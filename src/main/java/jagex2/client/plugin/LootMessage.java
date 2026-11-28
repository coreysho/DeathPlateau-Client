package jagex2.client.plugin;

import java.util.ArrayList;
import java.util.List;

import jagex2.client.plugin.event.LootReceived;

/**
 * The server's hidden loot line, read back into a {@link LootReceived}.
 *
 * WHY A GAME MESSAGE. The client already has a convention for game messages that are not words
 * for a person - ":tradereq:", ":duelreq:" - and this is one more of them, so the server needed
 * no new packet and the protocol no new opcode. The engine sends one only to a client that asked
 * for them (the "lootfeed" line the client sends at login), so an older client is never shown one.
 *
 * <pre>
 *   type | kill | more | level | name | id,count;id,count;... :loot:
 *   n|4821|0|83|Black dragon|536,1;1747,1;995,196:loot:
 * </pre>
 *
 * type is n (npc), p (player) or e (event: a casket, a chest). kill is a number the server picks
 * per kill. more is 1 when the next line carries more of the same kill - a game message is at most
 * 255 bytes, so a big drop is split, and the pieces are joined here by kill rather than counted as
 * kills of their own. The name is everything between the fourth bar and the last one, so a name
 * with a bar in it still reads.
 *
 * NOTHING HERE TRUSTS THE LINE. A part that does not parse is skipped rather than thrown, a count
 * of zero or less is no item, and a kill that never sends its last piece is dropped when the next
 * kill starts rather than held forever.
 */
public final class LootMessage {

	/** The tail that marks one, in the same shape as the client's ":tradereq:". */
	public static final String SUFFIX = ":loot:";

	/** The most items one kill may carry, so a broken stream cannot grow a list without end. */
	static final int MAX_ITEMS = 512;

	private int pendingKill = -1;
	private String pendingType;
	private String pendingName;
	private int pendingLevel;
	private List<LootReceived.Item> pendingItems;

	public static boolean isLoot(String text) {
		return text != null && text.endsWith(SUFFIX);
	}

	/**
	 * Reads one line. Returns the whole kill once its last piece is in, null while more is coming
	 * or when the line is not one at all.
	 */
	public LootReceived read(String text) {
		if (!isLoot(text)) {
			return null;
		}
		String body = text.substring(0, text.length() - SUFFIX.length());
		String[] head = body.split("\\|", 5);
		if (head.length < 5) {
			return null;
		}
		String type = typeOf(head[0]);
		int kill = parse(head[1], -1);
		boolean more = "1".equals(head[2]);
		int level = parse(head[3], 0);
		String rest = head[4];
		int bar = rest.lastIndexOf('|');
		if (type == null || bar < 0) {
			return null;
		}
		String name = rest.substring(0, bar);
		String items = rest.substring(bar + 1);

		if (kill != this.pendingKill || this.pendingItems == null) {
			// A new kill. Whatever was half-read before it lost its last piece and goes.
			this.pendingKill = kill;
			this.pendingType = type;
			this.pendingName = name;
			this.pendingLevel = level;
			this.pendingItems = new ArrayList<LootReceived.Item>();
		}
		readItems(items, this.pendingItems);
		if (more) {
			return null;
		}
		LootReceived done = new LootReceived(this.pendingType, this.pendingName, this.pendingLevel,
			this.pendingItems);
		this.pendingKill = -1;
		this.pendingItems = null;
		return done;
	}

	private static String typeOf(String code) {
		if ("n".equals(code)) {
			return LootReceived.NPC;
		}
		if ("p".equals(code)) {
			return LootReceived.PLAYER;
		}
		if ("e".equals(code)) {
			return LootReceived.EVENT;
		}
		return null;
	}

	private static void readItems(String items, List<LootReceived.Item> into) {
		if (items.length() == 0) {
			return;
		}
		String[] parts = items.split(";");
		for (int i = 0; i < parts.length && into.size() < MAX_ITEMS; i++) {
			int comma = parts[i].indexOf(',');
			if (comma <= 0) {
				continue;
			}
			int id = parse(parts[i].substring(0, comma), -1);
			int count = parse(parts[i].substring(comma + 1), 0);
			if (id >= 0 && count > 0) {
				into.add(new LootReceived.Item(id, count));
			}
		}
	}

	private static int parse(String text, int fallback) {
		try {
			return Integer.parseInt(text.trim());
		} catch (NumberFormatException e) {
			return fallback;
		}
	}
}
