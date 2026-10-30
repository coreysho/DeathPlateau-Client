/*
 * The world map, in a window of its own.
 *
 * A SWING WINDOW RATHER THAN A PAGE DRAWN ON THE GAME CANVAS, for the same reason the plugin
 * sidebar is one: the canvas is 765 by 503 and already full, and a map is the one thing that
 * wants to be bigger than the game. A separate window can be dragged to a second screen and
 * left open, which is what players do with the map in every client that has one. The applet
 * has no window and therefore has no map, the same trade the sidebar makes.
 *
 * IT DRAWS AND READS; IT DOES NOT TOUCH THE GAME. Everything in here runs on the event dispatch
 * thread and the only thing it ever calls is WorldMap.square, which hands back a finished array
 * or nothing at all. The drawing itself happens on the game thread, a square a frame, because
 * it writes statics the live game shares - so a window asking for forty squares at once costs
 * the game a frame's work per square and never a stall.
 *
 * WHAT THE PLAYER GETS. Drag to pan, wheel to zoom between four pixels a tile and one, the four
 * levels on keys 1 to 4, and their own position marked while they are logged in. A square that
 * has not been drawn yet is left as the background, so panning into unexplored ground shows
 * the map filling in rather than a frozen window.
 */
package jagex2.client.worldmap;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.Timer;

import jagex2.client.plugin.ui.Theme;

public final class MapWindow extends JFrame {

	/** The zooms, in pixels per tile. Four is what the minimap draws at; the rest are smaller. */
	private static final int[] ZOOMS = new int[] { 1, 2, 4 };

	private static final int DEFAULT_ZOOM = 2;

	/** How often the window looks again for squares the game thread has finished. */
	private static final int REPAINT_MS = 120;

	private final WorldMap map;
	private final View view = new View();

	/** Pixels per tile. */
	private int zoom = ZOOMS[DEFAULT_ZOOM];

	private int level;

	/** The world tile at the centre of the window. */
	private int centreX = 3222;
	private int centreZ = 3218;

	/** Where the drag started, in window pixels, and what the centre was then. */
	private int dragX;
	private int dragZ;
	private int dragFromX;
	private int dragFromZ;

	/** The player's tile, or -1 when they are not in the game. Written from the game thread. */
	private volatile int playerX = -1;
	private volatile int playerZ = -1;

	/** Scaled squares, keyed by the square's key and the zoom they were scaled to. */
	private final Map scaled = new HashMap();

	public MapWindow(WorldMap map) {
		super("World map");
		this.map = map;
		this.setDefaultCloseOperation(HIDE_ON_CLOSE);
		this.getContentPane().setLayout(new BorderLayout());
		this.getContentPane().add(this.view, BorderLayout.CENTER);
		this.view.setPreferredSize(new Dimension(900, 700));
		this.pack();
		this.setLocationRelativeTo(null);
		new Timer(REPAINT_MS, new java.awt.event.ActionListener() {

			public void actionPerformed(java.awt.event.ActionEvent event) {
				if (MapWindow.this.isVisible()) {
					MapWindow.this.view.repaint();
				}
			}
		}).start();
	}

	/** Puts the map over a world tile, which is how the game says "here is the player". */
	public void centreOn(int tileX, int tileZ) {
		this.centreX = tileX;
		this.centreZ = tileZ;
		this.view.repaint();
	}

	/** Where to draw the you-are-here marker, or -1 to draw none. Any thread. */
	public void setPlayer(int tileX, int tileZ, int level) {
		this.playerX = tileX;
		this.playerZ = tileZ;
		if (tileX >= 0 && this.level != level) {
			this.level = level;
			this.scaled.clear();
		}
	}

	// ------------------------------------------------------------------ where things land

	/**
	 * The window pixel the world's tile 0 would sit at, across and down.
	 *
	 * EVERYTHING ELSE IS MEASURED FROM THESE TWO and they do not agree with each other: x grows
	 * east and so does a picture, but z grows north and a picture grows down. So one of them
	 * subtracts the centre and the other adds it, which is the kind of asymmetry that looks
	 * like a typo and reads, when it is wrong, as a perfectly ordinary map of somewhere else.
	 */
	public static int originAcross(int wide, int centreX, int zoom) {
		return wide / 2 - centreX * zoom;
	}

	public static int originDown(int high, int centreZ, int zoom) {
		return high / 2 + centreZ * zoom;
	}

	/** Where a thing at tile or square x starts, across. */
	public static int acrossOf(int originX, int x, int step) {
		return originX + x * step;
	}

	/**
	 * Where a thing at tile or square z starts, down.
	 *
	 * The +1 is because a picture is placed by its top edge and z counts up from the bottom: a
	 * square at z sits between z and z+1, so its top is the far one.
	 */
	public static int downOf(int originZ, int z, int step) {
		return originZ - (z + 1) * step;
	}

	/** Where a drag of this many pixels leaves the centre. Dragging east shows what is west. */
	public static int pannedX(int fromX, int byPixels, int zoom) {
		return fromX - byPixels / zoom;
	}

	/** And down the window is north, so dragging down shows what is south. */
	public static int pannedZ(int fromZ, int byPixels, int zoom) {
		return fromZ + byPixels / zoom;
	}

	// ------------------------------------------------------------------ the map itself

	private final class View extends JPanel {

