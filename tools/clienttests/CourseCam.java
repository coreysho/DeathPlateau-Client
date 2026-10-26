// AN AGILITY COURSE RUN IN THE REAL CLIENT, WITH NO WINDOW, AND PHOTOGRAPHED AT EVERY OBSTACLE.
//
// WHY THIS EXISTS. Every other check of these courses is engine-side: tools/sim/rooftops.ts proves
// the landing tiles are standable, that the lap can carry on from each of them, that the experience
// adds up and that the level gate turns people away. All seven courses pass it. None of that can
// see what a player sees - whether the roof is drawn under the tile they land on, whether the
// crossing animation travels with them, whether the obstacle can be clicked at all from where the
// last one put them, or whether a "tightrope" slides them diagonally over three rooftops because
// its landing was chosen beside the NEXT obstacle instead of at the end of the rope.
//
// So this is the other half: the client's own code, logged in to a local engine, drawing frames
// into a BufferedImage, with the mouse handed to its own listeners - the ResizableShots pattern -
// pointed at content instead of at the window. Per obstacle it records
//
//   the menu under the cursor        proof the loc is there, named, and has the verb on it
//   the DRAWN path, frame by frame   where the client puts the player while the move plays out
//   floor under every tile of it     the client's own scene: a Square with no Ground is thin air
//   the tile it finished on          against what the course constant says
//   three photographs                before, mid-move and at rest
//
// A tile with no floor under it is the fault this is really for. draynorcourse.ts approximates it
// by reading overlay ids out of the .jm2; this asks the renderer.
//
// Driven by tools/clienttests/run_coursecam.py, which writes the obstacle table out of the content
// constants so nothing here is hardcoded:
//
//   java -Dlostcity.host=127.0.0.1 -Dlostcity.port=43694 -Dlostcity.webport=8694
//        -Dshots.rsan=<modulus> -Duser.home=<tmp>
//        -cp <client jar><sep><dir of this class> CourseCam <out dir> <user> <table.tsv>
import jagex2.client.Client;
import jagex2.dash3d.ClientPlayer;
import jagex2.dash3d.Square;
import jagex2.dash3d.World3D;
import sign.signlink;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javax.imageio.ImageIO;

public class CourseCam extends Client {

	static final Object LOCK = new Object();
	static volatile BufferedImage canvas;
	static volatile int frames;
	static volatile boolean pinCamera;
	static int pinYaw = Integer.getInteger("cam.yaw", 0).intValue();
	static int pinPitch = Integer.getInteger("cam.pitch", 280).intValue();
	static int pinZoom = Integer.getInteger("cam.zoom", 700).intValue();
	static volatile int menuOptions;
	/** Where to point the scene pick, and what it last came back with (scene tiles, -1 for none). */
	static volatile boolean wantPick;
	static volatile int pickX;
	static volatile int pickY;
	static volatile boolean armed;
	static volatile int pickedX = -1;
	static volatile int pickedZ = -1;
	static volatile int picks;

	static volatile String lastOption = "";

	public void useMenuOption(int option) {
		menuOptions++;
		try {
			String[] opts = (String[]) get("menuOption");
			lastOption = opts[option] == null ? "" : opts[option].replaceAll("@...@", "");
		} catch (RuntimeException ignored) {
			lastOption = "?";
		}
		super.useMenuOption(option);
	}

	public Graphics acquireGraphics() {
		int w = this.getWidth();
		int h = this.getHeight();
		if (w <= 0 || h <= 0) {
			w = 765;
			h = 503;
		}
		if (canvas == null || canvas.getWidth() != w || canvas.getHeight() != h) {
			canvas = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		}
		return canvas.createGraphics();
	}

