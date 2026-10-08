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

	/** Where clickable()/scrollable() put their regions. Set by the manager each frame. */
	private InteractiveRegions regions;

	/** Whose regions these are, so a plugin that is turned off takes its clicks with it. */
	private Plugin owner;

	/**
	 * Where the player has dragged this overlay to, as an offset added to everything it draws.
	 *
	 * THIS IS WHY NO PLUGIN HAD TO CHANGE TO BECOME DRAGGABLE. An overlay works out where it
	 * wants to be - a corner, a margin, a column under a tile - and this class quietly adds the
	 * player's offset on the way to the screen. The overlay never knows it moved, which is also
	 * what keeps the two plugins already published draggable without being rebuilt.
	 *
	 * Set by the manager before each overlay renders, and zero for an overlay nobody has moved.
	 */
	private int offsetX;
	private int offsetY;

	/**
	 * The bounding box of everything drawn since the last reset, in screen coordinates.
	 *
	 * Recorded rather than declared, because an overlay has never had to say how big it is and
	 * asking every one of them to start would be a change to the API and a chance to get wrong.
	 * What it draws is what it occupies, which is also exactly what a player would try to grab.
	 *
	 * Empty - left greater than right - when nothing was drawn.
	 */
	private int left;
	private int top;
	private int right;
	private int bottom;

	OverlayGraphics(PixFont small, PixFont normal, PixFont bold) {
		this.small = small;
		this.normal = normal;
		this.bold = bold;
		this.font = normal;
	}

	/** Something an overlay drew that the player can turn the wheel on. */
	public interface Scrolled {

		/** delta is the wheel's turn: negative is up, positive is down. */
		void onScroll(int delta);
	}

	/**
	 * Records a string's box, given the position the overlay asked for.
	 *
	 * Text is drawn from its BASELINE - y is the bottom of the line, not the top - so the box
	 * runs from one line height above y to a couple of pixels below it, which is where a
	 * descender and the client's own text shadow land. Getting this backwards would put an
	 * overlay's grab area a whole line below the words a player is looking at.
	 */
	private void markText(int x, int y, String text) {
		if (this.font == null || text == null) {
			return;
		}
		int wide = this.font.stringWidTag(text);
		int tall = this.lineHeight();
		this.mark(this.tx(x), this.ty(y) - tall, wide, tall + 2);
	}

	/**
	 * Marks a rectangle the overlay just drew as clickable. A click inside it runs the action and
	 * is CONSUMED - it will not also walk the player or open a menu.
	 *
	 * Declared while drawing, on purpose: the overlay has just worked out where the thing is, and
	 * saying so costs it nothing. Regions last one frame, so an overlay that stops drawing a
	 * thing stops it being clickable in the same breath.
	 *
	 * Later regions win where two overlap, matching what a player sees: the thing drawn last is
	 * the thing on top.
	 */
	public void clickable(int x, int y, int width, int height, Runnable onClick) {
		if (onClick != null && this.regions != null) {
			// Offset like everything else: a region left where the overlay USED to be is a
			// button the player can press by clicking empty space.
			this.regions.add(this.tx(x), this.ty(y), width, height, onClick, null, this.owner);
		}
	}

	/** The same for the wheel: a turn inside the rectangle goes to the overlay, not the camera. */
	public void scrollable(int x, int y, int width, int height, Scrolled onScroll) {
		if (onScroll != null && this.regions != null) {
			this.regions.add(this.tx(x), this.ty(y), width, height, null, onScroll, this.owner);
		}
	}

	/**
	 * Called before each overlay renders, so one overlay's font choice cannot leak into the next,
	 * and so every overlay in a frame is told the same drawable area.
	 */
	void reset(int width, int height, InteractiveRegions regions, Plugin owner,
		int offsetX, int offsetY) {
		this.font = this.normal;
		this.width = width;
		this.height = height;
		this.regions = regions;
		this.owner = owner;
		this.offsetX = offsetX;
		this.offsetY = offsetY;
		this.left = Integer.MAX_VALUE;
		this.top = Integer.MAX_VALUE;
		this.right = Integer.MIN_VALUE;
		this.bottom = Integer.MIN_VALUE;
	}

	/** x in screen coordinates: where the overlay asked for, plus where it has been dragged. */
	private int tx(int x) {
		return x + this.offsetX;
	}

	private int ty(int y) {
		return y + this.offsetY;
	}

	/**
	 * Records a rectangle as part of what this overlay occupies. Takes SCREEN coordinates - call
	 * it with tx/ty already applied, so the box is where the thing ended up rather than where it
	 * was asked for.
	 */
	private void mark(int x, int y, int width, int height) {
		if (width <= 0 || height <= 0) {
			return;
		}
		if (x < this.left) {
			this.left = x;
		}
		if (y < this.top) {
			this.top = y;
		}
		if (x + width > this.right) {
			this.right = x + width;
		}
		if (y + height > this.bottom) {
			this.bottom = y + height;
		}
	}

	/**
	 * What the last overlay to render occupies, or null if it drew nothing.
	 *
	 * Four ints rather than a Rectangle: this is read once per overlay per frame by the manager
	 * and allocating for it would be a garbage-per-frame answer to a question with four numbers
	 * in it.
	 */
	boolean hasBounds() {
		return this.left <= this.right && this.top <= this.bottom;
	}

	int boundsLeft() {
		return this.left;
	}

	int boundsTop() {
		return this.top;
	}

	int boundsRight() {
		return this.right;
	}

	int boundsBottom() {
		return this.bottom;
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
			this.markText(x, y, text);
			this.font.drawStringTag(colour, this.tx(x), this.ty(y), true, text);
		}
	}

	/** Text with no shadow. Cheaper, and easier to read on a solid background. */
	public void textFlat(int x, int y, String text, int colour) {
		if (text != null && this.font != null) {
			this.markText(x, y, text);
			this.font.drawString(this.tx(x), colour, this.ty(y), text);
		}
	}

	/** Shadowed text centred on x. */
	public void textCentred(int x, int y, String text, int colour) {
		if (text != null && this.font != null) {
			int centred = x - this.font.stringWidTag(text) / 2;
			this.markText(centred, y, text);
			this.font.drawStringTag(colour, this.tx(centred), this.ty(y), true, text);
		}
	}

	/**
	 * Centred, with a black outline instead of the drop shadow.
	 *
	 * A shadow is one offset copy, so text over a light wall or a sandy floor loses its left and
	 * top edges into the background - which is most of Al Kharid. An outline is the same trick in
	 * four directions and stays legible over anything.
	 *
	 * FOUR OFFSETS, NOT EIGHT. Five draws per label rather than nine, and the diagonals add almost
	 * nothing at this font size; a ground pile can be a dozen rows and this runs per row per
	 * frame. The outline passes are flat, not shadowed, or each of them would cast its own.
	 */
	public void textCentredOutlined(int x, int y, String text, int colour) {
		if (text == null || this.font == null) {
			return;
		}
		int centred = x - this.font.stringWidTag(text) / 2;
		this.markText(centred, y, text);
		int left = this.tx(centred);
		int top = this.ty(y);
		this.font.drawString(left - 1, 0, top, text);
		this.font.drawString(left + 1, 0, top, text);
		this.font.drawString(left, 0, top - 1, text);
		this.font.drawString(left, 0, top + 1, text);
		this.font.drawStringTag(colour, left, top, false, text);
	}

	/** Shadowed text ending at x, for right-aligned columns. */
	public void textRight(int x, int y, String text, int colour) {
		if (text != null && this.font != null) {
			int aligned = x - this.font.stringWidTag(text);
			this.markText(aligned, y, text);
			this.font.drawStringTag(colour, this.tx(aligned), this.ty(y), true, text);
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

	/**
	 * Draws a sprite with its top-left at x,y. A null sprite draws nothing, so a plugin can pass
	 * the result of a lookup straight in without testing it first.
	 */
	public void sprite(int x, int y, Sprite sprite) {
		if (sprite != null) {
			this.mark(this.tx(x), this.ty(y), sprite.width(), sprite.height());
			sprite.image.plotSprite(this.ty(y), this.tx(x));
		}
	}

	/** Solid filled rectangle. */
	public void fill(int x, int y, int width, int height, int colour) {
		this.mark(this.tx(x), this.ty(y), width, height);
		Pix2D.fillRect(height, this.ty(y), colour, width, this.tx(x));
	}

	/** Filled rectangle, alpha 0 (invisible) to 255 (solid). */
	public void fillAlpha(int x, int y, int width, int height, int colour, int alpha) {
		this.mark(this.tx(x), this.ty(y), width, height);
		Pix2D.fillRectTrans(colour, this.ty(y), width, height, alpha, this.tx(x));
	}

	/** One pixel outline. */
	public void box(int x, int y, int width, int height, int colour) {
		this.mark(this.tx(x), this.ty(y), width, height);
		Pix2D.drawRect(this.ty(y), height, colour, this.tx(x), width);
	}

	/** Horizontal line of the given width, starting at x,y. */
	public void hline(int x, int y, int width, int colour) {
		this.mark(this.tx(x), this.ty(y), width, 1);
		Pix2D.hline(this.tx(x), colour, this.ty(y), width);
	}

	/** Vertical line of the given height, starting at x,y. */
	public void vline(int x, int y, int height, int colour) {
		this.mark(this.tx(x), this.ty(y), 1, height);
		Pix2D.vline(this.tx(x), colour, height, this.ty(y));
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
