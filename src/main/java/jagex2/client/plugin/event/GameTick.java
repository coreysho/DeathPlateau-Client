package jagex2.client.plugin.event;

/**
 * One server tick - fired when the player-info packet lands, which the server sends once per 600ms
 * cycle. This is the clock game logic runs on, so anything counting "turns" (ticks until an action
 * finishes, xp per hour, idle time) should count these rather than frames.
 *
 * It stops while the connection is down, which is the honest answer: no packet, no tick.
 */
public final class GameTick {

	/** Ticks seen since the client started. Resets only on a fresh client, not on logout. */
	public final int count;

	public GameTick(int count) {
		this.count = count;
	}
}
