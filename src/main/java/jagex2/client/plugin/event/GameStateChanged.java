package jagex2.client.plugin.event;

/**
 * The client moved between the login screen and the game. A plugin that keeps per-session state
 * (a session xp counter, a list of what it has seen) resets on LOGGED_IN and stops its work on
 * LOGIN_SCREEN.
 */
public final class GameStateChanged {

	public static final int LOGIN_SCREEN = 0;
	public static final int LOGGED_IN = 1;

	public final int state;

	public GameStateChanged(int state) {
		this.state = state;
	}

	public boolean isLoggedIn() {
		return this.state == LOGGED_IN;
	}
}
