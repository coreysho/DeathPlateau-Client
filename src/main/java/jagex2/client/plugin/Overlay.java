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

	/**
	 * Drawn with the scene, UNDER the player names, hitsplats and headicons the client draws over
	 * it. Where a label about something in the world belongs: the ground item names have always
	 * been here, because a pile of bones should not cover the name of the person standing on it.
	 */
	public static final int LAYER_SCENE = 0;

	/**
	 * Drawn after all of that, over everything in the viewport. Where a panel in a corner
	 * belongs - the xp drops are here - and the default.
	 *
	 * Still under the client's own modal panels, whichever layer an overlay picks.
	 */
	public static final int LAYER_SCREEN = 1;

	/**
	 * Which of the two the client draws this overlay at. There are two because the client has two
	 * points in its frame where an overlay can go, and the difference is visible: one is under
	 * the names and hitsplats, the other over them.
	 */
	public int layer() {
		return LAYER_SCREEN;
	}
}
