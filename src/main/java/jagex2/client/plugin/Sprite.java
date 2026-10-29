package jagex2.client.plugin;

import jagex2.graphics.Pix32;

/**
 * An image from the client's cache, for an overlay to draw.
 *
 * WHY A WRAPPER. The client's own image class is Pix32, a decompiled class whose fields are
 * called wi, hi, owi and ohi, and whose plotSprite takes (y, x) in that order. A plugin drawing a
 * skill icon should not have to know any of that, and nothing outside this class does - if Pix32
 * is ever cleaned up, this absorbs it.
 *
 * WHERE THEY COME FROM. A plugin does not load sprites; it asks for one by what it is, such as
 * {@link PluginContext#getSkillIcon(int)}. There is no "give me the sprite cache" method, because
 * the names and indices inside that cache are the client's business and change with it.
 */
public final class Sprite {

	final Pix32 image;

	Sprite(Pix32 image) {
		this.image = image;
	}

	/** Wraps an image, or returns null for a missing one, so callers test one thing. */
	static Sprite of(Pix32 image) {
		return image == null ? null : new Sprite(image);
	}

	public int width() {
		return this.image.wi;
	}

	public int height() {
		return this.image.hi;
	}

	/**
	 * The same image as something Swing can draw, for a sidebar page.
	 *
	 * WHY THIS IS HERE AND NOT IN THE UI. Pix32 holds its pixels with no alpha channel at all -
	 * there is one transparent colour and it is 0, pure black - so turning one into a
	 * BufferedImage means knowing that rule. It is the client's rule, it has bitten this
	 * codebase before in the loot beams, and it belongs behind the same wrapper that already
	 * hides wi and hi rather than being rediscovered by every page that wants an item icon.
	 *
	 * A BLACK PIXEL IN THE ARTWORK IS LOST, which is the cost of a one-colour key and is why
	 * item icons are drawn over a dark panel here rather than cut out onto a light one.
	 */
	public java.awt.image.BufferedImage toImage() {
		int w = this.image.wi;
		int h = this.image.hi;
		if (w <= 0 || h <= 0 || this.image.pixels == null) {
			return null;
		}
		java.awt.image.BufferedImage out =
			new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
		int[] argb = new int[w * h];
		int[] pixels = this.image.pixels;
		for (int i = 0; i < argb.length && i < pixels.length; i++) {
			argb[i] = pixels[i] == 0 ? 0 : 0xFF000000 | pixels[i];
		}
		out.setRGB(0, 0, w, h, argb, 0, w);
		return out;
	}
}
