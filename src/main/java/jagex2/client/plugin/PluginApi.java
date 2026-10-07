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
 *     panels ({@code addPanel}), and the context as of this release: skills, position, the menu
 *     with swapping and left-click, chat messages out, ground item piles, modifier keys, drag
 *     delay, tile and world projection.</dd>
 * </dl>
 *
 * When the API next grows - notifications and sound are the first candidates - this becomes 2,
 * the new methods are listed under it, and a plugin that needs them declares
 * {@code apiLevel = 2}. A client at level 1 then refuses it by name instead of breaking.
 */
public final class PluginApi {

	/**
	 * The highest API level this client provides. Compared against
	 * {@link PluginDescriptor#apiLevel()} before a plugin is constructed.
	 */
	public static final int LEVEL = 1;

	private PluginApi() {
	}

	/** Whether a plugin asking for this level can run here. Level 0 - "does not say" - always can. */
	public static boolean supports(int apiLevel) {
		return apiLevel <= LEVEL;
	}
}
