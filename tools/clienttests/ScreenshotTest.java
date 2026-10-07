// Does the camera button produce a picture of the game?
//
// The capture itself is three lines, and all three of its failure modes are quiet: a buffer read
// while the game loop is halfway through writing it (a torn frame), a second shot in the same
// second overwriting the first, and an unwritable home directory taking the client down with it.
// Driven by tools/clienttests/run_panelpreview.py.
package jagex2.client;

import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import javax.imageio.ImageIO;

import jagex2.graphics.PixMap;

public class ScreenshotTest {

	static int fails;

	static void check(boolean ok, String what) {
		System.out.println((ok ? "  ok   " : "FAIL   ") + what);
		if (!ok) {
			fails++;
		}
	}

	public static void main(String[] args) throws Exception {
		File home = new File(args[0]);
		System.setProperty("user.home", home.getAbsolutePath());
		File shots = new File(home, ".deathplateau/screenshots");

		Client client = new Client();

		// Nothing to photograph yet: the viewport buffer does not exist until a scene does.
		call(client, "writeScreenshot");
		check(!shots.isDirectory() || shots.listFiles().length == 0,
			"before there is a game, it writes nothing rather than an empty file");

		// A viewport with a pattern in it, so the png can be compared pixel for pixel.
		int w = 64;
		int h = 48;
		// The client itself as the component: PixMap asks it for an Image, and an undisplayed
		// one cannot supply it. Under the runner's virtual display this one can.
		PixMap area = new PixMap(h, client, w);
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				area.data[y * w + x] = (x * 4) << 16 | (y * 5) << 8 | 0x40;
			}
		}
		set(client, "areaViewport", area);

		call(client, "writeScreenshot");
		File[] written = shots.listFiles();
		check(written != null && written.length == 1,
			"one shot writes one file (" + (written == null ? 0 : written.length) + ")");
		if (written == null || written.length == 0) {
			done();
		}
		check(written[0].getName().startsWith("deathplateau_") && written[0].getName().endsWith(".png"),
			"...named for the client and the time: " + written[0].getName());

		BufferedImage read = ImageIO.read(written[0]);
		check(read != null && read.getWidth() == w && read.getHeight() == h,
			"...at the viewport's own size (" + (read == null ? "unreadable"
				: read.getWidth() + "x" + read.getHeight()) + ")");
		boolean same = read != null;
		for (int y = 0; same && y < h; y++) {
			for (int x = 0; x < w; x++) {
				if ((read.getRGB(x, y) & 0xFFFFFF) != (area.data[y * w + x] & 0xFFFFFF)) {
					same = false;
					break;
				}
			}
		}
		check(same, "...and every pixel is the one the game drew");

		// Twice in the same second. The timestamp alone would collide and the second shot would
		// quietly replace the first.
		call(client, "writeScreenshot");
		call(client, "writeScreenshot");
		check(shots.listFiles().length == 3,
			"three shots in the same second are three files ("
				+ shots.listFiles().length + ")");

		// An unwritable home. A screenshot is not worth losing the game over.
		set(client, "areaViewport", area);
		System.setProperty("user.home", "/proc/nowhere-at-all");
		boolean threw = false;
		try {
			call(client, "writeScreenshot");
		} catch (Throwable error) {
			threw = true;
		}
		check(!threw, "a home it cannot write to is a chat message, not an exception");

		done();
	}

	static void done() {
		System.out.println(fails == 0 ? "ALL PASS" : fails + " FAILED");
		System.exit(fails == 0 ? 0 : 1);
	}

	static void set(Object target, String name, Object value) throws Exception {
		Field f = Client.class.getDeclaredField(name);
		f.setAccessible(true);
		f.set(target, value);
	}

	static void call(Object target, String name) throws Exception {
		Method m = Client.class.getDeclaredMethod(name);
		m.setAccessible(true);
		m.invoke(target);
	}
}
