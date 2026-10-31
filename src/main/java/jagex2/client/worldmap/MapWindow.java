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
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
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

import lostcity.Theme;

public final class MapWindow extends JFrame {

	/** The zooms, in pixels per tile. Four is what the minimap draws at; the rest are smaller. */
	private static final int[] ZOOMS = new int[] { 1, 2, 4 };

	private static final int DEFAULT_ZOOM = 2;

	/** How often the window looks again for squares the game thread has finished. */
	private static final int REPAINT_MS = 120;

	/** The yellow of the you-are-here cross, and of a ring round a search result. */
	private static final Color MARKER = new Color(0xFF, 0xE0, 0x00);

	/** The marked spot's colour, which is not the player's: two the same read as two players. */
	private static final Color MARK = new Color(0x40, 0xFF, 0x60);

	/**
	 * The cream the content letters its own maps in (playermap_east.if), so the names on this
	 * map match the names on the ones the game draws itself.
	 */
	private static final Color LABEL = new Color(0xFA, 0xD7, 0x82);

	/**
	 * Where underground stops being underground.
	 *
	 * THE WORLD IS TWO BANDS, not one map. Of this build's 702 squares, 523 are surface in rows
	 * 40 to 85 and 179 are underground in rows 142 to 161, with fifty-six empty rows between
	 * them - so a quarter of the map could be drawn perfectly and still never be seen, because
	 * reaching it meant dragging through nothing.
	 *
	 * AND THE TWO BANDS LINE UP. 175 of those 179 underground squares sit exactly a hundred
	 * rows north of the surface square they are under, which is what makes a toggle possible
	 * rather than a menu: the place under here is at the same x, this z plus six thousand four
	 * hundred. The other four have no twin, and those fall back to the nearest square there is.
	 */
	private static final int UNDER_FROM = 140 * MapSquare.SQUARE;

	private static final int UNDER_STEP = 100 * MapSquare.SQUARE;

	private final WorldMap map;
	private final View view = new View();
	private final JTextField search = new JTextField(14);
	private final JLabel found = new JLabel("type a place and press enter");
	private final JLabel where = new JLabel(" ");
	private final JLabel floor = new JLabel("level 0");
	private final KeyPanel key = new KeyPanel();
	private final JScrollPane keyScroll;

	/** The two buttons whose words depend on where the map is, and whether anyone is in it. */
	private JButton depth;
	private JButton here;

	/**
	 * Three sizes of lettering, one per rank, from the window's own font family.
	 *
	 * A KINGDOM AND A COW PEN CANNOT BE WRITTEN THE SAME SIZE or the map has no shape to it:
	 * what a player reads first should be the biggest thing they are looking at. Derived from
	 * Theme rather than named outright so the map letters in the same face as everything else.
	 */
	private final Font regionFont = Theme.FONT_BOLD.deriveFont(15f);
	private final Font townFont = Theme.FONT_BOLD.deriveFont(13f);
	private final Font placeFont = Theme.FONT_SMALL;

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
	private volatile int playerLevel;

	/** The marked spot, or -1 for none. Written from the game thread, like the player's. */
	private volatile int markX = -1;
	private volatile int markZ = -1;
	private volatile int markLevel;
	private volatile String markLabel = "";

	/** What the search is showing, and which of them the view is on. */
	private List matches;
	private int matchAt;

	/** One icon kind to show on its own, or -1 for all of them. */
	private int only = -1;

	/**
	 * Scaled squares, keyed by the square, its level AND the zoom they were scaled to.
	 *
	 * NEVER THROWN AWAY WHOLESALE, which it used to be - on every zoom and every change of
	 * floor, and again whenever it passed 256 entries. Zoomed right out a window this size
	 * shows a hundred and forty squares at once and a big one five hundred, so a wholesale
	 * clear meant every one of them had to come back from the game thread at one square a
	 * frame, and what the player saw was most of the map going black and crawling back. The
	 * key already carries the zoom and the level, so nothing in here is ever stale: the oldest
	 * goes when there are too many, and that is all.
	 */
	private final Map scaled = new LinkedHashMap(16, 0.75f, true) {

		protected boolean removeEldestEntry(Map.Entry eldest) {
			// A square scaled to one pixel a tile is 16k; even a thousand of them is a few
			// megabytes, and a thousand is more than any window can show at once.
			return this.size() > 1024;
		}
	};

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
					// A DRAG CAN CROSS THE BAND, and logging in is what gives Me anything to
					// do, so neither button's word is set once and left.
					MapWindow.this.refreshButtons();
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

