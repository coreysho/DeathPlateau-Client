// Drives the REAL client, logged in to a real (local) server, with no window: the frame is drawn onto
// a BufferedImage and the mouse and keys are handed to the client's own listeners, so everything from
// the AWT event to the pixels on screen is the client's code. See run_resizableshots.py.
//
// The same class runs against an older client jar too (everything version-specific is reflection),
// which is how the fixed screen is compared with the client from before resizable mode existed.
//
//   java -Dlostcity.host=127.0.0.1 -Dlostcity.port=43694 -Dlostcity.webport=8694 -Duser.home=<tmp>
//        -cp <client jar><sep><dir of this class> ResizableShots <out dir> <fixed|resizable> <w> <h> <user>
import jagex2.client.Client;
import sign.signlink;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
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

public class ResizableShots extends Client {

	static final Object LOCK = new Object();
	static volatile BufferedImage canvas;
	static volatile int frames;
	static volatile long drawNanos;
	static volatile int drawCount;
	/** Pinned camera, applied under the lock before every frame so two runs frame the same view. */
	static volatile boolean pinCamera;
	/**
	 * Compare mode: every frame is drawn without the things that move on their own - npcs, other
	 * players, a flashing tab, a hint arrow - so a frame depends only on the code that drew it and
	 * two runs (two client builds) can be compared pixel for pixel.
	 */
	static volatile boolean still;
	static int pinYaw = 0;
	static int pinPitch = 300;

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

	/** Compare mode: no title-screen flames, whose thread would draw random numbers at its own pace. */
	public void startThread(Runnable thread, int priority) {
		if (thread == this && priority == 2 && Boolean.getBoolean("shots.noflames")) {
			return;
		}
		super.startThread(thread, priority);
	}

	public String getHost(int arg0) {
		return "runescape.com";
	}

	public void draw() {
		synchronized (LOCK) {
			if (pinCamera) {
				set("orbitCameraYaw", pinYaw);
				set("orbitCameraPitch", pinPitch);
				set("macroCameraAngle", 0);
				set("macroCameraX", 0);
				set("macroCameraZ", 0);
				set("macroMinimapAngle", 0);
				set("macroMinimapZoom", 0);
				set("cameraZoomOffset", 0);
			}
			int npcs = 0;
			int players = 0;
			if (still) {
				npcs = geti("npcCount");
				players = geti("playerCount");
				set("npcCount", 0);
				set("playerCount", 0);
				set("flashingTab", -1);
				set("hintType", 0);
				showSelf(false);   // the local player's idle animation runs on the clock
			}
			long t = System.nanoTime();
			try {
				super.draw();
			} finally {
				if (still) {
					set("npcCount", npcs);
					set("playerCount", players);
					showSelf(true);
				}
			}
			if (this.ingame) {
				drawNanos += System.nanoTime() - t;
				drawCount++;
			}
			frames++;
		}
	}

	// ------------------------------------------------------------------------------ reflection
	static ResizableShots app;
	static int bankId = -1;

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

