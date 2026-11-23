/*
 * Headless test for the launcher's Discord sign-in.
 *
 * WHAT THIS GUARDS. The loopback hand-over is the one piece of this feature that had never run
 * anywhere before: a desktop program listens on 127.0.0.1, a browser arrives with the sign-in in a
 * query string, and the launcher has to take it out and believe it. Compiling says nothing at all
 * about whether that works, and running it by hand needs a browser, a website and a Discord
 * account. So this plays all three parts:
 *
 *   * a fake website on a loopback socket, which answers /launcher/accounts the way the real route
 *     does - 200 with a list of names, 401 for a sign-in that is gone, 503 for "cannot tell";
 *
 *   * a fake browser (the Opener seam), which is handed the url the launcher wants opened and does
 *     to it what a browser would: follows it, and knocks on the launcher's own port with a token.
 *
 * The four things that have to hold, none of them visible from the launcher's window:
 *
 *   * THE NONCE DECIDES, not the shape of what arrives. A socket on a player's own machine can be
 *     knocked on by anything running there, and without the check a page they happened to have open
 *     could hand this launcher somebody else's sign-in - which would put a stranger's character
 *     names on their login screen;
 *
 *   * 401 FORGETS THE SIGN-IN AND 503 MUST NOT. "The server did not answer" is a restart, a flat
 *     wifi, a box being deployed to; treating it as "you are signed out" would send players
 *     through a Discord sign-in because their connection blinked;
 *
 *   * WHAT IS WRITTEN DOWN IS A TOKEN AND SOME NAMES, and nothing that could log in. Read out of
 *     the actual bytes of the actual file, the way LoginProfilesTest does it;
 *
 *   * nothing throws. A sign-in that fails has to leave a launcher that still starts the game.
 */
