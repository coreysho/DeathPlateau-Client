package jagex2.client;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

import jagex2.client.plugin.ui.Theme;

/**
 * The window's own title bar, drawn rather than asked for.
 *
 * WHY DRAW ONE. A desktop draws the bar above a window in its own colours and there is no way to
 * ask it for different ones - no Java API, no look and feel, nothing. A dark client under a
 * bright grey Windows bar looks like a dark client somebody forgot to finish. The only way to a
 * window that is dark all the way to its edges is to turn the real bar off and put this in its
 * place, which is exactly what RuneLite does.
 *
 * WHAT IS GIVEN UP, because it is not nothing. An undecorated window has no title bar as far as
 * the desktop is concerned, so the behaviours the desktop attached to that bar have to be
 * rebuilt here or lost: dragging to move, double-click to maximise and the three buttons are
 * below; dragging the edges to resize is in {@link WindowResizer}. Snapping a window to the side
 * of the screen by dragging it there is the desktop's, and does not come back - it is the one
 * thing this costs.
 *
 * Everything is painted by hand rather than built from Swing buttons. A JButton brings the
 * player's look and feel with it, which is the thing being avoided.
 */
public final class TitleBar extends JPanel {

	/**
	 * Tall enough for a 16px icon with air around it, and about what a desktop bar gives you.
	 *
	 * NOT CALLED HEIGHT, and the reason is worth keeping. Component implements ImageObserver,
	 * which declares HEIGHT = 2 - so inside the Button class below, an inherited constant named
	 * HEIGHT shadows this outer one, and Java scoping prefers it silently. The buttons came out
	 * one pixel tall with their glyphs clipped to a few specks, and nothing warned about it.
	 */
	public static final int BAR_H = 28;

	private static final int BUTTON_W = 44;

	static final int MINIMISE = 0;
	static final int MAXIMISE = 1;
	static final int CLOSE = 2;

	private final JFrame frame;
	private final Button maximise;

	/** Where in the window the drag started, so the window keeps its grip on the cursor. */
	private Point grab;

