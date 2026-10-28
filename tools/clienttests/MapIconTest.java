/*
 * Headless test for the minimap's function icons, read out of a built media archive.
 *
 * WHAT THIS GUARDS. A map icon is three numbers that have to agree and nothing checks that they
 * do: the slot a sprite was pasted into on content/sprites/mapfunction.png, the mapfunction=
 * number on the loc, and the COUNT THE CLIENT LOOPS OVER in Client.java. The loop is the one that
 * bites - it is a literal, it was 71 while the sheet held 80 slots, and an icon written into slot
 * 71 would simply never have been loaded. Nothing throws: imageMapfunction[71] stays null and the
 * minimap draws the tile with no icon on it, which is indistinguishable from "I forgot to place
 * the loc".
 *
 * The sheet's spare slots are also a trap of their own. Slots 70-79 were all written with the
 * trading post's icon when the row was added, so a slot that was never filled still decodes, still
 * has pixels, and draws SOMETHING - the wrong thing. So the test is not "does 71 load" but "does
 * 71 draw something no other slot draws", which is a pixel comparison and cannot be done by
 * reading names or counting bytes.
 *
 * The loop count is read out of Client.java rather than written here, so this cannot fall behind
 * the client it is testing.
 */
package jagex2.client;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

import jagex2.graphics.Pix32;
import jagex2.io.Jagfile;

public class MapIconTest {

	static int ok;
	static int fails;

	static void check(String what, boolean pass, String detail) {
		if (pass) {
			ok++;
			System.out.println("  ok   " + what);
		} else {
			fails++;
			System.out.println("  FAIL " + what + ": " + detail);
		}
	}

	/** The literal in Client.java's mapfunction loop - the number of icons the client will load.
	 *
	 *  Found by walking back from the load itself rather than by matching the whole loop: the
	 *  shape of the loop is allowed to change, the thing being counted is not. */
	static int clientLoopCount(String clientSrc) throws Exception {
		String src = new String(Files.readAllBytes(Paths.get(clientSrc)), "UTF-8");
		int load = src.indexOf("this.imageMapfunction[i] = new Pix32");
		if (load < 0) {
			throw new IllegalStateException("no mapfunction load in " + clientSrc);
		}
		int lt = src.lastIndexOf("i < ", load);
		if (lt < 0) {
			throw new IllegalStateException("no loop bound before the mapfunction load");
		}
		int p = lt + 4;
		int n = 0;
		boolean any = false;
		while (p < src.length() && Character.isDigit(src.charAt(p))) {
			n = n * 10 + (src.charAt(p) - '0');
			p++;
			any = true;
		}
		if (!any) {
			throw new IllegalStateException("the mapfunction loop bound is not a literal");
		}
		return n;
	}

	/** Every slot the sheet actually holds, as a signature of its drawn pixels. */
	static String signature(Pix32 p) {
		StringBuilder sb = new StringBuilder();
		for (int v : p.pixels) {
			sb.append(Integer.toHexString(v));
			sb.append(',');
		}
		return sb.toString();
	}

	public static void main(String[] args) throws Exception {
		String media = args[0];                      // Engine-TS/data/pack/client/media
		String clientSrc = args[1];                  // src/main/java/jagex2/client/Client.java
		String outDir = args.length > 2 ? args[2] : null;

		Jagfile jag = new Jagfile(Files.readAllBytes(Paths.get(media)));
		int loop = clientLoopCount(clientSrc);
		System.out.println("== the client loads " + loop + " map icons");

		List<Pix32> icons = new ArrayList<>();
		int loaded = 0;
		for (int i = 0; i < loop; i++) {
			Pix32 p = null;
			try {
				p = new Pix32(jag, "mapfunction", i);
			} catch (Exception e) {
				p = null;
			}
			icons.add(p);
			if (p != null) loaded++;
		}
		check("all " + loop + " load out of the archive", loaded == loop,
			loaded + " of " + loop + " loaded");

		System.out.println("== and no two of them draw the same thing");
		Map<String, Integer> seen = new HashMap<>();
		List<String> dupes = new ArrayList<>();
		for (int i = 0; i < icons.size(); i++) {
			if (icons.get(i) == null) continue;
			String sig = signature(icons.get(i));
			Integer first = seen.get(sig);
			if (first != null) {
				dupes.add(i + " draws the same as " + first);
			} else {
				seen.put(sig, i);
			}
		}
		check("every icon is its own picture", dupes.isEmpty(), String.join("; ", dupes));

		if (outDir != null) {
			new File(outDir).mkdirs();
			int cell = 15, cols = 12;
			int rows = (icons.size() + cols - 1) / cols;
			BufferedImage sheet = new BufferedImage(cols * cell, rows * cell, BufferedImage.TYPE_INT_RGB);
			for (int i = 0; i < icons.size(); i++) {
				Pix32 p = icons.get(i);
				if (p == null) continue;
				for (int y = 0; y < p.ohi && y < cell; y++) {
					for (int x = 0; x < p.owi && x < cell; x++) {
						sheet.setRGB((i % cols) * cell + x, (i / cols) * cell + y, p.pixels[y * p.owi + x]);
					}
				}
			}
			File out = new File(outDir, "mapfunction.png");
			ImageIO.write(sheet, "png", out);
			System.out.println("wrote " + out.getAbsolutePath());
		}

		System.out.println();
		System.out.println(ok + " ok, " + fails + " failed");
		System.exit(fails > 0 ? 1 : 0);
	}
}
