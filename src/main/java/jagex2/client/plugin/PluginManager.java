package jagex2.client.plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import jagex2.client.Client;
import jagex2.client.DevLog;
import jagex2.client.plugin.event.ChatMessage;
import jagex2.client.plugin.event.ClientTick;
import jagex2.client.plugin.event.GameStateChanged;
import jagex2.client.plugin.event.GameTick;
import jagex2.client.plugin.event.KeyPressed;
import jagex2.client.plugin.event.MenuBuilt;
import jagex2.client.plugin.event.MenuOptionClicked;
import jagex2.client.plugin.event.StatChanged;
import jagex2.graphics.PixFont;

/**
 * Finds plugins, turns them on and off, and is the only thing Client.java talks to.
 *
 * WHY THIS EXISTS. Every QoL feature in this client so far lives in Client.java, switched on by a
 * line in QolSettings - which that class says plainly is not a plugin API. This is the other half:
 * a feature can now live in its own class, or its own jar, added without touching the client. The
 * shape is RuneLite's, deliberately, so that what a plugin author knows there carries over here -
 * a Plugin with startUp/shutDown, @Subscribe handlers, overlays, @ConfigItem settings.
 *
 * WHERE PLUGINS COME FROM. Jars in the "plugins" folder of the client's cache directory. Each is
 * loaded in its own class loader whose parent is the client's, so a plugin sees the client's
 * classes and the client never sees a plugin's. A jar may name its plugins in a "Plugin-Class"
 * manifest attribute; failing that, every class in it is checked.
 *
 * A NEW PLUGIN STARTS OFF. Dropping a jar in a folder is not consent to run its code on every
 * launch: the panel lists what was found, the player ticks what they want, and that choice is
 * remembered. A plugin has the client's own permissions once it runs - it is ordinary Java in the
 * client's process, with no sandbox - so this is the one place a deliberate act is worth asking
 * for. Only run plugins you trust, from somewhere you trust.
 *
 * THE CLIENT MUST SURVIVE ANY PLUGIN. Discovery, construction, lifecycle calls, event dispatch and
 * rendering all run inside a catch of Throwable. A plugin that throws repeatedly is turned off and
 * said so in the log; it never takes the game with it.
 */
public final class PluginManager {

	/**
	 * Plugins compiled into the client, which were QoL features in Client.java before they were
	 * plugins. They load with no jar and no folder, and honour enabledByDefault, because they are
	 * code the player already has and already trusted by running the client.
	 *
	 * Named rather than scanned: a classpath scan would be slower, would need a library, and
	 * would turn "is this class a plugin" into something a stray class could answer by accident.
	 */
	private static final String[] BUILT_IN = {
		"jagex2.client.plugin.builtin.EscapeClosesPlugin",
		"jagex2.client.plugin.builtin.HideRoofsPlugin",
		"jagex2.client.plugin.builtin.XpDropsPlugin"
	};

	/** What the panel shows as the source of a plugin that came with the client. */
	public static final String BUILT_IN_SOURCE = "built in";

	/** Folder under the cache directory that jars are read from. */
	public static final String PLUGIN_FOLDER = "plugins";

	private static final String STORE_FILE = "plugins.dat";

	/** Renders a plugin gets before it is turned off for throwing. */
	private static final int MAX_RENDER_ERRORS = 5;

	/** A plugin the client knows about, running or not. */
	public static final class Entry {

		public final String key;
		public final String name;
		public final String description;
		public final String source;

		final Plugin plugin;
		final PluginConfig config;

		boolean enabled;
		int renderErrors;

		/** What this plugin is when nothing has been saved about it yet. */
		boolean defaultEnabled;

		/** The jar this came out of, kept so its file can be released when the plugin is dropped. */
		final PluginLoader.Found found;

		Entry(String key, String name, String description, String source, Plugin plugin, PluginConfig config,
			PluginLoader.Found found) {
			this.found = found;
			this.key = key;
			this.name = name;
			this.description = description;
			this.source = source;
			this.plugin = plugin;
			this.config = config;
		}

		public boolean isEnabled() {
			return this.enabled;
		}

