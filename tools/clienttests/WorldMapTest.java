/*
 * Checks the arithmetic the world map is laid out with.
 *
 * WHY THESE AND NOT THE COLOURS. The colours are the client's own - WorldMapRender drives
 * World3D.method309, so a wrong colour here is a wrong colour in the minimap too and no test of
 * mine should have an opinion about it. What IS mine is where each tile and each square is put,
 * and every way of getting that wrong produces a map that looks entirely convincing: a world
 * flipped north for south, a world transposed because x and z were packed the wrong way round,
 * squares a row out so every coastline meets grass.
 *
 * Nothing you can see catches those. A test can.
 *
 * THE CHECKS THAT NEED THE CACHE are the ones that prove the renderer is driving the real thing:
 * that two scene placements of the same square agree pixel for pixel, that a square is lit the
 * same way twice, and that the neighbours loaded around it actually reach the picture. Those
 * need data/pack, which the engine builds and git does not carry, so the runner finds it and
 * says plainly when it cannot.
 *
 * Run by tools/clienttests/run_worldmaptest.py.
 */
package jagex2.client;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.RandomAccessFile;

import jagex2.io.FileStream;

public class WorldMapTest {

	static int checks = 0;
	static int failures = 0;

	public static void main(String[] args) throws Exception {
		layout();
		regions();
		shrinking();
		File pack = packDir();
		if (pack == null) {
			System.out.println("FAIL no cache: pass -Ddp.pack=<engine>/data/pack");
			failures++;
		} else {
			render(pack);
		}
		System.out.println();
		System.out.println(checks + " checks, " + failures + " failures");
		if (failures > 0) {
			System.exit(1);
		}
	}

	static File packDir() {
		String given = System.getProperty("dp.pack");
		if (given == null) {
			return null;
		}
		File dir = new File(given);
		return new File(dir, "main_file_cache.idx4").exists() ? dir : null;
	}

	// ----------------------------------------------------------------- layout

	static void layout() {
		System.out.println("-- layout --");

		// A SQUARE IS 64 TILES AND A TILE IS 4 PIXELS, both pinned here rather than read off the
		// renderer, because a test that asks the code what the number is agrees with any answer.
		eq("a map square is 64 tiles", 64, WorldMapRender.SQUARE);
		eq("the minimap draws a tile 4 pixels wide", 4, WorldMapRender.SCALE);
		eq("a square is 256 pixels", 256, WorldMapRender.SQUARE * WorldMapRender.SCALE);

		// NORTH UP. Tile 0 is the southernmost, so it is drawn along the BOTTOM of the square.
		int side = 256;
		eq("tile z=0 starts the last four rows", 252 * side, WorldMapRender.rowOf(0, side));
		eq("tile z=63 is the top row", 0, WorldMapRender.rowOf(63, side));
		yes("going north moves up the picture",
			WorldMapRender.rowOf(30, side) > WorldMapRender.rowOf(31, side));
		eq("one tile north is four rows up",
			4 * side, WorldMapRender.rowOf(30, side) - WorldMapRender.rowOf(31, side));

		// WEST LEFT, and the leftmost square sits flush against the edge.
		eq("the leftmost square starts at 0", 0, WorldMapRender.acrossOf(27, 27, 256));
		eq("the next square east is one cell over", 256, WorldMapRender.acrossOf(28, 27, 256));
		eq("the topmost square starts at 0", 0, WorldMapRender.downOf(75, 75, 256));
		eq("the next square south is one cell down", 256, WorldMapRender.downOf(74, 75, 256));

		// THE INDEX PACKS X ABOVE Z, and the two are not interchangeable. A swap transposes the
		// world, which still looks like a map.
		WorldMapRender.mapIndex = new int[] { (3 << 8) + 5, (5 << 8) + 3 };
		WorldMapRender.mapLand = new int[] { 111, 222 };
		WorldMapRender.mapLoc = new int[] { 333, 444 };
		eq("square 3,5 finds its own land", 111, WorldMapRender.fileFor(3, 5, 0));
		eq("square 5,3 finds its own land", 222, WorldMapRender.fileFor(5, 3, 0));
		eq("square 3,5 finds its own locs", 333, WorldMapRender.fileFor(3, 5, 1));
		eq("square 5,3 finds its own locs", 444, WorldMapRender.fileFor(5, 3, 1));
		eq("a square the index has not got is -1", -1, WorldMapRender.fileFor(9, 9, 0));

		// THE INDEX IS WHAT THE PICTURE IS SIZED FROM, so it has to be read back the same way.
		WorldMapRender.mapIndex = new int[] { (27 << 8) + 40, (59 << 8) + 161, (40 << 8) + 60 };
		int[] box = WorldMapRender.extent();
		eq("west edge", 27, box[0]);
		eq("east edge", 59, box[1]);
		eq("south edge", 40, box[2]);
		eq("north edge", 161, box[3]);
	}

