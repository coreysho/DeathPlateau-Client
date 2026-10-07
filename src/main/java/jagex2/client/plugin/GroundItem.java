package jagex2.client.plugin;

/**
 * One kind of item lying on one tile, with however many of it are there.
 *
 * ALREADY MERGED. The server sends one object per drop, so three separate sets of bones on a
 * tile arrive as three of them. Showing that as three identical rows is noise, so the count here
 * is the total and a plugin does not have to do the merging - nor know that objStacks is a
 * LinkList walked tail-first.
 */
public final class GroundItem {

	public final int id;

	/** Its name, or "" for an item the cache has no name for. */
	public final String name;

	/** How many are on the tile, counting every stack of this id. */
	public final int count;

	/** What ONE of them is worth. */
	public final int price;

	/**
	 * Whether a pile of these is worth count times price.
	 *
	 * Only a stackable item's worth multiplies: twenty swords on a tile are twenty separate
	 * swords, and calling that one pile worth twenty times a sword is how a plugin ends up
	 * disagreeing with what the game would pay for them.
	 */
	public final boolean stackable;

	/**
	 * Public so a plugin can build one - to filter or reshape a pile before drawing it, and so a
	 * test can make the half-dozen items a rule needs to be checked against without a cache to
	 * decode them out of.
	 */
	public GroundItem(int id, String name, int count, int price, boolean stackable) {
		this.id = id;
		this.name = name == null ? "" : name;
		this.count = count;
		this.price = price;
		this.stackable = stackable;
	}

	/** What everything of this kind on the tile is worth together. */
	public long worth() {
		return this.stackable ? (long) this.count * (long) this.price : this.price;
	}
}
