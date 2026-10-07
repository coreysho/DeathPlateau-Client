package jagex2.client.plugin;

import jagex2.graphics.Pix2D;
import jagex2.graphics.PixFont;

/**
 * Everything an overlay is allowed to draw with.
 *
 * WHY A FACADE AND NOT Pix2D ITSELF. Pix2D is decompiled code: fillRect takes (height, y, colour,
 * width, x), drawRect takes (y, height, colour, x, width), and fillRectTrans takes a sixth
 * argument that is the x. Nobody writing a plugin should have to know that, and nothing outside
 * this file should depend on it - if those signatures are ever cleaned up, this class absorbs the
 * change and every plugin keeps working.
 *
 * COORDINATES ARE VIEWPORT-LOCAL. Overlays render with the viewport bound, so 0,0 is its top-left
 * corner and the drawable area is {@link #width()} x {@link #height()}. Drawing outside that is
 * clipped, not an error.
 *
 * THE AREA IS NOT A CONSTANT. It was 512x334 in the fixed window and still is, but the resizable
 * display modes give the game the whole window - so an overlay anchored to a corner has to ask
 * each frame rather than assume. These are the same numbers the client's own xp drops anchor to
 * (Layout.openW/openH), which keeps a plugin clear of the chatbox and the side panels the way
 * they are.
 *
 * TEXT IS DRAWN FROM ITS BASELINE, which is how the client's own fonts work: y is the bottom of
 * the line, not the top. {@link #lineHeight()} is there to step between rows.
 */
public final class OverlayGraphics {

	/** The fixed window's viewport, and the smallest the drawable area is ever reported as. */
	public static final int FIXED_WIDTH = 512;
	public static final int FIXED_HEIGHT = 334;

	public static final int FONT_SMALL = 0;   // plain 11
	public static final int FONT_NORMAL = 1;  // plain 12
	public static final int FONT_BOLD = 2;    // bold 12

	private final PixFont small;
	private final PixFont normal;
	private final PixFont bold;

	private PixFont font;

	private int width = FIXED_WIDTH;
	private int height = FIXED_HEIGHT;

	OverlayGraphics(PixFont small, PixFont normal, PixFont bold) {
		this.small = small;
		this.normal = normal;
		this.bold = bold;
		this.font = normal;
	}

	/**
	 * Called before each overlay renders, so one overlay's font choice cannot leak into the next,
	 * and so every overlay in a frame is told the same drawable area.
	 */
	void reset(int width, int height) {
		this.font = this.normal;
		this.width = width;
		this.height = height;
	}

	/** Width of the area an overlay may draw in, this frame. */
	public int width() {
		return this.width;
	}

	/** Height of the area an overlay may draw in, this frame. */
	public int height() {
		return this.height;
	}

	public OverlayGraphics setFont(int which) {
		if (which == FONT_SMALL) {
			this.font = this.small;
		} else if (which == FONT_BOLD) {
			this.font = this.bold;
		} else {
			this.font = this.normal;
		}
		return this;
	}

	/**
	 * Draws text with a black drop shadow, the way the client labels things in the world. Honours
	 * the client's own colour tags, so "@gre@done" comes out green.
	 */
	public void text(int x, int y, String text, int colour) {
		if (text != null && this.font != null) {
			this.font.drawStringTag(colour, x, y, true, text);
		}
	}

	/** Text with no shadow. Cheaper, and easier to read on a solid background. */
	public void textFlat(int x, int y, String text, int colour) {
		if (text != null && this.font != null) {
			this.font.drawString(x, colour, y, text);
		}
	}

	/** Shadowed text centred on x. */
	public void textCentred(int x, int y, String text, int colour) {
		if (text != null && this.font != null) {
			this.font.drawStringTag(colour, x - this.font.stringWidTag(text) / 2, y, true, text);
		}
	}

	/** Shadowed text ending at x, for right-aligned columns. */
	public void textRight(int x, int y, String text, int colour) {
		if (text != null && this.font != null) {
			this.font.drawStringTag(colour, x - this.font.stringWidTag(text), y, true, text);
		}
	}

	/** Width the current font would need for the text, colour tags excluded. */
	public int textWidth(String text) {
		return this.font == null ? 0 : this.font.stringWidTag(text);
	}

	/** Distance from one baseline to the next in the current font. */
	public int lineHeight() {
		return this.font == null ? 12 : this.font.height;
	}

	/** Solid filled rectangle. */
	public void fill(int x, int y, int width, int height, int colour) {
		Pix2D.fillRect(height, y, colour, width, x);
	}

	/** Filled rectangle, alpha 0 (invisible) to 255 (solid). */
	public void fillAlpha(int x, int y, int width, int height, int colour, int alpha) {
		Pix2D.fillRectTrans(colour, y, width, height, alpha, x);
	}

	/** One pixel outline. */
	public void box(int x, int y, int width, int height, int colour) {
		Pix2D.drawRect(y, height, colour, x, width);
	}

	/** Horizontal line of the given width, starting at x,y. */
	public void hline(int x, int y, int width, int colour) {
		Pix2D.hline(x, colour, y, width);
	}

	/** Vertical line of the given height, starting at x,y. */
	public void vline(int x, int y, int height, int colour) {
		Pix2D.vline(x, colour, height, y);
	}

	/**
	 * A labelled panel: a translucent black box with a border, the title across the top and each
	 * line under it. Returns the height it used, so a second panel can be stacked below the first.
	 *
	 * This exists because every overlay that shows a few numbers ends up drawing the same thing,
	 * and six plugins each rolling their own means six slightly different looking boxes.
	 */
	public int panel(int x, int y, String title, String[] lines, int titleColour, int textColour) {
		int padding = 4;
		int lineHeight = this.lineHeight() + 2;
		int rows = lines == null ? 0 : lines.length;
		int width = this.textWidth(title);
		for (int i = 0; i < rows; i++) {
			int w = this.textWidth(lines[i]);
			if (w > width) {
				width = w;
			}
		}
		width += padding * 2;
		int height = padding * 2 + lineHeight * (rows + (title == null ? 0 : 1));

		this.fillAlpha(x, y, width, height, 0x000000, 160);
		this.box(x, y, width, height, 0x5A5A5A);

		int baseline = y + padding + this.lineHeight();
		if (title != null) {
			this.textFlat(x + padding, baseline, title, titleColour);
			baseline += lineHeight;
		}
		for (int i = 0; i < rows; i++) {
			this.text(x + padding, baseline, lines[i], textColour);
			baseline += lineHeight;
		}
		return height;
	}
}
