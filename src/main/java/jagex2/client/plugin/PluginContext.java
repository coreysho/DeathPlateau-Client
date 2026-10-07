package jagex2.client.plugin;

import java.util.ArrayList;
import java.util.List;

import jagex2.client.Client;
import jagex2.client.DevLog;
import jagex2.client.GameShell;
import jagex2.client.Stats;
import jagex2.config.ObjType;
import jagex2.dash3d.ClientObj;
import jagex2.dash3d.ClientPlayer;
import jagex2.datastruct.LinkList;

/**
 * What a plugin is allowed to know and do. Every plugin gets one, as {@link Plugin#ctx}.
 *
 * WHY THIS EXISTS RATHER THAN HANDING OVER THE CLIENT. Client.java is 16,000 lines of decompiled
 * code whose fields are called things like field1157, and whose internals move whenever a feature
 * lands. A plugin written against those internals breaks on the next client build and takes the
 * blame with it. This is the seam: a small, named, documented surface the client promises to keep
 * working, with everything behind it free to change. It is the equivalent of RuneLite's api
 * package, at the size this client actually needs.
 *
 * IT IS DELIBERATELY SMALL. Only what the plugins written so far have needed is here. Adding to it
 * is easy and expected - but each addition is a promise to keep, so it is worth adding one method
 * that answers the question a plugin is really asking rather than exposing the field behind it.
 *
 * EVERY GETTER IS SAFE TO CALL AT ANY TIME, including on the login screen and mid-logout. Nothing
 * here throws or returns null for a String; positions read 0 and names read "" when there is no
 * player. A plugin should still check {@link #isLoggedIn()} before doing anything that assumes a
 * world, but forgetting to must not crash it.
 */
public final class PluginContext {

	private final Client client;

	/**
	 * The manager, for the one thing the client cannot answer: that a plugin is still interested
	 * in the tile under the cursor. See {@link #getHoverTileX()}.
	 */
	private PluginManager manager;

	/** Scene units per tile - the client stores entity positions at 128 to the tile. */
	private static final int TILE = 128;

	PluginContext(Client client) {
		this.client = client;
	}

	/**
	 * Set once, by the manager that owns this context.
	 *
	 * Not a constructor argument because the manager builds its context before it has finished
	 * building itself, and handing out a half-made this is the kind of thing that works until
	 * the day something reads a field during construction.
	 */
	void attachManager(PluginManager manager) {
		this.manager = manager;
	}

	// ------------------------------------------------------------------ player and world state

	/** True once in game. False on the login screen, and the moment a logout starts. */
	public boolean isLoggedIn() {
		return this.client.ingame && Client.localPlayer != null;
	}

	/** The local player's display name, or "" before one is known. */
	public String getPlayerName() {
		ClientPlayer self = Client.localPlayer;
		return self == null || self.name == null ? "" : self.name;
	}

	/** Absolute world x of the tile the player is standing on, the coordinate ::tele uses. */
	public int getWorldX() {
		ClientPlayer self = Client.localPlayer;
		return self == null ? 0 : (self.field1157 >> 7) + this.client.sceneBaseTileX;
	}

	/** Absolute world z (south-north) of the tile the player is standing on. */
	public int getWorldZ() {
		ClientPlayer self = Client.localPlayer;
		return self == null ? 0 : (self.field1158 >> 7) + this.client.sceneBaseTileZ;
	}

	/** The plane the player is on, 0 to 3. */
	public int getPlane() {
		return this.client.currentLevel;
	}

	/** Frames a second, as the client's own Fps counter reports it. */
	public int getFps() {
		return this.client.fps;
	}

	// ------------------------------------------------------------------ skills

	/** Number of skills the client knows about. Indices below this are safe to pass around. */
	public int getSkillCount() {
		return Stats.field1503;
	}

