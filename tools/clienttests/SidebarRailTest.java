// The icon rail: does folding the page away actually give the width back?
//
// The rail exists so a player can put the page away without losing the way back in, and the only
// thing that makes that worth having is the window shrinking when they do. That is three
// components agreeing - the pages panel hiding, the sidebar reporting a smaller preferred width,
// and the window lowering its minimum before it packs - and any one of them quietly not playing
// leaves a strip of empty background where the page used to be. Driven by
// tools/clienttests/run_sidebarpreview.py.
//
// WIDTHS ARE WAITED FOR, NOT READ. pack() asks the window manager for a size and the manager
// gets round to it; on a displayed frame the new width is not always there by the time the next
// line runs. The first version of this read it straight away and failed once with the old width,
// which looked like the sidebar not folding and was really this test being faster than the
// desktop.
package jagex2.client;

import java.awt.Dimension;

import jagex2.client.plugin.PluginManager;
import jagex2.client.plugin.ui.Sidebar;
import jagex2.client.plugin.ui.Theme;

public class SidebarRailTest {

	static int fails;

	static void check(boolean ok, String what) {
		System.out.println((ok ? "  ok   " : "FAIL   ") + what);
		if (!ok) {
			fails++;
		}
	}

	/**
	 * The window's width once it has stopped changing, or what it was after a second of waiting.
	 *
	 * Polled rather than slept on: the usual case settles within a frame or two and this returns
	 * at once, while a genuinely stuck window still fails rather than hanging the run.
	 */
	static int width(ViewBox frame, int want) throws Exception {
		for (int waited = 0; waited < 1000 && frame.getWidth() != want; waited += 20) {
			Thread.sleep(20);
		}
		return frame.getWidth();
	}

	public static void main(String[] args) throws Exception {
		GameShell shell = new GameShell();
		shell.setPreferredSize(new Dimension(Layout.FIXED_W, Layout.FIXED_H));
		ViewBox frame = new ViewBox(Layout.FIXED_H, shell, Layout.FIXED_W);
		frame.setResizable(true);

		Client client = new Client();
		PluginManager manager = new PluginManager(client, null, null, null);
		manager.reload();
		Sidebar sidebar = new Sidebar(manager);
		frame.setSidebar(sidebar);
		frame.validate();

		int open = frame.getWidth();
		check(sidebar.isPanelOpen(), "the sidebar opens with a page showing");
		check(open > Layout.FIXED_W + Theme.RAIL_WIDTH,
			"...and the window is wide enough for the page and the rail (" + open + ")");

		sidebar.setPanelOpen(false);
		int folded = width(frame, open - Theme.WIDTH);
		check(!sidebar.isPanelOpen(), "folding the page away closes it");
		check(open - folded == Theme.WIDTH,
			"...and the window gives back exactly the page's width, no more and no less ("
				+ open + " -> " + folded + ", page is " + Theme.WIDTH + ")");
		check(sidebar.getWidth() >= Theme.RAIL_WIDTH && sidebar.isVisible(),
			"...while the rail stays, which is the whole point of folding rather than hiding");

		sidebar.setPanelOpen(true);
		int back = width(frame, open);
		check(back == open, "bringing it back restores the window exactly (" + back + ")");

		// The minimum has to come down with it, or the next pack is clamped at the old width -
		// the bug that made hiding the sidebar leave an empty strip for as long as F8 existed.
		sidebar.setPanelOpen(false);
		width(frame, folded);
		check(frame.getMinimumSize().width <= folded,
			"a folded sidebar lowers the window's minimum width too ("
				+ frame.getMinimumSize().width + " <= " + folded + ")");
		sidebar.setPanelOpen(true);

		// F8 and the chevron are a different switch: they take the rail as well.
		frame.setSidebarVisible(false);
		int bare = width(frame, Layout.FIXED_W + frameChrome(frame));
		check(!frame.isSidebarVisible(), "F8 still hides the rail along with the page");
		check(bare == Layout.FIXED_W + frameChrome(frame),
			"...back to the game's own width (" + bare + ", wanted "
				+ (Layout.FIXED_W + frameChrome(frame)) + ")");
		frame.setSidebarVisible(true);
		int shown = width(frame, open);
		check(shown == open, "...and shows the pair of them again (" + shown + ")");

		frame.dispose();
		System.out.println(fails == 0 ? "ALL PASS" : fails + " FAILED");
		System.exit(fails == 0 ? 0 : 1);
	}

	/** The window's own edges, which the game's width sits inside. */
	static int frameChrome(ViewBox frame) {
		java.awt.Container content = frame.getContentPane();
		java.awt.Insets in = content instanceof javax.swing.JComponent
			? ((javax.swing.JComponent) content).getInsets() : new java.awt.Insets(0, 0, 0, 0);
		return in.left + in.right + frame.getInsets().left + frame.getInsets().right;
	}
}
