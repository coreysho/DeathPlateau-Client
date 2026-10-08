// Driven by tools/clienttests/run_npctest.py.
//
// ActorTest owns this plugin's PURE rules - matching, term colours, the exact-term arithmetic the
// Tag row is built on, the clamps - because they need no client at all and that suite deliberately
// has none. What is here is everything that does: the marks on screen, the colour on a menu row,
// the Tag row on the settings menu, and the notice when a marked npc appears. All of it through
// the real PluginManager, exactly as Client.java drives it.
package jagex2.client.plugin.builtin;

import java.util.ArrayList;
import java.util.List;

import jagex2.client.Client;
import jagex2.client.plugin.Overlay;
import jagex2.client.plugin.PluginApi;
import jagex2.client.plugin.PluginConfig;
import jagex2.client.plugin.PluginManager;
import jagex2.client.plugin.event.SettingsMenuOpening;
import jagex2.config.NpcType;
import jagex2.dash3d.ClientNpc;
import jagex2.dash3d.ClientPlayer;
import jagex2.graphics.Pix2D;
import jagex2.graphics.Pix3D;
import jagex2.graphics.PixFont;

/**
 * Headless test for the Npc indicators plugin.
 *
 * THE SCENE IS REAL. Npcs are pushed into the client's own npcs[]/npcIds[] the way its entity
 * handler fills them, with real NpcTypes behind them, and the marks are projected by the real
 * Client.projectFromGround against a heightmap and a camera set so one named tile lands dead
 * centre. That is what makes "the outline is on the npc's tile" an assertion about arithmetic the
 * client actually does rather than about a number the test chose.
 *
 * The font records and paints nothing, so a name tag is a recorded call and a tile outline is
 * pixels - which is the right way round: the tag is text and the outline is the only thing here
 * that has a shape.
 */
public class NpcIndicatorsTest {

	static final int W = 512;
	static final int H = 334;

	/** The tile the camera is pointed at, so a mark on it lands in the middle of the buffer. */
	static final int MID_X = 64;
	static final int MID_Z = 64;

	static final int GREEN = 0x00FF00;

	static int fails;
	static int passes;

	static Client client;
	static PluginManager manager;
	static PluginManager.Entry entry;
	static NpcIndicatorsPlugin plugin;
	static RecordingFont font;
	static int[] pixels = new int[W * H];

	static void check(boolean ok, String what) {
		if (ok) {
			passes++;
			System.out.println("  ok   " + what);
		} else {
			fails++;
			System.out.println("FAIL   " + what);
		}
	}

	public static void main(String[] args) {
		if (java.awt.GraphicsEnvironment.isHeadless()) {
			System.out.println("  SKIP  no display, so a Client cannot be constructed - run with xvfb-run");
			System.out.println("ALL PASS (skipped)");
			System.exit(0);
		}

		setUp();
		System.out.println("1. the settings, and what they are out of the box");
		settingTests();
		System.out.println("2. marks on screen");
		markTests();
		System.out.println("3. the right-click menu");
		menuTests();
		System.out.println("4. tagging with shift-right-click");
		tagTests();
		System.out.println("5. a marked npc appearing");
		appearTests();

		System.out.println();
		System.out.println(fails == 0 ? (passes + " CHECKS, ALL PASS")
			: (fails + " FAILED of " + (passes + fails)));
		System.exit(fails == 0 ? 0 : 1);
	}

	// ---------------------------------------------------------------- 1

