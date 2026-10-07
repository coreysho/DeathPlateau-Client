#!/usr/bin/env python3
"""Headless test for which endpoint the cache is fetched from.

A player could not get past the loading screen - "connection problem - Will retry in 15 secs." -
while the same build worked for the server's owner. That message is getJagCrc failing to fetch
the cache checksums, and its retry loop alternates between HTTP and JAGGRAB. The JAGGRAB half
was hardcoded to port 43595 on SERVER_HOST: a port no tunnel forwards, on the GAME host rather
than the web one. On the LAN both mistakes cancel out, which is why it went unnoticed.

Needs no display and no network: the rule is a pure function and the rest is read out of the
source. Deliberately run with NO lostcity.* properties, because the default launch is the case
that was broken.

    python3 tools/clienttests/run_jaggrabtest.py
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
TEST = os.path.join(HERE, 'JaggrabTest.java')


def main():
    javac = shutil.which('javac')
    java = shutil.which('java')
    if not javac or not java:
        raise SystemExit('run_jaggrabtest: needs javac and java on PATH')

    work = tempfile.mkdtemp(prefix='jaggrabtest')
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
            raise SystemExit('run_jaggrabtest: the client does not compile')
        print('client compiles: %d sources' % len(sources))

        r = subprocess.run([javac, '-nowarn', '-encoding', 'UTF-8', '-cp', classes,
                            '-d', work, TEST], capture_output=True, text=True)
        if r.returncode != 0:
            print(r.stderr[-6000:])
            raise SystemExit('run_jaggrabtest: the test does not compile')

        # No lostcity.* properties on purpose: a plain launch is what a player gets, and a plain
        # launch is what was broken. dp.root is for the one check read out of the source.
        r = subprocess.run([java, '-Ddp.root=' + ROOT,
                            '-cp', classes + os.pathsep + work, 'jagex2.client.JaggrabTest'],
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
