package jagex2.client.plugin.builtin;

import jagex2.client.plugin.Actor;
import jagex2.client.plugin.ConfigList;
import jagex2.client.plugin.ConfigItem;
import jagex2.client.plugin.Overlay;
import jagex2.client.plugin.OverlayGraphics;
import jagex2.client.plugin.Plugin;
import jagex2.client.plugin.PluginDescriptor;
import jagex2.client.plugin.SceneLoc;
import jagex2.client.plugin.hub.Json;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * What to do next in the quest you are on, and what to bring.
 *
 * WHERE THE STEPS COME FROM, because it is the whole reason this is a few hundred lines rather
 * than a few years. The server's repository holds a sim per quest - tools/sim/quest_*_run.ts -
 * which plays that quest through on foot and is run every night by CI. Each one already records
 * the ordered steps, the npc or loc for each, where it stands, what you must be carrying, and the
 * value of the quest's own varp after each leg. tools/quest/ExportHelper.ts reads those and writes
 * quests.json: 83 quests, 678 stages, 4322 steps, every target resolved to a cache id.
 *
 * So the step list here is not a second, hand-written account of the quests that will drift from
 * the game. It is generated from the one the nightly proves: move a door and a sim fails.
 *
 * WHAT IT CANNOT DO, said plainly because the gap is visible in use. Progress is known to STAGE
 * granularity, not step granularity - the quest varp says "you have talked to Father Urhney", not
 * "you have taken three of the five steps since". So the panel shows the whole of the leg you are
 * on with the first step highlighted, rather than striking through steps one at a time. Finer
 * tracking would need the content to record sub-steps, which no part of the game does today.
 */
@PluginDescriptor(
	name = "Quest helper",
	description = "The step you are on, what to bring, and where the next thing is",
	key = "questhelper"
)
public final class QuestHelperPlugin extends Plugin {

	@ConfigItem(keyName = "highlight", name = "Highlight the target", description = "Mark the npc or object the step wants")
	public boolean highlight = true;

	@ConfigItem(keyName = "showPanel", name = "Show the step on screen", description = "A box in the viewport with the current step")
	public boolean showPanel = true;

	@ConfigItem(keyName = "showItems", name = "Warn about missing items", description = "List what this leg needs that you are not carrying")
	public boolean showItems = true;

	@ConfigItem(keyName = "highlightColour", name = "Highlight colour", description = "Hex colour for the marker, e.g. 00FF00")
	public String highlightColour = "00FF00";

	// ------------------------------------------------------------------ the table

	private static final class Step {
		String text = "";
		String kind = "note";

		/** What a player calls it, falling back to the server's handle when the cache had no name. */
		String shown() {
			if (this.label != null && !this.label.isEmpty()) {
				return this.label;
			}
			return this.target == null ? null : this.target.replace('_', ' ');
		}

		String target = null;
		String label = null;
		int id = -1;
		int[] at = null;
	}

	private static final class Need {
		String name = "";
		int id = -1;
		int n = 1;
		/** Worn, not merely carried - a different instruction, and the reason these two merge. */
		boolean wear;
	}

	private static final class Stage {
		int from;
		final List<String> headings = new ArrayList<>();
		final List<Step> steps = new ArrayList<>();
		final List<Need> items = new ArrayList<>();
	}

	private static final class Quest {
		String id = "";
		String name = "";
		int stageVarId = -1;
		boolean stageIsVarbit;
		int done;
		final List<Stage> stages = new ArrayList<>();
	}

	private final List<Quest> quests = new ArrayList<>();

	/** Null means "follow whichever quest is in progress"; set by the sidebar to pin one. */
	private String pinned = null;

	/**
	 * True while the page is showing the quest list instead of the tracked quest's steps.
	 *
	 * The auto-pick is a DEFAULT, not a decision: it takes the first started-and-unfinished quest
	 * in table order, which is alphabetical by id, so a player part-way through six quests was
	 * shown Fight Arena because "arena" sorts early. That is fine as an opening guess and no good
	 * as the only option, so the page browses.
	 */
	private boolean browsing = false;

