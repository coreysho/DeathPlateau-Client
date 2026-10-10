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
import jagex2.client.plugin.event.KeyPressed;
import jagex2.client.plugin.PluginApi;
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
		readingTests();
		perItemColourTests();
		menuRuleTests();

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
		beamRenderTests();
		lootPageTests();
		formatTests();
		menuTests();

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
		// WRAPPED for the same reason: without the cap this runs off the end of the array, and an
		// ArrayIndexOutOfBoundsException out of the suite does not name the rule that broke.
		seen.reset();
		boolean overflowed = false;
		try {
			seen.begin();
			for (int i = 0; i < GroundItemArrivals.MAX + 500; i++) {
				seen.add(GroundItemArrivals.key(3000 + i % 64, 3000 + i / 64, 0, i));
			}
			seen.finish();
		} catch (Throwable broke) {
			overflowed = true;
		}
		check(!overflowed, "a floor past the cap does not run off the end of anything");
		check(!overflowed && seen.remembered() == GroundItemArrivals.MAX,
			"a floor past the cap remembers the cap and no more (" + seen.remembered() + ")");

		// A LOG OUT HAS TO FORGET. onGameTick needs a client and a login state, so no test here
		// can call it - and the audit found that the reset could be deleted with every check
		// above still green. Read out of the source, the way the other wiring promises are.
		String plugin = read("src/main/java/jagex2/client/plugin/builtin/GroundItemsPlugin.java");
		int tick = plugin.indexOf("public void onGameTick(");
		int tickEnd = tick < 0 ? -1 : plugin.indexOf("\n\t}", tick);
		String tickBody = tick < 0 || tickEnd < 0 ? "" : plugin.substring(tick, tickEnd);
		check(tickBody.length() > 0, "onGameTick is readable");
		int loggedOut = tickBody.indexOf("!this.ctx.isLoggedIn()");
		int firstReset = tickBody.indexOf("this.arrivals.reset()");
		check(loggedOut >= 0 && firstReset > loggedOut,
			"a logged-out client forgets the floor, so logging in elsewhere is not a storm");
		// And when nothing is asking for notifications or beams either, so turning one on later
		// does not report everything in sight at once.
		check(countOf(tickBody, "this.arrivals.reset()") == 2,
			"...and so does a tick where nothing is asking (" + countOf(tickBody, "this.arrivals.reset()") + ")");

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
		// WRAPPED, because without the fallback this throws out of the build - and an exception
		// out of the suite is a crash rather than a check, which says nothing about which rule
		// broke. The audit called that out, and it is the same reason the nonsense loops above
		// catch Throwable.
		GroundItemPalette junk = null;
		boolean junkThrew = false;
		try {
			junk = GroundItemPalette.from("zzzzzz", null, "#12", null,
				new String[] { "", "nonsense", null, "00FF00" });
		} catch (Throwable broke) {
			junkThrew = true;
		}
		check(!junkThrew, "a palette built from junk does not throw out of a render");
		check(!junkThrew && junk.plain == 0xFFFF00,
			"an unparseable colour falls back rather than throwing");
		check(!junkThrew && junk.forWorth(0L) == 0xFFFF00,
			"...and so does the palette built from it");
	}

	/**
	 * A colour per rule: that it is honoured, that it travels with its rule, and that it survives
	 * the file.
	 *
	 * EVERY ONE OF THESE CLOSES A MUTATION THAT SURVIVED. The feature shipped with its colours
	 * working and nothing asserting it - I wrote the mutations and never the checks, so all seven
	 * could be broken with the suite still green.
	 *
	 * The nastiest of them was self-inflicted. "A new rule left at the zero-filled colour" is a
	 * real production bug - colours[] is an int[], 0 is what colourFor answers for a row it is not
	 * drawing, and a player with five rules in their file has colours[5] at zero until something
	 * sets it - but reset() calls clear(), and clear() now writes DEFAULT_COLOUR across all 128
	 * entries, so the test harness was blind to exactly the bug it had caught an hour earlier. The
	 * checks below build their state WITHOUT clear() in between for that reason.
	 */
	static void perItemColourTests() {
		GroundItemPrefs.clear();

		// A rule's own colour wins over the plugin's. Without this the whole feature is a
		// drop-down that does nothing.
		GroundItemPrefs.toggle("Clue scroll", GroundItemPrefs.HIGHLIGHT);
		int at = GroundItemPrefs.find("Clue scroll");
		check(at >= 0, "the rule is there to colour");
		check(GroundItemPrefs.colour(at) == GroundItemPrefs.DEFAULT_COLOUR,
			"a new rule starts with no colour of its own");
		check(GroundItemsPlugin.colourFor(item("Clue scroll", 1, 1), false, 0)
				== GroundItemPalette.DEFAULTS.highlighted,
			"...so it draws in the plugin's highlighted colour");

		GroundItemPrefs.cycleColour(at);
		int own = GroundItemPrefs.colour(at);
		check(own != GroundItemPrefs.DEFAULT_COLOUR, "cycling gives it one");
		check(GroundItemsPlugin.colourFor(item("Clue scroll", 1, 1), false, 0) == own,
			"and the row is drawn in it, not the plugin's");

		// The same for a hidden rule, which uses the other of the two colours.
		GroundItemPrefs.toggle("Bones", GroundItemPrefs.HIDE);
		int bones = GroundItemPrefs.find("Bones");
		check(GroundItemsPlugin.colourFor(item("Bones", 1, 1), true, 0)
				== GroundItemPalette.DEFAULTS.hidden,
			"a hidden rule with no colour draws in the plugin's hidden colour");
		GroundItemPrefs.cycleColour(bones);
		check(GroundItemsPlugin.colourFor(item("Bones", 1, 1), true, 0)
				== GroundItemPrefs.colour(bones),
			"...and in its own once it has one");
		check(GroundItemsPlugin.colourFor(item("Bones", 1, 1), false, 0) == 0,
			"...but is still not drawn at all unless something reveals it");

		// A NEW RULE ADDED AFTER OTHERS, with no clear() in between: this is the shape the
		// production bug takes, and the shape clear() was hiding.
		GroundItemPrefs.toggle("Shark", GroundItemPrefs.HIGHLIGHT);
		int shark = GroundItemPrefs.find("Shark");
		check(GroundItemPrefs.colour(shark) == GroundItemPrefs.DEFAULT_COLOUR,
			"a rule added after others still starts with no colour of its own");
		check(GroundItemsPlugin.colourFor(item("Shark", 1, 1), false, 0) != 0,
			"...and is drawn, rather than vanishing into the 0 that means 'no row'");

		// THE COLOUR TRAVELS WITH ITS RULE. Left behind, removing one rule silently recolours
		// every rule after it - which a player reports as "my colours moved" long afterwards.
		GroundItemPrefs.clear();
		GroundItemPrefs.toggle("First", GroundItemPrefs.HIGHLIGHT);
		GroundItemPrefs.toggle("Second", GroundItemPrefs.HIGHLIGHT);
		GroundItemPrefs.toggle("Third", GroundItemPrefs.HIGHLIGHT);
		// Give the second and third different colours, so a shift shows up as the wrong one
		// rather than as no change.
		int second = GroundItemPrefs.find("Second");
		GroundItemPrefs.cycleColour(second);
		int third = GroundItemPrefs.find("Third");
		GroundItemPrefs.cycleColour(third);
		GroundItemPrefs.cycleColour(third);
		int secondColour = GroundItemPrefs.colourOf("Second");
		int thirdColour = GroundItemPrefs.colourOf("Third");
		check(secondColour != thirdColour, "the two rules have different colours to tell apart");

		GroundItemPrefs.removeName("First");
		check(GroundItemPrefs.colourOf("Second") == secondColour,
			"removing a rule leaves the others' colours alone");
		check(GroundItemPrefs.colourOf("Third") == thirdColour, "...all of them");

		// A hand-edited black is read as "no colour of its own", because 0 is the answer
		// colourFor gives for a row it is not drawing: a rule coloured 000000 would be an item
		// that silently disappears.
		check(GroundItemsPlugin.colourFor(item("Second", 1, 1), false, 0) != 0,
			"no rule can end up drawn in the colour that means 'not drawn'");

		// A NEW RULE MUST NOT INHERIT THE COLOUR OF A RULE THAT USED TO SIT IN ITS SLOT.
		//
		// This is the only shape in which the missing initialiser shows, and getting here took
		// two tries: a test that calls clear() first cannot see it, because clear() writes
		// DEFAULT_COLOUR across every slot. The slot has to hold a STALE colour, which is what
		// removing an earlier rule leaves behind - the shift copies colours down and the last
		// slot keeps its old value.
		GroundItemPrefs.clear();
		GroundItemPrefs.toggle("Gone", GroundItemPrefs.HIGHLIGHT);
		GroundItemPrefs.toggle("Coloured", GroundItemPrefs.HIGHLIGHT);
		GroundItemPrefs.cycleColour(GroundItemPrefs.find("Coloured"));
		int staleSlot = GroundItemPrefs.colour(GroundItemPrefs.find("Coloured"));
		check(staleSlot != GroundItemPrefs.DEFAULT_COLOUR, "a rule in the second slot has a colour");
		GroundItemPrefs.removeName("Gone");
		// "Coloured" is in slot 0 now and slot 1 still holds its colour, so a rule added next
		// lands on a dirty slot.
		GroundItemPrefs.toggle("Fresh", GroundItemPrefs.HIGHLIGHT);
		check(GroundItemPrefs.colourOf("Fresh") == GroundItemPrefs.DEFAULT_COLOUR,
			"a rule added into a vacated slot starts with no colour of its own");
		check(GroundItemsPlugin.colourFor(item("Fresh", 1, 1), false, 0)
				== GroundItemPalette.DEFAULTS.highlighted,
			"...and draws in the plugin's colour, not the one left behind");

		// The same through set(), which is the other place a rule is added.
		GroundItemPrefs.clear();
		GroundItemPrefs.set("Vanish", GroundItemPrefs.HIGHLIGHT);
		GroundItemPrefs.set("Tinted", GroundItemPrefs.HIGHLIGHT);
		GroundItemPrefs.cycleColour(GroundItemPrefs.find("Tinted"));
		GroundItemPrefs.removeName("Vanish");
		GroundItemPrefs.set("New", GroundItemPrefs.HIGHLIGHT);
		check(GroundItemPrefs.colourOf("New") == GroundItemPrefs.DEFAULT_COLOUR,
			"and so does one added by set");

		// A COLOUR LINE IS MATCHED BY NAME, NOT BY POSITION. Written deliberately out of order,
		// so a position match would give each rule the other's colour - which is only reachable
		// through a real load, because applyPendingColours runs nowhere else.
		GroundItemPrefs.clear();
		// findcachedir(), not the raw property: it appends the separator when the property has
		// none, so reading the property and concatenating wrote the file as a SIBLING of the
		// directory the client reads from. Using the same call the production code uses is the
		// only version of this that cannot drift.
		String cache = sign.signlink.findcachedir();
		boolean wrote = false;
		try {
			java.io.PrintWriter out = new java.io.PrintWriter(
				new java.io.FileWriter(cache + "qol_grounditems.dat"));
			out.println("version=1");
			out.println("show=Alpha");
			out.println("show=Beta");
			out.println("colour=Beta=00FF00");
			out.println("colour=Alpha=FF0000");
			out.close();
			wrote = true;
		} catch (Throwable cannot) {
			check(false, "could not write a settings file to " + cache + " (" + cannot + ")");
		}
		if (wrote) {
			GroundItemPrefs.load();
			check(GroundItemPrefs.colourOf("Alpha") == 0xFF0000,
				"a colour line finds its own rule by name, whatever order the lines are in");
			check(GroundItemPrefs.colourOf("Beta") == 0x00FF00, "...and so does the other");
			// And a colour for a rule that is not there is dropped rather than landing on one
			// that is.
			try {
				java.io.PrintWriter out = new java.io.PrintWriter(
					new java.io.FileWriter(cache + "qol_grounditems.dat"));
				out.println("version=1");
				out.println("show=Only");
				out.println("colour=Deleted=FF0000");
				out.close();
				GroundItemPrefs.load();
				check(GroundItemPrefs.colourOf("Only") == GroundItemPrefs.DEFAULT_COLOUR,
					"a colour for a rule that is gone is dropped, not given to whoever is there");

				// A HAND-EDITED BLACK, which is the one colour a rule may not have: 0 is what
				// colourFor answers for a row it is not drawing, so a rule coloured 000000 would
				// be an item that silently disappears. The cycle never produces black, so only a
				// file can get here - which is why the earlier check on a cycled colour could not
				// see this and the mutation survived.
				java.io.PrintWriter black = new java.io.PrintWriter(
					new java.io.FileWriter(cache + "qol_grounditems.dat"));
				black.println("version=1");
				black.println("show=Inky");
				black.println("colour=Inky=000000");
				black.close();
				GroundItemPrefs.load();
				check(GroundItemPrefs.colourOf("Inky") == GroundItemPrefs.DEFAULT_COLOUR,
					"a colour of 000000 reads as no colour of its own");
				check(GroundItemsPlugin.colourFor(item("Inky", 1, 1), false, 0)
						== GroundItemPalette.DEFAULTS.highlighted,
					"...so the item is still drawn, in the plugin's colour");
			} catch (Throwable cannot) {
				check(false, "could not rewrite the settings file (" + cannot + ")");
			}
		}

		GroundItemPrefs.clear();
	}

	/** The hotkey, the double-tap, and the beam's shape. */
	/**
	 * The three rules behind menu restyling, none of which needs a client.
	 *
	 * The interesting one is deprioritiseOrder. "Move these rows to the bottom" looks like a loop
	 * and is not: each move shifts the rows below it, and the row moved LAST ends up lowest, so
	 * the indices read before the first move are wrong by the second and the obvious order comes
	 * out reversed. The simulation at the end is the check that matters - it runs the moves against
	 * an array the same way deprioritiseMenuEntry does, so the claim is about the result rather
	 * than about the formula.
	 */
	static void menuRuleTests() {
		GroundItemPrefs.clear();

		// ---- the name out of a row
		check(GroundItemsPlugin.menuItemName("Take @lre@Bones").equals("Bones"),
			"the item name comes out of a Take row with its tag stripped");
		check(GroundItemsPlugin.menuItemName("Examine @lre@Clue scroll").equals("Clue scroll"),
			"...names with spaces intact");
		check(GroundItemsPlugin.menuItemName("Walk here").length() == 0,
			"a row with no target gives no name, rather than its own text");
		check(GroundItemsPlugin.menuItemName("Cancel").length() == 0, "...and nor does Cancel");
		check(GroundItemsPlugin.menuItemName("").length() == 0, "...nor an empty row");
		// The level suffix, because the same parse serves npc rows and a rule keyed on "Guard"
		// has to match a level-21 one.
		check(GroundItemsPlugin.menuItemName("Attack @yel@Guard@gr2@ (level-21)").equals("Guard"),
			"...and a combat level is not part of the name");

		// ---- the colour a row is drawn in
		GroundItemPalette palette = GroundItemPalette.DEFAULTS;
		GroundItemPrefs.toggle("Bones", GroundItemPrefs.HIDE);
		GroundItemPrefs.toggle("Clue scroll", GroundItemPrefs.HIGHLIGHT);

		check(GroundItemsPlugin.menuColourFor("Bones", false, false, palette) == 0,
			"with both settings off no row is recoloured");
		check(GroundItemsPlugin.menuColourFor("Clue scroll", false, false, palette) == 0,
			"...neither kind of rule");
		check(GroundItemsPlugin.menuColourFor("Bones", false, true, palette) == palette.hidden,
			"a hidden item's row takes the hidden colour");
		check(GroundItemsPlugin.menuColourFor("Clue scroll", true, false, palette)
				== palette.highlighted,
			"a highlighted item's row takes the highlighted colour");
		check(GroundItemsPlugin.menuColourFor("Bones", true, false, palette) == 0,
			"the highlight setting alone leaves a hidden item alone");
		check(GroundItemsPlugin.menuColourFor("Clue scroll", false, true, palette) == 0,
			"...and the hidden setting alone leaves a highlighted one alone");
		check(GroundItemsPlugin.menuColourFor("Shark", true, true, palette) == 0,
			"an item with no rule is never recoloured, whatever is on");
		check(GroundItemsPlugin.menuColourFor("", true, true, palette) == 0,
			"...and a row with no name is left alone rather than looked up as \"\"");

		// THE CASE THAT MAKES THAT GUARD REAL, and the one the audit found the check above could
		// not see: a rule cannot be NAMED "" through either add path, but "*" is a documented
		// hand-edit for "everything" and it matches "" like it matches anything else. A player
		// with hide=* in their file would otherwise have every targetless row - Walk here, Cancel
		// - drawn as a hidden item.
		GroundItemPrefs.toggle("*", GroundItemPrefs.HIDE);
		check(GroundItemPrefs.isHidden(""), "a hide=* rule does match the empty name");
		check(GroundItemsPlugin.menuColourFor("", false, true, palette) == 0,
			"...and a row with no name is still not coloured as a hidden item");
		check(GroundItemsPlugin.menuColourFor("Bones", false, true, palette) == palette.hidden,
			"...while a row that does have a name is");
		GroundItemPrefs.removeName("*");
		check(!GroundItemPrefs.isHidden(""), "the wildcard is gone again");

		// A rule's own colour wins here too, so the floor and the menu agree about an item. Two
		// colours for the same thing reads as two different things.
		int at = GroundItemPrefs.find("Clue scroll");
		GroundItemPrefs.cycleColour(at);
		int own = GroundItemPrefs.colour(at);
		check(own != GroundItemPrefs.DEFAULT_COLOUR, "the rule has a colour of its own");
		check(GroundItemsPlugin.menuColourFor("Clue scroll", true, true, palette) == own,
			"...and the menu row is drawn in it, not the plugin's highlighted colour");
		int bones = GroundItemPrefs.find("Bones");
		GroundItemPrefs.cycleColour(bones);
		check(GroundItemsPlugin.menuColourFor("Bones", true, true, palette)
				== GroundItemPrefs.colour(bones),
			"...the same for a hidden rule");
		GroundItemPrefs.clear();

		// ---- the order to move rows in
		check(GroundItemsPlugin.deprioritiseOrder(new int[] { 5 }, 0).length == 0,
			"nothing to move is no moves");
		int[] one = GroundItemsPlugin.deprioritiseOrder(new int[] { 5 }, 1);
		check(one.length == 1 && one[0] == 5, "one row is moved at the index it is at");
		// Two rows: the TOP one goes first, and the second one's index has gone up by one because
		// the first move shifted everything below it. Taking them bottom-first instead would
		// reverse them, which is the bug this exists to prevent.
		int[] two = GroundItemsPlugin.deprioritiseOrder(new int[] { 3, 4 }, 2);
		check(two.length == 2 && two[0] == 4,
			"two rows start with the higher one, which is the one nearer the top");
		check(two[1] == 4, "...and the lower one has been pushed up to 4 by that move");
		int[] three = GroundItemsPlugin.deprioritiseOrder(new int[] { 2, 3, 4 }, 3);
		check(three[0] == 4 && three[1] == 4 && three[2] == 4,
			"three rows that were already at the top are all moved from the same index");
		int[] gap = GroundItemsPlugin.deprioritiseOrder(new int[] { 1, 3 }, 2);
		check(gap[0] == 3 && gap[1] == 2,
			"rows with something between them: 3 first, then 1 shifted up to 2");
		check(GroundItemsPlugin.deprioritiseOrder(new int[] { 2, 3, 4, 5 }, 2).length == 2,
			"only the first count entries are read, so the reused buffer's tail is ignored");

		// THE SIMULATION. The formula above could be self-consistently wrong; this runs the moves
		// the way deprioritiseMenuEntry does and asserts where the rows end up.
		check(movedToBottom(new String[] { "Cancel", "A", "B", "H1", "H2" }, new int[] { 3, 4 }, 2)
				.equals("Cancel,H1,H2,A,B"),
			"two hidden rows end up at the bottom, above Cancel, in the order they were in");
		check(movedToBottom(new String[] { "Cancel", "H1", "A", "H2" }, new int[] { 1, 3 }, 2)
				.equals("Cancel,H1,H2,A"),
			"...and so do two with an ordinary row between them");
		check(movedToBottom(new String[] { "Cancel", "A", "H1" }, new int[] { 2 }, 1)
				.equals("Cancel,H1,A"),
			"one hidden row drops below the ordinary one above it");
		check(movedToBottom(new String[] { "Cancel", "H1", "A" }, new int[] { 1 }, 1)
				.equals("Cancel,H1,A"),
			"a hidden row already at the bottom stays where it is");
		check(movedToBottom(new String[] { "Cancel", "H1", "H2", "H3" }, new int[] { 1, 2, 3 }, 3)
				.equals("Cancel,H1,H2,H3"),
			"a menu of nothing but hidden rows is left exactly as it was");
	}

	/**
	 * The moves deprioritiseOrder asks for, run against an array the way PluginContext runs them:
	 * bubble the row at the index down to 1 by swapping with its neighbour. Returned bottom-first,
	 * so index 0 (Cancel) reads first.
	 */
	static String movedToBottom(String[] menu, int[] rows, int count) {
		int[] order = GroundItemsPlugin.deprioritiseOrder(rows, count);
		for (int j = 0; j < order.length; j++) {
			for (int at = order[j]; at > 1; at--) {
				String swap = menu[at];
				menu[at] = menu[at - 1];
				menu[at - 1] = swap;
			}
		}
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < menu.length; i++) {
			if (i > 0) {
				out.append(',');
			}
			out.append(menu[i]);
		}
		return out.toString();
	}

	static void readingTests() {
		// ONE CHARACTER, EITHER CASE EITHER WAY ROUND. The client delivers a lowercase g when
		// nobody is holding shift, so a setting of "G" has to answer to it or a hotkey typed in
		// capitals would simply never fire.
		check(Hotkey.code("g") == 'g', "a lowercase key is itself");
		check(Hotkey.code("G") == 'g', "and an uppercase one is the same key");
		check(Hotkey.code(" g ") == 'g', "whitespace round it is forgiven");
		check(Hotkey.code("4") == '4', "a digit is a key too");

		// F-keys are the client's own range, 1008 for F1, as KeyPressed documents.
		check(Hotkey.code("F1") == 1008, "F1 is the client's 1008");
		check(Hotkey.code("f12") == 1019, "and F12 its 1019, in either case");
		check(Hotkey.code("F13") == -1, "there is no F13");
		check(Hotkey.code("F0") == -1, "nor an F0");

		// No hotkey is the default, and anything unreadable is no hotkey rather than a throw.
		// "F" ON ITS OWN IS THE LETTER F, not a malformed function key - a player typing one
		// character means that character, and only F1 to F12 are the function keys. Asserted
		// because it is the ambiguous case, and because my first version of this list had it
		// down as unreadable.
		check(Hotkey.code("F") == 'f', "F on its own is the letter F");

		// A BLANK SETTING IS NOT A KEY, which is the whole of how a hotkey is turned off.
		// Hotkey.code("") is NONE, and without the `want != NONE` test in pressed() that NONE
		// is compared against the delivered key code like any other number - so the blank
		// setting becomes a hotkey bound to whatever -1 is. Nothing here pressed a blank one,
		// and the audit deleted the test with every check in this list green.
		check(!Hotkey.pressed("", Hotkey.NONE),
			"a blank hotkey is pressed by nothing, not even by the code that means no key");
		check(!Hotkey.pressed("   ", Hotkey.NONE), "...however it was left blank");
		check(!Hotkey.pressed(null, Hotkey.NONE), "...or left unset altogether");
		check(Hotkey.pressed("g", Hotkey.code("g")) && Hotkey.pressed("F1", 1008),
			"...while a hotkey that is set is pressed by its own key, so this is not a hotkey "
				+ "that stopped working");
		String[] none = { null, "", "   ", "Ctrl", "shift", "Fx", "F-1", "gg", "++" };
		for (int i = 0; i < none.length; i++) {
			boolean threw = false;
			int got = 0;
			try {
				got = Hotkey.code(none[i]);
			} catch (Throwable broke) {
				threw = true;
			}
			check(!threw, "\"" + none[i] + "\" does not throw out of a key press");
			check(!threw && got == -1, "...and is no hotkey");
		}

		// THE DOUBLE-TAP IS OFF BY DEFAULT, and that matters more than it looks: Alt is already
		// held for the [-] and [+] controls, so a player who never asked for this must not be
		// able to blank their own labels by reaching for Alt twice while looting.
		check(!GroundItemsPlugin.isDoubleTap(1000L, 900L, 0),
			"with a window of 0, two taps are not a double-tap");
		check(GroundItemsPlugin.isDoubleTap(1000L, 900L, 250),
			"inside the window they are");
		check(!GroundItemsPlugin.isDoubleTap(1000L, 700L, 250),
			"outside it they are not");
		check(GroundItemsPlugin.isDoubleTap(1000L, 750L, 250),
			"and exactly on the window still counts");
		check(!GroundItemsPlugin.isDoubleTap(1000L, 0L, 250),
			"a first tap since the client started is not a second one");

		// THE CASES THAT ACTUALLY DISCRIMINATE. The three above pass whether the guards are there
		// or not, because 1000-900 is already outside a window of 0 and 1000-0 is already outside
		// one of 250 - the audit found all three guards deletable with every check still green.
		// These are the pairs where the arithmetic alone would say yes.
		check(!GroundItemsPlugin.isDoubleTap(1000L, 1000L, 0),
			"two taps in the same millisecond are not a double-tap when the window is 0");
		check(!GroundItemsPlugin.isDoubleTap(100L, 0L, 250),
			"...and a first tap 100ms after startup is not one either, inside any window");

		// The beam's shapes have to differ, or the drop-down is decoration. The taper is over
		// the beam's own height now, so the height it is tapering across is a parameter.
		int tall = GroundItemsPlugin.DEFAULT_BEAM_SEGMENTS;
		int base = GroundItemsPlugin.beamWidth(GroundItemsPlugin.BEAM_TAPERED, 0, tall);
		check(GroundItemsPlugin.beamWidth(GroundItemsPlugin.BEAM_TAPERED, 8, tall) < base,
			"a tapered beam narrows as it rises");
		check(GroundItemsPlugin.beamWidth(GroundItemsPlugin.BEAM_STRAIGHT, 8, tall)
				== GroundItemsPlugin.beamWidth(GroundItemsPlugin.BEAM_STRAIGHT, 0, tall),
			"a straight one does not");
		check(GroundItemsPlugin.beamWidth(GroundItemsPlugin.BEAM_NARROW, 0, tall) < base,
			"and a narrow one is thinner from the floor up");
		check(GroundItemsPlugin.beamWidth("Enormous", 0, tall) == base,
			"a shape from a release that offered something else draws the default");

		// THE TAPER FOLLOWS THE HEIGHT. A taper computed over a fixed fourteen would make a
		// forty-segment beam a needle halfway up and a four-segment one barely narrow at all.
		check(GroundItemsPlugin.beamWidth(GroundItemsPlugin.BEAM_TAPERED, 3, 4)
				< GroundItemsPlugin.beamWidth(GroundItemsPlugin.BEAM_TAPERED, 3, 40),
			"the same segment is narrower in a short beam than in a tall one");

		// NEVER ZERO OR NEGATIVE, at any height or any style: a width of 0 draws nothing and a
		// negative one is whatever fillAlpha makes of it.
		boolean tooThin = false;
		String[] styles = { GroundItemsPlugin.BEAM_LOOT, GroundItemsPlugin.BEAM_TAPERED,
			GroundItemsPlugin.BEAM_STRAIGHT, GroundItemsPlugin.BEAM_NARROW, null, "" };
		for (int st = 0; st < styles.length && !tooThin; st++) {
			for (int segment = 0; segment < 200; segment++) {
				if (GroundItemsPlugin.beamWidth(styles[st], segment, tall) < 1
						|| GroundItemsPlugin.beamWidth(styles[st], segment, 2) < 1
						|| GroundItemsPlugin.beamWidth(styles[st], segment, 40) < 1) {
					tooThin = true;
					break;
				}
			}
		}
		check(!tooThin, "every shape stays at least a pixel wide, at any height, in any beam");

		// ---- WHAT MAKES IT READ AS LIGHT RATHER THAN AS A SLAB.
		//
		// The beam was fourteen segments at one flat alpha. Four things change that, and each is
		// a property of light rather than a number off a wiki: it fades as it rises, it has a
		// brighter core than its edges, it lights the ground under it, and it can pulse.
		int alpha = GroundItemsPlugin.DEFAULT_BEAM_ALPHA;
		check(GroundItemsPlugin.beamAlpha(0, 14, alpha, true)
				> GroundItemsPlugin.beamAlpha(13, 14, alpha, true),
			"a beam is brighter at the item than at the top");
		check(GroundItemsPlugin.beamAlpha(0, 14, alpha, true)
				> GroundItemsPlugin.beamAlpha(7, 14, alpha, true)
				&& GroundItemsPlugin.beamAlpha(7, 14, alpha, true)
					> GroundItemsPlugin.beamAlpha(13, 14, alpha, true),
			"...and falls off the whole way up rather than in one step");
		check(GroundItemsPlugin.beamAlpha(13, 14, alpha, true) >= 1,
			"THE TOP SEGMENT IS STILL DRAWN: a beam whose last segment is invisible is a beam "
				+ "one segment shorter, and the height setting would lose its last notch");
		// ...AND IT IS DRAWN BY THE RAMP, not by the floor underneath it. A ramp that divides by
		// the segment count rather than one more than it reaches zero on the last segment and
		// lands on the floor, so the top segment comes out at 1 whatever the player set the
		// opacity to - the floor stops it vanishing and hides that the ramp was wrong. Measured
		// as a share of the base so it is a property of the curve rather than a restatement of it.
		int top = GroundItemsPlugin.beamAlpha(13, 14, alpha, true);
		check(top * 100 / alpha >= 4,
			"...and its faintness comes from the ramp rather than from the floor: the top segment "
				+ "keeps " + (top * 100 / alpha) + "% of the beam's brightness, not the floor's 1");
		check(GroundItemsPlugin.beamAlpha(0, 14, alpha, false)
				== GroundItemsPlugin.beamAlpha(13, 14, alpha, false),
			"with the fade off every segment is the same, which is what it used to be");
		check(GroundItemsPlugin.beamAlpha(0, 1, alpha, true) == alpha,
			"a beam of one segment is its own base, not a divide by something near zero");
		boolean overBright = false;
		for (int segs = 1; segs <= 40; segs++) {
			for (int seg = 0; seg < segs; seg++) {
				int a = GroundItemsPlugin.beamAlpha(seg, segs, alpha, true);
				if (a < 1 || a > alpha) {
					overBright = true;
				}
			}
		}
		check(!overBright,
			"no segment of any beam is invisible or brighter than the beam's own setting");
		// THE FLOOR IS LOAD-BEARING AT THE EXTREMES. At the default opacity the ramp never
		// reaches zero on its own, so removing the floor changes nothing and the loop above
		// passes either way. It bites at the dimmest opacity a player can set over the tallest
		// beam they can set, where the ramp genuinely divides the brightness away.
		check(GroundItemsPlugin.beamAlpha(GroundItemsPlugin.MAX_BEAM_SEGMENTS - 1,
				GroundItemsPlugin.MAX_BEAM_SEGMENTS, GroundItemsPlugin.MIN_BEAM_ALPHA, true) >= 1,
			"the faintest beam a player can set is still drawn at its top, where the ramp would "
				+ "otherwise divide it away to nothing");

		// The core, which is narrower than the column it sits in.
		check(GroundItemsPlugin.coreWidth(GroundItemsPlugin.BEAM_W)
				< GroundItemsPlugin.BEAM_W,
			"the bright core is narrower than the beam around it");
		check(GroundItemsPlugin.coreWidth(GroundItemsPlugin.BEAM_W) > 0,
			"...and wide enough to see");
		check(GroundItemsPlugin.coreWidth(1) == 0,
			"a beam too narrow to hold a core does not get one, rather than a zero-width fill");
		check(GroundItemsPlugin.coreWidth(0) == 0, "...nor does a beam of no width");

		// The caps, because every alpha here is multiplied by something.
		check(GroundItemsPlugin.capAlpha(300) == GroundItemsPlugin.MAX_BEAM_ALPHA,
			"a doubled alpha cannot leave the range Pix2D blends over");
		check(GroundItemsPlugin.capAlpha(0) == 1 && GroundItemsPlugin.capAlpha(-5) == 1,
			"...and cannot reach nothing either");
		check(GroundItemsPlugin.beamAlphaFor(96) == 96, "an opacity in range is kept");
		check(GroundItemsPlugin.beamAlphaFor(0) == GroundItemsPlugin.MIN_BEAM_ALPHA,
			"A ZERO IS NOT HOW THE BEAM IS TURNED OFF: two switches already do that honestly, "
				+ "and a box that silently disables a feature is worse than either");
		check(GroundItemsPlugin.beamAlphaFor(999) == 255, "and nothing is more than opaque");
		check(GroundItemsPlugin.beamSegmentsFor(14) == 14, "a height in range is kept");
		check(GroundItemsPlugin.beamSegmentsFor(0) == GroundItemsPlugin.MIN_BEAM_SEGMENTS,
			"a beam of no segments is not a beam");
		check(GroundItemsPlugin.beamSegmentsFor(500) == GroundItemsPlugin.MAX_BEAM_SEGMENTS,
			"and one into the sky is bounded");
		check(GroundItemsPlugin.DEFAULT_BEAM_ALPHA == 96,
			"the opacity default is the constant the beam had before it was a setting");
		check(GroundItemsPlugin.DEFAULT_BEAM_SEGMENTS == 24,
			"and the height is taller than the fourteen it was, because the sprite this is "
				+ "modelled on is four times taller than it is wide");

		// ---- THE SHAPE, AGAINST THE SPRITE IT IS MODELLED ON.
		//
		// Jagex's loot beam asset is 383x1586 and its width profile is not a cone: measured at
		// nine heights and normalised against its widest, it is 2% of full width a tenth of the
		// way below the tip, 7% at three tenths and 12% at the halfway mark. Cubing the distance
		// below the tip gives 12.5% at the halfway mark, which is the figure that matters
		// because halfway is where the eye reads the shape. These checks are that curve, not a
		// restatement of the code: they are percentages taken off the image.
		int segs = GroundItemsPlugin.DEFAULT_BEAM_SEGMENTS;
		int span = segs - 1;
		int full = GroundItemsPlugin.lootBeamWidth(0, segs);
		check(full == GroundItemsPlugin.BEAM_W,
			"the base of a loot beam is the full width (" + full + ")");
		check(GroundItemsPlugin.lootBeamWidth(span, segs) == 1,
			"and its tip is a point");
		int half = GroundItemsPlugin.lootBeamWidth(span / 2, segs);
		check(half * 100 / full >= 9 && half * 100 / full <= 16,
			"halfway up it is about an eighth of its base, as the sprite is: " + (half * 100 / full)
				+ "% against the sprite's 12%");
		// A LINEAR TAPER WOULD BE 50% THERE and a squared one 25%, and both read as cones. This
		// is the check that says which curve, rather than merely that it narrows.
		check(half * 100 / full < 20,
			"...which a linear taper (50%) and a squared one (25%) both fail");
		int quarter = GroundItemsPlugin.lootBeamWidth(span / 4, segs);
		check(quarter > half && quarter < full,
			"a quarter of the way up it is wider than halfway and narrower than the base");

		// Monotonic, and never thinner than a pixel, at any height a player can set.
		boolean wrong = false;
		for (int n = GroundItemsPlugin.MIN_BEAM_SEGMENTS; n <= GroundItemsPlugin.MAX_BEAM_SEGMENTS;
				n++) {
			int last = Integer.MAX_VALUE;
			for (int seg = 0; seg < n; seg++) {
				int w = GroundItemsPlugin.lootBeamWidth(seg, n);
				if (w < 1 || w > last) {
					wrong = true;
				}
				last = w;
			}
		}
		check(!wrong,
			"a loot beam only ever narrows as it rises, and never below a pixel, at every height");
		// CAUGHT, NOT THROWN. A one-segment beam has a span of zero, and without the guard in
		// lootBeamWidth that span is a divisor - so this is the one check here that can be
		// reached by an exception rather than a wrong answer. Unwrapped it ended the run with no
		// failure named, which the mutation runner reports as a crash, and a crash is not a catch.
		boolean oneSegment;
		try {
			oneSegment = GroundItemsPlugin.lootBeamWidth(0, 1) == GroundItemsPlugin.BEAM_W;
		} catch (RuntimeException ex) {
			oneSegment = false;
		}
		check(oneSegment, "a beam of one segment is its base rather than a divide by zero");

		// And it is the default shape, because it is the one this was asked to look like. Read
		// off a bare instance rather than through setting(): this section runs in the headless
		// half, before any Client exists, and reaching for the config here threw and ended the
		// run with no failure named - which the mutation runner would have reported as a crash.
		check(GroundItemsPlugin.BEAM_LOOT.equals(new GroundItemsPlugin().beamStyle),
			"the loot beam shape is what a player gets without choosing one");
		check(GroundItemsPlugin.beamWidth(GroundItemsPlugin.BEAM_LOOT, span / 2, segs) == half,
			"...and the drop-down's value reaches it");

		// THE PULSE, which takes the time rather than reading the clock so its curve can be
		// checked: the shape of a pulse is exactly the sort of thing that is wrong by a factor
		// and invisible in review.
		check(GroundItemsPlugin.pulsed(alpha, 0L, false) == alpha,
			"with the pulse off the time does not matter");
		check(GroundItemsPlugin.pulsed(alpha, 900L, false) == alpha, "...at any point in it");
		int atStart = GroundItemsPlugin.pulsed(alpha, 0L, true);
		int atPeak = GroundItemsPlugin.pulsed(alpha, GroundItemsPlugin.BEAM_PULSE_MS / 2, true);
		int atEnd = GroundItemsPlugin.pulsed(alpha, GroundItemsPlugin.BEAM_PULSE_MS - 1, true);
		check(atPeak > atStart, "the pulse brightens from the start of its cycle to the middle");
		check(atEnd < atPeak, "...and dims again by the end");
		check(Math.abs(atEnd - atStart) <= 2,
			"...arriving back where it began, so the cycle does not jump (" + atStart + " -> "
				+ atEnd + ")");
		check(atPeak <= alpha,
			"the pulse never exceeds the brightness a player set (" + atPeak + " of " + alpha + ")");
		check(atStart >= 1, "...and never goes out entirely (" + atStart + ")");
		// Across two whole cycles, so the modulo is exercised rather than assumed.
		boolean pulseOut = false;
		for (long t = 0; t < GroundItemsPlugin.BEAM_PULSE_MS * 2; t += 37) {
			int a = GroundItemsPlugin.pulsed(alpha, t, true);
			if (a < 1 || a > alpha) {
				pulseOut = true;
			}
		}
		check(!pulseOut, "and stays in range across two full cycles");

		// THE KEY PRESS ITSELF. onKeyPressed touches no client state, so it can be driven on a
		// bare plugin: the audit found that dropping event.consume() changed nothing any check
		// could see, and an unconsumed hotkey also lands in the chat box.
		GroundItemsPlugin keyed = new GroundItemsPlugin();
		keyed.hotkey = "G";
		KeyPressed pressed = new KeyPressed('g');
		keyed.onKeyPressed(pressed);
		check(pressed.isConsumed(), "the hotkey is swallowed, so it does not reach the chat box");
		check(keyed.isSuppressed(), "...and hides the labels");
		KeyPressed again = new KeyPressed('g');
		keyed.onKeyPressed(again);
		check(!keyed.isSuppressed(), "pressing it again brings them back");

		// A key that is not the hotkey is left alone entirely.
		KeyPressed other = new KeyPressed('x');
		keyed.onKeyPressed(other);
		check(!other.isConsumed(), "another key is not swallowed");
		check(!keyed.isSuppressed(), "...and changes nothing");

		// And with no hotkey set, nothing is a hotkey - including the 0 that an unset code is not.
		GroundItemsPlugin unkeyed = new GroundItemsPlugin();
		KeyPressed any = new KeyPressed('g');
		unkeyed.onKeyPressed(any);
		check(!any.isConsumed() && !unkeyed.isSuppressed(),
			"with no hotkey set, no key hides the labels");

		// And the outlined text the labels can use exists where the plugin reaches for it.
		String overlay = read("src/main/java/jagex2/client/plugin/OverlayGraphics.java");
		check(overlay.indexOf("public void textCentredOutlined(") >= 0,
			"the outlined text primitive is there to be used");
		String plugin = read("src/main/java/jagex2/client/plugin/builtin/GroundItemsPlugin.java");
		check(plugin.indexOf("g.textCentredOutlined(") >= 0,
			"...and the rows use it when the player asks for an outline");
		check(plugin.indexOf("if (this.textOutline) {") >= 0, "...only when they ask");

		// THE OUTLINE'S SHAPE. Four offsets, flat, with the coloured pass unshadowed on top -
		// none of which any check here could see, because nothing renders it. The audit found all
		// four deletable. Pixels would be the strong version of this; the source is the honest
		// cheap one, and it states each property exactly.
		int outlined = overlay.indexOf("public void textCentredOutlined(");
		int outlinedEnd = outlined < 0 ? -1 : overlay.indexOf("\n\t}", outlined);
		String body = outlined < 0 || outlinedEnd < 0 ? "" : overlay.substring(outlined, outlinedEnd);
		check(body.length() > 0, "the outline is readable");
		check(countOf(body, "this.font.drawString(") == 4,
			"an outline is four offset passes, not one - a shadow is the thing it replaces ("
				+ countOf(body, "this.font.drawString(") + ")");
		check(body.indexOf("left - 1") >= 0 && body.indexOf("left + 1") >= 0
				&& body.indexOf("top - 1") >= 0 && body.indexOf("top + 1") >= 0,
			"...one each way, so no side is left bare");
		check(countOf(body, "drawStringTag(") == 1,
			"the outline passes are flat, or each would cast a shadow of its own");
		check(body.indexOf("drawStringTag(colour, left, top, false, text)") >= 0,
			"...and the coloured pass on top is unshadowed too");
		check(body.indexOf("stringWidTag(text) / 2") >= 0,
			"outlined text is centred, so a row still sits on its tile");

		// The double-tap is watched on the press edge. Alt is held for the controls, so "is it
		// down" is true throughout - this is in render, which no check here reaches.
		check(plugin.indexOf("if (alt && !this.altWasDown) {") >= 0,
			"the double-tap is watched on the press edge, not while Alt is held");
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
		check(lists.size() == 2, "the plugin has two config lists (" + lists.size() + ")");
		check(lists.size() == 2 && lists.get(0).title.equals("Items")
				&& lists.get(1).title.equals("Item colours"),
			"the rules, then a colour for each: the three cycling rows are settings now");

		// THE COLOUR LIST, which is a second list rather than a second button on each rule's row,
		// because a ConfigList row has one action and it is already spent on the mode.
		GroundItemPrefs.toggle("Clue scroll", GroundItemPrefs.HIGHLIGHT);
		lists = manager.snapshotConfigLists(entry);
		check(lists.get(1).rows.size() == 1, "a rule gets a row in the colour list");
		check(lists.get(1).rows.get(0).label.equals("Clue scroll"), "...labelled with its name");
		check(lists.get(1).rows.get(0).action.equals("Default"),
			"...starting with no colour of its own");
		check(!lists.get(1).rows.get(0).removable,
			"...and not removable there: removing a rule belongs to the Items list");

		lists.get(1).act(0);
		check(GroundItemPrefs.colourOf("Clue scroll") != GroundItemPrefs.DEFAULT_COLOUR,
			"pressing it gives the rule a colour");
		check(!manager.snapshotConfigLists(entry).get(1).rows.get(0).action.equals("Default"),
			"...and the row says which");

		// All the way round and back to no colour of its own, so nothing is a one-way door.
		for (int i = 1; i < GroundItemPrefs.PALETTE.length; i++) {
			lists.get(1).act(0);
		}
		check(GroundItemPrefs.colourOf("Clue scroll") == GroundItemPrefs.DEFAULT_COLOUR,
			"cycling all the way round comes back to Default");
		GroundItemPrefs.clear();

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

		// THE COUNT, AGAINST THE README. It said "Eighteen settings" while the plugin had
		// twenty-five, which is the sort of drift nothing notices and everyone reads. The number
		// is a digit in the README for exactly this: it is cheap to check and the check is what
		// keeps the page honest.
		int count = entry.getConfig().getItems().size();
		check(read("plugins/README.md").indexOf(
				"the one to copy from. " + count + " settings:") >= 0,
			"the README says how many settings this plugin has, and it is " + count);

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

	// ---------------------------------------------------------------- 10: the right-click menu

	/** Client.java's own action ids for the two rows an item on the floor produces. */
	static final int TAKE = 684;
	static final int EXAMINE = 1564;

	/**
	 * Recolouring and reordering Take rows, through the real manager against a real menu.
	 *
	 * NOTHING HERE IS SIMULATED. The rows are written into the client's own menu arrays the way
	 * its object handler writes them, the handler is reached by posting MenuBuilt through the real
	 * PluginManager, and what it does lands in client.menuColour and client.menuOption - which is
	 * exactly what drawMenu reads. The draw itself is MenuTest's half of this.
	 *
	 * THE ORDER IS UPSIDE DOWN throughout: index 0 is Cancel at the bottom of the menu and the
	 * HIGHEST index is the top row, which is the one a left click takes. So "deprioritise" moves a
	 * row toward index 1, and a check that reads like it has the ends swapped has not.
	 */
	static void menuTests() {
		reset();
		GroundItemPrefs.clear();

		// READ BEFORE ANYTHING WRITES THEM. All three are off out of the box, and that is the
		// decision rather than an accident: the menu is the one piece of the client a player's
		// hands know without looking, and a first run that rearranged it would have moved
		// something they were already mid-click on.
		check(setting("menuColourHighlighted") != null
				&& !setting("menuColourHighlighted").booleanValue()
				&& setting("menuColourHidden") != null
				&& !setting("menuColourHidden").booleanValue()
				&& setting("menuDeprioritiseHidden") != null
				&& !setting("menuDeprioritiseHidden").booleanValue(),
			"all three menu settings are off until a player turns one on");

		check(setting("menuColourHighlighted") != null
				&& setting("menuColourHighlighted").isBoolean(),
			"colouring highlighted rows is a switch");
		check(setting("menuColourHidden") != null && setting("menuColourHidden").isBoolean(),
			"...and so is colouring hidden ones");
		check(setting("menuDeprioritiseHidden") != null
				&& setting("menuDeprioritiseHidden").isBoolean(),
			"...and so is moving them to the bottom");
		// The level menu restyling needs, not the level the client is at: that pin moves with
		// each addition and belongs to the newest suite, which is MouseTest now. What this one
		// has to keep true is that a plugin written against 6 still runs here.
		check(PluginApi.LEVEL >= 6,
			"this client is at least API level 6 (" + PluginApi.LEVEL + ")");
		check(PluginApi.supports(6), "a plugin asking for 6 runs here");
		check(read("src/main/java/jagex2/client/plugin/builtin/GroundItemsPlugin.java")
				.indexOf("apiLevel = 6") >= 0,
			"...and the plugin declares the level it needs, so an older client refuses it rather "
				+ "than loading it and failing on the first menu");

		GroundItemPrefs.toggle("Bones", GroundItemPrefs.HIDE);
		GroundItemPrefs.toggle("Clue scroll", GroundItemPrefs.HIGHLIGHT);

		// ---- all three off: the handler returns on its first line and touches nothing. The
		// default state, and the one that runs every frame for most players.
		menu(new String[] { "Walk here", "Take @lre@Bones", "Take @lre@Clue scroll" },
			new int[] { Client.WALK_HERE_ACTION, TAKE, TAKE });
		manager.onMenuBuilt(client.menuSize);
		check(colourAt(1) == 0 && colourAt(2) == 0 && colourAt(3) == 0,
			"with all three off no row is recoloured");
		check(menuText().equals("Cancel|Walk here|Take Bones|Take Clue scroll"),
			"...and nothing is moved");

		// ---- colouring hidden rows
		setSetting("menuColourHidden", "true");
		menu(new String[] { "Walk here", "Take @lre@Bones", "Take @lre@Clue scroll" },
			new int[] { Client.WALK_HERE_ACTION, TAKE, TAKE });
		manager.onMenuBuilt(client.menuSize);
		check(colourAt(2) == HIDDEN_COLOUR, "the hidden item's Take row is drawn in grey");
		check(colourAt(3) == 0, "...and the highlighted one is not, with only this setting on");
		check(colourAt(1) == 0, "...and Walk here, which has no item name, is left alone");
		check(colourAt(0) == 0, "...and so is Cancel");

		// Only Take. An Examine on a hidden item is still a row a player might want to read, and
		// greying out everything about the item would be a different feature.
		menu(new String[] { "Take @lre@Bones", "Examine @lre@Bones" },
			new int[] { TAKE, EXAMINE });
		manager.onMenuBuilt(client.menuSize);
		check(colourAt(1) == HIDDEN_COLOUR, "the Take row is coloured");
		check(colourAt(2) == 0, "...and the Examine row for the same item is not");

		// ---- colouring highlighted rows, and a rule's own colour
		setSetting("menuColourHidden", "false");
		setSetting("menuColourHighlighted", "true");
		menu(new String[] { "Take @lre@Bones", "Take @lre@Clue scroll" },
			new int[] { TAKE, TAKE });
		manager.onMenuBuilt(client.menuSize);
		check(colourAt(2) == HIGHLIGHT_COLOUR, "the highlighted item's row is drawn in magenta");
		check(colourAt(1) == 0, "...and the hidden one is not, with only this setting on");

		int at = GroundItemPrefs.find("Clue scroll");
		GroundItemPrefs.cycleColour(at);
		int own = GroundItemPrefs.colour(at);
		menu(new String[] { "Take @lre@Clue scroll" }, new int[] { TAKE });
		manager.onMenuBuilt(client.menuSize);
		check(colourAt(1) == own && own != HIGHLIGHT_COLOUR,
			"a rule with its own colour gets it in the menu too, so the floor and the menu agree");

		// An item with no rule at all, with both colour settings on.
		setSetting("menuColourHidden", "true");
		menu(new String[] { "Take @lre@Shark" }, new int[] { TAKE });
		manager.onMenuBuilt(client.menuSize);
		check(colourAt(1) == 0, "an item with no rule keeps the white the client drew it in");

		// ---- moving hidden rows to the bottom
		setSetting("menuColourHidden", "false");
		setSetting("menuColourHighlighted", "false");
		setSetting("menuDeprioritiseHidden", "true");

		menu(new String[] { "Walk here", "Take @lre@Bones", "Take @lre@Shark" },
			new int[] { Client.WALK_HERE_ACTION, TAKE, TAKE });
		manager.onMenuBuilt(client.menuSize);
		check(menuText().equals("Cancel|Take Bones|Walk here|Take Shark"),
			"the hidden item's row drops to the bottom, below Walk here, and the rest keep order");
		check(client.menuAction[1] == TAKE,
			"...and its action travelled with it, so the row still takes the item");

		// The left-click is the top index, and moving a hidden row out of it is the point: an
		// accidental left click should not pick up the thing you told the client to hide.
		menu(new String[] { "Take @lre@Shark", "Take @lre@Bones" }, new int[] { TAKE, TAKE });
		check(menuText().equals("Cancel|Take Shark|Take Bones"), "Bones starts as the left-click");
		manager.onMenuBuilt(client.menuSize);
		check(menuText().equals("Cancel|Take Bones|Take Shark"),
			"...and is moved out of it, leaving Shark as what a left click takes");

		// Two hidden rows keep their order relative to each other.
		GroundItemPrefs.toggle("Burnt bread", GroundItemPrefs.HIDE);
		menu(new String[] { "Take @lre@Burnt bread", "Take @lre@Shark", "Take @lre@Bones" },
			new int[] { TAKE, TAKE, TAKE });
		manager.onMenuBuilt(client.menuSize);
		check(menuText().equals("Cancel|Take Burnt bread|Take Bones|Take Shark"),
			"two hidden rows both drop below the ordinary one, in the order they were in");

		// ---- both at once: THE COLOUR HAS TO TRAVEL WITH THE ROW. Overrides are stored by index,
		// so a colour left behind lands on whatever row took the place of the one that moved -
		// which with these two settings on is the ordinary item the player wanted to see.
		setSetting("menuColourHidden", "true");
		menu(new String[] { "Take @lre@Shark", "Take @lre@Bones" }, new int[] { TAKE, TAKE });
		manager.onMenuBuilt(client.menuSize);
		check(menuText().equals("Cancel|Take Bones|Take Shark"), "the hidden row moved");
		check(colourAt(1) == HIDDEN_COLOUR, "...and its grey went with it");
		check(colourAt(2) == 0, "...and the row that took its place is not wearing it");

		// Three rows, so the colour has further to travel than one swap.
		menu(new String[] { "Walk here", "Take @lre@Shark", "Take @lre@Bones" },
			new int[] { Client.WALK_HERE_ACTION, TAKE, TAKE });
		manager.onMenuBuilt(client.menuSize);
		check(menuText().equals("Cancel|Take Bones|Walk here|Take Shark"), "it moved two places");
		check(colourAt(1) == HIDDEN_COLOUR && colourAt(2) == 0 && colourAt(3) == 0,
			"...and its colour is on it and nowhere else");

		// ---- a menu with nothing in it, and one with no Take rows: neither is a special case in
		// the handler, and both happen every frame the cursor is over scenery.
		menu(new String[] {}, new int[] {});
		manager.onMenuBuilt(client.menuSize);
		check(client.menuSize == 1 && colourAt(0) == 0, "a Cancel-only menu comes back untouched");
		menu(new String[] { "Walk here", "Open @cya@Door" },
			new int[] { Client.WALK_HERE_ACTION, 53 });
		manager.onMenuBuilt(client.menuSize);
		check(menuText().equals("Cancel|Walk here|Open Door") && colourAt(1) == 0
				&& colourAt(2) == 0,
			"a menu with no Take rows is untouched");

		// A hide=* rule, which is the hand-edit for "hide everything". Every Take row is hidden,
		// and the rows with no item name must still be left alone: the handler skips them before
		// it ever asks about a rule, and menuColourFor guards the same thing behind it.
		setSetting("menuColourHidden", "true");
		setSetting("menuDeprioritiseHidden", "true");
		GroundItemPrefs.toggle("*", GroundItemPrefs.HIDE);
		menu(new String[] { "Walk here", "Take @lre@Shark" },
			new int[] { Client.WALK_HERE_ACTION, TAKE });
		manager.onMenuBuilt(client.menuSize);
		check(menuText().equals("Cancel|Take Shark|Walk here"),
			"under hide=* the Take row drops to the bottom and Walk here stays above it");
		check(colourAt(1) == HIDDEN_COLOUR, "...the Take row is grey");
		check(colourAt(2) == 0, "...and Walk here, which has no item name, is not");

		// A TAKE ROW WHOSE ITEM HAS NO NAME. The client writes "Take @lre@" + name, so an obj
		// type with an empty name gives a row with no readable target - and a hide=* rule matches
		// the empty name, so without the skip in the scan that row joins the ones being moved and
		// drags the menu around. Three rows, because two adjacent moves cancel out and would make
		// a missing skip look harmless.
		menu(new String[] { "Take @lre@", "Walk here", "Take @lre@Shark" },
			new int[] { TAKE, Client.WALK_HERE_ACTION, TAKE });
		manager.onMenuBuilt(client.menuSize);
		check(menuText().equals("Cancel|Take Shark|Take |Walk here"),
			"a Take row whose item has no name is left where it is, and only Shark moves");
		check(colourAt(2) == 0, "...and it is not coloured either");
		GroundItemPrefs.removeName("*");

		// ---- and turning everything off again leaves the menu alone, so none of this is sticky.
		setSetting("menuColourHidden", "false");
		setSetting("menuDeprioritiseHidden", "false");
		menu(new String[] { "Take @lre@Shark", "Take @lre@Bones" }, new int[] { TAKE, TAKE });
		manager.onMenuBuilt(client.menuSize);
		check(menuText().equals("Cancel|Take Shark|Take Bones") && colourAt(1) == 0
				&& colourAt(2) == 0,
			"switching the settings back off leaves the menu exactly as the client built it");
		GroundItemPrefs.clear();

		// ---- THE LINE THE API IS DRAWN ON, which no run of it can state: the context can colour
		// a row and move a row, and there is no way through it to change what a row SAYS or what
		// it DOES. A plugin that could relabel one could put "Bank" where "Attack" is, and the
		// whole API rests on a plugin drawing and reading while the client owns input.
		String ctx = read("src/main/java/jagex2/client/plugin/PluginContext.java");
		check(ctx.indexOf("public void setMenuColour(") >= 0, "the context can colour a row");
		check(ctx.indexOf("public void deprioritiseMenuEntry(") >= 0, "...and move one");
		check(ctx.indexOf("setMenuOption(") < 0,
			"...and there is no way to change what a row says");
		check(ctx.indexOf("setMenuAction(") < 0, "...nor what it does");
		check(ctx.indexOf("public void removeMenuEntry(") < 0
				&& ctx.indexOf("public void addMenuEntry(") < 0,
			"...nor to add or remove one, which is why Collapse is not here");

		// The guards, which a test cannot reach: PluginContext's constructor is package-private,
		// so the only way in is the real manager, and the real manager only ever passes indices
		// the handler read out of the live menu.
		int setter = ctx.indexOf("public void setMenuColour(");
		String setterBody = setter < 0 ? "" : ctx.substring(setter, ctx.indexOf("\n\t}", setter));
		check(setterBody.indexOf("index >= 0") >= 0 && setterBody.indexOf("index < this.client.menuSize") >= 0,
			"a colour outside the live menu is dropped rather than written past it");
		check(setterBody.indexOf("index < this.client.menuColour.length") >= 0,
			"...and so is one past the end of the array, whatever menuSize says");
		int dep = ctx.indexOf("public void deprioritiseMenuEntry(");
		String depBody = dep < 0 ? "" : ctx.substring(dep, ctx.indexOf("\n\t}", dep));
		check(depBody.indexOf("index <= 1") >= 0,
			"index 1 is already the bottom, and Cancel at 0 stays there");
		check(depBody.indexOf("this.swapMenuEntries(at, at - 1)") >= 0,
			"...and the move is built on swapMenuEntries, so a row's action goes with its text");
		int swap = ctx.indexOf("public void swapMenuEntries(");
		String swapBody = swap < 0 ? "" : ctx.substring(swap, ctx.indexOf("\n\t}", swap));
		check(swapBody.indexOf("this.client.menuColour[a] = this.client.menuColour[b]") >= 0,
			"...and a row's colour goes with it too, or a moved row leaves its colour behind");
	}

	/**
	 * A menu the way the client builds one: "Cancel" at index 0 and the given rows appended above
	 * it, so the LAST one is the top row and the left click.
	 */
	static void menu(String[] options, int[] actions) {
		client.menuSize = 0;
		for (int i = 0; i < client.menuColour.length; i++) {
			client.menuColour[i] = 0;
		}
		client.menuOption[0] = "Cancel";
		client.menuAction[0] = 1016;
		client.menuSize = 1;
		for (int i = 0; i < options.length; i++) {
			client.menuOption[client.menuSize] = options[i];
			client.menuAction[client.menuSize] = actions[i];
			client.menuParamA[client.menuSize] = 0;
			client.menuParamB[client.menuSize] = 0;
			client.menuParamC[client.menuSize] = 0;
			client.menuSize++;
		}
	}

	static int colourAt(int index) {
		return client.menuColour[index];
	}

	/** The menu bottom-first, tags stripped, so a check reads as the array is laid out. */
	static String menuText() {
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < client.menuSize; i++) {
			if (i > 0) {
				out.append('|');
			}
			out.append(jagex2.client.DevLog.stripTags(client.menuOption[i]));
		}
		return out.toString();
	}

	// ---------------------------------------------------------------- 9: the Loot nearby page

	/**
	 * The rail page, which asks a different question of the same scene than the overlay does.
	 *
	 * Everything here was wrong in the first version and right in the picture, which is the
	 * argument for the test: an aggregate that silently under-reports looks exactly like one
	 * that does not.
	 */
	// ---------------------------------------------------------------- the beam, drawn

	/**
	 * The beam as pixels, which is the half of it the arithmetic checks cannot see.
	 *
	 * Every helper above is pure and was checked as one, and all of them passed while four of
	 * the beam's settings did nothing at all: the audit broke drawBeam so the core was always
	 * drawn, never drawn, the glow always drawn, never drawn, the height pinned to its old
	 * constant and the opacity to its, and not one check noticed. A pure function tested in
	 * isolation says what it computes, never that anybody calls it.
	 *
	 * So this section drives the real settings through the real config and counts pixels. The
	 * beam is reached by HIGHLIGHTING an item rather than by price: a highlighted item gets a
	 * beam whatever it is worth, which keeps the price table out of a test about drawing.
	 */
	static void beamRenderTests() {
		reset();
		pile(MID_X, MID_Z, 1, 1);
		GroundItemPrefs.set("Thing 0", GroundItemPrefs.HIGHLIGHT);
		// The pulse reads the wall clock, so with it on no two frames here are comparable.
		setSetting("beamPulse", "0");
		setSetting("beamStyle", GroundItemsPlugin.BEAM_LOOT);
		setSetting("beamSegments", "24");
		setSetting("beamOpacity", "96");
		setSetting("beamCore", "1");
		setSetting("beamGlow", "1");

		setSetting("beamHighlighted", "0");
		frame();
		int unbeamed = countPainted();
		setSetting("beamHighlighted", "1");
		frame();
		int beamed = countPainted();
		check(beamed > unbeamed,
			"a highlighted item with beams on paints pixels a highlighted item without them "
				+ "does not (" + unbeamed + " -> " + beamed + ")");
		check(beamed - unbeamed > GroundItemsPlugin.BEAM_W,
			"...and enough of them to be a column rather than a stray fill");

		// ---- THE GROUND GLOW. Twice the beam's width, so the only pixels it can account for
		// are the ones out past the column's own edge.
		setSetting("beamGlow", "1");
		frame();
		int withGlow = countOutsideColumn();
		setSetting("beamGlow", "0");
		frame();
		int withoutGlow = countOutsideColumn();
		check(withGlow > withoutGlow,
			"the pool of light on the ground is wider than the beam standing in it ("
				+ withoutGlow + " -> " + withGlow + ")");
		check(withoutGlow == 0,
			"...and with it switched off nothing is painted out there at all, so the switch is "
				+ "the only thing that draws it");

		// ---- THE BRIGHT CORE. Drawn inside the column at twice the alpha, over pixels the soft
		// outer already covered - so it adds no pixels and the count cannot see it. What it adds
		// is light, which is what the eye reads and what this measures.
		setSetting("beamGlow", "0");
		setSetting("beamCore", "0");
		frame();
		int soft = countPainted();
		long softLight = totalLight();
		setSetting("beamCore", "1");
		frame();
		int cored = countPainted();
		long coredLight = totalLight();
		check(coredLight > softLight,
			"a beam with a core is brighter than the same beam without one ("
				+ softLight + " -> " + coredLight + ")");
		check(cored == soft,
			"...and no wider, because the core sits inside the column rather than beside it");

		// ---- THE HEIGHT. A taller beam is more pixels; the setting is the only thing that says
		// how many, and pinning it to the old constant is a drop-down that does nothing.
		setSetting("beamCore", "0");
		setSetting("beamSegments", "6");
		frame();
		int shortBeam = countPainted();
		setSetting("beamSegments", "30");
		frame();
		int tallBeam = countPainted();
		check(tallBeam > shortBeam,
			"a beam set tall is drawn taller than one set short (" + shortBeam + " -> "
				+ tallBeam + ")");
		check(shortBeam > unbeamed,
			"...and the short one is still a beam, so this is a height rather than an off switch");

		// ---- THE OPACITY. Same pixels, more light, for the same reason the core is measured
		// this way rather than counted.
		setSetting("beamSegments", "24");
		setSetting("beamOpacity", "16");
		frame();
		long faint = totalLight();
		int faintWidth = widestRow();
		setSetting("beamOpacity", "255");
		frame();
		long solid = totalLight();
		int solidWidth = widestRow();
		check(solid > faint,
			"a beam set solid puts down more light than one set faint (" + faint + " -> "
				+ solid + ")");
		// NOT "the same pixels": Pix2D blends (channel * alpha) >> 8, so over a cleared buffer
		// the faintest segments of a dim beam round to literal zero and read as unpainted. A
		// dim beam really does paint fewer pixels, and the first version of this check called
		// that a failure. Its width at the base is the measure that holds - that is the one
		// place every beam is BEAM_W whatever its opacity.
		check(solidWidth == faintWidth && solidWidth == GroundItemsPlugin.BEAM_W,
			"...and no wider at its base, so it is an opacity rather than a size ("
				+ faintWidth + " and " + solidWidth + ")");

		GroundItemPrefs.clear();
		reset();
	}

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
		// A COPY. No check here holds two frames at once, but returning the font's own list means
		// the next frame clears and refills the same object - so the day one does, "it changed"
		// could never be false. XpDropsTest lost a check to exactly that.
		return new ArrayList<Drawn>(font.rows);
	}

	/** Pixels the last frame wrote, which is how a fill is told apart from nothing. */
	static int countPainted() {
		int n = 0;
		for (int i = 0; i < pixels.length; i++) {
			if (pixels[i] != 0) {
				n++;
			}
		}
		return n;
	}

	/**
	 * How much light the last frame put down: the brightest channel of every pixel, summed.
	 *
	 * The core and the opacity both draw over pixels something already covered, so neither
	 * changes how many are painted - only how bright they are. Counting cannot see either.
	 */
	static long totalLight() {
		long sum = 0;
		for (int i = 0; i < pixels.length; i++) {
			int p = pixels[i];
			int r = p >> 16 & 0xFF;
			int g = p >> 8 & 0xFF;
			int b = p & 0xFF;
			int max = r > g ? r : g;
			sum += max > b ? max : b;
		}
		return sum;
	}

	/**
	 * The widest single row the last frame painted, which is a beam's width at its base.
	 *
	 * Counting whole frames cannot separate "brighter" from "bigger", because a dim beam's top
	 * segments blend to zero and drop out of the count. Its base does not.
	 */
	static int widestRow() {
		int widest = 0;
		for (int y = 0; y < H; y++) {
			int n = 0;
			for (int x = 0; x < W; x++) {
				if (pixels[y * W + x] != 0) {
					n++;
				}
			}
			if (n > widest) {
				widest = n;
			}
		}
		return widest;
	}

	/**
	 * Painted pixels out past the beam column's own edge, where only the ground glow reaches.
	 *
	 * The beam is BEAM_W wide at its base and narrower above; the glow is twice that. So a
	 * pixel more than half the beam's width from the centre line came from the glow or from
	 * nothing, which is what makes the glow switch observable at all.
	 */
	static int countOutsideColumn() {
		int cx = originX();
		int n = 0;
		for (int y = 0; y < H; y++) {
			for (int x = 0; x < W; x++) {
				if (pixels[y * W + x] != 0 && Math.abs(x - cx) > GroundItemsPlugin.BEAM_W / 2 + 1) {
					n++;
				}
			}
		}
		return n;
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

	static int countOf(String haystack, String needle) {
		int n = 0;
		for (int at = haystack.indexOf(needle); at >= 0; at = haystack.indexOf(needle, at + 1)) {
			n++;
		}
		return n;
	}

	static String read(String path) {
		try {
			return new String(java.nio.file.Files.readAllBytes(
				new java.io.File(System.getProperty("dp.root", "."), path).toPath()), "UTF-8");
		} catch (Throwable missing) {
			check(false, "cannot read " + path + " (" + missing + ")");
			return "";
		}
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
