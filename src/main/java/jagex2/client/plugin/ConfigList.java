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

		Row(int index, String label, String detail, String action, boolean removable,
			String value, int progress) {
			this.index = index;
			this.label = label;
			this.detail = detail;
			this.action = action;
			this.removable = removable;
			this.value = value;
			this.progress = progress < 0 ? -1 : progress > 100 ? 100 : progress;
		}
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
					this.value(i), this.progress(i)));
			} catch (Throwable ignored) {
				// One bad row, not a blank page.
			}
		}
		return rows;
	}
}
