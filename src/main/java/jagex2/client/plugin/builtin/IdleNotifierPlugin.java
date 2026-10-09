package jagex2.client.plugin.builtin;

import jagex2.client.plugin.ConfigItem;
import jagex2.client.plugin.ConfigList;
import jagex2.client.plugin.Plugin;
import jagex2.client.plugin.PluginDescriptor;
import jagex2.client.plugin.Subscribe;
import jagex2.client.plugin.event.GameTick;
import jagex2.client.plugin.event.StatChanged;

/**
 * Says when you have stopped gaining experience: the furnace ran out, the fight ended, the tree
 * fell.
 *
 * WHAT IT DELIBERATELY DOES NOT WATCH. An earlier version of this also warned on low hitpoints
 * and low prayer, and those are gone on purpose. This server does not put a player's health,
 * prayer or special attack in front of them - not as an orb, not as a bar, and not as a popup
 * either. A notification is a quieter way of doing the same thing, and quieter was not the
 * objection. Experience going quiet is about what you are doing, not about how close to death
 * you are, which is why this half stayed.
 *
 * It also cannot do what RuneLite's idle notifier does with animations - noticing the moment a
 * woodcutting swing stops rather than waiting for the experience that would have followed.
 * Animation state is not something the plugin context exposes, and a notifier that guessed at it
 * would fire at the wrong moments. Waiting for the xp is later but it is never wrong.
 *
 * FIRES ONCE AND RE-ARMS ON THE NEXT GAIN. A warning that repeats while a condition holds is a
 * warning nobody reads, and "you are still not training" every six hundred milliseconds would be
 * the worst version of that.
 */
@PluginDescriptor(
	name = "Idle notifier",
	description = "Says when you stop gaining experience",
	key = "idle-notifier",
	apiLevel = 2
)
public final class IdleNotifierPlugin extends Plugin {

	/** A game tick, in milliseconds. The clock this counts in. */
	private static final int TICK_MS = 600;

	@ConfigItem(keyName = "idleSeconds", name = "Warn after this many seconds without xp",
		description = "0 turns it off")
	public int idleSeconds = 30;

	/**
	 * Which sound to play, or -1 for none.
	 *
	 * NO USEFUL DEFAULT EXISTS, which is why this is off rather than set to something plausible.
	 * Sound ids address entries in the cache and nothing in the client hardcodes one - every
	 * sound the game plays is an id the server sent - so the right id depends on the cache this
	 * server ships and cannot be known from the code. The page has a Test row: put a number in,
	 * press it, keep the one you like.
	 */
	@ConfigItem(keyName = "soundId", name = "Alert sound id",
		description = "-1 for silence. Use the Test row to find one this cache has")
	public int soundId = -1;

	@ConfigItem(keyName = "skills", name = "Only count these skills, comma separated",
		description = "Part of a name is enough. Blank is any experience at all")
	public String skills = "";

	/**
	 * Say it again every this many seconds while still idle. 0 says it once.
	 *
	 * ZERO BY DEFAULT, because a warning that repeats while a condition holds is a warning
	 * nobody reads - which is the argument the rest of this plugin is built on and is not
	 * weakened by offering the choice. It is here for the case the one-shot does not cover: a
	 * player who alt-tabbed away and wants telling again when they look back.
	 */
	@ConfigItem(keyName = "repeatSeconds", name = "Repeat every this many seconds",
		description = "0 says it once and waits for the next gain")
	public int repeatSeconds = 0;

	/**
	 * Warn even if no experience has been gained this session.
	 *
	 * Off, because standing in a bank having gained nothing all session is not having STOPPED
	 * training, and a notifier that cannot tell the difference tells you the moment you log in.
	 * On, it is a plain "you are not training" timer, which is what someone who logs in to do one
	 * thing may actually want.
	 */
	@ConfigItem(keyName = "warnWithoutGaining", name = "Warn without having trained first")
	public boolean warnWithoutGaining = false;

	/**
	 * False until experience has been gained at least once.
	 *
	 * Standing in a bank having gained nothing all session is not having stopped training, and a
	 * notifier that cannot tell the difference tells you the moment you log in.
	 */
	private boolean armed;

	/** Ticks since the last gain. */
	private int sinceGain;

