package lostcity;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;

// THE DEATH PLATEAU LAUNCHER. Players run this instead of the client. Every start it asks GitHub for
// the latest client release (.github/workflows/release.yml publishes one on every push), downloads
// it if it is newer than the one it has, and runs it. So a client change reaches everybody the next
// time they start the game, and nobody is sent a jar again.
//
// Everything lives in ~/.deathplateau: client.jar, and client.version holding the release tag it
// came from. (It was ~/.lostcity until the server was named; see migrate.) If GitHub cannot be reached the launcher runs whatever client it already has, and only a
// first start with no client at all is an error.
//
//   java -jar Death-Plateau-Launcher.jar           update if needed, then play
//   java -jar Death-Plateau-Launcher.jar --dev     ...on the dev world, without asking
//   java -jar Death-Plateau-Launcher.jar --check   update if needed, then exit (for testing)
//
// Any -Dlostcity.* property given to the launcher (lostcity.host, .port, .webhost, .webport) is passed on to
// the client. Java 8, no dependencies: the one thing a player needs is the Java they already have.
//
// WHICH WORLD. The window waits to be told: Play starts World 1, and nothing starts by itself. It used
// to count down from three seconds and then start World 1 unasked, which was reasonable beside two small
// buttons and is not beside a button this size - a window whose one control presses itself while you are
// still reading it. The ways to skip the window entirely are unchanged and are below. The
// dev world (World 2, node 11) is a second server in the same container, for staff only - the server
// turns everybody else away with "This world is full". It has its own game and web ports, found by
// devEndpoint(): the server's own ports plus one when lostcity.host points straight at it (the LAN, or
// Tailscale), else the dev world's own playit.gg tunnels below; lostcity.dev.host / .port / .webhost /
// .webport override any of it. --dev, --world=1, or -Dlostcity.world=dev|1 choose without the buttons.
public final class Launcher {
    private static final String REPO = "coreysho/DeathPlateau-Client";
    private static final String ASSET = "client.jar";
    private static final String API = "https://api.github.com/repos/" + REPO + "/releases/latest";

    // The dev world's playit.gg tunnels - one to the dev game port (43595 on the server) and one to its
    // web port (8889), made like World 1's (Client.SERVER_HOST / WEB_HOST). With lostcity.host given (the
    // LAN or Tailscale) the server's own ports are used instead, and lostcity.dev.* overrides either.
    private static final String DEV_TUNNEL_HOST = "carolyn-sternness.tun.ply.gg";
    private static final int DEV_TUNNEL_PORT = 55662;
    private static final String DEV_TUNNEL_WEBHOST = "carolyn-adapt.tun.ply.gg";
    private static final int DEV_TUNNEL_WEBPORT = 55673;

    // the 377 client's own node ids: World 1 is 10, the dev world 11 ("World 2" in the friends list)
    private static final int DEV_NODE_ID = 11;
    private static final String WORLD_1 = "1", DEV = "dev";

    // SELF-UPDATE. The launcher is compiled into client.jar as well as into its own jar (build.gradle),
    // so the newest launcher is already on the player's disk the moment the client updates - it is just
    // not the copy they double-click. So once client.jar is current, a launcher that differs from the
    // one inside it hands over: it starts THAT launcher and steps aside. Players keep the jar they have
    // for good, and a launcher change reaches everybody the way a client change does.
    // The handed-over launcher carries this property, so it never hands over again.
    private static final String HANDOFF = "lostcity.launcherhandoff";
    /**
     * Bump this whenever the launcher changes in a way players should get. It is the ONLY thing that
     * moves them onto a new launcher, so a change left unbumped simply never reaches them (which is
     * where they stood before any of this - nothing breaks).
     *
     * The number is read back OUT OF THE CLASS FILE inside client.jar, by finding STAMP's text in it:
     * javac folds the two constants below into that one string literal, and a literal sits in the
     * constant pool verbatim whichever compiler built the jar (gradle's and the release workflow's
     * javac disagree on everything else in there, so comparing the class bytes would hand over on
     * every single start). Nothing is loaded out of that jar to read it.
     *
     *   1  the launcher as first released
     *   2  hands over to the launcher inside client.jar; World 1 / Dev world buttons; its window closes
     *      as the game opens, and cannot be resized
     *   3  the dev world gets its own client cache (storeid 33), so the two worlds stop overwriting
     *      each other's config - see the note where the storeid is passed
     *   4  keeps a log in ~/.deathplateau/launcher.log, because a double-clicked launcher has no
     *      console and its output was gone exactly when it was wanted
     *   5  the game's own stdout and stderr go to ~/.deathplateau/client.log, so an exception that
     *      ends GameShell's loop can be read afterwards instead of just freezing the window
     *   6  the Death Plateau badge on the launcher's own window, instead of the default Java cup.
     *      The bump is what carries it: a player who downloaded their launcher months ago runs
     *      that jar, and only a higher version in client.jar makes it step aside for one that has
     *      the icons - which the client jar has, because launcher/src and src/main/resources are
     *      both compiled into it
     */
    /**
     * BUMP THIS WHENEVER THE LAUNCHER CHANGES, and never reset it.
     *
     * It is not a product version, it is the hand-over's only input: handOver() reads the stamp
     * out of the launcher inside client.jar and compares it with this number, so the number has
     * to identify the code. Two different launchers both calling themselves 7 would mean a jar on
     * disk declining to hand over to a newer one that happens to share its number, which is how a
     * player gets stuck on an old launcher with no way to be moved off it.
     *
     * It also must only ever go up. handOver refuses to hand over to anything lower precisely so
     * that two launchers cannot pass a player back and forth for ever, which means a reset to 1
     * would leave every launcher already out there - all of them 6 and 7 - permanently convinced
     * that it is the newer one.
     */
    private static final int VERSION = 10;

