/*
 * Headless test for the cursor accessors and the two plugins that use them.
 *
 * THE CHECK THAT MATTERS MOST is that asking what tile the cursor is over cannot make the player
 * walk there. The scene has exactly one "what is at this screen point" slot, and the client
 * already uses it for walk-here: whatever lands in World3D.clickTileX is read a frame later by
 * the walk code. Sharing it wrong breaks one of two ways, and both are tested here - a hover
 * that walks the player, and a click to walk that gets swallowed and does nothing.
 *
 * The rest is the lazy subscription (answering the question costs a hit test per tile, so it is
 * only asked while a plugin is reading it), and the two plugins' own arithmetic.
 */
package jagex2.client.plugin.builtin;

import jagex2.client.Client;
import jagex2.client.plugin.Overlay;
import jagex2.client.plugin.OverlayGraphics;
import jagex2.client.plugin.PluginManager;
import jagex2.dash3d.ClientPlayer;
import jagex2.dash3d.World3D;
import jagex2.graphics.Pix2D;
import jagex2.graphics.PixFont;

public class MouseTest {

	static final int W = 512;
	static final int H = 334;

	static int fails;

	static Client client;
	static PluginManager manager;
	static RecordingFont font;
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
		Client.localPlayer = new ClientPlayer();
		pixels = new int[W * H];
		Pix2D.bind(W, H, pixels);
		font = new RecordingFont();
		manager = new PluginManager(client, font, font, font);
		manager.reload();
		for (PluginManager.Entry entry : manager.getPlugins()) {
			if (entry.isEnabled()) {
				manager.setEnabled(entry, false);
			}
		}

		System.out.println("0. the plugins exist and keep to themselves");
		setUpTests();
		System.out.println();
		System.out.println("1. the cursor, in the coordinates an overlay draws in");
		cursorTests();
		System.out.println();
		System.out.println("2. the shared pick slot, which must not walk the player");
		pickTests();
		System.out.println();
		System.out.println("3. asking costs nothing until somebody reads it");
		subscriptionTests();
		System.out.println();
		System.out.println("4. Mouse highlight");
		highlightTests();
		System.out.println();
		System.out.println("5. Tile indicators");
		tileTests();

