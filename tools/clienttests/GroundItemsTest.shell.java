// Shell for tools/clienttests/run_groundtest.py. The declarations and the four methods under test
// are spliced in from jagex2/client/Client.java at run time - everything below them is the harness.
import jagex2.client.GameShell;
import jagex2.client.Layout;
import jagex2.client.GroundItemPrefs;
import jagex2.client.QolSettings;
import jagex2.config.ObjType;
import jagex2.dash3d.ClientObj;
import jagex2.dash3d.ClientPlayer;
import jagex2.datastruct.LinkList;
import jagex2.graphics.Pix2D;

import java.util.ArrayList;
import java.util.List;

/** What the extracted methods reach for with super. */
class GiShellBase {
	public int[] actionKey = new int[128];
	public int mouseX;
	public int mouseY;
	public int mouseClickX;
	public int mouseClickY;
	public int mouseScrollDelta;
}

/** One drawn row, as the font saw it. */
class Row {
	final int x;
	final int y;
	final int colour;
	final String text;

	Row(int x, int y, int colour, String text) {
		this.x = x;
		this.y = y;
		this.colour = colour;
		this.text = text;
	}

	public String toString() {
		return text + "@(" + x + "," + y + ")";
	}
}

/**
 * The one stub. PixFont needs a Jagfile to construct and its metrics are cache data, so the font is
 * stood in for - and recording every (x, y, colour, text) is exactly the measurement these tests
 * want. Shadow draws (the +1/+1 black pass) are dropped so a row is one Row.
 */
class FontStub {
	public int height = 12;
	final List<Row> rows = new ArrayList<Row>();

	public int stringWid(String s) {
		return s.length() * 4;
	}

	public void drawString(int x, int colour, int y, String s) {
		if (colour != 0x000000) {
			rows.add(new Row(x, y, colour, s));
		}
	}

	public void centreString(int x, int y, int colour, String s) {
		if (colour != 0x000000) {
			rows.add(new Row(x, y, colour, s));
		}
	}
}

public class GroundItemsTest extends GiShellBase {

	// @@DECLS@@

	// The fixed viewport's origin in the mouse's coordinates (Layout.fixed().vpX/vpY), which is
	// where the harness puts the mouse relative to the labels it draws in viewport pixels.
	static final int QOL_PANEL_ORIGIN = 4;

	// @@METHODS@@

	// ------------------------------------------------------------------ the collaborators
	static ClientPlayer localPlayer;
	int currentLevel;
	LinkList[][][] objStacks = new LinkList[4][104][104];
	int projectX;
	int projectY;
	FontStub fontPlain11 = new FontStub();
	final List<String> messages = new ArrayList<String>();

	/**
	 * A projection that is a straight line rather than a camera: every tile lands at its own
	 * predictable spot well inside the accept box, so a row's y IS its position in the column and
	 * the tests can say "12 apart" and mean it.
	 */
	void projectFromGround(int x, int height, int z) {
		int tileX = (x - 64) >> 7;
		int tileZ = (z - 64) >> 7;
		this.projectX = 260 + (tileX - 64) * 40;
		this.projectY = 200 + (tileZ - 64) * 40;
	}

	void addMessage(String a, String b, int c) {
		this.messages.add(b);
	}

	static String formatObjCount(int n) {
		return jagex2.client.Client.formatObjCount(n);   // the real one, not a copy of it
	}

	// ------------------------------------------------------------------ the fixture
	static final int BASE_ID = 900;
	static int[] buf = new int[520 * 340];

	/** Primes ObjType's own 10-slot cache so ObjType.get() answers without a cache file. */
	static void primeTypes(String[] names, int[] prices) {
		ObjType.field818 = new ObjType[10];
		for (int i = 0; i < 10; i++) {
			ObjType t = new ObjType();
			t.field845 = BASE_ID + i;
			t.field811 = i < names.length ? names[i] : ("Thing " + i);
			t.field827 = i < prices.length ? prices[i] : 1;
			t.field853 = false;
			ObjType.field818[i] = t;
		}
	}

	/** n distinct objs on one tile, ids BASE_ID..BASE_ID+n-1, one of each. */
	void pile(int tileX, int tileZ, int n) {
		pile(tileX, tileZ, n, 1);
	}

