package jagex2.client.plugin;

import jagex2.client.Client;
import jagex2.client.Stats;
import jagex2.dash3d.ClientPlayer;

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

	/** Scene units per tile - the client stores entity positions at 128 to the tile. */
	private static final int TILE = 128;

	PluginContext(Client client) {
		this.client = client;
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
