/*
 * Headless test for API level 10: what the server has told the client.
 *
 * WHAT THIS GUARDS, and none of it is the kind of bug that throws. Both halves of this level
 * decode a number out of a packed one, and both have an off-by-one that produces another
 * perfectly ordinary number:
 *
 *   * a varbit mask one bit short reads a four-bit patch stage as its lower three bits - still a
 *     stage, still in range, and a farm timer built on it is confidently early;
 *   * an inventory slot holds its obj id PLUS ONE so that zero can mean empty, and forgetting
 *     the plus one hands back the obj one id below the real one, which in this cache is an
 *     ordinary item with an ordinary name.
 *
 * Nothing errors in either case. There is no screen to look at, because a plugin reading these
 * draws whatever it was told. So the two decodes are pure functions and are tested as such -
 * every bit range there is, in a sweep, rather than the three or four somebody thought of.
 *
 * THE THIRD THING IS THE CLASS CONTRACT: every getter on PluginContext is safe to call with no
 * world. That is not decoration here. The client NULLS its varps on logout and throws the
 * interface cache away, while an overlay goes on being drawn, so the no-world path is the one
 * that actually runs - and it runs at the moment nobody is watching the log. That section needs
 * a Client, which needs a display (it extends Applet), so it says it SKIPPED rather than passing
 * when there is none.
 */
package jagex2.client.plugin;

import jagex2.client.Client;
import jagex2.config.Component;
import jagex2.config.LocType;
import jagex2.config.VarbitType;

public class GameStateTest {

	static int fails;

	static void check(boolean ok, String what) {
		System.out.println((ok ? "  ok   " : "FAIL   ") + what);
		if (!ok) {
			fails++;
		}
	}

	static void skip(String what) {
		System.out.println("  SKIP  " + what);
	}

	public static void main(String[] args) {
		System.out.println("1. a varbit is the bits it covers, and only those");
		varbitTests();
		System.out.println();
		System.out.println("2. an inventory slot holds its obj id plus one");
		slotTests();
		System.out.println();
		System.out.println("3. nothing here throws, or lies, without a world");
		noWorldTests();
		System.out.println();
		System.out.println("4. a varbit read out of the varp it actually lives in");
		liveVarpTests();
		System.out.println();
		System.out.println("5. the inventory, read through the component the server fills");
		inventoryTests();
		System.out.println();
		System.out.println("6. what a loc looks like right now");
		locTests();
		System.out.println();
		System.out.println("7. the level, and the line it is drawn on");
		levelTests();

		System.out.println();
		if (fails == 0) {
			System.out.println("all checks passed");
		} else {
			System.out.println(fails + " CHECK(S) FAILED");
		}
		System.exit(fails == 0 ? 0 : 1);
	}

	// ------------------------------------------------------------------ 1. the varbit decode

