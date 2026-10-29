#!/usr/bin/env python3
"""Mutation test for tools/clienttests/run_farmtest.py.

Breaks the farming page one plausible way at a time and checks run_farmtest notices - and
notices by NAMING a check, not by crashing.

WHY THIS NEEDS A SUITE. The page says two kinds of thing with completely different standing:
READY and DISEASED are read out of the cache's multiloc table and are exact, while the minutes
are watched and are an estimate. Every mutation below leaves a page that fills and reads
sensibly, and most of them turn one of those into the other - an estimate offered off a single
observation, or a first sight counted as a change so the page announces that all 32 patches just
grew. A wrong minute here is a trip planned around it.

    python3 tools/clienttests/mutate_farm.py [filter]
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
MODEL = os.path.join(BUILTIN, 'FarmPatches.java')
PLUGIN = os.path.join(BUILTIN, 'FarmPatchesPlugin.java')
RUNNER = os.path.join(HERE, 'run_farmtest.py')

MUTS = [
    # ---- THE FIRST SIGHT OF A PATCH. Logging in sets all 32 at once.
    (MODEL, 'a first sight counted as a change, so logging in says every patch just grew',
     '\t\tif (this.state[patch] == UNSEEN) {\n\t\t\tthis.state[patch] = state;\n\t\t\tthis.changedAt[patch] = minute;\n\t\t\treturn false;\n\t\t}',
     '\t\tif (this.state[patch] == UNSEEN) {\n\t\t\tthis.state[patch] = state;\n\t\t\tthis.changedAt[patch] = minute;\n\t\t\tthis.changes[patch]++;\n\t\t\treturn true;\n\t\t}'),
    (MODEL, 'the same state reported as a change every tick',
     '\t\tif (this.state[patch] == state) {\n\t\t\treturn false;\n\t\t}\n',
     ''),

    # ---- THE CADENCE. One observation is not one.
    # TWO MUTATIONS ARE DELIBERATELY NOT HERE. Loosening observe's `changes >= 1` to `>= 0`, and
    # isEstimateReady's `changes >= 2` to `>= 1`, are each unobservable on their own: the other
    # guard still refuses, because a single change leaves the interval at zero and
    # isEstimateReady also requires it to be positive. They are defence in depth, not a rule
    # apiece, and a suite that demanded them caught would be demanding a test of something that
    # is not a bug. The third one below IS reachable - two changes inside one minute.
    (MODEL, 'an estimate offered with no measured interval at all',
     '\t\treturn this.has(patch) && this.changes[patch] >= 2 && this.interval[patch] > 0;',
     '\t\treturn this.has(patch) && this.changes[patch] >= 2;'),

    # ---- THE ARITHMETIC.
    (MODEL, 'the next time counted from now rather than from the last change',
     '\t\tlong due = this.changedAt[patch] + this.interval[patch] - minute;',
     '\t\tlong due = this.interval[patch];'),
    (MODEL, 'an overdue patch counting backwards past zero',
     '\t\treturn due < 0 ? 0 : due;',
     '\t\treturn due;'),
    (MODEL, 'elapsed time going negative when the clock steps back',
     '\t\tlong since = minute - this.changedAt[patch];\n\t\treturn since < 0 ? 0 : since;',
     '\t\treturn minute - this.changedAt[patch];'),
    (MODEL, 'the interval averaged with the old one rather than replacing it',
     '\t\t\tthis.interval[patch] = since > 0 ? since : 0;',
     '\t\t\tthis.interval[patch] = this.interval[patch] == 0 ? since : (this.interval[patch] + since) / 2;'),

    # ---- SPANS.
    (MODEL, 'minutes past the hour dropped, so 1h 59m reads 1h',
     '\t\t\treturn rest == 0 ? hours + "h" : hours + "h " + rest + "m";',
     '\t\t\treturn hours + "h";'),
    (MODEL, 'the hour boundary off by one, so 60m is not yet an hour',
     '\t\tif (minutes < 60) {',
     '\t\tif (minutes <= 60) {'),

    # ---- WHAT THE CACHE SAYS. These decide READY and DISEASED, the exact half of the page.
    (PLUGIN, 'only the first option checked for Harvest, so a patch offering it second reads unready',
     '\t\treturn "Harvest".equalsIgnoreCase(op1) || "Harvest".equalsIgnoreCase(op2)\n\t\t\t|| "Harvest".equalsIgnoreCase(op3)',
     '\t\treturn "Harvest".equalsIgnoreCase(op1)\n\t\t\t|| false'),
    (PLUGIN, 'Pick not counted, so a ready bush reads as still growing',
     '\t\t\t|| "Pick".equalsIgnoreCase(op1) || "Pick".equalsIgnoreCase(op2)\n\t\t\t|| "Pick".equalsIgnoreCase(op3);',
     '\t\t\t;'),
    (PLUGIN, 'the option matched case-sensitively, so a cache that capitalises differently reads unready',
     '\t\treturn "Harvest".equalsIgnoreCase(op1) || "Harvest".equalsIgnoreCase(op2)',
     '\t\treturn "Harvest".equals(op1) || "Harvest".equalsIgnoreCase(op2)'),
    (PLUGIN, 'a dead patch not counted as sick, so it sits quietly in the list',
     '\t\treturn lower.indexOf("diseased") >= 0 || lower.indexOf("dead") >= 0;',
     '\t\treturn lower.indexOf("diseased") >= 0;'),
    (PLUGIN, 'weeds counted as a crop, so every bare patch fills the page',
     '\t\treturn lower.indexOf("weed") >= 0 || lower.indexOf("patch") >= 0 || lower.indexOf("soil") >= 0;',
     '\t\treturn lower.indexOf("patch") >= 0 || lower.indexOf("soil") >= 0;'),

    # ---- THE ORDER.
    (PLUGIN, 'ready sorted above diseased, so the thing that needs doing is not at the top',
     '\t\tif (sick) {\n\t\t\treturn 0;\n\t\t}\n\t\tif (ready) {\n\t\t\treturn 1;\n\t\t}',
     '\t\tif (ready) {\n\t\t\treturn 0;\n\t\t}\n\t\tif (sick) {\n\t\t\treturn 1;\n\t\t}'),
    (PLUGIN, 'empty patches sorted among the growing ones',
     '\t\treturn empty ? 3 : 2;',
     '\t\treturn 2;'),

    # ---- THE REAL COUNTDOWN, which the server now works out and sends.
    (PLUGIN, 'the due varps read one along, so every row shows the next patch\'s countdown',
     '\tstatic final int DUE_VARP_FIRST = 1957;',
     '\tstatic final int DUE_VARP_FIRST = 1958;'),
    (PLUGIN, 'the clock read from inside the run of due varps',
     '\tstatic final int WORLD_MINUTE_VARP = 1996;',
     '\tstatic final int WORLD_MINUTE_VARP = 1990;'),
    (PLUGIN, 'nothing-coming treated as due now, so every dead plant reads 0m',
     '\t\tif (clock <= 0 || due <= 0) {\n\t\t\treturn -1;\n\t\t}',
     '\t\tif (clock <= 0) {\n\t\t\treturn -1;\n\t\t}'),
    (PLUGIN, 'a server sending no clock read as the epoch, so every countdown is astronomical',
     '\t\tif (clock <= 0 || due <= 0) {\n\t\t\treturn -1;\n\t\t}',
     '\t\tif (due <= 0) {\n\t\t\treturn -1;\n\t\t}'),
    (PLUGIN, 'an overdue patch counting backwards',
     '\t\tlong left = (long) due - (long) clock;\n\t\treturn left < 0 ? 0 : left;',
     '\t\treturn (long) due - (long) clock;'),
    (PLUGIN, 'the page preferring its own estimate to the exact figure',
     '\t\tlong fromServer = this.serverMinutesLeft(patch);\n\t\tif (fromServer >= 0) {',
     '\t\tlong fromServer = -1;\n\t\tif (fromServer >= 0) {'),
    (PLUGIN, 'the content patch mapping dropped, so a row reads another patch\'s clock',
     '\t\t16, 17, 18, 19,               // herbs 1-4',
     '\t\t8, 9, 10, 11,                 // herbs 1-4'),

    # ---- THE CROP'S PICTURE AND THE BAR.
    (PLUGIN, 'the name tried stripped first, so Potato seed never matches the seed item',
     '\t\tout.add(name);\n\t\tString bare = name;',
     '\t\tString bare = name;'),
    (PLUGIN, 'Diseased not taken off, so a sick patch shows no picture at all',
     "\t\tfor (String prefix : new String[] { \"Diseased \", \"Dead \" }) {",
     '\t\tfor (String prefix : new String[] {}) {'),
    (PLUGIN, 'the plant suffix left on, so a growing crop shows nothing',
     "\t\tfor (String suffix : new String[] { \" plant\", \" bush\", \" tree\", \" plants\" }) {",
     '\t\tfor (String suffix : new String[] {}) {'),
    (PLUGIN, 'the bar running past full when the minute tick is late',
     '\t\tif (gone >= span) {\n\t\t\treturn 100;\n\t\t}',
     ''),
    (PLUGIN, 'a patch with nothing to measure drawn at 0 rather than with no bar',
     '\t\tif (due <= changedAt || now < changedAt) {\n\t\t\treturn -1;\n\t\t}',
     '\t\tif (due <= changedAt || now < changedAt) {\n\t\t\treturn 0;\n\t\t}'),
    (PLUGIN, 'the bar measured from now rather than from the last change',
     '\t\tlong gone = now - changedAt;',
     '\t\tlong gone = span;'),

    # ---- THE TABLE. A wrong id reads a different patch, with no symptom but a row that never moves.
    (PLUGIN, 'a patch varbit off by one, so a row reads the patch next door',
     '\t\t708, 709, 710, 711, 712, 713, 714, 715,   // allotments 1-8',
     '\t\t708, 709, 710, 711, 712, 713, 714, 716,   // allotments 1-8'),
    (PLUGIN, 'the herb patches reading the compost bins',
     '\t\t780, 781, 782, 783,                       // herbs 1-4',
     '\t\t740, 741, 742, 743,                       // herbs 1-4'),
]


def main():
    only = sys.argv[1] if len(sys.argv) > 1 else None
    orig = {}
    for path in (MODEL, PLUGIN):
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
