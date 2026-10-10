#!/usr/bin/env python3
"""Mutation test for tools/clienttests/run_notifytest.py.

Breaks notifications, sound and the Idle notifier one plausible way at a time and checks
run_alertstest notices - and notices by NAMING a check, not by crashing.

The one worth the most is the sound id guard. The client's audio loop reports a playback failure
TO THE SERVER, so an id that reaches the queue without a sound behind it is a plugin causing a
packet to be sent. Every way of loosening that check is below, and each leaves the client
compiling and running perfectly.

    python3 tools/clienttests/mutate_notifytest.py [filter]
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
CONTEXT = os.path.join(ROOT, 'src/main/java/jagex2/client/plugin/PluginContext.java')
IDLE = os.path.join(ROOT,
                    'src/main/java/jagex2/client/plugin/builtin/IdleNotifierPlugin.java')
NOTIFIER = os.path.join(ROOT, 'src/main/java/jagex2/client/Notifier.java')
FILTER = os.path.join(ROOT, 'src/main/java/jagex2/client/plugin/builtin/SkillFilter.java')
RUNNER = os.path.join(HERE, 'run_notifytest.py')

MUTS = [
    # --- the guard that keeps a plugin from making the client talk to the server ------------
    (CONTEXT, 'the sound id not checked at all, so a bad one reports a synth error upstream',
     '''		if (id < 0 || id >= jagex2.sound.Wave.field1471.length
			|| jagex2.sound.Wave.field1471[id] == null) {
			DevLog.log("PLUGIN", "a plugin asked for sound " + id + ", which this cache has none of");
			return;
		}
''', ''),
    (CONTEXT, 'only the lower bound checked, so an id past the table gets through',
     'if (id < 0 || id >= jagex2.sound.Wave.field1471.length\n\t\t\t|| jagex2.sound.Wave.field1471[id] == null) {',
     'if (id < 0) {'),
    (CONTEXT, 'the table bound checked but not whether a sound is actually there',
     'if (id < 0 || id >= jagex2.sound.Wave.field1471.length\n\t\t\t|| jagex2.sound.Wave.field1471[id] == null) {',
     'if (id < 0 || id >= jagex2.sound.Wave.field1471.length) {'),
    (CONTEXT, 'the bound off by one, so the slot past the end is allowed',
     'id >= jagex2.sound.Wave.field1471.length\n\t\t\t|| jagex2.sound.Wave.field1471[id] == null) {',
     'id > jagex2.sound.Wave.field1471.length\n\t\t\t|| jagex2.sound.Wave.field1471[id] == null) {'),
    (CONTEXT, 'hasSound answering yes to everything, so a plugin cannot check either',
     '''		return id >= 0 && id < jagex2.sound.Wave.field1471.length
			&& jagex2.sound.Wave.field1471[id] != null;''',
     '		return true;'),

    # --- the rest of playSound ---------------------------------------------------------------
    (CONTEXT, 'the client queue overflowed past its fifty slots',
     'if (this.client.waveCount < this.client.waveIds.length) {',
     'if (true) {'),
    (CONTEXT, 'the player having sound off ignored',
     '''		if (!this.client.waveEnabled || Client.lowMem) {
			return;
		}
''', ''),
    (CONTEXT, 'sounds not rate limited, so a plugin can hold a note every frame',
     '''		if (now - this.lastSound < SOUND_EVERY_MS) {
			return;
		}
''', ''),
    (CONTEXT, 'a queued sound given a delay, so it plays late or not at all',
     'this.client.waveDelay[this.client.waveCount] = 0;',
     'this.client.waveDelay[this.client.waveCount] = 100;'),

    # --- notifications ------------------------------------------------------------------------
    (CONTEXT, 'notifications not rate limited, so one plugin can fill the chatbox',
     '''		if (now - this.lastNotify < NOTIFY_EVERY_MS) {
			return;
		}
''', ''),
    (CONTEXT, 'the chat line dropped, so a notification is invisible with the window focused',
     '		this.addChatMessage(message);\n\t\tjagex2.client.Notifier.desktop(title, message, this.client.hasFocus);',
     '		jagex2.client.Notifier.desktop(title, message, this.client.hasFocus);'),
    (CONTEXT, 'an empty notification allowed through',
     '''		if (message == null || message.length() == 0) {
			return;
		}
		long now = System.currentTimeMillis();
		if (now - this.lastNotify < NOTIFY_EVERY_MS) {''',
     '''		long now = System.currentTimeMillis();
		if (now - this.lastNotify < NOTIFY_EVERY_MS) {'''),
    # The focus rule, which is what keeps this from being an annoyance. Inverted, every
    # notification pops a tray balloon at someone already looking at the game.
    (NOTIFIER, 'the focus check inverted, so the tray fires while you are looking at the game',
     'return !focused && QolSettings.on(QolSettings.DESKTOP_NOTIFY);',
     'return focused && QolSettings.on(QolSettings.DESKTOP_NOTIFY);'),
    (NOTIFIER, 'the player\'s desktop-notification switch ignored',
     'return !focused && QolSettings.on(QolSettings.DESKTOP_NOTIFY);',
     'return !focused;'),

    # --- the Idle notifier ---------------------------------------------------------------
    # NOT the field initialiser - startUp assigns over it, so mutating the field changes
    # nothing and no test could see it. The assignment that decides is the one in startUp.
    (IDLE, 'the notifier armed from the start, so logging in is "you have stopped training"',
     '\t\tthis.armed = false;\n\t\tthis.sinceGain = 0;',
     '\t\tthis.armed = true;\n\t\tthis.sinceGain = 0;'),
    (IDLE, 'the notifier left armed after firing, so it repeats every tick',
     '''		this.sinceGain = repeatFrom(this.idleSeconds, this.repeatSeconds);
		this.armed = false;
	}''',
     '''		this.sinceGain = repeatFrom(this.idleSeconds, this.repeatSeconds);
	}'''),
    (IDLE, 'the clock not reset by a gain, so it fires in the middle of training',
     '''		if (event.gained > 0
				&& SkillFilter.allows(this.ctx.getSkillName(event.skill), this.skills)) {
			this.sinceGain = 0;
			this.armed = true;
		}''',
     '''		if (event.gained > 0) {
			this.armed = true;
		}'''),
    (IDLE, 'zero seconds not turning it off',
     'if (!this.ctx.isLoggedIn() || this.idleSeconds <= 0) {',
     'if (!this.ctx.isLoggedIn()) {'),
    (IDLE, 'seconds read as ticks, so every wait is most of a minute short',
     'return (seconds * 1000 + TICK_MS - 1) / TICK_MS;',
     'return seconds;'),
    (IDLE, 'the tick conversion rounding down, so a wait finishes early',
     'return (seconds * 1000 + TICK_MS - 1) / TICK_MS;',
     'return seconds * 1000 / TICK_MS;'),
    (IDLE, 'the wait compared the wrong way, so it fires on the next tick',
     'if (this.sinceGain < ticksFor(this.idleSeconds)) {',
     'if (this.sinceGain > ticksFor(this.idleSeconds)) {'),
    # ---- TRANCHE FOUR: the skill filter, the plain timer, and the repeat - which is a wind-back
    # of the one counter this plugin keeps rather than a second timer.
    (IDLE, '''the skill filter not applied, so any experience re-arms the clock''',
     '''		if (event.gained > 0
				&& SkillFilter.allows(this.ctx.getSkillName(event.skill), this.skills)) {''',
     '''		if (event.gained > 0) {'''),
    (IDLE, '''the warning given before a player has trained at all''',
     '''		if (!this.armed && !this.warnWithoutGaining && this.repeatSeconds <= 0) {''',
     '''		if (false) {'''),
    (IDLE, '''the plain timer never reached, so the setting does nothing''',
     '''		if (!this.armed && !this.warnWithoutGaining && this.repeatSeconds <= 0) {''',
     '''		if (!this.armed) {'''),
    (IDLE, '''the repeat never reached, so it only ever says it once''',
     '''		if (!this.armed && !this.warnWithoutGaining && this.repeatSeconds <= 0) {''',
     '''		if (!this.armed && !this.warnWithoutGaining) {'''),
    (IDLE, '''the counter not wound back, so a repeat fires every tick''',
     '''		this.sinceGain = repeatFrom(this.idleSeconds, this.repeatSeconds);''',
     '''		this.sinceGain = 0;'''),
    # NOT MUTATED: repeatFrom's `if (repeatSeconds <= 0) return threshold;` has no observable
    # effect, so deleting it is not a mutation. ticksFor() already returns 0 for any
    # non-positive seconds, so without the guard the fallthrough computes
    # `threshold - ticksFor(repeatSeconds)` = `threshold - 0` = threshold, and the `back < 0`
    # clamp cannot fire because threshold is never negative. Every input gives the same answer
    # either way. The guard stays because it says what no-repeat means without making the
    # reader chase ticksFor, and ticksFor's own clamp is mutated and caught separately.
    (IDLE, '''a repeat longer than the threshold winding back past zero''',
     '''		return back < 0 ? 0 : back;''',
     '''		return back;'''),
    (IDLE, '''the repeat interval ignored, so it repeats at the idle threshold instead''',
     '''		int back = threshold - ticksFor(repeatSeconds);''',
     '''		int back = 0;'''),

]


def main():
    only = sys.argv[1] if len(sys.argv) > 1 else None
    orig = {}
    for path in (CONTEXT, IDLE, NOTIFIER, FILTER):
        with open(path, encoding='utf-8', newline='') as f:
            orig[path] = f.read()
    # Written into a copy, never into the working tree - see mutate_guard.workspace.
    _work, inside = workspace('notifytest')
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
                               timeout=900)
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
