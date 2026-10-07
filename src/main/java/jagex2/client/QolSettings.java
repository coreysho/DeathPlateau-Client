package jagex2.client;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;

/**
 * Client-side toggles for the QoL features built into this client.
 *
 * WHY THIS EXISTS. Around a dozen QoL behaviours had accumulated in Client.java, every one of them
 * hardcoded on with no way to turn it off. This is the switchboard for them, and the place any
 * future one plugs into.
 *
 * WHAT IT IS NOT. This is not a plugin API. There is no class loader, no event bus and no isolation
 * here - features are compiled into the client and this only decides whether each one runs. Adding a
 * feature still means editing Client.java. What this buys is that the player can turn things off,
 * that the choice survives a restart, and that there is one list to add to rather than a dozen
 * scattered hardcoded behaviours.
 *
 * FILE FORMAT. Plain "key=0/1" text in the client's own cache directory, one line per setting, with
 * a version line. Text rather than a packed bitfield on purpose: settings can then be added,
 * removed or reordered without invalidating anyone's saved file, because each line is matched by
 * name and anything unrecognised is ignored. An absent key falls back to its default, so a player
 * upgrading from a build that predates a feature simply gets that feature's default.
 *
 * NOTHING HERE MAY THROW. A settings file that is missing, unreadable, truncated or full of garbage
 * must leave the client running on defaults - a broken preferences file is never a reason to fail to
 * start a game.
 */
public final class QolSettings {

	private static final int FILE_VERSION = 1;
	private static final String FILE_NAME = "qol_settings.dat";

	// Indices into the parallel arrays below. Order here is display order in the panel; it is NOT
	// persisted, so these can be reordered freely - and entries can be removed, because the file
	// is matched by name.
	//
	// SETTINGS LEAVE HERE WHEN THEY BECOME PLUGINS. "Escape closes interfaces", "Hide roofs", "XP
	// drops", "Barrows doors" and "Left-click swaps" have gone (jagex2.client.plugin.builtin); their keys live on as the legacySetting
	// on those plugins, which read this file once to carry across a choice the player had made.
	// Removing one from here does not disturb the rest: the saved file is key=value, so nothing
	// shifts.
	public static final int CHAT_HISTORY = 0;
	public static final int COMPASS_NORTH = 1;
	public static final int SHIFT_DROP = 2;
	public static final int SPACE_CONTINUE = 3;
	public static final int DIALOGUE_KEYS = 4;
	public static final int BANKPIN_KEYS = 5;
	public static final int TAB_REPLY = 6;
	public static final int MMB_CAMERA = 7;
	public static final int WHEEL_ZOOM = 8;
	public static final int WHEEL_CHAT = 9;
	public static final int WHEEL_INTERFACE = 10;
	public static final int GROUND_ITEMS = 11;
	public static final int ANTI_DRAG = 12;

	/** Stable keys written to disk. NEVER rename one of these - it silently resets that setting. */
	private static final String[] KEYS = {
		"chat_history", "compass_north", "shift_drop", "space_continue", "dialogue_keys",
		"bankpin_keys", "tab_reply", "mmb_camera", "wheel_zoom", "wheel_chat",
		"wheel_interface", "ground_items", "anti_drag"
	};

	private static final String[] LABELS = {
		"Chat history (Page Up/Down)", "Click compass to face north", "Shift-click to drop",
		"Space advances dialogue", "Number keys pick dialogue option", "Number keys for bank PIN",
		"Tab replies to last PM", "Middle-mouse camera drag",
		"Scroll wheel zooms camera", "Scroll wheel scrolls chat", "Scroll wheel scrolls interfaces",
		"Ground item names", "Anti-drag (items drag after 0.2s)"
	};

