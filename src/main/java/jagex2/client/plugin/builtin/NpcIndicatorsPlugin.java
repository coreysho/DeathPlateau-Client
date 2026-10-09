package jagex2.client.plugin.builtin;

import java.util.List;

import jagex2.client.plugin.Actor;
import jagex2.client.plugin.ConfigItem;
import jagex2.client.plugin.Overlay;
import jagex2.client.plugin.OverlayGraphics;
import jagex2.client.plugin.Plugin;
import jagex2.client.plugin.PluginConfig;
import jagex2.client.plugin.PluginDescriptor;
import jagex2.client.plugin.Subscribe;
import jagex2.client.plugin.event.GameTick;
import jagex2.client.plugin.event.MenuBuilt;
import jagex2.client.plugin.event.SettingsMenuOpening;
import jagex2.client.MenuSwaps;

/**
 * Marks the npcs you name, so you can find them in a crowd.
 *
 * The case it earns its keep in is a busy one: a slayer task in a dungeon of six other monsters,
 * an npc you need among the shop crowd in Varrock, a boss whose spawn you want to see the moment
 * it lands. A 2006 client gives you a name on hover and nothing at a glance.
 *
 * MATCHING IS BY SUBSTRING, case-insensitive, on a comma-separated list - so "goblin" finds a
 * Goblin and a Goblin Guard, and you do not have to know which of the two the config calls it.
 * The alternative, exact names, reads as "nothing happened" the first time a player guesses the
 * name slightly wrong, and a highlight that silently does nothing is worse than one that catches
 * a little too much.
 *
 * ADDING AND REMOVING IS ALSO SHIFT-RIGHT-CLICK, which is how a player will actually do it: a
 * "Tag" row on the settings menu over any npc. Typing a list is the escape hatch, not the path.
 * The row writes the same comma-separated setting the box holds, so the two are one thing seen
 * two ways rather than two stores to keep in step - and tagging is an EXACT term while matching
 * is a substring, so untagging "Goblin" cannot take "Goblin Guard" with it.
 *
 * A TERM MAY CARRY ITS OWN COLOUR, written "Goblin=FF0000". Three monsters marked in the same
 * green tell you which three are interesting and nothing else, which is the same argument the
 * per-item colours in Ground items are built on. A term with no colour uses the plugin's.
 *
 * A SCENE-LAYER OVERLAY, like Tile indicators and for the same reason: it is attached to things
 * in the world, so dragging it could only ever point it somewhere wrong.
 *
 * NOTHING HERE CAN ACT. An {@link Actor} is a snapshot of name, place and size, with no handle on
 * the entity behind it, so this draws and nothing more - no clicking, no following, no attacking.
 * That is the line the whole plugin API is drawn on: a plugin draws and reads, the client owns
 * input and the socket.
 */
@PluginDescriptor(
	name = "Npc indicators",
	description = "Marks the npcs you name, by tile and by name tag",
	key = "npc-indicators",
	apiLevel = 6
)
public final class NpcIndicatorsPlugin extends Plugin {

	/** The two values {@link #tagPosition} takes. Constants, so the choices and the test agree. */
	static final String ABOVE = "Above";
	static final String AT_FEET = "At feet";

	/** The two values {@link #tileStyle} takes. */
	static final String EVERY_TILE = "Every tile";
	static final String ANCHOR_TILE = "South-west tile";

	/**
	 * How far above the ground the name tag is drawn, in scene units - 128 to a tile, so this is
	 * a little under two tiles up.
	 *
	 * A constant rather than the model's real height, because a plugin cannot see models. Too low
	 * and the tag sits inside the npc; this clears a human-sized one, and a bigger npc gets a
	 * little more from its size below.
	 */
	private static final int TAG_HEIGHT = 230;

	/** Extra height per tile of size, so a big npc's tag clears it too. */
	private static final int TAG_HEIGHT_PER_SIZE = 70;