		public PluginConfig getConfig() {
			return this.config;
		}
	}

	/** One line of the plugin panel. The panel draws these; it knows nothing about plugins. */
	public static final class PanelRow {

		public static final int KIND_PLUGIN = 0;
		public static final int KIND_CONFIG = 1;
		public static final int KIND_ACTION = 2;
		public static final int KIND_TEXT = 3;

		public final int kind;
		public final String label;
		public final String hint;
		public final boolean checked;
		public final boolean showCheckbox;

		final Entry entry;
		final PluginConfig.Item item;

		PanelRow(int kind, String label, String hint, boolean checked, boolean showCheckbox, Entry entry, PluginConfig.Item item) {
			this.kind = kind;
			this.label = label;
			this.hint = hint;
			this.checked = checked;
			this.showCheckbox = showCheckbox;
			this.entry = entry;
			this.item = item;
		}

		public boolean isClickable() {
			return this.kind != KIND_TEXT;
		}
	}

	private final Client client;
	private final PluginContext ctx;
	private final EventBus bus;
	private final PluginStore store;
	private final OverlayGraphics graphics;
	private final File pluginDirectory;

	private final List<Entry> entries = new ArrayList<Entry>();

	/** Overlays of every running plugin, in render order. Rebuilt whenever one is toggled. */
	private final List<Overlay> overlays = new ArrayList<Overlay>();

	/** How many plugins are running, so a client with none pays nothing for having the system. */
	private int running;

	private int gameTicks;

	/** Last state posted, so GameStateChanged fires on the change rather than every frame. */
	private boolean wasLoggedIn;

	/** Guards against a plugin's reply to a chat message posting another chat message, forever. */
	private boolean postingChat;

	// WHY THERE IS A QUEUE HERE. The sidebar is Swing and runs on the event dispatch thread; the
	// plugins, the event bus and the overlay list belong to the game thread. A click on a toggle
	// that called startUp() directly would be constructing overlays on one thread while the game
	// loop walked the overlay list on another. Everything the UI wants done is queued instead and
	// run at the top of the next frame, which is the same bargain RuneLite's clientThread makes.
	private final ConcurrentLinkedQueue<Runnable> clientThreadTasks = new ConcurrentLinkedQueue<Runnable>();

	/** Told after anything changes, so the sidebar can redraw itself. Set by the UI, may be null. */
	private volatile Runnable changeListener;

	public PluginManager(Client client, PixFont small, PixFont normal, PixFont bold) {
		this.client = client;
		this.ctx = new PluginContext(client);
		this.graphics = new OverlayGraphics(small, normal, bold);
		this.pluginDirectory = findPluginDirectory();
		this.store = new PluginStore(new File(sign.signlink.findcachedir() + STORE_FILE));
		this.bus = new EventBus(new EventBus.ErrorListener() {

			public void onSubscriberFailed(Object subscriber, Throwable error) {
				PluginManager.this.disableFaulty(subscriber, "its event handler kept throwing");
			}
		});
	}

	/**
	 * Where jars are read from: ~/.deathplateau/plugins, beside the client.jar the launcher keeps
	 * there.
	 *
	 * NOT the client's cache directory, which is where every other preference lives. A player has
	 * to put plugin jars in this folder by hand, so it has to be somewhere they can find: the
	 * cache directory is signlink.findcachedir(), which is a 2004 search through c:/windows,
	 * c:/winnt, d:/windows and eventually /tmp for a folder called .file_store_32. Settings stay
	 * there with the rest; jars go where the player already keeps the client.
	 *
	 * Falls back to the cache directory if there is no usable home directory, which is better
	 * than no plugins at all. Either way the panel shows the path it settled on.
	 */
	private static File findPluginDirectory() {
		try {
			String home = System.getProperty("user.home");
			if (home != null && home.length() > 0) {
				File dir = new File(home, ".deathplateau");
				if (dir.isDirectory() || dir.mkdirs()) {
					return new File(dir, PLUGIN_FOLDER);
				}
			}
		} catch (Throwable ignored) {
			// A security manager, a read-only home. The fallback below still gives us somewhere.
		}
		return new File(sign.signlink.findcachedir() + PLUGIN_FOLDER);
	}

