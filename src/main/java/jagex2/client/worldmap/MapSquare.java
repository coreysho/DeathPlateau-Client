/*
 * One map square, drawn the way the minimap draws it.
 *
 * THE GAME AND THE OFFLINE TOOL SHARE THIS, which is the point of it being here rather than in
 * tools/. The client opens its world map on it; tools/clienttests/WorldMapRender.java renders
 * PNGs with it from a packed cache; and the checks in WorldMapTest, which are about layout and
 * not about colour, are therefore checks on what ships.
 *
 * NOTHING HERE CHOOSES A COLOUR. 377 has no world map - it predates them - so there is nothing
 * to turn on, and a map drawn by reimplementing the tile colours would be a second renderer to
 * keep in step with the first. What there IS, is the minimap: World3D.method309 already knows
 * the colour of a tile and Client.drawMinimapLoc already knows how to draw a wall over it. This
 * drives those, over map squares handed to it as the bytes the cache holds.
 *
 *   World.method22    decode the land into heights and flags
 *   World.method27    add the scenery
 *   World.method15    build and light the terrain
 *   World3D.method309 the colour of one tile
 *
 * If a lake is the wrong blue here, it is the wrong blue in game.
 *
 * ONE SQUARE AT A TIME, WITH ITS NEIGHBOURS AROUND IT. A map square is 64 tiles and the client's
 * scene is 104, and method31 hard-codes that 104, so a scene cannot hold the world or even a
 * 3x3 block of squares. But terrain shading reads the tiles around the one it is lighting, so a
 * square loaded alone would come out with a seam of wrong light down every edge. The square
 * therefore goes in at a margin with its eight neighbours around it - the parts of them that
 * land outside the scene are skipped by method31 on the way past - and only the middle 64x64 is
 * kept.
 *
 * WHAT IT BORROWS AND PUTS BACK. The client keeps the terrain light, the low-memory switch and
 * the bound 2D surface in statics that the live game is also using, and this writes all of
 * them. On the game thread that is a few microseconds during which the game's own drawing would
 * see a world map's settings - so every one of them is saved on the way in and restored on the
 * way out, and the renderer is never called from anywhere but the game thread.
 */
package jagex2.client.worldmap;

import jagex2.config.LocType;
import jagex2.dash3d.CollisionMap;
import jagex2.dash3d.World;
import jagex2.dash3d.World3D;
import jagex2.graphics.Pix2D;
import jagex2.graphics.Pix8;

public final class MapSquare {

	/**
	 * A tile of this level that the map does not show.
	 *
	 * Bit 8 says the tile belongs to the level below and is drawn down there instead; bit 16 is
	 * the one the client skips on the minimap outright. Together they are why a bridge is not
	 * a hole: the span over a river is built at level 1 and marked as belonging at level 0, so
	 * level 0 leaves its own water tile out and takes the one above. Ignore the pair and the
	 * ground level comes out with a black gap across every bridge in the game, which reads as
	 * a river that nothing crosses.
	 */
	public static final int ELSEWHERE = 0x18;

	/** The bit that says a tile of the level above is drawn down here. */
	public static final int BELOW = 0x8;

	/** Pixels per tile, as the minimap draws it. */
	public static final int SCALE = 4;

	/** Tiles along one side of a map square. */
	public static final int SQUARE = 64;

	/** Tiles along one side of a scene, which method31 will not let us exceed. */
	public static final int SCENE = 104;

	/**
	 * Where in the scene the square being drawn sits, leaving a margin of neighbours around it.
	 *
	 * Any of these renders the same picture; there is more than one only so that a square whose
	 * lighting divides by zero at one scene edge can be tried against another. The lighting
	 * window reaches five tiles, and a square is 64 of the scene's 104, so a margin has to be at
	 * least 5 and at most 35.
	 */
	public static final int[] MARGINS = new int[] { 20, 28, 12, 34, 6 };

