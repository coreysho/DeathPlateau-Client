#!/usr/bin/env python3
"""Headless test for the world map window and its supply of squares.

Two things nobody can see going wrong. The window does its own arithmetic on top of the
renderer's - x grows east and so does a picture, but z grows north and a picture grows down -
and getting that pair wrong shows a real map, drawn correctly, of somewhere the player is not.
And the supply is all silence when it fails: a map that never fills in, one that fills in and
forgets, one that spends every frame asking for ground that is not coming.

The Supply here is a made-up one, which is what that interface is for, so this needs no
display, no network and no cache.

    python3 tools/clienttests/run_mapwindowtest.py

"""
import os
import shutil
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
SRC = os.path.join(ROOT, 'src/main/java')
LAUNCHER_SRC = os.path.join(ROOT, 'launcher/src')
TEST = os.path.join(HERE, 'MapWindowTest.java')


def main():
    javac = shutil.which('javac')
    java = shutil.which('java')
    if not javac or not java:
        raise SystemExit('run_mapwindowtest: needs javac and java on PATH')

    work = tempfile.mkdtemp(prefix='cluetest')
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
            raise SystemExit('run_mapwindowtest: the client does not compile')
        print('client compiles: %d sources' % len(sources))

        # Into `classes` so the test shares the client's package and can reach the window's
        # arithmetic without any of it having to be public for that reason alone.
        r = subprocess.run([javac, '-nowarn', '-encoding', 'UTF-8', '-cp', classes,
                            '-d', classes, TEST], capture_output=True, text=True)
        if r.returncode != 0:
            print(r.stderr[-6000:])
            raise SystemExit('run_mapwindowtest: the test does not compile')

        r = subprocess.run([java, '-Djava.awt.headless=true',
                            # cwd is the temp work dir, so the source checks need to be told
                            # where the repository is.
                            '-Ddp.root=' + ROOT,
                            '-cp', classes,
                            'jagex2.client.MapWindowTest'],
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
        bad = 0 if r.returncode == 0 else 1

        # AND THE KEY'S NAMES AGAINST THE CONTENT. MapIconNames is generated from the content's
        # .loc files; an icon added there and not regenerated here is a blank row in the key,
        # which looks like an icon nobody bothered to name rather than like a stale file.
        # AND THE PLACE NAMES, for the same reason and one more: make_labels also holds every
        # label against the map squares the content packs, so a place whose coordinates put it
        # in the sea fails here. A label is the one thing on this map with no picture behind
        # it - an icon in the sea is obviously wrong, a name in the sea just looks like a
        # place - so that check has nowhere else it could live.
        for gen in ('tools/worldmap/make_icons.py', 'tools/worldmap/make_labels.py'):
            r = subprocess.run([sys.executable, os.path.join(ROOT, gen), '--check'],
                               capture_output=True, text=True)
            # 2 IS "NO CONTENT REPO BESIDE ME", which is a skip and not a failure: this suite
            # runs inside a throwaway copy of the repository with nothing next to it. Printed
            # as FAIL it became the first failing line of every mutation run, and a mutation
            # caught by nothing but that line read as caught.
            for line in (r.stdout + r.stderr).split('\n'):
                if line.strip():
                    print(('ok   ' if r.returncode == 0
                           else 'SKIP ' if r.returncode == 2 else 'FAIL ') + line.strip())
            if r.returncode == 1:
                bad = 1
        return bad
    finally:
        shutil.rmtree(work, ignore_errors=True)


if __name__ == '__main__':
    sys.exit(main())
