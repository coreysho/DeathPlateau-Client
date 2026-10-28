package jagex2.client.plugin.builtin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Every agility obstacle this server has, taken from its own content rather than from OSRS.
 *
 * NOT A LIST OF OSRS IDS. The ids a wiki or another client would give are for a different
 * cache: the rooftop locs here were grafted in and carry new ids - OSRS's loc 11404 is 18897 in
 * this build - so a table copied from anywhere else marks nothing. Generated from
 * DeathPlateau-Content's DEFAULT BRANCH by reading every `[oploc*,name]` handler under
 * scripts/skill_agility/scripts, resolving each name through pack/loc.pack, and taking the
 * level requirement from the `stat(agility) &lt; n` the handler gates on - or, where the gate
 * is on the course's entry rather than each obstacle, from that course's own `^name_level`
 * constant. The display names are the `name=` the cache ships, so a label here reads the same
 * as the one in the right-click menu.
 *
 * THE SKILL'S OWN DIRECTORY IS THE SOURCE, and getting that wrong is how the first version of
 * this table shipped with nine courses missing. Searching for files NAMED "agility" found the
 * directory but not that it had grown; searching for handlers that grant agility experience
 * missed six rooftop courses, which award it through a shared helper rather than calling
 * agility_xp themselves. Every [oploc*] handler under that directory is an obstacle by
 * construction. The Yanille dungeon is the one that lives elsewhere.
 *
 * THREE HANDLERS ARE LEFT OUT because they bind CATEGORIES - _monkeybars, _island_rope_swing
 * and _karamja_stepping_stone - and no loc in this content is in any of them. They are dead
 * code, ported ahead of the locs they are for. If the locs ever land, they belong here.
 *
 * TWO MORE ARE LEFT OUT because they are not obstacles: shuttwistedchest is a locked chest and
 * trappedchaosaltar is a prayer altar with a trapdoor under it. They are only here at all
 * because they share the Yanille dungeon's script file.
 *
 * A LEVEL OF 1 MEANS THE HANDLER ASKS FOR NONE, not that the content says 1. Nothing on the
 * gnome course has a requirement, which is what makes it the beginners' course.
 */
final class AgilityObstacles {

	static final int GNOME = 0;

	static final int BARBARIAN = 1;

	static final int WILDERNESS = 2;

	static final int DRAYNOR = 3;

	static final int ALKHARID = 4;

	static final int VARROCK = 5;

	static final int CANIFIS = 6;

	static final int FALADOR = 7;

	static final int SEERS = 8;

	static final int POLLNIVNEACH = 9;

	static final int RELLEKKA = 10;

	static final int ARDOUGNE = 11;

	static final int SHORTCUT = 12;

	static final int DUNGEON = 13;

	/** What each course is called, in the order the constants above number them. */
	static final String[] COURSES = {
		"Gnome course", "Barbarian course", "Wilderness course", "Draynor rooftops",
		"Al Kharid rooftops", "Varrock rooftops", "Canifis rooftops", "Falador rooftops",
		"Seers rooftops", "Pollnivneach rooftops", "Rellekka rooftops", "Ardougne rooftops",
		"Shortcut", "Agility dungeon"
	};

	/** One obstacle: what the cache calls it, where it belongs, and what it asks for. */
	static final class Obstacle {

		final int id;

		final String name;

		final int course;

		final int level;

		Obstacle(int id, String name, int course, int level) {
			this.id = id;
			this.name = name;
			this.course = course;
			this.level = level;
		}
	}

	private static final List<Obstacle> ALL = new ArrayList<Obstacle>();

	private static final int[] IDS;

	private AgilityObstacles() {
	}

