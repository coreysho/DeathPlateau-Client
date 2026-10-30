package jagex2.client.plugin.builtin;

import jagex2.client.plugin.ConfigItem;
import jagex2.client.plugin.ConfigList;
import jagex2.client.plugin.Plugin;
import jagex2.client.plugin.PluginDescriptor;

/**
 * The clue scrolls in your bag: what they say, and where they want you.
 *
 * NO SERVER CHANGE, UNLIKE THE OTHER THREE, and the reason is worth writing down. Every clue
 * step in this game is its OWN OBJECT - 107 of them - and each carries what it says and where
 * it points as parameters on itself. So the client does not need to be told which step somebody
 * is on: it can see the scroll in the inventory and that scroll IS the step. Nothing had to be
 * transmitted, and no varp was added.
 *
 * WHAT THE TABLE IS FOR. Those params are server-side - this cache has never heard of
 * trail_desc - so the text, the coordinate, the person and the answer are generated into
 * {@link ClueSteps} from the content, and the test regenerates and compares. A clue added
 * there and not here is a failing test rather than a scroll the page has nothing to say about.
 *
 * A HELPER AND NOT A SOLVER, STILL, BUT THE LINE MOVED. It shows what the scroll says, in a
 * place a player can read while walking, and for the 65 steps with a place it works out the
 * compass bearing and distance - arithmetic on two numbers the player has both of. It also
 * now names the person an anagram hides and gives a challenge scroll its answer, which the
 * scroll does NOT already say: that is the owner's call rather than this file's, and the two
 * switches below turn each of them off. What it still does not do is act. It does not walk,
 * dig, or click: no plugin here does, and a clue that solved itself would not be a clue.
 *
 * ONE ROW PER SCROLL. A player can carry an easy, a medium and a hard at once, and an earlier
 * version of this page showed only whichever sat nearest the top left - so two thirds of what
 * somebody was carrying was invisible, and which third showed depended on how they had shuffled
 * their bag. They all show now, in slot order.
 */
@PluginDescriptor(
	name = "Clue scroll",
	description = "What your clues say, where they point, and who is waiting there",
	key = "clue-scroll",
	apiLevel = 10
)
public final class CluePlugin extends Plugin {

	@ConfigItem(keyName = "showDistance", name = "Show the bearing and distance",
		description = "For steps with a place, how far off you are and which way.")
	public boolean showDistance = true;

	@ConfigItem(keyName = "showAnswers", name = "Give away anagrams and challenge answers",
		description = "The person an anagram hides, and the number a challenge scroll wants.")
	public boolean showAnswers = true;

	@ConfigItem(keyName = "showGuardian", name = "Warn about guardians",
		description = "Whether something attacks when this one is dug up.")
	public boolean showGuardian = true;

	/**
	 * The bag, and the slots of it holding clues, as of the last time the page asked how many
	 * rows there were.
	 *
	 * READ ON THE GAME THREAD AND ONLY THERE, like every ConfigList. The panel takes one
	 * snapshot per frame - size() and then the accessors, in order - so taking both of these
	 * in size() is what keeps row 2 the same scroll for its label as it was for its icon.
	 * Asking the context again per accessor would let an inventory change mid-snapshot shift
	 * the rows under themselves: a dropped scroll between label(1) and icon(1) is a row whose
	 * words and picture are two different clues.
	 */
	int[] bag = new int[0];

	int[] slots = new int[0];

	// ------------------------------------------------------------------ finding the scrolls

	/**
	 * Every slot of a bag holding a clue, in slot order, which is the order they are shown in.
	 *
	 * Static and handed the bag rather than reading the context, so the test can lay out a
	 * player's inventory without a client under it.
	 */
	static int[] clueSlots(int[] objIds) {
		int[] found = new int[objIds.length];
		int count = 0;
		for (int slot = 0; slot < objIds.length; slot++) {
			if (ClueSteps.indexOf(objIds[slot]) >= 0) {
				found[count++] = slot;
			}
		}
		int[] out = new int[count];
		System.arraycopy(found, 0, out, 0, count);
		return out;
	}

