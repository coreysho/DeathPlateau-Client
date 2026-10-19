#!/usr/bin/env python3
"""Sizes the DP sigil for the login screen and writes it where the client reads it.

The master is tools/art/source/dp-sigil-512.png, the mark as it was drawn. This only resizes it
and flattens it onto black.

WHY ONTO BLACK RATHER THAN KEPT TRANSPARENT. Pix32 - the client's sprite - has no alpha channel.
It carries one transparent colour, and that colour is 0, pure black. So a PNG flattened onto
black arrives with its own background already transparent, and the crimson bloom around the ring
fades into the page instead of ending at a box edge. It only works because the login screen's
ground is black too; on any other colour this would need a different approach.

    python3 tools/art/make_sigil.py
    python3 tools/art/make_sigil.py --size 468 --out /tmp/check.png
"""
import argparse
import os

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
SRC = os.path.join(HERE, 'source/dp-sigil-512.png')
OUT = os.path.join(ROOT, 'src/main/resources/deathplateau/sigil.png')


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--size', type=int, default=468, help='square edge in pixels')
    ap.add_argument('--out', default=OUT)
    args = ap.parse_args()

    im = Image.open(SRC).convert('RGBA')
    flat = Image.new('RGBA', im.size, (0, 0, 0, 255))
    flat = Image.alpha_composite(flat, im).convert('RGB')
    flat = flat.resize((args.size, args.size), Image.LANCZOS)

    d = os.path.dirname(args.out)
    if d and not os.path.isdir(d):
        os.makedirs(d)
    flat.save(args.out, optimize=True)
    print('%s  %dx%d' % (args.out, args.size, args.size))


if __name__ == '__main__':
    main()
