package jagex2.client.plugin.event;

/**
 * A line arriving in the chatbox. Posted from Client.addMessage, the one choke point every kind of
 * message funnels through: public chat, private messages in and out, npc dialogue, game messages,
 * trade and duel requests.
 *
 * The message is reported, not offered for editing - by the time this fires the line is already on
 * its way into the chat buffer. A plugin reacts to what was said; it does not rewrite it.
 */
public final class ChatMessage {

	/** Who said it, or "" for a game message. May still carry @cr@ icon tags. */
	public final String sender;

	/** The text, with its colour/icon tags still in it. */
	public final String message;

	/** The client's message type: 0 game, 2 public, 3 private in, 6 private out, and so on. */
	public final int type;

	public ChatMessage(String sender, String message, int type) {
		this.sender = sender;
		this.message = message;
		this.type = type;
	}
}
