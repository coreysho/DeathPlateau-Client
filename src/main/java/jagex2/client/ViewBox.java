package jagex2.client;

import java.awt.BorderLayout;
import java.awt.Graphics;
import java.awt.Insets;

import javax.swing.JFrame;

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
	 * The game canvas is a fixed 765x503 and must stay that way - it is a raster the client blits
	 * whole - so the sidebar is added to the EAST of the BorderLayout and the WINDOW grows to fit
	 * it. The game is not resized, moved or drawn over. This is how the plugin sidebar attaches;
	 * see jagex2.client.plugin.ui.Sidebar.
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
	}

	public boolean hasSidebar() {
		return this.sidebar != null;
	}

	public boolean isSidebarVisible() {
		return this.sidebar != null && this.sidebar.isVisible();
	}

	public void update(Graphics g) {
		this.shell.update(g);
	}

	public void paint(Graphics g) {
		this.shell.paint(g);
	}
}
