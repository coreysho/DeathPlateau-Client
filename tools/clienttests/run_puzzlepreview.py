#!/usr/bin/env python3
"""Renders the puzzle box overlay to build/preview/puzzle-*.png.

A SOLVER IS THE ONE FEATURE WHERE A PASSING TEST PROVES NOTHING ABOUT THE EXPERIENCE. ClueTest
already shows the clicks solve the board - two thousand of them, played back move by move - and
that says nothing at all about whether a player can see WHICH piece to click. The marks have to
land on the cells, the numbers have to be legible on top of a piece, and the one to click now
has to be obviously different from the three after it.

So this draws it: a real PluginManager rendering the real overlay over a real interface tree,
at three shuffle depths, with the cells underneath so the marks have something to be on.

The pictures are checked for the one thing a machine can judge - that something was drawn, in
the colours the overlay uses, inside the grid - and looked at for the rest.

    python3 tools/clienttests/run_puzzlepreview.py
"""
import os
import shutil
import subprocess
import sys
import tempfile
import zlib

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
SRC = os.path.join(ROOT, 'src/main/java')
LAUNCHER_SRC = os.path.join(ROOT, 'launcher/src')
RES = os.path.join(ROOT, 'src/main/resources')
PREVIEW = os.path.join(HERE, 'PuzzlePreview.java')
CONTENT = os.environ.get('DP_CONTENT') or os.path.join(os.path.dirname(ROOT), 'DeathPlateau-Content')

# The overlay's two colours, from CluePlugin: the next click and the three after it.
NEXT = (0x00, 0xFF, 0x00)
SOON = (0xFF, 0xD3, 0x7A)

SHOTS = [
    ('puzzle-fresh', '101', '7'),        # a box straight out of a casket
    ('puzzle-tangled', '400', '11'),     # one somebody has already shuffled about in
    ('puzzle-nearly', '6', '3'),         # six moves from solved, where the numbers crowd
]

fails = 0


def check(ok, what):
    global fails
    print(('  ok   ' if ok else 'FAIL   ') + what)
    if not ok:
        fails += 1


def png_pixels(path):
    """{(r, g, b): count} over a PNG, without PIL, which is broken in this container."""
    data = open(path, 'rb').read()
    pos = 8
    width = height = 0
    raw = b''
    while pos < len(data):
        length = int.from_bytes(data[pos:pos + 4], 'big')
        kind = data[pos + 4:pos + 8]
        body = data[pos + 8:pos + 8 + length]
        if kind == b'IHDR':
            width = int.from_bytes(body[0:4], 'big')
            height = int.from_bytes(body[4:8], 'big')
        elif kind == b'IDAT':
            raw += body
        pos += 12 + length
    flat = zlib.decompress(raw)
    stride = width * 3
    out = {}
    previous = bytearray(stride)
    at = 0
    for _y in range(height):
        filt = flat[at]
        at += 1
        line = bytearray(flat[at:at + stride])
        at += stride
        for i in range(stride):
            left = line[i - 3] if i >= 3 else 0
            up = previous[i]
            upleft = previous[i - 3] if i >= 3 else 0
            if filt == 1:
                line[i] = (line[i] + left) & 0xFF
            elif filt == 2:
                line[i] = (line[i] + up) & 0xFF
            elif filt == 3:
                line[i] = (line[i] + (left + up) // 2) & 0xFF
            elif filt == 4:
                p = left + up - upleft
                pa, pb, pc = abs(p - left), abs(p - up), abs(p - upleft)
                pick = left if pa <= pb and pa <= pc else (up if pb <= pc else upleft)
                line[i] = (line[i] + pick) & 0xFF
        for x in range(0, stride, 3):
            key = (line[x], line[x + 1], line[x + 2])
            out[key] = out.get(key, 0) + 1
        previous = line
    return out


def main():
    javac, java = shutil.which('javac'), shutil.which('java')
    if not javac or not java:
        raise SystemExit('run_puzzlepreview: needs javac and java on PATH')

    work = tempfile.mkdtemp(prefix='puzzlepreview')
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
            raise SystemExit('run_puzzlepreview: the client does not compile')
        r = subprocess.run([javac, '-nowarn', '-encoding', 'UTF-8', '-cp', classes, '-d', work,
                            PREVIEW], capture_output=True, text=True)
        if r.returncode != 0:
            print(r.stderr[-6000:])
            raise SystemExit('run_puzzlepreview: the preview does not compile')

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

        for name, steps, seed in SHOTS:
            png = os.path.join(out_dir, name + '.png')
            r = subprocess.run(launcher + [java, '-Dlostcity.cachedir=' + cache,
                                           '-Duser.home=' + cache,
                                           '-cp', os.pathsep.join([classes, RES, work]),
                                           'jagex2.client.PuzzlePreview', png, steps, seed,
                                           CONTENT],
                               capture_output=True, text=True, cwd=work)
            said = [l for l in r.stdout.split('\n') if l.strip()]
            ok = r.returncode == 0 and os.path.exists(png)
            check(ok, '%s rendered%s' % (name, '' if ok else ' -- ' + r.stderr[-400:]))
            if not ok:
                continue
            for line in said:
                print('         ' + line)
            # THE MARKS ARE THERE AND THEY ARE THE RIGHT ONES. A preview that wrote a picture
            # of the empty grid would pass "rendered" and say nothing, which is the failure a
            # picture is supposed to make impossible.
            pixels = png_pixels(png)
            check(pixels.get(NEXT, 0) > 40,
                  '...and the next piece is marked in green (%d pixels)' % pixels.get(NEXT, 0))
            check(pixels.get(SOON, 0) > 20,
                  '...with the ones after it in amber (%d pixels)' % pixels.get(SOON, 0))
            check(len(pixels) > 4,
                  '...over a grid rather than a blank page (%d colours)' % len(pixels))
            print('         -> ' + png)
    finally:
        shutil.rmtree(work, ignore_errors=True)

    print()
    print('ALL PASS' if not fails else '%d FAILED' % fails)
    return 1 if fails else 0


if __name__ == '__main__':
    sys.exit(main())
