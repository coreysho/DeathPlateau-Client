#!/usr/bin/env python3
"""Headless test for the launcher's Discord sign-in and for the cards it puts on the login screen.

TWO SUITES, BECAUSE THERE ARE TWO PROGRAMS. DiscordSignInTest stands up a fake website and a fake
browser on the loopback and takes the launcher through a real sign-in; LauncherAccountsTest reads
what that left behind the way the client does and checks the login screen's row.

The loopback hand-over is the piece of this feature that had never run anywhere before - a desktop
program listening on 127.0.0.1 and a browser arriving with a token in a query string - and the only
other way to exercise it is a browser, a website and a Discord account.

Needs javac, java and a free loopback port. No display, no network off this machine, and it points
user.home at a scratch directory per case so a developer's real ~/.deathplateau is never touched.

    python3 tools/clienttests/run_discordsignintest.py
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
SUITES = [
    (os.path.join(HERE, 'DiscordSignInTest.java'), 'lostcity.DiscordSignInTest'),
    (os.path.join(HERE, 'LauncherAccountsTest.java'), 'jagex2.client.LauncherAccountsTest'),
]


def main():
    javac = shutil.which('javac')
    java = shutil.which('java')
    if not javac or not java:
        raise SystemExit('run_discordsignintest: needs javac and java on PATH')

    work = tempfile.mkdtemp(prefix='discordsignin')
    failed = 0
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
            raise SystemExit('run_discordsignintest: the client and launcher do not compile')
        print('client and launcher compile: %d sources' % len(sources))

        for test, main_class in SUITES:
            # Each suite lives in the package it tests, so it can call the package-private reload()
            # between cases - without that every case would see the one before it.
            r = subprocess.run([javac, '-nowarn', '-encoding', 'UTF-8', '-cp', classes, '-d', classes, test],
                               capture_output=True, text=True)
            if r.returncode != 0:
                print(r.stderr[-6000:])
                raise SystemExit('run_discordsignintest: %s does not compile' % os.path.basename(test))

            print('')
            print('== %s' % os.path.basename(test))
            r = subprocess.run([java, '-cp', classes, '-Djava.awt.headless=true', main_class],
                               capture_output=True, text=True, cwd=work)
            lines = [l for l in r.stdout.split('\n') if l.strip()]
            for l in lines:
                print(l)
            if not lines or (r.returncode != 0 and not any(l.startswith('FAIL') for l in lines)):
                # NON-ZERO WITH NO FAILURE NAMED means the suite died part way through and the
                # reason is on stderr. Printing stdout alone leaves a run that simply stopped.
                print(r.stderr[-4000:])
            if r.returncode != 0:
                failed += 1
        return 1 if failed else 0
    finally:
        shutil.rmtree(work, ignore_errors=True)


if __name__ == '__main__':
    sys.exit(main())
