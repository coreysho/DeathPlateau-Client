package jagex2.client.plugin;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A key=value text file in the client's cache directory, for plugin state that has to survive a
 * restart: which plugins are on, and every plugin's settings.
 *
 * Same rules as QolSettings, for the same reasons. Text rather than anything packed, so a key can
 * be added or dropped without invalidating the file and a player can read and fix it by hand.
 * Unknown keys are kept and written back untouched, so disabling a plugin for a while does not
 * throw away its settings. NOTHING HERE MAY THROW - a corrupt or unwritable preferences file is
 * never a reason to fail to start a game.
 */
final class PluginStore {

	private final File file;
	private final Map<String, String> values = new LinkedHashMap<String, String>();

	PluginStore(File file) {
		this.file = file;
		this.load();
	}

	String get(String key) {
		return this.values.get(key);
	}

	boolean getBoolean(String key, boolean fallback) {
		String value = this.values.get(key);
		if (value == null) {
			return fallback;
		}
		return value.equals("1") || value.equalsIgnoreCase("true");
	}

	void put(String key, String value) {
		this.values.put(key, value);
	}

	private void load() {
		BufferedReader reader = null;
		try {
			if (this.file == null || !this.file.exists()) {
				return;
			}
			reader = new BufferedReader(new FileReader(this.file));
			String line;
			while ((line = reader.readLine()) != null) {
				int split = line.indexOf('=');
				if (split <= 0) {
					continue;
				}
				this.values.put(line.substring(0, split).trim(), line.substring(split + 1).trim());
			}
		} catch (Exception ignored) {
			// Keep whatever parsed; every reader of this falls back to a default anyway.
		} finally {
			try {
				if (reader != null) {
					reader.close();
				}
			} catch (Exception ignored) {
			}
		}
	}

	void save() {
		PrintWriter writer = null;
		try {
			if (this.file == null) {
				return;
			}
			writer = new PrintWriter(new FileWriter(this.file));
			for (Map.Entry<String, String> entry : this.values.entrySet()) {
				writer.println(entry.getKey() + "=" + entry.getValue());
			}
		} catch (Exception ignored) {
			// Read-only cache dir or a full disk: the change holds for this session and is lost on
			// exit. Not worth interrupting play over.
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
