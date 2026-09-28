#!/usr/bin/env python3
"""Headless test for the Hide roofs toggle and the settings panel it lives in.

Same method as the other two harnesses: compile the whole client, slice getTopLevel() and the
panel's geometry out of Client.java, and drive them with the REAL QolSettings against the settings
file on disk - the setting's default and its persistence are the thing most likely to be wrong, and
a mock of the store tests neither.

getTopLevel() is the whole feature: it answers "what is the highest level to draw", and the roof is
whatever is above the player. The interesting cases are the ones the toggle has to survive - the
camera tilted steeply down, which skips the tile tests entirely, and the anticheat packet the method
sends on its own schedule, which must keep being sent.

    python3 tools/clienttests/run_rooftest.py
"""
import os
import re
import shutil
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
CLIENT = os.path.join(ROOT, 'src/main/java/jagex2/client/Client.java')
SETTINGS = os.path.join(ROOT, 'src/main/java/jagex2/client/QolSettings.java')
SHELL = os.path.join(HERE, 'RoofTest.shell.java')

DECLS = ['layout', 'QOL_PANEL_ROWS', 'QOL_PANEL_W', 'QOL_PANEL_ROW_H', 'QOL_PANEL_HEADER_H', 'QOL_PANEL_FOOTER_H',
         'currentLevel', 'levelTileFlags', 'cameraPitch', 'cameraX', 'cameraZ']
METHODS = ['getTopLevel', 'qolPanelHeight', 'qolPanelY']


def read(p):
    with open(p, encoding='utf-8', newline='') as f:
        return f.read().replace('\r\n', '\n')


def decl(src, name):
    m = re.search(r'(?m)^\t(?:private|public|protected)[^;\n{}]*\b%s\b[^;\n]*;$' % re.escape(name),
                  src)
    if not m:
        raise SystemExit('run_rooftest: no declaration found for %s in Client.java' % name)
    return m.group(0)


def method(src, name):
    m = re.search(r'(?m)^\t(?:private|public|protected|static)[^\n]*\b%s\(' % re.escape(name), src)
    if not m:
        raise SystemExit('run_rooftest: no method %s in Client.java' % name)
    i = src.index('{', m.start())
    depth = 0
    for j in range(i, len(src)):
        if src[j] == '{':
            depth += 1
        elif src[j] == '}':
            depth -= 1
            if depth == 0:
                return src[m.start():j + 1]
    raise SystemExit('run_rooftest: unbalanced braces in %s' % name)


def source_checks(src, settings):
    out = []
    top = method(src, 'getTopLevel')
    # The toggle has to sit at the single return, after the anticheat block and outside the pitch
    # test. Both of those are why, and both are invisible to a test that only reads return values.
    ret = top.rindex('return var2;')
    out.append(('the toggle is at the one return, after the ANTICHEAT_CYCLELOGIC1 block, so the '
                'packet that block sends keeps going out on its own schedule',
                'QolSettings.ROOFS_OFF' in top[:ret]
                and top.index('ANTICHEAT_CYCLELOGIC1') < top.index('QolSettings.ROOFS_OFF')))
    out.append(('...and outside the camera-pitch test, so a steeply tilted camera cannot put the '
                'roofs back',
                top.count('if (this.cameraPitch < 310) {') == 1
                and top.index('QolSettings.ROOFS_OFF') > top.rindex('levelTileFlags')))
    # The cutscene path is deliberately left alone.
    out.append(('the cutscene camera is left alone: getTopLevelCutscene() does not read the setting, '
                'because a scripted shot chose its own level',
                'ROOFS_OFF' not in method(src, 'getTopLevelCutscene')))
    # The default, which is the one place this setting breaks the file's own rule.
    m = re.search(r'private static final boolean\[\] DEFAULTS = \{(.*?)\};', settings, re.S)
    defaults = [x.strip() for x in m.group(1).replace('\n', '').split(',') if x.strip()]
    keys = re.search(r'private static final String\[\] KEYS = \{(.*?)\};', settings, re.S).group(1)
    keys = re.findall(r'"([^"]+)"', keys)
    labels = re.findall(r'"([^"]+)"',
                        re.search(r'private static final String\[\] LABELS = \{(.*?)\};',
                                  settings, re.S).group(1))
    out.append(('the three parallel arrays are the same length: %d keys, %d labels, %d defaults'
                % (len(keys), len(labels), len(defaults)),
                len(keys) == len(labels) == len(defaults)))
    idx = keys.index('roofs_off') if 'roofs_off' in keys else -1
    out.append(('roofs_off is the one setting that defaults OFF, because it changes how the world '
                'looks rather than adding a convenience',
                idx >= 0 and defaults[idx] == 'false'
                and all(d == 'true' for i, d in enumerate(defaults) if i != idx)))
    return out


def main():
    src = read(CLIENT)
    settings = read(SETTINGS)
    work = tempfile.mkdtemp(prefix='rooftest')
    try:
        classes = os.path.join(work, 'classes')
        os.makedirs(classes)
        sources = []
        # launcher/src too: the client jar compiles it in (Client.relaunchForUpdate uses it)
        for root, _dirs, files in [w for d in ('src/main/java', 'launcher/src')
                                   for w in os.walk(os.path.join(ROOT, d))]:
            sources += [os.path.join(root, f) for f in files if f.endswith('.java')]
        r = subprocess.run([shutil.which('javac'), '-nowarn', '-encoding', 'UTF-8', '-d', classes] + sources,
                           capture_output=True, text=True)
        if r.returncode != 0:
            print(r.stderr[-4000:])
            raise SystemExit('run_rooftest: the client does not compile')
        print('client compiles: %d sources' % len(sources))

        shell = read(SHELL)
        shell = shell.replace('// @@DECLS@@', '\n'.join(decl(src, n) for n in DECLS))
        shell = shell.replace('// @@METHODS@@', '\n\n'.join(method(src, n) for n in METHODS))
        assert '@@' not in shell, 'shell template still has a placeholder'
        out = os.path.join(work, 'RoofTest.java')
        with open(out, 'w', encoding='utf-8') as f:
            f.write(shell)
        r = subprocess.run([shutil.which('javac'), '-nowarn', '-encoding', 'UTF-8', '-cp', classes, '-d', work, out],
                           capture_output=True, text=True)
        if r.returncode != 0:
            print(r.stderr[-6000:])
            raise SystemExit('run_rooftest: the shell does not compile')
        r = subprocess.run([shutil.which('java'), '-cp', classes + os.pathsep + work, 'RoofTest'],
                           capture_output=True, text=True, cwd=work)
        lines = [l for l in r.stdout.split('\n') if l.strip()]
        for l in lines:
            print(l)
        if r.returncode != 0 and not lines:
            print(r.stderr[-4000:])
        fails = sum(1 for l in lines if l.startswith('FAIL'))
        print('4. where the toggle sits, and what it defaults to')
        for why, ok in source_checks(src, settings):
            print(('  ok   ' if ok else 'FAIL   ') + why)
            if not ok:
                fails += 1
        print()
        print('ALL PASS' if not fails and r.returncode == 0 else '%d FAILED' % max(fails, 1))
        return 1 if fails or r.returncode != 0 else 0
    finally:
        shutil.rmtree(work, ignore_errors=True)


if __name__ == '__main__':
    sys.exit(main())
