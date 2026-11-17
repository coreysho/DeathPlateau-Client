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

	/**
	 * The HUD scale steps, as percentages of 377's own pixel size. 100 is the client as it always
	 * was. Nearest-neighbour doubling is what draws them (Client.scaleRect), so these are chunky
	 * rather than redrawn: the art in the cache has no higher-resolution version, and sharpening it
	 * would mean new sprites, not a bigger blit.
	 */
	public static final int[] SCALES = { 100, 125, 150, 200 };
	public static final int SCALE_FIXED = 100;

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
	/**
	 * The row of buttons under the chatbox - All, Game, Public, Private, Clan, Trade, Report abuse.
	 * Its own panel so that the parchment above it can be folded away and this stays, which is the
	 * only thing that makes folding reversible. Index 1 and not the end of the table: the panel
	 * loop runs 0 to panels-1, so what a layout uses has to come first.
	 */
	public static final int CHAT_BAR = 1;
	public static final int MINIMAP = 2;
	public static final int SIDEBAR = 3;
	public static final int TABS_TOP = 4;
	public static final int TABS_BOTTOM = 5;
	/** The most panels any mode has; the panels field is how many THIS layout has. */
	public static final int PANELS = 6;
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
		{ 0, 338, 519, 142 },      // CHAT: the parchment, down to the buttons
		{ 0, 480, 519, 23 },       // CHAT_BAR: the row of buttons under it
		{ 545, 4, 172, 156 },      // MINIMAP: areaMapback
		{ 522, 168, 241, 335 },    // SIDEBAR: both tab rows and the panel between them
		{ 522, 168, 241, 37 },     // TABS_TOP: the top row of seven (modern)
		{ 522, 466, 241, 37 },     // TABS_BOTTOM: the bottom row of seven (modern)
	};
	/** SIDEBAR in modern: the open tab's panel on its own, without the rows. */
	private static final int[] MODERN_SIDEBAR = { 522, 205, 241, 261 };

	/**
	 * How much of each panel is drawn in modern, out of 256 - SOLID, every one of them (owner, 2026-09-28,
	 * looking at it in game: "the modern client layout has an opacity to it").
	 *
	 * Old School fades only the BACKGROUND of a panel and leaves its icons and text solid, because its
	 * panel art carries its own per-pixel transparency. The art in the 377 cache is opaque everywhere,
	 * so anything less than solid here fades the whole panel, writing and icons included - which is what
	 * the owner saw. Faithful transparency needs Old School's panel sprites imported into the cache (new
	 * sprites, new ids, a cache rebuild); until that happens modern is the full-window viewport with the
	 * panels moved, and nothing is washed out. blendRect and panelAlpha stay, so it is one table away.
	 */
	private static final int[] MODERN_ALPHA = { OPAQUE, OPAQUE, OPAQUE, OPAQUE, OPAQUE, OPAQUE };

	/** FIXED, CLASSIC or MODERN. */
	public final int mode;
	public final boolean resizable;
	/**
	 * The HUD scale actually in use, as a percentage - what the player asked for, or the largest
	 * step that fits this window (fit). Always SCALE_FIXED in the fixed layout, which is 377's
	 * screen and not ours to stretch.
	 */
	public final int scale;
	/**
	 * The open tab's panel is folded away, leaving the two rows of tabs. Modern only: classic keeps
	 * its tabs inside the rectangle this would hide, so there would be nothing left to click.
	 */
	public final boolean sidebarHidden;
	/** The chatbox parchment is folded away, leaving its row of buttons. Either resizable layout. */
	public final boolean chatHidden;
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
	/** The size the panel lands at: panelW/panelH times the scale. */
	private final int[] panelScreenW = new int[PANELS];
	private final int[] panelScreenH = new int[PANELS];
	private final int[] panelAlpha = new int[PANELS];

	private Layout(int mode, int width, int height, int scale, boolean hideSidebar, boolean hideChat) {
		this.mode = mode;
		this.resizable = mode != FIXED;
		this.width = width;
		this.height = height;
		this.scale = mode == FIXED ? SCALE_FIXED : scale;
		this.sidebarHidden = hideSidebar && mode == MODERN;
		this.chatHidden = hideChat && mode != FIXED;
		this.panels = mode == MODERN ? PANELS : 4;
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
		for (int p = 0; p < PANELS; p++) {
			this.panelScreenW[p] = scaled(this.panelW[p], this.scale);
			this.panelScreenH[p] = scaled(this.panelH[p], this.scale);
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
		// The column and the chatbox at their LANDED size, so a scaled HUD takes its room out of
		// the open area the same way the unscaled one does. openW/openH are what every corner
		// thing anchors to (the XP drops, the multicombat icon), so they move in with the panels.
		this.openW = width - scaled(PANEL_RECT[SIDEBAR][2], this.scale);
		// The chatbox gives up the parchment AND the bar while it is showing, and only the bar
		// when it is folded - so what hangs off the bottom of the open area moves down into the
		// room the chatbox was taking. Summed from the landed sizes rather than scaled again:
		// scaling a sum and summing two scaled numbers disagree by a pixel.
		this.openH = height - (this.chatHidden
			? scaled(PANEL_RECT[CHAT_BAR][3], this.scale)
			: scaled(PANEL_RECT[CHAT][3], this.scale) + scaled(PANEL_RECT[CHAT_BAR][3], this.scale));
		this.mainX = Math.max(0, (this.openW - VIEWPORT_W) / 2);
		this.mainY = Math.max(0, (this.openH - VIEWPORT_H) / 2);
		this.letterX = (width - FIXED_W) / 2;
		this.letterY = (height - FIXED_H) / 2;
		this.zoom = zoomFor(this.openH);
		// The bar sits on the bottom edge and the parchment stacks on top of it, so folding the
		// parchment leaves the bar where it was rather than sliding it down the window.
		this.panelScreenX[CHAT_BAR] = 0;
		this.panelScreenY[CHAT_BAR] = height - this.panelScreenH[CHAT_BAR];
		this.panelScreenX[CHAT] = 0;
		this.panelScreenY[CHAT] = this.panelScreenY[CHAT_BAR] - this.panelScreenH[CHAT];
		this.panelScreenX[MINIMAP] = width - this.panelScreenW[MINIMAP];
		this.panelScreenY[MINIMAP] = 0;
		int right = width - scaled(PANEL_RECT[SIDEBAR][2], this.scale);
		if (mode == MODERN) {
			// up from the bottom-right corner: the bottom row of tabs, the top row, then the panel
			this.panelScreenX[TABS_BOTTOM] = right;
			this.panelScreenY[TABS_BOTTOM] = height - this.panelScreenH[TABS_BOTTOM];
			this.panelScreenX[TABS_TOP] = right;
			this.panelScreenY[TABS_TOP] = this.panelScreenY[TABS_BOTTOM] - this.panelScreenH[TABS_TOP];
			this.panelScreenX[SIDEBAR] = right;
			this.panelScreenY[SIDEBAR] = this.panelScreenY[TABS_TOP] - this.panelScreenH[SIDEBAR];
		} else {
			this.panelScreenX[SIDEBAR] = right;
			this.panelScreenY[SIDEBAR] = height - this.panelScreenH[SIDEBAR];
		}
	}

	/**
	 * A fixed-frame length at the HUD scale, ROUNDED UP.
	 *
	 * Up and not down because the panel's last row and column have to be drawn. The blit reads
	 * source offset i*100/scale for each destination offset i, so a destination that is a pixel
	 * short stops one source pixel short as well: at 125% a rounded-down chatbox is 648 wide and
	 * its 519th column - the parchment's own right-hand edge - is never reached, and the same
	 * arithmetic run backwards in unscaled() leaves the last column of the side tabs unclickable.
	 * Rounding up costs at most one pixel of window and makes the two ends line up exactly, which
	 * the layout test checks for every panel at every scale.
	 */
	private static int scaled(int v, int scale) {
		return scale == SCALE_FIXED ? v : (v * scale + 99) / 100;
	}

	public static Layout fixed() {
		return new Layout(FIXED, FIXED_W, FIXED_H, SCALE_FIXED, false, false);
	}

	/** A window smaller than the fixed frame is laid out as the fixed frame (and clipped). */
	public static Layout resizable(int width, int height) {
		return resizable(CLASSIC, width, height);
	}

	public static Layout resizable(int mode, int width, int height) {
		return resizable(mode, width, height, SCALE_FIXED);
	}

	public static Layout resizable(int mode, int width, int height, int scale) {
		return resizable(mode, width, height, scale, false, false);
	}

	public static Layout resizable(int mode, int width, int height, int scale, boolean hideSidebar) {
		return resizable(mode, width, height, scale, hideSidebar, false);
	}

	public static Layout resizable(int mode, int width, int height, int scale, boolean hideSidebar, boolean hideChat) {
		int w = Math.max(width, MIN_W);
		int h = Math.max(height, MIN_H);
		return new Layout(mode == MODERN ? MODERN : CLASSIC, w, h, fit(scale, w, h), hideSidebar, hideChat);
	}

	public static Layout of(int mode, int width, int height) {
		return of(mode, width, height, SCALE_FIXED);
	}

	public static Layout of(int mode, int width, int height, int scale) {
		return mode == FIXED ? fixed() : resizable(mode, width, height, scale);
	}

	/**
	 * The largest step no bigger than `scale` whose panels still fit a width x height window.
	 *
	 * Two rules, which are the two ways scaled panels run into each other: the chatbox and the
	 * right-hand column share the bottom edge, and the minimap sits on top of that column. Nothing
	 * else can collide - the chatbox is left of the column by the first rule, and what is left over
	 * is the open area, which the first two rules keep at 519x326 or better whatever the scale.
	 *
	 * An unknown scale (a hand-edited properties file, an older build's number) comes back as the
	 * nearest step at or below it, and anything under the smallest is 100.
	 */
	public static int fit(int scale, int width, int height) {
		int best = SCALE_FIXED;
		for (int i = 0; i < SCALES.length; i++) {
			int s = SCALES[i];
			if (s > scale) {
				continue;
			}
			if (scaled(PANEL_RECT[CHAT][2] + PANEL_RECT[SIDEBAR][2], s) > width) {
				continue;
			}
			if (scaled(PANEL_RECT[MINIMAP][3] + PANEL_RECT[SIDEBAR][3], s) > height) {
				continue;
			}
			if (s > best) {
				best = s;
			}
		}
		return best;
	}

	/** The next step round the SCALES ring, for a control that steps rather than slides. */
	public static int nextScale(int scale) {
		for (int i = 0; i < SCALES.length; i++) {
			if (SCALES[i] == scale) {
				return SCALES[(i + 1) % SCALES.length];
			}
		}
		return SCALES[0];
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
		return other != null && other.mode == this.mode && other.width == this.width && other.height == this.height
			&& other.scale == this.scale && other.sidebarHidden == this.sidebarHidden
			&& other.chatHidden == this.chatHidden;
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

	/** True when this panel is folded away and should be neither drawn nor clicked. */
	public boolean hiddenPanel(int panel) {
		return (this.sidebarHidden && panel == SIDEBAR) || (this.chatHidden && panel == CHAT);
	}

	/** How wide the panel lands on the window: panelWidth at the HUD scale. */
	public int panelScreenWidth(int panel) {
		return this.panelScreenW[panel];
	}

	/** How tall the panel lands on the window: panelHeight at the HUD scale. */
	public int panelScreenHeight(int panel) {
		return this.panelScreenH[panel];
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
			// A folded panel is not there to be clicked: the point belongs to the scene behind it.
			if (this.hiddenPanel(p)) {
				continue;
			}
			if (x >= this.panelScreenX[p] && x < this.panelScreenX[p] + this.panelScreenW[p] && y >= this.panelScreenY[p] && y < this.panelScreenY[p] + this.panelScreenH[p]) {
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
		return unscaled(x - this.panelScreenX[area]) + this.panelX[area];
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
		return unscaled(y - this.panelScreenY[area]) + this.panelY[area];
	}

	/**
	 * A length on the window back to the fixed frame length it was drawn from - the inverse of the
	 * blit, and the whole reason a scaled HUD needs nothing else to change. Negative offsets happen
	 * (a menu or a drag that has left its panel keeps counting past the edge), and integer division
	 * truncates towards zero rather than down, so those are divided away from zero by hand: without
	 * that, the first scaled pixel outside a panel's left edge maps to 0, the same as the first
	 * pixel inside it, and a drag stalls for one pixel before it starts moving.
	 */
	private int unscaled(int v) {
		if (this.scale == SCALE_FIXED) {
			return v;
		}
		return v >= 0 ? v * 100 / this.scale : -((-v * 100 + this.scale - 1) / this.scale);
	}

	/** Is mouse point (x, y) inside the viewport? In fixed mode, 377's own 4 < x < 516, 4 < y < 338. */
	public boolean inViewport(int x, int y) {
		return x > this.vpX && y > this.vpY && x < this.vpX + this.vpW && y < this.vpY + this.vpH;
	}
}
