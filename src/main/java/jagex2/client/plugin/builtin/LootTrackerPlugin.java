package jagex2.client.plugin.builtin;

import java.util.List;

import jagex2.client.plugin.Actor;
import jagex2.client.plugin.ConfigItem;
import jagex2.client.plugin.ConfigList;
import jagex2.client.plugin.GroundItem;
import jagex2.client.plugin.GroundItemPile;
import jagex2.client.plugin.Plugin;
import jagex2.client.plugin.PluginDescriptor;
import jagex2.client.plugin.Subscribe;
import jagex2.client.plugin.event.GameTick;

/**
 * What has died near you this session, and what it dropped.
 *
 * THE CLIENT IS NEVER TOLD THAT ANYTHING DIED. RuneLite's loot tracker listens for an npc
 * despawning while dead and takes the items that spawn on its tile; it can do that because the
 * live client publishes both events. Nothing here does. A plugin sees the npcs in the scene and
 * the items on the floor, one snapshot per tick, and that is all - so the kill is INFERRED, by
 * {@link LootTracker}, from an npc that was standing somewhere, is not standing there now, and
 * has left loot behind.
 *
 * WHAT THAT GETS WRONG, said plainly because a tracker that looks precise and is not is worse
 * than one that admits it: a kill made by somebody ELSE next to you is indistinguishable from
 * one of yours, because nothing in the scene says whose it was. In a crowded place this will
 * count kills you had no part in. It does not invent loot out of nothing - an npc that walks
 * away leaves none - and drops it could not attribute to anything are counted separately and
 * shown, rather than quietly left out of the totals.
 *
 * THE ORDER OF THE TICK MATTERS. The npcs are read and the tiles they left worked out BEFORE the
 * floor is scanned, because an npc's removal and its drop usually land in the same tick: do it
 * the other way round and the loot arrives at a tile that has not been marked vacated yet, and
 * every kill goes uncredited.
 *
 * SESSION ONLY. Nothing here is written to disk. No builtin plugin persists anything but its
 * settings today, and a loot history is a bigger thing than a settings file - it wants to
 * survive a crash, to be per character, and to be trimmed. Worth doing, and worth doing on
 * purpose rather than as a side effect of this.
 */
@PluginDescriptor(
	name = "Loot tracker",
	description = "What has died near you, and what it dropped",
	key = "loot-tracker",
	apiLevel = 4
)
public final class LootTrackerPlugin extends Plugin {

	/** The widest the scene goes, and so the most a radius can usefully be. */
	static final int MAX_RADIUS = 104;

	@ConfigItem(keyName = "radius", name = "How far away drops are counted",
		description = "In tiles. Drops further out than this are not seen at all.")
	public int radius = 12;

	@ConfigItem(keyName = "groupBy", name = "List the page by",
		choices = { "Source", "Item" },
		description = "One row per thing that died, or one row per kind of item.")
	public String groupBy = "Source";

	@ConfigItem(keyName = "showUnattributed", name = "Show drops nothing was standing on",
		description = "Loot that landed where nothing had just died, which the totals leave out.")
	public boolean showUnattributed = true;

	private final LootTracker tracker = new LootTracker();

	private final GroundItemArrivals arrivals = new GroundItemArrivals();

	@Override
	protected void startUp() {
		try {
			this.addPanel("Loot", "bag", new ConfigList() {

				public int size() {
					return LootTrackerPlugin.this.rowCount();
				}

				public String label(int index) {
					return LootTrackerPlugin.this.label(index);
				}

				public String detail(int index) {
					return LootTrackerPlugin.this.detail(index);
				}

				public String value(int index) {
					return LootTrackerPlugin.this.value(index);
				}

				public boolean removable(int index) {
					return LootTrackerPlugin.this.bySource() && index < LootTrackerPlugin.this.sources().size();
				}

				public void onRemove(int index) {
					LootTrackerPlugin.this.forget(index);
				}

				public String emptyMessage() {
					return "Nothing has died near you yet.";
				}
			});
		} catch (Throwable olderClient) {
			// No rail pages in this client. The tracker still records; there is just nowhere to
			// show it, which is the same bargain every other panel in here makes.
		}
	}

	@Subscribe
	public void onGameTick(GameTick tick) {
		if (!this.ctx.isLoggedIn()) {
			return;
		}
		// THE NPCS FIRST. See the class comment: the drop and the removal share a tick, so the
		// tile has to be marked vacated before the floor is read or nothing is ever credited.
		this.readNpcs();
		this.readFloor();
	}

	/** Every tile an npc covers right now, in world coordinates. */
	private void readNpcs() {
		this.tracker.beginTick();
		int plane = this.ctx.getPlane();
		List<Actor> npcs = this.ctx.getNpcs();
		for (int i = 0; i < npcs.size(); i++) {
			Actor npc = npcs.get(i);
			if (!npc.isNpc()) {
				continue;
			}
			// A BIG NPC COVERS A SQUARE OF TILES and its drop can land on any of them, so all of
			// them are declared. Its tile is the south-west one, which is the corner the client
			// positions it from.
			int size = npc.size < 1 ? 1 : npc.size;
			for (int dx = 0; dx < size; dx++) {
				for (int dz = 0; dz < size; dz++) {
					this.tracker.standing(LootTracker.tileKey(
						this.ctx.sceneToWorldX(npc.sceneTileX + dx),
						this.ctx.sceneToWorldZ(npc.sceneTileZ + dz),
						plane), npc.name);
				}
			}
		}
		this.tracker.endTick();
	}