	static void settingTests() {
		check(PluginApi.LEVEL >= 6, "colouring menu rows is API level 6");

		// READ BEFORE ANYTHING WRITES THEM.
		check(text("names").length() == 0,
			"THE LIST STARTS EMPTY, and an empty list marks nothing: a blank one that meant "
				+ "everything would outline the whole scene the first time anyone turned this on");
		check(text("colour").equals("00FF00"), "the marker colour starts green");
		check(bool("tile") && bool("tag"), "both marks are on");
		check(!bool("level"), "the combat level is off");
		check(text("tagPosition").equals(NpcIndicatorsPlugin.ABOVE), "the name sits above");
		check(text("tileStyle").equals(NpcIndicatorsPlugin.EVERY_TILE),
			"every tile a big npc stands on is outlined");
		check(number("borderWidth") == 1, "the outline is one pixel");
		check(!bool("textOutline"), "the text outline is off");
		check(number("maxDrawn") == NpcIndicatorsPlugin.DEFAULT_MAX_DRAWN, "thirty-two at once");
		check(!bool("menuColour") && !bool("notifyAppears"),
			"and the menu colouring and the notice are both off");

		String[] colours = { "colour" };
		for (int i = 0; i < colours.length; i++) {
			check(setting(colours[i]) != null && setting(colours[i]).isColour(),
				colours[i] + " is edited as a colour");
		}
		String[] numbers = { "borderWidth", "maxDrawn" };
		for (int i = 0; i < numbers.length; i++) {
			check(setting(numbers[i]) != null && setting(numbers[i]).isInt(),
				numbers[i] + " is a number a player types");
		}
		check(setting("names") != null && setting("names").isString(),
			"and the list is text, so it can be typed as well as tagged");

		// A drop-down whose entries no branch matches is a control that silently does nothing,
		// and the compiler cannot see it: both sides are strings.
		check(hasChoice("tileStyle", NpcIndicatorsPlugin.EVERY_TILE)
				&& hasChoice("tileStyle", NpcIndicatorsPlugin.ANCHOR_TILE),
			"both tile styles the code branches on are offered");
		check(hasChoice("tagPosition", NpcIndicatorsPlugin.ABOVE)
				&& hasChoice("tagPosition", NpcIndicatorsPlugin.AT_FEET),
			"and both tag positions");

		int count = entry.getConfig().getItems().size();
		check(read("plugins/README.md").indexOf("### Npc indicators, in detail") >= 0,
			"the README has a section for this plugin");
		check(read("plugins/README.md").indexOf("Twelve settings") >= 0 && count == 12,
			"...and says how many settings it has, which is " + count);
	}

	// ---------------------------------------------------------------- 2

	static void markTests() {
		restore();
		spawn("Goblin", 2, 1, MID_X, MID_Z);

		check(painted() == 0 && frame().isEmpty(),
			"with no names listed, nothing is marked - not a tile, not a tag");

		set("names", "goblin");
		check(painted() > 0, "naming it outlines its tile");
		List<Drawn> tags = frame();
		check(tags.size() == 1 && tags.get(0).text.equals("Goblin"), "...and tags it by name");
		check(tags.get(0).colour == GREEN, "...in the marker colour");

		// The colour setting, and then a term's own colour, which must win over it.
		set("colour", "FF0000");
		check(frame().get(0).colour == 0xFF0000, "a colour a player picks is what gets drawn");
		check(countPixels(0xFF0000) > 0, "...on the outline too, not only the tag");
		set("names", "goblin=0000FF");
		check(frame().get(0).colour == 0x0000FF,
			"and a term's own colour wins over the plugin's, so three marks can be told apart");
		check(countPixels(0x0000FF) > 0, "...on its outline as well");
		set("names", "goblin");
		set("colour", "00FF00");

		// The two marks, separately. Each off alone, and both off is the early return.
		set("tag", "false");
		check(painted() > 0 && frame().isEmpty(), "the tile alone outlines without tagging");
		set("tag", "true");
		set("tile", "false");
		check(frame().size() == 1 && countPixels(GREEN) == 0,
			"the tag alone names without outlining");
		set("tile", "true");

		// The combat level.
		set("level", "true");
		check(frame().get(0).text.equals("Goblin (level-2)"), "the level follows the name when on");
		set("level", "false");

		// THE TEXT OUTLINE. Four flat passes plus the coloured one.
		check(frame().size() == 1, "without the outline a tag is one draw");
		set("textOutline", "true");
		List<Drawn> outlined = frame();
		check(outlined.size() == 5, "with it, four black passes and the coloured one ("
			+ outlined.size() + ")");
		int black = 0;
		for (int i = 0; i < outlined.size(); i++) {
			if (outlined.get(i).colour == 0) {
				black++;
			}
		}
		check(black == 4, "...one each way, with the colour on top");
		set("textOutline", "false");

		// A BIG NPC stands on several tiles, and which of them are outlined is a choice. Counted
		// as pixels: a 3x3 ring is longer than a 1x1 one, which is the only thing that can tell
		// "every tile" from "the anchor" apart without reading the projection back.
		clearNpcs();
		spawn("Goblin", 2, 3, MID_X, MID_Z);
		int everyTile = countPixels(GREEN);
		set("tileStyle", NpcIndicatorsPlugin.ANCHOR_TILE);
		int anchorOnly = countPixels(GREEN);
		check(everyTile > anchorOnly,
			"a 3x3 npc outlined on every tile draws more than on its anchor alone ("
				+ everyTile + " vs " + anchorOnly + ")");
		set("tileStyle", NpcIndicatorsPlugin.EVERY_TILE);

		// THE BORDER WIDTH, as rings pulled toward each tile's centre.
		int thin = countPixels(GREEN);
		set("borderWidth", "3");
		int thick = countPixels(GREEN);
		check(thick > thin, "a thicker border draws more pixels (" + thin + " -> " + thick + ")");
		set("borderWidth", "1");
		check(countPixels(GREEN) == thin, "...and back to one is back to where it was");

		// THE CAP. A term matching a crowd is bounded, and the nearest are the ones kept, since
		// getNpcs sorts by distance and they are what a player is looking for.
		clearNpcs();
		for (int i = 0; i < 6; i++) {
			spawn("Goblin", 2, 1, MID_X + i, MID_Z);
		}
		check(frame().size() == 6, "six goblins, six tags (" + frame().size() + ")");
		set("maxDrawn", "2");
		check(frame().size() == 2, "a cap of two draws two");
		set("maxDrawn", "1");
		List<Drawn> nearest = frame();
		check(nearest.size() == 1, "...and one, one");
		set("maxDrawn", Integer.toString(NpcIndicatorsPlugin.DEFAULT_MAX_DRAWN));

		// An npc that is not named is not marked, even standing beside one that is.
		clearNpcs();
		spawn("Goblin", 2, 1, MID_X, MID_Z);
		spawn("Cow", 2, 1, MID_X + 1, MID_Z);
		List<Drawn> mixed = frame();
		check(mixed.size() == 1 && mixed.get(0).text.equals("Goblin"),
			"a cow beside a goblin is not marked with it");
	}

