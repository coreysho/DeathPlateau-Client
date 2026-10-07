package jagex2.client.plugin.builtin;

import java.util.List;

import jagex2.client.GroundItemPrefs;
import jagex2.client.plugin.ConfigList;
import jagex2.client.plugin.GroundItem;
import jagex2.client.plugin.GroundItemPile;
import jagex2.client.plugin.Overlay;
import jagex2.client.plugin.OverlayGraphics;
import jagex2.client.plugin.Plugin;
import jagex2.client.plugin.PluginDescriptor;
import jagex2.client.plugin.Subscribe;
import jagex2.client.plugin.event.SettingsMenuOpening;

/**
 * The name of every item on the ground near you, over the tile it is on.
 *
 * The last and largest of the QoL features to become a plugin, and the one that uses most of the
 * API: an overlay, the scene, clickable regions, the wheel, the settings menu and two config
 * lists. It was about 400 lines across Client.java - the overlay, the Alt controls, the pile
 * scrolling and the F11 panel.
 *
 * WHAT A ROW LOOKS LIKE is worth keeping straight, because three things decide it:
 *
 *   HIGHLIGHTED items are always drawn, whatever the value floor says. That is the point of
 *   highlighting something cheap.
 *   HIDDEN ones are drawn only while revealing - Alt held, or the reveal setting on - and then
 *   in grey. Otherwise a hidden item would have no [+] to click and could only be recovered
 *   from the settings page.
 *   EVERYTHING ELSE is drawn when it is worth at least the floor, coloured by how much.
 *
 * The rules are looked up on the BARE name, never the "Coins x 500" label: a rule set on one
 * coin has to keep applying to a pile of them.
 *
 * The rules themselves still live in GroundItemPrefs, in the file they always did, so a player's
 * hidden and highlighted items survive this untouched.
 */
@PluginDescriptor(
	name = "Ground items",
	description = "Names the items lying near you. Hold Alt for the controls.",
	key = "ground-items",
	enabledByDefault = true,
	legacySetting = "ground_items"
)
public final class GroundItemsPlugin extends Plugin {

	/** Height above the tile the column sits at, and the gap between its rows. */
	private static final int ITEM_HEIGHT = 24;
	private static final int ROW_H = 12;

	/** Rows drawn before a pile starts scrolling instead of growing. */
	private static final int ROWS_SHOWN = 8;

	/** Labels in a frame, and distinct items on one tile: both bound the work per frame. */
	private static final int MAX_LABELS = 48;
	private static final int MAX_PER_TILE = 24;

	private static final int COLOUR = 0xFFFFFF;
	private static final int HIGHLIGHT_COLOUR = 0xFF40FF;
	private static final int HIDDEN_COLOUR = 0x707070;
	private static final int[] TIERS = { 1000000, 100000, 10000, 1000 };
	private static final int[] TIER_COLOURS = { 0xFF9040, 0x40C0FF, 0x40FF40, 0xFFFF80 };

	/** The [-] and [+] under Alt, and the scroll bar beside a pile too tall to show. */
	private static final int CONTROL_W = 10;
	private static final int MINUS_COLOUR = 0xFF6060;
	private static final int PLUS_COLOUR = 0x60FF60;
	private static final int BAR_W = 2;
	private static final int BAR_GAP = 3;
	private static final int BAR_TRACK = 0x282828;
	private static final int BAR_THUMB = 0xC8C8C8;

	/**
	 * ONE scrolled pile, not a scroll position per tile. You scroll the pile under the cursor,
	 * and scrolling a different one starts that one from the top; nothing is remembered once you
	 * walk away. Same call as the reveal toggle not being persisted - this is a peek, not a
	 * preference.
	 */
	private int scrollPlane = -1;
	private int scrollTileX = -1;
	private int scrollTileZ = -1;
	private int scrollOffset;

	/** How many labels have been drawn this frame, against MAX_LABELS. */
	private int drawn;

