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
import jagex2.dash3d.Model;
import jagex2.client.worldmap.MapSquare;
import jagex2.graphics.Pix3D;
import jagex2.graphics.Pix8;
import jagex2.io.FileStream;
import jagex2.io.Jagfile;
import jagex2.io.OnDemandProvider;
import jagex2.io.Packet;

public class WorldMapRender {

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
			renderTiles(out);
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
		Pix8[] scenes = new Pix8[72];
		for (int i = 0; i < scenes.length; i++) {
			scenes[i] = new Pix8(media, "mapscene", i);
		}
		MapSquare.useScenes(scenes);
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
	 * One map square, gathered out of the cache and handed to the client's own renderer.
	 *
	 * The nine squares are this one and its eight neighbours: MapSquare lights a square by the
	 * tiles around it, so a square fetched alone comes out with a seam down every edge.
	 */
	static BufferedImage renderSquare(int sx, int sz, int level) throws Exception {
		return wrap(MapSquare.draw(gather(sx, sz, 0), gather(sx, sz, 1), sx, sz, level));
	}

	static BufferedImage renderSquare(int sx, int sz, int level, int margin) throws Exception {
		return wrap(MapSquare.draw(gather(sx, sz, 0), gather(sx, sz, 1), sx, sz, level, margin));
	}

	/** This square and its eight neighbours, as [dx + 1][dz + 1], with nulls for what is missing. */
	static byte[][][] gather(int sx, int sz, int type) throws Exception {
		byte[][][] nine = new byte[3][3][];
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				nine[dx + 1][dz + 1] = fetch(fileFor(sx + dx, sz + dz, type));
			}
		}
		return nine;
	}

	static BufferedImage wrap(int[] pixels) {
		if (pixels == null) {
			return null;
		}
		int side = MapSquare.SIDE;
		BufferedImage img = new BufferedImage(side, side, BufferedImage.TYPE_INT_RGB);
		img.setRGB(0, 0, side, side, pixels, 0, side);
		return img;
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
		int cell = MapSquare.SIDE / shrink;
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

	/**
	 * Every square as its own file, named for the square and the level, which is what a
	 * pannable map reads.
	 *
	 * ALL FOUR LEVELS, AND THE EMPTY ONES LEFT OUT. Above the ground, most of the world is
	 * nothing at all - two of the four squares around Lumbridge have no second floor anywhere
	 * on them - and a file of 65536 black pixels for each of those is a file a map would have
	 * to fetch to learn there was nothing to show.
	 */
	static void renderTiles(File dir) throws Exception {
		if (!dir.isDirectory() && !dir.mkdirs()) {
			throw new IllegalStateException("cannot make " + dir);
		}
		for (int level = 0; level < 4; level++) {
			int drawn = 0;
			int empty = 0;
			for (int i = 0; i < mapIndex.length; i++) {
				int x = mapIndex[i] >> 8;
				int z = mapIndex[i] & 0xFF;
				BufferedImage one = safeSquare(x, z, level);
				if (one == null) {
					continue;
				}
				if (isEmpty(one)) {
					empty++;
					continue;
				}
				ImageIO.write(one, "png", new File(dir, x + "_" + z + "_" + level + ".png"));
				drawn++;
			}
			System.out.println("level " + level + ": " + drawn + " squares, " + empty
				+ " with nothing on them");
		}
		System.out.println("-> " + dir);
	}

	/** Whether a square has anything on it at all, or is the black a level above the roofs is. */
	static boolean isEmpty(BufferedImage img) {
		int side = img.getWidth();
		int[] pixels = new int[side * img.getHeight()];
		img.getRGB(0, 0, side, img.getHeight(), pixels, 0, side);
		return MapSquare.isEmpty(pixels);
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
