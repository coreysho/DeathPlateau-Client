package jagex2.client;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Insets;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import deob.ObfuscatedName;
import sign.signlink;

public class ViewBox extends JFrame {

	@ObfuscatedName("IEJCKZCR.a")
	public GameShell shell;

	public Insets insets;

	/** The plugin sidebar, when there is one. Null in the applet, which has no window to add to. */
	private java.awt.Component sidebar;

	public ViewBox(int height, GameShell shell, int width) {
		this.shell = shell;
		this.setTitle(Client.SERVER_NAME);
		this.setResizable(false);

		BorderLayout manager = new BorderLayout();
		this.setLayout(manager);

		this.add(shell, BorderLayout.CENTER);
		this.pack();

		this.setVisible(true);
		this.toFront();
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
		this.pack();
		this.applyMinimumSize();
	}

	/**
	 * Shows or hides the sidebar, shrinking the window back to the size of the game when it is
	 * hidden. BorderLayout leaves an invisible component out of its sizing, so a pack() is all it
	 * takes. Call on the event dispatch thread.
	 */
	public void setSidebarVisible(boolean visible) {
		if (this.sidebar == null || this.sidebar.isVisible() == visible) {
			return;
		}
		this.sidebar.setVisible(visible);
		this.pack();
		this.applyMinimumSize();
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
		this.setMinimumSize(new Dimension(Layout.MIN_W + this.sidebarWidth() + in.left + in.right,
			Layout.MIN_H + in.top + in.bottom));
	}

	/**
	 * Fixed: the 765x503 window 377 always had, which cannot be resized. Resizable: a window that
	 * can be dragged to any size from 765x503 up or maximised, opened at w x h (the size it was
	 * left at). Done on the event thread; the client picks up the new size at its next frame.
	 */
	public void setMode(final boolean resizable, final int w, final int h, final boolean maximized) {
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				if (resizable) {
					ViewBox.this.setResizable(true);
					ViewBox.this.shell.setPreferredSize(new Dimension(w, h));
					ViewBox.this.pack();
					ViewBox.this.applyMinimumSize();
					ViewBox.this.setLocationRelativeTo(null);
					if (maximized) {
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
