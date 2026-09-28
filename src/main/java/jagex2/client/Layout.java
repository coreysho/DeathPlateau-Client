package jagex2.client;

/**
 * Where the game sits in the window: the fixed 765x503 frame, or one of the two resizable layouts.
 *
 * FIXED is 377's screen, and every number in here reproduces it exactly - a Layout.fixed() maps
 * every point to itself and changes nothing about what is drawn where.
 *
 * CLASSIC is Old School's "resizable - classic layout". The 3D viewport is the whole window and the
 * three panels of the fixed frame are laid over it at their normal pixel size: the chatbox
 * bottom-left, the minimap top-right and the side tabs bottom-right. Each panel is a rectangle cut
 * straight out of the fixed frame - the client still draws that frame exactly as it always did,
 * into a 765x503 buffer, and the panels are copied from there to where the window has room for
 * them. So the sidebar, the chat, the tabs and the minimap are the same code at the same
 * coordinates in every mode, and nothing inside them had to learn about the window. The rectangles
 * are the panels only, without the screen's stone around them (see PANEL_RECT below).
 *
 * MODERN is Old School's "resizable - modern layout", the same three panels rearranged into five.
 * The side column is dealt out the other way up: Old School stacks the two rows of tabs together at
 * the very bottom-right, with the open tab's panel sitting above them, where classic keeps a row of
 * tabs above the panel and a row below it. So the side tabs are three rectangles here - the top tab
 * row, the panel, the bottom tab row - placed bottom row, top row, panel, upwards from the corner.
 * The panels are also drawn with some of the scene showing through them (panelAlpha), which is what
 * Old School's modern layout does and its classic one does not.
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

	/** The three display modes, in the order the F9 row steps through them. */
	public static final int FIXED = 0;
	public static final int CLASSIC = 1;
	public static final int MODERN = 2;
	public static final int MODES = 3;

	public static String modeName(int mode) {
		return mode == MODERN ? "Resizable - modern layout" : mode == CLASSIC ? "Resizable - classic layout" : "Fixed";
	}

	/**
	 * The panels. CHAT, MINIMAP and SIDEBAR are the three every mode has; TABS_TOP and TABS_BOTTOM
	 * are the two rows of tab buttons, which only modern moves away from the panel (in fixed and
	 * classic they are part of SIDEBAR and these two are not used - the panels field says how many).
	 */
	public static final int CHAT = 0;
	public static final int MINIMAP = 1;
	public static final int SIDEBAR = 2;
	public static final int TABS_TOP = 3;
	public static final int TABS_BOTTOM = 4;
	/** The most panels any mode has; the panels field is how many THIS layout has. */
	public static final int PANELS = 5;
	public static final int VIEWPORT = -1;
	/** mapX/mapY: whatever is under the point. */
	public static final int ANY = -2;

	/** Fully opaque, for panelAlpha. */
	public static final int OPAQUE = 256;

	// The panels as rectangles of the fixed frame - the CONTENT of each one, with the frame's stone
	// left behind. The fixed screen is a single 765x503 slab of decorated stone with the panels set
	// into it; lifting a panel out with its share of that stone is what gave resizable mode borders
	// Old School's classic layout does not have, so each rectangle is cut back to the panel itself:
	//
	//  - the chatbox: the parchment (the "chatback" sprite at 0,338) and the button row under it,
	//    519x165, which is the whole of it - the parchment's own dark edge is the chatbox's frame,
	//    the one Old School draws too, not the screen's.
	//  - the minimap: areaMapback alone, the 172x156 map with its ring and compass at (545,4). The
	//    29px of stone to its left, the 48 to its right and the 4 above it are the screen's.
	//  - the side tabs: from the top tab row's first stone (522,168) to the bottom row's last
	//    (763,503) - interface 548's own tab edges, the ones handleTabInput() hit-tests. That drops
	//    the 8px strip of claws above the top row (the bottom of the minimap's frame) and the thin
	//    stone margins either side. The pillars down the sides of the inventory stay: they are part
	//    of the panel, and Old School's classic layout has them too.
	//
	// Modern splits the side tabs into their three pieces at the same x: the top row (522,168) and
	// the bottom row (522,466), 37 tall each - interface 548's two rows of seven - with the panel
	// between them at (522,205). Laid end to end they are the classic rectangle exactly, which is
	// why modern needs no art the fixed frame does not already draw.
	private static final int[][] PANEL_RECT = {
		{ 0, 338, 519, 165 },      // CHAT: the parchment and the button row
		{ 545, 4, 172, 156 },      // MINIMAP: areaMapback
		{ 522, 168, 241, 335 },    // SIDEBAR: both tab rows and the panel between them
		{ 522, 168, 241, 37 },     // TABS_TOP: the top row of seven (modern)
		{ 522, 466, 241, 37 },     // TABS_BOTTOM: the bottom row of seven (modern)
	};
	/** SIDEBAR in modern: the open tab's panel on its own, without the rows. */
	private static final int[] MODERN_SIDEBAR = { 522, 205, 241, 261 };

	/**
	 * How much of each panel is drawn in modern, out of 256. The art in the 377 cache is opaque and
	 * was drawn for the fixed frame, so this is the whole panel faded rather than Old School's own
	 * semi-transparent backgrounds - see the note on presentGame(). The minimap stays solid, as Old
	 * School's does; the chatbox and the panel let a quarter of the scene through, enough to see it
	 * moving behind them without costing the text any legibility; the tab rows are nearly solid
	 * because they are what the mouse aims at.
	 */
	private static final int[] MODERN_ALPHA = { 192, OPAQUE, 192, 232, 232 };

	/** FIXED, CLASSIC or MODERN. */
	public final int mode;
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
	 * messages, the system update timer - anchors to a corner of this. The right-hand column and the
	 * chatbox are the same size in both resizable layouts, so this is the same in both and those
	 * things keep clear of the panels either way.
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
	/** How many of the PANELS this layout uses: three, or five in modern. */
	public final int panels;

	private final int[] panelX = new int[PANELS];
	private final int[] panelY = new int[PANELS];
	private final int[] panelW = new int[PANELS];
	private final int[] panelH = new int[PANELS];
	private final int[] panelScreenX = new int[PANELS];
	private final int[] panelScreenY = new int[PANELS];
	private final int[] panelAlpha = new int[PANELS];

	private Layout(int mode, int width, int height) {
		this.mode = mode;
		this.resizable = mode != FIXED;
		this.width = width;
		this.height = height;
		this.panels = mode == MODERN ? PANELS : 3;
		for (int p = 0; p < PANELS; p++) {
			this.panelX[p] = PANEL_RECT[p][0];
			this.panelY[p] = PANEL_RECT[p][1];
			this.panelW[p] = PANEL_RECT[p][2];
			this.panelH[p] = PANEL_RECT[p][3];
			this.panelAlpha[p] = mode == MODERN ? MODERN_ALPHA[p] : OPAQUE;
		}
		if (mode == MODERN) {
			this.panelX[SIDEBAR] = MODERN_SIDEBAR[0];
			this.panelY[SIDEBAR] = MODERN_SIDEBAR[1];
			this.panelW[SIDEBAR] = MODERN_SIDEBAR[2];
			this.panelH[SIDEBAR] = MODERN_SIDEBAR[3];
		}
		if (mode == FIXED) {
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
				this.panelScreenX[p] = this.panelX[p];
				this.panelScreenY[p] = this.panelY[p];
			}
			return;
		}
		this.vpX = 0;
		this.vpY = FAR_Y;
		this.vpW = width;
		this.vpH = height;
		this.vpScreenX = 0;
		this.vpScreenY = 0;
		this.openW = width - PANEL_RECT[SIDEBAR][2];
		this.openH = height - PANEL_RECT[CHAT][3];
		this.mainX = Math.max(0, (this.openW - VIEWPORT_W) / 2);
		this.mainY = Math.max(0, (this.openH - VIEWPORT_H) / 2);
		this.letterX = (width - FIXED_W) / 2;
		this.letterY = (height - FIXED_H) / 2;
		this.zoom = zoomFor(this.openH);
		this.panelScreenX[CHAT] = 0;
		this.panelScreenY[CHAT] = height - this.panelH[CHAT];
		this.panelScreenX[MINIMAP] = width - this.panelW[MINIMAP];
		this.panelScreenY[MINIMAP] = 0;
		int right = width - PANEL_RECT[SIDEBAR][2];
		if (mode == MODERN) {
			// up from the bottom-right corner: the bottom row of tabs, the top row, then the panel
			this.panelScreenX[TABS_BOTTOM] = right;
			this.panelScreenY[TABS_BOTTOM] = height - this.panelH[TABS_BOTTOM];
			this.panelScreenX[TABS_TOP] = right;
			this.panelScreenY[TABS_TOP] = this.panelScreenY[TABS_BOTTOM] - this.panelH[TABS_TOP];
			this.panelScreenX[SIDEBAR] = right;
			this.panelScreenY[SIDEBAR] = this.panelScreenY[TABS_TOP] - this.panelH[SIDEBAR];
		} else {
			this.panelScreenX[SIDEBAR] = right;
			this.panelScreenY[SIDEBAR] = height - this.panelH[SIDEBAR];
		}
	}

	public static Layout fixed() {
		return new Layout(FIXED, FIXED_W, FIXED_H);
	}

	/** A window smaller than the fixed frame is laid out as the fixed frame (and clipped). */
	public static Layout resizable(int width, int height) {
		return resizable(CLASSIC, width, height);
	}

	public static Layout resizable(int mode, int width, int height) {
		return new Layout(mode == MODERN ? MODERN : CLASSIC, Math.max(width, MIN_W), Math.max(height, MIN_H));
	}

	public static Layout of(int mode, int width, int height) {
		return mode == FIXED ? fixed() : resizable(mode, width, height);
	}

	/**
	 * The projection scale for an open area openH tall. Growing it with the square root of the
	 * height splits the difference between the two things a bigger window could mean: the world
	 * drawn bigger (the zoom rising with the height, as Old School does) and more of the world in
	 * view (the zoom left at 512). A 1080p window shows the world about 1.65x the fixed size with
	 * about 1.65x as much of it in each direction, which keeps the edge of the 25-tile scene out of
	 * sight at the usual camera angles - and a player who wants more world than that raises the draw
	 * distance in the F9 panel (World3D.drawDistance). Never below the fixed 512.
	 */
	public static int zoomFor(int openH) {
		if (openH <= VIEWPORT_H) {
			return 512;
		}
		return (int) Math.round(512.0 * Math.sqrt(openH / (double) VIEWPORT_H));
	}

	public boolean sameAs(Layout other) {
		return other != null && other.mode == this.mode && other.width == this.width && other.height == this.height;
	}

	public int panelFixedX(int panel) {
		return this.panelX[panel];
	}

	public int panelFixedY(int panel) {
		return this.panelY[panel];
	}

	public int panelWidth(int panel) {
		return this.panelW[panel];
	}

	public int panelHeight(int panel) {
		return this.panelH[panel];
	}

	/** Out of 256: how much of the panel is drawn, the rest being the scene behind it. */
	public int panelAlpha(int panel) {
		return this.panelAlpha[panel];
	}

	public int panelScreenX(int panel) {
		return this.panelScreenX[panel];
	}

	public int panelScreenY(int panel) {
		return this.panelScreenY[panel];
	}

	/** The panel over window point (x, y), or VIEWPORT. In fixed mode the frame is not the viewport, but it is only ever asked in resizable. */
	public int areaAt(int x, int y) {
		for (int p = 0; p < this.panels; p++) {
			if (x >= this.panelScreenX[p] && x < this.panelScreenX[p] + this.panelW[p] && y >= this.panelScreenY[p] && y < this.panelScreenY[p] + this.panelH[p]) {
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
		return x - this.panelScreenX[area] + this.panelX[area];
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
		return y - this.panelScreenY[area] + this.panelY[area];
	}

	/** Is mouse point (x, y) inside the viewport? In fixed mode, 377's own 4 < x < 516, 4 < y < 338. */
	public boolean inViewport(int x, int y) {
		return x > this.vpX && y > this.vpY && x < this.vpX + this.vpW && y < this.vpY + this.vpH;
	}
}
