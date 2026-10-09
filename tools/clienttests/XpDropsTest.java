// Driven by tools/clienttests/run_xptest.py.
//
// In jagex2.client.plugin.builtin so it can call the plugin's package-visible rules - fadeFor,
// visibleFor, easeFor, rowOffset, dropLabel, perHour, isLevelUp, fontFor, formatNumber - with
// values it built by hand. Everything else goes through the PUBLIC manager, exactly as
// Client.java does it: onStatChanged, onGameStateChanged, renderOverlays.
package jagex2.client.plugin.builtin;

import java.util.ArrayList;
import java.util.List;

import jagex2.client.Client;
import jagex2.client.plugin.Overlay;
import jagex2.client.plugin.OverlayGraphics;
import jagex2.client.plugin.PluginApi;
import jagex2.client.plugin.PluginConfig;
import jagex2.client.plugin.PluginManager;
import jagex2.client.plugin.event.GameStateChanged;
import jagex2.client.plugin.event.StatChanged;
import jagex2.dash3d.ClientPlayer;
import jagex2.graphics.Pix2D;
import jagex2.graphics.PixFont;

/**
 * Headless test for the XP drops plugin.
 *
 * WHAT IS REAL. The plugin is the shipped one, started by the real PluginManager, drawing through
 * the real OverlayGraphics into a real Pix2D buffer, reading real skill levels and the client's
 * real experience table through the real PluginContext, with its settings written through the
 * real config path. The font is a PixFont subclass that records (x, y, colour, text) and paints
 * nothing, because glyphs come out of a cache a headless test has none of - and recording the
 * call IS the measurement for a feature that is almost entirely text.
 *
 * THE ONE THING WORTH SAYING ABOUT THE DESIGN, because every check about the fade depends on it:
 * a row's fade timer is set once when the row is created and NEVER refreshed. Every row leaves on
 * its own schedule no matter what gains experience after it, which is what makes the column read
 * as drops rather than as a list. Grouping had to be built inside that rule rather than around
 * it: adding into a row does not extend it.
 */
public class XpDropsTest {

	static final int W = 512;
	static final int H = 334;

	/** Skill indices, as jagex2.client.Stats names them. */
	static final int ATTACK = 0;
	static final int DEFENCE = 1;

	static int fails;
	static int passes;

	static Client client;
	static PluginManager manager;
	static PluginManager.Entry entry;
	static RecordingFont font;
	static RecordingFont small;
	static RecordingFont bold;
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

	public static void main(String[] args) {
		// The rules half needs nothing but the plugin's own class, so it runs either way.
		System.out.println("1. the rules, on their own");
		ruleTests();

		if (java.awt.GraphicsEnvironment.isHeadless()) {
			System.out.println("  SKIP  no display, so a Client cannot be constructed - run with xvfb-run");
			System.out.println(fails == 0 ? "ALL PASS (the drawing sections were skipped)"
				: fails + " FAILED");
			System.exit(fails == 0 ? 0 : 1);
		}

		setUp();
		System.out.println("2. the settings, and what they are out of the box");
		settingTests();
		System.out.println("3. a drop on screen");
		dropTests();
		System.out.println("4. grouping");
		groupTests();
		System.out.println("5. the panel above them");
		trackerTests();
		System.out.println("6. level ups");
		levelTests();

		System.out.println();
		System.out.println(fails == 0 ? (passes + " CHECKS, ALL PASS")
			: (fails + " FAILED of " + (passes + fails)));
		System.exit(fails == 0 ? 0 : 1);
	}

	// ---------------------------------------------------------------- 1

