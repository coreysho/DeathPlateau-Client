/*
 * Headless test for the clue scroll page.
 *
 * WHAT THIS GUARDS. Four things, and the first is the one that fails in silence.
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
 * THE PEOPLE, WHICH ARE SCRAPED AND NOT DECLARED. A clue's words and its dig spot are its own
 * params, but who accepts an anagram is a trigger in some NPC's script, and a pattern that goes
 * stale there costs destinations without costing steps - the table still builds, just emptier.
 * Two checks stand against that: the generator refuses to write a table where half the steps
 * have lost their place, and the anagrams below are checked AGAINST THEIR OWN LETTERS. "OK CO"
 * unscrambles to Cook whatever any script says, so a mapping that drifted onto the wrong person
 * stops being an anagram of the clue and the count drops.
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
		System.out.println("4. where the step wants you");
		guidanceTests();
		System.out.println();
		System.out.println("5. the people, checked against the anagrams that hide them");
		peopleTests();
		System.out.println();
		System.out.println("6. the bag, and the rows it makes");
		emptyTests();
		System.out.println();
		System.out.println("7. the table against the content it came from");
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
		check(ClueSteps.SPOT.length == ClueSteps.count() && ClueSteps.WHO.length == ClueSteps.count()
			&& ClueSteps.ANSWER.length == ClueSteps.count() && ClueSteps.GUARD.length == ClueSteps.count(),
			"...and a kind of place, a person, an answer and a guardian, in step with the rest");

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

		// A coordinate is a real place or it is absent; half of one is neither. And a kind of
		// place without a place, or a place with no kind, is the page promising a row it cannot
		// fill - guidance() reads SPOT and then X and Z without asking again.
		int halfPlaced = 0;
		int placed = 0;
		int digs = 0;
		int searches = 0;
		for (int i = 0; i < ClueSteps.count(); i++) {
			boolean hasX = ClueSteps.X[i] >= 0;
			boolean hasZ = ClueSteps.Z[i] >= 0;
			boolean hasSpot = ClueSteps.SPOT[i] != ClueSteps.SPOT_NONE;
			if (hasX != hasZ || hasX != hasSpot) {
				halfPlaced++;
			}
			if (hasX) {
				placed++;
				if (ClueSteps.X[i] < 1000 || ClueSteps.X[i] > 4000 || ClueSteps.Z[i] < 1000) {
					halfPlaced++;
				}
			}
			if (ClueSteps.SPOT[i] == ClueSteps.SPOT_DIG) {
				digs++;
			}
			if (ClueSteps.SPOT[i] == ClueSteps.SPOT_SEARCH) {
				searches++;
			}
		}
		check(halfPlaced == 0, "every coordinate is a whole one, somewhere on the map, with a kind");
		check(placed > 60, "and most steps have one (" + placed + ")");
		check(digs > 30, "the scrolls' own dig spots are all there (" + digs + ")");
		check(searches > 20, "and so are the tiles scraped out of the scripts (" + searches + ")");
		check(digs + searches == placed, "...and those two kinds are all the kinds there are");

		// An answer is a number or it is nothing: the page prints it as it stands.
		int notANumber = 0;
		int answers = 0;
		for (int i = 0; i < ClueSteps.count(); i++) {
			String answer = ClueSteps.ANSWER[i];
			if (answer.length() == 0) {
				continue;
			}
			answers++;
			for (int c = 0; c < answer.length(); c++) {
				if (answer.charAt(c) < '0' || answer.charAt(c) > '9') {
					notANumber++;
					break;
				}
			}
		}
		check(answers > 4, "the challenge scrolls brought their answers (" + answers + ")");
		check(notANumber == 0, "...and every one of them is a number");

		// A PERSON OR A PLACE, NEVER BOTH, which is what lets guidance() print one line and
		// stop. It holds because the two come from different shapes of script - a trigger over
		// a conversation, a tile in a search - and a step that ever had both would have its
		// place silently swallowed by the person.
		int bothWays = 0;
		for (int i = 0; i < ClueSteps.count(); i++) {
			if (ClueSteps.WHO[i].length() > 0 && ClueSteps.SPOT[i] != ClueSteps.SPOT_NONE) {
				bothWays++;
			}
		}
		check(bothWays == 0, "no step both names somebody and points at a tile");
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
		int map = mapClue();
		check(map >= 0, "the table has a map clue in it, which is a step with no words at all");
		check(map >= 0 && "Map clue".equals(CluePlugin.wordsOf(map)),
			"...and a wordless step is labelled a map clue");
		check("".equals(CluePlugin.wordsOf(-1)), "and no step at all says nothing");
	}

	// ------------------------------------------------------------------ 4. the second line

	static void guidanceTests() {
		int dig = firstSpot(ClueSteps.SPOT_DIG, false);
		int search = firstSpot(ClueSteps.SPOT_SEARCH, false);
		int answer = firstAnswer();
		int hidden = firstHiddenPerson();
		int named = firstNamedPerson();
		int guarded = firstGuarded();

		check(dig >= 0 && search >= 0 && answer >= 0 && hidden >= 0 && named >= 0 && guarded >= 0,
			"there is a step of each kind to try: a dig, a search, a challenge, a hidden person,"
			+ " a named one and a guarded one (" + dig + ", " + search + ", " + answer + ", "
			+ hidden + ", " + named + ", " + guarded + ")");
		if (dig < 0 || search < 0 || answer < 0 || hidden < 0 || named < 0 || guarded < 0) {
			return;
		}

		// A DIG SAYS DIG AND A SEARCH DOES NOT, because they are different acts: a player who
		// reads "dig" at a crate wastes a spade charge and their patience.
		String far = CluePlugin.guidance(dig, ClueSteps.X[dig] - 40, ClueSteps.Z[dig],
			ClueSteps.LEVEL[dig], true, true, true);
		check(far.startsWith("dig 40 tiles east"), "a dig spot forty west of you is forty tiles east, to dig (" + far + ")");
		String onIt = CluePlugin.guidance(dig, ClueSteps.X[dig], ClueSteps.Z[dig],
			ClueSteps.LEVEL[dig], true, true, true);
		check(onIt.startsWith("dig here"), "...and standing on it, it just says dig here (" + onIt + ")");

		String crate = CluePlugin.guidance(search, ClueSteps.X[search], ClueSteps.Z[search] - 7,
			ClueSteps.LEVEL[search], true, true, true);
		check(crate.startsWith("7 tiles north") && crate.indexOf("dig") < 0,
			"a crate seven south of you is seven tiles north, and not to dig (" + crate + ")");
		String onCrate = CluePlugin.guidance(search, ClueSteps.X[search], ClueSteps.Z[search],
			ClueSteps.LEVEL[search], true, true, true);
		check(onCrate.startsWith("you are standing on it"), "...and standing on it, you are standing on it");

		// THE FLOOR IS NOT IN THE DISTANCE. Twelve tiles away and one storey up is a staircase
		// away, and the bearing alone would walk a player into the wall under it.
		String upstairs = CluePlugin.guidance(search, ClueSteps.X[search], ClueSteps.Z[search] - 7,
			ClueSteps.LEVEL[search] + 1, true, true, true);
		check(upstairs.indexOf("on another floor") >= 0, "a step one storey off says so (" + upstairs + ")");
		check(crate.indexOf("on another floor") < 0, "...and one on your own floor does not");

		check("".equals(CluePlugin.guidance(search, 3200, 3200, 0, false, true, true)),
			"with the distance switched off there is no second line");

		// THE ANSWER, which is the one thing here the scroll does not already say.
		String asked = CluePlugin.guidance(answer, 3200, 3200, 0, true, true, true);
		check(asked.startsWith("the answer is ") && asked.endsWith(ClueSteps.ANSWER[answer]),
			"a challenge scroll is given its answer (" + asked + ")");
		check("".equals(CluePlugin.guidance(answer, 3200, 3200, 0, true, false, true)),
			"...and withheld when the switch is off");

		String person = CluePlugin.guidance(hidden, 3200, 3200, 0, true, true, true);
		check(person.equals("speak to " + ClueSteps.WHO[hidden]),
			"a step whose words hide the person is told who it is (" + person + ")");
		check("".equals(CluePlugin.guidance(hidden, 3200, 3200, 0, true, false, true)),
			"...and that is the same switch");

		// AND THE PAGE DOES NOT TALK TO ITSELF. "Speak to Ned in Draynor Village" with
		// "speak to Ned" under it is a line that costs a row and says nothing.
		check(CluePlugin.namesPerson(named) && !CluePlugin.namesPerson(hidden),
			"...and the two are told apart by the words themselves (\"" + CluePlugin.wordsOf(named)
			+ "\" names " + ClueSteps.WHO[named] + ", \"" + CluePlugin.wordsOf(hidden)
			+ "\" does not name " + ClueSteps.WHO[hidden] + ")");
		String already = CluePlugin.guidance(named, 3200, 3200, 0, true, true, true);
		check(already.indexOf("speak to") < 0,
			"so a step that already names the person gets no line repeating it (\"" + already + "\")");

		String warned = CluePlugin.guidance(guarded, ClueSteps.X[guarded], ClueSteps.Z[guarded],
			ClueSteps.LEVEL[guarded], true, true, true);
		check(warned.indexOf("a " + ClueSteps.GUARD[guarded] + " guards it") >= 0,
			"a guarded step warns about it (" + warned + ")");
		check(warned.indexOf("dig here") >= 0, "...on top of where to go, not instead of it");
		check(CluePlugin.guidance(guarded, ClueSteps.X[guarded], ClueSteps.Z[guarded],
			ClueSteps.LEVEL[guarded], true, true, false).indexOf("guards it") < 0,
			"...and the warning has its own switch");

		check("".equals(CluePlugin.guidance(-1, 3200, 3200, 0, true, true, true)),
			"and no step at all has nothing to say");

		// THE READING ON THE RIGHT is the place or the number, never both and never a guess.
		check((ClueSteps.X[dig] + ", " + ClueSteps.Z[dig]).equals(CluePlugin.reading(dig, true)),
			"the right of the row is the place itself");
		check(ClueSteps.ANSWER[answer].equals(CluePlugin.reading(answer, true)),
			"...or the number a challenge wants");
		check("".equals(CluePlugin.reading(answer, false)), "...which the same switch withholds");
		check("".equals(CluePlugin.reading(firstSpot(ClueSteps.SPOT_NONE, true), true)),
			"a step with nowhere to be has no reading");
		check("".equals(CluePlugin.reading(-1, true)), "and nor has no step at all");
	}

	// ------------------------------------------------------------------ 5. the people

	static void peopleTests() {
		// THE ANAGRAMS CHECK THEMSELVES. WHO is scraped out of script triggers, so nothing in
		// the table proves it landed on the right person - except that an anagram clue's own
		// scrambled letters are the answer's letters. "BAR BELL SEEK" is the exception and is
		// meant to be: it unscrambles to "kebab seller", whose name is Karim.
		int anagrams = 0;
		int unscrambled = 0;
		for (int i = 0; i < ClueSteps.count(); i++) {
			String text = ClueSteps.TEXT[i];
			if (text.indexOf("This anagram reveals") < 0) {
				continue;
			}
			anagrams++;
			String scrambled = text.substring(text.lastIndexOf('|') + 1);
			if (letters(scrambled).equals(letters(ClueSteps.WHO[i]))) {
				unscrambled++;
			}
		}
		check(anagrams > 5, "the table has the anagram clues in it (" + anagrams + ")");
		check(unscrambled >= 6,
			"and the people they were mapped to are their own letters (" + unscrambled + " of " + anagrams + ")");

		// Nobody is mapped to an empty name, which is what a missing npc config would leave.
		int blank = 0;
		int people = 0;
		for (int i = 0; i < ClueSteps.count(); i++) {
			if (ClueSteps.WHO[i].length() > 0) {
				people++;
				if (ClueSteps.WHO[i].trim().length() == 0) {
					blank++;
				}
			}
		}
		check(people > 20, "a good few steps name somebody (" + people + ")");
		check(blank == 0, "and none of them is a name made of spaces");

		int guards = 0;
		for (int i = 0; i < ClueSteps.count(); i++) {
			if (ClueSteps.GUARD[i].length() > 0) {
				guards++;
			}
		}
		check(guards > 10, "and the guarded steps carry what guards them (" + guards + ")");
	}

	/** A name's letters, lowercased and sorted, for comparing one scramble against another. */
	static String letters(String text) {
		char[] out = new char[text.length()];
		int count = 0;
		for (int i = 0; i < text.length(); i++) {
			char c = Character.toLowerCase(text.charAt(i));
			if (c >= 'a' && c <= 'z') {
				out[count++] = c;
			}
		}
		char[] kept = new char[count];
		System.arraycopy(out, 0, kept, 0, count);
		java.util.Arrays.sort(kept);
		return new String(kept);
	}

	// ------------------------------------------------------------------ 4/5 helpers

	/*
	 * THESE RETURN -1 RATHER THAN THROWING, and the difference is the whole point of the
	 * mutation runner. Breaking namesPerson so that every step counts as already naming its
	 * person leaves no step for firstHiddenPerson to find - and a finder that threw there
	 * killed the suite part way through, which the runner reports as "caught by a crash",
	 * which is not a catch. Found below, a missing one fails a check by name instead.
	 */

	static int firstSpot(int spot, boolean wantNone) {
		for (int i = 0; i < ClueSteps.count(); i++) {
			if (ClueSteps.SPOT[i] == spot && (wantNone || ClueSteps.GUARD[i].length() == 0)
				&& ClueSteps.ANSWER[i].length() == 0 && CluePlugin.namesPerson(i)) {
				return i;
			}
		}
		return -1;
	}

	static int firstAnswer() {
		for (int i = 0; i < ClueSteps.count(); i++) {
			if (ClueSteps.ANSWER[i].length() > 0) {
				return i;
			}
		}
		return -1;
	}

	static int firstHiddenPerson() {
		for (int i = 0; i < ClueSteps.count(); i++) {
			if (!CluePlugin.namesPerson(i) && ClueSteps.ANSWER[i].length() == 0) {
				return i;
			}
		}
		return -1;
	}

	static int firstNamedPerson() {
		for (int i = 0; i < ClueSteps.count(); i++) {
			if (ClueSteps.WHO[i].length() > 0 && CluePlugin.namesPerson(i)) {
				return i;
			}
		}
		return -1;
	}

	static int firstGuarded() {
		for (int i = 0; i < ClueSteps.count(); i++) {
			if (ClueSteps.GUARD[i].length() > 0 && ClueSteps.SPOT[i] == ClueSteps.SPOT_DIG) {
				return i;
			}
		}
		return -1;
	}

	static int mapClue() {
		for (int i = 0; i < ClueSteps.count(); i++) {
			if (ClueSteps.TEXT[i].length() == 0) {
				return i;
			}
		}
		return -1;
	}

	// ------------------------------------------------------------------ 6. nothing in the bag

	static void emptyTests() {
		CluePlugin plugin = new CluePlugin();
		// No context, so no inventory: the page shows its message rather than a blank row.
		check(plugin.carrying().length == 0, "with no inventory there is nothing to carry");
		check(CluePlugin.clueSlots(plugin.carrying()).length == 0, "...so there are no clues in it");

		// A BAG WITH THREE SCROLLS IN IT SHOWS THREE ROWS. An earlier page showed only the
		// first, so two thirds of what somebody was carrying was invisible and which third
		// showed depended on how they had shuffled their bag.
		int[] bag = new int[28];
		java.util.Arrays.fill(bag, -1);
		bag[0] = 995;
		bag[2] = ClueSteps.ID[0];
		bag[5] = ClueSteps.ID[ClueSteps.count() / 2];
		bag[9] = ClueSteps.ID[ClueSteps.count() - 1];
		bag[11] = 1038;
		int[] rows = CluePlugin.clueSlots(bag);
		check(rows.length == 3, "three scrolls in a bag of junk are three rows (" + rows.length + ")");
		check(rows.length == 3 && rows[0] == 2 && rows[1] == 5 && rows[2] == 9,
			"...and they come back in slot order, which is the order they are drawn");
		check(CluePlugin.clueSlots(new int[0]).length == 0, "an empty bag is no rows");

		// AND A ROW INDEX IS NOT TRUSTED, because the panel's snapshot and the bag are a frame
		// apart: the scroll in row 2 can be gone by the time its label is asked for, and a page
		// that throws there takes the whole sidebar down with it.
		check(CluePlugin.stepAt(rows, bag, 0) == ClueSteps.indexOf(ClueSteps.ID[0]),
			"the first row is the first scroll in the bag");
		check(CluePlugin.stepAt(rows, bag, 2) == ClueSteps.count() - 1,
			"...and the last row is the last one");
		check(safeStepAt(rows, bag, 3) == -1 && safeStepAt(rows, bag, 99) == -1,
			"a row past the end is no step rather than a thrown page");
		check(safeStepAt(rows, bag, -1) == -1, "...and so is one before the start");
		check(safeStepAt(rows, new int[0], 0) == -1,
			"...and so is a row whose bag emptied under it");
		check(safeStep(plugin, 0) == -1, "...and no step in the first row");
		check(safeStep(plugin, -1) == -1 && safeStep(plugin, 99) == -1, "...nor off either end of it");
		check("".equals(plugin.detail(0)), "...and the row says nothing rather than throwing");
	}

	/**
	 * stepOf, with a thrown row counted as a wrong answer rather than as the end of the suite.
	 *
	 * A ROW INDEX IS NOT TRUSTED, because the panel's snapshot and the inventory are a frame
	 * apart: the bag can empty between size() and label(2). An unguarded stepOf would throw
	 * there, and a suite that dies on the throw reports a crash instead of naming the check -
	 * which the mutation runner counts as no catch at all.
	 */
	static int safeStep(CluePlugin plugin, int row) {
		try {
			return plugin.stepOf(row);
		} catch (Throwable threw) {
			return -2;
		}
	}

	/** The same for the static behind it, so a thrown row fails a check instead of the suite. */
	static int safeStepAt(int[] slots, int[] objIds, int row) {
		try {
			return CluePlugin.stepAt(slots, objIds, row);
		} catch (Throwable threw) {
			return -2;
		}
	}

	// ------------------------------------------------------------------ 7. the generator

	static void generatedTests() {
		String root = System.getProperty("dp.root", ".");
		String named = System.getProperty("dp.content", "");
		java.io.File content = named.length() > 0 ? new java.io.File(named)
			: new java.io.File(new java.io.File(root).getParentFile(), "DeathPlateau-Content");
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
