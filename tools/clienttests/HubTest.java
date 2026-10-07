// Driven by tools/clienttests/run_hubtest.py.
//
// The hub downloads code and puts it where the client will run it, so this drives the real
// HubClient against a real HTTP server on localhost - a real index, real jars, real downloads -
// rather than a mock of one. What it is mostly checking is the refusals: the entry that would
// write outside the plugins folder, the url that is not http, the jar that does not match its
// checksum, the download that is not a jar at all. Those are the paths nobody exercises by
// clicking Install on something that works.
package jagex2.client.plugin.hub;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.List;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

public class HubTest {

	static int fails;

	static void check(boolean ok, String what) {
		System.out.println((ok ? "  ok   " : "FAIL   ") + what);
		if (!ok) {
			fails++;
		}
	}

	static byte[] goodJar;
	static byte[] notAJar = "this is not a jar".getBytes();
	static String goodSha;

	public static void main(String[] args) throws Exception {
		File dir = new File(args[0]);
		goodJar = java.nio.file.Files.readAllBytes(new File(args[1]).toPath());
		goodSha = sha256(goodJar);

		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		serve(server, "/plugin.jar", "application/java-archive", goodJar);
		serve(server, "/broken.jar", "application/java-archive", notAJar);
		server.start();
		String base = "http://127.0.0.1:" + server.getAddress().getPort();

		System.out.println("1. reading the index");
		indexTests(base);
		System.out.println();
		System.out.println("2. installing");
		installTests(base, dir);
		System.out.println();
		System.out.println("3. refusing what it should");
		refusalTests(base, dir);

		server.stop(0);
		System.out.println();
		System.out.println(fails == 0 ? "ALL PASS" : fails + " FAILED");
		System.exit(fails == 0 ? 0 : 1);
	}

	// ---------------------------------------------------------------- 1

	static void indexTests(String base) {
		HubIndex index = HubIndex.parse("{\"plugins\":[" + entry("good", base + "/plugin.jar", goodSha) + "]}");
		check(index.getEntries().size() == 1, "an index with one plugin reads one plugin");
		HubEntry entry = index.getEntries().get(0);
		check(entry.id.equals("good") && entry.name.equals("Good") && entry.version.equals("1.0"),
			"...with its fields");

		// The shape someone writes by hand the first time.
		check(HubIndex.parse("[" + entry("good", base + "/plugin.jar", "") + "]").getEntries().size() == 1,
			"a bare array is accepted as well as a plugins object");

		// Every one of these must be dropped, and the good one beside it must survive.
		String[][] bad = {
			{ "../../evil", "an id that climbs out of the folder" },
			{ "has space", "an id with a space in it" },
			{ "dots.in.id", "an id with dots in it" },
		};
		for (int i = 0; i < bad.length; i++) {
			HubIndex mixed = HubIndex.parse("[" + entry(bad[i][0], base + "/plugin.jar", "") + ","
				+ entry("good", base + "/plugin.jar", "") + "]");
			check(mixed.getEntries().size() == 1 && mixed.byId("good") != null, bad[i][1] + " is dropped");
		}

		HubIndex scheme = HubIndex.parse("[" + entry("local", "file:///etc/passwd", "") + ","
			+ entry("good", base + "/plugin.jar", "") + "]");
		check(scheme.getEntries().size() == 1 && scheme.byId("good") != null,
			"a url that is not http or https is dropped");

		HubIndex dupes = HubIndex.parse("[" + entry("good", base + "/plugin.jar", "") + ","
			+ entry("good", base + "/broken.jar", "") + "]");
		check(dupes.getEntries().size() == 1, "a duplicate id is dropped, not guessed at");

		boolean threw = false;
		try {
			HubIndex.parse("{\"plugins\": [ {\"id\": }");
		} catch (RuntimeException error) {
			threw = true;
		}
		check(threw, "malformed JSON throws rather than returning something wrong");

		// A hostile index must not be able to run the parser out of stack.
		StringBuilder deep = new StringBuilder();
		for (int i = 0; i < 2000; i++) {
			deep.append('[');
		}
		threw = false;
		try {
			HubIndex.parse(deep.toString());
		} catch (RuntimeException error) {
			threw = true;
		}
		check(threw, "an index nested 2000 deep is refused, not a stack overflow");
	}

	// ---------------------------------------------------------------- 2

