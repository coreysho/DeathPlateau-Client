/*
 * Headless test for the loot tracker.
 *
 * WHAT THIS GUARDS. The server now says what each kill dropped (LootReceived, carried in a hidden
 * game message LootMessage reads back), so the tracker no longer has to guess a kill - but every
 * part of what it does with one is still a rule nobody can see working. A kill counted per item
 * reports three kills for one drop; a merge that overwrites instead of adding turns 4,000 coins into
 * the last 12; a save that drops a line forgets a boss; an ignored item still in the total makes the
 * total a lie; a split loot line read as two kills doubles the count. Each of those produces a page
 * full of plausible numbers that nobody can check against anything.
 *
 * So the rules are a model with no client in it (LootTracker), the wire is a parser with no client
 * in it (LootMessage), and both are tested as arithmetic. The page the plugin lays out is tested
 * without a client too: with no context every item is worth 0 and nothing is ignored, which is
 * enough to see which rows there are, in what order, and what the buttons do.
 */
package jagex2.client.plugin.builtin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jagex2.client.plugin.LootMessage;
import jagex2.client.plugin.event.LootReceived;

public class LootTrackerTest {

	static int fails;

	static void check(boolean ok, String what) {
		System.out.println((ok ? "  ok   " : "FAIL   ") + what);
		if (!ok) {
			fails++;
		}
	}

	static List<LootTracker.Item> items(long... idCount) {
		List<LootTracker.Item> out = new ArrayList<LootTracker.Item>();
		for (int i = 0; i + 1 < idCount.length; i += 2) {
			out.add(new LootTracker.Item((int) idCount[i], idCount[i + 1]));
		}
		return out;
	}

	static long count(List<LootTracker.Item> items, int id) {
		long n = 0;
		for (LootTracker.Item item : items) {
			if (item.id == id) {
				n += item.count;
			}
		}
		return n;
	}

	/** Coins 1 each, a sword 100, bones nothing: enough to tell an order and a total apart. */
	static final LootTracker.Prices PRICES = new LootTracker.Prices() {
		public long value(int objId) {
			return objId == 995 ? 1 : objId == 1333 ? 100 : 0;
		}
	};

	public static void main(String[] args) {
		System.out.println("1. a kill");
		kills();
		System.out.println();
		System.out.println("2. what it is worth");
		values();
		System.out.println();
		System.out.println("3. kept between sessions");
		saving();
		System.out.println();
		System.out.println("4. the line from the server");
		wire();
		System.out.println();
		System.out.println("5. the page");
		page();
		System.out.println();
		System.out.println("6. the numbers on it");
		numbers();
		System.out.println();
		if (fails > 0) {
			System.out.println(fails + " FAILED");
			System.exit(1);
		}
		System.out.println("all passed");
	}

