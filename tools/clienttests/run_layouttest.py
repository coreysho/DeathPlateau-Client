#!/usr/bin/env python3
"""Checks resizable mode's geometry: jagex2/client/Layout.java, compiled on its own (it is pure
arithmetic, no AWT) and driven by LayoutTest.java.

What it proves: the fixed layout maps every point of the 765x503 window to itself and draws the
viewport exactly where 377 did; in resizable mode every window point belongs to exactly one of
the viewport or a panel, panel points land inside that panel's rectangle of the fixed frame, the
panels sit in Old School's classic-layout corners, main interfaces are centred clear of them, an
open menu keeps the mouse past its area's edge, and the projection only ever grows.

    python3 tools/clienttests/run_layouttest.py
"""
import os
import shutil
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
LAYOUT = os.path.join(ROOT, 'src/main/java/jagex2/client/Layout.java')
TEST = os.path.join(HERE, 'LayoutTest.java')


def main():
    work = tempfile.mkdtemp(prefix='layouttest')
    try:
        r = subprocess.run([shutil.which('javac'), '-nowarn', '-encoding', 'UTF-8', '-d', work, LAYOUT, TEST],
                           capture_output=True, text=True)
        if r.returncode != 0:
            print(r.stderr[-4000:])
            raise SystemExit('run_layouttest: does not compile')
        r = subprocess.run([shutil.which('java'), '-cp', work, 'LayoutTest'], capture_output=True, text=True)
        print(r.stdout.rstrip())
        if r.returncode != 0 and 'FAIL' not in r.stdout:
            print(r.stderr[-4000:])
        return r.returncode
    finally:
        shutil.rmtree(work, ignore_errors=True)


if __name__ == '__main__':
    sys.exit(main())
