#!/usr/bin/env python3
"""Mutation test for tools/clienttests/run_gamestatetest.py.

Breaks API level 10 one way at a time and checks run_gamestatetest notices - and notices by
NAMING a check, not by crashing.

EVERY MUTATION BELOW LEAVES THE CLIENT COMPILING AND RUNNING, AND ANSWERING WITH A NUMBER. That
is the whole reason this level needs a suite of its own: a varbit read through a mask one bit
short is still a small number in the right range, and an inventory slot read without its plus
one is still an obj that exists and has a name. A plugin built on either would be confidently,
quietly wrong, and nothing in a log or on the screen would say so.

The arithmetic-vs-logical shift is NOT in here on purpose. Swapping `>>` for `>>>` in
decodeVarbit changes nothing for any range a varbit can have - the sign bits an arithmetic shift
drags down always land above the mask - so a suite that demanded it be caught would be demanding
a test of something that is not a bug.

    python3 tools/clienttests/mutate_gamestate.py [filter]
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
CTX = os.path.join(ROOT, 'src/main/java/jagex2/client/plugin/PluginContext.java')
API = os.path.join(ROOT, 'src/main/java/jagex2/client/plugin/PluginApi.java')
RUNNER = os.path.join(HERE, 'run_gamestatetest.py')

MUTS = [
    # ---- THE VARBIT MASK. The headline bug: every one of these reads a real varbit and hands
    # back a number that is the right shape and the wrong value.
    (CTX, 'the mask one bit short, so a four-bit patch stage reads as its lower three',
     '\t\treturn varp >> lsb & masks[msb - lsb];',
     '\t\treturn varp >> lsb & (masks[msb - lsb] >>> 1);'),
    (CTX, 'the mask taken from the top bit rather than the width',
     '\t\treturn varp >> lsb & masks[msb - lsb];',
     '\t\treturn varp >> lsb & masks[msb];'),
    (CTX, 'the mask hand-written instead of read from the client, which breaks at full width',
     '\t\treturn varp >> lsb & masks[msb - lsb];',
     '\t\treturn varp >> lsb & ((1 << (msb - lsb + 1)) - 1);'),
    (CTX, 'the field shifted down from the wrong end',
     '\t\treturn varp >> lsb & masks[msb - lsb];',
     '\t\treturn varp >> msb & masks[msb - lsb];'),
    (CTX, 'the shift off by one, so the field drags in the bit below it',
     '\t\treturn varp >> lsb & masks[msb - lsb];',
     '\t\treturn varp >> (lsb - 1) & masks[msb - lsb];'),
    (CTX, 'no shift at all, so every varbit reads as the bottom of its varp',
     '\t\treturn varp >> lsb & masks[msb - lsb];',
     '\t\treturn varp & masks[msb - lsb];'),

    # ---- THE MALFORMED RANGES. A varbit whose top bit is past 31 has a legal WIDTH, so the
    # width guard alone lets it through to read rubbish out of a neighbouring field.
    (CTX, 'the top-bit guard dropped, leaving only the width one behind it',
     ' || msb > 31 || msb - lsb >= masks.length) {',
     ' || msb - lsb >= masks.length) {'),

    # ---- THE VARBIT LOOKUP.
    (CTX, 'an unknown varbit answered as 0 rather than said to be unknown',
     '\tpublic boolean hasVarbit(int id) {\n\t\treturn varbitType(id) != null;',
     '\tpublic boolean hasVarbit(int id) {\n\t\treturn true;'),
    (CTX, 'the varbit reading the varp it is numbered after rather than the one it lives in',
     '\t\treturn decodeVarbit(this.getVarp(bit.field1762), bit.field1763, bit.field1764);',
     '\t\treturn decodeVarbit(this.getVarp(id), bit.field1763, bit.field1764);'),

    # ---- THE VARP GUARDS. The client nulls its varps on logout while overlays carry on being
    # drawn, so this is the path that actually runs when nobody is watching.
    (CTX, 'the no-world guard on getVarp dropped, so an overlay throws through a logout',
     '\t\treturn varps == null || id < 0 || id >= varps.length ? 0 : varps[id];',
     '\t\treturn varps[id];'),
    (CTX, 'getVarp bounds-checked at the start but not the end',
     '\t\treturn varps == null || id < 0 || id >= varps.length ? 0 : varps[id];',
     '\t\treturn varps == null || id < 0 ? 0 : varps[id];'),

    # ---- THE SLOT'S PLUS ONE. Forgetting it does not fail: it names the obj one id below.
    (CTX, 'the plus one forgotten, so every slot reads the obj below the one in it',
     '\t\treturn raw <= 0 ? -1 : raw - 1;',
     '\t\treturn raw <= 0 ? -1 : raw;'),
    (CTX, 'obj 0 treated as an empty slot, which is why empty cannot be stored as 0',
     '\t\treturn raw <= 0 ? -1 : raw - 1;',
     '\t\treturn raw <= 1 ? -1 : raw - 1;'),

    # ---- THE INVENTORY READS.
    (CTX, 'a count handed back for an empty slot, stale from whatever was there before',
     '\t\treturn decodeSlotObj(ids[slot]) == -1 ? 0 : counts[slot];',
     '\t\treturn counts[slot];'),
    (CTX, 'countObj counting slots rather than what is in them',
     '\t\t\t\ttotal += counts[slot];',
     '\t\t\t\ttotal++;'),
    (CTX, 'countObj matching the stored id, so it answers for the obj one above',
     '\t\t\tif (decodeSlotObj(ids[slot]) == objId) {',
     '\t\t\tif (ids[slot] == objId) {'),
    (CTX, 'the wrong component read, so everybody is carrying nothing',
     '\tprivate static final int INVENTORY_COM = 3214;',
     '\tprivate static final int INVENTORY_COM = 3213;'),
    (CTX, 'the interface cache not checked, so a slot read throws through a logout',
     '\t\tif (types == null || data == null || INVENTORY_COM >= types.length || INVENTORY_COM >= data.length) {\n\t\t\treturn null;\n\t\t}\n',
     ''),

    # ---- THE LEVEL. A level that does not move is a jar built for this client running on the
    # last one and throwing NoSuchMethodError mid-frame.
    (API, 'the level left where it was, so a plugin built for 10 is refused here',
     '\tpublic static final int LEVEL = 10;',
     '\tpublic static final int LEVEL = 9;'),
]


def main():
    only = sys.argv[1] if len(sys.argv) > 1 else None
    orig = {}
    for path in (CTX, API):
        with open(path, encoding='utf-8', newline='') as f:
            orig[path] = f.read()
    _work, inside = workspace('gamestate')
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
        skipped = [l for l in r.stdout.split('\n') if l.strip().startswith('SKIP')]
        if skipped:
            # A section that did not run cannot have caught anything, and a suite that quietly
            # skips the half this mutation lives in is not measuring what it claims to.
            print('  %-5s %-76s %s' % ('SKIP', why, 'a section did not run - no display?'))
            bad += 1
            continue
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
