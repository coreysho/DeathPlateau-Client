package jagex2.client.plugin.builtin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a player has been given, kill by kill and source by source: the model behind RuneLite's
 * loot tracker, with no client in it.
 *
 * TOLD, NOT GUESSED. This used to work a kill out from an npc vanishing near a fresh pile on the
 * floor, and it was wrong in every way that inference can be: somebody else's kill next to yours
 * was yours, a second drop on a tile already holding the same item was nothing, coins merging into
 * a stack were nothing. The server now says what each kill dropped (LootReceived), so a kill here
 * is exactly a kill the game credited to this player and its items are exactly what it dropped.
 *
 * TWO VIEWS OF ONE HISTORY, as RuneLite has. Grouped, every source is one box - "Black dragon x 13"
 * and everything thirteen of them have dropped - and the box that just paid out moves to the top.
 * Per kill, every kill is its own box, newest first. Both are kept: the grouped totals for ever
 * (bounded), and the last {@link #MAX_KILLS} kills for the other view.
 *
 * VALUES ARE NOT STORED. What an item is worth is asked of a {@link Prices} when the page is drawn,
 * so changing between store value and high alchemy, or ignoring an item, changes every total at
 * once - including the ones from before the change - which is what RuneLite does with its prices.
 *
 * SAVED AS TEXT, read back as untrusted. {@link #save()} writes one line per source, kill and item;
 * {@link #load(String)} takes any of it that parses and skips the rest, so a file mangled by hand or
 * by a crash halfway through a write costs the lines that are broken and nothing else.
 *
 * NO CLIENT, NO CACHE, NO AWT, so every rule above is tested as arithmetic (LootTrackerTest).
 */
public final class LootTracker {

	/** Sources remembered. Past this the one longest unseen is forgotten to make room. */
	public static final int MAX_SOURCES = 250;

	/** Kinds of item remembered per source. */
	public static final int MAX_ITEMS = 200;

	/** Kills remembered one by one, for the per-kill view. The grouped totals do not depend on it. */
	public static final int MAX_KILLS = 100;

	/** What one of an item is worth, by obj id. Supplied by the plugin, from the cache. */
	public interface Prices {

		long value(int objId);
	}

	/** One kind of item and how many of it. */
	public static final class Item {

		public final int id;
		public long count;

		Item(int id, long count) {
			this.id = id;
			this.count = count;
		}
	}

	/** One kill, or one casket: what it was and what it dropped. */
	public static final class Kill {

		public final String type;
		public final String name;
		public final int level;
		public final long time;
		public final List<Item> items;

		Kill(String type, String name, int level, long time, List<Item> items) {
			this.type = type;
			this.name = name;
			this.level = level;
			this.time = time;
			this.items = items;
		}

		public String key() {
			return keyOf(this.type, this.name);
		}
	}

	/** Everything one source has dropped, added up. */
	public static final class Source {

		public final String type;
		public final String name;
		public int level;
		public int kills;
		public long lastTime;
		final Map<Integer, Item> items = new LinkedHashMap<Integer, Item>();

		Source(String type, String name) {
			this.type = type;
			this.name = name;
		}

		public String key() {
			return keyOf(this.type, this.name);
		}

		/** Its items in the order they were first dropped. Sorted for show by {@link #sorted}. */
		public List<Item> items() {
			return new ArrayList<Item>(this.items.values());
		}
	}

	/** Grouped by type and name, as RuneLite's boxes are: "Goblin" the npc and "Goblin" the player are two. */
	static String keyOf(String type, String name) {
		return type + "|" + name;
	}

	private final Map<String, Source> sources = new HashMap<String, Source>();
	private final List<Kill> kills = new ArrayList<Kill>();

	// ------------------------------------------------------------------ taking loot in

	/**
	 * One kill. Items with the same id are added together; a count of zero or less is ignored.
	 * Returns the source it went to, or null when there was no name to file it under.
	 */
	public Source record(String type, String name, int level, long time, List<Item> items) {
		if (name == null || name.trim().length() == 0) {
			return null;
		}
		String clean = name.trim();
		String kind = type == null || type.length() == 0 ? "npc" : type;
		Map<Integer, Item> merged = new LinkedHashMap<Integer, Item>();
		if (items != null) {
			for (Item item : items) {
				if (item == null || item.count <= 0 || item.id < 0) {
					continue;
				}
				Item into = merged.get(Integer.valueOf(item.id));
				if (into == null) {
					merged.put(Integer.valueOf(item.id), new Item(item.id, item.count));
				} else {
					into.count += item.count;
				}
			}
		}

		Source source = this.sources.get(keyOf(kind, clean));
		if (source == null) {
			if (this.sources.size() >= MAX_SOURCES) {
				this.dropOldest();
			}
			source = new Source(kind, clean);
			this.sources.put(source.key(), source);
		}
		source.kills++;
		source.lastTime = time;
		if (level > 0) {
			source.level = level;
		}
		for (Item item : merged.values()) {
			Item into = source.items.get(Integer.valueOf(item.id));
			if (into == null) {
				if (source.items.size() >= MAX_ITEMS) {
					continue;
				}
				source.items.put(Integer.valueOf(item.id), new Item(item.id, item.count));
			} else {
				into.count += item.count;
			}
		}

		this.kills.add(0, new Kill(kind, clean, level, time, new ArrayList<Item>(merged.values())));
		while (this.kills.size() > MAX_KILLS) {
			this.kills.remove(this.kills.size() - 1);
		}
		return source;
	}

	/** Makes room by forgetting the source that has gone longest without a kill. */
	private void dropOldest() {
		Source oldest = null;
		for (Source source : this.sources.values()) {
			if (oldest == null || source.lastTime < oldest.lastTime) {
				oldest = source;
			}
		}
		if (oldest != null) {
			this.forget(oldest.key());
		}
	}

	// ------------------------------------------------------------------ reading it back

	private static final Comparator<Source> NEWEST_FIRST = new Comparator<Source>() {
		public int compare(Source a, Source b) {
			if (a.lastTime != b.lastTime) {
				return a.lastTime > b.lastTime ? -1 : 1;
			}
			return a.name.compareTo(b.name);
		}
	};

	/** Every source, the one that paid out last at the top - where RuneLite moves a box that updates. */
	public List<Source> sources() {
		List<Source> out = new ArrayList<Source>(this.sources.values());
		Collections.sort(out, NEWEST_FIRST);
		return out;
	}

	/** The kills one by one, newest first. */
	public List<Kill> kills() {
		return Collections.unmodifiableList(this.kills);
	}

	public Source source(String key) {
		return this.sources.get(key);
	}

	/** Every kill there has been, from every source - not only the ones still listed one by one. */
	public int totalKills() {
		int total = 0;
		for (Source source : this.sources.values()) {
			total += source.kills;
		}
		return total;
	}

	/**
	 * Items sorted for show: most valuable stack first, as RuneLite's grid is, then by id so the
	 * order holds still between frames. Ignored items go to the end - shown faded, if at all.
	 */
	public static List<Item> sorted(List<Item> items, final Prices prices, final Ignored ignored) {
		List<Item> out = new ArrayList<Item>(items);
		Collections.sort(out, new Comparator<Item>() {
			public int compare(Item a, Item b) {
				boolean ia = ignored != null && ignored.is(a.id);
				boolean ib = ignored != null && ignored.is(b.id);
				if (ia != ib) {
					return ia ? 1 : -1;
				}
				long va = worth(a, prices);
				long vb = worth(b, prices);
				if (va != vb) {
					return va > vb ? -1 : 1;
				}
				return a.id - b.id;
			}
		});
		return out;
	}

	/** Which items the player has said to leave out of the totals. */
	public interface Ignored {

		boolean is(int objId);
	}

	/** What a stack is worth: count times one of it. Twenty swords are worth twenty swords. */
	public static long worth(Item item, Prices prices) {
		return prices == null ? 0 : item.count * prices.value(item.id);
	}

	/** What a list of items is worth, less the ignored ones. */
	public static long total(List<Item> items, Prices prices, Ignored ignored) {
		long total = 0;
		for (Item item : items) {
			if (ignored == null || !ignored.is(item.id)) {
				total += worth(item, prices);
			}
		}
		return total;
	}

	/** Everything ever, less what is ignored and the sources that are hidden. */
	public long total(Prices prices, Ignored ignored, java.util.Set<String> hidden) {
		long total = 0;
		for (Source source : this.sources.values()) {
			if (hidden == null || !hidden.contains(source.name.toLowerCase())) {
				total += total(source.items(), prices, ignored);
			}
		}
		return total;
	}

	// ------------------------------------------------------------------ forgetting

	/** Forgets one source, and its kills from the per-kill list with it. */
	public void forget(String key) {
		this.sources.remove(key);
		for (int i = this.kills.size() - 1; i >= 0; i--) {
			if (this.kills.get(i).key().equals(key)) {
				this.kills.remove(i);
			}
		}
	}

	/** Forgets everything. */
	public void reset() {
		this.sources.clear();
		this.kills.clear();
	}

	public boolean isEmpty() {
		return this.sources.isEmpty();
	}

	// ------------------------------------------------------------------ the file

	static final String VERSION = "loot 1";

	/**
	 * The whole history as text. One line each:
	 *
	 * <pre>
	 *   S|type|kills|lastTime|level|name     a source; its items follow
	 *   K|type|time|level|name               a kill; its items follow
	 *   I|id|count                           an item of the line above
	 * </pre>
	 *
	 * The name goes last so a name with a bar in it still reads back.
	 */
	public String save() {
		StringBuilder out = new StringBuilder(4096);
		out.append(VERSION).append('\n');
		for (Source source : this.sources()) {
			out.append("S|").append(source.type).append('|').append(source.kills).append('|')
				.append(source.lastTime).append('|').append(source.level).append('|')
				.append(oneLine(source.name)).append('\n');
			items(out, source.items.values());
		}
		for (Kill kill : this.kills) {
			out.append("K|").append(kill.type).append('|').append(kill.time).append('|')
				.append(kill.level).append('|').append(oneLine(kill.name)).append('\n');
			items(out, kill.items);
		}
		return out.toString();
	}

	private static void items(StringBuilder out, Iterable<Item> items) {
		for (Item item : items) {
			out.append("I|").append(item.id).append('|').append(item.count).append('\n');
		}
	}

	private static String oneLine(String text) {
		return text.replace('\n', ' ').replace('\r', ' ');
	}

	/** Replaces this history with one read back from {@link #save()}. Whatever does not parse is skipped. */
	public void load(String text) {
		this.reset();
		if (text == null || !text.startsWith(VERSION)) {
			return;
		}
		String[] lines = text.split("\n");
		Source source = null;
		List<Item> killItems = null;
		List<Kill> read = new ArrayList<Kill>();
		for (int i = 1; i < lines.length; i++) {
			String line = lines[i].trim();
			try {
				if (line.startsWith("S|")) {
					String[] f = line.split("\\|", 6);
					killItems = null;
					source = null;
					if (f.length < 6 || f[5].length() == 0 || this.sources.size() >= MAX_SOURCES) {
						continue;
					}
					source = new Source(f[1], f[5]);
					source.kills = Math.max(0, Integer.parseInt(f[2]));
					source.lastTime = Long.parseLong(f[3]);
					source.level = Math.max(0, Integer.parseInt(f[4]));
					this.sources.put(source.key(), source);
				} else if (line.startsWith("K|")) {
					String[] f = line.split("\\|", 5);
					source = null;
					killItems = null;
					if (f.length < 5 || f[4].length() == 0 || read.size() >= MAX_KILLS) {
						continue;
					}
					killItems = new ArrayList<Item>();
					read.add(new Kill(f[1], f[4], Math.max(0, Integer.parseInt(f[3])), Long.parseLong(f[2]), killItems));
				} else if (line.startsWith("I|")) {
					String[] f = line.split("\\|", 3);
					if (f.length < 3) {
						continue;
					}
					int id = Integer.parseInt(f[1]);
					long count = Long.parseLong(f[2]);
					if (id < 0 || count <= 0) {
						continue;
					}
					if (source != null && source.items.size() < MAX_ITEMS) {
						Item had = source.items.get(Integer.valueOf(id));
						if (had == null) {
							source.items.put(Integer.valueOf(id), new Item(id, count));
						} else {
							had.count += count;
						}
					} else if (killItems != null && killItems.size() < MAX_ITEMS) {
						killItems.add(new Item(id, count));
					}
				}
			} catch (NumberFormatException broken) {
				// One bad line, not a lost history.
			}
		}
		this.kills.addAll(read);
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

	/** "just now", "4 minutes ago", "3 hours ago", "2 days ago" - for a kill's box in the per-kill view. */
	public static String ago(long then, long now) {
		long seconds = Math.max(0, (now - then) / 1000);
		if (seconds < 60) {
			return "just now";
		}
		long minutes = seconds / 60;
		if (minutes < 60) {
			return minutes + (minutes == 1 ? " minute ago" : " minutes ago");
		}
		long hours = minutes / 60;
		if (hours < 24) {
			return hours + (hours == 1 ? " hour ago" : " hours ago");
		}
		long days = hours / 24;
		return days + (days == 1 ? " day ago" : " days ago");
	}
}