	/** What this player is carrying, slot by slot, or nothing at all before there is a world. */
	int[] carrying() {
		if (this.ctx == null) {
			return new int[0];
		}
		int size = this.ctx.getInventorySize();
		int[] objIds = new int[size];
		for (int slot = 0; slot < size; slot++) {
			objIds[slot] = this.ctx.getInventoryObjId(slot);
		}
		return objIds;
	}

	/**
	 * Where in the table the scroll in a given row sits, or -1 when there is no such row.
	 *
	 * THE ROW INDEX IS NOT TRUSTED. It comes from a panel whose snapshot is a frame behind the
	 * bag, so the row that existed when size() answered may not exist by the time its label is
	 * asked for - and a page that throws there takes the whole sidebar with it.
	 */
	static int stepAt(int[] slots, int[] objIds, int row) {
		if (row < 0 || row >= slots.length) {
			return -1;
		}
		int slot = slots[row];
		if (slot < 0 || slot >= objIds.length) {
			return -1;
		}
		return ClueSteps.indexOf(objIds[slot]);
	}

	/** The same, for the rows this page last drew. */
	int stepOf(int row) {
		return stepAt(this.slots, this.bag, row);
	}

	// ------------------------------------------------------------------ the arithmetic

	/** The eight points of the compass, as a bearing from one place to another. */
	static String bearing(int fromX, int fromZ, int toX, int toZ) {
		int dx = toX - fromX;
		int dz = toZ - fromZ;
		if (dx == 0 && dz == 0) {
			return "here";
		}
		// A direction is only worth naming when it is a direction: inside a band either side of
		// an axis the other component is noise, and "north east" for two tiles east reads as a
		// mistake. The band is a third, which is what makes the eight points even.
		int ax = dx < 0 ? -dx : dx;
		int az = dz < 0 ? -dz : dz;
		String ns = dz > 0 ? "north" : "south";
		String ew = dx > 0 ? "east" : "west";
		if (az * 3 < ax) {
			return ew;
		}
		if (ax * 3 < az) {
			return ns;
		}
		return ns + " " + ew;
	}

	/**
	 * How far apart two places are, in tiles, the way the game measures it.
	 *
	 * CHEBYSHEV, not as the crow flies, because that is how this game moves: a diagonal step
	 * covers one tile the same as a straight one, so the number of steps to a place is the
	 * larger of the two differences. A player counting paces against a Pythagorean figure would
	 * find it always too big.
	 */
	static int paces(int fromX, int fromZ, int toX, int toZ) {
		int dx = toX - fromX;
		int dz = toZ - fromZ;
		if (dx < 0) {
			dx = -dx;
		}
		if (dz < 0) {
			dz = -dz;
		}
		return dx > dz ? dx : dz;
	}