	static void regions() {
		System.out.println("-- regions --");
		int[] box = WorldMapRender.box("27,40:59,75");
		eq("region west", 27, box[0]);
		eq("region east", 59, box[1]);
		eq("region south", 40, box[2]);
		eq("region north", 75, box[3]);

		// WRITTEN THE OTHER WAY ROUND it is the same rectangle, because nobody remembers which
		// corner goes first and a region silently inside out renders nothing at all.
		int[] flipped = WorldMapRender.box("59,75:27,40");
		eq("flipped region west", 27, flipped[0]);
		eq("flipped region east", 59, flipped[1]);
		eq("flipped region south", 40, flipped[2]);
		eq("flipped region north", 75, flipped[3]);
	}

	static void shrinking() {
		System.out.println("-- shrinking --");
		BufferedImage src = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
		src.setRGB(0, 0, 0xFFFFFF);
		src.setRGB(1, 0, 0x000000);
		src.setRGB(0, 1, 0x000000);
		src.setRGB(1, 1, 0x000000);

		// AVERAGED, NOT SAMPLED. One white pixel in four is a quarter-grey; a renderer that took
		// the top-left instead would make every path on the map either solid or invisible
		// depending on which pixel it happened to land on.
		eq("a quarter white averages to a quarter grey", 0x3F3F3F,
			WorldMapRender.shrink(src, 2).getRGB(0, 0) & 0xFFFFFF);
		eq("shrinking by 2 halves the side", 1, WorldMapRender.shrink(src, 2).getWidth());
		yes("shrinking by 1 is the picture itself", WorldMapRender.shrink(src, 1) == src);
	}

	// ----------------------------------------------------------------- the real thing

	static void render(File pack) throws Exception {
		System.out.println("-- rendering --");
		WorldMapRender.loadConfig(pack);
		WorldMapRender.loadIndex(pack);
		WorldMapRender.maps = new FileStream(5, 600000,
			new RandomAccessFile(new File(pack, "main_file_cache.dat"), "r"),
			new RandomAccessFile(new File(pack, "main_file_cache.idx4"), "r"));

		int sx = 50;
		int sz = 50;
		BufferedImage first = WorldMapRender.renderSquare(sx, sz, 0, 20);
		yes("a square in the index renders", first != null);
		eq("a square is 256 pixels wide", 256, first.getWidth());
		eq("a square is 256 pixels tall", 256, first.getHeight());
		yes("a square is not one flat colour", colours(first) > 20);

		// THE SAME SUN TWICE. method15 walks the light a step on every call, so without the pin
		// the map would drift from one corner to the other over seven hundred squares.
		BufferedImage again = WorldMapRender.renderSquare(sx, sz, 0, 20);
		eq("the same square renders the same twice", 0, differences(first, again));

		// THE SCENE PLACEMENT IS NOT PART OF THE PICTURE. The square moves within the 104-tile
		// scene only so a square whose lighting divides by zero at one edge can be retried; if
		// moving it changed the colours, that retry would quietly produce a different map.
		eq("margin 28 draws the same square as margin 20",
			0, differences(first, WorldMapRender.renderSquare(sx, sz, 0, 28)));
		eq("margin 6 draws the same square as margin 20",
			0, differences(first, WorldMapRender.renderSquare(sx, sz, 0, 6)));

		// THE NEIGHBOURS ARE LOADED FOR A REASON. Terrain shading reads five tiles past the edge
		// of the square, so a square rendered alone comes out with a band of wrong light all the
		// way round. This is the check that the eight neighbours are really going in: without
		// them the picture changes, and only near the edges.
		BufferedImage alone = lonely(sx, sz);
		yes("without its neighbours a square is lit differently", differences(first, alone) > 0);
		eq("and only near its edges, which is how far the lighting reaches",
			0, differencesInside(first, alone, 6 * WorldMapRender.SCALE));

		// NORTH UP, ON REAL GROUND. The ground does not stop at a square boundary: the bottom
		// row of this square and the top row of the square to the south are neighbouring tiles
		// in the world and look like each other, while its bottom row is 64 tiles away and does
		// not. Flip the picture and that stops being true - which is the only way to catch a
		// mirrored map, since a mirrored map is still a perfectly convincing map.
		BufferedImage south = WorldMapRender.renderSquare(sx, sz - 1, 0, 20);
		int seam = bandGap(first, 255, south, 0);
		int across = bandGap(first, 255, south, 255);
		yes("the bottom of a square joins the top of the one below it (" + seam
			+ " against " + across + ")", seam * 2 < across);

		// AND THE WHOLE WAY THROUGH. Everything above tests a piece; this tests the picture
		// that comes out, which is the one thing a transposed or upside-down map cannot survive.
		// Two squares, one above the other, and each has to land where it belongs.
		int[] stack = new int[] { sx, sx, sz, sz + 1 };
		BufferedImage pair = composed("two squares compose", stack, 1, 256, 512);
		eq("two squares stacked are one square wide", 256, pair.getWidth());
		eq("and two squares tall", 512, pair.getHeight());
		BufferedImage north = WorldMapRender.renderSquare(sx, sz + 1, 0, 20);
		eq("the northern square is the top half", 0, differencesAt(pair, 0, 0, north));
		eq("the southern square is the bottom half", 0, differencesAt(pair, 0, 256, first));

		// SHRINKING IS APPLIED TO THE LAYOUT TOO, not just to the squares, or every square after
		// the first is pasted a cell further off than the one before it.
		BufferedImage half = composed("two squares compose shrunk", stack, 2, 128, 256);
		eq("shrunk by two it is half as wide", 128, half.getWidth());
		eq("and half as tall", 256, half.getHeight());
		eq("and the northern square is still the top half",
			0, differencesAt(half, 0, 0, WorldMapRender.shrink(north, 2)));

		// A SQUARE THE INDEX HAS NOT GOT IS NOT A BLACK SQUARE, it is nothing at all, so that
		// renderAll leaves the sea black rather than painting a hole over its neighbour.
		yes("a square outside the index renders nothing",
			WorldMapRender.renderSquare(200, 200, 0) == null);
	}

