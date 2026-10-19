#!/usr/bin/env python3
"""Headless test for the saved logins behind the login screen's profile row.

This file holds people's passwords, so the checks that matter are the ones you cannot see by
looking at the login screen: the password never reaches the disk in readable text, a file that
will not decrypt costs the password and keeps the account, and nothing throws while the login
screen is drawing.

Needs no display and no network. Writes only into the system temp folder - it points user.home
at a scratch directory per case, so a developer's real ~/.deathplateau is never touched.

    python3 tools/clienttests/run_loginprofilestest.py
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
TEST = os.path.join(HERE, 'LoginProfilesTest.java')


def main():
    javac = shutil.which('javac')
    java = shutil.which('java')
    if not javac or not java:
        raise SystemExit('run_loginprofilestest: needs javac and java on PATH')

    work = tempfile.mkdtemp(prefix='loginprofiles')
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
            raise SystemExit('run_loginprofilestest: the client does not compile')
        print('client compiles: %d sources' % len(sources))

        # The test lives in jagex2.client so it can call the package-private reload() between
        # cases; without that every case would see the one before it.
        r = subprocess.run([javac, '-nowarn', '-encoding', 'UTF-8', '-cp', classes,
                            '-d', classes, TEST], capture_output=True, text=True)
        if r.returncode != 0:
            print(r.stderr[-6000:])
            raise SystemExit('run_loginprofilestest: the test does not compile')

        r = subprocess.run([java, '-cp', classes, 'jagex2.client.LoginProfilesTest'],
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
