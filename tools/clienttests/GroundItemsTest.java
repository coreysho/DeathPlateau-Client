// Driven by tools/clienttests/run_groundtest.py.
//
// It sits in jagex2.client.plugin.builtin so it can call the three package-visible methods the
// plugin's rules live in - visibleRows, colourFor and label - directly, with items it built by
// hand. Everything else goes through the PUBLIC manager, exactly as Client.java does it:
// renderOverlays, onViewportClick, onViewportScroll, onSettingsMenuOpening, snapshotConfigLists.
package jagex2.client.plugin.builtin;

import java.util.ArrayList;
import java.util.List;

import jagex2.client.Client;
import jagex2.client.GameShell;
import jagex2.client.GroundItemPrefs;
import jagex2.client.plugin.ConfigList;
import jagex2.client.plugin.GroundItem;
import jagex2.client.plugin.GroundItemPile;
import jagex2.client.plugin.Overlay;
import jagex2.client.plugin.PluginConfig;
import jagex2.client.plugin.PluginManager;
import jagex2.client.plugin.event.SettingsMenuOpening;
import jagex2.config.ObjType;
import jagex2.dash3d.ClientObj;
import jagex2.dash3d.ClientPlayer;
import jagex2.datastruct.LinkList;
import jagex2.graphics.Pix2D;
import jagex2.graphics.Pix3D;
import jagex2.graphics.PixFont;

/**
 * Headless test for the Ground items plugin.
 *
 * WHAT IS REAL HERE. Nearly all of it. The plugin is the shipped one, started by the real
 * PluginManager, drawing through the real OverlayGraphics into a real Pix2D buffer, reading real
 * ObjTypes through the real PluginContext, against the real GroundItemPrefs on disk. The scene
 * is real too: tile coordinates are projected by Client.projectFromGround with a heightmap and a
 * camera set up so that one named tile lands dead centre, which is what makes "the bottom row
 * sits on the tile" an assertion about arithmetic the client actually does.
 *
 * TWO THINGS ARE NOT. The font, because glyphs come out of a cache a headless test has none of -
 * so it is a PixFont subclass that records (x, y, colour, text) per call and paints nothing, and
 * recording that IS the measurement. And the server, obviously: piles are pushed into objStacks
 * the way the client's own object handler would.
 *
 * Because the font paints nothing, every pixel in the buffer came from a g.fill - which makes
 * the scroll bar the one thing checked as pixels rather than as a call, and checked honestly.
 */
public class GroundItemsTest {

	static final int W = 512;
	static final int H = 334;

	/** The tile the camera is aimed at, and the one most of these piles are on. */
	static final int MID_X = 64;
	static final int MID_Z = 64;

	/** Ids the ObjType cache is primed with. Ten, because ObjType.get's cache has ten slots. */
	static final int BASE_ID = 900;
	static final int TYPES = 10;

	/** Mirrors of the plugin's own constants, so a change to one is a failure here, not a drift. */
	static final int ROW_H = 12;
	static final int ROWS_SHOWN = 8;
	static final int MAX_LABELS = 48;
	static final int CONTROL_W = 10;
	static final int BAR_TRACK = 0x282828;
	static final int BAR_THUMB = 0xC8C8C8;
	static final int HIGHLIGHT_COLOUR = 0xFF40FF;
	static final int HIDDEN_COLOUR = 0x707070;
	static final int PLAIN_COLOUR = 0xFFFFFF;

	static int fails;

	static Client client;
	static PluginManager manager;
	static PluginManager.Entry entry;
	static RecordingFont font;
	static int[] pixels = new int[W * H];

	static void check(boolean ok, String what) {
		System.out.println((ok ? "  ok   " : "FAIL   ") + what);
		if (!ok) {
			fails++;
		}
	}

	public static void main(String[] args) {
		// The rules half needs nothing but the plugin's own classes, so it runs either way. The
		// rest needs a Client, and Applet's constructor refuses to run without a display.
		GroundItemPrefs.clear();
		ruleTests();

		arrivalTests();
		notifyTests();
		paletteTests();

		if (java.awt.GraphicsEnvironment.isHeadless()) {
			System.out.println("  SKIP  no display, so a Client cannot be constructed - run with xvfb-run");
			System.out.println(fails == 0 ? "ALL PASS (the drawing sections were skipped)"
				: fails + " FAILED");
			System.exit(fails == 0 ? 0 : 1);
		}

		setUp();
		columnTests();
		windowTests();
		scrollTests();
		barTests();
		altTests();
		settingsMenuTests();
		configTests();
		lootPageTests();
		formatTests();

		System.out.println(fails == 0 ? "ALL PASS" : fails + " FAILED");
		System.exit(fails == 0 ? 0 : 1);
	}

	// ---------------------------------------------------------------- 1: the rules

