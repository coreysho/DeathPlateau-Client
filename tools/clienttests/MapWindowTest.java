/*
 * Checks the world map window: where it puts things, and how it gets them.
 *
 * TWO THINGS NOBODY CAN SEE GOING WRONG. The first is the arithmetic the window does on top of
 * the renderer's: x grows east and so does a picture, but z grows north and a picture grows
 * down, so one of those measurements adds the centre and the other subtracts it. Get the pair
 * wrong and the window shows a map - a real one, of real ground, drawn correctly - of somewhere
 * the player is not. Get the drag the wrong way round and panning walks away from the cursor,
 * which a player notices but a screenshot does not.
 *
 * The second is the supply. The window asks for squares; the game thread draws one a frame;
 * squares the player has never been near are not in the cache at all. Every failure there is
 * silence: a map that never fills in, a map that fills in and then forgets, a map that spends
 * every frame asking for ground that is not coming. None of it throws and none of it looks
 * like anything.
 *
 * Needs no display, no network and no cache: the Supply here is a made-up one, which is what
 * that interface is for.
 *
 * Run by tools/clienttests/run_mapwindowtest.py.
 */
package jagex2.client;

import java.util.ArrayList;
import java.util.List;

import jagex2.client.worldmap.MapSquare;
import jagex2.client.worldmap.MapWindow;
import jagex2.client.worldmap.WorldMap;

public class MapWindowTest {

	static int checks = 0;
	static int failures = 0;

	public static void main(String[] args) {
		placement();
		panning();
		supply();
		drawing();
		keeping();
		System.out.println();
		System.out.println(checks + " checks, " + failures + " failures");
		if (failures > 0) {
			System.exit(1);
		}
	}

	// ----------------------------------------------------------------- where things land

	static void placement() {
		System.out.println("-- placement --");

		// THE CENTRE TILE LANDS IN THE MIDDLE, which is the whole contract of a centred map.
		// 900 wide at two pixels a tile: tile 3222 has to come out at 450.
		int originX = MapWindow.originAcross(900, 3222, 2);
		eq("the centred tile is halfway across", 450, MapWindow.acrossOf(originX, 3222, 2));
		eq("the tile east of it is two pixels further",
			452, MapWindow.acrossOf(originX, 3223, 2));
		eq("and the tile west of it two pixels back",
			448, MapWindow.acrossOf(originX, 3221, 2));

		// DOWN IS THE OTHER WAY. A tile is placed by its top edge and z counts up from the
		// bottom of the picture, so the centred tile's top edge is one tile above the middle.
		int originZ = MapWindow.originDown(700, 3218, 2);
		eq("the centred tile's top edge is a tile above halfway",
			348, MapWindow.downOf(originZ, 3218, 2));
		yes("the tile to the NORTH is higher up the window",
			MapWindow.downOf(originZ, 3219, 2) < MapWindow.downOf(originZ, 3218, 2));
		yes("and the tile to the SOUTH is lower down",
			MapWindow.downOf(originZ, 3217, 2) > MapWindow.downOf(originZ, 3218, 2));
		eq("one tile north is two pixels up", 2,
			MapWindow.downOf(originZ, 3218, 2) - MapWindow.downOf(originZ, 3219, 2));

		// A SQUARE IS PLACED THE SAME WAY, by its own step rather than a tile's, and its top
		// edge is the far side of its 64 tiles.
		int cell = MapSquare.SQUARE * 2;
		int squareZ = MapWindow.originDown(700, 0, 2);
		eq("square 0 fills the row under the origin", -cell,
			MapWindow.downOf(squareZ, 0, cell) - 350);
		eq("square 1 sits a whole square above it", cell,
			MapWindow.downOf(squareZ, 0, cell) - MapWindow.downOf(squareZ, 1, cell));

		// ZOOM MULTIPLIES BOTH, and the centre stays put while it does.
		eq("zoomed in, the centred tile is still halfway across", 450,
			MapWindow.acrossOf(MapWindow.originAcross(900, 3222, 4), 3222, 4));
		eq("and zoomed out", 450,
			MapWindow.acrossOf(MapWindow.originAcross(900, 3222, 1), 3222, 1));
	}

