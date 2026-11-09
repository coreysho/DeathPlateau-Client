// Driven by tools/clienttests/run_questhelpertest.py.
//
// THE QUEST THE PLAYER PICKED, AND THE STEP IN FRONT OF THEM. Everything here is about the one
// question the plugin exists to answer - "what do I do next, and where is it" - asked against the
// real step table that ships in the client, a real scene, and the real PluginManager.
//
// WHY THIS SUITE EXISTS. The marker used to point at the FIRST step of the leg the quest varp
// named. A leg nearly always opens with a walk, a walk names no npc, so nothing was highlighted
// until the walk was behind you and the varp had already moved on: pick The Feud, stand in front
// of Ali Morrisane, and Ali lit up only after you had talked to him. That is a bug about what is
// standing in the scene, so it cannot be tested without one.
package jagex2.client.plugin.builtin;

import java.util.ArrayList;
import java.util.List;

import jagex2.client.Client;
import jagex2.client.plugin.ConfigList;
import jagex2.client.plugin.Overlay;
import jagex2.client.plugin.PluginContext;
import jagex2.client.plugin.PluginManager;
import jagex2.config.NpcType;
import jagex2.dash3d.ClientNpc;
import jagex2.dash3d.ClientPlayer;
import jagex2.graphics.Pix2D;
import jagex2.graphics.Pix3D;
import jagex2.graphics.PixFont;

public class QuestHelperTest {

	static final int W = 512;
	static final int H = 334;

	/** The tile the camera points at, so a marker on it lands in the middle of the buffer. */
	static final int MID_X = 64;
	static final int MID_Z = 64;

	/** The Feud: its progress varp, and the first leg's two steps as the table records them. */
	static final String FEUD = "THE FEUD";
	static final int FEUD_VARP = 334;
	static final int ALI = 1862;
	static final String ALI_NAME = "Ali Morrisane";
	static final int BAZAAR_X = 3304;
	static final int BAZAAR_Z = 3213;

	/** The Restless Ghost, whose progress is a plain varp rather than a varbit. */
	static final String GHOST = "THE RESTLESS GHOST";
	static final int GHOST_VARP = 107;

	/**
	 * In Search of the Myreque, whose fifth leg is five people to talk to and nothing else.
	 *
	 * A leg with no walk in it and several npcs is the only way to ask WHICH of them the plugin
	 * picks: the second one is the second thing to do whether or not the fifth is closer.
	 */
	static final String MYREQUE = "IN SEARCH OF THE MYREQUE";
	static final int MYREQUE_VARP = 387;
	static final int RADIGAD = 1572;
	static final String RADIGAD_NAME = "Radigad Ponfit";
	static final int IVAN = 1574;
	static final String IVAN_NAME = "Ivan Strom";

	/**
	 * Shilo Village, whose first leg walks to two places 135 tiles apart.
	 *
	 * A scene is 104 tiles, so it cannot hold both: standing at one of them the other is not
	 * somewhere the marker can point, which is the only way to ask whether a tile the scene has
	 * not loaded is passed over or simply drawn at nothing.
	 */
	static final String SHILO = "SHILO VILLAGE";
	static final int SHILO_VARP = 116;
	static final int MOSOL_X = 2883;
	static final int MOSOL_Z = 2952;
	static final int TRUFITUS_X = 2809;
	static final int TRUFITUS_Z = 3087;

	/**
	 * Elemental Workshop I: the whole quest in ONE leg, under eleven headings.
	 *
	 * Its varp only moves when the quest ends, so there is no leg boundary to hang the guide's
	 * wording on - which is why a heading carries the step it is written in front of.
	 */
	static final String ELEMENTAL = "ELEMENTAL WORKSHOP I";
	static final int EARTH_ELEMENTAL = 1023;
	static final int GHOSTSPEAK = 552;

	/** Long enough for the plugin's fifth-of-a-second cache to expire between two scenes. */
	static final long SETTLE = 250L;

	/**
	 * The component the server transmits the inventory to, written down rather than read out of
	 * the client: `3214=inventory:inv` in the content's interface.pack. GameStateTest owns the
	 * check that PluginContext agrees with it - a fixture that followed the code instead would
	 * build its inventory at whatever the code said and could never disagree with it.
	 */
	static final int INVENTORY_COM = 3214;

	static int fails;
	static int passes;

	static Client client;
	static PluginManager manager;
	static PluginManager.Entry entry;
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