	/**
	 * Every number a player can type, at its bounds and just inside them.
	 *
	 * These are the checks that cost nothing and catch the most: each setting is a box someone
	 * can put anything in, and the failure mode of an unclamped one is the feature disappearing
	 * rather than an error.
	 */
	static void ruleTests() {
		// ---- the fade
		check(XpDropsPlugin.fadeFor(1500) == 1500, "a fade in range is kept");
		check(XpDropsPlugin.fadeFor(0) == XpDropsPlugin.MIN_FADE_MS,
			"a 0ms fade is a drop nobody sees, so it is floored");
		check(XpDropsPlugin.fadeFor(-5000) == XpDropsPlugin.MIN_FADE_MS, "...and so is a negative");
		check(XpDropsPlugin.fadeFor(99_999_999) == XpDropsPlugin.MAX_FADE_MS,
			"a fade longer than a minute is a column that never clears");
		check(XpDropsPlugin.fadeFor(XpDropsPlugin.MIN_FADE_MS) == XpDropsPlugin.MIN_FADE_MS
				&& XpDropsPlugin.fadeFor(XpDropsPlugin.MAX_FADE_MS) == XpDropsPlugin.MAX_FADE_MS,
			"...and both bounds are themselves allowed, rather than being one past");
		// AS NUMBERS. The two checks above are self-referential - raising MIN_FADE_MS moves both
		// sides of them - so the bounds have to be stated somewhere as the figures they are, or
		// a tenth of a second could quietly become three.
		check(XpDropsPlugin.MIN_FADE_MS == 100,
			"the shortest fade allowed is a tenth of a second (" + XpDropsPlugin.MIN_FADE_MS + ")");
		check(XpDropsPlugin.MAX_FADE_MS == 60_000,
			"and the longest a minute (" + XpDropsPlugin.MAX_FADE_MS + ")");
		check(XpDropsPlugin.MIN_VISIBLE == 1 && XpDropsPlugin.MAX_VISIBLE == 32,
			"between one row and thirty-two");
		check(XpDropsPlugin.MIN_SPEED == 1 && XpDropsPlugin.MAX_SPEED == 100,
			"and between 1% and 100% of the gap a frame");

		// ---- how many rows
		check(XpDropsPlugin.visibleFor(8) == 8, "a row count in range is kept");
		check(XpDropsPlugin.visibleFor(0) == 1 && XpDropsPlugin.visibleFor(-1) == 1,
			"zero rows is the feature turned off by a typo, so one is the floor");
		check(XpDropsPlugin.visibleFor(1000) == XpDropsPlugin.MAX_VISIBLE,
			"and a thousand rows is more than the screen has");

		// ---- the easing
		check(XpDropsPlugin.easeFor(25) == 0.25f, "25% closes a quarter of the gap a frame");
		check(XpDropsPlugin.easeFor(100) == 1.0f,
			"100% is no easing at all, which is a choice rather than a bug");
		check(XpDropsPlugin.easeFor(0) == 0.01f,
			"0% would be a row that never arrives, so the floor is 1%");
		check(XpDropsPlugin.easeFor(-50) == 0.01f && XpDropsPlugin.easeFor(5000) == 1.0f,
			"...and nonsense either way lands on a bound");

		// ---- which way the column runs
		check(XpDropsPlugin.rowOffset(0f, 27, false) == 0, "the first row sits at the top");
		check(XpDropsPlugin.rowOffset(2f, 27, false) == 54, "the third is two rows down");
		check(XpDropsPlugin.rowOffset(2f, 27, true) == -54, "...and two rows UP when it flows up");
		check(XpDropsPlugin.rowOffset(0f, 27, true) == 0,
			"the first row is in the same place either way, so switching does not jump it");
		check(XpDropsPlugin.rowOffset(1.5f, 27, false) == 41,
			"a row mid-ease is rounded rather than truncated (41)");

		// ---- the label
		check(XpDropsPlugin.dropLabel(500, "Attack", false).equals("+500"),
			"a row is the amount with a plus");
		check(XpDropsPlugin.dropLabel(500, "Attack", true).equals("+500 Attack"),
			"...and the skill after it when asked");
		check(XpDropsPlugin.dropLabel(500, null, true).equals("+500"),
			"a skill with no name does not leave a trailing space");
		check(XpDropsPlugin.dropLabel(500, "", true).equals("+500"), "...nor an empty one");

		// ---- experience per hour
		check(XpDropsPlugin.perHour(1000L, 3_600_000L) == 1000L,
			"a thousand in an hour is a thousand an hour");
		check(XpDropsPlugin.perHour(1000L, 1_800_000L) == 2000L, "...and in half an hour, two");
		check(XpDropsPlugin.perHour(1000L, 0L) == 0L,
			"THE FIRST MILLISECOND IS NOT A RATE: dividing by it gives a number in the hundreds "
				+ "of millions, which measures the denominator rather than the player");
		check(XpDropsPlugin.perHour(1000L, 999L) == 0L, "...and nor is the first second");
		check(XpDropsPlugin.perHour(1000L, 1000L) == 3_600_000L,
			"at exactly a second it starts answering");
		check(XpDropsPlugin.perHour(0L, 3_600_000L) == 0L, "no gain is no rate, not a divide");
		check(XpDropsPlugin.perHour(-50L, 3_600_000L) == 0L, "...and nor is a negative one");
		// A long session of a maxed account leaves int behind, which is why the figure is a long
		// all the way through rather than being narrowed for the format call.
		check(XpDropsPlugin.perHour(4_000_000_000L, 1000L) > (long) Integer.MAX_VALUE,
			"a rate past int is still a number rather than a wrapped negative");

		// ---- level ups
		check(XpDropsPlugin.isLevelUp(1200, 10, 1154),
			"passing the next level's experience is a level up");
		check(!XpDropsPlugin.isLevelUp(1100, 10, 1154), "...and falling short of it is not");
		check(XpDropsPlugin.isLevelUp(1154, 10, 1154), "landing exactly on it counts");
		check(!XpDropsPlugin.isLevelUp(99_000_000, 99, 13_034_431),
			"A MAXED SKILL NEVER LEVELS UP. getExperienceForLevel has nothing past 100 and "
				+ "answers 99's own figure, so without this every drop at 99 would announce one");
		check(!XpDropsPlugin.isLevelUp(99_000_000, 120, 13_034_431),
			"...nor does a skill somehow past 99");
		check(!XpDropsPlugin.isLevelUp(1200, 0, 1154),
			"a skill with no level yet is a sync rather than a level up");
		check(!XpDropsPlugin.isLevelUp(1200, 10, 0),
			"and no next level means nothing to reach");

		// ---- the font
		check(XpDropsPlugin.fontFor("Bold") == OverlayGraphics.FONT_BOLD, "Bold is the bold font");
		check(XpDropsPlugin.fontFor("Small") == OverlayGraphics.FONT_SMALL, "Small is the small one");
		check(XpDropsPlugin.fontFor("Normal") == OverlayGraphics.FONT_NORMAL, "Normal is plain 12");
		check(XpDropsPlugin.fontFor("Comic Sans") == OverlayGraphics.FONT_NORMAL,
			"and an unknown value falls back rather than throwing out of a render loop");
		// CAUGHT RATHER THAN THROWN. fontFor runs from render, so a value it cannot read has to
		// fall back rather than raise - and an exception here would end the run with a stack
		// trace rather than a named check, which the mutation runner correctly refuses to count.
		boolean fellBack = false;
		boolean threw = false;
		try {
			fellBack = XpDropsPlugin.fontFor(null) == OverlayGraphics.FONT_NORMAL;
		} catch (Throwable error) {
			threw = true;
		}
		check(!threw && fellBack,
			"a missing font falls back rather than throwing out of a render loop"
				+ (threw ? " (it threw)" : ""));

		// ---- the grouped total
		check(XpDropsPlugin.formatNumber(227731L).equals("227,731"), "a total is grouped in threes");
		check(XpDropsPlugin.formatNumber(999L).equals("999"), "...under a thousand, untouched");
		check(XpDropsPlugin.formatNumber(1000L).equals("1,000"), "...and at one exactly");
		check(XpDropsPlugin.formatNumber(13_034_431L).equals("13,034,431"), "...and at 99");
		check(XpDropsPlugin.formatNumber(4_000_000_000L).equals("4,000,000,000"),
			"...and past int, which a per-hour figure reaches");
	}

