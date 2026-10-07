package jagex2.client.plugin.hub;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.jar.JarFile;

import jagex2.client.DevLog;

/**
 * Fetches the plugin index and installs what the player picks from it.
 *
 * THIS DOWNLOADS CODE AND PUTS IT WHERE THE CLIENT WILL RUN IT, which is the most dangerous thing
 * in the client, so the rules are narrow and all in one place:
 *
 *   - nothing is ever fetched or installed except when the player clicked Install on that entry
 *   - only http and https urls, checked in HubEntry, so an index cannot point at the local disk
 *   - the file name is the entry's id, checked to be letters, digits, dash and underscore, so an
 *     index cannot write outside the plugins folder
 *   - a download is capped, so a hostile index cannot fill the disk
 *   - if the index gives a sha256 it must match, or the file is thrown away
 *   - the file must open as a jar before it is moved into place
 *   - the download lands in a temp file and is moved over only once it has passed all of that,
 *     so a failed install leaves whatever was there working
 *
 * WHAT IT CANNOT DO is make an installed plugin safe. A plugin is ordinary Java in the client's
 * process with the client's access to the machine; a checksum proves the jar is the one the index
 * meant, not that it is harmless. That is why installing is deliberate, and why the panel says
 * where a plugin came from.
 *
 * Every method here BLOCKS ON THE NETWORK and must be called from a background thread.
 */
public final class HubClient {

	/** Biggest index we will read, and biggest jar we will download. */
	private static final int MAX_INDEX_BYTES = 1024 * 1024;
	private static final int MAX_JAR_BYTES = 32 * 1024 * 1024;

	private static final int CONNECT_TIMEOUT = 10000;
	private static final int READ_TIMEOUT = 30000;

	/** GitHub's API refuses requests without one, and it says who is asking. */
	private static final String USER_AGENT = "DeathPlateau-Client";

	private HubClient() {
	}

	/** Downloads and parses the index. Throws IOException with a sentence fit to show a player. */
	public static HubIndex fetchIndex(String url) throws IOException {
		if (!HubEntry.isHttpUrl(url)) {
			throw new IOException("the plugin index address must start with http:// or https://");
		}
		byte[] bytes = read(open(url), MAX_INDEX_BYTES, null);
		try {
			return HubIndex.parse(new String(bytes, StandardCharsets.UTF_8));
		} catch (RuntimeException error) {
			throw new IOException("the plugin index is not valid JSON (" + error.getMessage() + ")");
		}
	}

	/** Told how a download is going, so the panel can say so. Called off the game thread. */
	public interface Progress {

		void onProgress(long done, long total);
	}

	/**
	 * Downloads an entry's jar and puts it in the plugins folder, replacing any previous version.
	 * Returns when the file is in place; the caller reloads the plugins.
	 */
	public static void install(HubEntry entry, File pluginDirectory, HubState state, Progress progress)
		throws IOException {
		if (entry == null || !entry.isUsable()) {
			throw new IOException("that plugin's index entry is not usable");
		}
		if (!pluginDirectory.isDirectory() && !pluginDirectory.mkdirs()) {
			throw new IOException("could not create " + pluginDirectory);
		}

		File target = new File(pluginDirectory, entry.fileName());
		File temp = new File(pluginDirectory, entry.fileName() + ".part");

		HttpURLConnection connection = open(entry.url);
		long expected = connection.getContentLengthLong();
		if (expected > MAX_JAR_BYTES) {
			throw new IOException("that plugin is larger than " + (MAX_JAR_BYTES / 1024 / 1024) + "MB");
		}

		MessageDigest digest = digest();
		InputStream in = null;
		OutputStream out = null;
		try {
			in = connection.getInputStream();
			out = new FileOutputStream(temp);
			byte[] buffer = new byte[16384];
			long done = 0;
			int n;
			while ((n = in.read(buffer)) != -1) {
				done += n;
				if (done > MAX_JAR_BYTES) {
					throw new IOException("that plugin is larger than "
						+ (MAX_JAR_BYTES / 1024 / 1024) + "MB");
				}
				out.write(buffer, 0, n);
				if (digest != null) {
					digest.update(buffer, 0, n);
				}
				if (progress != null) {
					progress.onProgress(done, expected);
				}
			}
		} finally {
			close(in);
			close(out);
		}

		try {
			verify(entry, temp, digest);
			// Replacing a jar that is still loaded fails on Windows, so the manager closes its
			// class loader before calling this. If the move still fails, the temp file goes and
			// whatever was installed before is untouched.
			Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException error) {
			temp.delete();
			throw error;
		}

		state.record(entry.id, entry.version);
		DevLog.log("HUB", "installed " + entry.id + " " + entry.version + " from " + entry.url);
	}

