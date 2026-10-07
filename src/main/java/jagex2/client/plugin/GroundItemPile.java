package jagex2.client.plugin;

import java.util.List;

/**
 * Everything lying on one tile.
 *
 * A pile rather than a flat list of items because that is the unit the player sees and a plugin
 * draws: one stack of labels over one tile, one thing to project to the screen.
 *
 * Tile coordinates are SCENE-LOCAL (0 to 103), which is what the projection methods want -
 * PluginContext.projectTile takes exactly these.
 */
public final class GroundItemPile {

	public final int sceneTileX;
	public final int sceneTileZ;

	/** The items, in the order the client stacks them: the top of the pile first. */
	public final List<GroundItem> items;

	/**
	 * Public for the same reason {@link GroundItem}'s is: a plugin that filters or reshapes a
	 * pile before drawing it has to be able to hand back a pile, and a test has to be able to
	 * build the one a rule is being checked against.
	 */
	public GroundItemPile(int sceneTileX, int sceneTileZ, List<GroundItem> items) {
		this.sceneTileX = sceneTileX;
		this.sceneTileZ = sceneTileZ;
		this.items = items;
	}
}
