// Driven by tools/clienttests/run_agilitytest.py.
//
// Two halves, like the other plugin suites. The first needs no client at all: the obstacle table
// and the plugin's own arithmetic are plain static methods. The second builds a scene by hand and
// checks what gets drawn on it.
//
// THE TABLE IS CHECKED AGAINST THE CONTENT, not against itself. A table generated from
// DeathPlateau-Content and then asserted to equal what it contains would pass whatever it held;
// the checks here are the handful of facts a reader of the scripts can confirm - the ids the
// gnome course binds, the level the hardest crevice asks for, the two things in the dungeon's
// script file that are not obstacles - plus the shape rules the whole table has to obey.
package jagex2.client.plugin.builtin;

import java.util.ArrayList;
import java.util.List;

import jagex2.client.Client;
import jagex2.client.plugin.Overlay;
import jagex2.client.plugin.PluginConfig;
import jagex2.client.plugin.PluginManager;
import jagex2.client.plugin.SceneLoc;
import jagex2.config.LocType;
import jagex2.dash3d.ClientPlayer;
import jagex2.dash3d.GroundDecor;
import jagex2.dash3d.Sprite;
import jagex2.dash3d.Square;
import jagex2.dash3d.Wall;
import jagex2.dash3d.World3D;
import jagex2.graphics.Pix2D;
import jagex2.graphics.Pix3D;
import jagex2.graphics.PixFont;

public final class AgilityTest {

	static final int W = 512;

	static final int H = 334;

	static final int MID_X = 50;

	static final int MID_Z = 50;

	/** Three obstacles the content names, one of each kind, used all through the scene half. */
	static final int GNOME_LOG = 2295;          // gnome_log_balance, no level

	static final int CREVICE = 9321;            // loc_9321, a shortcut at 81

	static final int DUNGEON_PIPE = 2290;       // loc_2290, the Yanille dungeon at 49

	/** An ordinary piece of scenery, for checking that the scan walks past it. */
	static final int NOT_AN_OBSTACLE = 1;

	static int fails;

	static Client client;

	static PluginManager manager;

	static PluginManager.Entry entry;

	static int[] pixels;

	static RecordingFont font;

	static void check(boolean ok, String what) {
		System.out.println((ok ? "  ok   " : "FAIL   ") + what);
		if (!ok) {
			fails++;
		}
	}

	public static void main(String[] args) {
		System.out.println("1. the obstacle table, against the content it came from");
		tableTests();
		System.out.println();
		System.out.println("2. the plugin's own arithmetic, and the level it needs");
		ruleTests();
		apiLevelTests();

		if (java.awt.GraphicsEnvironment.isHeadless()) {
			System.out.println("  SKIP  no display, so a Client cannot be constructed");
			System.out.println(fails == 0 ? "ALL PASS (the drawing sections were skipped)"
				: fails + " FAILED");
			System.exit(fails == 0 ? 0 : 1);
		}

		System.out.println();
		System.out.println("3. finding them in a scene");
		if (!setUp()) {
			System.out.println(fails + " FAILED");
			System.exit(1);
		}
		sceneTests();
		System.out.println();
		System.out.println("4. what gets drawn");
		drawTests();

		System.out.println();
		System.out.println(fails == 0 ? "ALL PASS" : fails + " FAILED");
		System.exit(fails == 0 ? 0 : 1);
	}

	// ---------------------------------------------------------------- 1