	protected void startUp() {
		this.addOverlay(new Overlay() {

			public void render(OverlayGraphics g) {
				GroundItemsPlugin.this.render(g);
			}

			/** Under the player names and hitsplats, where these labels have always been. */
			public int layer() {
				return LAYER_SCENE;
			}
		});
		this.addConfigList("Display", this.displayList());
		this.addConfigList("Items", this.itemList());
		this.addPanel("Loot nearby", "list", this.lootList());
	}

	// ------------------------------------------------------------------ drawing

	private void render(OverlayGraphics g) {
		this.drawn = 0;
		if (!this.ctx.isLoggedIn()) {
			return;
		}
		// Holding Alt reveals hidden items as well as offering the controls.
		boolean alt = this.ctx.isAltHeld();
		boolean reveal = GroundItemPrefs.showHidden() || alt;
		long floor = GroundItemPrefs.minValue();

		List<GroundItemPile> piles = this.ctx.getGroundItemPiles(GroundItemPrefs.radius());
		for (int i = 0; i < piles.size() && this.drawn < MAX_LABELS; i++) {
			this.renderPile(g, piles.get(i), alt, reveal, floor);
		}
	}

	private void renderPile(OverlayGraphics g, GroundItemPile pile, boolean alt, boolean reveal, long floor) {
		if (!this.ctx.projectTile(pile.sceneTileX, pile.sceneTileZ, ITEM_HEIGHT)) {
			return;
		}
		int originX = this.ctx.getProjectedX();
		int originY = this.ctx.getProjectedY();
		// A generous box around the drawable area. Not for correctness - drawing clips - but so a
		// tile off to the side costs one comparison instead of a label per row. Anything at -1 or
		// less in x is behind the camera or off the left, and the client has always dropped both.
		if (originX <= -1 || originX > g.width() + 128 || originY < -64 || originY > g.height() + 66) {
			return;
		}

		// PASS ONE: which rows will appear at all, and in what colour. A hidden row is not a row,
		// so the column can only be laid out once this is known.
		List<Row> rows = visibleRows(pile, reveal, floor);
		int visible = rows.size();
		if (visible == 0) {
			return;
		}

		// The window into those rows. A pile taller than ROWS_SHOWN shows a slice of itself and
		// the wheel moves the slice; every other pile shows all of itself and ignores the offset.
		int shown = Math.min(visible, ROWS_SHOWN);
		int offset = this.offsetFor(pile, visible, shown);

		// PASS TWO. Grow the column upwards: the last row lands on the tile and earlier ones
		// stack above it, so the pile never covers the item models below it.
		g.setFont(OverlayGraphics.FONT_SMALL);
		int rowY = originY - (shown - 1) * ROW_H;
		int firstY = rowY;
		int lastY = rowY;
		int minLeft = originX;
		int maxRight = originX;

		for (int at = offset; at < offset + shown && at < visible; at++) {
			Row row = rows.get(at);
			GroundItem item = row.item;
			String label = label(item);

			if (alt) {
				int right = this.drawControls(g, item, label, originX, rowY, row.colour);
				int left = originX - (CONTROL_W * 2 + g.textWidth(label)) / 2;
				minLeft = Math.min(minLeft, left);
				maxRight = Math.max(maxRight, right);
			} else {
				g.textCentred(originX, rowY, label, row.colour);
				int half = g.textWidth(label) / 2;
				minLeft = Math.min(minLeft, originX - half);
				maxRight = Math.max(maxRight, originX + half);
			}
			lastY = rowY;
			this.drawn++;
			rowY += ROW_H;
		}

		int top = firstY - g.lineHeight();
		int bottom = lastY + 2;
		if (visible > shown) {
			minLeft = this.drawScrollBar(g, minLeft, top, bottom, visible, shown, offset);
		}
		this.claimScroll(g, pile, visible, minLeft, maxRight, top, bottom);
	}

	/** One row that will be drawn: an item and the colour its rule decided on. */
	static final class Row {

		final GroundItem item;
		final int colour;

		Row(GroundItem item, int colour) {
			this.item = item;
			this.colour = colour;
		}
	}