	/**
	 * How many matching npcs are drawn in one frame.
	 *
	 * A cap because the cost is per-npc projection and text, and a name like "a" would match every
	 * npc in a crowded scene. Nearest first (getNpcs sorts), so the cap drops the far ones, which
	 * are the ones a player is least looking for.
	 */
	static final int DEFAULT_MAX_DRAWN = 32;
	static final int MIN_MAX_DRAWN = 1;
	static final int MAX_MAX_DRAWN = 64;

	/** A border is at least one pixel, and five is already thicker than a distant tile. */
	static final int MIN_BORDER = 1;
	static final int MAX_BORDER = 5;

	@ConfigItem(keyName = "names",
		name = "Npcs to mark, comma separated (part of a name is enough)")
	public String names = "";

	@ConfigItem(keyName = "colour", name = "Marker colour", colour = true)
	public String colour = "00FF00";

	@ConfigItem(keyName = "tile", name = "Outline the tiles they stand on")
	public boolean tile = true;

	@ConfigItem(keyName = "tag", name = "Show their name above them")
	public boolean tag = true;

	@ConfigItem(keyName = "level", name = "Include the combat level in the name")
	public boolean level = false;

	/**
	 * Over the npc's head, or on the ground at its feet.
	 *
	 * A drop-down rather than a boolean called something like "tagAtFeet", because there is no
	 * reading of that name that tells a player which way round it goes.
	 */
	@ConfigItem(keyName = "tagPosition", name = "Where the name sits",
		description = "Over its head, or on the ground", choices = { ABOVE, AT_FEET })
	public String tagPosition = ABOVE;

	@ConfigItem(keyName = "tileStyle", name = "Which tiles to outline",
		description = "A big npc stands on several", choices = { EVERY_TILE, ANCHOR_TILE })
	public String tileStyle = EVERY_TILE;

	@ConfigItem(keyName = "borderWidth", name = "Outline thickness, in pixels")
	public int borderWidth = MIN_BORDER;

	@ConfigItem(keyName = "textOutline", name = "Outline the name",
		description = "Instead of a drop shadow, which a light floor swallows")
	public boolean textOutline = false;

	@ConfigItem(keyName = "maxDrawn", name = "Most npcs marked at once",
		description = "A short term can match a whole crowd")
	public int maxDrawn = DEFAULT_MAX_DRAWN;

	@ConfigItem(keyName = "menuColour", name = "Colour their right-click options",
		description = "Every row for a marked npc, in its colour")
	public boolean menuColour = false;

	@ConfigItem(keyName = "notifyAppears", name = "Notify when a marked npc appears",
		description = "When one you named was not in the scene a moment ago")
	public boolean notifyAppears = false;

	protected void startUp() {
		this.addOverlay(new Overlay() {

			public int layer() {
				return Overlay.LAYER_SCENE;
			}

			public void render(OverlayGraphics g) {
				NpcIndicatorsPlugin.this.draw(g);
			}
		});
	}

	private void draw(OverlayGraphics g) {
		if (!this.ctx.isLoggedIn() || (!this.tile && !this.tag)) {
			return;
		}
		// Asked for once per frame, not once per npc: getNpcs walks the scene and allocates.
		List<Actor> npcs = this.ctx.getNpcs();
		int fallback = PluginConfig.parseColour(this.colour);
		int cap = maxDrawnFor(this.maxDrawn);
		int width = borderFor(this.borderWidth);
		boolean everyTile = everyTile(this.tileStyle);
		int drawn = 0;
		for (int i = 0; i < npcs.size() && drawn < cap; i++) {
			Actor npc = npcs.get(i);
			if (!matches(npc.name, this.names)) {
				continue;
			}
			int colour = termColour(npc.name, this.names, fallback);
			drawn++;
			if (this.tile) {
				// Every tile it occupies by default, not just the one it is anchored to: a 3x3
				// boss outlined on its south-west tile looks like a mis-aimed marker rather than
				// a boss. The anchor alone is offered for anyone who wants the quieter mark.
				int span = everyTile ? npc.size : 1;
				for (int dx = 0; dx < span; dx++) {
					for (int dz = 0; dz < span; dz++) {
						TileIndicatorsPlugin.outlineTile(this.ctx, g,
							npc.sceneTileX + dx, npc.sceneTileZ + dz, colour, width);
					}
				}
			}
			if (this.tag) {
				this.drawTag(g, npc, colour);
			}
		}
	}

