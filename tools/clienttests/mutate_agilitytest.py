#!/usr/bin/env python3
"""Mutation test for tools/clienttests/run_agilitytest.py.

The Agility plugin and the scene read it is built on: the obstacle table, which of the three
kinds a mark is drawn for, the level gate, and PluginContext.findLocs itself.

THE TABLE IS MOSTLY NOT MUTATED. It is generated from DeathPlateau-Content, so changing one of
its rows here tests nothing about the client - it tests that the test agrees with the row it was
handed. What IS mutated is everything the table is USED by, plus the two exclusions and the
shape rules, which are judgements rather than transcriptions.

Each entry breaks one thing and reports WHICH check caught it. The anchor must be UNIQUE in its
file - a mutation that lands in a comment is a green tick that means nothing.

    python3 tools/clienttests/mutate_npctest.py [filter]
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
PLUGIN = os.path.join(ROOT, 'src/main/java/jagex2/client/plugin/builtin/AgilityPlugin.java')
TABLE = os.path.join(ROOT, 'src/main/java/jagex2/client/plugin/builtin/AgilityObstacles.java')
CONTEXT = os.path.join(ROOT, 'src/main/java/jagex2/client/plugin/PluginContext.java')
LOC = os.path.join(ROOT, 'src/main/java/jagex2/client/plugin/SceneLoc.java')
RUNNER = os.path.join(HERE, 'run_agilitytest.py')

MUTS = [
    # ---- FINDING THEM. The scan is the whole feature: miss a layer and most obstacles vanish.
    (CONTEXT, 'only the scenery layer scanned, so gates and strange floors are missed',
     '''				this.keepLoc(found, wanted, level, x, z,
					this.client.scene.method300(level, x, z), SceneLoc.LAYER_WALL);''',
     ''),
    (CONTEXT, 'the ground decoration layer not scanned',
     '''				this.keepLoc(found, wanted, level, x, z,
					this.client.scene.method303(level, x, z), SceneLoc.LAYER_GROUND_DECOR);''',
     ''),
    (CONTEXT, 'the id read from the wrong end of the bitfield',
     '\t\tint id = packed >> 14 & 0x7FFF;',
     '\t\tint id = packed & 0x7FFF;'),
    (CONTEXT, 'every loc kept, not just the ones asked for',
     '\t\tif (java.util.Arrays.binarySearch(wanted, id) < 0) {',
     '\t\tif (false) {'),
    (CONTEXT, 'nothing kept, so the scan always comes back empty',
     '\t\tif (java.util.Arrays.binarySearch(wanted, id) < 0) {',
     '\t\tif (true) {'),
    (CONTEXT, 'an empty square counted as a loc, which is id 0 everywhere',
     '\t\tif (packed == 0) {\n\t\t\treturn;\n\t\t}',
     ''),
    (CONTEXT, 'the ids searched unsorted, so binarySearch answers at random',
     '\t\tjava.util.Arrays.sort(wanted);',
     ''),
    (CONTEXT, "the caller's own array sorted under it rather than a copy",
     '''		int[] wanted = new int[ids.length];
		System.arraycopy(ids, 0, wanted, 0, ids.length);''',
     '\t\tint[] wanted = ids;'),
    (CONTEXT, 'a null or empty id list walked anyway',
     '\t\tif (ids == null || ids.length == 0 || this.client.scene == null) {',
     '\t\tif (false) {'),
    (CONTEXT, 'the size left unrotated, so a turned obstacle marks the wrong tiles',
     '\t\tboolean turned = (rotation & 0x1) == 1;',
     '\t\tboolean turned = false;'),
    (CONTEXT, 'width and length swapped, which is the same bug the other way round',
     '\t\tint width = turned ? type.field1629 : type.field1655;',
     '\t\tint width = turned ? type.field1655 : type.field1629;'),
    (CONTEXT, 'the rotation read from the wrong bits',
     '\t\tint rotation = info < 0 ? 0 : info >> 6 & 0x3;',
     '\t\tint rotation = info < 0 ? 0 : info & 0x3;'),

    # ---- THE TABLE'S OWN JUDGEMENTS, which are not transcriptions of the content.
    (TABLE, 'the locked chest put back in, which is not an obstacle',
     '\t\tadd( 1947, "Crumbling wall", SHORTCUT,  5);',
     '\t\tadd( 1947, "Crumbling wall", SHORTCUT,  5);\n\t\tadd(  377, "Closed chest", DUNGEON,  1);'),
    (TABLE, 'an obstacle dropped, so a course has a gap in it',
     '\t\tadd( 2295, "Log balance", GNOME,  1);',
     ''),
    (TABLE, 'the ids handed out unsorted, which findLocs binary searches',
     '\t\tArrays.sort(IDS);',
     ''),
    (TABLE, 'the table handed out rather than a copy of its ids',
     '''		int[] copy = new int[IDS.length];
		System.arraycopy(IDS, 0, copy, 0, IDS.length);
		return copy;''',
     '\t\treturn IDS;'),
    (TABLE, 'byId answering with the first row whatever was asked for',
     '\t\t\tif (ALL.get(i).id == id) {',
     '\t\t\tif (true) {'),
    (TABLE, 'a shortcut counted as a course',
     '\t\treturn course >= GNOME && course <= ARDOUGNE;',
     '\t\treturn course != DUNGEON;'),
    (TABLE, 'the rooftop courses left out of isCourse, which is where they were missed before',
     '\t\treturn course >= GNOME && course <= ARDOUGNE;',
     '\t\treturn course <= WILDERNESS;'),

    # ---- WHICH OBSTACLES ARE MARKED.
    (PLUGIN, 'course obstacles marked whether the player asked or not',
     '\t\t\treturn this.dungeon;',
     '\t\t\treturn true;'),
    (PLUGIN, 'shortcuts marked whether the player asked or not',
     '\t\t\treturn this.shortcuts;',
     '\t\t\treturn true;'),
    (PLUGIN, 'the courses switch ignored',
     '\t\treturn this.courses;',
     '\t\treturn true;'),
    (PLUGIN, 'nothing marked at all, so every switch does nothing',
     '\t\treturn this.courses;',
     '\t\treturn false;'),
    (PLUGIN, 'an id with no obstacle behind it marked anyway',
     '\t\t\tif (obstacle == null || !this.wanted(obstacle)) {',
     '\t\t\tif (false) {'),

    # ---- THE LEVEL GATE.
    (PLUGIN, 'the level gate inverted, so what you can use is what reads as locked',
     '\t\t\tboolean locked = level < obstacle.level;',
     '\t\t\tboolean locked = level > obstacle.level;'),
    (PLUGIN, 'exactly on the level read as too low, which is off by one',
     '\t\t\tboolean locked = level < obstacle.level;',
     '\t\t\tboolean locked = level <= obstacle.level;'),
    (PLUGIN, 'nothing ever locked, so the colour and the switch are decoration',
     '\t\t\tboolean locked = level < obstacle.level;',
     '\t\t\tboolean locked = false;'),
    (PLUGIN, 'locked obstacles hidden whether the player asked or not',
     '\t\t\tif (locked && this.hideLocked) {',
     '\t\t\tif (locked) {'),
    (PLUGIN, 'the hide switch doing nothing',
     '\t\t\tif (locked && this.hideLocked) {',
     '\t\t\tif (false) {'),
    (PLUGIN, 'the locked colour not winning, so you cannot tell what you can use',
     '''		if (locked) {
			return this.lockedColour;
		}''',
     ''),
    (PLUGIN, 'a shortcut drawn in the course colour',
     '''		return obstacle.course == AgilityObstacles.SHORTCUT
			? this.shortcutColour : this.courseColour;''',
     '\t\treturn this.courseColour;'),
    (PLUGIN, 'the agility level read as another skill, so every gate is by the wrong number',
     '\t\t\t\tif ("agility".equalsIgnoreCase(this.ctx.getSkillName(skill))) {',
     '\t\t\t\tif (skill == 0) {'),
    (PLUGIN, 'a client with no Agility skill locking everything instead of nothing',
     '\t\t\treturn Integer.MAX_VALUE;',
     '\t\t\treturn 0;'),

    # ---- THE LABEL.
    (PLUGIN, 'the label always drawn, whatever the drop-down says',
     '''		if (LABEL_NONE.equals(this.label)) {
			return "";
		}''',
     ''),
    (PLUGIN, 'the level always on the label, including on a course that asks for none',
     '\t\tif (LABEL_NAME.equals(this.label) || obstacle.level <= 1) {',
     '\t\tif (LABEL_NAME.equals(this.label)) {'),
    (PLUGIN, 'the level never on the label, so the drop-down has two of the same choice',
     '\t\tif (LABEL_NAME.equals(this.label) || obstacle.level <= 1) {',
     '\t\tif (true) {'),
    (PLUGIN, 'an empty label still drawn, which is a shadow on an empty string',
     '\t\t\tif (text.length() > 0) {',
     '\t\t\tif (true) {'),

    # ---- THE MARK ITSELF.
    (PLUGIN, 'only the origin tile marked, so a three-tile obstacle is marked on one',
     '\t\tfor (int dx = 0; dx < loc.width; dx++) {',
     '\t\tfor (int dx = 0; dx < 1; dx++) {'),
    (PLUGIN, 'the length ignored, which is the same bug on the other axis',
     '\t\t\tfor (int dz = 0; dz < loc.length; dz++) {',
     '\t\t\tfor (int dz = 0; dz < 1; dz++) {'),
    (PLUGIN, 'the fill drawn whether the player asked for it or not',
     '\t\tint alpha = this.fill ? fillFor(this.fillOpacity) : 0;',
     '\t\tint alpha = fillFor(this.fillOpacity);'),
    (PLUGIN, 'the outline never drawn, leaving a fill with no edge',
     '\t\t\t\tTileIndicatorsPlugin.outlineTile(this.ctx, g, tx, tz, colour, width);',
     ''),
    (PLUGIN, 'the border width ignored, so the drop-down does nothing',
     '\t\tint width = borderFor(this.borderWidth);',
     '\t\tint width = DEFAULT_BORDER;'),
    (PLUGIN, 'a border of nothing left at nothing, so the mark is invisible',
     '''		if (width < MIN_BORDER) {
			return MIN_BORDER;
		}''',
     ''),
    (PLUGIN, 'a border with no ceiling, so a wide one is a block of colour',
     '\t\treturn width > MAX_BORDER ? MAX_BORDER : width;',
     '\t\treturn width;'),
    (PLUGIN, 'a negative fill passed through to fillAlpha',
     '''		if (alpha < 0) {
			return 0;
		}''',
     ''),
    (PLUGIN, 'a fill brighter than opaque',
     '\t\treturn alpha > MAX_FILL ? MAX_FILL : alpha;',
     '\t\treturn alpha;'),

    # ---- THE SCAN'S OWN TIMING.
    (PLUGIN, 'the scan never run, so nothing is ever marked',
     '\t\tthis.found = this.ctx.findLocs(AgilityObstacles.ids());',
     ''),
    (PLUGIN, 'the scan kept across a logout, so a course is drawn over the login screen',
     '''			this.found = null;
			return;''',
     '\t\t\treturn;'),
    (PLUGIN, 'drawn while logged out',
     '\t\tif (locs == null || !this.ctx.isLoggedIn()) {',
     '\t\tif (locs == null) {'),
]


def main():
    only = sys.argv[1] if len(sys.argv) > 1 else None
    orig = {}
    # EVERY FILE ANY MUTATION TARGETS. A target missing from this tuple is not a skipped
    # mutation, it is a KeyError that kills the run partway through.
    for path in (PLUGIN, TABLE, CONTEXT, LOC):
        with open(path, encoding='utf-8', newline='') as f:
            orig[path] = f.read()
    # Written into a copy, never into the working tree - see mutate_guard.workspace.
    _work, inside = workspace('agilitytest')
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
            r = subprocess.run([sys.executable, inside(RUNNER)], capture_output=True, text=True)
        finally:
            with open(inside(path), 'w', encoding='utf-8', newline='') as f:
                f.write(orig[path])
        fired = [l.strip()[5:].strip() for l in r.stdout.split('\n') if l.startswith('FAIL')]
        if r.returncode == 0:
            print('  %-5s %-84s %s' % ('GREEN', why, 'NOT CAUGHT'))
            bad += 1
        elif fired:
            print('  %-5s %-84s %s' % ('red', why, 'caught by: ' + fired[0][:48]))
        else:
            print('  %-5s %-84s %s' % ('red', why,
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
