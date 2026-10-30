#!/usr/bin/env python3
"""Paints the world map window to png files, and checks they are not blank.

Everything else about the map is checked as arithmetic - which pixel a wall lights up, which
square goes where, which way a drag moves the centre. None of that says whether the window is
any good: whether the squares line up at the zoom they are scaled to, whether the you-are-here
marker lands on the player, whether the thing reads as a map at all. Those you look at.

It drives the real window over a packed cache rather than a server, through the Supply the map
takes for exactly that reason, and pumps the game thread's share of the work by hand.

It also fails if a picture comes out blank or near enough, which is what a window that asked
for squares and never got them looks like - that is the regression worth catching without a
human; the rest is for a human to look at.

    python3 tools/clienttests/run_mapwindowpreview.py [output-dir] [path/to/data/pack]

Needs a display. Uses xvfb-run when there is no DISPLAY set, and skips (exit 0) when there is
neither, so it does not fail a headless machine that simply cannot run it. Needs the cache the
engine packs, as run_worldmaptest does, and takes DP_PACK the same way.
"""
import os
import shutil
import struct
import subprocess
import sys
import tempfile
import zlib

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
SRC = os.path.join(ROOT, 'src/main/java')
LAUNCHER_SRC = os.path.join(ROOT, 'launcher/src')
RENDER = os.path.join(HERE, 'WorldMapRender.java')
PREVIEW = os.path.join(HERE, 'MapWindowPreview.java')

GUESSES = [
    os.environ.get('DP_PACK'),
    os.path.join(os.path.dirname(ROOT), 'DeathPlateau-Engine/data/pack'),
]

# How many colours each ought to have at the least. AN UPPER FLOOR IS MOSTLY BLACK and that is
# correct - it is a floor, not another copy of the ground - so it cannot be held to the same
# line as a view of a town, and holding it to one would be asking the map to be wrong. A window
# that drew nothing at all has the background and the legend, which is four; the first floor
# over Lumbridge has sixteen, so ten is the line between them with room either side.
SHOTS = [('lumbridge', 200), ('varrock', 200), ('upstairs', 10)]


def find_pack(argv):
    for where in list(argv) + GUESSES:
        if where and os.path.exists(os.path.join(where, 'main_file_cache.idx4')):
            return os.path.abspath(where)
    return None


def colours(path):
    """How many distinct colours a png has, straight out of its IDAT.

    A WINDOW THAT DREW NOTHING is one colour; one that drew the background and the legend is
    three or four. Anything that is actually a map is hundreds, so the line between them is
    not a fine judgement and does not need a png library to find.
    """
    with open(path, 'rb') as f:
        data = f.read()
    pos, wide, high, raw = 8, 0, 0, b''
    while pos < len(data):
        length = struct.unpack('>I', data[pos:pos + 4])[0]
        kind = data[pos + 4:pos + 8]
        body = data[pos + 8:pos + 8 + length]
        if kind == b'IHDR':
            wide, high = struct.unpack('>II', body[:8])
        elif kind == b'IDAT':
            raw += body
        pos += 12 + length
    flat = zlib.decompress(raw)
    stride = wide * 3 + 1
    seen = set()
    # Every other row and pixel: enough to tell a map from a blank, a quarter of the work.
    for y in range(0, high, 2):
        row = flat[y * stride + 1:(y + 1) * stride]
        for x in range(0, wide, 2):
            seen.add(row[x * 3:x * 3 + 3])
    return len(seen)


def main():
    out = os.path.abspath(sys.argv[1]) if len(sys.argv) > 1 else os.path.join(ROOT, 'build/mapwindow')
    pack = find_pack(sys.argv[2:])
    if not pack:
        raise SystemExit('run_mapwindowpreview: no cache found. Pack the engine, pass its '
                         'data/pack, or set DP_PACK.')
    javac = shutil.which('javac')
    java = shutil.which('java')
    if not javac or not java:
        raise SystemExit('run_mapwindowpreview: needs javac and java on PATH')
    if not os.environ.get('DISPLAY') and not shutil.which('xvfb-run'):
        print('run_mapwindowpreview: no display and no xvfb-run, skipping')
        return 0

    work = tempfile.mkdtemp(prefix='mapwindow')
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
            raise SystemExit('run_mapwindowpreview: the client does not compile')
        r = subprocess.run([javac, '-nowarn', '-encoding', 'UTF-8', '-cp', classes,
                            '-d', classes, RENDER, PREVIEW], capture_output=True, text=True)
        if r.returncode != 0:
            print(r.stderr[-6000:])
            raise SystemExit('run_mapwindowpreview: the preview does not compile')

        cmd = [java, '-Xmx2g', '-cp', classes, 'jagex2.client.MapWindowPreview', pack, out]
        if not os.environ.get('DISPLAY'):
            cmd = ['xvfb-run', '-a'] + cmd
        r = subprocess.run(cmd, capture_output=True, text=True, cwd=work)
        for line in r.stdout.split('\n'):
            if line.strip():
                print(line)
        if r.returncode != 0:
            print(r.stderr[-4000:])
            return 1

        bad = 0
        for name, least in SHOTS:
            path = os.path.join(out, name + '.png')
            if not os.path.exists(path):
                print('FAIL %s was not written' % name)
                bad = 1
                continue
            n = colours(path)
            if n < least:
                print('FAIL %s is blank or near it: %d colours, wanted %d'
                      % (name, n, least))
                bad = 1
            else:
                print('ok   %s: %d colours' % (name, n))
        return bad
    finally:
        shutil.rmtree(work, ignore_errors=True)


if __name__ == '__main__':
    sys.exit(main())