	/** Everything that has appeared on the floor since the last look. */
	private void readFloor() {
		int radius = this.radius < 1 ? 1 : this.radius > MAX_RADIUS ? MAX_RADIUS : this.radius;
		int plane = this.ctx.getPlane();
		List<GroundItemPile> piles = this.ctx.getGroundItemPiles(radius);
		this.arrivals.begin();
		for (int i = 0; i < piles.size(); i++) {
			GroundItemPile pile = piles.get(i);
			int worldX = this.ctx.sceneToWorldX(pile.sceneTileX);
			int worldZ = this.ctx.sceneToWorldZ(pile.sceneTileZ);
			for (int j = 0; j < pile.items.size(); j++) {
				GroundItem item = pile.items.get(j);
				if (!this.arrivals.add(GroundItemArrivals.key(worldX, worldZ, plane, item.id))) {
					continue;
				}
				this.tracker.record(LootTracker.tileKey(worldX, worldZ, plane),
					item.id, item.name, item.count, item.price);
			}
		}
		this.arrivals.finish();
	}

	// ------------------------------------------------------------------ the page

	boolean bySource() {
		return !"Item".equals(this.groupBy);
	}

	List<LootTracker.Source> sources() {
		return this.tracker.bySource();
	}

	List<LootTracker.Item> items() {
		return this.tracker.byItem();
	}

	/** The tracker itself, for the test - the plugin is the wiring, the rules are in there. */
	LootTracker tracker() {
		return this.tracker;
	}

	/**
	 * Rows: the loot, then a total, then the drops nothing could be credited for.
	 *
	 * The two trailing rows are part of the list rather than a header because a ConfigList is all
	 * there is - and the unattributed row has to be IN the list, where a player reading the
	 * totals will see it, rather than somewhere it can be missed.
	 */
	int rowCount() {
		int rows = this.bySource() ? this.sources().size() : this.items().size();
		if (rows == 0) {
			return 0;
		}
		return rows + 1 + (this.unattributedRow() ? 1 : 0);
	}

	private boolean unattributedRow() {
		return this.showUnattributed && this.tracker.unattributed() > 0;
	}

	/** Which trailing row this index is: 1 the total, 2 the unattributed one, 0 a loot row. */
	int trailing(int index) {
		int rows = this.bySource() ? this.sources().size() : this.items().size();
		if (index < rows) {
			return 0;
		}
		if (index == rows) {
			return 1;
		}
		return index == rows + 1 && this.unattributedRow() ? 2 : -1;
	}

	String label(int index) {
		int trailing = this.trailing(index);
		if (trailing == 1) {
			return "Total";
		}
		if (trailing == 2) {
			return "Nothing was standing there";
		}
		if (trailing != 0) {
			return "";
		}
		if (this.bySource()) {
			List<LootTracker.Source> sources = this.sources();
			return index < sources.size() ? sources.get(index).name : "";
		}
		List<LootTracker.Item> items = this.items();
		return index < items.size() ? items.get(index).name : "";
	}

	String value(int index) {
		int trailing = this.trailing(index);
		if (trailing == 1) {
			return LootTracker.gp(this.tracker.total());
		}
		if (trailing == 2) {
			return LootTracker.gp(this.tracker.unattributedValue());
		}
		if (trailing != 0) {
			return "";
		}
		if (this.bySource()) {
			List<LootTracker.Source> sources = this.sources();
			return index < sources.size() ? LootTracker.gp(sources.get(index).value) : "";
		}
		List<LootTracker.Item> items = this.items();
		return index < items.size() ? LootTracker.gp(items.get(index).value) : "";
	}

	String detail(int index) {
		int trailing = this.trailing(index);
		if (trailing == 1) {
			return this.tracker.kills() + (this.tracker.kills() == 1 ? " kill" : " kills");
		}
		if (trailing == 2) {
			return this.tracker.unattributed() + " drops, not counted above";
		}
		if (trailing != 0) {
			return "";
		}
		if (this.bySource()) {
			List<LootTracker.Source> sources = this.sources();
			if (index >= sources.size()) {
				return "";
			}
			LootTracker.Source source = sources.get(index);
			LootTracker.Item best = source.best();
			String kills = source.kills + (source.kills == 1 ? " kill" : " kills");
			return best == null ? kills : kills + ", best " + best.name;
		}
		List<LootTracker.Item> items = this.items();
		if (index >= items.size()) {
			return "";
		}
		return LootTracker.commas(items.get(index).count) + " of them";
	}

	void forget(int index) {
		if (!this.bySource()) {
			return;
		}
		List<LootTracker.Source> sources = this.sources();
		if (index >= 0 && index < sources.size()) {
			this.tracker.forget(sources.get(index).name);
		}
	}
}
