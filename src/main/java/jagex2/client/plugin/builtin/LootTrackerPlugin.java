package jagex2.client.plugin.builtin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jagex2.client.plugin.ConfigItem;
import jagex2.client.plugin.ConfigList;
import jagex2.client.plugin.Plugin;
import jagex2.client.plugin.PluginConfig;
import jagex2.client.plugin.PluginDescriptor;
import jagex2.client.plugin.Sprite;
import jagex2.client.plugin.Subscribe;
import jagex2.client.plugin.event.GameStateChanged;
import jagex2.client.plugin.event.GameTick;
import jagex2.client.plugin.event.LootReceived;

/**
 * Everything you have been given, kill by kill: RuneLite's loot tracker.
 *
 * WHERE THE LOOT COMES FROM. The server says what each kill dropped (LootReceived, from a hidden
 * game message the engine sends at the end of the tick the drop is rolled), so a box here is
 * exactly a kill the game credited to you, with exactly what it dropped - not, as this plugin used
 * to be, a guess from an npc vanishing beside a fresh pile on the floor. Npc kills, players you
 * kill, clue caskets and the Barrows chest all arrive the same way.
 *
 * THE PAGE IS RUNELITE'S. A total across the top; then one box per source, the one that paid out
 * last at the top, its kill count in the heading and its value on the right, its drops as the
 * game's own icons with the stack number in them, most valuable first. Click a heading to fold the
 * box away. Hover an item for its value; right-click it to ignore it, which fades it and takes it
 * out of every total. The button on the total switches between grouped boxes and one box per
 * kill, and the last row resets the lot.
 *
 * KEPT BETWEEN SESSIONS, per account, as RuneLite keeps it - in a file of this plugin's own under
 * the client's folder, named by a hash of the account rather than the name itself. Switch it off
 * and each login starts empty, as the session-only tracker did.
 *
 * VALUES ARE THE CACHE'S. There is no Grand Exchange price in this client, so a stack is worth its
 * store value (the cache's cost) or its high alchemy (60% of that), or the tooltip says both.
 */
@PluginDescriptor(
	name = "Loot tracker",
	description = "Every kill, casket and chest, and what it dropped, as RuneLite's loot tracker",
	key = "loot-tracker",
	enabledByDefault = true,
	apiLevel = 14
)
public final class LootTrackerPlugin extends Plugin {

	static final String VIEW_GROUPED = "Grouped";
	static final String VIEW_KILLS = "Per kill";

	static final String PRICE_VALUE = "Store value";
	static final String PRICE_ALCH = "High alch";
	static final String PRICE_BOTH = "Both";

	@ConfigItem(keyName = "view", name = "Show loot",
		choices = { VIEW_GROUPED, VIEW_KILLS },
		description = "One box per source with everything it has dropped, or one box per kill.")
	public String view = VIEW_GROUPED;

	@ConfigItem(keyName = "priceType", name = "Value items by",
		choices = { PRICE_VALUE, PRICE_ALCH, PRICE_BOTH },
		description = "Store value, high alchemy, or both on hover (totals then use store value).")
	public String priceType = PRICE_BOTH;

	@ConfigItem(keyName = "saveLoot", name = "Remember loot between sessions",
		description = "Keep this account's loot when you log out, as RuneLite does.")
	public boolean saveLoot = true;

	@ConfigItem(keyName = "showIgnored", name = "Show ignored items",
		description = "Draw ignored items faded at the end of a box, rather than leaving them out.")
	public boolean showIgnored = true;

	@ConfigItem(keyName = "ignoredItems", name = "Ignored items",
		description = "Item names to leave out of the totals, separated by commas. * matches anything.")
	public String ignoredItems = "";

	@ConfigItem(keyName = "hiddenSources", name = "Hidden sources",
		description = "Names whose boxes are not shown, separated by commas: \"Cow, Chicken\".")
	public String hiddenSources = "";

	@ConfigItem(keyName = "chatValue", name = "Say what each drop is worth",
		description = "A chat line after every kill: \"Your loot is worth around 1,234 coins.\"")
	public boolean chatValue = false;

	final LootTracker tracker = new LootTracker();

	/** The account the history on the page belongs to, "" before one is known. */
	private String account = "";

	/** Unsaved changes. Written within a few ticks, and on logout and shutdown. */
	private boolean dirty;
	private int ticksSinceSave;
	static final int SAVE_EVERY_TICKS = 50;

