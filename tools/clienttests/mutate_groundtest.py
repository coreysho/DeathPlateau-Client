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

# THE WORKING TREE IS NEVER WRITTEN. Mutations go into a throwaway copy of the repository, so
# the tree stays clean and committable for the whole run and a kill at the worst moment leaves a
# broken file in a temp directory nobody builds from. It is a snapshot too: an edit to the tree
# mid-run cannot reach the run. mutate_guard.workspace has the reasoning, and its check() is the
# standalone pass that every pattern still matches its source exactly once.
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from mutate_guard import workspace  # noqa: E402  (after the sys.path line, necessarily)

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
PLUGIN = os.path.join(ROOT, 'src/main/java/jagex2/client/plugin/builtin/GroundItemsPlugin.java')
PALETTE = os.path.join(ROOT, 'src/main/java/jagex2/client/plugin/builtin/GroundItemPalette.java')
OVERLAY = os.path.join(ROOT, 'src/main/java/jagex2/client/plugin/OverlayGraphics.java')
ARRIVALS = os.path.join(ROOT, 'src/main/java/jagex2/client/plugin/builtin/GroundItemArrivals.java')
ITEM = os.path.join(ROOT, 'src/main/java/jagex2/client/plugin/GroundItem.java')
PREFS = os.path.join(ROOT, 'src/main/java/jagex2/client/GroundItemPrefs.java')
CONTEXT = os.path.join(ROOT, 'src/main/java/jagex2/client/plugin/PluginContext.java')
HOTKEY = os.path.join(ROOT, 'src/main/java/jagex2/client/plugin/builtin/Hotkey.java')
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
    # Anchored on the colourOf() line above, because menu restyling added a second pair of these
    # for the Take rows - same question, different argument - and a pattern matching both would
    # be a silent skip.
    (PLUGIN, "a rule's own colour ignored on the label, so setting one does nothing",
     '''int own = GroundItemPrefs.colourOf(item.name);
			return own == GroundItemPrefs.DEFAULT_COLOUR ? palette.highlighted : own;''',
     'return palette.highlighted;'),
    (PLUGIN, "a hidden rule's own colour ignored on the label",
     '''int own = GroundItemPrefs.colourOf(item.name);
			return own == GroundItemPrefs.DEFAULT_COLOUR ? palette.hidden : own;''',
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

    (HOTKEY, '''an uppercase hotkey setting no longer matching the lowercase key the client sends''',
     '''		return Character.toLowerCase(text.charAt(0));''',
     '''		return text.charAt(0);'''),
    (HOTKEY, '''F1 off by one, so every function key is the wrong one''',
     '''					return F1 - 1 + n;''',
     '''					return F1 + n;'''),
    (HOTKEY, '''F-key numbers unbounded, so F99 is a key code out of the blue''',
     '''				if (n >= 1 && n <= FUNCTION_KEYS) {''',
     '''				if (n >= 1) {'''),
    # The 'blank hotkey setting' mutation is gone with the guard it deleted: a length-0
    # check was redundant - a blank setting already fails the "exactly one character"
    # test - and the audit finding it deletable with nothing noticing is how a redundant
    # check announces itself. The guard went rather than the mutation gaining a test.
    (HOTKEY, '''a multi-character setting taking its first letter, so "Ctrl" becomes c''',
     '''		if (text.length() != 1) {
			return NONE;
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
     '''		if (BEAM_LOOT.equals(style)) {
			width = lootBeamWidth(segment, segments, tileWidth);
		} else if (BEAM_STRAIGHT.equals(style)) {
			width = tileWidth * BEAM_STRAIGHT_PERMILLE / 1000;
		} else if (BEAM_NARROW.equals(style)) {
			width = tileWidth * BEAM_NARROW_PERMILLE / 1000;
		} else {''',
     '''		if (false) {
			width = lootBeamWidth(segment, segments, tileWidth);
		} else if (false) {
			width = tileWidth * BEAM_STRAIGHT_PERMILLE / 1000;
		} else if (false) {
			width = tileWidth * BEAM_NARROW_PERMILLE / 1000;
		} else {'''),
    (PLUGIN, '''a tall beam allowed to reach zero width and draw nothing''',
     '''		return width < 1 ? 1 : width;
	}

	@Subscribe''',
     '''		return width;
	}

	@Subscribe'''),
    (PLUGIN, '''an unknown shape drawing nothing rather than the default''',
     '''			width = tileWidth * permille / 1000;
		}
		return width < 1 ? 1 : width;''',
     '''			width = 0;
		}
		return width;'''),
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

    # ---- MENU RESTYLING. Recolouring and reordering the Take rows: which rows are found, which
    # colour each gets, and the order they are moved in - which is the part that is not a loop.
    (CONTEXT, '''the Take action id off by one, so no ground item row is ever found''',
     '''	private static final int TAKE_ACTION = 684;''',
     '''	private static final int TAKE_ACTION = 685;'''),
    (PLUGIN, '''the Take filter dropped, so Examine and every other row is restyled too''',
     '''			if (!this.ctx.isGroundItemTake(i)) {
				continue;
			}
