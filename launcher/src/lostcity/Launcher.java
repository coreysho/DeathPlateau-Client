package lostcity;

import java.awt.BorderLayout;
import java.awt.Dimension;
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
import javax.swing.JLabel;
import javax.swing.JOptionPane;
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
//   java -jar Death-Plateau-Launcher.jar --check   update if needed, then exit (for testing)
//
// Any -Dlostcity.* property given to the launcher (lostcity.host, .port, .webhost, .webport) is passed on to
// the client. Java 8, no dependencies: the one thing a player needs is the Java they already have.
public final class Launcher {
    private static final String REPO = "coreysho/DeathPlateau-Client";
    private static final String ASSET = "client.jar";
    private static final String API = "https://api.github.com/repos/" + REPO + "/releases/latest";

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

    public static void main(String[] args) {
        boolean check = args.length > 0 && args[0].equals("--check");
        new Launcher().run(check);
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

        status("Starting...");
        try {
            launch();
        } catch (IOException e) {
            fail("Couldn't start the game: " + e.getMessage());
            return;
        }
        System.exit(0);
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

    private void launch() throws IOException {
        String bin = System.getProperty("java.home") + File.separator + "bin" + File.separator;
        boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
        String java = bin + (windows ? "javaw.exe" : "java");
        if (!new File(java).isFile()) {
            java = bin + (windows ? "java.exe" : "java");
        }
        List<String> cmd = new ArrayList<>();
        cmd.add(java);
        for (String key : System.getProperties().stringPropertyNames()) {
            if (key.startsWith("lostcity.")) {
                cmd.add("-D" + key + "=" + System.getProperty(key));
            }
        }
        cmd.add("-jar");
        cmd.add(jar.getAbsolutePath());
        new ProcessBuilder(cmd).directory(dir).start();
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

    // ---- the window: a title, one line of status, a progress bar ----

    private void window() {
        run(() -> {
            frame = new JFrame("Death Plateau Launcher");
            label = new JLabel("Starting...");
            bar = new JProgressBar(0, 1000);
            bar.setIndeterminate(true);
            label.setBorder(BorderFactory.createEmptyBorder(12, 12, 8, 12));
            bar.setBorder(BorderFactory.createEmptyBorder(0, 12, 12, 12));
            frame.getContentPane().add(label, BorderLayout.NORTH);
            frame.getContentPane().add(bar, BorderLayout.SOUTH);
            frame.setPreferredSize(new Dimension(360, 100));
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
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

    private static void run(Runnable r) {
        try {
            SwingUtilities.invokeAndWait(r);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
