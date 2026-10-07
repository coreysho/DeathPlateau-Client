package jagex2.client.plugin.hub;

import java.util.Map;

/**
 * One plugin offered by the hub, as the index describes it.
 *
 * THIS IS UNTRUSTED DATA. Every field comes off the network, so nothing here is believed about
 * anything else: the id is checked to be a safe file name before it is used as one, and the jar
 * url is checked to be http or https before it is fetched. A field that is missing or the wrong
 * type reads as empty rather than throwing, so one malformed entry does not take the index with
 * it - {@link #isUsable()} is what decides whether an entry is offered at all.
 */
public final class HubEntry {

	/** Stable id, and the name its jar is saved under. Letters, digits, dash and underscore. */
	public final String id;

	public final String name;
	public final String description;
	public final String author;

	/** Free-form, compared as text: a different string means an update is available. */
	public final String version;

	/** Where the jar is. Must be http or https. */
	public final String url;

	/** Lower case SHA-256 of the jar, or "" when the index does not give one. */
	public final String sha256;

	HubEntry(Map<?, ?> object) {
		this.id = Json.string(object, "id", "").trim();
		this.name = Json.string(object, "name", this.id).trim();
		this.description = Json.string(object, "description", "").trim();
		this.author = Json.string(object, "author", "").trim();
		this.version = Json.string(object, "version", "").trim();
		this.url = Json.string(object, "url", "").trim();
		this.sha256 = Json.string(object, "sha256", "").trim().toLowerCase();
	}

	/**
	 * Whether this entry is safe to offer. An entry that fails any of these is dropped with a log
	 * line rather than shown, because every one of them is something that would otherwise be
	 * acted on later: a name used as a path, or a url handed to a downloader.
	 */
	public boolean isUsable() {
		return isSafeId(this.id) && this.name.length() > 0 && isHttpUrl(this.url);
	}

	/** The file this plugin installs to, inside the plugins folder. */
	public String fileName() {
		return this.id + ".jar";
	}

	/**
	 * An id is used as a file name, so it may only be letters, digits, dash and underscore - no
	 * dots, no separators, nothing that could walk out of the plugins folder. "../../client" is
	 * the attack; a 40-character cap keeps it a file name on every filesystem.
	 */
	public static boolean isSafeId(String id) {
		if (id == null || id.length() == 0 || id.length() > 40) {
			return false;
		}
		for (int i = 0; i < id.length(); i++) {
			char c = id.charAt(i);
			boolean allowed = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
				|| (c >= '0' && c <= '9') || c == '-' || c == '_';
			if (!allowed) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Only http and https get fetched. Without this an index could name a file:// or jar:// url
	 * and have the client read something off the player's own disk, or an ftp:// one and have it
	 * open a connection nothing here is expecting.
	 */
	public static boolean isHttpUrl(String url) {
		if (url == null) {
			return false;
		}
		String lower = url.toLowerCase();
		return lower.startsWith("http://") || lower.startsWith("https://");
	}

	/** True when this entry is served over plain http, which the panel says out loud. */
	public boolean isInsecure() {
		return this.url.toLowerCase().startsWith("http://");
	}
}