	public static void main(String[] args) throws Exception {
		if (java.awt.GraphicsEnvironment.isHeadless()) {
			System.out.println("  SKIP  no display, so a Client cannot be constructed - run with xvfb-run");
			System.out.println("ALL PASS (skipped)");
			System.exit(0);
		}

		setUp();
		System.out.println("1. the table that ships with the client");
		tableTests();
		System.out.println("2. nothing is tracked until the player picks");
		pickTests();
		System.out.println("3. the step in front of you is the one that is marked");
		markTests();
		System.out.println("4. a tile the scene has not loaded");
		rangeTests();
		System.out.println("5. the leg follows the varp");
		progressTests();
		System.out.println("6. which of the leg's people is the one to talk to");
		orderTests();
		System.out.println("7. the guide's wording follows the step, inside one leg");
		headingTests();

		System.out.println();
		System.out.println(fails == 0 ? (passes + " CHECKS, ALL PASS") : (fails + " FAILED of " + (passes + fails)));
		System.exit(fails == 0 ? 0 : 1);
	}

	// ---------------------------------------------------------------- 1

	static void tableTests() {
		List<ConfigList.Row> rows = page();
		check(rows.size() > 100, "the quest list has every quest in it (" + rows.size() + ")");
		check(row(rows, FEUD) != null, "The Feud is one of them");
		ConfigList.Row feud = row(rows, FEUD);
		check(feud != null && "Track".equals(feud.action), "...offered with a Track button");
		check(feud != null && "not started".equals(feud.detail),
			"...and reading as not started, which is what varp " + FEUD_VARP + " says");
	}

	// ---------------------------------------------------------------- 2

	static void pickTests() throws Exception {
		spawn(ALI_NAME, ALI, MID_X, MID_Z);
		// A QUEST ALREADY IN PROGRESS, which is the state the guess used to fire in: with every
		// varp at zero there is nothing to guess and the check below would pass either way.
		client.varps[GHOST_VARP] = 2;
		settle();

		// NOTHING IS GUESSED. Picking the first started-and-unfinished quest in table order meant
		// a player part-way through six quests was handed whichever sorted earliest.
		check(scene().isEmpty() && screen().isEmpty(),
			"with a quest in progress but none picked, nothing is drawn - no marker and no panel");
		client.varps[GHOST_VARP] = 0;

		track(FEUD);
		check(!screen().isEmpty(), "picking The Feud puts the panel on screen");
		List<ConfigList.Row> rows = page();
		check(rows.size() > 0 && FEUD.equals(rows.get(0).label),
			"...and the page turns to that quest, with its name at the top");
		check(rows.size() > 1 && "Change".equals(rows.get(0).action),
			"...with a Change button to go back to the list");
	}

	// ---------------------------------------------------------------- 3

	static void markTests() throws Exception {
		settle();

		// THE BUG, as a check. Ali is the SECOND step of the first leg; the first is the walk to
		// the bazaar, which names nobody. Standing in front of him, he is what there is to do.
		check(said(scene(), ALI_NAME), "standing in front of Ali Morrisane, the marker names him");
		check(painted() > 0, "...and draws a box on his tile");
		check(said(screen(), "Talk to Ali Morrisane"),
			"...while the panel says to talk to him, not to walk to the bazaar he is standing in");

		// And the row the sidebar calls the live one is the same step.
		List<ConfigList.Row> rows = page();
		ConfigList.Row live = null;
		for (ConfigList.Row row : rows) {
			if ("now".equals(row.value)) {
				live = row;
			}
		}
		check(live != null && live.label.indexOf(ALI_NAME) >= 0,
			"...and the sidebar marks that same step as the one to do now");

		// With Ali out of the scene the leg's own first instruction stands, on the tile it names.
		clearNpcs();
		settle();
		check(!said(scene(), ALI_NAME), "with Ali gone from the scene, he is no longer named");
		check(painted() > 0, "...but the walk's own tile is marked, because the scene has it");
		check(said(screen(), "Go to the bazaar"), "...and the panel gives the walk instead");
	}

	// ---------------------------------------------------------------- 4

