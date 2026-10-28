package jagex2.client.plugin.builtin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * What died, and what it left behind.
 *
 * HOW A KILL IS RECOGNISED, which is the whole problem. This client is never told that anything
 * died. There is no death event, no damage, no "who killed it" - a plugin sees the npcs in the
 * scene and the items on the floor, and has to work out the rest. So:
 *
 *   an npc was standing on this tile a moment ago, it is not standing there now, and loot has
 *   just landed on it.
 *
 * That is the whole rule. It is not as good as being told, and it is honest about where it is
 * wrong: a kill somebody ELSE made next to you looks exactly like one of yours, because nothing
 * in the scene says whose it was. What it does not do is invent kills out of nothing - an npc
 * that merely walks away leaves no loot, so it is never counted.
 *
 * A FEW TICKS OF MEMORY, not one. The npc leaves the scene and the drop appears within a tick or
 * two of each other, in an order that is not guaranteed, so a vacated tile is remembered for
 * {@link #VACATED_TICKS} and loot landing on it in that window is credited. Longer would start
 * crediting the next thing to die near that spot to whatever died there before.
 *
 * ONE KILL, HOWEVER MANY ITEMS. A drop of bones, coins and a herb is one kill and three items;
 * counting a kill per item would make every kill count a lie in exactly the direction that
 * flatters the player.
 *
 * BOUNDED, like GroundItemArrivals and for the same reason: this runs per tick for as long as a
 * client is open, and a player in a busy place should cost a fixed amount of memory rather than
 * however much happens to die near them.
 *
 * NO CLIENT, NO CACHE, NO AWT. Everything here is arithmetic over values it is handed, which is
 * what lets the rules be tested rather than described.
 */
public final class LootTracker {

	/** Sources remembered. Past this the least valuable is dropped to make room. */
	public static final int MAX_SOURCES = 64;

	/** Kinds of item remembered per source, on the same rule. */
	public static final int MAX_ITEMS = 64;

	/**
	 * How many ticks a vacated tile stays creditable.
	 *
	 * Three because the npc's removal and its drop do not arrive together: either can be a tick
	 * or so after the other depending on what else the server sent. Long enough to join the two,
	 * short enough that the next thing to die on that tile is not credited to the last one.
	 */
	public static final int VACATED_TICKS = 3;

	/** One kind of item, and how much of it has come from one source. */
	public static final class Item {

		public final int id;
		public final String name;
		public long count;
		public long value;

		Item(int id, String name) {
			this.id = id;
			this.name = name == null ? "" : name;
		}
	}

	/** One thing that has been dying, and everything it has left behind. */
	public static final class Source {

		public final String name;
		public int kills;
		public long value;
		final Map<Integer, Item> items = new HashMap<Integer, Item>();

		Source(String name) {
			this.name = name == null ? "" : name;
		}

		/** Its items, most valuable first. */
		public List<Item> items() {
			List<Item> out = new ArrayList<Item>(this.items.values());
			Collections.sort(out, BY_ITEM_VALUE);
			return out;
		}

		/** The single most valuable kind of item it has dropped, or null before it drops any. */
		public Item best() {
			Item best = null;
			for (Item item : this.items.values()) {
				if (best == null || item.value > best.value
					|| (item.value == best.value && item.name.compareTo(best.name) < 0)) {
					best = item;
				}
			}
			return best;
		}
	}

	/** A tile an npc has just left, and what was standing on it. */
	private static final class Vacated {

		final String name;
		int ticks;
		boolean counted;

		Vacated(String name, int ticks) {
			this.name = name;
			this.ticks = ticks;
		}
	}

	private final Map<Long, String> standing = new HashMap<Long, String>();
	private final Map<Long, String> previous = new HashMap<Long, String>();
	private final Map<Long, Vacated> vacated = new HashMap<Long, Vacated>();
	private final Map<String, Source> sources = new HashMap<String, Source>();
	private int unattributed;
	private long unattributedValue;

	/**
	 * One tile, as a long: 15 bits of x, 15 of z and 2 of plane.
	 *
	 * WORLD COORDINATES, not scene ones, for the reason GroundItemArrivals gives: the scene is
	 * 104 tiles that slide under the player, so a remembered scene tile means a different place
	 * after a walk and would credit a kill to whatever had died 104 tiles away.
	 */
	public static long tileKey(int worldX, int worldZ, int plane) {
		return ((long) (worldX & 0x7FFF) << 17)
			| ((long) (worldZ & 0x7FFF) << 2)
			| (long) (plane & 0x3);
	}

	// ------------------------------------------------------------------ the tick

	/** Starts a tick. Every tile an npc covers is declared between here and {@link #endTick()}. */
	public void beginTick() {
		this.standing.clear();
	}

	/** An npc covers this tile right now. */
	public void standing(long tile, String name) {
		if (name != null && name.length() > 0) {
			this.standing.put(Long.valueOf(tile), name);
		}
	}

	/**
	 * Closes the tick: ages what was already remembered, then remembers the tiles an npc was on
	 * last tick and is not on now.
	 *
	 * AGEING FIRST IS THE POINT. Age afterwards and a tile vacated this tick has already lost one
	 * of its ticks before any loot could land on it, which costs exactly the window this exists
	 * to provide.
	 */
	public void endTick() {
		Iterator<Map.Entry<Long, Vacated>> going = this.vacated.entrySet().iterator();
		while (going.hasNext()) {
			Vacated was = going.next().getValue();
			if (--was.ticks <= 0) {
				going.remove();
			}
		}
		for (Map.Entry<Long, String> entry : this.previous.entrySet()) {
			if (!this.standing.containsKey(entry.getKey())) {
				this.vacated.put(entry.getKey(), new Vacated(entry.getValue(), VACATED_TICKS));
			}
		}
		this.previous.clear();
		this.previous.putAll(this.standing);
	}

	/** What left this tile recently, or null if nothing did. */
	public String vacatedBy(long tile) {
		Vacated was = this.vacated.get(Long.valueOf(tile));
		return was == null ? null : was.name;
	}

	// ------------------------------------------------------------------ the loot

	/**
	 * A drop has landed. Returns what it was credited to, or null when nothing died there.
	 *
	 * VALUE IS COUNT TIMES PRICE, which is deliberately NOT what {@link
	 * jagex2.client.plugin.GroundItem#worth()} answers. That multiplies only for a stackable,
	 * because it is used to decide whether a PILE deserves a lootbeam and twenty swords on one
	 * tile are twenty separate swords rather than one prize. A loot tracker is adding up what a
	 * player walked away with, and twenty swords are worth twenty swords.
	 */
	public String record(long tile, int objId, String itemName, int count, int price) {
		if (count <= 0) {
			return null;
		}
		long worth = (long) count * (long) price;
		Vacated was = this.vacated.get(Long.valueOf(tile));
		if (was == null) {
			this.unattributed++;
			this.unattributedValue += worth;
			return null;
		}
		Source source = this.source(was.name);
		if (source == null) {
			return null;
		}
		if (!was.counted) {
			// ONE KILL, HOWEVER MANY ITEMS. The rest of this drop lands on the same tile in the
			// same tick and must not each be a kill of their own.
			was.counted = true;
			source.kills++;
		}
		source.value += worth;
		Item item = source.items.get(Integer.valueOf(objId));
		if (item == null) {
			if (source.items.size() >= MAX_ITEMS) {
				return source.name;
			}
			item = new Item(objId, itemName);
			source.items.put(Integer.valueOf(objId), item);
		}
		item.count += count;
		item.value += worth;
		return source.name;
	}

	/** The source by that name, making it if there is room. */
	private Source source(String name) {
		Source source = this.sources.get(name);
		if (source != null) {
			return source;
		}
		if (this.sources.size() >= MAX_SOURCES && !this.dropLeast()) {
			return null;
		}
		source = new Source(name);
		this.sources.put(name, source);
		return source;
	}

	/** Makes room by forgetting the least valuable source. False when there is nothing to drop. */
	private boolean dropLeast() {
		String worst = null;
		long worstValue = Long.MAX_VALUE;
		for (Source source : this.sources.values()) {
			if (source.value < worstValue) {
				worstValue = source.value;
				worst = source.name;
			}
		}
		if (worst == null) {
			return false;
		}
		this.sources.remove(worst);
		return true;
	}

	// ------------------------------------------------------------------ reading it back

	private static final Comparator<Source> BY_VALUE = new Comparator<Source>() {
		public int compare(Source a, Source b) {
			if (a.value != b.value) {
				return a.value > b.value ? -1 : 1;
			}
			return a.name.compareTo(b.name);
		}
	};

	private static final Comparator<Item> BY_ITEM_VALUE = new Comparator<Item>() {
		public int compare(Item a, Item b) {
			if (a.value != b.value) {
				return a.value > b.value ? -1 : 1;
			}
			return a.name.compareTo(b.name);
		}
	};

	/** Everything that has died, most valuable haul first. Ties go alphabetically, so it is stable. */
	public List<Source> bySource() {
		List<Source> out = new ArrayList<Source>(this.sources.values());
		Collections.sort(out, BY_VALUE);
		return out;
	}

	/** Every kind of item, from every source, added together and most valuable first. */
	public List<Item> byItem() {
		Map<Integer, Item> merged = new HashMap<Integer, Item>();
		for (Source source : this.sources.values()) {
			for (Item item : source.items.values()) {
				Item into = merged.get(Integer.valueOf(item.id));
				if (into == null) {
					into = new Item(item.id, item.name);
					merged.put(Integer.valueOf(item.id), into);
				}
				into.count += item.count;
				into.value += item.value;
			}
		}
		List<Item> out = new ArrayList<Item>(merged.values());
		Collections.sort(out, BY_ITEM_VALUE);
		return out;
	}

	/** Everything, from everything, added up. */
	public long total() {
		long total = 0;
		for (Source source : this.sources.values()) {
			total += source.value;
		}
		return total;
	}

	/** Total kills across every source. */
	public int kills() {
		int kills = 0;
		for (Source source : this.sources.values()) {
			kills += source.kills;
		}
		return kills;
	}

	/**
	 * Drops that landed where nothing had just died.
	 *
	 * SHOWN RATHER THAN SWALLOWED. Loot this could not attribute is loot missing from the totals,
	 * and a tracker that quietly leaves it out looks precise while being wrong. A player who can
	 * see the number knows how much to trust the rest.
	 */
	public int unattributed() {
		return this.unattributed;
	}

	/** What those unattributed drops were worth. */
	public long unattributedValue() {
		return this.unattributedValue;
	}

	/** Forgets one source. */
	public void forget(String name) {
		this.sources.remove(name);
	}

	/** Forgets the loot, but not where things are standing - the tick goes on. */
	public void reset() {
		this.sources.clear();
		this.unattributed = 0;
		this.unattributedValue = 0;
	}

	// ------------------------------------------------------------------ showing a number

	/**
	 * A value, short enough for a sidebar row: 999, 12,345, 1.2M.
	 *
	 * Exact up to ten thousand, because at that size the digits are the useful part and a player
	 * comparing two drops wants to see which was bigger. Past it the leading digits are what
	 * anybody reads and the rest is noise in a column 60 pixels wide.
	 */
	public static String gp(long value) {
		if (value < 0) {
			return "-" + gp(-value);
		}
		if (value < 10000) {
			return commas(value);
		}
		if (value < 1000000) {
			return tenths(value, 1000) + "K";
		}
		if (value < 1000000000L) {
			return tenths(value, 1000000) + "M";
		}
		return tenths(value, 1000000000L) + "B";
	}

	/** One decimal place, and no trailing ".0" - "1.2M", but "5M" rather than "5.0M". */
	private static String tenths(long value, long unit) {
		long whole = value / unit;
		long tenth = value % unit * 10 / unit;
		return tenth == 0 ? commas(whole) : commas(whole) + "." + tenth;
	}

	/** A number with thousands separators, without asking what locale the player is in. */
	public static String commas(long value) {
		if (value < 0) {
			return "-" + commas(-value);
		}
		String digits = Long.toString(value);
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < digits.length(); i++) {
			if (i > 0 && (digits.length() - i) % 3 == 0) {
				out.append(',');
			}
			out.append(digits.charAt(i));
		}
		return out.toString();
	}
}
