#!/usr/bin/env python3
"""Mutation test for tools/clienttests/run_menutest.py.

Each entry breaks one thing the scrolling menu is supposed to do and reports WHICH check caught it.
The anchor must be UNIQUE in Client.java - a mutation that lands in a comment is a green tick that
means nothing.

    python3 tools/clienttests/mutate_menutest.py [filter]
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
CLIENT = os.path.join(ROOT, 'src/main/java/jagex2/client/Client.java')
RUNNER = os.path.join(HERE, 'run_menutest.py')

MUTS = [
    # ---- the cap, which is what the round was for
    ('the cap removed, so a long menu runs off the bottom edge again',
     '\t\tint rows = (areaHeight - MENU_CHROME_H) / MENU_ROW_H;', '\t\tint rows = this.menuSize;'),
    ('the cap not clamped to the menu it has, so a short menu claims rows that are not there',
     '\t\tif (rows > this.menuSize) {\n\t\t\trows = this.menuSize;\n\t\t}', ''),
    ('the draw looping over the whole array again rather than the window',
     'for (int p = 0; p < this.menuRowsShown; p++) {\n\t\t\tint var10 = this.menuRowY(p);',
     'for (int p = 0; p < this.menuSize; p++) {\n\t\t\tint var10 = this.menuRowY(p);'),
    ('the placement clamping against 21 again, so the menu draws a pixel past its area',
     '\t\t\tint var4 = rows0 * MENU_ROW_H + MENU_CHROME_H;',
     '\t\t\tint var4 = rows0 * MENU_ROW_H + 21;'),
    # ---- the window
    ('the window ignoring the scroll offset, so the wheel moves nothing',
     'return this.menuSize - 1 - p - this.menuScroll;', 'return this.menuSize - 1 - p;'),
    ('the offset not reset when a menu opens, so a new menu inherits the last one\'s scroll',
     '\t\t\tthis.menuRowsShown = rows0;\n\t\t\tthis.menuScroll = 0;',
     '\t\t\tthis.menuRowsShown = rows0;'),
    ('the offset not clamped at the end, so scrolling runs off the array',
     '\t\t\tif (this.menuScroll > max) {\n\t\t\t\tthis.menuScroll = max;\n\t\t\t}', ''),
    ('the offset not clamped at the top',
     '\t\t\tif (this.menuScroll < 0) {\n\t\t\t\tthis.menuScroll = 0;\n\t\t\t}', ''),
    # ---- the wheel
    # Two tabs, not three: handleGroundItemScroll consumes the wheel with the same line one level
    # deeper, and an anchor that matched both would be a silent skip.
    ('the wheel not consumed, so the camera zooms behind the open menu',
     '\t\tsuper.mouseScrollDelta = 0;\n\t\treturn true;', '\t\treturn true;'),
    ('the wheel taken even with no menu open, stealing it from the pile and the camera',
     'if (super.mouseScrollDelta == 0 || !this.menuVisible) {',
     'if (super.mouseScrollDelta == 0) {'),
    ('a scrolled sidebar menu not marked for redraw, so it does not visibly move',
     '\t\t\tif (this.menuArea == 1) {\n\t\t\t\tthis.redrawSidebar = true;\n\t\t\t}\n'
     '\t\t\tif (this.menuArea == 2) {\n\t\t\t\tthis.redrawChatback = true;\n\t\t\t}\n\t\t}\n'
     '\t\tsuper.mouseScrollDelta = 0;', '\t\t}\n\t\tsuper.mouseScrollDelta = 0;'),
    # ---- the bar
    ('the bar drawn on a menu that fits, a control with nothing to control',
     'if (this.menuSize > this.menuRowsShown) {', 'if (this.menuSize >= this.menuRowsShown) {'),
    ('the thumb not moving with the window',
     'int thumbY = trackY + track * this.menuScroll / this.menuSize;', 'int thumbY = trackY;'),
    ('the bar drawn outside the menu box',
     'int barX = var2 + var4 - MENU_BAR_W - 1;', 'int barX = var2 + var4 + 4;'),
    # ---- the click, which is checked in source because it cannot be lifted out
    ('the click carrying its own copy of the row arithmetic again, so it can disagree with the draw',
     '\t\t\t\tint var12 = this.menuRowY(p);\n\t\t\t\tif (var8 > var5 && var8 < var5 + var7 && '
     'var9 > var12 - 13 && var9 < var12 + 3) {\n\t\t\t\t\tvar10 = this.menuRowIndex(p);',
     '\t\t\t\tint var12 = (this.menuSize - 1 - p) * 15 + var6 + 31;\n\t\t\t\tif (var8 > var5 && '
     'var8 < var5 + var7 && var9 > var12 - 13 && var9 < var12 + 3) {\n\t\t\t\t\tvar10 = p;'),
    # The order the wheel is offered in. An overlay getting it first is the one that shows:
    # scrolling an open menu that happens to be over a tall ground-item pile would move the pile.
    ('an overlay offered the wheel before the open menu',
     '\t\t\tthis.handleMenuScroll();\n'
     '\t\t\t// An overlay that claimed this spot for the wheel takes the turn, before the camera\n'
     '\t\t\t// gets a look.\n'
     '\t\t\tif (super.mouseScrollDelta != 0 && this.plugins != null\n'
     '\t\t\t\t&& this.plugins.onViewportScroll(super.mouseX - this.layout.vpX,\n'
     '\t\t\t\t\tsuper.mouseY - this.layout.vpY, super.mouseScrollDelta)) {\n'
     '\t\t\t\tsuper.mouseScrollDelta = 0;\n'
     '\t\t\t}',
     '\t\t\tif (super.mouseScrollDelta != 0 && this.plugins != null\n'
     '\t\t\t\t&& this.plugins.onViewportScroll(super.mouseX - this.layout.vpX,\n'
     '\t\t\t\t\tsuper.mouseY - this.layout.vpY, super.mouseScrollDelta)) {\n'
     '\t\t\t\tsuper.mouseScrollDelta = 0;\n'
     '\t\t\t}\n'
     '\t\t\tthis.handleMenuScroll();'),
    # ---- A PLUGIN'S PER-ROW COLOUR. The client half of menu restyling: an override is stored by
    # index, drawn in place of the white, beaten by the hover, and wiped by the next rebuild.
    ('''a plugin's colour override read but never used, so the whole feature does nothing''',
     '''			int override = this.menuColour[this.menuRowIndex(p)];
			if (override != 0) {
				var11 = override;
			}''',
     '''			int override = this.menuColour[this.menuRowIndex(p)];'''),
    ('''the override indexed by the visual row rather than the array index, which is upside down''',
     '''int override = this.menuColour[this.menuRowIndex(p)];''',
     '''int override = this.menuColour[p];'''),
    ('''a 0 override applied, so every ordinary row is drawn black''',
     '''			if (override != 0) {
				var11 = override;
			}''',
     '''			if (true) {
				var11 = override;
			}'''),
    ('''the override winning over the hover, so a coloured row stops answering the cursor''',
     '''if (var7 > var2 && var7 < var2 + var4 && var8 > var10 - 13 && var8 < var10 + 3) {''',
     '''if (override == 0 && var7 > var2 && var7 < var2 + var4 && var8 > var10 - 13 && var8 < var10 + 3) {'''),
    ('''the overrides never cleared, so a colour set for one menu lands on the next menu's rows''',
     '''		for (int row = 0; row < this.menuColour.length; row++) {
			this.menuColour[row] = 0;
		}
''',
     ''''''),
    ('''the clear scoped to the menu being rebuilt, which at that point is one row long''',
     '''for (int row = 0; row < this.menuColour.length; row++) {''',
     '''for (int row = 0; row < this.menuSize; row++) {'''),

]


def main():
    only = sys.argv[1] if len(sys.argv) > 1 else None
    with open(CLIENT, encoding='utf-8', newline='') as f:
        orig = f.read()
    # Written into a copy, never into the working tree - see mutate_guard.workspace.
    _work, inside = workspace('menutest')
    muts = [m for m in MUTS if not only or only in m[0]]
    print('running %d of %d mutations' % (len(muts), len(MUTS)))
    bad = loose = 0
    for why, find, repl in muts:
        n = orig.count(find)
        if n != 1:
            print('  SKIP (pattern %s) %s'
                  % ('not found' if n == 0 else 'matches %d times' % n, why))
            bad += 1
            continue
        try:
            with open(inside(CLIENT), 'w', encoding='utf-8', newline='') as f:
                f.write(orig.replace(find, repl))
            r = subprocess.run([sys.executable, inside(RUNNER)], capture_output=True, text=True)
        finally:
            with open(inside(CLIENT), 'w', encoding='utf-8', newline='') as f:
                f.write(orig)
        fired = [l.strip()[5:].strip() for l in r.stdout.split('\n') if l.startswith('FAIL')]
        if r.returncode == 0:
            print('  %-5s %-78s %s' % ('GREEN', why, 'NOT CAUGHT'))
            bad += 1
        elif fired:
            print('  %-5s %-78s %s' % ('red', why, 'caught by: ' + fired[0][:56]))
        else:
            print('  %-5s %-78s %s' % ('red', why,
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
