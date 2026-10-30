/*
 * Headless test for the loot tracker.
 *
 * WHAT THIS GUARDS. The client is never told that anything died, so the kill is INFERRED: an npc
 * was standing on a tile, it is not standing there now, and loot has landed on it. Every part of
 * that inference is a rule nobody can see working. A tracker whose window is a tick too short
 * credits nothing and looks like a tracker with nothing to report; one a tick too long credits
 * the next kill to whatever died there before; one that counts a kill per ITEM reports three
 * kills for one drop of bones, coins and a herb - and all three failures produce a page full of
 * plausible numbers that nobody can check against anything.
 *
 * So the rules are a model with no client in it ({@link LootTracker}), and they are tested as
 * arithmetic: tiles in, loot in, totals out. The plugin on top is wiring, and the one thing in it
 * that cannot be checked by running it - that the npcs are read BEFORE the floor, without which
 * no kill is ever credited - is read out of the source, the way JaggrabTest reads openUrl.
 *
 * THE VALUE RULE IS DELIBERATELY NOT GroundItem.worth(). That multiplies only for a stackable,
 * because it decides whether a PILE deserves a lootbeam. A tracker is adding up what a player
 * walked away with, so twenty swords are worth twenty swords, and this is asserted rather than
 * assumed because the two sitting side by side invite someone to "fix" one of them.
 */
package jagex2.client.plugin.builtin;

import java.util.List;

public class LootTrackerTest {

	static int fails;

	static void check(boolean ok, String what) {
		System.out.println((ok ? "  ok   " : "FAIL   ") + what);
		if (!ok) {
			fails++;
		}
	}

	public static void main(String[] args) {
		System.out.println("1. what counts as a kill");
		killTests();
		System.out.println();
		System.out.println("2. how long a tile stays creditable");
		windowTests();
		System.out.println();
		System.out.println("3. one kill, however many items");
		dropTests();
		System.out.println();
		System.out.println("4. what a drop is worth");
		valueTests();
		System.out.println();
		System.out.println("5. tiles are told apart, including by plane");
		tileTests();
		System.out.println();
		System.out.println("6. reading it back");
		readingTests();
		System.out.println();
		System.out.println("7. it does not grow without limit");
		boundsTests();
		System.out.println();
		System.out.println("8. numbers a sidebar row has space for");
		formatTests();
		System.out.println();
		System.out.println("9. the page");
		pageTests();
		System.out.println();
		System.out.println("10. the order of the tick, which no run of it can state");
		orderTests();

		System.out.println();
		if (fails == 0) {
			System.out.println("all checks passed");
		} else {
			System.out.println(fails + " CHECK(S) FAILED");
		}
		System.exit(fails == 0 ? 0 : 1);
	}

	static final long T = LootTracker.tileKey(3200, 3200, 0);
	static final long U = LootTracker.tileKey(3201, 3200, 0);

	/** A tick with one npc standing on one tile. */
	static void tickWith(LootTracker loot, long tile, String name) {
		loot.beginTick();
		loot.standing(tile, name);
		loot.endTick();
	}

	/** A tick with nothing in the scene at all. */
	static void emptyTick(LootTracker loot) {
		loot.beginTick();
		loot.endTick();
	}

	// ------------------------------------------------------------------ 1. the kill

	static void killTests() {
		LootTracker loot = new LootTracker();
		tickWith(loot, T, "Goblin");
		emptyTick(loot);
		check("Goblin".equals(loot.record(T, 526, "Bones", 1, 100)),
			"an npc that was standing here, is not now, and left loot is a kill");
		check(loot.kills() == 1, "...counted once");
		check(loot.unattributed() == 0, "...and not counted as unattributed as well");

		// Still alive. The commonest way to be wrong, because loot does land next to living npcs.
		LootTracker alive = new LootTracker();
		tickWith(alive, T, "Goblin");
		tickWith(alive, T, "Goblin");
		check(alive.record(T, 526, "Bones", 1, 100) == null,
			"an npc still standing there has not died, whatever lands on it");
		check(alive.kills() == 0 && alive.unattributed() == 1, "...so that drop is unattributed");

		// Walked off. Nothing died, and nothing may be invented.
		LootTracker walked = new LootTracker();
		tickWith(walked, T, "Goblin");
		tickWith(walked, U, "Goblin");
		check(walked.kills() == 0, "an npc that walks away is not a kill");
		check(walked.vacatedBy(T) != null, "...though the tile it left is remembered");
		check(walked.vacatedBy(U) == null, "...and the one it walked onto is not");

		// Never there at all.
		LootTracker never = new LootTracker();
		emptyTick(never);
		check(never.record(T, 995, "Coins", 50, 1) == null, "loot where nothing has been is nobody's");
		check(never.unattributed() == 1 && never.unattributedValue() == 50,
			"...and is counted and valued separately rather than dropped");

		// The loot has to land where the thing died, not merely nearby.
		LootTracker elsewhere = new LootTracker();
		tickWith(elsewhere, T, "Goblin");
		emptyTick(elsewhere);
		check(elsewhere.record(U, 526, "Bones", 1, 100) == null, "loot on the next tile over is not its drop");
	}

