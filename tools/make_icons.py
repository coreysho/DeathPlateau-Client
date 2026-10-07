#!/usr/bin/env python3
"""Regenerates the client's icons from the badge in tools/art.

The icons are committed, because a build should not need Pillow and a release should not depend
on an image library being installable that morning. This is how they were made, so that changing
the artwork is a command rather than an archaeology exercise.

    pip install pillow && python3 tools/make_icons.py

WHAT IT WRITES

    src/main/resources/deathplateau/icon-<n>.png   the window icons, read by lostcity.Branding and
                                                   shipped in both jars
    launcher/launcher.ico                          the Windows file icon, embedded in the launcher
                                                   .exe by launch4j - a BUILD INPUT, deliberately
                                                   not under src/main/resources, because anything
                                                   there is copied into both jars and this is
                                                   169KB that neither of them would ever read

WHY THE SMALL SIZES ARE A DIFFERENT PICTURE. Below about 32 pixels the whole badge - the
wordmark, the castle, the ravine - is unreadable, so those entries are a tight crop of the skull.
What a 16 pixel icon has to do is be recognisable in a taskbar, not be complete.
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
SOURCE = os.path.join(HERE, 'art', 'death-plateau-badge.webp')

# The skull, crossed swords and crown, without the wordmark underneath them.
EMBLEM = (549, 276, 733, 460)

# Sizes a desktop asks for. setIconImages picks per use; Windows wants 256 for its largest view.
PNG_SIZES = (16, 24, 32, 48, 64, 128)
ICO_SIZES = (16, 24, 32, 48, 64, 128, 256)

# Below this, the emblem alone.
WHOLE_BADGE_FROM = 48


def main():
    try:
        from PIL import Image
    except ImportError:
        raise SystemExit('make_icons: needs Pillow (pip install pillow)')

    src = Image.open(SOURCE).convert('RGBA')
    emblem = src.crop(EMBLEM)

    def at(size):
        return (src if size >= WHOLE_BADGE_FROM else emblem).resize((size, size), Image.LANCZOS)

    out = os.path.join(ROOT, 'src/main/resources/deathplateau')
    if not os.path.exists(out):
        os.makedirs(out)
    for size in PNG_SIZES:
        path = os.path.join(out, 'icon-%d.png' % size)
        at(size).convert('RGB').save(path, optimize=True)
        print('%-52s %7d bytes' % (os.path.relpath(path, ROOT), os.path.getsize(path)))

    # Pillow caps every ICO entry at the BASE image's dimensions, so the base has to be the
    # largest and the rest go in append_images. Passing the 16 first silently writes a one-entry
    # file, which Windows then scales to everything.
    by_size = {s: at(s) for s in ICO_SIZES}
    biggest = max(ICO_SIZES)
    ico = os.path.join(ROOT, 'launcher/launcher.ico')
    by_size[biggest].save(ico, format='ICO', sizes=[(s, s) for s in ICO_SIZES],
                          append_images=[by_size[s] for s in ICO_SIZES if s != biggest])
    print('%-52s %7d bytes' % (os.path.relpath(ico, ROOT), os.path.getsize(ico)))

    got = sorted(Image.open(ico).ico.sizes())
    want = sorted((s, s) for s in ICO_SIZES)
    if got != want:
        raise SystemExit('make_icons: the .ico came out with %s, wanted %s' % (got, want))
    print('every size is in the .ico')
    return 0


if __name__ == '__main__':
    sys.exit(main())
