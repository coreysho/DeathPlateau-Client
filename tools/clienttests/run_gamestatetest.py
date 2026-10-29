#!/usr/bin/env python3
"""Headless test for API level 10: varps, varbits and the inventory.

Both halves of that level decode a number out of a packed one, and both have an off-by-one that
hands back another perfectly ordinary number rather than failing: a varbit mask one bit short
reads a four-bit patch stage as its lower three bits, and an inventory slot that forgets its
plus one reads the obj one id below the real one. Nothing throws either way, and there is no
picture to compare - so the two decodes are pure functions, swept across every bit range there
is rather than the four anybody would write out by hand.

Also checks the class contract that the client NULLS its varps on logout while overlays carry on
being drawn: every getter has to answer rather than throw with no world behind it.

Constructing a Client needs a display (it extends Applet), so this runs under xvfb when there is
no DISPLAY. Without either, the sections that need one say they skipped instead of passing.

Needs no network and no cache.

    python3 tools/clienttests/run_gamestatetest.py
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
TEST = os.path.join(HERE, 'GameStateTest.java')


def main():
    javac = shutil.which('javac')
    java = shutil.which('java')
    if not javac or not java:
        raise SystemExit('run_gamestatetest: needs javac and java on PATH')

    work = tempfile.mkdtemp(prefix='gamestatetest')
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
            raise SystemExit('run_gamestatetest: the client does not compile')
        print('client compiles: %d sources' % len(sources))

        # Into `classes` so the test shares the package and can reach PluginContext's
        # package-private constructor and its two decoders.
        r = subprocess.run([javac, '-nowarn', '-encoding', 'UTF-8', '-cp', classes,
                            '-d', classes, TEST], capture_output=True, text=True)
        if r.returncode != 0:
            print(r.stderr[-6000:])
            raise SystemExit('run_gamestatetest: the test does not compile')

        # The plugin folder is found from user.home and created if missing; pointed here so the
        # run does not make a ~/.deathplateau on whatever machine this is.
        home = os.path.join(work, 'home')
        os.makedirs(home)

        launcher = []
        if not os.environ.get('DISPLAY'):
            xvfb = shutil.which('xvfb-run')
            if xvfb:
                launcher = [xvfb, '-a']
        r = subprocess.run(launcher + [java,
                                       '-Duser.home=' + home,
                                       # cwd is the temp work dir, so the source checks need to
                                       # be told where the repository is.
                                       '-Ddp.root=' + ROOT,
                                       '-cp', classes,
                                       'jagex2.client.plugin.GameStateTest'],
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
