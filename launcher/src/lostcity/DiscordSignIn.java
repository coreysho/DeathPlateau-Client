package lostcity;

import java.awt.Desktop;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sign in with Discord, so a player's characters follow them to a computer that has never seen
 * them.
 *
 * WHAT THIS GETS YOU, AND ONLY THIS. The server says which character NAMES are linked to your
 * Discord account; the login screen offers them as cards that fill the name in. You still type the
 * password the first time on each machine, and Remember me works exactly as it did. Saved logins
 * are kept per-machine on purpose - the client's LoginProfiles ties them to this computer's account
 * so a copied file is useless - and nothing here changes that.
 *
 * WHAT IT DELIBERATELY IS NOT. It does not hand over anything that can log in. That version was
 * considered and refused: it would make one Discord account a master key to every character linked
 * to it, and a throwaway Discord account costs one email. So the worst a stolen launcher code can
 * do is list names that are on the hiscores anyway.
 *
 * THE SECRET IS NOT IN HERE, AND CANNOT BE. Anybody can read this jar, so the OAuth code exchange
 * happens on the website - the launcher only ever receives a token of ours at the end of it. That
 * is the native-app pattern (RFC 8252) and the reason the flow goes out through a browser at all.
 *
 * GETTING THE ANSWER BACK INTO A DESKTOP PROGRAM is the one genuinely awkward part, and there are
 * two ways because the first one fails on other people's computers:
 *
 *   * THE LOOPBACK. This binds a socket on 127.0.0.1, tells the website which port, and the
 *     callback redirects the browser to it. Never off this machine.
 *
 *   * THE CODE ON SCREEN. A firewall, an antivirus or a locked-down work machine can stop a desktop
 *     program listening at all. When the bind fails this asks for the same page WITHOUT a port, the
 *     website prints the code instead, and the player pastes it in. Nothing about the first path can
 *     detect that case from the server's side, which is why the decision is made here.
 *
 * THE TOKEN IS KEPT IN PLAIN TEXT, unlike a saved login, and that is a decision rather than an
 * oversight. profiles.properties obfuscates what it holds because a leaked saved login is worth an
 * account until it is cancelled; this file holds a token worth a list of character names. Copying
 * the client's cipher into this source tree - which cannot see jagex2.client, so it would be a
 * second copy to keep in step - to hide names that are printed on the hiscores would be ceremony.
 * The file says so in its own header, where somebody opening it will read it.
 *
 * NOTHING HERE MAY THROW at the launcher. Every path returns a Result with something to say to the
 * player: a sign-in that fails has to leave a launcher that still starts the game.
 */
final class DiscordSignIn {

    /**
     * The website. Overridable for development (-Dlostcity.site=http://localhost:3000), and
     * duplicated here rather than read from the client's classes for the same reason the dev relay
     * host is: the release workflow compiles launcher/src on its own, with none of the client on
     * the classpath.
     */
    private static final String SITE = Launcher.setting("lostcity.site", "DP_SITE", "https://deathplateau.com");

    /** ~/.deathplateau/launcher.properties, beside the client's own files. */
    private static final String FILE_NAME = "launcher.properties";

    private static final String KEY_TOKEN = "discord.token";
    private static final String KEY_ACCOUNTS = "discord.accounts";
    private static final String KEY_CHECKED = "discord.checked";

    /** The server mints 20 characters of this alphabet - LoginToken's shape, checked on both sides. */
    private static final int TOKEN_LEN = 20;
    private static final String TOKEN_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789";

    /**
     * How long to hold the loopback socket open.
     *
     * Five minutes, because the browser may ask them to log in to Discord first, and a timeout that
     * expires while somebody is typing a Discord password is a sign-in that fails for no reason.
     * Cancel is the way out before then, not the clock.
     */
    private static final int WAIT_SECONDS = 300;

    /** A safe name, which is all the server ever sends: the only shape allowed onto the login screen. */
    private static final Pattern SAFE_NAME = Pattern.compile("[a-z0-9_]{1,12}");

    private static final Pattern ACCOUNTS = Pattern.compile("\"accounts\"\\s*:\\s*\\[([^\\]]*)\\]");
    private static final Pattern QUOTED = Pattern.compile("\"([^\"]*)\"");

