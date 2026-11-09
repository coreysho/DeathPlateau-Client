// Driven by tools/clienttests/run_questhelpertest.py.
//
// THE QUEST THE PLAYER PICKED, AND THE STEP IN FRONT OF THEM. Everything here is about the one
// question the plugin exists to answer - "what do I do next, and where is it" - asked against the
// real step table that ships in the client, a real scene, and the real PluginManager.
//
// WHY THIS SUITE EXISTS. The marker used to point at the FIRST step of the leg the quest varp
// named. A leg nearly always opens with a walk, a walk names no npc, so nothing was highlighted
// until the walk was behind you and the varp had already moved on: pick The Feud, stand in front
// of Ali Morrisane, and Ali lit up only after you had talked to him. That is a bug about what is
// standing in the scene, so it cannot be tested without one.
package jagex2.client.plugin.builtin;

import java.util.ArrayList;
import java.util.List;

import jagex2.client.Client;
import jagex2.client.plugin.ConfigList;
import jagex2.client.plugin.Overlay;
import jagex2.client.plugin.PluginContext;
import jagex2.client.plugin.PluginManager;
import jagex2.config.LocType;
import jagex2.config.NpcType;
import jagex2.dash3d.ClientNpc;
import jagex2.dash3d.ClientPlayer;
import jagex2.dash3d.Sprite;
import jagex2.dash3d.Square;
import jagex2.dash3d.World3D;
import jagex2.graphics.Pix2D;
import jagex2.graphics.Pix3D;
import jagex2.graphics.PixFont;

public class QuestHelperTest {

	static final int W = 512;
	static final int H = 334;

	/** The tile the camera points at, so a marker on it lands in the middle of the buffer. */
	static final int MID_X = 64;
	static final int MID_Z = 64;

	/** The Feud: its progress varp, and the first leg's two steps as the table records them. */
	static final String FEUD = "THE FEUD";
	static final int FEUD_VARP = 334;
	static final int ALI = 1862;
	static final String ALI_NAME = "Ali Morrisane";
	static final int BAZAAR_X = 3304;
	static final int BAZAAR_Z = 3213;

	/** The Restless Ghost, whose progress is a plain varp rather than a varbit. */
	static final String GHOST = "THE RESTLESS GHOST";
	static final int GHOST_VARP = 107;

	/**
	 * In Search of the Myreque, whose fifth leg is five people to talk to and nothing else.
	 *
	 * A leg with no walk in it and several npcs is the only way to ask WHICH of them the plugin
	 * picks: the second one is the second thing to do whether or not the fifth is closer.
	 */
	static final String MYREQUE = "IN SEARCH OF THE MYREQUE";
	static final int MYREQUE_VARP = 387;
	/** A varp no quest in the table reads, used only to choose a multinpc's child. */
	static final int MULTI_SELECTOR = 1900;

	/** Elemental Workshop I's bookcase, the quest's first thing to search, and its name there. */
	static final int BOOKCASE = 3389;
	static final String BOOKCASE_NAME = "Bookcase";
	static final int BOOKCASE_X = 2716;
	static final int BOOKCASE_Z = 3481;
	static final int RADIGAD = 1572;
	static final String RADIGAD_NAME = "Radigad Ponfit";
	static final int IVAN = 1574;
	static final String IVAN_NAME = "Ivan Strom";

	/**
	 * Shilo Village, whose first leg walks to two places 135 tiles apart.
	 *
	 * A scene is 104 tiles, so it cannot hold both: standing at one of them the other is not
	 * somewhere the marker can point, which is the only way to ask whether a tile the scene has
	 * not loaded is passed over or simply drawn at nothing.
	 */
	static final String SHILO = "SHILO VILLAGE";
	static final int SHILO_VARP = 116;
	static final int MOSOL_X = 2883;
	static final int MOSOL_Z = 2952;
	static final int TRUFITUS_X = 2809;
	static final int TRUFITUS_Z = 3087;

	/**
	 * Elemental Workshop I: the whole quest in ONE leg, under eleven headings.
	 *
	 * Its varp only moves when the quest ends, so there is no leg boundary to hang the guide's
	 * wording on - which is why a heading carries the step it is written in front of.
	 */
	static final String ELEMENTAL = "ELEMENTAL WORKSHOP I";
	static final int EARTH_ELEMENTAL = 1023;
	static final int GHOSTSPEAK = 552;
	/** A bucket: one of the three things The Feud's first leg says to bring. */
	static final int BUCKET = 1925;

	/**
	 * Cook's Assistant's second leg: sixteen steps, and three of its items say different things.
	 *
	 * The bucket and the pot are a bank trip - the sim hands itself both before it starts - and
	 * the grain is picked in the wheat field halfway through. So holding a bucket says nothing
	 * about how far anybody has got and holding grain says a great deal, which is the whole of
	 * the rule in one leg.
	 */
	static final String COOK = "COOK'S ASSISTANT";
	static final int COOK_VARP = 29;
	static final int GRAIN = 1947;
	static final int POT = 1931;
	static final int COOK_X = 3172;
	static final int COOK_Z = 3307;

	/**
	 * Animal Magnetism's tenth leg: talk to the tree, which hands over a branch, then back to Ava.
	 *
	 * The receipt case. Nothing in the game says a talk happened - the quest's varp moves once
	 * for the whole leg - except what the talk put in your bag.
	 */
	static final String ANIMAL = "ANIMAL MAGNETISM";
	static final int ANIMAL_VARP = 1700;
	static final int UNDEAD_BRANCH = 10303;

	/**
	 * Animal Magnetism's second leg: eleven walks and talks under ONE progress value.
	 *
	 * The quest counts them in a flag of its own (varp 1701), which is the only thing that moves
	 * while you walk between Alice and her husband - and exactly the shape of the report this
	 * was written for: a leg the quest's own varp cannot see inside.
	 */
	static final int ANIMAL_COUNT = 1701;

	/**
	 * Horror from the Deep's second leg, whose last flag means the leg is over.
	 *
	 * The quest writes its progress varp at the end of a conversation the leg's last step
	 * starts, so between the two the leg is finished and the varp does not know it.
	 */
	static final String HORROR = "HORROR FROM THE DEEP";
	static final int HORROR_VARP = 740;
	static final int HORROR_DONE = 743;

	/**
	 * Lunar Diplomacy's eleventh leg, with four more legs behind it.
	 *
	 * Horror from the Deep has one leg after its finished one, so "the next leg" and "the last
	 * leg" are the same answer there; this one can tell them apart.
	 */
	static final String LUNAR = "LUNAR DIPLOMACY";
	static final int LUNAR_VARP = 1740;
	static final int LUNAR_DONE = 1742;

	/**
	 * Lost City's fifth leg: one step, and it names two items.
	 *
	 * "Use Knife on Dramen branch" is one instruction a player cannot follow holding half of it,
	 * and both halves are in the bag - which is the case a single highlighted slot cannot show.
	 */
	static final String LOST_CITY = "LOST CITY";
	static final int LOST_CITY_VARP = 147;
	static final int KNIFE = 946;
	static final int DRAMEN_BRANCH = 771;

	/**
	 * Shilo Village's first leg ends on "Use Wampum belt on Trufitus": an npc step with an item.
	 *
	 * Standing in front of Trufitus without the belt is the case that separates "mark what the
	 * step wants" from "mark what the step wants AND you are holding" - the npc is matched by id
	 * whether or not the bag has anything in it.
	 */
	static final int TRUFITUS = 740;
	static final int WAMPUM_BELT = 625;

