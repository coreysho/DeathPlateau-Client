package jagex2.client.plugin.builtin;

import java.util.ArrayList;
import java.util.List;

import jagex2.client.plugin.ConfigItem;
import jagex2.client.plugin.ConfigList;
import jagex2.client.plugin.Plugin;
import jagex2.client.plugin.PluginDescriptor;
import jagex2.client.plugin.Subscribe;
import jagex2.client.plugin.event.GameTick;

/**
 * A page of every skill: its level, its true level past 99, how far it is through the one it is
 * on, and the experience left to get there. Combat level across the top.
 *
 * THIS IS THREE OF RUNELITE'S PLUGINS IN ONE PAGE, and deliberately. Virtual levels, Combat level
 * and Skill calculator all work there by writing over the stats tab - the number on the tab
 * becomes 104, the combat level appears in the corner of it. That needs drawing into the game's
 * own interfaces, which is not something PluginContext offers and not something it should offer
 * lightly. A sidebar page shows the same numbers without reaching into the interface at all, and
 * one page reads better than three plugins each contributing a line.
 *
 * THE LEVEL TABLE IS EXTENDED HERE, not in the client. ctx.getExperienceForLevel answers up to
 * level 100 and then clamps, because that is where the client's own table ends - 99 is as high as
 * the game goes, and level 100's figure is what a full 99 needs. Levels past that are the same
 * formula continued, so this plugin continues it: each level adds floor(level + 300 * 2^(level/7))
 * to a running total that is then quartered.
 *
 * It does NOT reimplement the levels the client can answer for. Those are asked for, so the two
 * can never disagree about a level a player actually has - and the test checks the formula against
 * all 98 of the client's own entries, so the continuation is demonstrably the same curve rather
 * than one that merely looks like it.
 */
@PluginDescriptor(
	name = "Skills",
	description = "Levels, true levels past 99, and experience to the next level",
	key = "skills",
	apiLevel = 1
)
public final class SkillsPlugin extends Plugin {

	/** The highest level the game itself goes to. */
	static final int MAX_REAL_LEVEL = 99;

	/**
	 * The highest level the CLIENT's table can answer for, which is not the same number.
	 *
	 * Its table runs to level 100 - 99 entries for levels 2 to 100 - because the experience for
	 * level 100 is what a 99 needs to be full. Above that it clamps and returns 14,391,160 for
	 * every level, which is why anything past here is worked out rather than asked for.
	 */
	static final int LAST_TABLED_LEVEL = 100;

	/** Where the formula stops being worth continuing: 200M experience is level 126. */
	static final int MAX_VIRTUAL_LEVEL = 126;

	/** A skill the cache has no real name for - the client calls these "-unused-". */
	private static final char UNUSED_MARKER = '-';

	/** Skill indices that make up the combat level. The client's own order. */
	private static final int ATTACK = 0;
	private static final int DEFENCE = 1;
	private static final int STRENGTH = 2;
	private static final int HITPOINTS = 3;
	private static final int RANGED = 4;
	private static final int PRAYER = 5;
	private static final int MAGIC = 6;

	@ConfigItem(keyName = "virtual", name = "Show true levels past 99",
		description = "A 99 with 15M experience reads as 104")
	public boolean virtual = true;

	@ConfigItem(keyName = "hideMaxed", name = "Hide skills at 99",
		description = "Keeps the page to what is still being trained")
	public boolean hideMaxed = false;

	/** The four orders {@link #sortBy} takes. Constants, so the choices and the code agree. */
	static final String BY_SKILL = "Skill order";
	static final String BY_LEVEL = "Level";
	static final String BY_EXPERIENCE = "Experience";
	static final String BY_CLOSEST = "Closest to a level";

	@ConfigItem(keyName = "skills", name = "Only these skills, comma separated",
		description = "Part of a name is enough. Blank is all of them")
	public String skills = "";

	@ConfigItem(keyName = "showCombat", name = "Show the combat level row")
	public boolean showCombat = true;

