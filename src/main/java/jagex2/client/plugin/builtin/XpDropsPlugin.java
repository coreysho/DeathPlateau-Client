package jagex2.client.plugin.builtin;

import java.util.ArrayList;
import java.util.List;

import jagex2.client.plugin.ConfigItem;
import jagex2.client.plugin.Overlay;
import jagex2.client.plugin.OverlayGraphics;
import jagex2.client.plugin.Plugin;
import jagex2.client.plugin.PluginConfig;
import jagex2.client.plugin.PluginDescriptor;
import jagex2.client.plugin.Sprite;
import jagex2.client.plugin.Subscribe;
import jagex2.client.plugin.event.GameStateChanged;
import jagex2.client.plugin.event.StatChanged;

/**
 * Experience gained, as a stack of "+amount" rows in the top-right, with a panel above them
 * showing the skill's total and its progress to the next level.
 *
 * Ported out of Client.java, where it was about 180 lines across six fields, two methods and a
 * nested class. The behaviour is deliberately unchanged, down to the timings and the easing, and
 * the reasoning that produced them is kept with the code it explains:
 *
 *   - EVERY GAIN IS ITS OWN ROW. An earlier version merged same-skill gains into a running
 *     counter; Corey wanted "it to flow and disappear after each drop, like runescapes". So a
 *     new row goes in at the top, pushing the rest down, and each row's fade timer is set once
 *     when it is created and never refreshed - it leaves on its own schedule no matter what
 *     gains xp after it.
 *   - THE FADE IS 1500ms, tuned: 3000 was too slow, 600 was "a bit too fast lol".
 *   - THE ROWS EASE DOWN rather than snapping, which is what makes it read as a drop.
 *
 * WHAT CHANGED IN THE PORT. Nothing a player sees. The login guard did move: the client used to
 * skip a skill's first update itself, and now the StatChanged event carries a gained of 0 for
 * that sync, so this plugin needs no guard at all - it only ever reacts to a real gain.
 */
@PluginDescriptor(
	name = "XP drops",
	description = "Experience gained, in the top-right corner",
	key = "xp-drops",
	enabledByDefault = true,
	legacySetting = "xp_drops",
	apiLevel = 5
)
public final class XpDropsPlugin extends Plugin {

	private static final int ROW_HEIGHT = 27;

	/**
	 * Bounds on the numbers a player types, so a typo cannot make the feature vanish or stick.
	 *
	 * A 0ms fade is a drop nobody sees; a ten-minute one is a column that never clears. Neither
	 * is a wish, and a plugin that honoured either would read as broken rather than as obedient.
	 */
	static final int MIN_FADE_MS = 100;
	static final int MAX_FADE_MS = 60_000;
	static final int MIN_VISIBLE = 1;
	static final int MAX_VISIBLE = 32;
	static final int MIN_SPEED = 1;
	static final int MAX_SPEED = 100;

	/** What each number was before it was a setting, kept as the default it still is. */
	static final int DEFAULT_FADE_MS = 1500;
	static final int DEFAULT_TRACKER_FADE_MS = 6000;
	static final int DEFAULT_VISIBLE = 8;
	static final int DEFAULT_SPEED = 25;

	static final String DOWN = "Down";
	static final String UP = "Up";
	static final String STILL = "Still";

	static final String FONT_PLAIN = "Normal";
	static final String FONT_BIG = "Bold";
	static final String FONT_TINY = "Small";

	// ---- what a drop looks like

	@ConfigItem(keyName = "dropColour", name = "Drop colour", colour = true,
		description = "The \"+amount\" rows")
	public String dropColour = "FFFF00";

	@ConfigItem(keyName = "font", name = "Drop text size",
		choices = { FONT_PLAIN, FONT_BIG, FONT_TINY })
	public String font = FONT_PLAIN;

