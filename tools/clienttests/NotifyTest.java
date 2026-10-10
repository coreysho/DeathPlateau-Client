/*
 * Headless test for notifications, sound, and the Idle notifier that uses them.
 *
 * THE CHECK THAT MATTERS MOST is that a plugin cannot make the client talk to the server. The
 * audio loop's catch for a bad sound reports the failure to the server - out.p1isaac(80) - so an
 * unvalidated id from a plugin would be a plugin causing a packet to be sent, which is the one
 * thing this API does not do. The test asks for ids that do not exist and checks nothing reached
 * the queue.
 *
 * The rest is rate limiting - a plugin calling notify every tick must not get a notification
 * every tick - and the notifier's own arm-and-re-arm logic, which is what stops "you are still
 * not training" arriving every six hundred milliseconds.
 *
 * It also checks that nothing here reports health, prayer or special attack. Status bars and the
 * health half of this plugin both shipped briefly and were taken out: this server does not put a
 * player's vitals in front of them, as an orb, a bar, or a popup. Checked rather than assumed, so
 * it cannot quietly come back.
 */
package jagex2.client.plugin.builtin;

import jagex2.client.Client;
import jagex2.client.QolSettings;
import jagex2.client.plugin.PluginManager;
import jagex2.dash3d.ClientPlayer;
import jagex2.sound.Wave;

public class NotifyTest {

	/** A sound id the test puts into the cache, so there is one real sound to ask for. */
	static final int REAL_SOUND = 42;

	static int fails;

	static Client client;
	static PluginManager manager;

	static void check(boolean ok, String what) {
		System.out.println((ok ? "  ok   " : "FAIL   ") + what);
		if (!ok) {
			fails++;
		}
	}

	public static void main(String[] args) throws Exception {
		if (java.awt.GraphicsEnvironment.isHeadless()) {
			System.out.println("  SKIP  no display, so a Client cannot be constructed");
			System.exit(0);
		}
		client = new Client();
		client.ingame = true;
		Client.localPlayer = new ClientPlayer();
		for (int i = 0; i < client.skillLevel.length; i++) {
			client.skillLevel[i] = 99;
			client.skillBaseLevel[i] = 99;
		}
		// One sound in the cache, at a known id. Wave's own unpack needs a real sound file, so
		// the slot is filled directly - what is being tested is which ids reach the queue, not
		// whether the synthesiser works.
		Wave.field1471[REAL_SOUND] = new Wave(-1);

		manager = new PluginManager(client, null, null, null);
		manager.reload();
		for (PluginManager.Entry entry : manager.getPlugins()) {
			if (entry.isEnabled()) {
				manager.setEnabled(entry, false);
			}
		}

		System.out.println("1. sound: which ids are allowed near the queue");
		soundTests();
		System.out.println();
		System.out.println("2. notifications, and not too many of them");
		notifyTests();
		System.out.println();
		System.out.println("3. the Idle notifier plugin");
		idleTests();

		System.out.println();
		System.out.println(fails == 0 ? "ALL PASS" : fails + " FAILED");
		System.exit(fails == 0 ? 0 : 1);
	}

	// ---------------------------------------------------------------- 1

