package jagex2.client.plugin;

import java.util.ArrayList;
import java.util.List;

/**
 * A list of things a plugin keeps, shown on its config page: the swaps you have set, the ground
 * items you have hidden, whatever a plugin accumulates.
 *
 * WHY THIS IS NOT A @ConfigItem. A config item is one value the player chooses. These are rows
 * the PLUGIN produces, usually made in game rather than typed - you set a left-click swap by
 * right-clicking the thing, not by spelling its name into a box. So the page does not offer an
 * "add" field: it lists what is there, lets a row be cycled through its settings or removed, and
 * leaves creating one to the game.
 *
 * IT IS READ ON THE GAME THREAD, ALWAYS. The sidebar runs on Swing's thread and the rows usually
 * come from state the game loop is writing; asking size() and then label(i) from the UI thread
 * is a race whose prize is an index out of bounds. {@link #snapshot()} is taken on the game
 * thread and the panel draws from that, so an implementation can read its own state plainly
 * without locking anything.
 *
 * <pre>
 * protected void startUp() {
 *     this.addConfigList("Hidden items", new ConfigList() {
 *         public int size()               { return Hidden.count(); }
 *         public String label(int i)      { return Hidden.name(i); }
 *         public String action(int i)     { return Hidden.isHidden(i) ? "Hidden" : "Shown"; }
 *         public void onAction(int i)     { Hidden.cycle(i); }
 *         public void onRemove(int i)     { Hidden.remove(i); }
 *     });
 * }
 * </pre>
 */
public abstract class ConfigList {

	/** One row, as the panel draws it. Taken on the game thread, drawn on the UI thread. */
	public static final class Row {

		public final int index;
		public final String label;
		public final String detail;
		public final String action;
		public final boolean removable;

		/** A reading on the right of the row, or null. */
		public final String value;

		/** 0 to 100 for a bar under the row, or -1 for none. */
		public final int progress;

		/**
		 * A picture at the left of the row - a crop, an item - or null.
		 *
		 * COPIED HERE, NOT HELD AS A SPRITE, and that is the whole point of this field. The
		 * cache hands out item icons from a round-robin of Pix32s that the GAME thread
		 * overwrites as other items are drawn, while a row is painted later on the Swing
		 * thread. Keeping the Sprite would mean painting whatever that slot had become - the
		 * same trap Actor documents for npc configs, one thread further apart. The pixels are
		 * taken here, on the game thread, at the moment the snapshot is.
		 */
		public final java.awt.image.BufferedImage icon;

		/**
		 * A grid of pictures UNDER the row, or empty.
		 *
		 * Separate from {@link #icon} because it is a different shape of thing: one icon labels
		 * the row, and a grid is the row's contents - a kill's drops, a tab's items - laid out
		 * as a block the eye reads at once rather than as a sentence.
		 */
		public final java.awt.image.BufferedImage[] icons;

		Row(int index, String label, String detail, String action, boolean removable,
			String value, int progress) {
			this(index, label, detail, action, removable, value, progress, null, null);
		}

		Row(int index, String label, String detail, String action, boolean removable,
			String value, int progress, Sprite icon, Sprite[] icons) {
			this.icon = icon == null ? null : icon.toImage();
			this.icons = copy(icons);
			this.index = index;
			this.label = label;
			this.detail = detail;
			this.action = action;
			this.removable = removable;
			this.value = value;
			this.progress = progress < 0 ? -1 : progress > 100 ? 100 : progress;
		}
	}

	static final java.awt.image.BufferedImage[] NO_ICONS = new java.awt.image.BufferedImage[0];

	/** Every sprite turned into its own image, here and now, for the reason Row.icon gives. */
	private static java.awt.image.BufferedImage[] copy(Sprite[] icons) {
		if (icons == null || icons.length == 0) {
			return NO_ICONS;
		}
		java.awt.image.BufferedImage[] out = new java.awt.image.BufferedImage[icons.length];
		for (int i = 0; i < icons.length; i++) {
			out[i] = icons[i] == null ? null : icons[i].toImage();
		}
		return out;
	}

	/**
	 * A picture for the left of a row, or null for none.
	 *
	 * Asked for as a Sprite rather than a name, unlike the rail's icons, because these are the
	 * game's own art - an item, a crop - and there is no fixed set of them to name.
	 */
	public Sprite icon(int index) {
		return null;
	}

	/** A grid of pictures to draw under a row, or null for none. */
	public Sprite[] icons(int index) {
		return null;
	}

	/** How many rows there are. */
	public abstract int size();

	/**
	 * A reading to put on the right of the row - an amount, a rate, a level - or null for none.
	 *
	 * SEPARATE FROM detail() because it is a different kind of thing: detail explains the row in
	 * a dimmer second line under the label, and this is the number the row exists to show, which
	 * belongs in a column where the eye can run down it.
	 */
	public String value(int index) {
		return null;
	}

	/**
	 * Progress through something, 0 to 100, drawn as a bar under the row. -1 for no bar.
	 *
	 * The only shape of chart this offers, and deliberately: a plugin that wants to draw needs
	 * an overlay, and a panel that could draw anything would be a plugin handing the client a
	 * Swing component, which is the thing a declarative list is here to avoid.
	 */
	public int progress(int index) {
		return -1;
	}

	/** The row's text. */
	public abstract String label(int index);

	/** A second, dimmer line under the label, or null for none. */
	public String detail(int index) {
		return null;
	}

	/**
	 * The label for this row's button, or null for no button. It is the row's current setting
	 * rather than a verb - "Hidden", "Attack" - because pressing it cycles to the next one, and
	 * what a player needs to see is where it is now.
	 */
	public String action(int index) {
		return null;
	}

	/** Pressing the row's button. Called on the game thread. */
	public void onAction(int index) {
	}

	/** Whether this row can be removed. */
	public boolean removable(int index) {
		return true;
	}

	/** Removing the row. Called on the game thread. */
	public void onRemove(int index) {
	}

	/** Shown in place of the rows when there are none. Say where they come from. */
	public String emptyMessage() {
		return "Nothing here yet.";
	}

	/**
	 * Reads the whole list in one go, on the game thread. A row that throws is left out rather
	 * than taking the page with it - a half-written list is still worth showing.
	 */
	final List<Row> snapshot() {
		List<Row> rows = new ArrayList<Row>();
		int size;
		try {
			size = this.size();
		} catch (Throwable error) {
			return rows;
		}
		for (int i = 0; i < size; i++) {
			try {
				String label = this.label(i);
				if (label == null) {
					continue;
				}
				rows.add(new Row(i, label, this.detail(i), this.action(i), this.removable(i),
					this.value(i), this.progress(i), this.icon(i), this.icons(i)));
			} catch (Throwable ignored) {
				// One bad row, not a blank page.
			}
		}
		return rows;
	}
}