	/** The clue's words with the game's line breaks turned into spaces, for one flowing row. */
	static String flatten(String text) {
		if (text == null) {
			return "";
		}
		StringBuilder out = new StringBuilder(text.length());
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			out.append(c == '|' ? ' ' : c);
		}
		// The breaks leave doubled spaces where the content broke after one.
		String flat = out.toString();
		while (flat.indexOf("  ") >= 0) {
			flat = flat.replace("  ", " ");
		}
		return flat.trim();
	}

	// ------------------------------------------------------------------ what a step says

	/** The clue's words, or what a map clue has instead of them. */
	static String wordsOf(int step) {
		if (step < 0) {
			return "";
		}
		String text = flatten(ClueSteps.TEXT[step]);
		if (text.length() > 0) {
			return text;
		}
		// A MAP CLUE HAS NO WORDS. Its text is a picture the client is handed only when the
		// scroll is opened, so the page says what it is and leans on the place below.
		return "Map clue";
	}

	/**
	 * Whether naming this step's person would only repeat its own words.
	 *
	 * HALF THE PEOPLE IN THE TABLE ARE ALREADY IN THE TEXT - "Speak to Ned in Draynor Village"
	 * sits beside WHO of "Ned" - and a second line saying "speak to Ned" under it is the page
	 * talking to itself. The ones worth printing are the ones the words hide: an anagram, or a
	 * riddle whose answer is a pun on a name.
	 */
	static boolean namesPerson(int step) {
		String who = ClueSteps.WHO[step];
		if (who.length() == 0) {
			return true;
		}
		return wordsOf(step).toLowerCase().indexOf(who.toLowerCase()) >= 0;
	}

	/**
	 * The second line: where to go, or who to find, and what is waiting there.
	 *
	 * Static and fed its numbers rather than reading the context, so the test can walk a player
	 * around a step without a client under it.
	 */
	static String guidance(int step, int fromX, int fromZ, int plane,
		boolean showDistance, boolean showAnswers, boolean showGuardian) {
		if (step < 0) {
			return "";
		}
		StringBuilder out = new StringBuilder();
		if (showAnswers && ClueSteps.ANSWER[step].length() > 0) {
			out.append("the answer is ").append(ClueSteps.ANSWER[step]);
		} else if (showAnswers && !namesPerson(step)) {
			out.append("speak to ").append(ClueSteps.WHO[step]);
		} else if (showDistance && ClueSteps.SPOT[step] != ClueSteps.SPOT_NONE) {
			int away = paces(fromX, fromZ, ClueSteps.X[step], ClueSteps.Z[step]);
			String way = bearing(fromX, fromZ, ClueSteps.X[step], ClueSteps.Z[step]);
			boolean dig = ClueSteps.SPOT[step] == ClueSteps.SPOT_DIG;
			if ("here".equals(way)) {
				out.append(dig ? "dig here" : "you are standing on it");
			} else {
				out.append(dig ? "dig " : "").append(away).append(" tiles ").append(way);
			}
			if (ClueSteps.LEVEL[step] != plane) {
				out.append(", on another floor");
			}
		}
		if (showGuardian && ClueSteps.GUARD[step].length() > 0) {
			if (out.length() > 0) {
				out.append(" - ");
			}
			out.append("a ").append(ClueSteps.GUARD[step]).append(" guards it");
		}
		return out.toString();
	}

	/** The reading on the right: the place itself, or the number a challenge wants. */
	static String reading(int step, boolean showAnswers) {
		if (step < 0) {
			return "";
		}
		if (ClueSteps.ANSWER[step].length() > 0) {
			return showAnswers ? ClueSteps.ANSWER[step] : "";
		}
		if (ClueSteps.SPOT[step] == ClueSteps.SPOT_NONE) {
			return "";
		}
		return ClueSteps.X[step] + ", " + ClueSteps.Z[step];
	}

	// ------------------------------------------------------------------ the page

	@Override
	protected void startUp() {
		try {
			this.addPanel("Clue", "scroll", new ConfigList() {

				public int size() {
					CluePlugin.this.bag = CluePlugin.this.carrying();
					CluePlugin.this.slots = clueSlots(CluePlugin.this.bag);
					return CluePlugin.this.slots.length;
				}

				public String label(int index) {
					return wordsOf(CluePlugin.this.stepOf(index));
				}

				public String detail(int index) {
					return CluePlugin.this.detail(index);
				}

				public String value(int index) {
					return reading(CluePlugin.this.stepOf(index), CluePlugin.this.showAnswers);
				}

				public jagex2.client.plugin.Sprite icon(int index) {
					int step = CluePlugin.this.stepOf(index);
					if (step < 0 || CluePlugin.this.ctx == null) {
						return null;
					}
					// From the snapshot's own bag, so the picture is the scroll whose words
					// are on the same row even if the bag moved in between.
					return CluePlugin.this.ctx.getObjIcon(ClueSteps.ID[step], 1);
				}

				public boolean removable(int index) {
					return false;
				}

				public String emptyMessage() {
					return "No clue scroll in your inventory.";
				}
			});
		} catch (Throwable olderClient) {
			// No rail pages in this client; nothing else here depends on one.
		}
	}

	/** The second line for a row, with the player's own position filled in. */
	String detail(int row) {
		int step = this.stepOf(row);
		if (step < 0 || this.ctx == null) {
			return "";
		}
		return guidance(step, this.ctx.getWorldX(), this.ctx.getWorldZ(), this.ctx.getPlane(),
			this.showDistance, this.showAnswers, this.showGuardian);
	}
}