	static void varbitTests() {
		check(PluginContext.decodeVarbit(0x5, 0, 2) == 5, "three bits at the bottom read as they are");
		check(PluginContext.decodeVarbit(0x8, 3, 3) == 1, "a single bit set reads 1");
		check(PluginContext.decodeVarbit(0x7, 3, 3) == 0, "...and its neighbours below do not reach it");
		check(PluginContext.decodeVarbit(0xF0, 4, 7) == 15, "a field higher up is shifted down to zero");

		// THE SWEEP. Every range a varbit can have, twice: the field alone set to its largest
		// value, and the field clear with every bit around it set. The first catches a mask one
		// bit short, the second catches one bit too long - and between them they catch a shift
		// in the wrong direction and an inclusive/exclusive slip at either end. 528 ranges,
		// because the four anybody writes out by hand are never the four that are wrong.
		int narrow = 0;
		int wide = 0;
		for (int lsb = 0; lsb <= 31; lsb++) {
			for (int msb = lsb; msb <= 31; msb++) {
				int width = msb - lsb + 1;
				int max = (int) ((1L << width) - 1L);
				if (PluginContext.decodeVarbit(max << lsb, lsb, msb) != max) {
					narrow++;
				}
				if (PluginContext.decodeVarbit(~(max << lsb), lsb, msb) != 0) {
					wide++;
				}
			}
		}
		check(narrow == 0, "every one of the 528 ranges reads its own field back whole");
		check(wide == 0, "...and reads 0 when every bit AROUND the field is set and the field is not");

		// The whole varp as one varbit. The mask for this is -1, which is what the client's table
		// holds and what a hand-written (1 << 32) - 1 would get wrong - that expression is 1 in
		// Java, so this would read the bottom bit alone.
		check(PluginContext.decodeVarbit(-1, 0, 31) == -1, "a varbit spanning the whole varp reads the whole varp");
		check(PluginContext.decodeVarbit(0x5EED, 0, 31) == 0x5EED, "...whatever is in it");

		// A negative varp read through an arithmetic shift. The sign bits it drags down have to
		// land above the mask, or a quest step in the top half of a varp reads enormous.
		check(PluginContext.decodeVarbit(0x80000000, 28, 30) == 0, "a negative varp does not leak its sign into a field below");
		check(PluginContext.decodeVarbit(0x80000000, 31, 31) == 1, "...and the top bit itself still reads 1");
		check(PluginContext.decodeVarbit(-1, 8, 11) == 15, "...and an all-ones varp reads a four-bit field as 15, not -1");

		// Malformed ranges. 0 rather than an exception, because this runs inside a plugin's
		// overlay and the client's answer to a plugin that throws is to switch it off.
		check(PluginContext.decodeVarbit(-1, 5, 4) == 0, "a range that ends before it starts reads 0");
		check(PluginContext.decodeVarbit(-1, -1, 4) == 0, "...as does one starting below bit 0");
		check(PluginContext.decodeVarbit(-1, 0, 32) == 0, "...and one running past bit 31");
		check(PluginContext.decodeVarbit(-1, 10, 35) == 0,
			"...including one whose WIDTH is legal and whose top bit is not");
	}

	// ------------------------------------------------------------------ 2. the slot decode

	static void slotTests() {
		check(PluginContext.decodeSlotObj(0) == -1, "an empty slot is stored as 0 and reads as -1");
		check(PluginContext.decodeSlotObj(-3) == -1, "...and so is anything below it");
		check(PluginContext.decodeSlotObj(1) == 0,
			"a slot holding obj 0 is stored as 1 - which is why empty cannot be 0");
		check(PluginContext.decodeSlotObj(996) == 995, "and every other slot is one less than it is stored as");
		check(PluginContext.decodeSlotObj(Integer.MAX_VALUE) == Integer.MAX_VALUE - 1, "...including the largest");
	}

	// ------------------------------------------------------------------ 3 and 4. the live context

	/** A client, or null when there is no display to make one on. */
	static Client newClient() {
		try {
			return new Client();
		} catch (Throwable noDisplay) {
			return null;
		}
	}

	static void noWorldTests() {
		Client client = newClient();
		if (client == null) {
			skip("needs a display to build a Client - run under xvfb-run");
			return;
		}
		// What the client looks like after a logout: varps thrown away, interface cache gone.
		client.varps = null;
		Component.types = null;
		Component.data = null;
		PluginContext ctx = new PluginContext(client);

		String threw = "";
		try {
			check(ctx.getVarpCount() == 0, "with no varps, the count is 0");
			check(ctx.getVarp(0) == 0, "...and any varp reads 0");
			check(ctx.getVarp(-1) == 0, "...including one below the start");
			check(ctx.getVarp(Integer.MAX_VALUE) == 0, "...and one past the end");
			check(ctx.getVarbit(0) == 0, "...and any varbit reads 0");
			check(ctx.getInventorySize() == 0, "with no interface cache, the inventory has no slots");
			check(ctx.getInventoryObjId(0) == -1, "...and every slot reads empty");
			check(ctx.getInventoryObjCount(0) == 0, "...and holds nothing");
			check(ctx.countObj(995) == 0, "...so the player is carrying no coins");
			check(!ctx.hasObj(995), "...and has none");
			check("".equals(ctx.getObjName(995)), "...and an obj with no cache behind it has no name");
		} catch (Throwable error) {
			threw = error.getClass().getName();
		}
		check(threw.length() == 0, "and not one of them threw" + (threw.length() == 0 ? "" : " (" + threw + ")"));
	}