	// ------------------------------------------------------------------ 2. the window

	/**
	 * How many ticks the window is, written down here as well as in LootTracker.
	 *
	 * DELIBERATELY A SECOND COPY, for the reason GameStateTest pins the inventory component: the
	 * first version of this read VACATED_TICKS and checked the behaviour against it, so moving
	 * the constant moved the expectation with it and a window of two - or of ten - passed clean.
	 * A test that reads the number it is checking cannot disagree with it.
	 */
	static final int WINDOW = 3;

	static void windowTests() {
		check(LootTracker.VACATED_TICKS == WINDOW,
			"the window is " + WINDOW + " ticks (it says " + LootTracker.VACATED_TICKS + ")");

		// The drop and the removal do not arrive together, so the tile stays creditable for a
		// few ticks. Checked at every tick of the window and the first one past it, because
		// "about three" is how a window ends up two or four.
		for (int wait = 0; wait <= WINDOW; wait++) {
			LootTracker loot = new LootTracker();
			tickWith(loot, T, "Goblin");
			emptyTick(loot);                      // the tick it vanished
			for (int i = 0; i < wait; i++) {
				emptyTick(loot);
			}
			boolean credited = "Goblin".equals(loot.record(T, 526, "Bones", 1, 100));
			boolean shouldBe = wait < WINDOW;
			check(credited == shouldBe, "loot " + wait + " ticks after it vanished is "
				+ (shouldBe ? "still credited" : "too late to credit"));
		}

		// And the tile is clear afterwards, rather than crediting the next thing to die there.
		LootTracker later = new LootTracker();
		tickWith(later, T, "Goblin");
		for (int i = 0; i <= WINDOW; i++) {
			emptyTick(later);
		}
		check(later.vacatedBy(T) == null, "and the tile is forgotten once the window is up");
	}

	// ------------------------------------------------------------------ 3. one kill, many items

	static void dropTests() {
		LootTracker loot = new LootTracker();
		tickWith(loot, T, "Goblin");
		emptyTick(loot);
		loot.record(T, 526, "Bones", 1, 100);
		loot.record(T, 995, "Coins", 40, 1);
		loot.record(T, 199, "Grimy guam", 1, 300);
		check(loot.kills() == 1, "bones, coins and a herb off one goblin is ONE kill");
		List<LootTracker.Source> sources = loot.bySource();
		check(sources.size() == 1 && sources.get(0).items().size() == 3, "...and three kinds of item");
		check(sources.get(0).value == 440, "...worth the three of them added up");

		// A second goblin dying on the same tile later is a second kill.
		tickWith(loot, T, "Goblin");
		emptyTick(loot);
		loot.record(T, 526, "Bones", 1, 100);
		check(loot.kills() == 2, "a second goblin dying on the same tile is a second kill");
		check(loot.bySource().get(0).items().size() == 3, "...and its bones join the bones already there");
		LootTracker.Item bones = null;
		for (LootTracker.Item item : loot.bySource().get(0).items()) {
			if (item.id == 526) {
				bones = item;
			}
		}
		check(bones != null && bones.count == 2, "...as two of them rather than two rows");
	}

	// ------------------------------------------------------------------ 4. value

	static void valueTests() {
		LootTracker loot = new LootTracker();
		tickWith(loot, T, "Goblin");
		emptyTick(loot);
		loot.record(T, 995, "Coins", 100, 1);
		check(loot.total() == 100, "a hundred coins at a gp each is a hundred");

		LootTracker swords = new LootTracker();
		tickWith(swords, T, "Knight");
		emptyTick(swords);
		swords.record(T, 1277, "Bronze sword", 20, 500);
		// GroundItem.worth() answers 500 here, and is right to for its own purpose: it decides
		// whether a PILE is worth a lootbeam. This is a different question.
		check(swords.total() == 10000,
			"twenty unstackable swords are worth twenty swords, not one");

		LootTracker nothing = new LootTracker();
		tickWith(nothing, T, "Goblin");
		emptyTick(nothing);
		check(nothing.record(T, 526, "Bones", 0, 100) == null, "a drop of nothing is not a drop");
		check(nothing.kills() == 0, "...and does not count a kill");
	}

