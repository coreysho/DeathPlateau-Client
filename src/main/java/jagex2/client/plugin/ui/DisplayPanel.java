package jagex2.client.plugin.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

import jagex2.client.DisplaySettings;
import jagex2.client.Layout;
import jagex2.client.plugin.PluginManager;
import lostcity.Theme;

/**
 * The Display page: how big the game's own panels are drawn.
 *
 * WHY THIS PAGE EXISTS. The chatbox, the minimap and the side tabs are lumps of 765x503 art at a
 * fixed pixel size, so in resizable mode they stay the size they were on a 2006 monitor while the
 * viewport grows to fill a 1080p window. The result is a game with postage stamps in three of its
 * corners. The HUD size here draws them bigger - the same panels, the same coordinates underneath,
 * copied to the window through a nearest-neighbour blit (Layout, Client.scaleRect).
 *
 * WHY A SETTING AND NOT A RULE. The right size is a matter of eyesight and of how far away the
 * screen is, and the old size is right for somebody who grew up with it. 100% is what the client
 * has always done and is still the default.
 *
 * WHAT IT SAYS WHEN IT CANNOT. A scale needs room: at 200% the right-hand column is 482 wide and
 * 982 tall, which does not fit a 1366x768 laptop. Layout.fit drops to the largest step that does,
 * and the line under the control says so rather than letting a player click 200% and wonder why
 * nothing moved. The setting keeps what was asked for either way, so the same account carries 200%
 * between a desktop that can show it and a laptop that cannot.
 *
 * THREADING. Swing's thread throughout. The write goes straight into DisplaySettings, which is
 * synchronized and is read by the game thread in Client.syncLayout on the next frame - the same
 * path a window resize takes, so there is nothing to invalidate or redraw by hand here.
 */
final class DisplayPanel extends JPanel {

	private final PluginManager manager;
	private final JPanel rows = new JPanel();
	private final JLabel note = new JLabel();
	private final JLabel gameNote = new JLabel();
	private JComboBox<String> windowBox;
	private JComboBox<String> distanceBox;
	private JComboBox<String> sizeBox;
	private JComboBox<String> gameBox;
	private ToggleSwitch smooth;
	private ToggleSwitch stretched;
	private ToggleSwitch aspect;
	private ToggleSwitch integer;
	private final JLabel stretchNote = new JLabel();

	/** Set while rebuild() is filling the control, so its own setSelectedItem writes nothing. */
	private boolean filling;

	DisplayPanel(PluginManager manager) {
		this.manager = manager;

		this.setLayout(new BorderLayout());
		this.setBackground(Theme.BACKGROUND);
		this.add(this.buildHeader(), BorderLayout.NORTH);

		this.rows.setLayout(new BoxLayout(this.rows, BoxLayout.Y_AXIS));
		this.rows.setBackground(Theme.BACKGROUND);

		Sidebar.ScrollingColumn holder = new Sidebar.ScrollingColumn();
		holder.add(this.rows, BorderLayout.NORTH);
		this.add(Sidebar.scroll(holder), BorderLayout.CENTER);

		this.build();
		this.refresh();
		this.refreshGame();
		this.refreshStretch();
	}

	private JPanel buildHeader() {
		JPanel header = new JPanel(new BorderLayout());
		header.setBackground(Theme.DARKER);
		header.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
		JLabel title = new JLabel("Display");
		title.setFont(Theme.FONT_TITLE);
		title.setForeground(Theme.TEXT);
		header.add(title, BorderLayout.WEST);
		return header;
	}

