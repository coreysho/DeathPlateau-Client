package jagex2.client.plugin.builtin;

import java.util.List;

import jagex2.client.plugin.ConfigItem;
import jagex2.client.plugin.Overlay;
import jagex2.client.plugin.OverlayGraphics;
import jagex2.client.plugin.Plugin;
import jagex2.client.plugin.PluginConfig;
import jagex2.client.plugin.PluginDescriptor;
import jagex2.client.plugin.SceneLoc;
import jagex2.client.plugin.Subscribe;
import jagex2.client.plugin.event.GameTick;

/**
 * Marks every agility obstacle around you, on all three courses and at every shortcut.
 *
 * The case it earns its keep in is the one a 2006 client is worst at: an obstacle is a piece of
 * scenery that looks like scenery. A log is a log, a wall is a wall, and the one you can climb
 * looks exactly like the twenty you cannot. On a course that costs a lap; at a shortcut it costs
 * you not knowing the shortcut is there at all.
 *
 * THE OBSTACLES COME FROM THIS SERVER'S OWN CONTENT, not from OSRS - see
 * {@link AgilityObstacles}. Three courses are implemented here, not eight, and a table of ids
 * copied off a wiki would mark scenery that does nothing and miss the shortcuts that work.
 *
 * SHORTCUTS CARRY THEIR LEVEL, and one you cannot use yet is drawn differently rather than
 * hidden - knowing a 70 Agility crevice is there is most of why you would train the skill. The
 * switch to hide them is there for players who would rather not see what they cannot use.
 *
 * SCANNED ON A TICK, NOT PER FRAME. Finding locs is a walk of the scene's whole 104x104 grid,
 * which is cheap once a tick and silly fifty times a second. Rescanned every tick rather than
 * cached against a scene-change event, because obstacles are ordinary scenery: a gate the server
 * opens is a different loc in the same square, and a cache would keep marking the old one.
 *
 * A SCENE-LAYER OVERLAY, like Tile indicators and Npc indicators, for the same reason: it is
 * attached to things in the world, so dragging it could only ever point it somewhere wrong.
 *
 * NOTHING HERE CAN ACT. A {@link SceneLoc} is an id, a place and a size, with no handle on the
 * scene entry behind it. This draws and nothing more - it will not click an obstacle, walk you
 * to one, or run a lap. A plugin draws and reads; the client owns input and the socket.
 */
@PluginDescriptor(
	name = "Agility",
	description = "Marks course obstacles and shortcuts, with the level each one asks for",
	key = "agility",
	apiLevel = 9
)
public final class AgilityPlugin extends Plugin {

	/** The three values {@link #label} takes. Constants, so the choices and the test agree. */
	static final String LABEL_NONE = "Nothing";

	static final String LABEL_NAME = "Name";

	static final String LABEL_NAME_LEVEL = "Name and level";

	static final String DEFAULT_COURSE_COLOUR = "35E82E";

	static final String DEFAULT_SHORTCUT_COLOUR = "2EC6E8";

	static final String DEFAULT_LOCKED_COLOUR = "E8662E";

	static final int MIN_BORDER = 1;

	static final int MAX_BORDER = 5;

	static final int DEFAULT_BORDER = 2;

	static final int MAX_FILL = 255;

	static final int DEFAULT_FILL = 40;

	/** How high above an obstacle's tile its label sits, in scene units. */
	static final int LABEL_HEIGHT = 40;

	// ---- ONE SWITCH PER COURSE.
	//
	// There are twelve of them and a player trains one at a time, so a single "mark course
	// obstacles" switch is the wrong grain: it is on for the course you are running and on for
	// the eleven you are not. The order of these fields is the order AgilityObstacles numbers
	// the courses, which switchFor() depends on and a check walks end to end.

	@ConfigItem(keyName = "gnome", name = "Gnome course")
	public boolean gnome = true;

	@ConfigItem(keyName = "barbarian", name = "Barbarian course")
	public boolean barbarian = true;

	@ConfigItem(keyName = "wilderness", name = "Wilderness course")
	public boolean wilderness = true;

	@ConfigItem(keyName = "draynor", name = "Draynor rooftops", description = "Level 1")
	public boolean draynor = true;

	@ConfigItem(keyName = "alkharid", name = "Al Kharid rooftops", description = "Level 20")
	public boolean alkharid = true;

	@ConfigItem(keyName = "varrock", name = "Varrock rooftops", description = "Level 30")
	public boolean varrock = true;

	@ConfigItem(keyName = "canifis", name = "Canifis rooftops", description = "Level 40")
	public boolean canifis = true;

	@ConfigItem(keyName = "falador", name = "Falador rooftops", description = "Level 50")
	public boolean falador = true;