	// ---------------------------------------------------------------- 2

	static void settingTests() {
		check(PluginApi.LEVEL >= 7,
			"this plugin reads a font size through OverlayGraphics, which is API level 7");
		check(read("src/main/java/jagex2/client/plugin/builtin/XpDropsPlugin.java")
				.indexOf("apiLevel = 7") >= 0,
			"...and declares it, so an older client refuses it rather than breaking on a draw");

		// READ BEFORE ANYTHING WRITES THEM. The defaults are the behaviour this plugin shipped
		// with: a player who upgrades and changes nothing has to see exactly what they saw.
		check(bool("showTracker") && !bool("showPerHour") && !bool("notifyLevelUp"),
			"the panel is on, the rate and the notification off");
		check(bool("showIcons") && !bool("showSkillName") && !bool("textOutline"),
			"icons on, the skill name and the outline off");
		check(!bool("groupSameSkill"),
			"EVERY GAIN IS ITS OWN ROW out of the box, which is the decision the feature was "
				+ "tuned around rather than a default nobody chose");
		check(number("fadeMs") == XpDropsPlugin.DEFAULT_FADE_MS
				&& number("fadeMs") == 1500,
			"the fade is the 1500ms it was tuned to: 3000 was too slow and 600 too fast");
		check(number("trackerFadeMs") == 6000, "the panel outlives a drop, at 6000ms");
		check(number("maxVisible") == 8, "eight rows, as before");
		check(number("speed") == 25, "and a quarter of the gap a frame, as before");
		check(text("dropColour").equals("FFFF00") && text("progressColour").equals("C8641E"),
			"and both colours are the ones that were hardcoded");
		check(text("direction").equals(XpDropsPlugin.DOWN), "the column runs down");

		// The editors each setting gets, which is what the sidebar reads to draw them.
		String[] colours = { "dropColour", "progressColour" };
		for (int i = 0; i < colours.length; i++) {
			check(setting(colours[i]) != null && setting(colours[i]).isColour(),
				colours[i] + " is edited as a colour, not as six characters of text");
		}
		String[] numbers = { "fadeMs", "trackerFadeMs", "maxVisible", "speed" };
		for (int i = 0; i < numbers.length; i++) {
			check(setting(numbers[i]) != null && setting(numbers[i]).isInt(),
				numbers[i] + " is a number a player types");
		}
		// THE COUNT, AGAINST THE README, the way Ground items pins its own: the page said
		// "Eighteen settings" for a plugin that had twenty-five until a check held it honest.
		int count = entry.getConfig().getItems().size();
		check(read("plugins/README.md").indexOf("### XP drops, in detail") >= 0,
			"the README has a section for this plugin");
		check(read("plugins/README.md").indexOf(numberWord(count) + " settings, and the thing") >= 0,
			"...and says how many settings it has, which is " + count);

		check(setting("direction") != null && setting("direction").choices().length == 3,
			"which way the column runs is a drop-down of three");
		check(setting("font") != null && setting("font").choices().length == 3,
			"and the text size is a drop-down of three");

		// THE DROP-DOWN VALUES HAVE TO BE THE ONES THE CODE COMPARES AGAINST. A drop-down whose
		// entries no branch matches is a control that silently does nothing - and the compiler
		// cannot see the mismatch, because both sides are strings.
		check(hasChoice("direction", XpDropsPlugin.DOWN)
				&& hasChoice("direction", XpDropsPlugin.UP)
				&& hasChoice("direction", XpDropsPlugin.STILL),
			"every direction the code branches on is offered");
		check(hasChoice("font", XpDropsPlugin.FONT_PLAIN)
				&& hasChoice("font", XpDropsPlugin.FONT_BIG)
				&& hasChoice("font", XpDropsPlugin.FONT_TINY),
			"...and every font size");
		String[] fonts = setting("font").choices();
		for (int i = 0; i < fonts.length; i++) {
			check(XpDropsPlugin.fontFor(fonts[i]) == OverlayGraphics.FONT_BOLD
					|| XpDropsPlugin.fontFor(fonts[i]) == OverlayGraphics.FONT_SMALL
					|| fonts[i].equals(XpDropsPlugin.FONT_PLAIN),
				"the \"" + fonts[i] + "\" choice reaches a font of its own rather than the "
					+ "fallback every unknown value gets");
		}
	}

