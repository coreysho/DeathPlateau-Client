/*
 * Headless test for the config editors: colour and choices.
 *
 * WHAT THIS GUARDS. Every plugin's colour setting is six characters of text that might be
 * anything - a typo, a pasted "#", or an empty string from a release where the field did not
 * exist - and parseColour is called from render, per frame. An exception out of a draw turns the
 * plugin off, so "does not throw" is not a nicety here, it is the whole contract.
 *
 * The round trip matters just as much: the swatch shows parseColour(stored), and the picker writes
 * toHex(picked). If those two disagree anywhere, a player picks a colour and gets a different one
 * back the next time they open the panel.
 *
 * And the choices rule has one job that is easy to get wrong in the other direction: a value
 * stored by an older release, for a mode that no longer exists, must be KEPT. Silently rewriting
 * someone's settings file because the panel was opened is worse than showing them something odd.
 */
package jagex2.client.plugin.builtin;

import jagex2.client.plugin.PluginConfig;

public class ConfigTest {

	static int fails;

	static void check(boolean ok, String what) {
		System.out.println((ok ? "  ok   " : "FAIL   ") + what);
		if (!ok) {
			fails++;
		}
	}

	public static void main(String[] args) {
		System.out.println("1. a hex setting becoming a colour");
		colourTests();
		System.out.println();
		System.out.println("2. and back again");
		hexTests();
		System.out.println();
		System.out.println("3. the editors a setting asks for");
		editorTests();
		System.out.println();
		System.out.println("4. where the name tag sits");
		choiceUseTests();

		System.out.println();
		System.out.println(fails == 0 ? "ALL PASS" : fails + " FAILED");
		System.exit(fails == 0 ? 0 : 1);
	}

	// ---------------------------------------------------------------- 1

	static final int YELLOW = 0xFFFF00;

	static void colourTests() {
		check(PluginConfig.parseColour("00FF00") == 0x00FF00, "plain hex reads as itself");
		check(PluginConfig.parseColour("ff00ff") == 0xFF00FF, "lower case is fine");
		check(PluginConfig.parseColour("#00FF00") == 0x00FF00, "a pasted # is forgiven");
		check(PluginConfig.parseColour("  00FF00  ") == 0x00FF00, "and so is whitespace");
		check(PluginConfig.parseColour("000000") == 0x000000, "black is a colour, not a failure");

		// Everything a settings file can hold that is not a colour. None may throw, and all must
		// land on the one visible fallback rather than on black, which reads as "did not draw".
		String[] junk = { null, "", "   ", "#", "fff", "0000000", "GGGGGG", "00FF0", "00 FF 00",
			"0x00FF00", "-00FF0", "rgb(0,255,0)", "00FF00FF" };
		for (int i = 0; i < junk.length; i++) {
			boolean threw = false;
			int got = 0;
			try {
				got = PluginConfig.parseColour(junk[i]);
			} catch (Throwable broke) {
				threw = true;
			}
			check(!threw, "\"" + junk[i] + "\" does not throw out of a render");
			check(!threw && got == YELLOW,
				"...and falls back to the one visible colour (" + Integer.toHexString(got) + ")");
		}
	}

	// ---------------------------------------------------------------- 2

	static void hexTests() {
		check(PluginConfig.toHex(0x00FF00).equals("00FF00"), "a colour writes back as six characters");
		check(PluginConfig.toHex(0x000000).equals("000000"), "black keeps its leading zeroes");
		check(PluginConfig.toHex(0x0000FF).equals("0000FF"), "...and so does anything else short");
		// Swing hands back an RGB int with the alpha byte set, which must not reach the file.
		check(PluginConfig.toHex(0xFF00FF00).equals("00FF00"), "an alpha byte is dropped");
		check(PluginConfig.toHex(-1).equals("FFFFFF"), "and so is a fully opaque white's sign bit");

		// THE ROUND TRIP, which is what a player actually experiences: pick a colour, reopen the
		// panel, see the same one. Every value, not a sample, because the two functions have to
		// agree everywhere and this is cheap.
		int broken = -1;
		for (int rgb = 0; rgb <= 0xFFFFFF; rgb += 0x40) {
			if (PluginConfig.parseColour(PluginConfig.toHex(rgb)) != rgb) {
				broken = rgb;
				break;
			}
		}
		check(broken < 0, broken < 0 ? "every colour survives the round trip to hex and back"
			: "0x" + Integer.toHexString(broken) + " does not survive the round trip");

		// And the other direction, so a hand-typed value is not quietly reformatted.
		check(PluginConfig.toHex(PluginConfig.parseColour("A1B2C3")).equals("A1B2C3"),
			"a hand-typed colour comes back out as it went in");
	}

