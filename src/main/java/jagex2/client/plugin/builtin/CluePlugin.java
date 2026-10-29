package jagex2.client.plugin.builtin;

import jagex2.client.plugin.ConfigItem;
import jagex2.client.plugin.ConfigList;
import jagex2.client.plugin.Plugin;
import jagex2.client.plugin.PluginDescriptor;

/**
 * The clue scroll in your bag: what it says, and where it wants you.
 *
 * NO SERVER CHANGE, UNLIKE THE OTHER THREE, and the reason is worth writing down. Every clue
 * step in this game is its OWN OBJECT - 107 of them - and each carries what it says and where
 * it points as parameters on itself. So the client does not need to be told which step somebody
 * is on: it can see the scroll in the inventory and that scroll IS the step. Nothing had to be
 * transmitted, and no varp was added.
 *
 * WHAT THE TABLE IS FOR. Those params are server-side - this cache has never heard of
 * trail_desc - so the text and the coordinate are generated into {@link ClueSteps} from the
 * content, and the test regenerates and compares. A clue added there and not here is a failing
 * test rather than a scroll the page has nothing to say about.
 *
 * A HELPER AND NOT A SOLVER. It shows what the scroll already tells the player, in a place they
 * can read while walking, and for the 38 coordinate clues it works out the compass bearing and
 * distance - arithmetic on two numbers the player has both of. It does not walk anywhere, dig,
 * or click: no plugin here does, and a clue that solved itself would not be a clue.
 */
@PluginDescriptor(
	name = "Clue scroll",
	description = "What your clue says and how far off the dig spot is",
	key = "clue-scroll",
	apiLevel = 10
)
public final class CluePlugin extends Plugin {

	@ConfigItem(keyName = "showDistance", name = "Show the bearing and distance",
		description = "For coordinate clues, how far off you are and which way.")
	public boolean showDistance = true;

	// ------------------------------------------------------------------ finding the scroll

	/**
	 * The slot holding a clue, or -1 for none.
	 *
	 * THE FIRST ONE FOUND, in slot order, which matters because a player can carry an easy, a
	 * medium and a hard at once. Slot order is what they arranged, so the one nearest the top
	 * left is the one they are most likely working on - and the page says which it is by name.
	 */
	int clueSlot() {
		if (this.ctx == null) {
			return -1;
		}
		int slots = this.ctx.getInventorySize();
		for (int slot = 0; slot < slots; slot++) {
			if (ClueSteps.indexOf(this.ctx.getInventoryObjId(slot)) >= 0) {
				return slot;
			}
		}
		return -1;
	}

	/** Where in the table this player's clue sits, or -1 when they are carrying none. */
	int step() {
		int slot = this.clueSlot();
		return slot < 0 ? -1 : ClueSteps.indexOf(this.ctx.getInventoryObjId(slot));
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

	// ------------------------------------------------------------------ the page

	@Override
	protected void startUp() {
		try {
			this.addPanel("Clue", "scroll", new ConfigList() {

				public int size() {
					return CluePlugin.this.step() >= 0 ? 1 : 0;
				}

				public String label(int index) {
					return CluePlugin.this.label();
				}

				public String detail(int index) {
					return CluePlugin.this.detail();
				}

				public String value(int index) {
					return CluePlugin.this.value();
				}

				public jagex2.client.plugin.Sprite icon(int index) {
					int slot = CluePlugin.this.clueSlot();
					return slot < 0 ? null
						: CluePlugin.this.ctx.getObjIcon(CluePlugin.this.ctx.getInventoryObjId(slot), 1);
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

	/** The clue's words, or what a map clue has instead of them. */
	String label() {
		int step = this.step();
		if (step < 0) {
			return "";
		}
		String text = flatten(ClueSteps.TEXT[step]);
		if (text.length() > 0) {
			return text;
		}
		// A MAP CLUE HAS NO WORDS. Its text is a picture the client is handed only when the
		// scroll is opened, so the page says what it is and leans on the coordinate below.
		return "Map clue";
	}

	/** The bearing and distance, for the coordinate clues that have one. */
	String detail() {
		int step = this.step();
		if (step < 0 || !this.showDistance || this.ctx == null) {
			return "";
		}
		if (ClueSteps.X[step] < 0) {
			return "";
		}
		int toX = ClueSteps.X[step];
		int toZ = ClueSteps.Z[step];
		int away = paces(this.ctx.getWorldX(), this.ctx.getWorldZ(), toX, toZ);
		String way = bearing(this.ctx.getWorldX(), this.ctx.getWorldZ(), toX, toZ);
		if (ClueSteps.LEVEL[step] != this.ctx.getPlane()) {
			return away + " tiles " + way + ", on another floor";
		}
		return "here".equals(way) ? "you are standing on it" : away + " tiles " + way;
	}

	/** The dig spot itself, so a player can read it off rather than pace it out. */
	String value() {
		int step = this.step();
		if (step < 0 || ClueSteps.X[step] < 0) {
			return "";
		}
		return ClueSteps.X[step] + ", " + ClueSteps.Z[step];
	}
}
