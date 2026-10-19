#!/usr/bin/env python3
"""Headless test for the sidebar rail's icons, and a strip of them to look at.

Icons.named falls through to the plain list icon for a name it does not know, silently - and two
plugins that both fell through wore the SAME icon, so the rail carried three identical rows of
bars and the only way to tell Ground items from Skills was to click one.

So: every name a plugin asks for is one the set has, no two plugins ask for the same one, and no
two icons in the set DRAW the same thing - that last checked by rendering and comparing pixels,
because comparing names cannot see two names mapping to one picture.

The names are read out of the plugin sources, so the test cannot fall behind them.

    python3 tools/clienttests/run_iconstest.py
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
TEST = os.path.join(HERE, 'IconsTest.java')


def main():
    javac = shutil.which('javac')
    java = shutil.which('java')
    if not javac or not java:
        raise SystemExit('run_iconstest: needs javac and java on PATH')

    work = tempfile.mkdtemp(prefix='iconstest')
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
            raise SystemExit('run_iconstest: the client does not compile')
        print('client compiles: %d sources' % len(sources))

        r = subprocess.run([javac, '-nowarn', '-encoding', 'UTF-8', '-cp', classes,
                            '-d', classes, TEST], capture_output=True, text=True)
        if r.returncode != 0:
            print(r.stderr[-6000:])
            raise SystemExit('run_iconstest: the test does not compile')

        out_dir = os.path.join(ROOT, 'build', 'preview')
        if not os.path.exists(out_dir):
            os.makedirs(out_dir)
        sheet = os.path.join(out_dir, 'rail-icons.png')

        r = subprocess.run([java, '-Djava.awt.headless=true', '-Ddp.root=' + ROOT,
                            '-cp', classes, 'jagex2.client.plugin.ui.IconsTest', sheet],
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
