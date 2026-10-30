#!/usr/bin/env python3
"""Headless test for the world map renderer.

The map is drawn with the CLIENT'S OWN COLOURS - WorldMapRender drives World3D.method309, the
same call the minimap goes through - so the colours are not this test's business. What is, is
the layout, and every way of getting that wrong gives you a map that looks completely right:
north and south swapped, x and z packed the wrong way round so the world is transposed, the
light wandering a shade per square until one end of the world is a different colour from the
other.

Needs no display and no network. It DOES need the cache, because the checks that matter most
are the ones that render real ground: that moving the square inside the client's scene does not
change the picture, that two renders of a square agree pixel for pixel, and that the ground runs
on across a square boundary, which is the only honest way to tell a map from its mirror image.

    python3 tools/clienttests/run_worldmaptest.py [path/to/data/pack]

DP_PACK names it too, which is how the mutation suite reaches it: the suite runs this against a
copy of the repository in a temp directory, where guessing at a sibling checkout finds nothing.
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
RENDER = os.path.join(HERE, 'WorldMapRender.java')
TEST = os.path.join(HERE, 'WorldMapTest.java')

# WHERE THE CACHE USUALLY IS. The engine packs it and .gitignore keeps it out of the repository,
# so there is nothing to check in and nothing to download: it is beside the checkout or it is
# named on the command line.
GUESSES = [
    os.environ.get('DP_PACK'),
    os.path.join(os.path.dirname(ROOT), 'DeathPlateau-Engine/data/pack'),
    os.path.join(ROOT, '../DeathPlateau-Engine/data/pack'),
]


def find_pack(argv):
    for where in list(argv) + GUESSES:
        if where and os.path.exists(os.path.join(where, 'main_file_cache.idx4')):
            return os.path.abspath(where)
    return None


def main():
    javac = shutil.which('javac')
    java = shutil.which('java')
    if not javac or not java:
        raise SystemExit('run_worldmaptest: needs javac and java on PATH')

    pack = find_pack(sys.argv[1:])
    if not pack:
        raise SystemExit('run_worldmaptest: no cache found. Pack the engine (npm run build in '
                         'DeathPlateau-Engine), or pass the path to its data/pack, or set '
                         'DP_PACK.')

    work = tempfile.mkdtemp(prefix='worldmaptest')
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
            raise SystemExit('run_worldmaptest: the client does not compile')
        print('client compiles: %d sources' % len(sources))

        # Into `classes` so the test shares the package and can reach the renderer's statics
        # without any of them having to be public.
        r = subprocess.run([javac, '-nowarn', '-encoding', 'UTF-8', '-cp', classes,
                            '-d', classes, RENDER, TEST], capture_output=True, text=True)
        if r.returncode != 0:
            print(r.stderr[-6000:])
            raise SystemExit('run_worldmaptest: the test does not compile')

        bad = 0
        # TWICE, THE SECOND TIME COLD. The models and the caches LocType keeps of the models it
        # has built are static tables, so once the suite has filled them, main can skip loading
        # them and still draw a square with every building on it. A second JVM is the only way
        # to see what main finds when it is the first thing to run.
        for cold in (False, True):
            cmd = [java, '-Djava.awt.headless=true', '-Xmx1g', '-Ddp.pack=' + pack]
            if cold:
                cmd.append('-Ddp.coldstart=true')
            cmd += ['-cp', classes, 'jagex2.client.WorldMapTest']
            r = subprocess.run(cmd, capture_output=True, text=True, cwd=work)
            lines = [l for l in r.stdout.split('\n') if l.strip()]
            for l in lines:
                print(l)
            if not lines:
                print(r.stderr[-4000:])
                return 1
            if r.returncode != 0 and not any(l.startswith('FAIL') for l in lines):
                # NON-ZERO WITH NO FAILURE NAMED means the suite died part way through, and the
                # reason is on stderr. Printing stdout alone here showed a run that simply
                # stopped mid-section with nothing to say why, which cost an afternoon once.
                print(r.stderr[-4000:])
            if r.returncode != 0:
                bad = 1
        return bad
    finally:
        shutil.rmtree(work, ignore_errors=True)


if __name__ == '__main__':
    sys.exit(main())
