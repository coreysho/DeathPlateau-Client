/*
 * Headless test for the clue scroll page.
 *
 * WHAT THIS GUARDS. Three things, and the first is the one that fails in silence.
 *
 * THE TABLE IS BINARY-SEARCHED, so its ids have to be ascending. They are, because the
 * generator sorts them - but if they ever stop being, indexOf does not throw or return nonsense
 * it returns -1 for clues that ARE in the table, and the page says "no clue scroll" while one
 * is sitting in the bag. So the order is asserted, not assumed.
 *
 * THE BEARING AND THE DISTANCE are arithmetic a player checks against their own feet, so being
 * nearly right is worse than being absent. Distance is Chebyshev because that is how this game
 * moves - a diagonal step covers a tile like any other - and a Pythagorean figure would read
 * always too long. The compass has a band either side of each axis, or two tiles east reads as
 * "north east".
 *
 * AND THE TABLE AGAINST THE CONTENT IT CAME FROM, by running the generator again when the
 * content repo is beside this one. Without it the check says SKIPPED, because a check that
 * cannot run must not read as one that passed.
 */
package jagex2.client.plugin.builtin;

public class ClueTest {

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
		System.out.println("1. the table, which is binary-searched");
		tableTests();
		System.out.println();
		System.out.println("2. which way, and how far");
		bearingTests();
		System.out.println();
		System.out.println("3. the clue's own words");
		textTests();
		System.out.println();
		System.out.println("4. the page with nothing in the bag");
		emptyTests();
		System.out.println();
		System.out.println("5. the table against the content it came from");
		generatedTests();