	static void kills() {
		LootTracker t = new LootTracker();
		t.record("npc", "Goblin", 2, 1000, items(526, 1, 995, 5, 995, 7));
		LootTracker.Source goblin = t.source(LootTracker.keyOf("npc", "Goblin"));
		check(goblin != null && goblin.kills == 1, "one drop of three things is one kill");
		check(goblin != null && count(goblin.items(), 995) == 12, "two stacks of coins in one drop are 12 coins");
		check(t.kills().size() == 1 && count(t.kills().get(0).items, 995) == 12, "and the kill itself says 12");

		t.record("npc", "Goblin", 2, 2000, items(995, 4000));
		check(goblin.kills == 2, "a second goblin is a second kill of the same box");
		check(count(goblin.items(), 995) == 4012, "its coins are added to the first goblin's, not put in their place");

		t.record("npc", "Cow", 2, 3000, items(526, 1));
		check(t.sources().get(0).name.equals("Cow"), "the box that paid out last is at the top");
		t.record("npc", "Goblin", 2, 4000, items(526, 1));
		check(t.sources().get(0).name.equals("Goblin"), "and moves there again when it pays out again");
		check(t.kills().get(0).name.equals("Goblin") && t.kills().get(1).name.equals("Cow"),
			"the per-kill list is newest first");
		check(t.totalKills() == 4, "four kills in all");

		t.record("player", "Goblin", 3, 5000, items(995, 1));
		check(t.sources().size() == 3, "a player called Goblin is not the npc Goblin's box");

		t.record("npc", "Chicken", 1, 6000, null);
		LootTracker.Source chicken = t.source(LootTracker.keyOf("npc", "Chicken"));
		check(chicken != null && chicken.kills == 1 && chicken.items().isEmpty(),
			"a kill that dropped nothing is still a kill");
		check(t.record("npc", "  ", 1, 7000, items(995, 1)) == null, "a kill with no name is not filed");
		t.record("npc", "Rat", 1, 8000, items(995, 0, 995, -3, -1, 5));
		check(t.source(LootTracker.keyOf("npc", "Rat")).items().isEmpty(),
			"no item with a count of nothing, a negative count or no id");

		t.forget(LootTracker.keyOf("npc", "Goblin"));
		check(t.source(LootTracker.keyOf("npc", "Goblin")) == null, "Reset forgets the box");
		boolean anyGoblin = false;
		for (LootTracker.Kill kill : t.kills()) {
			anyGoblin |= kill.key().equals(LootTracker.keyOf("npc", "Goblin"));
		}
		check(!anyGoblin, "and its kills from the per-kill list with it");

		LootTracker bounded = new LootTracker();
		for (int i = 0; i < LootTracker.MAX_KILLS + 30; i++) {
			bounded.record("npc", "Rat", 1, i, items(526, 1));
		}
		check(bounded.kills().size() == LootTracker.MAX_KILLS, "the per-kill list stops at " + LootTracker.MAX_KILLS);
		check(bounded.source(LootTracker.keyOf("npc", "Rat")).kills == LootTracker.MAX_KILLS + 30,
			"but the box keeps counting every one");

		LootTracker many = new LootTracker();
		for (int i = 0; i < LootTracker.MAX_SOURCES; i++) {
			many.record("npc", "Npc " + i, 1, 1000 + i, items(526, 1));
		}
		many.record("npc", "Npc 0", 1, 99999, items(526, 1));
		many.record("npc", "Newcomer", 1, 100000, items(526, 1));
		check(many.sources().size() == LootTracker.MAX_SOURCES, "the sources stop at " + LootTracker.MAX_SOURCES);
		check(many.source(LootTracker.keyOf("npc", "Npc 1")) == null,
			"and the one longest unseen is the one that goes");
		check(many.source(LootTracker.keyOf("npc", "Npc 0")) != null,
			"not the oldest-made one, which has paid out since");
	}

	static void values() {
		List<LootTracker.Item> drop = items(995, 50, 1333, 2, 526, 1);
		check(LootTracker.total(drop, PRICES, null) == 250, "two swords are worth two swords: 50 + 200");
		LootTracker.Ignored swords = new LootTracker.Ignored() {
			public boolean is(int objId) {
				return objId == 1333;
			}
		};
		check(LootTracker.total(drop, PRICES, swords) == 50, "an ignored item is out of the total");
		List<LootTracker.Item> sorted = LootTracker.sorted(drop, PRICES, null);
		check(sorted.get(0).id == 1333 && sorted.get(1).id == 995, "most valuable stack first");
		List<LootTracker.Item> ignoredLast = LootTracker.sorted(drop, PRICES, swords);
		check(ignoredLast.get(ignoredLast.size() - 1).id == 1333, "and an ignored one at the end, however valuable");

		LootTracker t = new LootTracker();
		t.record("npc", "Goblin", 2, 1, items(995, 10));
		t.record("npc", "Guard", 21, 2, items(1333, 1));
		check(t.total(PRICES, null, null) == 110, "the grand total is every box");
		java.util.Set<String> hidden = new java.util.HashSet<String>(Arrays.asList("guard"));
		check(t.total(PRICES, null, hidden) == 10, "less the hidden ones");

		check(LootTrackerPlugin.matches("bones", "bones"), "an ignore by name");
		check(!LootTrackerPlugin.matches("bones", "big bones"), "and only that name");
		check(LootTrackerPlugin.matches("*bones", "big bones"), "* at the front");
		check(LootTrackerPlugin.matches("grimy *", "grimy ranarr"), "* at the end");
		check(LootTrackerPlugin.matches("*rune*", "adamant rune scimitar"), "* both ends");
		check(!LootTrackerPlugin.matches("rune*", "adamant rune"), "a pattern still has to start where it starts");
		check(LootTrackerPlugin.splitList(" Bones, ,Big BONES ").equals(Arrays.asList("bones", "big bones")),
			"the setting split on commas, trimmed, any case");
	}

