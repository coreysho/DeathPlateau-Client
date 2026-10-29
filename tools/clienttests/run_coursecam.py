#!/usr/bin/env python3
"""An agility course run in the REAL client, with no window, photographed at every obstacle.

The engine side of these courses is checked by Engine-TS/tools/sim/rooftops.ts and all seven pass
it. What it cannot see is what a player sees: whether the roof is drawn under the tile you land on,
whether the obstacle can be clicked from where the last one put you, and where the client draws you
while the crossing plays out. CourseCam.java is the other half - the client's own renderer, logged
in to a local engine, driven by its own mouse listeners.

NOTHING IS HARDCODED HERE. The obstacle table is read out of Engine-TS/tools/sim/rooftops.ts, the
landings out of the content constants, the verbs out of the loc configs and the ids out of
content/pack/loc.pack - so the cam is always looking at what the build actually contains.

Needs an engine running locally on its own ports, from Engine-TS:
    NODE_PORT=43694 WEB_PORT=8694 WEB_MANAGEMENT_PORT=8794 npx tsx src/app.ts
and that engine's login key modulus in a file:
    node -e "const k=require('crypto').createPrivateKey(require('fs').readFileSync('data/config/login-rsa.pem'));
             console.log(BigInt('0x'+Buffer.from(k.export({format:'jwk'}).n,'base64url').toString('hex')).toString())" > rsan.txt

    python3 tools/clienttests/run_coursecam.py --rsan rsan.txt --out shots [--course falador]
    python3 tools/clienttests/run_coursecam.py --rsan rsan.txt --out shots --sheet   # contact sheets only
"""
import argparse
import os
import re
import subprocess
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))          # the javaclient repo
JAR = os.path.join(ROOT, 'build/libs/rs2client-dev.jar')

# The two sibling repos. A scratchpad worktree overrides them with --content / --engine.
CONTENT = os.path.join(os.path.dirname(ROOT), 'content')
ENGINE = os.path.join(os.path.dirname(ROOT), 'Engine-TS')


# ---------------------------------------------------------------- the obstacle table
def read_courses(engine):
    """Engine-TS/tools/sim/courses.ts's COURSES array, as a list of dicts.

    One table, three readers: that file, rooftops.ts (the engine-side suite) and this cam. It used
    to live inside rooftops.ts, so that is still tried as a fallback."""
    for rel in ('tools/sim/courses.ts', 'tools/sim/rooftops.ts'):
        path = os.path.join(engine, rel)
        if os.path.exists(path):
            src = open(path, encoding='utf-8').read()
            if 'COURSES' in src and 'obstacles:' in src:
                break
    out = []
    for m in re.finditer(r"\{\s*key:\s*'(\w+)',\s*(?:\w+:\s*[^,\n]+,\s*)*?title:\s*(?:'([^']*)'|\"([^\"]*)\"),\s*"
                         r"(?:\w+:\s*[^,\n]+,\s*)*?level:\s*(\d+),\s*lapXp:\s*(\d+),\s*start:\s*\[(\d+),\s*(\d+),\s*(\d+)\],\s*"
                         r"obstacles:\s*\[(.*?)\n\s*\]", src, re.S):
        key, t1, t2, level, lapxp, sx, sz, sl, body = m.groups()
        obs = []
        for o in re.finditer(r"\{\s*name:\s*'([^']*)',\s*loc:\s*'(\w+)',\s*x:\s*(\d+),\s*z:\s*(\d+),\s*"
                             r"level:\s*(\d+),\s*land:\s*'(\w+)',\s*xp:\s*'(\w+)'", body):
            obs.append(dict(name=o.group(1), loc=o.group(2), x=int(o.group(3)), z=int(o.group(4)),
                            level=int(o.group(5)), land=o.group(6), xp=o.group(7)))
        out.append(dict(key=key, title=t1 or t2, level=int(level), lapXp=int(lapxp),
                        start=(int(sx), int(sz), int(sl)), obstacles=obs))
    return out