	// ---------------------------------------------------------------- 3

	static void editorTests() {
		String source = read("src/main/java/jagex2/client/plugin/ui/ConfigPanel.java");
		check(source.length() > 0, "ConfigPanel is readable");

		// The swatch must open a picker, and Cancel must leave the setting alone. A picker that
		// wrote on cancel would set every colour a player looked at to black.
		int colour = source.indexOf("private Component buildColourEditor");
		int colourEnd = colour < 0 ? -1 : source.indexOf("\n\t}", colour);
		String cbody = colour < 0 || colourEnd < 0 ? "" : source.substring(colour, colourEnd);
		check(cbody.length() > 0, "the colour editor is readable");
		check(cbody.indexOf("picked != null") >= 0,
			"a cancelled colour picker leaves the setting alone rather than writing black");
		check(cbody.indexOf("PluginConfig.toHex") >= 0,
			"...and a picked colour is written as hex, so the file format does not change");
		check(cbody.indexOf("item.colourValue()") >= 0,
			"...and the swatch shows what is stored, through the same parse the drawing uses");

		// The drop-down must keep a value it does not recognise.
		int choice = source.indexOf("private Component buildChoiceEditor");
		int choiceEnd = choice < 0 ? -1 : source.indexOf("\n\t}", choice);
		String hbody = choice < 0 || choiceEnd < 0 ? "" : source.substring(choice, choiceEnd);
		check(hbody.length() > 0, "the choice editor is readable");
		check(hbody.indexOf("options.contains(current)") >= 0 && hbody.indexOf("options.add(0, current)") >= 0,
			"a stored value that is no longer a choice is still offered, not silently rewritten");
		check(hbody.indexOf("equals(current)") >= 0,
			"...and selecting what is already selected queues no write");

		// A plugin that asks for an editor on the wrong type gets no editor rather than a broken
		// one. isColour and choices() both gate on isString.
		String config = read("src/main/java/jagex2/client/plugin/PluginConfig.java");
		int isColour = config.indexOf("public boolean isColour()");
		int isColourEnd = isColour < 0 ? -1 : config.indexOf("\n\t\t}", isColour);
		String ibody = isColour < 0 || isColourEnd < 0 ? "" : config.substring(isColour, isColourEnd);
		check(ibody.indexOf("isString()") >= 0,
			"a colour editor is only offered for a String setting");
		int choices = config.indexOf("public String[] choices()");
		int choicesEnd = choices < 0 ? -1 : config.indexOf("\n\t\t}", choices);
		String chbody = choices < 0 || choicesEnd < 0 ? "" : config.substring(choices, choicesEnd);
		check(chbody.indexOf("isString()") >= 0, "and so is a drop-down");
		// HANDED OUT AS A COPY. An annotation's array is cloned on every read, so a caller that
		// sorted or rewrote what it got would be rewriting nothing - confusingly, and only
		// sometimes. Checking the copy itself needs an Item, which needs a plugin and a store, so
		// the promise is read out of the source the way the others here are.
		check(chbody.indexOf(".clone()") >= 0,
			"a plugin is handed a copy of the choices, not the array behind them");

		// AND buildEditor HAS TO REACH BOTH. The two editors above can be perfectly correct and
		// never called: without the dispatch, a colour setting is a text box again and nothing in
		// their own bodies would say so.
		int build = source.indexOf("private Component buildEditor(");
		int buildEnd = build < 0 ? -1 : source.indexOf("\n\t}", build);
		String bbody = build < 0 || buildEnd < 0 ? "" : source.substring(build, buildEnd);
		check(bbody.length() > 0, "buildEditor is readable");
		check(bbody.indexOf("item.isColour()") >= 0 && bbody.indexOf("buildColourEditor") >= 0,
			"buildEditor sends a colour setting to the swatch");
		check(bbody.indexOf("choices.length > 0") >= 0 && bbody.indexOf("buildChoiceEditor") >= 0,
			"...and a choice setting to the drop-down");
		// Order matters: a colour setting with choices is a colour setting, and the swatch check
		// has to come first or the drop-down would swallow it.
		check(bbody.indexOf("buildColourEditor") < bbody.indexOf("buildChoiceEditor"),
			"...and asks about the colour first, so a colour with choices is still a swatch");

		// ONE IMPLEMENTATION OF THE COLOUR PARSE, FOR EVERY BUILT-IN. The swatch and the drawing
		// have to agree, and two copies of "is this six characters of hex" eventually would not.
		//
		// Stated across all of them rather than on the one that used to hold a delegate. There
		// was a MouseHighlightPlugin.parseColour that called straight through to PluginConfig's,
		// and one mutation guarding it; the delegate is gone, because an uncalled indirection is
		// a place for a second implementation to appear. This says the thing the delegate was
		// standing in for, for every plugin at once.
		String[] builtins = {
			"AntiDragPlugin", "BarrowsDoorsPlugin", "BoostsPlugin", "GroundItemsPlugin",
			"IdleNotifierPlugin", "MenuSwapperPlugin", "MouseHighlightPlugin",
			"NpcIndicatorsPlugin", "SkillsPlugin", "TileIndicatorsPlugin", "XpDropsPlugin"
		};
		for (int i = 0; i < builtins.length; i++) {
			String builtin = read("src/main/java/jagex2/client/plugin/builtin/"
				+ builtins[i] + ".java");
			check(builtin.length() > 0 && builtin.indexOf("Integer.parseInt(cleaned, 16)") < 0
					&& builtin.indexOf("static int parseColour(") < 0,
				builtins[i] + " has no colour parse of its own");
		}
		check(read("src/main/java/jagex2/client/plugin/PluginConfig.java")
				.indexOf("static int parseColour(") >= 0,
			"...because PluginConfig is the one that does, and the swatch reads the same one");
	}

