#!/usr/bin/env python3
"""Mutation test for tools/clienttests/run_skilltest.py.

Breaks Boosts and Skills one plausible way at a time and checks run_skilltest
notices - and notices by NAMING a check, not by crashing. A test that only goes red because
something threw is not measuring the thing it claims to.

Both plugins are almost entirely arithmetic, which is the kind of code that is wrong
quietly: a combat level one too low, an experience curve that diverges at level 73, a panel that
lists a skill the game does not have. Every mutation below leaves the client compiling and the plugin running.

    python3 tools/clienttests/mutate_skilltest.py [filter]
"""
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
BUILTIN = os.path.join(ROOT, 'src/main/java/jagex2/client/plugin/builtin')
BOOSTS = os.path.join(BUILTIN, 'BoostsPlugin.java')
SKILLS = os.path.join(BUILTIN, 'SkillsPlugin.java')
RUNNER = os.path.join(HERE, 'run_skilltest.py')

MUTS = [
    # --- Boosts -----------------------------------------------------------------------------
    (BOOSTS, 'the panel shown whether or not anything is boosted',
     '''		if (lines.isEmpty() && this.always) {
			lines.add(new Line("-", HEADING));
		}''',
     '''		if (lines.isEmpty()) {
			lines.add(new Line("-", HEADING));
		}'''),
    (BOOSTS, 'every skill listed, not just the ones that moved',
     '''			if (now == base) {
				continue;
			}
''', ''),
    (BOOSTS, 'a drain drawn in the boost colour, so the two read alike',
     'lines.add(new Line(text, delta > 0 ? BOOSTED : DRAINED));',
     'lines.add(new Line(text, BOOSTED));'),
    (BOOSTS, 'the cache\'s unused skill slots offered as skills',
     '''			if (!this.isRealSkill(skill)) {
				continue;
			}
''', ''),
    # The login check. The skill arrays keep their last values after a logout, so a plugin that
    # does not ask draws the panel it had over the login screen.
    (BOOSTS, 'the panel drawn while logged out, over the login screen',
     '''		if (!this.ctx.isLoggedIn()) {
			return;
		}
		List<Line> lines = this.lines();''',
     '		List<Line> lines = this.lines();'),
    (BOOSTS, 'the current and base levels the wrong way round',
     'int delta = now - base;',
     'int delta = base - now;'),

    # --- Skills: the curve ------------------------------------------------------------------
    # The formula itself. Each of these is a transcription slip that looks right.
    (SKILLS, 'the experience formula quartered per level instead of at the end',
     '''			points += (long) Math.floor(i + 300.0 * Math.pow(2.0, i / 7.0));
		}
		long experience = points / 4;''',
     '''			points += (long) Math.floor(i + 300.0 * Math.pow(2.0, i / 7.0)) / 4;
		}
		long experience = points;'''),
    (SKILLS, 'the formula\'s 300 written as 30',
     'points += (long) Math.floor(i + 300.0 * Math.pow(2.0, i / 7.0));',
     'points += (long) Math.floor(i + 30.0 * Math.pow(2.0, i / 7.0));'),
    (SKILLS, 'the formula\'s divisor of 7 written as 8',
     'points += (long) Math.floor(i + 300.0 * Math.pow(2.0, i / 7.0));',
     'points += (long) Math.floor(i + 300.0 * Math.pow(2.0, i / 8.0));'),
    (SKILLS, 'the sum run to the level rather than below it, so every level is one out',
     'for (int i = 1; i < level; i++) {',
     'for (int i = 1; i <= level; i++) {'),
    # The boundary. Getting it wrong either reimplements levels the client already knows, or
    # trusts the client past where it clamps - and the second makes every level read as 100.
    (SKILLS, 'the client trusted past where its table clamps',
     'if (level <= LAST_TABLED_LEVEL) {',
     'if (true) {'),
    (SKILLS, 'true levels capped at 99, so the whole feature does nothing',
     '''		if (level < MAX_REAL_LEVEL) {
			return level;
		}''',
     '''		if (level <= MAX_REAL_LEVEL) {
			return level;
		}'''),
    # The search used to be a while that climbed until it stopped, and deleting its bound made it
    # a loop with no end - which hung this runner rather than failing it. It is a bounded for now,
    # so the mutation worth making is the bound being one out, not the bound being gone.
    (SKILLS, 'the true-level search stopping one level short',
     'for (int next = MAX_REAL_LEVEL + 1; next <= MAX_VIRTUAL_LEVEL; next++) {',
     'for (int next = MAX_REAL_LEVEL + 1; next < MAX_VIRTUAL_LEVEL; next++) {'),

    # --- Skills: the combat level -----------------------------------------------------------
    # The bug this formula actually had: floored in pieces instead of once at the end, which is
    # one or two low everywhere and reads as plausible.
    (SKILLS, 'the combat level floored in pieces instead of once at the end',
     '''		int base = defensives * 250;
		int melee = (this.ctx.getBaseLevel(ATTACK) + this.ctx.getBaseLevel(STRENGTH)) * 325;
		int ranged = this.ctx.getBaseLevel(RANGED) * 3 / 2 * 325;
		int magic = this.ctx.getBaseLevel(MAGIC) * 3 / 2 * 325;''',
     '''		int base = defensives * 250 / 1000 * 1000;
		int melee = (this.ctx.getBaseLevel(ATTACK) + this.ctx.getBaseLevel(STRENGTH)) * 325
			/ 1000 * 1000;
		int ranged = this.ctx.getBaseLevel(RANGED) * 3 / 2 * 325 / 1000 * 1000;
		int magic = this.ctx.getBaseLevel(MAGIC) * 3 / 2 * 325 / 1000 * 1000;'''),
    (SKILLS, 'prayer counted in full rather than halved',
     '+ this.ctx.getBaseLevel(PRAYER) / 2;',
     '+ this.ctx.getBaseLevel(PRAYER);'),
    (SKILLS, 'ranged counted straight, without its 3/2',
     'int ranged = this.ctx.getBaseLevel(RANGED) * 3 / 2 * 325;',
     'int ranged = this.ctx.getBaseLevel(RANGED) * 325;'),
    (SKILLS, 'the offences summed instead of the best one taken',
     '''		int best = melee > ranged ? melee : ranged;
		if (magic > best) {
			best = magic;
		}''',
     '		int best = melee + ranged + magic;'),
    (SKILLS, 'the defensives weighted a quarter too heavily',
     'int base = defensives * 250;',
     'int base = defensives * 325;'),

    # --- Skills: the page -------------------------------------------------------------------
    (SKILLS, 'the page built fresh per question, which is the cache doing nothing',
     '''		if (this.cached != null) {
			return this.cached;
		}
''', ''),
    (SKILLS, 'the cache never cleared, so the page is frozen at the first read',
     '''	public void onGameTick(GameTick event) {
		this.cached = null;
	}''',
     '''	public void onGameTick(GameTick event) {
	}'''),
    (SKILLS, 'maxed skills kept on the page when they were asked to be hidden',
     '''			if (this.hideMaxed && level >= MAX_REAL_LEVEL) {
				continue;
			}
''', ''),
    (SKILLS, 'the combat level row dropped, so the page starts at a skill',
     '		rows.add(new Skill("Combat level", combat, combat, 0, 0, -1));',
     ''),
    (SKILLS, 'the placeholder slots listed as skills',
     '''			if (name.length() == 0 || name.charAt(0) == UNUSED_MARKER) {
				continue;
			}
''', ''),
    (SKILLS, 'the progress bar reported as a fraction rather than a percentage',
     'return through >= end - start ? 100 : through * 100 / (end - start);',
     'return through >= end - start ? 100 : through / (end - start);'),
    (SKILLS, 'the thousands separator dropped',
     '''		StringBuilder out = new StringBuilder();
		int lead = digits.length() % 3;''',
     '''		if (true) {
			return digits;
		}
		StringBuilder out = new StringBuilder();
		int lead = digits.length() % 3;'''),
]


def main():
    only = sys.argv[1] if len(sys.argv) > 1 else None
    orig = {}
    for path in (BOOSTS, SKILLS):
        with open(path, encoding='utf-8', newline='') as f:
            orig[path] = f.read()
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
            r = subprocess.run([sys.executable, RUNNER], capture_output=True, text=True,
                               timeout=600)
        except subprocess.TimeoutExpired:
            print('  %-5s %-74s %s' % ('HUNG', why, 'the suite never finished - not a catch'))
            loose += 1
            continue
        finally:
            with open(path, 'w', encoding='utf-8', newline='') as f:
                f.write(orig[path])
        fired = [l.strip()[5:].strip() for l in r.stdout.split('\n') if l.startswith('FAIL')]
        if r.returncode == 0:
            print('  %-5s %-74s %s' % ('GREEN', why, 'NOT CAUGHT'))
            bad += 1
        elif fired:
            print('  %-5s %-74s %s' % ('red', why, 'caught by: ' + fired[0][:46]))
        else:
            print('  %-5s %-74s %s' % ('red', why,
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
