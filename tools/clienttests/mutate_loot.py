#!/usr/bin/env python3
"""Mutation test for tools/clienttests/run_loottest.py.

Breaks the loot tracker one plausible way at a time and checks run_loottest notices - and
notices by NAMING a check, not by crashing.

WHY THIS NEEDS A SUITE. The server says what each kill dropped now, so nothing is guessed - but
every mutation below still leaves a tracker that runs, fills a page and adds up. A merge that
overwrites instead of adding reads as a quiet session; a kill counted per item reads as a busy one;
a split loot line counted twice doubles every casket; a save that loses a line forgets a boss.
Nobody can check a loot tracker against anything - that is what it is for - so it is checked here.

    python3 tools/clienttests/mutate_loot.py [filter]
"""
import os
import subprocess
import sys

# THE WORKING TREE IS NEVER WRITTEN. Mutations go into a throwaway copy of the repository - see
# mutate_guard.workspace - and its check() is the standalone pass that every pattern below still
# matches its source exactly once.
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from mutate_guard import read_source, workspace  # noqa: E402  (after the sys.path line, necessarily)

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
BUILTIN = os.path.join(ROOT, 'src/main/java/jagex2/client/plugin/builtin')
TRACKER = os.path.join(BUILTIN, 'LootTracker.java')
PLUGIN = os.path.join(BUILTIN, 'LootTrackerPlugin.java')
WIRE = os.path.join(ROOT, 'src/main/java/jagex2/client/plugin/LootMessage.java')
RUNNER = os.path.join(HERE, 'run_loottest.py')

MUTS = [
    # ---- A KILL.
    (TRACKER, 'a kill counted per item, so one drop of three things is three kills',
     '\t\tsource.kills++;\n',
     '\t\tsource.kills += merged.size() > 0 ? merged.size() : 1;\n'),
    (TRACKER, 'a second stack of the same item in one drop overwriting the first',
     '\t\t\t\t\tinto.count += item.count;\n\t\t\t\t}\n\t\t\t}\n\t\t}\n\n\t\tSource source',
     '\t\t\t\t\tinto.count = item.count;\n\t\t\t\t}\n\t\t\t}\n\t\t}\n\n\t\tSource source'),
    (TRACKER, 'a box overwriting its coins with the last kill\'s',
     '\t\t\t} else {\n\t\t\t\tinto.count += item.count;\n\t\t\t}\n\t\t}\n\n\t\tthis.kills.add(0',
     '\t\t\t} else {\n\t\t\t\tinto.count = item.count;\n\t\t\t}\n\t\t}\n\n\t\tthis.kills.add(0'),
    (TRACKER, 'the per-kill list oldest first',
     '\t\tthis.kills.add(0, new Kill(',
     '\t\tthis.kills.add(new Kill('),
    (TRACKER, 'boxes in the order they were first made, not the one that paid out last on top',
     '\t\t\t\treturn a.lastTime > b.lastTime ? -1 : 1;',
     '\t\t\t\treturn a.lastTime > b.lastTime ? 1 : -1;'),
    (TRACKER, 'the newest source forgotten to make room rather than the longest unseen',
     '\t\t\tif (oldest == null || source.lastTime < oldest.lastTime) {',
     '\t\t\tif (oldest == null || source.lastTime > oldest.lastTime) {'),
    (TRACKER, 'Reset leaving the box\'s kills in the per-kill list',
     '\t\tthis.sources.remove(key);\n\t\tfor (int i = this.kills.size() - 1; i >= 0; i--) {',
     '\t\tthis.sources.remove(key);\n\t\tfor (int i = this.kills.size() - 1; i >= this.kills.size(); i--) {'),

    # ---- VALUE.
    (TRACKER, 'a stack valued as one of it',
     '\t\treturn prices == null ? 0 : item.count * prices.value(item.id);',
     '\t\treturn prices == null ? 0 : prices.value(item.id);'),
    (TRACKER, 'an ignored item still in the total',
     '\t\t\tif (ignored == null || !ignored.is(item.id)) {\n\t\t\t\ttotal += worth(item, prices);',
     '\t\t\tif (true) {\n\t\t\t\ttotal += worth(item, prices);'),
    (PLUGIN, 'an ignore matching any name that merely contains it',
     '\t\t\treturn pattern.equals(name);',
     '\t\t\treturn name.indexOf(pattern) >= 0;'),

    # ---- THE FILE.
    (TRACKER, 'a source\'s items not written',
     '\t\t\titems(out, source.items.values());\n',
     ''),
    (TRACKER, 'a name read back up to its first bar',
     '\t\t\t\t\tString[] f = line.split("\\\\|", 6);',
     '\t\t\t\t\tString[] f = line.split("\\\\|");'),
    (PLUGIN, 'the account file named after the account',
     '\t\t\treturn out.append(".txt").toString();',
     '\t\t\treturn "loot-" + name + ".txt";'),

    # ---- THE WIRE.
    (WIRE, 'a split kill read as two kills',
     '\t\tif (more) {\n\t\t\treturn null;\n\t\t}',
     ''),
    (WIRE, 'a half-read kill glued onto the next one',
     '\t\tif (kill != this.pendingKill || this.pendingItems == null) {',
     '\t\tif (this.pendingItems == null) {'),

    # ---- THE PAGE.
    (PLUGIN, 'Reset acting on the first press',
     '\t\tif (!box.does.equals(this.armed)) {',
     '\t\tif (false) {'),
    (PLUGIN, 'a hidden source still given a box',
     '\t\t\t\tif (hidden.contains(source.name.toLowerCase())) {\n\t\t\t\t\tcontinue;\n\t\t\t\t}\n\t\t\t\tBox box',
     '\t\t\t\tBox box'),

    # ---- THE NUMBERS ON THE PAGE.
    (TRACKER, 'the shortening threshold moved, so a column of digits overflows its row',
     '\t\tif (value < 10000) {\n\t\t\treturn commas(value);\n\t\t}',
     '\t\tif (value < 1000000) {\n\t\t\treturn commas(value);\n\t\t}'),
    (TRACKER, 'a trailing .0 left on, so every round million reads 1.0M',
     '\t\treturn tenth == 0 ? commas(whole) : commas(whole) + "." + tenth;',
     '\t\treturn commas(whole) + "." + tenth;'),
]


def main():
    only = sys.argv[1] if len(sys.argv) > 1 else None
    orig = {}
    for path in (TRACKER, PLUGIN, WIRE):
        orig[path] = read_source(path)
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
