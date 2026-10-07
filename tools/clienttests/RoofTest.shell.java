// Shell for tools/clienttests/run_rooftest.py. getTopLevel() and the panel's geometry are spliced
// in from jagex2/client/Client.java at run time - everything below them is the harness.
import jagex2.client.QolSettings;
import jagex2.client.Layout;
import jagex2.client.MenuSwaps;
import jagex2.dash3d.ClientPlayer;

public class RoofTest {

	// @@DECLS@@

	// @@METHODS@@

	// ------------------------------------------------------------------ the collaborators
	static ClientPlayer localPlayer;
	static int cyclelogic1;
	OutStub out = new OutStub();

	/**
	 * The anticheat packet getTopLevel() writes on its own schedule. A recording stub rather than a
	 * real Packet, because what the test wants to know is that the bytes are still being written at
	 * all - the toggle returning early before this block would silence a packet the server counts on.
	 */
	static final class OutStub {
		int pos;
		int writes;

		void p1isaac(int v) {
			writes++;
			pos++;
		}

		void p1(int v) {
			writes++;
			pos++;
		}

		void p2(int v) {
			writes++;
			pos += 2;
		}

		void psize1(int v) {
			writes++;
		}
	}

	// ------------------------------------------------------------------ the fixture
	static final int ROOF = 0x4;

	RoofTest fresh() {
		current = this;
		this.pluginRoofsHidden = roofsHidden;
		this.levelTileFlags = new byte[4][104][104];
		this.currentLevel = 0;
		this.cameraPitch = 200;               // not looking down: the tile tests run
		// Four tiles back from the player, which is roughly where the orbit camera sits. Not on the
		// player's own tile: see sameTileTests().
		this.cameraX = 60 * 128;
		this.cameraZ = 60 * 128;
		localPlayer = new ClientPlayer();
		localPlayer.field1157 = 64 * 128;
		localPlayer.field1158 = 64 * 128;
		cyclelogic1 = 0;
		return this;
	}