	/**
	 * Which ground items count as NEW, which is what a notification and a beam both rest on.
	 *
	 * The two ways to get this wrong are opposite and both bad: report things that were already
	 * lying there and the player gets a notification storm for walking into a loot pile; miss real
	 * drops and the feature silently does nothing.
	 */
	static void arrivalTests() {
		GroundItemArrivals seen = new GroundItemArrivals();
		long coins = GroundItemArrivals.key(3200, 3200, 0, 995);
		long bones = GroundItemArrivals.key(3200, 3200, 0, 526);

		// THE FIRST SCAN REPORTS NOTHING. Everything already on the floor was not dropped while
		// the player was watching, and a player walking up to a pile wants silence.
		seen.begin();
		check(!seen.add(coins), "the first scan reports nothing new, however much is lying there");
		check(!seen.add(bones), "...for any of it");
		seen.finish();
		check(seen.remembered() == 2, "but it remembers what it saw (" + seen.remembered() + ")");

		// The same items again are not new.
		seen.begin();
		check(!seen.add(coins), "an item still lying there is not a new drop");
		check(!seen.add(bones), "...nor the one beside it");
		seen.finish();

		// Something else landing is.
		long sword = GroundItemArrivals.key(3200, 3200, 0, 1277);
		seen.begin();
		seen.add(coins);
		seen.add(bones);
		check(seen.add(sword), "something that was not there is a new drop");
		seen.finish();

		// And it is only new once.
		seen.begin();
		seen.add(coins);
		seen.add(bones);
		check(!seen.add(sword), "...and only the once");
		seen.finish();

		// AN ITEM THAT WENT AWAY AND CAME BACK is new again, which is right: a respawn or a fresh
		// drop on a tile someone cleared is a thing that just landed.
		seen.begin();
		seen.add(coins);
		seen.finish();
		seen.begin();
		seen.add(coins);
		check(seen.add(bones), "an item dropped again after being taken is new again");
		seen.finish();

		// COUNT IS NOT IDENTITY. Taking one coin off a stack changes the count and is not a drop,
		// and a kill adding to a stack already there is not one either. The key has no count in
		// it at all, which is what makes that true rather than nearly true.
		check(GroundItemArrivals.key(3200, 3200, 0, 995)
				== GroundItemArrivals.key(3200, 3200, 0, 995),
			"the same item on the same tile is the same key whatever the stack does");

		// Place and plane are part of it.
		check(GroundItemArrivals.key(3200, 3200, 0, 995)
				!= GroundItemArrivals.key(3201, 3200, 0, 995), "a tile east is a different key");
		check(GroundItemArrivals.key(3200, 3200, 0, 995)
				!= GroundItemArrivals.key(3200, 3201, 0, 995), "and a tile north");
		check(GroundItemArrivals.key(3200, 3200, 0, 995)
				!= GroundItemArrivals.key(3200, 3200, 1, 995), "and an upstairs");
		check(GroundItemArrivals.key(3200, 3200, 0, 995)
				!= GroundItemArrivals.key(3200, 3200, 0, 526), "and a different item");

		// A reset is what a log out does, so logging in somewhere else is not a storm.
        seen.reset();
		check(!seen.seenAnything(), "a reset forgets everything");
		seen.begin();
		check(!seen.add(coins), "...so the next scan reports nothing new again");
		seen.finish();

		// Bounded, because the scan is per tick and a crowded floor must cost a fixed amount.
		seen.reset();
		seen.begin();
		for (int i = 0; i < GroundItemArrivals.MAX + 500; i++) {
			seen.add(GroundItemArrivals.key(3000 + i % 64, 3000 + i / 64, 0, i));
		}
		seen.finish();
		check(seen.remembered() == GroundItemArrivals.MAX,
			"a floor past the cap remembers the cap and no more (" + seen.remembered() + ")");

		// Nothing here may throw on nonsense: worldX is whatever sceneToWorldX returned.
		boolean threw = false;
		try {
			GroundItemArrivals.key(-1, -1, -1, -1);
			GroundItemArrivals.key(Integer.MAX_VALUE, Integer.MIN_VALUE, 99, 99999);
		} catch (Throwable broke) {
			threw = true;
		}
		check(!threw, "a nonsense coordinate does not throw out of a tick");
	}

	/** Which drops are worth telling the player about, and which tier a name means. */
	static void notifyTests() {
		GroundItemPrefs.clear();
		GroundItemPalette palette = GroundItemPalette.DEFAULTS;

		check(GroundItemsPlugin.thresholdFor(GroundItemsPlugin.TIER_OFF, palette) == 0L,
			"Off is no threshold at all");
		check(GroundItemsPlugin.thresholdFor(GroundItemsPlugin.TIER_TOP, palette) == 1000000L,
			"Top tier reads the top threshold");
		check(GroundItemsPlugin.thresholdFor(GroundItemsPlugin.TIER_LOW, palette) == 1000L,
			"and Low tier the lowest");
		check(GroundItemsPlugin.thresholdFor(null, palette) == 0L, "an absent choice is Off");
		check(GroundItemsPlugin.thresholdFor("Enormous", palette) == 0L,
			"and so is one from a release that offered something else");

		// IT READS THE PALETTE, so moving a tier's price moves what gets notified about. After
		// sorting, so a player who typed the prices out of order gets the tier they picked.
		GroundItemPalette shuffled = new GroundItemPalette(0xFFFFFF, 0xFF40FF, 0x707070,
			new int[] { 1000, 1000000, 10000, 100000 },
			new int[] { 0xFFFF80, 0xFF9040, 0x40FF40, 0x40C0FF });
		check(GroundItemsPlugin.thresholdFor(GroundItemsPlugin.TIER_TOP, shuffled) == 1000000L,
			"Top tier is the biggest price, not the first one typed");
		check(GroundItemsPlugin.thresholdFor(GroundItemsPlugin.TIER_LOW, shuffled) == 1000L,
			"and Low tier the smallest");

		// A tier threshold of 0 is one the player turned off, so nothing is notified from it.
		GroundItemPalette noTop = new GroundItemPalette(0xFFFFFF, 0xFF40FF, 0x707070,
			new int[] { 0, 100000, 10000, 1000 },
			new int[] { 0xFF9040, 0x40C0FF, 0x40FF40, 0xFFFF80 });
		check(!GroundItemsPlugin.worthTelling(item("Gold", 1, 5000000),
				GroundItemsPlugin.thresholdFor(GroundItemsPlugin.TIER_OFF, noTop), false),
			"nothing is notified when the tier is Off, however valuable");
		// "TOP TIER" MEANS THE HIGHEST PRICE, NOT THE TOP BOX. Thresholds are sorted, so setting
		// "Top tier from" to 0 does not leave a hole at the top - the 0 sorts to the bottom and
		// Top tier becomes the largest price still set, here 100k. A player who zeroes a box to
		// turn a notification off has to pick Off in the drop-down instead; zeroing the price
		// turns off that COLOUR tier, which is a different question. Asserted because it is
		// surprising, not because it is wrong.
		check(GroundItemsPlugin.thresholdFor(GroundItemsPlugin.TIER_TOP, noTop) == 100000L,
			"zeroing the top price makes Top tier the next price down, not nothing");
		check(GroundItemsPlugin.worthTelling(item("Gold", 1, 5000000),
				GroundItemsPlugin.thresholdFor(GroundItemsPlugin.TIER_TOP, noTop), false),
			"...so a valuable drop is still notified from it");
		check(noTop.forWorth(5000000L) == 0x40C0FF,
			"and the colour tier it zeroed really is gone: a million now draws in the next one");

		// The value rule.
		check(GroundItemsPlugin.worthTelling(item("Gold", 1, 1000000), 1000000L, false),
			"something worth exactly the threshold is worth telling");
		check(!GroundItemsPlugin.worthTelling(item("Gold", 1, 999999), 1000000L, false),
			"and something under it is not");
		check(!GroundItemsPlugin.worthTelling(item("Bones", 1, 1), 0L, false),
			"with no threshold and no highlight, nothing is");

		// HIGHLIGHTED WINS OUTRIGHT, the same way it ignores the value floor when drawing: a
		// player who named an item wants to know it landed whatever it is worth.
		GroundItemPrefs.toggle("Bones", GroundItemPrefs.HIGHLIGHT);
		check(GroundItemsPlugin.worthTelling(item("Bones", 1, 1), 1000000L, true),
			"a highlighted item is worth telling whatever it is worth");
		check(!GroundItemsPlugin.worthTelling(item("Bones", 1, 1), 1000000L, false),
			"...but only when the player asked for highlighted drops");
		GroundItemPrefs.clear();
	}