	/**
	 * The rows of a pile that will actually appear, in draw order.
	 *
	 * Separated from the drawing because it is the half with the rules in it - and because the
	 * one bug this feature has had was here rather than in the pixels: the column used to be
	 * positioned from the TRACKED count while skipped rows still advanced the cursor, so a
	 * hidden item left a hole where it used to be. Laying out over this list cannot do that.
	 */
	static List<Row> visibleRows(GroundItemPile pile, boolean reveal, long floor) {
		List<Row> rows = new java.util.ArrayList<Row>();
		int distinct = Math.min(pile.items.size(), MAX_PER_TILE);
		for (int i = 0; i < distinct; i++) {
			GroundItem item = pile.items.get(i);
			int colour = colourFor(item, reveal, floor);
			if (colour != 0) {
				rows.add(new Row(item, colour));
			}
		}
		return rows;
	}

	/** What a row says: the name, and the count when there is more than one. */
	static String label(GroundItem item) {
		return item.count > 1 ? item.name + " x " + formatCount(item.count) : item.name;
	}

	/** The colour a row is drawn in, or 0 for a row that is not drawn at all. */
	static int colourFor(GroundItem item, boolean reveal, long floor) {
		if (item.name.length() == 0) {
			return 0;
		}
		if (GroundItemPrefs.isHighlighted(item.name)) {
			return HIGHLIGHT_COLOUR;                        // always shown, floor ignored
		}
		if (GroundItemPrefs.isHidden(item.name)) {
			return reveal ? HIDDEN_COLOUR : 0;
		}
		long worth = item.worth();
		if (worth < floor) {
			return 0;
		}
		for (int tier = 0; tier < TIERS.length; tier++) {
			if (worth >= (long) TIERS[tier]) {
				return TIER_COLOURS[tier];
			}
		}
		return COLOUR;
	}

	/**
	 * "[-] [+] Name" with the two controls clickable, laid out from the left edge of where the
	 * centred label would have started, so the row stays centred on the tile as it grows.
	 * Returns the right edge of the name.
	 */
	private int drawControls(OverlayGraphics g, final GroundItem item, String label, int originX, int rowY,
		int colour) {
		final String name = item.name;
		int nameW = g.textWidth(label);
		int left = originX - (CONTROL_W * 2 + nameW) / 2;
		int plusX = left + CONTROL_W;
		int nameX = plusX + CONTROL_W;
		int top = rowY - g.lineHeight();
		int height = rowY + 2 - top;

		g.text(left, rowY, "-", MINUS_COLOUR);
		g.text(plusX, rowY, "+", PLUS_COLOUR);
		g.text(nameX, rowY, label, colour);

		g.clickable(left, top, CONTROL_W, height, new Runnable() {

			public void run() {
				GroundItemsPlugin.this.hide(name);
			}
		});
		g.clickable(plusX, top, CONTROL_W, height, new Runnable() {

			public void run() {
				GroundItemsPlugin.this.ctx.addChatMessage(GroundItemPrefs.removeName(name)
					? name + " is back to normal." : name + " was already normal.");
			}
		});
		g.clickable(nameX, top, nameW, height, new Runnable() {

			public void run() {
				GroundItemsPlugin.this.toggleHighlight(name);
			}
		});
		return nameX + nameW;
	}

	/** The bar beside a pile with more rows than it shows. Returns the new left edge. */
	private int drawScrollBar(OverlayGraphics g, int minLeft, int top, int bottom, int visible, int shown,
		int offset) {
		int barX = minLeft - BAR_GAP - BAR_W;
		int track = bottom - top;
		g.fill(barX, top, BAR_W, track, BAR_TRACK);
		int thumb = Math.max(2, track * shown / visible);
		int thumbY = Math.min(top + track * offset / visible, top + track - thumb);
		g.fill(barX, thumbY, BAR_W, thumb, BAR_THUMB);
		return barX;
	}

