/*
 * A SAVED LOGIN, END TO END, AGAINST A REAL ENGINE.
 *
 * The client keeps a server-issued token where it used to keep the player's password, and the two
 * halves of that have to agree about bytes nobody can see: two new fields inside the RSA login
 * block, and one new server packet coming back. LoginProfilesTest proves what the client writes to
 * disk and the engine's own checks prove what it does with a token - neither can prove the client
 * and the engine read the same envelope. Only a real login can, and a disagreement there is not a
 * subtle bug: it is nobody being able to log in at all.
 *
 * So this runs the real client, with its own window and its own input handling, against an engine
 * on a socket, in three phases - each a fresh JVM sharing one scratch user.home, because "it still
 * works after you close the client" is the whole feature:
 *
 *   password  type a name and a password with Remember me ticked. Asserts the client gets into the
 *             game, is handed a token, saves it, and that the password is nowhere in the file.
 *   token     click the saved card and type nothing at all. Asserts it gets into the game on the
 *             token alone, with the password field still empty.
 *   stale     put a well-formed token the server has never issued on the card and click it.
 *             Asserts the login is refused, the card stops offering to sign in, and the screen
 *             says what to do about it rather than "invalid password" for a password nobody typed.
 *
 * Driven by run_logintokentest.py, which starts the engine and runs the phases in order.
 */
package jagex2.client;

import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.RandomAccessFile;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.net.InetAddress;
import java.util.List;

import sign.signlink;

public class LoginTokenTest extends Client {

	static LoginTokenTest app;
	static int frames;
	static int fails;

	/** Ours, not java.awt's - Component.LOCK is package private to java.awt. As in CourseCam. */
	static final Object LOCK = new Object();

	/** A minute of trying, in three second steps: the world's own hold on a just-closed session. */
	static final int RETRIES = 20;
	static final long RETRY_WAIT = 3000L;

	/** Well formed - 20 of the alphabet - and never issued by anything. */
	static final String NEVER_ISSUED = "qqqqwwwweeeerrrrtttt";

	static void check(boolean ok, String what) {
		System.out.println((ok ? "  ok   " : "FAIL   ") + what);
		if (!ok) {
			fails++;
		}
	}

	public void draw() {
		super.draw();
		frames++;
	}

	public static void main(String[] args) {
		try {
			run(args);
		} catch (Exception broken) {
			// Not a check failing - the harness itself fell over, and the stack is the only thing
			// that says where. A bare non-zero exit here reads as "the login was refused".
			broken.printStackTrace();
			System.out.println();
			System.out.println("1 CHECK(S) FAILED");
			System.exit(1);
		}
	}

	static void run(String[] args) throws Exception {
		String phase = args[0];
		String user = args[1];
		String password = args[2];

		String rsan = System.getProperty("shots.rsan");
		if (rsan != null) {
			Client.LOGIN_RSAN = new BigInteger(rsan.trim());
		}
		Client.nodeId = 10;
		Client.portOffset = 0;
		Client.setHighMem();
		Client.membersWorld = true;
		signlink.storeid = 33;
		signlink.startpriv(InetAddress.getLocalHost());

		// The title area's own size, so a coordinate in TitleScreen's 765x503 is a coordinate on
		// this canvas and nothing has to be offset.
		app = new LoginTokenTest();
		app.setSize(765, 503);
		// initApplication, NOT initApplet. The cams use initApplet because they are driving a
		// client inside their own window arrangement; an applet with no container has no Graphics
		// to acquire, and GameShell.run's first act is drawProgress, which draws into it. That is
		// an NPE on the client's own thread - the loop dies, no frame is ever drawn, and the only
		// symptom is this test timing out waiting for a title screen that was never going to come.
		// initApplication makes the ViewBox and takes its Graphics before the loop starts.
		app.initApplication(503, 765);
		waitFor("the title screen", 120000, () -> geti("titleScreenState") == 0 && frames > 20 && get("imageTitle4") != null);
		waitFrames(5);

		System.out.println("phase: " + phase);
		if (phase.equals("password")) {
			passwordPhase(user, password);
		} else if (phase.equals("token")) {
			tokenPhase(user);
		} else if (phase.equals("stale")) {
			stalePhase(user);
		} else if (phase.equals("legacy")) {
			legacyPhase(user, password);
		} else if (phase.equals("noremember")) {
			noRememberPhase(user, password);
		} else {
			check(false, "unknown phase " + phase);
		}

		System.out.println();
		if (fails == 0) {
			System.out.println("all checks passed");
		} else {
			System.out.println(fails + " CHECK(S) FAILED");
		}
		// The window and the client's own threads are still up, and nothing here wants a clean
		// shutdown of the game: the engine logging the account out because the socket went is the
		// same thing that happens when a player's client is killed, and the next phase needs that
		// to have happened anyway.
		System.exit(fails == 0 ? 0 : 1);
	}

