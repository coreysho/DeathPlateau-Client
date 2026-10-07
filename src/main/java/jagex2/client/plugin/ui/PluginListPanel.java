package jagex2.client.plugin.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import jagex2.client.plugin.PluginManager;

/**
 * The list of installed plugins: a search box, then a row per plugin with a cog and an on/off
 * switch. The sidebar's main page, and the one that matches what RuneLite puts behind its wrench.
 *
 * The rows are rebuilt wholesale on every refresh rather than being kept and updated in place.
 * With a few dozen plugins that is free, and it means there is exactly one path that turns the
 * manager's state into what is on screen - a list that can only ever be stale by one frame.
 */
final class PluginListPanel extends JPanel {

	private final PluginManager manager;
	private final Sidebar sidebar;

	private final JTextField search = new JTextField();
	private final JPanel rows = new JPanel();

	PluginListPanel(PluginManager manager, Sidebar sidebar) {
		this.manager = manager;
		this.sidebar = sidebar;

		this.setLayout(new BorderLayout());
		this.setBackground(Theme.BACKGROUND);

		this.add(this.buildHeader(), BorderLayout.NORTH);

		this.rows.setLayout(new BoxLayout(this.rows, BoxLayout.Y_AXIS));
		this.rows.setBackground(Theme.BACKGROUND);

		// The rows go in a panel of their own inside the scroll area so that a short list sits at
		// the top rather than being stretched down the panel by the layout.
		Sidebar.ScrollingColumn holder = new Sidebar.ScrollingColumn();
		holder.add(this.rows, BorderLayout.NORTH);
		this.add(Sidebar.scroll(holder), BorderLayout.CENTER);
	}