	/** Checks what was downloaded is what the index described, and is a jar at all. */
	private static void verify(HubEntry entry, File file, MessageDigest digest) throws IOException {
		if (entry.sha256.length() > 0) {
			if (digest == null) {
				throw new IOException("this Java has no SHA-256, so the download cannot be checked");
			}
			String actual = hex(digest.digest());
			if (!actual.equals(entry.sha256)) {
				throw new IOException("the download did not match the checksum in the index"
					+ " (got " + actual.substring(0, 12) + "..., expected "
					+ entry.sha256.substring(0, Math.min(12, entry.sha256.length())) + "...)");
			}
		}
		JarFile jar = null;
		try {
			jar = new JarFile(file);
		} catch (IOException error) {
			throw new IOException("the download is not a jar - it may have been cut off");
		} finally {
			close(jar);
		}
	}

	/** Deletes an installed plugin's jar. The manager closes its loader first. */
	public static void remove(String id, File pluginDirectory, HubState state) throws IOException {
		if (!HubEntry.isSafeId(id)) {
			throw new IOException("that is not a plugin id");
		}
		File file = new File(pluginDirectory, id + ".jar");
		if (file.isFile() && !file.delete()) {
			throw new IOException("could not delete " + file.getName() + " - it may still be in use");
		}
		state.forget(id);
		DevLog.log("HUB", "removed " + id);
	}

	// ------------------------------------------------------------------ plumbing

	private static HttpURLConnection open(String url) throws IOException {
		HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
		connection.setRequestProperty("User-Agent", USER_AGENT);
		connection.setConnectTimeout(CONNECT_TIMEOUT);
		connection.setReadTimeout(READ_TIMEOUT);
		connection.setInstanceFollowRedirects(true);
		int code = connection.getResponseCode();
		if (code == 404) {
			throw new IOException("not found (404) - check the address");
		}
		if (code != 200) {
			throw new IOException("the server answered " + code);
		}
		return connection;
	}

	private static byte[] read(HttpURLConnection connection, int limit, Progress progress) throws IOException {
		InputStream in = null;
		try {
			in = connection.getInputStream();
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			byte[] buffer = new byte[8192];
			int n;
			while ((n = in.read(buffer)) != -1) {
				if (out.size() + n > limit) {
					throw new IOException("the response is larger than " + (limit / 1024) + "KB");
				}
				out.write(buffer, 0, n);
			}
			return out.toByteArray();
		} finally {
			close(in);
		}
	}

	private static MessageDigest digest() {
		try {
			return MessageDigest.getInstance("SHA-256");
		} catch (Throwable error) {
			return null;                   // every Java has it; verify() reports it if one does not
		}
	}

	private static String hex(byte[] bytes) {
		StringBuilder out = new StringBuilder(bytes.length * 2);
		for (int i = 0; i < bytes.length; i++) {
			int value = bytes[i] & 0xFF;
			out.append(Character.forDigit(value >> 4, 16)).append(Character.forDigit(value & 15, 16));
		}
		return out.toString();
	}

	private static void close(java.io.Closeable closeable) {
		try {
			if (closeable != null) {
				closeable.close();
			}
		} catch (Exception ignored) {
		}
	}
}
