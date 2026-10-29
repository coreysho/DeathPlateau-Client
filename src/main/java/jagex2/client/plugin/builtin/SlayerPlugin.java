package jagex2.client.plugin.builtin;

import jagex2.client.plugin.ConfigItem;
import jagex2.client.plugin.ConfigList;
import jagex2.client.plugin.Plugin;
import jagex2.client.plugin.PluginDescriptor;

/**
 * The slayer task: what it is, how many are left, and what the streak is worth.
 *
 * NONE OF THIS USED TO LEAVE THE SERVER. Every slayer varp was scope=perm with no transmit, so a
 * client knew nothing about the task a player was on - which is why this plugin could not be
 * written when it was first asked for, and why the content change that goes with it is five
 * lines marking five varps transmit=yes. No block list, no unlock bits, no imbued heart clock:
 * a page that does not show a thing has no business being sent it.
 *
 * THE NAME IS THE AWKWARD PART, and the one place this carries a copy of anything. A varp is an
 * int, so "Goblins" cannot come over the wire, and this cache has never heard of a slayer
 * category - the categories are a content idea, written on npcs as a param the client does not
 * get. So the table is generated from the content into {@link SlayerTasks} and checked against
 * it by the test, rather than being written here by hand or guessed at.
 *
 * GUESSING WAS TRIED FIRST AND THROWN AWAY. Every slayer monster carries its category, so a
 * representative npc per task can be derived and its name read out of the cache, needing no
 * table at all. It gives "Duck" for Birds and "Kolodion" for Black demons - plausible, wrong,
 * and wrong in the way nobody notices. A generated table that can fall behind loudly beats a
 * derivation that is quietly incorrect.
 */
@PluginDescriptor(
	name = "Slayer",
	description = "Your task, what is left of it, and the streak",
	key = "slayer",
	apiLevel = 10
)
public final class SlayerPlugin extends Plugin {

	/**
	 * The varps the content now sends, by the ids pack/varp.pack assigns them.
	 *
	 * Fixed assignments in a checked-in file, like the farming ones, rather than an order
	 * things happen to be packed in - so they move only when somebody edits that file, and the
	 * test pins them so that it says so when they do.
	 */
	static final int VARP_COUNT = 394;
	static final int VARP_TARGET = 395;
	static final int VARP_POINTS = 865;
	static final int VARP_STREAK = 866;
	static final int VARP_MASTER = 867;

	/**
	 * Who gives tasks, in the order slayer.varp numbers them: 1 Turael through 5 Duradel.
	 *
	 * Five names written out rather than looked up. Everything else here comes from the content
	 * because there is a lot of it and it moves; this is five words that have not changed since
	 * 2005, and a generator for them would be more machinery than the thing it generates.
	 */
	static final String[] MASTER = { "", "Turael", "Mazchna", "Vannaka", "Chaeldar", "Duradel" };

	@ConfigItem(keyName = "showStreak", name = "Show the streak and points",
		description = "Turn off for just the task and what is left of it.")
	public boolean showStreak = true;

	@Override
	protected void startUp() {
		try {
			this.addPanel("Slayer", "skull", new ConfigList() {

				public int size() {
					return SlayerPlugin.this.rowCount();
				}

				public String label(int index) {
					return SlayerPlugin.this.label(index);
				}

				public String detail(int index) {
					return SlayerPlugin.this.detail(index);
				}

				public String value(int index) {
					return SlayerPlugin.this.value(index);
				}

				public int progress(int index) {
					return SlayerPlugin.this.progress(index);
				}

				public boolean removable(int index) {
					return false;
				}

				public String emptyMessage() {
					return "No task. See a Slayer master.";
				}
			});
		} catch (Throwable olderClient) {
			// No rail pages in this client; nothing else here depends on one.
		}
	}

	// ------------------------------------------------------------------ what the server says

	int target() {
		return this.ctx == null ? 0 : this.ctx.getVarp(VARP_TARGET);
	}

	int remaining() {
		return this.ctx == null ? 0 : this.ctx.getVarp(VARP_COUNT);
	}

	/**
	 * Whether there is a task to show.
	 *
	 * BOTH HALVES, because they go out of step on purpose: finishing a task leaves the target
	 * where it was and takes the count to zero, which is how the game knows who to credit the
	 * streak to. A page reading the target alone would show a finished task for ever.
	 */
	boolean onTask() {
		return onTask(this.target(), this.remaining());
	}

	/** Static, so the rule above can be tested without a world behind it. */
	static boolean onTask(int target, int count) {
		return target > 0 && count > 0;
	}

	/** What the task is called, or "" when the table has no name for it. */
	String taskName() {
		return SlayerTasks.name(this.target());
	}

	/** Who gave it, or "" for a task from before the master was recorded. */
	static String masterName(int master) {
		return master < 0 || master >= MASTER.length ? "" : MASTER[master];
	}

	// ------------------------------------------------------------------ the page

	/**
	 * Rows: the task, then the streak and the points if they are wanted.
	 *
	 * No task means no rows at all, so the page shows its message rather than three rows of
	 * zeroes - a streak is only interesting beside a task.
	 */
	int rowCount() {
		if (!this.onTask()) {
			return 0;
		}
		return this.showStreak ? 3 : 1;
	}

	String label(int index) {
		if (index == 0) {
			String name = this.taskName();
			return name.length() > 0 ? name : "Task " + this.target();
		}
		if (index == 1) {
			return "Streak";
		}
		return index == 2 ? "Slayer points" : "";
	}

	String value(int index) {
		if (index == 0) {
			return Integer.toString(this.remaining());
		}
		if (index == 1) {
			return Integer.toString(this.ctx == null ? 0 : this.ctx.getVarp(VARP_STREAK));
		}
		return index == 2 ? Integer.toString(this.ctx == null ? 0 : this.ctx.getVarp(VARP_POINTS)) : "";
	}

	String detail(int index) {
		if (index == 0) {
			return taskDetail(this.remaining(), this.ctx == null ? 0 : this.ctx.getVarp(VARP_MASTER));
		}
		if (index == 1) {
			return "tasks finished in a row";
		}
		return index == 2 ? "spent with a Slayer master" : "";
	}

	/** The line under the task: how many are left and who set it. */
	static String taskDetail(int left, int master) {
		// NO PLURAL HERE, deliberately. "left" does not take one, so the ternary this had -
		// left == 1 ? "1 left" : left + " left" - produced the same string down both branches
		// and only looked like a rule. A mutation suite that cannot tell the two apart is
		// telling you the code says nothing.
		String who = masterName(master);
		String much = left + " left";
		return who.length() > 0 ? much + ", from " + who : much;
	}

	/**
	 * No bar on any row.
	 *
	 * A TASK HAS NO MEASURABLE PROGRESS HERE, which is worth saying out loud because it looks
	 * like it should. The client is sent what is LEFT and never what the task started at, so
	 * "34 of 150" cannot be worked out - and a bar drawn against a guessed total would move at
	 * the wrong speed on every task of an unusual length, which is most of the interesting ones.
	 */
	int progress(int index) {
		return -1;
	}
}
