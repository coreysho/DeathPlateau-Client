#!/usr/bin/env python3
"""Mutation test for tools/clienttests/run_displaysettingstest.py.

    python3 tools/clienttests/mutate_displaysettings.py [filter]
"""
import os
import subprocess
import sys

# THE WORKING TREE IS NEVER WRITTEN - see mutate_guard.workspace.
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from mutate_guard import read_source, workspace  # noqa: E402  (after the sys.path line, necessarily)

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
SETTINGS = os.path.join(ROOT, 'src/main/java/jagex2/client/DisplaySettings.java')
RUNNER = os.path.join(HERE, 'run_displaysettingstest.py')

# THE DEFAULT IS STATED TWICE and has to agree with itself: the field's initialiser is what a
# player with no file gets, and the loader's fallback is what a player with a file that does not
# mention the key gets. Either one left on its own is the bug this suite is for, so each is
# mutated separately - a suite that only checked one of them would have let the other through.
MUTS = [
    (SETTINGS, 'the field initialiser back to ON, so a fresh install smooths again',
     'private static boolean smoothScale;                      // bilinear',
     'private static boolean smoothScale = true;               // bilinear'),
    (SETTINGS, 'the loader fallback back to ON, so a file that omits the key smooths again',
     'smoothScale = "true".equals(p.getProperty("smoothscale", "false").trim());',
     'smoothScale = !"false".equals(p.getProperty("smoothscale", "true").trim());'),
    # THE OTHER DIRECTION, and the one that would do real harm: off is a default, not an
    # override. A player who turned smoothing on keeps it, and there is no migration on purpose.
    (SETTINGS, 'a saved value ignored, silently resetting every player who chose smoothing',
     'smoothScale = "true".equals(p.getProperty("smoothscale", "false").trim());',
     'smoothScale = false;'),
    (SETTINGS, 'the saved value inverted, so the switch does the opposite of what it says',
     'smoothScale = "true".equals(p.getProperty("smoothscale", "false").trim());',
     'smoothScale = !"true".equals(p.getProperty("smoothscale", "false").trim());'),
    # The setting must still be settable, or the fix for a player who WANTS smoothing is gone.
    (SETTINGS, 'the setter made a no-op, so the panel switch never sticks',
     '\t\tif (smoothScale != on) {\n\t\t\tsmoothScale = on;\n\t\t\tsave();\n\t\t}',
     '\t\tif (false) {\n\t\t\tsmoothScale = on;\n\t\t\tsave();\n\t\t}'),
    (SETTINGS, 'the setter not saving, so it sticks for this session only',
     '\t\tif (smoothScale != on) {\n\t\t\tsmoothScale = on;\n\t\t\tsave();\n\t\t}',
     '\t\tif (smoothScale != on) {\n\t\t\tsmoothScale = on;\n\t\t}'),
    (SETTINGS, 'the key no longer written, so the file forgets it',
     '\t\t\tp.setProperty("smoothscale", "" + smoothScale);\n', ''),
    # A NEIGHBOUR, to prove the suite is reading this key and not just any key. gameScale is the
    # setting the player was actually changing when they hit this, so it is the right neighbour.
    (SETTINGS, 'the game scale default changed, which this suite should also notice',
     'private static int gameScale = 100;                      // the whole frame at its own size',
     'private static int gameScale = 150;                      // the whole frame at its own size'),
]


def main():
    only = sys.argv[1] if len(sys.argv) > 1 else None
    orig = {SETTINGS: read_source(SETTINGS)}
    _work, inside = workspace('displaysettings')
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
