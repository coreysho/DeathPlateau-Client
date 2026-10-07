package deathplateau.plugins;

import jagex2.client.plugin.ConfigItem;
import jagex2.client.plugin.Overlay;
import jagex2.client.plugin.OverlayGraphics;
import jagex2.client.plugin.Plugin;
import jagex2.client.plugin.PluginDescriptor;
import jagex2.client.plugin.Subscribe;
import jagex2.client.plugin.event.GameStateChanged;
import jagex2.client.plugin.event.GameTick;
import jagex2.client.plugin.event.KeyPressed;
import jagex2.client.plugin.event.StatChanged;

/**
 * Experience gained this session, and the rate it is coming in at.
 *
 * The example that uses the rest of the API: events, per-session state, a hotkey, and an overlay
 * built from several lines. Worth reading for how it handles the awkward parts -
 *
 * TIME IS COUNTED IN GAME TICKS, not milliseconds. A tick is a server cycle, so the clock stops
 * while the connection is down and a disconnect does not quietly tank the rate. 100 ticks to the
 * minute is near enough (a tick is 600ms) and keeps the arithmetic in integers.
 *
 * THE CLOCK STARTS AT THE FIRST XP, not at login. Someone who logs in, walks to a tree and starts
 * cutting wants the rate for the woodcutting, not for the walk.
 */
@PluginDescriptor(
	name = "XP tracker",
	description = "Session experience and xp/hr. F7 resets it.",
	key = "xptracker"
)
public final class XpTrackerPlugin extends Plugin {

	/** F7, in the client's key encoding: 1008 is F1, so F7 is 1014. */
	private static final int RESET_KEY = 1014;

	private static final int TICKS_PER_HOUR = 6000;

	@ConfigItem(keyName = "perSkill", name = "List each skill", description = "A line per skill as well as the total")
	public boolean perSkill = true;

	@ConfigItem(keyName = "resetOnLogin", name = "Reset on login", description = "Start a new session each time you log in")
	public boolean resetOnLogin = true;

	private final int[] gained = new int[64];
	private int total;
	private int ticks;
	private boolean counting;

	private final Overlay overlay = new Overlay() {

		public void render(OverlayGraphics g) {
			if (!counting || total == 0) {
				return;                      // nothing earned yet: do not clutter the screen
			}
			String[] lines = buildLines();
			g.setFont(OverlayGraphics.FONT_SMALL).panel(6, 40, "Session xp", lines, 0xFFB000, 0xFFFFFF);
		}
	};

	protected void startUp() {
		this.reset();
		this.addOverlay(this.overlay);
		this.addRailPage();
		// A config list: the rows come from what the plugin has accumulated, not from anything
		// typed in, which is what a ConfigList is for. Removing one forgets that skill.
		this.addConfigList("Skills this session", new jagex2.client.plugin.ConfigList() {

			public int size() {
				return tracked().size();
			}

			public String label(int index) {
				int skill = tracked().get(index).intValue();
				return ctx.getSkillName(skill);
			}

			public String detail(int index) {
				int skill = tracked().get(index).intValue();
				return format(gained[skill]) + " xp";
			}

			public void onRemove(int index) {
				int skill = tracked().get(index).intValue();
				total -= gained[skill];
				gained[skill] = 0;
				if (total <= 0) {
					reset();
				}
			}

			public String emptyMessage() {
				return "Gain some experience and the skills appear here.";
			}
		});
	}