	static void panning() {
		System.out.println("-- panning --");

		// THE MAP FOLLOWS THE CURSOR. Drag the mouse east and the ground under it goes east
		// with it, which means the centre of the view moves WEST. Reverse either of these and
		// the map slides away from the hand holding it.
		eq("dragging east shows what is west", 3212, MapWindow.pannedX(3222, 20, 2));
		eq("dragging west shows what is east", 3232, MapWindow.pannedX(3222, -20, 2));
		eq("dragging down the window shows what is north", 3228, MapWindow.pannedZ(3218, 20, 2));
		eq("dragging up shows what is south", 3208, MapWindow.pannedZ(3218, -20, 2));

		// AND A DRAG IS IN PIXELS, SO THE ZOOM DIVIDES IT: the same hand movement covers four
		// times the ground when each tile is a pixel.
		eq("twenty pixels at one pixel a tile is twenty tiles",
			3202, MapWindow.pannedX(3222, 20, 1));
		eq("twenty pixels at four is five", 3217, MapWindow.pannedX(3222, 20, 4));
	}

	// ----------------------------------------------------------------- the supply

	/** A cache that has whatever it was told to have, and counts what it was asked for. */
	static final class Pretend implements WorldMap.Supply {

		final List asked = new ArrayList();
		boolean prefetched;
		WorldMap map;

		/** Squares that exist, as x,z keys; files are numbered from them so they are distinct. */
		final List there = new ArrayList();

		/** Files that answer at once. Anything else is asked for and never arrives. */
		final List holds = new ArrayList();

		void has(int x, int z) {
			this.there.add(Integer.valueOf((x << 8) + z));
			this.holds.add(Integer.valueOf(file(x, z, 0)));
			this.holds.add(Integer.valueOf(file(x, z, 1)));
		}

		void existsButSilent(int x, int z) {
			this.there.add(Integer.valueOf((x << 8) + z));
		}

		static int file(int x, int z, int type) {
			return ((x << 8) + z) * 2 + type;
		}

		public int fileFor(int x, int z, int type) {
			return this.there.contains(Integer.valueOf((x << 8) + z)) ? file(x, z, type) : -1;
		}

		public void request(int which) {
			this.asked.add(Integer.valueOf(which));
			if (this.holds.contains(Integer.valueOf(which))) {
				// A byte of nothing: these checks are about what is asked for and kept, not
				// about what a square looks like, which WorldMapTest covers against real data.
				this.map.arrived(which, new byte[] { 0 });
			}
		}

		public void prefetchAll() {
			this.prefetched = true;
		}
	}

	static WorldMap over(Pretend cache) {
		WorldMap map = new WorldMap(cache);
		cache.map = map;
		return map;
	}

	static void supply() {
		System.out.println("-- the supply --");

		eq("a key carries the square and the level", 50,
			WorldMap.squareX(WorldMap.key(50, 51, 2)));
		eq("and the other half of the square", 51, WorldMap.squareZ(WorldMap.key(50, 51, 2)));
		eq("and the level", 2, WorldMap.level(WorldMap.key(50, 51, 2)));
		yes("two levels of one square are different keys",
			WorldMap.key(50, 51, 0) != WorldMap.key(50, 51, 1));
		yes("and so are x and z the other way round",
			WorldMap.key(50, 51, 0) != WorldMap.key(51, 50, 0));

		Pretend cache = new Pretend();
		cache.has(50, 50);
		WorldMap map = over(cache);
		yes("a square is not drawn before it is asked for", map.square(50, 50, 0) == null);
		yes("and asking for it does not draw it there and then", cache.asked.isEmpty());

		// NINE SQUARES ARE ASKED FOR, not one: a square is lit by the tiles around it. Only the
		// middle one exists here, so only its two files are real; the other eight are holes in
		// the world and are not asked for at all.
		map.tick();
		eq("the land and the scenery of the one square that exists", 2, cache.asked.size());

		yes("the world knows the square is there", map.exists(50, 50));
		yes("and knows its neighbour is not", !map.exists(50, 51));

		yes("the prefetch is off until somebody opens the map", !cache.prefetched);
		map.prefetchAll();
		yes("and on afterwards", cache.prefetched);
		cache.prefetched = false;
		map.prefetchAll();
		yes("once, not on every open", !cache.prefetched);
	}

