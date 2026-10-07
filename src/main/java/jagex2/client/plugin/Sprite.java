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
}