	static void rangeTests() throws Exception {
		// A WORLD TILE, THROUGH THE REAL CONVERSION. The table records where a step happens in
		// world coordinates; the scene is 104 tiles of wherever you are standing. Walking far
		// enough away has to end the marker rather than pin it to the edge of the screen, which
		// would be a guess at a direction.
		final PluginContext ctx = context();
		check(ctx.sceneToWorldX(ctx.worldToSceneX(BAZAAR_X)) == BAZAAR_X
				&& ctx.sceneToWorldZ(ctx.worldToSceneZ(BAZAAR_Z)) == BAZAAR_Z,
			"a world tile survives the trip into scene coordinates and back");

		int wasX = client.sceneBaseTileX;
		int wasZ = client.sceneBaseTileZ;
		client.sceneBaseTileX = BAZAAR_X + 500;
		client.sceneBaseTileZ = BAZAAR_Z + 500;
		settle();
		check(painted() == 0 && scene().isEmpty(),
			"with the bazaar outside the loaded scene, nothing is drawn in the world");
		check(said(screen(), "Go to the bazaar"),
			"...though the panel still says where to go, which is the part that still helps");

		// JUST off the edge, which is the case that has to be refused rather than survived: the
		// projection indexes the heightmap with whatever it is given, so a tile three west of the
		// scene is an array index of -3 and takes the whole plugin down with it.
		client.sceneBaseTileX = BAZAAR_X + 3;
		client.sceneBaseTileZ = BAZAAR_Z + 3;
		settle();
		check(painted() == 0 && scene().isEmpty(), "a tile just off the edge is not marked either");
		check(said(screen(), "Go to the bazaar"),
			"...and the plugin is still running, rather than switched off by a thrown frame");

		// A LEG WITH ONE TILE ON THE SCENE AND ONE OFF IT. Shilo Village opens by walking to Mosol
		// Rei and then to Trufitus, 135 tiles apart. Standing at Trufitus, the first of those is
		// not a place this scene can point at, so the second is the step to give - and a plugin
		// that took the first tile regardless would say "Go to Mosol Rei" and draw nothing.
		change();
		track(SHILO);
		client.sceneBaseTileX = TRUFITUS_X - MID_X;
		client.sceneBaseTileZ = TRUFITUS_Z - MID_Z;
		settle();
		check(!ctx.isInScene(ctx.worldToSceneX(MOSOL_X), ctx.worldToSceneZ(MOSOL_Z)),
			"standing at Trufitus, Mosol Rei's tile is outside the loaded scene");
		check(said(screen(), "Go to Trufitus"),
			"...so the leg's second walk is the one given, not its first");
		check(painted() > 0, "...and it is marked, because that tile is one the scene has");

		client.sceneBaseTileX = wasX;
		client.sceneBaseTileZ = wasZ;
		settle();
		change();
		track(FEUD);
		settle();
	}

	// ---------------------------------------------------------------- 5

	static void progressTests() throws Exception {
		// A QUEST WHOSE PROGRESS IS A PLAIN VARP. The Feud's is a varbit, and a varbit is a range
		// of bits inside a parent varp described by the cache - which this harness has none of, so
		// it reads zero whatever is written. 78 of the 106 quests use a varp; The Restless Ghost
		// is one, and its first three legs are three different places.
		change();
		track(GHOST);
		settle();
		check(said(screen(), "Talk to Father Aereck"),
			"a fresh quest opens on its first step, which is the priest in Lumbridge church");

		client.varps[GHOST_VARP] = 2;
		settle();
		check(said(screen(), "need Ghostspeak amulet"),
			"writing the quest's varp moves the panel to the leg that varp names, and that leg "
				+ "wants an amulet this player has not got");
		check(said(screen(), "Go back north out of the swamp"),
			"...so the walk leads, because wearing an amulet you do not have is not a step");

		// THE SAME LEG WITH THE AMULET IN THE BAG. Wearing it is then something that can be done
		// standing still, and it is what the leg opens with.
		carry(GHOSTSPEAK, 1);
		settle();
		check(said(screen(), "Wear Ghostspeak amulet"),
			"carrying the amulet, the panel says to put it on");
		// LOOKING AT THE CORNER OF THE SCENE, because a step with no tile is a Spot whose tile
		// fields are whatever they were left as, and 0,0 is a real tile that would be marked.
		int cameraX = client.cameraX;
		int cameraZ = client.cameraZ;
		client.cameraX = 64;
		client.cameraZ = 64 - 1024;
		check(painted() == 0 && scene().isEmpty(),
			"...and marks nothing in the world, because an inventory step happens where you stand");
		client.cameraX = cameraX;
		client.cameraZ = cameraZ;
		carry(GHOSTSPEAK, 0);
		settle();
		check(said(screen(), "Go back north out of the swamp"),
			"and once it is out of the inventory - which is what wearing it does - the leg moves on");

		client.varps[GHOST_VARP] = 0;
		settle();
		check(said(screen(), "Talk to Father Aereck"), "and back to the first leg when it is put back");

		// A finished quest is not a quest with a step list showing.
		client.varps[GHOST_VARP] = 5;
		settle();
		List<ConfigList.Row> rows = page();
		check(rows.size() > 0 && "complete".equals(rows.get(0).detail.split(" - ")[0]),
			"a quest whose varp has reached its end reads as complete");
		client.varps[GHOST_VARP] = 0;
		settle();
	}

