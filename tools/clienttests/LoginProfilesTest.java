/*
 * Headless test for the saved logins behind the login screen's profile row.
 *
 * WHAT THIS GUARDS. This file is what a player's saved logins are kept in, and four things have to
 * hold that are all invisible from the login screen:
 *
 *   * A PASSWORD MUST NEVER BE IN IT. Not obfuscated, not encrypted, not at all - what is saved is
 *     a token the server issued, and the password is never written down anywhere. The only way to
 *     be sure is to read the actual bytes of the actual file, which is what these do;
 *
 *   * A FILE FROM THE CLIENT THAT DID SAVE PASSWORDS must be cleaned, not just ignored. Every
 *     player upgrading has one of those files sitting on their disk with their password in it.
 *     Reading past the old field would leave it there for ever;
 *
 *   * a file that will not decrypt - copied from another machine, hand-edited, half-written -
 *     must cost the SAVED LOGIN and keep the ACCOUNT. Dropping the profile instead would look to
 *     a player like the client forgot their character existed;
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

	/** A token-shaped string, which is what the server would have sent. */
	static final String TOKEN = "a1b2c3d4e5f6g7h8i9j0";
	static final String TOKEN2 = "z9y8x7w6v5u4t3s2r1q0";

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

	/** The one call the client makes on a successful login, then the token a moment later. */
	static void signIn(String name, String token) {
		LoginProfiles.remember(name);
		LoginProfiles.rememberToken(name, token);
	}

	public static void main(String[] args) {
		System.setProperty("user.name", "tester");

		System.out.println("1. an empty machine");
		emptyTests();
		System.out.println();
		System.out.println("2. remembering an account");
		rememberTests();
		System.out.println();
		System.out.println("3. no password reaches the file, and the token is not readable either");
		secrecyTests();
		System.out.println();
		System.out.println("4. a file from the client that saved passwords");
		legacyTests();
		System.out.println();
		System.out.println("5. one row per account, newest first");
		listTests();
		System.out.println();
		System.out.println("6. forgetting");
		forgetTests();
		System.out.println();
		System.out.println("7. a file that will not decrypt keeps the account");
		damageTests();
		System.out.println();
		System.out.println("8. rubbish in, no exception out");
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
		signIn("Corey", TOKEN);
		List<LoginProfiles.Profile> list = LoginProfiles.all();
		check(list.size() == 1, "one profile after one login");
		check(list.get(0).username.equals("Corey"), "the name is kept as it was typed");
		check(list.get(0).token.equals(TOKEN), "the saved login round trips");
		check(list.get(0).canSignIn(), "so the profile can sign in on its own");
		check(list.get(0).lastUsed > 0L, "and it is stamped with when");

		// A login that asked for nothing to be saved. The account stays on the screen; there is
		// nothing on this computer that could sign in as it.
		freshHome("nokeep");
		LoginProfiles.remember("Corey");
		List<LoginProfiles.Profile> kept = LoginProfiles.all();
		check(kept.size() == 1, "a login with no token saved still saves the account");
		check(kept.get(0).token.length() == 0, "with no saved login");
		check(!kept.get(0).canSignIn(), "so clicking it fills the name in instead");

		// Survives the client closing.
		freshHome("persist");
		signIn("Corey", TOKEN);
		LoginProfiles.reload();
		List<LoginProfiles.Profile> after = LoginProfiles.all();
		check(after.size() == 1 && after.get(0).token.equals(TOKEN), "a saved login is there next launch");

		// SIGNING IN DOES NOT COST THE CARD ITS TOKEN. remember() runs before the token for this
		// login has arrived - and on a login that will never be sent one - so if it cleared the
		// field, every password login would silently un-save the account it just signed into.
		freshHome("keeps");
		signIn("Corey", TOKEN);
		LoginProfiles.remember("Corey");
		check(LoginProfiles.all().get(0).token.equals(TOKEN), "a later login keeps the saved login it already had");

		// A new token replaces the old one, and only writes when it is actually new.
		freshHome("replace");
		signIn("Corey", TOKEN);
		LoginProfiles.rememberToken("Corey", TOKEN2);
		check(LoginProfiles.all().get(0).token.equals(TOKEN2), "a fresh token replaces the one before it");

		// Nothing to attach it to. A token for an account with no card is dropped, not a new row:
		// the row is made by remember(), which is the login that was accepted.
		freshHome("unknown");
		LoginProfiles.rememberToken("nobody", TOKEN);
		check(LoginProfiles.all().isEmpty(), "a token for an account with no profile adds nothing");
	}

	static void secrecyTests() {
		freshHome("secrecy");
		signIn("Corey", TOKEN);
		String text = fileText();
		check(text.length() > 0, "the file is written");
		check(text.contains("Corey"), "the username is readable, which is the point of the row");
		check(!text.contains(TOKEN), "the TOKEN is not in the file in readable text");
		check(text.toLowerCase().contains("obfuscated"), "and the file says what it is, for whoever opens it");
		check(!text.contains("secret"), "and nothing writes the old password field any more");

		// A fresh IV per write. Without it the same value produces identical lines, which tells
		// anyone reading the file that two cards are related.
		//
		// BOTH ACCOUNTS GO IN THE SAME FILE ON PURPOSE, and the earlier version of this check did
		// not. It called freshHome() between them, which moves user.home - an input to the KEY.
		// Two different keys produce two different ciphertexts whatever the IV does, so the check
		// passed with a hardcoded IV and was testing nothing. One home, one key, two writes is
		// the only arrangement where the IV is the variable.
		freshHome("iv");
		signIn("alice", TOKEN);
		signIn("bob", TOKEN);
		String text2 = fileText();
		String first = tokenLine(text2, 0);
		String second = tokenLine(text2, 1);
		check(first.length() > 0 && second.length() > 0, "both accounts stored a saved login");
		check(!first.equals(second), "the same token under one key encrypts differently each time");
	}

	static String tokenLine(String text, int index) {
		String want = "profile." + index + ".token=";
		for (String line : text.split("\n")) {
			if (line.trim().startsWith(want)) {
				return line.trim().substring(want.length());
			}
		}
		return "";
	}

	/**
	 * THE UPGRADE. Every player who had saved logins before this has a file holding their password,
	 * encrypted under a key readable by anything running as them. Reading past that field would be
	 * a quiet decision to leave it on their disk for ever.
	 *
	 * The password is not recoverable from this side anyway - the whole point is that the client no
	 * longer sends one - so what has to be true is narrow and checkable: the account survives, it
	 * cannot sign in, and the old field is gone from the file by the time load() returns.
	 */
	static void legacyTests() {
		freshHome("legacy");
		// What the old client wrote: name, AES-wrapped password, stamp, face.
		write(profileFile(), "profile.0.name=Corey\nprofile.0.secret=" + fakeStoredPassword() + "\nprofile.0.last=1\n");
		LoginProfiles.reload();
		List<LoginProfiles.Profile> list = LoginProfiles.all();
		check(list.size() == 1, "an old file still lists the account");
		check(list.get(0).username.equals("Corey"), "under the right name");
		check(list.get(0).token.length() == 0, "with nothing that can sign in");
		check(!list.get(0).canSignIn(), "so the player types their password once");

		// AND THE OLD FIELD IS NOT READ AS A TOKEN. A password of 20 lowercase characters was
		// always legal at the login screen, so the old field can hold something the shape check
		// would wave through - which is the one way a password could still end up on the wire.
		freshHome("legacy-shaped");
		write(profileFile(), "profile.0.name=Corey\nprofile.0.secret=" + LoginProfiles.encrypt(TOKEN)
			+ "\nprofile.0.last=1\n");
		LoginProfiles.reload();
		check(LoginProfiles.all().size() == 1, "an old file whose password LOOKS like a token keeps the account");
		check(!LoginProfiles.all().get(0).canSignIn(), "and still cannot sign in with it");
		check(LoginProfiles.all().get(0).token.length() == 0, "because the old field is not read at all");

		freshHome("legacy");
		write(profileFile(), "profile.0.name=Corey\nprofile.0.secret=" + fakeStoredPassword() + "\nprofile.0.last=1\n");
		LoginProfiles.reload();
		list = LoginProfiles.all();
		String text = fileText();
		check(text.length() > 0, "and the file is rewritten");
		check(!text.contains("profile.0.secret"), "WITHOUT the field the password was in");
		check(!text.contains(fakeStoredPassword()), "and without the stored password itself");
		check(text.contains("Corey"), "but still with the account");

		// Reading it again finds nothing to clean, so it does not rewrite for ever.
		freshHome("legacy-twice");
		write(profileFile(), "profile.0.name=Corey\nprofile.0.secret=" + fakeStoredPassword() + "\nprofile.0.last=1\n");
		LoginProfiles.reload();
		String once = fileText();
		LoginProfiles.reload();
		LoginProfiles.all();
		check(!fileText().contains("profile.0.secret"), "a cleaned file stays clean");
		check(fileText().length() == once.length() || !fileText().contains("secret="), "and is not churned on every load");

		// A face in an old file is worth keeping: it is not a secret, and losing it would blank
		// every portrait on the row the moment a player upgraded.
		freshHome("legacy-face");
		StringBuilder look = new StringBuilder();
		for (int i = 0; i < LoginProfiles.LOOK_LEN; i++) {
			look.append(i > 0 ? "," : "").append(i);
		}
		write(profileFile(), "profile.0.name=Corey\nprofile.0.secret=" + fakeStoredPassword()
			+ "\nprofile.0.last=1\nprofile.0.look=" + look + "\n");
		LoginProfiles.reload();
		check(LoginProfiles.all().get(0).hasFace(), "an old file keeps its face");
	}

	/**
	 * Something in the old field's shape. The value does not matter - nothing decrypts it any
	 * more - only that it is there to be removed, and that it is distinctive enough to find in the
	 * rewritten file.
	 */
	static String fakeStoredPassword() {
		return "QUJDREVGR0hJSktMTU5PUFFSU1RVVldYWVowMTIzNDU2Nzg5";
	}

	static void listTests() {
		freshHome("list");
		signIn("alice", TOKEN);
		signIn("bob", TOKEN2);
		check(names().equals("bob,alice"), "most recently used comes first");

		LoginProfiles.remember("alice");
		check(names().equals("alice,bob"), "signing in again moves an account to the front");
		check(LoginProfiles.all().size() == 2, "and does not add a second row for it");
		check(LoginProfiles.all().get(0).token.equals(TOKEN), "keeping the saved login it had");

		// "Corey" and "corey" are one account on the server, so they are one row here.
		LoginProfiles.remember("ALICE");
		check(LoginProfiles.all().size() == 2, "a name in different case is the same account");
		check(LoginProfiles.all().get(0).username.equals("ALICE"), "kept as most recently typed");
		check(LoginProfiles.all().get(0).token.equals(TOKEN), "and the saved login follows the case change");

		freshHome("cap");
		LoginProfiles.remember("one");
		LoginProfiles.remember("two");
		LoginProfiles.remember("three");
		LoginProfiles.remember("four");
		LoginProfiles.remember("five");
		check(LoginProfiles.all().size() == LoginProfiles.MAX, "the list stops at MAX");
		check(names().equals("five,four,three,two"), "and it is the longest-unused that goes");
	}

	static void forgetTests() {
		freshHome("forget");
		signIn("alice", TOKEN);
		signIn("bob", TOKEN2);
		LoginProfiles.forget("alice");
		check(names().equals("bob"), "the x removes that account");
		check(!fileText().contains("alice"), "and takes it out of the file");

		LoginProfiles.forget("nobody");
		check(names().equals("bob"), "forgetting an account that is not there changes nothing");

		LoginProfiles.forget("BOB");
		check(LoginProfiles.all().isEmpty(), "forgetting ignores case too");

		// THE SERVER REFUSED A SAVED LOGIN. The card stays - with its name and its face - and asks
		// for a password, because that is the one thing the player can do about it.
		freshHome("expired");
		signIn("Corey", TOKEN);
		LoginProfiles.forgetToken("Corey");
		List<LoginProfiles.Profile> left = LoginProfiles.all();
		check(left.size() == 1, "a refused saved login keeps the account");
		check(!left.get(0).canSignIn(), "but it can no longer sign in");
		check(tokenLine(fileText(), 0).length() == 0, "and the file is written without it");
		LoginProfiles.reload();
		check(!LoginProfiles.all().get(0).canSignIn(), "which survives a restart");

		LoginProfiles.forgetToken("nobody");
		check(LoginProfiles.all().size() == 1, "dropping the token of an account that is not there changes nothing");
	}

	static void damageTests() {
		// The file carried to another machine, or to another user on this one. The key is derived
		// from the account, so it no longer decrypts.
		freshHome("moved");
		signIn("Corey", TOKEN);
		System.setProperty("user.name", "somebody-else");
		LoginProfiles.reload();
		List<LoginProfiles.Profile> moved = LoginProfiles.all();
		check(moved.size() == 1, "a file from another machine still lists the account");
		check(moved.get(0).username.equals("Corey"), "under the right name");
		check(moved.get(0).token.length() == 0, "but the saved login is gone, not wrongly decrypted");
		check(!moved.get(0).canSignIn(), "so the player is asked for a password");
		System.setProperty("user.name", "tester");

		// Hand-edited, truncated, or a half-finished write.
		freshHome("mangled");
		write(profileFile(), "profile.0.name=Corey\nprofile.0.token=not-base64-at-all!!\nprofile.0.last=1\n");
		LoginProfiles.reload();
		List<LoginProfiles.Profile> mangled = LoginProfiles.all();
		check(mangled.size() == 1 && mangled.get(0).username.equals("Corey"), "a mangled token keeps the account");
		check(mangled.get(0).token.length() == 0, "and drops the saved login");

		freshHome("short");
		write(profileFile(), "profile.0.name=Corey\nprofile.0.token=AAAA\nprofile.0.last=1\n");
		LoginProfiles.reload();
		check(LoginProfiles.all().size() == 1, "a value too short to hold an IV keeps the account");
		check(LoginProfiles.all().get(0).token.length() == 0, "and drops the saved login");
	}

	static void junkTests() {
		freshHome("junk");
		LoginProfiles.remember(null);
		check(LoginProfiles.all().isEmpty(), "a null username is ignored");
		LoginProfiles.remember("   ");
		check(LoginProfiles.all().isEmpty(), "so is an empty one");
		LoginProfiles.remember("thisnameisfartoolong");
		check(LoginProfiles.all().isEmpty(), "so is one longer than the login field allows");

		LoginProfiles.remember("ok");
		LoginProfiles.rememberToken("ok", null);
		check(LoginProfiles.all().size() == 1, "a null token still leaves the account");
		check(LoginProfiles.all().get(0).token.length() == 0, "with no saved login");

		// A TOKEN THAT IS NOT ONE. The only way to find out otherwise is a login the server
		// refuses, which the player reads as "invalid password" for a password they never typed.
		LoginProfiles.rememberToken("ok", "short");
		check(!LoginProfiles.all().get(0).canSignIn(), "a token of the wrong length is not stored");
		LoginProfiles.rememberToken("ok", "A1B2C3D4E5F6G7H8I9J0");
		check(!LoginProfiles.all().get(0).canSignIn(), "nor one with characters a token cannot have");
		LoginProfiles.rememberToken("ok", TOKEN);
		check(LoginProfiles.all().get(0).canSignIn(), "but a real one is");

		freshHome("binary");
		write(profileFile(), "\u0000\u0001 not a properties file at all ÿ\nprofile.=\n=\n");
		LoginProfiles.reload();
		check(LoginProfiles.all().isEmpty(), "a file that is not a properties file reads as empty");
		LoginProfiles.remember("after");
		check(LoginProfiles.all().size() == 1, "and the client can still save over it");

		// A VALUE THAT DECRYPTS PERFECTLY AND IS NOT A TOKEN. Everything else in this section
		// fails to decrypt, which the shape check never has to see: this is the case that holds it
		// to its job, and it is a real one - a file from a client whose idea of a token was a
		// different length is exactly this.
		freshHome("wrong-shape");
		write(profileFile(), "profile.0.name=Corey\nprofile.0.token=" + LoginProfiles.encrypt("TOOSHORT")
			+ "\nprofile.0.last=1\n");
		LoginProfiles.reload();
		check(LoginProfiles.all().size() == 1, "a stored value of the wrong shape keeps the account");
		check(!LoginProfiles.all().get(0).canSignIn(), "but is not offered as a saved login");

		freshHome("empty-token");
		write(profileFile(), "profile.0.name=Corey\nprofile.0.last=1\nprofile.0.token=\n");
		LoginProfiles.reload();
		check(LoginProfiles.all().size() == 1, "an entry with no token at all is fine");
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