	/** The quest list as the picker shows it: in progress first, then unstarted, then done. */
	private List<Quest> listed() {
		List<Quest> running = new ArrayList<>();
		List<Quest> fresh = new ArrayList<>();
		List<Quest> over = new ArrayList<>();
		for (Quest q : this.quests) {
			if (this.finished(q)) {
				over.add(q);
			} else if (this.started(q)) {
				running.add(q);
			} else {
				fresh.add(q);
			}
		}
		List<Quest> all = new ArrayList<>(running);
		all.addAll(fresh);
		all.addAll(over);
		return all;
	}

	private String statusOf(Quest q) {
		if (this.finished(q)) {
			return "complete";
		}
		if (!this.started(q)) {
			return "not started";
		}
		return "stage " + this.questValue(q) + " of " + q.done;
	}

	// ------------------------------------------------------------------ loading

	/**
	 * Read once, in startUp, because a handler must not do IO - "whatever yours does, the frame
	 * waits for it". 794KB through the client's own small Json parser measured 12-49ms here, which
	 * is a one-off cost at the point a player switches the plugin on.
	 */
	private void load() {
		this.quests.clear();
		try (InputStream in = QuestHelperPlugin.class.getResourceAsStream("/deathplateau/quests.json")) {
			if (in == null) {
				return;
			}
			ByteArrayOutputStream buf = new ByteArrayOutputStream();
			byte[] chunk = new byte[16384];
			for (int n = in.read(chunk); n > 0; n = in.read(chunk)) {
				buf.write(chunk, 0, n);
			}
			Object root = Json.parse(new String(buf.toByteArray(), "UTF-8"));
			if (!(root instanceof Map)) {
				return;
			}
			List<?> list = Json.array((Map<?, ?>) root, "quests");
			if (list == null) {
				return;
			}
			for (Object entry : list) {
				if (entry instanceof Map) {
					this.quests.add(quest((Map<?, ?>) entry));
				}
			}
		} catch (Exception e) {
			// A malformed table is a plugin with nothing to show, not a client that falls over.
			this.quests.clear();
		}
	}

	private static Quest quest(Map<?, ?> o) {
		Quest q = new Quest();
		q.id = Json.string(o, "id", "");
		q.name = Json.string(o, "name", q.id);
		q.stageVarId = Json.integer(o, "stageVarId", -1);
		q.stageIsVarbit = "true".equals(String.valueOf(o.get("stageIsVarbit")));
		q.done = Json.integer(o, "done", 0);
		List<?> stages = Json.array(o, "stages");
		if (stages != null) {
			for (Object s : stages) {
				if (s instanceof Map) {
					q.stages.add(stage((Map<?, ?>) s));
				}
			}
		}
		return q;
	}

	private static Stage stage(Map<?, ?> o) {
		Stage st = new Stage();
		st.from = Json.integer(o, "from", 0);
		List<?> heads = Json.array(o, "headings");
		if (heads != null) {
			for (Object h : heads) {
				st.headings.add(String.valueOf(h));
			}
		}
		List<?> steps = Json.array(o, "steps");
		if (steps != null) {
			for (Object s : steps) {
				if (!(s instanceof Map)) {
					continue;
				}
				Map<?, ?> m = (Map<?, ?>) s;
				Step step = new Step();
				step.text = Json.string(m, "text", "");
				step.kind = Json.string(m, "kind", "note");
				step.target = m.containsKey("target") ? Json.string(m, "target", null) : null;
				step.id = Json.integer(m, "id", -1);
				step.label = m.containsKey("label") ? Json.string(m, "label", null) : null;
				List<?> at = Json.array(m, "at");
				if (at != null && at.size() >= 2) {
					step.at = new int[] { num(at.get(0)), num(at.get(1)), at.size() > 2 ? num(at.get(2)) : 0 };
				}
				st.steps.add(step);
			}
		}
		// ONE ROW PER ITEM, even though the table lists carrying and wearing separately. An item
		// you must WEAR is also an item you must have, so the Ghostspeak amulet is in both lists
		// and appending them gave the panel the same row twice. Wearing is the stronger claim, so
		// it wins the row and the detail column says which.
		for (String key : new String[] { "items", "worn" }) {
			List<?> needs = Json.array(o, key);
			if (needs == null) {
				continue;
			}
			boolean wearing = "worn".equals(key);
			for (Object n : needs) {
				if (!(n instanceof Map)) {
					continue;
				}
				Map<?, ?> m = (Map<?, ?>) n;
				int id = Json.integer(m, "id", -1);
				String name = Json.string(m, "name", "");
				Need seen = null;
				for (Need have : st.items) {
					if (id >= 0 ? have.id == id : have.name.equals(name)) {
						seen = have;
						break;
					}
				}
				if (seen != null) {
					seen.wear = seen.wear || wearing;
					continue;
				}
				Need need = new Need();
				need.name = name;
				need.id = id;
				need.n = Json.integer(m, "n", 1);
				need.wear = wearing;
				st.items.add(need);
			}
		}
		return st;
	}

