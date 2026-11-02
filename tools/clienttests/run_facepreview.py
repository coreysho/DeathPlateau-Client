#!/usr/bin/env python3
"""Draws a saved profile's head the way the login screen does, against a real cache.

WHY. The login screen's portraits were blank, and three readings of the source produced three
wrong answers - a stale scanline table, a camera distance, a capture that was working all along.
run_titlepreview cannot settle it: it has no cache by design, so it can draw the frame and never
what goes in it. This loads the packed cache, builds the head through the client's own
getHeadModel, and draws it with the same call at a grid of eye distances and vertical lifts, so
the numbers in TitleScreen are chosen by looking at them.

It is what found the actual fault: getHeadModel merges the parts and recolours them and stops,
leaving the face arrays null, and a model with no face arrays draws nothing at all. The picture
was eight empty boxes until calculateNormals was called.

    python3 tools/clienttests/run_facepreview.py [out-dir] [path/to/data/pack] [look]

`look` is the eighteen numbers from a profile's look= line in profiles.properties. Without one it
builds a face from the first usable identikit of each body part, which is nobody's character and
is still enough to aim a camera at a head.
"""
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
CLASSES = os.path.join(ROOT, 'build/classes/java/main')
OUT = os.path.join(ROOT, 'build/facepreview')

PACKS = [
    os.environ.get('DP_PACK'),
    os.path.join(os.path.dirname(ROOT), 'Engine-TS/data/pack'),
    os.path.join(os.path.dirname(ROOT), 'DeathPlateau-Engine/data/pack'),
    os.path.join(os.path.dirname(ROOT), 'engine/data/pack'),
]


def find_pack(argv):
    for where in ([argv[0]] if argv else []) + PACKS:
        if where and os.path.exists(os.path.join(where, 'main_file_cache.idx1')):
            return where
    return None


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else OUT
    pack = find_pack(sys.argv[2:3])
    if not pack:
        raise SystemExit('run_facepreview: no packed cache found. Build the engine, pass its '
                         'data/pack, or set DP_PACK.')
    if not os.path.isdir(CLASSES):
        raise SystemExit('run_facepreview: no compiled client. Run ./gradlew compileJava first.')

    classes = os.path.join(out, 'classes')
    os.makedirs(classes, exist_ok=True)
    sources = [os.path.join(HERE, name) for name in ('FacePreview.java', 'WorldMapRender.java')]
    subprocess.run(['javac', '-nowarn', '-encoding', 'UTF-8', '-cp', CLASSES, '-d', classes]
                   + sources, check=True)

    cmd = ['java', '-Xmx1g', '-cp', os.pathsep.join([CLASSES, classes]),
           'jagex2.client.FacePreview', pack, out]
    if len(sys.argv) > 3:
        cmd.append(sys.argv[3])
    subprocess.run(cmd, check=True)


if __name__ == '__main__':
    main()
