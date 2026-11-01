// Does the launcher hand over to the one inside client.jar, and only to a newer one?
// Calls the real private methods; starts no processes (every case here decides "no").
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;

public class HandOverTest {
    static int ok, bad;
    static void check(String what, Object got, Object want) {
        boolean pass = String.valueOf(got).equals(String.valueOf(want));
        if (pass) ok++; else bad++;
        System.out.println((pass ? "  ok   " : "  FAIL ") + what + ": " + got + (pass ? "" : "   (want " + want + ")"));
    }

    public static void main(String[] a) throws Exception {
        File sandbox = new File(a[0]), clientJar = new File(a[1]), olderJar = new File(a[2]), newerJar = new File(a[3]);
        System.setProperty("user.home", sandbox.getAbsolutePath());
        Class<?> c = Class.forName("lostcity.Launcher");

        Method stampIn = c.getDeclaredMethod("stampIn", byte[].class);
        Method entry = c.getDeclaredMethod("entry", File.class, String.class);
        stampIn.setAccessible(true); entry.setAccessible(true);
        Field ver = c.getDeclaredField("VERSION"); ver.setAccessible(true);
        int mine = ver.getInt(null);
        System.out.println("this launcher is version " + mine);

        Object bytes = entry.invoke(null, clientJar, "lostcity/Launcher.class");
        check("the launcher inside client.jar carries a version stamp", stampIn.invoke(null, bytes), mine);
        check("a jar with no launcher in it reads as none", stampIn.invoke(null, entry.invoke(null, clientJar, "lostcity/NoSuch.class")), -1);
        check("an older launcher (stamped 1) reads as 1", stampIn.invoke(null, entry.invoke(null, olderJar, "lostcity/Launcher.class")), 1);
        check("a newer launcher (stamped 99) reads as 99", stampIn.invoke(null, entry.invoke(null, newerJar, "lostcity/Launcher.class")), 99);

        Constructor<?> ctor = c.getDeclaredConstructor(); ctor.setAccessible(true);
        Method handOver = c.getDeclaredMethod("handOver", boolean.class); handOver.setAccessible(true);
        Method command = c.getDeclaredMethod("handOverCommand", File.class, boolean.class);
        command.setAccessible(true);
        File home = new File(sandbox, ".deathplateau"); home.mkdirs();
        File installed = new File(home, "client.jar");

        Files.copy(clientJar.toPath(), installed.toPath(), StandardCopyOption.REPLACE_EXISTING);
        check("the same version: stays put", handOver.invoke(ctor.newInstance(), false), false);
        Files.copy(olderJar.toPath(), installed.toPath(), StandardCopyOption.REPLACE_EXISTING);
        check("an OLDER launcher in the jar: stays put (never hands back)", handOver.invoke(ctor.newInstance(), false), false);
        installed.delete();
        check("no client.jar at all: stays put", handOver.invoke(ctor.newInstance(), false), false);
        Files.copy(newerJar.toPath(), installed.toPath(), StandardCopyOption.REPLACE_EXISTING);
        System.setProperty("lostcity.launcherhandoff", "1");
        check("already handed over once: stays put, whatever the jar holds", handOver.invoke(ctor.newInstance(), true), false);
        System.clearProperty("lostcity.launcherhandoff");

        // WHICH FLAGS A HAND-OVER CARRIES, which is the whole of the bug that shipped as
        // "the jar doesn't search for an update before trying to play, and then it tells you
        // there's an update". HANDOFF means "you were handed to, do not hand over again" and
        // CHECKED means "somebody has just checked for you". They were one flag, and moving the
        // hand-over to before the window made the second meaning false while the first stayed
        // true: the launcher that was handed to skipped the check it had never had.
        @SuppressWarnings("unchecked")
        java.util.List<String> after = (java.util.List<String>) command.invoke(ctor.newInstance(), installed, true);
        @SuppressWarnings("unchecked")
        java.util.List<String> before = (java.util.List<String>) command.invoke(ctor.newInstance(), installed, false);
        check("a hand-over says it handed over", after.contains("-Dlostcity.launcherhandoff=1"), true);
        check("...and the early one does too", before.contains("-Dlostcity.launcherhandoff=1"), true);
        check("a hand-over AFTER a check says the check is done",
              after.contains("-Dlostcity.updatechecked=1"), true);
        check("...and the one BEFORE any check does not, or the next launcher skips a check "
              + "nobody did", before.contains("-Dlostcity.updatechecked=1"), false);
        check("both of them start the launcher out of the jar they copied",
              after.get(after.size() - 1) + " " + before.get(before.size() - 1),
              "lostcity.Launcher lostcity.Launcher");

        System.out.println("\n" + ok + " ok, " + bad + " failed");
        System.exit(bad == 0 ? 0 : 1);
    }
}