	@ConfigItem(keyName = "textOutline", name = "Outline the text",
		description = "Instead of a drop shadow, which a bright interface swallows")
	public boolean textOutline = false;

	@ConfigItem(keyName = "showIcons", name = "Show the skill icon")
	public boolean showIcons = true;

	@ConfigItem(keyName = "showSkillName", name = "Name the skill on the row",
		description = "\"+500 Attack\" rather than just \"+500\"")
	public boolean showSkillName = false;

	// ---- how it moves and when it goes

	@ConfigItem(keyName = "direction", name = "Which way the drops flow",
		choices = { DOWN, UP, STILL })
	public String direction = DOWN;

	@ConfigItem(keyName = "speed", name = "How fast they settle",
		description = "Percent of the remaining distance per frame, 1 to 100")
	public int speed = DEFAULT_SPEED;

	@ConfigItem(keyName = "fadeMs", name = "How long a drop stays, in ms",
		description = "Tuned: 3000 was too slow and 600 too fast")
	public int fadeMs = DEFAULT_FADE_MS;

	@ConfigItem(keyName = "maxVisible", name = "Most drops on screen at once")
	public int maxVisible = DEFAULT_VISIBLE;

	@ConfigItem(keyName = "groupSameSkill", name = "Add up gains in the same skill",
		description = "One running row per skill instead of a row per gain")
	public boolean groupSameSkill = false;

	// ---- the panel above them

	@ConfigItem(keyName = "showTracker", name = "Show the total panel",
		description = "The box above the drops with the skill's total and progress bar")
	public boolean showTracker = true;

	@ConfigItem(keyName = "trackerFadeMs", name = "How long the panel stays, in ms")
	public int trackerFadeMs = DEFAULT_TRACKER_FADE_MS;

	@ConfigItem(keyName = "progressColour", name = "Progress bar colour", colour = true)
	public String progressColour = "C8641E";

	@ConfigItem(keyName = "showPerHour", name = "Show experience per hour",
		description = "Measured from your first gain this session")
	public boolean showPerHour = false;

	// ---- telling you about it

	@ConfigItem(keyName = "notifyLevelUp", name = "Notify on a level up")
	public boolean notifyLevelUp = false;

	/** One "+amount" row on screen. */
	private static final class Drop {

		final int skill;

		/**
		 * Not final: grouping adds into the row that is already there rather than starting a new
		 * one. The fade still runs from {@code created} and is never refreshed - see the note at
		 * the top of the file - so a grouped row leaves on the schedule its FIRST gain set, which
		 * is what keeps "add them up" from meaning "a row that never goes away".
		 */
		int amount;

		final long created;

		/** Eased row offset in rows; -1 means "not placed yet", so the first draw flows it in. */
		float displayY = -1f;

		Drop(int skill, int amount, long created) {
			this.skill = skill;
			this.amount = amount;
			this.created = created;
		}
	}

	private final List<Drop> drops = new ArrayList<Drop>();

	private int trackerSkill = -1;
	private int trackerTotal;
	private long trackerUntil;

	/** Everything gained since the first gain of this session, for the per-hour figure. */
	private long sessionGained;
	private long sessionStart;

	protected void startUp() {
		this.reset();
		this.addOverlay(new Overlay() {

			public void render(OverlayGraphics g) {
				XpDropsPlugin.this.render(g);
			}
		});
	}

	protected void shutDown() {
		this.reset();
	}