	@ConfigItem(keyName = "seers", name = "Seers rooftops", description = "Level 60")
	public boolean seers = true;

	@ConfigItem(keyName = "pollnivneach", name = "Pollnivneach rooftops", description = "Level 70")
	public boolean pollnivneach = true;

	@ConfigItem(keyName = "rellekka", name = "Rellekka rooftops", description = "Level 80")
	public boolean rellekka = true;

	@ConfigItem(keyName = "ardougne", name = "Ardougne rooftops", description = "Level 90")
	public boolean ardougne = true;

	@ConfigItem(keyName = "shortcuts", name = "Mark agility shortcuts",
		description = "Walls, stiles, tunnels and crevices that need an Agility level")
	public boolean shortcuts = true;

	@ConfigItem(keyName = "dungeon", name = "Mark the Yanille dungeon",
		description = "The obstacles inside the Yanille agility dungeon")
	public boolean dungeon = true;

	@ConfigItem(keyName = "courseColour", name = "Course colour", colour = true)
	public String courseColour = DEFAULT_COURSE_COLOUR;

	@ConfigItem(keyName = "shortcutColour", name = "Shortcut colour", colour = true)
	public String shortcutColour = DEFAULT_SHORTCUT_COLOUR;

	@ConfigItem(keyName = "lockedColour", name = "Colour when your level is too low",
		colour = true)
	public String lockedColour = DEFAULT_LOCKED_COLOUR;

	@ConfigItem(keyName = "hideLocked", name = "Hide what your level cannot use",
		description = "Off by default: knowing a shortcut is there is why you train the skill")
	public boolean hideLocked = false;

	@ConfigItem(keyName = "label", name = "What a mark says",
		choices = { LABEL_NONE, LABEL_NAME, LABEL_NAME_LEVEL })
	public String label = LABEL_NAME_LEVEL;

	@ConfigItem(keyName = "fill", name = "Fill the tiles")
	public boolean fill = false;

	@ConfigItem(keyName = "fillOpacity", name = "How solid the fill is",
		description = "0 turns it off, 255 is opaque")
	public int fillOpacity = DEFAULT_FILL;

	@ConfigItem(keyName = "borderWidth", name = "How thick the outline is",
		description = "In pixels, 1 to 5")
	public int borderWidth = DEFAULT_BORDER;

	@ConfigItem(keyName = "font", name = "Label font",
		choices = { OverlayGraphics.FONT_CHOICE_SMALL, OverlayGraphics.FONT_CHOICE_NORMAL,
			OverlayGraphics.FONT_CHOICE_BOLD })
	public String font = OverlayGraphics.FONT_CHOICE_SMALL;

	/**
	 * What the last tick's scan found.
	 *
	 * Held between frames on purpose: the scan is a grid walk and the frames between two ticks
	 * would all get the same answer out of it.
	 */
	private List<SceneLoc> found;

	/** Not looked for yet, and looked for and not there. See {@link #agilityLevel()}. */
	static final int UNRESOLVED = -2;

	static final int NO_SUCH_SKILL = -1;

	private int agilitySkill = UNRESOLVED;

	protected void startUp() {
		this.addOverlay(new Overlay() {

			public int layer() {
				return Overlay.LAYER_SCENE;
			}

			public void render(OverlayGraphics g) {
				AgilityPlugin.this.draw(g);
			}
		});
	}

	protected void shutDown() {
		// Dropped rather than kept, so turning this off does not leave a scene's worth of locs
		// alive for as long as the client runs.
		this.found = null;
	}

	@Subscribe
	public void onGameTick(GameTick event) {
		if (!this.ctx.isLoggedIn()) {
			this.found = null;
			return;
		}
		this.found = this.ctx.findLocs(AgilityObstacles.ids());
	}

	private void draw(OverlayGraphics g) {
		List<SceneLoc> locs = this.found;
		if (locs == null || !this.ctx.isLoggedIn()) {
			return;
		}
		int width = borderFor(this.borderWidth);
		int alpha = this.fill ? fillFor(this.fillOpacity) : 0;
		int level = this.agilityLevel();
		g.setFont(OverlayGraphics.fontFor(this.font));
		for (int i = 0; i < locs.size(); i++) {
			SceneLoc loc = locs.get(i);
			AgilityObstacles.Obstacle obstacle = AgilityObstacles.byId(loc.id);
			if (obstacle == null || !this.wanted(obstacle)) {
				continue;
			}
			boolean locked = level < obstacle.level;
			if (locked && this.hideLocked) {
				continue;
			}
			int colour = PluginConfig.parseColour(this.colourFor(obstacle, locked));
			this.mark(g, loc, colour, width, alpha);
			String text = this.labelFor(obstacle, locked);
			if (text.length() > 0) {
				this.ctx.projectTile(loc.sceneTileX, loc.sceneTileZ, LABEL_HEIGHT);
				g.textCentred(this.ctx.getProjectedX(), this.ctx.getProjectedY(), text, colour);
			}
		}
	}