	/** Elemental Workshop I, in the order a player meets it: bookcase, book, key. */
	static final int BATTERED_BOOK = 2886;
	static final int BATTERED_KEY = 2887;
	static final int WORKSHOP_X = 2713;
	static final int WORKSHOP_Z = 3487;

	/** Long enough for the plugin's fifth-of-a-second cache to expire between two scenes. */
	static final long SETTLE = 250L;

	/**
	 * The component the server transmits the inventory to, written down rather than read out of
	 * the client: `3214=inventory:inv` in the content's interface.pack. GameStateTest owns the
	 * check that PluginContext agrees with it - a fixture that followed the code instead would
	 * build its inventory at whatever the code said and could never disagree with it.
	 */
	static final int INVENTORY_COM = 3214;

	/** The tab layer the grid hangs off, and where it hangs: this suite's own fixture. */
	static final int INVENTORY_TAB = 3213;

	/**
	 * The component the server transmits the WORN equipment to: `1688=wornitems:worn` in the
	 * content's interface.pack, written down here for the reason the inventory's number is.
	 */
	static final int WORN_COM = 1688;
	static final int GRID_X = 20;
	static final int GRID_Y = 30;

	static int fails;
	static int passes;

	static Client client;
	static PluginManager manager;
	static PluginManager.Entry entry;
	static RecordingFont font;
	static int[] pixels = new int[W * H];

	static void check(boolean ok, String what) {
		if (ok) {
			passes++;
			System.out.println("  ok   " + what);
		} else {
			fails++;
			System.out.println("FAIL   " + what);
		}
	}

	public static void main(String[] args) throws Exception {
		if (java.awt.GraphicsEnvironment.isHeadless()) {
			System.out.println("  SKIP  no display, so a Client cannot be constructed - run with xvfb-run");
			System.out.println("ALL PASS (skipped)");
			System.exit(0);
		}

		setUp();
		System.out.println("1. the table that ships with the client");
		tableTests();
		System.out.println("2. nothing is tracked until the player picks");
		pickTests();
		System.out.println("3. the step in front of you is the one that is marked");
		markTests();
		System.out.println("4. a tile the scene has not loaded");
		rangeTests();
		System.out.println("5. the leg follows the varp");
		progressTests();
		System.out.println("5b. what you have to bring, and what the quest hands over");
		bringTests();
		System.out.println("6. which of the leg's people is the one to talk to");
		orderTests();
		System.out.println("7. the guide's wording follows the step, inside one leg");
		headingTests();
		System.out.println("8. the bag says how far into a leg the player has got");
		bagTests();
		System.out.println("9. Elemental Workshop I, in the order a player meets it");
		workshopTests();
		System.out.println("9b. what a step handed over is the record that it happened");
		receiptTests();
		System.out.println("9bb. the quest's own flags, which move inside a leg");
		flagTests();
		System.out.println("9c. what the player is wearing");
		wornTests();
		System.out.println("10. the item in the bag, marked on the sidebar");
		slotTests();
		System.out.println("11. an npc that is one id with several faces");
		multiTests();
		System.out.println("12. a loc of the leg, standing in the scene");
		locTests();

		System.out.println();
		System.out.println(fails == 0 ? (passes + " CHECKS, ALL PASS") : (fails + " FAILED of " + (passes + fails)));
		System.exit(fails == 0 ? 0 : 1);
	}

	// ---------------------------------------------------------------- 1

	static void tableTests() {
		List<ConfigList.Row> rows = page();
		check(rows.size() > 100, "the quest list has every quest in it (" + rows.size() + ")");
		check(row(rows, FEUD) != null, "The Feud is one of them");
		ConfigList.Row feud = row(rows, FEUD);
		check(feud != null && "Track".equals(feud.action), "...offered with a Track button");
		check(feud != null && "not started".equals(feud.detail),
			"...and reading as not started, which is what varp " + FEUD_VARP + " says");
	}

	// ---------------------------------------------------------------- 2

	static void pickTests() throws Exception {
		spawn(ALI_NAME, ALI, MID_X, MID_Z);
		// A QUEST ALREADY IN PROGRESS, which is the state the guess used to fire in: with every
		// varp at zero there is nothing to guess and the check below would pass either way.
		client.varps[GHOST_VARP] = 2;
		settle();

		// NOTHING IS GUESSED. Picking the first started-and-unfinished quest in table order meant
		// a player part-way through six quests was handed whichever sorted earliest.
		check(scene().isEmpty() && screen().isEmpty(),
			"with a quest in progress but none picked, nothing is drawn - no marker and no panel");
		client.varps[GHOST_VARP] = 0;

		track(FEUD);
		check(!screen().isEmpty(), "picking The Feud puts the panel on screen");
		List<ConfigList.Row> rows = page();
		check(rows.size() > 0 && FEUD.equals(rows.get(0).label),
			"...and the page turns to that quest, with its name at the top");
		check(rows.size() > 1 && "Change".equals(rows.get(0).action),
			"...with a Change button to go back to the list");
	}

	// ---------------------------------------------------------------- 3

	static void markTests() throws Exception {
		settle();

		// THE BUG, as a check. Ali is the SECOND step of the first leg; the first is the walk to
		// the bazaar, which names nobody. Standing in front of him, he is what there is to do.
		check(said(scene(), ALI_NAME), "standing in front of Ali Morrisane, the marker names him");
		check(painted() > 0, "...and draws a box on his tile");
		check(said(screen(), "Talk to Ali Morrisane"),
			"...while the panel says to talk to him, not to walk to the bazaar he is standing in");

		// And the row the sidebar calls the live one is the same step.
		List<ConfigList.Row> rows = page();
		ConfigList.Row live = null;
		for (ConfigList.Row row : rows) {
			if ("now".equals(row.value)) {
				live = row;
			}
		}
		check(live != null && live.label.indexOf(ALI_NAME) >= 0,
			"...and the sidebar marks that same step as the one to do now");

		// With Ali out of the scene the leg's own first instruction stands, on the tile it names.
		clearNpcs();
		settle();
		check(!said(scene(), ALI_NAME), "with Ali gone from the scene, he is no longer named");
		check(painted() > 0, "...but the walk's own tile is marked, because the scene has it");
		check(said(screen(), "Go to the bazaar"), "...and the panel gives the walk instead");
	}

	// ---------------------------------------------------------------- 4

