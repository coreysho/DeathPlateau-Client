#!/usr/bin/env python3
"""Renders the DEATH PLATEAU wordmark and writes it where the client reads it.

WHY A PNG AND NOT A FONT IN THE CLIENT. The 377 client draws text with its own bitmap fonts out
of the cache; it has no way to load a TrueType face. The wordmark is therefore artwork, baked
once here, and the client blits it like any other sprite.

THE LICENCE. The face is Curse of the Zombie, whose own metadata reads "Free for personal use
ONLY" - and a client distributed to players is not personal use, so the rendered wordmark may
only ship under a commercial licence. One was obtained on 2026-10-19; see
tools/art/source/FONT-LICENCE.md, which is also where the purchase record belongs. If it ever
lapses or the face is replaced, drop the new file in under the same name and re-run this - the
client loads a picture and knows nothing about fonts.

THE U IS DRAWN 1.25x LARGER THAN THE REST, and that is a correction, not a flourish. Measured
from the font's own glyf table, its cap top is 1807 units where its neighbours are 2223 to 2360,
and it is the narrowest glyph in the word - so set flat it reads as a typo. 2250/1807 = 1.245,
and rendering the word at 1.00, 1.15, 1.25 and 1.35 to look at confirmed 1.25: below it the U is
visibly short, above it overshoots the A and E either side.

The colour is sampled from the sigil, not matched by eye - 16851 stone pixels of the mark gave
the highlight, light and mid below. The shadow tone it also has (#594742) is deliberately unused:
at 2.3:1 on black the bottoms of the letters go muddy.

    python3 tools/art/make_wordmark.py
    python3 tools/art/make_wordmark.py --preview /tmp/check.png --size 120
"""
import argparse
import os

from PIL import Image, ImageDraw, ImageFilter, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
FONT = os.path.join(HERE, 'source/curse-of-the-zombie.ttf')
OUT = os.path.join(ROOT, 'src/main/resources/deathplateau/wordmark.png')

WORD = 'DEATH PLATEAU'
U_SCALE = 1.25        # see the header: the font's U is 20% short of its neighbours
TRACK = 0.11          # letter spacing as a fraction of the size
SS = 4                # supersample for the mask, downscaled before compositing

STONE = ((0xE2, 0xDF, 0xE2), (0xA1, 0x9F, 0xA1), (0x79, 0x6D, 0x6C))
STONE_MID = 0.46
# The mockup's two shadows, and nothing else. An earlier pass added a solid crimson rim and a
# dark outline "to match the sigil" - they were never in the approved design and they turned a
# stone wordmark into a glowing one.
CRIMSON = (0xE0, 0x1B, 0x24)    # drop-shadow(0 0 17px rgba(224,27,36,0.26))
CRIMSON_ALPHA = 0.26
CRIMSON_BLUR = 8.5              # css blur radius 17px is about twice the gaussian sigma
SHADOW_ALPHA = 0.95             # drop-shadow(0 2px 2px rgba(0,0,0,0.95))
SHADOW_BLUR = 1.0
SHADOW_DROP = 2


def stone_column(h):
    """Top-lit highlight -> light -> mid, one column stretched across the word."""
    col = Image.new('RGB', (1, h))
    px = col.load()
    for y in range(h):
        t = y / float(max(1, h - 1))
        if t <= STONE_MID:
            u, a, b = t / STONE_MID, STONE[0], STONE[1]
        else:
            u, a, b = (t - STONE_MID) / (1 - STONE_MID), STONE[1], STONE[2]
        px[0, y] = tuple(int(a[i] + (b[i] - a[i]) * u) for i in range(3))
    return col


def text_mask(size, pad):
    """The word as a white-on-black mask, every glyph on one baseline."""
    big = size * SS
    f = ImageFont.truetype(FONT, big)
    fu = ImageFont.truetype(FONT, int(big * U_SCALE))
    track = TRACK * big

    def font_for(i):
        return fu if i == len(WORD) - 1 else f

    width = sum(font_for(i).getlength(c) + track for i, c in enumerate(WORD)) - track

    # EVERY GLYPH ON ONE BASELINE, which is what anchor 'ls' means and why it is not optional
    # here. PIL's default anchor places text by the top of the ASCENDER box, and the U is drawn
    # from a larger font whose ascender box is taller - so drawn that way the U comes out the
    # right size but sitting some nine pixels low, hanging under the word. Measuring the shipped
    # PNG is what found it: height right at 100% of its neighbours, top 26 against their 17.
    boxes = [font_for(i).getbbox(c, anchor='ls') for i, c in enumerate(WORD) if c != ' ']
    above = min(b[1] for b in boxes)        # negative: how far the tallest glyph rises
    below = max(b[3] for b in boxes)        # positive: how far the lowest spike drops

    w = int(width + pad * 2 * SS)
    h = int(below - above + pad * 2 * SS)
    baseline = pad * SS - above
    mask = Image.new('L', (w, h), 0)
    dr = ImageDraw.Draw(mask)
    x = pad * SS
    for i, c in enumerate(WORD):
        use = font_for(i)
        dr.text((x, baseline), c, font=use, fill=255, anchor='ls')
        x += use.getlength(c) + track
    return mask.resize((w // SS, h // SS), Image.LANCZOS)


def render(size, pad=14):
    mask = text_mask(size, pad)
    w, h = mask.size
    out = Image.new('RGB', (w, h), (0, 0, 0))

    # The crimson bloom sits furthest back and is faint, the dark shadow sits just behind the
    # letters, and the stone face goes on top. Both are the mockup's own filter values scaled
    # from its 44px to whatever size is asked for.
    unit = size / 44.0

    bloom = mask.filter(ImageFilter.GaussianBlur(CRIMSON_BLUR * unit))
    out = Image.composite(Image.new('RGB', (w, h), CRIMSON), out,
                          bloom.point(lambda v: int(v * CRIMSON_ALPHA)))

    shadow = Image.new('L', (w, h), 0)
    shadow.paste(mask, (0, int(round(SHADOW_DROP * unit))))
    shadow = shadow.filter(ImageFilter.GaussianBlur(max(0.5, SHADOW_BLUR * unit)))
    out = Image.composite(Image.new('RGB', (w, h), (0, 0, 0)), out,
                          shadow.point(lambda v: int(v * SHADOW_ALPHA)))

    out = Image.composite(stone_column(h).resize((w, h)), out, mask)
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--size', type=int, default=44, help='point size of the body letters')
    ap.add_argument('--preview', help='write here instead of the client resource')
    args = ap.parse_args()

    img = render(args.size)
    dest = args.preview or OUT
    d = os.path.dirname(dest)
    if d and not os.path.isdir(d):
        os.makedirs(d)
    img.save(dest, optimize=True)
    print('%s  %dx%d' % (dest, img.size[0], img.size[1]))


if __name__ == '__main__':
    main()