	static void saving() {
		LootTracker t = new LootTracker();
		t.record("npc", "Black dragon", 227, 1111, items(536, 1, 1747, 1, 995, 196));
		t.record("npc", "Black dragon", 227, 2222, items(536, 1));
		t.record("event", "Clue scroll (hard)", 0, 3333, items(1333, 1));
		t.record("player", "Lord | Fooby", 99, 4444, items(995, 1000000));
		String text = t.save();

		LootTracker back = new LootTracker();
		back.load(text);
		LootTracker.Source dragon = back.source(LootTracker.keyOf("npc", "Black dragon"));
		check(dragon != null && dragon.kills == 2 && dragon.level == 227, "a box comes back with its kills and level");
		check(dragon != null && count(dragon.items(), 536) == 2 && count(dragon.items(), 995) == 196,
			"and every item in it");
		check(back.source(LootTracker.keyOf("player", "Lord | Fooby")) != null, "a name with a bar in it still reads");
		check(back.kills().size() == 4 && back.kills().get(0).time == 4444, "the kills, newest first, at their times");
		check(back.save().equals(text), "and saving it again writes the same file");

		String broken = text.replace("I|1747|1", "I|17x7|1").replace("S|event|1|", "S|event|q|");
		LootTracker partial = new LootTracker();
		partial.load(broken);
		check(partial.source(LootTracker.keyOf("npc", "Black dragon")) != null, "a broken line costs that line");
		check(partial.source(LootTracker.keyOf("event", "Clue scroll (hard)")) == null, "and the box it starts");
		check(partial.source(LootTracker.keyOf("player", "Lord | Fooby")) != null, "but not the rest of the file");
		LootTracker none = new LootTracker();
		none.load("not a loot file\nS|npc|1|1|1|x\n");
		check(none.isEmpty(), "a file of another kind is not read at all");

		String file = LootTrackerPlugin.fileFor("Lord Fooby");
		check(file.startsWith("loot-") && file.endsWith(".txt") && file.toLowerCase().indexOf("fooby") < 0,
			"an account's file does not have the name in it");
		check(file.equals(LootTrackerPlugin.fileFor("lord_fooby")), "and is the same file however the name is written");
		check(!file.equals(LootTrackerPlugin.fileFor("Lord Foobar")), "and another account's is another file");
	}

	static void wire() {
		LootMessage m = new LootMessage();
		LootReceived one = m.read("n|7|0|227|Black dragon|536,1;995,196:loot:");
		check(one != null && LootReceived.NPC.equals(one.type) && one.name.equals("Black dragon")
			&& one.combatLevel == 227 && one.items.size() == 2, "one line, one kill");
		check(one != null && one.items.get(1).id == 995 && one.items.get(1).count == 196, "with its counts");

		check(m.read("e|9|1|0|Clue scroll (hard)|1333,1:loot:") == null, "a line saying more is coming is held");
		LootReceived joined = m.read("e|9|0|0|Clue scroll (hard)|995,5000:loot:");
		check(joined != null && joined.items.size() == 2 && LootReceived.EVENT.equals(joined.type),
			"and joined to the rest of its kill, as one kill");

		m.read("p|11|1|90|Somebody|995,1:loot:");
		LootReceived after = m.read("n|12|0|2|Goblin|526,1:loot:");
		check(after != null && after.name.equals("Goblin") && after.items.size() == 1,
			"a kill whose last piece never came is dropped, not glued to the next");

		check(m.read("n|13|0|2|A | name|526,1:loot:").name.equals("A | name"), "a name with a bar in it");
		LootReceived empty = m.read("n|15|0|2|Rat|:loot:");
		check(empty != null && empty.items.isEmpty(), "a kill with no items is still a kill");
		LootReceived bad = m.read("n|16|0|2|Rat|x,1;526,y;526,0;526,2:loot:");
		check(bad != null && bad.items.size() == 1 && bad.items.get(0).count == 2, "broken items skipped, good ones kept");
		check(m.read("z|1|0|0|What|1,1:loot:") == null, "an unknown type is nothing");
		check(!LootMessage.isLoot("Oh dear, you are dead!") && LootMessage.isLoot("x:loot:"),
			"only a line ending :loot: is one");
	}

