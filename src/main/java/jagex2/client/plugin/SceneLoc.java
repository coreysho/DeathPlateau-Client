package jagex2.client.plugin;

/**
 * One piece of scenery in the scene, as a plugin sees it: an id, a place and a size.
 *
 * A SNAPSHOT OF PLAIN VALUES, like {@link Actor}, and for the same reason: the scene's own
 * entries carry models and animation state, and the server replaces them as doors open and
 * obstacles change. A plugin holding one across a tick would be reading a thing that had gone.
 *
 * COORDINATES ARE SCENE-LOCAL, which is what the projection methods take. A loc that covers
 * more than one tile is reported ONCE, at its origin tile, with the size it covers - so a
 * three-tile obstacle is one entry three tiles wide rather than three entries. Use
 * PluginContext.sceneToWorldX/Z for absolute coordinates.
 *
 * THE LAYER IS PART OF THE ANSWER. A wall, a wall decoration, scenery and a ground decoration
 * are four separate things that can sit on one tile, and the agility obstacles alone include
 * all four - a gate is a wall, a stile is scenery, a strange floor is a ground decoration.
 */
public final class SceneLoc {

	/** The layer a loc sits on, which is also the order the scene stores them in. */
	public static final int LAYER_WALL = 0;

	public static final int LAYER_WALL_DECOR = 1;

	public static final int LAYER_SCENERY = 2;

	public static final int LAYER_GROUND_DECOR = 3;

	public final int id;

	public final int sceneTileX;

	public final int sceneTileZ;

	public final int level;

	public final int layer;

	/** Which way round it was placed, 0 to 3. */
	public final int rotation;

	/**
	 * How many tiles it covers east-west and north-south AS PLACED.
	 *
	 * Already turned by the rotation, because a loc type gives its size unrotated and a drawer
	 * handed that raw outlines the wrong tiles for every loc placed on its side.
	 */
	public final int width;

	public final int length;

	public SceneLoc(int id, int sceneTileX, int sceneTileZ, int level, int layer, int rotation,
			int width, int length) {
		this.id = id;
		this.sceneTileX = sceneTileX;
		this.sceneTileZ = sceneTileZ;
		this.level = level;
		this.layer = layer;
		this.rotation = rotation;
		this.width = width < 1 ? 1 : width;
		this.length = length < 1 ? 1 : length;
	}

	public String toString() {
		return "loc " + this.id + " at " + this.sceneTileX + "," + this.sceneTileZ + " level "
			+ this.level + " layer " + this.layer + " (" + this.width + "x" + this.length + ")";
	}
}