	private JPanel buildHeader() {
		JPanel header = new JPanel(new BorderLayout(4, 0));
		header.setBackground(Theme.DARKER);
		header.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

		JLabel magnifier = new JLabel(Icons.search(13, Theme.TEXT_DIM));
		magnifier.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 2));

		this.search.setBackground(Theme.ROW);
		this.search.setForeground(Theme.TEXT);
		this.search.setCaretColor(Theme.TEXT);
		this.search.setFont(Theme.FONT);
		this.search.setBorder(BorderFactory.createEmptyBorder(4, 2, 4, 4));
		this.search.getDocument().addDocumentListener(new DocumentListener() {

			public void insertUpdate(DocumentEvent event) {
				PluginListPanel.this.rebuild();
			}

			public void removeUpdate(DocumentEvent event) {
				PluginListPanel.this.rebuild();
			}

			public void changedUpdate(DocumentEvent event) {
				PluginListPanel.this.rebuild();
			}
		});

		JPanel box = new JPanel(new BorderLayout());
		box.setBackground(Theme.ROW);
		box.add(magnifier, BorderLayout.WEST);
		box.add(this.search, BorderLayout.CENTER);

		JButton reload = Sidebar.iconButton(Icons.refresh(14, Theme.TEXT_DIM), Icons.refresh(14, Theme.ACCENT),
			"Re-read the plugins folder");
		reload.addActionListener(new java.awt.event.ActionListener() {

			public void actionPerformed(java.awt.event.ActionEvent event) {
				PluginListPanel.this.manager.invokeOnClientThread(new Runnable() {

					public void run() {
						PluginListPanel.this.manager.reload();
					}
				});
			}
		});

		// Alt-drag has no undo of its own - an overlay dragged somewhere silly has to be dragged
		// back, and one dragged off the edge of a viewport that has since shrunk cannot be. This
		// is that undo, and it lives beside reload because both are "put it back how it was".
		JButton reset = Sidebar.iconButton(Icons.move(14, Theme.TEXT_DIM),
			Icons.move(14, Theme.ACCENT),
			"Put every overlay back where its plugin draws it (Alt-drag moves them)");
		reset.addActionListener(new java.awt.event.ActionListener() {

			public void actionPerformed(java.awt.event.ActionEvent event) {
				PluginListPanel.this.manager.invokeOnClientThread(new Runnable() {

					public void run() {
						PluginListPanel.this.manager.resetOverlayPositions();
					}
				});
			}
		});

		JPanel buttons = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 2, 0));
		buttons.setOpaque(false);
		buttons.add(reset);
		buttons.add(reload);

		header.add(box, BorderLayout.CENTER);
		header.add(buttons, BorderLayout.EAST);
		return header;
	}

	/** Rebuilds the rows from the manager. Must be called on the event dispatch thread. */
	void rebuild() {
		this.rows.removeAll();

		String filter = this.search.getText().trim().toLowerCase();
		List<PluginManager.Entry> plugins = this.manager.getPlugins();
		int shown = 0;
		for (int i = 0; i < plugins.size(); i++) {
			PluginManager.Entry entry = plugins.get(i);
			if (filter.length() > 0 && !matches(entry, filter)) {
				continue;
			}
			this.rows.add(this.buildRow(entry));
			shown++;
		}

		// After the working ones, because that is their standing: present, not usable. A refused
		// plugin shown nowhere is the failure this list is here to prevent - the player installed
		// something and "it is not in the list" is not an answer.
		List<PluginManager.Refused> refused = this.manager.getRefused();
		for (int i = 0; i < refused.size(); i++) {
			PluginManager.Refused one = refused.get(i);
			if (filter.length() > 0 && !matches(one, filter)) {
				continue;
			}
			this.rows.add(this.buildRefusedRow(one));
			shown++;
		}

		if (shown == 0) {
			this.rows.add(this.buildEmptyMessage(filter.length() > 0));
		}

		this.rows.revalidate();
		this.rows.repaint();
	}

	private static boolean matches(PluginManager.Entry entry, String filter) {
		return entry.name.toLowerCase().contains(filter)
			|| entry.description.toLowerCase().contains(filter);
	}

	private static boolean matches(PluginManager.Refused refused, String filter) {
		return refused.name.toLowerCase().contains(filter)
			|| refused.reason.toLowerCase().contains(filter);
	}

	/**
	 * A plugin that would not load: its name struck through, and the reason underneath.
	 *
	 * No toggle and no cog, because there is nothing to turn on - the plugin was never built. The
	 * row exists to say what happened and, where the reason is a client too old for it, what would
	 * fix it.
	 */
	private Component buildRefusedRow(PluginManager.Refused refused) {
		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setBackground(Theme.ROW);
		row.setAlignmentX(LEFT_ALIGNMENT);
		row.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.SEPARATOR),
			BorderFactory.createEmptyBorder(5, 8, 5, 6)));

		JPanel text = new JPanel();
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		text.setOpaque(false);
		JLabel name = new JLabel(refused.name);
		name.setFont(Theme.FONT);
		name.setForeground(Theme.TEXT_DIM);
		name.setToolTipText(refused.source);
		name.setAlignmentX(LEFT_ALIGNMENT);
		text.add(name);
		text.add(Sidebar.wrappedLabel(refused.reason, Theme.ACCENT, Theme.FONT_SMALL, 200));

		row.add(text, BorderLayout.CENTER);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		return row;
	}

	private Component buildEmptyMessage(boolean filtering) {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(Theme.BACKGROUND);
		panel.setBorder(BorderFactory.createEmptyBorder(16, 10, 10, 10));
		panel.setAlignmentX(LEFT_ALIGNMENT);

		if (filtering) {
			panel.add(Sidebar.leftStrip(Sidebar.label("No plugin matches that.", Theme.TEXT_DIM, Theme.FONT), 0, 0, 0, 0));
			return panel;
		}
		panel.add(Sidebar.leftStrip(Sidebar.label("No plugins installed.", Theme.TEXT, Theme.FONT_BOLD), 0, 0, 0, 0));
		panel.add(strut(6));
		panel.add(Sidebar.leftStrip(Sidebar.label("Put plugin jars in:", Theme.TEXT_DIM, Theme.FONT_SMALL), 0, 0, 0, 0));
		panel.add(Sidebar.leftStrip(
			Sidebar.wrappedLabel(this.manager.getPluginDirectory().getPath(), Theme.ACCENT, Theme.FONT_SMALL, 200), 2, 0, 2, 0));
		panel.add(strut(8));
		panel.add(Sidebar.leftStrip(Sidebar.label("then press the reload button.", Theme.TEXT_DIM, Theme.FONT_SMALL), 0, 0, 0, 0));
		return panel;
	}

	/** A gap that does not upset the column's alignment the way a bare Box strut does. */
	private static Component strut(int height) {
		Component strut = Box.createVerticalStrut(height);
		((javax.swing.JComponent) strut).setAlignmentX(LEFT_ALIGNMENT);
		return strut;
	}

	private Component buildRow(final PluginManager.Entry entry) {
		final JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setBackground(Theme.ROW);
		// See the note in ConfigPanel.buildItem: one alignmentX for every child of the column.
		row.setAlignmentX(LEFT_ALIGNMENT);
		row.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.SEPARATOR),
			BorderFactory.createEmptyBorder(5, 8, 5, 6)));
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));

		JLabel name = new JLabel(entry.name);
		name.setFont(Theme.FONT);
		name.setForeground(entry.isEnabled() ? Theme.TEXT : Theme.TEXT_DIM);
		name.setToolTipText(entry.description.length() > 0
			? entry.description + "  (" + entry.source + ")"
			: entry.source);

		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
		buttons.setOpaque(false);

		// The cog only appears for a plugin that has something to configure - settings, a list, or
		// both - which is also how a player can tell at a glance which ones do.
		if (entry.hasSettings()) {
			JButton cog = Sidebar.iconButton(Icons.gear(14, Theme.TEXT_DIM), Icons.gear(14, Theme.ACCENT),
				"Settings for " + entry.name);
			cog.addActionListener(new java.awt.event.ActionListener() {

				public void actionPerformed(java.awt.event.ActionEvent event) {
					PluginListPanel.this.sidebar.showConfig(entry);
				}
			});
			buttons.add(cog);
		}

		final ToggleSwitch toggle = new ToggleSwitch();
		toggle.setOn(entry.isEnabled());
		toggle.setToolTipText(entry.isEnabled() ? "Turn off" : "Turn on");
		toggle.setListener(new ToggleSwitch.Listener() {

			public void onToggled(ToggleSwitch source) {
				// Queued for the game thread, and the switch is NOT flipped here: the refresh that
				// follows the toggle sets it from what actually happened. A plugin that throws on
				// startUp leaves the switch where it was, which is the truth.
				PluginListPanel.this.manager.invokeOnClientThread(new Runnable() {

					public void run() {
						PluginListPanel.this.manager.toggle(entry);
					}
				});
			}
		});
		buttons.add(toggle);

		row.add(name, BorderLayout.CENTER);
		row.add(buttons, BorderLayout.EAST);

		final Color base = Theme.ROW;
		row.addMouseListener(new MouseAdapter() {

			public void mouseEntered(MouseEvent event) {
				row.setBackground(Theme.ROW_HOVER);
			}

			public void mouseExited(MouseEvent event) {
				row.setBackground(base);
			}
		});
		return row;
	}
}
