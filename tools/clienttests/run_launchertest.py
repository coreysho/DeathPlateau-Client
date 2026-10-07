#!/usr/bin/env python3
"""Builds Death-Plateau-Launcher.jar the way the release does, and checks what came out.

WHY THIS EXISTS. The launcher jar is built in exactly one place - the "Build launcher" step of
.github/workflows/release.yml - with javac and jar called by hand, outside gradle. Nothing else
builds it, so nothing else can be wrong about it until a release is already running, and a
release that publishes a broken launcher is a release every player's launcher then fetches.

So this does not reimplement those commands, it RUNS THEM: the step's shell block is read out of
the workflow and executed against a copy of the repo. A change to the workflow is a change to
what this tests, which is the only way a test of a build step stays true to it.

What it then checks is the thing that silently goes missing: the icons are resources, and a
resource that is not copied into the jar fails as a window with the default Java cup on it - no
error, no log line, nothing a build would notice.

    python3 tools/clienttests/run_launchertest.py
"""
import os
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
WORKFLOW = os.path.join(ROOT, '.github/workflows/release.yml')
JAR = 'Death-Plateau-Launcher.jar'
EXE = 'Death-Plateau-Launcher.exe'
ICO = os.path.join(ROOT, 'launcher/launcher.ico')
CONFIG = os.path.join(ROOT, 'launcher/launch4j.xml')
ICO_SIZES_WANTED = (16, 24, 32, 48, 64, 128, 256)
ICON_SIZES = (16, 24, 32, 48, 64, 128)

PROBE = '''public class Probe {
    public static void main(String[] args) {
        // Loaded through the jar's own classloader, from the jar's own resources - which is the
        // thing being tested. A count is enough; ImageIO already refused anything it could not
        // decode by returning null, and Branding drops those.
        System.out.println("icons=" + lostcity.Branding.icons().size());
        System.out.println("at16=" + (lostcity.Branding.iconAtLeast(16) != null));
    }
}
'''

fails = 0


def check(ok, what):
    global fails
    print(('  ok   ' if ok else 'FAIL   ') + what)
    if not ok:
        fails += 1


def workflow():
    with open(WORKFLOW, encoding='utf-8') as f:
        return f.read()


def step(name):
    """The shell block of a named step in release.yml, verbatim.

    Line by line rather than by regex: a run block ends at the first line indented no further
    than its own "run:", and a pattern loose enough to allow blank lines inside the block runs
    straight past that into the next step - which is how this first read the publish step's gh
    command as part of the build and tried to execute it.
    """
    lines = workflow().split('\n')
    at = None
    for i, line in enumerate(lines):
        if line.strip() == '- name: ' + name:
            at = i
            break
    if at is None:
        raise SystemExit('run_launchertest: no "%s" step in release.yml' % name)
    while at < len(lines) and not lines[at].strip().startswith('run:'):
        at += 1
    if at >= len(lines):
        raise SystemExit('run_launchertest: "%s" has no run: block' % name)
    opened = len(lines[at]) - len(lines[at].lstrip())
    body = []
    for line in lines[at + 1:]:
        if line.strip() and len(line) - len(line.lstrip()) <= opened:
            break
        body.append(line)
    while body and not body[-1].strip():
        body.pop()
    indent = min(len(l) - len(l.lstrip()) for l in body if l.strip())
    return '\n'.join(l[indent:] if l.strip() else '' for l in body)


def ico_sizes(path):
    """The sizes in an .ico, read from its directory. No Pillow: it is a six-byte header and a
    sixteen-byte entry each, and this test should not need an image library to run."""
    with open(path, 'rb') as f:
        head = f.read(6)
        if len(head) < 6 or head[:4] != b'\x00\x00\x01\x00':
            return []
        count = int.from_bytes(head[4:6], 'little')
        out = []
        for _ in range(count):
            entry = f.read(16)
            if len(entry) < 16:
                break
            # 0 in the width or height byte means 256.
            out.append((entry[0] or 256, entry[1] or 256))
        return sorted(out)