	static void page() {
		LootTrackerPlugin p = new LootTrackerPlugin();
		check(p.layOut().isEmpty(), "no loot, no rows - the page says how to get some");

		p.tracker.record("npc", "Goblin", 2, System.currentTimeMillis() - 5000, items(526, 1, 995, 3));
		p.tracker.record("npc", "Cow", 2, System.currentTimeMillis(), items(526, 1, 1739, 1));
		List<LootTrackerPlugin.Box> rows = p.layOut();
		check(rows.size() == 4, "the total, two boxes and the reset");
		check(rows.get(0).label.equals("Total") && rows.get(0).detail.startsWith("2 kills"), "the total first");
		check(rows.get(1).label.equals("Cow (lvl-2) x 1"), "the newest box next, named as RuneLite names one");
		check(rows.get(2).items.size() == 2, "a box carries its drops");
		check(rows.get(3).label.equals("Reset all"), "the reset last");

		p.hiddenSources = "cow";
		rows = p.layOut();
		check(rows.size() == 3 && rows.get(1).label.startsWith("Goblin"), "a hidden source has no box");
		check(rows.get(0).detail.indexOf("1 hidden") >= 0, "and the total says something is hidden");
		p.hiddenSources = "";

		p.press(p.layOut().get(0));
		check(!p.grouped(), "the button on the total switches to one box per kill");
		rows = p.layOut();
		check(rows.get(1).label.equals("Cow (lvl-2)") && rows.get(1).detail.startsWith("just now"),
			"a kill's box has no count, and says when");
		p.press(p.layOut().get(0));
		check(p.grouped(), "and back");

		LootTrackerPlugin.Box goblinBox = p.layOut().get(2);
		p.press(goblinBox);
		check(p.tracker.source(LootTracker.keyOf("npc", "Goblin")) != null, "one press of Reset resets nothing");
		check(p.layOut().get(2).action.equals("Confirm"), "it asks to be pressed again");
		p.press(p.layOut().get(2));
		check(p.tracker.source(LootTracker.keyOf("npc", "Goblin")) == null, "and the second press resets the box");

		List<LootTrackerPlugin.Box> now = p.layOut();
		p.press(now.get(now.size() - 1));
		check(!p.tracker.isEmpty(), "Reset all asks first too");
		now = p.layOut();
		p.press(now.get(now.size() - 1));
		check(p.tracker.isEmpty(), "and then empties the page");

		check(LootTrackerPlugin.title("Clue scroll (hard)", 0).equals("Clue scroll (hard)"), "a casket has no level");
	}

	static void numbers() {
		check(LootTracker.gp(9999).equals("9,999"), "exact under ten thousand");
		check(LootTracker.gp(12345).equals("12.3K"), "thousands past it");
		check(LootTracker.gp(1900000).equals("1.9M"), "a tenth of a million");
		check(LootTracker.gp(5000000).equals("5M"), "no trailing .0");
		check(LootTracker.commas(1234567).equals("1,234,567"), "thousands separators");
		check(LootTracker.ago(0, 30000).equals("just now"), "under a minute is just now");
		check(LootTracker.ago(0, 61000).equals("1 minute ago"), "one minute");
		check(LootTracker.ago(0, 3 * 3600000L).equals("3 hours ago"), "hours");
		check(LootTracker.ago(0, 49 * 3600000L).equals("2 days ago"), "days");
	}
}