def read_constants(content, course):
    """^<course>_* -> value, with coords flattened to absolute (x, z, level)."""
    path = os.path.join(content, 'scripts/skill_agility/configs', course + '_course.constant')
    out = {}
    for line in open(path, encoding='utf-8'):
        line = line.split('//')[0].strip()
        m = re.match(r'^\^(\w+)\s*=\s*(\d+)_(\d+)_(\d+)_(\d+)_(\d+)$', line)
        if m:
            pl, mx, mz, lx, lz = (int(m.group(i)) for i in range(2, 7))
            out[m.group(1)] = (mx * 64 + lx, mz * 64 + lz, pl)
            continue
        m = re.match(r'^\^(\w+)\s*=\s*(-?\d+)$', line)
        if m:
            out[m.group(1)] = int(m.group(2))
    return out


def read_verbs(content):
    """loc debugname -> [op1 text, width, length], over every agility loc config.

    The footprint comes along because the cam aims at the MIDDLE of it: a four-tile tree clicked at
    its origin corner is aimed at its edge, where the model above is branches and sky."""
    cfg = os.path.join(content, 'scripts/skill_agility/configs')
    out = {}
    for f in sorted(os.listdir(cfg)):
        if not f.endswith('.loc'):
            continue
        cur = None
        for line in open(os.path.join(cfg, f), encoding='utf-8'):
            line = line.split('//')[0].strip()
            m = re.match(r'^\[(\w+)\]$', line)
            if m:
                cur = m.group(1)
                out[cur] = ['Cross', 1, 1]
                continue
            if not cur or '=' not in line:
                continue
            k, v = line.split('=', 1)
            if k == 'op1':
                out[cur][0] = v.strip()
            elif k == 'width':
                out[cur][1] = int(v.strip())
            elif k == 'length':
                out[cur][2] = int(v.strip())
    return out


def read_pack(content):
    """loc debugname -> packed id."""
    out = {}
    for line in open(os.path.join(content, 'pack/loc.pack'), encoding='utf-8'):
        line = line.strip()
        if '=' in line:
            i, name = line.split('=', 1)
            out[name] = int(i)
    return out


def write_table(path, course, consts, verbs, pack):
    """The tsv CourseCam reads: one header line, then one line per obstacle."""
    with open(path, 'w', encoding='utf-8') as f:
        f.write('# written by run_coursecam.py from rooftops.ts, the course constants and the loc configs\n')
        f.write('course\t%s\t%d\t%d\t%d\t%d\n' % (course['key'], course['level'],
                                                  course['start'][0], course['start'][1], course['start'][2]))
        missing = []
        for o in course['obstacles']:
            land = consts.get(o['land'])
            pid = pack.get(o['loc'])
            info = verbs.get(o['loc'], ['Cross', 1, 1])
            verb = info[0].split()[0]
            if land is None or pid is None:
                missing.append(o['name'] + (' (no landing constant)' if land is None else ' (not in loc.pack)'))
                continue
            f.write('%s\t%d\t%d\t%d\t%d\t%d\t%d\t%d\t%s\t%d\t%d\t%d\n' % (
                o['name'], pid, o['x'], o['z'], o['level'], land[0], land[1], land[2], verb,
                consts.get(o['xp'], 0), info[1], info[2]))
        return missing


