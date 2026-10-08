package jagex2.client.plugin.builtin;

import jagex2.client.plugin.PluginConfig;

/**
 * Which colour a ground item's label is drawn in, as a value a player can change.
 *
 * These were six constants and four thresholds compiled into the plugin. They are the same six
 * and four, now read from settings, because what counts as "worth seeing" is not a thing a client
 * can know: a thousand coins is a fortune on a fresh account and rounding error on an old one.
 *
 * A VALUE OBJECT BUILT ONCE A FRAME, not read per item. colourFor runs per item per tile per
 * frame, and parsing six hex strings inside that loop would be parsing them a few hundred times a
 * frame to get the same six answers.
 *
 * THRESHOLDS ARE SORTED DESCENDING HERE, carrying their colours with them, because the tier walk
 * takes the first threshold the item's worth clears and a player typing them out of order would
 * otherwise get the wrong tier silently - a 10k item coming out in the 1k colour because 1k was
 * listed first. Sorting is the difference between "the settings are in a funny order" and "the
 * colours are wrong".
 */
public final class GroundItemPalette {

	/** How many value tiers there are. Four, as the plugin shipped with. */
	public static final int TIERS = 4;

	/** What the plugin drew before any of this was configurable. */
	public static final GroundItemPalette DEFAULTS = new GroundItemPalette(
		0xFFFFFF, 0xFF40FF, 0x707070,
		new int[] { 1000000, 100000, 10000, 1000 },
		new int[] { 0xFF9040, 0x40C0FF, 0x40FF40, 0xFFFF80 });

	/** An ordinary item, one the player highlighted, and one hidden and revealed under Alt. */
	public final int plain;
	public final int highlighted;
	public final int hidden;

	private final int[] thresholds;
	private final int[] colours;

	public GroundItemPalette(int plain, int highlighted, int hidden, int[] thresholds,
			int[] colours) {
		this.plain = plain;
		this.highlighted = highlighted;
		this.hidden = hidden;
		this.thresholds = new int[TIERS];
		this.colours = new int[TIERS];
		for (int i = 0; i < TIERS; i++) {
			this.thresholds[i] = thresholds != null && i < thresholds.length ? thresholds[i] : 0;
			this.colours[i] = colours != null && i < colours.length ? colours[i] : plain;
		}
		sortDescending(this.thresholds, this.colours);
	}

	/**
	 * The colour for something worth this much, or {@link #plain} when it clears no tier.
	 *
	 * A threshold of 0 or less is a tier the player turned off: every item is worth at least
	 * nothing, so a 0 that still counted would paint the whole floor in that tier's colour.
	 */
	public int forWorth(long worth) {
		for (int tier = 0; tier < TIERS; tier++) {
			if (this.thresholds[tier] > 0 && worth >= (long) this.thresholds[tier]) {
				return this.colours[tier];
			}
		}
		return this.plain;
	}

	/** This tier's threshold, after sorting. For tests and for the panel's own labels. */
	public int threshold(int tier) {
		return tier >= 0 && tier < TIERS ? this.thresholds[tier] : 0;
	}

	/** This tier's colour, after sorting. */
	public int colour(int tier) {
		return tier >= 0 && tier < TIERS ? this.colours[tier] : this.plain;
	}

	/**
	 * Built from what the player typed: hex for the colours, plain numbers for the thresholds.
	 *
	 * Nothing here may throw. It is called from a render, and every argument is a string out of a
	 * settings file - so a colour that is not a colour comes back yellow from
	 * PluginConfig.parseColour, and a threshold that is not a number comes back as that tier
	 * turned off rather than as an exception out of the draw.
	 */
	public static GroundItemPalette from(String plain, String highlighted, String hidden,
			int[] thresholds, String[] colours) {
		int[] parsed = new int[TIERS];
		for (int i = 0; i < TIERS; i++) {
			parsed[i] = colours != null && i < colours.length
				? PluginConfig.parseColour(colours[i]) : DEFAULTS.colour(i);
		}
		return new GroundItemPalette(PluginConfig.parseColour(plain),
			PluginConfig.parseColour(highlighted), PluginConfig.parseColour(hidden),
			thresholds, parsed);
	}

	/**
	 * Both arrays reordered together, biggest threshold first.
	 *
	 * An insertion sort over four elements, because that is the whole problem: anything cleverer
	 * would be more code than the thing it sorts, and the pairing is the part that matters - a
	 * sort that moved the thresholds and left the colours behind would be worse than no sort.
	 */
	private static void sortDescending(int[] thresholds, int[] colours) {
		for (int i = 1; i < thresholds.length; i++) {
			int threshold = thresholds[i];
			int colour = colours[i];
			int at = i - 1;
			while (at >= 0 && thresholds[at] < threshold) {
				thresholds[at + 1] = thresholds[at];
				colours[at + 1] = colours[at];
				at--;
			}
			thresholds[at + 1] = threshold;
			colours[at + 1] = colour;
		}
	}
}