	/** Where jars are read from. Shown in the panel so a player can find the folder. */
	public File getPluginDirectory() {
		return this.pluginDirectory;
	}

	/**
	 * The plugins, running or not. Read from the game thread, or from the UI thread for display
	 * only - anything that CHANGES one goes through {@link #invokeOnClientThread(Runnable)}.
	 */
	public List<Entry> getPlugins() {
		return this.entries;
	}

	/** Runs the task at the top of the next frame. Safe to call from any thread. */
	public void invokeOnClientThread(Runnable task) {
		if (task != null) {
			this.clientThreadTasks.add(task);
		}
	}

	/** Set by the sidebar so it can refresh when a plugin starts, stops, or is reloaded. */
	public void setChangeListener(Runnable listener) {
		this.changeListener = listener;
	}

	/** Called after anything the UI shows has changed. Never throws into the game loop. */
	void fireChanged() {
		Runnable listener = this.changeListener;
		if (listener == null) {
			return;
		}
		try {
			listener.run();
		} catch (Throwable error) {
			DevLog.log("PLUGIN", "the plugin sidebar threw while refreshing: " + error);
		}
	}

	// ------------------------------------------------------------------ discovery and lifecycle

	/**
	 * Scans the plugin folder and starts whatever the player had on. Safe to call again: everything
	 * running is shut down first, so this doubles as the panel's Reload - a plugin author can
	 * rebuild a jar and pick it up without restarting the client.
	 */
	public void reload() {
		for (int i = 0; i < this.entries.size(); i++) {
			Entry entry = this.entries.get(i);
			if (entry.enabled) {
				this.stop(entry);
			}
		}
		// Closed before the list is dropped: a jar stays open as long as its loader lives, and on
		// Windows an open jar cannot be replaced or deleted - which is how a hub install over a
		// running plugin, or a plain Reload after rebuilding one, fails without saying anything.
		for (int i = 0; i < this.entries.size(); i++) {
			PluginLoader.close(this.entries.get(i).found);
		}
		this.entries.clear();
		this.overlays.clear();
		this.running = 0;

		// Created if it is not there, so a player told "put jars in the plugins folder" finds a
		// folder waiting rather than having to guess where to make one. Failure is ignored: a
		// read-only cache directory means no plugins, not a broken client.
		try {
			if (!this.pluginDirectory.exists()) {
				this.pluginDirectory.mkdirs();
			}
		} catch (Throwable ignored) {
		}

		this.discover();
		Collections.sort(this.entries, new Comparator<Entry>() {

			public int compare(Entry a, Entry b) {
				return a.name.compareToIgnoreCase(b.name);
			}
		});

		for (int i = 0; i < this.entries.size(); i++) {
			Entry entry = this.entries.get(i);
			if (this.store.getBoolean(entry.key + ".enabled", entry.defaultEnabled)) {
				this.start(entry);
			}
		}
		DevLog.log("PLUGIN", this.entries.size() + " found, " + this.running + " running");
		this.fireChanged();
	}

	private void discover() {
		// Built in first, so a jar cannot take a built-in plugin's key and displace it.
		for (int i = 0; i < BUILT_IN.length; i++) {
			try {
				Class<?> type = Class.forName(BUILT_IN[i]);
				this.instantiate(new PluginLoader.Found(type, BUILT_IN_SOURCE, null));
			} catch (Throwable error) {
				DevLog.log("PLUGIN", "built-in " + BUILT_IN[i] + " did not load: " + error);
			}
		}
		List<PluginLoader.Found> found = PluginLoader.scan(this.pluginDirectory);
		for (int i = 0; i < found.size(); i++) {
			this.instantiate(found.get(i));
		}
	}

