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
import jagex2.client.plugin.event.SettingsMenuOpening;
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
		"jagex2.client.plugin.builtin.AntiDragPlugin",
		"jagex2.client.plugin.builtin.XpDropsPlugin",
		"jagex2.client.plugin.builtin.BarrowsDoorsPlugin",
		"jagex2.client.plugin.builtin.MenuSwapperPlugin",
		"jagex2.client.plugin.builtin.GroundItemsPlugin",
		"jagex2.client.plugin.builtin.BoostsPlugin",
		"jagex2.client.plugin.builtin.SkillsPlugin",
		"jagex2.client.plugin.builtin.IdleNotifierPlugin",
		"jagex2.client.plugin.builtin.MouseHighlightPlugin",
		"jagex2.client.plugin.builtin.TileIndicatorsPlugin"
	};

	/** The panel's action rows, matched by label when one is clicked. */
	public static final String ACTION_RELOAD = "Reload plugins";
	public static final String ACTION_RESET_OVERLAYS = "Reset overlay positions";

	/** What the panel shows as the source of a plugin that came with the client. */
	public static final String BUILT_IN_SOURCE = "built in";

	/** Folder under the cache directory that jars are read from. */
	public static final String PLUGIN_FOLDER = "plugins";

	private static final String STORE_FILE = "plugins.dat";

	/** Renders a plugin gets before it is turned off for throwing. */
	private static final int MAX_RENDER_ERRORS = 5;

	/** A plugin the client knows about, running or not. */
	/**
	 * A plugin that was found but not loaded, and the one sentence saying why.
	 *
	 * Kept so the plugin panel can show a dim row instead of the plugin simply not being there.
	 * A jar that vanishes without a word is the thing this whole mechanism exists to stop: the
	 * player installed something, and "it is not in the list" is not an answer.
	 */
	public static final class Refused {

		public final String name;
		public final String source;
		public final String reason;

		Refused(String name, String source, String reason) {
			this.name = name;
			this.source = source;
			this.reason = reason;
		}
	}

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

		/**
		 * Whether this plugin has a config page worth opening: settings, a list, or both.
		 *
		 * Both halves matter. A plugin can have settings and no list (XP drops), a list and no
		 * settings (Left-click swaps), or both - and the second of those is the one that was
		 * missed: the cog tested for settings alone, so the swaps page could not be reached at
		 * all. Lists are registered in startUp(), so a stopped list-only plugin has nothing to
		 * show, which is true rather than awkward: its list is empty until it runs.
		 */
		public boolean hasSettings() {
			return !this.config.getItems().isEmpty() || !this.plugin.getConfigLists().isEmpty();
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

	/** Plugins found and turned away, for the panel to show. Rebuilt by every {@link #reload()}. */
	private final List<Refused> refused = new ArrayList<Refused>();

	/** Where the player has dragged each overlay to. */
	private OverlayPositions positions;

	/** The overlay being dragged right now, or null. */
	private Overlay dragging;

	/** Cursor position within the dragged overlay's offset, so it does not jump to the cursor. */
	private int dragGrabX;
	private int dragGrabY;

	/** What Alt is hovering over, outlined so the player can see what they are about to grab. */
	private Overlay hovered;

	/** Whether Alt was down last frame, which is what turns dragging on at all. */
	private boolean dragMode;

	/**
	 * The frame a plugin last asked what tile the cursor is over, or -1 for never.
	 *
	 * A LAZY SUBSCRIPTION, and the reason there is no subscribe() to call. Answering "what is
	 * under the cursor" costs the renderer a hit test per tile, which the client was only ever
	 * paying on a click. Asking every frame on the off chance a plugin cares would make every
	 * player pay for a feature almost none of them are running.
	 *
	 * So reading the answer is what asks for the next one. A plugin that reads it each frame
	 * keeps it coming; one that stops reading stops the cost within {@link #HOVER_KEEPALIVE}
	 * frames. Nothing to turn on, nothing to leak.
	 */
	private int hoverWantedAt = -1;

	/** How many frames a read keeps the hover pick alive for. About a fifth of a second. */
	private static final int HOVER_KEEPALIVE = 10;

	/** The frame counter the keepalive is measured against. */
	private int frame;

	/** Overlays of every running plugin, in render order. Rebuilt whenever one is toggled. */
	private final List<Overlay> overlays = new ArrayList<Overlay>();

	/** What the overlays claimed as clickable this frame. Cleared at the start of every one. */
	private final InteractiveRegions regions = new InteractiveRegions();

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
		this.ctx.attachManager(this);
		this.graphics = new OverlayGraphics(small, normal, bold);
		this.pluginDirectory = findPluginDirectory();
		this.store = new PluginStore(new File(sign.signlink.findcachedir() + STORE_FILE));
		this.positions = new OverlayPositions(this.store);
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

	/**
	 * The plugins that were found but refused, with a reason each. Display only, same threading
	 * rules as {@link #getPlugins()}.
	 */
	public List<Refused> getRefused() {
		return this.refused;
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
		this.refused.clear();
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
		DevLog.log("PLUGIN", this.entries.size() + " found, " + this.running + " running"
			+ (this.refused.isEmpty() ? "" : ", " + this.refused.size() + " refused"));
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
		Class<?> type = found.type;
		PluginDescriptor descriptor = null;
		try {
			descriptor = type.getAnnotation(PluginDescriptor.class);
		} catch (Throwable ignored) {
			// An annotation that will not resolve is not worth a refusal of its own: fall through
			// with none, and the plugin is judged on whether it constructs.
		}
		String key = descriptor == null || descriptor.key().length() == 0
			? type.getName()
			: descriptor.key();
		String name = descriptor == null || descriptor.name().length() == 0
			? type.getSimpleName()
			: descriptor.name();
		String description = descriptor == null ? "" : descriptor.description();

		// Checked BEFORE the plugin is constructed. A constructor is already plugin code running,
		// and a jar built against a newer client can throw out of it - at which point the reason
		// is a LinkageError with no plugin name attached, rather than this sentence.
		int needs = descriptor == null ? 0 : descriptor.apiLevel();
		if (!PluginApi.supports(needs)) {
			this.refuse(name, found, "built for a newer client - it needs plugin API "
				+ needs + " and this client has " + PluginApi.LEVEL);
			return;
		}

		try {
			Plugin plugin = (Plugin) type.newInstance();

			for (int i = 0; i < this.entries.size(); i++) {
				if (this.entries.get(i).key.equals(key)) {
					this.refuse(name, found, "another plugin already uses the key " + key);
					return;
				}
			}

			PluginConfig config = new PluginConfig(key, this.store);
			plugin.attach(this.ctx, config);
			config.bind(plugin);
			Entry entry = new Entry(key, name, description, found.source, plugin, config, found);
			entry.defaultEnabled = this.defaultEnabled(descriptor, BUILT_IN_SOURCE.equals(found.source));
			this.entries.add(entry);
		} catch (LinkageError error) {
			// The jar wants something this client does not have, and did not declare a level that
			// would have caught it earlier - every plugin built before apiLevel existed. Said in
			// the same words as a declared mismatch, because to the player it is the same thing.
			this.refuse(name, found, "built for a different client - it needs " + missing(error));
		} catch (Throwable error) {
			// A missing no-argument constructor, or a constructor that threw.
			this.refuse(name, found, "it could not be created: " + error);
		}
	}

	/**
	 * Records a plugin that was found and not loaded, and lets go of its jar.
	 *
	 * The file matters: a loader left open holds the jar open, and on Windows an open jar cannot
	 * be replaced or deleted - so a refused plugin that kept its loader would also be one the
	 * player could not uninstall or overwrite with a working build.
	 */
	private void refuse(String name, PluginLoader.Found found, String reason) {
		DevLog.log("PLUGIN", name + " from " + found.source + " refused: " + reason);
		this.refused.add(new Refused(name, found.source, reason));
		PluginLoader.close(found);
	}

	/**
	 * What a LinkageError was about, in as few words as the error will give up.
	 *
	 * The JVM writes these for a compiler, not for a sidebar row, and it writes them in two
	 * shapes. A missing member comes QUOTED, as a signature: {@code 'void api.Helper.v2()'}. A
	 * missing or unusable class comes unquoted, as a sentence that starts with the class:
	 * {@code api/Helper} from NoClassDefFoundError, or {@code api/Helper has been compiled by a
	 * more recent version...} from UnsupportedClassVersionError.
	 *
	 * The quoting is what tells them apart, so it decides which token to keep: the last one that
	 * looks like a name in a signature, the first one in a sentence. Then slashes become dots and
	 * the owner plus the member is what is left - "Helper.v2()", "api.Helper".
	 *
	 * Anything that fits neither shape is returned as the JVM wrote it. A wrong guess here would
	 * be worse than the raw text, which is at least true.
	 */
	static String missing(LinkageError error) {
		String message = error.getMessage();
		if (message == null || message.trim().length() == 0) {
			return error.getClass().getSimpleName();
		}
		String text = message.trim();
		boolean quoted = text.length() > 1 && text.charAt(0) == '\''
			&& text.charAt(text.length() - 1) == '\'';
		if (quoted) {
			text = text.substring(1, text.length() - 1).trim();
		}
		// Everything from the first bracket goes, because it is full of dots and spaces that
		// every step below would otherwise mistake for the name - an argument list like
		// "v2(java.lang.String, int)" ends in a dotted segment of its own, and so does the
		// "(class file version 65.0)" an UnsupportedClassVersionError ends with.
		//
		// A bracket only MEANS a method in the quoted shape, where it is a signature. In the
		// sentence shape it is prose, and reading it as a method is how this once answered
		// "api.Helper()" for a class that was never a method.
		int open = text.indexOf('(');
		boolean method = quoted && open >= 0;
		if (open >= 0) {
			text = text.substring(0, open);
		}

		// One token out of what is left. In a signature the name is the last thing before the
		// arguments ("void api.Helper.v2"); in a sentence it is the first thing said
		// ("api/Helper has been compiled by..."). Taking the wrong end of a sentence is how this
		// used to answer "has been compiled by a more recent version of the Java Runtime".
		String name = null;
		String[] words = text.split("\\s+");
		for (int i = 0; i < words.length; i++) {
			String word = words[quoted ? words.length - 1 - i : i];
			if (word.indexOf('.') >= 0 || word.indexOf('/') >= 0) {
				name = word;
				break;
			}
		}
		if (name == null) {
			// No dots anywhere. One word is a bare field name, which is already the answer -
			// the cleaned one, so a message that arrived padded or quoted does not put that
			// padding in a sidebar row. More than one word is a sentence, and a sentence with
			// no name in it is better said in the JVM's own words than in a guess at them.
			return words.length == 1 && text.length() > 0 && text.length() <= 80
				? text : message;
		}

		name = name.replace('/', '.');
		// The owner and the member, which is what someone can act on. More is a package path.
		int last = name.lastIndexOf('.');
		if (last > 0) {
			int before = name.lastIndexOf('.', last - 1);
			if (before >= 0) {
				name = name.substring(before + 1);
			}
		}
		if (name.length() == 0 || name.length() > 80) {
			return message;
		}
		return method ? name + "()" : name;
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
		} catch (LinkageError error) {
			// Half-started is worse than off: unwind whatever it managed before it threw.
			DevLog.log("PLUGIN", entry.name + " failed to start: " + error);
			entry.enabled = false;
			this.stopQuietly(entry);
			// Said in chat, not just the log. This is the case that sent us looking for API
			// levels in the first place: a jar built against a newer client, installed cleanly,
			// switching itself off on the first launch with nothing anywhere explaining it.
			//
			// Queued rather than said here, because start() also runs during the first reload(),
			// which is before the chatbox has a font to measure the line with. The queue drains
			// at the top of a frame, by which point it does.
			final String line = "Plugin '" + entry.name + "' was built for a different client"
				+ " - it needs " + missing(error) + ".";
			this.invokeOnClientThread(new Runnable() {

				public void run() {
					PluginManager.this.ctx.addChatMessage(line);
				}
			});
		} catch (Throwable error) {
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
		this.dragging = null;
		this.hovered = null;
		for (int i = 0; i < this.entries.size(); i++) {
			Entry entry = this.entries.get(i);
			if (!entry.enabled) {
				continue;
			}
			// Keyed here, in the plugin's own order, BEFORE the flat list is sorted by priority.
			// Sorting mixes every plugin's overlays together, so an index taken after it would
			// change whenever another plugin was turned on.
			List<Overlay> own = entry.plugin.getOverlays();
			for (int j = 0; j < own.size(); j++) {
				own.get(j).positionKey = entry.key + "#" + j;
			}
			this.overlays.addAll(own);
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
		// Counted before the idle check, so the hover keepalive measures frames rather than
		// frames-in-which-a-plugin-was-running.
		this.frame++;
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

	/**
	 * Offers the settings menu to every plugin and returns the rows they added, in order.
	 * Returns an empty list when nothing is running, so the client's own rows are all there is.
	 */
	public java.util.List<SettingsMenuOpening.Row> onSettingsMenuOpening(
		java.util.List<SettingsMenuOpening.Target> targets, boolean worldMenu, int room) {
		if (this.idle()) {
			return new ArrayList<SettingsMenuOpening.Row>();
		}
		SettingsMenuOpening event = new SettingsMenuOpening(targets, worldMenu, room);
		this.bus.post(event);
		return event.getRows();
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

	/**
	 * A click in the viewport, in viewport-local coordinates. Returns true when an overlay had
	 * claimed that spot, in which case the click is spent and must not also walk the player.
	 */
	public boolean onViewportClick(int x, int y) {
		return !this.idle() && this.regions.click(x, y);
	}

	/** The same for the wheel. Returns true when an overlay took the turn. */
	public boolean onViewportScroll(int x, int y, int delta) {
		return !this.idle() && this.regions.scroll(x, y, delta);
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
	 * Draws the overlays on one layer. Called with the viewport bound, before the client's own
	 * panels, so a plugin can never draw over a modal panel. The width and height are the
	 * drawable area for this frame, which the display mode can change.
	 *
	 * Called twice a frame, scene layer first: see Overlay.LAYER_SCENE.
	 */
	public void renderOverlays(int width, int height, int layer) {
		if (this.idle()) {
			return;
		}
		// Last frame's regions go before this frame's are drawn, so nothing a plugin has stopped
		// drawing stays clickable. Done in the scene pass because that is the first of the two
		// the client makes each frame - clearing in both would throw away what the first drew.
		if (layer == Overlay.LAYER_SCENE) {
			this.regions.clear();
		}
		for (int i = 0; i < this.overlays.size(); i++) {
			Overlay overlay = this.overlays.get(i);
			if (overlay.layer() != layer) {
				continue;
			}
			try {
				boolean movable = overlay.layer() == Overlay.LAYER_SCREEN;
				this.graphics.reset(width, height, this.regions, overlay.owner,
					movable ? this.positions.x(overlay.positionKey) : 0,
					movable ? this.positions.y(overlay.positionKey) : 0);
				overlay.render(this.graphics);
				// What it drew, kept for the drag code to hit-test against next frame. Read from
				// the graphics rather than declared by the overlay: what it draws is what it
				// occupies, and that is also what a player would try to grab.
				overlay.lastBounds = movable && this.graphics.hasBounds()
					? new int[] { this.graphics.boundsLeft(), this.graphics.boundsTop(),
						this.graphics.boundsRight(), this.graphics.boundsBottom() }
					: null;
				if (movable && overlay == this.hovered && overlay.lastBounds != null) {
					this.outline(overlay.lastBounds);
				}
			} catch (Throwable error) {
				Entry entry = this.entryOf(overlay.owner);
				DevLog.log("PLUGIN", (entry == null ? "an overlay" : entry.name) + " threw while drawing: " + error);
				if (entry != null && ++entry.renderErrors >= MAX_RENDER_ERRORS) {
					this.disableFaulty(entry.plugin, "it kept throwing while drawing");
					return;                            // disabling edits the list being walked
				}
				// Half-drawn, so whatever it did claim this frame is not to be trusted.
				this.regions.forget(overlay.owner);
			}
		}
	}

	/**
	 * Draws a box round the overlay Alt is hovering, so a player can see what they will grab.
	 *
	 * Drawn through the same graphics the overlay just used, with its offset still applied - so
	 * the box is marked out in overlay coordinates and lands exactly over what was drawn, rather
	 * than being offset twice.
	 */
	private void outline(int[] bounds) {
		int x = bounds[0] - this.positions.x(this.hovered.positionKey);
		int y = bounds[1] - this.positions.y(this.hovered.positionKey);
		int wide = bounds[2] - bounds[0];
		int tall = bounds[3] - bounds[1];
		this.graphics.box(x - 1, y - 1, wide + 2, tall + 2,
			this.dragging == this.hovered ? DRAG_HELD : DRAG_HOVER);
	}

	// ------------------------------------------------------------------ dragging overlays

	/** The outline round an overlay Alt is over, and round the one being dragged. */
	private static final int DRAG_HOVER = 0xB83228;
	private static final int DRAG_HELD = 0xFFFFFF;

	/**
	 * Alt-drag: hold Alt and any overlay can be picked up and put anywhere.
	 *
	 * Called once a frame, before the overlays are drawn, with the cursor in viewport
	 * coordinates and the button that is held down. THE CLIENT OWNS THIS, not the plugins: a
	 * plugin never learns it has been moved, which is why the two already published are
	 * draggable without being rebuilt, and why a plugin cannot move itself somewhere a player
	 * did not put it.
	 *
	 * Only the screen layer moves. Scene-layer overlays are drawn over a tile in the world, and
	 * an offset on one of those would just be a label pointing at the wrong thing.
	 *
	 * Alt is also what Ground items reveals hidden piles with, and the two do not collide: that
	 * is a key held with no button, and its own controls are on the right button, while this
	 * needs a LEFT press on something a screen overlay drew.
	 */
	public void onOverlayDrag(int x, int y, int button, boolean alt, int width, int height) {
		if (this.idle()) {
			return;
		}
		if (!alt) {
			// Let go of whatever was held: releasing Alt mid-drag leaves it where it is rather
			// than snapping it back, which is what a player who changed their mind expects.
			if (this.dragging != null) {
				this.finishDrag(width, height);
			}
			this.dragMode = false;
			this.hovered = null;
			return;
		}
		this.dragMode = true;
		if (this.dragging == null) {
			this.hovered = this.overlayAt(x, y);
			if (button == 1 && this.hovered != null) {
				this.dragging = this.hovered;
				// The grab point, so the overlay does not jump its own top-left to the cursor.
				this.dragGrabX = x - this.positions.x(this.dragging.positionKey);
				this.dragGrabY = y - this.positions.y(this.dragging.positionKey);
			}
			return;
		}
		if (button != 1) {
			this.finishDrag(width, height);
			return;
		}
		this.moveTo(x - this.dragGrabX, y - this.dragGrabY, width, height);
	}

	/**
	 * Puts the dragged overlay at an offset, kept inside the viewport.
	 *
	 * Not saved here - this runs every frame of a drag, and writing plugins.dat sixty times a
	 * second to record a position the player has not settled on yet is a file write per frame
	 * for no reason.
	 */
	private void moveTo(int offsetX, int offsetY, int width, int height) {
		int[] bounds = this.dragging.lastBounds;
		if (bounds == null) {
			return;
		}
		int wasX = this.positions.x(this.dragging.positionKey);
		int wasY = this.positions.y(this.dragging.positionKey);
		// The box, expressed relative to the offset, so clamping can reason about where the
		// overlay would END UP rather than where it is.
		int clampedX = OverlayPositions.clamp(offsetX, bounds[0] - wasX, bounds[2] - wasX, width);
		int clampedY = OverlayPositions.clamp(offsetY, bounds[1] - wasY, bounds[3] - wasY, height);
		this.positions.move(this.dragging.positionKey, clampedX, clampedY);
	}

	/** Snaps the dropped overlay if it is nearly home, saves it, and lets go. */
	private void finishDrag(int width, int height) {
		Overlay dropped = this.dragging;
		this.dragging = null;
		if (dropped == null) {
			return;
		}
		int x = OverlayPositions.snap(this.positions.x(dropped.positionKey));
		int y = OverlayPositions.snap(this.positions.y(dropped.positionKey));
		this.positions.set(dropped.positionKey, x, y);
		DevLog.log("PLUGIN", "overlay " + dropped.positionKey + " moved to " + x + "," + y);
	}

	/**
	 * The overlay under the cursor, topmost first.
	 *
	 * Walked backwards, because the overlays are drawn in priority order and the last one drawn
	 * is the one on top - which is the one a player is pointing at when two overlap.
	 */
	private Overlay overlayAt(int x, int y) {
		for (int i = this.overlays.size() - 1; i >= 0; i--) {
			Overlay overlay = this.overlays.get(i);
			int[] bounds = overlay.lastBounds;
			if (bounds == null) {
				continue;
			}
			if (x >= bounds[0] && x < bounds[2] && y >= bounds[1] && y < bounds[3]) {
				return overlay;
			}
		}
		return null;
	}

	/**
	 * Whether the client should ask the scene what tile is under the cursor this frame.
	 *
	 * Called by Client once a frame. See {@link #hoverWantedAt} for why this is a question
	 * rather than a setting.
	 */
	public boolean wantsHoverTile() {
		return !this.idle() && this.hoverWantedAt >= 0
			&& this.frame - this.hoverWantedAt <= HOVER_KEEPALIVE;
	}

	/** Records that a plugin wants the tile under the cursor, and keeps it coming. */
	void hoverTileRead() {
		this.hoverWantedAt = this.frame;
	}

	/** True while Alt is held over something draggable, so the client can leave the click alone. */
	public boolean isDragging() {
		return this.dragging != null;
	}

	/**
	 * Whether a click at this point should be taken by the drag rather than by the overlay.
	 *
	 * An overlay with a button on it would otherwise have that button pressed by the same click
	 * that picks it up - and the click that drops it would press whatever is now underneath.
	 */
	public boolean dragWantsClick(int x, int y) {
		return !this.idle() && this.dragMode && this.overlayAt(x, y) != null;
	}

	/** Puts every overlay back where its plugin draws it. */
	public void resetOverlayPositions() {
		for (int i = 0; i < this.entries.size(); i++) {
			List<Overlay> own = this.entries.get(i).plugin.getOverlays();
			for (int j = 0; j < own.size(); j++) {
				this.positions.clear(this.entries.get(i).key + "#" + j);
				// Last frame's box went with the old position. Left behind, it is a grab area
				// where the overlay used to be - for the one frame before the next draw
				// replaces it, which is one frame of Alt grabbing thin air.
				own.get(j).lastBounds = null;
			}
		}
		this.dragging = null;
		this.hovered = null;
		DevLog.log("PLUGIN", "overlay positions reset");
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
		rows.add(new PanelRow(PanelRow.KIND_ACTION, ACTION_RELOAD, "Re-reads the plugins folder",
			false, false, null, null));
		rows.add(new PanelRow(PanelRow.KIND_ACTION, ACTION_RESET_OVERLAYS,
			"Puts every overlay back where its plugin draws it", false, false, null, null));
		return rows;
	}

	/** One of a plugin's config lists, read in full, ready for the panel to draw. */
	public static final class ListSnapshot {

		public final String title;
		public final List<ConfigList.Row> rows;
		public final String emptyMessage;

		private final ConfigList list;

		ListSnapshot(String title, ConfigList list) {
			this.title = title;
			this.list = list;
			this.rows = list.snapshot();
			this.emptyMessage = list.emptyMessage();
		}

		/** Presses a row's button. CALL ON THE GAME THREAD. */
		public void act(int index) {
			this.list.onAction(index);
		}

		/** Removes a row. CALL ON THE GAME THREAD. */
		public void remove(int index) {
			this.list.onRemove(index);
		}
	}

	/** One plugin's rail page, read in full, ready for the sidebar to draw. */
	public static final class PanelSnapshot {

		public final Entry entry;
		public final String title;
		public final String icon;
		public final List<ConfigList.Row> rows;
		public final String emptyMessage;

		private final ConfigList list;

		PanelSnapshot(Entry entry, Plugin.Panel panel) {
			this.entry = entry;
			this.title = panel.title;
			this.icon = panel.icon;
			this.list = panel.list;
			this.rows = panel.list.snapshot();
			this.emptyMessage = panel.list.emptyMessage();
		}

		/** Presses a row's button. CALL ON THE GAME THREAD. */
		public void act(int index) {
			this.list.onAction(index);
		}
	}

	/**
	 * Every running plugin's rail pages, in the order the plugins are listed.
	 *
	 * Only RUNNING plugins: a panel is registered in startUp(), so a stopped plugin has none,
	 * and its icon leaves the rail with it. CALL ON THE GAME THREAD, for the same reason the
	 * config lists are read there.
	 */
	public List<PanelSnapshot> snapshotPanels() {
		List<PanelSnapshot> out = new ArrayList<PanelSnapshot>();
		for (int i = 0; i < this.entries.size(); i++) {
			Entry entry = this.entries.get(i);
			if (!entry.enabled) {
				continue;
			}
			List<Plugin.Panel> panels = entry.plugin.getPanels();
			for (int j = 0; j < panels.size(); j++) {
				try {
					out.add(new PanelSnapshot(entry, panels.get(j)));
				} catch (Throwable error) {
					DevLog.log("PLUGIN", entry.name + "'s \"" + panels.get(j).title
						+ "\" panel threw while being read: " + error);
				}
			}
		}
		return out;
	}

	/**
	 * The plugin's config lists, each read in full. CALL ON THE GAME THREAD: that is the whole
	 * point of a snapshot - the rows usually come from state the game loop writes, and reading
	 * them from the UI thread is a race whose prize is an index out of bounds.
	 */
	public List<ListSnapshot> snapshotConfigLists(Entry entry) {
		List<ListSnapshot> out = new ArrayList<ListSnapshot>();
		if (entry == null) {
			return out;
		}
		List<Plugin.NamedList> lists = entry.plugin.getConfigLists();
		for (int i = 0; i < lists.size(); i++) {
			Plugin.NamedList named = lists.get(i);
			try {
				out.add(new ListSnapshot(named.title, named.list));
			} catch (Throwable error) {
				DevLog.log("PLUGIN", entry.name + "'s \"" + named.title + "\" list threw: " + error);
			}
		}
		return out;
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
			// Matched on the label, which is also what the row is identified by in the panel.
			// Two actions, so "it is an action" is no longer enough to say which.
			if (ACTION_RESET_OVERLAYS.equals(row.label)) {
				this.resetOverlayPositions();
			} else {
				this.reload();
			}
		}
	}
}
