package lostcity;

import java.awt.Image;
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
 * WHY THE SMALL ONES ARE A DIFFERENT PICTURE. Below about 32 pixels the whole badge - the
 * wordmark, the castle, the ravine - stops being readable at all, so those sizes are a tight crop
 * of the skull alone. What a 16-pixel icon has to do is be recognisable in a taskbar, not be
 * complete.
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
