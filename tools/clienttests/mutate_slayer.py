#!/usr/bin/env python3
"""Mutation test for tools/clienttests/run_slayertest.py.

Breaks the slayer page one plausible way at a time and checks run_slayertest notices - and
notices by NAMING a check, not by crashing.

EVERY ONE OF THESE LEAVES A PAGE THAT FILLS AND READS. The two that matter are the ones a
player would act on: a finished task still showing as a task, because complete_task leaves
slayer_target where it was and only zeroes the count, and a name table that has fallen behind
the content, because the symptom of that is a row reading "Task 81" rather than anything
breaking.

    python3 tools/clienttests/mutate_slayer.py [filter]
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
TASKS = os.path.join(BUILTIN, 'SlayerTasks.java')
PLUGIN = os.path.join(BUILTIN, 'SlayerPlugin.java')
RUNNER = os.path.join(HERE, 'run_slayertest.py')

MUTS = [
    # ---- IS THERE A TASK. complete_task zeroes the count and leaves the target alone.
    (PLUGIN, 'the count ignored, so a finished task shows for ever and the player walks to Vannaka',
     '\t\treturn target > 0 && count > 0;',
     '\t\treturn target > 0;'),
    (PLUGIN, 'the target ignored, so a stale count shows a task nobody has',
     '\t\treturn target > 0 && count > 0;',
     '\t\treturn count > 0;'),
    (PLUGIN, 'zero counted as a task, so the page opens on Task 0 for a new player',
     '\t\treturn target > 0 && count > 0;',
     '\t\treturn target >= 0 && count >= 0;'),

    # ---- THE NAME TABLE, which is the one copy of content data here.
    (TASKS, 'a task dropped from the table, as happens when the content gains one and this does not',
     '\t\tNAME[2] = "Goblins";\n',
     ''),
    (TASKS, 'the table read one along, so every task is named as its neighbour',
     '\t\treturn target < 0 || target >= NAME.length ? "" : NAME[target];',
     '\t\treturn target < 0 || target + 1 >= NAME.length ? "" : NAME[target + 1];'),
    (TASKS, 'the end of the table unchecked, so an unknown id throws instead of being unknown',
     '\t\treturn target < 0 || target >= NAME.length ? "" : NAME[target];',
     '\t\treturn target < 0 ? "" : NAME[target];'),

    # ---- THE MASTER.
    (PLUGIN, 'the master list off by one, so every task is credited to the master below',
     '\tstatic final String[] MASTER = { "", "Turael", "Mazchna", "Vannaka", "Chaeldar", "Duradel" };',
     '\tstatic final String[] MASTER = { "Turael", "Mazchna", "Vannaka", "Chaeldar", "Duradel", "" };'),
    (PLUGIN, 'an unrecorded master named as somebody, rather than as nobody',
     '\t\treturn master < 0 || master >= MASTER.length ? "" : MASTER[master];',
     '\t\treturn master < 0 || master >= MASTER.length ? "Turael" : MASTER[master];'),

    # ---- THE ROW.
    # A mutation that took the singular out of this row is NOT here: "left" has no plural, so
    # the ternary it removed produced the same string down both branches. Writing it showed the
    # ternary said nothing, and the ternary went rather than the mutation being chased.
    (PLUGIN, 'the master left off the row entirely',
     '\t\treturn who.length() > 0 ? much + ", from " + who : much;',
     '\t\treturn much;'),

    # ---- THE VARPS. A wrong id reads somebody else's state with no symptom but a wrong number.
    (PLUGIN, 'the count and the target swapped, so the page shows the id as the amount',
     '\tstatic final int VARP_COUNT = 394;\n\tstatic final int VARP_TARGET = 395;',
     '\tstatic final int VARP_COUNT = 395;\n\tstatic final int VARP_TARGET = 394;'),
    (PLUGIN, 'the streak reading the points varp',
     '\tstatic final int VARP_STREAK = 866;',
     '\tstatic final int VARP_STREAK = 865;'),
]


def main():
    only = sys.argv[1] if len(sys.argv) > 1 else None
    orig = {}
    for path in (TASKS, PLUGIN):
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
