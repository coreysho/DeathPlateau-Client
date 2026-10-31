package lostcity;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;

/**
 * The few things the splash and the launcher window both draw.
 *
 * SHARED BECAUSE THEY ARE THE SAME PICTURE at two sizes - the splash is the launcher's face with
 * the panel and the footer taken off - and a backdrop that drifted between them would be visible
 * in the half second where one replaces the other.
 *
 * THE BACKDROP IS DRAWN, NOT AN IMAGE. A photographic background would be a fourth art file to
 * keep, to scale to whatever the window is, and to keep in step with the palette; a radial
 * gradient in the logo's red over near-black is the same effect in four lines, is sharp at any
 * size, and moves when {@link Theme#ACCENT} moves.
 */
final class Art {

	/** How far the glow reaches, as a fraction of the window's diagonal. */
	private static final float GLOW = 0.72f;

	private Art() {
	}

	static void smooth(Graphics2D g) {
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
	}

	/**
	 * Black, with the logo's red bled in behind where the mark sits.
	 *
	 * centreY rather than the middle of the window: the glow belongs behind the sigil, which is
	 * in the upper half of the launcher and the middle of the splash.
	 */
	static void vignette(Graphics2D g, int w, int h, int centreY) {
		g.setColor(Color.BLACK);
		g.fillRect(0, 0, w, h);
		float radius = (float) Math.sqrt(w * w + h * h) * GLOW;
		// A RadialGradientPaint will not take a radius of zero, and a window can be asked to
		// paint before it has been given a size.
		if (radius < 1f) {
			return;
		}
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setPaint(new RadialGradientPaint(
			new Point2D.Float(w / 2f, centreY),
			radius,
			new float[] { 0f, 0.45f, 1f },
			new Color[] {
				// Dark enough that the artwork still reads as the brightest thing in the window -
				// and it has to hold at both sizes, because the splash is a third of the area and
				// a glow judged on the big window washes the small one out.
				new Color(0x2C, 0x0C, 0x09),
				new Color(0x15, 0x07, 0x06),
				Color.BLACK
			}));
		g2.fillRect(0, 0, w, h);
		g2.dispose();
	}

	/**
	 * The plateau, along the bottom of the window.
	 *
	 * THE ONE THING IN HERE THAT IS ABOUT THIS SERVER AND NO OTHER. A launcher is a badge, a
	 * button and a progress bar whichever server it belongs to, so the shape that says which one
	 * this is has to come from somewhere - and the place is named after a flat-topped mountain.
	 * The near ridge is that: it climbs, runs level across the middle, and falls away.
	 *
	 * Two layers, both barely above black. It is a horizon the eye finds after the artwork, not a
	 * picture competing with it - at the contrast a landscape painting would want, the mark stops
	 * being the first thing you see.
	 */
	static void ridge(Graphics2D g, int w, int h) {
		int band = Math.min(120, h / 3);
		if (band < 20) {
			return;
		}
		fill(g, w, h, band, new Color(0x16, 0x0C, 0x0B), FAR);
		fill(g, w, h, band, new Color(0x0B, 0x06, 0x06), NEAR);
	}

	/** x, then height above the base, both as fractions. The skyline behind the plateau. */
	private static final float[] FAR = {
		0f, 0.20f, 0.10f, 0.52f, 0.18f, 0.44f, 0.30f, 0.78f, 0.40f, 0.70f, 0.48f, 0.88f,
		0.56f, 0.74f, 0.66f, 0.86f, 0.76f, 0.58f, 0.88f, 0.66f, 1f, 0.40f
	};

	/** The plateau itself: the long level run between 0.30 and 0.62 is the whole point of it. */
	private static final float[] NEAR = {
		0f, 0.05f, 0.08f, 0.10f, 0.18f, 0.42f, 0.26f, 0.60f, 0.30f, 0.62f, 0.62f, 0.62f,
		0.66f, 0.58f, 0.74f, 0.30f, 0.86f, 0.16f, 1f, 0.10f
	};

	private static void fill(Graphics2D g, int w, int h, int band, Color colour, float[] shape) {
		int n = shape.length / 2;
		int[] xs = new int[n + 2];
		int[] ys = new int[n + 2];
		for (int i = 0; i < n; i++) {
			xs[i] = Math.round(shape[i * 2] * w);
			ys[i] = h - Math.round(shape[i * 2 + 1] * band);
		}
		// Closed down the right edge and back along the bottom, so it is a solid mass and not a line.
		xs[n] = w;
		ys[n] = h;
		xs[n + 1] = 0;
		ys[n + 1] = h;
		g.setColor(colour);
		g.fillPolygon(xs, ys, n + 2);
	}

	/**
	 * Draws an image centred on x, scaled to the given width, keeping its shape.
	 *
	 * Returns the bottom edge it reached, so a caller can stack the next thing under it without
	 * each one carrying its own copy of the arithmetic.
	 */
	static int image(Graphics2D g, Image image, int centreX, int top, int width) {
		if (image == null) {
			return top;
		}
		int sw = image.getWidth(null);
		int sh = image.getHeight(null);
		if (sw <= 0 || sh <= 0) {
			return top;
		}
		int height = Math.round((float) width * sh / sw);
		g.drawImage(image, centreX - width / 2, top, width, height, null);
		return top + height;
	}

	/** Centred text, returning the baseline used. */
	static int text(Graphics2D g, String line, int centreX, int baseline) {
		int width = g.getFontMetrics().stringWidth(line);
		g.drawString(line, centreX - width / 2, baseline);
		return baseline;
	}
}
