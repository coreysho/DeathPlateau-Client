package jagex2.client.plugin.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;

import jagex2.client.QolSettings;
import jagex2.client.plugin.PluginManager;
import lostcity.Theme;

/**
 * DP settings: every switch the in-game F9 panel has, on the rail where the rest of the settings
 * are.
 *
 * NOT A REPLACEMENT FOR THAT PANEL, A SECOND VIEW OF IT. F9 is the only settings screen the applet
 * has - there is no window there to bolt a sidebar to, which is the whole reason it exists - so
 * both read and write the same QolSettings and either can be used. What this does is mean that
 * nobody on the desktop needs to know the keypress: between this page and Display, the sidebar now
 * covers every row F9 lists.
 *
 * ONE MORE THAN F9 LISTS. QolSettings.PANEL_COUNT stops before the login-screen music, because the
 * in-game panel cannot be opened on the login screen and a switch you can only reach after the
 * thing it governs has finished playing is no switch at all. That reasoning is about F9's
 * position, not about the setting, so this page carries all of them - the title screen keeps its
 * own one-click toggle either way.
 *
 * THE ORDER IS F9'S. Grouping them by what they do was tempting and would have moved every row
 * somebody already knows the position of, for the benefit of a list of fifteen that fits on one
 * screen anyway.
 */
final class QolPanel extends JPanel {

	private final JPanel rows = new JPanel();
	private final ToggleSwitch[] switches = new ToggleSwitch[QolSettings.COUNT];

	QolPanel(PluginManager manager) {
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
	}

	private JPanel buildHeader() {
		JPanel header = new JPanel(new BorderLayout());
		header.setBackground(Theme.DARKER);
		header.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
		JLabel title = new JLabel("DP settings");
		title.setFont(Theme.FONT_TITLE);
		title.setForeground(Theme.TEXT);
		header.add(title, BorderLayout.WEST);
		return header;
	}

	private void build() {
		for (int i = 0; i < QolSettings.COUNT; i++) {
			final int setting = i;
			ToggleSwitch toggle = new ToggleSwitch();
			toggle.setListener(new ToggleSwitch.Listener() {

				public void onToggled(ToggleSwitch source) {
					// The switch does not flip itself - onToggled is "the player clicked" - so the
					// store is told to flip and the row is read back from it.
					QolSettings.toggle(setting);
					QolPanel.this.refresh();
				}
			});
			this.switches[i] = toggle;
			this.rows.add(this.row(QolSettings.label(i),
				setting == QolSettings.LOGIN_MUSIC ? "Also on the login screen, by the note in its corner." : null,
				toggle));
		}

		JLabel note = new JLabel("<html>The same switches as the F9 panel in game, which still works."
			+ " Saved on this computer.</html>");
		note.setFont(Theme.FONT_SMALL);
		note.setForeground(Theme.TEXT_DIM);
		note.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		JPanel noteRow = new JPanel(new BorderLayout());
		noteRow.setBackground(Theme.BACKGROUND);
		noteRow.add(note, BorderLayout.CENTER);
		noteRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
		this.rows.add(noteRow);
	}

	/** One switch: its name, an optional dimmer line under it, and the control on the right. */
	private JPanel row(String name, String about, java.awt.Component editor) {
		JPanel text = new JPanel();
		text.setOpaque(false);
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));

		JLabel title = new JLabel("<html>" + name + "</html>");
		title.setFont(Theme.FONT);
		title.setForeground(Theme.TEXT);
		title.setAlignmentX(LEFT_ALIGNMENT);
		text.add(title);

		if (about != null) {
			JLabel sub = new JLabel("<html>" + about + "</html>");
			sub.setFont(Theme.FONT_SMALL);
			sub.setForeground(Theme.TEXT_DIM);
			sub.setAlignmentX(LEFT_ALIGNMENT);
			text.add(sub);
		}

		JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.setOpaque(false);
		wrapper.add(editor, BorderLayout.CENTER);

		JPanel row = new JPanel(new BorderLayout(8, 0));
		row.setBackground(Theme.BACKGROUND);
		row.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
		row.add(text, BorderLayout.CENTER);
		row.add(wrapper, BorderLayout.EAST);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, about == null ? 40 : 56));
		return row;
	}

	/** Reads every switch back from the store, which is the only thing that knows. */
	void refresh() {
		for (int i = 0; i < this.switches.length; i++) {
			this.switches[i].setOn(QolSettings.on(i));
		}
	}
}
