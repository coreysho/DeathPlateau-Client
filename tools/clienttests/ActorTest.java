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
		check(plugin.indexOf("MAX_DRAWN") >= 0 && plugin.indexOf("drawn < MAX_DRAWN") >= 0,
			"Npc indicators caps how many it draws in a frame");
		check(plugin.indexOf("apiLevel = 5") >= 0, "...and declares the level it needs");
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
