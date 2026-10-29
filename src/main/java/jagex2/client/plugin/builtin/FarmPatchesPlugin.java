package jagex2.client.plugin.builtin;

import jagex2.client.plugin.ConfigItem;
import jagex2.client.plugin.ConfigList;
import jagex2.client.plugin.Plugin;
import jagex2.client.plugin.PluginDescriptor;
import jagex2.client.plugin.Subscribe;
import jagex2.client.plugin.event.GameTick;

/**
 * Every farming patch, what is in it and what it is doing - from anywhere in the world.
 *
 * WHY THIS IS NOT A COUNTDOWN, which is what RuneLite's farming plugin shows and what anybody
 * asking for "farm timers" means. Old School publishes a global growth tick on a fixed wall
 * clock, so its client can work out to the minute when a patch will next advance. This server
 * keeps a per-patch timestamp instead - farming_time_N, the world minute a patch last moved -
 * and that varp does NOT transmit. Neither does farming_flags_N, which holds the crop id. The
 * client has the picture and no clock.
 *
 * WHAT THE PICTURE IS WORTH, which turns out to be most of it. The appearance varp does
 * transmit, eight bits per patch, and the cache's own multiloc table says what each value
 * means: which crop, which growth stage, whether it is diseased or dead, and whether the thing
 * it has become offers Harvest. So READY and DISEASED - the two answers a farm run is actually
 * checking for - are exact, immediate, and available from the other side of the map. None of it
 * is guessed and none of it is a copy of the server's crop tables: it is all read out of the
 * cache through API level 11.
 *
 * AND A TIMER, EARNED RATHER THAN ASSUMED. The engine advances a patch at most once a minute
 * while you are online, so {@link FarmPatches} watches. Once it has seen a patch tick over
 * twice it knows that patch's cadence and says when the next one is due, marked as an estimate.
 * Before that it says how long ago the patch last changed and nothing more. An estimate is
 * labelled every time it is shown: a farm tracker that prints a confident wrong minute is worse
 * than one that admits it does not know yet, because the player plans a trip around it.
 *
 * FINDING THE PATCHES. The varbit ids below are original 377 cache data and do not move with the
 * content; the loc that each one drives is content-numbered and does, so it is looked up at run
 * time rather than written down. That lookup is a scan of the whole loc table, which is 17,000
 * decodes through a 20-entry round-robin cache - far too much for one tick - so it is done a
 * slice at a time in the background and remembered.
 */
@PluginDescriptor(
	name = "Farm patches",
	description = "Every patch, what is growing and whether it is ready - from anywhere",
	key = "farm-patches",
	apiLevel = 11
)
public final class FarmPatchesPlugin extends Plugin {

	/**
	 * The farming patches, as cache varbit id and the name a player knows them by.
	 *
	 * ORIGINAL CACHE DATA. These varbits and their multiloc tables came with the 377 cache - the
	 * server's farming content reads and writes the same slices rather than inventing its own -
	 * so unlike a loc id they do not move when the content is repacked. Derived from
	 * farming_state.rs2's accessor crossed with the cache's varbit table; the test pins them.
	 *
	 * THE SPECIAL PATCHES ARE NOT HERE: spirit tree, calquat, cactus, mushroom and belladonna
	 * pack into SIX bits rather than eight and do not line up with a cache varbit one to one.
	 * Left out rather than half-handled, because a patch shown wrong is worse than one missing.
	 */
	static final int[] PATCH_VARBIT = {
		708, 709, 710, 711, 712, 713, 714, 715,   // allotments 1-8
		780, 781, 782, 783,                       // herbs 1-4
		716, 717, 718, 719,                       // hops 1-4
		728, 729, 730, 731,                       // flowers 1-4
		732, 733, 734, 735,                       // bushes 1-4
		700, 701, 702, 703,                       // trees 1-4
		704, 705, 706, 707                        // fruit trees 1-4
	};

	static final String[] PATCH_NAME = {
		"Allotment 1", "Allotment 2", "Allotment 3", "Allotment 4",
		"Allotment 5", "Allotment 6", "Allotment 7", "Allotment 8",
		"Herb patch 1", "Herb patch 2", "Herb patch 3", "Herb patch 4",
		"Hops patch 1", "Hops patch 2", "Hops patch 3", "Hops patch 4",
		"Flower patch 1", "Flower patch 2", "Flower patch 3", "Flower patch 4",
		"Bush patch 1", "Bush patch 2", "Bush patch 3", "Bush patch 4",
		"Tree patch 1", "Tree patch 2", "Tree patch 3", "Tree patch 4",
		"Fruit tree 1", "Fruit tree 2", "Fruit tree 3", "Fruit tree 4"
	};

