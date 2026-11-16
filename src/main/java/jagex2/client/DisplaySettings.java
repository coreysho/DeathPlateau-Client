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

	/**
	 * What the F9 row steps through: 377's own 25 tiles, then out to the edge of the 104x104 scene
	 * the server loads. 25 first, so nobody's frame rate changes until they ask for it.
	 */
	public static final int[] DRAW_DISTANCES = { 25, 35, 45, 52 };

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
			out = new FileOutputStream(f);
			p.store(out, "Death Plateau window: mode=fixed|classic|modern (F9 in game switches it)");
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
