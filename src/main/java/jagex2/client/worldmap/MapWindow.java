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
 * thread and the only things it ever call are WorldMap.square and the icon index, both of which
 * hand back finished work or nothing at all. The drawing of a square happens on the game thread,
 * one a frame, because it writes statics the live game shares - so a window asking for forty
 * squares at once costs the game a frame's work per square and never a stall.
 *
 * WHAT THE PLAYER GETS. Drag to pan, the wheel or the two buttons to zoom, the four levels on
 * keys 1 to 4, their own position marked with the yellow cross, every bank and altar and
 * fishing spot drawn where the content put it, a key down the side saying what each icon is
 * and how many there are, and a search that takes them to the nearest one.
 *
 * ICONS ARE DRAWN AT ONE SIZE WHATEVER THE ZOOM. Shrinking them with the map would make them
 * four pixels across exactly when a player can see a county at once, which is when they are
 * looking for a bank rather than at one.
 */
package jagex2.client.worldmap;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.Timer;

import jagex2.client.plugin.ui.Theme;

public final class MapWindow extends JFrame {

	/** The zooms, in pixels per tile. Four is what the minimap draws at; the rest are smaller. */
	private static final int[] ZOOMS = new int[] { 1, 2, 4 };

	private static final int DEFAULT_ZOOM = 2;

	/** How often the window looks again for squares the game thread has finished. */
	private static final int REPAINT_MS = 120;

	/** The yellow of the you-are-here cross, and of a ring round a search result. */
	private static final Color MARKER = new Color(0xFF, 0xE0, 0x00);

	private final WorldMap map;
	private final View view = new View();
	private final JTextField search = new JTextField(14);
	private final JLabel found = new JLabel("type a place and press enter");
	private final JLabel where = new JLabel(" ");
	private final JLabel floor = new JLabel("level 0");
	private final KeyPanel key = new KeyPanel();
	private final JScrollPane keyScroll;

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

	/** What the search is showing, and which of them the view is on. */
	private List matches;
	private int matchAt;

	/** One icon kind to show on its own, or -1 for all of them. */
	private int only = -1;

	/** Scaled squares, keyed by the square's key and the zoom they were scaled to. */
	private final Map scaled = new HashMap();

	public MapWindow(WorldMap map) {
		super("World map");
		this.map = map;
		this.setDefaultCloseOperation(HIDE_ON_CLOSE);
		this.getContentPane().setBackground(Theme.BACKGROUND);
		this.getContentPane().setLayout(new BorderLayout());
		this.view.setPreferredSize(new Dimension(900, 680));
		this.getContentPane().add(this.view, BorderLayout.CENTER);

		this.keyScroll = new JScrollPane(this.key,
			JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		this.keyScroll.setPreferredSize(new Dimension(172, 10));
		this.keyScroll.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, Theme.SEPARATOR));
		this.keyScroll.getViewport().setBackground(Theme.BACKGROUND);
		this.getContentPane().add(this.keyScroll, BorderLayout.WEST);