	private void drawTag(OverlayGraphics g, Actor npc, int colour) {
		int height = tagHeight(this.tagPosition, npc.size);
		// centreX/centreZ, so a big npc's tag is over the middle of it. project answers false for
		// anything the scene cannot place - behind the camera, or on the outermost tiles, which
		// projectFromGround refuses - and drawing from a failed projection is how an overlay ends
		// up with text pinned in the corner of the screen.
		if (!this.ctx.project(npc.centreX(), npc.centreZ(), height)) {
			return;
		}
		String text = label(npc, this.level);
		if (this.textOutline) {
			g.textCentredOutlined(this.ctx.getProjectedX(), this.ctx.getProjectedY(), text, colour);
		} else {
			g.textCentred(this.ctx.getProjectedX(), this.ctx.getProjectedY(), text, colour);
		}
	}

	// ------------------------------------------------------------------ the right-click menu

	/**
	 * Every row for a marked npc, in its colour.
	 *
	 * COLOUR ONLY. Level 6 offers a row's colour and its order and nothing else, and this uses
	 * only the colour: moving an npc's Attack row is a change to what a click does, which is the
	 * client's business rather than a plugin's.
	 *
	 * Returns on the first line unless the player asked, because MenuBuilt fires on every frame
	 * the cursor is over anything.
	 */
	@Subscribe
	public void onMenuBuilt(MenuBuilt event) {
		if (!this.menuColour) {
			return;
		}
		int fallback = PluginConfig.parseColour(this.colour);
		for (int i = 1; i < event.size; i++) {
			String name = menuNpcName(this.ctx.getMenuOption(i));
			if (name.length() == 0 || !matches(name, this.names)) {
				continue;
			}
			this.ctx.setMenuColour(i, termColour(name, this.names, fallback));
		}
	}

	/**
	 * The npc name out of a menu row, or "" for a row that is not about an npc.
	 *
	 * The kind tag is how the client says what a row is about, and an npc's is "yel". Read
	 * through MenuSwaps, which already strips the tags and the combat-level suffix, so a term
	 * matches here exactly the way it matches a name tag in the world.
	 */
	static String menuNpcName(String option) {
		int at = MenuSwaps.tagAt(option);
		if (at < 0 || !"yel".equals(MenuSwaps.parseKind(option, at))) {
			return "";
		}
		return MenuSwaps.parseTarget(option, at);
	}

	// ------------------------------------------------------------------ tagging, on shift-right

	/**
	 * A "Tag" row on the settings menu over any npc, which is how a player will actually add one.
	 *
	 * Writes the same comma-separated setting the config box holds - see saveNames - so the menu
	 * and the box are one thing seen two ways. Untagging matches the term EXACTLY: a substring
	 * rule would let "Untag Goblin" quietly take "Goblin Guard" with it.
	 */
	@Subscribe
	public void onSettingsMenuOpening(SettingsMenuOpening event) {
		for (int i = 0; i < event.getTargets().size(); i++) {
			final SettingsMenuOpening.Target target = event.getTargets().get(i);
			// "yel" is the client's kind tag for an npc. Not the word "npc": Target.kind carries
			// the tag the menu row was written with.
			if (!"yel".equals(target.kind) || this.seenBefore(event, i)) {
				continue;
			}
			final String name = target.name;
			boolean tagged = hasTerm(this.names, name);
			event.addRow((tagged ? "Untag @yel@" : "Tag @yel@") + name, new Runnable() {

				public void run() {
					NpcIndicatorsPlugin.this.toggleTag(name);
				}
			});
		}
	}

