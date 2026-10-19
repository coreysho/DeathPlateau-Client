/*
 * Headless test for the saved logins behind the login screen's profile row.
 *
 * WHAT THIS GUARDS. This file holds people's passwords. Three things have to hold and none of
 * them are visible by looking at the login screen:
 *
 *   * a password must never reach the disk in readable text. The encryption is obfuscation and
 *     the class says so, but "obfuscated" and "accidentally written in the clear" are different
 *     failures, and only the second one is silent. So the test reads the actual bytes of the
 *     actual file and asserts the password is not in them;
 *
 *   * a file that will not decrypt - copied from another machine, hand-edited, half-written -
 *     must cost the PASSWORD and keep the ACCOUNT. Dropping the profile instead would look to a
 *     player like the client forgot their character existed;
 *
 *   * nothing may throw. This runs while the login screen is drawing. An exception here is a
 *     client that cannot reach its own login form.
 *
 * The list rules matter less but break more often: one entry per account however many times they
 * sign in, matched without case because the server lowercases a name into base37, most recent
 * first because that is the order the row draws, and a cap so the row cannot overflow.
 */
package jagex2.client;

import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.util.List;

public class LoginProfilesTest {

	static int fails;

	static void check(boolean ok, String what) {
		System.out.println((ok ? "  ok   " : "FAIL   ") + what);
		if (!ok) {
			fails++;
		}
	}

	static File home;

	/** A scratch home per case, so no case can see the one before it except on purpose. */
	static void freshHome(String name) {
		home = new File(System.getProperty("java.io.tmpdir"), "dp-profiles-" + name + "-" + System.nanoTime());
		new File(home, ".deathplateau").mkdirs();
		System.setProperty("user.home", home.getAbsolutePath());
		LoginProfiles.reload();
	}

	static File profileFile() {
		return new File(new File(home, ".deathplateau"), "profiles.properties");
	}

	static String fileText() {
		try {
			File f = profileFile();
			if (!f.exists()) {
				return "";
			}
			RandomAccessFile raf = new RandomAccessFile(f, "r");
			byte[] b = new byte[(int) raf.length()];
			raf.readFully(b);
			raf.close();
			return new String(b, "UTF-8");
		} catch (Exception e) {
			return "";
		}
	}

	static String names() {
		StringBuilder sb = new StringBuilder();
		for (LoginProfiles.Profile p : LoginProfiles.all()) {
			if (sb.length() > 0) {
				sb.append(',');
			}
			sb.append(p.username);
		}
		return sb.toString();
	}

	public static void main(String[] args) {
		System.setProperty("user.name", "tester");

		System.out.println("1. an empty machine");
		emptyTests();
		System.out.println();
		System.out.println("2. remembering an account");
		rememberTests();
		System.out.println();
		System.out.println("3. the password never lands in readable text");
		secrecyTests();
		System.out.println();
		System.out.println("4. one row per account, newest first");
		listTests();
		System.out.println();
		System.out.println("5. forgetting");
		forgetTests();
		System.out.println();
		System.out.println("6. a file that will not decrypt keeps the account");
		damageTests();
		System.out.println();
		System.out.println("7. rubbish in, no exception out");
		junkTests();

		System.out.println();
		if (fails == 0) {
			System.out.println("all checks passed");
		} else {
			System.out.println(fails + " CHECK(S) FAILED");
		}
		System.exit(fails == 0 ? 0 : 1);
	}

	static void emptyTests() {
		freshHome("empty");
		check(LoginProfiles.all().isEmpty(), "no file means no profiles");
		check(!profileFile().exists(), "and reading does not create one");
	}