	/** Lower case name of a skill, e.g. "woodcutting", or "" for an index out of range. */
	public String getSkillName(int skill) {
		if (skill < 0 || skill >= Stats.field1504.length) {
			return "";
		}
		return Stats.field1504[skill];
	}

	/** Current (boosted or drained) level in a skill. */
	public int getSkillLevel(int skill) {
		return skill < 0 || skill >= this.client.skillLevel.length ? 0 : this.client.skillLevel[skill];
	}

	/** Level the skill's experience alone would give, ignoring boosts. */
	public int getBaseLevel(int skill) {
		return skill < 0 || skill >= this.client.skillBaseLevel.length ? 0 : this.client.skillBaseLevel[skill];
	}

	/** Total experience in a skill. */
	public int getExperience(int skill) {
		return skill < 0 || skill >= this.client.skillExperience.length ? 0 : this.client.skillExperience[skill];
	}

	/**
	 * The icon the stats tab uses for a skill, or null when there is none (a skill the cache has
	 * no art for, or an index out of range).
	 *
	 * Asked for by skill rather than by sheet and index on purpose. Which sprite sheet holds
	 * which skill is knowledge about this cache - researched out of stats.if, and different in
	 * every revision - and it belongs to the client, not to every plugin that wants to draw a
	 * skill.
	 */
	public Sprite getSkillIcon(int skill) {
		return Sprite.of(this.client.skillIcon(skill));
	}

	/**
	 * Total experience needed to BE the given level: 1 is 0, 2 is 83, 99 is 13,034,431.
	 *
	 * ANSWERS UP TO LEVEL 100, not 99. The client's table holds 99 entries, for levels 2 to 100,
	 * because level 100's figure is the experience a full 99 has. Ask for more than 100 and this
	 * returns level 100's figure for all of them - a plugin that wants true levels past the end
	 * of the game has to continue the curve itself. Below 1 is 0.
	 *
	 * The client's own table is offset by two - its entry 0 is the xp for level 2 - which is the
	 * sort of thing that is correct once and then wrong in every plugin that copies it. This is
	 * the question plugins actually ask.
	 */
	public int getExperienceForLevel(int level) {
		if (level <= 1) {
			return 0;
		}
		int[] table = Client.levelExperience;
		int index = level - 2;
		if (index >= table.length) {
			index = table.length - 1;
		}
		return table[index];
	}

	/** Whether the client's own fps counter is on, which an overlay in that corner must dodge. */
	public boolean isFpsShown() {
		return Client.displayFps;
	}

	// ------------------------------------------------------------------ chat

	/**
	 * Puts a game message in the chatbox, as the client's own features do. Nothing is sent to the
	 * server - this is the plugin talking to the player, not the player talking to anyone.
	 */
	public void addChatMessage(String message) {
		if (message != null) {
			this.client.addMessage("", message, 0);
		}
	}

	/**
	 * How long must pass between two notifications, in milliseconds.
	 *
	 * SHARED BY EVERY PLUGIN, not one allowance each. There is a single PluginContext, so there
	 * is nothing here to attribute a call to - and the player does not care which plugin is
	 * shouting, only that something is. One spammy plugin can therefore drown out a well behaved
	 * one for a second and a half at a time, which is a worse answer than per-plugin budgets and
	 * a much better one than a tray balloon every frame.
	 */
	private static final long NOTIFY_EVERY_MS = 1500L;

	/**
	 * And between two sounds. Shorter, because a sound effect IS short and a plugin marking two
	 * things a quarter of a second apart is reasonable where two popups would not be.
	 */
	private static final long SOUND_EVERY_MS = 250L;

	private long lastNotify;
	private long lastSound;