    private DiscordSignIn() {
    }

    // ------------------------------------------------------------------ what is signed in

    private static boolean loaded;
    private static String token = "";
    private static final List<String> accounts = new ArrayList<String>();

    private static File file() {
        // Read at call time, never cached, so a test pointing user.home at a scratch directory is
        // not writing into a developer's real ~/.deathplateau. LoginProfiles does the same.
        return new File(new File(System.getProperty("user.home"), ".deathplateau"), FILE_NAME);
    }

    static synchronized void reload() {
        loaded = false;
        token = "";
        accounts.clear();
    }

    private static synchronized void load() {
        loaded = true;
        InputStream in = null;
        try {
            File f = file();
            if (!f.isFile()) {
                return;
            }
            in = new FileInputStream(f);
            Properties p = new Properties();
            p.load(in);
            String stored = p.getProperty(KEY_TOKEN, "").trim();
            token = looksLikeToken(stored) ? stored : "";
            for (String name : p.getProperty(KEY_ACCOUNTS, "").split(",")) {
                String safe = name.trim().toLowerCase();
                // Checked on the way in, not just on the way out: this file is a text file a player
                // can edit, and what it says ends up drawn on the login screen.
                if (SAFE_NAME.matcher(safe).matches() && !accounts.contains(safe)) {
                    accounts.add(safe);
                }
            }
        } catch (Exception broken) {
            // A hand-mangled or half-written file is "not signed in", which is the launcher
            // everybody had before this existed.
            token = "";
            accounts.clear();
        } finally {
            close(in);
        }
    }

    static synchronized boolean signedIn() {
        if (!loaded) {
            load();
        }
        return token.length() > 0;
    }

    static synchronized String token() {
        if (!loaded) {
            load();
        }
        return token;
    }

    /** The character names last fetched, oldest link first - the order the server sends. */
    static synchronized List<String> accounts() {
        if (!loaded) {
            load();
        }
        return new ArrayList<String>(accounts);
    }