	/**
	 * A wall the player cannot interact with: near-white.
	 *
	 * The client rolls these two a shade apart on every login, the same jitter it gives the sun,
	 * and then hard-codes exactly these values where the roll is out of scope - so these are
	 * both the middle of the range and the numbers the client itself writes down.
	 */
	public static final int WALL = 0xEEEEEE;

	/** A door, gate or anything else that opens: dark red. */
	public static final int DOOR = 0xEE0000;

	/** Pixels along one side of a drawn square. */
	public static final int SIDE = SQUARE * SCALE;

	/**
	 * The sprites the minimap draws in place of a building: the furnace, the anvil, the altar,
	 * the tree. Set once by whoever loaded the media - the client from its own jagfile, the
	 * offline tool from a packed one - because this has no business opening files.
	 *
	 * NOT THE CLIENT'S OWN COPY. The client tints all of its mapscenes by a random shade on
	 * every login, the same jitter it gives the sun. The map pins the sun; it takes untinted
	 * sprites to match.
	 */
	static Pix8[] scenes = new Pix8[0];

	public static void useScenes(Pix8[] sprites) {
		scenes = sprites == null ? new Pix8[0] : sprites;
	}

	private MapSquare() {
	}

	/**
	 * One square as 256 by 256 pixels, or null when there is no land for it.
	 *
	 * RETRIED AT A DIFFERENT MARGIN, because method15 divides by the total weight of the floors
	 * in an eleven-by-eleven window and at least one square in the world has a strip along a
	 * scene edge where that window is clipped short and every floor left in it weighs nothing.
	 * Moving the square within the scene moves the clipped edge somewhere else. It cannot change
	 * the picture: the height noise is seeded from world coordinates, not scene ones, and every
	 * margin here leaves the square more than five tiles of loaded neighbour on all four sides,
	 * which is as far as the lighting window reaches.
	 *
	 * @param land  the nine squares' land data, [dx + 1][dz + 1], nulls allowed
	 * @param locs  the nine squares' scenery, same shape, nulls allowed
	 */
	public static int[] draw(byte[][][] land, byte[][][] locs, int sx, int sz, int level) {
		if (land[1][1] == null) {
			return null;
		}
		ArithmeticException last = null;
		for (int i = 0; i < MARGINS.length; i++) {
			try {
				return draw(land, locs, sx, sz, level, MARGINS[i]);
			} catch (ArithmeticException degenerate) {
				last = degenerate;
			}
		}
		throw last;
	}

	public static int[] draw(byte[][][] land, byte[][][] locs, int sx, int sz, int level,
			int margin) {
		// WHAT THE LIVE GAME IS ALSO USING. Saved here, restored in the finally below.
		boolean wasLowMem = World.lowMem;
		int wasLight = World.field104;
		int wasAmbient = World.field126;
		int[] wasSurface = Pix2D.data;
		int wasWide = Pix2D.width2d;
		int wasHigh = Pix2D.height2d;
		int wasTop = Pix2D.top;
		int wasBottom = Pix2D.bottom;
		int wasLeft = Pix2D.left;
		int wasRight = Pix2D.right;
		int wasSafe = Pix2D.safeWidth;
		int wasCentreX = Pix2D.centerX2d;
		int wasCentreY = Pix2D.centerY2d;
		try {
			// The scene is never looked at from inside, so nothing should be hidden from it.
			World.lowMem = false;
			return build(land, locs, sx, sz, level, margin);
		} finally {
			World.lowMem = wasLowMem;
			World.field104 = wasLight;
			World.field126 = wasAmbient;
			Pix2D.data = wasSurface;
			Pix2D.width2d = wasWide;
			Pix2D.height2d = wasHigh;
			Pix2D.top = wasTop;
			Pix2D.bottom = wasBottom;
			Pix2D.left = wasLeft;
			Pix2D.right = wasRight;
			Pix2D.safeWidth = wasSafe;
			Pix2D.centerX2d = wasCentreX;
			Pix2D.centerY2d = wasCentreY;
		}
	}