		System.out.println();
		if (fails == 0) {
			System.out.println("all checks passed");
		} else {
			System.out.println(fails + " CHECK(S) FAILED");
		}
		System.exit(fails == 0 ? 0 : 1);
	}

	// ------------------------------------------------------------------ 1. the table

	static void tableTests() {
		check(ClueSteps.count() > 80, "the table has the content's steps in it (" + ClueSteps.count() + ")");
		check(ClueSteps.TEXT.length == ClueSteps.count(), "every step has words, or a blank where a map clue is");
		check(ClueSteps.X.length == ClueSteps.count() && ClueSteps.Z.length == ClueSteps.count()
			&& ClueSteps.LEVEL.length == ClueSteps.count(), "...and a place, or a -1 where it has none");

		// ASCENDING, OR THE SEARCH BELOW QUIETLY STOPS FINDING THINGS. indexOf would return -1
		// for a clue that is in the table and the page would say the bag was empty.
		int outOfOrder = 0;
		for (int i = 1; i < ClueSteps.ID.length; i++) {
			if (ClueSteps.ID[i] <= ClueSteps.ID[i - 1]) {
				outOfOrder++;
			}
		}
		check(outOfOrder == 0, "the ids ascend, which is what makes the search work");

		// And the search finds every one of them, which is the thing that order is for.
		int missed = 0;
		for (int i = 0; i < ClueSteps.ID.length; i++) {
			if (ClueSteps.indexOf(ClueSteps.ID[i]) != i) {
				missed++;
			}
		}
		check(missed == 0, "and every id in it is found at its own index");
		check(ClueSteps.indexOf(-1) == -1, "something that is not a clue is not found");
		check(ClueSteps.indexOf(999999) == -1, "...at either end");
		check(ClueSteps.indexOf(ClueSteps.ID[0] - 1) == -1, "...including just below the first");

		// A coordinate is a real place or it is absent; half of one is neither.
		int halfPlaced = 0;
		int placed = 0;
		for (int i = 0; i < ClueSteps.count(); i++) {
			boolean hasX = ClueSteps.X[i] >= 0;
			boolean hasZ = ClueSteps.Z[i] >= 0;
			if (hasX != hasZ) {
				halfPlaced++;
			}
			if (hasX) {
				placed++;
				if (ClueSteps.X[i] < 1000 || ClueSteps.X[i] > 4000 || ClueSteps.Z[i] < 1000) {
					halfPlaced++;
				}
			}
		}
		check(halfPlaced == 0, "every coordinate is a whole one, somewhere on the map");
		check(placed > 30, "and a good few steps have one (" + placed + ")");
	}

	// ------------------------------------------------------------------ 2. bearing and distance

	static void bearingTests() {
		check("north".equals(CluePlugin.bearing(3200, 3200, 3200, 3300)), "straight up is north");
		check("south".equals(CluePlugin.bearing(3200, 3200, 3200, 3100)), "straight down is south");
		check("east".equals(CluePlugin.bearing(3200, 3200, 3300, 3200)), "right is east");
		check("west".equals(CluePlugin.bearing(3200, 3200, 3100, 3200)), "left is west");
		check("north east".equals(CluePlugin.bearing(3200, 3200, 3300, 3300)), "and a true diagonal is both");
		check("south west".equals(CluePlugin.bearing(3200, 3200, 3100, 3100)), "...whichever diagonal it is");
		check("here".equals(CluePlugin.bearing(3200, 3200, 3200, 3200)), "nowhere to go is here");

		// THE BAND. Two tiles off an axis is not a diagonal, and calling it one sends a player
		// looking in the wrong place for a dig spot they are nearly on top of.
		check("east".equals(CluePlugin.bearing(3200, 3200, 3300, 3202)),
			"a hundred east and two north is east, not north east");
		check("north".equals(CluePlugin.bearing(3200, 3200, 3202, 3300)), "...and the same the other way round");
		check("north east".equals(CluePlugin.bearing(3200, 3200, 3250, 3300)),
			"but a real slant is named as one");

		// DISTANCE IS CHEBYSHEV: a diagonal step costs one tile here, like any other.
		check(CluePlugin.paces(3200, 3200, 3300, 3200) == 100, "a hundred east is a hundred paces");
		check(CluePlugin.paces(3200, 3200, 3300, 3300) == 100,
			"and a hundred east AND north is still a hundred, because a diagonal step is one step");
		check(CluePlugin.paces(3200, 3200, 3100, 3100) == 100, "...in any direction");
		check(CluePlugin.paces(3200, 3200, 3200, 3200) == 0, "standing on it is none");
		check(CluePlugin.paces(3300, 3300, 3200, 3250) == 100, "and it does not care which way round it is asked");
	}

	// ------------------------------------------------------------------ 3. the words

	static void textTests() {
		check("Search the chest in the Duke of Lumbridge's bedroom."
			.equals(CluePlugin.flatten("Search the chest in the|Duke of Lumbridge's bedroom.")),
			"the game's line breaks become spaces, for one flowing row");
		check("one two".equals(CluePlugin.flatten("one |two")), "a break after a space does not double it");
		check("one two".equals(CluePlugin.flatten("one||two")), "nor do two breaks together");
		check("one two".equals(CluePlugin.flatten("  one  two  ")), "and the ends are trimmed");
		check("".equals(CluePlugin.flatten("")), "nothing flattens to nothing");
		check("".equals(CluePlugin.flatten(null)), "...as does no text at all");

		// Every step either says something or is a map clue, which says nothing on purpose.
		int wordless = 0;
		for (int i = 0; i < ClueSteps.count(); i++) {
			if (CluePlugin.flatten(ClueSteps.TEXT[i]).length() == 0 && ClueSteps.X[i] < 0) {
				wordless++;
			}
		}
		check(wordless == 0, "and no step is both wordless and placeless, which would show an empty row");
	}

	// ------------------------------------------------------------------ 4. nothing in the bag

	static void emptyTests() {
		CluePlugin plugin = new CluePlugin();
		// No context, so no inventory: the page shows its message rather than a blank row.
		check(plugin.clueSlot() == -1, "with no inventory there is no clue");
		check(plugin.step() == -1, "...and no step");
		check("".equals(plugin.label()) && "".equals(plugin.detail()) && "".equals(plugin.value()),
			"...and the row says nothing rather than throwing");
	}

	// ------------------------------------------------------------------ 5. the generator

	static void generatedTests() {
		String root = System.getProperty("dp.root", ".");
		java.io.File content = new java.io.File(new java.io.File(root).getParentFile(),
			"DeathPlateau-Content");
		if (!content.isDirectory()) {
			skip("no content repo beside this one, so the table cannot be checked against it");
			return;
		}
		try {
			ProcessBuilder pb = new ProcessBuilder("python3", "tools/clues/make_clues.py",
				"--check", "--content", content.getAbsolutePath());
			pb.directory(new java.io.File(root));
			pb.redirectErrorStream(true);
			Process p = pb.start();
			java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
			byte[] buf = new byte[4096];
			int n;
			while ((n = p.getInputStream().read(buf)) > 0) {
				out.write(buf, 0, n);
			}
			int code = p.waitFor();
			check(code == 0, "ClueSteps is up to date with the content (" + out.toString("UTF-8").trim() + ")");
		} catch (Throwable error) {
			check(false, "the generator ran (" + error + ")");
		}
	}
}
