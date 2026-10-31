package lostcity;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

/**
 * The window icon, in the sizes a desktop asks for.
 *
 * IN THE LAUNCHER'S PACKAGE because both windows need it and this is the only source the two
 * jars share. launcher/src is compiled into the client jar as well as into
 * Death-Plateau-Launcher.jar (see build.gradle and release.yml), so a class put here is in both;
 * one put in jagex2.client would be in the client jar alone, and the launcher - the first window
 * a player sees - would be back to the default Java cup.
 *
 *
 * WHY SEVERAL. setIconImages hands the window manager a set and it picks per use - 16 in the
 * title bar, 32 or 48 in the taskbar and alt-tab, more for a large-icon view. Given one image it
 * scales, and a photographic badge scaled to 16 pixels by a window manager is a smear.
 *
 * WHY THE SMALL ONES ARE A DIFFERENT PICTURE. The icons are the DP sigil, and below about 64
 * pixels its ragged ring is a few pixels of broken red while the monogram inside it loses its
 * counters. So every size up to 64 is a tight crop of the monogram with the ring dropped, and
 * only 128 carries the whole mark. What a 16-pixel icon has to do is be recognisable in a
 * taskbar, not be complete.
 *
 * They are a badge on black rather than a cut-out mark, because the artwork is drawn on black
 * and cutting it out leaves it correct over black and bleached over anything else. The alpha is
 * the rounded-square badge shape instead. tools/art/make_icons.py builds them and has the whole
 * reasoning; re-run it when the mark changes.
 *
 * NOTHING HERE THROWS. An icon is decoration: a client that cannot find its own images is a
 * client with the default Java icon, not a client that fails to start. That matters for the
 * applet, which has no window to put an icon on and no resources beside it either.
 */
public final class Branding {

	private static final int[] SIZES = { 16, 24, 32, 48, 64, 128 };

	/** Loaded once - these go to the window manager at startup and never change. */
	private static List<Image> icons;

	private Branding() {
	}

	/** The window icons, largest last, or an empty list if none could be read. */
	public static synchronized List<Image> icons() {
		if (icons != null) {
			return icons;
		}
		List<Image> loaded = new ArrayList<Image>();
		for (int i = 0; i < SIZES.length; i++) {
			Image image = read("/deathplateau/icon-" + SIZES[i] + ".png");
			if (image != null) {
				loaded.add(image);
			}
		}
		icons = loaded;
		return icons;
	}

	/** Loaded once each, for the same reason as the icons: they are drawn, not re-read. */
	private static Image sigil, wordmark;

	/**
	 * The ring-and-monogram mark, 468 square, or null.
	 *
	 * THE BIG ART IS NOT THE ICON. icon-128 is a badge: the mark cropped to a rounded square with
	 * alpha cut round it, which is what a taskbar wants. These two are the artwork as drawn -
	 * black background included, no alpha - so they belong on a dark panel and nowhere else.
	 * The launcher's face is black, so that is exactly where they go.
	 */
	public static synchronized Image sigil() {
		if (sigil == null) {
			sigil = floatOnDark(read("/deathplateau/sigil.png"));
		}
		return sigil;
	}

	/** The "DEATH PLATEAU" lettering, 419x87, or null. */
	public static synchronized Image wordmark() {
		if (wordmark == null) {
			wordmark = floatOnDark(read("/deathplateau/wordmark.png"));
		}
		return wordmark;
	}

	/**
	 * Gives artwork drawn on black the alpha it never had, so it sits on a backdrop instead of in
	 * a black box.
	 *
	 * BOTH FILES ARE RGB WITH NO ALPHA - the mark painted on a black field - which is correct on
	 * a black page and a hard-edged rectangle on anything else. Drawn over the launcher's red
	 * vignette they were two obvious boxes.
	 *
	 * Cutting out "the black bits" by a threshold would hack the glow and the darker steel off
	 * the edges of the lettering. This instead unpremultiplies against black: alpha becomes the
	 * brightest channel and the colour is scaled back up by the same amount, so compositing over
	 * black reproduces the original pixel for pixel (out = colour*a + 0*(1-a) = the original),
	 * and over anything else the backdrop shows through in proportion to how dark the art is.
	 * Nothing is thresholded, so the soft edges stay soft.
	 */
	private static Image floatOnDark(Image source) {
		if (source == null) {
			return null;
		}
		try {
			int w = source.getWidth(null);
			int h = source.getHeight(null);
			if (w <= 0 || h <= 0) {
				return source;
			}
			BufferedImage flat = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
			Graphics2D g = flat.createGraphics();
			g.drawImage(source, 0, 0, null);
			g.dispose();

			BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					int rgb = flat.getRGB(x, y);
					int r = (rgb >> 16) & 0xFF;
					int gr = (rgb >> 8) & 0xFF;
					int b = rgb & 0xFF;
					int max = r > gr ? (r > b ? r : b) : (gr > b ? gr : b);
					if (max == 0) {
						continue;              // pure black: nothing of the mark is here
					}
					out.setRGB(x, y, (max << 24)
						| ((r * 255 / max) << 16)
						| ((gr * 255 / max) << 8)
						| (b * 255 / max));
				}
			}
			return out;
		} catch (Throwable error) {
			return source;                     // the boxed version is better than no mark at all
		}
	}

	/** The smallest icon at least this wide, for drawing in the title bar. Null if there are none. */
	public static Image iconAtLeast(int size) {
		List<Image> all = icons();
		for (int i = 0; i < all.size(); i++) {
			if (all.get(i).getWidth(null) >= size) {
				return all.get(i);
			}
		}
		return all.isEmpty() ? null : all.get(all.size() - 1);
	}

	private static Image read(String path) {
		java.io.InputStream in = null;
		try {
			in = Branding.class.getResourceAsStream(path);
			return in == null ? null : ImageIO.read(in);
		} catch (Throwable error) {
			return null;
		} finally {
			try {
				if (in != null) {
					in.close();
				}
			} catch (Throwable ignored) {
			}
		}
	}
}