	// ---------------------------------------------------------------- 3

	static void dropTests() {
		restore();
		set("showTracker", "false");           // the panel has its own section
		gain(ATTACK, 101_433, 100);
		List<Drawn> rows = frame();
		check(rows.size() == 1, "a gain draws one row (" + rows.size() + ")");
		check(rows.get(0).text.equals("+100"), "...saying what was gained");
		check(rows.get(0).colour == 0xFFFF00, "...in the yellow it always was");

		// The colour, which was hardcoded.
		set("dropColour", "40FF40");
		check(frame().get(0).colour == 0x40FF40, "a colour a player picks is what gets drawn");
		set("dropColour", "FFFF00");

		// The skill's name, read off the real client rather than spelled here: Stats is where
		// the names live, and a test that hardcoded "Attack" would pass against the wrong skill.
		set("showSkillName", "true");
		String named = frame().get(0).text;
		// SPELLED OUT, not run through BoostsPlugin.name(). Comparing against that asks the
		// transformation to confirm itself - delete the capitalisation and both sides change
		// together - and the audit found it deletable with this check still green.
		check(jagex2.client.Stats.field1504[ATTACK].equals("attack"),
			"the client stores skill names lower case, which is why they are capitalised at all");
		check(named.equals("+100 Attack"),
			"the skill name goes after the amount, with a capital (" + named + ")");
		set("showSkillName", "false");
		check(frame().get(0).text.equals("+100"), "...and off again it is the amount alone");

		// THE OUTLINE. Four flat passes plus the coloured one, so the count of draws is what
		// tells them apart - a shadow is one call, an outline is five.
		check(frame().size() == 1, "without the outline a row is one draw");
		set("textOutline", "true");
		List<Drawn> outlined = frame();
		check(outlined.size() == 5,
			"with it, four black passes and the coloured one (" + outlined.size() + ")");
		int black = 0;
		int coloured = 0;
		for (int i = 0; i < outlined.size(); i++) {
			if (outlined.get(i).colour == 0) {
				black++;
			} else if (outlined.get(i).colour == 0xFFFF00) {
				coloured++;
			}
		}
		check(black == 4 && coloured == 1, "...one each way, with the colour on top");
		set("textOutline", "false");

		// THE SIZE, which is a different font object rather than a different number: the only way
		// to ask "which font did it draw in" is to give the manager three and see which one was
		// handed the row.
		frame();
		check(font.calls == 1 && small.calls == 0 && bold.calls == 0,
			"the normal size draws in the normal font and no other");
		set("font", XpDropsPlugin.FONT_BIG);
		frame();
		check(bold.calls == 1 && font.calls == 0 && small.calls == 0,
			"Bold draws in the bold font (" + bold.calls + ")");
		set("font", XpDropsPlugin.FONT_TINY);
		frame();
		check(small.calls == 1 && font.calls == 0 && bold.calls == 0,
			"Small draws in the small one (" + small.calls + ")");
		set("font", XpDropsPlugin.FONT_PLAIN);
		frame();
		check(font.calls == 1, "and Normal comes back to plain 12");

		// THE CAP, which now applies while rows are up rather than only on the next gain: a
		// player lowering it and seeing nothing change would read that as broken.
		for (int i = 0; i < 6; i++) {
			gain(DEFENCE, 200_000 + i, 1);
		}
		check(frame().size() == 7, "seven rows are up (" + frame().size() + ")");
		set("maxVisible", "3");
		check(frame().size() == 3, "lowering the cap drops the oldest rows at once");
		set("maxVisible", "1");
		check(frame().size() == 1, "...down to one");
		check(frame().get(0).text.equals("+1"), "...and it is the NEWEST that is kept");
		set("maxVisible", "8");

		// AND THE CAP ON THE WAY IN, which is a second cap: one trims as gains arrive and one
		// trims at draw time, and the audit found the arriving one could be left at the old
		// constant with every check above still green, because they all set the cap AFTER the
		// gains and only ever exercised the draw.
		restore();
		set("showTracker", "false");
		set("maxVisible", "2");
		for (int i = 0; i < 5; i++) {
			gain(DEFENCE, 210_000 + i, 1);
		}
		check(frame().size() == 2,
			"with the cap set first, five gains leave two rows (" + frame().size() + ")");
		set("maxVisible", "8");

		// THE FADE. The one thing here that has to be waited for, because a row's timer is set
		// when it is created and never refreshed.
		restore();
		set("showTracker", "false");
		set("fadeMs", "200");
		gain(ATTACK, 101_533, 100);
		check(frame().size() == 1, "a row is up");
		sleep(350);
		check(frame().size() == 0, "and gone once its own fade has run, with nothing refreshing it");

		// A gain of nothing is login's sync of the whole account, and must not rain down it.
		restore();
		set("showTracker", "false");
		gain(ATTACK, 101_633, 0);
		check(frame().size() == 0, "a stat change that gained nothing draws no row");

		// Logging out clears what is on screen, so the next session does not start mid-fade.
		gain(ATTACK, 101_733, 100);
		check(frame().size() == 1, "a real gain is up");
		logout();
		check(frame().size() == 0, "and logging out clears it");
		login();
	}