	static void rangeTests() throws Exception {
		// A WORLD TILE, THROUGH THE REAL CONVERSION. The table records where a step happens in
		// world coordinates; the scene is 104 tiles of wherever you are standing. Walking far
		// enough away has to end the marker rather than pin it to the edge of the screen, which
		// would be a guess at a direction.
		final PluginContext ctx = context();
		check(ctx.sceneToWorldX(ctx.worldToSceneX(BAZAAR_X)) == BAZAAR_X
				&& ctx.sceneToWorldZ(ctx.worldToSceneZ(BAZAAR_Z)) == BAZAAR_Z,
			"a world tile survives the trip into scene coordinates and back");

		int wasX = client.sceneBaseTileX;
		int wasZ = client.sceneBaseTileZ;
		client.sceneBaseTileX = BAZAAR_X + 500;
		client.sceneBaseTileZ = BAZAAR_Z + 500;
		settle();
		check(painted() == 0 && scene().isEmpty(),
			"with the bazaar outside the loaded scene, nothing is drawn in the world");
		check(said(screen(), "Go to the bazaar"),
			"...though the panel still says where to go, which is the part that still helps");

		// JUST off the edge, which is the case that has to be refused rather than survived: the
		// projection indexes the heightmap with whatever it is given, so a tile three west of the
		// scene is an array index of -3 and takes the whole plugin down with it.
		client.sceneBaseTileX = BAZAAR_X + 3;
		client.sceneBaseTileZ = BAZAAR_Z + 3;
		settle();
		check(painted() == 0 && scene().isEmpty(), "a tile just off the edge is not marked either");
		check(said(screen(), "Go to the bazaar"),
			"...and the plugin is still running, rather than switched off by a thrown frame");

		// A LEG WITH ONE TILE ON THE SCENE AND ONE OFF IT. Shilo Village opens by walking to Mosol
		// Rei and then to Trufitus, 135 tiles apart. Standing at Trufitus, the first of those is
		// not a place this scene can point at, so the second is the step to give - and a plugin
		// that took the first tile regardless would say "Go to Mosol Rei" and draw nothing.
		change();
		track(SHILO);
		client.sceneBaseTileX = TRUFITUS_X - MID_X;
		client.sceneBaseTileZ = TRUFITUS_Z - MID_Z;
		settle();
		check(!ctx.isInScene(ctx.worldToSceneX(MOSOL_X), ctx.worldToSceneZ(MOSOL_Z)),
			"standing at Trufitus, Mosol Rei's tile is outside the loaded scene");
		check(said(screen(), "Go to Trufitus"),
			"...so the leg's second walk is the one given, not its first");
		check(painted() > 0, "...and it is marked, because that tile is one the scene has");

		client.sceneBaseTileX = wasX;
		client.sceneBaseTileZ = wasZ;
		settle();
		change();
		track(FEUD);
		settle();
	}

	// ---------------------------------------------------------------- 5

	static void progressTests() throws Exception {
		// A QUEST WHOSE PROGRESS IS A PLAIN VARP. The Feud's is a varbit, and a varbit is a range
		// of bits inside a parent varp described by the cache - which this harness has none of, so
		// it reads zero whatever is written. 78 of the 106 quests use a varp; The Restless Ghost
		// is one, and its first three legs are three different places.
		change();
		track(GHOST);
		settle();
		check(said(screen(), "Talk to Father Aereck"),
			"a fresh quest opens on its first step, which is the priest in Lumbridge church");

		client.varps[GHOST_VARP] = 2;
		settle();
		check(said(screen(), "Go back north out of the swamp"),
			"writing the quest's varp moves the panel to the leg that varp names");
		// THE AMULET IS NOT A SHOPPING ITEM. Father Urhney hands it over in the leg before, so
		// telling a player to go and get one is telling them to do the quest they are doing.
		check(!said(screen(), "need Ghostspeak amulet"),
			"...and does not ask for the amulet, which is something this quest hands over");

		// THE SAME LEG WITH THE AMULET IN THE BAG. Wearing it is then something that can be done
		// standing still, and it is what the leg opens with.
		carry(GHOSTSPEAK, 1);
		settle();
		check(said(screen(), "Wear Ghostspeak amulet"),
			"carrying the amulet, the panel says to put it on");
		// LOOKING AT THE CORNER OF THE SCENE, because a step with no tile is a Spot whose tile
		// fields are whatever they were left as, and 0,0 is a real tile that would be marked.
		int cameraX = client.cameraX;
		int cameraZ = client.cameraZ;
		client.cameraX = 64;
		client.cameraZ = 64 - 1024;
		check(painted() == 0 && scene().isEmpty(),
			"...and marks nothing in the world, because an inventory step happens where you stand");
		client.cameraX = cameraX;
		client.cameraZ = cameraZ;
		carry(GHOSTSPEAK, 0);
		settle();
		check(said(screen(), "Go back north out of the swamp"),
			"and once it is out of the inventory - which is what wearing it does - the leg moves on");

		client.varps[GHOST_VARP] = 0;
		settle();
		check(said(screen(), "Talk to Father Aereck"), "and back to the first leg when it is put back");

		// A finished quest is not a quest with a step list showing.
		client.varps[GHOST_VARP] = 5;
		settle();
		List<ConfigList.Row> rows = page();
		check(rows.size() > 0 && "complete".equals(rows.get(0).detail.split(" - ")[0]),
			"a quest whose varp has reached its end reads as complete");
		client.varps[GHOST_VARP] = 0;
		settle();
	}

	/**
	 * The other half of the item rule: what you were meant to BRING is still asked for.
	 *
	 * The Feud's first leg wants 2000 coins, leather gloves and a bucket, and the sim hands
	 * itself all three before it starts - which is what marks them as a bank trip rather than
	 * as something the quest gives you.
	 */
	static void bringTests() throws Exception {
		clearNpcs();
		change();
		track(FEUD);
		settle();
		check(said(screen(), "need Coins"), "a leg still asks for what the player has to bring");
		check(said(screen(), "need Bucket") && said(screen(), "need Leather gloves"),
			"...all of it, not just the first row");

		carry(BUCKET, 1);
		settle();
		check(!said(screen(), "need Bucket"), "...and stops asking once it is in the bag");
		check(said(screen(), "need Leather gloves"), "...while the rest of the list stands");
		carry(BUCKET, 0);
		settle();

		// AND THE QUEST'S OWN ITEMS ARE NOT ON THE PAGE AT ALL. A row for something the quest
		// hands over is a row about nothing anybody can act on; The Restless Ghost's amulet is
		// one, and its leg has no other item.
		change();
		track(GHOST);
		client.varps[GHOST_VARP] = 2;
		settle();
		List<ConfigList.Row> rows = page();
		String amulet = null;
		for (ConfigList.Row row : rows) {
			if (row.label != null && row.label.indexOf("Ghostspeak amulet") >= 0
				&& !row.label.startsWith("Wear ")) {
				amulet = row.label + " / " + row.detail;
			}
		}
		check(amulet == null, "the quest's own item is not a row on the page (" + amulet + ")");
		check(rows.size() > 1, "...and the leg's steps are still listed (" + rows.size() + " rows)");
		client.varps[GHOST_VARP] = 0;
		change();
		track(FEUD);
		settle();

		// The ones to bring ARE rows, with a count beside them.
		List<ConfigList.Row> feud = page();
		int counted = 0;
		for (ConfigList.Row row : feud) {
			if ("to carry".equals(row.detail) || "to wear".equals(row.detail)) {
				counted++;
			}
		}
		check(counted == 3, "a leg's three things to bring are three rows (" + counted + ")");
	}

	// ---------------------------------------------------------------- 6

