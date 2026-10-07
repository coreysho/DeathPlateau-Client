#!/usr/bin/env python3
"""Renders the client window's own chrome to build/preview/frame*.png.

The title bar, the window border and the icon are drawn by the client rather than by the
desktop, which means they can be wrong in ways no other test would notice - and right in ways
nobody can confirm without looking. So this builds a real ViewBox under a virtual display, puts
the real sidebar in it, paints the whole window into an image, and checks the few things that
are facts rather than taste: that the frame really is undecorated, that the title bar is where
and what it should be, and that the icon loaded at every size.

    python3 tools/clienttests/run_framepreview.py
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
TEST = os.path.join(HERE, 'FramePreview.java')
CHROME = os.path.join(HERE, 'FrameChromeTest.java')
ICON_SIZES = (16, 24, 32, 48, 64, 128)


def main():
    javac = shutil.which('javac')
    java = shutil.which('java')
    if not javac or not java:
        raise SystemExit('run_framepreview: needs javac and java on PATH')

    fails = 0
    # The icons are resources, so a typo in a name is invisible to the compiler and shows up as
    # a window with the default Java cup on it.
    for size in ICON_SIZES:
        path = os.path.join(RES, 'deathplateau', 'icon-%d.png' % size)
        ok = os.path.exists(path) and os.path.getsize(path) > 0
        print(('  ok   ' if ok else 'FAIL   ') + 'icon-%d.png is there (%s)'
              % (size, '%d bytes' % os.path.getsize(path) if ok else 'missing'))
        fails += 0 if ok else 1

    work = tempfile.mkdtemp(prefix='framepreview')
    try:
        sources = []
        for root, _dirs, files in os.walk(SRC):
            sources += [os.path.join(root, f) for f in files if f.endswith('.java')]
        classes = os.path.join(work, 'classes')
        os.makedirs(classes)
        r = subprocess.run([javac, '-nowarn', '-encoding', 'UTF-8', '-d', classes] + sources,
                           capture_output=True, text=True)
        if r.returncode != 0:
            print(r.stderr[-4000:])
            raise SystemExit('run_framepreview: the client does not compile')
        r = subprocess.run([javac, '-nowarn', '-encoding', 'UTF-8', '-cp', classes, '-d', work,
                            TEST, CHROME], capture_output=True, text=True)
        if r.returncode != 0:
            print(r.stderr[-6000:])
            raise SystemExit('run_framepreview: the preview does not compile')

        out_dir = os.path.join(ROOT, 'build', 'preview')
        if not os.path.exists(out_dir):
            os.makedirs(out_dir)
        cache = os.path.join(work, 'cache')
        os.makedirs(cache)
        home = os.path.join(work, 'home')
        os.makedirs(home)

        launcher = []
        if not os.environ.get('DISPLAY'):
            xvfb = shutil.which('xvfb-run')
            if xvfb:
                launcher = [xvfb, '-a']

        # The behaviour first: a picture cannot show that an edge resizes or that the minimum
        # size still leaves the game room under a title bar the desktop no longer provides.
        r = subprocess.run(launcher + [java, '-Dlostcity.cachedir=' + cache, '-Duser.home=' + home,
                                       '-cp', os.pathsep.join([classes, RES, work]),
                                       'jagex2.client.FrameChromeTest'],
                           capture_output=True, text=True, cwd=work)
        said = [l for l in r.stdout.split('\n') if l.strip()]
        for line in said:
            print(line)
        if not said:
            print(r.stderr[-2000:])
        if r.returncode != 0:
            fails += max(1, sum(1 for l in said if l.startswith('FAIL')))
        print()

        for name, args in (('frame', []), ('frame-resizable', ['resizable'])):
            png = os.path.join(out_dir, name + '.png')
            r = subprocess.run(launcher + [java,
                                           '-Dlostcity.cachedir=' + cache,
                                           '-Duser.home=' + home,
                                           # The resources live beside the classes in the jar; here
                                           # they are a second classpath entry.
                                           '-cp', os.pathsep.join([classes, RES, work]),
                                           'jagex2.client.FramePreview', png] + args,
                               capture_output=True, text=True, cwd=work)
            said = r.stdout.strip().split('\n')[-1] if r.stdout.strip() else ''
            if r.returncode != 0 or not os.path.exists(png):
                print('FAIL   %s did not render' % name)
                print(r.stderr[-2000:])
                fails += 1
                continue
            undecorated = 'undecorated=true' in said
            print(('  ok   ' if undecorated else 'FAIL   ')
                  + '%s: the desktop\'s title bar is off, so ours is the only one (%s)'
                  % (name, said))
            fails += 0 if undecorated else 1
            print('  ok   %s -> %s' % (name, png))
    finally:
        shutil.rmtree(work, ignore_errors=True)

    print()
    print('ALL PASS' if not fails else '%d FAILED' % fails)
    return 1 if fails else 0


if __name__ == '__main__':
    sys.exit(main())