	// ---------------------------------------------------------------- 1. a typed password
	static void passwordPhase(String user, String password) throws Exception {
		check(LoginProfiles.all().isEmpty(), "this machine has no saved logins yet");

		set("username", user);
		set("password", password);
		// Remember me, set rather than clicked: what is under test is the token, and the checkbox
		// has its own coverage on the title screen.
		set("rememberLogin", Boolean.TRUE);
		boolean in = signIn(true);
		check(in, "a typed password gets into the game" + why());
		if (!in) {
			return;
		}

		String token = (String) get("loginToken");
		check(LoginProfiles.looksLikeToken(token), "and the server hands back a saved login: " + shape(token));

		List<LoginProfiles.Profile> saved = LoginProfiles.all();
		check(saved.size() == 1 && saved.get(0).username.equalsIgnoreCase(user), "which is saved against the account");
		check(saved.size() == 1 && saved.get(0).token.equals(token), "as the token the server sent");
		check(saved.size() == 1 && saved.get(0).canSignIn(), "so the card can sign in next time");

		// THE POINT OF ALL OF IT. The password was typed into this client a moment ago and must
		// not have reached the disk, in any form, readable or not.
		String file = fileText();
		check(file.length() > 0, "the profile file is written");
		check(!file.contains(password), "and the PASSWORD is not in it");
		check(!file.contains("profile.0.secret"), "nor is the field the old client kept it in");
	}

	// ---------------------------------------------------------------- 2. the card alone
	static void tokenPhase(String user) throws Exception {
		List<LoginProfiles.Profile> saved = LoginProfiles.all();
		check(saved.size() == 1 && saved.get(0).canSignIn(), "the saved login survived the client closing");
		if (saved.isEmpty() || !saved.get(0).canSignIn()) {
			return;
		}
		String token = saved.get(0).token;

		// Nothing typed. Not the name, not the password - the card is the whole of the input, which
		// is what a player does when they come back to a client they have used before.
		boolean in = clickCardAndWait(true);
		check(in, "clicking the card gets into the game with nothing typed" + why());
		if (!in) {
			return;
		}
		check(((String) get("password")).length() == 0, "and no password was ever in the field");
		check(token.equals(get("loginToken")), "the login used the saved token");
		check(user.equalsIgnoreCase((String) get("username")), "as the right account");
	}

	// ---------------------------------------------------------------- 3. a token the server never issued
	static void stalePhase(String user) throws Exception {
		// Cancelled, expired, or made up: from the client's side these are one thing - a token the
		// server will not take - and this is the only one of the three a test can arrange without
		// waiting a month or reaching into the engine's database.
		LoginProfiles.rememberToken(user, NEVER_ISSUED);
		check(LoginProfiles.all().get(0).token.equals(NEVER_ISSUED), "the card is holding a token the server never issued");

		boolean in = clickCardAndWait(false);
		check(!in, "the login is refused");
		check(((String) get("loginMessage0")).toLowerCase().contains("saved login"), "the screen blames the saved login, not a password: " + get("loginMessage0"));
		check(((String) get("loginToken")).length() == 0, "the client drops the token it was holding");
		check(geti("titleLoginField") == 1, "and puts the cursor in the password field");

		List<LoginProfiles.Profile> after = LoginProfiles.all();
		check(after.size() == 1, "the account stays on the screen");
		check(!after.get(0).canSignIn(), "but stops offering to sign in");
		LoginProfiles.reload();
		check(!LoginProfiles.all().get(0).canSignIn(), "which is written down, not just forgotten in memory");
	}