	// ------------------------------------------------------------------ 5. tiles

	static void tileTests() {
		check(LootTracker.tileKey(3200, 3200, 0) == LootTracker.tileKey(3200, 3200, 0),
			"the same tile is the same key");
		check(LootTracker.tileKey(3200, 3200, 0) != LootTracker.tileKey(3201, 3200, 0),
			"one tile east is a different key");
		check(LootTracker.tileKey(3200, 3200, 0) != LootTracker.tileKey(3200, 3201, 0),
			"one tile north is a different key");
		check(LootTracker.tileKey(3200, 3200, 0) != LootTracker.tileKey(3200, 3200, 1),
			"the same tile upstairs is a different key");

		// A SWEEP OVER DISTINCT TILES, not just over planes of the same one. A packing whose x
		// and z overlap still gives different answers for the four tiles anybody checks by hand
		// and collides a few tiles away, which credits a kill to something that died in another
		// room. Every one of 16,384 real tiles has to have a key of its own.
		java.util.Set<Long> keys = new java.util.HashSet<Long>();
		int clashes = 0;
		for (int x = 0; x < 64; x++) {
			for (int z = 0; z < 64; z++) {
				for (int plane = 0; plane < 4; plane++) {
					if (!keys.add(Long.valueOf(LootTracker.tileKey(3000 + x, 3000 + z, plane)))) {
						clashes++;
					}
				}
			}
		}
		check(clashes == 0, "and no two of 16,384 tiles across four planes share a key");
		check(keys.size() == 64 * 64 * 4, "...which is 16,384 different keys");

		// The floor above is not the floor below, which a tracker that ignored plane would miss.
		LootTracker loot = new LootTracker();
		tickWith(loot, LootTracker.tileKey(3200, 3200, 0), "Goblin");
		emptyTick(loot);
		check(loot.record(LootTracker.tileKey(3200, 3200, 1), 526, "Bones", 1, 100) == null,
			"a drop one floor up is not the goblin that died below it");
	}

	// ------------------------------------------------------------------ 6. reading it back

	static void readingTests() {
		LootTracker loot = new LootTracker();
		drop(loot, T, "Goblin", 526, "Bones", 1, 100);
		drop(loot, T, "Hill giant", 532, "Big bones", 1, 500);
		drop(loot, T, "Hill giant", 526, "Bones", 1, 100);

		List<LootTracker.Source> sources = loot.bySource();
		check(sources.size() == 2, "two things have died");
		check("Hill giant".equals(sources.get(0).name), "the richer one is first");
		check(sources.get(0).value == 600 && sources.get(1).value == 100, "...with its own total");
		check(loot.total() == 700, "and the totals add up");
		check(loot.kills() == 3, "three kills between them");

		// The item view merges the same item across different sources, which is the whole reason
		// it is a second view rather than a sorted version of the first.
		List<LootTracker.Item> items = loot.byItem();
		check(items.size() == 2, "two kinds of item in all");
		check("Big bones".equals(items.get(0).name), "the more valuable kind first");
		LootTracker.Item plain = items.get(1);
		check("Bones".equals(plain.name) && plain.count == 2,
			"and bones from two different sources are two bones, not two rows");

		check("Big bones".equals(sources.get(0).best().name), "a source knows its best drop");
		check(loot.bySource().get(1).best() != null, "...and so does the other one");

		loot.forget("Hill giant");
		check(loot.bySource().size() == 1 && loot.total() == 100, "forgetting one leaves the other");
		loot.reset();
		check(loot.bySource().isEmpty() && loot.total() == 0 && loot.kills() == 0, "and a reset clears it");
	}

	/** A kill on a tile, with one item off it. */
	static void drop(LootTracker loot, long tile, String who, int id, String item, int count, int price) {
		tickWith(loot, tile, who);
		emptyTick(loot);
		loot.record(tile, id, item, count, price);
	}

	// ------------------------------------------------------------------ 7. bounds

