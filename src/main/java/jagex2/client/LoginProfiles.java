package jagex2.client;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Properties;
import java.util.Random;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * The accounts the login screen offers to sign in as, so a player with four characters picks one
 * instead of typing a name and a password every time.
 *
 * Kept in ~/.deathplateau/profiles.properties beside display.properties, for the same reason: it
 * is about this machine, not about how the game plays. Plain key=value, so a player can open it,
 * see exactly which accounts are on this computer, and delete the file or one entry by hand.
 *
 * WHAT IS SAVED IS A TOKEN, NOT THE PASSWORD. The server issues it when the player ticks
 * Remember me and then signs in for real, and it is worth that one account on this one computer
 * until the server cancels it: ::forgetlogins cancels every one, changing the password cancels
 * every one, and a month of not being used cancels this one. The player's own password is never
 * written to this file, or to any other.
 *
 * It used to be the password, encrypted with AES under a key derived from values any program on
 * this machine can read - because the client has to read it back without asking anybody anything,
 * which rules out a key worth the name. This file's own header said to treat a leak as a leaked
 * password, and that was exactly right. A token is the version of that which can be taken away.
 *
 * THE OBFUSCATION IS STILL HERE AND IS STILL NOT SECURITY. A leaked token is worth the account
 * until somebody cancels it, so it is kept out of readable text for the same ordinary reasons: a
 * backup service indexing the file, a screenshare, someone glancing at the folder. The key is tied
 * to the account on this machine (user.name and user.home), so the file carried to another
 * computer or another user simply fails to decrypt - which is wanted, not a failure. The profile
 * keeps its username, quietly forgets its token, and the player types a password once.
 *
 * A FILE FROM THE CLIENT THAT SAVED PASSWORDS is read for its names and its faces, and its
 * passwords are thrown away and rewritten out of the file on the spot. See load().
 *
 * Nothing here may throw. A missing, truncated or hand-mangled file is no saved profiles, which is
 * the login screen the client always had.
 */
public final class LoginProfiles {

	private static final String FILE_NAME = "profiles.properties";

	/**
	 * Four, because the login screen shows them in one row across a 765-wide title area with an
	 * add button on the end, and a fifth would have to scroll. Saving a fifth account drops the
	 * one used longest ago.
	 */
	public static final int MAX = 4;

	/** Mirrors the login form, which stops at 12. A longer stored value is a broken file. */
	private static final int MAX_NAME = 12;

	/**
	 * A token is exactly this long and made of exactly these characters, which is the server's
	 * LoginToken.LENGTH and LoginToken.ALPHABET. Checked on the way in, so a hand-edited or
	 * half-written file cannot put a login on the wire that the server will only refuse.
	 */
	public static final int TOKEN_LEN = 20;
	private static final String TOKEN_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789";

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

	private static boolean loaded;
	private static final List<Profile> profiles = new ArrayList<Profile>();

	private LoginProfiles() {
	}

	/**
	 * What a character looks like, kept so the login screen can draw their face: the twelve
	 * appearance slots the engine sends, then the five recolours, then 1 for a female body.
	 *
	 * It is written down because the client cannot work it out. Appearance arrives with the
	 * player once they are IN the world, and the login screen is the one place it is needed and
	 * the one place it has never been - so the only way to show a face beside a name is to have
	 * kept the last one seen.
	 */
	public static final int LOOK_LEN = 18;

	/** One saved account. `token` is "" when only the name was kept. */
	public static final class Profile {

		public final String username;

		/**
		 * The saved login, or "" for a card that can only fill the name in. Never a password:
		 * nothing in this client has written one here since saved logins became tokens.
		 */
		public final String token;

		public final long lastUsed;

		/** LOOK_LEN numbers, or null for an account not yet seen in the world. */
		public final int[] look;

		Profile(String username, String token, long lastUsed, int[] look) {
			this.username = username;
			this.token = token == null ? "" : token;
			this.lastUsed = lastUsed;
			this.look = look != null && look.length == LOOK_LEN ? look : null;
		}

		/** Whether clicking this one can sign in directly, or only fills the name in. */
		public boolean canSignIn() {
			return this.token.length() > 0;
		}

		/** Whether there is a face to draw, or only the empty frame. */
		public boolean hasFace() {
			return this.look != null;
		}
	}

	private static File file() {
		// Read at call time, never cached: the tests point user.home at a scratch folder, and a
		// cached path would write a developer's real profiles into it.
		return new File(new File(System.getProperty("user.home"), ".deathplateau"), FILE_NAME);
	}

