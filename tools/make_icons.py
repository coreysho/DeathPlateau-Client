#!/usr/bin/env python3
"""Regenerates the client's icons from the badge in tools/art.

The icons are committed, because a build should not need Pillow and a release should not depend
on an image library being installable that morning. This is how they were made, so that changing
the artwork is a command rather than an archaeology exercise.

    pip install pillow && python3 tools/make_icons.py

WHAT IT WRITES

    src/main/resources/deathplateau/icon-<n>.png   the window icons, read by lostcity.Branding and
                                                   shipped in both jars

WHY THE SMALL SIZES ARE A DIFFERENT PICTURE. Below about 32 pixels the whole badge - the
wordmark, the castle, the ravine - is unreadable, so those entries are a tight crop of the skull.
What a 16 pixel icon has to do is be recognisable in a taskbar, not be complete.

IT USED TO WRITE A .ICO TOO, for a Windows .exe wrapper around the launcher. That was built and
dropped: an unsigned wrapped exe draws a SmartScreen warning that reads as "this is a virus" to
anyone downloading a game client. If it ever comes back - which needs a code-signing certificate
to be worth doing - the recipe is in the commit that added it, along with the one trap worth
remembering: Pillow caps every ICO entry at the BASE image's size, so the largest has to be
passed first or the file silently ends up with one 16x16 entry that Windows scales to everything.
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
SOURCE = os.path.join(HERE, 'art', 'death-plateau-badge.webp')

# The skull, crossed swords and crown, without the wordmark underneath them.
EMBLEM = (549, 276, 733, 460)

# Sizes a desktop asks for: setIconImages hands the window manager the set and it picks per use.
PNG_SIZES = (16, 24, 32, 48, 64, 128)
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

    return 0


if __name__ == '__main__':
    sys.exit(main())
