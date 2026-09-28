package jagex2.client;

/**
 * Where the game sits in the window: the fixed 765x503 frame, or resizable.
 *
 * FIXED is 377's screen, and every number in here reproduces it exactly - a Layout.fixed() maps
 * every point to itself and changes nothing about what is drawn where.
 *
 * RESIZABLE is Old School's "resizable - classic layout". The 3D viewport is the whole window and
 * the three panels of the fixed frame are laid over it at their normal pixel size: the chatbox
 * bottom-left, the minimap top-right and the side tabs bottom-right. Each panel is a rectangle cut
 * straight out of the fixed frame - the client still draws that frame exactly as it always did,
 * into a 765x503 buffer, and the panels are copied from there to where the window has room for
 * them. So the sidebar, the chat, the tabs and the minimap are the same code at the same
 * coordinates in both modes, and nothing inside them had to learn about the window.
 *
 * THE MOUSE follows from that. A point over a panel is turned back into the fixed-frame point it
 * came from, so the sidebar's hit tests, the tab buttons, the chat bar and the minimap click all
 * keep their fixed numbers. A point over the viewport becomes (x, y + FAR_Y): viewport points live
 * far below the 503-pixel frame, where no fixed-frame test can match them, and a viewport test
 * (inViewport) can never match a panel point. Everything that turns a viewport mouse point into a
 * viewport pixel subtracts vpX/vpY - 4/4 in fixed mode, the frame's border, as 377 wrote it.
 *
 * Pure geometry, no AWT, so it can be tested on its own (tools/clienttests/run_layouttest.py).
 */
public final class Layout {

	public static final int FIXED_W = 765;
	public static final int FIXED_H = 503;
	public static final int VIEWPORT_W = 512;
	public static final int VIEWPORT_H = 334;

	/** The smallest window resizable mode lays out: the fixed frame, so the panels never overlap. */
	public static final int MIN_W = FIXED_W;
	public static final int MIN_H = FIXED_H;

	/** Where viewport points go in the mouse's coordinates in resizable mode (see above). */
	public static final int FAR_Y = 10000;

	public static final int CHAT = 0;
	public static final int MINIMAP = 1;
	public static final int SIDEBAR = 2;
	public static final int PANELS = 3;
	public static final int VIEWPORT = -1;
	/** mapX/mapY: whatever is under the point. */
	public static final int ANY = -2;

	// The panels as rectangles of the fixed frame. The chatbox is everything under the viewport,
	// from the chat frame down through the chat bar; the minimap is the right column above the top
	// tab row; the sidebar is the right column from the top tab row down through the bottom one.
	private static final int[] PANEL_X = { 0, 516, 516 };
	private static final int[] PANEL_Y = { 338, 0, 160 };
	private static final int[] PANEL_W = { 519, 249, 249 };
	private static final int[] PANEL_H = { 165, 160, 343 };

	public final boolean resizable;
	/** The window's drawing area. */
	public final int width;
	public final int height;
	/** Viewport pixel (0, 0) in the mouse's coordinates. */
	public final int vpX;
	public final int vpY;
	/** The viewport's size: the 3D scene's buffer. */
	public final int vpW;
	public final int vpH;
	/** Viewport pixel (0, 0) in the window. */
	public final int vpScreenX;
	public final int vpScreenY;
	/**
	 * The part of the viewport that no panel can cover, from its top-left corner: 512x334 when
	 * fixed, and in resizable everything left of the right-hand column and above the chatbox.
	 * What 377 anchored to a corner of its viewport - the XP drops, the multicombat icon, private
	 * messages, the system update timer - anchors to a corner of this.
	 */
	public final int openW;
	public final int openH;
	/** Viewport-local top-left of a 512x334 main interface (a bank, a shop): centred in the open area. */
	public final int mainX;
	public final int mainY;
	/** Where a screen that is always 765x503 - the title screen, a fullscreen interface - is put. */
	public final int letterX;
	public final int letterY;
	/** The scene's projection scale, Pix3D.zoom. */
	public final int zoom;

	private final int[] panelScreenX = new int[PANELS];
	private final int[] panelScreenY = new int[PANELS];