	@ConfigItem(keyName = "showExperience", name = "Show experience under each skill",
		description = "Off is a shorter page")
	public boolean showExperience = true;

	// Inline, because an annotation's array value cannot be a reference to a constant array.
	@ConfigItem(keyName = "sortBy", name = "Order the page by", choices = {
		BY_SKILL, BY_LEVEL, BY_EXPERIENCE, BY_CLOSEST
	})
	public String sortBy = BY_SKILL;

	/** One row of the page: a skill, and everything the page says about it. */
	static final class Skill {

		final String name;
		final int level;
		final int virtualLevel;
		final int experience;
		final int toNext;
		final int percent;

		Skill(String name, int level, int virtualLevel, int experience, int toNext, int percent) {
			this.name = name;
			this.level = level;
			this.virtualLevel = virtualLevel;
			this.experience = experience;
			this.toNext = toNext;
			this.percent = percent;
		}
	}

	protected void startUp() {
		// Guarded, because a page is API level 1 and this plugin may be running on a client that
		// predates it. Everything else here is a config item, which every client has had.
		try {
			this.addPanel("Skills", "list", new ConfigList() {

				public int size() {
					return SkillsPlugin.this.rows().size();
				}

				public String label(int index) {
					Skill skill = SkillsPlugin.this.row(index);
					return skill == null ? "" : skill.name;
				}

				public String detail(int index) {
					Skill skill = SkillsPlugin.this.row(index);
					if (skill == null || !SkillsPlugin.this.showExperience) {
						return "";
					}
					return skill.toNext > 0
						? commas(skill.experience) + " xp, " + commas(skill.toNext) + " to go"
						: commas(skill.experience) + " xp";
				}

				public String value(int index) {
					Skill skill = SkillsPlugin.this.row(index);
					return skill == null ? null : SkillsPlugin.this.levelText(skill);
				}

				public int progress(int index) {
					Skill skill = SkillsPlugin.this.row(index);
					return skill == null ? -1 : skill.percent;
				}

				public boolean removable(int index) {
					return false;
				}

				public String emptyMessage() {
					return "Log in to see your skills.";
				}
			});
		} catch (Throwable olderClient) {
			// No rail pages in this client. Nothing else in the plugin depends on one.
		}
	}

	/**
	 * The rows the page last showed, held for one game tick.
	 *
	 * The page asks size(), then five things about every row, so building the list per question
	 * is twenty-six skills read a hundred and thirty times for one refresh. Held for a tick
	 * rather than for a page read because that does not depend on the order the panel asks its
	 * questions in - a contract this plugin does not own and should not be reading from the
	 * other side of.
	 */
	private List<Skill> cached;

	@Subscribe
	public void onGameTick(GameTick event) {
		this.cached = null;
	}

	/** The rows the page shows. Built at most once a tick; see {@link #cached}. */
	List<Skill> rows() {
		if (this.cached != null) {
			return this.cached;
		}
		List<Skill> rows = new ArrayList<Skill>();
		if (!this.ctx.isLoggedIn()) {
			// Not cached: there is no tick to clear it while logged out, so an empty list held
			// here would still be empty on the first page read after logging back in.
			return rows;
		}
		// The combat level first, as its own row, because it is the number people quote and it
		// belongs to no single skill. It is not sorted with the others for the same reason - a
		// page ordered by level with "Combat level" somewhere in the middle reads as a skill.
		if (this.showCombat) {
			int combat = this.combatLevel();
			rows.add(new Skill("Combat level", combat, combat, 0, 0, -1));
		}
		List<Skill> listed = new ArrayList<Skill>();
		for (int skill = 0; skill < this.ctx.getSkillCount(); skill++) {
			String name = this.ctx.getSkillName(skill);
			if (name.length() == 0 || name.charAt(0) == UNUSED_MARKER
					|| !SkillFilter.allows(name, this.skills)) {
				continue;
			}
			int level = this.ctx.getBaseLevel(skill);
			if (this.hideMaxed && level >= MAX_REAL_LEVEL) {
				continue;
			}
			int experience = this.ctx.getExperience(skill);
			int shown = this.virtual ? this.virtualLevel(experience, level) : level;
			listed.add(new Skill(BoostsPlugin.name(name), level, shown, experience,
				this.experienceToNext(experience, shown), this.percentThrough(experience, shown)));
		}
		sort(listed, this.sortBy);
		rows.addAll(listed);
		this.cached = rows;
		return rows;
	}

