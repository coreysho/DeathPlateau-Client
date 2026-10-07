package jagex2.client.plugin.ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Insets;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.plaf.basic.BasicScrollBarUI;

import jagex2.client.plugin.PluginManager;

/**
 * The plugin sidebar: a Swing panel bolted to the side of the game window, the way RuneLite's is.
 *
 * WHY SWING AND NOT THE GAME CANVAS. The in-game panel (F8) is drawn with the client's own bitmap
 * fonts into a 512x334 viewport. That is fine for a list of checkboxes and is still there for the
 * applet, where there is no window to bolt anything to. It cannot have a text box, a scrollbar you
 * can drag, a tooltip or a mouse cursor that changes - and a settings page without a text box
 * cannot edit a number or a name. The window is already a JFrame with a BorderLayout and the game
 * in CENTER, so the sidebar costs the game nothing: the canvas keeps its exact 765x503 and the
 * window simply gets wider.
 *
 * THE RAIL down the outer edge is the way in, as it is in RuneLite: an icon per page, always
 * visible, and the page itself collapses behind it. Clicking a different icon switches page;
 * clicking the open one folds the page away and leaves the rail, which is how a player gets the
 * window back to the size of the game without losing the way back in. F8 and the title bar's
 * chevron still hide the whole thing, rail included.
 *
 * It holds two fixed tabs today. It is built as a list so that a plugin contributing its own
 * page is a matter of adding to that list rather than of rewriting this.
 *
 * THREADING. Everything in here runs on the event dispatch thread. Nothing in here touches a
 * plugin directly: reads are of fields that only change between frames, and every write is handed
 * to {@link PluginManager#invokeOnClientThread(Runnable)}. The manager calls back when anything
 * changes, and that callback bounces onto the EDT before touching a component.
 */
public final class Sidebar extends JPanel {

	private static final String CARD_LIST = "list";
	private static final String CARD_CONFIG = "config";
	private static final String CARD_HUB = "hub";

	private final PluginManager manager;
	private final CardLayout cards = new CardLayout();
	private final JPanel pages = new JPanel(this.cards);
	private final PluginListPanel list;
	private final ConfigPanel config;
	private final HubPanel hubPanel;
	private final java.util.List<Tab> tabs = new java.util.ArrayList<Tab>();

	/** Told when the sidebar's own width changes, so the window can re-fit around it. */
	private Runnable resizeListener;

	/** Which page is showing, or null while the rail is collapsed to icons alone. */
	private String openCard;

