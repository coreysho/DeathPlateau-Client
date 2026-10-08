#!/usr/bin/env python3
"""Mutation test for tools/clienttests/run_groundtest.py.

Breaks the Ground items plugin one plausible way at a time and checks that run_groundtest notices
- and notices by NAMING a check, not by crashing. A test that only goes red because something
threw is not measuring the thing it claims to.

    python3 tools/clienttests/mutate_groundtest.py [filter]
"""
import os
import subprocess
import sys

# THE SOURCE GOES BACK EVEN IF THIS PROCESS IS KILLED. The `finally` below covers a run that
# fails or times out; a SIGTERM skips it entirely, and a kill once left a mutation sitting in
# the tree where the next commit would have shipped it. mutate_guard also has the standalone
# check that every pattern still matches its source exactly once.
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from mutate_guard import guard  # noqa: E402  (after the sys.path line, necessarily)

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
PLUGIN = os.path.join(ROOT, 'src/main/java/jagex2/client/plugin/builtin/GroundItemsPlugin.java')
PALETTE = os.path.join(ROOT, 'src/main/java/jagex2/client/plugin/builtin/GroundItemPalette.java')
OVERLAY = os.path.join(ROOT, 'src/main/java/jagex2/client/plugin/OverlayGraphics.java')
ARRIVALS = os.path.join(ROOT, 'src/main/java/jagex2/client/plugin/builtin/GroundItemArrivals.java')
ITEM = os.path.join(ROOT, 'src/main/java/jagex2/client/plugin/GroundItem.java')
PREFS = os.path.join(ROOT, 'src/main/java/jagex2/client/GroundItemPrefs.java')
RUNNER = os.path.join(HERE, 'run_groundtest.py')

