package jagex2.client.plugin.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import jagex2.client.plugin.PluginApi;
import jagex2.client.plugin.hub.Hub;
import jagex2.client.plugin.hub.HubClient;
import jagex2.client.plugin.hub.HubEntry;
import jagex2.client.plugin.hub.HubIndex;

/**
 * The hub: plugins available to install, from the index.
 *
 * The second tab of the sidebar, and the one that makes the plugin system worth having - a plugin
 * arrives by clicking Install rather than by finding a folder and dropping a jar in it.
 *
 * WHAT IT SAYS OUT LOUD, because this installs code that runs unsandboxed in the client: who
 * wrote each plugin, and a standing line that an installed plugin can do anything the client can.
 * A plugin offered over plain http is marked, because a checksum in an index fetched over the
 * same plain http is not a check on anything.
 *
 * Nothing here blocks. Every action hands off to {@link Hub}, which works on its own thread and
 * calls back - on that thread, so every callback here bounces onto the EDT before touching a
 * component.
 */
final class HubPanel extends JPanel {

	private final Hub hub;

	private final JTextField search = new JTextField();
	private final JPanel rows = new JPanel();
	private final JLabel status = new JLabel();

	private boolean fetched;

	HubPanel(Hub hub) {
		this.hub = hub;

		this.setLayout(new BorderLayout());
		this.setBackground(Theme.BACKGROUND);

		this.add(this.buildHeader(), BorderLayout.NORTH);

		this.rows.setLayout(new BoxLayout(this.rows, BoxLayout.Y_AXIS));
		this.rows.setBackground(Theme.BACKGROUND);

		Sidebar.ScrollingColumn holder = new Sidebar.ScrollingColumn();
		holder.add(this.rows, BorderLayout.NORTH);
		this.add(Sidebar.scroll(holder), BorderLayout.CENTER);

		this.status.setFont(Theme.FONT_SMALL);
		this.status.setForeground(Theme.TEXT_DIM);
		this.status.setBorder(BorderFactory.createEmptyBorder(4, 8, 5, 8));
		this.add(this.status, BorderLayout.SOUTH);
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
				HubPanel.this.rebuild();
			}

			public void removeUpdate(DocumentEvent event) {
				HubPanel.this.rebuild();
			}

