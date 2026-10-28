/*
 * Renders the login screen to a PNG, so it can be looked at rather than reasoned about.
 *
 * WHY THIS EXISTS. The screen is three hundred and eighty thousand pixels of blending, a sprite
 * laid at 36% under a vignette, and a dozen controls whose hit tests have to agree with where
 * they were drawn. None of that is checkable by reading source - the loot beam earlier in this
 * project went three rounds of blind iteration before anyone rendered it - and the real title
 * screen cannot be reached in a headless test without a cache, a socket and a login server.
 *
 * THERE IS NO TYPE IN THE PICTURE, and that is not a defect of the preview. PixFont is built
 * from the cache, no test in this repo has a cache with fonts in it, and TitleScreen is written
 * to draw its art and skip its labels when the font is null - which is also what a new player
 * sees while the cache is still downloading. So the preview shows exactly that state.
 *
 * WHAT IT PROVES INSTEAD, with `guides`: every hit test is probed per pixel and tinted where it
 * answers true. A control drawn in one place and clicked in another shows up as a tint that does
 * not sit on its box - which is the bug this screen was written to make impossible, and the one
 * the music toggle shipped with for nineteen pixels.
 */
package jagex2.client;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import jagex2.graphics.Pix32;

public class TitlePreview {

	public static void main(String[] args) throws Exception {
		String out = args.length > 0 ? args[0] : "title.png";
		boolean guides = args.length > 1 && "guides".equals(args[1]);
		int hover = args.length > 2 ? Integer.parseInt(args[2]) : -1;

		Pix32 canvas = new Pix32(TitleScreen.W, TitleScreen.H);

		// The loading screen shares the ground and the mark; drawn here with a null font, which
		// is the state it is genuinely in for its first frames - the fonts are in the cache it
		// is still downloading.
		if ("progress".equals(args.length > 1 ? args[1] : "")) {
			TitleScreen.paintProgress(canvas, null, 35, "Loading animations");
			write(canvas, out);
			return;
		}

		List<LoginProfiles.Profile> profiles = new ArrayList<LoginProfiles.Profile>();
		profiles.add(profile("Corey", true));
		profiles.add(profile("Faken", true));
		profiles.add(profile("Iron_Sgt", false));

		TitleScreen.paint(canvas, null, null, "Corey", "hunter2", 1, true, "", profiles,
			hover, 142, 10);

		if (guides) {
			overlay(canvas.pixels, profiles.size());
		}

		write(canvas, out);
		System.out.println("wrote " + out + (guides ? " (with hit-test guides)" : ""));
	}

	static void write(Pix32 canvas, String out) throws Exception {
		BufferedImage img = new BufferedImage(TitleScreen.W, TitleScreen.H, BufferedImage.TYPE_INT_RGB);
		img.setRGB(0, 0, TitleScreen.W, TitleScreen.H, canvas.pixels, 0, TitleScreen.W);
		ImageIO.write(img, "png", new File(out));
	}

	/** Profile's constructor is package-private, which is why this preview lives in the package. */
	static LoginProfiles.Profile profile(String name, boolean withSecret) {
		return new LoginProfiles.Profile(name, withSecret ? "x" : "", 0L);
	}

	/**
	 * Tints each pixel by which hit test claims it. Probing every pixel rather than drawing the
	 * constants again is the point: this asks the SHIPPING predicates where they answer true, so
	 * it cannot agree with the drawing by sharing a mistake with it.
	 */
	static void overlay(int[] px, int count) {
		for (int y = 0; y < TitleScreen.H; y++) {
			for (int x = 0; x < TitleScreen.W; x++) {
				int tint = 0;
				if (TitleScreen.fieldAt(x, y) == 0) {
					tint = 0x00A0FF;
				} else if (TitleScreen.fieldAt(x, y) == 1) {
					tint = 0x00FFC0;
				} else if (TitleScreen.inEnter(x, y)) {
					tint = 0xFFC000;
				} else if (TitleScreen.inRemember(x, y, null)) {
					tint = 0xC080FF;
				} else if (TitleScreen.forgetAt(x, y, count) >= 0) {
					tint = 0xFF4040;
				} else if (TitleScreen.profileAt(x, y, count) >= 0) {
					tint = 0x40FF40;
				} else if (TitleScreen.inAdd(x, y, count)) {
					tint = 0xFFFFFF;
				}
				if (tint == 0) {
					continue;
				}
				int i = y * TitleScreen.W + x;
				int c = px[i];
				px[i] = blend(c, tint);
			}
		}
	}

	static int blend(int under, int over) {
		int a = 70;
		int b = 256 - a;
		return ((((over >> 16 & 0xFF) * a + (under >> 16 & 0xFF) * b) >> 8) << 16)
			| ((((over >> 8 & 0xFF) * a + (under >> 8 & 0xFF) * b) >> 8) << 8)
			| (((over & 0xFF) * a + (under & 0xFF) * b) >> 8);
	}
}
