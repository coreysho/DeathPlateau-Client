/*
 * Headless test for the two plugins that are nothing but arithmetic over the skills the plugin
 * context already exposes: Boosts and Skills.
 *
 * DRIVEN THE WAY THE CLIENT DRIVES THEM. The plugins are started by the real PluginManager, the
 * overlays draw through the real OverlayGraphics, and the Skills page is read through the same
 * snapshotPanels() the sidebar reads. So what is checked is what a player would see - the text
 * actually drawn, the rows actually offered - rather than a method called in isolation.
 *
 * That also means nothing here needs the plugins to widen a single method: a plugin is a leaf
 * class and owes nobody an API, and this test sits in their own package so the handful of static
 * helpers worth checking directly are already in reach.
 *
 * The font is a stub that records (x, y, colour, text) per call, because recording what was
 * drawn IS the measurement for a panel of numbers.
 *
 * Constructing a Client needs a display (it extends Applet), so the runner provides a virtual one.
 */
package jagex2.client.plugin.builtin;

import java.util.ArrayList;
import java.util.List;

import jagex2.client.Client;
import jagex2.client.plugin.OverlayGraphics;
import jagex2.client.plugin.PluginConfig;
import jagex2.client.plugin.PluginManager;
import jagex2.dash3d.ClientPlayer;
import jagex2.graphics.PixFont;

public class SkillPluginsTest {

	static final int W = 512;
	static final int H = 334;
	static final int ROW_H = 12;

	/** Where the status bars draw, and the colour their fill is. */
	static int fails;

	static Client client;
	static PluginManager manager;
	static RecordingFont font;

	/** What Pix2D draws into, kept so the test can read back what was painted where. */
	static int[] pixels;

	static void check(boolean ok, String what) {
		System.out.println((ok ? "  ok   " : "FAIL   ") + what);
		if (!ok) {
			fails++;
		}
	}

	public static void main(String[] args) {
		if (java.awt.GraphicsEnvironment.isHeadless()) {
			System.out.println("  SKIP  no display, so a Client cannot be constructed");
			System.exit(0);
		}
		System.out.println("0. the two plugins, started by the real manager");
		if (!setUp()) {
			System.out.println();
			System.out.println("1 FAILED");
			System.exit(1);
		}
		System.out.println();
		System.out.println("1. Boosts: which stats are up and which are down");
		boostTests();
		System.out.println();
		System.out.println("2. Skills: the experience curve, past where the client's table ends");
		System.out.println("1b. only these skills");
		filterTests();
		System.out.println();
		curveTests();
		System.out.println();
		System.out.println("3. Skills: the combat level, and the page");
		skillsPageTests();
		System.out.println();
		System.out.println("3b. the order the page is in");
		sortTests();

		System.out.println();
		System.out.println(fails == 0 ? "ALL PASS" : fails + " FAILED");
		System.exit(fails == 0 ? 0 : 1);
	}

	// ---------------------------------------------------------------- 0

	static boolean setUp() {
		try {
			client = new Client();
		} catch (Throwable error) {
			check(false, "a client can be built (" + error + ")");
			return false;
		}
		// isLoggedIn wants both of these, and all three plugins check it before doing anything.
		client.ingame = true;
		Client.localPlayer = new ClientPlayer();
		levelAll(50);

		pixels = new int[W * H];
		jagex2.graphics.Pix2D.bind(W, H, pixels);
		font = new RecordingFont();
		manager = new PluginManager(client, font, font, font);
		manager.reload();

		// Everything off to begin with, so a line of drawn text can only have come from the
		// plugin the test just turned on.
		List<PluginManager.Entry> entries = manager.getPlugins();
		for (int i = 0; i < entries.size(); i++) {
			if (entries.get(i).isEnabled()) {
				manager.setEnabled(entries.get(i), false);
			}
		}
		check(entry("boosts") != null, "Boosts is one of the built-in plugins");
		check(entry("skills") != null, "Skills is one of the built-in plugins");
		// Status bars shipped for one release and was taken out again: this server does not put
		// a player's health, prayer or special attack on the screen, as orbs, bars or anything
		// else. Checked rather than assumed, so it cannot quietly come back.
		check(entry("status-bars") == null, "Status bars is gone, and stays gone");
		// Neither asks to be on: both are additions, and an addition that turns itself on
		// changes what every existing player sees on their next launch.
		check(entry("boosts") != null && !entry("boosts").isEnabled()
			&& entry("skills") != null && !entry("skills").isEnabled(),
			"...and neither turns itself on");
		return entry("boosts") != null && entry("skills") != null;
	}

	// ---------------------------------------------------------------- 1

