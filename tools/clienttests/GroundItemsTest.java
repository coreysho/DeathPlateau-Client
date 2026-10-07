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
		formatTests();

		System.out.println(fails == 0 ? "ALL PASS" : fails + " FAILED");
		System.exit(fails == 0 ? 0 : 1);
	}

	// ---------------------------------------------------------------- 1: the rules

	/**
	 * Which rows a pile has, and in what colour, with no client at all.
	 *
	 * This is the half with the rules in it, and the half worth checking against hand-built
	 * items: a cache of ten slots cannot hold the two dozen distinct items the per-tile cap is
	 * about, and a floor is easier to be sure of against a price you wrote than one you decoded.
	 */
	static void ruleTests() {
		GroundItemPrefs.clear();
		setShowHidden(false);

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

	/** What the F11 panel used to be: two lists on the plugin's own page. */
	static void configTests() {
		reset();
		GroundItemPrefs.clear();

		List<PluginManager.ListSnapshot> lists = manager.snapshotConfigLists(entry);
		check(lists.size() == 2, "the plugin has two config lists (" + lists.size() + ")");
		if (lists.size() != 2) {
			return;
		}
		check(lists.get(0).title.equals("Display") && lists.get(1).title.equals("Items"),
			"the settings, then the rules");

		List<ConfigList.Row> display = lists.get(0).rows;
		check(display.size() == 3, "three settings (" + display.size() + ")");
		check(!display.get(0).removable && !display.get(1).removable && !display.get(2).removable,
			"none of which can be removed: they are settings, not entries");
		check(display.get(0).action.equals(GroundItemPrefs.radius() + " tiles"),
			"the radius row shows the radius");

		int was = GroundItemPrefs.radius();
		lists.get(0).act(0);
		check(GroundItemPrefs.radius() != was, "pressing it cycles to the next one ("
			+ was + " -> " + GroundItemPrefs.radius() + ")");
		check(manager.snapshotConfigLists(entry).get(0).rows.get(0).action
			.equals(GroundItemPrefs.radius() + " tiles"), "...and the row says so next time it is read");

		int value = GroundItemPrefs.minValue();
		lists.get(0).act(1);
		check(GroundItemPrefs.minValue() != value, "the value floor cycles too");

		boolean hidden = GroundItemPrefs.showHidden();
		lists.get(0).act(2);
		check(GroundItemPrefs.showHidden() != hidden, "and Show hidden is a toggle");
		lists.get(0).act(2);

		// Back to the defaults the rest of a run assumes, by going the rest of the way round.
		while (GroundItemPrefs.radius() != was) {
			GroundItemPrefs.cycleRadius();
		}
		while (GroundItemPrefs.minValue() != value) {
			GroundItemPrefs.cycleMinValue();
		}
		check(GroundItemPrefs.radius() == was && GroundItemPrefs.minValue() == value,
			"both cycle all the way round rather than stopping at the end");

		check(lists.get(1).rows.isEmpty() && lists.get(1).emptyMessage != null
			&& lists.get(1).emptyMessage.length() > 0,
			"with no rules set, the Items list says what to do instead of showing nothing");

		GroundItemPrefs.set("Bones", GroundItemPrefs.HIDE);
		lists = manager.snapshotConfigLists(entry);
		check(lists.get(1).rows.size() == 1 && lists.get(1).rows.get(0).label.equals("Bones"),
			"a rule appears as a row");
		check(lists.get(1).rows.get(0).action.equals("Hidden"), "...saying what the rule is");
		check(lists.get(1).rows.get(0).removable, "...and it can be removed, unlike a setting");
		lists.get(1).act(0);
		check(GroundItemPrefs.isHighlighted("Bones"), "pressing it cycles hidden to highlighted");
		lists = manager.snapshotConfigLists(entry);
		lists.get(1).remove(0);
		check(GroundItemPrefs.count() == 0, "and removing it drops the rule");
		GroundItemPrefs.clear();
	}

	// ---------------------------------------------------------------- 9: words and numbers

	static void formatTests() {
		check(GroundItemsPlugin.money(0).equals("any value"), "no floor reads as \"any value\"");
		check(GroundItemsPlugin.money(100).equals("100 gp+"), "100 gp+");
		check(GroundItemsPlugin.money(5000).equals("5k gp+"), "5k gp+");
		check(GroundItemsPlugin.money(1000000).equals("1m gp+"), "1m gp+");

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
			type.field853 = true;
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

	static void setShowHidden(boolean on) {
		if (GroundItemPrefs.showHidden() != on) {
			GroundItemPrefs.toggleShowHidden();
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
