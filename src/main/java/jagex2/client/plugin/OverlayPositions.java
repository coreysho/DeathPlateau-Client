package jagex2.client.plugin;

/**
 * Where the player has dragged each overlay to, and the rules about where one may go.
 *
 * Offsets, not positions. An overlay works out where it wants to be every frame - a corner, a
 * margin, a column over a tile - and in the resizable display modes those answers change as the
 * window does. Storing "120, 46" would pin an overlay to a corner that moved; storing "forty
 * pixels left of wherever you were going to be" survives it.
 *
 * Saved in plugins.dat beside the plugin settings, under "overlay.&lt;key&gt;.at", so a player's
 * layout comes back with the rest of their preferences and a plugin that is turned off does not
 * forget where its overlay was.
 */
final class OverlayPositions {

	/**
	 * How close to its home an overlay has to be dropped to snap back to it, in pixels.
	 *
	 * This is what makes a dragged overlay resettable by hand: nudge it back towards where it
	 * started and it clicks into place at exactly zero, rather than at the two pixels off that a
	 * mouse will actually give you.
	 */
	static final int SNAP = 8;

	/**
	 * How much of an overlay must stay on screen, in pixels.
	 *
	 * An overlay dragged entirely off the edge is gone - there is nothing left to grab to bring
	 * it back, and nothing on screen to explain where it went. So a strip of it always stays.
	 */
	static final int KEEP_VISIBLE = 12;

	private final PluginStore store;

	OverlayPositions(PluginStore store) {
		this.store = store;
	}

	private static String key(String overlayKey) {
		return "overlay." + overlayKey + ".at";
	}

	/** The saved x offset for an overlay, or 0 for one nobody has moved. */
	int x(String overlayKey) {
		return this.read(overlayKey, 0);
	}

	int y(String overlayKey) {
		return this.read(overlayKey, 1);
	}

	/**
	 * One half of a saved "x,y", or 0 for anything that does not parse.
	 *
	 * Deliberately forgiving: this file is plain text a player can edit, and a line they have
	 * mangled should put an overlay back where it started rather than stop the client drawing it.
	 */
	private int read(String overlayKey, int half) {
		if (overlayKey == null) {
			return 0;
		}
		String value = this.store.get(key(overlayKey));
		if (value == null) {
			return 0;
		}
		int comma = value.indexOf(',');
		if (comma < 0) {
			return 0;
		}
		try {
			return Integer.parseInt((half == 0
				? value.substring(0, comma)
				: value.substring(comma + 1)).trim());
		} catch (RuntimeException notANumber) {
			return 0;
		}
	}

	/**
	 * Remembers where an overlay is, without writing the file.
	 *
	 * This is what a drag in progress calls, once a frame. Saving here instead would be a write
	 * to plugins.dat fifty times a second to record a position the player has not settled on.
	 */
	void move(String overlayKey, int x, int y) {
		if (overlayKey != null) {
			this.store.put(key(overlayKey), x + "," + y);
		}
	}

	/** The same, and writes it out: what dropping an overlay calls. */
	void set(String overlayKey, int x, int y) {
		if (overlayKey != null) {
			this.move(overlayKey, x, y);
			this.store.save();
		}
	}

	/** Puts one overlay back where its plugin draws it. */
	void clear(String overlayKey) {
		this.set(overlayKey, 0, 0);
	}

	/**
	 * Snaps an offset that is nearly home to exactly home.
	 *
	 * Only towards zero, and only on each axis separately: an overlay nudged back to its original
	 * column but left lower down should keep the column and keep the drop.
	 */
	static int snap(int offset) {
		return offset > -SNAP && offset < SNAP ? 0 : offset;
	}

	/**
	 * Keeps an offset from putting an overlay somewhere there is nothing left to grab.
	 *
	 * Works from where the overlay actually IS - its box this frame - rather than from the offset
	 * alone, because an overlay that draws itself in the right-hand corner is already near an
	 * edge at offset zero and must not be treated as though it had been dragged there.
	 */
	static int clamp(int offset, int edgeLow, int edgeHigh, int available) {
		int low = offset + edgeLow;
		int high = offset + edgeHigh;
		if (high < KEEP_VISIBLE) {
			return offset + (KEEP_VISIBLE - high);
		}
		if (low > available - KEEP_VISIBLE) {
			return offset - (low - (available - KEEP_VISIBLE));
		}
		return offset;
	}
}
