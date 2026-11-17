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
	private JComboBox<String> sizeBox;
	private JComboBox<String> gameBox;
	private ToggleSwitch smooth;

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
		String[] options = new String[Layout.SCALES.length];
		for (int i = 0; i < Layout.SCALES.length; i++) {
			options[i] = percent(Layout.SCALES[i]);
		}
		this.sizeBox = new JComboBox<String>(options);
		this.sizeBox.setFont(Theme.FONT);
		this.sizeBox.setBackground(Theme.DARKER);
		this.sizeBox.setForeground(Theme.TEXT);
		this.sizeBox.setBorder(BorderFactory.createEmptyBorder());
		this.sizeBox.setPreferredSize(new Dimension(80, 22));
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
		this.gameBox = new JComboBox<String>(gameOptions);
		this.gameBox.setFont(Theme.FONT);
		this.gameBox.setBackground(Theme.DARKER);
		this.gameBox.setForeground(Theme.TEXT);
		this.gameBox.setBorder(BorderFactory.createEmptyBorder());
		this.gameBox.setPreferredSize(new Dimension(80, 22));
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
				DisplaySettings.setSmoothScale(source.isOn());
				DisplayPanel.this.refreshLater();
			}
		});
		this.rows.add(this.row("Smooth game scale", "Soft rather than blocky where the game is "
			+ "stretched. Off is sharper edges and uneven text; at 200% the two are the same "
			+ "picture. HUD size is always drawn with whole pixels.", this.smooth));

		JLabel fixedNote = new JLabel("<html>Both are for the resizable layouts. The fixed screen is"
			+ " one piece of 377's art and is left as it is.</html>");
		fixedNote.setFont(Theme.FONT_SMALL);
		fixedNote.setForeground(Theme.TEXT_DIM);
		fixedNote.setBorder(BorderFactory.createEmptyBorder(6, 10, 10, 10));
		JPanel fixedRow = new JPanel(new BorderLayout());
		fixedRow.setBackground(Theme.BACKGROUND);
		fixedRow.add(fixedNote, BorderLayout.CENTER);
		fixedRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));
		this.rows.add(fixedRow);
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
			this.note.setText("<html>The window is on the fixed screen, so this changes nothing yet."
				+ " F9 switches to a resizable layout.</html>");
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
		this.gameNote.setText("<html>The game is drawn at " + (10000 / game) + "% of the window and"
			+ " enlarged, so it is softer than it was - and there is no size it cannot reach, which"
			+ " HUD size has.</html>");
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