	/** The palette's own arithmetic: the tiers, the sort, and a tier turned off. */
	static void paletteTests() {
		GroundItemPalette defaults = GroundItemPalette.DEFAULTS;
		check(defaults.forWorth(1000000L) == 0xFF9040, "a million is the top tier");
		check(defaults.forWorth(999999L) == 0x40C0FF, "just under it is the next one down");
		check(defaults.forWorth(1000L) == 0xFFFF80, "a thousand is the lowest tier");
		check(defaults.forWorth(999L) == defaults.plain, "under every tier is the plain colour");
		check(defaults.forWorth(0L) == defaults.plain, "and so is nothing at all");

		// THE SORT, which is the part that matters: the tier walk takes the first threshold an
		// item clears, so prices typed out of order would otherwise give the wrong colour.
		GroundItemPalette shuffled = new GroundItemPalette(0xFFFFFF, 0xFF40FF, 0x707070,
			new int[] { 1000, 1000000, 10000, 100000 },
			new int[] { 0xFFFF80, 0xFF9040, 0x40FF40, 0x40C0FF });
		check(shuffled.threshold(0) == 1000000 && shuffled.threshold(3) == 1000,
			"thresholds come out biggest first however they were typed");
		check(shuffled.colour(0) == 0xFF9040 && shuffled.colour(3) == 0xFFFF80,
			"...and each colour travels with its own threshold, not with its slot");
		check(shuffled.forWorth(10000L) == 0x40FF40,
			"so a 10k item gets the 10k colour, not the one typed first");

		// A tier turned off must not swallow the floor: everything is worth at least nothing.
		GroundItemPalette off = new GroundItemPalette(0xFFFFFF, 0xFF40FF, 0x707070,
			new int[] { 0, 0, 0, 0 },
			new int[] { 0xFF0000, 0xFF0000, 0xFF0000, 0xFF0000 });
		check(off.forWorth(0L) == off.plain, "a tier set to 0 is off, not a tier matching anything");
		check(off.forWorth(5000000L) == off.plain, "...for any value");

		// Junk from a settings file: nothing may throw, and a bad colour reads as the fallback.
		GroundItemPalette junk = GroundItemPalette.from("zzzzzz", null, "#12", null,
			new String[] { "", "nonsense", null, "00FF00" });
		check(junk.plain == 0xFFFF00, "an unparseable colour falls back rather than throwing");
		check(junk.forWorth(0L) == 0xFFFF00, "...and so does the palette built from it");
	}

	/**
	 * Which rows a pile has, and in what colour, with no client at all.
	 *
	 * This is the half with the rules in it, and the half worth checking against hand-built
	 * items: a cache of ten slots cannot hold the two dozen distinct items the per-tile cap is
	 * about, and a floor is easier to be sure of against a price you wrote than one you decoded.
	 */
	static void ruleTests() {
		GroundItemPrefs.clear();
		// No setShowHidden reset here any more: it was global state in GroundItemPrefs that could
		// leak between sections, and it is a plugin setting now. Every assertion below passes
		// reveal explicitly, which is what made the reset vestigial rather than load-bearing -
		// and this section runs before there is a plugin to ask.

		check(GroundItemsPlugin.visibleRows(pileOf(item("Bones", 1, 100)), false, 0).size() == 1,
			"an ordinary item above the floor is a row");
		check(GroundItemsPlugin.visibleRows(pileOf(item("", 1, 100)), false, 0).isEmpty(),
			"an item the cache has no name for is never a row");

		// The floor is a >=, so an item worth exactly it is still named: a player who sets 1000
		// means "nothing under 1k", not "nothing at 1k".
		check(GroundItemsPlugin.colourFor(item("Bones", 1, 999), false, 1000) == 0,
			"below the floor: not drawn");
		check(GroundItemsPlugin.colourFor(item("Bones", 1, 1000), false, 1000) != 0,
			"exactly the floor: drawn");

		// Only a stackable item's worth multiplies. Twenty swords on a tile are twenty swords.
		check(GroundItemsPlugin.colourFor(new GroundItem(1, "Coins", 500, 1, true), false, 400) != 0,
			"500 coins clear a 400gp floor: a stack is worth count times price");
		check(GroundItemsPlugin.colourFor(new GroundItem(2, "Sword", 20, 100, false), false, 400) == 0,
			"...but twenty 100gp swords are not one 2000gp pile");

		check(GroundItemsPlugin.colourFor(item("Bones", 1, 1), false, 0) == PLAIN_COLOUR,
			"a cheap item is plain white");
		check(GroundItemsPlugin.colourFor(item("Bones", 1, 1000), false, 0) != PLAIN_COLOUR
			&& GroundItemsPlugin.colourFor(item("Bones", 1, 1000), false, 0)
				!= GroundItemsPlugin.colourFor(item("Bones", 1, 10000), false, 0),
			"the value tiers are four different colours");
		check(GroundItemsPlugin.colourFor(new GroundItem(1, "Coins", 2000, 1000, true), false, 0)
			== GroundItemsPlugin.colourFor(item("Bones", 1, 1000000), false, 0),
			"a tier is read off the whole pile's worth, not one item's price");

		GroundItemPrefs.set("Bones", GroundItemPrefs.HIDE);
		check(GroundItemsPlugin.colourFor(item("Bones", 1, 100), false, 0) == 0,
			"a hidden item is not drawn");
		check(GroundItemsPlugin.colourFor(item("Bones", 1, 100), true, 0) == HIDDEN_COLOUR,
			"...until something reveals it, and then it is grey");

		GroundItemPrefs.set("Bones", GroundItemPrefs.HIGHLIGHT);
		check(GroundItemsPlugin.colourFor(item("Bones", 1, 1), false, 1000000) == HIGHLIGHT_COLOUR,
			"a highlighted item ignores the floor - that is the point of highlighting something cheap");

		// The rules are keyed on the bare name. A rule set on one coin has to keep applying to
		// a pile of them, whose LABEL is "Coins x 500".
		GroundItemPrefs.clear();
		GroundItemPrefs.set("Coins", GroundItemPrefs.HIDE);
		GroundItem coins = new GroundItem(1, "Coins", 500, 1, true);
		check(GroundItemsPlugin.label(coins).equals("Coins x 500"), "a stack's label carries its count");
		check(GroundItemsPlugin.colourFor(coins, false, 0) == 0,
			"...and the rule still finds it, because the rule is looked up on the name");
		check(GroundItemsPlugin.label(item("Bones", 1, 1)).equals("Bones"),
			"a single item is just its name");

		// Thirty distinct items on one tile is a griefer's pile; the cap is what stops it.
		GroundItemPrefs.clear();
		List<GroundItem> many = new ArrayList<GroundItem>();
		for (int i = 0; i < 30; i++) {
			many.add(item("Thing " + i, 1, 1));
		}
		int capped = GroundItemsPlugin.visibleRows(new GroundItemPile(0, 0, many), false, 0).size();
		check(capped == 24, "a tile shows at most 24 distinct items, whatever is dropped on it ("
			+ capped + ")");

		// The bug this feature has had, as an assertion: a hidden row must not leave a hole.
		GroundItemPrefs.set("Middle", GroundItemPrefs.HIDE);
		List<GroundItemsPlugin.Row> three = GroundItemsPlugin.visibleRows(
			pileOf(item("Top", 1, 1), item("Middle", 1, 1), item("Bottom", 1, 1)), false, 0);
		check(three.size() == 2 && three.get(0).item.name.equals("Top")
			&& three.get(1).item.name.equals("Bottom"),
			"hiding the middle of three leaves two rows that are next to each other in the list");
		GroundItemPrefs.clear();
	}

