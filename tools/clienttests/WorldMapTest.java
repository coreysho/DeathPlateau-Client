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

import jagex2.client.worldmap.MapSquare;
import jagex2.io.FileStream;

public class WorldMapTest {

	static int checks = 0;
	static int failures = 0;

	public static void main(String[] args) throws Exception {
		File pack = packDir();
		if (pack == null) {
			System.out.println("FAIL no cache: pass -Ddp.pack=<engine>/data/pack");
			failures++;
		} else if (System.getProperty("dp.coldstart") != null) {
			coldStart(pack);
		} else {
			layout();
			regions();
			shrinking();
			walls();
			levels();
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
		eq("a map square is 64 tiles", 64, MapSquare.SQUARE);
		eq("the minimap draws a tile 4 pixels wide", 4, MapSquare.SCALE);
		eq("a square is 256 pixels", 256, MapSquare.SQUARE * MapSquare.SCALE);

		// NORTH UP. Tile 0 is the southernmost, so it is drawn along the BOTTOM of the square.
		int side = 256;
		eq("tile z=0 starts the last four rows", 252 * side, MapSquare.rowOf(0, side));
		eq("tile z=63 is the top row", 0, MapSquare.rowOf(63, side));
		yes("going north moves up the picture",
			MapSquare.rowOf(30, side) > MapSquare.rowOf(31, side));
		eq("one tile north is four rows up",
			4 * side, MapSquare.rowOf(30, side) - MapSquare.rowOf(31, side));

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

	// ----------------------------------------------------------------- walls

	/**
	 * Which pixels of a tile a wall lights up, shape by shape and rotation by rotation.
	 *
	 * THE WHOLE OF A BUILDING'S SHAPE IS IN THESE SIXTEEN PIXELS. A rotation off by one turns
	 * every room in the game inside out - the walls are all still there, all still the right
	 * length, just on the wrong side of the tile they belong to - and the result is a map of
	 * buildings that are each one tile adrift from their own doors. There is no looking at that
	 * and seeing it.
	 */
	static void walls() {
		System.out.println("-- walls --");
		eq("a straight wall runs down the west edge", "0,0 0,1 0,2 0,3", wall(0, 0));
		eq("turned once, along the north edge", "0,0 1,0 2,0 3,0", wall(0, 1));
		eq("turned twice, down the east edge", "3,0 3,1 3,2 3,3", wall(0, 2));
		eq("turned three times, along the south edge", "0,3 1,3 2,3 3,3", wall(0, 3));

		eq("a corner post is one pixel, north-west", "0,0", wall(3, 0));
		eq("turned once, north-east", "3,0", wall(3, 1));
		eq("turned twice, south-east", "3,3", wall(3, 2));
		eq("turned three times, south-west", "0,3", wall(3, 3));

		// AN L IS TWO WALLS, the second a quarter turn on from the first, and the pixel they
		// share is counted once. Seven, not eight, and not four.
		eq("an L corner is west and north", "0,0 0,1 0,2 0,3 1,0 2,0 3,0", wall(2, 0));
		eq("turned once, north and east", "0,0 1,0 2,0 3,0 3,1 3,2 3,3", wall(2, 1));
		eq("turned twice, east and south", "0,3 1,3 2,3 3,0 3,1 3,2 3,3", wall(2, 2));
		eq("turned three times, south and west", "0,0 0,1 0,2 0,3 1,3 2,3 3,3", wall(2, 3));

		// SHAPE AND ROTATION SHARE A BYTE, and both are small numbers, so a reader that takes
		// one for the other still draws walls - of the right length, in the right colour, on
		// the wrong edges. These two values are chosen so that the mix-up cannot hide: as
		// written they are a single corner pixel, and read backwards they are an L of seven.
		eq("rotation 2 shape 3 is one pixel at the south-east corner", "3,3", packed((2 << 6) | 3));
		eq("rotation 3 shape 2 is an L along the south and the west",
			"0,0 0,1 0,2 0,3 1,3 2,3 3,3", packed((3 << 6) | 2));
		eq("the shape is the low five bits", 9, MapSquare.shapeOf((2 << 6) | 9));
		eq("the rotation is the two above them", 2, MapSquare.rotationOf((2 << 6) | 9));

		eq("a diagonal runs north-east to south-west", "0,3 1,2 2,1 3,0", diagonal(0));
		eq("turned once, north-west to south-east", "0,0 1,1 2,2 3,3", diagonal(1));
		eq("turned twice, back the first way", "0,3 1,2 2,1 3,0", diagonal(2));
		eq("turned three times, back the other", "0,0 1,1 2,2 3,3", diagonal(3));

		// A WALL AT THE EDGE OF THE SQUARE belongs to the square next door, whose locs are in
		// the scene so that a building across the boundary is drawn on both sides of it. Its
		// pixels have to fall off this picture rather than wrap round onto the far side.
		int[] pixels = new int[8 * 8];
		MapSquare.edge(pixels, 8, -4, 0, 0, 0xFFFFFF);
		eq("a wall a tile off the west edge lands nowhere", "", litIn(pixels, 8));
		MapSquare.edge(pixels, 8, 6, 0, 2, 0xFFFFFF);
		eq("and one off the east edge does not wrap to the west", "", litIn(pixels, 8));
		MapSquare.edge(pixels, 8, 0, -4, 1, 0xFFFFFF);
		eq("nor one off the south edge", "", litIn(pixels, 8));
	}

	static String wall(int shape, int rotation) {
		int[] pixels = new int[8 * 8];
		MapSquare.drawWall(pixels, 8, 0, 0, rotation, shape, 0xFFFFFF);
		return litIn(pixels, 8);
	}

	static String packed(int info) {
		int[] pixels = new int[8 * 8];
		MapSquare.wallFrom(pixels, 8, 0, 0, info, 0xFFFFFF);
		return litIn(pixels, 8);
	}

	static String diagonal(int rotation) {
		int[] pixels = new int[8 * 8];
		MapSquare.diagonal(pixels, 8, 0, 0, rotation, 0xFFFFFF);
		return litIn(pixels, 8);
	}

	/** Every pixel that was written, as "x,y" in reading order, so a miss reads as a miss. */
	static String litIn(int[] pixels, int side) {
		StringBuilder out = new StringBuilder();
		for (int x = 0; x < side; x++) {
			for (int y = 0; y < side; y++) {
				if (pixels[y * side + x] != 0) {
					out.append(out.length() > 0 ? " " : "").append(x).append(',').append(y);
				}
			}
		}
		return out.toString();
	}

	// ----------------------------------------------------------------- levels

	/**
	 * Which level's tile is drawn where, which is not simply "this one".
	 *
	 * A BRIDGE IS BUILT ON THE LEVEL ABOVE THE WATER IT CROSSES and marked as belonging down
	 * below, so the ground map leaves out its own tile there and takes the one above instead.
	 * Read only this level and every bridge in the game is a black gap in the ground, which
	 * reads as a river nothing crosses - and the upper floors come out with a phantom span
	 * over every river, drawn at a height nobody stands at.
	 */
	static void levels() {
		System.out.println("-- levels --");
		byte[][][] flags = new byte[4][4][4];

		yes("an ordinary tile is drawn on its own level", MapSquare.onThisMap(flags, 0, 1, 1));
		flags[0][1][1] = 0x8;
		yes("one marked as belonging below is not",
			!MapSquare.onThisMap(flags, 0, 1, 1));
		flags[0][1][1] = 0x10;
		yes("nor one the client keeps off the minimap",
			!MapSquare.onThisMap(flags, 0, 1, 1));
		flags[0][1][1] = 0x1 | 0x2 | 0x4;
		yes("blocked, bridge and roof say nothing about it either way",
			MapSquare.onThisMap(flags, 0, 1, 1));

		byte[][][] span = new byte[4][4][4];
		yes("nothing comes down from above by default",
			!MapSquare.fromAbove(span, 0, 1, 1));
		span[1][1][1] = 0x8;
		yes("a tile above marked as belonging below is drawn down here",
			MapSquare.fromAbove(span, 0, 1, 1));
		yes("and only onto the level under it, not two down",
			!MapSquare.fromAbove(span, 1, 1, 1));
		span[3][1][1] = 0x8;
		yes("there is nothing above level 3 to come down from",
			!MapSquare.fromAbove(span, 3, 1, 1));
		span[1][1][1] = 0x10;
		yes("and off-minimap is not the same bit as belongs-below",
			!MapSquare.fromAbove(span, 0, 1, 1));
	}

	// ----------------------------------------------------------------- the real thing

	static void render(File pack) throws Exception {
		System.out.println("-- rendering --");
		WorldMapRender.loadConfig(pack);
		WorldMapRender.loadModels(pack);
		WorldMapRender.loadIndex(pack);
		WorldMapRender.maps = new FileStream(5, 600000,
			new RandomAccessFile(new File(pack, "main_file_cache.dat"), "r"),
			new RandomAccessFile(new File(pack, "main_file_cache.idx4"), "r"));

		int sx = 50;
		int sz = 50;
		BufferedImage first = rendered("a square in the index renders", sx, sz, 0);
		eq("a square is 256 pixels wide", 256, first.getWidth());
		eq("a square is 256 pixels tall", 256, first.getHeight());
		yes("a square is not one flat colour", colours(first) > 20);

		// THE SCENERY IS ON IT. Lumbridge is a castle, a church, a general store and a mill,
		// which is a great many walls; the doors are the few pixels painted red among them.
		// Without the models none of it reaches the scene at all and the square is bare ground,
		// which looks perfectly fine until you go looking for a building.
		yes("the square has walls on it (" + exactly(first, 0xEEEEEE) + " pixels)",
			exactly(first, 0xEEEEEE) > 200);
		yes("and doors among them (" + exactly(first, 0xEE0000) + " pixels)",
			exactly(first, 0xEE0000) > 10);
		// A BUILDING IS MOSTLY WALL. Counting the two separately is not enough on its own -
		// swap the colours over and both counts are still large enough to look healthy, because
		// some of the mapscene sprites are near-white too. The proportion is what gives it away.
		yes("far more wall than door, as a building is",
			exactly(first, 0xEEEEEE) > exactly(first, 0xEE0000) * 5);

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
			0, differencesInside(first, alone, 6 * MapSquare.SCALE));

		// THE NEIGHBOURS' SCENERY, TOO. A building is anchored at one tile and can be four
		// across, so one anchored just over the boundary has most of itself inside this square;
		// leave the neighbours' locs out and those simply are not drawn, and the only sign is a
		// row of half-buildings along an edge that nothing else disagrees with. Not Lumbridge
		// for this one - nothing happens to reach over its lines - but 54,51, east of it, where
		// three hundred pixels of next door's buildings fall on this side.
		BufferedImage over = rendered("the square east of Lumbridge renders", 54, 51, 0);
		BufferedImage bareEdges = withoutNeighbourLocs(54, 51);
		yes("a building anchored next door is still drawn on this square ("
			+ differences(over, bareEdges) + " pixels)", differences(over, bareEdges) > 0);

		// AND NEXT DOOR IS THE RIGHT NEXT DOOR. That it reaches over at all is not enough: the
		// nine squares go into the scene at nine offsets, and offsets that have x and z the
		// wrong way round still put scenery over all four edges - just the wrong neighbour's,
		// turned a quarter. So each neighbour is let in on its own, and what it spills has to
		// land on the side it came from.
		spill("the square to the west", 54, 51, -1, 0, WEST);
		spill("the square to the north", 54, 51, 0, 1, NORTH);
		spill("the square to the east", 53, 51, 1, 0, EAST);
		spill("the square to the south", 53, 51, 0, -1, SOUTH);

		// THE GROUND HAS NO HOLES IN IT. Every tile of Lumbridge is drawn by something, the
		// water and the grass and the road alike.
		eq("every tile of the ground level is drawn by something", 0, black(first));

		// AND WHERE THE LEVEL RULES BITE, THEY BITE EXACTLY THIS HARD. A map square does not
		// show every tile of its own level: the flags send some of them to the level below and
		// keep others off the map altogether, and bring tiles down from the level above in
		// their place. Lumbridge happens to be a square where all of that cancels out - it
		// renders the same whether the rules are followed or not, which is why checking there
		// proves nothing. 29,70 is where it shows: of its 65536 pixels only 2780 are ground
		// anyone can see, and each of the four rules moves that number. Dropping the span from
		// above leaves 604, drawing the hidden tiles anyway leaves 47328, and the two rules for
		// the scenery give 2176 and 3323.
		//
		// A MEASURED FACT ABOUT THIS CONTENT, not a law: repack a map square here and it has to
		// be measured again. What it is here for is that the bit arithmetic above is checked on
		// flags this test made up, and nothing else says those rules ever reach the picture.
		BufferedImage lumpy = rendered("the square the level rules show on renders", 29, 70, 0);
		eq("it shows exactly the ground its flags leave showing", 2780, 65536 - black(lumpy));

		// AN UPPER FLOOR IS A FLOOR, not another copy of the ground. Castle roofs, the palace,
		// the arena stands: a few thousand pixels on a square, against the ground's 65536.
		BufferedImage upstairs = rendered("the first floor renders", sx, sz, 1);
		yes("there is a first floor here (" + (65536 - black(upstairs)) + " pixels)",
			black(upstairs) < 65536);
		yes("and it is a floor, not the ground again", black(upstairs) > 50000);
		yes("the floor above that is smaller still",
			black(rendered("the second floor renders", sx, sz, 2)) > black(upstairs));

		// AND ABOVE THE ROOFS, NOTHING. Which is most of the world: a file of black pixels per
		// square per level is a file a map would fetch to learn there was nothing to show.
		BufferedImage sky = rendered("a square with no upper floor renders", 51, 50, 2);
		eq("it is empty", 65536, black(sky));
		yes("and reports itself empty", WorldMapRender.isEmpty(sky));
		yes("while the ground does not", !WorldMapRender.isEmpty(first));

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

		// THE TILES A MAP WOULD ACTUALLY READ. One directory, one file per square per level,
		// named for both - and nothing written for a level a square has nothing on, which above
		// the ground is most of them. Two squares are enough to see all of that; seven hundred
		// would take a minute and say no more.
		int[] whole = WorldMapRender.mapIndex;
		int[] land = WorldMapRender.mapLand;
		int[] locs = WorldMapRender.mapLoc;
		File dir = new File(System.getProperty("java.io.tmpdir"), "worldmaptiles" + sz);
		empty(dir);
		try {
			keepOnly(sx, sz, 51, 50);
			WorldMapRender.renderTiles(dir);
		} finally {
			WorldMapRender.mapIndex = whole;
			WorldMapRender.mapLand = land;
			WorldMapRender.mapLoc = locs;
		}
		yes("the ground of one square is written", new File(dir, "50_50_0.png").exists());
		yes("and the ground of the other", new File(dir, "51_50_0.png").exists());
		yes("and the floors above the first square", new File(dir, "50_50_3.png").exists());
		yes("nothing is written where the second square has no second floor",
			!new File(dir, "51_50_2.png").exists());
		yes("nor its third", !new File(dir, "51_50_3.png").exists());
		// 50,50 is Lumbridge and has all four; 51,50 is fields and a road and has two.
		eq("six files in all, from two squares of four levels", 6, pngs(dir));

		// A SQUARE THE INDEX HAS NOT GOT IS NOT A BLACK SQUARE, it is nothing at all, so that
		// renderAll leaves the sea black rather than painting a hole over its neighbour.
		yes("a square outside the index renders nothing",
			WorldMapRender.renderSquare(200, 200, 0) == null);
	}

	/**
	 * The command line, in a JVM that has done nothing else.
	 *
	 * EVERY OTHER CHECK REACHES PAST MAIN into the pieces, so main could stop loading the
	 * models altogether - stripping the scenery off every map anyone rendered - and the suite
	 * would stay green. Calling main from the middle of the suite does not catch that either:
	 * the models are a static table, and so are the caches LocType keeps of the models it has
	 * already built, and by then this test has filled all of them. So the runner starts a
	 * second JVM for this one check, which is the only way to see what main finds when it is
	 * the first thing to run.
	 */
	static void coldStart(File pack) throws Exception {
		System.out.println("-- the command line, cold --");
		File png = File.createTempFile("worldmap", ".png");
		png.deleteOnExit();
		WorldMapRender.main(new String[] { pack.getPath(), png.getPath(), "50,50", "0" });
		BufferedImage written = javax.imageio.ImageIO.read(png);
		eq("it writes a square", 256, written.getWidth());
		yes("with its scenery on it (" + exactly(written, 0xEEEEEE) + " wall pixels)",
			exactly(written, 0xEEEEEE) > 200);
	}

	static final int WEST = 0;
	static final int NORTH = 1;
	static final int EAST = 2;
	static final int SOUTH = 3;

	/**
	 * One neighbour's scenery, let in on its own, and which edge of the picture it lands on.
	 *
	 * A loc is anchored at a tile and drawn from there, so a building just over the western
	 * line paints its last column or two onto this square's western edge and nowhere else.
	 * Four tiles is as deep as the largest of them reaches.
	 */
	static void spill(String where, int sx, int sz, int dx, int dz, int edge) throws Exception {
		BufferedImage bare = onlyLocs(sx, sz, 0, 0);
		BufferedImage with = onlyLocs(sx, sz, dx, dz);
		int n = 0;
		int deepest = -1;
		for (int y = 0; y < bare.getHeight(); y++) {
			for (int x = 0; x < bare.getWidth(); x++) {
				if (bare.getRGB(x, y) == with.getRGB(x, y)) {
					continue;
				}
				n++;
				int depth = edge == WEST ? x
					: edge == NORTH ? y
					: edge == EAST ? bare.getWidth() - 1 - x
					: bare.getHeight() - 1 - y;
				if (depth > deepest) {
					deepest = depth;
				}
			}
		}
		yes(where + " reaches over the line (" + n + " pixels)", n > 0);
		yes(where + " lands on that side, " + deepest + " pixels in",
			deepest >= 0 && deepest < 4 * MapSquare.SCALE);
	}

	/** The index cut down to two squares, so a sweep over all of them is a second's work. */
	static void keepOnly(int ax, int az, int bx, int bz) {
		int[] index = WorldMapRender.mapIndex;
		int[] land = WorldMapRender.mapLand;
		int[] locs = WorldMapRender.mapLoc;
		int[] keptIndex = new int[2];
		int[] keptLand = new int[2];
		int[] keptLoc = new int[2];
		for (int i = 0; i < index.length; i++) {
			int at = index[i] == (ax << 8) + az ? 0 : index[i] == (bx << 8) + bz ? 1 : -1;
			if (at >= 0) {
				keptIndex[at] = index[i];
				keptLand[at] = land[i];
				keptLoc[at] = locs[i];
			}
		}
		WorldMapRender.mapIndex = keptIndex;
		WorldMapRender.mapLand = keptLand;
		WorldMapRender.mapLoc = keptLoc;
	}

	/** A directory with nothing in it, so what is counted afterwards is what this run wrote. */
	static void empty(File dir) {
		File[] was = dir.listFiles();
		for (int i = 0; was != null && i < was.length; i++) {
			was[i].delete();
		}
	}

	static int pngs(File dir) {
		String[] names = dir.list();
		int n = 0;
		for (int i = 0; names != null && i < names.length; i++) {
			if (names[i].endsWith(".png")) {
				n++;
			}
		}
		return n;
	}

	/** The square with its own scenery and one neighbour's, and nobody else's. */
	static BufferedImage onlyLocs(int sx, int sz, int dx, int dz) throws Exception {
		int[] locs = WorldMapRender.mapLoc;
		int[] mask = new int[locs.length];
		for (int i = 0; i < locs.length; i++) {
			int key = WorldMapRender.mapIndex[i];
			mask[i] = key == (sx << 8) + sz || key == (sx + dx << 8) + (sz + dz) ? locs[i] : -1;
		}
		WorldMapRender.mapLoc = mask;
		try {
			return WorldMapRender.renderSquare(sx, sz, 0, 20);
		} finally {
			WorldMapRender.mapLoc = locs;
		}
	}

	/** A square rendered at margin 20, with a renderer that threw reported as a failure. */
	static BufferedImage rendered(String what, int sx, int sz, int level) {
		try {
			BufferedImage img = WorldMapRender.renderSquare(sx, sz, level, 20);
			yes(what, img != null);
			return img;
		} catch (Throwable broken) {
			checks++;
			failures++;
			System.out.println("FAIL " + what + ": " + broken);
			return new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB);
		}
	}

