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

# THE WORKING TREE IS NEVER WRITTEN. Mutations go into a throwaway copy of the repository, so
# the tree stays clean and committable for the whole run and a kill at the worst moment leaves a
# broken file in a temp directory nobody builds from. It is a snapshot too: an edit to the tree
# mid-run cannot reach the run. mutate_guard.workspace has the reasoning, and its check() is the
# standalone pass that every pattern still matches its source exactly once.
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from mutate_guard import workspace  # noqa: E402  (after the sys.path line, necessarily)

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
BUILTIN = os.path.join(ROOT, 'src/main/java/jagex2/client/plugin/builtin')
BOOSTS = os.path.join(BUILTIN, 'BoostsPlugin.java')
SKILLS = os.path.join(BUILTIN, 'SkillsPlugin.java')
FILTER = os.path.join(ROOT, 'src/main/java/jagex2/client/plugin/builtin/SkillFilter.java')
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
     'lines.add(new Line(text, delta > 0 ? boosted : drained));',
     'lines.add(new Line(text, boosted));'),
    (BOOSTS, 'the cache\'s unused skill slots offered as skills',
     '''			if (!this.isRealSkill(skill)
					|| !SkillFilter.allows(this.ctx.getSkillName(skill), this.skills)) {
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
    # ---- TRANCHE FOUR: the six new Boosts settings, the expiry notice and the two skills it may
    # never name, and the skill filter both this plugin and the Idle notifier ask.
    (FILTER, '''an empty filter excluding every skill, so an unset box empties the panel''',
     '''		if (!isFiltering(terms)) {
			return true;
		}
''',
     ''''''),
    (FILTER, '''a list of nothing but commas treated as a real filter''',
     '''		for (int i = 0; i < trimmed.length(); i++) {
			if (trimmed.charAt(i) != ',' && trimmed.charAt(i) != ' ') {
				return true;
			}
		}
		return false;''',
     '''		return trimmed.length() > 0;'''),
    (FILTER, '''matching by substring, so "tack" matches Attack and one letter matches half the list''',
     '''if (term.length() > 0 && lower.startsWith(term.toLowerCase())) {''',
     '''if (term.length() > 0 && lower.indexOf(term.toLowerCase()) >= 0) {'''),
    (FILTER, '''matching made exact, so "wood" no longer finds Woodcutting''',
     '''if (term.length() > 0 && lower.startsWith(term.toLowerCase())) {''',
     '''if (term.length() > 0 && lower.equals(term.toLowerCase())) {'''),
    (FILTER, '''matching made case-sensitive, so a typed lower-case term finds nothing''',
     '''		String lower = skillName.toLowerCase();''',
     '''		String lower = skillName;'''),
    (FILTER, '''terms not trimmed, so a space after a comma breaks one''',
     '''			String term = (comma < 0 ? trimmed.substring(from)
				: trimmed.substring(from, comma)).trim();''',
     '''			String term = comma < 0 ? trimmed.substring(from)
				: trimmed.substring(from, comma);'''),
    (FILTER, '''an empty term matching everything, so a trailing comma opens the filter up''',
     '''if (term.length() > 0 && lower.startsWith(term.toLowerCase())) {''',
     '''if (lower.startsWith(term.toLowerCase())) {'''),
    (FILTER, '''a missing skill name passing every filter''',
     '''		if (skillName == null || skillName.length() == 0) {
			return false;
		}
''',
     ''''''),
    (BOOSTS, '''the boost colour left hardcoded, so the swatch does nothing''',
     '''		int boosted = PluginConfig.parseColour(this.boostedColour);''',
     '''		int boosted = 0x44DD44;'''),
    (BOOSTS, '''the drain colour left hardcoded''',
     '''		int drained = PluginConfig.parseColour(this.drainedColour);''',
     '''		int drained = 0xFF4444;'''),
    (BOOSTS, '''the filter not applied, so the skills box does nothing''',
     '''			if (!this.isRealSkill(skill)
					|| !SkillFilter.allows(this.ctx.getSkillName(skill), this.skills)) {''',
     '''			if (!this.isRealSkill(skill)) {'''),
    (BOOSTS, '''the heading drawn whether the player asked for it or not''',
     '''		if (this.showTitle) {
			g.textFlat(''',
     '''		if (true) {
			g.textFlat('''),
    (BOOSTS, '''the heading never drawn, so the panel does not say what it is''',
     '''		if (this.showTitle) {
			g.textFlat(''',
     '''		if (false) {
			g.textFlat('''),
    (BOOSTS, '''the box not shrunk when the heading goes, leaving an empty row''',
     '''		int rows = lines.size() + (this.showTitle ? 1 : 0);''',
     '''		int rows = lines.size() + 1;'''),
    (BOOSTS, '''the size ignored, so the drop-down does nothing''',
     '''		g.setFont(OverlayGraphics.fontFor(this.font));''',
     '''		g.setFont(OverlayGraphics.FONT_SMALL);'''),
    (BOOSTS, '''the notice given whether the player asked for it or not''',
     '''		if (!this.notifyExpired || !this.ctx.isLoggedIn()) {''',
     '''		if (!this.ctx.isLoggedIn()) {'''),
    (BOOSTS, '''the notice never given, so the switch does nothing''',
     '''		if (!this.notifyExpired || !this.ctx.isLoggedIn()) {''',
     '''		if (true) {'''),
    (BOOSTS, '''HITPOINTS AND PRAYER NAMED BY THE NOTICE, which is a vital reported out loud''',
     '''			if (!this.isRealSkill(skill) || isVital(name)
					|| !SkillFilter.allows(name, this.skills)) {''',
     '''			if (!this.isRealSkill(skill)
					|| !SkillFilter.allows(name, this.skills)) {'''),
    (BOOSTS, '''the vital list emptied, so hitpoints and prayer are notified like any stat''',
     '''return lower.equals("hitpoints") || lower.equals("prayer");''',
     '''return false;'''),
    (BOOSTS, '''only hitpoints excluded, leaving prayer notified''',
     '''return lower.equals("hitpoints") || lower.equals("prayer");''',
     '''return lower.equals("hitpoints");'''),
    (BOOSTS, '''the vital check made case-sensitive, so a differently-cased cache slips through''',
     '''		String lower = skillName.toLowerCase();
		return lower.equals("hitpoints")''',
     '''		String lower = skillName;
		return lower.equals("hitpoints")'''),
    (BOOSTS, '''a drain notified as a boost wearing off, which is a stat coming back''',
     '''			if (this.ctx.getSkillLevel(skill) > this.ctx.getBaseLevel(skill)) {''',
     '''			if (this.ctx.getSkillLevel(skill) != this.ctx.getBaseLevel(skill)) {'''),
    (BOOSTS, '''the first tick announcing every boost already running''',
     '''			} else if (this.scanned && this.wasBoosted.indexOf(key) >= 0) {''',
     '''			} else if (this.wasBoosted.indexOf(key) >= 0) {'''),
    (BOOSTS, '''nothing ever counting as a first tick, so no expiry is ever reported''',
     '''		this.scanned = true;''',
     ''''''),
    (BOOSTS, '''what was boosted not remembered, so an expiry is announced every tick''',
     '''		this.wasBoosted = now.toString();''',
     ''''''),

]


def main():
    only = sys.argv[1] if len(sys.argv) > 1 else None
    orig = {}
    for path in (BOOSTS, SKILLS, FILTER):
        with open(path, encoding='utf-8', newline='') as f:
            orig[path] = f.read()
    # Written into a copy, never into the working tree - see mutate_guard.workspace.
    _work, inside = workspace('skilltest')
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
            with open(inside(path), 'w', encoding='utf-8', newline='') as f:
                f.write(orig[path].replace(find, repl))
            r = subprocess.run([sys.executable, inside(RUNNER)], capture_output=True, text=True,
                               timeout=600)
        except subprocess.TimeoutExpired:
            print('  %-5s %-74s %s' % ('HUNG', why, 'the suite never finished - not a catch'))
            loose += 1
            continue
        finally:
            with open(inside(path), 'w', encoding='utf-8', newline='') as f:
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
