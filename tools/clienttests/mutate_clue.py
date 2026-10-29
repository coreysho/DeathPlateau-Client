#!/usr/bin/env python3
"""Mutation test for tools/clienttests/run_cluetest.py.

Breaks the clue page one plausible way at a time and checks run_cluetest notices - and notices
by NAMING a check, not by crashing.

THE ONE THAT FAILS IN SILENCE is the table's order. It is binary-searched, so ids out of order
do not throw and do not return rubbish: indexOf returns -1 for clues that ARE in the table, and
the page says "No clue scroll in your inventory" while one is sitting in the bag. The rest are
arithmetic a player checks against their own feet, where being nearly right is worse than being
absent.

    python3 tools/clienttests/mutate_clue.py [filter]
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
BUILTIN = os.path.join(ROOT, 'src/main/java/jagex2/client/plugin/builtin')
STEPS = os.path.join(BUILTIN, 'ClueSteps.java')
PLUGIN = os.path.join(BUILTIN, 'CluePlugin.java')
RUNNER = os.path.join(HERE, 'run_cluetest.py')

MUTS = [
    # ---- THE SEARCH AND THE ORDER IT NEEDS.
    (STEPS, 'two ids out of order, which a binary search answers -1 to rather than failing',
     '\tstatic final int[] ID = {\n\t\t',
     '\tstatic final int[] ID = {\n\t\t999999, '),
    (STEPS, 'the search half-open, so the last step in the table is never found',
     '\t\tint high = ID.length - 1;',
     '\t\tint high = ID.length - 2;'),
    (STEPS, 'the two halves of the search swapped, so it walks away from what it is looking for',
     '\t\t\tif (ID[mid] < objId) {\n\t\t\t\tlow = mid + 1;\n\t\t\t} else {\n\t\t\t\thigh = mid - 1;\n\t\t\t}',
     '\t\t\tif (ID[mid] < objId) {\n\t\t\t\thigh = mid - 1;\n\t\t\t} else {\n\t\t\t\tlow = mid + 1;\n\t\t\t}'),

    # ---- HOW FAR. A player counts this against their own feet.
    (PLUGIN, 'distance measured as the crow flies, so every figure reads too long',
     '\t\treturn dx > dz ? dx : dz;',
     '\t\treturn (int) Math.sqrt((double) dx * dx + (double) dz * dz);'),
    (PLUGIN, 'distance added rather than taken as the larger, so a diagonal counts twice',
     '\t\treturn dx > dz ? dx : dz;',
     '\t\treturn dx + dz;'),
    (PLUGIN, 'the sign not taken off, so walking the other way reads as negative tiles',
     '\t\tif (dx < 0) {\n\t\t\tdx = -dx;\n\t\t}\n',
     ''),

    # ---- WHICH WAY.
    (PLUGIN, 'north and south swapped, which sends a player exactly wrong',
     "\t\tString ns = dz > 0 ? \"north\" : \"south\";",
     "\t\tString ns = dz > 0 ? \"south\" : \"north\";"),
    (PLUGIN, 'east and west swapped',
     "\t\tString ew = dx > 0 ? \"east\" : \"west\";",
     "\t\tString ew = dx > 0 ? \"west\" : \"east\";"),
    (PLUGIN, 'the band dropped, so two tiles off an axis reads as a diagonal',
     '\t\tif (az * 3 < ax) {\n\t\t\treturn ew;\n\t\t}\n\t\tif (ax * 3 < az) {\n\t\t\treturn ns;\n\t\t}\n',
     ''),
    (PLUGIN, 'standing on the spot named as a direction rather than as here',
     '\t\tif (dx == 0 && dz == 0) {\n\t\t\treturn "here";\n\t\t}\n',
     ''),

    # ---- THE WORDS.
    (PLUGIN, "the game's line break left in, so a row reads with a pipe through it",
     "\t\t\tout.append(c == '|' ? ' ' : c);",
     '\t\t\tout.append(c);'),
    (PLUGIN, 'the doubled spaces left where the content broke after one',
     '\t\twhile (flat.indexOf("  ") >= 0) {\n\t\t\tflat = flat.replace("  ", " ");\n\t\t}\n',
     ''),
]


def main():
    only = sys.argv[1] if len(sys.argv) > 1 else None
    orig = {}
    for path in (STEPS, PLUGIN):
        with open(path, encoding='utf-8', newline='') as f:
            orig[path] = f.read()
    _work, inside = workspace('loot')
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
