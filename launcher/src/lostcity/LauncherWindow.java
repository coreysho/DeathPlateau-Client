package lostcity;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.RoundRectangle2D;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.Timer;

/**
 * The launcher's window: the mark on the left, what happens next on the right.
 *
 * PAINTED RATHER THAN BUILT OUT OF COMPONENTS, for the same reason {@link TitleBar} is. A JButton
 * is drawn by the player's look and feel - a grey Windows button, a blue macOS pill - and the one
 * thing this window must not look like is a Java dialog that wandered in. Everything below is one
 * panel, a few rectangles and two hit boxes.
 *
 * WHY IT IS LAID OUT ACROSS AND NOT DOWN. The obvious shape for a launcher is a tall poster -
 * badge, name, a panel with a button in it, a line of small print - and half the servers with a
 * launcher have exactly that, close enough to each other to be confused. This one reads left to
 * right instead: who we are on one side of a rule, what the button will do on the other, with the
 * plateau along the bottom of both. It is also the shape the artwork wants, the sigil being square
 * and heavy enough to hold a column of its own.
 *
 * THE WINDOW IS THE SAME CHROME AS THE GAME. It is undecorated with {@link TitleBar} on top, so
 * the launcher and the client have one title bar between them rather than two that drift. That is
 * also why TitleBar takes a null Actions: the screenshot and sidebar buttons are the game's.
 *
 * TWO STATES, and only the right-hand column changes between them: the Play button while it is
 * waiting to be pressed, and a bar in its place while something is happening. Failure is a dialog
 * over the top and not this class's business.
 */
final class LauncherWindow {

	private static final int W = 580, H = 300;

	/** The rule between the two columns, and what sits either side of it. */
	private static final int DIVIDER_X = 236;
	private static final int MARK_CX = 116;
	private static final int COL_X = 270, COL_RIGHT = W - 24;
	private static final int COL_W = COL_RIGHT - COL_X;

	private static final int SIGIL_W = 148;
	private static final int WORDMARK_W = 186;

	/** The Play button, and the bar that takes its place. */
	private static final int ACTION_Y = 92, ACTION_H = 52;
	private static final int BAR_H = 12;

	private static final int HEADLINE_BASELINE = 64;
	private static final int CAPTION_BASELINE = 174;
	private static final int DEV_BASELINE = 220;
	private static final int FOOTER_RULE = H - 40;
	private static final int FOOTER_BASELINE = H - 15;

	/** How fast the indeterminate block crosses the bar, in pixels a frame at 40ms. */
	private static final int SWEEP_SPEED = 5;
	private static final int SWEEP_BLOCK = 120;

	interface Actions {

		void onPlay();

		/** The dev world, which only staff can get into and only staff are told about. */
		void onDev();
	}

	private final JFrame frame;
	private final Face face;
	private final Actions actions;
	private final String version;

	/** The line under the action: the countdown while waiting, the job while working. */
	private String status;
	private boolean busy;
	/** -1 while there is no size to measure against, else 0..1000. */
	private int permille = -1;
	private boolean choosable = true;

	private final Timer sweep;
	private int sweepAt;

	private Rectangle playBox = new Rectangle();
	private Rectangle devBox = new Rectangle();
	private boolean playHot, devHot;

	LauncherWindow(String title, String version, Actions actions) {
		this.actions = actions;
		this.version = version;
		this.face = new Face();

		this.frame = new JFrame(title);
		this.frame.setIconImages(Branding.icons());
		this.frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		// The game's window is fixed and so is this one; two resizable windows read as two clients.
		this.frame.setResizable(false);
		this.frame.setLayout(new BorderLayout());

		TitleBar bar = decorate(title);
		this.frame.add(this.face, BorderLayout.CENTER);
		if (bar != null) {
			bar.setMaximiseAllowed(false);
		}

		this.sweep = new Timer(40, new ActionListener() {

			public void actionPerformed(ActionEvent event) {
				LauncherWindow.this.sweepAt += SWEEP_SPEED;
				LauncherWindow.this.face.repaint();
			}
		});

		// ENTER STILL PLAYS. The old window made World 1 the root pane's default button, which a
		// painted button cannot be - so the keystroke is bound directly, or a player who has
		// always pressed Enter would find it does nothing.
		this.frame.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
			.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "play");
		this.frame.getRootPane().getActionMap().put("play", new AbstractAction() {

			public void actionPerformed(ActionEvent event) {
				if (LauncherWindow.this.choosable && !LauncherWindow.this.busy) {
					LauncherWindow.this.actions.onPlay();
				}
			}
		});

