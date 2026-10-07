package jagex2.client.plugin.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;

import jagex2.client.plugin.ConfigList;
import jagex2.client.plugin.PluginManager;

/**
 * A plugin's own page, reached from its icon on the rail.
 *
 * WHAT A PLUGIN GETS TO SAY is a list of rows - a label, a dimmer line under it, a reading on the
 * right, a bar, a button - and nothing else. It hands over data, not components. That is the
 * whole bargain: a plugin from the hub can show a readout without being handed the event thread
 * and a blank rectangle to do as it likes with, and every page in the sidebar looks like it came
 * from the same client because this is the only thing that draws one.
 *
 * The cost is that a plugin cannot draw something this has no word for. When one genuinely needs
 * to, the answer is a new kind of row here rather than a JComponent from over there.
 */
public final class PanelPage extends JPanel {

	private final PluginManager manager;
	private final JPanel rows = new JPanel();

	/** What is being shown, kept so the page can be rebuilt when the plugin's numbers move. */
	private PluginManager.PanelSnapshot snapshot;

	PanelPage(PluginManager manager) {
		this.manager = manager;
		this.setLayout(new BorderLayout());
		this.setBackground(Theme.BACKGROUND);

		this.rows.setLayout(new BoxLayout(this.rows, BoxLayout.Y_AXIS));
		this.rows.setBackground(Theme.BACKGROUND);
		this.add(Sidebar.scroll(this.rows), BorderLayout.CENTER);
	}

	PluginManager.PanelSnapshot getSnapshot() {
		return this.snapshot;
	}

	void show(PluginManager.PanelSnapshot snapshot) {
		this.snapshot = snapshot;
		this.rebuild();
	}

	void rebuild() {
		if (this.snapshot == null) {
			return;
		}
		this.rows.removeAll();

		JPanel heading = new JPanel(new BorderLayout());
		heading.setBackground(Theme.DARKER);
		heading.setAlignmentX(LEFT_ALIGNMENT);
		heading.setBorder(BorderFactory.createEmptyBorder(7, 10, 6, 10));
		heading.add(Sidebar.label(this.snapshot.title, Theme.ACCENT, Theme.FONT_BOLD),
			BorderLayout.WEST);
		heading.setMaximumSize(new Dimension(Integer.MAX_VALUE, heading.getPreferredSize().height));
		this.rows.add(heading);

		if (this.snapshot.rows.isEmpty()) {
			this.rows.add(Sidebar.leftStrip(Sidebar.wrappedLabel(this.snapshot.emptyMessage,
				Theme.TEXT_DIM, Theme.FONT_SMALL, Theme.WIDTH - 36), 10, 10, 10, 10));
		} else {
			for (int i = 0; i < this.snapshot.rows.size(); i++) {
				this.rows.add(this.buildRow(this.snapshot.rows.get(i)));
			}
		}
		this.rows.revalidate();
		this.rows.repaint();
	}

	private Component buildRow(final ConfigList.Row row) {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(Theme.ROW);
		panel.setAlignmentX(LEFT_ALIGNMENT);
		panel.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.SEPARATOR),
			BorderFactory.createEmptyBorder(6, 10, 6, 8)));

		JPanel top = new JPanel(new BorderLayout(6, 0));
		top.setOpaque(false);
		top.setAlignmentX(LEFT_ALIGNMENT);

		JPanel text = new JPanel();
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		text.setOpaque(false);
		// What is left of the row once the reading on the right has taken its share.
		text.add(Sidebar.wrappedLabel(row.label, Theme.TEXT, Theme.FONT, Theme.WIDTH - 130));
		if (row.detail != null && row.detail.length() > 0) {
			text.add(Sidebar.wrappedLabel(row.detail, Theme.TEXT_DIM, Theme.FONT_SMALL,
				Theme.WIDTH - 130));
		}
		top.add(text, BorderLayout.CENTER);

		JPanel right = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 4, 0));
		right.setOpaque(false);
		if (row.value != null && row.value.length() > 0) {
			right.add(Sidebar.label(row.value, Theme.TEXT, Theme.FONT_BOLD));
		}
		if (row.action != null && row.action.length() > 0) {
			JButton button = new JButton(row.action);
			button.setFont(Theme.FONT_SMALL);
			button.setForeground(Theme.ACCENT);
			button.setBackground(Theme.DARKER);
			button.setFocusPainted(false);
			button.setBorder(BorderFactory.createEmptyBorder(2, 7, 2, 7));
			button.addActionListener(new ActionListener() {

				public void actionPerformed(ActionEvent event) {
					PanelPage.this.press(row.index);
				}
			});
			right.add(button);
		}
		if (right.getComponentCount() > 0) {
			top.add(right, BorderLayout.EAST);
		}
		top.setMaximumSize(new Dimension(Integer.MAX_VALUE, top.getPreferredSize().height));
		panel.add(top);

		if (row.progress >= 0) {
			panel.add(javax.swing.Box.createVerticalStrut(5));
			panel.add(new Bar(row.progress));
		}
		panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, panel.getPreferredSize().height));
		return panel;
	}

	/**
	 * Acts on a row, on the game thread.
	 *
	 * The snapshot the button was built from may be stale by the time it is pressed - the plugin
	 * has been writing to its own state all the while - so the page is read again afterwards
	 * rather than assuming what the press did to it.
	 */
	private void press(final int index) {
		final PluginManager.PanelSnapshot acting = this.snapshot;
		this.manager.invokeOnClientThread(new Runnable() {

			public void run() {
				try {
					acting.act(index);
				} catch (Throwable error) {
					// A plugin's own code on the game thread: one bad button, not a dead client.
					jagex2.client.DevLog.log("PLUGIN", "a panel button threw: " + error);
				}
			}
		});
	}

	/** A progress bar: a track and a fill, in the client's own colours and nothing else. */
	private static final class Bar extends JComponent {

		private static final int HEIGHT = 4;

		private final int percent;

		Bar(int percent) {
			this.percent = percent;
			this.setAlignmentX(LEFT_ALIGNMENT);
			this.setPreferredSize(new Dimension(0, HEIGHT));
			this.setMaximumSize(new Dimension(Integer.MAX_VALUE, HEIGHT));
		}

		protected void paintComponent(Graphics g) {
			int w = this.getWidth();
			g.setColor(Theme.DARKER);
			g.fillRect(0, 0, w, HEIGHT);
			g.setColor(Theme.ACCENT);
			g.fillRect(0, 0, w * this.percent / 100, HEIGHT);
		}
	}
}