			public void changedUpdate(DocumentEvent event) {
				HubPanel.this.rebuild();
			}
		});

		JPanel box = new JPanel(new BorderLayout());
		box.setBackground(Theme.ROW);
		box.add(magnifier, BorderLayout.WEST);
		box.add(this.search, BorderLayout.CENTER);

		JButton refresh = Sidebar.iconButton(Icons.refresh(14, Theme.TEXT_DIM), Icons.refresh(14, Theme.ACCENT),
			"Fetch the plugin list again");
		refresh.addActionListener(new ActionListener() {

			public void actionPerformed(ActionEvent event) {
				HubPanel.this.fetch();
			}
		});

		header.add(box, BorderLayout.CENTER);
		header.add(refresh, BorderLayout.EAST);
		return header;
	}

	/** Called when the tab is opened; fetches once rather than on every visit. */
	void shown() {
		if (!this.fetched) {
			this.fetch();
		}
	}

	private void fetch() {
		if (this.hub.isBusy()) {
			return;
		}
		this.fetched = true;
		this.setStatus("Fetching the plugin list...");
		this.rebuild();
		this.hub.refresh(new Hub.Callback() {

			public void onFinished(final String error) {
				HubPanel.this.later(error == null ? "" : "Could not fetch the list: " + error);
			}
		});
	}

	/** Back on the EDT: show the outcome and redraw from whatever the hub now holds. */
	private void later(final String error) {
		SwingUtilities.invokeLater(new Runnable() {

			public void run() {
				HubPanel.this.setStatus(error);
				HubPanel.this.rebuild();
			}
		});
	}

	private void setStatus(String text) {
		if (text == null || text.length() == 0) {
			HubIndex index = this.hub.getIndex();
			int count = index == null ? 0 : index.getEntries().size();
			this.status.setForeground(Theme.TEXT_DIM);
			this.status.setText(count + (count == 1 ? " plugin available" : " plugins available"));
			this.status.setToolTipText(this.hub.getIndexUrl());
			return;
		}
		this.status.setForeground(text.startsWith("Could not") || text.startsWith("Failed")
			? new java.awt.Color(0xE0, 0x6C, 0x6C) : Theme.TEXT_DIM);
		this.status.setText(text);
		this.status.setToolTipText(text);
	}

	/** Rebuilds the rows. Must be called on the event dispatch thread. */
	void rebuild() {
		this.rows.removeAll();

		HubIndex index = this.hub.getIndex();
		if (index == null) {
			this.rows.add(this.message(this.hub.isBusy()
				? "Fetching..."
				: "No plugin list yet. Press the refresh button."));
		} else {
			String filter = this.search.getText().trim().toLowerCase();
			List<HubEntry> entries = index.getEntries();
			int shown = 0;
			for (int i = 0; i < entries.size(); i++) {
				HubEntry entry = entries.get(i);
				if (filter.length() > 0 && !matches(entry, filter)) {
					continue;
				}
				this.rows.add(this.buildRow(entry));
				shown++;
			}
			if (shown == 0) {
				this.rows.add(this.message(entries.isEmpty()
					? "The list is empty."
					: "Nothing matches that."));
			} else {
				this.rows.add(this.warning());
			}
		}

		this.rows.revalidate();
		this.rows.repaint();
	}

	private static boolean matches(HubEntry entry, String filter) {
		return entry.name.toLowerCase().contains(filter)
			|| entry.description.toLowerCase().contains(filter)
			|| entry.author.toLowerCase().contains(filter);
	}

	private Component message(String text) {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(Theme.BACKGROUND);
		panel.setBorder(BorderFactory.createEmptyBorder(16, 10, 10, 10));
		panel.setAlignmentX(LEFT_ALIGNMENT);
		panel.add(Sidebar.leftStrip(Sidebar.wrappedLabel(text, Theme.TEXT_DIM, Theme.FONT, 200), 0, 0, 0, 0));
		panel.add(Box.createVerticalStrut(8));
		panel.add(Sidebar.leftStrip(
			Sidebar.wrappedLabel(this.hub.getIndexUrl(), Theme.TEXT_DIM, Theme.FONT_SMALL, 200), 0, 0, 0, 0));
		return panel;
	}

	/** The standing warning under the list: what installing one of these actually means. */
	private Component warning() {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(Theme.BACKGROUND);
		panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		panel.setAlignmentX(LEFT_ALIGNMENT);
		panel.add(Sidebar.leftStrip(Sidebar.wrappedLabel(
			"A plugin runs inside your client with the access your client has. "
				+ "Install only what you trust.",
			Theme.TEXT_DIM, Theme.FONT_SMALL, 200), 0, 0, 0, 0));
		return panel;
	}

	private Component buildRow(final HubEntry entry) {
		JPanel row = new JPanel(new BorderLayout(6, 2));
		row.setBackground(Theme.ROW);
		row.setAlignmentX(LEFT_ALIGNMENT);
		row.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.SEPARATOR),
			BorderFactory.createEmptyBorder(6, 8, 6, 6)));

		boolean installed = this.hub.isInstalled(entry);
		boolean update = this.hub.hasUpdate(entry);

		JPanel text = new JPanel();
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		text.setOpaque(false);
		text.add(Sidebar.wrappedLabel(entry.name, Theme.TEXT, Theme.FONT, 150));

		StringBuilder by = new StringBuilder();
		if (entry.author.length() > 0) {
			by.append("by ").append(entry.author);
		}
		if (entry.version.length() > 0) {
			by.append(by.length() > 0 ? "  " : "").append(entry.version);
		}
		if (by.length() > 0) {
			text.add(Sidebar.wrappedLabel(by.toString(), Theme.TEXT_DIM, Theme.FONT_SMALL, 150));
		}
		if (entry.description.length() > 0) {
			text.add(Sidebar.wrappedLabel(entry.description, Theme.TEXT_DIM, Theme.FONT_SMALL, 150));
		}
		// Said per plugin rather than once at the top: it is a property of where THIS jar comes
		// from, and a list can mix the two.
		if (entry.isInsecure()) {
			text.add(Sidebar.wrappedLabel("served over plain http", Theme.ACCENT, Theme.FONT_SMALL, 150));
		}
		// The entry is still listed, so a player can see the plugin exists and why they cannot
		// have it yet. Downloading it would work perfectly and then fail on the first frame.
		boolean tooNew = entry.needsNewerClient();
		if (tooNew) {
			text.add(Sidebar.wrappedLabel("needs a newer client (plugin API " + entry.clientApi
				+ "; this client has " + PluginApi.LEVEL + ")", Theme.ACCENT, Theme.FONT_SMALL, 150));
		}

		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
		buttons.setOpaque(false);
		// Nothing that would put this jar on disk, which covers the update case too: a plugin
		// that works today must not be replaced by a build that cannot run here.
		if (tooNew) {
			// Nothing to offer but removal, below.
		} else if (update) {
			buttons.add(this.actionButton("Update", entry, true));
		} else if (!installed) {
			buttons.add(this.actionButton("Install", entry, true));
		}
		if (installed) {
			buttons.add(this.actionButton("Remove", entry, false));
		}

		row.add(text, BorderLayout.CENTER);
		row.add(buttons, BorderLayout.EAST);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		return row;
	}

	private JButton actionButton(final String label, final HubEntry entry, final boolean install) {
		JButton button = new JButton(label);
		button.setFont(Theme.FONT_SMALL);
		button.setForeground(install ? Theme.ACCENT : Theme.TEXT_DIM);
		button.setBackground(Theme.DARKER);
		button.setFocusPainted(false);
		button.setFocusable(false);
		button.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(install ? Theme.ACCENT : Theme.SWITCH_OFF),
			BorderFactory.createEmptyBorder(2, 6, 2, 6)));
		button.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
		// Everything goes quiet while one job runs: the hub refuses a second anyway, and a button
		// that looks clickable but does nothing is worse than one that is plainly disabled.
		button.setEnabled(!this.hub.isBusy());
		button.addActionListener(new ActionListener() {

			public void actionPerformed(ActionEvent event) {
				HubPanel.this.act(entry, install);
			}
		});
		return button;
	}

	private void act(final HubEntry entry, boolean install) {
		if (this.hub.isBusy()) {
			return;
		}
		if (install) {
			this.setStatus("Downloading " + entry.name + "...");
			this.rebuild();
			this.hub.install(entry, new HubClient.Progress() {

				public void onProgress(final long done, final long total) {
					// Off the EDT, and once per 16KB block: only the text is touched, and only
					// through invokeLater, so this never fights the painting thread.
					final String text = total > 0
						? "Downloading " + entry.name + "... " + (done * 100 / total) + "%"
						: "Downloading " + entry.name + "... " + (done / 1024) + "KB";
					SwingUtilities.invokeLater(new Runnable() {

						public void run() {
							HubPanel.this.status.setText(text);
						}
					});
				}
			}, new Hub.Callback() {

				public void onFinished(String error) {
					HubPanel.this.later(error == null ? "Installed " + entry.name + "." : "Failed: " + error);
				}
			});
		} else {
			this.setStatus("Removing " + entry.name + "...");
			this.rebuild();
			this.hub.remove(entry, new Hub.Callback() {

				public void onFinished(String error) {
					HubPanel.this.later(error == null ? "Removed " + entry.name + "." : "Failed: " + error);
				}
			});
		}
	}
}
