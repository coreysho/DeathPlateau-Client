#!/usr/bin/env python3
"""Mutation test for tools/clienttests/run_mousetest.py.

Breaks the cursor accessors and the two plugins that use them one plausible way at a time, and
checks run_mousetest notices - and notices by NAMING a check, not by crashing.

The ones that matter most are around the shared pick slot. The scene answers exactly one "what
is at this screen point" question per frame and the client already uses it for walk-here, so
every way of sharing it wrong is here: a hover that walks the player, and a click that gets
swallowed. Both leave the client compiling and most of the game working.

    python3 tools/clienttests/mutate_mousetest.py [filter]
"""
import os
import subprocess
import sys

# THE WORKING TREE IS NEVER WRITTEN. Mutations go into a throwaway copy of the repository, so
# the tree stays clean and committable for the whole run and a kill at the worst moment leaves a
# broken file in a temp directory nobody builds from. It is a snapshot too: an edit to the tree
# mid-run cannot reach the run. mutate_guard.workspace has the reasoning, and its check() is the
# standalone pass that every pattern still matches its source exactly once.
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from mutate_guard import workspace  # noqa: E402  (after the sys.path line, necessarily)

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
CLIENT = os.path.join(ROOT, 'src/main/java/jagex2/client/Client.java')
CONTEXT = os.path.join(ROOT, 'src/main/java/jagex2/client/plugin/PluginContext.java')
MANAGER = os.path.join(ROOT, 'src/main/java/jagex2/client/plugin/PluginManager.java')
BUILTIN = os.path.join(ROOT, 'src/main/java/jagex2/client/plugin/builtin')
HIGHLIGHT = os.path.join(BUILTIN, 'MouseHighlightPlugin.java')
TILES = os.path.join(BUILTIN, 'TileIndicatorsPlugin.java')
MOUSE = os.path.join(ROOT, 'src/main/java/jagex2/client/plugin/builtin/MouseHighlightPlugin.java')
OVERLAY = os.path.join(ROOT, 'src/main/java/jagex2/client/plugin/OverlayGraphics.java')
RUNNER = os.path.join(HERE, 'run_mousetest.py')

