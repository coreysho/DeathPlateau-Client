package jagex2.client.plugin.hub;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What the hub has installed: which plugin id, at which version.
 *
 * Kept in installed.txt in the plugins folder, next to the jars it describes, so a player who
 * copies that folder to another machine carries the record with it. One line per plugin,
 * "id<tab>version".
 *
 * IT IS A RECORD, NOT THE TRUTH. The jars on disk are the truth - a player can delete one by
 * hand, or drop one in that the hub never installed. Everything here is checked against the file
 * actually being there before it is believed, which is why {@link #installedVersion(File, String)}
 * takes the folder.
 *
 * Nothing here throws. A missing or corrupt file means nothing is known to be installed, which
 * shows every hub plugin as not installed - annoying, not broken, and fixed by installing again.
 */
public final class HubState {

	private static final String FILE_NAME = "installed.txt";

	private final File file;
	private final Map<String, String> versions = new LinkedHashMap<String, String>();

	public HubState(File pluginDirectory) {
		this.file = new File(pluginDirectory, FILE_NAME);
		this.load();
	}

	/**
	 * The version installed for an id, or null when the hub did not install it or its jar is no
	 * longer there. The file check is what makes a hand-deleted jar show as not installed.
	 */
	public String installedVersion(File pluginDirectory, String id) {
		String version = this.versions.get(id);
		if (version == null) {
			return null;
		}
		return new File(pluginDirectory, id + ".jar").isFile() ? version : null;
	}

	public void record(String id, String version) {
		this.versions.put(id, version == null ? "" : version);
		this.save();
	}

	public void forget(String id) {
		if (this.versions.remove(id) != null) {
			this.save();
		}
	}

	private void load() {
		BufferedReader reader = null;
		try {
			if (!this.file.isFile()) {
				return;
			}
			reader = new BufferedReader(new FileReader(this.file));
			String line;
			while ((line = reader.readLine()) != null) {
				int tab = line.indexOf('\t');
				if (tab <= 0) {
					continue;
				}
				String id = line.substring(0, tab).trim();
				// An id out of this file is used to build a path, so it gets the same check an id
				// out of the index does. A hand-edited line cannot turn into a path traversal.
				if (HubEntry.isSafeId(id)) {
					this.versions.put(id, line.substring(tab + 1).trim());
				}
			}
		} catch (Exception ignored) {
			// Unreadable: nothing is known to be installed. Every caller copes with that.
		} finally {
			try {
				if (reader != null) {
					reader.close();
				}
			} catch (Exception ignored) {
			}
		}
	}

	private void save() {
		PrintWriter writer = null;
		try {
			writer = new PrintWriter(new FileWriter(this.file));
			for (Map.Entry<String, String> entry : this.versions.entrySet()) {
				writer.println(entry.getKey() + "\t" + entry.getValue());
			}
		} catch (Exception ignored) {
			// The plugin is installed either way - the jar is on disk. Only the record of which
			// version it is would be lost, and the hub would offer the update again.
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
