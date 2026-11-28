/*
 * Headless test for the hiscore panel.
 *
 * WHAT THIS GUARDS. The panel is meant to say what the website says about a player, read out of
 * /hiscores/lookup. Every way of getting that wrong still draws a page: a skill read into the wrong
 * icon (the site's order and the client's stat order are different lists), experience past two
 * billion wrapped negative by an int, a 404 shown as "the site is down" or a 503 as "nobody is
 * called that", minutes printed as a count. So the reading of the site's answer is a function with
 * no client and no network under it, and it is fed a body here.
 */
package jagex2.client.plugin.builtin;

import java.util.List;

public class HiscoreTest {

	static int fails;

	static void check(boolean ok, String what) {
		System.out.println((ok ? "  ok   " : "FAIL   ") + what);
		if (!ok) {
			fails++;
		}
	}

	/** What the site answers for a player, in the shape /hiscores/lookup sends. */
	static final String BODY = "{\"name\":\"Lord Fooby\",\"xpRate\":5,\"combat\":107,"
		+ "\"overall\":{\"level\":1601,\"xp\":3014887400,\"rank\":2},"
		+ "\"skills\":["
		+ "{\"slug\":\"attack\",\"name\":\"Attack\",\"level\":87,\"xp\":3972294,\"rank\":2},"
		+ "{\"slug\":\"hitpoints\",\"name\":\"Hitpoints\",\"level\":83,\"xp\":2673114,\"rank\":12},"
		+ "{\"slug\":\"mining\",\"name\":\"Mining\",\"level\":65,\"xp\":449428,\"rank\":null},"
		+ "{\"slug\":\"hunter\",\"name\":\"Hunter\",\"level\":50,\"xp\":101333,\"rank\":7},"
		+ "{\"slug\":\"sailing\",\"name\":\"Sailing\",\"level\":1,\"xp\":0,\"rank\":null}"
		+ "],"
		+ "\"tracked\":[{\"key\":\"quest\",\"label\":\"Quest points\",\"value\":214,\"rank\":3,\"minutes\":false},"
		+ "{\"key\":\"deaths\",\"label\":\"Deaths\",\"value\":40,\"rank\":null,\"minutes\":false},"
		+ "{\"key\":\"playtime\",\"label\":\"Time played\",\"value\":12900,\"rank\":5,\"minutes\":true}],"
		+ "\"bosses\":[{\"name\":\"Zulrah\",\"kills\":1300,\"rank\":1}],"
		+ "\"collectionLog\":{\"obtained\":120,\"total\":803},"
		+ "\"joined\":\"16 Nov 2026\",\"lastSeen\":\"27 Nov 2026\",\"online\":false,\"votes\":12,"
		+ "\"url\":\"/player?name=lord_fooby\"}";