	/**
	 * Orders the skill rows in place.
	 *
	 * AN INSERTION SORT, not Collections.sort with a Comparator: twenty-three rows once a tick
	 * is nothing either way, and this keeps the plugin free of a comparator class per order and
	 * of the question of whether the comparator is consistent - which for "closest to a level"
	 * it very nearly is not, two skills both 0 away being a real case.
	 *
	 * STABLE, so skills that tie stay in the client's own order rather than in whatever order a
	 * sort happens to leave them. A page that reshuffles its ties every tick is unreadable.
	 */
	static void sort(List<Skill> rows, String by) {
		if (BY_SKILL.equals(by) || by == null) {
			return;                                      // the client's order, which is the list
		}
		for (int i = 1; i < rows.size(); i++) {
			Skill moving = rows.get(i);
			int at = i;
			while (at > 0 && after(rows.get(at - 1), moving, by)) {
				rows.set(at, rows.get(at - 1));
				at--;
			}
			rows.set(at, moving);
		}
	}

	/**
	 * Whether `first` belongs after `second` in this order. Strictly after, which is what keeps
	 * the sort stable: a tie answers false and nothing moves.
	 */
	static boolean after(Skill first, Skill second, String by) {
		if (BY_LEVEL.equals(by)) {
			return first.virtualLevel < second.virtualLevel;      // highest first
		}
		if (BY_EXPERIENCE.equals(by)) {
			return first.experience < second.experience;
		}
		if (BY_CLOSEST.equals(by)) {
			// Nearest to a level first, and a maxed skill - nothing left to reach - last rather
			// than first, which is where a plain comparison on 0 would put it.
			long left = first.toNext > 0 ? first.toNext : Long.MAX_VALUE;
			long other = second.toNext > 0 ? second.toNext : Long.MAX_VALUE;
			return left > other;
		}
		return false;
	}

	Skill row(int index) {
		List<Skill> rows = this.rows();
		return index < 0 || index >= rows.size() ? null : rows.get(index);
	}

	/** "99 (104)" when a true level is worth saying, else just the level. */
	String levelText(Skill skill) {
		if (skill.virtualLevel > skill.level) {
			return skill.level + " (" + skill.virtualLevel + ")";
		}
		return String.valueOf(skill.level);
	}

	/**
	 * Combat level: a quarter of the defensives plus the best offence, floored.
	 *
	 *   base    = (defence + hitpoints + floor(prayer / 2)) * 0.25
	 *   melee   = (attack + strength) * 0.325
	 *   ranged  = floor(ranged * 3 / 2) * 0.325
	 *   magic   = floor(magic * 3 / 2) * 0.325
	 *   combat  = floor(base + max(melee, ranged, magic))
	 *
	 * FLOORED ONCE, AT THE END. The first version of this floored the base and each offence
	 * separately and came out one or two low everywhere - a new account read 2 instead of 3, and
	 * maxed melee with 99 prayer read 125 instead of 126. Both halves are fractional and the
	 * fractions add up: 61.75 + 64.35 is 126, and 61 + 64 is 125.
	 *
	 * So everything is kept multiplied by 1000 and divided once. No floating point: a quarter and
	 * 0.325 are both exact in thousandths, and nothing here is big enough to need more. The two
	 * floors inside - half the prayer level, and a ranged or magic level times 3/2 - are real
	 * floors in the formula and stay where they are.
	 */
	int combatLevel() {
		int defensives = this.ctx.getBaseLevel(DEFENCE) + this.ctx.getBaseLevel(HITPOINTS)
			+ this.ctx.getBaseLevel(PRAYER) / 2;
		int base = defensives * 250;
		int melee = (this.ctx.getBaseLevel(ATTACK) + this.ctx.getBaseLevel(STRENGTH)) * 325;
		int ranged = this.ctx.getBaseLevel(RANGED) * 3 / 2 * 325;
		int magic = this.ctx.getBaseLevel(MAGIC) * 3 / 2 * 325;
		int best = melee > ranged ? melee : ranged;
		if (magic > best) {
			best = magic;
		}
		return (base + best) / 1000;
	}