	// ---------------------------------------------------------------- 2: the column

	/** Three rows, 12 apart, the last one on the tile. And what happens when one goes away. */
	static void columnTests() {
		reset();
		pile(MID_X, MID_Z, 3, 1);

		List<Drawn> rows = frame();
		check(rows.size() == 3, "three items on a tile, three labels (" + rows.size() + ")");
		if (rows.size() != 3) {
			return;
		}
		check(rows.get(2).y == originY(), "the bottom row sits on the tile");
		check(rows.get(1).y - rows.get(0).y == ROW_H && rows.get(2).y - rows.get(1).y == ROW_H,
			"and the column grows upwards from it, a row at a time");
		check(rows.get(0).text.equals("Thing 0") && rows.get(2).text.equals("Thing 2"),
			"in the order the pile stacks them, top of the pile first");
		check(rows.get(0).x == originX() - font.widthOf("Thing 0") / 2,
			"each row is centred on the tile");

		int tallest = rows.get(0).y;

		GroundItemPrefs.set("Thing 1", GroundItemPrefs.HIDE);
		rows = frame();
		check(rows.size() == 2, "hide the middle one and two are left (" + rows.size() + ")");
		check(rows.size() == 2 && rows.get(1).y == originY(),
			"...still ending on the tile");
		check(rows.size() == 2 && rows.get(1).y - rows.get(0).y == ROW_H,
			"...and still a row apart: no hole where the hidden one was");
		check(rows.size() == 2 && rows.get(0).y > tallest,
			"...so the column is shorter than it was, not the same height with a gap");

		setShowHidden(true);
		rows = frame();
		check(rows.size() == 3, "turning on Show hidden brings it back (" + rows.size() + ")");
		check(rows.size() == 3 && rows.get(1).colour == HIDDEN_COLOUR,
			"...in grey, so it still reads as hidden");
		setShowHidden(false);

		GroundItemPrefs.set("Thing 0", GroundItemPrefs.HIDE);
		GroundItemPrefs.set("Thing 2", GroundItemPrefs.HIDE);
		check(frame().isEmpty(), "hide all three and the tile is blank");

		GroundItemPrefs.clear();
		GroundItemPrefs.set("Thing 1", GroundItemPrefs.HIGHLIGHT);
		rows = frame();
		check(rows.size() == 3 && rows.get(1).colour == HIGHLIGHT_COLOUR,
			"a highlighted row is drawn in its own colour");
		GroundItemPrefs.clear();
	}

	// ---------------------------------------------------------------- 3: the window

	/** A pile taller than the window shows a slice of itself, and a frame has a budget. */
	static void windowTests() {
		reset();
		pile(MID_X, MID_Z, TYPES, 1);

		List<Drawn> rows = frame();
		check(rows.size() == ROWS_SHOWN, "ten items show " + ROWS_SHOWN + " (" + rows.size() + ")");
		check(rows.size() == ROWS_SHOWN && rows.get(0).text.equals("Thing 0"),
			"the window starts at the top of the pile");
		check(rows.size() == ROWS_SHOWN && rows.get(rows.size() - 1).y == originY(),
			"and the bottom of the window is still on the tile");

		// Seven full piles is more labels than a frame is allowed to draw.
		reset();
		for (int i = 0; i < 7; i++) {
			pile(MID_X - 3 + i, MID_Z, TYPES, 1);
		}
		int drawn = frame().size();
		check(drawn == MAX_LABELS, "a frame draws at most " + MAX_LABELS + " labels, not 56 ("
			+ drawn + ")");

		// An id the cache cannot decode must cost one row, not the frame.
		reset();
		LinkList stack = new LinkList();
		ClientObj unknown = new ClientObj();
		unknown.field873 = 5;                 // not one of the primed ids
		unknown.field875 = 1;
		stack.push(unknown);
		ClientObj known = new ClientObj();
		known.field873 = BASE_ID;
		known.field875 = 1;
		stack.push(known);
		client.objStacks[0][MID_X][MID_Z] = stack;
		rows = frame();
		check(rows.size() == 1 && rows.get(0).text.equals("Thing 0"),
			"an item the cache cannot decode is skipped, and the rest of the pile still draws");
	}

	// ---------------------------------------------------------------- 4: the wheel