	// ---------------------------------------------------------------- 3

	static void menuTests() {
		restore();
		set("names", "goblin");

		menu(new String[] { "Walk here", "Attack @yel@Goblin", "Take @lre@Bones" },
			new int[] { Client.WALK_HERE_ACTION, 7, 684 });
		manager.onMenuBuilt(client.menuSize);
		check(colourAt(1) == 0 && colourAt(2) == 0 && colourAt(3) == 0,
			"with the setting off, no row is recoloured");

		set("menuColour", "true");
		menu(new String[] { "Walk here", "Attack @yel@Goblin", "Take @lre@Bones" },
			new int[] { Client.WALK_HERE_ACTION, 7, 684 });
		manager.onMenuBuilt(client.menuSize);
		check(colourAt(2) == GREEN, "the marked npc's row is drawn in its colour");
		check(colourAt(1) == 0, "...and Walk here is left alone");
		check(colourAt(3) == 0,
			"...AND SO IS THE GROUND ITEM ROW, which only the kind tag distinguishes: without "
				+ "that check a term like 'bones' would colour rows about items");

		// Every row about the npc, not just Attack: a player who marked something wants all of
		// its options to stand out.
		menu(new String[] { "Attack @yel@Goblin", "Examine @yel@Goblin" }, new int[] { 7, 1001 });
		manager.onMenuBuilt(client.menuSize);
		check(colourAt(1) == GREEN && colourAt(2) == GREEN, "every row about it is coloured");

		// A term's own colour reaches the menu too, so the floor and the menu agree.
		set("names", "goblin=FF00FF");
		menu(new String[] { "Attack @yel@Goblin" }, new int[] { 7 });
		manager.onMenuBuilt(client.menuSize);
		check(colourAt(1) == 0xFF00FF, "a term's own colour is what the row is drawn in");
		set("names", "goblin");

		// An npc nobody named.
		menu(new String[] { "Attack @yel@Cow" }, new int[] { 7 });
		manager.onMenuBuilt(client.menuSize);
		check(colourAt(1) == 0, "an npc nobody named keeps the white the client drew it in");

		// The combat level in the row does not stop a term matching it.
		menu(new String[] { "Attack @yel@Goblin@gr2@ (level-2)" }, new int[] { 7 });
		manager.onMenuBuilt(client.menuSize);
		check(colourAt(1) == GREEN, "a row carrying a combat level still matches its term");

		// NOTHING IS MOVED. Level 6 offers order as well as colour, and this uses only the
		// colour: moving an npc's Attack row changes what a click does.
		menu(new String[] { "Walk here", "Attack @yel@Goblin" },
			new int[] { Client.WALK_HERE_ACTION, 7 });
		String before = menuText();
		manager.onMenuBuilt(client.menuSize);
		check(menuText().equals(before), "and no row is moved, only coloured");
	}

