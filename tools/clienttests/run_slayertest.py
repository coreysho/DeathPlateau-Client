#!/usr/bin/env python3
"""Headless test for the loot tracker.

The client is never told that anything died, so the kill is inferred: an npc was standing on a
tile, it is not standing there now, and loot has landed on it. Every part of that is a rule
nobody can see working - a window a tick too short credits nothing and looks like a quiet night,
a kill counted per item reports three kills for one drop - and every failure produces a page of
plausible numbers.

So the rules live in a model with no client in it and are tested as arithmetic. The one thing a
run cannot state, that the npcs are read before the floor, is read out of the source.

Needs no display, no network and no cache.

    python3 tools/clienttests/run_slayertest.py
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
TEST = os.path.join(HERE, 'SlayerTest.java')


def main():
    javac = shutil.which('javac')
    java = shutil.which('java')
    if not javac or not java:
        raise SystemExit('run_slayertest: needs javac and java on PATH')

    work = tempfile.mkdtemp(prefix='slayertest')
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
            raise SystemExit('run_slayertest: the client does not compile')
        print('client compiles: %d sources' % len(sources))

        # Into `classes` so the test shares the package and can reach LootTracker and the
        # plugin's page methods without any of them having to be public.
        r = subprocess.run([javac, '-nowarn', '-encoding', 'UTF-8', '-cp', classes,
                            '-d', classes, TEST], capture_output=True, text=True)
        if r.returncode != 0:
            print(r.stderr[-6000:])
            raise SystemExit('run_slayertest: the test does not compile')

        r = subprocess.run([java, '-Djava.awt.headless=true',
                            # cwd is the temp work dir, so the source checks need to be told
                            # where the repository is.
                            '-Ddp.root=' + ROOT,
                            '-cp', classes,
                            'jagex2.client.plugin.builtin.SlayerTest'],
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