	// ---------------------------------------------------------------- 6

	static void orderTests() throws Exception {
		clearNpcs();
		change();
		track(MYREQUE);
		client.varps[MYREQUE_VARP] = 4;

		// FIVE PEOPLE, NO WALK. The leg is "talk to Harold, Radigad, Polmafi, Sani, Ivan" in that
		// order. Two of them are here, and one of those is nearer - along with somebody who is in
		// no step at all, standing nearest of the three.
		spawn("Man", 1, MID_X, MID_Z);
		spawn(IVAN_NAME, IVAN, MID_X + 2, MID_Z);
		spawn(RADIGAD_NAME, RADIGAD, MID_X + 4, MID_Z);
		settle();

		check(said(scene(), RADIGAD_NAME),
			"of two of the leg's people in the scene, the earlier in the leg is the one marked");
		check(!said(scene(), IVAN_NAME), "...and not the one further down the list");
		check(!said(scene(), "Man"), "...and not the passer-by who is in no step of it");
		check(said(screen(), "Talk to " + RADIGAD_NAME), "...with the panel naming the same person");

		clearNpcs();
		client.varps[MYREQUE_VARP] = 0;
		settle();
	}

	// ---------------------------------------------------------------- 7

	static void headingTests() throws Exception {
		clearNpcs();
		change();
		track(ELEMENTAL);
		settle();

		// Nothing of this quest is in this scene, so the leg's first step leads and the heading
		// is the one written above it.
		check(said(screen(), "The bookcase on the east wall"),
			"a leg's first step is shown under the heading written above it");

		// The elemental is step 28 of the leg, five headings further down. Standing in front of
		// it, the panel has to move the heading along with the step it is naming - this quest has
		// no second leg to do it for us.
		spawn("Earth elemental", EARTH_ELEMENTAL, MID_X, MID_Z);
		settle();
		check(said(screen(), "Kill Earth elemental"), "and a step from the middle of the leg leads when it is in front of you");
		check(said(screen(), "An elemental rock in the western cavern"),
			"...under ITS heading, five headings down the same leg");
		check(!said(screen(), "The bookcase on the east wall"),
			"...and not under the one the leg opened with");

		clearNpcs();
		change();
		track(FEUD);
		settle();
	}

	// ---------------------------------------------------------------- the client under it

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
		// The scene is placed so that the bazaar tile the table records for The Feud's first walk
		// is the middle of it, which is also where the camera points.
		client.sceneBaseTileX = BAZAAR_X - MID_X;
		client.sceneBaseTileZ = BAZAAR_Z - MID_Z;

		Client.localPlayer = new ClientPlayer();
		Client.localPlayer.field1157 = MID_X * 128;
		Client.localPlayer.field1158 = MID_Z * 128;

		// An inventory for countObj to read: one component, the one the server fills.
		jagex2.config.Component.types = new jagex2.config.Component[INVENTORY_COM + 1];
		jagex2.config.Component.data = new byte[INVENTORY_COM + 1][];
		jagex2.config.Component inv = new jagex2.config.Component();
		inv.type = 2;
		inv.width = 4;
		inv.height = 7;
		inv.invSlotObjId = new int[28];
		inv.invSlotObjCount = new int[28];
		jagex2.config.Component.types[INVENTORY_COM] = inv;

		Pix3D.zoom = 512;
		Pix3D.centerX = W / 2;
		Pix3D.centerY = H / 2;

		font = new RecordingFont();
		manager = new PluginManager(client, font, font, font);
		manager.reload();