	// ---------------------------------------------------------------- 4

	/**
	 * Adding gains up instead of a row per gain.
	 *
	 * THE HARD PART IS THAT IT MUST NOT EXTEND THE ROW. A running total that refreshed its own
	 * fade would be a row that never leaves while you train - which is a different feature, and
	 * one the drops were explicitly tuned away from.
	 */
	static void groupTests() {
		restore();
		set("showTracker", "false");

		// Off: two gains in the same skill are two rows, which is the default behaviour.
		gain(ATTACK, 101_833, 100);
		gain(ATTACK, 101_933, 100);
		check(frame().size() == 2, "with grouping off, two gains are two rows");

		restore();
		set("showTracker", "false");
		set("groupSameSkill", "true");
		gain(ATTACK, 102_033, 100);
		gain(ATTACK, 102_133, 100);
		List<Drawn> rows = frame();
		check(rows.size() == 1, "with it on, they are one row (" + rows.size() + ")");
		check(rows.get(0).text.equals("+200"), "...adding up to +200");
		gain(ATTACK, 102_183, 50);
		check(frame().get(0).text.equals("+250"), "...and a third adds into it");

		// A DIFFERENT SKILL STARTS A NEW ROW, and the old one keeps its own total: grouping is
		// per skill, not a single counter.
		gain(DEFENCE, 300_000, 70);
		rows = frame();
		check(rows.size() == 2, "a different skill starts a new row");
		check(rows.get(0).text.equals("+70"), "...the new skill's, on top");
		check(rows.get(1).text.equals("+250"), "...and the first row keeps its total");

		// Back to the first skill: it does NOT reach past the newest row to find its own. That
		// row has already eased into place and had another arrive after it, and growing it would
		// make a number change in the middle of the column with nothing to explain it.
		gain(ATTACK, 102_283, 100);
		rows = frame();
		check(rows.size() == 3, "coming back to the first skill starts another row");
		check(rows.get(0).text.equals("+100"),
			"...rather than reaching past the newest row to grow a settled one");

		// AND IT DOES NOT EXTEND THE FADE.
		restore();
		set("showTracker", "false");
		set("groupSameSkill", "true");
		set("fadeMs", "300");
		gain(ATTACK, 102_383, 100);
		sleep(180);
		gain(ATTACK, 102_483, 100);
		check(frame().get(0).text.equals("+200"), "a grouped row has both gains in it");
		sleep(200);
		check(frame().size() == 0,
			"and leaves on the schedule its FIRST gain set, rather than being kept alive by the "
				+ "second");
	}

	// ---------------------------------------------------------------- 5

