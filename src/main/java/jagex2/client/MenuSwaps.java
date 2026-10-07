package jagex2.client;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;

/**
 * Player-configured left-click swaps: "on a Guard, make Attack the left-click".
 *
 * WHY THIS IS NOT A QolSettings ENTRY. Every other QoL feature is a boolean - on or off, the same
 * for everyone. A swap is a choice about a particular thing in the world, and the whole point is
 * that each player picks their own. That needs a list, not a flag, so it gets its own store and its
 * own panel. QolSettings still owns the master on/off; this owns what the swaps are.
 *
 * WHAT A SWAP IS. A menu entry reads "<verb> @tag@<target>" - "Attack @yel@Guard@gr2@ (level-21)".
 * The colour tag both separates the two halves and says what kind of thing the target is, so a swap
 * is a (kind, target, verb) triple. Target "*" means any target of that kind, which is how you say
 * "always prefer Bury" rather than "prefer Bury on these particular bones".
 *
 * HOW A SWAP IS MADE. Hold Shift and right-click, the way RuneLite does it: the menu that opens
 * lists the same options, each offering to become the left-click. The client already knows the
 * verb, the kind and the target at that moment, so the player never types a name - and because
 * the thing being configured is the thing under the cursor, there is no mode to arm and no target
 * to go and find afterwards.
 *
 * FILE FORMAT. "kind|target|verb" lines in the client's cache directory, one per swap, with a
 * version line - same reasoning as QolSettings: text so the file survives the feature changing
 * shape, and anything unparseable is skipped rather than fatal.
 *
 * NOTHING HERE MAY THROW. A missing, unreadable or corrupt swaps file leaves the client running
 * with no swaps, exactly as if the player had never made one.
 */
public final class MenuSwaps {

	private static final int FILE_VERSION = 1;
	private static final String FILE_NAME = "qol_swaps.dat";

	/**
	 * Hard cap on stored swaps. It used to be 16, sized so that the panel fit the 512x334 viewport
	 * without paging; the panel scrolls now (Client.drawSwapPanel), so the screen no longer decides
	 * how many a player may keep and this is only here to bound the arrays.
	 *
	 * 128 is a number a player will not reach: a swap is one rule about one kind of thing, and the
	 * whole of 377's world - every npc worth a swap, every altar, every set of bones - does not add
	 * up to that many decisions. A cap stays, rather than a growing list, for the two reasons this
	 * class already lives by: kind/target/verb are plain arrays, and load() reads whatever is in the
	 * file, so a corrupt or hand-written qol_swaps.dat must not be able to make the client allocate
	 * without limit. Raising it costs three String slots each and nothing else.
	 *
	 * The file format did not change with it. load() stops at MAX and skips anything unparseable, so
	 * a file written when this was 16 loads exactly as it did, and one written now with more than 16
	 * lines is read by an older client as its first 16 - which is why FILE_VERSION stays at 1.
	 */
	public static final int MAX = 128;

	/** Target value meaning "any target of this kind". */
	public static final String ANY = "*";

	/**
	 * The verb that means "do not interact with this at all - just walk". Stored against a target
	 * like any other verb, but it promotes the menu's "Walk here" entry rather than an entry named
	 * after it, because that entry carries no target tag of its own. Matching the client's own
	 * wording exactly is deliberate: when a player IS standing on the tile the client produces a
	 * real "Walk here @whi@<name>" option, and the two must be the same rule, not two that disagree.
	 */
	public static final String WALK = "Walk here";

	// Colour tags RS2 puts in front of a menu target, which is also the only thing in the string
	// that says what kind of thing it is. Ground objs and inventory items share @lre@ - deliberately
	// not separated, because the verbs differ anyway ("Take" is never an inventory option).
	private static final String[] KINDS = { "yel", "cya", "lre", "whi", "gre" };
	private static final String[] KIND_LABELS = { "npc", "scenery", "item", "player", "interface" };

	private static final String[] kind = new String[MAX];
	private static final String[] target = new String[MAX];
	private static final String[] verb = new String[MAX];
	private static int count;
	private static boolean loaded;

	private MenuSwaps() {
	}

	public static int count() {
		ensure();
		return count;
	}

	public static boolean full() {
		return count() >= MAX;
	}

	public static String verb(int i) {
		return i >= 0 && i < count() ? verb[i] : "";
	}

	public static String target(int i) {
		return i >= 0 && i < count() ? target[i] : "";
	}

	/** The raw colour tag a rule is stored against, which remove() matches on. */
	public static String kindTag(int i) {
		return i >= 0 && i < count() ? kind[i] : "";
	}

	/** Human-readable kind, for the panel. Falls back to the raw tag if it is one we do not name. */
	public static String kindLabel(int i) {
		if (i < 0 || i >= count()) {
			return "";
		}
		for (int k = 0; k < KINDS.length; k++) {
			if (KINDS[k].equals(kind[i])) {
				return KIND_LABELS[k];
			}
		}
		return kind[i];
	}

	public static boolean isAny(int i) {
		return i >= 0 && i < count() && ANY.equals(target[i]);
	}

	/**
	 * Index of the tag that opens a menu option's target, or -1 if the option has no target at all
	 * ("Cancel", "Walk here", most interface buttons). A tag is exactly "@xxx@".
	 */
	public static int tagAt(String option) {
		if (option == null) {
			return -1;
		}
		// i + 5 < length, not i + 4: a tag with nothing after it marks no target at all, and
		// reporting one would hand every caller an empty target to check for separately.
		for (int i = 0; i + 5 < option.length(); i++) {
			if (option.charAt(i) == '@' && option.charAt(i + 4) == '@') {
				return i;
			}
		}
		return -1;
	}

