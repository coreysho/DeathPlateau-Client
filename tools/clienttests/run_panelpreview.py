#!/usr/bin/env python3
"""Renders the client's in-game panels to build/preview/panel-*.png.

The F8 and F9 panels are the only interface the client draws itself, and they are drawn with the
game's 2D renderer and the game's fonts - so until now there was no way to look at them without
a cache and a running game. This builds a PixFont out of an AWT font, hands it to a real Client,
and calls the real draw methods, which makes the colours something that can be checked rather
than imagined.

It also asserts the thing that is a fact rather than taste: that neither panel carries a colour
literal any more. They are themed from jagex2.client.plugin.ui.Theme, the same palette as the
sidebar and the window frame, and a hardcoded colour is how one surface quietly drifts from the
other two.

    python3 tools/clienttests/run_panelpreview.py
"""
import os
import re
import shutil
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
SRC = os.path.join(ROOT, 'src/main/java')
LAUNCHER_SRC = os.path.join(ROOT, 'launcher/src')
RES = os.path.join(ROOT, 'src/main/resources')
TEST = os.path.join(HERE, 'PanelPreview.java')
SHOT = os.path.join(HERE, 'ScreenshotTest.java')
CLIENT = os.path.join(SRC, 'jagex2/client/Client.java')

fails = 0


def check(ok, what):
    global fails
    print(('  ok   ' if ok else 'FAIL   ') + what)
    if not ok:
        fails += 1


def method(src, name):
    m = re.search(r'(?m)^\tprivate void %s\(\) \{$' % re.escape(name), src)
    if not m:
        raise SystemExit('run_panelpreview: no %s in Client.java' % name)
    i = src.index('{', m.start())
    depth = 0
    for j in range(i, len(src)):
        if src[j] == '{':
            depth += 1
        elif src[j] == '}':
            depth -= 1
            if depth == 0:
                return src[m.start():j + 1]
    raise SystemExit('run_panelpreview: unbalanced braces in %s' % name)


def main():
    javac, java = shutil.which('javac'), shutil.which('java')
    if not javac or not java:
        raise SystemExit('run_panelpreview: needs javac and java on PATH')

    with open(CLIENT, encoding='utf-8') as f:
        src = f.read()
    for name in ('drawQolPanel', 'drawPluginPanel'):
        body = method(src, name)
        literals = sorted(set(re.findall(r'0x[0-9A-Fa-f]{6}', body)))
        check(not literals, '%s has no colour of its own: every one comes from Theme (%s)'
              % (name, ', '.join(literals) if literals else 'none left'))
        check('Theme.' in body, '...and it does read Theme' if not literals else '...')

    work = tempfile.mkdtemp(prefix='panelpreview')
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
            raise SystemExit('run_panelpreview: the client does not compile')
        r = subprocess.run([javac, '-nowarn', '-encoding', 'UTF-8', '-cp', classes, '-d', work,
                            TEST, SHOT], capture_output=True, text=True)
        if r.returncode != 0:
            print(r.stderr[-6000:])
            raise SystemExit('run_panelpreview: the preview does not compile')

        out_dir = os.path.join(ROOT, 'build', 'preview')
        if not os.path.exists(out_dir):
            os.makedirs(out_dir)
        cache = os.path.join(work, 'cache')
        os.makedirs(cache)
        launcher = []
        if not os.environ.get('DISPLAY'):
            xvfb = shutil.which('xvfb-run')
            if xvfb:
                launcher = [xvfb, '-a']

        # The camera button, which writes a file rather than a picture to look at.
        shot_home = os.path.join(work, 'shot-home')
        os.makedirs(shot_home)
        r = subprocess.run(launcher + [java, '-Dlostcity.cachedir=' + cache,
                                       '-cp', os.pathsep.join([classes, RES, work]),
                                       'jagex2.client.ScreenshotTest', shot_home],
                           capture_output=True, text=True, cwd=work)
        said = [l for l in r.stdout.split('\n') if l.strip()]
        for line in said:
            print(line)
        if not said:
            print(r.stderr[-1500:])
        if r.returncode != 0:
            globals()['fails'] = fails + max(1, sum(1 for l in said if l.startswith('FAIL')))
        print()

        for name, arg in (('panel-settings', 'qol'), ('panel-plugins', 'plugins')):
            png = os.path.join(out_dir, name + '.png')
            r = subprocess.run(launcher + [java, '-Dlostcity.cachedir=' + cache,
                                           '-Duser.home=' + cache,
                                           '-cp', os.pathsep.join([classes, RES, work]),
                                           'jagex2.client.PanelPreview', png, arg],
                               capture_output=True, text=True, cwd=work)
            ok = r.returncode == 0 and os.path.exists(png)
            check(ok, '%s rendered%s' % (name, '' if ok else ' -- ' + r.stderr[-400:]))
            if ok:
                print('         -> ' + png)
    finally:
        shutil.rmtree(work, ignore_errors=True)

    print()
    print('ALL PASS' if not fails else '%d FAILED' % fails)
    return 1 if fails else 0


if __name__ == '__main__':
    sys.exit(main())