	static void boostTests() {
		PluginManager.Entry boosts = entry("boosts");
		levelAll(50);
		manager.setEnabled(boosts, true);

		check(drawnText().isEmpty(), "nothing boosted draws nothing at all, not an empty box");

		// One boost and one drain. Both must be named, and not in the same colour.
		client.skillLevel[0] = 54;   // attack, boosted
		client.skillLevel[1] = 46;   // defence, drained
		List<Drawn> rows = drawnText();
		check(find(rows, "Attack 54/50") != null,
			"a boost is drawn as now over base: " + texts(rows));
		check(find(rows, "Defence 46/50") != null, "...and so is a drain");
		Drawn up = find(rows, "Attack 54/50");
		Drawn down = find(rows, "Defence 46/50");
		check(up != null && down != null && up.colour != down.colour,
			"...in different colours, because a drain is not a boost");
		check(find(rows, "Boosts") != null, "...under a heading, so the panel says what it is");

		// AND WHICH COLOUR IS WHICH. "The two differ" is still true when they are swapped, and
		// swapping them is one subtraction the wrong way round - so the sign is checked where it
		// is visible as text, which is the relative mode.
		setBoolean(boosts, "relative", true);
		rows = drawnText();
		check(find(rows, "Attack +4") != null,
			"a boost carries its plus when shown as a difference: " + texts(rows));
		check(find(rows, "Defence -4") != null, "...and a drain its minus, exactly once");
		setBoolean(boosts, "relative", false);

		// The placeholder slots the client keeps. A plugin walking getSkillCount() reaches them,
		// and "-unused- 54/50" in a panel is a plugin reading an array rather than the game.
		int last = client.skillLevel.length - 1;
		client.skillLevel[last] = 54;
		client.skillBaseLevel[last] = 50;
		rows = drawnText();
		boolean named = false;
		for (int i = 0; i < rows.size(); i++) {
			if (rows.get(i).text.indexOf("nused") >= 0 || rows.get(i).text.startsWith("-")) {
				named = true;
			}
		}
		check(!named, "the cache's unused skill slots are not offered as boosted skills: "
			+ texts(rows));
		client.skillLevel[last] = 50;
		client.skillBaseLevel[last] = 50;

		// Logged out, nothing is drawn: the skill arrays still hold the last values seen, so a
		// plugin that does not check would paint a panel over the login screen.
		client.ingame = false;
		check(drawnText().isEmpty(), "logged out, the panel is gone rather than frozen");
		client.ingame = true;

		// ---- WHAT THIS PANEL ALREADY REPORTS, established rather than assumed.
		//
		// In 377 the server sends a skill's CURRENT level in UPDATE_STAT, and for hitpoints that
		// is current health - so a damaged player's hitpoints read below their base the same way
		// a drained stat does. This check exists to record what the panel does today, because
		// plugins/README.md says this server reports no health, prayer or special attack and
		// "the tests check for their absence". Whether a drained-looking Hitpoints row is within
		// that rule or outside it is a decision, not a bug to quietly fix, so the behaviour is
		// pinned here and named for what it is.
		levelAll(50);
		client.skillLevel[3] = 35;        // hitpoints: damaged, in the client's own terms
		rows = drawnText();
		boolean healthShown = find(rows, "Hitpoints 35/50") != null;
		check(healthShown,
			"TODAY the panel lists a damaged Hitpoints like any other drained stat ("
				+ texts(rows) + ")");
		levelAll(50);

		// ...and the one thing that is NOT a decision: the new notice never speaks about them.
		check(BoostsPlugin.isVital("hitpoints") && BoostsPlugin.isVital("prayer"),
			"hitpoints and prayer are the two the expiry notice never mentions");
		check(BoostsPlugin.isVital("HITPOINTS"), "...whatever case the cache names them in");
		check(!BoostsPlugin.isVital("attack") && !BoostsPlugin.isVital("strength")
				&& !BoostsPlugin.isVital("magic"),
			"...and nothing else is excluded, because everything else is a potion wearing off");
		check(!BoostsPlugin.isVital(null), "...and a missing name is not a vital");

        // ---- THE SIX NEW SETTINGS. Defaults first, before anything writes them: they are the
		// values that were hardcoded, so a player who upgrades sees what they saw.
		check(text(boosts, "boostedColour").equals(BoostsPlugin.DEFAULT_BOOSTED)
				&& text(boosts, "drainedColour").equals(BoostsPlugin.DEFAULT_DRAINED),
			"both colours start as the ones that were hardcoded");
		check(text(boosts, "font").equals(OverlayGraphics.FONT_CHOICE_SMALL),
			"the panel starts at the small font it always used");
		check(bool(boosts, "showTitle"), "the heading is on");
		check(text(boosts, "skills").length() == 0, "no skill filter");
		check(!bool(boosts, "notifyExpired"), "and the expiry notice off");
		check(setting(boosts, "boostedColour").isColour()
				&& setting(boosts, "drainedColour").isColour(),
			"both colours are edited as colours");

		// ---- THE COLOURS, which were hardcoded and had only "the two differ" on them.
		client.skillLevel[0] = 54;
		client.skillLevel[1] = 46;
		setText(boosts, "boostedColour", "00FF00");
		setText(boosts, "drainedColour", "FF0000");
		rows = drawnText();
		check(find(rows, "Attack 54/50").colour == 0x00FF00,
			"a boost is drawn in the colour a player picked");
		check(find(rows, "Defence 46/50").colour == 0xFF0000, "...and a drain in theirs");
		setText(boosts, "boostedColour", BoostsPlugin.DEFAULT_BOOSTED);
		setText(boosts, "drainedColour", BoostsPlugin.DEFAULT_DRAINED);

		// ---- THE HEADING, which costs a row.
		rows = drawnText();
		check(find(rows, "Boosts") != null, "the heading is drawn");
		setBoolean(boosts, "showTitle", false);
		rows = drawnText();
		check(find(rows, "Boosts") == null, "turning it off removes it");
		check(find(rows, "Attack 54/50") != null, "...and leaves the rows");
		setBoolean(boosts, "showTitle", true);

		// ---- THE FILTER. One list, shared with the Idle notifier.
		setText(boosts, "skills", "attack");
		rows = drawnText();
		check(find(rows, "Attack 54/50") != null && find(rows, "Defence 46/50") == null,
			"a filter of \"attack\" shows attack and not defence: " + texts(rows));
		setText(boosts, "skills", "att, def");
		rows = drawnText();
		check(find(rows, "Attack 54/50") != null && find(rows, "Defence 46/50") != null,
			"...and part of a name is enough, for each of several");
		setText(boosts, "skills", "mining");
		check(drawnText().isEmpty(),
			"a filter naming nothing boosted draws nothing, not an empty box");
		setText(boosts, "skills", "");
		rows = drawnText();
		check(find(rows, "Attack 54/50") != null && find(rows, "Defence 46/50") != null,
			"AN EMPTY FILTER IS EVERY SKILL, not no skills: the filter narrows something already "
				+ "useful, so no filter has to mean do not narrow it");

		levelAll(50);
		manager.setEnabled(boosts, false);
		levelAll(50);
	}