	/** Most recently used first, which is the order the login screen draws them in. */
	public static synchronized List<Profile> all() {
		if (!loaded) {
			load();
		}
		return new ArrayList<Profile>(profiles);
	}

	/**
	 * Called after a login is accepted - THE NAME, and nothing that could sign in with it.
	 *
	 * Whether this account gets a saved login is not settled here and cannot be: the token is
	 * minted by the server after the login goes through and arrives a moment later, in
	 * rememberToken. A player who ticked nothing still gets their name on a card, because a card
	 * that fills the name in is worth having.
	 *
	 * An account that already had a token keeps it. Signing in is not a reason to throw one away,
	 * and a password login by somebody who already had a card would otherwise clear the card.
	 */
	public static synchronized void remember(String username) {
		if (!loaded) {
			load();
		}
		if (username == null) {
			return;
		}
		String name = username.trim();
		if (name.length() == 0 || name.length() > MAX_NAME) {
			return;
		}

		// Same account signing in again replaces its entry rather than adding a second one. Matched
		// without case because the server lowercases a name into base37 and "Corey" and "corey" are
		// one account; the spelling kept is the one just typed.
		// The face and the token this account had last time are carried over. remember() runs when
		// the login is accepted, which is before the world has said what the player looks like and
		// before the token has arrived - so taking the entry out and putting a fresh one back would
		// blank both on every single login, and they would only reappear a moment later.
		int[] look = lookOf(name);
		String token = tokenOf(name);
		removeByName(name);
		profiles.add(0, new Profile(name, token, System.currentTimeMillis(), look));
		while (profiles.size() > MAX) {
			profiles.remove(profiles.size() - 1);
		}
		save();
	}

	/**
	 * Called when the server hands this session a saved login (server prot 14), which is after
	 * remember() and only for a login that asked for one.
	 *
	 * A token that is not the right shape is dropped rather than stored: the only way to find out
	 * otherwise is a login the server refuses, by which time the player is looking at "invalid
	 * password" for a password they never typed.
	 */
	public static synchronized void rememberToken(String username, String token) {
		if (!loaded) {
			load();
		}
		if (username == null || !looksLikeToken(token)) {
			return;
		}
		String name = username.trim();
		for (int i = 0; i < profiles.size(); i++) {
			Profile p = profiles.get(i);
			if (!p.username.equalsIgnoreCase(name)) {
				continue;
			}
			if (p.token.equals(token)) {
				return;
			}
			profiles.set(i, new Profile(p.username, token, p.lastUsed, p.look));
			save();
			// To client.log, so "the card cannot sign in" can be told apart from "no token ever
			// arrived". The token itself is never logged.
			System.err.println("profiles: saved the login for " + p.username);
			return;
		}
	}

	/**
	 * The server would not take this account's saved login - expired, cancelled, or for an account
	 * that is gone. The card stays, with its name and its face, and asks for a password.
	 */
	public static synchronized void forgetToken(String username) {
		if (!loaded) {
			load();
		}
		if (username == null) {
			return;
		}
		String name = username.trim();
		for (int i = 0; i < profiles.size(); i++) {
			Profile p = profiles.get(i);
			if (!p.username.equalsIgnoreCase(name)) {
				continue;
			}
			if (p.token.length() == 0) {
				return;
			}
			profiles.set(i, new Profile(p.username, "", p.lastUsed, p.look));
			save();
			System.err.println("profiles: dropped the saved login for " + p.username);
			return;
		}
	}

	/**
	 * Called once the world has said what this player looks like, which is after remember().
	 *
	 * Does nothing unless the face has actually changed, because this is reached on every
	 * appearance update - every time the player changes clothes - and rewriting the file to say
	 * the same thing is a file write per wardrobe change.
	 */
	public static synchronized void rememberFace(String username, int[] look) {
		if (!loaded) {
			load();
		}
		if (username == null || look == null || look.length != LOOK_LEN) {
			return;
		}
		String name = username.trim();
		for (int i = 0; i < profiles.size(); i++) {
			Profile p = profiles.get(i);
			if (!p.username.equalsIgnoreCase(name)) {
				continue;
			}
			if (same(p.look, look)) {
				return;
			}
			profiles.set(i, new Profile(p.username, p.token, p.lastUsed, look.clone()));
			save();
			// To client.log, so "no portrait" can be told apart from "no face was ever saved".
			System.err.println("profiles: kept the face for " + p.username);
			return;
		}
	}