	/** Where this pile is scrolled to, re-clamped in case it shrank since the last frame. */
	private int offsetFor(GroundItemPile pile, int visible, int shown) {
		if (this.scrollPlane != this.ctx.getPlane() || this.scrollTileX != pile.sceneTileX
			|| this.scrollTileZ != pile.sceneTileZ) {
			return 0;
		}
		// Somebody taking the bottom four items must not leave the column scrolled past its end.
		return Math.max(0, Math.min(this.scrollOffset, visible - shown));
	}

	/** A pile tall enough to scroll takes the wheel over it; every other one leaves it to zoom. */
	private void claimScroll(OverlayGraphics g, final GroundItemPile pile, final int visible, int left,
		int right, int top, int bottom) {
		if (visible <= ROWS_SHOWN) {
			return;
		}
		g.scrollable(left, top, right - left, bottom - top, new OverlayGraphics.Scrolled() {

			public void onScroll(int delta) {
				if (GroundItemsPlugin.this.scrollPlane != GroundItemsPlugin.this.ctx.getPlane()
					|| GroundItemsPlugin.this.scrollTileX != pile.sceneTileX
					|| GroundItemsPlugin.this.scrollTileZ != pile.sceneTileZ) {
					GroundItemsPlugin.this.scrollPlane = GroundItemsPlugin.this.ctx.getPlane();
					GroundItemsPlugin.this.scrollTileX = pile.sceneTileX;
					GroundItemsPlugin.this.scrollTileZ = pile.sceneTileZ;
					GroundItemsPlugin.this.scrollOffset = 0;
				}
				int max = visible - ROWS_SHOWN;
				GroundItemsPlugin.this.scrollOffset =
					Math.max(0, Math.min(GroundItemsPlugin.this.scrollOffset + delta, max));
			}
		});
	}

	// ------------------------------------------------------------------ setting a rule

	@Subscribe
	public void onSettingsMenuOpening(SettingsMenuOpening event) {
		// Only for a right-click in the world: a ground item and an inventory item carry the same
		// tag and cannot be told apart by it, so without this the rows would appear over the
		// backpack too.
		if (!event.isWorldMenu()) {
			return;
		}
		for (int i = 0; i < event.getTargets().size(); i++) {
			final SettingsMenuOpening.Target target = event.getTargets().get(i);
			if (!"lre".equals(target.kind) || this.seenBefore(event, i)) {
				continue;
			}
			final String name = target.name;
			boolean hidden = GroundItemPrefs.isHidden(name);
			boolean lit = GroundItemPrefs.isHighlighted(name);
			event.addRow((hidden ? "Stop hiding @lre@" : "Hide @lre@") + name, new Runnable() {

				public void run() {
					GroundItemsPlugin.this.hide(name);
				}
			});
			event.addRow((lit ? "Stop highlighting @lre@" : "Highlight @lre@") + name, new Runnable() {

				public void run() {
					GroundItemsPlugin.this.toggleHighlight(name);
				}
			});
		}
	}

	/** Whether an earlier target in the list is the same item, so it already has its two rows. */
	private boolean seenBefore(SettingsMenuOpening event, int index) {
		SettingsMenuOpening.Target target = event.getTargets().get(index);
		for (int j = 0; j < index; j++) {
			SettingsMenuOpening.Target other = event.getTargets().get(j);
			if ("lre".equals(other.kind) && other.name.equalsIgnoreCase(target.name)) {
				return true;
			}
		}
		return false;
	}

	private void hide(String name) {
		boolean was = GroundItemPrefs.isHidden(name);
		if (was) {
			GroundItemPrefs.removeName(name);
			this.ctx.addChatMessage(name + " is no longer hidden.");
		} else if (GroundItemPrefs.set(name, GroundItemPrefs.HIDE)) {
			this.ctx.addChatMessage(name + " will be hidden on the ground.");
		} else {
			this.full();
		}
	}

	private void toggleHighlight(String name) {
		if (GroundItemPrefs.isHighlighted(name)) {
			GroundItemPrefs.removeName(name);
			this.ctx.addChatMessage(name + " is no longer highlighted.");
		} else if (GroundItemPrefs.set(name, GroundItemPrefs.HIGHLIGHT)) {
			this.ctx.addChatMessage(name + " will be highlighted on the ground.");
		} else {
			this.full();
		}
	}

