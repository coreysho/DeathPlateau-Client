#!/usr/bin/env python3
"""Headless test for the Quest helper plugin.

WHAT IT IS FOR. The plugin answers one question - "what do I do next, and where is it" - and the
answer depends on what is standing in the scene around the player, so it cannot be checked by the
sidebar preview or by anything that has no client in it.

WHAT THE SECTIONS ARE FOR:

    the table       that quests.json shipped inside the jar and parsed, with its quests listed and
                    their progress read out of the varps the server sends.
    picking         that NOTHING is tracked until the player presses Track. Guessing the quest
                    handed somebody part-way through six of them whichever sorted earliest.
    the marker      real npcs in the client's own npcs[]/npcIds[], projected by the real
                    projectFromGround. The step that gets marked is the first one of the leg whose
                    npc or object is actually in front of you - not the first step of the leg,
                    which is nearly always a walk and names nobody. That difference IS the bug
                    this suite was written for: pick The Feud, stand in front of Ali Morrisane,
                    and he used to light up only after you had already talked to him.
    out of range    that a step whose tile the scene has not loaded draws nothing in the world,
                    rather than a box pinned to the edge of the screen pointing at a guess - and
                    that the panel still says where to go.
    the varp        that writing the quest's own progress varp moves the panel to the next leg.

Constructing a Client needs a display (it extends Applet), so this runs under xvfb when there is
no DISPLAY. Without either, the run says it skipped instead of passing.

    python3 tools/clienttests/run_questhelpertest.py
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
TEST = os.path.join(HERE, 'QuestHelperTest.java')
# The step table is a RESOURCE: the plugin reads /deathplateau/quests.json off its own
# classpath, and without this the suite would test a plugin with no quests in it.
RES = os.path.join(ROOT, 'src/main/resources')


def main():
    javac = shutil.which('javac')
    java = shutil.which('java')
    if not javac or not java:
        raise SystemExit('run_questhelpertest: needs javac and java on PATH')

    work = tempfile.mkdtemp(prefix='questhelpertest')
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
            raise SystemExit('run_questhelpertest: the client does not compile')
        print('client compiles: %d sources' % len(sources))

        r = subprocess.run([javac, '-nowarn', '-encoding', 'UTF-8', '-cp', classes, '-d', work,
                            TEST], capture_output=True, text=True)
        if r.returncode != 0:
            print(r.stderr[-6000:])
            raise SystemExit('run_questhelpertest: the test does not compile')

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
                                       '-cp', classes + os.pathsep + RES + os.pathsep + work,
                                       'jagex2.client.plugin.builtin.QuestHelperTest'],
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
