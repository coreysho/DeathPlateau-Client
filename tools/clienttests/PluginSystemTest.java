// Driven by tools/clienttests/run_plugintest.py. Unlike the other client tests this one needs no
// source splicing: the plugin system is ordinary code in its own package, so it can be compiled
// and called directly. It sits in jagex2.client.plugin to reach the package-private seams
// (PluginStore, PluginConfig) that the client itself uses.
package jagex2.client.plugin;

import java.io.File;
import java.util.ArrayList;
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
		swapperTests();
		System.out.println();
		System.out.println("6. config lists");
		configListTests();
		System.out.println();
		System.out.println("7. the settings menu");
		settingsMenuTests();
		System.out.println();
		System.out.println("8. overlays you can click");
		regionTests();
		System.out.println();
		System.out.println("9. the anti-drag plugin, which is a number rather than a switch");
		antiDragTests();
		System.out.println();
		System.out.println("10. panels: a plugin's own page on the rail");
		panelTests();
		System.out.println();
		System.out.println("11. client API levels: what a jar may ask for");
		apiLevelTests(new File(args[2]), new File(args[3]));
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
	 * Every built-in was a QolSettings switch before it was a plugin. What has to hold is that
	 * nobody notices: a player who never touched one gets the same behaviour, and a player who
	 * turned one off keeps it off.
	 *
	 * NOT EVERY PORT STUCK. "Escape closes interfaces" and "Hide roofs" were the first two and
	 * have gone back to the F9 panel, so they are not here any more - run_rooftest owns them
	 * again, including the check that Hide roofs is the one setting defaulting off. Nothing
	 * built in asks to start disabled now, so that half of defaultEnabled() is only covered by
	 * the jar case below, which returns false for its own reason.
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
		check(entry(manager, "xp-drops") != null && entry(manager, "barrows-doors") != null
			&& entry(manager, "menu-swapper") != null && entry(manager, "ground-items") != null
			&& entry(manager, "anti-drag") != null, "all five built-in plugins are found");
		check(entry(manager, "escape-closes") == null && entry(manager, "hide-roofs") == null,
			"...and the two that went back to the F9 panel are not among them");
		check(PluginManager.BUILT_IN_SOURCE.equals(entry(manager, "xp-drops").source),
			"...and they say they came with the client");
		check(enabled(manager, "ground-items"), "Ground items is on by default, as it was");
		check(enabled(manager, "anti-drag"), "Anti-drag is on by default, as it was");
		check(enabled(manager, "xp-drops"), "XP drops is on by default, as it was");
		check(enabled(manager, "barrows-doors"), "Barrows doors is on by default, as it was");
		check(enabled(manager, "menu-swapper"), "Left-click swaps is on by default, as it was");

		// The cog in the plugin list is drawn from hasSettings(), which is a LIST OR SETTINGS -
		// a plugin with only a list still needs a page, and the first version of that test
		// missed it, leaving Left-click swaps' page with no way in.
		//
		// LEFT-CLICK SWAPS USED TO BE THE LIST-ONLY EXAMPLE and is not any more: it has three
		// settings now. So the property is stated against a plugin built here for it rather than
		// against whichever built-in happens to have no settings this month - which is what made
		// the old check break the moment one gained some.
		check(entry(manager, "menu-swapper").hasSettings(),
			"Left-click swaps has a page");
		check(!entry(manager, "menu-swapper").getConfig().getItems().isEmpty(),
			"...from settings of its own now, as well as its list");
		check(entry(manager, "xp-drops").hasSettings(), "XP drops has a page from its settings");
		check(!entry(manager, "barrows-doors").hasSettings(),
			"a plugin with neither has no page, and no cog");

		ListOnlyPlugin listOnly = new ListOnlyPlugin();
		Plugin listOnlyBase = listOnly;
		PluginConfig emptyConfig = new PluginConfig("list-only", new PluginStore(null));
		emptyConfig.bind(listOnly);
		listOnlyBase.attach(null, emptyConfig);
		listOnlyBase.startUp();
		check(emptyConfig.getItems().isEmpty(), "a plugin with no @ConfigItem has no settings...");
		check(!listOnlyBase.getConfigLists().isEmpty(), "...and one list");
		check(emptyConfig.getItems().isEmpty() || !listOnlyBase.getConfigLists().isEmpty(),
			"...which is what hasSettings ORs, so it would still be given a page and a cog");

		// A player who turned one off back when it was a setting.
		write(qol, "version=1\nxp_drops=0\nground_items=1\n");
		plugins.delete();
		manager = freshManager();
		check(manager != null && !enabled(manager, "xp-drops"),
			"a player who turned XP drops off keeps it off");
		check(manager != null && enabled(manager, "ground-items"),
			"...and the one beside it in the same file is unaffected");

		// The key a setting was saved under is the ONLY thing tying it to its plugin, and the two
		// names differ - ground_items against ground-items. Getting that wrong loses the choice
		// silently, so it is worth a second feature rather than trusting the first.
		write(qol, "version=1\nground_items=0\n");
		plugins.delete();
		manager = freshManager();
		check(manager != null && !enabled(manager, "ground-items"),
			"a player who turned Ground items off keeps it off");

		// Once the plugin has its own state, the old file is history.
		write(qol, "version=1\nxp_drops=0\n");
		write(plugins, "xp-drops.enabled=1\n");
		manager = freshManager();
		check(manager != null && enabled(manager, "xp-drops"),
			"the plugin's own state wins over the setting it replaced");

		qol.delete();
		plugins.delete();
	}

	// ---------------------------------------------------------------- 9

	/**
	 * Anti-drag was a QolSettings switch that chose between two hardcoded numbers. As a plugin
	 * it is one number the player sets, so what has to hold is the conversion, the clamp, and
	 * that the client is left exactly as it was when the plugin is off.
	 */
	static void antiDragTests() {
		// The arithmetic needs no client at all.
		check(jagex2.client.plugin.builtin.AntiDragPlugin.cyclesFor(200) == 10, "200ms is ten cycles, the hold the switch used");
		check(jagex2.client.plugin.builtin.AntiDragPlugin.cyclesFor(100) == 5, "100ms is five, which is what the client does alone");
		check(jagex2.client.plugin.builtin.AntiDragPlugin.cyclesFor(600) == 30, "600ms is thirty, RuneLite's figure");
		// Rounding, not truncation: 190 is nearer ten cycles than nine.
		check(jagex2.client.plugin.builtin.AntiDragPlugin.cyclesFor(190) == 10, "190ms rounds to ten rather than down to nine");
		check(jagex2.client.plugin.builtin.AntiDragPlugin.cyclesFor(0) == 1 && jagex2.client.plugin.builtin.AntiDragPlugin.cyclesFor(-500) == 1,
			"zero and nonsense clamp to one cycle, so dragging never becomes impossible");
		check(jagex2.client.plugin.builtin.AntiDragPlugin.cyclesFor(1000000) == 100,
			"...and an hour of holding clamps to a hundred");

		if (java.awt.GraphicsEnvironment.isHeadless()) {
			System.out.println("  SKIP  no display, so a Client cannot be constructed");
			skipped = true;
			return;
		}
		jagex2.client.Client client;
		jagex2.client.plugin.builtin.AntiDragPlugin plugin =
			new jagex2.client.plugin.builtin.AntiDragPlugin();
		Plugin base = plugin;
		try {
			client = new jagex2.client.Client();
			base.attach(new PluginContext(client), new PluginConfig("anti-drag", new PluginStore(null)));
		} catch (Throwable error) {
			check(false, "the plugin attaches to a client (" + error + ")");
			return;
		}
		check(client.pluginDragCycles == 5,
			"a client nobody has touched holds for five cycles (" + client.pluginDragCycles + ")");

		base.startUp();
		check(client.pluginDragCycles == 10, "starting the plugin raises it to the 200ms default ("
			+ client.pluginDragCycles + ")");

		// A value typed into the config page has to take effect without toggling the plugin,
		// which is the whole reason it is re-applied on a tick rather than only at startup.
		plugin.holdMillis = 600;
		check(client.pluginDragCycles == 10, "...and changing the setting alone changes nothing yet");
		plugin.onClientTick(new jagex2.client.plugin.event.ClientTick(0));
		check(client.pluginDragCycles == 30, "...until the next frame, which picks it up ("
			+ client.pluginDragCycles + ")");

		plugin.holdMillis = 0;
		plugin.onClientTick(new jagex2.client.plugin.event.ClientTick(1));
		check(client.pluginDragCycles == 1, "a zero typed in is clamped on the way through too");

		plugin.holdMillis = 200;
		plugin.onClientTick(new jagex2.client.plugin.event.ClientTick(2));

		// ---- THE THREE NEW SETTINGS, read off client.pluginDragCycles rather than off the
		// plugin. That is what the client actually drags by, so it is the stronger question -
		// and it needs nothing package-private, which a test in another package should not be
		// reaching for anyway. The number to beat is 5: the client's own, which the check above
		// establishes before the plugin ever starts.
		int client5 = 5;

		// ---- SHIFT, through the real context. The client's own actionKey array is what
		// isShiftHeld reads, so this is the key the game would see.
		plugin.resetOnShift = true;
		client.actionKey[jagex2.client.GameShell.KEY_SHIFT] = 1;
		plugin.onClientTick(new jagex2.client.plugin.event.ClientTick(3));
		check(client.pluginDragCycles == client5,
			"holding shift puts the client's own hold time back for as long as it is held ("
				+ client.pluginDragCycles + ")");
		client.actionKey[jagex2.client.GameShell.KEY_SHIFT] = 0;
		plugin.onClientTick(new jagex2.client.plugin.event.ClientTick(4));
		check(client.pluginDragCycles == 10, "...and letting go brings the player's back");

		// Off, shift does nothing - which is the point of its being a setting.
		plugin.resetOnShift = false;
		client.actionKey[jagex2.client.GameShell.KEY_SHIFT] = 1;
		plugin.onClientTick(new jagex2.client.plugin.event.ClientTick(5));
		check(client.pluginDragCycles == 10, "with the setting off, shift changes nothing");
		client.actionKey[jagex2.client.GameShell.KEY_SHIFT] = 0;
		plugin.resetOnShift = true;

		// ---- THE SUSPEND KEY, which is the same parse Ground items uses.
		plugin.suspendKey = "D";
		plugin.announce = false;
		jagex2.client.plugin.event.KeyPressed press =
			new jagex2.client.plugin.event.KeyPressed('d');
		plugin.onKeyPressed(press);
		check(press.isConsumed(), "the suspend key is swallowed, so it does not reach the chat box");
		plugin.onClientTick(new jagex2.client.plugin.event.ClientTick(6));
		check(client.pluginDragCycles == client5,
			"...and the next frame hands the client its own hold time back ("
				+ client.pluginDragCycles + ")");
		plugin.onKeyPressed(new jagex2.client.plugin.event.KeyPressed('d'));
		plugin.onClientTick(new jagex2.client.plugin.event.ClientTick(7));
		check(client.pluginDragCycles == 10, "pressing it again brings the player's back");

		// Another key is left alone entirely, and with no key set nothing is a suspend key.
		jagex2.client.plugin.event.KeyPressed other =
			new jagex2.client.plugin.event.KeyPressed('x');
		plugin.onKeyPressed(other);
		plugin.onClientTick(new jagex2.client.plugin.event.ClientTick(8));
		check(!other.isConsumed() && client.pluginDragCycles == 10, "another key does nothing");
		plugin.suspendKey = "";
		jagex2.client.plugin.event.KeyPressed none =
			new jagex2.client.plugin.event.KeyPressed('d');
		plugin.onKeyPressed(none);
		plugin.onClientTick(new jagex2.client.plugin.event.ClientTick(9));
		check(!none.isConsumed() && client.pluginDragCycles == 10,
			"and with no key set, no key suspends it");

		base.shutDown();
		check(client.pluginDragCycles == 5,
			"turning it off leaves the client holding for five again, exactly as an unmodified one does");
	}

	// ---------------------------------------------------------------- 9b

	/**
	 * Left-click swaps' three settings, and the one thing it could not do before level 6.
	 *
	 * A swap is invisible by design - the whole point is that the option is simply there under
	 * the left button - and that is also what makes a wrong one hard to find: the menu looks
	 * normal and the click does the wrong thing. Colouring the row a swap promoted answers "is
	 * this mine" without changing what any row says or does.
	 *
	 * Driven by attaching the plugin to a bare Client and calling its handler, the way
	 * antiDragTests does: the manager is not needed to ask what a handler does to a menu.
	 */
	static void swapperTests() {
		if (java.awt.GraphicsEnvironment.isHeadless()) {
			System.out.println("  SKIP  no display, so a Client cannot be constructed");
			skipped = true;
			return;
		}
		jagex2.client.Client client;
		jagex2.client.plugin.builtin.MenuSwapperPlugin plugin =
			new jagex2.client.plugin.builtin.MenuSwapperPlugin();
		Plugin base = plugin;
		PluginConfig config = new PluginConfig("menu-swapper", new PluginStore(null));
		try {
			client = new jagex2.client.Client();
			client.ingame = true;
			jagex2.client.Client.localPlayer = new jagex2.dash3d.ClientPlayer();
			config.bind(plugin);
			base.attach(new PluginContext(client), config);
			base.startUp();
		} catch (Throwable error) {
			check(false, "the plugin attaches to a client (" + error + ")");
			return;
		}

		// Defaults: the behaviour this plugin shipped with, which had no settings at all.
		check(!plugin.colourSwapped, "the swap colouring starts off");
		check("00FFFF".equals(plugin.swapColour), "with a colour ready for when it is wanted");
		check(plugin.announce, "and the chat lines on, as they always were");
		check(config.getItems().size() == 3, "three settings ("
			+ config.getItems().size() + ")");

		jagex2.client.MenuSwaps.clear();
		jagex2.client.MenuSwaps.add("yel", "Guard", "Attack");

		// A menu where Attack is NOT the left click: index 0 is Cancel and the highest index is
		// what a left click runs, so Attack at 1 is the bottom row and needs promoting.
		client.menuOption[0] = "Cancel";
		client.menuAction[0] = 1016;
		client.menuOption[1] = "Attack @yel@Guard";
		client.menuAction[1] = 7;
		client.menuOption[2] = "Walk here";
		client.menuAction[2] = jagex2.client.Client.WALK_HERE_ACTION;
		client.menuSize = 3;
		for (int i = 0; i < client.menuColour.length; i++) {
			client.menuColour[i] = 0;
		}

		plugin.onMenuBuilt(new jagex2.client.plugin.event.MenuBuilt(client.menuSize));
		check("Attack @yel@Guard".equals(client.menuOption[client.menuSize - 1]),
			"the swap promotes Attack to the left click ("
				+ client.menuOption[client.menuSize - 1] + ")");
		check(client.menuColour[client.menuSize - 1] == 0,
			"...and with the colouring off, no row is coloured");

		// On: the promoted row is coloured, and it is the row the swap MOVED - the colour has to
		// be set after the promotion, or it lands on whatever index Attack came from.
		plugin.colourSwapped = true;
		client.menuOption[1] = "Attack @yel@Guard";
		client.menuOption[2] = "Walk here";
		client.menuColour[1] = 0;
		client.menuColour[2] = 0;
		plugin.onMenuBuilt(new jagex2.client.plugin.event.MenuBuilt(client.menuSize));
		int top = client.menuSize - 1;
		check("Attack @yel@Guard".equals(client.menuOption[top]), "the swap still promotes it");
		check(client.menuColour[top] == 0x00FFFF,
			"...and the promoted row is coloured, at the index it ended up at ("
				+ Integer.toHexString(client.menuColour[top]) + ")");
		check(client.menuColour[1] == 0,
			"...and not at the index it came from, which is a different row now");

		plugin.swapColour = "FF00FF";
		client.menuOption[1] = "Attack @yel@Guard";
		client.menuOption[2] = "Walk here";
		client.menuColour[1] = 0;
		client.menuColour[2] = 0;
		plugin.onMenuBuilt(new jagex2.client.plugin.event.MenuBuilt(client.menuSize));
		check(client.menuColour[client.menuSize - 1] == 0xFF00FF,
			"a colour a player picks is what gets used");

		// A menu with no swap in it is untouched, colouring on or off.
		client.menuOption[1] = "Attack @yel@Goblin";
		client.menuOption[2] = "Walk here";
		client.menuColour[1] = 0;
		client.menuColour[2] = 0;
		plugin.onMenuBuilt(new jagex2.client.plugin.event.MenuBuilt(client.menuSize));
		check(client.menuColour[1] == 0 && client.menuColour[2] == 0,
			"a menu no swap applies to is left entirely alone");

		// ---- THE CHAT LINES. Setting a swap says so; turning the setting off quietens it.
		//
		// THE ROW HAS TO BE RUN. The previous version of this compared messages() with ">=",
		// which is true of every pair of numbers - so deleting the chat line from say()
		// altogether passed it, and the audit found exactly that. Opening the menu says nothing
		// by design; it is setting the swap that speaks.
		jagex2.client.MenuSwaps.clear();
		plugin.announce = true;
		jagex2.client.plugin.event.SettingsMenuOpening opening = swapMenu();
		plugin.onSettingsMenuOpening(opening);
		java.util.List<jagex2.client.plugin.event.SettingsMenuOpening.Row> swapRows =
			opening.getRows();
		check(!swapRows.isEmpty(),
			"a shift-right-click over a Guard offers a swap row (" + swapRows.size() + ")");
		int before = messages(client);
		swapRows.get(0).action.run();
		check(messages(client) > before,
			"...and running it says so in chat, because a setting that changes what a click "
				+ "does and says nothing is indistinguishable from one that did not work ("
				+ before + " -> " + messages(client) + ")");

		jagex2.client.MenuSwaps.clear();
		plugin.announce = false;
		opening = swapMenu();
		plugin.onSettingsMenuOpening(opening);
		swapRows = opening.getRows();
		check(!swapRows.isEmpty(), "the row is still offered with the chat lines off");
		before = messages(client);
		swapRows.get(0).action.run();
		check(messages(client) == before,
			"...and running it is silent, which is what the setting is for (" + before + " -> "
				+ messages(client) + ")");
		plugin.announce = true;

		jagex2.client.MenuSwaps.clear();
		base.shutDown();
	}

	/** A shift-right-click over a Guard, as the client would build it. */
	static jagex2.client.plugin.event.SettingsMenuOpening swapMenu() {
		java.util.List<jagex2.client.plugin.event.SettingsMenuOpening.Target> targets =
			new ArrayList<jagex2.client.plugin.event.SettingsMenuOpening.Target>();
		targets.add(new jagex2.client.plugin.event.SettingsMenuOpening.Target(
			"yel", "Guard", "Attack"));
		return new jagex2.client.plugin.event.SettingsMenuOpening(targets, true, 32);
	}

	static int messages(jagex2.client.Client client) {
		int n = 0;
		for (int i = 0; i < client.messageText.length; i++) {
			if (client.messageText[i] != null) {
				n++;
			}
		}
		return n;
	}

	// ---------------------------------------------------------------- 10

	/**
	 * A plugin with a list and no settings, which is the case hasSettings() has to OR.
	 *
	 * Built here rather than borrowed from the built-ins: Left-click swaps was the example until
	 * it gained settings of its own, and a property stated against whichever plugin happens to
	 * have none this month is a check that breaks for the wrong reason.
	 */
	@PluginDescriptor(name = "List only", description = "A list and nothing else", key = "list-only")
	public static class ListOnlyPlugin extends Plugin {

		protected void startUp() {
			this.addConfigList("Things", new ConfigList() {

				public int size() {
					return 0;
				}

				public String label(int index) {
					return "";
				}

				public boolean removable(int index) {
					return false;
				}
			});
		}
	}

	/** A plugin with a page of its own, of the shape the rail draws. */
	@PluginDescriptor(name = "Counter", description = "A page", key = "counter")
	public static class PanelPlugin extends Plugin {

		int count = 3;
		int pressed = -1;
		boolean explode;

		protected void startUp() {
			this.addPanel("Readings", "chart", new ConfigList() {

				public int size() {
					if (PanelPlugin.this.explode) {
						throw new IllegalStateException("no");
					}
					return PanelPlugin.this.count;
				}

				public String label(int index) {
					return "Row " + index;
				}

				public String value(int index) {
					return index * 10 + " xp";
				}

				public int progress(int index) {
					// Deliberately out of range at both ends: the row is supposed to clamp.
					return index == 0 ? -40 : index == 1 ? 50 : 300;
				}

				public void onAction(int index) {
					PanelPlugin.this.pressed = index;
				}
			});
		}
	}

	static void panelTests() {
		if (java.awt.GraphicsEnvironment.isHeadless()) {
			System.out.println("  SKIP  no display, so a Client cannot be constructed");
			skipped = true;
			return;
		}
		PanelPlugin plugin = new PanelPlugin();
		Plugin base = plugin;
		PluginManager manager;
		try {
			manager = new PluginManager(new jagex2.client.Client(), null, null, null);
			base.attach(new PluginContext(new jagex2.client.Client()),
				new PluginConfig("counter", new PluginStore(null)));
		} catch (Throwable error) {
			check(false, "the plugin attaches (" + error + ")");
			return;
		}

		check(base.getPanels().isEmpty(), "a plugin that has not started has no page");
		base.startUp();
		check(base.getPanels().size() == 1, "starting it registers one");
		check(base.getPanels().get(0).title.equals("Readings"), "...under the title it gave");

		List<ConfigList.Row> rows = base.getPanels().get(0).list.snapshot();
		check(rows.size() == 3, "the page reads three rows (" + rows.size() + ")");
		check(rows.get(1).label.equals("Row 1") && "10 xp".equals(rows.get(1).value),
			"...each with a label and a reading of its own");
		check(rows.get(1).progress == 50, "...and a bar where it asked for one");

		// A bar is a percentage. A plugin handing out -40 or 300 must not paint outside its row.
		check(rows.get(0).progress == -1,
			"a negative progress means no bar rather than a backwards one ("
				+ rows.get(0).progress + ")");
		check(rows.get(2).progress == 100, "and anything over 100 is full, not wider than the row ("
			+ rows.get(2).progress + ")");

		// Row order and index are the plugin's: acting on row 2 must reach ITS row 2.
		base.getPanels().get(0).list.onAction(2);
		check(plugin.pressed == 2, "pressing a row's button reaches the row it was drawn for");

		// A page that throws is an empty page, not a dead sidebar.
		plugin.explode = true;
		List<ConfigList.Row> after = base.getPanels().get(0).list.snapshot();
		check(after.isEmpty(), "a page whose plugin throws reads as empty rather than taking the "
			+ "sidebar down (" + after.size() + ")");
		plugin.explode = false;

		// Only RUNNING plugins are on the rail: the page goes when the plugin does.
		base.shutDown();
		base.clearOverlays();
		check(base.getPanels().isEmpty(), "stopping the plugin takes its page off the rail");

		// And the manager only offers panels for plugins it has running.
		manager.reload();
		List<PluginManager.PanelSnapshot> built = manager.snapshotPanels();
		boolean allRunning = true;
		for (int i = 0; i < built.size(); i++) {
			allRunning &= built.get(i).entry.isEnabled();
		}
		check(allRunning, "every panel the manager offers belongs to a plugin that is running ("
			+ built.size() + " panels)");
	}

	/**
	 * A manager over the real built-in plugins.
	 *
	 * Client extends Applet, whose constructor refuses to run without a display - so this whole
	 * section needs one, and the runner provides a virtual one. There is no way round it worth
	 * having: a PluginManager with no client cannot start a built-in that touches one, which is
	 * exactly what is being tested.
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

	// ---------------------------------------------------------------- 8

	/**
	 * An overlay claims rectangles while it draws, and the client asks whether a click landed in
	 * one. The rules worth pinning down are that a region only lives for the frame that drew it,
	 * that the thing drawn last is the thing clicked, and that a handler which throws does not
	 * take the click path with it.
	 */
	static void regionTests() {
		InteractiveRegions regions = new InteractiveRegions();
		final StringBuilder log = new StringBuilder();

		regions.add(10, 10, 100, 20, new Runnable() {

			public void run() {
				log.append("under;");
			}
		}, null, null);
		regions.add(50, 15, 20, 10, new Runnable() {

			public void run() {
				log.append("over;");
			}
		}, null, null);

		check(regions.click(20, 15), "a click inside a claimed rectangle is taken");
		check(log.toString().equals("under;"), "...by the region it landed in");
		check(!regions.click(200, 200), "a click outside every rectangle is left alone");
		check(!regions.click(20, 100), "...including one below them all");

		log.setLength(0);
		regions.click(55, 18);
		check(log.toString().equals("over;"),
			"where two overlap, the one drawn last wins - that is the one on top");

		// A region is only alive for the frame that drew it.
		regions.clear();
		check(!regions.click(20, 15), "a cleared frame claims nothing");

		// The wheel is separate: a click region does not swallow turns, and the reverse.
		final int[] turned = new int[1];
		regions.add(0, 0, 50, 50, null, new OverlayGraphics.Scrolled() {

			public void onScroll(int delta) {
				turned[0] += delta;
			}
		}, null);
		check(!regions.click(10, 10), "a scroll region does not take clicks");
		check(regions.scroll(10, 10, -3) && turned[0] == -3,
			"...and takes the wheel, with its direction (" + turned[0] + ")");

		regions.clear();
		regions.add(0, 0, 50, 50, new Runnable() {

			public void run() {
				throw new IllegalStateException("this handler is broken");
			}
		}, null, null);
		boolean survived = true;
		try {
			check(regions.click(10, 10), "a region whose handler throws still counts as hit...");
		} catch (Throwable error) {
			survived = false;
		}
		check(survived, "...and the throw does not escape into the client's click handling");

		// Zero-sized regions are a plugin bug that would otherwise swallow nothing visibly.
		regions.clear();
		regions.add(5, 5, 0, 10, new Runnable() {

			public void run() {
				log.append("zero;");
			}
		}, null, null);
		check(!regions.click(5, 5), "a rectangle with no width claims nothing");
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
		// No offset: this is drawing an overlay, not testing where a player dragged it - see
		// run_dragtest for that.
		g.reset(w, h, new InteractiveRegions(), null, 0, 0);
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

	/**
	 * The gate that keeps a jar built against a newer client from loading on an older one.
	 *
	 * Driven through the real PluginManager over real jars in the real plugins folder, because
	 * the thing being checked is not that PluginApi.supports() does arithmetic - it is that a
	 * refused plugin is NOT constructed, IS still reported with a reason, and does not take the
	 * working plugin beside it down. The three jars are:
	 *
	 *   future.jar   declares apiLevel 999 - refused before its constructor runs
	 *   current.jar  declares apiLevel 1 - loads
	 *   linkage.jar  declares nothing and calls a method its jar no longer contains - loads, then
	 *                fails on startUp with a NoSuchMethodError the JVM raises
	 */
	static void apiLevelTests(File apiJars, File witness) {
		if (java.awt.GraphicsEnvironment.isHeadless()) {
			System.out.println("  SKIP  no display, so a Client cannot be constructed");
			skipped = true;
			return;
		}
		check(jagex2.client.plugin.PluginApi.LEVEL >= 1,
			"this client reports an API level of at least 1 (" + jagex2.client.plugin.PluginApi.LEVEL + ")");
		check(jagex2.client.plugin.PluginApi.supports(0),
			"level 0 - \"does not say\" - is supported, so no pre-levels plugin is refused");
		check(!jagex2.client.plugin.PluginApi.supports(jagex2.client.plugin.PluginApi.LEVEL + 1),
			"one past this client's level is not supported");

		// What the player is actually shown, checked against the messages the JVM really writes.
		// These strings are not invented: the first is what the linkage.jar case below produces.
		check("Helper.v2()".equals(PluginManager.missing(
			new NoSuchMethodError("'void api.Helper.v2()'"))),
			"a missing method reads as its owner and name: \""
				+ PluginManager.missing(new NoSuchMethodError("'void api.Helper.v2()'")) + "\"");
		check("Helper.v2()".equals(PluginManager.missing(
			new NoSuchMethodError("'int api.Helper.v2(java.lang.String, int)'"))),
			"...with the argument list off, dots and all: \""
				+ PluginManager.missing(new NoSuchMethodError("'int api.Helper.v2(java.lang.String, int)'")) + "\"");
		check("api.Helper".equals(PluginManager.missing(new NoClassDefFoundError("api/Helper"))),
			"a missing class reads as a class name, slashes and all: \""
				+ PluginManager.missing(new NoClassDefFoundError("api/Helper")) + "\"");
		check("PluginContext.getNpcs()".equals(PluginManager.missing(new NoSuchMethodError(
			"'java.util.List jagex2.client.plugin.PluginContext.getNpcs(int)'"))),
			"...and the case this exists for reads plainly: \"" + PluginManager.missing(new NoSuchMethodError(
				"'java.util.List jagex2.client.plugin.PluginContext.getNpcs(int)'")) + "\"");
		// The sentence shape, which the signature rules get backwards if nothing distinguishes
		// them: this message starts with the class and runs on in prose. Reading it from the
		// wrong end answered "has been compiled by a more recent version of the Java Runtime",
		// and the bracket in "(class file version 65.0)" made it look like a method.
		//
		// It is also the error a plugin built for the wrong Java raises, which is not
		// hypothetical - the plugin hub's builder shipped jars compiled for Java 21 against a
		// Java 8 client, and this is what a player would have seen.
		String tooNewJava = "api/Helper has been compiled by a more recent version of the Java"
			+ " Runtime (class file version 65.0), this version of the Java Runtime only"
			+ " recognizes class file versions up to 52.0";
		check("api.Helper".equals(PluginManager.missing(new UnsupportedClassVersionError(tooNewJava))),
			"a class built for a newer Java reads as just the class: \""
				+ PluginManager.missing(new UnsupportedClassVersionError(tooNewJava)) + "\"");
		check("someField".equals(PluginManager.missing(new NoSuchFieldError("someField"))),
			"a missing field, which has no dots at all, reads as itself: \""
				+ PluginManager.missing(new NoSuchFieldError("someField")) + "\"");
		check("someField".equals(PluginManager.missing(new NoSuchFieldError("  someField  "))),
			"...cleaned up, because padding in a sidebar row is still padding: \""
				+ PluginManager.missing(new NoSuchFieldError("  someField  ")) + "\"");

		// A sentence with TWO names in it, which is the only shape that can tell the two ends
		// apart - the UnsupportedClassVersionError above has one, so it reads the same either
		// way and proved nothing about which end is taken.
		String changed = "class api.Helper has interface java.lang.Runnable as super class";
		check("api.Helper".equals(PluginManager.missing(new IncompatibleClassChangeError(changed))),
			"a sentence naming two classes reads the one it is about, not the last one: \""
				+ PluginManager.missing(new IncompatibleClassChangeError(changed)) + "\"");

		// No message at all must not read as an empty sentence - and an empty one is not the
		// same case as a null one, which is the only one the first version of this checked.
		check(PluginManager.missing(new NoSuchMethodError()).length() > 0,
			"a LinkageError with no message still says something ("
				+ PluginManager.missing(new NoSuchMethodError()) + ")");
		check(PluginManager.missing(new NoSuchMethodError("")).length() > 0,
			"...and so does one whose message is empty ("
				+ PluginManager.missing(new NoSuchMethodError("")) + ")");
		check(PluginManager.missing(new NoSuchMethodError("   ")).length() > 0,
			"...and one that is nothing but spaces ("
				+ PluginManager.missing(new NoSuchMethodError("   ")) + ")");

		File folder = new File(new File(System.getProperty("user.home"), ".deathplateau"), "plugins");
		if (!folder.isDirectory() && !folder.mkdirs()) {
			check(false, "a plugins folder can be made under this run's home directory");
			return;
		}
		File[] copied = new File[3];
		String[] names = { "future.jar", "current.jar", "linkage.jar" };
		try {
			for (int i = 0; i < names.length; i++) {
				copied[i] = new File(folder, names[i]);
				copy(new File(apiJars, names[i]), copied[i]);
			}
			// Nothing saved about any of them, so current.jar starts off like any jar plugin and
			// linkage.jar has to be switched on by hand below.
			new File(sign.signlink.findcachedir() + "plugins.dat").delete();

			PluginManager manager = freshManager();
			check(manager != null, "a manager builds over the three jars");
			if (manager == null) {
				return;
			}

			check(entry(manager, "api.future") == null, "the jar asking for API 999 did not load");
			check(!witness.exists(),
				"...and was never constructed - the level is read off the class, not the object");
			check(entry(manager, "api.current") != null, "the jar asking for API 1 loaded");
			check(entry(manager, "api.linkage") != null,
				"...and so did the one that declares nothing: it only fails when it runs");

			PluginManager.Refused refused = refused(manager, "From the future");
			check(refused != null, "the refused jar is reported, not silently absent");
			if (refused != null) {
				check(refused.reason.indexOf("999") >= 0 && refused.reason.indexOf("newer client") >= 0,
					"...with a reason naming the level it wants: \"" + refused.reason + "\"");
				check(refused.source.endsWith("future.jar"),
					"...and the jar it came from (" + refused.source + ")");
			}

			// The other half: a jar that declares nothing and breaks anyway. It must come back
			// off, and the reason must say what it was looking for rather than leaving a
			// NoSuchMethodError in a log nobody reads.
			PluginManager.Entry linkage = entry(manager, "api.linkage");
			manager.setEnabled(linkage, true);
			check(!linkage.isEnabled(),
				"a plugin whose startUp hits a missing method does not come up enabled");
			// The count, not just the flag. start() clears enabled BEFORE unwinding so the
			// unwind does not decrement a counter that was never incremented; get that order
			// wrong and the count goes negative, idle() stops being true, and the client does
			// per-frame plugin work forever with nothing running. The flag alone cannot see it.
			check(runningCount(manager) == countEnabled(manager),
				"...and the running count still matches what is enabled ("
					+ runningCount(manager) + " of " + countEnabled(manager) + ")");

			check(entry(manager, "api.current") != null && entry(manager, "xp-drops") != null,
				"...and neither refusal took the plugins beside it down");

			// Reload is also the panel's Reload button, and a player can press it all day. The
			// refused list is rebuilt by it, not added to: without that, every press leaves
			// another copy of every refused plugin in the panel.
			int before = manager.getRefused().size();
			manager.reload();
			check(manager.getRefused().size() == before,
				"reloading rebuilds the refused list rather than adding to it ("
					+ before + " then " + manager.getRefused().size() + ")");
		} catch (Throwable error) {
			check(false, "the API level section ran without throwing (" + error + ")");
		} finally {
			for (int i = 0; i < copied.length; i++) {
				if (copied[i] != null) {
					copied[i].delete();
				}
			}
		}
	}

	/**
	 * The manager's private running count, which has no accessor and should not get one just for
	 * a test. Returns -1 if it cannot be read, which fails whatever is comparing it.
	 */
	static int runningCount(PluginManager manager) {
		try {
			java.lang.reflect.Field field = PluginManager.class.getDeclaredField("running");
			field.setAccessible(true);
			return ((Integer) field.get(manager)).intValue();
		} catch (Throwable error) {
			System.out.println("       " + error);
			return -1;
		}
	}

	static int countEnabled(PluginManager manager) {
		int count = 0;
		for (PluginManager.Entry entry : manager.getPlugins()) {
			if (entry.isEnabled()) {
				count++;
			}
		}
		return count;
	}

	static PluginManager.Refused refused(PluginManager manager, String name) {
		for (PluginManager.Refused one : manager.getRefused()) {
			if (one.name.equals(name)) {
				return one;
			}
		}
		return null;
	}

	static void copy(File from, File to) throws java.io.IOException {
		java.nio.file.Files.copy(from.toPath(), to.toPath(),
			java.nio.file.StandardCopyOption.REPLACE_EXISTING);
	}

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
