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
	 * What this overlay's dragged position is saved under, set by the manager.
	 *
	 * "<plugin key>#<index among that plugin's overlays>". Not something the overlay chooses: a
	 * plugin naming its own overlays would be another thing to get wrong, and an index is stable
	 * as long as startUp adds them in the same order, which is the only order there is.
	 *
	 * Reordering or removing an overlay therefore puts the ones after it back where the plugin
	 * draws them. That is the right trade: the alternative is a name every plugin has to invent
	 * and keep, for a feature most of them never think about.
	 */
	String positionKey;

	/** Where the last frame drew this overlay, or null if it drew nothing. See OverlayGraphics. */
	int[] lastBounds;

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
	 * Whether a player can drag this overlay somewhere else. True for the ones that float.
	 *
	 * FALSE FOR AN OVERLAY THAT IS ATTACHED TO SOMETHING. A panel in a corner belongs wherever
	 * its owner puts it, and the drag is a feature. An overlay drawn ON a window - numbers on
	 * the cells of a puzzle box, say - is not a panel: dragging it moves the numbers off the
	 * pieces they label, and there is nothing useful on the other side of that drag. Such an
	 * overlay says so here and the manager stops offering, which also keeps it out of the way
	 * of the ones a player does want to move.
	 */
	public boolean movable() {
		return true;
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
	 * Drawn on the SIDEBAR, over the open tab, in the sidebar's own coordinates: (0, 0) is the
	 * top-left of the panel under the tab row, not of the screen.
	 *
	 * For marking something the player has to click in the inventory - the item a quest step
	 * wants - which neither of the other two can reach: both of those draw into the viewport
	 * raster, and in fixed mode the sidebar is a different raster entirely, drawn beside it.
	 *
	 * THE SIDEBAR IS CACHED, so an overlay here only appears in the frames the client repaints
	 * it. A plugin that wants a mark to stay put calls {@link
	 * jagex2.client.plugin.PluginContext#requestSidebarRedraw()} on a tick while it has
	 * something to draw, and nothing is repainted for a plugin that wants nothing.
	 */
	public static final int LAYER_SIDEBAR = 2;

	/**
	 * On the CHATBOX, over whatever interface is open there - a conversation, a "Select an
	 * Option" box, a level-up.
	 *
	 * Its coordinates are the chat area's own, as the sidebar's are the sidebar's, so (0, 0) is
	 * the top left of the box and not of the screen. Use {@link
	 * jagex2.client.plugin.PluginContext#getDialogueOptionBounds(int)} to find a line in them
	 * rather than measuring text, because the option components carry their own geometry.
	 *
	 * THE CHATBOX IS CACHED like the sidebar, and this pass runs in the frames the client
	 * repaints it. A conversation repaints on its own whenever anything in it moves, so a mark on
	 * an option that is sitting still needs {@link
	 * jagex2.client.plugin.PluginContext#requestChatRedraw()}.
	 */
	public static final int LAYER_CHAT = 3;

	/**
	 * Which of the four the client draws this overlay at. There are four because the client has
	 * four points in its frame where an overlay can go, and the difference is visible: one is
	 * under the names and hitsplats, one over them, one is on the sidebar and one in the chatbox.
	 */
	public int layer() {
		return LAYER_SCREEN;
	}
}
