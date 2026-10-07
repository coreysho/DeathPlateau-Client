#!/usr/bin/env python3
"""Headless test for Alt-drag: moving any overlay anywhere in the viewport.

Driven through the real PluginManager, with Pix2D bound to an int[] the test reads back - so "the
overlay moved" is a pixel in a different place, not a field with a different number in it. That
distinction is the point here: the whole mechanism is a translation applied on the way to the
screen, and an offset that changed without the pixels following would be exactly the bug.

Three test plugins are compiled in beside the client:

    DragTestPlugin      a box and a line of text at fixed coordinates, which knows nothing about
                        dragging - the claim being that an overlay becomes movable without being
                        told, which is why the two published plugins are movable without a rebuild
    SceneOverlayPlugin  a scene-layer overlay, the one case Alt-drag must refuse: those are drawn
                        over a tile in the world, and an offset on one is a label pointing at the
                        wrong thing

Constructing a Client needs a display (it extends Applet), so this runs under xvfb when there is
no DISPLAY. Without either, the test says it skipped instead of passing.

    python3 tools/clienttests/run_dragtest.py
"""
import os
import shutil
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
SRC = os.path.join(ROOT, 'src/main/java')
# The client's source set is BOTH directories - build.gradle says so, and the client jar
# compiles launcher/src in (Client.relaunchForUpdate runs the launcher, and lostcity.Branding
# is the window icon both of them use). Compiling src/main/java alone builds a subset of the
# real client and fails on anything that reaches across.
LAUNCHER_SRC = os.path.join(ROOT, 'launcher/src')
TEST = os.path.join(HERE, 'OverlayDragTest.java')
# Compiled with it: the plugins the test drags around, in the plugin package so the
# manager's package-private seams are reachable the way GroundItemsTest reaches its own.
HELPERS = [os.path.join(HERE, name) for name in
           ('DragTestPlugin.java', 'SceneOverlayPlugin.java', 'OverlapTestPlugin.java')]


def main():
    javac = shutil.which('javac')
    java = shutil.which('java')
    if not javac or not java:
        raise SystemExit('run_dragtest: needs javac and java on PATH')

    work = tempfile.mkdtemp(prefix='dragtest')
    try:
        # Both source directories: see LAUNCHER_SRC above.
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
            raise SystemExit('run_dragtest: the client does not compile')
        print('client compiles: %d sources' % len(sources))

        r = subprocess.run([javac, '-nowarn', '-encoding', 'UTF-8', '-cp', classes, '-d', work, TEST] + HELPERS,
                           capture_output=True, text=True)
        if r.returncode != 0:
            print(r.stderr[-6000:])
            raise SystemExit('run_dragtest: the test does not compile')

        # The plugin's settings and the plugin store both live in signlink.findcachedir(), which
        # without this is a 2004 search ending at /tmp/.file_store_32 - the real one, shared with
        # every other run on the machine. The property is the first thing findcachedir checks.
        cache = os.path.join(work, 'cache')
        os.makedirs(cache)
        # The plugin FOLDER is found from user.home instead, and is created if missing; pointed
        # here so the run does not make a ~/.deathplateau on whatever machine this is.
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
                                       '-cp', classes + os.pathsep + work,
                                       'jagex2.client.plugin.OverlayDragTest'],
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
