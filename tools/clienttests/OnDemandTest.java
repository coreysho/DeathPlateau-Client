/*
 * Headless test for how fast the cache is fetched, and for the one property that makes it safe to
 * fetch it faster.
 *
 * WHY THIS EXISTS. Four complaints turned out to be one thing: items taking seconds to appear when
 * equipped, a fast weapon switch landing in batches rather than at once, an inventory slot drawn
 * blank until its model turned up, and an empty equipment tab on login. None of them is a server
 * bug - the item is in the inventory the whole time - it is the MODEL not being here yet.
 *
 * OnDemand.run() sleeps, then spins an inner loop while anything happened. read() is non-blocking,
 * so the moment the socket is empty the inner loop goes quiet and the thread sleeps again. That
 * makes it one wave of requests per sleep: issue up to N files, wait out the round trip in a sleep,
 * collect them, issue the next N. Throughput is (files per wave) x (waves per second), and both
 * were 2004's numbers for a dial-up client talking to Jagex over the internet.
 *
 * None of that can be tested as a pure function - it is a thread, a socket and a sleep - so what is
 * tested is what CAN be: the constants are named rather than scattered literals, the two counters
 * are compared against the named one in every place, and above all that every request still
 * assembles into its OWN buffer. That last one is why a bigger window is safe at all: files arrive
 * interleaved, and a shared assembly buffer would turn a wider window into corrupted models rather
 * than faster ones. If somebody ever simplifies that away, this is what says so.
 */
package jagex2.client;

public class OnDemandTest {

	static int fails;

	static void check(boolean ok, String what) {
		System.out.println((ok ? "  ok   " : "FAIL   ") + what);
		if (!ok) {
			fails++;
		}
	}

	public static void main(String[] args) {
		String src = read("src/main/java/jagex2/io/OnDemand.java");

		System.out.println("1. the numbers are named, not scattered");
		check(src.indexOf("MAX_IN_FLIGHT = 48") >= 0, "the window is 48 files in flight");
		check(src.indexOf("CYCLE_MS = 10") >= 0, "a wave every 10ms while something is wanted");
		check(src.indexOf("IDLE_CYCLE_MS = 20") >= 0, "and every 20ms while prefetching");
		check(count(src, "MAX_IN_FLIGHT") >= 5, "the window is read everywhere, not retyped: " + count(src, "MAX_IN_FLIGHT") + " uses");

		System.out.println();
		System.out.println("2. no bare counter literals left behind");
		// The three places that used to say 10. A literal surviving here is a window that is
		// 48 in one place and 10 in another, which is worse than either.
		check(src.indexOf("urgentCount < 10") < 0, "the urgent window is not a literal");
		check(src.indexOf("requestCount >= 10") < 0, "the prefetch window is not a literal");
		check(src.indexOf("requestCount == 10") < 0, "and its early return is not one either");
		// == would stop being a stop the moment anything else nudged the count past it.
		check(src.indexOf("requestCount >= MAX_IN_FLIGHT") >= 0, "the prefetch stop compares with >=, not ==");

		System.out.println();
		System.out.println("3. the sleep is the only thing that paces a wave");
		check(src.indexOf("int del = CYCLE_MS") >= 0, "run() sleeps for the named cycle");
		check(src.indexOf("del = IDLE_CYCLE_MS") >= 0, "and the longer one only on the prefetch path");

		System.out.println();
		System.out.println("4. WHY A WIDER WINDOW IS SAFE: every file assembles into its own buffer");
		// read() looks the incoming chunk's (archive,file) up in `pending` and writes into THAT
		// request's array at THAT chunk's offset. Interleaving is therefore fine. A shared buffer
		// here would make a wide window a corruption bug rather than a speed one.
		check(src.indexOf("this.current.data = new byte[size]") >= 0, "a request allocates its own array on part 0");
		check(src.indexOf("dst = this.current.data") >= 0, "and chunks are written into that array");
		check(src.indexOf("off = this.partOffset") >= 0, "at the offset the chunk names");
		int read = src.indexOf("public void read()");
		String body = read < 0 ? "" : src.substring(read, Math.min(src.length(), read + 2500));
		check(body.indexOf("req.archive == archive && req.file == file") >= 0,
			"and each chunk is matched to its own request before any of that");

		System.out.println();
		System.out.println("5. the window stays under what the server will hold");
		// engine/src/engine/OnDemandThread.ts caps a client's queue. The two numbers live in
		// different repositories, so this only runs where both are checked out - and says which it
		// is rather than passing quietly, because a check that cannot see its subject is not a pass.
		String server = readIfPresent("../engine/src/engine/OnDemandThread.ts");
		if (server.length() == 0) {
			System.out.println("  skip  the engine is not beside this checkout - server cap unchecked");
		} else {
			int cap = intAfter(server, "MAX_PENDING_PER_CLIENT = ");
			check(cap > 0, "the server's per-client cap was found: " + cap);
			check(cap > 48, "48 in flight is well under it");
		}

		System.out.println();
		System.out.println(fails == 0 ? "ALL PASS" : fails + " FAILED");
		System.exit(fails == 0 ? 0 : 1);
	}

	static int intAfter(String haystack, String needle) {
		int at = haystack.indexOf(needle);
		if (at < 0) {
			return -1;
		}
		int from = at + needle.length();
		int to = from;
		while (to < haystack.length() && Character.isDigit(haystack.charAt(to))) {
			to++;
		}
		return to == from ? -1 : Integer.parseInt(haystack.substring(from, to));
	}

	static int count(String haystack, String needle) {
		int n = 0;
		for (int at = haystack.indexOf(needle); at >= 0; at = haystack.indexOf(needle, at + 1)) {
			n++;
		}
		return n;
	}

	static String readIfPresent(String path) {
		try {
			return new String(java.nio.file.Files.readAllBytes(
				new java.io.File(System.getProperty("dp.root", "."), path).toPath()), "UTF-8");
		} catch (Throwable missing) {
			return "";
		}
	}

	static String read(String path) {
		String out = readIfPresent(path);
		if (out.length() == 0) {
			check(false, "cannot read " + path);
		}
		return out;
	}
}