	static void drawing() {
		System.out.println("-- drawing --");

		Pretend cache = new Pretend();
		cache.has(50, 50);
		WorldMap map = over(cache);
		map.square(50, 50, 0);
		map.tick();
		map.tick();
		int[] pixels = map.square(50, 50, 0);
		yes("once its files are in, the square is drawn", pixels != null);
		eq("and it is a square of the size the renderer draws",
			MapSquare.SIDE * MapSquare.SIDE, pixels.length);
		yes("asking again hands back the same one, not another draw",
			pixels == map.square(50, 50, 0));

		// A SQUARE WHOSE GROUND IS NOT COMING MUST NOT BE ASKED FOR AGAIN EVERY FRAME. It is
		// set aside until something arrives that might have been what it was waiting for; a
		// map left open over unexplored ground should cost nothing at all.
		Pretend quiet = new Pretend();
		quiet.existsButSilent(40, 40);
		WorldMap waiting = over(quiet);
		waiting.square(40, 40, 0);
		for (int frame = 0; frame < 50; frame++) {
			waiting.tick();
		}
		yes("a square that never arrives is asked for once, not fifty times",
			quiet.asked.size() == 2);
		yes("and it is still not drawn", waiting.square(40, 40, 0) == null);

		// UNTIL ITS GROUND TURNS UP, which puts it back in the queue by itself.
		waiting.arrived(Pretend.file(40, 40, 0), new byte[] { 0 });
		waiting.arrived(Pretend.file(40, 40, 1), new byte[] { 0 });
		waiting.tick();
		yes("an arrival wakes it up again", waiting.square(40, 40, 0) != null);
	}

	static void keeping() {
		System.out.println("-- keeping --");

		// A SQUARE IS A QUARTER OF A MEGABYTE and a map window can see a hundred of them, so
		// what is kept has to be bounded. The oldest goes.
		Pretend cache = new Pretend();
		for (int x = 0; x < 10; x++) {
			for (int z = 0; z < 10; z++) {
				cache.has(x, z);
			}
		}
		WorldMap map = over(cache);
		for (int x = 0; x < 10; x++) {
			for (int z = 0; z < 10; z++) {
				map.square(x, z, 0);
			}
		}
		for (int frame = 0; frame < 200; frame++) {
			map.tick();
		}
		int held = 0;
		for (int x = 0; x < 10; x++) {
			for (int z = 0; z < 10; z++) {
				if (map.square(x, z, 0) != null) {
					held++;
				}
			}
		}
		yes("a hundred squares drawn, not a hundred kept (" + held + ")",
			held > 0 && held <= 64);

		// WHICH ONES ARE KEPT IS NOT PROMISED - the queue reorders itself as files arrive, so
		// pinning a particular survivor would be pinning an accident. What is promised is that
		// one that was dropped comes back when it is wanted: a player panning away and back
		// again waits a frame, not forever.
		int gone = -1;
		for (int x = 0; x < 10 && gone < 0; x++) {
			for (int z = 0; z < 10 && gone < 0; z++) {
				if (map.square(x, z, 0) == null) {
					gone = (x << 8) + z;
				}
			}
		}
		yes("some of them were dropped", gone >= 0);
		for (int frame = 0; frame < 4; frame++) {
			map.tick();
		}
		yes("and one that was dropped is drawn again when it is asked for",
			map.square(gone >> 8, gone & 0xFF, 0) != null);
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