''',
     ''''''),
    (PLUGIN, '''the row text used raw, tags and all, so no rule matches it''',
     '''		return at < 0 ? "" : MenuSwaps.parseTarget(option, at);''',
     '''		return option;'''),
    (PLUGIN, '''a row with no target reading as an item called by its own text''',
     '''		return at < 0 ? "" : MenuSwaps.parseTarget(option, at);''',
     '''		return at < 0 ? option : MenuSwaps.parseTarget(option, at);'''),
    (PLUGIN, '''the hidden and highlighted menu colours swapped''',
     '''		if (colourHighlighted && GroundItemPrefs.isHighlighted(name)) {
			int own = GroundItemPrefs.colourOf(name);
			return own == GroundItemPrefs.DEFAULT_COLOUR ? palette.highlighted : own;
		}
		if (colourHidden && GroundItemPrefs.isHidden(name)) {
			int own = GroundItemPrefs.colourOf(name);
			return own == GroundItemPrefs.DEFAULT_COLOUR ? palette.hidden : own;
		}''',
     '''		if (colourHighlighted && GroundItemPrefs.isHighlighted(name)) {
			int own = GroundItemPrefs.colourOf(name);
			return own == GroundItemPrefs.DEFAULT_COLOUR ? palette.hidden : own;
		}
		if (colourHidden && GroundItemPrefs.isHidden(name)) {
			int own = GroundItemPrefs.colourOf(name);
			return own == GroundItemPrefs.DEFAULT_COLOUR ? palette.highlighted : own;
		}'''),
    (PLUGIN, '''highlighted rows coloured whether the player asked for it or not''',
     '''		if (colourHighlighted && GroundItemPrefs.isHighlighted(name)) {''',
     '''		if (GroundItemPrefs.isHighlighted(name)) {'''),
    (PLUGIN, '''hidden rows coloured whether the player asked for it or not''',
     '''		if (colourHidden && GroundItemPrefs.isHidden(name)) {''',
     '''		if (GroundItemPrefs.isHidden(name)) {'''),
    (PLUGIN, '''a rule's own colour ignored in the menu, so the floor and the menu disagree''',
     '''			int own = GroundItemPrefs.colourOf(name);
			return own == GroundItemPrefs.DEFAULT_COLOUR ? palette.highlighted : own;''',
     '''			return palette.highlighted;'''),
    (PLUGIN, '''a hidden rule's own colour ignored in the menu''',
     '''			int own = GroundItemPrefs.colourOf(name);
			return own == GroundItemPrefs.DEFAULT_COLOUR ? palette.hidden : own;''',
     '''			return palette.hidden;'''),
    (PLUGIN, '''a Take row whose item has no name not skipped, so hide=* drags it around too''',
     '''			if (name.length() == 0) {
				continue;
			}
''',
     ''),
    (PLUGIN, '''a nameless row looked up as a rule called "", which a hide=* rule does match''',
     '''		if (name.length() == 0) {
			return 0;
		}
		if (colourHighlighted''',
     '''		if (colourHighlighted'''),
    (PLUGIN, '''rows moved bottom-first, which comes out with them reversed''',
     '''			order[j] = rows[count - 1 - j] + j;''',
     '''			order[j] = rows[j] + j;'''),
    (PLUGIN, '''the shift from earlier moves not added, so the second row moved is the wrong one''',
     '''			order[j] = rows[count - 1 - j] + j;''',
     '''			order[j] = rows[count - 1 - j];'''),
    (PLUGIN, '''the moves applied in reverse, which reverses the rows''',
     '''			for (int j = 0; j < order.length; j++) {
				this.ctx.deprioritiseMenuEntry(order[j]);
			}''',
     '''			for (int j = order.length - 1; j >= 0; j--) {
				this.ctx.deprioritiseMenuEntry(order[j]);
			}'''),
    (PLUGIN, '''the whole buffer read rather than the rows collected this frame''',
     '''			int[] order = deprioritiseOrder(this.menuHidden, moving);''',
     '''			int[] order = deprioritiseOrder(this.menuHidden, this.menuHidden.length);'''),
    (PLUGIN, '''the early return missing a setting, so moving rows alone does nothing''',
     '''		if (!this.menuColourHighlighted && !this.menuColourHidden
			&& !this.menuDeprioritiseHidden) {''',
     '''		if (!this.menuColourHighlighted && !this.menuColourHidden) {'''),
    (PLUGIN, '''rows collected for moving whether the player asked for it or not''',
     '''			if (this.menuDeprioritiseHidden && moving < this.menuHidden.length''',
     '''			if (moving < this.menuHidden.length'''),
    (CONTEXT, '''a moved row leaving its colour behind, on whatever took its place''',
     '''		if (a < this.client.menuColour.length && b < this.client.menuColour.length) {
			int colour = this.client.menuColour[a];
			this.client.menuColour[a] = this.client.menuColour[b];
			this.client.menuColour[b] = colour;
		}
''',
     ''''''),
    (CONTEXT, '''the bubble running into index 0, which swaps Cancel out of the bottom''',
     '''		for (int at = index; at > 1; at--) {''',
     '''		for (int at = index; at > 0; at--) {'''),
    (CONTEXT, '''a colour set on a row the menu does not have''',
     '''		if (index >= 0 && index < this.client.menuSize && index < this.client.menuColour.length) {''',
     '''		if (index >= 0) {'''),
    (CONTEXT, '''a colour written one past the end of the array the draw reads''',
     '''&& index < this.client.menuColour.length) {''',
     '''&& index <= this.client.menuColour.length) {'''),
    (CONTEXT, '''setMenuColour writing nothing, so every override is dropped''',
     '''			this.client.menuColour[index] = rgb;
''',
     ''''''),
    (PLUGIN, '''the declared API level left behind, so an older client loads this and breaks''',
     '''	apiLevel = 6
)''',
     '''	apiLevel = 0
)'''),

    # ---- THE BEAM AS LIGHT. It was a stack of boxes at one flat alpha; it fades, has a brighter
    # core than its edges, lights the ground under it, and can pulse. Each of those is a property
    # of light, and each of these breaks one of them.
    (PLUGIN, '''the fade removed, so a beam is a slab of one brightness again''',
     '''		if (!fade || segments <= 1) {
			return base;
		}
''',
     ''''''),
    (PLUGIN, '''the fade inverted, so a beam is brightest where nobody is looking''',
     '''		int left = segments - segment;''',
     '''		int left = segment + 1;'''),
    (PLUGIN, '''the ramp over the segments exactly, so the top segment is invisible''',
     '''		int alpha = base * left / (segments + 1);''',
     '''		int alpha = base * (left - 1) / segments;'''),
    (PLUGIN, '''a segment allowed to reach nothing, which is a beam one segment shorter''',
     '''		return alpha < 1 ? 1 : alpha;''',
     '''		return alpha;'''),
    (PLUGIN, '''a one-segment beam dividing its brightness away''',
     '''		if (!fade || segments <= 1) {''',
     '''		if (!fade) {'''),
    (PLUGIN, '''the core as wide as the beam, so there is no core''',
     '''		return width / BEAM_CORE_DIVISOR;''',
     '''		return width;'''),
    (PLUGIN, '''the core drawn whether the player asked for it or not''',
     '''			if (this.beamCore) {''',
     '''			if (true) {'''),
    (PLUGIN, '''the core never drawn, so the switch does nothing''',
     '''			if (this.beamCore) {''',
     '''			if (false) {'''),
    # NOT MUTATED: `if (core > 0)` has no observable effect, so breaking it is not a mutation.
    # coreWidth() is width/3 and beamWidth() floors every style at 1, so core is never negative,
    # and at core == 0 the fillAlpha it guards is a no-op twice over: OverlayGraphics.mark()
    # returns on `width <= 0`, and Pix2D.fillRectTrans's inner loop is `for (j = -width; j < 0;
    # j++)`, which does not run. Nothing is painted and nothing is marked either way. The guard
    # stays in the source because it states the intent and does not lean on that loop bound, but
    # a mutation nothing can distinguish is a survivor by construction rather than a gap.
    (PLUGIN, '''the ground glow drawn whether the player asked for it or not''',
     '''		if (this.beamGlow) {
			pool(this.ctx, g, pile.sceneTileX, pile.sceneTileZ, colour,''',
     '''		if (true) {
			pool(this.ctx, g, pile.sceneTileX, pile.sceneTileZ, colour,'''),
    (PLUGIN, '''the ground glow never drawn''',
     '''		if (this.beamGlow) {
			pool(this.ctx, g, pile.sceneTileX, pile.sceneTileZ, colour,''',
     '''		if (false) {
			pool(this.ctx, g, pile.sceneTileX, pile.sceneTileZ, colour,'''),
    (PLUGIN, '''the height left at the old constant, so the setting is decoration''',
     '''		int segments = beamSegmentsFor(this.beamSegments);''',
     '''		int segments = DEFAULT_BEAM_SEGMENTS;'''),
    (PLUGIN, '''the opacity left at the old constant''',
     '''		int base = pulsed(beamAlphaFor(this.beamOpacity), System.currentTimeMillis(),
			this.beamPulse);''',
     '''		int base = DEFAULT_BEAM_ALPHA;'''),
    (PLUGIN, '''the height floor removed, so a zero is no beam at all''',
     '''		if (segments < MIN_BEAM_SEGMENTS) {
			return MIN_BEAM_SEGMENTS;
		}
''',
     ''''''),
    (PLUGIN, '''the height ceiling removed, so a beam can be a column into the sky''',
     '''		return segments > MAX_BEAM_SEGMENTS ? MAX_BEAM_SEGMENTS : segments;''',
     '''		return segments;'''),
    (PLUGIN, '''the opacity floor removed, so a 0 turns the beam off from a box that cannot say so''',
     '''		if (alpha < MIN_BEAM_ALPHA) {
			return MIN_BEAM_ALPHA;
		}
''',
     ''''''),
    (PLUGIN, '''the pulse live whether the player asked for it or not''',
     '''		if (!pulse) {
			return alpha;
		}
''',
     ''''''),
    (PLUGIN, '''the pulse peaking above the brightness a player set''',
     '''		return capAlpha(dimmed + (int) (swing * up / BEAM_PULSE_MS));''',
     '''		return capAlpha(dimmed + (int) (swing * 2L * up / BEAM_PULSE_MS));'''),
    (PLUGIN, '''the pulse a sawtooth rather than a triangle, so it jumps every cycle''',
     '''		long up = phase <= BEAM_PULSE_MS ? phase : BEAM_PULSE_MS * 2L - phase;''',
     '''		long up = phase;'''),
    (PLUGIN, '''the pulse never dimming, so it is a constant with extra arithmetic''',
     '''		int swing = alpha * BEAM_PULSE_PERCENT / 100;''',
     '''		int swing = 0;'''),
    (PLUGIN, '''the doubled alpha uncapped, so a bright beam wraps past opaque''',
     '''		if (alpha < 1) {
			return 1;
		}
		return alpha > MAX_BEAM_ALPHA ? MAX_BEAM_ALPHA : alpha;''',
     '''		return alpha;'''),
    (PLUGIN, '''the taper computed over a fixed height, so a tall beam is a needle halfway up''',
     '''			int permille = BEAM_TAPER_TIP_PERMILLE
				+ (BEAM_STRAIGHT_PERMILLE - BEAM_TAPER_TIP_PERMILLE) * down / segments;''',
     '''			int permille = BEAM_TAPER_TIP_PERMILLE
				+ (BEAM_STRAIGHT_PERMILLE - BEAM_TAPER_TIP_PERMILLE) * down
					/ DEFAULT_BEAM_SEGMENTS;'''),

    # ---- THE MEASURED SHAPE. Jagex's sprite is 12% of its full width at the halfway mark;
    # cubing gives 12.5%, squaring 25% and a linear taper 50%. Two of these mutations are those
    # wrong curves, because "it narrows" is true of all three and says nothing.
    (PLUGIN, '''the loot beam's straight part cubed, which is the needle this replaced''',
     '''			permille = BEAM_SLOPE_PERMILLE * down / segments;''',
     '''			permille = BEAM_SLOPE_PERMILLE * down * down * down
				/ (segments * segments * segments);'''),
    (PLUGIN, '''the loot beam's slope off the sprite's, so the column is the wrong thickness''',
     '''	static final int BEAM_SLOPE_PERMILLE = 226;''',
     '''	static final int BEAM_SLOPE_PERMILLE = 90;'''),
    # ---- THE REWRITE: a column drawn a row at a time, in layers, on a quad pool.
    (PLUGIN, '''the pool drawn as the tile's bounding box again, so it is a sticker on the floor''',
     '''			fillQuad(g, rx, ry, colour, each);''',
     '''			fillQuad(g, xs, ys, colour, each);'''),
    (PLUGIN, '''the pool a single flat fill, so its edge is hard''',
     '''	static final int BEAM_POOL_RINGS = 5;''',
     '''	static final int BEAM_POOL_RINGS = 1;'''),
    (PLUGIN, '''the soft halo never drawn, so the beam is a flat wedge again''',
     '''			int halo = width * BEAM_HALO_PERCENT / 100;''',
     '''			int halo = 0;'''),
    (PLUGIN, '''the halo as narrow as the body, so there is no halo''',
     '''	static final int BEAM_HALO_PERCENT = 210;''',
     '''	static final int BEAM_HALO_PERCENT = 100;'''),
    (PLUGIN, '''the halo at the body's own alpha, so the beam is twice as solid''',
     '''	static final int BEAM_HALO_ALPHA = 30;''',
     '''	static final int BEAM_HALO_ALPHA = 100;'''),
    (PLUGIN, '''the ribbons drawn whether the player asked for them or not''',
     '''			if (this.beamRibbons && down * 100 >= rise * BEAM_RIBBON_FROM) {''',
     '''			if (down * 100 >= rise * BEAM_RIBBON_FROM) {'''),
    (PLUGIN, '''the ribbons never drawn, so the switch does nothing''',
     '''			if (this.beamRibbons && down * 100 >= rise * BEAM_RIBBON_FROM) {''',
     '''			if (false && down * 100 >= rise * BEAM_RIBBON_FROM) {'''),
    (PLUGIN, '''the ribbons run the whole height, so they read as a second beam''',
     '''	static final int BEAM_RIBBON_FROM = 55;''',
     '''	static final int BEAM_RIBBON_FROM = 0;'''),
    (PLUGIN, '''the ribbons not wound, so they are two straight lines''',
     '''	static final int BEAM_RIBBON_TURNS = 2;''',
     '''	static final int BEAM_RIBBON_TURNS = 0;'''),
    (PLUGIN, '''the two strands wound together rather than opposite''',
     '''				int at = turn + side * 1024;''',
     '''				int at = turn;'''),
    (PLUGIN, '''the column drawn downward from its tip rather than between its ends''',
     '''			int x = tipX + (footX - tipX) * down / rise;''',
     '''			int x = tipX;'''),
    # NOT MUTATED: clamping the row loop to Pix2D's clip changes no pixel, so removing it is
    # not a mutation. Every fill it skips is one fillAlpha would have clipped away anyway - the
    # clamp exists so the LOOP does not run for rows that cannot be drawn, which for a beam
    # seen from close up is thousands of iterations per frame. That is a cost, and cost is the
    # one thing a check on the rendered image cannot see. Left in the source and recorded here
    # rather than deleted, because deleting it is a silent frame-rate regression on exactly the
    # drop a player is standing next to.
    (PLUGIN, '''the loot beam taper upside down, so it is widest at the tip''',
     '''		int down = segments - segment;
		int permille;''',
     '''		int down = segment;
		int permille;'''),
    (PLUGIN, '''a loot beam segment allowed to vanish, so its top half is not drawn''',
     '''		return width < 1 ? 1 : width;
	}

	/** How tall a beam''',
     '''		return width;
	}

	/** How tall a beam'''),
    (PLUGIN, '''a beam of no segments dividing by its own height in a render loop''',
     '''		if (segments < 1) {
			return 1;
		}
''',
     ''''''),
    (PLUGIN, '''the loot beam shape not reached by its own drop-down value''',
     '''		if (BEAM_LOOT.equals(style)) {
			width = lootBeamWidth(segment, segments, tileWidth);
		} else if (BEAM_STRAIGHT.equals(style)) {''',
     '''		if (BEAM_STRAIGHT.equals(style)) {'''),
    (PLUGIN, '''the default shape back to the cone, so nobody sees the one this was asked for''',
     '''	public String beamStyle = BEAM_LOOT;''',
     '''	public String beamStyle = BEAM_TAPERED;'''),
    (PLUGIN, '''the default height back to fourteen, which is barely two to one''',
     '''	static final int DEFAULT_BEAM_SEGMENTS = 24;''',
     '''	static final int DEFAULT_BEAM_SEGMENTS = 14;'''),

]


def main():
    only = sys.argv[1] if len(sys.argv) > 1 else None
    orig = {}
    # EVERY FILE ANY MUTATION TARGETS. A target missing from this tuple is not a skipped
    # mutation, it is a KeyError that kills the run partway through - which is how the first
    # eighteen mutations added here never ran at all.
    for path in (PLUGIN, ITEM, PALETTE, ARRIVALS, OVERLAY, PREFS, CONTEXT, HOTKEY):
        with open(path, encoding='utf-8', newline='') as f:
            orig[path] = f.read()
    # Written into a copy, never into the working tree - see mutate_guard.workspace.
    _work, inside = workspace('groundtest')
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
            r = subprocess.run([sys.executable, inside(RUNNER)], capture_output=True, text=True)
        finally:
            with open(inside(path), 'w', encoding='utf-8', newline='') as f:
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