	public URL getCodeBase() {
		try {
			return new URL("http://" + Client.WEB_HOST + ":" + Client.WEB_PORT);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public String getHost(int arg0) {
		return "runescape.com";
	}

	public void draw() {
		synchronized (LOCK) {
			// ARM A SCENE PICK, which is what tells us the tile under the cursor.
			//
			// The client picks only when something has asked: handleInput calls scene.method312
			// while a plugin is reading getHoverTileX, and the answer lands in World3D.clickTileX
			// as the scene draws. No plugin is running here, so the pick is armed and taken by
			// hand - and TAKEN IS THE WORD. clickTileX left set is what the client's own walk code
			// reads to send a step, so a pick that is not consumed here walks the player to
			// wherever the cursor was. It is cleared again below, in the same frame.
			if (wantPick && !World3D.field1044) {
				World3D sc = (World3D) get("scene");
				if (sc != null) {
					sc.method312(pickX - vp("vpX"), pickY - vp("vpY"));
					armed = true;
				}
			}
			if (pinCamera) {
				set("orbitCameraYaw", Integer.valueOf(pinYaw));
				set("orbitCameraPitch", Integer.valueOf(pinPitch));
				set("macroCameraAngle", Integer.valueOf(0));
				set("macroCameraX", Integer.valueOf(0));
				set("macroCameraZ", Integer.valueOf(0));
				set("macroMinimapAngle", Integer.valueOf(0));
				set("macroMinimapZoom", Integer.valueOf(0));
				set("cameraZoomOffset", Integer.valueOf(pinZoom));
			}
			super.draw();
			if (armed) {
				pickedX = World3D.clickTileX;
				pickedZ = World3D.clickTileZ;
				World3D.clickTileX = -1;
				World3D.clickTileZ = -1;
				World3D.field1044 = false;
				armed = false;
				picks++;
			}
			frames++;
		}
	}

	// ------------------------------------------------------------------------------ reflection
	static CourseCam app;

	static Field field(String name) {
		for (Class<?> c = app.getClass(); c != null; c = c.getSuperclass()) {
			try {
				Field f = c.getDeclaredField(name);
				f.setAccessible(true);
				return f;
			} catch (NoSuchFieldException ignored) {
			}
		}
		throw new RuntimeException("no field " + name);
	}

	static Object get(String name) {
		try {
			return field(name).get(app);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	static int geti(String name) {
		return ((Number) get(name)).intValue();
	}

	static void set(String name, Object v) {
		try {
			field(name).set(app, v);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	static Object call(String name, Object... args) {
		try {
			for (Class<?> c = app.getClass(); c != null; c = c.getSuperclass()) {
				for (Method m : c.getDeclaredMethods()) {
					if (m.getName().equals(name) && m.getParameterTypes().length == args.length) {
						m.setAccessible(true);
						return m.invoke(app, args);
					}
				}
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		throw new RuntimeException("no method " + name);
	}

	static void seedRandom(long seed) throws Exception {
		Field f = Class.forName("java.lang.Math$RandomNumberGeneratorHolder").getDeclaredField("randomNumberGenerator");
		f.setAccessible(true);
		((Random) f.get(null)).setSeed(seed);
	}

	// ------------------------------------------------------------------------------ input
	static void hover(int x, int y) {
		app.mouseMoved(new MouseEvent(app, MouseEvent.MOUSE_MOVED, System.currentTimeMillis(), 0, x, y, 0, false));
	}

	static void click(int x, int y, boolean right) throws Exception {
		hover(x, y);
		waitFrames(2);
		int button = right ? MouseEvent.BUTTON3 : MouseEvent.BUTTON1;
		app.mousePressed(new MouseEvent(app, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), 0, x, y, 1, false, button));
		waitFrames(2);
		app.mouseReleased(new MouseEvent(app, MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(), 0, x, y, 1, false, button));
		waitFrames(3);
	}

	static void key(int code, char ch) throws Exception {
		app.keyPressed(new KeyEvent(app, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, code, ch));
		waitFrames(1);
		app.keyReleased(new KeyEvent(app, KeyEvent.KEY_RELEASED, System.currentTimeMillis(), 0, code, ch));
		waitFrames(1);
	}

	static void type(String s) throws Exception {
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			app.keyPressed(new KeyEvent(app, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_UNDEFINED, c));
			app.keyReleased(new KeyEvent(app, KeyEvent.KEY_RELEASED, System.currentTimeMillis(), 0, KeyEvent.VK_UNDEFINED, c));
			waitFrames(1);
		}
	}

	/** A :: command, typed into the chatbox the way a player types it. */
	static void command(String cmd) throws Exception {
		type(cmd);
		waitFrames(2);
		key(KeyEvent.VK_ENTER, '\n');
		waitFrames(5);
	}

	// ------------------------------------------------------------------------------ waiting
	static void waitFrames(int n) throws InterruptedException {
		int until = frames + n;
		long give = System.currentTimeMillis() + 30000L;
		while (frames < until && System.currentTimeMillis() < give) {
			Thread.sleep(5);
		}
	}

	interface Cond {
		boolean ok();
	}

	static void waitFor(String what, long ms, Cond c) throws Exception {
		long give = System.currentTimeMillis() + ms;
		while (!c.ok()) {
			if (System.currentTimeMillis() > give) {
				String why = "";
				try {
					why = " (login screen says: " + get("loginMessage0") + " / " + get("loginMessage1") + ")";
				} catch (RuntimeException ignored) {
				}
				throw new RuntimeException("timed out waiting for " + what + why);
			}
			Thread.sleep(20);
		}
	}

	// ------------------------------------------------------------------------------ output
	static File out;
	static PrintWriter log;
	static int ok;
	static int bad;

	static void say(String s) {
		System.out.println(s);
		log.println(s);
		log.flush();
	}

	static void check(String what, boolean good, String detail) {
		if (good) {
			ok++;
		} else {
			bad++;
		}
		say("  " + (good ? "ok  " : "FAIL") + "  " + what + (detail.isEmpty() ? "" : "   " + detail));
	}

	static void shot(String name) throws Exception {
		waitFrames(2);
		BufferedImage copy;
		synchronized (LOCK) {
			BufferedImage c = canvas;
			copy = new BufferedImage(c.getWidth(), c.getHeight(), BufferedImage.TYPE_INT_RGB);
			Graphics2D g = copy.createGraphics();
			g.drawImage(c, 0, 0, null);
			g.dispose();
		}
		ImageIO.write(copy, "png", new File(out, name + ".png"));
	}

	/** "x,zLl" back to numbers. */
	static int[] tileOf(String tile) {
		String[] p = tile.split("[,L]");
		return new int[] { Integer.parseInt(p[0]), Integer.parseInt(p[1]) };
	}

	/** The rows under the cursor, top first, colour tags stripped. */
	static List<String> menu() {
		List<String> rows = new ArrayList<String>();
		synchronized (LOCK) {
			String[] opts = (String[]) get("menuOption");
			int size = geti("menuSize");
			for (int i = size - 1; i >= 0; i--) {
				rows.add(opts[i] == null ? "" : opts[i].replaceAll("@...@", ""));
			}
		}
		return rows;
	}

	/** The raw menuOption index whose text starts with this verb, or -1. */
	static int rowOf(String verb) {
		synchronized (LOCK) {
			String[] opts = (String[]) get("menuOption");
			int size = geti("menuSize");
			for (int i = 0; i < size; i++) {
				String o = opts[i] == null ? "" : opts[i].replaceAll("@...@", "");
				if (o.startsWith(verb)) {
					return i;
				}
			}
		}
		return -1;
	}

	// ------------------------------------------------------------------------------ the scene
	/** Where the client is DRAWING the player, in world tiles with the fraction kept. */
	static double[] drawnAt() {
		synchronized (LOCK) {
			ClientPlayer me = (ClientPlayer) get("localPlayer");
			if (me == null) {
				return new double[] { -1, -1 };
			}
			int bx = geti("sceneBaseTileX");
			int bz = geti("sceneBaseTileZ");
			return new double[] { bx + me.field1157 / 128.0, bz + me.field1158 / 128.0 };
		}
	}

	/** The tile the client has the player on. */
	static int[] tileAt() {
		synchronized (LOCK) {
			ClientPlayer me = (ClientPlayer) get("localPlayer");
			if (me == null) {
				return new int[] { -1, -1, -1 };
			}
			int bx = geti("sceneBaseTileX");
			int bz = geti("sceneBaseTileZ");
			return new int[] { bx + (me.field1157 >> 7), bz + (me.field1158 >> 7), geti("currentLevel") };
		}
	}

	/**
	 * IS THERE FLOOR DRAWN HERE? The client's scene keeps one Square per tile per level, and the
	 * floor is a Ground (a shaped tile) or a QuickGround (a flat one). A Square with neither is a
	 * tile the renderer draws nothing for: standing on it is standing in thin air, which is what
	 * both Draynor tightropes shipped doing.
	 */
	static boolean floorAt(int worldX, int worldZ, int level) {
		synchronized (LOCK) {
			World3D sc = (World3D) get("scene");
			if (sc == null) {
				return false;
			}
			int x = worldX - geti("sceneBaseTileX");
			int z = worldZ - geti("sceneBaseTileZ");
			if (x < 0 || x > 103 || z < 0 || z > 103 || level < 0 || level > 3) {
				return false;
			}
			Square sq = sc.field1018[level][x][z];
			if (sq == null) {
				return false;
			}
			// A LOC IS FLOOR TOO. Half of these obstacles are climbed ON TO - a market stall, a
			// basket, a banner, a steep roof, a tree - and the tile you end on has no Ground and no
			// QuickGround, because what holds you up is the obstacle's own model. Canifis' tall
			// tree lands you on one, and a terrain-only test called that standing in the sky.
			//
			// Scenery is kept at its origin tile only, so a tile in the middle of a big canopy can
			// still read empty; that is a false negative to live with rather than a reason to ask
			// about terrain alone.
			return sq.field1385 != null
				|| sq.field1384 != null
				|| sq.field1389 != null
				|| sq.field1388 != null
				|| sq.field1386 != null
				|| sq.field1387 != null;
		}
	}

	/**
	 * The world tile under the cursor, as the CLIENT picked it, or null if it picked nothing.
	 *
	 * Points the pick at (x, y), waits for a frame to answer it, and reads the answer. Nothing is
	 * clicked and the player is not moved: the pick slot is consumed in the same frame it is armed.
	 */
	static int[] pickAt(int x, int y) throws Exception {
		pickX = x;
		pickY = y;
		pickedX = -1;
		pickedZ = -1;
		int before = picks;
		wantPick = true;
		for (int i = 0; i < 12 && picks - before < 2; i++) {
			waitFrames(1);
		}
		wantPick = false;
		if (pickedX < 0 || pickedZ < 0) {
			return null;
		}
		return new int[] { pickedX + geti("sceneBaseTileX"), pickedZ + geti("sceneBaseTileZ") };
	}

	/**
	 * PUT THE CURSOR ON A WORLD TILE, by asking the client where the cursor is and stepping towards
	 * the answer.
	 *
	 * Why not projectFromGround: it writes viewport-local coordinates computed inside drawScene,
	 * with the viewport area bound and the camera of that frame. Called from a test thread it
	 * answers in whatever space the last bound area left behind - the first run of this clicked a
	 * chest of drawers inside a house while aiming at a rooftop.
	 *
	 * So the client's own scene pick is the oracle instead. Two probe moves give the pixels-per-tile
	 * of the current camera, and the rest is Newton steps. Returns the screen point, or null if the
	 * tile could not be landed on - which is itself a finding: it means that obstacle cannot be
	 * clicked from where the player is standing.
	 */
	static String aimWhy = "";

	static int[] aimAt(int worldX, int worldZ) throws Exception {
		int w = app.getWidth();
		int h = app.getHeight();
		int px = w / 2;
		int py = h / 2;
		aimWhy = "";
		// THE FIRST PICK OF A RUN CAN COME BACK EMPTY. The scene is loaded and the frames are
		// drawing, but the pick slot answers nothing for the first few of them, and the first
		// obstacle of the first course is where that showed up - reported as a wall that cannot be
		// clicked, a street away from the player.
		int[] at = null;
		for (int tryn = 0; tryn < 6 && at == null; tryn++) {
			at = pickAt(px, py);
			if (at == null) {
				waitFrames(10);
			}
		}
		if (at == null) {
			aimWhy = "the scene pick found no tile at the middle of the window (" + px + "," + py
				+ "), after " + picks + " picks";
			return null;
		}
		// THE JACOBIAN, MEASURED RATHER THAN DERIVED. A hundred pixels, not sixty: at the camera's
		// own zoom a tile can be wider than sixty pixels, and two probes that both pick the same
		// tile give a singular matrix and no step at all.
		// BOTH WAYS, because a probe can fall off the edge of the roof into the void and pick
		// nothing at all - which is not a reason to give up on the obstacle.
		int sx = 100;
		int sy = 100;
		int[] rx = pickAt(px + sx, py);
		if (rx == null) {
			sx = -100;
			rx = pickAt(px + sx, py);
		}
		int[] ry = pickAt(px, py + sy);
		if (ry == null) {
			sy = -100;
			ry = pickAt(px, py + sy);
		}
		if (rx == null || ry == null) {
			aimWhy = "the middle picked " + at[0] + "," + at[1]
				+ " but probes either side of it picked nothing, swept the window instead";
			return sweepFor(worldX, worldZ);
		}
		aimWhy = "the middle picked " + at[0] + "," + at[1] + ", wanted " + worldX + "," + worldZ;
		double axx = (rx[0] - at[0]) / (double) sx;
		double axz = (rx[1] - at[1]) / (double) sx;
		double ayx = (ry[0] - at[0]) / (double) sy;
		double ayz = (ry[1] - at[1]) / (double) sy;
		double det = axx * ayz - axz * ayx;
		if (Math.abs(det) < 1e-6) {
			aimWhy += "; no usable pixels-per-tile, swept the window instead";
			return sweepFor(worldX, worldZ);
		}
		int lastGoodX = px;
		int lastGoodY = py;
		for (int round = 0; round < 12; round++) {
			int[] now = pickAt(px, py);
			if (now == null) {
				// A STEP INTO THE VOID IS NOT A FAILURE. Two roofs with a street between them: the
				// straight line from one to the other crosses sky, and the Newton step lands in it.
				// Halve back towards the last point that picked something and carry on.
				px = (px + lastGoodX) / 2;
				py = (py + lastGoodY) / 2;
				if (px == lastGoodX && py == lastGoodY) {
					break;
				}
				continue;
			}
			lastGoodX = px;
			lastGoodY = py;
			if (now[0] == worldX && now[1] == worldZ) {
				return new int[] { px, py };
			}
			double dx = worldX - now[0];
			double dz = worldZ - now[1];
			// solve [axx ayx; axz ayz] * [dpx; dpy] = [dx; dz]
			double dpx = (dx * ayz - dz * ayx) / det;
			double dpy = (dz * axx - dx * axz) / det;
			// a Newton step that would leave the window is clamped rather than abandoned: the
			// scene wraps round the edges of a 1024x768 frame and the step is only a first guess
			px = (int) Math.max(8, Math.min(w - 8, Math.round(px + dpx)));
			py = (int) Math.max(8, Math.min(h - 8, Math.round(py + dpy)));
		}
		int[] end = pickAt(px, py);
		if (end != null && end[0] == worldX && end[1] == worldZ) {
			return new int[] { px, py };
		}
		aimWhy += "; twelve steps did not land on it, swept the window instead";
		return sweepFor(worldX, worldZ);
	}

	/**
	 * THE WHOLE WINDOW, EVERY 40 PIXELS, until one of them picks the tile.
	 *
	 * The Newton steps above need the camera to answer "how many pixels is a tile" and sometimes it
	 * cannot: at a steep pitch and a close zoom a tile is wider than the probe, both probes pick the
	 * same tile and the matrix is singular. A sweep always works and costs a few seconds, so it is
	 * the fallback rather than the method.
	 */
	static int[] sweepFor(int worldX, int worldZ) throws Exception {
		int w = app.getWidth();
		int h = app.getHeight();
		int[] best = null;
		int bestD = Integer.MAX_VALUE;
		for (int y = 40; y < h - 40; y += 40) {
			for (int x = 40; x < w - 40; x += 40) {
				int[] got = pickAt(x, y);
				if (got == null) {
					continue;
				}
				if (got[0] == worldX && got[1] == worldZ) {
					return new int[] { x, y };
				}
				int d = Math.abs(got[0] - worldX) + Math.abs(got[1] - worldZ);
				if (d < bestD) {
					bestD = d;
					best = new int[] { x, y };
				}
			}
		}
		// nothing picked the tile itself: the nearest point still gives the verb sweep a start,
		// and it is within a couple of tiles whenever the obstacle is on screen at all
		if (best != null && bestD <= 3) {
			aimWhy += "; nearest pick was " + bestD + " tiles off";
			return best;
		}
		return null;
	}

	/**
	 * THE POINT WHERE THE OBSTACLE IS ACTUALLY CLICKABLE, swept outwards from its tile.
	 *
	 * A tile is not a loc. The scene pick answers which GROUND tile is under the cursor, and the
	 * menu is built from the models under it - and a wall-shaped loc is drawn on the edge between
	 * two tiles, a centrepiece can lean off its own square, and a ledge is a thin plate up a wall.
	 * Falador's rough wall is the case this exists for: the cursor was on 3036,3341 exactly, and
	 * the menu was Walk here, because the wall itself is drawn a dozen pixels further on.
	 *
	 * So the tile is where the search STARTS. Rings of points outwards from it, stopping at the
	 * first one whose menu carries the verb, which is what a player does with the mouse. Coming
	 * back empty is a real finding: it means a player standing here cannot click the thing.
	 */
	static int[] findVerb(int px, int py, String verb) throws Exception {
		// A GRID ABOVE THE TILE, NEAREST FIRST.
		//
		// A model stands on its tile and rises up the screen from it, so the clickable part of
		// anything tall is well above the tile the scene pick answers with: Falador's rough wall
		// wanted 77 pixels of it. And a column straight up is not enough either - hand holds are a
		// thin plate up a wall, a dozen pixels to one side of the tile's middle, and a single
		// column walked right past them while the tile itself offered nothing but Cancel.
		//
		// So: every point in a band above the tile, closest first, which is what a player does with
		// the mouse when they cannot see what they are aiming at.
		final int[][] pts = new int[25 * 18][];
		int n = 0;
		for (int dy = 0; dy >= -204; dy -= 12) {
			for (int dx = -36; dx <= 36; dx += 12) {
				pts[n++] = new int[] { dx, dy };
			}
		}
		final int count = n;
		java.util.Arrays.sort(pts, 0, count, new java.util.Comparator<int[]>() {
			public int compare(int[] a, int[] b) {
				return (a[0] * a[0] + a[1] * a[1]) - (b[0] * b[0] + b[1] * b[1]);
			}
		});
		for (int i = 0; i < count; i++) {
			int x = px + pts[i][0];
			int y = py + pts[i][1];
			if (x < 8 || y < 8 || x > app.getWidth() - 8 || y > app.getHeight() - 8) {
				continue;
			}
			hover(x, y);
			waitFrames(3);
			if (rowOf(verb) >= 0) {
				return new int[] { x, y };
			}
		}
		// AND A FINE PASS FOR A MODEL MADE OF ALMOST NOTHING. Varrock's gap 1 is a 149-byte .ob2 -
		// a handful of triangles, where the tightrope beside it is 1023 - because a gap in a roof
		// edge is drawn as a marker rather than as a thing. The client picks locs triangle by
		// triangle, so a target that small falls between the twelve-pixel samples above.
		for (int dy = 6; dy >= -24; dy -= 6) {
			for (int dx = -18; dx <= 18; dx += 6) {
				int x = px + dx;
				int y = py + dy;
				if (x < 8 || y < 8 || x > app.getWidth() - 8 || y > app.getHeight() - 8) {
					continue;
				}
				hover(x, y);
				waitFrames(3);
				if (rowOf(verb) >= 0) {
					return new int[] { x, y };
				}
			}
		}
		// and a ring below it, for anything that leans the other way
		for (int a = 0; a < 12; a++) {
			double th = a * Math.PI / 6.0;
			int x = (int) Math.round(px + 48 * Math.cos(th));
			int y = (int) Math.round(py + 48 * Math.sin(th));
			if (x < 8 || y < 8 || x > app.getWidth() - 8 || y > app.getHeight() - 8) {
				continue;
			}
			hover(x, y);
			waitFrames(3);
			if (rowOf(verb) >= 0) {
				return new int[] { x, y };
			}
		}
		return null;
	}

	/** Project a world tile to the viewport, or null when it is off screen. */
	static int[] screenOf(int worldX, int worldZ) {
		synchronized (LOCK) {
			int x = worldX - geti("sceneBaseTileX");
			int z = worldZ - geti("sceneBaseTileZ");
			if (x < 0 || x > 103 || z < 0 || z > 103) {
				return null;
			}
			call("projectFromGround", Integer.valueOf(x * 128 + 64), Integer.valueOf(0), Integer.valueOf(z * 128 + 64));
			int px = geti("projectX");
			int py = geti("projectY");
			if (px == -1) {
				return null;
			}
			// projectFromGround writes viewport-local coordinates; the viewport is placed by the
			// layout, so a window coordinate is that plus the viewport's own origin.
			return new int[] { px + vp("vpX"), py + vp("vpY") };
		}
	}

	static int vp(String name) {
		try {
			Class<?> c = Class.forName("jagex2.client.Layout");
			Field f = c.getDeclaredField(name);
			f.setAccessible(true);
			return ((Number) f.get(null)).intValue();
		} catch (Exception e) {
			return 0;
		}
	}

	// ------------------------------------------------------------------------------ the table
	static class Ob {
		String name;
		int locId;
		int x;
		int z;
		int w = 1;
		int l = 1;
		int level;
		int landX;
		int landZ;
		int landLevel;
		String verb;
		int xp;
	}

	static String course;
	static int courseLevel;
	static int[] start = new int[3];
	static List<Ob> obstacles = new ArrayList<Ob>();

	static void readTable(String path) throws Exception {
		BufferedReader r = new BufferedReader(new FileReader(path));
		String line;
		while ((line = r.readLine()) != null) {
			line = line.trim();
			if (line.isEmpty() || line.startsWith("#")) {
				continue;
			}
			String[] f = line.split("\t");
			if (f[0].equals("course")) {
				course = f[1];
				courseLevel = Integer.parseInt(f[2]);
				start[0] = Integer.parseInt(f[3]);
				start[1] = Integer.parseInt(f[4]);
				start[2] = Integer.parseInt(f[5]);
				continue;
			}
			Ob o = new Ob();
			o.name = f[0];
			o.locId = Integer.parseInt(f[1]);
			o.x = Integer.parseInt(f[2]);
			o.z = Integer.parseInt(f[3]);
			o.level = Integer.parseInt(f[4]);
			o.landX = Integer.parseInt(f[5]);
			o.landZ = Integer.parseInt(f[6]);
			o.landLevel = Integer.parseInt(f[7]);
			o.verb = f[8];
			o.xp = Integer.parseInt(f[9]);
			if (f.length > 11) {
				o.w = Integer.parseInt(f[10]);
				o.l = Integer.parseInt(f[11]);
			}
			obstacles.add(o);
		}
		r.close();
	}

	// ------------------------------------------------------------------------------ the run
	public static void main(String[] args) {
		try {
			run(args);
		} catch (Throwable e) {
			e.printStackTrace();
			if (log != null) {
				e.printStackTrace(log);
				log.close();
			}
			System.exit(1);
		}
	}

	static String user;

	static void run(String[] args) throws Exception {
		out = new File(args[0]);
		out.mkdirs();
		user = args[1];
		readTable(args[2]);
		log = new PrintWriter(new FileWriter(new File(out, course + ".log")));

		seedRandom(377);
		String rsan = System.getProperty("shots.rsan");
		if (rsan != null) {
			Client.LOGIN_RSAN = new java.math.BigInteger(rsan.trim());
		}
		Client.nodeId = 10;
		Client.portOffset = 0;
		Client.setHighMem();
		Client.membersWorld = true;
		signlink.storeid = 33;
		signlink.startpriv(InetAddress.getLocalHost());

		int w = Integer.getInteger("cam.w", 1024).intValue();
		int h = Integer.getInteger("cam.h", 768).intValue();
		app = new CourseCam();
		app.setSize(w, h);
		app.initApplet(765, 503);
		if (app.graphics == null) {
			synchronized (LOCK) {
				app.graphics = app.acquireGraphics();
			}
		}
		call("setDisplayMode", Integer.valueOf(1)); // classic resizable: the viewport is the window
		waitFor("the title screen", 120000, () -> geti("titleScreenState") == 0 && frames > 20 && get("imageTitle4") != null);
		waitFrames(10);
		logIn(w, h);

		// A quiet spot first, so the account is out of the tutorial before anything is measured.
		command("::setvar tutorial 1000");
		waitFrames(20);
		command("::setstat agility " + courseLevel);
		waitFrames(20);
		pinCamera = true;

		say("");
		say("================ " + course.toUpperCase() + "   (agility " + courseLevel + ", " + obstacles.size() + " obstacles)");

		int[] from = new int[] { start[0], start[1], start[2] };
		for (int i = 0; i < obstacles.size(); i++) {
			Ob o = obstacles.get(i);
			say("");
			say(" " + (i + 1) + ". " + o.name + "  loc " + o.locId + " at " + o.x + "," + o.z + " L" + o.level
				+ "   from " + from[0] + "," + from[1] + " L" + from[2]);
			doObstacle(o, from, i);
			int[] now = tileAt();
			from = new int[] { now[0], now[1], now[2] };
		}

		say("");
		say(ok + " ok, " + bad + " FAIL");
		log.close();
		System.exit(bad > 0 ? 1 : 0);
	}

	/** level,mapsquareX,mapsquareZ,localX,localZ - what ::tele takes. */
	static String teleArg(int x, int z, int level) {
		return level + "," + (x / 64) + "," + (z / 64) + "," + (x % 64) + "," + (z % 64);
	}

	static void doObstacle(Ob o, int[] from, int index) throws Exception {
		// 1. STAND WHERE THE LAST OBSTACLE LEFT US. The first one is reached from the course start.
		//
		// CHECKED AND REPEATED, because a typed command does not always arrive: the chatbox takes
		// one character per frame and a frame that drops them leaves half a command behind. One
		// run teleported thirteen times and landed eleven tiles out on the twelfth, which read as
		// the previous obstacle having moved the player.
		int[] at = tileAt();
		for (int tryn = 0; tryn < 3; tryn++) {
			if (at[0] == from[0] && at[1] == from[1] && at[2] == from[2]) {
				break;
			}
			command("::tele " + teleArg(from[0], from[1], from[2]));
			waitFor("the scene", 30000, () -> geti("sceneState") == 2);
			waitFrames(25);
			at = tileAt();
		}
        check("standing at " + at[0] + "," + at[1] + " L" + at[2],
			at[0] == from[0] && at[1] == from[1] && at[2] == from[2],
			at[0] == from[0] && at[1] == from[1] && at[2] == from[2] ? "" : "wanted " + from[0] + "," + from[1] + " L" + from[2]);
		check("and the client draws floor under it", floorAt(at[0], at[1], at[2]), "");
		shot(course + "_" + (index + 1) + "a_before");

		// 2. THE OBSTACLE IS ON SCREEN AND THE MENU HAS THE VERB ON IT. This is the check that the
		// engine side cannot make: reachedLoc says a tile is close enough, and the client says
		// whether a player standing there can actually click the thing.
		// AIM AT THE MIDDLE OF THE FOOTPRINT. A four-tile tree clicked at its origin corner is
		// aimed at its edge, and the model above that corner can be branches and sky.
		int[] sxy = aimAt(o.x + (o.w - 1) / 2, o.z + (o.l - 1) / 2);
		if (sxy == null) {
			check("the cursor can be put on the obstacle's tile", false,
				"it is not on screen from here - " + aimWhy);
			return;
		}
		int[] on = findVerb(sxy[0], sxy[1], o.verb);
		// AND IF IT IS NOT THERE, LOWER THE CAMERA AND LOOK AGAIN.
		//
		// The client draws every storey up to the one the player is on, so from the street with
		// rooftops overhead a steep camera is looking at the roofs: the pick at Varrock's rough
		// wall came back with a blocked roof tile and a menu of nothing but Cancel. A player would
		// drag the camera down to see the wall, so this does - two pitches, nearly level and
		// steeper than the default - and restores it afterwards so the photographs stay comparable.
		if (on == null) {
			int wasPitch = pinPitch;
			for (int pitch : new int[] { 160, 128, 340 }) {
				pinPitch = pitch;
				waitFrames(8);
				int[] again = aimAt(o.x + (o.w - 1) / 2, o.z + (o.l - 1) / 2);
				if (again != null) {
					on = findVerb(again[0], again[1], o.verb);
					if (on != null) {
						say("      (found it with the camera at pitch " + pitch + ")");
						break;
					}
				}
			}
			if (on == null) {
				pinPitch = wasPitch;
				waitFrames(6);
			}
		}
		if (on == null) {
			hover(sxy[0], sxy[1]);
			waitFrames(4);
			check("the obstacle can be clicked from here ('" + o.verb + "')", false,
				"nothing in the band above its tile offers it; over the tile itself the menu is " + menu());
			return;
		}
		sxy = on;
		List<String> rows = menu();
		say("      menu: " + rows + "   at " + (sxy[0] - app.getWidth() / 2) + "," + (sxy[1] - app.getHeight() / 2) + " from centre");
		int row = rowOf(o.verb);
		check("the obstacle can be clicked from here ('" + o.verb + "')", row >= 0, "");
		if (row < 0) {
			return;
		}

		// 3. CLICK IT - a real left click when the verb is already the left-click option, which is
		// the path a player takes, and the client's own dispatch otherwise.
		int size = geti("menuSize");
		int before = menuOptions;
		lastOption = "";
		// A LEFT CLICK WHEN THE VERB IS ALREADY THE LEFT-CLICK OPTION, which is the path a player
		// takes, and the client's own dispatch when it is not. Twelve frames to answer, not three:
		// the press is read in one frame, the release in another, and the action runs in the
		// update after that - judging it at three frames called every click a dud.
		if (row == size - 1) {
			click(sxy[0], sxy[1], false);
			for (int i = 0; i < 12 && menuOptions == before; i++) {
				waitFrames(1);
			}
			if (menuOptions == before) {
				// one retry, in case the press landed on a frame that rebuilt the menu
				click(sxy[0], sxy[1], false);
				for (int i = 0; i < 12 && menuOptions == before; i++) {
					waitFrames(1);
				}
			}
		} else {
			call("useMenuOption", Integer.valueOf(row));
			waitFrames(4);
		}
		check("clicking it ran '" + lastOption + "'", menuOptions > before && lastOption.startsWith(o.verb),
			menuOptions == before ? "the click ran nothing at all" : "");

		// 4. WATCH IT PLAY OUT, frame by frame, in DRAWN position - which is where the player sees
		// himself, glide and all, rather than where the server has put him.
		List<String> path = new ArrayList<String>();
		int airborne = 0;
		double[] last = drawnAt();
		int still = 0;
		boolean shotMid = false;
		// A TELEJUMP ARRIVES A TICK AFTER THE ANIMATION. The climb up a rough wall plays where the
		// player stands, waits a tick and only then moves them, so "still for a while" is not the
		// same as finished: judging it after 25 still frames reported the wall as landing the
		// player on the wall. A tick is about thirty frames here, so two ticks of stillness it is.
		for (int f = 0; f < 300; f++) {
			waitFrames(1);
			double[] d = drawnAt();
			int[] t = tileAt();
			String tile = t[0] + "," + t[1] + "L" + t[2];
			if (path.isEmpty() || !path.get(path.size() - 1).equals(tile)) {
				path.add(tile);
				if (!floorAt(t[0], t[1], t[2])) {
					airborne++;
				}
			}
			if (!shotMid && (Math.abs(d[0] - last[0]) > 0.4 || Math.abs(d[1] - last[1]) > 0.4)) {
				shot(course + "_" + (index + 1) + "b_moving");
				shotMid = true;
			}
			if (Math.abs(d[0] - last[0]) < 0.01 && Math.abs(d[1] - last[1]) < 0.01) {
				still++;
				if (still > 65 && f > 90) {
					break;
				}
			} else {
				still = 0;
			}
			last = d;
		}
		if (!shotMid) {
			shot(course + "_" + (index + 1) + "b_moving");
		}
		waitFrames(10);
		shot(course + "_" + (index + 1) + "c_after");
		say("      drawn path: " + path);

		// 5. WHERE IT PUT US, against the course's own constant.
		int[] end = tileAt();
		boolean landed = end[0] == o.landX && end[1] == o.landZ && end[2] == o.landLevel;
		check("it landed on " + end[0] + "," + end[1] + " L" + end[2], landed,
			landed ? "" : "the constant says " + o.landX + "," + o.landZ + " L" + o.landLevel);

		// AND THE TILE IT LEFT US ON HAS SOMETHING UNDER IT.
		//
		// The tiles CROSSED are a different matter and must not be asserted: a tightrope, a clothes
		// line or a plank spans open air by definition, and at the storey the player is drawn on
		// there is nothing beneath them for the length of it. Varrock's clothes line crosses eight
		// tiles of which two are sky, and that is the obstacle working. The count is printed
		// because a run of them in the wrong place is still worth seeing.
		check("and the tile it left the player on has floor under it", floorAt(end[0], end[1], end[2]), "");
		if (airborne > 0) {
			say("      " + airborne + " of the " + path.size() + " tiles crossed had nothing drawn under them"
				+ (airborne > 1 ? " - expected on a rope or a line, not on a roof" : ""));
		}
		check("the crossing is " + (path.size() - 1) + " tiles", path.size() >= 2,
			path.size() < 2 ? "the player never moved" : "");

		// AND IT WENT IN A STRAIGHT LINE, for anything long enough for that to mean something.
		//
		// This is what catches the fault the landings had: ~agility_roof_move glides the player
		// from the obstacle to the landing in one slide, so a landing that is not in line with the
		// obstacle drags them diagonally across whatever is in between. Falador's first tightrope
		// slid ten tiles east and seven north off a three-tile rope. A climb or a drop changes
		// storey in two or three tiles and is left alone.
		// MEASURED OVER THE GLIDE ONLY, which is not the whole path. The op routes the player to the
		// obstacle first - the engine's own walk, one tile a tick, and diagonal whenever the last
		// landing was not in line with this obstacle, which is most of the time and is perfectly
		// fine. The glide starts afterwards, and Player.exactMove gives it away: it teleports the
		// player to the end before setting the mask, so the path contains one jump of several tiles
		// and then the drawn slide back from the start. Everything before that jump is walking.
		int jump = -1;
		for (int i = 1; i < path.size(); i++) {
			int[] a = tileOf(path.get(i - 1));
			int[] b = tileOf(path.get(i));
			if (Math.max(Math.abs(a[0] - b[0]), Math.abs(a[1] - b[1])) > 1) {
				jump = i;
				break;
			}
		}
		if (jump > 0 && path.size() - jump >= 3) {
			int[] start = tileOf(path.get(jump - 1));
			boolean straight = true;
			for (int i = jump; i < path.size(); i++) {
				int[] t = tileOf(path.get(i));
				if (t[0] != start[0] && t[1] != start[1]) {
					straight = false;
					break;
				}
			}
			check("and the crossing itself went in a straight line", straight,
				straight ? "" : "the glide from " + start[0] + "," + start[1]
					+ " moved on both axes, which means it slid across the roofs between");
		}
	}

	static void logIn(int w, int h) throws Exception {
		int lx = (Math.max(w, 765) - 765) / 2;
		int ly = (Math.max(h, 503) - 503) / 2;
		click(lx + 765 / 2 + 80, ly + 503 / 2 + 40, false);
		waitFor("the login box", 5000, () -> geti("titleScreenState") == 2);
		set("username", user);
		set("password", "cam12345");
		say("logging in as '" + user + "'");
		long giveUp = System.currentTimeMillis() + 150000L;
		while (true) {
			click(lx + 765 / 2 - 80, ly + 503 / 2 + 70, false);
			try {
				waitFor("the game", 15000, () -> app.ingame && geti("sceneState") == 2);
				break;
			} catch (RuntimeException e) {
				if (app.ingame || System.currentTimeMillis() > giveUp) {
					waitFor("the game", 45000, () -> app.ingame && geti("sceneState") == 2);
					break;
				}
				say("not in yet (" + get("loginMessage0") + " " + get("loginMessage1") + "), trying again");
				Thread.sleep(5000);
			}
		}
		say("logged in");
		waitFrames(50);
	}
}
