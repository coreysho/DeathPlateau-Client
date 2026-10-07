#!/usr/bin/env python3
"""Mutation test for tools/clienttests/run_dragtest.py.

Breaks Alt-drag one plausible way at a time and checks run_dragtest notices - and notices by
NAMING a check, not by crashing.

This feature is almost all off-by-one and which-coordinate-space arithmetic, applied to every
pixel every overlay draws. That is the kind of code where a mistake is invisible in one place and
obvious in another: an offset applied to the box but not to the text, a grab that jumps the
overlay's corner to the cursor, a clamp measured from the offset instead of from where the overlay
actually is. Every mutation below leaves the client compiling and drawing.

    python3 tools/clienttests/mutate_dragtest.py [filter]
"""
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
PLUGIN = os.path.join(ROOT, 'src/main/java/jagex2/client/plugin')
GRAPHICS = os.path.join(PLUGIN, 'OverlayGraphics.java')
MANAGER = os.path.join(PLUGIN, 'PluginManager.java')
POSITIONS = os.path.join(PLUGIN, 'OverlayPositions.java')
RUNNER = os.path.join(HERE, 'run_dragtest.py')

MUTS = [
    # --- the translation itself -------------------------------------------------------------
    (GRAPHICS, 'the offset never applied, so nothing moves at all',
     '''	private int tx(int x) {
		return x + this.offsetX;
	}''',
     '''	private int tx(int x) {
		return x;
	}'''),
    (GRAPHICS, 'the offset applied the wrong way, so an overlay runs away from the cursor',
     '''	private int ty(int y) {
		return y + this.offsetY;
	}''',
     '''	private int ty(int y) {
		return y - this.offsetY;
	}'''),
    (GRAPHICS, 'the offset left set between overlays, so one drags all of them',
     '''		this.offsetX = offsetX;
		this.offsetY = offsetY;''', ''),
    # A fill that moves while its border does not is a box drawn in two places.
    (GRAPHICS, 'a filled rectangle drawn unmoved while everything round it moves',
     '		Pix2D.fillRect(height, this.ty(y), colour, width, this.tx(x));',
     '		Pix2D.fillRect(height, y, colour, width, x);'),

    # --- the bounds the hit test uses -------------------------------------------------------
    (GRAPHICS, 'the bounds never reset, so they grow to cover everything ever drawn',
     '''		this.left = Integer.MAX_VALUE;
		this.top = Integer.MAX_VALUE;
		this.right = Integer.MIN_VALUE;
		this.bottom = Integer.MIN_VALUE;''', ''),
    (GRAPHICS, 'the bounds recorded before the offset, so the grab area stays behind',
     '''		this.mark(this.tx(x), this.ty(y), width, height);
		Pix2D.fillRect(height, this.ty(y), colour, width, this.tx(x));''',
     '''		this.mark(x, y, width, height);
		Pix2D.fillRect(height, this.ty(y), colour, width, this.tx(x));'''),
    # NOT MUTATED: hasBounds() returning true for an overlay that drew nothing cannot be
    # observed. The empty box is MAX_VALUE..MIN_VALUE, so the hit test rejects it either way -
    # x >= MAX_VALUE is false for every x. The guard is still right, and still cheap, but no
    # test can tell it apart from its absence, so pretending one does would be theatre.
    # Text is drawn from its baseline. Reading it as a top-left puts the grab area a whole line
    # below the words.
    (GRAPHICS, 'text measured from its top instead of its baseline',
     '		this.mark(this.tx(x), this.ty(y) - tall, wide, tall + 2);',
     '		this.mark(this.tx(x), this.ty(y), wide, tall + 2);'),

    # --- regions move with what was drawn ---------------------------------------------------
    (GRAPHICS, 'a clickable region left where the overlay used to be',
     'this.regions.add(this.tx(x), this.ty(y), width, height, onClick, null, this.owner);',
     'this.regions.add(x, y, width, height, onClick, null, this.owner);'),

    # --- the drag state machine -------------------------------------------------------------
    # NOT MUTATED EITHER: dropping the button check from the pickup is unobservable, because
    # the very next frame takes the "button != 1" branch and finishes the drag before anything
    # moves. The check stays because it says what the code means, not because a test can see it.
    (MANAGER, 'the hovered overlay ignored, so Alt drags whatever was last grabbed',
     '''			this.hovered = this.overlayAt(x, y);
			if (button == 1 && this.hovered != null) {''',
     '''			if (button == 1 && this.hovered != null) {'''),
    (MANAGER, 'the grab point ignored, so the overlay jumps its corner to the cursor',
     '''				this.dragGrabX = x - this.positions.x(this.dragging.positionKey);
				this.dragGrabY = y - this.positions.y(this.dragging.positionKey);''',
     '''				this.dragGrabX = 0;
				this.dragGrabY = 0;'''),
    (MANAGER, 'the drag never let go of, so the overlay follows the cursor for ever',
     '''		if (button != 1) {
			this.finishDrag(width, height);
			return;
		}''', ''),
    (MANAGER, 'dragging still live with Alt up, so the mouse drags things by accident',
     '''		if (!alt) {
			// Let go of whatever was held: releasing Alt mid-drag leaves it where it is rather
			// than snapping it back, which is what a player who changed their mind expects.
			if (this.dragging != null) {
				this.finishDrag(width, height);
			}
			this.dragMode = false;
			this.hovered = null;
			return;
		}''', ''),
    (MANAGER, 'the scene layer made draggable, so a world label can be pulled off its tile',
     'boolean movable = overlay.layer() == Overlay.LAYER_SCREEN;',
     'boolean movable = true;'),
    (MANAGER, 'the topmost overlay not preferred, so the one underneath is grabbed',
     'for (int i = this.overlays.size() - 1; i >= 0; i--) {',
     'for (int i = 0; i < this.overlays.size(); i++) {'),
    (MANAGER, 'the hit test off by one on the far edge',
     'if (x >= bounds[0] && x < bounds[2] && y >= bounds[1] && y < bounds[3]) {',
     'if (x >= bounds[0] && x <= bounds[2] + 40 && y >= bounds[1] && y <= bounds[3] + 40) {'),
    (MANAGER, 'the drag click handed to the overlay as well, so grabbing presses its buttons',
     'return !this.idle() && this.dragMode && this.overlayAt(x, y) != null;',
     'return false;'),

    # --- saving, snapping, resetting ---------------------------------------------------------
    (MANAGER, 'the drop never saved, so a layout is forgotten on relaunch',
     '''		this.positions.set(dropped.positionKey, x, y);''', ''),
    (MANAGER, 'the drop saved unsnapped, so an overlay can never be put back by hand',
     '''		int x = OverlayPositions.snap(this.positions.x(dropped.positionKey));
		int y = OverlayPositions.snap(this.positions.y(dropped.positionKey));''',
     '''		int x = this.positions.x(dropped.positionKey);
		int y = this.positions.y(dropped.positionKey);'''),
    (MANAGER, 'reset leaving the saved position behind',
     'this.positions.clear(this.entries.get(i).key + "#" + j);', ''),
    (MANAGER, 'the overlay key taken after the sort, so it changes with other plugins',
     '''			List<Overlay> own = entry.plugin.getOverlays();
			for (int j = 0; j < own.size(); j++) {
				own.get(j).positionKey = entry.key + "#" + j;
			}''',
     '''			List<Overlay> own = entry.plugin.getOverlays();
			for (int j = 0; j < own.size(); j++) {
				own.get(j).positionKey = entry.key + "#" + this.overlays.size();
			}'''),

    # --- keeping it on screen ----------------------------------------------------------------
    (POSITIONS, 'nothing kept on screen, so an overlay can be dragged away for good',
     '''		if (high < KEEP_VISIBLE) {
			return offset + (KEEP_VISIBLE - high);
		}
		if (low > available - KEEP_VISIBLE) {
			return offset - (low - (available - KEEP_VISIBLE));
		}
		return offset;''',
     '		return offset;'),
    (POSITIONS, 'only the left edge guarded, so the bottom right swallows an overlay',
     '''		if (low > available - KEEP_VISIBLE) {
			return offset - (low - (available - KEEP_VISIBLE));
		}
''', ''),
    (POSITIONS, 'the clamp measured from the offset rather than from where the overlay is',
     '''		int low = offset + edgeLow;
		int high = offset + edgeHigh;''',
     '''		int low = offset;
		int high = offset;'''),
    (POSITIONS, 'snapping anything, however far, back to home',
     'return offset > -SNAP && offset < SNAP ? 0 : offset;',
     'return 0;'),
    (POSITIONS, 'snapping nothing, so a near miss is kept',
     'return offset > -SNAP && offset < SNAP ? 0 : offset;',
     'return offset;'),
    (POSITIONS, 'a saved position read back as nothing, so a relaunch forgets the layout',
     '''		String value = this.store.get(key(overlayKey));
		if (value == null) {
			return 0;
		}''',
     '''		String value = this.store.get(key(overlayKey));
		if (value == null || true) {
			return 0;
		}'''),
    (POSITIONS, 'the two halves of a saved position swapped',
     '''			return Integer.parseInt((half == 0
				? value.substring(0, comma)
				: value.substring(comma + 1)).trim());''',
     '''			return Integer.parseInt((half == 0
				? value.substring(comma + 1)
				: value.substring(0, comma)).trim());'''),
    (POSITIONS, 'a drag in progress writing the file every frame',
     '''	void move(String overlayKey, int x, int y) {
		if (overlayKey != null) {
			this.store.put(key(overlayKey), x + "," + y);
		}
	}''',
     '''	void move(String overlayKey, int x, int y) {
		if (overlayKey != null) {
			this.store.put(key(overlayKey), x + "," + y);
			this.store.save();
		}
	}'''),
]


def main():
    only = sys.argv[1] if len(sys.argv) > 1 else None
    orig = {}
    for path in (GRAPHICS, MANAGER, POSITIONS):
        with open(path, encoding='utf-8', newline='') as f:
            orig[path] = f.read()
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
            with open(path, 'w', encoding='utf-8', newline='') as f:
                f.write(orig[path].replace(find, repl))
            r = subprocess.run([sys.executable, RUNNER], capture_output=True, text=True,
                               timeout=600)
        except subprocess.TimeoutExpired:
            print('  %-5s %-72s %s' % ('HUNG', why, 'the suite never finished - not a catch'))
            loose += 1
            continue
        finally:
            with open(path, 'w', encoding='utf-8', newline='') as f:
                f.write(orig[path])
        fired = [l.strip()[5:].strip() for l in r.stdout.split('\n') if l.startswith('FAIL')]
        if r.returncode == 0:
            print('  %-5s %-72s %s' % ('GREEN', why, 'NOT CAUGHT'))
            bad += 1
        elif fired:
            print('  %-5s %-72s %s' % ('red', why, 'caught by: ' + fired[0][:44]))
        else:
            print('  %-5s %-72s %s' % ('red', why,
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
