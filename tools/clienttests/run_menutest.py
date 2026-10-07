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
    'GI_BAR_TRACK', 'GI_BAR_THUMB',
    'menuOption', 'menuSize', 'menuVisible', 'menuArea', 'menuX', 'menuY', 'menuWidth',
    'menuHeight', 'menuSwapMode', 'layout', 'CHAT_X', 'CHAT_Y', 'CHAT_W', 'CHAT_H', 'SIDE_X',
    'imageModIcons',
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
    # the wheel order: a menu takes the wheel before a ground pile, which takes it before the camera
    order = [src.find('this.handleMenuScroll();'), src.find('this.handleGroundItemScroll();'),
             src.find('QolSettings.on(QolSettings.WHEEL_ZOOM)')]
    out.append(('an open menu is offered the wheel before a ground pile, and both before the '
                'camera zoom', all(x > 0 for x in order) and order == sorted(order)))
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