	private static int num(Object o) {
		try {
			return (int) Double.parseDouble(String.valueOf(o));
		} catch (Exception e) {
			return 0;
		}
	}

	// ------------------------------------------------------------------ where the player is

	/**
	 * Named questValue and not progress: ConfigList declares `progress(int)` for a row's bar, and
	 * inside the anonymous list below that name wins, so `questValue(q)` resolved to the row method
	 * and would not compile against a Quest.
	 */
	private int questValue(Quest q) {
		if (q.stageVarId < 0) {
			return 0;
		}
		return q.stageIsVarbit ? ctx.getVarbit(q.stageVarId) : ctx.getVarp(q.stageVarId);
	}

	private boolean started(Quest q) {
		return this.questValue(q) > 0;
	}

	private boolean finished(Quest q) {
		return q.done > 0 && this.questValue(q) >= q.done;
	}

	/**
	 * The quest being followed: the pinned one, else the first that is started and not finished.
	 *
	 * "First" is the table's order, which is alphabetical by id and so arbitrary. It is only a
	 * default - the sidebar pins one - but a player part-way through four quests should not have
	 * the panel flicker between them, so it has to be a stable choice rather than, say, the
	 * nearest.
	 */
	private Quest tracked() {
		if (this.pinned != null) {
			for (Quest q : this.quests) {
				if (q.id.equals(this.pinned)) {
					return q;
				}
			}
		}
		for (Quest q : this.quests) {
			if (this.started(q) && !this.finished(q)) {
				return q;
			}
		}
		return null;
	}

	/** The leg of the quest the varp says you are on: the last stage whose `from` you have passed. */
	private Stage stageNow(Quest q) {
		int value = this.questValue(q);
		Stage best = null;
		for (Stage st : q.stages) {
			if (st.from <= value && (best == null || st.from >= best.from)) {
				best = st;
			}
		}
		return best;
	}

	/** The first step of the current leg that names something to go and do. */
	private Step leadStep(Stage st) {
		if (st == null) {
			return null;
		}
		for (Step s : st.steps) {
			if (!"note".equals(s.kind)) {
				return s;
			}
		}
		return st.steps.isEmpty() ? null : st.steps.get(0);
	}

	private List<Need> missing(Stage st) {
		List<Need> out = new ArrayList<>();
		if (st == null) {
			return out;
		}
		for (Need n : st.items) {
			if (n.id >= 0 && ctx.countObj(n.id) < n.n) {
				out.add(n);
			}
		}
		return out;
	}

	private int colour() {
		try {
			int c = (int) Long.parseLong(this.highlightColour.trim().replace("#", ""), 16);
			return c == 0 ? 0x00FF00 : c & 0xFFFFFF;
		} catch (Exception e) {
			return 0x00FF00;
		}
	}

	// ------------------------------------------------------------------ the screen panel