	private static boolean same(int[] a, int[] b) {
		if (a == null || b == null || a.length != b.length) {
			return false;
		}
		for (int i = 0; i < a.length; i++) {
			if (a[i] != b[i]) {
				return false;
			}
		}
		return true;
	}

	private static int[] lookOf(String name) {
		for (int i = 0; i < profiles.size(); i++) {
			if (profiles.get(i).username.equalsIgnoreCase(name)) {
				return profiles.get(i).look;
			}
		}
		return null;
	}

	private static String tokenOf(String name) {
		for (int i = 0; i < profiles.size(); i++) {
			if (profiles.get(i).username.equalsIgnoreCase(name)) {
				return profiles.get(i).token;
			}
		}
		return "";
	}

	/** The × on a profile. Removes the account from this machine, not from the server. */
	public static synchronized void forget(String username) {
		if (!loaded) {
			load();
		}
		if (username == null || !removeByName(username.trim())) {
			return;
		}
		save();
	}

	private static boolean removeByName(String name) {
		for (int i = 0; i < profiles.size(); i++) {
			if (profiles.get(i).username.equalsIgnoreCase(name)) {
				profiles.remove(i);
				return true;
			}
		}
		return false;
	}

	private static void load() {
		loaded = true;
		profiles.clear();
		InputStream in = null;
		// Whether this file was written by the client that saved passwords. It is rewritten below
		// if so, which is what takes those passwords off the disk.
		boolean legacy = false;
		try {
			File f = file();
			if (!f.exists()) {
				return;
			}
			in = new FileInputStream(f);
			Properties p = new Properties();
			p.load(in);
			for (int i = 0; i < MAX; i++) {
				String name = p.getProperty("profile." + i + ".name");
				if (name == null) {
					continue;
				}
				name = name.trim();
				if (name.length() == 0 || name.length() > MAX_NAME) {
					continue;
				}
				// A token that will not decrypt - a file copied from another machine, an edit, a
				// truncated write - costs the saved login and keeps the account. Never the whole
				// entry: the name and the face are what make the card worth drawing at all.
				String token = decrypt(p.getProperty("profile." + i + ".token", ""));
				if (!looksLikeToken(token)) {
					token = "";
				}

				// THE OLD CLIENT'S profile.N.secret HELD THE PLAYER'S PASSWORD. It is not read and
				// not kept: nothing here will send a password again, so the account keeps its name
				// and its face, is asked for the password once, and is handed a token to replace
				// it. Noting it rewrites the file at the end of load, which is the only thing that
				// gets the old password out of it - leaving one there to rot would be the single
				// outcome worse than asking somebody to type theirs.
				if (p.getProperty("profile." + i + ".secret") != null) {
					legacy = true;
				}
				long last = 0L;
				try {
					last = Long.parseLong(p.getProperty("profile." + i + ".last", "0").trim());
				} catch (Exception ignored) {
				}
				if (!containsName(name)) {
					profiles.add(new Profile(name, token, last, parseLook(p.getProperty("profile." + i + ".look"))));
				}
			}
		} catch (Exception ignored) {
			profiles.clear();
		} finally {
			try {
				if (in != null) {
					in.close();
				}
			} catch (Exception ignored) {
			}
		}
		if (legacy) {
			// After the stream is closed, and whatever the read made of the rest of the file: the
			// point is that what is on disk afterwards is what this class writes, which has no
			// passwords in it.
			save();
			System.err.println("profiles: rewrote the file without the passwords the old client saved");
		}
	}

	/**
	 * A face out of the file, or null for anything that is not exactly what was written.
	 *
	 * Anything wrong here costs a picture and nothing else - the account, its name and its
	 * password are all still read. A face is the one part of a profile that can be thrown away
	 * without the player losing something they would notice as missing.
	 */
	private static int[] parseLook(String text) {
		if (text == null) {
			return null;
		}
		String[] parts = text.trim().split(",");
		if (parts.length != LOOK_LEN) {
			return null;
		}
		int[] look = new int[LOOK_LEN];
		for (int i = 0; i < LOOK_LEN; i++) {
			try {
				look[i] = Integer.parseInt(parts[i].trim());
			} catch (Exception malformed) {
				return null;
			}
		}
		return look;
	}

	private static String writeLook(int[] look) {
		StringBuilder text = new StringBuilder();
		for (int i = 0; i < look.length; i++) {
			if (i > 0) {
				text.append(',');
			}
			text.append(look[i]);
		}
		return text.toString();
	}

