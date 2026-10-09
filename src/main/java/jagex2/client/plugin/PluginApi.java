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
 * <dt>5</dt><dd>Two richer editors for a String setting: {@code @ConfigItem(colour = true)} for a
 *     swatch and a picker, and {@code @ConfigItem(choices = {...})} for a drop-down. Plus
 *     {@code PluginConfig.parseColour} and {@code PluginConfig.toHex}, which are the one place a
 *     hex setting becomes a colour.
 *     <p>
 *     Both are annotation elements with defaults, so a plugin built for an older client still
 *     compiles and still runs here - but a plugin that SETS one needs a client that reads it, and
 *     on an older client the setting would silently fall back to a plain text box. That is the
 *     kind of quiet wrong-looking UI a level exists to prevent, so it moves.</dd>
 * <dt>6</dt><dd>Restyling the right-click menu: {@code ctx.setMenuColour} to draw one row in
 *     another colour, {@code ctx.deprioritiseMenuEntry} to move one to the bottom, and
 *     {@code ctx.isGroundItemTake} to ask whether a row is a Take without knowing the action id.
 * <dt>7</dt><dd>{@code ctx.isMenuOpen}, for an overlay near the cursor that should stand aside
 *     while a menu is open, and {@code OverlayGraphics.fontFor} with {@code FONT_CHOICES} - one
 *     list of the three sizes and one parse of it, rather than a copy in each plugin.</dd>
 * <dt>8</dt><dd>{@code ctx.getTrueTileX} / {@code getTrueTileZ}: the tile the SERVER has the
 *     player on, which during a walk is ahead of the one they appear to stand on.</dd>
 *     <p>
 *     STILL NO TEXT AND NO ACTION. A row's words and what it does are not writable, and that is
 *     the point rather than an omission: a plugin that could relabel a row could put "Bank"
 *     where "Attack" is. A colour and a position can only change how the menu looks and what
 *     order it offers things in - the client still decides what each row does, and nothing here
 *     adds or removes a row.</dd>
 * </dl>
 */
public final class PluginApi {

	/**
	 * The highest API level this client provides. Compared against
	 * {@link PluginDescriptor#apiLevel()} before a plugin is constructed.
	 */
	public static final int LEVEL = 8;

	private PluginApi() {
	}

	/** Whether a plugin asking for this level can run here. Level 0 - "does not say" - always can. */
	public static boolean supports(int apiLevel) {
		return apiLevel <= LEVEL;
	}
}
