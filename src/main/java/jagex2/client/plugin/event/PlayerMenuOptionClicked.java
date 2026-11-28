package jagex2.client.plugin.event;

/**
 * A player clicked a menu row that a plugin put there with
 * {@link jagex2.client.plugin.Plugin#addPlayerMenuOption(String)} - "Lookup" on somebody.
 *
 * THE NAME, NOT AN INDEX. The same row is offered on a player standing in the world, on a name in
 * the chatbox and on one in the friends list, and only the first of those has an entry in the
 * client's player table at all. A name is the one thing all three have, and it is what every use
 * of a row like this wants anyway.
 *
 * ONLY THE PLUGINS' OWN ROWS COME HERE. A row a plugin adds does nothing in the client - it sends
 * nothing to the server and moves nobody - so this event is the whole of what clicking it does.
 */
public final class PlayerMenuOptionClicked {

	/** The word on the row, as the plugin registered it: "Lookup". */
	public final String option;

	/** The player it was clicked on, as the game writes the name: "Iron Zezima". Never null. */
	public final String playerName;

	public PlayerMenuOptionClicked(String option, String playerName) {
		this.option = option == null ? "" : option;
		this.playerName = playerName == null ? "" : playerName;
	}
}