	public Sidebar(PluginManager manager) {
		this.manager = manager;
		this.list = new PluginListPanel(manager, this);
		this.config = new ConfigPanel(manager, this);
		this.hubPanel = new HubPanel(new jagex2.client.plugin.hub.Hub(manager));

		this.setLayout(new BorderLayout());
		this.setBackground(Theme.BACKGROUND);
		this.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, Theme.SEPARATOR));

		this.pages.setBackground(Theme.BACKGROUND);
		this.pages.setPreferredSize(new Dimension(Theme.WIDTH, 0));
		this.pages.add(this.list, CARD_LIST);
		this.pages.add(this.config, CARD_CONFIG);
		this.pages.add(this.hubPanel, CARD_HUB);
		this.add(this.pages, BorderLayout.CENTER);

		this.add(this.buildRail(), BorderLayout.EAST);
		this.list.rebuild();
		this.show(CARD_LIST);

		// The manager calls this from the game thread whenever a plugin starts, stops or the
		// folder is re-read.
		manager.setChangeListener(new Runnable() {

			public void run() {
				Sidebar.this.refreshLater();
			}
		});
	}

	/** One tab in the strip: an icon, a hover, and an underline when it is the open one. */
	private final class Tab extends JLabel {

		private final String card;
		private final String tip;
		private boolean selected;

		Tab(Icon icon, String card, String tip) {
			super(icon);
			this.card = card;
			this.tip = tip;
			this.setToolTipText(tip);
			this.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			this.paintBorder(false);
			this.addMouseListener(new MouseAdapter() {

				public void mousePressed(MouseEvent event) {
					// The open one folds the page away; any other one opens itself.
					if (Tab.this.selected && Sidebar.this.isPanelOpen()) {
						Sidebar.this.setPanelOpen(false);
					} else {
						Sidebar.this.show(Tab.this.card);
					}
				}
			});
		}

		void setSelected(boolean selected) {
			this.selected = selected;
			this.paintBorder(selected);
		}

		private void paintBorder(boolean selected) {
			// A bar down the inner edge, pointing at the page it opens - the underline this had
			// while the strip was horizontal reads as nothing at all once the icons are stacked.
			this.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(0, 2, 0, 0, selected ? Theme.ACCENT : Theme.DARKER),
				BorderFactory.createEmptyBorder(7, 5, 7, 7)));
		}

		boolean isSelected() {
			return this.selected;
		}

		String card() {
			return this.card;
		}

		String tip() {
			return this.tip;
		}
	}

	private Component buildRail() {
		JPanel rail = new JPanel();
		rail.setLayout(new javax.swing.BoxLayout(rail, javax.swing.BoxLayout.Y_AXIS));
		rail.setBackground(Theme.DARKER);
		rail.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, Theme.SEPARATOR));
		rail.setPreferredSize(new Dimension(Theme.RAIL_WIDTH, 0));
		rail.setMinimumSize(new Dimension(Theme.RAIL_WIDTH, 0));
		rail.setMaximumSize(new Dimension(Theme.RAIL_WIDTH, Integer.MAX_VALUE));

		this.tabs.add(new Tab(Icons.wrench(20, Theme.ACCENT), CARD_LIST, "Plugins"));
		this.tabs.add(new Tab(Icons.download(20, Theme.ACCENT), CARD_HUB, "Plugin hub"));
		for (int i = 0; i < this.tabs.size(); i++) {
			Tab tab = this.tabs.get(i);
			// BoxLayout stretches anything that lets it, and a stretched icon row leaves the
			// rail as two tabs sharing its whole height.
			tab.setAlignmentX(LEFT_ALIGNMENT);
			tab.setMaximumSize(new Dimension(Theme.RAIL_WIDTH, tab.getPreferredSize().height));
			rail.add(tab);
		}
		rail.add(javax.swing.Box.createVerticalGlue());
		return rail;
	}

	// ------------------------------------------------------------------ the page, open or folded

	/** Whether a page is showing beside the rail. */
	public boolean isPanelOpen() {
		return this.pages.isVisible();
	}

	/**
	 * Folds the page away, or brings it back.
	 *
	 * The rail stays either way: collapsing is how a player gets the window back to the width of
	 * the game while keeping the way back in, which is the whole point of a rail rather than a
	 * strip along the top of a panel that disappears with it.
	 */
	public void setPanelOpen(boolean open) {
		if (this.pages.isVisible() == open) {
			return;
		}
		this.pages.setVisible(open);
		if (!open) {
			this.openCard = null;
			for (int i = 0; i < this.tabs.size(); i++) {
				this.tabs.get(i).setSelected(false);
			}
		}
		this.revalidate();
		this.repaint();
		if (this.resizeListener != null) {
			this.resizeListener.run();
		}
	}

	/**
	 * Set by the window so it can re-fit when the page folds away.
	 *
	 * A callback rather than this reaching up for its own Window and calling pack(): the window
	 * has to lower its minimum size BEFORE packing or the pack is clamped at the old width, and
	 * that ordering is the window's business to know, not the sidebar's.
	 */
	public void setResizeListener(Runnable listener) {
		this.resizeListener = listener;
	}

	/** Width when the page is folded away, which is the rail and the border beside it. */
	public Dimension getPreferredSize() {
		if (!this.pages.isVisible()) {
			Insets in = this.getInsets();
			return new Dimension(Theme.RAIL_WIDTH + in.left + in.right, 0);
		}
		return super.getPreferredSize();
	}

	/** Switches tab, and tells the page it is being shown. */
	private void show(String card) {
		this.openCard = card;
		this.setPanelOpen(true);
		this.cards.show(this.pages, card);
		for (int i = 0; i < this.tabs.size(); i++) {
			this.tabs.get(i).setSelected(this.tabs.get(i).card().equals(card));
		}
		if (CARD_HUB.equals(card)) {
			this.hubPanel.shown();
			this.hubPanel.rebuild();
		} else if (CARD_LIST.equals(card)) {
			this.list.rebuild();
		}
	}

	void showList() {
		this.show(CARD_LIST);
	}

	void showConfig(PluginManager.Entry entry) {
		this.config.show(entry);
		this.openCard = CARD_CONFIG;
		this.setPanelOpen(true);
		this.cards.show(this.pages, CARD_CONFIG);
		for (int i = 0; i < this.tabs.size(); i++) {
			// The config page belongs to the plugin list, so its tab stays lit while it is open.
			this.tabs.get(i).setSelected(this.tabs.get(i).card().equals(CARD_LIST));
		}
	}

	/** Opens the hub tab, as clicking its tab does. Used by run_sidebarpreview.py. */
	public void openHub() {
		this.show(CARD_HUB);
	}

	/** Refreshes whichever page is showing. Safe to call from the game thread. */
	public void refreshLater() {
		SwingUtilities.invokeLater(new Runnable() {

			public void run() {
				Sidebar.this.list.rebuild();
				if (Sidebar.this.config.getEntry() != null) {
					Sidebar.this.config.rebuild();
				}
			}
		});
	}

	// ------------------------------------------------------------------ shared widgets

	/** A label that will not stretch its row: BoxLayout grows anything that lets it. */
	static JLabel label(String text, Color colour, java.awt.Font font) {
		JLabel label = new JLabel(text);
		label.setFont(font);
		label.setForeground(colour);
		label.setAlignmentX(LEFT_ALIGNMENT);
		label.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
		label.setMaximumSize(new Dimension(Integer.MAX_VALUE, label.getPreferredSize().height));
		return label;
	}

	/**
	 * A label that wraps instead of running off the side of the panel.
	 *
	 * WHY THE LINES ARE WORKED OUT HERE. Swing has no wrapping label. The usual trick is
	 * "<html><body style='width:195px'>", but the width in that style is advisory - the label
	 * still reports a preferred width wide enough for the longest unbroken run, and a scroll pane
	 * with no horizontal bar then clips the right-hand edge off every row. Measuring the words
	 * and inserting the breaks gives a label whose preferred size is the truth, which is what
	 * every layout above it is working from.
	 */
	static JLabel wrappedLabel(String text, Color colour, java.awt.Font font, int width) {
		JLabel label = new JLabel();
		label.setFont(font);
		label.setForeground(colour);
		label.setAlignmentX(LEFT_ALIGNMENT);
		label.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
		label.setText(wrap(label, text, font, width));
		return label;
	}

	/** Breaks text into lines no wider than the width, as HTML the label can render. */
	private static String wrap(JLabel label, String text, java.awt.Font font, int width) {
		java.awt.FontMetrics metrics;
		try {
			metrics = label.getFontMetrics(font);
		} catch (Throwable error) {
			return escape(text);          // no metrics available: one long line beats no label
		}
		StringBuilder html = new StringBuilder("<html>");
		StringBuilder line = new StringBuilder();
		String[] words = text.split(" ");
		for (int i = 0; i < words.length; i++) {
			String word = words[i];
			// A word too long for a line of its own has to be broken mid-word, or it hangs off
			// the edge however many line breaks are put in front of it. This is the usual case
			// for the one thing in the panel that most needs reading: the path to the plugins
			// folder, which has no spaces in it at all.
			while (metrics.stringWidth(word) > width) {
				int fits = 1;
				while (fits < word.length() && metrics.stringWidth(word.substring(0, fits + 1)) <= width) {
					fits++;
				}
				if (line.length() > 0) {
					html.append(escape(line.toString())).append("<br>");
					line.setLength(0);
				}
				html.append(escape(word.substring(0, fits))).append("<br>");
				word = word.substring(fits);
			}
			String candidate = line.length() == 0 ? word : line + " " + word;
			if (line.length() > 0 && metrics.stringWidth(candidate) > width) {
				html.append(escape(line.toString())).append("<br>");
				line.setLength(0);
				line.append(word);
			} else {
				line.setLength(0);
				line.append(candidate);
			}
		}
		html.append(escape(line.toString())).append("</html>");
		return html.toString();
	}

	private static String escape(String text) {
		StringBuilder out = new StringBuilder(text.length() + 16);
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '<') {
				out.append("&lt;");
			} else if (c == '>') {
				out.append("&gt;");
			} else if (c == '&') {
				out.append("&amp;");
			} else {
				out.append(c);
			}
		}
		return out.toString();
	}

	/**
	 * Puts a component flush against the left edge of a vertical BoxLayout.
	 *
	 * BoxLayout positions a child by its alignmentX against the WIDEST child in the column, not
	 * against the container - so a narrow label next to a full-width row drifts right. A
	 * BorderLayout strip that fills the width and holds the child in WEST settles it.
	 */
	static JPanel leftStrip(Component child, int top, int left, int bottom, int right) {
		JPanel strip = new JPanel(new BorderLayout());
		strip.setOpaque(false);
		strip.setAlignmentX(LEFT_ALIGNMENT);
		strip.setBorder(BorderFactory.createEmptyBorder(top, left, bottom, right));
		strip.add(child, BorderLayout.WEST);
		strip.setMaximumSize(new Dimension(Integer.MAX_VALUE, strip.getPreferredSize().height));
		return strip;
	}

	/** A flat icon button with no border, no fill and no focus ring - just the icon. */
	static JButton iconButton(Icon normal, Icon hovered, String tooltip) {
		final JButton button = new JButton(normal);
		button.setRolloverIcon(hovered);
		button.setToolTipText(tooltip);
		button.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
		button.setContentAreaFilled(false);
		button.setFocusPainted(false);
		button.setFocusable(false);
		button.setOpaque(false);
		button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		return button;
	}

	/**
	 * The panel that goes inside a scroll pane.
	 *
	 * WHY NOT A PLAIN JPanel. A scroll pane gives its view the view's own preferred width, and
	 * horizontal scrolling is off - so a view one pixel wider than the viewport silently loses its
	 * right-hand edge, which on a plugin row is exactly where the on/off switch is. Saying the
	 * view tracks the viewport width pins it to the visible width instead, and everything inside
	 * lays out within what there is.
	 */
	static final class ScrollingColumn extends JPanel implements javax.swing.Scrollable {

		ScrollingColumn() {
			super(new BorderLayout());
			this.setBackground(Theme.BACKGROUND);
		}

		public Dimension getPreferredScrollableViewportSize() {
			return this.getPreferredSize();
		}

		public int getScrollableUnitIncrement(java.awt.Rectangle visible, int orientation, int direction) {
			return 16;
		}

		public int getScrollableBlockIncrement(java.awt.Rectangle visible, int orientation, int direction) {
			return visible.height;
		}

		public boolean getScrollableTracksViewportWidth() {
			return true;
		}

		public boolean getScrollableTracksViewportHeight() {
			return false;
		}
	}

	/** A scroll pane themed to match: no border, a thin dark bar and no arrow buttons. */
	static JScrollPane scroll(Component view) {
		JScrollPane pane = new JScrollPane(view);
		pane.setBorder(BorderFactory.createEmptyBorder());
		pane.setBackground(Theme.BACKGROUND);
		pane.getViewport().setBackground(Theme.BACKGROUND);
		pane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		pane.getVerticalScrollBar().setUnitIncrement(16);
		pane.getVerticalScrollBar().setPreferredSize(new Dimension(8, 0));
		pane.getVerticalScrollBar().setUI(new BasicScrollBarUI() {

			protected void configureScrollBarColors() {
				this.thumbColor = new Color(0x4A, 0x4A, 0x4A);
				this.trackColor = Theme.DARKER;
			}

			protected JButton createDecreaseButton(int orientation) {
				return hiddenButton();
			}

			protected JButton createIncreaseButton(int orientation) {
				return hiddenButton();
			}
		});
		return pane;
	}

	/** A scrollbar arrow button that takes up no space, which is how you remove them. */
	private static JButton hiddenButton() {
		JButton button = new JButton() {

			public void paint(Graphics g) {
			}
		};
		button.setPreferredSize(new Dimension(0, 0));
		button.setMinimumSize(new Dimension(0, 0));
		button.setMaximumSize(new Dimension(0, 0));
		return button;
	}
}
