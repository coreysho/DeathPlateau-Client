package jagex2.client;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

/**
 * The window: which of the three display modes, how big a resizable window was left, and how far
 * the scene is drawn.
 *
 * Kept in ~/.deathplateau/display.properties - the folder the launcher keeps the game in - rather
 * than beside QolSettings in the cache folder, because it is about this machine's screen and not
 * about how the game plays. Plain key=value text, so a player can edit or delete it by hand.
 *
 * Nothing here may throw: a missing or broken file is the fixed screen, as the client always was.
 */
public final class DisplaySettings {

	private static final String FILE_NAME = "display.properties";

	private static boolean loaded;
	private static int mode = Layout.FIXED;
	private static int width = 1024;
	private static int height = 768;
	private static boolean maximized;
	private static int drawDistance = 25;                    // World3D.MIN_DISTANCE, 377's own
	private static int panelScale = Layout.SCALE_FIXED;      // the HUD at 377's own pixel size
	private static int gameScale = 100;                      // the whole frame at its own size
	private static boolean smoothScale = true;               // bilinear rather than blocky
	private static boolean stretched;                        // the fixed screen fills the window
	private static boolean stretchAspect = true;             // ...keeping its shape
	private static boolean stretchInteger;                   // ...and by a whole number only
	private static boolean hideSidebar;                      // the open tab's panel folded away

	/**
	 * What the F9 row steps through: 377's own 25 tiles, then out to the edge of the 104x104 scene
	 * the server loads. 25 first, so nobody's frame rate changes until they ask for it.
	 */
	public static final int[] DRAW_DISTANCES = { 25, 35, 45, 52 };

	/**
	 * What the whole game can be stretched by, as a percentage. 100 is the client as it was: the
	 * frame is the window and nothing is scaled.
	 *
	 * 200 LAST AND WORTH REACHING FOR: at exactly double, one source pixel becomes four and the
	 * picture is as sharp as the original, whichever way it is smoothed. The fractions in between
	 * cannot be - something has to be invented between the pixels - so they are offered because
	 * the alternative is a game too small to read, not because they are free.
	 */
	public static final int[] GAME_SCALES = { 100, 125, 150, 200 };

	// ------------------------------------------------------------------ the browser's own screen

	/**
	 * HOW BIG THE SCREEN REALLY IS, WHEN THE PAGE KNOWS BETTER (2026-11-16).
	 *
	 * The game opens at 765x503 and ViewBox will not let the window go under Layout.MIN_W/MIN_H,
	 * which is right for a desktop: the panels are fixed lumps of 377's art and they overlap under
	 * that. A phone is smaller than that in at least one direction whichever way up it is held, so
	 * on a phone those two rules between them mean the window can never be the size of the screen
	 * it is on, and Client's scaled mode - which only turns on when the window IS smaller than the
	 * game - could never turn on at all.
	 *
	 * So rs2.cgi passes the display it made, and these let the window be that size. Nothing else
	 * sets them: a desktop client reads nothing, every number below is Layout's, and the minimum
	 * that keeps a dragged window from squeezing the panels is exactly as it was.
	 *
	 * Read once, here, rather than per call: a system property lookup is cheap but this is asked
	 * on every window resize.
	 */
	private static final int browserW = browserSize("lostcity.screenwidth");
	private static final int browserH = browserSize("lostcity.screenheight");

	/**
	 * One of the page's two sizes, or 0 for "not a browser, or a size not worth having". Anything
	 * under 200 is refused rather than honoured: a window that small is a mistake somewhere, and
	 * the game at the fixed size with its edges off the display beats a game four pixels tall.
	 */
	private static int browserSize(String name) {
		try {
			int v = Integer.parseInt(System.getProperty(name, "0"));
			return v >= 200 && v <= 8192 ? v : 0;
		} catch (Exception ignore) {
			return 0;
		}
	}

	/** The size the game opens at. */
	public static int startWidth() {
		return browserW > 0 ? browserW : Layout.FIXED_W;
	}

