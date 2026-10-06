// Driven by tools/clienttests/run_plugintest.py. Unlike the other client tests this one needs no
// source splicing: the plugin system is ordinary code in its own package, so it can be compiled
// and called directly. It sits in jagex2.client.plugin to reach the package-private seams
// (PluginStore, PluginConfig) that the client itself uses.
package jagex2.client.plugin;

import java.io.File;
import java.util.List;

import jagex2.client.plugin.event.ChatMessage;
import jagex2.client.plugin.event.GameTick;
import jagex2.client.plugin.event.MenuOptionClicked;

public class PluginSystemTest {

	static int fails;

	static void check(boolean ok, String what) {
		System.out.println((ok ? "  ok   " : "FAIL   ") + what);
		if (!ok) {
			fails++;
		}
	}

	public static void main(String[] args) {
		File jars = new File(args[0]);

		System.out.println("1. the event bus");
		busTests();
		System.out.println();
		System.out.println("2. settings that survive a restart");
		configTests(new File(args[1]));
		System.out.println();
		System.out.println("3. loading plugins out of jars");
		loaderTests(jars);
		System.out.println();
		System.out.println(fails == 0 ? "ALL PASS" : fails + " FAILED");
		System.exit(fails == 0 ? 0 : 1);
	}

	// ---------------------------------------------------------------- 1

	/** A subscriber of every shape the bus has to cope with. */
	public static class Listener {

		int ticks;
		String lastChat = "";
		int ignoredCalls;

		@Subscribe
		public void onGameTick(GameTick event) {
			this.ticks++;
		}

		@Subscribe
		public void onChatMessage(ChatMessage event) {
			this.lastChat = event.message;
		}

		@Subscribe
		public void onMenuOptionClicked(MenuOptionClicked event) {
			if (event.option.startsWith("Drop")) {
				event.consume();
			}
		}

		/** Mis-written on purpose: two arguments. The bus must skip it, not blow up. */
		@Subscribe
		public void wrongShape(GameTick event, int extra) {
			this.ignoredCalls++;
		}
	}

	public static class Thrower {

		int calls;

		@Subscribe
		public void onGameTick(GameTick event) {
			this.calls++;
			throw new IllegalStateException("plugins throw sometimes");
		}
	}

	static void busTests() {
		final Object[] failed = new Object[1];
		EventBus bus = new EventBus(new EventBus.ErrorListener() {

			public void onSubscriberFailed(Object subscriber, Throwable error) {
				failed[0] = subscriber;
			}
		});

		Listener listener = new Listener();
		bus.register(listener);
		bus.post(new GameTick(1));
		bus.post(new GameTick(2));
		bus.post(new ChatMessage("Zezima", "hello", 2));
		check(listener.ticks == 2, "a handler gets every event of its type (" + listener.ticks + " of 2)");
		check(listener.lastChat.equals("hello"), "and only events of its own type");
		check(listener.ignoredCalls == 0, "a mis-written @Subscribe is ignored, not called");

		MenuOptionClicked drop = new MenuOptionClicked("Drop @lre@Rune scimitar", 24, 1, 2, 3);
		bus.post(drop);
		check(drop.isConsumed(), "a handler can consume a menu click");
		MenuOptionClicked wield = new MenuOptionClicked("Wield @lre@Rune scimitar", 24, 1, 2, 3);
		bus.post(wield);
		check(!wield.isConsumed(), "...and leaves the ones it does not care about alone");

		bus.unregister(listener);
		bus.post(new GameTick(3));
		check(listener.ticks == 2, "unregistering stops delivery (" + listener.ticks + " still 2)");

		// The client must survive a plugin that throws on every single event.
		Thrower thrower = new Thrower();
		bus.register(thrower);
		for (int i = 0; i < 10; i++) {
			bus.post(new GameTick(i));
		}
		check(failed[0] == thrower, "a handler that keeps throwing is reported for shutdown");
		check(thrower.calls <= 5, "...after a few goes, not forever (" + thrower.calls + " calls)");
	}

	// ---------------------------------------------------------------- 2

	@PluginDescriptor(name = "Settings sample", key = "sample")
	public static class SettingsPlugin extends Plugin {

		@ConfigItem(keyName = "shown", name = "Shown")
		public boolean shown = true;

