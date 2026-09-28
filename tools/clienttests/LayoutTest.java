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

		int[][] sizes = { { 765, 503 }, { 1024, 768 }, { 1280, 800 }, { 1920, 1080 }, { 2560, 1440 }, { 3840, 2160 } };
		System.out.println("2. resizable: every window point belongs to exactly one thing");
		for (int[] s : sizes) {
			Layout r = Layout.resizable(s[0], s[1]);
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
						int px = Layout.panelFixedX(area);
						int py = Layout.panelFixedY(area);
						if (mx < px || my < py || mx >= px + Layout.panelWidth(area) || my >= py + Layout.panelHeight(area)) {
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

		System.out.println("3. resizable: the panels sit where Old School's classic layout puts them");
		Layout r = Layout.resizable(1280, 800);
		check(r.panelScreenX(Layout.CHAT) == 0 && r.panelScreenY(Layout.CHAT) + Layout.panelHeight(Layout.CHAT) == 800,
			"the chatbox in the bottom-left corner");
		check(r.panelScreenX(Layout.MINIMAP) + Layout.panelWidth(Layout.MINIMAP) == 1280 && r.panelScreenY(Layout.MINIMAP) == 0,
			"the minimap in the top-right corner");
		check(r.panelScreenX(Layout.SIDEBAR) + Layout.panelWidth(Layout.SIDEBAR) == 1280
			&& r.panelScreenY(Layout.SIDEBAR) + Layout.panelHeight(Layout.SIDEBAR) == 800, "the side tabs in the bottom-right corner");
		check(r.vpW == 1280 && r.vpH == 800 && r.vpScreenX == 0 && r.vpScreenY == 0, "the viewport is the whole window");
		check(r.mainX >= 0 && r.mainX + 512 <= r.openW && r.mainY >= 0 && r.mainY + 334 <= r.openH
			&& Math.abs(r.mainX - (r.openW - 512 - r.mainX)) <= 1 && Math.abs(r.mainY - (r.openH - 334 - r.mainY)) <= 1,
			"a main interface is centred in the open area, clear of the panels: " + r.mainX + "," + r.mainY);
		Layout m = Layout.resizable(765, 503);
		check(m.panelScreenY(Layout.SIDEBAR) == m.panelScreenY(Layout.MINIMAP) + Layout.panelHeight(Layout.MINIMAP),
			"at the smallest window the minimap and the side tabs meet, as in the fixed frame");
		Layout tiny = Layout.resizable(400, 300);
		check(tiny.width == 765 && tiny.height == 503, "a window smaller than 765x503 is laid out as 765x503");
		check(r.letterX == (1280 - 765) / 2 && r.letterY == (800 - 503) / 2, "the title screen is centred");

		System.out.println("4. an open menu keeps the mouse");
		int sx = r.panelScreenX(Layout.SIDEBAR) + 20;
		int sy = r.panelScreenY(Layout.SIDEBAR) + 100;
		check(r.inViewport(r.mapX(sx, sy, Layout.VIEWPORT), r.mapY(sx, sy, Layout.VIEWPORT)),
			"a viewport menu hanging over the side panel still sees the mouse there as viewport");
		check(r.mapX(sx - 30, sy, Layout.SIDEBAR) == r.mapX(sx, sy, Layout.SIDEBAR) - 30,
			"a sidebar menu follows the mouse smoothly off the panel's edge");
		check(r.mapX(-1, -1, Layout.ANY) == -1 && r.mapY(-1, -1, Layout.ANY) == -1, "the mouse outside the window stays (-1, -1)");

		System.out.println("5. the projection grows with the window, never below 512");
		int last = 0;
		boolean mono = true;
		for (int[] s : sizes) {
			int z = Layout.resizable(s[0], s[1]).zoom;
			if (z < last || z < 512) {
				mono = false;
			}
			last = z;
		}
		check(mono, "zoom rises with the window's height: 765x503 " + Layout.resizable(765, 503).zoom + ", 1280x800 "
			+ Layout.resizable(1280, 800).zoom + ", 1920x1080 " + Layout.resizable(1920, 1080).zoom + ", 3840x2160 " + last);

		System.out.println();
		System.out.println(fail == 0 ? (pass + " CHECKS, ALL PASS") : (fail + " FAILED of " + (pass + fail)));
		System.exit(fail == 0 ? 0 : 1);
	}
}