	static void rememberTests() {
		freshHome("remember");
		LoginProfiles.remember("Corey", "hunter2", true);
		List<LoginProfiles.Profile> list = LoginProfiles.all();
		check(list.size() == 1, "one profile after one login");
		check(list.get(0).username.equals("Corey"), "the name is kept as it was typed");
		check(list.get(0).secret.equals("hunter2"), "the password round trips");
		check(list.get(0).canSignIn(), "so the profile can sign in on its own");
		check(list.get(0).lastUsed > 0L, "and it is stamped with when");

		// The whole point of the opt-out: the account stays on the screen, the password does not.
		freshHome("nokeep");
		LoginProfiles.remember("Corey", "hunter2", false);
		List<LoginProfiles.Profile> kept = LoginProfiles.all();
		check(kept.size() == 1, "declining to keep the password still saves the account");
		check(kept.get(0).secret.length() == 0, "with no password");
		check(!kept.get(0).canSignIn(), "so clicking it fills the name in instead");
		check(!fileText().contains("hunter2"), "and the declined password is nowhere in the file");

		// Survives the client closing.
		freshHome("persist");
		LoginProfiles.remember("Corey", "hunter2", true);
		LoginProfiles.reload();
		List<LoginProfiles.Profile> after = LoginProfiles.all();
		check(after.size() == 1 && after.get(0).secret.equals("hunter2"), "a saved password is there next launch");
	}

	static void secrecyTests() {
		freshHome("secrecy");
		LoginProfiles.remember("Corey", "hunter2", true);
		String text = fileText();
		check(text.length() > 0, "the file is written");
		check(text.contains("Corey"), "the username is readable, which is the point of the row");
		check(!text.contains("hunter2"), "the PASSWORD is not in the file in readable text");
		check(text.toLowerCase().contains("obfuscated"), "and the file says what it is, for whoever opens it");

		// A fresh IV per write. Without it two accounts sharing a password produce identical
		// lines, which tells anyone reading the file that they match.
		//
		// BOTH ACCOUNTS GO IN THE SAME FILE ON PURPOSE, and the earlier version of this check did
		// not. It called freshHome() between them, which moves user.home - an input to the KEY.
		// Two different keys produce two different ciphertexts whatever the IV does, so the check
		// passed with a hardcoded IV and was testing nothing. One home, one key, two writes is
		// the only arrangement where the IV is the variable.
		freshHome("iv");
		LoginProfiles.remember("alice", "samepass", true);
		LoginProfiles.remember("bob", "samepass", true);
		String text2 = fileText();
		String first = secretLine(text2, 0);
		String second = secretLine(text2, 1);
		check(first.length() > 0 && second.length() > 0, "both accounts stored a secret");
		check(!first.equals(second), "the same password under one key encrypts differently each time");
	}

	static String secretLine(String text, int index) {
		String want = "profile." + index + ".secret=";
		for (String line : text.split("\n")) {
			if (line.trim().startsWith(want)) {
				return line.trim().substring(want.length());
			}
		}
		return "";
	}

	static void listTests() {
		freshHome("list");
		LoginProfiles.remember("alice", "a", true);
		LoginProfiles.remember("bob", "b", true);
		check(names().equals("bob,alice"), "most recently used comes first");

		LoginProfiles.remember("alice", "a2", true);
		check(names().equals("alice,bob"), "signing in again moves an account to the front");
		check(LoginProfiles.all().size() == 2, "and does not add a second row for it");
		check(LoginProfiles.all().get(0).secret.equals("a2"), "with the password just used");

		// "Corey" and "corey" are one account on the server, so they are one row here.
		LoginProfiles.remember("ALICE", "a3", true);
		check(LoginProfiles.all().size() == 2, "a name in different case is the same account");
		check(LoginProfiles.all().get(0).username.equals("ALICE"), "kept as most recently typed");

		freshHome("cap");
		LoginProfiles.remember("one", "p", true);
		LoginProfiles.remember("two", "p", true);
		LoginProfiles.remember("three", "p", true);
		LoginProfiles.remember("four", "p", true);
		LoginProfiles.remember("five", "p", true);
		check(LoginProfiles.all().size() == LoginProfiles.MAX, "the list stops at MAX");
		check(names().equals("five,four,three,two"), "and it is the longest-unused that goes");
	}