	/** Who gets the wheel, and what it does to the window. */
	static void scrollTests() {
		reset();
		pile(MID_X, MID_Z, 3, 1);
		frame();
		check(!manager.onViewportScroll(originX(), originY(), 1),
			"a pile that fits leaves the wheel alone, so it still zooms the camera");

		reset();
		pile(MID_X, MID_Z, TYPES, 1);
		frame();
		check(manager.onViewportScroll(originX(), originY(), 1),
			"a pile that does not fit takes the wheel");
		List<Drawn> rows = frame();
		check(rows.size() == ROWS_SHOWN && rows.get(0).text.equals("Thing 1"),
			"one notch down moves the window one row");

		for (int i = 0; i < 10; i++) {
			manager.onViewportScroll(originX(), originY(), 1);
		}
		rows = frame();
		check(rows.get(0).text.equals("Thing 2") && rows.get(rows.size() - 1).text.equals("Thing 9"),
			"and it stops at the bottom of the pile rather than scrolling past it");

		for (int i = 0; i < 10; i++) {
			manager.onViewportScroll(originX(), originY(), -1);
		}
		rows = frame();
		check(rows.get(0).text.equals("Thing 0"), "...and at the top going back up");

		check(!manager.onViewportScroll(originX(), originY() - 400, 1),
			"the wheel is only claimed over the pile itself");

		// A second pile. Scrolling it must not inherit the first one's position, and must leave
		// the first one where the player left it - which is at the top, since only one is kept.
		reset();
		pile(MID_X, MID_Z, TYPES, 1);
		pile(MID_X + 2, MID_Z, TYPES, 1);
		frame();
		manager.onViewportScroll(originX(), originY(), 3);
		frame();
		manager.onViewportScroll(originXAt(MID_X + 2), originY(), 1);
		rows = frame();
		String first = textAt(rows, originX());
		String second = textAt(rows, originXAt(MID_X + 2));
		check("Thing 1".equals(second), "scrolling a second pile starts it at the top (" + second + ")");
		check("Thing 0".equals(first), "...and the first pile goes back to the top (" + first + ")");
	}

	// ---------------------------------------------------------------- 5: the scroll bar

	/** The one thing drawn rather than written, so the one thing checked as pixels. */
	static void barTests() {
		reset();
		pile(MID_X, MID_Z, 3, 1);
		frame();
		check(painted() == 0, "a pile that fits draws no scroll bar - and the font paints nothing");

		reset();
		pile(MID_X, MID_Z, TYPES, 1);
		frame();
		check(painted() > 0, "a pile that does not fit draws one (" + painted() + " pixels)");
		int barLeft = leftmostPainted();
		check(barLeft >= 0 && barLeft < originX() - font.widthOf("Thing 0") / 2,
			"...to the left of the names, not over them");

		int top = topmostOf(BAR_THUMB);
		check(topmostOf(BAR_TRACK) >= 0 && top >= 0, "...as a track with a thumb on it");
		check(top <= topmostOf(BAR_TRACK) + 1, "and at the top of the pile the thumb is at the top");

		manager.onViewportScroll(originX(), originY(), 2);
		frame();
		check(topmostOf(BAR_THUMB) > top, "scrolling down moves the thumb down ("
			+ top + " -> " + topmostOf(BAR_THUMB) + ")");
		check(bottommostOf(BAR_THUMB) <= bottommostOf(BAR_TRACK),
			"...and never off the end of its track");
	}

	// ---------------------------------------------------------------- 6: Alt

	/** The controls, and the one thing they must get right: which string a rule is keyed on. */
	static void altTests() {
		reset();
		// A count, so the LABEL ("Thing 0 x 4") differs from the NAME ("Thing 0"). That is the
		// only way to tell whether a click zone carries the name the rules use or the label.
		pile(MID_X, MID_Z, 1, 4);

		List<Drawn> rows = frame();
		check(rows.size() == 1 && rows.get(0).text.equals("Thing 0 x 4"),
			"a stacked row is labelled with its count");
		check(!manager.onViewportClick(rows.get(0).x + 2, rows.get(0).y - 4),
			"with no Alt held, a label is not clickable: the click walks you there as always");

		alt(true);
		rows = frame();
		check(rows.size() == 3, "holding Alt draws two controls beside the name (" + rows.size() + ")");
		if (rows.size() != 3) {
			alt(false);
			return;
		}
		check(rows.get(0).text.equals("-") && rows.get(1).text.equals("+")
			&& rows.get(2).text.equals("Thing 0 x 4"), "a minus, a plus, then the label");
		check(rows.get(1).x - rows.get(0).x == CONTROL_W && rows.get(2).x - rows.get(1).x == CONTROL_W,
			"...laid out a control's width apart");
		check(rows.get(0).x < originX() && rows.get(2).x + font.widthOf(rows.get(2).text) > originX(),
			"...and still straddling the tile, so the row stays centred as it grows");

		int y = rows.get(0).y - 4;
		check(manager.onViewportClick(rows.get(0).x + CONTROL_W / 2, y), "the minus takes the click");
		check(GroundItemPrefs.isHidden("Thing 0"),
			"...and hides the item by its NAME, not the label with the count on it");

		frame();
		check(manager.onViewportClick(rows.get(1).x + CONTROL_W / 2, y), "the plus takes a click too");
		check(!GroundItemPrefs.isHidden("Thing 0") && GroundItemPrefs.find("Thing 0") < 0,
			"...and puts the item back to no rule at all");

		frame();
		check(manager.onViewportClick(rows.get(2).x + 2, y), "and the name itself is clickable");
		check(GroundItemPrefs.isHighlighted("Thing 0"), "...which highlights it");

		frame();
		manager.onViewportClick(rows.get(2).x + 2, y);
		check(GroundItemPrefs.find("Thing 0") < 0, "clicking the name again takes the highlight off");

		// A hidden item under Alt: it is revealed, in grey, WITH its controls - otherwise there
		// would be no plus to click and it could only be recovered from the settings page.
		GroundItemPrefs.set("Thing 0", GroundItemPrefs.HIDE);
		rows = frame();
		check(rows.size() == 3 && rows.get(2).colour == HIDDEN_COLOUR,
			"a hidden item is revealed under Alt, in grey, with its controls");

		alt(false);
		check(frame().isEmpty(), "and is gone again the moment Alt is let go");
		GroundItemPrefs.clear();
	}

	// ---------------------------------------------------------------- 7: the settings menu

