/*
 * Headless test for API level 4: who else is in the scene.
 *
 * WHAT THIS GUARDS. getNpcs and getPlayers are the first API that hands a plugin something about
 * somebody else, and the whole design rests on them handing over VALUES rather than a handle. The
 * client's npc config is a 20-entry round-robin cache that recycles under you once more than 20
 * types are on screen, so a plugin holding a live reference would read a thing that had quietly
 * become a different npc; and a handle on an entity is one field away from a plugin that can act
 * on it, which is the line the API is drawn on - a plugin draws and reads, the client owns input
 * and the socket.
 *
 * So the rules here are pure functions and are tested as such - the derived tile, the centre of a
 * big npc, the name matching, the combat-level rule, the ordering - and the structural promises
 * that no pure function can express are read out of the source, the way JaggrabTest reads
 * openUrl's branches.
 */
package jagex2.client.plugin.builtin;

import jagex2.client.plugin.Actor;
import jagex2.client.plugin.PluginApi;

public class ActorTest {

	static int fails;

	static void check(boolean ok, String what) {
		System.out.println((ok ? "  ok   " : "FAIL   ") + what);
		if (!ok) {
			fails++;
		}
	}

	public static void main(String[] args) {
		System.out.println("1. an actor's own arithmetic");
		actorTests();
		System.out.println();
		System.out.println("2. which npcs the player asked for");
		matchTests();
		System.out.println();
		System.out.println();
		System.out.println("2b. a colour per term, and the exact terms tagging works on");
		termTests();
		System.out.println();
		System.out.println("3. what the tag says");
		labelTests();
		System.out.println();
		System.out.println("4. nearest first");
		orderTests();
		System.out.println();
		System.out.println("5. the level, and the promises the source has to keep");
		sourceTests();

		System.out.println();
		System.out.println(fails == 0 ? "ALL PASS" : fails + " FAILED");
		System.exit(fails == 0 ? 0 : 1);
	}

	// ---------------------------------------------------------------- 1

	static Actor npc(String name, int level, int id, int sceneX, int sceneZ, int size) {
		return new Actor(name, level, id, sceneX, sceneZ, size, false);
	}

	static void actorTests() {
		// The tile is DERIVED, never passed, so it cannot disagree with the fine position. 128 to
		// a tile: the middle of tile 5 is 5*128+64 = 704.
		Actor a = npc("Goblin", 2, 101, 704, 1216, 1);
		check(a.sceneTileX == 5, "the tile comes from the fine x (704 -> 5)");
		check(a.sceneTileZ == 9, "and from the fine z (1216 -> 9)");
		check(a.sceneX == 704 && a.sceneZ == 1216, "the fine position is kept as given");

		// Mid-step, which is the whole reason the fine position exists: a name tag drawn from the
		// tile would snap, one drawn from the fine position follows.
		Actor walking = npc("Goblin", 2, 101, 704 + 40, 1216, 1);
		check(walking.sceneTileX == 5, "an npc mid-step is still on the tile it is crossing");
		check(walking.sceneX != a.sceneX, "...but its fine x has moved, so a tag can follow it");

		// A size-1 npc stands in the middle of its tile, so the centre is where it is.
		check(a.centreX() == 704 && a.centreZ() == 1216,
			"a size-1 npc's centre is its own position");

		// A bigger one is anchored at its south-west tile. Centre is half a tile per extra tile:
		// a 3x3 is one whole tile (128) further along each axis.
		Actor boss = npc("Boss", 200, 500, 704, 1216, 3);
		check(boss.centreX() == 704 + 128 && boss.centreZ() == 1216 + 128,
			"a 3x3 npc's centre is a tile further on, not its anchor corner");
		Actor two = npc("Big", 50, 400, 704, 1216, 2);
		check(two.centreX() == 704 + 64, "and a 2x2's is half a tile");

		// Defences, because this is handed straight to projection arithmetic.
		// "?".equals(...) and not the other way round: with the fallback removed, name is null
		// and null.equals would throw out of the suite - which the audit counts as a crash, not
		// a catch, because a crash does not say which rule broke.
		check("?".equals(new Actor(null, 0, -1, 0, 0, 1, false).name),
			"a missing name reads ? rather than crashing a plugin that matches on it");
		check(npc("X", 0, 1, 0, 0, 0).size == 1, "a size of 0 is treated as 1");
		check(npc("X", 0, 1, 0, 0, -5).size == 1, "...and so is a negative one");

		check(a.isNpc(), "an npc id makes it an npc");
		Actor person = new Actor("Zezima", 126, -1, 704, 1216, 1, false);
		check(!person.isNpc(), "a player's -1 id makes it not one");
		check(!person.self, "...and another player is not you");
		check(new Actor("Me", 3, -1, 0, 0, 1, true).self, "but you are");
	}

