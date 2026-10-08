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
import jagex2.client.plugin.PluginConfig;
import jagex2.client.plugin.PluginManager;
import jagex2.dash3d.ClientPlayer;
import jagex2.dash3d.World3D;
import jagex2.graphics.Pix2D;
import jagex2.graphics.Pix3D;
import jagex2.graphics.PixFont;

public class MouseTest {

	static final int W = 512;
	static final int H = 334;

	static int fails;

	static Client client;
	static PluginManager manager;
	static RecordingFont font;
	static RecordingFont small;
	static RecordingFont bold;
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
		java.util.List<Drawn> shared = new java.util.ArrayList<Drawn>();
		small = new RecordingFont(shared);
		font = new RecordingFont(shared);
		bold = new RecordingFont(shared);
		manager = new PluginManager(client, small, font, bold);
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
		System.out.println("4b. the three text sizes, shared");
		fontChoiceTests();
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

		check(PluginConfig.parseColour("FF0000") == 0xFF0000, "a hex colour parses");
		check(PluginConfig.parseColour("#00FF00") == 0x00FF00, "...with or without a hash");
		check(PluginConfig.parseColour("yellow") == 0xFFFF00,
			"a word falls back to yellow rather than refusing to draw");
		check(PluginConfig.parseColour("") == 0xFFFF00, "so does an empty box");
		check(PluginConfig.parseColour(null) == 0xFFFF00, "and so does null");
		check(PluginConfig.parseColour("GGGGGG") == 0xFFFF00,
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
		client.mouseX = 100 - (client.viewportMouseX() - client.mouseX);
		client.mouseY = 60 - (client.viewportMouseY() - client.mouseY);

		// ---- THE SIX NEW SETTINGS, starting with what they are out of the box: the defaults
		// are the values that were hardcoded, so a player who upgrades sees what they saw.
		check(setting(entry, "colour").stringValue().equals("FFFF00"), "the text starts yellow");
		check(setting(entry, "font").stringValue().equals(OverlayGraphics.FONT_CHOICE_NORMAL),
			"at plain 12");
		check(!setting(entry, "textOutline").booleanValue(), "with no outline");
		check(setting(entry, "boxed").booleanValue(), "a box behind it");
		check(setting(entry, "backdropColour").stringValue()
				.equals(MouseHighlightPlugin.DEFAULT_BACKDROP),
			"...in the black it always was");
		check(setting(entry, "backdropOpacity").intValue()
				== MouseHighlightPlugin.DEFAULT_BACKDROP_ALPHA,
			"...at the opacity it always was (" + setting(entry, "backdropOpacity").intValue() + ")");
		check(setting(entry, "borderColour").stringValue()
				.equals(MouseHighlightPlugin.DEFAULT_BORDER),
			"...with the border it always had");
		check(!setting(entry, "showWalkHere").booleanValue(),
			"and Walk here still off, which is the behaviour this shipped with");

		check(setting(entry, "backdropColour").isColour()
				&& setting(entry, "borderColour").isColour(),
			"both box colours are edited as colours");
		check(setting(entry, "backdropOpacity").isInt(), "the opacity is a number");
		check(setting(entry, "font").choices().length == OverlayGraphics.FONT_CHOICES.length,
			"the size drop-down offers the three sizes OverlayGraphics knows");
		for (int i = 0; i < OverlayGraphics.FONT_CHOICES.length; i++) {
			check(hasChoice(entry, "font", OverlayGraphics.FONT_CHOICES[i]),
				"...including \"" + OverlayGraphics.FONT_CHOICES[i] + "\"");
		}

		// ---- THE TEXT COLOUR, which nothing checked before: Drawn records it now.
		menu(new String[] { "Cancel", "Chop down Tree" }, new int[] { 1006, 3 });
		drawn("Chop down Tree");
		check(find("Chop down Tree").colour == 0xFFFF00, "the label is drawn in yellow");
		set(entry, "colour", "40FF40");
		drawn("Chop down Tree");
		check(find("Chop down Tree").colour == 0x40FF40, "...and in a colour a player picks");
		set(entry, "colour", "FFFF00");

		// ---- WALK HERE, which was a rule and is a setting with the same default.
		menu(new String[] { "Cancel", "Walk here" }, new int[] { 1006, Client.WALK_HERE_ACTION });
		check(!drawn("Walk here"), "Walk here is still not drawn by default");
		set(entry, "showWalkHere", "true");
		check(drawn("Walk here"), "...and is when a player asks for it");
		set(entry, "showWalkHere", "false");
		check(!drawn("Walk here"), "...and off again");

		// ---- WHILE A MENU IS OPEN, nothing. Not a setting: the label says what a LEFT click
		// would do, and with a menu open that is a click the player is not about to make.
		menu(new String[] { "Cancel", "Chop down Tree" }, new int[] { 1006, 3 });
		check(drawn("Chop down Tree"), "with no menu open the label is drawn");
		client.menuVisible = true;
		check(!drawn("Chop down Tree"), "with a menu open it stands aside");
		client.menuVisible = false;
		check(drawn("Chop down Tree"), "...and comes back when the menu closes");

		// ---- THE SIZE, which is a different font object rather than a different number.
		frame();
		check(font.calls > 0 && bold.calls == 0 && small.calls == 0,
			"Normal draws in the normal font and no other");
		set(entry, "font", OverlayGraphics.FONT_CHOICE_BOLD);
		frame();
		check(bold.calls > 0 && font.calls == 0, "Bold draws in the bold font");
		set(entry, "font", OverlayGraphics.FONT_CHOICE_SMALL);
		frame();
		check(small.calls > 0 && font.calls == 0 && bold.calls == 0, "Small in the small one");
		set(entry, "font", OverlayGraphics.FONT_CHOICE_NORMAL);

		// ---- THE OUTLINE: four flat passes plus the coloured one.
		frame();
		int plain = font.rows.size();
		set(entry, "textOutline", "true");
		frame();
		check(font.rows.size() == plain + 4,
			"the outline is four more draws than a plain label (" + plain + " -> "
				+ font.rows.size() + ")");
		int black = 0;
		for (int i = 0; i < font.rows.size(); i++) {
			if (font.rows.get(i).colour == 0) {
				black++;
			}
		}
		check(black == 4, "...one each way, with the colour on top");
		set(entry, "textOutline", "false");

		// ---- THE BOX, as pixels: it is a fill and a rectangle, so the font cannot see it.
		clearPixels();
		set(entry, "backdropColour", "FF00FF");
		set(entry, "backdropOpacity", "255");
		set(entry, "borderColour", "00FF00");
		frame();
		// NEAR, not exact: the fill is translucent and Pix2D blends with a >> 8, so FF at alpha
		// 255 lands on FE. The border is a solid draw and is matched exactly.
		check(countPixelsNear(0xFF00FF, 4) > 0, "the box is filled in the colour a player picked");
		check(countPixels(0x00FF00) > 0, "...and bordered in theirs");
		clearPixels();
		set(entry, "boxed", "false");
		frame();
		check(countPixelsNear(0xFF00FF, 4) == 0 && countPixels(0x00FF00) == 0,
			"turning the box off leaves neither");
		set(entry, "boxed", "true");

		// AN OPACITY OF 0 IS A BOX NOBODY CAN SEE, which is a legitimate way to lose it: the
		// fill goes and the border stays, so the label still has an edge.
		clearPixels();
		set(entry, "backdropOpacity", "0");
		frame();
		check(countPixelsNear(0xFF00FF, 4) == 0, "at opacity 0 the fill is gone");
		check(countPixels(0x00FF00) > 0, "...and the border is not, so the label keeps an edge");
		set(entry, "backdropColour", MouseHighlightPlugin.DEFAULT_BACKDROP);
		set(entry, "borderColour", MouseHighlightPlugin.DEFAULT_BORDER);
		set(entry, "backdropOpacity",
			Integer.toString(MouseHighlightPlugin.DEFAULT_BACKDROP_ALPHA));

		// ---- the clamp, as a rule
		check(MouseHighlightPlugin.alphaFor(150) == 150, "an opacity in range is kept");
		check(MouseHighlightPlugin.alphaFor(-10) == 0, "a negative is clear, not a wrap");
		check(MouseHighlightPlugin.alphaFor(9999) == 255, "and anything past opaque is opaque");
		check(MouseHighlightPlugin.MIN_ALPHA == 0 && MouseHighlightPlugin.MAX_ALPHA == 255,
			"between 0 and 255, as numbers rather than as their own names");

		manager.setEnabled(entry, false);
	}