		// THE WAY INTO THE OTHER HALF OF THE MAP. Its word says where it will take you and not
		// where you are, because a button labelled with the view it is already showing reads as
		// a statement rather than as something to press.
		this.depth = this.flat("Underground", new ActionListener() {

			public void actionPerformed(ActionEvent event) {
				MapWindow.this.toggleDepth();
			}
		});
		this.depth.setToolTipText("Jump between the surface and the dungeons under it");
		row.add(this.depth);

		// AND THE WAY BACK. Panning is how this map is read, and a map with no way home is one
		// a player closes and reopens to recentre - which is exactly what the orb does, so the
		// want is already proven.
		this.here = this.flat("Me", new ActionListener() {

			public void actionPerformed(ActionEvent event) {
				MapWindow.this.centreOnPlayer();
			}
		});
		this.here.setToolTipText("Put the map back over your character (Home)");
		row.add(this.here);

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
	/**
	 * Showing or hiding the window, and telling the map which it is.
	 *
	 * EVERY WAY OUT OF THIS WINDOW HAS TO COUNT, and until now only one did. The orb and F7 go
	 * through Client.toggleWorldMap, which said closed() itself - but Escape calls setVisible
	 * straight (see the view's key handler) and the title bar's X is HIDE_ON_CLOSE, and neither
	 * told the map anything. A map that has not been told nobody is looking keeps its wanted
	 * list, and every file that arrives afterwards wakes the whole list up again.
	 *
	 * So it is the WINDOW that says so, because the window is the thing that knows: there is
	 * one way to become visible and one to stop being, and both of them are here.
	 */
	public void setVisible(boolean showing) {
		super.setVisible(showing);
		if (showing) {
			this.map.opened();
		} else {
			this.map.closed();
		}
	}

	public void centreOn(int tileX, int tileZ) {
		this.centreX = tileX;
		this.centreZ = tileZ;
		this.view.repaint();
	}

	/** Where to draw the you-are-here cross, or -1 to draw none. Any thread. */
	/**
	 * A mark somewhere in the world, with a word for it, or x below zero for none.
	 *
	 * ONE MARK, not a set, because one is what there is to show: where the clue in your bag
	 * wants you. A second thing worth marking is the moment to key these by whoever put them
	 * there - until then, a single one is honest about what it is.
	 */
	public void setMark(int tileX, int tileZ, int level, String label) {
		this.markX = tileX;
		this.markZ = tileZ;
		this.markLevel = level;
		this.markLabel = label == null ? "" : label;
	}