	static void trackerTests() {
		restore();
		gain(ATTACK, 101_433, 100);
		int painted = painted();
		check(painted > 0, "the panel is drawn as pixels (" + painted + ")");
		check(rightmostPainted() > W / 2 && topmostPainted() < H / 2,
			"...in the top-right corner it has always used");

		set("showTracker", "false");
		check(painted() == 0, "turning it off leaves the box undrawn");
		set("showTracker", "true");
		check(painted() > 0, "and on again brings it back");

		// Its own fade, separate from a drop's. Set BEFORE the gain: the panel's deadline is
		// stamped when a gain arrives, so a shorter fade applies to the next one rather than
		// retroactively to the panel already up.
		set("trackerFadeMs", "200");
		gain(ATTACK, 101_533, 100);
		check(painted() > 0, "the panel is up");
		sleep(350);
		check(painted() == 0, "and gone once its own fade has run");
		set("trackerFadeMs", Integer.toString(XpDropsPlugin.DEFAULT_TRACKER_FADE_MS));

		// The progress bar's colour, which was hardcoded. Counted as pixels, because the bar is
		// a fill rather than text - so this is the one part of the panel the font cannot see.
		restore();
		// HALFWAY THROUGH A LEVEL, not a hundred experience into it: level 50 spans 101,333 to
		// 111,945, so 100xp in fills 100/10612 of the bar - zero pixels, which is a correct bar
		// and a test that can never see a colour.
		setLevel(ATTACK, 50, 106_000);
		set("progressColour", "FF00FF");
		gain(ATTACK, 106_100, 100);
		check(countPixels(0xFF00FF) > 0, "the bar is drawn in the colour a player picked");
		set("progressColour", "C8641E");
		check(countPixels(0xFF00FF) == 0 && countPixels(0xC8641E) > 0, "...and changes with it");

		// EXPERIENCE PER HOUR. Off by default; on, it adds a line and the box grows to hold it.
		restore();
		set("showPerHour", "false");
		gain(ATTACK, 101_433, 100);
		int shortBox = tallest();
		List<Drawn> plain = frame();
		set("showPerHour", "true");
		List<Drawn> rated = frame();
		check(rated.size() == plain.size() + 1, "the rate adds one line to the panel ("
			+ plain.size() + " -> " + rated.size() + ")");
		boolean perHour = false;
		for (int i = 0; i < rated.size(); i++) {
			perHour = perHour || rated.get(i).text.endsWith("/hr");
		}
		check(perHour, "...which says what it is");
		check(tallest() > shortBox,
			"...and the box grows to hold it rather than drawing over its own bar");

		// THE NUMBER ITSELF, which is the part every check above was blind to: perHour answers 0
		// until the session has run a second, so a panel checked straight after a gain says
		// "0/hr" whatever the bookkeeping behind it does. The audit found all three of the
		// session's mutations survivable - the clock restarted per gain, the total not
		// accumulated, and the session carried across a logout - because nothing ever read it.
		restore();
		set("showPerHour", "true");
		gain(ATTACK, 102_000, 1000);
		check(rateShown() == 0L,
			"before a second has passed there is no rate yet, only a placeholder");
		sleep(1200);
		gain(ATTACK, 103_000, 1000);
		long rate = rateShown();
		check(rate > 0L, "THE CLOCK RUNS FROM THE FIRST GAIN: restarted on each one, the elapsed "
			+ "time would be milliseconds and the rate would stay at its placeholder 0");
		// Two gains of 1000 over about 1.2 seconds is about 6 MILLION an hour - 2000 * 3,600,000
		// / 1200 - and one gain alone would be about 3. The bound sits between them, so it reads
		// the TOTAL rather than the last drop, and is loose enough not to care about the exact
		// sleep. (Six billion was the first version of this, off by the factor the sleep is.)
		check(rate > 4_000_000L,
			"...and over BOTH gains rather than only the latest (" + rate + ")");
		check(rate < 20_000_000L, "...with the hour the right way up (" + rate + ")");

		// A LOGOUT CLEARS IT. Carried across, the clock would still be running from the last
		// session and the rate would be measured over time nobody was playing.
		logout();
		login();
		gain(ATTACK, 104_000, 1000);
		check(rateShown() == 0L,
			"logging out starts the session again, rather than reporting a rate over time "
				+ "nobody played");
	}

	/**
	 * The per-hour figure as the panel actually drew it: the "/hr" line, commas out, parsed.
	 *
	 * Read off the draw rather than asked of the plugin, because what a player sees is the point
	 * and the formatting is between the arithmetic and them.
	 */
	static long rateShown() {
		List<Drawn> rows = frame();
		for (int i = 0; i < rows.size(); i++) {
			String text = rows.get(i).text;
			if (text.endsWith("/hr")) {
				try {
					return Long.parseLong(text.substring(0, text.length() - 3).replace(",", ""));
				} catch (NumberFormatException notANumber) {
					check(false, "the per-hour line is a number (" + text + ")");
					return -1L;
				}
			}
		}
		check(false, "the panel has a per-hour line");
		return -1L;
	}

	// ---------------------------------------------------------------- 6

	/**
	 * Level ups, read against the level the client still holds.
	 *
	 * The event is posted from the packet handler BEFORE the client recomputes skillBaseLevel, so
	 * getBaseLevel inside the handler is the level the player had a moment ago. That ordering is
	 * what lets this work without the plugin keeping a copy of the experience curve, and it is
	 * also the thing that would silently break if the packet handler were ever reordered - which
	 * is why the check below drives the real event rather than the pure rule.
	 */
	static void levelTests() {
		restore();

		// EVERY NEGATIVE CHECK HERE SLEEPS FIRST. PluginContext rate-limits notifications to one
		// every 1500ms across all plugins, so a silence measured right after a notification is
		// the cooldown rather than the rule - and three of the four checks below are silences.
		// Without the waits this section would pass with the whole feature deleted.
		set("notifyLevelUp", "false");
		setLevel(ATTACK, 10, 1300);
		sleep(COOLDOWN_MS);
		int before = chatLines();
		gain(ATTACK, 1400, 100);
		check(chatLines() == before, "with the setting off, a level up says nothing");

		// Level 10 runs 1,154 to 1,357 and level 11 starts at 1,358, so this crosses and the
		// one below it does not. Taken off the client's own table rather than guessed: the first
		// version of this section used numbers that crossed nothing, and "it said nothing" was
		// the right answer to the wrong question.
		set("notifyLevelUp", "true");
		setLevel(ATTACK, 10, 1300);
		sleep(COOLDOWN_MS);
		before = chatLines();
		check(client.getClass() != null && Client.levelExperience[9] == 1358,
			"level 11 starts at 1,358 on the client's own table ("
				+ Client.levelExperience[9] + ")");
		gain(ATTACK, 1400, 100);
		check(chatLines() > before, "with it on, crossing a level says so");

		setLevel(ATTACK, 10, 1300);
		sleep(COOLDOWN_MS);
		before = chatLines();
		gain(ATTACK, 1350, 50);
		check(chatLines() == before, "a gain that does not cross one says nothing");

		// A MAXED SKILL, which is the case the guard exists for: without it, a 99 would announce
		// a level up on every single drop for the rest of the account's life.
		setLevel(ATTACK, 99, 13_100_000);
		sleep(COOLDOWN_MS);
		before = chatLines();
		gain(ATTACK, 13_200_000, 100_000);
		check(chatLines() == before, "and a 99 never does, however much it gains");

		// ...and that the wait is what makes those silences mean something: a second crossing
		// after it does speak, so the three above were not simply all suppressed.
		setLevel(ATTACK, 10, 1300);
		sleep(COOLDOWN_MS);
		before = chatLines();
		gain(ATTACK, 1400, 100);
		check(chatLines() > before, "and the notification still works after all of that");
	}

