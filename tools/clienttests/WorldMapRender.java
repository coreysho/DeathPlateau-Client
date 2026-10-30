/*
 * Renders the game's map squares to a picture, with the CLIENT'S OWN COLOURS.
 *
 * WHY THIS IS THE ONLY HONEST WAY TO DRAW ONE. This gamepack has no world map - 377 predates it -
 * so there is nothing to turn on, and a map drawn by reimplementing the tile colours somewhere
 * else would be a second renderer to keep in step with the first. What there IS, is the minimap:
 * the client already knows how to colour a tile, in World3D.method309, and createMinimap already
 * walks a loaded scene calling it. This drives exactly that code over map squares loaded from
 * the cache on disk instead of from a server.
 *
 * SO THE PIPELINE IS THE GAME'S, step for step:
 *
 *   FileStream over main_file_cache.dat/.idx4   - the same reader the client caches to
 *   GZIP                                        - as OnDemand inflates them
 *   World.method22                              - decode the land into heights and flags
 *   World.method15                              - build that terrain into a World3D
 *   World3D.method309                           - the colour of one tile, as the minimap draws it
 *
 * Nothing here chooses a colour. If a lake is the wrong blue, it is the wrong blue in game too.
 *
 * ONE SQUARE AT A TIME, WITH ITS NEIGHBOURS AROUND IT. A map square is 64 tiles and the client's
 * scene is 104, and method31 hard-codes that 104, so a scene cannot hold the world or even a
 * 3x3 block of squares. But terrain shading reads the tiles around the one it is lighting, so a
 * square loaded alone would come out with a seam of wrong light down every edge. The square
 * therefore goes in at offset 20 and its eight neighbours are loaded around it - the parts of
 * them that land outside the scene are skipped by method31 on the way past - and only the
 * middle 64x64 is kept.
 *
 * Driven by tools/clienttests/run_worldmap.py.
 */
package jagex2.client;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.RandomAccessFile;
import java.util.zip.GZIPInputStream;

import javax.imageio.ImageIO;

import jagex2.config.FloType;
import jagex2.config.LocType;
import jagex2.config.SeqType;
import jagex2.dash3d.CollisionMap;
import jagex2.dash3d.Model;
import jagex2.dash3d.World;
import jagex2.dash3d.World3D;
import jagex2.graphics.Pix2D;
import jagex2.graphics.Pix3D;
import jagex2.graphics.Pix8;
import jagex2.io.FileStream;
import jagex2.io.Jagfile;
import jagex2.io.OnDemandProvider;
import jagex2.io.Packet;

public class WorldMapRender {

	/** Pixels per tile, as the minimap draws it. */
	static final int SCALE = 4;

	/** Tiles along one side of a map square. */
	static final int SQUARE = 64;

	/** Tiles along one side of a scene, which method31 will not let us exceed. */
	static final int SCENE = 104;

	/**
	 * Where in the scene the square being drawn sits, leaving a margin of neighbours around it.
	 *
	 * Any of these renders the same picture; there is more than one only so that a square whose
	 * lighting divides by zero at one scene edge can be tried against another. The lighting
	 * window reaches five tiles, and a square is 64 of the scene's 104, so a margin has to be at
	 * least 5 and at most 35.
	 */
	static final int[] MARGINS = new int[] { 20, 28, 12, 34, 6 };

	/** The sprites the minimap draws in place of a building, by the loc's mapscene number. */
	static final Pix8[] mapscene = new Pix8[72];

	/**
	 * A wall the player cannot interact with: near-white.
	 *
	 * The client rolls these two a shade apart on every login, the same jitter it gives the sun,
	 * and then hard-codes exactly these values where the roll is out of scope - so these are
	 * both the middle of the range and the numbers the client itself writes down.
	 */
	static final int WALL = 0xEEEEEE;

	/** A door, gate or anything else that opens: dark red. */
	static final int DOOR = 0xEE0000;

	static int[] mapIndex;
	static int[] mapLand;
	static int[] mapLoc;
	static FileStream maps;

