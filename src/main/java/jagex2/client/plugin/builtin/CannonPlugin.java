package jagex2.client.plugin.builtin;

import java.util.ArrayList;
import java.util.List;

import jagex2.client.plugin.ConfigItem;
import jagex2.client.plugin.Overlay;
import jagex2.client.plugin.OverlayGraphics;
import jagex2.client.plugin.Plugin;
import jagex2.client.plugin.PluginDescriptor;
import jagex2.client.plugin.Subscribe;
import jagex2.client.plugin.event.GameTick;

/**
 * How long the dwarf multicannon has left, and how many cannonballs are in it.
 *
 * THE RULES ARE THE SERVER'S (content quest_mcannon/scripts/cannon_setup.rs2, after Old School's
 * 24 March 2016 update): a built cannon breaks 25 minutes after it was set up, a broken one can
 * be repaired, which starts the 25 minutes again, and one left broken for 10 more minutes decays
 * and has to be fetched back from Nulodion. A part-built cannon never breaks; it just goes, 25
 * minutes after its last part. None of that clock is something the client could work out - the
 * loc on the floor looks the same for all 35 minutes - so the server says it in varps.
 *
 * THE SERVER SENDS A NUMBER EVERY FIVE TICKS, AND THIS COUNTS THE TICKS BETWEEN. cannon_hud_ticks
 * is how many ticks the current state had left when the server last looked, and it looks every
 * five. Shown as it arrives, the countdown would move in three-second jumps; so the value is
 * taken as an anchor when it changes and every game tick after it takes one off, until the next
 * one replaces it. A wrong guess here corrects itself within three seconds, which is why there is
 * no attempt to be cleverer.
 *
 * SHOWN ONLY WHILE THERE IS A CANNON, so it is on by default: a player without one never sees it.
 */
@PluginDescriptor(
	name = "Cannon",
	description = "Time until your cannon breaks or decays, and its cannonballs",
	key = "cannon",
	enabledByDefault = true,
	apiLevel = 10
)
public final class CannonPlugin extends Plugin {

	/**
	 * The content's varp ids (pack/varp.pack): rockthrower is the cannonballs loaded, and the
	 * other two are the decay clock. Fixed assignments - CannonTest compares them against the
	 * content when it is checked out beside this repo.
	 */
	static final int AMMO_VARP = 3;
	static final int HUD_VARP = 2121;
	static final int TICKS_VARP = 2122;

	/** %cannon_hud's values, the content's ^cannon_hud_* constants. */
	static final int HUD_NONE = 0;
	static final int HUD_BUILDING = 1;
	static final int HUD_WORKING = 2;
	static final int HUD_BROKEN = 3;

	/** What the cannon holds at most (cannon_setup.rs2's 30). */
	static final int AMMO_MAX = 30;

	/** Five minutes in ticks - the server's own warning, ^cannon_warn_ticks. */
	static final int WARN_TICKS = 500;

	static final int COLOUR_TITLE = 0xFFFFFF;
	static final int COLOUR_OK = 0xFFFFFF;
	static final int COLOUR_SOON = 0xFFFF00;
	static final int COLOUR_BROKEN = 0xFF4444;

	@ConfigItem(keyName = "showAmmo", name = "Show cannonballs loaded")
	public boolean showAmmo = true;

	@ConfigItem(keyName = "showBuilding", name = "Show a part-built cannon",
		description = "A base, stand or barrels on the ground also decay, 25 minutes after the last part")
	public boolean showBuilding = true;

	@ConfigItem(keyName = "notifyBroken", name = "Notify when the cannon breaks")
	public boolean notifyBroken = false;

	@ConfigItem(keyName = "font", name = "Text size", choices = {
		OverlayGraphics.FONT_CHOICE_SMALL,
		OverlayGraphics.FONT_CHOICE_NORMAL,
		OverlayGraphics.FONT_CHOICE_BOLD
	})
	public String font = OverlayGraphics.FONT_CHOICE_SMALL;

	/** Where the panel starts before the player drags it (hold Alt), under the Boosts panel. */
	private static final int X = 6;
	private static final int Y = 90;

	/** The countdown between the server's updates. See the class comment. */
	static final class Clock {

		int hud = HUD_NONE;
		int anchor;
		int since;

		/** One game tick, with what the varps say now. Returns the state before this tick. */
		int tick(int hudNow, int ticksNow) {
			int before = this.hud;
			if (hudNow != this.hud || ticksNow != this.anchor) {
				this.hud = hudNow;
				this.anchor = ticksNow;
				this.since = 0;
			} else {
				this.since++;
			}
			return before;
		}

		int remaining() {
			return Math.max(0, this.anchor - this.since);
		}
	}

	final Clock clock = new Clock();

	protected void startUp() {
		this.addOverlay(new Overlay() {

			public void render(OverlayGraphics g) {
				CannonPlugin.this.draw(g);
			}
		});
	}

	@Subscribe
	public void onGameTick(GameTick event) {
		if (!this.ctx.isLoggedIn()) {
			this.clock.tick(HUD_NONE, 0);
			return;
		}
		int before = this.clock.tick(this.ctx.getVarp(HUD_VARP), this.ctx.getVarp(TICKS_VARP));
		if (this.notifyBroken && before == HUD_WORKING && this.clock.hud == HUD_BROKEN) {
			this.ctx.notify("Cannon", "Your cannon has broken. Repair it within 10 minutes.");
		}
	}

	private void draw(OverlayGraphics g) {
		if (!this.ctx.isLoggedIn()) {
			return;
		}
		List<String> lines = lines(this.clock.hud, this.clock.remaining(), this.ctx.getVarp(AMMO_VARP),
			this.showAmmo, this.showBuilding);
		if (lines.isEmpty()) {
			return;
		}
		g.setFont(OverlayGraphics.fontFor(this.font));
		g.panel(X, Y, "Cannon", lines.toArray(new String[0]), COLOUR_TITLE,
			colour(this.clock.hud, this.clock.remaining()));
	}

	/** The panel's lines, or none when there is nothing to show. */
	static List<String> lines(int hud, int ticksLeft, int ammo, boolean showAmmo, boolean showBuilding) {
		List<String> lines = new ArrayList<String>();
		if (hud == HUD_WORKING) {
			lines.add("Breaks in " + time(ticksLeft));
		} else if (hud == HUD_BROKEN) {
			lines.add("Broken - repair it!");
			lines.add("Decays in " + time(ticksLeft));
		} else if (hud == HUD_BUILDING && showBuilding) {
			lines.add("Unfinished");
			lines.add("Decays in " + time(ticksLeft));
		} else {
			return lines;
		}
		if (showAmmo && hud != HUD_BUILDING) {
			lines.add("Cannonballs: " + Math.max(0, ammo) + "/" + AMMO_MAX);
		}
		return lines;
	}

	/** Red when broken, yellow in the last five minutes, white otherwise. */
	static int colour(int hud, int ticksLeft) {
		if (hud == HUD_BROKEN) {
			return COLOUR_BROKEN;
		}
		return ticksLeft <= WARN_TICKS ? COLOUR_SOON : COLOUR_OK;
	}

	/** Ticks as m:ss. A tick is 0.6 seconds, so 100 ticks is exactly one minute. */
	static String time(int ticks) {
		int seconds = Math.max(0, ticks) * 3 / 5;
		int s = seconds % 60;
		return (seconds / 60) + ":" + (s < 10 ? "0" : "") + s;
	}
}
