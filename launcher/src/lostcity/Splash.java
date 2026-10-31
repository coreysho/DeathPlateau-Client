package lostcity;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;

import javax.swing.JPanel;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;

/**
 * The first thing on screen, up before anything else happens.
 *
 * WHY IT EXISTS. The launcher's first act is a call to GitHub's API to find the latest release,
 * and until that answers there was nothing on screen at all: double-click, then a blank desktop
 * for as long as the network takes. A player on a slow connection sees a program that did not
 * start, and the usual response to that is to double-click it again - which is a second launcher,
 * a second update check, and eventually two clients.
 *
 * A JWindow RATHER THAN A FRAME, because a frame would put a second entry in the taskbar beside
 * the one the real window is about to take, and flash it. A JWindow has no decoration and no
 * button; it is a rectangle on top of the desktop, which is all a splash should be.
 *
 * IT DOES NOT WAIT FOR THE WINDOW IT PRECEDES. close() is called once the main window is up, and
 * the two overlap for a frame rather than leaving a gap of bare desktop between them.
 */
final class Splash {

	/**
	 * ACROSS, LIKE THE WINDOW IT PRECEDES, and small. A splash the size of the real window that
	 * is then replaced by a different arrangement of the same things reads as a redraw; a short
	 * strip reads as a step on the way. It is also the one that can go up fastest, which is the
	 * entire point of it.
	 */
	private static final int W = 420, H = 160;

	/**
	 * The mark leads and the lettering follows it, which is the other way round from the sizes
	 * the two files are drawn at - a 419px wordmark beside a 468px sigil comes out looking like
	 * the name with a decoration next to it. At these two the sigil is the thing you see.
	 */
	private static final int SIGIL_W = 106;
	private static final int WORDMARK_W = 176;

	/** Where the right-hand block starts, clear of the sigil. */
	private static final int TEXT_X = 172;

	private final JWindow window;
	private final Face face;

	private String status = "Loading launcher...";

	Splash() {
		this.face = new Face();
		this.window = new JWindow();
		this.window.setContentPane(this.face);
		this.window.pack();
		this.window.setLocationRelativeTo(null);
		this.window.setAlwaysOnTop(true);
		this.window.setVisible(true);
	}

	void setStatus(String text) {
		this.status = text;
		this.face.repaint();
	}

	void close() {
		this.window.setVisible(false);
		this.window.dispose();
	}

	/** Builds one on the Swing thread and hands it back, or null if that could not be done. */
	static Splash show() {
		final Splash[] made = new Splash[1];
		try {
			Runnable build = new Runnable() {

				public void run() {
					made[0] = new Splash();
				}
			};
			if (SwingUtilities.isEventDispatchThread()) {
				build.run();
			} else {
				SwingUtilities.invokeAndWait(build);
			}
		} catch (Throwable error) {
			// A splash is decoration. A headless or hostile desktop gets no splash, not no game.
			return null;
		}
		return made[0];
	}

	private final class Face extends JPanel {

		Face() {
			this.setPreferredSize(new Dimension(W, H));
			this.setOpaque(true);
		}

		protected void paintComponent(Graphics g) {
			Graphics2D g2 = (Graphics2D) g.create();
			Art.smooth(g2);
			int w = this.getWidth();
			int h = this.getHeight();
			// NO RIDGE HERE, though the window that follows has one: in a strip this short the
			// horizon takes a third of the height and the mark ends up standing in it.
			Art.vignette(g2, w, h, h / 2);

			Art.image(g2, Branding.sigil(), 86, 27, SIGIL_W);
			Art.image(g2, Branding.wordmark(), TEXT_X + WORDMARK_W / 2, 50, WORDMARK_W);

			g2.setFont(Theme.FONT.deriveFont(Font.PLAIN, 12f));
			g2.setColor(Theme.ACCENT_BRIGHT);
			g2.drawString(Splash.this.status, TEXT_X, 112);

			// The only edge it has: a window with no decoration and no border is a shape with no
			// end to it, and this one sits on whatever wallpaper the player has.
			g2.setColor(Theme.BORDER);
			g2.drawRect(0, 0, w - 1, h - 1);
			g2.setColor(new Color(0x10, 0x10, 0x10));
			g2.drawRect(1, 1, w - 3, h - 3);
			g2.dispose();
		}
	}
}
