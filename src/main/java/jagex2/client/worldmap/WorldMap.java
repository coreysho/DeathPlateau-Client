/*
 * The world map's supply of squares: what has been drawn, what is being waited for.
 *
 * WHY THE CLIENT DRAWS ITS OWN MAP RATHER THAN SHIPPING ONE. Seven hundred squares at four
 * pixels a tile is twenty megabytes of picture, which is not going in a jar a launcher fetches
 * on every release, and a map shipped separately is a second thing to build and keep in step
 * with the content. The client already has the map: it is in the cache it fills as a player
 * walks around, and MapSquare can draw it with the game's own minimap code. So the map is
 * drawn here, from here, and nothing is downloaded that the game was not downloading anyway.
 *
 * ON THE GAME THREAD, ONE SQUARE A FRAME. Drawing a square builds a scene and lights it - about
 * ten milliseconds - and it writes statics the live game shares, so it cannot happen on the
 * window's own thread and it cannot happen in a burst. The window asks for what it can see;
 * tick() draws at most one of them; the window repaints when one lands. Opening the map on a
 * fresh view therefore fills in over a second or two rather than freezing the game for one.
 *
 * WHAT IT DOES NOT HAVE YET. A square the player has never been near is not in the cache, and
 * the only way to get it is to ask the server. prefetchAll turns on the client's own background
 * prefetch - a thing 377 has and nothing was using - so that the rest of the world arrives at
 * low priority while the player plays, and stays on disk afterwards. Until a square arrives the
 * map leaves it blank rather than drawing it wrong.
 */
package jagex2.client.worldmap;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import jagex2.io.OnDemand;

public final class WorldMap {

	/** The levels a square can be drawn at, which is as many as the scene has. */
	public static final int LEVELS = 4;

	/**
	 * How many drawn squares to keep.
	 *
	 * A square is 256 kilobytes of pixels, so this is sixteen megabytes - about what a window
	 * two thousand pixels across can show at full zoom, with a ring around it to pan into.
	 */
	private static final int KEEP = 64;

	/** How many wanted squares to look at in one frame before giving up until the next. */
	private static final int LOOK_AT = 32;

	/**
	 * Where squares come from.
	 *
	 * IT IS AN INTERFACE SO THE MAP CAN BE DRIVEN WITHOUT A SERVER. In the game this is the
	 * client's on-demand thread; in the preview and the checks it is a packed cache read off
	 * disk, which is the only way to paint this window and look at it without logging in.
	 */
	public interface Supply {

		/** The on-demand file number for a square's land (type 0) or scenery (1), or -1. */
		int fileFor(int x, int z, int type);

		/** Asks for a file. What arrives comes back through {@link WorldMap#arrived}. */
		void request(int file);

		/** Turns on the background fetch of every square there is. */
		void prefetchAll();

		/** Every square the world has, packed as (x << 8) + z. */
		int[] squares();
	}

	private final Supply supply;

	/** Map files as they arrive, by on-demand file number. Written on the game thread. */
	private final Map files = new HashMap();

	/** Files asked for and not yet seen, so the same one is not asked for twice. */
	private final Set asked = new HashSet();

	/** Drawn squares by key. Written on the game thread, read by the window's thread. */
	private final Map drawn = new ConcurrentHashMap();

	/** The order they were drawn in, so the oldest can go when there are too many. */
	private final Queue age = new ArrayDeque();

	/** Squares the window has asked for. Written by its thread, drained by the game's. */
	private final Queue wanted = new ConcurrentLinkedQueue();

	/** Wanted squares whose data has not all arrived, held back until some of it does. */
	private final List waiting = new ArrayList();

	private boolean prefetched;

	/** Every icon in every square whose scenery has arrived. */
	private final MapIcons icons = new MapIcons();

	/** Which square each square's loc file belongs to, so an arrival can be placed. */
	private int[] squareOfLocFile;

	public WorldMap(Supply supply) {
		this.supply = supply;
	}

