#!/usr/bin/env python3
"""Headless test for the login screen's hit tests.

Every control on this screen is drawn by one set of constants and clicked through another
function, and when those disagree the screen still looks perfect - the button is in the right
place, it just does not answer. The title music toggle shipped nineteen pixels out that way.

Also checks the one ordering rule that is invisible from the drawing: the x that removes an
account sits inside the card that signs into it, so the caller must test it first.

Needs no display, no network and no cache.

    python3 tools/clienttests/run_titlescreentest.py
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
TEST = os.path.join(HERE, 'TitleScreenTest.java')


def main():
    javac = shutil.which('javac')
    java = shutil.which('java')
    if not javac or not java:
        raise SystemExit('run_titlescreentest: needs javac and java on PATH')

    work = tempfile.mkdtemp(prefix='titlescreentest')
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
            raise SystemExit('run_titlescreentest: the client does not compile')
        print('client compiles: %d sources' % len(sources))

        # Into `classes` so the test shares the package and can see LoginProfiles.MAX.
        r = subprocess.run([javac, '-nowarn', '-encoding', 'UTF-8', '-cp', classes,
                            '-d', classes, TEST], capture_output=True, text=True)
        if r.returncode != 0:
            print(r.stderr[-6000:])
            raise SystemExit('run_titlescreentest: the test does not compile')

        r = subprocess.run([java, '-Djava.awt.headless=true', '-cp', classes,
                            'jagex2.client.TitleScreenTest'],
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
