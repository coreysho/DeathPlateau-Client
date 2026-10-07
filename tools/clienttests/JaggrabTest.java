/*
 * Headless test for which endpoint the cache is fetched from.
 *
 * WHY THIS EXISTS. A player could not get past the loading screen - "connection problem - Will
 * retry in 15 secs." - while the same build worked for the server's owner. The message comes from
 * getJagCrc, which fetches the cache checksums before the title screen, and the retry loop
 * alternates between HTTP and JAGGRAB on every failure. The JAGGRAB half was hardcoded to port
 * 43595 on SERVER_HOST: the port the server listens on at home, which no tunnel forwards, and the
 * GAME host rather than the web one. On the LAN both mistakes cancel out, which is exactly why
 * nobody saw it; through a tunnel half of every cache retry failed before it started.
 *
 * The rule is a pure function, so it is tested as one. The socket it ends up opening is not
 * something a test can check without a server on the other end, and the part that was wrong was
 * never the socket.
 */
package jagex2.client;

public class JaggrabTest {

	static int fails;

	static void check(boolean ok, String what) {
		System.out.println((ok ? "  ok   " : "FAIL   ") + what);
		if (!ok) {
			fails++;
		}
	}

	public static void main(String[] args) {
		System.out.println("1. which port JAGGRAB goes to");
		portTests();
		System.out.println();
		System.out.println("2. when it may be used at all");
		usableTests();
		System.out.println();
		System.out.println("3. what this build resolved to, and where the cache comes from");
		liveTests();

		System.out.println();
		System.out.println(fails == 0 ? "ALL PASS" : fails + " FAILED");
		System.exit(fails == 0 ? 0 : 1);
	}

	// ---------------------------------------------------------------- 1

	static void portTests() {
		// THE CASE THAT WAS BROKEN. Nothing given and no host given means the default tunnels,
		// which forward the game port and the web port and nothing else. JAGGRAB has nowhere to
		// go, so it must not be tried - every attempt was a guaranteed failure that doubled the
		// backoff and printed an error on a retry HTTP would have served.
		check(Client.jaggrabPort(null, false) == 0,
			"through the tunnels, JAGGRAB is off (" + Client.jaggrabPort(null, false) + ")");

		// Pointed straight at the server - the LAN, or Tailscale - 43595 is a real open port.
		check(Client.jaggrabPort(null, true) == 43595,
			"pointed at the server itself, it is the server's own 43595 ("
				+ Client.jaggrabPort(null, true) + ")");

		// An explicit setting wins either way, which is how someone who HAS tunnelled it says so.
		check(Client.jaggrabPort("55000", false) == 55000,
			"an explicit port is used even through the tunnels");
		check(Client.jaggrabPort("55000", true) == 55000, "...and in place of the server's own");
		check(Client.jaggrabPort(" 55000 ", false) == 55000, "...with whitespace forgiven");

		// Explicitly off, for someone whose server does not serve JAGGRAB at all.
		check(Client.jaggrabPort("0", true) == 0, "an explicit 0 turns it off on the LAN too");

		// PARSED AT CLASS LOAD, so a throw here is not one feature off - it is an
		// ExceptionInInitializerError before Client exists and a client that does not start.
		// Caught rather than allowed to propagate, so that failure has a name instead of being
		// a dead test run.
		for (String[] nonsense : new String[][] { { "banana", "a setting that is not a number" },
			{ "", "an empty setting" }, { "   ", "a setting of only spaces" },
			{ "-1", "a negative port" }, { "0x80", "a port written in hex" },
			{ "99999", "a port past the end of the range" },
			{ "65536", "a port one past the last real one" } }) {
			int got = -1;
			boolean threw = false;
			try {
				got = Client.jaggrabPort(nonsense[0], true);
			} catch (Throwable error) {
				threw = true;
			}
			check(!threw && got == 0, nonsense[1] + " is off, and does not throw out of class"
				+ " load (" + (threw ? "threw" : String.valueOf(got)) + ")");
		}
	}

	// ---------------------------------------------------------------- 2

	static void usableTests() {
		check(Client.jaggrabUsable(43595, null), "with a port and no websocket, JAGGRAB is usable");
		check(!Client.jaggrabUsable(0, null), "with no port it is not");
		// In a browser the one WebSocket the page gives us is the GAME stream. Writing
		// "JAGGRAB /title" into it would make that the first thing the server reads from what it
		// thinks is a login.
		check(!Client.jaggrabUsable(43595, "wss://example/rs2.cgi"),
			"and never over a websocket, which is the game stream");
		check(!Client.jaggrabUsable(0, "wss://example/rs2.cgi"), "nor with neither");
	}

	// ---------------------------------------------------------------- 3

	static void liveTests() {
		// What a player launching the jar with no properties actually gets. The runner gives
		// this JVM no lostcity.* settings, which is the case that was broken.
		check(Client.JAGGRAB_PORT == 0,
			"a plain launch resolves JAGGRAB to off (" + Client.JAGGRAB_PORT + ")");
		check(!Client.jaggrabUsable(Client.JAGGRAB_PORT, Client.WS_URL),
			"...so every cache retry is HTTP, the one transport a tunnel carries");

		// The cache comes from the WEB host, and the game from the game host. They are different
		// hosts, which is the other half of what was wrong: JAGGRAB was being sent to the game
		// one.
		check(!Client.WEB_HOST.equals(Client.SERVER_HOST),
			"the web host and the game host are different machines here (" + Client.WEB_HOST
				+ " vs " + Client.SERVER_HOST + ")");

		// And the source no longer asks the game host for cache files.
		String source = read("src/main/java/jagex2/client/Client.java");
		int open = source.indexOf("public DataInputStream openUrl(");
		int end = open < 0 ? -1 : source.indexOf("\n\t}", open);
		String body = open < 0 || end < 0 ? "" : source.substring(open, end);
		check(body.length() > 0, "openUrl is where it was expected in the source");
		check(body.indexOf("openSocket(43595)") < 0,
			"openUrl no longer opens a hardcoded 43595 on the game host");
		check(body.indexOf("WEB_HOST") >= 0,
			"...it uses the web host for the web server's own protocol");
		check(body.indexOf("jaggrabUsable(") >= 0,
			"...and skips the attempt entirely when there is nowhere to send it");
	}

	static String read(String path) {
		try {
			return new String(java.nio.file.Files.readAllBytes(
				new java.io.File(System.getProperty("dp.root", "."), path).toPath()), "UTF-8");
		} catch (Throwable missing) {
			check(false, "cannot read " + path + " (" + missing + ")");
			return "";
		}
	}
}
