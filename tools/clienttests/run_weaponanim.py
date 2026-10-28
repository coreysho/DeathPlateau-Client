#!/usr/bin/env python3
"""A weapon's animations drawn by the REAL client, frame by frame - not simulated, not reasoned about.

WHY THIS EXISTS. Three rounds in one week of "the animation is wrong": the dragon warhammer's
special twice, then the abyssal bludgeon and the hill giant club "swinging like a gmaul" and the
staves' swipe. Engine-TS/tools/sim/specanims.ts asserts which seq id a weapon plays and which
entity its graphic lands on, and it PASSED on every one of those - because the id it asserts is the
one the obj names, and the obj was naming the granite maul's animations. A sim cannot tell a
bludgeon's swing from a maul's.

run_zulrahshots.py is the same instrument pointed at an npc, and its own header says why: "a sim
can assert that npc_anim was called and with which id; all three of those reports were of an
npc_anim that HAD been called, correctly, with the wrong animation in it. The only instrument that
can tell a dive from a rise is a picture of one." This points it at the PLAYER.

HOW. ::anim and ::spot (content/scripts/_test/scripts/debug/debug_anim.rs2 and debug_spot.rs2) play
any animation or graphic on the player, so there is no target and no combat to arrange. The mode
wields each weapon from the side panel, plays the animation and saves a frame per distinct pose,
reading the sequence and frame number out of the client's own ClientEntity - so the log says what
is DRAWN rather than what was sent.

Needs an engine running locally on its own ports, from Engine-TS:
    NODE_PORT=43694 WEB_PORT=8694 WEB_MANAGEMENT_PORT=8794 npx tsx src/app.ts
and that engine's login key's modulus in a file (the client's built-in one is the live world's):
    node -e "const k=require('crypto').createPrivateKey(require('fs').readFileSync('data/config/login-rsa.pem'));
             console.log(BigInt('0x'+Buffer.from(k.export({format:'jwk'}).n,'base64url').toString('hex')).toString())" > rsan.txt

    python3 tools/clienttests/run_weaponanim.py --rsan rsan.txt --out shots/anim \\
        --anim abyssal_bludgeon:abyssal_bludgeon_crush \\
        --anim abyssal_bludgeon:slayer_granite_maul_attack \\
        --anim staff_of_the_dead:human_scythe_sweep \\
        --anim dragon_warhammer:osrs_dwh_spec:dwh_spec

Each --anim is weapon:sequence[:graphic]. THE FIRST RUN ON A NEW ACCOUNT ONLY SETS IT UP: a fresh
account starts in the tutorial, where there is no inventory tab to click; the run marks the tutorial
complete and says so, and this script does the second run for you.

It writes <out>/<prefix>_<weapon>_<seq>_f<NN>.png for every pose, plus a _zoom of each (the player
is about sixty pixels tall in a 512x334 viewport, so the full frame is unreadable for a pose), and
prints the sequence the client is actually playing and every frame number it drew.
"""
import argparse
import os
import random
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
JAR = os.path.join(ROOT, 'build/libs/rs2client-dev.jar')


def seq_labels(content, wanted):
    """id:name for the sequences being watched, out of the pack file that assigned those ids.

    Without this the log numbers them, and "seq 3298" versus "seq 1665" is exactly the difference
    between a bludgeon's swing and a granite maul's - which is the thing being looked at."""
    out = []
    path = os.path.join(content, 'pack', 'seq.pack')
    with open(path, newline='') as f:
        for line in f:
            if '=' not in line:
                continue
            sid, name = line.strip().split('=', 1)
            if name in wanted:
                out.append('%s:%s' % (sid, name))
    return ','.join(out)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--rsan', required=True)
    ap.add_argument('--out', required=True)
    ap.add_argument('--anim', action='append', required=True,
                    help='weapon:sequence[:graphic], repeatable')
    ap.add_argument('--content', default=os.path.join(os.path.dirname(ROOT), 'content'),
                    help='content checkout, for pack/seq.pack (default: the sibling one)')
    ap.add_argument('--jar', help='client jar to drive (default build/libs/rs2client-dev.jar)')
    ap.add_argument('--user', help='account to log in as (default: a fresh throwaway)')
    ap.add_argument('--yaw', type=int, default=1536, help='camera yaw to pin, so poses compare')
    ap.add_argument('--pitch', type=int, default=300,
                    help='camera pitch: 383 is straight down, 128 nearly level. The interface modes '
                         'use 300, which looks at a swing from above and shows an arm out.')
    ap.add_argument('--port', type=int, default=43694)
    ap.add_argument('--webport', type=int, default=8694)
    ap.add_argument('--width', type=int, default=1280)
    ap.add_argument('--height', type=int, default=800)
    a = ap.parse_args()

    rows = []
    wanted = set()
    for spec in a.anim:
        parts = [p.strip() for p in spec.split(':')]
        if len(parts) < 2 or not parts[0] or not parts[1]:
            print('run_weaponanim: --anim wants weapon:sequence[:graphic], got %r' % spec)
            return 2
        rows.append(':'.join(parts[:3]))
        wanted.add(parts[1])

    jar = os.path.abspath(a.jar) if a.jar else JAR
    if not os.path.exists(jar):
        print('run_weaponanim: no client jar at %s - build it first (./gradlew shadowJar), or a '
              'stale jar is a different client' % jar)
        return 1
    out = os.path.abspath(a.out)
    os.makedirs(out, exist_ok=True)
    rsan = open(a.rsan).read().strip()
    labels = seq_labels(os.path.abspath(a.content), wanted)
    print('watching: ' + (labels or '(no names found in seq.pack - ids only)'))

    classes = os.path.join(out, 'classes')
    os.makedirs(classes, exist_ok=True)
    r = subprocess.run(['javac', '-nowarn', '-encoding', 'UTF-8', '-cp', jar, '-d', classes,
                        os.path.join(HERE, 'ResizableShots.java')], capture_output=True, text=True)
    if r.returncode != 0:
        print(r.stdout + r.stderr)
        return 1

    user = a.user or ('anim%04d' % random.randint(0, 9999))
    home = os.path.join(out, 'home')
    os.makedirs(home, exist_ok=True)
    print('account: ' + user)

    def run_once():
        cmd = ['java', '-Dshots.rsan=' + rsan, '-Dlostcity.host=127.0.0.1',
               '-Dlostcity.port=%d' % a.port, '-Dlostcity.webport=%d' % a.webport,
               '-Duser.home=' + home, '-Dshots.zulseqs=' + labels,
               '-Dshots.anims=' + ';'.join(rows), '-Dshots.yaw=%d' % a.yaw,
               '-Dshots.pitch=%d' % a.pitch,
               '-cp', jar + os.pathsep + classes, 'ResizableShots', out, 'weaponanim',
               str(a.width), str(a.height), user, 'anim']
        return subprocess.run(cmd, capture_output=True, text=True, cwd=out)

    p = run_once()
    if 'tutorial marked complete' in p.stdout:
        # The tabs arrive at the next login, so the first run is setup only. Saying so and stopping
        # would make every fresh account a two-command job for no reason.
        print('   (first run set the account up; logging in again to photograph)')
        p = run_once()

    for line in p.stdout.split('\n'):
        if line.startswith(('ok', 'FAIL', 'wielded', 'done', '  frames drawn')) or ': the client is playing' in line \
                or ': spotanim ' in line:
            print('   ' + line)
    if p.returncode != 0:
        print(p.stdout[-4000:] + p.stderr[-4000:])
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