	public static int startHeight() {
		return browserH > 0 ? browserH : Layout.FIXED_H;
	}

	/**
	 * True when the page reported a screen the game does not fit on - a phone, in practice.
	 *
	 * Things that are worth their space on a desktop are not worth it here. The plugin sidebar is
	 * the first of them: it is beside the game, so on a 390-wide screen its icon rail alone takes
	 * an eighth of the width the game has left to be drawn in, and none of it can be used by a
	 * finger anyway. The in-canvas panel (F8) is the fallback and is already what runs when no
	 * sidebar was attached.
	 */
	public static boolean smallScreen() {
		return (browserW > 0 || browserH > 0)
			&& (startWidth() < Layout.MIN_W || startHeight() < Layout.MIN_H);
	}

	/** The smallest the window may be made. Never larger than the screen there is to put it on. */
	public static int minWidth() {
		return browserW > 0 && browserW < Layout.MIN_W ? browserW : Layout.MIN_W;
	}

	public static int minHeight() {
		return browserH > 0 && browserH < Layout.MIN_H ? browserH : Layout.MIN_H;
	}

	private DisplaySettings() {
	}

	private static File file() {
		return new File(new File(System.getProperty("user.home"), ".deathplateau"), FILE_NAME);
	}

	private static void load() {
		loaded = true;
		InputStream in = null;
		try {
			File f = file();
			if (!f.exists()) {
				return;
			}
			in = new FileInputStream(f);
			Properties p = new Properties();
			p.load(in);
			mode = modeFor(p.getProperty("mode", "fixed").trim());
			width = Math.max(Layout.MIN_W, Integer.parseInt(p.getProperty("width", "" + width).trim()));
			height = Math.max(Layout.MIN_H, Integer.parseInt(p.getProperty("height", "" + height).trim()));
			maximized = "true".equals(p.getProperty("maximized", "false").trim());
			drawDistance = clampDistance(Integer.parseInt(p.getProperty("drawdistance", "" + drawDistance).trim()));
			panelScale = clampScale(Integer.parseInt(p.getProperty("panelscale", "" + panelScale).trim()));
			gameScale = clampGameScale(Integer.parseInt(p.getProperty("gamescale", "" + gameScale).trim()));
			smoothScale = !"false".equals(p.getProperty("smoothscale", "true").trim());
			stretched = "true".equals(p.getProperty("stretched", "false").trim());
			stretchAspect = !"false".equals(p.getProperty("stretchaspect", "true").trim());
			stretchInteger = "true".equals(p.getProperty("stretchinteger", "false").trim());
			hideSidebar = "true".equals(p.getProperty("hidesidebar", "false").trim());
		} catch (Exception ignored) {
		} finally {
			try {
				if (in != null) {
					in.close();
				}
			} catch (Exception ignored) {
			}
		}
	}

	/**
	 * The file says fixed, classic or modern. It said "resizable" before there were two resizable
	 * layouts, and a file that still does means the classic one - which is the layout that shipped
	 * under that name, so nobody's window changes under them.
	 */
	private static int modeFor(String name) {
		if ("modern".equals(name)) {
			return Layout.MODERN;
		}
		if ("classic".equals(name) || "resizable".equals(name)) {
			return Layout.CLASSIC;
		}
		return Layout.FIXED;
	}

	private static String nameFor(int mode) {
		return mode == Layout.MODERN ? "modern" : mode == Layout.CLASSIC ? "classic" : "fixed";
	}

	public static synchronized int mode() {
		if (!loaded) {
			load();
		}
		return mode;
	}

	public static synchronized boolean resizable() {
		return mode() != Layout.FIXED;
	}

	public static synchronized int width() {
		if (!loaded) {
			load();
		}
		return width;
	}

	public static synchronized int height() {
		if (!loaded) {
			load();
		}
		return height;
	}

	public static synchronized boolean maximized() {
		if (!loaded) {
			load();
		}
		return maximized;
	}

	public static synchronized int drawDistance() {
		if (!loaded) {
			load();
		}
		return drawDistance;
	}

