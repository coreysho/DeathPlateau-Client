#!/usr/bin/env python3
"""Dress a player and photograph him from four sides, with the CLIENT'S OWN RENDERER.

WHY THIS EXISTS. The content repo's tools/wearrender.py answers "does this garment show through that
one" by reimplementing Model.method382 in python. It is a useful tool and in October 2026 it was
wrong about that question five times running: eight passes of worn-model priorities shipped on its
numbers and every one was reported still clipping from the game, until the lot was reverted. Two of
its faults are known and fixed - it drew the kit body inside the armour, and its pixel count is
biased toward the depth-sorted bucket - and the third cannot be fixed, which is that it is not the
client. This is the client, logged in to a real server, drawing a real player.

Needs an engine running locally (its own ports, so nothing else is disturbed), e.g. from Engine-TS:
    NODE_PORT=43694 WEB_PORT=8694 WEB_MANAGEMENT_PORT=8794 npx tsx src/app.ts
and that engine's login key's modulus in a file (the client's built-in one is the live world's):
    node -e "const k=require('crypto').createPrivateKey(require('fs').readFileSync('data/config/login-rsa.pem'));
             console.log(BigInt('0x'+Buffer.from(k.export({format:'jwk'}).n,'base64url').toString('hex')).toString())" > rsan.txt

    python3 tools/clienttests/run_wearshots.py --rsan rsan.txt --out shots --wear max_cape
    python3 tools/clienttests/run_wearshots.py --rsan rsan.txt --out shots \
        --wear max_cape,armadyl_chestplate,rune_platelegs,bandos_boots --pitch 330

It writes <prefix>_y<yaw>.png (the whole frame) and <prefix>_y<yaw>_zoom.png (the player, scaled 5x
with no smoothing) for each yaw: 0 is his face, 1024 his back.

THE FIRST RUN ON A NEW ACCOUNT ONLY SETS IT UP. A fresh account starts in the tutorial, where there
is no inventory tab to click; the run marks the tutorial complete and says so, and the tabs arrive
at the next login. This script does that second run for you.

If the client says "Death Plateau has been updated", the cache in its throwaway home is older than
the server's - pass --fresh to start from an empty one.
"""
import argparse
import os
import shutil
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
JAR = os.path.join(ROOT, 'build', 'libs', 'rs2client-dev.jar')


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--rsan', required=True, help='file holding the local engine login key modulus')
    ap.add_argument('--out', default='shots')
    ap.add_argument('--wear', default='max_cape', help='comma separated obj names, worn in order')
    ap.add_argument('--yaws', default='0,512,1024,1536')
    ap.add_argument('--pitch', default='300', help='128 nearly level .. 383 straight down')
    ap.add_argument('--user', default='wearshots')
    ap.add_argument('--prefix', default='wear')
    ap.add_argument('--port', type=int, default=43694)
    ap.add_argument('--webport', type=int, default=8694)
    ap.add_argument('--jar', default=JAR)
    ap.add_argument('--fresh', action='store_true', help='empty client cache (after a server rebuild)')
    a = ap.parse_args()

    if not os.path.exists(a.jar):
        raise SystemExit(f'no client jar at {a.jar} - run gradlew build first')
    os.makedirs(a.out, exist_ok=True)
    rsan = open(a.rsan).read().strip()

    classes = tempfile.mkdtemp(prefix='wearshots-')
    home = os.path.join(tempfile.gettempdir(), 'wearshots-home')
    if a.fresh and os.path.isdir(home):
        shutil.rmtree(home, ignore_errors=True)
    os.makedirs(home, exist_ok=True)

    r = subprocess.run(['javac', '-nowarn', '-encoding', 'UTF-8', '-cp', a.jar, '-d', classes,
                        os.path.join(HERE, 'ResizableShots.java')], capture_output=True, text=True)
    if r.returncode:
        print(r.stdout + r.stderr)
        raise SystemExit('the harness did not compile')

    cmd = ['java', '-Dshots.rsan=' + rsan, '-Dshots.wear=' + a.wear, '-Dshots.yaws=' + a.yaws,
           '-Dshots.pitch=' + a.pitch, '-Dlostcity.host=127.0.0.1', '-Dlostcity.port=%d' % a.port,
           '-Dlostcity.webport=%d' % a.webport, '-Duser.home=' + home,
           '-cp', a.jar + os.pathsep + classes, 'ResizableShots', a.out, 'cape', '765', '503',
           a.user, a.prefix]

    # The first run on a new account only marks the tutorial complete; the tabs arrive at the next
    # login, so a second run is always needed and is harmless when the first one did the work.
    for attempt in (1, 2):
        r = subprocess.run(cmd, capture_output=True, text=True)
        out = r.stdout + r.stderr
        print(out.strip())
        if 'account set up' not in out:
            break
        print('--- running again now the account is past the tutorial ---')
    return 0


if __name__ == '__main__':
    sys.exit(main())