	/** The square with no neighbours around it, which is what the margin is there to avoid. */
	static BufferedImage lonely(int sx, int sz) throws Exception {
		int[] saved = WorldMapRender.mapIndex;
		int[] land = WorldMapRender.mapLand;
		int[] locs = WorldMapRender.mapLoc;
		int keep = -1;
		for (int i = 0; i < saved.length; i++) {
			if (saved[i] == (sx << 8) + sz) {
				keep = i;
			}
		}
		WorldMapRender.mapIndex = new int[] { saved[keep] };
		WorldMapRender.mapLand = new int[] { land[keep] };
		WorldMapRender.mapLoc = new int[] { locs[keep] };
		try {
			return WorldMapRender.renderSquare(sx, sz, 0, 20);
		} finally {
			WorldMapRender.mapIndex = saved;
			WorldMapRender.mapLand = land;
			WorldMapRender.mapLoc = locs;
		}
	}

	/**
	 * A composed picture, with a composer that threw reported as a failure of its own.
	 *
	 * A CRASH IS NOT A CATCH: a run that dies here says only that something went wrong, and the
	 * checks after it never run at all. Naming it, and handing back a blank of the size that
	 * was expected, leaves every later check to fail by name as well.
	 */
	static BufferedImage composed(String what, int[] box, int shrink, int wide, int high) {
		try {
			BufferedImage img = WorldMapRender.compose(box, 0, shrink);
			yes(what, img != null);
			return img;
		} catch (Throwable broken) {
			checks++;
			failures++;
			System.out.println("FAIL " + what + ": " + broken);
			return new BufferedImage(wide, high, BufferedImage.TYPE_INT_RGB);
		}
	}

	/** Pixels of `one` that differ from what `into` holds at that corner. */
	static int differencesAt(BufferedImage into, int atX, int atY, BufferedImage one) {
		int n = 0;
		for (int y = 0; y < one.getHeight(); y++) {
			for (int x = 0; x < one.getWidth(); x++) {
				if (into.getRGB(atX + x, atY + y) != one.getRGB(x, y)) {
					n++;
				}
			}
		}
		return n;
	}

	/** How far apart two pixel rows are, summed over all three channels. */
	static int bandGap(BufferedImage a, int rowA, BufferedImage b, int rowB) {
		int gap = 0;
		for (int x = 0; x < a.getWidth(); x++) {
			int p = a.getRGB(x, rowA);
			int q = b.getRGB(x, rowB);
			gap += Math.abs((p >> 16 & 0xFF) - (q >> 16 & 0xFF));
			gap += Math.abs((p >> 8 & 0xFF) - (q >> 8 & 0xFF));
			gap += Math.abs((p & 0xFF) - (q & 0xFF));
		}
		return gap;
	}

	static int colours(BufferedImage img) {
		java.util.HashSet<Integer> seen = new java.util.HashSet<Integer>();
		for (int y = 0; y < img.getHeight(); y++) {
			for (int x = 0; x < img.getWidth(); x++) {
				seen.add(Integer.valueOf(img.getRGB(x, y)));
			}
		}
		return seen.size();
	}

	static int differences(BufferedImage a, BufferedImage b) {
		return differencesInside(a, b, 0);
	}

	/** Pixels that differ, ignoring a border of `inset` pixels all the way round. */
	static int differencesInside(BufferedImage a, BufferedImage b, int inset) {
		int n = 0;
		for (int y = inset; y < a.getHeight() - inset; y++) {
			for (int x = inset; x < a.getWidth() - inset; x++) {
				if (a.getRGB(x, y) != b.getRGB(x, y)) {
					n++;
				}
			}
		}
		return n;
	}

	// ----------------------------------------------------------------- plumbing

	static void eq(String what, int want, int got) {
		checks++;
		if (want == got) {
			System.out.println("ok   " + what + " = " + got);
		} else {
			failures++;
			System.out.println("FAIL " + what + ": wanted " + want + ", got " + got);
		}
	}

	static void yes(String what, boolean got) {
		checks++;
		if (got) {
			System.out.println("ok   " + what);
		} else {
			failures++;
			System.out.println("FAIL " + what);
		}
	}
}
