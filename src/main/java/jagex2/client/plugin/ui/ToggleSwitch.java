package jagex2.client.plugin.ui;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;

import javax.swing.JComponent;

/**
 * The on/off pill on each plugin row.
 *
 * A JCheckBox with a custom icon would do the job, but it drags the platform look and feel's
 * focus painting, margins and pressed states along with it, and those differ on every desktop.
 * This is about forty lines of painting and looks the same everywhere.
 *
 * The switch does NOT flip itself. It reports the click and waits to be told what the new state
 * is, because turning a plugin on happens on the game thread and can fail - a switch that had
 * already slid across would be lying about a plugin that never started.
 */
public final class ToggleSwitch extends JComponent {

	public interface Listener {

		void onToggled(ToggleSwitch source);
	}

	private static final int W = 30;
	private static final int H = 16;

	private boolean on;
	private Listener listener;

	public ToggleSwitch() {
		this.setOpaque(false);
		this.setPreferredSize(new Dimension(W, H));
		this.setMinimumSize(new Dimension(W, H));
		this.setMaximumSize(new Dimension(W, H));
		this.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		this.addMouseListener(new MouseAdapter() {

			public void mousePressed(MouseEvent event) {
				if (ToggleSwitch.this.listener != null && ToggleSwitch.this.isEnabled()) {
					ToggleSwitch.this.listener.onToggled(ToggleSwitch.this);
				}
			}
		});
	}

	public void setListener(Listener listener) {
		this.listener = listener;
	}

	public boolean isOn() {
		return this.on;
	}

	public void setOn(boolean on) {
		if (this.on != on) {
			this.on = on;
			this.repaint();
		}
	}

	protected void paintComponent(Graphics graphics) {
		Graphics2D g = (Graphics2D) graphics.create();
		try {
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			int w = this.getWidth();
			int h = this.getHeight();
			int trackH = Math.min(h, H);
			int top = (h - trackH) / 2;

			g.setColor(this.on ? Theme.ACCENT : Theme.SWITCH_OFF);
			g.fill(new RoundRectangle2D.Float(0, top, w, trackH, trackH, trackH));

			int knob = trackH - 4;
			int knobX = this.on ? w - knob - 2 : 2;
			g.setColor(this.on ? Color.WHITE : Theme.KNOB);
			g.fill(new Ellipse2D.Float(knobX, top + 2, knob, knob));
		} finally {
			g.dispose();
		}
	}
}
