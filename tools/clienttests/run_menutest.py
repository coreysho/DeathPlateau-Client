#!/usr/bin/env python3
"""Headless test for the right-click menu's scrolling, driven against the real client source.

Same method as run_groundtest.py: compile the whole client, slice the methods and their constants
out of Client.java, and drive them with real collaborators where there can be real ones - here that
is Pix2D, bound to an int[] the test reads back, so "the menu's scroll bar is drawn" is a pixel.

WHAT IS CHECKED IN SOURCE RATHER THAN RUN. The click that turns a menu position into an action lives
inside handleMouseInput(), which is hundreds of fields deep and cannot be lifted out. What made that
click wrong was that it carried its OWN COPY of the row arithmetic, so the draw and the click could
disagree about which row is where. Both go through menuRowY() and menuRowIndex() now, and the two
source checks below are what keep them there - the arithmetic itself is tested through the helpers.

    python3 tools/clienttests/run_menutest.py
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
SHELL = os.path.join(HERE, 'MenuTest.shell.java')

DECLS = [
    'MENU_ROW_H', 'MENU_CHROME_H', 'menuScroll', 'menuRowsShown', 'MENU_BAR_W',
    'MENU_BAR_TRACK', 'MENU_BAR_THUMB',
    'menuOption', 'menuSize', 'menuVisible', 'menuArea', 'menuX', 'menuY', 'menuWidth',
    'menuHeight', 'menuSwapMode', 'layout', 'CHAT_X', 'CHAT_Y', 'CHAT_W', 'CHAT_H', 'SIDE_X',
    'imageModIcons', 'menuColour',
]
METHODS = ['menuRowsFor', 'menuRowIndex', 'menuRowY', 'handleMenuScroll', 'drawMenu',
           'showContextMenu', 'fitMenuText']


def read(p):
    with open(p, encoding='utf-8', newline='') as f:
        return f.read().replace('\r\n', '\n')


def decl(src, name):
    m = re.search(r'(?m)^\t(?:private|public|protected)[^;\n{}]*\b%s\b[^;\n]*;$' % re.escape(name),
                  src)
    if not m:
        raise SystemExit('run_menutest: no declaration found for %s in Client.java' % name)
    return m.group(0)


def method(src, name):
    m = re.search(r'(?m)^\t(?:private|public|protected|static)[^\n]*\b%s\(' % re.escape(name), src)
    if not m:
        raise SystemExit('run_menutest: no method %s in Client.java' % name)
    i = src.index('{', m.start())
    depth = 0
    for j in range(i, len(src)):
        if src[j] == '{':
            depth += 1
        elif src[j] == '}':
            depth -= 1
            if depth == 0:
                return src[m.start():j + 1]
    raise SystemExit('run_menutest: unbalanced braces in %s' % name)


def source_checks(src):
    """The two claims about the click, which cannot be lifted out of handleMouseInput()."""
    out = []
    # the block that turns a click into a row: it is the one that calls useMenuOption
    i = src.index('int var10 = -1;')
    block = src[i:src.index('this.menuVisible = false;', i)]
    out.append(('the click that picks a row goes through menuRowY() and menuRowIndex(), so it '
                'cannot disagree with the draw about which row is where',
                'this.menuRowY(p)' in block and 'this.menuRowIndex(p)' in block))
    # ...and no copy of the old arithmetic survives anywhere in the file
    old = re.findall(r'\(this\.menuSize - 1 - \w+\) \* 15', src)
    out.append(('and no copy of the old (menuSize - 1 - i) * 15 arithmetic is left in the file: '
                '%d found' % len(old), not old))
    # The wheel order: a menu takes the wheel before an overlay that claimed the spot under the
    # cursor - which is what a tall ground-item pile is now - and both before the camera.
    order = [src.find('this.handleMenuScroll();'), src.find('this.plugins.onViewportScroll('),
             src.find('QolSettings.on(QolSettings.WHEEL_ZOOM)')]
    out.append(('an open menu is offered the wheel before an overlay that claimed the spot, and '
                'both before the camera zoom', all(x > 0 for x in order) and order == sorted(order)))
    # THE PER-FRAME CLEAR, which lives in the same unliftable method. A plugin's colour overrides
    # are stored by index, so one left behind colours whatever row lands at that index in the NEXT
    # menu - a "Hidden item" grey on an Attack. The clear has to sit in the rebuild itself, which
    # is the block that writes Cancel into index 0, rather than anywhere that could be skipped.
    # Anchored on the "menuSize = 1" that only the per-frame rebuild has: buildSwapMenu() also
    # writes Cancel into index 0, and it runs AFTER this clear in the same method, so matching it
    # would be checking the wrong block.
    rebuild = src.index('this.menuAction[0] = 1016;\n\t\tthis.menuSize = 1;')
    nearby = src[rebuild:rebuild + 700]
    out.append(('a plugin\'s colour overrides are cleared by the same rebuild that writes Cancel '
                'into index 0, so none can survive into the next menu',
                'this.menuColour[row] = 0;' in nearby))
    # ...over the WHOLE array. menuSize is 1 at that point - the rebuild has just written Cancel
    # and nothing else - so a clear bounded by it would wipe one row and leave the rest of last
    # frame's colours sitting in the array for the next menu to wear. Neither harness can run the
    # clear, because it is in handleMouseInput with the click, so the bound is read here.
    loop = re.search(r'for \(int row = 0; row < ([^;]+); row\+\+\) \{\s*'
                     r'this\.menuColour\[row\] = 0;', nearby)
    out.append(('...and the clear covers the whole array, not the one row the menu has at that '
                'point: bound is %s' % (loop.group(1) if loop else 'not found'),
                bool(loop) and loop.group(1) == 'this.menuColour.length'))
    # And nothing else in the file writes the array, or there would be a second owner of it: the
    # draw reads it, the rebuild clears it, and PluginContext.setMenuColour is the only writer.
    writes = re.findall(r'this\.menuColour\[[^\]]+\] = ', src)
    out.append(('and the rebuild is the only thing in Client.java that writes an override: '
                '%d write(s)' % len(writes), len(writes) == 1))
    return out


def main():
    src = read(CLIENT)
    work = tempfile.mkdtemp(prefix='menutest')
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
            raise SystemExit('run_menutest: the client does not compile')
        print('client compiles: %d sources' % len(sources))

        shell = read(SHELL)
        shell = shell.replace('// @@DECLS@@', '\n'.join(decl(src, n) for n in DECLS))
        shell = shell.replace('// @@METHODS@@', '\n\n'.join(method(src, n) for n in METHODS))
        assert '@@' not in shell, 'shell template still has a placeholder'
        out = os.path.join(work, 'MenuTest.java')
        with open(out, 'w', encoding='utf-8') as f:
            f.write(shell)
        r = subprocess.run([shutil.which('javac'), '-nowarn', '-encoding', 'UTF-8', '-cp', classes, '-d', work, out],
                           capture_output=True, text=True)
        if r.returncode != 0:
            print(r.stderr[-6000:])
            raise SystemExit('run_menutest: the shell does not compile')
        r = subprocess.run([shutil.which('java'), '-cp', classes + os.pathsep + work, 'MenuTest'],
                           capture_output=True, text=True, cwd=work)
        lines = [l for l in r.stdout.split('\n') if l.strip()]
        for l in lines:
            print(l)
        if r.returncode != 0 and not lines:
            print(r.stderr[-4000:])
        fails = sum(1 for l in lines if l.startswith('FAIL'))
        print('6. the click, checked in source')
        for why, ok in source_checks(src):
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