	static void orderTests() throws Exception {
		clearNpcs();
		change();
		track(MYREQUE);
		client.varps[MYREQUE_VARP] = 4;

		// FIVE PEOPLE, NO WALK. The leg is "talk to Harold, Radigad, Polmafi, Sani, Ivan" in that
		// order. Two of them are here, and one of those is nearer - along with somebody who is in
		// no step at all, standing nearest of the three.
		spawn("Man", 1, MID_X, MID_Z);
		spawn(IVAN_NAME, IVAN, MID_X + 2, MID_Z);
		spawn(RADIGAD_NAME, RADIGAD, MID_X + 4, MID_Z);
		settle();

		check(said(scene(), RADIGAD_NAME),
			"of two of the leg's people in the scene, the earlier in the leg is the one marked");
		check(!said(scene(), IVAN_NAME), "...and not the one further down the list");
		check(!said(scene(), "Man"), "...and not the passer-by who is in no step of it");
		check(said(screen(), "Talk to " + RADIGAD_NAME), "...with the panel naming the same person");

		clearNpcs();
		client.varps[MYREQUE_VARP] = 0;
		settle();
	}

	// ---------------------------------------------------------------- 7

	static void headingTests() throws Exception {
		clearNpcs();
		change();
		track(ELEMENTAL);
		settle();

		// Nothing of this quest is in this scene, so the leg's first step leads and the heading
		// is the one written above it.
		check(said(screen(), "The bookcase on the east wall"),
			"a leg's first step is shown under the heading written above it");

		// The elemental is step 28 of the leg, five headings further down. Standing in front of
		// it, the panel has to move the heading along with the step it is naming - this quest has
		// no second leg to do it for us.
		spawn("Earth elemental", EARTH_ELEMENTAL, MID_X, MID_Z);
		settle();
		check(said(screen(), "Kill Earth elemental"), "and a step from the middle of the leg leads when it is in front of you");
		check(said(screen(), "An elemental rock in the western cavern"),
			"...under ITS heading, five headings down the same leg");
		check(!said(screen(), "The bookcase on the east wall"),
			"...and not under the one the leg opened with");

		clearNpcs();
		change();
		track(FEUD);
		settle();
	}

	// ---------------------------------------------------------------- 8

	static void bagTests() throws Exception {
		clearNpcs();
		emptyBag();
		change();
		track(COOK);
		client.varps[COOK_VARP] = 1;
		int wasX = client.sceneBaseTileX;
		int wasZ = client.sceneBaseTileZ;
		client.sceneBaseTileX = COOK_X - MID_X;
		client.sceneBaseTileZ = COOK_Z - MID_Z;
		settle();

		// THE VARP CANNOT SEE INSIDE A LEG. This one is sixteen steps long and the varp moves
		// once, at the end of it, so without the bag the first step is the answer throughout.
		check(said(screen(), "Go to the cow pasture") || said(screen(), "Pick up Egg")
				|| said(screen(), "Lumbridge road"),
			"a leg opens on its own first steps");

		carry(BUCKET, 1);
		settle();
		check(!said(screen(), "Use Grain on Hopper"),
			"a bucket in the bag moves nothing: it came from the bank, not from the quest");

		carry(GRAIN, 1);
		settle();
		check(said(screen(), "Use Grain on Hopper"),
			"grain in the bag does move it: the quest is what hands that over");

		carry(POT, 1);
		settle();
		check(said(screen(), "Use Grain on Hopper"),
			"...and a pot does not move it further, though a step further down uses one");

		emptyBag();
		client.sceneBaseTileX = wasX;
		client.sceneBaseTileZ = wasZ;
		client.varps[COOK_VARP] = 0;
		settle();
	}

	// ---------------------------------------------------------------- 9

	/**
	 * Elemental Workshop I in the order a player meets it, which is what was asked for: the
	 * bookcase, then the book it holds, then the key sewn into the book.
	 *
	 * All in ONE leg, with no varp moving between any of them.
	 */
	static void workshopTests() throws Exception {
		clearNpcs();
		emptyBag();
		change();
		track(ELEMENTAL);
		int wasX = client.sceneBaseTileX;
		int wasZ = client.sceneBaseTileZ;
		client.sceneBaseTileX = WORKSHOP_X - MID_X;
		client.sceneBaseTileZ = WORKSHOP_Z - MID_Z;
		settle();
		check(said(screen(), "house door"), "the quest starts at the house with the bookcase in it");

		carry(BATTERED_BOOK, 1);
		settle();
		check(said(screen(), "Read Battered book"),
			"with the book in the bag the bookcase is behind you, and the book is the step");

		carry(BATTERED_KEY, 1);
		settle();
		check(said(screen(), "Use Battered key on Odd looking wall"),
			"and with the key cut out of it, the wall it opens is the step");
		check(!said(screen(), "Read Battered book"), "...not the book, which is read");

		emptyBag();
		client.sceneBaseTileX = wasX;
		client.sceneBaseTileZ = wasZ;
		change();
		track(FEUD);
		settle();
	}

	// ---------------------------------------------------------------- 9b

	/**
	 * What a step handed you is the only record that you took it.
	 *
	 * This is the shape a player reported: Ali Morrisane sells a headpiece, then a beard, and
	 * the two make a disguise - three steps inside one leg, with the quest's own varbit moving
	 * before the first of them, so the helper kept saying "talk to Ali" after both sales. The
	 * Feud tracks a varbit, which this harness has no cache to decode, so the same shape is
	 * checked on Animal Magnetism: a talk that hands over a branch, and a walk back to Ava.
	 */
	static void receiptTests() throws Exception {
		clearNpcs();
		emptyBag();
		change();
		track(ANIMAL);
		client.varps[ANIMAL_VARP] = 9;
		settle();
		// Nothing of this leg is in the scene, so the panel gives its walk - and the point is
		// which END of the leg it is at, which is the half that is about the branch.
		check(!said(screen(), "Go back to Ava") && !said(screen(), "Talk to Ava"),
			"a leg opens at its own beginning, not at the step after the talk");

		carry(UNDEAD_BRANCH, 1);
		settle();
		check(!said(screen(), "Talk to Undead tree"),
			"...and once what that talk handed over is in the bag, the talk is behind you");
		check(said(screen(), "Go back to Ava") || said(screen(), "Talk to Ava"),
			"...with the rest of the leg in front of you instead");

		emptyBag();
		client.varps[ANIMAL_VARP] = 0;
		change();
		track(FEUD);
		settle();
	}

	// ---------------------------------------------------------------- 9bb

	/**
	 * The quest's own working flags, which move inside a leg.
	 *
	 * THE SECOND HAND ON THE CLOCK. A progress varp ticks once a leg; a flag ticks with each
	 * step, and the sims have been asserting them all along. Animal Magnetism walks between two
	 * farmers eleven times at one progress value and counts the trips in a varp of its own.
	 */
	static void flagTests() throws Exception {
		clearNpcs();
		emptyBag();
		change();
		track(ANIMAL);
		client.varps[ANIMAL_VARP] = 1;
		client.varps[ANIMAL_COUNT] = 0;
		settle();
		check(said(screen(), "Go to the farm") || said(screen(), "Talk to Alice"),
			"a leg opens at its first step while the quest's flag is at nothing");

		client.varps[ANIMAL_COUNT] = 5;
		settle();
		check(!said(screen(), "Go to the farm"),
			"...and the flag moving carries the leg with it, with no progress varp in sight");
		check(said(screen(), "Go back to Malcolm") || said(screen(), "Talk to Alice's husband"),
			"...to the step that flag is true from");

		// A FLAG AT THE END OF THE LIST SAYS THE LEG IS OVER. The quest writes its progress varp
		// at the end of a conversation the last step starts - the gap between the two is where a
		// player drinks three beers and is still told to buy the first.
		change();
		track(HORROR);
		client.varps[HORROR_VARP] = 1;
		client.varps[HORROR_DONE] = 0;
		settle();
		check(said(screen(), "Go to the Barbarian Outpost") || said(screen(), "Talk to Gunnjorn"),
			"a leg with an end-of-leg flag opens normally while that flag is unset");

		client.varps[HORROR_DONE] = 63;
		settle();
		check(said(screen(), "Go north through the opened panel"),
			"...and once it is set, the NEXT leg is what to do, varp or no varp");

		// AND THE NEXT LEG, NOT THE LAST. Lunar Diplomacy has four legs behind the one its flag
		// finishes, so a walk forward that went to the end of the quest would show the wrong one.
		change();
		track(LUNAR);
		client.varps[LUNAR_VARP] = 110;
		client.varps[LUNAR_DONE] = 255;
		settle();
		check(said(screen(), "Use Waking sleep vial on Kindling"),
			"a finished leg hands over to the one after it, not to the end of the quest");
		client.varps[LUNAR_VARP] = 0;
		client.varps[LUNAR_DONE] = 0;

		client.varps[HORROR_VARP] = 0;
		client.varps[HORROR_DONE] = 0;
		client.varps[ANIMAL_VARP] = 0;
		client.varps[ANIMAL_COUNT] = 0;
		change();
		track(FEUD);
		settle();
	}

