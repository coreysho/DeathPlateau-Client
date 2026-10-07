/*
 * Headless test for Alt-drag: holding Alt and moving an overlay anywhere in the viewport.
 *
 * DRIVEN THROUGH THE REAL MANAGER, with a real plugin, a real OverlayGraphics and Pix2D bound to
 * an int[] the test reads back. So "the overlay moved" is a pixel in a different place, not a
 * field with a different number in it - which matters here more than usual, because the whole
 * mechanism is a translation applied on the way to the screen and a field that changed without
 * the pixels following would be exactly the bug.
 *
 * The plugin under test draws a plain box and a line of text and knows nothing about dragging.
 * That is the point: no plugin had to change for this, including the two already published, and
 * a plugin that could tell it had been moved would be a plugin that could move itself.
 */
package jagex2.client.plugin;

import java.util.List;

import jagex2.client.Client;
import jagex2.graphics.Pix2D;
import jagex2.graphics.PixFont;

public class OverlayDragTest {

	static final int W = 512;
	static final int H = 334;

	/** Where the test plugin draws, before anything drags it. Read from the plugin itself. */
	static final int HOME_X = DragTestPlugin.X;
	static final int HOME_Y = DragTestPlugin.Y;
	static final int BOX_W = DragTestPlugin.W;
	static final int BOX_H = DragTestPlugin.H;

	static final int BOX_COLOUR = DragTestPlugin.COLOUR;

	static int fails;

	static Client client;
	static PluginManager manager;
	static int[] pixels;

	static void check(boolean ok, String what) {
		System.out.println((ok ? "  ok   " : "FAIL   ") + what);
		if (!ok) {
			fails++;
		}
	}

	public static void main(String[] args) throws Exception {
		if (java.awt.GraphicsEnvironment.isHeadless()) {
			System.out.println("  SKIP  no display, so a Client cannot be constructed");
			System.exit(0);
		}
		client = new Client();
		client.ingame = true;
		Client.localPlayer = new jagex2.dash3d.ClientPlayer();
		pixels = new int[W * H];
		Pix2D.bind(W, H, pixels);

		PixFont font = new StubFont();
		manager = new PluginManager(client, font, font, font);
		manager.reload();
		// Everything that came with the client off, so a pixel can only have come from the
		// plugin this test installed.
		for (PluginManager.Entry entry : manager.getPlugins()) {
			if (entry.isEnabled()) {
				manager.setEnabled(entry, false);
			}
		}

		// The plugin this test drags. Not a built-in and not in a jar, so it is registered
		// through the manager's own instantiate - the same path a real plugin takes.
		install(new DragTestPlugin(), "drag-test");

		System.out.println("1. an overlay nobody has moved");
		homeTests();
		System.out.println();
		System.out.println("2. picking one up and putting it down");
		dragTests();
		System.out.println();
		System.out.println("3. what Alt does and does not take over");
		altTests();
		System.out.println();
		System.out.println("4. how big an overlay is, and which one is on top");
		extentTests();
		System.out.println();
		System.out.println("5. the edges of the viewport");
		clampTests();
		System.out.println();
		System.out.println("6. snapping, saving and resetting");
		persistenceTests();

		System.out.println();
		System.out.println(fails == 0 ? "ALL PASS" : fails + " FAILED");
		System.exit(fails == 0 ? 0 : 1);
	}

	// ---------------------------------------------------------------- 1

	static void homeTests() {
		draw();
		check(boxAt(HOME_X, HOME_Y), "an overlay draws where its plugin puts it ("
			+ HOME_X + "," + HOME_Y + ")");
		check(painted() > 0, "...and paints something (" + painted() + " pixels)");
	}

	// ---------------------------------------------------------------- 2

