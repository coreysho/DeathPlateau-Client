package jagex2.client;

import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/**
 * Dragging the edges of an undecorated window to resize it.
 *
 * The desktop does this for an ordinary window and stops doing it the moment the window says it
 * has no decorations, so a frame with a drawn title bar has to do it itself or be stuck at
 * whatever size it opened at.
 *
 * WHERE THE EDGES ARE. The content pane carries a border {@link #MARGIN} pixels thick, and this
 * listens on the content pane itself - the border is part of that component's own area, not of
 * any child, so that is where the events land. It has to be a border rather than an overlay: the
 * game is a heavyweight Canvas and swallows every mouse event inside its own bounds, so an edge
 * drawn over the game would never be felt. A few pixels of gutter is the price of a resizable
 * undecorated window.
 *
 * It does nothing while the window is maximised or fixed - there is no edge to pull in either
 * case - and it will not drag a window below the minimum the frame was given, so the game can
 * never be squeezed under Layout.MIN_W and start overlapping its own panels.
 */
public final class WindowResizer extends MouseAdapter {

	/** How thick the grab strip is. Matches the border ViewBox puts on the content pane. */
	public static final int MARGIN = 4;

	private static final int NONE = 0;
	private static final int N = 1;
	private static final int S = 2;
	private static final int W = 4;
	private static final int E = 8;

	private final JFrame frame;

	/** Which edges are being dragged, and the window as it was when the drag began. */
	private int dragging = NONE;
	private Point start;
	private Rectangle startBounds;

	private WindowResizer(JFrame frame) {
		this.frame = frame;
	}

	/** Puts a resizer on a frame. Call once, after the frame has a content pane. */
	public static WindowResizer install(JFrame frame) {
		WindowResizer resizer = new WindowResizer(frame);
		frame.getContentPane().addMouseListener(resizer);
		frame.getContentPane().addMouseMotionListener(resizer);
		return resizer;
	}

	private boolean active() {
		return this.frame.isResizable() && !TitleBar.isMaximised(this.frame);
	}

	/** Which edges a point is on, as a bitmask. */
	private int edgesAt(Point p) {
		if (!this.active()) {
			return NONE;
		}
		int w = this.frame.getWidth();
		int h = this.frame.getHeight();
		int edges = NONE;
		if (p.y < MARGIN) {
			edges |= N;
		} else if (p.y >= h - MARGIN) {
			edges |= S;
		}
		if (p.x < MARGIN) {
			edges |= W;
		} else if (p.x >= w - MARGIN) {
			edges |= E;
		}
		return edges;
	}

	private static int cursorFor(int edges) {
		switch (edges) {
			case N: return Cursor.N_RESIZE_CURSOR;
			case S: return Cursor.S_RESIZE_CURSOR;
			case W: return Cursor.W_RESIZE_CURSOR;
			case E: return Cursor.E_RESIZE_CURSOR;
			case N | W: return Cursor.NW_RESIZE_CURSOR;
			case N | E: return Cursor.NE_RESIZE_CURSOR;
			case S | W: return Cursor.SW_RESIZE_CURSOR;
			case S | E: return Cursor.SE_RESIZE_CURSOR;
			default: return Cursor.DEFAULT_CURSOR;
		}
	}

	public void mouseMoved(MouseEvent event) {
		this.frame.setCursor(Cursor.getPredefinedCursor(cursorFor(this.edgesAt(this.inFrame(event)))));
	}

	public void mouseExited(MouseEvent event) {
		if (this.dragging == NONE) {
			this.frame.setCursor(Cursor.getDefaultCursor());
		}
	}

	public void mousePressed(MouseEvent event) {
		this.dragging = this.edgesAt(this.inFrame(event));
		this.start = event.getLocationOnScreen();
		this.startBounds = this.frame.getBounds();
	}

	public void mouseReleased(MouseEvent event) {
		this.dragging = NONE;
		this.frame.setCursor(Cursor.getDefaultCursor());
	}

	public void mouseDragged(MouseEvent event) {
		if (this.dragging == NONE || this.start == null) {
			return;
		}
		Point now = event.getLocationOnScreen();
		int dx = now.x - this.start.x;
		int dy = now.y - this.start.y;

		Rectangle want = new Rectangle(this.startBounds);
		if ((this.dragging & E) != 0) {
			want.width += dx;
		}
		if ((this.dragging & S) != 0) {
			want.height += dy;
		}
		// Dragging the top or left edge moves the opposite corner nowhere: the window grows
		// backwards, so its origin moves by as much as its size does.
		if ((this.dragging & W) != 0) {
			want.x += dx;
			want.width -= dx;
		}
		if ((this.dragging & N) != 0) {
			want.y += dy;
			want.height -= dy;
		}

		Dimension min = this.frame.getMinimumSize();
		int minW = min == null ? 1 : Math.max(1, min.width);
		int minH = min == null ? 1 : Math.max(1, min.height);
		if (want.width < minW) {
			// Clamped against the edge being pulled, so a window held at its minimum does not
			// crawl across the screen while the cursor keeps going.
			if ((this.dragging & W) != 0) {
				want.x -= minW - want.width;
			}
			want.width = minW;
		}
		if (want.height < minH) {
			if ((this.dragging & N) != 0) {
				want.y -= minH - want.height;
			}
			want.height = minH;
		}
		this.frame.setBounds(want);
		this.frame.validate();
	}

	/** The event's point in the frame's own coordinates, whichever child reported it. */
	private Point inFrame(MouseEvent event) {
		return SwingUtilities.convertPoint(event.getComponent(), event.getPoint(), this.frame);
	}
}
