#!/usr/bin/env python3
"""The step table shipped in the jar is the one the engine's exporter writes.

THE GAP THIS EXISTS FOR. data/quests.json is GENERATED, by Engine-TS/tools/quest/
ExportHelper.ts out of the sim manifests. The table a player's helper reads is a RESOURCE -
deathplateau/quests.json on the plugin's own classpath - and it used to get here by hand.

So the engine's PRs #36 and #37 fixed the exporter, regenerated data/quests.json there, went
green, and merged - and the table in the jar stayed eleven steps short for a day, across six
quests, The Feud among them. No server deploy touches this file and no git pull on the box would
have fixed it. Nothing anywhere compared the two copies, so nothing could say so.

WHAT CHANGED SINCE, and what this still guards. build.gradle now takes the engine's table when
there is an engine to take it from, and build.yaml and release.yml check one out - so the jar a
player downloads is generated rather than remembered, and the committed copy is no longer what
ships. It is the FALLBACK: what a lone clone of this repository builds with, which is every
clone that has no engine beside it, and what a release ships if that checkout does not arrive.
A fallback nobody compares is a fallback that rots, and eleven of the suites construct a real
client that reads it. So this keeps it equal to the engine's.

WHAT IT CHECKS, as two separate failures, because the fix is different for each:

    the engine's copy is current   data/quests.json there == a fresh ExportHelper run.
                                   Fails when somebody changes the exporter and does not
                                   regenerate. Fix: run the exporter, commit it there.
    the fallback is that copy      this repository's resource == data/quests.json there.
                                   Fails when the engine's table moves and nobody copies it
                                   here. That is the one that happened, back when the fallback
                                   was also what shipped. Fix: copy it across and push.

IT NAMES THE QUESTS. "The files differ" sends you diffing an 800KB JSON; a step count per quest
is what made this legible in the first place, so a mismatch prints the quests whose step counts
moved and in which direction.

A SKIP HERE IS A FAILURE IN CI, and build.yaml's suite loop is told so by name. That job checks
out the engine beside this one and packs a cache, so there is nothing left for this to skip for -
and a check that quietly stops running is the whole shape of the bug it exists to catch.

    python3 tools/clienttests/run_helpertabletest.py
"""
import json
import os
import shutil
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
SHIPPED = os.path.join(ROOT, 'src/main/resources/deathplateau/quests.json')


def _engine():
    """The engine beside this checkout, under either name it goes by - Engine-TS on CI and on a
    laptop that followed the README, DeathPlateau-Engine on one that cloned it by its own name."""
    beside = os.path.dirname(ROOT)
    for name in ('Engine-TS', 'DeathPlateau-Engine'):
        if os.path.isfile(os.path.join(beside, name, 'tools/quest/ExportHelper.ts')):
            return os.path.join(beside, name)
    return None


def steps(table):
    """Quest name -> how many steps it has, which is the shape a drift shows up in."""
    return {q['name']: sum(len(s['steps']) for s in q['stages']) for q in table['quests']}


def report(what, left, lname, right, rname):
    """One comparison. Returns True when they agree, and says which quests moved when they do not."""
    if left == right:
        print(f'  ok   {what} ({sum(steps(left).values())} steps across {len(left["quests"])} quests)')
        return True
    l, r = steps(left), steps(right)
    print(f'  FAIL {what}')
    print(f'         {lname}: {sum(l.values())} steps, {len(l)} quests')
    print(f'         {rname}: {sum(r.values())} steps, {len(r)} quests')
    moved = sorted(set(l) | set(r), key=lambda k: -abs(r.get(k, 0) - l.get(k, 0)))
    named = [k for k in moved if l.get(k, 0) != r.get(k, 0)]
    for k in named[:12]:
        print(f'         {k:30s} {lname} {l.get(k, 0):4d}  ->  {rname} {r.get(k, 0):4d}')
    if len(named) > 12:
        print(f'         ...and {len(named) - 12} more')
    if not named:
        # Same counts everywhere, so something inside a step changed - a coordinate, a name, an
        # npc id. Still a real mismatch, and still fixed by copying the file across.
        print('         every step count agrees, so the difference is inside the steps themselves')
    return False


def main():
    engine = _engine()
    if engine is None:
        print('run_helpertabletest: SKIP - no Engine-TS or DeathPlateau-Engine beside this '
              'checkout, so there is no exporter to compare against')
        return 2
    if not shutil.which('npx'):
        print('run_helpertabletest: SKIP - needs npx on PATH to run the engine\'s exporter')
        return 2

    committed = os.path.join(engine, 'data/quests.json')
    if not os.path.isfile(committed):
        print(f'run_helpertabletest: SKIP - no {committed} to compare against')
        return 2

    work = tempfile.mkdtemp(prefix='helpertabletest')
    try:
        fresh_path = os.path.join(work, 'fresh.json')
        r = subprocess.run(['npx', 'tsx', 'tools/quest/ExportHelper.ts', fresh_path],
                           cwd=engine, capture_output=True, text=True)
        if r.returncode != 0 or not os.path.isfile(fresh_path):
            # NOT a failure of this client. The exporter reads the packed cache to resolve its
            # targets, so without one it cannot run at all - which is a thing this suite could
            # not do rather than a thing that is wrong.
            print('run_helpertabletest: SKIP - the exporter did not run in %s' % engine)
            print((r.stderr or r.stdout).strip()[-800:])
            return 2

        with open(fresh_path, encoding='utf-8') as f:
            fresh = json.load(f)
        with open(committed, encoding='utf-8') as f:
            engine_copy = json.load(f)
        with open(SHIPPED, encoding='utf-8') as f:
            shipped = json.load(f)

        print('the quest helper\'s step table, in all three places it exists')
        ok = report('the engine\'s data/quests.json is what the exporter writes now',
                    fresh, 'exported', engine_copy, 'committed')
        ok &= report('and the committed fallback is that same table',
                     engine_copy, 'engine', shipped, 'committed')

        if ok:
            print('\n2 checks, ALL PASS')
            return 0
        print('\nRegenerate it in the engine with `npx tsx tools/quest/ExportHelper.ts`, copy '
              'data/quests.json to src/main/resources/deathplateau/quests.json here, and push - '
              'every push to dev-logging is a client release.')
        return 1
    finally:
        shutil.rmtree(work, ignore_errors=True)


if __name__ == '__main__':
    sys.exit(main())
