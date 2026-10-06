package jagex2.client.plugin.event;

/**
 * The right-click menu has been built for this frame and is about to be used: every option the
 * client is going to offer is in place, already priority-sorted, with the player's own left-click
 * swaps applied.
 *
 * This is the point to read or reorder it, through PluginContext.getMenuOption /
 * swapMenuEntries / setLeftClick. It fires every frame the mouse is over something, whether or not
 * a menu is open - the "menu" here is the list of options a click would produce, not a visible
 * window - so do as little as possible in a handler for it.
 */
public final class MenuBuilt {

	/** Number of entries, including the "Cancel" at index 0. */
	public final int size;

	public MenuBuilt(int size) {
		this.size = size;
	}
}
