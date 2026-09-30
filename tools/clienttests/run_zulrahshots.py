#!/usr/bin/env python3
"""Zulrah, photographed in the REAL client against a local engine - not rendered, not simulated.

ResizableShots.java's "zulrah" mode logs in, gives itself the stats and the varp the priestess's
boat wants, sails out to the shrine and then watches the snake frame by frame: which sequence the
CLIENT is playing on it, which frame of that sequence, what colour the npc is, and how many map
spotanims are lying on the floor. Every time the sequence changes it saves a frame.

Why this exists. Four rounds of this boss were sourced from the cache, asserted in tools/sim and
handed over, and every time the owner came back with something that LOOKED wrong: "zulrah
disappears quickly and respawns quickly", "the venom clouds are showing as fireballs", "it appears
they dont have the animations of leaving one spot and appearing at the other". None of those is
visible to a sim. A sim can assert that npc_anim was called and with which id; all three of those
reports were of an npc_anim that HAD been called, correctly, with the wrong animation in it. The
only instrument that can tell a dive from a rise is a picture of one.

Set up exactly as run_resizableshots.py describes - an engine on its own ports, and that engine's
login key's modulus in a file:

    NODE_PORT=43694 WEB_PORT=8694 WEB_MANAGEMENT_PORT=8794 npx tsx src/app.ts
    python3 tools/clienttests/run_zulrahshots.py --rsan rsan.txt --out shots/zulrah

It reads content/pack/seq.pack so the log names the animations instead of numbering them, which is
the whole difference between "seq 5239" and "the green form is playing the RISE where its dive
should be".
"""
import argparse
import os
import random
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
JAR = os.path.join(ROOT, 'build/libs/rs2client-dev.jar')

# The animations worth naming in the log. Everything else prints as its bare id, which is enough to
# notice that something unexpected played.
WANTED = ('zulrah_rise', 'zulrah_dive_serpentine', 'zulrah_dive_magma', 'zulrah_dive_tanzanite',
          'zulrah_venom_spray', 'zulrah_flinch', 'zulrah_tail_swipe', 'zulrah_tail_target',
          'osrs_seq_5069', 'osrs_seq_5070', 'osrs_seq_5804')


def seq_labels(content):
    """id:name for the Zulrah animations, out of the pack file that assigned those ids."""
    out = []
    path = os.path.join(content, 'pack', 'seq.pack')
    with open(path, newline='') as f:
        for line in f:
            if '=' not in line:
                continue
            sid, name = line.strip().split('=', 1)
            if name in WANTED:
                out.append('%s:%s' % (sid, name))
    return ','.join(out)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--rsan', required=True)
    ap.add_argument('--out', required=True)
    ap.add_argument('--content', default=os.path.join(os.path.dirname(ROOT), 'content'),
                    help='content checkout, for pack/seq.pack (default: the sibling one)')
    ap.add_argument('--jar', help='client jar to drive (default build/libs/rs2client-dev.jar)')
    ap.add_argument('--user', help='account to log in as (default: a fresh throwaway)')
    ap.add_argument('--frames', type=int, default=900, help='how many frames to watch the fight for')
    ap.add_argument('--shots', type=int, default=40, help='cap on saved frames')
    ap.add_argument('--port', type=int, default=43694)
    ap.add_argument('--webport', type=int, default=8694)
    ap.add_argument('--width', type=int, default=1280)
    ap.add_argument('--height', type=int, default=800)
    a = ap.parse_args()

    jar = os.path.abspath(a.jar) if a.jar else JAR
    out = os.path.abspath(a.out)
    os.makedirs(out, exist_ok=True)
    rsan = open(a.rsan).read().strip()
    labels = seq_labels(os.path.abspath(a.content))
    print('seq labels: ' + labels)

    classes = os.path.join(out, 'classes')
    os.makedirs(classes, exist_ok=True)
    r = subprocess.run(['javac', '-nowarn', '-encoding', 'UTF-8', '-cp', jar, '-d', classes,
                        os.path.join(HERE, 'ResizableShots.java')], capture_output=True, text=True)
    if r.returncode != 0:
        print(r.stdout + r.stderr)
        return 1

    user = a.user or ('zul%04d' % random.randint(0, 9999))
    home = os.path.join(out, 'home')
    os.makedirs(home, exist_ok=True)
    cmd = ['java', '-Dshots.rsan=' + rsan, '-Dlostcity.host=127.0.0.1', '-Dlostcity.port=%d' % a.port,
           '-Dlostcity.webport=%d' % a.webport, '-Duser.home=' + home,
           '-Dshots.zulseqs=' + labels, '-Dshots.zulframes=%d' % a.frames,
           '-Dshots.zulmax=%d' % a.shots,
           '-cp', jar + os.pathsep + classes, 'ResizableShots', out, 'zulrah',
           str(a.width), str(a.height), user, 'zulrah']
    print('account: ' + user)
    p = subprocess.run(cmd, capture_output=True, text=True, cwd=out)
    for line in p.stdout.split('\n'):
        if line.startswith(('ok', 'FAIL', 'frame ', 'shot ', 'in the shrine', 'boarding', 'clouds ',
                            'frames with', 'most map', 'what the client', 'no ', 'attacking')):
            print('   ' + line)
    if p.returncode != 0:
        print(p.stdout[-4000:] + p.stderr[-4000:])
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
