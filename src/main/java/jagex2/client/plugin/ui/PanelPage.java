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
import lostcity.Theme;

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

	/**
	 * The search box, for a page that asks for one (ConfigList.searchHint). Built once and kept,
	 * NOT rebuilt with the rows: the rows are thrown away whenever the plugin's numbers move, and a
	 * box thrown away with them would lose what was being typed in it.
	 */
	private final JPanel searchBar = new JPanel(new BorderLayout());
	private final HintField search = new HintField();

	/** The text the plugin last put in the box, so a change of its own can be told from typing. */
	private String pluginSearchText;

	/** Which page the box was last filled for, so moving to another page starts it afresh. */
	private String searchPage;

	/**
	 * What the rows were last built from, as text. The rows are rebuilt only when it changes.
	 *
	 * WHY NOT EVERY TIME, which is what this page did: a hover tooltip belongs to the component
	 * under the mouse, and a page rebuilt every 600ms throws that component away under it - so the
	 * tooltip on a loot icon or a skill blinked off before anybody could read it.
	 */
	private String builtFrom;

	/** What is being shown, kept so the page can be rebuilt when the plugin's numbers move. */
	private PluginManager.PanelSnapshot snapshot;

	PanelPage(PluginManager manager) {
		this.manager = manager;
		this.setLayout(new BorderLayout());
		this.setBackground(Theme.BACKGROUND);

		this.rows.setLayout(new BoxLayout(this.rows, BoxLayout.Y_AXIS));
		this.rows.setBackground(Theme.BACKGROUND);
		this.add(Sidebar.scroll(this.rows), BorderLayout.CENTER);

		this.search.setBackground(Theme.ROW);
		this.search.setForeground(Theme.TEXT);
		this.search.setCaretColor(Theme.TEXT);
		this.search.setFont(Theme.FONT);
		this.search.setBorder(BorderFactory.createEmptyBorder(4, 2, 4, 4));
		this.search.addActionListener(new ActionListener() {

			public void actionPerformed(ActionEvent event) {
				PanelPage.this.runSearch(PanelPage.this.search.getText());
			}
		});
		JLabel magnifier = new JLabel(Icons.search(13, Theme.TEXT_DIM));
		magnifier.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 2));
		JPanel box = new JPanel(new BorderLayout());
		box.setBackground(Theme.ROW);
		box.add(magnifier, BorderLayout.WEST);
		box.add(this.search, BorderLayout.CENTER);
		this.searchBar.setBackground(Theme.DARKER);
		this.searchBar.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
		this.searchBar.add(box, BorderLayout.CENTER);
		this.searchBar.setVisible(false);
		this.add(this.searchBar, BorderLayout.NORTH);
	}

	/** A text field that says what it is for while it is empty, in grey. */
	private static final class HintField extends javax.swing.JTextField {

		String hint = "";

		protected void paintComponent(Graphics g) {
			super.paintComponent(g);
			if (this.getText().length() == 0 && this.hint.length() > 0) {
				g.setColor(Theme.TEXT_DIM);
				g.setFont(this.getFont());
				java.awt.Insets in = this.getInsets();
				g.drawString(this.hint, in.left + 2, in.top + g.getFontMetrics().getAscent());
			}
		}
	}

	/** Enter in the box: hand the text to the plugin, on the game thread. */
	private void runSearch(final String text) {
		final PluginManager.PanelSnapshot acting = this.snapshot;
		if (acting == null) {
			return;
		}
		this.manager.invokeOnClientThread(new Runnable() {

			public void run() {
				try {
					acting.search(text);
				} catch (Throwable error) {
					jagex2.client.DevLog.log("PLUGIN", "a panel search threw", error);
				}
			}
		});
	}

	/**
	 * Shows the box for a page that wants one and fills it when the PLUGIN changed its text.
	 *
	 * Typing never comes back through here: the plugin's text only moves when it is told to search
	 * or looks somebody up itself, so a box the player is typing in is left alone until then.
	 */
	private void applySearch() {
		PluginManager.PanelSnapshot shown = this.snapshot;
		boolean wanted = shown != null && shown.searchHint != null;
		if (this.searchBar.isVisible() != wanted) {
			this.searchBar.setVisible(wanted);
			this.revalidate();
		}
		if (!wanted) {
			return;
		}
		String page = shown.entry.key + "/" + shown.title;
		if (!page.equals(this.searchPage)) {
			this.searchPage = page;
			this.pluginSearchText = null;
		}
		this.search.hint = shown.searchHint;
		if (!shown.searchText.equals(this.pluginSearchText)) {
			this.pluginSearchText = shown.searchText;
			if (!this.search.getText().equals(shown.searchText)) {
				this.search.setText(shown.searchText);
			}
		}
		this.search.repaint();
	}

	PluginManager.PanelSnapshot getSnapshot() {
		return this.snapshot;
	}

	void show(PluginManager.PanelSnapshot snapshot) {
		this.snapshot = snapshot;
		this.applySearch();
		this.rebuild();
	}

	void rebuild() {
		if (this.snapshot == null) {
			return;
		}
		String from = signature(this.snapshot);
		if (from.equals(this.builtFrom)) {
			return;
		}
		this.builtFrom = from;
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

		if (row.clickable) {
			top.addMouseListener(new java.awt.event.MouseAdapter() {

				public void mouseClicked(java.awt.event.MouseEvent event) {
					if (javax.swing.SwingUtilities.isLeftMouseButton(event)) {
						PanelPage.this.clickRow(row.index);
					}
				}
			});
			top.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
		}

		if (row.icons.length > 0 && !row.collapsed) {
			panel.add(javax.swing.Box.createVerticalStrut(5));
			panel.add(this.iconGrid(row));
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
					jagex2.client.DevLog.log("PLUGIN", "a panel button threw", error);
				}
			}
		});
	}

	/** A row's heading clicked, on the game thread, as {@link #press} does a button. */
	private void clickRow(final int index) {
		final PluginManager.PanelSnapshot acting = this.snapshot;
		this.manager.invokeOnClientThread(new Runnable() {

			public void run() {
				try {
					acting.click(index);
				} catch (Throwable error) {
					jagex2.client.DevLog.log("PLUGIN", "a panel row click threw", error);
				}
			}
		});
	}

	/** A picture's right-click word chosen, on the game thread. */
	private void iconAction(final int index, final int icon) {
		final PluginManager.PanelSnapshot acting = this.snapshot;
		this.manager.invokeOnClientThread(new Runnable() {

			public void run() {
				try {
					acting.iconAction(index, icon);
				} catch (Throwable error) {
					jagex2.client.DevLog.log("PLUGIN", "a panel icon action threw", error);
				}
			}
		});
	}

	/**
	 * Everything the rows show, as one string, so an unchanged page can be left standing.
	 *
	 * THE PICTURES BY THEIR PIXELS. An item's stack number is drawn into its icon by the cache, so
	 * a loot icon going from 4 to 5 changes nothing but pixels - and a page that compared words
	 * alone would keep showing 4.
	 */
	static String signature(PluginManager.PanelSnapshot snapshot) {
		StringBuilder out = new StringBuilder(256);
		out.append(snapshot.entry.key).append('/').append(snapshot.title).append('\t')
			.append(snapshot.emptyMessage).append('\t').append(snapshot.selectedTab);
		for (int i = 0; i < snapshot.tabTips.length; i++) {
			out.append("\ntab ").append(snapshot.tabTips[i]).append(' ').append(pixels(snapshot.tabs[i]));
		}
		for (int i = 0; i < snapshot.rows.size(); i++) {
			ConfigList.Row row = snapshot.rows.get(i);
			out.append("\nrow ").append(row.index).append('\t').append(row.label)
				.append('\t').append(row.detail).append('\t').append(row.value)
				.append('\t').append(row.action).append('\t').append(row.progress)
				.append('\t').append(row.iconAction).append('\t').append(row.clickable)
				.append('\t').append(row.collapsed).append('\t').append(pixels(row.icon));
			for (int j = 0; j < row.icons.length; j++) {
				out.append("\n icon ").append(pixels(row.icons[j]));
				if (row.captions != null && j < row.captions.length) {
					out.append("\tc ").append(row.captions[j]);
				}
				if (row.tips != null && j < row.tips.length) {
					out.append("\tt ").append(row.tips[j]);
				}
				if (row.faded != null && j < row.faded.length) {
					out.append(row.faded[j] ? "\tfaded" : "\tclear");
				}
			}
		}
		return out.toString();
	}

	/** A cheap fingerprint of an image's pixels: enough to see a stack number change. */
	private static String pixels(java.awt.image.BufferedImage image) {
		if (image == null) {
			return "-";
		}
		int w = image.getWidth();
		int h = image.getHeight();
		int hash = w * 31 + h;
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				hash = hash * 31 + image.getRGB(x, y);
			}
		}
		return Integer.toHexString(hash);
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
					jagex2.client.DevLog.log("PLUGIN", "a panel tab threw", error);
				}
			}
		});
	}

	/** How many icons fit across a page before the grid wraps. */
	static final int GRID_COLUMNS = 5;

	/** The same, when each picture has words beside it - a skill and its level. */
	static final int CAPTION_COLUMNS = 3;

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
	private Component iconGrid(final ConfigList.Row row) {
		java.awt.image.BufferedImage[] icons = row.icons;
		boolean captioned = row.captions != null;
		int columns = captioned ? CAPTION_COLUMNS : GRID_COLUMNS;
		int rows = (icons.length + columns - 1) / columns;
		JPanel grid = new JPanel(new java.awt.GridLayout(rows, columns, 2, 2));
		grid.setOpaque(false);
		grid.setAlignmentX(LEFT_ALIGNMENT);
		for (int i = 0; i < rows * columns; i++) {
			JLabel cell = new JLabel();
			cell.setOpaque(false);
			cell.setHorizontalAlignment(captioned ? JLabel.LEFT : JLabel.CENTER);
			if (i < icons.length) {
				boolean faded = row.faded != null && i < row.faded.length && row.faded[i];
				if (icons[i] != null) {
					cell.setIcon(new javax.swing.ImageIcon(faded ? fade(icons[i]) : icons[i]));
				}
				if (captioned && i < row.captions.length && row.captions[i] != null) {
					cell.setText(row.captions[i]);
					cell.setFont(Theme.FONT_BOLD);
					cell.setForeground(faded ? Theme.TEXT_DIM : Theme.TEXT);
					cell.setIconTextGap(4);
				}
				if (row.tips != null && i < row.tips.length && row.tips[i] != null && row.tips[i].length() > 0) {
					cell.setToolTipText(row.tips[i]);
				}
				if (row.iconAction != null && row.iconAction.length() > 0) {
					final int which = i;
					final String word = row.iconAction;
					cell.addMouseListener(new java.awt.event.MouseAdapter() {

						public void mousePressed(java.awt.event.MouseEvent event) {
							this.menu(event);
						}

						public void mouseReleased(java.awt.event.MouseEvent event) {
							this.menu(event);
						}

						private void menu(java.awt.event.MouseEvent event) {
							if (!event.isPopupTrigger()) {
								return;
							}
							javax.swing.JPopupMenu popup = new javax.swing.JPopupMenu();
							javax.swing.JMenuItem item = new javax.swing.JMenuItem(word);
							item.addActionListener(new ActionListener() {

								public void actionPerformed(ActionEvent e) {
									PanelPage.this.iconAction(row.index, which);
								}
							});
							popup.add(item);
							popup.show(event.getComponent(), event.getX(), event.getY());
						}
					});
				}
			}
			grid.add(cell);
		}
		return grid;
	}

	/** A picture at a third of its strength, for an item the player has said to ignore. */
	private static java.awt.image.BufferedImage fade(java.awt.image.BufferedImage image) {
		java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(image.getWidth(),
			image.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
		java.awt.Graphics2D g = out.createGraphics();
		g.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 0.35f));
		g.drawImage(image, 0, 0, null);
		g.dispose();
		return out;
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