	/**
	 * ...with a count on every one. A count above 1 makes the LABEL ("Coins x 4") differ from the
	 * NAME ("Coins"), which is the only way to tell whether the click zones carry the name the
	 * player's rules are keyed on or the label they are not.
	 */
	void pile(int tileX, int tileZ, int n, int count) {
		LinkList l = new LinkList();
		// push() adds at the tail and drawGroundItems walks tail()/prev(), so pushing in reverse
		// makes row order match id order and keeps the assertions readable.
		for (int i = n - 1; i >= 0; i--) {
			ClientObj o = new ClientObj();
			o.field873 = BASE_ID + i;
			o.field875 = count;
			l.push(o);
		}
		this.objStacks[0][tileX][tileZ] = l;
	}

	GroundItemsTest fresh() {
		GroundItemsTest c = new GroundItemsTest();
		c.currentLevel = 0;
		localPlayer = new ClientPlayer();
		localPlayer.field1157 = 64 * 128;
		localPlayer.field1158 = 64 * 128;
		java.util.Arrays.fill(buf, 0);
		Pix2D.bind(520, 340, buf);
		return c;
	}

	/**
	 * One frame. The font's rows and the pixel buffer both accumulate, so every assertion reads a
	 * single draw - reading across two of them is what made the first run of this harness report
	 * failures that were its own.
	 */
	List<Row> redraw() {
		this.fontPlain11.rows.clear();
		java.util.Arrays.fill(buf, 0);
		Pix2D.bind(520, 340, buf);
		this.drawGroundItems();
		return this.fontPlain11.rows;
	}

	static int pass;
	static int fail;

	static void check(boolean ok, String what) {
		if (ok) {
			pass++;
			System.out.println("  ok   " + what);
		} else {
			fail++;
			System.out.println("FAIL   " + what);
		}
	}

	static int painted() {
		int n = 0;
		for (int i = 0; i < buf.length; i++) {
			if (buf[i] != 0) {
				n++;
			}
		}
		return n;
	}

	/** The x of the left-most painted pixel, or -1. */
	static int paintedLeft() {
		for (int x = 0; x < 520; x++) {
			for (int y = 0; y < 340; y++) {
				if (buf[y * 520 + x] != 0) {
					return x;
				}
			}
		}
		return -1;
	}

	/** The y of the top-most pixel of the thumb colour, or -1. */
	static int thumbTop() {
		for (int y = 0; y < 340; y++) {
			for (int x = 0; x < 520; x++) {
				if (buf[y * 520 + x] == GI_BAR_THUMB) {
					return y;
				}
			}
		}
		return -1;
	}

	public static void main(String[] args) {
		GroundItemPrefs.clear();
		System.out.println("1. a hidden row does not leave a hole");
		gapTests();
		System.out.println("2. a tall pile shows a window of itself");
		windowTests();
		System.out.println("3. the wheel scrolls the pile under the cursor");
		scrollTests();
		System.out.println("4. the scroll bar");
		barTests();
		System.out.println("5. what Alt still does");
		altTests();
		System.out.println();
		System.out.println(fail == 0 ? (pass + " CHECKS, ALL PASS") : (fail + " FAILED of " + (pass + fail)));
		System.exit(fail == 0 ? 0 : 1);
	}