	/** The square with its neighbours' ground but none of their scenery. */
	static BufferedImage withoutNeighbourLocs(int sx, int sz) throws Exception {
		int[] locs = WorldMapRender.mapLoc;
		int[] only = new int[locs.length];
		for (int i = 0; i < locs.length; i++) {
			only[i] = WorldMapRender.mapIndex[i] == (sx << 8) + sz ? locs[i] : -1;
		}
		WorldMapRender.mapLoc = only;
		try {
			return WorldMapRender.renderSquare(sx, sz, 0, 20);
		} finally {
			WorldMapRender.mapLoc = locs;
		}
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

	static int black(BufferedImage img) {
		return exactly(img, 0);
	}

	static int exactly(BufferedImage img, int rgb) {
		int n = 0;
		for (int y = 0; y < img.getHeight(); y++) {
			for (int x = 0; x < img.getWidth(); x++) {
				if ((img.getRGB(x, y) & 0xFFFFFF) == rgb) {
					n++;
				}
			}
		}
		return n;
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

	static void eq(String what, String want, String got) {
		checks++;
		if (want.equals(got)) {
			System.out.println("ok   " + what + " = " + (got.isEmpty() ? "nothing" : got));
		} else {
			failures++;
			System.out.println("FAIL " + what + ": wanted [" + want + "], got [" + got + "]");
		}
	}

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
