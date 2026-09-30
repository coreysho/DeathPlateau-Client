#!/usr/bin/env python3
"""The F10 left-click swaps panel with more swaps in it than it can show, from the real client
logged in to a LOCAL server. Nothing is stubbed: ResizableShots.java's "swaps" mode puts 40 swaps
into the real MenuSwaps (in the harness's own throwaway cache dir), opens the panel with a real F10
and scrolls it with real wheel events through the client's own listener.

This is the evidence that MenuSwaps.MAX could go from 16 to 128 - the old cap was the panel's
height, and the panel scrolls now. RoofTest's panelTests() proves the geometry at every list length
in every display mode; this proves a player can actually see and click the bottom of the list.

Set up exactly as run_resizableshots.py describes (an engine on its own ports, and that engine's
login key's modulus in a file):

    python3 tools/clienttests/run_swapshots.py --rsan rsan.txt --out shots/swaps

It writes, for the fixed 765x503 frame and for a 1280x800 window in each resizable layout:
  <mode>_swaps_top.png         the panel with the list at the top, bar at the top
  <mode>_swaps_bottom.png      the same list scrolled to the bottom with the wheel
  <mode>_swaps_clicked.png     after clicking the bottom row - that swap, and only that one,
                               becomes "on any <kind>", which is what says a click on a scrolled
                               row is read through the scroll position
  <mode>_swaps_backtotop.png   wheeled back up, stopped at the top
  <mode>_swaps_bardragged.png  the bar's own input - both arrows held, then a press at the foot
                               of the track - which the wheel never goes near
"""
import argparse
import os
import random
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
JAR = os.path.join(ROOT, 'build/libs/rs2client-dev.jar')

# (mode, width, height, prefix): all three display modes, the fixed frame first because it is the
# one that cannot grow and so the one the panel has to fit or scroll inside.
RUNS = [('swaps', 765, 503, 'fixed'),
        ('swapsclassic', 1280, 800, 'classic'),
        ('swapsmodern', 1280, 800, 'modern')]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--rsan', required=True)
    ap.add_argument('--out', required=True)
    ap.add_argument('--swaps', type=int, default=40, help='how many swaps to store (default 40)')
    ap.add_argument('--jar', help='client jar to drive (default build/libs/rs2client-dev.jar)')
    ap.add_argument('--port', type=int, default=43694)
    ap.add_argument('--webport', type=int, default=8694)
    a = ap.parse_args()
    jar = os.path.abspath(a.jar) if a.jar else JAR
    out = os.path.abspath(a.out)
    os.makedirs(out, exist_ok=True)
    rsan = open(a.rsan).read().strip()
    classes = os.path.join(out, 'classes')
    os.makedirs(classes, exist_ok=True)
    r = subprocess.run(['javac', '-nowarn', '-encoding', 'UTF-8', '-cp', jar, '-d', classes,
                        os.path.join(HERE, 'ResizableShots.java')], capture_output=True, text=True)
    if r.returncode != 0:
        print(r.stdout + r.stderr)
        return 1

    fails = 0
    for mode, w, h, prefix in RUNS:
        user = 'swap%04d' % random.randint(0, 9999)
        home = os.path.join(out, 'home_' + prefix)   # its own cache, so neither run inherits the other's
        os.makedirs(home, exist_ok=True)
        print('%s %dx%d as %s' % (prefix, w, h, user))
        cmd = ['java', '-Dshots.rsan=' + rsan, '-Dlostcity.host=127.0.0.1', '-Dlostcity.port=%d' % a.port,
               '-Dlostcity.webport=%d' % a.webport, '-Duser.home=' + home, '-Dshots.swaps=%d' % a.swaps,
               '-cp', jar + os.pathsep + classes, 'ResizableShots', out, mode, str(w), str(h), user, prefix]
        p = subprocess.run(cmd, capture_output=True, text=True, cwd=out)
        for line in p.stdout.split('\n'):
            if line.startswith(('ok', 'FAIL', 'shot')):
                print('   ' + line)
            if line.startswith('FAIL'):
                fails += 1
        if p.returncode != 0:
            print(p.stdout[-3000:] + p.stderr[-3000:])
            fails += 1
    print('')
    print('ALL PASS' if not fails else '%d FAILED' % fails)
    return 1 if fails else 0


if __name__ == '__main__':
    sys.exit(main())
