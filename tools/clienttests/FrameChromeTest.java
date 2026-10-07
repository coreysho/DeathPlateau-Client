// Does the window we draw ourselves behave like a window?
//
// An undecorated frame gives up everything the desktop used to do for it, and each of those
// things is now code that can be wrong: the edges resize, the minimum size accounts for chrome
// that is no longer in getInsets(), the maximise button knows which state it is in, the icon
// loaded. None of that is visible in a screenshot, and all of it is the sort of thing that
// breaks quietly. Driven by tools/clienttests/run_framepreview.py.
package jagex2.client;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;

import javax.swing.JComponent;

public class FrameChromeTest {

	static int fails;

	static void check(boolean ok, String what) {
		System.out.println((ok ? "  ok   " : "FAIL   ") + what);
		if (!ok) {
			fails++;
		}
	}

	public static void main(String[] args) throws Exception {
		GameShell shell = new GameShell();
		shell.setPreferredSize(new Dimension(Layout.FIXED_W, Layout.FIXED_H));
		ViewBox frame = new ViewBox(Layout.FIXED_H, shell, Layout.FIXED_W);
		frame.validate();

		check(frame.isUndecorated(), "the desktop's own title bar is off");
		check(!frame.getIconImages().isEmpty(),
			"the window has icons (" + frame.getIconImages().size() + " sizes)");
		check(frame.getIconImages().size() >= 4, "...several of them, so nothing is scaled from one");

		// The chrome the desktop used to provide is a child and a border now, and getInsets()
		// reads zero for both. A minimum size that forgot them would let the window be dragged
		// in until the game was under its own minimum and the panels started overlapping.
		TitleBar bar = null;
		for (java.awt.Component c : frame.getContentPane().getComponents()) {
			if (c instanceof TitleBar) {
				bar = (TitleBar) c;
			}
		}
		check(bar != null, "the drawn title bar is in the window");
		check(bar != null && bar.getHeight() == TitleBar.BAR_H,
			"...at its full height, not collapsed (" + (bar == null ? -1 : bar.getHeight()) + ")");

		frame.setResizable(true);
		frame.setSidebar(null);
		Dimension min = frame.getMinimumSize();
		int chrome = ((JComponent) frame.getContentPane()).getInsets().top
			+ ((JComponent) frame.getContentPane()).getInsets().bottom + TitleBar.BAR_H;
		check(min.height >= Layout.MIN_H + chrome,
			"the minimum height leaves the game its own minimum under the chrome ("
				+ min.height + " >= " + (Layout.MIN_H + chrome) + ")");
		check(min.width >= Layout.MIN_W, "...and the minimum width likewise (" + min.width + ")");

		// Dragging the bottom-right corner outwards.
		Rectangle before = frame.getBounds();
		drag(frame, before.width - 1, before.height - 1, 120, 90);
		Rectangle after = frame.getBounds();
		check(after.width == before.width + 120 && after.height == before.height + 90,
			"dragging the bottom-right corner resizes by exactly the drag (" + before.width + "x"
				+ before.height + " -> " + after.width + "x" + after.height + ")");

		// Dragging the top-left corner inwards moves the origin and shrinks the window, which is
		// the half of this that is easy to get backwards.
		before = frame.getBounds();
		drag(frame, 0, 0, 40, 30);
		after = frame.getBounds();
		check(after.x == before.x + 40 && after.y == before.y + 30
			&& after.width == before.width - 40 && after.height == before.height - 30,
			"dragging the top-left corner in moves the origin and shrinks it the same amount");

		// Past the minimum, the window stops rather than crawling across the screen.
		before = frame.getBounds();
		drag(frame, 0, 0, 5000, 5000);
		after = frame.getBounds();
		check(after.width == frame.getMinimumSize().width
			&& after.height == frame.getMinimumSize().height,
			"a drag past the minimum stops at the minimum (" + after.width + "x" + after.height + ")");
		check(after.x <= before.x + before.width && after.y <= before.y + before.height,
			"...and the window stays where it was pinned rather than following the cursor");

		// A fixed window has no edges to pull: the same drag must do nothing at all.
		frame.setResizable(false);
		before = frame.getBounds();
		drag(frame, before.width - 1, before.height - 1, 120, 90);
		check(frame.getBounds().equals(before), "a fixed window cannot be resized by its edges");

		// ---- the two buttons we added to the bar, which are ours rather than the window's
		frame.setResizable(true);
		javax.swing.JPanel panel = new javax.swing.JPanel();
		panel.setPreferredSize(new Dimension(250, 0));
		frame.setSidebar(panel);
		frame.validate();
		check(frame.isSidebarVisible(), "a window with a sidebar starts with it showing");
		check(frame.isSidebarOpen(), "...and the chevron is told so");

		int wide = frame.getWidth();
		frame.onToggleSidebar();
		check(!frame.isSidebarVisible(), "the chevron hides it");
		check(frame.getWidth() < wide, "...and the window shrinks back to the game ("
			+ wide + " -> " + frame.getWidth() + ")");
		frame.onToggleSidebar();
		check(frame.isSidebarVisible() && frame.getWidth() == wide,
			"...and brings it back to exactly where it was");

		// The camera asks the client for a picture rather than taking one itself; with a plain
		// GameShell in the window there is no client to ask, and it must not throw for it.
		boolean threw = false;
		try {
			frame.onScreenshot();
		} catch (Throwable error) {
			threw = error != null;
		}
		check(!threw, "the camera button does nothing rather than throwing when there is no game");

		frame.dispose();
		System.out.println(fails == 0 ? "ALL PASS" : fails + " FAILED");
		System.exit(fails == 0 ? 0 : 1);
	}

	/**
	 * A press, a drag and a release on the content pane, in frame coordinates.
	 *
	 * The resizer reads getLocationOnScreen(), so the events carry screen coordinates worked out
	 * from where the frame actually is - the same arithmetic a real drag would produce.
	 */
	static void drag(ViewBox frame, int x, int y, int dx, int dy) {
		java.awt.Component on = frame.getContentPane();
		Point origin = frame.getLocationOnScreen();
		send(on, MouseEvent.MOUSE_PRESSED, x, y, origin);
		send(on, MouseEvent.MOUSE_DRAGGED, x + dx, y + dy, origin);
		send(on, MouseEvent.MOUSE_RELEASED, x + dx, y + dy, origin);
	}

	static void send(java.awt.Component on, int id, int x, int y, Point origin) {
		MouseEvent event = new MouseEvent(on, id, System.currentTimeMillis(), 0, x, y,
			origin.x + x, origin.y + y, 1, false, MouseEvent.BUTTON1);
		on.dispatchEvent(event);
	}
}