    static boolean looksLikeToken(String value) {
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

    private static synchronized void save(String newToken, List<String> newAccounts) {
        token = newToken == null ? "" : newToken;
        accounts.clear();
        if (newAccounts != null) {
            accounts.addAll(newAccounts);
        }
        loaded = true;

        OutputStream out = null;
        try {
            File f = file();
            File dir = f.getParentFile();
            if (!dir.isDirectory() && !dir.mkdirs()) {
                return;
            }
            if (token.length() == 0) {
                // Signed out: the file goes rather than being left holding an empty key, so that
                // "no file" and "no sign-in" are the same state and there is only one to read.
                f.delete();
                return;
            }
            Properties p = new Properties();
            p.setProperty(KEY_TOKEN, token);
            p.setProperty(KEY_ACCOUNTS, join(accounts));
            p.setProperty(KEY_CHECKED, String.valueOf(System.currentTimeMillis()));
            if (!f.exists()) {
                f.createNewFile();
            }
            // Narrowed before anything is written. These fail quietly on filesystems without the
            // bits, which is nothing we can act on from here.
            f.setReadable(false, false);
            f.setWritable(false, false);
            f.setReadable(true, true);
            f.setWritable(true, true);
            out = new FileOutputStream(f);
            p.store(out, "Death Plateau launcher. " + KEY_TOKEN + " is a Discord sign-in for THIS LAUNCHER:"
                + " it lets the launcher list the characters linked to your Discord account so the login screen can"
                + " offer them. It is NOT a password and cannot log in to any of them - you still type your password"
                + " the first time on each computer. Delete this file to sign out; /unlink in our Discord cancels it"
                + " everywhere.");
        } catch (Exception ignored) {
            // The sign-in still works for this run; it is only not remembered for the next one.
        } finally {
            close(out);
        }
    }

    private static String join(List<String> parts) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                text.append(',');
            }
            text.append(parts.get(i));
        }
        return text.toString();
    }

    // ------------------------------------------------------------------ the answer to a sign-in

    static final class Result {

        /** True when this launcher is signed in once this call returns. */
        final boolean ok;

        /**
         * True when the loopback could not be used, so the website is showing a code and the
         * launcher must offer somewhere to paste it. Not a failure.
         */
        final boolean paste;

        /** What to tell the player. Never null, never a stack trace. */
        final String message;

        /** The address the browser was sent to, so the launcher can offer it if the browser did not open. */
        final String url;

        Result(boolean ok, boolean paste, String message, String url) {
            this.ok = ok;
            this.paste = paste;
            this.message = message;
            this.url = url;
        }
    }

    private static ServerSocket listening;
    /**
     * Whether Cancel is why the wait ended.
     *
     * Cancel closes the socket, which ends the accept exactly as a timeout does - so without this
     * the launcher told somebody who had just pressed Cancel that "the sign-in did not finish. Try
     * again", which reads as a failure they should do something about rather than as the thing they
     * asked for.
     */
    private static boolean cancelled;

    /**
     * How the url gets in front of the player. The real one is their browser; the headless test
     * passes one that plays the browser's part, which is the only way to exercise the loopback
     * hand-over without a desktop - and the loopback is the piece of this that has never run
     * anywhere before.
     */
    interface Opener {

        boolean open(String url);
    }

    /**
     * Takes the player through the sign-in and blocks until it is finished, cancelled or has timed
     * out. Call it off the Swing thread - it waits minutes.
     */
    static Result signIn() {
        return signIn(new Opener() {

            public boolean open(String url) {
                return browse(url);
            }
        });
    }

    static Result signIn(Opener opener) {
        String nonce = nonce();
        ServerSocket server = listen();
        int port = server == null ? 0 : server.getLocalPort();

        String url = SITE + "/launcher/login?launcher=" + encode(nonce) + (port > 0 ? "&port=" + port : "");
        Launcher.log("discord: sign-in starting" + (port > 0 ? " on 127.0.0.1:" + port : " with no loopback (paste)"));

        // BEFORE THE BROWSER OPENS, not after the wait starts. The window puts its Cancel up the
        // moment it is clicked, and opening a browser can take a second or two on a cold machine -
        // so a Cancel in that window used to find no socket to close, leave this thread waiting out
        // its five minutes, and then overwrite whatever the player had done since with the result
        // of a sign-in they had given up on.
        synchronized (DiscordSignIn.class) {
            listening = server;
            cancelled = false;
        }

        boolean opened = opener.open(url);
        if (!opened) {
            Launcher.log("discord: no browser could be opened");
        }

        if (server == null) {
            // NO SOCKET, so nothing is coming back by itself. The website is printing the code.
            // (Nothing to clear: with no socket there was nothing to register above.)
            return new Result(false, true, opened
                ? "Your browser is finishing the sign-in. Copy the code it shows and paste it here."
                : "Open this address in your browser, then paste the code it shows here.", url);
        }

        try {
            String[] answer = await(server, nonce);
            if (answer == null) {
                boolean stopped;
                synchronized (DiscordSignIn.class) {
                    stopped = cancelled;
                }
                return new Result(false, false, stopped
                    ? "Sign-in cancelled. Nothing has changed."
                    : opened ? "The sign-in did not finish. Try again, or use a code instead."
                    : "No browser opened. Try again, or use a code instead.", url);
            }
            if (answer[0] == null) {
                return new Result(false, false, describe(answer[1]), url);
            }
            return finish(answer[0]);
        } finally {
            synchronized (DiscordSignIn.class) {
                listening = null;
            }
            try {
                server.close();
            } catch (IOException ignored) {
            }
        }
    }

    /** Stops a sign-in that is waiting, from the Swing thread: closing the socket ends the accept. */
    static void cancel() {
        ServerSocket server;
        synchronized (DiscordSignIn.class) {
            server = listening;
            cancelled = true;
        }
        if (server != null) {
            try {
                server.close();
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * The pasted code. Verified against the server before it is kept, so a mistyped one says so
     * here rather than silently offering no characters.
     */
    static Result paste(String typed) {
        String code = typed == null ? "" : typed.trim().toLowerCase().replaceAll("[^a-z0-9]", "");
        if (!looksLikeToken(code)) {
            return new Result(false, true, "That does not look like a code. It is " + TOKEN_LEN + " letters and numbers.", null);
        }
        return finish(code);
    }

    /** Fetches the character list for a token and keeps it if the server recognises it. */
    private static Result finish(String code) {
        List<String> names = new ArrayList<String>();
        int status = fetchAccounts(code, names);
        if (status == 200) {
            save(code, names);
            Launcher.log("discord: signed in, " + names.size() + " character(s) linked");
            // NOTHING TO SAY WHEN IT WORKED AND THERE ARE CHARACTERS. The block's own headline
            // changes from "Sign in with Discord" to "Discord: 3 characters linked" and a Sign out
            // appears under it, which is the confirmation; a line underneath repeating the count
            // was two sentences saying one thing, and it looked like it.
            return new Result(true, false, names.isEmpty()
                ? "No characters are linked to your Discord yet - type ::discord in game to link one."
                : "", null);
        }
        if (status == 401) {
            return new Result(false, false, "That code is not valid any more. Start the sign-in again.", null);
        }
        // 503, or no answer at all. NOT treated as signed out - see the note on fetchAccounts.
        return new Result(false, false, "The server could not be reached. Nothing is lost; try again shortly.", null);
    }

    /**
     * Re-reads the character list for the token already kept, which is what a launcher start does.
     *
     * 401 IS THE ONLY THING THAT FORGETS THE TOKEN. A server that cannot answer - it is restarting,
     * the box is down, the player is offline - must leave the sign-in alone: dropping it on a
     * timeout would send somebody through a Discord sign-in because their wifi blinked. The names
     * already on disk are what the client gets in the meantime, and the password is still typed, so
     * a stale list costs nothing.
     */
    static boolean refresh() {
        String code = token();
        if (code.length() == 0) {
            return false;
        }
        List<String> names = new ArrayList<String>();
        int status = fetchAccounts(code, names);
        if (status == 200) {
            save(code, names);
            Launcher.log("discord: " + names.size() + " character(s) linked");
            return true;
        }
        if (status == 401) {
            Launcher.log("discord: the sign-in has expired or was cancelled");
            save("", null);
            return false;
        }
        Launcher.log("discord: could not check the character list (" + status + "); keeping what we have");
        return true;
    }

    /** Signing out here and on the server, so the sign-in cannot be used from this computer again. */
    static void signOut() {
        String code = token();
        save("", null);
        if (code.length() == 0) {
            return;
        }
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(SITE + "/launcher/accounts").openConnection();
            c.setRequestMethod("POST");
            c.setRequestProperty("Authorization", "Bearer " + code);
            c.setRequestProperty("User-Agent", "DeathPlateau-Launcher");
            c.setFixedLengthStreamingMode(0);
            c.setDoOutput(true);
            c.setConnectTimeout(10_000);
            c.setReadTimeout(15_000);
            Launcher.log("discord: signed out (" + c.getResponseCode() + ")");
            c.disconnect();
        } catch (Exception offline) {
            // THE LOCAL SIGN-OUT ALREADY HAPPENED, above, and that is the one the player asked for.
            // A server that could not be told leaves a row that expires on its own in thirty days,
            // and /unlink in Discord cancels it now.
            Launcher.log("discord: signed out here, but the server could not be told: " + offline.getMessage());
        }
    }

    /**
     * GET /launcher/accounts with the token in a header, never in the url.
     *
     * Returns the HTTP status, or 0 for no answer at all, and fills `into` on 200. The three
     * answers mean different things and the caller must keep them apart: 200 is the list, 401 is
     * "this sign-in is gone", anything else is "cannot tell right now".
     */
    private static int fetchAccounts(String code, List<String> into) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(SITE + "/launcher/accounts").openConnection();
            c.setRequestProperty("Authorization", "Bearer " + code);
            c.setRequestProperty("User-Agent", "DeathPlateau-Launcher");
            c.setRequestProperty("Accept", "application/json");
            c.setConnectTimeout(10_000);
            c.setReadTimeout(20_000);
            int status = c.getResponseCode();
            if (status != 200) {
                return status;
            }
            String body = read(c.getInputStream());
            Matcher list = ACCOUNTS.matcher(body);
            if (list.find()) {
                Matcher each = QUOTED.matcher(list.group(1));
                while (each.find()) {
                    String name = each.group(1).trim().toLowerCase();
                    if (SAFE_NAME.matcher(name).matches() && !into.contains(name)) {
                        into.add(name);
                    }
                }
            }
            return 200;
        } catch (Exception offline) {
            Launcher.log("discord: " + offline.getMessage());
            return 0;
        } finally {
            if (c != null) {
                c.disconnect();
            }
        }
    }

    // ------------------------------------------------------------------ the loopback

    /** A socket on the loopback only, on whatever port the system gives us, or null if it cannot. */
    private static ServerSocket listen() {
        try {
            // Port 0 is "any free one": a fixed port would collide with whatever else is on the
            // machine and cannot be registered with Discord anyway, which is why the website takes
            // the port as a parameter and redirects to it.
            return new ServerSocket(0, 2, InetAddress.getByName("127.0.0.1"));
        } catch (Exception blocked) {
            Launcher.log("discord: could not listen on the loopback (" + blocked.getMessage() + ") - using a code instead");
            return null;
        }
    }

    /**
     * Waits for the browser to arrive with the sign-in.
     *
     * Returns {token, null}, or {null, reason} when the website sent a reason, or null when nothing
     * came - a timeout, or Cancel closing the socket under us.
     *
     * THE NONCE IS CHECKED, not just the shape of what arrives. This is a socket on the player's own
     * machine and anything running there could knock on it; without the check, a page they happened
     * to have open could hand this launcher somebody else's sign-in.
     */
    private static String[] await(ServerSocket server, String nonce) {
        long deadline = System.currentTimeMillis() + WAIT_SECONDS * 1000L;
        String[] answer = null;
        try {
            while (System.currentTimeMillis() < deadline) {
                int left = (int) Math.max(1000L, deadline - System.currentTimeMillis());
                server.setSoTimeout(answer == null ? left : 2000);
                Socket socket;
                try {
                    socket = server.accept();
                } catch (IOException timeout) {
                    // Timed out, or Cancel closed the socket. Either way there is no more to wait
                    // for - except that once the answer is in, a couple of seconds of this is how
                    // the browser gets the page saying it worked.
                    return answer;
                }
                try {
                    String target = request(socket);
                    if (answer == null) {
                        String[] got = read(target, nonce);
                        if (got != null) {
                            answer = got;
                            // Straight back out to a url with nothing in it, so the token is not
                            // left sitting in the address bar for whoever walks past.
                            redirect(socket, "http://127.0.0.1:" + server.getLocalPort() + "/done");
                            continue;
                        }
                    }
                    done(socket, answer);
                } finally {
                    try {
                        socket.close();
                    } catch (IOException ignored) {
                    }
                }
            }
        } catch (Exception broken) {
            Launcher.log("discord: the loopback failed (" + broken.getMessage() + ")");
        }
        return answer;
    }

    /** The request target of one HTTP request, or "" - the first line is all that is wanted. */
    private static String request(Socket socket) throws IOException {
        socket.setSoTimeout(10_000);
        InputStream in = socket.getInputStream();
        StringBuilder line = new StringBuilder();
        int c;
        // The first line only, and capped: a request line from a browser is short, and anything
        // sending megabytes at this is not a browser.
        while ((c = in.read()) != -1 && c != '\n' && line.length() < 4096) {
            if (c != '\r') {
                line.append((char) c);
            }
        }
        String[] parts = line.toString().split(" ");
        return parts.length >= 2 ? parts[1] : "";
    }

    /** {token, null} or {null, reason} if this request is the sign-in we asked for. */
    private static String[] read(String target, String nonce) {
        int at = target.indexOf('?');
        if (at < 0) {
            return null;
        }
        String state = null, got = null, error = null;
        for (String pair : target.substring(at + 1).split("&")) {
            int eq = pair.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String key = pair.substring(0, eq);
            String value = decode(pair.substring(eq + 1));
            if (key.equals("state")) {
                state = value;
            } else if (key.equals("token")) {
                got = value;
            } else if (key.equals("error")) {
                error = value;
            }
        }
        if (state == null || !state.equals(nonce)) {
            // Not ours. Something else on this machine, or a stale tab from an older attempt.
            Launcher.log("discord: ignored a loopback request that did not carry our own nonce");
            return null;
        }
        if (error != null) {
            return new String[] { null, error };
        }
        return looksLikeToken(got) ? new String[] { got, null } : new String[] { null, "shape" };
    }

    /** What the website's reasons mean, in words for somebody looking at a launcher. */
    private static String describe(String error) {
        if ("cancelled".equals(error)) {
            return "Sign-in cancelled. Nothing has changed.";
        }
        if ("state".equals(error)) {
            return "That sign-in did not come from this launcher. Try it again.";
        }
        if ("unavailable".equals(error)) {
            return "The server could not record the sign-in. Nothing you did; try again shortly.";
        }
        if ("discord".equals(error)) {
            return "Discord did not answer. Try again in a minute.";
        }
        return "The sign-in did not finish. Try again.";
    }

    private static void redirect(Socket socket, String to) throws IOException {
        byte[] head = ("HTTP/1.1 303 See Other\r\nLocation: " + to + "\r\nContent-Length: 0\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        socket.getOutputStream().write(head);
        socket.getOutputStream().flush();
    }

    /** The page the browser is left on. Plain, self-contained, and says to go back to the launcher. */
    private static void done(Socket socket, String[] answer) throws IOException {
        boolean ok = answer != null && answer[0] != null;
        String body = "<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">"
            + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
            + "<title>Death Plateau</title><style>:root{color-scheme:dark}"
            + "body{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;"
            + "background:#140e0d;color:#d8cbc8;font:16px/1.6 system-ui,-apple-system,\"Segoe UI\",Roboto,sans-serif}"
            + "main{max-width:28rem;padding:2.5rem 1.5rem}h1{margin:0 0 1rem;font-size:1.3rem;color:#fff}"
            + "p{margin:0;color:#a99a97}</style></head><body><main><h1>"
            + (ok ? "Signed in with Discord" : "Nothing happened")
            + "</h1><p>"
            + (ok ? "You can close this tab. Your characters are on the launcher now." : "This page is the launcher's. You can close it.")
            + "</p></main></body></html>";
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        OutputStream out = socket.getOutputStream();
        out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: " + bytes.length
            + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(bytes);
        out.flush();
    }

    // ------------------------------------------------------------------ odds and ends

    private static String nonce() {
        byte[] bytes = new byte[12];
        new SecureRandom().nextBytes(bytes);
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            // Hex, because the website narrows this to [A-Za-z0-9_-] and hex is already inside that.
            text.append(Integer.toHexString((bytes[i] & 0xF0) >> 4)).append(Integer.toHexString(bytes[i] & 0x0F));
        }
        return text.toString();
    }

    /**
     * Opens the player's browser, by whatever means this desktop has.
     *
     * Desktop.browse is the supported way and is missing or broken on plenty of Linux desktops, so
     * the platform's own opener is tried after it. False means the launcher has to show the address
     * and let them open it themselves, which is why signIn() carries the url back out.
     */
    private static boolean browse(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
                return true;
            }
        } catch (Throwable unsupported) {
            Launcher.log("discord: Desktop.browse failed (" + unsupported.getMessage() + ")");
        }
        String os = System.getProperty("os.name", "").toLowerCase();
        String[] cmd = os.contains("win") ? new String[] { "rundll32", "url.dll,FileProtocolHandler", url }
            : os.contains("mac") ? new String[] { "open", url }
            : new String[] { "xdg-open", url };
        try {
            new ProcessBuilder(cmd).start();
            return true;
        } catch (Exception none) {
            return false;
        }
    }

    private static String read(InputStream in) throws IOException {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
                if (out.size() > 1 << 20) {
                    break; // a list of names is bytes; anything this large is not one
                }
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            close(in);
        }
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (Exception never) {
            return value;
        }
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (Exception broken) {
            return "";
        }
    }

    private static void close(InputStream in) {
        try {
            if (in != null) {
                in.close();
            }
        } catch (Exception ignored) {
        }
    }

    private static void close(OutputStream out) {
        try {
            if (out != null) {
                out.close();
            }
        } catch (Exception ignored) {
        }
    }
}
