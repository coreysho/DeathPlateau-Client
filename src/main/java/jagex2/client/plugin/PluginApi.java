package jagex2.client.plugin;

/**
 * What this client's plugin API can do, as a number a plugin can be checked against.
 *
 * WHY THIS EXISTS. A plugin is compiled against the client, and a jar built against a newer
 * client than the one running it does not fail politely: the first call into a method that is
 * not there throws {@link NoSuchMethodError}, mid-frame, and the manager's answer to a plugin
 * that keeps throwing is to turn the whole plugin off. The player sees a plugin that installed
 * cleanly and then quietly stopped working, with nothing anywhere saying why. That happened
 * here once already, with a jar that called addPanel against a client that had no addPanel.
 *
 * So the API gets a level, a plugin says which level it needs, and a mismatch is caught in one
 * place with one sentence: "built for a newer client". The number only ever goes up, and only
 * when something is ADDED - a level is a promise that everything up to it is present, which is
 * why nothing in it may ever be removed or have its meaning changed. Take something away and
 * every plugin's declared level becomes a lie.
 *
 * <h3>The levels</h3>
 * <dl>
 * <dt>0</dt><dd>Does not say. Every plugin built before levels existed is in this bucket, so it
 *     means "run it and hope" - which is what the client did for all of them anyway. Never
 *     refused; the {@link LinkageError} path in PluginManager is the only net under it.</dd>
 * <dt>1</dt><dd>Overlays, config items, config lists with a value and a progress bar, sidebar
 *     panels ({@code addPanel}), and the context as it was then: skills, position, the menu
 *     with swapping and left-click, chat messages out, ground item piles, modifier keys, drag
 *     delay, tile and world projection.</dd>
 * <dt>2</dt><dd>{@code ctx.notify}, {@code ctx.playSound} and {@code ctx.hasSound}.
 *     <p>
 *     Note what is NOT here. Alt-drag arrived in the same stretch and did not move the level,
 *     because a plugin calls nothing for it: overlays became movable underneath them. A level
 *     only goes up when there is something new to CALL - a level is a promise to a compiler,
 *     not a changelog.</dd>
 * <dt>3</dt><dd>The cursor: {@code ctx.getMouseX}, {@code ctx.getMouseY},
 *     {@code ctx.getHoverTileX}, {@code ctx.getHoverTileZ}, and {@code ctx.sceneToWorldX} /
 *     {@code sceneToWorldZ} for keeping a tile across a region change.</dd>
 * <dt>4</dt><dd>Who else is in the scene: {@code ctx.getNpcs} and {@code ctx.getPlayers},
 *     returning {@link Actor} snapshots - name, combat level, npc id, fine and tile position,
 *     size, and whether it is you - sorted nearest first.
 *     <p>
 *     Reads only. An Actor is a copy taken on the game thread and carries no handle on the
 *     entity, so there is nothing on it to click, attack or follow: the client still owns input
 *     and the socket. That is also why it is a snapshot rather than a live object - the client's
 *     own npc config is a 20-entry round-robin cache that recycles under you, which Actor's
 *     comment covers.</dd>
 * </dl>
 */
public final class PluginApi {

	/**
	 * The highest API level this client provides. Compared against
	 * {@link PluginDescriptor#apiLevel()} before a plugin is constructed.
	 */
	public static final int LEVEL = 4;

	private PluginApi() {
	}

	/** Whether a plugin asking for this level can run here. Level 0 - "does not say" - always can. */
	public static boolean supports(int apiLevel) {
		return apiLevel <= LEVEL;
	}
}
