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

	/**
	 * The content's own patch index for each row above, which is NOT the order they are listed
	 * in: the content numbers allotments 0-7, hops 8-11, flowers 12-15, herbs 16-19, bushes
	 * 20-23, trees 24-27 and fruit trees 28-31, while this page lists herbs second because that
	 * is the order a player cares about them in. Used to find a patch's due varp.
	 */
	static final int[] PATCH_INDEX = {
		0, 1, 2, 3, 4, 5, 6, 7,       // allotments 1-8
		16, 17, 18, 19,               // herbs 1-4
		8, 9, 10, 11,                 // hops 1-4
		12, 13, 14, 15,               // flowers 1-4
		20, 21, 22, 23,               // bushes 1-4
		24, 25, 26, 27,               // trees 1-4
		28, 29, 30, 31                // fruit trees 1-4
	};

	/**
	 * farming_due_0, and the world clock beside the set.
	 *
	 * THE SERVER NOW DOES THE ARITHMETIC. farming_due_N is the world minute a patch next
	 * advances, worked out in the content where the growth rules are, and farming_world_minute
	 * is the server's own clock to subtract it from. Explicit ids in the content's
	 * pack/varp.pack (1957 onward), which is a file of fixed assignments rather than an order
	 * things happen to be in, so they do not move the way a loc id does.
	 *
	 * AN OLDER SERVER SENDS NEITHER, and both read 0. That is not a countdown of zero: it is no
	 * answer, and the page falls back to the cadence it measures for itself. A client on a world
	 * that has not taken the content change still works, which is the whole reason the fallback
	 * is kept rather than deleted.
	 */
	static final int DUE_VARP_FIRST = 1957;
	static final int WORLD_MINUTE_VARP = 1996;

	/** Locs, and objs, looked at per tick while the indexes are built. Small enough not to be felt. */
	static final int SCAN_PER_TICK = 400;

	/**
	 * The names to try, in order, when looking for the item that pictures a crop.
	 *
	 * THE PATCH KNOWS WHAT IT LOOKS LIKE, NOT WHAT IT GROWS. The cache calls a growing form
	 * "Potato plant", a seedling "Potato seed", a sick one "Diseased Belladonna" and a finished
	 * one "Potato" - and of those four only two are also the name of an item. So the name is
	 * tried as it stands first, because "Potato seed" and "Oak sapling" ARE items and anything
	 * clever would spoil them, and only then with the words the patch adds taken off.
	 *
	 * MATCHING AN ITEM IS THE TEST. Whichever candidate the obj table actually has is the right
	 * one, so a wrong guess costs nothing: it simply does not match and the next is tried. The
	 * page shows no picture rather than the wrong one.
	 */
	static String[] cropItemNames(String locName) {
		if (locName == null || locName.length() == 0) {
			return new String[0];
		}
		String name = locName.trim();
		java.util.List<String> out = new java.util.ArrayList<String>();
		out.add(name);
		String bare = name;
		for (String prefix : new String[] { "Diseased ", "Dead " }) {
			if (bare.length() > prefix.length() && bare.regionMatches(true, 0, prefix, 0, prefix.length())) {
				bare = bare.substring(prefix.length());
				// The cache lower-cases what follows: "Dead potato plant".
				bare = Character.toUpperCase(bare.charAt(0)) + bare.substring(1);
			}
		}
		for (String suffix : new String[] { " plant", " bush", " tree", " plants" }) {
			if (bare.length() > suffix.length() && bare.toLowerCase().endsWith(suffix)) {
				bare = bare.substring(0, bare.length() - suffix.length());
			}
		}
		if (!out.contains(bare)) {
			out.add(bare);
		}
		return out.toArray(new String[0]);
	}

	/**
	 * How far through its current stage a patch is, as a percentage, or -1 when that is not
	 * knowable.
	 *
	 * BETWEEN TWO THINGS THE PAGE ALREADY HAS: the minute the patch last changed, which this
	 * watched happen, and the minute it next advances, which the server sends. The bar is the
	 * distance travelled between them - not overall growth, which would need the stage count,
	 * and not a guess, which would be a moving bar that means nothing.
	 *
	 * -1 rather than 0 for "cannot say", because a bar at 0 looks like a patch that has just
	 * started rather than one nobody can measure.
	 */
	static int growthPercent(long changedAt, long due, long now) {
		if (due <= changedAt || now < changedAt) {
			return -1;
		}
		long span = due - changedAt;
		long gone = now - changedAt;
		if (gone >= span) {
			return 100;
		}
		return (int) (gone * 100L / span);
	}

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

	/** Lower-cased obj name -> id, built the same way and for the same reason as the loc index. */
	private final java.util.Map<String, Integer> objByName = new java.util.HashMap<String, Integer>();

	private int objScanned;
	private boolean objScanDone;

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

				public int progress(int index) {
					return FarmPatchesPlugin.this.progress(index);
				}

				public jagex2.client.plugin.Sprite icon(int index) {
					return FarmPatchesPlugin.this.icon(index);
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
		this.scanObjs();
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

	/**
	 * Works through the obj table a slice at a time, remembering what each item is called.
	 *
	 * Same bargain as the loc scan above: a page that wants the picture of a watermelon knows
	 * the cache calls it "Watermelon" and not what number that is, and a number written in here
	 * is one that moves when the content is repacked. Spread over ticks because ObjType hands
	 * its entries out of a ten-entry round-robin cache, so reading them all decodes them all.
	 */
	private void scanObjs() {
		if (this.objScanDone) {
			return;
		}
		int count = this.ctx.getObjCount();
		if (count <= 0) {
			return;
		}
		int end = this.objScanned + SCAN_PER_TICK;
		if (end > count) {
			end = count;
		}
		for (int obj = this.objScanned; obj < end; obj++) {
			String name = this.ctx.getObjName(obj);
            if (name.length() == 0) {
				continue;
			}
			String key = name.toLowerCase();
			// FIRST ONE WINS. The cache repeats a name across noted and placeholder forms, and
			// the lowest id is the real item - the one whose picture a player recognises.
			if (!this.objByName.containsKey(key)) {
				this.objByName.put(key, Integer.valueOf(obj));
			}
		}
		this.objScanned = end;
		this.objScanDone = this.objScanned >= count;
	}

	/** The item that pictures what is in a patch, or -1 while the index is still being built. */
	int cropObj(String locName) {
		String[] candidates = cropItemNames(locName);
		for (int i = 0; i < candidates.length; i++) {
			Integer id = this.objByName.get(candidates[i].toLowerCase());
			if (id != null) {
				return id.intValue();
			}
		}
		return -1;
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
	 * Minutes until this patch next advances, as the SERVER works it out, or -1 when it is not
	 * telling us.
	 *
	 * -1 covers three different silences and deliberately does not distinguish them, because
	 * the page does the same thing for all of them: an older server that has not taken the
	 * content change (both varps 0), a patch with nothing coming (due 0 - dead, bare, or
	 * finished with no produce), and a due time already in the past, which happens for the few
	 * seconds between a patch falling due and the once-a-minute tick picking it up.
	 */
	long serverMinutesLeft(int patch) {
		if (patch < 0 || patch >= PATCH_INDEX.length) {
			return -1;
		}
		return minutesLeft(this.ctx.getVarp(WORLD_MINUTE_VARP),
			this.ctx.getVarp(DUE_VARP_FIRST + PATCH_INDEX[patch]));
	}

	/**
	 * The subtraction, separate and static so it can be tested without a world.
	 *
	 * It is three lines and all three are a decision. Treating a 0 due as "due now" would put
	 * READY IN 0m under every dead plant and every empty patch on the page; treating a 0 clock
	 * as the epoch would make every countdown nine hundred thousand minutes; and letting the
	 * answer go negative would print a crop as overdue for the few seconds between it falling
	 * due and the once-a-minute tick noticing.
	 */
	static long minutesLeft(int clock, int due) {
		if (clock <= 0 || due <= 0) {
			return -1;
		}
		long left = (long) due - (long) clock;
		return left < 0 ? 0 : left;
	}

	/**
	 * The line under a row: what it is, and when it next moves.
	 *
	 * THREE ANSWERS, IN ORDER OF HOW MUCH THEY ARE WORTH. The server's own figure, which is
	 * exact and says so by not saying anything; the cadence this has measured for itself, which
	 * is marked an estimate every time; and how long ago the patch last changed, which is all
	 * there is until one of the other two is available. A page that printed the second as
	 * confidently as the first would be worth less than one that printed neither.
	 */
	String detail(int index) {
		int patch = this.patchAt(index);
		if (patch < 0) {
			return "";
		}
		int form = this.formOf(patch);
		String what = form == -1 ? "" : this.ctx.getLocName(form);
		long fromServer = this.serverMinutesLeft(patch);
		if (fromServer >= 0) {
			return what + " - next in " + FarmPatches.span(fromServer);
		}
		long minute = now();
		if (this.patches.isEstimateReady(patch)) {
			long until = this.patches.untilNext(patch, minute);
			return what + " - next ~" + FarmPatches.span(until) + " (estimated)";
		}
		long since = this.patches.sinceChange(patch, minute);
		return what + " - changed " + FarmPatches.span(since) + " ago";
	}

	/**
	 * The picture of what is in the patch: the crop's own item, as the game draws it.
	 *
	 * A SINGLE ONE, not a stack, so no number is drawn over it - the row is about the patch,
	 * not about how much of something is in it.
	 */
	jagex2.client.plugin.Sprite icon(int index) {
		int patch = this.patchAt(index);
		if (patch < 0 || this.ctx == null) {
			return null;
		}
		int form = this.formOf(patch);
		if (form == -1) {
			return null;
		}
		int obj = this.cropObj(this.ctx.getLocName(form));
		return obj < 0 ? null : this.ctx.getObjIcon(obj, 1);
	}

	/**
	 * The bar under a row: how far through its current stage the patch is.
	 *
	 * FULL WHEN THERE IS NOTHING LEFT TO WAIT FOR, and absent for a sick one. A ready patch is
	 * at the end of its road; a diseased one is not on the road at all, and a bar creeping
	 * along under a crop that is dying would say the opposite of what the row says.
	 */
	int progress(int index) {
		int patch = this.patchAt(index);
		if (patch < 0 || this.ctx == null) {
			return -1;
		}
		int form = this.formOf(patch);
		if (form != -1) {
			String name = this.ctx.getLocName(form);
			if (isSick(name)) {
				return -1;
			}
			if (isReady(this.ctx.getLocOp(form, 0), this.ctx.getLocOp(form, 1), this.ctx.getLocOp(form, 2))) {
				return 100;
			}
		}
		long left = this.serverMinutesLeft(patch);
		if (left < 0) {
			return -1;
		}
		long minute = now();
		return growthPercent(this.patches.changedAt(patch), minute + left, minute);
	}

	/** The model, for the test: the plugin is wiring, the rules are in there. */
	FarmPatches patches() {
		return this.patches;
	}
}