	public static String parseVerb(String option, int at) {
		return option.substring(0, at).trim();
	}

	public static String parseKind(String option, int at) {
		return option.substring(at + 1, at + 4);
	}

	/**
	 * The target name, with colour tags stripped and the combat/skill level suffix removed.
	 *
	 * The suffix has to go: addNpcOptions appends "@gr2@ (level-2)" to the name, so keying on the
	 * raw text would make a swap on a level-2 Goblin miss a level-5 one - the same monster as far as
	 * the player is concerned, and they would have no way to tell why their swap stopped working.
	 */
	public static String parseTarget(String option, int at) {
		String t = DevLog.stripTags(option.substring(at + 5)).trim();
		int cut = t.indexOf(" (level-");
		if (cut < 0) {
			cut = t.indexOf(" (skill-");
		}
		if (cut > 0) {
			t = t.substring(0, cut).trim();
		}
		return t;
	}

	/**
	 * The stored swap that applies to this menu entry, or -1. An exact target beats a wildcard, so
	 * "Attack on Guard" still wins where the player also has a catch-all for npcs.
	 */
	public static int match(String optKind, String optTarget, String optVerb) {
		ensure();
		if (optKind == null || optTarget == null || optVerb == null) {
			return -1;
		}
		int wild = -1;
		for (int i = 0; i < count; i++) {
			if (!kind[i].equals(optKind) || !verb[i].equalsIgnoreCase(optVerb)) {
				continue;
			}
			if (ANY.equals(target[i])) {
				if (wild == -1) {
					wild = i;
				}
			} else if (target[i].equalsIgnoreCase(optTarget)) {
				return i;
			}
		}
		return wild;
	}

	/** Index of the swap stored for exactly this kind+target (not a wildcard), or -1. */
	public static int exact(String k, String t) {
		ensure();
		if (k == null || t == null) {
			return -1;
		}
		for (int i = 0; i < count; i++) {
			if (kind[i].equals(k) && !ANY.equals(target[i]) && target[i].equalsIgnoreCase(t)) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * Drop the swap for exactly this kind+target. A wildcard covering it is left alone: it was set
	 * deliberately and somewhere else, so silently removing it here would be a surprise.
	 */
	public static boolean remove(String k, String t) {
		int i = exact(k, t);
		if (i < 0) {
			return false;
		}
		for (int j = i; j < count - 1; j++) {
			kind[j] = kind[j + 1];
			target[j] = target[j + 1];
			verb[j] = verb[j + 1];
		}
		count--;
		save();
		return true;
	}

	/**
	 * Record a swap. Replaces an existing swap for the same kind+target rather than stacking a
	 * second one, so picking a different option on the same thing changes your mind instead of
	 * leaving two rules that disagree. Returns false only when the list is full.
	 */
	public static boolean add(String k, String t, String v) {
		ensure();
		if (k == null || t == null || v == null || v.length() == 0) {
			return false;
		}
		for (int i = 0; i < count; i++) {
			if (kind[i].equals(k) && target[i].equalsIgnoreCase(t)) {
				verb[i] = v;
				save();
				return true;
			}
		}
		if (count >= MAX) {
			return false;
		}
		kind[count] = k;
		target[count] = t;
		verb[count] = v;
		count++;
		save();
		return true;
	}

	/** Panel click: a swap goes specific -> any target of its kind -> gone. */
	public static void cycle(int i) {
		ensure();
		if (i < 0 || i >= count) {
			return;
		}
		if (!ANY.equals(target[i])) {
			target[i] = ANY;
		} else {
			for (int j = i; j < count - 1; j++) {
				kind[j] = kind[j + 1];
				target[j] = target[j + 1];
				verb[j] = verb[j + 1];
			}
			count--;
		}
		save();
	}

	public static void clear() {
		ensure();
		count = 0;
		save();
	}

	private static void ensure() {
		if (!loaded) {
			load();
		}
	}

	public static void load() {
		// Set before the read, as in QolSettings: if anything below throws, the client is left with
		// an empty list and load() is not re-entered on every call.
		count = 0;
		loaded = true;

		BufferedReader reader = null;
		try {
			File file = new File(sign.signlink.findcachedir() + FILE_NAME);
			if (!file.exists()) {
				return;
			}
			reader = new BufferedReader(new FileReader(file));
			String line;
			while ((line = reader.readLine()) != null && count < MAX) {
				int a = line.indexOf('|');
				if (a <= 0) {
					continue;
				}
				int b = line.indexOf('|', a + 1);
				if (b <= a + 1 || b + 1 >= line.length()) {
					continue;
				}
				kind[count] = line.substring(0, a).trim();
				target[count] = line.substring(a + 1, b).trim();
				verb[count] = line.substring(b + 1).trim();
				if (kind[count].length() > 0 && target[count].length() > 0 && verb[count].length() > 0) {
					count++;
				}
			}
		} catch (Exception ex) {
			// Unreadable or corrupt: keep whatever parsed cleanly, drop the rest.
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
			for (int i = 0; i < count; i++) {
				writer.println(kind[i] + "|" + target[i] + "|" + verb[i]);
			}
		} catch (Exception ex) {
			// Read-only cache dir or a full disk: the swaps still apply this session, they just will
			// not survive a restart. Not worth interrupting play over.
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
