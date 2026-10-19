/*
 * Headless test for the sidebar rail's icons.
 *
 * WHAT THIS GUARDS, and it is a failure that nothing throws for: Icons.named falls through to the
 * plain list icon for any name it does not recognise. Two plugins that both fell through got the
 * SAME icon, so the rail carried three identical rows of bars and the only way to tell Ground
 * items from Skills was to click one. Nothing errored, nothing looked broken, the rail was simply
 * unreadable.
 *
 * So two rules, and the second is the one that matters:
 *
 *   * every icon a plugin asks for is a name the set actually has - no silent fallback;
 *   * no two of them DRAW THE SAME THING. That is checked by rendering each one and comparing
 *     pixels, not by comparing the names, because two different names mapping to one drawing is
 *     exactly the bug and comparing names cannot see it.
 *
 * The names are read out of the plugin sources rather than listed here. A list here would be a
 * second copy to keep in step, and the day it fell behind is the day this stops testing anything.
 */
package jagex2.client.plugin.ui;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;
import javax.swing.Icon;

public class IconsTest {

	static int fails;

	static void check(boolean ok, String what) {
		System.out.println((ok ? "  ok   " : "FAIL   ") + what);
		if (!ok) {
			fails++;
		}
	}

	static final int SIZE = 20;

	public static void main(String[] args) throws Exception {
		System.out.println("1. what the plugins ask for");
		Map<String, String> asked = askedFor();
		pluginTests(asked);
		System.out.println();
		System.out.println("2. no two icons draw the same thing");
		distinctTests();
		System.out.println();
		System.out.println("3. every icon actually draws something");
		inkTests();

		if (args.length > 0) {
			sheet(args[0]);
		}

		System.out.println();
		if (fails == 0) {
			System.out.println("all checks passed");
		} else {
			System.out.println(fails + " CHECK(S) FAILED");
		}
		System.exit(fails == 0 ? 0 : 1);
	}

	/** Every addPanel(title, icon, ...) in the built-in plugins, as plugin file -> icon name. */
	static Map<String, String> askedFor() throws Exception {
		Map<String, String> out = new LinkedHashMap<String, String>();
		File dir = new File(System.getProperty("dp.root", "."),
			"src/main/java/jagex2/client/plugin/builtin");
		File[] files = dir.listFiles();
		if (files == null) {
			return out;
		}
		Pattern call = Pattern.compile("addPanel\\(\\s*\"[^\"]*\"\\s*,\\s*\"([^\"]*)\"");
		for (File f : files) {
			if (!f.getName().endsWith(".java")) {
				continue;
			}
			String src = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
			Matcher m = call.matcher(src);
			while (m.find()) {
				out.put(f.getName(), m.group(1));
			}
		}
		return out;
	}

	/**
	 * The icons the rail wears regardless of any plugin - Plugins and Plugin hub - read out of
	 * Sidebar so the list cannot fall behind it.
	 *
	 * THESE COUNT AS TAKEN. A plugin asking for "wrench" collides with the Plugins tab sitting
	 * directly above it, and the first version of this test did not look at them: it compared
	 * plugins only against each other, so the notifier wearing the plugin list's wrench passed
	 * clean. A mutation put it back and nothing fired.
	 */
	static List<String> railTabs() throws Exception {
		List<String> out = new ArrayList<String>();
		File f = new File(System.getProperty("dp.root", "."),
			"src/main/java/jagex2/client/plugin/ui/Sidebar.java");
		if (!f.exists()) {
			return out;
		}
		String src = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
		Matcher m = Pattern.compile("new Tab\\(Icons\\.(\\w+)\\(").matcher(src);
		while (m.find()) {
			// Icons.named is the PLUGIN tab's constructor, not a fixed tab - it is how a plugin's
			// chosen icon gets made. Counting it would reserve a method name nothing can ask for.
			if (!"named".equals(m.group(1))) {
				out.add(m.group(1));
			}
		}
		return out;
	}