	private static int[] build(byte[][][] land, byte[][][] locs, int sx, int sz, int level,
			int margin) {
		int[][][] heightmap = new int[4][SCENE + 1][SCENE + 1];
		byte[][][] flags = new byte[4][SCENE][SCENE];
		CollisionMap[] collision = new CollisionMap[4];
		for (int i = 0; i < 4; i++) {
			collision[i] = new CollisionMap(SCENE, SCENE);
		}
		World world = new World(heightmap, flags, SCENE, SCENE);

		// The scene's own origin in world tiles, which is what the height noise is seeded from.
		int baseX = sx * SQUARE - margin;
		int baseZ = sz * SQUARE - margin;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				byte[] ground = land[dx + 1][dz + 1];
				int offX = margin + dx * SQUARE;
				int offZ = margin + dz * SQUARE;
				if (ground == null) {
					world.method28(offX, offZ, SQUARE, SQUARE);
				} else {
					world.method22(offZ, baseZ, offX, ground, baseX, collision);
				}
			}
		}

		World3D scene = new World3D(heightmap, SCENE, 4, SCENE);
		// THE SCENERY, AND THE NEIGHBOURS' SCENERY TOO. Not only so that a building standing
		// across the boundary is drawn on both sides of it: a loc casts a shadow onto the tiles
		// around it, and that shadow is part of what method15 lights the ground by, so a square
		// built without its neighbours' scenery is a different colour along its edges.
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				byte[] scenery = locs[dx + 1][dz + 1];
				if (scenery != null) {
					world.method27(margin + dz * SQUARE, collision, margin + dx * SQUARE,
						scene, scenery);
				}
			}
		}
		// THE SAME SUN OVER EVERY SQUARE. method15 nudges the light a step or two every time it
		// runs and keeps the walk inside its own clamps, which is why no two logins look quite
		// alike. Over seven hundred squares that walk wanders the full width of those clamps,
		// and it shows: a third of the pixels in a square move when the light does. There is no
		// true light to restore - the game picks a different one each time - so the map pins it
		// by handing method15 a value already past the clamp, which comes back as the clamp
		// itself whatever the nudge was. The bright end, because a map is for reading.
		World.field104 = 10;
		World.field126 = 20;
		world.method15(collision, scene);

		int[] pixels = new int[SIDE * SIDE];
		for (int tz = 0; tz < SQUARE; tz++) {
			int row = rowOf(tz, SIDE);
			for (int tx = 0; tx < SQUARE; tx++) {
				int x = margin + tx;
				int z = margin + tz;
				int at = row + tx * SCALE;
				if (onThisMap(flags, level, x, z)) {
					scene.method309(pixels, at, SIDE, level, x, z);
				}
				if (fromAbove(flags, level, x, z)) {
					scene.method309(pixels, at, SIDE, level + 1, x, z);
				}
			}
		}
		drawLocs(scene, flags, pixels, SIDE, level, margin);
		return pixels;
	}

	/** Whether a square has anything on it at all, or is the black a level above the roofs is. */
	public static boolean isEmpty(int[] pixels) {
		for (int i = 0; i < pixels.length; i++) {
			if ((pixels[i] & 0xFFFFFF) != 0) {
				return false;
			}
		}
		return true;
	}

	/** Whether this level's own tile is drawn here, or belongs somewhere else. */
	public static boolean onThisMap(byte[][][] flags, int level, int x, int z) {
		return (flags[level][x][z] & ELSEWHERE) == 0;
	}

	/** Whether the tile of the level above is drawn here instead - a bridge, a walkway, a pier. */
	public static boolean fromAbove(byte[][][] flags, int level, int x, int z) {
		return level < 3 && (flags[level + 1][x][z] & BELOW) != 0;
	}

	/**
	 * Walls, doors and mapscene icons over the ground, as Client.drawMinimapLoc draws them.
	 *
	 * THIS IS THE ONE PART THAT IS COPIED RATHER THAN CALLED, and only because the client's own
	 * version is wired to one particular picture: it indexes a 512-wide minimap at a fixed
	 * offset of 48, with the scene's 104 tiles baked into the arithmetic. None of the DECISIONS
	 * are rewritten - which pixels a wall of each shape and rotation lights up, which colour a
	 * door gets against a wall, where a mapscene sprite is centred on a loc bigger than a tile -
	 * only where the picture starts.
	 *
	 * RED IS A DOOR AND WHITE IS A WALL, which World.method21 says by the sign of the bitset: it
	 * sets the top bit for a loc the player cannot interact with. The two colours are random
	 * greys in the client, a shade apart on every login; here they are the middle of that range.
	 *
	 * EVERY TILE IN THE SCENE, not only the square's own 64 by 64, because a loc is anchored at
	 * one tile and a four-tile building anchored in the next square still has three tiles of
	 * itself over the boundary. Anything that lands off the picture is dropped by dot() and by
	 * the sprite clipping, which is the same thing the client does at the edge of its minimap.
	 */
	static void drawLocs(World3D scene, byte[][][] flags, int[] pixels, int side, int level,
			int margin) {
		Pix2D.bind(side, side, pixels);
		Pix2D.resetClipping();
		for (int x = 1; x < SCENE - 1; x++) {
			for (int z = 1; z < SCENE - 1; z++) {
				if (onThisMap(flags, level, x, z)) {
					locsOn(scene, pixels, side, level, x, z, margin);
				}
				if (fromAbove(flags, level, x, z)) {
					locsOn(scene, pixels, side, level + 1, x, z, margin);
				}
			}
		}
	}

	/** Everything standing on one tile: its wall, whatever is fixed to the wall, and its scenery. */
	static void locsOn(World3D scene, int[] pixels, int side, int level, int x, int z,
			int margin) {
		int px = (x - margin) * SCALE;
		int pz = (SQUARE - 1 - (z - margin)) * SCALE;

		int wall = scene.method300(level, x, z);
		if (wall != 0) {
			int info = scene.method304(level, x, z, wall);
			int id = wall >> 14 & 0x7FFF;
			if (hasScene(id)) {
				plotScene(id, x, z, margin);
			} else {
				wallFrom(pixels, side, px, pz, info, wall > 0 ? DOOR : WALL);
			}
		}

		int decor = scene.method302(level, x, z);
		if (decor != 0) {
			int info = scene.method304(level, x, z, decor);
			int id = decor >> 14 & 0x7FFF;
			if (hasScene(id)) {
				plotScene(id, x, z, margin);
			} else if (shapeOf(info) == 9) {
				diagonal(pixels, side, px, pz, rotationOf(info), decor > 0 ? DOOR : WALL);
			}
		}

		int ground = scene.method303(level, x, z);
		if (ground != 0 && hasScene(ground >> 14 & 0x7FFF)) {
			plotScene(ground >> 14 & 0x7FFF, x, z, margin);
		}
	}

	/** Whether a loc is drawn as a picture of itself rather than as lines on its tile edges. */
	static boolean hasScene(int id) {
		return LocType.method561(id).field1649 != -1;
	}

	/**
	 * A mapscene sprite, centred on the loc rather than on the tile it is anchored at.
	 *
	 * A loc two tiles across is anchored at its south-west corner, so its picture starts two
	 * tiles further north than the tile it sits on, and the sprite is usually smaller than the
	 * space it fills. Both of those are the client's arithmetic, unchanged. A loc asking for a
	 * sprite this media has not got is drawn as nothing, which is also what the client does.
	 */
	static void plotScene(int id, int x, int z, int margin) {
		LocType loc = LocType.method561(id);
		int wide = loc.field1655;
		int high = loc.field1629;
		int which = loc.field1649;
		if (which < 0 || which >= scenes.length || scenes[which] == null) {
			return;
		}
		Pix8 sprite = scenes[which];
		int offX = (wide * SCALE - sprite.wi) / 2;
		int offZ = (high * SCALE - sprite.hi) / 2;
		sprite.plotSprite((SQUARE - (z - margin) - high) * SCALE + offZ,
			(x - margin) * SCALE + offX);
	}

	/**
	 * Which of a tile's 32 wall shapes this is: a straight wall, a corner, a diagonal.
	 *
	 * SHAPE AND ROTATION TRAVEL IN ONE BYTE and are the same small numbers, so reading one
	 * where the other was meant draws walls of the right length in the right colour on the
	 * wrong edges, which looks exactly like a town.
	 */
	public static int shapeOf(int info) {
		return info & 0x1F;
	}

	/** Which quarter turn it stands at. */
	public static int rotationOf(int info) {
		return info >> 6 & 0x3;
	}

	/** One wall from the byte the scene stored it under. */
	public static void wallFrom(int[] pixels, int side, int px, int pz, int info, int colour) {
		drawWall(pixels, side, px, pz, rotationOf(info), shapeOf(info), colour);
	}

	/**
	 * One wall, as four pixels along whichever edge of the tile it stands on.
	 *
	 * Shape 0 is a straight wall, shape 3 a corner post - one pixel - and shape 2 an L, which is
	 * a wall along two edges at once and so draws twice, the second edge a quarter turn on.
	 */
	public static void drawWall(int[] pixels, int side, int px, int pz, int rotation, int shape,
			int colour) {
		if (shape == 0 || shape == 2) {
			edge(pixels, side, px, pz, rotation, colour);
		}
		if (shape == 3) {
			dot(pixels, side, rotation == 1 || rotation == 2 ? px + 3 : px,
				rotation == 2 || rotation == 3 ? pz + 3 : pz, colour);
		}
		if (shape == 2) {
			edge(pixels, side, px, pz, rotation + 1 & 0x3, colour);
		}
	}

	/**
	 * A wall across the corners of a tile, the one shape no straight edge fits.
	 *
	 * The client steps it a pixel at a time, and the two pairs of rotations run the two ways a
	 * diagonal can lie: north-west to south-east, or south-west to north-east.
	 */
	public static void diagonal(int[] pixels, int side, int px, int pz, int rotation, int colour) {
		for (int i = 0; i < 4; i++) {
			int at = rotation == 0 || rotation == 2 ? 3 - i : i;
			dot(pixels, side, px + at, pz + i, colour);
		}
	}

	/** The four pixels along one edge of a tile: 0 west, 1 north, 2 east, 3 south. */
	public static void edge(int[] pixels, int side, int px, int pz, int rotation, int colour) {
		for (int i = 0; i < 4; i++) {
			if (rotation == 0) {
				dot(pixels, side, px, pz + i, colour);
			} else if (rotation == 1) {
				dot(pixels, side, px + i, pz, colour);
			} else if (rotation == 2) {
				dot(pixels, side, px + 3, pz + i, colour);
			} else {
				dot(pixels, side, px + i, pz + 3, colour);
			}
		}
	}

	/** One pixel, or nothing at all when it falls outside the square being drawn. */
	static void dot(int[] pixels, int side, int px, int pz, int colour) {
		if (px >= 0 && px < side && pz >= 0 && pz < side) {
			pixels[pz * side + px] = colour;
		}
	}

	/**
	 * Where tile z starts in the picture.
	 *
	 * NORTH IS UP AND A PICTURE GROWS DOWN, so the tile with the highest z is drawn in the top
	 * row and tile 0 along the bottom. Get this backwards and the map is a mirror of itself,
	 * which is a thing you can stare straight at without seeing.
	 */
	public static int rowOf(int tz, int side) {
		return (SQUARE - 1 - tz) * SCALE * side;
	}
}
