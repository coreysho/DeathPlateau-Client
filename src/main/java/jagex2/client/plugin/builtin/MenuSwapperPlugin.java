package jagex2.client.plugin.builtin;

import jagex2.client.MenuSwaps;
import jagex2.client.plugin.ConfigList;
import jagex2.client.plugin.Plugin;
import jagex2.client.plugin.PluginDescriptor;
import jagex2.client.plugin.Subscribe;
import jagex2.client.plugin.event.MenuBuilt;
import jagex2.client.plugin.event.SettingsMenuOpening;

/**
 * Left-click swaps: shift-right-click a thing and pick what left-clicking it should do.
 *
 * Three parts, and each one is a different piece of the plugin API doing the job a chunk of
 * Client.java used to:
 *
 *   SETTING a swap  - SettingsMenuOpening, the shift-right-click menu. The rows were 60 lines of
 *                     menu building in buildSwapMenu(); they are the loops below now.
 *   APPLYING one    - MenuBuilt, every frame the mouse is over something. The pick is unchanged,
 *                     including which rule beats which, because that is the behaviour people
 *                     have and not a thing to improve while moving it.
 *   REVIEWING them  - a ConfigList on this plugin's page in the sidebar, in place of the F10
 *                     panel. The panel was 150 lines of drawing, hit-testing and scrolling.
 *
 * The rules themselves still live in MenuSwaps, in the file they always did: a player's swaps
 * survive this change because nothing about how they are stored has moved.
 */
@PluginDescriptor(
	name = "Left-click swaps",
	description = "Shift-right-click something to choose what left-clicking it does",
	key = "menu-swapper",
	enabledByDefault = true,
	legacySetting = "menu_swapper"
)
public final class MenuSwapperPlugin extends Plugin {

	protected void startUp() {
		this.addConfigList("Swaps", new ConfigList() {

			public int size() {
				return MenuSwaps.count();
			}

			public String label(int index) {
				return MenuSwaps.verb(index) + " on " + (MenuSwaps.isAny(index)
					? "any " + MenuSwaps.kindLabel(index)
					: MenuSwaps.target(index));
			}

			public String detail(int index) {
				return MenuSwaps.kindLabel(index);
			}

			/**
			 * Cycling a rule widens it to every target of its kind, and cycling again removes it
			 * - which is what the F10 panel's click did, kept because "this, then anything like
			 * this, then nothing" is a sensible way round a list with one button.
			 */
			public String action(int index) {
				return MenuSwaps.isAny(index) ? "Any" : "This one";
			}

			public void onAction(int index) {
				MenuSwaps.cycle(index);
			}

			public void onRemove(int index) {
				MenuSwaps.remove(MenuSwaps.kindTag(index), MenuSwaps.target(index));
			}

			public String emptyMessage() {
				return "Shift-right-click something in game and pick a left-click for it.";
			}
		});
	}

	// ------------------------------------------------------------------ setting a swap

	@Subscribe
	public void onSettingsMenuOpening(SettingsMenuOpening event) {
		this.addWalkHereRows(event);
		this.addSwapRows(event);
		this.addResetRows(event);
	}

	/**
	 * "Left-click Walk here on X" - a rule that makes a thing un-clickable, which is the one
	 * people reach for most. Only where walking is on offer at all.
	 *
	 * Deduped on the target rather than the option: several options share one target, and one of
	 * these per option would say the same thing three times.
	 */
	private void addWalkHereRows(SettingsMenuOpening event) {
		if (!event.isWorldMenu()) {
			return;
		}
		for (int i = 0; i < event.getTargets().size(); i++) {
			final SettingsMenuOpening.Target target = event.getTargets().get(i);
			// A player standing on the tile already produced a real "Walk here @whi@Name" option,
			// so that target has a row from addSwapRows; a second would say the same thing.
			if (target.verb.equalsIgnoreCase(MenuSwaps.WALK) || this.seenBefore(event, i)) {
				continue;
			}
			event.addRow("Left-click " + MenuSwaps.WALK + " @" + target.kind + "@" + target.name,
				new Runnable() {

					public void run() {
						MenuSwapperPlugin.this.set(target.kind, target.name, MenuSwaps.WALK);
					}
				});
		}
	}

	/** One row per option the ordinary menu offered, in the order it offered them. */
	private void addSwapRows(SettingsMenuOpening event) {
		for (int i = 0; i < event.getTargets().size(); i++) {
			final SettingsMenuOpening.Target target = event.getTargets().get(i);
			event.addRow("Left-click " + target.verb + " @" + target.kind + "@" + target.name,
				new Runnable() {

					public void run() {
						MenuSwapperPlugin.this.set(target.kind, target.name, target.verb);
					}
				});
		}
	}

