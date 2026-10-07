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


def launcher_step():
    """The shell block of the workflow's "Build launcher" step, verbatim."""
    with open(WORKFLOW, encoding='utf-8') as f:
        text = f.read()
    m = re.search(r'- name: Build launcher\n\s+run: \|\n((?:[ \t]+.*\n)+)', text)
    if not m:
        raise SystemExit('run_launchertest: no "Build launcher" step in release.yml')
    lines = m.group(1).rstrip('\n').split('\n')
    indent = min(len(l) - len(l.lstrip()) for l in lines if l.strip())
    return '\n'.join(l[indent:] for l in lines)


def main():
    if not shutil.which('javac') or not shutil.which('java') or not shutil.which('jar'):
        raise SystemExit('run_launchertest: needs javac, java and jar on PATH')

    step = launcher_step()
    print('running the workflow\'s own build step:')
    for line in step.split('\n'):
        print('    ' + line)
    print()

    work = tempfile.mkdtemp(prefix='launchertest')
    try:
        # Only what the step touches, so a step that reaches for something else fails here
        # rather than quietly picking it up from a full checkout.
        shutil.copytree(os.path.join(ROOT, 'launcher/src'), os.path.join(work, 'launcher/src'))
        shutil.copytree(os.path.join(ROOT, 'src/main/resources'),
                        os.path.join(work, 'src/main/resources'))

        r = subprocess.run(['bash', '-e', '-c', step], capture_output=True, text=True, cwd=work)
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
    finally:
        shutil.rmtree(work, ignore_errors=True)

    print()
    print('ALL PASS' if not fails else '%d FAILED' % fails)
    return 1 if fails else 0


if __name__ == '__main__':
    sys.exit(main())