	public static void main(String[] args) throws Exception {
		if (args.length < 3) {
			throw new IllegalArgumentException(
				"usage: WorldMapRender <packDir> <out>"
					+ " <squareX,squareZ|x0,z0:x1,z1|all|tiles> [level] [shrink]");
		}
		File pack = new File(args[0]);
		File out = new File(args[1]);
		String which = args[2];
		int level = args.length > 3 ? Integer.parseInt(args[3]) : 0;
		int shrink = args.length > 4 ? Integer.parseInt(args[4]) : 1;

		loadConfig(pack);
		loadModels(pack);
		loadIndex(pack);
		maps = new FileStream(5, 600000,
			new RandomAccessFile(new File(pack, "main_file_cache.dat"), "r"),
			new RandomAccessFile(new File(pack, "main_file_cache.idx4"), "r"));

		if ("all".equals(which)) {
			renderAll(out, extent(), level, shrink);
		} else if ("tiles".equals(which)) {
			renderTiles(out, level);
		} else if (which.indexOf(':') >= 0) {
			renderAll(out, box(which), level, shrink);
		} else {
			String[] parts = which.split(",");
			int sx = Integer.parseInt(parts[0]);
			int sz = Integer.parseInt(parts[1]);
			BufferedImage img = renderSquare(sx, sz, level);
			if (img == null) {
				throw new IllegalStateException("no map data for square " + sx + "," + sz);
			}
			ImageIO.write(img, "png", out);
			System.out.println("square " + sx + "," + sz + " -> " + out + "  "
				+ img.getWidth() + "x" + img.getHeight());
		}
	}

	/**
	 * Everything a tile's colour comes out of, loaded as the client loads it.
	 *
	 * The textures are here because a textured floor - water, lava, a gravel path - is drawn on
	 * the minimap as that texture's average colour, so without them every one of those is black.
	 *
	 * The mapscene sprites are the furnace, the anvil, the altar, the tree: what the minimap
	 * draws in place of a building when the loc has one. The client tints all of them by a
	 * random shade on every login, the same jitter it gives the sun; this does not, so the map
	 * gets the middle of that range and gets it the same way twice.
	 */
	static void loadConfig(File pack) throws Exception {
		Pix3D.unpackTextures(jagfile(pack, "textures"));
		Pix3D.initColourTable(0.8D);
		Pix3D.initPool(20);
		Jagfile config = jagfile(pack, "config");
		FloType.unpack(config);
		SeqType.unpack(config);
		LocType.unpack(config);
		Jagfile media = jagfile(pack, "media");
		for (int i = 0; i < mapscene.length; i++) {
			mapscene[i] = new Pix8(media, "mapscene", i);
		}
		// The scene is never looked at from inside, so nothing should be hidden from the camera.
		World.lowMem = false;
	}

	/**
	 * Every model in the cache, all of them, before a single square is drawn.
	 *
	 * WHY ALL OF THEM AND WHY UP FRONT. A loc only reaches the scene if its model can be built -
	 * World.method21 drops a piece of scenery whose model comes back null - and in the game the
	 * models arrive from the server as the player walks towards them, which is what the whole
	 * on-demand machinery is for. There is no server here and no walking, so the only way to be
	 * sure a square is drawn with everything on it is to have every model in hand first. They
	 * are kept as the bytes they were cached as plus a table of offsets into them, so this is
	 * tens of megabytes, not hundreds, and it happens once for a run of any length.
	 */
	static void loadModels(File pack) throws Exception {
		byte[] versions = jagfile(pack, "versionlist").read("model_version", null);
		int count = versions.length / 2;
		Model.init(count, new OnDemandProvider());
		FileStream models = new FileStream(2, 600000,
			new RandomAccessFile(new File(pack, "main_file_cache.dat"), "r"),
			new RandomAccessFile(new File(pack, "main_file_cache.idx1"), "r"));
		int loaded = 0;
		for (int file = 0; file < count; file++) {
			byte[] data = inflate(models.read(file));
			if (data != null) {
				Model.method357(data, file, (byte) 7);
				loaded++;
			}
		}
		System.out.println("models: " + loaded + " of " + count);
	}

