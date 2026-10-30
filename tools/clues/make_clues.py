#!/usr/bin/env python3
"""Generates ClueSteps.java from the content's clue objs and the scripts that accept them.

WHERE THIS DATA ALREADY IS. Every clue step in this game is its own object - 107 of them - and
each carries what it says as a param:

    param=trail_desc,Search the chest in the|Duke of Lumbridge's bedroom.
    param=trail_coord,0_49_52_41_32

So the content already holds, per step, the text and (for the 38 coordinate clues) the place.
It is structured data rather than logic scattered through scripts, which is what makes a helper
possible at all - and it means no server change: the client can already see which clue is in the
inventory, and this table says what that clue wants.

AND WHERE THE REST OF IT IS. The other two thirds of the steps have no trail_coord, because
they are not dig spots - they are a crate to search or somebody to talk to. That is not missing
data, it is data in the scripts instead, and in three shapes this reads:

    loc_coord = 1_50_50_28_12 & inv_total(inv, trail_clue_easy_simple002)   -> the crate's tile
    [opnpc1,cook] ... inv_total(inv, trail_clue_medium_anagram002)          -> the person
    npc_type = black_heather & inv_total(inv, trail_clue_medium_riddle001)  -> the person

A step the content has not wired yet gets nothing and the page falls back to its words, which
is the honest answer: the trail is partly implemented, and a helper that invented a destination
for an unfinished step would send a player somewhere there is nothing to find.

THE IDS COME FROM pack/obj.pack, which is a file of explicit assignments rather than an order
things happen to be packed in, so they are stable in the way a loc id is not.

    python3 tools/clues/make_clues.py [--check] [--content DIR]
"""
import argparse
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
OUT = os.path.join(ROOT, 'src/main/java/jagex2/client/plugin/builtin/ClueSteps.java')
DEFAULT_CONTENT = os.path.join(os.path.dirname(ROOT), 'DeathPlateau-Content')

# Where a step wants the player. DIG is the scroll's own trail_coord; SEARCH is a tile a script
# ties the step to, which is a thing standing on it rather than earth to turn over.
SPOT_NONE = -1
SPOT_DIG = 0
SPOT_SEARCH = 1

CLUE = re.compile(r'\btrail_clue_[a-z]+_[a-z]+\d+\b')
TRIGGER = re.compile(r'^\[(?:op|ap)npc\d,([a-z0-9_]+)\]')
LOC_SPOT = re.compile(r'loc_coord\s*=\s*(\d+(?:_\d+){4})\s*&\s*inv_total\(inv,\s*(trail_clue_\w+)\)')
NPC_SPOT = re.compile(r'npc_type\s*=\s*([a-z0-9_]+)\s*&\s*inv_total\(inv,\s*(trail_clue_\w+)\)')


def coord(raw):
    """level_squareX_squareZ_localX_localZ -> (level, absolute x, absolute z)."""
    parts = raw.split('_')
    if len(parts) != 5:
        return None
    try:
        level, sx, sz, lx, lz = (int(p) for p in parts)
    except ValueError:
        return None
    return level, sx * 64 + lx, sz * 64 + lz


def read_config(path, key):
    """{block name: value of `key=`} over one of the unpacked config files."""
    out = {}
    cur = None
    prefix = key + '='
    for line in open(path, encoding='utf-8', errors='replace'):
        line = line.rstrip('\n')
        if line.startswith('['):
            cur = line.strip('[]')
        elif cur and line.startswith(prefix) and cur not in out:
            out[cur] = line[len(prefix):]
    return out


def read_objs(content):
    """{obj name: {'desc', 'coord', 'guardian', 'answer'}} for everything that is a clue step."""
    objs = {}
    cur = None
    for line in open(os.path.join(content, 'scripts/_unpack/377/all.obj'),
                     encoding='utf-8', errors='replace'):
        line = line.rstrip('\n')
        if line.startswith('['):
            cur = line.strip('[]')
            objs[cur] = {}
        elif cur and line.startswith('param='):
            name, _, value = line[6:].partition(',')
            objs[cur][name] = value.strip()
    return objs


def read_wiring(content):
    """{clue obj name: {'npc': set, 'loc': set}} from the scripts that accept each step.

    A SCRIPT FILE IS ONE PERSON'S, by convention - cook.rs2 opens with [opnpc1,cook] - so the
    trigger a reference sits under names who accepts the clue. The two `&` shapes above are
    read anywhere, including inside game_trail's own scripts, because that is where the three
    pickpocket steps live.
    """
    wiring = {}

    def note(clue, field, value):
        wiring.setdefault(clue, {'npc': set(), 'loc': set()})[field].add(value)

    for root, dirs, files in os.walk(os.path.join(content, 'scripts')):
        if '_unpack' in root:
            continue
        for name in files:
            if not name.endswith('.rs2'):
                continue
            trigger = None
            for line in open(os.path.join(root, name), encoding='utf-8', errors='replace'):
                found = TRIGGER.match(line)
                if found:
                    trigger = found.group(1)
                for place, clue in LOC_SPOT.findall(line):
                    note(clue, 'loc', place)
                for npc, clue in NPC_SPOT.findall(line):
                    note(clue, 'npc', npc)
                # The trigger only speaks for the file it heads, and game_trail's scripts are
                # the trail's machinery rather than one person's, so their triggers are not
                # anybody in particular.
                if trigger and 'game_trail' not in root:
                    for clue in CLUE.findall(line):
                        note(clue, 'npc', trigger)
    return wiring


