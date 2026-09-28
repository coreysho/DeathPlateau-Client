#!/usr/bin/env python3
"""Checks the launcher's hand-over: launcher/src/lostcity/Launcher.java handOver/stampIn, driven by
HandOverTest.java.

WHY IT MATTERS. The launcher is the one piece that cannot fix itself - a player runs the jar they
downloaded once, so a launcher that will not start the game strands them. It now steps aside for the
newer launcher inside client.jar (the client jar carries one; see build.gradle), which is what lets a
launcher change reach players at all.

What it proves: the version stamped into a compiled Launcher.class is read back out of a real jar
(the class is never loaded), a jar with no launcher in it reads as none, and it hands over ONLY to a
higher version - never to the same one, never back to an older one, never when there is no client.jar,
and never twice (the handed-over launcher carries lostcity.launcherhandoff). Nothing is started: every
case here decides "stay put".

Needs a built client jar, for the stamp it really carries: gradlew build, then

    python3 tools/clienttests/run_handovertest.py
"""
import os
import shutil
import subprocess
import sys
import tempfile
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
SRC = os.path.join(ROOT, 'launcher', 'src')
TEST = os.path.join(HERE, 'HandOverTest.java')
CLIENT_JAR = os.path.join(ROOT, 'build', 'libs', 'rs2client-dev.jar')


def run_javac(args):
    if '-d' in args:
        os.makedirs(args[args.index('-d') + 1], exist_ok=True)  # javac will not make it itself
    r = subprocess.run([shutil.which('javac'), '-nowarn', '-encoding', 'UTF-8'] + args, capture_output=True, text=True)
    if r.returncode != 0:
        raise SystemExit('javac failed:\n' + (r.stdout + r.stderr).strip())


def sources(root):
    out = []
    for base, _, files in os.walk(root):
        out += [os.path.join(base, f) for f in files if f.endswith('.java')]
    return out


def build_launcher(work, version, into):
    """The launcher compiled at a given version, as a jar - to stand in for one inside a client jar."""
    src = os.path.join(work, 'src%d' % version)
    shutil.copytree(SRC, src)
    path = os.path.join(src, 'lostcity', 'Launcher.java')
    text = open(path, encoding='utf-8').read()
    marked = text.replace('private static final int VERSION = ', '// test build\n    private static final int VERSION = %d; //' % version, 1)
    assert marked != text, 'Launcher.java has no VERSION to set'
    open(path, 'w', encoding='utf-8').write(marked)
    classes = os.path.join(work, 'cls%d' % version)
    run_javac(['-d', classes] + sources(src))
    with zipfile.ZipFile(into, 'w') as z:
        for base, _, files in os.walk(classes):
            for f in files:
                full = os.path.join(base, f)
                z.write(full, os.path.relpath(full, classes).replace(os.sep, '/'))


def main():
    if not os.path.isfile(CLIENT_JAR):
        print('no %s - run gradlew build first' % CLIENT_JAR)
        return 1
    work = tempfile.mkdtemp(prefix='handover')
    try:
        older = os.path.join(work, 'launcher-older.jar')
        newer = os.path.join(work, 'launcher-newer.jar')
        build_launcher(work, 1, older)
        build_launcher(work, 99, newer)
        classes = os.path.join(work, 'test')
        run_javac(['-d', classes] + sources(SRC) + [TEST])
        sandbox = os.path.join(work, 'home')
        os.makedirs(sandbox)
        r = subprocess.run([shutil.which('java'), '-cp', classes, 'HandOverTest', sandbox, CLIENT_JAR, older, newer],
                           capture_output=True, text=True)
        print(r.stdout.strip())
        if r.stderr.strip():
            print(r.stderr.strip(), file=sys.stderr)
        return r.returncode
    finally:
        shutil.rmtree(work, ignore_errors=True)


if __name__ == '__main__':
    sys.exit(main())
