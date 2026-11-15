package jagex2.client.plugin.event;

/**
 * An option was clicked and is about to be acted on, before the client sends anything to the
 * server. A handler may call {@link #consume()} to stop it, which is how a plugin blocks an action
 * (a "are you sure" guard on dropping something valuable, say).
 *
 * Consuming does not undo the click - the menu still closes - it only stops the action being
 * performed and the packet being sent.
 */
public final class MenuOptionClicked {

	/** The option's text, tags and all, e.g. "Attack @yel@Man@whi@  (level-2)". */
	public final String option;

	/** The client's action id for the entry. */
	public final int action;

	/** The entry's three parameters; what they mean depends on the action. */
	public final int paramA;
	public final int paramB;
	public final int paramC;

	/**
	 * WHAT WAS CLICKED, already worked out, because the three parameters above do not say.
	 *
	 * paramA is an index into the client's npcs[] for one action and a packed loc id for
	 * another, and which it is depends on a list of action numbers that lives in the client. A
	 * plugin that wanted to know what the player had just clicked had to carry a copy of that
	 * list, which is a copy that silently goes stale when the client's menu changes. The client
	 * resolves it once, where the list belongs, and hands over the answer.
	 */
	public final Target target;

	private boolean consumed;

	/**
	 * The thing a menu row acts on: what kind of thing, which one, and where it is standing.
	 *
	 * An unknown row - a button on an interface, a world-map click - is kind {@link #NONE} with
	 * an id of -1, which is the ordinary answer and not an error.
	 */
	public static final class Target {

		public static final String NONE = "";
		public static final String NPC = "npc";
		public static final String LOC = "loc";
		public static final String OBJ = "obj";
		public static final String WORN = "worn";
		public static final String PLAYER = "player";

		/** One of the constants above. */
		public final String kind;

		/**
		 * Which one: an npc's CONFIG id, a loc's id, an obj's id, a player's index, else -1.
		 *
		 * An npc's config id and not its index in the scene, because that is what a plugin has
		 * to match on - the number the step table, a highlight list or a tag is written in.
		 */
		public final int id;

		/**
		 * The op slot the row came from, 1 to 5, or 0 for a row that has no op word of its own:
		 * "Use <item> with", a spell, Examine, Attack.
		 */
		public final int op;

		/** The scene tile it stands on, or -1 for a thing that is not placed in the world. */
		public final int sceneTileX;
		public final int sceneTileZ;

		public Target(String kind, int id, int op, int sceneTileX, int sceneTileZ) {
			this.kind = kind == null ? NONE : kind;
			this.id = id;
			this.op = op;
			this.sceneTileX = sceneTileX;
			this.sceneTileZ = sceneTileZ;
		}

		public boolean is(String kind) {
			return this.kind.equals(kind);
		}
	}

	private static final Target UNKNOWN = new Target(Target.NONE, -1, 0, -1, -1);

	public MenuOptionClicked(String option, int action, int paramA, int paramB, int paramC) {
		this(option, action, paramA, paramB, paramC, null);
	}

	public MenuOptionClicked(String option, int action, int paramA, int paramB, int paramC, Target target) {
		this.option = option;
		this.action = action;
		this.paramA = paramA;
		this.paramB = paramB;
		this.paramC = paramC;
		this.target = target == null ? UNKNOWN : target;
	}

	/** Stop the client acting on this click. */
	public void consume() {
		this.consumed = true;
	}

	public boolean isConsumed() {
		return this.consumed;
	}
}
