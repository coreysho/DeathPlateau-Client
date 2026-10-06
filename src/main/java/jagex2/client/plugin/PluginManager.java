package jagex2.client.plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

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

		Entry(String key, String name, String description, String source, Plugin plugin, PluginConfig config) {
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

	public PluginManager(Client client, PixFont small, PixFont normal, PixFont bold) {
		this.client = client;
		this.ctx = new PluginContext(client);
		this.graphics = new OverlayGraphics(small, normal, bold);
		this.pluginDirectory = new File(sign.signlink.findcachedir() + PLUGIN_FOLDER);
		this.store = new PluginStore(new File(sign.signlink.findcachedir() + STORE_FILE));
		this.bus = new EventBus(new EventBus.ErrorListener() {

			public void onSubscriberFailed(Object subscriber, Throwable error) {
				PluginManager.this.disableFaulty(subscriber, "its event handler kept throwing");
			}
		});
	}

	/** Where jars are read from. Shown in the panel so a player can find the folder. */
	public File getPluginDirectory() {
		return this.pluginDirectory;
	}

	public List<Entry> getPlugins() {
		return this.entries;
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
			if (this.store.getBoolean(entry.key + ".enabled", false)) {
				this.start(entry);
			}
		}
		DevLog.log("PLUGIN", this.entries.size() + " found, " + this.running + " running");
	}

	private void discover() {
		List<PluginLoader.Found> found = PluginLoader.scan(this.pluginDirectory);
		for (int i = 0; i < found.size(); i++) {
			this.instantiate(found.get(i));
		}
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
			this.entries.add(new Entry(key, name, description, found.source, plugin, config));
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
	 * own panels, so a plugin can never draw over a modal panel.
	 */
	public void renderOverlays() {
		if (this.idle()) {
			return;
		}
		for (int i = 0; i < this.overlays.size(); i++) {
			Overlay overlay = this.overlays.get(i);
			try {
				this.graphics.reset();
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