	// ---------------------------------------------------------------- 4

	/**
	 * The Tag row, which is how a player will actually add an npc.
	 *
	 * Driven through the real SettingsMenuOpening, so what is checked is the row the client would
	 * put in front of them and what running it does to the setting the config box holds.
	 */
	static void tagTests() {
		restore();

		List<SettingsMenuOpening.Row> event = settingsMenu(target("yel", "Goblin"));
		check(event.size() == 1, "an npc under the cursor gets one row");
		check(event.get(0).label.equals("Tag @yel@Goblin"),
			"...which offers to tag it (" + event.get(0).label + ")");

		event.get(0).action.run();
		check(NpcIndicatorsPlugin.hasTerm(text("names"), "Goblin"),
			"running it adds the npc to the list");
		check(text("names").equals("Goblin"), "...as the whole list, there being nothing else");

		// AND IT PERSISTS. The row writes through the plugin's own config, so the setting the
		// sidebar shows and the one the plugin reads are the same thing - a tag that only lived
		// in the field would be gone on the next restart.
		check(setting("names").stringValue().equals("Goblin"),
			"...and the config box shows it, so a tag survives a restart");

		// The same npc now offers to untag.
		event = settingsMenu(target("yel", "Goblin"));
		check(event.get(0).label.equals("Untag @yel@Goblin"),
			"a tagged npc offers to untag (" + event.get(0).label + ")");
		event.get(0).action.run();
		check(!NpcIndicatorsPlugin.hasTerm(text("names"), "Goblin"), "and running that removes it");
		check(text("names").length() == 0, "leaving an empty list rather than a stray comma");

		// A SECOND NPC goes alongside the first rather than replacing it.
		settingsMenu(target("yel", "Goblin")).get(0).action.run();
		settingsMenu(target("yel", "Cow")).get(0).action.run();
		check(text("names").equals("Goblin, Cow"), "two tags make a list of two");
		settingsMenu(target("yel", "Goblin")).get(0).action.run();
		check(text("names").equals("Cow"), "and untagging one leaves the other");

		// SUBSTRING MATCHING MUST NOT LEAK INTO TAGGING. Goblin Guard is matched by the term
		// "Goblin", but it is not that term - so it offers to Tag, not to Untag, and tagging it
		// leaves both.
		set("names", "Goblin");
		event = settingsMenu(target("yel", "Goblin Guard"));
		check(event.get(0).label.equals("Tag @yel@Goblin Guard"),
			"an npc merely MATCHED by a term still offers to tag, because it is not that term");
		event.get(0).action.run();
		check(text("names").equals("Goblin, Goblin Guard"), "...and tagging it keeps both");
		settingsMenu(target("yel", "Goblin")).get(0).action.run();
		check(text("names").equals("Goblin Guard"),
			"and untagging Goblin does not take Goblin Guard with it");

		// ONLY NPCS. The settings menu offers targets of every kind, and a Tag row over a tree or
		// a ground item would be a row that does nothing.
		restore();
		check(settingsMenu(target("lre", "Bones")).isEmpty(),
			"a ground item gets no Tag row");
		check(settingsMenu(target("cya", "Tree")).isEmpty(), "...nor scenery");
		check(settingsMenu(target("whi", "Zezima")).isEmpty(), "...nor a player");
		check(settingsMenu(target("gre", "Button")).isEmpty(), "...nor an interface");

		// One row per npc, however many of its options the menu was offering.
		List<SettingsMenuOpening.Row> twice = settingsMenu(
			new SettingsMenuOpening.Target("yel", "Goblin", "Attack"),
			new SettingsMenuOpening.Target("yel", "Goblin", "Examine"));
		check(twice.size() == 1,
			"an npc offering two options still gets one Tag row (" + twice.size() + ")");
		List<SettingsMenuOpening.Row> two = settingsMenu(target("yel", "Goblin"), target("yel", "Cow"));
		check(two.size() == 2, "and two different npcs get one each");

		// A tag says so in chat, because a menu row that looks like nothing happened is the
		// commonest way a player decides a feature is broken.
		restore();
		int before = chatLines();
		settingsMenu(target("yel", "Goblin")).get(0).action.run();
		check(chatLines() > before, "tagging says so in chat");
		before = chatLines();
		settingsMenu(target("yel", "Goblin")).get(0).action.run();
		check(chatLines() > before, "and so does untagging");
	}