	static void forgetTests() {
		freshHome("forget");
		LoginProfiles.remember("alice", "a", true);
		LoginProfiles.remember("bob", "b", true);
		LoginProfiles.forget("alice");
		check(names().equals("bob"), "the x removes that account");
		check(!fileText().contains("alice"), "and takes it out of the file");

		LoginProfiles.forget("nobody");
		check(names().equals("bob"), "forgetting an account that is not there changes nothing");

		LoginProfiles.forget("BOB");
		check(LoginProfiles.all().isEmpty(), "forgetting ignores case too");
	}

	static void damageTests() {
		// The file carried to another machine, or to another user on this one. The key is derived
		// from the account, so it no longer decrypts.
		freshHome("moved");
		LoginProfiles.remember("Corey", "hunter2", true);
		System.setProperty("user.name", "somebody-else");
		LoginProfiles.reload();
		List<LoginProfiles.Profile> moved = LoginProfiles.all();
		check(moved.size() == 1, "a file from another machine still lists the account");
		check(moved.get(0).username.equals("Corey"), "under the right name");
		check(moved.get(0).secret.length() == 0, "but the password is gone, not wrongly decrypted");
		check(!moved.get(0).canSignIn(), "so the player is asked for it");
		System.setProperty("user.name", "tester");

		// Hand-edited, truncated, or a half-finished write.
		freshHome("mangled");
		write(profileFile(), "profile.0.name=Corey\nprofile.0.secret=not-base64-at-all!!\nprofile.0.last=1\n");
		LoginProfiles.reload();
		List<LoginProfiles.Profile> mangled = LoginProfiles.all();
		check(mangled.size() == 1 && mangled.get(0).username.equals("Corey"), "a mangled secret keeps the account");
		check(mangled.get(0).secret.length() == 0, "and drops the password");

		freshHome("short");
		write(profileFile(), "profile.0.name=Corey\nprofile.0.secret=AAAA\nprofile.0.last=1\n");
		LoginProfiles.reload();
		check(LoginProfiles.all().size() == 1, "a secret too short to hold an IV keeps the account");
		check(LoginProfiles.all().get(0).secret.length() == 0, "and drops the password");
	}

	static void junkTests() {
		freshHome("junk");
		LoginProfiles.remember(null, "p", true);
		check(LoginProfiles.all().isEmpty(), "a null username is ignored");
		LoginProfiles.remember("   ", "p", true);
		check(LoginProfiles.all().isEmpty(), "so is an empty one");
		LoginProfiles.remember("thisnameisfartoolong", "p", true);
		check(LoginProfiles.all().isEmpty(), "so is one longer than the login field allows");
		LoginProfiles.remember("ok", null, true);
		check(LoginProfiles.all().size() == 1, "a null password still saves the account");
		check(LoginProfiles.all().get(0).secret.length() == 0, "with no password");

		freshHome("binary");
		write(profileFile(), "\u0000\u0001 not a properties file at all ÿ\nprofile.=\n=\n");
		LoginProfiles.reload();
		check(LoginProfiles.all().isEmpty(), "a file that is not a properties file reads as empty");
		LoginProfiles.remember("after", "p", true);
		check(LoginProfiles.all().size() == 1, "and the client can still save over it");

		freshHome("overlong-stored");
		write(profileFile(), "profile.0.name=Corey\nprofile.0.last=1\nprofile.0.secret=\n");
		LoginProfiles.reload();
		check(LoginProfiles.all().size() == 1, "an entry with no secret at all is fine");
	}

	static void write(File f, String text) {
		try {
			f.getParentFile().mkdirs();
			FileOutputStream out = new FileOutputStream(f);
			out.write(text.getBytes("UTF-8"));
			out.close();
		} catch (Exception e) {
			check(false, "could not write the test file: " + e);
		}
	}
}
