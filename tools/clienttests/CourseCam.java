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
import jagex2.dash3d.Model;
import jagex2.dash3d.Sprite;
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
	/** The standard camera, which every obstacle starts from however the last one had to be aimed. */
	static final int DEF_YAW = pinYaw;
	static final int DEF_PITCH = pinPitch;
	static final int DEF_ZOOM = pinZoom;
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
					sc.method312(pickX - layoutInt("vpScreenX"), pickY - layoutInt("vpScreenY"));
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

	/** The client's own idea of the player's Agility, which is what ::setstat has to move. */
	static int agilityLevel() {
		synchronized (LOCK) {
			try {
				int[] levels = (int[]) get("skillLevel");
				return levels == null || levels.length <= 16 ? -1 : levels[16];
			} catch (Throwable t) {
				return -1;
			}
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
	 * WHAT THE CLIENT ACTUALLY HAS ON A TILE, across every plane.
	 *
	 * A failed click reports the MENU, which is several inferences downstream of the question that
	 * matters: is the loc in the scene, and on which plane? World3D puts the scenery of a tile in
	 * Square.field1391 with the typecodes alongside in field1392, and a typecode carries its loc id
	 * at `>> 14 & 0x7FFF` - the same decode the client itself uses to build a loc's menu. So this
	 * reads them straight out and tells the three cases apart: not loaded, loaded on another plane,
	 * or there and not offering the op.
	 */
	static String locsAt(int worldX, int worldZ) {
		synchronized (LOCK) {
			World3D sc = (World3D) get("scene");
			if (sc == null) {
				return "no scene";
			}
			int x = worldX - geti("sceneBaseTileX");
			int z = worldZ - geti("sceneBaseTileZ");
			if (x < 0 || x > 103 || z < 0 || z > 103) {
				return "outside the loaded scene (" + x + "," + z + ")";
			}
			StringBuilder sb = new StringBuilder();
			for (int level = 0; level <= 3; level++) {
				Square sq = sc.field1018[level][x][z];
				if (sq == null) {
					continue;
				}
				StringBuilder ids = new StringBuilder();
				for (int i = 0; i < sq.field1390; i++) {
					if (ids.length() > 0) {
						ids.append(' ');
					}
					// THE TYPECODE IS ON THE SPRITE. Square.field1392 is the footprint edge mask
					// World3D.method287 builds, not an id, and decoding it gave every loc as 0.
					// Sprite.field88 IS the bitset, confirmed against Draynor: 1383255080 decodes to
					// 18891 and 1383271343 to 18892, which are the wall and the gap the course table
					// names. The two obvious-looking fields are not - Square.field1392 is the
					// footprint edge mask World3D.method287 builds, and Sprite.field81 is the
					// rotation, compared against 1664/384 angles by its caller.
					Sprite sp = sq.field1391[i];
					ids.append(sp == null ? "-" : String.valueOf(sp.field88 >> 14 & 0x7FFF));
				}
				sb.append(sb.length() > 0 ? "; " : "").append("L").append(level).append(": ");
				sb.append(sq.field1390).append(" loc(s)");
				if (ids.length() > 0) {
					sb.append(" [").append(ids).append(']');
				}
				if (sq.field1386 != null) {
					sb.append(" +wall");
				}
				if (sq.field1387 != null) {
					sb.append(" +decor");
				}
				if (sq.field1385 != null || sq.field1384 != null) {
					sb.append(" +floor");
				}
			}
			return sb.length() == 0 ? "no Square on any plane" : sb.toString();
		}
	}

	/**
	 * WHAT IS PLAYING ON THE PLAYER RIGHT NOW, or -1 for nothing.
	 *
	 * field1171 is the entity's current seq; the client clears it to -1 when the sequence runs out
	 * of frames. Sampled every frame through a crossing it says how long the animation lasted
	 * against how long the client spent gliding the player across - which is the whole question on
	 * an obstacle whose animation is 8 cycles and whose glide is 50.
	 */
	static int animNow() {
		synchronized (LOCK) {
			ClientPlayer me = (ClientPlayer) get("localPlayer");
			return me == null ? -1 : me.field1171;
		}
	}

	/**
	 * AND THE WALK BAS, which is the other way an obstacle animates.
	 *
	 * field1176 is the stance/walk sequence the entity is rendered with between one-shot anims. A
	 * tightrope, a plank or a narrow wall is crossed by ~agility_force_move, which sets this to
	 * human_walk_logbalance and then WALKS the player over - no one-shot seq is involved at all, so
	 * reading only field1171 calls those obstacles silent when they are working.
	 */
	static int basNow() {
		synchronized (LOCK) {
			ClientPlayer me = (ClientPlayer) get("localPlayer");
			return me == null ? -1 : me.field1176;
		}
	}

	/**
	 * IS THERE FLOOR DRAWN HERE? The client's scene keeps one Square per tile per level, and the
	 * floor is a Ground (a shaped tile) or a QuickGround (a flat one). A Square with neither is a
	 * tile the renderer draws nothing for: standing on it is standing in thin air, which is what
	 * both Draynor tightropes shipped doing.
	 */
	/**
	 * WHICH STOREY DRAWS THE FLOOR UNDER THIS TILE - this one, the one below, or neither (-1).
	 *
	 * A rooftop landing routinely has nothing on its own plane. The deck-over-roof rule strips a
	 * rooftop tile's floor overlay wherever a roof model covers it, and that model is drawn on the
	 * storey below, so the player stands on the roof while the renderer has their own plane empty.
	 * Canifis is every obstacle of a course like that, and asking only about the player's plane
	 * called a landing that a screenshot shows working "thin air".
	 *
	 * The storey is returned rather than a boolean so a report can say where the floor came from:
	 * one below is normal on a roof, neither is the fault worth catching.
	 */
	static int floorStorey(int worldX, int worldZ, int level) {
		if (floorAt(worldX, worldZ, level)) {
			return level;
		}
		if (level > 0 && floorAt(worldX, worldZ, level - 1)) {
			return level - 1;
		}
		return -1;
	}

	/**
	 * IS THERE TERRAIN under this tile, on this storey or the one below - and nothing else counts.
	 *
	 * A DIFFERENT QUESTION FROM floorAt, which asks whether anything at all is drawn and counts a
	 * centrepiece loc, because a stall or a walkway really does hold the player up. This one
	 * answers "was the tile open air", which is what tells a vault over a street from a slide
	 * across the roofs: a roof has ground under it, the gap between two roofs has none, and a stall
	 * standing on that gap has a loc. One predicate was answering both questions, so counting locs
	 * as floor - right for the first - silently broke the second on three obstacles at once.
	 */
	static boolean terrainAt(int worldX, int worldZ, int level) {
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
			for (int l = level; l >= Math.max(0, level - 1); l--) {
				Square sq = sc.field1018[l][x][z];
				if (sq != null && (sq.field1385 != null || sq.field1384 != null)) {
					return true;
				}
			}
			return false;
		}
	}

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
			// AND THE SCENERY LIST IS WHERE THAT LOC IS. This used to ask about everything except
			// field1391 and then excuse the gap by claiming scenery is kept at its origin tile
			// only - which is not what the client does. World3D.method287 loops over every tile of
			// the footprint and writes the Sprite into that Square, the whole span rather than the
			// corner. Eight failures of the nine-course run over four tiles were this, and the
			// photograph of one of them has the player standing on a drawn walkway.
			return sq.field1385 != null
				|| sq.field1384 != null
				|| sq.field1389 != null
				|| sq.field1388 != null
				|| sq.field1386 != null
				|| sq.field1387 != null
				|| sq.field1390 > 0;
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
	/**
	 * THE CLIENT'S OWN PROJECTION, DONE HERE, so the answer cannot depend on what was bound last.
	 *
	 * projectFromGround writes its answer through Pix3D.zoom and Pix3D.centerX, which belong to
	 * whichever drawing area was bound when it is called: the viewport inside drawScene, but the
	 * chatbox, the sidebar or an item sprite ObjType rendered when it is called from a test thread
	 * between frames. The answer is then a point in a space that is not on the screen, which is how
	 * the first run of this clicked a chest of drawers inside a house while aiming at a rooftop.
	 *
	 * The arithmetic is only a rotation into the camera, so it is repeated here against the layout's
	 * own viewport and zoom, which do not move between frames. Takes an ABSOLUTE scene y, where the
	 * client's version takes a height above the ground and looks the ground up for itself, because
	 * what this is aimed at is a loc's model and the scene already knows the y it was drawn at.
	 *
	 * Returns a window point, or null when the point is behind the camera.
	 */
	static int[] project(int sceneX, int sceneY, int sceneZ) {
		int zoom = layoutInt("zoom");
		int cx = layoutInt("vpW") / 2 + layoutInt("vpScreenX");
		int cy = layoutInt("vpH") / 2 + layoutInt("vpScreenY");
		int dx = sceneX - app.cameraX;
		int dy = sceneY - app.cameraY;
		int dz = sceneZ - app.cameraZ;
		int sinPitch = Model.sinTable[app.cameraPitch];
		int cosPitch = Model.cosTable[app.cameraPitch];
		int sinYaw = Model.sinTable[app.cameraYaw];
		int cosYaw = Model.cosTable[app.cameraYaw];
		int across = dx * cosYaw + dz * sinYaw >> 16;
		int along = dz * cosYaw - dx * sinYaw >> 16;
		int up = dy * cosPitch - sinPitch * along >> 16;
		int depth = dy * sinPitch + cosPitch * along >> 16;
		if (depth < 50) {
			return null;
		}
		return new int[] { across * zoom / depth + cx, up * zoom / depth + cy };
	}

	static boolean onScreen(int[] p) {
		return p != null && p[0] >= 8 && p[1] >= 8
			&& p[0] <= app.getWidth() - 8 && p[1] <= app.getHeight() - 8;
	}

	/**
	 * THE SPRITE THE CLIENT DREW FOR THIS OBSTACLE, found by the loc id the course table names.
	 *
	 * A loc is registered on every tile of its footprint, so its own tile almost always holds it -
	 * but the course constants name the tile a PLAYER stands on for several obstacles, which is
	 * beside the thing rather than on it, so a few tiles of slack and the nearest match wins. Every
	 * plane is searched because a tile with LINK_BELOW drops its locs to the plane underneath, and
	 * the table's level is the player's.
	 */
	static Sprite spriteOf(Ob o) {
		synchronized (LOCK) {
			World3D sc = (World3D) get("scene");
			if (sc == null) {
				return null;
			}
			int ox = o.x - geti("sceneBaseTileX");
			int oz = o.z - geti("sceneBaseTileZ");
			Sprite best = null;
			int bestScore = Integer.MAX_VALUE;
			for (int level = 0; level <= 3; level++) {
				for (int x = ox - 4; x <= ox + 4; x++) {
					for (int z = oz - 4; z <= oz + 4; z++) {
						if (x < 0 || x > 103 || z < 0 || z > 103) {
							continue;
						}
						Square sq = sc.field1018[level][x][z];
						if (sq == null) {
							continue;
						}
						for (int i = 0; i < sq.field1390; i++) {
							Sprite sp = sq.field1391[i];
							if (sp == null || (sp.field88 >> 14 & 0x7FFF) != o.locId) {
								continue;
							}
							// the tile matters more than the plane, and the plane breaks a tie
							int score = 8 * (Math.abs(x - ox) + Math.abs(z - oz))
								+ Math.abs(level - o.level);
							if (score < bestScore) {
								bestScore = score;
								best = sp;
							}
						}
					}
				}
			}
			return best;
		}
	}

	/**
	 * POINT AT THE OBSTACLE'S MODEL, which is what the band of points below is guessing at.
	 *
	 * The Sprite says where the model was drawn and how tall it is, so this walks up its body -
	 * the middle first, where a model is usually widest, then the shoulders and the feet for
	 * anything shaped like a ladder - and tries a small cross at each height, because the client
	 * picks locs triangle by triangle and a plate of hand holds is a few pixels of them.
	 *
	 * Returns the point the verb was found at, or null, in which case the tile-and-band search
	 * below is still worth running: a loc whose own model is behind a gable can still be clickable
	 * at the edge of it.
	 */
	/**
	 * WHERE THE PROJECTION PUTS THE PLAYER, which the camera orbits, so the answer is known: near
	 * the middle of the window. Printed with everything it was computed from, because when it is
	 * wrong the question is which of those numbers is not the one the scene was drawn with.
	 */
	static String selfCheck() {
		int[] me = tileAt();
		int x = (me[0] - geti("sceneBaseTileX")) * 128 + 64;
		int z = (me[1] - geti("sceneBaseTileZ")) * 128 + 64;
		int[] p = project(x, app.getHeightmapY(z, x, geti("currentLevel")), z);
		return "the player at " + me[0] + "," + me[1] + " projects to "
			+ (p == null ? "behind the camera" : p[0] + "," + p[1])
			+ " in a " + app.getWidth() + "x" + app.getHeight() + " window"
			+ " (zoom " + layoutInt("zoom") + ", vp " + layoutInt("vpW") + "x" + layoutInt("vpH")
			+ " at " + layoutInt("vpScreenX") + "," + layoutInt("vpScreenY")
			+ ", camera " + app.cameraX + "," + app.cameraY + "," + app.cameraZ
			+ " pitch " + app.cameraPitch + " yaw " + app.cameraYaw + ")";
	}

	/**
	 * SWING THE CAMERA ROUND TO THE OBSTACLE, and pull it back until the model is on the window.
	 *
	 * The cam looks north at a fixed yaw with the camera pulled in close, which frames the player
	 * and about eight tiles around them. Half of these obstacles are further away than that -
	 * Canifis' gap 2 is eight tiles west and sixteen north - and every aim before this moved the
	 * cursor and left the camera where it was, which is the one thing a player would not do.
	 *
	 * The yaw is the one that puts the model straight ahead: across = dx.cos + dz.sin is zero when
	 * the yaw is atan2(-dx, dz), and then the depth is the full distance, so the model is in front
	 * rather than behind. Angles are the client's own 2048 to the turn.
	 *
	 * Pulling back is a second resort because it costs detail in the photograph: cameraZoomOffset
	 * is subtracted from the camera's distance, so a smaller one stands further off.
	 */
	static int[] turnToModel(Ob o) throws Exception {
		Sprite sp = spriteOf(o);
		if (sp == null) {
			return null;
		}
		int[] me = tileAt();
		int px = (me[0] - geti("sceneBaseTileX")) * 128 + 64;
		int pz = (me[1] - geti("sceneBaseTileZ")) * 128 + 64;
		if (Math.abs(sp.field78 - px) < 64 && Math.abs(sp.field79 - pz) < 64) {
			return null;
		}
		int yaw = (int) Math.round(Math.atan2(-(sp.field78 - px), sp.field79 - pz) * 1024.0 / Math.PI)
			& 0x7FF;
		// AND IT HAS TO COME DOWN AS WELL AS ROUND. Varrock's leap and Al Kharid's zip line both
		// project to the middle of the window once the yaw is right and to fifty-odd pixels ABOVE
		// its top: a camera looking down at pitch 280 pushes anything far away towards the horizon
		// and off the frame. Working the zip line by hand, pitch 228 puts it at y = 99 and pitch
		// 128 at y = 324. Nearest to the standard camera first, and an obstacle already in frame
		// pays nothing for this, because aimAtModel rejects a projection off the window without
		// hovering anything.
		for (int zoom : new int[] { DEF_ZOOM, 300, 100 }) {
			for (int pitch : new int[] { DEF_PITCH, 228, 180, 128 }) {
				pinYaw = yaw;
				pinPitch = pitch;
				pinZoom = zoom;
				// WAIT FOR THE CAMERA TO GET THERE. The pin is written every frame and the orbit
				// camera is what the scene is drawn with, so the projection is wrong until they agree.
				for (int f = 0; f < 60 && (app.cameraYaw != yaw || app.cameraPitch != pitch); f++) {
					waitFrames(1);
				}
				waitFrames(4);
				int[] on = aimAtModel(o);
				if (on != null) {
					say("      (turned the camera to yaw " + yaw
						+ (pitch != DEF_PITCH ? ", brought it down to pitch " + pitch : "")
						+ (zoom != DEF_ZOOM ? ", stood it back at zoom " + zoom : "") + " to see it)");
					return on;
				}
			}
		}
		pinYaw = DEF_YAW;
		pinPitch = DEF_PITCH;
		pinZoom = DEF_ZOOM;
		waitFrames(8);
		return null;
	}

	static String modelWhy = "";

	static int[] aimAtModel(Ob o) throws Exception {
		modelWhy = "";
		Sprite sp = spriteOf(o);
		if (sp == null) {
			modelWhy = "no sprite with loc " + o.locId + " within four tiles of " + o.x + "," + o.z;
			return null;
		}
		int height = sp.field80 == null ? 0 : sp.field80.field1709;
		int offScreen = 0;
		int tried = 0;
		String first = "";
		for (int quarter : new int[] { 2, 1, 3, 0, 4 }) {
			int[] at = project(sp.field78, sp.field77 - height * quarter / 4, sp.field79);
			if (!onScreen(at)) {
				offScreen++;
				if (first.isEmpty()) {
					first = at == null ? "behind the camera"
						: "off the window at " + at[0] + "," + at[1];
				}
				continue;
			}
			tried++;
			if (first.isEmpty()) {
				// THE PROJECTION, AGAINST THE CLIENT'S OWN PICK. If the pick at this pixel answers
				// the obstacle's tile the arithmetic is sound and the verb is what is missing; if it
				// answers somewhere else the arithmetic is what is wrong.
				int[] under = pickAt(at[0], at[1]);
				first = "the model's middle projects to " + at[0] + "," + at[1] + " where the pick says "
					+ (under == null ? "nothing" : under[0] + "," + under[1]);
			}
			for (int[] d : new int[][] { { 0, 0 }, { -8, 0 }, { 8, 0 }, { 0, -8 }, { 0, 8 } }) {
				int x = at[0] + d[0];
				int y = at[1] + d[1];
				if (x < 8 || y < 8 || x > app.getWidth() - 8 || y > app.getHeight() - 8) {
					continue;
				}
				hover(x, y);
				waitFrames(3);
				if (rowOf(o.verb) >= 0) {
					return new int[] { x, y };
				}
			}
		}
		modelWhy = "sprite " + o.locId + " is at " + sp.field78 + "," + sp.field77 + "," + sp.field79
			+ " L" + sp.field76 + ", " + height + " tall, rotation " + sp.field81 + "; " + first
			+ "; " + tried + " of 5 heights on screen" + (offScreen > 0 ? ", " + offScreen + " not" : "")
			+ ", none of them offering '" + o.verb + "'; " + selfCheck();
		return null;
	}

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
			// NOTHING AT THE MIDDLE IS NOT NOTHING ANYWHERE. The middle of the window is sky
			// whenever the camera is low or the player is on a roof edge, and the Newton steps below
			// need a tile to start from - but the sweep does not, so it is the answer here rather
			// than abandoning the obstacle.
			aimWhy = "the scene pick found no tile at the middle of the window (" + px + "," + py
				+ "), after " + picks + " picks, swept the window instead";
			return sweepFor(worldX, worldZ);
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
	/**
	 * WALK A FEW TILES TOWARDS SOMETHING, the way a player would, and say whether we moved.
	 *
	 * Stops one tile short of the target: an obstacle's own tile is routinely blocked, because
	 * o161/o175 with f1 is how this build marks the span of one, and clicking a blocked tile walks
	 * nowhere. Tries the longest step first and shortens it, since a tile far across a roof is the
	 * one most likely to be off the window or behind a gable.
	 *
	 * The walk is taken off the menu rather than by a bare left click, so a loc lying under the
	 * cursor cannot have its own default option run by accident.
	 */
	static boolean walkToward(int targetX, int targetZ) throws Exception {
		int[] me = tileAt();
		int dx = targetX - me[0];
		int dz = targetZ - me[1];
		int dist = Math.max(Math.abs(dx), Math.abs(dz));
		if (dist <= 1) {
			return false;
		}
		for (int step = Math.min(dist - 1, 6); step >= 1; step--) {
			int tx = me[0] + (int) Math.round((double) dx * step / dist);
			int tz = me[1] + (int) Math.round((double) dz * step / dist);
			if (tx == me[0] && tz == me[1]) {
				continue;
			}
			int[] p = screenOf(tx, tz);
			if (p == null) {
				continue;
			}
			hover(p[0], p[1]);
			waitFrames(4);
			int row = rowOf("Walk here");
			if (row < 0) {
				continue;
			}
			call("useMenuOption", Integer.valueOf(row));
			// LET IT ARRIVE. A walk is a tile a tick, so a handful of tiles is a couple of
			// seconds; judging it by stillness rather than a fixed wait keeps short walks short.
			int[] last = tileAt();
			int still = 0;
			for (int f = 0; f < 200; f++) {
				waitFrames(1);
				int[] now = tileAt();
				if (now[0] == last[0] && now[1] == last[1]) {
					if (++still > 40) {
						break;
					}
				} else {
					still = 0;
					last = now;
				}
			}
			// AND IT HAS TO HAVE GOT CLOSER. The projection can land on a tile that is not the one
			// it was computed for, and then "Walk here" walks the wrong way: Al Kharid's zip line
			// is twenty-three tiles east, and four unchecked walks left the player twenty-one
			// tiles further west than they started. One wrong step is a nuisance; four is the
			// player on the other side of the city.
			int[] end = tileAt();
			if (end[0] == me[0] && end[1] == me[1]) {
				continue;
			}
			int was = Math.max(Math.abs(targetX - me[0]), Math.abs(targetZ - me[1]));
			int now = Math.max(Math.abs(targetX - end[0]), Math.abs(targetZ - end[1]));
			if (now < was) {
				return true;
			}
			say("      (a walk towards " + targetX + "," + targetZ + " ended at " + end[0] + ","
				+ end[1] + ", " + now + " tiles away against " + was + " before - not walking again)");
			return false;
		}
		return false;
	}

	static String lookWhy = "";

	/**
	 * LOOK FOR THE OBSTACLE FROM WHERE WE STAND: aim at the middle of its footprint, hunt the verb
	 * in the band above it, and if that fails lower the camera and look again.
	 *
	 * The client draws every storey up to the one the player is on, so from the street with
	 * rooftops overhead a steep camera is looking at the roofs: the pick at Varrock's rough wall
	 * came back with a blocked roof tile and a menu of nothing but Cancel. A player would drag the
	 * camera down to see the wall, so this does - two pitches, nearly level and steeper than the
	 * default - and restores it afterwards so the photographs stay comparable.
	 */
	static int[] lookFor(Ob o) throws Exception {
		lookWhy = "";
		// THE LOC'S OWN MODEL FIRST, at the pixel the client drew it at. Everything after this is a
		// search for where the model went, and a search is what fifteen of the twenty-nine failures
		// of the nine-course run were - "nothing in the band above its tile offers it", with the
		// scene naming the loc on the next line.
		int[] atModel = aimAtModel(o);
		if (atModel != null) {
			return atModel;
		}
		String fromStandard = modelWhy;
		atModel = turnToModel(o);
		if (atModel != null) {
			return atModel;
		}
		// AIM AT THE MIDDLE OF THE FOOTPRINT. A four-tile tree clicked at its origin corner is
		// aimed at its edge, and the model above that corner can be branches and sky.
		int cx = o.x + (o.w - 1) / 2;
		int cz = o.z + (o.l - 1) / 2;
		int[] sxy = aimAt(cx, cz);
		if (sxy == null) {
			lookWhy = "it is not on screen from here - " + aimWhy + "; by its model, " + fromStandard
				+ "; and with the camera turned onto it, " + modelWhy;
			return null;
		}
		int[] on = findVerb(sxy[0], sxy[1], o.verb);
		if (on == null) {
			int wasPitch = pinPitch;
			for (int pitch : new int[] { 160, 128, 340 }) {
				pinPitch = pitch;
				waitFrames(8);
				int[] again = aimAt(cx, cz);
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
			lookWhy = "nothing in the band above its tile offers it; over the tile itself the menu is "
				+ menu() + "; scene says " + locsAt(o.x, o.z) + "; by its model, " + fromStandard
				+ "; and with the camera turned onto it, " + modelWhy;
		}
		return on;
	}

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

	/**
	 * Project the middle of a world tile to a window point, or null when it is not on screen.
	 *
	 * Through project() rather than the client's projectFromGround, because this is what walkToward
	 * steers by and that projection answering in the space of whatever was bound last is the race
	 * that sent the player twenty-one tiles the wrong way across Al Kharid.
	 */
	static int[] screenOf(int worldX, int worldZ) {
		synchronized (LOCK) {
			int x = worldX - geti("sceneBaseTileX");
			int z = worldZ - geti("sceneBaseTileZ");
			if (x < 0 || x > 103 || z < 0 || z > 103) {
				return null;
			}
			int sx = x * 128 + 64;
			int sz = z * 128 + 64;
			int[] p = project(sx, app.getHeightmapY(sz, sx, geti("currentLevel")), sz);
			return onScreen(p) ? p : null;
		}
	}

	/**
	 * A field of the LAYOUT, which is an instance and not a class.
	 *
	 * This read them statically until now, so every call threw and came back 0 - harmless in the
	 * resizable layout the cam runs in, where the viewport is the window and its origin is 0,0, and
	 * wrong by the frame's four-pixel border in fixed.
	 */
	static int layoutInt(String name) {
		try {
			Object layout = get("layout");
			Field f = layout.getClass().getDeclaredField(name);
			f.setAccessible(true);
			return ((Number) f.get(layout)).intValue();
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
		// WHERE A FAILURE DROPS YOU, or level -1 when Old School does not let you fail this one.
		// Eleven of the seventy-three can be failed; see tools/sim/courses.ts.
		int fallX;
		int fallZ;
		int fallLevel = -1;
	}

	static String course;
	static int courseLevel;
	/** The level this course stops being failable at, or 0 when nothing on it can be failed. */
	static int failStop;
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
				if (f.length > 6) {
					failStop = Integer.parseInt(f[6]);
				}
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
			if (f.length > 14 && !f[14].equals("-")) {
				o.fallX = Integer.parseInt(f[12]);
				o.fallZ = Integer.parseInt(f[13]);
				o.fallLevel = Integer.parseInt(f[14]);
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
		// ABOVE THE FAIL CUTOFF, not at the entry level.
		//
		// Eleven rooftop obstacles can be failed, and the entry level is where they are MOST
		// failable - about one attempt in eight. The engine-side suites pin nextDouble for this;
		// a client over a socket cannot, so it runs the lap at the level the course stops being
		// failable at instead. Nothing here depends on the level: what is drawn, what can be
		// clicked and where a crossing ends are the same at 60 and at 79, and the fall tiles are
		// checked by standing on them rather than by failing into them.
		//
		// Without this the lap is a coin toss. Seers' failed its tightrope on the run that added
		// the fall checks - it "landed on 2709,3489 L0", which IS ^seers_fall_rope - and the next
		// obstacle could not be reached from the street, so one roll cost two FAIL lines.
		int runLevel = Math.max(courseLevel, failStop);
		// AND CHECK IT ARRIVED. A typed command is one character a frame and a dropped frame
		// leaves half of it behind - ::tele has been wrapped in a retry for that since the first
		// week. ::setstat was not, and the cost is a whole course: Varrock came back 39 ok and 9
		// FAIL because the level never took, ~varrock_blocked returned at the first obstacle, and
		// the cam had already PRINTED that it set the level.
		int gotLevel = -1;
		for (int tryn = 0; tryn < 4 && gotLevel != runLevel; tryn++) {
			command("::setstat agility " + runLevel);
			for (int f = 0; f < 60 && gotLevel != runLevel; f++) {
				waitFrames(2);
				gotLevel = agilityLevel();
			}
		}
		check("agility is " + gotLevel + " for the run", gotLevel == runLevel,
			gotLevel == runLevel ? "" : "::setstat agility " + runLevel + " never took, and every"
				+ " obstacle below is gated on it - the course's own level is " + courseLevel);
		if (runLevel != courseLevel && gotLevel == runLevel) {
			say("(agility " + runLevel + " for the run, not the course's own " + courseLevel
				+ ": that is where this course stops being failable, and a lap that can fail an"
				+ " obstacle at random is not a lap anybody can compare)");
		}
		pinCamera = true;

		say("");
		say("================ " + course.toUpperCase() + "   (agility " + courseLevel + ", " + obstacles.size() + " obstacles)");

		int[] from = new int[] { start[0], start[1], start[2] };
		for (int i = 0; i < obstacles.size(); i++) {
			Ob o = obstacles.get(i);
			say("");
			say(" " + (i + 1) + ". " + o.name + "  loc " + o.locId + " at " + o.x + "," + o.z + " L" + o.level
				+ "   from " + from[0] + "," + from[1] + " L" + from[2]);
			boolean crossed = doObstacle(o, from, i);
			// A FAILED OBSTACLE MUST NOT DRAG THE REST OF THE COURSE OFF COURSE. The next obstacle
			// starts from wherever the player actually ended up, which is right for a lap and was
			// harmless while a failure left them where it found them. Walking broke that: Canifis
			// obstacle two walks looking for a gap it cannot see, fails, and leaves the player six
			// tiles from where obstacle three expects to begin - which then reported a landing
			// failure that has nothing to do with the obstacle.
			//
			// So an obstacle that did not complete puts the player on its own landing constant.
			// That costs the obstacles after it nothing, and it is said out loud rather than
			// letting a reader think the player got there by crossing.
			if (!crossed && o.landX > 0) {
				command("::tele " + teleArg(o.landX, o.landZ, o.landLevel));
				waitFor("the scene", 30000, () -> geti("sceneState") == 2);
				// WAIT FOR THE TELEPORT BY ITS RESULT, not by a count of frames. tileAt() is
				// sceneBaseTileX plus the player's local offset, so while the scene is still being
				// rebuilt the two halves disagree and the answer is a tile nowhere near the
				// course. Draynor's narrow wall lands on 3088,3261 and the next obstacle reported
				// starting "from 3088,3206" - fifty-five tiles out, with everything after it
				// measured from there.
				boolean there = false;
				for (int f = 0; f < 120 && !there; f++) {
					waitFrames(2);
					int[] t = tileAt();
					there = t[0] == o.landX && t[1] == o.landZ && t[2] == o.landLevel;
				}
				int[] t = tileAt();
				say("      (placed on this obstacle's landing " + o.landX + "," + o.landZ
					+ " L" + o.landLevel + " so the rest of the course can still be tested"
					+ (there ? "" : " - BUT THE PLAYER IS AT " + t[0] + "," + t[1] + " L" + t[2]
						+ ", so what follows starts from the wrong tile") + ")");
			}
			int[] now = tileAt();
			from = new int[] { now[0], now[1], now[2] };
		}

		doFalls();

		say("");
		say(ok + " ok, " + bad + " FAIL");
		log.close();
		System.exit(bad > 0 ? 1 : 0);
	}

	/**
	 * THE TILES A FAILED OBSTACLE DROPS YOU ON, which until this course had any, nothing checked.
	 *
	 * Old School drops you off the roof when you fail - "failing the obstacle will drop the player
	 * below, requiring them to start the course over" - and the reason this build had no fall tiles
	 * is written in the courses themselves: "a fall needs a street tile per obstacle and that is
	 * guesswork". coursefall.ts derives them from the map and roofall.ts proves the mechanic with
	 * the roll pinned, but neither of those can see what the RENDERER draws, and a fall tile is a
	 * street tile under a grafted roof - which is the likeliest place in this build for a hole.
	 * Nineteen of the fifty-eight ordinary landings were in thin air with every engine check green.
	 *
	 * Same two questions as a stand tile, then: is the player really there, and does the client
	 * draw a floor under them.
	 */
	static void doFalls() throws Exception {
		List<Ob> falls = new ArrayList<Ob>();
		for (int i = 0; i < obstacles.size(); i++) {
			if (obstacles.get(i).fallLevel >= 0) {
				falls.add(obstacles.get(i));
			}
		}
		if (falls.isEmpty()) {
			return;
		}
		pinYaw = DEF_YAW;
		pinPitch = DEF_PITCH;
		pinZoom = DEF_ZOOM;
		say("");
		say("---------------- where a failure drops you (" + falls.size() + " of " + obstacles.size()
			+ " obstacles can be failed)");
		for (int i = 0; i < falls.size(); i++) {
			Ob o = falls.get(i);
			say("");
			say(" " + o.name + " falls to " + o.fallX + "," + o.fallZ + " L" + o.fallLevel);
			int[] at = tileAt();
			for (int tryn = 0; tryn < 3; tryn++) {
				if (at[0] == o.fallX && at[1] == o.fallZ && at[2] == o.fallLevel) {
					break;
				}
				command("::tele " + teleArg(o.fallX, o.fallZ, o.fallLevel));
				waitFor("the scene", 30000, () -> geti("sceneState") == 2);
				// BY ITS RESULT, not by a frame count: while the scene is rebuilding,
				// sceneBaseTileX and the player's local offset disagree and tileAt() answers with
				// a tile nowhere near the course.
				boolean there = false;
				for (int f = 0; f < 120 && !there; f++) {
					waitFrames(2);
					int[] t = tileAt();
					there = t[0] == o.fallX && t[1] == o.fallZ && t[2] == o.fallLevel;
				}
				at = tileAt();
			}
			check("the player is standing at " + at[0] + "," + at[1] + " L" + at[2],
				at[0] == o.fallX && at[1] == o.fallZ && at[2] == o.fallLevel,
				at[0] == o.fallX && at[1] == o.fallZ && at[2] == o.fallLevel
					? "" : "wanted " + o.fallX + "," + o.fallZ + " L" + o.fallLevel);
			// WAIT FOR THE ANSWER, not for a frame count. sceneState is 2 and tileAt() is right
			// before the squares around the player have been filled in, so a floorStorey read
			// straight after a teleport can come back empty on a tile that is perfectly solid -
			// 3212,3414 passed this check twice and then failed it once, having moved nowhere.
			int storey = floorStorey(o.fallX, o.fallZ, o.fallLevel);
			for (int f = 0; f < 120 && storey < 0; f++) {
				waitFrames(2);
				storey = floorStorey(o.fallX, o.fallZ, o.fallLevel);
			}
			check("and the client draws floor under the fall tile", storey >= 0,
				storey >= 0 ? "" : "nothing is drawn on this storey or the one below - a player who"
					+ " fails this obstacle lands in thin air");
			if (storey >= 0 && storey != o.fallLevel) {
				say("      (the floor under it is drawn on L" + storey + ")");
			}
			shot(course + "_fall_" + (i + 1) + "_" + o.name.replace(' ', '_'));
		}
	}

	/** level,mapsquareX,mapsquareZ,localX,localZ - what ::tele takes. */
	static String teleArg(int x, int z, int level) {
		return level + "," + (x / 64) + "," + (z / 64) + "," + (x % 64) + "," + (z % 64);
	}

	/** Returns whether the obstacle actually carried the player across it. */
	static boolean doObstacle(Ob o, int[] from, int index) throws Exception {
		// THE STANDARD CAMERA, however the last obstacle had to be aimed. A turn that is not undone
		// reframes every obstacle after it, and then two runs of the same course are not comparable.
		pinYaw = DEF_YAW;
		pinPitch = DEF_PITCH;
		pinZoom = DEF_ZOOM;
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
		int standFloor = floorStorey(at[0], at[1], at[2]);
		check("and the client draws floor under it", standFloor >= 0,
			standFloor >= 0 ? "" : "nothing is drawn on this storey or the one below");
		if (standFloor >= 0 && standFloor != at[2]) {
			say("      (the floor under it is the roof drawn on L" + standFloor + " - deck over roof)");
		}
		shot(course + "_" + (index + 1) + "a_before");

		// 2. THE OBSTACLE IS ON SCREEN AND THE MENU HAS THE VERB ON IT. This is the check that the
		// engine side cannot make: reachedLoc says a tile is close enough, and the client says
		// whether a player standing there can actually click the thing.
		int[] on = lookFor(o);
		String fromLanding = lookWhy;

		// AND IF IT CANNOT BE SEEN FROM THE LANDING, WALK TOWARDS IT AND LOOK AGAIN.
		//
		// The scene diagnostic is what made this worth doing: on Draynor the wall and the gap ARE
		// in the client's scene, on plane 3, as 18891 and 18892 - the course table's own ids. They
		// simply cannot be reached with the cursor from five tiles away across a roof, which is not
		// how the obstacle behaves for a player, who walks over and clicks it. Stopping there left
		// every obstacle after it on the course untested.
		int walks = 0;
		int[] startedAt = tileAt();
		while (on == null && walks < 4) {
			if (!walkToward(o.x + (o.w - 1) / 2, o.z + (o.l - 1) / 2)) {
				break;
			}
			walks++;
			on = lookFor(o);
		}
		if (on == null) {
			say("      the client's scene at " + o.x + "," + o.z + " - " + locsAt(o.x, o.z));
			say("      and under the player at " + tileAt()[0] + "," + tileAt()[1] + " - "
				+ locsAt(tileAt()[0], tileAt()[1]));
			check("the obstacle can be clicked ('" + o.verb + "')", false,
				(walks > 0 ? "not from the landing, and not after walking " + walks + " time(s) either: " : "")
					+ lookWhy);
			return false;
		}
		// WORTH KNOWING EITHER WAY. Some landings are islands on purpose, so whether the obstacle
		// could be clicked without moving is a real result - it just is not a reason to abandon
		// the course.
		if (walks > 0) {
			int[] nowAt = tileAt();
			int moved = Math.max(Math.abs(nowAt[0] - startedAt[0]), Math.abs(nowAt[1] - startedAt[1]));
			say("      (not clickable from the landing, so walked " + moved + " tile(s) closer: "
				+ fromLanding + ")");
		}
		int[] sxy = on;
		List<String> rows = menu();
		say("      menu: " + rows + "   at " + (sxy[0] - app.getWidth() / 2) + "," + (sxy[1] - app.getHeight() / 2) + " from centre");
		int row = rowOf(o.verb);
		check("the obstacle can be clicked from here ('" + o.verb + "')", row >= 0, "");
		if (row < 0) {
			return false;
		}

		// 3. CLICK IT - a real left click when the verb is already the left-click option, which is
		// the path a player takes, and the client's own dispatch otherwise.
		// RE-READ THE MENU IMMEDIATELY BEFORE DISPATCHING. The client rebuilds it every frame from
		// wherever the cursor is, so a row index found several frames ago can be stale by the time
		// it is used - Draynor reported "clicking it ran 'Walk here'" on a tightrope, which is the
		// wrong row of a menu that had moved underneath it. Better to say the verb has gone than
		// to run whatever took its place.
		hover(sxy[0], sxy[1]);
		waitFrames(3);
		row = rowOf(o.verb);
		if (row < 0) {
			check("the verb is still on the menu when the click goes in ('" + o.verb + "')", false,
				"the menu rebuilt between finding it and clicking it: " + menu());
			return false;
		}
		// A LEFT CLICK WHEN THE VERB IS ALREADY THE LEFT-CLICK OPTION, which is the path a player
		// takes, and the client's own dispatch when it is not. Twelve frames to answer, not three:
		// the press is read in one frame, the release in another, and the action runs in the
		// update after that - judging it at three frames called every click a dud.
		//
		// AND THREE ATTEMPTS, because the menu belongs to the frame it was built in. Re-reading the
		// row immediately before dispatching narrowed that race without closing it: Canifis lost a
		// click to it in one run and Pollnivneach's basket lost one in the next, each with the
		// right verb on the menu a moment earlier. The obstacle is found again between attempts,
		// because a stray "Walk here" walks the player a tile and moves where it is on the screen.
		// WHERE THEY SET OUT FROM, read before the click: see the seed below.
		int[] preClick = tileAt();
		boolean ran = false;
		int tries = 0;
		while (!ran && tries < 3) {
			tries++;
			int size = geti("menuSize");
			int before = menuOptions;
			lastOption = "";
			if (row == size - 1) {
				click(sxy[0], sxy[1], false);
				for (int i = 0; i < 12 && menuOptions == before; i++) {
					waitFrames(1);
				}
				// A PRESS THAT RAN NOTHING IS A FRAME THAT REBUILT THE MENU, and one more press at
				// the same pixel is the whole fix - it is the real left click that needs this, not
				// the useMenuOption path, which is overridden in this class and runs on the calling
				// thread. Taking this out cost every left click on every course an outer attempt,
				// and an outer attempt re-runs lookFor and can swing the camera round.
				if (menuOptions == before) {
					click(sxy[0], sxy[1], false);
					for (int i = 0; i < 12 && menuOptions == before; i++) {
						waitFrames(1);
					}
				}
			} else {
				call("useMenuOption", Integer.valueOf(row));
				waitFrames(4);
			}
			ran = menuOptions > before && lastOption.startsWith(o.verb);
			if (ran || tries == 3) {
				break;
			}
			int[] again = lookFor(o);
			if (again == null) {
				break;
			}
			sxy = again;
			hover(sxy[0], sxy[1]);
			waitFrames(3);
			row = rowOf(o.verb);
			if (row < 0) {
				break;
			}
		}
		check("clicking it ran '" + lastOption + "'" + (tries > 1 ? ", on attempt " + tries : ""), ran,
			ran ? "" : lastOption.isEmpty() ? "the click ran nothing at all, in " + tries + " attempt(s)"
				: tries + " attempt(s) and the last ran '" + lastOption + "'");

		// 4. WATCH IT PLAY OUT, frame by frame, in DRAWN position - which is where the player sees
		// himself, glide and all, rather than where the server has put him.
		List<String> path = new ArrayList<String>();
		// PER TILE, THE TWO THINGS THE PATH ALONE CANNOT SAY: whether the player reached it on
		// foot or mid-crossing, and whether anything at all is drawn under it.
		List<Boolean> onFoot = new ArrayList<Boolean>();
		List<Boolean> hasFloor = new ArrayList<Boolean>();
		List<Boolean> onGround = new ArrayList<Boolean>();
		int airborne = 0;
		// THE TILE THEY SET OUT FROM, seeded rather than sampled.
		//
		// This loop records the player's DRAWN position frame by frame, and a telejump can land
		// before its first sample: Ardougne's first gap reported "the crossing is 0 tiles - the
		// player never moved" with the path holding nothing but the landing, on an obstacle whose
		// landing check had just passed. The loop will not add a tile equal to the last one, so
		// seeding costs nothing when the sampling does catch the start.
		path.add(preClick[0] + "," + preClick[1] + "L" + preClick[2]);
		onFoot.add(Boolean.TRUE);
		hasFloor.add(Boolean.valueOf(floorStorey(preClick[0], preClick[1], preClick[2]) >= 0));
		onGround.add(Boolean.valueOf(terrainAt(preClick[0], preClick[1], preClick[2])));
		if (!terrainAt(preClick[0], preClick[1], preClick[2])) {
			airborne++;
		}
		double[] last = drawnAt();
		int still = 0;
		boolean shotMid = false;
		// AND HOW MUCH OF THE CROSSING WAS ANIMATED.
		//
		// Counting starts at the first frame a seq is on the player, because the frames before that
		// are the walk ONTO the obstacle - driven by the bas, with no seq, which is correct and not
		// what is being measured. From that frame on, crossFrames is how long the player kept
		// being drawn somewhere new and animFrames is how much of it had an animation.
		int crossFrames = 0;
		int animFrames = 0;
		int seqSeen = -1;
		boolean crossing = false;
		int basBefore = basNow();
		int basSeen = -1;
		int basFrames = 0;
		int walkFrames = 0;
		// A TELEJUMP ARRIVES A TICK AFTER THE ANIMATION. The climb up a rough wall plays where the
		// player stands, waits a tick and only then moves them, so "still for a while" is not the
		// same as finished: judging it after 25 still frames reported the wall as landing the
		// player on the wall. A tick is about thirty frames here, so two ticks of stillness it is.
		//
		// AND THE CAP HAS TO COVER THE LONGEST CROSSING, not the usual one. It was three hundred
		// frames, which is ten tiles of walking, and Canifis' gap 5 is thirteen: the loop ran out
		// with the player halfway across and the four failures that followed were all consequences
		// of reading a tile the obstacle had not finished with. Nine hundred costs nothing on a
		// short obstacle, because stillness still ends the loop and a dead one still gives up
		// around frame ninety.
		boolean settled = false;
		for (int f = 0; f < 900; f++) {
			waitFrames(1);
			double[] d = drawnAt();
			int[] t = tileAt();
			// READ THE ANIMATION FIRST. The label below asks whether a crossing was under way when
			// the player reached this tile, and reading the seq after recording it gets the very
			// frame a crossing starts wrong - which is one false "walked tile with nothing drawn"
			// per run, on whichever obstacle happens to land on that frame.
			int seqNow = animNow();
			boolean carried = crossing || seqNow >= 0;
			String tile = t[0] + "," + t[1] + "L" + t[2];
			if (path.isEmpty() || !path.get(path.size() - 1).equals(tile)) {
				path.add(tile);
				// a tile added while no seq has been seen was reached by walking - but see the
				// leap guard below, which has the final say.
				onFoot.add(Boolean.valueOf(!carried));
				// floorStorey, not terrainAt: a walked tile may be the top of a market stall or a
				// basket, which is a loc with no ground of its own and is still something to
				// stand on. It already looks a storey down for the deck-over-roof case.
				hasFloor.add(Boolean.valueOf(floorStorey(t[0], t[1], t[2]) >= 0));
				// AND THE TERRAIN ANSWER SEPARATELY, because the vault-versus-slide test below is
				// a question about open air and a loc is not air. Keeping one list and using it
				// for both is how the pole-vault got failed.
				onGround.add(Boolean.valueOf(terrainAt(t[0], t[1], t[2])));
				// terrainAt, not floorStorey: this counts OPEN AIR, and a loc is not air. It
				// already looks a storey down, because on a rooftop the ground is routinely drawn
				// on the storey below and asking only about the player's own plane calls solid roof
				// air.
				if (!terrainAt(t[0], t[1], t[2])) {
					airborne++;
				}
			}
			if (seqNow >= 0 && seqSeen < 0) {
				seqSeen = seqNow;
				crossing = true;
			}
			int basNowV = basNow();
			boolean moved = Math.abs(d[0] - last[0]) > 0.001 || Math.abs(d[1] - last[1]) > 0.001;
			// A CHANGED BAS IS A CROSSING TOO. ~agility_force_move swaps the walk bas and walks,
			// so the obstacle starting shows up here rather than in field1171.
			if (basNowV != basBefore && basNowV >= 0) {
				if (basSeen < 0) {
					basSeen = basNowV;
				}
				crossing = true;
			}
			if (crossing) {
				if (seqNow >= 0) {
					animFrames++;
				}
				if (basNowV != basBefore && basNowV >= 0) {
					basFrames++;
				}
				if (moved) {
					crossFrames++;
					if (seqNow < 0 && (basNowV == basBefore || basNowV < 0)) {
						walkFrames++;
					}
				}
			}
			if (!shotMid && (Math.abs(d[0] - last[0]) > 0.4 || Math.abs(d[1] - last[1]) > 0.4)) {
				shot(course + "_" + (index + 1) + "b_moving");
				shotMid = true;
			}
			if (Math.abs(d[0] - last[0]) < 0.01 && Math.abs(d[1] - last[1]) < 0.01) {
				// A CLIMB THAT PLAYS WHERE IT STANDS IS NOT A CROSSING THAT HAS FINISHED. Al
				// Kharid's tropical tree runs seq 751 for sixty frames and only then moves the
				// player, and counting those frames as stillness ended the watch mid-animation:
				// the landing read 3318,3165 L1 against a constant of 3317,3174 L2, and the next
				// obstacle's path then starts on the constant, so the player did arrive. A seq on
				// the player means the obstacle is still happening, and the cap is what stops a
				// looping one hanging the run.
				if (seqNow >= 0) {
					still = 0;
				} else {
					still++;
				}
				if (still > 65 && f > 90) {
					settled = true;
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
		// AND IF THE LOOP ENDED ON ITS CAP THE PLAYER WAS STILL GOING. Every check below reads the
		// tile they are on, so without this line a crossing cut off halfway reads exactly like a
		// landing constant that is wrong - which is how one thirteen-tile gap produced four
		// failures across two obstacles.
		if (!settled) {
			say("      THE OBSERVATION RAN OUT while the player was still being drawn somewhere new,"
				+ " after 900 frames and " + path.size() + " tiles - everything below reads the tile"
				+ " they had got to, not the one they stop on");
		}
		say("      crossing: " + crossFrames + " frames moving"
			+ (seqSeen >= 0 ? ", seq " + seqSeen + " for " + animFrames : ", no one-shot seq")
			+ (basSeen >= 0 ? ", walk bas " + basSeen + " for " + basFrames : "")
			+ (walkFrames > 0 ? ", " + walkFrames + " frames with neither" : ""));
		// HALF IS THE BAR, not all of it: a jump may finish just before the landing, and the cam
		// samples render frames while the engine counts client cycles, so the two never line up
		// exactly. What this catches is the player still being carried across long after the
		// animation has run out - sliding in their idle pose, which is the reported complaint.
		// WAS THE PLAYER EVER CARRIED WITH NOTHING ON THEM? That is the fault worth catching: not
		// which animation was picked, but the player being moved in their idle pose. A one-shot
		// seq that ends early and a bas that covers the rest are both fine; frames with neither
		// are the slide. A third of the crossing is the bar, to leave room for the frame-versus-
		// cycle slop and for the last frame or two of a landing.
		// A TELEJUMP IS NOT A SLIDE. An obstacle that animates where it stands and then jumps the
		// player moves them in a single frame, so "one of one moving frames had nothing on it"
		// fails a ratio that is meant to catch being carried. Draynor's rough wall is the case:
		// seq 737 plays for its whole length and the move is one frame at the end of it.
		// AN OBSERVATION, NOT AN ASSERTION - and it was an assertion until Canifis showed why it
		// cannot be. This samples render frames, with a screenshot taken in the middle of the
		// crossing, so a glide that is over in a second is caught in a handful of samples and the
		// ratio between them means very little: it called four Canifis gaps unanimated when
		// Engine-TS/tools/sim/agilityglide.ts - which reads SeqType.duration against the window
		// p_exactmove was actually given - says human_spot_jump covers 38 cycles of its 50 and is
		// fine. That tool is the authority on whether an animation covers a crossing. What this
		// line is still worth keeping for is saying WHICH sequence the client put on the player,
		// which no engine-side tool can answer.
		if (crossFrames > 2 && walkFrames * 3 > crossFrames) {
			say("      (" + walkFrames + " of " + crossFrames + " sampled moving frames had neither a"
				+ " seq nor a changed walk bas"
				+ (seqSeen >= 0 ? ", seq " + seqSeen + " covered " + animFrames : "")
				+ " - sampling is coarse here, agilityglide.ts is the authority on timing)");
		}

		// 5. WHERE IT PUT US, against the course's own constant.
		int[] end = tileAt();
		boolean landed = end[0] == o.landX && end[1] == o.landZ && end[2] == o.landLevel;
		check("it landed on " + end[0] + "," + end[1] + " L" + end[2], landed,
			landed ? "" : "the constant says " + o.landX + "," + o.landZ + " L" + o.landLevel
				+ (settled ? "" : ", AND THE OBSERVATION RAN OUT before they stopped, so this is"
					+ " where they had got to rather than a landing"));

		// AND THE TILE IT LEFT US ON HAS SOMETHING UNDER IT.
		//
		// The tiles CROSSED are a different matter and must not be asserted: a tightrope, a clothes
		// line or a plank spans open air by definition, and at the storey the player is drawn on
		// there is nothing beneath them for the length of it. Varrock's clothes line crosses eight
		// tiles of which two are sky, and that is the obstacle working. The count is printed
		// because a run of them in the wrong place is still worth seeing.
		int endFloor = floorStorey(end[0], end[1], end[2]);
		check("and the tile it left the player on has floor under it", endFloor >= 0,
			endFloor >= 0 ? "" : "nothing is drawn on this storey or the one below");
		if (endFloor >= 0 && endFloor != end[2]) {
			say("      (the floor under it is the roof drawn on L" + endFloor + " - deck over roof)");
		}
		// WALKED OVER A HOLE IS A FAULT; GLIDED OVER ONE IS THE OBSTACLE WORKING.
		//
		// These used to be one printed number over the whole path. A player who is being carried
		// by p_exactmove across a rope, a washing line or a street SHOULD have nothing under them
		// for the length of it - Varrock's clothes line crosses eight tiles of which two are sky.
		// A player putting one foot in front of the other should not.
		//
		// The split is by animation, not geometry: an exactmove glide draws the player on every
		// tile in between, so Draynor's sixth obstacle is nine CONTIGUOUS tiles of which four are
		// over the gap, and looking for a jump in the coordinates finds nothing to split on.
		// AND NOTHING AFTER A LEAP IS WALKED, whatever the animation was doing. A step of more
		// than one tile is a telejump or an exactmove arriving, and everything from there on is
		// the crossing - Al Kharid's cable throws the player eight tiles and the first tile of
		// the throw was being held to the standard of a footstep.
		int firstLeap = path.size();
		for (int i = 1; i < path.size(); i++) {
			int[] a = tileOf(path.get(i - 1));
			int[] b = tileOf(path.get(i));
			if (Math.max(Math.abs(a[0] - b[0]), Math.abs(a[1] - b[1])) > 1) {
				firstLeap = i;
				break;
			}
		}
		List<String> walkedHoles = new ArrayList<String>();
		int airborneCross = 0;
		for (int i = 0; i < path.size(); i++) {
			boolean foot = i < firstLeap && i < onFoot.size() && onFoot.get(i).booleanValue();
			boolean floor = i < hasFloor.size() && hasFloor.get(i).booleanValue();
			if (foot) {
				if (!floor) {
					walkedHoles.add(path.get(i));
				}
			} else if (i < onGround.size() && !onGround.get(i).booleanValue()) {
				airborneCross++;
			}
		}
		check("every tile the player walked to has floor under it", walkedHoles.isEmpty(),
			walkedHoles.isEmpty() ? "" : walkedHoles.size() + " walked tile(s) with nothing drawn: "
				+ String.join(", ", walkedHoles));
		if (airborneCross > 0) {
			say("      " + airborneCross + " of the " + path.size() + " tiles were crossed with nothing"
				+ " drawn under them - which is what a rope, a line or a gap is");
		}
		if (airborne > 0) {
			say("      (" + airborne + " of the " + path.size() + " are open air by TERRAIN alone, locs"
				+ " not counted - that is the vault-versus-slide measure below, not a fault)");
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
		// AND ONLY FOR A GLIDE THAT GOES SOMEWHERE. A one-by-two hop onto a market stall or a
		// steep roof is where the thing is, not a slide across anything, and it has no straight
		// line to be out of.
		int glideX = 0;
		int glideZ = 0;
		if (jump > 0) {
			int[] a = tileOf(path.get(jump - 1));
			int[] b = tileOf(path.get(jump));
			glideX = Math.abs(b[0] - a[0]);
			glideZ = Math.abs(b[1] - a[1]);
		}
		if (jump > 0 && path.size() - jump >= 3 && Math.max(glideX, glideZ) >= 3) {
			int[] start = tileOf(path.get(jump - 1));
			boolean straight = true;
			for (int i = jump; i < path.size(); i++) {
				int[] t = tileOf(path.get(i));
				if (t[0] != start[0] && t[1] != start[1]) {
					straight = false;
					break;
				}
			}
			// A DIAGONAL OVER OPEN AIR IS A VAULT, NOT THE FAULT. The fault this was written for
			// is a landing out of line with its obstacle, which drags the player diagonally
			// ACROSS WHATEVER IS IN BETWEEN - Falador's first tightrope slid ten tiles east and
			// seven north over solid roof. Canifis' pole-vault also goes diagonally, ten east and
			// seven south, and is correct: the probe says there is nothing walkable due east of it
			// at all, the two roofs sit corner to corner, and the photograph shows the player in
			// the air over the street between them. So what separates the two is what is
			// underneath - roof all the way is the fault, open air is the obstacle working.
			//
			// I TRIED TO REPLACE THIS WITH THE SHAPE OF THE GLIDE and it cannot be done: the
			// tightrope's slide is ten by seven and the pole-vault is ten by seven. Thirteen
			// obstacles failed a rule built on that, the vault among them. What the tiles are
			// standing on is the only thing that tells them apart, which is why airborne is counted
			// off terrainAt rather than floorAt.
			// THE WHOLE PATH'S TERRAIN COUNT, and it has to stay that way.
			//
			// I narrowed this to the crossing's own tiles, on the reasoning that a hole on the
			// approach should not excuse a slide. It failed the Canifis pole-vault immediately.
			// Whether an air tile is recorded at all depends on the client happening to sample the
			// player over the street mid-glide - the vault's path went 3480,3484 straight to
			// 3490,3477 with nothing in between - so narrowing the window just loses the evidence.
			// The case it was meant to catch has never once been seen.
			boolean overAir = airborne > 0;
			check("and the crossing itself went in a straight line", straight || overAir,
				straight || overAir ? "" : "the glide from " + start[0] + "," + start[1]
					+ " moved on both axes with solid ground the whole way, so it slid across the"
					+ " roofs between rather than crossing a gap");
			if (!straight && overAir) {
				say("      (diagonal, but " + airborne + " of its tiles are open air - a corner gap,"
					+ " not a slide across the roofs)");
			}
		}
		return true;
	}

	static void logIn(int w, int h) throws Exception {
		// THE TITLE SCREEN WAS REPLACED UNDER THIS AND THE CAM WENT ON CLICKING THE OLD ONE.
		// It used to be two pages: click "Existing User", wait for titleScreenState to reach 2, then
		// click Login. The client now has one page - TitleScreen draws the saved accounts, the two
		// fields and an ENTER button, and Client.updateTitle only ever sets titleScreenState to 0 or
		// 3. There is no state 2 left to wait for, so every run died in logIn with "timed out waiting
		// for the login box (login screen says:  / )". The message is empty because there IS no login
		// box, and the natural reading of it - that the engine is not serving the client its cache -
		// is wrong and costs an hour. The jar on disk was older than the source, which is why this
		// only appeared on the first run after rebuilding it.
		//
		// So: fill the fields and press ENTER. TitleScreen puts that button at FORM_X..FORM_X+FORM_W
		// by BUTTON_Y..BUTTON_Y+BUTTON_H in its own 765x503 coordinates, and whether the hit test
		// sees those or a centred canvas depends on the display mode, so both are tried. Clicking is
		// kept rather than calling Client.login by reflection because the click goes through the
		// client's own input handling on the client's own thread, which is the whole point of
		// driving the real thing.
		int lx = (Math.max(w, 765) - 765) / 2;
		int ly = (Math.max(h, 503) - 503) / 2;
		final int ex = 765 / 2;                 // TitleScreen.CX
		final int ey = 325 + 44 / 2;            // TitleScreen.BUTTON_Y + BUTTON_H / 2
		int[][] enter = { { ex, ey }, { ex + lx, ey + ly } };

		say("logging in as '" + user + "'");
		long giveUp = System.currentTimeMillis() + 180000L;
		while (!(app.ingame && geti("sceneState") == 2)) {
			if (System.currentTimeMillis() > giveUp) {
				// one more go, and let waitFor write the real message if it is still not in
				waitFor("the game", 1, () -> app.ingame && geti("sceneState") == 2);
			}
			// Set them every pass: a failed login leaves the fields where the client left them, and
			// the title screen's own key handling can edit them between attempts.
			set("username", user);
			set("password", "cam12345");
			for (int[] p : enter) {
				click(p[0], p[1], false);
				waitFrames(10);
				if (app.ingame) {
					break;
				}
			}
			if (!app.ingame) {
				Thread.sleep(1000);
			}
		}
		say("logged in");
		waitFrames(50);
	}
}