	static Jagfile jagfile(File pack, String name) throws Exception {
		File file = new File(new File(pack, "client"), name);
		byte[] data = new byte[(int) file.length()];
		RandomAccessFile in = new RandomAccessFile(file, "r");
		try {
			in.readFully(data);
		} finally {
			in.close();
		}
		return new Jagfile(data);
	}

	/** map_index, from the versionlist jagfile, exactly as OnDemand reads it. */
	static void loadIndex(File pack) throws Exception {
		byte[] data = jagfile(pack, "versionlist").read("map_index", null);
		Packet buf = new Packet(data);
		int count = data.length / 7;
		mapIndex = new int[count];
		mapLand = new int[count];
		mapLoc = new int[count];
		for (int i = 0; i < count; i++) {
			mapIndex[i] = buf.g2();
			mapLand[i] = buf.g2();
			mapLoc[i] = buf.g2();
			buf.g1();                 // members flag, which a picture does not care about
		}
		System.out.println("map_index: " + count + " squares");
	}

	static int fileFor(int x, int z, int type) {
		int index = (x << 8) + z;
		for (int i = 0; i < mapIndex.length; i++) {
			if (mapIndex[i] == index) {
				return type == 0 ? mapLand[i] : mapLoc[i];
			}
		}
		return -1;
	}

	/** One map file, inflated, or null when the cache has not got it. */
	static byte[] fetch(int file) throws Exception {
		return file < 0 ? null : inflate(maps.read(file));
	}