	private void full() {
		this.ctx.addChatMessage("You can only have " + GroundItemPrefs.MAX
			+ " ground item rules. Remove one from the plugin's settings first.");
	}

	// ------------------------------------------------------------------ the config page

	/**
	 * The three settings, as rows that cycle rather than values that are typed.
	 *
	 * They were cycling rows in the F11 panel and they stay cycling rows here: a radius is one of
	 * seven sensible numbers, not any number, and a list row whose button shows where it is now
	 * says that better than a text box would.
	 */
	private ConfigList displayList() {
		return new ConfigList() {

			public int size() {
				return 3;
			}

			public String label(int index) {
				return index == 0 ? "Radius" : index == 1 ? "Minimum value" : "Show hidden items";
			}

			public String detail(int index) {
				return index == 0 ? "How far away items are named"
					: index == 1 ? "Items worth less are not named"
					: "Hold Alt to reveal them anyway";
			}

			public String action(int index) {
				if (index == 0) {
					return GroundItemPrefs.radius() + " tiles";
				}
				if (index == 1) {
					return floor(GroundItemPrefs.minValue());
				}
				return GroundItemPrefs.showHidden() ? "Shown" : "Hidden";
			}

			public void onAction(int index) {
				if (index == 0) {
					GroundItemPrefs.cycleRadius();
				} else if (index == 1) {
					GroundItemPrefs.cycleMinValue();
				} else {
					GroundItemPrefs.toggleShowHidden();
				}
			}

			public boolean removable(int index) {
				return false;
			}
		};
	}

	/** The named rules: what is hidden, what is highlighted. */
	private ConfigList itemList() {
		return new ConfigList() {

			public int size() {
				return GroundItemPrefs.count();
			}

			public String label(int index) {
				return GroundItemPrefs.name(index);
			}

			public String action(int index) {
				return GroundItemPrefs.mode(index) == GroundItemPrefs.HIDE ? "Hidden" : "Highlighted";
			}

			public void onAction(int index) {
				GroundItemPrefs.cycle(index);
			}

			public void onRemove(int index) {
				GroundItemPrefs.removeName(GroundItemPrefs.name(index));
			}

			public String emptyMessage() {
				return "Hold Alt in game and click an item's name to highlight it, or its [-] to hide it.";
			}
		};
	}

	// ------------------------------------------------------------------ the rail page

	/**
	 * What is on the floor around you, worth first.
	 *
	 * THE SAME THINGS THE OVERLAY NAMES, by running every item through colourFor: a hidden item
	 * is absent from both, the value floor applies to both, and a highlighted one appears in
	 * both however cheap it is. A panel that disagreed with the labels on the ground would be
	 * worse than no panel - you would have to work out which of them was lying.
	 */
	private ConfigList lootList() {
		return new ConfigList() {

			public int size() {
				return nearby().size();
			}

			public String label(int index) {
				Near near = at(index);
				return near == null ? "" : near.name;
			}

			public String detail(int index) {
				Near near = at(index);
				if (near == null) {
					return null;
				}
				String count = near.count > 1 ? formatCount(near.count) + "  \u00b7  " : "";
				return count + (near.tiles == 0 ? "underfoot"
					: near.tiles == 1 ? "1 tile away" : near.tiles + " tiles away");
			}

			public String value(int index) {
				Near near = at(index);
				return near == null || near.worth <= 0 ? null : money(near.worth);
			}

			public boolean removable(int index) {
				return false;
			}

			public String emptyMessage() {
				return "Nothing worth naming nearby. The Display settings decide what counts.";
			}

			private Near at(int index) {
				List<Near> all = nearby();
				return index >= 0 && index < all.size() ? all.get(index) : null;
			}
		};
	}

	/** One kind of item on the floor, totalled across every tile it is lying on. */
	static final class Near {

		final String name;
		int count;
		long worth;
		int tiles;

		Near(String name, int tiles) {
			this.name = name;
			this.tiles = tiles;
		}
	}