	// ---------------------------------------------------------------- 1
	static void gapTests() {
		primeTypes(new String[] { "Coins", "Bones", "Iron dagger" }, new int[] { 1, 1, 1 });
		GroundItemPrefs.clear();
		GroundItemsTest c = new GroundItemsTest().fresh();
		c.pile(64, 64, 3);
		c.redraw();
		List<Row> r = c.fontPlain11.rows;
		check(r.size() == 3, "three items, nothing hidden: three rows (" + r.size() + ")");
		check(r.size() == 3 && r.get(2).y == c.projectY,
			"...the bottom row lands on the tile, so the column grows upwards");
		check(r.size() == 3 && r.get(1).y - r.get(0).y == GROUND_ITEM_ROW_H
			&& r.get(2).y - r.get(1).y == GROUND_ITEM_ROW_H,
			"...one row height apart, top to bottom");
		int tallest = r.isEmpty() ? 0 : r.get(0).y;

		GroundItemPrefs.set("Bones", GroundItemPrefs.HIDE);
		c = new GroundItemsTest().fresh();
		c.pile(64, 64, 3);
		c.redraw();
		r = c.fontPlain11.rows;
		check(r.size() == 2, "hide the middle one: two rows (" + r.size() + ")");
		check(r.size() == 2 && r.get(1).y - r.get(0).y == GROUND_ITEM_ROW_H,
			"...and they are ADJACENT - the hidden row leaves no hole, which is the whole ask: "
				+ (r.size() == 2 ? (r.get(1).y - r.get(0).y) : -1) + "px apart");
		check(r.size() == 2 && r.get(1).y == c.projectY,
			"...the bottom row still lands on the tile");
		check(r.size() == 2 && r.get(0).y > tallest,
			"...and the column is shorter than it was, rather than the same height with a gap");

		// The value floor takes the same path, and so does an obj type with no name at all.
		GroundItemPrefs.clear();
		// The floor is a cycled preset, not a number you set: one step off zero is 100, which is
		// above the price-1 rows below and under the price-900 ones.
		GroundItemPrefs.cycleMinValue();
		primeTypes(new String[] { "Coins", "Bones", "Iron dagger" }, new int[] { 900, 1, 900 });
		c = new GroundItemsTest().fresh();
		c.pile(64, 64, 3);
		c.redraw();
		r = c.fontPlain11.rows;
		check(r.size() == 2 && r.get(1).y - r.get(0).y == GROUND_ITEM_ROW_H,
			"a row under the value floor closes up the same way");
		while (GroundItemPrefs.minValue() != 0) {
			GroundItemPrefs.cycleMinValue();
		}

		primeTypes(new String[] { "Coins", "Bones", "Iron dagger" }, new int[] { 1, 1, 1 });
		ObjType.field818[1].field811 = null;
		c = new GroundItemsTest().fresh();
		c.pile(64, 64, 3);
		c.redraw();
		r = c.fontPlain11.rows;
		check(r.size() == 2 && r.get(1).y - r.get(0).y == GROUND_ITEM_ROW_H,
			"so does a row whose obj type has no name - the other half of the same bug");

		primeTypes(new String[] { "Coins", "Bones", "Iron dagger" }, new int[] { 1, 1, 1 });
		GroundItemPrefs.set("Coins", GroundItemPrefs.HIDE);
		GroundItemPrefs.set("Bones", GroundItemPrefs.HIDE);
		GroundItemPrefs.set("Iron dagger", GroundItemPrefs.HIDE);
		c = new GroundItemsTest().fresh();
		c.pile(64, 64, 3);
		c.redraw();
		check(c.fontPlain11.rows.isEmpty() && c.giPileCount == 0,
			"a pile with every row hidden draws nothing and records no pile to scroll");

		GroundItemPrefs.toggleShowHidden();
		c = new GroundItemsTest().fresh();
		c.pile(64, 64, 3);
		c.redraw();
		r = c.fontPlain11.rows;
		check(r.size() == 3 && r.get(0).colour == GROUND_ITEM_HIDDEN,
			"...and reveal brings all three back, in the hidden colour");
		GroundItemPrefs.toggleShowHidden();
		GroundItemPrefs.clear();
	}

	// ---------------------------------------------------------------- 2
	static void windowTests() {
		primeTypes(new String[0], new int[0]);
		GroundItemPrefs.clear();
		GroundItemsTest c = new GroundItemsTest().fresh();
		c.pile(64, 64, 10);
		c.redraw();
		List<Row> r = c.fontPlain11.rows;
		check(r.size() == GI_ROWS_SHOWN,
			"ten distinct items draw GI_ROWS_SHOWN rows, not ten (" + r.size() + ")");
		check(c.giPileCount == 1 && c.giPileRows[0] == 10,
			"...and the pile records all ten, which is what there is to scroll to");
		check(r.size() == GI_ROWS_SHOWN && r.get(0).text.equals("Thing 0"),
			"...showing the top of the pile first: " + (r.isEmpty() ? "-" : r.get(0).text));
		check(r.size() == GI_ROWS_SHOWN && r.get(r.size() - 1).y == c.projectY,
			"...and the window's last row is the one on the tile");
		// NOT TESTED HERE, and worth saying why rather than faking it: the tracking cap itself
		// needs a pile of more than GROUND_ITEM_MAX_PER_TILE DISTINCT ids, and this harness gets
		// its obj types by priming ObjType's own cache, which the real ObjType.get() scans ten
		// entries of. Eleven distinct ids would decode from a cache file there is none of. What can
		// be checked is the claim that actually matters, which is that the cap leaves something to
		// scroll to at all - a tracking cap equal to the rows shown would make the wheel pointless.
		check(GROUND_ITEM_MAX_PER_TILE > GI_ROWS_SHOWN,
			"more ids are tracked than are drawn, so a tall pile has rows to scroll to ("
				+ GROUND_ITEM_MAX_PER_TILE + " tracked, " + GI_ROWS_SHOWN + " shown)");
	}

