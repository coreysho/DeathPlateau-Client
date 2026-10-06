package jagex2.client.plugin;

/**
 * Something a plugin draws over the game viewport.
 *
 * Overlays belong to a plugin: adding one is how a plugin draws, and every overlay it added is
 * dropped when it is turned off, so a disabled plugin leaves nothing on screen. They render every
 * frame, in {@link #priority()} order, under the client's own panels - the settings, swaps, ground
 * item and plugin panels always stay on top of them, because those are modal and must never be
 * buried by something a plugin drew.
 */
public abstract class Overlay {

	/** Set by the manager when the overlay is added, so a throw can be blamed on the right plugin. */
	Plugin owner;

	/**
	 * Draws the overlay. Coordinates are viewport-local - see {@link OverlayGraphics}.
	 *
	 * Called every frame on the client thread. Exceptions are caught and logged, and an overlay
	 * that keeps throwing takes its plugin down with it rather than being left to throw forever.
	 */
	public abstract void render(OverlayGraphics g);

	/** Lower renders first, so a higher number draws on top. Equal priorities keep insertion order. */
	public int priority() {
		return 0;
	}
}
