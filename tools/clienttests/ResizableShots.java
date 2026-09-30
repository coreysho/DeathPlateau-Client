// Drives the REAL client, logged in to a real (local) server, with no window: the frame is drawn onto
// a BufferedImage and the mouse and keys are handed to the client's own listeners, so everything from
// the AWT event to the pixels on screen is the client's code. See run_resizableshots.py and, for the
// "doll" mode (474's Equipment Stats window, weapon by weapon), run_dollshots.py, and the
// "swaps" mode (the F10 left-click swaps panel with a list too long to fit), run_swapshots.py.
//
// The same class runs against an older client jar too (everything version-specific is reflection),
// which is how the fixed screen is compared with the client from before resizable mode existed.
//
//   java -Dlostcity.host=127.0.0.1 -Dlostcity.port=43694 -Dlostcity.webport=8694 -Duser.home=<tmp>
//        -cp <client jar><sep><dir of this class> ResizableShots <out dir> <fixed|resizable> <w> <h> <user>
import jagex2.client.Client;
import jagex2.client.MenuSwaps;
import sign.signlink;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
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
	/**
	 * Click-rate mode: milliseconds of padding added to every frame, to hold a chosen frame rate on
	 * any machine. A slow frame is what makes GameShell's catch-up loop run several update()s per
	 * frame, which is what a dropped click needs.
	 */
	static volatile int slowDrawMs;
	/** update() and useMenuOption() calls: how many updates a frame costs, and what a click did. */
	static volatile int updates;
	static volatile int menuOptions;

	public void update() {
		updates++;
		super.update();
	}

	public void useMenuOption(int option) {
		menuOptions++;
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
		// Outside the lock: the padding is meant to slow the game thread's frame, not to keep the
		// test thread out of the canvas for that long.
		int pad = slowDrawMs;
		if (pad > 0) {
			try {
				Thread.sleep(pad);
			} catch (InterruptedException ignored) {
			}
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

	/**
	 * A click on a row of the F9 panel. One click, once: the panel reads the mouse in the DRAW phase
	 * (handleInput is called from drawGame), and this used to need up to eight tries because
	 * GameShell's catch-up loop runs several update()s per frame at 1920x1080 and cleared the press
	 * before the frame that would have read it - see GameShell.heldClickButton and the clicks mode.
	 */
	static boolean clickSettingsRow(int row, String field) throws Exception {
		Object was = call(field);
		openSettings();
		int[] at = settingsRowAt(row);
		click(at[0], at[1], false);
		waitFrames(20);
		return !was.equals(call(field));
	}

	/** Where a row of the open F9 panel is, in window coordinates. */
	static int[] settingsRowAt(int row) throws Exception {
		int hdr = ((Number) staticField("QOL_PANEL_HEADER_H")).intValue();
		int rh = ((Number) staticField("QOL_PANEL_ROW_H")).intValue();
		return new int[] {
			((Number) call("qolPanelX")).intValue() + ((Number) layoutField("vpScreenX")).intValue() + 60,
			((Number) call("qolPanelY")).intValue() + ((Number) layoutField("vpScreenY")).intValue() + hdr + row * rh + rh / 2
		};
	}

	/**
	 * F9 until the settings panel is actually open. It closes itself whenever an interface it would
	 * be buried under appears, so a panel opened twenty frames ago may not be there any more.
	 */
	static void openSettings() throws Exception {
		for (int i = 0; i < 4; i++) {
			if (((Boolean) get("qolPanelOpen")).booleanValue()) {
				return;
			}
			key(KeyEvent.VK_F9, (char) 0);
			waitFrames(4);
		}
		say("FAIL the F9 settings panel would not stay open");
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
		// "modern" is the same run as "resizable", in Old School's modern layout instead of its classic one
		boolean modern = mode.equals("modern") || mode.equals("swapsmodern");
		// "swapsclassic" and "swapsmodern" are the swaps mode in the two resizable layouts, so the
		// F10 panel is seen in the fixed frame that cannot grow and in the windows that can.
		boolean resizable = modern || mode.equals("resizable") || mode.equals("drawdist") || mode.equals("title")
			|| mode.equals("swapsclassic");
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
			// Layout.CLASSIC is 1, Layout.MODERN 2
			call("setDisplayMode", Integer.valueOf(modern ? 2 : 1));
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

		dollUser = user;
		logIn(user, resizable, w, h);

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
		if (mode.equals("clicks")) {
			clickRates(prefix);
			return;
		}
		if (mode.equals("doll")) {
			doll(prefix);
			return;
		}
		if (mode.startsWith("swaps")) {
			swaps(prefix);
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
			resizableChecks(w, h, prefix);
		}
		say("done");
		log.close();
		System.exit(0);
	}

	/** Existing user -> login box -> Login. Window coordinates: the title screen is 765x503, centred. */
	static void logIn(String user, boolean resizable, int w, int h) throws Exception {
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
	}

	// ------------------------------------------------------------------------ the equipment doll
	/**
	 * 474's Equipment Stats window (equipment_stats.if) with one weapon in hand and then another.
	 * The figure in it is the component with client code 328, and its pose is meant to be the stand
	 * seq the server gave the player for whatever they hold - a staff carried like a staff, a whip
	 * like a whip. Every weapon named in -Dshots.weapons gets a frame of the window and a line of
	 * what the client actually has for the doll, so a pose that never changes can be told apart from
	 * one that changes to something that happens to look the same.
	 *
	 * Weapons are wielded from Equipment Stats' own side panel (equipment_side.if), which is where
	 * the bug was reported: that leaves the window open, so the doll is asked to change pose in place.
	 */
	static void doll(String prefix) throws Exception {
		String[] weapons = System.getProperty("shots.weapons", "staff_of_air,bronze_sword").split(",");
		// A throwaway account's first login lands it on Tutorial Island, where login.rs2 jumps to
		// @start_tutorial and clears every tab - including Worn Equipment, which this window hangs
		// off. The ::tele in run() has already moved it away, so logging out and straight back in
		// takes the ordinary branch (~initalltabs) and the tabs are there.
		if (tabInterface(4) == -1) {
			say("no Worn Equipment tab (still the tutorial's): logging out and back in");
			call("logout");
			waitFor("the title screen", 60000, () -> !app.ingame && geti("titleScreenState") == 0);
			waitFrames(20);
			logIn(dollUser, false, 765, 503);
			waitFor("the scene", 60000, () -> geti("sceneState") == 2);
			waitFrames(60);
		}
		say("Worn Equipment tab is interface " + tabInterface(4));
		// a whip wants 70 Attack and a trident 75 Magic, and a throwaway account has neither
		String[] stats = { "attack", "strength", "defence", "ranged", "magic", "hitpoints" };
		for (int i = 0; i < stats.length; i++) {
			command("::setstat " + stats[i] + " 99");
		}
		waitFrames(10);
		// the Worn Equipment tab, then its "Show Equipment Stats" button (wornitems:stats_button is
		// x=45 y=210 34x34 inside a sidebar the fixed layout draws at SIDE_X,205)
		set("selectedTab", Integer.valueOf(4));
		set("redrawSidebar", Boolean.TRUE);
		set("redrawSideicons", Boolean.TRUE);
		waitFrames(10);
		int[] button = windowOf(((Number) staticField("SIDE_X")).intValue() + 45 + 17, 205 + 210 + 17);
		click(button[0], button[1], false);
		waitFor("the Equipment Stats window", 15000, () -> geti("viewportInterfaceId") != -1);
		waitFrames(40);
		dollSay("bare");
		dollShot(prefix + "_bare");
		for (int i = 0; i < weapons.length; i++) {
			String weapon = weapons[i].trim();
			// The slot ::give fills, found by watching the side panel change rather than by taking
			// whichever slot holds something: wielding puts the weapon that came off back into the
			// inventory, so from the second weapon on there is always something else in there.
			int[] was = sideSlots();
			int held = wornPart(RIGHT_HAND);
			command("::give " + weapon);
			int slot = filledSlot(was);
			if (slot < 0) {
				say("FAIL ::give " + weapon + " put nothing in the side panel");
				continue;
			}
			int[] at = invSlotAt(slot);
			click(at[0], at[1], false);
			// A wield is a round trip - the op goes to the server, ~update_all answers with a new
			// appearance - so the frame is taken once the hand has actually changed, not a fixed
			// number of frames later.
			for (int waited = 0; waited < 40 && wornPart(RIGHT_HAND) == held; waited++) {
				waitFrames(5);
			}
			if (wornPart(RIGHT_HAND) == held) {
				say("FAIL " + weapon + " would not go on from the side panel (slot " + slot + ")");
			}
			waitFrames(30);
			dollSay(weapon);
			dollShot(prefix + "_" + weapon);
		}
		say("done");
		log.close();
		System.exit(0);
	}

	// ------------------------------------------------------------------------------ swaps (F10)
	/**
	 * The left-click swaps panel with a list far longer than it can show - the thing that used to be
	 * impossible, because MenuSwaps.MAX was 16 and 16 was however many rows fitted the 334px
	 * viewport. Forty swaps are put in through MenuSwaps itself (its own throwaway cache dir: the
	 * harness sets signlink.storeid and user.home), the panel is opened with a real F10, and it is
	 * scrolled with real wheel events through the client's own listener. Nothing here reaches past
	 * the client's front door, so a frame is what a player would see.
	 */
	static void swaps(String prefix) throws Exception {
		int want = Integer.getInteger("shots.swaps", 40).intValue();
		MenuSwaps.clear();
		String[] kinds = { "yel", "cya", "lre", "whi" };
		String[] verbs = { "Attack", "Talk-to", "Bury", "Take", "Use", "Pickpocket", "Trade with", "Bank" };
		String[] names = { "Guard", "Goblin", "Banker", "Bones", "Coins", "Yew tree", "Furnace", "Altar",
			"Al Kharid warrior", "Hill giant", "Man", "Cow", "Chicken", "Rat", "Iron ore", "Lobster" };
		for (int i = 0; i < want; i++) {
			MenuSwaps.add(kinds[i % kinds.length], names[i % names.length] + " " + (i + 1), verbs[i % verbs.length]);
		}
		say(MenuSwaps.count() == want ? ("ok " + want + " swaps stored, where the old cap was 16")
			: ("FAIL only " + MenuSwaps.count() + " of " + want + " swaps stored (MenuSwaps.MAX is " + MenuSwaps.MAX + ")"));

		openSwaps();
		int rows = ((Number) call("swapPanelRows", Integer.valueOf(want))).intValue();
		int shown = rows - ((Number) staticField("SWAP_PANEL_ACTIONS")).intValue();
		int max = ((Number) call("swapScrollMax", Integer.valueOf(want))).intValue();
		say("ok the panel shows " + shown + " of " + want + " swaps, " + max + "px of list behind the bar");
		shot(prefix + "_swaps_top");

		// Enough notches to reach the bottom of any list, then a few more: the clamp is the thing
		// being tested as much as the scrolling is.
		int[] mid = swapPanelPoint(want, 60, rows / 2);
		wheel(mid[0], mid[1], want);
		int at = geti("swapScrollPx");
		say(at == max ? ("ok the wheel reaches the bottom of the list and stops there (" + at + "px)")
			: ("FAIL the wheel left the list at " + at + "px of " + max));
		int first = ((Number) call("swapFirstRow", Integer.valueOf(want))).intValue();
		say(first + shown == want ? ("ok the last swap stored is the last row drawn (" + (first + 1) + "-" + want + ")")
			: ("FAIL showing " + (first + 1) + "-" + (first + shown) + " of " + want));
		shot(prefix + "_swaps_bottom");

		// The row a click lands on is read through the scroll position, not straight out of the
		// list - the one thing scrolling could quietly get wrong. Cycling the bottom row turns THAT
		// swap into a wildcard, so the check is which one changed.
		String was = MenuSwaps.target(want - 1);
		int[] last = swapPanelPoint(want, 60, rows - 1);
		click(last[0], last[1], false);
		waitFrames(10);
		say(MenuSwaps.isAny(want - 1) ? ("ok a click on the bottom row cycles the bottom swap (" + was + ")")
			: ("FAIL clicking the bottom row changed " + firstWildcard() + ", not " + was));
		shot(prefix + "_swaps_clicked");

		wheel(mid[0], mid[1], -want);
		say(geti("swapScrollPx") == 0 ? "ok the wheel comes back to the top and stops there"
			: ("FAIL the wheel left the list at " + geti("swapScrollPx") + "px going up"));
		shot(prefix + "_swaps_backtotop");

		// The bar's own input, which the wheel never touches: its two arrows, then a press near the
		// foot of the track so the grip jumps there.
		int listH = shown * ((Number) staticField("SWAP_PANEL_ROW_H")).intValue();
		int[] down = swapBarPoint(want, listH - 8);
		hold(down[0], down[1], 15);
		int held = geti("swapScrollPx");
		say(held > 0 ? ("ok holding the bar's down arrow scrolls the list (" + held + "px)")
			: "FAIL holding the bar's down arrow did nothing");
		int[] up = swapBarPoint(want, 8);
		hold(up[0], up[1], 15);
		say(geti("swapScrollPx") < held ? ("ok ...and its up arrow brings it back (" + geti("swapScrollPx") + "px)")
			: ("FAIL the up arrow left the list at " + geti("swapScrollPx") + "px, from " + held));
		int[] track = swapBarPoint(want, listH - 20);
		hold(track[0], track[1], 4);
		say(geti("swapScrollPx") == max ? "ok a press at the foot of the bar takes the grip to the bottom"
			: ("FAIL a press at the foot of the bar left the list at " + geti("swapScrollPx") + "px of " + max));
		shot(prefix + "_swaps_bardragged");
		say("done");
		log.close();
		System.exit(0);
	}

	/** F10 until the swaps panel is actually open, the way openSettings() does it for F9. */
	static void openSwaps() throws Exception {
		for (int i = 0; i < 4; i++) {
			if (((Boolean) get("swapPanelOpen")).booleanValue()) {
				return;
			}
			key(KeyEvent.VK_F10, (char) 0);
			waitFrames(4);
		}
		say("FAIL the F10 swaps panel would not stay open");
	}

	/** A point inside the open swaps panel, in window coordinates: dx across, row down. */
	static int[] swapPanelPoint(int swaps, int dx, int row) throws Exception {
		int hdr = ((Number) staticField("SWAP_PANEL_HEADER_H")).intValue();
		int rh = ((Number) staticField("SWAP_PANEL_ROW_H")).intValue();
		return new int[] {
			((Number) call("swapPanelX")).intValue() + ((Number) layoutField("vpScreenX")).intValue() + dx,
			((Number) call("swapPanelY", Integer.valueOf(swaps))).intValue()
				+ ((Number) layoutField("vpScreenY")).intValue() + hdr + row * rh + rh / 2
		};
	}

	/** A point on the swaps panel's scrollbar, dy pixels down its track, in window coordinates. */
	static int[] swapBarPoint(int swaps, int dy) throws Exception {
		int w = ((Number) staticField("SWAP_PANEL_W")).intValue();
		int bar = ((Number) staticField("SWAP_PANEL_SCROLL_W")).intValue();
		int actions = ((Number) staticField("SWAP_PANEL_ACTIONS")).intValue();
		int rh = ((Number) staticField("SWAP_PANEL_ROW_H")).intValue();
		int[] at = swapPanelPoint(swaps, w - 1 - bar + bar / 2, actions);
		return new int[] { at[0], at[1] - rh / 2 + dy };
	}

	/** A press held down for a few frames and then released: what an arrow or a grip answers. */
	static void hold(int x, int y, int frames) throws Exception {
		hover(x, y);
		waitFrames(2);
		app.mousePressed(new MouseEvent(app, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), 0, x, y, 1, false, MouseEvent.BUTTON1));
		waitFrames(frames);
		app.mouseReleased(new MouseEvent(app, MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(), 0, x, y, 1, false, MouseEvent.BUTTON1));
		waitFrames(3);
	}

	/** Which swap ended up a wildcard, for a FAIL message that says what went wrong instead of that it did. */
	static String firstWildcard() {
		for (int i = 0; i < MenuSwaps.count(); i++) {
			if (MenuSwaps.isAny(i)) {
				return "#" + (i + 1) + " (" + MenuSwaps.verb(i) + ")";
			}
		}
		return "nothing";
	}

	/**
	 * Wheel notches through the client's own listener, one per pass: GameShell accumulates them into
	 * a single delta that one update() consumes, so ten notches sent in one breath are one turn of
	 * the wheel as far as the client is concerned.
	 */
	static void wheel(int x, int y, int notches) throws Exception {
		hover(x, y);
		waitFrames(2);
		int step = notches < 0 ? -1 : 1;
		for (int i = 0; i < Math.abs(notches); i++) {
			app.mouseWheelMoved(new MouseWheelEvent(app, MouseWheelEvent.MOUSE_WHEEL, System.currentTimeMillis(), 0,
				x, y, 0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 1, step));
			waitFrames(2);
		}
		waitFrames(3);
	}

	/**
	 * A frame of the doll with the window facing straight ahead. The doll sways - client code 328
	 * turns it by the sine of loopCycle - so two frames taken at whatever moment they happen to land
	 * on show the figure at two different angles, and a pixel compare then measures the sway rather
	 * than the pose. loopCycle is put back to 0 so every frame is taken at the same point of it, and
	 * then straight back to where it was: the client counts everything else in loopCycle too, and a
	 * session left sitting at 4 stopped taking typed chat.
	 */
	static void dollShot(String name) throws Exception {
		int was = geti("loopCycle");
		set("loopCycle", Integer.valueOf(0));
		shot(name);
		set("loopCycle", Integer.valueOf(was + 4));
	}

	/** The interface the server put behind one of the sidebar tabs, or -1 if it left that tab empty. */
	static int tabInterface(int tab) {
		return ((int[]) get("tabInterfaceId"))[tab];
	}

	/** The account this run logs in as, so the doll mode can log back in after a tutorial logout. */
	static String dollUser;

	/** Where the weapon sits in the appearance the client has for a player (ClientPlayer.field1674). */
	static final int RIGHT_HAND = 3;

	/** The obj in one slot of that appearance, or 0 for an empty slot. */
	static int wornPart(int part) throws Exception {
		Object self = Client.class.getField("localPlayer").get(null);
		return self == null ? 0 : ((int[]) self.getClass().getField("field1674").get(self))[part];
	}

	/** What the side panel's inventory holds, slot by slot, as the client has it. */
	static int[] sideSlots() throws Exception {
		Class<?> comp = Class.forName("jagex2.config.Component");
		Method get = comp.getMethod("get", int.class);
		Object inv = findInv(comp, get, get.invoke(null, Integer.valueOf(geti("sidebarInterfaceId"))), 0);
		if (inv == null) {
			return new int[0];
		}
		return ((int[]) comp.getField("invSlotObjId").get(inv)).clone();
	}

	/** The slot that has gained something since these were read, waiting up to ten seconds for it. */
	static int filledSlot(int[] was) throws Exception {
		for (int waited = 0; waited < 40; waited++) {
			waitFrames(5);
			int[] now = sideSlots();
			for (int slot = 0; slot < now.length && slot < was.length; slot++) {
				if (now[slot] > 0 && now[slot] != was[slot]) {
					return slot;
				}
			}
		}
		return -1;
	}

	/** What the client has for the doll right now: the stand seq it was given, and the pose it draws. */
	static void dollSay(String what) throws Exception {
		Class<?> comp = Class.forName("jagex2.config.Component");
		Method get = comp.getMethod("get", int.class);
		Object doll = findCode328(comp, get, get.invoke(null, Integer.valueOf(geti("viewportInterfaceId"))), 0);
		Object self = Client.class.getField("localPlayer").get(null);
		int ready = self == null ? -2 : self.getClass().getField("field1181").getInt(self);
		int[] worn = self == null ? null : (int[]) self.getClass().getField("field1674").get(self);
		if (doll == null) {
			say("doll " + what + ": no client-code-328 component in interface " + geti("viewportInterfaceId"));
			return;
		}
		int anim = comp.getField("anim").getInt(doll);
		int frame = comp.getField("field717").getInt(doll);
		int cycle = comp.getField("field709").getInt(doll);
		int frames = -1;
		int transform = -1;
		if (anim >= 0) {
			Class<?> seqc = Class.forName("jagex2.config.SeqType");
			Object[] seqs = (Object[]) seqc.getField("field775").get(null);
			Object seq = seqs[anim];
			frames = seqc.getField("field776").getInt(seq);
			int[] primary = (int[]) seqc.getField("field777").get(seq);
			transform = frame < primary.length ? primary[frame] : -1;
		}
		say("doll " + what + ": readyanim(field1181)=" + ready + " com.anim=" + anim + " com.id="
			+ comp.getField("id").getInt(doll) + " frame=" + frame + "/" + frames + " cycle=" + cycle
			+ " transform=" + transform + " modelType=" + comp.getField("modelType").getInt(doll)
			+ " model=" + comp.getField("model").getInt(doll)
			+ " worn=" + java.util.Arrays.toString(worn)
			+ " hash=" + (self == null ? "?" : Long.toHexString(self.getClass().getField("field1676").getLong(self))));
	}

	/** The doll: the one component of an open interface that carries client code 328. */
	static Object findCode328(Class<?> comp, Method get, Object c, int depth) throws Exception {
		if (c == null || depth > 4) {
			return null;
		}
		if (comp.getField("clientCode").getInt(c) == 328) {
			return c;
		}
		int[] kids = (int[]) comp.getField("children").get(c);
		for (int i = 0; kids != null && i < kids.length && kids[i] != -1; i++) {
			Object found = findCode328(comp, get, get.invoke(null, Integer.valueOf(kids[i])), depth + 1);
			if (found != null) {
				return found;
			}
		}
		return null;
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

	/**
	 * How many of N clicks actually happen, at a held frame rate - and, at the slowest of them, that
	 * a click that does happen happens once.
	 *
	 * Run on the fixed 765x503 screen, where drawing costs a couple of milliseconds, and the frame
	 * rate is set by padding every frame (slowDrawMs) instead. GameShell's catch-up loop only reads
	 * the clock, so a padded frame is the same thing to it as a 1920x1080 one, and the rate is the
	 * same on any machine.
	 *
	 *   ~20 ms/frame  one update per frame  - the rate 377 was written for
	 *   ~26 ms/frame  two updates per frame - a big window
	 *   ~60 ms/frame  three or four         - 1920x1080 with something else on the machine
	 */
	static void clickRates(String prefix) throws Exception {
		int row = Integer.getInteger("shots.clickrow", 0).intValue();      // a plain on/off setting
		int n = Integer.getInteger("shots.clicks", 16).intValue();
		String[] pads = System.getProperty("shots.pads", "0,22,40,58").split(",");
		int worst = 0;
		for (String p : pads) {
			slowDrawMs = Integer.parseInt(p.trim());
			waitFrames(25);
			long t0 = System.currentTimeMillis();
			int f0 = frames;
			int u0 = updates;
			waitFrames(60);
			double ms = (System.currentTimeMillis() - t0) / (double) Math.max(1, frames - f0);
			double upf = (updates - u0) / (double) Math.max(1, frames - f0);
			int took = 0;
			for (int i = 0; i < n; i++) {
				openSettings();
				boolean was = qolOn(row);
				int[] at = settingsRowAt(row);
				click(at[0], at[1], false);
				waitFrames(10);
				if (qolOn(row) != was) {
					took++;                 // a click acted on twice would toggle back, and not count
				}
			}
			worst = took == n ? worst : worst + 1;
			say(String.format("%s clicks: %5.1f ms/frame, %.2f updates/frame - %2d of %2d clicks on the F9 '%s' row took effect",
				took == n ? "ok  " : "FAIL", ms, upf, took, n, qolLabel(row)));
		}
		doubleActions();
		slowDrawMs = 0;
		waitFrames(20);
		say("click rates: " + (pads.length - worst) + " of " + pads.length + " rates landed every click");
		say("done");
		log.close();
		System.exit(0);
	}

	/**
	 * At the slowest frame rate: one click, one action. A press that survived an extra update and
	 * was acted on again would walk the player twice or eat two pieces of food off one click.
	 */
	static void doubleActions() throws Exception {
		if (((Boolean) get("qolPanelOpen")).booleanValue()) {
			key(KeyEvent.VK_F9, (char) 0);
			waitFrames(6);
		}
		slowDrawMs = 58;
		waitFrames(30);
		// 1. an item: every click on it acts on it once. The bank's own inventory side is what is
		//    clicked, because a throwaway account is still in the tutorial and has no inventory tab
		//    yet; a left click there banks one item, so six clicks must move six and not twelve.
		//    Done before the walk clicks below - a bank closes the moment the player takes a step.
		command("::give cooked_meat 20");
		waitFrames(20);
		int clicks = 6;
		int banked = 0;
		for (int i = 0; i < clicks; i++) {
			if (geti("sidebarInterfaceId") == -1 && geti("viewportInterfaceId") == -1) {
				openBank();
				waitFrames(40);
			}
			int[] at = invSlotAt();
			int used = invUsed();
			click(at[0], at[1], false);
			waitFrames(25);
			banked += used - invUsed();
			say("   item click " + (i + 1) + " at " + at[0] + "," + at[1] + ": " + used + " -> " + invUsed() + " slots used");
		}
		say((banked == clicks ? "ok   " : "FAIL ") + clicks + " clicks on an inventory item banked "
			+ banked + " of them, one each");
		call("closeInterfaces");
		waitFrames(20);
		// 2. the scene: every left click runs exactly one menu option (Walk here, or whatever is there)
		int n = 8;
		int once = 0;
		int more = 0;
		int never = 0;
		for (int i = 0; i < n; i++) {
			int before = menuOptions;
			click(100 + i % 4 * 40, 80 + i % 3 * 40, false);
			waitFrames(12);
			int ran = menuOptions - before;
			if (ran == 1) {
				once++;
			} else if (ran > 1) {
				more++;
			} else {
				never++;
			}
		}
		say((once == n ? "ok   " : "FAIL ") + n + " walk clicks on the scene at " + slowDrawMs
			+ "ms a frame ran one menu option each (" + once + " once, " + more + " more than once, " + never + " never)");
	}

	/** The 28-slot inventory the bank's side panel shows. */
	static int invUsed() throws Exception {
		Class<?> comp = Class.forName("jagex2.config.Component");
		Method get = comp.getMethod("get", int.class);
		Object inv = null;
		int[] roots = { geti("sidebarInterfaceId"), geti("viewportInterfaceId") };
		for (int i = 0; inv == null && i < roots.length; i++) {
			if (roots[i] != -1) {
				inv = findInv(comp, get, get.invoke(null, Integer.valueOf(roots[i])), 0);
			}
		}
		if (inv == null) {
			throw new RuntimeException("no 28-slot inventory in the open interfaces (sidebar " + roots[0]
				+ ", main " + roots[1] + ")");
		}
		int[] ids = (int[]) comp.getField("invSlotObjId").get(inv);
		int used = 0;
		for (int i = 0; i < ids.length; i++) {
			if (ids[i] > 0) {
				used++;
			}
		}
		return used;
	}

	/** The component the inventory on screen belongs to, set by the last invSlotAt(). */
	static Object invComponent;

	/**
	 * The middle of the first slot of the inventory on screen that still holds something, in window
	 * coordinates. Banking a slot leaves it empty rather than closing the gap, so the second click
	 * of a run has to go one slot further along.
	 */
	static int[] invSlotAt() throws Exception {
		return invSlotAt(-1);
	}

	/** The middle of one slot of the inventory on screen, or of the first that holds something (-1). */
	static int[] invSlotAt(int want) throws Exception {
		Class<?> comp = Class.forName("jagex2.config.Component");
		Method get = comp.getMethod("get", int.class);
		int side = geti("sidebarInterfaceId");
		int main = geti("viewportInterfaceId");
		int[] p = null;
		int ox = 0;
		int oy = 0;
		if (side != -1) {
			p = invPath(comp, get, get.invoke(null, Integer.valueOf(side)), 0, 0, 0);
			if (p != null) {
				ox = ((Number) staticField("SIDE_X")).intValue();
				oy = 205;
			}
		}
		if (p == null && main != -1) {
			p = invPath(comp, get, get.invoke(null, Integer.valueOf(main)), 0, 0, 0);
			if (p != null) {
				ox = ((Number) layoutField("vpX")).intValue() + ((Number) layoutField("mainX")).intValue();
				oy = ((Number) layoutField("vpY")).intValue() + ((Number) layoutField("mainY")).intValue();
			}
		}
		if (p == null) {
			throw new RuntimeException("no inventory on screen (sidebar " + side + ", main " + main + ")");
		}
		int[] ids = (int[]) comp.getField("invSlotObjId").get(invComponent);
		int cols = comp.getField("width").getInt(invComponent);
		int pitchX = comp.getField("marginX").getInt(invComponent) + 32;
		int pitchY = comp.getField("marginY").getInt(invComponent) + 32;
		for (int slot = 0; slot < ids.length; slot++) {
			if (want == slot || (want == -1 && ids[slot] > 0)) {
				int x = ox + p[0] + slot % cols * pitchX + 16;
				int y = oy + p[1] + slot / cols * pitchY + 16;
				return side != -1 && oy == 205 ? windowOf(x, y) : new int[] { x, y };
			}
		}
		throw new RuntimeException("the inventory on screen is empty");
	}

	/** The top-left of the 28-slot inventory under this component, relative to it. */
	static int[] invPath(Class<?> comp, Method get, Object c, int x, int y, int depth) throws Exception {
		if (c == null || depth > 4) {
			return null;
		}
		int[] ids = (int[]) comp.getField("invSlotObjId").get(c);
		if (ids != null && ids.length == 28) {
			invComponent = c;
			return new int[] { x, y };
		}
		int[] kids = (int[]) comp.getField("children").get(c);
		int[] kx = (int[]) comp.getField("childX").get(c);
		int[] ky = (int[]) comp.getField("childY").get(c);
		for (int i = 0; kids != null && i < kids.length; i++) {
			int[] found = invPath(comp, get, get.invoke(null, Integer.valueOf(kids[i])), x + kx[i], y + ky[i], depth + 1);
			if (found != null) {
				return found;
			}
		}
		return null;
	}

	/** The 28-slot inventory somewhere under this component. */
	static Object findInv(Class<?> comp, Method get, Object c, int depth) throws Exception {
		if (c == null || depth > 4) {
			return null;
		}
		int[] ids = (int[]) comp.getField("invSlotObjId").get(c);
		if (ids != null && ids.length == 28) {
			return c;
		}
		int[] kids = (int[]) comp.getField("children").get(c);
		for (int i = 0; kids != null && i < kids.length; i++) {
			Object found = findInv(comp, get, get.invoke(null, Integer.valueOf(kids[i])), depth + 1);
			if (found != null) {
				return found;
			}
		}
		return null;
	}

	static boolean qolOn(int setting) throws Exception {
		return ((Boolean) Class.forName("jagex2.client.QolSettings").getMethod("on", int.class)
			.invoke(null, Integer.valueOf(setting))).booleanValue();
	}

	static String qolLabel(int setting) throws Exception {
		return (String) Class.forName("jagex2.client.QolSettings").getMethod("label", int.class)
			.invoke(null, Integer.valueOf(setting));
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
	// harness follows the client when a panel's rectangle changes. CHAT 0, MINIMAP 1, SIDEBAR 2,
	// and in the modern layout TABS_TOP 3 and TABS_BOTTOM 4 as well.
	static final int SIDEBAR = 2;

	static int layoutOn(String method, int panel) throws Exception {
		Object layout = get("layout");
		return ((Number) layout.getClass().getMethod(method, int.class).invoke(layout, Integer.valueOf(panel))).intValue();
	}

	/**
	 * A point of the fixed 765x503 frame, in window coordinates: whichever panel holds it, wherever
	 * this layout put that panel. Asked of Layout rather than written down here, so the harness
	 * follows the client between the classic layout and the modern one, where the tab rows are
	 * panels of their own.
	 */
	static int[] windowOf(int fixedX, int fixedY) throws Exception {
		Object layout = get("layout");
		int panels = layout.getClass().getField("panels").getInt(layout);
		for (int p = 0; p < panels; p++) {
			int px = layoutOn("panelFixedX", p);
			int py = layoutOn("panelFixedY", p);
			if (fixedX >= px && fixedX < px + layoutOn("panelWidth", p) && fixedY >= py && fixedY < py + layoutOn("panelHeight", p)) {
				return new int[] { layoutOn("panelScreenX", p) + fixedX - px, layoutOn("panelScreenY", p) + fixedY - py };
			}
		}
		throw new RuntimeException("no panel holds the fixed point " + fixedX + "," + fixedY);
	}

	/** The panels take the clicks meant for them, and the scene the rest, however big the window. */
	static void resizableChecks(int w, int h, String prefix) throws Exception {
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
		int[] tabAt = windowOf(fx0 + 10, fy0 + 15);
		click(tabAt[0], tabAt[1], false);
		int tabAfter = geti("selectedTab");
		boolean t = tabAfter == want;
		say((t ? "ok   " : "FAIL ") + "a click on tab " + want + "'s button in the bottom-right panel selects it (" + tabBefore + " -> " + tabAfter + ")");
		if (t) ok++; else bad++;
		shot(prefix + "_tab");
		// 2. hover the inventory: the menu is the sidebar's
		int[] invAt = windowOf(560, 230);
		hover(invAt[0], invAt[1]);
		waitFrames(5);
		say("hover over the inventory's first slot: " + menu());
		// 3. the minimap: a click walks, setting the flag
		set("flagSceneTileX", 0);
		int[] mapAt = windowOf(545 + 25 + 73 + 30, 4 + 5 + 75 + 10);
		click(mapAt[0], mapAt[1], false);
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
		shot(prefix + "_walk");
		waitFrames(100);
		// 5. mouse picking across the whole window: sweep a grid, count points where the scene
		//    under the mouse offered something other than Walk here
		int found = 0;
		int far = 0;
		String sample = null;
		int sideW = layoutOn("panelWidth", SIDEBAR) + 11;
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
			shot(prefix + "_pickmenu");
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
			shot(prefix + "_" + names[i]);
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
		shot(prefix + "_fullscreen");
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
		// 7. the F9 panel, drawn and clickable in the middle of the open area: its window row steps
		//    fixed -> classic -> modern -> fixed, and every mode draws
		openSettings();
		waitFrames(5);
		shot(prefix + "_settings");
		int windowRow = ((Number) staticField("ROW_WINDOW")).intValue();
		int started = ((Number) call("displayMode")).intValue();
		clickSettingsRow(windowRow, "displayMode");
		int next = ((Number) call("displayMode")).intValue();
		boolean stepsOn = next == (started + 1) % 3;
		say((stepsOn ? "ok   " : "FAIL ") + "a click on the F9 window row steps the display mode on ("
			+ started + " -> " + next + ", of fixed 0, classic 1, modern 2)");
		if (stepsOn) ok++; else bad++;
		// and each of the three draws: switched here rather than clicked, because a click that lands
		// in the wrong frame is lost (see clickSettingsRow) and this is about the drawing, not the row
		for (int dm = 0; dm < 3; dm++) {
			synchronized (LOCK) {
				call("setDisplayMode", Integer.valueOf(dm));
			}
			waitFrames(30);
			int now = ((Number) call("displayMode")).intValue();
			say((now == dm ? "ok   " : "FAIL ") + "display mode " + dm + " (" + (now == dm ? "drawn" : "did not take, at " + now) + ")");
			if (now == dm) ok++; else bad++;
			shot(prefix + (dm == 0 ? "_switchedfixed" : "_switched" + dm));
		}
		synchronized (LOCK) {
			call("setDisplayMode", Integer.valueOf(started));
		}
		waitFrames(30);
		// 8. the draw distance row, under it: a click steps it on to the next value and saves it
		int[] steps = (int[]) Class.forName("jagex2.client.DisplaySettings").getField("DRAW_DISTANCES").get(null);
		int distRow = ((Number) staticField("ROW_DRAW_DISTANCE")).intValue();
		int before = ((Number) call("drawDistance")).intValue();
		clickSettingsRow(distRow, "drawDistance");
		int after = ((Number) call("drawDistance")).intValue();
		boolean stepped = before == steps[0] && after == steps[1];
		say((stepped ? "ok   " : "FAIL ") + "clicking the draw distance row steps it on (" + before + " -> " + after + " tiles)");
		if (stepped) ok++; else bad++;
		shot(prefix + "_drawdistance");
		synchronized (LOCK) {
			call("setDrawDistance", Integer.valueOf(steps[0]));
		}
		waitFrames(10);
		key(KeyEvent.VK_F9, (char) 0);
		waitFrames(10);
		say("resizable checks: " + ok + " ok, " + bad + " failed");
	}
}