package lostcity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class DiscordSignInTest {

	static int fails;

	static void check(boolean ok, String what) {
		System.out.println((ok ? "  ok   " : "FAIL   ") + what);
		if (!ok) {
			fails++;
		}
	}

	/** A token of the server's shape: 20 of [a-z0-9]. */
	static final String TOKEN = "a1b2c3d4e5f6g7h8i9j0";
	static final String TOKEN2 = "z9y8x7w6v5u4t3s2r1q0";

	static File home;

	static void freshHome(String name) {
		home = new File(System.getProperty("java.io.tmpdir"), "dp-discord-" + name + "-" + System.nanoTime());
		new File(home, ".deathplateau").mkdirs();
		System.setProperty("user.home", home.getAbsolutePath());
		DiscordSignIn.reload();
	}

	static File launcherFile() {
		return new File(new File(home, ".deathplateau"), "launcher.properties");
	}

	static String fileText() throws IOException {
		File f = launcherFile();
		if (!f.isFile()) {
			return "";
		}
		byte[] bytes = new byte[(int) f.length()];
		InputStream in = new java.io.FileInputStream(f);
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

	// ---------------------------------------------------------------- the fake website

	/**
	 * Answers /launcher/accounts, and counts how often it was asked. `status` and `names` are what
	 * it will say next, so a case can make the server go wrong mid-test.
	 */
	static final class Site implements Runnable {

		final ServerSocket server;
		volatile int status = 200;
		volatile String names = "\"lord_fooby\",\"corey\"";
		volatile int asked;
		volatile String lastAuth = "";
		volatile String lastMethod = "";
		volatile boolean stop;

		Site() throws IOException {
			this.server = new ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"));
		}

		int port() {
			return this.server.getLocalPort();
		}

		public void run() {
			while (!this.stop) {
				Socket socket;
				try {
					socket = this.server.accept();
				} catch (IOException done) {
					return;
				}
				try {
					StringBuilder head = new StringBuilder();
					InputStream in = socket.getInputStream();
					int c;
					// Headers only; neither route this stands in for has a request body worth reading.
					while ((c = in.read()) != -1 && head.length() < 8192) {
						head.append((char) c);
						if (head.length() >= 4 && head.substring(head.length() - 4).equals("\r\n\r\n")) {
							break;
						}
					}
					String request = head.toString();
					this.asked++;
					this.lastMethod = request.split(" ")[0];
					for (String line : request.split("\r\n")) {
						if (line.toLowerCase().startsWith("authorization:")) {
							this.lastAuth = line.substring("authorization:".length()).trim();
						}
					}
					String body = this.status == 200
						? "{\"linked\":true,\"accounts\":[" + this.names + "]}"
						: this.status == 401 ? "{\"error\":\"signedout\"}" : "{\"error\":\"unavailable\"}";
					byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
					OutputStream out = socket.getOutputStream();
					out.write(("HTTP/1.1 " + this.status + " x\r\nContent-Type: application/json\r\nContent-Length: "
						+ bytes.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
					out.write(bytes);
					out.flush();
				} catch (IOException ignored) {
				} finally {
					try {
						socket.close();
					} catch (IOException ignored) {
					}
				}
			}
		}

		void close() {
			this.stop = true;
			try {
				this.server.close();
			} catch (IOException ignored) {
			}
		}
	}

	/** What a browser does with the url: knock on the launcher's loopback with this query. */
	static DiscordSignIn.Opener browser(final String query) {
		return new DiscordSignIn.Opener() {

			public boolean open(String url) {
				final int at = url.indexOf("port=");
				if (at < 0) {
					return true; // the paste path: a browser would show a code and nothing comes back
				}
				final int port = Integer.parseInt(url.substring(at + 5).split("&")[0]);
				final String nonce = url.contains("launcher=") ? url.substring(url.indexOf("launcher=") + 9).split("&")[0] : "";
				Thread knock = new Thread(new Runnable() {

					public void run() {
						get("http://127.0.0.1:" + port + "/launcher?" + query.replace("{nonce}", nonce));
					}
				}, "fake-browser");
				knock.setDaemon(true);
				knock.start();
				return true;
			}
		};
	}

	/** Follows one redirect, as a browser would, and throws nothing. */
	static void get(String url) {
		try {
			HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
			c.setInstanceFollowRedirects(true);
			c.setConnectTimeout(5000);
			c.setReadTimeout(5000);
			c.getResponseCode();
			InputStream in = c.getErrorStream() == null ? c.getInputStream() : c.getErrorStream();
			if (in != null) {
				while (in.read() != -1) {
					// drained, so the launcher's write completes rather than blocking on a full pipe
				}
				in.close();
			}
			c.disconnect();
		} catch (Exception ignored) {
		}
	}

	/** Presses Cancel a moment from now, from another thread, as the window does. */
	static void socketCancel() {
		Thread later = new Thread(new Runnable() {

			public void run() {
				try {
					Thread.sleep(500);
				} catch (InterruptedException ignored) {
				}
				DiscordSignIn.cancel();
			}
		}, "fake-cancel");
		later.setDaemon(true);
		later.start();
	}

	// ---------------------------------------------------------------- the cases

	public static void main(String[] args) throws Exception {
		Site site = new Site();
		Thread serving = new Thread(site, "fake-site");
		serving.setDaemon(true);
		serving.start();
		// BEFORE DiscordSignIn IS TOUCHED: its SITE is a static final read at class load.
		System.setProperty("lostcity.site", "http://127.0.0.1:" + site.port());

		System.out.println("-- the loopback hand-over");
		freshHome("loopback");
		DiscordSignIn.Result result = DiscordSignIn.signIn(browser("state={nonce}&token=" + TOKEN));
		check(result.ok, "a sign-in through the loopback is accepted");
		check(DiscordSignIn.signedIn(), "and the launcher is signed in afterwards");
		check(TOKEN.equals(DiscordSignIn.token()), "the token that arrived is the token kept");
		List<String> names = DiscordSignIn.accounts();
		check(names.size() == 2 && names.get(0).equals("lord_fooby") && names.get(1).equals("corey"),
			"the character names come back in the order the server sent them");
		// Nothing to say: the launcher's own headline shows the count, and a line repeating it is
		// two sentences saying one thing.
		check(result.message.length() == 0, "with nothing added underneath: " + result.message);
		check(site.lastAuth.equals("Bearer " + TOKEN), "the token went in the Authorization header, not the url");

		System.out.println("-- what is written down");
		String text = fileText();
		check(text.contains(TOKEN), "the token is in launcher.properties");
		check(text.contains("lord_fooby") && text.contains("corey"), "...with the names");
		// The word "password" IS in there - the header says it is not one - so what is checked is
		// that no password-shaped field was written: no saved-login profile block, no secret.
		check(!text.contains("profile.") && !text.toLowerCase().contains("password="),
			"and no field that is or could hold a password");
		check(text.toLowerCase().contains("not a password"), "the file's own header says what it is worth");

		System.out.println("-- a knock that is not ours");
		freshHome("nonce");
		result = DiscordSignIn.signIn(new DiscordSignIn.Opener() {

			public boolean open(String url) {
				final int at = url.indexOf("port=");
				final int port = Integer.parseInt(url.substring(at + 5).split("&")[0]);
				final String nonce = url.substring(url.indexOf("launcher=") + 9).split("&")[0];
				Thread knock = new Thread(new Runnable() {

					public void run() {
						// Something else on this machine, with a token of the right shape and the
						// wrong nonce. It must be ignored - and the real one after it must still work.
						get("http://127.0.0.1:" + port + "/launcher?state=notours&token=" + TOKEN2);
						get("http://127.0.0.1:" + port + "/launcher?state=" + nonce + "&token=" + TOKEN);
					}
				}, "fake-browser");
				knock.setDaemon(true);
				knock.start();
				return true;
			}
		});
		check(result.ok, "a sign-in still completes when something else knocked first");
		check(TOKEN.equals(DiscordSignIn.token()), "and it is OUR token that was kept, not the other one");

		System.out.println("-- the website said no");
		freshHome("cancelled");
		result = DiscordSignIn.signIn(browser("state={nonce}&error=cancelled"));
		check(!result.ok, "a cancelled sign-in is not a sign-in");
		check(!DiscordSignIn.signedIn(), "and nothing is signed in afterwards");
		check(result.message.toLowerCase().contains("cancel"), "said in words: " + result.message);

		System.out.println("-- Cancel, which is not a failure");
		freshHome("cancel");
		// A browser that opens and from which nothing ever comes back, and somebody who then
		// presses Cancel rather than waiting out the five minutes.
		socketCancel();
		result = DiscordSignIn.signIn(new DiscordSignIn.Opener() {

			public boolean open(String url) {
				return true; // opened, and the player then thinks better of it
			}
		});
		check(!result.ok, "a cancelled wait is not a sign-in");
		check(result.message.toLowerCase().contains("cancelled"),
			"AND IS NOT REPORTED AS A FAILURE - Cancel closes the socket exactly as a timeout does: "
				+ result.message);

		System.out.println("-- the pasted code");
		freshHome("paste");
		result = DiscordSignIn.paste("  " + TOKEN.toUpperCase() + "  ");
		check(result.ok, "a code pasted with spaces and in capitals is accepted");
		check(TOKEN.equals(DiscordSignIn.token()), "and stored in the shape the server minted it");
		freshHome("paste-bad");
		result = DiscordSignIn.paste("not-a-code");
		check(!result.ok && result.paste, "a code of the wrong shape is refused without asking the server");
		check(!DiscordSignIn.signedIn(), "and nothing is signed in");

		System.out.println("-- a code the server does not know");
		freshHome("paste-401");
		site.status = 401;
		result = DiscordSignIn.paste(TOKEN);
		check(!result.ok, "a 401 refuses the code");
		check(!DiscordSignIn.signedIn(), "and keeps nothing");

		System.out.println("-- 401 forgets the sign-in, 503 does not");
		freshHome("refresh-401");
		site.status = 200;
		DiscordSignIn.paste(TOKEN);
		site.status = 401;
		check(!DiscordSignIn.refresh(), "refresh says it is signed out");
		check(!DiscordSignIn.signedIn(), "and the token is gone - /unlink or an expiry means start again");
		check(!launcherFile().isFile(), "the file goes with it, so there is one state and not two");

		freshHome("refresh-503");
		site.status = 200;
		DiscordSignIn.paste(TOKEN);
		site.status = 503;
		check(DiscordSignIn.refresh(), "a server that cannot answer leaves the sign-in alone");
		check(DiscordSignIn.signedIn() && TOKEN.equals(DiscordSignIn.token()), "the token survives a 503");
		check(DiscordSignIn.accounts().size() == 2, "and so do the names already fetched");

		System.out.println("-- the names are checked on the way in");
		freshHome("names");
		site.status = 200;
		// A safe name is [a-z0-9_] and at most 12. Nothing else may reach the login screen's
		// username field, whatever a server or a hand-edited file says.
		site.names = "\"lord_fooby\",\"../../etc/passwd\",\"waytoolongaccountname\",\"Corey Shorter\",\"bob\"";
		DiscordSignIn.paste(TOKEN);
		List<String> safe = DiscordSignIn.accounts();
		check(safe.size() == 2 && safe.get(0).equals("lord_fooby") && safe.get(1).equals("bob"),
			"only safe names are kept: " + safe);

		System.out.println("-- a mangled file");
		freshHome("mangled");
		OutputStream out = new FileOutputStream(launcherFile());
		out.write("discord.token=nonsense\ndiscord.accounts=,,,\n\u0000\u0000".getBytes(StandardCharsets.UTF_8));
		out.close();
		DiscordSignIn.reload();
		check(!DiscordSignIn.signedIn(), "a token of the wrong shape is no sign-in");
		check(DiscordSignIn.accounts().isEmpty(), "and no names");

		System.out.println("-- signing out");
		freshHome("signout");
		site.names = "\"lord_fooby\"";
		DiscordSignIn.paste(TOKEN);
		int before = site.asked;
		DiscordSignIn.signOut();
		check(!DiscordSignIn.signedIn(), "signed out here");
		check(!launcherFile().isFile(), "and the file is gone");
		check(site.asked > before && site.lastMethod.equals("POST"), "the server was told, with a POST");

		site.close();
		System.out.println(fails == 0 ? "all ok" : fails + " FAILED");
		System.exit(fails == 0 ? 0 : 1);
	}
}