	static void installTests(String base, File dir) throws Exception {
		HubState state = new HubState(dir);
		HubEntry entry = one(entry("good", base + "/plugin.jar", goodSha));

		check(state.installedVersion(dir, "good") == null, "nothing is installed to begin with");

		final long[] seen = new long[2];
		HubClient.install(entry, dir, state, new HubClient.Progress() {

			public void onProgress(long done, long total) {
				seen[0] = done;
				seen[1] = total;
			}
		});

		File jar = new File(dir, "good.jar");
		check(jar.isFile(), "the jar lands in the plugins folder");
		check(jar.length() == goodJar.length, "...whole (" + jar.length() + " of " + goodJar.length + " bytes)");
		check(seen[0] == goodJar.length, "...and progress was reported to the end");
		check("1.0".equals(state.installedVersion(dir, "good")), "the version is recorded");

		// A new HubState over the same folder is what a restart does.
		check("1.0".equals(new HubState(dir).installedVersion(dir, "good")),
			"and the record survives a restart");

		// The jar is loadable as a plugin: the whole point of installing it.
		List<jagex2.client.plugin.PluginLoader.Found> found = jagex2.client.plugin.PluginLoader.scan(dir);
		check(found.size() >= 1, "the installed jar loads as a plugin (" + found.size() + " found)");

		HubClient.remove("good", dir, state);
		check(!jar.isFile(), "removing deletes the jar");
		check(state.installedVersion(dir, "good") == null, "...and forgets the version");

		// A hand-deleted jar must not keep reading as installed.
		HubClient.install(entry, dir, state, null);
		new File(dir, "good.jar").delete();
		check(state.installedVersion(dir, "good") == null,
			"a jar deleted by hand reads as not installed");
	}

	// ---------------------------------------------------------------- 3

	static void refusalTests(String base, File dir) {
		HubState state = new HubState(dir);

		// The checksum is the one thing standing between the index and whatever answered the url.
		String wrong = "0000000000000000000000000000000000000000000000000000000000000000";
		check(!installs(one(entry("mismatch", base + "/plugin.jar", wrong)), dir, state),
			"a jar that does not match its checksum is refused");
		check(!new File(dir, "mismatch.jar").isFile(), "...and is not left on disk");

		check(!installs(one(entry("broken", base + "/broken.jar", "")), dir, state),
			"a download that is not a jar is refused");
		check(!new File(dir, "broken.jar").isFile(), "...and is not left on disk");

		check(!installs(one(entry("missing", base + "/nothing.jar", "")), dir, state),
			"a url that 404s is refused");

		boolean threw = false;
		try {
			HubClient.remove("../../client", dir, state);
		} catch (IOException error) {
			threw = true;
		}
		check(threw, "remove refuses an id that is not an id");

		threw = false;
		try {
			HubClient.fetchIndex("file:///etc/passwd");
		} catch (IOException error) {
			threw = true;
		}
		check(threw, "fetching an index refuses a non-http url");

		// An install that fails must leave a working plugin working.
		try {
			HubClient.install(one(entry("keep", base + "/plugin.jar", goodSha)), dir, state, null);
			long before = new File(dir, "keep.jar").length();
			installs(one(entry("keep", base + "/broken.jar", "")), dir, state);
			File kept = new File(dir, "keep.jar");
			check(kept.isFile() && kept.length() == before,
				"a failed update leaves the installed version untouched");
		} catch (IOException error) {
			check(false, "a failed update leaves the installed version untouched (" + error + ")");
		}
	}

	// ---------------------------------------------------------------- plumbing

	static boolean installs(HubEntry entry, File dir, HubState state) {
		try {
			HubClient.install(entry, dir, state, null);
			return true;
		} catch (IOException error) {
			return false;
		}
	}

	static HubEntry one(String json) {
		return HubIndex.parse("[" + json + "]").getEntries().get(0);
	}

	static String entry(String id, String url, String sha) {
		return "{\"id\":\"" + id + "\",\"name\":\"" + Character.toUpperCase(id.charAt(0)) + id.substring(1)
			+ "\",\"author\":\"Tester\",\"version\":\"1.0\",\"description\":\"A plugin\",\"url\":\"" + url
			+ "\"" + (sha.length() > 0 ? ",\"sha256\":\"" + sha + "\"" : "") + "}";
	}

	static void serve(HttpServer server, String path, final String type, final byte[] body) {
		server.createContext(path, new HttpHandler() {

			public void handle(HttpExchange exchange) throws IOException {
				exchange.getResponseHeaders().add("Content-Type", type);
				exchange.sendResponseHeaders(200, body.length);
				OutputStream out = exchange.getResponseBody();
				out.write(body);
				out.close();
			}
		});
	}

	static String sha256(byte[] bytes) throws Exception {
		byte[] hash = java.security.MessageDigest.getInstance("SHA-256").digest(bytes);
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < hash.length; i++) {
			out.append(Character.forDigit((hash[i] & 0xFF) >> 4, 16));
			out.append(Character.forDigit(hash[i] & 15, 16));
		}
		return out.toString();
	}
}