	/** PluginContext.NOTIFY_EVERY_MS, plus enough to be past it. */
	static final long COOLDOWN_MS = 1600L;

	// ---------------------------------------------------------------- the harness

	static void setUp() {
		client = new Client();
		client.ingame = true;
		Client.localPlayer = new ClientPlayer();

		// THREE FONTS, NOT ONE OBJECT THREE TIMES. Handing the manager the same font for all
		// three sizes makes "it drew in the bold font" unaskable - the audit found the size
		// drop-down could be ignored entirely with every check still green. `font` stays as the
		// normal one, which is what the existing checks read.
		List<Drawn> shared = new ArrayList<Drawn>();
		small = new RecordingFont(shared);
		font = new RecordingFont(shared);
		bold = new RecordingFont(shared);
		manager = new PluginManager(client, small, font, bold);
		manager.reload();

		List<PluginManager.Entry> entries = manager.getPlugins();
		for (int i = 0; i < entries.size(); i++) {
			PluginManager.Entry e = entries.get(i);
			if ("xp-drops".equals(e.key)) {
				entry = e;
			} else if (e.isEnabled()) {
				// Everything else off, so a row or a pixel can only have come from this plugin.
				manager.setEnabled(e, false);
			}
		}
		check(entry != null && entry.isEnabled(), "the XP drops plugin is on by default");
		if (entry == null) {
			System.out.println("1 FAILED");
			System.exit(1);
		}
		restore();
	}

	/** Every setting back to its default, and nothing on screen. */
	static void restore() {
		set("showTracker", "true");
		set("showPerHour", "false");
		set("notifyLevelUp", "false");
		set("showIcons", "true");
		set("showSkillName", "false");
		set("textOutline", "false");
		set("groupSameSkill", "false");
		set("dropColour", "FFFF00");
		set("progressColour", "C8641E");
		set("direction", XpDropsPlugin.DOWN);
		set("font", XpDropsPlugin.FONT_PLAIN);
		set("fadeMs", Integer.toString(XpDropsPlugin.DEFAULT_FADE_MS));
		set("trackerFadeMs", Integer.toString(XpDropsPlugin.DEFAULT_TRACKER_FADE_MS));
		set("maxVisible", Integer.toString(XpDropsPlugin.DEFAULT_VISIBLE));
		set("speed", Integer.toString(XpDropsPlugin.DEFAULT_SPEED));
		logout();
		login();
	}

	/**
	 * Out of the game and back in, the way the manager notices it: there is no logout hook, one
	 * check a frame catches all half-dozen ways out, so the harness drives the frame.
	 */
	static void logout() {
		client.ingame = false;
		manager.onClientTick(tick++);
	}

	static void login() {
		client.ingame = true;
		Client.localPlayer = new ClientPlayer();
		manager.onClientTick(tick++);
	}

	static int tick = 1;

	/** A skill's level and experience, the way the client holds them. */
	static void setLevel(int skill, int level, int experience) {
		client.skillLevel[skill] = level;
		client.skillBaseLevel[skill] = level;
		client.skillExperience[skill] = experience;
	}

	/** A stat change, through the real manager, as the packet handler posts it. */
	static void gain(int skill, int experience, int gained) {
		manager.onStatChanged(skill, client.skillLevel[skill], experience, gained);
		// The packet handler updates the client AFTER posting, and a level-up check reads the
		// level from before - so the harness has to do it in that order too.
		client.skillExperience[skill] = experience;
		client.skillBaseLevel[skill] = 1;
		for (int i = 0; i < 98; i++) {
			if (experience >= Client.levelExperience[i]) {
				client.skillBaseLevel[skill] = i + 2;
			}
		}
	}

