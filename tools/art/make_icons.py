#!/usr/bin/env python3
"""Builds the window icons from the DP sigil, and writes them where Branding reads them.

WHY SIX, AND WHY THE SMALL ONES ARE A DIFFERENT PICTURE. setIconImages hands the window manager
a set and it picks per use - 16 in a title bar, 32 or 48 in the taskbar and alt-tab, 128 for a
large-icon view. Given one image it scales, and below about 32 pixels the full mark is a smudge:
the ragged ring is a few pixels of broken red and the DP inside it loses its counters. So the
small sizes are a tight crop of the monogram with the ring dropped, which is a legible shape at
16 pixels, and the large ones are the whole mark. The icon set this replaces was built on the
same reasoning for the same reason.

THEY ARE A BADGE ON BLACK, NOT A CUT-OUT MARK, and that was not the first attempt. Cutting the
sigil out the way the login screen does - alpha from each pixel's brightest channel, colour
unpremultiplied - is correct over black and only over black: the mid-grey stone comes out at
about half alpha, so on a light taskbar the whole mark bleaches to a faint red scribble. A
contact sheet on both grounds is what showed it; on black alone it looked right.

So the artwork keeps the black it was drawn on, and the ALPHA IS THE BADGE SHAPE instead - a
rounded square, opaque inside, nothing outside. That reads as an application icon on any
desktop, dark or light, and it keeps the crimson bloom intact rather than clipping it at a
threshold. The set this replaces was a circular badge for the same reason.

    python3 tools/art/make_icons.py
    python3 tools/art/make_icons.py --sheet /tmp/icons.png     # a contact sheet to look at
"""
import argparse
import os

from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
SRC = os.path.join(HERE, 'source/dp-sigil-512.png')
OUT_DIR = os.path.join(ROOT, 'src/main/resources/deathplateau')

SIZES = (16, 24, 32, 48, 64, 128)
FULL_FROM = 128         # this size and up get the whole mark, below it the cropped monogram

# The monogram inside the master, as a fraction of its edge. The ring lives outside this box;
# the spikes above and below the letters are kept, because they are most of what makes the
# silhouette read as ours rather than as two grey letters.
CROP = (0.205, 0.165, 0.795, 0.835)


RADIUS = 0.22           # corner radius as a fraction of the icon's edge


def badge(im, size):
    """The artwork on its black, masked to a rounded square. Opaque inside, nothing outside."""
    art = im.convert('RGB').resize((size, size), Image.LANCZOS)
    # Masked at 4x and scaled down, so the corners are smooth rather than stepped.
    ss = 4
    mask = Image.new('L', (size * ss, size * ss), 0)
    ImageDraw.Draw(mask).rounded_rectangle(
        (0, 0, size * ss - 1, size * ss - 1), radius=int(RADIUS * size * ss), fill=255)
    out = art.convert('RGBA')
    out.putalpha(mask.resize((size, size), Image.LANCZOS))
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--sheet', help='also write a contact sheet of every size')
    ap.add_argument('--out-dir', default=OUT_DIR)
    args = ap.parse_args()

    if not os.path.isdir(args.out_dir):
        os.makedirs(args.out_dir)

    master = Image.open(SRC).convert('RGB')
    w, h = master.size
    box = (int(CROP[0] * w), int(CROP[1] * h), int(CROP[2] * w), int(CROP[3] * h))
    cropped = master.crop(box)

    made = []
    for size in SIZES:
        source = master if size >= FULL_FROM else cropped
        icon = badge(source, size)
        path = os.path.join(args.out_dir, 'icon-%d.png' % size)
        icon.save(path, optimize=True)
        made.append((size, icon, path))
        print('%s  %dx%d  %s' % (path, size, size, 'full mark' if size >= FULL_FROM else 'monogram'))

    if args.sheet:
        # On two grounds, because an icon that only works on one is an icon that is half broken.
        pad = 12
        width = sum(s + pad for s, _, _ in made) + pad
        sheet = Image.new('RGBA', (width, 128 * 2 + pad * 3), (0, 0, 0, 255))
        sheet.paste(Image.new('RGBA', (width, 128 + pad), (236, 236, 238, 255)), (0, 128 + pad * 2))
        x = pad
        for size, icon, _ in made:
            sheet.alpha_composite(icon, (x, pad + (128 - size)))
            sheet.alpha_composite(icon, (x, 128 + pad * 2 + (128 - size)))
            x += size + pad
        sheet.convert('RGB').save(args.sheet)
        print('sheet -> %s' % args.sheet)


if __name__ == '__main__':
    main()