	// ---------------------------------------------------------------- 4. a client from before all this
	/**
	 * A LOGIN BLOCK OF THE OLD SHAPE, ACCEPTED.
	 *
	 * Every revision bump before this one was a flag day: the engine goes up, and every client still
	 * on the old number is turned away until its launcher fetches a release that only exists after
	 * the client repo is pushed. 382 is built not to be one - the two new fields are at the END of
	 * the block, so a client that sends neither is let in without saved logins - and that is a claim
	 * about the engine that has to be checked, because getting it wrong means a locked-out server
	 * for however long the other half takes to ship.
	 *
	 * There is no 381 client here to test it with, so this sends what one sent: the handshake, then
	 * the block with revision 381 and nothing after the password. It is written out by hand rather
	 * than by Client.login, which is the point - the old layout is frozen, and this is the only
	 * description of it left once login() has moved on.
	 *
	 * The CRCs and the uid come from the client this runs inside, which has loaded the real cache:
	 * the engine checks them, and a hand-made block with invented ones is refused as out of date
	 * whatever the revision says, which would make this pass for the wrong reason.
	 */
	static void legacyPhase(String user, String password) throws Exception {
		int reply = oldStyleLogin(user, password, 381);
		// 5 IS NOT AN ANSWER TO THE QUESTION. It means "already logged in": the phase before this
		// one was in the game a few seconds ago, its JVM is gone, and the world has not finished
		// saving and removing the player yet. Every other phase here retries for a minute through
		// signIn for exactly this reason; this one read its socket once and believed it, so on a
		// slower machine - a CI runner - it reported a pre-382 block being REFUSED when nothing of
		// the kind had happened. Wait it out, as the others do.
		for (int i = 0; i < RETRIES && reply == 5; i++) {
			Thread.sleep(RETRY_WAIT);
			reply = oldStyleLogin(user, password, 381);
		}
		// 2 is "you are in" and 0 is "you are in, as a new character". 6 is out of date, which is
		// the failure this phase exists to catch.
		check(reply == 2 || reply == 0, "a login block with no saved-login fields is accepted: reply " + reply);
		check(reply != 6, "and is NOT turned away as out of date");

		// And the number below the supported pair still is. Nothing should accept 380.
		int old = oldStyleLogin(user, password, 380);
		check(old == 6, "a revision older than those two is still refused: reply " + old);
	}

	// ---------------------------------------------------------------- 5. Remember me, off
	// THE OPPOSITE CLAIM, and the one nobody was making. The four phases above all tick Remember
	// me, so a client that ignored the flag - asking for a token on every login - would have
	// passed every one of them while storing a saved login for a player who never asked for one,
	// on a machine that may not be theirs. The name is still remembered, as it always was before
	// any of this: that is the card, and it is not a saved login.
	static void noRememberPhase(String user, String password) throws Exception {
		check(LoginProfiles.all().isEmpty(), "a home of its own, with nothing saved in it");

		set("username", user);
		set("password", password);
		set("rememberLogin", Boolean.FALSE);
		boolean in = signIn(true);
		check(in, "a login that did not ask to be remembered still gets in" + why());
		if (!in) {
			return;
		}

		check(((String) get("loginToken")).length() == 0, "and is handed no saved login");

		List<LoginProfiles.Profile> saved = LoginProfiles.all();
		check(saved.size() == 1 && saved.get(0).username.equalsIgnoreCase(user), "the name is remembered, as it always was");
		check(saved.size() == 1 && saved.get(0).token.length() == 0, "with nothing to sign in with");
		check(!saved.isEmpty() && !saved.get(0).canSignIn(), "so the card asks for the password instead of signing in");
		check(!fileText().contains(password), "and the password is not on the disk either");
	}

