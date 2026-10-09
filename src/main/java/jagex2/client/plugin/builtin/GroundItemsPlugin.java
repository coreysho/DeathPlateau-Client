package jagex2.client.plugin.builtin;

import java.util.List;

import jagex2.client.GroundItemPrefs;
import jagex2.client.MenuSwaps;
import jagex2.client.plugin.ConfigList;
import jagex2.client.plugin.GroundItem;
import jagex2.client.plugin.GroundItemPile;
import jagex2.client.plugin.ConfigItem;
import jagex2.client.plugin.Overlay;
import jagex2.client.plugin.OverlayGraphics;
import jagex2.client.plugin.Plugin;
import jagex2.client.plugin.PluginDescriptor;
import jagex2.client.plugin.Subscribe;
import jagex2.client.plugin.event.GameTick;
import jagex2.client.plugin.event.KeyPressed;
import jagex2.client.plugin.event.MenuBuilt;
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
	legacySetting = "ground_items",
	apiLevel = 6
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

	/**
	 * The ten colours and thresholds now live in {@link GroundItemPalette}, whose DEFAULTS are
	 * exactly what was compiled in here before. They are read into one palette per frame rather
	 * than per item: colourFor runs per item per tile per frame.
	 */
	@ConfigItem(keyName = "plainColour", name = "Ordinary items", colour = true,
		description = "Anything that clears no value tier")
	public String plainColour = "FFFFFF";

	@ConfigItem(keyName = "highlightColour", name = "Items you highlighted", colour = true)
	public String highlightColour = "FF40FF";

	@ConfigItem(keyName = "hiddenColour", name = "Hidden items, under Alt", colour = true)
	public String hiddenColour = "707070";

	@ConfigItem(keyName = "tier1Price", name = "Top tier from", description = "0 turns the tier off")
	public int tier1Price = 1000000;

	@ConfigItem(keyName = "tier1Colour", name = "Top tier colour", colour = true)
	public String tier1Colour = "FF9040";

	@ConfigItem(keyName = "tier2Price", name = "High tier from", description = "0 turns the tier off")
	public int tier2Price = 100000;

	@ConfigItem(keyName = "tier2Colour", name = "High tier colour", colour = true)
	public String tier2Colour = "40C0FF";

	@ConfigItem(keyName = "tier3Price", name = "Medium tier from", description = "0 turns the tier off")
	public int tier3Price = 10000;

	@ConfigItem(keyName = "tier3Colour", name = "Medium tier colour", colour = true)
	public String tier3Colour = "40FF40";

	@ConfigItem(keyName = "tier4Price", name = "Low tier from", description = "0 turns the tier off")
	public int tier4Price = 1000;

	@ConfigItem(keyName = "tier4Colour", name = "Low tier colour", colour = true)
	public String tier4Colour = "FFFF80";

	// ---- what is shown at all

	/*
	 * THESE THREE INITIALISERS ARE A MIGRATION, not just defaults.
	 *
	 * Radius, minimum value and show-hidden were cycling rows stored in GroundItemPrefs' own
	 * qol_grounditems.dat, from before plugins had settings. Writing a plain 12 here would quietly
	 * reset every player who had changed one, because PluginConfig.load leaves a field alone when
	 * the key has never been written - "never set: the field's initialiser stands". So the
	 * initialiser reads the old file, and the result is exactly right in both directions: a player
	 * who never touched the new setting keeps what they chose in the old panel, and one who has
	 * touched it has their saved value loaded over the top.
	 *
	 * GroundItemPrefs loads itself on first access, so this does not depend on anything having
	 * run first.
	 *
	 * The old file keeps these values and nothing writes them any more, which is deliberate: it
	 * makes the migration survive a player rolling back to an older client and forward again.
	 */
	@ConfigItem(keyName = "radius", name = "How far away items are named")
	public int radius = GroundItemPrefs.radius();

	@ConfigItem(keyName = "minValue", name = "Hide items worth less than",
		description = "0 names everything")
	public int minValue = GroundItemPrefs.minValue();

	@ConfigItem(keyName = "showHidden", name = "Show hidden items",
		description = "Hold Alt to reveal them anyway")
	public boolean showHidden = GroundItemPrefs.showHidden();

	@ConfigItem(keyName = "highlightedOnly", name = "Only items you highlighted",
		description = "Everything else is left unnamed")
	public boolean highlightedOnly = false;

	@ConfigItem(keyName = "highlightTiles", name = "Outline the tiles items are on")
	public boolean highlightTiles = false;

	/** The three forms {@link #priceDisplay} takes. Constants, so the choices and code agree. */
	static final String PRICE_NONE = "Name only";
	static final String PRICE_VALUE = "Name and value";
	static final String PRICE_EACH = "Name, value and each";

	@ConfigItem(keyName = "priceDisplay", name = "What a row says",
		choices = { PRICE_NONE, PRICE_VALUE, PRICE_EACH })
	public String priceDisplay = PRICE_NONE;

	// ---- telling you something landed

	/**
	 * The tiers a notification or a beam can be asked for, by name rather than by price.
	 *
	 * Named after the tiers above rather than repeating their numbers, because a player who moves
	 * "High tier from" expects what they asked to be notified about to move with it. A number here
	 * would be a second threshold to keep in step by hand.
	 */
	static final String TIER_OFF = "Off";
	static final String TIER_TOP = "Top tier";
	static final String TIER_HIGH = "High tier";
	static final String TIER_MEDIUM = "Medium tier";
	static final String TIER_LOW = "Low tier";

	@ConfigItem(keyName = "notifyHighlighted", name = "Notify when a highlighted item drops")
	public boolean notifyHighlighted = false;

	@ConfigItem(keyName = "notifyTier", name = "Notify from this tier up",
		choices = { TIER_OFF, TIER_TOP, TIER_HIGH, TIER_MEDIUM, TIER_LOW })
	public String notifyTier = TIER_OFF;

	@ConfigItem(keyName = "beamHighlighted", name = "Beam over highlighted items")
	public boolean beamHighlighted = false;

	@ConfigItem(keyName = "beamTier", name = "Beam from this tier up",
		choices = { TIER_OFF, TIER_TOP, TIER_HIGH, TIER_MEDIUM, TIER_LOW })
	public String beamTier = TIER_OFF;

	/** The beam shapes {@link #beamStyle} takes. Constants, so the choices and code agree. */
	static final String BEAM_TAPERED = "Tapered";
	static final String BEAM_STRAIGHT = "Straight";
	static final String BEAM_NARROW = "Narrow";

	@ConfigItem(keyName = "beamStyle", name = "Beam shape",
		choices = { BEAM_TAPERED, BEAM_STRAIGHT, BEAM_NARROW })
	public String beamStyle = BEAM_TAPERED;

	// ---- reading it

	@ConfigItem(keyName = "textOutline", name = "Outline the text",
		description = "Instead of a drop shadow, which a light floor swallows")
	public boolean textOutline = false;

	@ConfigItem(keyName = "hotkey", name = "Key that hides and shows the labels",
		description = "One character, or F1 to F12. Blank for none")
	public String hotkey = "";

	@ConfigItem(keyName = "altDoubleTapMs", name = "Double-tap Alt to hide, within this many ms",
		description = "0 turns double-tapping off")
	public int altDoubleTapMs = 0;

	// ---- the right-click menu
	//
	// All three are off by default. The menu is the one piece of the client a player's hands know
	// without looking, and a plugin that rearranged it on first run would have moved something
	// they were already mid-click on.

	@ConfigItem(keyName = "menuColourHighlighted", name = "Colour highlighted items in the menu",
		description = "Take rows for items you highlighted, in their own colour")
	public boolean menuColourHighlighted = false;

	@ConfigItem(keyName = "menuColourHidden", name = "Colour hidden items in the menu",
		description = "Take rows for items you hid, in the hidden colour")
	public boolean menuColourHidden = false;

	@ConfigItem(keyName = "menuDeprioritiseHidden", name = "Hidden items last in the menu",
		description = "Moves their Take rows to the bottom, keeping their order")
	public boolean menuDeprioritiseHidden = false;

	/**
	 * Rows collected for moving, reused so the handler does not allocate every frame.
	 *
	 * MenuBuilt fires on every frame the mouse is over anything, so this one is worth the field.
	 * 64 is far more Take rows than a tile can produce and bounds the work either way.
	 */
	private final int[] menuHidden = new int[64];

	/**
	 * Hidden by the hotkey or a double-tap, for this session only.
	 *
	 * NOT A SETTING. A player who hides the labels to see the floor under them wants them back
	 * next time they log in, not a plugin that quietly stayed off - and a key they pressed once by
	 * accident should not be a change to their settings file.
	 */
	private boolean suppressed;

	/** Whether the labels are hidden by the hotkey or a double-tap. Package-visible for the test. */
	boolean isSuppressed() {
		return this.suppressed;
	}

	/** When Alt last went down, for the double-tap. 0 for "not since this client started". */
	private long altDownAt;
	private boolean altWasDown;

	/**
	 * What was on the floor last tick, so a drop landing can be told from loot lying there.
	 *
	 * Scanned on GameTick rather than in render: a notification is about something happening in
	 * the game, which happens 1.6 times a second, and doing it per frame would be doing it fifty.
	 */
	private final GroundItemArrivals arrivals = new GroundItemArrivals();

	/** The beam: how many boxes, how tall each is, how wide at the base, and how solid. */
	private static final int BEAM_SEGMENTS = 14;
	private static final int BEAM_SEGMENT_H = 14;
	private static final int BEAM_W = 22;
	private static final int BEAM_ALPHA = 96;

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
		// The three cycling rows that used to be here are settings now - a typed radius rather
		// than one of seven presets, and a value anyone can set to the number they mean.
		this.addConfigList("Items", this.itemList());
		this.addConfigList("Item colours", this.colourList());
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

		// The double-tap, watched on the press edge rather than while held: Alt is held for the
		// controls, so "is it down" is true for as long as somebody is using them.
		if (alt && !this.altWasDown) {
			long now = System.currentTimeMillis();
			if (isDoubleTap(now, this.altDownAt, this.altDoubleTapMs)) {
				this.suppressed = !this.suppressed;
				this.altDownAt = 0L;                     // so a third tap is not a second pair
			} else {
				this.altDownAt = now;
			}
		}
		this.altWasDown = alt;

		if (this.suppressed) {
			return;
		}
		boolean reveal = this.showHidden || alt;
		long floor = this.minValue < 0 ? 0L : (long) this.minValue;

		// ONE PALETTE A FRAME. colourFor runs per item per tile, and parsing seven hex strings in
		// that loop would be parsing them a few hundred times to get the same seven answers.
		GroundItemPalette palette = this.palette();

		// A radius out of range is a typo, not a request: 0 would name nothing and the player
		// would have no way to tell that from the plugin being broken, and past the scene there
		// is nothing more to find.
		int radius = this.radius < 1 ? 1 : this.radius > 104 ? 104 : this.radius;
		List<GroundItemPile> piles = this.ctx.getGroundItemPiles(radius);
		for (int i = 0; i < piles.size() && this.drawn < MAX_LABELS; i++) {
			this.renderPile(g, piles.get(i), alt, reveal, floor, palette);
		}
	}

	@Subscribe
	public void onKeyPressed(KeyPressed event) {
		int wanted = Hotkey.code(this.hotkey);
		if (wanted < 0 || event.key != wanted) {
			return;
		}
		this.suppressed = !this.suppressed;
		// Swallowed, or the key also goes into the chat box - which is what KeyPressed's own
		// comment warns about.
		event.consume();
	}



	/**
	 * Whether two Alt presses this close together are a double-tap.
	 *
	 * A window of 0 is the feature turned off, which is the default: Alt is already held for the
	 * controls, so a player who never asked for this must not be able to hide their labels by
	 * holding it twice in quick succession while looting.
	 */
	static boolean isDoubleTap(long now, long lastDownAt, int windowMs) {
		return windowMs > 0 && lastDownAt > 0L && now - lastDownAt <= (long) windowMs;
	}

	/**
	 * How wide a beam is at this segment.
	 *
	 * Tapered narrows with height, which reads as going away from you. Straight does not, which is
	 * easier to see at a distance. Narrow is a thin line for somebody who wants to know a drop
	 * landed without a pillar over half the screen.
	 */
	static int beamWidth(String style, int segment) {
		int width;
		if (BEAM_STRAIGHT.equals(style)) {
			width = BEAM_W;
		} else if (BEAM_NARROW.equals(style)) {
			width = BEAM_W / 4;
		} else {
			width = BEAM_W - segment * BEAM_W / (BEAM_SEGMENTS + 1);
		}
		return width < 1 ? 1 : width;
	}

	@Subscribe
	public void onGameTick(GameTick event) {
		if (!this.ctx.isLoggedIn()) {
			// Logged out, so the next scan starts fresh rather than reporting the floor of
			// wherever the player logs in next as a pile of new drops.
			this.arrivals.reset();
			return;
		}
		boolean wantNotify = this.notifyHighlighted || !TIER_OFF.equals(this.notifyTier);
		if (!wantNotify && TIER_OFF.equals(this.beamTier) && !this.beamHighlighted) {
			// Nothing is asking, so do not even scan - but forget what was there, or turning a
			// notification on later would report the whole floor at once.
			this.arrivals.reset();
			return;
		}

		GroundItemPalette palette = this.palette();
		long notifyFrom = thresholdFor(this.notifyTier, palette);
		int radius = this.radius < 1 ? 1 : this.radius > 104 ? 104 : this.radius;
		List<GroundItemPile> piles = this.ctx.getGroundItemPiles(radius);

		this.arrivals.begin();
		for (int p = 0; p < piles.size(); p++) {
			GroundItemPile pile = piles.get(p);
			int worldX = this.ctx.sceneToWorldX(pile.sceneTileX);
			int worldZ = this.ctx.sceneToWorldZ(pile.sceneTileZ);
			for (int i = 0; i < pile.items.size(); i++) {
				GroundItem item = pile.items.get(i);
				boolean fresh = this.arrivals.add(
					GroundItemArrivals.key(worldX, worldZ, this.ctx.getPlane(), item.id));
				if (fresh && wantNotify && worthTelling(item, notifyFrom, this.notifyHighlighted)) {
					this.ctx.notify("Death Plateau", item.name + " dropped.");
				}
			}
		}
		this.arrivals.finish();
	}

	/**
	 * Whether a drop is one the player asked to hear about.
	 *
	 * Highlighted wins outright and ignores the tier, the same way it ignores the value floor when
	 * drawing: a player who named an item wants to know it landed whatever it is worth.
	 */
	static boolean worthTelling(GroundItem item, long notifyFrom, boolean notifyHighlighted) {
		if (notifyHighlighted && GroundItemPrefs.isHighlighted(item.name)) {
			return true;
		}
		return notifyFrom > 0L && item.worth() >= notifyFrom;
	}

	/**
	 * The price a named tier starts at, or 0 for "off".
	 *
	 * Reads the palette rather than its own number, so moving "High tier from" moves what gets
	 * notified about too. Reads it AFTER sorting, which is why it goes through threshold(i): a
	 * player who typed the prices out of order still gets the tier they picked rather than the
	 * slot they typed it into.
	 */
	static long thresholdFor(String tier, GroundItemPalette palette) {
		if (TIER_TOP.equals(tier)) {
			return palette.threshold(0);
		}
		if (TIER_HIGH.equals(tier)) {
			return palette.threshold(1);
		}
		if (TIER_MEDIUM.equals(tier)) {
			return palette.threshold(2);
		}
		if (TIER_LOW.equals(tier)) {
			return palette.threshold(3);
		}
		return 0L;
	}

	/** The player's ten colours and thresholds, as one value. */
	GroundItemPalette palette() {
		return GroundItemPalette.from(this.plainColour, this.highlightColour, this.hiddenColour,
			new int[] { this.tier1Price, this.tier2Price, this.tier3Price, this.tier4Price },
			new String[] { this.tier1Colour, this.tier2Colour, this.tier3Colour, this.tier4Colour });
	}

	private void renderPile(OverlayGraphics g, GroundItemPile pile, boolean alt, boolean reveal,
			long floor, GroundItemPalette palette) {
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
		List<Row> rows = visibleRows(pile, reveal, floor, palette, this.highlightedOnly);
		int visible = rows.size();
		if (visible == 0) {
			return;
		}

		// The tile, in the colour of the top row - the most valuable thing on it, since rows come
		// in the client's stacking order. Drawn before the labels so the text sits over the line.
		if (this.highlightTiles) {
			TileIndicatorsPlugin.outlineTile(this.ctx, g, pile.sceneTileX, pile.sceneTileZ,
				rows.get(0).colour);
		}
		this.drawBeam(g, pile, rows, palette);

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
			String label = label(item, this.priceDisplay);

			if (alt) {
				int right = this.drawControls(g, item, label, originX, rowY, row.colour);
				int left = originX - (CONTROL_W * 2 + g.textWidth(label)) / 2;
				minLeft = Math.min(minLeft, left);
				maxRight = Math.max(maxRight, right);
			} else {
				if (this.textOutline) {
					g.textCentredOutlined(originX, rowY, label, row.colour);
				} else {
					g.textCentred(originX, rowY, label, row.colour);
				}
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
		return visibleRows(pile, reveal, floor, GroundItemPalette.DEFAULTS, false);
	}

	static List<Row> visibleRows(GroundItemPile pile, boolean reveal, long floor,
			GroundItemPalette palette, boolean highlightedOnly) {
		List<Row> rows = new java.util.ArrayList<Row>();
		int distinct = Math.min(pile.items.size(), MAX_PER_TILE);
		for (int i = 0; i < distinct; i++) {
			GroundItem item = pile.items.get(i);
			int colour = colourFor(item, reveal, floor, palette, highlightedOnly);
			if (colour != 0) {
				rows.add(new Row(item, colour));
			}
		}
		return rows;
	}

	/** What a row says: the name, and the count when there is more than one. */
	static String label(GroundItem item) {
		return label(item, PRICE_NONE);
	}

	/**
	 * The same, with the value appended when the player asked for it.
	 *
	 * "Name and value" is the whole stack, which is the number that decides whether it is worth
	 * walking over to; "and each" adds the per-item price, which is the number that decides
	 * whether it is worth a trip back. A stack of one says the same thing twice, so the each form
	 * is only added when there is more than one.
	 */
	static String label(GroundItem item, String priceDisplay) {
		String text = item.count > 1 ? item.name + " x " + formatCount(item.count) : item.name;
		if (PRICE_NONE.equals(priceDisplay) || item.worth() <= 0L) {
			return text;
		}
		text = text + " (" + formatValue(item.worth()) + ")";
		if (PRICE_EACH.equals(priceDisplay) && item.count > 1) {
			text = text + " @ " + formatValue(item.worth() / item.count);
		}
		return text;
	}

	// ------------------------------------------------------------------ the right-click menu

	/**
	 * Recolours and reorders the Take rows for items the player has a rule about.
	 *
	 * COLOUR ONLY, AND ORDER ONLY. Nothing here can change what a row says or what it does - the
	 * API has no way to - so the worst a wrong rule can do is draw the right option in an odd
	 * colour or put it lower down. "Collapse the ground item menu" is the RuneLite option this
	 * stops short of, deliberately: merging rows removes clickable options, and that is input
	 * rather than drawing.
	 *
	 * Runs on every frame the mouse is over something, so it returns on the first line unless a
	 * player asked for one of the three.
	 */
	@Subscribe
	public void onMenuBuilt(MenuBuilt event) {
		if (!this.menuColourHighlighted && !this.menuColourHidden
			&& !this.menuDeprioritiseHidden) {
			return;
		}
		GroundItemPalette palette = this.palette();
		int moving = 0;
		for (int i = 1; i < event.size; i++) {
			if (!this.ctx.isGroundItemTake(i)) {
				continue;
			}
			String name = menuItemName(this.ctx.getMenuOption(i));
			if (name.length() == 0) {
				continue;
			}
			int rgb = menuColourFor(name, this.menuColourHighlighted, this.menuColourHidden,
				palette);
			if (rgb != 0) {
				this.ctx.setMenuColour(i, rgb);
			}
			if (this.menuDeprioritiseHidden && moving < this.menuHidden.length
				&& GroundItemPrefs.isHidden(name)) {
				this.menuHidden[moving] = i;
				moving++;
			}
		}
		// Only now, and only if there is something to move: the common frame has no hidden item
		// under the cursor and should allocate nothing.
		if (moving > 0) {
			int[] order = deprioritiseOrder(this.menuHidden, moving);
			for (int j = 0; j < order.length; j++) {
				this.ctx.deprioritiseMenuEntry(order[j]);
			}
		}
	}

	/**
	 * The bare item name out of a "Take @lre@Bones" row, or "" for a row with no target.
	 *
	 * Through MenuSwaps, which already strips the tags and the level suffix, so a rule keyed on a
	 * name matches here exactly the way it matches a label over the tile.
	 */
	static String menuItemName(String option) {
		int at = MenuSwaps.tagAt(option);
		return at < 0 ? "" : MenuSwaps.parseTarget(option, at);
	}

	/**
	 * The colour a Take row is drawn in, or 0 to leave it the white the client uses.
	 *
	 * The same colour as the label over the tile, including a rule's own: seeing one colour on the
	 * floor and another in the menu for the same item would read as two different items.
	 */
	static int menuColourFor(String name, boolean colourHighlighted, boolean colourHidden,
			GroundItemPalette palette) {
		if (name.length() == 0) {
			return 0;
		}
		if (colourHighlighted && GroundItemPrefs.isHighlighted(name)) {
			int own = GroundItemPrefs.colourOf(name);
			return own == GroundItemPrefs.DEFAULT_COLOUR ? palette.highlighted : own;
		}
		if (colourHidden && GroundItemPrefs.isHidden(name)) {
			int own = GroundItemPrefs.colourOf(name);
			return own == GroundItemPrefs.DEFAULT_COLOUR ? palette.hidden : own;
		}
		return 0;
	}

	/**
	 * The indices to call deprioritiseMenuEntry with, in call order, to move a set of rows to the
	 * bottom while keeping their order relative to each other.
	 *
	 * TWO THINGS MAKE THIS LESS OBVIOUS THAN IT LOOKS. Each move bubbles its row down to index 1,
	 * which shifts every row BELOW it up by one - so an index read before the first move is stale
	 * by the second. And the row moved LAST ends up lowest, so the rows have to be moved from the
	 * top down for the one that was on top to stay on top.
	 *
	 * Together those give order[j] = rows[count - 1 - j] + j: take them highest-first, and add one
	 * for each move already made, because every one of those was above this row and pushed it up.
	 *
	 * {@code rows} must be ascending, which is how the scan collects them.
	 */
	static int[] deprioritiseOrder(int[] rows, int count) {
		int[] order = new int[count];
		for (int j = 0; j < count; j++) {
			order[j] = rows[count - 1 - j] + j;
		}
		return order;
	}

	/**
	 * The colour a row is drawn in, or 0 for a row that is not drawn at all, with the colours the
	 * plugin shipped with. Kept so the rule can be asked about without building a palette, which
	 * is how every test of it reads.
	 */
	static int colourFor(GroundItem item, boolean reveal, long floor) {
		return colourFor(item, reveal, floor, GroundItemPalette.DEFAULTS, false);
	}

	/** The same rule, in the player's colours. */
	static int colourFor(GroundItem item, boolean reveal, long floor, GroundItemPalette palette,
			boolean highlightedOnly) {
		if (item.name.length() == 0) {
			return 0;
		}
		if (GroundItemPrefs.isHighlighted(item.name)) {
			// A rule's own colour wins over the plugin's, which is the whole point of setting one:
			// three highlighted clue steps in the same magenta tell you nothing.
			int own = GroundItemPrefs.colourOf(item.name);
			return own == GroundItemPrefs.DEFAULT_COLOUR ? palette.highlighted : own;
		}
		// "Only items you highlighted" is checked AFTER the highlight test and before everything
		// else, so it hides the rest of the floor without hiding what it is for.
		if (highlightedOnly) {
			return 0;
		}
		if (GroundItemPrefs.isHidden(item.name)) {
			if (!reveal) {
				return 0;
			}
			int own = GroundItemPrefs.colourOf(item.name);
			return own == GroundItemPrefs.DEFAULT_COLOUR ? palette.hidden : own;
		}
		long worth = item.worth();
		if (worth < floor) {
			return 0;
		}
		return palette.forWorth(worth);
	}

	/**
	 * A column of light over a pile worth noticing.
	 *
	 * NOT A MODEL, a line of boxes that narrow as they rise. A real lootbeam is a textured model
	 * the 377 renderer has no notion of, and the honest version of it here is a stack of projected
	 * quads - which reads as a beam at a distance, which is the only range it matters at. Drawn
	 * from the item's own colour so a top-tier drop and a low-tier one are not the same pillar.
	 *
	 * Projected per segment rather than drawn as a trapezoid between two projected ends, because
	 * perspective is not linear: a beam drawn between its base and tip leans the wrong way when
	 * the camera is low, which is exactly when a player is looking for it.
	 */
	private void drawBeam(OverlayGraphics g, GroundItemPile pile, List<Row> rows,
			GroundItemPalette palette) {
		long from = thresholdFor(this.beamTier, palette);
		GroundItem best = null;
		for (int i = 0; i < rows.size(); i++) {
			GroundItem item = rows.get(i).item;
			boolean named = this.beamHighlighted && GroundItemPrefs.isHighlighted(item.name);
			if (!named && (from <= 0L || item.worth() < from)) {
				continue;
			}
			// The most valuable thing on the tile gets the beam: one pile, one beam, whatever is
			// stacked under it.
			if (best == null || item.worth() > best.worth()) {
				best = item;
			}
		}
		if (best == null) {
			return;
		}
		int colour = colourFor(best, true, 0L, palette, false);
		if (colour == 0) {
			return;
		}
		for (int segment = 0; segment < BEAM_SEGMENTS; segment++) {
			int height = segment * BEAM_SEGMENT_H;
			if (!this.ctx.projectTile(pile.sceneTileX, pile.sceneTileZ, height)) {
				return;                                  // left the screen: the rest would too
			}
			// Narrowing with height, so the thing reads as going away from you rather than as a
			// rectangle standing on a tile.
			int width = beamWidth(this.beamStyle, segment);
			g.fillAlpha(this.ctx.getProjectedX() - width / 2, this.ctx.getProjectedY(),
				width, BEAM_SEGMENT_H, colour, BEAM_ALPHA);
		}
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
	 * A colour per rule, cycled.
	 *
	 * A SECOND LIST RATHER THAN A SECOND BUTTON. A config list row has one action, already spent
	 * on the mode, and giving rows two would change the ConfigList contract every plugin is
	 * written against. This costs a section in the panel and nothing else.
	 */
	private ConfigList colourList() {
		return new ConfigList() {

			public int size() {
				return GroundItemPrefs.count();
			}

			public String label(int index) {
				return GroundItemPrefs.name(index);
			}

			/** The mode, so a row says which of the two colours it is overriding. */
			public String detail(int index) {
				return GroundItemPrefs.mode(index) == GroundItemPrefs.HIDE
					? "Hidden items" : "Highlighted items";
			}

			public String action(int index) {
				return GroundItemPrefs.colourName(GroundItemPrefs.colour(index));
			}

			public void onAction(int index) {
				GroundItemPrefs.cycleColour(index);
			}

			/** Removing a rule belongs to the Items list; this one only colours them. */
			public boolean removable(int index) {
				return false;
			}

			public String emptyMessage() {
				return "Rules you add in Items can be given a colour of their own here.";
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
	/**
	 * The same scale for a value rather than a count.
	 *
	 * SEPARATE FROM THE INT FORM because a value is not a count: a stack of 2,000,000,000 coins
	 * worth 1 each already overflows an int, and a cast would print a negative price rather than
	 * a large one. Carries on past M to B for the same reason.
	 */
	static String formatValue(long value) {
		if (value < 0L) {
			return "?";
		}
		if (value < 100000L) {
			return String.valueOf(value);
		}
		if (value < 10000000L) {
			return value / 1000L + "K";
		}
		if (value < 10000000000L) {
			return value / 1000000L + "M";
		}
		return value / 1000000000L + "B";
	}

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
