#!/usr/bin/env python3
"""Renders the login screen to build/preview, plain and with its hit tests overlaid.

Needs no display, no network and no cache. There is no type in the picture: PixFont is built
from the cache and no test here has one, so TitleScreen draws its art and skips its labels -
which is also what a player sees while the cache is still coming down.

The `guides` pass probes every shipping hit test per pixel and tints where it answers true, so a
control drawn in one place and clicked in another is visible rather than deduced.

    python3 tools/clienttests/run_titlepreview.py
"""
import os
import shutil
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
SRC = os.path.join(ROOT, 'src/main/java')
RES = os.path.join(ROOT, 'src/main/resources')
LAUNCHER_SRC = os.path.join(ROOT, 'launcher/src')
PREVIEW = os.path.join(HERE, 'TitlePreview.java')


def main():
    javac = shutil.which('javac')
    java = shutil.which('java')
    if not javac or not java:
        raise SystemExit('run_titlepreview: needs javac and java on PATH')

    work = tempfile.mkdtemp(prefix='titlepreview')
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
            raise SystemExit('run_titlepreview: the client does not compile')
        print('client compiles: %d sources' % len(sources))

        # Into `classes` so the preview shares the package and can reach what is package-private.
        r = subprocess.run([javac, '-nowarn', '-encoding', 'UTF-8', '-cp', classes,
                            '-d', classes, PREVIEW], capture_output=True, text=True)
        if r.returncode != 0:
            print(r.stderr[-6000:])
            raise SystemExit('run_titlepreview: the preview does not compile')

        out_dir = os.path.join(ROOT, 'build', 'preview')
        if not os.path.exists(out_dir):
            os.makedirs(out_dir)

        # RES on the classpath is what lets getResourceAsStream find the sigil and the wordmark.
        cp = os.pathsep.join([classes, RES])
        rc = 0
        for name, extra in (('title', []), ('title-guides', ['guides']),
                            ('title-hover', ['plain', '0'])):
            png = os.path.join(out_dir, name + '.png')
            args = [java, '-Djava.awt.headless=true', '-cp', cp,
                    'jagex2.client.TitlePreview', png] + extra
            r = subprocess.run(args, capture_output=True, text=True, cwd=work)
            if r.returncode != 0:
                print(r.stderr[-3000:])
                rc = 1
                continue
            print(r.stdout.strip())
        return rc
    finally:
        shutil.rmtree(work, ignore_errors=True)


if __name__ == '__main__':
    sys.exit(main())