	static void soundTests() {
		jagex2.client.plugin.PluginContext ctx = context();
		check(ctx.hasSound(REAL_SOUND), "the cache has the sound this test put in it");
		check(!ctx.hasSound(REAL_SOUND + 1), "...and not one it did not");
		check(!ctx.hasSound(-1), "a negative id is not a sound");
		check(!ctx.hasSound(999999), "nor is one past the end of the table");

		client.waveCount = 0;
		ctx.playSound(REAL_SOUND);
		check(client.waveCount == 1, "playing a real sound queues it (" + client.waveCount + ")");
		check(client.waveIds[0] == REAL_SOUND, "...under the id that was asked for");
		check(client.waveDelay[0] == 0, "...to play now rather than after a delay");

		// THE ONE THAT MATTERS. An id with no sound behind it makes the audio loop throw, and
		// its catch reports the failure to the SERVER. Letting one through would be a plugin
		// causing a packet to be sent.
		client.waveCount = 0;
		int[] nonsense = { -1, -999, REAL_SOUND + 1, 4999, 5000, 123456, Integer.MAX_VALUE,
			Integer.MIN_VALUE };
		int reached = 0;
		for (int i = 0; i < nonsense.length; i++) {
			// ONE AT A TIME, WITH THE RATE LIMIT WAITED OUT. The first version of this fired all
			// eight in a loop, which meant seven of them were dropped by the limiter and the
			// guard was never consulted - the check passed with the guard deleted entirely.
			quiet();
			client.waveCount = 0;
			try {
				ctx.playSound(nonsense[i]);
			} catch (Throwable error) {
				check(false, "asking for sound " + nonsense[i] + " threw (" + error + ")");
			}
			if (client.waveCount != 0) {
				reached++;
				check(false, "sound " + nonsense[i] + " reached the queue");
			}
		}
		check(reached == 0,
			"no id without a sound behind it reaches the queue, so none can make the client"
				+ " report a synth error to the server (" + reached + " of " + nonsense.length
				+ " got through)");

		// The player's own answer to this question wins.
		client.waveCount = 0;
		client.waveEnabled = false;
		pause();
		ctx.playSound(REAL_SOUND);
		check(client.waveCount == 0, "sound effects turned off means a plugin plays nothing");
		client.waveEnabled = true;

		// Rate limited, or a plugin calling this every frame is a siren.
		client.waveCount = 0;
		pause();
		ctx.playSound(REAL_SOUND);
		ctx.playSound(REAL_SOUND);
		ctx.playSound(REAL_SOUND);
		check(client.waveCount == 1,
			"three sounds in a row play once, not three times (" + client.waveCount + ")");

		// And the client's own queue cap still applies on top.
		client.waveCount = client.waveIds.length;
		pause();
		boolean threw = false;
		try {
			ctx.playSound(REAL_SOUND);
		} catch (Throwable overflowed) {
			threw = true;
		}
		check(!threw && client.waveCount == client.waveIds.length,
			"a full queue is not overflowed, and does not throw trying ("
				+ client.waveCount + (threw ? ", threw" : "") + ")");
		client.waveCount = 0;
	}

	// ---------------------------------------------------------------- 2

	static void notifyTests() {
		jagex2.client.plugin.PluginContext ctx = context();
		// Desktop notifications are off for this run: there is no tray on a virtual display, and
		// what is being tested is the chat line and the rate limit, both of which are ours.
		if (QolSettings.on(QolSettings.DESKTOP_NOTIFY)) {
			QolSettings.toggle(QolSettings.DESKTOP_NOTIFY);
		}
		check(!QolSettings.on(QolSettings.DESKTOP_NOTIFY),
			"desktop notifications can be turned off, which this run does");

		int before = messages();
		pause();
		ctx.notify("something happened");
		check(messages() == before + 1, "a notification always puts a line in the chatbox");

		before = messages();
		ctx.notify("and again");
		ctx.notify("and again");
		check(messages() == before,
			"...but two more straight after add nothing: the rate limit drops them silently");

		pause();
		before = messages();
		ctx.notify(null);
		check(messages() == before, "a null notification is not a notification");
		// Separately, and after the limit has passed: run together, the first one's rate limit
		// would hide whatever the second did.
		pause();
		before = messages();
		ctx.notify("");
		check(messages() == before, "...and neither is an empty one");

		pause();
		before = messages();
		ctx.notify("A title", "with a message");
		check(messages() == before + 1, "a titled notification still says the message in chat");

		// The desktop half, as a decision rather than a delivery - there is no system tray on a
		// virtual display, so the only testable part is whether one is wanted.
		if (!QolSettings.on(QolSettings.DESKTOP_NOTIFY)) {
			QolSettings.toggle(QolSettings.DESKTOP_NOTIFY);
		}
		check(wanted(false),
			"with the window unfocused and the setting on, the desktop is told");
		check(!wanted(true),
			"...and never while the player is looking at the game, which is the whole point");
		QolSettings.toggle(QolSettings.DESKTOP_NOTIFY);
		check(!wanted(false), "with the setting off, not even unfocused");
	}

	// ---------------------------------------------------------------- 3