	/** Boxes folded away, by source key - or by kill time, in the per-kill view. */
	private final Set<String> collapsed = new HashSet<String>();

	/** A reset button pressed once, waiting for the second press: what it was, and when. */
	private String armed;
	private long armedAt;
	static final long CONFIRM_MS = 4000;

	/** The rows as last laid out. Taken in size() so a frame's rows agree with each other. */
	private List<Box> boxes = new ArrayList<Box>();

	@Override
	protected void startUp() {
		this.addPanel("Loot tracker", "bag", new ConfigList() {

			public int size() {
				LootTrackerPlugin.this.boxes = LootTrackerPlugin.this.layOut();
				return LootTrackerPlugin.this.boxes.size();
			}

			public String label(int index) {
				Box box = this.box(index);
				return box == null ? null : box.label;
			}

			public String detail(int index) {
				Box box = this.box(index);
				return box == null ? null : box.detail;
			}

			public String value(int index) {
				Box box = this.box(index);
				return box == null ? null : box.value;
			}

			public String action(int index) {
				Box box = this.box(index);
				return box == null ? null : box.action;
			}

			public void onAction(int index) {
				Box box = this.box(index);
				if (box != null) {
					LootTrackerPlugin.this.press(box);
				}
			}

			public boolean removable(int index) {
				return false;
			}

			public Sprite[] icons(int index) {
				Box box = this.box(index);
				if (box == null || box.items == null || LootTrackerPlugin.this.ctx == null) {
					return null;
				}
				Sprite[] out = new Sprite[box.items.size()];
				for (int i = 0; i < out.length; i++) {
					LootTracker.Item item = box.items.get(i);
					long n = item.count;
					out[i] = LootTrackerPlugin.this.ctx.getObjIcon(item.id, n > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) n);
				}
				return out;
			}

			public String[] iconTips(int index) {
				Box box = this.box(index);
				if (box == null || box.items == null) {
					return null;
				}
				String[] out = new String[box.items.size()];
				for (int i = 0; i < out.length; i++) {
					out[i] = LootTrackerPlugin.this.tip(box.items.get(i));
				}
				return out;
			}

			public boolean[] iconFaded(int index) {
				Box box = this.box(index);
				return box == null ? null : box.faded;
			}

			public String iconAction(int index) {
				Box box = this.box(index);
				return box == null || box.items == null ? null : "Toggle ignore";
			}

			public void onIconAction(int index, int icon) {
				Box box = this.box(index);
				if (box != null && box.items != null && icon >= 0 && icon < box.items.size()) {
					LootTrackerPlugin.this.toggleIgnore(box.items.get(icon).id);
				}
			}

			public boolean clickable(int index) {
				Box box = this.box(index);
				return box != null && box.foldKey != null;
			}

			public void onClick(int index) {
				Box box = this.box(index);
				if (box != null && box.foldKey != null) {
					if (!LootTrackerPlugin.this.collapsed.remove(box.foldKey)) {
						LootTrackerPlugin.this.collapsed.add(box.foldKey);
					}
				}
			}

			public boolean collapsed(int index) {
				Box box = this.box(index);
				return box != null && box.foldKey != null && LootTrackerPlugin.this.collapsed.contains(box.foldKey);
			}

			public String emptyMessage() {
				return "Nothing yet. Kill something, open a casket or loot the Barrows chest, and "
					+ "what it gave you is listed here.";
			}

			private Box box(int index) {
				List<Box> all = LootTrackerPlugin.this.boxes;
				return index >= 0 && index < all.size() ? all.get(index) : null;
			}
		});
	}

	@Override
	protected void shutDown() {
		this.saveNow();
	}

	// ------------------------------------------------------------------ loot coming in

	@Subscribe
	public void onLootReceived(LootReceived event) {
		this.claimAccount();
		List<LootTracker.Item> items = new ArrayList<LootTracker.Item>();
		for (LootReceived.Item item : event.items) {
			items.add(new LootTracker.Item(item.id, item.count));
		}
		LootTracker.Source source = this.tracker.record(event.type, event.name, event.combatLevel,
			System.currentTimeMillis(), items);
		if (source == null) {
			return;
		}
		this.dirty = true;
		if (this.chatValue && this.ctx != null) {
			long worth = LootTracker.total(items, this.prices(), this.ignored());
			this.ctx.addChatMessage("Your loot is worth around " + LootTracker.commas(worth) + " coins.");
		}
	}

	@Subscribe
	public void onGameTick(GameTick tick) {
		this.claimAccount();
		if (this.dirty && ++this.ticksSinceSave >= SAVE_EVERY_TICKS) {
			this.saveNow();
		}
	}

	/**
	 * Logging out writes the history and empties the page: the next person at this client may be
	 * somebody else, and the page is theirs once they log in.
	 */
	@Subscribe
	public void onGameStateChanged(GameStateChanged event) {
		if (event.state == GameStateChanged.LOGIN_SCREEN) {
			this.saveNow();
			this.tracker.reset();
			this.collapsed.clear();
			this.account = "";
		}
	}

	/**
	 * Whose history this is, caught the first tick a name is known, and that account's saved loot
	 * put back. The name is not there yet when the login event fires, which is why it is asked here.
	 */
	private void claimAccount() {
		if (this.ctx == null || !this.ctx.isLoggedIn() || this.account.length() > 0) {
			return;
		}
		String who = this.ctx.getPlayerName();
		if (who == null || who.length() == 0) {
			return;
		}
		this.account = who;
		if (this.saveLoot) {
			// Loot that arrived before the name did - a kill on the very first tick - is kept and
			// added on top of what was saved, rather than thrown away by the load.
			String pending = this.tracker.isEmpty() ? null : this.tracker.save();
			this.tracker.load(this.readData(fileFor(who)));
			if (pending != null) {
				LootTracker extra = new LootTracker();
				extra.load(pending);
				for (int i = extra.kills().size() - 1; i >= 0; i--) {
					LootTracker.Kill kill = extra.kills().get(i);
					this.tracker.record(kill.type, kill.name, kill.level, kill.time, kill.items);
				}
			}
		}
	}

	/** The file an account's history is kept in: a hash of the name, so no name is written down. */
	static String fileFor(String account) {
		String name = account.trim().toLowerCase().replace(' ', '_');
		try {
			java.security.MessageDigest sha = java.security.MessageDigest.getInstance("SHA-256");
			byte[] hash = sha.digest(name.getBytes("UTF-8"));
			StringBuilder out = new StringBuilder("loot-");
			for (int i = 0; i < 8; i++) {
				out.append(String.format("%02x", hash[i] & 0xFF));
			}
			return out.append(".txt").toString();
		} catch (Exception e) {
			return "loot-" + Integer.toHexString(name.hashCode()) + ".txt";
		}
	}

	private void saveNow() {
		this.ticksSinceSave = 0;
		if (!this.dirty) {
			return;
		}
		this.dirty = false;
		if (this.saveLoot && this.account.length() > 0) {
			this.writeData(fileFor(this.account), this.tracker.save());
		}
	}

	// ------------------------------------------------------------------ prices and ignores

	/** What one of an item is worth, by the setting. Totals use store value when it is "Both". */
	LootTracker.Prices prices() {
		final boolean alch = PRICE_ALCH.equals(this.priceType);
		return new LootTracker.Prices() {
			public long value(int objId) {
				long value = LootTrackerPlugin.this.ctx == null ? 0 : LootTrackerPlugin.this.ctx.getObjValue(objId);
				return alch ? value * 6 / 10 : value;
			}
		};
	}

	/** The ignored-items setting, read as RuneLite reads its own: names, any case, * for anything. */
	LootTracker.Ignored ignored() {
		final List<String> patterns = splitList(this.ignoredItems);
		return new LootTracker.Ignored() {
			public boolean is(int objId) {
				if (patterns.isEmpty() || LootTrackerPlugin.this.ctx == null) {
					return false;
				}
				String name = LootTrackerPlugin.this.ctx.getObjName(objId).toLowerCase();
				for (String pattern : patterns) {
					if (matches(pattern, name)) {
						return true;
					}
				}
				return false;
			}
		};
	}

	static List<String> splitList(String text) {
		List<String> out = new ArrayList<String>();
		if (text == null) {
			return out;
		}
		for (String part : text.split(",")) {
			String clean = part.trim().toLowerCase();
			if (clean.length() > 0) {
				out.add(clean);
			}
		}
		return out;
	}

	/** A name against a pattern where * matches any run of characters. Both already lower case. */
	static boolean matches(String pattern, String name) {
		if (pattern.indexOf('*') < 0) {
			return pattern.equals(name);
		}
		String[] parts = pattern.split("\\*", -1);
		int at = 0;
		for (int i = 0; i < parts.length; i++) {
			String part = parts[i];
			if (part.length() == 0) {
				continue;
			}
			int found = name.indexOf(part, at);
			if (found < 0 || i == 0 && found != 0) {
				return false;
			}
			at = found + part.length();
		}
		String last = parts[parts.length - 1];
		return last.length() == 0 || name.endsWith(last);
	}

	/** Ignores an item by name, or stops ignoring it, and saves the setting. */
	void toggleIgnore(int objId) {
		if (this.ctx == null) {
			return;
		}
		String name = this.ctx.getObjName(objId);
		if (name.length() == 0) {
			return;
		}
		List<String> list = splitList(this.ignoredItems);
		String key = name.toLowerCase();
		List<String> kept = new ArrayList<String>();
		boolean removed = false;
		for (String entry : list) {
			if (entry.equals(key)) {
				removed = true;
			} else {
				kept.add(entry);
			}
		}
		StringBuilder out = new StringBuilder();
		for (String entry : this.originalCase(kept)) {
			out.append(out.length() == 0 ? "" : ", ").append(entry);
		}
		if (!removed) {
			out.append(out.length() == 0 ? "" : ", ").append(name);
		}
		this.saveSetting("ignoredItems", out.toString());
	}

	/** The entries as the player typed them, kept in their own capitals. */
	private List<String> originalCase(List<String> lower) {
		List<String> out = new ArrayList<String>();
		for (String part : this.ignoredItems.split(",")) {
			String clean = part.trim();
			if (clean.length() > 0 && lower.contains(clean.toLowerCase())) {
				out.add(clean);
			}
		}
		return out;
	}

	private void saveSetting(String key, String value) {
		if ("ignoredItems".equals(key)) {
			this.ignoredItems = value;
		}
		if (this.config == null) {
			return;
		}
		List<PluginConfig.Item> items = this.config.getItems();
		for (int i = 0; i < items.size(); i++) {
			if (key.equals(items.get(i).key)) {
				this.config.set(items.get(i), value);
				return;
			}
		}
	}

	/** An item's hover: what it is, how many, and what they are worth by the setting. */
	String tip(LootTracker.Item item) {
		if (this.ctx == null) {
			return "";
		}
		String name = this.ctx.getObjName(item.id);
		long each = this.ctx.getObjValue(item.id);
		long alch = each * 6 / 10;
		StringBuilder out = new StringBuilder("<html>");
		out.append(escape(name)).append(" x ").append(LootTracker.commas(item.count));
		if (!PRICE_ALCH.equals(this.priceType)) {
			out.append("<br>Store value: ").append(LootTracker.commas(item.count * each));
			if (item.count > 1) {
				out.append(" (").append(LootTracker.commas(each)).append(" ea)");
			}
		}
		if (!PRICE_VALUE.equals(this.priceType)) {
			out.append("<br>High alch: ").append(LootTracker.commas(item.count * alch));
			if (item.count > 1) {
				out.append(" (").append(LootTracker.commas(alch)).append(" ea)");
			}
		}
		if (this.ignored().is(item.id)) {
			out.append("<br><i>Ignored</i>");
		}
		return out.append("</html>").toString();
	}

	private static String escape(String text) {
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	// ------------------------------------------------------------------ the page

	/** One row of the page. */
	static final class Box {

		String label;
		String detail;
		String value;
		String action;
		/** What the action button does: "view", "reset-all", or a source key to reset. */
		String does;
		/** What folding it remembers it by, or null for a row that does not fold. */
		String foldKey;
		List<LootTracker.Item> items;
		boolean[] faded;
	}

	boolean grouped() {
		return !VIEW_KILLS.equals(this.view);
	}

	/** The rows: the total, the boxes, and the reset. Nothing at all until there is loot. */
	List<Box> layOut() {
		List<Box> out = new ArrayList<Box>();
		if (this.tracker.isEmpty()) {
			return out;
		}
		LootTracker.Prices prices = this.prices();
		LootTracker.Ignored ignored = this.ignored();
		Set<String> hidden = new HashSet<String>(splitList(this.hiddenSources));
		long now = System.currentTimeMillis();
		this.disarmIfStale(now);

		Box total = new Box();
		int kills = this.tracker.totalKills();
		int hiddenCount = 0;
		for (LootTracker.Source source : this.tracker.sources()) {
			if (hidden.contains(source.name.toLowerCase())) {
				hiddenCount++;
			}
		}
		total.label = "Total";
		total.detail = LootTracker.commas(kills) + (kills == 1 ? " kill" : " kills")
			+ (hiddenCount > 0 ? ", " + hiddenCount + " hidden" : "");
		total.value = LootTracker.gp(this.tracker.total(prices, ignored, hidden));
		total.action = this.grouped() ? VIEW_GROUPED : VIEW_KILLS;
		total.does = "view";
		out.add(total);

		if (this.grouped()) {
			for (LootTracker.Source source : this.tracker.sources()) {
				if (hidden.contains(source.name.toLowerCase())) {
					continue;
				}
				Box box = this.itemsBox(source.items(), prices, ignored);
				box.label = title(source.name, source.level) + " x " + LootTracker.commas(source.kills);
				box.value = LootTracker.gp(LootTracker.total(source.items(), prices, ignored));
				box.foldKey = source.key();
				box.does = source.key();
				box.action = source.key().equals(this.armed) ? "Confirm" : "Reset";
				out.add(box);
			}
		} else {
			List<LootTracker.Kill> list = this.tracker.kills();
			for (int i = 0; i < list.size(); i++) {
				LootTracker.Kill kill = list.get(i);
				if (hidden.contains(kill.name.toLowerCase())) {
					continue;
				}
				Box box = this.itemsBox(kill.items, prices, ignored);
				box.label = title(kill.name, kill.level);
				box.detail = LootTracker.ago(kill.time, now) + (box.detail == null ? "" : ", " + box.detail);
				box.value = LootTracker.gp(LootTracker.total(kill.items, prices, ignored));
				box.foldKey = "kill:" + kill.time + ":" + kill.key();
				out.add(box);
			}
		}

		Box reset = new Box();
		reset.label = "Reset all";
		reset.detail = this.saveLoot ? "Every box, and this account's saved loot" : "Every box";
		reset.action = "reset-all".equals(this.armed) ? "Confirm" : "Reset";
		reset.does = "reset-all";
		out.add(reset);
		return out;
	}

	/** A box's grid: its items sorted, the ignored ones faded at the end or left out. */
	private Box itemsBox(List<LootTracker.Item> items, LootTracker.Prices prices, LootTracker.Ignored ignored) {
		Box box = new Box();
		List<LootTracker.Item> sorted = LootTracker.sorted(items, prices, ignored);
		List<LootTracker.Item> shown = new ArrayList<LootTracker.Item>();
		int left = 0;
		for (LootTracker.Item item : sorted) {
			if (ignored.is(item.id) && !this.showIgnored) {
				left++;
				continue;
			}
			shown.add(item);
		}
		box.items = shown;
		box.faded = new boolean[shown.size()];
		for (int i = 0; i < shown.size(); i++) {
			box.faded[i] = ignored.is(shown.get(i).id);
		}
		if (shown.isEmpty() && left == 0) {
			box.detail = "Nothing dropped";
		} else if (left > 0) {
			box.detail = left + (left == 1 ? " ignored item hidden" : " ignored items hidden");
		}
		return box;
	}

	/** "Black dragon (lvl-227)", or the name alone for a casket, a chest or a level of 0. */
	static String title(String name, int level) {
		return level > 0 ? name + " (lvl-" + level + ")" : name;
	}

	/**
	 * A button pressed. The view switch acts at once; a reset asks twice - the first press turns
	 * the button into "Confirm" for a few seconds, and only a second press inside that resets -
	 * because a page with no undo should not be one stray click from empty.
	 */
	void press(Box box) {
		long now = System.currentTimeMillis();
		this.disarmIfStale(now);
		if ("view".equals(box.does)) {
			this.view = this.grouped() ? VIEW_KILLS : VIEW_GROUPED;
			this.saveSetting("view", this.view);
			return;
		}
		if (box.does == null) {
			return;
		}
		if (!box.does.equals(this.armed)) {
			this.armed = box.does;
			this.armedAt = now;
			return;
		}
		this.armed = null;
		if ("reset-all".equals(box.does)) {
			this.tracker.reset();
			this.collapsed.clear();
		} else {
			this.tracker.forget(box.does);
			this.collapsed.remove(box.does);
		}
		this.dirty = true;
		this.saveNow();
	}

	private void disarmIfStale(long now) {
		if (this.armed != null && now - this.armedAt > CONFIRM_MS) {
			this.armed = null;
		}
	}
}
