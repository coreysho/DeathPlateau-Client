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
ICONS = os.path.join(ROOT, 'src/main/java/jagex2/client/worldmap/MapIcons.java')
ORB = os.path.join(ROOT, 'src/main/java/jagex2/client/worldmap/MapOrb.java')
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

    # ---- THE ORB. It sits inside the minimap's own click rectangle, so every pixel it takes
    # is a pixel of walk-here taken from the player.
    (ORB, 'the orb grown until it covers the map a player is trying to read',
     '\tpublic static final int RADIUS = 11;',
     '\tpublic static final int RADIUS = 22;'),
    (ORB, 'the orb moved off the bottom of the panel, where half of it is not drawn',
     '\tpublic static final int CENTRE_Y = 142;',
     '\tpublic static final int CENTRE_Y = 150;'),
    (ORB, 'the orb moved onto the middle of the minimap',
     '\tpublic static final int CENTRE_X = 158;',
     '\tpublic static final int CENTRE_X = 98;'),
    (ORB, 'the click area made square, which takes the corner of the map with it',
     '\t\treturn dx * dx + dy * dy <= RADIUS * RADIUS;',
     '\t\treturn dx > -RADIUS && dx <= RADIUS && dy > -RADIUS && dy <= RADIUS;'),
    (ORB, 'the rim itself not counted as a click, so the edge of the button does nothing',
     '\t\treturn dx * dx + dy * dy <= RADIUS * RADIUS;',
     '\t\treturn dx * dx + dy * dy < RADIUS * RADIUS;'),
    (ORB, 'the orb drawn somewhere other than where its click is tested',
     '\t\t\tPix2D.hline(CENTRE_X - half, colour, CENTRE_Y + dy, half * 2 + 1);',
     '\t\t\tPix2D.hline(CENTRE_X - half, colour, CENTRE_Y + dy - 20, half * 2 + 1);'),
    (ORB, 'the orb drawn as a square, which puts corners on a round frame',
     '\t\t\tint half = (int) Math.sqrt(radius * radius - dy * dy);\n'
     '\t\t\tPix2D.hline(CENTRE_X - half, colour, CENTRE_Y + dy, half * 2 + 1);',
     '\t\t\tPix2D.hline(CENTRE_X - radius, colour, CENTRE_Y + dy, radius * 2 + 1);'),
    (ORB, 'the hover left looking exactly like the rest, so the button says nothing',
     '\t\tdisc(RADIUS - 1, hover ? STONE_LIT : STONE);',
     '\t\tdisc(RADIUS - 1, STONE);'),

    # ---- THE ICONS. Every one of these puts a bank somewhere there is not one.
    (ICONS, 'x and z swapped reading an icon off its tile, which crosses every river',
     "\t\t\t\tthis.icons.add(new Icon(sx * MapSquare.SQUARE + (at >> 6 & 0x3F),\n"
     "\t\t\t\t\tsz * MapSquare.SQUARE + (at & 0x3F), at >> 12, function[id]));",
     "\t\t\t\tthis.icons.add(new Icon(sx * MapSquare.SQUARE + (at & 0x3F),\n"
     "\t\t\t\t\tsz * MapSquare.SQUARE + (at >> 6 & 0x3F), at >> 12, function[id]));"),
    (ICONS, "the level read out of the tile's own bits, so every icon is on a floor above",
     "\t\t\t\t\tsz * MapSquare.SQUARE + (at & 0x3F), at >> 12, function[id]));",
     "\t\t\t\t\tsz * MapSquare.SQUARE + (at & 0x3F), at >> 6, function[id]));"),
    (ICONS, "the square's own base left off, so the whole world's icons pile into one square",
     "\t\t\t\tthis.icons.add(new Icon(sx * MapSquare.SQUARE + (at >> 6 & 0x3F),",
     "\t\t\t\tthis.icons.add(new Icon((at >> 6 & 0x3F),"),
    (ICONS, 'a square read twice counted twice, so the key says there are seventy-eight banks',
     '\t\tif (this.read.contains(square)) {\n\t\t\treturn;\n\t\t}\n',
     ''),
    (ICONS, 'the position not advanced, so every icon on a square lands on the same tile',
     '\t\t\t\tat += moved - 1;',
     '\t\t\t\tat = moved - 1;'),
    (ICONS, 'the level ignored when picking what to draw, so upstairs shows on the ground',
     '\t\t\tif (icon.level == level && icon.x >= x0 && icon.x <= x1',
     '\t\t\tif (icon.x >= x0 && icon.x <= x1'),
    (ICONS, 'the rectangle ignored, so every icon in the world is drawn over one view',
     '\t\t\tif (icon.level == level && icon.x >= x0 && icon.x <= x1\n'
     '\t\t\t\t\t&& icon.z >= z0 && icon.z <= z1) {',
     '\t\t\tif (icon.level == level) {'),
    (ICONS, 'the key counting every icon rather than its own kind',
     '\t\t\tif (((Icon) this.icons.get(i)).function == function) {',
     '\t\t\tif (((Icon) this.icons.get(i)).function >= 0) {'),
    (ICONS, 'search made case sensitive, so a lower-case word finds nothing',
     '\t\t\tif (icon.name().toLowerCase().indexOf(want) >= 0) {',
     '\t\t\tif (icon.name().indexOf(want) >= 0) {'),
    (ICONS, 'an empty search answering with everything there is',
     '\t\tif (want.length() == 0) {\n\t\t\treturn found;\n\t\t}\n',
     ''),
    (ICONS, 'search left in file order, so the nearest bank is whichever loaded first',
     '\t\tjava.util.Collections.sort(found, new java.util.Comparator() {',
     '\t\tif (false) java.util.Collections.sort(found, new java.util.Comparator() {'),
    (ICONS, 'distance added rather than taken as the larger, so a diagonal counts twice',
     '\t\treturn dx > dz ? dx : dz;',
     '\t\treturn dx + dz;'),
    (ICONS, "an icon's picture left opaque, so every round icon is a black square",
     '\t\t\t\tout.setRGB(x, y, rgb == 0 ? 0 : 0xFF000000 | rgb);',
     '\t\t\t\tout.setRGB(x, y, 0xFF000000 | rgb);'),
    (ICONS, 'the picture rebuilt on every repaint, which is a window full of garbage',
     '\t\tif (drawn[function] != null) {\n\t\t\treturn drawn[function];\n\t\t}\n',
     ''),

    (ICONS, 'the key counting one kind at a time again, sixty-four walks a refresh',
     '\t\t\tif (function >= 0 && function < kinds) {\n\t\t\t\tfound[function]++;\n\t\t\t}',
     '\t\t\tif (function >= 0 && function < kinds && function == 5) {\n'
     '\t\t\t\tfound[function]++;\n\t\t\t}'),

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

    (SUPPLY, "a closed map still wanting what it asked for, which grinds for as long as the "
             "client is up",
     '\t\tthis.wanted.clear();\n\t\tthis.waiting.clear();',
     ''),
    (SUPPLY, 'a loc file arriving without its icons being read, so the key stays empty',
     '\t\tif (data != null && file >= 0 && file < of.length && of[file] >= 0) {\n'
     '\t\t\tthis.icons.add(of[file] >> 8, of[file] & 0xFF, data);\n\t\t}\n',
     ''),
    (SUPPLY, 'the files already in hand never fed through once the loc table arrives',
     '\t\tif (!this.icons.refresh()) {\n\t\t\treturn;\n\t\t}\n',
     '\t\tif (true) {\n\t\t\treturn;\n\t\t}\n'),
    (SUPPLY, "the square's files looked up the other way round, land for scenery",
     '\t\treturn table[(((x << 8) + z) << 1) + type];',
     '\t\treturn table[(((x << 8) + z) << 1) + 1 - type];'),
    (SUPPLY, 'the lookup table built for the wrong squares, so nothing is ever found',
     '\t\t\t\ttable[(squares[i] << 1)] = this.supply.fileFor(x, z, 0);',
     '\t\t\t\ttable[(squares[i] << 1)] = this.supply.fileFor(z, x, 0);'),

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
    # NOT HERE: dropping the newest rather than the oldest. The queue is declared a Queue and
    # a Queue has no end but its head, so that one does not compile - the declaration is the
    # check, and a mutation the compiler refuses tests nothing.
    (SUPPLY, 'barely anything kept, so the window redraws its whole view every time it moves',
     '\tprivate static final int KEEP = 64;',
     '\tprivate static final int KEEP = 1;'),
    (SUPPLY, 'the square not asked for when it is missing, so nothing is ever drawn',
     '\t\tif (pixels == null) {\n\t\t\tthis.wanted.add(at);\n\t\t}',
     ''),
    (SUPPLY, 'only the square itself gathered, not the eight around it that light its edges',
     '\t\tfor (int dx = -1; dx <= 1; dx++) {\n\t\t\tfor (int dz = -1; dz <= 1; dz++) {\n'
     '\t\t\t\tint file = this.fileOf(sx + dx, sz + dz, type);',
     '\t\tfor (int dx = 0; dx <= 0; dx++) {\n\t\t\tfor (int dz = 0; dz <= 0; dz++) {\n'
     '\t\t\t\tint file = this.fileOf(sx + dx, sz + dz, type);'),
    (SUPPLY, 'the world marked again every time the map opens',
     '\t\tif (this.prefetched) {\n\t\t\treturn;\n\t\t}\n\t\tthis.prefetched = true;\n',
     ''),
    (SUPPLY, 'a square that is not in the world reported as being in it',
     '\t\treturn this.fileOf(x, z, 0) >= 0;',
     '\t\treturn true;'),
    # ---- THE TABLE THE ICONS ARE READ AGAINST, and when it is built. This is what made every
    # real player's map iconless: built from nothing before the loc configs loaded, then kept.
    (ICONS, 'the table built once and kept, which is the bug itself: the loc configs load after '
            'the map files do, so it was built from nothing and never named a loc again',
     '\t\tif (this.functionOf == null || this.builtFor != count) {',
     '\t\tif (this.functionOf == null) {'),
    (ICONS, 'a rebuilt table leaving the old reads in place, so nothing is read a second time',
     '\t\t\tthis.read.clear();\n',
     ''),
    (ICONS, 'a rebuild not announced, so the files already in hand are never fed through',
     '\t\tint before = this.builtFor;\n\t\tthis.functions();\n\t\treturn this.builtFor != before;',
     '\t\tthis.functions();\n\t\treturn false;'),
    (ICONS, 'the table reported rebuilt every frame, so every file is fed through forever',
     '\t\tint before = this.builtFor;\n\t\tthis.functions();\n\t\treturn this.builtFor != before;',
     '\t\tthis.functions();\n\t\treturn true;'),
]


def main():
    only = sys.argv[1] if len(sys.argv) > 1 else None
    orig = {}
    for path in (WINDOW, SUPPLY, ICONS, ORB):
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