def read(content):
    """{obj id: step} for every clue step the content has, keyed by its packed id."""
    ids = {}
    for line in open(os.path.join(content, 'pack/obj.pack'), encoding='utf-8') :
        if '=' in line:
            num, name = line.strip().split('=', 1)
            ids[name] = int(num)

    npcs = read_config(os.path.join(content, 'scripts/_unpack/377/all.npc'), 'name')
    objs = read_objs(content)
    wiring = read_wiring(content)

    steps = {}
    for name, params in objs.items():
        desc = params.get('trail_desc')
        place = params.get('trail_coord')
        # EITHER IS ENOUGH. A map clue carries a coordinate and no trail_desc, because its text
        # is a picture - the five of them were dropped by a version of this that insisted on
        # the words, and they are exactly the steps a helper is most use for.
        if (desc is None and place is None) or name not in ids:
            continue

        spot = SPOT_NONE
        where = coord(place) if place else None
        if where:
            spot = SPOT_DIG
        else:
            # ONE TILE OR NONE. Two scripts tying one step to two different tiles would mean
            # either could be right, and a helper that picks one is wrong half the time.
            tiles = sorted(wiring.get(name, {}).get('loc', ()))
            if len(tiles) == 1:
                where = coord(tiles[0])
                if where:
                    spot = SPOT_SEARCH

        # ...AND ONE PERSON OR NONE, for the same reason.
        who = ''
        people = sorted(wiring.get(name, {}).get('npc', ()))
        if len(people) == 1:
            who = npcs.get(people[0], '')

        guardian = params.get('trail_guardian')
        steps[ids[name]] = {
            'name': name,
            'text': desc or '',
            'level': where[0] if where else -1,
            'x': where[1] if where else -1,
            'z': where[2] if where else -1,
            'spot': spot,
            'who': who,
            'answer': params.get('trail_challenge_answer', ''),
            'guard': npcs.get(guardian, '') if guardian else '',
        }
    return steps


def java(text):
    return text.replace('\\', '\\\\').replace('"', '\\"')


def strings(out, name, doc, ids, steps, field):
    out.append('\t/** %s */' % doc)
    out.append('\tpublic static final String[] %s = {' % name)
    for i in ids:
        out.append('\t\t"%s",' % java(steps[i][field]))
    out.append('\t};')
    out.append('')


def ints(out, name, doc, ids, steps, field):
    out.append('\t/** %s */' % doc)
    out.append('\tpublic static final int[] %s = {' % name)
    values = [str(steps[i][field]) for i in ids]
    for i in range(0, len(values), 12):
        out.append('\t\t' + ', '.join(values[i:i + 12]) + ',')
    out.append('\t};')
    out.append('')