	static void tableTests() {
		check(AgilityObstacles.count() == 53,
			"the table holds every obstacle the agility scripts bind, which is 53 ("
				+ AgilityObstacles.count() + ")");

		// ANCHORS A READER OF THE CONTENT CAN CONFIRM. One per course, picked where the script
		// says something specific, so a regenerated table that quietly lost a file fails here.
		AgilityObstacles.Obstacle log = AgilityObstacles.byId(GNOME_LOG);
		check(log != null && log.course == AgilityObstacles.GNOME && log.level == 1
				&& "Log balance".equals(log.name),
			"gnome_log_balance is 2295, on the gnome course, asking for nothing");
		AgilityObstacles.Obstacle pipe = AgilityObstacles.byId(2287);
		check(pipe != null && pipe.course == AgilityObstacles.BARBARIAN && pipe.level == 35,
			"barbarian_obstacle_pipe is 2287 and asks for 35");
		AgilityObstacles.Obstacle door = AgilityObstacles.byId(2309);
		check(door != null && door.course == AgilityObstacles.WILDERNESS && door.level == 52,
			"the wilderness course's door is 2309 and asks for 52");
		AgilityObstacles.Obstacle crevice = AgilityObstacles.byId(CREVICE);
		check(crevice != null && crevice.course == AgilityObstacles.SHORTCUT
				&& crevice.level == 81,
			"loc_9321 is the 81 crevice, and a shortcut rather than a course");
		AgilityObstacles.Obstacle rubble = AgilityObstacles.byId(2317);
		check(rubble != null && rubble.course == AgilityObstacles.DUNGEON && rubble.level == 67,
			"climbingcaverocks1 is 2317, in the dungeon, asking for 67");

		// THE TWO THINGS IN THE DUNGEON'S SCRIPT THAT ARE NOT OBSTACLES. They are only in that
		// file at all because it is where Yanille's scripts live, and marking a chest and an
		// altar as agility obstacles would be the table believing its own source too literally.
		check(AgilityObstacles.byId(377) == null,
			"the locked chest in the dungeon's script file is not an obstacle");
		check(AgilityObstacles.byId(412) == null, "...and nor is the chaos altar");
		check(AgilityObstacles.byId(0) == null && AgilityObstacles.byId(-1) == null,
			"and an id nothing uses is not an obstacle either");

		// SHAPE RULES, which hold however the table is regenerated.
		List<AgilityObstacles.Obstacle> all = AgilityObstacles.all();
		boolean badLevel = false;
		boolean badCourse = false;
		boolean badName = false;
		for (int i = 0; i < all.size(); i++) {
			AgilityObstacles.Obstacle o = all.get(i);
			if (o.level < 1 || o.level > 99) {
				badLevel = true;
			}
			if (o.course < 0 || o.course >= AgilityObstacles.COURSES.length) {
				badCourse = true;
			}
			if (o.name == null || o.name.length() == 0) {
				badName = true;
			}
		}
		check(!badLevel, "every obstacle asks for a level between 1 and 99");
		check(!badCourse, "...belongs to a course the names list knows");
		check(!badName, "...and has a name to put on a label");

		int[] ids = AgilityObstacles.ids();
		check(ids.length == AgilityObstacles.count(), "one id per obstacle");
		boolean sorted = true;
		boolean distinct = true;
		for (int i = 1; i < ids.length; i++) {
			if (ids[i] < ids[i - 1]) {
				sorted = false;
			}
			if (ids[i] == ids[i - 1]) {
				distinct = false;
			}
		}
		check(sorted, "the ids come back sorted, which is what findLocs searches");
		check(distinct, "...and no id is in the table twice, or one obstacle would mask another");

		// A COPY, because findLocs sorts what it is given and a caller handing out its own array
		// once would have it reordered under it ever after.
		int[] first = AgilityObstacles.ids();
		first[0] = 999999;
		check(AgilityObstacles.ids()[0] != 999999,
			"ids() hands out a copy, so writing to one does not edit the table");

		check(AgilityObstacles.isCourse(AgilityObstacles.GNOME)
				&& AgilityObstacles.isCourse(AgilityObstacles.BARBARIAN)
				&& AgilityObstacles.isCourse(AgilityObstacles.WILDERNESS),
			"the three courses are courses");
		check(!AgilityObstacles.isCourse(AgilityObstacles.SHORTCUT)
				&& !AgilityObstacles.isCourse(AgilityObstacles.DUNGEON),
			"...and a shortcut and the dungeon are not");
	}

	/**
	 * The client's API level, pinned exactly.
	 *
	 * THE EXACT LEVEL LIVES IN THE NEWEST SUITE, which is this one: 9 added findLocs, and this
	 * is what it was added for. Every older suite asks only that its own level is still
	 * supported, so this pin is the single place that moves when the next addition does - it
	 * was MouseTest's while 8 was the newest.
	 */
	static void apiLevelTests() {
		check(jagex2.client.plugin.PluginApi.LEVEL == 9,
			"this client is API level 9 (" + jagex2.client.plugin.PluginApi.LEVEL + ")");
		check(jagex2.client.plugin.PluginApi.supports(9), "a plugin asking for 9 runs here");
		check(jagex2.client.plugin.PluginApi.supports(8), "...and one asking for 8 still does");
		check(!jagex2.client.plugin.PluginApi.supports(10), "one asking for 10 does not");
		check(read("src/main/java/jagex2/client/plugin/builtin/AgilityPlugin.java")
				.indexOf("apiLevel = 9") >= 0,
			"Agility declares the level it needs, which is 9 because it reads the scene");
	}

