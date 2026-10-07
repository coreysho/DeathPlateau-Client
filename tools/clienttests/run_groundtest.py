#!/usr/bin/env python3
"""Headless test for the Ground items plugin.

NO MORE SOURCE SLICING. The old version of this test pulled drawGroundItems() and its forty-odd
constants out of Client.java by regex, because that was the only way to reach 150 lines of layout
arithmetic buried in a 14,000-line class that cannot be constructed without a cache, a window and
a server. The feature is a plugin now, so there is nothing to slice: it is ordinary code in its
own package, started by the real PluginManager and driven through the same public methods
Client.java calls. What the slicing used to buy, being the real code, now comes for free.

Everything that can be real IS real:

    real  the plugin, started by PluginManager.reload() with the other built-ins turned off
    real  jagex2.client.GroundItemPrefs, against a settings file in this run's own cache dir
    real  jagex2.config.ObjType, through ObjType.get() with its ten-slot cache primed by hand
    real  the projection - Client.projectFromGround over a heightmap, with a camera placed so
          that one named tile lands in the middle of the viewport
    real  jagex2.graphics.Pix2D, bound to an int[] the test reads back, so "the scroll bar is
          drawn" is a pixel rather than a code path
    stub  the font, because recording (x, y, colour, text) per row IS the measurement

Constructing a Client needs a display (it extends Applet), so this runs under xvfb when there is
no DISPLAY. Without either, the sections that need one say they skipped instead of passing.

    python3 tools/clienttests/run_groundtest.py
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
TEST = os.path.join(HERE, 'GroundItemsTest.java')


def main():
    javac = shutil.which('javac')
    java = shutil.which('java')
    if not javac or not java:
        raise SystemExit('run_groundtest: needs javac and java on PATH')

    work = tempfile.mkdtemp(prefix='groundtest')
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
            raise SystemExit('run_groundtest: the client does not compile')
        print('client compiles: %d sources' % len(sources))

        r = subprocess.run([javac, '-nowarn', '-encoding', 'UTF-8', '-cp', classes, '-d', work, TEST],
                           capture_output=True, text=True)
        if r.returncode != 0:
            print(r.stderr[-6000:])
            raise SystemExit('run_groundtest: the test does not compile')

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
                                       'jagex2.client.plugin.builtin.GroundItemsTest'],
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