	static void idleTests() {
		PluginManager.Entry entry = entry("idle-notifier");
		check(entry != null, "Idle notifier is one of the built-in plugins");
		check(entry("alerts") == null && entry("status-bars") == null,
			"...and nothing warns about health, prayer or special attack any more");
		if (entry == null) {
			return;
		}
		check(!entry.isEnabled(), "...and it does not turn itself on");
		manager.setEnabled(entry, true);
		IdleNotifierPlugin plugin = plugin(entry);
		if (plugin == null) {
			return;
		}
		plugin.idleSeconds = 1;

		check(IdleNotifierPlugin.ticksFor(0) == 0, "no seconds is no ticks");
		check(IdleNotifierPlugin.ticksFor(3) == 5,
			"three seconds is five ticks (" + IdleNotifierPlugin.ticksFor(3) + ")");
		check(IdleNotifierPlugin.ticksFor(1) == 2,
			"one second rounds up to two, so a wait is never short ("
				+ IdleNotifierPlugin.ticksFor(1) + ")");

		// Never having trained is not having stopped. A notifier that cannot tell the difference
		// tells you the moment you log in.
		//
		// The pause is load-bearing: section 2 left the notification limit warm, and without
		// waiting it out a notifier that DID fire here would be silenced by the limiter and the
		// check would pass anyway.
		pause();
		int before = messages();
		for (int i = 0; i < 20; i++) {
			plugin.onGameTick(new jagex2.client.plugin.event.GameTick(i));
		}
		check(messages() == before, "no xp ever gained means never having stopped training");

		plugin.onStatChanged(new jagex2.client.plugin.event.StatChanged(0, 50, 1000, 100));
		pause();
		before = messages();
		for (int i = 0; i < 20; i++) {
			plugin.onGameTick(new jagex2.client.plugin.event.GameTick(i));
		}
		check(messages() > before, "after a gain, going quiet does say so");

		before = messages();
		pause();
		for (int i = 0; i < 20; i++) {
			plugin.onGameTick(new jagex2.client.plugin.event.GameTick(i));
		}
		check(messages() == before, "...once, not once a tick, until xp is gained again");

		// A gain restarts the clock, or it fires in the middle of a run of training. Three
		// seconds is five ticks and the gains below come every other one, so the clock never
		// gets past two - the first version of this used a one-second threshold, which is two
		// ticks, and then correctly fired.
		plugin.idleSeconds = 3;
		plugin.onStatChanged(new jagex2.client.plugin.event.StatChanged(0, 50, 1100, 100));
		pause();
		before = messages();
		for (int i = 0; i < 20; i++) {
			plugin.onGameTick(new jagex2.client.plugin.event.GameTick(i));
			if (i % 2 == 0) {
				plugin.onStatChanged(
					new jagex2.client.plugin.event.StatChanged(0, 50, 1100 + i, 10));
			}
		}
		check(messages() == before, "xp still coming in keeps the clock from running out");

		// And once it stops, the same clock does run out.
		before = messages();
		for (int i = 0; i < 20; i++) {
			plugin.onGameTick(new jagex2.client.plugin.event.GameTick(i));
		}
		check(messages() > before, "...and when it stops coming in, it runs out");

		plugin.idleSeconds = 0;
		plugin.onStatChanged(new jagex2.client.plugin.event.StatChanged(0, 50, 2000, 100));
		pause();
		before = messages();
		for (int i = 0; i < 40; i++) {
			plugin.onGameTick(new jagex2.client.plugin.event.GameTick(i));
		}
		check(messages() == before, "zero seconds turns it off");

		// ---- THE THREE NEW SETTINGS. Defaults first: the behaviour this shipped with.
		check(plugin.skills.length() == 0, "no skill filter out of the box");
		check(plugin.repeatSeconds == 0,
			"and it says it ONCE, which is the argument the rest of the plugin is built on");
		check(!plugin.warnWithoutGaining,
			"and will not warn before you have trained, which is the other one");

		// ---- ONLY THESE SKILLS. "Warn me when I stop fishing" must not be re-armed by the
		// combat experience from whatever is attacking you while you fish.
		// THREE SECONDS, NOT ONE. At idleSeconds = 1 the threshold is two ticks and the
		// interfering xp arrives every two, so the clock ran out between gains whether the
		// filter was applied or not - the audit deleted the filter from the handler and this
		// check stayed green. The threshold has to outlast the gap for the filter to be the
		// thing that decides.
		plugin.idleSeconds = 3;
		plugin.skills = "fishing";
		plugin.warnWithoutGaining = false;
		plugin.repeatSeconds = 0;
		// Index 10 is fishing, 0 is attack - see jagex2.client.Stats.
		plugin.onStatChanged(new jagex2.client.plugin.event.StatChanged(10, 50, 5000, 100));
		pause();
		before = messages();
		for (int i = 0; i < 10; i++) {
			plugin.onGameTick(new jagex2.client.plugin.event.GameTick(i));
			if (i % 2 == 0) {
				// Attack xp arriving throughout, which the filter must ignore.
				plugin.onStatChanged(
					new jagex2.client.plugin.event.StatChanged(0, 50, 6000 + i, 50));
			}
		}
		check(messages() > before,
			"with a fishing filter, attack experience does not keep the clock from running out");

		// ...and the filtered skill's own experience does.
		plugin.onStatChanged(new jagex2.client.plugin.event.StatChanged(10, 50, 7000, 100));
		plugin.idleSeconds = 3;
		pause();
		before = messages();
		for (int i = 0; i < 10; i++) {
			plugin.onGameTick(new jagex2.client.plugin.event.GameTick(i));
			if (i % 2 == 0) {
				plugin.onStatChanged(
					new jagex2.client.plugin.event.StatChanged(10, 50, 7000 + i, 50));
			}
		}
		check(messages() == before, "...while fishing experience does");
		plugin.skills = "";
		plugin.idleSeconds = 1;

		// ---- WARN WITHOUT HAVING TRAINED. Off, the plugin waits for a first gain; on, it is a
		// plain "you are not training" timer from the moment you log in.
		IdleNotifierPlugin fresh = freshIdle();
		fresh.idleSeconds = 1;
		pause();
		before = messages();
		for (int i = 0; i < 10; i++) {
			fresh.onGameTick(new jagex2.client.plugin.event.GameTick(i));
		}
		check(messages() == before, "a session with no gain yet still says nothing");
		fresh.warnWithoutGaining = true;
		pause();
		before = messages();
		for (int i = 0; i < 10; i++) {
			fresh.onGameTick(new jagex2.client.plugin.event.GameTick(i));
		}
		check(messages() > before, "...until a player asks for the plain timer");

		// ---- REPEATING, which is a wind-back of the one counter rather than a second timer.
		check(IdleNotifierPlugin.repeatFrom(3, 0) == IdleNotifierPlugin.ticksFor(3),
			"with no repeat the counter is left at the threshold, so only disarming stops it");
		check(IdleNotifierPlugin.repeatFrom(10, 3)
				== IdleNotifierPlugin.ticksFor(10) - IdleNotifierPlugin.ticksFor(3),
			"with a repeat it winds back by the repeat interval");
		check(IdleNotifierPlugin.repeatFrom(1, 10) == 0,
			"a repeat longer than the threshold winds back to zero rather than past it");
		check(IdleNotifierPlugin.repeatFrom(3, -5) == IdleNotifierPlugin.ticksFor(3),
			"and a negative repeat is no repeat");

		// Driven: with a repeat set it speaks more than once without another gain.
		IdleNotifierPlugin repeater = freshIdle();
		repeater.idleSeconds = 1;
		repeater.repeatSeconds = 1;
		repeater.onStatChanged(new jagex2.client.plugin.event.StatChanged(0, 50, 9000, 100));
		int said = 0;
		for (int round = 0; round < 3; round++) {
			pause();
			before = messages();
			for (int i = 0; i < 6; i++) {
				repeater.onGameTick(new jagex2.client.plugin.event.GameTick(i));
			}
			if (messages() > before) {
				said++;
			}
		}
		check(said >= 2,
			"with a repeat set it says it again while still idle, without another gain ("
				+ said + " of 3 rounds)");

		// And with no repeat, the same drive says it once. The pause between rounds is what
		// makes this mean something: the notification limiter would hide a second warning
		// anyway, so without waiting it out both settings would look identical.
		IdleNotifierPlugin once = freshIdle();
		once.idleSeconds = 1;
		once.repeatSeconds = 0;
		once.onStatChanged(new jagex2.client.plugin.event.StatChanged(0, 50, 9500, 100));
		said = 0;
		for (int round = 0; round < 3; round++) {
			pause();
			before = messages();
			for (int i = 0; i < 6; i++) {
				once.onGameTick(new jagex2.client.plugin.event.GameTick(i));
			}
			if (messages() > before) {
				said++;
			}
		}
		check(said == 1, "with no repeat it says it exactly once (" + said + " of 3 rounds)");

		// ---- AND THE WIND-BACK IS TO THE REPEAT INTERVAL, NOT TO ZERO.
		//
		// The repeater above runs at idleSeconds = 1 and repeatSeconds = 1, where repeatFrom
		// winds back to 0 - so "wound back by the repeat" and "reset to zero" are the same
		// number, and the audit swapped one for the other with every check here green. Only a
		// threshold well above the repeat tells them apart: at six seconds against one, the
		// counter lands at eight of its ten ticks and the next warning is two ticks away rather
		// than ten.
		IdleNotifierPlugin wound = freshIdle();
		wound.idleSeconds = 6;
		wound.repeatSeconds = 1;
		wound.onStatChanged(new jagex2.client.plugin.event.StatChanged(0, 50, 9800, 100));
		pause();
		before = messages();
		for (int i = 0; i < IdleNotifierPlugin.ticksFor(6); i++) {
			wound.onGameTick(new jagex2.client.plugin.event.GameTick(i));
		}
		check(messages() > before,
			"a six-second timer runs out after its own ten ticks");
		int repeats = 0;
		for (int round = 0; round < 3; round++) {
			pause();
			before = messages();
			for (int i = 0; i < IdleNotifierPlugin.ticksFor(1); i++) {
				wound.onGameTick(new jagex2.client.plugin.event.GameTick(i));
			}
			if (messages() > before) {
				repeats++;
			}
		}
		check(repeats == 3,
			"...and repeats every two ticks from then on, because the counter is wound back to "
				+ "the repeat interval rather than to zero - wound back to zero it would be ten "
				+ "ticks each time (" + repeats + " of 3)");

		manager.setEnabled(entry, false);
	}