	/** Locs looked at per tick while the index is being built. Small enough not to be felt. */
	static final int SCAN_PER_TICK = 400;

	@ConfigItem(keyName = "onlyPlanted", name = "Hide empty patches",
		description = "Leave out patches that are bare soil or weeds.")
	public boolean onlyPlanted = true;

	@ConfigItem(keyName = "readyFirst", name = "Put ready and diseased patches first",
		description = "Rather than keeping them in patch order.")
	public boolean readyFirst = true;

	private final FarmPatches patches = new FarmPatches(PATCH_VARBIT.length);

	/** varbit index -> the loc whose multiloc table that varbit drives, or -1 if not found yet. */
	private final int[] patchLoc = new int[PATCH_VARBIT.length];

	private int scanned;
	private boolean scanDone;

	@Override
	protected void startUp() {
		for (int i = 0; i < this.patchLoc.length; i++) {
			this.patchLoc[i] = -1;
		}
		try {
			this.addPanel("Farming", "sprout", new ConfigList() {

				public int size() {
					return FarmPatchesPlugin.this.rows().length;
				}

				public String label(int index) {
					return FarmPatchesPlugin.this.label(index);
				}

				public String detail(int index) {
					return FarmPatchesPlugin.this.detail(index);
				}

				public String value(int index) {
					return FarmPatchesPlugin.this.value(index);
				}

				public boolean removable(int index) {
					return false;
				}

				public String emptyMessage() {
					return "Nothing planted, or no patch seen yet.";
				}
			});
		} catch (Throwable olderClient) {
			// No rail pages in this client. Nothing else here depends on one.
		}
	}

	@Subscribe
	public void onGameTick(GameTick tick) {
		if (!this.ctx.isLoggedIn()) {
			return;
		}
		this.scanSome();
		long minute = now();
		for (int i = 0; i < PATCH_VARBIT.length; i++) {
			this.patches.observe(i, this.ctx.getVarbit(PATCH_VARBIT[i]), minute);
		}
	}

	/** Wall clock in minutes. The patch clock is the server's and does not reach here. */
	static long now() {
		return System.currentTimeMillis() / 60000L;
	}

	/**
	 * Works through the loc table a slice at a time looking for each patch's multiloc.
	 *
	 * A SLICE, NOT THE LOT. The table is around 17,000 entries and LocType hands them out of a
	 * 20-entry round-robin cache, so reading all of them decodes all of them - on the thread
	 * that is also drawing the game. Spread over ticks it is invisible; done at once it is a
	 * visible hitch on login, which is the one moment a player is already waiting.
	 */
	private void scanSome() {
		if (this.scanDone) {
			return;
		}
		int count = this.ctx.getLocCount();
		if (count <= 0) {
			return;
		}
		int end = this.scanned + SCAN_PER_TICK;
		if (end > count) {
			end = count;
		}
		for (int loc = this.scanned; loc < end; loc++) {
			int varbit = this.ctx.getLocVarbit(loc);
			if (varbit < 0) {
				continue;
			}
			for (int i = 0; i < PATCH_VARBIT.length; i++) {
				if (PATCH_VARBIT[i] == varbit && this.patchLoc[i] == -1) {
					this.patchLoc[i] = loc;
				}
			}
		}
		this.scanned = end;
		this.scanDone = this.scanned >= count;
	}

	// ------------------------------------------------------------------ what a patch is doing

	/** The loc a patch has become, or -1 while the index is still being built. */
	int formOf(int patch) {
		if (patch < 0 || patch >= this.patchLoc.length || this.patchLoc[patch] == -1) {
			return -1;
		}
		return this.ctx.resolveLoc(this.patchLoc[patch]);
	}

	/**
	 * Whether a patch can be harvested, which is not a number anywhere: it is whether the form
	 * the patch is currently in offers Harvest. Reading the cache's own option beats counting
	 * growth stages, because the cache is what the game itself goes by.
	 */
	static boolean isReady(String op1, String op2, String op3) {
		return "Harvest".equalsIgnoreCase(op1) || "Harvest".equalsIgnoreCase(op2)
			|| "Harvest".equalsIgnoreCase(op3)
			|| "Pick".equalsIgnoreCase(op1) || "Pick".equalsIgnoreCase(op2)
			|| "Pick".equalsIgnoreCase(op3);
	}

