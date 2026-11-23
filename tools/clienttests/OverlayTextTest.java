// Driven by tools/clienttests/run_overlaytexttest.py.
//
// THE FONT IS AN ARRAY OF 256 AND THE INDEX IS THE CHARACTER. PixFont keeps a glyph per
// character in charMask[256] and charAdvance[256], indexed by the char itself - so a character
// above 255 is an ArrayIndexOutOfBoundsException the moment anything measures or draws it, and
// one between 127 and 255 the 2004 font never defined is a null mask and an NPE inside
// plotLetter. A plugin cannot catch either: five frames later PluginManager has turned it off,
// and when a player switches it back on it draws the same string and dies again.
//
// WHICH IS WHAT HAPPENED. The quest helper's step table carried a typographic apostrophe - the
// curly one - in 32 rows across 16 quests, because the sims' own prose is written with them:
// "Go west to Doric's hut". The Knight's Sword had one in its FIRST leg. Players reported the
// helper "quitting and not being able to be turned back on", which is exactly what this does.
//
// SO THE SUITE THAT MISSED IT. Every other check of a plugin's drawing uses a recording font
// that overrides the draw calls and never touches those arrays, which is why 172 passing checks
// said nothing about any of this. The font here is a REAL PixFont - the protected constructor
// exists for exactly this - with glyphs filled in for printable ASCII and nothing else, so the
// real stringWidTag, drawString, drawStringTag and plotLetter all run.
package jagex2.client.plugin;

import jagex2.graphics.Pix2D;
import jagex2.graphics.PixFont;

public class OverlayTextTest {

	static final int W = 320;
	static final int H = 200;

	static int passes;
	static int fails;

	/** The curly apostrophe out of the table, in the row that broke it. */
	static final String CURLY = "Go west to Doric’s hut.";
	static final String STRAIGHT = "Go west to Doric's hut.";

	static int[] pixels = new int[W * H];

	/**
	 * A real PixFont with printable ASCII in it and nothing else.
	 *
	 * NOTHING IS OVERRIDDEN. The arrays are the font's own, filled the way the archive would
	 * fill them, so every measuring and drawing path below is the client's.
	 */
	static final class AsciiFont extends PixFont {

		AsciiFont() {
			this.height = 12;
			for (char c = ' '; c <= '~'; c++) {
				this.charAdvance[c] = 4;
				this.charMask[c] = new byte[] { (byte) 255 };
				this.charMaskWidth[c] = 1;
				this.charMaskHeight[c] = 1;
				this.charOffsetX[c] = 0;
				this.charOffsetY[c] = 10;
			}
		}
	}

	static void check(boolean ok, String what) {
		if (ok) {
			passes++;
			System.out.println("  ok   " + what);
		} else {
			fails++;
			System.out.println("FAIL   " + what);
		}
	}

