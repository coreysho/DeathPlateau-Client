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
 * THE PASSWORD IS OBFUSCATED, NOT PROTECTED, AND NOTHING HERE SHOULD BE READ AS SECURITY. It is
 * encrypted with AES, but the client that writes it must also be able to read it back without
 * asking the player anything, so the key is derived from values any program on this machine can
 * read. Anyone who has both the file and this class can recover the password. What the encryption
 * genuinely buys is the realistic case rather than the determined one: the password is not sitting
 * in readable text for a backup service to index, a screenshare to expose, or someone glancing at
 * the folder to read. Treat a leaked profiles.properties as a leaked password.
 *
 * The key is tied to the account on this machine (user.name and user.home), so the file carried to
 * another computer or another user simply fails to decrypt. That is the wanted behaviour, not a
 * failure: the profile keeps its username and quietly forgets its password, and the player types
 * it once.
 *
 * A REVOCABLE TOKEN IS THE PROPER VERSION OF THIS. If the engine ever issues one, the stored
 * secret becomes something the server can cancel, and a leaked file stops being a leaked password.
 * Profile is deliberately shaped so that day is a change of what `secret` holds, not a change to
 * the login screen.
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

	/** Mirrors the login form, which stops at 12 and 20. A longer stored value is a broken file. */
	private static final int MAX_NAME = 12;
	private static final int MAX_SECRET = 20;

	private static boolean loaded;
	private static final List<Profile> profiles = new ArrayList<Profile>();

	private LoginProfiles() {
	}

	/** One saved account. `secret` is "" when only the name was kept. */
	public static final class Profile {

		public final String username;
		public final String secret;
		public final long lastUsed;

		Profile(String username, String secret, long lastUsed) {
			this.username = username;
			this.secret = secret == null ? "" : secret;
			this.lastUsed = lastUsed;
		}

		/** Whether clicking this one can sign in directly, or only fills the name in. */
		public boolean canSignIn() {
			return this.secret.length() > 0;
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
	 * Called after a login is accepted. Keeping the password is the player's choice on the screen;
	 * when they decline, the name is still saved so the profile button remains useful.
	 */
	public static synchronized void remember(String username, String password, boolean keepPassword) {
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
		String secret = keepPassword && password != null && password.length() <= MAX_SECRET ? password : "";

		// Same account signing in again replaces its entry rather than adding a second one. Matched
		// without case because the server lowercases a name into base37 and "Corey" and "corey" are
		// one account; the spelling kept is the one just typed.
		removeByName(name);
		profiles.add(0, new Profile(name, secret, System.currentTimeMillis()));
		while (profiles.size() > MAX) {
			profiles.remove(profiles.size() - 1);
		}
		save();
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
				// A secret that will not decrypt - a file copied from another machine, an edit, a
				// truncated write - costs the password and keeps the account. Never the whole entry.
				String secret = decrypt(p.getProperty("profile." + i + ".secret", ""));
				if (secret.length() > MAX_SECRET) {
					secret = "";
				}
				long last = 0L;
				try {
					last = Long.parseLong(p.getProperty("profile." + i + ".last", "0").trim());
				} catch (Exception ignored) {
				}
				if (!containsName(name)) {
					profiles.add(new Profile(name, secret, last));
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
				p.setProperty("profile." + i + ".secret", encrypt(profile.secret));
				p.setProperty("profile." + i + ".last", "" + profile.lastUsed);
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
				+ " The stored passwords are obfuscated, not safe: treat this file as the passwords themselves.");
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
	 * survives a properties file. A fresh IV per save is why the same password does not produce the
	 * same line twice, which would otherwise let anyone reading the file tell two accounts share a
	 * password.
	 */
	private static String encrypt(String plain) {
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
			// Better to save a profile that has forgotten its password than to lose the profile.
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
