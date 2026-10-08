#!/usr/bin/env python3
"""Headless test for API level 4: who else is in the scene.

getNpcs and getPlayers are the first API that tells a plugin about somebody else, and the design
rests on them handing over VALUES rather than a handle. The client's npc config is a 20-entry
round-robin cache that recycles once more than 20 types are on screen, so a plugin holding a live
reference would read an npc that had quietly become a different one - and a handle on an entity is
one field away from a plugin that can act on it, which is the line the API is drawn on.

Needs no display and no network: every rule here is a pure function, and the structural promises
no function can state are read out of the source.

    python3 tools/clienttests/run_actortest.py
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
TEST = os.path.join(HERE, 'ActorTest.java')


def main():
    javac = shutil.which('javac')
    java = shutil.which('java')
    if not javac or not java:
        raise SystemExit('run_actortest: needs javac and java on PATH')

    work = tempfile.mkdtemp(prefix='actortest')
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
            raise SystemExit('run_actortest: the client does not compile')
        print('client compiles: %d sources' % len(sources))

        r = subprocess.run([javac, '-nowarn', '-encoding', 'UTF-8', '-cp', classes,
                            '-d', work, TEST], capture_output=True, text=True)
        if r.returncode != 0:
            print(r.stderr[-6000:])
            raise SystemExit('run_actortest: the test does not compile')

        # No lostcity.* properties on purpose: a plain launch is what a player gets, and a plain
        # launch is what was broken. dp.root is for the one check read out of the source.
        r = subprocess.run([java, '-Ddp.root=' + ROOT,
                            '-cp', classes + os.pathsep + work, 'jagex2.client.plugin.builtin.ActorTest'],
                           capture_output=True, text=True, cwd=work)
        lines = [l for l in r.stdout.split('\n') if l.strip()]
        for l in lines:
            print(l)
        if not lines:
            print(r.stderr[-4000:])
            return 1
        return 0 if r.returncode == 0 else 1
    finally:
        shutil.rmtree(work, ignore_errors=True)


if __name__ == '__main__':
    sys.exit(main())
