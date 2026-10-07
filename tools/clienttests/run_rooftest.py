#!/usr/bin/env python3
"""Headless test for the Hide roofs toggle, the settings panel it lives in, and the swaps panel.

Same method as the other two harnesses: compile the whole client, slice getTopLevel() and the
panel's geometry out of Client.java, and drive them with the REAL QolSettings against the settings
file on disk - the setting's default and its persistence are the thing most likely to be wrong, and
a mock of the store tests neither.

getTopLevel() is the whole feature: it answers "what is the highest level to draw", and the roof is
whatever is above the player. The interesting cases are the ones the toggle has to survive - the
camera tilted steeply down, which skips the tile tests entirely, and the anticheat packet the method
sends on its own schedule, which must keep being sent.

The two panels are held to different rules, on purpose. The F9 settings panel has no paging, so it
MUST FIT the viewport and the checks fail the build the moment a twenty-first setting stops it
fitting. The F10 swaps panel scrolls, so its rule is that it must fit OR scroll - and then that it
is on screen at every list length in every display mode, and that everything it cannot show is
reachable with the bar. Neither rule lets a panel exist that a player cannot read the bottom of.

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
SWAPS = os.path.join(ROOT, 'src/main/java/jagex2/client/MenuSwaps.java')
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


def escape_branches(src):
    """Every `if (key == GameShell.KEY_ESCAPE...)` in the file, as (condition, body)."""
    out = []
    at = 0
    while True:
        i = src.find('if (key == GameShell.KEY_ESCAPE', at)
        if i < 0:
            return out
        open_brace = src.index('{', i)
        depth = 0
        for j in range(open_brace, len(src)):
            if src[j] == '{':
                depth += 1
            elif src[j] == '}':
                depth -= 1
                if depth == 0:
                    out.append((src[i:open_brace], src[open_brace + 1:j]))
                    at = j
                    break
        else:
            raise SystemExit('run_rooftest: unbalanced braces after an Escape branch')


def source_checks(src, settings, swaps):
    out = []
    # The swaps are a plugin now - the F10 panel is a page in the sidebar and the menu rows come
    # from SettingsMenuOpening - so what is checked here is that the client kept its half of that
    # bargain, and that the file underneath did not move.
    swapper = read(os.path.join(ROOT, 'src/main/java/jagex2/client/plugin/builtin/MenuSwapperPlugin.java'))
    out.append(('the swaps panel and the every-frame swap pass are gone from Client.java: both are '
                'the plugin\'s now',
                'drawSwapPanel' not in src and 'applyMenuSwap' not in src))
    out.append(('...and the plugin does both, through MenuBuilt and a ConfigList',
                'onMenuBuilt' in swapper and 'addConfigList' in swapper))
    build = method(src, 'buildSwapMenu')
    out.append(('the settings menu asks the plugins for their rows, and is no longer gated on any '
                'one feature being on',
                'onSettingsMenuOpening' in build
                and 'QolSettings.MENU_SWAPPER' not in method(src, 'showContextMenu')))
    out.append(('...and does not open at all when nothing offered a row, rather than showing a '
                'Choose Option with only Cancel in it',
                'if (size <= 1) {' in build))
    # The file the swaps live in did not change shape, which is the whole reason the cap could move.
    out.append(('MenuSwaps still stores the swaps in fixed arrays, so the cap bounds what a corrupt '
                'qol_swaps.dat can make the client allocate',
                'new String[MAX]' in swaps and 'count < MAX' in swaps))
    out.append(('...and the file format is untouched: FILE_VERSION stays 1, so the swaps a player already '
                'has load exactly as they did',
                'private static final int FILE_VERSION = 1;' in swaps))
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

    # Escape came back to the panel alongside the roofs toggle, and it is the other kind of
    # setting: a branch in the key loop rather than one in the renderer. Checked in source, like
    # the two above, because the key loop is 400 lines inside handleInputKey() and cannot be
    # lifted out - a source check that pins the two things that have ever been wrong here beats
    # no check at all.
    # Several branches test Escape - the two panels close on it too - so this is the one that
    # closes INTERFACES, found by what it does rather than by where it sits.
    closers = [(cond, body) for cond, body in escape_branches(src) if 'closeInterfaces()' in body]
    out.append(('exactly one Escape branch closes interfaces (%d)' % len(closers), len(closers) == 1))
    cond, body = closers[0] if len(closers) == 1 else ('', '')
    out.append(('...gated on its switch, so the panel can turn it off',
                'QolSettings.on(QolSettings.ESC_CLOSE)' in cond))
    out.append(('...and it does not consume the key, so anything else that wants Escape still gets it',
                'continue' not in body and 'break' not in body))
    # The default, which is the one place this setting breaks the file's own rule.
    m = re.search(r'private static final boolean\[\] DEFAULTS = \{(.*?)\};', settings, re.S)
    # Comments stripped first: this counts entries by splitting on commas, and a comma inside a
    # // comment in the array literal reads as another entry. That cost a confusing red build
    # once, reporting 15 defaults against 14 keys when all three arrays were the same length.
    body = re.sub(r'//[^\n]*', '', m.group(1))
    defaults = [x.strip() for x in body.replace('\n', '').split(',') if x.strip()]
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
    # Both of these were plugins for a while. Neither is one now, and neither may leave a built-in
    # behind claiming the same feature - two owners for one toggle is how a switch stops working
    # for reasons nobody can see from either side.
    manager = read(os.path.join(ROOT, 'src/main/java/jagex2/client/plugin/PluginManager.java'))
    out.append(('no built-in plugin is still shipped for either of them',
                'HideRoofsPlugin' not in manager and 'EscapeClosesPlugin' not in manager))
    builtin = os.path.join(ROOT, 'src/main/java/jagex2/client/plugin/builtin')
    out.append(('...and neither class is left in the tree for a classpath scan to find',
                not os.path.exists(os.path.join(builtin, 'HideRoofsPlugin.java'))
                and not os.path.exists(os.path.join(builtin, 'EscapeClosesPlugin.java'))))
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
        print('4. where the toggle sits, what it defaults to, and how the swaps panel scrolls')
        for why, ok in source_checks(src, settings, read(SWAPS)):
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
