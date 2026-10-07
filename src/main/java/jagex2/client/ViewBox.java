package jagex2.client;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.GraphicsConfiguration;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import jagex2.client.plugin.ui.Theme;
import lostcity.Branding;

import deob.ObfuscatedName;
import sign.signlink;

public class ViewBox extends JFrame implements TitleBar.Actions {

	@ObfuscatedName("IEJCKZCR.a")
	public GameShell shell;

	public Insets insets;

	/** The plugin sidebar, when there is one. Null in the applet, which has no window to add to. */
	private java.awt.Component sidebar;

	/**
	 * The drawn title bar, or null if the desktop would not let us turn its own one off.
	 *
	 * SOME DESKTOPS REFUSE. setUndecorated throws if the window is already displayable, and a
	 * window manager may ignore it outright. Either way the client has to come up, so a failure
	 * here leaves an ordinary decorated window with the right icon on it and nothing else
	 * changed - which is worse looking and completely usable.
	 */
	private TitleBar titleBar;

	public ViewBox(int height, GameShell shell, int width) {
		this.shell = shell;
		this.setTitle(Client.SERVER_NAME);
		this.setResizable(false);
		this.setIconImages(Branding.icons());
		this.setBackground(Theme.BACKGROUND);

		BorderLayout manager = new BorderLayout();
		this.setLayout(manager);

		this.decorate();
		this.add(shell, BorderLayout.CENTER);
		this.pack();

		this.setVisible(true);
		this.toFront();
	}

	/**
	 * Replaces the desktop's title bar with one we draw, and gives the window its own edge.
	 *
	 * The edge is not decoration. An undecorated window has no border at all, so without a line
	 * drawn around it a dark client sitting on a dark wallpaper has no visible extent - and the
	 * strip that line sits in is also the only place {@link WindowResizer} can feel a drag, the
	 * game being a heavyweight Canvas that swallows everything inside its own bounds.
	 */
	private void decorate() {
		try {
			this.setUndecorated(true);
		} catch (Throwable error) {
			// Already displayable, or a window manager that will not have it. Keep the real bar.
			return;
		}
		this.titleBar = new TitleBar(this, Client.SERVER_NAME, this);
		this.add(this.titleBar, BorderLayout.NORTH);
		this.paintBorder(false);
		this.getContentPane().setBackground(Theme.TITLE_BAR);
		WindowResizer.install(this);

		// The border is the only thing that says which window has the keyboard, now that the
		// desktop is not drawing a bar that changes colour.
		this.addWindowListener(new WindowAdapter() {

			public void windowActivated(WindowEvent event) {
				ViewBox.this.paintBorder(true);
			}

			public void windowDeactivated(WindowEvent event) {
				ViewBox.this.paintBorder(false);
			}
		});
	}

