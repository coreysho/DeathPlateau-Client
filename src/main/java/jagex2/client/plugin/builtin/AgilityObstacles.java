package jagex2.client.plugin.builtin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Every agility obstacle this server has, taken from its own content rather than from OSRS.
 *
 * NOT A LIST OF OSRS IDS. The ids a wiki or another client would give are for content this
 * server does not run: it implements three courses, not eight. This table is generated from
 * DeathPlateau-Content by reading every `[oploc*,name]` handler in the agility scripts,
 * resolving each name through pack/loc.pack to its id, and taking the level requirement from
 * the `if(stat(agility) &lt; n)` the handler opens with. The display names are the `name=` the
 * cache ships, so a label here reads the same as the one in the right-click menu.
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

	static final int SHORTCUT = 3;

	static final int DUNGEON = 4;

	/** What each course is called, in the order the constants above number them. */
	static final String[] COURSES = {
		"Gnome course", "Barbarian course", "Wilderness course", "Shortcut", "Agility dungeon"
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
		// ---- the gnome course (gnome_course.rs2)
		add( 2295, "Log balance", GNOME, 1);              // gnome_log_balance
		add( 2285, "Obstacle net", GNOME, 1);             // gnome_obstacle_net_1
		add( 2313, "Tree branch", GNOME, 1);              // gnome_tree_branch_1
		add( 2312, "Balancing rope", GNOME, 1);           // balancing_rope
		add( 2314, "Tree branch", GNOME, 1);              // climbing_tree
		add( 2315, "Tree branch", GNOME, 1);              // climbing_tree2
		add( 2286, "Obstacle net", GNOME, 1);             // gnome_obstacle_net_2
		add( 4058, "Obstacle pipe", GNOME, 1);            // gnome_obstacle_pipe
		add(  154, "Obstacle pipe", GNOME, 1);            // loc_154

		// ---- the barbarian course (barbarian_course.rs2)
		add( 2282, "Ropeswing", BARBARIAN, 1);            // barbarian_rope_swing
		add( 2294, "Log balance", BARBARIAN, 1);          // barbarian_log_balance
		add( 2284, "Obstacle net", BARBARIAN, 1);         // barbarian_obstacle_net
		add( 2302, "Balancing ledge", BARBARIAN, 1);      // barbarian_ledge
		add( 1948, "Crumbling wall", BARBARIAN, 1);       // castlecrumbly1
		add( 2287, "Obstacle pipe", BARBARIAN, 35);        // barbarian_obstacle_pipe

		// ---- the wilderness course (wilderness_course.rs2)
		add( 2309, "Door", WILDERNESS, 52);                // loc_2309
		add( 2307, "Gate", WILDERNESS, 1);                // loc_2307
		add( 2308, "Gate", WILDERNESS, 1);                // loc_2308
		add( 2288, "Obstacle pipe", WILDERNESS, 49);       // wilderness_obstacle_pipe
		add( 2283, "Ropeswing", WILDERNESS, 1);           // wilderness_rope_swing
		add( 2311, "Stepping stone", WILDERNESS, 1);      // wilderness_stepping_stone
		add( 2297, "Log balance", WILDERNESS, 1);         // wilderness_log_balance
		add( 2328, "Rocks", WILDERNESS, 1);               // wilderness_rocks

		// ---- log balances (log_balances.rs2)
		add( 9328, "Log balance", SHORTCUT, 1);           // loc_9328
		add( 9329, "Log balance", SHORTCUT, 1);           // loc_9329
		add( 9330, "Log balance", SHORTCUT, 1);           // loc_9330
		add( 9322, "Log balance", SHORTCUT, 1);           // loc_9322
		add( 9323, "Log balance", SHORTCUT, 1);           // loc_9323
		add( 9324, "Log balance", SHORTCUT, 1);           // loc_9324

		// ---- shortcuts (shortcuts.rs2)
		add( 1947, "Crumbling wall", SHORTCUT, 5);        // castlecrumbly
		add( 2298, "Climbing rocks", SHORTCUT, 5);        // loc_2298
		add( 2296, "Log balance", SHORTCUT, 20);           // loc_2296
		add( 2332, "A wooden log", SHORTCUT, 1);          // zq_logbalance
		add(  993, "Stile", SHORTCUT, 1);                 // fullstyle
		add( 7527, "Stile", SHORTCUT, 1);                 // farming_style
		add(12982, "Stile", SHORTCUT, 1);                 // qip_sheep_shearer_fullstyle
		add( 9299, "Fence", SHORTCUT, 29);                 // loc_9299
		add( 9309, "Underwall tunnel", SHORTCUT, 26);      // loc_9309
		add( 9310, "Underwall tunnel", SHORTCUT, 26);      // loc_9310
		add( 9311, "Underwall tunnel", SHORTCUT, 21);      // loc_9311
		add( 9312, "Underwall tunnel", SHORTCUT, 21);      // loc_9312
		add(15397, "Underwall tunnel", SHORTCUT, 42);      // draynor_underwall_tunnel_w
		add(15398, "Underwall tunnel", SHORTCUT, 42);      // draynor_underwall_tunnel_e
		add(11844, "Crumbling wall", SHORTCUT, 5);        // falador_crumbling_wall
		add( 9325, "Crevice", SHORTCUT, 42);               // loc_9325
		add( 9326, "Strange floor", SHORTCUT, 62);         // loc_9326
		add( 9321, "Crevice", SHORTCUT, 81);               // loc_9321
		add( 9293, "Obstacle pipe", SHORTCUT, 70);         // loc_9293
		add( 9294, "Strange floor", SHORTCUT, 80);         // loc_9294

		// ---- the Yanille agility dungeon (agility_dungeon.rs2)
		add( 2290, "Obstacle pipe", DUNGEON, 49);          // loc_2290
		add( 2303, "Balancing ledge", DUNGEON, 40);        // loc_2303
		add( 2317, "Pile of rubble", DUNGEON, 67);         // climbingcaverocks1
		add( 2318, "Pile of rubble", DUNGEON, 1);         // climbingcaverocks2

		// 53 obstacles

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

	/** Whether a course is one of the three courses rather than a shortcut or the dungeon. */
	static boolean isCourse(int course) {
		return course == GNOME || course == BARBARIAN || course == WILDERNESS;
	}
}