	// ---------------------------------------------------------------- 4

	static void choiceUseTests() {
		check(NpcIndicatorsPlugin.tagHeight(NpcIndicatorsPlugin.AT_FEET, 1) == 0,
			"at feet means on the ground");
		check(NpcIndicatorsPlugin.tagHeight(NpcIndicatorsPlugin.ABOVE, 1) > 0,
			"above means above");
		check(NpcIndicatorsPlugin.tagHeight(NpcIndicatorsPlugin.ABOVE, 3)
				> NpcIndicatorsPlugin.tagHeight(NpcIndicatorsPlugin.ABOVE, 1),
			"a bigger npc's tag clears it by more");
		check(NpcIndicatorsPlugin.tagHeight(NpcIndicatorsPlugin.AT_FEET, 3) == 0,
			"...but at feet is at feet whatever the size");

		// A VALUE FROM A RELEASE THAT OFFERED SOMETHING ELSE. It must read as the default rather
		// than put the tag somewhere nobody chose - and must not throw.
		String[] unknown = { null, "", "   ", "above", "Beside", "0", "At Feet" };
		for (int i = 0; i < unknown.length; i++) {
			boolean threw = false;
			int got = 0;
			try {
				got = NpcIndicatorsPlugin.tagHeight(unknown[i], 1);
			} catch (Throwable broke) {
				threw = true;
			}
			check(!threw, "\"" + unknown[i] + "\" does not throw");
			check(!threw && got == NpcIndicatorsPlugin.tagHeight(NpcIndicatorsPlugin.ABOVE, 1),
				"...and falls back to the default position");
		}

		// The choices the panel offers are the constants the code compares against, so the two
		// cannot drift into a drop-down whose values do nothing.
		String plugin = read("src/main/java/jagex2/client/plugin/builtin/NpcIndicatorsPlugin.java");
		check(plugin.indexOf("choices = { ABOVE, AT_FEET }") >= 0,
			"the drop-down offers the same constants the code tests against");
	}

	static String read(String path) {
		try {
			return new String(java.nio.file.Files.readAllBytes(
				new java.io.File(System.getProperty("dp.root", "."), path).toPath()), "UTF-8");
		} catch (Throwable missing) {
			check(false, "cannot read " + path + " (" + missing + ")");
			return "";
		}
	}
}