	static void dragTests() {
		// Grabbed off-centre on purpose: an overlay that jumps its own corner to the cursor is
		// the commonest way to get this wrong, and it only shows when the grab is not at 0,0.
		int grabX = HOME_X + 40;
		int grabY = HOME_Y + 10;
		alt(grabX, grabY, 0);
		draw();
		check(boxAt(HOME_X, HOME_Y), "holding Alt over an overlay does not move it on its own");

		alt(grabX, grabY, 1);
		draw();
		check(boxAt(HOME_X, HOME_Y), "pressing on it does not move it either, until the mouse does");

		alt(grabX + 60, grabY + 30, 1);
		draw();
		check(boxAt(HOME_X + 60, HOME_Y + 30),
			"dragging moves it by what the cursor moved, not to where the cursor is");

		alt(grabX + 60, grabY + 30, 0);
		draw();
		check(boxAt(HOME_X + 60, HOME_Y + 30), "letting go leaves it there");

		// Alt released, mouse moved: nothing should follow the cursor any more.
		plain(300, 300);
		draw();
		check(boxAt(HOME_X + 60, HOME_Y + 30), "...and it stays there once Alt is let go");

		// PICKED UP A SECOND TIME, from where it now is. The grab area is recorded from what was
		// drawn, so a box recorded before the offset was applied would still be back at home -
		// and every check above would pass, because they all grab at home.
		alt(HOME_X + 60 + 5, HOME_Y + 30 + 5, 0);
		alt(HOME_X + 60 + 5, HOME_Y + 30 + 5, 1);
		alt(HOME_X + 60 + 25, HOME_Y + 30 + 45, 1);
		alt(HOME_X + 60 + 25, HOME_Y + 30 + 45, 0);
		draw();
		check(boxAt(HOME_X + 80, HOME_Y + 70),
			"an overlay can be picked up again where it now is, not where it started");

		// And NOT where it used to be: the old box must not still be grabbable.
		alt(HOME_X + 5, HOME_Y + 5, 0);
		alt(HOME_X + 5, HOME_Y + 5, 1);
		alt(HOME_X + 5 + 100, HOME_Y + 5, 1);
		alt(HOME_X + 5 + 100, HOME_Y + 5, 0);
		draw();
		check(boxAt(HOME_X + 80, HOME_Y + 70),
			"...and grabbing where it used to be grabs nothing");

		reset();
		check(boxAt(HOME_X, HOME_Y), "resetting puts it back where the plugin draws it");

		// The button on it. A region left behind is a button pressed by clicking empty space.
		int before = DragTestPlugin.pressed;
		manager.onViewportClick(HOME_X + 2, HOME_Y + 2);
		check(DragTestPlugin.pressed == before + 1, "the overlay's own button works at home");

		alt(HOME_X + 40, HOME_Y + 20, 0);
		alt(HOME_X + 40, HOME_Y + 20, 1);
		alt(HOME_X + 40 + 90, HOME_Y + 20 + 60, 1);
		alt(HOME_X + 40 + 90, HOME_Y + 20 + 60, 0);
		draw();
		before = DragTestPlugin.pressed;
		manager.onViewportClick(HOME_X + 2, HOME_Y + 2);
		check(DragTestPlugin.pressed == before,
			"...and once dragged away, clicking where it was presses nothing");
		manager.onViewportClick(HOME_X + 90 + 2, HOME_Y + 60 + 2);
		check(DragTestPlugin.pressed == before + 1,
			"...while clicking where it now is presses it");

		reset();
	}

	// ---------------------------------------------------------------- 4