	// ---------------------------------------------------------------- 5

	/**
	 * "One you named has appeared", which is a difference between two ticks.
	 *
	 * BY NAME, NOT BY NPC: Actor carries the config id, which every Goblin shares, so nothing
	 * here could tell one Goblin from another - and a notice fired every time one of six wandered
	 * in and out of the scene would be noise rather than information.
	 */
	static void appearTests() {
		restore();
		set("names", "goblin");

		// THE FIRST TICK REPORTS NOTHING. Everything is new on a first scan, and a player who
		// just turned this on does not want the crowd they are standing in read out to them.
		set("notifyAppears", "true");
		spawn("Goblin", 2, 1, MID_X, MID_Z);
		sleep(COOLDOWN_MS);
		int before = chatLines();
		tick();
		check(chatLines() == before, "the first tick after turning it on says nothing");

		// Still nothing while it stays there.
		sleep(COOLDOWN_MS);
		before = chatLines();
		tick();
		check(chatLines() == before, "and nothing while it is still standing there");

		// GONE AND BACK IS AN APPEARANCE.
		clearNpcs();
		tick();
		sleep(COOLDOWN_MS);
		before = chatLines();
		spawn("Goblin", 2, 1, MID_X, MID_Z);
		tick();
		check(chatLines() > before, "one arriving where there was none says so");

		// An npc nobody named arriving says nothing.
		clearNpcs();
		tick();
		sleep(COOLDOWN_MS);
		before = chatLines();
		spawn("Cow", 2, 1, MID_X, MID_Z);
		tick();
		check(chatLines() == before, "an npc nobody named arriving says nothing");

		// With the setting off, nothing is said - and turning it on does not then announce the
		// scene, because what was seen is forgotten while it is off.
		clearNpcs();
		tick();
		set("notifyAppears", "false");
		spawn("Goblin", 2, 1, MID_X, MID_Z);
		sleep(COOLDOWN_MS);
		before = chatLines();
		tick();
		check(chatLines() == before, "with the setting off, an arrival says nothing");
		set("notifyAppears", "true");
		sleep(COOLDOWN_MS);
		before = chatLines();
		tick();
		check(chatLines() == before,
			"and turning it back on does not announce the npcs already standing there");

		// ...but the next real arrival does, so the forgetting is not a permanent silence.
		clearNpcs();
		tick();
		sleep(COOLDOWN_MS);
		before = chatLines();
		spawn("Goblin", 2, 1, MID_X, MID_Z);
		tick();
		check(chatLines() > before, "and the next real arrival still does");
	}

	// ---------------------------------------------------------------- the harness

	/** PluginContext rate-limits notifications to one every 1500ms, across every plugin. */
	static final long COOLDOWN_MS = 1600L;

