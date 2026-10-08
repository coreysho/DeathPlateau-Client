package jagex2.client.plugin.builtin;

import java.util.List;

import jagex2.client.plugin.Actor;
import jagex2.client.plugin.ConfigItem;
import jagex2.client.plugin.Overlay;
import jagex2.client.plugin.OverlayGraphics;
import jagex2.client.plugin.Plugin;
import jagex2.client.plugin.PluginDescriptor;

/**
 * Marks the npcs you name, so you can find them in a crowd.
 *
 * The case it earns its keep in is a busy one: a slayer task in a dungeon of six other monsters,
 * an npc you need among the shop crowd in Varrock, a boss whose spawn you want to see the moment
 * it lands. A 2006 client gives you a name on hover and nothing at a glance.
 *
 * MATCHING IS BY SUBSTRING, case-insensitive, on a comma-separated list - so "goblin" finds a
 * Goblin and a Goblin Guard, and you do not have to know which of the two the config calls it.
 * The alternative, exact names, reads as "nothing happened" the first time a player guesses the
 * name slightly wrong, and a highlight that silently does nothing is worse than one that catches
 * a little too much.
 *
 * A SCENE-LAYER OVERLAY, like Tile indicators and for the same reason: it is attached to things
 * in the world, so dragging it could only ever point it somewhere wrong.
 *
 * NOTHING HERE CAN ACT. An {@link Actor} is a snapshot of name, place and size, with no handle on
 * the entity behind it, so this draws and nothing more - no clicking, no following, no attacking.
 * That is the line the whole plugin API is drawn on: a plugin draws and reads, the client owns
 * input and the socket.
 */
@PluginDescriptor(
	name = "Npc indicators",
	description = "Marks the npcs you name, by tile and by name tag",
	key = "npc-indicators",
	apiLevel = 4
)
public final class NpcIndicatorsPlugin extends Plugin {

	/**
	 * How far above the ground the name tag is drawn, in scene units - 128 to a tile, so this is
	 * a little under two tiles up.
	 *
	 * A constant rather than the model's real height, because a plugin cannot see models. Too low
	 * and the tag sits inside the npc; this clears a human-sized one, and a bigger npc gets a
	 * little more from its size below.
	 */
	private static final int TAG_HEIGHT = 230;

	/** Extra height per tile of size, so a big npc's tag clears it too. */
	private static final int TAG_HEIGHT_PER_SIZE = 70;

	/**
	 * How many matching npcs are drawn in one frame.
	 *
	 * A cap because the cost is per-npc projection and text, and a name like "a" would match every
	 * npc in a crowded scene. Nearest first (getNpcs sorts), so the cap drops the far ones, which
	 * are the ones a player is least looking for.
	 */
	private static final int MAX_DRAWN = 32;

	@ConfigItem(keyName = "names",
		name = "Npcs to mark, comma separated (part of a name is enough)")
	public String names = "";

	@ConfigItem(keyName = "colour", name = "Marker colour")
	public String colour = "00FF00";

	@ConfigItem(keyName = "tile", name = "Outline the tiles they stand on")
	public boolean tile = true;

	@ConfigItem(keyName = "tag", name = "Show their name above them")
	public boolean tag = true;

	@ConfigItem(keyName = "level", name = "Include the combat level in the name")
	public boolean level = false;

	protected void startUp() {
		this.addOverlay(new Overlay() {

			public int layer() {
				return Overlay.LAYER_SCENE;
			}

			public void render(OverlayGraphics g) {
				NpcIndicatorsPlugin.this.draw(g);
			}
		});
	}

	private void draw(OverlayGraphics g) {
		if (!this.ctx.isLoggedIn() || (!this.tile && !this.tag)) {
			return;
		}
		// Asked for once per frame, not once per npc: getNpcs walks the scene and allocates.
		List<Actor> npcs = this.ctx.getNpcs();
		int colour = MouseHighlightPlugin.parseColour(this.colour);
		int drawn = 0;
		for (int i = 0; i < npcs.size() && drawn < MAX_DRAWN; i++) {
			Actor npc = npcs.get(i);
			if (!matches(npc.name, this.names)) {
				continue;
			}
			drawn++;
			if (this.tile) {
				// Every tile it occupies, not just the one it is anchored to: a 3x3 boss outlined
				// on its south-west tile looks like a mis-aimed marker rather than a boss.
				for (int dx = 0; dx < npc.size; dx++) {
					for (int dz = 0; dz < npc.size; dz++) {
						TileIndicatorsPlugin.outlineTile(this.ctx, g,
							npc.sceneTileX + dx, npc.sceneTileZ + dz, colour);
					}
				}
			}
			if (this.tag) {
				this.drawTag(g, npc, colour);
			}
		}
	}

	private void drawTag(OverlayGraphics g, Actor npc, int colour) {
		int height = TAG_HEIGHT + (npc.size - 1) * TAG_HEIGHT_PER_SIZE;
		// centreX/centreZ, so a big npc's tag is over the middle of it. project answers false for
		// anything the scene cannot place - behind the camera, or on the outermost tiles, which
		// projectFromGround refuses - and drawing from a failed projection is how an overlay ends
		// up with text pinned in the corner of the screen.
		if (!this.ctx.project(npc.centreX(), npc.centreZ(), height)) {
			return;
		}
		g.textCentred(this.ctx.getProjectedX(), this.ctx.getProjectedY(),
			label(npc, this.level), colour);
	}

	/**
	 * What the tag says. Pure, so the combat-level rule can be checked without a scene.
	 *
	 * A level only when there is one to show: a banker and a shopkeeper have no combat level, and
	 * "Banker (level-0)" is worse than "Banker". This is the same rule the client's own menu rows
	 * follow - see the addNpcOptions comment about a fishing spot reading "(level-2)".
	 */
	static String label(Actor npc, boolean withLevel) {
		if (!withLevel || npc.combatLevel <= 0) {
			return npc.name;
		}
		return npc.name + " (level-" + npc.combatLevel + ")";
	}

	/**
	 * Whether this npc's name matches the player's list.
	 *
	 * Pure and static so the whole rule is testable: comma separated, case-insensitive, each term
	 * trimmed, blank terms ignored, and a term matches when the name contains it. An empty or
	 * blank list matches nothing at all - the plugin ships enabled with no names, and a list that
	 * meant "everything" when empty would outline the entire scene on first run.
	 */
	static boolean matches(String name, String terms) {
		if (name == null || terms == null) {
			return false;
		}
		String lower = name.toLowerCase();
		int from = 0;
		while (from <= terms.length()) {
			int comma = terms.indexOf(',', from);
			String term = (comma < 0 ? terms.substring(from) : terms.substring(from, comma)).trim();
			if (term.length() > 0 && lower.indexOf(term.toLowerCase()) >= 0) {
				return true;
			}
			if (comma < 0) {
				return false;
			}
			from = comma + 1;
		}
		return false;
	}
}