	static Object staticField(String name) {
		return get(name);
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

	static void key(int code, char ch) {
		app.keyPressed(new KeyEvent(app, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, code, ch));
		app.keyReleased(new KeyEvent(app, KeyEvent.KEY_RELEASED, System.currentTimeMillis(), 0, code, ch));
	}

	static void type(String s) throws Exception {
		for (char c : s.toCharArray()) {
			key(KeyEvent.getExtendedKeyCodeForChar(c), c);
			waitFrames(1);
		}
	}

	static void command(String cmd) throws Exception {
		type(cmd);
		waitFrames(2);
		say("typed '" + get("chatTyped") + "'");
		key(KeyEvent.VK_ENTER, '\n');
		waitFrames(5);
	}

	// ------------------------------------------------------------------------------ waiting
	static void waitFrames(int n) throws InterruptedException {
		int until = frames + n;
		long give = System.currentTimeMillis() + 20000L;
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

	static void say(String s) {
		System.out.println(s);
		log.println(s);
		log.flush();
	}

	static void shot(String name) throws Exception {
		waitFrames(3);
		BufferedImage copy;
		synchronized (LOCK) {
			BufferedImage c = canvas;
			copy = new BufferedImage(c.getWidth(), c.getHeight(), BufferedImage.TYPE_INT_RGB);
			Graphics2D g = copy.createGraphics();
			g.drawImage(c, 0, 0, null);
			g.dispose();
		}
		ImageIO.write(copy, "png", new File(out, name + ".png"));
		say("shot " + name + ".png " + copy.getWidth() + "x" + copy.getHeight());
	}

	static List<String> menu() {
		List<String> rows = new ArrayList<String>();
		synchronized (LOCK) {
			String[] opts = (String[]) get("menuOption");
			int size = geti("menuSize");
			for (int i = size - 1; i >= 0; i--) {
				rows.add(opts[i].replaceAll("@...@", ""));
			}
		}
		return rows;
	}

	static boolean hasMethod(String name) {
		for (Class<?> c = app.getClass(); c != null; c = c.getSuperclass()) {
			for (Method m : c.getDeclaredMethods()) {
				if (m.getName().equals(name)) {
					return true;
				}
			}
		}
		return false;
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

	static void run(String[] args) throws Exception {
		out = new File(args[0]);
		out.mkdirs();
		String mode = args[1];
		int w = Integer.parseInt(args[2]);
		int h = Integer.parseInt(args[3]);
		String user = args[4];
		String prefix = args.length > 5 ? args[5] : mode + "_" + w + "x" + h;
		log = new PrintWriter(new FileWriter(new File(out, prefix + ".log")));
		boolean resizable = mode.equals("resizable") || mode.equals("drawdist") || mode.equals("title");
		pinPitch = Integer.getInteger("shots.pitch", mode.equals("compare") ? 383 : 300);

		seedRandom(377);
		// The local server's own (throwaway) login key, not the live world's.
		String rsan = System.getProperty("shots.rsan");
		if (rsan != null) {
			Client.LOGIN_RSAN = new java.math.BigInteger(rsan.trim());
		}
		Client.nodeId = 10;
		Client.portOffset = 0;
		Client.setHighMem();
		Client.membersWorld = true;
		signlink.storeid = 33;              // its own cache folder, never the player's
		signlink.startpriv(InetAddress.getLocalHost());

		app = new ResizableShots();
		app.setSize(w, h);
		app.initApplet(765, 503);
		if (app.graphics == null) {
			synchronized (LOCK) {
				app.graphics = app.acquireGraphics();
			}
		}
		if (resizable) {
			call("setResizable", true);
		}
		waitFor("the title screen", 120000, () -> geti("titleScreenState") == 0 && frames > 20 && get("imageTitle4") != null);
		waitFrames(10);
		shot(prefix + "_title");
		if (mode.equals("title")) {
			// the login box too, so the fill is judged with what sits on top of it
			int tx = resizable ? (Math.max(w, 765) - 765) / 2 : 0;
			int ty = resizable ? (Math.max(h, 503) - 503) / 2 : 0;
			click(tx + 765 / 2 + 80, ty + 503 / 2 + 40, false);
			waitFor("the login box", 5000, () -> geti("titleScreenState") == 2);
			waitFrames(10);
			shot(prefix + "_loginbox");
			say("done");
			log.close();
			System.exit(0);
		}

		// Existing user -> login box -> Login. Window coordinates: the title screen is 765x503, centred.
		int lx = resizable ? (Math.max(w, 765) - 765) / 2 : 0;
		int ly = resizable ? (Math.max(h, 503) - 503) / 2 : 0;
		click(lx + 765 / 2 + 80, ly + 503 / 2 + 40, false);
		waitFor("the login box", 5000, () -> geti("titleScreenState") == 2);
		set("username", user);
		set("password", "shots123");
		say("logging in as '" + get("username") + "'");
		// The same account twice in a row (two client builds compared) is still logged in for a
		// minute after the last run's socket closed: try again until the server lets it back in.
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

		// A quiet spot (nobody walks through it) so a frame can be compared with another run's.
		command("::tele " + System.getProperty("shots.tele", "0,50,50,22,22"));
		waitFrames(100);
		waitFor("the scene", 30000, () -> geti("sceneState") == 2);
		pinCamera = true;
		// The random bits of a session: the colour table's jitter and the minimap's wall colours.
		// Reseeded here, after the title screen's flame thread has stopped, and re-rolled in the same
		// order in every run, so the pixels depend on the code and not on the thread timing.
		synchronized (LOCK) {
			seedRandom(4242);
			jagex2.graphics.Pix3D.initColourTable(0.8D);
			call("createMinimap", geti("currentLevel"));
			set("redrawFrame", true);
		}
		hover(-1, -1);
		app.mouseExited(new MouseEvent(app, MouseEvent.MOUSE_EXITED, System.currentTimeMillis(), 0, -1, -1, 0, false));
		waitFrames(60);
		if (mode.equals("compare")) {
			compare(prefix);
			return;
		}
		if (mode.equals("toggle")) {
			toggle(prefix);
			return;
		}
		if (mode.equals("drawdist")) {
			drawDistances(prefix, w, h);
			return;
		}
		shot(prefix + "_game");
		waitFrames(25);
		shot(prefix + "_game2");

		// performance: average drawGame time over 200 frames
		drawNanos = 0;
		drawCount = 0;
		waitFrames(200);
		say(String.format("draw: %.2f ms/frame over %d frames at %dx%d (%s)", drawNanos / 1e6 / Math.max(1, drawCount), drawCount, w, h, mode));

		// a menu on the scene: right-click the middle of the viewport
		int vx = resizable ? w / 2 - 100 : 4 + 256;
		int vy = resizable ? (h - 165) / 2 : 4 + 167;
		click(vx, vy, true);
		say("menu open: " + get("menuVisible") + " area " + get("menuArea") + " rows " + menu());
		shot(prefix + "_menu");
		key(KeyEvent.VK_ESCAPE, (char) 27);
		click(vx + 400 > w ? 5 : vx + 400, 5, false);  // click away from the menu to close it
		set("menuVisible", false);
		waitFrames(5);

		// an interface: the bank
		openBank();
		bankId = geti("viewportInterfaceId");
		waitFrames(20);
		shot(prefix + "_bank");
		if (resizable) {
			// a right-click inside the bank's item grid, in window coordinates: the bank is centred in
			// the open area. Well inside the grid under either bank layout - the 8x5 one in a 488x305
			// window at (12,20), and the 12x6 one in a 506x328 window at (3,3) that replaced it.
			int mainX = ((Number) layoutField("mainX")).intValue();
			int mainY = ((Number) layoutField("mainY")).intValue();
			click(mainX + 120, mainY + 130, true);
			say("bank slot menu: area " + get("menuArea") + " " + menu());
			shot(prefix + "_bankmenu");
			set("menuVisible", false);
		}
		key(KeyEvent.VK_ESCAPE, (char) 27);
		waitFrames(10);
		if (geti("viewportInterfaceId") != -1) {
			call("closeInterfaces");
			waitFrames(10);
		}

		if (resizable) {
			resizableChecks(w, h);
		}
		say("done");
		log.close();
		System.exit(0);
	}

	/**
	 * Every draw distance the F9 row offers, in one session: a frame of each and what it costs. The
	 * scene is the only thing that changes, so the frame times compare directly.
	 */
	static void drawDistances(String prefix, int w, int h) throws Exception {
		// NOT still: hiding the npcs means zeroing npcCount, which the npc-info packet reads the
		// next tick ("Too many npcs", and the client throws itself off the server). The other modes
		// get away with it because they sit somewhere with no npcs in view; this one wants a place
		// with something to look at.
		int[] steps = (int[]) Class.forName("jagex2.client.DisplaySettings").getField("DRAW_DISTANCES").get(null);
		double[][] ms = new double[2][steps.length];
		for (int pass = 0; pass < 2; pass++) {
			// the second pass runs the steps backwards: a scene with anything living in it drifts, so
			// two passes in opposite orders show how much of the difference is the draw distance and
			// how much is the minute that went by
			for (int j = 0; j < steps.length; j++) {
				int i = pass == 0 ? j : steps.length - 1 - j;
				long built = System.currentTimeMillis();
				// under the draw lock: the client only ever changes this on its own game thread, and
				// rebuilding the tables under a frame half way through reading them would not end well
				synchronized (LOCK) {
					call("setDrawDistance", Integer.valueOf(steps[i]));
				}
				built = System.currentTimeMillis() - built;
				waitFrames(30);
				if (pass == 0) {
					shot(prefix + "_" + steps[i]);
				}
				drawNanos = 0;
				drawCount = 0;
				waitFrames(200);
				ms[pass][i] = drawNanos / 1e6 / Math.max(1, drawCount);
				say(String.format("draw: %.2f ms/frame over %d frames at %dx%d (%d tiles, pass %d, tables rebuilt in %d ms)",
					ms[pass][i], drawCount, w, h, steps[i], pass + 1, built));
			}
		}
		for (int i = 0; i < steps.length; i++) {
			say(String.format("draw: %d tiles -> %.1f ms/frame (%.2f up, %.2f down)", steps[i],
				(ms[0][i] + ms[1][i]) / 2, ms[0][i], ms[1][i]));
		}
		synchronized (LOCK) {
			call("setDrawDistance", Integer.valueOf(steps[0]));
		}
		waitFrames(10);
		say("done");
		log.close();
		System.exit(0);
	}

	/**
	 * Switching at runtime, in one session: fixed, then resizable at 1280x800, then fixed again. The
	 * fixed frames before and after must be the same pixels - switching back leaves nothing behind.
	 */
	static void toggle(String prefix) throws Exception {
		still = true;
		waitFrames(40);
		shot(prefix + "_1fixed");
		synchronized (LOCK) {
			app.setSize(1280, 800);
		}
		call("setResizable", true);
		waitFrames(40);
		say("resizable now: " + call("isResizable") + ", canvas " + canvas.getWidth() + "x" + canvas.getHeight());
		shot(prefix + "_2resizable");
		synchronized (LOCK) {
			app.setSize(1600, 900);
		}
		waitFrames(40);
		shot(prefix + "_3resized");
		call("setResizable", false);
		synchronized (LOCK) {
			app.setSize(765, 503);
		}
		waitFrames(60);
		say("resizable now: " + call("isResizable"));
		shot(prefix + "_4fixed");
		say("done");
		log.close();
		System.exit(0);
	}

	/** Fixed-screen frames for comparing two client builds: the scene, the bank, a menu. */
	static void compare(String prefix) throws Exception {
		still = true;
		waitFrames(40);
		shot(prefix + "_scene");
		waitFrames(100);
		shot(prefix + "_scene_again");
		click(4 + 300, 4 + 200, true);
		say("menu: " + menu());
		hover(4 + 300 + 5, 4 + 200 + 34);    // over the second row
		waitFrames(5);
		shot(prefix + "_menu");
		set("menuVisible", false);
		hover(-1, -1);
		app.mouseExited(new MouseEvent(app, MouseEvent.MOUSE_EXITED, System.currentTimeMillis(), 0, -1, -1, 0, false));
		openBank();
		waitFrames(30);
		shot(prefix + "_bank");
		say("done");
		log.close();
		System.exit(0);
	}

	static void showSelf(boolean on) {
		try {
			Object self = Client.class.getField("localPlayer").get(null);
			if (self != null) {
				Field f = self.getClass().getField("field1680");
				f.setBoolean(self, on);
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	static void openBank() throws Exception {
		for (int attempt = 0; ; attempt++) {
			command("::bank");
			try {
				waitFor("the bank", 6000, () -> geti("viewportInterfaceId") != -1);
				return;
			} catch (RuntimeException e) {
				if (attempt == 2) {
					throw e;
				}
			}
		}
	}

	static Object layoutField(String name) throws Exception {
		Object layout = get("layout");
		Field f = layout.getClass().getField(name);
		return f.get(layout);
	}

	// The panels' geometry, asked of Layout itself rather than written down again here, so the
	// harness follows the client when a panel's rectangle changes. CHAT 0, MINIMAP 1, SIDEBAR 2.
	static final int CHAT = 0;
	static final int MINIMAP = 1;
	static final int SIDEBAR = 2;

	static int layoutStatic(String method, int panel) throws Exception {
		Class<?> c = get("layout").getClass();
		return ((Number) c.getMethod(method, int.class).invoke(null, Integer.valueOf(panel))).intValue();
	}

	static int layoutOn(String method, int panel) throws Exception {
		Object layout = get("layout");
		return ((Number) layout.getClass().getMethod(method, int.class).invoke(layout, Integer.valueOf(panel))).intValue();
	}

	/** A point of the fixed frame, in window coordinates: where its panel put it. */
	static int panelWinX(int panel, int fixedX) throws Exception {
		return layoutOn("panelScreenX", panel) + fixedX - layoutStatic("panelFixedX", panel);
	}

	static int panelWinY(int panel, int fixedY) throws Exception {
		return layoutOn("panelScreenY", panel) + fixedY - layoutStatic("panelFixedY", panel);
	}

	/** The panels take the clicks meant for them, and the scene the rest, however big the window. */
	static void resizableChecks(int w, int h) throws Exception {
		int ok = 0;
		int bad = 0;
		// 1. a tab button in the bottom-right panel: the Stats tab (second in the top row, fixed x 560..593, y 168..204)
		int tabBefore = geti("selectedTab");
		int[] tabs = (int[]) get("tabInterfaceId");
		int want = -1;
		for (int i = 0; i < 14 && want == -1; i++) {
			if (tabs[i] != -1 && i != tabBefore) {
				want = i;
			}
		}
		// the tab's button in the fixed frame (interface 548's edges), then where the panel put it
		int col = want % 7;
		int fx0 = col == 0 ? 522 : 560 + (col - 1) * 33;
		int fy0 = want < 7 ? 168 : 466;
		click(panelWinX(SIDEBAR, fx0 + 10), panelWinY(SIDEBAR, fy0 + 15), false);
		int tabAfter = geti("selectedTab");
		boolean t = tabAfter == want;
		say((t ? "ok   " : "FAIL ") + "a click on tab " + want + "'s button in the bottom-right panel selects it (" + tabBefore + " -> " + tabAfter + ")");
		if (t) ok++; else bad++;
		shot("resizable_" + w + "x" + h + "_tab");
		// 2. hover the inventory: the menu is the sidebar's
		hover(panelWinX(SIDEBAR, 560), panelWinY(SIDEBAR, 230));
		waitFrames(5);
		say("hover over the inventory's first slot: " + menu());
		// 3. the minimap: a click walks, setting the flag
		set("flagSceneTileX", 0);
		click(panelWinX(MINIMAP, 545 + 25 + 73 + 30), panelWinY(MINIMAP, 4 + 5 + 75 + 10), false);
		waitFrames(3);
		boolean m = geti("flagSceneTileX") != 0;
		say((m ? "ok   " : "FAIL ") + "a click on the minimap in the top-right walks there (flag at " + geti("flagSceneTileX") + ")");
		if (m) ok++; else bad++;
		waitFrames(150);
		// 4. the scene far outside the old 512x334: a left click walks, and the cross lands under the mouse
		int fx = Math.min(w - 300, 900);
		int fy = Math.min(h - 200, 560);
		set("crossMode", 0);
		click(fx, fy, false);
		int crossMode = geti("crossMode");
		boolean c = crossMode == 1 || crossMode == 2;   // yellow: walked there; red: used what was there
		int cx = geti("crossX");
		int cy = geti("crossY");
		Object layout = get("layout");
		int vpX = ((Number) layoutField("vpX")).intValue();
		int vpY = ((Number) layoutField("vpY")).intValue();
		boolean where = cx - vpX == fx && cy - vpY == fy;
		say((c && where ? "ok   " : "FAIL ") + "a left click on the scene at " + fx + "," + fy + " (outside the fixed 512x334) is taken by the scene there (cross " + crossMode + " at viewport " + (cx - vpX) + "," + (cy - vpY) + ")");
		if (c && where) ok++; else bad++;
		shot("resizable_" + w + "x" + h + "_walk");
		waitFrames(100);
		// 5. mouse picking across the whole window: sweep a grid, count points where the scene
		//    under the mouse offered something other than Walk here
		int found = 0;
		int far = 0;
		String sample = null;
		int sideW = layoutStatic("panelWidth", SIDEBAR) + 11;
		for (int y = 20; y < h - 180; y += 40) {
			for (int x = 20; x < w - sideW; x += 40) {
				hover(x, y);
				waitFrames(2);
				List<String> rows = menu();
				if (rows.size() > 2 || rows.size() == 2 && !rows.get(0).startsWith("Walk here")) {
					found++;
					if (x > 512 || y > 334) {
						far++;
						if (sample == null) {
							sample = x + "," + y + " " + rows;
						}
					}
				}
			}
		}
		boolean p = far > 0;
		say((p ? "ok   " : "FAIL ") + "mouse picking over the whole window: " + found + " grid points found something to click, " + far + " of them outside the fixed viewport, e.g. " + sample);
		if (p) ok++; else bad++;
		if (sample != null) {
			String[] xy = sample.substring(0, sample.indexOf(' ')).split(",");
			click(Integer.parseInt(xy[0]), Integer.parseInt(xy[1]), true);
			say("menu on it: area " + get("menuArea") + " " + menu());
			shot("resizable_" + w + "x" + h + "_pickmenu");
			set("menuVisible", false);
		}
		// 5b. walkable overlays: the wilderness level keeps to the bottom-right corner of the open
		//     area, a cave's darkness covers the whole window (ids are content's interface.pack)
		int[] overlays = { Integer.getInteger("shots.wildoverlay", 193), Integer.getInteger("shots.darkoverlay", 12418) };
		String[] names = { "wildoverlay", "darkoverlay" };
		for (int i = 0; i < 2; i++) {
			synchronized (LOCK) {
				set("viewportOverlayInterfaceId", overlays[i]);
			}
			waitFrames(10);
			shot("resizable_" + w + "x" + h + "_" + names[i]);
		}
		synchronized (LOCK) {
			set("viewportOverlayInterfaceId", -1);
		}
		// 6. a fullscreen interface is 765x503 and is drawn in the middle of the window; closing it
		//    brings the window-sized viewport back
		synchronized (LOCK) {
			set("fullscreenInterfaceId0", bankId);
		}
		waitFrames(20);
		shot("resizable_" + w + "x" + h + "_fullscreen");
		synchronized (LOCK) {
			set("fullscreenInterfaceId0", -1);
			set("redrawFrame", true);
		}
		waitFrames(20);
		Object vp = get("areaViewport");
		int vpw = vp == null ? -1 : vp.getClass().getField("width").getInt(vp);
		boolean fs = vpw == w;
		say((fs ? "ok   " : "FAIL ") + "after a fullscreen interface the viewport is the window's width again (" + vpw + ")");
		if (fs) ok++; else bad++;
		// 7. the F9 panel, drawn and clickable in the middle of the open area: its last row switches
		//    to the fixed screen, and clicking it again (where it now is) switches back
		key(KeyEvent.VK_F9, (char) 0);
		waitFrames(5);
		shot("resizable_" + w + "x" + h + "_settings");
		int rows = ((Number) staticField("QOL_PANEL_ROWS")).intValue();
		int windowRow = ((Number) staticField("ROW_WINDOW")).intValue();
		int panelH = 24 + rows * 15 + 22;
		int px = ((Number) layoutField("mainX")).intValue() + (512 - 320) / 2 + 60;
		int py = ((Number) layoutField("mainY")).intValue() + (334 - panelH) / 2 + 24 + windowRow * 15 + 7;
		click(px, py, false);
		waitFrames(10);
		boolean off = !((Boolean) call("isResizable"));
		say((off ? "ok   " : "FAIL ") + "clicking 'Resizable window' in the F9 panel switches to the fixed screen");
		if (off) ok++; else bad++;
		shot("resizable_" + w + "x" + h + "_switchedfixed");
		px = (512 - 320) / 2 + 60 + 4;
		py = (334 - panelH) / 2 + 24 + windowRow * 15 + 7 + 4;
		click(px, py, false);
		waitFrames(10);
		boolean on = (Boolean) call("isResizable");
		say((on ? "ok   " : "FAIL ") + "...and clicking it again on the fixed screen switches back");
		if (on) ok++; else bad++;
		// 8. the draw distance row, under it: a click steps it on to the next value and saves it
		int[] steps = (int[]) Class.forName("jagex2.client.DisplaySettings").getField("DRAW_DISTANCES").get(null);
		int distRow = ((Number) staticField("ROW_DRAW_DISTANCE")).intValue();
		int before = ((Number) call("drawDistance")).intValue();
		click(((Number) layoutField("mainX")).intValue() + (512 - 320) / 2 + 60,
			((Number) layoutField("mainY")).intValue() + (334 - panelH) / 2 + 24 + distRow * 15 + 7, false);
		waitFrames(20);
		int after = ((Number) call("drawDistance")).intValue();
		boolean stepped = before == steps[0] && after == steps[1];
		say((stepped ? "ok   " : "FAIL ") + "clicking the draw distance row steps it on (" + before + " -> " + after + " tiles)");
		if (stepped) ok++; else bad++;
		shot("resizable_" + w + "x" + h + "_drawdistance");
		synchronized (LOCK) {
			call("setDrawDistance", Integer.valueOf(steps[0]));
		}
		waitFrames(10);
		key(KeyEvent.VK_F9, (char) 0);
		waitFrames(10);
		say("resizable checks: " + ok + " ok, " + bad + " failed");
	}
}
