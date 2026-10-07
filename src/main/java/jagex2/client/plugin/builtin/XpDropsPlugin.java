package jagex2.client.plugin.builtin;

import java.util.ArrayList;
import java.util.List;

import jagex2.client.plugin.ConfigItem;
import jagex2.client.plugin.Overlay;
import jagex2.client.plugin.OverlayGraphics;
import jagex2.client.plugin.Plugin;
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
	legacySetting = "xp_drops"
)
public final class XpDropsPlugin extends Plugin {

	/** How long one drop stays up. Tuned: 3000 too slow, 600 too fast, 1500 right. */
	private static final long DROP_FADE_MS = 1500L;

	/** How long the total-and-progress panel stays after the last gain. */
	private static final long TRACKER_FADE_MS = 6000L;

	/** Older rows past this are dropped outright rather than queued up. */
	private static final int MAX_VISIBLE = 8;

	private static final int ROW_HEIGHT = 27;

	@ConfigItem(keyName = "showTracker", name = "Show the total panel",
		description = "The box above the drops with the skill's total and progress bar")
	public boolean showTracker = true;

	/** One "+amount" row on screen. */
	private static final class Drop {

		final int skill;
		final int amount;
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
		this.trackerSkill = event.skill;
		this.trackerTotal = event.experience;
		this.trackerUntil = now + TRACKER_FADE_MS;
		this.drops.add(0, new Drop(event.skill, event.gained, now));
		while (this.drops.size() > MAX_VISIBLE) {
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
	}

	private void render(OverlayGraphics g) {
		long now = System.currentTimeMillis();
		for (int i = this.drops.size() - 1; i >= 0; i--) {
			if (now - this.drops.get(i).created >= DROP_FADE_MS) {
				this.drops.remove(i);
			}
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
		int boxH = 32;
		int boxW = 10 + iconWidth + 8 + g.textWidth(total);
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

		this.renderProgressBar(g, boxX + 4, y + boxH - 8, boxW - 8);
		return boxH;
	}

	/** Progress through the current level's experience band. Full at 99, which has no next. */
	private void renderProgressBar(OverlayGraphics g, int x, int y, int width) {
		g.fill(x, y, width, 5, 0x201C15);
		int level = this.ctx.getBaseLevel(this.trackerSkill);
		if (level >= 99) {
			g.fill(x, y, width, 5, 0xC8641E);
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
					g.fill(x, y, filled, 5, 0xC8641E);
				}
			}
		}
		g.box(x, y, width, 5, 0x000000);
	}

	/** The rows themselves: icon and "+amount", newest on top, each easing down as one arrives. */
	private void renderDrops(OverlayGraphics g, int rightX, int y) {
		g.setFont(OverlayGraphics.FONT_NORMAL);
		for (int i = 0; i < this.drops.size(); i++) {
			Drop drop = this.drops.get(i);
			float target = i;
			if (drop.displayY < 0f) {
				drop.displayY = Math.max(0f, target - 1f);
			}
			drop.displayY += (target - drop.displayY) * 0.25f;
			if (Math.abs(target - drop.displayY) < 0.02f) {
				drop.displayY = target;
			}

			int rowY = y + Math.round(drop.displayY * ROW_HEIGHT);
			// Shadowed, so it stays readable over any colour of scene.
			String text = "+" + drop.amount;
			g.textRight(rightX, rowY + 18, text, 0xFFFF00);

			Sprite icon = this.ctx.getSkillIcon(drop.skill);
			if (icon != null) {
				g.sprite(rightX - g.textWidth(text) - icon.width() - 4, rowY, icon);
			}
		}
	}

	/** 227731 -> "227,731", the grouped total the tracker shows. */
	static String formatNumber(int value) {
		String digits = Integer.toString(value);
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
