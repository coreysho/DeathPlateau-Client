#!/usr/bin/env python3
"""474's Equipment Stats window, from the real client logged in to a LOCAL server: the figure in it
(the component with client code 328) with one weapon in hand and then another, so its pose can be
compared weapon by weapon. Nothing is stubbed - ResizableShots.java's "doll" mode drives the real
client, wields each weapon from the window's own side panel, and writes a frame of each.

Set up exactly as run_resizableshots.py describes (an engine on its own ports, and that engine's
login key's modulus in a file):

    python3 tools/clienttests/run_dollshots.py --rsan rsan.txt --out shots/doll \
        --weapons staff_of_air,bronze_sword,abyssal_whip

It prints, for every weapon, what the client has for the doll - the stand seq the server gave the
player, the seq the component is animating, the frame it is on and the transform that frame applies -
and then how many pixels of the window differ between every pair of weapons. Two weapons whose frames
are identical are two weapons drawn in the same pose.
"""
import argparse
import itertools
import os
import random
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
JAR = os.path.join(ROOT, 'build/libs/rs2client-dev.jar')

# The window is drawn in the middle of the 765x503 frame; this is the doll's own corner of it, so a
# compare is not swayed by the bonus numbers beside it changing.
DOLL_BOX = (150, 60, 420, 350)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--rsan', required=True)
    ap.add_argument('--out', required=True)
    ap.add_argument('--weapons', default='staff_of_air,bronze_sword')
    ap.add_argument('--jar', help='client jar to drive (default build/libs/rs2client-dev.jar)')
    ap.add_argument('--user', help='account to log in as (default: a fresh throwaway)')
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
    user = a.user or ('doll%04d' % random.randint(0, 9999))
    home = os.path.join(out, 'home')
    os.makedirs(home, exist_ok=True)
    cmd = ['java', '-Dshots.rsan=' + rsan, '-Dlostcity.host=127.0.0.1', '-Dlostcity.port=%d' % a.port,
           '-Dlostcity.webport=%d' % a.webport, '-Duser.home=' + home, '-Dshots.weapons=' + a.weapons,
           '-cp', jar + os.pathsep + classes, 'ResizableShots', out, 'doll', '765', '503', user, 'doll']
    print('account: ' + user)
    p = subprocess.run(cmd, capture_output=True, text=True, cwd=out)
    for line in p.stdout.split('\n'):
        if line.startswith(('ok', 'FAIL', 'doll', 'shot', 'no ', 'Worn')):
            print('   ' + line)
    if p.returncode != 0:
        print(p.stdout[-3000:] + p.stderr[-3000:])
        return 1
    compare(out, ['bare'] + [w.strip() for w in a.weapons.split(',')])
    return 0


def compare(out, names):
    """How many pixels of the doll differ between each pair of weapons. Zero means the same pose."""
    from PIL import Image, ImageChops
    shots = {}
    for name in names:
        path = os.path.join(out, 'doll_%s.png' % name)
        if os.path.exists(path):
            shots[name] = Image.open(path).convert('RGB').crop(DOLL_BOX)
    print('')
    for a, b in itertools.combinations(sorted(shots, key=names.index), 2):
        d = ImageChops.difference(shots[a], shots[b]).convert('L').point(lambda v: 255 if v > 8 else 0)
        n = sum(1 for px in d.getdata() if px)
        print('   %-24s vs %-24s %6d pixels differ%s' % (a, b, n, '   <- SAME POSE' if n == 0 else ''))


if __name__ == '__main__':
    sys.exit(main())