	public TitleBar(JFrame frame, String title) {
		this.frame = frame;
		this.setLayout(new BorderLayout());
		this.setBackground(Theme.TITLE_BAR);
		this.setOpaque(true);
		this.setPreferredSize(new Dimension(0, BAR_H));
		// A hairline under the bar, so it reads as a separate surface from the game below it.
		this.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.SEPARATOR));

		JPanel left = new JPanel();
		left.setLayout(new BoxLayout(left, BoxLayout.X_AXIS));
		left.setOpaque(false);
		left.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));

		Image icon = Branding.iconAtLeast(16);
		if (icon != null) {
			JLabel badge = new JLabel(new javax.swing.ImageIcon(
				icon.getScaledInstance(16, 16, Image.SCALE_SMOOTH)));
			badge.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 8));
			left.add(badge);
		}
		JLabel text = new JLabel(title);
		text.setForeground(Theme.TEXT);
		text.setFont(Theme.FONT_TITLE);
		left.add(text);
		this.add(left, BorderLayout.WEST);

		JPanel buttons = new JPanel();
		buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
		buttons.setOpaque(false);
		buttons.add(new Button(MINIMISE));
		this.maximise = new Button(MAXIMISE);
		buttons.add(this.maximise);
		buttons.add(new Button(CLOSE));
		this.add(buttons, BorderLayout.EAST);

		this.installDrag();
	}

	/** Hides the maximise button in fixed mode, where the window cannot change size at all. */
	public void setMaximiseAllowed(boolean allowed) {
		this.maximise.setVisible(allowed);
		this.revalidate();
		this.repaint();
	}

	// ------------------------------------------------------------------ moving the window

	private void installDrag() {
		MouseAdapter press = new MouseAdapter() {

			public void mousePressed(MouseEvent event) {
				TitleBar.this.grab = event.getPoint();
			}

			public void mouseReleased(MouseEvent event) {
				TitleBar.this.grab = null;
				// The game owns the keyboard. A click up here must not leave it without focus,
				// or the next keypress goes nowhere.
				TitleBar.this.frame.getContentPane().requestFocusInWindow();
			}

			public void mouseClicked(MouseEvent event) {
				if (event.getClickCount() == 2 && TitleBar.this.frame.isResizable()) {
					TitleBar.this.toggleMaximised();
				}
			}
		};
		this.addMouseListener(press);
		this.addMouseMotionListener(new MouseMotionAdapter() {

			public void mouseDragged(MouseEvent event) {
				TitleBar.this.drag(event);
			}
		});
	}

	private void drag(MouseEvent event) {
		if (this.grab == null) {
			return;
		}
		Point onScreen = event.getLocationOnScreen();
		if (isMaximised(this.frame)) {
			// Dragging a maximised window restores it and keeps it under the cursor, left to
			// right, which is what every desktop does and what the hand expects.
			int wasWidth = this.frame.getWidth();
			this.frame.setExtendedState(Frame.NORMAL);
			int nowWidth = this.frame.getWidth();
			int gripX = wasWidth == 0 ? this.grab.x : this.grab.x * nowWidth / wasWidth;
			this.grab = new Point(gripX, this.grab.y);
		}
		this.frame.setLocation(onScreen.x - this.grab.x, onScreen.y - this.grab.y);
	}

	void toggleMaximised() {
		if (isMaximised(this.frame)) {
			this.frame.setExtendedState(Frame.NORMAL);
		} else {
			ViewBox.limitMaximisedBounds(this.frame);
			this.frame.setExtendedState(this.frame.getExtendedState() | Frame.MAXIMIZED_BOTH);
		}
		this.maximise.repaint();
	}

	static boolean isMaximised(JFrame frame) {
		return (frame.getExtendedState() & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH;
	}

	// ------------------------------------------------------------------ the three buttons

	/** One window button: a glyph drawn with two or three strokes, and a hover behind it. */
	private final class Button extends JComponent {

		private final int kind;
		private boolean hovered;

		Button(int kind) {
			this.kind = kind;
			this.setPreferredSize(new Dimension(BUTTON_W, BAR_H));
			this.setMaximumSize(new Dimension(BUTTON_W, BAR_H));
			this.addMouseListener(new MouseAdapter() {

				public void mouseEntered(MouseEvent event) {
					Button.this.hovered = true;
					Button.this.repaint();
				}

				public void mouseExited(MouseEvent event) {
					Button.this.hovered = false;
					Button.this.repaint();
				}

				public void mouseClicked(MouseEvent event) {
					Button.this.act();
				}
			});
		}

		private void act() {
			if (this.kind == MINIMISE) {
				TitleBar.this.frame.setExtendedState(TitleBar.this.frame.getExtendedState() | Frame.ICONIFIED);
			} else if (this.kind == MAXIMISE) {
				TitleBar.this.toggleMaximised();
			} else {
				// The same road as the real close button: let the frame's own closing handler
				// decide, rather than killing the process from here.
				TitleBar.this.frame.dispatchEvent(new java.awt.event.WindowEvent(
					TitleBar.this.frame, java.awt.event.WindowEvent.WINDOW_CLOSING));
			}
		}

		protected void paintComponent(Graphics g) {
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			int w = this.getWidth();
			int h = this.getHeight();
			if (this.hovered) {
				g2.setColor(this.kind == CLOSE ? Theme.CLOSE_HOVER : Theme.BUTTON_HOVER);
				g2.fillRect(0, 0, w, h);
			}
			// White on the close hover, because the red behind it is too dark for the dim grey.
			g2.setColor(this.hovered && this.kind == CLOSE ? Color.WHITE : Theme.TEXT);
			g2.setStroke(new BasicStroke(1.2f));
			int cx = w / 2;
			int cy = h / 2;
			if (this.kind == MINIMISE) {
				g2.drawLine(cx - 5, cy, cx + 5, cy);
			} else if (this.kind == MAXIMISE) {
				if (isMaximised(TitleBar.this.frame)) {
					// Restore: the window in front, and the shape it would go back to behind it.
					g2.drawRect(cx - 5, cy - 3, 8, 8);
					g2.drawLine(cx - 3, cy - 5, cx + 5, cy - 5);
					g2.drawLine(cx + 5, cy - 5, cx + 5, cy + 3);
				} else {
					g2.drawRect(cx - 5, cy - 5, 10, 10);
				}
			} else {
				g2.drawLine(cx - 5, cy - 5, cx + 5, cy + 5);
				g2.drawLine(cx + 5, cy - 5, cx - 5, cy + 5);
			}
			g2.dispose();
		}
	}
}
