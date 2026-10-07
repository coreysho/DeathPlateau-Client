package jagex2.client.plugin.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import jagex2.client.plugin.PluginConfig;
import jagex2.client.plugin.PluginManager;

/**
 * One plugin's settings: a row per {@link jagex2.client.plugin.ConfigItem}, with a back arrow to
 * the list. The same shape as RuneLite's config page, and the reason the sidebar is worth having
 * over the in-game panel - a number or a name can be typed here, which a panel drawn with the
 * client's own bitmap fonts cannot offer.
 *
 * EVERY EDIT GOES THROUGH THE GAME THREAD. The field being written may be read by a running
 * plugin on the next frame, so the write is queued rather than done on the event dispatch thread.
 * A value that will not parse is rejected and the box turns red; nothing is stored.
 */
final class ConfigPanel extends JPanel {

	private final PluginManager manager;
	private final Sidebar sidebar;

	private PluginManager.Entry entry;
	private final JLabel title = new JLabel();
	private final JPanel items = new JPanel();

	ConfigPanel(PluginManager manager, Sidebar sidebar) {
		this.manager = manager;
		this.sidebar = sidebar;

		this.setLayout(new BorderLayout());
		this.setBackground(Theme.BACKGROUND);

		JPanel header = new JPanel(new BorderLayout(6, 0));
		header.setBackground(Theme.DARKER);
		header.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

		JButton back = Sidebar.iconButton(Icons.back(14, Theme.TEXT_DIM), Icons.back(14, Theme.ACCENT),
			"Back to the plugin list");
		back.addActionListener(new ActionListener() {

			public void actionPerformed(ActionEvent event) {
				ConfigPanel.this.sidebar.showList();
			}
		});

		this.title.setFont(Theme.FONT_BOLD);
		this.title.setForeground(Theme.TEXT);

		header.add(back, BorderLayout.WEST);
		header.add(this.title, BorderLayout.CENTER);
		this.add(header, BorderLayout.NORTH);

		this.items.setLayout(new BoxLayout(this.items, BoxLayout.Y_AXIS));
		this.items.setBackground(Theme.BACKGROUND);

		Sidebar.ScrollingColumn holder = new Sidebar.ScrollingColumn();
		holder.add(this.items, BorderLayout.NORTH);
		this.add(Sidebar.scroll(holder), BorderLayout.CENTER);
	}

	PluginManager.Entry getEntry() {
		return this.entry;
	}

	void show(PluginManager.Entry entry) {
		this.entry = entry;
		this.rebuild();
	}

	/** Rebuilds from the plugin's current values. Must be called on the event dispatch thread. */
	void rebuild() {
		this.items.removeAll();
		if (this.entry == null) {
			return;
		}
		this.title.setText(this.entry.name);

		if (this.entry.description.length() > 0) {
			this.items.add(Sidebar.leftStrip(
				Sidebar.wrappedLabel(this.entry.description, Theme.TEXT_DIM, Theme.FONT_SMALL, 195), 8, 10, 4, 10));
		}

		// A plugin's settings are its own business while it is off - the fields are still there
		// and still saved - but it is worth saying so, because nothing visibly happens.
		if (!this.entry.isEnabled()) {
			this.items.add(Sidebar.leftStrip(
				Sidebar.label("This plugin is turned off.", Theme.ACCENT, Theme.FONT_SMALL), 2, 10, 6, 10));
		}

		List<PluginConfig.Item> list = this.entry.getConfig().getItems();
		for (int i = 0; i < list.size(); i++) {
			this.items.add(this.buildItem(list.get(i)));
		}

		this.items.revalidate();
		this.items.repaint();
	}

	private Component buildItem(final PluginConfig.Item item) {
		JPanel row = new JPanel(new BorderLayout(6, 2));
		row.setBackground(Theme.ROW);
		// EVERY child of a vertical BoxLayout must share one alignmentX. Mixing the default 0.5
		// of a plain JPanel with the 0.0 of a left-aligned label does not produce a mix of
		// alignments - it makes BoxLayout line the two anchors up, which shunts the 0.0 children
		// right and off the edge of the panel.
		row.setAlignmentX(LEFT_ALIGNMENT);
		row.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.SEPARATOR),
			BorderFactory.createEmptyBorder(6, 8, 6, 6)));
		// How much room the words get, once the editor on the right has taken its share. A
		// description that does not fit wraps rather than running off the side of the panel.
		int textWidth = item.isBoolean() ? 175 : item.isInt() ? 150 : 115;

		JPanel text = new JPanel();
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		text.setOpaque(false);
		text.add(Sidebar.wrappedLabel(item.name, Theme.TEXT, Theme.FONT, textWidth));
		if (item.description.length() > 0) {
			text.add(Sidebar.wrappedLabel(item.description, Theme.TEXT_DIM, Theme.FONT_SMALL, textWidth));
		}
		row.add(text, BorderLayout.CENTER);
		row.add(this.buildEditor(item), BorderLayout.EAST);
		// Rows are laid out by a BoxLayout, which stretches anything that lets it: without this a
		// short list of settings would be spread down the whole panel.
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		return row;
	}

	private Component buildEditor(final PluginConfig.Item item) {
		if (item.isBoolean()) {
			final ToggleSwitch toggle = new ToggleSwitch();
			toggle.setOn(item.booleanValue());
			toggle.setListener(new ToggleSwitch.Listener() {

				public void onToggled(ToggleSwitch source) {
					ConfigPanel.this.write(item, item.booleanValue() ? "0" : "1", null);
				}
			});
			JPanel wrapper = new JPanel(new BorderLayout());
			wrapper.setOpaque(false);
			wrapper.add(toggle, BorderLayout.CENTER);
			return wrapper;
		}

		final JTextField field = new JTextField(item.isInt() ? String.valueOf(item.intValue()) : item.stringValue());
		field.setFont(Theme.FONT);
		field.setBackground(Theme.DARKER);
		field.setForeground(Theme.TEXT);
		field.setCaretColor(Theme.TEXT);
		field.setBorder(BorderFactory.createEmptyBorder(3, 5, 3, 5));
		field.setPreferredSize(new Dimension(item.isInt() ? 56 : 90, 22));

		ActionListener commit = new ActionListener() {

			public void actionPerformed(ActionEvent event) {
				ConfigPanel.this.write(item, field.getText(), field);
			}
		};
		field.addActionListener(commit);
		// Committed on Enter and on clicking away, because nobody presses Enter in a settings box.
		field.addFocusListener(new FocusAdapter() {

			public void focusLost(FocusEvent event) {
				ConfigPanel.this.write(item, field.getText(), field);
			}
		});
		return field;
	}

	/**
	 * Queues the write and reports the outcome on the field it came from. The manager's change
	 * listener refreshes the panel afterwards, so a rejected value is replaced by the real one.
	 */
	private void write(final PluginConfig.Item item, final String text, final JTextField field) {
		final PluginManager.Entry target = this.entry;
		this.manager.invokeOnClientThread(new Runnable() {

			public void run() {
				final boolean ok = target.getConfig().set(item, text);
				javax.swing.SwingUtilities.invokeLater(new Runnable() {

					public void run() {
						if (field != null) {
							field.setBackground(ok ? Theme.DARKER : new java.awt.Color(0x5A, 0x24, 0x24));
						}
						if (ok) {
							ConfigPanel.this.rebuild();
						}
					}
				});
			}
		});
	}
}