		this.getContentPane().add(this.bar(), BorderLayout.SOUTH);
		this.key.refresh();
		this.pack();
		this.setLocationRelativeTo(null);
		new Timer(REPAINT_MS, new ActionListener() {

			public void actionPerformed(ActionEvent event) {
				if (MapWindow.this.isVisible()) {
					MapWindow.this.view.repaint();
					MapWindow.this.key.refresh();
				}
			}
		}).start();
	}

	// ------------------------------------------------------------------ the bar along the bottom

	private JPanel bar() {
		JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 5));
		row.setBackground(Theme.TITLE_BAR);
		row.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.SEPARATOR));

		JLabel prompt = new JLabel("Search");
		prompt.setForeground(Theme.TEXT_DIM);
		prompt.setFont(Theme.FONT_SMALL);
		row.add(prompt);

		this.search.setBackground(Theme.ROW);
		this.search.setForeground(Theme.TEXT);
		this.search.setCaretColor(Theme.TEXT);
		this.search.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(Theme.SEPARATOR),
			BorderFactory.createEmptyBorder(3, 5, 3, 5)));
		this.search.addActionListener(new ActionListener() {

			public void actionPerformed(ActionEvent event) {
				MapWindow.this.nextMatch();
			}
		});
		row.add(this.search);

		this.found.setForeground(Theme.TEXT_DIM);
		this.found.setFont(Theme.FONT_SMALL);
		this.found.setPreferredSize(new Dimension(230, 18));
		row.add(this.found);

		row.add(this.flat("Key", new ActionListener() {

			public void actionPerformed(ActionEvent event) {
				MapWindow.this.keyScroll.setVisible(!MapWindow.this.keyScroll.isVisible());
				MapWindow.this.getContentPane().revalidate();
				MapWindow.this.getContentPane().repaint();
			}
		}));
		row.add(this.flat("−", new ActionListener() {

			public void actionPerformed(ActionEvent event) {
				MapWindow.this.zoomBy(-1);
			}
		}));
		row.add(this.flat("+", new ActionListener() {

			public void actionPerformed(ActionEvent event) {
				MapWindow.this.zoomBy(1);
			}
		}));

		this.where.setForeground(Theme.TEXT_DIM);
		this.where.setFont(Theme.FONT_SMALL);
		this.where.setPreferredSize(new Dimension(80, 18));
		row.add(this.where);

		// THE FLOOR, ALWAYS SHOWN. A search for a bank lands on the first floor of Lumbridge
		// castle, because that is where the bank is - and a map that silently changes storey
		// looks like a map that has gone blank.
		this.floor.setForeground(Theme.TEXT);
		this.floor.setFont(Theme.FONT_SMALL);
		this.floor.setPreferredSize(new Dimension(58, 18));
		row.add(this.floor);
		row.add(Box.createHorizontalStrut(2));
		return row;
	}

	private JButton flat(String text, ActionListener onClick) {
		JButton button = new JButton(text);
		button.setFocusPainted(false);
		button.setBackground(Theme.ROW);
		button.setForeground(Theme.TEXT);
		button.setFont(Theme.FONT);
		button.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(Theme.SEPARATOR),
			BorderFactory.createEmptyBorder(2, 9, 2, 9)));
		button.addActionListener(onClick);
		return button;
	}

	// ------------------------------------------------------------------ what the game tells it

	/** Puts the map over a world tile, which is how the game says "here is the player". */
	public void centreOn(int tileX, int tileZ) {
		this.centreX = tileX;
		this.centreZ = tileZ;
		this.view.repaint();
	}

	/** Where to draw the you-are-here cross, or -1 to draw none. Any thread. */
	public void setPlayer(int tileX, int tileZ, int level) {
		this.playerX = tileX;
		this.playerZ = tileZ;
		if (tileX >= 0 && this.level != level) {
			this.setLevel(level);
		}
	}

	// ------------------------------------------------------------------ searching

	/**
	 * Shows the next thing matching what was typed, nearest to the player first.
	 *
	 * ENTER AGAIN GOES TO THE ONE AFTER, because "bank" has thirty-nine answers and the second
	 * nearest is a perfectly ordinary thing to want. The whole set stays ringed on the map
	 * while the box has anything in it, so a player can see where the others are.
	 */
	private void nextMatch() {
		int fromX = this.playerX >= 0 ? this.playerX : this.centreX;
		int fromZ = this.playerX >= 0 ? this.playerZ : this.centreZ;
		List hits = this.map.icons().search(this.search.getText(), fromX, fromZ);
		if (hits.isEmpty()) {
			this.matches = null;
			this.found.setText(this.search.getText().trim().length() == 0
				? " " : "nothing called that");
			this.view.repaint();
			return;
		}
		if (this.matches == null || this.matches.size() != hits.size()) {
			this.matchAt = 0;
		} else {
			this.matchAt = (this.matchAt + 1) % hits.size();
		}
		this.matches = hits;
		MapIcons.Icon icon = (MapIcons.Icon) hits.get(this.matchAt);
		this.setLevel(icon.level);
		this.centreOn(icon.x, icon.z);
		this.found.setText(icon.name() + "  " + (this.matchAt + 1) + " of " + hits.size()
			+ "   (" + icon.x + ", " + icon.z + ")");
	}

	/** Types into the search box and presses enter, which is what the preview needs to see it. */
	public void typeSearch(String text) {
		this.search.setText(text);
		this.nextMatch();
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

	/** The world tile under a window pixel, which is what the readout at the bottom says. */
	public static int tileAcross(int px, int originX, int zoom) {
		return Math.floorDiv(px - originX, zoom);
	}

	public static int tileDown(int py, int originZ, int zoom) {
		return Math.floorDiv(originZ - py, zoom);
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

				public void mouseMoved(MouseEvent event) {
					MapWindow.this.readOut(event.getX(), event.getY(),
						View.this.getWidth(), View.this.getHeight());
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
						MapWindow.this.setLevel(code - KeyEvent.VK_1);
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
			MapWindow.this.drawIcons(g, originX, originZ, at,
				firstX * MapSquare.SQUARE, firstZ * MapSquare.SQUARE,
				(lastX + 1) * MapSquare.SQUARE, (lastZ + 1) * MapSquare.SQUARE);
			MapWindow.this.drawPlayer(g, originX, originZ, at);
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

	private void drawIcons(Graphics2D g, int originX, int originZ, int at,
			int x0, int z0, int x1, int z1) {
		List here = this.map.icons().within(x0, z0, x1, z1, this.level);
		for (int i = 0; i < here.size(); i++) {
			MapIcons.Icon icon = (MapIcons.Icon) here.get(i);
			if (this.only >= 0 && icon.function != this.only) {
				continue;
			}
			BufferedImage art = MapIcons.sprite(icon.function);
			if (art == null) {
				continue;
			}
			// Centred on the tile rather than hung off its corner, so an icon marks the thing
			// it is on at every zoom.
			int px = acrossOf(originX, icon.x, at) + at / 2 - art.getWidth() / 2;
			int pz = downOf(originZ, icon.z, at) + at / 2 - art.getHeight() / 2;
			g.drawImage(art, px, pz, null);
			if (this.ringed(icon)) {
				g.setColor(MARKER);
				g.drawOval(px - 2, pz - 2, art.getWidth() + 3, art.getHeight() + 3);
			}
		}
	}

	private boolean ringed(MapIcons.Icon icon) {
		return this.matches != null && this.matches.contains(icon);
	}

	/**
	 * The player, as the yellow cross the game's own map uses.
	 *
	 * A CROSS RATHER THAN A DOT because it has to be findable on ground of any colour, and a
	 * dot the size of a tile is lost on a town. It is drawn with a dark outline for the same
	 * reason: yellow on sand is yellow on yellow.
	 */
	private void drawPlayer(Graphics2D g, int originX, int originZ, int at) {
		int x = this.playerX;
		int z = this.playerZ;
		if (x < 0) {
			return;
		}
		int px = acrossOf(originX, x, at) + at / 2;
		int pz = downOf(originZ, z, at) + at / 2;
		int arm = at < 4 ? 5 : 7;
		g.setStroke(new java.awt.BasicStroke(4f));
		g.setColor(new Color(0x20, 0x18, 0x00));
		g.drawLine(px - arm, pz - arm, px + arm, pz + arm);
		g.drawLine(px - arm, pz + arm, px + arm, pz - arm);
		g.setStroke(new java.awt.BasicStroke(2f));
		g.setColor(MARKER);
		g.drawLine(px - arm, pz - arm, px + arm, pz + arm);
		g.drawLine(px - arm, pz + arm, px + arm, pz - arm);
		g.setStroke(new java.awt.BasicStroke(1f));
	}

	private void setLevel(int to) {
		this.level = to;
		this.floor.setText("level " + to);
		this.scaled.clear();
	}

	private void readOut(int px, int py, int wide, int high) {
		int at = this.zoom;
		int x = tileAcross(px, originAcross(wide, this.centreX, at), at);
		int z = tileDown(py, originDown(high, this.centreZ, at), at);
		this.where.setText(x + ", " + z);
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

	// ------------------------------------------------------------------ the key

	/**
	 * What each icon means, and how many of them the map has found.
	 *
	 * THE COUNT IS THE HONEST PART. The map only knows about squares whose scenery has arrived,
	 * so on a fresh account the key starts nearly empty and fills in behind the prefetch.
	 * Printing a count rather than a bare list is what makes that visible instead of puzzling.
	 *
	 * Clicking a row shows that kind on its own, which is the only way to find the one fishing
	 * spot on a coast covered in them.
	 */
	private final class KeyPanel extends JPanel {

		private final JLabel[] rows;

		private int[] shown = new int[0];

		KeyPanel() {
			this.setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
			this.setBackground(Theme.BACKGROUND);
			this.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
			JLabel title = new JLabel("Key");
			title.setForeground(Theme.ACCENT);
			title.setFont(Theme.FONT);
			title.setBorder(BorderFactory.createEmptyBorder(0, 2, 6, 0));
			this.add(title);
			this.rows = new JLabel[MapIconNames.NAME.length];
			for (int function = 0; function < this.rows.length; function++) {
				if (MapIconNames.name(function).length() == 0) {
					continue;
				}
				this.rows[function] = this.row(function);
				this.add(this.rows[function]);
			}
			this.add(Box.createVerticalGlue());
		}

		private JLabel row(final int function) {
			JLabel label = new JLabel(MapIconNames.name(function));
			label.setForeground(Theme.TEXT_DIM);
			label.setFont(Theme.FONT_SMALL);
			label.setHorizontalAlignment(SwingConstants.LEFT);
			label.setBorder(BorderFactory.createEmptyBorder(1, 2, 1, 2));
			BufferedImage art = MapIcons.sprite(function);
			if (art != null) {
				label.setIcon(new javax.swing.ImageIcon(art));
			}
			label.setIconTextGap(6);
			label.addMouseListener(new MouseAdapter() {

				public void mousePressed(MouseEvent event) {
					MapWindow.this.only = MapWindow.this.only == function ? -1 : function;
					MapWindow.this.view.repaint();
					KeyPanel.this.refresh();
				}
			});
			return label;
		}

		/** Re-counts and re-colours, which is cheap and keeps the key honest while files land. */
		void refresh() {
			// isVisible rather than isShowing: the key being toggled off is a reason to skip
			// the work, but a window not yet on screen is not - that is the one state the
			// preview paints in, and a key that only fills in once a human is looking at it
			// is a key nothing can check.
			if (!this.isVisible()) {
				return;
			}
			MapIcons icons = MapWindow.this.map.icons();
			if (this.shown.length != this.rows.length) {
				this.shown = new int[this.rows.length];
				for (int i = 0; i < this.shown.length; i++) {
					this.shown[i] = -1;
				}
			}
			for (int function = 0; function < this.rows.length; function++) {
				JLabel label = this.rows[function];
				if (label == null) {
					continue;
				}
				int n = icons.count(function);
				boolean picked = MapWindow.this.only == function;
				if (this.shown[function] == n && label.getForeground() != Theme.TEXT_DIM
						== picked) {
					continue;
				}
				this.shown[function] = n;
				label.setText(MapIconNames.name(function) + (n > 0 ? "  " + n : ""));
				label.setForeground(picked ? Theme.ACCENT : n > 0 ? Theme.TEXT : Theme.TEXT_DIM);
			}
		}
	}
}