	/**
	 * Whether a plugin should be on, the first time it is ever seen.
	 *
	 * A plugin from a jar starts off, always: dropping a file in a folder is not consent to run
	 * it. A built-in one may ask to be on, and if it used to be a QolSettings switch, a choice
	 * the player made back then wins over what it asks for - that is the only thing legacySetting
	 * is for, and it is read once, before this plugin has any state of its own.
	 */
	private boolean defaultEnabled(PluginDescriptor descriptor, boolean builtIn) {
		if (descriptor == null || !builtIn) {
			return false;
		}
		String legacy = descriptor.legacySetting();
		if (legacy.length() > 0) {
			Boolean chosen = jagex2.client.QolSettings.saved(legacy);
			if (chosen != null) {
				return chosen.booleanValue();
			}
		}
		return descriptor.enabledByDefault();
	}

	/**
	 * Builds a plugin from a class the loader found, and registers it. A plugin that cannot be
	 * constructed is logged and skipped: one bad plugin, not a broken client.
	 */
	private void instantiate(PluginLoader.Found found) {
		try {
			Class<?> type = found.type;
			Plugin plugin = (Plugin) type.newInstance();
			PluginDescriptor descriptor = type.getAnnotation(PluginDescriptor.class);
			String key = descriptor == null || descriptor.key().length() == 0
				? type.getName()
				: descriptor.key();
			String name = descriptor == null ? type.getSimpleName() : descriptor.name();
			String description = descriptor == null ? "" : descriptor.description();

			for (int i = 0; i < this.entries.size(); i++) {
				if (this.entries.get(i).key.equals(key)) {
					DevLog.log("PLUGIN", "ignoring a second plugin with key " + key + " from " + found.source);
					return;
				}
			}

			PluginConfig config = new PluginConfig(key, this.store);
			plugin.attach(this.ctx, config);
			config.bind(plugin);
			Entry entry = new Entry(key, name, description, found.source, plugin, config, found);
			entry.defaultEnabled = this.defaultEnabled(descriptor, BUILT_IN_SOURCE.equals(found.source));
			this.entries.add(entry);
		} catch (Throwable error) {
			// A missing no-argument constructor, a constructor that threw, a class compiled
			// against a client that has since changed.
			DevLog.log("PLUGIN", "could not create " + found.type.getName() + " from " + found.source + ": " + error);
		}
	}

	/** Turns a plugin on or off and remembers the choice. */
	public void setEnabled(Entry entry, boolean enabled) {
		if (entry == null || entry.enabled == enabled) {
			return;
		}
		if (enabled) {
			this.start(entry);
		} else {
			this.stop(entry);
		}
		this.store.put(entry.key + ".enabled", entry.enabled ? "1" : "0");
		this.store.save();
		this.fireChanged();
	}

	public void toggle(Entry entry) {
		if (entry != null) {
			this.setEnabled(entry, !entry.enabled);
		}
	}

	private void start(Entry entry) {
		try {
			entry.renderErrors = 0;
			entry.plugin.startUp();
			entry.plugin.setRunning(true);
			entry.enabled = true;
			this.bus.register(entry.plugin);
			this.running++;
			this.rebuildOverlays();
			DevLog.log("PLUGIN", entry.name + " started");
		} catch (Throwable error) {
			// Half-started is worse than off: unwind whatever it managed before it threw.
			DevLog.log("PLUGIN", entry.name + " failed to start: " + error);
			entry.enabled = false;
			this.stopQuietly(entry);
		}
	}

	private void stop(Entry entry) {
		this.stopQuietly(entry);
		DevLog.log("PLUGIN", entry.name + " stopped");
	}

	private void stopQuietly(Entry entry) {
		if (entry.enabled) {
			this.running--;
		}
		entry.enabled = false;
		this.bus.unregister(entry.plugin);
		try {
			entry.plugin.setRunning(false);
			entry.plugin.shutDown();
		} catch (Throwable error) {
			DevLog.log("PLUGIN", entry.name + " threw on shutdown: " + error);
		}
		entry.plugin.clearOverlays();
		this.rebuildOverlays();
	}

