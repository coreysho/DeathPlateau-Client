#!/usr/bin/env python3
"""Headless test for the Npc indicators plugin.

ActorTest owns this plugin's PURE rules - matching, a term's own colour, the exact-term arithmetic
the Tag row is built on, the clamps - because they need no client and that suite deliberately has
none. What is here is everything that does need one.

WHAT THE SECTIONS ARE FOR:

    the settings    that each has the editor the sidebar needs, that the drop-down values are the
                    ones the code branches on - a drop-down whose entries no branch matches is a
                    control that silently does nothing, and the compiler cannot see it, because
                    both sides are strings - and that an empty name list marks NOTHING, which is
                    what stops a first run outlining the whole scene.
    the marks       real npcs in the client's own npcs[]/npcIds[], projected by the real
                    projectFromGround against a heightmap and a camera set so one tile lands dead
                    centre. The tag is a recorded font call; the outline is pixels, since it is
                    the only thing here with a shape.
    the menu        that a marked npc's rows are coloured and a GROUND ITEM row is not - only the
                    kind tag tells them apart, and without that check a term like "bones" would
                    colour rows about items. And that nothing is moved: level 6 offers order too,
                    and an npc's Attack row moving changes what a click does.
    tagging         the Tag row on shift-right-click, through the real SettingsMenuOpening, and
                    that running it writes the setting the config box holds - so a tag survives a
                    restart rather than living in a field. Substring matching must not leak into
                    it: Goblin Guard is MATCHED by "Goblin" but is not that term, so it offers to
                    Tag, and untagging Goblin must not take it along.
    appearing       a difference between two ticks, by name rather than by npc, with the first
                    tick after turning it on reporting nothing.

Constructing a Client needs a display (it extends Applet), so this runs under xvfb when there is
no DISPLAY. Without either, the run says it skipped instead of passing.

    python3 tools/clienttests/run_npctest.py
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
TEST = os.path.join(HERE, 'NpcIndicatorsTest.java')


def main():
    javac = shutil.which('javac')
    java = shutil.which('java')
    if not javac or not java:
        raise SystemExit('run_npctest: needs javac and java on PATH')

    work = tempfile.mkdtemp(prefix='npctest')
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
            raise SystemExit('run_npctest: the client does not compile')
        print('client compiles: %d sources' % len(sources))

        r = subprocess.run([javac, '-nowarn', '-encoding', 'UTF-8', '-cp', classes, '-d', work,
                            TEST], capture_output=True, text=True)
        if r.returncode != 0:
            print(r.stderr[-6000:])
            raise SystemExit('run_npctest: the test does not compile')

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
                                       'jagex2.client.plugin.builtin.NpcIndicatorsTest'],
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