    /**
     * The first launcher version whose client writes the window marker (see awaitClientWindow).
     *
     * launcher/src is compiled into client.jar as well, so the stamp inside the client says which
     * launcher it was built beside - which is also whether its ViewBox knows to touch the file.
     * Asking it is better than waiting out a timeout on every old client: the two are released
     * together, so the only way to be running an older one is to have been offline when the
     * update check ran.
     */
    private static final int READY_SINCE = 7;
    private static final String STAMP_PREFIX = "DP-LAUNCHER-VERSION:";
    @SuppressWarnings("unused") // read out of the compiled class, not called
    private static final String STAMP = STAMP_PREFIX + VERSION;

    private static final Pattern TAG = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern URL_ = Pattern.compile("\"browser_download_url\"\\s*:\\s*\"([^\"]*/" + Pattern.quote(ASSET) + ")\"");

    private final File dir = new File(System.getProperty("user.home"), ".deathplateau");
    // where launchers before the rename kept the client - moved out of by migrate()
    private final File oldDir = new File(System.getProperty("user.home"), ".lostcity");
    private final File jar = new File(dir, ASSET);
    /**
     * Touched by the client the moment its window is on screen - see ViewBox - so this launcher
     * knows when to get out of the way. Named here, passed as -Dlostcity.ready.
     */
    private final File ready = new File(dir, "client.ready");
    private final File version = new File(dir, "client.version");

    private LauncherWindow ui;
    /** Up from the first instruction of main() until the real window has something to show. */
    private Splash splash;