	private final Overlay panel = new Overlay() {

		public int layer() {
			return Overlay.LAYER_SCREEN;
		}

		public void render(OverlayGraphics g) {
			if (!showPanel || !ctx.isLoggedIn()) {
				return;
			}
			Quest q = tracked();
			if (q == null) {
				return;
			}
			Stage st = stageNow(q);
			Step lead = leadStep(st);
			List<String> lines = new ArrayList<>();
			if (st != null && !st.headings.isEmpty()) {
				lines.add("@whi@" + wrapped(st.headings.get(0)));
			}
			lines.add(lead == null ? "No step recorded for this stage." : wrapped(lead.text));
			if (lead != null && lead.shown() != null) {
				lines.add("@gre@" + lead.shown());
			}
			if (showItems) {
				for (Need n : missing(st)) {
					lines.add("@red@need " + pretty(n.name) + (n.n > 1 ? " x" + n.n : ""));
				}
			}
			g.setFont(OverlayGraphics.FONT_SMALL).panel(6, 60, q.name, lines.toArray(new String[0]), 0xFFB000, 0xFFFFFF);
		}
	};

	/** One line of a step, cut rather than allowed to run off the panel. */
	private static String wrapped(String text) {
		String s = text == null ? "" : text.trim();
		return s.length() <= 58 ? s : s.substring(0, 56) + "..";
	}

	private static String pretty(String debugname) {
		return debugname == null ? "" : debugname.replace('_', ' ');
	}

	// ------------------------------------------------------------------ the world marker

	private final Overlay marker = new Overlay() {

		public int layer() {
			return Overlay.LAYER_SCENE;
		}

		public void render(OverlayGraphics g) {
			if (!highlight || !ctx.isLoggedIn()) {
				return;
			}
			Quest q = tracked();
			if (q == null) {
				return;
			}
			Step lead = leadStep(stageNow(q));
			if (lead == null || lead.id < 0) {
				return;
			}
			int c = colour();

			// An npc walks, so it is found by id in the scene rather than by the tile the manifest
			// recorded - that tile is where the sim met it, not where it is standing now.
			if ("talk".equals(lead.kind)) {
				for (Actor a : ctx.getNpcs()) {
					if (a.npcId == lead.id) {
						mark(g, a.sceneTileX, a.sceneTileZ, c, a.name);
						return;
					}
				}
			} else if ("loc".equals(lead.kind)) {
				List<SceneLoc> found = ctx.findLocs(new int[] { lead.id });
				if (found != null) {
					for (SceneLoc l : found) {
						if (l.level == ctx.getPlane()) {
							mark(g, l.sceneTileX, l.sceneTileZ, c, ctx.getLocName(l.id));
							return;
						}
					}
				}
			}

			// Not in the scene: mark the recorded tile if it is loaded, so the arrow still points
			// the right way as you walk in. Off the edge of the scene there is nothing to project
			// onto, and a marker pinned to the screen edge would be a guess at a direction.
			if (lead.at != null && ctx.isInScene(lead.at[0] - ctx.sceneToWorldX(0), lead.at[1] - ctx.sceneToWorldZ(0))) {
				mark(g, lead.at[0] - ctx.sceneToWorldX(0), lead.at[1] - ctx.sceneToWorldZ(0), c, pretty(lead.target));
			}
		}
	};

	/**
	 * A box on a tile with its name over it.
	 *
	 * A BOX AND NOT AN OUTLINED TILE. A tile in perspective is a quadrilateral whose four edges
	 * run at four angles, and OverlayGraphics draws horizontal and vertical lines only, so there
	 * is no honest way to trace one from here. The client's own npc indicators do it with
	 * concentric inset rings inside the renderer; a plugin gets the projected centre, so a marker
	 * centred on it is what can actually be drawn rather than a quad faked out of four hlines.
	 */
	private void mark(OverlayGraphics g, int sceneTileX, int sceneTileZ, int colour, String label) {
		if (!ctx.projectTile(sceneTileX, sceneTileZ, 0)) {
			return;
		}
		int x = ctx.getProjectedX();
		int y = ctx.getProjectedY();
		g.fillAlpha(x - 14, y - 14, 28, 28, colour, 48);
		g.box(x - 14, y - 14, 28, 28, colour);
		if (label != null && !label.isEmpty()) {
			g.setFont(OverlayGraphics.FONT_SMALL).textCentredOutlined(x, y - 18, label, colour);
		}
	}