	/** Not any varp value a check here expects, so a throw fails the check that caused it. */
	static final int THREW = Integer.MIN_VALUE;

	/**
	 * getVarp, with anything it throws turned into a failure that has a NAME.
	 *
	 * The answer for an index out of range is 0. An exception is not 0, and letting one out of
	 * here ends the whole run with no failure printed - which the mutation runner counts as a
	 * crash rather than as a catch, because a suite that only goes red by dying is not saying
	 * which promise broke. Returning a value no check expects makes it an ordinary failure,
	 * naming the index that caused it.
	 */
	static int varp(PluginContext ctx, int id) {
		try {
			return ctx.getVarp(id);
		} catch (RuntimeException threw) {
			return THREW;
		}
	}

	static void liveVarpTests() {
		Client client = newClient();
		if (client == null) {
			skip("needs a display to build a Client - run under xvfb-run");
			return;
		}
		client.varps = new int[2000];
		client.varps[40] = 0xA0;   // 1010 sitting in bits 4 to 7
		client.varps[3] = 0x70;    // and something else entirely in the varp numbered like the varbit

		// A varbit numbered 3 that lives in varp 40 - deliberately different numbers, because a
		// varbit read out of the varp it is NUMBERED after rather than the one it points at is a
		// one-word slip that works perfectly for every varbit whose two numbers happen to agree.
		VarbitType[] bits = new VarbitType[8];
		VarbitType bit = new VarbitType();
		bit.field1762 = 40;
		bit.field1763 = 4;
		bit.field1764 = 7;
		bits[3] = bit;
		VarbitType.field1760 = bits;

		PluginContext ctx = new PluginContext(client);
		check(ctx.getVarpCount() == 2000, "the client's varps are all there");
		check(varp(ctx, 40) == 0xA0, "a varp reads what the server put in it");
		check(varp(ctx, 2000) == 0, "one past the end reads 0 rather than throwing");
		check(varp(ctx, Integer.MAX_VALUE) == 0, "...and so does one a long way past it");
		check(varp(ctx, -1) == 0, "...and one before the start");

		check(ctx.getVarbitCount() == 8, "the cache's varbits are all there");
		check(ctx.getVarbit(3) == 10, "a varbit reads the field inside the varp it points at");
		check(ctx.hasVarbit(3), "...and says it is defined");
		check(!ctx.hasVarbit(4), "a varbit the cache leaves undefined says so");
		check(ctx.getVarbit(4) == 0, "...and reads 0");
		check(!ctx.hasVarbit(99) && ctx.getVarbit(99) == 0, "...as does one past the end of the table");
		check(!ctx.hasVarbit(-1) && ctx.getVarbit(-1) == 0, "...and one before the start");

		VarbitType.field1760 = null;
	}

	/**
	 * The component the inventory is transmitted to, pinned here as well as in PluginContext.
	 *
	 * DELIBERATELY A SECOND COPY. The first version of this read the number out of PluginContext's
	 * source and built the fixture at whatever it found, which meant a wrong constant moved the
	 * test along with it and the check passed on an inventory nobody could read. A test that
	 * follows the code cannot disagree with it. This one is written down from the content's
	 * interface.pack (`3214=inventory:inv`) and the client's copy is checked against it.
	 */
	static final int INVENTORY_COM = 3214;

	/** The component id PluginContext reads the inventory from, read out of its source. */
	static int inventoryCom() {
		String src = read("src/main/java/jagex2/client/plugin/PluginContext.java");
		int at = src.indexOf("INVENTORY_COM = ");
		if (at < 0) {
			return -1;
		}
		int end = src.indexOf(';', at);
		try {
			return Integer.parseInt(src.substring(at + 16, end).trim());
		} catch (RuntimeException notANumber) {
			return -1;
		}
	}