	public void setPlayer(int tileX, int tileZ, int level) {
		this.playerX = tileX;
		this.playerZ = tileZ;
		// KEPT, NOT JUST ACTED ON: the Me button has to know which storey to put the map back
		// to, and by the time it is pressed this has been called four hundred times.
		this.playerLevel = level;
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

	/** Whether a world z is in the underground band rather than on the surface. */
	public static boolean isUnderground(int tileZ) {
		return tileZ >= UNDER_FROM;
	}

	/**
	 * The same place in the other band: under here if we are above, above here if we are under.
	 *
	 * One step either way rather than two functions, because the map has exactly two bands and
	 * this is the content's own relation between them - six thousand four hundred tiles.
	 */
	public static int twinOf(int tileZ) {
		return isUnderground(tileZ) ? tileZ - UNDER_STEP : tileZ + UNDER_STEP;
	}

	/**
	 * Where the jump out of this band lands, or -1 if one step would not leave it.
	 *
	 * A STEP IS NOT ALWAYS A BAND CHANGE, which is the hole a round-trip check over every z
	 * found: the underground band starts at row 140 and the step is a hundred rows, so from
	 * row 240 and up - far north of the last square the world has, but reachable by dragging -
	 * taking a hundred rows off still leaves you underground. A button that reads "Surface"
	 * and then does not surface is worse than one that admits it has to go somewhere else, so
	 * this says -1 and the caller falls back to the nearest square in the band it wants.
	 */
	public static int jumpTo(int fromZ) {
		int to = twinOf(fromZ);
		return isUnderground(to) == isUnderground(fromZ) ? -1 : to;
	}

	/**
	 * Whether a label's box can go down without touching one already placed.
	 *
	 * TOUCHING AND NOT MERELY OVERLAPPING: two names whose boxes share an edge are two names
	 * with no gap between them, which reads as one longer name.
	 */
	public static boolean roomFor(List taken, Rectangle box) {
		for (int i = 0; i < taken.size(); i++) {
			if (((Rectangle) taken.get(i)).intersects(box)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Whether a label of this rank is worth drawing at this zoom.
	 *
	 * ALL HUNDRED AND NINETEEN AT ONCE IS NOT A MAP, it is a list with ground behind it. Zoomed
	 * right out a window shows four hundred squares, so only the kingdoms and the towns get a
	 * name there - thirty of them - and the guilds, mines and camps appear as soon as there is
	 * room to read them.
	 */
	public static boolean labelShown(int rank, int zoom) {
		return rank > MapLabels.RANK_PLACE || zoom > 1;
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
					} else if (code == KeyEvent.VK_HOME) {
						MapWindow.this.centreOnPlayer();
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
			// LABELS OVER THE ICONS, which is the way round the picture settled. Under them
			// was the first guess and it argued itself well - a bank icon with the word
			// Varrock across it is a bank you cannot find - but the view that needs names
			// most is the one zoomed right out, and there eight hundred icons cover the
			// county so thickly that half the towns had no readable name at all. A word is
			// thin and carries its own shadow; what it crosses is still legible around the
			// letters, and an icon is findable by zooming in, which a name is not.
			MapWindow.this.drawLabels(g, originX, originZ, at,
				firstX * MapSquare.SQUARE, firstZ * MapSquare.SQUARE,
				(lastX + 1) * MapSquare.SQUARE, (lastZ + 1) * MapSquare.SQUARE);
			MapWindow.this.drawMark(g, originX, originZ, at);
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
		this.scaled.put(which, out);
		return out;
	}

	/**
	 * The names of places, over the ground they name.
	 *
	 * ANTIALIASED, unlike everything else in this window. The squares are drawn nearest
	 * neighbour on purpose - a map scaled smoothly is a map of nowhere in particular - but
	 * lettering at eleven points with no antialiasing is lettering nobody can read, so the
	 * hint goes on for the text and comes straight back off.
	 *
	 * CENTRED ON THE PLACE AND NOT HUNG OFF IT, with each line under the last, which is what
	 * the slash in the content's names is for.
	 */
	private void drawLabels(Graphics2D g, int originX, int originZ, int at,
			int x0, int z0, int x1, int z1) {
		Object was = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		// BIGGEST FIRST, so that when two names want the same pixels the kingdom keeps them
		// and the mine gives way. Underground, where Falador Mine and Draynor Basement are
		// nineteen tiles apart, the two overprinted into one unreadable word.
		List taken = new ArrayList();
		for (int rank = MapLabels.RANK_REGION; rank >= MapLabels.RANK_PLACE; rank--) {
			if (!labelShown(rank, at)) {
				continue;
			}
			for (int i = 0; i < MapLabels.NAME.length; i++) {
				if (MapLabels.RANK[i] != rank) {
					continue;
				}
				int x = MapLabels.X[i];
				int z = MapLabels.Z[i];
				if (x < x0 || x > x1 || z < z0 || z > z1) {
					continue;
				}
				g.setFont(rank == MapLabels.RANK_REGION ? MapWindow.this.regionFont
					: rank == MapLabels.RANK_TOWN ? MapWindow.this.townFont
					: MapWindow.this.placeFont);
				String[] lines = MapLabels.lines(MapLabels.NAME[i]);
				int line = g.getFontMetrics().getHeight();
				int widest = 0;
				for (int part = 0; part < lines.length; part++) {
					widest = Math.max(widest, g.getFontMetrics().stringWidth(lines[part]));
				}
				int px = acrossOf(originX, x, at) + at / 2;
				int pz = downOf(originZ, z, at) + at / 2 - (lines.length - 1) * line / 2;
				Rectangle box = new Rectangle(px - widest / 2, pz - line,
					widest, lines.length * line);
				if (!roomFor(taken, box)) {
					continue;
				}
				taken.add(box);
				for (int part = 0; part < lines.length; part++) {
					int wide = g.getFontMetrics().stringWidth(lines[part]);
					int tx = px - wide / 2;
					int ty = pz + part * line;
					// A DARK COPY UNDER IT, because this lettering lands on grass, sand,
					// stone and water in the same window and no one colour reads on all four.
					g.setColor(Color.BLACK);
					g.drawString(lines[part], tx + 1, ty + 1);
					g.setColor(LABEL);
					g.drawString(lines[part], tx, ty);
				}
			}
		}
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
			was == null ? RenderingHints.VALUE_ANTIALIAS_DEFAULT : was);
	}

	/**
	 * Jumps to the same place in the other band, or to the nearest thing there is to it.
	 *
	 * THE NEAREST SQUARE RATHER THAN NOTHING. Four of the underground squares have no surface
	 * twin and the empty rows between the bands have no squares at all, so a jump that insisted
	 * on an exact twin would do nothing at all on a map centred a few tiles off - which reads
	 * as a broken button, not as an honest refusal.
	 */
	private void toggleDepth() {
		boolean wantUnder = !isUnderground(this.centreZ);
		int wantZ = jumpTo(this.centreZ);
		if (wantZ >= 0
				&& this.map.exists(this.centreX / MapSquare.SQUARE, wantZ / MapSquare.SQUARE)) {
			this.centreOn(this.centreX, wantZ);
			this.refreshButtons();
			return;
		}
		int near = this.nearest(this.centreX, wantZ < 0 ? this.centreZ : wantZ, wantUnder);
		if (near < 0) {
			return;
		}
		// The middle of that square rather than its corner, so the jump lands on ground.
		this.centreOn((near >> 8) * MapSquare.SQUARE + MapSquare.SQUARE / 2,
			(near & 0xFF) * MapSquare.SQUARE + MapSquare.SQUARE / 2);
		this.refreshButtons();
	}

	/** The nearest square the world has in one band, as (x &lt;&lt; 8) + z, or -1 if it has none. */
	private int nearest(int tileX, int tileZ, boolean underground) {
		int fromX = tileX / MapSquare.SQUARE;
		int fromZ = tileZ / MapSquare.SQUARE;
		int firstZ = underground ? UNDER_FROM / MapSquare.SQUARE : 0;
		int lastZ = underground ? 0xFF : UNDER_FROM / MapSquare.SQUARE - 1;
		int best = -1;
		long nearest = Long.MAX_VALUE;
		for (int x = 0; x <= 0xFF; x++) {
			for (int z = firstZ; z <= lastZ; z++) {
				if (!this.map.exists(x, z)) {
					continue;
				}
				long acrossBy = x - fromX;
				long downBy = z - fromZ;
				long away = acrossBy * acrossBy + downBy * downBy;
				if (away < nearest) {
					nearest = away;
					best = (x << 8) + z;
				}
			}
		}
		return best;
	}

	/** Puts the map back over the player, on their storey. Does nothing if nobody is in. */
	public void centreOnPlayer() {
		int x = this.playerX;
		int z = this.playerZ;
		if (x < 0) {
			return;
		}
		this.setLevel(this.playerLevel);
		this.centreOn(x, z);
		this.refreshButtons();
	}

	/**
	 * What the two buttons say, which depends on the map and not on the press.
	 *
	 * Only when it changes: this runs eight times a second, and setText on a Swing button
	 * revalidates the row it is in.
	 */
	private void refreshButtons() {
		String word = isUnderground(this.centreZ) ? "Surface" : "Underground";
		if (this.depth != null && !word.equals(this.depth.getText())) {
			this.depth.setText(word);
		}
		if (this.here != null && this.here.isEnabled() != (this.playerX >= 0)) {
			this.here.setEnabled(this.playerX >= 0);
		}
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
	/**
	 * The marked spot: a ring and a flag, and its word under it.
	 *
	 * DELIBERATELY NOT THE PLAYER'S CROSS. Two crosses on one map is a map with two players on
	 * it; what this says is "the thing you are looking for is here", so it is a different shape
	 * and a different colour, and it carries its own label because a ring on its own could be
	 * anything.
	 */
	private void drawMark(Graphics2D g, int originX, int originZ, int at) {
		if (this.markX < 0 || this.markLevel != this.level) {
			return;
		}
		int px = acrossOf(originX, this.markX, at) + at / 2;
		int pz = downOf(originZ, this.markZ, at) + at / 2;
		int ring = at < 4 ? 7 : 10;
		g.setStroke(new java.awt.BasicStroke(3f));
		g.setColor(new Color(0x20, 0x18, 0x00));
		g.drawOval(px - ring, pz - ring, ring * 2, ring * 2);
		g.setStroke(new java.awt.BasicStroke(2f));
		g.setColor(MARK);
		g.drawOval(px - ring, pz - ring, ring * 2, ring * 2);
		g.drawLine(px, pz - ring, px, pz + ring);
		g.drawLine(px - ring, pz, px + ring, pz);
		g.setStroke(new java.awt.BasicStroke(1f));
		if (this.markLabel.length() > 0) {
			int width = g.getFontMetrics().stringWidth(this.markLabel);
			int tx = px - width / 2;
			int ty = pz + ring + 14;
			g.setColor(new Color(0x20, 0x18, 0x00));
			g.drawString(this.markLabel, tx + 1, ty + 1);
			g.setColor(MARK);
			g.drawString(this.markLabel, tx, ty);
		}
	}

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
			// ONE PASS, NOT SIXTY-FOUR. Counting each kind separately walks the whole icon
			// list once per row, four times a second, on the thread that also has to draw.
			int[] counts = MapWindow.this.map.icons().counts(this.rows.length);
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
				int n = counts[function];
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