	// ---------------------------------------------------------------- 9c

	/**
	 * A thing you are WEARING is a thing you have - and a thing you are told to wear is not.
	 *
	 * The equipment is its own transmitted component, so neither of these could be seen before:
	 * a player wearing the amulet they were told to bring was told to bring it again, and "Wear
	 * Ghostspeak amulet" would have counted itself done the moment the amulet reached the bag.
	 */
	static void wornTests() throws Exception {
		clearNpcs();
		emptyBag();
		change();
		track(GHOST);
		client.varps[GHOST_VARP] = 2;
		settle();

		carry(GHOSTSPEAK, 1);
		settle();
		check(said(screen(), "Wear Ghostspeak amulet"),
			"the amulet in the bag is a step still waiting to be taken");

		carry(GHOSTSPEAK, 0);
		wear(GHOSTSPEAK, 1);
		settle();
		check(!said(screen(), "Wear Ghostspeak amulet"),
			"...and on your neck it is done, which no varp in this quest ever says");
		check(said(screen(), "Go back north out of the swamp"), "...so the leg moves on");

		// And the other way round: a leg's shopping list counts what you are wearing.
		change();
		track(FEUD);
		settle();
		check(said(screen(), "need Bucket"), "a leg asks for a bucket it cannot see");
		wear(BUCKET, 1);
		settle();
		check(!said(screen(), "need Bucket"), "...and stops once the player is wearing one");

		wear(BUCKET, 0);
		wear(GHOSTSPEAK, 0);
		client.varps[GHOST_VARP] = 0;
		settle();
	}

	// ---------------------------------------------------------------- 10

