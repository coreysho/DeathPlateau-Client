#!/usr/bin/env python3
"""Mutation test for the client API level gate.

Breaks the gate one plausible way at a time and checks that run_plugintest or run_hubtest notices
- and notices by NAMING a check, not by crashing. A gate whose tests only go red because
something threw is not measuring the thing it claims to.

The gate is worth this because it is a safety net, and a net nobody tests is a net with a hole
in it: every mutation below leaves the client compiling and running perfectly, and every one of
them silently lets a jar through onto a client that cannot run it.

    python3 tools/clienttests/mutate_apilevel.py [filter]
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
SRC = os.path.join(ROOT, 'src/main/java/jagex2/client/plugin')
API = os.path.join(SRC, 'PluginApi.java')
MANAGER = os.path.join(SRC, 'PluginManager.java')
ENTRY = os.path.join(SRC, 'hub/HubEntry.java')
HOTKEY = os.path.join(ROOT, 'src/main/java/jagex2/client/plugin/builtin/Hotkey.java')
SWAPPER = os.path.join(ROOT, 'src/main/java/jagex2/client/plugin/builtin/MenuSwapperPlugin.java')
DRAG = os.path.join(ROOT, 'src/main/java/jagex2/client/plugin/builtin/AntiDragPlugin.java')
PLUGIN_RUNNER = os.path.join(HERE, 'run_plugintest.py')
GROUND_RUNNER = os.path.join(HERE, 'run_groundtest.py')
HUB_RUNNER = os.path.join(HERE, 'run_hubtest.py')

MUTS = [
    # --- the comparison itself -------------------------------------------------------------
    (API, PLUGIN_RUNNER, 'the level comparison inverted',
     'return apiLevel <= LEVEL;',
     'return apiLevel >= LEVEL;'),
    (API, PLUGIN_RUNNER, 'everything supported, which is the client as it was before levels',
     'return apiLevel <= LEVEL;',
     'return true;'),
    # Off by one is the mistake that would let through exactly the jars this exists to stop.
    (API, PLUGIN_RUNNER, 'one level of slack, so the next client\'s jars load on this one',
     'return apiLevel <= LEVEL;',
     'return apiLevel <= LEVEL + 1;'),

    # --- where the check happens ------------------------------------------------------------
    # The whole reason the level is read off the annotation and not the object: a constructor is
    # already the plugin's code running.
    (MANAGER, PLUGIN_RUNNER, 'the plugin constructed first and judged afterwards',
     '''		int needs = descriptor == null ? 0 : descriptor.apiLevel();
		if (!PluginApi.supports(needs)) {
			this.refuse(name, found, "built for a newer client - it needs plugin API "
				+ needs + " and this client has " + PluginApi.LEVEL);
			return;
		}

		try {
			Plugin plugin = (Plugin) type.newInstance();''',
     '''		int needs = descriptor == null ? 0 : descriptor.apiLevel();

		try {
			Plugin plugin = (Plugin) type.newInstance();
			if (!PluginApi.supports(needs)) {
				this.refuse(name, found, "built for a newer client - it needs plugin API "
					+ needs + " and this client has " + PluginApi.LEVEL);
				return;
			}'''),
    (MANAGER, PLUGIN_RUNNER, 'the gate removed, leaving only the LinkageError net under it',
     '''		if (!PluginApi.supports(needs)) {
			this.refuse(name, found, "built for a newer client - it needs plugin API "
				+ needs + " and this client has " + PluginApi.LEVEL);
			return;
		}
''', ''),
    # A declared level that is never read is the same as no gate at all.
    (MANAGER, PLUGIN_RUNNER, 'the declared level read as zero whatever the plugin says',
     'int needs = descriptor == null ? 0 : descriptor.apiLevel();',
     'int needs = 0;'),

    # --- the refusal being visible ----------------------------------------------------------
    # A plugin that vanishes without a word is the failure this whole mechanism exists to stop.
    (MANAGER, PLUGIN_RUNNER, 'the refusal logged but not recorded, so the panel cannot show it',
     'this.refused.add(new Refused(name, found.source, reason));',
     ''),
    (MANAGER, PLUGIN_RUNNER, 'the refusal reason replaced by something that names nothing',
     '''			this.refuse(name, found, "built for a newer client - it needs plugin API "
				+ needs + " and this client has " + PluginApi.LEVEL);''',
     '			this.refuse(name, found, "it would not load");'),
    (MANAGER, PLUGIN_RUNNER, 'the refused list kept across a reload, so stale rows pile up',
     '''		this.entries.clear();
		this.refused.clear();''',
     '		this.entries.clear();'),

    # --- the net under an undeclared jar ----------------------------------------------------
    # The case every pre-levels jar is in: it declares nothing and breaks on startUp.
    (MANAGER, PLUGIN_RUNNER, 'a plugin left enabled after its startUp hit a missing method',
     '''		} catch (LinkageError error) {
			// Half-started is worse than off: unwind whatever it managed before it threw.
			DevLog.log("PLUGIN", entry.name + " failed to start: " + error);
			entry.enabled = false;''',
     '''		} catch (LinkageError error) {
			// Half-started is worse than off: unwind whatever it managed before it threw.
			DevLog.log("PLUGIN", entry.name + " failed to start: " + error);
			entry.enabled = true;'''),

    # --- what the player is shown -----------------------------------------------------------
    # The JVM writes these messages in two shapes and the quoting is what tells them apart, so
    # each of these breaks one shape while leaving the other reading correctly - which is exactly
    # how the first version of this formatter shipped a bug.
    (MANAGER, PLUGIN_RUNNER, 'the bracket cut dropped, so a signature\'s own dots become the name',
     """		int open = text.indexOf('(');
		boolean method = quoted && open >= 0;
		if (open >= 0) {
			text = text.substring(0, open);
		}""",
     """		int open = text.indexOf('(');
		boolean method = quoted && open >= 0;"""),
    (MANAGER, PLUGIN_RUNNER, 'a bracket read as a method whatever shape the message is',
     '		boolean method = quoted && open >= 0;',
     '		boolean method = open >= 0;'),
    # The two halves of the one rule that distinguishes the shapes. Each direction breaks a
    # different real message, and the formatter had the second bug for real.
    (MANAGER, PLUGIN_RUNNER, 'the first token always taken, which reads a signature backwards',
     '			String word = words[quoted ? words.length - 1 - i : i];',
     '			String word = words[i];'),
    (MANAGER, PLUGIN_RUNNER, 'the last token always taken, which reads a sentence backwards',
     '			String word = words[quoted ? words.length - 1 - i : i];',
     '			String word = words[words.length - 1 - i];'),
    (MANAGER, PLUGIN_RUNNER, 'slashes left as slashes, so a missing class reads as a path',
     "		name = name.replace('/', '.');",
     '		name = name;'),
    (MANAGER, PLUGIN_RUNNER, 'only the member kept, so the owner it belongs to is dropped',
     """		int last = name.lastIndexOf('.');
		if (last > 0) {
			int before = name.lastIndexOf('.', last - 1);
			if (before >= 0) {
				name = name.substring(before + 1);
			}
		}""",
     """		int last = name.lastIndexOf('.');
		if (last > 0) {
			name = name.substring(last + 1);
		}"""),
    (MANAGER, PLUGIN_RUNNER, 'an empty message read as an empty sentence',
     """		if (message == null || message.trim().length() == 0) {
			return error.getClass().getSimpleName();
		}""",
     """		if (message == null) {
			return error.getClass().getSimpleName();
		}"""),
    # A message with no dots at all - a bare field name - must come back as itself, not as a
    # sentence and not as nothing.
    (MANAGER, PLUGIN_RUNNER, 'a message with no dots returned raw, padding and all',
     """			return words.length == 1 && text.length() > 0 && text.length() <= 80
				? text : message;""",
     '			return message;'),

    # --- the index side ---------------------------------------------------------------------
    (ENTRY, HUB_RUNNER, 'the index\'s clientApi never read, so the hub offers everything',
     'int api = Json.integer(object, "clientApi", 0);\n\t\tthis.clientApi = api < 0 ? 0 : api;',
     'this.clientApi = 0;'),
    (ENTRY, HUB_RUNNER, 'a negative clientApi believed, which supports every level at once',
     'this.clientApi = api < 0 ? 0 : api;',
     'this.clientApi = api;'),
    (ENTRY, HUB_RUNNER, 'a missing clientApi read as the newest level rather than as unknown',
     'int api = Json.integer(object, "clientApi", 0);',
     'int api = Json.integer(object, "clientApi", 9999);'),
    (ENTRY, HUB_RUNNER, 'the hub\'s own gate answering no to everything',
     'return !jagex2.client.plugin.PluginApi.supports(this.clientApi);',
     'return false;'),
    # An entry the client cannot run must still be LISTED - a player should be able to see that
    # the plugin exists and why they cannot have it. Dropping it is the other failure.
    (ENTRY, HUB_RUNNER, 'a too-new entry dropped from the index instead of shown',
     'return isSafeId(this.id) && this.name.length() > 0 && isHttpUrl(this.url);',
     'return isSafeId(this.id) && this.name.length() > 0 && isHttpUrl(this.url)\n'
     '\t\t\t&& !this.needsNewerClient();'),
    # ---- TRANCHE FIVE: Anti-drag's three new settings, the shared hotkey parse, and the
    # swapper's menu colour - which is the one thing it could not do before level 6.
    (DRAG, PLUGIN_RUNNER, '''the suspend key ignored, so the key does nothing''',
     '''		return suspended || shiftHeld ? CLIENT_DEFAULT_CYCLES : cyclesFor(millis);''',
     '''		return shiftHeld ? CLIENT_DEFAULT_CYCLES : cyclesFor(millis);'''),
    (DRAG, PLUGIN_RUNNER, '''shift ignored, so holding it no longer gives the quick drag back''',
     '''		return suspended || shiftHeld ? CLIENT_DEFAULT_CYCLES : cyclesFor(millis);''',
     '''		return suspended ? CLIENT_DEFAULT_CYCLES : cyclesFor(millis);'''),
    (DRAG, PLUGIN_RUNNER, '''the precedence inverted, so the plugin looks on and does nothing''',
     '''		return suspended || shiftHeld ? CLIENT_DEFAULT_CYCLES : cyclesFor(millis);''',
     '''		return suspended || shiftHeld ? cyclesFor(millis) : CLIENT_DEFAULT_CYCLES;'''),
    (DRAG, PLUGIN_RUNNER, '''the shift setting not read, so shift always resets it''',
     '''			this.resetOnShift && this.ctx.isShiftHeld()));''',
     '''			this.ctx.isShiftHeld()));'''),
    (DRAG, PLUGIN_RUNNER, '''the suspend key not toggling, so once off it never comes back''',
     '''		this.suspended = !this.suspended;''',
     '''		this.suspended = true;'''),
    (DRAG, PLUGIN_RUNNER, '''the suspend key not consumed, so it also lands in the chat box''',
     '''		event.consume();
		this.suspended = !this.suspended;''',
     '''		this.suspended = !this.suspended;'''),
    (DRAG, PLUGIN_RUNNER, '''every key suspending it, not the one a player named''',
     '''		if (!Hotkey.pressed(this.suspendKey, event.key)) {''',
     '''		if (false) {'''),
    # GROUND_RUNNER, not PLUGIN_RUNNER: Hotkey is package-private in builtin and
    # PluginSystemTest is in jagex2.client.plugin, so it cannot reach it. GroundItemsTest is in
    # the same package and owns the rest of the hotkey's checks.
    (HOTKEY, GROUND_RUNNER, '''a blank setting reading as a real key, so nothing can turn the feature off''',
     '''		return want != NONE && keyCode == want;''',
     '''		return keyCode == want;'''),
    (SWAPPER, PLUGIN_RUNNER, '''the promoted row coloured whether the player asked for it or not''',
     '''		if (this.colourSwapped) {''',
     '''		if (true) {'''),
    (SWAPPER, PLUGIN_RUNNER, '''the promoted row never coloured, so the switch does nothing''',
     '''		if (this.colourSwapped) {''',
     '''		if (false) {'''),
    (SWAPPER, PLUGIN_RUNNER, '''the swap colour left hardcoded, so the swatch does nothing''',
     '''				PluginConfig.parseColour(this.swapColour));''',
     '''				PluginConfig.parseColour("00FFFF"));'''),
    # NOT MUTATED: colouring before the swap instead of after has no observable effect, so
    # swapping the two is not a mutation. setLeftClick(best) is
    # swapMenuEntries(best, getLeftClickIndex()), and swapMenuEntries swaps menuColour alongside
    # the option, the action and the three params. So setMenuColour(best, C) then swap leaves
    # colour[top] = C and colour[best] = the top's old colour - which is exactly what swap then
    # setMenuColour(getLeftClickIndex(), C) leaves. Where best is already the left click the
    # swap is a no-op and both write the same index. Every case is identical. The source keeps
    # the later order because it reads in the order it happens, and its comment now says so
    # rather than claiming a bug that cannot occur.
    (SWAPPER, PLUGIN_RUNNER, '''the chat lines silenced whatever the setting says''',
     '''		if (this.announce) {
			this.ctx.addChatMessage(message);
		}''',
     ''''''),

]


def main():
    only = sys.argv[1] if len(sys.argv) > 1 else None
    orig = {}
    for path in (API, MANAGER, ENTRY, DRAG, SWAPPER, HOTKEY):
        with open(path, encoding='utf-8', newline='') as f:
            orig[path] = f.read()
    # Written into a copy, never into the working tree - see mutate_guard.workspace.
    _work, inside = workspace('apilevel')
    muts = [m for m in MUTS if not only or only in m[2]]
    print('running %d of %d mutations' % (len(muts), len(MUTS)))
    bad = loose = 0
    for path, runner, why, find, repl in muts:
        n = orig[path].count(find)
        if n != 1:
            print('  SKIP (pattern %s) %s'
                  % ('not found' if n == 0 else 'matches %d times' % n, why))
            bad += 1
            continue
        try:
            with open(inside(path), 'w', encoding='utf-8', newline='') as f:
                f.write(orig[path].replace(find, repl))
            # TIMED, because a mutated test can hang rather than fail. HubTest once did: its
            # HttpServer runs on a non-daemon thread, so a section that threw before stop() left
            # a JVM alive with nothing to do and this script waiting on it all night. The suite
            # is fixed; the timeout is here so the next one costs ten minutes, not a night.
            r = subprocess.run([sys.executable, inside(runner)], capture_output=True, text=True,
                               timeout=600)
        except subprocess.TimeoutExpired:
            print('  %-5s %-78s %s' % ('HUNG', why, 'the suite never finished - not a catch'))
            loose += 1
            continue
        finally:
            with open(inside(path), 'w', encoding='utf-8', newline='') as f:
                f.write(orig[path])
        fired = [l.strip()[5:].strip() for l in r.stdout.split('\n') if l.startswith('FAIL')]
        which = os.path.basename(runner)[4:-3]
        if r.returncode == 0:
            print('  %-5s %-78s %s' % ('GREEN', why, 'NOT CAUGHT'))
            bad += 1
        elif fired:
            print('  %-5s %-78s %s' % ('red', why, which + ': ' + fired[0][:48]))
        else:
            print('  %-5s %-78s %s' % ('red', why,
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