		View() {
			this.setBackground(Theme.DARKER);
			this.setFocusable(true);
			this.addMouseListener(new MouseAdapter() {

				public void mousePressed(MouseEvent event) {
					View.this.requestFocusInWindow();
					MapWindow.this.dragX = event.getX();
					MapWindow.this.dragZ = event.getY();
					MapWindow.this.dragFromX = MapWindow.this.centreX;
					MapWindow.this.dragFromZ = MapWindow.this.centreZ;
				}
			});
			this.addMouseMotionListener(new MouseMotionAdapter() {

				public void mouseDragged(MouseEvent event) {
					int at = MapWindow.this.zoom;
					MapWindow.this.centreX = pannedX(MapWindow.this.dragFromX,
						event.getX() - MapWindow.this.dragX, at);
					MapWindow.this.centreZ = pannedZ(MapWindow.this.dragFromZ,
						event.getY() - MapWindow.this.dragZ, at);
					View.this.repaint();
				}
			});
			this.addMouseWheelListener(new MouseWheelListener() {

				public void mouseWheelMoved(MouseWheelEvent event) {
					MapWindow.this.zoomBy(event.getWheelRotation() < 0 ? 1 : -1);
				}
			});
			this.addKeyListener(new KeyAdapter() {

				public void keyPressed(KeyEvent event) {
					int code = event.getKeyCode();
					if (code >= KeyEvent.VK_1 && code <= KeyEvent.VK_4) {
						MapWindow.this.level = code - KeyEvent.VK_1;
						MapWindow.this.scaled.clear();
						View.this.repaint();
					} else if (code == KeyEvent.VK_ESCAPE) {
						MapWindow.this.setVisible(false);
					}
				}
			});
		}

		public void paintComponent(Graphics graphics) {
			super.paintComponent(graphics);
			Graphics2D g = (Graphics2D) graphics;
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
				RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

			int at = MapWindow.this.zoom;
			int cell = MapSquare.SQUARE * at;
			int wide = this.getWidth();
			int high = this.getHeight();
			int originX = originAcross(wide, MapWindow.this.centreX, at);
			int originZ = originDown(high, MapWindow.this.centreZ, at);

			int firstX = Math.floorDiv(-originX, cell);
			int lastX = Math.floorDiv(wide - originX, cell);
			int firstZ = Math.floorDiv(originZ - high, cell);
			int lastZ = Math.floorDiv(originZ, cell);
			for (int x = firstX; x <= lastX; x++) {
				for (int z = firstZ; z <= lastZ; z++) {
					if (x < 0 || z < 0 || x > 0xFF || z > 0xFF) {
						continue;
					}
					BufferedImage one = MapWindow.this.square(x, z, at);
					if (one != null) {
						g.drawImage(one, acrossOf(originX, x, cell), downOf(originZ, z, cell),
							null);
					}
				}
			}
			MapWindow.this.drawPlayer(g, originX, originZ, at);
			MapWindow.this.drawLegend(g, high);
		}
	}

	/** A square at the zoom being drawn, scaled once and kept, or null if it is not drawn yet. */
	private BufferedImage square(int x, int z, int at) {
		Long which = Long.valueOf(((long) WorldMap.key(x, z, this.level) << 8) + at);
		BufferedImage have = (BufferedImage) this.scaled.get(which);
		if (have != null) {
			return have;
		}
		int[] pixels = this.map.square(x, z, this.level);
		if (pixels == null) {
			return null;
		}
		int side = MapSquare.SIDE;
		BufferedImage full = new BufferedImage(side, side, BufferedImage.TYPE_INT_RGB);
		full.setRGB(0, 0, side, side, pixels, 0, side);
		BufferedImage out = full;
		if (at != MapSquare.SCALE) {
			int cell = MapSquare.SQUARE * at;
			out = new BufferedImage(cell, cell, BufferedImage.TYPE_INT_RGB);
			Graphics2D g = out.createGraphics();
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
				RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g.drawImage(full, 0, 0, cell, cell, null);
			g.dispose();
		}
		// A SMALL CACHE OF SCALED SQUARES, cleared wholesale rather than aged: zooming or
		// changing level invalidates all of them at once, and panning only ever adds a ring.
		if (this.scaled.size() > 256) {
			this.scaled.clear();
		}
		this.scaled.put(which, out);
		return out;
	}

	private void drawPlayer(Graphics2D g, int originX, int originZ, int at) {
		int x = this.playerX;
		int z = this.playerZ;
		if (x < 0) {
			return;
		}
		int px = acrossOf(originX, x, at);
		int pz = downOf(originZ, z, at);
		int size = at < 4 ? 5 : 7;
		g.setColor(Color.WHITE);
		g.fillOval(px - size / 2, pz - size / 2, size, size);
		g.setColor(Theme.ACCENT_BRIGHT);
		g.fillOval(px - size / 2 + 1, pz - size / 2 + 1, size - 2, size - 2);
	}

	private void drawLegend(Graphics2D g, int high) {
		g.setColor(Theme.TITLE_BAR);
		g.fillRect(0, high - 22, 320, 22);
		g.setColor(Theme.TEXT_DIM);
		g.setFont(Theme.FONT_SMALL);
		g.drawString("drag to pan   wheel to zoom   1-4 for the floor"
			+ "      level " + this.level, 8, high - 7);
	}

	private void zoomBy(int steps) {
		int at = 0;
		for (int i = 0; i < ZOOMS.length; i++) {
			if (ZOOMS[i] == this.zoom) {
				at = i;
			}
		}
		int want = at + steps;
		if (want < 0) {
			want = 0;
		}
		if (want >= ZOOMS.length) {
			want = ZOOMS.length - 1;
		}
		if (ZOOMS[want] != this.zoom) {
			this.zoom = ZOOMS[want];
			this.scaled.clear();
			this.view.repaint();
		}
	}
}
