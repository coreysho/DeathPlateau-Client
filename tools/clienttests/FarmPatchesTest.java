/*
 * Headless test for the farming page.
 *
 * WHAT THIS GUARDS. The client has every patch's picture and no clock: the appearance varp
 * transmits, farming_time_N does not. So the page says two kinds of thing, and they have
 * completely different standing. READY and DISEASED are read out of the cache's own multiloc
 * table and are exact. The minutes are WATCHED - the engine advances a patch at most once a
 * minute while you are online, so a cadence is measured by seeing it happen twice - and are an
 * estimate until then.
 *
 * The failure that matters is the page promising more than it knows. An interval measured from
 * the login that first saw a patch is not a cadence, it is how long ago you logged in; counting
 * the first sight of a patch as a change makes the page announce that all 32 just grew. Both
 * produce a confident number that a player plans a trip around, and neither throws.
 *
 * So the rules are a model with no client in it and are tested as arithmetic, the classifiers
 * are pure functions, and the patch table is checked against the cache ids it was derived from.
 */
package jagex2.client.plugin.builtin;

public class FarmPatchesTest {

	static int fails;

	static void check(boolean ok, String what) {
		System.out.println((ok ? "  ok   " : "FAIL   ") + what);
		if (!ok) {
			fails++;
		}
	}

	public static void main(String[] args) {
		System.out.println("1. seeing a patch change");
		observeTests();
		System.out.println();
		System.out.println("2. a cadence has to be measured, not assumed");
		cadenceTests();
		System.out.println();
		System.out.println("3. spans a sidebar row has space for");
		spanTests();
		System.out.println();
		System.out.println("4. what the cache says a patch is doing");
		classifyTests();
		System.out.println();
		System.out.println("5. the order the page puts them in");
		orderTests();
		System.out.println();
		System.out.println("6. the patch table");
		tableTests();
		System.out.println();
		System.out.println("7. the line the page is drawn on");
		sourceTests();

		System.out.println();
		if (fails == 0) {
			System.out.println("all checks passed");
		} else {
			System.out.println(fails + " CHECK(S) FAILED");
		}
		System.exit(fails == 0 ? 0 : 1);
	}

	// ------------------------------------------------------------------ 1. observing

	static void observeTests() {
		FarmPatches p = new FarmPatches(4);
		check(p.size() == 4, "it holds the patches it was made for");
		check(p.stateOf(0) == FarmPatches.UNSEEN, "a patch starts unseen");

		// THE FIRST SIGHT IS NOT A CHANGE. Logging in sets all 32 at once, and counting those
		// would have the page announce that every patch just grew.
		check(!p.observe(0, 23, 100), "the first sight of a patch is not a change");
		check(p.stateOf(0) == 23, "...but it is remembered");
		check(p.changes(0) == 0, "...and counts as no changes");

		check(!p.observe(0, 23, 101), "seeing the same thing again is not a change");
		check(p.observe(0, 24, 102), "a different state is");
		check(p.stateOf(0) == 24 && p.changes(0) == 1, "...and is remembered and counted");

		check(p.sinceChange(0, 107) == 5, "it knows how long ago that was");
		check(p.sinceChange(0, 102) == 0, "...which is 0 at the moment it happened");
		check(p.sinceChange(0, 90) == 0, "...and never negative, whatever the clock does");
		check(p.sinceChange(1, 107) == 0, "a patch never seen has no elapsed time");

		// Out of range must answer rather than throw: this is called per patch per tick.
		check(p.stateOf(-1) == FarmPatches.UNSEEN && p.stateOf(99) == FarmPatches.UNSEEN,
			"a patch out of range is simply unseen");
		check(!p.observe(-1, 1, 1) && !p.observe(99, 1, 1), "...and cannot be observed");
	}

	// ------------------------------------------------------------------ 2. the cadence

