package jagex2.client.plugin.builtin;

import jagex2.client.plugin.ConfigItem;
import jagex2.client.plugin.Overlay;
import jagex2.client.plugin.OverlayGraphics;
import jagex2.client.plugin.Plugin;
import jagex2.client.plugin.PluginDescriptor;

/**
 * Hitpoints and prayer as bars at the edge of the viewport, so neither needs the stats tab open.
 *
 * WHY THIS AND NOT A REGEN METER. RuneLite's regen meter counts down to the next hitpoint, and
 * that cannot be built here honestly: the regen schedule lives on the server and the client is
 * never told it. The only clock a plugin could use would be one it made up, and a countdown that
 * is wrong is worse than no countdown - a player would trust it. The server does send run
 * energy, which belongs on a bar beside these two, but PluginContext has no accessor for it yet;
 * that is an API addition and so a job for the level after this one.
 *
 * What the client does know is the current and base level of every skill, which is exactly a
 * value and a maximum. Hitpoints and prayer are the two that drain in play.
 */
@PluginDescriptor(
	name = "Status bars",
	description = "Hitpoints and prayer as bars beside the game",
	key = "status-bars",
	apiLevel = 1
)
public final class StatusBarsPlugin extends Plugin {

	/** Skill indices. The client's own order - see Stats.field1504. */
	static final int HITPOINTS = 3;
	static final int PRAYER = 5;

	private static final int HP_FULL = 0x4A9E3F;
	private static final int HP_LOW = 0xC43E33;
	private static final int PRAYER_COLOUR = 0x6C9EC6;
	private static final int BACKDROP = 0x1C1C1C;
	private static final int BORDER = 0x3A3A3A;
	private static final int TEXT = 0xFFFFFF;

	/** Below this fraction of the maximum, the hitpoints bar turns red. */
	private static final int LOW_PERCENT = 30;

	/** Package-private, not private: the test reads it to know which pixel column to look in. */
	static final int BAR_W = 14;

	private static final int GAP = 3;

	@ConfigItem(keyName = "showPrayer", name = "Show the prayer bar")
	public boolean showPrayer = true;

	@ConfigItem(keyName = "showNumbers", name = "Show the number on each bar")
	public boolean showNumbers = true;

	@ConfigItem(keyName = "height", name = "Bar height")
	public int height = 120;

	/**
	 * Where the bars start, before the player drags them.
	 *
	 * NOT A SETTING, for the same reason as Boosts: position is what Alt-drag is for, and two
	 * ways to set it means the real position is a sum of both. The HEIGHT stays a setting - that
	 * is a size, not a place, and nothing drags it.
	 */
	static final int X = 6;
	static final int Y = 40;

	protected void startUp() {
		this.addOverlay(new Overlay() {

			public void render(OverlayGraphics g) {
				StatusBarsPlugin.this.draw(g);
			}
		});
	}

	private void draw(OverlayGraphics g) {
		if (!this.ctx.isLoggedIn()) {
			return;
		}
		int tall = clampHeight(this.height, g.height() - Y);
		if (tall < 8) {
			return;
		}
		int left = X;
		this.bar(g, left, Y, tall, this.ctx.getSkillLevel(HITPOINTS),
			this.ctx.getBaseLevel(HITPOINTS), true);
		if (this.showPrayer) {
			this.bar(g, left + BAR_W + GAP, Y, tall, this.ctx.getSkillLevel(PRAYER),
				this.ctx.getBaseLevel(PRAYER), false);
		}
	}

	/**
	 * One bar, filled from the bottom up.
	 *
	 * From the bottom because that is how a tank empties and how every bar in the game reads;
	 * filling from the top would have a full bar and an empty one look the same at a glance.
	 */
	private void bar(OverlayGraphics g, int x, int y, int tall, int value, int max, boolean hp) {
		g.fillAlpha(x, y, BAR_W, tall, BACKDROP, 200);
		int filled = fillHeight(value, max, tall);
		if (filled > 0) {
			int colour = hp && percent(value, max) < LOW_PERCENT ? HP_LOW
				: hp ? HP_FULL : PRAYER_COLOUR;
			g.fill(x + 1, y + tall - filled, BAR_W - 2, filled, colour);
		}
		g.box(x, y, BAR_W, tall, BORDER);
		if (this.showNumbers) {
			g.setFont(OverlayGraphics.FONT_SMALL);
			// Under the bar, not on it: a number over a part-filled bar is unreadable against
			// one colour or the other, whichever pair is picked.
			g.textCentred(x + BAR_W / 2, y + tall + g.lineHeight(), String.valueOf(value), TEXT);
		}
	}

	/**
	 * How much of a bar of the given height is filled. Static and arithmetic only, because this
	 * is the part that is worth testing and the drawing around it is not.
	 *
	 * A value above the maximum - which a boosted skill is, and prayer never is - fills the bar
	 * rather than overflowing it. A maximum of zero, which is what an unsynced skill reads as
	 * just after login, fills nothing instead of dividing by it.
	 */
	static int fillHeight(int value, int max, int tall) {
		if (max <= 0 || value <= 0 || tall <= 0) {
			return 0;
		}
		if (value >= max) {
			return tall;
		}
		return value * tall / max;
	}

	/** Percentage of the maximum, 0 to 100. Zero when there is no maximum to be a part of. */
	static int percent(int value, int max) {
		if (max <= 0 || value <= 0) {
			return 0;
		}
		return value >= max ? 100 : value * 100 / max;
	}

	/**
	 * The bar height, kept inside what the viewport can show.
	 *
	 * The height is a setting a player types in, so it can be anything - 9999, or negative. A bar
	 * taller than the viewport would draw off the bottom, and in the resizable modes the viewport
	 * changes size while the game is running, so this is asked every frame rather than once.
	 */
	static int clampHeight(int wanted, int available) {
		if (available <= 0) {
			return 0;
		}
		int tall = wanted < 8 ? 8 : wanted;
		return tall > available ? available : tall;
	}
}