	/**
	 * Tells the player something: a line in the chatbox, and a desktop notification if they are
	 * not looking at the game.
	 *
	 * The chat line always happens. The desktop half only fires when the window is unfocused -
	 * popping a tray balloon at someone who is already watching the screen is how a useful
	 * feature becomes an irritating one - and only if they have left desktop notifications on.
	 *
	 * RATE LIMITED, and silently. A plugin that calls this every tick gets one notification
	 * every {@value #NOTIFY_EVERY_MS}ms and no error, because the alternative is either a
	 * notification every frame or an exception thrown out of a game handler for something that
	 * is not the plugin's mistake so much as its enthusiasm.
	 *
	 * Nothing is sent to the server. This is the plugin talking to the player.
	 */
	public void notify(String message) {
		this.notify(null, message);
	}

	/** The same, with a title for the desktop notification. The chat line is just the message. */
	public void notify(String title, String message) {
		if (message == null || message.length() == 0) {
			return;
		}
		long now = System.currentTimeMillis();
		if (now - this.lastNotify < NOTIFY_EVERY_MS) {
			return;
		}
		this.lastNotify = now;
		this.addChatMessage(message);
		jagex2.client.Notifier.desktop(title, message, this.client.hasFocus);
	}

	/**
	 * Plays one of the game's own sound effects, by its id in the cache.
	 *
	 * VALIDATED BEFORE IT IS QUEUED, and that is not politeness. An id with no sound behind it
	 * makes the client's audio loop throw, and its catch for that reports the failure TO THE
	 * SERVER - so an unchecked id here would be a plugin causing a packet to be sent, which is
	 * the one thing the plugin API does not do. A bad id is dropped and logged instead.
	 *
	 * Rate limited like {@link #notify}, and bounded again by the client's own fifty-sound queue.
	 * Does nothing when the player has sound effects turned off, which is the answer they already
	 * gave to this question.
	 */
	public void playSound(int id) {
		if (!this.client.waveEnabled || Client.lowMem) {
			return;
		}
		if (id < 0 || id >= jagex2.sound.Wave.field1471.length
			|| jagex2.sound.Wave.field1471[id] == null) {
			DevLog.log("PLUGIN", "a plugin asked for sound " + id + ", which this cache has none of");
			return;
		}
		long now = System.currentTimeMillis();
		if (now - this.lastSound < SOUND_EVERY_MS) {
			return;
		}
		this.lastSound = now;
		// The same queue the server's own SYNTH_SOUND fills, and the same cap on it.
		if (this.client.waveCount < this.client.waveIds.length) {
			this.client.waveIds[this.client.waveCount] = id;
			this.client.waveLoops[this.client.waveCount] = 0;
			this.client.waveDelay[this.client.waveCount] = 0;
			this.client.waveCount++;
		}
	}

	/** Whether this cache has a sound under that id, so a plugin can offer only ones that work. */
	public boolean hasSound(int id) {
		return id >= 0 && id < jagex2.sound.Wave.field1471.length
			&& jagex2.sound.Wave.field1471[id] != null;
	}

	// ------------------------------------------------------------------ asking the client to act

	/**
	 * Closes whatever interface is open - a bank, a shop, a dialogue - as the client's own
	 * Escape handling does. Nothing is sent to the server; this is the client putting its own
	 * windows away.
	 */
	public void closeInterfaces() {
		this.client.closeInterfaces();
	}

	/**
	 * How long a click must be held on an item before it starts dragging, in client cycles of
	 * 20ms. Five is what the client does on its own.
	 *
	 * A SETTING, NOT A CALL. The client turns a held click into a drag deep inside its input
	 * loop, in a method no event could usefully fire from, so a plugin says what it wants and
	 * the loop reads it - which also means a plugin that is turned off must put it back, as
	 * AntiDragPlugin does in shutDown(). Values outside 1..100 cycles are clamped: nothing a
	 * plugin passes here may make dragging impossible.
	 */
	public void setDragDelay(int cycles) {
		this.client.pluginDragCycles = cycles < 1 ? 1 : cycles > 100 ? 100 : cycles;
	}

	/** The hold a drag currently needs, in client cycles. */
	public int getDragDelay() {
		return this.client.pluginDragCycles;
	}