	static void boundsTests() {
		LootTracker loot = new LootTracker();
		// Every source worth more than the last, then one more. The cheapest must be the one
		// that goes, or a long session ends up remembering the first 64 things it ever saw.
		for (int i = 0; i < LootTracker.MAX_SOURCES; i++) {
			drop(loot, T, "Thing " + i, 995, "Coins", 1, 100 + i);
		}
		check(loot.bySource().size() == LootTracker.MAX_SOURCES, "it holds as many sources as it says");
		drop(loot, T, "One more", 995, "Coins", 1, 9999);
		check(loot.bySource().size() == LootTracker.MAX_SOURCES, "...and no more than that");
		boolean kept = false;
		boolean cheapestGone = true;
		for (LootTracker.Source source : loot.bySource()) {
			if ("One more".equals(source.name)) {
				kept = true;
			}
			if ("Thing 0".equals(source.name)) {
				cheapestGone = false;
			}
		}
		check(kept, "the new one is kept");
		check(cheapestGone, "...and the least valuable one is what made room for it");

		LootTracker many = new LootTracker();
		tickWith(many, T, "Hoarder");
		emptyTick(many);
		for (int i = 0; i < LootTracker.MAX_ITEMS + 20; i++) {
			many.record(T, 1000 + i, "Item " + i, 1, 10);
		}
		check(many.bySource().get(0).items().size() == LootTracker.MAX_ITEMS,
			"and a single source holds a bounded number of item kinds");
		check(many.kills() == 1, "...still off one kill");
	}

	// ------------------------------------------------------------------ 8. formatting

	static void formatTests() {
		check("0".equals(LootTracker.gp(0)), "nothing is 0");
		check("999".equals(LootTracker.gp(999)), "small numbers are themselves");
		check("9,999".equals(LootTracker.gp(9999)), "...with separators, up to ten thousand");
		check("10K".equals(LootTracker.gp(10000)), "ten thousand is where it shortens");
		check("12.3K".equals(LootTracker.gp(12345)), "...to one decimal place");
		check("999.9K".equals(LootTracker.gp(999999)), "...up to a million");
		check("1M".equals(LootTracker.gp(1000000)), "a round million has no trailing .0");
		check("1.2M".equals(LootTracker.gp(1234567)), "and an unround one has a tenth");
		check("18.1M".equals(LootTracker.gp(18141940)), "a real drop reads plainly");
		check("1B".equals(LootTracker.gp(1000000000L)), "and a billion is a billion");
		check("2.1B".equals(LootTracker.gp(2147483647L)), "...past what an int could hold");
		check("-5,500".equals(LootTracker.gp(-5500)),
			"a negative under the threshold keeps its sign and its separators");
		check("-12.3K".equals(LootTracker.gp(-12345)), "...and one over it keeps its sign too");

		check("0".equals(LootTracker.commas(0)), "commas: zero");
		check("100".equals(LootTracker.commas(100)), "commas: under a thousand, none");
		check("1,000".equals(LootTracker.commas(1000)), "commas: a thousand, one");
		check("1,000,000".equals(LootTracker.commas(1000000)), "commas: a million, two");
		check("-1,000".equals(LootTracker.commas(-1000)), "commas: and a negative keeps its sign");
	}

	// ------------------------------------------------------------------ 9. the page