	/**
	 * Stops every plugin that came out of one jar and closes it, so the file can be replaced or
	 * deleted. Used by the hub before it installs over a plugin or removes one.
	 *
	 * The plugins are forgotten, not disabled: their saved enabled state is left alone, so a
	 * reload after an update brings back the ones that were on.
	 */
	public void releaseJar(String fileName) {
		if (fileName == null) {
			return;
		}
		boolean released = false;
		for (int i = this.entries.size() - 1; i >= 0; i--) {
			Entry entry = this.entries.get(i);
			if (!fileName.equals(entry.source)) {
				continue;
			}
			if (entry.enabled) {
				this.stopQuietly(entry);
			}
			PluginLoader.close(entry.found);
			this.entries.remove(i);
			released = true;
		}
		if (released) {
			DevLog.log("PLUGIN", "released " + fileName);
			this.fireChanged();
		}
	}

	/** Turns everything off, for client shutdown. */
	public void shutdown() {
		for (int i = 0; i < this.entries.size(); i++) {
			Entry entry = this.entries.get(i);
			if (entry.enabled) {
				this.stopQuietly(entry);
			}
		}
	}

	/** A plugin misbehaved badly enough to be turned off. The player is told; this is not silent. */
	private void disableFaulty(Object subscriberOrPlugin, String why) {
		for (int i = 0; i < this.entries.size(); i++) {
			Entry entry = this.entries.get(i);
			if (entry.plugin == subscriberOrPlugin) {
				this.setEnabled(entry, false);
				this.ctx.addChatMessage("Plugin '" + entry.name + "' was turned off because " + why + ".");
				return;
			}
		}
	}

	private void rebuildOverlays() {
		this.overlays.clear();
		for (int i = 0; i < this.entries.size(); i++) {
			Entry entry = this.entries.get(i);
			if (entry.enabled) {
				this.overlays.addAll(entry.plugin.getOverlays());
			}
		}
		Collections.sort(this.overlays, new Comparator<Overlay>() {

			public int compare(Overlay a, Overlay b) {
				return a.priority() - b.priority();
			}
		});
	}

	// ------------------------------------------------------------------ hooks Client.java calls

	/** True when nothing is running, which lets every hook below cost one field read. */
	private boolean idle() {
		return this.running == 0;
	}

	public void onClientTick(int cycle) {
		// Drained before the idle check: the queue is how the sidebar turns the FIRST plugin on,
		// and at that point nothing is running yet.
		this.drainTasks();
		if (this.idle()) {
			return;
		}
		// Login and logout are noticed here rather than hooked separately: the client has half a
		// dozen ways in and out of the game (logout, dropped connection, a reconnect, the update
		// relaunch) and one check a frame catches all of them.
		boolean loggedIn = this.client.ingame && Client.localPlayer != null;
		if (loggedIn != this.wasLoggedIn) {
			this.wasLoggedIn = loggedIn;
			this.bus.post(new GameStateChanged(loggedIn ? GameStateChanged.LOGGED_IN : GameStateChanged.LOGIN_SCREEN));
		}
		this.bus.post(new ClientTick(cycle));
	}

	/** Runs whatever the UI asked for, on the game thread, one frame's worth at a time. */
	private void drainTasks() {
		Runnable task;
		while ((task = this.clientThreadTasks.poll()) != null) {
			try {
				task.run();
			} catch (Throwable error) {
				DevLog.log("PLUGIN", "a queued plugin task threw: " + error);
			}
		}
	}

	public void onGameTick() {
		if (this.idle()) {
			return;
		}
		this.bus.post(new GameTick(++this.gameTicks));
	}

	public void onChatMessage(String sender, String message, int type) {
		if (this.idle() || this.postingChat) {
			// A plugin answering a message with a message of its own re-enters addMessage, which
			// posts again. Without this guard a handler that replies to everything - including its
			// own reply - recurses until the stack gives out. The reply still reaches the chatbox;
			// it is only the event for it that is skipped, which is the one nobody wants.
			return;
		}
		this.postingChat = true;
		try {
			this.bus.post(new ChatMessage(sender == null ? "" : sender, message == null ? "" : message, type));
		} finally {
			this.postingChat = false;
		}
	}

	public void onStatChanged(int skill, int level, int experience, int gained) {
		if (this.idle()) {
			return;
		}
		this.bus.post(new StatChanged(skill, level, experience, gained));
	}