	/**
	 * A cached on-demand file, as OnDemand.cycle hands it on.
	 *
	 * EVERYTHING IN THE CACHE IS GZIPPED. The client caches what the server sent, compressed,
	 * and inflates it on the way out - every archive, maps and models alike. A model handed on
	 * still compressed does not fail: Model.method357 reads its header off the last eighteen
	 * bytes, which in a gzip stream are a plausible-looking set of counts, and the model comes
	 * out as offsets pointing a hundred kilobytes past the end of a two hundred byte file.
	 */
	static byte[] inflate(byte[] raw) throws Exception {
		if (raw == null || raw.length == 0) {
			return null;
		}
		GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(raw));
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] chunk = new byte[8192];
		int n;
		while ((n = in.read(chunk)) > 0) {
			out.write(chunk, 0, n);
		}
		in.close();
		return out.toByteArray();
	}

	/**
	 * One map square, as the minimap would draw it, or null when the cache has no land for it.
	 *
	 * RETRIED AT A DIFFERENT MARGIN, because method15 divides by the total weight of the floors
	 * in an eleven-by-eleven window and one square has a strip along a scene edge where that
	 * window is clipped short and every floor in what is left weighs nothing. Moving the square
	 * within the scene moves the clipped edge somewhere else. It cannot change the picture: the
	 * height noise is seeded from world coordinates, not scene ones, and every margin here
	 * leaves the square more than five tiles of loaded neighbour on all four sides, which is as
	 * far as the lighting window reaches.
	 */
	static BufferedImage renderSquare(int sx, int sz, int level) throws Exception {
		if (fetch(fileFor(sx, sz, 0)) == null) {
			return null;
		}
		ArithmeticException last = null;
		for (int i = 0; i < MARGINS.length; i++) {
			try {
				return renderSquare(sx, sz, level, MARGINS[i]);
			} catch (ArithmeticException degenerate) {
				last = degenerate;
			}
		}
		throw last;
	}

	static BufferedImage renderSquare(int sx, int sz, int level, int margin) throws Exception {

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
				byte[] land = fetch(fileFor(sx + dx, sz + dz, 0));
				int offX = margin + dx * SQUARE;
				int offZ = margin + dz * SQUARE;
				if (land == null) {
					world.method28(offX, offZ, SQUARE, SQUARE);
				} else {
					world.method22(offZ, baseZ, offX, land, baseX, collision);
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
				byte[] locs = fetch(fileFor(sx + dx, sz + dz, 1));
				if (locs != null) {
					world.method27(margin + dz * SQUARE, collision, margin + dx * SQUARE,
						scene, locs);
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

		int side = SQUARE * SCALE;
		int[] pixels = new int[side * side];
		for (int tz = 0; tz < SQUARE; tz++) {
			int row = rowOf(tz, side);
			for (int tx = 0; tx < SQUARE; tx++) {
				scene.method309(pixels, row + tx * SCALE, side, level, margin + tx, margin + tz);
			}
		}
		drawLocs(scene, pixels, side, level, margin);
		BufferedImage img = new BufferedImage(side, side, BufferedImage.TYPE_INT_RGB);
		img.setRGB(0, 0, side, side, pixels, 0, side);
		return img;
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
	static void drawLocs(World3D scene, int[] pixels, int side, int level, int margin) {
		Pix2D.bind(side, side, pixels);
		Pix2D.resetClipping();
		for (int x = 1; x < SCENE - 1; x++) {
			for (int z = 1; z < SCENE - 1; z++) {
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
		if (which < 0 || which >= mapscene.length || mapscene[which] == null) {
			return;
		}
		Pix8 sprite = mapscene[which];
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
	static int shapeOf(int info) {
		return info & 0x1F;
	}

	/** Which quarter turn it stands at. */
	static int rotationOf(int info) {
		return info >> 6 & 0x3;
	}

	/** One wall from the byte the scene stored it under. */
	static void wallFrom(int[] pixels, int side, int px, int pz, int info, int colour) {
		drawWall(pixels, side, px, pz, rotationOf(info), shapeOf(info), colour);
	}

	/**
	 * One wall, as four pixels along whichever edge of the tile it stands on.
	 *
	 * Shape 0 is a straight wall, shape 3 a corner post - one pixel - and shape 2 an L, which is
	 * a wall along two edges at once and so draws twice, the second edge a quarter turn on.
	 */
	static void drawWall(int[] pixels, int side, int px, int pz, int rotation, int shape,
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
	static void diagonal(int[] pixels, int side, int px, int pz, int rotation, int colour) {
		for (int i = 0; i < 4; i++) {
			int at = rotation == 0 || rotation == 2 ? 3 - i : i;
			dot(pixels, side, px + at, pz + i, colour);
		}
	}

	/** The four pixels along one edge of a tile: 0 west, 1 north, 2 east, 3 south. */
	static void edge(int[] pixels, int side, int px, int pz, int rotation, int colour) {
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
	static int rowOf(int tz, int side) {
		return (SQUARE - 1 - tz) * SCALE * side;
	}

	/** Where square x starts across a picture whose leftmost square is minX. */
	static int acrossOf(int x, int minX, int cell) {
		return (x - minX) * cell;
	}

	/** Where square z starts down a picture whose topmost - so highest - square is maxZ. */
	static int downOf(int z, int maxZ, int cell) {
		return (maxZ - z) * cell;
	}

	/**
	 * Every square the index knows, laid out in one picture.
	 *
	 * SHRINK IS NOT A CHOICE ABOUT QUALITY. method309 writes a tile as four pixels by four and
	 * takes no say in it, so the only scale this can render at is 4, and the whole world at 4 is
	 * eleven thousand pixels by seventeen - a third of a gigabyte of ints, and nothing will open
	 * it. Shrinking afterwards by averaging each block keeps the colours honest; dropping pixels
	 * instead would make a one-tile path flicker in and out along its length.
	 */
	static void renderAll(File out, int[] box, int level, int shrink) throws Exception {
		ImageIO.write(compose(box, level, shrink), "png", out);
		System.out.println("-> " + out);
	}

	static BufferedImage compose(int[] box, int level, int shrink) throws Exception {
		int minX = box[0];
		int maxX = box[1];
		int minZ = box[2];
		int maxZ = box[3];
		int cell = SQUARE * SCALE / shrink;
		int wide = (maxX - minX + 1) * cell;
		int high = (maxZ - minZ + 1) * cell;
		System.out.println("squares x " + minX + ".." + maxX + ", z " + minZ + ".." + maxZ
			+ " -> " + wide + "x" + high + " at 1/" + shrink);
		BufferedImage img = new BufferedImage(wide, high, BufferedImage.TYPE_INT_RGB);
		int drawn = 0;
		for (int i = 0; i < mapIndex.length; i++) {
			int x = mapIndex[i] >> 8;
			int z = mapIndex[i] & 0xFF;
			if (x < minX || x > maxX || z < minZ || z > maxZ) {
				continue;
			}
			BufferedImage one = safeSquare(x, z, level);
			if (one == null) {
				continue;
			}
			paste(img, shrink(one, shrink), acrossOf(x, minX, cell), downOf(z, maxZ, cell));
			drawn++;
		}
		System.out.println(drawn + " of " + mapIndex.length + " squares drawn");
		return img;
	}

	/** Every square as its own file, named for the square, which is what a pannable map reads. */
	static void renderTiles(File dir, int level) throws Exception {
		if (!dir.isDirectory() && !dir.mkdirs()) {
			throw new IllegalStateException("cannot make " + dir);
		}
		int drawn = 0;
		for (int i = 0; i < mapIndex.length; i++) {
			int x = mapIndex[i] >> 8;
			int z = mapIndex[i] & 0xFF;
			BufferedImage one = safeSquare(x, z, level);
			if (one == null) {
				continue;
			}
			ImageIO.write(one, "png", new File(dir, x + "_" + z + "_" + level + ".png"));
			drawn++;
		}
		System.out.println(drawn + " of " + mapIndex.length + " squares -> " + dir);
	}

	/**
	 * One square, with a broken one reported and skipped.
	 *
	 * A SWEEP OF SEVEN HUNDRED SQUARES MUST NOT STOP AT THE FIRST BAD ONE, because the one that
	 * throws is the one worth knowing about and a stack trace instead of a map tells you only
	 * that something, somewhere, is wrong.
	 */
	static BufferedImage safeSquare(int x, int z, int level) {
		try {
			return renderSquare(x, z, level);
		} catch (Throwable broken) {
			System.out.println("  square " + x + "," + z + " failed: " + broken);
			return null;
		}
	}

	/** "x0,z0:x1,z1" as minX, maxX, minZ, maxZ, whichever way round it was written. */
	static int[] box(String region) {
		String[] ends = region.split(":");
		String[] from = ends[0].split(",");
		String[] to = ends[1].split(",");
		int x0 = Integer.parseInt(from[0]);
		int z0 = Integer.parseInt(from[1]);
		int x1 = Integer.parseInt(to[0]);
		int z1 = Integer.parseInt(to[1]);
		return new int[] { Math.min(x0, x1), Math.max(x0, x1), Math.min(z0, z1), Math.max(z0, z1) };
	}

	/** The square coordinates the index spans, as minX, maxX, minZ, maxZ. */
	static int[] extent() {
		int minX = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int minZ = Integer.MAX_VALUE;
		int maxZ = Integer.MIN_VALUE;
		for (int i = 0; i < mapIndex.length; i++) {
			int x = mapIndex[i] >> 8;
			int z = mapIndex[i] & 0xFF;
			minX = Math.min(minX, x);
			maxX = Math.max(maxX, x);
			minZ = Math.min(minZ, z);
			maxZ = Math.max(maxZ, z);
		}
		return new int[] { minX, maxX, minZ, maxZ };
	}

	/** An image reduced by averaging each block, so a one-pixel path survives as a faint one. */
	static BufferedImage shrink(BufferedImage src, int by) {
		if (by <= 1) {
			return src;
		}
		int wide = src.getWidth() / by;
		int high = src.getHeight() / by;
		BufferedImage out = new BufferedImage(wide, high, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < high; y++) {
			for (int x = 0; x < wide; x++) {
				int r = 0;
				int g = 0;
				int b = 0;
				for (int dy = 0; dy < by; dy++) {
					for (int dx = 0; dx < by; dx++) {
						int rgb = src.getRGB(x * by + dx, y * by + dy);
						r += rgb >> 16 & 0xFF;
						g += rgb >> 8 & 0xFF;
						b += rgb & 0xFF;
					}
				}
				int n = by * by;
				out.setRGB(x, y, (r / n << 16) + (g / n << 8) + b / n);
			}
		}
		return out;
	}

	static void paste(BufferedImage into, BufferedImage one, int atX, int atZ) {
		for (int y = 0; y < one.getHeight(); y++) {
			for (int x = 0; x < one.getWidth(); x++) {
				into.setRGB(atX + x, atZ + y, one.getRGB(x, y));
			}
		}
	}
}
