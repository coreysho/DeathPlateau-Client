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

import jagex2.client.worldmap.MapIconNames;
import jagex2.client.worldmap.MapIcons;
import jagex2.client.worldmap.MapLabels;
import jagex2.client.worldmap.MapOrb;
import jagex2.client.worldmap.MapSquare;
import jagex2.client.worldmap.MapWindow;
import jagex2.client.worldmap.WorldMap;

public class MapWindowTest {

	static int checks = 0;
	static int failures = 0;

	public static void main(String[] args) {
		placement();
		panning();
		orb();
		icons();
		supply();
		drawing();
		keeping();
		labels();
		bands();
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

	// ----------------------------------------------------------------- the orb

	/**
	 * The button beside the minimap: where it is, and what a click on it is not.
	 *
	 * IT SITS INSIDE THE MINIMAP'S OWN HIT TEST. The walk-here rectangle is the whole of the
	 * map panel from (25, 5), corners included, so every pixel the orb takes is a pixel the
	 * player can no longer walk to - which is the entire reason it is round and in a corner,
	 * and the reason its click is tested before the walk. Two things have to stay true: it
	 * must not cover any of the round map, and it must not take any more of the corner than
	 * the circle it draws.
	 */
	static void orb() {
		System.out.println("-- the orb --");
		yes("the whole orb is inside the minimap panel", MapOrb.insidePanel());
		yes("and it does not reach into the round map (" + MapOrb.overlapsMap() + " pixels)",
			MapOrb.overlapsMap() <= 0);

		yes("a click in the middle of it is on it",
			MapOrb.contains(MapOrb.CENTRE_X, MapOrb.CENTRE_Y));
		yes("and one on its rim", MapOrb.contains(MapOrb.CENTRE_X + MapOrb.RADIUS,
			MapOrb.CENTRE_Y));
		yes("one a pixel past the rim is not",
			!MapOrb.contains(MapOrb.CENTRE_X + MapOrb.RADIUS + 1, MapOrb.CENTRE_Y));
		yes("nor one above it", !MapOrb.contains(MapOrb.CENTRE_X,
			MapOrb.CENTRE_Y - MapOrb.RADIUS - 1));

		// ROUND, NOT SQUARE. The corner of its bounding box is a pixel the player keeps.
		yes("the corner of the square it would fit in is not on it",
			!MapOrb.contains(MapOrb.CENTRE_X + MapOrb.RADIUS, MapOrb.CENTRE_Y + MapOrb.RADIUS));

		// AND IT REALLY IS INSIDE THE WALK RECTANGLE, which is why the order of the two tests
		// in the client matters. If this ever stopped being true the orb would be in dead
		// stone and the click order would not matter - and nobody would notice it had changed.
		yes("the orb is inside the minimap's own click rectangle, so it must be tested first",
			MapOrb.CENTRE_X >= MapOrb.MAP_LEFT
				&& MapOrb.CENTRE_X < MapOrb.MAP_LEFT + MapOrb.MAP_WIDE
				&& MapOrb.CENTRE_Y >= MapOrb.MAP_TOP
				&& MapOrb.CENTRE_Y < MapOrb.MAP_TOP + MapOrb.MAP_HIGH);

		// AND IT DRAWS SOMETHING, in the place it says it is.
		int[] panel = new int[MapOrb.PANEL_WIDE * MapOrb.PANEL_HIGH];
		jagex2.graphics.Pix2D.bind(MapOrb.PANEL_WIDE, MapOrb.PANEL_HIGH, panel);
		jagex2.graphics.Pix2D.resetClipping();
		MapOrb.draw(false);
		yes("the middle of it is painted",
			panel[MapOrb.CENTRE_Y * MapOrb.PANEL_WIDE + MapOrb.CENTRE_X] != 0);
		yes("and its rim", panel[MapOrb.CENTRE_Y * MapOrb.PANEL_WIDE
			+ MapOrb.CENTRE_X + MapOrb.RADIUS] != 0);
		eq("and nothing outside it", 0, panel[(MapOrb.CENTRE_Y - MapOrb.RADIUS - 2)
			* MapOrb.PANEL_WIDE + MapOrb.CENTRE_X]);
		eq("nor in the corner of its square", 0,
			panel[(MapOrb.CENTRE_Y + MapOrb.RADIUS) * MapOrb.PANEL_WIDE
				+ MapOrb.CENTRE_X + MapOrb.RADIUS]);

		// HOVER HAS TO LOOK DIFFERENT or it is not telling the player anything.
		int cold = panel[MapOrb.CENTRE_Y * MapOrb.PANEL_WIDE + MapOrb.CENTRE_X - 8];
		MapOrb.draw(true);
		yes("it lights up under the cursor",
			panel[MapOrb.CENTRE_Y * MapOrb.PANEL_WIDE + MapOrb.CENTRE_X - 8] != cold);
	}

	// ----------------------------------------------------------------- the icons

	/**
	 * Where the icons are, what they are called, and which one a search answers with.
	 *
	 * THE TILE IS PACKED INTO ONE NUMBER and x and z are both six bits of it, so reading them
	 * the wrong way round puts every bank in the county on the wrong side of the river - a map
	 * covered in icons, all of them plausible, none of them where the thing is. The level is
	 * the bits above, and getting that wrong hangs every upstairs bank in the open air.
	 */
	static void icons() {
		System.out.println("-- icons --");

		/*
		 * A SQUARE READ BEFORE THERE WAS A TABLE TO READ IT AGAINST MUST BE READ AGAIN.
		 *
		 * This is the fault that made every real player's map iconless, and no check here could
		 * see it because every check here hands in its own table. The index builds its table out
		 * of the loc configs the first time a square arrives - and for anybody with a file store
		 * on disk, the map files are already there at login and the loc configs are not, so the
		 * table was built from nothing, kept, and never named a single loc again. 0 icons out of
		 * 1,404 map files, measured on a real client against a real server; the driver that said
		 * 55 had a fresh home directory and no file store, so it was the one configuration where
		 * the race is won.
		 *
		 * So: no table means the square is not marked read, and a table arriving later clears
		 * what was read against the old one and says so, which is how the files already in hand
		 * get fed through again.
		 */
		TestIcons early = new TestIcons(new int[0]);
		byte[] bankSquare = locFile(new int[][] { { BANK_ICON, 10, 20, 0 } });
		early.add(50, 60, bankSquare);
		eq("a square read before the loc configs load finds nothing", 0, early.all().size());
		early.functions = bankOnly();
		yes("...and the table says so the moment the configs land", early.refresh());
		yes("...once, and not every frame after", !early.refresh());
		early.add(50, 60, bankSquare);
		eq("...and the same square read again now finds its icon", 1, early.all().size());

		TestIcons told = new TestIcons(bankOnly());
		told.add(1, 1, bankSquare);
		told.add(1, 1, bankSquare);
		eq("and a square that arrives twice against one table is read once", 1,
			told.all().size());

		// One loc file by hand: square-local x=10, z=20, ground; and x=1, z=2, first floor.
		MapIcons found = withBank();
		found.add(50, 60, locFile(new int[][] {
			{ BANK_ICON, 10, 20, 0 },
			{ BANK_ICON, 1, 2, 1 },
		}));
		eq("two icons on the square", 2, found.all().size());
		MapIcons.Icon first = (MapIcons.Icon) found.all().get(0);
		eq("x is the square's own x plus the tile's", 50 * 64 + 10, first.x);
		eq("and z likewise", 60 * 64 + 20, first.z);
		eq("on the ground", 0, first.level);
		MapIcons.Icon second = (MapIcons.Icon) found.all().get(1);
		eq("the second is upstairs", 1, second.level);
		eq("at its own x", 50 * 64 + 1, second.x);
		eq("and its own z", 60 * 64 + 2, second.z);

		// THE SAME FILE TWICE IS THE SAME ICONS. A square's scenery arrives when it is
		// prefetched and again when the player walks into it, and a key that counted both
		// would say there are seventy-eight banks.
		found.add(50, 60, locFile(new int[][] { { BANK_ICON, 10, 20, 0 } }));
		eq("the same square read twice adds nothing", 2, found.all().size());

		eq("the bank icon is called what the content calls it", "Bank store",
			MapIconNames.name(BANK_FUNCTION));
		eq("and the icon knows its own name", "Bank store", first.name());
		eq("a number no loc carries has no name", "", MapIconNames.name(127));
		yes("the content defines a key worth showing", MapIconNames.count() > 40);

		eq("counted by kind", 2, found.count(BANK_FUNCTION));
		eq("and nothing of a kind that is not there", 0, found.count(0));

		// THE KEY COUNTS EVERY KIND IN ONE WALK, because counting them one at a time is
		// sixty-four walks of every icon in the world, four times a second, on the thread
		// that also has to draw. One kind coming out right says nothing: the whole point is
		// that the others do too.
		MapIcons mixed = withBank();
		mixed.add(20, 20, locFile(new int[][] {
			{ BANK_ICON, 1, 1, 0 },
			{ ALTAR_ICON, 2, 2, 0 },
			{ ALTAR_ICON, 3, 3, 0 },
		}));
		int[] counted = mixed.counts(64);
		eq("one bank", 1, counted[BANK_FUNCTION]);
		eq("and two altars", 2, counted[ALTAR_FUNCTION]);
		eq("and nothing of anything else", 0, counted[0]);
		eq("the whole key adds up to every icon there is", 3,
			counted[BANK_FUNCTION] + counted[ALTAR_FUNCTION]);

		eq("found inside a rectangle on its level", 1,
			found.within(0, 0, 9999, 9999, 0).size());
		eq("and not on another level", 1, found.within(0, 0, 9999, 9999, 1).size());
		eq("nor outside the rectangle", 0, found.within(0, 0, 10, 10, 0).size());

		// NEAREST FIRST, measured the way a player walks it. Thirty-nine banks and the one
		// they meant is the one they can see.
		MapIcons many = withBank();
		many.add(10, 10, locFile(new int[][] { { BANK_ICON, 0, 0, 0 } }));
		many.add(11, 10, locFile(new int[][] { { BANK_ICON, 0, 0, 0 } }));
		many.add(40, 40, locFile(new int[][] { { BANK_ICON, 0, 0, 0 } }));
		List hits = many.search("bank", 11 * 64, 10 * 64);
		eq("every bank matches", 3, hits.size());
		eq("the one you are standing on comes first", 11 * 64,
			((MapIcons.Icon) hits.get(0)).x);
		eq("then the one next door", 10 * 64, ((MapIcons.Icon) hits.get(1)).x);
		eq("then the far one", 40 * 64, ((MapIcons.Icon) hits.get(2)).x);

		eq("searching is not case sensitive", 3, many.search("BANK", 0, 0).size());
		eq("and matches part of a name", 3, many.search("ank st", 0, 0).size());
		eq("an empty search finds nothing rather than everything", 0,
			many.search("   ", 0, 0).size());
		eq("and so does a word nothing is called", 0, many.search("zzz", 0, 0).size());

		eq("distance is the longer axis, as a player walks it", 30,
			MapIcons.paces((MapIcons.Icon) many.all().get(0), 10 * 64 + 30, 10 * 64 + 4));

		// THE PICTURE HAS ONE TRANSPARENT COLOUR AND IT IS BLACK. A Pix32 carries no alpha
		// channel: pure black is the hole. Convert it without that and every round icon is a
		// black square sitting on the map, which is a thing you notice but a colour count does
		// not.
		jagex2.graphics.Pix32 made = new jagex2.graphics.Pix32(2, 1);
		made.pixels[0] = 0;
		made.pixels[1] = 0x336699;
		MapIcons.useSprites(new jagex2.graphics.Pix32[] { made });
		java.awt.image.BufferedImage art = MapIcons.sprite(0);
		yes("an icon's picture is made", art != null);
		eq("black comes out transparent", 0, art.getRGB(0, 0) >>> 24);
		eq("and anything else comes out solid", 255, art.getRGB(1, 0) >>> 24);
		eq("with its colour kept", 0x336699, art.getRGB(1, 0) & 0xFFFFFF);
		yes("converted once, not once a repaint", art == MapIcons.sprite(0));
		yes("a sprite the media has not got is nothing at all", MapIcons.sprite(9) == null);
	}

	/**
	 * An index that knows one loc type, the bank icon.
	 *
	 * The real one reads every loc type's mapfunction out of the cache. These checks are about
	 * where an icon lands and what it is called, not about what the cache holds, so they hand
	 * in a table of their own and need no cache at all.
	 */
	static MapIcons withBank() {
		return new TestIcons(bankOnly());
	}

	/**
	 * A MapIcons that answers for itself what the cache would normally answer.
	 *
	 * The real one reads every loc type's mapfunction out of the cache. These checks are about
	 * where an icon lands and what it is called, not about what the cache holds - but they go
	 * through the SAME build-and-rebuild path the game does, because when that path builds its
	 * table is the whole of the fault it was written for.
	 */
	static final class TestIcons extends MapIcons {

		int[] functions;

		TestIcons(int[] functions) {
			this.functions = functions;
		}

		protected int locCount() {
			return this.functions.length;
		}

		protected int locFunction(int id) {
			return this.functions[id];
		}
	}

	/** The loc that marks an altar, so the checks have two kinds and not just one. */
	static final int ALTAR_ICON = 2752;
	static final int ALTAR_FUNCTION = 19;

	static int[] bankOnly() {
		int[] functions = new int[ALTAR_ICON + 1];
		for (int i = 0; i < functions.length; i++) {
			functions[i] = -1;
		}
		functions[BANK_ICON] = BANK_FUNCTION;
		functions[ALTAR_ICON] = ALTAR_FUNCTION;
		return functions;
	}

	/** The loc id of the loc that exists only to put a bank on the map, and its icon number. */
	static final int BANK_ICON = 2738;
	static final int BANK_FUNCTION = 5;

	/**
	 * A loc file as the cache holds one: a run of ids, each with a run of packed positions.
	 *
	 * Written out here rather than taken from the cache so that the checks above can put an
	 * icon on a tile they chose and say where it should come out.
	 */
	static byte[] locFile(int[][] locs) {
		java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
		int lastId = -1;
		int at = 0;
		boolean open = false;
		for (int i = 0; i < locs.length; i++) {
			// ONE RUN PER ID, and the ids ascend. A second run for an id already written would
			// have a delta of zero, and a zero delta is what ends the file - so a hand-written
			// file that repeats an id silently throws away everything after it.
			if (!open || locs[i][0] != lastId) {
				if (open) {
					out.write(0);
				}
				smart(out, locs[i][0] - lastId);
				lastId = locs[i][0];
				at = 0;
				open = true;
			}
			int packed = (locs[i][3] << 12) + (locs[i][1] << 6) + locs[i][2];
			smart(out, packed - at + 1);
			at = packed;
			out.write(0);                      // shape and rotation, which an icon does not use
		}
		if (open) {
			out.write(0);                      // no more of this id
		}
		out.write(0);                          // no more ids
		return out.toByteArray();
	}

	static void smart(java.io.ByteArrayOutputStream out, int value) {
		if (value < 128) {
			out.write(value);
		} else {
			out.write(value + 32768 >> 8 & 0xFF);
			out.write(value + 32768 & 0xFF);
		}
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

		/** A square whose ground is there but whose scenery is not, or the other way round. */
		void hasOnly(int x, int z, int type) {
			this.there.add(Integer.valueOf((x << 8) + z));
			this.holds.add(Integer.valueOf(file(x, z, type)));
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

		public int[] squares() {
			int[] all = new int[this.there.size()];
			for (int i = 0; i < all.length; i++) {
				all[i] = ((Integer) this.there.get(i)).intValue();
			}
			return all;
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

		// ICONS ARRIVE THE WAY MAP FILES ARRIVE. Everything above hands them to the index by
		// hand, and so did the preview - which is how a map that indexed nothing in the real
		// client passed every check there was. A loc file going past is the only way the game
		// ever gets one, so that is the way this gets one too.
		Pretend post = new Pretend();
		post.has(50, 50);
		WorldMap byPost = over(post);
		byPost.useIcons(new TestIcons(bankOnly()));
		yes("nothing is indexed before anything arrives", byPost.icons().all().isEmpty());
		byPost.arrived(Pretend.file(50, 50, 1),
			locFile(new int[][] { { BANK_ICON, 10, 20, 0 } }));
		eq("a loc file going past puts its icons on the map", 1,
			byPost.icons().all().size());

		// AND ONE THAT ARRIVED BEFORE THE LOC CONFIGS DID is read again out of the file the map
		// already holds, the frame after they load. This is the half of the fault that lives
		// here rather than in the index: with a file store on disk the squares round wherever
		// you log in all arrive first, so without this nothing would ever trigger the re-read.
		Pretend soon = new Pretend();
		soon.has(50, 50);
		WorldMap beforeConfigs = over(soon);
		TestIcons late = new TestIcons(new int[0]);
		beforeConfigs.useIcons(late);
		beforeConfigs.arrived(Pretend.file(50, 50, 1),
			locFile(new int[][] { { BANK_ICON, 10, 20, 0 } }));
		eq("a loc file arriving before the loc configs indexes nothing", 0, late.all().size());
		late.functions = bankOnly();
		beforeConfigs.tick();
		eq("...and is read again, from the file already held, once they load", 1,
			late.all().size());
		beforeConfigs.tick();
		eq("...and not once more every frame after that", 1, late.all().size());
		MapIcons.Icon posted = (MapIcons.Icon) byPost.icons().all().get(0);
		eq("in the right square", 50 * 64 + 10, posted.x);
		byPost.arrived(Pretend.file(50, 50, 0), new byte[] { 0 });
		eq("and a LAND file going past adds nothing", 1, byPost.icons().all().size());

		// THE BACKGROUND FETCH OF THE REST OF THE WORLD. The client's own on-demand thread
		// sweeps its priority table whenever nothing urgent is outstanding, so marking is all
		// the map has to do - but it must do it once, when somebody opens the map, and not
		// before: a player who never opens it has no reason to be downloading squares they
		// will not look at.
		Pretend wide = new Pretend();
		wide.has(1, 1);
		WorldMap world = over(wide);
		yes("nothing is marked until somebody opens the map", !wide.prefetched);
		world.opened();
		yes("opening it marks the world", wide.prefetched);
		wide.prefetched = false;
		world.opened();
		yes("and marks it once, not on every open", !wide.prefetched);

		// A CLOSED MAP COSTS THE GAME NOTHING. What it asked for has to STOP being wanted:
		// every arrival afterwards wakes the whole waiting list up again, and the client is
		// full of arrivals, so a map opened once and shut went on building scenes nobody
		// could see for as long as the client was up.
		//
		// COUNTING REQUESTS DOES NOT SHOW THIS, which is how it got past the first time: a
		// file already asked for is not asked for twice, so the number stops going up whether
		// the map gave up or not. What shows it is the work itself - if the queue is still
		// live, the square gets drawn.
		Pretend quiet3 = new Pretend();
		quiet3.existsButSilent(90, 90);
		WorldMap shut2 = over(quiet3);
		shut2.square(90, 90, 0);
		shut2.tick();
		shut2.closed();
		shut2.arrived(Pretend.file(90, 90, 0), new byte[] { 0 });
		shut2.arrived(Pretend.file(90, 90, 1), new byte[] { 0 });
		for (int frame = 0; frame < 20; frame++) {
			shut2.tick();
		}
		yes("a closed map draws nothing, even once what it wanted turns up",
			shut2.square(90, 90, 0) == null);
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

		// GROUND AND SCENERY ARE BOTH REQUIRED, not either. A square drawn the moment its land
		// turns up is a square with no buildings on it, and nothing ever draws it again: the
		// map keeps a finished picture of a town with no town in it.
		Pretend half = new Pretend();
		half.hasOnly(60, 60, 0);
		WorldMap partial = over(half);
		partial.square(60, 60, 0);
		for (int frame = 0; frame < 10; frame++) {
			partial.tick();
		}
		yes("a square with its ground but not its scenery is not drawn yet",
			partial.square(60, 60, 0) == null);
		partial.arrived(Pretend.file(60, 60, 1), new byte[] { 0 });
		partial.tick();
		yes("and is drawn once the scenery turns up too",
			partial.square(60, 60, 0) != null);

		// THE EIGHT AROUND IT, TOO. A square is lit by its neighbours, so all nine go in: two
		// files each, eighteen in all, and a square gathered alone would ask for two.
		Pretend town = new Pretend();
		for (int x = 49; x <= 51; x++) {
			for (int z = 49; z <= 51; z++) {
				town.has(x, z);
			}
		}
		WorldMap nine = over(town);
		nine.square(50, 50, 0);
		nine.tick();
		eq("nine squares' worth of files are asked for", 18, town.asked.size());

		// AND EACH OF THEM ONCE. The same neighbour is wanted by several squares and by every
		// level of each; asking again for one already asked for is a request per frame per
		// square, at a server, for a file that is already on its way.
		nine.square(50, 50, 1);
		nine.square(50, 50, 2);
		nine.tick();
		nine.tick();
		eq("and not asked for again by the floors above", 18, town.asked.size());

		/*
		 * AND THE QUEUE OF SQUARES DOES NOT GROW WHILE IT WAITS.
		 *
		 * The window repaints eight times a second and asks for every square it can see that
		 * it has not got yet, so a square it is waiting on is asked for eight times a second.
		 * As a plain queue that was a duplicate every time: a window the size of a screen
		 * shows five hundred squares, and the queue measured SEVENTEEN THOUSAND entries - all
		 * but a few hundred of them the same squares again. tick() looks at thirty-two a
		 * frame, so it spent every frame stepping past copies of squares it had already drawn.
		 * The map stopped filling in and the game thread went on paying for it, and both of
		 * those got worse the bigger the window: at the default size it kept up, which is why
		 * every check and every picture here had said it was fine.
		 */
		Pretend over = new Pretend();
		over.has(50, 50);
		WorldMap asking = over(over);
		for (int again = 0; again < 200; again++) {
			asking.square(50, 50, 0);
			asking.square(51, 51, 0);
		}
		eq("asking for two squares two hundred times queues two", 2, queued(asking));
		asking.tick();
		asking.tick();
		eq("...and drawing them empties the queue", 0, queued(asking));
		asking.square(50, 50, 0);
		eq("a square already drawn is not queued again", 0, queued(asking));
		asking.square(60, 60, 0);
		yes("but one that is not drawn still is", queued(asking) > 0);
		asking.closed();
		eq("and closing the map empties it", 0, queued(asking));
		// AND EMPTIES WHAT IT REMEMBERS OF IT, or the map is shut for good: a square that was
		// queued when it closed would be remembered as queued forever, and asking for it on
		// the next open would quietly do nothing.
		asking.square(60, 60, 0);
		yes("...and the same square can be asked for again on the next open",
			queued(asking) > 0);

		// A FILE THAT IS NOT COMING IS ASKED FOR ONCE. Its square is set aside, but every
		// arrival of anything wakes it up again, and each waking gathers again. Without a
		// memory of what has been asked for that is a request per waking, at a server, for a
		// file already on its way - and the busier the area, the more of them.
		Pretend patchy = new Pretend();
		patchy.has(70, 70);
		patchy.existsButSilent(71, 70);
		WorldMap edge = over(patchy);
		edge.square(70, 70, 0);
		for (int frame = 0; frame < 20; frame++) {
			edge.tick();
		}
		int asks = 0;
		for (int i = 0; i < patchy.asked.size(); i++) {
			if (((Integer) patchy.asked.get(i)).intValue() == Pretend.file(71, 70, 0)) {
				asks++;
			}
		}
		eq("the neighbour that never answers is asked once, not once a waking", 1, asks);
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
		// ENOUGH OF THEM TO BE WORTH HAVING, as well as not all of them. A cache of one is
		// bounded too, and would redraw the window's whole view every time it moved a pixel.
		yes("a hundred squares drawn, not a hundred kept (" + held + ")",
			held > 16 && held <= 64);

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

	// ----------------------------------------------------------------- the names of places

	/**
	 * The place names, and which zoom shows which.
	 *
	 * WHERE EACH ONE IS is checked by the generator instead, against the squares the content
	 * packs - a label in the sea is the fault worth catching and only the content can say
	 * where the sea is. What is checked here is everything the window does with the table
	 * after that: splitting a name, and deciding whether it has room for one.
	 */
	static void labels() {
		System.out.println();
		System.out.println("-- the names of places --");

		// THE FOUR COLUMNS ARE ONE TABLE, and a generator that lost a row from one of them
		// would put every name after it over the wrong place. Read against each other rather
		// than against a number, so adding a place does not touch this test.
		eq("as many x as names", MapLabels.NAME.length, MapLabels.X.length);
		eq("as many z as names", MapLabels.NAME.length, MapLabels.Z.length);
		eq("as many ranks as names", MapLabels.NAME.length, MapLabels.RANK.length);
		yes("there are places to name at all", MapLabels.NAME.length > 0);

		int outside = 0;
		int unranked = 0;
		int empty = 0;
		for (int i = 0; i < MapLabels.NAME.length; i++) {
			if (MapLabels.X[i] < 0 || MapLabels.Z[i] < 0
					|| MapLabels.X[i] > 0xFF * MapSquare.SQUARE
					|| MapLabels.Z[i] > 0xFF * MapSquare.SQUARE) {
				outside++;
			}
			if (MapLabels.RANK[i] < MapLabels.RANK_PLACE
					|| MapLabels.RANK[i] > MapLabels.RANK_REGION) {
				unranked++;
			}
			if (MapLabels.NAME[i].length() == 0) {
				empty++;
			}
		}
		eq("every place is somewhere in the world", 0, outside);
		eq("every place is a region, a town or a place", 0, unranked);
		eq("no place is nameless", 0, empty);

		// A SLASH IS A LINE BREAK. "Kingdom Of/Misthalin" written on one line is a word and a
		// half across a kingdom; the point of the convention is that it does not have to be.
		String[] two = MapLabels.lines("Kingdom Of/Misthalin");
		eq("a slashed name is two lines", 2, two.length);
		eq("...the first being", "Kingdom Of", two[0]);
		eq("...and the second", "Misthalin", two[1]);
		eq("an unslashed name is one line", 1, MapLabels.lines("Varrock").length);
		eq("...which is the name", "Varrock", MapLabels.lines("Varrock")[0]);
		eq("two slashes are three lines", 3, MapLabels.lines("a/b/c").length);
		eq("...and the middle one survives", "b", MapLabels.lines("a/b/c")[1]);

		// AND NOTHING COMES BACK STILL CARRYING ONE, which is what a split on the wrong
		// character, or one that stops at the first, would leave behind.
		int unsplit = 0;
		for (int i = 0; i < MapLabels.NAME.length; i++) {
			String[] parts = MapLabels.lines(MapLabels.NAME[i]);
			for (int part = 0; part < parts.length; part++) {
				if (parts[part].indexOf('/') >= 0) {
					unsplit++;
				}
			}
		}
		eq("no line still has a slash in it", 0, unsplit);

		// WHICH ZOOM SHOWS WHICH. Zoomed right out the window shows four hundred squares, and
		// a name on every guild and cow pen in four hundred squares is a list with ground
		// behind it.
		yes("zoomed right out, a kingdom is named",
			MapWindow.labelShown(MapLabels.RANK_REGION, 1));
		yes("...and so is a town", MapWindow.labelShown(MapLabels.RANK_TOWN, 1));
		yes("...but not every mine and guild",
			!MapWindow.labelShown(MapLabels.RANK_PLACE, 1));
		yes("a step in and the mines are named too",
			MapWindow.labelShown(MapLabels.RANK_PLACE, 2));
		yes("...and at the closest zoom", MapWindow.labelShown(MapLabels.RANK_PLACE, 4));

		// AND TWO NAMES NEVER SHARE PIXELS. Underground, Falador Mine and Draynor Basement
		// are nineteen tiles apart, which at the far zoom is nineteen pixels: the two printed
		// over each other into one word that was neither.
		List placed = new ArrayList();
		java.awt.Rectangle first = new java.awt.Rectangle(100, 100, 60, 14);
		yes("the first name always has room", MapWindow.roomFor(placed, first));
		placed.add(first);
		yes("one right on top of it does not",
			!MapWindow.roomFor(placed, new java.awt.Rectangle(100, 100, 60, 14)));
		yes("one overlapping a corner of it does not",
			!MapWindow.roomFor(placed, new java.awt.Rectangle(150, 110, 60, 14)));
		yes("one clear of it does",
			MapWindow.roomFor(placed, new java.awt.Rectangle(100, 130, 60, 14)));
		yes("...and one just past its edge does",
			MapWindow.roomFor(placed, new java.awt.Rectangle(160, 100, 60, 14)));

		// THE RULE HAS TO SEE EVERY BOX ALREADY DOWN, not just the last one: a third name can
		// clear the second and still land on the first.
		placed.add(new java.awt.Rectangle(300, 300, 60, 14));
		yes("a name clear of the newest but not the oldest is still refused",
			!MapWindow.roomFor(placed, new java.awt.Rectangle(110, 105, 20, 8)));
	}

	// ----------------------------------------------------------------- the two bands

	/**
	 * Surface and underground, and the jump between them.
	 *
	 * THE WORLD IS TWO BANDS WITH FIFTY-SIX EMPTY ROWS BETWEEN THEM, so a quarter of the map
	 * can be drawn perfectly and never be seen. The arithmetic of the jump is the whole of
	 * whether it can be: one step of a hundred rows, the same step back.
	 */
	static void bands() {
		System.out.println();
		System.out.println("-- surface and underground --");

		yes("Varrock is on the surface", !MapWindow.isUnderground(3450));
		yes("the Varrock sewers are not", MapWindow.isUnderground(9833));

		// THE BOUNDARY ITSELF, because an off-by-one here puts the last surface row
		// underground, and row 139 is empty so nothing would ever look wrong.
		int boundary = 140 * MapSquare.SQUARE;
		yes("the row below the boundary is surface", !MapWindow.isUnderground(boundary - 1));
		yes("the boundary row itself is underground", MapWindow.isUnderground(boundary));

		// A HUNDRED ROWS, which is where 175 of the 179 underground squares sit relative to
		// the surface square they are under.
		eq("under Varrock is a hundred rows north", 3450 + 100 * MapSquare.SQUARE,
			MapWindow.twinOf(3450));
		eq("and back again", 3450, MapWindow.twinOf(MapWindow.twinOf(3450)));

		// THE SAME STEP BOTH WAYS, so the button is one button. Over every z in the map's
		// whole range and not just the rows the world has squares in, because panning is not
		// stopped at the last square and the button has to mean something wherever it is
		// pressed.
		int wrong = 0;
		int unmoved = 0;
		int admitted = 0;
		for (int z = 0; z <= 0xFF * MapSquare.SQUARE; z += 37) {
			int to = MapWindow.jumpTo(z);
			if (to < 0) {
				// The one honest refusal: a step from here would not leave the band, so the
				// button goes to the nearest square instead of lying about it.
				admitted++;
				continue;
			}
			if (MapWindow.isUnderground(to) == MapWindow.isUnderground(z)) {
				unmoved++;
			}
			if (MapWindow.jumpTo(to) != z) {
				wrong++;
			}
		}
		eq("a jump that says where it lands always changes band", 0, unmoved);
		eq("...and jumping back is where you started", 0, wrong);
		yes("...and from the empty north it says it cannot, rather than not moving",
			admitted > 0);

		// AND THE ROWS THE WORLD ACTUALLY HAS all jump cleanly, which is the case a player is
		// ever in: rows 40 to 85 on the surface, 142 to 161 underground.
		int refused = 0;
		for (int row = 40; row <= 161; row++) {
			if (MapWindow.jumpTo(row * MapSquare.SQUARE + 32) < 0) {
				refused++;
			}
		}
		eq("every row the world has can be jumped out of", 0, refused);
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

	/** How many squares the map has queued to draw, duplicates and all. */
	static int queued(WorldMap map) {
		try {
			java.lang.reflect.Field f = WorldMap.class.getDeclaredField("wanted");
			f.setAccessible(true);
			return ((java.util.Collection) f.get(map)).size();
		} catch (Exception broken) {
			throw new RuntimeException(broken);
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