	/** The map as the game supplies it: the client's own on-demand thread. */
	public static WorldMap of(final OnDemand onDemand) {
		return new WorldMap(new Supply() {

			public int fileFor(int x, int z, int type) {
				return onDemand.getMapFile(x, z, type);
			}

			public void request(int file) {
				onDemand.request(3, file);
			}

			public void prefetchAll() {
				onDemand.prefetchMaps(true);
			}

			public int[] squares() {
				int[] all = new int[onDemand.mapIndex.length];
				System.arraycopy(onDemand.mapIndex, 0, all, 0, all.length);
				return all;
			}
		});
	}

	/** x, z and level packed into one number, which is what everything here is keyed by. */
	public static int key(int x, int z, int level) {
		return (x << 16) + (z << 8) + level;
	}

	public static int squareX(int key) {
		return key >> 16 & 0xFF;
	}

	public static int squareZ(int key) {
		return key >> 8 & 0xFF;
	}

	public static int level(int key) {
		return key & 0xFF;
	}

	/**
	 * A drawn square, or null if it is not drawn yet - in which case it is now wanted.
	 *
	 * Safe to call from the window's thread: what comes back is either a finished array or
	 * nothing, and the game thread never writes into an array it has already handed over.
	 */
	public int[] square(int x, int z, int level) {
		Integer at = Integer.valueOf(key(x, z, level));
		int[] pixels = (int[]) this.drawn.get(at);
		if (pixels == null) {
			this.wanted.add(at);
		}
		return pixels;
	}

	/** Whether the world has a square there at all, drawn or not. */
	public boolean exists(int x, int z) {
		return this.supply.fileFor(x, z, 0) >= 0;
	}

	/**
	 * Draws at most one wanted square. Game thread, once a frame.
	 *
	 * IT LOOKS PAST THE ONES IT CANNOT DRAW. A square whose land has not arrived is set aside
	 * rather than retried in a loop, and put back only when something arrives that might have
	 * been what it was waiting for. Otherwise a map opened over unexplored ground would spend
	 * every frame asking the same question.
	 */
	public void tick() {
		for (int looked = 0; looked < LOOK_AT; looked++) {
			Integer at = (Integer) this.wanted.poll();
			if (at == null) {
				return;
			}
			if (this.drawn.containsKey(at)) {
				continue;
			}
			int x = squareX(at.intValue());
			int z = squareZ(at.intValue());
			// SET ASIDE BEFORE IT IS GATHERED, not after. Gathering is what asks for the
			// missing files, and an answer that comes back inside that call - which is what a
			// supply reading off a local cache does - would otherwise sweep the waiting list
			// a moment before this square joined it, and this square would wait for an
			// arrival that had already happened.
			this.waiting.add(at);
			byte[][][] land = this.gather(x, z, 0);
			byte[][][] locs = this.gather(x, z, 1);
			if (land == null || locs == null) {
				continue;
			}
			this.waiting.remove(at);
			int[] pixels = null;
			try {
				pixels = MapSquare.draw(land, locs, x, z, level(at.intValue()));
			} catch (Throwable broken) {
				// A square that will not draw must not take the frame down with it, and must
				// not be asked for again every frame after that either: an empty one is
				// remembered in its place.
				pixels = new int[MapSquare.SIDE * MapSquare.SIDE];
			}
			if (pixels == null) {
				pixels = new int[MapSquare.SIDE * MapSquare.SIDE];
			}
			this.keep(at, pixels);
			return;
		}
	}

