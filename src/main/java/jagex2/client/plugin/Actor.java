package jagex2.client.plugin;

/**
 * One npc or player in the scene, as a plugin sees it: a name, a place, and nothing it could act
 * on.
 *
 * Deliberately a snapshot of plain values rather than a handle on the live entity. The client's
 * own ClientNpc and ClientPlayer carry the model, the animation state and the route the thing is
 * walking, and a plugin holding one across a tick would be reading a thing that moved under it.
 * Everything here is copied on the game thread and then cannot change.
 *
 * COORDINATES ARE SCENE-LOCAL, the way {@link GroundItemPile}'s are, because that is what the
 * projection methods take. There are two pairs, and the difference matters:
 *
 *   - sceneX/sceneZ are FINE coordinates, 128 to a tile, and are where the actor actually is
 *     mid-step. PluginContext.project takes these. A label drawn from them follows a walking npc
 *     smoothly.
 *   - sceneTileX/sceneTileZ are the tile it is standing on. PluginContext.projectTile takes
 *     these. A label drawn from them snaps from tile to tile as the npc walks, which is right for
 *     a tile outline and wrong for a name tag.
 *
 * Use PluginContext.sceneToWorldX/Z to get absolute world coordinates when a plugin needs to
 * compare against somewhere it remembered.
 */
public final class Actor {

	/**
	 * The npc's or player's name, never null.
	 *
	 * An npc with no config loaded reads "null" - that is the string 377 puts in the config, not a
	 * missing value - and an npc whose type has not arrived yet reads "?". Neither is worth hiding
	 * behind an empty string, because a plugin matching on names wants to see that it happened.
	 *
	 * A player's name is the bare name: the @icon@ tags the client prefixes for a moderator crown
	 * or an ironman badge are not part of it.
	 */
	public final String name;

	/** Combat level, or 0 for something that has none - a banker, a shopkeeper, a fishing spot. */
	public final int combatLevel;

	/** The npc's config id, or -1 when this is a player. Also the "is this an npc" question. */
	public final int npcId;

	/** Fine scene x, 128 to a tile. What PluginContext.project wants. */
	public final int sceneX;

	/** Fine scene z, 128 to a tile. What PluginContext.project wants. */
	public final int sceneZ;

	/** The tile it stands on, 0 to 103. What PluginContext.projectTile wants. */
	public final int sceneTileX;

	/** The tile it stands on, 0 to 103. What PluginContext.projectTile wants. */
	public final int sceneTileZ;

	/** Width in tiles: 1 for a player and most npcs, more for a big one. */
	public final int size;

	/** True for the player running this client, so a plugin can leave itself out. */
	public final boolean self;

	/**
	 * Public for the same reason {@link GroundItemPile}'s is: a plugin that filters or reshapes
	 * what it was handed has to be able to build one, and a test has to be able to build the actor
	 * a rule is being checked against without a running client.
	 */
	public Actor(String name, int combatLevel, int npcId, int sceneX, int sceneZ, int size,
			boolean self) {
		this.name = name == null ? "?" : name;
		this.combatLevel = combatLevel;
		this.npcId = npcId;
		this.sceneX = sceneX;
		this.sceneZ = sceneZ;
		// Derived rather than passed, so the tile and the fine position can never disagree.
		this.sceneTileX = sceneX >> 7;
		this.sceneTileZ = sceneZ >> 7;
		this.size = size < 1 ? 1 : size;
		this.self = self;
	}

	/** Whether this is an npc rather than a player. */
	public boolean isNpc() {
		return this.npcId >= 0;
	}

	/**
	 * The fine scene x of the middle of what this actor occupies.
	 *
	 * A size-1 npc stands in the middle of its tile and this is just sceneX. A bigger one is
	 * anchored at its south-west tile, so a label drawn at sceneX sits on that corner rather than
	 * over the thing - which is the bug every first attempt at a boss overlay has.
	 */
	public int centreX() {
		return this.sceneX + (this.size - 1) * 64;
	}

	/** The fine scene z of the middle of what this actor occupies. See {@link #centreX()}. */
	public int centreZ() {
		return this.sceneZ + (this.size - 1) * 64;
	}

	/**
	 * Nearer to (fromX, fromZ) sorts first. A {@link java.util.Comparator}'s contract, so it can
	 * be handed to one.
	 *
	 * HERE RATHER THAN HIDDEN IN PluginContext because a plugin needs it too: getNpcs hands back a
	 * list already sorted this way, but one that filters, merges or adds to that list has to be
	 * able to put it back in order, and the alternative is every plugin writing this arithmetic
	 * again - including the part below that is easy to get wrong.
	 *
	 * Measured from the CENTRE, so a 3x3 npc standing on top of you does not sort as though it
	 * were a tile away; see {@link #centreX()}.
	 */
	public static int compareByDistance(Actor a, Actor b, int fromX, int fromZ) {
		// Squared distance, because the ordering is identical and a square root per comparison is
		// not. Widened to long: a scene is 13312 fine units across so an int does hold it today,
		// but a larger scene would overflow, and an overflow here is an ordering that is quietly
		// wrong rather than anything that announces itself.
		long da = distanceSquared(a, fromX, fromZ);
		long db = distanceSquared(b, fromX, fromZ);
		return da < db ? -1 : da > db ? 1 : 0;
	}

	/** Squared distance from a point to this actor's centre, in fine units. */
	public static long distanceSquared(Actor a, int fromX, int fromZ) {
		long dx = a.centreX() - fromX;
		long dz = a.centreZ() - fromZ;
		return dx * dx + dz * dz;
	}
}
