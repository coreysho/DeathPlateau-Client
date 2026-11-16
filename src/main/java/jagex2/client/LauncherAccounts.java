package jagex2.client;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * What the launcher's Discord sign-in left for this client: the characters linked to that Discord
 * account, and the token that says the launcher was signed in.
 *
 * WHY THE LOGIN SCREEN WANTS THIS. Saved logins are per-machine on purpose - LoginProfiles ties
 * them to this computer's account so a copied file is useless, which is wanted - and that leaves a
 * player with four characters typing names from memory on a new computer. The launcher asks the
 * website which names are linked to their Discord account and writes them here; the login screen
 * offers them as cards that fill the name in. The password is still typed, on every machine, the
 * first time.
 *
 * NAMES ARE NOT CREDENTIALS. Nothing in this file can sign in to anything. A card made from one of
 * these names behaves exactly like a saved profile that lost its token: it fills the name in and
 * puts the cursor in the password field. That is the whole of what the feature does, deliberately -
 * handing out something that could log in would make one Discord account a master key to every
 * character linked to it.
 *
 * THE TOKEN IS SENT WITH THE LOGIN, in the same RSA envelope as the password, and the server uses
 * it for one thing: to record the Discord link itself, so a launcher player never has to type
 * ::discord. It cannot stand in for a password - the server reads it from a different field and
 * does nothing with it until a login has already been proved.
 *
 * WHY THIS IS NOT THE LAUNCHER'S OWN DiscordSignIn, which already reads this file. The release
 * workflow compiles launcher/src on its own and the client's sources on their own; the client must
 * be able to draw its login screen without a class from the launcher being present. What the two
 * share is a file format with three keys in it, written down in both places.
 *
 * NOTHING HERE MAY THROW. No file, a half-written file, a hand-edited file and the browser client
 * (which has no home directory worth the name) are all "no launcher sign-in", which is the login
 * screen this client always had.
 */
public final class LauncherAccounts {

	/** Written by the launcher - see lostcity/DiscordSignIn.java - beside profiles.properties. */
	private static final String FILE_NAME = "launcher.properties";

	private static final String KEY_TOKEN = "discord.token";
	private static final String KEY_ACCOUNTS = "discord.accounts";

	/** The server's token shape, the same 20 characters LoginProfiles checks its own against. */
	private static final int TOKEN_LEN = 20;
	private static final String TOKEN_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789";

	/** A safe name: lowercase, digits and underscores, which is all the server ever sends. */
	private static boolean safeName(String name) {
		if (name.length() == 0 || name.length() > 12) {
			return false;
		}
		for (int i = 0; i < name.length(); i++) {
			char c = name.charAt(i);
			if ((c < 'a' || c > 'z') && (c < '0' || c > '9') && c != '_') {
				return false;
			}
		}
		return true;
	}

	private static boolean loaded;
	private static String token = "";
	private static final List<String> accounts = new ArrayList<String>();

	private LauncherAccounts() {
	}

	private static File file() {
		// Read at call time and never cached, so a test pointing user.home somewhere else is not
		// reading a developer's real sign-in. LoginProfiles does the same, for the same reason.
		return new File(new File(System.getProperty("user.home"), ".deathplateau"), FILE_NAME);
	}

	private static synchronized void load() {
		loaded = true;
		InputStream in = null;
		try {
			File f = file();
			if (!f.isFile()) {
				return;
			}
			in = new FileInputStream(f);
			Properties p = new Properties();
			p.load(in);
			String stored = p.getProperty(KEY_TOKEN, "").trim();
			token = looksLikeToken(stored) ? stored : "";
			String names = p.getProperty(KEY_ACCOUNTS, "");
			for (int at = 0; at < names.length(); ) {
				int comma = names.indexOf(',', at);
				String name = (comma < 0 ? names.substring(at) : names.substring(at, comma)).trim().toLowerCase();
				at = comma < 0 ? names.length() : comma + 1;
				// CHECKED ON THE WAY IN, not only where it is drawn: this is a text file a player
				// can edit, and what it says ends up in the username field of a real login.
				if (safeName(name) && !contains(name)) {
					accounts.add(name);
				}
			}
		} catch (Exception broken) {
			token = "";
			accounts.clear();
		} finally {
			try {
				if (in != null) {
					in.close();
				}
			} catch (Exception ignored) {
			}
		}
	}

	private static boolean contains(String name) {
		for (int i = 0; i < accounts.size(); i++) {
			if (accounts.get(i).equalsIgnoreCase(name)) {
				return true;
			}
		}
		return false;
	}

	/** Shape only. Whether the server still honours it is the server's to say. */
	public static boolean looksLikeToken(String value) {
		if (value == null || value.length() != TOKEN_LEN) {
			return false;
		}
		for (int i = 0; i < value.length(); i++) {
			if (TOKEN_CHARS.indexOf(value.charAt(i)) < 0) {
				return false;
			}
		}
		return true;
	}

	/** The launcher's sign-in, or "" when this client was not started from a signed-in launcher. */
	public static synchronized String token() {
		if (!loaded) {
			load();
		}
		return token;
	}

	/** The linked character names, oldest link first - the order the server sends them in. */
	public static synchronized List<String> accounts() {
		if (!loaded) {
			load();
		}
		return new ArrayList<String>(accounts);
	}

	/** The tests reload between cases; the client reads the file once and keeps it. */
	static synchronized void reload() {
		loaded = false;
		token = "";
		accounts.clear();
	}
}