	// Every default is ON. For the twelve that predate this class that was the point - introducing
	// a switchboard should not change what anyone sees. Features added since default ON too, on
	// the grounds that a QoL feature nobody discovers is a QoL feature nobody has; the panel is one
	// keypress away for anyone who wants it off.
	//
	// The one exception was "Hide roofs", which defaulted off because it changes what the world
	// looks like rather than adding a convenience. It is a plugin now and that reasoning went with
	// it, which is why this array is all true again.
	private static final boolean[] DEFAULTS = {
		true, true, true, true, true, true, true, true, true, true, true, true, true
	};

	public static final int COUNT = KEYS.length;

	private static final boolean[] enabled = new boolean[COUNT];
	private static boolean loaded;

	private QolSettings() {
	}

	public static boolean on(int setting) {
		if (!loaded) {
			load();
		}
		if (setting < 0 || setting >= COUNT) {
			return false;
		}
		return enabled[setting];
	}

	public static String label(int setting) {
		if (setting < 0 || setting >= COUNT) {
			return "";
		}
		return LABELS[setting];
	}

	public static void toggle(int setting) {
		if (!loaded) {
			load();
		}
		if (setting < 0 || setting >= COUNT) {
			return;
		}
		enabled[setting] = !enabled[setting];
		save();
	}

	/**
	 * What the saved file says about a key, or null when it does not mention it.
	 *
	 * Different from on(), which answers with the default for anything unsaved. The difference is
	 * the whole point here: this is read when a setting becomes a plugin, to carry across a
	 * choice the player actually made, and a default is not a choice.
	 *
	 * Reads the file rather than the array because a setting that has become a plugin is no
	 * longer in the array at all.
	 */
	public static Boolean saved(String key) {
		BufferedReader reader = null;
		try {
			File file = new File(sign.signlink.findcachedir() + FILE_NAME);
			if (!file.exists()) {
				return null;
			}
			reader = new BufferedReader(new FileReader(file));
			String line;
			while ((line = reader.readLine()) != null) {
				int split = line.indexOf('=');
				if (split > 0 && line.substring(0, split).trim().equals(key)) {
					return Boolean.valueOf(line.substring(split + 1).trim().equals("1"));
				}
			}
		} catch (Exception ignored) {
			// Unreadable: nothing was chosen as far as we can tell, so the plugin's default wins.
		} finally {
			try {
				if (reader != null) {
					reader.close();
				}
			} catch (Exception ignored) {
			}
		}
		return null;
	}

	public static void load() {
		// Set before the read, not after: if anything below throws, defaults are already in place and
		// load() will not be re-entered on every subsequent on() call.
		System.arraycopy(DEFAULTS, 0, enabled, 0, COUNT);
		loaded = true;

		BufferedReader reader = null;
		try {
			File file = new File(sign.signlink.findcachedir() + FILE_NAME);
			if (!file.exists()) {
				return;
			}
			reader = new BufferedReader(new FileReader(file));
			String line;
			while ((line = reader.readLine()) != null) {
				int split = line.indexOf('=');
				if (split <= 0) {
					continue;
				}
				String key = line.substring(0, split).trim();
				String value = line.substring(split + 1).trim();
				for (int i = 0; i < COUNT; i++) {
					if (KEYS[i].equals(key)) {
						enabled[i] = value.equals("1");
						break;
					}
				}
			}
		} catch (Exception ex) {
			// Unreadable or corrupt: keep whatever was parsed, defaults for the rest.
		} finally {
			try {
				if (reader != null) {
					reader.close();
				}
			} catch (Exception ignored) {
			}
		}
	}

	public static void save() {
		PrintWriter writer = null;
		try {
			writer = new PrintWriter(new FileWriter(sign.signlink.findcachedir() + FILE_NAME));
			writer.println("version=" + FILE_VERSION);
			for (int i = 0; i < COUNT; i++) {
				writer.println(KEYS[i] + "=" + (enabled[i] ? "1" : "0"));
			}
		} catch (Exception ex) {
			// Read-only cache dir or a full disk: the setting still applies for this session, it just
			// will not survive a restart. Not worth interrupting play over.
		} finally {
			try {
				if (writer != null) {
					writer.close();
				}
			} catch (Exception ignored) {
			}
		}
	}
}