	/** A source file, for the checks that are about what the source says. */
	static String read(String path) {
		try {
			byte[] bytes = java.nio.file.Files.readAllBytes(
				java.nio.file.Paths.get(System.getProperty("dp.root", "."), path));
			return new String(bytes, "UTF-8");
		} catch (Throwable missing) {
			return "";
		}
	}

	// ---------------------------------------------------------------- 2

	static void ruleTests() {
		check(AgilityPlugin.borderFor(2) == 2, "a border in range is kept");
		check(AgilityPlugin.borderFor(0) == AgilityPlugin.MIN_BORDER,
			"a border of nothing is still a border, or the mark is invisible");
		check(AgilityPlugin.borderFor(99) == AgilityPlugin.MAX_BORDER,
			"and a thick one is bounded, or the outline is a block of colour");
		check(AgilityPlugin.fillFor(40) == 40, "a fill in range is kept");
		check(AgilityPlugin.fillFor(-5) == 0, "a negative fill is no fill");
		check(AgilityPlugin.fillFor(999) == AgilityPlugin.MAX_FILL,
			"and nothing is more than opaque");

		AgilityPlugin plugin = new AgilityPlugin();
		AgilityObstacles.Obstacle gnome = AgilityObstacles.byId(GNOME_LOG);
		AgilityObstacles.Obstacle crevice = AgilityObstacles.byId(CREVICE);
		AgilityObstacles.Obstacle dungeon = AgilityObstacles.byId(DUNGEON_PIPE);

		// WHAT A MARK SAYS.
		plugin.label = AgilityPlugin.LABEL_NONE;
		check(plugin.labelFor(crevice, false).length() == 0, "set to say nothing, it says nothing");
		plugin.label = AgilityPlugin.LABEL_NAME;
		check("Crevice".equals(plugin.labelFor(crevice, false)),
			"set to the name, it is the name the cache gives it ("
				+ plugin.labelFor(crevice, false) + ")");
		plugin.label = AgilityPlugin.LABEL_NAME_LEVEL;
		check("Crevice (81)".equals(plugin.labelFor(crevice, false)),
			"and with the level, the level it asks for (" + plugin.labelFor(crevice, false) + ")");
		// A LEVEL OF 1 IS NOT WORTH SAYING. Nothing on the gnome course asks for one, and
		// "(1)" on every obstacle of the beginners' course teaches a player to stop reading.
		check("Log balance".equals(plugin.labelFor(gnome, false)),
			"an obstacle that asks for nothing says no level, even set to show one ("
				+ plugin.labelFor(gnome, false) + ")");

		// WHICH COLOUR. Locked beats both of the others, because the thing a player most needs
		// to know about an obstacle is whether they can use it.
		plugin.courseColour = "111111";
		plugin.shortcutColour = "222222";
		plugin.lockedColour = "333333";
		check("111111".equals(plugin.colourFor(gnome, false)), "a course obstacle is the course colour");
		check("222222".equals(plugin.colourFor(crevice, false)), "a shortcut is the shortcut colour");
		check("333333".equals(plugin.colourFor(crevice, true)),
			"...and one your level cannot use is the locked colour instead");
		check("333333".equals(plugin.colourFor(gnome, true)),
			"which beats the course colour too, rather than only applying to shortcuts");

		// THE THREE SWITCHES, each against all three kinds.
		plugin.courses = true;
		plugin.shortcuts = true;
		plugin.dungeon = true;
		check(plugin.wanted(gnome) && plugin.wanted(crevice) && plugin.wanted(dungeon),
			"with everything on, everything is wanted");
		plugin.courses = false;
		check(!plugin.wanted(gnome) && plugin.wanted(crevice) && plugin.wanted(dungeon),
			"turning courses off leaves shortcuts and the dungeon");
		plugin.courses = true;
		plugin.shortcuts = false;
		check(plugin.wanted(gnome) && !plugin.wanted(crevice) && plugin.wanted(dungeon),
			"turning shortcuts off leaves courses and the dungeon");
		plugin.shortcuts = true;
		plugin.dungeon = false;
		check(plugin.wanted(gnome) && plugin.wanted(crevice) && !plugin.wanted(dungeon),
			"and turning the dungeon off leaves the other two");
	}