	protected void startUp() {
		this.armed = false;
		this.sinceGain = 0;
		try {
			this.addPanel("Idle notifier", "wrench", this.testList());
		} catch (Throwable olderClient) {
			// No rail pages in this client. Everything else works; the sound is just harder to
			// try out.
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged event) {
		// Filtered, so "warn me when I stop fishing" is not re-armed by the combat experience
		// from whatever is attacking you while you fish.
		if (event.gained > 0
				&& SkillFilter.allows(this.ctx.getSkillName(event.skill), this.skills)) {
			this.sinceGain = 0;
			this.armed = true;
		}
	}

	@Subscribe
	public void onGameTick(GameTick event) {
		if (!this.ctx.isLoggedIn() || this.idleSeconds <= 0) {
			return;
		}
		// ARMED, or asked to warn without having trained, or already warned once and asked to
		// repeat. The third is why this is not simply `armed`: a warning sets it false, and a
		// repeat has to survive that.
		if (!this.armed && !this.warnWithoutGaining && this.repeatSeconds <= 0) {
			return;
		}
		this.sinceGain++;
		if (this.sinceGain < ticksFor(this.idleSeconds)) {
			return;
		}
		this.ctx.notify("Death Plateau",
			"You have not gained experience for " + this.idleSeconds + " seconds.");
		if (this.soundId >= 0) {
			this.ctx.playSound(this.soundId);
		}
		// REPEATING IS A WIND-BACK, not a second timer. Setting the counter to the repeat
		// interval short of the threshold means the next warning is repeatSeconds away and
		// nothing else has to be tracked - and with a 0 repeat the counter stays past the
		// threshold, so disarming is what stops it rather than the arithmetic.
		this.sinceGain = repeatFrom(this.idleSeconds, this.repeatSeconds);
		this.armed = false;
	}

	/**
	 * What the tick counter becomes after a warning.
	 *
	 * With no repeat, the counter is left at the threshold: it has already fired and `armed` is
	 * what stops it firing again, so the number only has to not go backwards. With a repeat, it
	 * winds back to repeatSeconds short of the threshold, which is the next warning's due time
	 * expressed in the one counter this plugin keeps.
	 */
	static int repeatFrom(int idleSeconds, int repeatSeconds) {
		int threshold = ticksFor(idleSeconds);
		if (repeatSeconds <= 0) {
			return threshold;
		}
		int back = threshold - ticksFor(repeatSeconds);
		return back < 0 ? 0 : back;
	}

	/** How many game ticks a number of seconds is, rounded up so "5 seconds" is never four. */
	static int ticksFor(int seconds) {
		if (seconds <= 0) {
			return 0;
		}
		return (seconds * 1000 + TICK_MS - 1) / TICK_MS;
	}

	/**
	 * A page with one row: play the configured sound, and say whether the cache has it.
	 *
	 * Here because a sound id is a number with no meaning until you hear it, and the alternative
	 * is editing plugins.dat and relaunching to find out you picked a silent one.
	 */
	private ConfigList testList() {
		return new ConfigList() {

			public int size() {
				return 1;
			}

			public String label(int index) {
				return "Alert sound";
			}

			public String value(int index) {
				int id = IdleNotifierPlugin.this.soundId;
				if (id < 0) {
					return "off";
				}
				return IdleNotifierPlugin.this.ctx.hasSound(id) ? String.valueOf(id)
					: id + " (missing)";
			}

			public String detail(int index) {
				return IdleNotifierPlugin.this.soundId < 0
					? "Set a sound id in the settings, then press Test"
					: "Press Test to hear it";
			}

			public String action(int index) {
				return "Test";
			}

			public void onAction(int index) {
				int id = IdleNotifierPlugin.this.soundId;
				if (id < 0) {
					IdleNotifierPlugin.this.ctx.addChatMessage("No alert sound is set.");
				} else if (!IdleNotifierPlugin.this.ctx.hasSound(id)) {
					IdleNotifierPlugin.this.ctx.addChatMessage("This cache has no sound " + id + ".");
				} else {
					IdleNotifierPlugin.this.ctx.playSound(id);
				}
			}

			public boolean removable(int index) {
				return false;
			}
		};
	}
}
