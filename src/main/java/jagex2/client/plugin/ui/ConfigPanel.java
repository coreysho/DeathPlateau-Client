package jagex2.client.plugin.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import jagex2.client.plugin.ConfigList;
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

	/**
	 * The plugin's lists, read on the game thread. Null until the first read lands, which is why
	 * rebuild() draws the settings immediately and the lists a moment later rather than waiting:
	 * the page appears the instant the cog is pressed either way.
	 */
	private List<PluginManager.ListSnapshot> lists;

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
		this.lists = null;
		this.rebuild();
		this.readLists();
	}

	/**
	 * Asks the game thread for the plugin's lists and redraws when they arrive.
	 *
	 * Every list is read over there, in one go, and the panel only ever draws the copy - see
	 * ConfigList for why.
	 */
	private void readLists() {
		final PluginManager.Entry target = this.entry;
		if (target == null) {
			return;
		}
		this.manager.invokeOnClientThread(new Runnable() {

			public void run() {
				final List<PluginManager.ListSnapshot> read = ConfigPanel.this.manager.snapshotConfigLists(target);
				SwingUtilities.invokeLater(new Runnable() {

					public void run() {
						if (ConfigPanel.this.entry == target) {
							ConfigPanel.this.lists = read;
							ConfigPanel.this.rebuild();
						}
					}
				});
			}
		});
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

		if (this.lists != null) {
			for (int i = 0; i < this.lists.size(); i++) {
				this.buildList(this.lists.get(i));
			}
		}

		if (list.isEmpty() && (this.lists == null || this.lists.isEmpty())) {
			this.items.add(Sidebar.leftStrip(
				Sidebar.wrappedLabel("This plugin has no settings.", Theme.TEXT_DIM, Theme.FONT_SMALL, 195),
				8, 10, 8, 10));
		}

		this.items.revalidate();
		this.items.repaint();
	}

	/** A heading, then a row per entry, or the list's own message when it is empty. */
	private void buildList(final PluginManager.ListSnapshot snapshot) {
		JPanel heading = new JPanel(new BorderLayout());
		heading.setBackground(Theme.DARKER);
		heading.setAlignmentX(LEFT_ALIGNMENT);
		heading.setBorder(BorderFactory.createEmptyBorder(5, 8, 4, 8));
		heading.add(Sidebar.label(snapshot.title + "  (" + snapshot.rows.size() + ")",
			Theme.ACCENT, Theme.FONT_BOLD), BorderLayout.WEST);
		heading.setMaximumSize(new Dimension(Integer.MAX_VALUE, heading.getPreferredSize().height));
		this.items.add(heading);

		if (snapshot.rows.isEmpty()) {
			this.items.add(Sidebar.leftStrip(
				Sidebar.wrappedLabel(snapshot.emptyMessage, Theme.TEXT_DIM, Theme.FONT_SMALL, 195),
				8, 10, 8, 10));
			return;
		}
		for (int i = 0; i < snapshot.rows.size(); i++) {
			this.items.add(this.buildListRow(snapshot, snapshot.rows.get(i)));
		}
	}

	private Component buildListRow(final PluginManager.ListSnapshot snapshot, final ConfigList.Row row) {
		JPanel panel = new JPanel(new BorderLayout(6, 2));
		panel.setBackground(Theme.ROW);
		panel.setAlignmentX(LEFT_ALIGNMENT);
		panel.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.SEPARATOR),
			BorderFactory.createEmptyBorder(5, 8, 5, 6)));

		int textWidth = 120;
		JPanel text = new JPanel();
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		text.setOpaque(false);
		text.add(Sidebar.wrappedLabel(row.label, Theme.TEXT, Theme.FONT, textWidth));
		if (row.detail != null && row.detail.length() > 0) {
			text.add(Sidebar.wrappedLabel(row.detail, Theme.TEXT_DIM, Theme.FONT_SMALL, textWidth));
		}

		JPanel buttons = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 4, 0));
		buttons.setOpaque(false);
		if (row.action != null && row.action.length() > 0) {
			// The button is labelled with where the setting IS, not with a verb, because that is
			// what a player needs to read; pressing it moves to the next one.
			JButton action = this.smallButton(row.action, Theme.ACCENT);
			action.setToolTipText("Change this");
			action.addActionListener(new ActionListener() {

				public void actionPerformed(ActionEvent event) {
					ConfigPanel.this.runOnList(snapshot, row.index, true);
				}
			});
			buttons.add(action);
		}
		if (row.removable) {
			JButton remove = this.smallButton("\u00d7", Theme.TEXT_DIM);
			remove.setToolTipText("Remove");
			remove.addActionListener(new ActionListener() {

				public void actionPerformed(ActionEvent event) {
					ConfigPanel.this.runOnList(snapshot, row.index, false);
				}
			});
			buttons.add(remove);
		}

		panel.add(text, BorderLayout.CENTER);
		panel.add(buttons, BorderLayout.EAST);
		panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, panel.getPreferredSize().height));
		return panel;
	}

	private JButton smallButton(String label, java.awt.Color colour) {
		JButton button = new JButton(label);
		button.setFont(Theme.FONT_SMALL);
		button.setForeground(colour);
		button.setBackground(Theme.DARKER);
		button.setFocusPainted(false);
		button.setFocusable(false);
		button.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(Theme.SWITCH_OFF),
			BorderFactory.createEmptyBorder(2, 6, 2, 6)));
		button.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
		return button;
	}

	/**
	 * Cycles or removes a row, on the game thread, then re-reads the list.
	 *
	 * Re-read rather than patched: removing a row renumbers every row after it, and a panel that
	 * guessed at the new numbering would act on the wrong one next time.
	 */
	private void runOnList(final PluginManager.ListSnapshot snapshot, final int index, final boolean action) {
		this.manager.invokeOnClientThread(new Runnable() {

			public void run() {
				try {
					if (action) {
						snapshot.act(index);
					} else {
						snapshot.remove(index);
					}
				} catch (Throwable error) {
					jagex2.client.DevLog.log("PLUGIN", "a config list threw: " + error);
				}
			}
		});
		this.readLists();
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

		if (item.isColour()) {
			return this.buildColourEditor(item);
		}
		String[] choices = item.choices();
		if (choices.length > 0) {
			return this.buildChoiceEditor(item, choices);
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
	 * A swatch that opens a colour picker.
	 *
	 * A BUTTON SHOWING THE COLOUR, not a box showing its hex. Six characters of hex is a thing a
	 * player decodes rather than reads, and the whole point of the tier colours this exists for is
	 * telling them apart at a glance - which you cannot do in a settings panel that shows you
	 * "3C8A2F".
	 *
	 * The hex stays underneath as the stored value and as the tooltip, so a player who knows what
	 * they want can still see it, and the file is the same file it was before this existed.
	 */
	private Component buildColourEditor(final PluginConfig.Item item) {
		final JButton swatch = new JButton();
		final int rgb = item.colourValue();
		swatch.setPreferredSize(new Dimension(44, 22));
		swatch.setBackground(new Color(rgb));
		swatch.setToolTipText("#" + PluginConfig.toHex(rgb));
		swatch.setFocusPainted(false);
		swatch.setOpaque(true);
		// A thin light border, because a dark colour against a dark panel is otherwise an
		// invisible button, and the swatch for black has to still look like something to click.
		swatch.setBorder(BorderFactory.createLineBorder(Theme.SEPARATOR));
		swatch.addActionListener(new ActionListener() {

			public void actionPerformed(ActionEvent event) {
				Color picked = JColorChooser.showDialog(ConfigPanel.this, item.name, new Color(rgb));
				// null is the player pressing Cancel, which must leave the setting alone rather
				// than writing black.
				if (picked != null) {
					ConfigPanel.this.write(item, PluginConfig.toHex(picked.getRGB()), null);
				}
			}
		});
		JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.setOpaque(false);
		wrapper.add(swatch, BorderLayout.CENTER);
		return wrapper;
	}

	/**
	 * A drop-down of the values a setting allows.
	 *
	 * WHAT IS STORED NOW IS ALWAYS OFFERED, even when it is not one of the choices any more. A
	 * release that drops a mode would otherwise silently rewrite the player's file the moment they
	 * opened the panel; this way they see what they have, and change it when they mean to.
	 */
	private Component buildChoiceEditor(final PluginConfig.Item item, String[] choices) {
		final String current = item.stringValue() == null ? "" : item.stringValue();
		List<String> options = new ArrayList<String>();
		for (int i = 0; i < choices.length; i++) {
			options.add(choices[i]);
		}
		if (!options.contains(current)) {
			options.add(0, current);
		}
		final JComboBox<String> box = new JComboBox<String>(options.toArray(new String[0]));
		box.setSelectedItem(current);
		box.setFont(Theme.FONT);
		box.setBackground(Theme.DARKER);
		box.setForeground(Theme.TEXT);
		box.setBorder(BorderFactory.createEmptyBorder());
		box.setPreferredSize(new Dimension(110, 22));
		box.addActionListener(new ActionListener() {

			public void actionPerformed(ActionEvent event) {
				Object picked = box.getSelectedItem();
				// Only on a real change: setSelectedItem above fires this too, and writing on
				// every rebuild would queue a client-thread task per panel refresh.
				if (picked != null && !String.valueOf(picked).equals(current)) {
					ConfigPanel.this.write(item, String.valueOf(picked), null);
				}
			}
		});
		JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.setOpaque(false);
		wrapper.add(box, BorderLayout.CENTER);
		return wrapper;
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