MUTS = [
    # The bug this feature actually had: the column positioned from how many items are on the
    # tile while only the visible ones are drawn, so a hidden row leaves a hole under the pile.
    (PLUGIN, 'the column laid out from the tracked count rather than the visible rows',
     'int shown = Math.min(visible, ROWS_SHOWN);',
     'int shown = Math.min(pile.items.size(), ROWS_SHOWN);'),
    (PLUGIN, 'the column grown downwards from the tile instead of up to it',
     'int rowY = originY - (shown - 1) * ROW_H;',
     'int rowY = originY;'),

    # The three rules.
    (PLUGIN, 'hidden items drawn whether or not anything is revealing them',
     '''			if (!reveal) {
				return 0;
			}''',
     ''),
    (PLUGIN, 'highlighted items made to obey the value floor after all',
     'if (GroundItemPrefs.isHighlighted(item.name)) {',
     'if (GroundItemPrefs.isHighlighted(item.name) && item.worth() >= floor) {'),
    # ---- PER-ITEM COLOURS. A rule's own colour has to win, and the arrays have to stay paired.
    (PLUGIN, "a rule's own colour ignored, so setting one does nothing",
     'return own == GroundItemPrefs.DEFAULT_COLOUR ? palette.highlighted : own;',
     'return palette.highlighted;'),
    (PLUGIN, "a hidden rule's own colour ignored",
     'return own == GroundItemPrefs.DEFAULT_COLOUR ? palette.hidden : own;',
     'return palette.hidden;'),
    (PREFS, '''a new rule added by toggle left at the zero-filled colour, so its item silently disappears''',
     '''public static boolean toggle(String item, int mode) {
		ensure();
		if (item == null || item.length() == 0) {
			return false;
		}
		int at = find(item);
		if (at >= 0) {
			if (modes[at] == mode) {
				removeAt(at);
			} else {
				modes[at] = mode;       // hidden <-> highlighted, rather than a second contradictory rule
				save();
			}
			return true;
		}
		if (count >= MAX) {
			return false;
		}
		names[count] = item;
		modes[count] = mode;
		// EXPLICITLY, because an int[] is zero-filled and 0 is the "do not draw this row" answer
		// colourFor gives - so a new rule left at 0 is a rule whose item silently disappears.
		// That is what happened the first time this change ran against the tests, in both of the
		// two places a rule is added.
		colours[count] = DEFAULT_COLOUR;
		count++;''',
     '''public static boolean toggle(String item, int mode) {
		ensure();
		if (item == null || item.length() == 0) {
			return false;
		}
		int at = find(item);
		if (at >= 0) {
			if (modes[at] == mode) {
				removeAt(at);
			} else {
				modes[at] = mode;       // hidden <-> highlighted, rather than a second contradictory rule
				save();
			}
			return true;
		}
		if (count >= MAX) {
			return false;
		}
		names[count] = item;
		modes[count] = mode;
		// EXPLICITLY, because an int[] is zero-filled and 0 is the "do not draw this row" answer
		// colourFor gives - so a new rule left at 0 is a rule whose item silently disappears.
		// That is what happened the first time this change ran against the tests, in both of the
		// two places a rule is added.
		count++;'''),
    (PREFS, '''a new rule added by set left at the zero-filled colour, so its item silently disappears''',
     '''public static boolean set(String item, int mode) {
		ensure();
		if (item == null || item.length() == 0) {
			return false;
		}
		int at = find(item);
		if (at >= 0) {
			modes[at] = mode;
			save();
			return true;
		}
		if (count >= MAX) {
			return false;
		}
		names[count] = item;
		modes[count] = mode;
		// EXPLICITLY, because an int[] is zero-filled and 0 is the "do not draw this row" answer
		// colourFor gives - so a new rule left at 0 is a rule whose item silently disappears.
		// That is what happened the first time this change ran against the tests, in both of the
		// two places a rule is added.
		colours[count] = DEFAULT_COLOUR;
		count++;''',
     '''public static boolean set(String item, int mode) {
		ensure();
		if (item == null || item.length() == 0) {
			return false;
		}
		int at = find(item);
		if (at >= 0) {
			modes[at] = mode;
			save();
			return true;
		}
		if (count >= MAX) {
			return false;
		}
		names[count] = item;
		modes[count] = mode;
		// EXPLICITLY, because an int[] is zero-filled and 0 is the "do not draw this row" answer
		// colourFor gives - so a new rule left at 0 is a rule whose item silently disappears.
		// That is what happened the first time this change ran against the tests, in both of the
		// two places a rule is added.
		count++;'''),
    (PREFS, 'the colour left behind when a rule is removed, recolouring every rule after it',
     '			colours[j] = colours[j + 1];\n',
     ''),
    (PREFS, 'a hand-edited black kept, so the row it belongs to draws nothing at all',
     'if (rgb != DEFAULT_COLOUR && rgb != 0) {',
     'if (rgb != DEFAULT_COLOUR) {'),
    (PREFS, 'a colour line matched to the wrong rule, by position instead of by name',
     '			int at = find(line.substring(0, split));',
     '			int at = i < count ? i : -1;'),
    (PREFS, 'the colour cycle never coming back round to Default',
     '		colours[i] = PALETTE[at % PALETTE.length];',
     '		colours[i] = PALETTE[at < PALETTE.length ? at : PALETTE.length - 1];'),
    (PLUGIN, 'an item the cache has no name for drawn as an empty row',
     'if (item.name.length() == 0) {\n\t\t\treturn 0;\n\t\t}\n',
     ''),
    (ITEM, 'a pile of non-stackables priced as though it stacked',
     'return this.stackable ? (long) this.count * (long) this.price : this.price;',
     'return (long) this.count * (long) this.price;'),

    # The rules are keyed on the bare name. Keying them on the label instead is the mistake that
    # makes a rule set on "Coins x 500" never match the single coin you drop next.
    (PLUGIN, 'the Alt controls keying a rule on the label instead of the item name',
     'final String name = item.name;',
     'final String name = label;'),

    # The caps, both of which exist to stop one tile costing a frame.
    (PLUGIN, 'the per-frame label budget removed',
     'for (int i = 0; i < piles.size() && this.drawn < MAX_LABELS; i++) {',
     'for (int i = 0; i < piles.size(); i++) {'),
    (PLUGIN, 'the per-tile cap removed, so a griefer\'s pile draws in full',
     'int distinct = Math.min(pile.items.size(), MAX_PER_TILE);',
     'int distinct = pile.items.size();'),
    (PLUGIN, 'one more row shown per pile',
     'private static final int ROWS_SHOWN = 8;',
     'private static final int ROWS_SHOWN = 9;'),

    # The wheel. Taking it when the pile does not need it is the version of this that players
    # notice, because the camera stops zooming wherever anything is lying on the floor.
    (PLUGIN, 'every pile claiming the wheel, not just the ones too tall to show',
     'if (visible <= ROWS_SHOWN) {\n\t\t\treturn;\n\t\t}\n',
     ''),
    (PLUGIN, 'the scroll offset left unclamped, so the window runs off the end of the pile',
     'GroundItemsPlugin.this.scrollOffset =\n'
     '\t\t\t\t\tMath.max(0, Math.min(GroundItemsPlugin.this.scrollOffset + delta, max));',
     'GroundItemsPlugin.this.scrollOffset = GroundItemsPlugin.this.scrollOffset + delta;'),

    # The scroll bar.
    (PLUGIN, 'the scroll bar drawn on the right of the column, over the names',
     'int barX = minLeft - BAR_GAP - BAR_W;',
     'int barX = minLeft + BAR_GAP;'),

    # The settings menu.
    (PLUGIN, 'the settings rows offered in interface menus too, so they appear over the backpack',
     'if (!event.isWorldMenu()) {\n\t\t\treturn;\n\t\t}\n',
     ''),
    (PLUGIN, 'the same item under the cursor twice getting two pairs of rows',
     'if (!"lre".equals(target.kind) || this.seenBefore(event, i)) {',
     'if (!"lre".equals(target.kind)) {'),

    # The config page.
    # Anchored on the line above it. There are two lists with a removable() of false now - the
    # display settings and the loot page - and the bare method body matches both, which makes
    # the mutation ambiguous rather than wrong.
    # The 'display settings made removable' mutation is gone with the list it broke: radius,
    # minimum value and show-hidden are @ConfigItem settings now, not ConfigList rows, so
    # there is no removable() for them to answer wrongly. GroundItemsTest asserts their
    # types instead, which is the promise that replaced it.
    (PLUGIN, 'the loot page totalling one of a non-stackable rather than all of them',
     'near.worth += (long) item.count * (long) item.price;',
     'near.worth += item.worth();'),
    (PLUGIN, 'the loot page using the value floor\'s formatter, so a worth reads as "or more"',
     'return near == null || near.worth <= 0 ? null : money(near.worth);',
     'return near == null || near.worth <= 0 ? null : floor((int) near.worth);'),
    (PALETTE, '''the thresholds left in the order they were typed, so a tier gives another's colour''',
     '''		sortDescending(this.thresholds, this.colours);''',
     ''''''),
    (PALETTE, '''the sort moving the thresholds and leaving the colours behind''',
     '''				colours[at + 1] = colours[at];
''',
     ''''''),
    (PALETTE, '''the sort put the other way up, so the lowest tier swallows everything''',
     '''while (at >= 0 && thresholds[at] < threshold) {''',
     '''while (at >= 0 && thresholds[at] > threshold) {'''),
    (PALETTE, '''a tier set to 0 matching everything, painting the whole floor''',
     '''if (this.thresholds[tier] > 0 && worth >= (long) this.thresholds[tier]) {''',
     '''if (worth >= (long) this.thresholds[tier]) {'''),
    (PALETTE, '''the tier boundary made exclusive, so an item worth exactly it drops a tier''',
     '''if (this.thresholds[tier] > 0 && worth >= (long) this.thresholds[tier]) {''',
     '''if (this.thresholds[tier] > 0 && worth > (long) this.thresholds[tier]) {'''),
    (PALETTE, '''a colour out of a settings file parsed without the fallback, out of a render''',
     '''				? PluginConfig.parseColour(colours[i]) : DEFAULTS.colour(i);''',
     '''				? Integer.parseInt(colours[i], 16) : DEFAULTS.colour(i);'''),
    (ARRIVALS, '''the first scan reporting every item already on the floor as a fresh drop''',
     '''return this.previous.length > 0 && !contains(this.previous, key);''',
     '''return !contains(this.previous, key);'''),
    (ARRIVALS, '''everything reported new every tick, so standing still is a notification storm''',
     '''return this.previous.length > 0 && !contains(this.previous, key);''',
     '''return this.previous.length > 0;'''),
    (ARRIVALS, '''nothing ever reported new, so the feature silently does nothing''',
     '''return this.previous.length > 0 && !contains(this.previous, key);''',
     '''return false;'''),
    (ARRIVALS, '''the scan not remembering what it saw, so every tick is a first scan''',
     '''		this.previous = kept;''',
     ''''''),
    (ARRIVALS, '''a reset leaving what it had, so logging in elsewhere is a storm''',
     '''		this.previous = new long[0];
''',
     ''''''),
    (ARRIVALS, '''the key ignoring the tile, so one drop hides another somewhere else''',
     '''return ((long) (worldX & 0x7FFF) << 34)''',
     '''return ((long) 0 << 34)'''),
    (ARRIVALS, '''the key ignoring the item, so a second drop on one tile is never new''',
     '''			| (long) (id & 0x1FFFF);''',
     '''			| 0L;'''),
    (ARRIVALS, '''the cap removed, so a crowded floor grows without limit''',
     '''		if (this.count < MAX) {''',
     '''		if (true) {'''),
    (PLUGIN, '''a highlighted drop notified even when the player did not ask for that''',
     '''if (notifyHighlighted && GroundItemPrefs.isHighlighted(item.name)) {''',
     '''if (GroundItemPrefs.isHighlighted(item.name)) {'''),
    (PLUGIN, '''everything notified when the tier is Off''',
     '''return notifyFrom > 0L && item.worth() >= notifyFrom;''',
     '''return item.worth() >= notifyFrom;'''),
    (PLUGIN, '''the notify tier reading its own number rather than the sorted palette''',
     '''		if (TIER_TOP.equals(tier)) {
			return palette.threshold(0);
		}''',
     '''		if (TIER_TOP.equals(tier)) {
			return 1000000L;
		}'''),
    (PLUGIN, '''a logged-out client keeping its floor, so logging in elsewhere is a storm''',
     '''			this.arrivals.reset();
			return;
		}
		boolean wantNotify''',
     '''			return;
		}
		boolean wantNotify'''),

    (PLUGIN, '''an uppercase hotkey setting no longer matching the lowercase key the client sends''',
     '''		return Character.toLowerCase(text.charAt(0));''',
     '''		return text.charAt(0);'''),
    (PLUGIN, '''F1 off by one, so every function key is the wrong one''',
     '''					return 1007 + n;''',
     '''					return 1008 + n;'''),
    (PLUGIN, '''F-key numbers unbounded, so F99 is a key code out of the blue''',
     '''				if (n >= 1 && n <= 12) {''',
     '''				if (n >= 1) {'''),
    # The 'blank hotkey setting' mutation is gone with the guard it deleted: a length-0
    # check was redundant - a blank setting already fails the "exactly one character"
    # test - and the audit finding it deletable with nothing noticing is how a redundant
    # check announces itself. The guard went rather than the mutation gaining a test.
    (PLUGIN, '''a multi-character setting taking its first letter, so "Ctrl" becomes c''',
     '''		if (text.length() != 1) {
			return -1;
		}
''',
     ''''''),
    (PLUGIN, '''the hotkey not consumed, so it also lands in the chat box''',
     '''		event.consume();''',
     ''''''),
    (PLUGIN, '''the double-tap live even when the player set its window to 0''',
     '''return windowMs > 0 && lastDownAt > 0L && now - lastDownAt <= (long) windowMs;''',
     '''return lastDownAt > 0L && now - lastDownAt <= (long) windowMs;'''),
    (PLUGIN, '''the first Alt press since startup counting as the second of a pair''',
     '''return windowMs > 0 && lastDownAt > 0L && now - lastDownAt <= (long) windowMs;''',
     '''return windowMs > 0 && now - lastDownAt <= (long) windowMs;'''),
    (PLUGIN, '''the double-tap window made exclusive, so a tap exactly on it does not count''',
     '''now - lastDownAt <= (long) windowMs;''',
     '''now - lastDownAt < (long) windowMs;'''),
    (PLUGIN, '''the tap watched while Alt is held rather than on the press edge''',
     '''		if (alt && !this.altWasDown) {''',
     '''		if (alt) {'''),
    (PLUGIN, '''every beam shape drawing the same width, so the choice does nothing''',
     '''		if (BEAM_STRAIGHT.equals(style)) {
			width = BEAM_W;
		} else if (BEAM_NARROW.equals(style)) {
			width = BEAM_W / 4;
		} else {''',
     '''		if (false) {
			width = BEAM_W;
		} else if (false) {
			width = BEAM_W / 4;
		} else {'''),
    (PLUGIN, '''a tall beam allowed to reach zero width and draw nothing''',
     '''		return width < 1 ? 1 : width;''',
     '''		return width;'''),
    (PLUGIN, '''an unknown shape drawing nothing rather than the default''',
     '''		} else {
			width = BEAM_W - segment * BEAM_W / (BEAM_SEGMENTS + 1);
		}''',
     '''		} else {
			width = 0;
		}'''),
    (OVERLAY, '''the outline drawn on one side only, which is the shadow it replaces''',
     '''		this.font.drawString(left + 1, 0, top, text);
''',
     ''''''),
    (OVERLAY, '''the outline passes shadowed, so each casts a shadow of its own''',
     '''		this.font.drawString(left - 1, 0, top, text);''',
     '''		this.font.drawStringTag(0, left - 1, top, true, text);'''),
    (OVERLAY, '''the coloured pass shadowed as well, so an outline is also a shadow''',
     '''		this.font.drawStringTag(colour, left, top, false, text);''',
     '''		this.font.drawStringTag(colour, left, top, true, text);'''),
    (OVERLAY, '''outlined text not centred, so a row sits off its tile''',
     '''		int centred = x - this.font.stringWidTag(text) / 2;
		this.markText(centred, y, text);''',
     '''		int centred = x;
		this.markText(centred, y, text);'''),
    (PLUGIN, '''the outline drawn whether the player asked for it or not''',
     '''				if (this.textOutline) {''',
     '''				if (true) {'''),

]


def main():
    only = sys.argv[1] if len(sys.argv) > 1 else None
    orig = {}
    # EVERY FILE ANY MUTATION TARGETS. A target missing from this tuple is not a skipped
    # mutation, it is a KeyError that kills the run partway through - which is how the first
    # eighteen mutations added here never ran at all.
    for path in (PLUGIN, ITEM, PALETTE, ARRIVALS, OVERLAY, PREFS):
        with open(path, encoding='utf-8', newline='') as f:
            orig[path] = f.read()
    guard(orig)
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
            r = subprocess.run([sys.executable, RUNNER], capture_output=True, text=True)
        finally:
            with open(path, 'w', encoding='utf-8', newline='') as f:
                f.write(orig[path])
        fired = [l.strip()[5:].strip() for l in r.stdout.split('\n') if l.startswith('FAIL')]
        if r.returncode == 0:
            print('  %-5s %-80s %s' % ('GREEN', why, 'NOT CAUGHT'))
            bad += 1
        elif fired:
            print('  %-5s %-80s %s' % ('red', why, 'caught by: ' + fired[0][:52]))
        else:
            print('  %-5s %-80s %s' % ('red', why,
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