	private Layout(boolean resizable, int width, int height) {
		this.resizable = resizable;
		this.width = width;
		this.height = height;
		if (!resizable) {
			this.vpX = 4;
			this.vpY = 4;
			this.vpW = VIEWPORT_W;
			this.vpH = VIEWPORT_H;
			this.vpScreenX = 4;
			this.vpScreenY = 4;
			this.openW = VIEWPORT_W;
			this.openH = VIEWPORT_H;
			this.mainX = 0;
			this.mainY = 0;
			this.letterX = 0;
			this.letterY = 0;
			this.zoom = 512;
			for (int p = 0; p < PANELS; p++) {
				this.panelScreenX[p] = PANEL_X[p];
				this.panelScreenY[p] = PANEL_Y[p];
			}
			return;
		}
		this.vpX = 0;
		this.vpY = FAR_Y;
		this.vpW = width;
		this.vpH = height;
		this.vpScreenX = 0;
		this.vpScreenY = 0;
		this.openW = width - PANEL_W[SIDEBAR];
		this.openH = height - PANEL_H[CHAT];
		this.mainX = Math.max(0, (this.openW - VIEWPORT_W) / 2);
		this.mainY = Math.max(0, (this.openH - VIEWPORT_H) / 2);
		this.letterX = (width - FIXED_W) / 2;
		this.letterY = (height - FIXED_H) / 2;
		this.zoom = zoomFor(this.openH);
		this.panelScreenX[CHAT] = 0;
		this.panelScreenY[CHAT] = height - PANEL_H[CHAT];
		this.panelScreenX[MINIMAP] = width - PANEL_W[MINIMAP];
		this.panelScreenY[MINIMAP] = 0;
		this.panelScreenX[SIDEBAR] = width - PANEL_W[SIDEBAR];
		this.panelScreenY[SIDEBAR] = height - PANEL_H[SIDEBAR];
	}

	public static Layout fixed() {
		return new Layout(false, FIXED_W, FIXED_H);
	}

	/** A window smaller than the fixed frame is laid out as the fixed frame (and clipped). */
	public static Layout resizable(int width, int height) {
		return new Layout(true, Math.max(width, MIN_W), Math.max(height, MIN_H));
	}

	/**
	 * The projection scale for an open area openH tall. Growing it with the square root of the
	 * height splits the difference between the two things a bigger window could mean: the world
	 * drawn bigger (the zoom rising with the height, as Old School does) and more of the world in
	 * view (the zoom left at 512). A 1080p window shows the world about 1.65x the fixed size with
	 * about 1.65x as much of it in each direction, which keeps the edge of the 25-tile scene out of
	 * sight at the usual camera angles. Never below the fixed 512.
	 */
	public static int zoomFor(int openH) {
		if (openH <= VIEWPORT_H) {
			return 512;
		}
		return (int) Math.round(512.0 * Math.sqrt(openH / (double) VIEWPORT_H));
	}

	public boolean sameAs(Layout other) {
		return other != null && other.resizable == this.resizable && other.width == this.width && other.height == this.height;
	}

	public static int panelFixedX(int panel) {
		return PANEL_X[panel];
	}

	public static int panelFixedY(int panel) {
		return PANEL_Y[panel];
	}

	public static int panelWidth(int panel) {
		return PANEL_W[panel];
	}

	public static int panelHeight(int panel) {
		return PANEL_H[panel];
	}

	public int panelScreenX(int panel) {
		return this.panelScreenX[panel];
	}

	public int panelScreenY(int panel) {
		return this.panelScreenY[panel];
	}

	/** The panel over window point (x, y), or VIEWPORT. In fixed mode the frame is not the viewport, but it is only ever asked in resizable. */
	public int areaAt(int x, int y) {
		for (int p = 0; p < PANELS; p++) {
			if (x >= this.panelScreenX[p] && x < this.panelScreenX[p] + PANEL_W[p] && y >= this.panelScreenY[p] && y < this.panelScreenY[p] + PANEL_H[p]) {
				return p;
			}
		}
		return VIEWPORT;
	}

	/**
	 * Window point (x, y) in the mouse's coordinates, as seen by area (a panel, VIEWPORT, or ANY for
	 * whatever is under it). Naming the area is how an open menu or a drag keeps the mouse: its
	 * coordinates carry on smoothly past the edge of the panel it started in rather than jumping.
	 * (-1, -1), the mouse outside the window, stays (-1, -1).
	 */
	public int mapX(int x, int y, int area) {
		if (x == -1 && y == -1) {
			return -1;
		}
		if (area == ANY) {
			area = this.areaAt(x, y);
		}
		if (area == VIEWPORT) {
			return x - this.vpScreenX + this.vpX;
		}
		return x - this.panelScreenX[area] + PANEL_X[area];
	}

	public int mapY(int x, int y, int area) {
		if (x == -1 && y == -1) {
			return -1;
		}
		if (area == ANY) {
			area = this.areaAt(x, y);
		}
		if (area == VIEWPORT) {
			return y - this.vpScreenY + this.vpY;
		}
		return y - this.panelScreenY[area] + PANEL_Y[area];
	}

	/** Is mouse point (x, y) inside the viewport? In fixed mode, 377's own 4 < x < 516, 4 < y < 338. */
	public boolean inViewport(int x, int y) {
		return x > this.vpX && y > this.vpY && x < this.vpX + this.vpW && y < this.vpY + this.vpH;
	}
}
