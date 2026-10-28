#!/usr/bin/env python3
"""Mutation test for tools/clienttests/run_loottest.py.

Breaks the loot tracker one plausible way at a time and checks run_loottest notices - and
notices by NAMING a check, not by crashing.

WHY THIS NEEDS A SUITE. Every mutation below leaves a tracker that runs, fills a page and adds
up. The kill is INFERRED from an npc that was standing somewhere and is not now, so each part of
that inference fails quietly and in a direction that looks like data: a window a tick short
credits nothing and reads as a quiet session; a window a tick long credits the next kill to the
last thing that died there; a kill counted per item turns one drop of bones, coins and a herb
into three kills. Nobody can check a loot tracker against anything - that is what it is for -
so it has to be checked here.

    python3 tools/clienttests/mutate_loot.py [filter]
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
TRACKER = os.path.join(BUILTIN, 'LootTracker.java')
PLUGIN = os.path.join(BUILTIN, 'LootTrackerPlugin.java')
RUNNER = os.path.join(HERE, 'run_loottest.py')

MUTS = [
    # ---- WHAT COUNTS AS A KILL. The inference itself.
    (TRACKER, 'loot credited to an npc that is still standing there, alive',
     '\t\tVacated was = this.vacated.get(Long.valueOf(tile));\n\t\tif (was == null) {',
     '\t\tVacated was = this.vacated.get(Long.valueOf(tile));\n'
     '\t\tif (was == null && this.standing.get(Long.valueOf(tile)) != null) {\n'
     '\t\t\twas = new Vacated(this.standing.get(Long.valueOf(tile)), 1);\n'
     '\t\t}\n'
     '\t\tif (was == null) {'),
    (TRACKER, 'a kill counted per ITEM, so one drop of three things is three kills',
     '\t\tif (!was.counted) {',
     '\t\tif (true) {'),
    (TRACKER, 'the kill flag never set, which is the same bug written the other way',
     '\t\t\twas.counted = true;\n',
     ''),

    # ---- THE WINDOW. A tick either way, and both look like working software.
    (TRACKER, 'the window one tick short, so a drop on the tick after is missed',
     '\tpublic static final int VACATED_TICKS = 3;',
     '\tpublic static final int VACATED_TICKS = 2;'),
    (TRACKER, 'the window one tick long, so the next kill inherits the last one',
     '\tpublic static final int VACATED_TICKS = 3;',
     '\tpublic static final int VACATED_TICKS = 4;'),
    (TRACKER, 'a vacated tile never ageing out at all',
     '\t\t\tif (--was.ticks <= 0) {',
     '\t\t\tif (false) {'),
    (TRACKER, 'ageing done after the new tiles are added, costing each one a tick of its window',
     '''\t\tIterator<Map.Entry<Long, Vacated>> going = this.vacated.entrySet().iterator();
\t\twhile (going.hasNext()) {
\t\t\tVacated was = going.next().getValue();
\t\t\tif (--was.ticks <= 0) {
\t\t\t\tgoing.remove();
\t\t\t}
\t\t}
\t\tfor (Map.Entry<Long, String> entry : this.previous.entrySet()) {
\t\t\tif (!this.standing.containsKey(entry.getKey())) {
\t\t\t\tthis.vacated.put(entry.getKey(), new Vacated(entry.getValue(), VACATED_TICKS));
\t\t\t}
\t\t}''',
     '''\t\tfor (Map.Entry<Long, String> entry : this.previous.entrySet()) {
\t\t\tif (!this.standing.containsKey(entry.getKey())) {
\t\t\t\tthis.vacated.put(entry.getKey(), new Vacated(entry.getValue(), VACATED_TICKS));
\t\t\t}
\t\t}
\t\tIterator<Map.Entry<Long, Vacated>> going = this.vacated.entrySet().iterator();
\t\twhile (going.hasNext()) {
\t\t\tVacated was = going.next().getValue();
\t\t\tif (--was.ticks <= 0) {
\t\t\t\tgoing.remove();
\t\t\t}
\t\t}'''),
    (TRACKER, 'every tile an npc stands on treated as vacated, so walking away is a kill',
     '\t\t\tif (!this.standing.containsKey(entry.getKey())) {',
     '\t\t\tif (true) {'),

    # ---- THE TILE. A packing that drops a bit credits a kill from another room.
    (TRACKER, 'the plane left out of a tile, so a drop upstairs is the kill below it',
     '\t\t\t| (long) (plane & 0x3);',
     '\t\t\t| 0L;'),
    (TRACKER, 'x and z overlapping, so tiles on a diagonal share a key',
     '\t\treturn ((long) (worldX & 0x7FFF) << 17)\n\t\t\t| ((long) (worldZ & 0x7FFF) << 2)',
     '\t\treturn ((long) (worldX & 0x7FFF) << 3)\n\t\t\t| ((long) (worldZ & 0x7FFF) << 2)'),

    # ---- VALUE.
    (TRACKER, 'value taken per item rather than per drop, as GroundItem.worth would',
     '\t\tlong worth = (long) count * (long) price;',
     '\t\tlong worth = price;'),
    (TRACKER, 'an empty drop recorded, which would count a kill for nothing',
     '\t\tif (count <= 0) {\n\t\t\treturn null;\n\t\t}\n',
     ''),

    # ---- UNATTRIBUTED LOOT, which exists so the page cannot look precise while being wrong.
    (TRACKER, 'loot nothing was standing on quietly dropped instead of counted',
     '\t\t\tthis.unattributed++;\n\t\t\tthis.unattributedValue += worth;\n',
     ''),

    # ---- BOUNDS.
    (TRACKER, 'the most valuable source dropped to make room rather than the least',
     '\t\t\tif (source.value < worstValue) {',
     '\t\t\tif (source.value > worstValue) {'),

    # ---- READING IT BACK.
    (TRACKER, 'sources listed cheapest first',
     '\t\t\t\treturn a.value > b.value ? -1 : 1;\n\t\t\t}\n\t\t\treturn a.name.compareTo(b.name);\n\t\t}\n\t};\n\n\tprivate static final Comparator<Item> BY_ITEM_VALUE',
     '\t\t\t\treturn a.value > b.value ? 1 : -1;\n\t\t\t}\n\t\t\treturn a.name.compareTo(b.name);\n\t\t}\n\t};\n\n\tprivate static final Comparator<Item> BY_ITEM_VALUE'),
    (TRACKER, 'the item view overwriting rather than adding, so two sources of bones read as one',
     '\t\t\t\tinto.count += item.count;',
     '\t\t\t\tinto.count = item.count;'),

    # ---- THE NUMBERS ON THE PAGE.
    (TRACKER, 'the shortening threshold moved, so a column of digits overflows its row',
     '\t\tif (value < 10000) {\n\t\t\treturn commas(value);\n\t\t}',
     '\t\tif (value < 1000000) {\n\t\t\treturn commas(value);\n\t\t}'),
    (TRACKER, 'the tenth taken by truncating the wrong part, so 1.9M reads 1.1M',
     '\t\tlong tenth = value % unit * 10 / unit;',
     '\t\tlong tenth = value / unit % 10;'),
    (TRACKER, 'a trailing .0 left on, so every round million reads 1.0M',
     '\t\treturn tenth == 0 ? commas(whole) : commas(whole) + "." + tenth;',
     '\t\treturn commas(whole) + "." + tenth;'),
    (TRACKER, 'the separator every two digits instead of three',
     '\t\t\tif (i > 0 && (digits.length() - i) % 3 == 0) {',
     '\t\t\tif (i > 0 && (digits.length() - i) % 2 == 0) {'),

    # ---- THE ORDER OF THE TICK, which is the whole plugin working or reporting nothing.
    (PLUGIN, 'the floor read before the npcs, so every kill goes uncredited',
     '\t\tthis.readNpcs();\n\t\tthis.readFloor();',
     '\t\tthis.readFloor();\n\t\tthis.readNpcs();'),
]


def main():
    only = sys.argv[1] if len(sys.argv) > 1 else None
    orig = {}
    for path in (TRACKER, PLUGIN):
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
