#!/usr/bin/env python3
"""Headless test for the minimap's function icons, and a strip of them to look at.

A map icon is three numbers that have to agree: the slot a sprite sits in on
content/sprites/mapfunction.png, the mapfunction= on the loc, and the count Client.java loops over
when it loads them. Nothing checks the third, it is a literal, and an icon past it is never loaded
- imageMapfunction[i] stays null and the minimap simply draws no icon, which looks exactly like a
loc that was never placed.

Reads the built media archive, so the engine's cache must have been built first:
    cd Engine-TS && npx tsx tools/pack/Build.ts

    python3 tools/clienttests/run_mapicontest.py [--engine ../Engine-TS] [--out build/preview]
"""
import argparse
import os
import shutil
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))          # the javaclient repo
SRC = os.path.join(ROOT, 'src/main/java')
LAUNCHER_SRC = os.path.join(ROOT, 'launcher/src')
TEST = os.path.join(HERE, 'MapIconTest.java')
ENGINE = os.path.join(os.path.dirname(ROOT), 'Engine-TS')


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--engine', default=ENGINE, help='Engine-TS checkout holding data/pack/client')
    ap.add_argument('--out', default=os.path.join(ROOT, 'build', 'preview'))
    a = ap.parse_args()

    media = os.path.join(a.engine, 'data', 'pack', 'client', 'media')
    if not os.path.exists(media):
        raise SystemExit('run_mapicontest: no %s - build the cache first' % media)

    javac, java = shutil.which('javac'), shutil.which('java')
    if not javac or not java:
        raise SystemExit('run_mapicontest: needs javac and java on PATH')

    work = tempfile.mkdtemp(prefix='mapicontest')
    try:
        sources = []
        for where in (SRC, LAUNCHER_SRC):
            for root, _dirs, files in os.walk(where):
                sources += [os.path.join(root, f) for f in files if f.endswith('.java')]
        classes = os.path.join(work, 'classes')
        os.makedirs(classes)
        r = subprocess.run([javac, '-nowarn', '-encoding', 'UTF-8', '-d', classes] + sources,
                           capture_output=True, text=True)
        if r.returncode != 0:
            print(r.stderr[-4000:])
            raise SystemExit('run_mapicontest: the client does not compile')
        print('client compiles: %d sources' % len(sources))

        r = subprocess.run([javac, '-nowarn', '-encoding', 'UTF-8', '-cp', classes,
                            '-d', classes, TEST], capture_output=True, text=True)
        if r.returncode != 0:
            print(r.stderr[-6000:])
            raise SystemExit('run_mapicontest: the test does not compile')

        client_src = os.path.join(SRC, 'jagex2', 'client', 'Client.java')
        r = subprocess.run([java, '-Djava.awt.headless=true', '-cp', classes,
                            'jagex2.client.MapIconTest', media, client_src, a.out],
                           capture_output=True, text=True, cwd=work)
        lines = [l for l in r.stdout.split('\n') if l.strip()]
        for l in lines:
            print(l)
        if not lines:
            print(r.stderr[-4000:])
            return 1
        if r.returncode != 0 and not any(l.startswith('FAIL') for l in lines):
            # NON-ZERO WITH NO FAILURE NAMED means the suite died part way through, and the
            # reason is on stderr. Printing stdout alone here showed a run that simply stopped
            # mid-section with nothing to say why, which cost an afternoon once.
            print(r.stderr[-4000:])
        return 0 if r.returncode == 0 else 1
    finally:
        shutil.rmtree(work, ignore_errors=True)


if __name__ == '__main__':
    sys.exit(main())