	// ---------------------------------------------------------------- the fixture

	static boolean setUp() {
		try {
			client = new Client();
		} catch (Throwable error) {
			check(false, "a client can be built (" + error + ")");
			return false;
		}
		client.levelHeightmap = new int[4][105][105];
		client.levelTileFlags = new byte[4][104][104];
		client.currentLevel = 0;
		client.ingame = true;
		client.sceneBaseTileX = 0;
		client.sceneBaseTileZ = 0;
		client.scene = newScene();

		Client.localPlayer = new ClientPlayer();
		Client.localPlayer.field1157 = MID_X * 128;
		Client.localPlayer.field1158 = MID_Z * 128;

		// A camera above and behind, so a tile projects to a shape rather than to a line.
		int yaw = 300;
		int away = 900;
		double bearing = yaw * 2.0 * Math.PI / 2048.0;
		client.cameraPitch = 320;
		client.cameraYaw = yaw;
		client.cameraX = MID_X * 128 + 64 + (int) (away * Math.sin(bearing));
		client.cameraZ = MID_Z * 128 + 64 - (int) (away * Math.cos(bearing));
		client.cameraY = -660;

		Pix3D.zoom = 420;
		Pix3D.centerX = W / 2;
		Pix3D.centerY = H / 2;

		pixels = new int[W * H];
		Pix2D.bind(W, H, pixels);
		font = new RecordingFont();
		manager = new PluginManager(client, font, font, font);
		manager.reload();

		List<PluginManager.Entry> entries = manager.getPlugins();
		for (int i = 0; i < entries.size(); i++) {
			PluginManager.Entry e = entries.get(i);
			if ("agility".equals(e.key)) {
				entry = e;
			} else if (e.isEnabled()) {
				// Everything else off, so a mark can only have come from this plugin.
				manager.setEnabled(e, false);
			}
		}
		check(entry != null, "Agility is one of the built-in plugins");
		if (entry == null) {
			return false;
		}
		check(!entry.isEnabled(),
			"...and it does not turn itself on, because an addition that does changes what "
				+ "every existing player sees on their next launch");
		manager.setEnabled(entry, true);
		// AFTER THE OTHER PLUGINS HAVE BEEN STARTED AND STOPPED, not before. Barrows doors
		// recolours its doors while a loc type is being decoded, and turning that on or off
		// has to forget every type already decoded - which it does by setting every slot's id
		// to -1. reload() starts it and this loop stops it again, so a cache primed any
		// earlier is emptied before the first check runs.
		primeLocTypes();
		return true;
	}

	/**
	 * Fills LocType's own twenty-slot cache by hand, so method561 answers without a cache file.
	 *
	 * The same trick GroundItemsTest plays on ObjType. method561 walks the twenty slots looking
	 * for the id before it decodes anything, so an entry put there is an entry it will return.
	 */
	static void primeLocTypes() {
		LocType.field1636 = new LocType[20];
		for (int i = 0; i < 20; i++) {
			LocType.field1636[i] = new LocType();
			LocType.field1636[i].field1627 = -1;
			LocType.field1636[i].field1655 = 1;
			LocType.field1636[i].field1629 = 1;
		}
		size(0, GNOME_LOG, 1, 1);
		size(1, CREVICE, 1, 1);
		size(2, DUNGEON_PIPE, 1, 1);
		// The ordinary scenery the scan has to walk past. Primed like the rest because
		// findLocs reads a type for anything it is ASKED for, and one check asks for this.
		size(3, NOT_AN_OBSTACLE, 1, 1);
	}

	/** One primed loc type: an id and the tiles it covers, unrotated. */
	static void size(int slot, int id, int width, int length) {
		LocType.field1636[slot].field1627 = id;
		LocType.field1636[slot].field1655 = width;
		LocType.field1636[slot].field1629 = length;
	}