	// ---------------------------------------------------------------- 3
	static void scrollTests() {
		primeTypes(new String[0], new int[0]);
		GroundItemPrefs.clear();
		GroundItemsTest c = new GroundItemsTest().fresh();
		c.pile(64, 64, 10);
		c.redraw();
		int mx = (c.giPileLeft[0] + c.giPileRight[0]) / 2 + QOL_PANEL_ORIGIN;
		int my = (c.giPileTop[0] + c.giPileBottom[0]) / 2 + QOL_PANEL_ORIGIN;

		c.mouseX = mx;
		c.mouseY = my;
		c.mouseScrollDelta = 1;
		boolean took = c.handleGroundItemScroll();
		check(took && c.mouseScrollDelta == 0,
			"a wheel turn over a tall pile is consumed, so the camera cannot also zoom");
		c.redraw();
		List<Row> r = c.fontPlain11.rows;
		check(r.size() == GI_ROWS_SHOWN && r.get(0).text.equals("Thing 1"),
			"...and the window moved down one: " + (r.isEmpty() ? "-" : r.get(0).text));

		c.mouseScrollDelta = 40;
		c.handleGroundItemScroll();
		check(c.giScrollOffset == 10 - GI_ROWS_SHOWN,
			"scrolling past the end clamps at the last full window (" + c.giScrollOffset + ")");
		c.mouseScrollDelta = -40;
		c.handleGroundItemScroll();
		check(c.giScrollOffset == 0, "and past the top clamps at zero (" + c.giScrollOffset + ")");

		// off the pile
		c.mouseScrollDelta = 1;
		c.mouseX = c.giPileRight[0] + 40 + QOL_PANEL_ORIGIN;
		check(!c.handleGroundItemScroll() && c.mouseScrollDelta == 1,
			"a turn away from any pile is left alone, so it still zooms");
		c.mouseX = mx;
		c.mouseY = c.giPileTop[0] - 20 + QOL_PANEL_ORIGIN;
		check(!c.handleGroundItemScroll() && c.mouseScrollDelta == 1,
			"...and so is one above the column");

		// a short pile has nothing to scroll and must not eat the wheel
		GroundItemsTest s = new GroundItemsTest().fresh();
		s.pile(64, 64, 3);
		s.redraw();
		s.mouseX = (s.giPileLeft[0] + s.giPileRight[0]) / 2 + QOL_PANEL_ORIGIN;
		s.mouseY = (s.giPileTop[0] + s.giPileBottom[0]) / 2 + QOL_PANEL_ORIGIN;
		s.mouseScrollDelta = 1;
		check(!s.handleGroundItemScroll() && s.mouseScrollDelta == 1,
			"a pile that fits needs no scrolling and does not consume the wheel");

		// a second pile starts at the top
		GroundItemsTest two = new GroundItemsTest().fresh();
		two.pile(64, 64, 10);
		two.pile(66, 64, 10);
		two.redraw();
		int a = -1;
		int b = -1;
		for (int i = 0; i < two.giPileCount; i++) {
			if (two.giPileTileX[i] == 64) {
				a = i;
			} else if (two.giPileTileX[i] == 66) {
				b = i;
			}
		}
		check(a >= 0 && b >= 0, "two piles on screen are two records");
		two.mouseX = (two.giPileLeft[a] + two.giPileRight[a]) / 2 + QOL_PANEL_ORIGIN;
		two.mouseY = (two.giPileTop[a] + two.giPileBottom[a]) / 2 + QOL_PANEL_ORIGIN;
		two.mouseScrollDelta = 2;
		two.handleGroundItemScroll();
		check(two.giScrollOffset == 2 && two.giScrollTileX == 64, "scrolling the first one moves it");
		two.mouseX = (two.giPileLeft[b] + two.giPileRight[b]) / 2 + QOL_PANEL_ORIGIN;
		two.mouseY = (two.giPileTop[b] + two.giPileBottom[b]) / 2 + QOL_PANEL_ORIGIN;
		two.mouseScrollDelta = 1;
		two.handleGroundItemScroll();
		check(two.giScrollTileX == 66 && two.giScrollOffset == 1,
			"...and moving to the other one starts that one from the top rather than inheriting");

		// the pile shrinking under a scrolled window
		GroundItemsTest sh = new GroundItemsTest().fresh();
		sh.pile(64, 64, 10);
		sh.redraw();
		sh.mouseX = (sh.giPileLeft[0] + sh.giPileRight[0]) / 2 + QOL_PANEL_ORIGIN;
		sh.mouseY = (sh.giPileTop[0] + sh.giPileBottom[0]) / 2 + QOL_PANEL_ORIGIN;
		sh.mouseScrollDelta = 2;
		sh.handleGroundItemScroll();
		sh.pile(64, 64, 4);
		sh.redraw();
		check(sh.fontPlain11.rows.size() == 4
			&& sh.fontPlain11.rows.get(0).text.equals("Thing 0"),
			"somebody taking the bottom of a scrolled pile does not leave it scrolled past its end");
	}