	/** Whether an earlier target in the list is the same npc, so it already has its row. */
	private boolean seenBefore(SettingsMenuOpening event, int index) {
		SettingsMenuOpening.Target target = event.getTargets().get(index);
		for (int j = 0; j < index; j++) {
			SettingsMenuOpening.Target other = event.getTargets().get(j);
			if ("yel".equals(other.kind) && other.name.equalsIgnoreCase(target.name)) {
				return true;
			}
		}
		return false;
	}

	/** Adds the name, or takes it out if it is already an exact term. Package-visible for the test. */
	void toggleTag(String name) {
		String before = this.names;
		String after = hasTerm(before, name) ? removeTerm(before, name) : addTerm(before, name);
		this.saveNames(after);
		this.ctx.addChatMessage(hasTerm(after, name)
			? "Now marking " + name + "."
			: "No longer marking " + name + ".");
	}

	/**
	 * Writes the names setting and persists it, so a tag survives a restart.
	 *
	 * Through the plugin's own PluginConfig rather than a file of its own: the setting IS the
	 * store, and a second one would be two things to keep in step. The loop is because config is
	 * addressed by Item rather than by key - which is all a plugin needs, and not worth new API.
	 */
	private void saveNames(String value) {
		this.names = value;
		if (this.config == null) {
			return;
		}
		List<PluginConfig.Item> items = this.config.getItems();
		for (int i = 0; i < items.size(); i++) {
			if ("names".equals(items.get(i).key)) {
				this.config.set(items.get(i), value);
				return;
			}
		}
	}

	// ------------------------------------------------------------------ appearing

	/** The marked names in the scene at the last tick, for "one you named has appeared". */
	private String seen = "";

	/**
	 * Whether there has been a tick to compare against.
	 *
	 * A FLAG, NOT AN EMPTY `seen`. "Nothing was here last tick" and "there was no last tick" are
	 * different answers and the empty string was being used for both - so the first arrival after
	 * a scene emptied of marked npcs was read as a first scan and said nothing, which is exactly
	 * the case the feature exists for: the boss has gone and come back.
	 */
	private boolean scanned;

	/**
	 * Notifies when a marked npc is in the scene that was not a moment ago.
	 *
	 * BY NAME, NOT BY NPC. Actor carries the config id, which every Goblin shares, so there is
	 * nothing here that could tell one Goblin from another - and "a Goblin appeared" fired every
	 * time one of six wandered in and out would be noise. A name going from absent to present is
	 * the question a player actually has: the boss has spawned, the slayer monster is here.
	 */
	@Subscribe
	public void onGameTick(GameTick event) {
		if (!this.notifyAppears || !this.ctx.isLoggedIn()) {
			// Forgotten while it is off, so turning it on does not announce the whole scene -
			// and a logout does not leave last session's crowd behind.
			this.seen = "";
			this.scanned = false;
			return;
		}
		List<Actor> npcs = this.ctx.getNpcs();
		StringBuilder now = new StringBuilder();
		for (int i = 0; i < npcs.size(); i++) {
			String name = npcs.get(i).name;
			if (!matches(name, this.names)) {
				continue;
			}
			String key = "," + name.toLowerCase() + ",";
			if (now.indexOf(key) < 0) {
				now.append(key);
				if (this.scanned && this.seen.indexOf(key) < 0) {
					this.ctx.notify("Npc indicators", name + " has appeared.");
				}
			}
		}
		// THE FIRST TICK AFTER TURNING IT ON REPORTS NOTHING: everything is new on a first scan,
		// and a player who just enabled this does not want the crowd they are standing in read
		// out to them. Every tick after it has something to compare against, including one where
		// the scene held no marked npc at all.
		this.seen = now.toString();
		this.scanned = true;
	}