	// ---------------------------------------------------------------- 2

	static void matchTests() {
		check(NpcIndicatorsPlugin.matches("Goblin", "goblin"), "case does not matter");
		check(NpcIndicatorsPlugin.matches("Goblin Guard", "goblin"),
			"part of a name is enough, so you need not know which one the config uses");
		check(NpcIndicatorsPlugin.matches("Goblin", "cow, goblin, chicken"),
			"any term in the list matches");
		check(NpcIndicatorsPlugin.matches("Cow", " cow "), "whitespace round a term is forgiven");
		check(NpcIndicatorsPlugin.matches("Cow", "cow,,,"), "and empty terms are skipped");
		check(NpcIndicatorsPlugin.matches("Cow", ",,cow"), "...wherever they are in the list");

		check(!NpcIndicatorsPlugin.matches("Goblin", "cow"), "a name that is not listed is not marked");
		// THE IMPORTANT ONE. The plugin ships enabled with no names: a blank list that meant
		// "everything" would outline the whole scene the first time anyone turned it on.
		check(!NpcIndicatorsPlugin.matches("Goblin", ""), "an empty list marks nothing");
		check(!NpcIndicatorsPlugin.matches("Goblin", "   "), "and neither does a blank one");
		check(!NpcIndicatorsPlugin.matches("Goblin", ",,,"), "nor one that is only commas");

		// Nothing here may throw: it runs per npc, per frame, on the game thread.
		boolean threw = false;
		try {
			NpcIndicatorsPlugin.matches(null, "cow");
			NpcIndicatorsPlugin.matches("Cow", null);
			NpcIndicatorsPlugin.matches(null, null);
		} catch (Throwable broke) {
			threw = true;
		}
		check(!threw, "a null name or list does not throw out of a render");
		check(!NpcIndicatorsPlugin.matches(null, "cow") && !NpcIndicatorsPlugin.matches("Cow", null),
			"...and answers no");
	}

	// ---------------------------------------------------------------- 2b

