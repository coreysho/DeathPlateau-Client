package jagex2.client.plugin.event;

/**
 * An option was clicked and is about to be acted on, before the client sends anything to the
 * server. A handler may call {@link #consume()} to stop it, which is how a plugin blocks an action
 * (a "are you sure" guard on dropping something valuable, say).
 *
 * Consuming does not undo the click - the menu still closes - it only stops the action being
 * performed and the packet being sent.
 */
public final class MenuOptionClicked {

	/** The option's text, tags and all, e.g. "Attack @yel@Man@whi@  (level-2)". */
	public final String option;

	/** The client's action id for the entry. */
	public final int action;

	/** The entry's three parameters; what they mean depends on the action. */
	public final int paramA;
	public final int paramB;
	public final int paramC;

	private boolean consumed;

	public MenuOptionClicked(String option, int action, int paramA, int paramB, int paramC) {
		this.option = option;
		this.action = action;
		this.paramA = paramA;
		this.paramB = paramB;
		this.paramC = paramC;
	}

	/** Stop the client acting on this click. */
	public void consume() {
		this.consumed = true;
	}

	public boolean isConsumed() {
		return this.consumed;
	}
}
