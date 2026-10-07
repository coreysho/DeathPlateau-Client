package jagex2.client.plugin.event;

/**
 * One frame of the client loop - 50 a second, the rate GameShell drives update() at. Use it for
 * anything that should feel immediate (reading the mouse, animating an overlay). For game logic
 * prefer {@link GameTick}, which is paced by the server and so does not run faster on a faster
 * machine.
 */
public final class ClientTick {

	/** The client's own loop counter, as seen by Client.loopCycle. */
	public final int cycle;

	public ClientTick(int cycle) {
		this.cycle = cycle;
	}
}
