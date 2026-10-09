#!/usr/bin/env python3
"""Headless test for the Boosts, Status bars and Skills plugins.

The three plugins that need no new client API at all: each is arithmetic over the skill levels
the plugin context already exposes, so each is driven by setting those levels on a real Client
and asking the plugin what it would say.

What is actually worth testing here is not the drawing - it is the numbers:

    the experience curve  Skills continues the client's experience table past where it ends, and
                          the test points the continuation at the 99 levels the client DOES
                          table. All of them, not a spot check: a formula right at 2 and 99 and
                          wrong at 73 is exactly the bug this catches.
    the combat formula    checked against figures worked out by hand - combat 3 for a new
                          account, 126 for maxed melee with 99 prayer, 123 for the same account
                          as a pure ranger.
    the bar arithmetic    a boosted skill is above its own maximum, and a skill just after login
                          reads 0/0. Both would draw outside the bar or divide by zero, in a
                          render loop, which the manager answers by turning the plugin off.
    the placeholder slots the client keeps two unused skill slots and names them "-unused-". A
                          plugin walking getSkillCount() reaches them, and neither panel may
                          offer one as a skill.

Constructing a Client needs a display (it extends Applet), so this runs under xvfb when there is
no DISPLAY. Without either, the section says it skipped instead of passing.

    python3 tools/clienttests/run_skilltest.py
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
TEST = os.path.join(HERE, 'SkillPluginsTest.java')


def main():
    javac = shutil.which('javac')
    java = shutil.which('java')
    if not javac or not java:
        raise SystemExit('run_skilltest: needs javac and java on PATH')

    work = tempfile.mkdtemp(prefix='skilltest')
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
            raise SystemExit('run_skilltest: the client does not compile')
        print('client compiles: %d sources' % len(sources))

        r = subprocess.run([javac, '-nowarn', '-encoding', 'UTF-8', '-cp', classes, '-d', work, TEST],
                           capture_output=True, text=True)
        if r.returncode != 0:
            print(r.stderr[-6000:])
            raise SystemExit('run_skilltest: the test does not compile')

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
                                       '-Ddp.root=' + ROOT,
                                       '-cp', classes + os.pathsep + work,
                                       'jagex2.client.plugin.builtin.SkillPluginsTest'],
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
