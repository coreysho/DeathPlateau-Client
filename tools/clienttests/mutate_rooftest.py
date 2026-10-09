#!/usr/bin/env python3
"""Mutation test for tools/clienttests/run_rooftest.py.

    python3 tools/clienttests/mutate_rooftest.py [filter]
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
SETTINGS = os.path.join(ROOT, 'src/main/java/jagex2/client/QolSettings.java')
RUNNER = os.path.join(HERE, 'run_rooftest.py')

MUTS = [
    (CLIENT, 'the toggle not read at all, so Hide roofs does nothing',
     '\t\tif (QolSettings.on(QolSettings.ROOFS_OFF)) {\n\t\t\treturn this.currentLevel;\n\t\t}\n',
     ''),
    (CLIENT, 'the toggle returning 3, which is "draw everything" - the opposite of hiding',
     'if (QolSettings.on(QolSettings.ROOFS_OFF)) {\n\t\t\treturn this.currentLevel;',
     'if (QolSettings.on(QolSettings.ROOFS_OFF)) {\n\t\t\treturn 3;'),
    # The same bug as putting the toggle inside the pitch test, in one edit that still compiles:
    # the setting only applies when the tile tests would have run.
    (CLIENT, 'the toggle made conditional on the camera pitch, so tilting down puts the roofs back',
     'if (QolSettings.on(QolSettings.ROOFS_OFF)) {\n\t\t\treturn this.currentLevel;',
     'if (QolSettings.on(QolSettings.ROOFS_OFF) && this.cameraPitch < 310) {\n'
     '\t\t\treturn this.currentLevel;'),
    (CLIENT, 'the roof test on the player\'s own tile dropped, so standing indoors shows the roof',
     '\t\tif ((this.levelTileFlags[this.currentLevel][localPlayer.field1157 >> 7]'
     '[localPlayer.field1158 >> 7] & 0x4) != 0) {\n\t\t\tvar2 = this.currentLevel;\n\t\t}',
     ''),
    (CLIENT, 'the divide-by-zero guard removed, so a camera on the player\'s tile throws again',
     '} else if (var10 != 0) {\n\t\t\t\tint var13 = var9 * 65536 / var10;',
     '} else {\n\t\t\t\tint var13 = var9 * 65536 / var10;'),
    (CLIENT, 'the anticheat cycle block skipped when roofs are off',
     '\t\tint var2 = 3;\n\t\tif (this.cameraPitch < 310) {',
     '\t\tint var2 = 3;\n\t\tif (QolSettings.on(QolSettings.ROOFS_OFF)) {\n'
     '\t\t\treturn this.currentLevel;\n\t\t}\n\t\tif (this.cameraPitch < 310) {'),
    (CLIENT, 'the cutscene camera made to obey the toggle, overriding a scripted shot',
     '\tpublic int getTopLevelCutscene() {',
     '\tpublic int getTopLevelCutscene() {\n\t\tif (QolSettings.on(QolSettings.ROOFS_OFF)) {\n'
     '\t\t\treturn this.currentLevel;\n\t\t}'),
    # Anchored on the TAIL of each array rather than on the whole of it. Settings come and go -
    # five have left this file and two have come back - and a pattern that spells out every
    # entry goes stale on each of those, which is how four of these mutations ended up silently
    # skipping for a fortnight. Hide roofs is last and the comment above DEFAULTS says why.
    (SETTINGS, 'Hide roofs defaulting ON, which changes the world for everyone who updates',
     '\n\t\tfalse, true\n\t};', '\n\t\ttrue, true\n\t};'),
    (SETTINGS, 'a label added without a key, so the three parallel arrays disagree',
     '"Plugin notifications on the desktop"\n\t};',
     '"Plugin notifications on the desktop", "Something else"\n\t};'),
    # The panel outgrowing its viewport. NOT by adding settings: COUNT comes from three parallel
    # arrays that have to be edited together, and a single-edit version of that breaks the parity
    # check instead and gets attributed to it. Taller rows is the same arithmetic in one edit.
    (CLIENT, 'the panel rows made taller than the viewport can fit',
     'private static final int QOL_PANEL_ROW_H = 14;',
     'private static final int QOL_PANEL_ROW_H = 22;'),
    # Escape went back to the panel with it, and the switch is the whole point of it being there.
    (CLIENT, 'Escape closing interfaces whatever the setting says',
     'if (key == GameShell.KEY_ESCAPE && QolSettings.on(QolSettings.ESC_CLOSE)) {',
     'if (key == GameShell.KEY_ESCAPE) {'),
    (CLIENT, 'Escape swallowed once it has closed something, so nothing after it sees the key',
     '\t\t\t\t\t\tthis.closeInterfaces();\n\t\t\t\t\t}',
     '\t\t\t\t\t\tthis.closeInterfaces();\n\t\t\t\t\t\tcontinue;\n\t\t\t\t\t}'),
]


def main():
    only = sys.argv[1] if len(sys.argv) > 1 else None
    orig = {}
    for path in (CLIENT, SETTINGS):
        with open(path, encoding='utf-8', newline='') as f:
            orig[path] = f.read()
    # Written into a copy, never into the working tree - see mutate_guard.workspace.
    _work, inside = workspace('rooftest')
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
            print('  %-5s %-80s %s' % ('GREEN', why, 'NOT CAUGHT'))
            bad += 1
        elif fired:
            print('  %-5s %-80s %s' % ('red', why, 'caught by: ' + fired[0][:52]))
        else:
            print('  %-5s %-80s %s' % ('red', why,
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