MUTS = [
    # --- the shared pick slot ----------------------------------------------------------------
    (CLIENT, 'a plugin\'s pick left in place, so hovering walks the player there',
     '''		this.hoverTileX = World3D.clickTileX;
		this.hoverTileZ = World3D.clickTileZ;
		World3D.clickTileX = -1;
		World3D.clickTileZ = -1;''',
     '''		this.hoverTileX = World3D.clickTileX;
		this.hoverTileZ = World3D.clickTileZ;'''),
    (CLIENT, 'every pick taken as a plugin\'s, so clicking the ground never walks',
     'if (!this.hoverPickPending || World3D.clickTileX == -1) {',
     'if (World3D.clickTileX == -1) {'),
    (CLIENT, 'the request left armed, so the next real click is swallowed too',
     '''		World3D.clickTileZ = -1;
		this.hoverPickPending = false;
		return true;''',
     '''		World3D.clickTileZ = -1;
		return true;'''),
    (CLIENT, 'the walk-here branch no longer disowning the pick it just armed',
     '			this.hoverPickPending = false;\n\t\t}\n\t\tif (var5 == 903) {',
     '		}\n\t\tif (var5 == 903) {'),
    (CLIENT, 'a hover armed over the top of a pick something else wanted',
     "&& !World3D.field1044\n\t\t\t\t&& this.mouseInViewport()) {",
     "&& this.mouseInViewport()) {"),

    # --- the cursor ---------------------------------------------------------------------------
    (CLIENT, 'the cursor reported in window coordinates, not the viewport\'s',
     '''	public int viewportMouseX() {
		return super.mouseX - this.layout.vpX;
	}''',
     '''	public int viewportMouseX() {
		return super.mouseX;
	}'''),
    (CLIENT, 'the viewport test ignoring its far edge, so a cursor past it still reads',
     'return x >= 0 && y >= 0 && x < this.layout.openW && y < this.layout.openH;',
     'return x >= 0 && y >= 0;'),
    (CONTEXT, 'the cursor reported as its last position once it leaves the viewport',
     'return this.client.mouseInViewport() ? this.client.viewportMouseX() : -1;',
     'return this.client.viewportMouseX();'),

    # --- the lazy subscription ----------------------------------------------------------------
    (MANAGER, 'the hover answer asked for forever once asked for at all',
     'return !this.idle() && this.hoverWantedAt >= 0\n\t\t\t&& this.frame - this.hoverWantedAt <= HOVER_KEEPALIVE;',
     'return !this.idle() && this.hoverWantedAt >= 0;'),
    (MANAGER, 'the hover answer never asked for, so the tile is always stale',
     'void hoverTileRead() {\n\t\tthis.hoverWantedAt = this.frame;\n\t}',
     'void hoverTileRead() {\n\t}'),
    (MANAGER, 'the keepalive cut to nothing, so reading every other frame flickers',
     'private static final int HOVER_KEEPALIVE = 10;',
     'private static final int HOVER_KEEPALIVE = 0;'),
    (MANAGER, 'the answer still asked for with every plugin turned off',
     'return !this.idle() && this.hoverWantedAt >= 0',
     'return this.hoverWantedAt >= 0'),

    # --- Mouse highlight ----------------------------------------------------------------------
    # The old "label drawn for Walk here" mutation is gone: Walk here is a setting now, and
    # the two mutations below it - shown whether asked or not, and never shown - cover both
    # directions of what used to be one rule.
    (HIGHLIGHT, 'colour tags left in, so the label reads "Chop down <col=00ffff>Tree"',
     '		return strip(this.ctx.getMenuOption(index));',
     '		return this.ctx.getMenuOption(index);'),
    (HIGHLIGHT, 'only the first colour tag stripped',
     '''		int tag = option.indexOf('<');
		if (tag < 0) {
			return option;
		}''',
     '''		int tag = option.indexOf('<');
		if (tag < 0) {
			return option;
		}
		if (true) {
			int shut = option.indexOf('>');
			return shut < 0 ? option : option.substring(0, tag) + option.substring(shut + 1);
		}'''),
    (HIGHLIGHT, 'an unclosed tag eating the rest of the name',
     '''				out.append(option, open, option.length());
				break;''',
     '''				break;'''),
    # The colour parse moved to PluginConfig, so the mutation that broke it lives with it:
    # see mutate_configtest's "the length check dropped". Two suites mutating one file is
    # how a pattern goes stale unnoticed - and this one did, until mutate_guard's check
    # caught that it matched nothing and so tested nothing.
    (HIGHLIGHT, 'the label allowed off the right edge of the viewport',
     '''		if (left + wide + 4 > g.width()) {
			left = x - OFFSET_X - wide;
		}''', ''),
    (HIGHLIGHT, 'the label allowed off the bottom of the viewport',
     '''		if (baseline + 2 > g.height()) {
			baseline = y - OFFSET_Y + tall;
		}''', ''),
    (HIGHLIGHT, 'the label drawn with the cursor outside the game view',
     '''		if (x < 0 || y < 0) {
			return;
		}
''', ''),

    # --- Tile indicators ----------------------------------------------------------------------
    (TILES, 'the line never reaching its far end',
     '''			if (x0 == x1 && y0 == y1) {
				return i + 1;
			}''', '''			if (false) {
				return i + 1;
			}'''),
    (TILES, 'the line stepping only on one axis, so a diagonal comes out as a bar',
     '''			if (twice <= dx) {
				error += dx;
				y0 += stepY;
			}''', ''),
    # The cap is the only bound now. The old code also computed an exact step count alongside
    # it, which meant mutating either one left the other still stopping the loop - a bound that
    # cannot be broken on its own is a bound no test can check.
    (TILES, 'the line left unbounded, so a bad projection grinds inside the render loop',
     'for (int i = 0; i < MAX_STEPS; i++) {',
     'for (int i = 0; i < Integer.MAX_VALUE; i++) {'),
    # ---- TRANCHE THREE: the eight settings each on Tile indicators and Mouse highlight, the
    # shared font parse, and the span arithmetic a fill's shape comes out of.
    (MOUSE, '''the text colour left hardcoded, so the swatch does nothing''',
     '''		int colour = PluginConfig.parseColour(this.colour);''',
     '''		int colour = 0xFFFF00;'''),
    (MOUSE, '''the box colour left hardcoded''',
     '''PluginConfig.parseColour(this.backdropColour), alphaFor(this.backdropOpacity));''',
     '''PluginConfig.parseColour(DEFAULT_BACKDROP), alphaFor(this.backdropOpacity));'''),
    (MOUSE, '''the box opacity left hardcoded''',
     '''PluginConfig.parseColour(this.backdropColour), alphaFor(this.backdropOpacity));''',
     '''PluginConfig.parseColour(this.backdropColour), DEFAULT_BACKDROP_ALPHA);'''),
    (MOUSE, '''the border colour left hardcoded''',
     '''			g.box(left - 2, baseline - tall, wide + 4, tall + 3,
				PluginConfig.parseColour(this.borderColour));''',
     '''			g.box(left - 2, baseline - tall, wide + 4, tall + 3,
				PluginConfig.parseColour(DEFAULT_BORDER));'''),
    (MOUSE, '''the box drawn whether the player asked for it or not''',
     '''		if (this.boxed) {''',
     '''		if (true) {'''),
    (MOUSE, '''the box never drawn, so the switch does nothing''',
     '''		if (this.boxed) {''',
     '''		if (false) {'''),
    (MOUSE, '''the opacity floor removed, so a negative wraps to something opaque''',
     '''		if (alpha < MIN_ALPHA) {
			return MIN_ALPHA;
		}
''',
     ''''''),
    (MOUSE, '''the opacity ceiling removed''',
     '''		return alpha > MAX_ALPHA ? MAX_ALPHA : alpha;''',
     '''		return alpha;'''),
    (MOUSE, '''the text outline drawn whether the player asked for it or not''',
     '''		if (this.textOutline) {''',
     '''		if (true) {'''),
    (MOUSE, '''the text outline never drawn''',
     '''		if (this.textOutline) {''',
     '''		if (false) {'''),
    (MOUSE, '''the size ignored, so the drop-down does nothing''',
     '''		g.setFont(OverlayGraphics.fontFor(this.font));''',
     '''		g.setFont(OverlayGraphics.FONT_NORMAL);'''),
    (MOUSE, '''Walk here shown whether the player asked for it or not, on every empty tile''',
     '''if (index < 0 || (!this.showWalkHere && this.ctx.isWalkHere(index))) {''',
     '''if (index < 0) {'''),
    (MOUSE, '''Walk here never shown, so the setting does nothing''',
     '''if (index < 0 || (!this.showWalkHere && this.ctx.isWalkHere(index))) {''',
     '''if (index < 0 || this.ctx.isWalkHere(index)) {'''),
    (MOUSE, '''the label drawn over an open menu, describing a click nobody is about to make''',
     '''		if (this.ctx.isMenuOpen()) {
			return;
		}
''',
     ''''''),
    (MOUSE, '''the label hidden whether a menu is open or not''',
     '''		if (this.ctx.isMenuOpen()) {''',
     '''		if (true) {'''),
    (CONTEXT, '''isMenuOpen always false, so an overlay never stands aside''',
     '''		return this.client.menuVisible;''',
     '''		return false;'''),
    (OVERLAY, '''the bold choice falling through to the fallback''',
     '''		if (FONT_CHOICE_BOLD.equals(choice)) {
			return FONT_BOLD;
		}
''',
     ''''''),
    (OVERLAY, '''the small choice falling through to the fallback''',
     '''return FONT_CHOICE_SMALL.equals(choice) ? FONT_SMALL : FONT_NORMAL;''',
     '''return FONT_NORMAL;'''),
    (OVERLAY, '''an unreadable size throwing instead of falling back, out of a render loop''',
     '''		if (FONT_CHOICE_BOLD.equals(choice)) {''',
     '''		if (choice.equals(FONT_CHOICE_BOLD)) {'''),
    (OVERLAY, '''a choice offered that no branch matches, so one size silently does nothing''',
     '''FONT_CHOICE_NORMAL, FONT_CHOICE_BOLD, FONT_CHOICE_SMALL''',
     '''FONT_CHOICE_NORMAL, "Huge", FONT_CHOICE_SMALL'''),
    (TILES, '''the border width ignored, so the setting does nothing''',
     '''		int width = borderFor(this.borderWidth);''',
     '''		int width = 1;'''),
    (TILES, '''the fill opacity ignored''',
     '''		int alpha = fillFor(this.fillOpacity);''',
     '''		int alpha = DEFAULT_FILL;'''),
    (TILES, '''your own tile filled whether the player asked for it or not''',
     '''			if (this.currentFill) {''',
     '''			if (true) {'''),
    (TILES, '''your own tile never filled, so the switch does nothing''',
     '''			if (this.currentFill) {''',
     '''			if (false) {'''),
    (TILES, '''the outline drawn under its own fill rather than on top of it''',
     '''			if (this.currentFill) {
				fillTile(this.ctx, g, tx, tz, colour, alpha);
			}
			outlineTile(this.ctx, g, tx, tz, colour, width);''',
     '''			outlineTile(this.ctx, g, tx, tz, colour, width);
			if (this.currentFill) {
				fillTile(this.ctx, g, tx, tz, colour, alpha);
			}'''),
    (TILES, '''a zero opacity drawing a fill anyway''',
     '''		if (alpha <= MIN_FILL || !ctx.isInScene(sceneTileX, sceneTileZ)) {''',
     '''		if (!ctx.isInScene(sceneTileX, sceneTileZ)) {'''),
    (TILES, '''the opacity floor removed, so a negative wraps to something solid''',
     '''		if (alpha < MIN_FILL) {
			return MIN_FILL;
		}
''',
     ''''''),
    (TILES, '''the opacity ceiling removed''',
     '''		return alpha > MAX_FILL ? MAX_FILL : alpha;''',
     '''		return alpha;'''),
    (TILES, '''the border floor removed, so a zero is no outline at all''',
     '''		if (width < MIN_BORDER) {
			return MIN_BORDER;
		}
''',
     ''''''),
    (TILES, '''the border ceiling removed''',
     '''		return width > MAX_BORDER ? MAX_BORDER : width;''',
     '''		return width;'''),
    (TILES, '''a horizontal edge on the row ignored, so every tile's top row is one pixel wide''',
     '''			if (y0 == y1) {
				if (y0 != y) {
					continue;
				}''',
     '''			if (y0 == y1) {
				continue;
			}
			if (false) {'''),
    (TILES, '''a horizontal edge contributing one end, so the top row is half a tile''',
     '''best = wantLeft ? Math.min(best, Math.min(x0, x1)) : Math.max(best, Math.max(x0, x1));''',
     '''best = wantLeft ? Math.min(best, x0) : Math.max(best, x0);'''),
    (TILES, '''the divide-by-zero guard removed, so a tile seen edge-on throws from a render''',
     '''			if (y0 == y1) {''',
     '''			if (false) {'''),
    (TILES, '''the row-range test dropped, so every edge crosses every row''',
     '''			if (y < Math.min(y0, y1) || y > Math.max(y0, y1)) {
				continue;
			}
''',
     ''''''),
    (TILES, '''the interpolation along an edge inverted, so a fill is drawn mirrored''',
     '''			int at = x0 + (y - y0) * (x1 - x0) / (y1 - y0);''',
     '''			int at = x1 - (y - y0) * (x1 - x0) / (y1 - y0);'''),
    (TILES, '''left and right the same, so a fill is one pixel wide''',
     '''			best = wantLeft ? Math.min(best, at) : Math.max(best, at);''',
     '''			best = Math.min(best, at);'''),
    (TILES, '''an empty row given a span, so a fill bleeds past the tile''',
     '''			if (left <= right) {''',
     '''			if (true) {'''),
    (TILES, '''the scanline height unbounded, so an absurd projection fills the whole frame''',
     '''		if (bottom - top > MAX_STEPS) {
			return;
		}
''',
     ''''''),
    (TILES, '''the corners not shared, so the outline and the fill disagree about the tile''',
     '''		if (!corners(ctx, sceneTileX, sceneTileZ, xs, ys)) {
			return;
		}
		int top = ys[0];''',
     '''		corners(ctx, sceneTileX, sceneTileZ, xs, ys);
		int top = ys[0];'''),

]


