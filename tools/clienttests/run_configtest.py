#!/usr/bin/env python3
"""Headless test for the config editors: colour and choices.

Every plugin's colour setting is six characters of text that might be anything, and parseColour
runs from render, per frame - an exception out of a draw turns the plugin off. The round trip
matters as much: the swatch shows parseColour(stored) and the picker writes toHex(picked), so if
those disagree a player picks a colour and gets a different one back.

The choices rule errs the other way: a value stored by an older release, for a mode that no longer
exists, is KEPT rather than silently rewritten.

Needs no display and no network.

    python3 tools/clienttests/run_configtest.py
"""
import os
import shutil
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
SRC = os.path.join(ROOT, 'src/main/java')
LAUNCHER_SRC = os.path.join(ROOT, 'launcher/src')
TEST = os.path.join(HERE, 'ConfigTest.java')


def main():
    javac = shutil.which('javac')
    java = shutil.which('java')
    if not javac or not java:
        raise SystemExit('run_configtest: needs javac and java on PATH')

    work = tempfile.mkdtemp(prefix='configtest')
    try:
        sources = []
        for where in (SRC, LAUNCHER_SRC):
            for root, _dirs, files in os.walk(where):
                sources += [os.path.join(root, f) for f in files if f.endswith('.java')]
        classes = os.path.join(work, 'classes')
        os.makedirs(classes)
        r = subprocess.run([javac, '-nowarn', '-encoding', 'UTF-8', '-d', classes] + sources,
                           capture_output=True, text=True)
        if r.returncode != 0:
            print(r.stderr[-4000:])
            raise SystemExit('run_configtest: the client does not compile')
        print('client compiles: %d sources' % len(sources))

        r = subprocess.run([javac, '-nowarn', '-encoding', 'UTF-8', '-cp', classes,
                            '-d', work, TEST], capture_output=True, text=True)
        if r.returncode != 0:
            print(r.stderr[-6000:])
            raise SystemExit('run_configtest: the test does not compile')

        # No lostcity.* properties on purpose: a plain launch is what a player gets, and a plain
        # launch is what was broken. dp.root is for the one check read out of the source.
        r = subprocess.run([java, '-Ddp.root=' + ROOT,
                            '-cp', classes + os.pathsep + work, 'jagex2.client.plugin.builtin.ConfigTest'],
                           capture_output=True, text=True, cwd=work)
        lines = [l for l in r.stdout.split('\n') if l.strip()]
        for l in lines:
            print(l)
        if not lines:
            print(r.stderr[-4000:])
            return 1
        return 0 if r.returncode == 0 else 1
    finally:
        shutil.rmtree(work, ignore_errors=True)


if __name__ == '__main__':
    sys.exit(main())