	static void cadenceTests() {
		FarmPatches p = new FarmPatches(2);
		p.observe(0, 10, 0);                 // first sight, at minute 0
		check(!p.isEstimateReady(0), "nothing is estimated off a first sight");
		check(p.untilNext(0, 0) == -1, "...and no next time is offered");

		p.observe(0, 11, 40);                // first change
		check(!p.isEstimateReady(0),
			"ONE change is still not a cadence - that interval is just when you logged in");
		check(p.untilNext(0, 40) == -1, "...so still no next time");

		p.observe(0, 12, 80);                // second change, 40 minutes later
		check(p.isEstimateReady(0), "two changes give a cadence");
		check(p.intervalOf(0) == 40, "...of the gap between them");
		check(p.untilNext(0, 80) == 40, "...and the next one is due a cadence later");
		check(p.untilNext(0, 100) == 20, "...counting down as time passes");
		check(p.untilNext(0, 130) == 0, "...and resting at 0 once it is overdue rather than going negative");

		// A changed cadence is followed, not averaged: a crop that moves to a different stage
		// length should not be reported as the average of the two.
		p.observe(0, 13, 100);
		check(p.intervalOf(0) == 20, "a new interval replaces the old one rather than averaging");

		// TWO CHANGES IN ONE MINUTE give an interval of zero, which is not a cadence: offered as
		// one it says "due now", for ever, on a patch that is quietly still growing.
		FarmPatches fast = new FarmPatches(1);
		fast.observe(0, 1, 500);
		fast.observe(0, 2, 500);
		fast.observe(0, 3, 500);
		check(fast.changes(0) == 2, "two changes inside one minute are still two changes");
		check(!fast.isEstimateReady(0), "...but an interval of zero minutes is not a cadence");
		check(fast.untilNext(0, 500) == -1, "...so no next time is offered off it");

		p.reset();
		check(p.stateOf(0) == FarmPatches.UNSEEN && !p.isEstimateReady(0) && p.changes(0) == 0,
			"and a reset forgets all of it");
	}

	// ------------------------------------------------------------------ 3. spans

	static void spanTests() {
		check("0m".equals(FarmPatches.span(0)), "no time at all is 0m");
		check("59m".equals(FarmPatches.span(59)), "under an hour is minutes");
		check("1h".equals(FarmPatches.span(60)), "a round hour has no minutes on it");
		check("1h 12m".equals(FarmPatches.span(72)), "and an unround one does");
		check("23h 59m".equals(FarmPatches.span(1439)), "up to a day");
		check("1d".equals(FarmPatches.span(1440)), "a round day has no hours on it");
		check("2d 3h".equals(FarmPatches.span(3060)), "and an unround one does");
		check("".equals(FarmPatches.span(-1)), "and a negative span is no span at all");
	}

	// ------------------------------------------------------------------ 4. classifying

	static void classifyTests() {
		// READY IS THE CACHE'S OWN OPTION, not a counted growth stage. The cache is what the
		// game goes by, so a patch that offers Harvest can be harvested by definition.
		check(FarmPatchesPlugin.isReady("Harvest", "Inspect", "Guide"), "a form offering Harvest is ready");
		check(FarmPatchesPlugin.isReady("Inspect", "Harvest", ""), "...in whichever slot it sits");
		check(FarmPatchesPlugin.isReady("Pick", "Inspect", ""), "a bush offers Pick instead, and counts");
		check(!FarmPatchesPlugin.isReady("Inspect", "Guide", "Rake"), "and a growing one is not ready");
		check(!FarmPatchesPlugin.isReady("", "", ""), "nor is one with no options at all");
		check(FarmPatchesPlugin.isReady("harvest", "", ""), "the option is matched whatever its case");

		check(FarmPatchesPlugin.isSick("Diseased Belladonna"), "a diseased form says so in its name");
		check(FarmPatchesPlugin.isSick("Dead potato plant"), "...and so does a dead one");
		check(!FarmPatchesPlugin.isSick("Potato plant"), "and a healthy one does not");
		check(!FarmPatchesPlugin.isSick(null), "nor does a form with no name at all");

		check(FarmPatchesPlugin.isEmpty("Herb patch"), "a bare patch is empty");
		check(FarmPatchesPlugin.isEmpty("Weeds"), "...and so are weeds");
		check(FarmPatchesPlugin.isEmpty(""), "...and so is nothing");
		check(!FarmPatchesPlugin.isEmpty("Potato plant"), "but a crop is not");
		check(!FarmPatchesPlugin.isEmpty("Guam"), "...whatever it is called");
	}

	// ------------------------------------------------------------------ 5. the order