	/**
	 * Keeps the green the cache ships the opened Barrows doors in, so the one that will open for
	 * you stands out from the five that will not.
	 *
	 * DELIBERATELY THIS SPECIFIC. The recolour is chosen while a loc type is being DECODED, far
	 * below any frame or event, and the only thing that can be said at that point is yes or no.
	 * A general "recolour this loc" API would be a much bigger promise than the one feature that
	 * wants it, so this says exactly what it does. Changing it throws away the decoded types and
	 * built models, so the next frame builds the doors the new way.
	 */
	public void setBarrowsDoorsHighlighted(boolean highlighted) {
		jagex2.config.LocType.setBarrowsDoorsHighlighted(highlighted);
	}

	// ------------------------------------------------------------------ the right-click menu

	/** Number of entries, including "Cancel" at index 0. */
	public int getMenuSize() {
		return this.client.menuSize;
	}

	/**
	 * An entry's text, tags and all, or "" out of range.
	 *
	 * ENTRIES ARE STORED UPSIDE DOWN. Index 0 is "Cancel" and the LAST entry is the one at the top
	 * of the menu - the one a left click performs. {@link #getLeftClickIndex()} spells that out so
	 * no plugin has to remember it.
	 */
	public String getMenuOption(int index) {
		if (index < 0 || index >= this.client.menuSize) {
			return "";
		}
		String option = this.client.menuOption[index];
		return option == null ? "" : option;
	}

	/** The client's action id for an entry, or -1 out of range. */
	public int getMenuAction(int index) {
		return index < 0 || index >= this.client.menuSize ? -1 : this.client.menuAction[index];
	}

	public int getMenuParamA(int index) {
		return index < 0 || index >= this.client.menuSize ? 0 : this.client.menuParamA[index];
	}

	public int getMenuParamB(int index) {
		return index < 0 || index >= this.client.menuSize ? 0 : this.client.menuParamB[index];
	}

	public int getMenuParamC(int index) {
		return index < 0 || index >= this.client.menuSize ? 0 : this.client.menuParamC[index];
	}

	/**
	 * Whether an entry is the "Walk here" the client offers on any ground you can reach.
	 *
	 * It has no target tag, so it cannot be found by reading the text - and a plugin reordering
	 * the menu needs to know where it is, because "always walk here" is a thing people set.
	 */
	public boolean isWalkHere(int index) {
		return this.getMenuAction(index) == Client.WALK_HERE_ACTION;
	}

	/** Index of the entry a left click would perform, or -1 when there is nothing but Cancel. */
	public int getLeftClickIndex() {
		return this.client.menuSize < 2 ? -1 : this.client.menuSize - 1;
	}

	/** Exchanges two entries, keeping each one's action and parameters with its text. */
	public void swapMenuEntries(int a, int b) {
		int size = this.client.menuSize;
		if (a == b || a < 0 || b < 0 || a >= size || b >= size) {
			return;
		}
		String option = this.client.menuOption[a];
		this.client.menuOption[a] = this.client.menuOption[b];
		this.client.menuOption[b] = option;
		int action = this.client.menuAction[a];
		this.client.menuAction[a] = this.client.menuAction[b];
		this.client.menuAction[b] = action;
		int paramA = this.client.menuParamA[a];
		this.client.menuParamA[a] = this.client.menuParamA[b];
		this.client.menuParamA[b] = paramA;
		int paramB = this.client.menuParamB[a];
		this.client.menuParamB[a] = this.client.menuParamB[b];
		this.client.menuParamB[b] = paramB;
		int paramC = this.client.menuParamC[a];
		this.client.menuParamC[a] = this.client.menuParamC[b];
		this.client.menuParamC[b] = paramC;
	}

	/** Promotes an entry to the left click, leaving the rest of the menu as it was. */
	public void setLeftClick(int index) {
		this.swapMenuEntries(index, this.getLeftClickIndex());
	}

