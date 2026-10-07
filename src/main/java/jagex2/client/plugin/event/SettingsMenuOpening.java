package jagex2.client.plugin.event;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The player shift-right-clicked, and the menu about to open is the SETTINGS menu: not things to
 * do, but preferences to set about whatever is under the cursor. A plugin adds its rows here.
 *
 * WHY THIS AND NOT "INSERT INTO ANY MENU". Letting a plugin put rows in the ordinary right-click
 * menu is the most dangerous thing this API could offer: the last entry is what a left click
 * performs, so a plugin could quietly make clicking a tree do something else. This menu is
 * different in kind - the client opens it only on shift, it is built fresh at the moment of the
 * click, the frame's real menu is untouched behind it, and NOTHING IN IT PERFORMS A GAME ACTION.
 * Choosing a row runs the plugin's own code and sends nothing to the server, which a plugin
 * could do from a game tick anyway. So this adds a place to put a choice, not a new power.
 *
 * ROWS APPEAR IN THE ORDER THEY ARE ADDED, bottom-upwards, because that is how the client stores
 * a menu: the last entry is the top row. "Do nothing to this" belongs nearest Cancel at the
 * bottom, and the rows that change something belong at the top where they are read first.
 *
 * <pre>
 * {@literal @}Subscribe
 * public void onSettingsMenuOpening(SettingsMenuOpening event) {
 *     for (final SettingsMenuOpening.Target target : event.getTargets()) {
 *         event.addRow("Hide @lre@" + target.name, new Runnable() {
 *             public void run() { Hidden.add(target.name); }
 *         });
 *     }
 * }
 * </pre>
 */
public final class SettingsMenuOpening {

	/** One thing the cursor is over, and one option the ordinary menu offers on it. */
	public static final class Target {

		/** "npc", "scenery", "item", "player" or "interface". */
		public final String kind;

		/** Its name, without colour tags. */
		public final String name;

		/** The option, e.g. "Attack" or "Chop down". */
		public final String verb;

		public Target(String kind, String name, String verb) {
			this.kind = kind;
			this.name = name;
			this.verb = verb;
		}
	}

	/** A row a plugin added, and what choosing it does. */
	public static final class Row {

		public final String label;
		public final Runnable action;

		Row(String label, Runnable action) {
			this.label = label;
			this.action = action;
		}
	}

	private final List<Target> targets;
	private final boolean worldMenu;
	private final int room;
	private final List<Row> rows = new ArrayList<Row>();

	public SettingsMenuOpening(List<Target> targets, boolean worldMenu, int room) {
		this.targets = targets;
		this.worldMenu = worldMenu;
		this.room = room;
	}

	/** Everything under the cursor, one entry per option the ordinary menu was offering. */
	public List<Target> getTargets() {
		return Collections.unmodifiableList(this.targets);
	}

	/**
	 * True for a right-click in the world, false for one in the inventory or an interface.
	 *
	 * Worth checking: a ground item and an inventory item carry the same tag and cannot be told
	 * apart by it, so a plugin about things on the floor would otherwise offer its rows over the
	 * backpack too.
	 */
	public boolean isWorldMenu() {
		return this.worldMenu;
	}

	/**
	 * Adds a row. Returns false when the menu has no room left, which a plugin adding a row per
	 * target should check rather than assume - a crowded tile can offer a lot of options.
	 */
	public boolean addRow(String label, Runnable action) {
		if (label == null || action == null || this.rows.size() >= this.room) {
			return false;
		}
		this.rows.add(new Row(label, action));
		return true;
	}

	/** What the plugins added, in order. Read by the client once every plugin has had the event. */
	public List<Row> getRows() {
		return this.rows;
	}
}
