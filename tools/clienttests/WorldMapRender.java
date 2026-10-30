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
import jagex2.dash3d.CollisionMap;
import jagex2.dash3d.World;
import jagex2.dash3d.World3D;
import jagex2.graphics.Pix3D;
import jagex2.io.FileStream;
import jagex2.io.Jagfile;
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
	 * The two tables a tile colour comes out of, loaded as the client loads them.
	 *
	 * The textures are here because a textured floor - water, lava, a gravel path - is drawn on
	 * the minimap as that texture's average colour, so without them every one of those is black.
	 */
	static void loadConfig(File pack) throws Exception {
		Pix3D.unpackTextures(jagfile(pack, "textures"));
		Pix3D.initColourTable(0.8D);
		Pix3D.initPool(20);
		FloType.unpack(jagfile(pack, "config"));
		// The scene is never looked at from inside, so nothing should be hidden from the camera.
		World.lowMem = false;
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

	/** One on-demand file, inflated, or null when the cache has not got it. */
	static byte[] fetch(int file) throws Exception {
		if (file < 0) {
			return null;
		}
		byte[] raw = maps.read(file);
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
		BufferedImage img = new BufferedImage(side, side, BufferedImage.TYPE_INT_RGB);
		img.setRGB(0, 0, side, side, pixels, 0, side);
		return img;
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