	// ---------------------------------------------------------------- 3b

	/**
	 * The order the Skills page is in.
	 *
	 * A STABLE INSERTION SORT over at most twenty-three rows, which is why it is written out
	 * rather than handed to Collections.sort with a comparator per order: "closest to a level"
	 * is very nearly not a consistent comparator - two skills both 0 away is a real case - and a
	 * page that reshuffles its ties every tick is unreadable.
	 */
	static void sortTests() {
		// name, level, virtualLevel, experience, toNext, percent
		SkillsPlugin.Skill a = skill("Attack", 50, 50, 100_000, 5_000);
		SkillsPlugin.Skill b = skill("Bravery", 70, 70, 800_000, 1_000);
		SkillsPlugin.Skill c = skill("Cooking", 60, 60, 300_000, 0);

		check(order(SkillsPlugin.BY_SKILL, a, b, c).equals("Attack,Bravery,Cooking"),
			"skill order is the list as it came, which is the client's own");
		check(order(null, a, b, c).equals("Attack,Bravery,Cooking"),
			"...and so is an order nobody recognises, rather than an empty page");

		check(order(SkillsPlugin.BY_LEVEL, a, b, c).equals("Bravery,Cooking,Attack"),
			"by level is highest first");
		check(order(SkillsPlugin.BY_EXPERIENCE, a, b, c).equals("Bravery,Cooking,Attack"),
			"by experience is most first");

		// CLOSEST TO A LEVEL, where a maxed skill has to go LAST. It has 0 left to reach, which
		// a plain comparison puts first - the one place this order needs thought.
		check(order(SkillsPlugin.BY_CLOSEST, a, b, c).equals("Bravery,Attack,Cooking"),
			"closest to a level is nearest first, with nothing-left-to-reach LAST");

		// STABLE. Three skills with the same level stay in the order they arrived, or the page
		// reshuffles itself every tick for no reason a player can see.
		SkillsPlugin.Skill x = skill("Xerxes", 50, 50, 1, 1);
		SkillsPlugin.Skill y = skill("Yvonne", 50, 50, 1, 1);
		SkillsPlugin.Skill z = skill("Zebedee", 50, 50, 1, 1);
		check(order(SkillsPlugin.BY_LEVEL, x, y, z).equals("Xerxes,Yvonne,Zebedee"),
			"ties keep the order they came in, by level");
		check(order(SkillsPlugin.BY_CLOSEST, x, y, z).equals("Xerxes,Yvonne,Zebedee"),
			"...and by how close they are");
		check(order(SkillsPlugin.BY_EXPERIENCE, x, y, z).equals("Xerxes,Yvonne,Zebedee"),
			"...and by experience");

		// Two maxed skills are both "nothing left", which is the tie that would make a careless
		// comparator inconsistent rather than merely unstable.
		SkillsPlugin.Skill m1 = skill("Maxed one", 99, 99, 13_034_431, 0);
		SkillsPlugin.Skill m2 = skill("Maxed two", 99, 99, 13_034_431, 0);
		check(order(SkillsPlugin.BY_CLOSEST, m1, m2, a).equals("Attack,Maxed one,Maxed two"),
			"two maxed skills tie with each other and both sit behind one still training");

		// An empty page and a page of one sort without incident, which an insertion sort written
		// by hand is exactly where an off-by-one lives.
		check(order(SkillsPlugin.BY_LEVEL).length() == 0, "an empty page sorts to nothing");
		check(order(SkillsPlugin.BY_LEVEL, a).equals("Attack"), "and a page of one to itself");
	}

