package jagex2.client;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

/**
 * The window: fixed or resizable, how big a resizable window was left, and how far the scene is
 * drawn.
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
	private static boolean resizable;
	private static int width = 1024;
	private static int height = 768;
	private static boolean maximized;
	private static int drawDistance = 25;                    // World3D.MIN_DISTANCE, 377's own

	/**
	 * What the F9 row steps through: 377's own 25 tiles, then out to the edge of the 104x104 scene
	 * the server loads. 25 first, so nobody's frame rate changes until they ask for it.
	 */
	public static final int[] DRAW_DISTANCES = { 25, 35, 45, 52 };

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
			resizable = "resizable".equals(p.getProperty("mode", "fixed").trim());
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

	public static synchronized boolean resizable() {
		if (!loaded) {
			load();
		}
		return resizable;
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

	public static synchronized void setResizable(boolean on) {
		if (!loaded) {
			load();
		}
		if (resizable != on) {
			resizable = on;
			save();
		}
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
			p.setProperty("mode", resizable ? "resizable" : "fixed");
			p.setProperty("width", "" + width);
			p.setProperty("height", "" + height);
			p.setProperty("maximized", "" + maximized);
			p.setProperty("drawdistance", "" + drawDistance);
			out = new FileOutputStream(f);
			p.store(out, "Death Plateau window: mode=fixed|resizable (F9 in game switches it)");
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
