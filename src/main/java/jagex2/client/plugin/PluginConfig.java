package jagex2.client.plugin;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import jagex2.client.DevLog;

/**
 * One plugin's settings: the {@link ConfigItem} fields on it, bound to saved values.
 *
 * HOW IT WORKS. On startup the manager hands every plugin its config, which scans the plugin for
 * annotated fields and writes each saved value into the field. From then on the plugin just reads
 * its own field - no map lookups, no getters, no cost in the game loop. Changing a setting (from
 * the panel, or by the plugin itself) writes the field and saves the store.
 *
 * WHAT A PLUGIN MAY NOT DO is rely on a config value surviving a rename: the saved key is the
 * annotation's keyName, not the field name, exactly so a field can be renamed freely and a key
 * never is.
 *
 * All values live in one file for all plugins, see {@link PluginStore}. Types are boolean, int and
 * String; anything else is reported once at load and left alone.
 */
public final class PluginConfig {

	/** One annotated field, remembered so the panel can list and toggle it. */
	public static final class Item {

		final Field field;
		final Object target;

		public final String key;
		public final String name;
		public final String description;

		Item(Field field, Object target, ConfigItem annotation) {
			this.field = field;
			this.target = target;
			this.key = annotation.keyName();
			this.name = annotation.name();
			this.description = annotation.description();
		}

		public boolean isBoolean() {
			return this.field.getType() == boolean.class;
		}

		public boolean isInt() {
			return this.field.getType() == int.class;
		}

		public boolean isString() {
			return this.field.getType() == String.class;
		}

		/** The value as the panel should show it: "on"/"off" for a boolean, otherwise toString. */
		public String displayValue() {
			Object value = this.read();
			if (value == null) {
				return "";
			}
			if (this.isBoolean()) {
				return ((Boolean) value).booleanValue() ? "on" : "off";
			}
			return String.valueOf(value);
		}

		public boolean booleanValue() {
			Object value = this.read();
			return value instanceof Boolean && ((Boolean) value).booleanValue();
		}

		public int intValue() {
			Object value = this.read();
			return value instanceof Integer ? ((Integer) value).intValue() : 0;
		}

		public String stringValue() {
			Object value = this.read();
			return value == null ? "" : String.valueOf(value);
		}

		private Object read() {
			try {
				return this.field.get(this.target);
			} catch (Throwable error) {
				return null;
			}
		}
	}

	private final String pluginKey;
	private final PluginStore store;
	private final List<Item> items = new ArrayList<Item>();

	PluginConfig(String pluginKey, PluginStore store) {
		this.pluginKey = pluginKey;
		this.store = store;
	}

	/**
	 * Finds the annotated fields on a plugin and loads their saved values. A field of a type that
	 * is not supported, or one that cannot be made accessible, is skipped with a log line and the
	 * plugin keeps whatever it initialised the field to.
	 */
	void bind(Plugin plugin) {
		Class<?> type = plugin.getClass();
		while (type != null && type != Plugin.class && type != Object.class) {
			Field[] fields = type.getDeclaredFields();
			for (int i = 0; i < fields.length; i++) {
				Field field = fields[i];
				ConfigItem annotation = field.getAnnotation(ConfigItem.class);
				if (annotation == null) {
					continue;
				}
				Class<?> fieldType = field.getType();
				if (fieldType != boolean.class && fieldType != int.class && fieldType != String.class) {
					DevLog.log("PLUGIN", this.pluginKey + "." + field.getName()
						+ ": @ConfigItem supports boolean, int and String only");
					continue;
				}
				try {
					field.setAccessible(true);
				} catch (Throwable error) {
					DevLog.log("PLUGIN", this.pluginKey + "." + field.getName() + " is not accessible");
					continue;
				}
				Item item = new Item(field, plugin, annotation);
				this.items.add(item);
				this.load(item);
			}
			type = type.getSuperclass();
		}
	}

	/** Every setting this plugin declared, in the order it declared them. */
	public List<Item> getItems() {
		return this.items;
	}

	/** Flips a boolean setting and saves it. Does nothing for the other types. */
	public void toggle(Item item) {
		if (item != null && item.isBoolean()) {
			this.set(item, item.booleanValue() ? "0" : "1");
		}
	}

	/**
	 * Writes a setting from its saved form - "1"/"0" for a boolean, digits for an int, the text
	 * itself for a String - and saves it. Returns false, changing nothing, when the text does not
	 * fit the field: that is how the config page tells the player their "12x" is not a number
	 * rather than quietly storing a zero.
	 *
	 * Call it on the game thread (PluginManager.invokeOnClientThread), not from the UI: it writes
	 * a field a running plugin may be reading.
	 */
	public boolean set(Item item, String text) {
		if (item == null || text == null) {
			return false;
		}
		try {
			Class<?> type = item.field.getType();
			if (type == boolean.class) {
				item.field.setBoolean(item.target, text.equals("1") || text.equalsIgnoreCase("true"));
			} else if (type == int.class) {
				item.field.setInt(item.target, Integer.parseInt(text.trim()));
			} else {
				item.field.set(item.target, text);
			}
		} catch (Throwable error) {
			DevLog.log("PLUGIN", "could not set " + this.pluginKey + "." + item.key + " to \"" + text + "\": " + error);
			return false;
		}
		this.store.put(this.pluginKey + "." + item.key, item.isBoolean() ? (item.booleanValue() ? "1" : "0") : text);
		this.store.save();
		return true;
	}

	private void load(Item item) {
		String saved = this.store.get(this.pluginKey + "." + item.key);
		if (saved == null) {
			return;                                      // never set: the field's initialiser stands
		}
		try {
			Class<?> type = item.field.getType();
			if (type == boolean.class) {
				item.field.setBoolean(item.target, saved.equals("1") || saved.equalsIgnoreCase("true"));
			} else if (type == int.class) {
				item.field.setInt(item.target, Integer.parseInt(saved.trim()));
			} else {
				item.field.set(item.target, saved);
			}
		} catch (Throwable error) {
			// A hand-edited file with "banana" where a number goes. The default stands and the
			// player gets a working plugin rather than one that refuses to start.
			DevLog.log("PLUGIN", "bad saved value for " + this.pluginKey + "." + item.key + ": " + saved);
		}
	}
}
