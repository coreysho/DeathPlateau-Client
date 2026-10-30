#!/usr/bin/env python3
"""Mutation test for tools/clienttests/run_worldmaptest.py.

Breaks the world map one plausible way at a time and checks run_worldmaptest notices - and
notices by NAMING a check, not by crashing.

EVERY ONE OF THESE STILL DRAWS A MAP. That is the whole problem with a renderer: north and south
swapped, x and z packed the wrong way round, the neighbours left out, the light left to wander -
each of them produces a picture of a world, in the right colours, with coastlines and roads and
buildings, and the only way to know it is the wrong world is to have checked.

    python3 tools/clienttests/mutate_worldmap.py [filter]
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
RENDER = os.path.join(HERE, 'WorldMapRender.java')
RUNNER = os.path.join(HERE, 'run_worldmaptest.py')

def find_pack():
    for where in (os.environ.get('DP_PACK'),
                  os.path.join(os.path.dirname(ROOT), 'DeathPlateau-Engine/data/pack')):
        if where and os.path.exists(os.path.join(where, 'main_file_cache.idx4')):
            return os.path.abspath(where)
    return None


MUTS = [
    # ---- WHICH WAY IS UP. A mirrored map is still a map.
    (RENDER, 'the north-up flip dropped, so the world is drawn upside down',
     '\t\treturn (SQUARE - 1 - tz) * SCALE * side;',
     '\t\treturn tz * SCALE * side;'),
    (RENDER, 'the flip off by one, so the southernmost tile is drawn past the bottom edge',
     '\t\treturn (SQUARE - 1 - tz) * SCALE * side;',
     '\t\treturn (SQUARE - tz) * SCALE * side;'),
    (RENDER, 'squares laid out south-up while their tiles stay north-up',
     '\t\treturn (maxZ - z) * cell;',
     '\t\treturn (z - maxZ) * cell;'),
    (RENDER, 'squares measured from the north edge rather than placed against it',
     '\t\treturn (maxZ - z) * cell;',
     '\t\treturn (maxZ - z - 1) * cell;'),

    # ---- WHICH SQUARE. The index packs x above z, and the two look alike.
    (RENDER, 'x and z swapped in the index key, which transposes the world',
     '\t\tint index = (x << 8) + z;',
     '\t\tint index = (z << 8) + x;'),
    (RENDER, 'the key shifted 16 rather than 8, so no square is ever found',
     '\t\tint index = (x << 8) + z;',
     '\t\tint index = (x << 16) + z;'),
    (RENDER, 'the loc file returned where the land file was asked for',
     '\t\t\t\treturn type == 0 ? mapLand[i] : mapLoc[i];',
     '\t\t\t\treturn type == 0 ? mapLoc[i] : mapLand[i];'),
    (RENDER, 'z read out of the index the way x is, so every square reads as a northern one',
     '\t\t\tint z = mapIndex[i] & 0xFF;\n\t\t\tminX = Math.min(minX, x);',
     '\t\t\tint z = mapIndex[i] >> 8;\n\t\t\tminX = Math.min(minX, x);'),

    # ---- THE SQUARE IN ITS SCENE.
    (RENDER, 'the neighbours left out, so every square is lit wrong round its edges',
     '\t\tfor (int dx = -1; dx <= 1; dx++) {\n\t\t\tfor (int dz = -1; dz <= 1; dz++) {\n'
     '\t\t\t\tbyte[] land = fetch(fileFor(sx + dx, sz + dz, 0));',
     '\t\tfor (int dx = 0; dx <= 0; dx++) {\n\t\t\tfor (int dz = 0; dz <= 0; dz++) {\n'
     '\t\t\t\tbyte[] land = fetch(fileFor(sx + dx, sz + dz, 0));'),
    # NOT HERE: the scene base. It is passed to method22 correctly and the renderer would be
    # lying about what it means if it were not, but all it reaches is the noise the ground
    # heights fall back on, and a minimap tile's colour never reads a height - so a base an
    # entire margin out renders, provably, the same picture to the pixel. An equivalent mutant
    # is not a gap in the checks; it is a mutation with nothing to catch.
    (RENDER, 'x and z swapped going into the land decode, which transposes every square',
     '\t\t\t\t\tworld.method22(offZ, baseZ, offX, land, baseX, collision);',
     '\t\t\t\t\tworld.method22(offX, baseX, offZ, land, baseZ, collision);'),
    (RENDER, 'x and z swapped reading the tile back out',
     '\t\t\t\t\tscene.method309(pixels, at, side, level, x, z);',
     '\t\t\t\t\tscene.method309(pixels, at, side, level, z, x);'),
    (RENDER, 'the margin not taken off the tile read, so a square shows its neighbour',
     '\t\t\t\tint x = margin + tx;\n\t\t\t\tint z = margin + tz;',
     '\t\t\t\tint x = tx;\n\t\t\t\tint z = tz;'),

    # ---- THE LIGHT. It wanders on its own unless it is pinned past the clamp.
    (RENDER, 'the light left in the middle of its range, where the nudge still moves it',
     '\t\tWorld.field104 = 10;\n\t\tWorld.field126 = 20;',
     '\t\tWorld.field104 = 0;\n\t\tWorld.field126 = 0;'),
    (RENDER, 'the light pinned at the clamp itself, which the nudge can still fall short of',
     '\t\tWorld.field104 = 10;\n\t\tWorld.field126 = 20;',
     '\t\tWorld.field104 = 8;\n\t\tWorld.field126 = 16;'),
    (RENDER, 'the light left where the last square put it, so the map drifts end to end',
     '\t\tWorld.field104 = 10;\n\t\tWorld.field126 = 20;',
     ''),

    # ---- WHICH LEVEL'S TILE. A bridge is built above the water and marked as belonging below.
    (RENDER, 'off-minimap tiles drawn anyway, which the client never shows at any level',
     '\tstatic final int ELSEWHERE = 0x18;',
     '\tstatic final int ELSEWHERE = 0x8;'),
    (RENDER, 'a tile drawn on its own level as well as the one it belongs to',
     '\tstatic final int ELSEWHERE = 0x18;',
     '\tstatic final int ELSEWHERE = 0x10;'),
    (RENDER, 'the belongs-below bit read as the off-minimap one, so no bridge comes down',
     '\tstatic final int BELOW = 0x8;',
     '\tstatic final int BELOW = 0x10;'),
    (RENDER, 'this level asked whether it belongs below instead of the one above it',
     '\t\treturn level < 3 && (flags[level + 1][x][z] & BELOW) != 0;',
     '\t\treturn level < 3 && (flags[level][x][z] & BELOW) != 0;'),
    (RENDER, 'a level looked for above the top one, which there is not one of',
     '\t\treturn level < 3 && (flags[level + 1][x][z] & BELOW) != 0;',
     '\t\treturn level < 4 && (flags[level + 1][x][z] & BELOW) != 0;'),
    (RENDER, 'the span above not brought down, so a tile that belongs down here is left blank',
     '\t\t\t\tif (fromAbove(flags, level, x, z)) {\n'
     '\t\t\t\t\tscene.method309(pixels, at, side, level + 1, x, z);\n\t\t\t\t}\n',
     ''),
    (RENDER, "the level's own tiles all drawn, bridge tiles included, under the span",
     '\t\t\t\tif (onThisMap(flags, level, x, z)) {\n'
     '\t\t\t\t\tscene.method309(pixels, at, side, level, x, z);\n\t\t\t\t}',
     '\t\t\t\tscene.method309(pixels, at, side, level, x, z);'),
    (RENDER, "the span's railings left off, so what comes down arrives without what stands on it",
     '\t\t\t\tif (fromAbove(flags, level, x, z)) {\n'
     '\t\t\t\t\tlocsOn(scene, pixels, side, level + 1, x, z, margin);\n\t\t\t\t}\n',
     ''),
    (RENDER, 'scenery drawn on tiles the map hides, which is scenery from a level nobody is on',
     '\t\t\t\tif (onThisMap(flags, level, x, z)) {\n'
     '\t\t\t\t\tlocsOn(scene, pixels, side, level, x, z, margin);\n\t\t\t\t}',
     '\t\t\t\tlocsOn(scene, pixels, side, level, x, z, margin);'),
    (RENDER, 'only the ground written out, so a map has no upstairs to show',
     '\t\tfor (int level = 0; level < 4; level++) {',
     '\t\tfor (int level = 0; level < 1; level++) {'),
    (RENDER, 'a file written for every empty level, three quarters of them black',
     '\t\t\t\tif (isEmpty(one)) {\n\t\t\t\t\tempty++;\n\t\t\t\t\tcontinue;\n\t\t\t\t}\n',
     ''),
    (RENDER, 'a square called empty the moment it finds anything drawn on it',
     '\t\t\t\tif ((img.getRGB(x, y) & 0xFFFFFF) != 0) {\n\t\t\t\t\treturn false;\n\t\t\t\t}',
     '\t\t\t\tif ((img.getRGB(x, y) & 0xFFFFFF) == 0) {\n\t\t\t\t\treturn false;\n\t\t\t\t}'),
    (RENDER, 'the level left out of the tile name, so four levels overwrite each other',
     '\t\t\t\tImageIO.write(one, "png", new File(dir, x + "_" + z + "_" + level + ".png"));',
     '\t\t\t\tImageIO.write(one, "png", new File(dir, x + "_" + z + ".png"));'),

    # ---- THE OVERVIEW.
    # ---- THE SCENERY. Every one of these still draws walls, just the wrong ones.
    (RENDER, 'the models left compressed, so no loc has a model and none reaches the scene',
     '\t\t\tbyte[] data = inflate(models.read(file));',
     '\t\t\tbyte[] data = models.read(file);'),
    (RENDER, 'the models left unloaded by the command line, so only this test ever sees them',
     '\t\tloadModels(pack);\n',
     ''),
    (RENDER, "the neighbours' scenery left out, so a building over the line is drawn half",
     '\t\t\t\tbyte[] locs = fetch(fileFor(sx + dx, sz + dz, 1));',
     '\t\t\t\tbyte[] locs = dx == 0 && dz == 0 ? fetch(fileFor(sx, sz, 1)) : null;'),
    (RENDER, "the scenery all stacked at the scene origin instead of at each square's offset",
     '\t\t\t\t\tworld.method27(margin + dz * SQUARE, collision, margin + dx * SQUARE,\n'
     '\t\t\t\t\t\tscene, locs);',
     '\t\t\t\t\tworld.method27(margin, collision, margin, scene, locs);'),
    (RENDER, 'x and z swapped placing the scenery, so the buildings sit across the ground',
     '\t\t\t\t\tworld.method27(margin + dz * SQUARE, collision, margin + dx * SQUARE,\n'
     '\t\t\t\t\t\tscene, locs);',
     '\t\t\t\t\tworld.method27(margin + dx * SQUARE, collision, margin + dz * SQUARE,\n'
     '\t\t\t\t\t\tscene, locs);'),
    (RENDER, 'doors and walls the same colour, so nothing marks a way in',
     '\t\t\t\twallFrom(pixels, side, px, pz, info, wall > 0 ? DOOR : WALL);',
     '\t\t\t\twallFrom(pixels, side, px, pz, info, WALL);'),
    (RENDER, 'doors and walls swapped, so a building is red with white doorways',
     '\t\t\t\twallFrom(pixels, side, px, pz, info, wall > 0 ? DOOR : WALL);',
     '\t\t\t\twallFrom(pixels, side, px, pz, info, wall > 0 ? WALL : DOOR);'),
    (RENDER, 'the walls not drawn at all, leaving a map of ground and tree icons',
     '\t\t\tif (hasScene(id)) {\n\t\t\t\tplotScene(id, x, z, margin);\n\t\t\t} else {\n\t\t\t\twallFrom(pixels, side, px, pz, info, wall > 0 ? DOOR : WALL);\n\t\t\t}',
     '\t\t\tif (hasScene(id)) {\n\t\t\t\tplotScene(id, x, z, margin);\n\t\t\t}'),
    (RENDER, 'the shape read where the rotation is, so every wall faces the same way',
     '\t\tdrawWall(pixels, side, px, pz, rotationOf(info), shapeOf(info), colour);',
     '\t\tdrawWall(pixels, side, px, pz, shapeOf(info), rotationOf(info), colour);'),
    (RENDER, 'the shape taken from three bits rather than five, so a diagonal reads as a post',
     '\t\treturn info & 0x1F;',
     '\t\treturn info & 0x7;'),
    (RENDER, 'the rotation taken from the wrong two bits',
     '\t\treturn info >> 6 & 0x3;',
     '\t\treturn info >> 5 & 0x3;'),
    (RENDER, 'a wall turned one quarter, which puts every room inside out',
     '\t\tif (shape == 0 || shape == 2) {\n\t\t\tedge(pixels, side, px, pz, rotation, colour);',
     '\t\tif (shape == 0 || shape == 2) {\n\t\t\tedge(pixels, side, px, pz, rotation + 1 & 0x3, colour);'),
    (RENDER, "an L's second edge turned the other way, so corners point outward",
     '\t\t\tedge(pixels, side, px, pz, rotation + 1 & 0x3, colour);\n\t\t}\n\t}',
     '\t\t\tedge(pixels, side, px, pz, rotation - 1 & 0x3, colour);\n\t\t}\n\t}'),
    (RENDER, 'an L drawn as a single wall, so half of every corner is missing',
     '\t\tif (shape == 2) {\n\t\t\tedge(pixels, side, px, pz, rotation + 1 & 0x3, colour);\n\t\t}\n',
     ''),
    (RENDER, 'a corner post drawn at the wrong corner',
     '\t\t\tdot(pixels, side, rotation == 1 || rotation == 2 ? px + 3 : px,\n\t\t\t\trotation == 2 || rotation == 3 ? pz + 3 : pz, colour);',
     '\t\t\tdot(pixels, side, rotation == 1 || rotation == 3 ? px + 3 : px,\n\t\t\t\trotation == 2 || rotation == 3 ? pz + 3 : pz, colour);'),
    (RENDER, 'a wall edge three pixels long, so every join has a gap in it',
     '\t\tfor (int i = 0; i < 4; i++) {\n\t\t\tif (rotation == 0) {',
     '\t\tfor (int i = 0; i < 3; i++) {\n\t\t\tif (rotation == 0) {'),
    (RENDER, 'the east edge drawn down the middle of the tile',
     '\t\t\t} else if (rotation == 2) {\n\t\t\t\tdot(pixels, side, px + 3, pz + i, colour);',
     '\t\t\t} else if (rotation == 2) {\n\t\t\t\tdot(pixels, side, px + 2, pz + i, colour);'),
    (RENDER, 'the models never fed in, which quietly strips the scenery off the whole world',
     '\t\t\t\tModel.method357(data, file, (byte) 7);\n',
     ''),
    (RENDER, 'a diagonal wall drawn the other way across its tile',
     '\t\t\tint at = rotation == 0 || rotation == 2 ? 3 - i : i;',
     '\t\t\tint at = rotation == 0 || rotation == 2 ? i : 3 - i;'),
    (RENDER, 'a pixel off the square wrapped round onto the far side of the picture',
     '\t\tif (px >= 0 && px < side && pz >= 0 && pz < side) {\n\t\t\tpixels[pz * side + px] = colour;\n\t\t}',
     '\t\tpixels[(pz + side) % side * side + (px + side) % side] = colour;'),
    (RENDER, 'only the square itself walked, so nothing next door ever reaches over the line',
     '\t\tfor (int x = 1; x < SCENE - 1; x++) {\n\t\t\tfor (int z = 1; z < SCENE - 1; z++) {',
     '\t\tfor (int x = margin; x < margin + SQUARE; x++) {\n\t\t\tfor (int z = margin; z < margin + SQUARE; z++) {'),

    (RENDER, 'the shrink left out of the cell size, so squares overlap by three quarters',
     '\t\tint cell = SQUARE * SCALE / shrink;',
     '\t\tint cell = SQUARE * SCALE;'),
    (RENDER, 'the squares pasted full size into a shrunken layout',
     '\t\t\tpaste(img, shrink(one, shrink), acrossOf(x, minX, cell), downOf(z, maxZ, cell));',
     '\t\t\tpaste(img, one, acrossOf(x, minX, cell), downOf(z, maxZ, cell));'),
    (RENDER, 'shrinking by sampling the corner rather than averaging the block',
     '\t\t\t\tint n = by * by;\n\t\t\t\tout.setRGB(x, y, (r / n << 16) + (g / n << 8) + b / n);',
     '\t\t\t\tout.setRGB(x, y, src.getRGB(x * by, y * by));'),
    (RENDER, 'a region taken as written, so the corners the wrong way round render nothing',
     '\t\treturn new int[] { Math.min(x0, x1), Math.max(x0, x1), Math.min(z0, z1), Math.max(z0, z1) };',
     '\t\treturn new int[] { x0, x1, z0, z1 };'),
    (RENDER, 'a square outside the region drawn anyway, over whatever was there',
     '\t\t\tif (x < minX || x > maxX || z < minZ || z > maxZ) {\n\t\t\t\tcontinue;\n\t\t\t}\n',
     ''),
]


def main():
    only = sys.argv[1] if len(sys.argv) > 1 else None
    with open(RENDER, encoding='utf-8', newline='') as f:
        orig = f.read()
    _work, inside = workspace('worldmap')
    # THE CACHE IS NOT IN THE REPOSITORY, so it is not in the copy either. The suite found it
    # once, here, and hands the same path to every run: a run that cannot find it refuses
    # outright, which would read as a mutation caught by a crash.
    pack = find_pack()
    if not pack:
        print('mutate_worldmap: no cache found. Pack the engine (npm run build in '
              'DeathPlateau-Engine) or set DP_PACK.')
        return 1
    env = dict(os.environ, DP_PACK=pack)
    print('cache: ' + pack)
    muts = [m for m in MUTS if not only or only in m[1]]
    print('running %d of %d mutations' % (len(muts), len(MUTS)))
    bad = loose = 0
    for path, why, find, repl in muts:
        n = orig.count(find)
        if n != 1:
            print('  SKIP (pattern %s) %s'
                  % ('not found' if n == 0 else 'matches %d times' % n, why))
            bad += 1
            continue
        try:
            with open(inside(path), 'w', encoding='utf-8', newline='') as f:
                f.write(orig.replace(find, repl))
            r = subprocess.run([sys.executable, inside(RUNNER)], capture_output=True, text=True,
                               timeout=900, env=env)
        except subprocess.TimeoutExpired:
            print('  %-5s %-76s %s' % ('HUNG', why, 'the suite never finished - not a catch'))
            loose += 1
            continue
        finally:
            with open(inside(path), 'w', encoding='utf-8', newline='') as f:
                f.write(orig)
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
