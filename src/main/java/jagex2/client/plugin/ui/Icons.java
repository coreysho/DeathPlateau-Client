package jagex2.client.plugin.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;

import javax.swing.Icon;

/**
 * The sidebar's icons, painted rather than loaded.
 *
 * WHY NOT IMAGE FILES. The client's art all lives in the cache as Jagex sprites, and the sidebar
 * is Swing, outside that entirely. Adding png files would mean a second art pipeline, something to
 * keep in sync with the jar, and a licence question about where the art came from. Four shapes
 * drawn with Graphics2D cost about a hundred lines, scale to any size, and recolour on hover for
 * free.
 */
public final class Icons {

	private Icons() {
	}

	/** Base for the icons below: sizes itself, antialiases, and paints in whatever colour it is given. */
	private abstract static class Vector implements Icon {

		final int size;
		final Color colour;

		Vector(int size, Color colour) {
			this.size = size;
			this.colour = colour;
		}

		public int getIconWidth() {
			return this.size;
		}

		public int getIconHeight() {
			return this.size;
		}

		public void paintIcon(Component component, Graphics graphics, int x, int y) {
			Graphics2D g = (Graphics2D) graphics.create();
			try {
				g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
				g.translate(x, y);
				g.setColor(this.colour);
				this.draw(g, this.size);
			} finally {
				g.dispose();
			}
		}

		abstract void draw(Graphics2D g, int size);
	}

	/** The cog on each row, and the sidebar's own settings tab. */
	public static Icon gear(int size, Color colour) {
		return new Vector(size, colour) {

			void draw(Graphics2D g, int s) {
				double centre = s / 2.0;
				double outer = s * 0.46;
				double inner = s * 0.30;
				// Eight teeth, drawn as spokes rather than a path: at 14 pixels across, a proper
				// gear outline turns to mush and this reads as a cog at any size.
				g.setStroke(new BasicStroke(Math.max(1.5f, s * 0.13f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				for (int i = 0; i < 8; i++) {
					double angle = Math.PI * i / 4.0;
					g.drawLine((int) Math.round(centre + Math.cos(angle) * inner * 0.75),
						(int) Math.round(centre + Math.sin(angle) * inner * 0.75),
						(int) Math.round(centre + Math.cos(angle) * outer),
						(int) Math.round(centre + Math.sin(angle) * outer));
				}
				g.setStroke(new BasicStroke(Math.max(1.5f, s * 0.14f)));
				double ring = inner * 1.15;
				g.draw(new Ellipse2D.Double(centre - ring, centre - ring, ring * 2, ring * 2));
			}
		};
	}

	/** The sidebar's plugin tab, matching the wrench RuneLite uses for the same thing. */
	public static Icon wrench(int size, Color colour) {
		return new Vector(size, colour) {

			void draw(Graphics2D g, int s) {
				g.setStroke(new BasicStroke(Math.max(2f, s * 0.17f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				// The shaft, running from the bottom left up to the head.
				g.drawLine((int) (s * 0.26), (int) (s * 0.74), (int) (s * 0.60), (int) (s * 0.40));
				// The head: an arc with a bite taken out of the top right, which is what makes it
				// a spanner rather than a key. The gap has to face away from the shaft or the two
				// read as one closed loop.
				int d = (int) (s * 0.46);
				g.drawArc((int) (s * 0.48), (int) (s * 0.06), d, d, 75, 290);
			}
		};
	}

	/** Back, on the config page. */
	public static Icon back(int size, Color colour) {
		return new Vector(size, colour) {

			void draw(Graphics2D g, int s) {
				g.setStroke(new BasicStroke(Math.max(2f, s * 0.15f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				int midY = s / 2;
				g.drawLine((int) (s * 0.22), midY, (int) (s * 0.78), midY);
				g.drawLine((int) (s * 0.22), midY, (int) (s * 0.46), (int) (midY - s * 0.24));
				g.drawLine((int) (s * 0.22), midY, (int) (s * 0.46), (int) (midY + s * 0.24));
			}
		};
	}

	/** The magnifier in the search box. */
	public static Icon search(int size, Color colour) {
		return new Vector(size, colour) {

			void draw(Graphics2D g, int s) {
				g.setStroke(new BasicStroke(Math.max(1.5f, s * 0.13f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				double d = s * 0.52;
				g.draw(new Ellipse2D.Double(s * 0.12, s * 0.12, d, d));
				g.drawLine((int) (s * 0.62), (int) (s * 0.62), (int) (s * 0.86), (int) (s * 0.86));
			}
		};
	}

	/** Reload, on the plugin list's header. */
	public static Icon refresh(int size, Color colour) {
		return new Vector(size, colour) {

			void draw(Graphics2D g, int s) {
				g.setStroke(new BasicStroke(Math.max(1.5f, s * 0.13f), BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
				int inset = (int) Math.round(s * 0.20);
				int d = s - inset * 2;
				g.drawArc(inset, inset, d, d, 70, 280);
				// A filled head on the open end, because two short strokes at 14 pixels across
				// just look like a thicker bit of the arc.
				double angle = Math.toRadians(70);
				double cx = inset + d / 2.0 + Math.cos(angle) * d / 2.0;
				double cy = inset + d / 2.0 - Math.sin(angle) * d / 2.0;
				double wing = s * 0.17;
				Path2D head = new Path2D.Double();
				head.moveTo(cx + wing, cy);
				head.lineTo(cx - wing, cy);
				head.lineTo(cx, cy - wing * 1.3);
				head.closePath();
				g.fill(head);
			}
		};
	}
}
