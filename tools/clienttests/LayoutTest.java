// The geometry of resizable mode (jagex2.client.Layout), checked point by point. See run_layouttest.py.
import jagex2.client.Layout;

public class LayoutTest {

	static int pass;
	static int fail;

	static void check(boolean ok, String what) {
		if (ok) {
			pass++;
			System.out.println("  ok   " + what);
		} else {
			fail++;
			System.out.println("FAIL   " + what);
		}
	}

	static final int[][] SIZES = { { 765, 503 }, { 1024, 768 }, { 1280, 800 }, { 1920, 1080 }, { 2560, 1440 }, { 3840, 2160 } };

	public static void main(String[] args) {
		System.out.println("1. the fixed screen is 377's, point for point");
		Layout f = Layout.fixed();
		int moved = 0;
		int vpMismatch = 0;
		for (int y = 0; y < 503; y++) {
			for (int x = 0; x < 765; x++) {
				if (f.mapX(x, y, Layout.ANY) != x || f.mapY(x, y, Layout.ANY) != y) {
					moved++;
				}
				boolean old = x > 4 && y > 4 && x < 516 && y < 338;
				if (f.inViewport(x, y) != old) {
					vpMismatch++;
				}
			}
		}
		check(moved == 0, "every window point is its own mouse point (" + moved + " moved)");
		check(vpMismatch == 0, "inViewport is 377's 4 < x < 516, 4 < y < 338 everywhere (" + vpMismatch + " differ)");
		check(f.vpX == 4 && f.vpY == 4 && f.vpW == 512 && f.vpH == 334, "the viewport is 512x334 at (4,4)");
		check(f.mainX == 0 && f.mainY == 0 && f.openW == 512 && f.openH == 334 && f.zoom == 512,
			"main interfaces at the viewport's corner, the corners where they were, the projection 512");
		check(f.mode == Layout.FIXED && !f.resizable && f.panels == Layout.PANELS,
			"the fixed screen is FIXED, and names all " + Layout.PANELS + " panels");

		for (int mode = Layout.CLASSIC; mode <= Layout.MODERN; mode++) {
			String name = Layout.modeName(mode);
			System.out.println();
			System.out.println(name);
			System.out.println("2. every window point belongs to exactly one thing");
			for (int[] s : SIZES) {
				Layout r = Layout.resizable(mode, s[0], s[1]);
				int both = 0;
				int none = 0;
				int badPanel = 0;
				for (int y = 0; y < r.height; y += 3) {
					for (int x = 0; x < r.width; x += 3) {
						int mx = r.mapX(x, y, Layout.ANY);
						int my = r.mapY(x, y, Layout.ANY);
						int area = r.areaAt(x, y);
						boolean vp = r.inViewport(mx, my);
						boolean panel = area != Layout.VIEWPORT;
						if (vp && panel) {
							both++;
						}
						if (panel) {
							// a panel point lands inside that panel's rectangle of the fixed frame
							int px = r.panelFixedX(area);
							int py = r.panelFixedY(area);
							if (mx < px || my < py || mx >= px + r.panelWidth(area) || my >= py + r.panelHeight(area)) {
								badPanel++;
							}
						} else if (!vp && x > 0 && y > 0) {
							none++;
						} else if (vp && (mx - r.vpX != x || my - r.vpY != y)) {
							badPanel++;
						}
					}
				}
				check(both == 0 && none == 0 && badPanel == 0, s[0] + "x" + s[1] + ": no point is both viewport and panel (" + both
					+ "), none is neither (" + none + "), each maps to its own pixel (" + badPanel + " wrong)");
			}

			System.out.println("3. the panels sit in this layout's corners, and never on each other");
			Layout r = Layout.resizable(mode, 1280, 800);
			// The BAR is what sits on the bottom edge now; the parchment stacks on top of it, so
			// folding the parchment leaves the buttons where they were.
			check(r.panelScreenX(Layout.CHAT_BAR) == 0
					&& r.panelScreenY(Layout.CHAT_BAR) + r.panelHeight(Layout.CHAT_BAR) == 800
					&& r.panelScreenY(Layout.CHAT_INPUT) + r.panelHeight(Layout.CHAT_INPUT) == r.panelScreenY(Layout.CHAT_BAR)
					&& r.panelScreenX(Layout.CHAT) == 0
					&& r.panelScreenY(Layout.CHAT) + r.panelHeight(Layout.CHAT) == r.panelScreenY(Layout.CHAT_INPUT),
				"the chatbox in the bottom-left corner: buttons on the edge, the line you type on above them, the log above that");
			check(r.panelScreenX(Layout.MINIMAP) + r.panelWidth(Layout.MINIMAP) == 1280 && r.panelScreenY(Layout.MINIMAP) == 0,
				"the minimap in the top-right corner");
			// The bottom row is on the edge in BOTH layouts now; what differs is what is above it.
			check(r.panelScreenX(Layout.TABS_BOTTOM) + r.panelWidth(Layout.TABS_BOTTOM) == 1280
				&& r.panelScreenY(Layout.TABS_BOTTOM) + r.panelHeight(Layout.TABS_BOTTOM) == 800,
				"the side tabs in the bottom-right corner");
			int above = mode == Layout.MODERN ? Layout.TABS_TOP : Layout.SIDEBAR;
			check(r.panelScreenY(above) + r.panelHeight(above) == r.panelScreenY(Layout.TABS_BOTTOM),
				mode == Layout.MODERN ? "with the other row of tabs directly above it"
					: "with the open tab's panel directly above it");
			check(r.vpW == 1280 && r.vpH == 800 && r.vpScreenX == 0 && r.vpScreenY == 0, "the viewport is the whole window");
			check(r.mainX >= 0 && r.mainX + 512 <= r.openW && r.mainY >= 0 && r.mainY + 334 <= r.openH
				&& Math.abs(r.mainX - (r.openW - 512 - r.mainX)) <= 1 && Math.abs(r.mainY - (r.openH - 334 - r.mainY)) <= 1,
				"a main interface is centred in the open area, clear of the panels: " + r.mainX + "," + r.mainY);
			Layout m = Layout.resizable(mode, 765, 503);
			int overlaps = 0;
			for (int a = 0; a < m.panels; a++) {
				for (int b = a + 1; b < m.panels; b++) {
					if (m.panelScreenX(a) < m.panelScreenX(b) + m.panelWidth(b)
						&& m.panelScreenX(b) < m.panelScreenX(a) + m.panelWidth(a)
						&& m.panelScreenY(a) < m.panelScreenY(b) + m.panelHeight(b)
						&& m.panelScreenY(b) < m.panelScreenY(a) + m.panelHeight(a)) {
						overlaps++;
					}
				}
			}
			check(overlaps == 0, "at the smallest window the panels still do not overlap");

			System.out.println("4. each panel is the panel, without the fixed screen's stone around it");
			// Every panel rectangle is inside the 765x503 frame and clear of its 512x334 viewport, so no
			// panel ever carries a copy of the scene; and each is the panel's own art, no wider.
			// THE SAME SEVEN IN BOTH LAYOUTS. All that separates classic from modern now is which
			// way up the side column is stacked, which is all it ever was.
			int[][] want = { { 0, 338, 519, 117 }, { 0, 455, 519, 25 }, { 0, 480, 519, 23 },
				{ 545, 4, 172, 156 }, { 547, 205, 190, 261 }, { 522, 168, 241, 37 }, { 522, 466, 241, 37 } };
			String[] named = { "the chatbox: the log alone", "the chat input: the line you type on",
				"the chat bar: the row of buttons under it", "the minimap: areaMapback alone",
				"the open tab's panel, without the screen's pillars",
				"the top row of seven tabs", "the bottom row of seven tabs" };
			check(r.panels == want.length, "this layout has " + r.panels + " panels");
			for (int p = 0; p < r.panels; p++) {
				int px = r.panelFixedX(p);
				int py = r.panelFixedY(p);
				int pw = r.panelWidth(p);
				int ph = r.panelHeight(p);
				boolean inFrame = px >= 0 && py >= 0 && px + pw <= 765 && py + ph <= 503;
				boolean clearOfScene = px >= 516 || py >= 338;
				boolean exact = px == want[p][0] && py == want[p][1] && pw == want[p][2] && ph == want[p][3];
				check(inFrame && clearOfScene && exact, named[p] + " is " + pw + "x" + ph + " at (" + px + "," + py + ")");
			}

			System.out.println("5. an open menu keeps the mouse");
			int sx = r.panelScreenX(Layout.SIDEBAR) + 20;
			int sy = r.panelScreenY(Layout.SIDEBAR) + 100;
			check(r.inViewport(r.mapX(sx, sy, Layout.VIEWPORT), r.mapY(sx, sy, Layout.VIEWPORT)),
				"a viewport menu hanging over the side panel still sees the mouse there as viewport");
			check(r.mapX(sx - 30, sy, Layout.SIDEBAR) == r.mapX(sx, sy, Layout.SIDEBAR) - 30,
				"a sidebar menu follows the mouse smoothly off the panel's edge");
			check(r.mapX(-1, -1, Layout.ANY) == -1 && r.mapY(-1, -1, Layout.ANY) == -1, "the mouse outside the window stays (-1, -1)");
		}

		System.out.println();
		System.out.println("6. the tab rows modern stacks are the same two rows classic keeps in its panel");
		Layout c = Layout.resizable(Layout.CLASSIC, 1920, 1080);
		Layout md = Layout.resizable(Layout.MODERN, 1920, 1080);
		// The two layouts name the SAME rectangles now and differ only in where they put them,
		// which is the whole of the parity change - and is what lets classic fold its panel and
		// lose the fixed screen's pillars, neither of which it could do as one rectangle.
		int differs = 0;
		for (int p = 0; p < md.panels; p++) {
			if (md.panelFixedX(p) != c.panelFixedX(p) || md.panelFixedY(p) != c.panelFixedY(p)
				|| md.panelWidth(p) != c.panelWidth(p) || md.panelHeight(p) != c.panelHeight(p)) {
				differs++;
			}
		}
		check(differs == 0, "classic and modern cut the same " + md.panels + " rectangles out of the frame (" + differs + " differ)");
		boolean order = md.panelScreenY(Layout.SIDEBAR) < md.panelScreenY(Layout.TABS_TOP)
			&& md.panelScreenY(Layout.TABS_TOP) < md.panelScreenY(Layout.TABS_BOTTOM);
		check(order, "and on screen they run panel, top row, bottom row, downwards");
		boolean solid = true;
		for (int p = 0; p < Layout.PANELS; p++) {
			solid &= md.panelAlpha(p) == Layout.OPAQUE && c.panelAlpha(p) == Layout.OPAQUE
				&& Layout.fixed().panelAlpha(p) == Layout.OPAQUE;
		}
		// Fading the 377 cache's opaque art fades a panel's writing and icons with it, so every panel is
		// solid in every mode until Old School's own part-transparent panel sprites are in the cache
		// (Layout.MODERN_ALPHA says so at more length).
		check(solid, "no panel is washed out, in any layout");

		System.out.println();
		System.out.println("7. the projection grows with the window, never below 512");
		int last = 0;
		boolean mono = true;
		for (int[] s : SIZES) {
			int z = Layout.resizable(s[0], s[1]).zoom;
			if (z < last || z < 512) {
				mono = false;
			}
			last = z;
		}
		check(mono, "zoom rises with the window's height: 765x503 " + Layout.resizable(765, 503).zoom + ", 1280x800 "
			+ Layout.resizable(1280, 800).zoom + ", 1920x1080 " + Layout.resizable(1920, 1080).zoom + ", 3840x2160 " + last);
		Layout tiny = Layout.resizable(400, 300);
		check(tiny.width == 765 && tiny.height == 503, "a window smaller than 765x503 is laid out as 765x503");
		check(Layout.resizable(1280, 800).letterX == (1280 - 765) / 2 && Layout.resizable(1280, 800).letterY == (800 - 503) / 2,
			"the title screen is centred");

		System.out.println();
		System.out.println("8. the HUD scale: bigger panels, the same 377 coordinates underneath");
		// 100% IS THE CLIENT AS IT WAS. Not "close to": the same numbers, or every check above this
		// one is testing a layout nobody runs.
		for (int[] s : SIZES) {
			for (int mode = Layout.CLASSIC; mode <= Layout.MODERN; mode++) {
				Layout plain = Layout.resizable(mode, s[0], s[1]);
				Layout one = Layout.resizable(mode, s[0], s[1], Layout.SCALE_FIXED);
				boolean same = plain.openW == one.openW && plain.openH == one.openH && plain.zoom == one.zoom;
				for (int p = 0; p < plain.panels; p++) {
					same &= plain.panelScreenX(p) == one.panelScreenX(p) && plain.panelScreenY(p) == one.panelScreenY(p)
						&& one.panelScreenWidth(p) == one.panelWidth(p) && one.panelScreenHeight(p) == one.panelHeight(p);
				}
				if (!same) {
					check(false, s[0] + "x" + s[1] + " " + Layout.modeName(mode) + ": 100% is not the unscaled layout");
				}
			}
		}
		check(true, "at 100% every layout is the one that was there before, panel for panel");

		int corners = 0;
		int strayed = 0;
		int offEdge = 0;
		int overlaps = 0;
		int unreachable = 0;
		for (int[] s : SIZES) {
			for (int mode = Layout.CLASSIC; mode <= Layout.MODERN; mode++) {
				for (int i = 0; i < Layout.SCALES.length; i++) {
					int want = Layout.SCALES[i];
					Layout r = Layout.resizable(mode, s[0], s[1], want);
					if (r.scale > want) {
						strayed++;
					}
					for (int p = 0; p < r.panels; p++) {
						int px = r.panelScreenX(p);
						int py = r.panelScreenY(p);
						int pw = r.panelScreenWidth(p);
						int ph = r.panelScreenHeight(p);
						// THE TWO ENDS. The panel's first on-screen pixel must be its first pixel in
						// the fixed frame and its last must be its last: a mouse at the far edge of
						// the inventory has to land on the far edge of the inventory, and a column
						// the inverse never reaches is a column no click can ever hit.
						if (r.mapX(px, py, p) != r.panelFixedX(p) || r.mapY(px, py, p) != r.panelFixedY(p)) {
							corners++;
						}
						if (r.mapX(px + pw - 1, py, p) != r.panelFixedX(p) + r.panelWidth(p) - 1
							|| r.mapY(px, py + ph - 1, p) != r.panelFixedY(p) + r.panelHeight(p) - 1) {
							corners++;
						}
						// Every source column and row reachable from somewhere on screen.
						boolean[] seenX = new boolean[r.panelWidth(p)];
						for (int x = 0; x < pw; x++) {
							int src = r.mapX(px + x, py, p) - r.panelFixedX(p);
							if (src < 0 || src >= seenX.length) {
								strayed++;
								continue;
							}
							seenX[src] = true;
						}
						for (int col = 0; col < seenX.length; col++) {
							if (!seenX[col]) {
								unreachable++;
							}
						}
						// On the window, and in its corner.
						if (px < 0 || py < 0 || px + pw > r.width || py + ph > r.height) {
							offEdge++;
						}
						for (int q = p + 1; q < r.panels; q++) {
							int qx = r.panelScreenX(q);
							int qy = r.panelScreenY(q);
							if (px < qx + r.panelScreenWidth(q) && qx < px + pw
								&& py < qy + r.panelScreenHeight(q) && qy < py + ph) {
								overlaps++;
							}
						}
					}
				}
			}
		}
		check(corners == 0, "a panel's first and last pixel are its first and last in the fixed frame (" + corners + " are not)");
		check(unreachable == 0, "no column of a panel is unreachable by the mouse (" + unreachable + " are)");
		check(strayed == 0, "no point on a panel maps outside it, and no window gets a scale it was not asked for (" + strayed + ")");
		check(offEdge == 0, "every scaled panel is inside the window (" + offEdge + " are not)");
		check(overlaps == 0, "no two scaled panels overlap, at any size or scale (" + overlaps + " do)");

		// FIT. The point of it is that a setting can be carried between machines, so what matters is
		// that it never returns something that does not fit and never refuses something that does.
		check(Layout.fit(200, 765, 503) == 100, "the smallest window takes no scale above 100%");
		check(Layout.fit(200, 3840, 2160) == 200, "a 4K window takes the largest step");
		check(Layout.fit(100, 3840, 2160) == 100, "and still gives 100% to somebody who asked for it");
		check(Layout.fit(137, 3840, 2160) == 125, "a number that is not a step comes back as the step below it");
		check(Layout.fit(-5, 1920, 1080) == 100 && Layout.fit(0, 1920, 1080) == 100, "nonsense comes back as 100%");
		boolean monotonic = true;
		int prev = 0;
		for (int[] s : SIZES) {
			int got = Layout.fit(200, s[0], s[1]);
			if (got < prev) {
				monotonic = false;
			}
			prev = got;
		}
		check(monotonic, "a bigger window never allows a smaller scale: 765x503 " + Layout.fit(200, 765, 503)
			+ ", 1280x800 " + Layout.fit(200, 1280, 800) + ", 1920x1080 " + Layout.fit(200, 1920, 1080)
			+ ", 3840x2160 " + Layout.fit(200, 3840, 2160));

		// THE OPEN AREA is what the XP drops and the multicombat icon hang off, so it has to give
		// way as the panels grow - and never to nothing.
		Layout small = Layout.resizable(Layout.CLASSIC, 1920, 1080, 100);
		Layout big = Layout.resizable(Layout.CLASSIC, 1920, 1080, 200);
		check(big.scale == 200 && big.openW < small.openW && big.openH < small.openH && big.openW > 0 && big.openH > 0,
			"the open area gives way to a bigger HUD: 1920x1080 goes from " + small.openW + "x" + small.openH
			+ " to " + big.openW + "x" + big.openH);
		check(big.mainX >= 0 && big.mainY >= 0, "a bank still has somewhere to open");

		// A DRAG THAT LEAVES ITS PANEL keeps counting, and at a scale it has to keep counting
		// smoothly: integer division truncates towards zero, so without unscaled()'s negative arm
		// the first pixel outside the left edge reads the same as the first pixel inside it.
		int dsx = big.panelScreenX(Layout.SIDEBAR);
		int dsy = big.panelScreenY(Layout.SIDEBAR) + 40;
		boolean smooth = true;
		int lastMapped = Integer.MIN_VALUE;
		for (int off = -40; off <= 40; off++) {
			int m = big.mapX(dsx + off, dsy, Layout.SIDEBAR);
			if (m < lastMapped) {
				smooth = false;
			}
			lastMapped = m;
		}
		check(smooth, "a drag off the side panel's edge keeps moving the same way at 200%");
		check(big.mapX(dsx - 2, dsy, Layout.SIDEBAR) < big.mapX(dsx, dsy, Layout.SIDEBAR),
			"and a point outside the panel is outside it, not on its first column");

		System.out.println();
		System.out.println("8b. the chatbox is the two pieces the one rectangle used to be");
		Layout cc = Layout.resizable(Layout.CLASSIC, 1920, 1080, Layout.SCALE_FIXED);
		boolean joined = cc.panelFixedY(Layout.CHAT) == 338
			&& cc.panelFixedY(Layout.CHAT_INPUT) == cc.panelFixedY(Layout.CHAT) + cc.panelHeight(Layout.CHAT)
			&& cc.panelFixedY(Layout.CHAT_BAR) == cc.panelFixedY(Layout.CHAT_INPUT) + cc.panelHeight(Layout.CHAT_INPUT)
			&& cc.panelFixedY(Layout.CHAT_BAR) + cc.panelHeight(Layout.CHAT_BAR) == 503
			&& cc.panelWidth(Layout.CHAT) == cc.panelWidth(Layout.CHAT_BAR)
			&& cc.panelWidth(Layout.CHAT_INPUT) == cc.panelWidth(Layout.CHAT_BAR);
		check(joined, "the log, the input line and the bar laid end to end are the old 519x165 at (0,338)");
		// AND THE COLUMN, the same way: three pieces where classic used to have one.
		boolean column = cc.panelFixedY(Layout.TABS_TOP) == 168
			&& cc.panelFixedY(Layout.SIDEBAR) == cc.panelFixedY(Layout.TABS_TOP) + cc.panelHeight(Layout.TABS_TOP)
			&& cc.panelFixedY(Layout.TABS_BOTTOM) == cc.panelFixedY(Layout.SIDEBAR) + cc.panelHeight(Layout.SIDEBAR)
			&& cc.panelFixedY(Layout.TABS_BOTTOM) + cc.panelHeight(Layout.TABS_BOTTOM) == 503;
		check(column, "and the two tab rows with the panel between them are the old 241x335 at (522,168)");
		check(cc.panelScreenY(Layout.CHAT_BAR) + cc.panelScreenHeight(Layout.CHAT_BAR) == 1080
			&& cc.panelScreenY(Layout.CHAT_INPUT) + cc.panelScreenHeight(Layout.CHAT_INPUT) == cc.panelScreenY(Layout.CHAT_BAR)
			&& cc.panelScreenY(Layout.CHAT) + cc.panelScreenHeight(Layout.CHAT) == cc.panelScreenY(Layout.CHAT_INPUT),
			"on screen the bar is on the bottom edge with the input line and then the log stacked on it");

		System.out.println();
		System.out.println("9. the open tab's panel, folded away");
		Layout mh = Layout.resizable(Layout.MODERN, 1920, 1080, Layout.SCALE_FIXED, true);
		Layout ch = Layout.resizable(Layout.CLASSIC, 1920, 1080, Layout.SCALE_FIXED, true);
		check(mh.sidebarHidden && mh.hiddenPanel(Layout.SIDEBAR), "modern folds the side panel away when asked");
		check(ch.sidebarHidden && ch.hiddenPanel(Layout.SIDEBAR) && !ch.hiddenPanel(Layout.TABS_TOP)
				&& !ch.hiddenPanel(Layout.TABS_BOTTOM),
			"and classic folds it too now, its own tab rows staying behind to bring it back");
		boolean onlySidebar = true;
		for (int p = 0; p < mh.panels; p++) {
			if (p != Layout.SIDEBAR && mh.hiddenPanel(p)) {
				onlySidebar = false;
			}
		}
		check(onlySidebar, "and nothing else goes with it");

		// THE HIT TEST IS THE ONE THAT MATTERS. A panel that is not drawn but still answers the
		// mouse is a strip of window that eats clicks meant for the scene, and it looks like
		// nothing is wrong at all.
		int hx = mh.panelScreenX(Layout.SIDEBAR) + mh.panelScreenWidth(Layout.SIDEBAR) / 2;
		int hy = mh.panelScreenY(Layout.SIDEBAR) + mh.panelScreenHeight(Layout.SIDEBAR) / 2;
		check(mh.areaAt(hx, hy) == Layout.VIEWPORT, "a click where it was goes to the scene behind it");
		Layout ms = Layout.resizable(Layout.MODERN, 1920, 1080, Layout.SCALE_FIXED, false);
		check(ms.areaAt(hx, hy) == Layout.SIDEBAR, "and lands on the panel when it is showing");

		// The way back has to survive, or the fold is one-way.
		int tx = mh.panelScreenX(Layout.TABS_BOTTOM) + 10;
		int ty = mh.panelScreenY(Layout.TABS_BOTTOM) + 10;
		check(mh.areaAt(tx, ty) == Layout.TABS_BOTTOM, "the bottom row of tabs still answers, so it can be brought back");
		check(mh.areaAt(mh.panelScreenX(Layout.TABS_TOP) + 10, mh.panelScreenY(Layout.TABS_TOP) + 10) == Layout.TABS_TOP,
			"and so does the top row");
		check(!mh.sameAs(ms), "a folded layout is not the same layout, so the frame is rebuilt");
		check(mh.openW == ms.openW && mh.openH == ms.openH,
			"the open area is unchanged: the tab rows are as wide as the panel was");

		System.out.println();
		System.out.println("10. the chatbox, folded away");
		for (int mode = Layout.CLASSIC; mode <= Layout.MODERN; mode++) {
			Layout shown = Layout.resizable(mode, 1920, 1080, Layout.SCALE_FIXED, false, false);
			Layout folded = Layout.resizable(mode, 1920, 1080, Layout.SCALE_FIXED, false, true);
			String name = Layout.modeName(mode);
			check(folded.chatHidden && folded.hiddenPanel(Layout.CHAT)
					&& !folded.hiddenPanel(Layout.CHAT_INPUT) && !folded.hiddenPanel(Layout.CHAT_BAR),
				name + ": the log folds, and the line you type on and the buttons do not");
			int cx = shown.panelScreenX(Layout.CHAT) + 20;
			int cy = shown.panelScreenY(Layout.CHAT) + 20;
			check(shown.areaAt(cx, cy) == Layout.CHAT && folded.areaAt(cx, cy) == Layout.VIEWPORT,
				name + ": a click there hits the chatbox, and the scene once it is folded");
			int bx = shown.panelScreenX(Layout.CHAT_BAR) + 20;
			int by = shown.panelScreenY(Layout.CHAT_BAR) + 10;
			check(folded.areaAt(bx, by) == Layout.CHAT_BAR,
				name + ": the buttons still answer, so folding is not one-way");
			check(folded.openH > shown.openH,
				name + ": the open area grows by the parchment's height (" + shown.openH + " -> " + folded.openH + ")");
			check(!folded.sameAs(shown), name + ": a folded layout is not the same layout");
			// THE ONE THAT WAS WRONG IN GAME. zoomFor grows the projection with the open area, so
			// handing it the chatbox's 142 pixels zoomed the camera in on anyone who folded it.
			// The projection follows the window, not what is folded.
			check(folded.zoom == shown.zoom,
				name + ": folding does not move the camera (zoom " + shown.zoom + " either way)");
		}
		Layout hidTabs = Layout.resizable(Layout.MODERN, 1920, 1080, Layout.SCALE_FIXED, true, false);
		check(hidTabs.zoom == Layout.resizable(Layout.MODERN, 1920, 1080, Layout.SCALE_FIXED).zoom,
			"and nor does folding the inventory");

		System.out.println();
		System.out.println("11. a HUD the player has dragged");
		int[] dragOffsets = new int[Layout.GROUPS * 2];
		dragOffsets[Layout.GROUP_CHAT * 2] = 60;
		dragOffsets[Layout.GROUP_CHAT * 2 + 1] = -40;
		Layout home = Layout.resizable(Layout.CLASSIC, 1920, 1080, Layout.SCALE_FIXED);
		Layout drag = Layout.resizable(Layout.CLASSIC, 1920, 1080, Layout.SCALE_FIXED, false, false, dragOffsets);
		check(drag.panelScreenX(Layout.CHAT) == home.panelScreenX(Layout.CHAT) + 60
				&& drag.panelScreenY(Layout.CHAT) == home.panelScreenY(Layout.CHAT) - 40,
			"the chatbox lands where it was dropped");
		check(drag.panelScreenX(Layout.CHAT_BAR) == home.panelScreenX(Layout.CHAT_BAR) + 60
				&& drag.panelScreenY(Layout.CHAT_BAR) == home.panelScreenY(Layout.CHAT_BAR) - 40,
			"and its row of buttons goes with it, by the same amount");
		check(drag.panelScreenX(Layout.MINIMAP) == home.panelScreenX(Layout.MINIMAP)
				&& drag.panelScreenY(Layout.SIDEBAR) == home.panelScreenY(Layout.SIDEBAR),
			"and nothing else moves");
		check(!drag.sameAs(home), "a dragged layout is not the same layout, so the frame is rebuilt");

		// THE INVERSE IS THE ONE THAT MATTERS. Every hit test above this layer works in the fixed
		// frame's coordinates, and mapX gets there by subtracting where the panel landed. A panel
		// that moves while its inverse does not is an inventory whose squares are all one row out.
		int mapBad = 0;
		for (int p = 0; p < drag.panels; p++) {
			int px = drag.panelScreenX(p);
			int py = drag.panelScreenY(p);
			if (drag.mapX(px, py, p) != drag.panelFixedX(p) || drag.mapY(px, py, p) != drag.panelFixedY(p)) {
				mapBad++;
			}
			int lastX = px + drag.panelScreenWidth(p) - 1;
			int lastY = py + drag.panelScreenHeight(p) - 1;
			if (drag.mapX(lastX, py, p) != drag.panelFixedX(p) + drag.panelWidth(p) - 1
				|| drag.mapY(px, lastY, p) != drag.panelFixedY(p) + drag.panelHeight(p) - 1) {
				mapBad++;
			}
			if (drag.areaAt(px + 2, py + 2) != p) {
				mapBad++;
			}
		}
		check(mapBad == 0, "a dragged panel still maps its own corners and answers its own clicks (" + mapBad + " do not)");

		// Dragged at a HUD scale as well: the two offsets are in different units and multiplying
		// one by the other is the mistake waiting to be made here.
		Layout dragScaled = Layout.resizable(Layout.CLASSIC, 1920, 1080, 200, false, false, dragOffsets);
		check(dragScaled.mapX(dragScaled.panelScreenX(Layout.CHAT), dragScaled.panelScreenY(Layout.CHAT), Layout.CHAT) == dragScaled.panelFixedX(Layout.CHAT),
			"and still does at 200%, where a window pixel is half a frame pixel");

		// OFF THE EDGE IS GONE, so it is not allowed: a panel with nothing on screen has nothing
		// left to grab, and a window made smaller can do this without any drag at all.
		int[] far = new int[Layout.GROUPS * 2];
		far[Layout.GROUP_MINIMAP * 2] = 99999;
		far[Layout.GROUP_MINIMAP * 2 + 1] = -99999;
		Layout pushed = Layout.resizable(Layout.CLASSIC, 1920, 1080, Layout.SCALE_FIXED, false, false, far);
		int mpx = pushed.panelScreenX(Layout.MINIMAP);
		int mpy = pushed.panelScreenY(Layout.MINIMAP);
		int mpw = pushed.panelScreenWidth(Layout.MINIMAP);
		int mph = pushed.panelScreenHeight(Layout.MINIMAP);
		check(mpx < 1920 && mpx + mpw > 0 && mpy < 1080 && mpy + mph > 0, "a panel dragged at the horizon stays on the window");
		check(1920 - mpx >= Layout.KEEP_VISIBLE && mpy + mph >= Layout.KEEP_VISIBLE,
			"with enough of it left to take hold of again (" + Layout.KEEP_VISIBLE + "px)");

		System.out.println();
		System.out.println(fail == 0 ? (pass + " CHECKS, ALL PASS") : (fail + " FAILED of " + (pass + fail)));
		System.exit(fail == 0 ? 0 : 1);
	}
}