	/** Mark the player's own tile as having something above it. */
	void roofOverPlayer() {
		this.levelTileFlags[this.currentLevel][64][64] |= ROOF;
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

	// Hiding roofs is a plugin now (jagex2.client.plugin.builtin.HideRoofsPlugin), and all the
	// plugin does is set pluginRoofsHidden - so these tests drive that flag, which is what
	// getTopLevel actually reads.
	//
	// The setting it replaced was static, so the tests below set it both BEFORE building a
	// client and AFTER - "c = fresh(); roofsOff(true); c.getTopLevel()" is as common here as the
	// other order. A per-instance flag has to serve both, so this remembers the choice for the
	// next fixture and applies it to the current one. Keeping both orders working is what makes
	// this a port of the test rather than a rewrite of it.
	static boolean roofsHidden;
	static RoofTest current;

	static void roofsOff(boolean want) {
		roofsHidden = want;
		if (current != null) {
			current.pluginRoofsHidden = want;
		}
	}

	public static void main(String[] args) {
		System.out.println("1. what getTopLevel answers");
		levelTests();
		System.out.println("2. the camera on the player's own tile");
		sameTileTests();
		System.out.println("3. the panel the switch lives in");
		panelTests();
		System.out.println("3b. the left-click swaps panel, which scrolls instead of fitting");
		swapPanelTests();
		System.out.println();
		System.out.println(fail == 0 ? (pass + " CHECKS, ALL PASS")
			: (fail + " FAILED of " + (pass + fail)));
		System.exit(fail == 0 ? 0 : 1);
	}

	// ---------------------------------------------------------------- 1
	static void levelTests() {
		roofsOff(false);
		RoofTest c = new RoofTest().fresh();
		check(c.getTopLevel() == 3,
			"roofs on, nothing overhead: draw every level, which is what 3 means ("
				+ c.getTopLevel() + ")");
		c = new RoofTest().fresh();
		c.roofOverPlayer();
		check(c.getTopLevel() == 0,
			"roofs on, standing under one: draw only the player's own level, as it always did");
		c = new RoofTest().fresh();
		c.currentLevel = 2;
		localPlayer.field1157 = 64 * 128;
		c.levelTileFlags[2][64][64] |= ROOF;
		check(c.getTopLevel() == 2, "...and on an upper floor that is that floor, not zero");

		roofsOff(true);
		c = new RoofTest().fresh();
		check(c.getTopLevel() == 0,
			"roofs off, nothing overhead: still only the player's level - which is the whole "
				+ "feature, because everything above it IS the roof");
		c = new RoofTest().fresh();
		c.roofOverPlayer();
		check(c.getTopLevel() == 0, "roofs off, under a roof: unchanged");
		c = new RoofTest().fresh();
		c.currentLevel = 2;
		check(c.getTopLevel() == 2, "roofs off on an upper floor draws up to that floor");

		// The pitch test is the case an early return inside it would have broken.
		c = new RoofTest().fresh();
		c.cameraPitch = 320;
		roofsOff(false);
		check(c.getTopLevel() == 3, "camera tilted steeply down, roofs on: everything drawn");
		roofsOff(true);
		check(c.getTopLevel() == 0,
			"...and roofs off still hides them, even though the tile tests are skipped at that "
				+ "pitch - the bug an early exit would have shipped");
		// At this pitch the camera-to-player walk never runs, so the final test on the player's own
		// tile is the ONLY thing that can notice a roof. At any other pitch the walk ends on that
		// tile and tests it too, which is why this case needs its own fixture.
		c = new RoofTest().fresh();
		c.cameraPitch = 320;
		c.roofOverPlayer();
		roofsOff(false);
		check(c.getTopLevel() == 0,
			"roofs on, tilted down, standing under one: the player's own tile is tested even when "
				+ "the camera walk is not");

		// ...and the anticheat block keeps running whichever way the setting is set.
		c = new RoofTest().fresh();
		cyclelogic1 = 0;
		roofsOff(true);
		for (int i = 0; i < 5; i++) {
			c.getTopLevel();
		}
		check(cyclelogic1 == 5,
			"the anticheat cycle counter still advances once per call with roofs off ("
				+ cyclelogic1 + ")");
		c = new RoofTest().fresh();
		cyclelogic1 = 1457;
		c.out.writes = 0;
		c.getTopLevel();
		check(c.out.writes > 0 && cyclelogic1 == 0,
			"...and its packet is still written when the counter comes round (" + c.out.writes
				+ " writes)");
		c = new RoofTest().fresh();
		c.cameraPitch = 320;
		cyclelogic1 = 0;
		c.getTopLevel();
		check(cyclelogic1 == 0,
			"...and is still gated on the camera pitch, as it was before the toggle existed");
		roofsOff(false);
	}

	// ---------------------------------------------------------------- 2
	//
	// The simplest fixture to write puts the camera and the player on one tile - and that threw
	// ArithmeticException out of the vanilla getTopLevel(), because with both deltas zero it takes
	// the else branch and divides by the zero it just failed to be greater than. Never fired in a
	// real game, where the orbit camera keeps its distance; reachable since the wheel zoom, which
	// can pull that distance to 200 units, under one tile at a steep pitch.
	static void sameTileTests() {
		for (int level = 0; level < 3; level++) {
			for (int roof = 0; roof < 2; roof++) {
				for (int off = 0; off < 2; off++) {
					roofsOff(off == 1);
					RoofTest c = new RoofTest().fresh();
					c.currentLevel = level;
					c.cameraX = 64 * 128;
					c.cameraZ = 64 * 128;
					if (roof == 1) {
						c.roofOverPlayer();
					}
					// CAUGHT, not allowed to kill the process: an exception here is the bug this
					// case is about, and a dead JVM prints no check name at all - which the
					// mutation harness would report as "a crash is not a catch".
					int got = -1;
					String threw = null;
					try {
						got = c.getTopLevel();
					} catch (RuntimeException e) {
						threw = e.getClass().getSimpleName();
					}
					int want = (off == 1 || roof == 1) ? level : 3;
					check(threw == null && got == want,
						"camera on the player's tile, level " + level + ", roof " + (roof == 1)
							+ ", hide " + (off == 1) + ": "
							+ (threw != null ? ("threw " + threw) : String.valueOf(got)));
				}
			}
		}
		roofsOff(false);
	}

	// ---------------------------------------------------------------- 3
	static void panelTests() {
		RoofTest c = new RoofTest().fresh();
		check(QolSettings.COUNT == 16, "sixteen settings now (" + QolSettings.COUNT + ")");
		check(QOL_PANEL_ROWS == QolSettings.COUNT + 2,
			"...and two more rows under them, the window and the draw distance, which are not QolSettings switches");
		// Hide roofs is no longer one of them: it is a plugin, listed in the plugin panel.
		boolean roofsGone = true;
		for (int i = 0; i < QolSettings.COUNT; i++) {
			if (QolSettings.label(i).equals("Hide roofs")) {
				roofsGone = false;
			}
		}
		check(roofsGone, "...and Hide roofs is not among them any more - it is a plugin");
		// The panel has no paging. It has never needed it, and the only thing stopping it is that
		// nobody has added enough settings - which is worth failing on rather than discovering.
		check(c.qolPanelHeight() <= 334,
			"the panel still fits the 334px viewport with no paging: " + c.qolPanelHeight() + "px");
		check(c.qolPanelY() >= 0, "...and is centred without hanging off the top");
		int room = (334 - QOL_PANEL_HEADER_H - QOL_PANEL_FOOTER_H) / QOL_PANEL_ROW_H;
		check(room >= QOL_PANEL_ROWS,
			"...with room for " + (room - QOL_PANEL_ROWS) + " more before it needs paging");
		// Resizable: the panel is centred where a bank would be, in the part of the window no
		// side panel or chatbox covers.
		c.layout = Layout.resizable(1920, 1080);
		int top = c.qolPanelY();
		int open = c.layout.openH;
		check(top >= 0 && top + c.qolPanelHeight() <= open && Math.abs(top - (open - c.qolPanelHeight()) / 2) <= 1,
			"a 1920x1080 window centres it above the chatbox: y " + top + " in an open area " + open + " tall");
		c.layout = Layout.fixed();
		check(c.qolPanelY() == (334 - c.qolPanelHeight()) / 2, "...and the fixed screen where it always was");
		// the toggle round-trips through the real store
		// Still worth driving the real store once; XP drops stands in for the one that left.
		boolean was = QolSettings.on(QolSettings.XP_DROPS);
		QolSettings.toggle(QolSettings.XP_DROPS);
		check(QolSettings.on(QolSettings.XP_DROPS) != was, "the switch flips");
		QolSettings.toggle(QolSettings.XP_DROPS);
		check(QolSettings.on(QolSettings.XP_DROPS) == was, "...and flips back");
	}

	// ---------------------------------------------------------------- 3b
	/**
	 * The swaps panel. Its rule is not the settings panel's "it must fit" - it cannot, 128 swaps is
	 * 1920px of rows - it is "it must fit OR scroll, and either way it must be ON SCREEN". So every
	 * check below is run at every list length that matters, in all three display modes: the panel
	 * never hangs off the top (where the header and the way to close it are) or off the bottom
	 * (where the hint is), and whatever it cannot show is reachable with the bar.
	 *
	 * Driven through swapPanelRows(int) and friends rather than through MenuSwaps, deliberately:
	 * MenuSwaps.count() reads - and add() would WRITE - the player's real qol_swaps.dat.
	 */
	static final int[] SWAP_LENGTHS = { 0, 1, 16, 40, MenuSwaps.MAX };

	static void swapPanelTests() {
		RoofTest c = new RoofTest().fresh();
		check(MenuSwaps.MAX >= 128,
			"a cap a player will not reach: " + MenuSwaps.MAX + " swaps, where the panel's height used to allow 16");
		check(SWAP_PANEL_ACTIONS == 1 && SWAP_PANEL_SCROLL_W == 16,
			"one pinned action row, and the bar is the client's own 16px scrollbar sprite");

		int[][] modes = { { Layout.FIXED, 765, 503 }, { Layout.CLASSIC, 1280, 800 },
			{ Layout.CLASSIC, 1920, 1080 }, { Layout.MODERN, 1920, 1080 } };
		for (int m = 0; m < modes.length; m++) {
			c.layout = Layout.of(modes[m][0], modes[m][1], modes[m][2]);
			String where = Layout.modeName(modes[m][0]) + " " + modes[m][1] + "x" + modes[m][2];
			int open = c.layout.openH;
			boolean onScreen = true;
			boolean reachable = true;
			for (int i = 0; i < SWAP_LENGTHS.length; i++) {
				int n = SWAP_LENGTHS[i];
				int h = c.swapPanelHeight(n);
				int y = c.swapPanelY(n);
				if (y < 0 || y + h > open) {
					onScreen = false;
					System.out.println("       " + n + " swaps: y " + y + " h " + h + " open " + open);
				}
				// Every swap is either drawn or scrolled to: the rows on screen plus the rows the
				// bar can bring on screen have to be the whole list, with nothing falling off.
				int shown = c.swapPanelRows(n) - SWAP_PANEL_ACTIONS;
				c.swapScrollPx = Integer.MAX_VALUE;
				int last = c.swapFirstRow(n) + shown;
				if (n > 0 && (shown < 1 || last != Math.max(shown, n))) {
					reachable = false;
					System.out.println("       " + n + " swaps: shows " + shown + ", bottom of the list is " + last);
				}
			}
			check(onScreen, "the panel is on screen at 0, 1, 16, 40 and " + MenuSwaps.MAX
				+ " swaps - " + where + ", open area " + open + "px");
			check(reachable, "...and every swap is either shown or scrollable to");
		}

		// The tight case, spelled out: the fixed 765x503 client, which is the one that cannot grow.
		c.layout = Layout.fixed();
		int room = c.swapPanelRows(MenuSwaps.MAX) - SWAP_PANEL_ACTIONS;
		check(c.swapPanelHeight(MenuSwaps.MAX) <= Layout.VIEWPORT_H,
			"fixed: a full list is " + c.swapPanelHeight(MenuSwaps.MAX) + "px, inside the 334px viewport, showing "
				+ room + " swaps at a time");
		check(c.swapScrollMax(MenuSwaps.MAX) == (MenuSwaps.MAX - room) * SWAP_PANEL_ROW_H,
			"...with the other " + (MenuSwaps.MAX - room) + " behind the bar");
		check(c.swapScrollMax(room) == 0 && c.swapScrollMax(16) == 0,
			"...and no bar at all until the list outgrows the panel, so a player with 16 swaps sees what they always saw");
		check(c.swapPanelHeight(16) == SWAP_PANEL_HEADER_H + 17 * SWAP_PANEL_ROW_H + SWAP_PANEL_FOOTER_H
				&& c.swapPanelY(16) == (Layout.VIEWPORT_H - c.swapPanelHeight(16)) / 2,
			"...the same size and the same place the old 16-swap panel had: " + c.swapPanelHeight(16)
				+ "px at y " + c.swapPanelY(16));

		// A bigger window is used, not wasted.
		c.layout = Layout.resizable(Layout.CLASSIC, 1920, 1080);
		int wide = c.swapPanelRows(MenuSwaps.MAX) - SWAP_PANEL_ACTIONS;
		check(wide > room, "a 1920x1080 window shows " + wide + " swaps at once where the fixed screen shows " + room);
		check(Math.abs(c.swapPanelY(40) - (c.layout.openH - c.swapPanelHeight(40)) / 2) <= 1,
			"...and the panel is still centred in the part of the window no panel covers");

		// The scroll position is clamped as it is read, which is what lets cycle() shorten the list
		// under a panel that is scrolled to the bottom.
		c.layout = Layout.fixed();
		c.swapScrollPx = 999999;
		check(c.swapScroll(MenuSwaps.MAX) == c.swapScrollMax(MenuSwaps.MAX), "a scroll past the end stops at the end");
		check(c.swapScroll(0) == 0, "...and a list that has just been emptied under it goes back to the top");
		c.swapScrollPx = -500;
		check(c.swapScroll(40) == 0, "a scroll past the top stops at the top");
		c.swapScrollPx = 7 * SWAP_PANEL_ROW_H;
		check(c.swapFirstRow(40) == 7, "...and the first row drawn is the scroll position in whole rows");
	}
}