	/** Only for targets that actually have a swap - offering to undo nothing is noise. */
	private void addResetRows(SettingsMenuOpening event) {
		for (int i = 0; i < event.getTargets().size(); i++) {
			final SettingsMenuOpening.Target target = event.getTargets().get(i);
			if (MenuSwaps.exact(target.kind, target.name) < 0 || this.seenBefore(event, i)) {
				continue;
			}
			event.addRow("Reset left-click @" + target.kind + "@" + target.name, new Runnable() {

				public void run() {
					MenuSwaps.remove(target.kind, target.name);
					MenuSwapperPlugin.this.ctx.addChatMessage(
						"Left-click on " + target.name + " is back to normal.");
				}
			});
		}
	}

	/** Whether an earlier target in the list is the same kind and name as this one. */
	private boolean seenBefore(SettingsMenuOpening event, int index) {
		SettingsMenuOpening.Target target = event.getTargets().get(index);
		for (int j = 0; j < index; j++) {
			SettingsMenuOpening.Target other = event.getTargets().get(j);
			if (other.kind.equals(target.kind) && other.name.equalsIgnoreCase(target.name)) {
				return true;
			}
		}
		return false;
	}

	private void set(String kind, String target, String verb) {
		if (MenuSwaps.add(kind, target, verb)) {
			this.ctx.addChatMessage(verb + " is now the left-click on " + target + ".");
		} else {
			this.ctx.addChatMessage("You can only have " + MenuSwaps.MAX
				+ " left-click swaps. Remove one from the plugin's settings first.");
		}
	}

	// ------------------------------------------------------------------ applying one

	/**
	 * Moves the player's preferred entry into the left-click slot, with the menu fully built and
	 * already priority-sorted.
	 *
	 * The pick is the client's, unchanged: an exact target beats a wildcard, and between two of
	 * the same kind the one set first wins, so the order in the list is the order they apply.
	 */
	@Subscribe
	public void onMenuBuilt(MenuBuilt event) {
		if (event.size < 3 || MenuSwaps.count() == 0) {
			return;                                  // Cancel plus one option: nothing to choose
		}
		int best = -1;
		int bestRule = Integer.MAX_VALUE;
		boolean bestExact = false;

		// Where "Walk here" sits, for a walk-here rule to promote. It carries no target tag
		// (unless a player happens to be standing on the tile), so the scan below cannot find it.
		int walkAt = -1;
		for (int i = 1; i < event.size; i++) {
			if (this.ctx.isWalkHere(i)) {
				walkAt = i;
				break;
			}
		}

		// The TOP entry is scanned too, even though it is already the left-click: a walk-here
		// rule has to be able to demote it. "Make the Guard un-clickable" is exactly the case
		// where Attack is already the default. A normal rule naming the current default is a
		// no-op, caught by the best == top guard at the end.
		for (int i = 1; i < event.size; i++) {
			String option = this.ctx.getMenuOption(i);
			int at = MenuSwaps.tagAt(option);
			if (at < 0) {
				continue;
			}
			String kind = MenuSwaps.parseKind(option, at);
			String target = MenuSwaps.parseTarget(option, at);
			int rule = MenuSwaps.match(kind, target, MenuSwaps.parseVerb(option, at));
			int promote = i;

			// A walk-here rule is stored against the target but promotes the "Walk here" entry -
			// that is the point of it. Checked inside the same scan so it competes on the same
			// exact-beats-wildcard terms rather than overriding them or being overridden.
			if (walkAt >= 0) {
				int walkRule = MenuSwaps.match(kind, target, MenuSwaps.WALK);
				if (walkRule >= 0 && (rule < 0 || better(walkRule, rule))) {
					rule = walkRule;
					promote = walkAt;
				}
			}
			if (rule < 0) {
				continue;
			}
			boolean exact = !MenuSwaps.isAny(rule);
			if ((exact && !bestExact) || (exact == bestExact && rule < bestRule)) {
				best = promote;
				bestRule = rule;
				bestExact = exact;
			}
		}
		if (best < 0) {
			return;
		}
		this.ctx.setLeftClick(best);
	}

	/** Rule a beats rule b: an exact target first, then whichever was set earlier. */
	private static boolean better(int a, int b) {
		boolean exactA = !MenuSwaps.isAny(a);
		boolean exactB = !MenuSwaps.isAny(b);
		return exactA != exactB ? exactA : a < b;
	}
}