def windows_exe(work):
    """The .exe wrapper: the only way a Death Plateau icon gets onto a FILE in Explorer."""
    print()
    got = ico_sizes(ICO)
    want = sorted((s, s) for s in ICO_SIZES_WANTED)
    check(got == want, 'launcher.ico carries every size Windows asks for (%s)'
          % (', '.join('%d' % w for w, _h in got) or 'none'))

    with open(CONFIG, encoding='utf-8') as f:
        config = f.read()
    # launch4j resolves these against the CONFIG's directory, not the working directory, which
    # is the one thing about it that is easy to get wrong and silent when wrong.
    here = os.path.dirname(CONFIG)
    for tag, want_path in (('icon', ICO), ('jar', os.path.join(ROOT, JAR))):
        m = re.search(r'<%s>([^<]+)</%s>' % (tag, tag), config)
        resolved = os.path.normpath(os.path.join(here, m.group(1))) if m else None
        check(resolved == os.path.normpath(want_path),
              '<%s> resolves against the config\'s own directory to %s'
              % (tag, os.path.relpath(want_path, ROOT)))
    m = re.search(r'<outfile>([^<]+)</outfile>', config)
    outfile = os.path.basename(m.group(1)) if m else ''
    check(outfile == EXE, 'the exe is written as %s' % (outfile or 'nothing'))
    # The realistic failure is renaming one and not the other, leaving the release uploading a
    # file that was never built.
    check(EXE in workflow().split('- name: Publish')[-1],
          '...and the release publishes that exact name')

    l4j = os.environ.get('LAUNCH4J_JAR')
    if not l4j or not os.path.exists(l4j):
        print('  SKIP  launch4j not here, so the wrap itself is unchecked - set LAUNCH4J_JAR to '
              'the launch4j.jar from launch4j-3.50-linux-x64.tgz to run it')
        return
    # The real thing: run the workflow's wrap step, minus the download, and look at what came out.
    wrap = [l for l in step('Wrap the launcher for Windows').split('\n')
            if l.strip() and not l.strip().startswith('#')
            and 'curl' not in l and 'sha256sum' not in l and 'tar xzf' not in l
            and not l.strip().startswith('"https')]
    wrap = [l.replace('java -jar launch4j/launch4j.jar', 'java -jar ' + l4j) for l in wrap]
    shutil.copytree(os.path.join(ROOT, 'launcher'), os.path.join(work, 'launcher'),
                    dirs_exist_ok=True)
    r = subprocess.run(['bash', '-e', '-c', '\n'.join(wrap)], capture_output=True, text=True,
                       cwd=work)
    exe = os.path.join(work, EXE)
    if r.returncode != 0 or not os.path.exists(exe):
        print(r.stdout[-1500:])
        print(r.stderr[-1500:])
        check(False, 'the wrap step produces ' + EXE)
        return
    check(True, 'the wrap step produces ' + EXE)
    with open(exe, 'rb') as f:
        blob = f.read()
    check(blob[:2] == b'MZ', '...a real Windows executable, not a renamed jar')
    pe = int.from_bytes(blob[0x3C:0x40], 'little')
    check(blob[pe:pe + 4] == b'PE\0\0', '...with a PE header')
    subsystem = int.from_bytes(blob[pe + 4 + 20 + 68:pe + 4 + 20 + 70], 'little')
    check(subsystem == 2, '...built as a GUI app, so no console window flashes up (%d)' % subsystem)
    with open(ICO, 'rb') as f:
        ico = f.read()
    check(ico[len(ico) // 2:len(ico) // 2 + 64] in blob, '...carrying our icon')
    with zipfile.ZipFile(exe) as z:
        inside = z.namelist()
    check('lostcity/Launcher.class' in inside and 'deathplateau/icon-16.png' in inside,
          '...and the whole launcher jar embedded in it, so it is one self-contained download')


def main():
    if not shutil.which('javac') or not shutil.which('java') or not shutil.which('jar'):
        raise SystemExit('run_launchertest: needs javac, java and jar on PATH')

    build = step('Build launcher')
    print('running the workflow\'s own build step:')
    for line in build.split('\n'):
        print('    ' + line)
    print()

    work = tempfile.mkdtemp(prefix='launchertest')
    try:
        # Only what the step touches, so a step that reaches for something else fails here
        # rather than quietly picking it up from a full checkout.
        shutil.copytree(os.path.join(ROOT, 'launcher/src'), os.path.join(work, 'launcher/src'))
        shutil.copytree(os.path.join(ROOT, 'src/main/resources'),
                        os.path.join(work, 'src/main/resources'))

        r = subprocess.run(['bash', '-e', '-c', build], capture_output=True, text=True, cwd=work)
        if r.returncode != 0:
            print(r.stdout[-2000:])
            print(r.stderr[-3000:])
            raise SystemExit('run_launchertest: the release\'s own launcher build failed')
        jar = os.path.join(work, JAR)
        check(os.path.exists(jar), 'the step produces ' + JAR)
        if not os.path.exists(jar):
            return 1

        with zipfile.ZipFile(jar) as z:
            names = z.namelist()
            manifest = z.read('META-INF/MANIFEST.MF').decode('utf-8', 'replace')
        check('lostcity/Launcher.class' in names, '...with the launcher in it')
        check('Main-Class: lostcity.Launcher' in manifest,
              '...and a Main-Class, so a double-click runs it')
        check('lostcity/Branding.class' in names,
              '...and Branding, which only reaches this jar because it lives in launcher/src')
        missing = [s for s in ICON_SIZES if 'deathplateau/icon-%d.png' % s not in names]
        check(not missing, 'every icon size is in the jar (missing: %s)'
              % (', '.join(str(s) for s in missing) if missing else 'none'))

        # The names being right is not the same as the bytes being readable. Run against the
        # shipped jar and nothing else, which is the classpath a player's launcher has.
        probe_dir = os.path.join(work, 'probe')
        os.makedirs(probe_dir)
        with open(os.path.join(probe_dir, 'Probe.java'), 'w', encoding='utf-8') as f:
            f.write(PROBE)
        r = subprocess.run(['javac', '-nowarn', '-cp', jar, '-d', probe_dir,
                            os.path.join(probe_dir, 'Probe.java')], capture_output=True, text=True)
        if r.returncode != 0:
            print(r.stderr[-2000:])
            raise SystemExit('run_launchertest: the probe does not compile against the jar')
        r = subprocess.run(['java', '-Djava.awt.headless=true',
                            '-cp', os.pathsep.join([jar, probe_dir]), 'Probe'],
                           capture_output=True, text=True)
        said = r.stdout.strip()
        check('icons=%d' % len(ICON_SIZES) in said,
              'the launcher decodes all %d of them out of its own jar (%s)'
              % (len(ICON_SIZES), said.replace('\n', ', ') or r.stderr[-200:]))
        check('at16=true' in said, '...and finds one for the title bar')

        # And that the launcher actually puts them on its window, which no jar listing can show.
        with open(os.path.join(ROOT, 'launcher/src/lostcity/Launcher.java'), encoding='utf-8') as f:
            source = f.read()
        check('frame.setIconImages(Branding.icons());' in source,
              'the launcher window is given them')

        # THE OTHER COPY, which is the one most players will actually run. A launcher downloaded
        # months ago hands over to the newer one inside client.jar and runs it from there, so the
        # icons have to be in THAT jar too or the hand-over lands on an iconless window.
        client = os.path.join(ROOT, 'build/libs/rs2client-dev.jar')
        if not os.path.exists(client):
            print('  SKIP  no build/libs/rs2client-dev.jar - run gradlew build to check the '
                  'hand-over copy too')
        else:
            with zipfile.ZipFile(client) as z:
                inside = z.namelist()
            check('lostcity/Branding.class' in inside and 'lostcity/Launcher.class' in inside,
                  'the client jar carries the launcher and its icon loader')
            gone = [s for s in ICON_SIZES if 'deathplateau/icon-%d.png' % s not in inside]
            check(not gone, '...and every icon, so a handed-over launcher has them (missing: %s)'
                  % (', '.join(str(s) for s in gone) if gone else 'none'))

        windows_exe(work)
    finally:
        shutil.rmtree(work, ignore_errors=True)

    print()
    print('ALL PASS' if not fails else '%d FAILED' % fails)
    return 1 if fails else 0


if __name__ == '__main__':
    sys.exit(main())
