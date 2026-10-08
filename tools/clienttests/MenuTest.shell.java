// Shell for tools/clienttests/run_menutest.py. The declarations and the six methods under test are
// spliced in from jagex2/client/Client.java at run time - everything below them is the harness.
import jagex2.client.QolSettings;
import jagex2.client.GameShell;
import jagex2.client.Layout;
import jagex2.graphics.Pix2D;
import jagex2.graphics.Pix8;

import java.util.ArrayList;
import java.util.List;

/** What the extracted methods reach for with super. */
class MenuShellBase {
	public int[] actionKey = new int[128];
	public int mouseX;
	public int mouseY;
	public int mouseClickX;
	public int mouseClickY;
	public int mouseScrollDelta;
}

/** One drawn row, as the font saw it. */
class MenuRow {
	final int x;
	final int y;
	final int colour;
	final String text;

	MenuRow(int x, int y, int colour, String text) {
		this.x = x;
		this.y = y;
		this.colour = colour;
		this.text = text;
	}

	public String toString() {
		return text + "@" + y;
	}
}

/** The one stub: PixFont needs a Jagfile, and recording the draws IS the measurement. */
class MenuFontStub {
	public int height = 12;
	final List<MenuRow> rows = new ArrayList<MenuRow>();
	final List<String> plain = new ArrayList<String>();

	public int stringWidTag(String s) {
		return s.length() * 6;
	}

	public void drawString(int x, int colour, int y, String s) {
		plain.add(s);
	}

	public void drawStringTag(int colour, int x, int y, boolean shadow, String s) {
		rows.add(new MenuRow(x, y, colour, s));
	}
}

/**
 * The rows go through ChatIcons now (a player's crown before the name). The real one takes a
 * PixFont; this one hands the text to the recording font, which is what the harness measures.
 */
class ChatIcons {
	static void draw(MenuFontStub font, Pix8[] icons, int x, int y, int colour, String text) {
		font.drawStringTag(colour, x, y, true, text.startsWith("@sh1@") ? text.substring(5) : text);
	}

	static int width(MenuFontStub font, String text) {
		return font.stringWidTag(text);
	}
}

public class MenuTest extends MenuShellBase {

	// @@DECLS@@

	// @@METHODS@@

	// ------------------------------------------------------------------ the collaborators
	MenuFontStub fontBold12 = new MenuFontStub();
	boolean redrawSidebar;
	boolean redrawChatback;

	/**
	 * Shift + right-click builds a menu of swaps instead of actions. A no-op here: this harness is
	 * about the menu's geometry, and every test leaves Shift up so the real one would not be called
	 * either.
	 */
	void buildSwapMenu() {
	}

	static int[] buf = new int[520 * 340];

	// ------------------------------------------------------------------ the fixture
	/** A menu of n rows: "Cancel" at index 0 and "Take n" upward, which is how the client fills it. */
	MenuTest menu(int n) {
		this.menuOption = new String[500];
		// Cleared with the options, because the client clears it with the options: the rebuild
		// that writes Cancel into index 0 wipes every override, since an index means nothing once
		// the list behind it has changed.
		this.menuColour = new int[500];
		this.menuOption[0] = "Cancel";
		for (int i = 1; i < n; i++) {
			this.menuOption[i] = "Take item " + i;
		}
		this.menuSize = n;
		return this;
	}

	/** Right-click at a point inside one of the three areas. */
	void openAt(int x, int y) {
		this.mouseClickX = x;
		this.mouseClickY = y;
		this.showContextMenu();
	}

	List<MenuRow> redraw() {
		this.fontBold12.rows.clear();
		java.util.Arrays.fill(buf, 0);
		Pix2D.bind(520, 340, buf);
		this.drawMenu();
		return this.fontBold12.rows;
	}

	/**
	 * Puts the cursor on the row at visual position p, in the band drawMenu tests against.
	 *
	 * menuArea 0 means the viewport, so the draw subtracts the viewport's origin from the mouse
	 * before comparing - which the point has to add back, or every row reads as un-hovered.
	 */
	void hover(int p) {
		this.mouseX = this.layout.vpX + this.menuX + 5;
		this.mouseY = this.layout.vpY + this.menuRowY(p) - 5;
	}