	static {
		// ---- gnome course (gnome_course.rs2)
		add( 2295, "Log balance", GNOME,  1);                  // gnome_log_balance
		add( 2285, "Obstacle net", GNOME,  1);                 // gnome_obstacle_net_1
		add( 2313, "Tree branch", GNOME,  1);                  // gnome_tree_branch_1
		add( 2312, "Balancing rope", GNOME,  1);               // balancing_rope
		add( 2314, "Tree branch", GNOME,  1);                  // climbing_tree
		add( 2315, "Tree branch", GNOME,  1);                  // climbing_tree2
		add( 2286, "Obstacle net", GNOME,  1);                 // gnome_obstacle_net_2
		add( 4058, "Obstacle pipe", GNOME,  1);                // gnome_obstacle_pipe
		add(  154, "Obstacle pipe", GNOME,  1);                // loc_154

		// ---- barbarian course (barbarian_course.rs2)
		add( 2282, "Ropeswing", BARBARIAN,  1);                // barbarian_rope_swing
		add( 2294, "Log balance", BARBARIAN,  1);              // barbarian_log_balance
		add( 2284, "Obstacle net", BARBARIAN,  1);             // barbarian_obstacle_net
		add( 2302, "Balancing ledge", BARBARIAN,  1);          // barbarian_ledge
		add( 1948, "Crumbling wall", BARBARIAN,  1);           // castlecrumbly1
		add( 2287, "Obstacle pipe", BARBARIAN, 35);            // barbarian_obstacle_pipe

		// ---- wilderness course (wilderness_course.rs2)
		add( 2309, "Door", WILDERNESS, 52);                    // loc_2309
		add( 2307, "Gate", WILDERNESS,  1);                    // loc_2307
		add( 2308, "Gate", WILDERNESS,  1);                    // loc_2308
		add( 2288, "Obstacle pipe", WILDERNESS, 49);           // wilderness_obstacle_pipe
		add( 2283, "Ropeswing", WILDERNESS,  1);               // wilderness_rope_swing
		add( 2311, "Stepping stone", WILDERNESS,  1);          // wilderness_stepping_stone
		add( 2297, "Log balance", WILDERNESS,  1);             // wilderness_log_balance
		add( 2328, "Rocks", WILDERNESS,  1);                   // wilderness_rocks

		// ---- draynor rooftops (draynor_course.rs2)
		add(18897, "Rough wall", DRAYNOR,  1);                 // osrsloc_11404
		add(18887, "Tightrope", DRAYNOR,  1);                  // osrsloc_11405
		add(18888, "Tightrope", DRAYNOR,  1);                  // osrsloc_11406
		add(18890, "Narrow wall", DRAYNOR,  1);                // osrsloc_11430
		add(18891, "Wall", DRAYNOR,  1);                       // osrsloc_11630
		add(18892, "Gap", DRAYNOR,  1);                        // osrsloc_11631
		add(18893, "Crate", DRAYNOR,  1);                      // osrsloc_11632

		// ---- al kharid rooftops (alkharid_course.rs2)
		add(18965, "Rough wall", ALKHARID, 20);                // osrsloc_11633
		add(18947, "Tightrope", ALKHARID, 20);                 // osrsloc_14398
		add(18950, "Cable", ALKHARID, 20);                     // osrsloc_14402
		add(18951, "Zip line", ALKHARID, 20);                  // osrsloc_14403
		add(18960, "Tropical tree", ALKHARID, 20);             // osrsloc_14404
		add(18959, "Roof top beams", ALKHARID, 20);            // osrsloc_11634
		add(18953, "Tightrope", ALKHARID, 20);                 // osrsloc_14409
		add(18948, "Gap", ALKHARID, 20);                       // osrsloc_14399

		// ---- varrock rooftops (varrock_course.rs2)
		add(18978, "Rough wall", VARROCK, 30);                 // osrsloc_14412
		add(18969, "Clothes line", VARROCK, 30);               // osrsloc_14413
		add(18970, "Gap", VARROCK, 30);                        // osrsloc_14414
		add(18971, "Gap", VARROCK, 30);                        // osrsloc_14834
		add(18972, "Gap", VARROCK, 30);                        // osrsloc_14835
		add(18973, "Ledge", VARROCK, 30);                      // osrsloc_14836
		add(18974, "Edge", VARROCK, 30);                       // osrsloc_14841

		// ---- canifis rooftops (canifis_course.rs2)
		add(18990, "Tall tree", CANIFIS, 40);                  // osrsloc_14843
		add(18981, "Gap", CANIFIS, 40);                        // osrsloc_14844
		add(18982, "Gap", CANIFIS, 40);                        // osrsloc_14845
		add(18985, "Gap", CANIFIS, 40);                        // osrsloc_14848
		add(18983, "Gap", CANIFIS, 40);                        // osrsloc_14846
		add(18986, "Pole-vault", CANIFIS, 40);                 // osrsloc_14894
		add(18984, "Gap", CANIFIS, 40);                        // osrsloc_14847
		add(18987, "Gap", CANIFIS, 40);                        // osrsloc_14897

		// ---- falador rooftops (falador_course.rs2)
		add(19016, "Rough wall", FALADOR, 50);                 // osrsloc_14898
		add(18993, "Tightrope", FALADOR, 50);                  // osrsloc_14899
		add(18995, "Hand holds", FALADOR, 50);                 // osrsloc_14901
		add(18996, "Gap", FALADOR, 50);                        // osrsloc_14903
		add(18997, "Gap", FALADOR, 50);                        // osrsloc_14904
		add(18998, "Tightrope", FALADOR, 50);                  // osrsloc_14905
		add(19000, "Tightrope", FALADOR, 50);                  // osrsloc_14911
		add(19001, "Gap", FALADOR, 50);                        // osrsloc_14919
		add(19002, "Ledge", FALADOR, 50);                      // osrsloc_14920
		add(19003, "Ledge", FALADOR, 50);                      // osrsloc_14921
		add(19004, "Ledge", FALADOR, 50);                      // osrsloc_14922
		add(19005, "Ledge", FALADOR, 50);                      // osrsloc_14923
		add(19006, "Ledge", FALADOR, 50);                      // osrsloc_14924
		add(19007, "Edge", FALADOR, 50);                       // osrsloc_14925

		// ---- seers rooftops (seers_course.rs2)
		add(19057, "Wall", SEERS, 60);                         // osrsloc_14927
		add(19018, "Gap", SEERS, 60);                          // osrsloc_14928
		add(19022, "Tightrope", SEERS, 60);                    // osrsloc_14932
		add(19019, "Gap", SEERS, 60);                          // osrsloc_14929
		add(19020, "Gap", SEERS, 60);                          // osrsloc_14930
		add(19021, "Edge", SEERS, 60);                         // osrsloc_14931

		// ---- pollnivneach rooftops (pollnivneach_course.rs2)
		add(19082, "Basket", POLLNIVNEACH, 70);                // osrsloc_14935
		add(19068, "Market stall", POLLNIVNEACH, 70);          // osrsloc_14936
		add(19069, "Banner", POLLNIVNEACH, 70);                // osrsloc_14937
		add(19070, "Gap", POLLNIVNEACH, 70);                   // osrsloc_14938
		add(19071, "Tree", POLLNIVNEACH, 70);                  // osrsloc_14939
		add(19072, "Rough wall", POLLNIVNEACH, 70);            // osrsloc_14940
		add(19073, "Monkeybars", POLLNIVNEACH, 70);            // osrsloc_14941
		add(19075, "Tree", POLLNIVNEACH, 70);                  // osrsloc_14944
		add(19076, "Drying line", POLLNIVNEACH, 70);           // osrsloc_14945

		// ---- rellekka rooftops (rellekka_course.rs2)
		add(19093, "Rough wall", RELLEKKA, 80);                // osrsloc_14946
		add(19083, "Gap", RELLEKKA, 80);                       // osrsloc_14947
		add(19084, "Tightrope", RELLEKKA, 80);                 // osrsloc_14987
		add(19086, "Gap", RELLEKKA, 80);                       // osrsloc_14990
		add(19087, "Gap", RELLEKKA, 80);                       // osrsloc_14991
		add(19088, "Tightrope", RELLEKKA, 80);                 // osrsloc_14992
		add(19089, "Pile of fish", RELLEKKA, 80);              // osrsloc_14994

		// ---- ardougne rooftops (ardougne_course.rs2)
		add(19106, "Wooden Beams", ARDOUGNE, 90);              // osrsloc_15608
		add(19098, "Gap", ARDOUGNE, 90);                       // osrsloc_15609
		add(19102, "Plank", ARDOUGNE, 90);                     // osrsloc_26635
		add(19099, "Gap", ARDOUGNE, 90);                       // osrsloc_15610
		add(19100, "Gap", ARDOUGNE, 90);                       // osrsloc_15611
		add(19104, "Steep roof", ARDOUGNE, 90);                // osrsloc_28912
		add(19101, "Gap", ARDOUGNE, 90);                       // osrsloc_15612

		// ---- shortcut (log_balances.rs2)
		add( 9328, "Log balance", SHORTCUT,  1);               // loc_9328
		add( 9329, "Log balance", SHORTCUT,  1);               // loc_9329
		add( 9330, "Log balance", SHORTCUT,  1);               // loc_9330
		add( 9322, "Log balance", SHORTCUT,  1);               // loc_9322
		add( 9323, "Log balance", SHORTCUT,  1);               // loc_9323
		add( 9324, "Log balance", SHORTCUT,  1);               // loc_9324

		// ---- shortcut (shortcuts.rs2)
		add( 1947, "Crumbling wall", SHORTCUT,  5);            // castlecrumbly
		add( 2298, "Climbing rocks", SHORTCUT,  5);            // loc_2298
		add( 2296, "Log balance", SHORTCUT, 20);               // loc_2296
		add( 2332, "A wooden log", SHORTCUT,  1);              // zq_logbalance
		add(  993, "Stile", SHORTCUT,  1);                     // fullstyle
		add( 7527, "Stile", SHORTCUT,  1);                     // farming_style
		add(12982, "Stile", SHORTCUT,  1);                     // qip_sheep_shearer_fullstyle
		add( 9299, "Fence", SHORTCUT, 29);                     // loc_9299
		add( 9309, "Underwall tunnel", SHORTCUT, 26);          // loc_9309
		add( 9310, "Underwall tunnel", SHORTCUT, 26);          // loc_9310
		add( 9311, "Underwall tunnel", SHORTCUT, 21);          // loc_9311
		add( 9312, "Underwall tunnel", SHORTCUT, 21);          // loc_9312
		add(15397, "Underwall tunnel", SHORTCUT, 42);          // draynor_underwall_tunnel_w
		add(15398, "Underwall tunnel", SHORTCUT, 42);          // draynor_underwall_tunnel_e
		add(11844, "Crumbling wall", SHORTCUT,  5);            // falador_crumbling_wall
		add( 9325, "Crevice", SHORTCUT, 42);                   // loc_9325
		add( 9326, "Strange floor", SHORTCUT, 62);             // loc_9326
		add( 9321, "Crevice", SHORTCUT, 81);                   // loc_9321
		add( 9293, "Obstacle pipe", SHORTCUT, 70);             // loc_9293
		add( 9294, "Strange floor", SHORTCUT, 80);             // loc_9294

		// ---- agility dungeon (agility_dungeon.rs2)
		add( 2290, "Obstacle pipe", DUNGEON, 49);              // loc_2290
		add( 2303, "Balancing ledge", DUNGEON, 40);            // loc_2303
		add( 2317, "Pile of rubble", DUNGEON, 67);             // climbingcaverocks1
		add( 2318, "Pile of rubble", DUNGEON,  1);             // climbingcaverocks2

		// GROUPED BY COURSE ABOVE, in the order each script binds them, because that is how a
		// reader checks the table against the content - all nine gnome obstacles together,
		// not interleaved with Draynor's tunnels by id. So the ids come out unsorted and this
		// sort is the thing that makes ids() answer what findLocs binary searches.
		IDS = new int[ALL.size()];
		for (int i = 0; i < ALL.size(); i++) {
			IDS[i] = ALL.get(i).id;
		}
		Arrays.sort(IDS);
	}

	private static void add(int id, String name, int course, int level) {
		ALL.add(new Obstacle(id, name, course, level));
	}

	/** Every obstacle's id, sorted, which is what PluginContext.findLocs takes. */
	static int[] ids() {
		int[] copy = new int[IDS.length];
		System.arraycopy(IDS, 0, copy, 0, IDS.length);
		return copy;
	}

	static int count() {
		return ALL.size();
	}

	/** The obstacle with this id, or null. */
	static Obstacle byId(int id) {
		for (int i = 0; i < ALL.size(); i++) {
			if (ALL.get(i).id == id) {
				return ALL.get(i);
			}
		}
		return null;
	}

	/** Every obstacle, in the order the table lists them. */
	static List<Obstacle> all() {
		return new ArrayList<Obstacle>(ALL);
	}

	/** Whether this is one of the twelve courses rather than a shortcut or the dungeon. */
	static boolean isCourse(int course) {
		return course >= GNOME && course <= ARDOUGNE;
	}
}