	/**
	 * Puts a loc in the scene the way the map decode does: a bitfield on a square's slot.
	 *
	 * The scene keeps no list of locs, so there is nothing to add to - World3D.method300..303
	 * read these four fields and nothing else, and the id is the top of each bitfield. Built by
	 * hand rather than through World3D's own add methods because those want a ModelSource, and
	 * a model is the one thing none of this is about.
	 */
	static void putLoc(int layer, int tileX, int tileZ, int id, int rotation) {
		Square square = client.scene.field1018[0][tileX][tileZ];
		if (square == null) {
			square = new Square(0, tileX, tileZ);
			client.scene.field1018[0][tileX][tileZ] = square;
		}
		int packed = id << 14;
		if (layer == SceneLoc.LAYER_WALL) {
			Wall wall = new Wall();
			wall.field1539 = packed;
			wall.field1540 = (byte) (rotation << 6);
			square.field1386 = wall;
		} else if (layer == SceneLoc.LAYER_SCENERY) {
			Sprite sprite = new Sprite();
			sprite.field88 = packed | 2 << 29;
			sprite.field82 = tileX;
			sprite.field84 = tileZ;
			sprite.field89 = (byte) (rotation << 6);
			square.field1391[square.field1390] = sprite;
			square.field1390++;
		} else if (layer == SceneLoc.LAYER_GROUND_DECOR) {
			GroundDecor decor = new GroundDecor();
			decor.field1314 = packed;
			decor.field1315 = (byte) (rotation << 6);
			square.field1388 = decor;
		}
	}

	static void clearScene() {
		client.scene = newScene();
	}

	/**
	 * A scene the size the client makes one.
	 *
	 * THE CLIENT'S OWN ARGUMENT ORDER, copied rather than read off the parameter names: the
	 * constructor is (heightmap, 104, 4, 104) and allocates [arg2][arg3][arg1], so the middle
	 * argument is the level count and the two 104s are not interchangeable with it.
	 */
	static World3D newScene() {
		return new World3D(client.levelHeightmap, 104, 4, 104);
	}

	static jagex2.client.plugin.PluginContext context() {
		try {
			java.lang.reflect.Field f = PluginManager.class.getDeclaredField("ctx");
			f.setAccessible(true);
			return (jagex2.client.plugin.PluginContext) f.get(manager);
		} catch (Throwable error) {
			throw new IllegalStateException("cannot reach the context: " + error);
		}
	}

	// ---------------------------------------------------------------- 3

	static void sceneTests() {
		clearScene();
		check(context().findLocs(AgilityObstacles.ids()).isEmpty(),
			"an empty scene holds no obstacles");

		putLoc(SceneLoc.LAYER_SCENERY, MID_X, MID_Z, GNOME_LOG, 0);
		List<SceneLoc> found = context().findLocs(AgilityObstacles.ids());
		check(found.size() == 1, "one obstacle in the scene is found once (" + found.size() + ")");
		if (found.size() == 1) {
			SceneLoc loc = found.get(0);
			check(loc.id == GNOME_LOG, "...with the id it was placed under");
			check(loc.sceneTileX == MID_X && loc.sceneTileZ == MID_Z, "...on the tile it is on");
			check(loc.layer == SceneLoc.LAYER_SCENERY, "...on the layer it was put on");
		}

		// NOT EVERY LOC, only the ones asked for. A scene is mostly scenery, and a plugin that
		// marked all of it would be marking every tree in Gnome Stronghold.
		putLoc(SceneLoc.LAYER_SCENERY, MID_X + 2, MID_Z, NOT_AN_OBSTACLE, 0);
		check(context().findLocs(AgilityObstacles.ids()).size() == 1,
			"a loc that is not an obstacle is left where it is");
		check(context().findLocs(new int[] { NOT_AN_OBSTACLE }).size() == 1,
			"...and is found when it is the thing being asked for");
		check(context().findLocs(new int[0]).isEmpty(), "asking for nothing finds nothing");
		check(context().findLocs(null).isEmpty(), "...and so does asking for null");

		// ALL FOUR LAYERS. The obstacles alone include a gate (a wall), a stile (scenery) and a
		// strange floor (a ground decoration), so a scan of one layer would miss most of them.
		clearScene();
		putLoc(SceneLoc.LAYER_WALL, MID_X, MID_Z, GNOME_LOG, 0);
		check(context().findLocs(AgilityObstacles.ids()).size() == 1, "a wall obstacle is found");
		clearScene();
		putLoc(SceneLoc.LAYER_GROUND_DECOR, MID_X, MID_Z, GNOME_LOG, 0);
		check(context().findLocs(AgilityObstacles.ids()).size() == 1,
			"...and so is one on the ground");

		// SIZE, AND TURNED WITH THE LOC. A three-by-one obstacle laid north-south covers three
		// tiles of z, and a drawer handed the type's unrotated size outlines the wrong three.
		clearScene();
		size(0, GNOME_LOG, 3, 1);
		putLoc(SceneLoc.LAYER_SCENERY, MID_X, MID_Z, GNOME_LOG, 0);
		List<SceneLoc> flat = context().findLocs(AgilityObstacles.ids());
		check(flat.size() == 1 && flat.get(0).width == 3 && flat.get(0).length == 1,
			"a three-by-one obstacle is three wide unrotated");
		clearScene();
		putLoc(SceneLoc.LAYER_SCENERY, MID_X, MID_Z, GNOME_LOG, 1);
		List<SceneLoc> turned = context().findLocs(AgilityObstacles.ids());
		check(turned.size() == 1 && turned.get(0).width == 1 && turned.get(0).length == 3,
			"...and three LONG when it is placed on its side, which is the same loc turned");
		check(turned.size() == 1 && turned.get(0).rotation == 1, "...which it reports");
		size(0, GNOME_LOG, 1, 1);
		clearScene();
	}

