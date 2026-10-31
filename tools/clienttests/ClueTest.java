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
		System.out.println("7. the packet that logged you out");
		transmitTests();
		System.out.println();
		System.out.println("8. the puzzle box");
		puzzleTests();
		System.out.println();
		System.out.println("9. the table against the content it came from");
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

		// WHICH SCROLL GETS THE RING ON THE WORLD MAP. The map can point at one place and the
		// page lists them all, so the one marked is the first in the bag that HAS a place - a
		// challenge scroll in the top slot must not take the ring away from the dig spot below
		// it, and a bag of steps that are all people gets no ring at all.
		int placed = firstSpot(ClueSteps.SPOT_DIG, false);
		int answered = firstAnswer();
		int[] bothKinds = new int[28];
		java.util.Arrays.fill(bothKinds, -1);
		bothKinds[0] = ClueSteps.ID[answered];
		bothKinds[3] = ClueSteps.ID[placed];
		check(CluePlugin.markable(bothKinds) == placed,
			"the ring goes on the first scroll with a place, not the first scroll");
		int[] noPlace = new int[28];
		java.util.Arrays.fill(noPlace, -1);
		noPlace[0] = ClueSteps.ID[answered];
		check(CluePlugin.markable(noPlace) < 0, "...and a bag with no place in it gets no ring");
		check(CluePlugin.markable(new int[0]) < 0, "...nor does an empty one");

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

	// ------------------------------------------------------------------ 7. the transmit

	/*
	 * FOUND BY WAY OF THE PUZZLE BOX, which is why it is tested here.
	 *
	 * trail_puzzle.rs2 transmitted the puzzle's 25 pieces to trail_puzzle:com_3 - the Hint
	 * button, a model - instead of com_2, the 5x5 grid. Component.decode gives invSlotObjId
	 * only to types 2 and 7, so on a model it is null, and all three UPDATE_INV packets wrote
	 * into it without asking. The write threw inside readPacket; the catch at the bottom of
	 * readPacket calls logout(). Opening a puzzle box threw the player out of the game.
	 *
	 * The content is fixed and sweeps itself now, but the client should not be one typo away
	 * from that, so the packets ask holdsInv first. This checks the asking; the three call
	 * sites are a read, because readPacket needs a live socket to drive.
	 */

	static void transmitTests() {
		jagex2.config.Component grid = new jagex2.config.Component();
		grid.type = 2;
		grid.invSlotObjId = new int[25];
		grid.invSlotObjCount = new int[25];
		check(jagex2.config.Component.holdsInv(grid), "a 5x5 inv component can be transmitted to");

		jagex2.config.Component hint = new jagex2.config.Component();
		hint.type = 6;
		check(!jagex2.config.Component.holdsInv(hint),
			"a model component cannot, which is the one the puzzle box was aimed at");

		// BOTH ARRAYS, because the packets write both and either one missing is the same throw.
		jagex2.config.Component half = new jagex2.config.Component();
		half.type = 2;
		half.invSlotObjId = new int[25];
		check(!jagex2.config.Component.holdsInv(half), "and nor can one with only half the pair");

		check(!jagex2.config.Component.holdsInv(null),
			"...nor a component id the cache has never heard of");
	}

	// ------------------------------------------------------------------ 8. the puzzle box

	/*
	 * SOLVING IS NOT CHECKED AGAINST AN EXPECTED ANSWER, because there is no expected answer: a
	 * row-at-a-time solve is one valid route of billions, and a test naming one would break on
	 * every improvement to the solver while catching nothing. What is checked is the property
	 * that matters - the clicks it hands back, played back onto the board it was given, leave
	 * it solved - over thousands of boards shuffled the way the game shuffles them.
	 *
	 * AND THE PLAYBACK IS STRICT. SlidingPuzzle.after refuses a click on a cell that is not
	 * next to the gap, which is the only move the game allows, so a solution containing one
	 * fails here rather than reading as a pass and misleading a player at a puzzle box.
	 */

	static void puzzleTests() {
		int[] solved = new int[SlidingPuzzle.CELLS];
		for (int cell = 0; cell < SlidingPuzzle.CELLS - 1; cell++) {
			solved[cell] = cell;
		}
		solved[SlidingPuzzle.CELLS - 1] = SlidingPuzzle.GAP;

		check(SlidingPuzzle.isBoard(solved), "a full board with one gap is a board");
		check(SlidingPuzzle.isSolved(solved), "...and that one is solved");
		// ONE MOVE FROM SOLVED, which is the board that catches a check that stops a cell
		// early: every piece but the last is home, and the last has swapped with the gap.
		int[] nearly = solved.clone();
		nearly[SlidingPuzzle.CELLS - 2] = SlidingPuzzle.GAP;
		nearly[SlidingPuzzle.CELLS - 1] = SlidingPuzzle.CELLS - 2;
		check(SlidingPuzzle.isBoard(nearly), "a board one move from solved is still a board");
		check(!SlidingPuzzle.isSolved(nearly), "...and is not solved, last piece and all");
		check(!SlidingPuzzle.isBoard(new int[] { 1, 2, 3 }), "a short array is not a board");
		int[] twoGaps = solved.clone();
		twoGaps[0] = SlidingPuzzle.GAP;
		check(!SlidingPuzzle.isBoard(twoGaps), "nor is one with two gaps");
		int[] twice = solved.clone();
		twice[0] = 1;
		check(!SlidingPuzzle.isBoard(twice), "nor one with the same piece in it twice");
		int[] noGap = solved.clone();
		noGap[SlidingPuzzle.CELLS - 1] = 0;
		check(!SlidingPuzzle.isBoard(noGap), "nor one with no gap at all");

		// ONE SWAP IS THE UNSOLVABLE BOARD. Half of all arrangements cannot be reached from
		// solved, and swapping two pieces is how you land in the other half.
		int[] swapped = solved.clone();
		int keep = swapped[0];
		swapped[0] = swapped[1];
		swapped[1] = keep;
		check(SlidingPuzzle.isSolvable(solved), "the solved board is solvable");
		check(!SlidingPuzzle.isSolvable(swapped), "...and two pieces swapped is not");
		check(SlidingPuzzle.solve(swapped) == null, "so solving it says no rather than guessing");
		check(SlidingPuzzle.solve(solved) != null && SlidingPuzzle.solve(solved).length == 0,
			"and a board already solved needs no clicks, which is not the same as no answer");

		// A CLICK IS A SLIDE, and the game has no other move: anything else must not play back.
		check(SlidingPuzzle.after(solved, new int[] { 19 }) != null,
			"the cell above the gap can be slid into it");
		check(SlidingPuzzle.after(solved, new int[] { 0 }) == null,
			"...and a cell nowhere near it cannot");
		check(SlidingPuzzle.after(solved, new int[] { 99 }) == null, "...nor a cell off the board");
		check(SlidingPuzzle.after(solved, new int[] { SlidingPuzzle.CELLS - 1 }) == null,
			"...nor the gap itself, which is not a piece to click");

		// THE PROPERTY, over boards shuffled the way ~shuffle_trail_puzzle shuffles them: walk
		// the gap at random from the solved board, which is the only way the game makes one.
		java.util.Random rng = new java.util.Random(20261030);
		int runs = 2000;
		int failed = 0;
		int wrong = 0;
		int illegal = 0;
		int longest = 0;
		long total = 0;
		for (int run = 0; run < runs; run++) {
			int[] board = shuffle(solved, rng, 101 + rng.nextInt(400));
			int[] clicks = SlidingPuzzle.solve(board);
			if (clicks == null) {
				failed++;
				continue;
			}
			int[] end = SlidingPuzzle.after(board, clicks);
			if (end == null) {
				illegal++;
			} else if (!SlidingPuzzle.isSolved(end)) {
				wrong++;
			}
			total += clicks.length;
			if (clicks.length > longest) {
				longest = clicks.length;
			}
		}
		check(failed == 0, "every one of " + runs + " shuffled boards got an answer (" + failed + " did not)");
		check(illegal == 0, "...made only of moves the game would allow (" + illegal + " were not)");
		check(wrong == 0, "...and every one of those answers leaves the board solved (" + wrong + " did not)");
		check(longest < SlidingPuzzle.LIMIT,
			"and none of them came near the ceiling (" + longest + " of " + SlidingPuzzle.LIMIT + ")");
		check(total / runs < 250, "the average solve is a couple of hundred clicks (" + total / runs + ")");

		// THE TABLE THE BOARD IS READ THROUGH. An obj the content does not call a puzzle piece
		// must not be silently given a cell: a board with a wrong piece in it solves to clicks
		// that move the wrong things.
		check(PuzzlePieces.count() == 24, "the content's 24 pieces are in the table");
		int outOfOrder = 0;
		int wrongHome = 0;
		boolean[] homes = new boolean[24];
		for (int i = 0; i < PuzzlePieces.count(); i++) {
			if (i > 0 && PuzzlePieces.ID[i] <= PuzzlePieces.ID[i - 1]) {
				outOfOrder++;
			}
			if (PuzzlePieces.homeOf(PuzzlePieces.ID[i]) != PuzzlePieces.HOME[i]) {
				wrongHome++;
			}
			homes[PuzzlePieces.HOME[i]] = true;
		}
		check(outOfOrder == 0, "...ascending, which is what makes the search work");
		check(wrongHome == 0, "...and each is found at its own cell");
		int missing = 0;
		for (int cell = 0; cell < homes.length; cell++) {
			if (!homes[cell]) {
				missing++;
			}
		}
		check(missing == 0, "every cell of the grid has exactly one piece that belongs in it");
		check(PuzzlePieces.homeOf(995) < 0, "and a coin is not a puzzle piece");

		// READING THE WINDOW. The grid holds obj ids; the solver wants cells.
		int[] slots = new int[SlidingPuzzle.CELLS];
		for (int cell = 0; cell < SlidingPuzzle.CELLS - 1; cell++) {
			slots[cell] = PuzzlePieces.ID[cell];
		}
		slots[SlidingPuzzle.CELLS - 1] = -1;
		int[] read = CluePlugin.read(slots);
		check(read != null && SlidingPuzzle.isBoard(read), "a window full of pieces reads as a board");
		check(read != null && SlidingPuzzle.isSolved(read),
			"...and the pieces in their own order read as solved");
		int[] intruder = slots.clone();
		intruder[0] = 995;
		check(CluePlugin.read(intruder) == null,
			"a window with something that is not a piece in it reads as no board at all");
		int[] gappy = slots.clone();
		gappy[0] = -1;
		check(CluePlugin.read(gappy) == null, "...and so does one with two gaps");
	}

	/** A board shuffled from solved by walking the gap, which is how the content makes one. */
	static int[] shuffle(int[] solved, java.util.Random rng, int steps) {
		int[] board = solved.clone();
		int gap = SlidingPuzzle.CELLS - 1;
		int[] options = new int[4];
		for (int step = 0; step < steps; step++) {
			int n = 0;
			for (int side = 0; side < 4; side++) {
				int next = SlidingPuzzle.neighbour(gap, side);
				if (next >= 0) {
					options[n++] = next;
				}
			}
			int pick = options[rng.nextInt(n)];
			board[gap] = board[pick];
			board[pick] = SlidingPuzzle.GAP;
			gap = pick;
		}
		return board;
	}

	// ------------------------------------------------------------------ 9. the generator

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
