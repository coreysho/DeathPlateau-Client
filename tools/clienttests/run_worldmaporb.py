#!/usr/bin/env python3
"""The world map orb, in the real client logged in to a LOCAL server.

The orb is drawn onto the minimap's own 2004 frame with the client's 2D primitives, and the
click lands through the same offsets the compass click uses. Neither of those can be judged
from a unit test: one is a picture and the other is whether a mouse event reaches the right
handler through the whole of the client's input path.

So this drives the real client - ResizableShots.java paints its frames onto an image and hands
its mouse and keys to the client's own listeners - takes the fixed frame with the orb on it,
clicks the orb, and reads off the client whether the window opened.

Needs an engine running locally on its own ports, and that engine's login key's modulus, the
same way run_resizableshots.py does:

    NODE_PORT=43694 WEB_PORT=8694 WEB_MANAGEMENT_PORT=8794 npx tsx src/app.ts

    python3 tools/clienttests/run_worldmaporb.py --rsan rsan.txt --out shots

Needs a display, and the client's own jar: gradlew build first, or pass --jar.
"""
import argparse
import os
import shutil
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
SHOTS = os.path.join(HERE, 'ResizableShots.java')
JAR = os.path.join(ROOT, 'build/libs/rs2client-dev.jar')


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--rsan', required=True, help='file with the local engine key modulus')
    ap.add_argument('--out', default=os.path.join(ROOT, 'build/orb'))
    ap.add_argument('--jar', default=JAR)
    ap.add_argument('--host', default='127.0.0.1')
    ap.add_argument('--port', default='43694')
    ap.add_argument('--webport', default='8694')
    args = ap.parse_args()

    java = shutil.which('java')
    javac = shutil.which('javac')
    if not java or not javac:
        raise SystemExit('run_worldmaporb: needs java and javac on PATH')
    if not os.path.exists(args.jar):
        raise SystemExit('run_worldmaporb: no client jar at %s - run gradlew build' % args.jar)
    if not os.environ.get('DISPLAY') and not shutil.which('xvfb-run'):
        print('run_worldmaporb: no display and no xvfb-run, skipping')
        return 0

    out = os.path.abspath(args.out)
    os.makedirs(out, exist_ok=True)
    classes = os.path.join(out, 'classes')
    os.makedirs(classes, exist_ok=True)
    home = os.path.join(out, 'home')
    os.makedirs(home, exist_ok=True)

    r = subprocess.run([javac, '-nowarn', '-encoding', 'UTF-8', '-cp', args.jar,
                        '-d', classes, SHOTS], capture_output=True, text=True)
    if r.returncode != 0:
        print(r.stderr[-4000:])
        raise SystemExit('run_worldmaporb: the driver does not compile')

    rsan = open(args.rsan).read().strip()
    cmd = [java,
           # The driver seeds Math's generator so a run is repeatable, which on a modern JVM
           # needs the module opened to it.
           '--add-opens', 'java.base/java.lang=ALL-UNNAMED',
           '-Dlostcity.host=' + args.host, '-Dlostcity.port=' + args.port,
           '-Dlostcity.webport=' + args.webport, '-Dshots.rsan=' + rsan,
           '-Duser.home=' + home,
           '-cp', args.jar + os.pathsep + classes,
           'ResizableShots', out, 'worldmap', '765', '503', 'orbtest']
    if not os.environ.get('DISPLAY'):
        cmd = ['xvfb-run', '-a'] + cmd
    r = subprocess.run(cmd, capture_output=True, text=True)
    lines = [l for l in (r.stdout + r.stderr).split('\n')
             if l.strip() and 'Picked up' not in l]
    for line in lines:
        print(line)
    if not any(l.strip() == 'done' for l in lines):
        return 1
    return 1 if any(l.startswith('FAIL') for l in lines) else 0


if __name__ == '__main__':
    sys.exit(main())