	/** Every tile the obstacle stands on, outlined, and filled when the player asked. */
	private void mark(OverlayGraphics g, SceneLoc loc, int colour, int width, int alpha) {
		for (int dx = 0; dx < loc.width; dx++) {
			for (int dz = 0; dz < loc.length; dz++) {
				int tx = loc.sceneTileX + dx;
				int tz = loc.sceneTileZ + dz;
				if (alpha > 0) {
					TileIndicatorsPlugin.fillTile(this.ctx, g, tx, tz, colour, alpha);
				}
				TileIndicatorsPlugin.outlineTile(this.ctx, g, tx, tz, colour, width);
			}
		}
	}

	/**
	 * The player's Agility level, found by NAME rather than by a hardcoded index.
	 *
	 * The index is 16 in a stock 377 cache and this server is free to change that; a wrong
	 * constant would read some other skill's level and lock or unlock every shortcut by it,
	 * which looks like the plugin working and is not. Resolved once and remembered, because
	 * the cache's skill list does not change while the client runs.
	 *
	 * IF THERE IS NO SUCH SKILL, NOTHING IS LOCKED. The alternative - level 0 - would grey out
	 * every shortcut in the game and read as "this plugin is broken" rather than "this client
	 * has no Agility", and marking an obstacle a player cannot use is the lesser mistake.
	 */
	int agilityLevel() {
		if (this.agilitySkill == UNRESOLVED) {
			this.agilitySkill = NO_SUCH_SKILL;
			for (int skill = 0; skill < this.ctx.getSkillCount(); skill++) {
				if ("agility".equalsIgnoreCase(this.ctx.getSkillName(skill))) {
					this.agilitySkill = skill;
					break;
				}
			}
		}
		if (this.agilitySkill == NO_SUCH_SKILL) {
			return Integer.MAX_VALUE;
		}
		return this.ctx.getBaseLevel(this.agilitySkill);
	}

	/**
	 * Whether the player asked for this obstacle's course.
	 *
	 * BY INDEX, against a list in the courses' own order, rather than a switch with fourteen
	 * arms. The risk that buys is a mis-ordered list silently mapping Falador's switch onto
	 * Seers' obstacles, which is why the check for this walks every course end to end: each
	 * one turned on alone, and only its own obstacles wanted.
	 */
	boolean wanted(AgilityObstacles.Obstacle obstacle) {
		boolean[] on = this.switchFor();
		return obstacle.course >= 0 && obstacle.course < on.length && on[obstacle.course];
	}

	/** The switches, in the order AgilityObstacles numbers the courses. */
	boolean[] switchFor() {
		return new boolean[] {
			this.gnome, this.barbarian, this.wilderness, this.draynor, this.alkharid,
			this.varrock, this.canifis, this.falador, this.seers, this.pollnivneach,
			this.rellekka, this.ardougne, this.shortcuts, this.dungeon
		};
	}

	/** Which of the three colours a mark is drawn in. */
	String colourFor(AgilityObstacles.Obstacle obstacle, boolean locked) {
		if (locked) {
			return this.lockedColour;
		}
		return obstacle.course == AgilityObstacles.SHORTCUT
			? this.shortcutColour : this.courseColour;
	}

	/**
	 * What a mark says.
	 *
	 * A LEVEL IS ONLY WORTH SAYING WHEN THERE IS ONE. Nothing on the gnome course asks for a
	 * level, and "Log balance (level 1)" on every obstacle of the beginners' course is noise
	 * that teaches the player to stop reading the labels.
	 */
	String labelFor(AgilityObstacles.Obstacle obstacle, boolean locked) {
		if (LABEL_NONE.equals(this.label)) {
			return "";
		}
		if (LABEL_NAME.equals(this.label) || obstacle.level <= 1) {
			return obstacle.name;
		}
		return obstacle.name + " (" + obstacle.level + ")";
	}

	/** An outline a player can see, within what is an outline rather than a block of colour. */
	static int borderFor(int width) {
		if (width < MIN_BORDER) {
			return MIN_BORDER;
		}
		return width > MAX_BORDER ? MAX_BORDER : width;
	}

	/** A fill that cannot be more than opaque. 0 is honest: it means no fill. */
	static int fillFor(int alpha) {
		if (alpha < 0) {
			return 0;
		}
		return alpha > MAX_FILL ? MAX_FILL : alpha;
	}
}