	/**
	 * The level the experience really is, continuing past 99.
	 *
	 * Below 99 the client's own answer is used unchanged - its table is the game's table, and a
	 * second opinion about a level a player actually has is a bug waiting to happen. Only past
	 * the end of that table does this work anything out, and then only by continuing the same
	 * formula.
	 */
	int virtualLevel(int experience, int level) {
		if (level < MAX_REAL_LEVEL) {
			return level;
		}
		// A for over the levels there are, not a while that climbs until it stops. The bound is
		// then structural: there is no condition to drop that turns this into a loop that does
		// not end. That matters more than it sounds - experienceForLevel is itself a loop, so an
		// unbounded search here is not slow, it is a client that stops drawing frames.
		int virtual = MAX_REAL_LEVEL;
		for (int next = MAX_REAL_LEVEL + 1; next <= MAX_VIRTUAL_LEVEL; next++) {
			if (experience < experienceForLevel(next)) {
				break;
			}
			virtual = next;
		}
		return virtual;
	}

	/** Experience left to the next level, or 0 when there is no next level to reach. */
	int experienceToNext(int experience, int level) {
		if (level >= MAX_VIRTUAL_LEVEL) {
			return 0;
		}
		int next = experienceForLevel(level + 1);
		return next <= experience ? 0 : next - experience;
	}

	/** How far through the current level, 0 to 100, or -1 when there is no next level. */
	int percentThrough(int experience, int level) {
		if (level >= MAX_VIRTUAL_LEVEL) {
			return -1;
		}
		int start = experienceForLevel(level);
		int end = experienceForLevel(level + 1);
		if (end <= start) {
			return -1;
		}
		int through = experience - start;
		if (through <= 0) {
			return 0;
		}
		return through >= end - start ? 100 : through * 100 / (end - start);
	}

	/**
	 * Experience needed to BE a level, for any level this page shows.
	 *
	 * Up to level 100 this is the client's table, reached through ctx so the client stays the
	 * single source of truth for the levels the game has. Past that the client clamps, so the
	 * same formula is continued here: sum floor(i + 300 * 2^(i/7)) for i below the level, then
	 * divide by four.
	 *
	 * The sum is kept as a long and returned as an int. Level 126 is about 188M, which fits; the
	 * long is so the running total cannot overflow on the way there.
	 */
	int experienceForLevel(int level) {
		if (level <= LAST_TABLED_LEVEL) {
			return this.ctx.getExperienceForLevel(level);
		}
		long points = 0;
		for (int i = 1; i < level; i++) {
			points += (long) Math.floor(i + 300.0 * Math.pow(2.0, i / 7.0));
		}
		long experience = points / 4;
		return experience > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) experience;
	}

	/** "1,234,567". The page is narrow and a bare nine-digit number is unreadable. */
	static String commas(int value) {
		String digits = String.valueOf(value);
		StringBuilder out = new StringBuilder();
		int lead = digits.length() % 3;
		if (lead == 0) {
			lead = 3;
		}
		out.append(digits, 0, lead);
		for (int i = lead; i < digits.length(); i += 3) {
			out.append(',').append(digits, i, i + 3);
		}
		return out.toString();
	}
}