		@ConfigItem(keyName = "radius", name = "Radius")
		public int radius = 10;

		@ConfigItem(keyName = "label", name = "Label")
		public String label = "default";
	}

	static void configTests(File dir) {
		File file = new File(dir, "plugins.dat");

		PluginStore store = new PluginStore(file);
		store.put("someone.elses.key", "keepme");
		SettingsPlugin plugin = new SettingsPlugin();
		PluginConfig config = new PluginConfig("sample", store);
		config.bind(plugin);
		check(config.getItems().size() == 3, "every @ConfigItem field is found (" + config.getItems().size() + " of 3)");
		check(plugin.shown && plugin.radius == 10 && plugin.label.equals("default"),
			"with nothing saved, the field's own initialiser stands");

		PluginConfig.Item shown = config.getItems().get(0);
		check(shown.isBoolean() && shown.booleanValue(), "a boolean item reads its field");
		config.toggle(shown);
		check(!plugin.shown, "toggling writes the field");
		check(!config.getItems().get(1).isBoolean(), "an int item is not offered as a toggle");
		check(config.getItems().get(1).displayValue().equals("10"), "...it shows its value instead");

		// What a restart does: a new store over the same file, a new plugin object.
		SettingsPlugin reloaded = new SettingsPlugin();
		PluginStore second = new PluginStore(file);
		new PluginConfig("sample", second).bind(reloaded);
		check(!reloaded.shown, "a toggled setting survives a restart");
		check(second.get("someone.elses.key") != null,
			"and a key belonging to a plugin that is not loaded is not thrown away");

		// A hand-edited file with nonsense in it must not stop a plugin starting.
		second.put("sample.radius", "banana");
		second.save();
		SettingsPlugin third = new SettingsPlugin();
		new PluginConfig("sample", new PluginStore(file)).bind(third);
		check(third.radius == 10, "a corrupt saved value falls back to the default (" + third.radius + ")");
	}

	// ---------------------------------------------------------------- 3

	static void loaderTests(File jars) {
		List<PluginLoader.Found> declared = PluginLoader.scan(new File(jars, "declared"));
		check(declared.size() == 1, "a jar's Plugin-Class manifest decides what loads ("
			+ declared.size() + " of 1)");
		check(declared.size() == 1 && declared.get(0).type.getName().equals("sample.SamplePlugin"),
			"...and it is the class the manifest named");

		List<PluginLoader.Found> scanned = PluginLoader.scan(new File(jars, "scanned"));
		check(scanned.size() == 2, "without a manifest every class in the jar is checked ("
			+ scanned.size() + " of 2)");
		boolean skippedOrdinaryClass = true;
		boolean skippedAnonymous = true;
		for (int i = 0; i < scanned.size(); i++) {
			String name = scanned.get(i).type.getName();
			if (name.endsWith("NotAPlugin")) {
				skippedOrdinaryClass = false;
			}
			if (name.indexOf('$') >= 0) {
				skippedAnonymous = false;
			}
		}
		check(skippedOrdinaryClass, "...a class that is not a plugin is skipped");
		check(skippedAnonymous, "...and so is an anonymous Overlay inside one");

		// The plugin must come back usable, not just named.
		boolean constructed = false;
		boolean descriptorRead = false;
		try {
			for (int i = 0; i < scanned.size(); i++) {
				Class<?> type = scanned.get(i).type;
				if (!type.getName().endsWith("SamplePlugin")) {
					continue;
				}
				Plugin plugin = (Plugin) type.newInstance();
				constructed = plugin != null;
				PluginDescriptor descriptor = type.getAnnotation(PluginDescriptor.class);
				descriptorRead = descriptor != null && descriptor.name().equals("Sample");
			}
		} catch (Throwable error) {
			System.out.println("       " + error);
		}
		check(constructed, "a loaded plugin can be constructed");
		check(descriptorRead, "...and its descriptor is readable through the client's own annotation");

		List<PluginLoader.Found> broken = PluginLoader.scan(new File(jars, "broken"));
		check(broken.size() == 1, "a corrupt jar is skipped and the good one beside it still loads ("
			+ broken.size() + " of 1)");

		List<PluginLoader.Found> missing = PluginLoader.scan(new File(jars, "no-such-folder"));
		check(missing.isEmpty(), "no plugins folder is not an error");
	}
}