	static void inventoryTests() {
		Client client = newClient();
		if (client == null) {
			skip("needs a display to build a Client - run under xvfb-run");
			return;
		}
		check(inventoryCom() == INVENTORY_COM,
			"PluginContext reads the inventory from component " + INVENTORY_COM
				+ " (it says " + inventoryCom() + ")");
		int com = INVENTORY_COM;

		// A cache with one component in it: the one the server fills with the inventory. Built
		// here rather than loaded, so the test says what is in each slot.
		Component.types = new Component[com + 1];
		Component.data = new byte[com + 1][];
		Component inv = new Component();
		inv.type = 2;
		inv.width = 4;
		inv.height = 7;
		inv.invSlotObjId = new int[28];
		inv.invSlotObjCount = new int[28];
		Component.types[com] = inv;

		// Stored as id + 1. Coins twice, to be summed; a gap in the middle, to stay a gap.
		inv.invSlotObjId[0] = 996;      // 995, coins
		inv.invSlotObjCount[0] = 1000;
		inv.invSlotObjId[1] = 1206;     // 1205, a dagger
		inv.invSlotObjCount[1] = 1;
		inv.invSlotObjId[2] = 0;        // empty, deliberately between two that are not
		inv.invSlotObjCount[2] = 5;     // ...with a stale count left under it, as the client does
		inv.invSlotObjId[3] = 996;      // 995 again, a second stack
		inv.invSlotObjCount[3] = 7;
		inv.invSlotObjId[4] = 1;        // obj 0, the id that proves the plus one is applied
		inv.invSlotObjCount[4] = 1;

		PluginContext ctx = new PluginContext(client);
		check(ctx.getInventorySize() == 28, "the inventory has 28 slots");
		check(ctx.getInventoryObjId(0) == 995, "slot 0 holds coins, one less than it is stored as");
		check(ctx.getInventoryObjCount(0) == 1000, "...a thousand of them");
		check(ctx.getInventoryObjId(1) == 1205, "slot 1 holds the dagger");
		check(ctx.getInventoryObjId(2) == -1, "slot 2 is empty, and stays empty rather than closing up");
		check(ctx.getInventoryObjCount(2) == 0,
			"...and holds nothing, whatever stale count is left under it");
		check(ctx.getInventoryObjId(4) == 0,
			"slot 4 holds obj 0, which a decode that forgot the plus one would call empty");
		check(ctx.getInventoryObjCount(4) == 1, "...and one of it");
		check(ctx.getInventoryObjId(27) == -1, "the last slot is empty");

		check(ctx.countObj(995) == 1007, "coins are counted across both stacks of them");
		check(ctx.countObj(1205) == 1, "...and the dagger once");
		check(ctx.countObj(0) == 1, "...and obj 0 once, rather than once per empty slot");
		check(ctx.countObj(4151) == 0, "...and something not carried, not at all");
		check(ctx.hasObj(995) && ctx.hasObj(0), "what is carried is carried");
		check(!ctx.hasObj(4151) && !ctx.hasObj(-1), "...and what is not, is not");

		check(ctx.getInventoryObjId(28) == -1, "a slot past the end is empty rather than an exception");
		check(ctx.getInventoryObjId(-1) == -1, "...and so is one before the start");
		check(ctx.getInventoryObjCount(28) == 0, "...both of which hold nothing");

		Component.types = null;
		Component.data = null;
	}

	// ------------------------------------------------------------------ 6. locs

	/**
	 * pickChild, with anything it throws turned into a failure that has a NAME.
	 *
	 * Same reason as {@link #varp}: a state past the end of the table must answer -1, and an
	 * exception is not -1. Letting one out ends the run with no failure printed, which the
	 * mutation runner counts as a crash rather than a catch.
	 */
	static int child(int[] children, int value) {
		try {
			return PluginContext.pickChild(children, value);
		} catch (RuntimeException threw) {
			return THREW;
		}
	}

