/*
 * Headless test for the Cannon overlay.
 *
 * WHAT THIS GUARDS. The countdown is two numbers the server sends and one the client counts, and
 * each way of getting it wrong still draws a believable clock:
 *
 *   THE BETWEEN-UPDATES COUNT. The server says how many ticks are left every five ticks. A clock
 *   that never counts between them jumps three seconds at a time; one that keeps counting after a
 *   new value arrives runs fast and reads a minute short by the end.
 *
 *   THE IDS. They are the content's, written here by hand. When the content is checked out
 *   beside this repo they are read back out of pack/varp.pack and cannon.constant; without it
 *   that part SKIPs, because a check that could not run must not read as one that passed.
 */
package jagex2.client.plugin.builtin;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class CannonTest {

	static int fails;

	static void check(boolean ok, String what) {
		System.out.println((ok ? "  ok   " : "FAIL   ") + what);
		if (!ok) {
			fails++;
		}
	}

	static void skip(String what) {
		System.out.println("  SKIP  " + what);
	}

	public static void main(String[] args) throws Exception {
		System.out.println("1. the time");
		check(CannonPlugin.time(2500).equals("25:00"), "2500 ticks is 25:00: " + CannonPlugin.time(2500));
		check(CannonPlugin.time(1).equals("0:00"), "a tick rounds down to 0:00: " + CannonPlugin.time(1));
		check(CannonPlugin.time(5).equals("0:03"), "five ticks is three seconds: " + CannonPlugin.time(5));
		check(CannonPlugin.time(1017).equals("10:10"), "1017 ticks is 10:10: " + CannonPlugin.time(1017));
		check(CannonPlugin.time(-4).equals("0:00"), "a late update never reads negative");

		System.out.println();
		System.out.println("2. the count between updates");
		CannonPlugin.Clock c = new CannonPlugin.Clock();
		c.tick(CannonPlugin.HUD_WORKING, 2500);
		check(c.remaining() == 2500, "the first value is shown as it came");
		for (int i = 0; i < 4; i++) {
			c.tick(CannonPlugin.HUD_WORKING, 2500);
		}
		check(c.remaining() == 2496, "four quiet ticks take four off: " + c.remaining());
		c.tick(CannonPlugin.HUD_WORKING, 2495);
		check(c.remaining() == 2495, "a new value replaces the count rather than adding to it: " + c.remaining());
		for (int i = 0; i < 3000; i++) {
			c.tick(CannonPlugin.HUD_WORKING, 2495);
		}
		check(c.remaining() == 0, "and stops at zero if the server goes quiet");
		int before = c.tick(CannonPlugin.HUD_BROKEN, 1000);
		check(before == CannonPlugin.HUD_WORKING && c.hud == CannonPlugin.HUD_BROKEN && c.remaining() == 1000,
			"a break is seen as working -> broken, with the 10 minutes");
		c.tick(CannonPlugin.HUD_WORKING, 2500);
		check(c.remaining() == 2500, "a repair is a fresh anchor");

		System.out.println();
		System.out.println("3. the panel");
		check(CannonPlugin.lines(CannonPlugin.HUD_NONE, 0, 0, true, true).isEmpty(), "no cannon, no panel");
		List<String> working = CannonPlugin.lines(CannonPlugin.HUD_WORKING, 1500, 23, true, true);
		check(working.size() == 2 && working.get(0).equals("Breaks in 15:00") && working.get(1).equals("Cannonballs: 23/30"),
			"working: " + working);
		List<String> broken = CannonPlugin.lines(CannonPlugin.HUD_BROKEN, 600, 30, true, true);
		check(broken.size() == 3 && broken.get(1).equals("Decays in 6:00"), "broken: " + broken);
		check(CannonPlugin.lines(CannonPlugin.HUD_BUILDING, 600, 0, true, false).isEmpty(),
			"a part-built cannon is hidden when asked");
		List<String> building = CannonPlugin.lines(CannonPlugin.HUD_BUILDING, 600, 9, true, true);
		check(building.size() == 2 && !building.get(building.size() - 1).startsWith("Cannonballs"),
			"a part-built cannon has no cannonballs line: " + building);
		check(CannonPlugin.lines(CannonPlugin.HUD_WORKING, 1500, 23, false, true).size() == 1, "ammo can be hidden");
		check(CannonPlugin.colour(CannonPlugin.HUD_WORKING, 501) == CannonPlugin.COLOUR_OK
			&& CannonPlugin.colour(CannonPlugin.HUD_WORKING, 500) == CannonPlugin.COLOUR_SOON
			&& CannonPlugin.colour(CannonPlugin.HUD_BROKEN, 900) == CannonPlugin.COLOUR_BROKEN,
			"white, yellow in the last five minutes, red when broken");

		System.out.println();
		System.out.println("4. the ids against the content");
		Path content = findContent();
		if (content == null) {
			skip("no content checkout beside this repo (../DeathPlateau-Content or ../content)");
		} else {
			String varps = new String(Files.readAllBytes(content.resolve("pack/varp.pack")), StandardCharsets.UTF_8).replace("\r", "");
			check(varps.contains("\n" + CannonPlugin.AMMO_VARP + "=rockthrower\n"), "rockthrower is varp " + CannonPlugin.AMMO_VARP);
			check(varps.contains("\n" + CannonPlugin.HUD_VARP + "=cannon_hud\n"), "cannon_hud is varp " + CannonPlugin.HUD_VARP);
			check(varps.contains("\n" + CannonPlugin.TICKS_VARP + "=cannon_hud_ticks\n"), "cannon_hud_ticks is varp " + CannonPlugin.TICKS_VARP);
			Path constants = content.resolve("scripts/quests/quest_mcannon/configs/cannon.constant");
			String k = new String(Files.readAllBytes(constants), StandardCharsets.UTF_8).replace("\r", "");
			check(k.contains("^cannon_hud_none = " + CannonPlugin.HUD_NONE + "\n")
				&& k.contains("^cannon_hud_building = " + CannonPlugin.HUD_BUILDING + "\n")
				&& k.contains("^cannon_hud_working = " + CannonPlugin.HUD_WORKING + "\n")
				&& k.contains("^cannon_hud_broken = " + CannonPlugin.HUD_BROKEN + "\n"),
				"the hud states match ^cannon_hud_*");
			check(k.contains("^cannon_warn_ticks = " + CannonPlugin.WARN_TICKS + "\n"), "the yellow starts where the server warns");
			String varpCfg = new String(Files.readAllBytes(content.resolve("scripts/quests/quest_mcannon/configs/quest_mcannon.varp")),
				StandardCharsets.UTF_8).replace("\r", "");
			for (String name : new String[] { "rockthrower", "cannon_hud", "cannon_hud_ticks" }) {
				int at = varpCfg.indexOf("[" + name + "]\n");
				int end = at < 0 ? -1 : varpCfg.indexOf("\n[", at + 1);
				String block = at < 0 ? "" : varpCfg.substring(at, end < 0 ? varpCfg.length() : end);
				check(block.contains("\ntransmit=yes"), name + " is transmitted, or it reads 0 here for ever");
			}
		}

		System.out.println();
		if (fails == 0) {
			System.out.println("all checks passed");
		} else {
			System.out.println(fails + " CHECK(S) FAILED");
		}
		System.exit(fails == 0 ? 0 : 1);
	}

	/** The content checkout beside this repo, or null. */
	static Path findContent() {
		Path root = Paths.get(System.getProperty("dp.root", ".")).toAbsolutePath().getParent();
		if (root == null) {
			return null;
		}
		for (String name : new String[] { "DeathPlateau-Content", "content" }) {
			Path p = root.resolve(name);
			if (Files.isRegularFile(p.resolve("pack/varp.pack"))) {
				return p;
			}
		}
		return null;
	}
}
