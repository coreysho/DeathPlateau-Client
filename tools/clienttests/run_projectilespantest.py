#!/usr/bin/env python3
"""Headless test: a projectile in flight must not change the order the scene paints walls in.

Compiles the whole client, reads the padding Client.pushProjectiles adds a projectile to the scene
with, and drives the REAL World3D with ProjectileSpanTest.java (stub models that record the order
they are drawn in): seven wall segments with an open door leaf in the middle, a player beside the
door tile, a projectile at every 16 units around them, from 24 camera positions.

It fails on 377's own padding of 60 - a projectile within 60 of a tile edge spans two tiles, the
scene holds back both tiles' walls until it is drawn, and a wall behind an open door came out after
the door and painted over it (the toxic blowpipe's vanishing door, 2026-09-27). The run with 60 is
kept as a control, so the test is known to be able to fail.

    python3 tools/clienttests/run_projectilespantest.py
"""
import os
import re
import shutil
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
CLIENT = os.path.join(ROOT, 'src/main/java/jagex2/client/Client.java')
TEST = os.path.join(HERE, 'ProjectileSpanTest.java')


def padding():
    src = open(CLIENT, encoding='utf-8').read()
    body = src[src.index('public void pushProjectiles()'):]
    body = body[:body.index('\n\t}\n') if '\n\t}\n' in body else body.index('\r\n\t}\r\n')]
    m = re.search(r'this\.scene\.method285\(-1, var2, [^;]*?, this\.currentLevel, (\d+), ', body)
    if not m:
        raise SystemExit('run_projectilespantest: no scene.method285 call in pushProjectiles')
    return int(m.group(1))


def run(classes, pad):
    r = subprocess.run([shutil.which('java'), '-cp', classes, 'ProjectileSpanTest', str(pad)],
                       capture_output=True, text=True)
    if r.returncode != 0:
        print(r.stderr[-4000:])
        raise SystemExit('run_projectilespantest: the harness failed')
    return dict(kv.split('=') for kv in r.stdout.split())


def main():
    pad = padding()
    work = tempfile.mkdtemp(prefix='projspan')
    try:
        classes = os.path.join(work, 'classes')
        os.makedirs(classes)
        sources = [os.path.join(r, f) for r, _d, fs in os.walk(os.path.join(ROOT, 'src/main/java'))
                   for f in fs if f.endswith('.java')]
        r = subprocess.run([shutil.which('javac'), '-nowarn', '-encoding', 'UTF-8', '-d', classes] + sources + [TEST],
                           capture_output=True, text=True)
        if r.returncode != 0:
            print(r.stderr[-4000:])
            raise SystemExit('run_projectilespantest: the client does not compile')
        fails = 0
        got = run(classes, pad)
        ok = got['orderChanged'] == '0' and got['projMissing'] == '0'
        fails += not ok
        print('%s  pushProjectiles pads a projectile by %d: %s frames, the wall order never moves '
              '(%s changed) and the projectile is always drawn (%s missing)'
              % ('ok  ' if ok else 'FAIL', pad, got['frames'], got['orderChanged'], got['projMissing']))
        control = run(classes, 60)
        ok = control['orderChanged'] != '0'
        fails += not ok
        print('%s  control: 377\'s padding of 60 does move it (%s of %s frames), so the test can fail'
              % ('ok  ' if ok else 'FAIL', control['orderChanged'], control['frames']))
        print('ALL PASS' if not fails else '%d FAILED' % fails)
        sys.exit(1 if fails else 0)
    finally:
        shutil.rmtree(work, ignore_errors=True)


if __name__ == '__main__':
    main()