	// ---------------------------------------------------------------- 4b

	/**
	 * The three text sizes, which live on OverlayGraphics now rather than in each plugin.
	 *
	 * Two plugins had their own copy of these three words and their own parse of them. A
	 * drop-down whose entries no branch matches is a control that silently does nothing, and the
	 * compiler cannot see it because both sides are strings - so the list and the parse being one
	 * thing is the only structural defence there is.
	 */
	static void fontChoiceTests() {
		// THE EXACT LEVEL LIVES IN THE NEWEST SUITE, which is this one: isMenuOpen and fontFor
		// are what level 7 added. Every older suite asks only that its own level is supported,
		// so this pin is the single place that has to move when the next addition lands.
		check(jagex2.client.plugin.PluginApi.LEVEL == 7,
			"this client is API level 7 (" + jagex2.client.plugin.PluginApi.LEVEL + ")");
		check(jagex2.client.plugin.PluginApi.supports(7), "a plugin asking for 7 runs here");
		check(!jagex2.client.plugin.PluginApi.supports(8), "one asking for 8 does not");
		check(read("src/main/java/jagex2/client/plugin/builtin/MouseHighlightPlugin.java")
				.indexOf("apiLevel = 7") >= 0,
			"and Mouse highlight declares the level it needs");

		check(OverlayGraphics.FONT_CHOICES.length == 3, "three sizes are offered");
		check(OverlayGraphics.fontFor(OverlayGraphics.FONT_CHOICE_BOLD)
				== OverlayGraphics.FONT_BOLD, "Bold is the bold font");
		check(OverlayGraphics.fontFor(OverlayGraphics.FONT_CHOICE_SMALL)
				== OverlayGraphics.FONT_SMALL, "Small is the small one");
		check(OverlayGraphics.fontFor(OverlayGraphics.FONT_CHOICE_NORMAL)
				== OverlayGraphics.FONT_NORMAL, "Normal is plain 12");

		// EVERY OFFERED CHOICE REACHES A FONT OF ITS OWN. Three distinct answers for three
		// choices is the thing that catches an entry no branch matches - it would fall through
		// to the Normal fallback and look exactly like a working one.
		boolean[] seen = new boolean[3];
		for (int i = 0; i < OverlayGraphics.FONT_CHOICES.length; i++) {
			int which = OverlayGraphics.fontFor(OverlayGraphics.FONT_CHOICES[i]);
			check(which >= 0 && which <= 2, OverlayGraphics.FONT_CHOICES[i] + " is a real font");
			seen[which] = true;
		}
		check(seen[0] && seen[1] && seen[2],
			"the three choices reach three DIFFERENT fonts, so none is silently the fallback");

		check(OverlayGraphics.fontFor("Huge") == OverlayGraphics.FONT_NORMAL,
			"an unknown size falls back rather than throwing out of a render loop");
		boolean threw = false;
		boolean fellBack = false;
		try {
			fellBack = OverlayGraphics.fontFor(null) == OverlayGraphics.FONT_NORMAL;
		} catch (Throwable error) {
			threw = true;
		}
		check(!threw && fellBack, "...and so does none at all" + (threw ? " (it threw)" : ""));

		// And the plugins that used to have their own copy now agree with it by construction.
		check(XpDropsPlugin.FONT_PLAIN.equals(OverlayGraphics.FONT_CHOICE_NORMAL)
				&& XpDropsPlugin.FONT_BIG.equals(OverlayGraphics.FONT_CHOICE_BOLD)
				&& XpDropsPlugin.FONT_TINY.equals(OverlayGraphics.FONT_CHOICE_SMALL),
			"XP drops' names are aliases of these, not a second list");
		for (int i = 0; i < OverlayGraphics.FONT_CHOICES.length; i++) {
			check(XpDropsPlugin.fontFor(OverlayGraphics.FONT_CHOICES[i])
					== OverlayGraphics.fontFor(OverlayGraphics.FONT_CHOICES[i]),
				"...and its parse is this parse for \"" + OverlayGraphics.FONT_CHOICES[i] + "\"");
		}
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

		// ---- the four new settings, and what they are out of the box
		check(setting(entry, "borderWidth").intValue() == TileIndicatorsPlugin.MIN_BORDER,
			"the outline starts one pixel thick");
		check(!setting(entry, "hoverFill").booleanValue()
				&& !setting(entry, "currentFill").booleanValue(),
			"neither tile is filled, which is the behaviour this shipped with");
		check(setting(entry, "fillOpacity").intValue() == TileIndicatorsPlugin.DEFAULT_FILL,
			"and a fill, once asked for, is part-transparent rather than solid ("
				+ setting(entry, "fillOpacity").intValue() + ")");
		check(setting(entry, "borderWidth").isInt() && setting(entry, "fillOpacity").isInt(),
			"both are numbers a player types");

		check(TileIndicatorsPlugin.borderFor(3) == 3, "a thickness in range is kept");
		check(TileIndicatorsPlugin.borderFor(0) == 1 && TileIndicatorsPlugin.borderFor(-9) == 1,
			"a zero-pixel border is no outline at all, so one is the floor");
		check(TileIndicatorsPlugin.borderFor(900) == TileIndicatorsPlugin.MAX_BORDER,
			"and a very thick one would swallow a distant tile whole");
		check(TileIndicatorsPlugin.MIN_BORDER == 1 && TileIndicatorsPlugin.MAX_BORDER == 5,
			"between one pixel and five, as numbers rather than as their own names");
		check(TileIndicatorsPlugin.fillFor(70) == 70, "an opacity in range is kept");
		check(TileIndicatorsPlugin.fillFor(-5) == 0, "a negative is clear, not a wrap");
		check(TileIndicatorsPlugin.fillFor(9999) == 255, "and anything past opaque is opaque");
		check(TileIndicatorsPlugin.MIN_FILL == 0 && TileIndicatorsPlugin.MAX_FILL == 255
				&& TileIndicatorsPlugin.DEFAULT_FILL == 70,
			"0 to 255, starting at 70");

		// ---- THE SPAN ARITHMETIC, which decides a fill's shape.
		//
		// The most valuable pure checks in this plugin: a wrong span is not a slightly wrong
		// tile, it is a bar of colour across the screen. A quadrilateral is walked one screen
		// row at a time, and each of its four edges is asked where it crosses that row.
		int[] xs = { 0, 10, 10, 0 };
		int[] ys = { 0, 0, 10, 10 };
		check(TileIndicatorsPlugin.spanLeft(xs, ys, 5) == 0
				&& TileIndicatorsPlugin.spanRight(xs, ys, 5) == 10,
			"a square's middle row spans its full width");
		check(TileIndicatorsPlugin.spanLeft(xs, ys, 0) == 0
				&& TileIndicatorsPlugin.spanRight(xs, ys, 0) == 10,
			"ITS TOP ROW TOO: a horizontal edge lying on the row contributes BOTH its ends, or "
				+ "the top and bottom rows of every tile would be one pixel wide");
		check(TileIndicatorsPlugin.spanLeft(xs, ys, 10) == 0
				&& TileIndicatorsPlugin.spanRight(xs, ys, 10) == 10,
			"...and its bottom row");
		// A row the quad does not reach must read as "draw nothing" rather than as a span. Left
		// stays at MAX and right at MIN, so left > right says it without a second return value.
		check(TileIndicatorsPlugin.spanLeft(xs, ys, 11)
				> TileIndicatorsPlugin.spanRight(xs, ys, 11),
			"a row below the tile is empty rather than a span");
		check(TileIndicatorsPlugin.spanLeft(xs, ys, -1)
				> TileIndicatorsPlugin.spanRight(xs, ys, -1), "...and one above it");

		// A DIAMOND, which is what a tile actually looks like from the game's camera. Its top
		// row is a single pixel and its middle is the full width - a square would pass checks
		// that this fails.
		int[] dx = { 5, 10, 5, 0 };
		int[] dy = { 0, 5, 10, 5 };
		check(TileIndicatorsPlugin.spanLeft(dx, dy, 0) == 5
				&& TileIndicatorsPlugin.spanRight(dx, dy, 0) == 5,
			"a diamond's top row is its vertex, one pixel wide");
		check(TileIndicatorsPlugin.spanLeft(dx, dy, 5) == 0
				&& TileIndicatorsPlugin.spanRight(dx, dy, 5) == 10,
			"...and its middle row is the full width");
		check(TileIndicatorsPlugin.spanLeft(dx, dy, 2) > 0
				&& TileIndicatorsPlugin.spanRight(dx, dy, 2) < 10,
			"...and a row between them is narrower than both");
		check(TileIndicatorsPlugin.spanLeft(dx, dy, 2)
				< TileIndicatorsPlugin.spanRight(dx, dy, 2), "...but not empty");

        // A TILE SEEN EXACTLY EDGE-ON, every corner on the same row. Every edge is horizontal,
        // which is a divide by zero in the obvious implementation of this - in a render loop.
		int[] fx = { 2, 9, 9, 2 };
		int[] fy = { 5, 5, 5, 5 };
		boolean threw = false;
		int flatLeft = 0;
		int flatRight = 0;
		try {
			flatLeft = TileIndicatorsPlugin.spanLeft(fx, fy, 5);
			flatRight = TileIndicatorsPlugin.spanRight(fx, fy, 5);
		} catch (Throwable error) {
			threw = true;
		}
		check(!threw, "a tile seen edge-on does not divide by zero" + (threw ? " (it threw)" : ""));
		check(!threw && flatLeft == 2 && flatRight == 9,
			"...and spans the width it has (" + flatLeft + " to " + flatRight + ")");
		check(TileIndicatorsPlugin.spanLeft(fx, fy, 6)
				> TileIndicatorsPlugin.spanRight(fx, fy, 6), "...on that row and no other");

		// ---- ON SCREEN. The fill and the thickness, as pixels, through the real overlay.
		//
		// A SCENE HAS TO EXIST FIRST. Everything above this point in the suite works on the
		// cursor and on pure arithmetic, so nothing here ever needed a heightmap or a camera -
		// and without them every corner fails to project and the plugin correctly draws nothing,
		// which is indistinguishable from a broken fill. Set up the way GroundItemsTest does it:
		// a flat level and a camera placed so one named tile lands in the middle of the buffer.
		client.levelHeightmap = new int[4][105][105];
		client.levelTileFlags = new byte[4][104][104];
		client.currentLevel = 0;
		// LOOKING DOWN, not along. With a level camera the tile under the player is seen
		// edge-on: it projects to a quad one pixel tall, where the outline ring and the fill
		// cover exactly the same pixels - so "the fill covers more" was false for a fill that
		// was working. A tile needs area on screen before anything can be said about filling it.
		// Pitch 256 is a eighth of the 2048-step table, so 45 degrees down, with the camera a
		// thousand units up and a thousand south of the tile. Worked out of projectFromGround
		// rather than guessed: that puts the tile's near edge on the centre row and its far edge
		// about thirty pixels above, which is a quad with real area to fill.
		client.cameraPitch = 256;
		client.cameraYaw = 0;
		client.cameraX = 64 * 128;
		client.cameraZ = 64 * 128 - 1000;
		client.cameraY = -1000;
		Client.localPlayer.field1157 = 64 * 128;
		Client.localPlayer.field1158 = 64 * 128;
		Pix3D.zoom = 512;
		Pix3D.centerX = W / 2;
		Pix3D.centerY = H / 2;

		manager.setEnabled(entry, true);
		set(entry, "hover", "false");
		set(entry, "current", "true");
		set(entry, "currentColour", "00FFFF");
		set(entry, "currentFill", "false");
		set(entry, "borderWidth", "1");
		clearPixels();
		sceneFrame();
		int thinOutline = countPixels(0x00FFFF);
		check(thinOutline > 0, "your own tile is outlined (" + thinOutline + " pixels)");

		set(entry, "borderWidth", "3");
		clearPixels();
		sceneFrame();
		int thickOutline = countPixels(0x00FFFF);
		check(thickOutline > thinOutline,
			"a thicker border draws more (" + thinOutline + " -> " + thickOutline + ")");
		set(entry, "borderWidth", "1");

		// THE FILL, which is the new drawing. Near rather than exact: it is translucent.
		clearPixels();
		sceneFrame();
		int unfilled = countPainted();
		set(entry, "currentFill", "true");
		clearPixels();
		sceneFrame();
		int filled = countPainted();
		check(filled > unfilled,
			"filling the tile covers more of it than the outline alone ("
				+ unfilled + " -> " + filled + ")");
		check(countPixels(0x00FFFF) > 0,
			"...and the outline is still drawn ON TOP of its own fill rather than under it");

		// AN OPACITY OF 0 IS NO FILL, which is how a player turns one off without the switch.
		set(entry, "fillOpacity", "0");
		clearPixels();
		sceneFrame();
		check(countPainted() == unfilled,
			"at opacity 0 the fill draws nothing and the outline is all that is left");
		set(entry, "fillOpacity", Integer.toString(TileIndicatorsPlugin.DEFAULT_FILL));

		// And the switch itself.
		set(entry, "currentFill", "false");
		clearPixels();
		sceneFrame();
		check(countPainted() == unfilled, "turning the fill off leaves the outline");
		set(entry, "current", "false");
		clearPixels();
		sceneFrame();
		check(countPixels(0x00FFFF) == 0, "and turning the tile off leaves nothing");
		manager.setEnabled(entry, false);
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
		frame();
		return find(text) != null;
	}