	/**
	 * Everything nameable within the player's radius, merged by name and sorted by worth.
	 *
	 * MERGED BY NAME RATHER THAN BY ID because that is the question the panel answers - "how
	 * much of this is around" - and two ids with one name (a noted item, a charged version) are
	 * the same answer to it. The overlay merges by id instead, because there it is drawing one
	 * row per thing on one tile and the ids are what is there.
	 *
	 * Read on the game thread, as every ConfigList is.
	 */
	private List<Near> nearby() {
		List<Near> out = new java.util.ArrayList<Near>();
		if (!this.ctx.isLoggedIn()) {
			return out;
		}
		boolean reveal = GroundItemPrefs.showHidden();
		long floor = GroundItemPrefs.minValue();
		int selfX = this.ctx.worldToSceneX(this.ctx.getWorldX());
		int selfZ = this.ctx.worldToSceneZ(this.ctx.getWorldZ());

		java.util.Map<String, Near> byName = new java.util.LinkedHashMap<String, Near>();
		List<GroundItemPile> piles = this.ctx.getGroundItemPiles(GroundItemPrefs.radius());
		for (int i = 0; i < piles.size(); i++) {
			GroundItemPile pile = piles.get(i);
			// Chebyshev, not Euclidean: the game moves and reaches in squares, so "three tiles
			// away" means three steps, not 2.83 of something.
			int tiles = Math.max(Math.abs(pile.sceneTileX - selfX), Math.abs(pile.sceneTileZ - selfZ));
			List<Row> rows = visibleRows(pile, reveal, floor);
			for (int j = 0; j < rows.size(); j++) {
				GroundItem item = rows.get(j).item;
				Near near = byName.get(item.name);
				if (near == null) {
					near = new Near(item.name, tiles);
					byName.put(item.name, near);
				} else if (tiles < near.tiles) {
					near.tiles = tiles;              // the nearest one of them is the useful one
				}
				near.count += item.count;
				// count * price, NOT item.worth(). worth() answers the overlay's question -
				// what is ONE of these worth, which is what decides the colour of a label and
				// whether a pile of twenty swords counts as cheap. This page asks a different
				// one: how much is lying there. Two 2800gp dragon bones are 5600gp of bones,
				// and reusing worth() here said 2800 because they do not stack.
				near.worth += (long) item.count * (long) item.price;
			}
		}
		out.addAll(byName.values());
		java.util.Collections.sort(out, new java.util.Comparator<Near>() {

			public int compare(Near a, Near b) {
				// Worth first, then nearest, then by name so the order never jitters between
				// two reads of the same scene.
				if (a.worth != b.worth) {
					return a.worth > b.worth ? -1 : 1;
				}
				return a.tiles != b.tiles ? a.tiles - b.tiles : a.name.compareToIgnoreCase(b.name);
			}
		});
		return out;
	}

	// ------------------------------------------------------------------ formatting

	/**
	 * An amount of money the way a player reads it: 15000 is "15k gp".
	 *
	 * SEPARATE FROM floor() BELOW, and the difference is a word. The floor setting means "this
	 * much OR MORE" and says so with a +; an item's actual worth means exactly this much. The
	 * loot page reused the floor's formatter at first and told the player a 15,000gp scimitar
	 * was worth "15k gp+", which is not a rounding - it is the wrong claim.
	 */
	static String money(long value) {
		if (value >= 1000000) {
			return value / 1000000 + "m gp";
		}
		if (value >= 1000) {
			return value / 1000 + "k gp";
		}
		return value + " gp";
	}

	/** The value floor, as its button reads: a minimum, so "or more" is part of the answer. */
	static String floor(int value) {
		return value == 0 ? "any value" : money(value) + "+";
	}

	/** A stack size the way the client writes it elsewhere: 100K, 10M. */
	static String formatCount(int count) {
		if (count < 100000) {
			return String.valueOf(count);
		}
		if (count < 10000000) {
			return count / 1000 + "K";
		}
		return count / 1000000 + "M";
	}
}