	/**
	 * A second Idle notifier, attached but not registered on the bus.
	 *
	 * Needed because `armed` and the tick counter are per plugin and there is no way to reset
	 * them from outside - which is right, they are not settings. A fresh instance is the honest
	 * way to test a fresh session, and driving its handlers directly is what the checks above
	 * already do to the real one.
	 */
	static IdleNotifierPlugin freshIdle() {
		IdleNotifierPlugin made = new IdleNotifierPlugin();
		try {
			java.lang.reflect.Method attach = jagex2.client.plugin.Plugin.class
				.getDeclaredMethod("attach", jagex2.client.plugin.PluginContext.class,
					jagex2.client.plugin.PluginConfig.class);
			attach.setAccessible(true);
			java.lang.reflect.Field ctx = PluginManager.class.getDeclaredField("ctx");
			ctx.setAccessible(true);
			attach.invoke(made, ctx.get(manager), null);
		} catch (Throwable error) {
			check(false, "cannot attach a fresh Idle notifier (" + error + ")");
		}
		return made;
	}

	// ---------------------------------------------------------------- the plumbing

	/**
	 * Notifier.wanted, which is package-private in jagex2.client and this test is not.
	 *
	 * Reflection rather than making it public: it is the client's own rule, and widening it so a
	 * test can read it would be publishing a method nothing else should call.
	 */
	static boolean wanted(boolean focused) {
		try {
			java.lang.reflect.Method method =
				jagex2.client.Notifier.class.getDeclaredMethod("wanted", boolean.class);
			method.setAccessible(true);
			return ((Boolean) method.invoke(null, Boolean.valueOf(focused))).booleanValue();
		} catch (Throwable error) {
			check(false, "cannot reach Notifier.wanted (" + error + ")");
			return false;
		}
	}

