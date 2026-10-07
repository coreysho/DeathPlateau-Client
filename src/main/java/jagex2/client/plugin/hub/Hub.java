package jagex2.client.plugin.hub;

import java.io.IOException;
import jagex2.client.DevLog;
import jagex2.client.plugin.PluginManager;

/**
 * The plugin hub: what is on offer, what is installed, and installing it.
 *
 * Sits between the panel and {@link HubClient}, and owns the one thing neither of them can do
 * alone - the ORDER an install has to happen in:
 *
 *   1. stop the plugins from that jar and close its class loader, on the game thread. A loaded
 *      jar is an open file, and on Windows an open file cannot be replaced.
 *   2. download, verify and move the new jar into place, on a background thread. The game keeps
 *      running; only the plugins from that one jar are off, for as long as the download takes.
 *   3. rescan the plugins folder, on the game thread. Whatever was enabled comes back enabled,
 *      because the enabled state is saved per plugin and was never cleared.
 *
 * Every step that can fail leaves the previous state working: the download lands in a temp file,
 * and a plugin whose install failed is simply not there when the folder is rescanned.
 *
 * NETWORK WORK RUNS ON A THREAD OF ITS OWN. The game thread must never wait on a download, and
 * neither must the event dispatch thread. The callback comes back on that background thread -
 * the panel bounces it onto the EDT itself.
 */
public final class Hub {

	/** Where the index is read from, overridable for anyone running their own. */
	private static final String DEFAULT_INDEX =
		"https://raw.githubusercontent.com/coreysho/DeathPlateau-Plugins/main/index.json";

	private final PluginManager manager;
	private final HubState state;
	private final String indexUrl;

	private volatile HubIndex index;
	private volatile boolean busy;

	/** Told when something finishes. error is null on success, or a sentence to show the player. */
	public interface Callback {

		void onFinished(String error);
	}

	public Hub(PluginManager manager) {
		this.manager = manager;
		this.state = new HubState(manager.getPluginDirectory());
		String configured = System.getProperty("lostcity.pluginindex",
			System.getenv().getOrDefault("LOSTCITY_PLUGININDEX", DEFAULT_INDEX));
		this.indexUrl = configured == null || configured.trim().length() == 0 ? DEFAULT_INDEX : configured.trim();
	}

	public String getIndexUrl() {
		return this.indexUrl;
	}

	/** The last index fetched, or null if none has been. */
	public HubIndex getIndex() {
		return this.index;
	}

	/** True while a fetch or an install is running, so the panel can say so and not start another. */
	public boolean isBusy() {
		return this.busy;
	}

	/** The version installed for an entry, or null when it is not installed. */
	public String installedVersion(HubEntry entry) {
		return entry == null ? null : this.state.installedVersion(this.manager.getPluginDirectory(), entry.id);
	}

	public boolean isInstalled(HubEntry entry) {
		return this.installedVersion(entry) != null;
	}

	/**
	 * Whether the index offers a different version to the one installed. Versions are compared as
	 * text, not ordered: an index that moves a plugin backwards is offering that, and "different"
	 * is the honest word for it.
	 */
	public boolean hasUpdate(HubEntry entry) {
		String installed = this.installedVersion(entry);
		return installed != null && !installed.equals(entry.version);
	}

	// ------------------------------------------------------------------ actions

	/** Fetches the index in the background. */
	public void refresh(final Callback callback) {
		this.run("refresh", callback, new Job() {

			public void run() throws IOException {
				Hub.this.index = HubClient.fetchIndex(Hub.this.indexUrl);
				DevLog.log("HUB", Hub.this.index.getEntries().size() + " plugins offered by " + Hub.this.indexUrl);
			}
		});
	}

	/** Installs or updates one plugin, then rescans the plugins folder. */
	public void install(final HubEntry entry, final HubClient.Progress progress, final Callback callback) {
		if (entry == null) {
			return;
		}
		this.run("install " + entry.id, callback, new Job() {

			public void run() throws IOException {
				Hub.this.release(entry.fileName());
				HubClient.install(entry, Hub.this.manager.getPluginDirectory(), Hub.this.state, progress);
				Hub.this.reload();
			}
		});
	}

	/** Removes one plugin's jar, then rescans. */
	public void remove(final HubEntry entry, final Callback callback) {
		if (entry == null) {
			return;
		}
		this.run("remove " + entry.id, callback, new Job() {

			public void run() throws IOException {
				Hub.this.release(entry.fileName());
				HubClient.remove(entry.id, Hub.this.manager.getPluginDirectory(), Hub.this.state);
				Hub.this.reload();
			}
		});
	}

	// ------------------------------------------------------------------ running the work

	private interface Job {

		void run() throws IOException;
	}

	/**
	 * Runs a job on a thread of its own and reports what happened.
	 *
	 * One at a time: the panel's buttons go quiet while something is running, and this refuses a
	 * second job anyway, because two installs racing for the same folder is not worth reasoning
	 * about.
	 */
	private void run(final String what, final Callback callback, final Job job) {
		if (this.busy) {
			if (callback != null) {
				callback.onFinished("Something else is still running.");
			}
			return;
		}
		this.busy = true;
		Thread thread = new Thread(new Runnable() {

			public void run() {
				String error = null;
				try {
					job.run();
				} catch (IOException failure) {
					error = failure.getMessage() == null ? failure.toString() : failure.getMessage();
					DevLog.log("HUB", what + " failed: " + error);
				} catch (Throwable failure) {
					// A bug in here must not take the client's other threads with it, and the
					// player gets told rather than watching a button do nothing forever.
					error = "something went wrong: " + failure;
					DevLog.log("HUB", what + " threw: " + failure);
				} finally {
					Hub.this.busy = false;
				}
				if (callback != null) {
					callback.onFinished(error);
				}
			}
		}, "plugin-hub");
		thread.setDaemon(true);                 // never holds the client open at exit
		thread.start();
	}

	/** Stops and closes a jar on the game thread, and waits for it to have happened. */
	private void release(final String fileName) throws IOException {
		this.onClientThread(new Runnable() {

			public void run() {
				Hub.this.manager.releaseJar(fileName);
			}
		});
	}

	/** Rescans the plugins folder on the game thread, and waits for it to have happened. */
	private void reload() throws IOException {
		this.onClientThread(new Runnable() {

			public void run() {
				Hub.this.manager.reload();
			}
		});
	}

	/**
	 * Queues work for the game thread and waits for it to run.
	 *
	 * The waiting is the point: the download must not start until the old jar is closed, and the
	 * rescan must have happened before the panel is told the install is done. The timeout is
	 * there because this is a background thread waiting on the game loop, and a game loop that
	 * has stopped - a client shutting down - must not leave this thread waiting forever.
	 */
	private void onClientThread(final Runnable task) throws IOException {
		final Object lock = new Object();
		final boolean[] done = new boolean[1];
		this.manager.invokeOnClientThread(new Runnable() {

			public void run() {
				try {
					task.run();
				} finally {
					synchronized (lock) {
						done[0] = true;
						lock.notifyAll();
					}
				}
			}
		});
		synchronized (lock) {
			long deadline = System.currentTimeMillis() + 10000;
			while (!done[0]) {
				long remaining = deadline - System.currentTimeMillis();
				if (remaining <= 0) {
					throw new IOException("the game did not respond - is the client still running?");
				}
				try {
					lock.wait(remaining);
				} catch (InterruptedException interrupted) {
					Thread.currentThread().interrupt();
					throw new IOException("interrupted");
				}
			}
		}
	}
}
