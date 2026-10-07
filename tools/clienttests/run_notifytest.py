#!/usr/bin/env python3
"""Headless test for plugin notifications, sound, and the Idle notifier.

The check worth the most here is a safety one. The client's audio loop wraps playback in a catch,
and that catch REPORTS THE FAILURE TO THE SERVER - out.p1isaac(80). A sound id with nothing behind
it makes the loop throw, so an unvalidated id coming from a plugin would be a plugin causing a
packet to be sent, which is the one thing the plugin API does not do. The test asks for every
shape of bad id and checks none of them reached the queue.

The rest: rate limiting, because a plugin calling notify every tick must not get a notification
every tick; and the plugin's arm-and-re-arm logic, which is what stops a low-hitpoints warning
firing every tick for as long as you happen to be hurt.

This one takes about fifteen seconds to run. The rate limits are real clocks and the test waits
them out rather than mocking a clock that would then be the thing under test.

Constructing a Client needs a display (it extends Applet), so this runs under xvfb when there is
no DISPLAY.

    python3 tools/clienttests/run_notifytest.py
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
TEST = os.path.join(HERE, 'NotifyTest.java')


def main():
    javac = shutil.which('javac')
    java = shutil.which('java')
    if not javac or not java:
        raise SystemExit('run_notifytest: needs javac and java on PATH')

    work = tempfile.mkdtemp(prefix='notifytest')
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
            raise SystemExit('run_notifytest: the client does not compile')
        print('client compiles: %d sources' % len(sources))

        r = subprocess.run([javac, '-nowarn', '-encoding', 'UTF-8', '-cp', classes, '-d', work, TEST],
                           capture_output=True, text=True)
        if r.returncode != 0:
            print(r.stderr[-6000:])
            raise SystemExit('run_notifytest: the test does not compile')

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
                                       'jagex2.client.plugin.builtin.NotifyTest'],
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
