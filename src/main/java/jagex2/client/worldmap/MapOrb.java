/*
 * The button beside the minimap that opens the world map.
 *
 * WHERE IT GOES, AND WHY THERE. Old School hangs its orbs round the minimap, and the world map's
 * is the one at the bottom right. 377's minimap panel is 172 by 156 with the round map drawn at
 * (25, 5) and 146 across, so its centre is (98, 80) and its edge is 73 out - which leaves the
 * bottom-right corner of the panel as stone, and that is where this sits. Far enough out that it
 * touches the map's rim without covering any of it.
 *
 * INSIDE THE MINIMAP PANEL RATHER THAN THE STONE BESIDE IT. The 48 pixels of frame to the right
 * of the minimap look like the obvious place, and in the fixed layout they are - but that strip
 * is drawn only by the fixed frame, and resizable does not have it. The minimap panel is drawn
 * by every layout, so an orb inside it is an orb that is always there, and the click lands
 * through the same offsets the compass click already uses, which is the client's own answer to
 * where the minimap is in a layout that moved it.
 *
 * DRAWN RATHER THAN IMPORTED. 2004's media has no orb art - orbs are a later idea - and
 * RuneLite's is not ours to take. So it is a circle, a bevel and a folded map, out of the
 * client's own 2D primitives, in the stone and parchment the rest of the frame is made of.
 */
package jagex2.client.worldmap;

import jagex2.graphics.Pix2D;

public final class MapOrb {

	/** The minimap panel, which is what every measurement here is inside. */
	public static final int PANEL_WIDE = 172;

	public static final int PANEL_HIGH = 156;

	/** The round map inside that panel: where it starts, and how far across it is. */
	public static final int MAP_LEFT = 25;

	public static final int MAP_TOP = 5;

	public static final int MAP_WIDE = 146;

	public static final int MAP_HIGH = 151;

	/** The orb's middle, in the panel's own pixels, and how far out it goes. */
	public static final int CENTRE_X = 158;

	public static final int CENTRE_Y = 142;

	public static final int RADIUS = 12;

	private static final int RIM_DARK = 0x1C1711;
	private static final int RIM_LIGHT = 0x6E6354;
	private static final int STONE = 0x3E3529;
	private static final int STONE_LIT = 0x584C3C;
	private static final int PARCHMENT = 0xC8B48C;
	private static final int INK = 0x6B5A3E;
	private static final int LAND_GREEN = 0x4E6B35;

	private MapOrb() {
	}

	/**
	 * Whether a click in the minimap panel landed on the orb.
	 *
	 * ROUND, NOT SQUARE, because the walk-here click under it is square: the minimap's own hit
	 * test is the rectangle (25, 5) to (171, 156), corners and all, so every pixel this takes
	 * is a pixel the player can no longer walk to. A circle takes back the corners.
	 */
	public static boolean contains(int localX, int localY) {
		int dx = localX - CENTRE_X;
		int dy = localY - CENTRE_Y;
		return dx * dx + dy * dy <= RADIUS * RADIUS;
	}

	/**
	 * How far the orb reaches into the round map, which should be nothing.
	 *
	 * The map is what a player is looking at; the orb is a button. Negative means they do not
	 * touch at all, zero means they are rim to rim, positive means the button is sitting on
	 * the map.
	 */
	public static int overlapsMap() {
		int dx = CENTRE_X - (MAP_LEFT + MAP_WIDE / 2);
		int dy = CENTRE_Y - (MAP_TOP + MAP_HIGH / 2);
		int apart = (int) Math.sqrt(dx * dx + dy * dy);
		return MAP_WIDE / 2 + RADIUS - apart;
	}

	/** Whether the whole orb is inside the panel it is drawn into. */
	public static boolean insidePanel() {
		return CENTRE_X - RADIUS >= 0 && CENTRE_Y - RADIUS >= 0
			&& CENTRE_X + RADIUS < PANEL_WIDE && CENTRE_Y + RADIUS < PANEL_HIGH;
	}

	/** Draws it into whatever Pix2D is bound, which is the minimap panel. */
	public static void draw(boolean hover) {
		disc(RADIUS, RIM_DARK);
		disc(RADIUS - 1, hover ? STONE_LIT : STONE);
		// A bevel: one lit pixel along the top-left of the rim, so it reads as raised rather
		// than as a hole punched in the frame.
		for (int i = 0; i < RADIUS; i++) {
			int dy = -RADIUS + 1 + i;
			int half = (int) Math.sqrt((RADIUS - 1) * (RADIUS - 1) - dy * dy);
			Pix2D.hline(CENTRE_X - half, RIM_LIGHT, CENTRE_Y + dy, 2);
		}
		glyph(hover);
	}

	/** A filled circle, which Pix2D has no primitive for - a row at a time. */
	private static void disc(int radius, int colour) {
		for (int dy = -radius; dy <= radius; dy++) {
			int half = (int) Math.sqrt(radius * radius - dy * dy);
			Pix2D.hline(CENTRE_X - half, colour, CENTRE_Y + dy, half * 2 + 1);
		}
	}

	/**
	 * A map on the face of it: a scrap of parchment with a coastline on it.
	 *
	 * ELEVEN PIXELS ACROSS IS NOT MUCH TO SAY "MAP" WITH. The first try was a folded map - a
	 * rectangle with two creases - and at this size the creases and the border read as three
	 * vertical bars and nothing else. A shape with a coast in it is what a map looks like even
	 * when it is this small, so the land is spelled out a pixel at a time below.
	 */
	private static final String[] LAND = {
		"...........",
		"..###......",
		".#####..#..",
		".#########.",
		"..#####.#..",
		"...###.....",
		"..#####....",
		"...###.....",
	};

	private static void glyph(boolean hover) {
		int left = CENTRE_X - LAND[0].length() / 2;
		int top = CENTRE_Y - LAND.length / 2;
		Pix2D.fillRect(LAND.length + 2, top - 1, hover ? 0xE0CCA4 : PARCHMENT,
			LAND[0].length() + 2, left - 1);
		Pix2D.drawRect(top - 1, LAND.length + 2, INK, left - 1, LAND[0].length() + 2);
		for (int y = 0; y < LAND.length; y++) {
			for (int x = 0; x < LAND[y].length(); x++) {
				if (LAND[y].charAt(x) == '#') {
					Pix2D.hline(left + x, LAND_GREEN, top + y, 1);
				}
			}
		}
	}
}