	// ---------------------------------------------------------------- 4

	static void drawTests() {
		clearScene();
		set("courses", "true");
		set("shortcuts", "true");
		set("dungeon", "true");
		set("courseColour", "00FF00");
		set("shortcutColour", "00FFFF");
		set("lockedColour", "FF0000");
		set("hideLocked", "false");
		set("label", AgilityPlugin.LABEL_NONE);
		set("fill", "false");
		set("borderWidth", "1");
		levelAll(99);

		// NOTHING BEFORE A TICK. The scan runs on the tick, so a frame drawn before the first
		// one has nothing to draw - and a plugin that scanned per frame would be walking the
		// whole grid fifty times a second for an answer that changes once every six hundred ms.
		putLoc(SceneLoc.LAYER_SCENERY, MID_X, MID_Z, GNOME_LOG, 0);
		frame();
		check(countPixels(0x00FF00) == 0, "nothing is marked before the first tick");
		manager.onGameTick();
		frame();
		int marked = countPixels(0x00FF00);
		check(marked > 0, "after a tick the obstacle is outlined (" + marked + " pixels)");

		// IN THE RIGHT COLOUR FOR WHAT IT IS.
		clearScene();
		putLoc(SceneLoc.LAYER_SCENERY, MID_X, MID_Z, CREVICE, 0);
		manager.onGameTick();
		frame();
		check(countPixels(0x00FFFF) > 0 && countPixels(0x00FF00) == 0,
			"a shortcut is drawn in the shortcut colour, not the course one");

		// AND IN THE LOCKED COLOUR WHEN YOUR LEVEL CANNOT USE IT. The crevice asks for 81.
		levelAll(80);
		manager.onGameTick();
		frame();
		check(countPixels(0xFF0000) > 0 && countPixels(0x00FFFF) == 0,
			"one level short of a shortcut, it is drawn in the locked colour");
		levelAll(81);
		manager.onGameTick();
		frame();
		check(countPixels(0x00FFFF) > 0 && countPixels(0xFF0000) == 0,
			"...and exactly on the level it asks for, it is not locked");

		// HIDDEN ONLY IF THE PLAYER ASKED. Knowing a shortcut is there is most of why anyone
		// trains the skill, so the default is to show it greyed rather than to hide it.
		levelAll(80);
		manager.onGameTick();
		frame();
		int shown = countPainted();
		set("hideLocked", "true");
		frame();
		check(countPainted() < shown,
			"with hide-locked on, a shortcut above your level is not drawn (" + shown + " -> "
				+ countPainted() + ")");
		set("hideLocked", "false");
		levelAll(99);

		// THE SWITCHES, each in the scene.
		manager.onGameTick();
		frame();
		check(countPainted() > 0, "a shortcut is marked with shortcuts on");
		set("shortcuts", "false");
		frame();
		check(countPainted() == 0, "...and not with them off");
		set("shortcuts", "true");

		// A THICKER BORDER IS MORE PIXELS, and a fill is more again.
		frame();
		int thin = countPainted();
		set("borderWidth", "3");
		frame();
		check(countPainted() > thin, "a thicker border draws more (" + thin + " -> "
			+ countPainted() + ")");
		set("borderWidth", "1");
		frame();
		int outlineOnly = countPainted();
		set("fill", "true");
		frame();
		check(countPainted() > outlineOnly,
			"filling covers more of the tile than the outline alone");
		set("fill", "false");

		// A MULTI-TILE OBSTACLE IS MARKED ON EVERY TILE IT STANDS ON, not just its origin.
		clearScene();
		size(1, CREVICE, 1, 1);
		putLoc(SceneLoc.LAYER_SCENERY, MID_X, MID_Z, CREVICE, 0);
		manager.onGameTick();
		frame();
		int oneTile = countPainted();
		clearScene();
		size(1, CREVICE, 3, 1);
		putLoc(SceneLoc.LAYER_SCENERY, MID_X, MID_Z, CREVICE, 0);
		manager.onGameTick();
		frame();
		check(countPainted() > oneTile,
			"a three-tile obstacle is marked across all three (" + oneTile + " -> "
				+ countPainted() + ")");
		size(1, CREVICE, 1, 1);

		// THE LABEL.
		clearScene();
		putLoc(SceneLoc.LAYER_SCENERY, MID_X, MID_Z, CREVICE, 0);
		manager.onGameTick();
		set("label", AgilityPlugin.LABEL_NONE);
		check(drawnText().isEmpty(), "set to say nothing, no label is drawn");
		set("label", AgilityPlugin.LABEL_NAME);
		List<String> named = drawnText();
		check(named.size() == 1 && "Crevice".equals(named.get(0)),
			"set to the name, the name is drawn (" + named + ")");
		set("label", AgilityPlugin.LABEL_NAME_LEVEL);
		List<String> levelled = drawnText();
		check(levelled.size() == 1 && "Crevice (81)".equals(levelled.get(0)),
			"and with the level, the level is on it (" + levelled + ")");

		// LOGGED OUT, NOTHING. The skill arrays and the scene both still hold their last values,
		// so a plugin that did not check would paint a course over the login screen.
		client.ingame = false;
		frame();
		check(countPainted() == 0, "logged out, nothing is marked");
		manager.onGameTick();
		client.ingame = true;
		frame();
		check(countPainted() == 0,
			"...and the tick that ran while logged out dropped the scan rather than keeping it");
		manager.onGameTick();
		frame();
		check(countPainted() > 0, "logging back in and ticking marks it again");
	}