	/**
	 * The other half of "highlight what you need": the item in the bag.
	 *
	 * A box round the slot, drawn on the SIDEBAR - a different raster from the one the marker and
	 * the panel use, which is why the client needed a third overlay pass to draw it at all.
	 */
	static void slotTests() throws Exception {
		clearNpcs();
		emptyBag();
		change();
		track(ELEMENTAL);
		int wasX = client.sceneBaseTileX;
		int wasZ = client.sceneBaseTileZ;
		client.sceneBaseTileX = WORKSHOP_X - MID_X;
		client.sceneBaseTileZ = WORKSHOP_Z - MID_Z;
		settle();
		sidebar();
		check(paintedIn(0, 0, W, H) == 0, "with no item step in front of you, no slot is marked");

		carry(BATTERED_BOOK, 1);
		settle();
		sidebar();
		int[] slot = context().getSidebarInvCellBounds(0);
		check(slot != null && slot[0] == GRID_X && slot[1] == GRID_Y,
			"the first slot is where the grid says it is, walked out of the open tab");
		check(slot != null && paintedIn(slot[0], slot[1], slot[2], slot[3]) > 0,
			"the book's own slot is marked once the step is to read it");
		check(paintedIn(0, 0, W, H) == paintedIn(slot[0], slot[1], slot[2], slot[3]),
			"...and nothing is painted outside it");

		// ONE BOX PER ITEM, however many of it you are carrying. Two battered books is one
		// instruction, and two boxes would read as two things to do.
		carrySlot(1, BATTERED_BOOK, 1);
		settle();
		sidebar();
		int[] spare = context().getSidebarInvCellBounds(1);
		check(spare != null && paintedIn(spare[0], spare[1], spare[2], spare[3]) == 0,
			"a second copy of the item is not marked as a second thing to do");
		carrySlot(1, 0, 0);

		// The same box follows the item, rather than the slot it was first seen in.
		emptyBag();
		carry(POT, 1);
		carry(BATTERED_BOOK, 1);
		settle();
		sidebar();
		int[] second = context().getSidebarInvCellBounds(1);
		check(second != null && paintedIn(second[0], second[1], second[2], second[3]) > 0,
			"with something else in the first slot, the mark moves to where the book is");

		// Another tab open is not an error and not a mark.
		int tab = client.selectedTab;
		client.selectedTab = tab == 0 ? 1 : 0;
		sidebar();
		check(paintedIn(0, 0, W, H) == 0, "with another tab open there is nothing to mark");
		client.selectedTab = tab;

		// TWO ITEMS, TWO SLOTS. "Use Knife on Dramen branch" is one step holding two things, and
		// a player who can only see one of them marked is being shown half an instruction.
		emptyBag();
		change();
		track(LOST_CITY);
		client.varps[LOST_CITY_VARP] = 4;
		settle();
		sidebar();
		// The panel still names the step - it is the only one in this leg, and saying nothing
		// would be less use - but there is nothing in the bag to point at.
		check(paintedIn(0, 0, W, H) == 0, "with neither item in the bag, no slot is marked");

		carry(KNIFE, 1);
		settle();
		sidebar();
		check(paintedIn(0, 0, W, H) == 0, "with only the knife, still nothing: the branch is the other half");

		carry(DRAMEN_BRANCH, 1);
		settle();
		check(said(screen(), "Use Knife on Dramen branch"), "with both, the step is what to do");
		sidebar();
		int[] knifeSlot = context().getSidebarInvCellBounds(0);
		int[] branchSlot = context().getSidebarInvCellBounds(1);
		check(knifeSlot != null && paintedIn(knifeSlot[0], knifeSlot[1], knifeSlot[2], knifeSlot[3]) > 0
				&& branchSlot != null
				&& paintedIn(branchSlot[0], branchSlot[1], branchSlot[2], branchSlot[3]) > 0,
			"...and both slots are marked, not just one");

		emptyBag();
		client.varps[LOST_CITY_VARP] = 0;
		change();
		track(ELEMENTAL);
		settle();

		// AN ITEM THE STEP WANTS AND YOU HAVE NOT GOT. The npc is in front of you, so the step is
		// the one to show - but there is no slot to mark, and nothing to repaint the panel for.
		emptyBag();
		clearNpcs();
		change();
		track(SHILO);
		client.varps[SHILO_VARP] = 0;
		spawn("Trufitus", TRUFITUS, MID_X, MID_Z);
		settle();
		check(said(screen(), "Use Wampum belt on Trufitus"),
			"a step whose npc is in front of you is the step, belt or no belt");
		sidebar();
		check(paintedIn(0, 0, W, H) == 0, "...and with no belt in the bag, no slot is marked");
		client.redrawSidebar = false;
		manager.onGameTick();
		check(!client.redrawSidebar,
			"...nor is the panel repainted for a mark that is not there");

		carry(WAMPUM_BELT, 1);
		settle();
		sidebar();
		int[] belt = context().getSidebarInvCellBounds(0);
		check(belt != null && paintedIn(belt[0], belt[1], belt[2], belt[3]) > 0,
			"and once the belt is in the bag, its slot is marked");
		emptyBag();
		clearNpcs();
		change();
		track(ELEMENTAL);
		settle();

		// THE SIDEBAR IS CACHED, so a mark that is only drawn when the client happens to repaint
		// the panel would come and go with the mouse. The plugin asks for the repaint on a tick
		// while it has something to mark, and once more when it stops.
		carry(BATTERED_BOOK, 1);
		settle();
		client.redrawSidebar = false;
		manager.onGameTick();
		check(client.redrawSidebar, "with a slot to mark, the plugin asks for the sidebar to be repainted");
		emptyBag();
		settle();
		client.redrawSidebar = false;
		manager.onGameTick();
		check(client.redrawSidebar, "...and once more when the mark goes, so it is rubbed out");
		client.redrawSidebar = false;
		manager.onGameTick();
		check(!client.redrawSidebar, "...and not every tick after that, for a panel with nothing on it");

		// AND THE CLIENT MAKES THE PASS AT ALL. Everything above runs renderOverlays by hand; the
		// line that does it in the game is in drawSidebar, where nothing a test can call reaches,
		// and without it this whole section would pass against a plugin that draws nowhere.
		String source = read("src/main/java/jagex2/client/Client.java");
		int draws = source.indexOf("public void drawSidebar()");
		int pass = source.indexOf("Overlay.LAYER_SIDEBAR", draws);
		int menu = source.indexOf("this.drawMenu();", draws);
		check(draws >= 0 && pass > draws && pass < menu,
			"drawSidebar runs the sidebar overlay pass, after the tab and before the menu");

		client.sceneBaseTileX = wasX;
		client.sceneBaseTileZ = wasZ;
		change();
		track(FEUD);
		settle();
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

	// ---------------------------------------------------------------- the client under it

	static void setUp() {
		client = new Client();
		client.levelHeightmap = new int[4][105][105];
		client.levelTileFlags = new byte[4][104][104];
		client.currentLevel = 0;
		client.cameraPitch = 0;
		client.cameraYaw = 0;
		client.cameraX = MID_X * 128 + 64;
		client.cameraZ = MID_Z * 128 + 64 - 1024;
		client.cameraY = -24;
		client.ingame = true;
		// The scene is placed so that the bazaar tile the table records for The Feud's first walk
		// is the middle of it, which is also where the camera points.
		client.sceneBaseTileX = BAZAAR_X - MID_X;
		client.sceneBaseTileZ = BAZAAR_Z - MID_Z;

		Client.localPlayer = new ClientPlayer();
		Client.localPlayer.field1157 = MID_X * 128;
		Client.localPlayer.field1158 = MID_Z * 128;

		// An inventory for countObj to read: one component, the one the server fills.
		jagex2.config.Component.types = new jagex2.config.Component[INVENTORY_COM + 1];
		jagex2.config.Component.data = new byte[INVENTORY_COM + 1][];
		jagex2.config.Component inv = new jagex2.config.Component();
		inv.type = 2;
		inv.width = 4;
		inv.height = 7;
		inv.invSlotObjId = new int[28];
		inv.invSlotObjCount = new int[28];
		jagex2.config.Component.types[INVENTORY_COM] = inv;
		inv.id = INVENTORY_COM;
		inv.marginX = 10;
		inv.marginY = 1;

		// THE TAB THE GRID SITS IN. The client finds the inventory by walking the open sidebar
		// tab's tree, so the test builds the smallest tree with that shape: one layer holding the
		// grid at an offset, which is also what makes "the box is on the slot" an assertion about
		// the real walk rather than about a number this file chose.
		jagex2.config.Component tab = new jagex2.config.Component();
		tab.id = INVENTORY_TAB;
		tab.type = 0;
		tab.width = 190;
		tab.height = 261;
		tab.children = new int[] { INVENTORY_COM };
		tab.childX = new int[] { GRID_X };
		tab.childY = new int[] { GRID_Y };
		jagex2.config.Component.types[INVENTORY_TAB] = tab;

		// The equipment, which is its own component and not part of the tab above.
		jagex2.config.Component body = new jagex2.config.Component();
		body.id = WORN_COM;
		body.type = 2;
		body.width = 1;
		body.height = 14;
		body.invSlotObjId = new int[14];
		body.invSlotObjCount = new int[14];
		jagex2.config.Component.types[WORN_COM] = body;
		client.tabInterfaceId[client.selectedTab] = INVENTORY_TAB;

		Pix3D.zoom = 512;
		Pix3D.centerX = W / 2;
		Pix3D.centerY = H / 2;

		font = new RecordingFont();
		manager = new PluginManager(client, font, font, font);
		manager.reload();

		for (PluginManager.Entry e : manager.getPlugins()) {
			if ("questhelper".equals(e.key)) {
				entry = e;
			} else if (e.isEnabled()) {
				// Everything else off, so a marker, a pixel or a panel line can only be this one.
				manager.setEnabled(e, false);
			}
		}
		check(entry != null, "the Quest helper plugin is there");
		if (entry == null) {
			System.out.println("1 FAILED");
			System.exit(1);
		}
		manager.setEnabled(entry, true);
		manager.onClientTick(0);
	}

	// ---------------------------------------------------------------- 11

	// ---------------------------------------------------------------- 12

	/**
	 * A LOC OF THE LEG, in the scene: the half of the marker the npc checks never reach.
	 *
	 * Elemental Workshop I is nearly all locs - a bookcase, a door, a lever, a furnace - and was
	 * the quest the request came in about. The scene is built by hand here, as AgilityTest builds
	 * one, because findLocs walks the squares themselves: the scene keeps no list of locs.
	 *
	 * The name the marker writes comes from the CACHE and not from the table, which is the point
	 * of the second check: the live name primed here is not the word the table recorded, so only a
	 * reader that asks the cache can write it. The third is the Feud's bug in its loc form - a
	 * multiloc's shell carries the placeholder name, the cache hands back nothing for it, and what
	 * the step calls the thing has to stand in or the marker goes out with no label at all.
	 */
	static void locTests() throws Exception {
		clearNpcs();
		change();
		track(ELEMENTAL);

		// Standing beside the bookcase, which is where the quest's second step happens.
		client.sceneBaseTileX = BOOKCASE_X - MID_X;
		client.sceneBaseTileZ = BOOKCASE_Z - MID_Z;
		Client.localPlayer.field1157 = MID_X * 128;
		Client.localPlayer.field1158 = MID_Z * 128;
		primeLocs("Dusty bookcase");
		client.scene = new World3D(client.levelHeightmap, 104, 4, 104);
		putLoc(MID_X, MID_Z, BOOKCASE);
		settle();

		check(said(screen(), "Search " + BOOKCASE_NAME),
			"a loc of the leg in the scene is the step the panel gives");
		check(painted() > 0, "...with a box drawn on its tile");
		check(said(scene(), "Dusty bookcase"),
			"...and the marker labelled from the cache in front of you, not from the table");

		// THE SAME SHAPE AS THE VILLAGER. A multiloc shell has no name of its own, so the cache
		// answers with nothing and the step's own word is the only label there is.
		primeLocs("null");
		settle();
		check(said(scene(), BOOKCASE_NAME),
			"a loc the cache has no name for is labelled from the step instead");
		check(!said(scene(), "null"), "...and never with the placeholder the cache carries");

		client.scene = null;
		client.sceneBaseTileX = 0;
		client.sceneBaseTileZ = 0;
		change();
		track(FEUD);
		settle();
	}

	/**
	 * LocType's own twenty-slot cache, filled by hand so method561 answers without a cache file.
	 *
	 * field1612 is the offsets table, which is only read for its LENGTH here - PluginContext uses
	 * it to tell a loc id the cache could define from one it could not - so an empty array of the
	 * right size is the whole of what a lookup needs.
	 */
	static void primeLocs(String name) {
		LocType.field1612 = new int[BOOKCASE + 1];
		LocType.field1636 = new LocType[20];
		for (int i = 0; i < LocType.field1636.length; i++) {
			LocType.field1636[i] = new LocType();
			LocType.field1636[i].field1627 = -1;
			LocType.field1636[i].field1655 = 1;
			LocType.field1636[i].field1629 = 1;
		}
		LocType.field1636[0].field1627 = BOOKCASE;
		LocType.field1636[0].field1630 = name;
	}

	/**
	 * One piece of scenery in the scene, the way the map decode leaves it.
	 *
	 * The id is the top of a bitfield and 2 << 29 is the layer; World3D reads these fields and
	 * nothing else, so there is no model and nothing to load.
	 */
	static void putLoc(int tileX, int tileZ, int id) {
		Square square = client.scene.field1018[0][tileX][tileZ];
		if (square == null) {
			square = new Square(0, tileX, tileZ);
			client.scene.field1018[0][tileX][tileZ] = square;
		}
		Sprite sprite = new Sprite();
		sprite.field88 = (id << 14) | 2 << 29;
		sprite.field82 = tileX;
		sprite.field84 = tileZ;
		square.field1391[square.field1390] = sprite;
		square.field1390++;
	}

	/**
	 * A MULTINPC: one npc id whose name, level and right-click words live on a child type.
	 *
	 * The Feud's villagers are these, and so is every quest npc with something different to say
	 * as the quest moves. The shell the server sends carries no name of its own - what it carries
	 * is the config's placeholder, the four letters "null" - and the client picks the child with a
	 * varp before it reads a name for its own right-click menu. A plugin that read the shell
	 * instead wrote "null" over the villager's head, which is what a player reported.
	 *
	 * Radigad stands in for the villager because the harness can write his quest's progress: The
	 * Feud's is a varbit, and there is no cache here to decode one. The shape is the villager's.
	 */
	static void multiTests() throws Exception {
		clearNpcs();
		change();
		track(MYREQUE);
		client.varps[MYREQUE_VARP] = 4;

		// THE CHILD IS WHAT THE PLAYER SEES. The shell is nameless and its child carries a name
		// that is NOT the one the table recorded, so only a reader that resolves the child can
		// write it - the table's own label would read "Radigad Ponfit".
		final String DISGUISED = "Dwarf in a hood";
		clearNpcs();
		spawnMulti(RADIGAD, MULTI_SELECTOR, 1, DISGUISED, MID_X, MID_Z);
		settle();
		check(said(scene(), DISGUISED),
			"an npc whose name is on a multinpc child is labelled with the child's name");
		check(!said(scene(), "null"), "...and not with the placeholder the shell carries");
		check(said(screen(), "Talk to " + RADIGAD_NAME),
			"...while the panel still gives the step's own words, which is what the guide says");

		// THE VARP CHOOSES. Slot 0 of this shell holds no child, which is the state a quest npc is
		// in before its quest starts, and there is nothing to resolve.
		client.varps[MULTI_SELECTOR] = 0;
		settle();
		check(!said(scene(), DISGUISED), "moving the varp off that child stops it being the name");
		check(said(scene(), RADIGAD_NAME),
			"...and with no child to read, the step's own name is written instead");
		check(!said(scene(), "null"), "...still never the placeholder");

		// AN ORDINARY NAMELESS NPC, no children at all: the same placeholder, the same answer.
		clearNpcs();
		spawn("null", RADIGAD, MID_X, MID_Z);
		settle();
		check(said(scene(), RADIGAD_NAME),
			"an npc with no name in its config is labelled from the step rather than \"null\"");
		check(!said(scene(), "null"), "...which is the whole of what that word was doing on screen");

		clearNpcs();
		client.varps[MYREQUE_VARP] = 0;
		client.varps[MULTI_SELECTOR] = 0;
		change();
		track(FEUD);
		settle();
	}

	/** The plugin's sidebar page as the sidebar reads it. */
	static List<ConfigList.Row> page() {
		for (PluginManager.PanelSnapshot panel : manager.snapshotPanels()) {
			if ("questhelper".equals(panel.entry.key)) {
				return panel.rows;
			}
		}
		return new ArrayList<ConfigList.Row>();
	}

	static ConfigList.Row row(List<ConfigList.Row> rows, String label) {
		for (ConfigList.Row row : rows) {
			if (label.equals(row.label)) {
				return row;
			}
		}
		return null;
	}

	/** Presses Change on the tracked quest's row, which puts the page back to the list. */
	static void change() {
		for (PluginManager.PanelSnapshot panel : manager.snapshotPanels()) {
			if ("questhelper".equals(panel.entry.key) && !panel.rows.isEmpty()) {
				panel.act(0);
				return;
			}
		}
		throw new IllegalStateException("no page to change from");
	}

	/** Presses Track on a quest's row, which is the only way a quest becomes the tracked one. */
	static void track(String quest) {
		for (PluginManager.PanelSnapshot panel : manager.snapshotPanels()) {
			if (!"questhelper".equals(panel.entry.key)) {
				continue;
			}
			ConfigList.Row row = row(panel.rows, quest);
			if (row != null) {
				panel.act(row.index);
				return;
			}
		}
		throw new IllegalStateException("no row for " + quest);
	}

	/**
	 * Puts an obj in the player's inventory, where PluginContext reads it from.
	 *
	 * The inventory is a COMPONENT the server fills, not a field on the client, and ids are
	 * stored one higher than they are so that an empty slot can be zero.
	 */
	static void carry(int objId, int count) {
		jagex2.config.Component inv = jagex2.config.Component.types[INVENTORY_COM];
		int slot = -1;
		for (int i = 0; i < inv.invSlotObjId.length; i++) {
			if (inv.invSlotObjId[i] == objId + 1) {
				slot = i;
				break;
			}
			if (inv.invSlotObjId[i] == 0 && slot < 0) {
				slot = i;
			}
		}
		if (slot < 0) {
			throw new IllegalStateException("no room for " + objId);
		}
		inv.invSlotObjId[slot] = count > 0 ? objId + 1 : 0;
		inv.invSlotObjCount[slot] = count;
	}

	/** An item in a named slot, for the cases where which slot it is in is the question. */
	static void carrySlot(int slot, int objId, int count) {
		jagex2.config.Component inv = jagex2.config.Component.types[INVENTORY_COM];
		inv.invSlotObjId[slot] = count > 0 ? objId + 1 : 0;
		inv.invSlotObjCount[slot] = count;
	}

	/** An item on the player's body, which is a different component from the bag. */
	static void wear(int objId, int count) {
		jagex2.config.Component worn = jagex2.config.Component.types[WORN_COM];
		int slot = -1;
		for (int i = 0; i < worn.invSlotObjId.length; i++) {
			if (worn.invSlotObjId[i] == objId + 1) {
				slot = i;
				break;
			}
			if (worn.invSlotObjId[i] == 0 && slot < 0) {
				slot = i;
			}
		}
		worn.invSlotObjId[slot] = count > 0 ? objId + 1 : 0;
		worn.invSlotObjCount[slot] = count;
	}

	/** An empty bag, so one section's items cannot decide another's answer. */
	static void emptyBag() {
		jagex2.config.Component inv = jagex2.config.Component.types[INVENTORY_COM];
		java.util.Arrays.fill(inv.invSlotObjId, 0);
		java.util.Arrays.fill(inv.invSlotObjCount, 0);
	}

	/**
	 * A MULTINPC in the scene: a nameless shell whose child the varp picks.
	 *
	 * The shell keeps NpcType's own default name, which is the placeholder the config carries,
	 * because that is the thing being tested. field1425 is the child table and field1462 the varp
	 * that indexes it; slot 0 is left empty on purpose, so writing 0 to the varp is a shell with
	 * no child to resolve. The child is primed into NpcType's 20-entry round-robin cache, which is
	 * where NpcType.get looks first - there is no cache file here for it to fall through to.
	 */
	static void spawnMulti(int id, int selector, int slot, String childName, int tileX, int tileZ) {
		NpcType.varProvider = client;
		if (NpcType.field1458 == null) {
			NpcType.field1458 = new NpcType[20];
			for (int i = 0; i < NpcType.field1458.length; i++) {
				NpcType.field1458[i] = new NpcType();
			}
		}
		int childId = id + 1000;
		NpcType child = new NpcType();
		child.field1455 = childName;
		child.field1442 = 1;
		child.field1431 = childId;
		NpcType.field1458[0] = child;

		NpcType shell = new NpcType();
		shell.field1431 = id;
		shell.field1462 = selector;
		shell.field1425 = new int[slot + 1];
		for (int i = 0; i < shell.field1425.length; i++) {
			shell.field1425[i] = -1;
		}
		shell.field1425[slot] = childId;
		client.varps[selector] = slot;

		ClientNpc npc = new ClientNpc();
		npc.field1370 = shell;
		npc.field1148 = 1;
		npc.field1157 = tileX * 128 + 64;
		npc.field1158 = tileZ * 128 + 64;
		int at = client.npcCount;
		client.npcs[at] = npc;
		client.npcIds[at] = at;
		client.npcCount = at + 1;
	}

	/** An npc in the scene, the way the client's entity handler puts one there. */
	static void spawn(String name, int id, int tileX, int tileZ) {
		NpcType type = new NpcType();
		type.field1455 = name;
		type.field1442 = 1;
		type.field1431 = id;
		ClientNpc npc = new ClientNpc();
		npc.field1370 = type;
		npc.field1148 = 1;
		npc.field1157 = tileX * 128 + 64;
		npc.field1158 = tileZ * 128 + 64;
		int slot = client.npcCount;
		client.npcs[slot] = npc;
		client.npcIds[slot] = slot;
		client.npcCount = slot + 1;
	}

	static void clearNpcs() {
		for (int i = 0; i < client.npcCount; i++) {
			client.npcs[client.npcIds[i]] = null;
		}
		client.npcCount = 0;
	}

	/**
	 * Waits out the plugin's cache.
	 *
	 * The marker looks the scene up once a fifth of a second rather than once a frame, because
	 * finding a loc means sweeping 104x104 tiles through four layers. A test that moved an npc
	 * and drew immediately would be reading the answer from before the move - which is also what
	 * a player sees for a fifth of a second, and is the one place this suite has to wait.
	 */
	static void settle() throws Exception {
		Thread.sleep(SETTLE);
		manager.onClientTick(ticks++);
	}

	static int ticks = 1;

	/** One sidebar frame: what the bag overlay drew, in the sidebar's own coordinates. */
	static List<Drawn> sidebar() {
		return frame(Overlay.LAYER_SIDEBAR);
	}

	/** Pixels of a colour inside a rectangle, which is how a box on a slot is measured. */
	static int paintedIn(int x, int y, int w, int h) {
		int n = 0;
		for (int row = Math.max(0, y); row < Math.min(H, y + h); row++) {
			for (int col = Math.max(0, x); col < Math.min(W, x + w); col++) {
				if (pixels[row * W + col] != 0) {
					n++;
				}
			}
		}
		return n;
	}

	/** One scene-layer frame: what the marker drew. */
	static List<Drawn> scene() {
		return frame(Overlay.LAYER_SCENE);
	}

	/** One screen-layer frame: what the panel drew. */
	static List<Drawn> screen() {
		return frame(Overlay.LAYER_SCREEN);
	}

	static List<Drawn> frame(int layer) {
		font.rows.clear();
		java.util.Arrays.fill(pixels, 0);
		Pix2D.bind(W, H, pixels);
		manager.renderOverlays(W, H, layer);
		return new ArrayList<Drawn>(font.rows);
	}

	/** The pixels the marker left behind, which is the box rather than its label. */
	static int painted() {
		scene();
		int n = 0;
		for (int i = 0; i < pixels.length; i++) {
			if (pixels[i] != 0) {
				n++;
			}
		}
		return n;
	}

	static boolean said(List<Drawn> rows, String text) {
		for (Drawn row : rows) {
			if (strip(row.text).indexOf(text) >= 0) {
				return true;
			}
		}
		return false;
	}

	/** A drawn line without its colour tags, which the panel writes as @gre@ and @red@. */
	static String strip(String text) {
		return text == null ? "" : text.replaceAll("@[a-z]{3}@", "").trim();
	}

	static PluginContext context() {
		try {
			java.lang.reflect.Field field = PluginManager.class.getDeclaredField("ctx");
			field.setAccessible(true);
			return (PluginContext) field.get(manager);
		} catch (Throwable error) {
			throw new IllegalStateException("cannot reach the context: " + error);
		}
	}

	static final class Drawn {
		final int colour;
		final String text;

		Drawn(int colour, String text) {
			this.colour = colour;
			this.text = text;
		}
	}

	/** A font that paints nothing and remembers every line, so a label is a recorded call. */
	static final class RecordingFont extends PixFont {

		static final int ADVANCE = 4;

		final List<Drawn> rows = new ArrayList<Drawn>();

		RecordingFont() {
			this.height = 12;
			java.util.Arrays.fill(this.charAdvance, ADVANCE);
		}

		int widthOf(String text) {
			return text == null ? 0 : text.length() * ADVANCE;
		}

		public int stringWid(String text) {
			return this.widthOf(text);
		}

		public int stringWidTag(String text) {
			return this.widthOf(text);
		}

		public void drawStringTag(int colour, int x, int y, boolean shadow, String text) {
			this.rows.add(new Drawn(colour, text));
		}

		public void drawString(int x, int colour, int y, String text) {
			this.rows.add(new Drawn(colour, text));
		}
	}
}
