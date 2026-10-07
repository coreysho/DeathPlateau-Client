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
		System.out.println("4. what a failed fetch says about itself");
		reportTests();

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

	// ---------------------------------------------------------------- 4

	/*
	 * A SECOND PLAYER GOT THE SAME FOUR WORDS and they meant something else. "connection problem"
	 * is every IOException the fetch can throw: a name that does not resolve, a port that refuses,
	 * and a connect that times out - a DNS or filtering problem, a dead tunnel, and a blocked
	 * route, which are three different fixes. The loading screen has room for four words, so the
	 * log carries the rest, and these are the checks that it does. The line is a pure function for
	 * the same reason the port rule is; the wiring that reaches it is read out of the source.
	 */
	static void reportTests() {
		String from = "http://death-plateau.playit.plus:80/crc12345678-377";
		// The name in the exception is deliberately NOT the one in the address. Asserting on a
		// name both of them carry is an assertion the address alone satisfies, and the first run
		// of this suite proved it: dropping the message entirely left the check still passing.
		String named = "a-name-the-address-does-not-carry.invalid";
		check(from.indexOf(named) < 0, "the probe name cannot come from the address");

		// The three failures that used to read alike come out distinguishable.
		String dns = Client.fetchFailure(from, new java.net.UnknownHostException(named));
		String refused = Client.fetchFailure(from, new java.net.ConnectException("Connection refused: connect"));
		String timeout = Client.fetchFailure(from, new java.net.SocketTimeoutException("connect timed out"));

		check(dns.indexOf("UnknownHostException") >= 0, "a name that does not resolve says so");
		check(dns.indexOf(named) >= 0, "...and says which name, from the exception and not the address");
		check(refused.indexOf("ConnectException") >= 0 && refused.indexOf("refused") >= 0,
			"a refused port says so, and carries its own words");
		check(timeout.indexOf("SocketTimeoutException") >= 0 && timeout.indexOf("timed out") >= 0,
			"a timed-out connect says so, and carries its own words");
		check(!dns.equals(refused) && !refused.equals(timeout) && !dns.equals(timeout),
			"...and no two of the three read alike, which is the whole point");

		// The address travels with the reason. Without it a log cannot tell a client pointed at the
		// wrong host from a host that is down, and those want opposite fixes too.
		check(dns.indexOf(from) >= 0, "the reason carries the address it was dialling");

		// An exception with no message must not append the word "null" to the line.
		String bare = Client.fetchFailure(from, new java.io.EOFException());
		check(bare.indexOf("null") < 0, "a message-less exception adds no 'null' (" + bare + ")");
		check(bare.indexOf("EOFException") >= 0, "...and still names its class");

		String source = read("src/main/java/jagex2/client/Client.java");

		// openUrl cannot return without naming where it went: three ways out, three assignments,
		// each before the connect that can throw. A branch added later without one is the exact
		// regression this guards, and only the source can see it.
		int open = source.indexOf("public DataInputStream openUrl(");
		int shut = open < 0 ? -1 : source.indexOf("\n\t}", open);
		String body = open < 0 || shut < 0 ? "" : source.substring(open, shut);
		check(body.length() > 0, "openUrl is readable");
		int assigns = count(body, "this.lastFetchFrom =");
		int returns = count(body, "return ");
		check(returns == 3 && assigns == returns,
			"every one of openUrl's " + returns + " ways out names its address first ("
				+ assigns + " assignments)");

		// The field says something before the first fetch. A null would print the word "null" and
		// leave a report no better off than it was.
		// Anchored to the DECLARATION. An unanchored pattern finds openUrl's own assignments and
		// passes however the field is declared - which is how the first run of this suite let a
		// null declaration through.
		java.util.regex.Matcher init = java.util.regex.Pattern
			.compile("public String lastFetchFrom\\s*=\\s*(\"[^\"]*\"|[A-Za-z0-9_]+)\\s*;")
			.matcher(source);
		boolean declared = init.find();
		String initial = declared ? init.group(1) : "(no declaration found)";
		check(declared && initial.startsWith("\"") && initial.length() > 2,
			"a client that has fetched nothing still has something to say (" + initial + ")");

		// And getJagCrc's four catches all reach it - the one that fires is the one we never see.
		int crc = source.indexOf("public void getJagCrc() {");
		int crcEnd = crc < 0 ? -1 : source.indexOf("\n\t}", crc);
		String crcBody = crc < 0 || crcEnd < 0 ? "" : source.substring(crc, crcEnd);
		check(crcBody.length() > 0, "getJagCrc is readable");
		// Every catch that paints one of the four words also says why. Counting catches outright
		// would count the retry's own sleep, which has nothing to report; what matters is that no
		// message reaches the screen without its reason reaching the log.
		String[] painted = { "EOF problem", "connection problem", "logic problem" };
		for (int i = 0; i < painted.length; i++) {
			int at = crcBody.indexOf("var5 = \"" + painted[i] + "\"");
			int close = at < 0 ? -1 : crcBody.indexOf("\n\t\t\t}", at);
			String arm = at < 0 || close < 0 ? "" : crcBody.substring(at, close);
			check(arm.length() > 0 && arm.indexOf("this.cacheFetchFailed(") >= 0,
				"\"" + painted[i] + "\" does not reach the screen without its reason reaching the log");
		}
		check(count(crcBody, "this.cacheFetchFailed(") == painted.length,
			"...and nothing else reports (" + count(crcBody, "this.cacheFetchFailed(") + " calls)");
		check(crcBody.indexOf("this.openUrl(path)") >= 0,
			"...and the path it logs is the path it asked for");

		// What a plain launch would print. A malformed address here is a silent misdirect: every
		// fetch fails and the log names something that was never a URL.
		try {
			java.net.URL url = new java.net.URL(Client.webAddress());
			check(url.getHost() != null && url.getHost().length() > 0,
				"a plain launch's cache address is a URL with a host (" + Client.webAddress() + ")");
		} catch (java.net.MalformedURLException bad) {
			check(false, "a plain launch's cache address is not a URL: " + Client.webAddress());
		}
		check(Client.gameAddress().indexOf(Client.SERVER_HOST) >= 0
				&& Client.gameAddress().indexOf("" + Client.GAME_PORT) >= 0,
			"and the game address names host and port (" + Client.gameAddress() + ")");

		// getCodeBase must read the same expression the log prints, or the log misdirects.
		int base = source.indexOf("public URL getCodeBase() {");
		int baseEnd = base < 0 ? -1 : source.indexOf("\n\t}", base);
		String baseBody = base < 0 || baseEnd < 0 ? "" : source.substring(base, baseEnd);
		check(baseBody.indexOf("new URL(webAddress())") >= 0,
			"getCodeBase dials the address the log names, from the one expression");
		check(baseBody.indexOf("WEB_HOST") < 0 && baseBody.indexOf("WEB_URL") < 0,
			"...and does not build a second one of its own");

		// The startup banner carries both addresses, so a log answers this on its own first lines.
		int main = source.indexOf("public static void main(String[] args) {");
		String head = main < 0 ? "" : source.substring(main, Math.min(source.length(), main + 3000));
		check(head.indexOf("webAddress()") >= 0 && head.indexOf("gameAddress()") >= 0,
			"a launch logs both addresses before the first frame");
	}

	static int count(String haystack, String needle) {
		int n = 0;
		for (int at = haystack.indexOf(needle); at >= 0; at = haystack.indexOf(needle, at + 1)) {
			n++;
		}
		return n;
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