	/**
	 * A term's own colour, and the exact-term arithmetic the Tag row is built on.
	 *
	 * TWO KINDS OF MATCHING LIVE SIDE BY SIDE HERE and the difference is the whole point.
	 * Marking is by SUBSTRING, so "goblin" finds a Goblin Guard. Tagging is by EXACT TERM, so
	 * untagging "Goblin" cannot take "Goblin Guard" with it. Mixing them up gives a Tag row that
	 * offers to untag something it never added, and an Untag that quietly removes a second rule.
	 */
	static void termTests() {
		int fallback = 0x00FF00;

		// ---- a colour per term
		check(NpcIndicatorsPlugin.termColour("Goblin", "goblin", fallback) == fallback,
			"a term with no colour of its own uses the plugin's");
		check(NpcIndicatorsPlugin.termColour("Goblin", "goblin=FF0000", fallback) == 0xFF0000,
			"a term with one uses that instead");
		check(NpcIndicatorsPlugin.termColour("Goblin Guard", "goblin=FF0000", fallback) == 0xFF0000,
			"...and it still matches by substring");
		check(NpcIndicatorsPlugin.termColour("Cow", "goblin=FF0000, cow=0000FF", fallback)
				== 0x0000FF,
			"each term carries its own");
		check(NpcIndicatorsPlugin.termColour("Cow", "goblin=FF0000, cow", fallback) == fallback,
			"...and a term without one in the same list still falls back");
		check(NpcIndicatorsPlugin.termColour("Shark", "goblin=FF0000", fallback) == 0,
			"a name no term matches gets 0, which is how matches() reads 'no'");

		// THE FIRST MATCHING TERM WINS, which is the order they are written in. Worth stating
		// because the alternative - longest match, or last wins - is just as defensible, and a
		// player ordering their list has to know which it is.
		check(NpcIndicatorsPlugin.termColour("Goblin Guard", "goblin=FF0000, goblin guard=0000FF",
				fallback) == 0xFF0000,
			"the first matching term wins, so a narrower rule goes above a broader one");
		check(NpcIndicatorsPlugin.termColour("Goblin Guard", "goblin guard=0000FF, goblin=FF0000",
				fallback) == 0x0000FF,
			"...and written that way round, it gets its own colour");

		// A colour that is not six hex characters is no colour rather than a thrown exception or
		// a plausible wrong answer: this runs per npc, per frame.
		check(NpcIndicatorsPlugin.termColour("Cow", "cow=ZZZZZZ", fallback) == fallback,
			"a term with nonsense where its colour goes falls back");
		check(NpcIndicatorsPlugin.termColour("Cow", "cow=FFF", fallback) == fallback,
			"...and so does a short one");
		check(NpcIndicatorsPlugin.termColour("Cow", "cow=-00FF0", fallback) == fallback,
			"...and one with a sign in it, which parseInt would otherwise accept");
		check(NpcIndicatorsPlugin.termColour("Cow", "cow=", fallback) == fallback,
			"...and an empty one");
		// BLACK IS READ AS NO COLOUR, because 0 is already the answer for "no match": a term
		// written =000000 would be an npc that silently stops being marked.
		check(NpcIndicatorsPlugin.termColour("Cow", "cow=000000", fallback) == fallback,
			"a term coloured black falls back, rather than being drawn in 'not drawn'");
		check(NpcIndicatorsPlugin.matches("Cow", "cow=000000"),
			"...and still matches, so the npc is marked rather than vanishing");

		check(NpcIndicatorsPlugin.termName("Goblin=FF0000").equals("Goblin"),
			"a term's name is what comes before its colour");
		check(NpcIndicatorsPlugin.termName(" Goblin ").equals("Goblin"), "...trimmed");
		check(NpcIndicatorsPlugin.termName("Goblin = FF0000 ").equals("Goblin"),
			"...with spaces round the equals forgiven");
		check(NpcIndicatorsPlugin.termName(null).length() == 0, "...and a null term has no name");

		// ---- exact terms, which is what tagging works on
		check(NpcIndicatorsPlugin.hasTerm("goblin, cow", "Goblin"),
			"a term is found whatever its case");
		// ---- A TERM'S OWN COLOUR, AND WHAT IS NOT ONE.
		//
		// Each of these three is a guard whose absence either throws inside a per-npc, per-frame
		// loop or invents a colour nobody wrote. The two that throw are wrapped: an exception
		// ends the run with no failure named, which the mutation runner reports as a crash, and
		// a crash is not a catch.
		check(NpcIndicatorsPlugin.termOwnColour("goblin=FF0000") == 0xFF0000,
			"a term's own colour is read off it");
		check(NpcIndicatorsPlugin.termOwnColour("goblin") == 0,
			"a term with no equals has no colour of its own");
		// "decade" IS SIX HEX DIGITS. Without the equals guard the whole term is read as the
		// colour, and a name that happens to spell hex becomes 0xDECADE - which is why this is
		// checked with a word rather than with "goblin", where the length guard hides it.
		check(NpcIndicatorsPlugin.termOwnColour("decade") == 0,
			"...not even when the name itself spells six hex digits ("
				+ Integer.toHexString(NpcIndicatorsPlugin.termOwnColour("decade")) + ")");
		int shortColour;
		try {
			shortColour = NpcIndicatorsPlugin.termOwnColour("goblin=FFF");
		} catch (RuntimeException threwOnShort) {
			shortColour = -1;
		}
		check(shortColour == 0,
			"a colour that is not six digits is no colour, rather than six digits read off the "
				+ "end of a shorter string");
		int notHex;
		try {
			notHex = NpcIndicatorsPlugin.termOwnColour("goblin=zzzzzz");
		} catch (RuntimeException threwOnLetters) {
			notHex = -1;
		}
		check(notHex == 0, "...and six characters that are not digits are no colour either");
		check(NpcIndicatorsPlugin.termOwnColour("goblin=000000") == 0,
			"and a hand-written black is read as no colour, because a marker drawn in the "
				+ "colour that means not-drawn is an npc that silently stops being marked");

		check(NpcIndicatorsPlugin.hasTerm("goblin=FF0000", "Goblin"),
			"...and a coloured term is still that term");
		check(!NpcIndicatorsPlugin.hasTerm("goblin", "Goblin Guard"),
			"BUT NOT BY SUBSTRING: Goblin being tagged does not make Goblin Guard tagged, or the "
				+ "Tag row would offer to untag something it never added");
		check(!NpcIndicatorsPlugin.hasTerm("", "Goblin") && !NpcIndicatorsPlugin.hasTerm(null, "Goblin"),
			"an empty or missing list holds nothing");
		check(!NpcIndicatorsPlugin.hasTerm("goblin", ""), "and no name is not a term");
		// ...INCLUDING WHERE THE LIST HAS A BLANK TERM OF ITS OWN. Against "goblin" an empty
		// name matches nothing anyway, so the check above passed with the length guard deleted.
		// A trailing comma puts a blank term in the list, and then an empty name matches it -
		// which makes every unnamed npc read as tagged.
		check(!NpcIndicatorsPlugin.hasTerm("goblin,", ""),
			"...even where a trailing comma has left a blank term in the list");
		check(!NpcIndicatorsPlugin.hasTerm("", ""), "...and not in an empty list either");
		// A TERM AFTER A COMMA HAS A SPACE IN FRONT OF IT, which is how anybody writes a list.
		// Every check above asks about the first term, where there is nothing to trim.
		check(NpcIndicatorsPlugin.hasTerm("goblin, cow", "cow"),
			"a term written after a comma and a space is still that term");
		check(NpcIndicatorsPlugin.hasTerm("goblin ,  cow  ", "Cow"),
			"...however much space is around it");

		// ---- adding
		check(NpcIndicatorsPlugin.addTerm("", "Goblin").equals("Goblin"),
			"the first tag is the whole list");
		check(NpcIndicatorsPlugin.addTerm("Goblin", "Cow").equals("Goblin, Cow"),
			"the next goes on the end, comma and space, the way a player would type it");
		check(NpcIndicatorsPlugin.addTerm("Goblin", "goblin").equals("Goblin"),
			"tagging something already tagged changes nothing, rather than listing it twice");
		check(NpcIndicatorsPlugin.addTerm(null, "Goblin").equals("Goblin"),
			"a missing list is an empty one");
		check(NpcIndicatorsPlugin.addTerm("Goblin=FF0000", "Cow").equals("Goblin=FF0000, Cow"),
			"and an existing term keeps its colour");
		// A NAME THAT CANNOT BE STORED IS DECLINED. A comma would be read back as two terms and
		// an equals as a colour; mangling the list silently is worse than not adding.
		check(NpcIndicatorsPlugin.addTerm("Goblin", "Cow, Sheep").equals("Goblin"),
			"a name with a comma in it is declined rather than mangled into two terms");
		check(NpcIndicatorsPlugin.addTerm("Goblin", "Cow=FF0000").equals("Goblin"),
			"...and one with an equals, which would be read as a colour");
		check(NpcIndicatorsPlugin.addTerm("Goblin", "  ").equals("Goblin"), "...and a blank name");
		check(NpcIndicatorsPlugin.addTerm("Goblin", null).equals("Goblin"), "...and none");

		// ---- removing
		check(NpcIndicatorsPlugin.removeTerm("Goblin, Cow", "Goblin").equals("Cow"),
			"untagging the first leaves the rest with no leading comma");
		check(NpcIndicatorsPlugin.removeTerm("Goblin, Cow", "Cow").equals("Goblin"),
			"...and the last with no trailing one");
		check(NpcIndicatorsPlugin.removeTerm("Goblin, Cow, Sheep", "Cow").equals("Goblin, Sheep"),
			"...and one in the middle joins the two either side");
		check(NpcIndicatorsPlugin.removeTerm("Goblin", "Goblin").length() == 0,
			"untagging the only term leaves an empty list, not a stray comma");
		check(NpcIndicatorsPlugin.removeTerm("Goblin", "Cow").equals("Goblin"),
			"untagging something not there changes nothing");
		check(NpcIndicatorsPlugin.removeTerm("Goblin=FF0000, Cow=0000FF", "Goblin")
				.equals("Cow=0000FF"),
			"THE COLOURS TRAVEL WITH THEIR TERMS: left behind, untagging one rule would silently "
				+ "recolour another");
		check(NpcIndicatorsPlugin.removeTerm("goblin, cow", "GOBLIN").equals("cow"),
			"case does not matter when untagging");
		check(!NpcIndicatorsPlugin.hasTerm(
				NpcIndicatorsPlugin.removeTerm("Goblin, Goblin Guard", "Goblin"), "Goblin"),
			"untagging Goblin removes Goblin");
		check(NpcIndicatorsPlugin.hasTerm(
				NpcIndicatorsPlugin.removeTerm("Goblin, Goblin Guard", "Goblin"), "Goblin Guard"),
			"...and leaves Goblin Guard, which a substring rule would have taken too");
		check(NpcIndicatorsPlugin.removeTerm("Goblin,,  , Cow", "Cow").equals("Goblin"),
			"and the blank terms a hand-edited list may hold are tidied away with it");

		// ---- the round trip, which is what a player actually does: tag, untag, tag again.
		String list = NpcIndicatorsPlugin.addTerm("", "Goblin");
		list = NpcIndicatorsPlugin.addTerm(list, "Cow");
		check(NpcIndicatorsPlugin.matches("Goblin", list)
				&& NpcIndicatorsPlugin.matches("Cow", list),
			"two tags both mark");
		list = NpcIndicatorsPlugin.removeTerm(list, "Goblin");
		check(!NpcIndicatorsPlugin.matches("Goblin", list)
				&& NpcIndicatorsPlugin.matches("Cow", list),
			"untagging one leaves the other marking");
		list = NpcIndicatorsPlugin.addTerm(list, "Goblin");
		check(NpcIndicatorsPlugin.matches("Goblin", list)
				&& NpcIndicatorsPlugin.matches("Cow", list),
			"and tagging it again brings it back");

		// ---- the npc name out of a menu row
		check(NpcIndicatorsPlugin.menuNpcName("Attack @yel@Goblin").equals("Goblin"),
			"an npc row gives up its name");
		check(NpcIndicatorsPlugin.menuNpcName("Attack @yel@Guard@gr2@ (level-21)").equals("Guard"),
			"...without the combat level, so a term matches it the way it matches a tag");
		check(NpcIndicatorsPlugin.menuNpcName("Take @lre@Bones").length() == 0,
			"AN ITEM ROW IS NOT AN NPC ROW. The kind tag is the only thing that tells them apart, "
				+ "and without the check a term like 'bones' would colour ground item rows");
		check(NpcIndicatorsPlugin.menuNpcName("Chop down @cya@Tree").length() == 0,
			"...nor is scenery");
		check(NpcIndicatorsPlugin.menuNpcName("Trade with @whi@Zezima").length() == 0,
			"...nor a player");
		check(NpcIndicatorsPlugin.menuNpcName("Walk here").length() == 0,
			"...and a row with no target has no name");

		// ---- the clamps
		check(NpcIndicatorsPlugin.everyTile(NpcIndicatorsPlugin.EVERY_TILE),
			"every tile means every tile");
		check(!NpcIndicatorsPlugin.everyTile(NpcIndicatorsPlugin.ANCHOR_TILE),
			"and the anchor means just the one");
		check(NpcIndicatorsPlugin.everyTile("something else")
				&& NpcIndicatorsPlugin.everyTile(null),
			"an unknown value falls back to the default rather than marking nothing");

		check(NpcIndicatorsPlugin.borderFor(1) == 1 && NpcIndicatorsPlugin.borderFor(3) == 3,
			"a thickness in range is kept");
		check(NpcIndicatorsPlugin.borderFor(0) == 1 && NpcIndicatorsPlugin.borderFor(-4) == 1,
			"a zero-pixel border is no outline at all, so one is the floor");
		check(NpcIndicatorsPlugin.borderFor(500) == NpcIndicatorsPlugin.MAX_BORDER,
			"and a very thick one would swallow a distant tile whole");
		check(NpcIndicatorsPlugin.MIN_BORDER == 1 && NpcIndicatorsPlugin.MAX_BORDER == 5,
			"between one pixel and five, as numbers rather than as their own names");

		check(NpcIndicatorsPlugin.maxDrawnFor(32) == 32, "a cap in range is kept");
		check(NpcIndicatorsPlugin.maxDrawnFor(0) == 1,
			"a cap of zero is the feature off by typo, so one is the floor");
		check(NpcIndicatorsPlugin.maxDrawnFor(9999) == NpcIndicatorsPlugin.MAX_MAX_DRAWN,
			"and a crowd is bounded whatever is typed");
		check(NpcIndicatorsPlugin.DEFAULT_MAX_DRAWN == 32 && NpcIndicatorsPlugin.MAX_MAX_DRAWN == 64,
			"thirty-two by default, sixty-four at most");

		// ---- the inset a thick border is drawn as
		check(TileIndicatorsPlugin.toward(10, 20, 3) == 13, "a corner moves toward the centre");
		check(TileIndicatorsPlugin.toward(30, 20, 3) == 27, "...from either side");
		check(TileIndicatorsPlugin.toward(10, 20, 0) == 10, "the first ring does not move at all");
		check(TileIndicatorsPlugin.toward(20, 20, 3) == 20, "a corner already at the centre stays");
		// CLAMPED AT THE CENTRE. A distant tile is a few pixels across, and a three-pixel inset
		// that overshot would draw a ring BIGGER than the one it was meant to sit inside.
		check(TileIndicatorsPlugin.toward(19, 20, 5) == 20,
			"an inset longer than the tile stops at the centre rather than crossing it");
		check(TileIndicatorsPlugin.toward(21, 20, 5) == 20, "...from the other side too");
	}

