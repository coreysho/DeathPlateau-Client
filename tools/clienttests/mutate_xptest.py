#!/usr/bin/env python3
"""Mutation test for tools/clienttests/run_xptest.py.

Each entry breaks one thing the XP drops plugin is supposed to do and reports WHICH check caught
it. The anchor must be UNIQUE in its file - a mutation that lands in a comment is a green tick
that means nothing.

    python3 tools/clienttests/mutate_xptest.py [filter]
"""
import os
import subprocess
import sys

# THE SOURCE GOES BACK EVEN IF THIS PROCESS IS KILLED. The `finally` below covers a run that
# fails or times out; a SIGTERM skips it entirely, and a kill once left a mutation sitting in the
# tree where the next commit would have shipped it. mutate_guard also has the standalone check
# that every pattern still matches its source exactly once.
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from mutate_guard import guard  # noqa: E402  (after the sys.path line, necessarily)

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
PLUGIN = os.path.join(ROOT, 'src/main/java/jagex2/client/plugin/builtin/XpDropsPlugin.java')
BOOSTS = os.path.join(ROOT, 'src/main/java/jagex2/client/plugin/builtin/BoostsPlugin.java')
RUNNER = os.path.join(HERE, 'run_xptest.py')

MUTS = [
    # ---- THE CLAMPS. The failure mode of an unclamped setting is the feature disappearing
    # rather than an error, which is why each bound is its own mutation.
    (PLUGIN, 'the fade floor removed, so a 0 typed into the box hides every drop',
     '''		if (ms < MIN_FADE_MS) {
			return MIN_FADE_MS;
		}
''',
     ''),
    (PLUGIN, 'the fade ceiling removed, so a long one is a column that never clears',
     '\t\treturn ms > MAX_FADE_MS ? MAX_FADE_MS : ms;',
     '\t\treturn ms;'),
    # "< MIN" to "<= MIN" is an EQUIVALENT MUTANT, not an untested behaviour: at ms == MIN both
    # answer MIN, so no check could ever tell them apart. It was dropped rather than chased. What
    # is really untestable without these two is the bounds' VALUES - fadeFor(MIN) == MIN moves
    # both of its own sides - so the constants are mutated instead.
    (PLUGIN, 'the shortest fade allowed quietly tripled, so a short one is not honoured',
     '\tstatic final int MIN_FADE_MS = 100;',
     '\tstatic final int MIN_FADE_MS = 300;'),
    (PLUGIN, 'the longest fade allowed cut to under a second',
     '\tstatic final int MAX_FADE_MS = 60_000;',
     '\tstatic final int MAX_FADE_MS = 600;'),
    (PLUGIN, 'the row bounds moved, so eight rows is no longer a thing a player can have',
     '\tstatic final int MAX_VISIBLE = 32;',
     '\tstatic final int MAX_VISIBLE = 4;'),
    (PLUGIN, 'the speed bounds moved, so 100% is no longer reachable',
     '\tstatic final int MAX_SPEED = 100;',
     '\tstatic final int MAX_SPEED = 50;'),
    (PLUGIN, 'the row floor removed, so zero rows turns the feature off by typo',
     '''		if (rows < MIN_VISIBLE) {
			return MIN_VISIBLE;
		}
''',
     ''),
    (PLUGIN, 'the row ceiling removed, so a thousand rows is honoured',
     '\t\treturn rows > MAX_VISIBLE ? MAX_VISIBLE : rows;',
     '\t\treturn rows;'),
    (PLUGIN, 'the easing floor removed, so 0% is a row that never arrives',
     'int clamped = percent < MIN_SPEED ? MIN_SPEED : (percent > MAX_SPEED ? MAX_SPEED : percent);',
     'int clamped = percent;'),
    (PLUGIN, 'the easing scaled wrong, so every speed setting is ten times what it says',
     '\t\treturn clamped / 100f;',
     '\t\treturn clamped / 10f;'),

    # ---- THE CAP, which now applies while rows are up. A player lowering it and seeing nothing
    # change reads that as broken.
    (PLUGIN, 'the cap only applied on the next gain, so lowering it does nothing until one',
     '''		// Lowering the cap while rows are up takes effect now rather than on the next gain.
		while (this.drops.size() > visibleFor(this.maxVisible)) {
			this.drops.remove(this.drops.size() - 1);
		}
''',
     ''),
    (PLUGIN, 'the cap dropping the newest row rather than the oldest',
     '''		while (this.drops.size() > visibleFor(this.maxVisible)) {
			this.drops.remove(this.drops.size() - 1);
		}
		boolean tracker''',
     '''		while (this.drops.size() > visibleFor(this.maxVisible)) {
			this.drops.remove(0);
		}
		boolean tracker'''),
    (PLUGIN, 'the cap left at the old constant, so the setting is decoration',
     '''		while (this.drops.size() > visibleFor(this.maxVisible)) {
			this.drops.remove(this.drops.size() - 1);
		}
	}
''',
     '''		while (this.drops.size() > DEFAULT_VISIBLE) {
			this.drops.remove(this.drops.size() - 1);
		}
	}
'''),

    # ---- THE FADE, read from the setting rather than from the constant it used to be.
    (PLUGIN, 'the drop fade left at the old constant, so the setting is decoration',
     '\t\tlong fade = fadeFor(this.fadeMs);',
     '\t\tlong fade = DEFAULT_FADE_MS;'),
    (PLUGIN, 'the panel fade left at the old constant',
     '\t\tthis.trackerUntil = now + (long) fadeFor(this.trackerFadeMs);',
     '\t\tthis.trackerUntil = now + DEFAULT_TRACKER_FADE_MS;'),
    (PLUGIN, 'the fade made exclusive, so a row lives one frame past its own deadline',
     'if (now - this.drops.get(i).created >= fade) {',
     'if (now - this.drops.get(i).created > fade + 1000L) {'),

    # ---- WHAT A ROW SAYS AND WHAT COLOUR IT IS.
    (PLUGIN, 'the drop colour left hardcoded, so the swatch does nothing',
     '\t\tint colour = PluginConfig.parseColour(this.dropColour);',
     '\t\tint colour = 0xFFFF00;'),
    (PLUGIN, 'the progress bar colour left hardcoded',
     '\t\tint bar = PluginConfig.parseColour(this.progressColour);',
     '\t\tint bar = 0xC8641E;'),
    (PLUGIN, 'the skill name on every row whether the player asked or not',
     '''			String text = dropLabel(drop.amount,
				BoostsPlugin.name(this.ctx.getSkillName(drop.skill)), this.showSkillName);''',
     '''			String text = dropLabel(drop.amount,
				BoostsPlugin.name(this.ctx.getSkillName(drop.skill)), true);'''),
    (PLUGIN, 'the skill name never shown, so the setting does nothing',
     'return named && skill != null && skill.length() > 0 ? text + " " + skill : text;',
     'return text;'),
    (PLUGIN, 'an empty skill name leaving a trailing space on the row',
     'return named && skill != null && skill.length() > 0 ? text + " " + skill : text;',
     'return named && skill != null ? text + " " + skill : text;'),
    (BOOSTS, 'the skill name left lower case, so a row reads "+100 attack"',
     '\t\treturn Character.toUpperCase(skill.charAt(0)) + skill.substring(1);',
     '\t\treturn skill;'),

    # ---- THE OUTLINE, which is five draws rather than one.
    (PLUGIN, 'the outline drawn whether the player asked for it or not',
     '\t\t\tif (this.textOutline) {',
     '\t\t\tif (true) {'),
    (PLUGIN, 'the outline never drawn, so the switch does nothing',
     '\t\t\tif (this.textOutline) {',
     '\t\t\tif (false) {'),

    # ---- THE FONT. A drop-down whose values no branch matches is a control that silently does
    # nothing, and the compiler cannot see it: both sides are strings.
    (PLUGIN, 'the font left at plain 12, so the size drop-down does nothing',
     '\t\tg.setFont(fontFor(this.font));',
     '\t\tg.setFont(OverlayGraphics.FONT_NORMAL);'),
    (PLUGIN, 'the bold choice falling through to the fallback',
     '''		if (FONT_BIG.equals(choice)) {
			return OverlayGraphics.FONT_BOLD;
		}
''',
     ''),
    (PLUGIN, 'the small choice falling through to the fallback',
     'return FONT_TINY.equals(choice) ? OverlayGraphics.FONT_SMALL : OverlayGraphics.FONT_NORMAL;',
     'return OverlayGraphics.FONT_NORMAL;'),
    (PLUGIN, 'an unknown font throwing instead of falling back, out of a render loop',
     '\t\tif (FONT_BIG.equals(choice)) {',
     '\t\tif (choice.equals(FONT_BIG)) {'),
    (PLUGIN, 'the font drop-down offering a value no branch matches',
     '\t\tchoices = { FONT_PLAIN, FONT_BIG, FONT_TINY })',
     '\t\tchoices = { FONT_PLAIN, "Huge", FONT_TINY })'),
    (PLUGIN, 'the direction drop-down offering a value no branch matches',
     '\t\tchoices = { DOWN, UP, STILL })',
     '\t\tchoices = { DOWN, "Sideways", STILL })'),

    # ---- WHICH WAY THE COLUMN RUNS.
    (PLUGIN, 'the direction ignored, so the drop-down does nothing',
     '\t\treturn up ? -offset : offset;',
     '\t\treturn offset;'),
    (PLUGIN, 'a row mid-ease truncated rather than rounded',
     '\t\tint offset = Math.round(displayY * rowHeight);',
     '\t\tint offset = (int) (displayY * rowHeight);'),

    # ---- GROUPING. The hard part is that it must NOT extend the row.
    (PLUGIN, 'gains grouped whether the player asked for it or not',
     '\t\tif (this.groupSameSkill && newest != null && newest.skill == event.skill) {',
     '\t\tif (newest != null && newest.skill == event.skill) {'),
    (PLUGIN, 'gains never grouped, so the switch does nothing',
     '\t\tif (this.groupSameSkill && newest != null && newest.skill == event.skill) {',
     '\t\tif (false && newest != null && newest.skill == event.skill) {'),
    (PLUGIN, 'a different skill grouped into the row above it',
     '\t\tif (this.groupSameSkill && newest != null && newest.skill == event.skill) {',
     '\t\tif (this.groupSameSkill && newest != null) {'),
    (PLUGIN, 'a grouped row replacing its total rather than adding to it',
     '\t\t\tnewest.amount += event.gained;',
     '\t\t\tnewest.amount = event.gained;'),
    (PLUGIN, 'a grouped row refreshing its own fade, so it never leaves while you train',
     '''		Drop newest = this.drops.isEmpty() ? null : this.drops.get(0);
		if (this.groupSameSkill && newest != null && newest.skill == event.skill) {
			newest.amount += event.gained;
			return;
		}''',
     '''		Drop newest = this.drops.isEmpty() ? null : this.drops.get(0);
		if (this.groupSameSkill && newest != null && newest.skill == event.skill) {
			this.drops.set(0, new Drop(newest.skill, newest.amount + event.gained, now));
			return;
		}'''),
    (PLUGIN, 'grouping reaching past the newest row to grow a settled one',
     '\t\tDrop newest = this.drops.isEmpty() ? null : this.drops.get(0);',
     '\t\tDrop newest = this.drops.isEmpty() ? null\n\t\t\t: this.drops.get(this.drops.size() - 1);'),

    # ---- THE PANEL.
    (PLUGIN, 'the panel drawn whether the player asked for it or not',
     'boolean tracker = this.showTracker && this.trackerSkill >= 0 && now < this.trackerUntil;',
     'boolean tracker = this.trackerSkill >= 0 && now < this.trackerUntil;'),
    (PLUGIN, 'the per-hour line drawn whether the player asked for it or not',
     '\t\tString rate = this.showPerHour',
     '\t\tString rate = true'),
    (PLUGIN, 'the per-hour line never drawn',
     '\t\tString rate = this.showPerHour',
     '\t\tString rate = false'),
    (PLUGIN, 'the box not grown for the per-hour line, so it draws over its own bar',
     '\t\tint boxH = rate == null ? 32 : 44;',
     '\t\tint boxH = 32;'),

    # ---- EXPERIENCE PER HOUR. A rate measured over a few milliseconds is a measurement of the
    # denominator.
    (PLUGIN, 'the first millisecond treated as a rate, giving a number in the hundreds of millions',
     '\t\tif (gained <= 0L || elapsedMs < 1000L) {',
     '\t\tif (gained <= 0L || elapsedMs <= 0L) {'),
    (PLUGIN, 'no gain dividing anyway',
     '\t\tif (gained <= 0L || elapsedMs < 1000L) {',
     '\t\tif (elapsedMs < 1000L) {'),
    (PLUGIN, 'the rate per minute rather than per hour',
     '\t\treturn gained * 3_600_000L / elapsedMs;',
     '\t\treturn gained * 60_000L / elapsedMs;'),
    (PLUGIN, 'the rate computed in int, so a long session wraps negative',
     '\t\treturn gained * 3_600_000L / elapsedMs;',
     '\t\treturn (int) gained * 3_600_000 / (int) elapsedMs;'),
    (PLUGIN, 'the session clock restarted on every gain, so the rate is always the last drop',
     '''		if (this.sessionStart == 0L) {
			this.sessionStart = now;
		}''',
     '\t\tthis.sessionStart = now;'),
    (PLUGIN, 'the session total not accumulated, so the rate is one gain an hour',
     '\t\tthis.sessionGained += event.gained;',
     '\t\tthis.sessionGained = event.gained;'),
    (PLUGIN, 'the session carried across a logout, reporting a rate over time nobody played',
     '''		this.sessionGained = 0L;
		this.sessionStart = 0L;
''',
     ''),

    # ---- THE GROUPED TOTAL.
    (PLUGIN, 'the total not grouped, so eight digits run together',
     '''		int lead = digits.length() % 3;
		if (lead == 0) {
			lead = 3;
		}
''',
     '\t\tint lead = digits.length();\n'),
    (PLUGIN, 'the lead group wrong for a length that divides by three',
     '''		if (lead == 0) {
			lead = 3;
		}
''',
     ''),
    (PLUGIN, 'the total narrowed to int, so a per-hour figure past int wraps negative',
     '\t\tString digits = Long.toString(value);',
     '\t\tString digits = Integer.toString((int) value);'),

    # ---- LEVEL UPS. The 99 guard is the whole point.
    (PLUGIN, 'a maxed skill announcing a level up on every single drop for the rest of the account',
     'return fromLevel >= 1 && fromLevel < 99 && nextLevelExperience > 0',
     'return fromLevel >= 1 && nextLevelExperience > 0'),
    (PLUGIN, 'login\'s sync of an unlevelled skill read as a level up',
     'return fromLevel >= 1 && fromLevel < 99 && nextLevelExperience > 0',
     'return fromLevel < 99 && nextLevelExperience > 0'),
    (PLUGIN, 'a level boundary landed on exactly not counting',
     '\t\t\t&& experience >= nextLevelExperience;',
     '\t\t\t&& experience > nextLevelExperience;'),
    (PLUGIN, 'a level up announced whether the player asked for it or not',
     '\t\tif (this.notifyLevelUp) {',
     '\t\tif (true) {'),
    (PLUGIN, 'level ups never announced, so the switch does nothing',
     '\t\tif (this.notifyLevelUp) {',
     '\t\tif (false) {'),
    (PLUGIN, 'the level read after the change rather than before it, so nothing ever crosses',
     '\t\t\tint was = this.ctx.getBaseLevel(event.skill);',
     '\t\t\tint was = this.ctx.getBaseLevel(event.skill) + 1;'),

    # ---- THE LOGIN GUARD, which is what stops a whole account raining down the screen.
    (PLUGIN, 'login\'s sync of every skill drawn as a screen full of drops',
     '\t\tif (event.gained <= 0) {',
     '\t\tif (event.gained < 0) {'),

    # ---- THE DECLARED LEVEL.
    (PLUGIN, 'the declared API level left behind, so an older client loads this and breaks',
     '\tapiLevel = 5\n)',
     '\tapiLevel = 0\n)'),
]


