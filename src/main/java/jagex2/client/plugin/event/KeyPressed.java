package jagex2.client.plugin.event;

/**
 * A key came off the client's key queue, before the game looks at it. Codes are the client's own,
 * not AWT's: printable keys are their character, and the rest are the constants on GameShell
 * (KEY_ESCAPE, KEY_ALT) or the client's F-key range, 1008 for F1 up to 1019 for F12.
 *
 * {@link #consume()} swallows the key so the game never sees it, which is what a plugin with a
 * hotkey wants - otherwise its key also goes into the chat box. Keys only reach here while in game,
 * and never while a client panel (settings, swaps, ground items, plugins) is open and eating input.
 */
public final class KeyPressed {

	public final int key;

	private boolean consumed;

	public KeyPressed(int key) {
		this.key = key;
	}

	public void consume() {
		this.consumed = true;
	}

	public boolean isConsumed() {
		return this.consumed;
	}
}
