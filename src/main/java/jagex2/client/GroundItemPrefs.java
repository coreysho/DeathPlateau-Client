package jagex2.client;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;

/**
 * Per-player settings for the ground item labels: how far to look, what a pile has to be worth to
 * be worth a label, and the short list of items to always hide or always highlight.
 *
 * WHY THIS IS NOT A QolSettings ENTRY. Same reason as MenuSwaps: QolSettings stores booleans, and
 * these are values and a list. QolSettings.GROUND_ITEMS stays the master on/off; this owns the rest.
 *
 * ONE LIST, NOT TWO. Hidden and highlighted live in a single list with a mode per entry, because an
 * item is never both and two lists would need twice the panel rows for the same information. The
 * panel cycles an entry hidden -> highlighted -> gone, the same idiom the swaps panel uses.
 *
 * NO TEXT ENTRY ANYWHERE. Names are captured by shift + right-clicking the item on the ground, the
 * way a swap is. Building a text field into a 377 panel to have the player retype a name the client
 * is already holding would be work spent making the feature worse.
 *
 * FILE FORMAT. "key=value" lines in the client's own cache directory, hide/show lines repeated -
 * same reasoning as QolSettings: text, so entries can be added or dropped without invalidating an
 * existing file, and an unparseable line is skipped rather than fatal.
 *
 * NOTHING HERE MAY THROW. A missing, unreadable or corrupt file leaves the client on defaults.
 */
public final class GroundItemPrefs {

	private static final int FILE_VERSION = 1;
	private static final String FILE_NAME = "qol_grounditems.dat";

	/**
	 * Entries in the hide/highlight list.
	 *
	 * THE OLD CAP WAS 14, "so the panel fits the 512x334 viewport with no paging". That viewport
	 * was the in-game F11 panel these rules used to be edited in. They live in the plugin sidebar
	 * now, which is a Swing panel inside a JScrollPane - it pages by scrolling, and has for a
	 * while, so 14 was costing players thirteen rules for a reason that had stopped being true.
	 *
	 * Still capped, because this is a fixed-size array read in a loop per item per frame and a
	 * rule list nobody meant to type is better refused than silently slowing the client down.
	 * 128 is past anything a person will sit and enter by hand.
	 */
	public static final int MAX = 128;

	public static final int HIDE = 0;
	public static final int HIGHLIGHT = 1;

	/**
	 * Values the old in-game panel cycled through, because it had no text entry.
	 *
	 * NOTHING DRIVES THESE ANY MORE. Radius, minimum value and show-hidden are plugin settings
	 * now, typed rather than cycled - see GroundItemsPlugin's three initialisers, which read the
	 * accessors below once so a player who set them in the old panel keeps what they chose. The
	 * file is still written with them so that migration survives a rollback to an older client
	 * and forward again.
	 */
	private static final int[] RADIUS_STEPS = { 4, 6, 8, 10, 12, 16, 20 };
	private static final int[] VALUE_STEPS = { 0, 100, 1000, 5000, 10000, 100000, 1000000 };

	private static final int DEFAULT_RADIUS = 12;      // what the feature shipped with
	private static final int DEFAULT_MIN_VALUE = 0;    // label everything, as before

	private static final String[] names = new String[MAX];
	private static final int[] modes = new int[MAX];
	private static int count;
	private static int radius = DEFAULT_RADIUS;
	private static int minValue = DEFAULT_MIN_VALUE;
	private static boolean loaded;

	/**
	 * Runtime only, never saved: while this is on, hidden items are drawn anyway (in their hidden
	 * colour) so the player can see what they have hidden without unpicking the list. A peek is not
	 * a preference - carrying it across a restart would leave someone wondering why hiding stopped
	 * working.
	 */
	private static boolean showHidden;

	private GroundItemPrefs() {
	}

	public static int count() {
		ensure();
		return count;
	}

	public static boolean full() {
		return count() >= MAX;
	}

	public static String name(int i) {
		return i >= 0 && i < count() ? names[i] : "";
	}

	public static int mode(int i) {
		return i >= 0 && i < count() ? modes[i] : HIDE;
	}

	public static int radius() {
		ensure();
		return radius;
	}

	public static int minValue() {
		ensure();
		return minValue;
	}

	public static boolean showHidden() {
		return showHidden;
	}

	public static void toggleShowHidden() {
		showHidden = !showHidden;
	}

	public static void cycleRadius() {
		ensure();
		radius = next(RADIUS_STEPS, radius);
		save();
	}

	public static void cycleMinValue() {
		ensure();
		minValue = next(VALUE_STEPS, minValue);
		save();
	}

	private static int next(int[] steps, int now) {
		for (int i = 0; i < steps.length; i++) {
			if (steps[i] == now) {
				return steps[(i + 1) % steps.length];
			}
		}
		return steps[0];       // a hand-edited value that is not a step: snap back to the first
	}