	/** The pre-382 login block, start to finish, and whatever byte the engine answers with. */
	static int oldStyleLogin(String user, String password, int revision) throws Exception {
		java.net.Socket socket = app.openSocket(Client.portOffset + Client.GAME_PORT);
		try {
			jagex2.io.ClientStream stream = new jagex2.io.ClientStream(socket, app);
			jagex2.io.Packet out = new jagex2.io.Packet(new byte[5000]);
			jagex2.io.Packet login = new jagex2.io.Packet(new byte[5000]);

			// The name hash handshake, exactly as login() opens with.
			long name37 = jagex2.jstring.JString.toBase37(user);
			out.pos = 0;
			out.p1(14);
			out.p1((int) (name37 >> 16 & 0x1FL));
			stream.write(2, 0, out.data);
			for (int i = 0; i < 8; i++) {
				stream.method234();
			}
			int status = stream.method234();
			if (status != 0) {
				return status;
			}
			byte[] seedBytes = new byte[8];
			stream.read(seedBytes, 0, 8);
			jagex2.io.Packet in = new jagex2.io.Packet(seedBytes);
			long serverSeed = in.g8();

			int[] seed = new int[] { 1, 2, (int) (serverSeed >> 32), (int) serverSeed };
			out.pos = 0;
			out.p1(10);
			out.p4(seed[0]);
			out.p4(seed[1]);
			out.p4(seed[2]);
			out.p4(seed[3]);
			out.p4(sign.signlink.uid);
			out.pjstr(user);
			out.pjstr(password);
			// AND NOTHING ELSE. This is the whole of the difference: no remember flag, no token.
			out.rsaenc(Client.LOGIN_RSAN, Client.LOGIN_RSAE);

			login.pos = 0;
			login.p1(16);
			login.p1(out.pos + 36 + 1 + 1 + 2);
			login.p1(255);
			login.p2(revision);
			login.p1(0x2);
			for (int i = 0; i < 9; i++) {
				login.p4(app.jagChecksum[i]);
			}
			login.pdata(out.data, out.pos, 0);
			stream.write(login.pos, 0, login.data);
			return stream.method234();
		} finally {
			try {
				socket.close();
			} catch (Exception ignored) {
			}
		}
	}

	// ---------------------------------------------------------------- driving it
	/**
	 * Presses the login button, waiting for the game.
	 *
	 * Retried when a login is expected to work, because the phase before this one left an account
	 * logged in on the world and the engine needs a moment to notice the socket went: that reads
	 * as "Your account is already logged in", which is true, temporary, and not what is under test.
	 *
	 * The budget is a minute, because that is what the client's own message for it says - "Try
	 * again in 60 secs". Sixteen seconds was enough against an engine that had been up a while and
	 * not enough against one started moments earlier, which is exactly the flakiness worth paying
	 * three quarters of a minute to be rid of.
	 */
	static boolean signIn(boolean expected) throws Exception {
		int tries = expected ? RETRIES : 1;
		for (int i = 0; i < tries && !app.ingame; i++) {
			if (i > 0) {
				Thread.sleep(RETRY_WAIT);
			}
			click(765 / 2, 325 + 44 / 2, false);   // TitleScreen's ENTER: CX, BUTTON_Y + BUTTON_H / 2
			awaitLogin();
		}
		return app.ingame;
	}

	static boolean clickCardAndWait(boolean expected) throws Exception {
		int[] at = cardCentre();
		check(at != null, "the card is on the screen");
		if (at == null) {
			return false;
		}
		int tries = expected ? RETRIES : 1;
		for (int i = 0; i < tries && !app.ingame; i++) {
			if (i > 0) {
				Thread.sleep(RETRY_WAIT);
			}
			click(at[0], at[1], false);
			awaitLogin();
		}
		return app.ingame;
	}

	/** Up to 30 seconds, or until the client has something to say about it. */
	static void awaitLogin() throws Exception {
		for (int f = 0; f < 300 && !app.ingame; f++) {
			waitFrames(1);
			if (((String) get("loginMessage1")).length() > 0 && !((String) get("loginMessage1")).startsWith("Connecting")) {
				break;
			}
		}
	}

	/**
	 * The middle of the first saved card, found by asking TitleScreen rather than by working it out
	 * from its constants. A test that recomputes a layout is a test that passes while the layout
	 * moves - TitleScreenTest had eleven checks go stale that way when the card grew a portrait.
	 */
	static int[] cardCentre() {
		int count = LoginProfiles.all().size();
		int y = TitleScreen.PROFILE_Y + TitleScreen.PROFILE_H / 2;
		int from = -1;
		int to = -1;
		for (int x = 0; x < 765; x++) {
			if (TitleScreen.profileAt(x, y, count) == 0) {
				if (from < 0) {
					from = x;
				}
				to = x;
			}
		}
		if (from < 0) {
			return null;
		}
		// Clear of the x in the card's top band, which would remove the account instead.
		return new int[] { (from + to) / 2, y + 10 };
	}

