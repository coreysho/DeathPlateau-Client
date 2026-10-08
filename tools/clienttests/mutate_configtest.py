#!/usr/bin/env python3
"""Mutation test for tools/clienttests/run_configtest.py.

Breaks the config editors one way at a time and checks run_configtest notices - and notices by
NAMING a check, not by crashing.

The parse is the part worth guarding hardest. It runs from render, per frame, on text a player can
type, and it was wrong for releases: a length check without a character check let "-00FF0" through
Integer.parseInt as -4080. Nothing displayed the parsed colour, so nothing caught it. The swatch
displays it now, and these mutations keep the hole shut.

    python3 tools/clienttests/mutate_configtest.py [filter]
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
CONFIG = os.path.join(ROOT, 'src/main/java/jagex2/client/plugin/PluginConfig.java')
PANEL = os.path.join(ROOT, 'src/main/java/jagex2/client/plugin/ui/ConfigPanel.java')
PLUGIN = os.path.join(ROOT, 'src/main/java/jagex2/client/plugin/builtin/NpcIndicatorsPlugin.java')
MOUSE = os.path.join(ROOT, 'src/main/java/jagex2/client/plugin/builtin/MouseHighlightPlugin.java')
RUNNER = os.path.join(HERE, 'run_configtest.py')

MUTS = [
    # ---- THE HOLE THAT SHIPPED, put back exactly as it was.
    (CONFIG, 'the character check dropped, so "-00FF0" parses as a negative colour again',
     '''		for (int i = 0; i < 6; i++) {
			if (Character.digit(cleaned.charAt(i), 16) < 0) {
				return 0xFFFF00;
			}
		}
''',
     ''),
    (CONFIG, 'the result masked instead of refused, turning nonsense into a plausible colour',
     '''		for (int i = 0; i < 6; i++) {
			if (Character.digit(cleaned.charAt(i), 16) < 0) {
				return 0xFFFF00;
			}
		}
''',
     '''		if (cleaned.startsWith("-") || cleaned.startsWith("+")) {
			return Integer.parseInt(cleaned.substring(1), 16) & 0xFFFFFF;
		}
'''),

    # ---- THE REST OF THE PARSE. All of it runs per frame on whatever is in the file.
    (CONFIG, 'a null colour setting thrown rather than defaulted, out of a render',
     '''		if (text == null) {
			return 0xFFFF00;
		}
''',
     ''),
    (CONFIG, 'the length check dropped, so a short or long value parses',
     '''		if (cleaned.length() != 6) {
			return 0xFFFF00;
		}
''',
     ''),
    (CONFIG, 'the trim dropped, so a space in the file breaks a colour',
     'String cleaned = text.trim();',
     'String cleaned = text;'),
    (CONFIG, 'a pasted # no longer forgiven',
     '''		if (cleaned.startsWith("#")) {
			cleaned = cleaned.substring(1);
		}
''',
     ''),
    (CONFIG, 'the fallback made black, which reads as "the text did not draw"',
     '''	public static int parseColour(String text) {
		if (text == null) {
			return 0xFFFF00;
		}''',
     '''	public static int parseColour(String text) {
		if (text == null) {
			return 0x000000;
		}'''),

    # ---- AND BACK AGAIN. The swatch shows parseColour(stored); the picker writes toHex(picked).
    (CONFIG, 'the alpha byte left in, so a picked colour writes eight characters',
     'String hex = Integer.toHexString(rgb & 0xFFFFFF).toUpperCase();',
     'String hex = Integer.toHexString(rgb).toUpperCase();'),
    (CONFIG, 'leading zeroes dropped, so black writes as "0" and reads back as yellow',
     '''		while (hex.length() < 6) {
			hex = "0" + hex;
		}
''',
     ''),
    (CONFIG, 'the padding added on the wrong end, so 0000FF becomes FF0000',
     'hex = "0" + hex;',
     'hex = hex + "0";'),

    # ---- WHICH EDITOR A SETTING GETS. Offered on the wrong type, there is nothing to show.
    (CONFIG, 'a colour editor offered for an int or boolean setting too',
     'return this.colour && this.isString();',
     'return this.colour;'),
    (CONFIG, 'a drop-down offered for a non-String setting',
     'return this.isString() ? this.choices.clone() : new String[0];',
     'return this.choices.clone();'),
    (CONFIG, 'the choices array handed out rather than copied, so a caller can rewrite it',
     'return this.isString() ? this.choices.clone() : new String[0];',
     'return this.isString() ? this.choices : new String[0];'),

    # ---- THE PANEL. Cancel must not write, and an unknown value must not be rewritten.
    (PANEL, 'a cancelled colour picker writing black over the setting',
     '''				if (picked != null) {
					ConfigPanel.this.write(item, PluginConfig.toHex(picked.getRGB()), null);
				}''',
     '''				ConfigPanel.this.write(item, PluginConfig.toHex(
					picked == null ? java.awt.Color.BLACK.getRGB() : picked.getRGB()), null);'''),
    (PANEL, 'the swatch showing a colour of its own rather than what is stored',
     'final int rgb = item.colourValue();',
     'final int rgb = 0xFFFFFF;'),
    (PANEL, 'a stored value that is no longer a choice silently dropped',
     '''		if (!options.contains(current)) {
			options.add(0, current);
		}
''',
     ''),
    (PANEL, 'the drop-down writing on every rebuild, not only on a real change',
     'if (picked != null && !String.valueOf(picked).equals(current)) {',
     'if (picked != null) {'),
    (PANEL, 'the colour editor never reached, so a colour setting is a text box again',
     '''		if (item.isColour()) {
			return this.buildColourEditor(item);
		}
''',
     ''),
    (PANEL, 'the drop-down never reached, so a choice setting is a text box again',
     '''		String[] choices = item.choices();
		if (choices.length > 0) {
			return this.buildChoiceEditor(item, choices);
		}
''',
     ''),

    # ---- ONE IMPLEMENTATION. The swatch and the drawing have to agree.
    (MOUSE, 'the built-in keeping its own colour parse again, free to drift from the swatch',
     'return PluginConfig.parseColour(text);',
     '''if (text == null || text.trim().length() != 6) {
			return 0xFFFF00;
		}
		try {
			return Integer.parseInt(text.trim(), 16);
		} catch (RuntimeException notHex) {
			return 0xFFFF00;
		}'''),

    # ---- WHAT THE DROP-DOWN DRIVES.
    (PLUGIN, 'an unknown tag position putting the tag on the ground rather than defaulting',
     '''		if (AT_FEET.equals(position)) {
			return 0;
		}
		return TAG_HEIGHT + (size - 1) * TAG_HEIGHT_PER_SIZE;''',
     '''		if (ABOVE.equals(position)) {
			return TAG_HEIGHT + (size - 1) * TAG_HEIGHT_PER_SIZE;
		}
		return 0;'''),
    (PLUGIN, 'a null position thrown rather than defaulted',
     'if (AT_FEET.equals(position)) {',
     'if (position.equals(AT_FEET)) {'),
    (PLUGIN, 'at feet ignored, so the choice does nothing',
     '''		if (AT_FEET.equals(position)) {
			return 0;
		}
''',
     ''),
    (PLUGIN, 'the size no longer clearing a big npc',
     'return TAG_HEIGHT + (size - 1) * TAG_HEIGHT_PER_SIZE;',
     'return TAG_HEIGHT;'),
    (PLUGIN, 'the drop-down offering values the code does not test against',
     'choices = { ABOVE, AT_FEET }',
     'choices = { "Over its head", "On the floor" }'),
]


def main():
    only = sys.argv[1] if len(sys.argv) > 1 else None
    orig = {}
    for path in (CONFIG, PANEL, PLUGIN, MOUSE):
        with open(path, encoding='utf-8', newline='') as f:
            orig[path] = f.read()
    # Written into a copy, never into the working tree - see mutate_guard.workspace.
    _work, inside = workspace('configtest')
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
            print('  %-5s %-76s %s' % ('HUNG', why, 'the suite never finished - not a catch'))
            loose += 1
            continue
        finally:
            with open(inside(path), 'w', encoding='utf-8', newline='') as f:
                f.write(orig[path])
        fired = [l.strip()[5:].strip() for l in r.stdout.split('\n') if l.startswith('FAIL')]
        if r.returncode == 0:
            print('  %-5s %-76s %s' % ('GREEN', why, 'NOT CAUGHT'))
            bad += 1
        elif fired:
            print('  %-5s %-76s %s' % ('red', why, 'caught by: ' + fired[0][:44]))
        else:
            print('  %-5s %-76s %s' % ('red', why,
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