	/** One frame, with the shared list and all three call counts cleared first. */
	static void frame() {
		layer(Overlay.LAYER_SCREEN);
	}

	/**
	 * One frame of the SCENE layer, which is where Tile indicators draws.
	 *
	 * Worth its own name rather than a parameter on frame(): a scene overlay rendered on the
	 * screen layer draws nothing at all, which looks exactly like a broken draw. Four checks
	 * reported a fill that was working perfectly before this existed.
	 */
	static void sceneFrame() {
		layer(Overlay.LAYER_SCENE);
	}

	static void layer(int which) {
		font.rows.clear();
		small.calls = 0;
		font.calls = 0;
		bold.calls = 0;
		manager.renderOverlays(W, H, which);
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
		frame();
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

	/** Writes a setting through the real config path, which is what the sidebar does. */
	static void set(PluginManager.Entry entry, String key, String value) {
		java.util.List<PluginConfig.Item> items = entry.getConfig().getItems();
		for (int i = 0; i < items.size(); i++) {
			if (items.get(i).key.equals(key)) {
				check(entry.getConfig().set(items.get(i), value),
					"setting " + entry.key + "." + key + " to \"" + value + "\" is accepted");
				return;
			}
		}
		check(false, entry.key + " has a setting called " + key);
	}

	static PluginConfig.Item setting(PluginManager.Entry entry, String key) {
		java.util.List<PluginConfig.Item> items = entry.getConfig().getItems();
		for (int i = 0; i < items.size(); i++) {
			if (items.get(i).key.equals(key)) {
				return items.get(i);
			}
		}
		return null;
	}

	static boolean hasChoice(PluginManager.Entry entry, String key, String choice) {
		PluginConfig.Item item = setting(entry, key);
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

	/**
	 * Pixels close to this colour, for a TRANSLUCENT fill.
	 *
	 * Exact matching is wrong for one: Pix2D blends with (channel * alpha) >> 8, so FF at alpha
	 * 255 comes out FE, and a check for the colour a player picked would fail on a fill that is
	 * working perfectly. The first version of this did exactly that. A solid draw - g.box, g.fill
	 * - is exact and uses countPixels below.
	 */
	static int countPixelsNear(int colour, int tolerance) {
		int wantR = colour >> 16 & 0xFF;
		int wantG = colour >> 8 & 0xFF;
		int wantB = colour & 0xFF;
		int n = 0;
		for (int i = 0; i < pixels.length; i++) {
			int p = pixels[i];
			if (Math.abs((p >> 16 & 0xFF) - wantR) <= tolerance
					&& Math.abs((p >> 8 & 0xFF) - wantG) <= tolerance
					&& Math.abs((p & 0xFF) - wantB) <= tolerance) {
				n++;
			}
		}
		return n;
	}

	/** Pixels of exactly this colour in the buffer, for a solid fill or a box. */
	static int countPixels(int colour) {
		int n = 0;
		for (int i = 0; i < pixels.length; i++) {
			if ((pixels[i] & 0xFFFFFF) == colour) {
				n++;
			}
		}
		return n;
	}

	/**
	 * Pixels that are not black, for "the fill covers more of the tile than the outline".
	 *
	 * AREA, NOT COLOUR. A translucent fill at the default opacity blends 00FFFF down to about
	 * 004545 on a black background, which no sane tolerance round the colour a player picked
	 * would count - the first version of this check looked for the colour and reported a working
	 * fill as drawing nothing. What a fill does is cover area; what the outline does is be
	 * exactly its colour. Each is measured by the thing it actually changes.
	 */
	static int countPainted() {
		int n = 0;
		for (int i = 0; i < pixels.length; i++) {
			if (pixels[i] != 0) {
				n++;
			}
		}
		return n;
	}

	static void clearPixels() {
		java.util.Arrays.fill(pixels, 0);
		Pix2D.bind(W, H, pixels);
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

		/** Recorded now: half of what the new settings change is which colour a label is. */
		final int colour;

		Drawn(String text, int x, int y, int colour) {
			this.text = text;
			this.x = x;
			this.y = y;
			this.colour = colour;
		}
	}

	/**
	 * A font that records what it was asked to draw, and where, instead of drawing it.
	 *
	 * THE THREE SIZES SHARE ONE LIST and keep their own call counts. One font object handed to
	 * the manager three times makes "which size did it draw in" unaskable, which is how a size
	 * setting can be ignored entirely with every check still green - XpDropsTest was built that
	 * way and the audit found exactly that. The list answers what was drawn; the counter answers
	 * by whom.
	 */
	static final class RecordingFont extends PixFont {

		static final int ADVANCE = 4;

		final java.util.List<Drawn> rows;

		/** Draws made through THIS size since the list was last cleared. */
		int calls;

		RecordingFont(java.util.List<Drawn> shared) {
			this.rows = shared;
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
			this.rows.add(new Drawn(text, x, y, colour));
			this.calls++;
		}

		public void drawString(int x, int colour, int y, String text) {
			this.rows.add(new Drawn(text, x, y, colour));
			this.calls++;
		}
	}
}