	/**
	 * How far above the ground the tag goes. Pure, so the rule is tested as one.
	 *
	 * Anything that is not "At feet" reads as above, including an empty string or a value left
	 * behind by a release that offered a third choice: an unknown setting must fall back to the
	 * default rather than put the tag somewhere nobody asked for.
	 */
	static int tagHeight(String position, int size) {
		if (AT_FEET.equals(position)) {
			return 0;
		}
		return TAG_HEIGHT + (size - 1) * TAG_HEIGHT_PER_SIZE;
	}

	/**
	 * What the tag says. Pure, so the combat-level rule can be checked without a scene.
	 *
	 * A level only when there is one to show: a banker and a shopkeeper have no combat level, and
	 * "Banker (level-0)" is worse than "Banker". This is the same rule the client's own menu rows
	 * follow - see the addNpcOptions comment about a fishing spot reading "(level-2)".
	 */
	static String label(Actor npc, boolean withLevel) {
		if (!withLevel || npc.combatLevel <= 0) {
			return npc.name;
		}
		return npc.name + " (level-" + npc.combatLevel + ")";
	}

	/**
	 * Whether this npc's name matches the player's list.
	 *
	 * Pure and static so the whole rule is testable: comma separated, case-insensitive, each term
	 * trimmed, blank terms ignored, and a term matches when the name contains it. An empty or
	 * blank list matches nothing at all - the plugin ships enabled with no names, and a list that
	 * meant "everything" when empty would outline the entire scene on first run.
	 */
	static boolean matches(String name, String terms) {
		return termColour(name, terms, 1) != 0;
	}

	/**
	 * The colour this npc should be marked in, or 0 for a name no term matches.
	 *
	 * ONE WALK, TWO ANSWERS. "Does it match" and "in what colour" are the same search, and two
	 * methods walking the list separately could disagree about which term won - so matches() is
	 * this with a non-zero fallback, and 0 means no match. The fallback is what a term with no
	 * colour of its own gets.
	 *
	 * THE FIRST MATCHING TERM WINS, which is the order they are written in. A later term cannot
	 * override an earlier one, so a player who wants "Goblin Guard" a different colour from
	 * "Goblin" writes the longer one first - and the chat line the Tag row prints is the thing
	 * that tells them the tag landed, whichever colour it ends up in.
	 */
	static int termColour(String name, String terms, int fallback) {
		if (name == null || terms == null || name.length() == 0) {
			return 0;
		}
		String lower = name.toLowerCase();
		int from = 0;
		while (from <= terms.length()) {
			int comma = terms.indexOf(',', from);
			String term = (comma < 0 ? terms.substring(from) : terms.substring(from, comma)).trim();
			String want = termName(term);
			if (want.length() > 0 && lower.indexOf(want.toLowerCase()) >= 0) {
				int own = termOwnColour(term);
				return own == 0 ? fallback : own;
			}
			if (comma < 0) {
				return 0;
			}
			from = comma + 1;
		}
		return 0;
	}

	/** "Goblin=FF0000" -> "Goblin". A term with no colour is its own name. */
	static String termName(String term) {
		if (term == null) {
			return "";
		}
		int equals = term.indexOf('=');
		return (equals < 0 ? term : term.substring(0, equals)).trim();
	}

	/**
	 * A term's own colour, or 0 for one that has none.
	 *
	 * 0 rather than -1 because 0 is already what termColour answers for "no match", so a rule
	 * written "Goblin=000000" is read as having no colour of its own - the same decision Ground
	 * items makes about a hand-edited black, and for the same reason: a marker drawn in the
	 * colour that means "not drawn" is an npc that silently stops being marked.
	 */
	static int termOwnColour(String term) {
		if (term == null) {
			return 0;
		}
		int equals = term.indexOf('=');
		if (equals < 0) {
			return 0;
		}
		String hex = term.substring(equals + 1).trim();
		if (hex.length() != 6) {
			return 0;
		}
		for (int i = 0; i < 6; i++) {
			if (Character.digit(hex.charAt(i), 16) < 0) {
				return 0;
			}
		}
		return Integer.parseInt(hex, 16);
	}