	/** Shift-right-click on a ground item offers the same two rules the Alt controls do. */
	static void settingsMenuTests() {
		reset();
		GroundItemPrefs.clear();

		List<SettingsMenuOpening.Row> rows = menu(true, 8, target("lre", "Bones"));
		check(rows.size() == 2, "a ground item under the cursor gets two rows (" + rows.size() + ")");
		check(labelled(rows, "Hide @lre@Bones") && labelled(rows, "Highlight @lre@Bones"),
			"...one to hide it and one to highlight it, with the item coloured as the game colours it");

		check(menu(false, 8, target("lre", "Bones")).isEmpty(),
			"nothing in an interface menu: an inventory item carries the same tag as a ground one");
		check(menu(true, 8, target("npc", "Goblin")).isEmpty(),
			"and nothing for something that is not an item");

		check(menu(true, 8, target("lre", "Bones"), target("lre", "bones")).size() == 2,
			"the same item twice under the cursor still gets one pair of rows");
		check(menu(true, 8, target("lre", "Bones"), target("lre", "Coins")).size() == 4,
			"two different items get a pair each");

		List<SettingsMenuOpening.Row> cramped = menu(true, 1, target("lre", "Bones"));
		check(cramped.size() == 1, "a menu with room for one row gets one, not a thrown exception");

		rows = menu(true, 8, target("lre", "Bones"));
		run(rows, "Hide @lre@Bones");
		check(GroundItemPrefs.isHidden("Bones"), "choosing Hide hides it");
		rows = menu(true, 8, target("lre", "Bones"));
		check(labelled(rows, "Stop hiding @lre@Bones"),
			"...and the row now offers to undo itself rather than repeating");
		run(rows, "Stop hiding @lre@Bones");
		check(GroundItemPrefs.find("Bones") < 0, "...which it does");

		rows = menu(true, 8, target("lre", "Bones"));
		run(rows, "Highlight @lre@Bones");
		check(GroundItemPrefs.isHighlighted("Bones"), "the same for Highlight");
		check(labelled(menu(true, 8, target("lre", "Bones")), "Stop highlighting @lre@Bones"),
			"...and its undo");
		GroundItemPrefs.clear();
	}

	// ---------------------------------------------------------------- 8: the config page

	/**
	 * The rules list, and the settings that used to be cycling rows beside it.
	 *
	 * THERE IS ONE CONFIG LIST NOW, not two. Radius, minimum value and show-hidden were three
	 * rows a player clicked to step through presets, because the in-game panel they lived in had
	 * no text entry. They are ordinary settings now - typed, and alongside ten colours and
	 * thresholds that used to be compiled in.
	 */
	static void configTests() {
		reset();
		GroundItemPrefs.clear();

		List<PluginManager.ListSnapshot> lists = manager.snapshotConfigLists(entry);
		check(lists.size() == 1, "the plugin has one config list (" + lists.size() + ")");
		check(lists.size() == 1 && lists.get(0).title.equals("Items"),
			"and it is the rules: the three cycling rows are settings now");

		// The three that moved, with the types their editors depend on.
		check(setting("radius") != null && setting("radius").isInt(),
			"radius is a number a player types");
		check(setting("minValue") != null && setting("minValue").isInt(),
			"and so is the value floor");
		check(setting("showHidden") != null && setting("showHidden").isBoolean(),
			"and show hidden is a switch");

		// The ten that were constants. Colours have to report as colours or they get a text box.
		String[] colours = { "plainColour", "highlightColour", "hiddenColour",
			"tier1Colour", "tier2Colour", "tier3Colour", "tier4Colour" };
		for (int i = 0; i < colours.length; i++) {
			PluginConfig.Item item = setting(colours[i]);
			check(item != null && item.isColour(), colours[i] + " is edited as a colour");
		}
		String[] prices = { "tier1Price", "tier2Price", "tier3Price", "tier4Price" };
		for (int i = 0; i < prices.length; i++) {
			PluginConfig.Item item = setting(prices[i]);
			check(item != null && item.isInt(), prices[i] + " is a number");
		}

		// And the drop-down, whose values have to be the ones the code compares against.
		PluginConfig.Item price = setting("priceDisplay");
		check(price != null && price.choices().length == 3,
			"what a row says is a drop-down of three forms");

		// Writing one through the real config path takes effect and is read back.
		setSetting("radius", "7");
		check(setting("radius").intValue() == 7, "a typed radius is kept (7)");
		setSetting("radius", "12");
		check(setting("radius").intValue() == 12, "...and can be set back");
	}

	// ---------------------------------------------------------------- 9: the Loot nearby page

	/**
	 * The rail page, which asks a different question of the same scene than the overlay does.
	 *
	 * Everything here was wrong in the first version and right in the picture, which is the
	 * argument for the test: an aggregate that silently under-reports looks exactly like one
	 * that does not.
	 */
	static void lootPageTests() {
		reset();
		// Three tiles: bones underfoot, two lots of dragon bones at different distances, and a
		// coin pile. Non-stackables on purpose - that is where the totalling went wrong.
		pile(MID_X, MID_Z, 1, 1);                       // Thing 0, underfoot
		pile(MID_X + 2, MID_Z + 1, 1, 2);               // Thing 0 again, two tiles off, two of them
		pile(MID_X + 5, MID_Z, 2, 1);                   // Thing 0 and Thing 1, five tiles off

		List<ConfigList.Row> rows = lootRows();
		check(rows.size() == 2, "two kinds of item nearby, however many tiles they are on ("
			+ rows.size() + ")");
		if (rows.size() != 2) {
			return;
		}

		// Thing 0: 1 + 2 + 1 = 4 of them, at 1gp each because primeTypes prices them at 1.
		ConfigList.Row thing0 = rows.get(0).label.equals("Thing 0") ? rows.get(0) : rows.get(1);
		check(thing0.detail != null && thing0.detail.startsWith("4"),
			"...merged by name across every tile they are on (" + thing0.detail + ")");
		check(thing0.detail != null && thing0.detail.endsWith("underfoot"),
			"...reported at the distance of the NEAREST one (" + thing0.detail + ")");

		// The aggregate. Thing 0 is stackable at 1gp, so four of them is 4gp.
		check("4 gp".equals(thing0.value), "...and worth all of them together (" + thing0.value + ")");

		// AND THE SAME FOR SOMETHING THAT DOES NOT STACK, which is the half that was wrong.
		// Three separate Thing 1s are three times the price even though a pile of them is not a
		// stack - the page is asking how much is lying there, not what one is worth.
		reset();
		pile(MID_X, MID_Z, 2, 3);                       // three each of Thing 0 and Thing 1
		rows = lootRows();
		ConfigList.Row odd = null;
		for (int i = 0; i < rows.size(); i++) {
			if (rows.get(i).label.equals("Thing 1")) {
				odd = rows.get(i);
			}
		}
		check(odd != null && "3 gp".equals(odd.value),
			"three of a non-stackable are worth three of it on this page ("
				+ (odd == null ? "missing" : odd.value) + ")");

		// A non-stackable: the overlay says one is worth 500, this page says all of them are.
		reset();
		List<GroundItem> three = new ArrayList<GroundItem>();
		three.add(new GroundItem(1, "Sword", 3, 500, false));
		check(GroundItemsPlugin.colourFor(three.get(0), false, 1000) == 0,
			"the overlay still prices a non-stackable at one of it: three 500gp swords miss a "
				+ "1000gp floor");

		reset();
		pile(MID_X, MID_Z, 1, 1);
		rows = lootRows();
		check(rows.size() == 1 && "1 gp".equals(rows.get(0).value),
			"a single item reads as its own price");

		// Nothing nearby is an empty page with something to say, not a blank one.
		reset();
		check(lootRows().isEmpty(), "an empty floor is an empty page");
		check(lootPanel().emptyMessage != null && lootPanel().emptyMessage.length() > 0,
			"...which says what would put something on it");

		// A hidden item is absent from the page exactly as it is absent from the ground.
		reset();
		pile(MID_X, MID_Z, 2, 1);
		check(lootRows().size() == 2, "two items on the floor, two rows");
		GroundItemPrefs.set("Thing 0", GroundItemPrefs.HIDE);
		check(lootRows().size() == 1,
			"hiding one takes it off the page too, so the page and the ground agree");
		GroundItemPrefs.clear();
	}