	/** Whether a patch's current form is a diseased or dead one, which its name says. */
	static boolean isSick(String name) {
		if (name == null) {
			return false;
		}
		String lower = name.toLowerCase();
		return lower.indexOf("diseased") >= 0 || lower.indexOf("dead") >= 0;
	}

	/**
	 * Whether a form is bare ground rather than a crop.
	 *
	 * By name, because that is what the cache gives: the empty forms of every patch are weeds
	 * and bare soil, and nothing growing is called either.
	 */
	static boolean isEmpty(String name) {
		if (name == null || name.length() == 0) {
			return true;
		}
		String lower = name.toLowerCase();
		return lower.indexOf("weed") >= 0 || lower.indexOf("patch") >= 0 || lower.indexOf("soil") >= 0;
	}

	/** How a row sorts when ready and diseased are put first: lower is nearer the top. */
	static int rank(boolean ready, boolean sick, boolean empty) {
		if (sick) {
			return 0;
		}
		if (ready) {
			return 1;
		}
		return empty ? 3 : 2;
	}

	// ------------------------------------------------------------------ the page

	/** The patches to show, in the order they are shown. */
	int[] rows() {
		int[] all = new int[PATCH_VARBIT.length];
		int count = 0;
		for (int i = 0; i < PATCH_VARBIT.length; i++) {
			int form = this.formOf(i);
			if (form == -1) {
				continue;
			}
			if (this.onlyPlanted && isEmpty(this.ctx.getLocName(form))) {
				continue;
			}
			all[count++] = i;
		}
		int[] rows = new int[count];
		System.arraycopy(all, 0, rows, 0, count);
		if (this.readyFirst) {
			sortByRank(rows, this);
		}
		return rows;
	}

	/**
	 * An insertion sort by rank, keeping patch order within a rank.
	 *
	 * Stable on purpose: the patches have a natural order a player knows - herb patch 1 then 2 -
	 * and a sort that reshuffles equals makes a page that moves under the eye every tick.
	 */
	static void sortByRank(int[] rows, FarmPatchesPlugin plugin) {
		for (int i = 1; i < rows.length; i++) {
			int row = rows[i];
			int rank = plugin.rankOf(row);
			int j = i - 1;
			while (j >= 0 && plugin.rankOf(rows[j]) > rank) {
				rows[j + 1] = rows[j];
				j--;
			}
			rows[j + 1] = row;
		}
	}

	int rankOf(int patch) {
		int form = this.formOf(patch);
		if (form == -1) {
			return 3;
		}
		String name = this.ctx.getLocName(form);
		return rank(isReady(this.ctx.getLocOp(form, 0), this.ctx.getLocOp(form, 1),
			this.ctx.getLocOp(form, 2)), isSick(name), isEmpty(name));
	}

	private int patchAt(int index) {
		int[] rows = this.rows();
		return index >= 0 && index < rows.length ? rows[index] : -1;
	}

	String label(int index) {
		int patch = this.patchAt(index);
		return patch < 0 ? "" : PATCH_NAME[patch];
	}

	/** The crop and its condition: what the cache calls the form the patch is in. */
	String value(int index) {
		int patch = this.patchAt(index);
		if (patch < 0) {
			return "";
		}
		int form = this.formOf(patch);
		if (form == -1) {
			return "";
		}
		String name = this.ctx.getLocName(form);
		if (isSick(name)) {
			return "DISEASED";
		}
		if (isReady(this.ctx.getLocOp(form, 0), this.ctx.getLocOp(form, 1), this.ctx.getLocOp(form, 2))) {
			return "READY";
		}
		return name;
	}

	/**
	 * The line under a row: what it is, and the timing - but only the timing this can stand
	 * behind. An estimate says so.
	 */
	String detail(int index) {
		int patch = this.patchAt(index);
		if (patch < 0) {
			return "";
		}
		int form = this.formOf(patch);
		String what = form == -1 ? "" : this.ctx.getLocName(form);
		long minute = now();
		if (this.patches.isEstimateReady(patch)) {
			long until = this.patches.untilNext(patch, minute);
			return what + " - next ~" + FarmPatches.span(until) + " (estimated)";
		}
		long since = this.patches.sinceChange(patch, minute);
		return what + " - changed " + FarmPatches.span(since) + " ago";
	}

	/** The model, for the test: the plugin is wiring, the rules are in there. */
	FarmPatches patches() {
		return this.patches;
	}
}