	static String why() {
		String a = (String) get("loginMessage0");
		String b = (String) get("loginMessage1");
		return a.length() + b.length() == 0 ? "" : " (screen says: " + a + " / " + b + ")";
	}

	static String shape(String token) {
		return token == null ? "null" : token.length() + " chars";
	}

	static String fileText() {
		try {
			File f = new File(new File(System.getProperty("user.home"), ".deathplateau"), "profiles.properties");
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

	// ---------------------------------------------------------------- the usual reflection
	static Field field(String name) {
		for (Class<?> c = app.getClass(); c != null; c = c.getSuperclass()) {
			try {
				Field f = c.getDeclaredField(name);
				f.setAccessible(true);
				return f;
			} catch (NoSuchFieldException ignored) {
			}
		}
		throw new RuntimeException("no field " + name);
	}

	static Object get(String name) {
		try {
			return field(name).get(app);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	static int geti(String name) {
		return ((Number) get(name)).intValue();
	}

	static void set(String name, Object v) {
		try {
			field(name).set(app, v);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	static Object call(String name, Object... args) {
		try {
			for (Class<?> c = app.getClass(); c != null; c = c.getSuperclass()) {
				for (Method m : c.getDeclaredMethods()) {
					if (m.getName().equals(name) && m.getParameterTypes().length == args.length) {
						m.setAccessible(true);
						return m.invoke(app, args);
					}
				}
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		throw new RuntimeException("no method " + name);
	}

	static void hover(int x, int y) {
		app.mouseMoved(new MouseEvent(app, MouseEvent.MOUSE_MOVED, System.currentTimeMillis(), 0, x, y, 0, false));
	}

	static void click(int x, int y, boolean right) throws Exception {
		hover(x, y);
		waitFrames(2);
		int button = right ? MouseEvent.BUTTON3 : MouseEvent.BUTTON1;
		app.mousePressed(new MouseEvent(app, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), 0, x, y, 1, false, button));
		waitFrames(2);
		app.mouseReleased(new MouseEvent(app, MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(), 0, x, y, 1, false, button));
		waitFrames(3);
	}

	static void key(int code, char ch) throws Exception {
		app.keyPressed(new KeyEvent(app, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, code, ch));
		waitFrames(1);
		app.keyReleased(new KeyEvent(app, KeyEvent.KEY_RELEASED, System.currentTimeMillis(), 0, code, ch));
		waitFrames(1);
	}

	static void waitFrames(int n) throws Exception {
		int until = frames + n;
		long giveUp = System.currentTimeMillis() + 30000L;
		while (frames < until && System.currentTimeMillis() < giveUp) {
			Thread.sleep(10L);
		}
	}

	interface Until {
		boolean done();
	}

	static void waitFor(String what, long ms, Until until) throws Exception {
		long giveUp = System.currentTimeMillis() + ms;
		while (!until.done()) {
			if (System.currentTimeMillis() > giveUp) {
				// WHAT IT WAS WAITING ON, not just that it waited. "timed out waiting for the title
				// screen" sent me looking at the engine, the ports and my own last commit in turn,
				// and every one of those was fine: the client had simply not been given a cache to
				// draw one from. Each of these is one field, and between them they say which link
				// is missing - the file store, the download, or the draw.
				check(false, "timed out waiting for " + what + why()
					+ " [state=" + geti("titleScreenState") + " frames=" + frames
					+ " title=" + (get("imageTitle4") != null)
					+ " cache=" + (sign.signlink.cache_dat != null)
					+ " loading=" + get("lastProgressPercent") + "% " + get("lastProgressMessage")
					+ " lastFetch=" + get("lastFetchFrom") + "]");
				System.out.println();
				System.out.println("1 CHECK(S) FAILED");
				System.exit(1);
			}
			Thread.sleep(50L);
		}
	}
}
