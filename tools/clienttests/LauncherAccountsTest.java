/*
 * Headless test for the cards the launcher's Discord sign-in puts on the login screen.
 *
 * WHAT THIS GUARDS, and none of it is visible from the login screen:
 *
 *   * A LINKED NAME IS NOT A LOGIN. A card offered because somebody's Discord says they own that
 *     character must behave exactly like a saved profile with no token: fill the name in, ask for
 *     the password. canSignIn() returning true for one of these would be the whole feature turning
 *     into the version that was refused - a Discord account as a master key;
 *
 *   * THE ROW MUST NOT DOUBLE UP. The names from the server are safe names ("lord_fooby") and the
 *     ones in profiles.properties are as the player typed them ("Lord Fooby"). Matched with case,
 *     a player would see the same character twice, once able to sign in and once not;
 *
 *   * SAVED ONES COME FIRST AND THE CAP STILL HOLDS. The row is drawn for four cards and an add
 *     button. A launcher card pushing a one-click saved login off the end would be a feature making
 *     the login screen worse for the player it works best for;
 *
 *   * THEY ARE NOT WRITTEN TO profiles.properties. That file is "the accounts on this machine";
 *     one of these is "an account your Discord says is yours". Written down it would outlive the
 *     sign-in and show the next person to use the computer somebody else's character names;
 *
 *   * nothing throws. This runs while the login screen is drawing.
 */
package jagex2.client;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class LauncherAccountsTest {

	static int fails;

	static final String TOKEN = "a1b2c3d4e5f6g7h8i9j0";

	static void check(boolean ok, String what) {
		System.out.println((ok ? "  ok   " : "FAIL   ") + what);
		if (!ok) {
			fails++;
		}
	}

	static File home;

	static void freshHome(String name) {
		home = new File(System.getProperty("java.io.tmpdir"), "dp-launcheracc-" + name + "-" + System.nanoTime());
		new File(home, ".deathplateau").mkdirs();
		System.setProperty("user.home", home.getAbsolutePath());
		LoginProfiles.reload();
		LauncherAccounts.reload();
	}

	static File dir() {
		return new File(home, ".deathplateau");
	}

	/** The file the launcher writes. Written here by hand, which is the contract between the two. */
	static void launcherFile(String token, String accounts) throws Exception {
		OutputStream out = new FileOutputStream(new File(dir(), "launcher.properties"));
		out.write(("discord.token=" + token + "\ndiscord.accounts=" + accounts + "\n").getBytes(StandardCharsets.UTF_8));
		out.close();
		LauncherAccounts.reload();
	}

	static String text(File f) throws Exception {
		byte[] bytes = new byte[(int) f.length()];
		java.io.InputStream in = new java.io.FileInputStream(f);
		try {
			int at = 0;
			while (at < bytes.length) {
				int n = in.read(bytes, at, bytes.length - at);
				if (n < 0) {
					break;
				}
				at += n;
			}
		} finally {
			in.close();
		}
		return new String(bytes, StandardCharsets.UTF_8);
	}

	/**
	 * THROUGH BASE 37, like cards() itself. Written with equalsIgnoreCase first, and it could not
	 * find the saved "Lord Fooby" when asked for "lord_fooby" - which is the very mistake these
	 * cases are here to catch, made in the test instead of in the code.
	 */
	static LoginProfiles.Profile card(List<LoginProfiles.Profile> cards, String name) {
		long id = jagex2.jstring.JString.toBase37(name);
		for (int i = 0; i < cards.size(); i++) {
			if (jagex2.jstring.JString.toBase37(cards.get(i).username) == id) {
				return cards.get(i);
			}
		}
		return null;
	}

	public static void main(String[] args) throws Exception {
		System.out.println("-- nothing signed in");
		freshHome("none");
		check(LauncherAccounts.token().length() == 0, "no file is no token");
		check(LauncherAccounts.accounts().isEmpty(), "and no names");
		check(LoginProfiles.cards().isEmpty(), "so the row is the one the client always had");

		System.out.println("-- the linked characters are offered");
		freshHome("offered");
		launcherFile(TOKEN, "lord_fooby,corey");
		check(TOKEN.equals(LauncherAccounts.token()), "the token is read back");
		List<LoginProfiles.Profile> cards = LoginProfiles.cards();
		check(cards.size() == 2, "both characters are on the row");
		LoginProfiles.Profile first = card(cards, "lord_fooby");
		check(first != null && first.fromLauncher, "and marked as the launcher's");
		check(first != null && !first.canSignIn(), "A LINKED CARD CANNOT SIGN IN - it asks for the password");
		check(first != null && !first.hasFace(), "and has no face, having never been seen on this computer");

		System.out.println("-- a saved account and a linked one are the same account");
		freshHome("dedupe");
		LoginProfiles.remember("Lord Fooby");
		LoginProfiles.rememberToken("Lord Fooby", TOKEN);
		launcherFile(TOKEN, "lord_fooby,corey");
		cards = LoginProfiles.cards();
		check(cards.size() == 2, "the row has two cards, not three: " + cards.size());
		LoginProfiles.Profile saved = card(cards, "lord_fooby");
		check(saved != null && saved.canSignIn() && !saved.fromLauncher,
			"the saved one wins, keeping its one-click login and the spelling the player typed");
		check(saved != null && saved.username.equals("Lord Fooby"), "...which is \"Lord Fooby\": " + (saved == null ? "-" : saved.username));

		System.out.println("-- saved first, and the cap still holds");
		freshHome("cap");
		LoginProfiles.remember("alice");
		LoginProfiles.remember("bob");
		launcherFile(TOKEN, "carol,dave,erin,frank,grace");
		cards = LoginProfiles.cards();
		check(cards.size() == LoginProfiles.MAX, "four cards, which is what the row is drawn for: " + cards.size());
		check(!cards.get(0).fromLauncher && !cards.get(1).fromLauncher, "the saved ones are first");
		check(cards.get(2).fromLauncher && cards.get(3).fromLauncher, "and the launcher fills what is left");
		check(card(cards, "grace") == null, "a Discord account with more characters than the row has them left off");

		System.out.println("-- nothing of this is saved on the machine");
		freshHome("notsaved");
		launcherFile(TOKEN, "lord_fooby");
		LoginProfiles.cards();
		File profiles = new File(dir(), "profiles.properties");
		check(!profiles.isFile() || !text(profiles).contains("lord_fooby"),
			"drawing the row does not write a linked name into profiles.properties");
		LoginProfiles.forget("lord_fooby");
		check(LoginProfiles.cards().size() == 1, "and forget() cannot remove one - there is nothing here to remove");

		System.out.println("-- a hand-edited file");
		freshHome("edited");
		launcherFile("nonsense", "lord_fooby");
		check(LauncherAccounts.token().length() == 0, "a token of the wrong shape is no token");
		check(LoginProfiles.cards().size() == 1, "...but the names still read, because they are only names");
		launcherFile(TOKEN, "../../etc/passwd,LORD FOOBY,waytoolongaccountname,bob");
		List<String> names = LauncherAccounts.accounts();
		check(names.size() == 1 && names.get(0).equals("bob"), "only a safe name reaches the login screen: " + names);

		System.out.println(fails == 0 ? "all ok" : fails + " FAILED");
		System.exit(fails == 0 ? 0 : 1);
	}
}