	/**
	 * The nine squares' data, or null when any of it is still coming.
	 *
	 * A square with no entry in the index is a hole in the world and its null is the right
	 * answer; a square that has one but no bytes yet is asked for and waited on, because
	 * drawing without a neighbour gives that edge the wrong light and nothing would ever
	 * redraw it.
	 */
	private byte[][][] gather(int sx, int sz, int type) {
		byte[][][] nine = new byte[3][3][];
		boolean complete = true;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				int file = this.supply.fileFor(sx + dx, sz + dz, type);
				if (file < 0) {
					continue;
				}
				Integer which = Integer.valueOf(file);
				byte[] data = (byte[]) this.files.get(which);
				if (data == null) {
					this.ask(which);
					complete = false;
				} else {
					nine[dx + 1][dz + 1] = data;
				}
			}
		}
		return complete ? nine : null;
	}

	private void ask(Integer file) {
		if (this.asked.add(file)) {
			this.supply.request(file.intValue());
		}
	}

	private void keep(Integer at, int[] pixels) {
		this.drawn.put(at, pixels);
		this.age.add(at);
		while (this.age.size() > KEEP) {
			this.drawn.remove(this.age.poll());
		}
	}

	/**
	 * A map file has arrived. Game thread, from the client's on-demand pump.
	 *
	 * A null means the server has not got it, which is still an answer: it is remembered as an
	 * empty file so that nothing waits on it forever.
	 */
	public void arrived(int file, byte[] data) {
		Integer which = Integer.valueOf(file);
		this.files.put(which, data == null ? new byte[0] : data);
		// A LOC FILE IS ALSO A LIST OF ICONS, and this is the only moment the map is certain to
		// see one: a square is drawn only if somebody looks at it, but every square's scenery
		// comes past here once, so the key and the search fill in as the prefetch lands rather
		// than as the player pans.
		int[] of = this.squareOfLocFile();
		if (data != null && file >= 0 && file < of.length && of[file] >= 0) {
			this.icons.add(of[file] >> 8, of[file] & 0xFF, data);
		}
		if (this.waiting.isEmpty()) {
			return;
		}
		// Everything set aside goes back in the queue: working out which of them wanted this
		// particular file costs more than simply asking all of them again.
		this.wanted.addAll(this.waiting);
		this.waiting.clear();
	}

	/**
	 * Turns on the background prefetch of every map square, once.
	 *
	 * 377 HAS THIS AND NOTHING USED IT. prefetchMaps marks every square low priority, and the
	 * on-demand thread then fetches them in the gaps between what the game actually needs,
	 * writing each to the cache on the way past. A player who opens the map on their first day
	 * sees it fill in; one who has played a while sees it already there.
	 */
	public void prefetchAll() {
		if (this.prefetched) {
			return;
		}
		this.prefetched = true;
		this.supply.prefetchAll();
	}

	/** The banks, altars and fishing spots of everywhere whose scenery has arrived. */
	public MapIcons icons() {
		return this.icons;
	}

	/**
	 * Which square a loc file belongs to, by its file number.
	 *
	 * BUILT ONCE FROM THE INDEX, not searched for on every arrival: the index is a flat list of
	 * seven hundred and the obvious way round - ask it which file square x,z uses - is a scan
	 * of that list per square, which is half a million comparisons to answer a question that is
	 * one array read the other way round.
	 */
	private int[] squareOfLocFile() {
		if (this.squareOfLocFile == null) {
			int[] squares = this.supply.squares();
			int widest = 0;
			int[][] pairs = new int[squares.length][];
			for (int i = 0; i < squares.length; i++) {
				int x = squares[i] >> 8;
				int z = squares[i] & 0xFF;
				int file = this.supply.fileFor(x, z, 1);
				pairs[i] = new int[] { file, squares[i] };
				if (file > widest) {
					widest = file;
				}
			}
			int[] of = new int[widest + 1];
			for (int i = 0; i < of.length; i++) {
				of[i] = -1;
			}
			for (int i = 0; i < pairs.length; i++) {
				if (pairs[i][0] >= 0) {
					of[pairs[i][0]] = pairs[i][1];
				}
			}
			this.squareOfLocFile = of;
		}
		return this.squareOfLocFile;
	}

	/** Forgets every drawn square, for when the look of them changes under us. */
	public void forget() {
		this.drawn.clear();
		this.age.clear();
	}
}