	/**
	 * The Loot nearby page, read the way the sidebar reads it - through the manager, which is
	 * the only public way in and so the only one worth testing.
	 */
	static PluginManager.PanelSnapshot lootPanel() {
		List<PluginManager.PanelSnapshot> panels = manager.snapshotPanels();
		for (int i = 0; i < panels.size(); i++) {
			if (panels.get(i).title.equals("Loot nearby")) {
				return panels.get(i);
			}
		}
		throw new IllegalStateException("the plugin has no Loot nearby page");
	}

	static List<ConfigList.Row> lootRows() {
		return lootPanel().rows;
	}

	// ---------------------------------------------------------------- 10: words and numbers

	static void formatTests() {
		// The floor is a minimum and says so; a worth is exact and must not.
		check(GroundItemsPlugin.floor(0).equals("any value"), "no floor reads as \"any value\"");
		check(GroundItemsPlugin.floor(100).equals("100 gp+"), "a floor of 100 is \"100 gp+\"");
		check(GroundItemsPlugin.floor(5000).equals("5k gp+"), "5k gp+");
		check(GroundItemsPlugin.floor(1000000).equals("1m gp+"), "1m gp+");
		check(GroundItemsPlugin.money(15000).equals("15k gp"),
			"...but an item WORTH 15000 is \"15k gp\", with no plus: it is not 15k or more");
		check(GroundItemsPlugin.money(120).equals("120 gp"), "120 gp");
		check(GroundItemsPlugin.money(2800000L).equals("2m gp"), "2m gp");

		check(GroundItemsPlugin.formatCount(5).equals("5"), "a small count is written out");
		check(GroundItemsPlugin.formatCount(99999).equals("99999"), "...up to 99999");
		check(GroundItemsPlugin.formatCount(100000).equals("100K"), "then K");
		check(GroundItemsPlugin.formatCount(10000000).equals("10M"), "then M, as the client does elsewhere");
	}

	// ---------------------------------------------------------------- the fixture

	/**
	 * A client with a scene, a camera and a player, and a manager running the one plugin.
	 *
	 * The camera is aimed so that the centre of tile (64, 64) at the label's height lands on the
	 * middle of the viewport: looking straight down the z axis (yaw and pitch zero) from 1024
	 * units back, at the height the labels are drawn at, over a flat heightmap. Every other tile
	 * then projects to a predictable offset - half a pixel per scene unit at this zoom - so
	 * "pile at tile 66" means "pile 128 pixels to the right".
	 */
	static void setUp() {
		client = new Client();
		client.levelHeightmap = new int[4][105][105];
		client.levelTileFlags = new byte[4][104][104];
		client.currentLevel = 0;
		client.cameraPitch = 0;
		client.cameraYaw = 0;
		client.cameraX = MID_X * 128 + 64;
		client.cameraZ = MID_Z * 128 + 64 - 1024;
		client.cameraY = -24;                    // the height the plugin draws a column at
		client.ingame = true;

		Client.localPlayer = new ClientPlayer();
		Client.localPlayer.field1157 = MID_X * 128;
		Client.localPlayer.field1158 = MID_Z * 128;

		Pix3D.zoom = 512;
		Pix3D.centerX = W / 2;
		Pix3D.centerY = H / 2;

		primeTypes();

		font = new RecordingFont();
		manager = new PluginManager(client, font, font, font);
		manager.reload();

		List<PluginManager.Entry> entries = manager.getPlugins();
		for (int i = 0; i < entries.size(); i++) {
			PluginManager.Entry e = entries.get(i);
			if ("ground-items".equals(e.key)) {
				entry = e;
			} else if (e.isEnabled()) {
				// Everything else off, so a label, a pixel or a settings row can only have come
				// from this plugin.
				manager.setEnabled(e, false);
			}
		}
		check(entry != null && entry.isEnabled(), "the Ground items plugin is on by default");
		if (entry == null) {
			System.out.println("1 FAILED");
			System.exit(1);
		}
	}

	/** Primes ObjType's own ten-slot cache, so ObjType.get answers without a cache file. */
	static void primeTypes() {
		ObjType.field818 = new ObjType[TYPES];
		for (int i = 0; i < TYPES; i++) {
			ObjType type = new ObjType();
			type.field845 = BASE_ID + i;
			type.field811 = "Thing " + i;
			type.field827 = 1;
			// EVEN IDS STACK, ODD ONES DO NOT. The two answer differently to "what is a pile of
			// these worth" - a stack multiplies, three separate swords do not - and a fixture
			// where everything stacked made those two indistinguishable, which is how the loot
			// page shipped a total that under-reported every non-stackable in the preview.
			type.field853 = i % 2 == 0;
			ObjType.field818[i] = type;
		}
	}

	/** An empty scene and no rules: every section starts from the same place. */
	static void reset() {
		client.objStacks = new LinkList[4][104][104];
		GroundItemPrefs.clear();
		setShowHidden(false);
		alt(false);
		frame();
	}

	/** n distinct objs on one tile, ids BASE_ID upwards, with a count on each. */
	static void pile(int tileX, int tileZ, int n, int count) {
		LinkList stack = new LinkList();
		// push() adds at the tail and the client reads a stack tail-first, so pushing in reverse
		// makes row order match id order and keeps the assertions readable.
		for (int i = n - 1; i >= 0; i--) {
			ClientObj obj = new ClientObj();
			obj.field873 = BASE_ID + i % TYPES;
			obj.field875 = count;
			stack.push(obj);
		}
		client.objStacks[0][tileX][tileZ] = stack;
	}

