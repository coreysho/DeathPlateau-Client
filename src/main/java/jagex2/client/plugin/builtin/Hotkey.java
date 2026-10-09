package jagex2.client.plugin.builtin;

/**
 * A key a player typed into a settings box, as the code the client sends.
 *
 * Two plugins want the same parse - Ground items, to hide and show its labels, and Anti-drag, to
 * suspend itself for a moment - and one implementation is the point. Two readings of "F3" would
 * eventually differ by one, and a hotkey that fires the wrong key is indistinguishable from a
 * hotkey that does nothing.
 *
 * WHAT A SETTING MAY SAY: one character, or F1 to F12. Anything else is no hotkey, including an
 * empty box, a word, and an F-number out of range. Nothing here throws: it is read from a key
 * handler on the game thread.
 */
final class Hotkey {

	/** No key. What an empty or unreadable setting comes back as. */
	static final int NONE = -1;

	/** The client's code for F1. F2 is one past it, and so on to F12. */
	private static final int F1 = 1008;

	private static final int FUNCTION_KEYS = 12;

	private Hotkey() {
	}

	/** The key code a setting names, or {@link #NONE}. */
	static int code(String setting) {
		if (setting == null) {
			return NONE;
		}
		String text = setting.trim();
		// No length-0 check: a blank setting falls through to the "exactly one character" test
		// below and comes back as no hotkey. The audit found such a guard could be deleted with
		// nothing noticing, which is how a redundant check announces itself.
		if (text.length() >= 2 && (text.charAt(0) == 'F' || text.charAt(0) == 'f')) {
			try {
				int n = Integer.parseInt(text.substring(1));
				if (n >= 1 && n <= FUNCTION_KEYS) {
					return F1 - 1 + n;
				}
			} catch (RuntimeException notANumber) {
				return NONE;
			}
			return NONE;
		}
		if (text.length() != 1) {
			return NONE;
		}
		// Lower case, because that is what the client sends for an unshifted letter and "G" is
		// what a player types.
		return Character.toLowerCase(text.charAt(0));
	}

	/** Whether this key press is the one a setting names. */
	static boolean pressed(String setting, int keyCode) {
		int want = code(setting);
		return want != NONE && keyCode == want;
	}
}
