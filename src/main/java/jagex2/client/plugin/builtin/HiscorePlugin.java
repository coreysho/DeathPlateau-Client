package jagex2.client.plugin.builtin;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import jagex2.client.plugin.ConfigItem;
import jagex2.client.plugin.ConfigList;
import jagex2.client.plugin.Plugin;
import jagex2.client.plugin.PluginDescriptor;
import jagex2.client.plugin.Sprite;
import jagex2.client.plugin.Subscribe;
import jagex2.client.plugin.event.PlayerMenuOptionClicked;
import jagex2.client.plugin.hub.Json;

/**
 * Somebody's hiscores, in the sidebar: RuneLite's hiscore panel, reading this game's own board.
 *
 * TWO WAYS IN, AS RUNELITE HAS. Type a name in the box at the top of the page and press Enter, or
 * right-click a player - standing in the world, a name in the chatbox, one in the friends list -
 * and choose Lookup, which fills the box, looks them up and opens the page.
 *
 * WHAT THE WEBSITE SAYS, AND NOTHING ELSE. The page is /hiscores/lookup on the website, which is
 * built out of the same functions as the player page there (DeathPlateau-Web), so the panel and the
 * site cannot disagree: the levels and ranks in stat-tab order, combat, total, the counted figures,
 * boss kills, the collection log, when they joined and when they were last seen. A figure added to
 * the player page arrives here when the site sends it.
 *
 * OFF THE GAME THREAD. The request goes on a thread of its own and hands back one finished answer;
 * the page reads whatever answer is current, so a slow site makes the page say "Looking up..." for
 * longer and never makes the game wait. A second lookup before the first answers simply wins: each
 * request knows which lookup it was for and an answer for an older one is thrown away.
 *
 * THE RANKS ARE WITHIN THE PLAYER'S OWN EXPERIENCE MODE, as on the site, and the page says which
 * mode that is - "rank 3" on a 10x account and on a 1x account are different claims.
 */
@PluginDescriptor(
	name = "Hiscores",
	description = "Look players up on the hiscores, from the sidebar or by right-clicking them",
	key = "hiscores",
	enabledByDefault = true,
	apiLevel = 14
)
public final class HiscorePlugin extends Plugin {

	static final String LOOKUP = "Lookup";

	@ConfigItem(keyName = "menuOption", name = "Lookup on players' menus",
		description = "Right-click a player, or their name in chat or your friends list, to look them up.")
	public boolean menuOption = true;

	/** Longest name the site will take; the game's own limit is twelve. */
	static final int MAX_NAME = 20;

	/** Requests are numbered so a late answer to an earlier one can be recognised and dropped. */
	private volatile int asked;

	/** What the page shows: null before any lookup. Replaced whole, never edited. */
	private volatile Answer answer;

	/** What the box should hold - the name last looked up. */
	private volatile String typed = "";

	@Override
	protected void startUp() {
		this.applyMenuOption();
		this.addPanel("Hiscores", "trophy", new ConfigList() {

			public int size() {
				Answer a = HiscorePlugin.this.answer;
				return a == null || a.player == null ? 0 : a.player.rows().size();
			}

			public String label(int index) {
				Line row = this.row(index);
				return row == null ? null : row.label;
			}

			public String detail(int index) {
				Line row = this.row(index);
				return row == null ? null : row.detail;
			}

			public String value(int index) {
				Line row = this.row(index);
				return row == null ? null : row.value;
			}

			public Sprite[] icons(int index) {
				Line row = this.row(index);
				if (row == null || !row.skills || HiscorePlugin.this.ctx == null) {
					return null;
				}
				Player p = HiscorePlugin.this.answer.player;
				Sprite[] out = new Sprite[p.skills.size()];
				for (int i = 0; i < out.length; i++) {
					out[i] = HiscorePlugin.this.ctx.getSkillIcon(p.skills.get(i).stat);
				}
				return out;
			}

			public String[] iconCaptions(int index) {
				Line row = this.row(index);
				if (row == null || !row.skills) {
					return null;
				}
				List<Skill> skills = HiscorePlugin.this.answer.player.skills;
				String[] out = new String[skills.size()];
				for (int i = 0; i < out.length; i++) {
					out[i] = Integer.toString(skills.get(i).level);
				}
				return out;
			}

			public String[] iconTips(int index) {
				Line row = this.row(index);
				if (row == null || !row.skills) {
					return null;
				}
				List<Skill> skills = HiscorePlugin.this.answer.player.skills;
				String[] out = new String[skills.size()];
				for (int i = 0; i < out.length; i++) {
					out[i] = skills.get(i).tip();
				}
				return out;
			}

			public boolean removable(int index) {
				return false;
			}

			public String emptyMessage() {
				Answer a = HiscorePlugin.this.answer;
				if (a == null) {
					return "Type a name above and press Enter, or right-click a player and choose "
						+ LOOKUP + ".";
				}
				return a.message;
			}

			public String searchHint() {
				return "Look up a player";
			}

			public String searchText() {
				return HiscorePlugin.this.typed;
			}

			public void onSearch(String text) {
				HiscorePlugin.this.lookup(text);
			}

			private Line row(int index) {
				Answer a = HiscorePlugin.this.answer;
				if (a == null || a.player == null) {
					return null;
				}
				List<Line> rows = a.player.rows();
				return index >= 0 && index < rows.size() ? rows.get(index) : null;
			}
		});
	}

