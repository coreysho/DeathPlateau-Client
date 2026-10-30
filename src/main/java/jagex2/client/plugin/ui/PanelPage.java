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
import javax.swing.JLabel;
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

		if (this.snapshot.tabs.length > 0) {
			this.rows.add(this.buildTabs());
		}

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

		if (row.icon != null) {
			JLabel picture = new JLabel(new javax.swing.ImageIcon(row.icon));
			picture.setOpaque(false);
			picture.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 4));
			top.add(picture, BorderLayout.WEST);
		}

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

		if (row.icons.length > 0) {
			panel.add(javax.swing.Box.createVerticalStrut(5));
			panel.add(iconGrid(row.icons));
		}

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
	/** How many tabs fit across the strip before it wraps. */
	static final int TAB_COLUMNS = 6;

	/**
	 * The strip of pictures above the rows.
	 *
	 * PICTURES AND NOT WORDS, because words across a 240-pixel column cost a line each and run
	 * out after three, while the game's own art says "herbs" in twenty pixels. The name is the
	 * tooltip, for the ones that are not obvious.
	 *
	 * The one showing is drawn on the row colour with the accent under it, the way the rail
	 * marks its own open page, so the strip and the rail say "you are here" the same way.
	 */
	private Component buildTabs() {
		final java.awt.image.BufferedImage[] icons = this.snapshot.tabs;
		int rows = (icons.length + TAB_COLUMNS - 1) / TAB_COLUMNS;
		JPanel strip = new JPanel(new java.awt.GridLayout(rows, TAB_COLUMNS, 2, 2));
		strip.setBackground(Theme.DARKER);
		strip.setAlignmentX(LEFT_ALIGNMENT);
		strip.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
		for (int i = 0; i < rows * TAB_COLUMNS; i++) {
			if (i >= icons.length) {
				JLabel filler = new JLabel();
				filler.setOpaque(false);
				strip.add(filler);
				continue;
			}
			final int which = i;
			JButton tab = new JButton();
			if (icons[i] != null) {
				tab.setIcon(new javax.swing.ImageIcon(icons[i]));
			} else {
				// No picture for this one - the cache had no item by that name - so the strip
				// falls back to a word rather than to a gap nobody can click with confidence.
				tab.setText(shortTip(this.snapshot.tabTips[i]));
				tab.setFont(Theme.FONT_SMALL);
				tab.setForeground(Theme.TEXT_DIM);
			}
			tab.setToolTipText(this.snapshot.tabTips[i]);
			tab.setFocusPainted(false);
			tab.setBackground(which == this.snapshot.selectedTab ? Theme.ROW : Theme.DARKER);
			tab.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(0, 0, 2, 0,
					which == this.snapshot.selectedTab ? Theme.ACCENT : Theme.DARKER),
				BorderFactory.createEmptyBorder(3, 3, 1, 3)));
			tab.addActionListener(new ActionListener() {

				public void actionPerformed(ActionEvent event) {
					PanelPage.this.pickTab(which);
				}
			});
			strip.add(tab);
		}
		strip.setMaximumSize(new Dimension(Integer.MAX_VALUE, strip.getPreferredSize().height));
		return strip;
	}

	/** Enough of a tab's name to tell it from its neighbours when there is no picture. */
	static String shortTip(String tip) {
		if (tip == null || tip.length() == 0) {
			return "?";
		}
		return tip.length() <= 3 ? tip : tip.substring(0, 3);
	}

	/**
	 * Shows a tab, on the game thread, then reads the page again.
	 *
	 * Same shape as {@link #press}: what the plugin does with the click is its own business and
	 * the snapshot in hand is stale the moment it happens.
	 */
	private void pickTab(final int index) {
		final PluginManager.PanelSnapshot acting = this.snapshot;
		this.manager.invokeOnClientThread(new Runnable() {

			public void run() {
				try {
					acting.selectTab(index);
				} catch (Throwable error) {
					// As press(): a plugin's own code on the game thread, so one bad tab is a
					// log line and not a dead client.
					jagex2.client.DevLog.log("PLUGIN", "a panel tab threw: " + error);
				}
			}
		});
	}

	/** How many icons fit across a page before the grid wraps. */
	static final int GRID_COLUMNS = 5;

	/**
	 * A block of item pictures under a row, five to a line.
	 *
	 * A GRID RATHER THAN A SENTENCE. This is a kill's drops, and the thing a player does with
	 * it is glance: twelve pictures in a block are read at once, while "2947 x Zulrah's scales,
	 * 30 x Grimy ranarr weed, ..." has to be read a word at a time. The count is already drawn
	 * into the picture by the cache, which is where the game draws it too.
	 *
	 * The last line is padded with empty cells so the ones above it stay square - a GridLayout
	 * with a short final row stretches every cell in it otherwise.
	 */
	private static Component iconGrid(java.awt.image.BufferedImage[] icons) {
		int rows = (icons.length + GRID_COLUMNS - 1) / GRID_COLUMNS;
		JPanel grid = new JPanel(new java.awt.GridLayout(rows, GRID_COLUMNS, 2, 2));
		grid.setOpaque(false);
		grid.setAlignmentX(LEFT_ALIGNMENT);
		for (int i = 0; i < rows * GRID_COLUMNS; i++) {
			JLabel cell = new JLabel();
			cell.setOpaque(false);
			cell.setHorizontalAlignment(JLabel.CENTER);
			if (i < icons.length && icons[i] != null) {
				cell.setIcon(new javax.swing.ImageIcon(icons[i]));
			}
			grid.add(cell);
		}
		return grid;
	}

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
