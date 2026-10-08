#!/usr/bin/env python3
"""Mutation test for tools/clienttests/run_npctest.py.

The client-driven half of Npc indicators: the marks, the menu colour, the Tag row and the
appearance notice. The pure rules are mutated by mutate_actortest.py, which is where they are
tested.

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
PLUGIN = os.path.join(ROOT, 'src/main/java/jagex2/client/plugin/builtin/NpcIndicatorsPlugin.java')
TILES = os.path.join(ROOT, 'src/main/java/jagex2/client/plugin/builtin/TileIndicatorsPlugin.java')
RUNNER = os.path.join(HERE, 'run_npctest.py')

MUTS = [
    # ---- THE MARKS, and each switch actually reaching them.
    (PLUGIN, 'the tile outlined whether the player asked for it or not',
     '\t\t\tif (this.tile) {',
     '\t\t\tif (true) {'),
    (PLUGIN, 'the tile never outlined, so the switch does nothing',
     '\t\t\tif (this.tile) {',
     '\t\t\tif (false) {'),
    (PLUGIN, 'the tag drawn whether the player asked for it or not',
     '\t\t\tif (this.tag) {',
     '\t\t\tif (true) {'),
    (PLUGIN, 'the tag never drawn',
     '\t\t\tif (this.tag) {',
     '\t\t\tif (false) {'),
    (PLUGIN, 'the early return gone, so an unmarked scene is still walked and drawn',
     'if (!this.ctx.isLoggedIn() || (!this.tile && !this.tag)) {',
     'if (!this.ctx.isLoggedIn()) {'),
    (PLUGIN, 'the combat level on every tag whether asked for or not',
     '\t\tString text = label(npc, this.level);',
     '\t\tString text = label(npc, true);'),
    (PLUGIN, 'the colour left hardcoded, so the swatch does nothing',
     '\t\tint fallback = PluginConfig.parseColour(this.colour);\n\t\tint cap',
     '\t\tint fallback = 0x00FF00;\n\t\tint cap'),
    (PLUGIN, "a term's own colour ignored on the marks, so the whole feature is decoration",
     '\t\t\tint colour = termColour(npc.name, this.names, fallback);',
     '\t\t\tint colour = fallback;'),
    (PLUGIN, 'the text outline drawn whether the player asked for it or not',
     '\t\tif (this.textOutline) {',
     '\t\tif (true) {'),
    (PLUGIN, 'the text outline never drawn',
     '\t\tif (this.textOutline) {',
     '\t\tif (false) {'),

    # ---- THE TILES A BIG NPC STANDS ON.
    (PLUGIN, 'every tile outlined whatever the style says, so the drop-down does nothing',
     '\t\t\t\tint span = everyTile ? npc.size : 1;',
     '\t\t\t\tint span = npc.size;'),
    (PLUGIN, 'only the anchor tile ever outlined, so a 3x3 boss reads as a mis-aimed marker',
     '\t\t\t\tint span = everyTile ? npc.size : 1;',
     '\t\t\t\tint span = 1;'),

    # ---- THE BORDER, drawn as rings pulled toward the tile's centre.
    (PLUGIN, 'the border width ignored, so the setting does nothing',
     '\t\tint width = borderFor(this.borderWidth);',
     '\t\tint width = 1;'),
    (TILES, 'only one ring ever drawn, so a thick border is a thin one',
     'for (int ring = 0; ring < (width < 1 ? 1 : width); ring++) {',
     'for (int ring = 0; ring < 1; ring++) {'),
    (TILES, 'a zero width drawing no ring at all',
     'for (int ring = 0; ring < (width < 1 ? 1 : width); ring++) {',
     'for (int ring = 0; ring < width; ring++) {'),
    (TILES, 'the rings all drawn on top of each other, so thickness adds nothing',
     '''				line(g, toward(xs[i], midX, ring), toward(ys[i], midY, ring),
					toward(xs[next], midX, ring), toward(ys[next], midY, ring), colour);''',
     '\t\t\t\tline(g, xs[i], ys[i], xs[next], ys[next], colour);'),
    (TILES, 'the rings pushed outward, so a thick border leaves the tile it marks',
     '\t\tint step = from < centre ? by : -by;',
     '\t\tint step = from < centre ? -by : by;'),
    (TILES, 'the inset unclamped, so on a distant tile the corners cross the centre',
     '\t\treturn (step > 0) == (moved > centre) ? centre : moved;',
     '\t\treturn moved;'),
    (TILES, 'the first ring inset as well, so a one-pixel border is drawn shrunk',
     '\t\tif (from == centre || by <= 0) {',
     '\t\tif (from == centre) {'),

    # ---- THE CAP.
    (PLUGIN, 'the cap read from the constant rather than the setting',
     '\t\tint cap = maxDrawnFor(this.maxDrawn);',
     '\t\tint cap = DEFAULT_MAX_DRAWN;'),
    (PLUGIN, 'the cap removed, so a one-letter term costs a frame',
     'for (int i = 0; i < npcs.size() && drawn < cap; i++) {',
     'for (int i = 0; i < npcs.size(); i++) {'),
    (PLUGIN, 'the cap counting every npc rather than the marked ones, so it cuts in far too early',
     '''			int colour = termColour(npc.name, this.names, fallback);
			drawn++;''',
     '\t\t\tint colour = termColour(npc.name, this.names, fallback);'),

    # ---- THE MENU COLOUR. Level 6, and only the colour.
    (PLUGIN, 'menu rows coloured whether the player asked for it or not',
     '''		if (!this.menuColour) {
			return;
		}
''',
     ''),
    (PLUGIN, 'menu rows never coloured, so the switch does nothing',
     '\t\tif (!this.menuColour) {',
     '\t\tif (true) {'),
    (PLUGIN, 'the kind tag not checked, so a term colours rows about items and scenery too',
     '''		if (at < 0 || !"yel".equals(MenuSwaps.parseKind(option, at))) {
			return "";
		}''',
     '''		if (at < 0) {
			return "";
		}'''),
    (PLUGIN, 'the wrong kind tag, so no npc row is ever found',
     '\t\tif (at < 0 || !"yel".equals(MenuSwaps.parseKind(option, at))) {',
     '\t\tif (at < 0 || !"cya".equals(MenuSwaps.parseKind(option, at))) {'),
    (PLUGIN, 'every npc row coloured rather than the marked ones',
     '\t\t\tif (name.length() == 0 || !matches(name, this.names)) {',
     '\t\t\tif (name.length() == 0) {'),
    (PLUGIN, "a term's own colour ignored in the menu, so the marks and the menu disagree",
     '\t\t\tthis.ctx.setMenuColour(i, termColour(name, this.names, fallback));',
     '\t\t\tthis.ctx.setMenuColour(i, fallback);'),
    (PLUGIN, 'the menu walk starting at Cancel',
     '\t\tfor (int i = 1; i < event.size; i++) {\n\t\t\tString name = menuNpcName',
     '\t\tfor (int i = 0; i < event.size; i++) {\n\t\t\tString name = menuNpcName'),

    # ---- THE TAG ROW.
    (PLUGIN, 'the Tag row offered over everything, not only npcs',
     '\t\t\tif (!"yel".equals(target.kind) || this.seenBefore(event, i)) {',
     '\t\t\tif (this.seenBefore(event, i)) {'),
    (PLUGIN, 'the Tag row offered over the wrong kind, so it never appears on an npc',
     '\t\t\tif (!"yel".equals(target.kind) || this.seenBefore(event, i)) {',
     '\t\t\tif (!"lre".equals(target.kind) || this.seenBefore(event, i)) {'),
    (PLUGIN, 'a row per option rather than per npc, so Attack and Examine both add one',
     '\t\t\tif (!"yel".equals(target.kind) || this.seenBefore(event, i)) {',
     '\t\t\tif (!"yel".equals(target.kind)) {'),
    (PLUGIN, 'the row always reading Tag, so there is no way to see one is already tagged',
     '\t\t\tboolean tagged = hasTerm(this.names, name);',
     '\t\t\tboolean tagged = false;'),
    (PLUGIN, 'the row label the wrong way round',
     '\t\t\tevent.addRow((tagged ? "Untag @yel@" : "Tag @yel@") + name, new Runnable() {',
     '\t\t\tevent.addRow((tagged ? "Tag @yel@" : "Untag @yel@") + name, new Runnable() {'),
    (PLUGIN, 'tagging matched by substring, so Goblin Guard reads as already tagged',
     '\t\tString after = hasTerm(before, name) ? removeTerm(before, name) : addTerm(before, name);',
     '\t\tString after = matches(name, before) ? removeTerm(before, name) : addTerm(before, name);'),
    (PLUGIN, 'the Tag row only ever adding, so nothing can be untagged from the menu',
     '\t\tString after = hasTerm(before, name) ? removeTerm(before, name) : addTerm(before, name);',
     '\t\tString after = addTerm(before, name);'),
    (PLUGIN, 'the tag never persisted, so it is gone on the next restart',
     '''		List<PluginConfig.Item> items = this.config.getItems();
		for (int i = 0; i < items.size(); i++) {
			if ("names".equals(items.get(i).key)) {
				this.config.set(items.get(i), value);
				return;
			}
		}
''',
     ''),
    (PLUGIN, 'the field not updated, so a tag does nothing until the plugin is restarted',
     '\t\tthis.names = value;',
     ''),
    (PLUGIN, 'tagging silent, which is how a player decides a menu row is broken',
     '''		this.ctx.addChatMessage(hasTerm(after, name)
			? "Now marking " + name + "."
			: "No longer marking " + name + ".");''',
     ''),

    # ---- APPEARING. A difference between two ticks, by name.
    (PLUGIN, 'the notice given whether the player asked for it or not',
     '\t\tif (!this.notifyAppears || !this.ctx.isLoggedIn()) {',
     '\t\tif (!this.ctx.isLoggedIn()) {'),
    (PLUGIN, 'the notice never given, so the switch does nothing',
     '\t\tif (!this.notifyAppears || !this.ctx.isLoggedIn()) {',
     '\t\tif (true) {'),
    (PLUGIN, 'the first scan announcing the whole crowd a player is standing in',
     '\t\t\t\tif (this.scanned && this.seen.indexOf(key) < 0) {',
     '\t\t\t\tif (this.seen.indexOf(key) < 0) {'),
    (PLUGIN, 'nothing ever counting as a first scan, so no arrival is ever reported',
     '\t\tthis.scanned = true;',
     ''),
    (PLUGIN, 'an empty scene read as no last tick, so the boss coming back goes unreported',
     '\t\t\t\tif (this.scanned && this.seen.indexOf(key) < 0) {',
     '\t\t\t\tif (this.seen.length() > 0 && this.seen.indexOf(key) < 0) {'),
    (PLUGIN, 'what was seen not remembered, so a standing npc is announced every tick',
     '\t\tthis.seen = now.toString();',
     ''),
    (PLUGIN, 'the scene remembered while the setting is off, so turning it on announces nothing new',
     '''			this.seen = "";
			this.scanned = false;
			return;''',
     '\t\t\treturn;'),
    (PLUGIN, 'an npc nobody named announced when it arrives',
     '\t\t\tif (!matches(name, this.names)) {',
     '\t\t\tif (false) {'),

    # ---- THE DECLARED LEVEL.
    (PLUGIN, 'the declared API level left behind, so an older client loads this and breaks',
     '\tapiLevel = 6\n)',
     '\tapiLevel = 0\n)'),
]


def main():
    only = sys.argv[1] if len(sys.argv) > 1 else None
    orig = {}
    # EVERY FILE ANY MUTATION TARGETS. A target missing from this tuple is not a skipped
    # mutation, it is a KeyError that kills the run partway through.
    for path in (PLUGIN, TILES):
        with open(path, encoding='utf-8', newline='') as f:
            orig[path] = f.read()
    # Written into a copy, never into the working tree - see mutate_guard.workspace.
    _work, inside = workspace('npctest')
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
