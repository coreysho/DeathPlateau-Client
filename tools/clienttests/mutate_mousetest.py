#!/usr/bin/env python3
"""Mutation test for tools/clienttests/run_mousetest.py.

Breaks the cursor accessors and the two plugins that use them one plausible way at a time, and
checks run_mousetest notices - and notices by NAMING a check, not by crashing.

The ones that matter most are around the shared pick slot. The scene answers exactly one "what
is at this screen point" question per frame and the client already uses it for walk-here, so
every way of sharing it wrong is here: a hover that walks the player, and a click that gets
swallowed. Both leave the client compiling and most of the game working.

    python3 tools/clienttests/mutate_mousetest.py [filter]
"""
import os
import subprocess
import sys

# THE SOURCE GOES BACK EVEN IF THIS PROCESS IS KILLED. The `finally` below covers a run that
# fails or times out; a SIGTERM skips it entirely, and a kill once left a mutation sitting in
# the tree where the next commit would have shipped it. mutate_guard also has the standalone
# check that every pattern still matches its source exactly once.
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from mutate_guard import guard  # noqa: E402  (after the sys.path line, necessarily)

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
CLIENT = os.path.join(ROOT, 'src/main/java/jagex2/client/Client.java')
CONTEXT = os.path.join(ROOT, 'src/main/java/jagex2/client/plugin/PluginContext.java')
MANAGER = os.path.join(ROOT, 'src/main/java/jagex2/client/plugin/PluginManager.java')
BUILTIN = os.path.join(ROOT, 'src/main/java/jagex2/client/plugin/builtin')
HIGHLIGHT = os.path.join(BUILTIN, 'MouseHighlightPlugin.java')
TILES = os.path.join(BUILTIN, 'TileIndicatorsPlugin.java')
RUNNER = os.path.join(HERE, 'run_mousetest.py')

