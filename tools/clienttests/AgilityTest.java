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

	/** What every skill that is NOT Agility is set to, so the two can never be confused. */
	static final int OTHER_SKILLS = 7;

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
		check(AgilityObstacles.count() == 126,
			"the table holds every obstacle the agility scripts bind, which is 126 ("
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

		// ---- THE ROOFTOP COURSES, which the first version of this table missed entirely.
		//
		// They were added to the content after the branch that table was generated from, and
		// nothing here noticed - the plugin shipped marking the barbarian course and the
		// shortcuts and silently ignoring nine courses. These anchors are one per course, at
		// the level the course's own constant sets, so a table regenerated from a stale
		// checkout fails rather than quietly shrinking.
		int[] rooftops = {
			AgilityObstacles.DRAYNOR, AgilityObstacles.ALKHARID, AgilityObstacles.VARROCK,
			AgilityObstacles.CANIFIS, AgilityObstacles.FALADOR, AgilityObstacles.SEERS,
			AgilityObstacles.POLLNIVNEACH, AgilityObstacles.RELLEKKA,
			AgilityObstacles.ARDOUGNE
		};
		int[] wantLevels = { 1, 20, 30, 40, 50, 60, 70, 80, 90 };
		for (int i = 0; i < rooftops.length; i++) {
			int course = rooftops[i];
			int found = 0;
			boolean rightLevel = true;
			for (int j = 0; j < all.size(); j++) {
				if (all.get(j).course == course) {
					found++;
					if (all.get(j).level != wantLevels[i]) {
						rightLevel = false;
					}
				}
			}
			check(found > 0 && rightLevel,
				AgilityObstacles.COURSES[course] + " is in the table, at level " + wantLevels[i]
					+ " (" + found + " obstacles)");
			check(AgilityObstacles.isCourse(course),
				"..." + AgilityObstacles.COURSES[course] + " counts as a course");
		}

		// A GRAFTED LOC CARRIES THIS CACHE'S ID, NOT OSRS'S. The rooftop obstacles were
		// imported from OSRS and re-encoded, so Draynor's rough wall is loc 11404 there and
		// 18897 here - a table taken from a wiki, or from the comments in the script that
		// name the OSRS ids, would mark nothing at all.
		AgilityObstacles.Obstacle roughWall = AgilityObstacles.byId(18897);
		check(roughWall != null && roughWall.course == AgilityObstacles.DRAYNOR,
			"Draynor's rough wall is in the table under THIS cache's id, 18897");
		check(AgilityObstacles.byId(11404) == null,
			"...and not under the OSRS id its own script comment names");
	}

	/**
	 * The client's API level, from the suite that needed 9.
	 *
	 * THE EXACT PIN HAS MOVED ON to GameStateTest, which is the newest suite - 10 added varps and
	 * the inventory. This one goes back to asking only that its own level is still supported,
	 * the way every older suite does, because an exact pin in two places is one that gets missed
	 * in one of them.
	 */
	static void apiLevelTests() {
		check(jagex2.client.plugin.PluginApi.LEVEL >= 9,
			"this client is at least API level 9 (" + jagex2.client.plugin.PluginApi.LEVEL + ")");
		check(jagex2.client.plugin.PluginApi.supports(9), "a plugin asking for 9 runs here");
		check(jagex2.client.plugin.PluginApi.supports(8), "...and one asking for 8 still does");
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

		// ---- ONE SWITCH PER COURSE, WALKED END TO END.
		//
		// wanted() looks the switch up BY INDEX against a list in the courses' own order, so a
		// list one short or one out of step would map Falador's switch onto Seers' obstacles -
		// every switch would still "work", just on the wrong course. Nothing short of turning
		// each on alone and checking only its own obstacles answer catches that.
		check(plugin.switchFor().length == AgilityObstacles.COURSES.length,
			"there is a switch for every course (" + plugin.switchFor().length + " of "
				+ AgilityObstacles.COURSES.length + ")");
		boolean everyCourseMapped = true;
		StringBuilder wrong = new StringBuilder();
		for (int course = 0; course < AgilityObstacles.COURSES.length; course++) {
			setEverySwitch(plugin, false);
			setSwitch(plugin, course, true);
			for (int other = 0; other < AgilityObstacles.COURSES.length; other++) {
				AgilityObstacles.Obstacle sample = firstOf(other);
				if (sample == null) {
					continue;
				}
				boolean want = other == course;
				if (plugin.wanted(sample) != want) {
					everyCourseMapped = false;
					wrong.append(AgilityObstacles.COURSES[course]).append("->")
						.append(AgilityObstacles.COURSES[other]).append(' ');
				}
			}
		}
		check(everyCourseMapped,
			"each course's switch marks that course's obstacles and no other's" + (wrong.length()
				> 0 ? " (crossed: " + wrong + ")" : ""));
		// A COURSE THE SWITCHES DO NOT COVER IS NOT MARKED, and does not throw.
		//
		// Nothing in the table has a course past the end of the list - a shape check above
		// sees to that - so this guard is unreachable from the data as it stands. It is what
		// happens the day a fifteenth course is added to the table and its switch is
		// forgotten: a quiet "not marked" in the sidebar rather than an exception thrown out
		// of a draw that runs fifty times a second.
		AgilityObstacles.Obstacle offTheEnd =
			new AgilityObstacles.Obstacle(1, "Nonesuch", AgilityObstacles.COURSES.length, 1);
		boolean handled;
		try {
			handled = !plugin.wanted(offTheEnd);
		} catch (RuntimeException threw) {
			handled = false;
		}
		check(handled,
			"an obstacle whose course has no switch is left unmarked rather than throwing");
		AgilityObstacles.Obstacle negative =
			new AgilityObstacles.Obstacle(1, "Nonesuch", -1, 1);
		boolean negativeHandled;
		try {
			negativeHandled = !plugin.wanted(negative);
		} catch (RuntimeException threw) {
			negativeHandled = false;
		}
		check(negativeHandled, "...and so is one with no course at all");

		setEverySwitch(plugin, true);
		check(plugin.wanted(gnome) && plugin.wanted(crevice) && plugin.wanted(dungeon),
			"with everything on, everything is wanted");
		setEverySwitch(plugin, false);
		check(!plugin.wanted(gnome) && !plugin.wanted(crevice) && !plugin.wanted(dungeon),
			"and with everything off, nothing is");
		setEverySwitch(plugin, true);
	}

	/** The first obstacle of a course, or null if the table has none. */
	static AgilityObstacles.Obstacle firstOf(int course) {
		List<AgilityObstacles.Obstacle> all = AgilityObstacles.all();
		for (int i = 0; i < all.size(); i++) {
			if (all.get(i).course == course) {
				return all.get(i);
			}
		}
		return null;
	}

	/**
	 * Writes one course's switch by index, through the field the config binds.
	 *
	 * By reflection over the declared order rather than a fourteen-arm setter, so this helper
	 * cannot drift from the plugin the way a hand-written copy of the mapping would - and so
	 * that the check above is testing the plugin's own order rather than agreeing with a second
	 * copy of it.
	 */
	static void setSwitch(AgilityPlugin plugin, int course, boolean on) {
		String[] keys = {
			"gnome", "barbarian", "wilderness", "draynor", "alkharid", "varrock", "canifis",
			"falador", "seers", "pollnivneach", "rellekka", "ardougne", "shortcuts", "dungeon"
		};
		if (course < 0 || course >= keys.length) {
			return;
		}
		try {
			java.lang.reflect.Field f = AgilityPlugin.class.getDeclaredField(keys[course]);
			f.setAccessible(true);
			f.setBoolean(plugin, on);
		} catch (Throwable missing) {
			check(false, "AgilityPlugin has a switch called " + keys[course]);
		}
	}

	static void setEverySwitch(AgilityPlugin plugin, boolean on) {
		for (int course = 0; course < AgilityObstacles.COURSES.length; course++) {
			setSwitch(plugin, course, on);
		}
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
		// CAUGHT, NOT THROWN. Without the guard, `ids.length` on a null is an exception out of
		// a per-tick scan, and an unwrapped check here ends the run with no failure named -
		// which the mutation runner reports as a crash, and a crash is not a catch.
		boolean nullSafe;
		try {
			nullSafe = context().findLocs(null).isEmpty();
		} catch (RuntimeException threw) {
			nullSafe = false;
		}
		check(nullSafe, "...and so does asking for null");

		// ID 0 IS NOT A LOC, it is what an empty square reads as. Without the guard on that,
		// asking for 0 matches all ten thousand of them - which is the one id where "found
		// nothing" and "found the whole scene" are a character apart.
		int zeroFound;
		try {
			zeroFound = context().findLocs(new int[] { 0 }).size();
		} catch (RuntimeException threwOnZero) {
			// Without the guard the scan asks LocType about every empty square in the scene,
			// and an id nothing has decoded throws. Named rather than loose: unwrapped this
			// ends the run with no failure reported.
			zeroFound = -1;
		}
		check(zeroFound == 0,
			"asking for id 0 finds nothing, rather than every empty square in the scene ("
				+ zeroFound + ")");

		// UNSORTED INPUT STILL WORKS, and comes back unchanged. The plugin happens to hand over
		// a sorted array, so nothing about the sort or the copy shows through it - but the
		// method takes any int[] and a caller is owed both: that it finds what it asked for,
		// and that its own array is not reordered under it.
		// DESCENDING, not merely shuffled. A binary search over an unsorted array still finds
		// some of what it looks for by luck - the first version of this check used an order
		// that happened to find both, and the audit deleted the sort with it green. Descending
		// is the order that reliably loses the smallest id, which here is the one that is in
		// the scene.
		int[] jumbled = { CREVICE, GNOME_LOG, DUNGEON_PIPE, NOT_AN_OBSTACLE };
		int[] before = { CREVICE, GNOME_LOG, DUNGEON_PIPE, NOT_AN_OBSTACLE };
		check(context().findLocs(jumbled).size() == 2,
			"an unsorted list of ids finds what is in the scene ("
				+ context().findLocs(jumbled).size() + " of 2)");
		boolean untouched = true;
		for (int i = 0; i < jumbled.length; i++) {
			if (jumbled[i] != before[i]) {
				untouched = false;
			}
		}
		check(untouched, "...and the caller's own array comes back in the order it went in");

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
		size(0, GNOME_LOG, 1, 3);
		putLoc(SceneLoc.LAYER_SCENERY, MID_X, MID_Z, GNOME_LOG, 0);
		List<SceneLoc> deep = context().findLocs(AgilityObstacles.ids());
		check(deep.size() == 1 && deep.get(0).width == 1 && deep.get(0).length == 3,
			"a one-by-three obstacle is three LONG, which is the other axis and a separate "
				+ "number from the width");
		size(0, GNOME_LOG, 3, 1);
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
		for (int course = 0; course < AgilityObstacles.COURSES.length; course++) {
			set(switchKey(course), "true");
		}
		set("courseColour", "00FF00");
		set("shortcutColour", "00FFFF");
		set("lockedColour", "FF0000");
		set("hideLocked", "false");
		set("label", AgilityPlugin.LABEL_NONE);
		set("fill", "false");
		set("borderWidth", "1");
		agilityAt(99);

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
		agilityAt(80);
		manager.onGameTick();
		frame();
		check(countPixels(0xFF0000) > 0 && countPixels(0x00FFFF) == 0,
			"one level short of a shortcut, it is drawn in the locked colour");
		agilityAt(81);
		manager.onGameTick();
		frame();
		check(countPixels(0x00FFFF) > 0 && countPixels(0xFF0000) == 0,
			"...and exactly on the level it asks for, it is not locked");

		// HIDDEN ONLY IF THE PLAYER ASKED. Knowing a shortcut is there is most of why anyone
		// trains the skill, so the default is to show it greyed rather than to hide it.
		agilityAt(80);
		manager.onGameTick();
		frame();
		int shown = countPainted();
		set("hideLocked", "true");
		frame();
		check(countPainted() < shown,
			"with hide-locked on, a shortcut above your level is not drawn (" + shown + " -> "
				+ countPainted() + ")");
		set("hideLocked", "false");
		agilityAt(99);

		// A CACHE WITH NO AGILITY SKILL LOCKS NOTHING, rather than everything. Level 0 would
		// grey out every shortcut in the game and read as "this plugin is broken" rather than
		// as "this client has no Agility" - and marking an obstacle a player cannot use is the
		// lesser mistake of the two.
		String[] realSkills = jagex2.client.Stats.field1504;
		String[] noAgility = new String[realSkills.length];
		for (int i = 0; i < realSkills.length; i++) {
			noAgility[i] = "agility".equalsIgnoreCase(realSkills[i]) ? "nonesuch" : realSkills[i];
		}
		jagex2.client.Stats.field1504 = noAgility;
		AgilityPlugin fresh = freshPlugin();
		check(fresh != null && fresh.agilityLevel() == Integer.MAX_VALUE,
			"with no Agility in the cache's skill list, nothing reads as above your level");
		jagex2.client.Stats.field1504 = realSkills;
		AgilityPlugin known = freshPlugin();
		check(known != null && known.agilityLevel() == 99,
			"...and with it there, the level is the one the player has ("
				+ (known == null ? -1 : known.agilityLevel()) + ")");

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
		clearScene();
		size(1, CREVICE, 1, 3);
		putLoc(SceneLoc.LAYER_SCENERY, MID_X, MID_Z, CREVICE, 0);
		manager.onGameTick();
		frame();
		check(countPainted() > oneTile,
			"...and a one-by-three is marked across all three the other way, which is a "
				+ "separate loop from the width (" + oneTile + " -> " + countPainted() + ")");
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

	/**
	 * Agility at one level and EVERY OTHER SKILL AT ANOTHER.
	 *
	 * The first version of this set every skill to the same number, which makes "reads the
	 * agility level" and "reads skill 0's level" the same answer - the audit swapped the
	 * lookup for a hardcoded index and every gate check stayed green. A level nothing else
	 * shares is what makes the lookup mean anything.
	 */
	static void agilityAt(int level) {
		for (int i = 0; i < client.skillLevel.length; i++) {
			client.skillLevel[i] = OTHER_SKILLS;
			client.skillBaseLevel[i] = OTHER_SKILLS;
		}
		int agility = agilityIndex();
		if (agility >= 0) {
			client.skillLevel[agility] = level;
			client.skillBaseLevel[agility] = level;
		}
	}

	/** Where Agility is in this cache's skill list, found the way the plugin finds it. */
	static int agilityIndex() {
		for (int i = 0; i < jagex2.client.Stats.field1504.length; i++) {
			if ("agility".equalsIgnoreCase(jagex2.client.Stats.field1504[i])) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * A second Agility plugin, attached but not on the bus.
	 *
	 * The skill index is resolved once and remembered, which is right - the cache's skill list
	 * does not change while the client runs - and means the live plugin cannot be asked the
	 * question twice with two different lists. A fresh instance is the honest way to ask what
	 * a plugin starting against this cache would find.
	 */
	static AgilityPlugin freshPlugin() {
		try {
			AgilityPlugin plugin = new AgilityPlugin();
			java.lang.reflect.Method attach = jagex2.client.plugin.Plugin.class
				.getDeclaredMethod("attach", jagex2.client.plugin.PluginContext.class,
					PluginConfig.class);
			attach.setAccessible(true);
			attach.invoke(plugin, context(), entry.getConfig());
			return plugin;
		} catch (Throwable cannot) {
			check(false, "a second Agility plugin can be built (" + cannot + ")");
			return null;
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

	/** The config key for one course's switch, in the courses' own order. */
	static String switchKey(int course) {
		String[] keys = {
			"gnome", "barbarian", "wilderness", "draynor", "alkharid", "varrock", "canifis",
			"falador", "seers", "pollnivneach", "rellekka", "ardougne", "shortcuts", "dungeon"
		};
		return keys[course];
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