	static void setUp() {
		client = new Client();
		client.levelHeightmap = new int[4][105][105];
		client.levelTileFlags = new byte[4][104][104];
		client.currentLevel = 0;
		client.cameraPitch = 0;
		client.cameraYaw = 0;
		client.cameraX = MID_X * 128 + 64;
		client.cameraZ = MID_Z * 128 + 64 - 1024;
		client.cameraY = -24;
		client.ingame = true;

		Client.localPlayer = new ClientPlayer();
		Client.localPlayer.field1157 = MID_X * 128;
		Client.localPlayer.field1158 = MID_Z * 128;

		Pix3D.zoom = 512;
		Pix3D.centerX = W / 2;
		Pix3D.centerY = H / 2;

		font = new RecordingFont();
		manager = new PluginManager(client, font, font, font);
		manager.reload();

		List<PluginManager.Entry> entries = manager.getPlugins();
		for (int i = 0; i < entries.size(); i++) {
			PluginManager.Entry e = entries.get(i);
			if ("npc-indicators".equals(e.key)) {
				entry = e;
			} else if (e.isEnabled()) {
				// Everything else off, so a mark, a pixel, a menu colour or a chat line can only
				// have come from this plugin.
				manager.setEnabled(e, false);
			}
		}
		check(entry != null, "the Npc indicators plugin is there");
		if (entry == null) {
			System.out.println("1 FAILED");
			System.exit(1);
		}
		// It ships off, being an addition rather than a client feature that became a plugin.
		check(!entry.isEnabled(), "...and ships off, the way every addition does");
		manager.setEnabled(entry, true);
		check(entry.isEnabled(), "...and can be turned on");
	}

	/** Every setting back to its default, with no npcs in the scene and nothing listed. */
	static void restore() {
		set("names", "");
		set("colour", "00FF00");
		set("tile", "true");
		set("tag", "true");
		set("level", "false");
		set("tagPosition", NpcIndicatorsPlugin.ABOVE);
		set("tileStyle", NpcIndicatorsPlugin.EVERY_TILE);
		set("borderWidth", "1");
		set("textOutline", "false");
		set("maxDrawn", Integer.toString(NpcIndicatorsPlugin.DEFAULT_MAX_DRAWN));
		set("menuColour", "false");
		set("notifyAppears", "false");
		clearNpcs();
		tick();
	}

	/** An npc in the scene, the way the client's entity handler puts one there. */
	static void spawn(String name, int combatLevel, int size, int tileX, int tileZ) {
		NpcType type = new NpcType();
		type.field1455 = name;
		type.field1442 = combatLevel;
		type.field1431 = nextId++;
		ClientNpc npc = new ClientNpc();
		npc.field1370 = type;
		npc.field1148 = size;
		npc.field1157 = tileX * 128 + size * 64;
		npc.field1158 = tileZ * 128 + size * 64;
		int slot = client.npcCount;
		client.npcs[slot] = npc;
		client.npcIds[slot] = slot;
		client.npcCount = slot + 1;
	}

	static int nextId = 100;

	static void clearNpcs() {
		for (int i = 0; i < client.npcCount; i++) {
			client.npcs[client.npcIds[i]] = null;
		}
		client.npcCount = 0;
	}

	/** One game tick through the real manager, which is where the appearance scan runs. */
	static void tick() {
		manager.onClientTick(ticks++);
		manager.onGameTick();
	}

	static int ticks = 1;

	/** One frame, into a cleared buffer with a cleared font. Both accumulate. */
	static List<Drawn> frame() {
		font.rows.clear();
		java.util.Arrays.fill(pixels, 0);
		Pix2D.bind(W, H, pixels);
		manager.renderOverlays(W, H, Overlay.LAYER_SCENE);
		// A COPY: the next frame clears and refills the font's own list, so a held reference
		// would describe the later frame and a comparison between two could never fail.
		return new ArrayList<Drawn>(font.rows);
	}

	static int painted() {
		frame();
		int n = 0;
		for (int i = 0; i < pixels.length; i++) {
			if (pixels[i] != 0) {
				n++;
			}
		}
		return n;
	}

	static int countPixels(int colour) {
		frame();
		int n = 0;
		for (int i = 0; i < pixels.length; i++) {
			if ((pixels[i] & 0xFFFFFF) == colour) {
				n++;
			}
		}
		return n;
	}

	/** A menu the way the client builds one: Cancel at 0, the rest appended above it. */
	static void menu(String[] options, int[] actions) {
		for (int i = 0; i < client.menuColour.length; i++) {
			client.menuColour[i] = 0;
		}
		client.menuOption[0] = "Cancel";
		client.menuAction[0] = 1016;
		client.menuSize = 1;
		for (int i = 0; i < options.length; i++) {
			client.menuOption[client.menuSize] = options[i];
			client.menuAction[client.menuSize] = actions[i];
			client.menuSize++;
		}
	}

