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
import jagex2.client.plugin.event.SettingsMenuOpening;
import jagex2.client.plugin.event.StatChanged;

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
		System.out.println("5. the XP drops plugin draws what it used to");
		xpDropTests();
		System.out.println();
		System.out.println("6. config lists");
		configListTests();
		System.out.println();
		System.out.println("7. the settings menu");
		settingsMenuTests();
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
		check(entry(manager, "escape-closes") != null && entry(manager, "hide-roofs") != null
			&& entry(manager, "xp-drops") != null && entry(manager, "barrows-doors") != null
			&& entry(manager, "menu-swapper") != null, "all five built-in plugins are found");
		check(PluginManager.BUILT_IN_SOURCE.equals(entry(manager, "escape-closes").source),
			"...and say they came with the client");
		check(enabled(manager, "escape-closes"), "Escape closes interfaces is on by default, as it was");
		check(!enabled(manager, "hide-roofs"), "Hide roofs is off by default, as it was");
		check(enabled(manager, "xp-drops"), "XP drops is on by default, as it was");
		check(enabled(manager, "barrows-doors"), "Barrows doors is on by default, as it was");
		check(enabled(manager, "menu-swapper"), "Left-click swaps is on by default, as it was");

		// A player who turned Escape off back when it was a setting.
		write(qol, "version=1\nesc_close=0\nxp_drops=1\n");
		plugins.delete();
		manager = freshManager();
		check(manager != null && !enabled(manager, "escape-closes"),
			"a player who turned Escape off keeps it off");
		check(manager != null && !enabled(manager, "hide-roofs"), "...and Hide roofs is unaffected");

		// And the one that draws: a player who turned XP drops off keeps it off.
		write(qol, "version=1\nxp_drops=0\n");
		plugins.delete();
		manager = freshManager();
		check(manager != null && !enabled(manager, "xp-drops"),
			"a player who turned XP drops off keeps it off");

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

	// ---------------------------------------------------------------- 7

	/** A plugin that offers a row per target and records which one was chosen. */
	public static final class Rower extends Plugin {

		String chosen = "";
		boolean sawWorldMenu;
		int offered;

		@Subscribe
		public void onSettingsMenuOpening(SettingsMenuOpening event) {
			this.sawWorldMenu = event.isWorldMenu();
			for (int i = 0; i < event.getTargets().size(); i++) {
				final SettingsMenuOpening.Target target = event.getTargets().get(i);
				if (event.addRow("Do something to " + target.name, new Runnable() {

					public void run() {
						Rower.this.chosen = target.name;
					}
				})) {
					this.offered++;
				}
			}
		}
	}

	static void settingsMenuTests() {
		List<SettingsMenuOpening.Target> targets = new java.util.ArrayList<SettingsMenuOpening.Target>();
		targets.add(new SettingsMenuOpening.Target("yel", "Guard", "Attack"));
		targets.add(new SettingsMenuOpening.Target("lre", "Bones", "Take"));

		SettingsMenuOpening event = new SettingsMenuOpening(targets, true, 10);
		Rower plugin = new Rower();
		plugin.onSettingsMenuOpening(event);
		check(event.getRows().size() == 2, "a plugin adds a row per target (" + event.getRows().size() + ")");
		check(plugin.sawWorldMenu, "...and is told this was a right-click in the world");
		check(event.getRows().get(0).label.equals("Do something to Guard"), "rows keep the order they were added");

		event.getRows().get(1).action.run();
		check(plugin.chosen.equals("Bones"), "choosing a row runs that row's own action");

		// The menu is a fixed array, so a plugin that adds a row per target on a crowded tile has
		// to be told to stop rather than running off the end of it.
		SettingsMenuOpening tight = new SettingsMenuOpening(targets, true, 1);
		Rower greedy = new Rower();
		greedy.onSettingsMenuOpening(tight);
		check(tight.getRows().size() == 1 && greedy.offered == 1,
			"a full menu refuses the next row and says so (" + tight.getRows().size() + " of 1)");

		// An inventory right-click: a plugin about things on the floor needs to know the
		// difference, because a ground item and an inventory item carry the same tag.
		SettingsMenuOpening inventory = new SettingsMenuOpening(targets, false, 10);
		Rower indoors = new Rower();
		indoors.onSettingsMenuOpening(inventory);
		check(!indoors.sawWorldMenu, "an inventory right-click is not a world menu");

		boolean immutable = false;
		try {
			event.getTargets().add(new SettingsMenuOpening.Target("yel", "Cow", "Attack"));
		} catch (UnsupportedOperationException expected) {
			immutable = true;
		}
		check(immutable, "a plugin cannot add targets to the event, only rows");
	}

	// ---------------------------------------------------------------- 6

	/** A list backed by a plain array, standing in for a plugin's own state. */
	static final class Fruit extends ConfigList {

		final java.util.List<String> names = new java.util.ArrayList<String>();
		final java.util.List<Boolean> ripe = new java.util.ArrayList<Boolean>();
		int thrown;

		public int size() {
			return this.names.size();
		}

		public String label(int index) {
			// Every third row throws, to prove one bad row does not take the page with it.
			if (index == 2) {
				this.thrown++;
				throw new IllegalStateException("this row is broken");
			}
			return this.names.get(index);
		}

		public String detail(int index) {
			return "a fruit";
		}

		public String action(int index) {
			return this.ripe.get(index).booleanValue() ? "Ripe" : "Green";
		}

		public void onAction(int index) {
			this.ripe.set(index, Boolean.valueOf(!this.ripe.get(index).booleanValue()));
		}

		public void onRemove(int index) {
			this.names.remove(index);
			this.ripe.remove(index);
		}

		public String emptyMessage() {
			return "No fruit.";
		}

		void add(String name) {
			this.names.add(name);
			this.ripe.add(Boolean.FALSE);
		}
	}

	/** A plugin that does nothing but own a list. */
	public static final class FruitPlugin extends Plugin {

		final Fruit fruit = new Fruit();

		protected void startUp() {
			this.addConfigList("Fruit", this.fruit);
		}
	}

	static void configListTests() {
		FruitPlugin plugin = new FruitPlugin();
		Plugin base = plugin;
		base.attach(null, null);
		base.startUp();

		plugin.fruit.add("apple");
		plugin.fruit.add("pear");
		plugin.fruit.add("fig");          // index 2: the one that throws
		plugin.fruit.add("plum");

		List<ConfigList.Row> rows = base.getConfigLists().get(0).list.snapshot();
		check(base.getConfigLists().size() == 1, "a plugin can register a list");
		check("Fruit".equals(base.getConfigLists().get(0).title), "...under a title");
		check(rows.size() == 3, "a row that throws is left out, the rest are kept (" + rows.size() + " of 4)");
		check(plugin.fruit.thrown == 1, "...and it really did throw");
		check(rows.get(0).label.equals("apple") && rows.get(0).action.equals("Green")
			&& "a fruit".equals(rows.get(0).detail) && rows.get(0).removable,
			"a row carries its label, detail, action and whether it can go");

		// The index on a row is the list's own, not the row's position in the snapshot - which
		// is what makes acting on a row still right when an earlier row was skipped.
		check(rows.get(2).index == 3 && rows.get(2).label.equals("plum"),
			"a row keeps the list's index, not the snapshot's (" + rows.get(2).index + ")");

		PluginManager.ListSnapshot snapshot = new PluginManager.ListSnapshot("Fruit", plugin.fruit);
		snapshot.act(0);
		check(plugin.fruit.ripe.get(0).booleanValue(), "pressing a row's button cycles it");
		snapshot.remove(3);
		check(plugin.fruit.names.size() == 3 && !plugin.fruit.names.contains("plum"),
			"removing a row removes the right one");

		base.shutDown();
		base.clearOverlays();
		check(base.getConfigLists().isEmpty(), "stopping the plugin drops its lists");

		FruitPlugin empty = new FruitPlugin();
		Plugin emptyBase = empty;
		emptyBase.attach(null, null);
		emptyBase.startUp();
		check(emptyBase.getConfigLists().get(0).list.snapshot().isEmpty()
			&& "No fruit.".equals(emptyBase.getConfigLists().get(0).list.emptyMessage()),
			"an empty list says so in its own words");
	}

	// ---------------------------------------------------------------- 5

	/**
	 * XP drops moved out of Client.java into a plugin. Pixels are the only honest check that a
	 * drawing feature survived that, so this binds Pix2D to a buffer, feeds the plugin a gain and
	 * looks at what lands in the corner it draws in.
	 *
	 * No fonts: they come out of the cache, which a headless test has none of. OverlayGraphics
	 * skips text when it has no font, so what is checked is the tracker box and its progress bar
	 * - which is enough to prove the overlay is wired up, draws in the right place, and stops
	 * when it should.
	 */
	static void xpDropTests() {
		if (java.awt.GraphicsEnvironment.isHeadless()) {
			System.out.println("  SKIP  no display, so a Client cannot be constructed");
			skipped = true;
			return;
		}
		final int w = 512;
		final int h = 334;
		int[] pixels = new int[w * h];

		jagex2.client.plugin.builtin.XpDropsPlugin plugin = new jagex2.client.plugin.builtin.XpDropsPlugin();
		// Also held as a Plugin: startUp, shutDown and getOverlays are the base class's, and a
		// subclass in another package does not re-export them.
		Plugin base = plugin;
		try {
			jagex2.client.Client client = new jagex2.client.Client();
			base.attach(new PluginContext(client), new PluginConfig("xp-drops", new PluginStore(null)));
			base.startUp();
		} catch (Throwable error) {
			check(false, "the plugin starts up (" + error + ")");
			return;
		}
		Overlay overlay = base.getOverlays().isEmpty() ? null : base.getOverlays().get(0);
		check(overlay != null, "starting up adds an overlay");
		if (overlay == null) {
			return;
		}
		OverlayGraphics g = new OverlayGraphics(null, null, null);

		check(drawn(overlay, g, pixels, w, h) == 0, "with no xp gained, nothing is drawn");

		plugin.onStatChanged(new StatChanged(0, 50, 101_333, 0));
		check(drawn(overlay, g, pixels, w, h) == 0,
			"a stat change that gained nothing - login's sync - draws nothing");

		plugin.onStatChanged(new StatChanged(0, 50, 101_433, 100));
		int painted = drawn(overlay, g, pixels, w, h);
		check(painted > 0, "a real gain draws the tracker (" + painted + " pixels)");
		check(rightmostPainted(pixels, w, h) > w / 2, "...in the right-hand half of the viewport");
		check(topmostPainted(pixels, w, h) < h / 2, "...and the top half: the corner it has always used");

		// Every row's fade is set once when it is created and never refreshed, so waiting is the
		// only way to see it expire - and the whole feature is that they go away on their own.
		try {
			Thread.sleep(1700);
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
		}
		plugin.showTracker = false;         // the tracker box outlives a drop; this is about drops
		check(drawn(overlay, g, pixels, w, h) == 0, "a drop is gone 1.7s later, without being touched");

		base.shutDown();
		check(base.getOverlays().size() == 1, "shutting down leaves the manager to clear overlays");
	}

	/** Renders into a cleared buffer and returns how many pixels were painted. */
	static int drawn(Overlay overlay, OverlayGraphics g, int[] pixels, int w, int h) {
		java.util.Arrays.fill(pixels, 0);
		jagex2.graphics.Pix2D.bind(w, h, pixels);
		g.reset(w, h);
		overlay.render(g);
		int count = 0;
		for (int i = 0; i < pixels.length; i++) {
			if (pixels[i] != 0) {
				count++;
			}
		}
		return count;
	}

	static int rightmostPainted(int[] pixels, int w, int h) {
		for (int x = w - 1; x >= 0; x--) {
			for (int y = 0; y < h; y++) {
				if (pixels[y * w + x] != 0) {
					return x;
				}
			}
		}
		return -1;
	}

	static int topmostPainted(int[] pixels, int w, int h) {
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				if (pixels[y * w + x] != 0) {
					return y;
				}
			}
		}
		return h;
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