	@Override
	protected void shutDown() {
		this.asked++;
	}

	/** What the menus were last given, so the setting is applied when it changes and only then. */
	private boolean optionApplied;

	/** The setting, applied: the row is on the menus only while it is switched on. */
	private void applyMenuOption() {
		if (this.menuOption) {
			this.addPlayerMenuOption(LOOKUP);
		} else {
			this.removePlayerMenuOption(LOOKUP);
		}
		this.optionApplied = this.menuOption;
	}

	@Subscribe
	public void onPlayerMenuOptionClicked(PlayerMenuOptionClicked event) {
		if (!LOOKUP.equals(event.option)) {
			return;
		}
		this.lookup(event.playerName);
		this.openPanel();
	}

	/**
	 * A switch flipped on the settings page is written straight into the field, and there is no
	 * event for it - so the menus are brought into line with it once a tick, which is as soon as
	 * anybody could right-click again anyway.
	 */
	@Subscribe
	public void onClientTick(jagex2.client.plugin.event.ClientTick tick) {
		if (this.optionApplied != this.menuOption) {
			this.applyMenuOption();
		}
	}

	// ------------------------------------------------------------------ looking somebody up

	/**
	 * Starts a lookup. The page says "Looking up..." at once and fills in when the site answers.
	 *
	 * The name is cleaned as the game would - trimmed, underscores read as spaces, cut at the
	 * site's limit - and an empty one is ignored rather than sent.
	 */
	void lookup(String name) {
		final String who = clean(name);
		if (who.length() == 0) {
			return;
		}
		this.typed = who;
		final int ticket = ++this.asked;
		this.answer = Answer.waiting(who);
		final String site = this.ctx == null ? "" : this.ctx.getSiteUrl();
		Thread fetch = new Thread(new Runnable() {

			public void run() {
				Answer got = fetch(site, who);
				if (ticket == HiscorePlugin.this.asked) {
					HiscorePlugin.this.answer = got;
				}
			}
		}, "hiscore-lookup");
		fetch.setDaemon(true);
		fetch.start();
	}

	static String clean(String name) {
		if (name == null) {
			return "";
		}
		String out = name.replace('_', ' ').replace(' ', ' ').trim();
		while (out.indexOf("  ") >= 0) {
			out = out.replace("  ", " ");
		}
		return out.length() > MAX_NAME ? out.substring(0, MAX_NAME) : out;
	}

	/** Asks the site. Never throws: every failure comes back as an answer that says what it was. */
	static Answer fetch(String site, String who) {
		HttpURLConnection connection = null;
		try {
			URL url = new URL(site + "/hiscores/lookup?name=" + URLEncoder.encode(who, "UTF-8").replace("+", "%20"));
			connection = (HttpURLConnection) url.openConnection();
			connection.setConnectTimeout(6000);
			connection.setReadTimeout(8000);
			connection.setRequestProperty("User-Agent", "DeathPlateau-Client");
			connection.setRequestProperty("Accept", "application/json");
			int status = connection.getResponseCode();
			InputStream in = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
			String body = in == null ? "" : read(in);
			return answer(who, status, body);
		} catch (Throwable error) {
			return Answer.failed(who, "Could not reach the hiscores. Check your connection and try again.");
		} finally {
			if (connection != null) {
				connection.disconnect();
			}
		}
	}