	/** The cursor nowhere near the menu. */
	void hoverNothing() {
		this.mouseX = -100;
		this.mouseY = -100;
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

	/**
	 * Pixels of one exact colour. Not "any pixel", which is what the ground-item harness can ask:
	 * there the font is the only painter, and here drawMenu() paints its own box and border first.
	 */
	static int painted(int colour) {
		int n = 0;
		for (int i = 0; i < buf.length; i++) {
			if (buf[i] == colour) {
				n++;
			}
		}
		return n;
	}

	static int bar() {
		return painted(MENU_BAR_TRACK) + painted(MENU_BAR_THUMB);
	}

	static int thumbTop() {
		for (int y = 0; y < 340; y++) {
			for (int x = 0; x < 520; x++) {
				if (buf[y * 520 + x] == MENU_BAR_THUMB) {
					return y;
				}
			}
		}
		return -1;
	}

	/** The x of the right-most bar pixel, or -1. */
	static int paintedRight() {
		for (int x = 519; x >= 0; x--) {
			for (int y = 0; y < 340; y++) {
				int px = buf[y * 520 + x];
				if (px == MENU_BAR_TRACK || px == MENU_BAR_THUMB) {
					return x;
				}
			}
		}
		return -1;
	}

	public static void main(String[] args) {
		System.out.println("1. a menu taller than its area is capped, not drawn off the edge");
		capTests();
		System.out.println("2. a short menu is exactly what it was");
		shortTests();
		System.out.println("3. the wheel moves the window");
		wheelTests();
		System.out.println("4. every row can be reached");
		reachTests();
		System.out.println("5. the scroll bar");
		barTests();
		System.out.println("7. resizable: a window-sized viewport under the panels");
		resizableTests();
		System.out.println("8. a plugin's per-row colour");
		overrideTests();
		System.out.println();
		System.out.println(fail == 0 ? (pass + " CHECKS, ALL PASS")
			: (fail + " FAILED of " + (pass + fail)));
		System.exit(fail == 0 ? 0 : 1);
	}

	// ---------------------------------------------------------------- 1
	static void capTests() {
		MenuTest c = new MenuTest().menu(30);
		c.openAt(200, 100);
		check(c.menuVisible && c.menuArea == 0, "a right-click in the viewport opens a menu there");
		check(c.menuRowsShown == 20,
			"thirty rows in the 334px viewport show 20 (" + c.menuRowsShown + ")");
		check(c.menuY >= 0 && c.menuY + c.menuHeight <= 334,
			"...and the whole menu is inside the area: y " + c.menuY + " + h " + c.menuHeight);
		List<MenuRow> r = c.redraw();
		check(r.size() == 20, "twenty rows are drawn, not thirty (" + r.size() + ")");
		check(!r.isEmpty() && r.get(0).text.equals(c.menuOption[29]),
			"...the top row is the last entry appended, which is the left-click action");
		boolean cancel = false;
		for (MenuRow row : r) {
			if (row.text.equals("Cancel")) {
				cancel = true;
			}
		}
		check(!cancel, "...and Cancel, which is the BOTTOM of the array, is off the end of the "
			+ "window - clicking away from the menu is what cancels");
		// The reset has to be tested on a client that has already scrolled something: menuScroll is
		// a field of the client, not of the menu, so "it happens to be zero on a fresh object"
		// proves nothing - and the first version of this check proved exactly that.
		c.mouseScrollDelta = 4;
		c.handleMenuScroll();
		check(c.menuScroll == 4, "scrolling a menu moves it");
		c.menu(30);
		c.openAt(200, 100);
		check(c.menuScroll == 0,
			"...and opening the NEXT menu starts it at the top rather than inheriting the last "
				+ "one's scroll (" + c.menuScroll + ")");

		// the other two areas cap at their own heights
		MenuTest s = new MenuTest().menu(30);
		s.openAt(600, 300);
		check(s.menuArea == 1 && s.menuRowsShown == 15,
			"the sidebar's 261px shows 15 (" + s.menuRowsShown + ")");
		check(s.menuY >= 0 && s.menuY + s.menuHeight <= 261,
			"...and fits: y " + s.menuY + " + h " + s.menuHeight);
		MenuTest b = new MenuTest().menu(30);
		b.openAt(200, 400);
		int chatRows = (CHAT_H - MENU_CHROME_H) / MENU_ROW_H;
		check(b.menuArea == 2 && b.menuRowsShown == chatRows,
			"the chatbox's " + CHAT_H + "px shows " + chatRows + " (" + b.menuRowsShown + ")");
		check(b.menuY >= 0 && b.menuY + b.menuHeight <= CHAT_H,
			"...and fits: y " + b.menuY + " + h " + b.menuHeight);
	}

	// ---------------------------------------------------------------- 8
	/**
	 * A plugin's per-row colour override, which is the whole client half of menu restyling.
	 *
	 * THREE PROPERTIES, and the third is the one worth the test. The override is drawn. It is
	 * drawn on the row it was set on, which is not the row it looks like: the array is upside down,
	 * so menuColour is indexed the way menuOption is and the TOP row is the HIGHEST index. And
	 * hovering still wins over it, because the hover colour is the only thing on screen that says
	 * which row a click is about to take - a coloured row that stopped responding to the cursor
	 * would have traded feedback for decoration.
	 */
	static void overrideTests() {
		int white = 16777215;
		int hovered = 16776960;
		int green = 0x40FF40;

		MenuTest c = new MenuTest().menu(4);
		c.openAt(200, 100);
		c.hoverNothing();
		List<MenuRow> plain = c.redraw();
		check(plain.size() == 4, "a four-entry menu fits, so all four rows draw - Cancel at the "
			+ "bottom, which is index 0 (" + plain.size() + ")");
		boolean allWhite = true;
		for (MenuRow row : plain) {
			allWhite = allWhite && row.colour == white;
		}
		check(allWhite, "with no override every row is the white the client always drew");

		// Index 3 is the TOP row of a four-entry menu - menuRowIndex(0) - which is also the
		// left-click. Colouring it and asserting the TOP row changed is what catches the array
		// being read the wrong way up.
		c.menuColour[3] = green;
		List<MenuRow> lit = c.redraw();
		check(lit.get(0).colour == green,
			"an override on the last index colours the TOP row, because the array is upside down");
		check(lit.get(0).text.equals(c.menuOption[3]), "...and that row is the one it was set on");
		check(lit.get(1).colour == white && lit.get(2).colour == white
				&& lit.get(3).colour == white,
			"...and no other row is touched, Cancel included");

		// The bottom-but-one, to pin the mapping at both ends rather than at one.
		c.menuColour[3] = 0;
		c.menuColour[1] = green;
		List<MenuRow> low = c.redraw();
		check(low.get(2).colour == green && low.get(0).colour == white,
			"an override on index 1 colours the BOTTOM row of the window, not the top");
		check(low.get(2).text.equals(c.menuOption[1]), "...the row it was set on");

		// Setting it back to 0 is how a plugin undoes one, and is also what the per-frame clear
		// does - a colour that could not be removed would stick for the session.
		c.menuColour[1] = 0;
		check(c.redraw().get(2).colour == white, "0 puts a row back to white");

		// HOVER WINS, over an override and over nothing.
		c.menuColour[3] = green;
		c.hover(0);
		List<MenuRow> over = c.redraw();
		check(over.get(0).colour == hovered,
			"hovering a coloured row still draws it in the hover colour, not its override");
		check(over.get(1).colour == white,
			"...and only the hovered row, so the override is not smeared");
		c.hover(1);
		List<MenuRow> other = c.redraw();
		check(other.get(1).colour == hovered, "hovering a plain row highlights it as it always did");
		check(other.get(0).colour == green,
			"...and the coloured row keeps its colour while the cursor is elsewhere");

		// A SCROLLED MENU. menuRowIndex subtracts menuScroll, so the draw and the override have to
		// agree about it or a colour slides by a row every notch of the wheel.
		MenuTest big = new MenuTest().menu(30);
		big.openAt(200, 100);
		big.hoverNothing();
		big.menuColour[29] = green;
		check(big.redraw().get(0).colour == green, "the top row of a long menu takes its override");
		big.mouseScrollDelta = 2;
		big.handleMenuScroll();
		check(big.menuScroll == 2, "the menu is scrolled down two rows");
		List<MenuRow> scrolled = big.redraw();
		check(scrolled.get(0).text.equals(big.menuOption[27]),
			"...so the top row is now entry 27");
		check(scrolled.get(0).colour == white,
			"...which has no override, and is not wearing entry 29's");
		boolean moved = false;
		for (int p = 0; p < scrolled.size(); p++) {
			moved = moved || scrolled.get(p).colour == green;
		}
		check(!moved, "...and entry 29's colour went off the top of the window with entry 29");
	}

	// ---------------------------------------------------------------- 2
	static void shortTests() {
		MenuTest c = new MenuTest().menu(5);
		c.openAt(200, 100);
		check(c.menuRowsShown == 5 && c.menuHeight == 5 * MENU_ROW_H + MENU_CHROME_H,
			"five rows show five, at the height the menu always was (" + c.menuHeight + ")");
		List<MenuRow> r = c.redraw();
		check(r.size() == 5, "all five drawn");
		check(r.size() == 5 && r.get(4).text.equals("Cancel"),
			"...with Cancel on the bottom, where it has always been");
		check(r.size() == 5 && r.get(1).y - r.get(0).y == MENU_ROW_H
			&& r.get(0).y == c.menuY + 31,
			"...one row height apart, the first at menuY + 31, exactly as before");
		check(bar() == 0, "and no scroll bar, because nothing is hidden (" + bar() + " bar pixels)");
		MenuTest one = new MenuTest().menu(1);
		one.openAt(200, 100);
		check(one.menuRowsShown == 1, "a one-row menu shows its one row");
	}

	// ---------------------------------------------------------------- 3
	static void wheelTests() {
		MenuTest c = new MenuTest().menu(30);
		c.openAt(200, 100);
		check(c.menuRowIndex(0) == 29 && c.menuRowIndex(19) == 10,
			"at the top, the window is indices 29 down to 10");
		c.mouseScrollDelta = 3;
		boolean took = c.handleMenuScroll();
		check(took && c.mouseScrollDelta == 0 && c.menuScroll == 3,
			"a wheel turn is consumed and moves the window three rows");
		check(c.menuRowIndex(0) == 26, "...so the top row is now index 26 (" + c.menuRowIndex(0) + ")");
		List<MenuRow> r = c.redraw();
		check(!r.isEmpty() && r.get(0).text.equals(c.menuOption[26]),
			"...and that is what gets drawn there");
		c.mouseScrollDelta = 99;
		c.handleMenuScroll();
		check(c.menuScroll == 30 - 20,
			"scrolling past the end stops with the last row in view (" + c.menuScroll + ")");
		c.mouseScrollDelta = -99;
		c.handleMenuScroll();
		check(c.menuScroll == 0, "and past the top stops at the top (" + c.menuScroll + ")");

		// consumed even with nothing to scroll: the world behind a menu must not move
		MenuTest s = new MenuTest().menu(5);
		s.openAt(200, 100);
		s.mouseScrollDelta = 1;
		check(s.handleMenuScroll() && s.mouseScrollDelta == 0 && s.menuScroll == 0,
			"a wheel turn with a short menu open is still consumed, so the camera stays put behind "
				+ "it, and the menu does not move");

		// no menu, no claim on the wheel
		MenuTest n = new MenuTest().menu(30);
		n.menuVisible = false;
		n.mouseScrollDelta = 1;
		check(!n.handleMenuScroll() && n.mouseScrollDelta == 1,
			"with no menu open the wheel is left for the pile and the camera");

		// the sidebar and chatbox get their redraw flags, or the scroll would not appear
		MenuTest sb = new MenuTest().menu(30);
		sb.openAt(600, 300);
		sb.mouseScrollDelta = 1;
		sb.handleMenuScroll();
		check(sb.redrawSidebar, "scrolling a sidebar menu marks the sidebar for redraw");
		MenuTest cb = new MenuTest().menu(30);
		cb.openAt(200, 400);
		cb.mouseScrollDelta = 1;
		cb.handleMenuScroll();
		check(cb.redrawChatback, "...and a chatbox menu the chatbox");
	}

	// ---------------------------------------------------------------- 4
	static void reachTests() {
		MenuTest c = new MenuTest().menu(30);
		c.openAt(200, 100);
		boolean[] seen = new boolean[30];
		for (int scroll = 0; scroll <= 30 - c.menuRowsShown; scroll++) {
			c.menuScroll = scroll;
			for (MenuRow row : c.redraw()) {
				for (int i = 0; i < 30; i++) {
					if (row.text.equals(c.menuOption[i])) {
						seen[i] = true;
					}
				}
			}
		}
		int missed = 0;
		for (int i = 0; i < 30; i++) {
			if (!seen[i]) {
				missed++;
			}
		}
		check(missed == 0, "scrolling from end to end reaches every one of the 30 entries, which is "
			+ "the whole point: " + missed + " unreachable");
		c.menuScroll = 30 - c.menuRowsShown;
		List<MenuRow> last = c.redraw();
		check(!last.isEmpty() && last.get(last.size() - 1).text.equals("Cancel"),
			"...and the last window ends on Cancel");
		check(c.menuRowIndex(c.menuRowsShown - 1) == 0,
			"...which is index 0, so nothing is off the bottom of the array");
	}

	// ---------------------------------------------------------------- 7
	/** A right-click at WINDOW point (x, y), through the same mapping the client's mouse goes through. */
	void openAtWindow(int x, int y) {
		this.openAt(this.layout.mapX(x, y, Layout.ANY), this.layout.mapY(x, y, Layout.ANY));
	}

	static void resizableTests() {
		MenuTest c = new MenuTest().menu(30);
		c.layout = Layout.resizable(1280, 800);
		c.openAtWindow(700, 300);
		check(c.menuVisible && c.menuArea == 0, "a right-click on the scene of a 1280x800 window opens a viewport menu");
		check(c.menuRowsShown == 30, "...and the window's 800px shows all 30 rows (" + c.menuRowsShown + ")");
		check(c.menuX == 700 - c.menuWidth / 2 && c.menuY == 300,
			"...centred on the click in window pixels: " + c.menuX + "," + c.menuY);
		c.menu(30);
		c.menuVisible = false;
		c.openAtWindow(1100, 600);
		check(c.menuArea == 1, "a right-click on the inventory in the bottom-right panel is a sidebar menu");
		check(c.menuY >= 0 && c.menuY + c.menuHeight <= 261 && c.menuRowsShown == 15,
			"...capped to the sidebar as it always was: y " + c.menuY + " + h " + c.menuHeight);
		c.menu(30);
		// just left of the side panel (x 1031..1280) and just above the chatbox (y 635..800)
		c.openAtWindow(1025, 630);
		check(c.menuArea == 0, "the scene right beside both panels is still the viewport");
		check(c.menuX + c.menuWidth <= 1280 && c.menuY + c.menuHeight <= 800 && c.menuX >= 0 && c.menuY >= 0,
			"...and its menu stays on the window, over the panels if it must: " + c.menuX + "," + c.menuY
				+ " " + c.menuWidth + "x" + c.menuHeight);
		c.menu(30);
		c.openAtWindow(100, 700);
		check(c.menuArea == 2, "a right-click on the chatbox in the bottom-left is a chatbox menu");
		// The hover highlight: a viewport menu is hit-tested in window pixels, however far from the
		// fixed 512x334 it is.
		c.menu(5);
		c.openAtWindow(900, 500);
		c.mouseX = c.layout.mapX(c.menuX + 10, c.menuY + 31, Layout.VIEWPORT);
		c.mouseY = c.layout.mapY(c.menuX + 10, c.menuY + 31, Layout.VIEWPORT);
		List<MenuRow> rows = c.redrawWindow();
		check(!rows.isEmpty() && rows.get(0).colour == 16776960,
			"...the row under the mouse lights up (top row colour " + (rows.isEmpty() ? -1 : rows.get(0).colour) + ")");
	}

	List<MenuRow> redrawWindow() {
		this.fontBold12.rows.clear();
		int[] big = new int[1280 * 800];
		Pix2D.bind(1280, 800, big);
		this.drawMenu();
		Pix2D.bind(520, 340, buf);
		return this.fontBold12.rows;
	}

	// ---------------------------------------------------------------- 5
	static void barTests() {
		MenuTest c = new MenuTest().menu(30);
		c.openAt(200, 100);
		c.redraw();
		check(bar() > 0, "a capped menu draws a scroll bar (" + bar() + " bar pixels)");
		check(paintedRight() < c.menuX + c.menuWidth,
			"...inside the menu's own width (bar right " + paintedRight() + ", menu right "
				+ (c.menuX + c.menuWidth) + ")");
		int top = thumbTop();
		c.mouseScrollDelta = 5;
		c.handleMenuScroll();
		c.redraw();
		check(thumbTop() > top,
			"and the thumb moves down as the window does (" + top + " -> " + thumbTop() + ")");
		c.menuScroll = 30 - c.menuRowsShown;
		c.redraw();
		int end = thumbTop();
		check(end + 3 <= c.menuY + 19 + c.menuRowsShown * MENU_ROW_H,
			"...and never past the end of its track (" + end + ")");
	}
}
