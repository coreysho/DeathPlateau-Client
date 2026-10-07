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
	static boolean skipped;

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
		System.out.println("4. the built-in plugins, and the settings they used to be");
		builtInTests();
		System.out.println();
		System.out.println(fails == 0 ? (skipped ? "ALL PASS (a section was skipped)" : "ALL PASS")
			: fails + " FAILED");
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

	// ---------------------------------------------------------------- 4

	/**
	 * Escape-closes and Hide-roofs were QolSettings switches before they were plugins. What has
	 * to hold is that nobody notices: a player who never touched them gets the same behaviour,
	 * and a player who turned one off keeps it off.
	 */
	static void builtInTests() {
		if (java.awt.GraphicsEnvironment.isHeadless()) {
			// Said loudly rather than passing quietly: this is the section that checks nobody's
			// saved preference is lost, and a silent skip is how that stops being checked.
			System.out.println("  SKIP  no display, so a Client cannot be constructed - run with xvfb-run");
			skipped = true;
			return;
		}
		java.io.File cache = new java.io.File(sign.signlink.findcachedir());
		java.io.File qol = new java.io.File(cache, "qol_settings.dat");
		java.io.File plugins = new java.io.File(cache, "plugins.dat");

		// A player who has never had either file: the defaults the features always had.
		qol.delete();
		plugins.delete();
		PluginManager manager = freshManager();
		check(manager != null, "a manager builds with no settings files at all");
		if (manager == null) {
			return;
		}
		check(entry(manager, "escape-closes") != null && entry(manager, "hide-roofs") != null,
			"both built-in plugins are found");
		check(PluginManager.BUILT_IN_SOURCE.equals(entry(manager, "escape-closes").source),
			"...and say they came with the client");
		check(enabled(manager, "escape-closes"), "Escape closes interfaces is on by default, as it was");
		check(!enabled(manager, "hide-roofs"), "Hide roofs is off by default, as it was");

		// A player who turned Escape off back when it was a setting.
		write(qol, "version=1\nesc_close=0\nxp_drops=1\n");
		plugins.delete();
		manager = freshManager();
		check(manager != null && !enabled(manager, "escape-closes"),
			"a player who turned Escape off keeps it off");
		check(manager != null && !enabled(manager, "hide-roofs"), "...and Hide roofs is unaffected");

		// A player who turned Hide roofs ON back when it was a setting.
		write(qol, "version=1\nroofs_off=1\n");
		plugins.delete();
		manager = freshManager();
		check(manager != null && enabled(manager, "hide-roofs"),
			"a player who turned Hide roofs on keeps it on");

		// Once the plugin has its own state, the old file is history.
		write(qol, "version=1\nesc_close=0\n");
		write(plugins, "escape-closes.enabled=1\n");
		manager = freshManager();
		check(manager != null && enabled(manager, "escape-closes"),
			"the plugin's own state wins over the setting it replaced");

		qol.delete();
		plugins.delete();
	}

	/**
	 * A manager over the real built-in plugins.
	 *
	 * Client extends Applet, whose constructor refuses to run without a display - so this whole
	 * section needs one, and the runner provides a virtual one. There is no way round it worth
	 * having: a PluginManager with no client cannot start HideRoofsPlugin, which is exactly what
	 * is being tested.
	 */
	static PluginManager freshManager() {
		try {
			PluginManager manager = new PluginManager(new jagex2.client.Client(), null, null, null);
			manager.reload();
			return manager;
		} catch (Throwable error) {
			System.out.println("       " + error);
			return null;
		}
	}

	static PluginManager.Entry entry(PluginManager manager, String key) {
		for (PluginManager.Entry entry : manager.getPlugins()) {
			if (entry.key.equals(key)) {
				return entry;
			}
		}
		return null;
	}

	static boolean enabled(PluginManager manager, String key) {
		PluginManager.Entry entry = entry(manager, key);
		return entry != null && entry.isEnabled();
	}

	static void write(java.io.File file, String text) {
		try {
			java.io.PrintWriter writer = new java.io.PrintWriter(file);
			writer.print(text);
			writer.close();
		} catch (Exception error) {
			check(false, "could not write " + file + ": " + error);
		}
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
