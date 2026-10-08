#!/usr/bin/env python3
"""Headless test for the XP drops plugin.

Almost all of this feature is text, so the harness is a PixFont subclass that paints nothing and
records (x, y, colour, text) per call - recording the call IS the measurement. The parts that are
not text, the tracker box and its progress bar, are checked as pixels out of a real Pix2D buffer.

WHAT THE SECTIONS ARE FOR:

    the rules       every number a player can type, at its bounds. The failure mode of an
                    unclamped setting is the feature disappearing rather than an error, so these
                    are the cheapest checks here and catch the most.
    the settings    that each one has the editor the sidebar needs, that the drop-down values are
                    the ones the code branches on - a drop-down whose entries no branch matches
                    is a control that silently does nothing, and the compiler cannot see it,
                    because both sides are strings - and that the defaults are the behaviour the
                    plugin shipped with.
    the drops       what lands on screen, including the cap applying while rows are up and the
                    fade running from when a row was created rather than from the last gain.
    grouping        that adding gains up does NOT extend the row, which is the whole difficulty:
                    a running total that refreshed its own fade would never leave.
    level ups       driven through the real event, because the thing that makes them possible is
                    that the packet handler posts BEFORE it recomputes the level.

Constructing a Client needs a display (it extends Applet), so this runs under xvfb when there is
no DISPLAY. Without either, the sections that need one say they skipped instead of passing.

    python3 tools/clienttests/run_xptest.py
"""
import os
import shutil
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
SRC = os.path.join(ROOT, 'src/main/java')
# The client's source set is BOTH directories - build.gradle says so, and the client jar compiles
# launcher/src in. Compiling src/main/java alone builds a subset of the real client.
LAUNCHER_SRC = os.path.join(ROOT, 'launcher/src')
TEST = os.path.join(HERE, 'XpDropsTest.java')


def main():
    javac = shutil.which('javac')
    java = shutil.which('java')
    if not javac or not java:
        raise SystemExit('run_xptest: needs javac and java on PATH')

    work = tempfile.mkdtemp(prefix='xptest')
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
            raise SystemExit('run_xptest: the client does not compile')
        print('client compiles: %d sources' % len(sources))

        r = subprocess.run([javac, '-nowarn', '-encoding', 'UTF-8', '-cp', classes, '-d', work,
                            TEST], capture_output=True, text=True)
        if r.returncode != 0:
            print(r.stderr[-6000:])
            raise SystemExit('run_xptest: the test does not compile')

        # The plugin's settings and the plugin store live in signlink.findcachedir(), which
        # without this is a 2004 search ending at /tmp/.file_store_32 - the real one, shared with
        # every other run on the machine. The property is the first thing findcachedir checks.
        cache = os.path.join(work, 'cache')
        os.makedirs(cache)
        # The plugin FOLDER is found from user.home instead, and created if missing; pointed here
        # so the run does not make a ~/.deathplateau on whatever machine this is.
        home = os.path.join(work, 'home')
        os.makedirs(home)

        launcher = []
        if not os.environ.get('DISPLAY'):
            xvfb = shutil.which('xvfb-run')
            if xvfb:
                launcher = [xvfb, '-a']
        r = subprocess.run(launcher + [java,
                                       '-Dlostcity.cachedir=' + cache,
                                       '-Duser.home=' + home,
                                       '-Ddp.root=' + ROOT,
                                       '-cp', classes + os.pathsep + work,
                                       'jagex2.client.plugin.builtin.XpDropsTest'],
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