def main():
    only = sys.argv[1] if len(sys.argv) > 1 else None
    orig = {}
    for path in (CLIENT, CONTEXT, MANAGER, HIGHLIGHT, TILES, MOUSE, OVERLAY):
        with open(path, encoding='utf-8', newline='') as f:
            orig[path] = f.read()
    # Written into a copy, never into the working tree - see mutate_guard.workspace.
    _work, inside = workspace('mousetest')
    muts = [m for m in MUTS if not only or only in m[1]]
    print('running %d of %d mutations' % (len(muts), len(MUTS)))
    bad = loose = 0
    for path, why, find, repl in muts:
        n = orig[path].count(find)
        if n != 1:
            print('  SKIP (pattern %s) %s'
                  % ('not found' if n == 0 else 'matches %d times' % n, why))
            bad += 1
            continue
        try:
            with open(inside(path), 'w', encoding='utf-8', newline='') as f:
                f.write(orig[path].replace(find, repl))
            r = subprocess.run([sys.executable, inside(RUNNER)], capture_output=True, text=True,
                               timeout=600)
        except subprocess.TimeoutExpired:
            print('  %-5s %-74s %s' % ('HUNG', why, 'the suite never finished - not a catch'))
            loose += 1
            continue
        finally:
            with open(inside(path), 'w', encoding='utf-8', newline='') as f:
                f.write(orig[path])
        fired = [l.strip()[5:].strip() for l in r.stdout.split('\n') if l.startswith('FAIL')]
        if r.returncode == 0:
            print('  %-5s %-74s %s' % ('GREEN', why, 'NOT CAUGHT'))
            bad += 1
        elif fired:
            print('  %-5s %-74s %s' % ('red', why, 'caught by: ' + fired[0][:44]))
        else:
            print('  %-5s %-74s %s' % ('red', why,
                  'caught, but by a non-zero exit with no check named - a crash is not a catch'))
            loose += 1
    print()
    if bad:
        print('%d MUTATIONS SURVIVED OR SKIPPED' % bad)
    elif loose:
        print('every mutation was caught, but %d only by a crash' % loose)
    else:
        print('every mutation was caught, each by a named check')
    return 1 if bad or loose else 0


if __name__ == '__main__':
    sys.exit(main())