	private static String read(InputStream in) throws java.io.IOException {
		try {
			java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
			byte[] buffer = new byte[4096];
			int n;
			while ((n = in.read(buffer)) > 0 && out.size() < 1 << 20) {
				out.write(buffer, 0, n);
			}
			return new String(out.toByteArray(), "UTF-8");
		} finally {
			in.close();
		}
	}

	/** The site's reply, read. Split from fetch so the test can feed it a body without a site. */
	static Answer answer(String who, int status, String body) {
		Object parsed;
		try {
			parsed = Json.parse(body);
		} catch (Throwable error) {
			parsed = null;
		}
		Map<?, ?> json = parsed instanceof Map ? (Map<?, ?>) parsed : null;
		if (status == 404) {
			return Answer.failed(who, Json.string(json, "error", "Nobody is called " + who + "."));
		}
		if (status != 200 || json == null) {
			return Answer.failed(who, Json.string(json, "error",
				"The hiscores could not be read right now. Try again in a minute."));
		}
		return Answer.found(who, Player.read(json));
	}

	// ------------------------------------------------------------------ what came back

	/** The page's state: waiting, failed with a sentence, or a player. */
	static final class Answer {

		final String name;
		final String message;
		final Player player;

		private Answer(String name, String message, Player player) {
			this.name = name;
			this.message = message;
			this.player = player;
		}

		static Answer waiting(String name) {
			return new Answer(name, "Looking up " + name + "...", null);
		}

		static Answer failed(String name, String message) {
			return new Answer(name, message, null);
		}

		static Answer found(String name, Player player) {
			return new Answer(name, "", player);
		}
	}

	/** One skill: its stat index for the icon, and the three numbers. */
	static final class Skill {

		final String name;
		final int stat;
		final int level;
		final long xp;
		final long rank;

		Skill(String name, int stat, int level, long xp, long rank) {
			this.name = name;
			this.stat = stat;
			this.level = level;
			this.xp = xp;
			this.rank = rank;
		}

		/** "Attack - level 87, 3,972,294 xp, rank 2", or "unranked" for a skill nobody has. */
		String tip() {
			return this.name + " - level " + this.level + ", " + LootTracker.commas(this.xp) + " xp, "
				+ (this.rank > 0 ? "rank " + LootTracker.commas(this.rank) : "unranked");
		}
	}

	/** One line of the page. */
	static final class Line {

		final String label;
		final String detail;
		final String value;
		final boolean skills;

		Line(String label, String detail, String value, boolean skills) {
			this.label = label;
			this.detail = detail;
			this.value = value;
			this.skills = skills;
		}
	}

	/** Everything the site said about one player, already laid out as rows. */
	static final class Player {

		final String name;
		final List<Skill> skills;
		private final List<Line> rows;

		private Player(String name, List<Skill> skills, List<Line> rows) {
			this.name = name;
			this.skills = skills;
			this.rows = rows;
		}

		List<Line> rows() {
			return this.rows;
		}

		/**
		 * The site's skill slugs in PlayerStat order - the order the client's own stat icons are
		 * kept in (Client.SKILL_ICON_INDEX) and the order the hiscore types are numbered in.
		 */
		static final String[] STATS = {
			"attack", "defence", "strength", "hitpoints", "ranged", "prayer", "magic",
			"cooking", "woodcutting", "fletching", "fishing", "firemaking", "crafting", "smithing",
			"mining", "herblore", "agility", "thieving", "slayer", "farming", "runecraft",
			"construction", "hunter"
		};

		static int statOf(String slug) {
			for (int i = 0; i < STATS.length; i++) {
				if (STATS[i].equals(slug)) {
					return i;
				}
			}
			return -1;
		}