	/** Index of the entry covering this item name, or -1. */
	public static int find(String item) {
		ensure();
		if (item == null) {
			return -1;
		}
		for (int i = 0; i < count; i++) {
			if (matches(names[i], item)) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * Names are matched whole and case-insensitively. A trailing "*" matches by prefix - there is no
	 * way to type one in game, so it exists purely as an escape hatch for anyone who wants to edit
	 * the file by hand ("Iron *"), and costs two lines to honour.
	 */
	private static boolean matches(String rule, String item) {
		if (rule.endsWith("*")) {
			String head = rule.substring(0, rule.length() - 1);
			return item.length() >= head.length()
				&& item.substring(0, head.length()).equalsIgnoreCase(head);
		}
		return rule.equalsIgnoreCase(item);
	}

	public static boolean isHidden(String item) {
		int i = find(item);
		return i >= 0 && modes[i] == HIDE;
	}

	public static boolean isHighlighted(String item) {
		int i = find(item);
		return i >= 0 && modes[i] == HIGHLIGHT;
	}

	/**
	 * Put this item in the list under the given mode, or take it out again if it is already there
	 * under that mode. Returns false only when the list is full.
	 */
	public static boolean toggle(String item, int mode) {
		ensure();
		if (item == null || item.length() == 0) {
			return false;
		}
		int at = find(item);
		if (at >= 0) {
			if (modes[at] == mode) {
				removeAt(at);
			} else {
				modes[at] = mode;       // hidden <-> highlighted, rather than a second contradictory rule
				save();
			}
			return true;
		}
		if (count >= MAX) {
			return false;
		}
		names[count] = item;
		modes[count] = mode;
		count++;
		save();
		return true;
	}

	/** Put this item under this mode, whatever it was before. The minus control on a label. */
	public static boolean set(String item, int mode) {
		ensure();
		if (item == null || item.length() == 0) {
			return false;
		}
		int at = find(item);
		if (at >= 0) {
			modes[at] = mode;
			save();
			return true;
		}
		if (count >= MAX) {
			return false;
		}
		names[count] = item;
		modes[count] = mode;
		count++;
		save();
		return true;
	}

	/** Back to normal: no rule at all. The plus control on a label. Returns false if there was none. */
	public static boolean removeName(String item) {
		int at = find(item);
		if (at < 0) {
			return false;
		}
		removeAt(at);
		return true;
	}

	/** Panel click: hidden -> highlighted -> gone. */
	public static void cycle(int i) {
		ensure();
		if (i < 0 || i >= count) {
			return;
		}
		if (modes[i] == HIDE) {
			modes[i] = HIGHLIGHT;
			save();
		} else {
			removeAt(i);
		}
	}

	public static void clear() {
		ensure();
		count = 0;
		save();
	}

	private static void removeAt(int i) {
		for (int j = i; j < count - 1; j++) {
			names[j] = names[j + 1];
			modes[j] = modes[j + 1];
		}
		count--;
		save();
	}

	private static void ensure() {
		if (!loaded) {
			load();
		}
	}

	public static void load() {
		// Defaults first and loaded set immediately, as in QolSettings: a throw mid-read leaves a
		// working client rather than half-applied settings and a re-entrant load on every call.
		count = 0;
		radius = DEFAULT_RADIUS;
		minValue = DEFAULT_MIN_VALUE;
		loaded = true;

		BufferedReader reader = null;
		try {
			File file = new File(sign.signlink.findcachedir() + FILE_NAME);
			if (!file.exists()) {
				return;
			}
			reader = new BufferedReader(new FileReader(file));
			String line;
			while ((line = reader.readLine()) != null) {
				int split = line.indexOf('=');
				if (split <= 0 || split + 1 >= line.length()) {
					continue;
				}
				String key = line.substring(0, split).trim();
				String value = line.substring(split + 1).trim();
				if (value.length() == 0) {
					continue;
				}
				if (key.equals("radius")) {
					radius = parse(value, DEFAULT_RADIUS);
				} else if (key.equals("minvalue")) {
					minValue = parse(value, DEFAULT_MIN_VALUE);
				} else if ((key.equals("hide") || key.equals("show")) && count < MAX) {
					names[count] = value;
					modes[count] = key.equals("hide") ? HIDE : HIGHLIGHT;
					count++;
				}
			}
		} catch (Exception ex) {
			// Unreadable or corrupt: keep whatever parsed cleanly, defaults for the rest.
		} finally {
			try {
				if (reader != null) {
					reader.close();
				}
			} catch (Exception ignored) {
			}
		}
	}

	private static int parse(String value, int fallback) {
		try {
			int v = Integer.parseInt(value);
			return v < 0 ? fallback : v;
		} catch (Exception ex) {
			return fallback;
		}
	}

	public static void save() {
		PrintWriter writer = null;
		try {
			writer = new PrintWriter(new FileWriter(sign.signlink.findcachedir() + FILE_NAME));
			writer.println("version=" + FILE_VERSION);
			writer.println("radius=" + radius);
			writer.println("minvalue=" + minValue);
			for (int i = 0; i < count; i++) {
				writer.println((modes[i] == HIDE ? "hide=" : "show=") + names[i]);
			}
		} catch (Exception ex) {
			// Read-only cache dir or a full disk: the settings still apply this session, they just
			// will not survive a restart. Not worth interrupting play over.
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