	static void orderTests() {
		// Diseased first, because it is the one thing a player has to act on before it dies;
		// then ready; then growing; then empty.
		check(FarmPatchesPlugin.rank(false, true, false) < FarmPatchesPlugin.rank(true, false, false),
			"diseased sorts above ready");
		check(FarmPatchesPlugin.rank(true, false, false) < FarmPatchesPlugin.rank(false, false, false),
			"ready sorts above growing");
		check(FarmPatchesPlugin.rank(false, false, false) < FarmPatchesPlugin.rank(false, false, true),
			"growing sorts above empty");
		// A diseased patch that also offers Harvest is still diseased: it is the thing to do.
		check(FarmPatchesPlugin.rank(true, true, false) == FarmPatchesPlugin.rank(false, true, false),
			"and diseased wins over ready when a form is both");
	}

	// ------------------------------------------------------------------ 6. the table

	static void tableTests() {
		int[] varbits = FarmPatchesPlugin.PATCH_VARBIT;
		String[] names = FarmPatchesPlugin.PATCH_NAME;
		check(varbits.length == names.length,
			"every patch has a name (" + varbits.length + " varbits, " + names.length + " names)");
		check(varbits.length == 32, "and there are 32 of them");

		// A DUPLICATE WOULD BE INVISIBLE: two rows reading the same patch, both plausible.
		int dupes = 0;
		for (int i = 0; i < varbits.length; i++) {
			for (int j = i + 1; j < varbits.length; j++) {
				if (varbits[i] == varbits[j]) {
					dupes++;
				}
			}
		}
		check(dupes == 0, "no two patches read the same varbit");
		int sameName = 0;
		for (int i = 0; i < names.length; i++) {
			for (int j = i + 1; j < names.length; j++) {
				if (names[i].equals(names[j])) {
					sameName++;
				}
			}
		}
		check(sameName == 0, "and no two are called the same thing");

		// THE IDS THEMSELVES, pinned. They were derived by crossing the content's
		// farming_state.rs2 accessor with the cache's varbit table, and a wrong one reads a
		// different patch - or somebody else's varbit entirely - with no symptom but a row that
		// never changes. Written out here so the derivation cannot quietly drift.
		check(varbits[0] == 708 && varbits[7] == 715, "allotments 1-8 are varbits 708-715");
		check(varbits[8] == 780 && varbits[11] == 783, "herbs 1-4 are 780-783");
		check(varbits[12] == 716 && varbits[15] == 719, "hops 1-4 are 716-719");
		check(varbits[16] == 728 && varbits[19] == 731, "flowers 1-4 are 728-731");
		check(varbits[20] == 732 && varbits[23] == 735, "bushes 1-4 are 732-735");
		check(varbits[24] == 700 && varbits[27] == 703, "trees 1-4 are 700-703");
		check(varbits[28] == 704 && varbits[31] == 707, "fruit trees 1-4 are 704-707");
		check(names[8].indexOf("Herb") == 0, "and the names line up with them");
	}

	// ------------------------------------------------------------------ 7. the line

	static void sourceTests() {
		String src = read("src/main/java/jagex2/client/plugin/builtin/FarmPatchesPlugin.java");
		check(src.length() > 0, "the plugin's source is where the test expects it");

		// AN ESTIMATE MUST SAY SO. The whole standing of this page is that the exact parts are
		// exact; a minute printed as fact that was inferred from two observations undoes that.
		// The METHOD, at one tab, not the one-line forwarder inside the anonymous ConfigList at
		// four. The first version of this matched the forwarder and read the wrong block.
		int detail = src.indexOf("\n\tString detail(int index) {");
		String body = detail < 0 ? "" : src.substring(detail, src.indexOf("\n\t}", detail));
		check(body.indexOf("isEstimateReady") >= 0,
			"a next time is only offered when a cadence has been measured");
		check(body.indexOf("estimated") >= 0, "...and is labelled as an estimate when it is");

		// The scan is spread, or it is a visible hitch on login.
		check(src.indexOf("SCAN_PER_TICK") >= 0, "the loc scan is done a slice at a time");

		// And the page reads, as every page here does.
		check(src.indexOf("setVarp") < 0 && src.indexOf("consume()") < 0,
			"the page changes nothing and consumes nothing");
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
