package jagex2.client.plugin.builtin;

import jagex2.client.plugin.ConfigItem;
import jagex2.client.plugin.Plugin;
import jagex2.client.plugin.PluginDescriptor;
import jagex2.client.plugin.Subscribe;
import jagex2.client.plugin.event.ClientTick;

/**
 * An item only starts dragging once you have held it down for a moment.
 *
 * WHY THIS ONE IS A PLUGIN when "Hide roofs" and "Escape closes interfaces" went back to the F9
 * panel: those are a boolean and nothing else, and a plugin that is one boolean is a worse
 * switch than a row on the settings panel. This has a number in it. A hold time is a thing
 * people want to tune - too short and a fast switch whose mouse is still moving drags instead of
 * clicking, too long and laying out an inventory becomes a chore - and the right value depends
 * on how someone plays and how their mouse behaves. As a switch it was one hardcoded compromise
 * with an on and an off; as a plugin it is a box you type a number into.
 *
 * WHAT IT ACTUALLY CHANGES. The client turns a held click into a drag after a number of client
 * cycles, five by default - Old School's own figure, 100ms. This raises it. 200ms is the value
 * the switch used and stays the default here; 600ms is RuneLite's, which made rearranging
 * switches a chore when it was tried, and is left as something a player can choose rather than
 * something they are given.
 *
 * LIKE HIDE ROOFS BEFORE IT, this is a setting the client reads rather than an event a plugin
 * handles: the drag decision happens inside the input loop, in the middle of a method no event
 * could usefully fire from. So the plugin says what it wants and the loop reads it, and turning
 * the plugin off puts the client's own figure back.
 */
@PluginDescriptor(
	name = "Anti-drag",
	description = "An item only drags once you have held it down for a moment",
	key = "anti-drag",
	enabledByDefault = true,
	legacySetting = "anti_drag"
)
public final class AntiDragPlugin extends Plugin {

	/**
	 * In milliseconds, because that is the unit the player is thinking in. The client counts in
	 * cycles of 20ms and this is converted on the way in - a round number of milliseconds is
	 * easier to reason about than "ten cycles", and nobody should have to know the frame rate to
	 * set a hold time.
	 */
	@ConfigItem(
		keyName = "holdMillis",
		name = "Hold before an item drags",
		description = "Milliseconds. The client's own figure is 100; this one is 200."
	)
	public int holdMillis = 200;

	/** What the client does on its own, and what it goes back to when this is turned off. */
	private static final int CLIENT_DEFAULT_CYCLES = 5;

	private static final int MILLIS_PER_CYCLE = 20;

	/** A hold has to be at least one cycle, and an hour of holding is a typo rather than a wish. */
	private static final int MIN_CYCLES = 1;
	private static final int MAX_CYCLES = 100;

	protected void startUp() {
		this.apply();
	}

	protected void shutDown() {
		this.ctx.setDragDelay(CLIENT_DEFAULT_CYCLES);
	}

	/**
	 * Re-applied every frame rather than once at startup.
	 *
	 * There is no "a setting changed" event, and a value typed into the config page would
	 * otherwise do nothing until the plugin was toggled off and on - which is the kind of thing
	 * a player reads as broken rather than as unimplemented. One int assignment a frame is not
	 * a cost worth an event for.
	 */
	@Subscribe
	public void onClientTick(ClientTick event) {
		this.apply();
	}

	private void apply() {
		this.ctx.setDragDelay(cyclesFor(this.holdMillis));
	}

	/** The hold time in client cycles, clamped so a nonsense value cannot wedge dragging. */
	public static int cyclesFor(int millis) {
		// Rounded rather than truncated: 190ms asked for is nearer ten cycles than nine, and a
		// player typing a round number should not land one cycle under it.
		int cycles = (millis + MILLIS_PER_CYCLE / 2) / MILLIS_PER_CYCLE;
		if (cycles < MIN_CYCLES) {
			return MIN_CYCLES;
		}
		return cycles > MAX_CYCLES ? MAX_CYCLES : cycles;
	}
}