	public static synchronized void setDrawDistance(int tiles) {
		if (!loaded) {
			load();
		}
		tiles = clampDistance(tiles);
		if (drawDistance != tiles) {
			drawDistance = tiles;
			save();
		}
	}

	/** The next value in DRAW_DISTANCES after this one, wrapping - what clicking the F9 row does. */
	public static int nextDistance(int tiles) {
		for (int i = 0; i < DRAW_DISTANCES.length; i++) {
			if (DRAW_DISTANCES[i] == tiles) {
				return DRAW_DISTANCES[(i + 1) % DRAW_DISTANCES.length];
			}
		}
		return DRAW_DISTANCES[0];
	}

	/** A value from the file that is not one of the steps is rounded down to the nearest one. */
	private static int clampDistance(int tiles) {
		int best = DRAW_DISTANCES[0];
		for (int i = 0; i < DRAW_DISTANCES.length; i++) {
			if (DRAW_DISTANCES[i] <= tiles) {
				best = DRAW_DISTANCES[i];
			}
		}
		return best;
	}

	public static synchronized void setMode(int next) {
		if (!loaded) {
			load();
		}
		if (next < Layout.FIXED || next >= Layout.MODES) {
			next = Layout.FIXED;
		}
		if (mode != next) {
			mode = next;
			save();
		}
	}

	public static synchronized void setResizable(boolean on) {
		setMode(on ? Layout.CLASSIC : Layout.FIXED);
	}

	/** The next mode after this one, wrapping: what clicking the F9 row does. */
	public static int nextMode(int mode) {
		return (mode + 1) % Layout.MODES;
	}

	/**
	 * How big the chatbox, minimap and side tabs are drawn, as a percentage of 377's own size.
	 *
	 * What was ASKED for, which is not always what is used: a window too small for it gets the
	 * largest step that fits (Layout.fit). Keeping the request rather than the result is what lets
	 * one account carry 200% between a 4K desktop and a laptop that can only show 150%, instead of
	 * the number ratcheting down to the smallest screen it was ever opened on.
	 */
	public static synchronized int panelScale() {
		if (!loaded) {
			load();
		}
		return panelScale;
	}

	public static synchronized void setPanelScale(int percent) {
		if (!loaded) {
			load();
		}
		percent = clampScale(percent);
		if (panelScale != percent) {
			panelScale = percent;
			save();
		}
	}

	/** A value that is not one of Layout.SCALES is rounded down to the nearest one. */
	private static int clampScale(int percent) {
		int best = Layout.SCALES[0];
		for (int i = 0; i < Layout.SCALES.length; i++) {
			if (Layout.SCALES[i] <= percent) {
				best = Layout.SCALES[i];
			}
		}
		return best;
	}

	/**
	 * How much the whole frame is stretched by, as a percentage - the world, the panels and the
	 * text together. 100 is off, and off is the default.
	 *
	 * Unlike panelScale this has no fitting to do: the layout is built for the smaller frame, so
	 * there is no window too small for it and nothing that can overlap. What it costs is sharpness,
	 * and smoothScale decides which kind.
	 */
	public static synchronized int gameScale() {
		if (!loaded) {
			load();
		}
		return gameScale;
	}

	public static synchronized void setGameScale(int percent) {
		if (!loaded) {
			load();
		}
		percent = clampGameScale(percent);
		if (gameScale != percent) {
			gameScale = percent;
			save();
		}
	}

	/** Bilinear rather than nearest neighbour when anything is stretched. */
	public static synchronized boolean smoothScale() {
		if (!loaded) {
			load();
		}
		return smoothScale;
	}

	public static synchronized void setSmoothScale(boolean on) {
		if (!loaded) {
			load();
		}
		if (smoothScale != on) {
			smoothScale = on;
			save();
		}
	}