	static void pluginTests(Map<String, String> asked) throws Exception {
		check(asked.size() >= 3, "found the plugins' panels in the source (" + asked.size() + ")");
		for (Map.Entry<String, String> e : asked.entrySet()) {
			check(Icons.isKnown(e.getValue()),
				e.getKey() + " asks for \"" + e.getValue() + "\", which the set has");
		}

		List<String> fixed = railTabs();
		check(fixed.size() >= 2, "found the rail's own fixed tabs in Sidebar (" + fixed + ")");
		for (Map.Entry<String, String> e : asked.entrySet()) {
			check(!fixed.contains(e.getValue()),
				e.getKey() + " does not take an icon the rail already wears");
		}

		// The actual complaint: two pages wearing one icon.
		List<String> seen = new ArrayList<String>();
		boolean clash = false;
		for (String name : asked.values()) {
			if (seen.contains(name)) {
				clash = true;
			}
			seen.add(name);
		}
		check(!clash, "and no two plugins ask for the same one");
	}

	static int[] render(String name) {
		Icon icon = Icons.named(name, SIZE, Color.WHITE);
		BufferedImage img = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		icon.paintIcon(null, g, 0, 0);
		g.dispose();
		return img.getRGB(0, 0, SIZE, SIZE, null, 0, SIZE);
	}

	static boolean same(int[] a, int[] b) {
		if (a.length != b.length) {
			return false;
		}
		for (int i = 0; i < a.length; i++) {
			if (a[i] != b[i]) {
				return false;
			}
		}
		return true;
	}

	static void distinctTests() {
		String[] names = Icons.KNOWN;
		int[][] pixels = new int[names.length][];
		for (int i = 0; i < names.length; i++) {
			pixels[i] = render(names[i]);
		}
		int clashes = 0;
		for (int i = 0; i < names.length; i++) {
			for (int j = i + 1; j < names.length; j++) {
				if (same(pixels[i], pixels[j])) {
					check(false, names[i] + " and " + names[j] + " draw exactly the same thing");
					clashes++;
				}
			}
		}
		check(clashes == 0, "all " + names.length + " icons in the set are different pictures");

		// An unknown name is allowed to fall back - but the test exists to prove it IS a
		// fallback, so nobody mistakes the silence for a match.
		check(same(render("no-such-icon"), render("list")),
			"an unknown name falls back to the plain list icon, as documented");
		// And isKnown has to actually refuse it, or the check above that every plugin asks for a
		// real name is satisfied by anything at all.
		check(!Icons.isKnown("no-such-icon"), "and isKnown says so rather than waving it through");
		check(!Icons.isKnown(""), "nor does it accept an empty name");
	}

	static void inkTests() {
		for (String name : Icons.KNOWN) {
			int[] px = render(name);
			int lit = 0;
			for (int p : px) {
				if ((p >>> 24) > 8) {
					lit++;
				}
			}
			// A shape that covers almost nothing is a shape nobody can see at tab size; one that
			// covers nearly everything is a filled square.
			check(lit > SIZE * 2 && lit < SIZE * SIZE * 3 / 4,
				name + " covers a sensible amount of its box (" + lit + " of " + SIZE * SIZE + ")");
		}
	}

	/** A strip of every icon, so the set can be looked at rather than described. */
	static void sheet(String path) throws Exception {
		String[] names = Icons.KNOWN;
		int pad = 10;
		int cell = SIZE * 3;
		BufferedImage out = new BufferedImage(names.length * (cell + pad) + pad, cell + pad * 2,
			BufferedImage.TYPE_INT_RGB);
		Graphics2D g = out.createGraphics();
		g.setColor(new Color(0x1E1E1E));
		g.fillRect(0, 0, out.getWidth(), out.getHeight());
		int x = pad;
		for (String name : names) {
			BufferedImage one = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
			Graphics2D og = one.createGraphics();
			Icons.named(name, SIZE, new Color(0xC8, 0x3C, 0x3C)).paintIcon(null, og, 0, 0);
			og.dispose();
			g.drawImage(one, x, pad, cell, cell, null);
			x += cell + pad;
		}
		g.dispose();
		ImageIO.write(out, "png", new File(path));
		System.out.println("  sheet -> " + path);
	}
}