	static void extentTests() {
		draw();
		// The grab area is what was DRAWN, which ends with the text. Text is drawn from its
		// baseline, so reading that as a top-left would put the area a whole line too low and
		// leave a gap over the words a player is pointing at.
		check(manager.dragWantsClick(HOME_X + 2, DragTestPlugin.TEXT_BASELINE - 4),
			"the text is part of what can be grabbed, measured from its baseline");
		// JUST BELOW THE BASELINE, which is the only place the mistake shows. Measuring text
		// from its top instead puts the bottom of the grab area a whole line lower - but the
		// area is one box round everything drawn, so a probe far below is outside it either way
		// and a probe inside the box is inside it either way. The gap the mistake opens up is
		// the few pixels between "just under the words" and "a line under the words", and only
		// a probe in there can tell the two apart.
		check(!manager.dragWantsClick(HOME_X + 2, DragTestPlugin.TEXT_BASELINE + 6),
			"...and the area stops just under the words, not a line below them");
		check(!manager.dragWantsClick(HOME_X + 2, DragTestPlugin.TEXT_BASELINE + 20),
			"...with nothing further down grabbable either");
		check(!manager.dragWantsClick(HOME_X + BOX_W + 30, HOME_Y + 2),
			"nor is empty space to the right of it");

		// Two overlays in the same place: the one a player can see is the one drawn last.
		OverlapTestPlugin over = install(new OverlapTestPlugin(), "overlap-test");
		draw();
		int grabX = OverlapTestPlugin.X + 4;
		int grabY = OverlapTestPlugin.Y + 4;
		alt(grabX, grabY, 0);
		alt(grabX, grabY, 1);
		alt(grabX + 50, grabY + 60, 1);
		alt(grabX + 50, grabY + 60, 0);
		draw();
		check(overlapBoxAt(OverlapTestPlugin.X + 50, OverlapTestPlugin.Y + 60),
			"where two overlays overlap, the one on top is the one grabbed");
		check(boxAt(HOME_X, HOME_Y), "...and the one underneath has not moved");

		reset();
		manager.setEnabled(entry("overlap-test"), false);
		draw();
		if (over == null) {
			check(false, "unreachable");
		}
	}

	// ---------------------------------------------------------------- 3

	static void altTests() {
		// A click that grabs an overlay must not also reach the overlay's own buttons or the
		// game: grabbing something with a button on it would press that button.
		alt(HOME_X + 5, HOME_Y + 5, 0);
		check(manager.dragWantsClick(HOME_X + 5, HOME_Y + 5),
			"with Alt held, a click on an overlay belongs to the drag");
		check(!manager.dragWantsClick(5, 300),
			"...but a click on empty ground still belongs to the game");

		plain(HOME_X + 5, HOME_Y + 5);
		check(!manager.dragWantsClick(HOME_X + 5, HOME_Y + 5),
			"without Alt, a click on an overlay is the overlay's as it always was");

		// The scene layer is drawn over a tile in the world. An offset on one of those is a label
		// pointing at the wrong thing, so it must not be grabbable at all.
		SceneOverlayPlugin scene = install(new SceneOverlayPlugin(), "scene-overlay");
		draw();
		// Grabbed INSIDE the scene overlay's own box. Grabbing somewhere else proves nothing
		// about the scene layer, which is what the first version of this test did.
		int sceneX = SceneOverlayPlugin.X + 4;
		int sceneY = SceneOverlayPlugin.Y + 4;
		check(!manager.dragWantsClick(sceneX, sceneY),
			"Alt over a scene-layer overlay does not claim the click");
		alt(sceneX, sceneY, 0);
		alt(sceneX, sceneY, 1);
		alt(sceneX + 80, sceneY + 80, 1);
		alt(sceneX + 80, sceneY + 80, 0);
		draw();
		check(sceneBoxAt(SceneOverlayPlugin.X, SceneOverlayPlugin.Y),
			"a scene-layer overlay cannot be dragged: it belongs to a tile, not the screen");
		manager.setEnabled(entry("scene-overlay"), false);
		if (scene == null) {
			check(false, "unreachable");
		}
		reset();
	}

	// ---------------------------------------------------------------- 4

