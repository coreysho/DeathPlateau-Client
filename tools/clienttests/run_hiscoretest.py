#!/usr/bin/env python3
"""Headless test for the hiscore panel.

The panel says what the website's /hiscores/lookup says about a player. Reading that answer is a
function with no client and no network under it, so it is fed a body here: skills into the right
stat icons, experience past two billion, a 404 told apart from a 503, minutes as hours.

Needs no display, no network and no cache.

    python3 tools/clienttests/run_hiscoretest.py
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
TEST = os.path.join(HERE, 'HiscoreTest.java')


def main():
    javac = shutil.which('javac')
    java = shutil.which('java')
    if not javac or not java:
        raise SystemExit('run_hiscoretest: needs javac and java on PATH')

    work = tempfile.mkdtemp(prefix='hiscoretest')
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
            raise SystemExit('run_hiscoretest: the client does not compile')
        print('client compiles: %d sources' % len(sources))

        # Into `classes` so the test shares the package and can reach LootTracker and the
        # plugin's page methods without any of them having to be public.
        r = subprocess.run([javac, '-nowarn', '-encoding', 'UTF-8', '-cp', classes,
                            '-d', classes, TEST], capture_output=True, text=True)
        if r.returncode != 0:
            print(r.stderr[-6000:])
            raise SystemExit('run_hiscoretest: the test does not compile')

        r = subprocess.run([java, '-Djava.awt.headless=true',
                            # cwd is the temp work dir, so the source checks need to be told
                            # where the repository is.
                            '-Ddp.root=' + ROOT,
                            '-cp', classes,
                            'jagex2.client.plugin.builtin.HiscoreTest'],
                           capture_output=True, text=True, cwd=work)
        lines = [l for l in r.stdout.split('\n') if l.strip()]
        for l in lines:
            print(l)
        if not lines:
            print(r.stderr[-4000:])
            return 1
        if r.returncode != 0 and not any(l.startswith('FAIL') for l in lines):
            # NON-ZERO WITH NO FAILURE NAMED means the suite died part way through, and the
            # reason is on stderr. Printing stdout alone here showed a run that simply stopped
            # mid-section with nothing to say why, which cost an afternoon once.
            print(r.stderr[-4000:])
        return 0 if r.returncode == 0 else 1
    finally:
        shutil.rmtree(work, ignore_errors=True)


if __name__ == '__main__':
    sys.exit(main())