# ---------------------------------------------------------------- the contact sheet
def sheet(out, course, shots):
    """One picture of the whole lap: the at-rest frame of every obstacle, captioned."""
    try:
        from PIL import Image, ImageDraw
    except ImportError:
        print('   (no PIL, skipping the contact sheet)')
        return None
    cells = []
    for name, path in shots:
        if os.path.exists(path):
            cells.append((name, Image.open(path).convert('RGB')))
    if not cells:
        return None
    cw, ch = 420, 315
    cols = 4
    rows = (len(cells) + cols - 1) // cols
    pad = 22
    img = Image.new('RGB', (cols * cw, rows * (ch + pad)), (16, 16, 16))
    d = ImageDraw.Draw(img)
    for i, (name, im) in enumerate(cells):
        im = im.resize((cw, ch), Image.LANCZOS)
        x = (i % cols) * cw
        y = (i // cols) * (ch + pad)
        img.paste(im, (x, y + pad))
        d.text((x + 4, y + 6), name[:64], fill=(230, 230, 230))
    path = os.path.join(out, course + '_sheet.png')
    img.save(path)
    print('   contact sheet: ' + path)
    return path


# ---------------------------------------------------------------- the run
def java(out, classes, rsan, port, webport, user, table, props=(), home=None):
    # ONE CACHE FOLDER, SHARED BY EVERY RUN. The client builds its own cache under user.home on
    # first use, and until it has, the scene is empty: a run with a fresh home teleports fine,
    # photographs a blank world, and reports every obstacle as unclickable because there is no
    # terrain to pick. So the home lives beside the output rather than inside it.
    home = home or os.path.join(os.path.dirname(os.path.abspath(out)), 'coursecam_home')
    os.makedirs(home, exist_ok=True)
    cmd = ['java', '-Dshots.rsan=' + rsan, '-Dlostcity.host=127.0.0.1', '-Dlostcity.port=%d' % port,
           '-Dlostcity.webport=%d' % webport, '-Duser.home=' + home] + ['-D' + p for p in props]
    cmd += ['-cp', JAR + os.pathsep + classes, 'CourseCam', out, user, table]
    r = subprocess.run(cmd, capture_output=True, text=True, cwd=out)
    for l in r.stdout.split('\n'):
        if l.strip().startswith(('ok', 'FAIL')) or l.startswith((' ', '=')) or ' ok, ' in l:
            print('   ' + l)
    if r.returncode != 0 and 'FAIL' not in r.stdout:
        print(r.stdout[-3000:] + r.stderr[-2000:])
    return r.stdout


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--rsan', required=True)
    ap.add_argument('--out', required=True)
    ap.add_argument('--course', help='one course key (default: all of them)')
    ap.add_argument('--content', default=CONTENT)
    ap.add_argument('--engine', default=ENGINE)
    ap.add_argument('--jar')
    ap.add_argument('--port', type=int, default=43694)
    ap.add_argument('--webport', type=int, default=8694)
    ap.add_argument('--user', help='account name (default: a fresh one per run)')
    ap.add_argument('--sheet', action='store_true', help='rebuild the contact sheets from shots already taken')
    ap.add_argument('--home', help="the client's cache folder (default: coursecam_home beside --out)")
    a = ap.parse_args()
    global JAR
    if a.jar:
        JAR = os.path.abspath(a.jar)
    out = os.path.abspath(a.out)
    os.makedirs(out, exist_ok=True)
    content = os.path.abspath(a.content)
    engine = os.path.abspath(a.engine)

    courses = read_courses(engine)
    if a.course:
        courses = [c for c in courses if c['key'] == a.course]
        if not courses:
            print('no such course in rooftops.ts: ' + a.course)
            return 1
    verbs = read_verbs(content)
    pack = read_pack(content)

    if a.sheet:
        for c in courses:
            shots = [('%d %s' % (i + 1, o['name']), os.path.join(out, '%s_%dc_after.png' % (c['key'], i + 1)))
                     for i, o in enumerate(c['obstacles'])]
            sheet(out, c['key'], shots)
        return 0

    rsan = open(a.rsan).read().strip()
    classes = os.path.join(out, 'classes')
    os.makedirs(classes, exist_ok=True)
    r = subprocess.run(['javac', '-nowarn', '-encoding', 'UTF-8', '-cp', JAR, '-d', classes,
                        os.path.join(HERE, 'CourseCam.java')], capture_output=True, text=True)
    if r.returncode != 0:
        print(r.stderr)
        return 1

    bad = 0
    for c in courses:
        print(c['title'] + ' (%d obstacles)' % len(c['obstacles']))
        consts = read_constants(content, c['key'])
        table = os.path.join(out, c['key'] + '.tsv')
        missing = write_table(table, c, consts, verbs, pack)
        for m in missing:
            print('   SKIPPED ' + m)
        user = a.user or ('cam' + str(int(time.time()) % 100000))
        sout = java(out, classes, rsan, a.port, a.webport, user, table, home=a.home)
        if 'FAIL' in sout:
            bad += 1
        shots = [('%d %s' % (i + 1, o['name']), os.path.join(out, '%s_%dc_after.png' % (c['key'], i + 1)))
                 for i, o in enumerate(c['obstacles'])]
        sheet(out, c['key'], shots)
    print()
    print('ALL PASS' if bad == 0 else '%d course(s) with failures above' % bad)
    return 0 if bad == 0 else 1


sys.exit(main())