	private static boolean containsName(String name) {
		for (int i = 0; i < profiles.size(); i++) {
			if (profiles.get(i).username.equalsIgnoreCase(name)) {
				return true;
			}
		}
		return false;
	}

	private static void save() {
		OutputStream out = null;
		try {
			File f = file();
			File dir = f.getParentFile();
			if (!dir.exists() && !dir.mkdirs()) {
				return;
			}
			Properties p = new Properties();
			for (int i = 0; i < profiles.size(); i++) {
				Profile profile = profiles.get(i);
				p.setProperty("profile." + i + ".name", profile.username);
				p.setProperty("profile." + i + ".token", encrypt(profile.token));
				p.setProperty("profile." + i + ".last", "" + profile.lastUsed);
				if (profile.look != null) {
					p.setProperty("profile." + i + ".look", writeLook(profile.look));
				}
			}
			// Narrowed before the write, not after, so the secrets are never briefly world
			// readable. These return false on filesystems that do not carry the bits, which is
			// nothing to act on - the attempt is what we can do from here.
			if (!f.exists()) {
				f.createNewFile();
			}
			f.setReadable(false, false);
			f.setWritable(false, false);
			f.setReadable(true, true);
			f.setWritable(true, true);
			out = new FileOutputStream(f);
			p.store(out, "Death Plateau saved logins. Delete this file to forget every account on this machine."
				+ " What is stored is a token the server issued, not your password. It is obfuscated, not safe:"
				+ " anyone who takes this file can sign in as you until the token is cancelled, which ::forgetlogins"
				+ " in game does to all of them, as does changing your password.");
		} catch (Exception ignored) {
		} finally {
			try {
				if (out != null) {
					out.close();
				}
			} catch (Exception ignored) {
			}
		}
	}

	// ---------------------------------------------------------------- the obfuscation

	/**
	 * AES-CBC with a random 16 byte IV written in front of the ciphertext, the pair base64'd so it
	 * survives a properties file. A fresh IV per save is why one token does not produce the same
	 * line twice - and with tokens it is the stronger reason it ever was: two cards written from
	 * the same machine on the same day would otherwise look related to anyone reading the file.
	 */
	static String encrypt(String plain) {
		if (plain == null || plain.length() == 0) {
			return "";
		}
		try {
			byte[] iv = new byte[16];
			new Random().nextBytes(iv);
			Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
			c.init(Cipher.ENCRYPT_MODE, key(), new IvParameterSpec(iv));
			byte[] body = c.doFinal(plain.getBytes("UTF-8"));
			byte[] all = new byte[iv.length + body.length];
			System.arraycopy(iv, 0, all, 0, iv.length);
			System.arraycopy(body, 0, all, iv.length, body.length);
			return Base64.getEncoder().encodeToString(all);
		} catch (Exception ignored) {
			// Better to save a profile that has forgotten its saved login than to lose the profile.
			return "";
		}
	}

	private static String decrypt(String stored) {
		if (stored == null || stored.trim().length() == 0) {
			return "";
		}
		try {
			byte[] all = Base64.getDecoder().decode(stored.trim());
			if (all.length <= 16) {
				return "";
			}
			byte[] iv = new byte[16];
			System.arraycopy(all, 0, iv, 0, 16);
			Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
			c.init(Cipher.DECRYPT_MODE, key(), new IvParameterSpec(iv));
			return new String(c.doFinal(all, 16, all.length - 16), "UTF-8");
		} catch (Exception ignored) {
			return "";
		}
	}

	/**
	 * Derived from this machine's account rather than a constant, so the file is useless if it is
	 * copied to another computer or another user on this one. It is NOT a secret - every input is
	 * readable by anything running as this user - and it is not meant to be one.
	 */
	private static SecretKeySpec key() throws Exception {
		MessageDigest md = MessageDigest.getInstance("SHA-256");
		md.update("death-plateau-profiles-v1".getBytes("UTF-8"));
		md.update(String.valueOf(System.getProperty("user.name", "")).getBytes("UTF-8"));
		md.update(String.valueOf(System.getProperty("user.home", "")).getBytes("UTF-8"));
		byte[] digest = md.digest();
		byte[] k = new byte[16];
		System.arraycopy(digest, 0, k, 0, 16);
		return new SecretKeySpec(k, "AES");
	}

	/** The tests reload between cases; the client never needs it. */
	static synchronized void reload() {
		loaded = false;
		profiles.clear();
	}
}
