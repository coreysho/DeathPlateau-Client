package jagex2.client.plugin.hub;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jagex2.client.DevLog;

/**
 * The list of plugins the hub offers, parsed from the index file.
 *
 * The index is either an object with a "plugins" array, or just the array - both are accepted
 * because the second is what someone writes by hand the first time.
 *
 * <pre>
 * {
 *   "plugins": [
 *     {
 *       "id": "coordinates",
 *       "name": "Coordinates",
 *       "description": "Shows your world position",
 *       "author": "Corey",
 *       "version": "1.0",
 *       "url": "https://github.com/.../releases/download/v1.0/coordinates.jar",
 *       "sha256": "9f86d0818..."
 *     }
 *   ]
 * }
 * </pre>
 *
 * ONE BAD ENTRY DOES NOT SPOIL THE INDEX. An entry that is malformed, or whose id or url would
 * not be safe to act on, is dropped with a log line and the rest are offered. A duplicate id is
 * dropped too: ids name files, and two entries claiming one file is not a thing to guess at.
 */
public final class HubIndex {

	private final List<HubEntry> entries = new ArrayList<HubEntry>();

	private HubIndex() {
	}

	public List<HubEntry> getEntries() {
		return this.entries;
	}

	public HubEntry byId(String id) {
		for (int i = 0; i < this.entries.size(); i++) {
			if (this.entries.get(i).id.equals(id)) {
				return this.entries.get(i);
			}
		}
		return null;
	}

	/** Parses an index. Throws IllegalArgumentException when the text is not a usable index. */
	public static HubIndex parse(String text) {
		Object root = Json.parse(text);
		List<?> list;
		if (root instanceof List) {
			list = (List<?>) root;
		} else if (root instanceof Map) {
			list = Json.array((Map<?, ?>) root, "plugins");
		} else {
			throw new IllegalArgumentException("the index is not an object or an array");
		}

		HubIndex index = new HubIndex();
		for (int i = 0; i < list.size(); i++) {
			Object element = list.get(i);
			if (!(element instanceof Map)) {
				DevLog.log("HUB", "index entry " + i + " is not an object");
				continue;
			}
			HubEntry entry = new HubEntry((Map<?, ?>) element);
			if (!entry.isUsable()) {
				DevLog.log("HUB", "index entry " + i + " dropped: "
					+ (HubEntry.isSafeId(entry.id) ? "bad name or url" : "unusable id \"" + entry.id + "\""));
				continue;
			}
			if (index.byId(entry.id) != null) {
				DevLog.log("HUB", "index entry " + i + " dropped: duplicate id " + entry.id);
				continue;
			}
			index.entries.add(entry);
		}
		return index;
	}
}
