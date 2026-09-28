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
// WHICH WORLD. The window has two buttons, World 1 and the dev world. Nobody has to press either: after
// a few seconds World 1 starts on its own, as it always has, and pressing World 1 starts it at once. The
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
    // how long World 1 waits for somebody to pick the dev world instead
    private static final long CHOICE_MILLIS = 3000;
    private static final String WORLD_1 = "1", DEV = "dev";

    private static final Pattern TAG = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern URL_ = Pattern.compile("\"browser_download_url\"\\s*:\\s*\"([^\"]*/" + Pattern.quote(ASSET) + ")\"");

    private final File dir = new File(System.getProperty("user.home"), ".deathplateau");
    // where launchers before the rename kept the client - moved out of by migrate()
    private final File oldDir = new File(System.getProperty("user.home"), ".lostcity");
    private final File jar = new File(dir, ASSET);
    private final File version = new File(dir, "client.version");

    private JFrame frame;
    private JLabel label;
    private JProgressBar bar;
    private JButton world1Button, devButton;

    // WORLD_1, DEV, or null while nobody has chosen - guarded by choiceLock
    private final Object choiceLock = new Object();
    private String choice;
    private long windowShownAt;
    private boolean devFailed;

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
        if (!check) {
            window();
        }
        if (!dir.isDirectory() && !dir.mkdirs()) {
            fail("Could not create " + dir);
            return;
        }
        migrate();

        try {
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

    // The world to start: one chosen already (--dev, lostcity.world), else whichever button is pressed
    // first - and World 1 if neither is within CHOICE_MILLIS of the window opening, so a player who never
    // looks at the buttons starts the game just as before. After a dev world that could not be reached,
    // nothing starts by itself: it waits for a button.
    private String awaitChoice() {
        for (;;) {
            long left;
            boolean waitForButton;
            synchronized (choiceLock) {
                if (choice != null) {
                    return choice;
                }
                left = windowShownAt + CHOICE_MILLIS - System.currentTimeMillis();
                waitForButton = devFailed;
                if (!waitForButton && left <= 0) {
                    choice = WORLD_1;
                    return choice;
                }
            }
            // not while holding the lock: status() waits for the Swing thread, which takes it in choose()
            status(waitForButton ? "Choose a world." : "Starting World 1 in " + ((left + 999) / 1000) + "...   (or choose the dev world)");
            synchronized (choiceLock) {
                if (choice == null) {
                    try {
                        choiceLock.wait(waitForButton ? 0 : Math.max(1, Math.min(left, 1000)));
                    } catch (InterruptedException e) {
                        choice = WORLD_1;
                    }
                }
            }
        }
    }

    // a button (on the Swing thread), or null to take the choice back after a dev world that failed
    private void choose(String world) {
        synchronized (choiceLock) {
            if (world == null) {
                devFailed = true;
            }
            choice = world;
            choiceLock.notifyAll();
        }
        run(() -> {
            if (world1Button != null) {
                world1Button.setEnabled(world == null);
                devButton.setEnabled(world == null);
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
        status("Downloading update " + tag + "...");
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
            if (dev != null && (java.util.Arrays.asList(names).contains(name) || name.startsWith("dev.") || name.equals("world"))) {
                continue;
            }
            cmd.add("-D" + key + "=" + System.getProperty(key));
        }
        if (dev != null) {
            // Where the client connects (its SERVER_HOST, GAME_PORT, WEB_HOST, WEB_PORT). The same four
            // again as lostcity.dev.*, with lostcity.world: a dev client that is told to update restarts
            // this launcher with its -Dlostcity.* settings (Client.relaunchForUpdate), and they bring it
            // straight back here.
            for (int i = 0; i < names.length; i++) {
                cmd.add("-Dlostcity." + names[i] + "=" + dev[i]);
                cmd.add("-Dlostcity.dev." + names[i] + "=" + dev[i]);
            }
            cmd.add("-Dlostcity.world=" + DEV);
        }
        cmd.add("-jar");
        cmd.add(jar.getAbsolutePath());
        if (dev != null) {
            // Client.main's node-id, port-offset, memory, members, storeid. Node 11 is "World 2" in the
            // friends list. The port is the exact one above (a tunnel's is anything), so no offset.
            cmd.add(String.valueOf(DEV_NODE_ID));
            cmd.add("0");
            cmd.add("highmem");
            cmd.add("members");
            cmd.add("32");
        }
        log("running " + cmd);
        new ProcessBuilder(cmd).directory(dir).start();
        // ...and this window goes now, not whenever the JVM gets round to exiting: the game's own
        // window takes a few seconds to appear, and a launcher still on screen beside it looks like a
        // second client (reported from play 2026-09-27)
        hide();
    }

    // take the window down at once, from whichever thread
    private void hide() {
        JFrame f = frame;
        frame = null;
        if (f != null) {
            run(() -> {
                f.setVisible(false);
                f.dispose();
            });
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
            frame = new JFrame("Death Plateau Launcher");
            label = new JLabel("Starting...");
            bar = new JProgressBar(0, 1000);
            bar.setIndeterminate(true);
            label.setBorder(BorderFactory.createEmptyBorder(12, 12, 8, 12));
            bar.setBorder(BorderFactory.createEmptyBorder(0, 12, 12, 12));

            world1Button = new JButton("World 1");
            devButton = new JButton("Dev world (staff only)");
            world1Button.addActionListener(e -> choose(WORLD_1));
            devButton.addActionListener(e -> choose(DEV));
            boolean open;
            synchronized (choiceLock) {
                open = choice == null;
            }
            world1Button.setEnabled(open);
            devButton.setEnabled(open);
            JPanel worlds = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
            worlds.add(world1Button);
            worlds.add(devButton);

            frame.getContentPane().add(label, BorderLayout.NORTH);
            frame.getContentPane().add(worlds, BorderLayout.CENTER);
            frame.getContentPane().add(bar, BorderLayout.SOUTH);
            frame.getRootPane().setDefaultButton(world1Button); // Enter plays World 1
            frame.setResizable(false); // the game's window is fixed; two resizable windows read as two clients
            frame.setPreferredSize(new Dimension(360, 140));
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            world1Button.requestFocusInWindow();
            windowShownAt = System.currentTimeMillis();
        });
    }

    private void message(String text) {
        log(text.replace('\n', ' '));
        if (frame != null) {
            run(() -> JOptionPane.showMessageDialog(frame, text, "Death Plateau", JOptionPane.INFORMATION_MESSAGE));
        }
    }

    private void status(String text) {
        log(text);
        run(() -> {
            if (label != null) {
                label.setText(text);
                bar.setIndeterminate(true);
            }
        });
    }

    private void progress(long done, long size) {
        if (size <= 0) {
            return;
        }
        int v = (int) (done * 1000 / size);
        run(() -> {
            if (bar != null) {
                bar.setIndeterminate(false);
                bar.setValue(v);
            }
        });
    }

    private void fail(String text) {
        log("error: " + text);
        if (frame != null) {
            run(() -> JOptionPane.showMessageDialog(frame, text, "Death Plateau", JOptionPane.ERROR_MESSAGE));
        }
        System.exit(1);
    }

    private static void log(String text) {
        System.out.println("[launcher] " + text);
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
