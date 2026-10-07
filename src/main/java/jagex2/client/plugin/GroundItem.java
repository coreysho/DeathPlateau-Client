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

	/** What ONE of them is worth. Multiply by count for the pile's worth. */
	public final int price;

	GroundItem(int id, String name, int count, int price) {
		this.id = id;
		this.name = name == null ? "" : name;
		this.count = count;
		this.price = price;
	}
}