	/** Waits out the shorter sound limit. */
	static void quiet() {
		try {
			Thread.sleep(300);
		} catch (InterruptedException ignored) {
			Thread.currentThread().interrupt();
		}
	}

	/** Waits out the rate limit, so the next call is measuring something else. */
	static void pause() {
		try {
			Thread.sleep(1600);
		} catch (InterruptedException ignored) {
			Thread.currentThread().interrupt();
		}
	}

	/** How many lines the chatbox holds, which is how a notification is counted. */
	static int messages() {
		int count = 0;
		for (int i = 0; i < client.messageText.length; i++) {
			if (client.messageText[i] != null) {
				count++;
			}
		}
		return count;
	}

	static jagex2.client.plugin.PluginContext context() {
		try {
			java.lang.reflect.Field field = PluginManager.class.getDeclaredField("ctx");
			field.setAccessible(true);
			return (jagex2.client.plugin.PluginContext) field.get(manager);
		} catch (Throwable error) {
			throw new IllegalStateException("cannot reach the context: " + error);
		}
	}

	static IdleNotifierPlugin plugin(PluginManager.Entry entry) {
		try {
			java.lang.reflect.Field field = PluginManager.Entry.class.getDeclaredField("plugin");
			field.setAccessible(true);
			return (IdleNotifierPlugin) field.get(entry);
		} catch (Throwable error) {
			check(false, "cannot reach the Idle notifier plugin (" + error + ")");
			return null;
		}
	}

	static PluginManager.Entry entry(String key) {
		for (PluginManager.Entry e : manager.getPlugins()) {
			if (e.key.equals(key)) {
				return e;
			}
		}
		return null;
	}
}
