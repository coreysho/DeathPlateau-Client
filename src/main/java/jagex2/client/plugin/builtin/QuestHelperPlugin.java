package jagex2.client.plugin.builtin;

import jagex2.client.plugin.Actor;
import jagex2.client.plugin.ConfigList;
import jagex2.client.plugin.ConfigItem;
import jagex2.client.plugin.Overlay;
import jagex2.client.plugin.OverlayGraphics;
import jagex2.client.plugin.Plugin;
import jagex2.client.plugin.PluginDescriptor;
import jagex2.client.plugin.Subscribe;
import jagex2.client.plugin.event.GameTick;
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
 * quests.json: 106 quests, 892 stages, 5322 steps, every target resolved to a cache id.
 *
 * So the step list here is not a second, hand-written account of the quests that will drift from
 * the game. It is generated from the one the nightly proves: move a door and a sim fails.
 *
 * WHAT IT CANNOT DO, said plainly because the gap is visible in use. Progress is known to STAGE
 * granularity, not step granularity - the quest varp says "you have talked to Father Urhney", not
 * "you have taken three of the five steps since". So the sidebar lists the whole of the leg you
 * are on rather than striking steps out one at a time, and which of those steps leads is worked
 * out from the world rather than read off the varp: what is standing in front of you ({@link
 * #spot}) and what the quest has put in your bag ({@link #reached}). Both are inferences. A step
 * that leaves no trace - reading a book you keep, pulling a lever - cannot be seen to have
 * happened, so the panel can sit on one until the next thing it CAN see arrives. Finer tracking
 * would need the content to record sub-steps, which no part of the game does today.
 *
 * AND THE ITEM LIST IS TWO LISTS. What a leg says you must hold is partly a bank trip and partly
 * what the quest hands you while you are in it; only the first is something to go and fetch, and
 * the table marks which is which. See {@link Need#bring}.
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
		/** The sim's note about this step, shown under it. Null when the step is its own note. */
		String why = null;
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

		/**
		 * The OTHER npcs this step would take, or empty.
		 *
		 * Pollnivneach has three villagers to pickpocket and the sim used one of them, so the
		 * marker pointed at that one across town while an identical villager stood next to the
		 * player. These are the ids the SERVER runs the same script on for this step's op, worked
		 * out where the scripts are loaded; it is per op and not per npc, so The Feud's Lure takes
		 * two of its villagers and its Knock-Out only one.
		 */
		int[] also = NONE;

		/** Whether an npc in the scene is the one this step is about, or one just as good. */
		boolean takes(int npcId) {
			if (npcId == this.id) {
				return true;
			}
			for (int i = 0; i < this.also.length; i++) {
				if (this.also[i] == npcId) {
					return true;
				}
			}
			return false;
		}

		int[] at = null;

		/**
		 * The obj this step is HOLDING, for a step that uses one thing on another, else -1.
		 *
		 * "Use Battered key on Odd looking wall" is a step about the wall with the key in your
		 * hand. Which item that is matters twice: it is the one to light up in the bag, and
		 * holding it is how the plugin can tell you are past the steps that handed it over.
		 */
		int item = -1;

		/**
		 * What this step handed over, as obj ids, or empty.
		 *
		 * THE ONLY RECORD THAT A TALK HAPPENED. The quest's varp moves once a leg and says
		 * nothing about the steps inside it, but the sim asserts what the player is holding
		 * right after each click that hands something over - so carrying what a step gave you
		 * is how the plugin can tell that step is behind you. Ali Morrisane's two sales and the
		 * disguise they make are three of these in a row.
		 */
		int[] gives = NONE;

		/**
		 * What this step put ON, as obj ids: a receipt that only counts while it is worn.
		 *
		 * The same amulet in the bag is "Wear Ghostspeak amulet" still waiting to be done, so
		 * these are kept apart from {@link #gives} rather than both being "do you have it".
		 */
		int[] wears = NONE;

		/**
		 * The objs this step is about: what is in your hand, what it is used on, or both.
		 *
		 * "Use Knife on Battered book" is two items and one instruction, and a player holding
		 * one of them is not holding the step - so both are marked in the bag, and either of
		 * them arriving says the step before this one is done.
		 */
		int[] objs() {
			int held = this.item;
			int self = "obj".equals(this.kind) ? this.id : -1;
			if (held >= 0 && self >= 0 && held != self) {
				return new int[] { held, self };
			}
			if (held >= 0) {
				return new int[] { held };
			}
			return self >= 0 ? new int[] { self } : NONE;
		}
	}

	/** No items at all: a walk, a note, a talk. Shared so a frame does not allocate for each. */
	private static final int[] NONE = new int[0];

	private static final class Need {
		String name = "";
		int id = -1;
		int n = 1;
		/** Worn, not merely carried - a different instruction, and the reason these two merge. */
		boolean wear;

		/**
		 * Something to BRING, rather than something the quest hands over on the way.
		 *
		 * The table knows the difference because the sim does: a sim hands itself what a player
		 * would fetch from the bank and merely asserts what the quest gives it. 819 of the 1371
		 * item rows are the second kind, and listing those in red told a player to go and get a
		 * key that is sewn into a book they have not found yet.
		 */
		boolean bring;
	}

	/**
	 * A line of the quest guide, and the step of this leg it is written in front of.
	 *
	 * WITH THE STEP, because a leg often carries several and a panel that showed the first of
	 * them was describing the wrong part of the quest. Elemental Workshop I is ONE leg with
	 * eleven headings - its varp only moves when the quest ends - so the panel said "the bookcase
	 * on the east wall" while the player stood at the furnace.
	 */
	private static final class Head {
		int at;
		String text = "";
	}

	/**
	 * One of the quest's own working flags, and the step it is true from.
	 *
	 * THE SECOND HAND ON THE CLOCK. The progress varp moves once a leg; the flags move inside
	 * one - how many beers Drunken Ali has had, which gangs have been asked - and a step whose
	 * only trace is a flag can be seen no other way. A mark at the end of a leg's list says the
	 * leg is finished whatever its varp still reads, which is how a player who drank three beers
	 * stops being told to buy the first one.
	 */
	private static final class Mark {
		int at;
		int var = -1;
		boolean varbit;
		int is;
	}

	private static final class Stage {
		int from;
		final List<Mark> marks = new ArrayList<>();
		final List<Head> headings = new ArrayList<>();
		final List<Step> steps = new ArrayList<>();
		final List<Need> items = new ArrayList<>();
	}

	private static final class Quest {
		/**
		 * Every obj this quest expects the player to BRING, over all its legs.
		 *
		 * Quest-wide and not per leg, because a knife handed over at the start is still a knife
		 * from the bank three legs later - and because holding one of these says nothing about
		 * how far anybody has got, which is what {@link #reached} needs it for.
		 */
		final List<Integer> brought = new ArrayList<>();

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
	 * NOTHING IS TRACKED UNTIL THE PLAYER PICKS. Guessing took the first started-and-unfinished
	 * quest in table order, which is alphabetical by id, so somebody part-way through six quests
	 * was shown Fight Arena because "arena" sorts early. A guess that is wrong five times out of
	 * six is worse than an empty page with a list on it, and the page opens on the list.
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
		for (Stage st : q.stages) {
			for (Need need : st.items) {
				if (need.bring && need.id >= 0 && !q.brought.contains(Integer.valueOf(need.id))) {
					q.brought.add(Integer.valueOf(need.id));
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
				if (!(h instanceof Map)) {
					continue;
				}
				Head head = new Head();
				head.at = Json.integer((Map<?, ?>) h, "at", 0);
				head.text = Json.string((Map<?, ?>) h, "text", "");
				st.headings.add(head);
			}
		}
		List<?> marks = Json.array(o, "marks");
		if (marks != null) {
			for (Object m : marks) {
				if (!(m instanceof Map)) {
					continue;
				}
				Mark mark = new Mark();
				mark.at = Json.integer((Map<?, ?>) m, "at", 0);
				mark.var = Json.integer((Map<?, ?>) m, "var", -1);
				mark.varbit = "true".equals(String.valueOf(((Map<?, ?>) m).get("varbit")));
				mark.is = Json.integer((Map<?, ?>) m, "is", 0);
				st.marks.add(mark);
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
				step.item = Json.integer(m, "item", -1);
				step.also = ids(Json.array(m, "also"));
				step.gives = ids(Json.array(m, "gives"));
				step.wears = ids(Json.array(m, "wears"));
				step.why = m.containsKey("why") ? Json.string(m, "why", null) : null;
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
					// Brought in either list means brought: the carry row and the wear row are the
					// same item, and the one that knows where it came from decides.
					seen.bring = seen.bring || "true".equals(String.valueOf(m.get("bring")));
					continue;
				}
				Need need = new Need();
				need.name = name;
				need.id = id;
				need.n = Json.integer(m, "n", 1);
				need.wear = wearing;
				need.bring = "true".equals(String.valueOf(m.get("bring")));
				st.items.add(need);
			}
		}
		return st;
	}

	/** A json array of obj ids as an int[], or NONE. */
	private static int[] ids(List<?> list) {
		if (list == null || list.isEmpty()) {
			return NONE;
		}
		int[] out = new int[list.size()];
		for (int i = 0; i < out.length; i++) {
			out[i] = num(list.get(i));
		}
		return out;
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
	 * The quest being followed, which is the one the player pressed Track on and nothing else.
	 *
	 * IT USED TO GUESS, taking the first started-and-unfinished quest in the table, whose order is
	 * alphabetical by id and so arbitrary: somebody part-way through six quests was shown Fight
	 * Arena because "arena" sorts early. A guess that is wrong five times in six is worse than an
	 * empty page with a list on it, so the page opens on the list and the player picks.
	 */
	private Quest tracked() {
		if (this.pinned == null) {
			return null;
		}
		for (Quest q : this.quests) {
			if (q.id.equals(this.pinned)) {
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

	/**
	 * The leg to show: the one the varp names, unless the quest's own flags say it is finished.
	 *
	 * A LEG CAN BE OVER BEFORE THE VARP SAYS SO. The Feud writes its progress at the end of a
	 * conversation that three beers start, so a player who has drunk all three - and whose
	 * beer-counting flag says exactly that - can still be sitting on the value the leg began at.
	 * Rather than show them the first beer again, the flags are taken at their word and the next
	 * leg is shown: it is where the game will put them the moment it catches up.
	 *
	 * Bounded, because a table whose legs all read finished would otherwise walk off the end.
	 */
	private Stage legNow(Quest q) {
		Stage st = this.stageNow(q);
		for (int guard = 0; st != null && guard < 8; guard++) {
			if (st.steps.isEmpty() || this.reached(q, st) < st.steps.size()) {
				return st;
			}
			Stage next = null;
			for (Stage other : q.stages) {
				if (other.from > st.from && (next == null || other.from < next.from)) {
					next = other;
				}
			}
			if (next == null) {
				return st;
			}
			st = next;
		}
		return st;
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

	// ------------------------------------------------------------------ what to point at

	/**
	 * A step of the current leg, and the scene tile to point at it on.
	 *
	 * A tile of -1 is a step with nowhere to stand: an item you use from the inventory happens
	 * wherever you are, so it has words but no box.
	 */
	private static final class Spot {
		Step step;
		int sceneTileX = -1;
		int sceneTileZ = -1;
		String label;
	}

	private Spot held;
	private Stage heldFor;
	private long heldAt;

	/**
	 * The step of this leg you can act on from where you are standing, and where it is.
	 *
	 * NOT THE FIRST STEP OF THE LEG, which is what this did and why it felt a step behind. The varp
	 * says which leg you are on, not how far into it you are, and a leg almost always opens with a
	 * walk - a step that names no npc and no loc. Pointing at that meant nothing was highlighted
	 * until the walk was already behind you AND the varp had moved on: pick The Feud, whose first
	 * leg is "go to the bazaar, talk to Ali Morrisane", and Ali lit up only after you had talked to
	 * him. So the scene decides. The earliest step of the leg whose npc or object is actually in
	 * front of you wins; if none is, the earliest tile the leg recorded that the scene has loaded.
	 * Standing in the bazaar that is Ali himself; walking out of Lumbridge it is the road east.
	 *
	 * CACHED FOR A FIFTH OF A SECOND because findLocs sweeps the whole 104x104 scene through four
	 * layers, and the answer does not change fifty times a second. Both overlays read the one
	 * answer, which is also why the panel's instruction and the marker's box cannot name different
	 * steps.
	 */
	private Spot spot(Stage st) {
		long now = System.currentTimeMillis();
		if (st == this.heldFor && now - this.heldAt < 200L) {
			return this.held;
		}
		this.heldFor = st;
		this.heldAt = now;
		this.held = this.look(st);
		return this.held;
	}

	/**
	 * How far into this leg the quest's own items say the player has already got.
	 *
	 * THE BAG IS THE ONLY RECORD INSIDE A LEG. The varp moves between legs and says nothing
	 * about the dozen steps within one, so a leg whose first step is still in the scene stayed
	 * the answer for the whole leg: Elemental Workshop I puts the bookcase, the book, the key,
	 * the wall and the furnace in ONE leg, and the panel said "Search Bookcase" at the furnace.
	 *
	 * An item the quest hands over is a receipt for the steps that handed it over. Holding the
	 * battered book means the bookcase has been searched; holding the key means the book has been
	 * cut open. So the last step in the leg whose item is in the bag is where the leg is now, and
	 * everything before it is done. Items the player BROUGHT prove nothing and are skipped - a
	 * knife is in the bag from the first step to the last.
	 */
	private int reached(Quest q, Stage st) {
		int start = 0;
		// A STEP'S RECEIPT PUTS IT BEHIND YOU. Ali Morrisane sells a headpiece, then a beard, and
		// the two make a disguise: three steps in one leg, with the varp moving before the first
		// of them. Carrying - or WEARING - what a step handed over is the only thing in the game
		// that says it happened, so the leg starts after the last step whose receipt you have.
		for (int i = 0; i < st.steps.size() - 1; i++) {
			Step step = st.steps.get(i);
			boolean done = false;
			for (int j = 0; j < step.gives.length; j++) {
				done = done || ctx.countObjCarriedOrWorn(step.gives[j]) > 0;
			}
			// A WEAR IS ONLY DONE WHILE IT IS WORN. The amulet in the bag is that step waiting.
			for (int j = 0; j < step.wears.length; j++) {
				done = done || ctx.countWornObj(step.wears[j]) > 0;
			}
			if (done && i + 1 > start) {
				start = i + 1;
			}
		}

		// AND THE QUEST'S OWN FLAGS. A step whose only trace is a flag - three beers drunk, two
		// gangs asked - can be seen no other way, and the flag is already in the client because
		// the quest keeps it. `at` is the step it is true from, so everything above it is done.
		for (int i = 0; i < st.marks.size(); i++) {
			Mark mark = st.marks.get(i);
			if (mark.var < 0 || mark.is <= 0 || mark.at <= start) {
				continue;
			}
			int now = mark.varbit ? ctx.getVarbit(mark.var) : ctx.getVarp(mark.var);
			if (now >= mark.is) {
				start = mark.at;
			}
		}

		// THE FIRST STEP THAT WANTS IT, not the last. Holding a thing says the steps that handed
		// it over are done, and the step that hands it over is the one before the first step that
		// uses it - so the book puts the leg at "Read Battered book", which is where a player who
		// has just searched the bookcase actually is, rather than at the cut two steps later.
		List<Integer> seen = new ArrayList<>();
		for (int i = 0; i < st.steps.size(); i++) {
			int[] objs = st.steps.get(i).objs();
			for (int j = 0; j < objs.length; j++) {
				Integer obj = Integer.valueOf(objs[j]);
				if (seen.contains(obj)) {
					continue;
				}
				seen.add(obj);
				if (q != null && q.brought.contains(obj)) {
					continue;
				}
				if (ctx.countObj(obj.intValue()) > 0 && i > start) {
					start = i;
				}
			}
		}
		return start;
	}

	private Spot look(Stage st) {
		if (st == null) {
			return null;
		}
		int start = this.reached(this.tracked(), st);

		// EVERY LOC OF THE LEG IN ONE SWEEP. findLocs costs a full scene walk per call, so asking
		// it once per loc step would multiply that by the length of the leg, every frame.
		int wanted = 0;
		for (int i = start; i < st.steps.size(); i++) {
			if (st.steps.get(i).id >= 0 && "loc".equals(st.steps.get(i).kind)) {
				wanted++;
			}
		}
		List<SceneLoc> locs = null;
		if (wanted > 0) {
			int[] ids = new int[wanted];
			int n = 0;
			for (int i = start; i < st.steps.size(); i++) {
				if (st.steps.get(i).id >= 0 && "loc".equals(st.steps.get(i).kind)) {
					ids[n++] = st.steps.get(i).id;
				}
			}
			locs = ctx.findLocs(ids);
		}
		List<Actor> npcs = ctx.getNpcs();

		for (int i = start; i < st.steps.size(); i++) {
			Step s = st.steps.get(i);
			if (s.id < 0) {
				continue;
			}
			// An npc walks, so it is found by id in the scene rather than at the tile the manifest
			// recorded - that tile is where the sim met it, not where it is standing now.
			if ("talk".equals(s.kind) && npcs != null) {
				for (Actor a : npcs) {
					if (a != null && s.takes(a.npcId)) {
						return at(s, a.sceneTileX, a.sceneTileZ, nameOf(a, s));
					}
				}
			} else if ("loc".equals(s.kind) && locs != null) {
				for (SceneLoc l : locs) {
					if (l.id == s.id && l.level == ctx.getPlane()) {
						return at(s, l.sceneTileX, l.sceneTileZ, this.locName(s, l.id));
					}
				}
			}
		}

		// NOTHING OUT THERE, BUT SOMETHING IN YOUR BAG. An item step is done in the inventory, so
		// it can be acted on from anywhere - "Wear Ghostspeak amulet" before walking back to the
		// churchyard - and it is only worth saying while you are actually carrying the item. That
		// is also what makes it unstick: the amulet leaves the inventory when you put it on, so
		// the step stops winning and the leg moves on without the varp having to change.
		//
		// AFTER the scene, not before, although it comes first in the leg. An item you keep
		// carrying - a tome to read, a key to hold - is a step nothing can tell you have done, so
		// a step you can see in front of you has to beat it or the panel would stick on it.
		for (int i = start; i < st.steps.size(); i++) {
			Step s = st.steps.get(i);
			if ("obj".equals(s.kind) && this.carrying(s)) {
				Spot spot = new Spot();
				spot.step = s;
				return spot;
			}
		}

		// Nothing of the leg is in front of you, so point at the earliest tile it recorded that the
		// scene has loaded: the way out of the room rather than no marker at all. Off the edge of
		// the scene there is nothing to project onto, and a box pinned to the screen edge would be
		// a guess at a direction.
		for (int i = start; i < st.steps.size(); i++) {
			Step s = st.steps.get(i);
			if (s.at == null || s.at[2] != ctx.getPlane()) {
				continue;
			}
			int x = ctx.worldToSceneX(s.at[0]);
			int z = ctx.worldToSceneZ(s.at[1]);
			if (ctx.isInScene(x, z)) {
				return at(s, x, z, s.shown());
			}
		}

		// NOTHING TO POINT AT, BUT STILL A STEP. The leg is somewhere the scene cannot show -
		// the quest is three regions away - and the words are the whole of the help. Taken from
		// `start` like everything else: falling back to the leg's first step would undo what the
		// bag has already told us, and put "talk to the tree" back after the branch was in hand.
		for (int i = start; i < st.steps.size(); i++) {
			if (!"note".equals(st.steps.get(i).kind)) {
				Spot spot = new Spot();
				spot.step = st.steps.get(i);
				return spot;
			}
		}
		return null;
	}

	/** Whether every item a step names is in the bag: half of "use the knife on it" is no step. */
	private boolean carrying(Step step) {
		int[] objs = step.objs();
		if (objs.length == 0) {
			return false;
		}
		for (int i = 0; i < objs.length; i++) {
			if (ctx.countObj(objs[i]) <= 0) {
				return false;
			}
		}
		return true;
	}

	/**
	 * What to write over a loc: the cache's name for it, or the step's own word when it has none.
	 *
	 * A MULTILOC is the same shape as the multinpc below - one id whose name lives on a child the
	 * varbit picks - but it cannot be resolved in the same place. A loc's shell is exactly what a
	 * plugin asks about when it wants that varbit, so PluginContext hands back the shell and
	 * leaves resolving to the caller that wants it; the scene deals in shell ids, so a quest door
	 * or a bookcase that changes with the quest has no name of its own to give. The step's word is
	 * the better of the two in any case: it is what the guide calls the thing.
	 */
	private String locName(Step step, int id) {
		String name = ctx.getLocName(id);
		return name == null || name.isEmpty() ? step.shown() : name;
	}

	/**
	 * What to write over an npc: its own name, unless the config has not got one.
	 *
	 * An npc with no name of its own reads "null" - the four letters 377 puts in the config, not a
	 * missing value - and one whose type has not arrived yet reads "?". Neither is a label, so the
	 * step's own word stands in. PluginContext already resolves a multinpc to the child the player
	 * sees, so this is the npc that genuinely has no name rather than the Feud's villager.
	 */
	private static String nameOf(Actor npc, Step step) {
		String name = npc.name;
		if (name == null || name.isEmpty() || "null".equals(name) || "?".equals(name)) {
			return step.shown();
		}
		return name;
	}

	private static Spot at(Step step, int sceneTileX, int sceneTileZ, String label) {
		Spot spot = new Spot();
		spot.step = step;
		spot.sceneTileX = sceneTileX;
		spot.sceneTileZ = sceneTileZ;
		spot.label = label;
		return spot;
	}

	/**
	 * The guide line the step at this index sits under: the last one written before it.
	 *
	 * Before it and not nearest it, because a heading introduces what follows and says nothing
	 * about what came above. A leg whose first heading is written after its first step - which
	 * happens when a sim opens with a walk and the guide's wording starts at the talk - leaves
	 * that step with no heading at all rather than borrowing the one below it.
	 */
	private String headingFor(Stage st, int index) {
		if (st == null) {
			return null;
		}
		String text = null;
		for (Head head : st.headings) {
			if (head.at <= index) {
				text = head.text;
			}
		}
		return text;
	}

	/** Where a step sits in its leg, which is what its heading is keyed on. */
	private static int indexOf(Stage st, Step step) {
		if (st == null || step == null) {
			return -1;
		}
		for (int i = 0; i < st.steps.size(); i++) {
			if (st.steps.get(i) == step) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * What this leg wants that you have not got - and only what you were meant to BRING.
	 *
	 * A leg's item list is everything you must be holding by the end of it, which includes what
	 * the quest hands over while you are in it: the battered key is sewn into a book you have not
	 * found, so "need Battered key" was an instruction nobody could follow. Those rows are still
	 * in the table and still on the page, where they read as what is coming; they are just not
	 * counted as missing.
	 */
	/**
	 * The items of a leg worth showing anybody: the ones to BRING.
	 *
	 * The rest are what the quest hands over while you are in it, and a row for those is a row
	 * about nothing you can act on - "Battered key 0/1" above the steps that go and find it. They
	 * stay in the table, because that is where the sims put them and because {@link #reached}
	 * reads the ids to tell how far into a leg you have got; they are simply not a list.
	 */
	private static List<Need> bringing(Stage st) {
		List<Need> out = new ArrayList<>();
		if (st == null) {
			return out;
		}
		for (Need n : st.items) {
			if (n.bring) {
				out.add(n);
			}
		}
		return out;
	}

	private List<Need> missing(Stage st) {
		List<Need> out = new ArrayList<>();
		if (st == null) {
			return out;
		}
		for (Need n : st.items) {
			// ON THE BODY COUNTS. A player wearing the amulet they were told to bring has brought
			// it, and the row said they had not.
			if (n.bring && n.id >= 0 && ctx.countObjCarriedOrWorn(n.id) < n.n) {
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
			Stage st = legNow(q);
			Spot sp = spot(st);
			// The step the marker is boxing, so the words and the box agree. With nothing of the
			// leg in the scene there is no box, and the leg's own first instruction is the answer.
			Step lead = sp != null ? sp.step : leadStep(st);
			List<String> lines = new ArrayList<>();
			String heading = headingFor(st, lead == null ? 0 : indexOf(st, lead));
			if (heading != null && !heading.isEmpty()) {
				lines.add("@whi@" + wrapped(heading));
			}
			lines.add(lead == null ? "No step recorded for this stage." : wrapped(lead.text));
			if (lead != null && lead.why != null && !lead.why.isEmpty()) {
				lines.add("@gra@" + wrapped(lead.why));
			}
			// THE NAME ONLY IF THE INSTRUCTION HAS NOT ALREADY SAID IT. "Pickpocket Villager."
			// with "Villager" under it is the same word twice, and the line is there for the
			// steps whose wording does not name what they are pointing at.
			String named = lead == null ? null : lead.shown();
			if (named != null && !named.isEmpty() && lead.text.indexOf(named) < 0) {
				lines.add("@gre@" + named);
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
			Spot sp = spot(legNow(q));
			if (sp == null || sp.sceneTileX < 0) {
				return;
			}
			mark(g, sp.sceneTileX, sp.sceneTileZ, colour(), sp.label);
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

	// ------------------------------------------------------------------ the item in your bag

	/**
	 * The obj the step in front of you wants in your hand, or -1.
	 *
	 * Only one you are actually holding: a step that wants something you have not got is a step
	 * you cannot take, and a box drawn round an empty slot would be pointing at nothing. It is
	 * the step's own item for "Read Battered book", and the held half for "Use Battered key on
	 * Odd looking wall" - the wall is in the world, the key is in the bag, and both get marked.
	 */
	private int[] wantedItems() {
		Quest q = this.tracked();
		if (q == null) {
			return NONE;
		}
		Spot sp = this.spot(this.legNow(q));
		if (sp == null || sp.step == null) {
			return NONE;
		}
		// ALL OF THEM OR NONE. A box means "click this", and half of "use the knife on the
		// branch" is not something anybody can click - marking the knife alone would be pointing
		// at the one part of the step that is not the problem.
		return this.carrying(sp.step) ? sp.step.objs() : NONE;
	}

	/** How many slots the sidebar was last asked to mark, so their going is repainted too. */
	private int marked;

	/**
	 * Keeps the sidebar repainting while there is something in it to mark.
	 *
	 * ON THE GAME TICK AND NOT EVERY FRAME. The client caches the sidebar raster and repaints it
	 * when something it owns changes, so an overlay there draws in those frames only. A tick is
	 * 600ms, which is as often as the answer can change anyway - an item arrives in the bag on a
	 * tick - and it costs one 190x261 repaint rather than fifty a second. The frames between keep
	 * the mark because the raster keeps it; the tick after it stops being wanted is what rubs it
	 * out, which is why `marked` is remembered.
	 */
	@Subscribe
	public void onGameTick(GameTick tick) {
		if (!this.highlight || !ctx.isLoggedIn()) {
			return;
		}
		int want = this.wantedItems().length;
		if (want > 0 || this.marked > 0) {
			ctx.requestSidebarRedraw();
		}
		this.marked = want;
	}

	/**
	 * The box round the slot the step's item is in.
	 *
	 * ON THE SIDEBAR, which is a different raster from the one the other two overlays draw into
	 * - see Overlay.LAYER_SIDEBAR. Null bounds are the ordinary answer rather than an error: the
	 * player has another tab open, or a shop has taken the panel, and there is nothing to mark.
	 */
	private final Overlay bag = new Overlay() {

		public int layer() {
			return Overlay.LAYER_SIDEBAR;
		}

		/** Not draggable: it marks a slot the client owns, so it has nowhere else to be. */
		public boolean movable() {
			return false;
		}

		public void render(OverlayGraphics g) {
			if (!highlight || !ctx.isLoggedIn()) {
				return;
			}
			int[] want = wantedItems();
			int c = colour();
			for (int i = 0; i < want.length; i++) {
				for (int slot = 0; slot < ctx.getInventorySize(); slot++) {
					if (ctx.getInventoryObjId(slot) != want[i]) {
						continue;
					}
					int[] box = ctx.getSidebarInvCellBounds(slot);
					if (box == null) {
						return;
					}
					g.fillAlpha(box[0], box[1], box[2], box[3], c, 48);
					g.box(box[0], box[1], box[2], box[3], c);
					// ONE SLOT PER ITEM. Four buckets in the bag is one instruction, and four
					// boxes would read as four things to do.
					break;
				}
			}
		}
	};

	// ------------------------------------------------------------------ the sidebar page

	/**
	 * GUARDED, as XpTrackerPlugin's note explains: addPanel arrived in a later client than a jar
	 * may land on, and an unresolved call throws NoSuchMethodError, which the manager answers by
	 * switching the whole plugin off - losing the overlays too, over a page that client could not
	 * have drawn. Throwable rather than Exception, because a missing method is an Error.
	 */
	private void addRailPage() {
		try {
			this.addPanel("Quest helper", "pin", new ConfigList() {

				/** Browsing shows every quest; otherwise the tracked quest's leg, under a header. */
				private boolean picking() {
					return browsing || tracked() == null;
				}

				public int size() {
					if (picking()) {
						return listed().size();
					}
					Quest q = tracked();
					Stage st = legNow(q);
					return 1 + (st == null ? 0 : st.steps.size() + bringing(st).size());
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
					Stage st = legNow(q);
					if (st == null) {
						return "";
					}
					int i = index - 1;
					if (i < st.steps.size()) {
						// NOT TRUNCATED. The panel wraps a row itself, so cutting at 58 characters
						// first only guaranteed the wrap landed on a "..".
						return st.steps.get(i).text;
					}
					List<Need> items = bringing(st);
					int at = i - st.steps.size();
					return at < items.size() ? pretty(items.get(at).name) : "";
				}

				public String detail(int index) {
					if (picking()) {
						List<Quest> all = listed();
						return index >= 0 && index < all.size() ? statusOf(all.get(index)) : null;
					}
					Quest q = tracked();
					Stage st = legNow(q);
					if (index == 0) {
						Spot sp = spot(st);
						String head = headingFor(st, sp == null ? 0 : indexOf(st, sp.step));
						return statusOf(q) + (head == null || head.isEmpty() ? "" : " - " + head);
					}
					if (st == null) {
						return null;
					}
					int i = index - 1;
					if (i < st.steps.size()) {
						// The explanation under the instruction when there is one - "Open it; the only
						// way into the house" under "Open Door." - and the thing's name otherwise.
						Step step = st.steps.get(i);
						if (step.why != null && !step.why.isEmpty()) {
							return step.why;
						}
						// And nothing at all rather than the name the row above has just said.
						String name = step.shown();
						return name == null || step.text.indexOf(name) >= 0 ? null : name;
					}
					List<Need> items = bringing(st);
					int at = i - st.steps.size();
					return at >= items.size() ? null : items.get(at).wear ? "to wear" : "to carry";
				}

				public String value(int index) {
					if (picking()) {
						return null;
					}
					Quest q = tracked();
					Stage st = legNow(q);
					if (index == 0 || st == null) {
						return null;
					}
					int i = index - 1;
					if (i < st.steps.size()) {
						// The one the marker is pointing at, so the list reads as a position in the
						// leg and not just as its contents.
						Spot sp = spot(st);
						return sp != null && sp.step == st.steps.get(i) ? "now" : null;
					}
					List<Need> items = bringing(st);
					int at = i - st.steps.size();
					if (at >= items.size()) {
						return null;
					}
					Need n = items.get(at);
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
		this.addOverlay(this.bag);
		this.addRailPage();
	}

	protected void shutDown() {
		this.quests.clear();
		this.pinned = null;
		this.browsing = false;
		// One more repaint, so the box goes with the plugin rather than sitting on the cached
		// sidebar until something else happens to repaint it.
		if (this.marked > 0) {
			ctx.requestSidebarRedraw();
			this.marked = 0;
		}
	}
}