	// ------------------------------------------------------------------ the scene

	/**
	 * What is lying on the ground within so many tiles of the player, a pile per tile.
	 *
	 * The scanning, the walk of each tile's stack and the merging of same-id drops are all done
	 * here, because every one of them is a detail of how this client stores a scene: objStacks
	 * is a three-deep array of LinkList walked tail-first, and a plugin that knew that would
	 * break the day it changed.
	 *
	 * Allocates a pile per occupied tile, so call it once a frame and walk the result rather
	 * than calling it per tile. Returns an empty list when logged out or when the radius is
	 * nonsense.
	 */
	public List<GroundItemPile> getGroundItemPiles(int radius) {
		List<GroundItemPile> piles = new ArrayList<GroundItemPile>();
		ClientPlayer self = Client.localPlayer;
		if (self == null || radius <= 0 || radius > 104) {
			return piles;
		}
		int level = this.client.currentLevel;
		if (this.client.objStacks == null || level < 0 || level >= this.client.objStacks.length) {
			return piles;
		}
		int centreX = self.field1157 >> 7;
		int centreZ = self.field1158 >> 7;
		int minX = Math.max(0, centreX - radius);
		int minZ = Math.max(0, centreZ - radius);
		int maxX = Math.min(103, centreX + radius);
		int maxZ = Math.min(103, centreZ + radius);

		for (int tileX = minX; tileX <= maxX; tileX++) {
			for (int tileZ = minZ; tileZ <= maxZ; tileZ++) {
				LinkList stack = this.client.objStacks[level][tileX][tileZ];
				if (stack == null) {
					continue;
				}
				List<GroundItem> items = this.readStack(stack);
				if (!items.isEmpty()) {
					piles.add(new GroundItemPile(tileX, tileZ, items));
				}
			}
		}
		return piles;
	}

	/**
	 * One tile's stack, merged by item id.
	 *
	 * Walked tail-first to match every other walk of objStacks in the client, which is what puts
	 * the top of the pile first.
	 */
	private List<GroundItem> readStack(LinkList stack) {
		List<GroundItem> items = new ArrayList<GroundItem>();
		for (ClientObj obj = (ClientObj) stack.tail(); obj != null; obj = (ClientObj) stack.prev()) {
			int id = obj.field873;
			int count = obj.field875;
			boolean merged = false;
			for (int i = 0; i < items.size(); i++) {
				if (items.get(i).id == id) {
					GroundItem was = items.get(i);
					items.set(i, new GroundItem(id, was.name, was.count + count, was.price, was.stackable));
					merged = true;
					break;
				}
			}
			if (merged) {
				continue;
			}
			try {
				ObjType type = ObjType.get(id);
				if (type == null || type.field811 == null) {
					continue;              // an item this cache has no name for: nothing to show
				}
				items.add(new GroundItem(id, type.field811, count, type.field827, type.field853));
			} catch (Throwable error) {
				// A decode that failed. One item missing beats an overlay that throws.
			}
		}
		return items;
	}

	// ------------------------------------------------------------------ keys held

	/**
	 * Whether Alt is held down right now.
	 *
	 * Read as a held key rather than from the key queue, because a held key auto-repeats and the
	 * queue cannot tell a genuine press from someone leaning on it.
	 */
	public boolean isAltHeld() {
		return this.client.actionKey[GameShell.KEY_ALT] == 1;
	}

	// ------------------------------------------------------------------ the cursor

	/**
	 * Where the cursor is, in the same viewport coordinates an overlay draws in, or -1 when it
	 * is not over the game at all.
	 *
	 * -1 rather than the last place it was: a plugin drawing something at the cursor should stop
	 * drawing it when the cursor leaves, and "the last known position" is how a tooltip gets
	 * stranded in a corner.
	 */
	public int getMouseX() {
		return this.client.mouseInViewport() ? this.client.viewportMouseX() : -1;
	}