		System.out.println();
		System.out.println(fails == 0 ? "ALL PASS" : fails + " FAILED");
		System.exit(fails == 0 ? 0 : 1);
	}

	// ---------------------------------------------------------------- 0

	static void setUpTests() {
		check(entry("mouse-highlight") != null, "Mouse highlight is a built-in plugin");
		check(entry("tile-indicators") != null, "Tile indicators is a built-in plugin");
		check(entry("mouse-highlight") != null && !entry("mouse-highlight").isEnabled()
			&& entry("tile-indicators") != null && !entry("tile-indicators").isEnabled(),
			"...and neither turns itself on");
		check(jagex2.client.plugin.PluginApi.LEVEL >= 3,
			"the cursor accessors are API level 3 (" + jagex2.client.plugin.PluginApi.LEVEL + ")");
	}

	// ---------------------------------------------------------------- 1

	static void cursorTests() {
		jagex2.client.plugin.PluginContext ctx = context();
		// The viewport sits at an offset inside the window, and a plugin draws in viewport
		// coordinates - handing it window ones puts everything it draws out by that offset,
		// which reads as "a bit wrong" rather than as broken.
		//
		// The origin is read off the layout rather than derived from the method being tested.
		// Deriving it is what the first version of this did, and an accessor returning window
		// coordinates passed, because the expected value moved with it.
		int originX = viewportOrigin(true);
		int originY = viewportOrigin(false);
		check(originX > 0 && originY > 0,
			"the viewport starts inside the window, so the two spaces really do differ ("
				+ originX + "," + originY + ")");
		client.mouseX = originX;
		client.mouseY = originY;
		check(ctx.getMouseX() == 0 && ctx.getMouseY() == 0,
			"the cursor on the viewport's top-left corner reads 0,0 (" + ctx.getMouseX() + ","
				+ ctx.getMouseY() + ")");
		client.mouseX = originX + 100;
		client.mouseY = originY + 60;
		check(ctx.getMouseX() == 100 && ctx.getMouseY() == 60,
			"...and a hundred across reads a hundred (" + ctx.getMouseX() + ","
				+ ctx.getMouseY() + ")");

		// Off the game view entirely: a plugin drawing at the cursor has to stop, and "the last
		// place it was" is how a label gets stranded in a corner.
		client.mouseX = -50;
		client.mouseY = -50;
		check(ctx.getMouseX() == -1 && ctx.getMouseY() == -1,
			"outside the viewport it reads -1, not the last place it was");
		client.mouseX = 100000;
		client.mouseY = 100000;
		check(ctx.getMouseX() == -1 && ctx.getMouseY() == -1, "...and past the far edge too");

		client.mouseX = viewportOrigin(true) + 100;
		client.mouseY = viewportOrigin(false) + 60;
	}

	/** Where the viewport starts inside the window, read off the layout the client is using. */
	static int viewportOrigin(boolean horizontal) {
		try {
			java.lang.reflect.Field field = Client.class.getDeclaredField("layout");
			field.setAccessible(true);
			Object layout = field.get(client);
			return layout.getClass().getField(horizontal ? "vpX" : "vpY").getInt(layout);
		} catch (Throwable error) {
			check(false, "cannot read the viewport origin (" + error + ")");
			return 0;
		}
	}

	// ---------------------------------------------------------------- 2

	static void pickTests() {
		// A pick the PLUGIN asked for. It must be taken before the walk code can see it, which
		// means clickTileX ends up cleared and the walk never happens.
		World3D.clickTileX = 11;
		World3D.clickTileZ = 22;
		client.hoverPickPending = true;
		client.hoverTileX = -1;
		client.hoverTileZ = -1;
		boolean took = client.takeHoverPick();
		check(took, "a pick a plugin asked for is taken");
		check(client.hoverTileX == 11 && client.hoverTileZ == 22,
			"...and becomes the hovered tile (" + client.hoverTileX + ","
				+ client.hoverTileZ + ")");
		check(World3D.clickTileX == -1,
			"...and is cleared, so the walk code never sees it and the player stays put");
		check(!client.hoverPickPending, "...and the request is spent, not left armed");

		// A pick the PLAYER asked for by clicking the ground. Taking this one would swallow the
		// walk and the player would stand there wondering why nothing happened.
		World3D.clickTileX = 33;
		World3D.clickTileZ = 44;
		client.hoverPickPending = false;
		took = client.takeHoverPick();
		check(!took, "a pick the player asked for by clicking is left alone");
		check(World3D.clickTileX == 33 && World3D.clickTileZ == 44,
			"...and is still there for the walk code to act on (" + World3D.clickTileX + ")");
		check(client.hoverTileX == 11,
			"...and does not become the hovered tile either (" + client.hoverTileX + ")");
		World3D.clickTileX = -1;
		World3D.clickTileZ = -1;

		// Nothing armed, nothing resolved.
		client.hoverPickPending = true;
		check(!client.takeHoverPick(), "with no pick resolved there is nothing to take");
		client.hoverPickPending = false;

		// THE SEAM THE TESTS ABOVE CANNOT REACH. useMenuOption's walk-here branch has to clear
		// the flag the moment it arms its own pick - without that, a click made while a plugin
		// was hovering arrives with the flag still set and gets taken as a hover. Driving
		// useMenuOption needs a menu, a scene and a camera; reading the source does not.
		String source = read("src/main/java/jagex2/client/Client.java");
		int branch = source.indexOf("if (var5 == 14) {");
		int end = branch < 0 ? -1 : source.indexOf("\n\t\t}", branch);
		String body = branch < 0 || end < 0 ? "" : source.substring(branch, end);
		check(body.indexOf("method312") >= 0 && body.indexOf("hoverPickPending = false") >= 0,
			"the walk-here branch clears the flag right where it arms its own pick");
		// And the hover is only armed when nothing else has: the same frame cannot hold both.
		check(source.indexOf("!World3D.field1044") >= 0,
			"...and a hover is only asked for when no other pick is already armed");
	}

	// ---------------------------------------------------------------- 3

	static void subscriptionTests() {
		jagex2.client.plugin.PluginContext ctx = context();
		PluginManager.Entry entry = entry("tile-indicators");
		manager.setEnabled(entry, true);

		// Nothing has read it in a while, so the client should not be paying for the answer.
		for (int i = 0; i < 40; i++) {
			manager.onClientTick(i);
		}
		check(!manager.wantsHoverTile(),
			"a plugin that has not asked lately costs the renderer nothing");

		ctx.getHoverTileX();
		check(manager.wantsHoverTile(), "reading it is what asks for the next answer");

		// It keeps coming for a short while without being re-read, so a plugin reading it every
		// other frame does not flicker.
		for (int i = 0; i < 5; i++) {
			manager.onClientTick(i);
		}
		check(manager.wantsHoverTile(), "...and keeps coming for a few frames after");

		for (int i = 0; i < 40; i++) {
			manager.onClientTick(i);
		}
		check(!manager.wantsHoverTile(), "...then stops, so a plugin cannot leak the cost");

		manager.setEnabled(entry, false);
		ctx.getHoverTileX();
		check(!manager.wantsHoverTile(),
			"with nothing running at all, nothing is asked for whatever was read");
	}

	// ---------------------------------------------------------------- 4

	static void highlightTests() {
		check("Chop down Tree".equals(MouseHighlightPlugin.strip("Chop down <col=00ffff>Tree")),
			"a menu option's colour tags are stripped: "
				+ MouseHighlightPlugin.strip("Chop down <col=00ffff>Tree"));
		check("Attack Goblin  (level-2)".equals(
			MouseHighlightPlugin.strip("Attack <col=ffff00>Goblin<col=ff0000>  (level-2)")),
			"...all of them, not just the first: "
				+ MouseHighlightPlugin.strip("Attack <col=ffff00>Goblin<col=ff0000>  (level-2)"));
		check("Walk here".equals(MouseHighlightPlugin.strip("Walk here")),
			"an option with no tags is left alone");
		check("".equals(MouseHighlightPlugin.strip(null)), "null is empty, not a crash");
		// An unclosed tag is not a tag. Dropping the rest would lose the name entirely.
		check("Use <col=00ffff".equals(MouseHighlightPlugin.strip("Use <col=00ffff")),
			"an unclosed tag is not a tag, and what follows is kept whole: \""
				+ MouseHighlightPlugin.strip("Use <col=00ffff") + "\"");

		check(MouseHighlightPlugin.parseColour("FF0000") == 0xFF0000, "a hex colour parses");
		check(MouseHighlightPlugin.parseColour("#00FF00") == 0x00FF00, "...with or without a hash");
		check(MouseHighlightPlugin.parseColour("yellow") == 0xFFFF00,
			"a word falls back to yellow rather than refusing to draw");
		check(MouseHighlightPlugin.parseColour("") == 0xFFFF00, "so does an empty box");
		check(MouseHighlightPlugin.parseColour(null) == 0xFFFF00, "and so does null");
		check(MouseHighlightPlugin.parseColour("GGGGGG") == 0xFFFF00,
			"and six characters that are not hex");

		// On screen, through the real overlay.
		PluginManager.Entry entry = entry("mouse-highlight");
		manager.setEnabled(entry, true);
		client.mouseX = 100 - (client.viewportMouseX() - client.mouseX);
		client.mouseY = 60 - (client.viewportMouseY() - client.mouseY);

		menu(new String[] { "Cancel", "Chop down <col=00ffff>Tree" }, new int[] { 1006, 3 });
		check(drawn("Chop down Tree"), "the left-click option is drawn by the cursor: " + texts());

		// Everything that is not something is walk-here, so drawing it would mean a label
		// following the cursor across every empty tile.
		menu(new String[] { "Cancel", "Walk here" }, new int[] { 1006, Client.WALK_HERE_ACTION });
		check(!drawn("Walk here"), "...but not for Walk here, which is everywhere: " + texts());

		menu(new String[] { "Cancel" }, new int[] { 1006 });
		check(texts().length() <= 2, "...and nothing at all with an empty menu: " + texts());

		// KEPT INSIDE THE VIEWPORT. The label normally sits to the right of the cursor, which
		// puts it off the edge exactly when the cursor is near something at the edge of the
		// screen - which in fixed mode is most of the interesting things.
		menu(new String[] { "Cancel", "Chop down Tree" }, new int[] { 1006, 3 });
		client.mouseX = (W - 6) - (client.viewportMouseX() - client.mouseX);
		drawn("Chop down Tree");
		Drawn atEdge = find("Chop down Tree");
		check(atEdge != null && atEdge.x + font.stringWid("Chop down Tree") <= W,
			"near the right edge the label flips to the left of the cursor (x="
				+ (atEdge == null ? -1 : atEdge.x) + ", viewport " + W + ")");
		client.mouseX = 100 - (client.viewportMouseX() - client.mouseX);

		client.mouseY = (H - 4) - (client.viewportMouseY() - client.mouseY);
		drawn("Chop down Tree");
		Drawn atBottom = find("Chop down Tree");
		check(atBottom != null && atBottom.y <= H,
			"...and near the bottom it flips above (y=" + (atBottom == null ? -1 : atBottom.y)
				+ ", viewport " + H + ")");
		client.mouseY = 60 - (client.viewportMouseY() - client.mouseY);

		// Off the game view: nothing, rather than a label stranded at the last position.
		menu(new String[] { "Cancel", "Chop down Tree" }, new int[] { 1006, 3 });
		client.mouseX = -100;
		client.mouseY = -100;
		check(!drawn("Chop down Tree"), "with the cursor off the viewport, nothing is drawn");
		manager.setEnabled(entry, false);
	}

	// ---------------------------------------------------------------- 5

	static void tileTests() {
		PluginManager.Entry entry = entry("tile-indicators");
		TileIndicatorsPlugin plugin = plugin(entry);
		if (plugin == null) {
			return;
		}
		// A line is drawn as a run of single pixels, and a bad projection must not spin in it.
		java.util.Arrays.fill(pixels, 0);
		OverlayGraphics g = graphics();
		TileIndicatorsPlugin.line(g, 10, 10, 40, 30, 0x00FF00);
		check(pixels[10 * W + 10] == 0x00FF00 && pixels[30 * W + 40] == 0x00FF00,
			"a line reaches both of its ends");
		int painted = 0;
		for (int i = 0; i < pixels.length; i++) {
			if (pixels[i] == 0x00FF00) {
				painted++;
			}
		}
		check(painted >= 30 && painted <= 34,
			"...and is as long as the longer of its two spans (" + painted + ")");

		java.util.Arrays.fill(pixels, 0);
		TileIndicatorsPlugin.line(g, 20, 20, 20, 20, 0x00FF00);
		check(pixels[20 * W + 20] == 0x00FF00, "a line of no length is one pixel, not a hang");

		// A projection that came back absurd. Without a cap this walks millions of pixels every
		// frame inside the render loop, and the client stops drawing rather than crashing -
		// which is the hardest kind of fault to trace back to its cause.
		//
		// Counted rather than timed. Pixels outside the buffer are clipped so cheaply that ten
		// million of them finish in a twentieth of a second, so a stopwatch cannot tell the
		// capped version from the uncapped one at any length a test can afford to wait for.
		int steps = TileIndicatorsPlugin.line(g, 0, 0, 10000000, 9000000, 0x00FF00);
		check(steps <= TileIndicatorsPlugin.MAX_STEPS,
			"a line to an absurd coordinate stops at the cap rather than walking it all ("
				+ steps + " of at most " + TileIndicatorsPlugin.MAX_STEPS + ")");
		check(TileIndicatorsPlugin.MAX_STEPS <= 8192,
			"...and the cap is past any real viewport but nowhere near a million ("
				+ TileIndicatorsPlugin.MAX_STEPS + ")");
		check(TileIndicatorsPlugin.line(g, 5, 5, 9, 5, 0x00FF00) == 5,
			"an ordinary line reports the pixels it actually drew ("
				+ TileIndicatorsPlugin.line(g, 5, 5, 9, 5, 0x00FF00) + ")");
	}

	// ---------------------------------------------------------------- the plumbing

	/** Puts a menu in place, as the client's own option building does. */
	static void menu(String[] options, int[] actions) {
		for (int i = 0; i < options.length; i++) {
			client.menuOption[i] = options[i];
			client.menuAction[i] = actions[i];
		}
		client.menuSize = options.length;
	}

	/** Renders a frame and says whether that text was drawn. */
	static boolean drawn(String text) {
		font.rows.clear();
		manager.renderOverlays(W, H, Overlay.LAYER_SCREEN);
		return find(text) != null;
	}

	/** The recorded draw of that text from the last frame, or null. */
	static Drawn find(String text) {
		for (int i = 0; i < font.rows.size(); i++) {
			if (text.equals(font.rows.get(i).text)) {
				return font.rows.get(i);
			}
		}
		return null;
	}

	static String texts() {
		font.rows.clear();
		manager.renderOverlays(W, H, Overlay.LAYER_SCREEN);
		StringBuilder out = new StringBuilder("[");
		for (int i = 0; i < font.rows.size(); i++) {
			out.append(i > 0 ? ", " : "").append(font.rows.get(i).text);
		}
		return out.append(']').toString();
	}

	static OverlayGraphics graphics() {
		try {
			java.lang.reflect.Field field = PluginManager.class.getDeclaredField("graphics");
			field.setAccessible(true);
			OverlayGraphics g = (OverlayGraphics) field.get(manager);
			java.lang.reflect.Method reset = OverlayGraphics.class.getDeclaredMethod("reset",
				int.class, int.class, jagex2.client.plugin.InteractiveRegions.class,
				jagex2.client.plugin.Plugin.class, int.class, int.class);
			reset.setAccessible(true);
			reset.invoke(g, Integer.valueOf(W), Integer.valueOf(H), null, null,
				Integer.valueOf(0), Integer.valueOf(0));
			return g;
		} catch (Throwable error) {
			throw new IllegalStateException("cannot reach the overlay graphics: " + error);
		}
	}

	static jagex2.client.plugin.PluginContext context() {
		try {
			java.lang.reflect.Field field = PluginManager.class.getDeclaredField("ctx");
			field.setAccessible(true);
			return (jagex2.client.plugin.PluginContext) field.get(manager);
		} catch (Throwable error) {
			throw new IllegalStateException("cannot reach the context: " + error);
		}
	}

	static TileIndicatorsPlugin plugin(PluginManager.Entry entry) {
		try {
			java.lang.reflect.Field field = PluginManager.Entry.class.getDeclaredField("plugin");
			field.setAccessible(true);
			return (TileIndicatorsPlugin) field.get(entry);
		} catch (Throwable error) {
			check(false, "cannot reach the Tile indicators plugin (" + error + ")");
			return null;
		}
	}

	static PluginManager.Entry entry(String key) {
		for (PluginManager.Entry e : manager.getPlugins()) {
			if (e.key.equals(key)) {
				return e;
			}
		}
		return null;
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

	/** One string the font was asked to draw, and where. */
	static final class Drawn {

		final String text;
		final int x;
		final int y;

		Drawn(String text, int x, int y) {
			this.text = text;
			this.x = x;
			this.y = y;
		}
	}

	/** A font that records what it was asked to draw, and where, instead of drawing it. */
	static final class RecordingFont extends PixFont {

		static final int ADVANCE = 4;

		final java.util.List<Drawn> rows = new java.util.ArrayList<Drawn>();

		RecordingFont() {
			this.height = 12;
			java.util.Arrays.fill(this.charAdvance, ADVANCE);
		}

		public int stringWid(String text) {
			return text == null ? 0 : text.length() * ADVANCE;
		}

		public int stringWidTag(String text) {
			return this.stringWid(text);
		}

		public void drawStringTag(int colour, int x, int y, boolean shadow, String text) {
			this.rows.add(new Drawn(text, x, y));
		}

		public void drawString(int x, int colour, int y, String text) {
			this.rows.add(new Drawn(text, x, y));
		}
	}
}