	/**
	 * The fixed screen drawn to fill the window rather than sitting in the middle of it.
	 *
	 * Only the fixed layout: the resizable ones already use the whole window, and what they want
	 * instead is gameScale. Off by default, because the fixed screen at its own size is what this
	 * client has always shown and what its art was drawn for.
	 */
	public static synchronized boolean stretched() {
		if (!loaded) {
			load();
		}
		return stretched;
	}

	public static synchronized void setStretched(boolean on) {
		if (!loaded) {
			load();
		}
		if (stretched != on) {
			stretched = on;
			save();
		}
	}

	/** Grow until one side runs out and leave a border, rather than filling both and distorting. */
	public static synchronized boolean stretchAspect() {
		if (!loaded) {
			load();
		}
		return stretchAspect;
	}

	public static synchronized void setStretchAspect(boolean on) {
		if (!loaded) {
			load();
		}
		if (stretchAspect != on) {
			stretchAspect = on;
			save();
		}
	}

	/**
	 * Whole multiples only - double, triple - so one pixel becomes exactly four or nine.
	 *
	 * The answer to uneven text: at any other factor some rows of pixels are doubled and the ones
	 * between them are not, and 2006 lettering is one pixel wide. The cost is the fractional part,
	 * which becomes a border.
	 */
	public static synchronized boolean stretchInteger() {
		if (!loaded) {
			load();
		}
		return stretchInteger;
	}

	public static synchronized void setStretchInteger(boolean on) {
		if (!loaded) {
			load();
		}
		if (stretchInteger != on) {
			stretchInteger = on;
			save();
		}
	}

	/**
	 * The open tab's panel is folded away, leaving its two rows of tabs - what clicking the lit tab
	 * does in the modern layout. Remembered between sessions, as Old School remembers it.
	 */
	public static synchronized boolean hideSidebar() {
		if (!loaded) {
			load();
		}
		return hideSidebar;
	}

	public static synchronized void setHideSidebar(boolean on) {
		if (!loaded) {
			load();
		}
		if (hideSidebar != on) {
			hideSidebar = on;
			save();
		}
	}

	private static int clampGameScale(int percent) {
		int best = GAME_SCALES[0];
		for (int i = 0; i < GAME_SCALES.length; i++) {
			if (GAME_SCALES[i] <= percent) {
				best = GAME_SCALES[i];
			}
		}
		return best;
	}

	/** The size a resizable window was left at, to open at next time. Saved only when it changes. */
	public static synchronized void setWindow(int w, int h, boolean max) {
		if (!loaded) {
			load();
		}
		w = Math.max(Layout.MIN_W, w);
		h = Math.max(Layout.MIN_H, h);
		if (max ? maximized : !maximized && w == width && h == height) {
			return;
		}
		// A maximised window keeps the size it had before, so un-maximising next session lands
		// somewhere sensible rather than at the whole screen.
		if (!max) {
			width = w;
			height = h;
		}
		maximized = max;
		save();
	}

	private static void save() {
		OutputStream out = null;
		try {
			File f = file();
			File dir = f.getParentFile();
			if (!dir.exists() && !dir.mkdirs()) {
				return;
			}
			Properties p = new Properties();
			p.setProperty("mode", nameFor(mode));
			p.setProperty("width", "" + width);
			p.setProperty("height", "" + height);
			p.setProperty("maximized", "" + maximized);
			p.setProperty("drawdistance", "" + drawDistance);
			p.setProperty("panelscale", "" + panelScale);
			p.setProperty("gamescale", "" + gameScale);
			p.setProperty("smoothscale", "" + smoothScale);
			p.setProperty("stretched", "" + stretched);
			p.setProperty("stretchaspect", "" + stretchAspect);
			p.setProperty("stretchinteger", "" + stretchInteger);
			p.setProperty("hidesidebar", "" + hideSidebar);
			out = new FileOutputStream(f);
			p.store(out, "Death Plateau window: mode=fixed|classic|modern (F9 in game switches it), panelscale=100|125|150|200");
		} catch (Exception ignored) {
		} finally {
			try {
				if (out != null) {
					out.close();
				}
			} catch (Exception ignored) {
			}
		}
	}
}