	@Subscribe
	public void onStatChanged(StatChanged event) {
		// gained is 0 for login's initial sync of every skill, which is what stops a player
		// seeing their whole account's xp rain down the screen the moment they log in.
		if (event.gained <= 0) {
			return;
		}
		long now = System.currentTimeMillis();

		// LEVEL UPS ARE READ BEFORE THE DROP, and against the level the client still holds: the
		// event is posted from the packet handler BEFORE the client recomputes skillBaseLevel, so
		// getBaseLevel here is the level the player had a moment ago. That is exactly what makes
		// the comparison possible without the plugin keeping its own copy of the curve.
		if (this.notifyLevelUp) {
			int was = this.ctx.getBaseLevel(event.skill);
			if (isLevelUp(event.experience, was, this.ctx.getExperienceForLevel(was + 1))) {
				// Through BoostsPlugin.name rather than a second copy of it: the client stores
				// skill names lower case, and "Your attack level" reads as a typo.
				this.ctx.notify("Level up", "Your "
					+ BoostsPlugin.name(this.ctx.getSkillName(event.skill))
					+ " level is now " + (was + 1) + ".");
			}
		}

		if (this.sessionStart == 0L) {
			this.sessionStart = now;
		}
		this.sessionGained += event.gained;

		this.trackerSkill = event.skill;
		this.trackerTotal = event.experience;
		this.trackerUntil = now + (long) fadeFor(this.trackerFadeMs);

		// Grouping adds into the newest row when it is the same skill, rather than pushing a new
		// one in. Only the NEWEST: a row further down has already eased into place and had other
		// drops arrive after it, and growing it would make a number change in the middle of the
		// column with nothing arriving to explain it.
		Drop newest = this.drops.isEmpty() ? null : this.drops.get(0);
		if (this.groupSameSkill && newest != null && newest.skill == event.skill) {
			newest.amount += event.gained;
			return;
		}
		this.drops.add(0, new Drop(event.skill, event.gained, now));
		while (this.drops.size() > visibleFor(this.maxVisible)) {
			this.drops.remove(this.drops.size() - 1);
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event) {
		// Nothing from the last session should still be fading when the next one starts.
		if (!event.isLoggedIn()) {
			this.reset();
		}
	}

	private void reset() {
		this.drops.clear();
		this.trackerSkill = -1;
		this.trackerTotal = 0;
		this.trackerUntil = 0L;
		// The per-hour figure is per SESSION, so a logout clears it: carrying it across would
		// report a rate measured over time the player was not playing.
		this.sessionGained = 0L;
		this.sessionStart = 0L;
	}

	private void render(OverlayGraphics g) {
		long now = System.currentTimeMillis();
		long fade = fadeFor(this.fadeMs);
		for (int i = this.drops.size() - 1; i >= 0; i--) {
			if (now - this.drops.get(i).created >= fade) {
				this.drops.remove(i);
			}
		}
		// Lowering the cap while rows are up takes effect now rather than on the next gain.
		while (this.drops.size() > visibleFor(this.maxVisible)) {
			this.drops.remove(this.drops.size() - 1);
		}
		boolean tracker = this.showTracker && this.trackerSkill >= 0 && now < this.trackerUntil;
		if (!tracker && this.drops.isEmpty()) {
			return;
		}

		int rightX = g.width() - 5;
		// Below the client's own fps counter when that is on, where it has always sat.
		int y = this.ctx.isFpsShown() ? 68 : 22;

		if (tracker) {
			y += this.renderTracker(g, rightX, y) + 4;
		}
		this.renderDrops(g, rightX, y);
	}

	/** The total-and-progress box. Returns its height. */
	private int renderTracker(OverlayGraphics g, int rightX, int y) {
		g.setFont(OverlayGraphics.FONT_BOLD);

		// Sized to its contents. A fixed width overflowed as soon as the total got long -
		// 13,050,939 spilled past the left edge into the skill icon, because the total is
		// right-aligned inside the box.
		Sprite icon = this.ctx.getSkillIcon(this.trackerSkill);
		int iconWidth = icon == null ? 0 : icon.width();
		String total = formatNumber(this.trackerTotal);
		String rate = this.showPerHour
			? formatNumber(perHour(this.sessionGained, System.currentTimeMillis() - this.sessionStart))
				+ "/hr"
			: null;
		int boxH = rate == null ? 32 : 44;
		int boxW = 10 + iconWidth + 8 + g.textWidth(total);
		if (rate != null && 10 + g.textWidth(rate) > boxW - iconWidth + 2) {
			boxW = iconWidth + 8 + g.textWidth(rate);
		}
		if (boxW < 96) {
			boxW = 96;
		}
		int boxX = rightX - boxW;

		g.fillAlpha(boxX, y, boxW, boxH, 0x000000, 165);
		g.box(boxX, y, boxW, boxH, 0x6F6A5A);
		if (icon != null) {
			// Centred in the space above the progress bar, whatever the icon's height is.
			g.sprite(boxX + 5, y + Math.max(1, (boxH - 8 - icon.height()) / 2), icon);
		}

		// One call, not two: the client's own shadowed draw puts a black copy at +1,+1, which is
		// exactly what this hand-rolled before the overlay API had a shadow of its own.
		g.textRight(boxX + boxW - 5, y + 18, total, 0xFFFFFF);
		if (rate != null) {
			// Dimmer than the total, which is the number being read: a rate competing with it
			// for attention makes the box harder to read at a glance, not easier.
			g.textRight(boxX + boxW - 5, y + 30, rate, 0xC8C8C8);
		}

		this.renderProgressBar(g, boxX + 4, y + boxH - 8, boxW - 8);
		return boxH;
	}

	/** Progress through the current level's experience band. Full at 99, which has no next. */
	private void renderProgressBar(OverlayGraphics g, int x, int y, int width) {
		g.fill(x, y, width, 5, 0x201C15);
		int bar = PluginConfig.parseColour(this.progressColour);
		int level = this.ctx.getBaseLevel(this.trackerSkill);
		if (level >= 99) {
			g.fill(x, y, width, 5, bar);
		} else if (level >= 1) {
			int floor = this.ctx.getExperienceForLevel(level);
			int span = this.ctx.getExperienceForLevel(level + 1) - floor;
			if (span > 0) {
				int done = this.trackerTotal - floor;
				if (done < 0) {
					done = 0;
				}
				if (done > span) {
					done = span;
				}
				int filled = (int) ((long) width * (long) done / (long) span);
				if (filled > 0) {
					g.fill(x, y, filled, 5, bar);
				}
			}
		}
		g.box(x, y, width, 5, 0x000000);
	}

	/** The rows themselves: icon and "+amount", newest on top, each easing down as one arrives. */
	private void renderDrops(OverlayGraphics g, int rightX, int y) {
		g.setFont(fontFor(this.font));
		int colour = PluginConfig.parseColour(this.dropColour);
		float ease = easeFor(this.speed);
		boolean up = UP.equals(this.direction);
		boolean still = STILL.equals(this.direction);
		for (int i = 0; i < this.drops.size(); i++) {
			Drop drop = this.drops.get(i);
			float target = i;
			if (drop.displayY < 0f) {
				// Starting one row short of its place is what makes a new row FLOW in rather
				// than appear. Flowing the wrong way would read as the column jumping, so the
				// start follows the direction the column runs in.
				drop.displayY = still ? target : Math.max(0f, target - 1f);
			}
			drop.displayY += (target - drop.displayY) * ease;
			if (Math.abs(target - drop.displayY) < 0.02f) {
				drop.displayY = target;
			}

			int rowY = y + rowOffset(drop.displayY, ROW_HEIGHT, up);
			String text = dropLabel(drop.amount,
				BoostsPlugin.name(this.ctx.getSkillName(drop.skill)), this.showSkillName);
			if (this.textOutline) {
				// textCentredOutlined is the only outlined draw the API has, so the right edge
				// is converted to a centre rather than a second entry point being added for it.
				g.textCentredOutlined(rightX - g.textWidth(text) / 2, rowY + 18, text, colour);
			} else {
				g.textRight(rightX, rowY + 18, text, colour);
			}

			if (this.showIcons) {
				Sprite icon = this.ctx.getSkillIcon(drop.skill);
				if (icon != null) {
					g.sprite(rightX - g.textWidth(text) - icon.width() - 4, rowY, icon);
				}
			}
		}
	}

	// ------------------------------------------------------------------ the rules, on their own

	/** The fade a player asked for, within the bounds a visible drop needs. */
	static int fadeFor(int ms) {
		if (ms < MIN_FADE_MS) {
			return MIN_FADE_MS;
		}
		return ms > MAX_FADE_MS ? MAX_FADE_MS : ms;
	}

	/** How many rows may be up at once. */
	static int visibleFor(int rows) {
		if (rows < MIN_VISIBLE) {
			return MIN_VISIBLE;
		}
		return rows > MAX_VISIBLE ? MAX_VISIBLE : rows;
	}

	/**
	 * The share of the remaining distance a row closes each frame.
	 *
	 * 100 means "no easing at all", which is a legitimate choice and not a bug: some people want
	 * the rows to snap. 0 would be a row that never arrives, so the floor is 1.
	 */
	static float easeFor(int percent) {
		int clamped = percent < MIN_SPEED ? MIN_SPEED : (percent > MAX_SPEED ? MAX_SPEED : percent);
		return clamped / 100f;
	}

	/** Where a row sits relative to the first one, in pixels. Up is negative. */
	static int rowOffset(float displayY, int rowHeight, boolean up) {
		int offset = Math.round(displayY * rowHeight);
		return up ? -offset : offset;
	}

	/** "+500", or "+500 Attack" when the skill is named. */
	static String dropLabel(int amount, String skill, boolean named) {
		String text = "+" + amount;
		return named && skill != null && skill.length() > 0 ? text + " " + skill : text;
	}

	/**
	 * Experience per hour, or 0 before there is enough of a session to divide by.
	 *
	 * THE FIRST SECOND IS NOT A RATE. Dividing a gain by a few milliseconds gives a number in the
	 * hundreds of millions, which is not a measurement of anything - it is the denominator being
	 * small. So the figure stays at 0 until the session has run long enough to mean something.
	 */
	static long perHour(long gained, long elapsedMs) {
		if (gained <= 0L || elapsedMs < 1000L) {
			return 0L;
		}
		return gained * 3_600_000L / elapsedMs;
	}

	/**
	 * Whether this much experience is a new level, given the level before it.
	 *
	 * THE 99 GUARD IS THE WHOLE POINT. getExperienceForLevel has nothing past level 100 and
	 * answers level 99's own figure for it, so a maxed player's next-level threshold is experience
	 * they already have - and without this, every single drop at 99 would announce a level up.
	 */
	static boolean isLevelUp(int experience, int fromLevel, int nextLevelExperience) {
		return fromLevel >= 1 && fromLevel < 99 && nextLevelExperience > 0
			&& experience >= nextLevelExperience;
	}

	/** The overlay font a drop is drawn in. */
	static int fontFor(String choice) {
		if (FONT_BIG.equals(choice)) {
			return OverlayGraphics.FONT_BOLD;
		}
		return FONT_TINY.equals(choice) ? OverlayGraphics.FONT_SMALL : OverlayGraphics.FONT_NORMAL;
	}

	/**
	 * 227731 -> "227,731", the grouped total the tracker shows.
	 *
	 * Takes a long because the per-hour figure is one: a short session divides by a small number,
	 * and the result leaves int behind long before it stops being a number worth printing.
	 */
	static String formatNumber(long value) {
		String digits = Long.toString(value);
		StringBuilder out = new StringBuilder(digits.length() + 4);
		int lead = digits.length() % 3;
		if (lead == 0) {
			lead = 3;
		}
		out.append(digits, 0, lead);
		for (int i = lead; i < digits.length(); i += 3) {
			out.append(',');
			out.append(digits, i, i + 3);
		}
		return out.toString();
	}
}