	static void locTests() {
		// The table lookup, which is the part that can be wrong invisibly: an off-by-one here
		// hands back a REAL loc one state along - a farming patch at the growth stage either
		// side of the one it is at, with the right name and a plausible picture.
		int[] children = { 40, 41, 42, -1, 44 };
		check(child(children, 0) == 40, "value 0 picks the first form");
		check(child(children, 2) == 42, "...and value 2 the third, not the second");
		check(child(children, 4) == 44, "...and the last value the last form");
		check(child(children, 5) == -1, "a value past the table picks nothing");
		check(child(children, -1) == -1, "...as does one below it");
		check(child(children, 3) == -1,
			"and an empty slot is 'draw nothing', which is an answer rather than a failure");
		check(child(null, 0) == -1, "a loc with no table at all picks nothing");

		// A sweep: every value of an 8-bit state against a full table, which is the shape a
		// farming patch actually has.
		int[] full = new int[256];
		for (int i = 0; i < 256; i++) {
			full[i] = 5000 + i;
		}
		int wrong = 0;
		for (int i = 0; i < 256; i++) {
			if (child(full, i) != 5000 + i) {
				wrong++;
			}
		}
		check(wrong == 0, "and all 256 states of a patch-sized table pick their own form");

		// The no-cache contract, as for the rest of this class.
		Client client = newClient();
		if (client == null) {
			skip("needs a display to build a Client - run under xvfb-run");
			return;
		}
		LocType.field1612 = null;
		PluginContext ctx = new PluginContext(client);
		String threw = "";
		try {
			check(ctx.getLocCount() == 0, "with no loc cache, the count is 0");
			check(ctx.resolveLoc(0) == -1, "...and nothing resolves");
			check("".equals(ctx.getLocName(0)), "...and nothing is named");
			check("".equals(ctx.getLocDesc(0)), "...nor described");
			check("".equals(ctx.getLocOp(0, 0)), "...nor offers an option");
			check(ctx.getLocVarbit(0) == -1 && ctx.getLocVarp(0) == -1, "...nor answers for a varbit");
			check(ctx.getLocOp(-1, 99) == "" || "".equals(ctx.getLocOp(-1, 99)), "...at any index");
		} catch (Throwable error) {
			threw = error.getClass().getName();
		}
		check(threw.length() == 0, "and not one of them threw"
			+ (threw.length() == 0 ? "" : " (" + threw + ")"));
	}

	// ------------------------------------------------------------------ 7. the level

	static void levelTests() {
		check(PluginApi.LEVEL == 11, "this client is API level 11 (" + PluginApi.LEVEL + ")");
		check(PluginApi.supports(11), "a plugin asking for 11 runs here");
		check(PluginApi.supports(10), "...and one asking for 10 still does");
		check(!PluginApi.supports(12), "one asking for 12 does not");

		// THE LINE THE API IS DRAWN ON, which no run of it can state. A varp is the server's copy
		// of the truth; a plugin that could write the client's could make a quest look finished
		// or a patch look grown, and the two would disagree until the next update arrived.
		String ctx = read("src/main/java/jagex2/client/plugin/PluginContext.java");
		check(ctx.indexOf("public int getVarp(") >= 0, "the context can read a varp");
		check(ctx.indexOf("public int getVarbit(") >= 0, "...and a varbit");
		check(ctx.indexOf("setVarp(") < 0, "...and there is no way through it to write a varp");
		check(ctx.indexOf("setVarbit(") < 0, "...nor a varbit");
		check(ctx.indexOf("public int getInventoryObjId(") >= 0, "it can read a slot");
		check(ctx.indexOf("setInventory") < 0 && ctx.indexOf("public void dropObj(") < 0
				&& ctx.indexOf("public void useObj(") < 0,
			"...and cannot move, drop or use what is in one");
		check(ctx.indexOf("public int resolveLoc(") >= 0, "it can see what form a loc is in");
		check(ctx.indexOf("setLoc(") < 0 && ctx.indexOf("public void addLoc(") < 0,
			"...and cannot put one anywhere or change one");
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
