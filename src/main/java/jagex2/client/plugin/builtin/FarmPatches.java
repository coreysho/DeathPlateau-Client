package jagex2.client.plugin.builtin;

/**
 * What each farming patch is doing, and how long it has been doing it.
 *
 * WHAT THE CLIENT CAN AND CANNOT KNOW, because the difference is the whole design. Every patch's
 * APPEARANCE lives in a varp the server transmits - eight bits per patch, encoding crop, stage
 * and condition together - so the client knows exactly what every patch looks like from anywhere
 * in the world, logged in at Lumbridge or standing in the patch. The patch's CLOCK does not
 * transmit: farming_time_N, the world minute it last advanced, is server-side only, and so is
 * farming_flags_N.
 *
 * So "ready", "diseased" and "stage 3 of 4" are exact and immediate. "Ready in 28 minutes" is
 * not available, and cannot be made available from here. What this does instead is WATCH: the
 * engine advances a patch at most once a minute while you are online, so once this has seen a
 * patch tick over twice it knows that patch's cadence and can say when the next one is due. Up
 * to then it says how long ago the last change was and nothing it cannot support.
 *
 * AN ESTIMATE IS MARKED AS ONE, everywhere, every time. A farm tracker that prints a confident
 * wrong minute is worse than one that says it does not know yet - the player plans a trip round
 * it. {@link #isEstimateReady} is false until two changes have actually been seen.
 *
 * NO CLIENT AND NO CACHE IN HERE. Everything is arithmetic over values it is handed, so the
 * rules can be tested rather than described.
 */
public final class FarmPatches {

	/** Nothing has been seen in this slot yet. */
	public static final int UNSEEN = -1;

	private final int[] state;
	private final long[] changedAt;
	private final long[] interval;
	private final int[] changes;

	public FarmPatches(int patches) {
		int size = patches < 0 ? 0 : patches;
		this.state = new int[size];
		this.changedAt = new long[size];
		this.interval = new long[size];
		this.changes = new int[size];
		for (int i = 0; i < size; i++) {
			this.state[i] = UNSEEN;
		}
	}

	public int size() {
		return this.state.length;
	}

	private boolean has(int patch) {
		return patch >= 0 && patch < this.state.length;
	}

	/**
	 * Tells it what a patch looks like now. True when that is a change from last time.
	 *
	 * THE FIRST SIGHT OF A PATCH IS NOT A CHANGE. Logging in sets every patch at once, and
	 * counting those as advances would have the page announce that all 32 just grew, and would
	 * poison the cadence with an interval measured from nothing.
	 */
	public boolean observe(int patch, int state, long minute) {
		if (!this.has(patch)) {
			return false;
		}
		if (this.state[patch] == UNSEEN) {
			this.state[patch] = state;
			this.changedAt[patch] = minute;
			return false;
		}
		if (this.state[patch] == state) {
			return false;
		}
		// The gap between the last two changes IS the cadence, but only once there have been two
		// changes to measure between: the first interval would be measured from the login that
		// first saw the patch, which is any length at all.
		if (this.changes[patch] >= 1) {
			long since = minute - this.changedAt[patch];
			this.interval[patch] = since > 0 ? since : 0;
		}
		this.changes[patch]++;
		this.state[patch] = state;
		this.changedAt[patch] = minute;
		return true;
	}

	/** What it last looked like, or {@link #UNSEEN}. */
	public int stateOf(int patch) {
		return this.has(patch) ? this.state[patch] : UNSEEN;
	}

	/** The minute it was last seen to change, or when it was first seen. */
	public long changedAt(int patch) {
		return this.has(patch) ? this.changedAt[patch] : 0;
	}

	/** How many changes have been seen here. */
	public int changes(int patch) {
		return this.has(patch) ? this.changes[patch] : 0;
	}

	/** Minutes since it last changed, never negative. */
	public long sinceChange(int patch, long minute) {
		if (!this.has(patch) || this.state[patch] == UNSEEN) {
			return 0;
		}
		long since = minute - this.changedAt[patch];
		return since < 0 ? 0 : since;
	}

	/**
	 * Whether there is a measured cadence for this patch yet.
	 *
	 * Two changes, not one. One change gives an interval measured from whenever this happened to
	 * first see the patch, which is not a cadence - it is how long ago you logged in.
	 */
	public boolean isEstimateReady(int patch) {
		return this.has(patch) && this.changes[patch] >= 2 && this.interval[patch] > 0;
	}

	/** The measured cadence in minutes, or 0 when there is not one yet. */
	public long intervalOf(int patch) {
		return this.isEstimateReady(patch) ? this.interval[patch] : 0;
	}

	/**
	 * Minutes until the next advance is due, or -1 when there is no cadence to work from.
	 *
	 * 0 means it is due now or overdue, which is a real answer: the engine advances a patch
	 * within a minute of the hour it is owed, and a patch that is late is usually one that has
	 * finished growing and has nothing left to advance to.
	 */
	public long untilNext(int patch, long minute) {
		if (!this.isEstimateReady(patch)) {
			return -1;
		}
		long due = this.changedAt[patch] + this.interval[patch] - minute;
		return due < 0 ? 0 : due;
	}

	/** Forgets everything, so a page can be reset without restarting the client. */
	public void reset() {
		for (int i = 0; i < this.state.length; i++) {
			this.state[i] = UNSEEN;
			this.changedAt[i] = 0;
			this.interval[i] = 0;
			this.changes[i] = 0;
		}
	}

	/**
	 * A span of minutes, for a sidebar row: "4m", "1h 12m", "2d 3h".
	 *
	 * Rounded down and never padded, because the number is an estimate the row is already
	 * marking as one and a false precision on top of it reads as certainty.
	 */
	public static String span(long minutes) {
		if (minutes < 0) {
			return "";
		}
		if (minutes < 60) {
			return minutes + "m";
		}
		long hours = minutes / 60;
		if (hours < 24) {
			long rest = minutes % 60;
			return rest == 0 ? hours + "h" : hours + "h " + rest + "m";
		}
		long days = hours / 24;
		long restHours = hours % 24;
		return restHours == 0 ? days + "d" : days + "d " + restHours + "h";
	}
}
