#!/usr/bin/env python3
"""Mutation test for tools/clienttests/run_mapwindowtest.py.

Breaks the world map window and its supply one plausible way at a time and checks
run_mapwindowtest notices - and notices by NAMING a check, not by crashing.

BOTH OF THESE FAIL IN SILENCE. The window's arithmetic has x growing east with the picture and
z growing north against it, so one measurement adds the centre and the other subtracts it: get
the pair wrong and the window shows a real map, correctly drawn, of somewhere the player is
not, with their marker sitting confidently in the middle of it. And the supply never throws at
all - a map that never fills in, one that fills in and forgets, one that asks for the same
missing ground every frame for as long as it is open all look exactly like a map.

    python3 tools/clienttests/mutate_mapwindow.py [filter]
"""
import os
import subprocess
import sys

# THE WORKING TREE IS NEVER WRITTEN. Mutations go into a throwaway copy of the repository - see
# mutate_guard.workspace - and its check() is the standalone pass that every pattern below still
# matches its source exactly once.
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from mutate_guard import workspace  # noqa: E402  (after the sys.path line, necessarily)

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
WINDOW = os.path.join(ROOT, 'src/main/java/jagex2/client/worldmap/MapWindow.java')
SUPPLY = os.path.join(ROOT, 'src/main/java/jagex2/client/worldmap/WorldMap.java')
RUNNER = os.path.join(HERE, 'run_mapwindowtest.py')

MUTS = [
    # ---- WHERE THINGS LAND. Each of these draws a map of somewhere else.
    (WINDOW, 'the centre added across rather than taken off, so the map runs the wrong way',
     '\t\treturn wide / 2 - centreX * zoom;',
     '\t\treturn wide / 2 + centreX * zoom;'),
    (WINDOW, 'the centre taken off downward too, which turns the world upside down',
     '\t\treturn high / 2 + centreZ * zoom;',
     '\t\treturn high / 2 - centreZ * zoom;'),
    (WINDOW, 'the zoom left out of the origin, so only the middle tile is ever right',
     '\t\treturn wide / 2 - centreX * zoom;',
     '\t\treturn wide / 2 - centreX;'),
    (WINDOW, 'the window not halved, so the centre sits in the corner',
     '\t\treturn wide / 2 - centreX * zoom;',
     '\t\treturn wide - centreX * zoom;'),
    (WINDOW, 'a tile placed by its bottom edge, so everything is a tile too low',
     '\t\treturn originZ - (z + 1) * step;',
     '\t\treturn originZ - z * step;'),
    (WINDOW, 'down measured downward, which mirrors the map north for south',
     '\t\treturn originZ - (z + 1) * step;',
     '\t\treturn originZ + (z + 1) * step;'),
    (WINDOW, 'across measured backwards, which mirrors it east for west',
     '\t\treturn originX + x * step;',
     '\t\treturn originX - x * step;'),

    # ---- PANNING.
    (WINDOW, 'the map dragged away from the cursor across',
     '\t\treturn fromX - byPixels / zoom;',
     '\t\treturn fromX + byPixels / zoom;'),
    (WINDOW, 'and down, so the map runs away upward',
     '\t\treturn fromZ + byPixels / zoom;',
     '\t\treturn fromZ - byPixels / zoom;'),
    (WINDOW, 'the zoom left out of the drag, so a zoomed-out map barely moves',
     '\t\treturn fromX - byPixels / zoom;',
     '\t\treturn fromX - byPixels;'),

    # ---- THE KEY. Three numbers in one, and two of them are the same size.
    (SUPPLY, 'x and z packed the same way round, so a square answers for its mirror',
     '\t\treturn (x << 16) + (z << 8) + level;',
     '\t\treturn (z << 16) + (x << 8) + level;'),
    (SUPPLY, 'the level left out of the key, so every floor is the ground floor',
     '\t\treturn (x << 16) + (z << 8) + level;',
     '\t\treturn (x << 16) + (z << 8);'),
    (SUPPLY, 'x read out of the key where z was put in',
     '\t\treturn key >> 16 & 0xFF;',
     '\t\treturn key >> 8 & 0xFF;'),

    # ---- THE SUPPLY. None of these throws; all of them leave a map that looks fine.
    (SUPPLY, 'a square drawn without waiting for the ground it stands on',
     '\t\t\tif (land == null || locs == null) {\n\t\t\t\tcontinue;\n\t\t\t}',
     '\t\t\tif (land == null && locs == null) {\n\t\t\t\tcontinue;\n\t\t\t}'),
    (SUPPLY, 'the same missing file asked for again on every frame, forever',
     '\t\tif (this.asked.add(file)) {\n\t\t\tthis.supply.request(file.intValue());\n\t\t}',
     '\t\tthis.supply.request(file.intValue());'),
    (SUPPLY, 'a square set aside after it is gathered, so an answer that came back inside the '
             'gather is lost and it waits forever',
     '\t\t\tthis.waiting.add(at);\n\t\t\tbyte[][][] land = this.gather(x, z, 0);',
     '\t\t\tbyte[][][] land = this.gather(x, z, 0);'),
    (SUPPLY, 'nothing woken when a file arrives, so a square waits for one already in hand',
     '\t\tthis.wanted.addAll(this.waiting);\n\t\tthis.waiting.clear();',
     ''),
    (SUPPLY, 'the drawn square not kept, so the map redraws everything every frame',
     '\t\tthis.drawn.put(at, pixels);\n',
     ''),
    (SUPPLY, 'nothing ever dropped, so a map panned across the world fills the heap',
     '\t\twhile (this.age.size() > KEEP) {\n\t\t\tthis.drawn.remove(this.age.poll());\n\t\t}',
     ''),
    (SUPPLY, 'the newest dropped rather than the oldest, so what is on screen goes first',
     '\t\t\tthis.drawn.remove(this.age.poll());',
     '\t\t\tthis.drawn.remove(this.age.peek());'),
    (SUPPLY, 'the square not asked for when it is missing, so nothing is ever drawn',
     '\t\tif (pixels == null) {\n\t\t\tthis.wanted.add(at);\n\t\t}',
     ''),
    (SUPPLY, 'only the square itself gathered, not the eight around it that light its edges',
     '\t\tfor (int dx = -1; dx <= 1; dx++) {\n\t\t\tfor (int dz = -1; dz <= 1; dz++) {\n'
     '\t\t\t\tint file = this.supply.fileFor(sx + dx, sz + dz, type);',
     '\t\tfor (int dx = 0; dx <= 0; dx++) {\n\t\t\tfor (int dz = 0; dz <= 0; dz++) {\n'
     '\t\t\t\tint file = this.supply.fileFor(sx + dx, sz + dz, type);'),
    (SUPPLY, 'the prefetch turned on again every time the map opens',
     '\t\tif (this.prefetched) {\n\t\t\treturn;\n\t\t}\n\t\tthis.prefetched = true;\n',
     ''),
    (SUPPLY, 'the prefetch never turned on, so the world stops at where the player has walked',
     '\t\tthis.supply.prefetchAll();',
     ''),
    (SUPPLY, 'a square that is not in the world reported as being in it',
     '\t\treturn this.supply.fileFor(x, z, 0) >= 0;',
     '\t\treturn true;'),
]