	// ------------------------------------------------------------------ the sidebar page

	/**
	 * GUARDED, as XpTrackerPlugin's note explains: addPanel arrived in a later client than a jar
	 * may land on, and an unresolved call throws NoSuchMethodError, which the manager answers by
	 * switching the whole plugin off - losing the overlays too, over a page that client could not
	 * have drawn. Throwable rather than Exception, because a missing method is an Error.
	 */
	private void addRailPage() {
		try {
			this.addPanel("Quest helper", "list", new ConfigList() {

				/** Browsing shows every quest; otherwise the tracked quest's leg, under a header. */
				private boolean picking() {
					return browsing || tracked() == null;
				}

				public int size() {
					if (picking()) {
						return listed().size();
					}
					Quest q = tracked();
					Stage st = stageNow(q);
					return 1 + (st == null ? 0 : st.steps.size() + st.items.size());
				}

				public String label(int index) {
					if (picking()) {
						List<Quest> all = listed();
						return index >= 0 && index < all.size() ? all.get(index).name : "";
					}
					Quest q = tracked();
					if (index == 0) {
						return q.name;
					}
					Stage st = stageNow(q);
					if (st == null) {
						return "";
					}
					int i = index - 1;
					if (i < st.steps.size()) {
						return wrapped(st.steps.get(i).text);
					}
					return pretty(st.items.get(i - st.steps.size()).name);
				}

				public String detail(int index) {
					if (picking()) {
						List<Quest> all = listed();
						return index >= 0 && index < all.size() ? statusOf(all.get(index)) : null;
					}
					Quest q = tracked();
					Stage st = stageNow(q);
					if (index == 0) {
						return statusOf(q) + (st != null && !st.headings.isEmpty() ? " - " + st.headings.get(0) : "");
					}
					if (st == null) {
						return null;
					}
					int i = index - 1;
					if (i < st.steps.size()) {
						return st.steps.get(i).shown();
					}
					return st.items.get(i - st.steps.size()).wear ? "to wear" : "to carry";
				}

				public String value(int index) {
					if (picking()) {
						return null;
					}
					Quest q = tracked();
					Stage st = stageNow(q);
					if (index == 0 || st == null) {
						return null;
					}
					int i = index - 1;
					if (i < st.steps.size()) {
						return null;
					}
					Need n = st.items.get(i - st.steps.size());
					return (n.id < 0 ? 0 : ctx.countObj(n.id)) + "/" + n.n;
				}

				public int progress(int index) {
					return -1;
				}

				/** The button on a row: pick a quest while browsing, change it while tracking. */
				public String action(int index) {
					if (picking()) {
						List<Quest> all = listed();
						if (index < 0 || index >= all.size()) {
							return null;
						}
						Quest q = all.get(index);
						return q.id.equals(pinned) ? "Tracking" : "Track";
					}
					return index == 0 ? "Change" : null;
				}

				public void onAction(int index) {
					if (picking()) {
						List<Quest> all = listed();
						if (index >= 0 && index < all.size()) {
							pinned = all.get(index).id;
							browsing = false;
						}
						return;
					}
					if (index == 0) {
						browsing = true;
					}
				}

				public String emptyMessage() {
					if (quests.isEmpty()) {
						return "No step table loaded - quests.json is missing from the client.";
					}
					return "No quests in the table.";
				}
			});
		} catch (Throwable ignored) {
			// An older client: the overlays and settings still work.
		}
	}

	protected void startUp() {
		this.load();
		this.addOverlay(this.panel);
		this.addOverlay(this.marker);
		this.addRailPage();
	}

	protected void shutDown() {
		this.quests.clear();
		this.pinned = null;
		this.browsing = false;
	}
}
