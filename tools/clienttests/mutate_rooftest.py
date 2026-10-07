#!/usr/bin/env python3
"""Mutation test for tools/clienttests/run_rooftest.py.

    python3 tools/clienttests/mutate_rooftest.py [filter]
"""
import os
import subprocess
import sys

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
    (SETTINGS, 'Hide roofs defaulting ON, which changes the world for everyone who updates',
     'true, true, true, true, true, true, true, true, true, true, true, true, true,\n\t\tfalse',
     'true, true, true, true, true, true, true, true, true, true, true, true, true,\n\t\ttrue'),
    (SETTINGS, 'a label added without a key, so the three parallel arrays disagree',
     '"Anti-drag (items drag after 0.2s)", "Hide roofs"',
     '"Anti-drag (items drag after 0.2s)", "Hide roofs", "Something else"'),
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