	/**
	 * Whether the list holds this name as a term of its own, matched whole.
	 *
	 * EXACT, unlike matches(). Tagging and untagging are about the term a player added, so
	 * "Untag Goblin" must not take "Goblin Guard" with it - and "is Goblin Guard already tagged"
	 * must answer no just because "Goblin" is, or the Tag row would offer to untag something it
	 * never added.
	 */
	static boolean hasTerm(String terms, String name) {
		return indexOfTerm(terms, name) >= 0;
	}

	/** Where this exact term starts in the list, or -1. */
	private static int indexOfTerm(String terms, String name) {
		if (terms == null || name == null || name.length() == 0) {
			return -1;
		}
		int from = 0;
		while (from <= terms.length()) {
			int comma = terms.indexOf(',', from);
			String term = (comma < 0 ? terms.substring(from) : terms.substring(from, comma)).trim();
			if (termName(term).equalsIgnoreCase(name.trim())) {
				return from;
			}
			if (comma < 0) {
				return -1;
			}
			from = comma + 1;
		}
		return -1;
	}

	/** The list with this name added, or unchanged if it is already an exact term. */
	static String addTerm(String terms, String name) {
		String add = name == null ? "" : name.trim();
		if (add.length() == 0 || add.indexOf(',') >= 0 || add.indexOf('=') >= 0) {
			// A name with a comma or an equals in it would be read back as two terms, or as a
			// colour. No npc in the cache has either, and silently mangling the list is worse
			// than declining a name that cannot be stored.
			return terms == null ? "" : terms;
		}
		if (hasTerm(terms, add)) {
			return terms;
		}
		String list = terms == null ? "" : terms.trim();
		return list.length() == 0 ? add : list + ", " + add;
	}

	/**
	 * The list with this exact term taken out, keeping the rest and its colours.
	 *
	 * Rebuilt term by term rather than cut out by index, because a term carries its colour with
	 * it and a substring splice would have to get the commas right on both sides of whichever
	 * position it landed in - including first and last, which is where that kind of code breaks.
	 */
	static String removeTerm(String terms, String name) {
		if (terms == null || name == null) {
			return terms == null ? "" : terms;
		}
		String want = name.trim();
		StringBuilder out = new StringBuilder();
		int from = 0;
		while (from <= terms.length()) {
			int comma = terms.indexOf(',', from);
			String term = (comma < 0 ? terms.substring(from) : terms.substring(from, comma)).trim();
			if (term.length() > 0 && !termName(term).equalsIgnoreCase(want)) {
				if (out.length() > 0) {
					out.append(", ");
				}
				out.append(term);
			}
			if (comma < 0) {
				break;
			}
			from = comma + 1;
		}
		return out.toString();
	}

	/** Whether every tile a big npc stands on is outlined, or only the one it is anchored to. */
	static boolean everyTile(String style) {
		return !ANCHOR_TILE.equals(style);
	}

	/** The outline thickness a player asked for, within what a tile can carry. */
	static int borderFor(int width) {
		if (width < MIN_BORDER) {
			return MIN_BORDER;
		}
		return width > MAX_BORDER ? MAX_BORDER : width;
	}

	/** How many npcs may be marked in one frame. */
	static int maxDrawnFor(int most) {
		if (most < MIN_MAX_DRAWN) {
			return MIN_MAX_DRAWN;
		}
		return most > MAX_MAX_DRAWN ? MAX_MAX_DRAWN : most;
	}
}