	static void pageTests() {
		LootTrackerPlugin plugin = new LootTrackerPlugin();
		check(plugin.rowCount() == 0, "an empty tracker has no rows, so the page shows its message");

		LootTracker loot = plugin.tracker();
		drop(loot, T, "Goblin", 526, "Bones", 1, 100);
		drop(loot, T, "Hill giant", 532, "Big bones", 1, 500);

		// Two sources, a total, and no unattributed row because there is nothing unattributed.
		check(plugin.rowCount() == 3, "two sources and a total");
		// "Zulrah x 13": the kill count rides with the name, where the eye already is, leaving
		// the value column for the gp.
		check(plugin.label(0).indexOf("Hill giant") == 0 && plugin.label(1).indexOf("Goblin") == 0,
			"the richer source is the first row");
		check(plugin.label(0).indexOf("x 1") > 0, "...and its name carries the kill count");
		check("500".equals(plugin.value(0)), "a row shows what that source gave");
		check("Total".equals(plugin.label(2)) && "600".equals(plugin.value(2)), "and the last row totals them");
		check("2 kills".equals(plugin.detail(2)), "...with the kill count under it");
		check(plugin.detail(0).indexOf("1 kill") == 0, "one kill is singular");
		// The best drop used to be named here. It is a picture under the row now, so saying it
		// in words as well would be the same thing twice in a column this narrow.
		check(plugin.detail(0).indexOf("Big bones") < 0,
			"...and does not also name the best drop, which is drawn under the row");

		// The unattributed row only exists when there is something in it, and it is IN the list
		// rather than somewhere a player reading the total would miss it.
		loot.record(LootTracker.tileKey(9000, 9000, 0), 995, "Coins", 7, 1);
		check(plugin.rowCount() == 4, "a drop nothing was standing on adds a row");
		check(plugin.label(3).indexOf("Nothing was standing") == 0, "...saying so");
		check("7".equals(plugin.value(3)), "...and what it was worth");
		check(plugin.detail(3).indexOf("1 drops") == 0, "...and how many there were");
		plugin.showUnattributed = false;
		check(plugin.rowCount() == 3, "which can be turned off");
		plugin.showUnattributed = true;

		// The item view is the same data counted the other way.
		plugin.groupBy = "Item";
		check(plugin.rowCount() == 4, "by item: two kinds, a total and the unattributed row");
		check("Big bones".equals(plugin.label(0)), "the more valuable kind leads");
		check(plugin.detail(0).indexOf("1 of them") == 0, "...and a row counts them");
		check("Total".equals(plugin.label(2)), "the total is still there");
		plugin.groupBy = "Source";

		// THE DROPS AS A GRID, which is what this page was sent back to do: a row names the
		// source and the kills, and the drops themselves are pictures under it. The icons need
		// a cache to draw, which a headless run has none of, so what is checked here is the
		// shape - one cell per kind of item, richest first, nothing under a total row.
		check(plugin.gridItems(0) != null && plugin.gridItems(0).size() == 1,
			"a source's row carries one cell per kind of item it dropped");
		drop(loot, T, "Hill giant", 526, "Bones", 1, 100);
		drop(loot, T, "Hill giant", 995, "Coins", 50, 1);
		check(plugin.gridItems(0).size() == 3, "...growing as it drops more kinds");
		check(plugin.gridItems(plugin.rowCount() - 1) == null, "the total row carries no drops");
		check(plugin.gridItems(99) == null, "nor does a row that is not there");


		// Removing a row forgets that source, and only in the view where a row IS a source.
		plugin.forget(0);
		check(plugin.sources().size() == 1 && plugin.label(0).indexOf("Goblin") == 0,
			"removing a row forgets that source");
		plugin.groupBy = "Item";
		plugin.forget(0);
		check(plugin.sources().size() == 1, "...and does nothing in the item view, where a row is not one");
		plugin.groupBy = "Source";

		// A haul of a hundred kinds must not push the rest of the page off the bottom. Last,
		// because filling a source up reorders the page and the checks above are about order.
		for (int i = 0; i < LootTrackerPlugin.MAX_ICONS + 5; i++) {
			loot.record(T, 2000 + i, "Thing " + i, 1, 5);
		}
		check(plugin.gridItems(0).size() <= LootTrackerPlugin.MAX_ICONS,
			"and a row stops at " + LootTrackerPlugin.MAX_ICONS + " of them");
		check(plugin.detail(0).indexOf("showing") > 0, "...saying so when it does");
	}

	// ------------------------------------------------------------------ 10. the order

	static void orderTests() {
		// THE ONE THING A RUN CANNOT STATE. The npc's removal and its drop share a tick, so the
		// tiles it left have to be worked out before the floor is read. The other way round, the
		// loot arrives at a tile not yet marked vacated and EVERY kill goes uncredited - a
		// tracker that works perfectly and reports nothing.
		String src = read("src/main/java/jagex2/client/plugin/builtin/LootTrackerPlugin.java");
		int tick = src.indexOf("public void onGameTick(");
		String body = tick < 0 ? "" : src.substring(tick, src.indexOf("\n\t}", tick));
		int npcs = body.indexOf("this.readNpcs()");
		int floor = body.indexOf("this.readFloor()");
		check(npcs >= 0 && floor >= 0, "the tick reads the npcs and the floor");
		check(npcs < floor, "...and the npcs first, or nothing is ever credited");

		// And the line the API is drawn on: this plugin reads and draws, it does not act.
		check(src.indexOf("consume()") < 0, "the tracker does not consume anything the player clicked");
		check(src.indexOf("setLeftClick") < 0 && src.indexOf("swapMenuEntries") < 0,
			"...nor reorder their menu");
	}

	/** A source file, for the checks that are about what the source says. */
	static String read(String path) {
		try {
			byte[] bytes = java.nio.file.Files.readAllBytes(
				java.nio.file.Paths.get(System.getProperty("dp.root", "."), path));
			return new String(bytes, "UTF-8");
		} catch (Throwable missing) {
			return "";
		}
	}
}