		this.frame.pack();
		this.frame.setLocationRelativeTo(null);
		this.frame.setVisible(true);
		this.frame.toFront();
	}

	/**
	 * Turns the desktop's title bar off and puts ours there, or leaves the real one if the
	 * desktop refuses - the same bargain the client's ViewBox strikes, and for the same reason:
	 * a launcher that will not open is worse than one with the wrong title bar.
	 */
	private TitleBar decorate(String title) {
		try {
			this.frame.setUndecorated(true);
		} catch (Throwable error) {
			return null;
		}
		TitleBar bar = new TitleBar(this.frame, title, null);
		this.frame.add(bar, BorderLayout.NORTH);
		this.face.setBorder(BorderFactory.createMatteBorder(0, 1, 1, 1, Theme.BORDER));
		return bar;
	}

	JFrame frame() {
		return this.frame;
	}

	/** The line under the action area. Does not change which state the window is in. */
	void setStatus(String text) {
		this.status = text;
		this.face.repaint();
	}

	/** Swaps the Play button for a bar, sweeping rather than filling until there is a size. */
	void setBusy(String text) {
		this.status = text;
		this.busy = true;
		this.permille = -1;
		this.startSweep(true);
		this.face.repaint();
	}

	void setProgress(int permille) {
		this.busy = true;
		this.permille = permille;
		this.startSweep(false);
		this.face.repaint();
	}

	/** Back to the Play button - after a dev world that could not be reached, say. */
	void setChoosing(String text) {
		this.status = text;
		this.busy = false;
		this.startSweep(false);
		this.face.repaint();
	}

	/** Whether Play and the dev link answer a click at all. */
	void setChoosable(boolean choosable) {
		this.choosable = choosable;
		this.face.repaint();
	}

	void dispose() {
		this.sweep.stop();
		this.frame.setVisible(false);
		this.frame.dispose();
	}

	private void startSweep(boolean on) {
		if (on && !this.sweep.isRunning()) {
			this.sweepAt = -SWEEP_BLOCK;
			this.sweep.start();
		} else if (!on && this.sweep.isRunning()) {
			this.sweep.stop();
		}
	}

	// ------------------------------------------------------------------ the face

	private final class Face extends JPanel {

		Face() {
			this.setPreferredSize(new Dimension(W, H));
			this.setOpaque(true);
			this.addMouseListener(new MouseAdapter() {

				public void mouseClicked(MouseEvent event) {
					Face.this.click(event);
				}

				public void mouseExited(MouseEvent event) {
					LauncherWindow.this.playHot = false;
					LauncherWindow.this.devHot = false;
					Face.this.repaint();
				}
			});
			this.addMouseMotionListener(new MouseMotionAdapter() {

				public void mouseMoved(MouseEvent event) {
					Face.this.hover(event);
				}
			});
		}

		private boolean live() {
			return LauncherWindow.this.choosable && !LauncherWindow.this.busy;
		}

		private void hover(MouseEvent event) {
			boolean play = this.live() && LauncherWindow.this.playBox.contains(event.getPoint());
			boolean dev = LauncherWindow.this.choosable && LauncherWindow.this.devBox.contains(event.getPoint());
			if (play == LauncherWindow.this.playHot && dev == LauncherWindow.this.devHot) {
				return;
			}
			LauncherWindow.this.playHot = play;
			LauncherWindow.this.devHot = dev;
			this.setCursor(Cursor.getPredefinedCursor(play || dev ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
			this.repaint();
		}

		private void click(MouseEvent event) {
			if (this.live() && LauncherWindow.this.playBox.contains(event.getPoint())) {
				LauncherWindow.this.actions.onPlay();
			} else if (LauncherWindow.this.choosable && LauncherWindow.this.devBox.contains(event.getPoint())) {
				LauncherWindow.this.actions.onDev();
			}
		}

		protected void paintComponent(Graphics g) {
			Graphics2D g2 = (Graphics2D) g.create();
			Art.smooth(g2);
			int w = this.getWidth();
			int h = this.getHeight();
			Art.vignette(g2, w, h, 120);
			Art.ridge(g2, w, h);

			this.mark(g2);
			this.divider(g2);
			this.column(g2);
			this.footer(g2, w, h);
			g2.dispose();
		}

		/** The left-hand column: the sigil, and the name under it. */
		private void mark(Graphics2D g2) {
			int bottom = Art.image(g2, Branding.sigil(), MARK_CX, 30, SIGIL_W);
			Art.image(g2, Branding.wordmark(), MARK_CX, bottom + 10, WORDMARK_W);
		}

		/**
		 * The rule between the columns, faded out at both ends.
		 *
		 * A line that stops dead against the background draws attention to its own ends; this one
		 * is a gradient to the backdrop, so it reads as a division rather than as a drawn object.
		 */
		private void divider(Graphics2D g2) {
			int top = 40, bottom = FOOTER_RULE - 10;
			Graphics2D line = (Graphics2D) g2.create();
			line.setPaint(new GradientPaint(
				0, top, new Color(0x3A, 0x22, 0x20, 0),
				0, (top + bottom) / 2, new Color(0x4A, 0x2C, 0x29)));
			line.fillRect(DIVIDER_X, top, 1, (bottom - top) / 2);
			line.setPaint(new GradientPaint(
				0, (top + bottom) / 2, new Color(0x4A, 0x2C, 0x29),
				0, bottom, new Color(0x3A, 0x22, 0x20, 0)));
			line.fillRect(DIVIDER_X, (top + bottom) / 2, 1, (bottom - top) / 2);
			line.dispose();
		}

		/** The right-hand column: what the button will do, the button, and the words under it. */
		private void column(Graphics2D g2) {
			// THE HEADLINE IS THE WORLD, not a greeting: this window's whole job is to start one,
			// and which one is the only thing about it a player cannot see from the artwork.
			String headline = LauncherWindow.this.busy && LauncherWindow.this.status != null
				? LauncherWindow.this.status
				: "World 1";
			g2.setFont(Theme.FONT_BOLD.deriveFont(14f));
			g2.setColor(LauncherWindow.this.busy ? Theme.TEXT : Color.WHITE);
			g2.drawString(headline, COL_X, HEADLINE_BASELINE);

			if (LauncherWindow.this.busy && LauncherWindow.this.permille >= 0) {
				String percent = (LauncherWindow.this.permille / 10) + "%";
				g2.setColor(Theme.ACCENT_BRIGHT);
				g2.drawString(percent, COL_RIGHT - g2.getFontMetrics().stringWidth(percent), HEADLINE_BASELINE);
			}

			g2.setColor(new Color(0x33, 0x22, 0x20));
			g2.drawLine(COL_X, HEADLINE_BASELINE + 12, COL_RIGHT, HEADLINE_BASELINE + 12);

			if (LauncherWindow.this.busy) {
				LauncherWindow.this.playBox = new Rectangle();
				this.bar(g2);
			} else {
				LauncherWindow.this.playBox = new Rectangle(COL_X, ACTION_Y, COL_W, ACTION_H);
				this.play(g2);
			}
			this.caption(g2);
			this.dev(g2);
		}

		private void play(Graphics2D g2) {
			boolean on = LauncherWindow.this.choosable;
			boolean hot = LauncherWindow.this.playHot && on;
			Color top = on ? (hot ? new Color(0xC8, 0x3E, 0x30) : Theme.ACCENT) : new Color(0x42, 0x2C, 0x2B);
			Color bottom = on ? (hot ? new Color(0x86, 0x20, 0x19) : new Color(0x6E, 0x1A, 0x14)) : new Color(0x2C, 0x20, 0x1F);
			g2.setPaint(new GradientPaint(0, ACTION_Y, top, 0, ACTION_Y + ACTION_H, bottom));
			g2.fillRect(COL_X, ACTION_Y, COL_W, ACTION_H);

			// A SQUARE BUTTON WITH ONE LIT EDGE. Rounded corners and a full outline are what every
			// launcher's button has; this is the sigil's own language - a hard edge with the red
			// catching along the top of it.
			g2.setColor(on ? new Color(0xE4, 0x74, 0x64) : new Color(0x4E, 0x38, 0x36));
			g2.drawLine(COL_X, ACTION_Y, COL_X + COL_W - 1, ACTION_Y);
			g2.setColor(on ? new Color(0x5A, 0x12, 0x0D) : new Color(0x24, 0x1A, 0x19));
			g2.drawLine(COL_X, ACTION_Y + ACTION_H - 1, COL_X + COL_W - 1, ACTION_Y + ACTION_H - 1);

			g2.setFont(Theme.FONT_BOLD.deriveFont(Font.BOLD, 20f));
			g2.setColor(on ? Color.WHITE : new Color(0x80, 0x72, 0x70));
			FontMetrics fm = g2.getFontMetrics();
			String text = "PLAY";
			// Letter-spaced by hand: a 20pt bold word in a 286px bar looks lost without it, and
			// Java 8 has no tracking attribute worth the Map it needs.
			int spacing = 6;
			int width = fm.stringWidth(text) + spacing * (text.length() - 1);
			int x = COL_X + (COL_W - width) / 2;
			int baseline = ACTION_Y + (ACTION_H + fm.getAscent() - fm.getDescent()) / 2;
			for (int i = 0; i < text.length(); i++) {
				String ch = text.substring(i, i + 1);
				g2.drawString(ch, x, baseline);
				x += fm.stringWidth(ch) + spacing;
			}
		}

		/**
		 * A slim bar where the button was, not a filled copy of it.
		 *
		 * The percentage is up on the headline rather than written inside the track: a number
		 * sitting on a moving fill has to be legible against both halves of it, which is why it
		 * ends up white and heavy, and that is a lot of weight for a progress bar to carry.
		 */
		private void bar(Graphics2D g2) {
			int y = ACTION_Y + (ACTION_H - BAR_H) / 2;
			g2.setColor(new Color(0x16, 0x10, 0x0F));
			g2.fillRect(COL_X, y, COL_W, BAR_H);

			Graphics2D clip = (Graphics2D) g2.create();
			clip.clip(new RoundRectangle2D.Float(COL_X + 1, y + 1, COL_W - 2, BAR_H - 2, 1, 1));
			clip.setPaint(new GradientPaint(0, y, Theme.ACCENT_BRIGHT, 0, y + BAR_H, new Color(0x6E, 0x1A, 0x14)));
			int permille = LauncherWindow.this.permille;
			if (permille >= 0) {
				clip.fillRect(COL_X + 1, y + 1, (COL_W - 2) * permille / 1000, BAR_H - 2);
			} else {
				// Nothing to measure: a block crossing the track, which says "working" without
				// claiming a fraction nobody knows.
				int at = COL_X + 1 + mod(LauncherWindow.this.sweepAt, COL_W + SWEEP_BLOCK) - SWEEP_BLOCK;
				clip.fillRect(at, y + 1, SWEEP_BLOCK, BAR_H - 2);
			}
			clip.dispose();

			g2.setColor(new Color(0x3A, 0x26, 0x24));
			g2.drawRect(COL_X, y, COL_W - 1, BAR_H - 1);
		}

		/**
		 * Two lines under the action: what the launcher is for, or whatever it is doing instead.
		 *
		 * The status takes the first line while the window is waiting, which is where the
		 * countdown to World 1 appears - it belongs beside the button it is about to press.
		 */
		private void caption(Graphics2D g2) {
			g2.setFont(Theme.FONT_SMALL);
			g2.setColor(Theme.TEXT_DIM);
			boolean waiting = !LauncherWindow.this.busy && LauncherWindow.this.status != null;
			if (waiting) {
				g2.drawString(LauncherWindow.this.status, COL_X, CAPTION_BASELINE);
			} else {
				g2.drawString("Keeps the client and the cache up to date,", COL_X, CAPTION_BASELINE);
				g2.drawString("then starts the game.", COL_X, CAPTION_BASELINE + 16);
			}
		}

		/**
		 * STAFF ONLY, AND QUIET ABOUT IT. The dev world is not for players and a second button
		 * beside Play would invite everyone to try it, so it is a line of small print under the
		 * column - findable by anyone told where to look, ignorable by everyone else.
		 */
		private void dev(Graphics2D g2) {
			g2.setFont(Theme.FONT_SMALL);
			FontMetrics fm = g2.getFontMetrics();
			String text = "Dev world ›";
			int width = fm.stringWidth(text);
			LauncherWindow.this.devBox = new Rectangle(COL_X - 4, DEV_BASELINE - fm.getAscent() - 3, width + 8, fm.getHeight() + 6);
			boolean hot = LauncherWindow.this.devHot && LauncherWindow.this.choosable;
			g2.setColor(hot ? Theme.ACCENT_BRIGHT : new Color(0x6A, 0x5A, 0x58));
			g2.drawString(text, COL_X, DEV_BASELINE);
			if (hot) {
				g2.drawLine(COL_X, DEV_BASELINE + 2, COL_X + width, DEV_BASELINE + 2);
			}
		}

		private void footer(Graphics2D g2, int w, int h) {
			g2.setColor(new Color(0x26, 0x18, 0x17));
			g2.drawLine(24, FOOTER_RULE, w - 24, FOOTER_RULE);

			g2.setFont(Theme.FONT_SMALL);
			g2.setColor(Theme.ACCENT_BRIGHT);
			g2.drawString("deathplateau.com", 24, FOOTER_BASELINE);

			g2.setColor(new Color(0x5E, 0x50, 0x4E));
			String right = "Launcher v" + LauncherWindow.this.version;
			g2.drawString(right, w - 24 - g2.getFontMetrics().stringWidth(right), FOOTER_BASELINE);
		}
	}

	/** Java's % keeps the sign, and the sweep starts negative so the block slides in from the left. */
	private static int mod(int value, int by) {
		int m = value % by;
		return m < 0 ? m + by : m;
	}
}