	public int getMouseY() {
		return this.client.mouseInViewport() ? this.client.viewportMouseY() : -1;
	}

	/**
	 * The scene tile the cursor is over, or -1 when it is over none.
	 *
	 * ONE FRAME BEHIND, and it cannot be otherwise. The scene answers "what is at this screen
	 * point" while it draws, so the question has to be asked before a frame and read after it.
	 * At fifty frames a second that is twenty milliseconds, which nobody has ever seen.
	 *
	 * COSTS NOTHING UNTIL IT IS READ. Answering it is a hit test per tile, which the client only
	 * ever paid on a click; reading this is what asks for the next answer, so a plugin that does
	 * not care never makes anyone pay for it. Read it every frame you want it.
	 *
	 * Scene coordinates, the same ones {@link #projectTile} takes - 0 to 103 across the loaded
	 * area, not world coordinates. {@link #sceneToWorldX(int)} converts when something has to be
	 * remembered across a region change.
	 */
	public int getHoverTileX() {
		this.keepHoverAlive();
		return this.client.hoverTileX;
	}

	public int getHoverTileZ() {
		this.keepHoverAlive();
		return this.client.hoverTileZ;
	}

	/** Tells the manager the answer is still wanted. Harmless before one is attached. */
	private void keepHoverAlive() {
		if (this.manager != null) {
			this.manager.hoverTileRead();
		}
	}

	/**
	 * A scene tile as a world coordinate, which is what to save when something must outlive the
	 * loaded area.
	 *
	 * Scene coordinates are relative to whatever chunk of the map is loaded, so the same tile is
	 * a different pair of numbers after walking far enough for the client to reload around you.
	 * A marker saved in scene coordinates comes back pointing somewhere else entirely.
	 */
	public int sceneToWorldX(int sceneTileX) {
		return sceneTileX + this.client.baseX;
	}

	public int sceneToWorldZ(int sceneTileZ) {
		return sceneTileZ + this.client.baseZ;
	}

	/** Whether Shift is held down right now. */
	public boolean isShiftHeld() {
		return this.client.actionKey[GameShell.KEY_SHIFT] == 1;
	}

	// ------------------------------------------------------------------ world to screen

	/** Last projection's x, or -1 when the point was off screen or behind the camera. */
	public int getProjectedX() {
		return this.client.projectX;
	}

	/** Last projection's y. */
	public int getProjectedY() {
		return this.client.projectY;
	}

	/**
	 * Projects the centre of a tile to the viewport, with height measured up from the ground.
	 * Returns false when the point is off screen, in which case there is nothing to draw and
	 * {@link #getProjectedX()} reads -1.
	 *
	 * Tile coordinates are scene-local (0 to 103), not world ones: subtract the scene base, or use
	 * {@link #worldToSceneX(int)}.
	 */
	public boolean projectTile(int sceneTileX, int sceneTileZ, int height) {
		return this.project(sceneTileX * TILE + TILE / 2, sceneTileZ * TILE + TILE / 2, height);
	}

	/** Projects a point in scene units (128 to a tile). */
	public boolean project(int sceneX, int sceneZ, int height) {
		this.client.projectFromGround(sceneX, height, sceneZ);
		return this.client.projectX != -1;
	}

	/** Turns an absolute world x into the scene-local tile x the projection methods want. */
	public int worldToSceneX(int worldX) {
		return worldX - this.client.sceneBaseTileX;
	}

	/** Turns an absolute world z into the scene-local tile z the projection methods want. */
	public int worldToSceneZ(int worldZ) {
		return worldZ - this.client.sceneBaseTileZ;
	}

	/** True when the tile is inside the loaded scene and so can be projected at all. */
	public boolean isInScene(int sceneTileX, int sceneTileZ) {
		return sceneTileX >= 0 && sceneTileX < 104 && sceneTileZ >= 0 && sceneTileZ < 104;
	}
}
