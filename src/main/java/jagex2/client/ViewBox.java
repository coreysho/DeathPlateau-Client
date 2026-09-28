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
					Insets in = ViewBox.this.getInsets();
					ViewBox.this.setMinimumSize(new Dimension(Layout.MIN_W + in.left + in.right, Layout.MIN_H + in.top + in.bottom));
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
		this.shell.update(g);
	}

	public void paint(Graphics g) {
		this.shell.paint(g);
	}
}