	/**
	 * One frame, into a cleared buffer with a cleared font. Both accumulate.
	 *
	 * A COPY, NOT THE FONT'S OWN LIST. Returning the live list makes every comparison between
	 * two frames vacuous - the next frame clears and refills the same object, so both variables
	 * describe the later one and "it changed" can never be false. One check here failed exactly
	 * that way, reporting two identical frames that were the same list seen twice.
	 */
	static List<Drawn> frame() {
		font.rows.clear();                 // one list, shared by all three sizes
		small.calls = 0;
		font.calls = 0;
		bold.calls = 0;
		java.util.Arrays.fill(pixels, 0);
		Pix2D.bind(W, H, pixels);
		manager.renderOverlays(W, H, Overlay.LAYER_SCREEN);
		return new ArrayList<Drawn>(font.rows);
	}

	static int painted() {
		frame();
		int n = 0;
		for (int i = 0; i < pixels.length; i++) {
			if (pixels[i] != 0) {
				n++;
			}
		}
		return n;
	}

	static int countPixels(int colour) {
		frame();
		int n = 0;
		for (int i = 0; i < pixels.length; i++) {
			if ((pixels[i] & 0xFFFFFF) == colour) {
				n++;
			}
		}
		return n;
	}

	static int rightmostPainted() {
		int best = -1;
		for (int y = 0; y < H; y++) {
			for (int x = 0; x < W; x++) {
				if (pixels[y * W + x] != 0 && x > best) {
					best = x;
				}
			}
		}
		return best;
	}

	static int topmostPainted() {
		for (int y = 0; y < H; y++) {
			for (int x = 0; x < W; x++) {
				if (pixels[y * W + x] != 0) {
					return y;
				}
			}
		}
		return H;
	}

	/** The painted box's height, for "it grew to hold another line". */
	static int tallest() {
		frame();
		int top = -1;
		int bottom = -1;
		for (int y = 0; y < H; y++) {
			for (int x = 0; x < W; x++) {
				if (pixels[y * W + x] != 0) {
					if (top < 0) {
						top = y;
					}
					bottom = y;
					break;
				}
			}
		}
		return top < 0 ? 0 : bottom - top + 1;
	}

	/** Chat lines the plugin has added, which is where a notification lands. */
	static int chatLines() {
		int n = 0;
		for (int i = 0; i < client.messageText.length; i++) {
			if (client.messageText[i] != null) {
				n++;
			}
		}
		return n;
	}

	static void set(String key, String value) {
		List<PluginConfig.Item> items = entry.getConfig().getItems();
		for (int i = 0; i < items.size(); i++) {
			if (items.get(i).key.equals(key)) {
				check(entry.getConfig().set(items.get(i), value),
					"setting " + key + " to " + value + " is accepted");
				return;
			}
		}
		check(false, "there is a setting called " + key);
	}

	static PluginConfig.Item setting(String key) {
		List<PluginConfig.Item> items = entry.getConfig().getItems();
		for (int i = 0; i < items.size(); i++) {
			if (items.get(i).key.equals(key)) {
				return items.get(i);
			}
		}
		return null;
	}

	static boolean bool(String key) {
		PluginConfig.Item item = setting(key);
		return item != null && item.booleanValue();
	}

	static int number(String key) {
		PluginConfig.Item item = setting(key);
		return item == null ? -1 : item.intValue();
	}

	static String text(String key) {
		PluginConfig.Item item = setting(key);
		return item == null ? "" : item.stringValue();
	}

	static boolean hasChoice(String key, String choice) {
		PluginConfig.Item item = setting(key);
		if (item == null) {
			return false;
		}
		String[] choices = item.choices();
		for (int i = 0; i < choices.length; i++) {
			if (choices[i].equals(choice)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * "Fifteen" for 15. The README is prose and a digit reads wrong in it, so the check has to
	 * speak the same language the page does - for the handful of counts a plugin can have.
	 */
	static String numberWord(int n) {
		String[] words = { "Zero", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight",
			"Nine", "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen",
			"Seventeen", "Eighteen", "Nineteen", "Twenty" };
		return n >= 0 && n < words.length ? words[n] : Integer.toString(n);
	}

	static void sleep(long ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
		}
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

	/** One drawn string, as the font saw it. */
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

		public String toString() {
			return text + "@" + y;
		}
	}

	/**
	 * Paints nothing and records every call, because recording it IS the measurement.
	 *
	 * THE THREE SIZES SHARE ONE LIST and keep their own call counts. A list each would lose the
	 * order between them - the panel draws in bold and the rows in whichever size is set, so
	 * "the panel is above the rows" would stop being askable - while one list for all three
	 * cannot say WHICH font drew a row, which is the only way to test the size setting at all.
	 * So: the list answers what was drawn, the counter answers by whom.
	 */
	static final class RecordingFont extends PixFont {

		static final int ADVANCE = 4;

		final List<Drawn> rows;

		/** Draws made through THIS size since the last frame. */
		int calls;

		RecordingFont(List<Drawn> shared) {
			this.rows = shared;
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
			this.rows.add(new Drawn(x, y, colour, text));
			this.calls++;
		}

		public void drawString(int x, int colour, int y, String text) {
			this.rows.add(new Drawn(x, y, colour, text));
			this.calls++;
		}
	}
}