def main():
    only = sys.argv[1] if len(sys.argv) > 1 else None
    orig = {}
    for path in (WINDOW, SUPPLY):
        with open(path, encoding='utf-8', newline='') as f:
            orig[path] = f.read()
    _work, inside = workspace('mapwindow')
    muts = [m for m in MUTS if not only or only in m[1]]
    print('running %d of %d mutations' % (len(muts), len(MUTS)))
    bad = loose = 0
    for path, why, find, repl in muts:
        n = orig[path].count(find)
        if n != 1:
            print('  SKIP (pattern %s) %s'
                  % ('not found' if n == 0 else 'matches %d times' % n, why))
            bad += 1
            continue
        try:
            with open(inside(path), 'w', encoding='utf-8', newline='') as f:
                f.write(orig[path].replace(find, repl))
            r = subprocess.run([sys.executable, inside(RUNNER)], capture_output=True, text=True,
                               timeout=900)
        except subprocess.TimeoutExpired:
            print('  %-5s %-76s %s' % ('HUNG', why, 'the suite never finished - not a catch'))
            loose += 1
            continue
        finally:
            with open(inside(path), 'w', encoding='utf-8', newline='') as f:
                f.write(orig[path])
        fired = [l.strip()[5:].strip() for l in r.stdout.split('\n') if l.startswith('FAIL')]
        if r.returncode == 0:
            print('  %-5s %-76s %s' % ('GREEN', why, 'NOT CAUGHT'))
            bad += 1
        elif fired:
            print('  %-5s %-76s %s' % ('red', why, 'caught by: ' + fired[0][:44]))
        else:
            print('  %-5s %-76s %s' % ('red', why,
                  'caught, but by a non-zero exit with no check named - a crash is not a catch'))
            loose += 1
    print()
    if bad:
        print('%d MUTATIONS SURVIVED OR SKIPPED' % bad)
    elif loose:
        print('every mutation was caught, but %d only by a crash' % loose)
    else:
        print('every mutation was caught, each by a named check')
    return 1 if bad or loose else 0


if __name__ == '__main__':
    sys.exit(main())