	/**
	 * A page of its own on the sidebar's rail: the total at the top and a row per skill
	 * under it, each with what it has earned, the rate it is earning at, and how far through
	 * its current level it is.
	 *
	 * GUARDED, BECAUSE A PLUGIN OUTLIVES THE CLIENT IT WAS BUILT AGAINST. addPanel arrived in
	 * a later client than this jar will land on: on an older one the call does not resolve and
	 * throws NoSuchMethodError, which the manager handles by switching the WHOLE PLUGIN off -
	 * so a player on an older client would lose the overlay and the settings page too, over a
	 * page they could not have seen anyway. Catching it costs them the page and nothing else.
	 *
	 * Throwable rather than Exception on purpose: a missing method is an Error, and catching
	 * the narrower one would not catch this at all.
	 */
	private void addRailPage() {
		try {
			this.addPanel("Session xp", "chart", new jagex2.client.plugin.ConfigList() {

				public int size() {
					return 1 + tracked().size();          // the total, then a row per skill
				}

				public String label(int index) {
					return index == 0 ? "Total" : ctx.getSkillName(skillAt(index));
				}

				public String detail(int index) {
					int amount = index == 0 ? total : gained[skillAt(index)];
					return format(perHour(amount)) + " xp/hr";
				}

				public String value(int index) {
					return format(index == 0 ? total : gained[skillAt(index)]);
				}

				/** How far through the current level, which the total row has no answer for. */
				public int progress(int index) {
					if (index == 0) {
						return -1;
					}
					int skill = skillAt(index);
					int level = ctx.getBaseLevel(skill);
					if (level <= 0) {
						// No level to be partway through: not logged in, or a skill the client has
						// not been told about. NO BAR rather than a full one - an empty readout
						// drawn as "complete" is worse than no readout.
						return -1;
					}
					int start = ctx.getExperienceForLevel(level);
					int next = ctx.getExperienceForLevel(level + 1);
					if (next <= start) {
						return 100;                       // 99, or a skill with nowhere left to go
					}
					long through = (long) (ctx.getExperience(skill) - start) * 100L / (next - start);
					return (int) (through < 0 ? 0 : through);
				}

				public String action(int index) {
					return index == 0 ? "Reset" : null;
				}

				public void onAction(int index) {
					if (index == 0) {
						reset();
					}
				}

				public boolean removable(int index) {
					return false;
				}

				public String emptyMessage() {
					return "Gain some experience and it appears here.";
				}
			});
		} catch (Throwable olderClient) {
			// A client from before rail pages existed. Everything else here still works.
		}
	}

	/** The skill on a panel row, where row 0 is the total and the rest follow in skill order. */
	private int skillAt(int row) {
		java.util.List<Integer> skills = this.tracked();
		int at = row - 1;
		return at >= 0 && at < skills.size() ? skills.get(at).intValue() : 0;
	}

	/** The skills with experience this session, in skill order - the rows of the config list. */
	private java.util.List<Integer> tracked() {
		java.util.List<Integer> skills = new java.util.ArrayList<Integer>();
		for (int skill = 0; skill < this.gained.length && skill < this.ctx.getSkillCount(); skill++) {
			if (this.gained[skill] > 0) {
				skills.add(Integer.valueOf(skill));
			}
		}
		return skills;
	}

	protected void shutDown() {
		// Turned off and on again starts a fresh session rather than resuming a stale one from
		// however long ago the plugin was last running.
		this.reset();
	}

	@Subscribe
	public void onStatChanged(StatChanged event) {
		if (event.gained <= 0 || event.skill < 0 || event.skill >= this.gained.length) {
			return;
		}
		this.gained[event.skill] += event.gained;
		this.total += event.gained;
		this.counting = true;
	}

	@Subscribe
	public void onGameTick(GameTick event) {
		if (this.counting) {
			this.ticks++;
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event) {
		if (event.isLoggedIn() && this.resetOnLogin) {
			this.reset();
		}
	}

	@Subscribe
	public void onKeyPressed(KeyPressed event) {
		if (event.key == RESET_KEY) {
			this.reset();
			this.ctx.addChatMessage("Session xp reset.");
			// Consumed so the key is the plugin's and does not fall through into the chat box.
			event.consume();
		}
	}

	private void reset() {
		for (int i = 0; i < this.gained.length; i++) {
			this.gained[i] = 0;
		}
		this.total = 0;
		this.ticks = 0;
		this.counting = false;
	}

	private String[] buildLines() {
		java.util.List<String> lines = new java.util.ArrayList<String>();
		lines.add(format(this.total) + "   (" + format(this.perHour(this.total)) + "/hr)");
		if (this.perSkill) {
			for (int skill = 0; skill < this.gained.length && skill < this.ctx.getSkillCount(); skill++) {
				if (this.gained[skill] > 0) {
					lines.add("@whi@" + this.ctx.getSkillName(skill) + " @yel@" + format(this.gained[skill]));
				}
			}
		}
		return lines.toArray(new String[lines.size()]);
	}

	/** Rate so far, or 0 for the first few seconds - extrapolating from two ticks is nonsense. */
	private int perHour(int amount) {
		if (this.ticks < 10) {
			return 0;
		}
		return (int) ((long) amount * TICKS_PER_HOUR / this.ticks);
	}

	private static String format(int amount) {
		if (amount >= 1000000) {
			return amount / 1000000 + "." + amount / 100000 % 10 + "m";
		}
		if (amount >= 10000) {
			return amount / 1000 + "k";
		}
		return String.valueOf(amount);
	}
}
