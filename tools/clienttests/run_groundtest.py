#!/usr/bin/env python3
"""Headless test for the ground-item overlay, driven against the REAL client source.

WHY IT EXTRACTS RATHER THAN REIMPLEMENTS. drawGroundItems() is 150 lines of layout arithmetic
inside a 14,000-line class that cannot be constructed without a cache, a window and a server. So
this pulls the methods and their constants OUT of Client.java by source slicing and drops them into
a shell that supplies the handful of collaborators they touch - and everything that can be real IS
real:

    real  jagex2.datastruct.LinkList, jagex2.dash3d.ClientObj, jagex2.dash3d.ClientPlayer
    real  jagex2.client.GroundItemPrefs and QolSettings, against the settings file on disk
    real  jagex2.config.ObjType, through ObjType.get() with its 10-slot cache primed by hand
    real  jagex2.graphics.Pix2D, bound to an int[] the test reads back - so "the scroll bar is
          drawn" is a pixel, not a code path
    stub  the font, because recording (x, y, colour, text) per row IS the measurement

The constants come across rather than being retyped, so a change to GI_ROWS_SHOWN or
GROUND_ITEM_ROW_H cannot pass a test that asserts the old one.

    python3 tools/clienttests/run_groundtest.py
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
SHELL = os.path.join(HERE, 'GroundItemsTest.shell.java')

# Declarations the extracted methods read. One line each in Client.java; pulled by name so a
# renamed field is a loud failure here rather than a silent divergence.
DECLS = [
    'layout', 'GROUND_ITEM_ROW_H', 'GROUND_ITEM_HEIGHT', 'GROUND_ITEM_MAX_PER_TILE',
    'GI_ROWS_SHOWN', 'GROUND_ITEM_MAX_LABELS', 'GROUND_ITEM_TIERS', 'GROUND_ITEM_TIER_COLOURS',
    'GROUND_ITEM_COLOUR', 'GROUND_ITEM_HIGHLIGHT', 'GROUND_ITEM_HIDDEN', 'GI_CONTROL_W',
    'GI_MINUS_COLOUR', 'GI_PLUS_COLOUR', 'groundItemIds', 'groundItemCounts',
    'ALT_DOUBLE_TAP_MS', 'altDown', 'altLastPress', 'giZoneCount', 'giZoneName', 'giZoneTop',
    'giZoneBottom', 'giZoneMinusX', 'giZonePlusX', 'giZoneNameX', 'giZoneNameEndX',
    'GI_ROW_SKIP', 'giRowColour', 'GI_MAX_PILES', 'giPileCount', 'giPileTileX', 'giPileTileZ',
    'giPileLeft', 'giPileRight', 'giPileTop', 'giPileBottom', 'giPileRows',
    'giScrollLevel', 'giScrollTileX', 'giScrollTileZ', 'giScrollOffset',
    'GI_BAR_W', 'GI_BAR_GAP', 'GI_BAR_TRACK', 'GI_BAR_THUMB',
]

METHODS = ['updateAltState', 'handleGroundItemClick', 'drawGroundItems', 'handleGroundItemScroll']


def read(p):
    with open(p, encoding='utf-8', newline='') as f:
        return f.read().replace('\r\n', '\n')


def decl(src, name):
    """The single declaration line for a field or constant, as written."""
    m = re.search(r'(?m)^\t(?:private|public|protected)[^;\n{}]*\b%s\b[^;\n]*;$' % re.escape(name),
                  src)
    if not m:
        raise SystemExit('run_groundtest: no declaration found for %s in Client.java' % name)
    return m.group(0)


def method(src, name):
    """A whole method, from its signature to the brace that closes it."""
    m = re.search(r'(?m)^\t(?:private|public|protected|static)[^\n]*\b%s\(' % re.escape(name), src)
    if not m:
        raise SystemExit('run_groundtest: no method %s in Client.java' % name)
    i = src.index('{', m.start())
    depth = 0
    for j in range(i, len(src)):
        if src[j] == '{':
            depth += 1
        elif src[j] == '}':
            depth -= 1
            if depth == 0:
                return src[m.start():j + 1]
    raise SystemExit('run_groundtest: unbalanced braces in %s' % name)


def main():
    src = read(CLIENT)
    work = tempfile.mkdtemp(prefix='groundtest')
    try:
        classes = os.path.join(work, 'classes')
        os.makedirs(classes)
        sources = []
        # launcher/src too: the client jar compiles it in (Client.relaunchForUpdate uses it)
        for root, _dirs, files in [w for d in ('src/main/java', 'launcher/src')
                                   for w in os.walk(os.path.join(ROOT, d))]:
            sources += [os.path.join(root, f) for f in files if f.endswith('.java')]
        # The whole client, not a subset: the extracted code calls into it and the point of this
        # harness is that those calls are the real ones.
        r = subprocess.run([shutil.which('javac'), '-nowarn', '-encoding', 'UTF-8', '-d', classes] + sources,
                           capture_output=True, text=True)
        if r.returncode != 0:
            print(r.stderr[-4000:])
            raise SystemExit('run_groundtest: the client does not compile')
        print('client compiles: %d sources' % len(sources))

        shell = read(SHELL)
        shell = shell.replace('// @@DECLS@@',
                              '\n'.join(decl(src, n) for n in DECLS))
        shell = shell.replace('// @@METHODS@@',
                              '\n\n'.join(method(src, n) for n in METHODS))
        assert '@@' not in shell, 'shell template still has a placeholder'
        out = os.path.join(work, 'GroundItemsTest.java')
        with open(out, 'w', encoding='utf-8') as f:
            f.write(shell)
        r = subprocess.run([shutil.which('javac'), '-nowarn', '-encoding', 'UTF-8', '-cp', classes, '-d', work, out],
                           capture_output=True, text=True)
        if r.returncode != 0:
            print(r.stderr[-6000:])
            raise SystemExit('run_groundtest: the shell does not compile')
        r = subprocess.run([shutil.which('java'), '-cp', classes + os.pathsep + work,
                            'GroundItemsTest'], capture_output=True, text=True, cwd=work)
        out_lines = [l for l in r.stdout.split('\n') if l.strip()]
        for l in out_lines:
            print(l)
        if r.returncode != 0:
            print(r.stderr[-4000:])
        fails = sum(1 for l in out_lines if l.startswith('FAIL'))
        return 1 if fails or r.returncode != 0 else 0
    finally:
        shutil.rmtree(work, ignore_errors=True)


if __name__ == '__main__':
    sys.exit(main())