	static int colourAt(int index) {
		return client.menuColour[index];
	}

	static String menuText() {
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < client.menuSize; i++) {
			if (i > 0) {
				out.append('|');
			}
			out.append(jagex2.client.DevLog.stripTags(client.menuOption[i]));
		}
		return out.toString();
	}

	static SettingsMenuOpening.Target target(String kind, String name) {
		return new SettingsMenuOpening.Target(kind, name, "Attack");
	}

	/**
	 * A shift-right-click, through the real manager, with these things under the cursor.
	 *
	 * Returns the rows the plugins added, which is what the client takes back and builds the
	 * menu from - so a check on them is a check on what a player would see.
	 */
	static List<SettingsMenuOpening.Row> settingsMenu(SettingsMenuOpening.Target... targets) {
		List<SettingsMenuOpening.Target> list = new ArrayList<SettingsMenuOpening.Target>();
		for (int i = 0; i < targets.length; i++) {
			list.add(targets[i]);
		}
		return manager.onSettingsMenuOpening(list, true, 32);
	}

	static int chatLines() {
		int n = 0;
		for (int i = 0; i < client.messageText.length; i++) {
			if (client.messageText[i] != null) {
				n++;
			}
		}
		return n;
	}

	static void set(String key, String value) {
		List<PluginConfig.Item> items = entry.getConfig().getItems();
		for (int i = 0; i < items.size(); i++) {
			if (items.get(i).key.equals(key)) {
				check(entry.getConfig().set(items.get(i), value),
					"setting " + key + " to \"" + value + "\" is accepted");
				return;
			}
		}
		check(false, "there is a setting called " + key);
	}

	static PluginConfig.Item setting(String key) {
		List<PluginConfig.Item> items = entry.getConfig().getItems();
		for (int i = 0; i < items.size(); i++) {
			if (items.get(i).key.equals(key)) {
				return items.get(i);
			}
		}
		return null;
	}

	static boolean bool(String key) {
		PluginConfig.Item item = setting(key);
		return item != null && item.booleanValue();
	}

	static int number(String key) {
		PluginConfig.Item item = setting(key);
		return item == null ? -1 : item.intValue();
	}

	static String text(String key) {
		PluginConfig.Item item = setting(key);
		return item == null ? "" : item.stringValue();
	}

	static boolean hasChoice(String key, String choice) {
		PluginConfig.Item item = setting(key);
		if (item == null) {
			return false;
		}
		String[] choices = item.choices();
		for (int i = 0; i < choices.length; i++) {
			if (choices[i].equals(choice)) {
				return true;
			}
		}
		return false;
	}

	static void sleep(long ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
		}
	}

	static String read(String path) {
		try {
			return new String(java.nio.file.Files.readAllBytes(
				new java.io.File(System.getProperty("dp.root", "."), path).toPath()), "UTF-8");
		} catch (Throwable missing) {
			check(false, "cannot read " + path + " (" + missing + ")");
			return "";
		}
	}

	/** One drawn string, as the font saw it. */
	static final class Drawn {

		final int x;
		final int y;
		final int colour;
		final String text;

		Drawn(int x, int y, int colour, String text) {
			this.x = x;
			this.y = y;
			this.colour = colour;
			this.text = text;
		}

		public String toString() {
			return text + "@" + y;
		}
	}

	/** Paints nothing and records every call, because recording it IS the measurement. */
	static final class RecordingFont extends PixFont {

		static final int ADVANCE = 4;

		final List<Drawn> rows = new ArrayList<Drawn>();

		RecordingFont() {
			this.height = 12;
			java.util.Arrays.fill(this.charAdvance, ADVANCE);
		}

		int widthOf(String text) {
			return text == null ? 0 : text.length() * ADVANCE;
		}

		public int stringWid(String text) {
			return this.widthOf(text);
		}

		public int stringWidTag(String text) {
			return this.widthOf(text);
		}

		public void drawStringTag(int colour, int x, int y, boolean shadow, String text) {
			this.rows.add(new Drawn(x, y, colour, text));
		}

		public void drawString(int x, int colour, int y, String text) {
			this.rows.add(new Drawn(x, y, colour, text));
		}
	}
}