	// ---------------------------------------------------------------- 4
	static void barTests() {
		primeTypes(new String[0], new int[0]);
		GroundItemPrefs.clear();
		GroundItemsTest c = new GroundItemsTest().fresh();
		c.pile(64, 64, 3);
		c.redraw();
		check(painted() == 0, "a pile that fits draws no scroll bar - the font is the only painter");

		c = new GroundItemsTest().fresh();
		c.pile(64, 64, 10);
		c.redraw();
		check(painted() > 0, "a pile with more rows than it shows draws one");
		int barLeft = paintedLeft();
		int rowLeft = 520;
		for (Row r : c.fontPlain11.rows) {
			int left = r.x - c.fontPlain11.stringWid(r.text) / 2;
			if (left < rowLeft) {
				rowLeft = left;
			}
		}
		check(barLeft >= 0 && barLeft < rowLeft,
			"...to the left of the widest row (bar " + barLeft + ", rows " + rowLeft + ")");
		check(c.giPileLeft[0] <= barLeft,
			"...and inside the pile's own hover box, so the bar itself scrolls");
		int top = thumbTop();
		c.mouseX = (c.giPileLeft[0] + c.giPileRight[0]) / 2 + QOL_PANEL_ORIGIN;
		c.mouseY = (c.giPileTop[0] + c.giPileBottom[0]) / 2 + QOL_PANEL_ORIGIN;
		c.mouseScrollDelta = 2;
		c.handleGroundItemScroll();
		c.redraw();
		check(thumbTop() > top,
			"and the thumb moves down the track when the window does (" + top + " -> " + thumbTop() + ")");
	}

	// ---------------------------------------------------------------- 5
	static void altTests() {
		primeTypes(new String[0], new int[0]);
		GroundItemPrefs.clear();
		GroundItemsTest c = new GroundItemsTest().fresh();
		c.pile(64, 64, 10);
		c.redraw();
		check(c.giZoneCount == 0, "no Alt, no click targets at all");

		c = new GroundItemsTest().fresh();
		c.actionKey[GameShell.KEY_ALT] = 1;
		c.pile(64, 64, 10, 4);
		List<Row> ar = c.redraw();
		check(c.giZoneCount == GI_ROWS_SHOWN,
			"Alt gives one click target per DRAWN row and none for the rows scrolled out of sight: "
				+ c.giZoneCount);
		// Under Alt a row is three recorded draws - "-", "+", then the label - so the label is
		// looked for rather than indexed.
		boolean labelled = false;
		for (Row row : ar) {
			if (row.text.equals("Thing 0 x 4")) {
				labelled = true;
			}
		}
		check(labelled, "a stacked row is labelled with its count");
		check(c.giZoneName[0].equals("Thing 0") && c.giZoneNameEndX[0] > c.giZoneNameX[0],
			"...but the click target carries the BARE name, which is what the rules are keyed on: "
				+ c.giZoneName[0]);
		c.mouseClickX = c.giZoneMinusX[0] + 1 + QOL_PANEL_ORIGIN;
		c.mouseClickY = c.giZoneBottom[0] - 1 + QOL_PANEL_ORIGIN;
		check(c.handleGroundItemClick() && GroundItemPrefs.isHidden("Thing 0"),
			"and the minus still hides what is under it");
		GroundItemPrefs.clear();
	}
}
