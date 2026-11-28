package jagex2.client.plugin.event;

import java.util.Collections;
import java.util.List;

/**
 * The server says this player has just been given loot: what from, and what.
 *
 * TOLD, NOT GUESSED. The client has no death event and cannot see who killed what, so the first
 * loot tracker worked a kill out from an npc vanishing near a fresh pile on the floor - and got it
 * wrong whenever somebody else's kill was next to yours, whenever a second drop landed on a tile
 * that already held the same item, and whenever coins merged into a stack. The server knows all of
 * it at the moment it rolls the drop, so now it says so, in a hidden game message the client
 * swallows before the chatbox sees it (see Client's MESSAGE_GAME handler).
 *
 * ONE EVENT PER KILL, with every item that kill dropped, which is RuneLite's NpcLootReceived:
 * a kill and its drop arrive together, so a tracker never has to decide which item belongs to
 * which kill.
 */
public final class LootReceived {

	/** An npc's drop. {@link #name} is the npc's name and {@link #combatLevel} its level. */
	public static final String NPC = "npc";

	/** What a player left behind when you killed them. */
	public static final String PLAYER = "player";

	/** Anything that is not a kill: a clue casket, a chest, a reward. {@link #combatLevel} is 0. */
	public static final String EVENT = "event";

	/** One kind of item and how many of it. */
	public static final class Item {

		public final int id;
		public final int count;

		public Item(int id, int count) {
			this.id = id;
			this.count = count;
		}
	}

	/** {@link #NPC}, {@link #PLAYER} or {@link #EVENT}. */
	public final String type;

	/** What it came from, as the game names it: "Black dragon", "Clue scroll (hard)". */
	public final String name;

	/** The npc's or player's combat level, or 0 when there is none. */
	public final int combatLevel;

	/** What it dropped, never null, possibly empty - a kill with nothing on its table still counts. */
	public final List<Item> items;

	public LootReceived(String type, String name, int combatLevel, List<Item> items) {
		this.type = type == null ? NPC : type;
		this.name = name == null ? "" : name;
		this.combatLevel = combatLevel;
		this.items = items == null ? Collections.<Item>emptyList() : Collections.unmodifiableList(items);
	}
}