    // WORLD_1, DEV, or null while nobody has chosen - guarded by choiceLock
    private final Object choiceLock = new Object();
    private String choice;

    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        boolean check = false;
        launcher.choice = world(System.getProperty("lostcity.world"));
        for (String arg : args) {
            if (arg.equals("--check")) {
                check = true;
            } else if (arg.equals("--dev")) {
                launcher.choice = DEV;
            } else if (arg.startsWith("--world=")) {
                launcher.choice = world(arg.substring("--world=".length()));
            }
        }
        // BEFORE run(), whose first act is a call to GitHub: until this existed the desktop stayed
        // empty for however long that took, which looks like a program that did not start - and
        // the answer to that is to double-click it again, which is a second launcher. Not for
        // --check, which is a test switch and has no window of its own to precede.
        if (!check) {
            launcher.splash = Splash.show();
        }
        launcher.run(check);
    }

    private static String world(String name) {
        if (name == null) {
            return null;
        }
        name = name.trim().toLowerCase();
        return name.equals(DEV) || name.equals("2") ? DEV : name.equals(WORLD_1) ? WORLD_1 : null;
    }

    private void run(boolean check) {
        if (!dir.isDirectory() && !dir.mkdirs()) {
            fail("Could not create " + dir);
            return;
        }
        migrate();

        // HAND OVER BEFORE PUTTING ANYTHING ON SCREEN, if the client we already have carries a
        // newer launcher than this one.
        //
        // The jar a player double-clicks is whatever they downloaded once, and it is never
        // replaced - the newer launcher travels inside client.jar and is handed to at startup. So
        // a player on an old jar saw that old launcher's window first and the real one a second
        // later, every single run, which looks like the thing started twice. (Observed here on
        // 2026-10-31: launcher 6's window, then launcher 7's.)
        //
        // The check costs a zip entry read and needs no network, because the stamp is in the
        // client.jar already on disk. Only when there is no newer launcher there does this one go
        // on to show a window and check for updates. The second attempt further down stays: a
        // download that has just arrived may itself carry a newer launcher, and that case cannot
        // be known before the download.
        if (!check && handOver()) {
            System.exit(0);
        }

        if (!check) {
            window();
        }

        if (System.getProperty(HANDOFF) != null) {
            // the launcher that handed over checked and downloaded a moment ago
            log("handed over to: skipping the update check");
        } else try {
            status("Checking for updates...");
            String json = fetch(API);
            Matcher tag = TAG.matcher(json);
            Matcher url = URL_.matcher(json);
            if (!tag.find() || !url.find()) {
                throw new IOException("the latest release has no " + ASSET);
            }
            String have = version.isFile() ? new String(Files.readAllBytes(version.toPath()), StandardCharsets.UTF_8).trim() : "";
            if (!jar.isFile() || !have.equals(tag.group(1))) {
                download(url.group(1), tag.group(1));
            } else {
                log("up to date: " + have);
            }
        } catch (IOException e) {
            log("update check failed: " + e.getMessage());
            if (!jar.isFile()) {
                fail("Couldn't download the game.\n\n" + e.getMessage() + "\n\nCheck your internet connection and try again.");
                return;
            }
            // offline, or GitHub is down: play what we have
        }

        if (check) {
            System.exit(0);
        }

        if (handOver()) {
            System.exit(0);
        }

        // BACK TO THE BUTTON. The checking is done and the window is about to wait for a click, so
        // something has to put it back from the progress bar - and nothing else does. The countdown
        // used to, incidentally: it called choosing() every second, and clearing the busy state was
        // a side effect of drawing "Starting World 1 in 2...". Taking the countdown out took that
        // with it and left the window sitting on "Checking for updates..." for ever, with no Play
        // button to press. Shipped in client-146; found by running it.
        //
        // Not when a world is already chosen (--dev, --world=, lostcity.world): awaitChoice returns
        // at once there and the button would show for a single frame on its way past.
        boolean waiting;
        synchronized (choiceLock) {
            waiting = choice == null;
        }
        if (waiting) {
            choosing(null);
        }

        for (;;) {
            String world = awaitChoice();
            String[] dev = null;
            if (world.equals(DEV)) {
                dev = devEndpoint();
                if (dev == null) {
                    // no way to the dev world from here - say so, and let them choose again
                    message("The dev world has no public address yet.\n\n"
                        + "Start the launcher with -Dlostcity.host=<the server's address> (the LAN or Tailscale),\n"
                        + "or give -Dlostcity.dev.host, .port, .webhost and .webport.");
                    choose(null);
                    // Back to the button, and say so: the dialog has just been dismissed and
                    // without a word here the window looks like it did nothing.
                    choosing("The dev world is out of reach - Play starts World 1.");
                    continue;
                }
            }
            status(dev == null ? "Starting World 1..." : "Starting the dev world...");
            try {
                launch(dev);
            } catch (IOException e) {
                fail("Couldn't start the game: " + e.getMessage());
                return;
            }
            System.exit(0);
        }
    }

    /**
     * The world to start: one chosen already (--dev, --world=, lostcity.world), else whichever the
     * player clicks. It waits for as long as that takes.
     *
     * NOTHING STARTS BY ITSELF ANY MORE. This used to give the window three seconds and then take
     * World 1 if nobody had said otherwise, so that a player who ignored the two buttons still got
     * into the game. With one large Play button that reads as the window pressing its own control
     * while you are still looking at it, and it made the dev world a race rather than a choice.
     *
     * Nothing here touches the window, deliberately: status() hands work to the Swing thread, and
     * the Swing thread takes choiceLock in choose(), so saying anything from inside this lock is a
     * deadlock. There is nothing to say in any case - the caption already explains what Play does.
     */
    private String awaitChoice() {
        synchronized (choiceLock) {
            while (choice == null) {
                try {
                    choiceLock.wait();
                } catch (InterruptedException ignored) {
                    // round again. An interrupt is not somebody choosing a world.
                }
            }
            return choice;
        }
    }

    // a button (on the Swing thread), or null to take the choice back after a dev world that failed
    private void choose(String world) {
        synchronized (choiceLock) {
            choice = world;
            choiceLock.notifyAll();
        }
        run(() -> {
            if (ui != null) {
                ui.setChoosable(world == null);
            }
        });
    }

    // The dev world's {game host, game port, web host, web port}, or null if there is no way there. Each
    // lostcity.dev.* given wins. Else, with the server's own address given (lostcity.host - the LAN or
    // Tailscale), its game and web ports plus one, the way 377 numbered its worlds (portOffset). Else the
    // dev world's tunnels (DEV_TUNNEL_*), when there are some.
    static String[] devEndpoint() {
        String host = setting("lostcity.host", "LOSTCITY_HOST");
        String gameHost, webHost;
        int gamePort, webPort;
        if (host != null) {
            gameHost = host;
            webHost = setting("lostcity.webhost", "LOSTCITY_WEBHOST") != null ? setting("lostcity.webhost", "LOSTCITY_WEBHOST") : host;
            gamePort = Integer.parseInt(setting("lostcity.port", "LOSTCITY_PORT") != null ? setting("lostcity.port", "LOSTCITY_PORT") : "43594") + 1;
            webPort = Integer.parseInt(setting("lostcity.webport", "LOSTCITY_WEBPORT") != null ? setting("lostcity.webport", "LOSTCITY_WEBPORT") : "8888") + 1;
        } else {
            gameHost = DEV_TUNNEL_HOST;
            gamePort = DEV_TUNNEL_PORT;
            webHost = DEV_TUNNEL_WEBHOST;
            webPort = DEV_TUNNEL_WEBPORT;
        }
        gameHost = System.getProperty("lostcity.dev.host", gameHost);
        gamePort = Integer.parseInt(System.getProperty("lostcity.dev.port", String.valueOf(gamePort)));
        webHost = System.getProperty("lostcity.dev.webhost", webHost.isEmpty() ? gameHost : webHost);
        webPort = Integer.parseInt(System.getProperty("lostcity.dev.webport", String.valueOf(webPort)));
        if (gameHost.isEmpty() || gamePort <= 0 || webHost.isEmpty() || webPort <= 0) {
            return null;
        }
        return new String[] { gameHost, String.valueOf(gamePort), webHost, String.valueOf(webPort) };
    }

    // as Client.setting: a -D property, else the environment variable
    private static String setting(String property, String env) {
        String value = System.getProperty(property);
        return value != null ? value : System.getenv(env);
    }

    // ~/.lostcity is where the client lived before the server was named Death Plateau. Its client
    // and version come across the first time (so nobody downloads the game again for the rename), and
    // then the old folder's own files are deleted, and the folder with them if that empties it. A
    // client still closing in there keeps its jar locked on Windows for a moment, so whatever cannot
    // be deleted now is left for the next start.
    private void migrate() {
        if (!oldDir.isDirectory()) {
            return;
        }
        File oldJar = new File(oldDir, ASSET), oldVersion = new File(oldDir, "client.version");
        try {
            if (!jar.isFile() && oldJar.isFile() && oldVersion.isFile()) {
                Files.copy(oldJar.toPath(), jar.toPath(), StandardCopyOption.REPLACE_EXISTING);
                Files.copy(oldVersion.toPath(), version.toPath(), StandardCopyOption.REPLACE_EXISTING);
                log("moved the client over from " + oldDir);
            }
        } catch (IOException e) {
            log("could not copy the old client: " + e.getMessage());
            jar.delete();
            version.delete();
        }
        for (String name : new String[] { ASSET, "client.version", ASSET + ".part", "launcher-run.jar" }) {
            new File(oldDir, name).delete();
        }
        if (oldDir.delete()) {
            log("removed " + oldDir);
        }
    }

    // Into a temp file, checked to be a real jar, then moved over the old one - so a download cut off
    // halfway leaves the previous client working rather than a broken one.
    private void download(String url, String tag) throws IOException {
        // The tag goes to the log, not to the bar: it is drawn inside a 268px button-sized track
        // with a percentage after it, and "Downloading update client-1482... 7%" does not fit.
        log("downloading " + tag + " from " + url);
        status("Downloading the client...");
        File tmp = new File(dir, ASSET + ".part");
        HttpURLConnection c = open(url);
        long size = c.getContentLengthLong();
        try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(tmp)) {
            byte[] buf = new byte[16384];
            long done = 0;
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
                done += n;
                progress(done, size);
            }
        }
        try (JarFile ignored = new JarFile(tmp)) {
            // opens, so it is a whole jar
        } catch (IOException e) {
            tmp.delete();
            throw new IOException("the download was damaged - try again");
        }
        // A client restarting itself for this update may still be closing, and on Windows its jar
        // is locked until it has - so the swap is retried for a while rather than failed at once.
        for (int attempt = 0; ; attempt++) {
            try {
                Files.move(tmp.toPath(), jar.toPath(), StandardCopyOption.REPLACE_EXISTING);
                break;
            } catch (IOException e) {
                if (attempt >= 30) {
                    throw e;
                }
                status("Waiting for the old client to close...");
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ie) {
                    throw e;
                }
            }
        }
        Files.write(version.toPath(), tag.getBytes(StandardCharsets.UTF_8));
        log("updated to " + tag);
    }

    /**
     * Start the launcher inside client.jar and step aside, when it is not the one running. Returns
     * whether it has taken over. Anything at all goes wrong - no jar, no launcher in it, no reading it,
     * no starting it - and this one simply carries on, because a launcher that cannot start the game is
     * the one failure a player cannot fix for themselves.
     */
    private boolean handOver() {
        if (System.getProperty(HANDOFF) != null) {
            return false; // already the newest: it was handed to us
        }
        try {
            if (!jar.isFile()) {
                return false;
            }
            int theirs = stampIn(entry(jar, "lostcity/Launcher.class"));
            if (theirs <= VERSION) {
                // the same launcher, an older one, or a client.jar from before stamps: stay here.
                // NEVER hand over to an older one - that is how two launchers hand a player back and
                // forth for ever.
                return false;
            }
            log("the launcher in " + jar.getName() + " is version " + theirs + ", this one is " + VERSION);
            // from a COPY: on Windows a running jar is locked, and client.jar has to stay replaceable
            File copy = new File(dir, "launcher-run.jar");
            Files.copy(jar.toPath(), copy.toPath(), StandardCopyOption.REPLACE_EXISTING);
            List<String> cmd = new ArrayList<>();
            cmd.add(javaBinary());
            for (String key : System.getProperties().stringPropertyNames()) {
                // The world is added once, below. Skipped here because launch() now passes it to
                // every client, so a launcher that was itself started by a client restart has it
                // set - and without this it went on the command line twice.
                if (key.startsWith("lostcity.") && !key.equals("lostcity.world")) {
                    cmd.add("-D" + key + "=" + System.getProperty(key));
                }
            }
            String world;
            synchronized (choiceLock) {
                world = choice;
            }
            if (world != null) {
                cmd.add("-Dlostcity.world=" + world); // --dev / --world=, as a property it reads back
            }
            cmd.add("-D" + HANDOFF + "=1");
            cmd.add("-cp");
            cmd.add(copy.getAbsolutePath());
            cmd.add("lostcity.Launcher");
            log("handing over to the launcher in " + jar.getName() + ": " + cmd);
            new ProcessBuilder(cmd).directory(dir).start();
            hide(); // its window opens in a moment; two would look like two launchers
            return true;
        } catch (Throwable e) {
            log("hand-over failed, carrying on with this launcher: " + e);
            return false;
        }
    }

    /** The launcher version stamped into a compiled Launcher.class, or -1 if it carries none. */
    private static int stampIn(byte[] clazz) {
        if (clazz == null) {
            return -1;
        }
        byte[] want = STAMP_PREFIX.getBytes(StandardCharsets.US_ASCII);
        for (int i = 0; i + want.length < clazz.length; i++) {
            int j = 0;
            while (j < want.length && clazz[i + j] == want[j]) {
                j++;
            }
            if (j < want.length) {
                continue;
            }
            int n = 0, at = i + want.length;
            while (at < clazz.length && clazz[at] >= '0' && clazz[at] <= '9') {
                n = n * 10 + (clazz[at++] - '0');
                if (n > 1000000) {
                    n = -1; // not a version number
                    break;
                }
            }
            if (at > i + want.length && n >= 0) {
                return n;
            }
            // the prefix on its own is in the pool too (stampIn asks for it by name): keep looking for
            // the one the number is written on the end of
        }
        return -1;
    }

    private static byte[] entry(File jarFile, String name) {
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(jarFile)) {
            java.util.zip.ZipEntry e = zip.getEntry(name);
            return e == null ? null : read(zip.getInputStream(e));
        } catch (IOException e) {
            return null;
        }
    }

    private static byte[] read(InputStream in) {
        if (in == null) {
            return null;
        }
        try (InputStream open = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n = open.read(buf); n > 0; n = open.read(buf)) {
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        } catch (IOException e) {
            return null;
        }
    }

    // dev: devEndpoint()'s answer for the dev world, null for World 1 (the client's defaults, as ever)
    private void launch(String[] dev) throws IOException {
        List<String> cmd = new ArrayList<>();
        cmd.add(javaBinary());
        String[] names = { "host", "port", "webhost", "webport" };
        for (String key : System.getProperties().stringPropertyNames()) {
            if (!key.startsWith("lostcity.")) {
                continue;
            }
            // for the dev world, the address settings are replaced below rather than passed on
            String name = key.substring("lostcity.".length());
            // ...and this one is this launch's, set fresh below. A client told to update restarts
            // the launcher with its own -Dlostcity.* settings (Client.relaunchForUpdate), so
            // without this the second launch would carry the first one's marker back in.
            if (name.equals("ready")) {
                continue;
            }
            // The world this launch picked is added once, below, whichever it is - so a stale one
            // passed in is dropped here rather than appearing twice on the command line.
            if (name.equals("world")) {
                continue;
            }
            if (dev != null && (java.util.Arrays.asList(names).contains(name) || name.startsWith("dev."))) {
                continue;
            }
            cmd.add("-D" + key + "=" + System.getProperty(key));
        }
        if (dev != null) {
            // Where the client connects (its SERVER_HOST, GAME_PORT, WEB_HOST, WEB_PORT). The same four
            // again as lostcity.dev.*: a dev client that is told to update restarts this launcher with
            // its -Dlostcity.* settings (Client.relaunchForUpdate), and they bring it straight back here.
            for (int i = 0; i < names.length; i++) {
                cmd.add("-Dlostcity." + names[i] + "=" + dev[i]);
                cmd.add("-Dlostcity.dev." + names[i] + "=" + dev[i]);
            }
        }
        // WHICH WORLD THIS IS - now for World 1 as well, not just for dev, and that is not tidying.
        // "RuneScape has been updated" restarts this launcher through the client (relaunchForUpdate),
        // which passes every -Dlostcity.* back; the world used to be among them only on dev, because
        // World 1 needed no help - the countdown started it. With the countdown gone, a World 1
        // client that updated itself would come back to a launcher sitting and waiting for a click
        // nobody is expecting to have to make.
        cmd.add("-Dlostcity.world=" + (dev != null ? DEV : WORLD_1));
        // Gone before the client starts, so the file that appears is this launch's and not one
        // left behind by the last.
        ready.delete();
        cmd.add("-Dlostcity.ready=" + ready.getAbsolutePath());
        cmd.add("-jar");
        cmd.add(jar.getAbsolutePath());
        if (dev != null) {
            // Client.main's node-id, port-offset, memory, members, storeid. Node 11 is "World 2" in the
            // friends list. The port is the exact one above (a tunnel's is anything), so no offset.
            cmd.add(String.valueOf(DEV_NODE_ID));
            cmd.add("0");
            cmd.add("highmem");
            cmd.add("members");
            // 33, NOT 32: the dev world gets its own cache directory. signlink.findcachedir names it
            // ".file_store_" + storeid and accepts 32 to 34, and both worlds used to say 32 - one
            // directory holding one world's config at a time, for two servers that are deliberately
            // never on the same content.
            //
            // That is not merely wasteful, it strands people. A map's locs are checked in
            // Client.checkScene: for every loc in the square it does `var4 &= locType.method566()`,
            // and if that stays false checkScene returns -3 and the scene NEVER finishes loading -
            // no timeout, no recovery, and no way out, because the cache is on disk and the same
            // stale config comes back on the next login. It happened on 2026-09-29: a teleport to
            // Zul-andra, whose map places loc 18635 - the very newest loc in the build, from the
            // Zulrah round - onto a client still holding World 1's older config. The character could
            // only be recovered by editing its save from outside the server.
            cmd.add("33");
        }
        log("running " + cmd);
        // THE GAME'S OWN OUTPUT GOES TO A FILE, because otherwise it goes nowhere. GameShell.run
        // calls update() and draw() with no try/catch, so one exception ends the loop thread: the
        // window keeps showing its last painted frame, nothing updates again, and the client cannot
        // even report it. It looks exactly like a scene that is loading slowly and never finishes -
        // which is how a Zul-andra teleport presented on 2026-09-29, with the stack trace going to a
        // stderr nobody was reading. Redirected here, so the next one can be read afterwards.
        // The redirect is the child's own, set by the OS, so it keeps working once this exits.
        Process game = startClient(cmd);
        // ...and this window goes when the game's window arrives, not before and not after.
        //
        // BOTH WAYS ROUND ARE WRONG WITHOUT THIS WAIT. Closing at once leaves the desktop empty
        // for the few seconds a JVM takes to start and read a cache - the same hole the splash
        // fills at the other end, and the reason a player thinks nothing happened. Leaving it up
        // until this process exits puts a launcher on screen beside the game, which was reported
        // from play on 2026-09-27 as looking like a second client. So: wait for the client to say
        // its window is up, then go.
        awaitClientWindow(game);
        hide();
    }

    // take the window down at once, from whichever thread
    private void hide() {
        closeSplash();
        LauncherWindow going = ui;
        ui = null;
        if (going != null) {
            run(going::dispose);
        }
    }

    private static String javaBinary() {
        String bin = System.getProperty("java.home") + File.separator + "bin" + File.separator;
        boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
        String java = bin + (windows ? "javaw.exe" : "java");
        if (!new File(java).isFile()) {
            java = bin + (windows ? "java.exe" : "java");
        }
        return java;
    }

    private static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestProperty("User-Agent", "LostCity-Launcher"); // GitHub's API refuses requests without one
        c.setConnectTimeout(10_000);
        c.setReadTimeout(30_000);
        c.setInstanceFollowRedirects(true);
        int code = c.getResponseCode();
        if (code != 200) {
            throw new IOException("GitHub answered " + code + " for " + url);
        }
        return c;
    }

    private static String fetch(String url) throws IOException {
        try (InputStream in = open(url).getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    // ---- the window: a title, one line of status, the two worlds, a progress bar ----

    private void window() {
        run(() -> {
            ui = new LauncherWindow("Death Plateau Launcher", String.valueOf(VERSION), new LauncherWindow.Actions() {

                public void onPlay() {
                    choose(WORLD_1);
                }

                public void onDev() {
                    choose(DEV);
                }
            });
            boolean open;
            synchronized (choiceLock) {
                open = choice == null;
            }
            ui.setChoosable(open);
            // Only now, with the window painted: closing it first leaves a gap of bare desktop
            // between the two, which is the thing the splash is there to avoid.
            closeSplash();
        });
    }

    /** Takes the splash down, from any thread, once or never. */
    private void closeSplash() {
        Splash going = splash;
        splash = null;
        if (going != null) {
            run(going::close);
        }
    }

    private void message(String text) {
        log(text.replace('\n', ' '));
        run(() -> JOptionPane.showMessageDialog(owner(), text, "Death Plateau", JOptionPane.INFORMATION_MESSAGE));
    }

    /**
     * The window to hang a dialog on, or null for one that stands on its own.
     *
     * THERE IS NOW A STRETCH WITH NO WINDOW AT ALL - the directory check and the early hand-over
     * both run before window() - and a launcher that cannot create its own folder has to be able
     * to say so. A dialog with no owner is untidy and visible; no dialog is a program that exits
     * without a word.
     */
    private java.awt.Frame owner() {
        return ui == null ? null : ui.frame();
    }

    /** Something is happening and nothing is waiting on the player: the bar, not the button. */
    private void status(String text) {
        log(text);
        run(() -> {
            if (ui != null) {
                ui.setBusy(text);
            }
            // --check has no window, and the splash is still the only thing on screen while the
            // first update check runs.
            if (splash != null) {
                splash.setStatus(text);
            }
        });
    }

    /**
     * Waiting on the player: the Play button, with text under it or none.
     *
     * Null means "nothing to say" - the window falls back to its own caption - and there is
     * nothing to write in the log either, the line that matters having been logged already.
     */
    private void choosing(String text) {
        if (text != null) {
            log(text);
        }
        run(() -> {
            if (ui != null) {
                ui.setChoosing(text);
            }
        });
    }

    private void progress(long done, long size) {
        if (size <= 0) {
            return;
        }
        int v = (int) (done * 1000 / size);
        run(() -> {
            if (ui != null) {
                ui.setProgress(v);
            }
        });
    }

    private void fail(String text) {
        log("error: " + text);
        closeSplash();
        run(() -> JOptionPane.showMessageDialog(owner(), text, "Death Plateau", JOptionPane.ERROR_MESSAGE));
        System.exit(1);
    }

    /**
     * Everything log(), status() and fail() say, kept in ~/.deathplateau/launcher.log.
     *
     * A launcher that is double-clicked has no console, so the one occasion anybody wants this output
     * - something went wrong and the game did not start - is the one occasion it has gone. On
     * 2026-09-29 a dev world came up on the wrong client cache and the question "which launcher
     * actually ran, and what did it pass the client?" could not be answered at all; the line this
     * already writes at the launch site names the storeid, and would have said so outright.
     *
     * Not the `dir` field, because log() is static and is called before there is an instance.
     */
    private static final File LOG = new File(new File(System.getProperty("user.home"), ".deathplateau"), "launcher.log");
    /** Rolled to launcher.log.old past this, so a log cannot grow without end on a machine nobody tidies. */
    private static final long LOG_MAX = 256L * 1024L;
    private static boolean logStarted;

    private static void log(String text) {
        System.out.println("[launcher] " + text);
        // NOTHING in here may throw. A launcher that dies because it could not write its own log is
        // worse than a launcher with no log, and this runs before the window exists, so a failure
        // would not even have anywhere to show itself.
        try {
            File home = LOG.getParentFile();
            if (!home.isDirectory() && !home.mkdirs()) {
                return;
            }
            if (!logStarted) {
                logStarted = true;
                if (LOG.length() > LOG_MAX) {
                    File old = new File(home, "launcher.log.old");
                    old.delete();
                    LOG.renameTo(old);
                }
                // A header per run, because the interesting question is usually "what happened the
                // LAST time I started it", and runs otherwise run together.
                append(System.lineSeparator() + "---- " + stamp() + "  launcher " + VERSION
                    + "  java " + System.getProperty("java.version")
                    + "  " + System.getProperty("os.name") + System.lineSeparator());
            }
            append(stamp() + "  " + text + System.lineSeparator());
        } catch (Throwable ignored) {
            // a log is never worth a crash
        }
    }

    /**
     * Start the game with its stdout and stderr appended to ~/.deathplateau/client.log.
     *
     * Kept apart from the plain ProcessBuilder call the hand-over uses: that one starts another
     * LAUNCHER, which writes launcher.log itself and would interleave two runs into one file.
     */
    private Process startClient(List<String> cmd) throws IOException {
        File out = new File(dir, "client.log");
        try {
            if (out.length() > LOG_MAX) {
                File old = new File(dir, "client.log.old");
                old.delete();
                out.renameTo(old);
            }
            append(out, System.lineSeparator() + "---- " + stamp() + "  " + cmd + System.lineSeparator());
            return new ProcessBuilder(cmd).directory(dir).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(out)).start();
        } catch (Throwable e) {
            // A log is never worth not starting the game for.
            log("could not redirect the game's output (" + e + ") - starting it anyway");
            return new ProcessBuilder(cmd).directory(dir).start();
        }
    }

    /** Longest this window stays up waiting to be told the game is drawing. */
    private static final long READY_TIMEOUT_MS = 45000;

    /**
     * Holds the launcher on screen until the client's window is up, or until waiting is pointless.
     *
     * THE CLIENT IS ASKED FIRST whether it will say anything at all - a client.jar older than
     * READY_SINCE never writes the marker, and waiting on one would put this window beside the
     * game for the whole timeout, which is the exact complaint the hand-over exists to fix.
     *
     * THE TIMEOUT IS ONLY FOR A CLIENT THAT SHOULD HAVE SPOKEN AND DID NOT - its window failed in
     * a way that left the process alive, say. It is not long, and deliberately: overshooting puts
     * a launcher beside the game, while cutting it short only gives back today's behaviour, where
     * the launcher closes and the desktop is briefly empty. The cheaper mistake wins.
     */
    private void awaitClientWindow(Process game) {
        // Both of these answer with null / -1 rather than throwing when the jar cannot be read.
        int stamp = stampIn(entry(jar, "lostcity/Launcher.class"));
        if (stamp < READY_SINCE) {
            log("client.jar is launcher version " + stamp + ", older than " + READY_SINCE
                + ": it writes no window marker, so not waiting for one");
            return;
        }
        long until = System.currentTimeMillis() + READY_TIMEOUT_MS;
        while (System.currentTimeMillis() < until) {
            if (ready.isFile()) {
                log("the client's window is up");
                ready.delete();
                return;
            }
            if (game != null) {
                try {
                    // Throws while it is still running, which is the case we want to keep waiting in.
                    log("the client exited before its window appeared (" + game.exitValue() + ")");
                    return;
                } catch (IllegalThreadStateException running) {
                    // still going
                }
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                return;
            }
        }
        log("gave up waiting for the client's window after " + READY_TIMEOUT_MS + "ms");
    }

    private static void append(File file, String line) throws IOException {
        FileOutputStream out = new FileOutputStream(file, true);
        try {
            out.write(line.getBytes(StandardCharsets.UTF_8));
        } finally {
            out.close();
        }
    }

    private static void append(String line) throws IOException {
        append(LOG, line);
    }

    private static String stamp() {
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date());
    }

    // on the Swing thread - directly, when that is where we are already (a button's handler)
    private static void run(Runnable r) {
        if (SwingUtilities.isEventDispatchThread()) {
            r.run();
            return;
        }
        try {
            SwingUtilities.invokeAndWait(r);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