def main():
    only = sys.argv[1] if len(sys.argv) > 1 else None
    orig = {}
    # EVERY FILE ANY MUTATION TARGETS. A target missing from this tuple is not a skipped
    # mutation, it is a KeyError that kills the run partway through.
    for path in (PLUGIN, BOOSTS):
        with open(path, encoding='utf-8', newline='') as f:
            orig[path] = f.read()
    guard(orig)
    muts = [m for m in MUTS if not only or only in m[1]]
    print('running %d of %d mutations' % (len(muts), len(MUTS)))
    bad = loose = 0
    for path, why, find, repl in muts:
        n = orig[path].count(find)
        if n != 1:
            print('  SKIP (pattern %s) %s'
                  % ('not found' if n == 0 else 'matches %d times' % n, why))
            bad += 1
            continue
        try:
            with open(path, 'w', encoding='utf-8', newline='') as f:
                f.write(orig[path].replace(find, repl))
            r = subprocess.run([sys.executable, RUNNER], capture_output=True, text=True)
        finally:
            with open(path, 'w', encoding='utf-8', newline='') as f:
                f.write(orig[path])
        fired = [l.strip()[5:].strip() for l in r.stdout.split('\n') if l.startswith('FAIL')]
        if r.returncode == 0:
            print('  %-5s %-82s %s' % ('GREEN', why, 'NOT CAUGHT'))
            bad += 1
        elif fired:
            print('  %-5s %-82s %s' % ('red', why, 'caught by: ' + fired[0][:50]))
        else:
            print('  %-5s %-82s %s' % ('red', why,
                  'caught, but by a non-zero exit with no check named - a crash is not a catch'))
            loose += 1
    print()
    if bad:
        print('%d MUTATIONS SURVIVED OR SKIPPED' % bad)
    elif loose:
        print('every mutation was caught, but %d only by a crash' % loose)
    else:
        print('every mutation was caught, each by a named check')
    return 1 if bad or loose else 0


if __name__ == '__main__':
    sys.exit(main())