	/** Built once: nothing on this page appears or disappears, only its values change. */
	private void build() {
		// ---- WINDOW, first, because what the rest of this page can do depends on it.
		String[] modes = new String[Layout.MODES];
		for (int i = 0; i < Layout.MODES; i++) {
			modes[i] = Layout.modeName(i);
		}
		this.windowBox = this.combo(modes, 150);
		this.windowBox.addActionListener(new ActionListener() {

			public void actionPerformed(ActionEvent event) {
				if (DisplayPanel.this.filling) {
					return;
				}
				DisplayPanel.this.manager.setDisplayMode(Math.max(0, DisplayPanel.this.windowBox.getSelectedIndex()));
				DisplayPanel.this.refreshLater();
			}
		});
		this.rows.add(this.row("Window", "Fixed is the 2006 screen. The resizable layouts give the "
			+ "world the whole window.", this.windowBox));

		String[] distances = new String[DisplaySettings.DRAW_DISTANCES.length];
		for (int i = 0; i < DisplaySettings.DRAW_DISTANCES.length; i++) {
			distances[i] = DisplaySettings.DRAW_DISTANCES[i] + " tiles";
		}
		this.distanceBox = this.combo(distances, 90);
		this.distanceBox.addActionListener(new ActionListener() {

			public void actionPerformed(ActionEvent event) {
				if (DisplayPanel.this.filling) {
					return;
				}
				int i = Math.max(0, DisplayPanel.this.distanceBox.getSelectedIndex());
				DisplayPanel.this.manager.setDrawDistance(DisplaySettings.DRAW_DISTANCES[i]);
				DisplayPanel.this.refreshLater();
			}
		});
		this.rows.add(this.row("Draw distance", "How far into the world is drawn. Further costs "
			+ "frames; 25 tiles is what the client has always done.", this.distanceBox));

		String[] options = new String[Layout.SCALES.length];
		for (int i = 0; i < Layout.SCALES.length; i++) {
			options[i] = percent(Layout.SCALES[i]);
		}
		this.sizeBox = this.combo(options, 80);
		this.sizeBox.addActionListener(new ActionListener() {

			public void actionPerformed(ActionEvent event) {
				if (DisplayPanel.this.filling) {
					return;
				}
				int picked = Layout.SCALES[Math.max(0, DisplayPanel.this.sizeBox.getSelectedIndex())];
				DisplaySettings.setPanelScale(picked);
				// The game picks it up on its next frame; this only catches the line up with it,
				// and a tick later than the panel moves is soon enough for a sentence.
				DisplayPanel.this.refreshLater();
			}
		});

		this.rows.add(this.row("HUD size", "The chatbox, minimap and side tabs, drawn bigger. "
			+ "Whole multiples only, so every pixel stays square and the text keeps its shape. "
			+ "The world keeps the whole window and all of its sharpness.", this.sizeBox));

		this.note.setFont(Theme.FONT_SMALL);
		this.note.setForeground(Theme.TEXT_DIM);
		this.note.setBorder(BorderFactory.createEmptyBorder(2, 10, 10, 10));
		JPanel noteRow = new JPanel(new BorderLayout());
		noteRow.setBackground(Theme.BACKGROUND);
		noteRow.add(this.note, BorderLayout.CENTER);
		noteRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
		this.rows.add(noteRow);

		// ---- GAME SCALE, the other way to answer the same question.
		String[] gameOptions = new String[DisplaySettings.GAME_SCALES.length];
		for (int i = 0; i < DisplaySettings.GAME_SCALES.length; i++) {
			gameOptions[i] = percent(DisplaySettings.GAME_SCALES[i]);
		}
		this.gameBox = this.combo(gameOptions, 80);
		this.gameBox.addActionListener(new ActionListener() {

			public void actionPerformed(ActionEvent event) {
				if (DisplayPanel.this.filling) {
					return;
				}
				int picked = DisplaySettings.GAME_SCALES[Math.max(0, DisplayPanel.this.gameBox.getSelectedIndex())];
				DisplaySettings.setGameScale(picked);
				DisplayPanel.this.refreshLater();
			}
		});
		this.rows.add(this.row("Game scale", "Everything at once - the world, the panels and the "
			+ "text. The game is drawn smaller and stretched to the window.", this.gameBox));

		this.gameNote.setFont(Theme.FONT_SMALL);
		this.gameNote.setForeground(Theme.TEXT_DIM);
		this.gameNote.setBorder(BorderFactory.createEmptyBorder(2, 10, 10, 10));
		JPanel gameNoteRow = new JPanel(new BorderLayout());
		gameNoteRow.setBackground(Theme.BACKGROUND);
		gameNoteRow.add(this.gameNote, BorderLayout.CENTER);
		gameNoteRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));
		this.rows.add(gameNoteRow);

		// ---- SMOOTHING, which is what to do with the pixels in between.
		this.smooth = new ToggleSwitch();
		this.smooth.setOn(DisplaySettings.smoothScale());
		this.smooth.setListener(new ToggleSwitch.Listener() {

			public void onToggled(ToggleSwitch source) {
				// THE SWITCH DOES NOT FLIP ITSELF: onToggled is "the player clicked", and the
				// value it still holds is the old one. Reading it here wrote the same setting
				// back and the switch never moved.
				DisplaySettings.setSmoothScale(!DisplaySettings.smoothScale());
				DisplayPanel.this.refreshLater();
			}
		});
		this.rows.add(this.row("Smooth game scale", "Soft rather than blocky where the game is "
			+ "stretched. Off is sharper edges and uneven text; at 200% the two are the same "
			+ "picture. HUD size is always drawn with whole pixels.", this.smooth));

		// WHOLE SIZES GOVERNS BOTH SCALES, so it sits with them rather than under the fixed
		// screen's heading where it started.
		this.integer = new ToggleSwitch();
		this.integer.setListener(new ToggleSwitch.Listener() {

			public void onToggled(ToggleSwitch source) {
				DisplaySettings.setStretchInteger(!DisplaySettings.stretchInteger());
				DisplayPanel.this.refreshLater();
			}
		});
		this.rows.add(this.row("Whole sizes only", "Double, triple - one pixel becomes exactly four "
			+ "or nine, so nothing is uneven. Applies to the game scale and to stretched mode; "
			+ "costs the leftover as a border.", this.integer));

		// MOVING THE HUD, and the way back from it. A thing that can be dragged and not put back
		// is one bad drag from a client somebody cannot use, and the drag itself leaves nothing on
		// screen to explain itself.
		javax.swing.JButton putBack = new javax.swing.JButton("Put the HUD back");
		putBack.setFont(Theme.FONT);
		putBack.setForeground(Theme.TEXT);
		putBack.setBackground(Theme.ROW);
		putBack.setFocusPainted(false);
		putBack.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
		putBack.addActionListener(new ActionListener() {

			public void actionPerformed(ActionEvent event) {
				DisplaySettings.resetHudOffsets();
				DisplayPanel.this.refreshLater();
			}
		});
		this.rows.add(this.row("Move the HUD", "Hold Alt and drag the chatbox, the minimap or the "
			+ "side tabs anywhere in the window.", putBack));

		JLabel fixedNote = new JLabel("<html>Those are for the resizable layouts. Whole sizes also"
			+ " applies to the fixed screen below.</html>");
		fixedNote.setFont(Theme.FONT_SMALL);
		fixedNote.setForeground(Theme.TEXT_DIM);
		fixedNote.setBorder(BorderFactory.createEmptyBorder(6, 10, 10, 10));
		JPanel fixedRow = new JPanel(new BorderLayout());
		fixedRow.setBackground(Theme.BACKGROUND);
		fixedRow.add(fixedNote, BorderLayout.CENTER);
		fixedRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
		this.rows.add(fixedRow);

		// ---- STRETCHED MODE, which is the fixed screen's answer to the same question.
		JLabel heading = new JLabel("On the fixed screen");
		heading.setFont(Theme.FONT_BOLD);
		heading.setForeground(Theme.ACCENT);
		heading.setBorder(BorderFactory.createEmptyBorder(10, 10, 2, 10));
		JPanel headingRow = new JPanel(new BorderLayout());
		headingRow.setBackground(Theme.BACKGROUND);
		headingRow.add(heading, BorderLayout.CENTER);
		headingRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		this.rows.add(headingRow);

		this.stretched = new ToggleSwitch();
		this.stretched.setListener(new ToggleSwitch.Listener() {

			public void onToggled(ToggleSwitch source) {
				DisplaySettings.setStretched(!DisplaySettings.stretched());
				DisplayPanel.this.refreshLater();
			}
		});
		this.rows.add(this.row("Stretched mode", "The fixed screen drawn to fill the window instead "
			+ "of sitting in the middle of it.", this.stretched));

		this.aspect = new ToggleSwitch();
		this.aspect.setListener(new ToggleSwitch.Listener() {

			public void onToggled(ToggleSwitch source) {
				DisplaySettings.setStretchAspect(!DisplaySettings.stretchAspect());
				DisplayPanel.this.refreshLater();
			}
		});
		this.rows.add(this.row("Keep its shape", "On, it grows until one side runs out and the rest "
			+ "is a border. Off, it fills the window and the picture is stretched wider than it "
			+ "was drawn.", this.aspect));

		this.stretchNote.setFont(Theme.FONT_SMALL);
		this.stretchNote.setForeground(Theme.TEXT_DIM);
		this.stretchNote.setBorder(BorderFactory.createEmptyBorder(2, 10, 10, 10));
		JPanel stretchNoteRow = new JPanel(new BorderLayout());
		stretchNoteRow.setBackground(Theme.BACKGROUND);
		stretchNoteRow.add(this.stretchNote, BorderLayout.CENTER);
		stretchNoteRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 90));
		this.rows.add(stretchNoteRow);
	}

	/** The page's one kind of dropdown, so five of them cannot drift apart. */
	private JComboBox<String> combo(String[] options, int width) {
		JComboBox<String> box = new JComboBox<String>(options);
		box.setFont(Theme.FONT);
		box.setBackground(Theme.DARKER);
		box.setForeground(Theme.TEXT);
		box.setBorder(BorderFactory.createEmptyBorder());
		box.setPreferredSize(new Dimension(width, 22));
		return box;
	}

	/** One setting: its name, a dimmer line under it, and the control on the right. */
	private JPanel row(String name, String about, java.awt.Component editor) {
		JPanel text = new JPanel();
		text.setOpaque(false);
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));

		JLabel title = new JLabel(name);
		title.setFont(Theme.FONT_BOLD);
		title.setForeground(Theme.TEXT);
		title.setAlignmentX(LEFT_ALIGNMENT);
		text.add(title);

		JLabel sub = new JLabel("<html>" + about + "</html>");
		sub.setFont(Theme.FONT_SMALL);
		sub.setForeground(Theme.TEXT_DIM);
		sub.setAlignmentX(LEFT_ALIGNMENT);
		text.add(sub);

		JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.setOpaque(false);
		wrapper.add(editor, BorderLayout.CENTER);

		JPanel row = new JPanel(new BorderLayout(8, 0));
		row.setBackground(Theme.BACKGROUND);
		row.setBorder(BorderFactory.createEmptyBorder(10, 10, 6, 10));
		row.add(text, BorderLayout.CENTER);
		row.add(wrapper, BorderLayout.EAST);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 64));
		return row;
	}

	/** Catches the control and the line up with what is saved and what is on screen. */
	void refresh() {
		this.refreshGame();
		this.refreshStretch();
		this.filling = true;
		this.windowBox.setSelectedIndex(Math.max(0, Math.min(Layout.MODES - 1, DisplaySettings.mode())));
		int tiles = DisplaySettings.drawDistance();
		for (int i = 0; i < DisplaySettings.DRAW_DISTANCES.length; i++) {
			if (DisplaySettings.DRAW_DISTANCES[i] == tiles) {
				this.distanceBox.setSelectedIndex(i);
			}
		}
		this.filling = false;
		int want = DisplaySettings.panelScale();
		int using = this.manager.hudScaleInUse();
		this.filling = true;
		for (int i = 0; i < Layout.SCALES.length; i++) {
			if (Layout.SCALES[i] == want) {
				this.sizeBox.setSelectedIndex(i);
			}
		}
		this.filling = false;
		if (!DisplaySettings.resizable()) {
			this.note.setText("<html>The window is on the fixed screen, so this changes nothing yet -"
				+ " pick a resizable layout above.</html>");
			return;
		}
		if (using < want) {
			this.note.setText("<html>Showing " + percent(using) + ": the window is too small for "
				+ percent(want) + ". It will be used on a bigger one.</html>");
			return;
		}
		this.note.setText(want == Layout.SCALE_FIXED
			? "<html>The size the client has always drawn them.</html>"
			: "<html>Showing " + percent(using) + ".</html>");
	}

	/** The game-scale line: what it is doing, and the trade it is making. */
	private void refreshGame() {
		int game = DisplaySettings.gameScale();
		this.filling = true;
		for (int i = 0; i < DisplaySettings.GAME_SCALES.length; i++) {
			if (DisplaySettings.GAME_SCALES[i] == game) {
				this.gameBox.setSelectedIndex(i);
			}
		}
		this.smooth.setOn(DisplaySettings.smoothScale());
		this.filling = false;
		if (game == 100) {
			this.gameNote.setText("<html>Off: the game is drawn at the window's own size.</html>");
			return;
		}
		if (DisplaySettings.stretchInteger() && game / 100 < 2) {
			// The honest answer rather than a silent one: a whole factor above 1 is what whole
			// sizes needs, and 125% and 150% have none.
			this.gameNote.setText("<html>Doing nothing: whole sizes only is on, and " + percent(game)
				+ " is not a whole number of times. 200% is.</html>");
			return;
		}
		if (DisplaySettings.stretchInteger()) {
			this.gameNote.setText("<html>The game is drawn at " + (10000 / game) + "% of the window"
				+ " and shown at exactly " + (game / 100) + "x, so every pixel is the same size."
				+ " The few left over are a border.</html>");
			return;
		}
		this.gameNote.setText("<html>The game is drawn at " + (10000 / game) + "% of the window and"
			+ " enlarged, so it is softer than it was - and there is no size it cannot reach, which"
			+ " HUD size has.</html>");
	}

	/** The stretched-mode switches, and a line saying what they add up to. */
	private void refreshStretch() {
		boolean on = DisplaySettings.stretched();
		this.filling = true;
		this.stretched.setOn(on);
		this.aspect.setOn(DisplaySettings.stretchAspect());
		this.integer.setOn(DisplaySettings.stretchInteger());
		this.filling = false;
		// Keep its shape is stretched mode's alone; whole sizes governs the game scale too, so it
		// stays live whether or not stretched mode is on.
		this.aspect.setEnabled(on);
		if (!on) {
			this.stretchNote.setText("<html>Off: the fixed screen is drawn at 765x503, the size its"
				+ " art was made for.</html>");
			return;
		}
		if (DisplaySettings.stretchInteger()) {
			this.stretchNote.setText("<html>Whole sizes wins where both are on: the shape is kept"
				+ " either way, and the leftover is a border.</html>");
			return;
		}
		this.stretchNote.setText(DisplaySettings.stretchAspect()
			? "<html>Filling as much of the window as it can without changing shape.</html>"
			: "<html>Filling the whole window. On a widescreen monitor the picture is noticeably"
				+ " wider than it was drawn.</html>");
	}

	private void refreshLater() {
		javax.swing.SwingUtilities.invokeLater(new Runnable() {

			public void run() {
				DisplayPanel.this.refresh();
			}
		});
	}

	private static String percent(int scale) {
		return scale + "%";
	}
}