	static SkillsPlugin.Skill skill(String name, int level, int virtual, int xp, int toNext) {
		return new SkillsPlugin.Skill(name, level, virtual, xp, toNext, 50);
	}

	/** The names, in the order this sort leaves them. */
	static String order(String by, SkillsPlugin.Skill... rows) {
		List<SkillsPlugin.Skill> list = new ArrayList<SkillsPlugin.Skill>();
		for (int i = 0; i < rows.length; i++) {
			list.add(rows[i]);
		}
		SkillsPlugin.sort(list, by);
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < list.size(); i++) {
			if (i > 0) {
				out.append(',');
			}
			out.append(list.get(i).name);
		}
		return out.toString();
	}

	// ---------------------------------------------------------------- 1b

	/**
	 * "Only these skills", which Boosts and the Idle notifier both ask.
	 *
	 * ONE IMPLEMENTATION, because two walks of the same list would eventually disagree about
	 * whether "wood" matches Woodcutting - and a filter that silently excludes what a player
	 * meant to include is read as the plugin being broken rather than as a filter being strict.
	 */
	static void filterTests() {
		check(SkillFilter.allows("attack", "attack"), "a whole name matches");
		check(SkillFilter.allows("attack", "att"), "a prefix matches, which is what people type");
		check(SkillFilter.allows("woodcutting", "wood"), "...including the obvious short ones");
		check(SkillFilter.allows("runecraft", "runecraft"), "and a long name is its own prefix");
		check(SkillFilter.allows("ATTACK", "attack") && SkillFilter.allows("attack", "ATTACK"),
			"case does not matter either way round");
		check(SkillFilter.allows("attack", " att , def "), "spaces round the terms are forgiven");
		check(SkillFilter.allows("defence", "att, def"), "any term in the list matches");

		// PREFIX, NOT SUBSTRING. "tack" is inside "attack" and matches nothing anyone means by
		// it; a substring rule also makes a one-letter term match half the list.
		check(!SkillFilter.allows("attack", "tack"),
			"a term that is only INSIDE a name does not match: prefix, not substring");
		check(!SkillFilter.allows("attack", "defence"), "a different skill does not match");
		check(!SkillFilter.allows("attack", "attacking"),
			"and a term longer than the name does not");

		// AN EMPTY LIST IS EVERY SKILL. The opposite of the rule in Npc indicators, and right in
		// both: there an empty list is a plugin not set up yet, here it is a filter not applied.
		check(SkillFilter.allows("attack", ""), "an empty list allows everything");
		check(SkillFilter.allows("attack", "   "), "...and a blank one");
		check(SkillFilter.allows("attack", null), "...and none at all");
		check(SkillFilter.allows("attack", ",,,"),
			"...and one that is nothing but commas, which is not a filter either");
		check(!SkillFilter.isFiltering("") && !SkillFilter.isFiltering(null)
				&& !SkillFilter.isFiltering(",, ,"),
			"none of those counts as filtering");
		check(SkillFilter.isFiltering("attack") && SkillFilter.isFiltering(" , attack"),
			"and a list with a term in it does");

		// The two answers cannot disagree: the first version of this had allows() reject every
		// skill for ",,," while isFiltering() said there was no filter.
		String[] odd = { "", "   ", null, ",,,", ", ,", "attack", " , attack, " };
		for (int i = 0; i < odd.length; i++) {
			if (!SkillFilter.isFiltering(odd[i])) {
				check(SkillFilter.allows("attack", odd[i]) && SkillFilter.allows("mining", odd[i]),
					"\"" + odd[i] + "\" is not filtering, so it allows every skill");
			}
		}

		check(!SkillFilter.allows(null, "attack"), "a missing skill name matches no filter");
		check(!SkillFilter.allows("", "attack"), "...nor an empty one");
		boolean threw = false;
		try {
			SkillFilter.allows(null, null);
			SkillFilter.allows("attack", "a,,b,");
		} catch (Throwable error) {
			threw = true;
		}
		check(!threw, "and nothing here throws, because both callers ask it per skill per tick");
	}

	// ---------------------------------------------------------------- 2

	static void curveTests() {
		PluginManager.Entry entry = entry("skills");
		manager.setEnabled(entry, true);

		// THE CHECK THAT MATTERS. This plugin continues the client's experience table past where
		// it ends, and the only way to know it continues the SAME curve is to point the
		// continuation at the part the client already answers for - all 99 levels of it, not a
		// spot check. A formula right at 2 and 99 and wrong at 73 is the bug this catches.
		int wrong = 0;
		int firstWrong = 0;
		for (int level = 2; level <= SkillsPlugin.LAST_TABLED_LEVEL; level++) {
			if (formula(level) != experienceFor(level)) {
				if (wrong == 0) {
					firstWrong = level;
				}
				wrong++;
			}
		}
		check(wrong == 0, wrong == 0
			? "the formula reproduces every level the client tables (2 to "
				+ SkillsPlugin.LAST_TABLED_LEVEL + ")"
			: wrong + " of those levels disagree with the client's table, first at " + firstWrong);

		// Past the end, where the client clamps and this does not. Without the continuation
		// every level above 100 would report the same experience and read as level 100 forever.
		check(experienceFor(101) > experienceFor(100),
			"past the table the levels keep going up rather than flattening ("
				+ experienceFor(100) + " then " + experienceFor(101) + ")");
		check(experienceFor(126) == formula(126),
			"...on the same curve, all the way to 126");
		check(experienceFor(126) > 180_000_000 && experienceFor(126) < 200_000_000,
			"...which lands where 200M experience does (" + experienceFor(126) + ")");

		// True levels. A 99 is only a 99 until it has a 100's experience.
		check(virtualFor(13_034_431, 99) == 99, "a bare 99 is a 99");
		check(virtualFor(14_391_160, 99) == 100, "a 99 with a 100's experience is a 100");
		check(virtualFor(formula(110), 99) == 110, "...and it keeps counting (110)");
		check(virtualFor(500, 7) == 7,
			"below 99 the client's own answer is used unchanged, whatever the experience");
		check(virtualFor(formula(SkillsPlugin.MAX_VIRTUAL_LEVEL), 99)
			== SkillsPlugin.MAX_VIRTUAL_LEVEL,
			"the top level is reachable, not one short of it ("
				+ virtualFor(formula(SkillsPlugin.MAX_VIRTUAL_LEVEL), 99) + ")");
		check(virtualFor(Integer.MAX_VALUE, 99) == SkillsPlugin.MAX_VIRTUAL_LEVEL,
			"the most experience an int can hold stops exactly there rather than counting on ("
				+ virtualFor(Integer.MAX_VALUE, 99) + ")");

		check("1,234,567".equals(SkillsPlugin.commas(1234567)),
			"a seven-digit total gets its commas: " + SkillsPlugin.commas(1234567));
		check("83".equals(SkillsPlugin.commas(83)), "a small one does not: " + SkillsPlugin.commas(83));
		check("1,000".equals(SkillsPlugin.commas(1000)),
			"and a round thousand groups correctly: " + SkillsPlugin.commas(1000));
		check("0".equals(SkillsPlugin.commas(0)), "zero is zero: " + SkillsPlugin.commas(0));
		manager.setEnabled(entry, false);
	}

	// ---------------------------------------------------------------- 3

	static void skillsPageTests() {
		PluginManager.Entry entry = entry("skills");
		manager.setEnabled(entry, true);

		// Combat level, against figures worked out by hand from the 377 formula.
		baseAll(1);
		client.skillBaseLevel[3] = 10;  // hitpoints, as a new account has
		check(combatRow() == 3, "a fresh account is combat 3 (" + combatRow() + ")");

		baseAll(1);
		client.skillBaseLevel[0] = 99;  // attack
		client.skillBaseLevel[2] = 99;  // strength
		client.skillBaseLevel[1] = 99;  // defence
		client.skillBaseLevel[3] = 99;  // hitpoints
		client.skillBaseLevel[5] = 99;  // prayer
		check(combatRow() == 126, "maxed melee with 99 prayer is combat 126 (" + combatRow() + ")");

		client.skillBaseLevel[0] = 1;
		client.skillBaseLevel[2] = 1;
		client.skillBaseLevel[4] = 99;  // ranged instead
		// 109, not 123: ranged counts as floor(99 * 3 / 2) = 148 against melee's 99 + 99 = 198,
		// so the same defensives carry a pure ranger seventeen levels lower than maxed melee.
		// 123 was this test's first guess and it was simply wrong - worth keeping the real
		// number, because a combat level a player can check is the point of having one.
		check(combatRow() == 109,
			"the same account as a pure ranger is 109 (" + combatRow() + ")");

		// The page the sidebar actually reads.
		baseAll(50);
		levelAll(50);
		tick();
		List<PluginManager.PanelSnapshot> panels = manager.snapshotPanels();
		PluginManager.PanelSnapshot page = null;
		for (int i = 0; i < panels.size(); i++) {
			if ("Skills".equals(panels.get(i).title)) {
				page = panels.get(i);
			}
		}
		check(page != null, "the plugin puts a page on the rail");
		if (page == null) {
			manager.setEnabled(entry, false);
			return;
		}
		check(page.rows.size() > 20, "...with a row per skill (" + page.rows.size() + ")");
		check("Combat level".equals(page.rows.get(0).label),
			"...the combat level first, because it belongs to no one skill");
		boolean unused = false;
		boolean progressed = false;
		for (int i = 0; i < page.rows.size(); i++) {
			if (page.rows.get(i).label.indexOf("nused") >= 0) {
				unused = true;
			}
			if (page.rows.get(i).progress > 0) {
				progressed = true;
			}
		}
		check(!unused, "...and no placeholder slots among them");
		check(progressed, "...each with a bar showing how far through its level it is");

		// Every skill at 99 and nothing left to train: the page is the combat level and nothing
		// else, which is what "hide maxed" is for.
		// The cache. Read twice without a tick between, the page must say the same thing both
		// times: the sidebar asks size() and then five questions per row, and a page that
		// rebuilt itself per question could answer them about two different games.
		baseAll(50);
		tick();
		String first = pageText();
		client.skillBaseLevel[4] = 1;
		client.skillExperience[4] = 0;
		check(first.equals(pageText()),
			"a page read twice inside one tick reads the same both times");
		tick();
		check(!first.equals(pageText()),
			"...and the next tick rebuilds it, so the page is never a tick behind for long");

		baseAll(99);
		int all = pageRows();
		setBoolean(entry, "hideMaxed", true);
		int left = pageRows();
		check(left < all, "hiding maxed skills takes the 99s off (" + all + " then " + left + ")");
		check(left >= 1, "...and leaves the combat level, which is not a skill to hide");
		setBoolean(entry, "hideMaxed", false);

		// ---- THE FOUR NEW SETTINGS. Defaults first, so a player who upgrades sees the page
		// they had: everything on, in the client's own order.
		baseAll(50);
		levelAll(50);
		tick();
		check(bool(entry, "showCombat") && bool(entry, "showExperience"),
			"the combat row and the experience line are both on");
		check(text(entry, "sortBy").equals(SkillsPlugin.BY_SKILL),
			"and the page starts in the client's own skill order");
		check(text(entry, "skills").length() == 0, "with no filter");
		check(setting(entry, "sortBy").choices().length == 4, "the order is a drop-down of four");

		// EVERY ORDER THE DROP-DOWN OFFERS HAS TO BE ONE THE CODE BRANCHES ON. A value no branch
		// matches falls through to "leave it alone" and looks exactly like the default working.
		String[] orders = setting(entry, "sortBy").choices();
		for (int i = 0; i < orders.length; i++) {
			check(orders[i].equals(SkillsPlugin.BY_SKILL) || orders[i].equals(SkillsPlugin.BY_LEVEL)
					|| orders[i].equals(SkillsPlugin.BY_EXPERIENCE)
					|| orders[i].equals(SkillsPlugin.BY_CLOSEST),
				"\"" + orders[i] + "\" is an order the code knows");
		}

		// ---- THE COMBAT ROW.
		check(pageText().indexOf("Combat level") >= 0, "the combat row is on the page");
		setBoolean(entry, "showCombat", false);
		tick();
		check(pageText().indexOf("Combat level") < 0, "turning it off takes it away");
		check(pageRows() > 20, "...and leaves every skill");
		setBoolean(entry, "showCombat", true);
		tick();

		// ---- THE EXPERIENCE LINE, which is the row's detail rather than its value.
		client.skillExperience[0] = 120_000;
		tick();
		check(pageDetail().indexOf("xp") >= 0, "a row says how much experience it has");
		setBoolean(entry, "showExperience", false);
		tick();
		check(pageDetail().indexOf("xp") < 0, "turning it off leaves the rows without it");
		check(pageRows() > 20, "...and the rows themselves are still there");
		setBoolean(entry, "showExperience", true);
		tick();

		// ---- THE FILTER, the same one Boosts uses.
		setText(entry, "skills", "attack");
		tick();
		check(pageRows() == 2,
			"a filter of \"attack\" leaves the combat row and one skill (" + pageRows() + ")");
		check(pageText().indexOf("Attack") >= 0, "...which is Attack");
		setText(entry, "skills", "");
		tick();
		check(pageRows() > 20, "and clearing it brings them all back");

		manager.setEnabled(entry, false);
	}

	// ---------------------------------------------------------------- the plumbing

	/**
	 * A server cycle, through the manager's own hook.
	 *
	 * The Skills page holds its rows for one tick - building the list per question is twenty-six
	 * skills read a hundred and thirty times for one refresh - so anything that changes a level
	 * and then reads the page has to tick first. The first run of this test did not, and read
	 * the levels as they were three assertions ago.
	 */
	static void tick() {
		manager.onGameTick();
	}

	/** The combat level as the page reports it, which is the number a player would read. */
	static int combatRow() {
		tick();
		List<PluginManager.PanelSnapshot> panels = manager.snapshotPanels();
		for (int i = 0; i < panels.size(); i++) {
			PluginManager.PanelSnapshot page = panels.get(i);
			if (!"Skills".equals(page.title) || page.rows.isEmpty()) {
				continue;
			}
			jagex2.client.plugin.ConfigList.Row row = page.rows.get(0);
			if (!"Combat level".equals(row.label)) {
				return -1;
			}
			try {
				return Integer.parseInt(row.value);
			} catch (RuntimeException notANumber) {
				return -1;
			}
		}
		return -1;
	}

	/** Every row of the page as one string, for comparing one read against another. */
	static String pageText() {
		List<PluginManager.PanelSnapshot> panels = manager.snapshotPanels();
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < panels.size(); i++) {
			if (!"Skills".equals(panels.get(i).title)) {
				continue;
			}
			List<jagex2.client.plugin.ConfigList.Row> rows = panels.get(i).rows;
			for (int r = 0; r < rows.size(); r++) {
				out.append(rows.get(r).label).append('=').append(rows.get(r).value)
					.append('/').append(rows.get(r).progress).append(';');
			}
		}
		return out.toString();
	}

	/** Every row's detail line joined, which is where the experience figures go. */
	static String pageDetail() {
		List<PluginManager.PanelSnapshot> panels = manager.snapshotPanels();
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < panels.size(); i++) {
			if (!"Skills".equals(panels.get(i).title)) {
				continue;
			}
			List<jagex2.client.plugin.ConfigList.Row> rows = panels.get(i).rows;
			for (int r = 0; r < rows.size(); r++) {
				out.append(rows.get(r).detail).append(';');
			}
		}
		return out.toString();
	}

	/** How many rows the Skills page has. */
	static int pageRows() {
		tick();
		List<PluginManager.PanelSnapshot> panels = manager.snapshotPanels();
		for (int i = 0; i < panels.size(); i++) {
			if ("Skills".equals(panels.get(i).title)) {
				return panels.get(i).rows.size();
			}
		}
		return -1;
	}

	/**
	 * The experience curve, asked of the plugin the manager started.
	 *
	 * The curve is the one thing here worth asking about directly rather than reading off a
	 * label: it is a formula, and a formula is checked against another formula. Everything else
	 * in this test goes through what a player would see.
	 */
	static int experienceFor(int level) {
		return managedSkills().experienceForLevel(level);
	}

	static int virtualFor(int experience, int level) {
		return managedSkills().virtualLevel(experience, level);
	}

	/**
	 * The Skills plugin instance the manager started.
	 *
	 * PluginManager.Entry keeps its plugin package-private, in a package this test is not in, so
	 * it is read reflectively. Worth it to avoid the alternative: making the field public, which
	 * would hand every plugin a way to reach into every other one.
	 */
	static SkillsPlugin managedSkills() {
		try {
			java.lang.reflect.Field field = PluginManager.Entry.class.getDeclaredField("plugin");
			field.setAccessible(true);
			return (SkillsPlugin) field.get(entry("skills"));
		} catch (Throwable error) {
			throw new IllegalStateException("cannot reach the started Skills plugin: " + error);
		}
	}

	/** Sets an int config item on a running plugin, the way the config panel does. */
	static void setInt(PluginManager.Entry entry, String key, int value) {
		try {
			java.lang.reflect.Field field = PluginManager.Entry.class.getDeclaredField("plugin");
			field.setAccessible(true);
			Object plugin = field.get(entry);
			plugin.getClass().getField(key).setInt(plugin, value);
		} catch (Throwable error) {
			check(false, "cannot set " + key + " (" + error + ")");
		}
	}

	/** Sets a boolean config item on a running plugin, the way the config panel does. */
	static void setBoolean(PluginManager.Entry entry, String key, boolean value) {
		List<PluginManager.Entry> all = manager.getPlugins();
		for (int i = 0; i < all.size(); i++) {
			if (all.get(i) != entry) {
				continue;
			}
			try {
				java.lang.reflect.Field field = PluginManager.Entry.class.getDeclaredField("plugin");
				field.setAccessible(true);
				Object plugin = field.get(entry);
				java.lang.reflect.Field item = plugin.getClass().getField(key);
				item.setBoolean(plugin, value);
			} catch (Throwable error) {
				check(false, "cannot set " + key + " (" + error + ")");
			}
		}
	}

	/**
	 * The experience for a level, from the formula, written out independently of the plugin.
	 *
	 * Not called from the plugin on purpose: a test that asks the code under test for the
	 * expected answer checks nothing at all.
	 */
	static int formula(int level) {
		long points = 0;
		for (int i = 1; i < level; i++) {
			points += (long) Math.floor(i + 300.0 * Math.pow(2.0, i / 7.0));
		}
		return (int) (points / 4);
	}

	/**
	 * Every skill at a level, with the experience that level implies - and a bit more.
	 *
	 * The experience matters: a level with no experience behind it is not a state any account is
	 * ever in, and it makes every progress bar read zero, which is a page that looks right and
	 * says nothing. Halfway to the next level is what a real account mostly looks like.
	 */
	static void levelAll(int level) {
		int start = formula(level);
		int next = formula(level + 1);
		for (int i = 0; i < client.skillLevel.length; i++) {
			client.skillLevel[i] = level;
			client.skillBaseLevel[i] = level;
			client.skillExperience[i] = start + (next - start) / 2;
		}
	}

	/** The same, named for the tests that are about base levels rather than boosted ones. */
	static void baseAll(int level) {
		levelAll(level);
	}

	/**
	 * A String setting, written through the real config path.
	 *
	 * Through PluginConfig rather than the reflected field the older helpers here use, because
	 * the config path is what the sidebar does and it is what persists - a field written directly
	 * would be a setting that works in the test and not in the game.
	 */
	static void setText(PluginManager.Entry entry, String key, String value) {
		List<PluginConfig.Item> items = entry.getConfig().getItems();
		for (int i = 0; i < items.size(); i++) {
			if (items.get(i).key.equals(key)) {
				check(entry.getConfig().set(items.get(i), value),
					"setting " + entry.key + "." + key + " to \"" + value + "\" is accepted");
				return;
			}
		}
		check(false, entry.key + " has a setting called " + key);
	}

	static PluginConfig.Item setting(PluginManager.Entry entry, String key) {
		List<PluginConfig.Item> items = entry.getConfig().getItems();
		for (int i = 0; i < items.size(); i++) {
			if (items.get(i).key.equals(key)) {
				return items.get(i);
			}
		}
		return null;
	}

	static String text(PluginManager.Entry entry, String key) {
		PluginConfig.Item item = setting(entry, key);
		return item == null ? "" : item.stringValue();
	}

	static boolean bool(PluginManager.Entry entry, String key) {
		PluginConfig.Item item = setting(entry, key);
		return item != null && item.booleanValue();
	}

	static int number(PluginManager.Entry entry, String key) {
		PluginConfig.Item item = setting(entry, key);
		return item == null ? -1 : item.intValue();
	}

	static PluginManager.Entry entry(String key) {
		List<PluginManager.Entry> entries = manager.getPlugins();
		for (int i = 0; i < entries.size(); i++) {
			if (key.equals(entries.get(i).key)) {
				return entries.get(i);
			}
		}
		return null;
	}

	/** Everything the running overlays draw as text, one frame's worth. */
	static List<Drawn> drawnText() {
		font.rows.clear();
		manager.renderOverlays(W, H, jagex2.client.plugin.Overlay.LAYER_SCREEN);
		return new ArrayList<Drawn>(font.rows);
	}

	static Drawn find(List<Drawn> rows, String text) {
		for (int i = 0; i < rows.size(); i++) {
			if (text.equals(rows.get(i).text)) {
				return rows.get(i);
			}
		}
		return null;
	}

	static String texts(List<Drawn> rows) {
		StringBuilder out = new StringBuilder("[");
		for (int i = 0; i < rows.size(); i++) {
			out.append(i > 0 ? ", " : "").append(rows.get(i).text);
		}
		return out.append(']').toString();
	}

	/** One string the font was asked to draw. */
	static final class Drawn {

		final int x;
		final int y;
		final int colour;
		final String text;

		Drawn(int x, int y, int colour, String text) {
			this.x = x;
			this.y = y;
			this.colour = colour;
			this.text = text;
		}
	}

	/** A font that records what it was asked to draw instead of drawing it. */
	static final class RecordingFont extends PixFont {

		static final int ADVANCE = 4;

		final List<Drawn> rows = new ArrayList<Drawn>();

		RecordingFont() {
			this.height = ROW_H;
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
			this.rows.add(new Drawn(x, y, colour, text));
		}

		public void drawString(int x, int colour, int y, String text) {
			this.rows.add(new Drawn(x, y, colour, text));
		}
	}
}