	// ---------------------------------------------------------------- 3

	static void labelTests() {
		Actor goblin = npc("Goblin", 2, 101, 0, 0, 1);
		check(NpcIndicatorsPlugin.label(goblin, false).equals("Goblin"),
			"with the level off, the tag is just the name");
		check(NpcIndicatorsPlugin.label(goblin, true).equals("Goblin (level-2)"),
			"with it on, the level follows the name");

		// A banker, a shopkeeper and a fishing spot have no combat level. "(level-0)" is worse
		// than nothing, and the client's own menu rows make the same exception.
		Actor banker = npc("Banker", 0, 494, 0, 0, 1);
		check(NpcIndicatorsPlugin.label(banker, true).equals("Banker"),
			"something with no combat level does not get a (level-0)");
		Actor unset = npc("Thing", -1, 1, 0, 0, 1);
		check(NpcIndicatorsPlugin.label(unset, true).equals("Thing"),
			"...nor one whose level never loaded");
	}

	// ---------------------------------------------------------------- 4

	static void orderTests() {
		// Nearest first, so a plugin that draws only the closest few - or stops at a cap, as Npc
		// indicators does - drops the far ones rather than an arbitrary subset.
		Actor near = npc("Near", 1, 1, 704, 704, 1);
		Actor far = npc("Far", 1, 2, 704 + 1280, 704, 1);
		check(Actor.compareByDistance(near, far, 704, 704) < 0, "the nearer one sorts first");
		check(Actor.compareByDistance(far, near, 704, 704) > 0, "...and the farther one last");
		check(Actor.compareByDistance(near, near, 704, 704) == 0, "equal distances tie");

		// Distance is from the CENTRE, so a big npc standing on you is not sorted as if it were
		// a tile away. Its anchor is further off than the small one's; its centre is nearer.
		Actor smallFar = npc("Small", 1, 1, 704 + 200, 704, 1);
		Actor bigOnTop = npc("Big", 1, 2, 704 + 64, 704, 3);
		// The offset applies on BOTH axes, which is the part that is easy to miss: the big npc's
		// anchor is nearer than the small one's tile along x, and it still sorts farther, because
		// its centre has moved diagonally away. dx 192, dz 128 -> 53248, against 200, 0 -> 40000.
		check(bigOnTop.centreX() == 704 + 64 + 128 && bigOnTop.centreZ() == 704 + 128,
			"a 3x3's centre is offset on both axes, not just the one it was moved along");
		check(bigOnTop.sceneX < smallFar.sceneX,
			"the big npc's anchor is the nearer of the two along x");
		check(Actor.compareByDistance(smallFar, bigOnTop, 704, 704) < 0,
			"...yet the small one sorts first, because distance is measured from centres");

		// Diagonals: a tile away on both axes is further than a tile away on one.
		Actor straight = npc("Straight", 1, 1, 704 + 128, 704, 1);
		Actor diagonal = npc("Diagonal", 1, 2, 704 + 128, 704 + 128, 1);
		check(Actor.compareByDistance(straight, diagonal, 704, 704) < 0,
			"a diagonal neighbour is farther than a straight one, so both axes count");

		// The far corners of a scene, which is where an int would have overflowed had the squared
		// distance not been widened.
		Actor corner = npc("Corner", 1, 1, 13311, 13311, 1);
		Actor origin = npc("Origin", 1, 2, 0, 0, 1);
		check(Actor.compareByDistance(origin, corner, 0, 0) < 0,
			"opposite corners of the scene still order correctly");

		// DELIBERATELY BEYOND ANY REAL SCENE. A scene is 13312 fine units across, so nothing a
		// client can produce overflows an int here - which is exactly why the widening to long
		// would otherwise be untested, and an overflow shows up as an ordering quietly in the
		// wrong order rather than as anything that announces itself. These are pure functions,
		// so they can be asked about distances a bigger scene would bring.
		Actor vastNear = npc("VastNear", 1, 1, 1 << 29, 0, 1);
		Actor vastFar = npc("VastFar", 1, 2, 1 << 30, 0, 1);
		check(Actor.distanceSquared(vastNear, 0, 0) > 0,
			"a distance a bigger scene would bring stays positive rather than overflowing");
		check(Actor.distanceSquared(vastNear, 0, 0) < Actor.distanceSquared(vastFar, 0, 0),
			"...and still compares the right way round");
		check(Actor.compareByDistance(vastNear, vastFar, 0, 0) < 0,
			"...so the ordering survives a scene larger than this one");
	}

