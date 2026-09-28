#!/usr/bin/env python3
"""Screenshots and live checks of the resizable window, from the real client logged in to a LOCAL
server - nothing is stubbed: ResizableShots.java draws the client's frames onto an image instead of a
window and hands its mouse and key events to the client's own listeners.

Needs an engine running locally (its own ports, so nothing else is disturbed), e.g. from Engine-TS:
    NODE_PORT=43694 WEB_PORT=8694 WEB_MANAGEMENT_PORT=8794 npx tsx src/app.ts
and that engine's login key's modulus in a file (the client's built-in one is the live world's):
    node -e "const k=require('crypto').createPrivateKey(require('fs').readFileSync('data/config/login-rsa.pem'));
             console.log(BigInt('0x'+Buffer.from(k.export({format:'jwk'}).n,'base64url').toString('hex')).toString())" > rsan.txt

    python3 tools/clienttests/run_resizableshots.py --rsan rsan.txt --out shots [--base old-client.jar]

It builds nothing: it runs build/libs/rs2client-dev.jar (gradlew.bat build first). Each run logs in
as a fresh throwaway account on the local server. What it does:
  - resizable at 1280x800 and 1920x1080: the title screen centred, the game, a menu on the scene, the
    bank centred with a menu on it, a tab button, the minimap, a walk far outside the old 512x334,
    a mouse-picking sweep of the whole window, a fullscreen interface, and the F9 switch both ways -
    each check printed ok/FAIL
  - fixed, switched to resizable and back in one session: the fixed frames before and after compared
  - with --base: the same still frames (npcs and anything that moves on its own hidden) from this
    client and from the old jar, compared pixel for pixel. The desert has nothing animated in view;
    in the Draynor house a torch's and a lantern's frames depend on the clock, so later frames there
    differ in those few pixels between any two runs, of either jar.
"""
import argparse
import os
import subprocess
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
JAR = os.path.join(ROOT, 'build/libs/rs2client-dev.jar')


def java(args, jar, classes, out, rsan, port, webport, props=()):
    home = os.path.join(out, 'home')         # display.properties goes here, not in the real ~
    os.makedirs(home, exist_ok=True)
    cmd = ['java', '-Dshots.rsan=' + rsan, '-Dlostcity.host=127.0.0.1', '-Dlostcity.port=%d' % port,
           '-Dlostcity.webport=%d' % webport, '-Duser.home=' + home] + ['-D' + p for p in props]
    cmd += ['-cp', jar + os.pathsep + classes, 'ResizableShots', out] + args
    r = subprocess.run(cmd, capture_output=True, text=True, cwd=out)
    lines = [l for l in r.stdout.split('\n') if l.startswith(('ok', 'FAIL', 'draw:', 'shot', 'resizable'))]
    for l in lines:
        print('   ' + l)
    if r.returncode != 0:
        print(r.stdout[-2000:] + r.stderr[-2000:])
    return r.returncode == 0 and not any(l.startswith('FAIL') for l in lines)


def same(a, b, crop=None):
    from PIL import Image, ImageChops
    ia = Image.open(a).convert('RGB')
    ib = Image.open(b).convert('RGB')
    if crop:
        ia = ia.crop(crop)
        ib = ib.crop(crop)
    if ia.size != ib.size:
        return False, 'sizes differ'
    box = ImageChops.difference(ia, ib).getbbox()
    return box is None, 'identical' if box is None else 'differ in %s' % (box,)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--rsan', required=True)
    ap.add_argument('--out', required=True)
    ap.add_argument('--base')
    ap.add_argument('--port', type=int, default=43694)
    ap.add_argument('--webport', type=int, default=8694)
    a = ap.parse_args()
    out = os.path.abspath(a.out)
    if a.base:
        a.base = os.path.abspath(a.base)
    os.makedirs(out, exist_ok=True)
    rsan = open(a.rsan).read().strip()
    classes = os.path.join(out, 'classes')
    os.makedirs(classes, exist_ok=True)
    r = subprocess.run(['javac', '-nowarn', '-encoding', 'UTF-8', '-cp', JAR, '-d', classes,
                        os.path.join(HERE, 'ResizableShots.java')], capture_output=True, text=True)
    if r.returncode != 0:
        print(r.stderr)
        return 1
    tag = str(int(time.time()) % 100000)
    ok = True
    for w, h in ((1280, 800), (1920, 1080)):
        print('resizable %dx%d' % (w, h))
        ok &= java(['resizable', str(w), str(h), 'rz%d%s' % (w, int(tag) % 1000)], JAR, classes, out, rsan, a.port,
                   a.webport, ['shots.pitch=200'])
    print('fixed -> resizable -> fixed, one session')
    ok &= java(['toggle', '765', '503', 'tg' + tag, 'toggle'], JAR, classes, out, rsan, a.port, a.webport,
               ['shots.noflames=true', 'shots.pitch=383', 'shots.tele=0,51,45,36,20'])
    good, why = same(os.path.join(out, 'toggle_1fixed.png'), os.path.join(out, 'toggle_4fixed.png'), (0, 0, 765, 503))
    print(('   ok   ' if good else '   FAIL ') + 'the fixed screen after switching back: ' + why)
    ok &= good
    if a.base:
        for place, spot, pitch in (('desert', '0,51,45,36,20', '383'), ('draynor', '0,48,50,28,60', '260')):
            print('fixed screen, this client against %s (%s)' % (os.path.basename(a.base), place))
            for name, jar in (('base', a.base), ('new', JAR)):
                # a fresh account each (logging the same one in twice changes what the server sends
                # it the second time); a new account opens on the tutorial's chatbox, so no name shows
                ok &= java(['compare', '765', '503', 'c%s%s%s' % (name[0], place[0], tag), '%s_%s' % (place, name)], jar,
                           classes, out, rsan, a.port, a.webport,
                           ['shots.noflames=true', 'shots.pitch=' + pitch, 'shots.tele=' + spot])
            for shot in ('scene', 'menu', 'bank'):
                good, why = same(os.path.join(out, '%s_base_%s.png' % (place, shot)),
                                 os.path.join(out, '%s_new_%s.png' % (place, shot)))
                print(('   ok   ' if good else '   DIFF ') + '%s %s: %s' % (place, shot, why))
    print()
    print('ALL PASS' if ok else 'FAILURES above')
    return 0 if ok else 1


if __name__ == '__main__':
    sys.exit(main())