	public static void main(String[] args) {
		AsciiFont font = new AsciiFont();
		Pix2D.bind(W, H, pixels);

		System.out.println("1. what the font does with it, which is the hazard");
		String threw = "";
		try {
			font.stringWidTag(CURLY);
		} catch (Throwable error) {
			threw = error.getClass().getName();
		}
		check(threw.indexOf("ArrayIndexOutOfBounds") >= 0,
			"measuring a curly apostrophe through the font throws (" + (threw.isEmpty() ? "it did not" : threw) + ")");
		threw = "";
		try {
			font.drawString(10, 0xFFFFFF, 20, CURLY);
		} catch (Throwable error) {
			threw = error.getClass().getName();
		}
		check(threw.indexOf("ArrayIndexOutOfBounds") >= 0,
			"...and so does drawing it (" + (threw.isEmpty() ? "it did not" : threw) + ")");
		// AND THE OTHER HALF OF IT, quieter, and worth measuring rather than assuming: a
		// character between 127 and 255 is INSIDE those arrays, so it throws nothing - its mask is
		// simply empty and the word comes out with a hole in it. Only the ones above 255 take the
		// plugin down, and both are worth mapping.
		int[] hole = new int[W * H];
		Pix2D.bind(W, H, hole);
		threw = "";
		try {
			font.drawString(10, 0xFFFFFF, 20, "a\u00A0b");
		} catch (Throwable error) {
			threw = error.getClass().getName();
		}
		Pix2D.bind(W, H, pixels);
		int drawn = 0;
		for (int i = 0; i < hole.length; i++) {
			if (hole[i] != 0) {
				drawn++;
			}
		}
		check(threw.isEmpty(), "a character the font never defined does not throw"
			+ (threw.isEmpty() ? "" : " - " + threw));
		check(drawn == 2, "...it draws a hole where it should be, so only the two letters round it"
			+ " land (" + drawn + " pixels)");

		System.out.println("2. what a plugin gets instead, through the drawing API");
		OverlayGraphics g = new OverlayGraphics(font, font, font);
		InteractiveRegions regions = new InteractiveRegions();
		String[] hard = new String[] {
			CURLY,
			"an em dash — here",
			"an ellipsis…",
			"a non-breaking space",
			"a bullet • and a middot ·",
			"quotes “in” them",
			"something unmapped: 中",
		};
		for (int i = 0; i < hard.length; i++) {
			threw = "";
			try {
				g.reset(W, H, regions, null, 0, 0);
				g.text(4, 20, hard[i], 0xFFFFFF);
				g.textFlat(4, 40, hard[i], 0xFFFFFF);
				g.textCentred(W / 2, 60, hard[i], 0xFFFFFF);
				g.textCentredOutlined(W / 2, 80, hard[i], 0xFFFFFF);
				g.textRight(W - 4, 100, hard[i], 0xFFFFFF);
				g.textWidth(hard[i]);
				g.panel(4, 120, hard[i], new String[] { hard[i], hard[i] }, 0xFFB000, 0xFFFFFF);
			} catch (Throwable error) {
				threw = error.getClass().getName() + ": " + error.getMessage();
			}
			check(threw.isEmpty(), "every text call draws \"" + summary(hard[i]) + "\""
				+ (threw.isEmpty() ? "" : " - " + threw));
		}

		System.out.println("3. and it reads as what it was imitating");
		g.reset(W, H, regions, null, 0, 0);
		check(g.textWidth(CURLY) == g.textWidth(STRAIGHT),
			"a curly apostrophe measures as a straight one (" + g.textWidth(CURLY) + " vs " + g.textWidth(STRAIGHT) + ")");
		check(g.textWidth("a…b") == g.textWidth("a...b"),
			"an ellipsis measures as three stops (" + g.textWidth("a…b") + " vs " + g.textWidth("a...b") + ")");
		check(g.textWidth("a—b") == g.textWidth("a-b"), "an em dash measures as a hyphen");
		check(g.textWidth("a“b”c") == g.textWidth("a\"b\"c"), "curly quotes measure as straight ones");
		check(g.textWidth("a中b") == g.textWidth("a?b"), "and anything unmapped is one question mark");
		check(g.textWidth("a\u00A0b") == g.textWidth("a b"),
			"a non-breaking space measures as a space, so the hole is gone too");

		// THE SAME PIXELS, which is the whole claim: a player reads the straight-quoted line.
		int[] curly = draw(g, regions, CURLY);
		int[] straight = draw(g, regions, STRAIGHT);
		int differ = 0;
		for (int i = 0; i < curly.length; i++) {
			if (curly[i] != straight[i]) {
				differ++;
			}
		}
		check(differ == 0, "the two draw the same pixels, to the pixel (" + differ + " differ)");
		int lit = 0;
		for (int i = 0; i < curly.length; i++) {
			if (curly[i] != 0) {
				lit++;
			}
		}
		check(lit > 0, "...and something was actually drawn (" + lit + " pixels)");

		System.out.println();
		System.out.println(fails == 0 ? (passes + " CHECKS, ALL PASS") : (fails + " FAILED of " + (passes + fails)));
		System.exit(fails == 0 ? 0 : 1);
	}

	/** One line drawn into a fresh buffer, for comparing two strings pixel for pixel. */
	static int[] draw(OverlayGraphics g, InteractiveRegions regions, String text) {
		int[] buffer = new int[W * H];
		Pix2D.bind(W, H, buffer);
		g.reset(W, H, regions, null, 0, 0);
		g.text(4, 20, text, 0xFFFFFF);
		Pix2D.bind(W, H, pixels);
		return buffer;
	}

	/** The string with its awkward characters named, so a failure says which one it was. */
	static String summary(String text) {
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c >= ' ' && c <= '~') {
				out.append(c);
			} else {
				out.append("\\u").append(Integer.toHexString(c));
			}
		}
		return out.toString();
	}
}
