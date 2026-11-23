#!/usr/bin/env python3
"""Headless test for the text a plugin draws, against a REAL PixFont.

WHAT IT IS FOR. PixFont indexes its glyphs by the character into arrays of 256, so text from
outside printable ASCII throws inside the font - and a plugin cannot catch it: five frames later
PluginManager has turned the plugin off, and switching it back on draws the same string again.
The quest helper's step table carried a curly apostrophe in 32 rows across 16 quests, which is
what players meant by the helper "quitting and not being able to be turned back on".

WHY IT IS ITS OWN SUITE. Every other check of a plugin's drawing uses a recording font that
overrides the draw calls, so none of them touches those arrays. This one builds a real PixFont
through the protected constructor, fills in printable ASCII and nothing else, and lets the
client's own measuring and drawing run.

No display and no cache are needed: a font with no archive and a pixel buffer are the whole
fixture.

    python3 tools/clienttests/run_overlaytexttest.py
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
TEST = os.path.join(HERE, 'OverlayTextTest.java')


def main():
    javac = shutil.which('javac')
    java = shutil.which('java')
    if not javac or not java:
        raise SystemExit('run_overlaytexttest: needs javac and java on PATH')

    work = tempfile.mkdtemp(prefix='overlaytexttest')
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
            raise SystemExit('run_overlaytexttest: the client does not compile')
        print('client compiles: %d sources' % len(sources))

        r = subprocess.run([javac, '-nowarn', '-encoding', 'UTF-8', '-cp', classes, '-d', work,
                            TEST], capture_output=True, text=True)
        if r.returncode != 0:
            print(r.stderr[-6000:])
            raise SystemExit('run_overlaytexttest: the test does not compile')

        r = subprocess.run([java, '-cp', classes + os.pathsep + work,
                            'jagex2.client.plugin.OverlayTextTest'],
                           capture_output=True, text=True, cwd=work)
        lines = [l for l in r.stdout.split('\n') if l.strip()]
        for l in lines:
            print(l)
        if not lines:
            print(r.stderr[-4000:])
            return 1
        if r.returncode != 0 and not any(l.startswith('FAIL') for l in lines):
            print(r.stderr[-4000:])
        return 0 if r.returncode == 0 else 1
    finally:
        shutil.rmtree(work, ignore_errors=True)


if __name__ == '__main__':
    sys.exit(main())