	static void clampTests() {
		// Dragged hard off the left edge. A strip has to stay, or there is nothing left to grab
		// to bring it back and nothing on screen to say where it went.
		alt(HOME_X + 5, HOME_Y + 5, 0);
		alt(HOME_X + 5, HOME_Y + 5, 1);
		alt(-900, HOME_Y + 5, 1);
		alt(-900, HOME_Y + 5, 0);
		draw();
		int left = leftmostPainted();
		check(painted() > 0, "dragged off the left edge, some of it is still drawn");
		check(left >= 0 && left < OverlayPositions.KEEP_VISIBLE + BOX_W,
			"...a strip of it, at the edge (leftmost painted column " + left + ")");

		reset();
		alt(HOME_X + 5, HOME_Y + 5, 0);
		alt(HOME_X + 5, HOME_Y + 5, 1);
		alt(W + 900, H + 900, 1);
		alt(W + 900, H + 900, 0);
		draw();
		check(painted() > 0, "and dragged off the bottom right, some of it is still drawn ("
			+ painted() + " pixels)");
		reset();
	}

	// ---------------------------------------------------------------- 5

	static void persistenceTests() {
		// Dropped a couple of pixels from home: that is a player trying to put it back, and a
		// mouse will not give them exactly zero.
		alt(HOME_X + 5, HOME_Y + 5, 0);
		alt(HOME_X + 5, HOME_Y + 5, 1);
		alt(HOME_X + 5 + 40, HOME_Y + 5 + 40, 1);
		alt(HOME_X + 5 + 3, HOME_Y + 5 + 2, 1);
		alt(HOME_X + 5 + 3, HOME_Y + 5 + 2, 0);
		draw();
		check(boxAt(HOME_X, HOME_Y),
			"dropped within a few pixels of home, it snaps exactly home");

		check(OverlayPositions.snap(3) == 0 && OverlayPositions.snap(-3) == 0,
			"snap pulls a near-miss to zero");
		check(OverlayPositions.snap(40) == 40 && OverlayPositions.snap(-40) == -40,
			"...and leaves a real move alone");

		// ANOTHER PLUGIN WITH AN OVERLAY TURNED ON FIRST, and it matters. The saved key is the
		// overlay's index within ITS OWN plugin, taken before the flat list is sorted by
		// priority. Taken after, it would be an index into every plugin's overlays at once and
		// would change whenever another plugin was turned on - which a test with only one
		// plugin running cannot tell apart. "Boosts" sorts before "Drag test", so it is keyed
		// first and the two answers differ.
		PluginManager.Entry boosts = entry("boosts");
		if (boosts != null) {
			manager.setEnabled(boosts, true);
		}
		check(boosts != null, "a second plugin with an overlay of its own is available");

		java.io.File saved = new java.io.File(sign.signlink.findcachedir() + "plugins.dat");
		alt(HOME_X + 5, HOME_Y + 5, 0);
		alt(HOME_X + 5, HOME_Y + 5, 1);
		alt(HOME_X + 5 + 70, HOME_Y + 5 + 50, 1);
		// MID-DRAG. The position is not written yet: a drag runs every frame, and saving on each
		// one is a write to plugins.dat fifty times a second to record somewhere the player has
		// not settled on.
		check(read(saved).indexOf("overlay.drag-test#0.at=70,50") < 0,
			"a drag in progress has not written anything yet");
		alt(HOME_X + 5 + 70, HOME_Y + 5 + 50, 0);
		String text = read(saved);
		check(text.indexOf("overlay.drag-test#0.at=70,50") >= 0,
			"and dropping it writes it to plugins.dat: "
				+ lineWith(text, "overlay.drag-test#0.at"));

		// And read back by a manager that has never seen this run - which is what a relaunch is.
		PluginManager second = new PluginManager(client, new StubFont(), new StubFont(), new StubFont());
		second.reload();
		PluginManager original = manager;
		manager = second;
		install(new DragTestPlugin(), "drag-test");
		draw();
		check(boxAt(HOME_X + 70, HOME_Y + 50),
			"...and a fresh client puts it back where it was left");
		manager = original;

		if (boosts != null) {
			manager.setEnabled(boosts, false);
		}
		reset();
		check(boxAt(HOME_X, HOME_Y), "reset clears it again");
		check(read(saved).indexOf("overlay.drag-test#0.at=0,0") >= 0,
			"...and writes that, rather than leaving the old position in the file");
	}

