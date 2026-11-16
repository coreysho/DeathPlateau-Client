package jagex2.client.plugin.builtin;

import jagex2.client.plugin.Actor;
import jagex2.client.plugin.ConfigList;
import jagex2.client.plugin.ConfigItem;
import jagex2.client.plugin.Overlay;
import jagex2.client.plugin.OverlayGraphics;
import jagex2.client.plugin.Plugin;
import jagex2.client.plugin.PluginConfig;
import jagex2.client.plugin.PluginDescriptor;
import jagex2.client.plugin.Subscribe;
import jagex2.client.plugin.event.GameStateChanged;
import jagex2.client.plugin.event.GameTick;
import jagex2.client.plugin.event.MenuOptionClicked;
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
 * HOW FAR INTO A LEG, which the server does not say. The quest varp moves once a leg - "you have
 * talked to Father Urhney", not "you have taken three of the five steps since" - so the sidebar
 * lists the whole of the leg you are on, and which of its steps leads is worked out here from
 * four things: what the quest has put in your bag, what its own flags read ({@link #reached}),
 * what is standing in front of you ({@link #spot}), and WHAT YOU HAVE JUST CLICKED ({@link
 * #watchTheStep}).
 *
 * The click is the one that moves inside a leg. A talk leaves no trace at all - nothing arrives,
 * no flag moves - so before the client watched for it, the panel went on naming the person you
 * had just finished talking to for as long as they stood in front of you, and only moved on when
 * the quest's own progress caught up a leg later. Clicking the step the panel names is the player
 * saying they have done it, and a conversation that opens and then closes is that same step
 * finishing. Neither is read off the varp, so neither can be proof - they are the same kind of
 * inference as the bag - but between them the panel moves at the speed the player does.
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

		/**
		 * The dialogue options this step picks, in the order it picks them, or empty.
		 *
		 * The sims have always recorded these - it is how a run gets through a conversation - and
		 * 522 steps across 102 quests carry at least one. A pick is either the option's words or
		 * its place in the list as a number, and the words may offer several alternatives
		 * separated by "||" for a question that is asked at random.
		 */
		String[] picks = NO_PICKS;

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

	/**
	 * The plane of a tile the table recorded without one: match whatever floor you are on.
	 *
	 * Two thirds of the tiles in the table are a bare pair of coordinates, and a pair says
	 * nothing about the floor either way - so the only honest reading is "wherever you are".
	 * The ones that DO name a level are still held to it: a tile on the top floor of the Temple
	 * of Light is not where to stand while you are at the bottom of it.
	 */
	private static final int ANY_PLANE = -1;
	private static final String[] NO_PICKS = new String[0];

	/** A step's dialogue picks, as written. */
	private static String[] picks(List<?> raw) {
		if (raw == null || raw.isEmpty()) {
			return NO_PICKS;
		}
		String[] out = new String[raw.size()];
		for (int i = 0; i < out.length; i++) {
			Object o = raw.get(i);
			out[i] = o == null ? "" : String.valueOf(o);
		}
		return out;
	}

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

		/**
		 * The last step of this leg the player was SEEN to take, or -1.
		 *
		 * Set by {@link #watchTheStep} from the player's own clicks and forgotten on logout,
		 * because it is this character's progress and nothing in the cache. It is a floor under
		 * {@link #reached} rather than the whole answer: the bag and the quest's flags can say
		 * more than the clicks this session has seen, never less.
		 */
		int doneTo = -1;

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

	/**
	 * The quest the page follows, by id, or empty for none.
	 *
	 * A SETTING SO THAT IT SURVIVES, which is the whole point of it being here rather than a
	 * plain field: a field is dropped when the plugin stops, and the plugin stops on every
	 * client restart, so the player had to find their quest again every time they opened the
	 * game. Written by the Track button, by {@link #follow}, and by nothing else.
	 */
	@ConfigItem(keyName = "tracked", name = "Tracked quest",
		description = "The quest the page follows - set by the Track button on the page")
	public String trackedQuest = "";

	/**
	 * True while the page is showing the quest list instead of the tracked quest's steps.
	 *
	 * THE PAGE OPENS ON THE LIST when there is nothing to follow, and it is the player's pick
	 * that closes it. What it does NOT do is guess: taking the first started-and-unfinished
	 * quest in table order, which is alphabetical by id, showed somebody part-way through six
	 * quests Fight Arena because "arena" sorts early. {@link #followTheQuestYouAreDoing} picks
	 * one up only when it is not a guess - the quest whose progress just moved, or the single
	 * quest underway.
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
				step.picks = picks(Json.array(m, "picks"));
				step.gives = ids(Json.array(m, "gives"));
				step.wears = ids(Json.array(m, "wears"));
				step.why = m.containsKey("why") ? Json.string(m, "why", null) : null;
				List<?> at = Json.array(m, "at");
				if (at != null && at.size() >= 2) {
					// A MISSING PLANE IS UNKNOWN AND NOT ZERO, which is what this read as before:
					// 2677 of the table's tiles are a pair, because the sim stood there with a
					// `go [x,z]` that never named a level, and taking that as the ground floor
					// threw every one of them away the moment the player climbed a staircase -
					// no marker on an upper floor, and a walk that could never be arrived at.
					step.at = new int[] { num(at.get(0)), num(at.get(1)), at.size() > 2 ? num(at.get(2)) : ANY_PLANE };
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
	 * The quest being followed: the one the player pressed Track on, or the one the plugin
	 * picked up because the player is plainly doing it.
	 *
	 * IT STILL DOES NOT GUESS. The two ways in are both evidence rather than a sort order - a
	 * quest whose progress moved while the plugin was watching, and a single quest underway with
	 * no second one to confuse it. See {@link #followTheQuestYouAreDoing}.
	 */
	private Quest tracked() {
		if (this.trackedQuest == null || this.trackedQuest.isEmpty()) {
			return null;
		}
		for (Quest q : this.quests) {
			if (q.id.equals(this.trackedQuest)) {
				return q;
			}
		}
		return null;
	}

	/**
	 * Follow this quest from now on, and remember it.
	 *
	 * SAVED EVERY TIME, including when the plugin picked the quest itself: the player who logs
	 * back in tomorrow is still in the middle of the same quest, and the point of noticing it
	 * was to stop making them say so twice.
	 */
	private void follow(String id) {
		String want = id == null ? "" : id;
		if (want.equals(this.trackedQuest)) {
			// ALREADY FOLLOWING IT, and this is reached once a tick by the rule below - so it
			// must not touch the page. Closing the list here made Change unusable: press it, and
			// the next tick put the steps straight back.
			return;
		}
		this.trackedQuest = want;
		// The pending click belonged to the quest we have just left.
		this.waiting = null;
		try {
			List<PluginConfig.Item> items = this.config == null ? null : this.config.getItems();
			for (int i = 0; items != null && i < items.size(); i++) {
				if ("tracked".equals(items.get(i).key)) {
					this.config.set(items.get(i), want);
					return;
				}
			}
		} catch (Throwable ignored) {
			// A page that cannot save the pin still follows the quest for this session.
		}
	}

	/**
	 * FOLLOW THE QUEST THE PLAYER IS ACTUALLY DOING, once a tick.
	 *
	 * The page used to follow nothing at all until Track was pressed, which left a player
	 * half-way through a quest looking at a list of 108 of them - the one thing the plugin knows
	 * for certain about them, that they are in the middle of this one, ignored.
	 *
	 * TWO SIGNALS, BOTH EVIDENCE.
	 *
	 * A MOVE is the strongest thing there is: the quest whose progress var changed while the
	 * plugin was watching is the quest being played, whatever else is half-finished. It is also
	 * what makes starting a quest show its first step at once - the var going from nothing to
	 * its first stage is a move like any other.
	 *
	 * ONE QUEST UNDERWAY is the other, and it is not a guess either: with exactly one quest
	 * started and unfinished there is nothing to choose between. Two or more and the page waits
	 * on the list, which is where the old alphabetical guess went wrong.
	 *
	 * NOT DURING LOGIN. Every varp arrives in the first few ticks after logging in, so every
	 * started quest would look like it had just moved. Those ticks only record the values.
	 */
	private void followTheQuestYouAreDoing() {
		int n = this.quests.size();
		if (this.was == null || this.was.length != n) {
			this.was = new int[n];
			for (int i = 0; i < n; i++) {
				this.was[i] = this.questValue(this.quests.get(i));
			}
			this.settling = SETTLING;
		}
		Quest moved = null;
		for (int i = 0; i < n; i++) {
			Quest q = this.quests.get(i);
			int now = this.questValue(q);
			if (now == this.was[i]) {
				continue;
			}
			// BACKWARDS IS A NEW RUN AT IT, not progress: a quest reset by a cheat or by the
			// content itself, and the steps this session watched the player take are no longer
			// behind them.
			if (now < this.was[i]) {
				forget(q);
			}
			this.was[i] = now;
			if (this.settling <= 0 && now > 0 && !this.finished(q)) {
				moved = q;
			}
		}
		if (this.settling > 0) {
			this.settling--;
		}
		if (moved != null) {
			// Doing something in a quest is asking for that quest, so this one closes the list.
			this.follow(moved.id);
			this.browsing = false;
			return;
		}
		if (this.tracked() != null) {
			return;
		}
		Quest only = null;
		int count = 0;
		for (Quest q : this.quests) {
			if (this.started(q) && !this.finished(q)) {
				only = q;
				count++;
			}
		}
		if (count == 1) {
			this.follow(only.id);
		}
	}

	/** Each quest's progress as of the last tick, so that a move can be seen at all. */
	private int[] was;

	/** Ticks left of the login settle, during which a move is recorded but followed by nothing. */
	private int settling;

	/** How long the varps take to arrive after logging in, in game ticks. */
	private static final int SETTLING = 5;

	/** Everything this session watched the player do in a quest, forgotten. */
	private static void forget(Quest q) {
		for (Stage st : q.stages) {
			st.doneTo = -1;
		}
	}

	/**
	 * The leg of the quest the varp says you are on: the last stage whose `from` you have passed.
	 *
	 * AND THE FIRST OF TWO THAT SHARE A VALUE. Monkey Madness records two legs at stage 12 -
	 * the varp does not move between the Grand Tree and Lumdo - and taking the later of them
	 * put nine steps where no player could ever see them. The earlier one is where somebody at
	 * stage 12 is, and {@link #legNow} walks on to the other when its steps are behind them.
	 */
	private Stage stageNow(Quest q) {
		int value = this.questValue(q);
		Stage best = null;
		for (Stage st : q.stages) {
			if (st.from <= value && (best == null || st.from > best.from)) {
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
			// A LEG IS OVER WHEN NOTHING IN IT IS LEFT TO DO, which is not the same as having run
			// off the end of its list. Four legs in the table END with an explanation rather than
			// an instruction - Death Plateau's two talks with Harold are followed by "Roll the
			// dice" - and once the talks were behind you the only thing left was a note, which
			// the panel cannot name: it said "No step recorded for this stage" and the marker
			// went out. A leg with NO instructions at all is the same case with the count at
			// zero, which is Big Chompy's stage 35, a wait while Rantz misses.
			if (this.reached(q, st) <= lastAction(st)) {
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

	/** The last step of a leg that names something to do, or -1 when the leg names nothing. */
	private static int lastAction(Stage st) {
		if (st == null) {
			return -1;
		}
		for (int i = st.steps.size() - 1; i >= 0; i--) {
			if (!"note".equals(st.steps.get(i).kind)) {
				return i;
			}
		}
		return -1;
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

		// AND WHAT THE PLAYER WAS SEEN TO DO, which is the only thing that moves for a step
		// leaving no trace at all. A FLOOR and not the answer: the bag and the flags above can
		// know more than this session watched - somebody who logged in half-way through a leg -
		// and must not be pulled back by it.
		if (st.doneTo >= start) {
			start = st.doneTo + 1;
		}
		return start;
	}

	private Spot look(Stage st) {
		if (st == null) {
			return null;
		}
		int start = this.reached(this.tracked(), st);
		// THE LAST STEP STANDS ON THE LAST LEG. Everything done and the quest's varp not yet
		// caught up happens for a tick or two at the end of any leg, and legNow moves to the next
		// one - except on the last, where there is nowhere to move to and an empty panel would be
		// the reward for finishing the quest.
		if (start >= st.steps.size() && !st.steps.isEmpty()) {
			start = st.steps.size() - 1;
		}

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
			if (s.at == null || !this.onThisFloor(s)) {
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

	/**
	 * What this leg wants that you have not got - and only what you were meant to BRING.
	 *
	 * A leg's item list is everything you must be holding by the end of it, which includes what
	 * the quest hands over while you are in it: the battered key is sewn into a book you have not
	 * found, so "need Battered key" was an instruction nobody could follow. Those rows are still
	 * in the table and still on the page, where they read as what is coming; they are just not
	 * counted as missing.
	 */
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
		if (!ctx.isLoggedIn()) {
			return;
		}
		this.tick = tick.count;
		// BEFORE THE HIGHLIGHT CHECK, both of them: which quest the player is in the middle of
		// and how far into it they are have nothing to do with whether they want a box drawn
		// round the target, and a player with the highlight off was left with a page that never
		// moved.
		this.followTheQuestYouAreDoing();
		this.watchTheStep();
		if (!this.highlight) {
			return;
		}
		int want = this.wantedItems().length;
		if (want > 0 || this.marked > 0) {
			ctx.requestSidebarRedraw();
		}
		this.marked = want;

		// THE CHATBOX IS CACHED TOO, and for the same reason: an option box that nothing is
		// disturbing is painted once and kept, so the mark on a line would come and go with the
		// mouse. The tick after the box closes is what rubs it out, which is why `marking` is
		// remembered exactly as `marked` is above.
		boolean option = this.optionNow() >= 0;
		if (option || this.marking) {
			ctx.requestChatRedraw();
		}
		this.marking = option;
	}

	/** Whether an option was marked last tick, so the tick it stops is the one that rubs it out. */
	private boolean marking = false;

	/** The game tick, for timing out a click whose step never happened. */
	private int tick;

	/**
	 * A click whose step has not finished yet.
	 *
	 * A CLICK IS NOT THE STEP. Clicking "Talk-to Ali Morrisane" from across the bazaar starts a
	 * walk, a conversation and then an end to it, and the step is done at the end - so taking the
	 * click itself as the answer would have moved the panel on while the player was still
	 * standing there reading, and taken the mark off the option the step says to pick with it.
	 * This is the click, held until the thing it started finishes.
	 */
	private static final class Waiting {
		Stage leg;
		int step;
		String kind = "";
		int tick;
		boolean sawDialogue;
		/** Where the thing clicked was standing, or -1 for one used out of the bag. */
		int worldX = -1;
		int worldZ = -1;
	}

	private Waiting waiting;

	/** How close counts as having walked to the thing you clicked, or to the tile of a walk. */
	private static final int CLOSE = 3;

	/** How long a click is held before it is written off, in game ticks. */
	private static final int GIVE_UP = 20;

	/**
	 * WHAT THE PLAYER HAS JUST DONE, watched to its end, once a tick.
	 *
	 * Three endings, because three kinds of step end differently:
	 *
	 * A CONVERSATION THAT CLOSES is a talk taken. Nothing else can see one happen - no item
	 * arrives, no flag moves - which is why the panel used to sit on "Talk to Ali Morrisane"
	 * for as long as Ali stood in front of you. While it is open the step is held, on purpose:
	 * the panel goes on naming it, so the mark stays on the option the step would pick.
	 *
	 * WALKING TO THE THING is a loc taken. A door, a ladder, a chest: the click sends the player
	 * to it, and arriving there with the click outstanding is the step. A conversation beats it
	 * when one opens, because a chest that talks is finished when the talking is.
	 *
	 * ARRIVING is a walk taken, and it needs no click at all - "Go to the bazaar" is done when
	 * you are in the bazaar, whether or not anything of the next step has loaded yet.
	 *
	 * AND A CLICK THAT LED NOWHERE IS WRITTEN OFF. Clicked the wrong thing, walked away,
	 * interrupted by a fight: {@link #GIVE_UP} ticks later the panel is where it was, which is
	 * the honest answer and not a step skipped.
	 */
	private void watchTheStep() {
		Quest q = this.tracked();
		Stage leg = q == null ? null : this.legNow(q);
		if (leg != null) {
			Spot sp = this.spot(leg);
			Step on = sp == null ? null : sp.step;
			if (on != null && "walk".equals(on.kind) && on.at != null && this.onThisFloor(on)
				&& this.near(on.at[0], on.at[1])) {
				this.took(leg, indexOf(leg, on));
			}
		}
		Waiting w = this.waiting;
		if (w == null) {
			return;
		}
		if (ctx.isDialogueOpen()) {
			w.sawDialogue = true;
			return;
		}
		boolean done;
		if ("talk".equals(w.kind)) {
			done = w.sawDialogue;
		} else if (w.worldX < 0) {
			// Nothing standing anywhere to walk to - an obj used out of the bag happens where the
			// player already is - so the tick after the click is the end of it.
			done = w.sawDialogue || this.tick != w.tick;
		} else {
			// WALKING TO IT IS THE WHOLE OF IT, and a conversation is NOT enough on its own:
			// nobody talks to a door from across the room, so a chatbox that opens on the way
			// there - a level-up, most often - is somebody else's news and not this step
			// finishing. A chest that does talk has been walked to by the time it does.
			done = this.near(w.worldX, w.worldZ);
		}
		if (done) {
			this.took(w.leg, w.step);
			this.waiting = null;
			return;
		}
		if (this.tick - w.tick > GIVE_UP) {
			this.waiting = null;
		}
	}

	/**
	 * Remember a step as taken, and throw away the cached answer it has just made stale.
	 *
	 * The cache is a fifth of a second of "the step in front of you", which is cheap for a
	 * question asked every frame and wrong the moment the step is behind you - and this plugin
	 * exists to say what to do NEXT, so it says it now rather than on the next frame after that.
	 */
	private void took(Stage leg, int at) {
		if (leg == null || at < 0 || at <= leg.doneTo) {
			return;
		}
		leg.doneTo = at;
		this.heldFor = null;
	}

	/** Whether this step's tile is on the floor the player is on, or names no floor at all. */
	private boolean onThisFloor(Step step) {
		return step.at != null && (step.at[2] == ANY_PLANE || step.at[2] == ctx.getPlane());
	}

	/** Whether the player is standing at a world tile, give or take {@link #CLOSE}. */
	private boolean near(int worldX, int worldZ) {
		int dx = ctx.getWorldX() - worldX;
		int dz = ctx.getWorldZ() - worldZ;
		return (dx < 0 ? -dx : dx) <= CLOSE && (dz < 0 ? -dz : dz) <= CLOSE;
	}

	/**
	 * THE PLAYER CLICKED SOMETHING. If it is the step the panel is naming, that step is under
	 * way, and {@link #watchTheStep} waits for the end of it.
	 *
	 * ONLY THE STEP IN FRONT OF THEM, never one further down the leg. A player clicking three
	 * steps ahead - the furnace while the panel says to search the bookcase - has not done the
	 * two in between, and a panel that jumped there would have skipped them silently. The step
	 * the panel names is the one the plugin claimed was next, so doing it is the one click it
	 * can read without guessing.
	 */
	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event) {
		if (!ctx.isLoggedIn() || event == null) {
			return;
		}
		Quest q = this.tracked();
		if (q == null) {
			return;
		}
		Stage leg = this.legNow(q);
		Spot sp = this.spot(leg);
		Step on = sp == null ? null : sp.step;
		if (leg == null || on == null || !clicked(on, event.target)) {
			return;
		}
		int at = indexOf(leg, on);
		if (at < 0) {
			return;
		}
		Waiting w = new Waiting();
		w.leg = leg;
		w.step = at;
		w.kind = on.kind;
		w.tick = this.tick;
		if (event.target.sceneTileX >= 0) {
			w.worldX = ctx.sceneToWorldX(event.target.sceneTileX);
			w.worldZ = ctx.sceneToWorldZ(event.target.sceneTileZ);
		}
		this.waiting = w;
	}

	/** Whether a click landed on what this step is about. */
	private static boolean clicked(Step step, MenuOptionClicked.Target target) {
		if (target == null || target.id < 0) {
			return false;
		}
		if ("talk".equals(step.kind)) {
			// The npc's family, as everywhere else here: one of Pollnivneach's three villagers
			// is the step as much as the one the sim met.
			return target.is(MenuOptionClicked.Target.NPC) && step.takes(target.id);
		}
		if ("loc".equals(step.kind)) {
			return target.is(MenuOptionClicked.Target.LOC) && target.id == step.id;
		}
		if ("obj".equals(step.kind)) {
			if (!target.is(MenuOptionClicked.Target.OBJ) && !target.is(MenuOptionClicked.Target.WORN)) {
				return false;
			}
			int[] objs = step.objs();
			for (int i = 0; i < objs.length; i++) {
				if (objs[i] == target.id) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Logging out is the end of this character's progress.
	 *
	 * WHAT THE CLICKS SAID GOES WITH THEM. The next player at this client may be somebody else
	 * entirely, and "you have already talked to him" is the last thing they need to be told
	 * about a quest they have not started. The quest to follow is kept, because it is a saved
	 * setting and the same player usually comes back.
	 */
	@Subscribe
	public void onGameStateChanged(GameStateChanged event) {
		if (event == null || event.isLoggedIn()) {
			return;
		}
		for (Quest q : this.quests) {
			forget(q);
		}
		this.was = null;
		this.waiting = null;
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

	/**
	 * Which option on screen the step would pick, or -1.
	 *
	 * THE SIM'S OWN RULE, because the sim is what wrote these picks and a second rule would
	 * disagree with it somewhere: a numeric pick is the option's place in the list, counting from
	 * one; a worded pick is matched by case-insensitive SUBSTRING, and may offer several
	 * alternatives separated by "||" for a question asked at random - whichever of them is on the
	 * menu is the answer. See drive() in the engine's tools/sim/a1lib.ts.
	 *
	 * ONLY THE FIRST PICK OF A STEP. A step that picks twice does so in two different boxes, one
	 * after the other, and the second has not been asked yet.
	 */
	private int optionFor(Step step, List<String> options) {
		if (step == null || step.picks.length == 0 || options.isEmpty()) {
			return -1;
		}
		String pick = step.picks[0].trim();
		if (pick.isEmpty()) {
			return -1;
		}
		boolean numeric = true;
		for (int i = 0; i < pick.length(); i++) {
			if (pick.charAt(i) < '0' || pick.charAt(i) > '9') {
				numeric = false;
				break;
			}
		}
		// PARSED INSIDE THE GUARD, because this runs while the chatbox is being drawn: the digits
		// are the table's and a long enough run of them overflows an int, and five throws out of
		// an overlay is what PluginManager disables a plugin at.
		if (numeric) {
			try {
				int at = Integer.parseInt(pick) - 1;
				return at >= 0 && at < options.size() ? at : -1;
			} catch (NumberFormatException overflow) {
				return -1;
			}
		}
		String[] wants = pick.split("\\|\\|");
		for (int i = 0; i < options.size(); i++) {
			String option = options.get(i).toLowerCase();
			for (int w = 0; w < wants.length; w++) {
				String want = wants[w].trim().toLowerCase();
				if (!want.isEmpty() && option.indexOf(want) >= 0) {
					return i;
				}
			}
		}
		return -1;
	}

	/** The option the tracked quest's live step would pick, or -1 - the whole of what chat draws. */
	private int optionNow() {
		if (!this.highlight) {
			return -1;
		}
		List<String> options = ctx.getDialogueOptions();
		if (options.isEmpty()) {
			return -1;
		}
		Quest q = this.tracked();
		if (q == null) {
			return -1;
		}
		Spot spot = this.spot(this.legNow(q));
		return spot == null ? -1 : this.optionFor(spot.step, options);
	}

	/**
	 * The box round the dialogue option the step would pick.
	 *
	 * IN THE CHATBOX, a third raster again - see Overlay.LAYER_CHAT. Nothing is drawn unless the
	 * quest being tracked has a step with a pick AND that pick is one of the options actually on
	 * screen, so an unrelated conversation is left alone.
	 */
	private final Overlay chat = new Overlay() {

		public int layer() {
			return Overlay.LAYER_CHAT;
		}

		/** Not draggable: it marks a line the client owns, so it has nowhere else to be. */
		public boolean movable() {
			return false;
		}

		public void render(OverlayGraphics g) {
			if (!ctx.isLoggedIn()) {
				return;
			}
			int at = optionNow();
			if (at < 0) {
				return;
			}
			int[] box = ctx.getDialogueOptionBounds(at);
			if (box == null) {
				return;
			}
			int c = colour();
			g.fillAlpha(box[0], box[1], box[2], box[3], c, 48);
			g.box(box[0], box[1], box[2], box[3], c);
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
						if (sp != null && sp.step == st.steps.get(i)) {
							return "now";
						}
						// AND THE ONES BEHIND YOU. Everything above the step being named is what
						// the bag, the quest's flags and the player's own clicks say is already
						// taken - the same answer the panel is drawn from, said on the page where
						// a player reads the leg. A note is nobody's step and gets nothing.
						int live = sp == null || sp.step == null ? -1 : indexOf(st, sp.step);
						if (live > 0 && i < live && !"note".equals(st.steps.get(i).kind)) {
							return "done";
						}
						return null;
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
						return q.id.equals(trackedQuest) ? "Tracking" : "Track";
					}
					return index == 0 ? "Change" : null;
				}

				public void onAction(int index) {
					if (picking()) {
						List<Quest> all = listed();
						if (index >= 0 && index < all.size()) {
							browsing = false;
							follow(all.get(index).id);
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
		this.addOverlay(this.chat);
		this.addRailPage();
	}

	protected void shutDown() {
		this.quests.clear();
		this.browsing = false;
		this.was = null;
		this.waiting = null;
		// One more repaint, so the box goes with the plugin rather than sitting on the cached
		// sidebar until something else happens to repaint it.
		if (this.marked > 0) {
			ctx.requestSidebarRedraw();
			this.marked = 0;
		}
	}
}