		static Player read(Map<?, ?> json) {
			String name = Json.string(json, "name", "");
			int rate = Json.integer(json, "xpRate", 1);

			List<Skill> skills = new ArrayList<Skill>();
			List<?> list = Json.array(json, "skills");
			for (int i = 0; i < list.size(); i++) {
				if (!(list.get(i) instanceof Map)) {
					continue;
				}
				Map<?, ?> s = (Map<?, ?>) list.get(i);
				int stat = statOf(Json.string(s, "slug", ""));
				if (stat < 0) {
					continue;
				}
				skills.add(new Skill(Json.string(s, "name", STATS[stat]), stat, Json.integer(s, "level", 1),
					number(s, "xp"), number(s, "rank")));
			}

			List<Line> rows = new ArrayList<Line>();
			// THE HEADING: who, their combat and mode, and whether they are about.
			StringBuilder about = new StringBuilder();
			about.append("Combat ").append(Json.integer(json, "combat", 3)).append(", ").append(rate)
				.append("x account");
			if (Boolean.TRUE.equals(json.get("online"))) {
				about.append(". On right now");
			} else if (Json.string(json, "lastSeen", null) != null) {
				about.append(". Last seen ").append(Json.string(json, "lastSeen", ""));
			}
			rows.add(new Line(name, about.toString(), "", false));

			// THE SKILLS, as the stat tab draws them: a block of icons with the level beside each,
			// and the experience and rank on hover - RuneLite's grid, in this game's own art.
			if (!skills.isEmpty()) {
				rows.add(new Line("Skills", "Hover a skill for its experience and rank.", "", true));
			}

			Object overall = json.get("overall");
			if (overall instanceof Map) {
				Map<?, ?> o = (Map<?, ?>) overall;
				long rank = number(o, "rank");
				rows.add(new Line("Total level", LootTracker.commas(number(o, "xp")) + " xp"
					+ (rank > 0 ? ", rank " + LootTracker.commas(rank) : ""),
					LootTracker.commas(number(o, "level")), false));
			} else {
				rows.add(new Line("Total level", "Not on the hiscores yet - they appear after a logout.",
					"", false));
			}

			List<?> tracked = Json.array(json, "tracked");
			for (int i = 0; i < tracked.size(); i++) {
				if (!(tracked.get(i) instanceof Map)) {
					continue;
				}
				Map<?, ?> t = (Map<?, ?>) tracked.get(i);
				long value = number(t, "value");
				boolean minutes = Boolean.TRUE.equals(t.get("minutes"));
				rows.add(new Line(Json.string(t, "label", ""), rankText(number(t, "rank")),
					minutes ? hours(value) : LootTracker.commas(value), false));
			}

			Object log = json.get("collectionLog");
			if (log instanceof Map) {
				Map<?, ?> c = (Map<?, ?>) log;
				long total = number(c, "total");
				rows.add(new Line("Collection log", "", LootTracker.commas(number(c, "obtained"))
					+ (total > 0 ? " / " + LootTracker.commas(total) : ""), false));
			}

			List<?> bosses = Json.array(json, "bosses");
			for (int i = 0; i < bosses.size(); i++) {
				if (!(bosses.get(i) instanceof Map)) {
					continue;
				}
				Map<?, ?> b = (Map<?, ?>) bosses.get(i);
				rows.add(new Line(Json.string(b, "name", ""), rankText(number(b, "rank")),
					LootTracker.commas(number(b, "kills")), false));
			}

			long votes = number(json, "votes");
			if (votes > 0) {
				rows.add(new Line("Votes cast", "", LootTracker.commas(votes), false));
			}
			String joined = Json.string(json, "joined", null);
			if (joined != null) {
				rows.add(new Line("Joined", "", joined, false));
			}
			return new Player(name, Collections.unmodifiableList(skills), Collections.unmodifiableList(rows));
		}

		private static String rankText(long rank) {
			return rank > 0 ? "Rank " + LootTracker.commas(rank) : "";
		}

		/** "214 hours", "45 minutes", as the site says time played. */
		static String hours(long minutes) {
			if (minutes < 60) {
				return LootTracker.commas(minutes) + (minutes == 1 ? " minute" : " minutes");
			}
			long hours = minutes / 60;
			return LootTracker.commas(hours) + (hours == 1 ? " hour" : " hours");
		}

		/** A number as a long: JSON has one number type, and total experience outgrows an int. */
		static long number(Map<?, ?> object, String key) {
			Object value = object == null ? null : object.get(key);
			return value instanceof Double ? Math.round((Double) value) : 0L;
		}
	}
}