	// ---------------------------------------------------------------- 5

	static void sourceTests() {
		// The level the scene actors need, not the level the client is at: that pin moves with
		// each addition and belongs to the newest suite, which is where it is now. What this one
		// has to keep true is that a plugin written against 5 still runs here.
		check(PluginApi.LEVEL >= 5, "this client is at least API level 5 (" + PluginApi.LEVEL + ")");
		check(PluginApi.supports(5), "a plugin asking for 5 runs here");
		check(!PluginApi.supports(PluginApi.LEVEL + 1),
			"one asking for a level past this client's does not");
		check(PluginApi.supports(0) && PluginApi.supports(4), "and everything older still does");

		// AN ACTOR CARRIES NO HANDLE. This is the design rule, and no pure function can state it:
		// the moment Actor holds a ClientNpc, a plugin can reach the entity, and everything the
		// entity can do becomes API by accident.
		String actor = read("src/main/java/jagex2/client/plugin/Actor.java");
		check(actor.length() > 0, "Actor is readable");
		// CODE ONLY. Actor's own javadoc explains why it does not hold a ClientNpc, and the first
		// version of this check matched that sentence and failed on a file that was correct.
		String code = stripComments(actor);
		check(code.indexOf("ClientNpc") < 0 && code.indexOf("ClientPlayer") < 0,
			"an Actor holds no reference to a client entity, so there is nothing on it to act on");
		check(code.indexOf("import jagex2.dash3d") < 0, "...and does not import the scene classes");
		check(actor.indexOf("ClientNpc") >= 0,
			"...and the comment that says why is still there (this check reads code, not prose)");

		String ctx = read("src/main/java/jagex2/client/plugin/PluginContext.java");
		int npcs = ctx.indexOf("public List<Actor> getNpcs()");
		int npcsEnd = npcs < 0 ? -1 : ctx.indexOf("\n\t}", npcs);
		String body = npcs < 0 || npcsEnd < 0 ? "" : ctx.substring(npcs, npcsEnd);
		check(body.length() > 0, "getNpcs is readable");
		// npcs[] is 16384 long and mostly stale. npcCount says how much of npcIds[] is live.
		check(body.indexOf("npcIds[") >= 0 && body.indexOf("npcCount") >= 0,
			"getNpcs reads the live npcIds/npcCount pair, not the whole 16384-entry array");
		check(body.indexOf("field1370 == null") >= 0,
			"...and tolerates an npc whose config has not arrived yet");

		int players = ctx.indexOf("public List<Actor> getPlayers()");
		int playersEnd = players < 0 ? -1 : ctx.indexOf("\n\t}", players);
		String pbody = players < 0 || playersEnd < 0 ? "" : ctx.substring(players, playersEnd);
		check(pbody.length() > 0, "getPlayers is readable");
		// The local player is not in playerIds[] - the client keeps it at LOCAL_PLAYER_INDEX.
		check(pbody.indexOf("Client.localPlayer") >= 0 && pbody.indexOf("player(me, true)") >= 0,
			"getPlayers adds the local player, which playerIds does not contain");
		check(pbody.indexOf("other == me") >= 0,
			"...and cannot list you twice if playerIds ever does contain you");
		// A PLAYER'S ID IS -1, which is what isNpc answers on. Built inside player(), so no
		// test that constructs an Actor directly can see it being got wrong.
		int maker = ctx.indexOf("private static Actor player(ClientPlayer p, boolean self)");
		int makerEnd = maker < 0 ? -1 : ctx.indexOf("\n\t}", maker);
		String mbody = maker < 0 || makerEnd < 0 ? "" : ctx.substring(maker, makerEnd);
		check(mbody.length() > 0, "the player-to-Actor step is readable");
		check(mbody.indexOf("-1") >= 0,
			"a player is given the -1 npc id, so isNpc answers no for a person");

		// NEAREST FIRST IS A PROMISE both accessors make, and the cap in Npc indicators relies
		// on it: without the sort, a cap keeps an arbitrary 32 rather than the closest 32.
		check(body.indexOf("sortByDistanceFromPlayer") >= 0,
			"getNpcs sorts before returning, so nearest-first is true of what it hands back");
		check(pbody.indexOf("sortByDistanceFromPlayer") >= 0, "...and so does getPlayers");

		// OUT-OF-SCENE ACTORS ARE DROPPED. The client drops them too rather than drawing them,
		// and projection cannot answer for a position the scene does not cover - a kept one
		// becomes a label pinned in the corner of the screen.
		int scene = ctx.indexOf("private static boolean inScene(int sceneX, int sceneZ)");
		int sceneEnd = scene < 0 ? -1 : ctx.indexOf("\n\t}", scene);
		String sbody = scene < 0 || sceneEnd < 0 ? "" : ctx.substring(scene, sceneEnd);
		check(sbody.length() > 0, "the scene-bounds test is readable");
		check(sbody.indexOf("104") >= 0 && sbody.indexOf(">= 0") >= 0,
			"an actor outside the 104x104 scene is dropped rather than returned");
		check(body.indexOf("inScene(") >= 0 && pbody.indexOf("inScene(") >= 0,
			"...and both accessors apply it");

		// The cap, which is what stops a one-letter name costing a frame.
		String plugin = read("src/main/java/jagex2/client/plugin/builtin/NpcIndicatorsPlugin.java");
		check(plugin.indexOf("drawn < cap") >= 0
				&& plugin.indexOf("int cap = maxDrawnFor(this.maxDrawn)") >= 0,
			"Npc indicators caps how many it draws in a frame, at the number a player set");
		check(plugin.indexOf("apiLevel = 6") >= 0,
			"...and declares the level it needs, which is 6 now that it colours menu rows");
		// One copy of the tile-corner walk, shared, not two.
		check(plugin.indexOf("TileIndicatorsPlugin.outlineTile") >= 0,
			"...and reuses the tile outline rather than keeping a second copy of it");
	}

	/**
	 * Java source with its comments taken out, so a structural check cannot be satisfied - or
	 * broken - by prose. Crude on purpose: it does not know about a "//" inside a string literal,
	 * which no file this is pointed at contains.
	 */
	static String stripComments(String java) {
		StringBuilder out = new StringBuilder(java.length());
		int at = 0;
		while (at < java.length()) {
			if (java.startsWith("/*", at)) {
				int close = java.indexOf("*/", at + 2);
				at = close < 0 ? java.length() : close + 2;
			} else if (java.startsWith("//", at)) {
				int eol = java.indexOf('\n', at);
				at = eol < 0 ? java.length() : eol;
			} else {
				out.append(java.charAt(at));
				at++;
			}
		}
		return out.toString();
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
}