	public void onMenuBuilt(int size) {
		if (this.idle()) {
			return;
		}
		this.bus.post(new MenuBuilt(size));
	}

	/** Returns true when a plugin consumed the click and the client should not act on it. */
	public boolean onMenuOptionClicked(String option, int action, int paramA, int paramB, int paramC) {
		if (this.idle()) {
			return false;
		}
		MenuOptionClicked event = new MenuOptionClicked(option == null ? "" : option, action, paramA, paramB, paramC);
		this.bus.post(event);
		return event.isConsumed();
	}

	/** Returns true when a plugin consumed the key and the game should not see it. */
	public boolean onKeyPressed(int key) {
		if (this.idle()) {
			return false;
		}
		KeyPressed event = new KeyPressed(key);
		this.bus.post(event);
		return event.isConsumed();
	}

	/**
	 * Draws every running plugin's overlays. Called with the viewport bound, before the client's
	 * own panels, so a plugin can never draw over a modal panel. The width and height are the
	 * drawable area for this frame, which the display mode can change.
	 */
	public void renderOverlays(int width, int height) {
		if (this.idle()) {
			return;
		}
		for (int i = 0; i < this.overlays.size(); i++) {
			Overlay overlay = this.overlays.get(i);
			try {
				this.graphics.reset(width, height);
				overlay.render(this.graphics);
			} catch (Throwable error) {
				Entry entry = this.entryOf(overlay.owner);
				DevLog.log("PLUGIN", (entry == null ? "an overlay" : entry.name) + " threw while drawing: " + error);
				if (entry != null && ++entry.renderErrors >= MAX_RENDER_ERRORS) {
					this.disableFaulty(entry.plugin, "it kept throwing while drawing");
					return;                            // disabling edits the list being walked
				}
			}
		}
	}

	private Entry entryOf(Plugin plugin) {
		for (int i = 0; i < this.entries.size(); i++) {
			if (this.entries.get(i).plugin == plugin) {
				return this.entries.get(i);
			}
		}
		return null;
	}

	// ------------------------------------------------------------------ the panel's model

	/**
	 * The panel as a flat list of rows: every plugin, each running plugin's settings under it, and
	 * a reload row at the end. Built fresh each frame, which costs nothing at this size and means
	 * the panel can never show a stale list after a reload.
	 */
	public List<PanelRow> buildPanelRows() {
		List<PanelRow> rows = new ArrayList<PanelRow>();
		if (this.entries.isEmpty()) {
			rows.add(new PanelRow(PanelRow.KIND_TEXT, "No plugins found.", null, false, false, null, null));
			rows.add(new PanelRow(PanelRow.KIND_TEXT, "Put plugin jars in " + this.pluginDirectory.getPath(),
				null, false, false, null, null));
		}
		for (int i = 0; i < this.entries.size(); i++) {
			Entry entry = this.entries.get(i);
			rows.add(new PanelRow(PanelRow.KIND_PLUGIN, entry.name, entry.description, entry.enabled, true, entry, null));
			if (!entry.enabled) {
				continue;
			}
			List<PluginConfig.Item> items = entry.config.getItems();
			for (int j = 0; j < items.size(); j++) {
				PluginConfig.Item item = items.get(j);
				String label = "    " + item.name + (item.isBoolean() ? "" : ": " + item.displayValue());
				rows.add(new PanelRow(PanelRow.KIND_CONFIG, label, item.description, item.booleanValue(),
					item.isBoolean(), entry, item));
			}
		}
		rows.add(new PanelRow(PanelRow.KIND_ACTION, "Reload plugins", "Re-reads the plugins folder", false, false, null, null));
		return rows;
	}

	/** Acts on a clicked row. Anything not clickable is ignored. */
	public void clickRow(PanelRow row) {
		if (row == null) {
			return;
		}
		if (row.kind == PanelRow.KIND_PLUGIN) {
			this.toggle(row.entry);
		} else if (row.kind == PanelRow.KIND_CONFIG && row.item != null && row.item.isBoolean()) {
			row.entry.config.toggle(row.item);
		} else if (row.kind == PanelRow.KIND_ACTION) {
			this.reload();
		}
	}
}