	/**
	 * One frame, drawn into a cleared buffer with a cleared font.
	 *
	 * Both are cleared because both accumulate, and reading an assertion across two frames is
	 * what made the first version of this harness report failures that were its own.
	 */
	static List<Drawn> frame() {
		font.rows.clear();
		java.util.Arrays.fill(pixels, 0);
		Pix2D.bind(W, H, pixels);
		manager.renderOverlays(W, H, Overlay.LAYER_SCENE);
		return font.rows;
	}

	static GroundItem item(String name, int count, int price) {
		return new GroundItem(1, name, count, price, true);
	}

	static GroundItemPile pileOf(GroundItem... items) {
		List<GroundItem> list = new ArrayList<GroundItem>();
		for (int i = 0; i < items.length; i++) {
			list.add(items[i]);
		}
		return new GroundItemPile(MID_X, MID_Z, list);
	}

	static int originX() {
		return originXAt(MID_X);
	}

	/** Where a tile's column is centred, from the projection the camera above was chosen for. */
	static int originXAt(int tileX) {
		return W / 2 + (tileX - MID_X) * 128 * Pix3D.zoom / 1024;
	}

	static int originY() {
		return H / 2;
	}

	/** The topmost row of the column centred on x, which is how two piles are told apart. */
	static String textAt(List<Drawn> rows, int x) {
		for (int i = 0; i < rows.size(); i++) {
			Drawn row = rows.get(i);
			if (Math.abs(row.x + font.widthOf(row.text) / 2 - x) <= 2) {
				return row.text;
			}
		}
		return null;
	}

	static void alt(boolean held) {
		client.actionKey[GameShell.KEY_ALT] = held ? 1 : 0;
	}

	/**
	 * Show hidden is a plugin setting now, not a cycling row in GroundItemPrefs, so this drives
	 * it through the real config - the same path the panel's switch takes.
	 */
	static void setShowHidden(boolean on) {
		setSetting("showHidden", on ? "1" : "0");
	}

	/** Writes one of the plugin's settings by key, failing loudly if there is no such setting. */
	static void setSetting(String key, String value) {
		List<PluginConfig.Item> items = entry.getConfig().getItems();
		for (int i = 0; i < items.size(); i++) {
			if (items.get(i).key.equals(key)) {
				check(entry.getConfig().set(items.get(i), value),
					"setting " + key + " to " + value + " is accepted");
				return;
			}
		}
		check(false, "there is a setting called " + key);
	}

	/** One of the plugin's settings by key, or null. */
	static PluginConfig.Item setting(String key) {
		List<PluginConfig.Item> items = entry.getConfig().getItems();
		for (int i = 0; i < items.size(); i++) {
			if (items.get(i).key.equals(key)) {
				return items.get(i);
			}
		}
		return null;
	}

	static SettingsMenuOpening.Target target(String kind, String name) {
		return new SettingsMenuOpening.Target(kind, name, "Take");
	}

	static List<SettingsMenuOpening.Row> menu(boolean world, int room, SettingsMenuOpening.Target... targets) {
		List<SettingsMenuOpening.Target> list = new ArrayList<SettingsMenuOpening.Target>();
		for (int i = 0; i < targets.length; i++) {
			list.add(targets[i]);
		}
		return manager.onSettingsMenuOpening(list, world, room);
	}

	static boolean labelled(List<SettingsMenuOpening.Row> rows, String label) {
		for (int i = 0; i < rows.size(); i++) {
			if (rows.get(i).label.equals(label)) {
				return true;
			}
		}
		return false;
	}

	static void run(List<SettingsMenuOpening.Row> rows, String label) {
		for (int i = 0; i < rows.size(); i++) {
			if (rows.get(i).label.equals(label)) {
				rows.get(i).action.run();
				return;
			}
		}
		check(false, "no menu row labelled \"" + label + "\" to choose");
	}

	// ---------------------------------------------------------------- reading the buffer

	static int painted() {
		int count = 0;
		for (int i = 0; i < pixels.length; i++) {
			if (pixels[i] != 0) {
				count++;
			}
		}
		return count;
	}

	static int leftmostPainted() {
		for (int x = 0; x < W; x++) {
			for (int y = 0; y < H; y++) {
				if (pixels[y * W + x] != 0) {
					return x;
				}
			}
		}
		return -1;
	}

	static int topmostOf(int colour) {
		for (int y = 0; y < H; y++) {
			for (int x = 0; x < W; x++) {
				if ((pixels[y * W + x] & 0xFFFFFF) == colour) {
					return y;
				}
			}
		}
		return -1;
	}

	static int bottommostOf(int colour) {
		for (int y = H - 1; y >= 0; y--) {
			for (int x = 0; x < W; x++) {
				if ((pixels[y * W + x] & 0xFFFFFF) == colour) {
					return y;
				}
			}
		}
		return -1;
	}

	// ---------------------------------------------------------------- the stub font

	/** One call to the font: where it was asked to draw, in what colour, and what it said. */
	static final class Drawn {

		final int x;
		final int y;
		final int colour;
		final String text;

		Drawn(int x, int y, int colour, String text) {
			this.x = x;
			this.y = y;
			this.colour = colour;
			this.text = text;
		}
	}

	/**
	 * A font that measures and records instead of drawing.
	 *
	 * Glyphs come out of the cache, which a headless test has none of - but a font is only its
	 * advances, its masks and a height, and nothing the overlay asks of one needs a real glyph.
	 * Every character is the same width here, which makes a centred row's left edge something
	 * the test can work out rather than read back.
	 */
	static final class RecordingFont extends PixFont {

		static final int ADVANCE = 4;

		final List<Drawn> rows = new ArrayList<Drawn>();

		RecordingFont() {
			this.height = ROW_H;
			java.util.Arrays.fill(this.charAdvance, ADVANCE);
		}

		int widthOf(String text) {
			return text == null ? 0 : text.length() * ADVANCE;
		}

		public int stringWid(String text) {
			return this.widthOf(text);
		}

		public int stringWidTag(String text) {
			return this.widthOf(text);
		}

		public void drawStringTag(int colour, int x, int y, boolean shadow, String text) {
			this.rows.add(new Drawn(x, y, colour, text));
		}

		public void drawString(int x, int colour, int y, String text) {
			this.rows.add(new Drawn(x, y, colour, text));
		}
	}
}
