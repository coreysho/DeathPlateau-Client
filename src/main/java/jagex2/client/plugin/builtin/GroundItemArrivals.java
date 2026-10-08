package jagex2.client.plugin.builtin;

/**
 * Which ground items are NEW since the last look.
 *
 * Notifications and lootbeams both want the same thing and neither can get it from a pile: a pile
 * is what is on a tile right now, and "a drop just landed" is a difference between two moments.
 * So this remembers the last set and answers the difference.
 *
 * IDENTITY IS WORLD COORDINATES AND THE ITEM ID, not scene coordinates. The scene is 104 tiles
 * that slide under the player: walk far enough and every scene coordinate means a different place
 * than it did, so a remembered scene tile would report the whole floor as new drops on one tick
 * and miss real ones on the next. World coordinates do not move.
 *
 * COUNT IS NOT PART OF IT. Picking one coin off a stack of ten changes the count, and that is not
 * a new drop; neither is a kill adding to a stack already there. A notification per coin would be
 * worse than none, and this is the kind of thing that only shows up once a player stands in a
 * busy place.
 *
 * BOUNDED. It holds at most {@link #MAX} keys and forgets the rest, because the scan runs per
 * game tick and a player in a crowded spot should cost a fixed amount of work rather than however
 * much loot happens to be on the floor. Forgetting means an item may be reported new twice, which
 * is the right way round to be wrong: the alternative is growing without limit.
 */
public final class GroundItemArrivals {

	/**
	 * Keys remembered. A radius-12 scan covers 625 tiles, and this is past anything one of those
	 * holds in practice while staying a small fixed array.
	 */
	public static final int MAX = 2048;

	private long[] previous = new long[0];
	private long[] current = new long[MAX];
	private int count;

	/**
	 * One item's identity, as a long.
	 *
	 * A long rather than a string or an object because this is built per item per tick and
	 * compared against every remembered key: 15 bits of x, 15 of z, 2 of plane and the id. World
	 * coordinates in 377 fit 15 bits each with room to spare, and masking rather than trusting
	 * keeps a nonsense coordinate from colliding with a real one somewhere else.
	 */
	public static long key(int worldX, int worldZ, int plane, int id) {
		return ((long) (worldX & 0x7FFF) << 34)
			| ((long) (worldZ & 0x7FFF) << 19)
			| ((long) (plane & 0x3) << 17)
			| (long) (id & 0x1FFFF);
	}

	/** Starts a new scan. Everything added until {@link #finish()} is what is on the floor now. */
	public void begin() {
		this.count = 0;
	}

	/**
	 * Adds one item to this scan and answers whether it is new.
	 *
	 * New means "was not there at the end of the last scan". The first scan after a login or a
	 * reset reports nothing new, because everything already on the floor was not dropped while
	 * the player was watching - and a player walking into a loot pile does not want a dozen
	 * notifications for things that have been lying there.
	 */
	public boolean add(long key) {
		if (this.count < MAX) {
			this.current[this.count++] = key;
		}
		return this.previous.length > 0 && !contains(this.previous, key);
	}

	/** Ends the scan: what was added becomes what is remembered. */
	public void finish() {
		long[] kept = new long[this.count];
		System.arraycopy(this.current, 0, kept, 0, this.count);
		this.previous = kept;
	}

	/**
	 * Forgets everything, so the next scan reports nothing new.
	 *
	 * Called on a log out and on a region change. Without it, logging into somewhere else would
	 * report every item within sight as a fresh drop - the player would get a notification storm
	 * for a floor they just walked onto.
	 */
	public void reset() {
		this.previous = new long[0];
		this.count = 0;
	}

	/** How many keys the last finished scan remembered. For tests and for a sanity check. */
	public int remembered() {
		return this.previous.length;
	}

	/** Whether this scan has anything in it yet - so a first scan can be told from an empty one. */
	public boolean seenAnything() {
		return this.previous.length > 0;
	}

	private static boolean contains(long[] keys, long key) {
		for (int i = 0; i < keys.length; i++) {
			if (keys[i] == key) {
				return true;
			}
		}
		return false;
	}
}
