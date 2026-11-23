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
import jagex2.config.VarbitType;
import jagex2.config.NpcType;
import jagex2.dash3d.ClientNpc;
import jagex2.dash3d.Model;
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
	/**
	 * Hazeel Cult, the one quest in the table with TWO entries on ONE progress varp: one per side.
	 *
	 * That is what DeathPlateau-Bugs#84 was about - "hazeel cult (hazeel side) not working. each
	 * step opens the other guide" - because a move in hazeelcultquest moves both of them and the
	 * follow rule kept the last one it saw, which is the Carnillean side.
	 */
	static final String HAZEEL_GOOD = "HAZEEL CULT";
	static final String HAZEEL_EVIL = "HAZEEL CULT (Hazeel side)";
	static final int HAZEEL_VARP = 223;

	/** A varp no quest in the table reads, used only to choose a multinpc's child. */
	static final int MULTI_SELECTOR = 1900;

	/**
	 * Scorpion Catcher's three Kharid Scorpions, which the server runs one script on.
	 *
	 * Its first leg names 385 and its second 386, so the table's own rows are the proof that the
	 * sim met a different one each time; a player standing in front of the third is standing in
	 * front of the step.
	 */
	static final String SCORPION = "SCORPION CATCHER";
	static final int SCORPION_VARP = 76;
	static final int SCORPION_FIRST = 385;
	static final int SCORPION_THIRD = 387;
	static final String SCORPION_NAME = "Kharid Scorpion";

	/** Elemental Workshop I's bookcase, the quest's first thing to search, and its name there. */
	static final int BOOKCASE = 3389;
	static final String BOOKCASE_NAME = "Bookcase";
	static final int BOOKCASE_X = 2716;
	static final int BOOKCASE_Z = 3481;
	static final int RADIGAD = 1572;
	static final String RADIGAD_NAME = "Radigad Ponfit";
	/** The first of Myreque's five people, so that the second is a step with a first in front of it. */
	static final int HAROLD = 1571;
	static final String HAROLD_NAME = "Harold Evans";
	/** Longer than the plugin waits for a click's conversation, whatever that is. */
	static final int PATIENCE = 24;

	/**
	 * Monkey Madness, whose table has TWO legs recorded at stage 12.
	 *
	 * The varp does not move between the Grand Tree and Lumdo, so the sim's two runs of steps
	 * both carry that value - and only one of them can be the leg a player at 12 is shown.
	 */
	static final String MONKEY = "MONKEY MADNESS";
	static final int MONKEY_VARP = 365;

	/**
	 * Death Plateau's stage 50: two talks with Harold, and then a note.
	 *
	 * Four legs in the table end with an explanation rather than an instruction, and this is the
	 * one whose progress is a plain varp. Once both talks are behind the player the only thing
	 * left in the leg is "Roll the dice", which is nobody's step.
	 */
	/** Fight Arena's first walk, which names the floor it is on: [2569, 3198, 0]. */
	static final String ARENA = "FIGHT ARENA";
	static final int ARENA_VARP = 17;
	static final int SERVIL_X = 2569;
	static final int SERVIL_Z = 3198;

	static final String PLATEAU = "DEATH PLATEAU";
	static final int PLATEAU_VARP = 314;
	/** Not Myreque's Harold Evans above: Death Plateau's Harold, who runs the dice game. */
	static final int DICE_HAROLD = 1078;
	static final String DICE_HAROLD_NAME = "Harold";

	/**
	 * Big Chompy Bird Hunting, whose stage 35 has no steps at all.
	 *
	 * The leg is "Rantz takes his shot and misses": a wait and a lucky roll, neither of which is
	 * anything a player clicks, so the exporter has nothing to write for it.
	 */
	static final String HERO = "HEROES' QUEST (solo part)";
	static final int HERO_VARP = 188;
	static final String CHOMPY = "BIG CHOMPY BIRD HUNTING";
	static final int CHOMPY_VARP = 293;
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
		System.out.println("2b. the quest list is in the quest tab's order");
		listOrderTests();
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
		System.out.println("13. one of a crowd the step would take just as well");
		familyTests();
		System.out.println("14. the dialogue option the step would pick");
		dialogueTests();
		System.out.println("15. every quest, at every leg, drawn");
		sweepTests();
		System.out.println("15b. and every value between the legs, where a player actually sits");
		gapSweepTests();
		System.out.println("16. the quest you are already in the middle of");
		followTests();
		System.out.println("17. the step you have just taken");
		stepTests();
		System.out.println("18. the shapes in the table that left the panel with nothing to say");
		shapeTests();

		System.out.println();
		System.out.println(fails == 0 ? (passes + " CHECKS, ALL PASS") : (fails + " FAILED of " + (passes + fails)));
		System.exit(fails == 0 ? 0 : 1);
	}

	// ---------------------------------------------------------------- 15

	/**
	 * EVERY QUEST AT EVERY LEG, DRAWN, AND NONE OF THEM THROWING.
	 *
	 * WHY THIS EXISTS. A player reported the helper turning itself off, quoting the client's own
	 * words back: "it kept throwing while drawing". That message comes from PluginManager, which
	 * catches a Throwable out of an overlay, counts it, and disables the plugin at the fifth one -
	 * so the plugin fails SILENTLY until it vanishes, and the only record is a line in the dev log
	 * on the player's own machine. Every test above picks a quest and a leg by hand, which is how
	 * a throw in the 880 legs nobody picked stays invisible.
	 *
	 * So this walks the shipped table: track each quest, write each leg's `from` into its progress
	 * varp, and draw all three layers. It asserts nothing about what is drawn - the sections above
	 * do that - only that drawing it does not throw.
	 *
	 * THE COUNTER, NOT THE EXCEPTION. renderOverlays swallows the Throwable by design, so there is
	 * nothing to catch out here; what it leaves behind is entry.renderErrors. The count is zeroed
	 * and the plugin re-enabled before each leg, so one bad leg cannot hide the ones after it.
	 *
	 * IT USED TO STOP AT THE VARBIT QUESTS. A varbit is a range of bits inside a parent varp
	 * described by the cache, and this harness has no cache, so writing one read back zero: 28
	 * quests were swept at their first leg only and 225 legs were drawn by nothing. The Feud was
	 * the worst of them at 16 unreachable legs, and The Feud is the quest the reports keep coming
	 * from, which is how "667 legs swept, no throw" and a player hitting one were both true.
	 *
	 * So the three numbers that make up a varbit now travel in the table - `stageBits`, emitted by
	 * the engine's ExportHelper out of VarBitType - and this builds the entries getVarbit needs by
	 * hand. VarbitType.field1760 is the table it reads; nothing but this harness ever assigns it,
	 * because a real client unpacks it from the cache.
	 *
	 * AND EVERY LEG IS READ BACK THROUGH THE PLUGIN'S OWN questValue PATH before it counts. Bit
	 * arithmetic written here could disagree with decodeVarbit, a bit range could be wrong, or a
	 * leg value could be too wide for its field - and in all three cases the sweep would draw leg
	 * 0 over and over while reporting a number it had not reached. A leg that does not read back
	 * is counted as unreachable and named, which is the one thing worse than a small sweep: a big
	 * one that is lying.
	 */
	static void sweepTests() throws Exception {
		List<?> all = questTable();

		java.lang.reflect.Field errorsField = PluginManager.Entry.class.getDeclaredField("renderErrors");
		errorsField.setAccessible(true);
		java.lang.reflect.Field enabledField = PluginManager.Entry.class.getDeclaredField("enabled");
		enabledField.setAccessible(true);

		VarbitType[] wasTable = VarbitType.field1760;
		VarbitType.field1760 = varbitTable(all);

		PluginContext ctx = context();
		int legs = 0;
		int unreachable = 0;
		List<String> threw = new ArrayList<String>();
		List<String> untrackable = new ArrayList<String>();
		List<String> notWritten = new ArrayList<String>();

		for (Object element : all) {
			java.util.Map<?, ?> quest = (java.util.Map<?, ?>) element;
			String name = jagex2.client.plugin.hub.Json.string(quest, "name", "");
			int varp = jagex2.client.plugin.hub.Json.integer(quest, "stageVarId", -1);
			boolean varbit = Boolean.TRUE.equals(quest.get("stageIsVarbit"));
			try {
				change();
				track(name);
			} catch (RuntimeException notListed) {
				untrackable.add(name);
				continue;
			}
			java.util.Map<?, ?> bits = (java.util.Map<?, ?>) quest.get("stageBits");
			for (Object stage : jagex2.client.plugin.hub.Json.array(quest, "stages")) {
				int from = jagex2.client.plugin.hub.Json.integer((java.util.Map<?, ?>) stage, "from", 0);
				if (varbit) {
					writeBits(bits, from);
				} else if (varp >= 0 && varp < client.varps.length) {
					client.varps[varp] = from;
				}
				// READ BACK THE WAY THE PLUGIN READS IT. If this disagrees, the leg was never set
				// and drawing it would be drawing some other leg under its name.
				int saw = varp < 0 ? 0 : (varbit ? ctx.getVarbit(varp) : ctx.getVarp(varp));
				if (saw != from) {
					unreachable++;
					notWritten.add(name + " leg " + from + " read back as " + saw);
					continue;
				}
				manager.onClientTick(ticks++);
				errorsField.setInt(entry, 0);
				enabledField.setBoolean(entry, true);
				screen();
				scene();
				sidebar();
				// AND THE CHATBOX, which nothing drew until now. The sweep ran three of the
				// plugin's four overlays: panel, marker and bag. The fourth draws a box round the
				// dialogue option a step would pick, and it is the only one that reads the
				// table's `picks` - 884 of them across the table, parsed by optionFor while the
				// chatbox is being painted. A pick has already been a fault once (a run of digits
				// long enough to overflow an int, guarded there now), and with no dialogue on
				// screen optionNow returns before optionFor is ever reached, so drawing the layer
				// alone proves nothing. The leg's own picks go up as the options so that the
				// matching path is the one taken.
				String[] lines = optionsFor((java.util.Map<?, ?>) stage);
				openDialogue(lines);
				chat();
				closeDialogue();
				legs++;
				if (errorsField.getInt(entry) > 0) {
					threw.add(name + " at leg " + from);
				}
			}
		}

		for (String bad : threw) {
			System.out.println("       threw while drawing: " + bad);
		}
		check(untrackable.isEmpty(), "every quest in the table has a row to track (" + all.size() + " quests)");
		for (String bad : untrackable) {
			System.out.println("       no row: " + bad);
		}
		for (String bad : notWritten) {
			System.out.println("       could not set: " + bad);
		}
		check(threw.isEmpty(), "every quest at every leg draws without throwing, on all four layers ("
			+ legs + " legs drawn)");
		// THE SHORTFALL IS ITS OWN CHECK NOW, not a number in another check's name. While it was
		// only ever printed, 225 legs went unswept for as long as nobody read the line.
		check(unreachable == 0, "and every leg in the table could be set to begin with ("
			+ unreachable + " unreachable)");
		VarbitType.field1760 = wasTable;
	}

	/** Whether a leg has a step that names something to DO, which is what the panel can show. */
	static boolean actionable(java.util.Map<?, ?> stage) {
		List<?> steps = jagex2.client.plugin.hub.Json.array(stage, "steps");
		if (steps == null) {
			return false;
		}
		for (Object s : steps) {
			if (!"note".equals(jagex2.client.plugin.hub.Json.string((java.util.Map<?, ?>) s, "kind", ""))) {
				return true;
			}
		}
		return false;
	}

	/** The shipped table, as the plugin's own loader reads it. */
	static List<?> questTable() throws Exception {
		java.io.InputStream in = QuestHelperPlugin.class.getResourceAsStream("/deathplateau/quests.json");
		java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
		byte[] chunk = new byte[8192];
		for (int n = in.read(chunk); n > 0; n = in.read(chunk)) {
			buf.write(chunk, 0, n);
		}
		in.close();
		Object root = jagex2.client.plugin.hub.Json.parse(new String(buf.toByteArray(), "UTF-8"));
		return jagex2.client.plugin.hub.Json.array((java.util.Map<?, ?>) root, "quests");
	}

	/**
	 * THE VARBIT TABLE THE CACHE WOULD HAVE GIVEN US, built from what the quests declare. Sized
	 * to the highest id in use; every other slot stays null, which is exactly what a cache that
	 * does not define an id looks like to varbitType(), so no OTHER varbit starts answering.
	 */
	static VarbitType[] varbitTable(List<?> all) {
		int widest = -1;
		for (Object element : all) {
			java.util.Map<?, ?> quest = (java.util.Map<?, ?>) element;
			if (quest.get("stageBits") != null) {
				widest = Math.max(widest, jagex2.client.plugin.hub.Json.integer(quest, "stageVarId", -1));
			}
		}
		VarbitType[] table = new VarbitType[widest + 1];
		for (Object element : all) {
			java.util.Map<?, ?> quest = (java.util.Map<?, ?>) element;
			java.util.Map<?, ?> bits = (java.util.Map<?, ?>) quest.get("stageBits");
			int id = jagex2.client.plugin.hub.Json.integer(quest, "stageVarId", -1);
			if (bits == null || id < 0 || id >= table.length) {
				continue;
			}
			VarbitType type = new VarbitType();
			type.field1762 = jagex2.client.plugin.hub.Json.integer(bits, "varp", -1);
			type.field1763 = jagex2.client.plugin.hub.Json.integer(bits, "lsb", -1);
			type.field1764 = jagex2.client.plugin.hub.Json.integer(bits, "msb", -1);
			table[id] = type;
		}
		return table;
	}

	// --------------------------------------------------------------- 15b

	/**
	 * AND EVERY VALUE BETWEEN THE LEGS, WHICH IS WHERE A PLAYER ACTUALLY SITS.
	 *
	 * WHY THE SWEEP ABOVE IS NOT ENOUGH. It walks the table's own legs - the 850 rows that exist -
	 * and a leg exists only where a sim's playthrough passed through that progress value. A
	 * `{ stage: N }` in a manifest ASSERTS the var equals N; it does not set it. So any value the
	 * content can write that no sim route crosses has no row at all, and the sweep above cannot
	 * reach it by construction: it iterates the rows. DeathPlateau-Bugs#16 counts 45 such (quest,
	 * value) pairs across 20 quests, 44 of them somewhere a player can genuinely be sitting.
	 *
	 * What the plugin does there is fall back: {@link QuestHelperPlugin#stageNow} takes the last
	 * leg whose `from` is at or below the value, so a player at a legless value is shown the
	 * PREVIOUS leg - already done, and wrong. That is a wording bug, and #16 owns it.
	 *
	 * This check is for the other half: whether being there makes an overlay THROW. Four open
	 * reports (#4, #10, #15, #17) are the helper turning itself off, which is PluginManager
	 * counting five throws out of an overlay and disabling the plugin, and a legless value is the
	 * one state no test had ever drawn. So every value from 0 to the quest's own `done` that is
	 * not already a leg gets written into the progress var and drawn on all four layers.
	 *
	 * A value the var is too narrow to hold reads back as something else and is counted, not
	 * drawn: a draw at a value the plugin never saw would be a draw of some other state.
	 */
	static void gapSweepTests() throws Exception {
		List<?> all = questTable();

		java.lang.reflect.Field errorsField = PluginManager.Entry.class.getDeclaredField("renderErrors");
		errorsField.setAccessible(true);
		java.lang.reflect.Field enabledField = PluginManager.Entry.class.getDeclaredField("enabled");
		enabledField.setAccessible(true);

		VarbitType[] wasTable = VarbitType.field1760;
		VarbitType.field1760 = varbitTable(all);

		PluginContext ctx = context();
		int drawn = 0;
		int tooNarrow = 0;
		List<String> threw = new ArrayList<String>();

		for (Object element : all) {
			java.util.Map<?, ?> quest = (java.util.Map<?, ?>) element;
			String name = jagex2.client.plugin.hub.Json.string(quest, "name", "");
			int varp = jagex2.client.plugin.hub.Json.integer(quest, "stageVarId", -1);
			boolean varbit = Boolean.TRUE.equals(quest.get("stageIsVarbit"));
			int done = jagex2.client.plugin.hub.Json.integer(quest, "done", 0);
			java.util.Map<?, ?> bits = (java.util.Map<?, ?>) quest.get("stageBits");
			List<?> stages = jagex2.client.plugin.hub.Json.array(quest, "stages");
			if (stages == null) {
				continue;
			}
			java.util.Set<Integer> legs = new java.util.HashSet<Integer>();
			for (Object stage : stages) {
				legs.add(jagex2.client.plugin.hub.Json.integer((java.util.Map<?, ?>) stage, "from", 0));
			}
			try {
				change();
				track(name);
			} catch (RuntimeException notListed) {
				continue;
			}
			for (int value = 0; value <= done; value++) {
				if (legs.contains(value)) {
					continue;
				}
				if (varbit) {
					writeBits(bits, value);
				} else if (varp >= 0 && varp < client.varps.length) {
					client.varps[varp] = value;
				}
				int saw = varp < 0 ? 0 : (varbit ? ctx.getVarbit(varp) : ctx.getVarp(varp));
				if (saw != value) {
					tooNarrow++;
					continue;
				}
				manager.onClientTick(ticks++);
				errorsField.setInt(entry, 0);
				enabledField.setBoolean(entry, true);
				screen();
				scene();
				sidebar();
				// THE CHATBOX GETS THE FALLBACK LEG'S OWN PICKS, because that is the leg the
				// plugin is about to show: optionFor is reached only through the leg stageNow
				// settles on, so putting any other quest's options up would test nothing.
				java.util.Map<?, ?> fallback = legBelow(stages, value);
				if (fallback != null) {
					openDialogue(optionsFor(fallback));
					chat();
					closeDialogue();
				} else {
					chat();
				}
				drawn++;
				if (errorsField.getInt(entry) > 0) {
					threw.add(name + " at value " + value + " (no leg; nearest below "
						+ (fallback == null ? "none"
							: String.valueOf(jagex2.client.plugin.hub.Json.integer(fallback, "from", 0)))
						+ ")");
				}
			}
		}

		for (String bad : threw) {
			System.out.println("       threw while drawing: " + bad);
		}
		check(threw.isEmpty(), "every value between the legs draws without throwing, on all four layers ("
			+ drawn + " legless values drawn)");
		check(tooNarrow == 0, "and every value up to done fits the var that carries it ("
			+ tooNarrow + " too narrow)");
		VarbitType.field1760 = wasTable;
	}

	/** The leg the plugin will fall back to at this value: the last whose `from` is at or below it. */
	static java.util.Map<?, ?> legBelow(List<?> stages, int value) {
		java.util.Map<?, ?> best = null;
		int bestFrom = -1;
		for (Object stage : stages) {
			java.util.Map<?, ?> st = (java.util.Map<?, ?>) stage;
			int from = jagex2.client.plugin.hub.Json.integer(st, "from", 0);
			if (from <= value && from > bestFrom) {
				best = st;
				bestFrom = from;
			}
		}
		return best;
	}


	/**
	 * One varbit's value, written into the parent varp's bits. The inverse of
	 * PluginContext.decodeVarbit, which is what reads it back two lines after every call to this.
	 */
	static void writeBits(java.util.Map<?, ?> bits, int value) {
		if (bits == null) {
			return;
		}
		int varp = jagex2.client.plugin.hub.Json.integer(bits, "varp", -1);
		int lsb = jagex2.client.plugin.hub.Json.integer(bits, "lsb", -1);
		int msb = jagex2.client.plugin.hub.Json.integer(bits, "msb", -1);
		if (varp < 0 || varp >= client.varps.length || lsb < 0 || msb < lsb || msb > 31) {
			return;
		}
		int mask = msb - lsb >= 31 ? -1 : (1 << (msb - lsb + 1)) - 1;
		client.varps[varp] = client.varps[varp] & ~(mask << lsb) | (value & mask) << lsb;
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
		// TWO QUESTS ALREADY IN PROGRESS, which is the state the guess used to fire in: with
		// every varp at zero there is nothing to guess and the check below would pass either
		// way - and with ONE there is a quest to follow and no guess in following it, which is
		// section 16's business.
		client.varps[GHOST_VARP] = 2;
		client.varps[MYREQUE_VARP] = 4;
		logOut();
		logIn();
		gameTicks(8);
		settle();

		// NOTHING IS GUESSED. Picking the first started-and-unfinished quest in table order meant
		// a player part-way through six quests was handed whichever sorted earliest.
		check(scene().isEmpty() && screen().isEmpty(),
			"with two quests in progress and none picked, nothing is drawn - no marker and no panel");
		client.varps[GHOST_VARP] = 0;
		client.varps[MYREQUE_VARP] = 0;
		gameTicks(1);

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
		//
		// FROM SOMEWHERE ELSE IN THE BAZAAR, deliberately: the walk is "go to the tile Ali is
		// standing on", and the player standing on it has already made it - which is a step the
		// plugin now takes as done, and is section 17's business. Eight tiles off, it is a walk
		// with somewhere to go.
		clearNpcs();
		stand(MID_X + 8, MID_Z + 8);
		gameTick();
		settle();
		check(!said(scene(), ALI_NAME), "with Ali gone from the scene, he is no longer named");
		check(painted() > 0, "...but the walk's own tile is marked, because the scene has it");
		check(said(screen(), "Go to the bazaar"), "...and the panel gives the walk instead");
		stand(MID_X, MID_Z);
		settle();
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

		// A FINISHED QUEST IS NOT TRACKED AT ALL, which is a stronger statement than the step list
		// being gone. The pin is dropped, so the page goes back to the list - the state it opens
		// in and the state the plugin is designed to sit in with nothing picked - and the quest's
		// own row there reads complete and offers Track again.
		//
		// It used to stay on the finished quest's last leg for good: the steps you had just done,
		// struck off, with nothing to say the quest was over, until the player thought to press
		// Change. That is what this section was renamed for.
		client.varps[GHOST_VARP] = 5;
		gameTicks(1);
		List<ConfigList.Row> rows = page();
		ConfigList.Row ghost = row(rows, GHOST);
		check(ghost != null, "completing the tracked quest puts the page back to the quest list");
		check(ghost != null && "complete".equals(ghost.detail),
			"...with the quest reading as complete there");
		check(ghost != null && "Track".equals(ghost.action),
			"...and offered to Track again, rather than still shown as Tracking");
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

	// ---------------------------------------------------------------- 14

	/**
	 * THE OPTION TO CLICK, marked in the chatbox.
	 *
	 * The sims have always recorded which option they pick - it is how a run gets through a
	 * conversation - and the exporter ships them, so 522 steps across 102 quests already say what
	 * to click. What was missing was the client end.
	 *
	 * The matching rule is the SIM's, because the sim wrote these picks: a number is the option's
	 * place in the list, words are matched by case-insensitive substring, and "||" separates
	 * alternatives for a question asked at random.
	 *
	 * The Restless Ghost opens on "Talk to Father Aereck" picking "looking for a quest", and
	 * Cook's Assistant opens on "Talk to Cook" picking "1" - a worded pick and a numeric one,
	 * both already fixtures here.
	 */
	static void dialogueTests() throws Exception {
		clearNpcs();
		change();
		track(GHOST);
		client.varps[GHOST_VARP] = 0;
		settle();

		check(chat().isEmpty() && chatPainted() == 0,
			"with no option box open, nothing is marked in the chatbox");

		// THE WORDED PICK. The option carrying the step's words is marked and the others are not,
		// and the words are only PART of the line, which is what makes it a substring match.
		openDialogue(new String[] {
			"Can you tell me about this church?",
			"I'm looking for a quest, actually.",
			"Nothing, sorry."
		});
		settle();
		int[] want = context().getDialogueOptionBounds(1);
		check(want != null, "the option box gives the second line its own bounds");
		check(onlyInside(want), "the step's own option is the one marked, and nothing else is");

		// NONE OF THESE IS THE ANSWER, so the box is left alone rather than guessed at.
		openDialogue(new String[] { "Nice weather.", "Goodbye.", "Who are you?" });
		settle();
		check(chatPainted() == 0, "an option box with no answer of the step's in it is not marked");

		// THE NUMERIC PICK is a place in the list, not words: Cook's Assistant picks "1", and the
		// first line is marked whatever it happens to say.
		change();
		track(COOK);
		client.varps[COOK_VARP] = 0;
		openDialogue(new String[] { "What's wrong?", "I'm looking for a quest, actually.", "Bye." });
		settle();
		int[] first = context().getDialogueOptionBounds(0);
		check(first != null && onlyInside(first),
			"a numeric pick marks the line in that place, not the one whose words match");

		// AND THE TICK ASKS FOR THE REPAINT, because the chatbox raster is kept between frames.
		int before = client.redrawChatback ? 1 : 0;
		client.redrawChatback = false;
		manager.onGameTick();
		check(client.redrawChatback, "with an option to mark, the plugin asks for the chatbox repaint (" + before + ")");
		closeDialogue();
		settle();
		client.redrawChatback = false;
		manager.onGameTick();
		check(client.redrawChatback, "...and once more when the box closes, so the mark is rubbed out");
		client.redrawChatback = false;
		manager.onGameTick();
		check(!client.redrawChatback, "...and not every tick after that");

		closeDialogue();
		change();
		track(FEUD);
		settle();
	}

	/**
	 * A "Select an Option" box with these lines, built the way the client's own interfaces are.
	 *
	 * The ids are the CLIENT's - an option's component is the open interface's id plus one plus
	 * its place, which is how its number-key hotkey finds one - so this cannot drift from the
	 * thing it is standing in for.
	 */
	static void openDialogue(String[] lines) {
		int root = lines.length == 2 ? Client.MULTI2_INTERFACE_ID
			: lines.length == 3 ? Client.MULTI3_INTERFACE_ID
			: lines.length == 4 ? Client.MULTI4_INTERFACE_ID : Client.MULTI5_INTERFACE_ID;
		jagex2.config.Component box = new jagex2.config.Component();
		box.id = root;
		box.type = 0;
		box.width = 479;
		box.height = 96;
		box.children = new int[lines.length];
		box.childX = new int[lines.length];
		box.childY = new int[lines.length];
		for (int i = 0; i < lines.length; i++) {
			jagex2.config.Component line = new jagex2.config.Component();
			line.id = root + 1 + (i + 1);
			line.type = 4;
			line.text = lines[i];
			line.width = 300;
			line.height = 14;
			jagex2.config.Component.types[line.id] = line;
			box.children[i] = line.id;
			box.childX[i] = 20;
			box.childY[i] = 20 + i * 16;
		}
		jagex2.config.Component.types[root] = box;
		client.chatInterfaceId = root;
	}

	static void closeDialogue() {
		client.chatInterfaceId = -1;
	}

	/**
	 * A "Select an Option" box for one leg, built out of that leg's own picks.
	 *
	 * Between two and five lines, because those are the boxes the client has. The leg's picks
	 * come first so a step that picks is matched rather than missed; the rest is filler, which
	 * is what an unrelated option looks like and is also what makes the no-match path run.
	 */
	static String[] optionsFor(java.util.Map<?, ?> stage) {
		List<String> lines = new ArrayList<String>();
		for (Object step : jagex2.client.plugin.hub.Json.array(stage, "steps")) {
			for (Object pick : jagex2.client.plugin.hub.Json.array((java.util.Map<?, ?>) step, "picks")) {
				String text = String.valueOf(pick);
				// A worded pick may offer alternatives for a question asked at random; the box a
				// player sees carries one of them, so the first is what goes up.
				int bar = text.indexOf("||");
				if (bar >= 0) {
					text = text.substring(0, bar);
				}
				if (!text.trim().isEmpty() && lines.size() < 4) {
					lines.add(text);
				}
			}
		}
		lines.add("Nothing, I'm fine thanks.");
		if (lines.size() < 2) {
			lines.add("I'll come back later.");
		}
		return lines.toArray(new String[lines.size()]);
	}

	/** One chatbox-layer frame: what the option mark drew. */
	static List<Drawn> chat() {
		return frame(Overlay.LAYER_CHAT);
	}

	/** The pixels the chatbox pass left behind. */
	static int chatPainted() {
		chat();
		int n = 0;
		for (int i = 0; i < pixels.length; i++) {
			if (pixels[i] != 0) {
				n++;
			}
		}
		return n;
	}

	/**
	 * Whether the chatbox pass painted inside this box and nowhere outside it.
	 *
	 * The box's own coordinates decide, so this says "that line and no other" without reading
	 * back the number the mark was drawn at.
	 */
	static boolean onlyInside(int[] box) {
		chat();
		int in = 0, out = 0;
		for (int y = 0; y < H; y++) {
			for (int x = 0; x < W; x++) {
				if (pixels[y * W + x] == 0) {
					continue;
				}
				boolean inside = x >= box[0] && x < box[0] + box[2] && y >= box[1] && y < box[1] + box[3];
				if (inside) {
					in++;
				} else {
					out++;
				}
			}
		}
		return in > 0 && out == 0;
	}

	// ---------------------------------------------------------------- 13

	/**
	 * A STEP WHOSE TARGET COMES IN A CROWD, which is what a player reported on The Feud.
	 *
	 * Pollnivneach has three villagers to pickpocket, Scorpion Catcher three scorpions, Plague
	 * City two Edmonds. The sim met one of them and the table recorded that one, so the marker
	 * pointed across town at the id it had while an identical one stood next to the player.
	 *
	 * The step now carries the ids the SERVER would run the same script on for its op, worked out
	 * in the exporter where the scripts are loaded. Scorpion Catcher is the quest to check it on
	 * because its own legs name different scorpions - 385 in the second leg, 386 in the third -
	 * so the table itself says these are interchangeable, and because its progress is a plain
	 * varp this harness can write.
	 */
	static void familyTests() throws Exception {
		clearNpcs();
		change();
		track(SCORPION);
		client.varps[SCORPION_VARP] = 2;

		// THE THIRD SCORPION, where the leg names the first.
		spawn(SCORPION_NAME, SCORPION_THIRD, MID_X, MID_Z);
		settle();
		check(said(screen(), "Use Scorpion cage on " + SCORPION_NAME),
			"a step is the step with another of its family in front of you, not only its own id");
		check(said(scene(), SCORPION_NAME), "...and that one is what the marker points at");
		check(painted() > 0, "...with a box on its tile");

		// AND NOT ANY NPC AT ALL, which is what a family that swallowed the whole cache would
		// come to: a passer-by in no step of the leg, sharing no script with one.
		clearNpcs();
		spawn("Man", 1, MID_X, MID_Z);
		settle();
		check(!said(scene(), "Man"), "an npc of no family of the leg is not the step");
		check(!said(screen(), "Use Scorpion cage on " + SCORPION_NAME),
			"...and the panel falls back to the leg rather than claiming he is one");

		// The id the leg names itself still works, which is the case that worked before.
		clearNpcs();
		spawn(SCORPION_NAME, SCORPION_FIRST, MID_X, MID_Z);
		settle();
		check(said(screen(), "Use Scorpion cage on " + SCORPION_NAME),
			"the step's own id is still the step");

		clearNpcs();
		client.varps[SCORPION_VARP] = 0;
		change();
		track(FEUD);
		settle();
	}

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
	 *
	 * THE TILE IS IN THE WORD TOO, where the graphics picker puts it: handleViewportOptions reads
	 * the tile out of the word it was handed and asks the scene what is standing there, so a word
	 * without it builds a menu for a loc at 0,0 and finds nothing. {@link #pickedWord} hands this
	 * same word back to the menu, which is how a click here is the click the game makes.
	 */
	static void putLoc(int tileX, int tileZ, int id) {
		Square square = client.scene.field1018[0][tileX][tileZ];
		if (square == null) {
			square = new Square(0, tileX, tileZ);
			client.scene.field1018[0][tileX][tileZ] = square;
		}
		Sprite sprite = new Sprite();
		sprite.field88 = (id << 14) | (tileZ << 7) | tileX | 2 << 29;
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

	// ---------------------------------------------------------------- 16

	/**
	 * THE QUEST YOU ARE ALREADY IN THE MIDDLE OF.
	 *
	 * WHAT A PLAYER REPORTED: "quest helper doesnt track if you already started the quest". The
	 * page followed the quest the player pressed Track on and nothing else, and the pick was a
	 * plain field - so it was lost every time the client closed. Somebody half-way through a
	 * quest opened the helper and got a list of 108 of them, the one thing it knew for certain
	 * about them ignored.
	 *
	 * IT STILL MUST NOT GUESS, which is the other half. The guess this replaced took the first
	 * started-and-unfinished quest in table order - alphabetical by id - and showed a player
	 * part-way through six quests Fight Arena because "arena" sorts early. So the two ways in
	 * are evidence instead: a quest whose progress MOVED while the plugin was watching, and a
	 * single quest underway with nothing to choose it against. Two underway and no movement is
	 * the list, as before.
	 *
	 * The quests here are the ones whose progress is a plain varp. The Feud's is a varbit, which
	 * needs a cache to decode and reads zero outside the sweep above.
	 */
	static void followTests() throws Exception {
		clearNpcs();
		emptyBag();
		wipeQuests();
		setPin("");
		gameTicks(8);
		check(followed() == null, "with no quest begun, the page is the list and follows nothing");
		check(screen().isEmpty(), "...and there is no panel on screen to follow it with");

		// ONE QUEST UNDERWAY. Nothing pressed, and nothing moving: logged out and in again with
		// the value already written, which is the state a player is in every time they log in
		// part-way through something. A varp written under a RUNNING plugin is a move, and would
		// be answered by the rule below this one rather than by this one.
		client.varps[GHOST_VARP] = 1;
		logOut();
		logIn();
		gameTicks(2);
		check(GHOST.equals(followed()),
			"a quest already under way is picked up without the player saying so again");
		check(!screen().isEmpty(), "...and its step is on screen (" + followed() + ")");

		// TWO UNDERWAY AND NOTHING TO CHOOSE BETWEEN THEM, which is where the old guess went
		// wrong. The page waits on the list rather than picking the one that sorts first.
		//
		// LOGGED OUT AND IN AGAIN, because that is what "nothing has moved" means: the plugin
		// keeps each quest's value from the tick before, and a harness that writes two varps
		// under a running plugin has moved them both.
		wipeQuests();
		setPin("");
		client.varps[GHOST_VARP] = 1;
		client.varps[MYREQUE_VARP] = 4;
		logOut();
		logIn();
		gameTicks(8);
		check(followed() == null, "two quests under way and nothing moving: the list, not a guess");

		// AND THE VALUES ARRIVE AFTER THE WORLD DOES. The server sends them over the ticks after
		// login, so every started quest looks like it has just moved; those first ticks only
		// write them down. Without that, logging in part-way through two quests follows whichever
		// varp happened to arrive last.
		wipeQuests();
		setPin("");
		logOut();
		logIn();
		gameTicks(1);
		client.varps[GHOST_VARP] = 1;
		client.varps[MYREQUE_VARP] = 4;
		gameTicks(2);
		check(followed() == null, "the values arriving just after login are not mistaken for progress");

		// A MOVE IS THE ANSWER. Doing anything at all in one of them says which one is being
		// played, whatever else is half-finished.
		gameTicks(8);
		client.varps[MYREQUE_VARP] = 5;
		gameTicks(1);
		check(MYREQUE.equals(followed()), "the quest whose progress moves is the one followed");
		client.varps[GHOST_VARP] = 2;
		gameTicks(1);
		check(GHOST.equals(followed()), "...and moving the other one moves the page with it");

		// STARTING A QUEST is a move like any other: nothing to something, and the first step is
		// there to read without opening the list at all.
		wipeQuests();
		setPin("");
		logOut();
		logIn();
		gameTicks(8);
		check(followed() == null, "nothing under way again");
		client.varps[MYREQUE_VARP] = 1;
		gameTicks(1);
		check(MYREQUE.equals(followed()), "starting a quest follows it from its first step");
		List<ConfigList.Row> rows = page();
		check(rows.size() > 1 && "Change".equals(rows.get(0).action),
			"...with the leg's steps under it (" + rows.size() + " rows)");

		// A QUEST ENDING IS NOT A QUEST BEING PLAYED. Its var moves like any other - to the
		// value that means complete - and the page has nothing left to say about it.
		client.varps[GHOST_VARP] = 1;
		gameTicks(1);
		client.varps[MYREQUE_VARP] = 2;
		gameTicks(1);
		check(MYREQUE.equals(followed()), "following the quest in progress");
		client.varps[GHOST_VARP] = 5;
		gameTicks(1);
		check(MYREQUE.equals(followed()), "a quest reaching its end does not take the page to it");

		// AND THE PLAYER'S OWN PICK IS SAVED. A pin in a field was dropped when the plugin
		// stopped, and the plugin stops every time the client closes.
		change();
		track(GHOST);
		check(GHOST.equals(followed()), "pressing Track follows that quest");
		check(store().indexOf("questhelper.tracked=restlessghost") >= 0,
			"...and writes the choice where the next client start will read it");

		// A MOVE IN ANOTHER QUEST STILL WINS, and it is the rule the page is easiest to explain
		// with: it follows what you are doing. Picking from the list is for the quests you are
		// NOT in the middle of.
		client.varps[MYREQUE_VARP] = 3;
		gameTicks(1);
		check(MYREQUE.equals(followed()), "a move in another quest takes the page with it");

		// ---------------------------------------------------------------- two entries, one varp
		//
		// A MOVE THAT NAMES TWO QUESTS NAMES NEITHER. Hazeel Cult is in the table twice, once per
		// side, and both entries read hazeelcultquest - so every step of either side moves both of
		// them. The rule used to keep the LAST one it saw, which is table order, which is the
		// Carnillean side: a player on Hazeel's side was dragged to the other guide on every step
		// of the quest. DeathPlateau-Bugs#84.
		wipeQuests();
		setPin("");
		logOut();
		logIn();
		gameTicks(8);
		track(HAZEEL_EVIL);
		check(HAZEEL_EVIL.equals(followed()), "a player can pick the Hazeel side");
		client.varps[HAZEEL_VARP] = 3;
		gameTicks(1);
		check(HAZEEL_EVIL.equals(followed()),
			"...and a step of it does not throw them at the other side's guide");
		client.varps[HAZEEL_VARP] = 4;
		gameTicks(1);
		check(HAZEEL_EVIL.equals(followed()), "...nor does the next step");
		client.varps[HAZEEL_VARP] = 5;
		gameTicks(1);
		check(HAZEEL_EVIL.equals(followed()), "...nor the one after that");

		// AND THE OTHER SIDE IS NOT PRIVILEGED EITHER, which is what a fix that simply reversed
		// the loop would look like: it would read as fixed from Hazeel's side and break the
		// Carnillean one instead.
		change();
		track(HAZEEL_GOOD);
		check(HAZEEL_GOOD.equals(followed()), "a player can pick the Carnillean side");
		client.varps[HAZEEL_VARP] = 6;
		gameTicks(1);
		check(HAZEEL_GOOD.equals(followed()), "...and a step of it leaves them on it");

		// AND ABSTAINING IS NOT GIVING UP. An ambiguous move must not stop the page following an
		// ordinary quest afterwards - the was[] values are written down either way, so the same
		// ambiguity is not re-examined for ever.
		client.varps[MYREQUE_VARP] = 4;
		gameTicks(1);
		check(MYREQUE.equals(followed()), "an unambiguous move still takes the page, after one that was not");

		// AND WITH NOTHING PICKED, the page says so rather than choosing a side for somebody.
		// Before, it chose - and chose wrong for half of all players.
		wipeQuests();
		setPin("");
		logOut();
		logIn();
		gameTicks(8);
		client.varps[HAZEEL_VARP] = 3;
		gameTicks(2);
		check(followed() == null, "a step of Hazeel Cult with no side picked waits on the list");

		wipeQuests();
		setPin("");
		gameTicks(8);
	}

	// ---------------------------------------------------------------- 17

	/**
	 * THE STEP YOU HAVE JUST TAKEN, which nothing in the game writes down.
	 *
	 * WHAT A PLAYER REPORTED: it "still needs to be showing your next step, immediately after
	 * finishing one". The quest varp moves once a LEG, and inside one the only traces were what
	 * the quest put in the bag and what its own flags read - so a talk, which leaves neither,
	 * could not be seen to have happened at all. Talk to the first of Myreque's five people and
	 * the panel went on naming him, because he was still standing in front of you, until the
	 * varp caught up a leg later.
	 *
	 * SO THE CLICK IS THE RECORD. Clicking the step the panel names is the player saying they are
	 * doing it; a conversation that opens and then closes is that talk finishing; arriving at the
	 * thing you clicked is a loc finishing; and a click that led nowhere is written off rather
	 * than counted.
	 *
	 * THE CLICKS HERE ARE THE GAME'S OWN. The menu row is built by the client's menu builder and
	 * read back by the client's resolver, which are the two places the list of action ids lives;
	 * a test that wrote the number itself would agree with both of them and notice neither going
	 * wrong.
	 */
	static void stepTests() throws Exception {
		clearNpcs();
		emptyBag();
		wipeQuests();
		stand(MID_X, MID_Z);
		track(MYREQUE);
		client.varps[MYREQUE_VARP] = 4;

		// FIVE PEOPLE TO TALK TO, in order, and the first two of them standing here. Harold is
		// the step; Radigad is the one after it.
		spawn(HAROLD_NAME, HAROLD, MID_X, MID_Z);
		spawn(RADIGAD_NAME, RADIGAD, MID_X + 2, MID_Z);
		gameTicks(1);
		settle();
		check(said(screen(), "Talk to " + HAROLD_NAME), "the leg opens on the first of its five people");

		// SOMETHING THAT IS NOT THE STEP, first: the second of the five, talked to out of turn.
		// Three steps of this leg are still ahead of the player and a page that counted any click
		// at all would have skipped the two in between in silence.
		clickNpc(RADIGAD);
		openDialogue(new String[] { "Yes.", "No." });
		gameTicks(1);
		closeDialogue();
		gameTicks(1);
		settle();
		check(said(screen(), "Talk to " + HAROLD_NAME),
			"a conversation with somebody further down the leg is not this step");

		int op = clickNpc(HAROLD);
		check(op == 1, "the menu's own Talk-to row reads back as op 1 on an npc (" + op + ")");
		gameTicks(1);
		settle();
		check(said(screen(), "Talk to " + HAROLD_NAME),
			"clicking him is not the talk yet: the panel waits for the conversation");

		openDialogue(new String[] { "Yes.", "No." });
		gameTicks(1);
		settle();
		check(said(screen(), "Talk to " + HAROLD_NAME),
			"...and holds his step while the conversation is open, which is what keeps the option marked");
		check(liveRow().indexOf(HAROLD_NAME) >= 0,
			"...the page agreeing that his is still the step to do now (" + liveRow() + ")");

		closeDialogue();
		gameTicks(1);
		settle();
		check(said(screen(), "Talk to " + RADIGAD_NAME),
			"the conversation closing moves the panel to the next person, with the first still standing there");
		check(said(scene(), RADIGAD_NAME), "...and the marker moves to him too");
		ConfigList.Row harold = row(page(), "Talk to " + HAROLD_NAME + ".");
		check(harold != null && "done".equals(harold.value),
			"...and the page strikes his off as done (" + (harold == null ? "no row" : harold.value) + ")");

		// A CLICK THAT LED NOWHERE. No conversation ever opened - clicked the wrong thing, walked
		// away, interrupted - so the panel is where it was rather than a step further on.
		clickNpc(RADIGAD);
		gameTicks(PATIENCE);
		settle();
		check(said(screen(), "Talk to " + RADIGAD_NAME),
			"a click with no conversation after it is written off, not counted");
		openDialogue(new String[] { "Yes.", "No." });
		gameTicks(1);
		closeDialogue();
		gameTicks(1);
		settle();
		check(said(screen(), "Talk to " + RADIGAD_NAME),
			"...and a conversation long afterwards does not finish it for him");

		// AND IT IS THIS CHARACTER'S PROGRESS. Logging out forgets what was watched; the quest
		// to follow is a saved setting and stays.
		clickNpc(RADIGAD);
		openDialogue(new String[] { "Yes.", "No." });
		gameTicks(1);
		closeDialogue();
		gameTicks(1);
		settle();
		check(!said(screen(), "Talk to " + RADIGAD_NAME), "talking to the second moves the panel on again");
		logOut();
		logIn();
		gameTicks(8);
		settle();
		check(MYREQUE.equals(followed()), "logging out keeps the quest being followed");
		check(said(screen(), "Talk to " + HAROLD_NAME),
			"...and forgets the steps it watched, because the next player may be somebody else");

		// ARRIVING IS TAKING A WALK STEP, and it needs no click at all: The Feud's first leg is
		// "go to the tile Ali is standing on, talk to Ali", and a player standing on it has made
		// the walk whether or not Ali has loaded yet.
		clearNpcs();
		track(FEUD);
		client.sceneBaseTileX = BAZAAR_X - MID_X;
		client.sceneBaseTileZ = BAZAAR_Z - MID_Z;
		stand(MID_X + 8, MID_Z + 8);
		gameTicks(1);
		settle();
		check(said(screen(), "Go to the bazaar"), "away from the tile, the walk is still the step");
		stand(MID_X, MID_Z);
		gameTicks(1);
		settle();
		check(!said(screen(), "Go to the bazaar"), "standing on it, the walk is behind you");
		check(said(screen(), "Talk to " + ALI_NAME), "...and the panel has moved to what is next");

		// A LOC: clicked, walked to, done - and a conversation is not involved at all.
		clearNpcs();
		track(ELEMENTAL);
		client.sceneBaseTileX = BOOKCASE_X - MID_X;
		client.sceneBaseTileZ = BOOKCASE_Z - MID_Z;
		stand(MID_X, MID_Z);
		primeLocs("Dusty bookcase");
		client.scene = new World3D(client.levelHeightmap, 104, 4, 104);
		putLoc(MID_X, MID_Z, BOOKCASE);
		gameTicks(1);
		settle();
		check(said(screen(), "Search " + BOOKCASE_NAME), "the bookcase is the step, standing at it");

		// CLICKED FROM ACROSS THE ROOM, which is a walk and then a search. A loc opens no
		// conversation to wait for, so arriving at it is the only end there is - and until then
		// the step is still the step.
		stand(MID_X + 8, MID_Z + 8);
		int locOp = clickLoc(MID_X, MID_Z);
		check(locOp == 1, "the menu's own Search row reads back as op 1 on a loc (" + locOp + ")");
		gameTicks(1);
		settle();
		check(said(screen(), "Search " + BOOKCASE_NAME),
			"clicking it from across the room is not the search yet");

		// AND A CONVERSATION DOES NOT STAND IN FOR WALKING THERE. Nobody talks to a bookcase
		// from eight tiles away, so a chatbox that opens on the way - a level-up, most often -
		// is somebody else's news.
		openDialogue(new String[] { "Yes.", "No." });
		gameTicks(1);
		closeDialogue();
		gameTicks(1);
		settle();
		check(said(screen(), "Search " + BOOKCASE_NAME),
			"...and a conversation on the way there is not the search either");

		stand(MID_X, MID_Z);
		gameTicks(1);
		settle();
		check(!said(screen(), "Search " + BOOKCASE_NAME), "searching it is the step done");
		check(said(screen(), "Go out while the door is still open"),
			"...and the panel gives the step after it");

		client.scene = null;
		client.sceneBaseTileX = BAZAAR_X - MID_X;
		client.sceneBaseTileZ = BAZAAR_Z - MID_Z;
		clearNpcs();
		wipeQuests();
		setPin("");
		gameTicks(8);
	}

	// ---------------------------------------------------------------- 18

	/**
	 * THE SHAPES IN THE TABLE THE PANEL USED TO FALL OVER, each read off the table itself rather
	 * than invented here: a tile with no floor, two legs at one value, and a leg with no steps.
	 *
	 * NONE OF THESE WERE VISIBLE FROM A QUEST PICKED BY HAND. Every section above chooses a
	 * quest, a leg and a scene, and all three of these are quests nobody had chosen - which is
	 * the same gap the sweep in section 15 exists for, one level further in: the sweep proves
	 * nothing throws, and a panel that says "No step recorded for this stage" throws nothing.
	 */
	static void shapeTests() throws Exception {
		clearNpcs();
		emptyBag();
		wipeQuests();

		// LOGGED OUT FIRST, because section 17 walked The Feud's first step and the plugin
		// remembers that for as long as the character is logged in - which is the point of it.
		// This section is about a player who has done none of it.
		logOut();
		logIn();

		// A TILE WITH NO FLOOR ON IT. 2677 of the table's tiles are a bare pair of coordinates,
		// because the sim stood there with a `go [x,z]` that never named a level - and the plugin
		// read the missing third number as the ground floor, so every one of them was thrown away
		// the moment the player went upstairs. The Feud's first walk is one of them.
		track(FEUD);
		client.sceneBaseTileX = BAZAAR_X - MID_X;
		client.sceneBaseTileZ = BAZAAR_Z - MID_Z;
		client.currentLevel = 1;
		stand(MID_X + 8, MID_Z + 8);
		gameTicks(1);
		settle();
		check(said(screen(), "Go to the bazaar"), "a step whose tile names no floor is still the step upstairs");
		check(painted() > 0, "...and its tile is still marked, rather than dropped for not being plane 0");
		stand(MID_X, MID_Z);
		gameTicks(1);
		settle();
		check(!said(screen(), "Go to the bazaar"), "...and standing on it upstairs is still arriving");
		client.currentLevel = 0;

		// AND A TILE THAT DOES NAME A FLOOR IS STILL HELD TO IT, which is the other half of the
		// same fix: 849 of the table's tiles say "plane 0" out loud, and a marker on the ground
		// floor is no use to somebody standing on the first. Fight Arena's walk to Lady Servil
		// names it.
		clearNpcs();
		wipeQuests();
		logOut();
		logIn();
		track(ARENA);
		client.sceneBaseTileX = SERVIL_X - MID_X;
		client.sceneBaseTileZ = SERVIL_Z - MID_Z;
		stand(MID_X + 8, MID_Z + 8);
		gameTicks(1);
		settle();
		check(said(screen(), "Go to Lady Servil"), "the leg opens on the walk to Lady Servil");
		check(painted() > 0, "...and on her own floor the tile is marked");
		client.currentLevel = 1;
		settle();
		check(painted() == 0, "...but a floor up it is not, because that tile said which floor it is on");
		check(said(screen(), "Go to Lady Servil"), "...while the panel still says where to go");
		client.currentLevel = 0;

		// TWO LEGS AT ONE STAGE VALUE. Monkey Madness records nine steps and then five, both at
		// stage 12, and the later of them used to win - so the nine were somewhere no player
		// could reach.
		clearNpcs();
		wipeQuests();
		track(MONKEY);
		client.varps[MONKEY_VARP] = 12;
		gameTicks(1);
		settle();
		check(said(screen(), "Grand Tree"), "of two legs at one stage value, the first is the one shown");
		check(!said(screen(), "Go to Lumdo"), "...and not the later one that used to hide it");
		List<ConfigList.Row> rows = page();
		check(rows.size() > 9, "...with its own steps under it on the page (" + rows.size() + " rows)");

		// A LEG THAT ENDS IN AN EXPLANATION. Death Plateau's stage 50 is "talk to Harold, talk to
		// Harold again, roll the dice" - and with both talks behind you the only thing left is
		// the note, which the panel cannot name. It used to say "No step recorded for this
		// stage" at the exact moment the player had done everything right.
		clearNpcs();
		wipeQuests();
		logOut();
		logIn();
		track(PLATEAU);
		client.varps[PLATEAU_VARP] = 50;
		spawn(DICE_HAROLD_NAME, DICE_HAROLD, MID_X, MID_Z);
		gameTicks(1);
		settle();
		check(said(screen(), "Talk to " + DICE_HAROLD_NAME), "the leg opens on the first talk with Harold");
		for (int i = 0; i < 2; i++) {
			clickNpc(DICE_HAROLD);
			openDialogue(new String[] { "Yes.", "No." });
			gameTicks(1);
			closeDialogue();
			gameTicks(1);
			settle();
		}
		check(!said(screen(), "No step recorded"),
			"with both talks done, the note left behind is not a step and does not empty the panel");
		check(said(screen(), "Read Iou"), "...the next leg is what is next (" + firstLine() + ")");

		// A LEG WITH NO STEPS IN IT. Big Chompy's stage 35 is a wait while Rantz misses, so there
		// is nothing to click and nothing for the table to say - and an empty leg used to be the
		// answer, which read as "No step recorded for this stage".
		wipeQuests();
		track(CHOMPY);
		client.varps[CHOMPY_VARP] = 35;
		gameTicks(1);
		settle();
		check(!said(screen(), "No step recorded"), "a leg with no steps in it does not leave the panel empty");
		check(said(screen(), "Rantz"), "...it gives what is next instead (Rantz, two steps later)");

		// AND THE ONE LEG THE PANEL STILL CANNOT NAME, which is worth pinning rather than hiding.
		// Everything above rescues a leg whose steps run out by walking the panel on to the next
		// leg, and that only works while there IS a next leg. quest_hero_run.ts stops at stage 11
		// on purpose - the candlestick needs a Phoenix player to kill Grip, so one player's route
		// ends there - which leaves leg 11 bare, 0 steps and 0 headings, with nothing after it.
		// A player at 11, 12 or 13 therefore reads "No step recorded for this stage."
		//
		// That is the LAST state in the whole table that does it, and it is the only shape left
		// that could have a player saying the helper shows them nothing - the four reports on
		// DeathPlateau-Bugs#4, #10, #15 and #17. It is not a throw: section 15b draws every
		// legless value on all four overlays without one.
		wipeQuests();
		track(HERO);
		client.varps[HERO_VARP] = 11;
		gameTicks(1);
		settle();
		check(said(screen(), "No step recorded"),
			"Heroes' Quest 11 is bare, and the panel says so rather than going blank");
		client.varps[HERO_VARP] = 13;
		gameTicks(1);
		settle();
		check(said(screen(), "No step recorded"), "...and 13 falls back to it and reads the same");

		// THE RATCHET ON IT. Counted from the table rather than driven, because the point is that
		// these are rare and deliberate: a leg with nothing to do and no later leg to walk on to.
		// If somebody fixes one this fails and the expectation comes down; if somebody adds one,
		// it fails and names it. Either way it cannot arrive quietly.
		//
		// IT CAUGHT THE SECOND ONE, which is the entry below. Both are the same shape and the
		// shape is not a bug: Heroes' Quest is a TWO-PLAYER quest, and each row of it is one
		// player's route, so each row ends where that player runs out. The solo part stops at the
		// deputy's post (11) and the Phoenix side at Charlie's secret door (4) - stage 5 is Grip
		// dead and needs the other gang. A tail leg with nothing clickable is the honest panel for
		// "you cannot go further alone", and "No step recorded" is what it says.
		//
		// Phoenix 4 arrived with DeathPlateau-Engine#128 and was given a wiki heading and
		// deliberately no step, so legNow walks the panel on rather than parking it - the same
		// treatment as Fight Arena 8. The engine's CI cannot see this check, so the pin here is
		// the only thing that connects the two repositories on it.
		List<String> bare = new ArrayList<String>();
		for (Object element : questTable()) {
			java.util.Map<?, ?> quest = (java.util.Map<?, ?>) element;
			List<?> stages = jagex2.client.plugin.hub.Json.array(quest, "stages");
			if (stages == null) {
				continue;
			}
			for (int i = 0; i < stages.size(); i++) {
				if (actionable((java.util.Map<?, ?>) stages.get(i))) {
					continue;
				}
				boolean laterHasOne = false;
				for (int j = i + 1; j < stages.size(); j++) {
					laterHasOne = laterHasOne || actionable((java.util.Map<?, ?>) stages.get(j));
				}
				if (!laterHasOne) {
					bare.add(jagex2.client.plugin.hub.Json.string(quest, "name", "?") + " "
						+ jagex2.client.plugin.hub.Json.integer((java.util.Map<?, ?>) stages.get(i), "from", 0));
				}
			}
		}
		for (String b : bare) {
			System.out.println("       panel cannot name: " + b);
		}
		java.util.Collections.sort(bare);
		List<String> wantBare = new ArrayList<String>();
		wantBare.add("HEROES' QUEST (Phoenix side) 4");
		wantBare.add("HEROES' QUEST (solo part) 11");
		check(bare.equals(wantBare),
			"and they are the only two legs in the table the panel cannot name (" + bare.size() + ")");

		clearNpcs();
		wipeQuests();
		setPin("");
		gameTicks(8);
	}

	// ---------------------------------------------------------------- the harness for 16 and 17

	/** Where the player is standing, which is what "you have walked there" is measured against. */
	static void stand(int sceneTileX, int sceneTileZ) {
		Client.localPlayer.field1157 = sceneTileX * 128;
		Client.localPlayer.field1158 = sceneTileZ * 128;
	}

	/**
	 * Every quest back to not started.
	 *
	 * A varbit is a window on a varp, so zeroing the varps zeroes every quest in the table -
	 * which is what makes "two quests under way" a fact about these two rather than about
	 * whatever the section before this one left behind.
	 */
	static void wipeQuests() {
		java.util.Arrays.fill(client.varps, 0);
	}

	static void gameTick() {
		manager.onGameTick();
	}

	static void gameTicks(int n) throws Exception {
		for (int i = 0; i < n; i++) {
			manager.onGameTick();
		}
		manager.onClientTick(ticks++);
	}

	/** Logs out and back in the way the manager notices it: the client's own two fields. */
	static void logOut() {
		client.ingame = false;
		manager.onClientTick(ticks++);
	}

	static void logIn() {
		client.ingame = true;
		manager.onClientTick(ticks++);
	}

	/** The first line the on-screen panel drew, for a check that wants to say what it saw. */
	static String firstLine() {
		List<Drawn> drawn = screen();
		return drawn.isEmpty() ? "nothing drawn" : strip(drawn.get(0).text);
	}

	/** The label of the row the page marks as the step to do now, or "". */
	static String liveRow() {
		for (ConfigList.Row row : page()) {
			if ("now".equals(row.value)) {
				return row.label;
			}
		}
		return "";
	}

	/** The quest the page is following, read off the page, or null while it shows the list. */
	static String followed() {
		List<ConfigList.Row> rows = page();
		if (rows.isEmpty() || !"Change".equals(rows.get(0).action)) {
			return null;
		}
		return rows.get(0).label;
	}

	/** Writes the plugin's saved pin, which is the only way to put the page back to none. */
	static void setPin(String questId) {
		for (jagex2.client.plugin.PluginConfig.Item item : entry.getConfig().getItems()) {
			if ("tracked".equals(item.key)) {
				entry.getConfig().set(item, questId);
			}
		}
	}

	/** What the plugin store on disk says, which is what the next client start will read. */
	static String store() {
		// The directory is the client's own answer, not this file's guess at it; the file's name
		// is PluginManager's and private, so it is the one thing written down here.
		java.io.File file = new java.io.File(sign.signlink.findcachedir() + "plugins.dat");
		if (!file.exists()) {
			return "";
		}
		try {
			byte[] all = new byte[(int) file.length()];
			java.io.DataInputStream in = new java.io.DataInputStream(new java.io.FileInputStream(file));
			in.readFully(all);
			in.close();
			return new String(all, "UTF-8");
		} catch (Exception error) {
			return "";
		}
	}

	/**
	 * Clicks an npc, through the client's own menu builder and resolver. Returns the op slot the
	 * resolver read back, so the test can say the two agree.
	 *
	 * The ops are put on the type here rather than in {@link #spawn}: an npc needs a Talk-to word
	 * and the interactable flag before the menu will offer anything at all, and the sections
	 * above are about what is DRAWN over an npc, which needs neither.
	 */
	static int clickNpc(int npcId) {
		int index = -1;
		for (int i = 0; i < client.npcCount; i++) {
			ClientNpc npc = client.npcs[client.npcIds[i]];
			if (npc != null && npc.field1370 != null && (int) npc.field1370.field1431 == npcId) {
				index = client.npcIds[i];
			}
		}
		if (index < 0) {
			throw new IllegalStateException("no npc " + npcId + " in the scene to click");
		}
		NpcType type = client.npcs[index].field1370;
		type.field1434 = true;
		type.field1449 = new String[] { "Talk-to", null, null, null, null };
		client.menuSize = 0;
		client.addNpcOptions(type, 0, 0, index);
		return click("Talk-to");
	}

	/** The same for the loc standing on a tile, through handleViewportOptions. */
	static int clickLoc(int tileX, int tileZ) {
		LocType.field1636[0].field1644 = new String[] { "Search", null, null, null, null };
		Model.pickedCount = 0;
		Model.field1256[Model.pickedCount++] = pickedWord(tileX, tileZ);
		client.menuSize = 0;
		client.handleViewportOptions();
		return click("Search");
	}

	/** The word the graphics picker hands the menu for the loc on a tile: the scene's own. */
	static int pickedWord(int tileX, int tileZ) {
		Square square = client.scene.field1018[0][tileX][tileZ];
		return square.field1391[0].field88;
	}

	/**
	 * Presses the menu row whose word is this one, and hands the plugins what the client would.
	 *
	 * The row is found by the WORD a player reads, so which action id the builder gave it is
	 * never written down here; the resolver's answer for that row is what comes back.
	 */
	static int click(String word) {
		for (int i = 0; i < client.menuSize; i++) {
			if (client.menuOption[i] == null || client.menuOption[i].indexOf(word) != 0) {
				continue;
			}
			int action = client.menuAction[i];
			int a = client.menuParamA[i];
			int b = client.menuParamB[i];
			int c = client.menuParamC[i];
			jagex2.client.plugin.event.MenuOptionClicked.Target target =
				client.resolveMenuTarget(action >= 2000 ? action - 2000 : action, a, b, c);
			manager.onMenuOptionClicked(client.menuOption[i], action, a, b, c, target);
			return target.op;
		}
		throw new IllegalStateException("the menu has no \"" + word + "\" row (" + client.menuSize + " rows)");
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

	/**
	 * The quest tab's own sort key: lowercased, a leading "The " dropped, punctuation left alone.
	 * Written out here rather than reached into the plugin for, so a change to the plugin's copy
	 * has to disagree with a written-down rule instead of moving the goalposts along with itself.
	 */
	static String sortKey(String name) {
		String s = name.toLowerCase();
		return s.startsWith("the ") ? s.substring(4) : s;
	}

	static int indexOfQuest(List<ConfigList.Row> rows, String name) {
		ConfigList.Row r = row(rows, name);
		return r == null ? -1 : r.index;
	}

	/**
	 * The picker's list reads like the quest tab, with the finished quests at the bottom.
	 *
	 * IT USED TO BE SORTED BY THE INTERNAL ID, which is nothing a player can see: Fight Arena,
	 * Ernest the Chicken and The Restless Ghost are "arena", "haunted" and "priest", so the list
	 * looked unsorted because by name it was.
	 *
	 * THE TWO PAIRS BELOW ARE THE RULE, not decoration. questlist.if is generated in the order 474
	 * draws the list, and both of its sections come out of this key exactly - but only this key:
	 *
	 *   The Restless Ghost sits under R, so a leading "The" is dropped. Keep it and the quest
	 *   falls to the bottom of the list under T.
	 *
	 *   Monk's Friend sits ABOVE Monkey Madness, because the apostrophe sorts below 'e' and is
	 *   left in. Strip the punctuation "to normalise" and that pair swaps - which is the one
	 *   observation that says the rule was read off the interface rather than invented to look
	 *   like it.
	 */
	static void listOrderTests() throws Exception {
		// IT PUTS BACK WHAT IT BORROWS. Reading the list means leaving the tracked quest's page,
		// and markTests() runs on the quest pickTests() left tracked - so the pin and the varp are
		// both restored before this returns. Finishing a quest here is what gives "at the bottom"
		// something to be true about: with every varp at zero that check passes vacuously.
		int wasGhost = client.varps[GHOST_VARP];
		client.varps[GHOST_VARP] = 5;
		change();
		settle();
		List<ConfigList.Row> rows = page();
		check(rows.size() > 100, "the page is the quest list (" + rows.size() + " rows)");
		check(row(rows, GHOST) != null && "complete".equals(row(rows, GHOST).detail),
			"...with one quest finished, to sort");

		int lastUnfinished = -1;
		int firstComplete = -1;
		for (ConfigList.Row r : rows) {
			if ("complete".equals(r.detail)) {
				if (firstComplete < 0) {
					firstComplete = r.index;
				}
			} else {
				lastUnfinished = r.index;
			}
		}
		check(firstComplete < 0 || firstComplete > lastUnfinished,
			"every finished quest sits below every unfinished one");

		String prev = null;
		boolean prevDone = false;
		String broke = null;
		for (ConfigList.Row r : rows) {
			boolean done = "complete".equals(r.detail);
			String key = sortKey(r.label);
			if (prev != null && done == prevDone && key.compareTo(prev) < 0) {
				broke = prev + " came before " + key;
				break;
			}
			prev = key;
			prevDone = done;
		}
		check(broke == null, "the list is in the quest tab's order" + (broke == null ? "" : " - " + broke));

		int ghost = indexOfQuest(rows, GHOST);
		int ali = indexOfQuest(rows, "PRINCE ALI RESCUE");
		check(ghost > 0 && ali > 0 && ghost > ali,
			"The Restless Ghost sorts under R, so a leading \"The\" is dropped");

		int monks = indexOfQuest(rows, "MONK'S FRIEND");
		int madness = indexOfQuest(rows, "MONKEY MADNESS");
		check(monks > 0 && madness > 0 && monks < madness,
			"Monk's Friend sorts above Monkey Madness, so the punctuation is left in");

		client.varps[GHOST_VARP] = wasGhost;
		track(FEUD);
		settle();
	}

	/**
	 * Presses Change on the tracked quest's row, which puts the page back to the list - and does
	 * nothing when the page is already the list.
	 *
	 * THE PAGE CAN BE THE LIST WITH NOBODY HAVING PRESSED ANYTHING: completing the tracked quest
	 * drops the pin. Row 0 there is not Change, it is Track on whichever quest sorts first, so
	 * pressing it blind tracked an arbitrary quest and the next track() went looking for a row the
	 * page no longer had. The action on the row says which page this is; ask it.
	 */
	static void change() {
		for (PluginManager.PanelSnapshot panel : manager.snapshotPanels()) {
			if ("questhelper".equals(panel.entry.key) && !panel.rows.isEmpty()) {
				if ("Change".equals(panel.rows.get(0).action)) {
					panel.act(0);
				}
				return;
			}
		}
		throw new IllegalStateException("no page to change from");
	}

	/**
	 * Presses Track on a quest's row.
	 *
	 * Puts the page back to the list first when it is following something, because row 0 of a
	 * followed quest is the Change button and row 0 of the list is a Track - pressing one when
	 * you meant the other quietly tracks whatever sorted first.
	 */
	static void track(String quest) {
		if (followed() != null) {
			change();
		}
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