	// ---------------------------------------------------------------- helpers

	static void levelAll(int level) {
		for (int i = 0; i < client.skillLevel.length; i++) {
			client.skillLevel[i] = level;
			client.skillBaseLevel[i] = level;
		}
	}

	static void frame() {
		font.rows.clear();
		java.util.Arrays.fill(pixels, 0);
		Pix2D.bind(W, H, pixels);
		manager.renderOverlays(W, H, Overlay.LAYER_SCENE);
	}

	/** The labels the last frame drew. */
	static List<String> drawnText() {
		frame();
		return new ArrayList<String>(font.rows);
	}

	static int countPixels(int colour) {
		int n = 0;
		for (int i = 0; i < pixels.length; i++) {
			if (pixels[i] == colour) {
				n++;
			}
		}
		return n;
	}

	static int countPainted() {
		int n = 0;
		for (int i = 0; i < pixels.length; i++) {
			if (pixels[i] != 0) {
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
					"setting " + key + " to " + value + " is accepted");
				return;
			}
		}
		check(false, "Agility has a setting called " + key);
	}

	/** Records the text it is asked to draw and paints nothing. */
	static final class RecordingFont extends PixFont {

		final List<String> rows = new ArrayList<String>();

		RecordingFont() {
			this.height = 12;
			java.util.Arrays.fill(this.charAdvance, 4);
		}

		public int stringWid(String text) {
			return text == null ? 0 : text.length() * 4;
		}

		public int stringWidTag(String text) {
			return this.stringWid(text);
		}

		public void drawStringTag(int colour, int x, int y, boolean shadow, String text) {
			this.rows.add(text);
		}

		public void drawString(int x, int colour, int y, String text) {
			this.rows.add(text);
		}
	}
}
