#!/usr/bin/env python3
"""Mutation test for tools/clienttests/run_cluetest.py.

Breaks the clue page one plausible way at a time and checks run_cluetest notices - and notices
by NAMING a check, not by crashing.

THE ONE THAT FAILS IN SILENCE is the table's order. It is binary-searched, so ids out of order
do not throw and do not return rubbish: indexOf returns -1 for clues that ARE in the table, and
the page says "No clue scroll in your inventory" while one is sitting in the bag. The rest are
arithmetic a player checks against their own feet, where being nearly right is worse than being
absent.

AND THE GENERATOR IS MUTATED TOO, which needs the content repo. Two thirds of the table is
scraped out of scripts rather than read off a param, so a pattern that goes stale costs
destinations without costing steps - the table still builds, just emptier. Those mutations are
only caught by the check that regenerates and compares, and that check needs to be told where
the content is, because this runs against a copy of the repository in a temp directory where
nothing is beside anything. DP_CONTENT is how it is told; without a content repo at all the
generator mutations below survive, and say so.

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
MAKER = os.path.join(ROOT, 'tools/clues/make_clues.py')
RUNNER = os.path.join(HERE, 'run_cluetest.py')
CONTENT = os.environ.get('DP_CONTENT') or os.path.join(os.path.dirname(ROOT), 'DeathPlateau-Content')

MUTS = [
    # ---- THE SEARCH AND THE ORDER IT NEEDS.
    (STEPS, 'two ids out of order, which a binary search answers -1 to rather than failing',
     '\tpublic static final int[] ID = {\n\t\t',
     '\tpublic static final int[] ID = {\n\t\t999999, '),
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

    # ---- WHICH SCROLLS ARE SHOWN. A player carries an easy, a medium and a hard at once.
    (PLUGIN, 'only the first scroll in the bag listed, which is what the page used to do',
     '\t\t\t\tfound[count++] = slot;',
     '\t\t\t\tfound[count++] = slot;\n\t\t\t\tbreak;'),
    (PLUGIN, 'the rows numbered off by one, so every scroll draws the next slot\'s icon',
     '\t\t\t\tfound[count++] = slot;',
     '\t\t\t\tfound[count++] = slot + 1;'),
    (PLUGIN, 'a row index taken on trust, which throws the frame the bag empties',
     '\t\tif (row < 0 || row >= slots.length) {\n\t\t\treturn -1;\n\t\t}\n',
     ''),
    (PLUGIN, 'a slot taken on trust, which throws the frame the bag shrinks',
     '\t\tif (slot < 0 || slot >= objIds.length) {\n\t\t\treturn -1;\n\t\t}\n',
     ''),

    # ---- DIG OR SEARCH. A player who reads "dig" at a crate wastes a spade charge.
    (PLUGIN, 'a crate told to be dug up and a dig spot not',
     '\t\t\tboolean dig = ClueSteps.SPOT[step] == ClueSteps.SPOT_DIG;',
     '\t\t\tboolean dig = ClueSteps.SPOT[step] != ClueSteps.SPOT_DIG;'),
    (PLUGIN, 'the floor ignored, so a step one storey up reads as twelve tiles of walking',
     '\t\t\tif (ClueSteps.LEVEL[step] != plane) {\n\t\t\t\tout.append(", on another floor");\n\t\t\t}\n',
     ''),

    # ---- THE THINGS THE SCROLL DOES NOT SAY, AND THE SWITCHES THAT WITHHOLD THEM.
    (PLUGIN, 'the answer given whether or not it was asked for',
     '\t\tif (showAnswers && ClueSteps.ANSWER[step].length() > 0) {',
     '\t\tif (ClueSteps.ANSWER[step].length() > 0) {'),
    (PLUGIN, 'the person named whether or not it was asked for',
     '\t\t} else if (showAnswers && !namesPerson(step)) {',
     '\t\t} else if (!namesPerson(step)) {'),
    (PLUGIN, 'the answer still on the right of the row with the switch off',
     '\t\t\treturn showAnswers ? ClueSteps.ANSWER[step] : "";',
     '\t\t\treturn ClueSteps.ANSWER[step];'),
    (PLUGIN, 'the guardian warning not switchable',
     '\t\tif (showGuardian && ClueSteps.GUARD[step].length() > 0) {',
     '\t\tif (ClueSteps.GUARD[step].length() > 0) {'),
    (PLUGIN, 'the guardian warning replacing where to go rather than following it',
     '\t\t\tif (out.length() > 0) {\n\t\t\t\tout.append(" - ");\n\t\t\t}\n',
     '\t\t\tout.setLength(0);\n'),
    (PLUGIN, 'every step told to speak to somebody, including the ones that already say so',
     '\t\treturn wordsOf(step).toLowerCase().indexOf(who.toLowerCase()) >= 0;',
     '\t\treturn false;'),
    (PLUGIN, 'no step told who it hides, because they all count as already saying it',
     '\t\treturn wordsOf(step).toLowerCase().indexOf(who.toLowerCase()) >= 0;',
     '\t\treturn true;'),
    (PLUGIN, 'a placeless step given a reading, which prints as "-1, -1"',
     '\t\tif (ClueSteps.SPOT[step] == ClueSteps.SPOT_NONE) {\n\t\t\treturn "";\n\t\t}\n',
     ''),

    # ---- THE SCRAPE. Two thirds of the table comes out of scripts rather than off a param, so
    # a pattern that goes stale here costs destinations and still builds a table.
    (MAKER, 'the tile pattern narrowed, so the crates and drawers lose their places',
     r"LOC_SPOT = re.compile(r'loc_coord\s*=\s*(\d+(?:_\d+){4})\s*&\s*inv_total\(inv,\s*(trail_clue_\w+)\)')",
     r"LOC_SPOT = re.compile(r'loc_coord = (\d+(?:_\d+){4}) & inv_total\(inv, (trail_clue_\w+)\)x')"),
    (MAKER, "the trigger pattern narrowed, so nobody's name is scraped at all",
     r"TRIGGER = re.compile(r'^\[(?:op|ap)npc\d,([a-z0-9_]+)\]')",
     r"TRIGGER = re.compile(r'^\[(?:op|ap)npc\d,([a-z0-9_]+)\]x')"),
    (MAKER, 'the npc display name dropped for the script name behind it',
     "        who = npcs.get(people[0], '')",
     "        who = people[0]"),
    # NOT MUTATED: the `len(people) == 1` and `len(tiles) == 1` tie-breaks. Loosening either to
    # >= 1 produces a byte-identical table, because no step in the content today is claimed by
    # two scripts - measured, not assumed. They are equivalent mutants and equivalent mutants
    # are deleted rather than chased; the guards stay because the trail is still being wired.
    (MAKER, "the scroll's own dig spot passed over for a tile some script mentions",
     "        if where:\n            spot = SPOT_DIG\n        else:",
     "        if False:\n            spot = SPOT_DIG\n        else:"),
    (MAKER, 'the guardian left off the steps that have one',
     "            'guard': npcs.get(guardian, '') if guardian else '',",
     "            'guard': '',"),
    (MAKER, 'the challenge answers left off',
     "            'answer': params.get('trail_challenge_answer', ''),",
     "            'answer': '',"),
]


def main():
    only = sys.argv[1] if len(sys.argv) > 1 else None
    orig = {}
    for path in {m[0] for m in MUTS}:
        with open(path, encoding='utf-8', newline='') as f:
            orig[path] = f.read()
    if not os.path.isdir(CONTENT):
        print('no content repo at %s - the generator mutations below will survive, honestly'
              % CONTENT)
    env = dict(os.environ, DP_CONTENT=CONTENT)
    _work, inside = workspace('clue')
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
                               timeout=900, env=env)
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