	public static void main(String[] args) {
		System.out.println("1. the name");
		check(HiscorePlugin.clean("  lord_fooby ").equals("lord fooby"), "underscores read as spaces, trimmed");
		check(HiscorePlugin.clean("Lord   Fooby").equals("Lord Fooby"), "runs of spaces closed up");
		check(HiscorePlugin.clean(null).equals(""), "no name is no name");
		check(HiscorePlugin.clean("abcdefghijklmnopqrstuvwxyz").length() == HiscorePlugin.MAX_NAME, "cut at the site's limit");

		System.out.println();
		System.out.println("2. what the site said");
		HiscorePlugin.Answer nobody = HiscorePlugin.answer("Zezima", 404, "{\"error\":\"Nobody is called Zezima.\"}");
		check(nobody.player == null && nobody.message.equals("Nobody is called Zezima."), "404 is nobody, in the site's words");
		HiscorePlugin.Answer down = HiscorePlugin.answer("Zezima", 503, "{\"error\":\"The hiscores cannot be read right now. Try again in a minute.\"}");
		check(down.player == null && down.message.startsWith("The hiscores cannot be read"), "503 is try again, not nobody");
		HiscorePlugin.Answer junk = HiscorePlugin.answer("Zezima", 200, "<html>Cloudflare</html>");
		check(junk.player == null && junk.message.indexOf("could not be read") >= 0, "a page that is not JSON is a failure, said so");

		HiscorePlugin.Answer found = HiscorePlugin.answer("lord fooby", 200, BODY);
		check(found.player != null, "a 200 is a player");
		if (found.player == null) {
			finish();
			return;
		}
		HiscorePlugin.Player p = found.player;
		List<HiscorePlugin.Line> rows = p.rows();
		check(rows.get(0).label.equals("Lord Fooby"), "the heading is their name, as the site writes it");
		check(rows.get(0).detail.equals("Combat 107, 5x account. Last seen 27 Nov 2026"),
			"with combat, their mode and when they were last about");
		check(rows.get(1).skills, "then the skills block");

		check(p.skills.size() == 4, "a skill the client has no icon for is left out, not drawn as another");
		check(p.skills.get(0).name.equals("Attack") && p.skills.get(0).stat == 0, "Attack is stat 0");
		check(p.skills.get(1).stat == 3, "Hitpoints is stat 3, not the site's second");
		check(p.skills.get(2).stat == 14, "Mining is stat 14");
		check(p.skills.get(3).stat == 22, "Hunter is stat 22");
		check(p.skills.get(0).tip().equals("Attack - level 87, 3,972,294 xp, rank 2"), "a skill's hover");
		check(p.skills.get(2).tip().endsWith("unranked"), "a skill with no rank says so");

		HiscorePlugin.Line total = find(rows, "Total level");
		check(total != null && total.value.equals("1,601"), "the total level");
		check(total != null && total.detail.startsWith("3,014,887,400 xp"), "its experience past two billion, not wrapped");
		HiscorePlugin.Line quest = find(rows, "Quest points");
		check(quest != null && quest.value.equals("214") && quest.detail.equals("Rank 3"), "a counted figure and its rank");
		HiscorePlugin.Line deaths = find(rows, "Deaths");
		check(deaths != null && deaths.detail.equals(""), "deaths have no rank, as on the site");
		HiscorePlugin.Line played = find(rows, "Time played");
		check(played != null && played.value.equals("215 hours"), "minutes shown as hours");
		HiscorePlugin.Line zulrah = find(rows, "Zulrah");
		check(zulrah != null && zulrah.value.equals("1,300") && zulrah.detail.equals("Rank 1"), "a boss's kills and rank");
		HiscorePlugin.Line log = find(rows, "Collection log");
		check(log != null && log.value.equals("120 / 803"), "the collection log, out of everything");
		check(find(rows, "Votes cast") != null && find(rows, "Joined").value.equals("16 Nov 2026"), "votes and the join day");

		HiscorePlugin.Answer fresh = HiscorePlugin.answer("new", 200,
			"{\"name\":\"New\",\"xpRate\":1,\"combat\":3,\"overall\":null,\"skills\":[],\"tracked\":[],\"bosses\":[],"
				+ "\"online\":true,\"votes\":0}");
		check(fresh.player != null && fresh.player.rows().get(0).detail.endsWith("On right now"), "online, said so");
		check(find(fresh.player.rows(), "Total level").detail.startsWith("Not on the hiscores yet"),
			"an account with no hiscore rows yet is told when it will have some");
		check(find(fresh.player.rows(), "Skills") == null, "and has no empty skills block");

		check(HiscorePlugin.Answer.waiting("Bob").message.equals("Looking up Bob..."), "the page while it waits");
		finish();
	}

	static HiscorePlugin.Line find(List<HiscorePlugin.Line> rows, String label) {
		for (HiscorePlugin.Line row : rows) {
			if (row.label.equals(label)) {
				return row;
			}
		}
		return null;
	}

	static void finish() {
		System.out.println();
		if (fails > 0) {
			System.out.println(fails + " FAILED");
			System.exit(1);
		}
		System.out.println("all passed");
	}
}
