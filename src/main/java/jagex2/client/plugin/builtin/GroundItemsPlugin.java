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
import jagex2.graphics.Pix2D;
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
	/**
	 * The shape Jagex's own loot beam sprite has, measured off it rather than guessed.
	 *
	 * The asset is 383x1586 - four times taller than it is wide - and its width profile is not a
	 * cone. From the tip down it stays narrow for the whole top half, reaching only 41px of its
	 * eventual 349 at the halfway mark, and then flares hard through the bottom third into a
	 * bell standing on a disc. The SOLID CORE inside it tapers linearly over the same height;
	 * it is the soft outer that flares.
	 *
	 * That difference is the whole reason the old shapes read as cones: a linear taper spends
	 * its width evenly, and the real thing spends almost none of it until the last third.
	 */
	static final String BEAM_LOOT = "Loot beam";

	static final String BEAM_TAPERED = "Tapered";
	static final String BEAM_STRAIGHT = "Straight";
	static final String BEAM_NARROW = "Narrow";

	@ConfigItem(keyName = "beamFade", name = "Beams fade as they rise",
		description = "Brightest at the item, fading out at the top, the way light does")
	public boolean beamFade = true;

	@ConfigItem(keyName = "beamCore", name = "Beams have a bright core",
		description = "A narrow bright column inside the soft one")
	public boolean beamCore = true;

	@ConfigItem(keyName = "beamGlow", name = "Beams light the ground",
		description = "A pool of light on the tile, which roots the beam to it")
	public boolean beamGlow = true;

	@ConfigItem(keyName = "beamRibbons", name = "Beams have ribbons",
		description = "The two strands wound round the lower half, as Jagex's beam has")
	public boolean beamRibbons = true;

	@ConfigItem(keyName = "beamPulse", name = "Beams pulse",
		description = "A slow brighten and dim. Off by default: motion catches the eye hardest")
	public boolean beamPulse = false;

	@ConfigItem(keyName = "beamSegments", name = "How tall a beam is",
		description = "In segments of 24 scene units, so 24 segments stand about four and a "
			+ "half tiles tall - the proportion Jagex's own beam has")
	public int beamSegments = DEFAULT_BEAM_SEGMENTS;

	@ConfigItem(keyName = "beamOpacity", name = "How solid a beam is",
		description = "8 is barely there, 255 is opaque")
	public int beamOpacity = DEFAULT_BEAM_ALPHA;

	@ConfigItem(keyName = "beamStyle", name = "Beam shape",
		choices = { BEAM_LOOT, BEAM_TAPERED, BEAM_STRAIGHT, BEAM_NARROW })
	public String beamStyle = BEAM_LOOT;

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
	/**
	 * How tall one segment is, IN SCENE UNITS. A tile is 128 of them.
	 *
	 * It was 14, described in the setting as "about one and a half tiles" - which is wrong by a
	 * factor of thirteen, 14 units being a ninth of a tile. The default of 24 segments
	 * therefore stood 2.6 tiles tall where the sprite's own proportions want about 4.4, and the
	 * same 14 was separately used as a count of PIXELS when drawing, which is what left gaps
	 * between the segments at any distance where the two did not happen to agree.
	 */
	static final int BEAM_SEGMENT_H = 24;
	/**
	 * THE COLUMN'S WIDTHS ARE PERMILLE OF THE TILE IT STANDS ON, not pixels.
	 *
	 * The first version used pixel constants - 22 across, 14 tall a segment - and a beam drawn
	 * in pixels over a scene measured in units is wrong at every distance but one. It stayed 22
	 * pixels wide whether the drop was at your feet or across the square, and its segments
	 * overlapped up close and left visible gaps further off, because the drawn height was a
	 * constant and the gap the projection leaves is not. A share of the tile's own projected
	 * box is the same share at every distance, the perspective having already been done.
	 *
	 * The numbers are measured off Jagex's sprite as a share of its widest point, which is the
	 * disc on the ground - and the disc is about a tile across in game. See lootBeamWidth.
	 */
	static final int BEAM_SLOPE_PERMILLE = 226;

	/** Where the straight part ends and the flare begins, in tenths of the beam's height. */
	static final int BEAM_FLARE_FROM = 7;

	/** The column where it meets the ground, in permille of a tile. */
	static final int BEAM_FOOT_PERMILLE = 620;

	/** ...and where the flare starts, which is the straight part's widest. */
	static final int BEAM_FLARE_PERMILLE = 158;

	static final int BEAM_STRAIGHT_PERMILLE = 300;

	static final int BEAM_NARROW_PERMILLE = 100;

	static final int BEAM_TAPER_TIP_PERMILLE = 20;

	/** What the beam was before any of it was a setting, kept as the defaults it still is. */
	/**
	 * Taller than the fourteen it was, because the real sprite is four times taller than wide
	 * and fourteen segments of a twenty-two-wide beam is barely two to one.
	 */
	static final int DEFAULT_BEAM_SEGMENTS = 24;
	static final int DEFAULT_BEAM_ALPHA = 96;

	static final int MIN_BEAM_SEGMENTS = 2;
	static final int MAX_BEAM_SEGMENTS = 40;
	static final int MIN_BEAM_ALPHA = 8;
	static final int MAX_BEAM_ALPHA = 255;

	/**
	 * How much brighter the core is than the column around it, and how wide it is.
	 *
	 * A beam of light is not one translucent slab: it is bright where the light is dense and
	 * faint at its edges. Two passes - a wide soft one and a narrow bright one - is the cheapest
	 * thing that reads that way, and it is what the old single pass was missing.
	 */
	static final int BEAM_CORE_DIVISOR = 3;
	static final int BEAM_CORE_BOOST = 2;

	/**
	 * The pool of light on the ground is THE TILE'S OWN BOX, so it needs no constant.
	 *
	 * It was 44 by 6 pixels: a fifth of a tile at arm's length and three tiles wide across the
	 * square. The sprite's disc is its widest part and about a tile across, so the tile's
	 * projected bounding box is the right size and the right shape at every distance and every
	 * camera angle, for nothing.
	 */
	static final int BEAM_GLOW_BRIGHTER = 2;

	/**
	 * The soft outer, as a percentage of the body's width, and how much of its alpha it keeps.
	 *
	 * A beam of one width at one alpha is a translucent wedge. The sprite is a bright narrow
	 * core inside a soft halo about twice the body's width, and drawing those as separate
	 * passes is the whole difference between reading as light and reading as a slab.
	 */
	static final int BEAM_HALO_PERCENT = 210;

	static final int BEAM_HALO_ALPHA = 30;

	/** Where up the beam the ribbons start, as a percentage of its height below the tip. */
	static final int BEAM_RIBBON_FROM = 55;

	/** How far round they wind over the rest of it. */
	static final int BEAM_RIBBON_TURNS = 2;

	/** How far out they swing, as a percentage of the body's width at that height. */
	static final int BEAM_RIBBON_SPREAD = 95;

	static final int BEAM_RIBBON_ALPHA = 75;

	/** How many nested quads the ground pool is built from, which is its softness. */
	static final int BEAM_POOL_RINGS = 5;

	/** One full brighten-and-dim of the pulse, in milliseconds. */
	static final long BEAM_PULSE_MS = 1800L;

	/** How far the pulse moves the brightness, as a share of it. */
	static final int BEAM_PULSE_PERCENT = 35;

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
	static int beamWidth(String style, int segment, int segments, int tileWidth) {
		int width;
		if (BEAM_LOOT.equals(style)) {
			width = lootBeamWidth(segment, segments, tileWidth);
		} else if (BEAM_STRAIGHT.equals(style)) {
			width = tileWidth * BEAM_STRAIGHT_PERMILLE / 1000;
		} else if (BEAM_NARROW.equals(style)) {
			width = tileWidth * BEAM_NARROW_PERMILLE / 1000;
		} else {
			// Linear from its foot to a tip that is still visible, which is what Tapered was.
			int down = segments - segment;
			int permille = BEAM_TAPER_TIP_PERMILLE
				+ (BEAM_STRAIGHT_PERMILLE - BEAM_TAPER_TIP_PERMILLE) * down / segments;
			width = tileWidth * permille / 1000;
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
		int segments = beamSegmentsFor(this.beamSegments);
		int base = pulsed(beamAlphaFor(this.beamOpacity), System.currentTimeMillis(),
			this.beamPulse);

		// HOW BIG THIS TILE IS ON SCREEN, which every width below is a share of. One
		// measurement, four projections, and the perspective is done for the whole beam.
		int[] box = new int[4];
		if (!tileBox(this.ctx, pile.sceneTileX, pile.sceneTileZ, box)) {
			return;                                      // cannot be placed, so cannot be drawn
		}
		int tileWidth = box[2] - box[0];
		if (tileWidth < 1) {
			return;
		}

		// THE POOL ON THE GROUND FIRST, under everything: it is what roots the beam to the tile
		// rather than leaving it hovering over one.
		//
		// THE TILE'S OWN QUAD, not its bounding box. A box is axis-aligned and the tile is a
		// diamond, so the first version painted a hard-edged rectangle on the floor with
		// corners sticking out past the tile on all four sides - it read as a sticker rather
		// than as light. fillTile is Tile indicators' scanline fill of the real quad.
		if (this.beamGlow) {
			pool(this.ctx, g, pile.sceneTileX, pile.sceneTileZ, colour,
				capAlpha(base * BEAM_GLOW_BRIGHTER));
		}

		// THE COLUMN, ONE SCREEN ROW AT A TIME.
		//
		// Not one rectangle per segment: twenty-four rectangles is twenty-four visible steps
		// down each edge of a thing that is supposed to be light, and the width within a
		// segment is constant, so the taper comes out as a staircase. A row at a time is a
		// smooth outline for the same arithmetic, and it is what makes the segment count a
		// height rather than a resolution.
		//
		// THREE PASSES, because a beam is not one flat colour. Jagex's is a bright narrow core
		// inside a soft wide halo, and that layering - not the silhouette - is most of why the
		// reference reads as light and a single translucent wedge reads as a slab.
		if (!this.ctx.projectTile(pile.sceneTileX, pile.sceneTileZ, 0)) {
			return;
		}
		int footX = this.ctx.getProjectedX();
		int footY = this.ctx.getProjectedY();
		if (!this.ctx.projectTile(pile.sceneTileX, pile.sceneTileZ,
				segments * BEAM_SEGMENT_H)) {
			return;                                      // the tip left the screen
		}
		int tipX = this.ctx.getProjectedX();
		int tipY = this.ctx.getProjectedY();
		int rise = footY - tipY;
		if (rise < 1) {
			return;                                      // edge on, so there is no beam to draw
		}
		// ONLY THE ROWS THAT CAN BE SEEN. The tip is projected, not clipped, so a beam viewed
		// from close up has its tip thousands of pixels above the viewport - and the loop would
		// run every one of those rows, calling fillAlpha each time, for a beam that is mostly
		// off screen. fillAlpha clips the drawing but not the loop around it. Pix2D's own clip
		// bounds are what the fills would be clipped to anyway.
		int firstRow = tipY < Pix2D.top ? Pix2D.top : tipY;
		int lastRow = footY > Pix2D.bottom ? Pix2D.bottom : footY;
		for (int y = firstRow; y <= lastRow; y++) {
			// Rows count down from the tip; the width and fade helpers count up from the foot,
			// so the row's "segment" is the far end of that.
			int down = y - tipY;
			int segment = rise - down;
			int x = tipX + (footX - tipX) * down / rise;
			int width = beamWidth(this.beamStyle, segment, rise, tileWidth);
			int alpha = beamAlpha(segment, rise, base, this.beamFade);
			int halo = width * BEAM_HALO_PERCENT / 100;
			g.fillAlpha(x - halo / 2, y, halo, 1, colour, capAlpha(alpha * BEAM_HALO_ALPHA / 100));
			g.fillAlpha(x - width / 2, y, width, 1, colour, alpha);
			if (this.beamCore) {
				int core = coreWidth(width);
				if (core > 0) {
					g.fillAlpha(x - core / 2, y, core, 1, colour,
						capAlpha(alpha * BEAM_CORE_BOOST));
				}
			}
			// THE TWO HELICAL RIBBONS, wound round the lower half.
			//
			// These were left out on the grounds that they are a separate element from the
			// beam's body - which is true, and is why the body is measured without them, and
			// was still the wrong call: they are most of what the eye picks out as "a loot
			// beam" rather than "a green cone". They only exist below the point where the
			// sprite's rows stop being one contiguous run, which is where the body ends and
			// they begin.
			if (this.beamRibbons && down * 100 >= rise * BEAM_RIBBON_FROM) {
				int into = down * 100 - rise * BEAM_RIBBON_FROM;
				int over = rise * (100 - BEAM_RIBBON_FROM);
				// How far round the two strands have wound by this row, as a table index.
				int turn = BEAM_RIBBON_TURNS * 2048 * into / over;
				int swing = width * BEAM_RIBBON_SPREAD / 100;
				int strand = width / 6 < 1 ? 1 : width / 6;
				int ribbon = capAlpha(alpha * BEAM_RIBBON_ALPHA / 100);
				for (int side = 0; side < 2; side++) {
					int at = turn + side * 1024;
					int off = swing * jagex2.graphics.Pix3D.sinTable[at & 2047] >> 16;
					g.fillAlpha(x + off - strand / 2, y, strand, 1, colour, ribbon);
				}
			}
		}
	}

	/**
	 * The pool of light on the ground: the tile's own quad, filled as nested rings.
	 *
	 * ONE FLAT FILL IS A SLAB. A tile quad has hard edges and a single alpha, so filling it
	 * once paints a sharp-edged lozenge on the floor that reads as a sticker - which is what
	 * the first version did with the tile's BOUNDING BOX, hard corners sticking out past the
	 * tile on four sides. The reference disc is bright in the middle and fades to nothing.
	 *
	 * Rings give that for nothing: each is the same quad pulled in toward the centre and filled
	 * at a share of the alpha, so the middle is covered by every ring and the rim by one. The
	 * spans come from Tile indicators, which is where the scanline fill already lives.
	 */
	static void pool(jagex2.client.plugin.PluginContext ctx, OverlayGraphics g, int sceneTileX,
			int sceneTileZ, int colour, int alpha) {
		int[] xs = new int[4];
		int[] ys = new int[4];
		if (!TileIndicatorsPlugin.corners(ctx, sceneTileX, sceneTileZ, xs, ys)) {
			return;
		}
		int midX = (xs[0] + xs[1] + xs[2] + xs[3]) / 4;
		int midY = (ys[0] + ys[1] + ys[2] + ys[3]) / 4;
		int each = alpha / BEAM_POOL_RINGS;
		if (each < 1) {
			each = 1;
		}
		int[] rx = new int[4];
		int[] ry = new int[4];
		for (int ring = 0; ring < BEAM_POOL_RINGS; ring++) {
			int of = BEAM_POOL_RINGS - ring;
			for (int i = 0; i < 4; i++) {
				rx[i] = midX + (xs[i] - midX) * of / BEAM_POOL_RINGS;
				ry[i] = midY + (ys[i] - midY) * of / BEAM_POOL_RINGS;
			}
			fillQuad(g, rx, ry, colour, each);
		}
	}

	/** One projected quad, filled a screen row at a time. See TileIndicatorsPlugin.fillTile. */
	private static void fillQuad(OverlayGraphics g, int[] xs, int[] ys, int colour, int alpha) {
		int top = ys[0];
		int bottom = ys[0];
		for (int i = 1; i < 4; i++) {
			top = Math.min(top, ys[i]);
			bottom = Math.max(bottom, ys[i]);
		}
		for (int y = top; y <= bottom; y++) {
			int left = TileIndicatorsPlugin.spanLeft(xs, ys, y);
			int right = TileIndicatorsPlugin.spanRight(xs, ys, y);
			if (left <= right) {
				g.fillAlpha(left, y, right - left + 1, 1, colour, alpha);
			}
		}
	}

	/**
	 * A tile's projected bounding box on screen, as {minX, minY, maxX, maxY}. False if it cannot
	 * be placed.
	 *
	 * All four corners rather than two, because the camera can be at any yaw: the screen width
	 * of a tile is the spread of its corners, and along one axis alone it collapses to nothing
	 * at 45 degrees.
	 */
	static boolean tileBox(jagex2.client.plugin.PluginContext ctx, int sceneTileX, int sceneTileZ, int[] out) {
		int[] xs = new int[4];
		int[] ys = new int[4];
		// TILE INDICATORS' OWN CORNER WALK, not a second copy of it. Two plugins that disagree
		// by half a pixel about where a tile is would show, and the beam stands on the same
		// square the tile outline draws.
		if (!TileIndicatorsPlugin.corners(ctx, sceneTileX, sceneTileZ, xs, ys)) {
			return false;
		}
		int minX = xs[0];
		int minY = ys[0];
		int maxX = xs[0];
		int maxY = ys[0];
		for (int i = 1; i < 4; i++) {
			minX = Math.min(minX, xs[i]);
			minY = Math.min(minY, ys[i]);
			maxX = Math.max(maxX, xs[i]);
			maxY = Math.max(maxY, ys[i]);
		}
		out[0] = minX;
		out[1] = minY;
		out[2] = maxX;
		out[3] = maxY;
		return true;
	}

	/**
	 * The width of one segment of a loot beam, following the measured profile of Jagex's sprite.
	 *
	 * LINEAR, NOT CUBED. The asset is 383x1586, and the first version of this measured its OUTER
	 * EXTENT per row - 12% of full width at the halfway mark - and cubed the distance below the
	 * tip to fit that one number. Both halves of that were wrong. The outer extent at halfway is
	 * dominated by the two HELICAL RIBBONS wound round the beam, which this does not draw;
	 * measuring instead the widest CONTIGUOUS run per row, which is the body alone, gives:
	 *
	 *     below the tip   0.05  0.10  0.20  0.30  0.40  0.50  0.60  0.70  0.80  0.90
	 *     body width       1.1   2.2   4.4   6.9   9.2  11.4  13.9  15.8  23.9  39.7
	 *
	 * which is a straight line at 22.6% per unit for the top seven tenths, and a flare over the
	 * last three into the disc on the ground. A cube fitted to the wrong number gave 0.1% where
	 * the sprite has 2.2%, so the top half of the beam came out one pixel wide and read as a
	 * dotted line rather than as light.
	 *
	 * The percentages are of the sprite's widest point, which is that ground disc - about a tile
	 * across in game. So they are percentages of a tile, which is what tileWidth is.
	 *
	 * Flat-topped at one pixel rather than nought: a width of 0 draws nothing and a negative one
	 * is whatever fillAlpha makes of it.
	 */
	static int lootBeamWidth(int segment, int segments, int tileWidth) {
		if (segments < 1) {
			return 1;
		}
		// How far below the TIP this segment is, in tenths. Segment 0 sits on the ground, so it
		// is the far end of the loop: down runs from segments at the foot to 1 at the tip.
		int down = segments - segment;
		int permille;
		if (down * 10 <= segments * BEAM_FLARE_FROM) {
			// The straight part: a line through the origin at the sprite's own slope.
			permille = BEAM_SLOPE_PERMILLE * down / segments;
		} else {
			// The flare, interpolated from where the straight part ends to the foot.
			// SQUARED, so the flare accelerates into a bell rather than opening as a cone.
			// The measured widths over the last three tenths are 15.8, 23.9 and 39.7 - the
			// gaps are 8 and 16, so the curve roughly doubles its rate each step. A straight
			// interpolation between the ends gives a cone, which is the one shape the whole
			// sprite is not.
			//
			// Divided twice rather than squaring first: `into` runs to three times the beam's
			// height in rows, so into * into * delta overflows an int on a tall beam.
			int into = down * 10 - segments * BEAM_FLARE_FROM;
			int across = segments * (10 - BEAM_FLARE_FROM);
			int delta = BEAM_FOOT_PERMILLE - BEAM_FLARE_PERMILLE;
			permille = BEAM_FLARE_PERMILLE + delta * into / across * into / across;
		}
		int width = tileWidth * permille / 1000;
		return width < 1 ? 1 : width;
	}

	/** How tall a beam a player asked for, within what is a beam rather than a wall. */
	static int beamSegmentsFor(int segments) {
		if (segments < MIN_BEAM_SEGMENTS) {
			return MIN_BEAM_SEGMENTS;
		}
		return segments > MAX_BEAM_SEGMENTS ? MAX_BEAM_SEGMENTS : segments;
	}

	/**
	 * How solid a beam is at its base.
	 *
	 * The floor is 8 rather than 0: a beam at 0 is a setting that turns the feature off from a
	 * box that does not say so, and the two switches above it already do that honestly.
	 */
	static int beamAlphaFor(int alpha) {
		if (alpha < MIN_BEAM_ALPHA) {
			return MIN_BEAM_ALPHA;
		}
		return alpha > MAX_BEAM_ALPHA ? MAX_BEAM_ALPHA : alpha;
	}

	/**
	 * The alpha of one segment: full at the bottom, fading to nothing at the top.
	 *
	 * LINEAR, and deliberately not quite reaching zero at the last segment - a beam whose top
	 * segment is invisible is a beam one segment shorter, and a player setting the height would
	 * find the last one did nothing. The ramp runs over segments + 1 so the top is faint rather
	 * than absent.
	 */
	static int beamAlpha(int segment, int segments, int base, boolean fade) {
		if (!fade || segments <= 1) {
			return base;
		}
		int left = segments - segment;
		int alpha = base * left / (segments + 1);
		return alpha < 1 ? 1 : alpha;
	}

	/** The bright inner column's width, or 0 when the beam is too narrow to have one. */
	static int coreWidth(int width) {
		return width / BEAM_CORE_DIVISOR;
	}

	/**
	 * A slow brighten and dim, as a share of the beam's own brightness.
	 *
	 * Takes the time rather than reading the clock, so the curve is testable: the shape of a
	 * pulse is exactly the sort of thing that is wrong by a factor and invisible in review.
	 * A triangle rather than a sine - Math.sin in a per-frame draw for a 35% wobble is not a
	 * trade anybody would make, and at this speed the two are indistinguishable.
	 */
	static int pulsed(int alpha, long nowMs, boolean pulse) {
		if (!pulse) {
			return alpha;
		}
		// nowMs % period is already under the period, so doubling it cannot reach twice the
		// period and the second modulo the first version of this had never fired once.
		long phase = (nowMs % BEAM_PULSE_MS) * 2L;
		// 0 at the start of the cycle, 1 at the middle, 0 again at the end.
		long up = phase <= BEAM_PULSE_MS ? phase : BEAM_PULSE_MS * 2L - phase;
		// THE SETTING IS THE BRIGHTEST, and the pulse dims BELOW it. The first version swung
		// symmetrically about it and peaked a third over - 129 where the player asked for 96 -
		// which is a setting that does not mean what its label says. Caught by the check that
		// the pulse never exceeds the brightness a player set.
		int swing = alpha * BEAM_PULSE_PERCENT / 100;
		int dimmed = alpha - swing;
		return capAlpha(dimmed + (int) (swing * up / BEAM_PULSE_MS));
	}

	/** An alpha that cannot leave the range Pix2D blends over. */
	static int capAlpha(int alpha) {
		if (alpha < 1) {
			return 1;
		}
		return alpha > MAX_BEAM_ALPHA ? MAX_BEAM_ALPHA : alpha;
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