	// ---------------------------------------------------------------- the plumbing

	/** Sets the cursor, the button and Alt, then runs one frame of the drag code. */
	static void alt(int x, int y, int button) {
		client.actionKey[jagex2.client.GameShell.KEY_ALT] = 1;
		manager.onOverlayDrag(x, y, button, true, W, H);
	}

	/** The same with Alt up. */
	static void plain(int x, int y) {
		client.actionKey[jagex2.client.GameShell.KEY_ALT] = 0;
		manager.onOverlayDrag(x, y, 0, false, W, H);
	}

	/**
	 * Resets every overlay and draws a frame.
	 *
	 * The frame is not optional. Hit-testing reads the box the LAST frame drew, so a reset
	 * followed straight by an Alt-hover would be testing against where the overlay used to be -
	 * which the client never does, because a frame is always drawn in between.
	 */
	static void reset() {
		manager.resetOverlayPositions();
		draw();
	}

	/** One frame: clears the buffer and renders both layers, as the client does. */
	static void draw() {
		java.util.Arrays.fill(pixels, 0);
		manager.renderOverlays(W, H, Overlay.LAYER_SCENE);
		manager.renderOverlays(W, H, Overlay.LAYER_SCREEN);
	}

	static boolean boxAt(int x, int y) {
		return pixels[y * W + x] == BOX_COLOUR && pixels[(y + BOX_H - 1) * W + x + BOX_W - 1] == BOX_COLOUR;
	}

	static boolean sceneBoxAt(int x, int y) {
		return pixels[y * W + x] == SceneOverlayPlugin.COLOUR;
	}

	static boolean overlapBoxAt(int x, int y) {
		return pixels[y * W + x] == OverlapTestPlugin.COLOUR;
	}

	static int painted() {
		int count = 0;
		for (int i = 0; i < pixels.length; i++) {
			if (pixels[i] == BOX_COLOUR) {
				count++;
			}
		}
		return count;
	}

	static int leftmostPainted() {
		for (int x = 0; x < W; x++) {
			for (int y = 0; y < H; y++) {
				if (pixels[y * W + x] == BOX_COLOUR) {
					return x;
				}
			}
		}
		return -1;
	}

	static PluginManager.Entry entry(String key) {
		for (PluginManager.Entry e : manager.getPlugins()) {
			if (e.key.equals(key)) {
				return e;
			}
		}
		return null;
	}

	/** Attaches a plugin to the manager by hand and turns it on. */
	static <T extends Plugin> T install(T plugin, String key) {
		try {
			java.lang.reflect.Method instantiate =
				PluginManager.class.getDeclaredMethod("instantiate", PluginLoader.Found.class);
			instantiate.setAccessible(true);
			instantiate.invoke(manager, new PluginLoader.Found(plugin.getClass(), "test", null));
			PluginManager.Entry entry = entry(key);
			if (entry == null) {
				check(false, "the test plugin " + key + " did not register");
				return null;
			}
			manager.setEnabled(entry, true);
			return plugin;
		} catch (Throwable error) {
			check(false, "cannot install " + key + " (" + error + ")");
			return null;
		}
	}

	static String read(java.io.File file) {
		try {
			return new String(java.nio.file.Files.readAllBytes(file.toPath()), "UTF-8");
		} catch (Throwable missing) {
			return "";
		}
	}

	static String lineWith(String text, String needle) {
		for (String line : text.split("\n")) {
			if (line.indexOf(needle) >= 0) {
				return line.trim();
			}
		}
		return "(no line with " + needle + ")";
	}

	/** A measuring font, for anything else in this package that needs one without a cache. */
	static PixFont font() {
		return new StubFont();
	}

	/** Measures text without needing a font from the cache. */
	static final class StubFont extends PixFont {

		StubFont() {
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
		}

		public void drawString(int x, int colour, int y, String text) {
		}
	}
}