		for (PluginManager.Entry e : manager.getPlugins()) {
			if ("questhelper".equals(e.key)) {
				entry = e;
			} else if (e.isEnabled()) {
				// Everything else off, so a marker, a pixel or a panel line can only be this one.
				manager.setEnabled(e, false);
			}
		}
		check(entry != null, "the Quest helper plugin is there");
		if (entry == null) {
			System.out.println("1 FAILED");
			System.exit(1);
		}
		manager.setEnabled(entry, true);
		manager.onClientTick(0);
	}

	/** The plugin's sidebar page as the sidebar reads it. */
	static List<ConfigList.Row> page() {
		for (PluginManager.PanelSnapshot panel : manager.snapshotPanels()) {
			if ("questhelper".equals(panel.entry.key)) {
				return panel.rows;
			}
		}
		return new ArrayList<ConfigList.Row>();
	}

	static ConfigList.Row row(List<ConfigList.Row> rows, String label) {
		for (ConfigList.Row row : rows) {
			if (label.equals(row.label)) {
				return row;
			}
		}
		return null;
	}

	/** Presses Change on the tracked quest's row, which puts the page back to the list. */
	static void change() {
		for (PluginManager.PanelSnapshot panel : manager.snapshotPanels()) {
			if ("questhelper".equals(panel.entry.key) && !panel.rows.isEmpty()) {
				panel.act(0);
				return;
			}
		}
		throw new IllegalStateException("no page to change from");
	}

	/** Presses Track on a quest's row, which is the only way a quest becomes the tracked one. */
	static void track(String quest) {
		for (PluginManager.PanelSnapshot panel : manager.snapshotPanels()) {
			if (!"questhelper".equals(panel.entry.key)) {
				continue;
			}
			ConfigList.Row row = row(panel.rows, quest);
			if (row != null) {
				panel.act(row.index);
				return;
			}
		}
		throw new IllegalStateException("no row for " + quest);
	}

	/**
	 * Puts an obj in the player's inventory, where PluginContext reads it from.
	 *
	 * The inventory is a COMPONENT the server fills, not a field on the client, and ids are
	 * stored one higher than they are so that an empty slot can be zero.
	 */
	static void carry(int objId, int count) {
		jagex2.config.Component inv = jagex2.config.Component.types[INVENTORY_COM];
		inv.invSlotObjId[0] = count > 0 ? objId + 1 : 0;
		inv.invSlotObjCount[0] = count;
	}

	/** An npc in the scene, the way the client's entity handler puts one there. */
	static void spawn(String name, int id, int tileX, int tileZ) {
		NpcType type = new NpcType();
		type.field1455 = name;
		type.field1442 = 1;
		type.field1431 = id;
		ClientNpc npc = new ClientNpc();
		npc.field1370 = type;
		npc.field1148 = 1;
		npc.field1157 = tileX * 128 + 64;
		npc.field1158 = tileZ * 128 + 64;
		int slot = client.npcCount;
		client.npcs[slot] = npc;
		client.npcIds[slot] = slot;
		client.npcCount = slot + 1;
	}

	static void clearNpcs() {
		for (int i = 0; i < client.npcCount; i++) {
			client.npcs[client.npcIds[i]] = null;
		}
		client.npcCount = 0;
	}

	/**
	 * Waits out the plugin's cache.
	 *
	 * The marker looks the scene up once a fifth of a second rather than once a frame, because
	 * finding a loc means sweeping 104x104 tiles through four layers. A test that moved an npc
	 * and drew immediately would be reading the answer from before the move - which is also what
	 * a player sees for a fifth of a second, and is the one place this suite has to wait.
	 */
	static void settle() throws Exception {
		Thread.sleep(SETTLE);
		manager.onClientTick(ticks++);
	}

	static int ticks = 1;

	/** One scene-layer frame: what the marker drew. */
	static List<Drawn> scene() {
		return frame(Overlay.LAYER_SCENE);
	}

	/** One screen-layer frame: what the panel drew. */
	static List<Drawn> screen() {
		return frame(Overlay.LAYER_SCREEN);
	}

	static List<Drawn> frame(int layer) {
		font.rows.clear();
		java.util.Arrays.fill(pixels, 0);
		Pix2D.bind(W, H, pixels);
		manager.renderOverlays(W, H, layer);
		return new ArrayList<Drawn>(font.rows);
	}

	/** The pixels the marker left behind, which is the box rather than its label. */
	static int painted() {
		scene();
		int n = 0;
		for (int i = 0; i < pixels.length; i++) {
			if (pixels[i] != 0) {
				n++;
			}
		}
		return n;
	}

	static boolean said(List<Drawn> rows, String text) {
		for (Drawn row : rows) {
			if (strip(row.text).indexOf(text) >= 0) {
				return true;
			}
		}
		return false;
	}

	/** A drawn line without its colour tags, which the panel writes as @gre@ and @red@. */
	static String strip(String text) {
		return text == null ? "" : text.replaceAll("@[a-z]{3}@", "").trim();
	}

	static PluginContext context() {
		try {
			java.lang.reflect.Field field = PluginManager.class.getDeclaredField("ctx");
			field.setAccessible(true);
			return (PluginContext) field.get(manager);
		} catch (Throwable error) {
			throw new IllegalStateException("cannot reach the context: " + error);
		}
	}

	static final class Drawn {
		final int colour;
		final String text;

		Drawn(int colour, String text) {
			this.colour = colour;
			this.text = text;
		}
	}

	/** A font that paints nothing and remembers every line, so a label is a recorded call. */
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
			this.rows.add(new Drawn(colour, text));
		}

		public void drawString(int x, int colour, int y, String text) {
			this.rows.add(new Drawn(colour, text));
		}
	}
}