def render(steps):
    ids = sorted(steps)
    digs = sum(1 for i in ids if steps[i]['spot'] == SPOT_DIG)
    searches = sum(1 for i in ids if steps[i]['spot'] == SPOT_SEARCH)
    people = sum(1 for i in ids if steps[i]['who'])
    answers = sum(1 for i in ids if steps[i]['answer'])
    guards = sum(1 for i in ids if steps[i]['guard'])
    lost = sum(1 for i in ids if steps[i]['spot'] == SPOT_NONE and not steps[i]['who']
               and not steps[i]['answer'])

    out = []
    out.append('package jagex2.client.plugin.builtin;')
    out.append('')
    out.append('/**')
    out.append(' * What every clue step says, where it wants the player, and who is waiting there.')
    out.append(' *')
    out.append(" * GENERATED by tools/clues/make_clues.py from the content's clue objs and the scripts that")
    out.append(' * accept them - do not hand-edit; regenerate instead. ClueTest runs the generator again')
    out.append(' * with --check when the content repo is beside this one, so a clue added there and not')
    out.append(' * here is a failing test rather than a scroll the page has nothing to say about.')
    out.append(' *')
    out.append(' * None of this is invented or guessed. The words and the dig spots are the clue objects\'')
    out.append(' * own params; the crates and the people are the tiles and triggers of the scripts that')
    out.append(' * take the scroll off the player. A step the content has not wired yet gets nothing,')
    out.append(' * which is the honest answer for a trail that is partly implemented.')
    out.append(' *')
    out.append(' * %d steps: %d dig somewhere, %d point at a tile a script watches, %d name somebody to' % (len(ids), digs, searches, people))
    out.append(' * talk to, %d are challenge scrolls with an answer, %d warn of a guardian, and %d have' % (answers, guards, lost))
    out.append(' * nothing yet but their words. A step with no words at all is a map clue, whose text is a')
    out.append(' * picture the client is handed only when the scroll is opened.')
    out.append(' */')
    out.append('public final class ClueSteps {')
    out.append('')
    out.append('\tprivate ClueSteps() {')
    out.append('\t}')
    out.append('')
    out.append('\t/** {@link #SPOT} for a step that points nowhere in particular. */')
    out.append('\tpublic static final int SPOT_NONE = %d;' % SPOT_NONE)
    out.append('')
    out.append("\t/** {@link #SPOT} for the scroll's own dig spot: earth to turn over. */")
    out.append('\tpublic static final int SPOT_DIG = %d;' % SPOT_DIG)
    out.append('')
    out.append('\t/** {@link #SPOT} for a tile with the thing to search standing on it. */')
    out.append('\tpublic static final int SPOT_SEARCH = %d;' % SPOT_SEARCH)
    out.append('')
    # PUBLIC, all of it. This is a generated data table rather than a plugin's state: the
    # sidebar preview lays out a bag from it, and a tool that cannot read the table has to
    # hardcode obj ids that go stale the next time the content renumbers.
    out.append('\t/** Clue obj ids, ascending, so a lookup can stop early. */')
    out.append('\tpublic static final int[] ID = {')
    for i in range(0, len(ids), 10):
        out.append('\t\t' + ', '.join(str(x) for x in ids[i:i + 10]) + ',')
    out.append('\t};')
    out.append('')
    strings(out, 'TEXT', "What each one says. A '|' is where the game breaks the line.", ids, steps, 'text')
    ints(out, 'LEVEL', 'The floor of the place below, or -1 where there is no place.', ids, steps, 'level')
    ints(out, 'X', 'Where to go, as a world x, or -1.', ids, steps, 'x')
    ints(out, 'Z', '...and a world z.', ids, steps, 'z')
    ints(out, 'SPOT', 'Whether that place is a dig or a search: one of the SPOT_ constants.', ids, steps, 'spot')
    strings(out, 'WHO', 'The person who takes this step, by the name a player sees, or empty.', ids, steps, 'who')
    strings(out, 'ANSWER', "A challenge scroll's answer, or empty for a step that asks nothing.", ids, steps, 'answer')
    strings(out, 'GUARD', 'What attacks when this one is dug up, or empty for an undefended step.', ids, steps, 'guard')
    out.append('\t/** Where this obj sits in the arrays above, or -1 for an obj that is not a clue. */')
    out.append('\tpublic static int indexOf(int objId) {')
    out.append('\t\tint low = 0;')
    out.append('\t\tint high = ID.length - 1;')
    out.append('\t\twhile (low <= high) {')
    out.append('\t\t\tint mid = (low + high) >>> 1;')
    out.append('\t\t\tif (ID[mid] == objId) {')
    out.append('\t\t\t\treturn mid;')
    out.append('\t\t\t}')
    out.append('\t\t\tif (ID[mid] < objId) {')
    out.append('\t\t\t\tlow = mid + 1;')
    out.append('\t\t\t} else {')
    out.append('\t\t\t\thigh = mid - 1;')
    out.append('\t\t\t}')
    out.append('\t\t}')
    out.append('\t\treturn -1;')
    out.append('\t}')
    out.append('')
    out.append('\t/** How many steps this table knows. */')
    out.append('\tpublic static int count() {')
    out.append('\t\treturn ID.length;')
    out.append('\t}')
    out.append('}')
    return '\n'.join(out) + '\n'


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--check', action='store_true')
    ap.add_argument('--content', default=DEFAULT_CONTENT)
    args = ap.parse_args()
    if not os.path.isdir(args.content):
        print('make_clues: no content repo at %s' % args.content)
        return 2
    steps = read(args.content)
    if not steps:
        print('make_clues: read no clue steps at all - the patterns have gone stale')
        return 1
    # THE SCRIPT PATTERNS GO STALE SILENTLY, unlike the obj ones: a renamed trigger or a
    # reworded condition costs destinations rather than steps, and the table still builds.
    placed = sum(1 for s in steps.values() if s['x'] >= 0)
    if placed < len(steps) // 2:
        print('make_clues: only %d of %d steps have a place - the script patterns have gone stale'
              % (placed, len(steps)))
        return 1
    want = render(steps)
    if args.check:
        have = open(OUT, encoding='utf-8').read() if os.path.exists(OUT) else ''
        if have == want:
            print('make_clues: up to date (%d steps, %d placed)' % (len(steps), placed))
            return 0
        print('make_clues: OUT OF DATE - %d steps in the content, regenerate with' % len(steps))
        print('  python3 tools/clues/make_clues.py')
        return 1
    with open(OUT, 'w', encoding='utf-8') as f:
        f.write(want)
    print('make_clues: wrote %d steps (%d placed) to %s'
          % (len(steps), placed, os.path.relpath(OUT, ROOT)))
    return 0


if __name__ == '__main__':
    sys.exit(main())