	/**
	 * The window's edge: one coloured line, then a dark gutter out to the grab margin.
	 *
	 * The line is all the edge that should be visible - a window outlined in four pixels of red
	 * looks like a frame around a picture rather than a window. The gutter behind it is not
	 * decoration at all: it is the strip {@link WindowResizer} needs to feel a drag in, because
	 * the game is a heavyweight Canvas and swallows every mouse event inside its own bounds.
	 */
	private void paintBorder(boolean focused) {
		java.awt.Container content = this.getContentPane();
		if (!(content instanceof JComponent)) {
			return;
		}
		int gutter = WindowResizer.MARGIN - 1;
		((JComponent) content).setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(1, 1, 1, 1,
				focused ? Theme.BORDER_FOCUSED : Theme.BORDER),
			BorderFactory.createMatteBorder(gutter, gutter, gutter, gutter, Theme.TITLE_BAR)));
		content.repaint();
	}

	/**
	 * Keeps a maximised undecorated window off the taskbar.
	 *
	 * A decorated window maximises to the desktop's working area because the window manager
	 * puts it there. An undecorated one is handed the whole screen and covers the taskbar, so
	 * the working area has to be worked out and set as the limit - from the screen this window
	 * is actually on, not the primary one, or it maximises to the wrong size on a second
	 * monitor.
	 */
	static void limitMaximisedBounds(JFrame frame) {
		try {
			GraphicsConfiguration gc = frame.getGraphicsConfiguration();
			if (gc == null) {
				return;
			}
			Rectangle screen = gc.getBounds();
			Insets taken = Toolkit.getDefaultToolkit().getScreenInsets(gc);
			frame.setMaximizedBounds(new Rectangle(
				screen.x + taken.left, screen.y + taken.top,
				screen.width - taken.left - taken.right,
				screen.height - taken.top - taken.bottom));
		} catch (Throwable ignored) {
			// No insets to be had: maximise over the whole screen rather than not at all.
		}
	}

	/**
	 * Puts a panel down the right-hand side of the window, beside the game.
	 *
	 * The game canvas is added to CENTER and the sidebar to EAST, so the WINDOW grows to fit the
	 * sidebar and the game keeps whatever size its display mode gives it. The game is not resized,
	 * moved or drawn over. This is how the plugin sidebar attaches; see
	 * jagex2.client.plugin.ui.Sidebar.
	 *
	 * Call on the event dispatch thread.
	 */
	public void setSidebar(java.awt.Component sidebar) {
		if (this.sidebar != null) {
			this.remove(this.sidebar);
		}
		this.sidebar = sidebar;
		if (sidebar != null) {
			this.add(sidebar, BorderLayout.EAST);
		}
		if (this.titleBar != null) {
			// No sidebar, nothing for the chevron to do, so it is not drawn at all.
			this.titleBar.setSidebarAvailable(sidebar != null);
		}
		if (sidebar instanceof jagex2.client.plugin.ui.Sidebar) {
			// Folding its page away changes how wide it wants to be, and only the window can
			// act on that - and only in the order below, minimum first.
			((jagex2.client.plugin.ui.Sidebar) sidebar).setResizeListener(new Runnable() {

				public void run() {
					ViewBox.this.applyMinimumSize();
					ViewBox.this.pack();
				}
			});
		}
		// Minimum BEFORE pack, always: see setSidebarVisible.
		this.applyMinimumSize();
		this.pack();
	}

	// ------------------------------------------------------------------ the title bar's buttons

	/**
	 * The camera button. The client takes the picture itself, at the end of its next frame -
	 * see Client.requestScreenshot, which explains why it cannot be done from here.
	 */
	public void onScreenshot() {
		if (this.shell instanceof Client) {
			((Client) this.shell).requestScreenshot();
		}
	}

	public void onToggleSidebar() {
		this.setSidebarVisible(!this.isSidebarVisible());
	}

	public boolean isSidebarOpen() {
		return this.isSidebarVisible();
	}

	/**
	 * Shows or hides the sidebar, shrinking the window back to the size of the game when it is
	 * hidden. BorderLayout leaves an invisible component out of its sizing, so a pack() is all it
	 * takes. Call on the event dispatch thread.
	 *
	 * THE MINIMUM SIZE IS LOWERED FIRST, and the order is the whole of it. The minimum includes
	 * whatever the sidebar is taking, so packing while the old one is still in force clamps the
	 * window at its old width and hiding the sidebar leaves a strip of empty background where it
	 * used to be. It did exactly that until the chevron gave a reason to check.
	 */
	public void setSidebarVisible(boolean visible) {
		if (this.sidebar == null || this.sidebar.isVisible() == visible) {
			return;
		}
		this.sidebar.setVisible(visible);
		if (this.titleBar != null) {
			// F8 and the chevron are the same switch; whichever was used, the arrow turns round.
			this.titleBar.sidebarChanged();
		}
		this.applyMinimumSize();
		this.pack();
	}

	public boolean hasSidebar() {
		return this.sidebar != null;
	}

	public boolean isSidebarVisible() {
		return this.sidebar != null && this.sidebar.isVisible();
	}

	/** Width the sidebar is taking up, which the window needs on top of the game's own minimum. */
	private int sidebarWidth() {
		return this.sidebar != null && this.sidebar.isVisible() ? this.sidebar.getPreferredSize().width : 0;
	}

	/**
	 * The smallest the window may be dragged to in resizable mode: the game's minimum plus the
	 * window's borders plus whatever the sidebar is taking. Without the sidebar's share, dragging
	 * the window in would squeeze the game below Layout.MIN_W and the panels would overlap.
	 *
	 * Only applies while resizable - a fixed window cannot be dragged at all, and giving it a
	 * minimum would stop pack() shrinking it back when the sidebar is hidden.
	 */
	private void applyMinimumSize() {
		if (!this.isResizable()) {
			this.setMinimumSize(null);
			return;
		}
		Insets in = this.getInsets();
		Insets chrome = this.chromeInsets();
		this.setMinimumSize(new Dimension(
			Layout.MIN_W + this.sidebarWidth() + in.left + in.right + chrome.left + chrome.right,
			Layout.MIN_H + in.top + in.bottom + chrome.top + chrome.bottom));
	}

	/**
	 * What the window spends on itself before the game gets any: the content pane's border and
	 * the drawn title bar.
	 *
	 * These used to be in getInsets(), which is where a decorated frame keeps its own border and
	 * bar. Undecorated, getInsets() reads zero and the chrome is a child and a border instead -
	 * so a minimum size built from getInsets() alone would be short by the height of the title
	 * bar, and dragging the window in would squeeze the game under Layout.MIN_H.
	 */
	private Insets chromeInsets() {
		Insets out = new Insets(0, 0, 0, 0);
		java.awt.Container content = this.getContentPane();
		if (content instanceof JComponent) {
			Insets border = ((JComponent) content).getInsets();
			out.top += border.top;
			out.bottom += border.bottom;
			out.left += border.left;
			out.right += border.right;
		}
		if (this.titleBar != null && this.titleBar.isVisible()) {
			out.top += this.titleBar.getPreferredSize().height;
		}
		return out;
	}

	/**
	 * Fixed: the 765x503 window 377 always had, which cannot be resized. Resizable: a window that
	 * can be dragged to any size from 765x503 up or maximised, opened at w x h (the size it was
	 * left at). Done on the event thread; the client picks up the new size at its next frame.
	 */
	public void setMode(final boolean resizable, final int w, final int h, final boolean maximized) {
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				if (ViewBox.this.titleBar != null) {
					// Nothing to maximise to in fixed mode: the window is one size by definition.
					ViewBox.this.titleBar.setMaximiseAllowed(resizable);
				}
				if (resizable) {
					ViewBox.this.setResizable(true);
					ViewBox.this.shell.setPreferredSize(new Dimension(w, h));
					ViewBox.this.pack();
					ViewBox.this.applyMinimumSize();
					ViewBox.this.setLocationRelativeTo(null);
					if (maximized) {
						limitMaximisedBounds(ViewBox.this);
						ViewBox.this.setExtendedState(ViewBox.this.getExtendedState() | Frame.MAXIMIZED_BOTH);
					}
				} else {
					ViewBox.this.setExtendedState(Frame.NORMAL);
					ViewBox.this.setMinimumSize(null);
					ViewBox.this.setResizable(false);
					ViewBox.this.shell.setPreferredSize(new Dimension(Layout.FIXED_W, Layout.FIXED_H));
					ViewBox.this.pack();
				}
				ViewBox.this.shell.requestFocus();
			}
		});
	}

	public boolean isMaximized() {
		return (this.getExtendedState() & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH;
	}

	public void update(Graphics g) {
		this.paint(g);
	}

	/**
	 * WHY super.paint IS HERE. This override is from when the frame WAS the game: hand the frame's
	 * paint straight to the shell and there is nothing else in the window to draw. But the method
	 * it overrides is Container.paint, which is the thing that walks the child list - so with the
	 * override in place and no super call, a lightweight Swing child is never painted by the
	 * window at all.
	 *
	 * The plugin sidebar is exactly that child, and it showed: the window opened with a black bar
	 * down the side, which filled in the moment the mouse went near it. A mouse-over repaints that
	 * component through its nearest heavyweight ancestor and never goes through the frame's paint,
	 * so hovering drew what the expose should have.
	 *
	 * The game shell is heavyweight and paints through its own peer, so it is unaffected by this
	 * and still gets its call below. Only an expose comes through here - a fresh window, an
	 * alt-tab, a resize - never the game's own frame loop, which blits through a Graphics it keeps.
	 * See tools/clienttests/SidebarPaintTest.java.
	 */
	public void paint(Graphics g) {
		super.paint(g);
		this.shell.paint(g);
	}
}
