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
        Method handOver = c.getDeclaredMethod("handOver"); handOver.setAccessible(true);
        File home = new File(sandbox, ".deathplateau"); home.mkdirs();
        File installed = new File(home, "client.jar");

        Files.copy(clientJar.toPath(), installed.toPath(), StandardCopyOption.REPLACE_EXISTING);
        check("the same version: stays put", handOver.invoke(ctor.newInstance()), false);
        Files.copy(olderJar.toPath(), installed.toPath(), StandardCopyOption.REPLACE_EXISTING);
        check("an OLDER launcher in the jar: stays put (never hands back)", handOver.invoke(ctor.newInstance()), false);
        installed.delete();
        check("no client.jar at all: stays put", handOver.invoke(ctor.newInstance()), false);
        Files.copy(newerJar.toPath(), installed.toPath(), StandardCopyOption.REPLACE_EXISTING);
        System.setProperty("lostcity.launcherhandoff", "1");
        check("already handed over once: stays put, whatever the jar holds", handOver.invoke(ctor.newInstance()), false);
        System.clearProperty("lostcity.launcherhandoff");

        System.out.println("\n" + ok + " ok, " + bad + " failed");
        System.exit(bad == 0 ? 0 : 1);
    }
}