MUTS = [
    # --- the shared pick slot ----------------------------------------------------------------
    (CLIENT, 'a plugin\'s pick left in place, so hovering walks the player there',
     '''		this.hoverTileX = World3D.clickTileX;
		this.hoverTileZ = World3D.clickTileZ;
		World3D.clickTileX = -1;
		World3D.clickTileZ = -1;''',
     '''		this.hoverTileX = World3D.clickTileX;
		this.hoverTileZ = World3D.clickTileZ;'''),
    (CLIENT, 'every pick taken as a plugin\'s, so clicking the ground never walks',
     'if (!this.hoverPickPending || World3D.clickTileX == -1) {',
     'if (World3D.clickTileX == -1) {'),
    (CLIENT, 'the request left armed, so the next real click is swallowed too',
     '''		World3D.clickTileZ = -1;
		this.hoverPickPending = false;
		return true;''',
     '''		World3D.clickTileZ = -1;
		return true;'''),
    (CLIENT, 'the walk-here branch no longer disowning the pick it just armed',
     '			this.hoverPickPending = false;\n\t\t}\n\t\tif (var5 == 903) {',
     '		}\n\t\tif (var5 == 903) {'),
    (CLIENT, 'a hover armed over the top of a pick something else wanted',
     "&& !World3D.field1044\n\t\t\t\t&& this.mouseInViewport()) {",
     "&& this.mouseInViewport()) {"),

    # --- the cursor ---------------------------------------------------------------------------
    (CLIENT, 'the cursor reported in window coordinates, not the viewport\'s',
     '''	public int viewportMouseX() {
		return super.mouseX - this.layout.vpX;
	}''',
     '''	public int viewportMouseX() {
		return super.mouseX;
	}'''),
    (CLIENT, 'the viewport test ignoring its far edge, so a cursor past it still reads',
     'return x >= 0 && y >= 0 && x < this.layout.openW && y < this.layout.openH;',
     'return x >= 0 && y >= 0;'),
    (CONTEXT, 'the cursor reported as its last position once it leaves the viewport',
     'return this.client.mouseInViewport() ? this.client.viewportMouseX() : -1;',
     'return this.client.viewportMouseX();'),

    # --- the lazy subscription ----------------------------------------------------------------
    (MANAGER, 'the hover answer asked for forever once asked for at all',
     'return !this.idle() && this.hoverWantedAt >= 0\n\t\t\t&& this.frame - this.hoverWantedAt <= HOVER_KEEPALIVE;',
     'return !this.idle() && this.hoverWantedAt >= 0;'),
    (MANAGER, 'the hover answer never asked for, so the tile is always stale',
     'void hoverTileRead() {\n\t\tthis.hoverWantedAt = this.frame;\n\t}',
     'void hoverTileRead() {\n\t}'),
    (MANAGER, 'the keepalive cut to nothing, so reading every other frame flickers',
     'private static final int HOVER_KEEPALIVE = 10;',
     'private static final int HOVER_KEEPALIVE = 0;'),
    (MANAGER, 'the answer still asked for with every plugin turned off',
     'return !this.idle() && this.hoverWantedAt >= 0',
     'return this.hoverWantedAt >= 0'),

    # --- Mouse highlight ----------------------------------------------------------------------
    (HIGHLIGHT, 'the label drawn for Walk here, so it follows the cursor over every empty tile',
     'if (index < 0 || this.ctx.isWalkHere(index)) {',
     'if (index < 0) {'),
    (HIGHLIGHT, 'colour tags left in, so the label reads "Chop down <col=00ffff>Tree"',
     '		return strip(this.ctx.getMenuOption(index));',
     '		return this.ctx.getMenuOption(index);'),
    (HIGHLIGHT, 'only the first colour tag stripped',
     '''		int tag = option.indexOf('<');
		if (tag < 0) {
			return option;
		}''',
     '''		int tag = option.indexOf('<');
		if (tag < 0) {
			return option;
		}
		if (true) {
			int shut = option.indexOf('>');
			return shut < 0 ? option : option.substring(0, tag) + option.substring(shut + 1);
		}'''),
    (HIGHLIGHT, 'an unclosed tag eating the rest of the name',
     '''				out.append(option, open, option.length());
				break;''',
     '''				break;'''),
    (HIGHLIGHT, 'a colour box that is not hex refusing to draw rather than falling back',
     '''		if (cleaned.length() != 6) {
			return 0xFFFF00;
		}''',
     '''		if (cleaned.length() != 6) {
			return 0;
		}'''),
    (HIGHLIGHT, 'the label allowed off the right edge of the viewport',
     '''		if (left + wide + 4 > g.width()) {
			left = x - OFFSET_X - wide;
		}''', ''),
    (HIGHLIGHT, 'the label allowed off the bottom of the viewport',
     '''		if (baseline + 2 > g.height()) {
			baseline = y - OFFSET_Y + tall;
		}''', ''),
    (HIGHLIGHT, 'the label drawn with the cursor outside the game view',
     '''		if (x < 0 || y < 0) {
			return;
		}
''', ''),

    # --- Tile indicators ----------------------------------------------------------------------
    (TILES, 'the line never reaching its far end',
     '''			if (x0 == x1 && y0 == y1) {
				return i + 1;
			}''', '''			if (false) {
				return i + 1;
			}'''),
    (TILES, 'the line stepping only on one axis, so a diagonal comes out as a bar',
     '''			if (twice <= dx) {
				error += dx;
				y0 += stepY;
			}''', ''),
    # The cap is the only bound now. The old code also computed an exact step count alongside
    # it, which meant mutating either one left the other still stopping the loop - a bound that
    # cannot be broken on its own is a bound no test can check.
    (TILES, 'the line left unbounded, so a bad projection grinds inside the render loop',
     'for (int i = 0; i < MAX_STEPS; i++) {',
     'for (int i = 0; i < Integer.MAX_VALUE; i++) {'),
]


def main():
    only = sys.argv[1] if len(sys.argv) > 1 else None
    orig = {}
    for path in (CLIENT, CONTEXT, MANAGER, HIGHLIGHT, TILES):
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
            print('  %-5s %-74s %s' % ('red', why, 'caught by: ' + fired[0][:44]))
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
