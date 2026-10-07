package jagex2.client;

import deob.ObfuscatedName;
import jagex2.config.Component;
import jagex2.config.FloType;
import jagex2.config.IdkType;
import jagex2.config.LocType;
import jagex2.config.NpcType;
import jagex2.config.ObjType;
import jagex2.config.SeqType;
import jagex2.config.SpotAnimType;
import jagex2.config.UnkType;
import jagex2.config.VarbitType;
import jagex2.config.VarpType;
import jagex2.dash3d.AnimFrame;
import jagex2.dash3d.ClientEntity;
import jagex2.dash3d.ClientLocAnim;
import jagex2.dash3d.ClientNpc;
import jagex2.dash3d.ClientObj;
import jagex2.dash3d.ClientPlayer;
import jagex2.dash3d.ClientProj;
import jagex2.dash3d.CollisionMap;
import jagex2.dash3d.Decor;
import jagex2.dash3d.GroundDecor;
import jagex2.dash3d.LocChange;
import jagex2.dash3d.MapSpotAnim;
import jagex2.dash3d.Model;
import jagex2.dash3d.Sprite;
import jagex2.dash3d.Wall;
import jagex2.dash3d.World;
import jagex2.dash3d.World3D;
import jagex2.datastruct.LinkList;
import jagex2.graphics.Pix2D;
import jagex2.graphics.Pix32;
import jagex2.graphics.Pix3D;
import jagex2.graphics.Pix8;
import jagex2.graphics.PixFont;
import jagex2.graphics.PixMap;
import jagex2.io.ClientStream;
import jagex2.io.FileStream;
import jagex2.io.Isaac;
import jagex2.io.Jagfile;
import jagex2.io.OnDemand;
import jagex2.io.OnDemandRequest;
import jagex2.io.Packet;
import jagex2.io.Protocol;
import jagex2.jstring.JString;
import jagex2.sound.AreaSounds;
import jagex2.sound.Wave;
import jagex2.wordenc.WordFilter;
import jagex2.wordenc.ChatText;
import java.applet.AppletContext;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URL;
import java.util.Calendar;
import java.util.Date;
import java.util.zip.CRC32;
import jagex2.client.plugin.PluginManager;
import jagex2.client.plugin.event.SettingsMenuOpening;
import sign.signlink;

public class Client extends GameShell implements PixMap.Target {

	@ObfuscatedName("client.K")
	public int[] jagChecksum = new int[9];

	@ObfuscatedName("client.M")
	public String reportAbuseInput = "";

	@ObfuscatedName("client.P")
	public int[] CHAT_COLOURS = new int[] { 16776960, 16711680, 65280, 65535, 16711935, 16777215 };

	@ObfuscatedName("client.Q")
	public int[] skillExperience = new int[Stats.field1503];

	@ObfuscatedName("client.W")
	public String[] friendName = new String[200];

	@ObfuscatedName("client.Z")
	public int[] cameraModifierWobbleScale = new int[5];

	@ObfuscatedName("client.bb")
	public int macroCameraXModifier = 2;

	@ObfuscatedName("client.ib")
	public String field157 = "";

	@ObfuscatedName("client.kb")
	public String[] field159 = new String[100];

	@ObfuscatedName("client.lb")
	public int[] field160 = new int[100];

	@ObfuscatedName("client.nb")
	public boolean showSocialInput = false;

	@ObfuscatedName("client.Ab")
	public int[][][] sceneMapRegion = new int[4][13][13];

	@ObfuscatedName("client.Gb")
	public int[][] bfsDirection = new int[104][104];

	@ObfuscatedName("client.Hb")
	public int[][] tileLastOccupiedCycle = new int[104][104];

	@ObfuscatedName("client.Rb")
	public Pix32[] imageCross = new Pix32[8];

	@ObfuscatedName("client.Vb")
	public boolean field196 = false;

	@ObfuscatedName("client.mc")
	public int macroMinimapAngleModifier = 2;

	@ObfuscatedName("client.pc")
	public int[] minimapMaskLineLengths = new int[151];

	@ObfuscatedName("client.wc")
	public boolean[] cameraModifierEnabled = new boolean[5];

	@ObfuscatedName("client.yc")
	public Packet login = Packet.alloc(1);

	@ObfuscatedName("client.Ac")
	public int SCROLLBAR_TRACK = 2301979;

	@ObfuscatedName("client.Bc")
	public int projectX = -1;

	@ObfuscatedName("client.Cc")
	public int projectY = -1;

	@ObfuscatedName("client.Ec")
	public int lastWaveLoops = -1;

	@ObfuscatedName("client.Gc")
	public String socialMessage = "";

	@ObfuscatedName("client.Jc")
	public int MAX_CHATS = 50;

	@ObfuscatedName("client.Kc")
	public int[] chatX = new int[this.MAX_CHATS];

	@ObfuscatedName("client.Lc")
	public int[] chatY = new int[this.MAX_CHATS];

	@ObfuscatedName("client.Mc")
	public int[] chatHeight = new int[this.MAX_CHATS];

	@ObfuscatedName("client.Nc")
	public int[] chatWidth = new int[this.MAX_CHATS];

	@ObfuscatedName("client.Oc")
	public int[] chatColour = new int[this.MAX_CHATS];

	@ObfuscatedName("client.Pc")
	public int[] chatEffect = new int[this.MAX_CHATS];

	@ObfuscatedName("client.Qc")
	public int[] chatTimer = new int[this.MAX_CHATS];

	@ObfuscatedName("client.Rc")
	public String[] chatMessage = new String[this.MAX_CHATS];

	@ObfuscatedName("client.Sc")
	public String chatbackInput = "";

	@ObfuscatedName("client.Tc")
	public boolean redrawSideicons = false;

	@ObfuscatedName("client.Xc")
	public Pix32[] imageHeadiconsHint = new Pix32[32];

	@ObfuscatedName("client.ad")
	public String loginMessage0 = "";

	@ObfuscatedName("client.bd")
	public String loginMessage1 = "";

	@ObfuscatedName("client.dd")
	public int fullscreenInterfaceId1 = -1;

	@ObfuscatedName("client.ed")
	public int localPid = -1;

	@ObfuscatedName("client.hd")
	public Packet out = Packet.alloc(1);

	@ObfuscatedName("client.ld")
	public int MAX_PLAYER_COUNT = 2048;

	@ObfuscatedName("client.md")
	public int LOCAL_PLAYER_INDEX = 2047;

	@ObfuscatedName("client.nd")
	public ClientPlayer[] players = new ClientPlayer[this.MAX_PLAYER_COUNT];

	@ObfuscatedName("client.pd")
	public int[] playerIds = new int[this.MAX_PLAYER_COUNT];

	@ObfuscatedName("client.rd")
	public int[] entityUpdateIds = new int[this.MAX_PLAYER_COUNT];

	@ObfuscatedName("client.sd")
	public Packet[] playerAppearanceBuffer = new Packet[this.MAX_PLAYER_COUNT];

	@ObfuscatedName("client.td")
	public Pix8[] imageSideicons = new Pix8[17];

	@ObfuscatedName("client.wd")
	public int[] menuParamB = new int[500];

	@ObfuscatedName("client.xd")
	public int[] menuParamC = new int[500];

	@ObfuscatedName("client.yd")
	public int[] menuAction = new int[500];

	@ObfuscatedName("client.zd")
	public int[] menuParamA = new int[500];

	@ObfuscatedName("client.Fd")
	public int chatInterfaceId = -1;

	@ObfuscatedName("client.Id")
	public int[] cameraModifierWobbleSpeed = new int[5];

	@ObfuscatedName("client.Wd")
	public int[] varCache = new int[2000];

	@ObfuscatedName("client.be")
	public int macroCameraZModifier = 2;

	@ObfuscatedName("client.he")
	public boolean errorStarted = false;

	@ObfuscatedName("client.ke")
	public int[] minimapMaskLineOffsets = new int[151];

	@ObfuscatedName("client.re")
	public String socialInput = "";

	@ObfuscatedName("client.ue")
	public int[] skillLevel = new int[Stats.field1503];

	@ObfuscatedName("client.we")
	public Pix32[] imageMapfunction = new Pix32[100];

	@ObfuscatedName("client.xe")
	public final int[] LOC_SHAPE_TO_LAYER = new int[] { 0, 0, 0, 0, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3 };

	@ObfuscatedName("client.Ee")
	public int[] varps = new int[2000];

	@ObfuscatedName("client.Le")
	public boolean redrawFrame = false;

	@ObfuscatedName("client.Se")
	public int fullscreenInterfaceId0 = -1;

	@ObfuscatedName("client.Te")
	public int[] skillBaseLevel = new int[Stats.field1503];

	@ObfuscatedName("client.ef")
	public boolean menuVisible = false;

	@ObfuscatedName("client.gf")
	public boolean withinTutorialIsland = false;

	@ObfuscatedName("client.jf")
	public String[] playerOps = new String[5];

	@ObfuscatedName("client.kf")
	public boolean[] playerOpPrimary = new boolean[5];

	@ObfuscatedName("client.nf")
	public long[] ignoreName37 = new long[100];

	@ObfuscatedName("client.rf")
	public int[] activeMapFunctionX = new int[1000];

	@ObfuscatedName("client.sf")
	public int[] activeMapFunctionZ = new int[1000];

	@ObfuscatedName("client.tf")
	public Pix32[] imageHeadiconsPrayer = new Pix32[32];

	@ObfuscatedName("client.uf")
	public int SCROLLBAR_GRIP_FOREGROUND = 5063219;

	@ObfuscatedName("client.vf")
	public int[] tabInterfaceId = new int[] { -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1 };

	@ObfuscatedName("client.Cf")
	public CRC32 crc32 = new CRC32();

	@ObfuscatedName("client.Df")
	public int sidebarInterfaceId = -1;

	@ObfuscatedName("client.Ef")
	public int[] waveIds = new int[50];

	@ObfuscatedName("client.Gf")
	public String username = "";

	@ObfuscatedName("client.Hf")
	public String password = "";

	@ObfuscatedName("client.Lf")
	public boolean errorHost = false;

	@ObfuscatedName("client.Mf")
	public boolean reportAbuseMuteOption = false;

	@ObfuscatedName("client.Nf")
	public int[] designColours = new int[5];

	@ObfuscatedName("client.Sf")
	public String chatTyped = "";

	// QoL: recall previously sent chat lines with Page Up / Page Down.
	//
	// Page Up/Down rather than the arrow keys because arrows never reach here: GameShell.keyPressed
	// maps them to ch 1-4 and the key queue is gated on `ch > 4`, so they only ever set actionKey[]
	// for camera rotation (left/right yaw, up/down pitch). Page Up and Page Down are already mapped
	// to 1002/1003, already queued, and referenced nowhere else in the client.
	//
	// history[0] is the newest. browsePos is -1 when not browsing; the line that was half-typed when
	// browsing started is parked in browseDraft so Page Down can put it back.
	// QoL settings panel. F9 opens it; see QolSettings for what the toggles do and how they persist.
	//
	// Drawn into areaViewport rather than over the whole screen, because drawGame() has no single
	// fullscreen buffer - it composes the frame out of separate bound surfaces (viewport, sidebar,
	// chatback, backbase) and blits each one. The panel is laid out in viewport-local coordinates,
	// centred where a main interface would be (layout.mainX/mainY), and the viewport's origin in the
	// mouse's coordinates (layout.vpX/vpY: 4,4 on the fixed screen) is added back when hit-testing.
	private static final int QOL_PANEL_KEY = 1016; // F9
	private static final int QOL_PANEL_W = 320;
	// 14, not the 15 it was: the panel has no paging and has to fit the fixed screen's 334px
	// viewport, and the draw distance row is the twentieth. RoofTest's panelTests() fails the build
	// the moment it stops fitting, so this is the number to shave next time a setting is added.
	private static final int QOL_PANEL_ROW_H = 14;
	private static final int QOL_PANEL_HEADER_H = 24;
	private static final int QOL_PANEL_FOOTER_H = 22;
	private boolean qolPanelOpen;

	// QoL: player-configured left-click swaps. F10 opens the list; see MenuSwaps for what a swap is
	// and how it is stored.
	//
	// WHY IT HOOKS MENU CONSTRUCTION AND NOT THE CLICK. The left-click action is menuOption[
	// menuSize - 1] - the last entry appended, which drawMenu() renders as the TOP row (the array
	// is stored bottom-to-top). drawTooltip() reads that same slot. So moving the chosen entry into
	// it makes the hover text, the left click and the right-click menu order all agree at once,
	// with no second place to keep in step. Redirecting the click instead would leave the tooltip
	// still naming the old default.
	//
	// It runs at the very end of handleInput(), after the client's own >1000 priority sort, so a
	// deliberate swap is not undone by it. That also means it does not run for fullscreen
	// interfaces, which return early - no loss, there is nothing there worth swapping.
	//
	// HOW A SWAP IS MADE: hold Shift and right-click, the way RuneLite does it. The menu that opens
	// lists the same options, each one offering to become the left-click. That works because the
	// thing you are configuring is the thing under the cursor - you never have to arm a mode, go
	// find a target, and remember what you were doing when you get there.

	// Worn options: an item's own options in the Worn Equipment tab, after Remove (ObjType.wearop).
	// Menu actions WEAROP_ACTION..+7 are worn options 1-8; below 1000 so they stay above Examine.
	// WEAROP_TAB is the sidebar slot the equipment tab sits in (content's ^tab_wornitems).
	private static final int WEAROP_ACTION = 600;
	private static final int WEAROP_TAB = 4;
	// "Clear all swaps", above the list. The action rows do NOT scroll: it stays pinned under the
	// header, so the way out of a long list is the first thing under the title rather than something
	// to go and find. Only the swaps below it move.
	// THE SWAPS LIST SCROLLS, which is the only reason MenuSwaps.MAX could go from 16 to 128: the
	// old cap was the panel's height, not the storage. The bar is drawScrollbar() - the same one the
	// bank, the chatbox and every interface list use - so there is one scrollbar in this client and
	// not a second one that looks nearly like it. 16 is the width of its "scrollbar" sprites.
	// The panel is as tall as the open area leaves room for, less this margin top and bottom, so it
	// never sits flush against the edge of the viewport or under the chatbox. That is what makes it
	// work in all three display modes: 334px of open height in FIXED gives 18 rows, and the bigger
	// open area of a resizable window is used rather than wasted (1280x800 classic gives 38).
	// Rows per notch of the wheel. Three, not one: with 128 swaps allowed, a list a player actually
	// filled would take a minute to walk one row at a time.
	/** How far the swaps list is scrolled, in pixels. Zeroed when the panel opens. */
	// handleViewportOptions() gives the "Walk here" entry this action. Swapping to it is how a
	// player says "left-clicking this must not interact with it" - the reason walk-here is offered
	// at all, and why it has to be promoted by action rather than by name: the entry carries no
	// target tag unless another player happens to be standing on the tile.
	public static final int WALK_HERE_ACTION = 14;

	// The plugin system. F8 opens the list of what is installed; see jagex2.client.plugin for the
	// API itself. The manager is null until load() builds it and null again if it could not be
	// built, so every hook below is guarded - a client with no plugins behaves exactly as it did
	// before any of this existed.
	//
	// Same panel shape as the QoL panel, with one difference: the rows are not a fixed list, so
	// it is built from PluginManager.buildPanelRows() each frame and scrolls when it outgrows the
	// screen. (It had three siblings once. The swaps panel and the ground item panel are both
	// plugin config pages now, which is where a panel about one feature belongs.)
	public PluginManager plugins;
	private static final int PLUGIN_PANEL_KEY = 1015; // F8
	private static final int PLUGIN_PANEL_W = 400;
	private static final int PLUGIN_PANEL_ROW_H = 15;
	private static final int PLUGIN_PANEL_HEADER_H = 24;
	private static final int PLUGIN_PANEL_FOOTER_H = 22;
	private static final int PLUGIN_PANEL_MAX_ROWS = 16;
	private boolean pluginPanelOpen;
	private int pluginPanelScroll;
	/** The Swing sidebar, once it exists. Null in the applet, where F8 opens the panel instead. */
	private volatile jagex2.client.plugin.ui.Sidebar pluginSidebar;
	/** Rebuilt by drawPluginPanel() each frame and read by the click handler on the next one. */
	private java.util.List<PluginManager.PanelRow> pluginPanelRows;
	// True while the open right-click menu is a swap menu rather than a real one. Set only inside
	// showContextMenu(), so the every-frame menu that the left click, the tooltip and shift-drop all
	// read is never rewritten - only the copy the player is looking at.
	private boolean menuSwapMode;
	// What choosing each row of that swap menu runs. Parallel to menuOption while menuSwapMode is
	// set, and null for Cancel.
	//
	// It used to be five arrays and a four-valued op, because the client built the rows itself and
	// had to remember which feature each one belonged to. Every row in this menu is a plugin's
	// now - the swaps and the ground item rules both - so a row is its label and the Runnable
	// behind it, and there is nothing left for the client to tell apart.
	private final Runnable[] swapRowAction = new Runnable[500];

	// QoL: the right-click menu scrolls when it is taller than the area it opens in.
	//
	// A pile of thirty drops builds a thirty-row "Choose Option", and the placement code clamps its
	// y to zero and lets the rest run off the bottom edge - where the entries are both invisible and
	// unclickable, because the area's raster clips them. That is Corey's screenshot: a menu whose
	// last dozen Takes cannot be reached at all.
	//
	// So the menu shows a WINDOW of its rows and the wheel moves it. menuScroll counts rows hidden
	// off the TOP, because the array is drawn upside down - index menuSize-1 is the top row (it is
	// the left-click action) and index 0, Cancel, is the bottom one. Cancel can therefore scroll out
	// of sight on a long menu, which costs nothing: clicking anywhere off the menu already cancels,
	// and on a menu this long Cancel was off the bottom of the screen anyway.
	private static final int MENU_ROW_H = 15;
	private static final int MENU_CHROME_H = 22;

	// The 2008 frame (the one with All / Game / Public / Private / Clan / Trade under the chatbox and
	// a door on the logout tab). It is OSRS's fixed layout - interface 548 for the frame and 162 for
	// the chatbox - and tools/models/genframe2008.py pulls its art out of an OSRS cache, with the
	// same coordinates written down there. Against the 377 frame it moves three things:
	//
	//  - the chatbox is a 519x142 parchment at (0,338) instead of 479x96 stone-framed at (17,357).
	//    CHAT_* is the area INSIDE the parchment's border that chat is drawn into; everything that
	//    used to say 479, 96, 77 (the message area above the input line) or 463 (the scrollbar's x)
	//    says it in terms of these now. 377's chat dialogues are 479x96 and are centred in it.
	//  - the side panel is at x 547, not 553, and the minimap at x 545, not 550.
	//  - under the chatbox is a 519x23 row of buttons, not a 496x50 bar of text.
	private static final int CHAT_X = 7;
	private static final int CHAT_Y = 345;
	private static final int CHAT_W = 505;
	private static final int CHAT_H = 129;
	private static final int CHAT_LOG_H = CHAT_H - 19;
	private static final int CHAT_IF_X = (CHAT_W - 479) / 2;
	private static final int CHAT_IF_Y = (CHAT_H - 96) / 2;
	private static final int SIDE_X = 547;
	private static final int MAP_X = 545;
	// The button row. Six filter/mode buttons on a 66px pitch, then Report filling the rest.
	private static final int BAR_Y = 480;
	private static final int BAR_BUTTON_W = 56;
	private static final int BAR_PITCH = 66;
	private static final int BAR_REPORT_X = 5 + BAR_PITCH * 6;
	private static final int BAR_REPORT_W = 113;
	private static final String[] BAR_LABELS = { "All", "Game", "Public", "Private", "Clan", "Trade" };
	// Left edge of each tab's icon in the top row (548's own numbers). The bottom row is the same
	// seven tabs 3px further left, because its area starts at x 519 and the top row's at 516.
	private static final int[] TAB_ICON_X = { 10, 44, 77, 110, 143, 176, 210 };
	// What the chatbox lists: 0 everything, 1 game messages only (type 0). Client-side, not sent.
	private int chatFilter = 0;
	// The bar button under the mouse, 0-5 then 6 for Report, or -1; the bar redraws when it changes.
	private int chatBarHover = -1;

	// CLAN CHAT (engine/clan/ClanChat.ts). The channel this player is in, as the server last sent it
	// (UPDATE_CLANCHANNEL): owner 0 means none. Ranks are 2008's: -1 anyone, 0 friend, 7 owner, 127 staff.
	private long clanOwner37 = 0L;
	private long clanName37 = 0L;
	private int clanKickRank = 7;
	private int clanCount = 0;
	private final long[] clanMemberName37 = new long[100];
	private final int[] clanMemberWorld = new int[100];
	private final int[] clanMemberRank = new int[100];
	// Which clan lines the chatbox shows, like the other three modes but client-side only: 0 On,
	// 1 Friends, 2 Off. Cycled by the Clan button.
	private int chatClanMode = 0;
	// The channel name each chatbox line was said in (message type 11), beside messageSender.
	private final String[] messageChannel = new String[100];
	private String nextMessageChannel = null;
	private int menuScroll;
	private int menuRowsShown;
	// The scroll bar on a long right-click menu. These were the ground item piles' colours first
	// and the plugin still draws its own bar in them, so the two controls go on looking alike -
	// but they are this menu's now, and named for it.
	private static final int MENU_BAR_TRACK = 0x282828;
	private static final int MENU_BAR_THUMB = 0xC8C8C8;
	private static final int MENU_BAR_W = 3;

	private static final int CHAT_HISTORY_MAX = 10;
	private final String[] chatHistory = new String[CHAT_HISTORY_MAX];
	private int chatHistoryCount;
	private int chatHistoryBrowsePos = -1;
	private String chatHistoryBrowseDraft = "";

	@ObfuscatedName("client.Tf")
	public int[] cameraModifierJitter = new int[5];

	@ObfuscatedName("client.Vf")
	public int chatScrollHeight = 78;

	@ObfuscatedName("client.lg")
	public int[] bfsStepX = new int[4000];

	@ObfuscatedName("client.mg")
	public int[] bfsStepZ = new int[4000];

	@ObfuscatedName("client.pg")
	public boolean scrollGrabbed = false;

	@ObfuscatedName("client.sg")
	public long[] friendName37 = new long[200];

	@ObfuscatedName("client.tg")
	public Packet chatPacket = new Packet(new byte[5000]);

	@ObfuscatedName("client.ug")
	public ClientNpc[] npcs = new ClientNpc[16384];

	@ObfuscatedName("client.wg")
	public int[] npcIds = new int[16384];

	@ObfuscatedName("client.xg")
	public int SCROLLBAR_GRIP_HIGHLIGHT = 7759444;

	@ObfuscatedName("client.zg")
	public boolean ingame = false;

	@ObfuscatedName("client.Eg")
	// 0-1 the moderator and administrator crowns; 2-4 the Realism, 5x and 10x badges; 5 the developer
	// crown; 6 the owner's; 7 the second owner's. See ChatIcons.
	public Pix8[] imageModIcons = new Pix8[8];

	// the clan list's rank icons: friend, Recruit .. General, owner, staff (content sprites/clanrank)
	public Pix32[] imageClanRanks = new Pix32[9];

	@ObfuscatedName("client.Gg")
	public boolean designGender = true;

	@ObfuscatedName("client.Hg")
	public int[] cameraModifierCycle = new int[5];

	@ObfuscatedName("client.Pg")
	public Pix8[] imageMapscene = new Pix8[100];

	@ObfuscatedName("client.Rg")
	public boolean objGrabThreshold = false;

	@ObfuscatedName("client.Zg")
	public boolean sceneInstanced = false;

	@ObfuscatedName("client.ch")
	public int[] flameLineOffset = new int[256];

	@ObfuscatedName("client.fh")
	public int viewportInterfaceId = -1;

	@ObfuscatedName("client.qh")
	public int[] compassMaskLineOffsets = new int[33];

	@ObfuscatedName("client.rh")
	public boolean redrawSidebar = false;

	@ObfuscatedName("client.sh")
	public Pix32[] imageHitmarks = new Pix32[20];

	@ObfuscatedName("client.uh")
	public String[] menuOption = new String[500];

	@ObfuscatedName("client.yh")
	// alloc(2) is 30000 bytes, not alloc(1)'s 5000. A variable-length server packet is read whole
	// into this buffer - "this.stream.read(this.in.data, 0, this.psize)" - so the largest packet
	// the server can send has to fit in it or the read walks off the end of the array. An inv
	// update costs up to 7 bytes a slot, so 5000 bytes capped a transmitted inv at 713 slots;
	// the bank is 1410. The server's outgoing buffer (ClientSocket.out) was raised to match.
	public Packet in = Packet.alloc(2);

	@ObfuscatedName("client.zh")
	public int[][] bfsCost = new int[104][104];

	@ObfuscatedName("client.Bh")
	public int stickyChatInterfaceId = -1;

	@ObfuscatedName("client.Th")
	public boolean awaitingSync = false;

	@ObfuscatedName("client.Uh")
	public LinkList spotanims = new LinkList();

	@ObfuscatedName("client.Vh")
	public boolean cutscene = false;

	@ObfuscatedName("client.Wh")
	public boolean redrawPrivacySettings = false;

	@ObfuscatedName("client.Xh")
	public int flashingTab = -1;

	@ObfuscatedName("client.mi")
	public FileStream[] fileStreams = new FileStream[5];

	@ObfuscatedName("client.pi")
	public int reportAbuseInterfaceId = -1;

	@ObfuscatedName("client.si")
	public int macroMinimapZoomModifier = 1;

	@ObfuscatedName("client.xi")
	public boolean pressedContinueOption = false;

	// QoL (Corey, 2026-09-02): last time a bank PIN keypad digit key press was actually sent.
	// The keypad reshuffles after every digit but stays open (no interface-reopen packet), so
	// unlike pressedContinueOption (reset when a new interface opens) this needs a small time
	// debounce instead - otherwise OS key-repeat from a briefly-held digit key would spam the
	// same shuffled button several times before the server's reshuffle reply arrives back.
	public long lastBankPinKeyTime = 0L;

	// QoL (Corey, 2026-09-03): XP drop counter - a floating "+X" indicator with the skill's icon
	// near the top of the viewport whenever a skill's xp increases, matching real OSRS's XP drops
	// (icon + number, no spelled-out skill name). Built entirely client-side off the existing
	// UPDATE_STAT packet (opcode 49, see that handler below) - the server already sends every
	// skill's current level/xp uniformly on every single gain across all skills, so the client
	// just diffs the new xp against what it already had stored and turns any increase into a
	// drop. No server-side changes needed at all.
	// The RuneLite-style xp tracker panel (Corey, 2026-09-05: "just like runelite on osrs ... where
	// you can see the total xp you currently have in the skill and the xp flowing up/down"): the
	// skill of the most recent gain, that skill's new running total, and when the panel hides again.
	// Deliberately separate state from the drop list - the panel shows one running total that
	// survives while individual drops come and go, which is what the earlier inline "+550 (12,345)"
	// version got wrong by welding the total onto every drop row.

	// Root cause of Corey's "character/compass seem off-center compared to normal runescape" report:
	// retail Jagex clients randomize macroCameraX/Z/Angle and macroMinimapAngle/Zoom on login (see
	// setLoginScreen-ish block below) and then let them slowly drift (+-2 units every ~500 cycles,
	// see the macroCameraCycle/macroMinimapCycle blocks) as an anti-macro measure - bots that read
	// fixed on-screen pixel offsets get thrown off by a camera focus point and minimap rotation that
	// silently wobble away from the player's true position/facing over time. That's genuine 2006 RS
	// behavior, but on a private server nobody is screen-scraping bots this way, and it has two
	// visible side effects here: (1) drawScene() adds macroCameraX/Z directly to the camera's focus
	// point (see updateOrbitCamera()), so the player renders up to ~0.4 tiles off from the true
	// viewport center - normally a couple subtle pixels in retail's zoom range, but our custom
	// scroll-wheel zoom (see the "QoL: scroll wheel zoom" comment in drawScene()) lets players zoom
	// in ~5x closer than retail ever allowed, which massively amplifies that same world-space offset
	// into a very obvious on-screen shift (confirmed via pixel measurement: ~28px off-center at a
	// normal view, ~59px off-center zoomed in); (2) the minimap rotates by orbitCameraYaw +
	// macroMinimapAngle while the compass dial rotates by orbitCameraYaw alone (see drawMinimap()),
	// so the compass's indicated bearing can silently disagree with the minimap's rotation by up to
	// ~10 degrees. Disabling the drift (below) keeps all the mechanism's code intact - re-enable by
	// flipping this back to true - but zeroes its effect so the camera always tracks the player
	// exactly and the compass always agrees with the minimap.
	private static final boolean MACRO_ANTIBOT_CAMERA_JITTER = false;

	// QoL (Corey, 2026-09-05: "middle mouse button click and drag to rotate camera is inverted, also
	// its a bit too fast"). Both axes are flipped from the original (yaw was +2, pitch was -2) and the
	// speed drops from 2.0 to 1.5 units per pixel dragged. Kept as numerator/denominator constants so
	// either axis can be flipped back by negating its numerator, and the speed retuned by changing
	// the pair, without touching the maths at the call site in the camera block below.
	private static final int CAMERA_DRAG_YAW_NUM = -3;
	private static final int CAMERA_DRAG_PITCH_NUM = 3;
	private static final int CAMERA_DRAG_DIV = 2;
	// Guards against a spurious "gained thousands of xp!" drop for every skill at login, when the
	// server sends each skill's real current xp for the first time against a freshly-zeroed
	// skillExperience[] array (a fresh Client object has no prior xp to diff against). A skill
	// only starts producing drops after its first real UPDATE_STAT since this client connected.
	private final boolean[] xpDropStatSeen = new boolean[Stats.field1503];

	// Per-stat-id (matching Stats.field1504's order) skill icon, as (sprite sheet, index within
	// that sheet) - read directly out of interfaces/stats.if's own icon components (its "View
	// guide" row layout: each row is a layer with 3 icon graphics paired column-for-column with
	// that column's script1op1=stat_level,<skill> text). 18 of the 21 skills use the "staticons"
	// sheet (indices 0-17); the last row (Runecraft/Slayer/Farming) uses a second sheet,
	// "staticons2" (indices 0-2), confirmed from stats.if directly rather than assumed.
	private static final String[] SKILL_ICON_SHEET = {
		"staticons", "staticons", "staticons", "staticons", "staticons", "staticons", "staticons", // attack, defence, strength, hitpoints, ranged, prayer, magic
		"staticons", "staticons", "staticons", "staticons", "staticons", "staticons", "staticons", // cooking, woodcutting, fletching, fishing, firemaking, crafting, smithing
		"staticons", "staticons", "staticons", "staticons", // mining, herblore, agility, thieving
		"staticons2", "staticons2", "staticons2", // slayer, farming, runecraft
		"staticons2", // construction (stat 21, 2026-09-10) - index 5 of staticons2, same cell stats.if uses
		"staticons2", // hunter (stat 22, 2026-09-22) - index 4, same cell stats.if uses
	};
	private static final int[] SKILL_ICON_INDEX = {
		0, 2, 1, 6, 3, 4, 5, // attack, defence, strength, hitpoints, ranged, prayer, magic
		15, 17, 11, 14, 16, 10, 13, // cooking, woodcutting, fletching, fishing, firemaking, crafting, smithing
		12, 8, 7, 9, // mining, herblore, agility, thieving
		1, 2, 0, // slayer, farming, runecraft
		5, // construction
		// staticons2 ships six drawn icons and 377 only ever referenced four of them. Index 4 is a
		// brown long-tailed creature - Jagex's own Hunter icon, sitting unused in the 377 cache - so
		// Hunter needs no new art. Index 3 (a spade) is still spare.
		4, // hunter
	};

	// QoL (Corey, 2026-09-05): a plain game message (type 0) can carry a rank crown via an inline
	// "@cr1@"/"@cr2@" marker - the same markers the messageSender field already uses for public and
	// private chat lines (see the @cr1@/@cr2@ stripping in the chatbox draw loop). Type 0 has no
	// sender field at all, so ::yell embeds the marker in the message text instead and this draws
	// the crown sprite at exactly the point the marker sits, rather than spelling out "Admin"/"Mod".
	// (2026-09-23) Any number of markers now, anywhere in the line, and the XP-mode badges beside the
	// crowns - the drawing lives in ChatIcons, which also handles the @sh1@/@sh0@ shadow tags.
	//
	// COLOUR (2026-09-21): drawn with drawStringTag rather than drawString, so a game message can carry
	// the same @col@ tags an interface text can - @dre@, @dbl@, @red@, @gre@ and the rest of
	// PixFont.evaluateTag - and the server can colour part of a line. Black stays the default. The
	// width was already measured with stringWidTag, which skips tags, so the crown lands where it did.
	private void drawGameMessage(PixFont font, int y, String text) {
		ChatIcons.draw(font, this.imageModIcons, 4, y, 0, text);
	}


	// The last two rows of the panel are not QolSettings switches: they are the window and the draw
	// distance (DisplaySettings), kept with the launcher's files rather than the cache because they
	// are about this machine's screen and what it can push. Neither is a switch: both step through
	// their values, so each keeps the tick box its neighbours have and spells out what it is on.
	private static final int ROW_WINDOW = QolSettings.COUNT;
	private static final int ROW_DRAW_DISTANCE = QolSettings.COUNT + 1;
	private static final int QOL_PANEL_ROWS = QolSettings.COUNT + 2;

	private int qolPanelHeight() {
		return QOL_PANEL_HEADER_H + QOL_PANEL_ROWS * QOL_PANEL_ROW_H + QOL_PANEL_FOOTER_H;
	}

	private int qolPanelX() {
		return this.layout.mainX + (512 - QOL_PANEL_W) / 2;
	}

	private int qolPanelY() {
		return this.layout.mainY + (334 - this.qolPanelHeight()) / 2;
	}

	/** Called with areaViewport bound, so coordinates here are viewport-local. */
	private void drawQolPanel() {
		int x = this.qolPanelX();
		int y = this.qolPanelY();
		int h = this.qolPanelHeight();

		Pix2D.fillRectTrans(0x000000, y, QOL_PANEL_W, h, 200, x);
		Pix2D.drawRect(y, h, 0x8B7B5A, x, QOL_PANEL_W);

		this.fontBold12.drawString(x + 10, 0xFFB000, y + 17, "Client settings");
		String close = "F9 / Esc to close";
		this.fontPlain12.drawString(x + QOL_PANEL_W - 10 - this.fontPlain12.stringWid(close), 0x9F9F9F, y + 17, close);

		int mouseX = super.mouseX - this.layout.vpX;
		int mouseY = super.mouseY - this.layout.vpY;
		for (int i = 0; i < QOL_PANEL_ROWS; i++) {
			int rowY = y + QOL_PANEL_HEADER_H + i * QOL_PANEL_ROW_H;
			boolean hovered = mouseX >= x + 1 && mouseX < x + QOL_PANEL_W - 1 && mouseY >= rowY && mouseY < rowY + QOL_PANEL_ROW_H;
			if (hovered) {
				Pix2D.fillRectTrans(0xFFFFFF, rowY, QOL_PANEL_W - 2, QOL_PANEL_ROW_H, 30, x + 1);
			}
			int baseline = rowY + QOL_PANEL_ROW_H - 4;
			// The two stepping rows keep the same box the switches have, so the column still lines up;
			// it is ticked when the row is on anything other than the client's old behaviour, and what
			// it is on is spelled out in the label.
			if (i == ROW_DRAW_DISTANCE) {
				int tiles = DisplaySettings.drawDistance();
				boolean far = tiles > DisplaySettings.DRAW_DISTANCES[0];
				this.fontPlain12.drawString(x + 10, far ? 0x00C000 : 0x707070, baseline, far ? "[X]" : "[  ]");
				this.fontPlain12.drawString(x + 36, far ? 0xFFFFFF : 0x909090, baseline, "Draw distance: " + tiles + " tiles");
				continue;
			}
			if (i == ROW_WINDOW) {
				boolean sized = this.wantMode != Layout.FIXED;
				this.fontPlain12.drawString(x + 10, sized ? 0x00C000 : 0x707070, baseline, sized ? "[X]" : "[  ]");
				this.fontPlain12.drawString(x + 36, sized ? 0xFFFFFF : 0x909090, baseline, "Window: " + Layout.modeName(this.wantMode));
				continue;
			}
			boolean on = QolSettings.on(i);
			this.fontPlain12.drawString(x + 10, on ? 0x00C000 : 0x707070, baseline, on ? "[X]" : "[  ]");
			this.fontPlain12.drawString(x + 36, on ? 0xFFFFFF : 0x909090, baseline, QolSettings.label(i));
		}

		String hint = "Click a row to change it. Saved on this computer.";
		this.fontPlain12.drawString(x + 10, 0x9F9F9F, y + h - 8, hint);
	}

	/**
	 * Consumes a click while the panel is open. Returns with the click eaten either way, so a click
	 * meant for a toggle can never also walk the player or open a menu behind the panel.
	 */
	private void handleQolPanelInput() {
		// The server can push an interface at any time (a bank, a trade request). One of those draws
		// over the panel, so stand down rather than keep swallowing input underneath it.
		//
		// A CHATBOX interface does not: it draws in the chatbox, where the panel is not. It used to be
		// in this list, which made F9 useless anywhere the server keeps a chatbox interface up - the
		// whole tutorial, every quest dialogue - because the panel closed itself again a tick after it
		// opened, sometimes between the click landing and the frame that would have read it.
		if (this.viewportInterfaceId != -1 || this.fullscreenInterfaceId0 != -1) {
			this.qolPanelOpen = false;
			return;
		}
		if (super.mouseClickButton == 0) {
			return;
		}
		int x = this.qolPanelX() + this.layout.vpX;
		int y = this.qolPanelY() + this.layout.vpY;
		int clickX = super.mouseClickX;
		int clickY = super.mouseClickY;
		super.mouseClickButton = 0;

		if (clickX < x || clickX >= x + QOL_PANEL_W) {
			return;
		}
		int row = (clickY - (y + QOL_PANEL_HEADER_H)) / QOL_PANEL_ROW_H;
		if (clickY < y + QOL_PANEL_HEADER_H || row < 0 || row >= QOL_PANEL_ROWS) {
			return;
		}
		if (row == ROW_WINDOW) {
			this.setDisplayMode(DisplaySettings.nextMode(this.wantMode));
			DevLog.log("QOL", "Window -> " + Layout.modeName(this.wantMode));
			return;
		}
		if (row == ROW_DRAW_DISTANCE) {
			this.setDrawDistance(DisplaySettings.nextDistance(DisplaySettings.drawDistance()));
			DevLog.log("QOL", "Draw distance -> " + DisplaySettings.drawDistance() + " tiles");
			return;
		}
		QolSettings.toggle(row);
		DevLog.log("QOL", QolSettings.label(row) + " -> " + (QolSettings.on(row) ? "on" : "off"));
	}

	/**
	 * QoL: draws a name (and stack size) over every ground item near the player. Called from
	 * drawScene() with areaViewport bound, so every coordinate here is viewport-local - the same
	 * space projectFromGround() writes projectX/projectY in.
	 *
	 * Stacks of the same obj id on one tile are merged into a single "name x count" row. The server
	 * pushes one ClientObj per drop, so three separate sets of bones on a tile arrive as three
	 * entries; showing them as three identical lines would be noise.
	 */
	/**
	 * Moves the player's preferred entry into the left-click slot. Called at the end of
	 * handleInput(), with the menu fully built and already priority-sorted.
	 */
	/** Rule a beats rule b: an exact target first, then whichever was set earlier. */
	private static boolean better(int a, int b) {
		boolean ea = !MenuSwaps.isAny(a);
		boolean eb = !MenuSwaps.isAny(b);
		return ea != eb ? ea : a < b;
	}

	/**
	 * Rewrites the menu that is about to open into a list of swaps to set. Called from
	 * showContextMenu() when Shift is held, i.e. at the exact moment the player right-clicks - not
	 * during the every-frame build. That matters: the menu built each frame is what the left click,
	 * the tooltip and shift-drop all read, and rewriting it would break all three. Only the copy on
	 * screen is touched, and the next frame rebuilds a normal one.
	 *
	 * Returns false and changes nothing when there is nothing swappable under the cursor, so
	 * shift-right-clicking bare ground behaves exactly as it always did.
	 */
	private boolean buildSwapMenu() {
		if (this.objSelected == 1 || this.spellSelected == 1) {
			// The verb here is "Use <item> with" or a spell name - a one-off, not a preference.
			return false;
		}
		String[] kinds = new String[this.menuSize];
		String[] targets = new String[this.menuSize];
		String[] verbs = new String[this.menuSize];
		int n = 0;
		boolean hasWalk = false;
		for (int i = 1; i < this.menuSize; i++) {          // index 0 is always Cancel
			String option = this.menuOption[i];
			if (this.menuAction[i] == WALK_HERE_ACTION) {
				hasWalk = true;
			}
			int at = MenuSwaps.tagAt(option);
			if (at < 0) {
				continue;                                   // Walk here, interface buttons: no target
			}
			String verb = MenuSwaps.parseVerb(option, at);
			String target = MenuSwaps.parseTarget(option, at);
			if (verb.length() == 0 || target.length() == 0) {
				continue;
			}
			boolean seen = false;
			for (int j = 0; j < n; j++) {
				if (verbs[j].equalsIgnoreCase(verb) && targets[j].equalsIgnoreCase(target)
					&& kinds[j].equals(MenuSwaps.parseKind(option, at))) {
					seen = true;
					break;
				}
			}
			if (!seen) {
				kinds[n] = MenuSwaps.parseKind(option, at);
				targets[n] = target;
				verbs[n] = verb;
				n++;
			}
		}
		if (n == 0) {
			return false;
		}
		this.menuOption[0] = "Cancel";
		this.menuAction[0] = 1016;
		this.swapRowAction[0] = null;       // Cancel: nothing to run, and never dispatched
		int size = 1;
		// Everything below this point is a plugin's. The left-click swaps
		// (jagex2.client.plugin.builtin.MenuSwapperPlugin) and the ground item rules
		// (GroundItemsPlugin) both arrive here, in the order the plugins added them.
		if (this.plugins != null) {
			java.util.List<SettingsMenuOpening.Target> seen =
				new java.util.ArrayList<SettingsMenuOpening.Target>();
			for (int i = 0; i < n; i++) {
				seen.add(new SettingsMenuOpening.Target(kinds[i], targets[i], verbs[i]));
			}
			// Room left, keeping a slot free so a full menu cannot run off the end of the arrays.
			int room = this.menuOption.length - size - 1;
			java.util.List<SettingsMenuOpening.Row> rows =
				this.plugins.onSettingsMenuOpening(seen, hasWalk, room < 0 ? 0 : room);
			for (int i = 0; i < rows.size() && size < this.menuOption.length - 1; i++) {
				this.menuOption[size] = rows.get(i).label;
				this.menuAction[size] = 1016;              // never dispatched; the row carries its own
				this.swapRowAction[size] = rows.get(i).action;
				size++;
			}
		}
		if (size <= 1) {
			// Nothing offered a row - every plugin that would have is switched off. Leave the
			// real menu alone rather than opening a Choose Option with only Cancel in it.
			return false;
		}
		this.menuSize = size;
		this.menuSwapMode = true;
		return true;
	}

	/** Acts on a row of the settings menu. Nothing here ever performs a game action. */
	private void applySwapChoice(int row) {
		if (row <= 0 || row >= this.menuSize) {
			return;                                         // Cancel, or a click that missed
		}
		Runnable action = this.swapRowAction[row];
		if (action == null) {
			return;
		}
		try {
			action.run();
		} catch (Throwable error) {
			// The plugin's own code, on the game thread. One bad row must not take the click
			// handler down with it.
			DevLog.log("PLUGIN", "a settings menu row threw: " + error);
		}
	}


	/**
	 * Builds the Swing plugin sidebar and puts it beside the game, when there is a window to put
	 * it in. In the applet there is not, and F8's in-canvas panel is the whole interface instead.
	 *
	 * Swing components may only be touched on the event dispatch thread and this runs on the game
	 * thread during load(), hence the invokeLater. Failure is not fatal: no sidebar, and the
	 * in-canvas panel takes over, which is why pluginSidebar is only set once one exists.
	 */
	private void attachPluginSidebar() {
		final ViewBox window = super.frame;
		if (window == null) {
			return;
		}
		final PluginManager manager = this.plugins;
		javax.swing.SwingUtilities.invokeLater(new Runnable() {

			public void run() {
				try {
					jagex2.client.plugin.ui.Sidebar sidebar = new jagex2.client.plugin.ui.Sidebar(manager);
					window.setSidebar(sidebar);
					Client.this.pluginSidebar = sidebar;
					DevLog.log("PLUGIN", "sidebar attached");
				} catch (Throwable error) {
					DevLog.log("PLUGIN", "could not build the sidebar, using the in-game panel: " + error);
				}
			}
		});
	}

	/** Shows or hides the sidebar from the game thread. */
	private void togglePluginSidebar() {
		final ViewBox window = super.frame;
		if (window == null) {
			return;
		}
		javax.swing.SwingUtilities.invokeLater(new Runnable() {

			public void run() {
				window.setSidebarVisible(!window.isSidebarVisible());
			}
		});
	}

	private int pluginPanelRowCount() {
		int rows = this.pluginPanelRows == null ? 1 : this.pluginPanelRows.size();
		return rows > PLUGIN_PANEL_MAX_ROWS ? PLUGIN_PANEL_MAX_ROWS : rows;
	}

	private int pluginPanelHeight() {
		return PLUGIN_PANEL_HEADER_H + this.pluginPanelRowCount() * PLUGIN_PANEL_ROW_H + PLUGIN_PANEL_FOOTER_H;
	}

	private int pluginPanelX() {
		return this.layout.mainX + (512 - PLUGIN_PANEL_W) / 2;
	}

	private int pluginPanelY() {
		return this.layout.mainY + (334 - this.pluginPanelHeight()) / 2;
	}

	/**
	 * The plugin list. Called with areaViewport bound, so coordinates here are viewport-local.
	 *
	 * The row list comes from the manager rather than being held here, because it changes as
	 * plugins are toggled: enabling one adds its settings as rows underneath it. It is stashed in
	 * pluginPanelRows so the click handler hit-tests against exactly what was drawn.
	 */
	private void drawPluginPanel() {
		this.pluginPanelRows = this.plugins.buildPanelRows();
		int total = this.pluginPanelRows.size();
		int shown = this.pluginPanelRowCount();
		if (this.pluginPanelScroll > total - shown) {
			this.pluginPanelScroll = total - shown;
		}
		if (this.pluginPanelScroll < 0) {
			this.pluginPanelScroll = 0;
		}

		int x = this.pluginPanelX();
		int y = this.pluginPanelY();
		int h = this.pluginPanelHeight();

		Pix2D.fillRectTrans(0x000000, y, PLUGIN_PANEL_W, h, 200, x);
		Pix2D.drawRect(y, h, 0x8B7B5A, x, PLUGIN_PANEL_W);

		this.fontBold12.drawString(x + 10, 0xFFB000, y + 17, "Plugins");
		String close = "F8 / Esc to close";
		this.fontPlain12.drawString(x + PLUGIN_PANEL_W - 10 - this.fontPlain12.stringWid(close), 0x9F9F9F, y + 17, close);

		int mouseX = super.mouseX - this.layout.vpX;
		int mouseY = super.mouseY - this.layout.vpY;
		String hint = total > shown
			? "Scroll for more. Jars go in .deathplateau/plugins in your home folder."
			: "Click a row to toggle. Jars go in .deathplateau/plugins in your home folder.";

		for (int i = 0; i < shown; i++) {
			PluginManager.PanelRow row = this.pluginPanelRows.get(i + this.pluginPanelScroll);
			int rowY = y + PLUGIN_PANEL_HEADER_H + i * PLUGIN_PANEL_ROW_H;
			boolean hovered = mouseX >= x + 1 && mouseX < x + PLUGIN_PANEL_W - 1
				&& mouseY >= rowY && mouseY < rowY + PLUGIN_PANEL_ROW_H;
			if (hovered && row.isClickable()) {
				Pix2D.fillRectTrans(0xFFFFFF, rowY, PLUGIN_PANEL_W - 2, PLUGIN_PANEL_ROW_H, 30, x + 1);
				if (row.hint != null && row.hint.length() > 0) {
					hint = row.hint;
				}
			}
			int baseline = rowY + PLUGIN_PANEL_ROW_H - 4;
			if (row.showCheckbox) {
				this.fontPlain12.drawString(x + 10, row.checked ? 0x00C000 : 0x707070, baseline, row.checked ? "[X]" : "[  ]");
			}
			int colour = row.kind == PluginManager.PanelRow.KIND_ACTION ? 0xFFB000
				: row.kind == PluginManager.PanelRow.KIND_TEXT ? 0x9F9F9F
				: row.checked ? 0xFFFFFF : 0x909090;
			this.fontPlain12.drawString(x + 36, colour, baseline, row.label);
		}

		this.fontPlain12.drawString(x + 10, 0x9F9F9F, y + h - 8, hint);
	}

	/** Consumes a click while the plugin panel is open, on the same terms as the other three. */
	private void handlePluginPanelInput() {
		if (this.viewportInterfaceId != -1 || this.fullscreenInterfaceId0 != -1 || this.chatInterfaceId != -1) {
			this.pluginPanelOpen = false;
			return;
		}
		// The wheel scrolls the list while the panel is open, and is consumed so it cannot also
		// zoom the camera behind it. Clamped in drawPluginPanel(), which is the only place that
		// knows how many rows there are to scroll through.
		if (super.mouseScrollDelta != 0) {
			this.pluginPanelScroll += super.mouseScrollDelta;
			super.mouseScrollDelta = 0;
		}
		if (super.mouseClickButton == 0) {
			return;
		}
		// Drawn before input is handled, so a first frame with no rows yet means there is nothing
		// to hit-test against - the click is still eaten, as it is for every other panel.
		java.util.List<PluginManager.PanelRow> rows = this.pluginPanelRows;
		int x = this.pluginPanelX() + this.layout.vpX;
		int y = this.pluginPanelY() + this.layout.vpY;
		int clickX = super.mouseClickX;
		int clickY = super.mouseClickY;
		super.mouseClickButton = 0;

		if (rows == null || clickX < x || clickX >= x + PLUGIN_PANEL_W) {
			return;
		}
		int row = (clickY - (y + PLUGIN_PANEL_HEADER_H)) / PLUGIN_PANEL_ROW_H + this.pluginPanelScroll;
		if (clickY < y + PLUGIN_PANEL_HEADER_H || row < this.pluginPanelScroll
			|| row >= this.pluginPanelScroll + this.pluginPanelRowCount() || row >= rows.size()) {
			return;
		}
		this.plugins.clickRow(rows.get(row));
	}



	/**
	 * Rows a menu can show in an area this tall: never fewer than one, never more than it has.
	 */
	private int menuRowsFor(int areaHeight) {
		int rows = (areaHeight - MENU_CHROME_H) / MENU_ROW_H;
		if (rows < 1) {
			rows = 1;
		}
		if (rows > this.menuSize) {
			rows = this.menuSize;
		}
		return rows;
	}

	/**
	 * The menuOption index drawn at visual position p, counting p from the TOP row. The array is
	 * drawn upside down, so scrolling down the menu walks DOWN the indices.
	 */
	private int menuRowIndex(int p) {
		return this.menuSize - 1 - p - this.menuScroll;
	}

	/** The baseline y of visual position p, in the menu's own area coordinates. */
	private int menuRowY(int p) {
		return this.menuY + 31 + p * MENU_ROW_H;
	}

	/**
	 * QoL: the wheel scrolls an open menu that is taller than its area. Consumed either way while a
	 * menu is up - a wheel turn with a menu open is aimed at the menu, and zooming the camera behind
	 * it would move the world the menu's entries refer to.
	 */
	private boolean handleMenuScroll() {
		if (super.mouseScrollDelta == 0 || !this.menuVisible) {
			return false;
		}
		int max = this.menuSize - this.menuRowsShown;
		if (max > 0) {
			this.menuScroll += super.mouseScrollDelta;
			if (this.menuScroll > max) {
				this.menuScroll = max;
			}
			if (this.menuScroll < 0) {
				this.menuScroll = 0;
			}
			if (this.menuArea == 1) {
				this.redrawSidebar = true;
			}
			if (this.menuArea == 2) {
				this.redrawChatback = true;
			}
		}
		super.mouseScrollDelta = 0;
		return true;
	}


	/**
	 * The stats tab's icon for a skill, or null if there is none. Public because the XP drops
	 * plugin asks for it through PluginContext.getSkillIcon - the tables above are knowledge
	 * about this cache, so they stay here rather than being copied into a plugin.
	 */
	public Pix32 skillIcon(int skillId) {
		if (skillId < 0 || skillId >= SKILL_ICON_SHEET.length) {
			return null;
		}
		try {
			return Component.getImage(SKILL_ICON_INDEX[skillId], SKILL_ICON_SHEET[skillId]);
		} catch (Throwable error) {
			// No image cache yet - asked before the interfaces loaded, or they failed to. "No
			// icon" is the right answer and the callers all draw nothing for null; throwing here
			// would instead kill whichever overlay asked, every frame, until it was disabled.
			// Not logged: this is called once per drop per frame and would flood the log.
			return null;
		}
	}



	@ObfuscatedName("client.yi")
	public boolean redrawChatback = false;

	@ObfuscatedName("client.Bi")
	public volatile boolean flameActive = false;

	@ObfuscatedName("client.Di")
	public byte[] textureBuffer = new byte[16384];

	@ObfuscatedName("client.Hi")
	public Component chatInterface = new Component();

	@ObfuscatedName("client.Ji")
	public int orbitCameraPitch = 128;

	@ObfuscatedName("client.Oi")
	public int macroCameraAngleModifier = 1;

	@ObfuscatedName("client.Qi")
	public int[] messageIds = new int[100];

	@ObfuscatedName("client.Ri")
	public int[] waveDelay = new int[50];

	@ObfuscatedName("client.Si")
	public CollisionMap[] levelCollisionMap = new CollisionMap[4];

	@ObfuscatedName("client.Ti")
	public LinkList locChanges = new LinkList();

	@ObfuscatedName("client.Xi")
	public boolean movingCamera = false;

	@ObfuscatedName("client.Yi")
	public boolean midiActive = true;

	@ObfuscatedName("client.Zi")
	public int[] friendWorld = new int[200];

	@ObfuscatedName("client.dj")
	public boolean midiFading = true;

	@ObfuscatedName("client.ej")
	public int lastWaveId = -1;

	@ObfuscatedName("client.hj")
	public boolean field571 = true;

	@ObfuscatedName("client.ij")
	public int minimapLevel = -1;

	@ObfuscatedName("client.jj")
	public boolean updateDesignModel = false;

	@ObfuscatedName("client.kj")
	public Pix32[] activeMapFunctions = new Pix32[1000];

	@ObfuscatedName("client.lj")
	public int viewportOverlayInterfaceId = -1;

	@ObfuscatedName("client.oj")
	public LinkList projectiles = new LinkList();

	@ObfuscatedName("client.pj")
	public boolean errorLoading = false;

	@ObfuscatedName("client.rj")
	public int selectedTab = 3;

	@ObfuscatedName("client.sj")
	public int[] compassMaskLineLengths = new int[33];

	@ObfuscatedName("client.tj")
	public int SCROLLBAR_GRIP_LOWLIGHT = 3353893;

	@ObfuscatedName("client.uj")
	public Pix32[] imageHeadiconsPk = new Pix32[32];

	@ObfuscatedName("client.wj")
	/**
	 * The textures that scroll, and how fast, as {id, speed} pairs.
	 *
	 * The four Jagex shipped are not an arbitrary list: the OSRS cache still marks textures 17, 24,
	 * 34 and 40 as animated in direction 1 at speed 2, which is exactly this set and exactly the
	 * rate the loop below used to hardcode. Texture 50 is this fork's - OSRS texture 59, the
	 * Infernal cape's molten crust - and the cache gives it the same direction at speed 1, so the
	 * speed had to stop being a literal.
	 */
	public int[][] ANIMATED_TEXTURES = new int[][] { { 17, 2 }, { 24, 2 }, { 34, 2 }, { 40, 2 }, { 50, 1 } };

	@ObfuscatedName("client.Bj")
	public int[] entityRemovalIds = new int[1000];

	@ObfuscatedName("client.Cj")
	public int[] messageType = new int[100];

	@ObfuscatedName("client.Dj")
	public String[] messageSender = new String[100];

	@ObfuscatedName("client.Ej")
	public String[] messageText = new String[100];

	// P_DIALOGPROMPT (custom, 2026-09-21): the question the server wants the next count or name dialog
	// to ask, instead of "Enter amount:" / "Enter name:". It arrives just before the dialog opens, is
	// taken by that dialog, and so lasts exactly one - a later plain p_countdialog asks the usual way.
	private String pendingDialogPrompt = null;
	private String dialogPrompt = null;

	// a wrapped message's second and later lines (see wrapChat), and where their text starts
	public boolean[] messageCont = new boolean[100];
	public int[] messageIndent = new int[100];

	@ObfuscatedName("client.Hj")
	public boolean waveEnabled = true;

	// 474's area sounds (jagex2/sound/AreaSounds.java) and their volume, 0 to 127, from the Options
	// tab's fourth slider (a varp with clientcode 10)
	public AreaSounds areaSounds = new AreaSounds();
	public int areaSoundVolume = 127;

	// The spellbook the server says you are on (%spellbook, varp clientcode 11): 0 normal, 1 Ancient
	// Magicks, 2 Lunar. The magic tab draws sideicons 15 or 16 for the last two.
	public int spellbookIcon = 0;

	@ObfuscatedName("client.Uj")
	public volatile boolean flameActive0 = false;

	@ObfuscatedName("client.ak")
	public volatile boolean flameThread = false;

	@ObfuscatedName("client.bk")
	public int[] waveLoops = new int[50];

	@ObfuscatedName("client.dk")
	public LinkList[][][] objStacks = new LinkList[4][104][104];

	@ObfuscatedName("client.gk")
	public int[] designKits = new int[7];

	@ObfuscatedName("client.hk")
	public int nextMidiSong = -1;

	@ObfuscatedName("client.N")
	// The public half of the server's login RSA key (engine: `npm run rsa`, data/config/login-rsa.pem).
	// Rotated 2026-09-27: the old 512-bit key's private half was public on GitHub. Change these only
	// together with the server's key AND the p2(...) build number in login() below.
	public static BigInteger LOGIN_RSAN = new BigInteger("153884090520538728936878302027910202920542948295897114523288142356390821628154839790201213541418821317049841112218678628942025501794640330604458662944406617266109433307208963311978451516363571029097291301242557629716636019046289026568724861317137936845248862778762767562566708241027964593626362603622200050473");

	@ObfuscatedName("client.sc")
	public static int nodeId = 10;

	// The server's name and slogan, wherever the client says them: the window, the login screen and
	// the loading and error messages. (The logo is an image, content/title/logo.png.)
	public static final String SERVER_NAME = "Death Plateau";
	public static final String SLOGAN = "The true golden era.";
	public static final int SLOGAN_COLOUR = 0xE8C35A;

	// Server address the standalone client connects to. Override at launch with -Dlostcity.host /
	// lostcity.port / lostcity.webhost / lostcity.webport (or the LOSTCITY_HOST, LOSTCITY_PORT,
	// LOSTCITY_WEBHOST, LOSTCITY_WEBPORT env vars), without touching source.
	// (2026-09-27) The public way in is a playit.gg tunnel in front of the home server, so the home IP is
	// never handed out: one tunnel for the game (TCP 43594 on the server) and one for the web/cache
	// server (TCP 8888), each with its own host and port. Point lostcity.host at the server directly
	// (e.g. -Dlostcity.host=192.168.4.97 on the LAN) and both ports fall back to the server's own
	// 43594 and 8888, and the web host follows the game host, unless they are given too.
	private static String setting(String property, String env) {
		String value = System.getProperty(property);
		return value != null ? value : System.getenv(env);
	}
	private static final boolean HOST_GIVEN = setting("lostcity.host", "LOSTCITY_HOST") != null;
	public static String SERVER_HOST = HOST_GIVEN ? setting("lostcity.host", "LOSTCITY_HOST") : "carolyn-scientist.tun.ply.gg";
	public static int GAME_PORT = Integer.parseInt(setting("lostcity.port", "LOSTCITY_PORT") != null ? setting("lostcity.port", "LOSTCITY_PORT") : (HOST_GIVEN ? "43594" : "53562"));
	public static String WEB_HOST = setting("lostcity.webhost", "LOSTCITY_WEBHOST") != null ? setting("lostcity.webhost", "LOSTCITY_WEBHOST") : (HOST_GIVEN ? SERVER_HOST : "death-plateau.playit.plus");
	public static int WEB_PORT = Integer.parseInt(setting("lostcity.webport", "LOSTCITY_WEBPORT") != null ? setting("lostcity.webport", "LOSTCITY_WEBPORT") : (HOST_GIVEN ? "8888" : "80"));
	// IN A BROWSER THERE IS NO TCP. lostcity.ws is set only by the page that runs this client under
	// CheerpJ (Engine-TS serves it at /rs2.cgi), and it names one WebSocket URL - the server's web
	// port, which already carries both streams, because the first byte a client sends is what tells
	// the server whether it is a login or an update connection. Unset anywhere else, so the desktop
	// client below is the client it has always been.
	public static final String WS_URL = setting("lostcity.ws", "LOSTCITY_WS");
	// THE WHOLE URL THE CACHE COMES FROM, scheme and all. WEB_HOST/WEB_PORT below build an http://
	// one, which is right for a plain tunnel and wrong the moment the server is behind TLS: a page
	// served over https cannot fetch http, the browser blocks it as mixed content, and the client
	// sits on "Loading title screen" with nothing to say why. The page passes its own origin here
	// (Engine-TS serves it), so the browser client follows whatever address it was opened on; a
	// desktop launch can set -Dlostcity.weburl=https://... for the same reason.
	// World 1's web address, and the default only when nothing more specific was given. http, not
	// https: playit does not terminate TLS - the tunnel hands 443 straight to the origin, and the
	// origin is this server's plain HTTP. Putting a certificate in front of it is a Caddy away and
	// changes one word here, which is the point of taking a whole URL rather than a host and port.
	// The rest of it: a dev-world
	// launch passes lostcity.webhost/webport (the launcher does, for its own tunnel) and a LAN launch
	// passes lostcity.host, and either of those has to win - a dev client fetching World 1's cache is
	// the stale-config trap signlink's storeid comment describes.
	private static final boolean WEBHOST_GIVEN = setting("lostcity.webhost", "LOSTCITY_WEBHOST") != null
		|| setting("lostcity.webport", "LOSTCITY_WEBPORT") != null || HOST_GIVEN;
	public static final String WEB_URL = setting("lostcity.weburl", "LOSTCITY_WEBURL") != null
		? setting("lostcity.weburl", "LOSTCITY_WEBURL")
		: (WEBHOST_GIVEN ? null : "http://death-plateau.playit.plus");

	// --- QoL additions (Corey, 2026-09-01): Tab-to-reply, space-to-continue, Escape-to-close,
	// middle-mouse camera drag, scroll-wheel zoom, shift-click drop. See handleInputKey(),
	// handleMouseInput(), updateOrbitCamera() and drawScene() for where these are used.
	public long lastPmFrom37;
	public boolean hasLastPmFrom;
	public int cameraZoomOffset;

	@ObfuscatedName("client.uc")
	public static boolean membersWorld = true;

	@ObfuscatedName("client.Vc")
	public static int[] levelExperience = new int[99];

	@ObfuscatedName("client.Yd")
	public static String CHARSET;

	@ObfuscatedName("client.Zd")
	public static final int[][] DESIGN_BODY_COLOUR;

	@ObfuscatedName("client.Yh")
	public static int[] VARBIT_MASKS;

	@ObfuscatedName("client.aj")
	public static final int[] DESIGN_HAIR_COLOUR;

	@ObfuscatedName("client.Wj")
	public static BigInteger LOGIN_RSAE;

	@ObfuscatedName("client.O")
	public static int oplogic6;

	@ObfuscatedName("client.R")
	public int hintTileX;

	@ObfuscatedName("client.S")
	public int hintTileZ;

	@ObfuscatedName("client.T")
	public int hintHeight;

	@ObfuscatedName("client.U")
	public int hintOffsetX;

	@ObfuscatedName("client.V")
	public int hintOffsetZ;

	@ObfuscatedName("client.X")
	public int loginRetryCount;

	@ObfuscatedName("client.Y")
	public int chatScrollOffset;

	@ObfuscatedName("client.ab")
	public int macroCameraX;

	@ObfuscatedName("client.cb")
	public int ignoreCount;

	@ObfuscatedName("client.gb")
	public int friendCount;

	@ObfuscatedName("client.hb")
	public int friendlistStatus;

	@ObfuscatedName("client.jb")
	public int field158;

	@ObfuscatedName("client.mb")
	public int field161;

	@ObfuscatedName("client.ob")
	public int staffmodlevel;

	@ObfuscatedName("client.qb")
	public int psize;

	@ObfuscatedName("client.rb")
	public int ptype;

	@ObfuscatedName("client.sb")
	public int idleNetCycles;

	@ObfuscatedName("client.tb")
	public int noTimeoutCycle;

	@ObfuscatedName("client.ub")
	public int idleTimeout;

	@ObfuscatedName("client.vb")
	public int cutsceneSrcLocalTileX;

	@ObfuscatedName("client.wb")
	public int cutsceneSrcLocalTileZ;

	@ObfuscatedName("client.xb")
	public int cutsceneSrcHeight;

	@ObfuscatedName("client.yb")
	public int cutsceneMoveSpeed;

	@ObfuscatedName("client.zb")
	public int cutsceneMoveAcceleration;

	@ObfuscatedName("client.Ib")
	public int chatPrivateMode;

	@ObfuscatedName("client.Kb")
	public int sceneCenterZoneX;

	@ObfuscatedName("client.Lb")
	public int sceneCenterZoneZ;

	@ObfuscatedName("client.Ob")
	public int field189;

	@ObfuscatedName("client.Qb")
	public static int cyclelogic3;

	@ObfuscatedName("client.Yb")
	public int ptype0;

	@ObfuscatedName("client.Zb")
	public int ptype1;

	@ObfuscatedName("client.ac")
	public int ptype2;

	@ObfuscatedName("client.kc")
	public int lastHoveredInterfaceId;

	@ObfuscatedName("client.lc")
	public int macroMinimapAngle;

	@ObfuscatedName("client.tc")
	public static int portOffset;

	@ObfuscatedName("client.Ic")
	public int chatCount;

	@ObfuscatedName("client.Uc")
	public int sceneDelta;

	@ObfuscatedName("client.Yc")
	public int bankArrangeMode;

	@ObfuscatedName("client.od")
	public int playerCount;

	@ObfuscatedName("client.qd")
	public int entityUpdateCount;

	@ObfuscatedName("client.ud")
	public int titleLoginField;

	@ObfuscatedName("client.vd")
	public static int cyclelogic1;

	@ObfuscatedName("client.Gd")
	public int baseX;

	@ObfuscatedName("client.Hd")
	public int baseZ;

	@ObfuscatedName("client.Jd")
	public int daysOfMembersRemaining;

	@ObfuscatedName("client.Kd")
	public int cutsceneDstLocalTileX;

	@ObfuscatedName("client.Ld")
	public int cutsceneDstLocalTileZ;

	@ObfuscatedName("client.Md")
	public int cutsceneDstHeight;

	@ObfuscatedName("client.Nd")
	public int field292;

	@ObfuscatedName("client.Od")
	public int field293;

	@ObfuscatedName("client.Pd")
	public int chatEffects;

	@ObfuscatedName("client.Vd")
	public int field300;

	@ObfuscatedName("client.Xd")
	public int chatPublicMode;

	@ObfuscatedName("client.ae")
	public int macroCameraZ;

	@ObfuscatedName("client.ce")
	public int field307;

	@ObfuscatedName("client.de")
	public int field308;

	@ObfuscatedName("client.ee")
	public static int oplogic8;

	@ObfuscatedName("client.le")
	public int crossX;

	@ObfuscatedName("client.me")
	public int crossY;

	@ObfuscatedName("client.ne")
	public int crossCycle;

	@ObfuscatedName("client.oe")
	public int crossMode;

	@ObfuscatedName("client.ve")
	public int runweight;

	@ObfuscatedName("client.ze")
	public int recoveriesLastChangedDay;

	@ObfuscatedName("client.Ae")
	public int waveCount;

	@ObfuscatedName("client.Fe")
	public int sceneBaseTileX;

	@ObfuscatedName("client.Ge")
	public int sceneBaseTileZ;

	@ObfuscatedName("client.He")
	public int mapLastBaseX;

	@ObfuscatedName("client.Ie")
	public int mapLastBaseZ;

	@ObfuscatedName("client.Je")
	public int field340;

	@ObfuscatedName("client.Ke")
	public int macroMinimapCycle;

	@ObfuscatedName("client.Me")
	public int flameGradientCycle0;

	@ObfuscatedName("client.Ne")
	public int flameGradientCycle1;

	@ObfuscatedName("client.Oe")
	public static int oplogic9;

	@ObfuscatedName("client.Pe")
	public int minimapType;

	@ObfuscatedName("client.Re")
	public static int oplogic4;

	@ObfuscatedName("client.We")
	public int systemUpdateTimer;

	@ObfuscatedName("client.cf")
	public int hoveredSlot;

	@ObfuscatedName("client.df")
	public int hoveredSlotInterfaceId;

	// Bank tabs: which bank tab the cursor is over, or -1. Set in handleInterfaceInput off the
	// component clientCode, and read on drag release. Without it a drag dropped on a tab button
	// is indistinguishable from one dropped on empty chrome and is silently thrown away.
	public int bankTabHovered = -1;

	// Which bank tab a drag STARTED on, or -1. The existing drag machinery only ever begins on an
	// inventory slot (objDragArea), so dragging a tab button needed its own fields rather than a
	// reuse - a tab is not an inv slot and has no slot number to carry.
	public int bankTabDragFrom = -1;
	public int bankTabGrabX;
	public int bankTabGrabY;

	// The bank grid's component id, learned from the first IF_SETINVWINDOW the server sends for it.
	// A tab-swap packet has to name a component the server will accept, and the bank cannot be open
	// without this having arrived first.
	public int bankGridCom = -1;

	// Bank search: the text typed into the search box, "" when no search is running. The box is
	// chatbackInputOpen == 4, and unlike the amount and name prompts it stays open while the
	// player clicks items, so a search can be withdrawn from. Purely client side - the server is
	// never told, the grid just shows a different subset of the slots it already has.
	public String bankSearchText = "";

	// Bank tabs: true when the cell under the cursor is PADDING - one of the blanks between the end
	// of a tab's items and the start of the next tab's row. It is still a drop target, but it means
	// "put this on the end of that tab", not "insert it at that slot", and the two are not the same
	// place: inserting at the tab's last item lands the dragged obj one short of the end.
	public boolean hoveredSlotPad = false;

	@ObfuscatedName("client.hf")
	public int membersAccount;

	@ObfuscatedName("client.lf")
	public int sceneState;

	@ObfuscatedName("client.pf")
	public int field371;

	@ObfuscatedName("client.qf")
	public int activeMapFunctionCount;

	@ObfuscatedName("client.wf")
	public static int field378;

	@ObfuscatedName("client.xf")
	public int daysSincePasswordChanged;

	@ObfuscatedName("client.Ff")
	public int currentLevel;

	@ObfuscatedName("client.If")
	public int dragCycles;

	@ObfuscatedName("client.Of")
	public static int oplogic1;

	@ObfuscatedName("client.Pf")
	public int flameCycle;

	@ObfuscatedName("client.Uf")
	public int chatHoveredInterfaceIndex;

	@ObfuscatedName("client.Zf")
	public int objDragInterfaceId;

	@ObfuscatedName("client.ag")
	public int objDragSlot;

	@ObfuscatedName("client.bg")
	public int objDragArea;

	@ObfuscatedName("client.cg")
	public int objGrabX;

	@ObfuscatedName("client.dg")
	public int objGrabY;

	@ObfuscatedName("client.gg")
	public int macroCameraCycle;

	@ObfuscatedName("client.ig")
	public int flagSceneTileX;

	@ObfuscatedName("client.jg")
	public int flagSceneTileZ;

	@ObfuscatedName("client.og")
	public int tryMoveNearest;

	@ObfuscatedName("client.qg")
	public int nextMusicDelay;

	@ObfuscatedName("client.rg")
	public int field425;

	@ObfuscatedName("client.vg")
	public int npcCount;

	@ObfuscatedName("client.Ag")
	public int sceneCycle;

	@ObfuscatedName("client.Bg")
	public static int oplogic5;

	@ObfuscatedName("client.Ig")
	public int objSelected;

	@ObfuscatedName("client.Jg")
	public int objSelectedSlot;

	@ObfuscatedName("client.Kg")
	public int objSelectedInterface;

	@ObfuscatedName("client.Lg")
	public int objInterface;

	@ObfuscatedName("client.Ng")
	public int hintPlayer;

	@ObfuscatedName("client.Og")
	public int privateMessageCount;

	@ObfuscatedName("client.Wg")
	public static int field456;

	@ObfuscatedName("client.Yg")
	public static int oplogic10;

	@ObfuscatedName("client.bh")
	public static int oplogic2;

	@ObfuscatedName("client.eh")
	public static int field464;

	@ObfuscatedName("client.gh")
	public int currentDay;

	@ObfuscatedName("client.hh")
	public int spellSelected;

	@ObfuscatedName("client.ih")
	public int activeSpellId;

	@ObfuscatedName("client.jh")
	public int activeSpellFlags;

	@ObfuscatedName("client.ph")
	public int lastWaveLength;

	@ObfuscatedName("client.th")
	public int menuSize;

	@ObfuscatedName("client.Hh")
	public int hintType;

	@ObfuscatedName("client.Sh")
	public int field504;

	@ObfuscatedName("client.Zh")
	public int previousLoginDay;

	@ObfuscatedName("client.ai")
	public int cameraX;

	@ObfuscatedName("client.bi")
	public int cameraY;

	@ObfuscatedName("client.ci")
	public int cameraZ;

	@ObfuscatedName("client.di")
	public int cameraPitch;

	@ObfuscatedName("client.ei")
	public int cameraYaw;

	@ObfuscatedName("client.fi")
	public int socialInputType;

	@ObfuscatedName("client.gi")
	public int field518;

	@ObfuscatedName("client.hi")
	public int splitPrivateChat;

	@ObfuscatedName("client.ji")
	public int titleScreenState;

	@ObfuscatedName("client.ki")
	public int hintNpc;

	@ObfuscatedName("client.li")
	public int chatTradeMode;

	@ObfuscatedName("client.oi")
	public static int oplogic7;

	@ObfuscatedName("client.ri")
	public int macroMinimapZoom;

	@ObfuscatedName("client.ti")
	public static int oplogic3;

	@ObfuscatedName("client.vi")
	public static int field533;

	@ObfuscatedName("client.wi")
	public int flameCycle0;

	@ObfuscatedName("client.zi")
	public int lastAddress;

	@ObfuscatedName("client.Ci")
	public int chatbackInputOpen;

	@ObfuscatedName("client.Ei")
	public int overrideChat;

	@ObfuscatedName("client.Ki")
	public int orbitCameraYaw;

	@ObfuscatedName("client.Li")
	public int orbitCameraYawVelocity;

	@ObfuscatedName("client.Mi")
	public int orbitCameraPitchVelocity;

	// QoL: click-compass-to-face-north (see handleMinimapInput()/updateOrbitCamera()) - not a
	// stored/obfuscated client field, just drives a smooth camera turn once set.
	private boolean compassResetting;

	@ObfuscatedName("client.Ni")
	public int macroCameraAngle;

	@ObfuscatedName("client.Ui")
	public int orbitCameraX;

	@ObfuscatedName("client.Vi")
	public int orbitCameraZ;

	@ObfuscatedName("client.Wi")
	public int cameraPositionEventTimer;

	@ObfuscatedName("client.bj")
	public int objDragCycles;

	@ObfuscatedName("client.cj")
	public int midiSong;

	@ObfuscatedName("client.fj")
	public int unreadMessageCount;

	@ObfuscatedName("client.mj")
	public int sidebarHoveredInterfaceIndex;

	@ObfuscatedName("client.qj")
	public int field580;

	@ObfuscatedName("client.vj")
	public int cameraPitchClamp;

	@ObfuscatedName("client.Aj")
	public int entityRemovalCount;

	@ObfuscatedName("client.Fj")
	public int field595;

	@ObfuscatedName("client.Gj")
	public int oneMouseButton;

	@ObfuscatedName("client.Ij")
	public int viewportHoveredInterfaceIndex;

	@ObfuscatedName("client.Jj")
	public int scrollInputPadding;

	@ObfuscatedName("client.Kj")
	public int menuArea;

	@ObfuscatedName("client.Lj")
	public int menuX;

	@ObfuscatedName("client.Mj")
	public int menuY;

	@ObfuscatedName("client.Nj")
	public int menuWidth;

	@ObfuscatedName("client.Oj")
	public int menuHeight;

	@ObfuscatedName("client.Pj")
	public static int drawCycle;

	@ObfuscatedName("client.Vj")
	public int field611;

	@ObfuscatedName("client.Zj")
	public int inMultizone;

	@ObfuscatedName("client.ck")
	public int lastProgressPercent;

	@ObfuscatedName("client.ek")
	public int runenergy;

	@ObfuscatedName("client.fk")
	public static int loopCycle;

	@ObfuscatedName("client.jk")
	public int selectedCycle;

	@ObfuscatedName("client.kk")
	public int selectedInterface;

	@ObfuscatedName("client.lk")
	public int selectedItem;

	@ObfuscatedName("client.mk")
	public int selectedArea;

	@ObfuscatedName("client.Xb")
	public long lastMouseClickTime;

	@ObfuscatedName("client.zc")
	public long serverSeed;

	@ObfuscatedName("client.Dg")
	public long socialName37;

	@ObfuscatedName("client.ni")
	public long sceneLoadStartTime;
	/**
	 * When THIS scene load began. Not sceneLoadStartTime, which is restarted every six minutes to pace
	 * the error report and would take the stuck note back off the screen each time.
	 */
	private long sceneLoadBegan;
	/** checkScene's last answer: 0 once the scene is built, else -1 to -4. See sceneStuckNote. */
	private int sceneStuckReason;
	/** The note currently painted under "Loading - please wait", so it is repainted only when it changes. */
	private String sceneStuckShown;
	/**
	 * How long a scene may sit unfinished before the loading screen starts saying why. Long enough
	 * that an ordinary load on a slow connection never shows it - those finish in a second or two off
	 * a warm cache, and a cold one is a progress bar before this point, not a stuck scene.
	 */
	private static final long SCENE_STUCK_MS = 20000L;

	@ObfuscatedName("client.Ii")
	public long lastWaveStartTime;

	@ObfuscatedName("client.Jb")
	public Jagfile jagTitle;

	@ObfuscatedName("client.Gi")
	public MouseTracking mouseTracking;

	@ObfuscatedName("client.ie")
	public Pix32 imageFlamesLeft;

	@ObfuscatedName("client.je")
	public Pix32 imageFlamesRight;

	@ObfuscatedName("client.Be")
	public Pix32 imageMapmarker0;

	@ObfuscatedName("client.Ce")
	public Pix32 imageMapmarker1;

	@ObfuscatedName("client.Af")
	public Pix32 imageOverlayMultiway;

	@ObfuscatedName("client.Qf")
	public Pix32 genderButtonImage0;

	@ObfuscatedName("client.Rf")
	public Pix32 genderButtonImage1;

	@ObfuscatedName("client.eg")
	public Pix32 imageCompass;

	@ObfuscatedName("client.kg")
	public Pix32 imageMinimap;

	@ObfuscatedName("client.Ch")
	public Pix32 imageMapdot0;

	@ObfuscatedName("client.Dh")
	public Pix32 imageMapdot1;

	@ObfuscatedName("client.Eh")
	public Pix32 imageMapdot2;

	@ObfuscatedName("client.Fh")
	public Pix32 imageMapdot3;

	@ObfuscatedName("client.Gh")
	public Pix32 imageMapdot4;

	@ObfuscatedName("client.Fi")
	public Pix32 imageMapedge;

	@ObfuscatedName("client.pe")
	public ClientStream stream;

	@ObfuscatedName("client.bc")
	public PixMap areaBackleft1;

	// The parchment chatbox's border, drawn once with the rest of the frame. Chat itself goes in
	// areaChatback, which is the inside of it.
	public PixMap areaChatframe;

	@ObfuscatedName("client.dc")
	public PixMap areaBackright1;

	@ObfuscatedName("client.ec")
	public PixMap areaBackright2;

	@ObfuscatedName("client.fc")
	public PixMap areaBacktop1;

	@ObfuscatedName("client.gc")
	public PixMap areaBackvmid1;

	@ObfuscatedName("client.hc")
	public PixMap areaBackvmid2;

	@ObfuscatedName("client.ic")
	public PixMap areaBackvmid3;

	@ObfuscatedName("client.Wf")
	public PixMap areaBackbase1;

	@ObfuscatedName("client.Xf")
	public PixMap areaBackbase2;

	@ObfuscatedName("client.Yf")
	public PixMap areaBackmid1;

	@ObfuscatedName("client.Sg")
	public PixMap areaSidebar;

	@ObfuscatedName("client.Tg")
	public PixMap areaMapback;

	@ObfuscatedName("client.Ug")
	public PixMap areaViewport;

	@ObfuscatedName("client.Vg")
	public PixMap areaChatback;

	@ObfuscatedName("client.Ih")
	public PixMap imageTitle2;

	@ObfuscatedName("client.Jh")
	public PixMap imageTitle3;

	@ObfuscatedName("client.Kh")
	public PixMap imageTitle4;

	@ObfuscatedName("client.Lh")
	public PixMap imageTitle0;

	@ObfuscatedName("client.Mh")
	public PixMap imageTitle1;

	@ObfuscatedName("client.Nh")
	public PixMap imageTitle5;

	@ObfuscatedName("client.Oh")
	public PixMap imageTitle6;

	@ObfuscatedName("client.Ph")
	public PixMap imageTitle7;

	@ObfuscatedName("client.Qh")
	public PixMap imageTitle8;

	@ObfuscatedName("client.Ye")
	public PixFont fontPlain11;

	@ObfuscatedName("client.Ze")
	public PixFont fontPlain12;

	@ObfuscatedName("client.af")
	public PixFont fontBold12;

	@ObfuscatedName("client.bf")
	public PixFont fontQuill8;

	@ObfuscatedName("client.ah")
	public World3D scene;

	@ObfuscatedName("client.Ub")
	public Isaac randomIn;

	// Selected-tab stones: 0 top-left, 1 top-right, 2 bottom-left, 3 bottom-right, 4 middle.
	public Pix8[] imageTabstones = new Pix8[5];

	// Chat bar buttons: 0 normal, 1 hover, 2 selected, 3 selected+hover. Report: 0 normal, 1 hover.
	public Pix8[] imageChatbuttons = new Pix8[4];

	public Pix8[] imageReportbutton = new Pix8[2];

	@ObfuscatedName("client.id")
	public Pix8 imageBackbase1;

	@ObfuscatedName("client.jd")
	public Pix8 imageBackbase2;

	@ObfuscatedName("client.kd")
	public Pix8 imageBackhmid1;

	@ObfuscatedName("client.Jf")
	public Pix8 imageScrollbar0;

	@ObfuscatedName("client.Kf")
	public Pix8 imageScrollbar1;

	@ObfuscatedName("client.vh")
	public Pix8 imageInvback;

	@ObfuscatedName("client.wh")
	public Pix8 imageMapback;

	@ObfuscatedName("client.xh")
	public Pix8 imageChatback;

	@ObfuscatedName("client.yj")
	public Pix8 imageTitlebox;

	@ObfuscatedName("client.zj")
	public Pix8 imageTitlebutton;

	@ObfuscatedName("client.dh")
	public static ClientPlayer localPlayer;

	@ObfuscatedName("client.xj")
	public OnDemand onDemand;

	@ObfuscatedName("client.se")
	public String lastProgressMessage;

	@ObfuscatedName("client.Xe")
	public String modalMessage;

	@ObfuscatedName("client.Mg")
	public String objSelectedName;

	@ObfuscatedName("client.kh")
	public String spellCaption;

	@ObfuscatedName("client.ii")
	public Socket field520;

	@ObfuscatedName("client.pb")
	public static boolean displayFps;

	@ObfuscatedName("client.vc")
	public static boolean lowMem;

	@ObfuscatedName("client.fd")
	public static boolean field258;

	@ObfuscatedName("client.Qd")
	public static boolean alreadyStarted;

	@ObfuscatedName("client.db")
	public int[] sceneMapIndex;

	@ObfuscatedName("client.eb")
	public int[] sceneMapLandFile;

	@ObfuscatedName("client.fb")
	public int[] sceneMapLocFile;

	@ObfuscatedName("client.Rd")
	public int[] areaChatbackOffset;

	@ObfuscatedName("client.Sd")
	public int[] areaSidebarOffset;

	@ObfuscatedName("client.Td")
	public int[] areaViewportOffset;

	@ObfuscatedName("client.Ud")
	public int[] areaFullscreenOffset;

	@ObfuscatedName("client.yf")
	public int[] flameBuffer2;

	@ObfuscatedName("client.zf")
	public int[] flameBuffer3;

	@ObfuscatedName("client.mh")
	public int[] flameBuffer0;

	@ObfuscatedName("client.nh")
	public int[] flameBuffer1;

	@ObfuscatedName("client.Qj")
	public int[] flameGradient;

	@ObfuscatedName("client.Rj")
	public int[] flameGradient0;

	@ObfuscatedName("client.Sj")
	public int[] flameGradient1;

	@ObfuscatedName("client.Tj")
	public int[] flameGradient2;

	@ObfuscatedName("client.fg")
	public Pix8[] imageRunes;

	@ObfuscatedName("client.L")
	public byte[][] sceneMapLandData;

	@ObfuscatedName("client.qi")
	public byte[][] sceneMapLocData;

	@ObfuscatedName("client.ng")
	public byte[][][] levelTileFlags;

	@ObfuscatedName("client.Mb")
	public int[][][] levelHeightmap;

	static {
		int var0 = 0;
		for (int var1 = 0; var1 < 99; var1++) {
			int var4 = var1 + 1;
			int var5 = (int) ((double) var4 + Math.pow(2.0D, (double) var4 / 7.0D) * 300.0D);
			var0 += var5;
			levelExperience[var1] = var0 / 4;
		}
		CHARSET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!\"£$%^&*()-_=+[{]};:'@#~,<.>/?\\| ";
		DESIGN_BODY_COLOUR = new int[][] { { 6798, 107, 10283, 16, 4797, 7744, 5799, 4634, 33697, 22433, 2983, 54193 }, { 8741, 12, 64030, 43162, 7735, 8404, 1701, 38430, 24094, 10153, 56621, 4783, 1341, 16578, 35003, 25239 }, { 25238, 8742, 12, 64030, 43162, 7735, 8404, 1701, 38430, 24094, 10153, 56621, 4783, 1341, 16578, 35003 }, { 4626, 11146, 6439, 12, 4758, 10270 }, { 4550, 4537, 5681, 5673, 5790, 6806, 8076, 4574 } };
		VARBIT_MASKS = new int[32];
		int var2 = 2;
		for (int var3 = 0; var3 < 32; var3++) {
			VARBIT_MASKS[var3] = var2 - 1;
			var2 += var2;
		}
		DESIGN_HAIR_COLOUR = new int[] { 9104, 10275, 7595, 3610, 7975, 8526, 918, 38802, 24466, 10145, 58654, 5027, 1457, 16565, 34991, 25486 };
		LOGIN_RSAE = new BigInteger("65537");
	}

	// ----

	public static void main(String[] args) {
		try {
			System.out.println(SERVER_NAME);
			DevLog.log("SESSION", "=== DEV CLIENT === logging every menu action, chat message, and login/logout to console + dev-client.log");
			// WHICH DIAGNOSTICS THIS BUILD HAS. Two logs in a row came back with no [animframe] lines
			// and there was no way to tell "frames are fine" from "this client predates that check",
			// which wasted a round. Any log can now answer it on its own first line.
			DevLog.log("SESSION", "diagnostics: npcmodel, animframe, offscene");

			if (args.length == 5) {
				nodeId = Integer.parseInt(args[0]);
				portOffset = Integer.parseInt(args[1]);

				if (args[2].equals("lowmem")) {
					setLowMem();
				} else if (args[2].equals("highmem")) {
					setHighMem();
				} else {
					System.out.println("Usage: node-id, port-offset, [lowmem/highmem], [free/members], storeid");
					return;
				}

				if (args[3].equals("free")) {
					membersWorld = false;
				} else if (args[3].equals("members")) {
					membersWorld = true;
				} else {
					System.out.println("Usage: node-id, port-offset, [lowmem/highmem], [free/members], storeid");
					return;
				}

				signlink.storeid = Integer.parseInt(args[4]);
				signlink.startpriv(InetAddress.getLocalHost());

				Client app = new Client();
				app.initApplication(503, 765);
			} else if (args.length == 0) {
				// default args: 10 0 highmem members 32
				nodeId = 10;
				portOffset = 0;
				setHighMem();
				membersWorld = true;

				signlink.storeid = 32;
				signlink.startpriv(InetAddress.getLocalHost());

				Client app = new Client();
				app.initApplication(503, 765);
			} else {
				System.out.println("Usage: node-id, port-offset, [lowmem/highmem], [free/members], storeid");
			}
		} catch (Exception ignore) {
		}
	}

	public void init() {
		nodeId = Integer.parseInt(this.getParameter("nodeid"));
		portOffset = Integer.parseInt(this.getParameter("portoff"));
		String var1 = this.getParameter("lowmem");
		if (var1 != null && var1.equals("1")) {
			setLowMem();
		} else {
			setHighMem();
		}
		String var2 = this.getParameter("free");
		if (var2 != null && var2.equals("1")) {
			membersWorld = false;
		} else {
			membersWorld = true;
		}
		this.initApplet(765, 503);
	}

	public void run() {
		if (this.flameActive0) {
			this.runFlames();
		} else {
			super.run();
		}
	}

	@ObfuscatedName("client.l(Z)V")
	public static void setLowMem() {
		World3D.lowMem = true;
		Pix3D.lowMem = true;
		lowMem = true;
		World.lowMem = true;
		LocType.lowMem = true;
	}

	@ObfuscatedName("client.d(Z)V")
	public static void setHighMem() {
		World3D.lowMem = false;
		Pix3D.lowMem = false;
		lowMem = false;
		World.lowMem = false;
		LocType.lowMem = false;
	}

	public URL getCodeBase() {
		if (signlink.mainapp != null) {
			return signlink.mainapp.getCodeBase();
		}
		try {
			if (super.frame != null) {
				// Standalone (non-applet) launches otherwise always talk to 127.0.0.1, which only works
				// when the client and server run on the same machine. SERVER_HOST/WEB_PORT (above)
				// default to the homelab server so a plain launch just connects; override with
				// -Dlostcity.host=/-Dlostcity.webport= (or LOSTCITY_HOST/LOSTCITY_WEBPORT env vars)
				// to point this build at some other server instead (e.g. local same-machine dev).
				return new URL(WEB_URL != null ? WEB_URL : "http://" + WEB_HOST + ":" + WEB_PORT);
			}
		} catch (Exception var1) {
		}
		return super.getCodeBase();
	}

	public String getParameter(String arg0) {
		return signlink.mainapp == null ? super.getParameter(arg0) : signlink.mainapp.getParameter(arg0);
	}

	@ObfuscatedName("client.j(I)Ljava/lang/String;")
	public String getHost(int arg0) {
		if (arg0 != -42588) {
			this.ptype = this.in.g1();
		}
		if (signlink.mainapp == null) {
			return super.frame == null ? super.getDocumentBase().getHost().toLowerCase() : "runescape.com";
		} else {
			return signlink.mainapp.getDocumentBase().getHost().toLowerCase();
		}
	}

	@ObfuscatedName("client.d(I)Ljava/awt/Component;")
	public java.awt.Component getBaseComponent() {
		return this;
	}

	@ObfuscatedName("client.b(Ljava/lang/String;)Ljava/io/DataInputStream;")
	public DataInputStream openUrl(String arg0) throws IOException {
		if (this.field196) {
			if (this.field520 != null) {
				try {
					this.field520.close();
				} catch (Exception var4) {
				}
				this.field520 = null;
			}
			this.field520 = this.openSocket(43595);
			this.field520.setSoTimeout(10000);
			InputStream var2 = this.field520.getInputStream();
			OutputStream var3 = this.field520.getOutputStream();
			var3.write(("JAGGRAB /" + arg0 + "\n\n").getBytes());
			return new DataInputStream(var2);
		} else if (signlink.mainapp == null) {
			return new DataInputStream((new URL(this.getCodeBase(), arg0)).openStream());
		} else {
			return signlink.openurl(arg0);
		}
	}

	@ObfuscatedName("client.g(I)Ljava/net/Socket;")
	public Socket openSocket(int port) throws IOException {
		// Every socket this client opens comes through here - the game stream and OnDemand's update
		// stream alike - which is what makes the browser case one line rather than a port of the
		// networking. jagex2/io/WsSocket.java.
		if (WS_URL != null) {
			return new jagex2.io.WsSocket(WS_URL);
		}
		return new Socket(InetAddress.getByName(SERVER_HOST), port);
	}

	@ObfuscatedName("client.a(Ljava/lang/Runnable;I)V")
	public void startThread(Runnable thread, int priority) {
		if (priority > 10) {
			priority = 10;
		}
		if (signlink.mainapp == null) {
			super.startThread(thread, priority);
		} else {
			signlink.startthread(thread, priority);
		}
	}

	@ObfuscatedName("client.a(Z[BI)V")
	public void saveMidi(boolean arg0, byte[] arg1) {
		if (this.midiActive) {
			signlink.midifade = arg0 ? 1 : 0;
			signlink.midisave(arg1, arg1.length);
		}
	}

	@ObfuscatedName("client.g(Z)V")
	public void stopMidi() {
		signlink.midiplay = false;
		signlink.midifade = 0;
		signlink.midi = "stop";
	}

	@ObfuscatedName("client.a(ZBI)V")
	public void setMidiVolume(boolean midiActive, int arg2) {
		signlink.midivol = arg2;
		if (midiActive) {
			signlink.midi = "voladjust";
		}
	}

	@ObfuscatedName("client.a(II[B)Z")
	public boolean saveWave(int arg1, byte[] arg2) {
		return arg2 == null ? true : signlink.wavesave(arg2, arg1);
	}

	@ObfuscatedName("client.s(I)Z")
	public boolean replayWave() {
		return signlink.wavereplay();
	}

	@ObfuscatedName("client.c(II)V")
	public void setWaveVolume(int arg1) {
		signlink.wavevol = arg1;
	}

	@ObfuscatedName("client.a()V")
	public void load() {
		// A player who left the window resizable gets it back, in the layout they left it in. Only a
		// standalone client has a window to resize; an applet is the size its page makes it.
		if (super.frame != null && DisplaySettings.resizable()) {
			this.setDisplayMode(DisplaySettings.mode());
		}
		this.drawProgress(20, "Starting up");

		if (signlink.sunjava) {
			super.mindel = 5;
		}

		if (alreadyStarted) {
			this.errorStarted = true;
			return;
		}

		alreadyStarted = true;

		boolean validHost = false;
		String host = this.getHost(-42588);
		if (host.endsWith("jagex.com")) {
			validHost = true;
		}
		if (host.endsWith("runescape.com")) {
			validHost = true;
		}
		if (host.endsWith("192.168.1.2")) {
			validHost = true;
		}
		if (host.endsWith("192.168.1.231")) {
			validHost = true;
		}
		if (host.endsWith("192.168.1.229")) {
			validHost = true;
		}
		if (host.endsWith("192.168.1.228")) {
			validHost = true;
		}
		if (host.endsWith("192.168.1.227")) {
			validHost = true;
		}
		if (host.endsWith("192.168.1.226")) {
			validHost = true;
		}
		if (host.endsWith("192.168.1.224")) {
			validHost = true;
		}
		if (host.endsWith("192.168.1.223")) {
			validHost = true;
		}
		if (host.endsWith("192.168.1.221")) {
			validHost = true;
		}
		if (host.endsWith("127.0.0.1")) {
			validHost = true;
		}
		if (!validHost) {
			this.errorHost = true;
			return;
		}

		if (signlink.cache_dat != null) {
			for (int i = 0; i < 5; i++) {
				this.fileStreams[i] = new FileStream(i + 1, 600000, signlink.cache_dat, signlink.cache_idx[i]);
			}
		}

		try {
			this.getJagCrc();

			this.jagTitle = this.getJagFile(this.jagChecksum[1], "title", 25, 1, "title screen");

			this.fontPlain11 = new PixFont(false, this.jagTitle, "p11_full");
			this.fontPlain12 = new PixFont(false, this.jagTitle, "p12_full");
			this.fontBold12 = new PixFont(false, this.jagTitle, "b12_full");
			this.fontQuill8 = new PixFont(true, this.jagTitle, "q8_full");

			// Plugins: built here because overlays draw with the client's fonts, so there is no
			// manager to hand out until they exist. Anything that goes wrong reading the plugins
			// folder is swallowed by the manager; a throw escaping it would be a bug in the
			// manager itself, and even then the client still starts - just without plugins.
			try {
				this.plugins = new PluginManager(this, this.fontPlain11, this.fontPlain12, this.fontBold12);
				this.plugins.reload();
				this.attachPluginSidebar();
			} catch (Throwable error) {
				this.plugins = null;
				DevLog.log("PLUGIN", "plugin system disabled for this session: " + error);
			}

			this.loadTitleBackground();
			this.loadTitleImages();

			Jagfile jagConfig = this.getJagFile(this.jagChecksum[2], "config", 30, 2, "config");
			Jagfile jagInterface = this.getJagFile(this.jagChecksum[3], "interface", 35, 3, "interface");
			Jagfile jagMedia = this.getJagFile(this.jagChecksum[4], "media", 40, 4, "2d graphics");
			Jagfile jagTextures = this.getJagFile(this.jagChecksum[6], "textures", 45, 6, "textures");
			Jagfile jagWordenc = this.getJagFile(this.jagChecksum[7], "wordenc", 50, 7, "chat system");
			Jagfile jagSounds = this.getJagFile(this.jagChecksum[8], "sounds", 55, 8, "sound effects");

			this.levelTileFlags = new byte[4][104][104];
			this.levelHeightmap = new int[4][105][105];
			this.scene = new World3D(this.levelHeightmap, 104, 4, 104);
			for (int i = 0; i < 4; i++) {
				this.levelCollisionMap[i] = new CollisionMap(104, 104);
			}
			this.imageMinimap = new Pix32(512, 512);

			Jagfile jagVersionlist = this.getJagFile(this.jagChecksum[5], "versionlist", 60, 5, "update list");

			this.drawProgress(60, "Connecting to update server");

			this.onDemand = new OnDemand();
			this.onDemand.unpack(jagVersionlist, this);
			AnimFrame.init(this.onDemand.getAnimCount());
			Model.init(this.onDemand.getFileCount(0), this.onDemand);

			if (!lowMem) {
				this.midiSong = 0;
				this.midiFading = true;

				this.onDemand.request(2, this.midiSong);

				while (this.onDemand.remaining() > 0) {
					this.updateOnDemand();
					try { Thread.sleep(100L); } catch (Exception ignore) { }

					if (this.onDemand.tries > 3) {
						this.showError("ondemand");
						return;
					}
				}
			}

			this.drawProgress(65, "Requesting animations");

			int animCount = this.onDemand.getFileCount(1);
			for (int i = 0; i < animCount; i++) {
				this.onDemand.request(1, i);
			}

			while (this.onDemand.remaining() > 0) {
				int progress = animCount - this.onDemand.remaining();
				if (progress > 0) {
					this.drawProgress(65, "Loading animations - " + progress * 100 / animCount + "%");
				}

				this.updateOnDemand();

				try {
					Thread.sleep(100L);
				} catch (Exception ignore) {
				}

				if (this.onDemand.tries > 3) {
					this.showError("ondemand");
					return;
				}
			}

			this.drawProgress(70, "Requesting models");

			int modelCount = this.onDemand.getFileCount(0);
			for (int i = 0; i < modelCount; i++) {
				int flags = this.onDemand.getModelFlags(i);
				if ((flags & 0x1) != 0) {
					this.onDemand.request(0, i);
				}
			}

			int modelPrefetch = this.onDemand.remaining();
			while (this.onDemand.remaining() > 0) {
				int progress = modelPrefetch - this.onDemand.remaining();
				if (progress > 0) {
					this.drawProgress(70, "Loading models - " + progress * 100 / modelPrefetch + "%");
				}

				this.updateOnDemand();

				try {
					Thread.sleep(100L);
				} catch (Exception ignore) {
				}
			}

			if (this.fileStreams[0] != null) {
				this.drawProgress(75, "Requesting maps");

				this.onDemand.request(3, this.onDemand.getMapFile(47, 48, 0));
				this.onDemand.request(3, this.onDemand.getMapFile(47, 48, 1));
				this.onDemand.request(3, this.onDemand.getMapFile(48, 48, 0));
				this.onDemand.request(3, this.onDemand.getMapFile(48, 48, 1));
				this.onDemand.request(3, this.onDemand.getMapFile(49, 48, 0));
				this.onDemand.request(3, this.onDemand.getMapFile(49, 48, 1));
				this.onDemand.request(3, this.onDemand.getMapFile(47, 47, 0));
				this.onDemand.request(3, this.onDemand.getMapFile(47, 47, 1));
				this.onDemand.request(3, this.onDemand.getMapFile(48, 47, 0));
				this.onDemand.request(3, this.onDemand.getMapFile(48, 47, 1));
				this.onDemand.request(3, this.onDemand.getMapFile(48, 148, 0));
				this.onDemand.request(3, this.onDemand.getMapFile(48, 148, 1));

				int mapPrefetch = this.onDemand.remaining();
				while (this.onDemand.remaining() > 0) {
					int progress = mapPrefetch - this.onDemand.remaining();
					if (progress > 0) {
						this.drawProgress(75, "Loading maps - " + progress * 100 / mapPrefetch + "%");
					}

					this.updateOnDemand();

					try {
						Thread.sleep(100L);
					} catch (Exception ignore) {
					}
				}
			}

			int modelCount2 = this.onDemand.getFileCount(0);
			for (int i = 0; i < modelCount2; i++) {
				int flags = this.onDemand.getModelFlags(i);

				byte priority = 0;
				if ((flags & 0x8) != 0) {
					priority = 10;
				} else if ((flags & 0x20) != 0) {
					priority = 9;
				} else if ((flags & 0x10) != 0) {
					priority = 8;
				} else if ((flags & 0x40) != 0) {
					priority = 7;
				} else if ((flags & 0x80) != 0) {
					priority = 6;
				} else if ((flags & 0x2) != 0) {
					priority = 5;
				} else if ((flags & 0x4) != 0) {
					priority = 4;
				}

				if ((flags & 0x1) != 0) {
					priority = 3;
				}

				if (priority != 0) {
					this.onDemand.prefetchPriority(0, priority, i);
				}
			}

			this.onDemand.prefetchMaps(membersWorld);

			if (!lowMem) {
				int midiCount = this.onDemand.getFileCount(2);
				for (int i = 1; i < midiCount; i++) {
					if (this.onDemand.shouldPrefetchMidi(i)) {
						this.onDemand.prefetchPriority(2, (byte) 1, i);
					}
				}
			}

			int modelCount3 = this.onDemand.getFileCount(0);
			for (int i = 0; i < modelCount3; i++) {
				int flags = this.onDemand.getModelFlags(i);
				if (flags == 0 && this.onDemand.totalPrefetchFiles < 200) {
					this.onDemand.prefetchPriority(0, (byte) 1, i);
				}
			}

			this.drawProgress(80, "Unpacking media");

			this.imageInvback = new Pix8(jagMedia, "invback", 0);
			this.imageChatback = new Pix8(jagMedia, "chatback", 0);
			this.imageMapback = new Pix8(jagMedia, "mapback", 0);
			this.imageBackbase1 = new Pix8(jagMedia, "backbase1", 0);
			this.imageBackbase2 = new Pix8(jagMedia, "backbase2", 0);
			this.imageBackhmid1 = new Pix8(jagMedia, "backhmid1", 0);

			for (int i = 0; i < this.imageSideicons.length; i++) {
				try {
					this.imageSideicons[i] = new Pix8(jagMedia, "sideicons", i);
				} catch (Exception e) {
					// 15 and 16, the Ancient and Lunar magic icons, are newer than some servers' media
					if (i < 15) {
						throw e;
					}
				}
			}

			this.imageCompass = new Pix32(jagMedia, "compass", 0);

			this.imageMapedge = new Pix32(jagMedia, "mapedge", 0);
			this.imageMapedge.trim();

			for (int i = 0; i < 72; i++) {
				this.imageMapscene[i] = new Pix8(jagMedia, "mapscene", i);
			}

			// 70 is the trading post's (Old School's Grand Exchange icon), newer than some servers' media
			for (int i = 0; i < 71; i++) {
				try {
					this.imageMapfunction[i] = new Pix32(jagMedia, "mapfunction", i);
				} catch (Exception e) {
					if (i < 70) {
						throw e;
					}
				}
			}

			// 0-4 are 377's own (block, damage, poison, and the two disease splats); 5-7 are Old School's
			// venom, heal and max hit, at the numbers Old School gives the first two. A server whose media
			// stops at 4 leaves the rest null, and the draw falls back to the plain damage splat.
			for (int i = 0; i < 8; i++) {
				try {
					this.imageHitmarks[i] = new Pix32(jagMedia, "hitmarks", i);
				} catch (Exception e) {
					if (i < 5) {
						throw e;
					}
				}
			}

			for (int i = 0; i < 6; i++) {
				this.imageHeadiconsPk[i] = new Pix32(jagMedia, "headicons_pk", i);
			}

			for (int i = 0; i < 9; i++) {
				this.imageHeadiconsPrayer[i] = new Pix32(jagMedia, "headicons_prayer", i);
			}

			for (int i = 0; i < 6; i++) {
				this.imageHeadiconsHint[i] = new Pix32(jagMedia, "headicons_hint", i);
			}

			this.imageOverlayMultiway = new Pix32(jagMedia, "overlay_multiway", 0);

			this.imageMapmarker0 = new Pix32(jagMedia, "mapmarker", 0);
			this.imageMapmarker1 = new Pix32(jagMedia, "mapmarker", 1);

			for (int i = 0; i < 8; i++) {
				this.imageCross[i] = new Pix32(jagMedia, "cross", i);
			}

			this.imageMapdot0 = new Pix32(jagMedia, "mapdots", 0);
			this.imageMapdot1 = new Pix32(jagMedia, "mapdots", 1);
			this.imageMapdot2 = new Pix32(jagMedia, "mapdots", 2);
			this.imageMapdot3 = new Pix32(jagMedia, "mapdots", 3);
			this.imageMapdot4 = new Pix32(jagMedia, "mapdots", 4);

			this.imageScrollbar0 = new Pix8(jagMedia, "scrollbar", 0);
			this.imageScrollbar1 = new Pix8(jagMedia, "scrollbar", 1);

			for (int i = 0; i < this.imageTabstones.length; i++) {
				this.imageTabstones[i] = new Pix8(jagMedia, "tabstones", i);
			}
			for (int i = 0; i < this.imageChatbuttons.length; i++) {
				this.imageChatbuttons[i] = new Pix8(jagMedia, "chatbuttons", i);
			}
			for (int i = 0; i < this.imageReportbutton.length; i++) {
				this.imageReportbutton[i] = new Pix8(jagMedia, "reportbutton", i);
			}

			for (int i = 0; i < 2; i++) {
				this.imageModIcons[i] = new Pix8(jagMedia, "mod_icons", i);
			}
			// Clan rank icons - optional like the badges: a cache without them draws the list without icons.
			for (int i = 0; i < this.imageClanRanks.length; i++) {
				try {
					this.imageClanRanks[i] = new Pix32(jagMedia, "clanrank", i);
				} catch (Exception ignored) {
					this.imageClanRanks[i] = null;
				}
			}
			// The badges are optional: a cache from before they existed has only the two crowns, and
			// ChatIcons draws a missing sprite as nothing rather than failing the whole load.
			for (int i = 2; i < this.imageModIcons.length; i++) {
				try {
					this.imageModIcons[i] = new Pix8(jagMedia, "mod_icons", i);
				} catch (Exception ignored) {
					this.imageModIcons[i] = null;
				}
			}

			Pix32 backleft1 = new Pix32(jagMedia, "backleft1", 0);
			this.areaBackleft1 = new PixMap(backleft1.hi, this.getBaseComponent(), backleft1.wi);
			backleft1.quickPlotSprite(0, 0);

			Pix32 chatframe = new Pix32(jagMedia, "chatback", 0);
			this.areaChatframe = new PixMap(chatframe.hi, this.getBaseComponent(), chatframe.wi);
			chatframe.quickPlotSprite(0, 0);

			Pix32 backright1 = new Pix32(jagMedia, "backright1", 0);
			this.areaBackright1 = new PixMap(backright1.hi, this.getBaseComponent(), backright1.wi);
			backright1.quickPlotSprite(0, 0);

			Pix32 backright2 = new Pix32(jagMedia, "backright2", 0);
			this.areaBackright2 = new PixMap(backright2.hi, this.getBaseComponent(), backright2.wi);
			backright2.quickPlotSprite(0, 0);

			Pix32 backtop1 = new Pix32(jagMedia, "backtop1", 0);
			this.areaBacktop1 = new PixMap(backtop1.hi, this.getBaseComponent(), backtop1.wi);
			backtop1.quickPlotSprite(0, 0);

			Pix32 backvmid1 = new Pix32(jagMedia, "backvmid1", 0);
			this.areaBackvmid1 = new PixMap(backvmid1.hi, this.getBaseComponent(), backvmid1.wi);
			backvmid1.quickPlotSprite(0, 0);

			Pix32 backvmid2 = new Pix32(jagMedia, "backvmid2", 0);
			this.areaBackvmid2 = new PixMap(backvmid2.hi, this.getBaseComponent(), backvmid2.wi);
			backvmid2.quickPlotSprite(0, 0);

			Pix32 backvmid3 = new Pix32(jagMedia, "backvmid3", 0);
			this.areaBackvmid3 = new PixMap(backvmid3.hi, this.getBaseComponent(), backvmid3.wi);
			backvmid3.quickPlotSprite(0, 0);

			int randR = (int) (Math.random() * 21.0D) - 10;
			int randG = (int) (Math.random() * 21.0D) - 10;
			int randB = (int) (Math.random() * 21.0D) - 10;
			int rand = (int) (Math.random() * 41.0D) - 20;

			for (int i = 0; i < 100; i++) {
				if (this.imageMapfunction[i] != null) {
					this.imageMapfunction[i].rgbAdjust(randB + rand, randG + rand, randR + rand);
				}

				if (this.imageMapscene[i] != null) {
					this.imageMapscene[i].rgbAdjust(randB + rand, randG + rand, randR + rand);
				}
			}

			this.drawProgress(83, "Unpacking textures");

			Pix3D.unpackTextures(jagTextures);
			Pix3D.initColourTable(0.8D);
			Pix3D.initPool(20);

			this.drawProgress(86, "Unpacking config");

			SeqType.unpack(jagConfig);
			LocType.unpack(jagConfig);
			FloType.unpack(jagConfig);
			ObjType.unpack(jagConfig);
			AreaSounds.unpack(jagConfig);
			NpcType.unpack(jagConfig);
			IdkType.unpack(jagConfig);
			SpotAnimType.unpack(jagConfig);
			VarpType.unpack(jagConfig);
			VarbitType.unpack(jagConfig);
			ObjType.membersWorld = membersWorld;

			if (!lowMem) {
				this.drawProgress(90, "Unpacking sounds");

				byte[] dat = jagSounds.read("sounds.dat", null);
				Packet sounds = new Packet(dat);
				Wave.unpack(sounds, 36135);
			}

			this.drawProgress(95, "Unpacking interfaces");

			PixFont[] fonts = new PixFont[] { this.fontPlain11, this.fontPlain12, this.fontBold12, this.fontQuill8};
			Component.unpack(fonts, jagInterface, jagMedia);

			this.drawProgress(100, "Preparing game engine");

			for (int y = 0; y < 33; y++) {
				int left = 999;
				int right = 0;

				for (int x = 0; x < 34; x++) {
					if (this.imageMapback.pixels[this.imageMapback.wi * y + x] == 0) {
						if (left == 999) {
							left = x;
						}
					} else if (left != 999) {
						right = x;
						break;
					}
				}

				this.compassMaskLineOffsets[y] = left;
				this.compassMaskLineLengths[y] = right - left;
			}

			for (int y = 5; y < 156; y++) {
				int left = 999;
				int right = 0;

				for (int x = 25; x < 172; x++) {
					if (this.imageMapback.pixels[this.imageMapback.wi * y + x] == 0 && (x > 34 || y > 34)) {
						if (left == 999) {
							left = x;
						}
					} else if (left != 999) {
						right = x;
						break;
					}
				}

				this.minimapMaskLineOffsets[y - 5] = left - 25;
				this.minimapMaskLineLengths[y - 5] = right - left;
			}

			Pix3D.init3D(503, 765);
			this.areaFullscreenOffset = Pix3D.lineOffset;

			Pix3D.init3D(CHAT_H, CHAT_W);
			this.areaChatbackOffset = Pix3D.lineOffset;

			Pix3D.init3D(261, 190);
			this.areaSidebarOffset = Pix3D.lineOffset;

			Pix3D.init3D(334, 512);
			this.areaViewportOffset = Pix3D.lineOffset;

			int[] distance = new int[9];
			for (int x = 0; x < 9; x++) {
				int angle = x * 32 + 128 + 15;
				int offset = angle * 3 + 600;
				int sin = Pix3D.sinTable[angle];
				distance[x] = offset * sin >> 16;
			}

			World3D.drawDistance = DisplaySettings.drawDistance();
			World3D.init(334, distance, 800, 500, 512);
			this.sceneDistances = distance;
			this.sceneVisW = 512;
			this.sceneVisH = 334;
			this.sceneVisZoom = 512;
			this.sceneVisDistance = World3D.drawDistance;
			// A window already resizable when the game started was laid out before these existed.
			this.updateSceneVisibility();
			WordFilter.unpack(jagWordenc);

			this.mouseTracking = new MouseTracking(this);
			this.startThread(this.mouseTracking, 10);

			ClientLocAnim.varProvider = this;
			LocType.varProvider = this;
			NpcType.varProvider = this;
		} catch (Exception ignore) {
			signlink.reporterror("loaderror " + this.lastProgressMessage + " " + this.lastProgressPercent);
			this.errorLoading = true;
		}
	}

	@ObfuscatedName("client.a(B)V")
	public void update() {
		if (this.errorStarted || this.errorLoading || this.errorHost) {
			return;
		}

		loopCycle++;

		if (this.ingame) {
			this.updateGame();
		} else {
			this.updateTitle();
		}

		this.updateOnDemand();

		// Plugins get the frame after the client has had it, so a handler reads state the game has
		// already finished updating rather than something half way through a tick.
		if (this.plugins != null) {
			this.plugins.onClientTick(loopCycle);
		}
	}

	@ObfuscatedName("client.c(I)V")
	public void draw() {
		if (this.errorStarted || this.errorLoading || this.errorHost) {
			this.drawError();
			return;
		}

		drawCycle++;

		if (this.layout.resizable) {
			this.prepareResizableFrame();
		}

		if (this.ingame) {
			this.drawGame();
		} else {
			this.drawTitle();
		}

		this.dragCycles = 0;
	}

	@ObfuscatedName("client.b(I)V")
	public void unload() {
		// Shutting down: every plugin gets its shutDown() before the client tears its own state
		// down, so one that writes a file on the way out still can.
		if (this.plugins != null) {
			this.plugins.shutdown();
		}
		this.players = null;
		this.playerIds = null;
		this.entityUpdateIds = null;
		this.playerAppearanceBuffer = null;
		this.entityRemovalIds = null;
		this.areaBackleft1 = null;
		this.areaChatframe = null;
		this.areaBackright1 = null;
		this.areaBackright2 = null;
		this.friendName = null;
		this.friendName37 = null;
		this.friendWorld = null;
		this.areaBackbase1 = null;
		this.areaBackbase2 = null;
		this.areaBackmid1 = null;
		this.varps = null;
		this.sceneMapIndex = null;
		this.sceneMapLandData = null;
		this.sceneMapLocData = null;
		this.sceneMapLandFile = null;
		this.sceneMapLocFile = null;
		this.imageTitle5 = null;
		this.imageTitle6 = null;
		this.imageTitle7 = null;
		this.imageTitle8 = null;
		this.bfsDirection = null;
		this.bfsCost = null;
		this.bfsStepX = null;
		this.bfsStepZ = null;
		this.imageMapdot0 = null;
		this.imageMapdot1 = null;
		this.imageMapdot2 = null;
		this.imageMapdot3 = null;
		this.imageMapdot4 = null;
		if (this.mouseTracking != null) {
			this.mouseTracking.field94 = false;
		}
		this.mouseTracking = null;
		this.imageBackbase1 = null;
		this.imageBackbase2 = null;
		this.imageBackhmid1 = null;
		this.areaBacktop1 = null;
		this.areaBackvmid1 = null;
		this.areaBackvmid2 = null;
		this.areaBackvmid3 = null;
		this.imageTabstones = null;
		this.imageChatbuttons = null;
		this.imageReportbutton = null;
		this.levelHeightmap = null;
		this.levelTileFlags = null;
		this.scene = null;
		this.levelCollisionMap = null;
		this.imageMinimap = null;
		this.imageTitle0 = null;
		this.imageTitle1 = null;
		this.imageTitle2 = null;
		this.imageTitle3 = null;
		this.imageTitle4 = null;
		this.imageCompass = null;
		this.imageHitmarks = null;
		this.imageHeadiconsPk = null;
		this.imageHeadiconsPrayer = null;
		this.imageHeadiconsHint = null;
		this.imageCross = null;
		this.stopMidi();
		this.out = null;
		this.login = null;
		this.in = null;
		this.areaSidebar = null;
		this.areaMapback = null;
		this.areaViewport = null;
		this.areaChatback = null;
		this.imageInvback = null;
		this.imageMapback = null;
		this.imageChatback = null;
		try {
			if (this.stream != null) {
				this.stream.method233();
			}
		} catch (Exception var3) {
		}
		this.stream = null;
		this.activeMapFunctionX = null;
		this.activeMapFunctionZ = null;
		this.activeMapFunctions = null;
		this.npcs = null;
		this.npcIds = null;
		this.textureBuffer = null;
		this.chatPacket = null;
		this.imageMapscene = null;
		this.imageMapfunction = null;
		this.tileLastOccupiedCycle = null;
		this.imageSideicons = null;
		this.projectiles = null;
		this.spotanims = null;
		this.imageOverlayMultiway = null;
		if (this.onDemand != null) {
			this.onDemand.stop();
		}
		this.onDemand = null;
		this.menuParamB = null;
		this.menuParamC = null;
		this.menuAction = null;
		this.menuParamA = null;
		this.menuOption = null;
		this.objStacks = null;
		this.locChanges = null;
		this.unloadTitle();
		LocType.unload();
		NpcType.unload();
		ObjType.unload();
		Component.unload();
		FloType.field796 = null;
		IdkType.field1699 = null;
		UnkType.field66 = null;
		SeqType.field775 = null;
		SpotAnimType.field1297 = null;
		SpotAnimType.field1309 = null;
		VarpType.field1507 = null;
		super.drawArea = null;
		ClientPlayer.field1683 = null;
		Pix3D.unload();
		World3D.unload();
		Model.unload();
		AnimFrame.unload();
		System.gc();
	}

	@ObfuscatedName("client.b(B)V")
	public void refresh() {
		this.redrawFrame = true;
	}

	@ObfuscatedName("client.a(IZLjava/lang/String;)V")
	public void drawProgress(int percent, String message) {
		// load() draws these before the game loop has started, so nothing else has looked at the
		// window yet: a resizable window is centred here rather than at the first game frame.
		this.syncLayout();
		if (this.layout.resizable) {
			this.prepareResizableFrame();
		}
		this.lastProgressPercent = percent;
		this.lastProgressMessage = message;
		this.loadTitle();
		if (this.jagTitle == null) {
			super.drawProgress(percent, message);
			return;
		}
		this.imageTitle4.bind();
		short var4 = 360;
		short var5 = 200;
		byte var6 = 20;
		this.fontBold12.centreString(var4 / 2, var5 / 2 - 26 - var6, 16777215, SERVER_NAME + " is loading - please wait...");
		int var7 = var5 / 2 - 18 - var6;
		Pix2D.drawRect(var7, 34, 9179409, var4 / 2 - 152, 304);
		Pix2D.drawRect(var7 + 1, 32, 0, var4 / 2 - 151, 302);
		Pix2D.fillRect(30, var7 + 2, 9179409, percent * 3, var4 / 2 - 150);
		Pix2D.fillRect(30, var7 + 2, 0, 300 - percent * 3, percent * 3 + (var4 / 2 - 150));
		this.fontBold12.centreString(var4 / 2, var5 / 2 + 5 - var6, 16777215, message);
		this.imageTitle4.draw(171, 202, super.graphics);
		if (this.redrawFrame) {
			this.redrawFrame = false;
			if (!this.flameActive) {
				this.imageTitle0.draw(0, 0, super.graphics);
				this.imageTitle1.draw(0, 637, super.graphics);
			}
			this.imageTitle2.draw(0, 128, super.graphics);
			this.imageTitle3.draw(371, 202, super.graphics);
			this.imageTitle5.draw(265, 0, super.graphics);
			this.imageTitle6.draw(265, 562, super.graphics);
			this.imageTitle7.draw(171, 128, super.graphics);
			this.imageTitle8.draw(171, 562, super.graphics);
		}
	}

	@ObfuscatedName("client.G(I)V")
	public void drawError() {
		Graphics var2 = this.getBaseComponent().getGraphics();
		var2.setColor(Color.black);
		// the whole window, not just the fixed frame: an error page in a resizable window would
		// otherwise leave the title art showing around its edges
		var2.fillRect(0, 0, Math.max(765, this.layout.width), Math.max(503, this.layout.height));
		this.setFramerate(1);
		if (this.errorLoading) {
			this.flameActive = false;
			var2.setFont(new Font("Helvetica", 1, 16));
			var2.setColor(Color.yellow);
			byte var4 = 35;
			var2.drawString("Sorry, an error has occured whilst loading " + SERVER_NAME, 30, var4);
			int var6 = var4 + 50;
			var2.setColor(Color.white);
			var2.drawString("To fix this try the following (in order):", 30, var6);
			int var7 = var6 + 50;
			var2.setColor(Color.white);
			var2.setFont(new Font("Helvetica", 1, 12));
			var2.drawString("1: Try closing ALL open web-browser windows, and reloading", 30, var7);
			int var8 = var7 + 30;
			var2.drawString("2: Try clearing your web-browsers cache from tools->internet options", 30, var8);
			int var9 = var8 + 30;
			var2.drawString("3: Try using a different game-world", 30, var9);
			int var11 = var9 + 30;
			var2.drawString("4: Try rebooting your computer", 30, var11);
			int var13 = var11 + 30;
			var2.drawString("5: Try selecting a different version of Java from the play-game menu", 30, var13);
		}
		if (this.errorHost) {
			this.flameActive = false;
			var2.setFont(new Font("Helvetica", 1, 20));
			var2.setColor(Color.white);
			var2.drawString("Error - unable to load game!", 50, 50);
			var2.drawString("To play " + SERVER_NAME + " make sure you play from", 50, 100);
			var2.drawString("the official " + SERVER_NAME + " launcher", 50, 150);
		}
		if (this.errorStarted) {
			this.flameActive = false;
			var2.setColor(Color.yellow);
			byte var5 = 35;
			var2.drawString("Error a copy of " + SERVER_NAME + " already appears to be loaded", 30, var5);
			int var10 = var5 + 50;
			var2.setColor(Color.white);
			var2.drawString("To fix this try the following (in order):", 30, var10);
			int var12 = var10 + 50;
			var2.setColor(Color.white);
			var2.setFont(new Font("Helvetica", 1, 12));
			var2.drawString("1: Try closing ALL open web-browser windows, and reloading", 30, var12);
			int var14 = var12 + 30;
			var2.drawString("2: Try rebooting your computer, and reloading", 30, var14);
			int var15 = var14 + 30;
		}
	}

	@ObfuscatedName("client.a(IILjava/lang/String;IILjava/lang/String;)LATJMVOZR;")
	public Jagfile getJagFile(int arg1, String arg2, int arg3, int arg4, String arg5) {
		byte[] var7 = null;
		int var8 = 5;
		try {
			if (this.fileStreams[0] != null) {
				var7 = this.fileStreams[0].read(arg4);
			}
		} catch (Exception var30) {
		}
		if (var7 != null) {
			this.crc32.reset();
			this.crc32.update(var7);
			int var9 = (int) this.crc32.getValue();
			if (arg1 != var9) {
				var7 = null;
			}
		}
		if (var7 != null) {
			return new Jagfile(var7);
		}
		int var11 = 0;
		while (var7 == null) {
			String var12 = "Unknown error";
			this.drawProgress(arg3, "Requesting " + arg5);
			Object var13 = null;
			try {
				int var14 = 0;
				DataInputStream var15 = this.openUrl(arg2 + arg1);
				byte[] var16 = new byte[6];
				var15.readFully(var16, 0, 6);
				Packet var17 = new Packet(var16);
				var17.pos = 3;
				int var18 = var17.g3() + 6;
				int var19 = 6;
				var7 = new byte[var18];
				for (int var20 = 0; var20 < 6; var20++) {
					var7[var20] = var16[var20];
				}
				while (var19 < var18) {
					int var21 = var18 - var19;
					if (var21 > 1000) {
						var21 = 1000;
					}
					int var22 = var15.read(var7, var19, var21);
					if (var22 < 0) {
						(new StringBuffer("Length error: ")).append(var19).append("/").append(var18).toString();
						throw new IOException("EOF");
					}
					var19 += var22;
					int var23 = var19 * 100 / var18;
					if (var14 != var23) {
						this.drawProgress(arg3, "Loading " + arg5 + " - " + var23 + "%");
					}
					var14 = var23;
				}
				var15.close();
				try {
					if (this.fileStreams[0] != null) {
						this.fileStreams[0].write(var7.length, var7, arg4);
					}
				} catch (Exception var29) {
					this.fileStreams[0] = null;
				}
				if (var7 != null) {
					this.crc32.reset();
					this.crc32.update(var7);
					int var24 = (int) this.crc32.getValue();
					if (arg1 != var24) {
						var7 = null;
						var11++;
						var12 = "Checksum error: " + var24;
					}
				}
			} catch (IOException var31) {
				if (var12.equals("Unknown error")) {
					var12 = "Connection error";
				}
				var7 = null;
			} catch (NullPointerException var32) {
				var12 = "Null error";
				var7 = null;
				if (!signlink.reporterror) {
					return null;
				}
			} catch (ArrayIndexOutOfBoundsException var33) {
				var12 = "Bounds error";
				var7 = null;
				if (!signlink.reporterror) {
					return null;
				}
			} catch (Exception var34) {
				var12 = "Unexpected error";
				var7 = null;
				if (!signlink.reporterror) {
					return null;
				}
			}
			if (var7 == null) {
				for (int var26 = var8; var26 > 0; var26--) {
					if (var11 >= 3) {
						this.drawProgress(arg3, "Game updated - please reload page");
						var26 = 10;
					} else {
						this.drawProgress(arg3, var12 + " - Retrying in " + var26);
					}
					try {
						Thread.sleep(1000L);
					} catch (Exception var28) {
					}
				}
				var8 *= 2;
				if (var8 > 60) {
					var8 = 60;
				}
				this.field196 = !this.field196;
			}
		}
		return new Jagfile(var7);
	}

	@ObfuscatedName("client.j(Z)V")
	public void updateOnDemand() {
		while (true) {
			OnDemandRequest req = this.onDemand.cycle();
			if (req == null) {
				return;
			}
			if (req.archive == 0) {
				Model.method357(req.data, req.file, (byte) 7);
				if ((this.onDemand.getModelFlags(req.file) & 0x62) != 0) {
					this.redrawSidebar = true;
					if (this.chatInterfaceId != -1 || this.stickyChatInterfaceId != -1) {
						this.redrawChatback = true;
					}
				}
			}
			if (req.archive == 1 && req.data != null) {
				AnimFrame.method262(req.data);
			}
			if (req.archive == 2 && this.midiSong == req.file && req.data != null) {
				this.saveMidi(this.midiFading, req.data);
			}
			if (req.archive == 3 && this.sceneState == 1) {
				for (int var3 = 0; var3 < this.sceneMapLandData.length; var3++) {
					if (this.sceneMapLandFile[var3] == req.file) {
						this.sceneMapLandData[var3] = req.data;
						if (req.data == null) {
							this.sceneMapLandFile[var3] = -1;
						}
						break;
					}
					if (this.sceneMapLocFile[var3] == req.file) {
						this.sceneMapLocData[var3] = req.data;
						if (req.data == null) {
							this.sceneMapLocFile[var3] = -1;
						}
						break;
					}
				}
			}
			if (req.archive == 93 && this.onDemand.hasMapLocFile(req.file)) {
				World.method17(this.onDemand, new Packet(req.data));
			}
		}
	}

	@ObfuscatedName("client.K(I)V")
	public void updateTitle() {
		if (this.titleScreenState == 0) {
			int var2 = super.canvasWidth / 2 - 80;
			int var3 = super.canvasHeight / 2 + 20;
			int var14 = var3 + 20;
			if (super.mouseClickButton == 1 && super.mouseClickX >= var2 - 75 && super.mouseClickX <= var2 + 75 && super.mouseClickY >= var14 - 20 && super.mouseClickY <= var14 + 20) {
				this.titleScreenState = 3;
				this.titleLoginField = 0;
			}
			int var4 = super.canvasWidth / 2 + 80;
			if (super.mouseClickButton == 1 && super.mouseClickX >= var4 - 75 && super.mouseClickX <= var4 + 75 && super.mouseClickY >= var14 - 20 && super.mouseClickY <= var14 + 20) {
				this.loginMessage0 = "";
				this.loginMessage1 = "Enter your username & password.";
				this.titleScreenState = 2;
				this.titleLoginField = 0;
			}
		} else if (this.titleScreenState == 2) {
			int var5 = super.canvasHeight / 2 - 40;
			int var15 = var5 + 30;
			int var16 = var15 + 25;
			if (super.mouseClickButton == 1 && super.mouseClickY >= var16 - 15 && super.mouseClickY < var16) {
				this.titleLoginField = 0;
			}
			var5 = var16 + 15;
			if (super.mouseClickButton == 1 && super.mouseClickY >= var5 - 15 && super.mouseClickY < var5) {
				this.titleLoginField = 1;
			}
			var5 += 15;
			int var6 = super.canvasWidth / 2 - 80;
			int var7 = super.canvasHeight / 2 + 50;
			int var17 = var7 + 20;
			if (super.mouseClickButton == 1 && super.mouseClickX >= var6 - 75 && super.mouseClickX <= var6 + 75 && super.mouseClickY >= var17 - 20 && super.mouseClickY <= var17 + 20) {
				this.loginRetryCount = 0;
				this.login(this.username, this.password, false);
				if (this.ingame) {
					return;
				}
			}
			int var8 = super.canvasWidth / 2 + 80;
			if (super.mouseClickButton == 1 && super.mouseClickX >= var8 - 75 && super.mouseClickX <= var8 + 75 && super.mouseClickY >= var17 - 20 && super.mouseClickY <= var17 + 20) {
				this.titleScreenState = 0;
				this.username = "";
				this.password = "";
			}
			while (true) {
				int var9 = this.pollKey();
				if (var9 == -1) {
					return;
				}
				boolean var10 = false;
				for (int var11 = 0; var11 < CHARSET.length(); var11++) {
					if (var9 == CHARSET.charAt(var11)) {
						var10 = true;
						break;
					}
				}
				if (this.titleLoginField == 0) {
					if (var9 == 8 && this.username.length() > 0) {
						this.username = this.username.substring(0, this.username.length() - 1);
					}
					if (var9 == 9 || var9 == 10 || var9 == 13) {
						this.titleLoginField = 1;
					}
					if (var10) {
						this.username = this.username + (char) var9;
					}
					if (this.username.length() > 12) {
						this.username = this.username.substring(0, 12);
					}
				} else if (this.titleLoginField == 1) {
					if (var9 == 8 && this.password.length() > 0) {
						this.password = this.password.substring(0, this.password.length() - 1);
					}
					if (var9 == 9 || var9 == 10 || var9 == 13) {
						this.titleLoginField = 0;
					}
					if (var10) {
						this.password = this.password + (char) var9;
					}
					if (this.password.length() > 20) {
						this.password = this.password.substring(0, 20);
					}
				}
			}
		} else if (this.titleScreenState == 3) {
			int var12 = super.canvasWidth / 2;
			int var13 = super.canvasHeight / 2 + 50;
			int var18 = var13 + 20;
			if (super.mouseClickButton == 1 && super.mouseClickX >= var12 - 75 && super.mouseClickX <= var12 + 75 && super.mouseClickY >= var18 - 20 && super.mouseClickY <= var18 + 20) {
				this.titleScreenState = 0;
			}
		}
	}

	@ObfuscatedName("client.a(Ljava/lang/String;Ljava/lang/String;Z)V")
	public void login(String arg0, String arg1, boolean arg2) {
		signlink.errorname = arg0;
		try {
			if (!arg2) {
				this.loginMessage0 = "";
				this.loginMessage1 = "Connecting to server...";
				this.drawTitle();
			}
			this.stream = new ClientStream(this.openSocket(portOffset + GAME_PORT), this);
			long var4 = JString.toBase37(arg0);
			int var6 = (int) (var4 >> 16 & 0x1FL);
			this.out.pos = 0;
			this.out.p1(14);
			this.out.p1(var6);
			this.stream.write(2, 0, this.out.data);
			for (int var7 = 0; var7 < 8; var7++) {
				this.stream.method234();
			}
			int var8 = this.stream.method234();
			int var9 = var8;
			if (var8 == 0) {
				this.stream.read(this.in.data, 0, 8);
				this.in.pos = 0;
				this.serverSeed = this.in.g8();
				int[] var10 = new int[] { (int) (Math.random() * 9.9999999E7D), (int) (Math.random() * 9.9999999E7D), (int) (this.serverSeed >> 32), (int) this.serverSeed };
				this.out.pos = 0;
				this.out.p1(10);
				this.out.p4(var10[0]);
				this.out.p4(var10[1]);
				this.out.p4(var10[2]);
				this.out.p4(var10[3]);
				this.out.p4(signlink.uid);
				this.out.pjstr(arg0);
				this.out.pjstr(arg1);
				this.out.rsaenc(LOGIN_RSAN, LOGIN_RSAE);
				this.login.pos = 0;
				if (arg2) {
					this.login.p1(18);
				} else {
					this.login.p1(16);
				}
				this.login.p1(this.out.pos + 36 + 1 + 1 + 2);
				this.login.p1(255);
				// Build handshake, not the RS protocol revision - must match
				// Environment.ENGINE_REVISION on the server, or login is refused with
				// "your client is out of date". 378 = the walk-merge skeleton guard. 379 = P_DIALOGPROMPT,
				// server prot 9, which an older client has no length for. 380 = the login RSA key rotation.
				// 381 = chat as typed (ChatText): public, private and clan lines are their characters, not WordPack.
				this.login.p2(381);
				// 0x1 low memory. 0x2 = this client draws Old School's hitsplats (venom, heal, max hit -
				// hitmarks 5-7); the server sends a client without the bit plain poison and damage splats
				// instead, so a jar from before them keeps working rather than being turned away.
				this.login.p1((lowMem ? 1 : 0) | 0x2);
				for (int var11 = 0; var11 < 9; var11++) {
					this.login.p4(this.jagChecksum[var11]);
				}
				this.login.pdata(this.out.data, this.out.pos, 0);
				this.out.field1284 = new Isaac(var10);
				for (int var12 = 0; var12 < 4; var12++) {
					var10[var12] += 50;
				}
				this.randomIn = new Isaac(var10);
				this.stream.write(this.login.pos, 0, this.login.data);
				var8 = this.stream.method234();
			}
			if (var8 == 1) {
				try {
					Thread.sleep(2000L);
				} catch (Exception var22) {
				}
				this.login(arg0, arg1, arg2);
			} else if (var8 == 2) {
				this.staffmodlevel = this.stream.method234();
				field258 = this.stream.method234() == 1;
				this.lastMouseClickTime = 0L;
				this.field595 = 0;
				this.mouseTracking.field99 = 0;
				super.hasFocus = true;
				this.field571 = true;
				this.ingame = true;
				DevLog.log("SESSION", "logged in as \"" + arg0 + "\"");
				this.out.pos = 0;
				this.in.pos = 0;
				this.ptype = -1;
				this.ptype0 = -1;
				this.ptype1 = -1;
				this.ptype2 = -1;
				this.psize = 0;
				this.idleNetCycles = 0;
				this.systemUpdateTimer = 0;
				this.idleTimeout = 0;
				this.hintType = 0;
				this.menuSize = 0;
				this.menuVisible = false;
				super.idleCycles = 0;
				for (int var13 = 0; var13 < 100; var13++) {
					this.messageText[var13] = null;
				}
				this.objSelected = 0;
				this.spellSelected = 0;
				this.sceneState = 0;
				this.waveCount = 0;
				// See MACRO_ANTIBOT_CAMERA_JITTER above - retail randomizes these on login, which is
				// exactly why the very first frame after logging in can already look off-center.
				if (MACRO_ANTIBOT_CAMERA_JITTER) {
					this.macroCameraX = (int) (Math.random() * 100.0D) - 50;
					this.macroCameraZ = (int) (Math.random() * 110.0D) - 55;
					this.macroCameraAngle = (int) (Math.random() * 80.0D) - 40;
					this.macroMinimapAngle = (int) (Math.random() * 120.0D) - 60;
					this.macroMinimapZoom = (int) (Math.random() * 30.0D) - 20;
				} else {
					this.macroCameraX = 0;
					this.macroCameraZ = 0;
					this.macroCameraAngle = 0;
					this.macroMinimapAngle = 0;
					this.macroMinimapZoom = 0;
				}
				this.orbitCameraYaw = (int) (Math.random() * 20.0D) - 10 & 0x7FF;
				this.minimapType = 0;
				this.minimapLevel = -1;
				this.flagSceneTileX = 0;
				this.flagSceneTileZ = 0;
				this.playerCount = 0;
				this.npcCount = 0;
				for (int var14 = 0; var14 < this.MAX_PLAYER_COUNT; var14++) {
					this.players[var14] = null;
					this.playerAppearanceBuffer[var14] = null;
				}
				for (int var15 = 0; var15 < 16384; var15++) {
					this.npcs[var15] = null;
				}
				localPlayer = this.players[this.LOCAL_PLAYER_INDEX] = new ClientPlayer();
				this.projectiles.clear();
				this.spotanims.clear();
				for (int var16 = 0; var16 < 4; var16++) {
					for (int var17 = 0; var17 < 104; var17++) {
						for (int var18 = 0; var18 < 104; var18++) {
							this.objStacks[var16][var17][var18] = null;
						}
					}
				}
				this.locChanges = new LinkList();
				this.friendlistStatus = 0;
				this.friendCount = 0;
				this.unloadCom(this.stickyChatInterfaceId);
				this.stickyChatInterfaceId = -1;
				this.unloadCom(this.chatInterfaceId);
				this.chatInterfaceId = -1;
				this.unloadCom(this.viewportInterfaceId);
				this.viewportInterfaceId = -1;
				this.unloadCom(this.fullscreenInterfaceId0);
				this.fullscreenInterfaceId0 = -1;
				this.unloadCom(this.fullscreenInterfaceId1);
				this.fullscreenInterfaceId1 = -1;
				this.unloadCom(this.sidebarInterfaceId);
				this.sidebarInterfaceId = -1;
				this.unloadCom(this.viewportOverlayInterfaceId);
				this.viewportOverlayInterfaceId = -1;
				this.pressedContinueOption = false;
				this.selectedTab = 3;
				this.chatbackInputOpen = 0;
				this.menuVisible = false;
				this.showSocialInput = false;
				this.modalMessage = null;
				this.inMultizone = 0;
				this.flashingTab = -1;
				this.designGender = true;
				this.validateCharacterDesign();
				for (int var19 = 0; var19 < 5; var19++) {
					this.designColours[var19] = 0;
				}
				for (int var20 = 0; var20 < 5; var20++) {
					this.playerOps[var20] = null;
					this.playerOpPrimary[var20] = false;
				}
				oplogic1 = 0;
				oplogic2 = 0;
				oplogic3 = 0;
				oplogic4 = 0;
				oplogic5 = 0;
				oplogic6 = 0;
				oplogic7 = 0;
				oplogic8 = 0;
				oplogic9 = 0;
				oplogic10 = 0;
				this.prepareGame();
			} else if (var8 == 3) {
				this.loginMessage0 = "";
				this.loginMessage1 = "Invalid username or password.";
			} else if (var8 == 4) {
				this.loginMessage0 = "Your account has been disabled.";
				this.loginMessage1 = "Please check your message-centre for details.";
			} else if (var8 == 5) {
				this.loginMessage0 = "Your account is already logged in.";
				this.loginMessage1 = "Try again in 60 secs...";
			} else if (var8 == 6) {
				this.loginMessage0 = SERVER_NAME + " has been updated!";
				this.loginMessage1 = "Please reload this page.";
				if (this.relaunchForUpdate()) {
					this.loginMessage1 = "Updating - restarting in a moment...";
				} else if (System.getProperty(UPDATE_ATTEMPT) != null) {
					// already came back from the launcher with the newest client and the server still
					// says no: it is the SERVER that is mid-update, so there is nothing to download
					this.loginMessage0 = "Already up to date.";
					this.loginMessage1 = "Please try again in a few minutes.";
				}
			} else if (var8 == 7) {
				this.loginMessage0 = "This world is full.";
				this.loginMessage1 = "Please use a different world.";
			} else if (var8 == 8) {
				this.loginMessage0 = "Unable to connect.";
				this.loginMessage1 = "Login server offline.";
			} else if (var8 == 9) {
				this.loginMessage0 = "Login limit exceeded.";
				this.loginMessage1 = "Too many connections from your address.";
			} else if (var8 == 10) {
				this.loginMessage0 = "Unable to connect.";
				this.loginMessage1 = "Bad session id.";
			} else if (var8 == 12) {
				this.loginMessage0 = "You need a members account to login to this world.";
				this.loginMessage1 = "Please subscribe, or use a different world.";
			} else if (var8 == 13) {
				this.loginMessage0 = "Could not complete login.";
				this.loginMessage1 = "Please try using a different world.";
			} else if (var8 == 14) {
				this.loginMessage0 = "The server is being updated.";
				this.loginMessage1 = "Please wait 1 minute and try again.";
			} else if (var8 == 15) {
				this.ingame = true;
				DevLog.log("SESSION", "logged in as \"" + arg0 + "\"");
				this.out.pos = 0;
				this.in.pos = 0;
				this.ptype = -1;
				this.ptype0 = -1;
				this.ptype1 = -1;
				this.ptype2 = -1;
				this.psize = 0;
				this.idleNetCycles = 0;
				this.systemUpdateTimer = 0;
				this.menuSize = 0;
				this.menuVisible = false;
				this.sceneLoadStartTime = System.currentTimeMillis();
				this.sceneLoadBegan = System.currentTimeMillis();
				this.sceneStuckShown = null;
			} else if (var8 == 16) {
				this.loginMessage0 = "Login attempts exceeded.";
				this.loginMessage1 = "Please wait 1 minute and try again.";
			} else if (var8 == 17) {
				this.loginMessage0 = "You are standing in a members-only area.";
				this.loginMessage1 = "To play on this world move to a free area first";
			} else if (var8 == 18) {
				this.loginMessage0 = "Account locked as we suspect it has been stolen.";
				this.loginMessage1 = "Press 'recover a locked account' on front page.";
			} else if (var8 == 20) {
				this.loginMessage0 = "Invalid loginserver requested";
				this.loginMessage1 = "Please try using a different world.";
			} else if (var8 == 21) {
				int var21 = this.stream.method234();
				for (int var26 = var21 + 3; var26 >= 0; var26--) {
					this.loginMessage0 = "You have only just left another world";
					this.loginMessage1 = "Your profile will be transferred in: " + var26;
					this.drawTitle();
					try {
						Thread.sleep(1200L);
					} catch (Exception var23) {
					}
				}
				this.login(arg0, arg1, arg2);
			} else if (var8 == 22) {
				this.loginMessage0 = "Malformed login packet.";
				this.loginMessage1 = "Please try again.";
			} else if (var8 == 23) {
				this.loginMessage0 = "No reply from loginserver.";
				this.loginMessage1 = "Please try again.";
			} else if (var8 == 24) {
				this.loginMessage0 = "Error loading your profile.";
				this.loginMessage1 = "Please contact customer support.";
			} else if (var8 == 25) {
				this.loginMessage0 = "Unexpected loginserver response.";
				this.loginMessage1 = "Please try using a different world.";
			} else if (var8 == 26) {
				this.loginMessage0 = "This computers address has been blocked";
				this.loginMessage1 = "as it was used to break our rules";
			} else if (var8 != -1) {
				System.out.println("response:" + var8);
				this.loginMessage0 = "Unexpected server response";
				this.loginMessage1 = "Please try using a different world.";
			} else if (var9 != 0) {
				this.loginMessage0 = "No response from server";
				this.loginMessage1 = "Please try using a different world.";
			} else if (this.loginRetryCount < 2) {
				try {
					Thread.sleep(2000L);
				} catch (Exception var24) {
				}
				this.loginRetryCount++;
				this.login(arg0, arg1, arg2);
			} else {
				this.loginMessage0 = "No response from loginserver";
				this.loginMessage1 = "Please wait 1 minute and try again.";
			}
		} catch (IOException var25) {
			this.loginMessage0 = "";
			this.loginMessage1 = "Error connecting to server.";
		}
	}

	@ObfuscatedName("client.n(Z)V")
	public void logout() {
		DevLog.log("SESSION", "logged out, was \"" + this.username + "\"");
		try {
			if (this.stream != null) {
				this.stream.method233();
			}
		} catch (Exception var3) {
		}
		this.stream = null;
		this.ingame = false;
		this.areaSounds.stopAll();
		this.titleScreenState = 0;
		this.username = "";
		this.password = "";
		this.clearCache();
		this.scene.method274();
		for (int var2 = 0; var2 < 4; var2++) {
			this.levelCollisionMap[var2].method532();
		}
		System.gc();
		this.stopMidi();
		this.nextMidiSong = -1;
		this.midiSong = -1;
		this.nextMusicDelay = 0;
	}

	@ObfuscatedName("client.k(I)V")
	public void clearCache() {
		LocType.field1633.clear();
		LocType.field1616.clear();
		NpcType.field1438.clear();
		ObjType.field819.clear();
		ObjType.field828.clear();
		ClientPlayer.field1683.clear();
		SpotAnimType.field1309.clear();
	}

	@ObfuscatedName("client.F(I)V")
	public void prepareGame() {
		if (this.areaChatback != null) {
			return;
		}
		this.unloadTitle();
		super.drawArea = null;
		this.imageTitle2 = null;
		this.imageTitle3 = null;
		this.imageTitle4 = null;
		this.imageTitle0 = null;
		this.imageTitle1 = null;
		this.imageTitle5 = null;
		this.imageTitle6 = null;
		this.imageTitle7 = null;
		this.imageTitle8 = null;
		this.areaChatback = new PixMap(CHAT_H, this.getBaseComponent(), CHAT_W);
		this.areaMapback = new PixMap(156, this.getBaseComponent(), 172);
		Pix2D.cls();
		this.imageMapback.plotSprite(0, 0);
		this.areaSidebar = new PixMap(261, this.getBaseComponent(), 190);
		this.createViewportArea();
		Pix2D.cls();
		this.areaBackbase1 = new PixMap(23, this.getBaseComponent(), 519);
		this.areaBackbase2 = new PixMap(37, this.getBaseComponent(), 246);
		this.areaBackmid1 = new PixMap(45, this.getBaseComponent(), 249);
		this.redrawFrame = true;
		this.areaViewport.bind();
		Pix3D.lineOffset = this.areaViewportOffset;
	}

	@ObfuscatedName("client.e(B)V")
	public void updateGame() {
		if (this.systemUpdateTimer > 1) {
			this.systemUpdateTimer--;
		}

		if (this.idleTimeout > 0) {
			this.idleTimeout--;
		}

		for (int i = 0; i < 5 && this.readPacket(); i++) {
		}

		if (this.ingame) {
			Object lock = this.mouseTracking.lock;
			synchronized (lock) {
				if (!field258) {
					this.mouseTracking.field99 = 0;
				} else if (super.mouseClickButton != 0 || this.mouseTracking.field99 >= 40) {
					// EVENT_MOUSE_MOVE
					this.out.p1isaac(171);
					this.out.p1(0);
					int var4 = this.out.pos;
					int var5 = 0;
					for (int var6 = 0; var6 < this.mouseTracking.field99 && var4 - this.out.pos < 240; var6++) {
						var5++;
						int var7 = this.mouseTracking.field95[var6];
						if (var7 < 0) {
							var7 = 0;
						} else if (var7 > 502) {
							var7 = 502;
						}
						int var8 = this.mouseTracking.field100[var6];
						if (var8 < 0) {
							var8 = 0;
						} else if (var8 > 764) {
							var8 = 764;
						}
						int var9 = var7 * 765 + var8;
						if (this.mouseTracking.field95[var6] == -1 && this.mouseTracking.field100[var6] == -1) {
							var8 = -1;
							var7 = -1;
							var9 = 524287;
						}
						if (this.field307 != var8 || this.field308 != var7) {
							int var10 = var8 - this.field307;
							this.field307 = var8;
							int var11 = var7 - this.field308;
							this.field308 = var7;
							if (this.field595 < 8 && var10 >= -32 && var10 <= 31 && var11 >= -32 && var11 <= 31) {
								var10 += 32;
								var11 += 32;
								this.out.p2((this.field595 << 12) + (var10 << 6) + var11);
								this.field595 = 0;
							} else if (this.field595 < 8) {
								this.out.p3((this.field595 << 19) + 8388608 + var9);
								this.field595 = 0;
							} else {
								this.out.p4((this.field595 << 19) + -1073741824 + var9);
								this.field595 = 0;
							}
						} else if (this.field595 < 2047) {
							this.field595++;
						}
					}
					this.out.psize1(this.out.pos - var4);
					if (var5 >= this.mouseTracking.field99) {
						this.mouseTracking.field99 = 0;
					} else {
						this.mouseTracking.field99 -= var5;
						for (int var12 = 0; var12 < this.mouseTracking.field99; var12++) {
							this.mouseTracking.field100[var12] = this.mouseTracking.field100[var5 + var12];
							this.mouseTracking.field95[var12] = this.mouseTracking.field95[var5 + var12];
						}
					}
				}
			}

			if (super.mouseClickButton != 0) {
				long var13 = (super.mouseClickTime - this.lastMouseClickTime) / 50L;
				if (var13 > 4095L) {
					var13 = 4095L;
				}
				this.lastMouseClickTime = super.mouseClickTime;
				int var15 = super.mouseClickY;
				if (var15 < 0) {
					var15 = 0;
				} else if (var15 > 502) {
					var15 = 502;
				}
				int var16 = super.mouseClickX;
				if (var16 < 0) {
					var16 = 0;
				} else if (var16 > 764) {
					var16 = 764;
				}
				int var17 = var15 * 765 + var16;
				byte var18 = 0;
				if (super.mouseClickButton == 2) {
					var18 = 1;
				}
				int var19 = (int) var13;
				// EVENT_MOUSE_CLICK
				this.out.p1isaac(19);
				this.out.p4((var18 << 19) + (var19 << 20) + var17);
			}

			if (this.cameraPositionEventTimer > 0) {
				this.cameraPositionEventTimer--;
			}

			if (super.actionKey[1] == 1 || super.actionKey[2] == 1 || super.actionKey[3] == 1 || super.actionKey[4] == 1) {
				this.movingCamera = true;
			}

			if (this.movingCamera && this.cameraPositionEventTimer <= 0) {
				this.cameraPositionEventTimer = 20;
				this.movingCamera = false;

				// todo: camera position
				this.out.p1isaac(140);
				this.out.p2_alt1(this.orbitCameraPitch);
				this.out.p2_alt1(this.orbitCameraYaw);
			}

			if (super.hasFocus && !this.field571) {
				this.field571 = true;

				// todo: applet focus
				this.out.p1isaac(187);
				this.out.p1(1);
			}

			if (!super.hasFocus && this.field571) {
				this.field571 = false;

				// todo: applet focus
				this.out.p1isaac(187);
				this.out.p1(0);
			}

			this.updateSceneState();
			this.updateLocChanges();
			this.updateAudio();

			this.idleNetCycles++;
			if (this.idleNetCycles > 750) {
				this.tryReconnect();
			}

			this.updatePlayers();
			this.updateNpcs();
			this.updateEntityChats();

			this.sceneDelta++;

			if (this.crossMode != 0) {
				this.crossCycle += 20;

				if (this.crossCycle >= 400) {
					this.crossMode = 0;
				}
			}

			if (this.selectedArea != 0) {
				this.selectedCycle++;

				if (this.selectedCycle >= 15) {
					if (this.selectedArea == 2) {
						this.redrawSidebar = true;
					}

					if (this.selectedArea == 3) {
						this.redrawChatback = true;
					}

					this.selectedArea = 0;
				}
			}

			// Bank tabs: a tab dragged onto another tab swaps the two. This is its own little drag
			// loop because objDragArea only ever starts on an inventory slot, and it sits before that
			// block so a tab drag is settled whether or not an item drag is also in flight.
			//
			// It rides INV_BUTTOND like the drop-on-a-tab gesture does, in a distinct mode range:
			// 150 + (from - 1) * 8 + (to - 1), which for 1..8 x 1..8 is 150..213 and fits the byte.
			// slot and targetSlot are both 0, which is a real occupied slot whenever any tab exists
			// at all - the bank list is kept gapless - so the engine accepts the packet.
			if (this.bankTabDragFrom >= 1 && super.mouseButton == 0) {
				int tabFrom = this.bankTabDragFrom;
				int tabTo = this.bankTabHovered;
				this.bankTabDragFrom = -1;
				boolean moved = super.mouseX > this.bankTabGrabX + 5 || super.mouseX < this.bankTabGrabX - 5 ||
					super.mouseY > this.bankTabGrabY + 5 || super.mouseY < this.bankTabGrabY - 5;
				if (moved && tabTo >= 1 && tabTo != tabFrom && this.bankGridCom != -1) {
					this.out.p1isaac(123);
					this.out.p2_alt3(0);
					this.out.p1_alt1(150 + (tabFrom - 1) * 8 + tabTo - 1);
					this.out.p2_alt2(this.bankGridCom);
					this.out.p2_alt1(0);
				}
			}
			if (this.objDragArea != 0) {
				this.objDragCycles++;

				if (super.mouseX > this.objGrabX + 5 || super.mouseX < this.objGrabX - 5 || super.mouseY > this.objGrabY + 5 || super.mouseY < this.objGrabY - 5) {
					this.objGrabThreshold = true;
				}

				if (super.mouseButton == 0) {
					if (this.objDragArea == 2) {
						this.redrawSidebar = true;
					}

					if (this.objDragArea == 3) {
						this.redrawChatback = true;
					}

					this.objDragArea = 0;

					// A click that moves a few pixels while held is a drag after 5 client cycles (100ms) -
					// Old School's own default - or, with Anti-drag on, after 10 (200ms): long enough that a
					// fast switch whose mouse is still moving as the button comes up stays a click on the
					// item, short enough to lay out an inventory. (30, RuneLite's default, made rearranging
					// switches a chore.) Client settings (F9) turns it off.
					int dragCycles = QolSettings.on(QolSettings.ANTI_DRAG) ? 10 : 5;
					if (this.objGrabThreshold && this.objDragCycles >= dragCycles) {
						this.hoveredSlotInterfaceId = -1;
						this.bankTabHovered = -1;
						this.hoveredSlotPad = false;
						this.handleInput();

						// Bank tabs: dropped on a tab button rather than on another slot. The packet below
						// carries ONE component and both slots are validated against that same inv server
						// side, so a tab button can never be the target - the tab rides the mode byte, which
						// the server used to decode and discard. Sending slot == targetSlot keeps the packet
						// valid; the script reads last_dragmode and ignores the target.
						if (this.bankTabHovered >= 0 && this.objDragInterfaceId != -1 && Component.get(this.objDragInterfaceId).clientCode == 206) {
							this.out.p1isaac(123);
							this.out.p2_alt3(this.objDragSlot);
							this.out.p1_alt1(100 + this.bankTabHovered);
							this.out.p2_alt2(this.objDragInterfaceId);
							this.out.p2_alt1(this.objDragSlot);
							this.bankTabHovered = -1;
						} else if (this.objDragInterfaceId == this.hoveredSlotInterfaceId && this.objDragSlot != this.hoveredSlot) {
							Component com = Component.get(this.objDragInterfaceId);

							int mode = 0;
							if (this.bankArrangeMode == 1 && com.clientCode == 206) {
								mode = 1;
							}
							if (com.invSlotObjId[this.hoveredSlot] <= 0) {
								mode = 0;
							}
							boolean append = com.clientCode == 206 && this.hoveredSlotPad;
							if (append) {
								// Dropped on the blank space after a tab's items: append to that tab.
								// The target slot is that tab's LAST item, which is all the server
								// needs to work out which tab was meant.
								mode = 220;
							}

							if (append) {
								// Deliberately no optimistic update. The server moves a whole run and
								// resends the inv; guessing here is what made a drag across a break
								// flicker a different item into the next tab until the bank was
								// closed and reopened.
							} else if (com.swappable) {
								int src = this.objDragSlot;
								int dst = this.hoveredSlot;
								com.invSlotObjId[dst] = com.invSlotObjId[src];
								com.invSlotObjCount[dst] = com.invSlotObjCount[src];
								com.invSlotObjId[src] = -1;
								com.invSlotObjCount[src] = 0;
							} else if (mode == 1) {
								int src = this.objDragSlot;
								int dst = this.hoveredSlot;
								while (src != dst) {
									if (src > dst) {
										com.swapObj(src - 1, src);
										src--;
									} else if (src < dst) {
										com.swapObj(src + 1, src);
										src++;
									}
								}
							} else {
								com.swapObj(this.hoveredSlot, this.objDragSlot);
							}

							// INV_BUTTOND
							this.out.p1isaac(123);
							this.out.p2_alt3(this.hoveredSlot);
							this.out.p1_alt1(mode);
							this.out.p2_alt2(this.objDragInterfaceId);
							this.out.p2_alt1(this.objDragSlot);
						}
					} else if ((this.oneMouseButton == 1 || this.isAddFriendOption(this.menuSize - 1)) && this.menuSize > 2) {
						this.showContextMenu();
					} else if (this.menuSize > 0) {
						this.useMenuOption(this.menuSize - 1);
					}

					this.selectedCycle = 10;
					super.mouseClickButton = 0;
				}
			}

			if (World3D.clickTileX != -1) {
				int x = World3D.clickTileX;
				int z = World3D.clickTileZ;
				boolean success = this.tryMove(true, false, z, localPlayer.routeTileZ[0], 0, 0, 0, 0, x, 0, 0, localPlayer.routeTileX[0]);
				World3D.clickTileX = -1;

				if (success) {
					this.crossX = super.mouseClickX;
					this.crossY = super.mouseClickY;
					this.crossMode = 1;
					this.crossCycle = 0;
				}
			}

			if (super.mouseClickButton == 1 && this.modalMessage != null) {
				this.modalMessage = null;
				this.redrawChatback = true;
				super.mouseClickButton = 0;
			}

			this.handleMouseInput();

			if (this.fullscreenInterfaceId0 == -1) {
				this.handleMinimapInput();
				this.handleTabInput();
				this.handleChatModeInput();
			}

			if (super.mouseButton == 1 || super.mouseClickButton == 1) {
				this.dragCycles++;
			}

			if (this.field580 == 0 && this.field340 == 0 && this.field425 == 0) {
				if (this.field189 > 0) {
					this.field189--;
				}
			} else if (this.field189 < 100) {
				this.field189++;

				if (this.field189 == 100) {
					if (this.field580 != 0) {
						this.redrawChatback = true;
					}

					if (this.field340 != 0) {
						this.redrawSidebar = true;
					}
				}
			}

			if (this.sceneState == 2) {
				this.updateOrbitCamera();
			}

			if (this.sceneState == 2 && this.cutscene) {
				this.applyCutscene();
			}

			for (int i = 0; i < 5; i++) {
				this.cameraModifierCycle[i]++;
			}

			this.handleInputKey();

			super.idleCycles++;
			if (super.idleCycles > 4500) {
				this.idleTimeout = 250;
				super.idleCycles -= 500;

				// IDLE_TIMER
				this.out.p1isaac(202);
			}

			// See MACRO_ANTIBOT_CAMERA_JITTER above - skipping this whole block entirely (rather than
			// just leaving the values at 0) means macroCameraCycle/macroMinimapCycle never advance
			// either, so there's no dormant state to worry about if this is ever flipped back on.
			if (MACRO_ANTIBOT_CAMERA_JITTER) {
				this.macroCameraCycle++;
				if (this.macroCameraCycle > 500) {
					this.macroCameraCycle = 0;

					int rand = (int) (Math.random() * 8.0D);
					if ((rand & 0x1) == 1) {
						this.macroCameraX += this.macroCameraXModifier;
					}
					if ((rand & 0x2) == 2) {
						this.macroCameraZ += this.macroCameraZModifier;
					}
					if ((rand & 0x4) == 4) {
						this.macroCameraAngle += this.macroCameraAngleModifier;
					}
				}

				if (this.macroCameraX < -50) {
					this.macroCameraXModifier = 2;
				} else if (this.macroCameraX > 50) {
					this.macroCameraXModifier = -2;
				}

				if (this.macroCameraZ < -55) {
					this.macroCameraZModifier = 2;
				} else if (this.macroCameraZ > 55) {
					this.macroCameraZModifier = -2;
				}

				if (this.macroCameraAngle < -40) {
					this.macroCameraAngleModifier = 1;
				} else if (this.macroCameraAngle > 40) {
					this.macroCameraAngleModifier = -1;
				}

				this.macroMinimapCycle++;
				if (this.macroMinimapCycle > 500) {
					this.macroMinimapCycle = 0;

					int rand = (int) (Math.random() * 8.0D);
					if ((rand & 0x1) == 1) {
						this.macroMinimapAngle += this.macroMinimapAngleModifier;
					}
					if ((rand & 0x2) == 2) {
						this.macroMinimapZoom += this.macroMinimapZoomModifier;
					}
				}

				if (this.macroMinimapAngle < -60) {
					this.macroMinimapAngleModifier = 2;
				} else if (this.macroMinimapAngle > 60) {
					this.macroMinimapAngleModifier = -2;
				}

				if (this.macroMinimapZoom < -20) {
					this.macroMinimapZoomModifier = 1;
				} else if (this.macroMinimapZoom > 10) {
					this.macroMinimapZoomModifier = -1;
				}
			}

			this.noTimeoutCycle++;
			if (this.noTimeoutCycle > 50) {
				// NO_TIMEOUT
				this.out.p1isaac(40);
			}

			try {
				if (this.stream != null && this.out.pos > 0) {
					this.stream.write(this.out.pos, 0, this.out.data);
					this.out.pos = 0;
					this.noTimeoutCycle = 0;
				}
			} catch (IOException ignore) {
				this.tryReconnect();
			} catch (Exception ignore) {
				this.logout();
			}
		}
	}

	@ObfuscatedName("client.m(I)V")
	public void tryReconnect() {
		if (this.idleTimeout > 0) {
			this.logout();
			return;
		}
		this.showPopupMessage("Please wait - attempting to reestablish", "Connection lost");
		this.minimapType = 0;
		this.flagSceneTileX = 0;
		ClientStream var2 = this.stream;
		this.ingame = false;
		this.loginRetryCount = 0;
		this.login(this.username, this.password, true);
		if (!this.ingame) {
			this.logout();
		}
		try {
			var2.method233();
		} catch (Exception var3) {
		}
	}

	@ObfuscatedName("client.l(B)V")
	public void updateSceneState() {
		if (lowMem && this.sceneState == 2 && World.field125 != this.currentLevel) {
			this.showPopupMessage(null, "Loading - please wait.");
			this.sceneState = 1;
			this.sceneLoadStartTime = System.currentTimeMillis();
			this.sceneLoadBegan = System.currentTimeMillis();
			this.sceneStuckShown = null;
		}
		if (this.sceneState == 1) {
			int var2 = this.checkScene();
			this.sceneStuckReason = var2;
			// The loading screen is painted ONCE, where the load starts, and nothing repaints it while
			// the scene is coming in - so a scene that never finishes looks exactly like one that is
			// merely slow, for ever. checkScene can sit at -3 indefinitely (a loc whose models will
			// never resolve) with no timeout and no way out of it, and on 2026-09-29 a character had to
			// be moved by editing its save from outside the server because of it. Once the load has
			// plainly stopped getting anywhere, repaint with the reason underneath so the next one
			// reports itself.
			String note = this.sceneStuckNote();
			if (note != null && !note.equals(this.sceneStuckShown)) {
				this.sceneStuckShown = note;
				this.showPopupMessage(note, "Loading - please wait.");
			}
			if (var2 != 0 && System.currentTimeMillis() - this.sceneLoadStartTime > 360000L) {
				signlink.reporterror(this.username + " glcfb " + this.serverSeed + "," + var2 + "," + lowMem + "," + this.fileStreams[0] + "," + this.onDemand.remaining() + "," + this.currentLevel + "," + this.sceneCenterZoneX + "," + this.sceneCenterZoneZ);
				this.sceneLoadStartTime = System.currentTimeMillis();
			}
		}
		if (this.sceneState == 2 && this.minimapLevel != this.currentLevel) {
			this.minimapLevel = this.currentLevel;
			this.createMinimap(this.currentLevel);
		}
	}

	@ObfuscatedName("client.I(I)I")
	public int checkScene() {
		for (int var2 = 0; var2 < this.sceneMapLandData.length; var2++) {
			if (this.sceneMapLandData[var2] == null && this.sceneMapLandFile[var2] != -1) {
				return -1;
			}
			if (this.sceneMapLocData[var2] == null && this.sceneMapLocFile[var2] != -1) {
				return -2;
			}
		}
		boolean var3 = true;
		for (int var4 = 0; var4 < this.sceneMapLandData.length; var4++) {
			byte[] var5 = this.sceneMapLocData[var4];
			if (var5 != null) {
				int var6 = (this.sceneMapIndex[var4] >> 8) * 64 - this.sceneBaseTileX;
				int var7 = (this.sceneMapIndex[var4] & 0xFF) * 64 - this.sceneBaseTileZ;
				if (this.sceneInstanced) {
					var6 = 10;
					var7 = 10;
				}
				var3 &= World.method29(var6, var7, var5);
			}
		}
		if (!var3) {
			return -3;
		} else if (this.awaitingSync) {
			return -4;
		} else {
			this.sceneState = 2;
			this.sceneStuckShown = null;
			World.field125 = this.currentLevel;
			this.buildScene();
			// MAP_BUILD_COMPLETE
			this.out.p1isaac(6);
			return 0;
		}
	}

	@ObfuscatedName("client.x(I)V")
	public void buildScene() {
		try {
			this.minimapLevel = -1;
			this.spotanims.clear();
			this.projectiles.clear();
			Pix3D.clearTexels();
			this.clearCache();
			this.scene.method274();
			System.gc();
			for (int var2 = 0; var2 < 4; var2++) {
				this.levelCollisionMap[var2].method532();
			}
			for (int var3 = 0; var3 < 4; var3++) {
				for (int var4 = 0; var4 < 104; var4++) {
					for (int var5 = 0; var5 < 104; var5++) {
						this.levelTileFlags[var3][var4][var5] = 0;
					}
				}
			}
			World var6 = new World(this.levelHeightmap, this.levelTileFlags, 104, 104);
			int var7 = this.sceneMapLandData.length;
			// NO_TIMEOUT
			this.out.p1isaac(40);
			if (!this.sceneInstanced) {
				for (int var8 = 0; var8 < var7; var8++) {
					int var9 = (this.sceneMapIndex[var8] >> 8) * 64 - this.sceneBaseTileX;
					int var10 = (this.sceneMapIndex[var8] & 0xFF) * 64 - this.sceneBaseTileZ;
					byte[] var11 = this.sceneMapLandData[var8];
					if (var11 != null) {
						var6.method22(var10, (this.sceneCenterZoneZ - 6) * 8, var9, var11, (this.sceneCenterZoneX - 6) * 8, this.levelCollisionMap);
					}
				}
				for (int var12 = 0; var12 < var7; var12++) {
					int var13 = (this.sceneMapIndex[var12] >> 8) * 64 - this.sceneBaseTileX;
					int var14 = (this.sceneMapIndex[var12] & 0xFF) * 64 - this.sceneBaseTileZ;
					byte[] var15 = this.sceneMapLandData[var12];
					if (var15 == null && this.sceneCenterZoneZ < 800) {
						var6.method28(var13, var14, 64, 64);
					}
				}
				// NO_TIMEOUT
				this.out.p1isaac(40);
				for (int var16 = 0; var16 < var7; var16++) {
					byte[] var17 = this.sceneMapLocData[var16];
					if (var17 != null) {
						int var18 = (this.sceneMapIndex[var16] >> 8) * 64 - this.sceneBaseTileX;
						int var19 = (this.sceneMapIndex[var16] & 0xFF) * 64 - this.sceneBaseTileZ;
						var6.method27(var19, this.levelCollisionMap, var18, this.scene, var17);
					}
				}
			}
			if (this.sceneInstanced) {
				int var20 = 0;
				label252: while (true) {
					if (var20 >= 4) {
						for (int var31 = 0; var31 < 13; var31++) {
							for (int var32 = 0; var32 < 13; var32++) {
								int var33 = this.sceneMapRegion[0][var31][var32];
								if (var33 == -1) {
									var6.method28(var31 * 8, var32 * 8, 8, 8);
								}
							}
						}
						// NO_TIMEOUT
						this.out.p1isaac(40);
						int var34 = 0;
						while (true) {
							if (var34 >= 4) {
								break label252;
							}
							for (int var35 = 0; var35 < 13; var35++) {
								for (int var36 = 0; var36 < 13; var36++) {
									int var37 = this.sceneMapRegion[var34][var35][var36];
									if (var37 != -1) {
										int var38 = var37 >> 24 & 0x3;
										int var39 = var37 >> 1 & 0x3;
										int var40 = var37 >> 14 & 0x3FF;
										int var41 = var37 >> 3 & 0x7FF;
										int var42 = (var40 / 8 << 8) + var41 / 8;
										for (int var43 = 0; var43 < this.sceneMapIndex.length; var43++) {
											if (this.sceneMapIndex[var43] == var42 && this.sceneMapLocData[var43] != null) {
												var6.method20(var34, this.levelCollisionMap, this.scene, this.sceneMapLocData[var43], var36 * 8, var39, (var40 & 0x7) * 8, var35 * 8, (var41 & 0x7) * 8, var38);
												break;
											}
										}
									}
								}
							}
							var34++;
						}
					}
					for (int var21 = 0; var21 < 13; var21++) {
						for (int var22 = 0; var22 < 13; var22++) {
							boolean var23 = false;
							int var24 = this.sceneMapRegion[var20][var21][var22];
							if (var24 != -1) {
								int var25 = var24 >> 24 & 0x3;
								int var26 = var24 >> 1 & 0x3;
								int var27 = var24 >> 14 & 0x3FF;
								int var28 = var24 >> 3 & 0x7FF;
								int var29 = (var27 / 8 << 8) + var28 / 8;
								for (int var30 = 0; var30 < this.sceneMapIndex.length; var30++) {
									if (this.sceneMapIndex[var30] == var29 && this.sceneMapLandData[var30] != null) {
										var6.method16(var26, (var28 & 0x7) * 8, this.sceneMapLandData[var30], var20, var25, var21 * 8, this.levelCollisionMap, var22 * 8, (var27 & 0x7) * 8);
										var23 = true;
										break;
									}
								}
							}
							if (!var23) {
								var6.method14(var20, var22 * 8, var21 * 8);
							}
						}
					}
					var20++;
				}
			}
			// NO_TIMEOUT
			this.out.p1isaac(40);
			var6.method15(this.levelCollisionMap, this.scene);
			if (this.areaViewport != null) {
				this.areaViewport.bind();
				Pix3D.lineOffset = this.areaViewportOffset;
			}
			// NO_TIMEOUT
			this.out.p1isaac(40);
			int var44 = World.field113;
			if (var44 > this.currentLevel) {
				var44 = this.currentLevel;
			}
			if (var44 < this.currentLevel - 1) {
				int var45 = this.currentLevel - 1;
			}
			if (lowMem) {
				this.scene.method275(World.field113);
			} else {
				this.scene.method275(0);
			}
			for (int var46 = 0; var46 < 104; var46++) {
				for (int var47 = 0; var47 < 104; var47++) {
					this.sortObjStacks(var46, var47);
				}
			}
			this.clearLocChanges();
		} catch (Exception var61) {
		}
		LocType.field1633.clear();
		if (super.frame != null) {
			// todo: notifying the client is in a frame
			this.out.p1isaac(78);
			this.out.p4(1057001181);
		}
		if (lowMem && signlink.cache_dat != null) {
			int var49 = this.onDemand.getFileCount(0);
			for (int var50 = 0; var50 < var49; var50++) {
				int var51 = this.onDemand.getModelFlags(var50);
				if ((var51 & 0x79) == 0) {
					Model.method358(var50);
				}
			}
		}
		System.gc();
		Pix3D.initPool(20);
		this.onDemand.clearPrefetches();
		int var52 = (this.sceneCenterZoneX - 6) / 8 - 1;
		int var53 = (this.sceneCenterZoneX + 6) / 8 + 1;
		int var54 = (this.sceneCenterZoneZ - 6) / 8 - 1;
		int var55 = (this.sceneCenterZoneZ + 6) / 8 + 1;
		if (this.withinTutorialIsland) {
			var52 = 49;
			var53 = 50;
			var54 = 49;
			var55 = 50;
		}
		for (int var57 = var52; var57 <= var53; var57++) {
			for (int var58 = var54; var58 <= var55; var58++) {
				if (var52 == var57 || var53 == var57 || var54 == var58 || var55 == var58) {
					int var59 = this.onDemand.getMapFile(var57, var58, 0);
					if (var59 != -1) {
						this.onDemand.prefetch(var59, 3);
					}
					int var60 = this.onDemand.getMapFile(var57, var58, 1);
					if (var60 != -1) {
						this.onDemand.prefetch(var60, 3);
					}
				}
			}
		}
	}

	@ObfuscatedName("client.d(B)V")
	public void clearLocChanges() {
		for (LocChange var2 = (LocChange) this.locChanges.head(); var2 != null; var2 = (LocChange) this.locChanges.next()) {
			if (var2.field1322 == -1) {
				var2.field1327 = 0;
				this.storeLoc(var2);
			} else {
				var2.unlink();
			}
		}
	}

	@ObfuscatedName("client.g(II)V")
	public void createMinimap(int arg0) {
		int[] var3 = this.imageMinimap.pixels;
		int var4 = var3.length;
		for (int var5 = 0; var5 < var4; var5++) {
			var3[var5] = 0;
		}
		for (int var6 = 1; var6 < 103; var6++) {
			int var23 = (103 - var6) * 512 * 4 + 24628;
			for (int var24 = 1; var24 < 103; var24++) {
				if ((this.levelTileFlags[arg0][var24][var6] & 0x18) == 0) {
					this.scene.method309(var3, var23, 512, arg0, var24, var6);
				}
				if (arg0 < 3 && (this.levelTileFlags[arg0 + 1][var24][var6] & 0x8) != 0) {
					this.scene.method309(var3, var23, 512, arg0 + 1, var24, var6);
				}
				var23 += 4;
			}
		}
		int var7 = ((int) (Math.random() * 20.0D) + 238 - 10 << 16) + ((int) (Math.random() * 20.0D) + 238 - 10 << 8) + ((int) (Math.random() * 20.0D) + 238 - 10);
		int var8 = (int) (Math.random() * 20.0D) + 238 - 10 << 16;
		this.imageMinimap.bind();
		for (int var9 = 1; var9 < 103; var9++) {
			for (int var22 = 1; var22 < 103; var22++) {
				if ((this.levelTileFlags[arg0][var22][var9] & 0x18) == 0) {
					this.drawMinimapLoc(var9, arg0, var22, var8, var7);
				}
				if (arg0 < 3 && (this.levelTileFlags[arg0 + 1][var22][var9] & 0x8) != 0) {
					this.drawMinimapLoc(var9, arg0 + 1, var22, var8, var7);
				}
			}
		}
		if (this.areaViewport != null) {
			this.areaViewport.bind();
			Pix3D.lineOffset = this.areaViewportOffset;
		}
		field378++;
		if (field378 > 177) {
			field378 = 0;
			// ANTICHEAT_CYCLELOGIC4
			this.out.p1isaac(173);
			this.out.p3(2657152);
		}
		this.activeMapFunctionCount = 0;
		for (int var10 = 0; var10 < 104; var10++) {
			for (int var11 = 0; var11 < 104; var11++) {
				int var12 = this.scene.method303(this.currentLevel, var10, var11);
				if (var12 != 0) {
					int var13 = var12 >> 14 & 0x7FFF;
					int var14 = LocType.method561(var13).field1660;
					if (var14 >= 0) {
						int var15 = var10;
						int var16 = var11;
						// The client scatters most map icons up to three tiles off their loc, a random walk
						// redone on every scene rebuild; these few stay put. 70 is Death Plateau's trading post
						// (the Grand Exchange icon): it marks one building, so it sits on it rather than
						// wandering down the street.
						if (var14 != 22 && var14 != 29 && var14 != 34 && var14 != 36 && var14 != 46 && var14 != 47 && var14 != 48 && var14 != 70) {
							byte var17 = 104;
							byte var18 = 104;
							int[][] var19 = this.levelCollisionMap[this.currentLevel].field1585;
							for (int var20 = 0; var20 < 10; var20++) {
								int var21 = (int) (Math.random() * 4.0D);
								if (var21 == 0 && var15 > 0 && var15 > var10 - 3 && (var19[var15 - 1][var16] & 0x1280108) == 0) {
									var15--;
								}
								if (var21 == 1 && var15 < var17 - 1 && var15 < var10 + 3 && (var19[var15 + 1][var16] & 0x1280180) == 0) {
									var15++;
								}
								if (var21 == 2 && var16 > 0 && var16 > var11 - 3 && (var19[var15][var16 - 1] & 0x1280102) == 0) {
									var16--;
								}
								if (var21 == 3 && var16 < var18 - 1 && var16 < var11 + 3 && (var19[var15][var16 + 1] & 0x1280120) == 0) {
									var16++;
								}
							}
						}
						this.activeMapFunctions[this.activeMapFunctionCount] = this.imageMapfunction[var14];
						this.activeMapFunctionX[this.activeMapFunctionCount] = var15;
						this.activeMapFunctionZ[this.activeMapFunctionCount] = var16;
						this.activeMapFunctionCount++;
					}
				}
			}
		}
	}

	@ObfuscatedName("client.i(I)V")
	public void updateLocChanges() {
		if (this.sceneState != 2) {
			return;
		}
		for (LocChange var2 = (LocChange) this.locChanges.head(); var2 != null; var2 = (LocChange) this.locChanges.next()) {
			if (var2.field1322 > 0) {
				var2.field1322--;
			}
			if (var2.field1322 != 0) {
				if (var2.field1327 > 0) {
					var2.field1327--;
				}
				if (var2.field1327 == 0 && var2.field1325 >= 1 && var2.field1326 >= 1 && var2.field1325 <= 102 && var2.field1326 <= 102 && (var2.field1316 < 0 || World.method18(var2.field1318, var2.field1316))) {
					this.addLoc(var2.field1317, var2.field1325, var2.field1316, var2.field1326, var2.field1323, var2.field1318, var2.field1324);
					var2.field1327 = -1;
					if (var2.field1319 == var2.field1316 && var2.field1319 == -1) {
						var2.unlink();
					} else if (var2.field1319 == var2.field1316 && var2.field1320 == var2.field1317 && var2.field1321 == var2.field1318) {
						var2.unlink();
					}
				}
			} else if (var2.field1319 < 0 || World.method18(var2.field1321, var2.field1319)) {
				this.addLoc(var2.field1320, var2.field1325, var2.field1319, var2.field1326, var2.field1323, var2.field1321, var2.field1324);
				var2.unlink();
			}
		}
	}

	@ObfuscatedName("client.M(I)V")
	public void updateAudio() {
		this.areaSounds.update(this.scene, this.currentLevel, localPlayer == null ? 0 : localPlayer.field1157, localPlayer == null ? 0 : localPlayer.field1158, lowMem ? 0 : this.areaSoundVolume, this.ingame && this.sceneState == 2 && localPlayer != null);
		for (int var2 = 0; var2 < this.waveCount; var2++) {
			if (this.waveDelay[var2] <= 0) {
				boolean var3 = false;
				try {
					if (this.waveIds[var2] != this.lastWaveId || this.waveLoops[var2] != this.lastWaveLoops) {
						Packet var4 = Wave.method479(this.waveLoops[var2], (byte) 6, this.waveIds[var2]);
						if (System.currentTimeMillis() + (long) (var4.pos / 22) > (long) (this.lastWaveLength / 22) + this.lastWaveStartTime) {
							this.lastWaveLength = var4.pos;
							this.lastWaveStartTime = System.currentTimeMillis();
							if (this.saveWave(var4.pos, var4.data)) {
								this.lastWaveId = this.waveIds[var2];
								this.lastWaveLoops = this.waveLoops[var2];
							} else {
								var3 = true;
							}
						}
					} else if (!this.replayWave()) {
						var3 = true;
					}
				} catch (Exception var7) {
					if (signlink.reporterror) {
						// todo: reporting synth error
						this.out.p1isaac(80);
						this.out.p2(this.waveIds[var2] & 0x7FFF);
					} else {
						// todo: reporting synth error
						this.out.p1isaac(80);
						this.out.p2(-1);
					}
				}
				if (var3 && this.waveDelay[var2] != -5) {
					this.waveDelay[var2] = -5;
				} else {
					this.waveCount--;
					for (int var6 = var2; var6 < this.waveCount; var6++) {
						this.waveIds[var6] = this.waveIds[var6 + 1];
						this.waveLoops[var6] = this.waveLoops[var6 + 1];
						this.waveDelay[var6] = this.waveDelay[var6 + 1];
					}
					var2--;
				}
			} else {
				int var10002 = this.waveDelay[var2]--;
			}
		}
		if (this.nextMusicDelay > 0) {
			this.nextMusicDelay -= 20;
			if (this.nextMusicDelay < 0) {
				this.nextMusicDelay = 0;
			}
			if (this.nextMusicDelay == 0 && this.midiActive && !lowMem) {
				this.midiSong = this.nextMidiSong;
				this.midiFading = true;
				this.onDemand.request(2, this.midiSong);
			}
		}
	}

	// Bank search: open the box if it is closed, close it (and drop the filter) if it is open.
	public void toggleBankSearch() {
		if (this.chatbackInputOpen == 4) {
			this.chatbackInputOpen = 0;
			this.bankSearchText = "";
			this.applyBankSearch();
		} else {
			this.showSocialInput = false;
			this.chatbackInputOpen = 4;
			this.chatbackInput = "";
			this.bankSearchText = "";
			this.applyBankSearch();
		}
		this.redrawChatback = true;
	}

	// Bank search: push the current search text into the bank grid's cell map and re-fit the
	// scroll layer to whatever is now showing. Same re-fit the IF_SETINVWINDOW / IF_SETINVBREAKS
	// handlers do, because a search result is usually much shorter than the tab it replaced.
	public void applyBankSearch() {
		if (this.bankGridCom == -1) {
			return;
		}
		Component grid = Component.get(this.bankGridCom);
		if (grid == null) {
			return;
		}
		grid.invSearch = this.bankSearchText.length() == 0 ? null : this.bankSearchText.toLowerCase();
		grid.rebuildCellMap();
		Component parent = Component.get(grid.layer);
		if (parent != null && parent.type == 0) {
			int rows;
			if (grid.invCellSlot != null) {
				int last = -1;
				for (int i = 0; i < grid.invCellSlot.length; i++) {
					if (grid.invCellSlot[i] >= 0) {
						last = i;
					}
				}
				rows = last / grid.width + 1;
			} else {
				rows = grid.height;
			}
			parent.scroll = grid.field741 + rows * (grid.marginY + 32);
			if (parent.scroll < parent.height) {
				parent.scroll = parent.height;
			}
			if (grid.invSearch != null) {
				parent.field713 = 0;
			}
			if (parent.field713 > parent.scroll - parent.height) {
				parent.field713 = parent.scroll - parent.height;
			}
		}
		this.redrawSidebar = true;
	}

	@ObfuscatedName("client.w(I)V")
	public void handleInput() {
		if (this.objDragArea != 0) {
			return;
		}
		this.bankTabHovered = -1;
		// Bank search: whatever closed the box - the bank closing, an amount prompt replacing it,
		// a packet resetting the chatback - the filter goes with it.
		if (this.bankSearchText.length() > 0 && (this.chatbackInputOpen != 4 || this.viewportInterfaceId == -1)) {
			this.bankSearchText = "";
			this.applyBankSearch();
		}
		if (this.chatbackInputOpen == 4 && this.viewportInterfaceId == -1) {
			this.chatbackInputOpen = 0;
			this.redrawChatback = true;
		}
		this.menuOption[0] = "Cancel";
		this.menuAction[0] = 1016;
		this.menuSize = 1;
		// The settings panel swallows input while it is open - after the menu reset above, so the
		// game is left with a clean "Cancel"-only menu rather than a stale one from last frame.
		if (this.qolPanelOpen) {
			this.handleQolPanelInput();
			return;
		}
		if (this.pluginPanelOpen) {
			this.handlePluginPanelInput();
			return;
		}
		if (this.fullscreenInterfaceId0 != -1) {
			this.lastHoveredInterfaceId = 0;
			this.field611 = 0;
			this.handleInterfaceInput(0, Component.get(this.fullscreenInterfaceId0), 0, 0, 0, super.mouseX, super.mouseY);
			if (this.viewportHoveredInterfaceIndex != this.lastHoveredInterfaceId) {
				this.viewportHoveredInterfaceIndex = this.lastHoveredInterfaceId;
			}
			if (this.field611 != this.field425) {
				this.field425 = this.field611;
			}
			return;
		}
		this.handlePrivateChatInput();
		this.lastHoveredInterfaceId = 0;
		this.field611 = 0;
		if (this.layout.inViewport(super.mouseX, super.mouseY)) {
			if (this.viewportInterfaceId == -1) {
				this.handleViewportOptions();
			} else {
				this.handleInterfaceInput(this.layout.vpY + this.layout.mainY, Component.get(this.viewportInterfaceId), 0, 0, this.layout.vpX + this.layout.mainX, super.mouseX, super.mouseY);
			}
		}
		if (this.viewportHoveredInterfaceIndex != this.lastHoveredInterfaceId) {
			this.viewportHoveredInterfaceIndex = this.lastHoveredInterfaceId;
		}
		if (this.field611 != this.field425) {
			this.field425 = this.field611;
		}
		this.lastHoveredInterfaceId = 0;
		this.field611 = 0;
		if (super.mouseX > SIDE_X && super.mouseY > 205 && super.mouseX < SIDE_X + 190 && super.mouseY < 466) {
			if (this.sidebarInterfaceId != -1) {
				this.handleInterfaceInput(205, Component.get(this.sidebarInterfaceId), 1, 0, SIDE_X, super.mouseX, super.mouseY);
			} else if (this.tabInterfaceId[this.selectedTab] != -1) {
				this.handleInterfaceInput(205, Component.get(this.tabInterfaceId[this.selectedTab]), 1, 0, SIDE_X, super.mouseX, super.mouseY);
			}
		}
		if (this.sidebarHoveredInterfaceIndex != this.lastHoveredInterfaceId) {
			this.redrawSidebar = true;
			this.sidebarHoveredInterfaceIndex = this.lastHoveredInterfaceId;
		}
		if (this.field611 != this.field340) {
			this.redrawSidebar = true;
			this.field340 = this.field611;
		}
		this.lastHoveredInterfaceId = 0;
		this.field611 = 0;
		if (super.mouseX > CHAT_X && super.mouseY > CHAT_Y && super.mouseX < CHAT_X + CHAT_W && super.mouseY < CHAT_Y + CHAT_H) {
			if (this.chatInterfaceId != -1) {
				this.handleInterfaceInput(CHAT_Y + CHAT_IF_Y, Component.get(this.chatInterfaceId), 2, 0, CHAT_X + CHAT_IF_X, super.mouseX, super.mouseY);
			} else if (this.stickyChatInterfaceId != -1) {
				this.handleInterfaceInput(CHAT_Y + CHAT_IF_Y, Component.get(this.stickyChatInterfaceId), 3, 0, CHAT_X + CHAT_IF_X, super.mouseX, super.mouseY);
			} else if (super.mouseY < CHAT_Y + CHAT_LOG_H && super.mouseX < CHAT_X + CHAT_W - 70 && this.chatbackInputOpen == 0) {
				this.handleChatMouseInput(super.mouseX - CHAT_X, super.mouseY - CHAT_Y);
			}
		}
		if ((this.chatInterfaceId != -1 || this.stickyChatInterfaceId != -1) && this.chatHoveredInterfaceIndex != this.lastHoveredInterfaceId) {
			this.redrawChatback = true;
			this.chatHoveredInterfaceIndex = this.lastHoveredInterfaceId;
		}
		if ((this.chatInterfaceId != -1 || this.stickyChatInterfaceId != -1) && this.field611 != this.field580) {
			this.redrawChatback = true;
			this.field580 = this.field611;
		}
		boolean var2 = false;
		while (!var2) {
			var2 = true;
			for (int var3 = 0; var3 < this.menuSize - 1; var3++) {
				if (this.menuAction[var3] < 1000 && this.menuAction[var3 + 1] > 1000) {
					String var4 = this.menuOption[var3];
					this.menuOption[var3] = this.menuOption[var3 + 1];
					this.menuOption[var3 + 1] = var4;
					int var5 = this.menuAction[var3];
					this.menuAction[var3] = this.menuAction[var3 + 1];
					this.menuAction[var3 + 1] = var5;
					int var6 = this.menuParamB[var3];
					this.menuParamB[var3] = this.menuParamB[var3 + 1];
					this.menuParamB[var3 + 1] = var6;
					int var7 = this.menuParamC[var3];
					this.menuParamC[var3] = this.menuParamC[var3 + 1];
					this.menuParamC[var3 + 1] = var7;
					int var8 = this.menuParamA[var3];
					this.menuParamA[var3] = this.menuParamA[var3 + 1];
					this.menuParamA[var3 + 1] = var8;
					var2 = false;
				}
			}
		}
		// Plugins get the menu last of all, so one can override a swap the player configured.
		// Whoever runs last wins, and a plugin the player installed deliberately is the more
		// specific instruction of the two.
		if (this.plugins != null) {
			this.plugins.onMenuBuilt(this.menuSize);
		}
	}

	@ObfuscatedName("client.D(I)V")
	public void handlePrivateChatInput() {
		if (this.splitPrivateChat == 0) {
			return;
		}
		int var3 = 0;
		if (this.systemUpdateTimer != 0) {
			var3 = 1;
		}
		for (int var4 = 0; var4 < 100; var4++) {
			if (this.messageText[var4] != null) {
				int var5 = this.messageType[var4];
				String var6 = this.messageSender[var4];
				boolean var7 = false;
				// any rank icon in front of the name (ChatIcons), not only the two crowns
				if (var6 != null) {
					var6 = var6.substring(ChatIcons.leading(var6).length());
				}
				if ((var5 == 3 || var5 == 7) && (var5 == 7 || this.chatPrivateMode == 0 || this.chatPrivateMode == 1 && this.isFriend(var6))) {
					int var10 = this.layout.openH - 5 - var3 * 13;
					if (super.mouseX > this.layout.vpX && super.mouseY - this.layout.vpY > var10 - 10 && super.mouseY - this.layout.vpY <= var10 + 3) {
						int var11 = this.fontPlain12.stringWid("From:  " + var6 + this.messageText[var4]) + 25;
						if (var11 > 450) {
							var11 = 450;
						}
						if (super.mouseX < var11 + this.layout.vpX) {
							if (this.staffmodlevel >= 1) {
								this.menuOption[this.menuSize] = "Report abuse @whi@" + var6;
								this.menuAction[this.menuSize] = 2507;
								this.menuSize++;
							}
							this.addSocialMenuOptions(var6, 2000);
						}
					}
					var3++;
					if (var3 >= 5) {
						return;
					}
				}
				if ((var5 == 5 || var5 == 6) && this.chatPrivateMode < 2) {
					var3++;
					if (var3 >= 5) {
						return;
					}
				}
			}
		}
	}

	@ObfuscatedName("client.d(III)V")
	public void handleChatMouseInput(int arg1, int arg2) {
		int var4 = 0;
		for (int var6 = 0; var6 < 100; var6++) {
			if (this.messageText[var6] != null) {
				int var7 = this.messageType[var6];
				if (this.chatFilter == 1 && var7 != 0) {
					continue;
				}
				int var8 = CHAT_LOG_H - 7 - var4 * 14 + this.chatScrollOffset + 4;
				if (var8 < -20) {
					break;
				}
				String var9 = this.messageSender[var6];
				boolean var10 = false;
				// any rank icon in front of the name (ChatIcons), not only the two crowns
				if (var9 != null) {
					var9 = var9.substring(ChatIcons.leading(var9).length());
				}
				if (var7 == 0) {
					var4++;
				}
				if ((var7 == 1 || var7 == 2) && (var7 == 1 || this.chatPublicMode == 0 || this.chatPublicMode == 1 && this.isFriend(var9))) {
					if (arg2 > var8 - 14 && arg2 <= var8 && !var9.equals(localPlayer.name)) {
						if (this.staffmodlevel >= 1) {
							this.menuOption[this.menuSize] = "Report abuse @whi@" + var9;
							this.menuAction[this.menuSize] = 507;
							this.menuSize++;
						}
						this.addSocialMenuOptions(var9, 0);
					}
					var4++;
				}
				if ((var7 == 3 || var7 == 7) && this.splitPrivateChat == 0 && (var7 == 7 || this.chatPrivateMode == 0 || this.chatPrivateMode == 1 && this.isFriend(var9))) {
					if (arg2 > var8 - 14 && arg2 <= var8) {
						if (this.staffmodlevel >= 1) {
							this.menuOption[this.menuSize] = "Report abuse @whi@" + var9;
							this.menuAction[this.menuSize] = 507;
							this.menuSize++;
						}
						this.addSocialMenuOptions(var9, 0);
					}
					var4++;
				}
				if (var7 == 4 && (this.chatTradeMode == 0 || this.chatTradeMode == 1 && this.isFriend(var9))) {
					if (arg2 > var8 - 14 && arg2 <= var8) {
						this.menuOption[this.menuSize] = "Accept trade @whi@" + var9;
						this.menuAction[this.menuSize] = 544;
						this.menuSize++;
					}
					var4++;
				}
				if ((var7 == 5 || var7 == 6) && this.splitPrivateChat == 0 && this.chatPrivateMode < 2) {
					var4++;
				}
				if (var7 == 11 && this.showClanLine(var9)) {
					if (arg2 > var8 - 14 && arg2 <= var8 && !var9.equals(localPlayer.name)) {
						this.addSocialMenuOptions(var9, 0);
					}
					var4++;
				}
				if (var7 == 8 && (this.chatTradeMode == 0 || this.chatTradeMode == 1 && this.isFriend(var9))) {
					if (arg2 > var8 - 14 && arg2 <= var8) {
						this.menuOption[this.menuSize] = "Accept challenge @whi@" + var9;
						this.menuAction[this.menuSize] = 695;
						this.menuSize++;
					}
					var4++;
				}
			}
		}
	}

	@ObfuscatedName("client.h(B)V")
	public void handleViewportOptions() {
		if (this.objSelected == 0 && this.spellSelected == 0) {
			this.menuOption[this.menuSize] = "Walk here";
			this.menuAction[this.menuSize] = 14;
			this.menuParamB[this.menuSize] = super.mouseX;
			this.menuParamC[this.menuSize] = super.mouseY;
			this.menuSize++;
		}
		int var2 = -1;
		for (int var3 = 0; var3 < Model.pickedCount; var3++) {
			int var4 = Model.field1256[var3];
			int var5 = var4 & 0x7F;
			int var6 = var4 >> 7 & 0x7F;
			int var7 = var4 >> 29 & 0x3;
			int var8 = var4 >> 14 & 0x7FFF;
			if (var2 != var4) {
				var2 = var4;
				if (var7 == 2 && this.scene.method304(this.currentLevel, var5, var6, var4) >= 0) {
					LocType var9 = LocType.method561(var8);
					if (var9.field1659 != null) {
						var9 = var9.method562();
					}
					if (var9 == null) {
						continue;
					}
					if (this.objSelected == 1) {
						this.menuOption[this.menuSize] = "Use " + this.objSelectedName + " with @cya@" + var9.field1630;
						this.menuAction[this.menuSize] = 467;
						this.menuParamA[this.menuSize] = var4;
						this.menuParamB[this.menuSize] = var5;
						this.menuParamC[this.menuSize] = var6;
						this.menuSize++;
					} else if (this.spellSelected != 1) {
						if (var9.field1644 != null) {
							for (int var10 = 4; var10 >= 0; var10--) {
								if (var9.field1644[var10] != null) {
									this.menuOption[this.menuSize] = var9.field1644[var10] + " @cya@" + var9.field1630;
									if (var10 == 0) {
										this.menuAction[this.menuSize] = 35;
									}
									if (var10 == 1) {
										this.menuAction[this.menuSize] = 389;
									}
									if (var10 == 2) {
										this.menuAction[this.menuSize] = 888;
									}
									if (var10 == 3) {
										this.menuAction[this.menuSize] = 892;
									}
									if (var10 == 4) {
										this.menuAction[this.menuSize] = 1280;
									}
									this.menuParamA[this.menuSize] = var4;
									this.menuParamB[this.menuSize] = var5;
									this.menuParamC[this.menuSize] = var6;
									this.menuSize++;
								}
							}
						}
						this.menuOption[this.menuSize] = "Examine @cya@" + var9.field1630;
						this.menuAction[this.menuSize] = 1412;
						this.menuParamA[this.menuSize] = var9.field1627 << 14;
						this.menuParamB[this.menuSize] = var5;
						this.menuParamC[this.menuSize] = var6;
						this.menuSize++;
					} else if ((this.activeSpellFlags & 0x4) == 4) {
						this.menuOption[this.menuSize] = this.spellCaption + " @cya@" + var9.field1630;
						this.menuAction[this.menuSize] = 376;
						this.menuParamA[this.menuSize] = var4;
						this.menuParamB[this.menuSize] = var5;
						this.menuParamC[this.menuSize] = var6;
						this.menuSize++;
					}
				}
				if (var7 == 1) {
					ClientNpc var11 = this.npcs[var8];
					if (var11.field1370.field1445 == 1 && (var11.field1157 & 0x7F) == 64 && (var11.field1158 & 0x7F) == 64) {
						for (int var12 = 0; var12 < this.npcCount; var12++) {
							ClientNpc var15 = this.npcs[this.npcIds[var12]];
							if (var15 != null && var11 != var15 && var15.field1370.field1445 == 1 && var11.field1157 == var15.field1157 && var11.field1158 == var15.field1158) {
								this.addNpcOptions(var15.field1370, var6, var5, this.npcIds[var12]);
							}
						}
						for (int var13 = 0; var13 < this.playerCount; var13++) {
							ClientPlayer var14 = this.players[this.playerIds[var13]];
							if (var14 != null && var11.field1157 == var14.field1157 && var11.field1158 == var14.field1158) {
								this.addPlayerOptions(this.playerIds[var13], var6, var5, var14);
							}
						}
					}
					this.addNpcOptions(var11.field1370, var6, var5, var8);
				}
				if (var7 == 0) {
					ClientPlayer var16 = this.players[var8];
					if ((var16.field1157 & 0x7F) == 64 && (var16.field1158 & 0x7F) == 64) {
						for (int var17 = 0; var17 < this.npcCount; var17++) {
							ClientNpc var20 = this.npcs[this.npcIds[var17]];
							if (var20 != null && var20.field1370.field1445 == 1 && var16.field1157 == var20.field1157 && var16.field1158 == var20.field1158) {
								this.addNpcOptions(var20.field1370, var6, var5, this.npcIds[var17]);
							}
						}
						for (int var18 = 0; var18 < this.playerCount; var18++) {
							ClientPlayer var19 = this.players[this.playerIds[var18]];
							if (var19 != null && var16 != var19 && var16.field1157 == var19.field1157 && var16.field1158 == var19.field1158) {
								this.addPlayerOptions(this.playerIds[var18], var6, var5, var19);
							}
						}
					}
					this.addPlayerOptions(var8, var6, var5, var16);
				}
				if (var7 == 3) {
					LinkList var21 = this.objStacks[this.currentLevel][var5][var6];
					if (var21 != null) {
						for (ClientObj var22 = (ClientObj) var21.tail(); var22 != null; var22 = (ClientObj) var21.prev()) {
							ObjType var23 = ObjType.get(var22.field873);
							if (this.objSelected == 1) {
								this.menuOption[this.menuSize] = "Use " + this.objSelectedName + " with @lre@" + var23.field811;
								this.menuAction[this.menuSize] = 100;
								this.menuParamA[this.menuSize] = var22.field873;
								this.menuParamB[this.menuSize] = var5;
								this.menuParamC[this.menuSize] = var6;
								this.menuSize++;
							} else if (this.spellSelected != 1) {
								for (int var24 = 4; var24 >= 0; var24--) {
									if (var23.field820 != null && var23.field820[var24] != null) {
										this.menuOption[this.menuSize] = var23.field820[var24] + " @lre@" + var23.field811;
										if (var24 == 0) {
											this.menuAction[this.menuSize] = 68;
										}
										if (var24 == 1) {
											this.menuAction[this.menuSize] = 26;
										}
										if (var24 == 2) {
											this.menuAction[this.menuSize] = 684;
										}
										if (var24 == 3) {
											this.menuAction[this.menuSize] = 930;
										}
										if (var24 == 4) {
											this.menuAction[this.menuSize] = 270;
										}
										this.menuParamA[this.menuSize] = var22.field873;
										this.menuParamB[this.menuSize] = var5;
										this.menuParamC[this.menuSize] = var6;
										this.menuSize++;
									} else if (var24 == 2) {
										this.menuOption[this.menuSize] = "Take @lre@" + var23.field811;
										this.menuAction[this.menuSize] = 684;
										this.menuParamA[this.menuSize] = var22.field873;
										this.menuParamB[this.menuSize] = var5;
										this.menuParamC[this.menuSize] = var6;
										this.menuSize++;
									}
								}
								this.menuOption[this.menuSize] = "Examine @lre@" + var23.field811;
								this.menuAction[this.menuSize] = 1564;
								this.menuParamA[this.menuSize] = var22.field873;
								this.menuParamB[this.menuSize] = var5;
								this.menuParamC[this.menuSize] = var6;
								this.menuSize++;
							} else if ((this.activeSpellFlags & 0x1) == 1) {
								this.menuOption[this.menuSize] = this.spellCaption + " @lre@" + var23.field811;
								this.menuAction[this.menuSize] = 199;
								this.menuParamA[this.menuSize] = var22.field873;
								this.menuParamB[this.menuSize] = var5;
								this.menuParamC[this.menuSize] = var6;
								this.menuSize++;
							}
						}
					}
				}
			}
		}
	}

	@ObfuscatedName("client.l(I)V")
	public void handleMouseInput() {
		if (this.objDragArea != 0) {
			return;
		}
		int var2 = super.mouseClickButton;
		if (this.spellSelected == 1 && super.mouseClickX >= 516 && super.mouseClickY >= 160 && super.mouseClickX <= 765 && super.mouseClickY <= 205) {
			var2 = 0;
		}
		if (!this.menuVisible) {
			// A click on something an overlay drew and claimed. Checked before everything below so
			// it cannot also walk the player; a click that lands on nothing claimed falls through
			// and behaves exactly as it always did.
			if (var2 == 1 && this.plugins != null
				&& this.plugins.onViewportClick(super.mouseClickX - this.layout.vpX,
					super.mouseClickY - this.layout.vpY)) {
				return;
			}
			// QoL: shift-click an inventory item to drop it instantly, bypassing whatever its
			// normal default left-click action (and the drag-to-reorder handling below) would be.
			if (QolSettings.on(QolSettings.SHIFT_DROP) && var2 == 1 && super.actionKey[GameShell.KEY_SHIFT] == 1 && this.menuSize > 0) {
				int dropIndex = -1;
				for (int i = 0; i < this.menuSize; i++) {
					if (this.menuAction[i] == 891) {
						dropIndex = i;
						break;
					}
				}
				if (dropIndex != -1) {
					this.useMenuOption(dropIndex);
					return;
				}
			}
			// Bank tabs: a press on a numbered tab may be the start of a drag onto another tab. Record it
			// and fall through - the click still selects the tab, and the release below decides whether the
			// gesture was actually a drag.
			if (var2 == 1 && this.bankTabHovered >= 1) {
				this.bankTabDragFrom = this.bankTabHovered;
				this.bankTabGrabX = super.mouseClickX;
				this.bankTabGrabY = super.mouseClickY;
			}
			if (var2 == 1 && this.menuSize > 0) {
				int var13 = this.menuAction[this.menuSize - 1];
				if (var13 == 9 || var13 == 225 || var13 == 444 || var13 == 564 || var13 == 894 || var13 == 961 || var13 == 399 || var13 == 324 || var13 == 227 || var13 == 891 || var13 == 52 || var13 == 1094) {
					int var14 = this.menuParamB[this.menuSize - 1];
					int var15 = this.menuParamC[this.menuSize - 1];
					Component var16 = Component.get(var15);
					// Bank search: the visible cells are slots from every tab at once, so a drag
					// between two of them would reorder the real list underneath the tab counts.
					// OSRS locks rearranging while a search is up too. Only the drag is locked: the
					// press falls through to the plain left-click below, so a result withdraws on a
					// left click as any bank item does. (A draggable item's click is normally made on
					// release, by the drag code - returning here used to swallow it altogether.)
					boolean searching = var16.clientCode == 206 && this.bankSearchText.length() > 0;
					if (!searching && (var16.draggable || var16.swappable)) {
						this.objGrabThreshold = false;
						this.objDragCycles = 0;
						this.objDragInterfaceId = var15;
						this.objDragSlot = var14;
						this.objDragArea = 2;
						this.objGrabX = super.mouseClickX;
						this.objGrabY = super.mouseClickY;
						if (Component.get(var15).layer == this.viewportInterfaceId) {
							this.objDragArea = 1;
						}
						if (Component.get(var15).layer == this.chatInterfaceId) {
							this.objDragArea = 3;
						}
						return;
					}
				}
			}
			if (var2 == 1 && (this.oneMouseButton == 1 || this.isAddFriendOption(this.menuSize - 1)) && this.menuSize > 2) {
				var2 = 2;
			}
			if (var2 == 1 && this.menuSize > 0) {
				this.useMenuOption(this.menuSize - 1);
			}
			if (var2 != 2 || this.menuSize <= 0) {
				return;
			}
			this.showContextMenu();
			return;
		}
		if (var2 != 1) {
			int var3 = super.mouseX;
			int var4 = super.mouseY;
			if (this.menuArea == 0) {
				var3 -= this.layout.vpX;
				var4 -= this.layout.vpY;
			}
			if (this.menuArea == 1) {
				var3 -= SIDE_X;
				var4 -= 205;
			}
			if (this.menuArea == 2) {
				var3 -= CHAT_X;
				var4 -= CHAT_Y;
			}
			if (var3 < this.menuX - 10 || var3 > this.menuWidth + this.menuX + 10 || var4 < this.menuY - 10 || var4 > this.menuHeight + this.menuY + 10) {
				this.menuVisible = false;
				if (this.menuArea == 1) {
					this.redrawSidebar = true;
				}
				if (this.menuArea == 2) {
					this.redrawChatback = true;
				}
			}
		}
		if (var2 == 1) {
			int var5 = this.menuX;
			int var6 = this.menuY;
			int var7 = this.menuWidth;
			int var8 = super.mouseClickX;
			int var9 = super.mouseClickY;
			if (this.menuArea == 0) {
				var8 -= this.layout.vpX;
				var9 -= this.layout.vpY;
			}
			if (this.menuArea == 1) {
				var8 -= SIDE_X;
				var9 -= 205;
			}
			if (this.menuArea == 2) {
				var8 -= CHAT_X;
				var9 -= CHAT_Y;
			}
			int var10 = -1;
			for (int p = 0; p < this.menuRowsShown; p++) {
				int var12 = this.menuRowY(p);
				if (var8 > var5 && var8 < var5 + var7 && var9 > var12 - 13 && var9 < var12 + 3) {
					var10 = this.menuRowIndex(p);
				}
			}
			// QoL: a swap menu's rows configure, they never act. Cleared here rather than where it
			// was set, so the mode cannot outlive the menu it belongs to.
			if (this.menuSwapMode) {
				this.menuSwapMode = false;
				this.applySwapChoice(var10);
			} else if (var10 != -1) {
				this.useMenuOption(var10);
			}
			this.menuVisible = false;
			if (this.menuArea == 1) {
				this.redrawSidebar = true;
			}
			if (this.menuArea == 2) {
				this.redrawChatback = true;
			}
		}
	}

	// The stone behind a selected tab. col is 0-6 along its row; the end tabs get the rounded corner
	// stones (corner 0 for the top row, 2 for the bottom) and the five between them share one.
	private void plotTabStone(int col, int y, int corner) {
		int shift = corner == 0 ? 0 : 3;
		if (col == 0) {
			this.imageTabstones[corner].plotSprite(y, 6 - shift);
		} else if (col == 6) {
			this.imageTabstones[corner + 1].plotSprite(y, 209 - shift);
		} else {
			this.imageTabstones[4].plotSprite(y, 44 + (col - 1) * 33 - shift);
		}
	}

	@ObfuscatedName("client.m(B)V")
	public void handleMinimapInput() {
		// QoL: click the compass (top-left corner of the minimap) to smoothly turn the camera to
		// face north, like retail RS. Checked before the minimapType/walk-to-point logic below since
		// it should work regardless of minimap display mode, and uses the same hole-in-the-mask data
		// (compassMaskLineOffsets/Lengths, built at startup from imageMapback) that's already used to
		// draw the compass itself, so the click region always matches its actual on-screen shape.
		if (super.mouseClickButton == 1 && QolSettings.on(QolSettings.COMPASS_NORTH)) {
			int compassLocalX = super.mouseClickX - MAP_X;
			int compassLocalY = super.mouseClickY - 4;
			if (compassLocalY >= 0 && compassLocalY < 33 && compassLocalX >= this.compassMaskLineOffsets[compassLocalY] && compassLocalX < this.compassMaskLineOffsets[compassLocalY] + this.compassMaskLineLengths[compassLocalY]) {
				this.compassResetting = true;
				super.mouseClickButton = 0;
				return;
			}
		}

		if (this.minimapType != 0 || super.mouseClickButton != 1) {
			return;
		}
		int var2 = super.mouseClickX - 25 - MAP_X;
		int var3 = super.mouseClickY - 5 - 4;
		if (var2 < 0 || var3 < 0 || var2 >= 146 || var3 >= 151) {
			return;
		}
		var2 -= 73;
		var3 -= 75;
		int var4 = this.orbitCameraYaw + this.macroMinimapAngle & 0x7FF;
		int var5 = Pix3D.sinTable[var4];
		int var6 = Pix3D.cosTable[var4];
		int var7 = (this.macroMinimapZoom + 256) * var5 >> 8;
		int var8 = (this.macroMinimapZoom + 256) * var6 >> 8;
		int var9 = var2 * var8 + var3 * var7 >> 11;
		int var10 = var3 * var8 - var2 * var7 >> 11;
		int var11 = localPlayer.field1157 + var9 >> 7;
		int var12 = localPlayer.field1158 - var10 >> 7;
		boolean var13 = this.tryMove(true, false, var12, localPlayer.routeTileZ[0], 0, 0, 1, 0, var11, 0, 0, localPlayer.routeTileX[0]);
		if (var13) {
			this.out.p1(var2);
			this.out.p1(var3);
			this.out.p2(this.orbitCameraYaw);
			this.out.p1(57);
			this.out.p1(this.macroMinimapAngle);
			this.out.p1(this.macroMinimapZoom);
			this.out.p1(89);
			this.out.p2(localPlayer.field1157);
			this.out.p2(localPlayer.field1158);
			this.out.p1(this.tryMoveNearest);
			this.out.p1(63);
		}
	}

	@ObfuscatedName("client.c(Z)V")
	public void handleTabInput() {
		if (super.mouseClickButton == 1) {
			// Tab edges from interface 548: the end tabs are 38 wide, the five between them 33.
			for (int col = 0; col < 7; col++) {
				int left = col == 0 ? 522 : 560 + (col - 1) * 33;
				int right = col == 6 ? 763 : 560 + col * 33;
				if (super.mouseClickX < left || super.mouseClickX >= right) {
					continue;
				}
				int tab = -1;
				if (super.mouseClickY >= 168 && super.mouseClickY < 204) {
					tab = col;
				} else if (super.mouseClickY >= 466 && super.mouseClickY < 502) {
					tab = col + 7;
				}
				if (tab != -1 && this.tabInterfaceId[tab] != -1) {
					this.redrawSidebar = true;
					this.selectedTab = tab;
					this.redrawSideicons = true;
				}
			}
		}
	}

	@ObfuscatedName("client.f(Z)V")
	public void handleChatModeInput() {
		if (super.mouseClickButton != 1) {
			return;
		}
		int button = this.chatBarButtonAt(super.mouseClickX, super.mouseClickY);
		if (button == 0 || button == 1) {
			// All / Game: what the chatbox lists. Nothing to tell the server - it is a view.
			if (this.chatFilter != button) {
				this.chatFilter = button;
				this.chatScrollOffset = 0;
			}
			this.redrawPrivacySettings = true;
			this.redrawChatback = true;
		} else if (button == 2 || button == 3 || button == 5) {
			if (button == 2) {
				this.chatPublicMode = (this.chatPublicMode + 1) % 4;
			} else if (button == 3) {
				this.chatPrivateMode = (this.chatPrivateMode + 1) % 3;
			} else {
				this.chatTradeMode = (this.chatTradeMode + 1) % 3;
			}
			this.redrawPrivacySettings = true;
			this.redrawChatback = true;
			// CHAT_SETMODE
			this.out.p1isaac(176);
			this.out.p1(this.chatPublicMode);
			this.out.p1(this.chatPrivateMode);
			this.out.p1(this.chatTradeMode);
		} else if (button == 4) {
			// Clan: which clan lines show. The server sends every line; this is a view.
			this.chatClanMode = (this.chatClanMode + 1) % 3;
			this.redrawPrivacySettings = true;
			this.redrawChatback = true;
		} else if (button == 6) {
			if (this.viewportInterfaceId == -1) {
				this.closeInterfaces();
				this.reportAbuseInput = "";
				this.reportAbuseMuteOption = false;
				this.reportAbuseInterfaceId = this.viewportInterfaceId = Component.field728;
			} else {
				this.addMessage("", "Please close the interface you have open before using 'report abuse'", 0);
			}
		}
		field456++;
		if (field456 > 161) {
			field456 = 0;
			// ANTICHEAT_CYCLELOGIC6
			this.out.p1isaac(22);
			this.out.p2(38304);
		}
	}

	// Which chat bar button (x,y) is on: 0-5 All/Game/Public/Private/Clan/Trade, 6 Report, or -1.
	private int chatBarButtonAt(int x, int y) {
		if (y < BAR_Y + 1 || y >= BAR_Y + 23) {
			return -1;
		}
		if (x >= BAR_REPORT_X && x < BAR_REPORT_X + BAR_REPORT_W) {
			return 6;
		}
		int col = (x - 5) / BAR_PITCH;
		if (x < 5 || col > 5 || x - 5 - col * BAR_PITCH >= BAR_BUTTON_W) {
			return -1;
		}
		return col;
	}

	private void drawChatBarLabel(int x, int w, String label, String mode, int modeColour) {
		int cx = x + w / 2;
		if (mode == null) {
			this.fontPlain11.centreStringTag(true, 0xFFFFFF, 16, cx, label);
		} else {
			this.fontPlain11.centreStringTag(true, 0xFFFFFF, 11, cx, label);
			this.fontPlain11.centreStringTag(true, modeColour, 21, cx, mode);
		}
	}

	private static String chatModeName(int mode) {
		return mode == 0 ? "On" : mode == 1 ? "Friends" : mode == 2 ? "Off" : "Hide";
	}

	private static int chatModeColour(int mode) {
		return mode == 0 ? 0x00FF00 : mode == 1 ? 0xFFFF00 : mode == 2 ? 0xFF0000 : 0x00FFFF;
	}

	@ObfuscatedName("client.b(Z)V")
	public void closeInterfaces() {
		// CLOSE_MODAL
		this.out.p1isaac(110);
		if (this.sidebarInterfaceId != -1) {
			this.unloadCom(this.sidebarInterfaceId);
			this.sidebarInterfaceId = -1;
			this.redrawSidebar = true;
			this.pressedContinueOption = false;
			this.redrawSideicons = true;
		}
		if (this.chatInterfaceId != -1) {
			this.unloadCom(this.chatInterfaceId);
			this.chatInterfaceId = -1;
			this.redrawChatback = true;
			this.pressedContinueOption = false;
		}
		if (this.fullscreenInterfaceId0 != -1) {
			this.unloadCom(this.fullscreenInterfaceId0);
			this.fullscreenInterfaceId0 = -1;
			this.redrawFrame = true;
		}
		if (this.fullscreenInterfaceId1 != -1) {
			this.unloadCom(this.fullscreenInterfaceId1);
			this.fullscreenInterfaceId1 = -1;
		}
		if (this.viewportInterfaceId != -1) {
			this.unloadCom(this.viewportInterfaceId);
			this.viewportInterfaceId = -1;
		}
	}

	// QoL: find the first "click here to continue" component (buttonType 6) anywhere under the
	// given interface, so space bar can trigger it without needing the mouse to be hovering over
	// it (mirrors the geometry-free part of what handleInterfaceInput() does for buttonType 6).
	private int findContinueComponentId(Component com) {
		if (com.children == null) {
			return -1;
		}
		for (int i = 0; i < com.children.length; i++) {
			Component child = Component.get(com.children[i]);
			if (child.type == 0) {
				int found = this.findContinueComponentId(child);
				if (found != -1) {
					return found;
				}
			} else if (child.buttonType == 6) {
				return child.id;
			}
		}
		return -1;
	}

	// QoL (Corey, 2026-09-02): number-key dialogue option selection. The classic "Select an
	// Option" chat popup (used throughout scripts for player dialogue choices, header text
	// "Select an Option") is built from four fixed interfaces - multi2/multi3/multi4/multi5,
	// one per possible option count - each opened via if_openchat with its N choice buttons
	// registered as if_addresumebutton(multiN:com_1) .. com_N. Unlike findContinueComponentId()
	// above, this can't be done structurally: if_openchat is also used by several other popups
	// (the bank withdraw-amount picker, crafting item choice, etc.) that also have buttonType 1
	// text/graphic buttons but where "button order" doesn't mean "option 1, 2, 3...". So this is
	// scoped to just these four known interface ids instead, matching the numeric-id convention
	// this whole codebase already uses for varps/components. Read directly from interface.pack's
	// bare "NNNN=multiN" lines (the interface's OWN id, not to be confused with its com_0 - every
	// interface's com_0 is one higher than its own root id, e.g. "2459=multi2" then
	// "2460=multi2:com_0", same offset bankpin_main uses; com_N for N>=1 is root+1+N). Corrected
	// 2026-09-02: first pass wrongly assumed com_0's id was the root id (off by one for all four).
	// If interface.pack is ever repacked such that these ids shift, these four constants need
	// updating to match.
	private static final int MULTI2_INTERFACE_ID = 2459;
	private static final int MULTI3_INTERFACE_ID = 2469;
	private static final int MULTI4_INTERFACE_ID = 2480;
	private static final int MULTI5_INTERFACE_ID = 2492;

	// Returns how many selectable options the currently-open chat interface has (2-5), or -1 if
	// it isn't one of the four "Select an Option" dialogue interfaces above.
	private int dialogueOptionCount(int interfaceId) {
		if (interfaceId == MULTI2_INTERFACE_ID) {
			return 2;
		} else if (interfaceId == MULTI3_INTERFACE_ID) {
			return 3;
		} else if (interfaceId == MULTI4_INTERFACE_ID) {
			return 4;
		} else if (interfaceId == MULTI5_INTERFACE_ID) {
			return 5;
		}
		return -1;
	}

	// QoL (Corey, 2026-09-02): bank PIN keypad number-key entry. bankpin_main (interface id 7424,
	// content/scripts/interfaces/bankpin_main.if) shows ten buttons (a_button..j_button) whose
	// digit values are reshuffled after every press (bank_pin.rs2's bankpin_shuffle proc) and
	// displayed via ten matching text labels (a_text..j_text). Typing a digit looks up which
	// label currently shows it and clicks that button - so this reads live component state
	// instead of trying to replicate the server's shuffle math client-side. Ids read directly
	// from interface.pack, same convention as the dialogue-option ids above.
	private static final int BANKPIN_MAIN_INTERFACE_ID = 7424;
	private static final int[] BANKPIN_DIGIT_TEXT_IDS = {14883, 14884, 14885, 14886, 14887, 14888, 14889, 14890, 14891, 14892}; // a_text..j_text
	private static final int[] BANKPIN_DIGIT_BUTTON_IDS = {14873, 14874, 14875, 14876, 14877, 14878, 14879, 14880, 14881, 14882}; // a_button..j_button

	// DEV: best-effort description of a useMenuOption() target, based on the menuParamA/B/C
	// conventions this client already uses per action family - see addNpcOptions() (npc index in
	// paramA), addPlayerOptions() (player index in paramA), and the loc menu-building block in
	// handleViewportOptions() (loc id packed into paramA, local tile x/z in paramB/paramC). These
	// action ids are read directly from where each family actually assigns them, not guessed.
	// Falls back to the raw params for anything else (interface-only clicks like bank/trade/
	// spellbook, "Walk here", etc.) so nothing is silently dropped.
	private String describeMenuTarget(int action, int paramA, int paramB, int paramC) {
		switch (action) {
			case 318:
			case 921:
			case 118:
			case 553:
			case 432:
			case 1668:
			case 67: {
				// NPC actions (addNpcOptions): paramA = index into this.npcs[]
				ClientNpc npc = paramA >= 0 && paramA < this.npcs.length ? this.npcs[paramA] : null;
				if (npc != null) {
					String name = npc.field1370 != null ? npc.field1370.field1455 : "?";
					return "target=npc name=\"" + name + "\" coords=(" + (npc.routeTileX[0] + this.sceneBaseTileX) + "," + (npc.routeTileZ[0] + this.sceneBaseTileZ) + "," + this.currentLevel + ")";
				}
				break;
			}
			case 200:
			case 493:
			case 408:
			case 677:
			case 876:
			case 918:
			case 596: {
				// Player actions (addPlayerOptions): paramA = index into this.players[]
				ClientPlayer p = paramA >= 0 && paramA < this.players.length ? this.players[paramA] : null;
				if (p != null) {
					return "target=player name=\"" + p.name + "\" coords=(" + (p.routeTileX[0] + this.sceneBaseTileX) + "," + (p.routeTileZ[0] + this.sceneBaseTileZ) + "," + this.currentLevel + ")";
				}
				break;
			}
			case 35:
			case 389:
			case 888:
			case 892:
			case 1280:
			case 1412:
			case 467:
			case 376: {
				// Loc actions (handleViewportOptions): paramA packs the loc id at bits 14-28,
				// paramB/paramC are the local scene tile x/z (add sceneBaseTileX/Z for world coords).
				int locId = paramA >> 14 & 0x7FFF;
				LocType loc = LocType.method561(locId);
				String name = loc != null ? loc.field1630 : "?";
				return "target=loc id=" + locId + " name=\"" + name + "\" coords=(" + (paramB + this.sceneBaseTileX) + "," + (paramC + this.sceneBaseTileZ) + "," + this.currentLevel + ")";
			}
			case WEAROP_ACTION:
			case WEAROP_ACTION + 1:
			case WEAROP_ACTION + 2:
			case WEAROP_ACTION + 3:
			case WEAROP_ACTION + 4:
			case WEAROP_ACTION + 5:
			case WEAROP_ACTION + 6:
			case WEAROP_ACTION + 7: {
				// Worn options: paramA = obj id, paramB = worn slot
				return "target=item id=" + paramA + " slot=" + paramB + " (worn, option " + (action - WEAROP_ACTION + 1) + ")";
			}
			case 891:
			case 894: {
				// Inventory item actions (OPHELD5/INV_BUTTON5, incl. shift-click drop): paramA = obj
				// id, paramB = inventory slot. No world coords - it's not placed anywhere.
				return "target=item id=" + paramA + " slot=" + paramB + " (inventory, no world coords)";
			}
			default:
				break;
		}
		return "target=? action=" + action + " paramA=" + paramA + " paramB=" + paramB + " paramC=" + paramC;
	}

	@ObfuscatedName("client.u(I)V")
	public void updateEntityChats() {
		for (int var2 = -1; var2 < this.playerCount; var2++) {
			int var6;
			if (var2 == -1) {
				var6 = this.LOCAL_PLAYER_INDEX;
			} else {
				var6 = this.playerIds[var2];
			}
			ClientPlayer var7 = this.players[var6];
			if (var7 != null && var7.chatTimer > 0) {
				var7.chatTimer--;
				if (var7.chatTimer == 0) {
					var7.chatMessage = null;
				}
			}
		}
		for (int var3 = 0; var3 < this.npcCount; var3++) {
			int var4 = this.npcIds[var3];
			ClientNpc var5 = this.npcs[var4];
			if (var5 != null && var5.chatTimer > 0) {
				var5.chatTimer--;
				if (var5.chatTimer == 0) {
					var5.chatMessage = null;
				}
			}
		}
	}

	@ObfuscatedName("client.e(I)V")
	public void updateOrbitCamera() {
		try {
			int var3 = localPlayer.field1157 + this.macroCameraX;
			int var4 = localPlayer.field1158 + this.macroCameraZ;
			if (this.orbitCameraX - var3 < -500 || this.orbitCameraX - var3 > 500 || this.orbitCameraZ - var4 < -500 || this.orbitCameraZ - var4 > 500) {
				this.orbitCameraX = var3;
				this.orbitCameraZ = var4;
			}
			if (this.orbitCameraX != var3) {
				this.orbitCameraX += (var3 - this.orbitCameraX) / 16;
			}
			if (this.orbitCameraZ != var4) {
				this.orbitCameraZ += (var4 - this.orbitCameraZ) / 16;
			}
			// QoL: click-compass-to-face-north. Manual rotation (arrow keys or the middle-mouse-drag
			// QoL below) cancels an in-progress reset so the player's own input always wins.
			if (this.compassResetting && (super.actionKey[1] == 1 || super.actionKey[2] == 1 || super.cameraDragDeltaX != 0 || super.cameraDragDeltaY != 0)) {
				this.compassResetting = false;
			}

			if (this.compassResetting) {
				// Shortest signed distance from the current yaw to north (0), in (-1024, 1024].
				int yawDeltaToNorth = ((-this.orbitCameraYaw + 1024) & 0x7FF) - 1024;
				if (yawDeltaToNorth > -12 && yawDeltaToNorth < 12) {
					// Close enough that one more tick would overshoot - snap the rest of the way and
					// stop, rather than letting the velocity ramp-down oscillate around 0.
					this.orbitCameraYaw = 0;
					this.orbitCameraYawVelocity = 0;
					this.compassResetting = false;
				} else if (yawDeltaToNorth < 0) {
					this.orbitCameraYawVelocity += (-24 - this.orbitCameraYawVelocity) / 2;
				} else {
					this.orbitCameraYawVelocity += (24 - this.orbitCameraYawVelocity) / 2;
				}
			} else if (super.actionKey[1] == 1) {
				this.orbitCameraYawVelocity += (-24 - this.orbitCameraYawVelocity) / 2;
			} else if (super.actionKey[2] == 1) {
				this.orbitCameraYawVelocity += (24 - this.orbitCameraYawVelocity) / 2;
			} else {
				this.orbitCameraYawVelocity /= 2;
			}
			if (super.actionKey[3] == 1) {
				this.orbitCameraPitchVelocity += (12 - this.orbitCameraPitchVelocity) / 2;
			} else if (super.actionKey[4] == 1) {
				this.orbitCameraPitchVelocity += (-12 - this.orbitCameraPitchVelocity) / 2;
			} else {
				this.orbitCameraPitchVelocity /= 2;
			}
			this.orbitCameraYaw = this.orbitCameraYawVelocity / 2 + this.orbitCameraYaw & 0x7FF;
			this.orbitCameraPitch += this.orbitCameraPitchVelocity / 2;

			// QoL: middle-mouse-drag camera rotation, on top of the arrow-key rotation above.
			if (super.cameraDragDeltaX != 0 || super.cameraDragDeltaY != 0) {
				if (QolSettings.on(QolSettings.MMB_CAMERA)) {
					this.orbitCameraYaw = this.orbitCameraYaw + super.cameraDragDeltaX * CAMERA_DRAG_YAW_NUM / CAMERA_DRAG_DIV & 0x7FF;
					this.orbitCameraPitch += super.cameraDragDeltaY * CAMERA_DRAG_PITCH_NUM / CAMERA_DRAG_DIV;
				}
				// Cleared whether or not the feature is on, so a drag made while it is off cannot bank up
				// and snap the camera the moment it is switched back on.
				super.cameraDragDeltaX = 0;
				super.cameraDragDeltaY = 0;
			}

			if (this.orbitCameraPitch < 128) {
				this.orbitCameraPitch = 128;
			}
			if (this.orbitCameraPitch > 383) {
				this.orbitCameraPitch = 383;
			}

			// QoL: scroll wheel camera zoom. Accumulate into cameraZoomOffset here (once per game
			// tick); drawScene() folds it into the camera distance every frame. Only zoom when no
			// interface is open (same fields closeInterfaces() checks) AND the mouse is actually
			// over the 3D game viewport (Corey, 2026-09-04) - otherwise scrolling over the
			// inventory/spellbook/tabs or the chatbox zoomed the camera too, on top of (or instead
			// of) whatever that panel's own scroll handling did with the same wheel delta. The
			// viewport rect below is the same one handleInput() uses to route hover input to the
			// viewport (see the identical check at ~line 4066) - this client is fixed 765x503, not
			// resizable, so these bounds are safe to hardcode here too.
			// QoL: an open menu owns the wheel, then an overlay that claimed the spot under the
			// cursor - a tall ground-item pile is one - then the camera. Each consumes the delta
			// when it takes it, so exactly one of the three acts on a turn: the order is the
			// specificity - a menu is in front of everything, an overlay is a thing you are
			// pointing at, the camera is what is left.
			// (The swaps panel used to go first here, while it was an in-game panel. It is a
			// plugin's page in the sidebar now, which is a Swing scroll pane and never sees the
			// game's wheel at all.)
			this.handleMenuScroll();
			// An overlay that claimed this spot for the wheel takes the turn, before the camera
			// gets a look.
			if (super.mouseScrollDelta != 0 && this.plugins != null
				&& this.plugins.onViewportScroll(super.mouseX - this.layout.vpX,
					super.mouseY - this.layout.vpY, super.mouseScrollDelta)) {
				super.mouseScrollDelta = 0;
			}
			if (super.mouseScrollDelta != 0 && this.sidebarInterfaceId == -1 && this.chatInterfaceId == -1 && this.fullscreenInterfaceId0 == -1 && this.fullscreenInterfaceId1 == -1 && this.viewportInterfaceId == -1 && this.layout.inViewport(super.mouseX, super.mouseY)) {
				if (QolSettings.on(QolSettings.WHEEL_ZOOM)) {
					this.cameraZoomOffset -= super.mouseScrollDelta * 40;
				}
				// Consumed either way - see the middle-mouse note above. It also stops a wheel turn over
				// the viewport falling through to another handler when zoom is off.
				super.mouseScrollDelta = 0;
				if (this.cameraZoomOffset < -600) {
					this.cameraZoomOffset = -600;
				}
				if (this.cameraZoomOffset > 900) {
					this.cameraZoomOffset = 900;
				}
			}
			int var5 = this.orbitCameraX >> 7;
			int var6 = this.orbitCameraZ >> 7;
			int var7 = this.getHeightmapY(this.orbitCameraZ, this.orbitCameraX, this.currentLevel);
			int var8 = 0;
			if (var5 > 3 && var6 > 3 && var5 < 100 && var6 < 100) {
				for (int var9 = var5 - 4; var9 <= var5 + 4; var9++) {
					for (int var10 = var6 - 4; var10 <= var6 + 4; var10++) {
						int var11 = this.currentLevel;
						if (var11 < 3 && (this.levelTileFlags[1][var9][var10] & 0x2) == 2) {
							var11++;
						}
						int var12 = var7 - this.levelHeightmap[var11][var9][var10];
						if (var12 > var8) {
							var8 = var12;
						}
					}
				}
			}
			int var13 = var8 * 192;
			if (var13 > 98048) {
				var13 = 98048;
			}
			if (var13 < 32768) {
				var13 = 32768;
			}
			if (var13 > this.cameraPitchClamp) {
				this.cameraPitchClamp += (var13 - this.cameraPitchClamp) / 24;
			} else if (var13 < this.cameraPitchClamp) {
				this.cameraPitchClamp += (var13 - this.cameraPitchClamp) / 80;
			}
		} catch (Exception var14) {
			signlink.reporterror("glfc_ex " + localPlayer.field1157 + "," + localPlayer.field1158 + "," + this.orbitCameraX + "," + this.orbitCameraZ + "," + this.sceneCenterZoneX + "," + this.sceneCenterZoneZ + "," + this.sceneBaseTileX + "," + this.sceneBaseTileZ);
			throw new RuntimeException("eek");
		}
	}

	@ObfuscatedName("client.e(Z)V")
	public void applyCutscene() {
		int var2 = this.cutsceneSrcLocalTileX * 128 + 64;
		int var3 = this.cutsceneSrcLocalTileZ * 128 + 64;
		int var4 = this.getHeightmapY(var3, var2, this.currentLevel) - this.cutsceneSrcHeight;
		if (this.cameraX < var2) {
			this.cameraX += (var2 - this.cameraX) * this.cutsceneMoveAcceleration / 1000 + this.cutsceneMoveSpeed;
			if (this.cameraX > var2) {
				this.cameraX = var2;
			}
		}
		if (this.cameraX > var2) {
			this.cameraX -= (this.cameraX - var2) * this.cutsceneMoveAcceleration / 1000 + this.cutsceneMoveSpeed;
			if (this.cameraX < var2) {
				this.cameraX = var2;
			}
		}
		if (this.cameraY < var4) {
			this.cameraY += (var4 - this.cameraY) * this.cutsceneMoveAcceleration / 1000 + this.cutsceneMoveSpeed;
			if (this.cameraY > var4) {
				this.cameraY = var4;
			}
		}
		if (this.cameraY > var4) {
			this.cameraY -= (this.cameraY - var4) * this.cutsceneMoveAcceleration / 1000 + this.cutsceneMoveSpeed;
			if (this.cameraY < var4) {
				this.cameraY = var4;
			}
		}
		if (this.cameraZ < var3) {
			this.cameraZ += (var3 - this.cameraZ) * this.cutsceneMoveAcceleration / 1000 + this.cutsceneMoveSpeed;
			if (this.cameraZ > var3) {
				this.cameraZ = var3;
			}
		}
		if (this.cameraZ > var3) {
			this.cameraZ -= (this.cameraZ - var3) * this.cutsceneMoveAcceleration / 1000 + this.cutsceneMoveSpeed;
			if (this.cameraZ < var3) {
				this.cameraZ = var3;
			}
		}
		int var5 = this.cutsceneDstLocalTileX * 128 + 64;
		int var6 = this.cutsceneDstLocalTileZ * 128 + 64;
		int var7 = this.getHeightmapY(var6, var5, this.currentLevel) - this.cutsceneDstHeight;
		int var8 = var5 - this.cameraX;
		int var9 = var7 - this.cameraY;
		int var10 = var6 - this.cameraZ;
		int var11 = (int) Math.sqrt((double) (var8 * var8 + var10 * var10));
		int var12 = (int) (Math.atan2((double) var9, (double) var11) * 325.949D) & 0x7FF;
		int var14 = (int) (Math.atan2((double) var8, (double) var10) * -325.949D) & 0x7FF;
		if (var12 < 128) {
			var12 = 128;
		}
		if (var12 > 383) {
			var12 = 383;
		}
		if (this.cameraPitch < var12) {
			this.cameraPitch += (var12 - this.cameraPitch) * this.field293 / 1000 + this.field292;
			if (this.cameraPitch > var12) {
				this.cameraPitch = var12;
			}
		}
		if (this.cameraPitch > var12) {
			this.cameraPitch -= (this.cameraPitch - var12) * this.field293 / 1000 + this.field292;
			if (this.cameraPitch < var12) {
				this.cameraPitch = var12;
			}
		}
		int var15 = var14 - this.cameraYaw;
		if (var15 > 1024) {
			var15 -= 2048;
		}
		if (var15 < -1024) {
			var15 += 2048;
		}
		if (var15 > 0) {
			this.cameraYaw += this.field293 * var15 / 1000 + this.field292;
			this.cameraYaw &= 0x7FF;
		}
		if (var15 < 0) {
			this.cameraYaw -= -var15 * this.field293 / 1000 + this.field292;
			this.cameraYaw &= 0x7FF;
		}
		int var16 = var14 - this.cameraYaw;
		if (var16 > 1024) {
			var16 -= 2048;
		}
		if (var16 < -1024) {
			var16 += 2048;
		}
		if (var16 < 0 && var15 > 0 || var16 > 0 && var15 < 0) {
			this.cameraYaw = var14;
		}
	}

	@ObfuscatedName("client.f(B)V")
	public void handleInputKey() {
		while (true) {
			int key;
			do {
				while (true) {
					key = this.pollKey();
					if (key == -1) {
						return;
					}

					// The plugin list, on the same terms as the QoL panel below it.
					if (key == PLUGIN_PANEL_KEY && this.ingame && this.plugins != null) {
						// One key, two interfaces: where there is a window there is a sidebar, and
						// F8 shows and hides it. The in-canvas panel is for the applet, which has
						// no window to put a sidebar in.
						if (this.pluginSidebar != null) {
							this.togglePluginSidebar();
							continue;
						}
						this.pluginPanelOpen = !this.pluginPanelOpen;
						if (this.pluginPanelOpen) {
							this.closeInterfaces();
						}
						continue;
					}
					if (this.pluginPanelOpen) {
						if (key == GameShell.KEY_ESCAPE) {
							this.pluginPanelOpen = false;
						}
						continue;
					}
					// QoL settings panel. Handled at the very top of the key loop so it works from
					// any interface state, and so its keys are swallowed rather than reaching chat.
					if (key == QOL_PANEL_KEY && this.ingame) {
						this.qolPanelOpen = !this.qolPanelOpen;
						if (this.qolPanelOpen) {
							// Opening closes whatever interface is up. The panel draws inside
							// areaViewport, so an interface drawn over the viewport would bury it
							// while it was still swallowing input - a dead client with nothing on
							// screen to explain why. Closing first guarantees it is visible whenever
							// it is modal.
							this.closeInterfaces();
						}
						continue;
					}
					if (this.qolPanelOpen) {
						if (key == GameShell.KEY_ESCAPE) {
							this.qolPanelOpen = false;
						}
						continue;
					}
					// Alt is read as a held key, never from the queue - swallow it here so an
					// auto-repeating hold cannot fall through into chat or anything else. The
					// Ground items plugin reads it through PluginContext.isAltHeld().
					if (key == GameShell.KEY_ALT) {
						continue;
					}

					// A plugin's own hotkey, offered the key before the game sees it: consuming one
					// stops it reaching chat, which is what a plugin bound to a letter needs.
					// Deliberately BELOW the client's own panel keys - a plugin that swallowed
					// everything must never be able to lock the player out of the panel that turns
					// it off.
					if (this.plugins != null && this.plugins.onKeyPressed(key)) {
						continue;
					}

					// QoL: Escape closes whatever interface is currently open, regardless of state.
					// Not consumed - anything else that wants Escape still gets it, which is how
					// this has always behaved.
					if (key == GameShell.KEY_ESCAPE && QolSettings.on(QolSettings.ESC_CLOSE)) {
						DevLog.log("HOTKEY", "Escape closed interfaces");
						this.closeInterfaces();
					}

					if (this.viewportInterfaceId != -1 && this.reportAbuseInterfaceId == this.viewportInterfaceId) {
						if (key == 8 && this.reportAbuseInput.length() > 0) {
							this.reportAbuseInput = this.reportAbuseInput.substring(0, this.reportAbuseInput.length() - 1);
						}

						break;
					}

					if (this.showSocialInput) {
						if (key >= 32 && key <= 122 && this.socialInput.length() < 80) {
							this.socialInput = this.socialInput + (char) key;
							this.redrawChatback = true;
						}

						if (key == 8 && this.socialInput.length() > 0) {
							this.socialInput = this.socialInput.substring(0, this.socialInput.length() - 1);
							this.redrawChatback = true;
						}

						if (key == 13 || key == 10) {
							this.showSocialInput = false;
							this.redrawChatback = true;

							if (this.socialInputType == 1) {
								long username = JString.toBase37(this.socialInput);
								this.addFriend(username);
							}

							if (this.socialInputType == 2 && this.friendCount > 0) {
								long username = JString.toBase37(this.socialInput);
								this.removeFriend(username);
							}

							if (this.socialInputType == 3) {
								// the line as typed, case and all (ChatText, not WordPack)
								this.socialInput = ChatText.format(this.socialInput);
							}

							if (this.socialInputType == 3 && this.socialInput.length() > 0) {
								// MESSAGE_PRIVATE
								this.out.p1isaac(227);
								this.out.p1(0);

								int start = this.out.pos;
								this.out.p8(this.socialName37);
								ChatText.pack(this.socialInput, this.out);
								this.out.psize1(this.out.pos - start);

								// this.socialInput = WordFilter.filter(this.socialInput); // client-side profanity filter disabled per Corey's request

								this.addMessage(JString.formatDisplayName(JString.fromBase37(this.socialName37)), this.socialInput, 6);

								if (this.chatPrivateMode == 2) {
									this.chatPrivateMode = 1;
									this.redrawPrivacySettings = true;

									// CHAT_SETMODE
									this.out.p1isaac(176);
									this.out.p1(this.chatPublicMode);
									this.out.p1(this.chatPrivateMode);
									this.out.p1(this.chatTradeMode);
								}
							}

							if (this.socialInputType == 4 && this.ignoreCount < 100) {
								long username = JString.toBase37(this.socialInput);
								this.addIgnore(username);
							}

							if (this.socialInputType == 5 && this.ignoreCount > 0) {
								long username = JString.toBase37(this.socialInput);
								this.removeIgnore(username);
							}

							if (this.socialInputType == 6 && this.socialInput.length() > 0) {
								// CLAN_JOINCHAT (custom)
								this.out.p1isaac(7);
								this.out.p8(JString.toBase37(this.socialInput));
							}
						}
					} else if (this.chatbackInputOpen == 1) {
						// "Enter amount": digits, plus one k/m/b suffix (and one '.', for "1.5m")
						if (acceptAmountKey(this.chatbackInput, key)) {
							this.chatbackInput = this.chatbackInput + (char) key;
							this.redrawChatback = true;
						}

						if (key == 8 && this.chatbackInput.length() > 0) {
							this.chatbackInput = this.chatbackInput.substring(0, this.chatbackInput.length() - 1);
							this.redrawChatback = true;
						}

						if (key == 13 || key == 10) {
							if (this.chatbackInput.length() > 0) {
								int value = parseAmount(this.chatbackInput);

								// RESUME_P_COUNTDIALOG
								this.out.p1isaac(75);
								this.out.p4(value);
							}

							this.chatbackInputOpen = 0;
							this.redrawChatback = true;
						}
					} else if (this.chatbackInputOpen == 2) {
						if (key >= 32 && key <= 122 && this.chatbackInput.length() < 12) {
							this.chatbackInput = this.chatbackInput + (char) key;
							this.redrawChatback = true;
						}

						if (key == 8 && this.chatbackInput.length() > 0) {
							this.chatbackInput = this.chatbackInput.substring(0, this.chatbackInput.length() - 1);
							this.redrawChatback = true;
						}

						if (key == 13 || key == 10) {
							if (this.chatbackInput.length() > 0) {
								// RESUME_P_NAMEDIALOG
								this.out.p1isaac(206);
								this.out.p8(JString.toBase37(this.chatbackInput));
							}

							this.chatbackInputOpen = 0;
							this.redrawChatback = true;
						}
					} else if (this.chatbackInputOpen == 4) {
						// Bank search box: filters as you type, Enter or Escape closes it
						if (key >= 32 && key <= 122 && this.chatbackInput.length() < 20) {
							this.chatbackInput = this.chatbackInput + (char) key;
							this.bankSearchText = this.chatbackInput;
							this.applyBankSearch();
							this.redrawChatback = true;
						}

						if (key == 8 && this.chatbackInput.length() > 0) {
							this.chatbackInput = this.chatbackInput.substring(0, this.chatbackInput.length() - 1);
							this.bankSearchText = this.chatbackInput;
							this.applyBankSearch();
							this.redrawChatback = true;
						}

						if (key == 13 || key == 10 || key == 27) {
							this.toggleBankSearch();
						}
					} else if (this.chatbackInputOpen == 3) {
						if (key >= 32 && key <= 122 && this.chatbackInput.length() < 40) {
							this.chatbackInput = this.chatbackInput + (char) key;
							this.redrawChatback = true;
						}

						if (key == 8 && this.chatbackInput.length() > 0) {
							this.chatbackInput = this.chatbackInput.substring(0, this.chatbackInput.length() - 1);
							this.redrawChatback = true;
						}
					} else if (key == 9 && this.hasLastPmFrom && QolSettings.on(QolSettings.TAB_REPLY)) {
						// QoL: Tab replies to whoever last sent you a PM
						DevLog.log("HOTKEY", "Tab reply to " + JString.formatDisplayName(JString.fromBase37(this.lastPmFrom37)));
						this.redrawChatback = true;
						this.chatbackInputOpen = 0;
						this.showSocialInput = true;
						this.socialInput = "";
						this.socialInputType = 3;
						this.socialName37 = this.lastPmFrom37;
						this.socialMessage = "Enter message to send to " + JString.formatDisplayName(JString.fromBase37(this.lastPmFrom37));
					} else if (this.chatInterfaceId != -1 && key == 32 && QolSettings.on(QolSettings.SPACE_CONTINUE)) {
						// QoL: space bar advances "click here to continue" dialogues
						if (!this.pressedContinueOption) {
							int continueComponentId = this.findContinueComponentId(Component.get(this.chatInterfaceId));
							if (continueComponentId != -1) {
								DevLog.log("HOTKEY", "Space advanced dialogue");
								// RESUME_PAUSEBUTTON
								this.out.p1isaac(226);
								this.out.p2(continueComponentId);
								this.pressedContinueOption = true;
							}
						}
					} else if (this.chatInterfaceId != -1 && key >= 49 && key <= 53 && QolSettings.on(QolSettings.DIALOGUE_KEYS)) {
						// QoL: number keys 1-5 pick the matching "Select an Option" dialogue choice.
						// See dialogueOptionCount() above for why this is scoped to just multi2-5.
						// Corrected 2026-09-02: a buttonType 1 ("normal") click - which is what every
						// multiN:com_N option button is - is NOT a RESUME_PAUSEBUTTON click. Real mouse
						// clicks on buttonType 1 components go through menu action 352, which sends
						// IF_BUTTON (opcode 79, see the "var5 == 352" block elsewhere in this file) -
						// RESUME_PAUSEBUTTON (226) is only for buttonType 6 ("pause"/continue) targets,
						// which is why the space-bar continue hotkey right above this correctly uses it
						// and this one, copied from that without checking, originally did not work.
						int optionCount = this.dialogueOptionCount(this.chatInterfaceId);
						int digit = key - 48;
						if (optionCount != -1 && digit <= optionCount && !this.pressedContinueOption) {
							int optionComponentId = this.chatInterfaceId + 1 + digit;
							DevLog.log("HOTKEY", "Number key selected dialogue option " + digit);
							// IF_BUTTON
							this.out.p1isaac(79);
							this.out.p2(optionComponentId);
							this.pressedContinueOption = true;
						}
					} else if (this.viewportInterfaceId == BANKPIN_MAIN_INTERFACE_ID && key >= 48 && key <= 57 && QolSettings.on(QolSettings.BANKPIN_KEYS)) {
						// QoL: typing a digit clicks whichever shuffled bank PIN keypad button
						// currently displays it. Time-debounced rather than using
						// pressedContinueOption, since this interface stays open across all 4
						// digit presses (just reshuffling each time) instead of closing/reopening.
						// Corrected 2026-09-02: same wrong-opcode bug as the dialogue-option hotkey
						// above - a_button..j_button are buttonType 1, so this needs IF_BUTTON (79),
						// not RESUME_PAUSEBUTTON (226). This is why the earlier version logged the
						// hotkey firing (the digit/button lookup itself was correct) but nothing
						// actually happened in game - the server has no paused resume target
						// registered for these buttons at all, so it silently ignored the packet.
						long now = System.currentTimeMillis();
						if (now - this.lastBankPinKeyTime >= 200L) {
							int digit = key - 48;
							String digitText = String.valueOf(digit);
							for (int i = 0; i < BANKPIN_DIGIT_TEXT_IDS.length; i++) {
								Component digitCom = Component.get(BANKPIN_DIGIT_TEXT_IDS[i]);
								if (digitCom != null && digitText.equals(digitCom.text)) {
									DevLog.log("HOTKEY", "Number key entered bank PIN digit " + digit);
									// IF_BUTTON
									this.out.p1isaac(79);
									this.out.p2(BANKPIN_DIGIT_BUTTON_IDS[i]);
									this.lastBankPinKeyTime = now;
									break;
								}
							}
						}
					} else if (this.chatInterfaceId == -1 && this.fullscreenInterfaceId0 == -1) {
						if (key >= 32 && (key <= 122 || (this.chatTyped.startsWith("::") && key <= 126)) && this.chatTyped.length() < 80) {
							this.chatTyped = this.chatTyped + (char) key;
							this.redrawChatback = true;
						}

						if (key == 8 && this.chatTyped.length() > 0) {
							this.chatTyped = this.chatTyped.substring(0, this.chatTyped.length() - 1);
							this.redrawChatback = true;
						}

						// QoL: Page Up walks back through sent lines, Page Down walks forward again.
						if (key == 1002 && this.chatHistoryCount > 0 && QolSettings.on(QolSettings.CHAT_HISTORY)) {
							if (this.chatHistoryBrowsePos == -1) {
								this.chatHistoryBrowseDraft = this.chatTyped;
								this.chatHistoryBrowsePos = 0;
							} else if (this.chatHistoryBrowsePos < this.chatHistoryCount - 1) {
								this.chatHistoryBrowsePos++;
							}
							this.chatTyped = this.chatHistory[this.chatHistoryBrowsePos];
							this.redrawChatback = true;
						}

						if (key == 1003 && this.chatHistoryBrowsePos != -1 && QolSettings.on(QolSettings.CHAT_HISTORY)) {
							if (this.chatHistoryBrowsePos > 0) {
								this.chatHistoryBrowsePos--;
								this.chatTyped = this.chatHistory[this.chatHistoryBrowsePos];
							} else {
								// Past the newest entry - hand back whatever was being typed before browsing.
								this.chatHistoryBrowsePos = -1;
								this.chatTyped = this.chatHistoryBrowseDraft;
							}
							this.redrawChatback = true;
						}

						if ((key == 13 || key == 10) && this.chatTyped.length() > 0) {
							// Captured here, before the colour and effect prefixes below start substring-ing
							// chatTyped apart - the history should hold what was actually typed, so recalling
							// "red:hello" or a "::command" gives it back whole.
							this.pushChatHistory(this.chatTyped);
							// >= 2, not == 2: the login rank is 4 for a developer since the purple crown, and
							// these are the commands developers are the ones to use.
							if (this.staffmodlevel >= 2) {
								if (this.chatTyped.equals("::clientdrop")) {
									this.tryReconnect();
								} else if (this.chatTyped.equals("::lag")) {
									this.lag();
								} else if (this.chatTyped.equals("::prefetchmusic")) {
									for (int i = 0; i < this.onDemand.getFileCount(2); i++) {
										this.onDemand.prefetchPriority(2, (byte) 1, i);
									}
								} else if (this.chatTyped.equals("::fpson")) {
									displayFps = true;
								} else if (this.chatTyped.equals("::fpsoff")) {
									displayFps = false;
								} else if (this.chatTyped.equals("::noclip")) {
									for (int level = 0; level < 4; level++) {
										for (int x = 1; x < 103; x++) {
											for (int z = 1; z < 103; z++) {
												this.levelCollisionMap[level].field1585[x][z] = 0;
											}
										}
									}
								}
							}

							if (this.chatTyped.startsWith("::")) {
								// CLIENT_CHEAT
								this.out.p1isaac(56);
								this.out.p1(this.chatTyped.length() - 1);
								this.out.pjstr(this.chatTyped.substring(2));
							} else if (this.chatTyped.startsWith("/") && this.chatTyped.length() > 1) {
								// CLAN_MESSAGE (custom): the server sends the line back to everybody in the
								// channel, this player included, so there is no local echo
								String clanLine = ChatText.format(this.chatTyped.substring(1));
								if (clanLine.length() > 0) {
									this.out.p1isaac(9);
									this.out.p1(0);
									int start = this.out.pos;
									ChatText.pack(clanLine, this.out);
									this.out.psize1(this.out.pos - start);
								}
							} else {
								String lower = this.chatTyped.toLowerCase();

								byte colour = 0;
								if (lower.startsWith("yellow:")) {
									colour = 0;
									this.chatTyped = this.chatTyped.substring(7);
								} else if (lower.startsWith("red:")) {
									colour = 1;
									this.chatTyped = this.chatTyped.substring(4);
								} else if (lower.startsWith("green:")) {
									colour = 2;
									this.chatTyped = this.chatTyped.substring(6);
								} else if (lower.startsWith("cyan:")) {
									colour = 3;
									this.chatTyped = this.chatTyped.substring(5);
								} else if (lower.startsWith("purple:")) {
									colour = 4;
									this.chatTyped = this.chatTyped.substring(7);
								} else if (lower.startsWith("white:")) {
									colour = 5;
									this.chatTyped = this.chatTyped.substring(6);
								} else if (lower.startsWith("flash1:")) {
									colour = 6;
									this.chatTyped = this.chatTyped.substring(7);
								} else if (lower.startsWith("flash2:")) {
									colour = 7;
									this.chatTyped = this.chatTyped.substring(7);
								} else if (lower.startsWith("flash3:")) {
									colour = 8;
									this.chatTyped = this.chatTyped.substring(7);
								} else if (lower.startsWith("glow1:")) {
									colour = 9;
									this.chatTyped = this.chatTyped.substring(6);
								} else if (lower.startsWith("glow2:")) {
									colour = 10;
									this.chatTyped = this.chatTyped.substring(6);
								} else if (lower.startsWith("glow3:")) {
									colour = 11;
									this.chatTyped = this.chatTyped.substring(6);
								}

								String lower2 = this.chatTyped.toLowerCase();
								byte effect = 0;
								if (lower2.startsWith("wave:")) {
									effect = 1;
									this.chatTyped = this.chatTyped.substring(5);
								} else if (lower2.startsWith("wave2:")) {
									effect = 2;
									this.chatTyped = this.chatTyped.substring(6);
								} else if (lower2.startsWith("shake:")) {
									effect = 3;
									this.chatTyped = this.chatTyped.substring(6);
								} else if (lower2.startsWith("scroll:")) {
									effect = 4;
									this.chatTyped = this.chatTyped.substring(7);
								} else if (lower2.startsWith("slide:")) {
									effect = 5;
									this.chatTyped = this.chatTyped.substring(6);
								}

								// the line as typed, case and all (ChatText, not WordPack); a colour or effect
								// prefix with nothing after it says nothing
								this.chatTyped = ChatText.format(this.chatTyped);
								if (this.chatTyped.length() > 0) {
									// MESSAGE_PUBLIC
									this.out.p1isaac(49);
									this.out.p1(0);

									int start = this.out.pos;
									this.out.p1_alt2(colour);
									this.out.p1_alt1(effect);
									ChatText.pack(this.chatTyped, this.out);
									this.out.psize1(this.out.pos - start);

									// this.chatTyped = WordFilter.filter(this.chatTyped); // client-side profanity filter disabled per Corey's request

									localPlayer.chatMessage = this.chatTyped;
									localPlayer.chatColour = colour;
									localPlayer.chatEffect = effect;
									localPlayer.chatTimer = 150;

									String icons = localPlayer.icons.length() > 0 ? localPlayer.icons : ChatIcons.forPlayer(this.staffmodlevel == 3 ? 2 : this.staffmodlevel);
									this.addMessage(icons + localPlayer.name, localPlayer.chatMessage, 2);

									if (this.chatPublicMode == 2) {
										this.chatPublicMode = 3;
										this.redrawPrivacySettings = true;
										// CHAT_SETMODE
										this.out.p1isaac(176);
										this.out.p1(this.chatPublicMode);
										this.out.p1(this.chatPrivateMode);
										this.out.p1(this.chatTradeMode);
									}
								}
							}

							this.chatTyped = "";
							this.redrawChatback = true;
						}
					}
				}
			} while ((key < 97 || key > 122) && (key < 65 || key > 90) && (key < 48 || key > 57) && key != 32);

			if (this.reportAbuseInput.length() < 12) {
				this.reportAbuseInput = this.reportAbuseInput + (char) key;
			}
		}
	}

	@ObfuscatedName("client.q(Z)V")
	public void lag() {
		System.out.println("============");
		System.out.println("flame-cycle:" + this.flameCycle);
		if (this.onDemand != null) {
			System.out.println("Od-cycle:" + this.onDemand.cycle);
		}
		System.out.println("loop-cycle:" + loopCycle);
		System.out.println("draw-cycle:" + drawCycle);
		System.out.println("ptype:" + this.ptype);
		System.out.println("psize:" + this.psize);
		if (this.stream != null) {
			this.stream.method238();
		}
		super.debug = true;
	}

	@ObfuscatedName("client.z(I)V")
	public void updatePlayers() {
		for (int var2 = -1; var2 < this.playerCount; var2++) {
			int var4;
			if (var2 == -1) {
				var4 = this.LOCAL_PLAYER_INDEX;
			} else {
				var4 = this.playerIds[var2];
			}
			ClientPlayer var5 = this.players[var4];
			if (var5 != null) {
				this.updateEntity(1, var5);
			}
		}
	}

	@ObfuscatedName("client.o(I)V")
	public void updateNpcs() {
		for (int var2 = 0; var2 < this.npcCount; var2++) {
			int var3 = this.npcIds[var2];
			ClientNpc var4 = this.npcs[var3];
			if (var4 != null) {
				this.updateEntity(var4.field1370.field1445, var4);
			}
		}
	}

	@ObfuscatedName("client.a(IBLLRUWCBNN;)V")
	public void updateEntity(int arg0, ClientEntity arg2) {
		if (arg2.field1157 < 128 || arg2.field1158 < 128 || arg2.field1157 >= 13184 || arg2.field1158 >= 13184) {
			arg2.field1171 = -1;
			arg2.field1161 = -1;
			arg2.field1153 = 0;
			arg2.field1154 = 0;
			arg2.field1157 = arg2.routeTileX[0] * 128 + arg2.field1148 * 64;
			arg2.field1158 = arg2.routeTileZ[0] * 128 + arg2.field1148 * 64;
			arg2.clearRoute();
		}
		if (localPlayer == arg2 && (arg2.field1157 < 1536 || arg2.field1158 < 1536 || arg2.field1157 >= 11776 || arg2.field1158 >= 11776)) {
			arg2.field1171 = -1;
			arg2.field1161 = -1;
			arg2.field1153 = 0;
			arg2.field1154 = 0;
			arg2.field1157 = arg2.routeTileX[0] * 128 + arg2.field1148 * 64;
			arg2.field1158 = arg2.routeTileZ[0] * 128 + arg2.field1148 * 64;
			arg2.clearRoute();
		}
		if (arg2.field1153 > loopCycle) {
			this.updateForceMovement(arg2);
		} else if (arg2.field1154 >= loopCycle) {
			this.startForceMovement(arg2);
		} else {
			this.updateMovement(arg2);
		}
		this.updateFacingDirection(arg2);
		this.updateSequences(arg2);
	}

	@ObfuscatedName("client.a(LLRUWCBNN;Z)V")
	public void updateForceMovement(ClientEntity arg0) {
		int var3 = arg0.field1153 - loopCycle;
		int var4 = arg0.field1149 * 128 + arg0.field1148 * 64;
		int var5 = arg0.field1151 * 128 + arg0.field1148 * 64;
		arg0.field1157 += (var4 - arg0.field1157) / var3;
		arg0.field1158 += (var5 - arg0.field1158) / var3;
		arg0.field1170 = 0;
		if (arg0.field1155 == 0) {
			arg0.field1131 = 1024;
		}
		if (arg0.field1155 == 1) {
			arg0.field1131 = 1536;
		}
		if (arg0.field1155 == 2) {
			arg0.field1131 = 0;
		}
		if (arg0.field1155 == 3) {
			arg0.field1131 = 512;
		}
	}

	@ObfuscatedName("client.a(LLRUWCBNN;I)V")
	public void startForceMovement(ClientEntity arg0) {
		if (loopCycle == arg0.field1154 || arg0.field1171 == -1 || arg0.field1174 != 0 || arg0.field1173 + 1 > SeqType.field775[arg0.field1171].method214(arg0.field1172)) {
			int var3 = arg0.field1154 - arg0.field1153;
			int var4 = loopCycle - arg0.field1153;
			int var5 = arg0.field1149 * 128 + arg0.field1148 * 64;
			int var6 = arg0.field1151 * 128 + arg0.field1148 * 64;
			int var7 = arg0.field1150 * 128 + arg0.field1148 * 64;
			int var8 = arg0.field1152 * 128 + arg0.field1148 * 64;
			arg0.field1157 = ((var3 - var4) * var5 + var4 * var7) / var3;
			arg0.field1158 = ((var3 - var4) * var6 + var4 * var8) / var3;
		}
		arg0.field1170 = 0;
		if (arg0.field1155 == 0) {
			arg0.field1131 = 1024;
		}
		if (arg0.field1155 == 1) {
			arg0.field1131 = 1536;
		}
		if (arg0.field1155 == 2) {
			arg0.field1131 = 0;
		}
		if (arg0.field1155 == 3) {
			arg0.field1131 = 512;
		}
		arg0.field1159 = arg0.field1131;
	}

	@ObfuscatedName("client.b(LLRUWCBNN;I)V")
	public void updateMovement(ClientEntity arg0) {
		arg0.field1135 = arg0.field1181;
		if (arg0.field1180 == 0) {
			arg0.field1170 = 0;
			return;
		}
		if (arg0.field1171 != -1 && arg0.field1174 == 0) {
			SeqType var3 = SeqType.field775[arg0.field1171];
			if (arg0.field1160 > 0 && var3.field787 == 0) {
				arg0.field1170++;
				return;
			}
			if (arg0.field1160 <= 0 && var3.field788 == 0) {
				arg0.field1170++;
				return;
			}
		}
		int var4 = arg0.field1157;
		int var5 = arg0.field1158;
		int var6 = arg0.routeTileX[arg0.field1180 - 1] * 128 + arg0.field1148 * 64;
		int var7 = arg0.routeTileZ[arg0.field1180 - 1] * 128 + arg0.field1148 * 64;
		if (var6 - var4 > 256 || var6 - var4 < -256 || var7 - var5 > 256 || var7 - var5 < -256) {
			arg0.field1157 = var6;
			arg0.field1158 = var7;
			return;
		}
		if (var4 < var6) {
			if (var5 < var7) {
				arg0.field1131 = 1280;
			} else if (var5 > var7) {
				arg0.field1131 = 1792;
			} else {
				arg0.field1131 = 1536;
			}
		} else if (var4 > var6) {
			if (var5 < var7) {
				arg0.field1131 = 768;
			} else if (var5 > var7) {
				arg0.field1131 = 256;
			} else {
				arg0.field1131 = 512;
			}
		} else if (var5 < var7) {
			arg0.field1131 = 1024;
		} else {
			arg0.field1131 = 0;
		}
		int var8 = arg0.field1131 - arg0.field1159 & 0x7FF;
		if (var8 > 1024) {
			var8 -= 2048;
		}
		int var9 = arg0.field1167;
		if (var8 >= -256 && var8 <= 256) {
			var9 = arg0.field1166;
		} else if (var8 >= 256 && var8 < 768) {
			var9 = arg0.field1169;
		} else if (var8 >= -768 && var8 <= -256) {
			var9 = arg0.field1168;
		}
		if (var9 == -1) {
			var9 = arg0.field1166;
		}
		arg0.field1135 = var9;
		int var10 = 4;
		if (arg0.field1159 != arg0.field1131 && arg0.field1156 == -1 && arg0.field1147 != 0) {
			var10 = 2;
		}
		if (arg0.field1180 > 2) {
			var10 = 6;
		}
		if (arg0.field1180 > 3) {
			var10 = 8;
		}
		if (arg0.field1170 > 0 && arg0.field1180 > 1) {
			var10 = 8;
			arg0.field1170--;
		}
		if (arg0.field1138[arg0.field1180 - 1]) {
			var10 <<= 0x1;
		}
		if (var10 >= 8 && arg0.field1166 == arg0.field1135 && arg0.field1176 != -1) {
			arg0.field1135 = arg0.field1176;
		}
		if (var4 < var6) {
			arg0.field1157 += var10;
			if (arg0.field1157 > var6) {
				arg0.field1157 = var6;
			}
		} else if (var4 > var6) {
			arg0.field1157 -= var10;
			if (arg0.field1157 < var6) {
				arg0.field1157 = var6;
			}
		}
		if (var5 < var7) {
			arg0.field1158 += var10;
			if (arg0.field1158 > var7) {
				arg0.field1158 = var7;
			}
		} else if (var5 > var7) {
			arg0.field1158 -= var10;
			if (arg0.field1158 < var7) {
				arg0.field1158 = var7;
			}
		}
		if (arg0.field1157 == var6 && arg0.field1158 == var7) {
			arg0.field1180--;
			if (arg0.field1160 > 0) {
				arg0.field1160--;
			}
		}
	}

	@ObfuscatedName("client.a(BLLRUWCBNN;)V")
	public void updateFacingDirection(ClientEntity arg1) {
		if (arg1.field1147 == 0) {
			return;
		}
		if (arg1.field1156 != -1 && arg1.field1156 < 32768) {
			ClientNpc var3 = this.npcs[arg1.field1156];
			if (var3 != null) {
				int var4 = arg1.field1157 - var3.field1157;
				int var5 = arg1.field1158 - var3.field1158;
				if (var4 != 0 || var5 != 0) {
					arg1.field1131 = (int) (Math.atan2((double) var4, (double) var5) * 325.949D) & 0x7FF;
				}
			}
		}
		if (arg1.field1156 >= 32768) {
			int var6 = arg1.field1156 - 32768;
			if (this.localPid == var6) {
				var6 = this.LOCAL_PLAYER_INDEX;
			}
			ClientPlayer var7 = this.players[var6];
			if (var7 != null) {
				int var8 = arg1.field1157 - var7.field1157;
				int var9 = arg1.field1158 - var7.field1158;
				if (var8 != 0 || var9 != 0) {
					arg1.field1131 = (int) (Math.atan2((double) var8, (double) var9) * 325.949D) & 0x7FF;
				}
			}
		}
		if ((arg1.field1145 != 0 || arg1.field1146 != 0) && (arg1.field1180 == 0 || arg1.field1170 > 0)) {
			int var10 = arg1.field1157 - (arg1.field1145 - this.sceneBaseTileX - this.sceneBaseTileX) * 64;
			int var11 = arg1.field1158 - (arg1.field1146 - this.sceneBaseTileZ - this.sceneBaseTileZ) * 64;
			if (var10 != 0 || var11 != 0) {
				arg1.field1131 = (int) (Math.atan2((double) var10, (double) var11) * 325.949D) & 0x7FF;
			}
			arg1.field1145 = 0;
			arg1.field1146 = 0;
		}
		int var12 = arg1.field1131 - arg1.field1159 & 0x7FF;
		if (var12 != 0) {
			if (var12 < arg1.field1147 || var12 > 2048 - arg1.field1147) {
				arg1.field1159 = arg1.field1131;
			} else if (var12 > 1024) {
				arg1.field1159 -= arg1.field1147;
			} else {
				arg1.field1159 += arg1.field1147;
			}
			arg1.field1159 &= 0x7FF;
			if (arg1.field1181 == arg1.field1135 && arg1.field1159 != arg1.field1131) {
				if (arg1.field1182 != -1) {
					arg1.field1135 = arg1.field1182;
				} else {
					arg1.field1135 = arg1.field1166;
				}
			}
		}
	}

	@ObfuscatedName("client.c(LLRUWCBNN;I)V")
	public void updateSequences(ClientEntity arg0) {
		arg0.field1139 = false;
		if (arg0.field1135 != -1) {
			SeqType var3 = SeqType.field775[arg0.field1135];
			arg0.field1137++;
			if (arg0.field1136 < var3.field776 && arg0.field1137 > var3.method214(arg0.field1136)) {
				arg0.field1137 = 1;
				arg0.field1136++;
			}
			if (arg0.field1136 >= var3.field776) {
				arg0.field1137 = 1;
				arg0.field1136 = 0;
			}
		}
		if (arg0.field1161 != -1 && loopCycle >= arg0.field1164) {
			if (arg0.field1162 < 0) {
				arg0.field1162 = 0;
			}
			SeqType var4 = SpotAnimType.field1297[arg0.field1161].field1301;
			arg0.field1163++;
			if (arg0.field1162 < var4.field776 && arg0.field1163 > var4.method214(arg0.field1162)) {
				arg0.field1163 = 1;
				arg0.field1162++;
			}
			if (arg0.field1162 >= var4.field776 && (arg0.field1162 < 0 || arg0.field1162 >= var4.field776)) {
				arg0.field1161 = -1;
			}
		}
		if (arg0.field1171 != -1 && arg0.field1174 <= 1) {
			SeqType var5 = SeqType.field775[arg0.field1171];
			if (var5.field787 == 1 && arg0.field1160 > 0 && arg0.field1153 <= loopCycle && arg0.field1154 < loopCycle) {
				arg0.field1174 = 1;
				return;
			}
		}
		if (arg0.field1171 != -1 && arg0.field1174 == 0) {
			SeqType var6 = SeqType.field775[arg0.field1171];
			arg0.field1173++;
			if (arg0.field1172 < var6.field776 && arg0.field1173 > var6.method214(arg0.field1172)) {
				arg0.field1173 = 1;
				arg0.field1172++;
			}
			if (arg0.field1172 >= var6.field776) {
				arg0.field1172 -= var6.field780;
				arg0.field1175++;
				if (arg0.field1175 >= var6.field786) {
					arg0.field1171 = -1;
				}
				if (arg0.field1172 < 0 || arg0.field1172 >= var6.field776) {
					arg0.field1171 = -1;
				}
			}
			arg0.field1139 = var6.field782;
		}
		if (arg0.field1174 > 0) {
			arg0.field1174--;
		}
	}

	@ObfuscatedName("client.n(I)V")
	public void loadTitle() {
		if (this.imageTitle2 != null) {
			return;
		}
		super.drawArea = null;
		this.areaChatback = null;
		this.areaMapback = null;
		this.areaSidebar = null;
		this.areaViewport = null;
		this.areaBackbase1 = null;
		this.areaBackbase2 = null;
		this.areaBackmid1 = null;
		this.imageTitle0 = new PixMap(265, this.getBaseComponent(), 128);
		Pix2D.cls();
		this.imageTitle1 = new PixMap(265, this.getBaseComponent(), 128);
		Pix2D.cls();
		this.imageTitle2 = new PixMap(171, this.getBaseComponent(), 509);
		Pix2D.cls();
		this.imageTitle3 = new PixMap(132, this.getBaseComponent(), 360);
		Pix2D.cls();
		this.imageTitle4 = new PixMap(200, this.getBaseComponent(), 360);
		Pix2D.cls();
		this.imageTitle5 = new PixMap(238, this.getBaseComponent(), 202);
		Pix2D.cls();
		this.imageTitle6 = new PixMap(238, this.getBaseComponent(), 203);
		Pix2D.cls();
		this.imageTitle7 = new PixMap(94, this.getBaseComponent(), 74);
		Pix2D.cls();
		this.imageTitle8 = new PixMap(94, this.getBaseComponent(), 75);
		Pix2D.cls();
		if (this.jagTitle != null) {
			this.loadTitleBackground();
			this.loadTitleImages();
		}
		this.redrawFrame = true;
	}

	@ObfuscatedName("client.r(Z)V")
	public void loadTitleBackground() {
		byte[] var2 = this.jagTitle.read("title.dat", null);
		Pix32 var3 = new Pix32(var2, this);
		this.imageTitle0.bind();
		var3.quickPlotSprite(0, 0);
		this.imageTitle1.bind();
		var3.quickPlotSprite(0, -637);
		this.imageTitle2.bind();
		var3.quickPlotSprite(0, -128);
		this.imageTitle3.bind();
		var3.quickPlotSprite(-371, -202);
		this.imageTitle4.bind();
		var3.quickPlotSprite(-171, -202);
		this.imageTitle5.bind();
		var3.quickPlotSprite(-265, 0);
		this.imageTitle6.bind();
		var3.quickPlotSprite(-265, -562);
		this.imageTitle7.bind();
		var3.quickPlotSprite(-171, -128);
		this.imageTitle8.bind();
		var3.quickPlotSprite(-171, -562);
		int[] var4 = new int[var3.wi];
		for (int var5 = 0; var5 < var3.hi; var5++) {
			for (int var11 = 0; var11 < var3.wi; var11++) {
				var4[var11] = var3.pixels[var3.wi * var5 + (var3.wi - var11 - 1)];
			}
			for (int var12 = 0; var12 < var3.wi; var12++) {
				var3.pixels[var3.wi * var5 + var12] = var4[var12];
			}
		}
		this.imageTitle0.bind();
		var3.quickPlotSprite(0, 382);
		this.imageTitle1.bind();
		var3.quickPlotSprite(0, -255);
		this.imageTitle2.bind();
		var3.quickPlotSprite(0, 254);
		this.imageTitle3.bind();
		var3.quickPlotSprite(-371, 180);
		this.imageTitle4.bind();
		var3.quickPlotSprite(-171, 180);
		this.imageTitle5.bind();
		var3.quickPlotSprite(-265, 382);
		this.imageTitle6.bind();
		var3.quickPlotSprite(-265, -180);
		this.imageTitle7.bind();
		var3.quickPlotSprite(-171, 254);
		this.imageTitle8.bind();
		var3.quickPlotSprite(-171, -180);
		this.buildTitleBackdrop();
		Pix32 var7 = new Pix32(this.jagTitle, "logo", 0);
		this.imageTitle2.bind();
		var7.plotSprite(18, 382 - var7.wi / 2 - 128);
		Object var8 = null;
		Object var9 = null;
		Object var10 = null;
		System.gc();
	}

	/**
	 * The title screen's BACKGROUND as one 765x503 picture - the hall, its pillars and its braziers,
	 * with no logo and no login box - for filling a resizable window around the centred title
	 * screen (see fillTitleSurround). Taken at this exact point of loadTitleBackground because it is
	 * the one moment all nine pieces hold nothing but the background: the logo goes into imageTitle2
	 * on the next line, and imageTitle4 is the middle of the picture until drawTitle() paints the
	 * login box into it a frame later. A backdrop with either of those in it would show a second
	 * logo, or a second login box, behind the real one.
	 */
	private void buildTitleBackdrop() {
		if (this.imageTitle0 == null) {
			return;
		}
		PixMap backdrop = new PixMap(Layout.FIXED_H, this.getBaseComponent(), Layout.FIXED_W);
		PixMap[] pieces = { this.imageTitle0, this.imageTitle1, this.imageTitle2, this.imageTitle7, this.imageTitle4,
			this.imageTitle8, this.imageTitle3, this.imageTitle5, this.imageTitle6 };
		int[] px = { 0, 637, 128, 128, 202, 562, 202, 0, 562 };
		int[] py = { 0, 0, 0, 171, 171, 171, 371, 265, 265 };
		for (int i = 0; i < pieces.length; i++) {
			PixMap piece = pieces[i];
			if (piece != null) {
				copyRect(piece.data, piece.width, 0, 0, piece.width, piece.height, backdrop.data, Layout.FIXED_W, Layout.FIXED_H, px[i], py[i]);
			}
		}
		backdrop.setPixels();
		this.titleBlur = this.blurredBackdrop(backdrop);
		this.imageTitle2.bind();
		// the loading screen has already filled the window - with black, because this did not exist
		// yet when it did - so ask for it to be filled again now that it does
		this.letterCleared = false;
	}

	@ObfuscatedName("client.i(Z)V")
	public void loadTitleImages() {
		this.imageTitlebox = new Pix8(this.jagTitle, "titlebox", 0);
		this.imageTitlebutton = new Pix8(this.jagTitle, "titlebutton", 0);
		this.imageRunes = new Pix8[12];
		for (int var2 = 0; var2 < 12; var2++) {
			this.imageRunes[var2] = new Pix8(this.jagTitle, "runes", var2);
		}
		this.imageFlamesLeft = new Pix32(128, 265);
		this.imageFlamesRight = new Pix32(128, 265);
		for (int var3 = 0; var3 < 33920; var3++) {
			this.imageFlamesLeft.pixels[var3] = this.imageTitle0.data[var3];
		}
		for (int var4 = 0; var4 < 33920; var4++) {
			this.imageFlamesRight.pixels[var4] = this.imageTitle1.data[var4];
		}
		this.flameGradient0 = new int[256];
		for (int var5 = 0; var5 < 64; var5++) {
			this.flameGradient0[var5] = var5 * 262144;
		}
		for (int var6 = 0; var6 < 64; var6++) {
			this.flameGradient0[var6 + 64] = var6 * 1024 + 16711680;
		}
		for (int var7 = 0; var7 < 64; var7++) {
			this.flameGradient0[var7 + 128] = var7 * 4 + 16776960;
		}
		for (int var8 = 0; var8 < 64; var8++) {
			this.flameGradient0[var8 + 192] = 16777215;
		}
		this.flameGradient1 = new int[256];
		for (int var9 = 0; var9 < 64; var9++) {
			this.flameGradient1[var9] = var9 * 1024;
		}
		for (int var10 = 0; var10 < 64; var10++) {
			this.flameGradient1[var10 + 64] = var10 * 4 + 65280;
		}
		for (int var11 = 0; var11 < 64; var11++) {
			this.flameGradient1[var11 + 128] = var11 * 262144 + 65535;
		}
		for (int var12 = 0; var12 < 64; var12++) {
			this.flameGradient1[var12 + 192] = 16777215;
		}
		this.flameGradient2 = new int[256];
		for (int var13 = 0; var13 < 64; var13++) {
			this.flameGradient2[var13] = var13 * 4;
		}
		for (int var14 = 0; var14 < 64; var14++) {
			this.flameGradient2[var14 + 64] = var14 * 262144 + 255;
		}
		for (int var15 = 0; var15 < 64; var15++) {
			this.flameGradient2[var15 + 128] = var15 * 1024 + 16711935;
		}
		for (int var16 = 0; var16 < 64; var16++) {
			this.flameGradient2[var16 + 192] = 16777215;
		}
		this.flameGradient = new int[256];
		this.flameBuffer0 = new int[32768];
		this.flameBuffer1 = new int[32768];
		this.updateFlameBuffer(null);
		this.flameBuffer2 = new int[32768];
		this.flameBuffer3 = new int[32768];
		this.drawProgress(10, "Connecting to fileserver");
		if (!this.flameActive) {
			this.flameActive0 = true;
			this.flameActive = true;
			this.startThread(this, 2);
		}
	}

	@ObfuscatedName("client.a(BZ)V")
	public void drawTitle() {
		this.loadTitle();
		this.imageTitle4.bind();
		this.imageTitlebox.plotSprite(0, 0);
		short var3 = 360;
		short var4 = 200;
		if (this.titleScreenState == 0) {
			int var6 = var4 / 2 + 80;
			this.fontPlain11.centreStringTag(true, 7711145, var6, var3 / 2, this.onDemand.message);
			int var7 = var4 / 2 - 28;
			this.fontBold12.centreStringTag(true, 16776960, var7, var3 / 2, "Welcome to " + SERVER_NAME);
			this.fontPlain12.centreStringTag(true, SLOGAN_COLOUR, var7 + 16, var3 / 2, SLOGAN);
			int var18 = var7 + 30;
			int var8 = var3 / 2 - 80;
			int var9 = var4 / 2 + 20;
			this.imageTitlebutton.plotSprite(var9 - 20, var8 - 73);
			this.fontBold12.centreStringTag(true, 16777215, var9 + 5, var8, "New User");
			int var10 = var3 / 2 + 80;
			this.imageTitlebutton.plotSprite(var9 - 20, var10 - 73);
			this.fontBold12.centreStringTag(true, 16777215, var9 + 5, var10, "Existing User");
		}
		if (this.titleScreenState == 2) {
			int var11 = var4 / 2 - 40;
			if (this.loginMessage0.length() > 0) {
				this.fontBold12.centreStringTag(true, 16776960, var11 - 15, var3 / 2, this.loginMessage0);
				this.fontBold12.centreStringTag(true, 16776960, var11, var3 / 2, this.loginMessage1);
				var11 += 30;
			} else {
				this.fontBold12.centreStringTag(true, 16776960, var11 - 7, var3 / 2, this.loginMessage1);
				var11 += 30;
			}
			this.fontBold12.drawStringTag(16777215, var3 / 2 - 90, var11, true, "Username: " + this.username + (this.titleLoginField == 0 & loopCycle % 40 < 20 ? "@yel@|" : ""));
			var11 += 15;
			this.fontBold12.drawStringTag(16777215, var3 / 2 - 88, var11, true, "Password: " + JString.censor(this.password) + (this.titleLoginField == 1 & loopCycle % 40 < 20 ? "@yel@|" : ""));
			var11 += 15;
			int var12 = var3 / 2 - 80;
			int var13 = var4 / 2 + 50;
			this.imageTitlebutton.plotSprite(var13 - 20, var12 - 73);
			this.fontBold12.centreStringTag(true, 16777215, var13 + 5, var12, "Login");
			int var14 = var3 / 2 + 80;
			this.imageTitlebutton.plotSprite(var13 - 20, var14 - 73);
			this.fontBold12.centreStringTag(true, 16777215, var13 + 5, var14, "Cancel");
		}
		if (this.titleScreenState == 3) {
			this.fontBold12.centreStringTag(true, 16776960, var4 / 2 - 60, var3 / 2, "Create a free account");
			int var15 = var4 / 2 - 35;
			this.fontBold12.centreStringTag(true, 16777215, var15, var3 / 2, "To create a new account you need to");
			int var19 = var15 + 15;
			this.fontBold12.centreStringTag(true, 16777215, var19, var3 / 2, "go back to the main " + SERVER_NAME + " webpage");
			int var20 = var19 + 15;
			this.fontBold12.centreStringTag(true, 16777215, var20, var3 / 2, "and choose the 'create account'");
			int var21 = var20 + 15;
			this.fontBold12.centreStringTag(true, 16777215, var21, var3 / 2, "button near the top of that page.");
			int var22 = var21 + 15;
			int var16 = var3 / 2;
			int var17 = var4 / 2 + 50;
			this.imageTitlebutton.plotSprite(var17 - 20, var16 - 73);
			this.fontBold12.centreStringTag(true, 16777215, var17 + 5, var16, "Cancel");
		}
		this.imageTitle4.draw(171, 202, super.graphics);
		if (this.redrawFrame) {
			this.redrawFrame = false;
			this.imageTitle2.draw(0, 128, super.graphics);
			this.imageTitle3.draw(371, 202, super.graphics);
			this.imageTitle5.draw(265, 0, super.graphics);
			this.imageTitle6.draw(265, 562, super.graphics);
			this.imageTitle7.draw(171, 128, super.graphics);
			this.imageTitle8.draw(171, 562, super.graphics);
		}
	}

	@ObfuscatedName("client.p(I)V")
	public void drawGame() {
		if (this.fullscreenInterfaceId0 != -1 && (this.sceneState == 2 || super.drawArea != null)) {
			if (this.sceneState == 2) {
				this.updateInterfaceAnimation(this.sceneDelta, this.fullscreenInterfaceId0);
				if (this.fullscreenInterfaceId1 != -1) {
					this.updateInterfaceAnimation(this.sceneDelta, this.fullscreenInterfaceId1);
				}
				this.sceneDelta = 0;
				this.prepareFullGame();
				super.drawArea.bind();
				Pix3D.lineOffset = this.areaFullscreenOffset;
				Pix2D.cls();
				this.redrawFrame = true;
				Component var2 = Component.get(this.fullscreenInterfaceId0);
				if (var2.width == 512 && var2.height == 334 && var2.type == 0) {
					var2.width = 765;
					var2.height = 503;
				}
				this.drawInterface(0, 0, var2, 0);
				if (this.fullscreenInterfaceId1 != -1) {
					Component var3 = Component.get(this.fullscreenInterfaceId1);
					if (var3.width == 512 && var3.height == 334 && var3.type == 0) {
						var3.width = 765;
						var3.height = 503;
					}
					this.drawInterface(0, 0, var3, 0);
				}
				if (this.menuVisible) {
					this.drawMenu();
				} else {
					this.handleInput();
					this.drawTooltip();
				}
			}
			super.drawArea.draw(0, 0, super.graphics);
			return;
		}

		if (this.redrawFrame) {
			this.prepareGame();
			this.redrawFrame = false;
			this.areaBackleft1.draw(4, 0, super.graphics);
			this.areaChatframe.draw(338, 0, super.graphics);
			this.areaBackright1.draw(4, 717, super.graphics);
			this.areaBackright2.draw(205, 737, super.graphics);
			this.areaBacktop1.draw(0, 0, super.graphics);
			this.areaBackvmid1.draw(4, 516, super.graphics);
			this.areaBackvmid2.draw(205, 516, super.graphics);
			this.areaBackvmid3.draw(338, 519, super.graphics);
			this.redrawSidebar = true;
			this.redrawChatback = true;
			this.redrawSideicons = true;
			this.redrawPrivacySettings = true;

			if (this.sceneState != 2) {
				this.areaViewport.draw(4, 4, super.graphics);
				this.areaMapback.draw(4, MAP_X, super.graphics);
			}

			field533++;
			if (field533 > 85) {
				field533 = 0;
				// ANTICHEAT_CYCLELOGIC2
				this.out.p1isaac(168);
			}
		}

		if (this.sceneState == 2) {
			this.drawScene();
		}

		if (this.menuVisible && this.menuArea == 1) {
			this.redrawSidebar = true;
		}
		if (this.sidebarInterfaceId != -1) {
			boolean var4 = this.updateInterfaceAnimation(this.sceneDelta, this.sidebarInterfaceId);
			if (var4) {
				this.redrawSidebar = true;
			}
		}
		if (this.selectedArea == 2) {
			this.redrawSidebar = true;
		}
		if (this.objDragArea == 2) {
			this.redrawSidebar = true;
		}
		if (this.redrawSidebar) {
			this.drawSidebar();
			this.redrawSidebar = false;
		}
		if (this.chatInterfaceId == -1 && this.chatbackInputOpen == 0) {
			this.chatInterface.field713 = this.chatScrollHeight - this.chatScrollOffset - CHAT_LOG_H;
			if (super.mouseX > CHAT_X + CHAT_W - 48 && super.mouseX < CHAT_X + CHAT_W + 64 && super.mouseY > CHAT_Y - 25) {
				this.handleScrollInput(this.chatScrollHeight, 0, this.chatInterface, super.mouseY - CHAT_Y, -1, super.mouseX - CHAT_X, CHAT_LOG_H, CHAT_W - 16);
			}
			int var5 = this.chatScrollHeight - CHAT_LOG_H - this.chatInterface.field713;
			if (var5 < 0) {
				var5 = 0;
			}
			if (var5 > this.chatScrollHeight - 77) {
				var5 = this.chatScrollHeight - 77;
			}
			// QoL: mouse wheel scrolls the chat history when hovering over it (Corey, 2026-09-04:
			// widened from the narrow scrollbar-column rect to the full chatbox rect - same bounds
			// handleInput() uses to route hover input to the chatbox, see ~line 4098 - so scrolling
			// works anywhere over the chat text, not just right over the scrollbar)
			if (QolSettings.on(QolSettings.WHEEL_CHAT) && super.mouseScrollDelta != 0 && super.mouseX > CHAT_X && super.mouseX < CHAT_X + CHAT_W && super.mouseY > CHAT_Y && super.mouseY < CHAT_Y + CHAT_H) {
				var5 -= super.mouseScrollDelta * 16;
				if (var5 < 0) {
					var5 = 0;
				}
				if (var5 > this.chatScrollHeight - 77) {
					var5 = this.chatScrollHeight - 77;
				}
				super.mouseScrollDelta = 0;
			}
			if (this.chatScrollOffset != var5) {
				this.chatScrollOffset = var5;
				this.redrawChatback = true;
			}
		}
		if (this.chatInterfaceId == -1 && this.chatbackInputOpen == 3) {
			int var6 = this.field158 * 14 + 7;
			this.chatInterface.field713 = this.field161;
			if (super.mouseX > CHAT_X + CHAT_W - 48 && super.mouseX < CHAT_X + CHAT_W + 64 && super.mouseY > CHAT_Y - 25) {
				this.handleScrollInput(var6, 0, this.chatInterface, super.mouseY - CHAT_Y, -1, super.mouseX - CHAT_X, CHAT_LOG_H, CHAT_W - 16);
			}
			int var7 = this.chatInterface.field713;
			if (var7 < 0) {
				var7 = 0;
			}
			if (var7 > var6 - CHAT_LOG_H) {
				var7 = var6 - CHAT_LOG_H;
			}
			if (this.field161 != var7) {
				this.field161 = var7;
				this.redrawChatback = true;
			}
		}
		if (this.chatInterfaceId != -1) {
			boolean var8 = this.updateInterfaceAnimation(this.sceneDelta, this.chatInterfaceId);
			if (var8) {
				this.redrawChatback = true;
			}
		}
		if (this.selectedArea == 3) {
			this.redrawChatback = true;
		}
		if (this.objDragArea == 3) {
			this.redrawChatback = true;
		}
		if (this.modalMessage != null) {
			this.redrawChatback = true;
		}
		if (this.menuVisible && this.menuArea == 2) {
			this.redrawChatback = true;
		}
		if (this.redrawChatback) {
			this.drawChat();
			this.redrawChatback = false;
		}
		if (this.sceneState == 2) {
			this.drawMinimap();
			this.areaMapback.draw(4, MAP_X, super.graphics);
		}
		if (this.flashingTab != -1) {
			this.redrawSideicons = true;
		}
		if (this.redrawSideicons) {
			if (this.flashingTab != -1 && this.selectedTab == this.flashingTab) {
				this.flashingTab = -1;
				// TUTORIAL_CLICKSIDE
				this.out.p1isaac(119);
				this.out.p1(this.selectedTab);
			}
			this.redrawSideicons = false;
			// Both tab rows are drawn the way interface 548 lays them out: a stone for the selected tab
			// (the end tabs have their own corner stones), then the fourteen icons over the top. The
			// top row's area starts 8px above its tabs because backhmid1 carries the strip under the
			// minimap as well.
			this.areaBackmid1.bind();
			this.imageBackhmid1.plotSprite(0, 0);
			if (this.sidebarInterfaceId == -1) {
				if (this.selectedTab < 7 && this.tabInterfaceId[this.selectedTab] != -1) {
					this.plotTabStone(this.selectedTab, 8, 0);
				}
				for (int tab = 0; tab < 7; tab++) {
					if (this.tabInterfaceId[tab] != -1 && (this.flashingTab != tab || loopCycle % 20 < 10)) {
						Pix8 icon = this.imageSideicons[tab];
						if (tab == 6 && this.spellbookIcon >= 1 && this.spellbookIcon <= 2 && this.imageSideicons[14 + this.spellbookIcon] != null) {
							icon = this.imageSideicons[14 + this.spellbookIcon];
						}
						icon.plotSprite(8, TAB_ICON_X[tab]);
					}
				}
			}
			this.areaBackmid1.draw(160, 516, super.graphics);
			this.areaBackbase2.bind();
			this.imageBackbase2.plotSprite(0, 0);
			if (this.sidebarInterfaceId == -1) {
				if (this.selectedTab >= 7 && this.tabInterfaceId[this.selectedTab] != -1) {
					this.plotTabStone(this.selectedTab - 7, 0, 2);
				}
				for (int tab = 7; tab < 14; tab++) {
					if (this.tabInterfaceId[tab] != -1 && (this.flashingTab != tab || loopCycle % 20 < 10)) {
						this.imageSideicons[tab].plotSprite(0, TAB_ICON_X[tab - 7] - 3);
					} else if (tab == 7) {
						// Clan chat: the server has none yet, so its tab shows, greyed, with nothing behind it.
						this.imageSideicons[14].plotSprite(0, TAB_ICON_X[0] - 3);
					}
				}
			}
			this.areaBackbase2.draw(466, 519, super.graphics);
			this.areaViewport.bind();
			Pix3D.lineOffset = this.areaViewportOffset;
		}

		int barHover = this.chatBarButtonAt(super.mouseX, super.mouseY);
		if (barHover != this.chatBarHover) {
			this.chatBarHover = barHover;
			this.redrawPrivacySettings = true;
		}
		if (this.redrawPrivacySettings) {
			this.redrawPrivacySettings = false;

			this.areaBackbase1.bind();
			this.imageBackbase1.plotSprite(0, 0);

			int[] modes = { -1, -1, this.chatPublicMode, this.chatPrivateMode, this.chatClanMode, this.chatTradeMode };
			for (int i = 0; i < 6; i++) {
				int x = 5 + i * BAR_PITCH;
				int state = (i == this.chatFilter ? 2 : 0) + (i == this.chatBarHover ? 1 : 0);
				this.imageChatbuttons[state].plotSprite(1, x);
				this.drawChatBarLabel(x, BAR_BUTTON_W, BAR_LABELS[i], modes[i] == -1 ? null : chatModeName(modes[i]), modes[i] == -1 ? 0 : chatModeColour(modes[i]));
			}
			this.imageReportbutton[this.chatBarHover == 6 ? 1 : 0].plotSprite(1, BAR_REPORT_X);
			this.drawChatBarLabel(BAR_REPORT_X, BAR_REPORT_W, "Report abuse", null, 0);

			this.areaBackbase1.draw(BAR_Y, 0, super.graphics);

			this.areaViewport.bind();
			Pix3D.lineOffset = this.areaViewportOffset;
		}
		this.sceneDelta = 0;
		if (this.layout.resizable) {
			this.presentGame(true);
		}
	}

	@ObfuscatedName("client.L(I)V")
	public void drawScene() {
		this.sceneCycle++;
		this.pushNpcs(true);
		this.pushPlayers(true);
		this.pushNpcs(false);
		this.pushPlayers(false);
		this.pushProjectiles();
		this.pushSpotanims();
		if (!this.cutscene) {
			int var2 = this.orbitCameraPitch;
			if (this.cameraPitchClamp / 256 > var2) {
				var2 = this.cameraPitchClamp / 256;
			}
			if (this.cameraModifierEnabled[4] && this.cameraModifierWobbleScale[4] + 128 > var2) {
				var2 = this.cameraModifierWobbleScale[4] + 128;
			}
			int var3 = this.macroCameraAngle + this.orbitCameraYaw & 0x7FF;

			// QoL: scroll wheel zoom folded into the normal pitch-based camera distance.
			int var3zoom = var2 * 3 + 600 - this.cameraZoomOffset;
			if (var3zoom < 200) {
				var3zoom = 200;
			}
			if (var3zoom > 3000) {
				var3zoom = 3000;
			}

			this.orbitCamera(this.getHeightmapY(localPlayer.field1158, localPlayer.field1157, this.currentLevel) - 50, this.orbitCameraX, var2, var3zoom, var3, this.orbitCameraZ);
		}
		int var4;
		if (this.cutscene) {
			var4 = this.getTopLevelCutscene();
		} else {
			var4 = this.getTopLevel();
		}
		int var5 = this.cameraX;
		int var6 = this.cameraY;
		int var7 = this.cameraZ;
		int var8 = this.cameraPitch;
		int var9 = this.cameraYaw;
		for (int var10 = 0; var10 < 5; var10++) {
			if (this.cameraModifierEnabled[var10]) {
				int var12 = (int) (Math.random() * (double) (this.cameraModifierJitter[var10] * 2 + 1) - (double) this.cameraModifierJitter[var10] + Math.sin((double) this.cameraModifierWobbleSpeed[var10] / 100.0D * (double) this.cameraModifierCycle[var10]) * (double) this.cameraModifierWobbleScale[var10]);
				if (var10 == 0) {
					this.cameraX += var12;
				}
				if (var10 == 1) {
					this.cameraY += var12;
				}
				if (var10 == 2) {
					this.cameraZ += var12;
				}
				if (var10 == 3) {
					this.cameraYaw = this.cameraYaw + var12 & 0x7FF;
				}
				if (var10 == 4) {
					this.cameraPitch += var12;
					if (this.cameraPitch < 128) {
						this.cameraPitch = 128;
					}
					if (this.cameraPitch > 383) {
						this.cameraPitch = 383;
					}
				}
			}
		}
		int var11 = Pix3D.cycle;
		Model.checkHover = true;
		Model.pickedCount = 0;
		Model.mouseX = super.mouseX - this.layout.vpX;
		Model.mouseY = super.mouseY - this.layout.vpY;
		Pix2D.cls();
		// The scene and what is drawn over it at a projected point (names, hitsplats, ground items)
		// at the layout's projection. Everything after - interfaces, whose models are drawn at 377's
		// fixed scale - at 512.
		Pix3D.zoom = this.layout.zoom;
		this.scene.draw(this.cameraX, var4, this.cameraY, this.cameraZ, this.cameraYaw, this.cameraPitch);
		this.scene.clearLocChanges();
		// Plugin overlays on the scene layer - ground item names among them. Here and not in
		// draw3DEntityElements() so they sit under player names, hitsplats and headicons rather
		// than over them. First of the two overlay passes in a frame.
		if (this.plugins != null) {
			this.plugins.renderOverlays(this.layout.openW, this.layout.openH,
				jagex2.client.plugin.Overlay.LAYER_SCENE);
		}
		this.draw2DEntityElements();
		this.drawTileHint();
		this.updateTextures(var11);
		Pix3D.zoom = 512;
		this.draw3DEntityElements();
		// In resizable mode the viewport is put on the screen once, at the end of drawGame(), with
		// the panels and an open menu laid over it.
		if (!this.layout.resizable) {
			this.areaViewport.draw(4, 4, super.graphics);
		}
		this.cameraX = var5;
		this.cameraY = var6;
		this.cameraZ = var7;
		this.cameraPitch = var8;
		this.cameraYaw = var9;
	}

	@ObfuscatedName("client.a(IZ)V")
	/** How many npcs have been dropped for sitting outside the 104x104 scene. */
	private static int offScene = 0;





	public void pushPlayers(boolean arg1) {
		for (int var4 = 0; var4 < this.npcCount; var4++) {
			ClientNpc var5 = this.npcs[this.npcIds[var4]];
			int var6 = (this.npcIds[var4] << 14) + 536870912;
			if (var5 != null && var5.method351() && var5.field1370.field1447 == arg1 && var5.field1370.method473()) {
				int var7 = var5.field1157 >> 7;
				int var8 = var5.field1158 >> 7;
				// THE NPC EXISTS BUT IS NOT PUT IN THE SCENE, which is the last way one can be absent
				// that nothing reported. The server sent it, the client has it in npcs[], and then it
				// is dropped here for being outside the 104x104 the scene covers - so no model is ever
				// asked for and the npcmodel and animframe counters stay silent while the thing is
				// plainly gone. That is the shape of what the owner describes: away for a tick, back
				// the next, with every other instrument quiet.
				if (!(var7 >= 0 && var7 < 104 && var8 >= 0 && var8 < 104)) {
					offScene++;
					if (offScene <= 12 || offScene % 200 == 0) {
						DevLog.log("offscene", "npc #" + offScene + " not in scene: tile " + var7 + "," + var8
							+ " (scene is 0..103)  key=" + var5.field1370.field1431 + " size=" + var5.field1148);
					}
				}
				if (var7 >= 0 && var7 < 104 && var8 >= 0 && var8 < 104) {
					if (var5.field1148 == 1 && (var5.field1157 & 0x7F) == 64 && (var5.field1158 & 0x7F) == 64) {
						if (this.tileLastOccupiedCycle[var7][var8] == this.sceneCycle) {
							continue;
						}
						this.tileLastOccupiedCycle[var7][var8] = this.sceneCycle;
					}
					if (!var5.field1370.field1434) {
						var6 += Integer.MIN_VALUE;
					}
					this.scene.method285(var6, var5, var5.field1157, this.getHeightmapY(var5.field1158, var5.field1157, this.currentLevel), var5.field1139, 0, this.currentLevel, (var5.field1148 - 1) * 64 + 60, var5.field1158, var5.field1159);
				}
			}
		}
	}

	@ObfuscatedName("client.b(IZ)V")
	public void pushNpcs(boolean arg1) {
		if (localPlayer.field1157 >> 7 == this.flagSceneTileX && localPlayer.field1158 >> 7 == this.flagSceneTileZ) {
			this.flagSceneTileX = 0;
		}
		int var3 = this.playerCount;
		if (arg1) {
			var3 = 1;
		}
		for (int var4 = 0; var4 < var3; var4++) {
			ClientPlayer var5;
			int var6;
			if (arg1) {
				var5 = localPlayer;
				var6 = this.LOCAL_PLAYER_INDEX << 14;
			} else {
				var5 = this.players[this.playerIds[var4]];
				var6 = this.playerIds[var4] << 14;
			}
			if (var5 != null && var5.method351()) {
				var5.field1685 = false;
				if ((lowMem && this.playerCount > 50 || this.playerCount > 200) && !arg1 && var5.field1181 == var5.field1135) {
					var5.field1685 = true;
				}
				int var7 = var5.field1157 >> 7;
				int var8 = var5.field1158 >> 7;
				if (var7 >= 0 && var7 < 104 && var8 >= 0 && var8 < 104) {
					if (var5.field1668 == null || loopCycle < var5.field1686 || loopCycle >= var5.field1687) {
						if ((var5.field1157 & 0x7F) == 64 && (var5.field1158 & 0x7F) == 64) {
							if (this.tileLastOccupiedCycle[var7][var8] == this.sceneCycle) {
								continue;
							}
							this.tileLastOccupiedCycle[var7][var8] = this.sceneCycle;
						}
						var5.field1672 = this.getHeightmapY(var5.field1158, var5.field1157, this.currentLevel);
						this.scene.method285(var6, var5, var5.field1157, var5.field1672, var5.field1139, 0, this.currentLevel, 60, var5.field1158, var5.field1159);
					} else {
						var5.field1685 = false;
						var5.field1672 = this.getHeightmapY(var5.field1158, var5.field1157, this.currentLevel);
						this.scene.method286(var5.field1672, var5.field1691, 60, var5, var5.field1690, var5.field1158, var5.field1693, var5.field1157, var5.field1159, var5.field1692, this.currentLevel, var6);
					}
				}
			}
		}
	}

	@ObfuscatedName("client.h(Z)V")
	public void pushProjectiles() {
		ClientProj var2 = (ClientProj) this.projectiles.head();
		while (var2 != null) {
			if (this.currentLevel != var2.field975 || loopCycle > var2.field987) {
				var2.unlink();
			} else if (loopCycle >= var2.field986) {
				if (var2.field981 > 0) {
					ClientNpc var3 = this.npcs[var2.field981 - 1];
					if (var3 != null && var3.field1157 >= 0 && var3.field1157 < 13312 && var3.field1158 >= 0 && var3.field1158 < 13312) {
						var2.method271(var3.field1157, var3.field1158, this.getHeightmapY(var3.field1158, var3.field1157, var2.field975) - var2.field1000, loopCycle);
					}
				}
				if (var2.field981 < 0) {
					int var4 = -var2.field981 - 1;
					ClientPlayer var5;
					if (this.localPid == var4) {
						var5 = localPlayer;
					} else {
						var5 = this.players[var4];
					}
					if (var5 != null && var5.field1157 >= 0 && var5.field1157 < 13312 && var5.field1158 >= 0 && var5.field1158 < 13312) {
						var2.method271(var5.field1157, var5.field1158, this.getHeightmapY(var5.field1158, var5.field1157, var2.field975) - var2.field1000, loopCycle);
					}
				}
				var2.method272(this.sceneDelta);
				// A projectile belongs to the one tile its centre is over (padding 0), not every tile within
				// 60 units of it as 377 had. A dart leaving a player who stands against a wall sits within 60
				// of the tile edge, so it spanned the wall's tile as well, and a sprite spanning two tiles
				// makes the scene hold back the walls of both until it is drawn - which reordered them: the
				// wall BEHIND an open door came out after the door and painted over it, so the door seemed
				// to vanish for the first ticks of every toxic blowpipe shot (reported with a video,
				// 2026-09-27). Any projectile did it near a wall; the blowpipe fires from beside one four
				// times as often. With a one-tile span the scene's order is exactly what it is with no
				// projectile at all (tools/clienttests/run_projectilespantest.py: 194k frames, no wall moved,
				// against 30k that moved at 60). A projectile is small; the cost is at most a sliver of dart
				// overdrawn by a wall on the next tile for a frame.
				this.scene.method285(-1, var2, (int) var2.field976, (int) var2.field978, false, 0, this.currentLevel, 0, (int) var2.field977, var2.field983);
			}
			var2 = (ClientProj) this.projectiles.next();
		}
		field464++;
		if (field464 > 51) {
			field464 = 0;
			// ANTICHEAT_CYCLELOGIC5
			this.out.p1isaac(248);
		}
	}

	@ObfuscatedName("client.r(I)V")
	public void pushSpotanims() {
		for (MapSpotAnim var2 = (MapSpotAnim) this.spotanims.head(); var2 != null; var2 = (MapSpotAnim) this.spotanims.next()) {
			if (this.currentLevel != var2.field1522 || var2.field1527) {
				var2.unlink();
			} else if (loopCycle >= var2.field1531) {
				var2.method486((byte) 1, this.sceneDelta);
				if (var2.field1527) {
					var2.unlink();
				} else {
					this.scene.method285(-1, var2, var2.field1523, var2.field1525, false, 0, var2.field1522, 60, var2.field1524, 0);
				}
			}
		}
	}

	@ObfuscatedName("client.a(IIIIIIB)V")
	public void orbitCamera(int arg0, int arg1, int arg2, int arg3, int arg4, int arg5) {
		int var8 = 2048 - arg2 & 0x7FF;
		int var9 = 2048 - arg4 & 0x7FF;
		int var10 = 0;
		int var11 = 0;
		int var12 = arg3;
		if (var8 != 0) {
			int var13 = Model.sinTable[var8];
			int var14 = Model.cosTable[var8];
			int var15 = var11 * var14 - arg3 * var13 >> 16;
			var12 = var11 * var13 + arg3 * var14 >> 16;
			var11 = var15;
		}
		if (var9 != 0) {
			int var16 = Model.sinTable[var9];
			int var17 = Model.cosTable[var9];
			int var18 = var10 * var17 + var12 * var16 >> 16;
			var12 = var12 * var17 - var10 * var16 >> 16;
			var10 = var18;
		}
		this.cameraX = arg1 - var10;
		this.cameraY = arg0 - var11;
		this.cameraZ = arg5 - var12;
		this.cameraPitch = arg2;
		this.cameraYaw = arg4;
	}

	@ObfuscatedName("client.E(I)I")
	public int getTopLevelCutscene() {
		int var2 = this.getHeightmapY(this.cameraZ, this.cameraX, this.currentLevel);
		return var2 - this.cameraY >= 800 || (this.levelTileFlags[this.currentLevel][this.cameraX >> 7][this.cameraZ >> 7] & 0x4) == 0 ? 3 : this.currentLevel;
	}

	@ObfuscatedName("client.j(B)I")
	public int getTopLevel() {
		int var2 = 3;
		if (this.cameraPitch < 310) {
			cyclelogic1++;
			if (cyclelogic1 > 1457) {
				cyclelogic1 = 0;
				// ANTICHEAT_CYCLELOGIC1
				this.out.p1isaac(244);
				this.out.p1(0);
				int var4 = this.out.pos;
				this.out.p1(219);
				this.out.p2(37745);
				this.out.p1(61);
				this.out.p2(43756);
				this.out.p2((int) (Math.random() * 65536.0D));
				this.out.p1((int) (Math.random() * 256.0D));
				this.out.p2(51171);
				if ((int) (Math.random() * 2.0D) == 0) {
					this.out.p2(15808);
				}
				this.out.p1(97);
				this.out.p1((int) (Math.random() * 256.0D));
				this.out.psize1(this.out.pos - var4);
			}
			int var5 = this.cameraX >> 7;
			int var6 = this.cameraZ >> 7;
			int var7 = localPlayer.field1157 >> 7;
			int var8 = localPlayer.field1158 >> 7;
			if ((this.levelTileFlags[this.currentLevel][var5][var6] & 0x4) != 0) {
				var2 = this.currentLevel;
			}
			int var9;
			if (var7 > var5) {
				var9 = var7 - var5;
			} else {
				var9 = var5 - var7;
			}
			int var10;
			if (var8 > var6) {
				var10 = var8 - var6;
			} else {
				var10 = var6 - var8;
			}
			if (var9 > var10) {
				int var11 = var10 * 65536 / var9;
				int var12 = 32768;
				while (var5 != var7) {
					if (var5 < var7) {
						var5++;
					} else if (var5 > var7) {
						var5--;
					}
					if ((this.levelTileFlags[this.currentLevel][var5][var6] & 0x4) != 0) {
						var2 = this.currentLevel;
					}
					var12 += var11;
					if (var12 >= 65536) {
						var12 -= 65536;
						if (var6 < var8) {
							var6++;
						} else if (var6 > var8) {
							var6--;
						}
						if ((this.levelTileFlags[this.currentLevel][var5][var6] & 0x4) != 0) {
							var2 = this.currentLevel;
						}
					}
				}
			// var10 != 0 is a GUARD, not a condition: reaching here means var9 <= var10, so var10
			// being zero means both are, the camera is on the player's own tile, there is no line
			// between them to walk - and the division below would be by zero. The vanilla client
			// divides anyway and throws out of drawScene. It has never fired because the orbit
			// camera keeps its distance, but the wheel-zoom QoL feature can pull that distance to
			// 200 units and a steep pitch makes 200 units less than one tile. Found by a test that
			// put the camera and the player on the same tile because that was the simplest fixture
			// to write.
			} else if (var10 != 0) {
				int var13 = var9 * 65536 / var10;
				int var14 = 32768;
				while (var6 != var8) {
					if (var6 < var8) {
						var6++;
					} else if (var6 > var8) {
						var6--;
					}
					if ((this.levelTileFlags[this.currentLevel][var5][var6] & 0x4) != 0) {
						var2 = this.currentLevel;
					}
					var14 += var13;
					if (var14 >= 65536) {
						var14 -= 65536;
						if (var5 < var7) {
							var5++;
						} else if (var5 > var7) {
							var5--;
						}
						if ((this.levelTileFlags[this.currentLevel][var5][var6] & 0x4) != 0) {
							var2 = this.currentLevel;
						}
					}
				}
			}
		}
		if ((this.levelTileFlags[this.currentLevel][localPlayer.field1157 >> 7][localPlayer.field1158 >> 7] & 0x4) != 0) {
			var2 = this.currentLevel;
		}
		// QoL: hide roofs. Everything above the player's own level IS the roof, and the tile tests
		// in this method decide case by case whether to draw it - hidden when the player stands
		// under one, or when the line from the camera to the player passes under one, and drawn
		// otherwise. With the setting on the answer is always "hide", which is what Old School's
		// Roofs toggle does.
		//
		// DONE AT THE ONE RETURN rather than as an early exit, for two reasons. The ANTICHEAT_
		// CYCLELOGIC1 block above sends a packet on its own schedule and must keep running at that
		// rate whatever the player has chosen to look at; and the pitch test the method opens with
		// leaves var2 at 3 when the camera is looking steeply down, so an early exit inside it
		// would draw roofs again the moment you tilted the camera - the bug this is not.
		if (QolSettings.on(QolSettings.ROOFS_OFF)) {
			return this.currentLevel;
		}
		return var2;
	}

	@ObfuscatedName("client.m(Z)V")
	public void draw2DEntityElements() {
		this.chatCount = 0;
		for (int var2 = -1; var2 < this.npcCount + this.playerCount; var2++) {
			ClientEntity var19;
			if (var2 == -1) {
				var19 = localPlayer;
			} else if (var2 < this.playerCount) {
				var19 = this.players[this.playerIds[var2]];
			} else {
				var19 = this.npcs[this.npcIds[var2 - this.playerCount]];
			}
			if (var19 != null && var19.method351()) {
				if (var19 instanceof ClientNpc) {
					NpcType var20 = ((ClientNpc) var19).field1370;
					if (var20.field1425 != null) {
						var20 = var20.method476();
					}
					if (var20 == null) {
						continue;
					}
				}
				if (var2 >= this.playerCount) {
					NpcType var23 = ((ClientNpc) var19).field1370;
					if (var23.field1441 >= 0 && var23.field1441 < this.imageHeadiconsPrayer.length) {
						this.projectFromEntity(var19, var19.field1141 + 15);
						if (this.projectX > -1) {
							this.imageHeadiconsPrayer[var23.field1441].plotSprite(this.projectY - 30, this.projectX - 12);
						}
					}
					if (this.hintType == 1 && this.npcIds[var2 - this.playerCount] == this.hintNpc && loopCycle % 20 < 10) {
						this.projectFromEntity(var19, var19.field1141 + 15);
						if (this.projectX > -1) {
							this.imageHeadiconsHint[0].plotSprite(this.projectY - 28, this.projectX - 12);
						}
					}
				} else {
					int var21 = 30;
					ClientPlayer var22 = (ClientPlayer) var19;
					if (var22.field1678 != -1 || var22.field1670 != -1) {
						this.projectFromEntity(var19, var19.field1141 + 15);
						if (this.projectX > -1) {
							if (var22.field1678 != -1) {
								this.imageHeadiconsPk[var22.field1678].plotSprite(this.projectY - var21, this.projectX - 12);
								var21 += 25;
							}
							if (var22.field1670 != -1) {
								this.imageHeadiconsPrayer[var22.field1670].plotSprite(this.projectY - var21, this.projectX - 12);
								var21 += 25;
							}
						}
					}
					if (var2 >= 0 && this.hintType == 10 && this.playerIds[var2] == this.hintPlayer) {
						this.projectFromEntity(var19, var19.field1141 + 15);
						if (this.projectX > -1) {
							this.imageHeadiconsHint[1].plotSprite(this.projectY - var21, this.projectX - 12);
						}
					}
				}
				if (var19.chatMessage != null && (var2 >= this.playerCount || this.chatPublicMode == 0 || this.chatPublicMode == 3 || this.chatPublicMode == 1 && this.isFriend(((ClientPlayer) var19).name))) {
					this.projectFromEntity(var19, var19.field1141);
					if (this.projectX > -1 && this.chatCount < this.MAX_CHATS) {
						this.chatWidth[this.chatCount] = this.fontBold12.stringWid(var19.chatMessage) / 2;
						this.chatHeight[this.chatCount] = this.fontBold12.height;
						this.chatX[this.chatCount] = this.projectX;
						this.chatY[this.chatCount] = this.projectY;
						this.chatColour[this.chatCount] = var19.chatColour;
						this.chatEffect[this.chatCount] = var19.chatEffect;
						this.chatTimer[this.chatCount] = var19.chatTimer;
						this.chatMessage[this.chatCount++] = var19.chatMessage;
						if (this.chatEffects == 0 && var19.chatEffect >= 1 && var19.chatEffect <= 3) {
							this.chatHeight[this.chatCount] += 10;
							this.chatY[this.chatCount] += 5;
						}
						if (this.chatEffects == 0 && var19.chatEffect == 4) {
							this.chatWidth[this.chatCount] = 60;
						}
						if (this.chatEffects == 0 && var19.chatEffect == 5) {
							this.chatHeight[this.chatCount] += 5;
						}
					}
				}
				if (var19.field1142 > loopCycle) {
					this.projectFromEntity(var19, var19.field1141 + 15);
					if (this.projectX > -1) {
						int var24 = var19.field1143 * 30 / var19.field1144;
						if (var24 > 30) {
							var24 = 30;
						}
						Pix2D.fillRect(5, this.projectY - 3, 65280, var24, this.projectX - 15);
						Pix2D.fillRect(5, this.projectY - 3, 16711680, 30 - var24, this.projectX - 15 + var24);
					}
				}
				for (int var25 = 0; var25 < 4; var25++) {
					if (var19.field1179[var25] > loopCycle) {
						this.projectFromEntity(var19, var19.field1141 / 2);
						if (this.projectX > -1) {
							if (var25 == 1) {
								this.projectY -= 20;
							}
							if (var25 == 2) {
								this.projectX -= 15;
								this.projectY -= 10;
							}
							if (var25 == 3) {
								this.projectX += 15;
								this.projectY -= 10;
							}
							int hitmark = var19.field1178[var25];
							if (hitmark < 0 || hitmark >= this.imageHitmarks.length || this.imageHitmarks[hitmark] == null) {
								hitmark = 1; // a type this media has no splat for draws as ordinary damage, not a crash
							}
							this.imageHitmarks[hitmark].plotSprite(this.projectY - 12, this.projectX - 12);
							this.fontPlain11.centreString(this.projectX, this.projectY + 4, 0, String.valueOf(var19.field1177[var25]));
							this.fontPlain11.centreString(this.projectX - 1, this.projectY + 3, 16777215, String.valueOf(var19.field1177[var25]));
						}
					}
				}
			}
		}
		for (int var3 = 0; var3 < this.chatCount; var3++) {
			int var4 = this.chatX[var3];
			int var5 = this.chatY[var3];
			int var6 = this.chatWidth[var3];
			int var7 = this.chatHeight[var3];
			boolean var8 = true;
			while (var8) {
				var8 = false;
				for (int var18 = 0; var18 < var3; var18++) {
					if (var5 + 2 > this.chatY[var18] - this.chatHeight[var18] && var5 - var7 < this.chatY[var18] + 2 && var4 - var6 < this.chatWidth[var18] + this.chatX[var18] && var4 + var6 > this.chatX[var18] - this.chatWidth[var18] && this.chatY[var18] - this.chatHeight[var18] < var5) {
						var5 = this.chatY[var18] - this.chatHeight[var18];
						var8 = true;
					}
				}
			}
			this.projectX = this.chatX[var3];
			this.projectY = this.chatY[var3] = var5;
			String var9 = this.chatMessage[var3];
			if (this.chatEffects == 0) {
				int var10 = 16776960;
				if (this.chatColour[var3] < 6) {
					var10 = this.CHAT_COLOURS[this.chatColour[var3]];
				}
				if (this.chatColour[var3] == 6) {
					var10 = this.sceneCycle % 20 < 10 ? 16711680 : 16776960;
				}
				if (this.chatColour[var3] == 7) {
					var10 = this.sceneCycle % 20 < 10 ? 255 : 65535;
				}
				if (this.chatColour[var3] == 8) {
					var10 = this.sceneCycle % 20 < 10 ? 45056 : 8454016;
				}
				if (this.chatColour[var3] == 9) {
					int var11 = 150 - this.chatTimer[var3];
					if (var11 < 50) {
						var10 = var11 * 1280 + 16711680;
					} else if (var11 < 100) {
						var10 = 16776960 - (var11 - 50) * 327680;
					} else if (var11 < 150) {
						var10 = (var11 - 100) * 5 + 65280;
					}
				}
				if (this.chatColour[var3] == 10) {
					int var12 = 150 - this.chatTimer[var3];
					if (var12 < 50) {
						var10 = var12 * 5 + 16711680;
					} else if (var12 < 100) {
						var10 = 16711935 - (var12 - 50) * 327680;
					} else if (var12 < 150) {
						var10 = (var12 - 100) * 327680 + 255 - (var12 - 100) * 5;
					}
				}
				if (this.chatColour[var3] == 11) {
					int var13 = 150 - this.chatTimer[var3];
					if (var13 < 50) {
						var10 = 16777215 - var13 * 327685;
					} else if (var13 < 100) {
						var10 = (var13 - 50) * 327685 + 65280;
					} else if (var13 < 150) {
						var10 = 16777215 - (var13 - 100) * 327680;
					}
				}
				if (this.chatEffect[var3] == 0) {
					this.fontBold12.centreString(this.projectX, this.projectY + 1, 0, var9);
					this.fontBold12.centreString(this.projectX, this.projectY, var10, var9);
				}
				if (this.chatEffect[var3] == 1) {
					this.fontBold12.centreStringWave(this.projectY + 1, this.sceneCycle, var9, this.projectX, 0);
					this.fontBold12.centreStringWave(this.projectY, this.sceneCycle, var9, this.projectX, var10);
				}
				if (this.chatEffect[var3] == 2) {
					this.fontBold12.centreStringWave2(this.projectY + 1, 0, var9, this.projectX, this.sceneCycle);
					this.fontBold12.centreStringWave2(this.projectY, var10, var9, this.projectX, this.sceneCycle);
				}
				if (this.chatEffect[var3] == 3) {
					this.fontBold12.centreStringShake(var9, 0, this.projectX, this.projectY + 1, 150 - this.chatTimer[var3], this.sceneCycle);
					this.fontBold12.centreStringShake(var9, var10, this.projectX, this.projectY, 150 - this.chatTimer[var3], this.sceneCycle);
				}
				if (this.chatEffect[var3] == 4) {
					int var14 = this.fontBold12.stringWid(var9);
					int var15 = (150 - this.chatTimer[var3]) * (var14 + 100) / 150;
					Pix2D.setClipping(0, this.projectX - 50, this.layout.vpH, this.projectX + 50);
					this.fontBold12.drawString(this.projectX + 50 - var15, 0, this.projectY + 1, var9);
					this.fontBold12.drawString(this.projectX + 50 - var15, var10, this.projectY, var9);
					Pix2D.resetClipping();
				}
				if (this.chatEffect[var3] == 5) {
					int var16 = 150 - this.chatTimer[var3];
					int var17 = 0;
					if (var16 < 25) {
						var17 = var16 - 25;
					} else if (var16 > 125) {
						var17 = var16 - 125;
					}
					Pix2D.setClipping(this.projectY - this.fontBold12.height - 1, 0, this.projectY + 5, this.layout.vpW);
					this.fontBold12.centreString(this.projectX, this.projectY + 1 + var17, 0, var9);
					this.fontBold12.centreString(this.projectX, this.projectY + var17, var10, var9);
					Pix2D.resetClipping();
				}
			} else {
				this.fontBold12.centreString(this.projectX, this.projectY + 1, 0, var9);
				this.fontBold12.centreString(this.projectX, this.projectY, 16776960, var9);
			}
		}
	}

	@ObfuscatedName("client.o(Z)V")
	public void drawTileHint() {
		if (this.hintType == 2) {
			this.projectFromGround((this.hintTileX - this.sceneBaseTileX << 7) + this.hintOffsetX, this.hintHeight * 2, (this.hintTileZ - this.sceneBaseTileZ << 7) + this.hintOffsetZ);
			if (this.projectX > -1 && loopCycle % 20 < 10) {
				this.imageHeadiconsHint[0].plotSprite(this.projectY - 28, this.projectX - 12);
			}
		}
	}

	@ObfuscatedName("client.a(LLRUWCBNN;ZI)V")
	public void projectFromEntity(ClientEntity arg0, int arg2) {
		this.projectFromGround(arg0.field1157, arg2, arg0.field1158);
	}

	@ObfuscatedName("client.c(IIII)V")
	public void projectFromGround(int arg0, int arg1, int arg2) {
		if (arg0 < 128 || arg2 < 128 || arg0 > 13056 || arg2 > 13056) {
			this.projectX = -1;
			this.projectY = -1;
			return;
		}
		int var5 = this.getHeightmapY(arg2, arg0, this.currentLevel) - arg1;
		int var6 = arg0 - this.cameraX;
		int var7 = var5 - this.cameraY;
		int var8 = arg2 - this.cameraZ;
		int var9 = Model.sinTable[this.cameraPitch];
		int var10 = Model.cosTable[this.cameraPitch];
		int var11 = Model.sinTable[this.cameraYaw];
		int var12 = Model.cosTable[this.cameraYaw];
		int var13 = var6 * var12 + var8 * var11 >> 16;
		int var14 = var8 * var12 - var6 * var11 >> 16;
		int var16 = var7 * var10 - var9 * var14 >> 16;
		int var17 = var7 * var9 + var10 * var14 >> 16;
		if (var17 >= 50) {
			this.projectX = var13 * Pix3D.zoom / var17 + Pix3D.centerX;
			this.projectY = var16 * Pix3D.zoom / var17 + Pix3D.centerY;
		} else {
			this.projectX = -1;
			this.projectY = -1;
		}
	}

	@ObfuscatedName("client.a(IIBI)I")
	public int getHeightmapY(int arg0, int arg1, int arg3) {
		int var5 = arg1 >> 7;
		int var6 = arg0 >> 7;
		if (var5 < 0 || var6 < 0 || var5 > 103 || var6 > 103) {
			return 0;
		}
		int var7 = arg3;
		if (arg3 < 3 && (this.levelTileFlags[1][var5][var6] & 0x2) == 2) {
			var7 = arg3 + 1;
		}
		int var8 = arg1 & 0x7F;
		int var9 = arg0 & 0x7F;
		int var10 = (128 - var8) * this.levelHeightmap[var7][var5][var6] + this.levelHeightmap[var7][var5 + 1][var6] * var8 >> 7;
		int var11 = (128 - var8) * this.levelHeightmap[var7][var5][var6 + 1] + this.levelHeightmap[var7][var5 + 1][var6 + 1] * var8 >> 7;
		return (128 - var9) * var10 + var9 * var11 >> 7;
	}

	@ObfuscatedName("client.d(II)V")
	public void updateTextures(int arg0) {
		if (lowMem) {
			return;
		}
		for (int var3 = 0; var3 < this.ANIMATED_TEXTURES.length; var3++) {
			int var4 = this.ANIMATED_TEXTURES[var3][0];
			int speed = this.ANIMATED_TEXTURES[var3][1];
			if (var4 < Pix3D.TEXTURE_COUNT && Pix3D.textures[var4] != null && Pix3D.textureCycle[var4] >= arg0) {
				Pix8 var5 = Pix3D.textures[var4];
				int var6 = var5.hi * var5.wi - 1;
				int var7 = this.sceneDelta * var5.wi * speed;
				byte[] var8 = var5.pixels;
				byte[] var9 = this.textureBuffer;
				for (int var10 = 0; var10 <= var6; var10++) {
					var9[var10] = var8[var10 - var7 & var6];
				}
				var5.pixels = var9;
				this.textureBuffer = var8;
				Pix3D.pushTexture(var4);
			}
		}
	}

	@ObfuscatedName("client.C(I)V")
	public void draw3DEntityElements() {
		this.drawPrivateMessages();
		if (this.crossMode == 1) {
			this.imageCross[this.crossCycle / 100].plotSprite(this.crossY - 8 - this.layout.vpY, this.crossX - 8 - this.layout.vpX);
		}
		if (this.crossMode == 2) {
			this.imageCross[this.crossCycle / 100 + 4].plotSprite(this.crossY - 8 - this.layout.vpY, this.crossX - 8 - this.layout.vpX);
		}
		if (this.viewportOverlayInterfaceId != -1) {
			this.updateInterfaceAnimation(this.sceneDelta, this.viewportOverlayInterfaceId);
			this.drawViewportOverlay(Component.get(this.viewportOverlayInterfaceId));
		}
		if (this.viewportInterfaceId != -1) {
			this.updateInterfaceAnimation(this.sceneDelta, this.viewportInterfaceId);
			this.drawInterface(this.layout.mainY, this.layout.mainX, Component.get(this.viewportInterfaceId), 0);
		}
		this.updateWorldLocation();
		if (!this.menuVisible) {
			this.handleInput();
			this.drawTooltip();
		} else if (this.menuArea == 0 && !this.layout.resizable) {
			// (resizable: presentGame() draws it, over the panels as well as the scene)
			this.drawMenu();
		}
		if (this.inMultizone == 1) {
			this.imageOverlayMultiway.plotSprite(this.layout.openH - 38, this.layout.openW - 40);
		}
		if (displayFps) {
			int var2 = this.layout.openW - 5;
			byte var3 = 20;
			int var4 = 16776960;
			if (super.fps < 30 && lowMem) {
				var4 = 16711680;
			}
			if (super.fps < 20 && !lowMem) {
				var4 = 16711680;
			}
			this.fontPlain12.method243("Fps:" + super.fps, var4, var2, var3);
			int var13 = var3 + 15;
			Runtime var5 = Runtime.getRuntime();
			int var6 = (int) ((var5.totalMemory() - var5.freeMemory()) / 1024L);
			int var7 = 16776960;
			if (var6 > 33554432 && lowMem) {
				int var8 = 16711680;
			}
			if (var6 > 67108864 && !lowMem) {
				int var9 = 16711680;
			}
			this.fontPlain12.method243("Mem:" + var6 + "k", 16776960, var2, var13);
			var13 += 15;
		}
		// Plugin overlays. Under the client's own panels and under the xp drops, which are the
		// client's own overlay - a plugin draws alongside the game, never over the furniture the
		// player needs to turn it off.
		if (this.plugins != null) {
			this.plugins.renderOverlays(this.layout.openW, this.layout.openH,
				jagex2.client.plugin.Overlay.LAYER_SCREEN);
		}
		// Drawn last of the viewport overlays so the settings panels sit on top of everything else.
		if (this.pluginPanelOpen && this.plugins != null) {
			this.drawPluginPanel();
		}
		if (this.qolPanelOpen) {
			this.drawQolPanel();
		}
		if (this.systemUpdateTimer != 0) {
			int var10 = this.systemUpdateTimer / 50;
			int var11 = var10 / 60;
			int var12 = var10 % 60;
			if (var12 < 10) {
				this.fontPlain12.drawString(4, 16776960, this.layout.openH - 5, "System update in: " + var11 + ":0" + var12);
			} else {
				this.fontPlain12.drawString(4, 16776960, this.layout.openH - 5, "System update in: " + var11 + ":" + var12);
			}
			cyclelogic3++;
			if (cyclelogic3 > 112) {
				cyclelogic3 = 0;
				// ANTICHEAT_CYCLELOGIC3
				this.out.p1isaac(197);
				this.out.p4(0);
			}
		}
	}

	@ObfuscatedName("client.q(I)V")
	public void drawPrivateMessages() {
		if (this.splitPrivateChat == 0) {
			return;
		}
		PixFont var2 = this.fontPlain12;
		int var3 = 0;
		if (this.systemUpdateTimer != 0) {
			var3 = 1;
		}
		for (int var4 = 0; var4 < 100; var4++) {
			if (this.messageText[var4] != null) {
				int var5 = this.messageType[var4];
				String var6 = this.messageSender[var4];
				// the sender's icons (ChatIcons markers), taken off the front of the name
				String var7 = ChatIcons.leading(var6);
				if (var6 != null) {
					var6 = var6.substring(var7.length());
				}
				if ((var5 == 3 || var5 == 7) && (var5 == 7 || this.chatPrivateMode == 0 || this.chatPrivateMode == 1 && this.isFriend(var6))) {
					int var8 = this.layout.openH - 5 - var3 * 13;
					if (this.messageCont[var4]) {
						var2.drawString(4 + this.messageIndent[var4], 0, var8, this.messageText[var4]);
						var2.drawString(4 + this.messageIndent[var4], 65535, var8 - 1, this.messageText[var4]);
						var3++;
						if (var3 >= 5) {
							return;
						}
						continue;
					}
					byte var9 = 4;
					var2.drawString(var9, 0, var8, "From");
					var2.drawString(var9, 65535, var8 - 1, "From");
					int var10 = var9 + var2.stringWidTag("From ");
					if (var7.length() > 0) {
						ChatIcons.draw(var2, this.imageModIcons, var10, var8, 0, var7);
						var10 += ChatIcons.width(var2, var7);
					}
					var2.drawString(var10, 0, var8, var6 + ": " + this.messageText[var4]);
					var2.drawString(var10, 65535, var8 - 1, var6 + ": " + this.messageText[var4]);
					var3++;
					if (var3 >= 5) {
						return;
					}
				}
				if (var5 == 5 && this.chatPrivateMode < 2) {
					int var11 = this.layout.openH - 5 - var3 * 13;
					var2.drawString(4, 0, var11, this.messageText[var4]);
					var2.drawString(4, 65535, var11 - 1, this.messageText[var4]);
					var3++;
					if (var3 >= 5) {
						return;
					}
				}
				if (var5 == 6 && this.chatPrivateMode < 2) {
					int var12 = this.layout.openH - 5 - var3 * 13;
					String toLine = this.messageCont[var4] ? this.messageText[var4] : "To " + var6 + ": " + this.messageText[var4];
					int toX = this.messageCont[var4] ? 4 + this.messageIndent[var4] : 4;
					var2.drawString(toX, 0, var12, toLine);
					var2.drawString(toX, 65535, var12 - 1, toLine);
					var3++;
					if (var3 >= 5) {
						return;
					}
				}
			}
		}
	}

	@ObfuscatedName("client.A(I)V")
	public void updateWorldLocation() {
		this.overrideChat = 0;
		int var2 = (localPlayer.field1157 >> 7) + this.sceneBaseTileX;
		int var3 = (localPlayer.field1158 >> 7) + this.sceneBaseTileZ;
		if (var2 >= 3053 && var2 <= 3156 && var3 >= 3056 && var3 <= 3136) {
			this.overrideChat = 1;
		}
		if (var2 >= 3072 && var2 <= 3118 && var3 >= 9492 && var3 <= 9535) {
			this.overrideChat = 1;
		}
		if (this.overrideChat == 1 && var2 >= 3139 && var2 <= 3199 && var3 >= 3008 && var3 <= 3062) {
			this.overrideChat = 0;
		}
	}

	@ObfuscatedName("client.g(B)V")
	public void drawTooltip() {
		if (this.menuSize < 2 && this.objSelected == 0 && this.spellSelected == 0) {
			return;
		}
		String var2;
		if (this.objSelected == 1 && this.menuSize < 2) {
			var2 = "Use " + this.objSelectedName + " with...";
		} else if (this.spellSelected == 1 && this.menuSize < 2) {
			var2 = this.spellCaption + "...";
		} else {
			var2 = this.menuOption[this.menuSize - 1];
		}
		if (this.menuSize > 2) {
			var2 = var2 + "@whi@ / " + (this.menuSize - 2) + " more options";
		}
		if (ChatIcons.iconCount(var2) > 0) {
			ChatIcons.draw(this.fontBold12, this.imageModIcons, 4, 15, 16777215, "@sh1@" + var2);
		} else {
			this.fontBold12.drawStringAntiMacro(true, loopCycle / 1000, 4, 16777215, 15, var2);
		}
	}

	@ObfuscatedName("client.p(Z)V")
	public void drawMenu() {
		int var2 = this.menuX;
		int var3 = this.menuY;
		int var4 = this.menuWidth;
		int var5 = this.menuHeight;
		int var6 = 6116423;
		Pix2D.fillRect(var5, var3, var6, var4, var2);
		Pix2D.fillRect(16, var3 + 1, 0, var4 - 2, var2 + 1);
		Pix2D.drawRect(var3 + 18, var5 - 19, 0, var2 + 1, var4 - 2);
		this.fontBold12.drawString(var2 + 3, var6, var3 + 14, "Choose Option");
		int var7 = super.mouseX;
		int var8 = super.mouseY;
		if (this.menuArea == 0) {
			var7 -= this.layout.vpX;
			var8 -= this.layout.vpY;
		}
		if (this.menuArea == 1) {
			var7 -= SIDE_X;
			var8 -= 205;
		}
		if (this.menuArea == 2) {
			var7 -= CHAT_X;
			var8 -= CHAT_Y;
		}
		// Visual positions, not array indices: p counts from the top row and menuRowIndex() turns it
		// into the index, so the draw and the click in handleMouseInput() cannot disagree about
		// which row is where. They used to share a copy of the same arithmetic.
		for (int p = 0; p < this.menuRowsShown; p++) {
			int var10 = this.menuRowY(p);
			int var11 = 16777215;
			if (var7 > var2 && var7 < var2 + var4 && var8 > var10 - 13 && var8 < var10 + 3) {
				var11 = 16776960;
			}
			// ChatIcons draws a player's crown and badge where the markers sit; a row without any is
			// the same drawStringTag call it always was.
			ChatIcons.draw(this.fontBold12, this.imageModIcons, var2 + 3, var10, var11,
				"@sh1@" + this.fitMenuText(this.menuOption[this.menuRowIndex(p)], var4 - 6));
		}
		// A menu with rows it is not showing says so, in what were the ground-item overlay's own
		// colours and are now this menu's - see MENU_BAR_TRACK. A
		// menu that runs off the bottom of the screen with no mark is what this round is fixing;
		// one that silently shows two thirds of itself would be the same bug in a smaller box.
		if (this.menuSize > this.menuRowsShown) {
			int barX = var2 + var4 - MENU_BAR_W - 1;
			int trackY = var3 + 19;
			int track = this.menuRowsShown * MENU_ROW_H;
			Pix2D.fillRect(track, trackY, MENU_BAR_TRACK, MENU_BAR_W, barX);
			int thumb = track * this.menuRowsShown / this.menuSize;
			if (thumb < 3) {
				thumb = 3;
			}
			int thumbY = trackY + track * this.menuScroll / this.menuSize;
			if (thumbY + thumb > trackY + track) {
				thumbY = trackY + track - thumb;
			}
			Pix2D.fillRect(thumb, thumbY, MENU_BAR_THUMB, MENU_BAR_W, barX);
		}
	}

	// A menu row cut down to $max pixels with "..." on the end, never inside an @col@ tag: every '@'
	// in a menu option opens or closes one, so an odd count means the cut landed inside a tag.
	private String fitMenuText(String text, int max) {
		if (text == null || ChatIcons.width(this.fontBold12, text) <= max) {
			return text;
		}
		int dots = this.fontBold12.stringWidTag("...");
		for (int n = text.length() - 1; n > 0; n--) {
			String cut = text.substring(0, n);
			int ats = 0;
			for (int i = 0; i < cut.length(); i++) {
				if (cut.charAt(i) == '@') {
					ats++;
				}
			}
			if ((ats & 1) == 1) {
				continue;
			}
			if (ChatIcons.width(this.fontBold12, cut) + dots <= max) {
				return cut + "...";
			}
		}
		return "...";
	}

	@ObfuscatedName("client.a(IIIIII)V")
	/**
	 * The minimap icon for a loc, or null when there is none to draw.
	 *
	 * imageMapscene holds ONE HUNDRED sprites and drawMinimapLoc used to index it with the loc's own
	 * mapscene id and no check at all. A loc carrying a higher id - which is what an import from a
	 * later cache produces, OSRS numbering them past 200 - threw ArrayIndexOutOfBoundsException from
	 * inside update(), and GameShell.run calls update() with no try/catch, so the loop thread ended
	 * and the window froze on whatever it had last painted. On 2026-09-29 that was "Loading - please
	 * wait" after a Zul-andra teleport, for ever, through restarts, and the character had to be moved
	 * by editing its save from outside the server.
	 *
	 * Those four locs have had their ids taken off (content zulandra.loc), but an icon that cannot be
	 * drawn must never be able to do this again, whatever puts it there.
	 */
	private Pix8 mapsceneOf(LocType loc) {
		int id = loc.field1649;
		return id < 0 || id >= this.imageMapscene.length ? null : this.imageMapscene[id];
	}

	public void drawMinimapLoc(int arg0, int arg1, int arg2, int arg3, int arg5) {
		int var7 = this.scene.method300(arg1, arg2, arg0);
		if (var7 != 0) {
			int var9 = this.scene.method304(arg1, arg2, arg0, var7);
			int var10 = var9 >> 6 & 0x3;
			int var11 = var9 & 0x1F;
			int var12 = arg5;
			if (var7 > 0) {
				var12 = arg3;
			}
			int[] var13 = this.imageMinimap.pixels;
			int var14 = (103 - arg0) * 512 * 4 + arg2 * 4 + 24624;
			int var15 = var7 >> 14 & 0x7FFF;
			LocType var16 = LocType.method561(var15);
			if (var16.field1649 == -1) {
				if (var11 == 0 || var11 == 2) {
					if (var10 == 0) {
						var13[var14] = var12;
						var13[var14 + 512] = var12;
						var13[var14 + 1024] = var12;
						var13[var14 + 1536] = var12;
					} else if (var10 == 1) {
						var13[var14] = var12;
						var13[var14 + 1] = var12;
						var13[var14 + 2] = var12;
						var13[var14 + 3] = var12;
					} else if (var10 == 2) {
						var13[var14 + 3] = var12;
						var13[var14 + 3 + 512] = var12;
						var13[var14 + 3 + 1024] = var12;
						var13[var14 + 3 + 1536] = var12;
					} else if (var10 == 3) {
						var13[var14 + 1536] = var12;
						var13[var14 + 1536 + 1] = var12;
						var13[var14 + 1536 + 2] = var12;
						var13[var14 + 1536 + 3] = var12;
					}
				}
				if (var11 == 3) {
					if (var10 == 0) {
						var13[var14] = var12;
					} else if (var10 == 1) {
						var13[var14 + 3] = var12;
					} else if (var10 == 2) {
						var13[var14 + 3 + 1536] = var12;
					} else if (var10 == 3) {
						var13[var14 + 1536] = var12;
					}
				}
				if (var11 == 2) {
					if (var10 == 3) {
						var13[var14] = var12;
						var13[var14 + 512] = var12;
						var13[var14 + 1024] = var12;
						var13[var14 + 1536] = var12;
					} else if (var10 == 0) {
						var13[var14] = var12;
						var13[var14 + 1] = var12;
						var13[var14 + 2] = var12;
						var13[var14 + 3] = var12;
					} else if (var10 == 1) {
						var13[var14 + 3] = var12;
						var13[var14 + 3 + 512] = var12;
						var13[var14 + 3 + 1024] = var12;
						var13[var14 + 3 + 1536] = var12;
					} else if (var10 == 2) {
						var13[var14 + 1536] = var12;
						var13[var14 + 1536 + 1] = var12;
						var13[var14 + 1536 + 2] = var12;
						var13[var14 + 1536 + 3] = var12;
					}
				}
			} else {
				Pix8 var17 = this.mapsceneOf(var16);
				if (var17 != null) {
					int var18 = (var16.field1655 * 4 - var17.wi) / 2;
					int var19 = (var16.field1629 * 4 - var17.hi) / 2;
					var17.plotSprite((104 - arg0 - var16.field1629) * 4 + 48 + var19, arg2 * 4 + 48 + var18);
				}
			}
		}
		int var20 = this.scene.method302(arg1, arg2, arg0);
		if (var20 != 0) {
			int var21 = this.scene.method304(arg1, arg2, arg0, var20);
			int var22 = var21 >> 6 & 0x3;
			int var23 = var21 & 0x1F;
			int var24 = var20 >> 14 & 0x7FFF;
			LocType var25 = LocType.method561(var24);
			if (var25.field1649 != -1) {
				Pix8 var26 = this.mapsceneOf(var25);
				if (var26 != null) {
					int var27 = (var25.field1655 * 4 - var26.wi) / 2;
					int var28 = (var25.field1629 * 4 - var26.hi) / 2;
					var26.plotSprite((104 - arg0 - var25.field1629) * 4 + 48 + var28, arg2 * 4 + 48 + var27);
				}
			} else if (var23 == 9) {
				int var29 = 15658734;
				if (var20 > 0) {
					var29 = 15597568;
				}
				int[] var30 = this.imageMinimap.pixels;
				int var31 = (103 - arg0) * 512 * 4 + arg2 * 4 + 24624;
				if (var22 == 0 || var22 == 2) {
					var30[var31 + 1536] = var29;
					var30[var31 + 1024 + 1] = var29;
					var30[var31 + 512 + 2] = var29;
					var30[var31 + 3] = var29;
				} else {
					var30[var31] = var29;
					var30[var31 + 512 + 1] = var29;
					var30[var31 + 1024 + 2] = var29;
					var30[var31 + 1536 + 3] = var29;
				}
			}
		}
		int var32 = this.scene.method303(arg1, arg2, arg0);
		if (var32 != 0) {
			int var33 = var32 >> 14 & 0x7FFF;
			LocType var34 = LocType.method561(var33);
			if (var34.field1649 != -1) {
				Pix8 var35 = this.mapsceneOf(var34);
				if (var35 != null) {
					int var36 = (var34.field1655 * 4 - var35.wi) / 2;
					int var37 = (var34.field1629 * 4 - var35.hi) / 2;
					var35.plotSprite((104 - arg0 - var34.field1629) * 4 + 48 + var37, arg2 * 4 + 48 + var36);
				}
			}
		}
	}

	@ObfuscatedName("client.a(IIII)Z")
	public boolean interactWithLoc(int arg0, int arg2, int arg3) {
		int var5 = arg3 >> 14 & 0x7FFF;
		int var6 = this.scene.method304(this.currentLevel, arg2, arg0, arg3);
		if (var6 == -1) {
			return false;
		}
		int var7 = var6 & 0x1F;
		int var8 = var6 >> 6 & 0x3;
		if (var7 == 10 || var7 == 11 || var7 == 22) {
			LocType var9 = LocType.method561(var5);
			int var10;
			int var11;
			if (var8 == 0 || var8 == 2) {
				var10 = var9.field1655;
				var11 = var9.field1629;
			} else {
				var10 = var9.field1629;
				var11 = var9.field1655;
			}
			int var12 = var9.field1618;
			if (var8 != 0) {
				var12 = (var12 >> 4 - var8) + (var12 << var8 & 0xF);
			}
			this.tryMove(true, false, arg0, localPlayer.routeTileZ[0], var10, var11, 2, 0, arg2, var12, 0, localPlayer.routeTileX[0]);
		} else {
			this.tryMove(true, false, arg0, localPlayer.routeTileZ[0], 0, 0, 2, var7 + 1, arg2, 0, var8, localPlayer.routeTileX[0]);
		}
		this.crossX = super.mouseClickX;
		this.crossY = super.mouseClickY;
		this.crossMode = 2;
		this.crossCycle = 0;
		return true;
	}

	@ObfuscatedName("client.a(ZZIIIIIIIIII)Z")
	public boolean tryMove(boolean arg0, boolean arg1, int arg2, int arg3, int arg4, int arg5, int arg6, int arg7, int arg8, int arg9, int arg10, int arg11) {
		byte var13 = 104;
		byte var14 = 104;
		for (int var15 = 0; var15 < var13; var15++) {
			for (int var39 = 0; var39 < var14; var39++) {
				this.bfsDirection[var15][var39] = 0;
				this.bfsCost[var15][var39] = 99999999;
			}
		}
		int var16 = arg11;
		int var17 = arg3;
		this.bfsDirection[arg11][arg3] = 99;
		this.bfsCost[arg11][arg3] = 0;
		byte var18 = 0;
		int var19 = 0;
		this.bfsStepX[var18] = arg11;
		int var40 = var18 + 1;
		this.bfsStepZ[var18] = arg3;
		boolean var20 = false;
		int var21 = this.bfsStepX.length;
		int[][] var22 = this.levelCollisionMap[this.currentLevel].field1585;
		while (var40 != var19) {
			var16 = this.bfsStepX[var19];
			var17 = this.bfsStepZ[var19];
			var19 = (var19 + 1) % var21;
			if (arg8 == var16 && arg2 == var17) {
				var20 = true;
				break;
			}
			if (arg7 != 0) {
				if ((arg7 < 5 || arg7 == 10) && this.levelCollisionMap[this.currentLevel].method541(arg8, arg2, arg7 - 1, var16, var17, arg10)) {
					var20 = true;
					break;
				}
				if (arg7 < 10 && this.levelCollisionMap[this.currentLevel].method542(var17, arg8, var16, arg10, arg7 - 1, arg2)) {
					var20 = true;
					break;
				}
			}
			if (arg4 != 0 && arg5 != 0 && this.levelCollisionMap[this.currentLevel].method543(arg4, var16, arg8, arg9, arg5, arg2, var17)) {
				var20 = true;
				break;
			}
			int var38 = this.bfsCost[var16][var17] + 1;
			if (var16 > 0 && this.bfsDirection[var16 - 1][var17] == 0 && (var22[var16 - 1][var17] & 0x1280108) == 0) {
				this.bfsStepX[var40] = var16 - 1;
				this.bfsStepZ[var40] = var17;
				var40 = (var40 + 1) % var21;
				this.bfsDirection[var16 - 1][var17] = 2;
				this.bfsCost[var16 - 1][var17] = var38;
			}
			if (var16 < var13 - 1 && this.bfsDirection[var16 + 1][var17] == 0 && (var22[var16 + 1][var17] & 0x1280180) == 0) {
				this.bfsStepX[var40] = var16 + 1;
				this.bfsStepZ[var40] = var17;
				var40 = (var40 + 1) % var21;
				this.bfsDirection[var16 + 1][var17] = 8;
				this.bfsCost[var16 + 1][var17] = var38;
			}
			if (var17 > 0 && this.bfsDirection[var16][var17 - 1] == 0 && (var22[var16][var17 - 1] & 0x1280102) == 0) {
				this.bfsStepX[var40] = var16;
				this.bfsStepZ[var40] = var17 - 1;
				var40 = (var40 + 1) % var21;
				this.bfsDirection[var16][var17 - 1] = 1;
				this.bfsCost[var16][var17 - 1] = var38;
			}
			if (var17 < var14 - 1 && this.bfsDirection[var16][var17 + 1] == 0 && (var22[var16][var17 + 1] & 0x1280120) == 0) {
				this.bfsStepX[var40] = var16;
				this.bfsStepZ[var40] = var17 + 1;
				var40 = (var40 + 1) % var21;
				this.bfsDirection[var16][var17 + 1] = 4;
				this.bfsCost[var16][var17 + 1] = var38;
			}
			if (var16 > 0 && var17 > 0 && this.bfsDirection[var16 - 1][var17 - 1] == 0 && (var22[var16 - 1][var17 - 1] & 0x128010E) == 0 && (var22[var16 - 1][var17] & 0x1280108) == 0 && (var22[var16][var17 - 1] & 0x1280102) == 0) {
				this.bfsStepX[var40] = var16 - 1;
				this.bfsStepZ[var40] = var17 - 1;
				var40 = (var40 + 1) % var21;
				this.bfsDirection[var16 - 1][var17 - 1] = 3;
				this.bfsCost[var16 - 1][var17 - 1] = var38;
			}
			if (var16 < var13 - 1 && var17 > 0 && this.bfsDirection[var16 + 1][var17 - 1] == 0 && (var22[var16 + 1][var17 - 1] & 0x1280183) == 0 && (var22[var16 + 1][var17] & 0x1280180) == 0 && (var22[var16][var17 - 1] & 0x1280102) == 0) {
				this.bfsStepX[var40] = var16 + 1;
				this.bfsStepZ[var40] = var17 - 1;
				var40 = (var40 + 1) % var21;
				this.bfsDirection[var16 + 1][var17 - 1] = 9;
				this.bfsCost[var16 + 1][var17 - 1] = var38;
			}
			if (var16 > 0 && var17 < var14 - 1 && this.bfsDirection[var16 - 1][var17 + 1] == 0 && (var22[var16 - 1][var17 + 1] & 0x1280138) == 0 && (var22[var16 - 1][var17] & 0x1280108) == 0 && (var22[var16][var17 + 1] & 0x1280120) == 0) {
				this.bfsStepX[var40] = var16 - 1;
				this.bfsStepZ[var40] = var17 + 1;
				var40 = (var40 + 1) % var21;
				this.bfsDirection[var16 - 1][var17 + 1] = 6;
				this.bfsCost[var16 - 1][var17 + 1] = var38;
			}
			if (var16 < var13 - 1 && var17 < var14 - 1 && this.bfsDirection[var16 + 1][var17 + 1] == 0 && (var22[var16 + 1][var17 + 1] & 0x12801E0) == 0 && (var22[var16 + 1][var17] & 0x1280180) == 0 && (var22[var16][var17 + 1] & 0x1280120) == 0) {
				this.bfsStepX[var40] = var16 + 1;
				this.bfsStepZ[var40] = var17 + 1;
				var40 = (var40 + 1) % var21;
				this.bfsDirection[var16 + 1][var17 + 1] = 12;
				this.bfsCost[var16 + 1][var17 + 1] = var38;
			}
		}
		this.tryMoveNearest = 0;
		if (!var20) {
			if (!arg0) {
				return false;
			}
			int var23 = 1000;
			int var24 = 100;
			byte var25 = 10;
			for (int var26 = arg8 - var25; var26 <= arg8 + var25; var26++) {
				for (int var27 = arg2 - var25; var27 <= arg2 + var25; var27++) {
					if (var26 >= 0 && var27 >= 0 && var26 < 104 && var27 < 104 && this.bfsCost[var26][var27] < 100) {
						int var28 = 0;
						if (var26 < arg8) {
							var28 = arg8 - var26;
						} else if (var26 > arg4 + arg8 - 1) {
							var28 = var26 - (arg4 + arg8 - 1);
						}
						int var29 = 0;
						if (var27 < arg2) {
							var29 = arg2 - var27;
						} else if (var27 > arg2 + arg5 - 1) {
							var29 = var27 - (arg2 + arg5 - 1);
						}
						int var30 = var28 * var28 + var29 * var29;
						if (var30 < var23 || var23 == var30 && this.bfsCost[var26][var27] < var24) {
							var23 = var30;
							var24 = this.bfsCost[var26][var27];
							var16 = var26;
							var17 = var27;
						}
					}
				}
			}
			if (var23 == 1000) {
				return false;
			}
			if (arg11 == var16 && arg3 == var17) {
				return false;
			}
			this.tryMoveNearest = 1;
		}
		byte var31 = 0;
		if (arg1) {
			this.load();
		}
		this.bfsStepX[var31] = var16;
		int var41 = var31 + 1;
		this.bfsStepZ[var31] = var17;
		int var32;
		int var33 = var32 = this.bfsDirection[var16][var17];
		while (arg11 != var16 || arg3 != var17) {
			if (var32 != var33) {
				var32 = var33;
				this.bfsStepX[var41] = var16;
				this.bfsStepZ[var41++] = var17;
			}
			if ((var33 & 0x2) != 0) {
				var16++;
			} else if ((var33 & 0x8) != 0) {
				var16--;
			}
			if ((var33 & 0x1) != 0) {
				var17++;
			} else if ((var33 & 0x4) != 0) {
				var17--;
			}
			var33 = this.bfsDirection[var16][var17];
		}
		if (var41 > 0) {
			int var34 = var41;
			if (var41 > 25) {
				var34 = 25;
			}
			var41--;
			int var35 = this.bfsStepX[var41];
			int var36 = this.bfsStepZ[var41];
			if (arg6 == 0) {
				// MOVE_GAMECLICK
				this.out.p1isaac(28);
				this.out.p1(var34 + var34 + 3);
			}
			if (arg6 == 1) {
				// MOVE_MINIMAPCLICK
				this.out.p1isaac(213);
				this.out.p1(var34 + var34 + 3 + 14);
			}
			if (arg6 == 2) {
				// MOVE_OPCLICK
				this.out.p1isaac(247);
				this.out.p1(var34 + var34 + 3);
			}
			this.out.p2_alt3(this.sceneBaseTileX + var35);
			this.out.p1(super.actionKey[5] == 1 ? 1 : 0);
			this.out.p2_alt3(this.sceneBaseTileZ + var36);
			this.flagSceneTileX = this.bfsStepX[0];
			this.flagSceneTileZ = this.bfsStepZ[0];
			for (int var37 = 1; var37 < var34; var37++) {
				var41--;
				this.out.p1(this.bfsStepX[var41] - var35);
				this.out.p1_alt3(this.bfsStepZ[var41] - var36);
			}
			return true;
		} else if (arg6 == 1) {
			return false;
		} else {
			return true;
		}
	}

	@ObfuscatedName("client.h(I)Z")
	public boolean readPacket() {
		if (this.stream == null) {
			return false;
		}

		try {
			int available = this.stream.available();
			if (available == 0) {
				return false;
			}

			if (this.ptype == -1) {
				this.stream.read(this.in.data, 0, 1);
				this.ptype = this.in.data[0] & 0xFF;
				if (this.randomIn != null) {
					this.ptype = this.ptype - this.randomIn.nextInt() & 0xFF;
				}
				this.psize = Protocol.SERVERPROT_LENGTH[this.ptype];
				available--;
			}

			if (this.psize == -1) {
				if (available <= 0) {
					return false;
				}

				this.stream.read(this.in.data, 0, 1);
				this.psize = this.in.data[0] & 0xFF;
				available--;
			} else if (this.psize == -2) {
				if (available <= 1) {
					return false;
				}

				this.stream.read(this.in.data, 0, 2);
				this.in.pos = 0;
				this.psize = this.in.g2();
				available -= 2;
			}

			if (available < this.psize) {
				return false;
			}

			this.in.pos = 0;
			this.stream.read(this.in.data, 0, this.psize);

			this.idleNetCycles = 0;
			this.ptype2 = this.ptype1;
			this.ptype1 = this.ptype0;
			this.ptype0 = this.ptype;

			if (this.ptype == 166) {
				// IF_SETPOSITION
				int var4 = this.in.g2b_alt1();
				int var5 = this.in.g2b_alt1();
				int var6 = this.in.g2();
				Component var7 = Component.get(var6);
				var7.field710 = var5;
				var7.field741 = var4;
				this.ptype = -1;
				return true;
			}

			if (this.ptype == 186) {
				// todo (setting model xan/yan/zoom)
				int var8 = this.in.g2_alt2();
				int var9 = this.in.g2_alt3();
				int var10 = this.in.g2_alt2();
				int var11 = this.in.g2_alt1();
				Component.get(var9).xan = var8;
				Component.get(var9).yan = var11;
				Component.get(var9).zoom = var10;
				this.ptype = -1;
				return true;
			}

			if (this.ptype == 216) {
				// IF_SETMODEL
				int var12 = this.in.g2_alt3();
				int var13 = this.in.g2_alt3();
				Component.get(var13).modelType = 1;
				Component.get(var13).model = var12;
				this.ptype = -1;
				return true;
			}

			if (this.ptype == 26) {
				// SYNTH_SOUND
				int var14 = this.in.g2();
				int var15 = this.in.g1();
				int var16 = this.in.g2();
				if (var16 == 65535) {
					if (this.waveCount < 50) {
						this.waveIds[this.waveCount] = (short) var14;
						this.waveLoops[this.waveCount] = var15;
						this.waveDelay[this.waveCount] = 0;
						this.waveCount++;
					}
				} else if (this.waveEnabled && !lowMem && this.waveCount < 50) {
					this.waveIds[this.waveCount] = var14;
					this.waveLoops[this.waveCount] = var15;
					this.waveDelay[this.waveCount] = Wave.field1472[var14] + var16;
					this.waveCount++;
				}
				this.ptype = -1;
				return true;
			}

			if (this.ptype == 182) {
				// VARP_SMALL
				int var17 = this.in.g2_alt2();
				byte var18 = this.in.g1b_alt3();
				this.varCache[var17] = var18;
				if (this.varps[var17] != var18) {
					this.varps[var17] = var18;
					this.updateVarp(var17);
					this.redrawSidebar = true;
					if (this.stickyChatInterfaceId != -1) {
						this.redrawChatback = true;
					}
				}
				this.ptype = -1;
				return true;
			}

			if (this.ptype == 13) {
				// RESET_ANIMS
				for (int var19 = 0; var19 < this.players.length; var19++) {
					if (this.players[var19] != null) {
						this.players[var19].field1171 = -1;
					}
				}
				for (int var20 = 0; var20 < this.npcs.length; var20++) {
					if (this.npcs[var20] != null) {
						this.npcs[var20].field1171 = -1;
					}
				}
				this.ptype = -1;
				return true;
			}

			if (this.ptype == 156) {
				// MINIMAP_TOGGLE
				this.minimapType = this.in.g1();
				this.ptype = -1;
				return true;
			}

			if (this.ptype == 162) {
				// IF_SETNPCHEAD
				int var21 = this.in.g2_alt2();
				int var22 = this.in.g2_alt1();
				Component.get(var22).modelType = 2;
				Component.get(var22).model = var21;
				this.ptype = -1;
				return true;
			}

			if (this.ptype == 109) {
				// IF_OPENCHAT
				int var23 = this.in.g2();
				this.resetInterfaceAnimation(var23);
				if (this.sidebarInterfaceId != -1) {
					this.unloadCom(this.sidebarInterfaceId);
					this.sidebarInterfaceId = -1;
					this.redrawSidebar = true;
					this.redrawSideicons = true;
				}
				if (this.fullscreenInterfaceId0 != -1) {
					this.unloadCom(this.fullscreenInterfaceId0);
					this.fullscreenInterfaceId0 = -1;
					this.redrawFrame = true;
				}
				if (this.fullscreenInterfaceId1 != -1) {
					this.unloadCom(this.fullscreenInterfaceId1);
					this.fullscreenInterfaceId1 = -1;
				}
				if (this.viewportInterfaceId != -1) {
					this.unloadCom(this.viewportInterfaceId);
					this.viewportInterfaceId = -1;
				}
				if (this.chatInterfaceId != var23) {
					this.unloadCom(this.chatInterfaceId);
					this.chatInterfaceId = var23;
				}
				this.pressedContinueOption = false;
				this.redrawChatback = true;
				this.ptype = -1;
				return true;
			}

			if (this.ptype == 220) {
				// MIDI_SONG
				int var24 = this.in.g2_alt3();
				if (var24 == 65535) {
					var24 = -1;
				}
				if (this.nextMidiSong != var24 && this.midiActive && !lowMem && this.nextMusicDelay == 0) {
					this.midiSong = var24;
					this.midiFading = true;
					this.onDemand.request(2, this.midiSong);
				}
				this.nextMidiSong = var24;
				this.ptype = -1;
				return true;
			}

			if (this.ptype == 249) {
				// MIDI_JINGLE
				int var25 = this.in.g2_alt1();
				int var26 = this.in.g3_alt3();
				if (this.midiActive && !lowMem) {
					this.midiSong = var25;
					this.midiFading = false;
					this.onDemand.request(2, this.midiSong);
					this.nextMusicDelay = var26;
				}
				this.ptype = -1;
				return true;
			}

			if (this.ptype == 158) {
				// TUT_OPEN
				int var27 = this.in.g2b_alt1();
				if (this.stickyChatInterfaceId != var27) {
					this.unloadCom(this.stickyChatInterfaceId);
					this.stickyChatInterfaceId = var27;
				}
				this.redrawChatback = true;
				this.ptype = -1;
				return true;
			}

			if (this.ptype == 218) {
				// IF_SETCOLOUR
				int var28 = this.in.g2();
				int var29 = this.in.g2_alt2();
				int var30 = var29 >> 10 & 0x1F;
				int var31 = var29 >> 5 & 0x1F;
				int var32 = var29 & 0x1F;
				Component.get(var28).colour = (var32 << 3) + (var30 << 19) + (var31 << 11);
				this.ptype = -1;
				return true;
			}

			if (this.ptype == 157) {
				// SET_PLAYER_OP
				int var33 = this.in.g1_alt2();
				String var34 = this.in.gjstr();
				int var35 = this.in.g1();
				if (var33 >= 1 && var33 <= 5) {
					if (var34.equalsIgnoreCase("null")) {
						var34 = null;
					}
					this.playerOps[var33 - 1] = var34;
					this.playerOpPrimary[var33 - 1] = var35 == 0;
				}
				this.ptype = -1;
				return true;
			}

			if (this.ptype == 6) {
				// P_NAMEDIALOG
				this.dialogPrompt = this.pendingDialogPrompt;
				this.pendingDialogPrompt = null;
				this.showSocialInput = false;
				this.chatbackInputOpen = 2;
				this.chatbackInput = "";
				this.redrawChatback = true;
				this.ptype = -1;
				return true;
			}

			if (this.ptype == 201) {
				// CHAT_FILTER_SETTINGS
				this.chatPublicMode = this.in.g1();
				this.chatPrivateMode = this.in.g1();
				this.chatTradeMode = this.in.g1();
				this.redrawPrivacySettings = true;
				this.redrawChatback = true;
				this.ptype = -1;
				return true;
			}

			if (this.ptype == 199) {
				// HINT_ARROW
				this.hintType = this.in.g1();
				if (this.hintType == 1) {
					this.hintNpc = this.in.g2();
				}
				if (this.hintType >= 2 && this.hintType <= 6) {
					if (this.hintType == 2) {
						this.hintOffsetX = 64;
						this.hintOffsetZ = 64;
					}
					if (this.hintType == 3) {
						this.hintOffsetX = 0;
						this.hintOffsetZ = 64;
					}
					if (this.hintType == 4) {
						this.hintOffsetX = 128;
						this.hintOffsetZ = 64;
					}
					if (this.hintType == 5) {
						this.hintOffsetX = 64;
						this.hintOffsetZ = 0;
					}
					if (this.hintType == 6) {
						this.hintOffsetX = 64;
						this.hintOffsetZ = 128;
					}
					this.hintType = 2;
					this.hintTileX = this.in.g2();
					this.hintTileZ = this.in.g2();
					this.hintHeight = this.in.g1();
				}
				if (this.hintType == 10) {
					this.hintPlayer = this.in.g2();
				}
				this.ptype = -1;
				return true;
			}

			if (this.ptype == 167) {
				// CAM_LOOKAT
				this.cutscene = true;
				this.cutsceneDstLocalTileX = this.in.g1();
				this.cutsceneDstLocalTileZ = this.in.g1();
				this.cutsceneDstHeight = this.in.g2();
				this.field292 = this.in.g1();
				this.field293 = this.in.g1();
				if (this.field293 >= 100) {
					int var36 = this.cutsceneDstLocalTileX * 128 + 64;
					int var37 = this.cutsceneDstLocalTileZ * 128 + 64;
					int var38 = this.getHeightmapY(var37, var36, this.currentLevel) - this.cutsceneDstHeight;
					int var39 = var36 - this.cameraX;
					int var40 = var38 - this.cameraY;
					int var41 = var37 - this.cameraZ;
					int var42 = (int) Math.sqrt((double) (var39 * var39 + var41 * var41));
					this.cameraPitch = (int) (Math.atan2((double) var40, (double) var42) * 325.949D) & 0x7FF;
					this.cameraYaw = (int) (Math.atan2((double) var39, (double) var41) * -325.949D) & 0x7FF;
					if (this.cameraPitch < 128) {
						this.cameraPitch = 128;
					}
					if (this.cameraPitch > 383) {
						this.cameraPitch = 383;
					}
				}
				this.ptype = -1;
				return true;
			}

			if (this.ptype == 5) {
				// LOGOUT
				this.logout();
				this.ptype = -1;
				return false;
			}

			if (this.ptype == 115) {
				// VARP_LARGE
				int var43 = this.in.g4_alt3();
				int var44 = this.in.g2_alt1();
				this.varCache[var44] = var43;
				if (this.varps[var44] != var43) {
					this.varps[var44] = var43;
					this.updateVarp(var44);
					this.redrawSidebar = true;
					if (this.stickyChatInterfaceId != -1) {
						this.redrawChatback = true;
					}
				}
				this.ptype = -1;
				return true;
			}

			if (this.ptype == 29) {
				// IF_CLOSE
				if (this.sidebarInterfaceId != -1) {
					this.unloadCom(this.sidebarInterfaceId);
					this.sidebarInterfaceId = -1;
					this.redrawSidebar = true;
					this.redrawSideicons = true;
				}
				if (this.chatInterfaceId != -1) {
					this.unloadCom(this.chatInterfaceId);
					this.chatInterfaceId = -1;
					this.redrawChatback = true;
				}
				if (this.fullscreenInterfaceId0 != -1) {
					this.unloadCom(this.fullscreenInterfaceId0);
					this.fullscreenInterfaceId0 = -1;
					this.redrawFrame = true;
				}
				if (this.fullscreenInterfaceId1 != -1) {
					this.unloadCom(this.fullscreenInterfaceId1);
					this.fullscreenInterfaceId1 = -1;
				}
				if (this.viewportInterfaceId != -1) {
					this.unloadCom(this.viewportInterfaceId);
					this.viewportInterfaceId = -1;
				}
				if (this.chatbackInputOpen != 0) {
					this.chatbackInputOpen = 0;
					this.redrawChatback = true;
				}
				this.pressedContinueOption = false;
				this.ptype = -1;
				return true;
			}

			if (this.ptype == 76) {
				// LAST_LOGIN_INFO
				this.daysSincePasswordChanged = this.in.g2_alt1();
				this.field371 = this.in.g2_alt3();
				this.in.g2();
				this.field504 = this.in.g2();
				this.currentDay = this.in.g2_alt1();
				this.unreadMessageCount = this.in.g2_alt2();
				this.previousLoginDay = this.in.g2_alt2();
				this.daysOfMembersRemaining = this.in.g2();
				this.lastAddress = this.in.g4_alt1();
				this.recoveriesLastChangedDay = this.in.g2_alt3();
				this.in.g1_alt1();
				signlink.dnslookup(JString.formatIPv4(this.lastAddress));
				this.ptype = -1;
				return true;
			}

			if (this.ptype == 63) {
				// MESSAGE_GAME
				String var45 = this.in.gjstr();
				if (var45.endsWith(":tradereq:")) {
					String var46 = var45.substring(0, var45.indexOf(":"));
					long var47 = JString.toBase37(var46);
					boolean var49 = false;
					for (int var50 = 0; var50 < this.ignoreCount; var50++) {
						if (this.ignoreName37[var50] == var47) {
							var49 = true;
							break;
						}
					}
					if (!var49 && this.overrideChat == 0) {
						this.addMessage(var46, "wishes to trade with you.", 4);
					}
				} else if (var45.endsWith(":duelreq:")) {
					String var51 = var45.substring(0, var45.indexOf(":"));
					long var52 = JString.toBase37(var51);
					boolean var54 = false;
					for (int var55 = 0; var55 < this.ignoreCount; var55++) {
						if (this.ignoreName37[var55] == var52) {
							var54 = true;
							break;
						}
					}
					if (!var54 && this.overrideChat == 0) {
						this.addMessage(var51, "wishes to duel with you.", 8);
					}
				} else if (var45.endsWith(":chalreq:")) {
					String var56 = var45.substring(0, var45.indexOf(":"));
					long var57 = JString.toBase37(var56);
					boolean var59 = false;
					for (int var60 = 0; var60 < this.ignoreCount; var60++) {
						if (this.ignoreName37[var60] == var57) {
							var59 = true;
							break;
						}
					}
					if (!var59 && this.overrideChat == 0) {
						String var61 = var45.substring(var45.indexOf(":") + 1, var45.length() - 9);
						this.addMessage(var56, var61, 8);
					}
				} else {
					this.addMessage("", var45, 0);
				}
				this.ptype = -1;
				return true;
			}

			if (this.ptype == 50) {
				// IF_OPENOVERLAY
				int var62 = this.in.g2b();
				if (var62 >= 0) {
					this.resetInterfaceAnimation(var62);
				}
				if (this.viewportOverlayInterfaceId != var62) {
					this.unloadCom(this.viewportOverlayInterfaceId);
					this.viewportOverlayInterfaceId = var62;
				}
				this.ptype = -1;
				return true;
			}

			if (this.ptype == 82) {
				// IF_SETHIDE
				boolean var63 = this.in.g1() == 1;
				int var64 = this.in.g2();
				Component.get(var64).hide = var63;
				this.ptype = -1;
				return true;
			}

			if (this.ptype == 174) {
				// UPDATE_RUNWEIGHT
				if (this.selectedTab == 12) {
					this.redrawSidebar = true;
				}
				this.runweight = this.in.g2b();
				this.ptype = -1;
				return true;
			}

			if (this.ptype == 233) {
				// SET_MULTIWAY
				this.inMultizone = this.in.g1();
				this.ptype = -1;
				return true;
			}

			if (this.ptype == 61) {
				// UNSET_MAP_FLAG
				this.flagSceneTileX = 0;
				this.ptype = -1;
				return true;
			}

			if (this.ptype == 128) {
				// IF_OPENMAIN_SIDE
				int var65 = this.in.g2_alt2();
				int var66 = this.in.g2_alt3();
				if (this.chatInterfaceId != -1) {
					this.unloadCom(this.chatInterfaceId);
					this.chatInterfaceId = -1;
					this.redrawChatback = true;
				}
				if (this.fullscreenInterfaceId0 != -1) {
					this.unloadCom(this.fullscreenInterfaceId0);
					this.fullscreenInterfaceId0 = -1;
					this.redrawFrame = true;
				}
				if (this.fullscreenInterfaceId1 != -1) {
					this.unloadCom(this.fullscreenInterfaceId1);
					this.fullscreenInterfaceId1 = -1;
				}
				if (this.viewportInterfaceId != var65) {
					this.unloadCom(this.viewportInterfaceId);
					this.viewportInterfaceId = var65;
				}
				if (this.sidebarInterfaceId != var66) {
					this.unloadCom(this.sidebarInterfaceId);
					this.sidebarInterfaceId = var66;
				}
				if (this.chatbackInputOpen != 0) {
					this.chatbackInputOpen = 0;
					this.redrawChatback = true;
				}
				this.redrawSidebar = true;
				this.redrawSideicons = true;
				this.pressedContinueOption = false;
				this.ptype = -1;
				return true;
			}

			if (this.ptype == 67) {
				// CAM_SHAKE
				int var67 = this.in.g1();
				int var68 = this.in.g1();
				int var69 = this.in.g1();
				int var70 = this.in.g1();
				this.cameraModifierEnabled[var67] = true;
				this.cameraModifierJitter[var67] = var68;
				this.cameraModifierWobbleScale[var67] = var69;
				this.cameraModifierWobbleSpeed[var67] = var70;
				this.cameraModifierCycle[var67] = 0;
				this.ptype = -1;
				return true;
			}

			if (this.ptype == 134) {
				// UPDATE_INV_PARTIAL
				this.redrawSidebar = true;
				int var71 = this.in.g2();
				Component var72 = Component.get(var71);
				while (this.in.pos < this.psize) {
					int var73 = this.in.gsmarts();
					int var74 = this.in.g2();
					int var75 = this.in.g1();
					if (var75 == 255) {
						var75 = this.in.g4();
					}
					if (var73 >= 0 && var73 < var72.invSlotObjId.length) {
						var72.invSlotObjId[var73] = var74;
						var72.invSlotObjCount[var73] = var75;
					}
				}
				if (var71 == this.bankGridCom && this.bankSearchText.length() > 0) {
					this.applyBankSearch();
				}
				this.ptype = -1;
				return true;
			}

			if (this.ptype == 78) {
				// UPDATE_FRIENDLIST
				long var76 = this.in.g8();
				int var78 = this.in.g1();
				String var79 = JString.formatDisplayName(JString.fromBase37(var76));
				for (int var80 = 0; var80 < this.friendCount; var80++) {
					if (this.friendName37[var80] == var76) {
						if (this.friendWorld[var80] != var78) {
							this.friendWorld[var80] = var78;
							this.redrawSidebar = true;
							if (var78 > 0) {
								this.addMessage("", var79 + " has logged in.", 5);
							}
							if (var78 == 0) {
								this.addMessage("", var79 + " has logged out.", 5);
							}
						}
						var79 = null;
						break;
					}
				}
				if (var79 != null && this.friendCount < 200) {
					this.friendName37[this.friendCount] = var76;
					this.friendName[this.friendCount] = var79;
					this.friendWorld[this.friendCount] = var78;
					this.friendCount++;
					this.redrawSidebar = true;
				}
				boolean var81 = false;
				while (!var81) {
					var81 = true;
					for (int var82 = 0; var82 < this.friendCount - 1; var82++) {
						if (this.friendWorld[var82] != nodeId && this.friendWorld[var82 + 1] == nodeId || this.friendWorld[var82] == 0 && this.friendWorld[var82 + 1] != 0) {
							int var83 = this.friendWorld[var82];
							this.friendWorld[var82] = this.friendWorld[var82 + 1];
							this.friendWorld[var82 + 1] = var83;
							String var84 = this.friendName[var82];
							this.friendName[var82] = this.friendName[var82 + 1];
							this.friendName[var82 + 1] = var84;
							long var85 = this.friendName37[var82];
							this.friendName37[var82] = this.friendName37[var82 + 1];
							this.friendName37[var82 + 1] = var85;
							this.redrawSidebar = true;
							var81 = false;
						}
					}
				}
				this.ptype = -1;
				return true;
			}

			if (this.ptype == 9) {
				// P_DIALOGPROMPT (custom)
				this.pendingDialogPrompt = this.in.gjstr();
				this.ptype = -1;
				return true;
			}

			if (this.ptype == 58) {
				// P_COUNTDIALOG
				this.dialogPrompt = this.pendingDialogPrompt;
				this.pendingDialogPrompt = null;
				this.showSocialInput = false;
				this.chatbackInputOpen = 1;
				this.chatbackInput = "";
				this.redrawChatback = true;
				this.ptype = -1;
				return true;
			}

			if (this.ptype == 252) {
				// IF_SETTAB_ACTIVE
				this.selectedTab = this.in.g1_alt2();
				this.redrawSidebar = true;
				this.redrawSideicons = true;
				this.ptype = -1;
				return true;
			}

			if (this.ptype == 40) {
				// UPDATE_ZONE_FULL_FOLLOWS
				this.baseZ = this.in.g1_alt3();
				this.baseX = this.in.g1_alt2();
				for (int var87 = this.baseX; var87 < this.baseX + 8; var87++) {
					for (int var88 = this.baseZ; var88 < this.baseZ + 8; var88++) {
						if (this.objStacks[this.currentLevel][var87][var88] != null) {
							this.objStacks[this.currentLevel][var87][var88] = null;
							this.sortObjStacks(var87, var88);
						}
					}
				}
				for (LocChange var89 = (LocChange) this.locChanges.head(); var89 != null; var89 = (LocChange) this.locChanges.next()) {
					if (var89.field1325 >= this.baseX && var89.field1325 < this.baseX + 8 && var89.field1326 >= this.baseZ && var89.field1326 < this.baseZ + 8 && this.currentLevel == var89.field1323) {
						var89.field1322 = 0;
					}
				}
				this.ptype = -1;
				return true;
			}

			if (this.ptype == 255) {
				// IF_SETPLAYERHEAD
				int var90 = this.in.g2_alt3();
				Component.get(var90).modelType = 3;
				if (localPlayer.field1679 == null) {
					Component.get(var90).model = (localPlayer.field1674[11] << 5) + (localPlayer.field1674[8] << 10) + (localPlayer.field1674[0] << 15) + (localPlayer.field1682[0] << 25) + (localPlayer.field1682[4] << 20) + localPlayer.field1674[1];
				} else {
					Component.get(var90).model = (int) (localPlayer.field1679.field1431 + 305419896L);
				}
				this.ptype = -1;
				return true;
			}

			if (this.ptype == 11) {
				// MESSAGE_CLAN (custom): from, channel name, message id, the sender's icons, then the words
				long from = this.in.g8();
				long channel = this.in.g8();
				this.in.g4();
				int icons = this.in.g1();
				boolean ignored = false;
				if ((icons & 0xF) <= 1) {
					for (int i = 0; i < this.ignoreCount; i++) {
						if (this.ignoreName37[i] == from) {
							ignored = true;
							break;
						}
					}
				}
				if (!ignored && this.overrideChat == 0) {
					String text = ChatText.unpack(this.in, this.psize - 21);
					this.addClanMessage(JString.formatDisplayName(JString.fromBase37(channel)), ChatIcons.forPlayer(icons) + JString.formatDisplayName(JString.fromBase37(from)), text);
				}
				this.ptype = -1;
				return true;
			}

			if (this.ptype == 12) {
				// UPDATE_CLANCHANNEL (custom): the whole channel, or owner 0 for none
				this.clanOwner37 = this.in.g8();
				this.clanCount = 0;
				if (this.clanOwner37 != 0L) {
					this.clanName37 = this.in.g8();
					this.clanKickRank = this.in.g1b();
					int count = this.in.g1();
					for (int i = 0; i < count; i++) {
						long name = this.in.g8();
						int world = this.in.g2();
						int rank = this.in.g1b();
						if (this.clanCount < this.clanMemberName37.length) {
							this.clanMemberName37[this.clanCount] = name;
							this.clanMemberWorld[this.clanCount] = world;
							this.clanMemberRank[this.clanCount] = rank;
							this.clanCount++;
						}
					}
				} else {
					this.clanName37 = 0L;
				}
				this.redrawSidebar = true;
				this.ptype = -1;
				return true;
			}

			if (this.ptype == 135) {
				// MESSAGE_PRIVATE
				long var91 = this.in.g8();
				int var93 = this.in.g4();
				int var94 = this.in.g1();
				boolean var95 = false;
				for (int var96 = 0; var96 < 100; var96++) {
					if (this.messageIds[var96] == var93) {
						var95 = true;
						break;
					}
				}
				if (var94 <= 1) {
					for (int var97 = 0; var97 < this.ignoreCount; var97++) {
						if (this.ignoreName37[var97] == var91) {
							var95 = true;
							break;
						}
					}
				}
				if (!var95 && this.overrideChat == 0) {
					try {
						this.messageIds[this.privateMessageCount] = var93;
						this.privateMessageCount = (this.privateMessageCount + 1) % 100;
						// QoL: remember who sent this so Tab can reply to them
						this.lastPmFrom37 = var91;
						this.hasLastPmFrom = true;
						String var98 = ChatText.unpack(this.in, this.psize - 13);
						// client-side profanity filter disabled per Corey's request
						//if (var94 != 3) {
						//	var98 = WordFilter.filter(var98);
						//}
						if (var94 >= 6) {
							this.addMessage("@cr8@" + JString.formatDisplayName(JString.fromBase37(var91)), var98, 7);
						} else if (var94 == 5) {
							this.addMessage("@cr7@" + JString.formatDisplayName(JString.fromBase37(var91)), var98, 7);
						} else if (var94 == 4) {
							this.addMessage("@cr6@" + JString.formatDisplayName(JString.fromBase37(var91)), var98, 7);
						} else if (var94 == 2 || var94 == 3) {
							this.addMessage("@cr2@" + JString.formatDisplayName(JString.fromBase37(var91)), var98, 7);
						} else if (var94 == 1) {
							this.addMessage("@cr1@" + JString.formatDisplayName(JString.fromBase37(var91)), var98, 7);
						} else {
							this.addMessage(JString.formatDisplayName(JString.fromBase37(var91)), var98, 3);
						}
					} catch (Exception var191) {
						signlink.reporterror("cde1");
					}
				}
				this.ptype = -1;
				return true;
			}

			if (this.ptype == 183) {
				// UPDATE_ZONE_PARTIAL_ENCLOSED
				this.baseX = this.in.g1();
				this.baseZ = this.in.g1_alt1();
				while (this.in.pos < this.psize) {
					int var100 = this.in.g1();
					this.readZonePacket(this.in, var100);
				}
				this.ptype = -1;
				return true;
			}

			if (this.ptype == 159) {
				// IF_OPENMAIN
				int var101 = this.in.g2_alt3();
				this.resetInterfaceAnimation(var101);
				if (this.sidebarInterfaceId != -1) {
					this.unloadCom(this.sidebarInterfaceId);
					this.sidebarInterfaceId = -1;
					this.redrawSidebar = true;
					this.redrawSideicons = true;
				}
				if (this.chatInterfaceId != -1) {
					this.unloadCom(this.chatInterfaceId);
					this.chatInterfaceId = -1;
					this.redrawChatback = true;
				}
				if (this.fullscreenInterfaceId0 != -1) {
					this.unloadCom(this.fullscreenInterfaceId0);
					this.fullscreenInterfaceId0 = -1;
					this.redrawFrame = true;
				}
				if (this.fullscreenInterfaceId1 != -1) {
					this.unloadCom(this.fullscreenInterfaceId1);
					this.fullscreenInterfaceId1 = -1;
				}
				if (this.viewportInterfaceId != var101) {
					this.unloadCom(this.viewportInterfaceId);
					this.viewportInterfaceId = var101;
				}
				if (this.chatbackInputOpen != 0) {
					this.chatbackInputOpen = 0;
					this.redrawChatback = true;
				}
				this.pressedContinueOption = false;
				this.ptype = -1;
				return true;
			}

			if (this.ptype == 246) {
				// IF_OPENSIDE
				int var102 = this.in.g2_alt3();
				this.resetInterfaceAnimation(var102);
				if (this.chatInterfaceId != -1) {
					this.unloadCom(this.chatInterfaceId);
					this.chatInterfaceId = -1;
					this.redrawChatback = true;
				}
				if (this.fullscreenInterfaceId0 != -1) {
					this.unloadCom(this.fullscreenInterfaceId0);
					this.fullscreenInterfaceId0 = -1;
					this.redrawFrame = true;
				}
				if (this.fullscreenInterfaceId1 != -1) {
					this.unloadCom(this.fullscreenInterfaceId1);
					this.fullscreenInterfaceId1 = -1;
				}
				if (this.viewportInterfaceId != -1) {
					this.unloadCom(this.viewportInterfaceId);
					this.viewportInterfaceId = -1;
				}
				if (this.sidebarInterfaceId != var102) {
					this.unloadCom(this.sidebarInterfaceId);
					this.sidebarInterfaceId = var102;
				}
				if (this.chatbackInputOpen != 0) {
					this.chatbackInputOpen = 0;
					this.redrawChatback = true;
				}
				this.redrawSidebar = true;
				this.redrawSideicons = true;
				this.pressedContinueOption = false;
				this.ptype = -1;
				return true;
			}

			if (this.ptype == 49) {
				// UPDATE_STAT
				this.redrawSidebar = true;
				int var103 = this.in.g1_alt2();
				int var104 = this.in.g1();
				int var105 = this.in.g4();
				// Plugins see every stat change; the XP drops plugin is one of them. Gained is 0
				// for each skill's first update of the session - login's sync of the whole
				// account - so nothing reads it as a gain. That guard is here rather than in a
				// plugin because it is about the packet, not about any one display of it.
				if (this.plugins != null) {
					this.plugins.onStatChanged(var103, var104, var105,
						this.xpDropStatSeen[var103] && var105 > this.skillExperience[var103]
							? var105 - this.skillExperience[var103]
							: 0);
				}
				this.xpDropStatSeen[var103] = true;
				this.skillExperience[var103] = var105;
				this.skillLevel[var103] = var104;
				this.skillBaseLevel[var103] = 1;
				for (int var106 = 0; var106 < 98; var106++) {
					if (var105 >= levelExperience[var106]) {
						this.skillBaseLevel[var103] = var106 + 2;
					}
				}
				this.ptype = -1;
				return true;
			}

			if (this.ptype == 206) {
				// UPDATE_INV_FULL
				this.redrawSidebar = true;
				int var107 = this.in.g2();
				Component var108 = Component.get(var107);
				int var109 = this.in.g2();
				for (int var110 = 0; var110 < var109; var110++) {
					var108.invSlotObjId[var110] = this.in.g2_alt3();
					int var111 = this.in.g1_alt2();
					if (var111 == 255) {
						var111 = this.in.g4_alt1();
					}
					var108.invSlotObjCount[var110] = var111;
				}
				for (int var112 = var109; var112 < var108.invSlotObjId.length; var112++) {
					var108.invSlotObjId[var112] = 0;
					var108.invSlotObjCount[var112] = 0;
				}
				if (var107 == this.bankGridCom && this.bankSearchText.length() > 0) {
					this.applyBankSearch();
				}
				this.ptype = -1;
				return true;
			}

			if (this.ptype == 222 || this.ptype == 53) {
				int var113 = this.sceneCenterZoneX;
				int var114 = this.sceneCenterZoneZ;
				if (this.ptype == 222) {
					// REBUILD_NORMAL
					var114 = this.in.g2();
					var113 = this.in.g2_alt3();
					this.sceneInstanced = false;
				}
				if (this.ptype == 53) {
					// REBUILD_REGION
					var113 = this.in.g2_alt2();
					this.in.accessBits();
					int var115 = 0;
					while (true) {
						if (var115 >= 4) {
							this.in.accessBytes();
							var114 = this.in.g2_alt2();
							this.sceneInstanced = true;
							break;
						}
						for (int var116 = 0; var116 < 13; var116++) {
							for (int var117 = 0; var117 < 13; var117++) {
								int var118 = this.in.gBit(1);
								if (var118 == 1) {
									this.sceneMapRegion[var115][var116][var117] = this.in.gBit(26);
								} else {
									this.sceneMapRegion[var115][var116][var117] = -1;
								}
							}
						}
						var115++;
					}
				}
				if (this.sceneCenterZoneX == var113 && this.sceneCenterZoneZ == var114 && this.sceneState == 2) {
					this.ptype = -1;
					return true;
				}
				this.sceneCenterZoneX = var113;
				this.sceneCenterZoneZ = var114;
				this.sceneBaseTileX = (this.sceneCenterZoneX - 6) * 8;
				this.sceneBaseTileZ = (this.sceneCenterZoneZ - 6) * 8;
				this.withinTutorialIsland = false;
				if ((this.sceneCenterZoneX / 8 == 48 || this.sceneCenterZoneX / 8 == 49) && this.sceneCenterZoneZ / 8 == 48) {
					this.withinTutorialIsland = true;
				}
				if (this.sceneCenterZoneX / 8 == 48 && this.sceneCenterZoneZ / 8 == 148) {
					this.withinTutorialIsland = true;
				}
				this.sceneState = 1;
				this.sceneLoadStartTime = System.currentTimeMillis();
				this.sceneLoadBegan = System.currentTimeMillis();
				this.sceneStuckShown = null;
				this.showPopupMessage(null, "Loading - please wait.");
				if (this.ptype == 222) {
					int var119 = 0;
					int var120 = (this.sceneCenterZoneX - 6) / 8;
					label1209: while (true) {
						if (var120 > (this.sceneCenterZoneX + 6) / 8) {
							this.sceneMapLandData = new byte[var119][];
							this.sceneMapLocData = new byte[var119][];
							this.sceneMapIndex = new int[var119];
							this.sceneMapLandFile = new int[var119];
							this.sceneMapLocFile = new int[var119];
							int var122 = 0;
							int var123 = (this.sceneCenterZoneX - 6) / 8;
							while (true) {
								if (var123 > (this.sceneCenterZoneX + 6) / 8) {
									break label1209;
								}
								for (int var124 = (this.sceneCenterZoneZ - 6) / 8; var124 <= (this.sceneCenterZoneZ + 6) / 8; var124++) {
									this.sceneMapIndex[var122] = (var123 << 8) + var124;
									if (this.withinTutorialIsland && (var124 == 49 || var124 == 149 || var124 == 147 || var123 == 50 || var123 == 49 && var124 == 47)) {
										this.sceneMapLandFile[var122] = -1;
										this.sceneMapLocFile[var122] = -1;
										var122++;
									} else {
										int var125 = this.sceneMapLandFile[var122] = this.onDemand.getMapFile(var123, var124, 0);
										if (var125 != -1) {
											this.onDemand.request(3, var125);
										}
										int var126 = this.sceneMapLocFile[var122] = this.onDemand.getMapFile(var123, var124, 1);
										if (var126 != -1) {
											this.onDemand.request(3, var126);
										}
										var122++;
									}
								}
								var123++;
							}
						}
						for (int var121 = (this.sceneCenterZoneZ - 6) / 8; var121 <= (this.sceneCenterZoneZ + 6) / 8; var121++) {
							var119++;
						}
						var120++;
					}
				}
				if (this.ptype == 53) {
					int var127 = 0;
					int[] var128 = new int[676];
					int var129 = 0;
					label1168: while (true) {
						if (var129 >= 4) {
							this.sceneMapLandData = new byte[var127][];
							this.sceneMapLocData = new byte[var127][];
							this.sceneMapIndex = new int[var127];
							this.sceneMapLandFile = new int[var127];
							this.sceneMapLocFile = new int[var127];
							int var137 = 0;
							while (true) {
								if (var137 >= var127) {
									break label1168;
								}
								int var138 = this.sceneMapIndex[var137] = var128[var137];
								int var139 = var138 >> 8 & 0xFF;
								int var140 = var138 & 0xFF;
								int var141 = this.sceneMapLandFile[var137] = this.onDemand.getMapFile(var139, var140, 0);
								if (var141 != -1) {
									this.onDemand.request(3, var141);
								}
								int var142 = this.sceneMapLocFile[var137] = this.onDemand.getMapFile(var139, var140, 1);
								if (var142 != -1) {
									this.onDemand.request(3, var142);
								}
								var137++;
							}
						}
						for (int var130 = 0; var130 < 13; var130++) {
							for (int var131 = 0; var131 < 13; var131++) {
								int var132 = this.sceneMapRegion[var129][var130][var131];
								if (var132 != -1) {
									int var133 = var132 >> 14 & 0x3FF;
									int var134 = var132 >> 3 & 0x7FF;
									int var135 = (var133 / 8 << 8) + var134 / 8;
									for (int var136 = 0; var136 < var127; var136++) {
										if (var128[var136] == var135) {
											var135 = -1;
											break;
										}
									}
									if (var135 != -1) {
										var128[var127++] = var135;
									}
								}
							}
						}
						var129++;
					}
				}
				int var143 = this.sceneBaseTileX - this.mapLastBaseX;
				int var144 = this.sceneBaseTileZ - this.mapLastBaseZ;
				this.mapLastBaseX = this.sceneBaseTileX;
				this.mapLastBaseZ = this.sceneBaseTileZ;
				for (int var145 = 0; var145 < 16384; var145++) {
					ClientNpc var146 = this.npcs[var145];
					if (var146 != null) {
						for (int var147 = 0; var147 < 10; var147++) {
							var146.routeTileX[var147] -= var143;
							var146.routeTileZ[var147] -= var144;
						}
						var146.field1157 -= var143 * 128;
						var146.field1158 -= var144 * 128;
					}
				}
				for (int var148 = 0; var148 < this.MAX_PLAYER_COUNT; var148++) {
					ClientPlayer var149 = this.players[var148];
					if (var149 != null) {
						for (int var150 = 0; var150 < 10; var150++) {
							var149.routeTileX[var150] -= var143;
							var149.routeTileZ[var150] -= var144;
						}
						var149.field1157 -= var143 * 128;
						var149.field1158 -= var144 * 128;
					}
				}
				this.awaitingSync = true;
				byte var151 = 0;
				byte var152 = 104;
				byte var153 = 1;
				if (var143 < 0) {
					var151 = 103;
					var152 = -1;
					var153 = -1;
				}
				byte var154 = 0;
				byte var155 = 104;
				byte var156 = 1;
				if (var144 < 0) {
					var154 = 103;
					var155 = -1;
					var156 = -1;
				}
				for (int var157 = var151; var157 != var152; var157 += var153) {
					for (int var158 = var154; var158 != var155; var158 += var156) {
						int var159 = var143 + var157;
						int var160 = var144 + var158;
						for (int var161 = 0; var161 < 4; var161++) {
							if (var159 >= 0 && var160 >= 0 && var159 < 104 && var160 < 104) {
								this.objStacks[var161][var157][var158] = this.objStacks[var161][var159][var160];
							} else {
								this.objStacks[var161][var157][var158] = null;
							}
						}
					}
				}
				for (LocChange var162 = (LocChange) this.locChanges.head(); var162 != null; var162 = (LocChange) this.locChanges.next()) {
					var162.field1325 -= var143;
					var162.field1326 -= var144;
					if (var162.field1325 < 0 || var162.field1326 < 0 || var162.field1325 >= 104 || var162.field1326 >= 104) {
						var162.unlink();
					}
				}
				if (this.flagSceneTileX != 0) {
					this.flagSceneTileX -= var143;
					this.flagSceneTileZ -= var144;
				}
				this.cutscene = false;
				this.ptype = -1;
				return true;
			}

			if (this.ptype == 190) {
				// UPDATE_REBOOT_TIMER
				this.systemUpdateTimer = this.in.g2_alt1() * 30;
				this.ptype = -1;
				return true;
			}

			if (this.ptype == 41 || this.ptype == 121 || this.ptype == 203 || this.ptype == 106 || this.ptype == 59 || this.ptype == 181 || this.ptype == 208 || this.ptype == 107 || this.ptype == 142 || this.ptype == 88 || this.ptype == 152) {
				this.readZonePacket(this.in, this.ptype);
				this.ptype = -1;
				return true;
			}

			if (this.ptype == 125) {
				// UPDATE_RUNENERGY
				if (this.selectedTab == 12) {
					this.redrawSidebar = true;
				}
				this.runenergy = this.in.g1();
				this.ptype = -1;
				return true;
			}

			if (this.ptype == 21) {
				// IF_SETOBJECT
				int var163 = this.in.g2();
				int var164 = this.in.g2_alt1();
				int var165 = this.in.g2_alt3();
				if (var164 == 65535) {
					Component.get(var165).modelType = 0;
					this.ptype = -1;
					return true;
				}
				ObjType var166 = ObjType.get(var164);
				Component.get(var165).modelType = 4;
				Component.get(var165).model = var164;
				Component.get(var165).xan = var166.field841;
				Component.get(var165).yan = var166.field838;
				Component.get(var165).zoom = var166.field851 * 100 / var163;
				this.ptype = -1;
				return true;
			}

			if (this.ptype == 3) {
				// CAM_MOVETO
				this.cutscene = true;
				this.cutsceneSrcLocalTileX = this.in.g1();
				this.cutsceneSrcLocalTileZ = this.in.g1();
				this.cutsceneSrcHeight = this.in.g2();
				this.cutsceneMoveSpeed = this.in.g1();
				this.cutsceneMoveAcceleration = this.in.g1();
				if (this.cutsceneMoveAcceleration >= 100) {
					this.cameraX = this.cutsceneSrcLocalTileX * 128 + 64;
					this.cameraZ = this.cutsceneSrcLocalTileZ * 128 + 64;
					this.cameraY = this.getHeightmapY(this.cameraZ, this.cameraX, this.currentLevel) - this.cutsceneSrcHeight;
				}
				this.ptype = -1;
				return true;
			}

			if (this.ptype == 2) {
				// IF_SETANIM
				int var167 = this.in.g2_alt3();
				int var168 = this.in.g2b_alt2();
				Component var169 = Component.get(var167);
				if (var169.anim != var168 || var168 == -1) {
					var169.anim = var168;
					var169.field717 = 0;
					var169.field709 = 0;
				}
				this.ptype = -1;
				return true;
			}

			if (this.ptype == 71) {
				// NPC_INFO
				this.getNpcPos(this.in, this.psize);
				this.ptype = -1;
				return true;
			}

			if (this.ptype == 226) {
				// UPDATE_IGNORELIST
				this.ignoreCount = this.psize / 8;
				for (int var170 = 0; var170 < this.ignoreCount; var170++) {
					this.ignoreName37[var170] = this.in.g8();
				}
				this.ptype = -1;
				return true;
			}

			if (this.ptype == 10) {
				// IF_SETTAB
				int var171 = this.in.g1_alt3();
				int var172 = this.in.g2_alt2();
				if (var172 == 65535) {
					var172 = -1;
				}
				if (this.tabInterfaceId[var171] != var172) {
					this.unloadCom(this.tabInterfaceId[var171]);
					this.tabInterfaceId[var171] = var172;
				}
				this.redrawSidebar = true;
				this.redrawSideicons = true;
				this.ptype = -1;
				return true;
			}

			if (this.ptype == 219) {
				// UPDATE_INV_STOP_TRANSMIT
				int var173 = this.in.g2_alt1();
				Component var174 = Component.get(var173);
				for (int var175 = 0; var175 < var174.invSlotObjId.length; var175++) {
					var174.invSlotObjId[var175] = -1;
					var174.invSlotObjId[var175] = 0;
				}
				this.ptype = -1;
				return true;
			}

			if (this.ptype == 238) {
				// TUT_FLASH
				this.flashingTab = this.in.g1();
				if (this.selectedTab == this.flashingTab) {
					if (this.flashingTab == 3) {
						this.selectedTab = 1;
					} else {
						this.selectedTab = 3;
					}
					this.redrawSidebar = true;
				}
				this.ptype = -1;
				return true;
			}

			if (this.ptype == 148) {
				// CAM_RESET
				this.cutscene = false;
				for (int var176 = 0; var176 < 5; var176++) {
					this.cameraModifierEnabled[var176] = false;
				}
				this.ptype = -1;
				return true;
			}

			if (this.ptype == 126) {
				// UPDATE_PID
				this.membersAccount = this.in.g1();
				this.localPid = this.in.g2_alt1();
				this.ptype = -1;
				return true;
			}

			if (this.ptype == 75) {
				// UPDATE_ZONE_PARTIAL_FOLLOWS
				this.baseX = this.in.g1_alt2();
				this.baseZ = this.in.g1_alt1();
				this.ptype = -1;
				return true;
			}

			if (this.ptype == 253) {
				// todo: opens fullscreen interface
				int var177 = this.in.g2_alt1();
				int var178 = this.in.g2_alt2();
				this.resetInterfaceAnimation(var178);
				if (var177 != -1) {
					this.resetInterfaceAnimation(var177);
				}
				if (this.viewportInterfaceId != -1) {
					this.unloadCom(this.viewportInterfaceId);
					this.viewportInterfaceId = -1;
				}
				if (this.sidebarInterfaceId != -1) {
					this.unloadCom(this.sidebarInterfaceId);
					this.sidebarInterfaceId = -1;
				}
				if (this.chatInterfaceId != -1) {
					this.unloadCom(this.chatInterfaceId);
					this.chatInterfaceId = -1;
				}
				if (this.fullscreenInterfaceId0 != var178) {
					this.unloadCom(this.fullscreenInterfaceId0);
					this.fullscreenInterfaceId0 = var178;
				}
				if (this.fullscreenInterfaceId1 != var178) {
					this.unloadCom(this.fullscreenInterfaceId1);
					this.fullscreenInterfaceId1 = var177;
				}
				this.chatbackInputOpen = 0;
				this.pressedContinueOption = false;
				this.ptype = -1;
				return true;
			}

			if (this.ptype == 251) {
				// FRIENDLIST_LOADED
				this.friendlistStatus = this.in.g1();
				this.redrawSidebar = true;
				this.ptype = -1;
				return true;
			}

			if (this.ptype == 18) {
				// todo
				int var179 = this.in.g2();
				int var180 = this.in.g2_alt2();
				int var181 = this.in.g2_alt1();
				Component.get(var180).field700 = (var179 << 16) + var181;
				this.ptype = -1;
				return true;
			}

			if (this.ptype == 90) {
				// PLAYER_INFO
				this.getPlayerPos(this.psize, this.in);
				// One of these a server cycle, which is what makes it the plugin system's game
				// tick - see jagex2.client.plugin.event.GameTick.
				if (this.plugins != null) {
					this.plugins.onGameTick();
				}
				this.awaitingSync = false;
				this.ptype = -1;
				return true;
			}

			if (this.ptype == 113) {
				// RESET_CLIENT_VARCACHE
				for (int var182 = 0; var182 < this.varps.length; var182++) {
					if (this.varps[var182] != this.varCache[var182]) {
						this.varps[var182] = this.varCache[var182];
						this.updateVarp(var182);
						this.redrawSidebar = true;
					}
				}
				this.ptype = -1;
				return true;
			}

			if (this.ptype == 232) {
				// IF_SETTEXT
				int var183 = this.in.g2_alt3();
				String var184 = this.in.gjstr();
				Component.get(var183).text = var184;
				int var10001 = this.tabInterfaceId[this.selectedTab];
				if (Component.get(var183).layer == var10001) {
					this.redrawSidebar = true;
				}
				this.ptype = -1;
				return true;
			}

			if (this.ptype == 8) {
				// IF_SETINVOP - replace one of an inv component's five right-click option strings.
				// The bank uses it to put the selected default quantity on the left click. Not an
				// official 377 opcode; 8 was free.
				int opCom = this.in.g2();
				int opIndex = this.in.g1();
				String opText = this.in.gjstr();
				Component opTarget = Component.get(opCom);
				if (opTarget != null && opTarget.iop != null && opIndex >= 1 && opIndex <= opTarget.iop.length) {
					opTarget.iop[opIndex - 1] = opText.length() == 0 ? null : opText;
				}
				this.ptype = -1;
				return true;
			}

			if (this.ptype == 7) {
				// IF_SETINVBREAKS - eight slot numbers at which the grid starts a fresh row, so the
				// bank's "all items" view breaks between tabs instead of running them together.
				int brkCom = this.in.g2();
				int[] brk = new int[8];
				for (int i = 0; i < 8; i++) {
					brk[i] = this.in.g2();
				}
				Component brkTarget = Component.get(brkCom);
				if (brkTarget != null) {
					brkTarget.invBreaks = brk;
					brkTarget.rebuildCellMap();
					Component brkParent = Component.get(brkTarget.layer);
					if (brkParent != null && brkParent.type == 0 && brkTarget.invCellSlot != null) {
						// the breaks push items further down, so the scroll extent has to grow with them
						int last = -1;
						for (int i = 0; i < brkTarget.invCellSlot.length; i++) {
							if (brkTarget.invCellSlot[i] >= 0) {
								last = i;
							}
						}
						int rows = last / brkTarget.width + 1;
						brkParent.scroll = brkTarget.field741 + rows * (brkTarget.marginY + 32);
						if (brkParent.scroll < brkParent.height) {
							brkParent.scroll = brkParent.height;
						}
						if (brkParent.field713 > brkParent.scroll - brkParent.height) {
							brkParent.field713 = brkParent.scroll - brkParent.height;
						}
					}
				}
				this.ptype = -1;
				return true;
			}

			if (this.ptype == 4) {
				// IF_SETINVWINDOW - show only part of a transmitted inv on a component.
				// count < 0 restores the whole inv. The parent layer's scroll extent is resized to
				// fit, or a three-item bank tab would still scroll through forty empty rows.
				int winCom = this.in.g2();
				int winFirst = this.in.g2();
				int winCount = this.in.g2();
				Component winTarget = Component.get(winCom);
				if (winTarget != null) {
					this.bankGridCom = winCom;
					winTarget.invWindowFirst = winFirst;
					winTarget.invWindowCount = winCount >= 0 && winCount < winTarget.width * winTarget.height ? winCount : -1;
					winTarget.rebuildCellMap();
					Component winParent = Component.get(winTarget.layer);
					if (winParent != null && winParent.type == 0) {
						int shown = winTarget.invWindowCount < 0 ? winTarget.width * winTarget.height : winTarget.invWindowCount;
						int rows = (shown + winTarget.width - 1) / winTarget.width;
						winParent.scroll = winTarget.field741 + rows * (winTarget.marginY + 32);
						if (winParent.scroll < winParent.height) {
							winParent.scroll = winParent.height;
						}
						if (winParent.field713 > winParent.scroll - winParent.height) {
							winParent.field713 = winParent.scroll - winParent.height;
						}
					}
				}
				this.ptype = -1;
				return true;
			}

			if (this.ptype == 200) {
				// IF_SETSCROLLPOS
				int var185 = this.in.g2();
				int var186 = this.in.g2_alt3();
				Component var187 = Component.get(var185);
				if (var187 != null && var187.type == 0) {
					if (var186 < 0) {
						var186 = 0;
					}
					if (var186 > var187.scroll - var187.height) {
						var186 = var187.scroll - var187.height;
					}
					var187.field713 = var186;
				}
				this.ptype = -1;
				return true;
			}

			signlink.reporterror("T1 - " + this.ptype + "," + this.psize + " - " + this.ptype1 + "," + this.ptype2);
			this.logout();
		} catch (IOException ignore) {
			this.tryReconnect();
		} catch (Exception ignore) {
			String errMsg = "T2 - " + this.ptype + "," + this.ptype1 + "," + this.ptype2 + " - " + this.psize + "," + (localPlayer.routeTileX[0] + this.sceneBaseTileX) + "," + (localPlayer.routeTileZ[0] + this.sceneBaseTileZ) + " - ";
			for (int i = 0; i < this.psize && i < 50; i++) {
				errMsg = errMsg + this.in.data[i] + ",";
			}
			signlink.reporterror(errMsg);
			this.logout();
		}

		return true;
	}

	@ObfuscatedName("client.b(LMFMVIYHT;II)V")
	public void readZonePacket(Packet arg0, int arg2) {
		if (arg2 == 203) {
			// LOC_MERGE
			int var4 = arg0.g2();
			int var5 = arg0.g1();
			int var6 = var5 >> 2;
			int var7 = var5 & 0x3;
			int var8 = this.LOC_SHAPE_TO_LAYER[var6];
			byte var9 = arg0.g1b_alt2();
			int var10 = arg0.g1_alt1();
			int var11 = (var10 >> 4 & 0x7) + this.baseX;
			int var12 = (var10 & 0x7) + this.baseZ;
			byte var13 = arg0.g1b_alt1();
			int var14 = arg0.g2_alt2();
			int var15 = arg0.g2_alt1();
			byte var16 = arg0.g1b();
			byte var17 = arg0.g1b_alt1();
			int var18 = arg0.g2();
			ClientPlayer var19;
			if (this.localPid == var15) {
				var19 = localPlayer;
			} else {
				var19 = this.players[var15];
			}
			if (var19 != null) {
				LocType var20 = LocType.method561(var4);
				int var21 = this.levelHeightmap[this.currentLevel][var11][var12];
				int var22 = this.levelHeightmap[this.currentLevel][var11 + 1][var12];
				int var23 = this.levelHeightmap[this.currentLevel][var11 + 1][var12 + 1];
				int var24 = this.levelHeightmap[this.currentLevel][var11][var12 + 1];
				Model var25 = var20.method569(var6, var7, var21, var22, var23, var24, -1);
				if (var25 != null) {
					this.appendLoc(this.currentLevel, var11, 0, var14 + 1, 0, -1, var18 + 1, var8, var12);
					var19.field1686 = loopCycle + var18;
					var19.field1687 = loopCycle + var14;
					var19.field1668 = var25;
					int var26 = var20.field1655;
					int var27 = var20.field1629;
					if (var7 == 1 || var7 == 3) {
						var26 = var20.field1629;
						var27 = var20.field1655;
					}
					var19.field1665 = var11 * 128 + var26 * 64;
					var19.field1667 = var12 * 128 + var27 * 64;
					var19.field1666 = this.getHeightmapY(var19.field1667, var19.field1665, this.currentLevel);
					if (var13 > var9) {
						byte var28 = var13;
						var13 = var9;
						var9 = var28;
					}
					if (var17 > var16) {
						byte var29 = var17;
						var17 = var16;
						var16 = var29;
					}
					var19.field1690 = var11 + var13;
					var19.field1692 = var9 + var11;
					var19.field1691 = var12 + var17;
					var19.field1693 = var12 + var16;
				}
			}
		}
		if (arg2 == 106) {
			// OBJ_REVEAL
			int var30 = arg0.g1_alt1();
			int var31 = (var30 >> 4 & 0x7) + this.baseX;
			int var32 = (var30 & 0x7) + this.baseZ;
			int var33 = arg0.g2_alt3();
			int var34 = arg0.g2_alt2();
			int var35 = arg0.g2_alt2();
			if (var31 >= 0 && var32 >= 0 && var31 < 104 && var32 < 104 && this.localPid != var35) {
				ClientObj var36 = new ClientObj();
				var36.field873 = var34;
				var36.field875 = var33;
				if (this.objStacks[this.currentLevel][var31][var32] == null) {
					this.objStacks[this.currentLevel][var31][var32] = new LinkList();
				}
				this.objStacks[this.currentLevel][var31][var32].push(var36);
				this.sortObjStacks(var31, var32);
			}
		} else if (arg2 == 142) {
			// LOC_ANIM
			int var37 = arg0.g2();
			int var38 = arg0.g1_alt1();
			int var39 = var38 >> 2;
			int var40 = var38 & 0x3;
			int var41 = this.LOC_SHAPE_TO_LAYER[var39];
			int var42 = arg0.g1();
			int var43 = (var42 >> 4 & 0x7) + this.baseX;
			int var44 = (var42 & 0x7) + this.baseZ;
			if (var43 >= 0 && var44 >= 0 && var43 < 103 && var44 < 103) {
				int var45 = this.levelHeightmap[this.currentLevel][var43][var44];
				int var46 = this.levelHeightmap[this.currentLevel][var43 + 1][var44];
				int var47 = this.levelHeightmap[this.currentLevel][var43 + 1][var44 + 1];
				int var48 = this.levelHeightmap[this.currentLevel][var43][var44 + 1];
				if (var41 == 0) {
					Wall var49 = this.scene.method296(this.currentLevel, var43, var44);
					if (var49 != null) {
						int var50 = var49.field1539 >> 14 & 0x7FFF;
						if (var39 == 2) {
							var49.field1537 = new ClientLocAnim(var37, var47, var48, var46, 2, var50, false, var45, var40 + 4);
							var49.field1538 = new ClientLocAnim(var37, var47, var48, var46, 2, var50, false, var45, var40 + 1 & 0x3);
						} else {
							var49.field1537 = new ClientLocAnim(var37, var47, var48, var46, var39, var50, false, var45, var40);
						}
					}
				}
				if (var41 == 1) {
					Decor var51 = this.scene.method297(this.currentLevel, var44, var43);
					if (var51 != null) {
						var51.field1411 = new ClientLocAnim(var37, var47, var48, var46, 4, var51.field1412 >> 14 & 0x7FFF, false, var45, 0);
					}
				}
				if (var41 == 2) {
					Sprite var52 = this.scene.method298(var43, var44, this.currentLevel);
					if (var39 == 11) {
						var39 = 10;
					}
					if (var52 != null) {
						var52.field80 = new ClientLocAnim(var37, var47, var48, var46, var39, var52.field88 >> 14 & 0x7FFF, false, var45, var40);
					}
				}
				if (var41 == 3) {
					GroundDecor var53 = this.scene.method299(this.currentLevel, var44, var43);
					if (var53 != null) {
						var53.field1313 = new ClientLocAnim(var37, var47, var48, var46, 22, var53.field1314 >> 14 & 0x7FFF, false, var45, var40);
					}
				}
			}
		} else if (arg2 == 107) {
			// OBJ_ADD
			int var54 = arg0.g2();
			int var55 = arg0.g1_alt2();
			int var56 = (var55 >> 4 & 0x7) + this.baseX;
			int var57 = (var55 & 0x7) + this.baseZ;
			int var58 = arg0.g2_alt2();
			if (var56 >= 0 && var57 >= 0 && var56 < 104 && var57 < 104) {
				ClientObj var59 = new ClientObj();
				var59.field873 = var54;
				var59.field875 = var58;
				if (this.objStacks[this.currentLevel][var56][var57] == null) {
					this.objStacks[this.currentLevel][var56][var57] = new LinkList();
				}
				this.objStacks[this.currentLevel][var56][var57].push(var59);
				this.sortObjStacks(var56, var57);
			}
		} else if (arg2 == 121) {
			// OBJ_COUNT
			int var60 = arg0.g1();
			int var61 = (var60 >> 4 & 0x7) + this.baseX;
			int var62 = (var60 & 0x7) + this.baseZ;
			int var63 = arg0.g2();
			int var64 = arg0.g2();
			int var65 = arg0.g2();
			if (var61 >= 0 && var62 >= 0 && var61 < 104 && var62 < 104) {
				LinkList var66 = this.objStacks[this.currentLevel][var61][var62];
				if (var66 != null) {
					for (ClientObj var67 = (ClientObj) var66.head(); var67 != null; var67 = (ClientObj) var66.next()) {
						if ((var63 & 0x7FFF) == var67.field873 && var67.field875 == var64) {
							var67.field875 = var65;
							break;
						}
					}
					this.sortObjStacks(var61, var62);
				}
			}
		} else if (arg2 == 181) {
			// MAP_PROJANIM
			int var68 = arg0.g1();
			int var69 = (var68 >> 4 & 0x7) + this.baseX;
			int var70 = (var68 & 0x7) + this.baseZ;
			int var71 = var69 + arg0.g1b();
			int var72 = var70 + arg0.g1b();
			int var73 = arg0.g2b();
			int var74 = arg0.g2();
			int var75 = arg0.g1() * 4;
			int var76 = arg0.g1() * 4;
			int var77 = arg0.g2();
			int var78 = arg0.g2();
			int var79 = arg0.g1();
			int var80 = arg0.g1();
			if (var69 >= 0 && var70 >= 0 && var69 < 104 && var70 < 104 && var71 >= 0 && var72 >= 0 && var71 < 104 && var72 < 104 && var74 != 65535) {
				int var81 = var69 * 128 + 64;
				int var82 = var70 * 128 + 64;
				int var83 = var71 * 128 + 64;
				int var84 = var72 * 128 + 64;
				ClientProj var85 = new ClientProj(this.currentLevel, var76, var80, var82, var74, loopCycle + var78, var79, var73, this.getHeightmapY(var82, var81, this.currentLevel) - var75, var81, loopCycle + var77);
				var85.method271(var83, var84, this.getHeightmapY(var84, var83, this.currentLevel) - var76, loopCycle + var77);
				this.projectiles.push(var85);
			}
		} else {
			if (arg2 == 41) {
				// SOUND_AREA
				int var86 = arg0.g1();
				int var87 = (var86 >> 4 & 0x7) + this.baseX;
				int var88 = (var86 & 0x7) + this.baseZ;
				int var89 = arg0.g2();
				int var90 = arg0.g1();
				int var91 = var90 >> 4 & 0xF;
				int var92 = var90 & 0x7;
				if (localPlayer.routeTileX[0] >= var87 - var91 && localPlayer.routeTileX[0] <= var87 + var91 && localPlayer.routeTileZ[0] >= var88 - var91 && localPlayer.routeTileZ[0] <= var88 + var91 && this.waveEnabled && !lowMem && this.waveCount < 50) {
					this.waveIds[this.waveCount] = var89;
					this.waveLoops[this.waveCount] = var92;
					this.waveDelay[this.waveCount] = Wave.field1472[var89];
					this.waveCount++;
				}
			}
			if (arg2 == 59) {
				// MAP_ANIM
				int var93 = arg0.g1();
				int var94 = (var93 >> 4 & 0x7) + this.baseX;
				int var95 = (var93 & 0x7) + this.baseZ;
				int var96 = arg0.g2();
				int var97 = arg0.g1();
				int var98 = arg0.g2();
				if (var94 >= 0 && var95 >= 0 && var94 < 104 && var95 < 104) {
					int var99 = var94 * 128 + 64;
					int var100 = var95 * 128 + 64;
					MapSpotAnim var101 = new MapSpotAnim(var99, this.currentLevel, this.getHeightmapY(var100, var99, this.currentLevel) - var97, var98, var96, loopCycle, var100, 10709);
					this.spotanims.push(var101);
				}
			} else if (arg2 == 152) {
				// LOC_ADD_CHANGE
				int var102 = arg0.g1_alt2();
				int var103 = var102 >> 2;
				int var104 = var102 & 0x3;
				int var105 = this.LOC_SHAPE_TO_LAYER[var103];
				int var106 = arg0.g2_alt3();
				int var107 = arg0.g1_alt1();
				int var108 = (var107 >> 4 & 0x7) + this.baseX;
				int var109 = (var107 & 0x7) + this.baseZ;
				if (var108 >= 0 && var109 >= 0 && var108 < 104 && var109 < 104) {
					this.appendLoc(this.currentLevel, var108, var104, -1, var103, var106, 0, var105, var109);
				}
			} else if (arg2 == 208) {
				// OBJ_DEL
				int var110 = arg0.g2_alt2();
				int var111 = arg0.g1_alt1();
				int var112 = (var111 >> 4 & 0x7) + this.baseX;
				int var113 = (var111 & 0x7) + this.baseZ;
				if (var112 >= 0 && var113 >= 0 && var112 < 104 && var113 < 104) {
					LinkList var114 = this.objStacks[this.currentLevel][var112][var113];
					if (var114 != null) {
						for (ClientObj var115 = (ClientObj) var114.head(); var115 != null; var115 = (ClientObj) var114.next()) {
							if ((var110 & 0x7FFF) == var115.field873) {
								var115.unlink();
								break;
							}
						}
						if (var114.head() == null) {
							this.objStacks[this.currentLevel][var112][var113] = null;
						}
						this.sortObjStacks(var112, var113);
					}
				}
			} else if (arg2 == 88) {
				// LOC_DEL
				int var116 = arg0.g1_alt3();
				int var117 = (var116 >> 4 & 0x7) + this.baseX;
				int var118 = (var116 & 0x7) + this.baseZ;
				int var119 = arg0.g1_alt3();
				int var120 = var119 >> 2;
				int var121 = var119 & 0x3;
				int var122 = this.LOC_SHAPE_TO_LAYER[var120];
				if (var117 >= 0 && var118 >= 0 && var117 < 104 && var118 < 104) {
					this.appendLoc(this.currentLevel, var117, var121, -1, var120, -1, 0, var122, var118);
				}
			}
		}
	}

	@ObfuscatedName("client.a(ZIIIIIIIII)V")
	public void appendLoc(int arg1, int arg2, int arg3, int arg4, int arg5, int arg6, int arg7, int arg8, int arg9) {
		LocChange var11 = null;
		for (LocChange var12 = (LocChange) this.locChanges.head(); var12 != null; var12 = (LocChange) this.locChanges.next()) {
			if (var12.field1323 == arg1 && var12.field1325 == arg2 && var12.field1326 == arg9 && var12.field1324 == arg8) {
				var11 = var12;
				break;
			}
		}
		if (var11 == null) {
			var11 = new LocChange();
			var11.field1323 = arg1;
			var11.field1324 = arg8;
			var11.field1325 = arg2;
			var11.field1326 = arg9;
			this.storeLoc(var11);
			this.locChanges.push(var11);
		}
		var11.field1316 = arg6;
		var11.field1318 = arg5;
		var11.field1317 = arg3;
		var11.field1327 = arg7;
		var11.field1322 = arg4;
	}

	@ObfuscatedName("client.a(BLNLLHDXXJ;)V")
	public void storeLoc(LocChange arg1) {
		int var3 = 0;
		int var4 = -1;
		int var5 = 0;
		int var6 = 0;
		if (arg1.field1324 == 0) {
			var3 = this.scene.method300(arg1.field1323, arg1.field1325, arg1.field1326);
		}
		if (arg1.field1324 == 1) {
			var3 = this.scene.method301(arg1.field1325, arg1.field1323, arg1.field1326);
		}
		if (arg1.field1324 == 2) {
			var3 = this.scene.method302(arg1.field1323, arg1.field1325, arg1.field1326);
		}
		if (arg1.field1324 == 3) {
			var3 = this.scene.method303(arg1.field1323, arg1.field1325, arg1.field1326);
		}
		if (var3 != 0) {
			int var7 = this.scene.method304(arg1.field1323, arg1.field1325, arg1.field1326, var3);
			var4 = var3 >> 14 & 0x7FFF;
			var5 = var7 & 0x1F;
			var6 = var7 >> 6;
		}
		arg1.field1319 = var4;
		arg1.field1321 = var5;
		arg1.field1320 = var6;
	}

	@ObfuscatedName("client.a(IIIIIIBI)V")
	public void addLoc(int arg0, int arg1, int arg2, int arg3, int arg4, int arg5, int arg7) {
		if (arg1 < 1 || arg3 < 1 || arg1 > 102 || arg3 > 102) {
			return;
		}
		if (lowMem && this.currentLevel != arg4) {
			return;
		}
		int var9 = 0;
		boolean var10 = true;
		boolean var11 = false;
		boolean var12 = false;
		if (arg7 == 0) {
			var9 = this.scene.method300(arg4, arg1, arg3);
		}
		if (arg7 == 1) {
			var9 = this.scene.method301(arg1, arg4, arg3);
		}
		if (arg7 == 2) {
			var9 = this.scene.method302(arg4, arg1, arg3);
		}
		if (arg7 == 3) {
			var9 = this.scene.method303(arg4, arg1, arg3);
		}
		if (var9 != 0) {
			int var13 = this.scene.method304(arg4, arg1, arg3, var9);
			int var14 = var9 >> 14 & 0x7FFF;
			int var15 = var13 & 0x1F;
			int var16 = var13 >> 6;
			if (arg7 == 0) {
				this.scene.method291(arg3, arg4, arg1);
				LocType var17 = LocType.method561(var14);
				if (var17.field1664) {
					this.levelCollisionMap[arg4].method537(var16, arg1, arg3, var15, var17.field1663);
				}
			}
			if (arg7 == 1) {
				this.scene.method292(false, arg1, arg3, arg4);
			}
			if (arg7 == 2) {
				this.scene.method293(arg3, arg4, arg1);
				LocType var18 = LocType.method561(var14);
				if (var18.field1655 + arg1 > 103 || var18.field1655 + arg3 > 103 || var18.field1629 + arg1 > 103 || var18.field1629 + arg3 > 103) {
					return;
				}
				if (var18.field1664) {
					this.levelCollisionMap[arg4].method538(arg3, arg1, var16, var18.field1629, var18.field1663, var18.field1655);
				}
			}
			if (arg7 == 3) {
				this.scene.method294(arg1, arg3, arg4);
				LocType var19 = LocType.method561(var14);
				if (var19.field1664 && var19.field1613) {
					this.levelCollisionMap[arg4].method540(arg1, arg3);
				}
			}
		}
		if (arg2 >= 0) {
			int var20 = arg4;
			if (arg4 < 3 && (this.levelTileFlags[1][arg1][arg3] & 0x2) == 2) {
				var20 = arg4 + 1;
			}
			World.method13(arg2, var20, arg5, arg3, this.levelCollisionMap[arg4], arg0, arg1, arg4, this.scene, this.levelHeightmap);
		}
	}

	@ObfuscatedName("client.b(II)V")
	public void sortObjStacks(int arg0, int arg1) {
		LinkList var3 = this.objStacks[this.currentLevel][arg0][arg1];
		if (var3 == null) {
			this.scene.method295(this.currentLevel, arg0, arg1);
			return;
		}
		int var4 = -99999999;
		ClientObj var5 = null;
		for (ClientObj var6 = (ClientObj) var3.head(); var6 != null; var6 = (ClientObj) var3.next()) {
			ObjType var11 = ObjType.get(var6.field873);
			int var12 = var11.field827;
			if (var11.field853) {
				var12 = (var6.field875 + 1) * var12;
			}
			if (var12 > var4) {
				var4 = var12;
				var5 = var6;
			}
		}
		var3.addHead(var5);
		ClientObj var7 = null;
		ClientObj var8 = null;
		for (ClientObj var9 = (ClientObj) var3.head(); var9 != null; var9 = (ClientObj) var3.next()) {
			if (var5.field873 != var9.field873 && var7 == null) {
				var7 = var9;
			}
			if (var5.field873 != var9.field873 && var7.field873 != var9.field873 && var8 == null) {
				var8 = var9;
			}
		}
		int var10 = (arg1 << 7) + arg0 + 1610612736;
		this.scene.method281(this.getHeightmapY(arg1 * 128 + 64, arg0 * 128 + 64, this.currentLevel), this.currentLevel, var5, var7, var10, var8, arg1, arg0);
	}

	@ObfuscatedName("client.a(IILMFMVIYHT;)V")
	public void getPlayerPos(int psize, Packet buf) {
		this.entityRemovalCount = 0;
		this.entityUpdateCount = 0;

		this.getPlayerLocal(psize, buf);
		this.getPlayerOldVis(psize, buf);
		this.getPlayerNewVis(psize, buf);
		this.getPlayerExtended(buf, psize);

		for (int i = 0; i < this.entityRemovalCount; i++) {
			int index = this.entityRemovalIds[i];
			if (loopCycle != this.players[index].cycle) {
				this.players[index] = null;
			}
		}

		if (buf.pos != psize) {
			signlink.reporterror("Error packet size mismatch in getplayer pos:" + buf.pos + " psize:" + psize);
			throw new RuntimeException("eek");
		}

		for (int i = 0; i < this.playerCount; i++) {
			if (this.players[this.playerIds[i]] == null) {
				signlink.reporterror(this.username + " null entry in pl list - pos:" + i + " size:" + this.playerCount);
				throw new RuntimeException("eek");
			}
		}
	}

	@ObfuscatedName("client.a(IZLMFMVIYHT;)V")
	public void getPlayerLocal(int psize, Packet buf) {
		buf.accessBits();

		int info = buf.gBit(1);
		if (info == 0) {
			return;
		}

		int op = buf.gBit(2);
		if (op == 0) {
			this.entityUpdateIds[this.entityUpdateCount++] = this.LOCAL_PLAYER_INDEX;
		} else if (op == 1) {
			int walkDir = buf.gBit(3);
			localPlayer.step(false, walkDir);

			int extendedInfo = buf.gBit(1);
			if (extendedInfo == 1) {
				this.entityUpdateIds[this.entityUpdateCount++] = this.LOCAL_PLAYER_INDEX;
			}
		} else if (op == 2) {
			int walkDir = buf.gBit(3);
			localPlayer.step(true, walkDir);

			int runDir = buf.gBit(3);
			localPlayer.step(true, runDir);

			int extendedInfo = buf.gBit(1);
			if (extendedInfo == 1) {
				this.entityUpdateIds[this.entityUpdateCount++] = this.LOCAL_PLAYER_INDEX;
			}
		} else if (op == 3) {
			int telejump = buf.gBit(1);
			this.currentLevel = buf.gBit(2);
			int z = buf.gBit(7);
			int x = buf.gBit(7);

			int extendedInfo = buf.gBit(1);
			if (extendedInfo == 1) {
				this.entityUpdateIds[this.entityUpdateCount++] = this.LOCAL_PLAYER_INDEX;
			}

			localPlayer.move(z, telejump == 1, x);
		}
	}

	@ObfuscatedName("client.b(IILMFMVIYHT;)V")
	public void getPlayerOldVis(int psize, Packet buf) {
		int count = buf.gBit(8);

		if (count < this.playerCount) {
			for (int i = count; i < this.playerCount; i++) {
				this.entityRemovalIds[this.entityRemovalCount++] = this.playerIds[i];
			}
		}

		if (count > this.playerCount) {
			signlink.reporterror(this.username + " Too many players");
			throw new RuntimeException("eek");
		}

		this.playerCount = 0;
		for (int i = 0; i < count; i++) {
			int index = this.playerIds[i];
			ClientPlayer player = this.players[index];

			int info = buf.gBit(1);
			if (info == 0) {
				this.playerIds[this.playerCount++] = index;
				player.cycle = loopCycle;
			} else {
				int op = buf.gBit(2);
				if (op == 0) {
					this.playerIds[this.playerCount++] = index;
					player.cycle = loopCycle;

					this.entityUpdateIds[this.entityUpdateCount++] = index;
				} else if (op == 1) {
					this.playerIds[this.playerCount++] = index;
					player.cycle = loopCycle;

					int walkDir = buf.gBit(3);
					player.step(false, walkDir);

					int extendedInfo = buf.gBit(1);
					if (extendedInfo == 1) {
						this.entityUpdateIds[this.entityUpdateCount++] = index;
					}
				} else if (op == 2) {
					this.playerIds[this.playerCount++] = index;
					player.cycle = loopCycle;

					int walkDir = buf.gBit(3);
					player.step(true, walkDir);

					int runDir = buf.gBit(3);
					player.step(true, runDir);

					int extendedInfo = buf.gBit(1);
					if (extendedInfo == 1) {
						this.entityUpdateIds[this.entityUpdateCount++] = index;
					}
				} else if (op == 3) {
					this.entityRemovalIds[this.entityRemovalCount++] = index;
				}
			}
		}
	}

	@ObfuscatedName("client.a(IBLMFMVIYHT;)V")
	public void getPlayerNewVis(int psize, Packet buf) {
		while (buf.bitPos + 10 < psize * 8) {
			int index = buf.gBit(11);
			if (index == 2047) {
				break;
			}

			if (this.players[index] == null) {
				this.players[index] = new ClientPlayer();

				if (this.playerAppearanceBuffer[index] != null) {
					this.players[index].method574(this.playerAppearanceBuffer[index]);
				}
			}

			this.playerIds[this.playerCount++] = index;
			ClientPlayer player = this.players[index];
			player.cycle = loopCycle;

			int dx = buf.gBit(5);
			if (dx > 15) {
				dx -= 32;
			}

			int extendedInfo = buf.gBit(1);
			if (extendedInfo == 1) {
				this.entityUpdateIds[this.entityUpdateCount++] = index;
			}

			int telejump = buf.gBit(1);

			int dz = buf.gBit(5);
			if (dz > 15) {
				dz -= 32;
			}

			player.move(localPlayer.routeTileZ[0] + dz, telejump == 1, localPlayer.routeTileX[0] + dx);
		}

		buf.accessBytes();
		this.ptype = -1;
	}

	@ObfuscatedName("client.a(ILMFMVIYHT;I)V")
	public void getPlayerExtended(Packet buf, int psize) {
		for (int i = 0; i < this.entityUpdateCount; i++) {
			int index = this.entityUpdateIds[i];
			ClientPlayer player = this.players[index];

			int flags = buf.g1();
			if ((flags & 0x20) != 0) {
				// BIG
				flags += buf.g1() << 8;
			}

			this.getPlayerExtendedInfo(index, player, flags, buf);
		}
	}

	@ObfuscatedName("client.a(IILZGNGQRPJ;ILMFMVIYHT;)V")
	public void getPlayerExtendedInfo(int arg1, ClientPlayer arg2, int arg3, Packet arg4) {
		if ((arg3 & 0x8) != 0) {
			// ANIM
			int var7 = arg4.g2();
			if (var7 == 65535) {
				var7 = -1;
			}
			int var8 = arg4.g1_alt3();
			if (arg2.field1171 == var7 && var7 != -1) {
				int var9 = SeqType.field775[var7].field789;
				if (var9 == 1) {
					arg2.field1172 = 0;
					arg2.field1173 = 0;
					arg2.field1174 = var8;
					arg2.field1175 = 0;
				}
				if (var9 == 2) {
					arg2.field1175 = 0;
				}
			} else if (var7 == -1 || arg2.field1171 == -1 || SeqType.field775[var7].field783 >= SeqType.field775[arg2.field1171].field783) {
				arg2.field1171 = var7;
				arg2.field1172 = 0;
				arg2.field1173 = 0;
				arg2.field1174 = var8;
				arg2.field1175 = 0;
				arg2.field1160 = arg2.field1180;
			}
		}
		if ((arg3 & 0x10) != 0) {
			// SAY
			arg2.chatMessage = arg4.gjstr();
			if (arg2.chatMessage.charAt(0) == '~') {
				arg2.chatMessage = arg2.chatMessage.substring(1);
				this.addMessage(arg2.name, arg2.chatMessage, 2);
			} else if (localPlayer == arg2) {
				this.addMessage(arg2.name, arg2.chatMessage, 2);
			}
			arg2.chatColour = 0;
			arg2.chatEffect = 0;
			arg2.chatTimer = 150;
		}
		if ((arg3 & 0x100) != 0) {
			// EXACTMOVE
			arg2.field1149 = arg4.g1_alt1();
			arg2.field1151 = arg4.g1_alt2();
			arg2.field1150 = arg4.g1_alt3();
			arg2.field1152 = arg4.g1();
			arg2.field1153 = arg4.g2() + loopCycle;
			arg2.field1154 = arg4.g2_alt2() + loopCycle;
			arg2.field1155 = arg4.g1();
			arg2.clearRoute();
		}
		if ((arg3 & 0x1) != 0) {
			// FACE_ENTITY
			arg2.field1156 = arg4.g2_alt2();
			if (arg2.field1156 == 65535) {
				arg2.field1156 = -1;
			}
		}
		if ((arg3 & 0x2) != 0) {
			// FACE_COORD
			arg2.field1145 = arg4.g2();
			arg2.field1146 = arg4.g2();
		}
		if ((arg3 & 0x200) != 0) {
			// SPOTANIM
			arg2.field1161 = arg4.g2_alt2();
			int var10 = arg4.g4_alt2();
			arg2.field1165 = var10 >> 16;
			arg2.field1164 = (var10 & 0xFFFF) + loopCycle;
			arg2.field1162 = 0;
			arg2.field1163 = 0;
			if (arg2.field1164 > loopCycle) {
				arg2.field1162 = -1;
			}
			if (arg2.field1161 == 65535) {
				arg2.field1161 = -1;
			}
		}
		if ((arg3 & 0x4) != 0) {
			// APPEARANCE
			int var11 = arg4.g1();
			byte[] var12 = new byte[var11];
			Packet var13 = new Packet(var12);
			arg4.gdata_alt1(var12, var11, 0);
			this.playerAppearanceBuffer[arg1] = var13;
			arg2.method574(var13);
		}
		if ((arg3 & 0x400) != 0) {
			// DAMAGE
			int var14 = arg4.g1_alt1();
			int var15 = arg4.g1_alt3();
			arg2.method353(loopCycle, var14, var15);
			arg2.field1142 = loopCycle + 300;
			arg2.field1143 = arg4.g1_alt2();
			arg2.field1144 = arg4.g1();
		}
		if ((arg3 & 0x40) != 0) {
			// CHAT
			int var16 = arg4.g2();
			int var17 = arg4.g1_alt2();
			int var18 = arg4.g1_alt1();
			int var19 = arg4.pos;
			if (arg2.name != null && arg2.field1680) {
				long var20 = JString.toBase37(arg2.name);
				boolean var22 = false;
				if (var17 <= 1) {
					for (int var23 = 0; var23 < this.ignoreCount; var23++) {
						if (this.ignoreName37[var23] == var20) {
							var22 = true;
							break;
						}
					}
				}
				if (!var22 && this.overrideChat == 0) {
					try {
						this.chatPacket.pos = 0;
						arg4.gdata_alt2(this.chatPacket.data, var18, 0);
						this.chatPacket.pos = 0;
						String var24 = ChatText.unpack(this.chatPacket, var18);
						String var25 = var24; // WordFilter.filter(var24) disabled per Corey's request (client-side profanity filter)
						arg2.chatMessage = var25;
						arg2.chatColour = var16 >> 8;
						arg2.chatEffect = var16 & 0xFF;
						arg2.chatTimer = 150;
						// The icons come from the appearance (crown and XP-mode badge). A server that does
						// not send them leaves only the chat mask's crown, which is what this drew before.
						String icons = arg2.icons.length() > 0 ? arg2.icons : ChatIcons.forPlayer(var17 == 3 ? 2 : var17);
						this.addMessage(icons + arg2.name, var25, var17 > 0 ? 1 : 2);
					} catch (Exception var29) {
						signlink.reporterror("cde2");
					}
				}
			}
			arg4.pos = var18 + var19;
		}
		if ((arg3 & 0x80) != 0) {
			// DAMAGE2
			int var27 = arg4.g1_alt3();
			int var28 = arg4.g1_alt2();
			arg2.method353(loopCycle, var27, var28);
			arg2.field1142 = loopCycle + 300;
			arg2.field1143 = arg4.g1_alt3();
			arg2.field1144 = arg4.g1();
		}
	}

	@ObfuscatedName("client.a(LMFMVIYHT;ZI)V")
	public void getNpcPos(Packet arg0, int arg2) {
		this.entityRemovalCount = 0;
		this.entityUpdateCount = 0;
		this.getNpcPosOldVis(arg2, arg0);
		this.getNpcPosNewVis(arg0, arg2);
		this.getNpcPosExtended(arg0, arg2);
		for (int var4 = 0; var4 < this.entityRemovalCount; var4++) {
			int var6 = this.entityRemovalIds[var4];
			if (loopCycle != this.npcs[var6].cycle) {
				this.npcs[var6].field1370 = null;
				this.npcs[var6] = null;
			}
		}
		if (arg0.pos != arg2) {
			signlink.reporterror(this.username + " size mismatch in getnpcpos - pos:" + arg0.pos + " psize:" + arg2);
			throw new RuntimeException("eek");
		}
		for (int var5 = 0; var5 < this.npcCount; var5++) {
			if (this.npcs[this.npcIds[var5]] == null) {
				signlink.reporterror(this.username + " null entry in npc list - pos:" + var5 + " size:" + this.npcCount);
				throw new RuntimeException("eek");
			}
		}
	}

	@ObfuscatedName("client.b(IBLMFMVIYHT;)V")
	public void getNpcPosOldVis(int arg0, Packet arg2) {
		arg2.accessBits();
		int var4 = arg2.gBit(8);
		if (var4 < this.npcCount) {
			for (int var5 = var4; var5 < this.npcCount; var5++) {
				this.entityRemovalIds[this.entityRemovalCount++] = this.npcIds[var5];
			}
		}
		if (var4 > this.npcCount) {
			signlink.reporterror(this.username + " Too many npcs");
			throw new RuntimeException("eek");
		}
		this.npcCount = 0;
		for (int var6 = 0; var6 < var4; var6++) {
			int var7 = this.npcIds[var6];
			ClientNpc var8 = this.npcs[var7];
			int var9 = arg2.gBit(1);
			if (var9 == 0) {
				this.npcIds[this.npcCount++] = var7;
				var8.cycle = loopCycle;
			} else {
				int var10 = arg2.gBit(2);
				if (var10 == 0) {
					this.npcIds[this.npcCount++] = var7;
					var8.cycle = loopCycle;
					this.entityUpdateIds[this.entityUpdateCount++] = var7;
				} else if (var10 == 1) {
					this.npcIds[this.npcCount++] = var7;
					var8.cycle = loopCycle;
					int var11 = arg2.gBit(3);
					var8.step(false, var11);
					int var12 = arg2.gBit(1);
					if (var12 == 1) {
						this.entityUpdateIds[this.entityUpdateCount++] = var7;
					}
				} else if (var10 == 2) {
					this.npcIds[this.npcCount++] = var7;
					var8.cycle = loopCycle;
					int var13 = arg2.gBit(3);
					var8.step(true, var13);
					int var14 = arg2.gBit(3);
					var8.step(true, var14);
					int var15 = arg2.gBit(1);
					if (var15 == 1) {
						this.entityUpdateIds[this.entityUpdateCount++] = var7;
					}
				} else if (var10 == 3) {
					this.entityRemovalIds[this.entityRemovalCount++] = var7;
				}
			}
		}
	}

	@ObfuscatedName("client.a(LMFMVIYHT;IZ)V")
	public void getNpcPosNewVis(Packet arg0, int arg1) {
		while (arg0.bitPos + 21 < arg1 * 8) {
			int var4 = arg0.gBit(14);
			if (var4 == 16383) {
				break;
			}
			if (this.npcs[var4] == null) {
				this.npcs[var4] = new ClientNpc();
			}
			ClientNpc var5 = this.npcs[var4];
			this.npcIds[this.npcCount++] = var4;
			var5.cycle = loopCycle;
			int var6 = arg0.gBit(1);
			if (var6 == 1) {
				this.entityUpdateIds[this.entityUpdateCount++] = var4;
			}
			int var7 = arg0.gBit(5);
			if (var7 > 15) {
				var7 -= 32;
			}
			int var8 = arg0.gBit(5);
			if (var8 > 15) {
				var8 -= 32;
			}
			int var9 = arg0.gBit(1);
			var5.field1370 = NpcType.get(arg0.gBit(13));
			// Get the model data moving the moment the npc appears, not the first time something
			// tries to draw it - see NpcType.requestModels.
			var5.field1370.requestModels();
			var5.field1148 = var5.field1370.field1445;
			var5.field1147 = var5.field1370.field1454;
			var5.field1166 = var5.field1370.field1448;
			var5.field1167 = var5.field1370.field1446;
			var5.field1168 = var5.field1370.field1444;
			var5.field1169 = var5.field1370.field1436;
			var5.field1181 = var5.field1370.field1424;
			var5.move(localPlayer.routeTileZ[0] + var7, var9 == 1, localPlayer.routeTileX[0] + var8);
		}
		arg0.accessBytes();
	}

	@ObfuscatedName("client.a(LMFMVIYHT;II)V")
	public void getNpcPosExtended(Packet arg0, int arg1) {
		for (int var5 = 0; var5 < this.entityUpdateCount; var5++) {
			int var6 = this.entityUpdateIds[var5];
			ClientNpc var7 = this.npcs[var6];
			int var8 = arg0.g1();
			if ((var8 & 0x1) != 0) {
				// CHANGETYPE
				var7.field1370 = NpcType.get(arg0.g2_alt2());
				// The one that matters for a boss that changes form: Zulrah's changetype arrives
				// while it is submerged on another level, so this gets the new colour's model a
				// couple of ticks' head start on the rise that draws it.
				var7.field1370.requestModels();
				var7.field1148 = var7.field1370.field1445;
				var7.field1147 = var7.field1370.field1454;
				var7.field1166 = var7.field1370.field1448;
				var7.field1167 = var7.field1370.field1446;
				var7.field1168 = var7.field1370.field1444;
				var7.field1169 = var7.field1370.field1436;
				var7.field1181 = var7.field1370.field1424;
			}
			if ((var8 & 0x40) != 0) {
				// FACE_ENTITY
				var7.field1156 = arg0.g2_alt1();
				if (var7.field1156 == 65535) {
					var7.field1156 = -1;
				}
			}
			if ((var8 & 0x80) != 0) {
				// DAMAGE
				int var9 = arg0.g1_alt1();
				int var10 = arg0.g1_alt1();
				var7.method353(loopCycle, var9, var10);
				var7.field1142 = loopCycle + 300;
				var7.field1143 = arg0.g1();
				var7.field1144 = arg0.g1_alt3();
			}
			if ((var8 & 0x4) != 0) {
				// SPOTANIM
				var7.field1161 = arg0.g2();
				int var11 = arg0.g4_alt2();
				var7.field1165 = var11 >> 16;
				var7.field1164 = (var11 & 0xFFFF) + loopCycle;
				var7.field1162 = 0;
				var7.field1163 = 0;
				if (var7.field1164 > loopCycle) {
					var7.field1162 = -1;
				}
				if (var7.field1161 == 65535) {
					var7.field1161 = -1;
				}
			}
			if ((var8 & 0x20) != 0) {
				// SAY
				var7.chatMessage = arg0.gjstr();
				var7.chatTimer = 100;
			}
			if ((var8 & 0x8) != 0) {
				// FACE_COORD
				var7.field1145 = arg0.g2_alt3();
				var7.field1146 = arg0.g2_alt1();
			}
			if ((var8 & 0x2) != 0) {
				// ANIM
				int var12 = arg0.g2();
				if (var12 == 65535) {
					var12 = -1;
				}
				int var13 = arg0.g1_alt3();
				if (var7.field1171 == var12 && var12 != -1) {
					int var14 = SeqType.field775[var12].field789;
					if (var14 == 1) {
						var7.field1172 = 0;
						var7.field1173 = 0;
						var7.field1174 = var13;
						var7.field1175 = 0;
					}
					if (var14 == 2) {
						var7.field1175 = 0;
					}
				} else if (var12 == -1 || var7.field1171 == -1 || SeqType.field775[var12].field783 >= SeqType.field775[var7.field1171].field783) {
					var7.field1171 = var12;
					var7.field1172 = 0;
					var7.field1173 = 0;
					var7.field1174 = var13;
					var7.field1175 = 0;
					var7.field1160 = var7.field1180;
				}
			}
			if ((var8 & 0x10) != 0) {
				// DAMAGE2
				int var15 = arg0.g1_alt3();
				int var16 = arg0.g1_alt3();
				var7.method353(loopCycle, var15, var16);
				var7.field1142 = loopCycle + 300;
				var7.field1143 = arg0.g1();
				var7.field1144 = arg0.g1_alt2();
			}
		}
	}

	@ObfuscatedName("client.B(I)V")
	public void showContextMenu() {
		// QoL: Shift + right-click opens a menu of settings for whatever is under the cursor
		// instead of actions to take. Done here, at the moment of the right-click, so the
		// every-frame menu is left alone - see buildSwapMenu(). Not gated on any one feature any
		// more: the rows come from the plugins that want them, and the menu does not open at all
		// when none of them offered anything.
		this.menuSwapMode = false;
		if (super.actionKey[GameShell.KEY_SHIFT] == 1) {
			this.buildSwapMenu();
		}
		int var2 = this.fontBold12.stringWidTag("Choose Option");
		for (int var3 = 0; var3 < this.menuSize; var3++) {
			int var11 = ChatIcons.width(this.fontBold12, this.menuOption[var3]);
			if (var11 > var2) {
				var2 = var11;
			}
		}
		var2 += 8;
		// THE HEIGHT IS NOW PER AREA, because it is capped to what the area can show. The number the
		// placement clamps against is MENU_CHROME_H now rather than 21: the original placed with
		// menuSize * 15 + 21 and drew with + 22, so a menu pushed against the bottom of its area
		// always drew one pixel past it. Harmless while the height was unbounded and the row was
		// clipped anyway; wrong now that the height is a deliberate fit. It used to be
		// menuSize * 15 + 21 everywhere, and a menu taller than its area had its y clamped to zero
		// and the rest of it drawn off the bottom, where the raster clips it: invisible rows that
		// cannot be clicked. Each area caps at its own height - 20 rows in the viewport, 15 in the
		// sidebar, 4 in the chatbox - and menuScroll moves the window.
		if (this.layout.inViewport(super.mouseClickX, super.mouseClickY)) {
			// A menu wider than its area used to be pushed off the left edge and clipped - "Uncharge
			// Trident of the Seas" in the 190-wide sidebar lost its first letters. The width is capped
			// to the area now, and drawMenu() shortens any row that does not fit with "...".
			// (The viewport is 512x334 on the fixed screen and the whole window when resizable.)
			var2 = Math.min(var2, this.layout.vpW);
			int rows0 = this.menuRowsFor(this.layout.vpH);
			int var4 = rows0 * MENU_ROW_H + MENU_CHROME_H;
			int var5 = super.mouseClickX - this.layout.vpX - var2 / 2;
			if (var2 + var5 > this.layout.vpW) {
				var5 = this.layout.vpW - var2;
			}
			if (var5 < 0) {
				var5 = 0;
			}
			int var6 = super.mouseClickY - this.layout.vpY;
			if (var4 + var6 > this.layout.vpH) {
				var6 = this.layout.vpH - var4;
			}
			if (var6 < 0) {
				var6 = 0;
			}
			this.menuVisible = true;
			this.menuArea = 0;
			this.menuX = var5;
			this.menuY = var6;
			this.menuWidth = var2;
			this.menuRowsShown = rows0;
			this.menuScroll = 0;
			this.menuHeight = rows0 * MENU_ROW_H + MENU_CHROME_H;
		}
		if (super.mouseClickX > SIDE_X && super.mouseClickY > 205 && super.mouseClickX < SIDE_X + 190 && super.mouseClickY < 466) {
			var2 = Math.min(var2, 190);
			int rows1 = this.menuRowsFor(261);
			int var4 = rows1 * MENU_ROW_H + MENU_CHROME_H;
			int var7 = super.mouseClickX - SIDE_X - var2 / 2;
			if (var7 < 0) {
				var7 = 0;
			} else if (var2 + var7 > 190) {
				var7 = 190 - var2;
			}
			int var8 = super.mouseClickY - 205;
			if (var8 < 0) {
				var8 = 0;
			} else if (var4 + var8 > 261) {
				var8 = 261 - var4;
			}
			this.menuVisible = true;
			this.menuArea = 1;
			this.menuX = var7;
			this.menuY = var8;
			this.menuWidth = var2;
			this.menuRowsShown = rows1;
			this.menuScroll = 0;
			this.menuHeight = rows1 * MENU_ROW_H + MENU_CHROME_H;
		}
		if (super.mouseClickX > CHAT_X && super.mouseClickY > CHAT_Y && super.mouseClickX < CHAT_X + CHAT_W && super.mouseClickY < CHAT_Y + CHAT_H) {
			var2 = Math.min(var2, CHAT_W);
			int rows2 = this.menuRowsFor(CHAT_H);
			int var4 = rows2 * MENU_ROW_H + MENU_CHROME_H;
			int var9 = super.mouseClickX - CHAT_X - var2 / 2;
			if (var9 < 0) {
				var9 = 0;
			} else if (var2 + var9 > CHAT_W) {
				var9 = CHAT_W - var2;
			}
			int var10 = super.mouseClickY - CHAT_Y;
			if (var10 < 0) {
				var10 = 0;
			} else if (var4 + var10 > CHAT_H) {
				var10 = CHAT_H - var4;
			}
			this.menuVisible = true;
			this.menuArea = 2;
			this.menuX = var9;
			this.menuY = var10;
			this.menuWidth = var2;
			this.menuRowsShown = rows2;
			this.menuScroll = 0;
			this.menuHeight = rows2 * MENU_ROW_H + MENU_CHROME_H;
		}
	}

	@ObfuscatedName("client.b(IB)Z")
	public boolean isAddFriendOption(int arg0) {
		if (arg0 < 0) {
			return false;
		}
		int var3 = this.menuAction[arg0];
		if (var3 >= 2000) {
			var3 -= 2000;
		}
		return var3 == 762;
	}

	@ObfuscatedName("client.h(II)V")
	public void useMenuOption(int arg0) {
		if (arg0 < 0) {
			return;
		}
		int var3 = this.menuParamB[arg0];
		int var4 = this.menuParamC[arg0];
		int var5 = this.menuAction[arg0];
		int var6 = this.menuParamA[arg0];
		// Plugins see the click before anything is sent, and may stop it - how a plugin guards an
		// action ("really drop that?"). Read from the raw action, before the 2000 offset below is
		// taken off, so a plugin matching on an action id sees the same number the menu carried.
		if (this.plugins != null && this.plugins.onMenuOptionClicked(this.menuOption[arg0], var5, var6, var3, var4)) {
			return;
		}
		if (var5 >= 2000) {
			var5 -= 2000;
		}
		// DEV: log every menu-driven interaction (walk, attack, use, examine, trade, bank, equip,
		// spell-on-target, drop, etc. all funnel through here) with its human-readable menu text,
		// the player's own world position, and (best-effort) the id/coords of whatever was clicked.
		DevLog.log("ACTION", DevLog.stripTags(this.menuOption[arg0])
			+ " | player=(" + (localPlayer.routeTileX[0] + this.sceneBaseTileX) + "," + (localPlayer.routeTileZ[0] + this.sceneBaseTileZ) + "," + this.currentLevel + ")"
			+ " | " + this.describeMenuTarget(var5, var6, var3, var4));
		// Bank search (chatbackInputOpen == 4) survives clicks: withdrawing from the results is the
		// whole point of it. The amount and name prompts still close on any action as they did.
		if (this.chatbackInputOpen != 0 && this.chatbackInputOpen != 4 && var5 != 1016) {
			this.chatbackInputOpen = 0;
			this.redrawChatback = true;
		}
		if (var5 == 200) {
			ClientPlayer var7 = this.players[var6];
			if (var7 != null) {
				this.tryMove(false, false, var7.routeTileZ[0], localPlayer.routeTileZ[0], 1, 1, 2, 0, var7.routeTileX[0], 0, 0, localPlayer.routeTileX[0]);
				this.crossX = super.mouseClickX;
				this.crossY = super.mouseClickY;
				this.crossMode = 2;
				this.crossCycle = 0;
				// OPPLAYER1
				this.out.p1isaac(245);
				this.out.p2_alt3(var6);
			}
		}
		if (var5 == 227) {
			oplogic2++;
			if (oplogic2 >= 62) {
				// ANTICHEAT_OPLOGIC2
				this.out.p1isaac(165);
				this.out.p1(206);
				oplogic2 = 0;
			}
			// OPHELD4
			this.out.p1isaac(228);
			this.out.p2_alt1(var3);
			this.out.p2_alt2(var6);
			this.out.p2(var4);
			this.selectedCycle = 0;
			this.selectedInterface = var4;
			this.selectedItem = var3;
			this.selectedArea = 2;
			if (Component.get(var4).layer == this.viewportInterfaceId) {
				this.selectedArea = 1;
			}
			if (Component.get(var4).layer == this.chatInterfaceId) {
				this.selectedArea = 3;
			}
		}
		if (var5 == 876) {
			ClientPlayer var8 = this.players[var6];
			if (var8 != null) {
				this.tryMove(false, false, var8.routeTileZ[0], localPlayer.routeTileZ[0], 1, 1, 2, 0, var8.routeTileX[0], 0, 0, localPlayer.routeTileX[0]);
				this.crossX = super.mouseClickX;
				this.crossY = super.mouseClickY;
				this.crossMode = 2;
				this.crossCycle = 0;
				// OPPLAYER5
				this.out.p1isaac(45);
				this.out.p2_alt2(var6);
			}
		}
		if (var5 == 921) {
			ClientNpc var9 = this.npcs[var6];
			if (var9 != null) {
				this.tryMove(false, false, var9.routeTileZ[0], localPlayer.routeTileZ[0], 1, 1, 2, 0, var9.routeTileX[0], 0, 0, localPlayer.routeTileX[0]);
				this.crossX = super.mouseClickX;
				this.crossY = super.mouseClickY;
				this.crossMode = 2;
				this.crossCycle = 0;
				// OPNPC2
				this.out.p1isaac(67);
				this.out.p2_alt2(var6);
			}
		}
		if (var5 == 961) {
			oplogic5 += var6;
			if (oplogic5 >= 115) {
				// ANTICHEAT_OPLOGIC5
				this.out.p1isaac(126);
				this.out.p1(125);
				oplogic5 = 0;
			}
			// OPHELD1
			this.out.p1isaac(203);
			this.out.p2_alt2(var4);
			this.out.p2_alt1(var3);
			this.out.p2_alt1(var6);
			this.selectedCycle = 0;
			this.selectedInterface = var4;
			this.selectedItem = var3;
			this.selectedArea = 2;
			if (Component.get(var4).layer == this.viewportInterfaceId) {
				this.selectedArea = 1;
			}
			if (Component.get(var4).layer == this.chatInterfaceId) {
				this.selectedArea = 3;
			}
		}
		if (var5 == 467 && this.interactWithLoc(var4, var3, var6)) {
			// OPLOCU
			this.out.p1isaac(152);
			this.out.p2_alt1(var6 >> 14 & 0x7FFF);
			this.out.p2_alt1(this.objSelectedInterface);
			this.out.p2_alt1(this.objInterface);
			this.out.p2_alt1(this.sceneBaseTileZ + var4);
			this.out.p2(this.objSelectedSlot);
			this.out.p2_alt3(this.sceneBaseTileX + var3);
		}
		if (var5 == 9) {
			// INV_BUTTON1
			this.out.p1isaac(3);
			this.out.p2_alt2(var6);
			this.out.p2(var4);
			this.out.p2(var3);
			this.selectedCycle = 0;
			this.selectedInterface = var4;
			this.selectedItem = var3;
			this.selectedArea = 2;
			if (Component.get(var4).layer == this.viewportInterfaceId) {
				this.selectedArea = 1;
			}
			if (Component.get(var4).layer == this.chatInterfaceId) {
				this.selectedArea = 3;
			}
		}
		if (var5 >= WEAROP_ACTION && var5 < WEAROP_ACTION + ObjType.WEAROP_COUNT) {
			// WEAROP (custom): worn option 1-8 of the item in the Worn Equipment tab
			this.out.p1isaac(153);
			this.out.p1(var5 - WEAROP_ACTION + 1);
			this.out.p2(var6);
			this.out.p2(var3);
			this.out.p2(var4);
			this.selectedCycle = 0;
			this.selectedInterface = var4;
			this.selectedItem = var3;
			this.selectedArea = 2;
		}
		if (var5 == 553) {
			ClientNpc var10 = this.npcs[var6];
			if (var10 != null) {
				this.tryMove(false, false, var10.routeTileZ[0], localPlayer.routeTileZ[0], 1, 1, 2, 0, var10.routeTileX[0], 0, 0, localPlayer.routeTileX[0]);
				this.crossX = super.mouseClickX;
				this.crossY = super.mouseClickY;
				this.crossMode = 2;
				this.crossCycle = 0;
				// OPNPC4
				this.out.p1isaac(42);
				this.out.p2_alt1(var6);
			}
		}
		if (var5 == 677) {
			ClientPlayer var11 = this.players[var6];
			if (var11 != null) {
				this.tryMove(false, false, var11.routeTileZ[0], localPlayer.routeTileZ[0], 1, 1, 2, 0, var11.routeTileX[0], 0, 0, localPlayer.routeTileX[0]);
				this.crossX = super.mouseClickX;
				this.crossY = super.mouseClickY;
				this.crossMode = 2;
				this.crossCycle = 0;
				// OPPLAYER4
				this.out.p1isaac(116);
				this.out.p2_alt1(var6);
			}
		}
		if (var5 == 762 || var5 == 574 || var5 == 775 || var5 == 859) {
			String var12 = this.menuOption[arg0];
			int var13 = var12.indexOf("@whi@");
			if (var13 != -1) {
				long var14 = JString.toBase37(var12.substring(var13 + 5).trim());
				if (var5 == 762) {
					this.addFriend(var14);
				}
				if (var5 == 574) {
					this.addIgnore(var14);
				}
				if (var5 == 775) {
					this.removeFriend(var14);
				}
				if (var5 == 859) {
					this.removeIgnore(var14);
				}
			}
		}
		if (var5 == 930) {
			boolean var16 = this.tryMove(false, false, var4, localPlayer.routeTileZ[0], 0, 0, 2, 0, var3, 0, 0, localPlayer.routeTileX[0]);
			if (!var16) {
				this.tryMove(false, false, var4, localPlayer.routeTileZ[0], 1, 1, 2, 0, var3, 0, 0, localPlayer.routeTileX[0]);
			}
			this.crossX = super.mouseClickX;
			this.crossY = super.mouseClickY;
			this.crossMode = 2;
			this.crossCycle = 0;
			// OPOBJ4
			this.out.p1isaac(54);
			this.out.p2_alt2(var6);
			this.out.p2_alt1(this.sceneBaseTileZ + var4);
			this.out.p2(this.sceneBaseTileX + var3);
		}
		if (var5 == 399) {
			// OPHELD2
			this.out.p1isaac(24);
			this.out.p2_alt1(var4);
			this.out.p2_alt1(var6);
			this.out.p2_alt2(var3);
			this.selectedCycle = 0;
			this.selectedInterface = var4;
			this.selectedItem = var3;
			this.selectedArea = 2;
			if (Component.get(var4).layer == this.viewportInterfaceId) {
				this.selectedArea = 1;
			}
			if (Component.get(var4).layer == this.chatInterfaceId) {
				this.selectedArea = 3;
			}
		}
		if (var5 == 347) {
			ClientNpc var18 = this.npcs[var6];
			if (var18 != null) {
				this.tryMove(false, false, var18.routeTileZ[0], localPlayer.routeTileZ[0], 1, 1, 2, 0, var18.routeTileX[0], 0, 0, localPlayer.routeTileX[0]);
				this.crossX = super.mouseClickX;
				this.crossY = super.mouseClickY;
				this.crossMode = 2;
				this.crossCycle = 0;
				// OPNPCU
				this.out.p1isaac(57);
				this.out.p2(var6);
				this.out.p2_alt1(this.objInterface);
				this.out.p2_alt3(this.objSelectedInterface);
				this.out.p2(this.objSelectedSlot);
			}
		}
		if (var5 == 890) {
			// IF_BUTTON
			this.out.p1isaac(79);
			this.out.p2(var4);
			Component var19 = Component.get(var4);
			if (var19.scripts != null && var19.scripts[0][0] == 5) {
				int var20 = var19.scripts[0][1];
				this.varps[var20] = 1 - this.varps[var20];
				this.updateVarp(var20);
				this.redrawSidebar = true;
			}
		}
		if (var5 == 493) {
			ClientPlayer var21 = this.players[var6];
			if (var21 != null) {
				this.tryMove(false, false, var21.routeTileZ[0], localPlayer.routeTileZ[0], 1, 1, 2, 0, var21.routeTileX[0], 0, 0, localPlayer.routeTileX[0]);
				this.crossX = super.mouseClickX;
				this.crossY = super.mouseClickY;
				this.crossMode = 2;
				this.crossCycle = 0;
				// OPPLAYER2
				this.out.p1isaac(233);
				this.out.p2_alt2(var6);
			}
		}
		if (var5 == 14) {
			if (this.menuVisible) {
				this.scene.method312(var3 - this.layout.vpX, var4 - this.layout.vpY);
			} else {
				this.scene.method312(super.mouseClickX - this.layout.vpX, super.mouseClickY - this.layout.vpY);
			}
		}
		if (var5 == 903) {
			// OPHELDU
			this.out.p1isaac(1);
			this.out.p2(var6);
			this.out.p2_alt1(this.objSelectedSlot);
			this.out.p2_alt1(this.objInterface);
			this.out.p2_alt3(this.objSelectedInterface);
			this.out.p2_alt2(var3);
			this.out.p2_alt2(var4);
			this.selectedCycle = 0;
			this.selectedInterface = var4;
			this.selectedItem = var3;
			this.selectedArea = 2;
			if (Component.get(var4).layer == this.viewportInterfaceId) {
				this.selectedArea = 1;
			}
			if (Component.get(var4).layer == this.chatInterfaceId) {
				this.selectedArea = 3;
			}
		}
		if (var5 == 361) {
			// OPHELDT
			this.out.p1isaac(36);
			this.out.p2(this.activeSpellId);
			this.out.p2_alt2(var4);
			this.out.p2_alt2(var3);
			this.out.p2_alt2(var6);
			this.selectedCycle = 0;
			this.selectedInterface = var4;
			this.selectedItem = var3;
			this.selectedArea = 2;
			if (Component.get(var4).layer == this.viewportInterfaceId) {
				this.selectedArea = 1;
			}
			if (Component.get(var4).layer == this.chatInterfaceId) {
				this.selectedArea = 3;
			}
		}
		if (var5 == 118) {
			ClientNpc var22 = this.npcs[var6];
			if (var22 != null) {
				this.tryMove(false, false, var22.routeTileZ[0], localPlayer.routeTileZ[0], 1, 1, 2, 0, var22.routeTileX[0], 0, 0, localPlayer.routeTileX[0]);
				this.crossX = super.mouseClickX;
				this.crossY = super.mouseClickY;
				this.crossMode = 2;
				this.crossCycle = 0;
				oplogic3 += var6;
				if (oplogic3 >= 143) {
					// ANTICHEAT_OPLOGIC3
					this.out.p1isaac(157);
					this.out.p4(0);
					oplogic3 = 0;
				}
				// OPNPC3
				this.out.p1isaac(13);
				this.out.p2_alt3(var6);
			}
		}
		if (var5 == 376 && this.interactWithLoc(var4, var3, var6)) {
			// OPLOCT
			this.out.p1isaac(210);
			this.out.p2(this.activeSpellId);
			this.out.p2_alt1(var6 >> 14 & 0x7FFF);
			this.out.p2_alt2(this.sceneBaseTileX + var3);
			this.out.p2_alt1(this.sceneBaseTileZ + var4);
		}
		if (var5 == 432) {
			ClientNpc var23 = this.npcs[var6];
			if (var23 != null) {
				this.tryMove(false, false, var23.routeTileZ[0], localPlayer.routeTileZ[0], 1, 1, 2, 0, var23.routeTileX[0], 0, 0, localPlayer.routeTileX[0]);
				this.crossX = super.mouseClickX;
				this.crossY = super.mouseClickY;
				this.crossMode = 2;
				this.crossCycle = 0;
				// OPNPC5
				this.out.p1isaac(8);
				this.out.p2_alt1(var6);
			}
		}
		if (var5 == 639) {
			this.closeInterfaces();
		}
		if (var5 == 918) {
			ClientPlayer var24 = this.players[var6];
			if (var24 != null) {
				this.tryMove(false, false, var24.routeTileZ[0], localPlayer.routeTileZ[0], 1, 1, 2, 0, var24.routeTileX[0], 0, 0, localPlayer.routeTileX[0]);
				this.crossX = super.mouseClickX;
				this.crossY = super.mouseClickY;
				this.crossMode = 2;
				this.crossCycle = 0;
				// OPPLAYERT
				this.out.p1isaac(31);
				this.out.p2(var6);
				this.out.p2_alt1(this.activeSpellId);
			}
		}
		if (var5 == 67) {
			ClientNpc var25 = this.npcs[var6];
			if (var25 != null) {
				this.tryMove(false, false, var25.routeTileZ[0], localPlayer.routeTileZ[0], 1, 1, 2, 0, var25.routeTileX[0], 0, 0, localPlayer.routeTileX[0]);
				this.crossX = super.mouseClickX;
				this.crossY = super.mouseClickY;
				this.crossMode = 2;
				this.crossCycle = 0;
				// OPNPCT
				this.out.p1isaac(104);
				this.out.p2_alt2(this.activeSpellId);
				this.out.p2_alt1(var6);
			}
		}
		if (var5 == 68) {
			boolean var26 = this.tryMove(false, false, var4, localPlayer.routeTileZ[0], 0, 0, 2, 0, var3, 0, 0, localPlayer.routeTileX[0]);
			if (!var26) {
				this.tryMove(false, false, var4, localPlayer.routeTileZ[0], 1, 1, 2, 0, var3, 0, 0, localPlayer.routeTileX[0]);
			}
			this.crossX = super.mouseClickX;
			this.crossY = super.mouseClickY;
			this.crossMode = 2;
			this.crossCycle = 0;
			// OPOBJ1
			this.out.p1isaac(77);
			this.out.p2_alt2(this.sceneBaseTileX + var3);
			this.out.p2(this.sceneBaseTileZ + var4);
			this.out.p2_alt3(var6);
		}
		if (var5 == 684) {
			boolean var28 = this.tryMove(false, false, var4, localPlayer.routeTileZ[0], 0, 0, 2, 0, var3, 0, 0, localPlayer.routeTileX[0]);
			if (!var28) {
				this.tryMove(false, false, var4, localPlayer.routeTileZ[0], 1, 1, 2, 0, var3, 0, 0, localPlayer.routeTileX[0]);
			}
			this.crossX = super.mouseClickX;
			this.crossY = super.mouseClickY;
			this.crossMode = 2;
			this.crossCycle = 0;
			if ((var6 & 0x3) == 0) {
				oplogic4++;
			}
			if (oplogic4 >= 84) {
				// ANTICHEAT_OPLOGIC4
				this.out.p1isaac(222);
				this.out.p3(11257922);
				oplogic4 = 0;
			}
			// OPOBJ3
			this.out.p1isaac(71);
			this.out.p2_alt3(var6);
			this.out.p2_alt3(this.sceneBaseTileX + var3);
			this.out.p2_alt2(this.sceneBaseTileZ + var4);
		}
		if (var5 == 544 || var5 == 695) {
			String var30 = this.menuOption[arg0];
			int var31 = var30.indexOf("@whi@");
			if (var31 != -1) {
				String var32 = var30.substring(var31 + 5).trim();
				String var33 = JString.formatDisplayName(JString.fromBase37(JString.toBase37(var32)));
				boolean var34 = false;
				for (int var35 = 0; var35 < this.playerCount; var35++) {
					ClientPlayer var36 = this.players[this.playerIds[var35]];
					if (var36 != null && var36.name != null && var36.name.equalsIgnoreCase(var33)) {
						this.tryMove(false, false, var36.routeTileZ[0], localPlayer.routeTileZ[0], 1, 1, 2, 0, var36.routeTileX[0], 0, 0, localPlayer.routeTileX[0]);
						if (var5 == 544) {
							// OPPLAYER4
							this.out.p1isaac(116);
							this.out.p2_alt1(this.playerIds[var35]);
						}
						if (var5 == 695) {
							// OPPLAYER1
							this.out.p1isaac(245);
							this.out.p2_alt3(this.playerIds[var35]);
						}
						var34 = true;
						break;
					}
				}
				if (!var34) {
					this.addMessage("", "Unable to find " + var33, 0);
				}
			}
		}
		if (var5 == 225) {
			// INV_BUTTON2
			this.out.p1isaac(177);
			this.out.p2_alt2(var3);
			this.out.p2_alt1(var6);
			this.out.p2_alt1(var4);
			this.selectedCycle = 0;
			this.selectedInterface = var4;
			this.selectedItem = var3;
			this.selectedArea = 2;
			if (Component.get(var4).layer == this.viewportInterfaceId) {
				this.selectedArea = 1;
			}
			if (Component.get(var4).layer == this.chatInterfaceId) {
				this.selectedArea = 3;
			}
		}
		if (var5 == 70) {
			Component var37 = Component.get(var4);
			this.spellSelected = 1;
			this.activeSpellId = var4;
			this.activeSpellFlags = var37.targetMask;
			this.objSelected = 0;
			this.redrawSidebar = true;
			String var38 = var37.targetVerb;
			if (var38.indexOf(" ") != -1) {
				var38 = var38.substring(0, var38.indexOf(" "));
			}
			String var39 = var37.targetVerb;
			if (var39.indexOf(" ") != -1) {
				var39 = var39.substring(var39.indexOf(" ") + 1);
			}
			this.spellCaption = var38 + " " + var37.targetText + " " + var39;
			if (this.activeSpellFlags == 16) {
				this.redrawSidebar = true;
				this.selectedTab = 3;
				this.redrawSideicons = true;
			}
			return;
		}
		if (var5 == 891) {
			// OPHELD5
			this.out.p1isaac(4);
			this.out.p2_alt1(var3);
			this.out.p2_alt3(var6);
			this.out.p2_alt3(var4);
			this.selectedCycle = 0;
			this.selectedInterface = var4;
			this.selectedItem = var3;
			this.selectedArea = 2;
			if (Component.get(var4).layer == this.viewportInterfaceId) {
				this.selectedArea = 1;
			}
			if (Component.get(var4).layer == this.chatInterfaceId) {
				this.selectedArea = 3;
			}
		}
		if (var5 == 894) {
			// INV_BUTTON5
			this.out.p1isaac(158);
			this.out.p2_alt3(var3);
			this.out.p2_alt3(var6);
			this.out.p2_alt1(var4);
			this.selectedCycle = 0;
			this.selectedInterface = var4;
			this.selectedItem = var3;
			this.selectedArea = 2;
			if (Component.get(var4).layer == this.viewportInterfaceId) {
				this.selectedArea = 1;
			}
			if (Component.get(var4).layer == this.chatInterfaceId) {
				this.selectedArea = 3;
			}
		}
		if (var5 == 1280) {
			this.interactWithLoc(var4, var3, var6);
			// OPLOC5
			this.out.p1isaac(55);
			this.out.p2_alt1(var6 >> 14 & 0x7FFF);
			this.out.p2_alt1(this.sceneBaseTileZ + var4);
			this.out.p2(this.sceneBaseTileX + var3);
		}
		if (var5 == 35) {
			this.interactWithLoc(var4, var3, var6);
			// OPLOC1
			this.out.p1isaac(181);
			this.out.p2_alt2(this.sceneBaseTileX + var3);
			this.out.p2_alt1(this.sceneBaseTileZ + var4);
			this.out.p2_alt1(var6 >> 14 & 0x7FFF);
		}
		if (var5 == 888) {
			this.interactWithLoc(var4, var3, var6);
			// OPLOC3
			this.out.p1isaac(50);
			this.out.p2_alt2(this.sceneBaseTileZ + var4);
			this.out.p2_alt1(var6 >> 14 & 0x7FFF);
			this.out.p2_alt3(this.sceneBaseTileX + var3);
		}
		if (var5 == 324) {
			// OPHELD3
			this.out.p1isaac(161);
			this.out.p2_alt3(var3);
			this.out.p2_alt3(var6);
			this.out.p2_alt1(var4);
			this.selectedCycle = 0;
			this.selectedInterface = var4;
			this.selectedItem = var3;
			this.selectedArea = 2;
			if (Component.get(var4).layer == this.viewportInterfaceId) {
				this.selectedArea = 1;
			}
			if (Component.get(var4).layer == this.chatInterfaceId) {
				this.selectedArea = 3;
			}
		}
		if (var5 == 1094) {
			ObjType var40 = ObjType.get(var6);
			Component var41 = Component.get(var4);
			String var42;
			if (var41 != null && var41.invSlotObjCount[var3] >= 100000) {
				var42 = var41.invSlotObjCount[var3] + " x " + var40.field811;
			} else if (var40.field810 == null) {
				var42 = "It's a " + var40.field811 + ".";
			} else {
				var42 = new String(var40.field810);
			}
			this.addMessage("", var42, 0);
		}
		if (var5 == 352) {
			Component var43 = Component.get(var4);
			boolean var44 = true;
			if (var43.clientCode > 0) {
				var44 = this.handleInterfaceAction(var43);
			}
			if (var44) {
				// IF_BUTTON
				this.out.p1isaac(79);
				this.out.p2(var4);
			}
		}
		if (var5 == 1412) {
			int var45 = var6 >> 14 & 0x7FFF;
			LocType var46 = LocType.method561(var45);
			String var47;
			if (var46.field1637 == null) {
				var47 = "It's a " + var46.field1630 + ".";
			} else {
				var47 = new String(var46.field1637);
			}
			this.addMessage("", var47, 0);
		}
		if (var5 == 575 && !this.pressedContinueOption) {
			// RESUME_PAUSEBUTTON
			this.out.p1isaac(226);
			this.out.p2(var4);
			this.pressedContinueOption = true;
		}
		if (var5 == 892) {
			this.interactWithLoc(var4, var3, var6);
			// OPLOC4
			this.out.p1isaac(136);
			this.out.p2(this.sceneBaseTileX + var3);
			this.out.p2_alt1(this.sceneBaseTileZ + var4);
			this.out.p2(var6 >> 14 & 0x7FFF);
		}
		if (var5 == 270) {
			boolean var48 = this.tryMove(false, false, var4, localPlayer.routeTileZ[0], 0, 0, 2, 0, var3, 0, 0, localPlayer.routeTileX[0]);
			if (!var48) {
				this.tryMove(false, false, var4, localPlayer.routeTileZ[0], 1, 1, 2, 0, var3, 0, 0, localPlayer.routeTileX[0]);
			}
			this.crossX = super.mouseClickX;
			this.crossY = super.mouseClickY;
			this.crossMode = 2;
			this.crossCycle = 0;
			// OPOBJ5
			this.out.p1isaac(230);
			this.out.p2_alt1(var6);
			this.out.p2_alt2(this.sceneBaseTileX + var3);
			this.out.p2(this.sceneBaseTileZ + var4);
		}
		if (var5 == 596) {
			ClientPlayer var50 = this.players[var6];
			if (var50 != null) {
				this.tryMove(false, false, var50.routeTileZ[0], localPlayer.routeTileZ[0], 1, 1, 2, 0, var50.routeTileX[0], 0, 0, localPlayer.routeTileX[0]);
				this.crossX = super.mouseClickX;
				this.crossY = super.mouseClickY;
				this.crossMode = 2;
				this.crossCycle = 0;
				// OPPLAYERU
				this.out.p1isaac(143);
				this.out.p2_alt1(this.objInterface);
				this.out.p2_alt3(this.objSelectedSlot);
				this.out.p2(this.objSelectedInterface);
				this.out.p2_alt2(var6);
			}
		}
		if (var5 == 100) {
			boolean var51 = this.tryMove(false, false, var4, localPlayer.routeTileZ[0], 0, 0, 2, 0, var3, 0, 0, localPlayer.routeTileX[0]);
			if (!var51) {
				this.tryMove(false, false, var4, localPlayer.routeTileZ[0], 1, 1, 2, 0, var3, 0, 0, localPlayer.routeTileX[0]);
			}
			this.crossX = super.mouseClickX;
			this.crossY = super.mouseClickY;
			this.crossMode = 2;
			this.crossCycle = 0;
			// OPOBJU
			this.out.p1isaac(211);
			this.out.p2_alt3(this.objSelectedSlot);
			this.out.p2_alt2(this.objInterface);
			this.out.p2_alt3(this.sceneBaseTileZ + var4);
			this.out.p2_alt3(this.sceneBaseTileX + var3);
			this.out.p2_alt1(this.objSelectedInterface);
			this.out.p2_alt1(var6);
		}
		if (var5 == 1668) {
			// EXAMINE_NPC (2026-09-23): examine is answered by the server now, which prints the same
			// description this used to and then anything else worth knowing - the elemental weakness.
			// It is not an interaction: nothing walks, faces or stops.
			ClientNpc var53 = this.npcs[var6];
			if (var53 != null) {
				this.out.p1isaac(150);
				this.out.p2(var6);
			}
		}
		if (var5 == 26) {
			boolean var56 = this.tryMove(false, false, var4, localPlayer.routeTileZ[0], 0, 0, 2, 0, var3, 0, 0, localPlayer.routeTileX[0]);
			if (!var56) {
				this.tryMove(false, false, var4, localPlayer.routeTileZ[0], 1, 1, 2, 0, var3, 0, 0, localPlayer.routeTileX[0]);
			}
			this.crossX = super.mouseClickX;
			this.crossY = super.mouseClickY;
			this.crossMode = 2;
			this.crossCycle = 0;
			oplogic1++;
			if (oplogic1 >= 120) {
				// ANTICHEAT_OPLOGIC1
				this.out.p1isaac(95);
				this.out.p4(0);
				oplogic1 = 0;
			}
			// OPOBJ2
			this.out.p1isaac(100);
			this.out.p2(this.sceneBaseTileX + var3);
			this.out.p2_alt2(this.sceneBaseTileZ + var4);
			this.out.p2_alt3(var6);
		}
		if (var5 == 444) {
			// INV_BUTTON3
			this.out.p1isaac(91);
			this.out.p2_alt1(var6);
			this.out.p2_alt3(var3);
			this.out.p2(var4);
			this.selectedCycle = 0;
			this.selectedInterface = var4;
			this.selectedItem = var3;
			this.selectedArea = 2;
			if (Component.get(var4).layer == this.viewportInterfaceId) {
				this.selectedArea = 1;
			}
			if (Component.get(var4).layer == this.chatInterfaceId) {
				this.selectedArea = 3;
			}
		}
		if (var5 == 507) {
			String var58 = this.menuOption[arg0];
			int var59 = var58.indexOf("@whi@");
			if (var59 != -1) {
				if (this.viewportInterfaceId == -1) {
					this.closeInterfaces();
					this.reportAbuseInput = var58.substring(var59 + 5).trim();
					this.reportAbuseMuteOption = false;
					this.reportAbuseInterfaceId = this.viewportInterfaceId = Component.field728;
				} else {
					this.addMessage("", "Please close the interface you have open before using 'report abuse'", 0);
				}
			}
		}
		if (var5 == 389) {
			this.interactWithLoc(var4, var3, var6);
			// OPLOC2
			this.out.p1isaac(241);
			this.out.p2(var6 >> 14 & 0x7FFF);
			this.out.p2(this.sceneBaseTileX + var3);
			this.out.p2_alt2(this.sceneBaseTileZ + var4);
		}
		if (var5 == 564) {
			// INV_BUTTON4
			this.out.p1isaac(231);
			this.out.p2_alt3(var4);
			this.out.p2_alt1(var3);
			this.out.p2(var6);
			this.selectedCycle = 0;
			this.selectedInterface = var4;
			this.selectedItem = var3;
			this.selectedArea = 2;
			if (Component.get(var4).layer == this.viewportInterfaceId) {
				this.selectedArea = 1;
			}
			if (Component.get(var4).layer == this.chatInterfaceId) {
				this.selectedArea = 3;
			}
		}
		if (var5 == 984) {
			String var60 = this.menuOption[arg0];
			int var61 = var60.indexOf("@whi@");
			if (var61 != -1) {
				long var62 = JString.toBase37(var60.substring(var61 + 5).trim());
				int var64 = -1;
				for (int var65 = 0; var65 < this.friendCount; var65++) {
					if (this.friendName37[var65] == var62) {
						var64 = var65;
						break;
					}
				}
				if (var64 != -1 && this.friendWorld[var64] > 0) {
					this.redrawChatback = true;
					this.chatbackInputOpen = 0;
					this.showSocialInput = true;
					this.socialInput = "";
					this.socialInputType = 3;
					this.socialName37 = this.friendName37[var64];
					this.socialMessage = "Enter message to send to " + this.friendName[var64];
				}
			}
		}
		if (var5 == 518) {
			// IF_BUTTON
			Component var66 = Component.get(var4);
			if (var66 != null && var66.clientCode >= 207 && var66.clientCode <= 215 && this.bankSearchText.length() > 0) {
				// A bank tab was picked while a search was running. The search shows every tab at
				// once and its cell map overrides the window the server is about to send, so the
				// tab would appear to do nothing - close the search and let the tab win.
				this.toggleBankSearch();
			}
			this.out.p1isaac(79);
			this.out.p2(var4);
			if (var66.scripts != null && var66.scripts[0][0] == 5) {
				int var67 = var66.scripts[0][1];
				if (this.varps[var67] != var66.scriptOperand[0]) {
					this.varps[var67] = var66.scriptOperand[0];
					this.updateVarp(var67);
					this.redrawSidebar = true;
				}
			}
		}
		if (var5 == 318) {
			ClientNpc var68 = this.npcs[var6];
			if (var68 != null) {
				this.tryMove(false, false, var68.routeTileZ[0], localPlayer.routeTileZ[0], 1, 1, 2, 0, var68.routeTileX[0], 0, 0, localPlayer.routeTileX[0]);
				this.crossX = super.mouseClickX;
				this.crossY = super.mouseClickY;
				this.crossMode = 2;
				this.crossCycle = 0;
				// OPNPC1
				this.out.p1isaac(112);
				this.out.p2_alt1(var6);
			}
		}
		if (var5 == 199) {
			boolean var69 = this.tryMove(false, false, var4, localPlayer.routeTileZ[0], 0, 0, 2, 0, var3, 0, 0, localPlayer.routeTileX[0]);
			if (!var69) {
				this.tryMove(false, false, var4, localPlayer.routeTileZ[0], 1, 1, 2, 0, var3, 0, 0, localPlayer.routeTileX[0]);
			}
			this.crossX = super.mouseClickX;
			this.crossY = super.mouseClickY;
			this.crossMode = 2;
			this.crossCycle = 0;
			// OPOBJT
			this.out.p1isaac(83);
			this.out.p2_alt1(var6);
			this.out.p2(this.sceneBaseTileZ + var4);
			this.out.p2_alt1(this.activeSpellId);
			this.out.p2_alt3(this.sceneBaseTileX + var3);
		}
		if (var5 == 55) {
			this.unloadCom(this.stickyChatInterfaceId);
			this.stickyChatInterfaceId = -1;
			this.redrawChatback = true;
		}
		if (var5 == 52) {
			this.objSelected = 1;
			this.objSelectedSlot = var3;
			this.objSelectedInterface = var4;
			this.objInterface = var6;
			this.objSelectedName = String.valueOf(ObjType.get(var6).field811);
			this.spellSelected = 0;
			this.redrawSidebar = true;
			return;
		}
		if (var5 == 1564) {
			ObjType var71 = ObjType.get(var6);
			String var72;
			if (var71.field810 == null) {
				var72 = "It's a " + var71.field811 + ".";
			} else {
				var72 = new String(var71.field810);
			}
			this.addMessage("", var72, 0);
		}
		if (var5 == 408) {
			ClientPlayer var73 = this.players[var6];
			if (var73 != null) {
				this.tryMove(false, false, var73.routeTileZ[0], localPlayer.routeTileZ[0], 1, 1, 2, 0, var73.routeTileX[0], 0, 0, localPlayer.routeTileX[0]);
				this.crossX = super.mouseClickX;
				this.crossY = super.mouseClickY;
				this.crossMode = 2;
				this.crossCycle = 0;
				// OPPLAYER3
				this.out.p1isaac(194);
				this.out.p2_alt1(var6);
			}
		}
		this.objSelected = 0;
		this.spellSelected = 0;
		this.redrawSidebar = true;
	}

	// The npc index of the local player's own follower, or -1 for none. The server keeps it in the
	// follower slot's own varp - content scripts/quests/quest_fluffs/configs/quest_fluffs.varp,
	// [follower_uid], transmitted with clientcode 12 - whose value is the pet's uid,
	// (npc type << 16) | npc index. Set in updateVarp(); read by addNpcOptions() below.
	public int followerNpcIndex = -1;

	@ObfuscatedName("client.a(LSLDUQHOR;IIIB)V")
	public void addNpcOptions(NpcType arg0, int arg1, int arg2, int arg3) {
		if (this.menuSize >= 400) {
			return;
		}
		if (arg0.field1425 != null) {
			arg0 = arg0.method476();
		}
		if (arg0 == null || !arg0.field1434) {
			return;
		}
		// A pet's options belong to its owner. Old School has hidden them from everyone else since
		// 15 May 2014 - "The options on other players' pets are no longer visible" - so somebody
		// else's pet gets no menu entries at all, not even Examine, and left-clicking through it
		// walks you there. Your own follower is the npc followerNpcIndex names and is untouched.
		if (arg0.follower && arg3 != this.followerNpcIndex) {
			return;
		}
		String var6 = arg0.field1455;
		// A combat level only on what you can fight, as Old School shows it: a banker, a shopkeeper or
		// a fishing spot with a level in its config no longer reads "(level-2)".
		if (arg0.field1442 != 0 && hasAttackOp(arg0)) {
			var6 = var6 + getCombatLevelTag(arg0.field1442, localPlayer.field1675) + " (level-" + arg0.field1442 + ")";
		}
		if (this.objSelected == 1) {
			this.menuOption[this.menuSize] = "Use " + this.objSelectedName + " with @yel@" + var6;
			this.menuAction[this.menuSize] = 347;
			this.menuParamA[this.menuSize] = arg3;
			this.menuParamB[this.menuSize] = arg2;
			this.menuParamC[this.menuSize] = arg1;
			this.menuSize++;
		} else if (this.spellSelected != 1) {
			if (arg0.field1449 != null) {
				for (int var7 = 4; var7 >= 0; var7--) {
					if (arg0.field1449[var7] != null && !arg0.field1449[var7].equalsIgnoreCase("attack")) {
						this.menuOption[this.menuSize] = arg0.field1449[var7] + " @yel@" + var6;
						if (var7 == 0) {
							this.menuAction[this.menuSize] = 318;
						}
						if (var7 == 1) {
							this.menuAction[this.menuSize] = 921;
						}
						if (var7 == 2) {
							this.menuAction[this.menuSize] = 118;
						}
						if (var7 == 3) {
							this.menuAction[this.menuSize] = 553;
						}
						if (var7 == 4) {
							this.menuAction[this.menuSize] = 432;
						}
						this.menuParamA[this.menuSize] = arg3;
						this.menuParamB[this.menuSize] = arg2;
						this.menuParamC[this.menuSize] = arg1;
						this.menuSize++;
					}
				}
			}
			if (arg0.field1449 != null) {
				for (int var8 = 4; var8 >= 0; var8--) {
					if (arg0.field1449[var8] != null && arg0.field1449[var8].equalsIgnoreCase("attack")) {
						short var9 = 0;
						if (arg0.field1442 > localPlayer.field1675) {
							var9 = 2000;
						}
						this.menuOption[this.menuSize] = arg0.field1449[var8] + " @yel@" + var6;
						if (var8 == 0) {
							this.menuAction[this.menuSize] = var9 + 318;
						}
						if (var8 == 1) {
							this.menuAction[this.menuSize] = var9 + 921;
						}
						if (var8 == 2) {
							this.menuAction[this.menuSize] = var9 + 118;
						}
						if (var8 == 3) {
							this.menuAction[this.menuSize] = var9 + 553;
						}
						if (var8 == 4) {
							this.menuAction[this.menuSize] = var9 + 432;
						}
						this.menuParamA[this.menuSize] = arg3;
						this.menuParamB[this.menuSize] = arg2;
						this.menuParamC[this.menuSize] = arg1;
						this.menuSize++;
					}
				}
			}
			this.menuOption[this.menuSize] = "Examine @yel@" + var6;
			this.menuAction[this.menuSize] = 1668;
			this.menuParamA[this.menuSize] = arg3;
			this.menuParamB[this.menuSize] = arg2;
			this.menuParamC[this.menuSize] = arg1;
			this.menuSize++;
		} else if ((this.activeSpellFlags & 0x2) == 2) {
			this.menuOption[this.menuSize] = this.spellCaption + " @yel@" + var6;
			this.menuAction[this.menuSize] = 67;
			this.menuParamA[this.menuSize] = arg3;
			this.menuParamB[this.menuSize] = arg2;
			this.menuParamC[this.menuSize] = arg1;
			this.menuSize++;
		}
	}

	@ObfuscatedName("client.a(IIILZGNGQRPJ;I)V")
	public void addPlayerOptions(int arg0, int arg1, int arg2, ClientPlayer arg3) {
		if (localPlayer == arg3 || this.menuSize >= 400) {
			return;
		}
		String var6;
		if (arg3.field1681 == 0) {
			var6 = arg3.icons + arg3.name + getCombatLevelTag(arg3.field1675, localPlayer.field1675) + " (level-" + arg3.field1675 + ")";
		} else {
			var6 = arg3.icons + arg3.name + " (skill-" + arg3.field1681 + ")";
		}
		if (this.objSelected == 1) {
			this.menuOption[this.menuSize] = "Use " + this.objSelectedName + " with @whi@" + var6;
			this.menuAction[this.menuSize] = 596;
			this.menuParamA[this.menuSize] = arg0;
			this.menuParamB[this.menuSize] = arg2;
			this.menuParamC[this.menuSize] = arg1;
			this.menuSize++;
		} else if (this.spellSelected != 1) {
			for (int var7 = 4; var7 >= 0; var7--) {
				if (this.playerOps[var7] != null) {
					this.menuOption[this.menuSize] = this.playerOps[var7] + " @whi@" + var6;
					short var9 = 0;
					if (this.playerOps[var7].equalsIgnoreCase("attack")) {
						if (arg3.field1675 > localPlayer.field1675) {
							var9 = 2000;
						}
						if (localPlayer.field1688 != 0 && arg3.field1688 != 0) {
							if (localPlayer.field1688 == arg3.field1688) {
								var9 = 2000;
							} else {
								var9 = 0;
							}
						}
					} else if (this.playerOpPrimary[var7]) {
						var9 = 2000;
					}
					if (var7 == 0) {
						this.menuAction[this.menuSize] = var9 + 200;
					}
					if (var7 == 1) {
						this.menuAction[this.menuSize] = var9 + 493;
					}
					if (var7 == 2) {
						this.menuAction[this.menuSize] = var9 + 408;
					}
					if (var7 == 3) {
						this.menuAction[this.menuSize] = var9 + 677;
					}
					if (var7 == 4) {
						this.menuAction[this.menuSize] = var9 + 876;
					}
					this.menuParamA[this.menuSize] = arg0;
					this.menuParamB[this.menuSize] = arg2;
					this.menuParamC[this.menuSize] = arg1;
					this.menuSize++;
				}
			}
		} else if ((this.activeSpellFlags & 0x8) == 8) {
			this.menuOption[this.menuSize] = this.spellCaption + " @whi@" + var6;
			this.menuAction[this.menuSize] = 918;
			this.menuParamA[this.menuSize] = arg0;
			this.menuParamB[this.menuSize] = arg2;
			this.menuParamC[this.menuSize] = arg1;
			this.menuSize++;
		}
		for (int var8 = 0; var8 < this.menuSize; var8++) {
			if (this.menuAction[var8] == 14) {
				this.menuOption[var8] = "Walk here @whi@" + var6;
				return;
			}
		}
	}

	@ObfuscatedName("client.c(III)Ljava/lang/String;")
	public static String getCombatLevelTag(int arg0, int arg1) {
		int var3 = arg1 - arg0;
		if (var3 < -9) {
			return "@red@";
		} else if (var3 < -6) {
			return "@or3@";
		} else if (var3 < -3) {
			return "@or2@";
		} else if (var3 < 0) {
			return "@or1@";
		} else if (var3 > 9) {
			return "@gre@";
		} else if (var3 > 6) {
			return "@gr3@";
		} else if (var3 > 3) {
			return "@gr2@";
		} else if (var3 > 0) {
			return "@gr1@";
		} else {
			return "@yel@";
		}
	}

	@ObfuscatedName("client.a(IILEWIXBTLV;II)V")
	public void drawInterface(int arg0, int arg1, Component arg2, int arg3) {
		// the outermost call draws the tooltip a component queued (queueTooltip), after everything else
		this.tooltipDepth++;
		try {
			this.drawInterfaceLayer(arg0, arg1, arg2, arg3);
		} finally {
			this.tooltipDepth--;
			if (this.tooltipDepth == 0) {
				this.drawQueuedTooltip();
			}
		}
	}

	private void drawInterfaceLayer(int arg0, int arg1, Component arg2, int arg3) {
		if (arg2.type != 0 || arg2.children == null || arg2.hide && this.viewportHoveredInterfaceIndex != arg2.id && this.sidebarHoveredInterfaceIndex != arg2.id && this.chatHoveredInterfaceIndex != arg2.id) {
			return;
		}
		int var6 = Pix2D.left;
		int var7 = Pix2D.top;
		int var8 = Pix2D.right;
		int var9 = Pix2D.bottom;
		Pix2D.setClipping(arg0, arg1, arg2.height + arg0, arg2.width + arg1);
		int var10 = arg2.children.length;
		for (int var11 = 0; var11 < var10; var11++) {
			int var12 = arg2.childX[var11] + arg1;
			int var13 = arg2.childY[var11] + arg0 - arg3;
			Component var14 = Component.get(arg2.children[var11]);
			int var15 = var14.field710 + var12;
			int var16 = var14.field741 + var13;
			if (var14.clientCode > 0) {
				this.updateInterfaceContent(var14);
			}
			if (var14.type == 0) {
				if (var14.field713 > var14.scroll - var14.height) {
					var14.field713 = var14.scroll - var14.height;
				}
				if (var14.field713 < 0) {
					var14.field713 = 0;
				}
				this.drawInterface(var16, var15, var14, var14.field713);
				// A HIDDEN scroll layer draws no scrollbar. 377 drew one for every layer with a scroll
				// height whether or not the layer itself was showing, and a window that swaps between
				// hidden lists (the drop table's five lengths, npc_drops.if) stacked all five bars on
				// the same spot: the one on top was the longest list's, which never moved.
				if (var14.scroll > var14.height && this.isLayerShown(var14)) {
					this.drawScrollbar(var14.field713, var14.width + var15, var14.height, var14.scroll, var16);
				}
			} else if (var14.type != 1) {
				if (var14.type == 2) {
					// Bank tabs: var17 is the grid CELL, invSlot is the real inventory slot. They are the
					// same thing for every component that has not been given a window, which is all of
					// them except the bank grid - invWindowCount < 0 means "show the whole inv".
					int var17 = 0;
					int[] cellMap = var14.invCellSlot;
					for (int var18 = 0; var18 < var14.height; var18++) {
						for (int var19 = 0; var19 < var14.width; var19++) {
							int invSlot = cellMap == null ? var17 : (var17 < cellMap.length ? cellMap[var17] : -1);
							int var20 = (var14.marginX + 32) * var19 + var15;
							int var21 = (var14.marginY + 32) * var18 + var16;
							if (var17 < 20) {
								var20 += var14.invSlotOffsetX[var17];
								var21 += var14.invSlotOffsetY[var17];
							}
							// Bank tabs: rule a line above the first row of each tab in the all-items view, so the
							// break reads as a divider rather than as a gap somebody forgot to fill.
							if (var14.invCellBreak != null && var17 < var14.invCellBreak.length && var14.invCellBreak[var17]) {
								int ruleW = var14.width * (var14.marginX + 32) - var14.marginX;
								Pix2D.hline(var15, 0x5a4f3d, var21 - 4, ruleW);
								Pix2D.hline(var15, 0x2b2419, var21 - 3, ruleW);
							}
							if (invSlot >= 0 && invSlot < var14.invSlotObjId.length && var14.invSlotObjId[invSlot] > 0) {
								int var22 = 0;
								int var23 = 0;
								int var24 = var14.invSlotObjId[invSlot] - 1;
								if (var20 > Pix2D.left - 32 && var20 < Pix2D.right && var21 > Pix2D.top - 32 && var21 < Pix2D.bottom || this.objDragArea != 0 && this.objDragSlot == invSlot) {
									int var25 = 0;
									if (this.objSelected == 1 && this.objSelectedSlot == invSlot && this.objSelectedInterface == var14.id) {
										var25 = 16777215;
									}
									// Bank placeholders: a slot holding the obj with a count of ZERO. The engine's
									// Inventory has no other way to say "this slot is spoken for but empty", so it
									// arrives here as an ordinary item that happens to have none of itself. Build
									// the icon as if there were one of it - a count of 0 would pick the wrong
									// stack-size variant for anything that has them - then draw it faded, and
									// suppress the number underneath, which would otherwise read "0".
									boolean placeholder = var14.invSlotObjCount[invSlot] == 0;
									Pix32 var26 = ObjType.method230(var25, placeholder ? 1 : var14.invSlotObjCount[invSlot], var24);
									if (var26 != null) {
										if (this.objDragArea != 0 && this.objDragSlot == invSlot && this.objDragInterfaceId == var14.id) {
											var22 = super.mouseX - this.objGrabX;
											var23 = super.mouseY - this.objGrabY;
											if (var22 < 5 && var22 > -5) {
												var22 = 0;
											}
											if (var23 < 5 && var23 > -5) {
												var23 = 0;
											}
											if (this.objDragCycles < 5) {
												var22 = 0;
												var23 = 0;
											}
											var26.transPlotSprite(var20 + var22, var21 + var23, 128);
											// Bank tabs: the bank grid auto-scrolls while an item is being dragged, but SLOWLY.
											// At the normal rate, carrying an item up to the tab row from the bottom of a 51-row
											// bank dragged the whole list to the top with it before the cursor got there, and the
											// player had to scroll all the way back down after every filing - so it was switched
											// off entirely for a while, which cost the ability to file into a tab you could not
											// see. A quarter of the ramp and a fifth of the ceiling is the compromise: it still
											// creeps to whatever is off screen, slowly enough to let go first.
											int dragRate = var14.clientCode == 206 ? 12 : 3;
											int dragCap = var14.clientCode == 206 ? this.sceneDelta * 2 : this.sceneDelta * 10;
											if (var21 + var23 < Pix2D.top && arg2.field713 > 0) {
												int var27 = (Pix2D.top - var21 - var23) * this.sceneDelta / dragRate;
												if (var27 > dragCap) {
													var27 = dragCap;
												}
												if (var27 > arg2.field713) {
													var27 = arg2.field713;
												}
												arg2.field713 -= var27;
												this.objGrabY += var27;
											}
											if (var21 + var23 + 32 > Pix2D.bottom && arg2.field713 < arg2.scroll - arg2.height) {
												int var28 = (var21 + var23 + 32 - Pix2D.bottom) * this.sceneDelta / dragRate;
												if (var28 > dragCap) {
													var28 = dragCap;
												}
												if (var28 > arg2.scroll - arg2.height - arg2.field713) {
													var28 = arg2.scroll - arg2.height - arg2.field713;
												}
												arg2.field713 += var28;
												this.objGrabY -= var28;
											}
										} else if (this.selectedArea != 0 && this.selectedItem == invSlot && this.selectedInterface == var14.id) {
											var26.transPlotSprite(var20, var21, 128);
										} else if (placeholder) {
											var26.transPlotSprite(var20, var21, 70);
										} else {
											var26.plotSprite(var21, var20);
										}
										if (!placeholder && (var26.owi == 33 || var14.invSlotObjCount[invSlot] != 1)) {
											int var29 = var14.invSlotObjCount[invSlot];
											this.fontPlain11.drawString(var20 + 1 + var22, 0, var21 + 10 + var23, formatObjCount(var29));
											this.fontPlain11.drawString(var20 + var22, objCountColour(var29), var21 + 9 + var23, formatObjCount(var29));
										}
									}
								}
							} else if (var14.invSlotGraphic != null && invSlot >= 0 && var17 < 20) {
								Pix32 var30 = var14.invSlotGraphic[var17];
								if (var30 != null) {
									var30.plotSprite(var21, var20);
								}
							}
							var17++;
						}
					}
				} else if (var14.type == 3) {
					boolean var31 = false;
					if (this.chatHoveredInterfaceIndex == var14.id || this.sidebarHoveredInterfaceIndex == var14.id || this.viewportHoveredInterfaceIndex == var14.id) {
						var31 = true;
					}
					int var32;
					if (this.executeInterfaceScript(var14)) {
						var32 = var14.activeColour;
						if (var31 && var14.activeOverColour != 0) {
							var32 = var14.activeOverColour;
						}
					} else {
						var32 = var14.colour;
						if (var31 && var14.overColour != 0) {
							var32 = var14.overColour;
						}
					}
					if (var14.trans == 0) {
						if (var14.fill) {
							Pix2D.fillRect(var14.height, var16, var32, var14.width, var15);
						} else {
							Pix2D.drawRect(var16, var14.height, var32, var15, var14.width);
						}
					} else if (var14.fill) {
						Pix2D.fillRectTrans(var32, var16, var14.width, var14.height, 256 - (var14.trans & 0xFF), var15);
					} else {
						Pix2D.drawRectTrans(var15, var14.width, var32, var14.height, var16, 256 - (var14.trans & 0xFF));
					}
				} else if (var14.type == 4 && (var14.clientCode == 331 || var14.clientCode == 329 || var14.clientCode == 332)) {
					// the skill tab's hover (see OSRS TOOLTIPS): a box, not text on the tab. 332 is a line
					// the 331 before it takes in (tooltipWords) and draws nothing of its own.
					if (var14.clientCode != 332) {
						this.queueTooltip(this.tooltipWords(arg2, var11, var14), var15, var16, var14.width, var14.height, Math.max(arg1, 0), Math.max(arg0, 0), Math.min(arg2.width + arg1, Pix2D.width2d), Math.min(arg2.height + arg0, Pix2D.height2d));
					}
				} else if (var14.type == 4) {
					PixFont var33 = var14.font;
					String var34 = var14.text;
					boolean var35 = false;
					if (this.chatHoveredInterfaceIndex == var14.id || this.sidebarHoveredInterfaceIndex == var14.id || this.viewportHoveredInterfaceIndex == var14.id) {
						var35 = true;
					}
					int var36;
					if (this.executeInterfaceScript(var14)) {
						var36 = var14.activeColour;
						if (var35 && var14.activeOverColour != 0) {
							var36 = var14.activeOverColour;
						}
						if (var14.activeText.length() > 0) {
							var34 = var14.activeText;
						}
					} else {
						var36 = var14.colour;
						if (var35 && var14.overColour != 0) {
							var36 = var14.overColour;
						}
					}
					if (var14.buttonType == 6 && this.pressedContinueOption) {
						var34 = "Please wait...";
						var36 = var14.colour;
					}
					if (Pix2D.width2d == CHAT_W) {
						if (var36 == 16776960) {
							var36 = 255;
						}
						if (var36 == 49152) {
							var36 = 16777215;
						}
					}
					int var37 = var33.height + var16;
					boolean runeCount = isRuneCountText(var34, var14);
					while (var34.length() > 0) {
						if (var34.indexOf("%") != -1) {
							label393: while (true) {
								int var38 = var34.indexOf("%1");
								if (var38 == -1) {
									while (true) {
										int var39 = var34.indexOf("%2");
										if (var39 == -1) {
											while (true) {
												int var40 = var34.indexOf("%3");
												if (var40 == -1) {
													while (true) {
														int var41 = var34.indexOf("%4");
														if (var41 == -1) {
															while (true) {
																int var42 = var34.indexOf("%5");
																if (var42 == -1) {
																	break label393;
																}
																var34 = var34.substring(0, var42) + this.getIntString(this.executeClientScript(4, var14)) + var34.substring(var42 + 2);
															}
														}
														var34 = var34.substring(0, var41) + this.getIntString(this.executeClientScript(3, var14)) + var34.substring(var41 + 2);
													}
												}
												var34 = var34.substring(0, var40) + this.getIntString(this.executeClientScript(2, var14)) + var34.substring(var40 + 2);
											}
										}
										var34 = var34.substring(0, var39) + this.getIntString(this.executeClientScript(1, var14)) + var34.substring(var39 + 2);
									}
								}
								int var38v = this.executeClientScript(0, var14);
								var34 = var34.substring(0, var38) + (runeCount ? getRuneCountString(var38v) : this.getIntString(var38v)) + var34.substring(var38 + 2);
							}
						}
						int var43 = var34.indexOf("\\n");
						String var44;
						if (var43 == -1) {
							var44 = var34;
							var34 = "";
						} else {
							var44 = var34.substring(0, var43);
							var34 = var34.substring(var43 + 2);
						}
						if (var14.center) {
							var33.centreStringTag(var14.shadowed, var36, var37, var14.width / 2 + var15, var44);
						} else {
							var33.drawStringTag(var36, var15, var37, var14.shadowed, var44);
						}
						var37 += var33.height;
					}
				} else if (var14.type == 5) {
					Pix32 var45;
					if (this.executeInterfaceScript(var14)) {
						var45 = var14.activeGraphic;
					} else {
						var45 = var14.graphic;
					}
					// The selected spell, lit the way OSRS lights it until it is cast or cancelled: a
					// translucent white square behind the icon and a brighter 1px edge around it.
					// activeSpellId is the id of the buttontype=target component that was clicked (the
					// spell icon itself), so only that icon lights; the autocast chooser's buttons are
					// buttontype=normal and never set spellSelected.
					boolean spellHighlight = this.spellSelected == 1 && var14.id == this.activeSpellId && var14.buttonType == 2;
					if (spellHighlight) {
						Pix2D.fillRectTrans(0xFFFFFF, var16, var14.width, var14.height, 56, var15);
					}
					if (var45 != null) {
						var45.plotSprite(var16, var15);
					}
					if (spellHighlight) {
						Pix2D.drawRectTrans(var15, var14.width, 0xFFFFFF, var14.height, var16, 150);
					}
				} else if (var14.type == 6) {
					// QoL fix: unlike text (type 4, via PixFont) and sprites (type 5, via Pix32.plotSprite),
					// this 3D model icon draw (var52.method380(...)) never consulted Pix2D's active clip
					// rect at all - it always rendered at full position/size regardless of the enclosing
					// interface layer's bounds. That's invisible for a static icon, but inside a *scrollable*
					// layer (e.g. the Cooking Guide's item list) it meant icons kept drawing past the
					// scrolled-out edge of the list even once their row had scrolled off - "extra items
					// scrolled outside of the interface" per Corey's report. Added the same kind of manual
					// AABB-vs-clip-rect bounds check the type=2 inv slot icon draw already does above
					// (see the `var20 > Pix2D.left - 32 && ...` check) rather than relying on Pix2D's clip
					// alone, since 3D model rendering plainly doesn't honor it.
					if (var15 + var14.width > Pix2D.left && var15 < Pix2D.right && var16 + var14.height > Pix2D.top && var16 < Pix2D.bottom) {
						int var46 = Pix3D.centerX;
						int var47 = Pix3D.centerY;
						Pix3D.centerX = var14.width / 2 + var15;
						Pix3D.centerY = var14.height / 2 + var16;
						int var48 = Pix3D.sinTable[var14.xan] * var14.zoom >> 16;
						int var49 = Pix3D.cosTable[var14.xan] * var14.zoom >> 16;
						boolean var50 = this.executeInterfaceScript(var14);
						int var51;
						if (var50) {
							var51 = var14.activeAnim;
						} else {
							var51 = var14.anim;
						}
						Model var52;
						if (var51 == -1) {
							var52 = var14.getModel(-1, -1, var50);
						} else {
							SeqType var53 = SeqType.field775[var51];
							var52 = var14.getModel(var53.field777[var14.field717], var53.field778[var14.field717], var50);
						}
						if (var52 != null) {
							// Bank tabs: centre obj icons on their own geometry.
							//
							// A model is built with its origin at the item's base and y running negative upwards, so
							// drawing it at the component centre hangs the whole thing ABOVE the middle - and by a
							// different amount for every item, which is why a row of tab icons looked scattered.
							// field1709 is max(-y), the height above the origin, so lifting by half of it puts the
							// model's middle on the component's middle. This is exactly what ObjType.method230 does
							// for inventory icons (field1709 / 2 in the same argument); interface obj icons simply
							// never got it.
							//
							// Gated on modelType 4 (set by IF_SETOBJECT), so plain model components - which are
							// positioned by hand and already look right - are untouched. Other obj icons DO shift:
							// the jewellery, cooking and leather windows will each sit a few pixels lower and
							// centred, which is the same correction.
							if (var14.modelType == 4) {
								var52.method380(0, var14.yan, 0, var14.xan, 0, var52.field1709 / 2 + var48, var49);
							} else {
								var52.method380(0, var14.yan, 0, var14.xan, 0, var48, var49);
							}
						}
						Pix3D.centerX = var46;
						Pix3D.centerY = var47;
					}
				} else {
					if (var14.type == 7) {
						PixFont var54 = var14.font;
						int var55 = 0;
						for (int var56 = 0; var56 < var14.height; var56++) {
							for (int var57 = 0; var57 < var14.width; var57++) {
								if (var14.invSlotObjId[var55] > 0) {
									ObjType var58 = ObjType.get(var14.invSlotObjId[var55] - 1);
									String var59 = String.valueOf(var58.field811);
									if (var58.field853 || var14.invSlotObjCount[var55] != 1) {
										var59 = var59 + " x" + formatObjCountTagged(var14.invSlotObjCount[var55]);
									}
									int var60 = (var14.marginX + 115) * var57 + var15;
									int var61 = (var14.marginY + 12) * var56 + var16;
									if (var14.center) {
										var54.centreStringTag(var14.shadowed, var14.colour, var61, var14.width / 2 + var60, var59);
									} else {
										var54.drawStringTag(var14.colour, var60, var61, var14.shadowed, var59);
									}
								}
								var55++;
							}
						}
					}
					if (var14.type == 8 && (this.field580 == var14.id || this.field340 == var14.id || this.field425 == var14.id) && this.field189 == 100) {
						// OSRS's tooltip box and placement (see OSRS TOOLTIPS), inside the layer AND the area it
						// is drawn on: a side tab's root is 512x334 to the packer, and 377 clamped to that.
						this.queueTooltip(var14.text, var15, var16, var14.width, var14.height, Math.max(arg1, 0), Math.max(arg0, 0), Math.min(arg2.width + arg1, Pix2D.width2d), Math.min(arg2.height + arg0, Pix2D.height2d));
					}
				}
			}
		}
		Pix2D.setClipping(var7, var6, var9, var8);
	}

	// Whether drawInterface draws this layer: not hidden, or hidden but raised by a hover (overlayer).
	private boolean isLayerShown(Component com) {
		return !com.hide || this.viewportHoveredInterfaceIndex == com.id || this.sidebarHoveredInterfaceIndex == com.id || this.chatHoveredInterfaceIndex == com.id;
	}

	// ---- OSRS TOOLTIPS ------------------------------------------------------------------------------
	// OSRS draws every hover box the same way (its client scripts 2344 and 9444, read out of the
	// current cache): a 0xFFFFA0 box with a 1px black edge, black p12 text 12px a line, 2px in from
	// the left edge and the first line's cell 1px below the top, 4px wider than the widest line and
	// 12 * lines + 7 tall. It sits 5px right of the hovered thing's left edge and 5px below its bottom;
	// if that runs off the right of the layer it is pushed back in, and if it runs off the bottom it
	// goes ABOVE the hovered thing instead (5px clear of its top) rather than over it.
	//
	// A line may have two columns, "label|value": the value is right-aligned 4px clear of the label,
	// as the skill tab's "Attack XP:   13,034,431" is.
	//
	// Used by 377's own tooltip component (type 8), whose box was 377's - wider, 13px lines, and
	// clamped to the ROOT, which for a side tab is the 512x334 the packer gives every root, so a
	// prayer's tooltip ran off the right of the tab and over the prayers below - and by a text
	// component with client code 331 (or 329, the Total level's), which is the skill tab's hover.
	private String tooltipText;
	private int tooltipX;
	private int tooltipY;
	private int tooltipDepth;

	private static final int TOOLTIP_BG = 0xFFFFA0;
	private static final int TOOLTIP_LINE = 12;

	// Lay the box out now (the bounds are the layer's, which only this call knows) and draw it when
	// the outermost drawInterface finishes, so nothing drawn after it in child order - the prayer
	// points under the grid, a later layer - lands on top of it.
	private void queueTooltip(String text, int ax, int ay, int aw, int ah, int boundL, int boundT, int boundR, int boundB) {
		if (text == null || text.length() == 0) {
			return;
		}
		PixFont font = this.fontPlain12;
		String[] lines = splitTooltip(text);
		int widest = 0;
		for (String line : lines) {
			widest = Math.max(widest, tooltipLineWidth(font, line));
		}
		int w = widest + 4;
		int h = lines.length * TOOLTIP_LINE + 7;
		int x = ax + 5;
		int y = ay + ah + 5;
		if (x + w > boundR) {
			x = boundR - w;
		}
		if (x < boundL) {
			x = boundL;
		}
		if (y + h > boundB) {
			y = ay - h - 5;
		}
		if (y < boundT) {
			y = boundT;
		}
		this.tooltipText = text;
		this.tooltipX = x;
		this.tooltipY = y;
	}

	private void drawQueuedTooltip() {
		String text = this.tooltipText;
		this.tooltipText = null;
		if (text == null) {
			return;
		}
		PixFont font = this.fontPlain12;
		String[] lines = splitTooltip(text);
		int widest = 0;
		for (String line : lines) {
			widest = Math.max(widest, tooltipLineWidth(font, line));
		}
		int w = widest + 4;
		int h = lines.length * TOOLTIP_LINE + 7;
		int x = this.tooltipX;
		int y = this.tooltipY;
		Pix2D.fillRect(h, y, TOOLTIP_BG, w, x);
		Pix2D.drawRect(y, h, 0, x, w);
		int baseline = y + 1 + font.height;
		for (String line : lines) {
			int bar = line.indexOf('|');
			if (bar == -1) {
				font.drawStringTag(0, x + 2, baseline, false, line);
			} else {
				String value = line.substring(bar + 1);
				font.drawStringTag(0, x + 2, baseline, false, line.substring(0, bar));
				font.drawStringTag(0, x + w - 2 - font.stringWidTag(value), baseline, false, value);
			}
			baseline += TOOLTIP_LINE;
		}
	}

	private static String[] splitTooltip(String text) {
		return text.split("\\\\n");
	}

	private static int tooltipLineWidth(PixFont font, String line) {
		int bar = line.indexOf('|');
		if (bar == -1) {
			return font.stringWidTag(line);
		}
		return font.stringWidTag(line.substring(0, bar)) + 4 + font.stringWidTag(line.substring(bar + 1));
	}

	// A client-code-331 text's words: its active text while its scripts' comparators hold (the skill
	// tab: "below 99" shows the next-level lines), %1..%5 filled in from its scripts with thousands
	// commas, then the non-empty text of every client-code-332 text after it in the same layer - the
	// XP lock's "XP locked", which the server sets and clears with if_settext.
	private String tooltipWords(Component layer, int index, Component com) {
		String text = this.fillTooltipNumbers(com, this.executeInterfaceScript(com) && com.activeText != null && com.activeText.length() > 0 ? com.activeText : com.text);
		for (int i = index + 1; i < layer.children.length; i++) {
			Component extra = Component.get(layer.children[i]);
			if (extra != null && extra.type == 4 && extra.clientCode == 332 && extra.text != null && extra.text.length() > 0) {
				text = text + "\\n" + this.fillTooltipNumbers(extra, extra.text);
			}
		}
		return text;
	}

	private String fillTooltipNumbers(Component com, String text) {
		if (text == null) {
			return "";
		}
		for (int n = 1; n <= 5; n++) {
			String tag = "%" + n;
			int at;
			while ((at = text.indexOf(tag)) != -1) {
				text = text.substring(0, at) + String.format(java.util.Locale.US, "%,d", this.executeClientScript(n - 1, com)) + text.substring(at + 2);
			}
		}
		return text;
	}

	@ObfuscatedName("client.a(ZIIIII)V")
	public void drawScrollbar(int arg1, int arg2, int arg3, int arg4, int arg5) {
		this.imageScrollbar0.plotSprite(arg5, arg2);
		this.imageScrollbar1.plotSprite(arg3 + arg5 - 16, arg2);
		Pix2D.fillRect(arg3 - 32, arg5 + 16, this.SCROLLBAR_TRACK, 16, arg2);
		int var7 = (arg3 - 32) * arg3 / arg4;
		if (var7 < 8) {
			var7 = 8;
		}
		int var8 = (arg3 - 32 - var7) * arg1 / (arg4 - arg3);
		Pix2D.fillRect(var7, arg5 + 16 + var8, this.SCROLLBAR_GRIP_FOREGROUND, 16, arg2);
		Pix2D.vline(arg2, this.SCROLLBAR_GRIP_HIGHLIGHT, var7, arg5 + 16 + var8);
		Pix2D.vline(arg2 + 1, this.SCROLLBAR_GRIP_HIGHLIGHT, var7, arg5 + 16 + var8);
		Pix2D.hline(arg2, this.SCROLLBAR_GRIP_HIGHLIGHT, arg5 + 16 + var8, 16);
		Pix2D.hline(arg2, this.SCROLLBAR_GRIP_HIGHLIGHT, arg5 + 17 + var8, 16);
		Pix2D.vline(arg2 + 15, this.SCROLLBAR_GRIP_LOWLIGHT, var7, arg5 + 16 + var8);
		Pix2D.vline(arg2 + 14, this.SCROLLBAR_GRIP_LOWLIGHT, var7 - 1, arg5 + 17 + var8);
		Pix2D.hline(arg2, this.SCROLLBAR_GRIP_LOWLIGHT, arg5 + 15 + var8 + var7, 16);
		Pix2D.hline(arg2 + 1, this.SCROLLBAR_GRIP_LOWLIGHT, arg5 + 14 + var8 + var7, 15);
	}

	@ObfuscatedName("client.a(II)Ljava/lang/String;")
	public static String formatObjCount(int arg0) {
		if (arg0 < 100000) {
			return String.valueOf(arg0);
		} else if (arg0 < 10000000) {
			return arg0 / 1000 + "K";
		} else {
			return arg0 / 1000000 + "M";
		}
	}

	// The stack number's colour, as OSRS draws it: yellow under 100K, white in K, green in M.
	// Matches the cut-offs in formatObjCount, so the colour always agrees with the suffix.
	public static int objCountColour(int count) {
		if (count < 100000) {
			return 0xffff00;
		} else if (count < 10000000) {
			return 0xffffff;
		} else {
			return 0x00ff80;
		}
	}

	@ObfuscatedName("client.i(II)Ljava/lang/String;")
	public static String formatObjCountTagged(int arg1) {
		String var2 = String.valueOf(arg1);
		for (int var3 = var2.length() - 3; var3 > 0; var3 -= 3) {
			var2 = var2.substring(0, var3) + "," + var2.substring(var3);
		}
		if (var2.length() > 8) {
			var2 = "@gre@" + var2.substring(0, var2.length() - 8) + " million @whi@(" + var2 + ")";
		} else if (var2.length() > 4) {
			var2 = "@cya@" + var2.substring(0, var2.length() - 4) + "K @whi@(" + var2 + ")";
		}
		return " " + var2;
	}

	@ObfuscatedName("client.a(IILEWIXBTLV;BIIIII)V")
	public void handleScrollInput(int arg0, int arg1, Component arg2, int arg4, int arg5, int arg6, int arg7, int arg8) {
		if (this.scrollGrabbed) {
			this.scrollInputPadding = 32;
		} else {
			this.scrollInputPadding = 0;
		}
		this.scrollGrabbed = false;
		if (arg6 >= arg8 && arg6 < arg8 + 16 && arg4 >= arg1 && arg4 < arg1 + 16) {
			arg2.field713 -= this.dragCycles * 4;
			if (arg5 == 1) {
				this.redrawSidebar = true;
			}
			if (arg5 == 2 || arg5 == 3) {
				this.redrawChatback = true;
			}
		} else if (arg6 >= arg8 && arg6 < arg8 + 16 && arg4 >= arg1 + arg7 - 16 && arg4 < arg1 + arg7) {
			arg2.field713 += this.dragCycles * 4;
			if (arg5 == 1) {
				this.redrawSidebar = true;
			}
			if (arg5 == 2 || arg5 == 3) {
				this.redrawChatback = true;
			}
		} else if (arg6 >= arg8 - this.scrollInputPadding && arg6 < arg8 + 16 + this.scrollInputPadding && arg4 >= arg1 + 16 && arg4 < arg1 + arg7 - 16 && this.dragCycles > 0) {
			int var11 = (arg7 - 32) * arg7 / arg0;
			if (var11 < 8) {
				var11 = 8;
			}
			int var12 = arg4 - arg1 - 16 - var11 / 2;
			int var13 = arg7 - 32 - var11;
			arg2.field713 = (arg0 - arg7) * var12 / var13;
			if (arg5 == 1) {
				this.redrawSidebar = true;
			}
			if (arg5 == 2 || arg5 == 3) {
				this.redrawChatback = true;
			}
			this.scrollGrabbed = true;
		}
	}

	// Whether an npc has an Attack option - the one thing that makes it a fight rather than a talk.
	private static boolean hasAttackOp(jagex2.config.NpcType npc) {
		if (npc.field1449 == null) {
			return false;
		}
		for (String op : npc.field1449) {
			if (op != null && op.equalsIgnoreCase("Attack")) {
				return true;
			}
		}
		return false;
	}

	@ObfuscatedName("client.e(II)Ljava/lang/String;")
	public String getIntString(int arg0) {
		return arg0 < 999999999 ? String.valueOf(arg0) : "*";
	}

	// A spell's rune label: "%1/<required>" whose %1 is an inventory count (the first op of its
	// first script is inv_count, opcode 4). Every such text in content - the three spellbooks, the
	// autocast and enchant-bolts panels, inter_233/267, a quest scroll's spell copy - is a rune
	// count (573 of them, all with inv_count first), and nothing else has that shape:
	// the prayer tab's "%1/%2" and every "%1" stat or XP label fail it, so they keep getIntString.
	public static boolean isRuneCountText(String text, Component com) {
		if (text == null || text.length() < 4 || !text.startsWith("%1/")) {
			return false;
		}
		for (int i = 3; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c < '0' || c > '9') {
				return false;
			}
		}
		return com.scripts != null && com.scripts.length > 0 && com.scripts[0] != null && com.scripts[0].length > 0 && com.scripts[0][0] == 4;
	}

	// OSRS stack formatting for a rune count: 99999, 100K, 9999K, 10M. A staff makes the count
	// 999999999 and stays "*"; a negative count here can only be int overflow from a staff (or two
	// huge stacks) on top of runes, so it is "*" too.
	public static String getRuneCountString(int count) {
		if (count < 0 || count >= 999999999) {
			return "*";
		} else if (count < 100000) {
			return String.valueOf(count);
		} else if (count < 10000000) {
			return count / 1000 + "K";
		} else {
			return count / 1000000 + "M";
		}
	}

	// The "Enter amount" prompt (chatbackInputOpen 1): digits, then optionally one '.' (only before
	// a suffix, and only with a digit before it), then at most one k/m/b suffix, which ends the input.
	public static boolean acceptAmountKey(String input, int key) {
		if (input.length() >= 12) {
			return false;
		}
		char last = input.length() > 0 ? Character.toLowerCase(input.charAt(input.length() - 1)) : 0;
		if (last == 'k' || last == 'm' || last == 'b') {
			return false;
		}
		if (key >= '0' && key <= '9') {
			return true;
		}
		boolean hasDigit = false;
		for (int i = 0; i < input.length(); i++) {
			char c = input.charAt(i);
			if (c >= '0' && c <= '9') {
				hasDigit = true;
			}
		}
		if (key == '.') {
			return hasDigit && input.indexOf('.') == -1;
		}
		char lower = Character.toLowerCase((char) key);
		return hasDigit && (lower == 'k' || lower == 'm' || lower == 'b');
	}

	// "10k" = 10000, "1.5m" = 1500000, "2b" = Integer.MAX_VALUE (clamped); fractions below one unit
	// are dropped ("1.2345k" = 1234). Anything unparseable is 0, as Integer.parseInt's catch was.
	public static int parseAmount(String input) {
		String s = input.trim().toLowerCase();
		if (s.length() == 0) {
			return 0;
		}
		long multiplier = 1L;
		char last = s.charAt(s.length() - 1);
		if (last == 'k') {
			multiplier = 1000L;
		} else if (last == 'm') {
			multiplier = 1000000L;
		} else if (last == 'b') {
			multiplier = 1000000000L;
		}
		if (multiplier != 1L) {
			s = s.substring(0, s.length() - 1);
		}
		try {
			java.math.BigDecimal value = new java.math.BigDecimal(s).multiply(java.math.BigDecimal.valueOf(multiplier));
			if (value.signum() <= 0) {
				return 0;
			}
			if (value.compareTo(java.math.BigDecimal.valueOf(Integer.MAX_VALUE)) >= 0) {
				return Integer.MAX_VALUE;
			}
			return value.intValue();
		} catch (Exception ignore) {
			return 0;
		}
	}

	@ObfuscatedName("client.b(LEWIXBTLV;I)Z")
	public boolean executeInterfaceScript(Component arg0) {
		if (arg0.scriptComparator == null) {
			return false;
		}
		for (int var3 = 0; var3 < arg0.scriptComparator.length; var3++) {
			int var4 = this.executeClientScript(var3, arg0);
			int var5 = arg0.scriptOperand[var3];
			if (arg0.scriptComparator[var3] == 2) {
				if (var4 >= var5) {
					return false;
				}
			} else if (arg0.scriptComparator[var3] == 3) {
				if (var4 <= var5) {
					return false;
				}
			} else if (arg0.scriptComparator[var3] == 4) {
				if (var4 == var5) {
					return false;
				}
			} else if (var4 != var5) {
				return false;
			}
		}
		return true;
	}

	@ObfuscatedName("client.a(IILEWIXBTLV;)I")
	public int executeClientScript(int arg1, Component arg2) {
		if (arg2.scripts == null || arg1 >= arg2.scripts.length) {
			return -2;
		} else {
			try {
				int[] var4 = arg2.scripts[arg1];
				int var5 = 0;
				int var6 = 0;
				byte var7 = 0;
				while (true) {
					int var8 = var4[var6++];
					int var9 = 0;
					byte var10 = 0;
					if (var8 == 0) {
						return var5;
					}
					if (var8 == 1) {
						var9 = this.skillLevel[var4[var6++]];
					}
					if (var8 == 2) {
						var9 = this.skillBaseLevel[var4[var6++]];
					}
					if (var8 == 3) {
						var9 = this.skillExperience[var4[var6++]];
					}
					if (var8 == 4) {
						Component var11 = Component.get(var4[var6++]);
						int var12 = var4[var6++];
						if (var12 >= 0 && var12 < ObjType.field817 && (!ObjType.get(var12).field859 || membersWorld)) {
							for (int var13 = 0; var13 < var11.invSlotObjId.length; var13++) {
								if (var12 + 1 == var11.invSlotObjId[var13]) {
									var9 += var11.invSlotObjCount[var13];
								}
							}
						}
					}
					if (var8 == 5) {
						var9 = this.varps[var4[var6++]];
					}
					if (var8 == 6) {
						var9 = levelExperience[this.skillBaseLevel[var4[var6++]] - 1];
					}
					if (var8 == 7) {
						var9 = this.varps[var4[var6++]] * 100 / 46875;
					}
					if (var8 == 8) {
						var9 = localPlayer.field1675;
					}
					if (var8 == 9) {
						for (int var14 = 0; var14 < Stats.field1503; var14++) {
							if (Stats.field1505[var14]) {
								var9 += this.skillBaseLevel[var14];
							}
						}
					}
					if (var8 == 10) {
						Component var15 = Component.get(var4[var6++]);
						int var16 = var4[var6++] + 1;
						if (var16 >= 0 && var16 < ObjType.field817 && (!ObjType.get(var16).field859 || membersWorld)) {
							for (int var17 = 0; var17 < var15.invSlotObjId.length; var17++) {
								if (var15.invSlotObjId[var17] == var16) {
									var9 = 999999999;
									break;
								}
							}
						}
					}
					if (var8 == 11) {
						var9 = this.runenergy;
					}
					if (var8 == 12) {
						var9 = this.runweight;
					}
					if (var8 == 13) {
						int var18 = this.varps[var4[var6++]];
						int var19 = var4[var6++];
						var9 = (var18 & 0x1 << var19) == 0 ? 0 : 1;
					}
					if (var8 == 14) {
						int var20 = var4[var6++];
						VarbitType var21 = VarbitType.field1760[var20];
						int var22 = var21.field1762;
						int var23 = var21.field1763;
						int var24 = var21.field1764;
						int var25 = VARBIT_MASKS[var24 - var23];
						var9 = this.varps[var22] >> var23 & var25;
					}
					if (var8 == 15) {
						var10 = 1;
					}
					if (var8 == 16) {
						var10 = 2;
					}
					if (var8 == 17) {
						var10 = 3;
					}
					if (var8 == 18) {
						var9 = (localPlayer.field1157 >> 7) + this.sceneBaseTileX;
					}
					if (var8 == 19) {
						var9 = (localPlayer.field1158 >> 7) + this.sceneBaseTileZ;
					}
					if (var8 == 20) {
						var9 = var4[var6++];
					}
					if (var10 == 0) {
						if (var7 == 0) {
							var5 += var9;
						}
						if (var7 == 1) {
							var5 -= var9;
						}
						if (var7 == 2 && var9 != 0) {
							var5 /= var9;
						}
						if (var7 == 3) {
							var5 *= var9;
						}
						var7 = 0;
					} else {
						var7 = var10;
					}
				}
			} catch (Exception var26) {
				return -1;
			}
		}
	}

	@ObfuscatedName("client.a(ILEWIXBTLV;IIIIII)V")
	public void handleInterfaceInput(int arg0, Component arg1, int arg2, int arg3, int arg4, int arg5, int arg7) {
		if (arg1.type != 0 || arg1.children == null || arg1.hide || (arg5 < arg4 || arg7 < arg0 || arg5 > arg1.width + arg4 || arg7 > arg1.height + arg0)) {
			return;
		}
		int var9 = arg1.children.length;
		for (int var10 = 0; var10 < var9; var10++) {
			int var11 = arg1.childX[var10] + arg4;
			int var12 = arg1.childY[var10] + arg0 - arg3;
			Component var13 = Component.get(arg1.children[var10]);
			int var14 = var13.field710 + var11;
			int var15 = var13.field741 + var12;
			if ((var13.overlayer >= 0 || var13.overColour != 0) && arg5 >= var14 && arg7 >= var15 && arg5 < var13.width + var14 && arg7 < var13.height + var15) {
				if (var13.overlayer >= 0) {
					this.lastHoveredInterfaceId = var13.overlayer;
				} else {
					this.lastHoveredInterfaceId = var13.id;
				}
			}
			// Bank tabs: remember the tab under the cursor. 207..215 are the nine tab buttons;
			// 206 is the bank grid itself, which the client already special-cases below.
			if (var13.clientCode >= 207 && var13.clientCode <= 215 && arg5 >= var14 && arg7 >= var15 && arg5 < var13.width + var14 && arg7 < var13.height + var15) {
				this.bankTabHovered = var13.clientCode - 207;
			}
			if (var13.type == 8 && arg5 >= var14 && arg7 >= var15 && arg5 < var13.width + var14 && arg7 < var13.height + var15) {
				this.field611 = var13.id;
			}
			if (var13.type == 0) {
				this.handleInterfaceInput(var15, var13, arg2, var13.field713, var14, arg5, arg7);
				// ...nor takes scroll input: a hidden list ahead of the shown one in child order ate the
				// wheel (it zeroes mouseScrollDelta) and its bar answered drags over the same pixels
				if (var13.scroll > var13.height && this.isLayerShown(var13)) {
					this.handleScrollInput(var13.scroll, var15, var13, arg7, arg2, arg5, var13.height, var13.width + var14);
					// QoL: mouse wheel scrolls any scrollable interface panel (e.g. bank) when hovering over it
					if (QolSettings.on(QolSettings.WHEEL_INTERFACE) && super.mouseScrollDelta != 0 && arg5 >= var14 && arg7 >= var15 && arg5 < var13.width + var14 && arg7 < var13.height + var15) {
						var13.field713 += super.mouseScrollDelta * 16;
						if (var13.field713 < 0) {
							var13.field713 = 0;
						}
						if (var13.field713 > var13.scroll - var13.height) {
							var13.field713 = var13.scroll - var13.height;
						}
						super.mouseScrollDelta = 0;
						if (arg2 == 1) {
							this.redrawSidebar = true;
						}
						if (arg2 == 2 || arg2 == 3) {
							this.redrawChatback = true;
						}
					}
				}
			} else {
				if (var13.buttonType == 1 && arg5 >= var14 && arg7 >= var15 && arg5 < var13.width + var14 && arg7 < var13.height + var15) {
					boolean var16 = false;
					if (var13.clientCode != 0) {
						var16 = this.handleSocialMenuOption(var13);
					}
					if (!var16) {
						this.menuOption[this.menuSize] = var13.option;
						this.menuAction[this.menuSize] = 352;
						this.menuParamC[this.menuSize] = var13.id;
						this.menuSize++;
					}
				}
				if (var13.buttonType == 2 && this.spellSelected == 0 && arg5 >= var14 && arg7 >= var15 && arg5 < var13.width + var14 && arg7 < var13.height + var15) {
					String var17 = var13.targetVerb;
					if (var17.indexOf(" ") != -1) {
						var17 = var17.substring(0, var17.indexOf(" "));
					}
					this.menuOption[this.menuSize] = var17 + " @gre@" + var13.targetText;
					this.menuAction[this.menuSize] = 70;
					this.menuParamC[this.menuSize] = var13.id;
					this.menuSize++;
				}
				if (var13.buttonType == 3 && arg5 >= var14 && arg7 >= var15 && arg5 < var13.width + var14 && arg7 < var13.height + var15) {
					this.menuOption[this.menuSize] = "Close";
					if (arg2 == 3) {
						this.menuAction[this.menuSize] = 55;
					} else {
						this.menuAction[this.menuSize] = 639;
					}
					this.menuParamC[this.menuSize] = var13.id;
					this.menuSize++;
				}
				if (var13.buttonType == 4 && arg5 >= var14 && arg7 >= var15 && arg5 < var13.width + var14 && arg7 < var13.height + var15) {
					this.menuOption[this.menuSize] = var13.option;
					this.menuAction[this.menuSize] = 890;
					this.menuParamC[this.menuSize] = var13.id;
					this.menuSize++;
				}
				if (var13.buttonType == 5 && arg5 >= var14 && arg7 >= var15 && arg5 < var13.width + var14 && arg7 < var13.height + var15) {
					this.menuOption[this.menuSize] = var13.option;
					this.menuAction[this.menuSize] = 518;
					this.menuParamC[this.menuSize] = var13.id;
					this.menuSize++;
				}
				if (var13.buttonType == 6 && !this.pressedContinueOption && arg5 >= var14 && arg7 >= var15 && arg5 < var13.width + var14 && arg7 < var13.height + var15) {
					this.menuOption[this.menuSize] = var13.option;
					this.menuAction[this.menuSize] = 575;
					this.menuParamC[this.menuSize] = var13.id;
					this.menuSize++;
				}
				if (var13.type == 2) {
					// Bank tabs: the cell/slot split again. hoveredSlot must be the REAL slot, because
					// it is what gets sent back as the drag target and as last_slot.
					int var18 = 0;
					int[] cellMap = var13.invCellSlot;
					int[] dropMap = var13.invCellDrop;
					for (int var19 = 0; var19 < var13.height; var19++) {
						for (int var20 = 0; var20 < var13.width; var20++) {
							int var21 = (var13.marginX + 32) * var20 + var14;
							int var22 = (var13.marginY + 32) * var19 + var15;
							if (var18 < 20) {
								var21 += var13.invSlotOffsetX[var18];
								var22 += var13.invSlotOffsetY[var18];
							}
							if (arg5 >= var21 && arg7 >= var22 && arg5 < var21 + 32 && arg7 < var22 + 32) {
									// The drop map, not the draw map: a blank padding cell draws nothing but is still a
									// legitimate place to drop something ("put it in this tab"). realSlot below stays the
									// draw map, so the right-click menu is only ever built from a cell that holds an item.
									int hitSlot = dropMap == null ? var18 : (var18 < dropMap.length ? dropMap[var18] : -1);
									int realSlot = cellMap == null ? var18 : (var18 < cellMap.length ? cellMap[var18] : -1);
									if (hitSlot < 0) {
										var18++;
										continue;
									}
									this.hoveredSlot = hitSlot;
									this.hoveredSlotPad = realSlot < 0;
								this.hoveredSlotInterfaceId = var13.id;
								if (realSlot >= 0 && var13.invSlotObjId[realSlot] > 0) {
									ObjType var23 = ObjType.get(var13.invSlotObjId[realSlot] - 1);
									if (this.objSelected == 1 && var13.interactable) {
										if (this.objSelectedInterface != var13.id || this.objSelectedSlot != realSlot) {
											this.menuOption[this.menuSize] = "Use " + this.objSelectedName + " with @lre@" + var23.field811;
											this.menuAction[this.menuSize] = 903;
											this.menuParamA[this.menuSize] = var23.field845;
											this.menuParamB[this.menuSize] = realSlot;
											this.menuParamC[this.menuSize] = var13.id;
											this.menuSize++;
										}
									} else if (this.spellSelected != 1 || !var13.interactable) {
										if (var13.interactable) {
											for (int var24 = 4; var24 >= 3; var24--) {
												if (var23.field830 != null && var23.field830[var24] != null) {
													this.menuOption[this.menuSize] = var23.field830[var24] + " @lre@" + var23.field811;
													if (var24 == 3) {
														this.menuAction[this.menuSize] = 227;
													}
													if (var24 == 4) {
														this.menuAction[this.menuSize] = 891;
													}
													this.menuParamA[this.menuSize] = var23.field845;
													this.menuParamB[this.menuSize] = realSlot;
													this.menuParamC[this.menuSize] = var13.id;
													this.menuSize++;
												} else if (var24 == 4) {
													this.menuOption[this.menuSize] = "Drop @lre@" + var23.field811;
													this.menuAction[this.menuSize] = 891;
													this.menuParamA[this.menuSize] = var23.field845;
													this.menuParamB[this.menuSize] = realSlot;
													this.menuParamC[this.menuSize] = var13.id;
													this.menuSize++;
												}
											}
										}
										if (var13.usable) {
											this.menuOption[this.menuSize] = "Use @lre@" + var23.field811;
											this.menuAction[this.menuSize] = 52;
											this.menuParamA[this.menuSize] = var23.field845;
											this.menuParamB[this.menuSize] = realSlot;
											this.menuParamC[this.menuSize] = var13.id;
											this.menuSize++;
										}
										if (var13.interactable && var23.field830 != null) {
											for (int var25 = 2; var25 >= 0; var25--) {
												if (var23.field830[var25] != null) {
													this.menuOption[this.menuSize] = var23.field830[var25] + " @lre@" + var23.field811;
													if (var25 == 0) {
														this.menuAction[this.menuSize] = 961;
													}
													if (var25 == 1) {
														this.menuAction[this.menuSize] = 399;
													}
													if (var25 == 2) {
														this.menuAction[this.menuSize] = 324;
													}
													this.menuParamA[this.menuSize] = var23.field845;
													this.menuParamB[this.menuSize] = realSlot;
													this.menuParamC[this.menuSize] = var13.id;
													this.menuSize++;
												}
											}
										}
										if (var23.wearop != null && var13.layer == this.tabInterfaceId[WEAROP_TAB]) {
											// OSRS's worn options: in the Worn Equipment tab an item offers its own
											// options under Remove - a glory's four teleports, a slayer helmet's
											// Check. The menu is stored bottom-to-top, so they go in last-first and
											// BEFORE the component's own options below, which leaves Remove on top as
											// the left click and the item's options under it in their own order.
											//
											// The tab is recognised by the sidebar slot it is shown in, not by a
											// component id, for the reason the placeholder note below gives: an id
											// hardcoded here is a number nobody maintaining the content would look
											// for. The server checks the rest (WearOpHandler): that the component
											// shows the player's own worn items and the item has that option.
											for (int w = ObjType.WEAROP_COUNT - 1; w >= 0; w--) {
												if (var23.wearop[w] != null) {
													this.menuOption[this.menuSize] = var23.wearop[w] + " @lre@" + var23.field811;
													this.menuAction[this.menuSize] = WEAROP_ACTION + w;
													this.menuParamA[this.menuSize] = var23.field845;
													this.menuParamB[this.menuSize] = realSlot;
													this.menuParamC[this.menuSize] = var13.id;
													this.menuSize++;
												}
											}
										}
										if (var13.iop != null && var13.invSlotObjCount[realSlot] == 0
												&& var13.iop[0] != null && var13.iop[0].startsWith("Withdraw")) {
											// Bank placeholders: the stub is not a stack you can take any of, so the
											// five Withdraw entries are replaced by the one thing you can do to it.
											// It rides op 1, so [inv_button1] is where the script picks it up - and
											// the script has to test for a placeholder BEFORE it withdraws.
											//
											// AND ONLY IN THE BANK. A zero count is not unique to a placeholder: a
											// shop line stocked at 0 looks exactly the same on the wire, and 35 shop
											// invs in the content carry 272 of them between them. Without the option
											// test every one of those items offered "Release" - reported from play
											// 2026-09-21 against the Shantay Pass shop, whose bucket, bowl and jug
											// are all stocked at 0.
											//
											// The gate is the component's own first option rather than its id,
											// because an id hardcoded here is a number nobody maintaining the
											// content would ever think to look at. The contract is that the bank
											// grid's options are the Withdraw ones, and banktab_sim.py checks that
											// from the content side - a client-only check could never go red there.
											this.menuOption[this.menuSize] = "Release @lre@" + var23.field811;
											this.menuAction[this.menuSize] = 9;
											this.menuParamA[this.menuSize] = var23.field845;
											this.menuParamB[this.menuSize] = realSlot;
											this.menuParamC[this.menuSize] = var13.id;
											this.menuSize++;
										} else if (var13.iop != null) {
											for (int var26 = 4; var26 >= 0; var26--) {
												if (var13.iop[var26] != null) {
													this.menuOption[this.menuSize] = var13.iop[var26] + " @lre@" + var23.field811;
													if (var26 == 0) {
														this.menuAction[this.menuSize] = 9;
													}
													if (var26 == 1) {
														this.menuAction[this.menuSize] = 225;
													}
													if (var26 == 2) {
														this.menuAction[this.menuSize] = 444;
													}
													if (var26 == 3) {
														this.menuAction[this.menuSize] = 564;
													}
													if (var26 == 4) {
														this.menuAction[this.menuSize] = 894;
													}
													this.menuParamA[this.menuSize] = var23.field845;
													this.menuParamB[this.menuSize] = realSlot;
													this.menuParamC[this.menuSize] = var13.id;
													this.menuSize++;
												}
											}
										}
										this.menuOption[this.menuSize] = "Examine @lre@" + var23.field811;
										this.menuAction[this.menuSize] = 1094;
										this.menuParamA[this.menuSize] = var23.field845;
										this.menuParamB[this.menuSize] = realSlot;
										this.menuParamC[this.menuSize] = var13.id;
										this.menuSize++;
									} else if ((this.activeSpellFlags & 0x10) == 16) {
										this.menuOption[this.menuSize] = this.spellCaption + " @lre@" + var23.field811;
										this.menuAction[this.menuSize] = 361;
										this.menuParamA[this.menuSize] = var23.field845;
										this.menuParamB[this.menuSize] = realSlot;
										this.menuParamC[this.menuSize] = var13.id;
										this.menuSize++;
									}
								}
							}
							var18++;
						}
					}
				}
			}
		}
	}

	@ObfuscatedName("client.a(LEWIXBTLV;I)Z")
	public boolean handleSocialMenuOption(Component arg0) {
		int var4 = arg0.clientCode;
		if (var4 >= 1 && var4 <= 200 || !(var4 < 701 || var4 > 900)) {
			if (var4 >= 801) {
				var4 -= 701;
			} else if (var4 >= 701) {
				var4 -= 601;
			} else if (var4 >= 101) {
				var4 -= 101;
			} else {
				var4--;
			}
			this.menuOption[this.menuSize] = "Remove @whi@" + this.friendName[var4];
			this.menuAction[this.menuSize] = 775;
			this.menuSize++;
			this.menuOption[this.menuSize] = "Message @whi@" + this.friendName[var4];
			this.menuAction[this.menuSize] = 984;
			this.menuSize++;
			return true;
		} else if (var4 >= 401 && var4 <= 500) {
			this.menuOption[this.menuSize] = "Remove @whi@" + arg0.text;
			this.menuAction[this.menuSize] = 859;
			this.menuSize++;
			return true;
		} else {
			return false;
		}
	}

	@ObfuscatedName("client.b(BI)V")
	public void resetInterfaceAnimation(int arg1) {
		Component var3 = Component.get(arg1);
		for (int var4 = 0; var4 < var3.children.length && var3.children[var4] != -1; var4++) {
			Component var5 = Component.get(var3.children[var4]);
			if (var5.type == 1) {
				this.resetInterfaceAnimation(var5.id);
			}
			var5.field717 = 0;
			var5.field709 = 0;
		}
	}

	@ObfuscatedName("client.a(IIB)Z")
	public boolean updateInterfaceAnimation(int arg0, int arg1) {
		boolean var4 = false;
		Component var5 = Component.get(arg1);
		for (int var6 = 0; var6 < var5.children.length && var5.children[var6] != -1; var6++) {
			Component var7 = Component.get(var5.children[var6]);
			if (var7.type == 0) {
				var4 |= this.updateInterfaceAnimation(arg0, var7.id);
			}
			if (var7.type == 6 && (var7.anim != -1 || var7.activeAnim != -1)) {
				boolean var8 = this.executeInterfaceScript(var7);
				int var9;
				if (var8) {
					var9 = var7.activeAnim;
				} else {
					var9 = var7.anim;
				}
				if (var9 != -1) {
					SeqType var10 = SeqType.field775[var9];
					var7.field709 += arg0;
					while (var7.field709 > var10.method214(var7.field717)) {
						var7.field709 -= var10.method214(var7.field717);
						var7.field717++;
						if (var7.field717 >= var10.field776) {
							var7.field717 -= var10.field780;
							if (var7.field717 < 0 || var7.field717 >= var10.field776) {
								var7.field717 = 0;
							}
						}
						var4 = true;
					}
				}
			}
			if (var7.type == 6 && var7.field700 != 0) {
				int var11 = var7.field700 >> 16;
				int var12 = var7.field700 << 16 >> 16;
				int var13 = arg0 * var11;
				int var14 = arg0 * var12;
				var7.xan = var7.xan + var13 & 0x7FF;
				var7.yan = var7.yan + var14 & 0x7FF;
				var4 = true;
			}
		}
		return var4;
	}

	@ObfuscatedName("client.f(II)V")
	public void updateVarp(int arg1) {
		int var3 = VarpType.field1507[arg1].field1515;
		if (var3 == 0) {
			return;
		}
		int var4 = this.varps[arg1];
		if (var3 == 12) {
			// The follower slot: the uid of the pet following you, (npc type << 16) | npc index, or
			// null (-1) when nothing is. Only the index is wanted here - which npc in this.npcs[] is
			// mine - and 0 means the slot has never been written this session.
			this.followerNpcIndex = var4 <= 0 ? -1 : var4 & 0xFFFF;
		}
		if (var3 == 1) {
			if (var4 == 1) {
				Pix3D.initColourTable(0.9D);
			}
			if (var4 == 2) {
				Pix3D.initColourTable(0.8D);
			}
			if (var4 == 3) {
				Pix3D.initColourTable(0.7D);
			}
			if (var4 == 4) {
				Pix3D.initColourTable(0.6D);
			}
			ObjType.field828.clear();
			this.redrawFrame = true;
		}
		if (var3 == 3) {
			boolean var5 = this.midiActive;
			if (var4 == 0) {
				this.setMidiVolume(this.midiActive, 128);
				this.midiActive = true;
			}
			if (var4 == 1) {
				this.setMidiVolume(this.midiActive, 96);
				this.midiActive = true;
			}
			if (var4 == 2) {
				this.setMidiVolume(this.midiActive, 64);
				this.midiActive = true;
			}
			if (var4 == 3) {
				this.setMidiVolume(this.midiActive, 32);
				this.midiActive = true;
			}
			if (var4 == 4) {
				this.midiActive = false;
			}
			if (this.midiActive != var5 && !lowMem) {
				if (this.midiActive) {
					this.midiSong = this.nextMidiSong;
					this.midiFading = true;
					this.onDemand.request(2, this.midiSong);
				} else {
					this.stopMidi();
				}
				this.nextMusicDelay = 0;
			}
		}
		if (var3 == 4) {
			if (var4 == 0) {
				this.waveEnabled = true;
				this.setWaveVolume(128);
			}
			if (var4 == 1) {
				this.waveEnabled = true;
				this.setWaveVolume(96);
			}
			if (var4 == 2) {
				this.waveEnabled = true;
				this.setWaveVolume(64);
			}
			if (var4 == 3) {
				this.waveEnabled = true;
				this.setWaveVolume(32);
			}
			if (var4 == 4) {
				this.waveEnabled = false;
			}
		}
		if (var3 == 5) {
			this.oneMouseButton = var4;
		}
		if (var3 == 6) {
			this.chatEffects = var4;
		}
		if (var3 == 8) {
			this.splitPrivateChat = var4;
			this.redrawChatback = true;
		}
		if (var3 == 9) {
			this.bankArrangeMode = var4;
		}
		if (var3 == 10) {
			// area sounds: 0 loudest to 4 off, as the sound effects slider
			this.areaSoundVolume = var4 == 0 ? 127 : var4 == 1 ? 96 : var4 == 2 ? 64 : var4 == 3 ? 32 : 0;
		}
		if (var3 == 11) {
			this.spellbookIcon = var4;
			this.redrawSideicons = true;
		}
	}

	@ObfuscatedName("client.a(BLEWIXBTLV;)V")
	public void updateInterfaceContent(Component arg1) {
		int var4 = arg1.clientCode;
		if (var4 >= 1001 && var4 <= 1400) {
			this.updateClanContent(arg1, var4);
			return;
		}
		if (var4 == 204) {
			// 474's Friends tab title, which says which world you are on
			arg1.text = "Friends List - World " + (nodeId - 9);
			return;
		}
		if ((var4 < 1 || var4 > 100) && (var4 < 701 || var4 > 800)) {
			if (var4 >= 101 && var4 <= 200 || !(var4 < 801 || var4 > 900)) {
				int var6 = this.friendCount;
				if (this.friendlistStatus != 2) {
					var6 = 0;
				}
				if (var4 > 800) {
					var4 -= 701;
				} else {
					var4 -= 101;
				}
				if (var4 >= var6) {
					arg1.text = "";
					arg1.buttonType = 0;
				} else {
					if (this.friendWorld[var4] == 0) {
						arg1.text = "@red@Offline";
					} else if (this.friendWorld[var4] < 200) {
						if (this.friendWorld[var4] == nodeId) {
							arg1.text = "@gre@World" + (this.friendWorld[var4] - 9);
						} else {
							arg1.text = "@yel@World" + (this.friendWorld[var4] - 9);
						}
					} else if (this.friendWorld[var4] == nodeId) {
						arg1.text = "@gre@Classic" + (this.friendWorld[var4] - 219);
					} else {
						arg1.text = "@yel@Classic" + (this.friendWorld[var4] - 219);
					}
					arg1.buttonType = 1;
				}
			} else if (var4 == 203) {
				int var7 = this.friendCount;
				if (this.friendlistStatus != 2) {
					var7 = 0;
				}
				arg1.scroll = var7 * 15 + 20;
				if (arg1.scroll <= arg1.height) {
					arg1.scroll = arg1.height + 1;
				}
			} else if (var4 >= 401 && var4 <= 500) {
				var4 -= 401;
				if (var4 == 0 && this.friendlistStatus == 0) {
					arg1.text = "Loading ignore list";
					arg1.buttonType = 0;
				} else if (var4 == 1 && this.friendlistStatus == 0) {
					arg1.text = "Please wait...";
					arg1.buttonType = 0;
				} else {
					int var8 = this.ignoreCount;
					if (this.friendlistStatus == 0) {
						var8 = 0;
					}
					if (var4 >= var8) {
						arg1.text = "";
						arg1.buttonType = 0;
					} else {
						arg1.text = JString.formatDisplayName(JString.fromBase37(this.ignoreName37[var4]));
						arg1.buttonType = 1;
					}
				}
			} else if (var4 == 503) {
				arg1.scroll = this.ignoreCount * 15 + 20;
				if (arg1.scroll <= arg1.height) {
					arg1.scroll = arg1.height + 1;
				}
			} else if (var4 == 329) {
				// the skill tab's Total level hover: every skill's experience, summed as a long because
				// 23 skills at 200M overflow the int a component script adds in, with thousands commas
				long total = 0L;
				for (int i = 0; i < this.skillExperience.length; i++) {
					total += this.skillExperience[i];
				}
				arg1.text = "Total XP:|" + String.format(java.util.Locale.US, "%,d", total);
			} else if (var4 == 328) {
				// 474's Equipment Stats: you, standing in what you wear, idling
				if (localPlayer != null) {
					arg1.modelType = 6;
					// A fold of the appearance, as 474's client sets it here. Every worn item sits above
					// bit 15 of it and the mask drops them all, so this is the SAME number whatever the
					// player is holding and it is no use as a cache key: Component.loadModel builds the
					// body from the whole appearance instead and ignores this. Kept so a type-6
					// component still has a model id at all.
					arg1.model = (int) (localPlayer.field1676 ^ localPlayer.field1676 >>> 32) & 0x7FFF;
					// A new stance (a weapon changed while the window is open) starts at its first frame:
					// the frame counter belongs to the old one and can run past the new one's end.
					if (arg1.anim != localPlayer.field1181) {
						arg1.anim = localPlayer.field1181;
						arg1.field717 = 0;
						arg1.field709 = 0;
					}
					// The camera OSRS's client gives content type 328 (and 327, the character design
					// screen below): looking down at you from 150 (26 degrees), rocking 45 degrees either
					// side of facing you, one sway about every five seconds. OSRS's own 84:4 is zoom 550,
					// every angle 0, and leaves the angles to this. It was a steady full turn at eye
					// level, which put the camera at your feet and showed your back half the time.
					arg1.xan = 150;
					arg1.yan = (int) (Math.sin((double) loopCycle / 40.0D) * 256.0D) & 0x7FF;
				}
			} else if (var4 == 327) {
				arg1.xan = 150;
				arg1.yan = (int) (Math.sin((double) loopCycle / 40.0D) * 256.0D) & 0x7FF;
				if (this.updateDesignModel) {
					for (int var9 = 0; var9 < 7; var9++) {
						int var16 = this.designKits[var9];
						if (var16 >= 0 && !IdkType.field1699[var16].method577()) {
							return;
						}
					}
					this.updateDesignModel = false;
					Model[] var10 = new Model[7];
					int var11 = 0;
					for (int var12 = 0; var12 < 7; var12++) {
						int var15 = this.designKits[var12];
						if (var15 >= 0) {
							var10[var11++] = IdkType.field1699[var15].method578();
						}
					}
					Model var13 = new Model(var11, var10, (byte) -89);
					for (int var14 = 0; var14 < 5; var14++) {
						if (this.designColours[var14] != 0) {
							var13.method373(DESIGN_BODY_COLOUR[var14][0], DESIGN_BODY_COLOUR[var14][this.designColours[var14]]);
							if (var14 == 1) {
								var13.method373(DESIGN_HAIR_COLOUR[0], DESIGN_HAIR_COLOUR[this.designColours[var14]]);
							}
						}
					}
					var13.createLabelReferences();
					var13.applyTransform(SeqType.field775[localPlayer.field1181].field777[0]);
					var13.calculateNormals(64, 850, -30, -50, -30, true);
					arg1.modelType = 5;
					arg1.model = 0;
					Component.cacheModel(5, var13, 0);
				}
			} else if (var4 == 324) {
				if (this.genderButtonImage0 == null) {
					this.genderButtonImage0 = arg1.graphic;
					this.genderButtonImage1 = arg1.activeGraphic;
				}
				if (this.designGender) {
					arg1.graphic = this.genderButtonImage1;
				} else {
					arg1.graphic = this.genderButtonImage0;
				}
			} else if (var4 == 325) {
				if (this.genderButtonImage0 == null) {
					this.genderButtonImage0 = arg1.graphic;
					this.genderButtonImage1 = arg1.activeGraphic;
				}
				if (this.designGender) {
					arg1.graphic = this.genderButtonImage0;
				} else {
					arg1.graphic = this.genderButtonImage1;
				}
			} else if (var4 == 600) {
				arg1.text = this.reportAbuseInput;
				if (loopCycle % 20 < 10) {
					arg1.text = arg1.text + "|";
				} else {
					arg1.text = arg1.text + " ";
				}
			} else {
				if (var4 == 620) {
					if (this.staffmodlevel < 1) {
						arg1.text = "";
					} else if (this.reportAbuseMuteOption) {
						arg1.colour = 16711680;
						arg1.text = "Moderator option: Mute player for 48 hours: <ON>";
					} else {
						arg1.colour = 16777215;
						arg1.text = "Moderator option: Mute player for 48 hours: <OFF>";
					}
				}
				// The welcome screen's other panels - recovery questions (661), the message centre (662),
				// member credit (665) and their help texts (667, 668) - were Jagex's website's, filled in here
				// with text this server has no truth for. Their words are now inter_313.if's and the server's.
				if (var4 == 660) {
					int var17 = this.currentDay - this.previousLoginDay;
					String var18;
					if (var17 <= 0) {
						var18 = "earlier today";
					} else if (var17 == 1) {
						var18 = "yesterday";
					} else {
						var18 = var17 + " days ago";
					}
					// the server sends no address when it has none to give (lastAddress 0), rather than 127.0.0.1
					arg1.text = "You last logged in @red@" + var18 + (this.lastAddress == 0 ? "" : "@bla@ from: @red@" + signlink.dns);
				}
				if (var4 == 663) {
					if (this.daysSincePasswordChanged > 0 && this.daysSincePasswordChanged <= this.currentDay + 10) {
						arg1.text = "Last password change:\\n@gre@" + this.getDateString(this.daysSincePasswordChanged);
					} else {
						arg1.text = "Last password change:\\n@gre@Never changed";
					}
				}
			}
		} else if (var4 == 1 && this.friendlistStatus == 0) {
			arg1.text = "Loading friend list";
			arg1.buttonType = 0;
		} else if (var4 == 1 && this.friendlistStatus == 1) {
			arg1.text = "Connecting to friendserver";
			arg1.buttonType = 0;
		} else if (var4 == 2 && this.friendlistStatus != 2) {
			arg1.text = "Please wait...";
			arg1.buttonType = 0;
		} else {
			int var5 = this.friendCount;
			if (this.friendlistStatus != 2) {
				var5 = 0;
			}
			if (var4 > 700) {
				var4 -= 601;
			} else {
				var4--;
			}
			if (var4 >= var5) {
				arg1.text = "";
				arg1.buttonType = 0;
			} else {
				arg1.text = this.friendName[var4];
				arg1.buttonType = 1;
			}
		}
	}

	// The clan tab (content interface clanchat) is drawn from UPDATE_CLANCHANNEL by client code, the
	// way the friends list is: 1001 "Talking in", 1002 "Owner", 1003 Join/Leave Chat, 1005 the list's
	// scroll layer, 1101-1200 member names (Kick when this player outranks them), 1201-1300 worlds.
	private void updateClanContent(Component com, int code) {
		boolean in = this.clanOwner37 != 0L;
		if (code == 1001) {
			com.text = in ? "Talking in: @whi@" + JString.formatDisplayName(JString.fromBase37(this.clanName37)) : "Talking in: Not in chat";
		} else if (code == 1002) {
			com.text = in ? "Owner: @whi@" + JString.formatDisplayName(JString.fromBase37(this.clanOwner37)) : "Owner: None";
		} else if (code == 1003) {
			// on the button and on its label alike
			com.option = in ? "Leave Chat" : "Join Chat";
			if (com.type == 4) {
				com.text = com.option;
			}
		} else if (code == 1005) {
			com.scroll = this.clanCount * 15 + 20;
			if (com.scroll <= com.height) {
				com.scroll = com.height + 1;
			}
		} else if (code >= 1101 && code <= 1200) {
			int i = code - 1101;
			if (!in || i >= this.clanCount) {
				com.text = "";
				com.buttonType = 0;
				return;
			}
			com.text = JString.formatDisplayName(JString.fromBase37(this.clanMemberName37[i]));
			int mine = this.clanRankOfSelf();
			com.buttonType = mine >= this.clanKickRank && mine > this.clanMemberRank[i] ? 1 : 0;
		} else if (code >= 1201 && code <= 1300) {
			int i = code - 1201;
			if (!in || i >= this.clanCount) {
				com.text = "";
				return;
			}
			// in the row's own colour, 474's pale yellow, as its script 197 wrote it
			com.text = "World " + (this.clanMemberWorld[i] - 9);
		} else if (code >= 1301 && code <= 1400) {
			// the rank icon in front of the name (474's sprites 1004-1012, the clanrank sheet)
			int i = code - 1301;
			com.graphic = in && i < this.clanCount ? this.clanRankIcon(this.clanMemberRank[i]) : null;
		}
	}

	/** clanrank frame for a rank: 0 friend, 1-6 Recruit to General, 7 owner, 127 staff; -1 has none. */
	private Pix32 clanRankIcon(int rank) {
		int frame = rank >= 0 && rank <= 7 ? rank : rank == 127 ? 8 : -1;
		return frame == -1 ? null : this.imageClanRanks[frame];
	}

	private int clanRankOfSelf() {
		long me = localPlayer == null ? 0L : JString.toBase37(localPlayer.name);
		for (int i = 0; i < this.clanCount; i++) {
			if (this.clanMemberName37[i] == me) {
				return this.clanMemberRank[i];
			}
		}
		return -1;
	}

	@ObfuscatedName("client.a(ILEWIXBTLV;)Z")
	public boolean handleInterfaceAction(Component arg1) {
		int var3 = arg1.clientCode;
		if (var3 == 221) {
			// Bank search button. Returning false keeps the click off the wire entirely - the
			// filter is a client-side view of an inv the client already has, so the server has
			// nothing to do and no script to run.
			this.toggleBankSearch();
			return false;
		}
		if (this.friendlistStatus == 2) {
			if (var3 == 201) {
				this.redrawChatback = true;
				this.chatbackInputOpen = 0;
				this.showSocialInput = true;
				this.socialInput = "";
				this.socialInputType = 1;
				this.socialMessage = "Enter name of friend to add to list";
			}
			if (var3 == 202) {
				this.redrawChatback = true;
				this.chatbackInputOpen = 0;
				this.showSocialInput = true;
				this.socialInput = "";
				this.socialInputType = 2;
				this.socialMessage = "Enter name of friend to delete from list";
			}
		}
		if (var3 == 205) {
			this.idleTimeout = 250;
			return true;
		}
		if (var3 == 1003) {
			// the clan tab's Join Chat / Leave Chat
			if (this.clanOwner37 != 0L) {
				// CLAN_JOINCHAT (custom): 0 leaves
				this.out.p1isaac(7);
				this.out.p8(0L);
			} else {
				this.redrawChatback = true;
				this.chatbackInputOpen = 0;
				this.showSocialInput = true;
				this.socialInput = "";
				this.socialInputType = 6;
				this.socialMessage = "Enter the player name whose channel you wish to join:";
			}
			return false;
		}
		if (var3 >= 1101 && var3 <= 1200) {
			// a name in the clan list: Kick
			int member = var3 - 1101;
			if (member < this.clanCount) {
				// CLAN_KICK (custom)
				this.out.p1isaac(11);
				this.out.p8(this.clanMemberName37[member]);
			}
			return false;
		}
		if (var3 == 501) {
			this.redrawChatback = true;
			this.chatbackInputOpen = 0;
			this.showSocialInput = true;
			this.socialInput = "";
			this.socialInputType = 4;
			this.socialMessage = "Enter name of player to add to list";
		}
		if (var3 == 502) {
			this.redrawChatback = true;
			this.chatbackInputOpen = 0;
			this.showSocialInput = true;
			this.socialInput = "";
			this.socialInputType = 5;
			this.socialMessage = "Enter name of player to delete from list";
		}
		if (var3 >= 300 && var3 <= 313) {
			int var4 = (var3 - 300) / 2;
			int var5 = var3 & 0x1;
			int var6 = this.designKits[var4];
			if (var6 != -1) {
				while (true) {
					if (var5 == 0) {
						var6--;
						if (var6 < 0) {
							var6 = IdkType.field1698 - 1;
						}
					}
					if (var5 == 1) {
						var6++;
						if (var6 >= IdkType.field1698) {
							var6 = 0;
						}
					}
					if (!IdkType.field1699[var6].field1705 && IdkType.field1699[var6].field1700 == var4 + (this.designGender ? 0 : 7)) {
						this.designKits[var4] = var6;
						this.updateDesignModel = true;
						break;
					}
				}
			}
		}
		if (var3 >= 314 && var3 <= 323) {
			int var7 = (var3 - 314) / 2;
			int var8 = var3 & 0x1;
			int var9 = this.designColours[var7];
			if (var8 == 0) {
				var9--;
				if (var9 < 0) {
					var9 = DESIGN_BODY_COLOUR[var7].length - 1;
				}
			}
			if (var8 == 1) {
				var9++;
				if (var9 >= DESIGN_BODY_COLOUR[var7].length) {
					var9 = 0;
				}
			}
			this.designColours[var7] = var9;
			this.updateDesignModel = true;
		}
		if (var3 == 324 && !this.designGender) {
			this.designGender = true;
			this.validateCharacterDesign();
		}
		if (var3 == 325 && this.designGender) {
			this.designGender = false;
			this.validateCharacterDesign();
		}
		if (var3 == 326) {
			// IF_PLAYERDESIGN
			this.out.p1isaac(163);
			this.out.p1(this.designGender ? 0 : 1);
			for (int var10 = 0; var10 < 7; var10++) {
				this.out.p1(this.designKits[var10]);
			}
			for (int var11 = 0; var11 < 5; var11++) {
				this.out.p1(this.designColours[var11]);
			}
			return true;
		}
		if (var3 == 620) {
			this.reportAbuseMuteOption = !this.reportAbuseMuteOption;
		}
		if (var3 >= 601 && var3 <= 613) {
			this.closeInterfaces();
			if (this.reportAbuseInput.length() > 0) {
				// REPORT_ABUSE
				this.out.p1isaac(184);
				this.out.p8(JString.toBase37(this.reportAbuseInput));
				this.out.p1(var3 - 601);
				this.out.p1(this.reportAbuseMuteOption ? 1 : 0);
			}
		}
		return false;
	}

	@ObfuscatedName("client.f(I)V")
	public void validateCharacterDesign() {
		this.updateDesignModel = true;
		for (int var2 = 0; var2 < 7; var2++) {
			this.designKits[var2] = -1;
			for (int var3 = 0; var3 < IdkType.field1698; var3++) {
				if (!IdkType.field1699[var3].field1705 && IdkType.field1699[var3].field1700 == var2 + (this.designGender ? 0 : 7)) {
					this.designKits[var2] = var3;
					break;
				}
			}
		}
	}

	@ObfuscatedName("client.k(B)V")
	public void drawSidebar() {
		this.areaSidebar.bind();
		Pix3D.lineOffset = this.areaSidebarOffset;
		this.imageInvback.plotSprite(0, 0);
		if (this.sidebarInterfaceId != -1) {
			this.drawInterface(0, 0, Component.get(this.sidebarInterfaceId), 0);
		} else if (this.tabInterfaceId[this.selectedTab] != -1) {
			this.drawInterface(0, 0, Component.get(this.tabInterfaceId[this.selectedTab]), 0);
		}
		if (this.menuVisible && this.menuArea == 1) {
			this.drawMenu();
		}
		this.areaSidebar.draw(205, SIDE_X, super.graphics);
		this.areaViewport.bind();
		Pix3D.lineOffset = this.areaViewportOffset;
	}

	@ObfuscatedName("client.t(I)V")
	public void drawChat() {
		this.areaChatback.bind();
		Pix3D.lineOffset = this.areaChatbackOffset;
		this.imageChatback.plotSprite(338 - CHAT_Y, -CHAT_X);
		if (this.showSocialInput) {
			this.fontBold12.centreString(CHAT_W / 2, CHAT_IF_Y + 40, 0, this.socialMessage);
			this.fontBold12.centreString(CHAT_W / 2, CHAT_IF_Y + 60, 128, this.socialInput + "*");
		} else if (this.chatbackInputOpen == 1) {
			this.fontBold12.centreString(CHAT_W / 2, CHAT_IF_Y + 40, 0, this.dialogPrompt != null ? this.dialogPrompt : "Enter amount:");
			this.fontBold12.centreString(CHAT_W / 2, CHAT_IF_Y + 60, 128, this.chatbackInput + "*");
		} else if (this.chatbackInputOpen == 2) {
			this.fontBold12.centreString(CHAT_W / 2, CHAT_IF_Y + 40, 0, this.dialogPrompt != null ? this.dialogPrompt : "Enter name:");
			this.fontBold12.centreString(CHAT_W / 2, CHAT_IF_Y + 60, 128, this.chatbackInput + "*");
		} else if (this.chatbackInputOpen == 4) {
			this.fontBold12.centreString(CHAT_W / 2, CHAT_IF_Y + 40, 0, "Show items whose names contain the following text:");
			this.fontBold12.centreString(CHAT_W / 2, CHAT_IF_Y + 60, 128, this.chatbackInput + "*");
		} else if (this.chatbackInputOpen == 3) {
			if (this.chatbackInput != this.field157) {
				this.searchObjNames(this.chatbackInput);
				this.field157 = this.chatbackInput;
			}
			PixFont var2 = this.fontPlain12;
			Pix2D.setClipping(0, 0, CHAT_LOG_H, CHAT_W - 16);
			for (int var3 = 0; var3 < this.field158; var3++) {
				int var4 = var3 * 14 + 18 - this.field161;
				if (var4 > 0 && var4 < CHAT_LOG_H + 33) {
					var2.centreString(CHAT_W / 2, var4, 0, this.field159[var3]);
				}
			}
			Pix2D.resetClipping();
			if (this.field158 * 14 + 7 > CHAT_LOG_H) {
				this.drawScrollbar(this.field161, CHAT_W - 16, CHAT_LOG_H, this.field158 * 14 + 7, 0);
			}
			if (this.chatbackInput.length() == 0) {
				this.fontBold12.centreString(CHAT_W / 2, CHAT_IF_Y + 40, 255, "Enter object name");
			} else if (this.field158 == 0) {
				this.fontBold12.centreString(CHAT_W / 2, CHAT_IF_Y + 40, 0, "No matching objects found, please shorten search");
			}
			var2.centreString(CHAT_W / 2, CHAT_H - 6, 0, this.chatbackInput + "*");
			Pix2D.hline(0, 0, CHAT_LOG_H, CHAT_W);
		} else if (this.modalMessage != null) {
			// Drawn the way the chat log draws a game message - through ChatIcons, so colour tags
			// colour it, shadow tags shadow it and @cr4@ is a badge sprite. centreString drew the
			// tags as letters, which both printed "@sh1@@cya@[Yell]" at the player and measured the
			// line far too wide, so it was centred on the wrong width and ran off both edges. The
			// server wraps game messages to 456 pixels measuring the same way ChatIcons.width does,
			// so a line fits the box once its tags stop taking space.
			int modalX = CHAT_W / 2 - ChatIcons.width(this.fontBold12, this.modalMessage) / 2;
			ChatIcons.draw(this.fontBold12, this.imageModIcons, modalX, CHAT_IF_Y + 40, 0, this.modalMessage);
			this.fontBold12.centreString(CHAT_W / 2, CHAT_IF_Y + 60, 128, "Click to continue");
		} else if (this.chatInterfaceId != -1) {
			this.drawInterface(CHAT_IF_Y, CHAT_IF_X, Component.get(this.chatInterfaceId), 0);
		} else if (this.stickyChatInterfaceId == -1) {
			PixFont var5 = this.fontPlain12;
			int var6 = 0;
			Pix2D.setClipping(0, 0, CHAT_LOG_H, CHAT_W - 16);
			for (int var7 = 0; var7 < 100; var7++) {
				if (this.messageText[var7] != null) {
					int var9 = this.messageType[var7];
					if (this.chatFilter == 1 && var9 != 0) {
						continue;
					}
					int var10 = CHAT_LOG_H - 7 - var6 * 14 + this.chatScrollOffset;
					String var11 = this.messageSender[var7];
					// the sender's icons (ChatIcons markers), taken off the front of the name
					String var12 = ChatIcons.leading(var11);
					if (var11 != null) {
						var11 = var11.substring(var12.length());
					}
					if (var9 == 0) {
						if (var10 > 0 && var10 < CHAT_LOG_H + 33) {
							this.drawGameMessage(var5, var10, this.messageText[var7]);
						}
						var6++;
					}
					if ((var9 == 1 || var9 == 2) && (var9 == 1 || this.chatPublicMode == 0 || this.chatPublicMode == 1 && this.isFriend(var11))) {
						if (var10 > 0 && var10 < CHAT_LOG_H + 33 && this.messageCont[var7]) {
							var5.drawString(4 + this.messageIndent[var7], 255, var10, this.messageText[var7]);
						} else if (var10 > 0 && var10 < CHAT_LOG_H + 33) {
							int var13 = 4;
							if (var12.length() > 0) {
								ChatIcons.draw(var5, this.imageModIcons, var13, var10, 0, var12);
								var13 += ChatIcons.width(var5, var12);
							}
							var5.drawString(var13, 0, var10, var11 + ":");
							int var14 = var13 + var5.stringWidTag(var11) + 8;
							var5.drawString(var14, 255, var10, this.messageText[var7]);
						}
						var6++;
					}
					if ((var9 == 3 || var9 == 7) && this.splitPrivateChat == 0 && (var9 == 7 || this.chatPrivateMode == 0 || this.chatPrivateMode == 1 && this.isFriend(var11))) {
						if (var10 > 0 && var10 < CHAT_LOG_H + 33 && this.messageCont[var7]) {
							var5.drawString(4 + this.messageIndent[var7], 8388608, var10, this.messageText[var7]);
						} else if (var10 > 0 && var10 < CHAT_LOG_H + 33) {
							byte var15 = 4;
							var5.drawString(var15, 0, var10, "From");
							int var16 = var15 + var5.stringWidTag("From ");
							if (var12.length() > 0) {
								ChatIcons.draw(var5, this.imageModIcons, var16, var10, 0, var12);
								var16 += ChatIcons.width(var5, var12);
							}
							var5.drawString(var16, 0, var10, var11 + ":");
							int var17 = var16 + var5.stringWidTag(var11) + 8;
							var5.drawString(var17, 8388608, var10, this.messageText[var7]);
						}
						var6++;
					}
					if (var9 == 4 && (this.chatTradeMode == 0 || this.chatTradeMode == 1 && this.isFriend(var11))) {
						if (var10 > 0 && var10 < CHAT_LOG_H + 33) {
							var5.drawString(4, 8388736, var10, this.messageCont[var7] ? this.messageText[var7] : var11 + " " + this.messageText[var7]);
						}
						var6++;
					}
					if (var9 == 5 && this.splitPrivateChat == 0 && this.chatPrivateMode < 2) {
						if (var10 > 0 && var10 < CHAT_LOG_H + 33) {
							var5.drawString(4, 8388608, var10, this.messageText[var7]);
						}
						var6++;
					}
					if (var9 == 6 && this.splitPrivateChat == 0 && this.chatPrivateMode < 2) {
						if (var10 > 0 && var10 < CHAT_LOG_H + 33 && this.messageCont[var7]) {
							var5.drawString(4 + this.messageIndent[var7], 8388608, var10, this.messageText[var7]);
						} else if (var10 > 0 && var10 < CHAT_LOG_H + 33) {
							var5.drawString(4, 0, var10, "To " + var11 + ":");
							var5.drawString(var5.stringWidTag("To " + var11) + 12, 8388608, var10, this.messageText[var7]);
						}
						var6++;
					}
					if (var9 == 11 && this.showClanLine(var11)) {
						if (var10 > 0 && var10 < CHAT_LOG_H + 33 && this.messageCont[var7]) {
							var5.drawString(4 + this.messageIndent[var7], 0x7F0000, var10, this.messageText[var7]);
						} else if (var10 > 0 && var10 < CHAT_LOG_H + 33) {
							int x = 4;
							String chan = "[" + this.messageChannel[var7] + "] ";
							var5.drawString(x, 255, var10, chan);
							x += var5.stringWidTag(chan);
							if (var12.length() > 0) {
								ChatIcons.draw(var5, this.imageModIcons, x, var10, 0, var12);
								x += ChatIcons.width(var5, var12);
							}
							var5.drawString(x, 0, var10, var11 + ":");
							var5.drawString(x + var5.stringWidTag(var11) + 8, 0x7F0000, var10, this.messageText[var7]);
						}
						var6++;
					}
					if (var9 == 8 && (this.chatTradeMode == 0 || this.chatTradeMode == 1 && this.isFriend(var11))) {
						if (var10 > 0 && var10 < CHAT_LOG_H + 33) {
							var5.drawString(4, 8270336, var10, this.messageCont[var7] ? this.messageText[var7] : var11 + " " + this.messageText[var7]);
						}
						var6++;
					}
				}
			}
			Pix2D.resetClipping();
			this.chatScrollHeight = var6 * 14 + 7;
			if (this.chatScrollHeight < CHAT_LOG_H + 1) {
				this.chatScrollHeight = CHAT_LOG_H + 1;
			}
			this.drawScrollbar(this.chatScrollHeight - this.chatScrollOffset - CHAT_LOG_H, CHAT_W - 16, CHAT_LOG_H, this.chatScrollHeight, 0);
			String var8;
			if (localPlayer == null || localPlayer.name == null) {
				var8 = JString.formatDisplayName(this.username);
			} else {
				var8 = localPlayer.name;
			}
			// your own crown and XP-mode badge in front of your name, as OSRS's input line shows them -
			// the same icons a line you send is given
			String ownIcons = localPlayer != null && localPlayer.icons.length() > 0 ? localPlayer.icons : ChatIcons.forPlayer(this.staffmodlevel == 3 ? 2 : this.staffmodlevel);
			int inputX = 4;
			if (ownIcons.length() > 0) {
				ChatIcons.draw(var5, this.imageModIcons, inputX, CHAT_H - 6, 0, ownIcons);
				inputX += ChatIcons.width(var5, ownIcons);
			}
			var5.drawString(inputX, 0, CHAT_H - 6, var8 + ":");
			var5.drawString(inputX + var5.stringWidTag(var8 + ": ") + 2, 255, CHAT_H - 6, this.chatTyped + "*");
			Pix2D.hline(0, 0, CHAT_LOG_H, CHAT_W);
		} else {
			this.drawInterface(CHAT_IF_Y, CHAT_IF_X, Component.get(this.stickyChatInterfaceId), 0);
		}
		if (this.menuVisible && this.menuArea == 2) {
			this.drawMenu();
		}
		this.areaChatback.draw(CHAT_Y, CHAT_X, super.graphics);
		this.areaViewport.bind();
		Pix3D.lineOffset = this.areaViewportOffset;
	}

	@ObfuscatedName("client.v(I)V")
	public void drawMinimap() {
		this.areaMapback.bind();
		if (this.minimapType == 2) {
			byte[] var2 = this.imageMapback.pixels;
			int[] var3 = Pix2D.data;
			int var4 = var2.length;
			for (int var5 = 0; var5 < var4; var5++) {
				if (var2[var5] == 0) {
					var3[var5] = 0;
				}
			}
			this.imageCompass.drawRotatedMasked(0, 33, 25, 33, this.compassMaskLineLengths, 0, this.orbitCameraYaw, 256, this.compassMaskLineOffsets, 25);
			this.areaViewport.bind();
			Pix3D.lineOffset = this.areaViewportOffset;
			return;
		}
		int var6 = this.orbitCameraYaw + this.macroMinimapAngle & 0x7FF;
		int var7 = localPlayer.field1157 / 32 + 48;
		int var9 = 464 - localPlayer.field1158 / 32;
		this.imageMinimap.drawRotatedMasked(5, 151, var7, 146, this.minimapMaskLineLengths, 25, var6, this.macroMinimapZoom + 256, this.minimapMaskLineOffsets, var9);
		this.imageCompass.drawRotatedMasked(0, 33, 25, 33, this.compassMaskLineLengths, 0, this.orbitCameraYaw, 256, this.compassMaskLineOffsets, 25);
		for (int var10 = 0; var10 < this.activeMapFunctionCount; var10++) {
			int var40 = this.activeMapFunctionX[var10] * 4 + 2 - localPlayer.field1157 / 32;
			int var41 = this.activeMapFunctionZ[var10] * 4 + 2 - localPlayer.field1158 / 32;
			this.drawOnMinimap(var41, this.activeMapFunctions[var10], var40);
		}
		for (int var11 = 0; var11 < 104; var11++) {
			for (int var36 = 0; var36 < 104; var36++) {
				LinkList var37 = this.objStacks[this.currentLevel][var11][var36];
				if (var37 != null) {
					int var38 = var11 * 4 + 2 - localPlayer.field1157 / 32;
					int var39 = var36 * 4 + 2 - localPlayer.field1158 / 32;
					this.drawOnMinimap(var39, this.imageMapdot0, var38);
				}
			}
		}
		for (int var12 = 0; var12 < this.npcCount; var12++) {
			ClientNpc var32 = this.npcs[this.npcIds[var12]];
			if (var32 != null && var32.method351()) {
				NpcType var33 = var32.field1370;
				if (var33.field1425 != null) {
					var33 = var33.method476();
				}
				if (var33 != null && var33.field1439 && var33.field1434) {
					int var34 = var32.field1157 / 32 - localPlayer.field1157 / 32;
					int var35 = var32.field1158 / 32 - localPlayer.field1158 / 32;
					this.drawOnMinimap(var35, this.imageMapdot1, var34);
				}
			}
		}
		for (int var13 = 0; var13 < this.playerCount; var13++) {
			ClientPlayer var24 = this.players[this.playerIds[var13]];
			if (var24 != null && var24.method351()) {
				int var25 = var24.field1157 / 32 - localPlayer.field1157 / 32;
				int var26 = var24.field1158 / 32 - localPlayer.field1158 / 32;
				boolean var27 = false;
				long var28 = JString.toBase37(var24.name);
				for (int var30 = 0; var30 < this.friendCount; var30++) {
					if (this.friendName37[var30] == var28 && this.friendWorld[var30] != 0) {
						var27 = true;
						break;
					}
				}
				boolean var31 = false;
				if (localPlayer.field1688 != 0 && var24.field1688 != 0 && localPlayer.field1688 == var24.field1688) {
					var31 = true;
				}
				if (var27) {
					this.drawOnMinimap(var26, this.imageMapdot3, var25);
				} else if (var31) {
					this.drawOnMinimap(var26, this.imageMapdot4, var25);
				} else {
					this.drawOnMinimap(var26, this.imageMapdot2, var25);
				}
			}
		}
		if (this.hintType != 0 && loopCycle % 20 < 10) {
			if (this.hintType == 1 && this.hintNpc >= 0 && this.hintNpc < this.npcs.length) {
				ClientNpc var14 = this.npcs[this.hintNpc];
				if (var14 != null) {
					int var15 = var14.field1157 / 32 - localPlayer.field1157 / 32;
					int var16 = var14.field1158 / 32 - localPlayer.field1158 / 32;
					this.drawMinimapArrow(var16, this.imageMapmarker1, var15);
				}
			}
			if (this.hintType == 2) {
				int var17 = (this.hintTileX - this.sceneBaseTileX) * 4 + 2 - localPlayer.field1157 / 32;
				int var18 = (this.hintTileZ - this.sceneBaseTileZ) * 4 + 2 - localPlayer.field1158 / 32;
				this.drawMinimapArrow(var18, this.imageMapmarker1, var17);
			}
			if (this.hintType == 10 && this.hintPlayer >= 0 && this.hintPlayer < this.players.length) {
				ClientPlayer var19 = this.players[this.hintPlayer];
				if (var19 != null) {
					int var20 = var19.field1157 / 32 - localPlayer.field1157 / 32;
					int var21 = var19.field1158 / 32 - localPlayer.field1158 / 32;
					this.drawMinimapArrow(var21, this.imageMapmarker1, var20);
				}
			}
		}
		if (this.flagSceneTileX != 0) {
			int var22 = this.flagSceneTileX * 4 + 2 - localPlayer.field1157 / 32;
			int var23 = this.flagSceneTileZ * 4 + 2 - localPlayer.field1158 / 32;
			this.drawOnMinimap(var23, this.imageMapmarker0, var22);
		}
		Pix2D.fillRect(3, 78, 16777215, 3, 97);
		this.areaViewport.bind();
		Pix3D.lineOffset = this.areaViewportOffset;
	}

	@ObfuscatedName("client.a(ILEPQDEJTO;II)V")
	public void drawMinimapArrow(int arg0, Pix32 arg1, int arg3) {
		int var5 = arg0 * arg0 + arg3 * arg3;
		if (var5 <= 4225 || var5 >= 90000) {
			this.drawOnMinimap(arg0, arg1, arg3);
			return;
		}
		int var6 = this.orbitCameraYaw + this.macroMinimapAngle & 0x7FF;
		int var7 = Model.sinTable[var6];
		int var8 = Model.cosTable[var6];
		int var9 = var7 * 256 / (this.macroMinimapZoom + 256);
		int var10 = var8 * 256 / (this.macroMinimapZoom + 256);
		int var11 = arg0 * var9 + arg3 * var10 >> 16;
		int var12 = arg0 * var10 - arg3 * var9 >> 16;
		double var13 = Math.atan2((double) var11, (double) var12);
		int var15 = (int) (Math.sin(var13) * 63.0D);
		int var16 = (int) (Math.cos(var13) * 57.0D);
		this.imageMapedge.drawRotated(256, 15, var15 + 94 + 4 - 10, 15, 20, 20, var13, 83 - var16 - 20);
	}

	@ObfuscatedName("client.a(IZLEPQDEJTO;I)V")
	public void drawOnMinimap(int arg0, Pix32 arg2, int arg3) {
		if (arg2 == null) {
			return;
		}
		int var5 = this.orbitCameraYaw + this.macroMinimapAngle & 0x7FF;
		int var6 = arg0 * arg0 + arg3 * arg3;
		if (var6 > 6400) {
			return;
		}
		int var7 = Model.sinTable[var5];
		int var8 = Model.cosTable[var5];
		int var9 = var7 * 256 / (this.macroMinimapZoom + 256);
		int var10 = var8 * 256 / (this.macroMinimapZoom + 256);
		int var11 = arg0 * var9 + arg3 * var10 >> 16;
		int var12 = arg0 * var10 - arg3 * var9 >> 16;
		if (var6 > 2500) {
			arg2.drawMasked(this.imageMapback, 83 - var12 - arg2.ohi / 2 - 4, var11 + 94 - arg2.owi / 2 + 4);
		} else {
			arg2.plotSprite(83 - var12 - arg2.ohi / 2 - 4, var11 + 94 - arg2.owi / 2 + 4);
		}
	}

	// QoL: record a sent line for Page Up / Page Down recall. Newest first, capped at
	// CHAT_HISTORY_MAX, and consecutive duplicates collapse so repeating one line does not fill the
	// buffer with copies of itself. Also ends any in-progress browse, so the next Page Up starts
	// from the top rather than resuming halfway down.
	private void pushChatHistory(String line) {
		if (line == null || line.length() == 0) {
			return;
		}
		this.chatHistoryBrowsePos = -1;
		this.chatHistoryBrowseDraft = "";
		if (this.chatHistoryCount > 0 && line.equals(this.chatHistory[0])) {
			return;
		}
		for (int i = Math.min(this.chatHistoryCount, CHAT_HISTORY_MAX - 1); i > 0; i--) {
			this.chatHistory[i] = this.chatHistory[i - 1];
		}
		this.chatHistory[0] = line;
		if (this.chatHistoryCount < CHAT_HISTORY_MAX) {
			this.chatHistoryCount++;
		}
	}

	@ObfuscatedName("client.a(Ljava/lang/String;BLjava/lang/String;I)V")
	// AUTO-UPDATE (2026-09-21). The server refuses an out-of-date client at login with response 6,
	// "Death Plateau has been updated!". A client the launcher installed - one running from
	// ~/.deathplateau/client.jar, or ~/.lostcity/client.jar where launchers from before the server was
	// named put it - no longer has to be closed and reopened by hand: it starts the
	// launcher (compiled into this jar too, lostcity.Launcher) with the same Java and the same
	// -Dlostcity.* settings, which downloads the new client and opens it, and then this one exits.
	// Any other jar - a dev build run from build/libs, say - is left alone, so a developer's own
	// client is never swapped for the release. Returns whether a restart is on its way.
	// set on the client the launcher starts after an update relaunch - see relaunchForUpdate
	private static final String UPDATE_ATTEMPT = "lostcity.updateattempt";

	private boolean relaunchForUpdate() {
		// ONCE PER CHAIN. The launcher hands the new client this property, so a client that has already
		// been through it does not go round again: between a client release and the server's deploy the
		// newest client IS refused (the server takes only its own revision), and without this the pair
		// looped - refused, relaunch, download nothing new, refused - with no way out but killing it.
		if (System.getProperty(UPDATE_ATTEMPT) != null) {
			return false;
		}
		try {
			java.io.File jar = new java.io.File(Client.class.getProtectionDomain().getCodeSource().getLocation().toURI());
			java.io.File userHome = new java.io.File(System.getProperty("user.home"));
			java.io.File home = new java.io.File(userHome, ".deathplateau");
			java.io.File parent = jar.isFile() ? jar.getCanonicalFile().getParentFile() : null;
			if (parent == null || !(parent.equals(home.getCanonicalFile()) || parent.equals(new java.io.File(userHome, ".lostcity").getCanonicalFile()))) {
				return false;
			}
			// this launcher is the new one, which moves an old install into ~/.deathplateau
			home.mkdirs();
			String bin = System.getProperty("java.home") + java.io.File.separator + "bin" + java.io.File.separator;
			boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
			String javaBin = bin + (windows ? "javaw.exe" : "java");
			if (!new java.io.File(javaBin).isFile()) {
				javaBin = bin + (windows ? "java.exe" : "java");
			}
			final java.util.List<String> cmd = new java.util.ArrayList<>();
			cmd.add(javaBin);
			for (String key : System.getProperties().stringPropertyNames()) {
				if (key.startsWith("lostcity.")) {
					cmd.add("-D" + key + "=" + System.getProperty(key));
				}
			}
			// From a COPY of this jar: on Windows a running jar is locked, and the launcher has to
			// replace client.jar - so it must not be running out of it.
			java.io.File copy = new java.io.File(home, "launcher-run.jar");
			java.nio.file.Files.copy(jar.toPath(), copy.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			cmd.add("-D" + UPDATE_ATTEMPT + "=1"); // the launcher passes every -Dlostcity.* on to the client
			cmd.add("-cp");
			cmd.add(copy.getAbsolutePath());
			cmd.add("lostcity.Launcher");
			final java.io.File dir = home;
			Thread t = new Thread(() -> {
				try {
					Thread.sleep(2000); // long enough to read the message
					// this window goes before the launcher's opens, so there are never two on screen
					ViewBox self = this.frame; // null as an applet, where there is no window of ours
					if (self != null) {
						self.setVisible(false);
						self.dispose();
					}
					new ProcessBuilder(cmd).directory(dir).start();
				} catch (Exception e) {
					e.printStackTrace();
					return;
				}
				System.exit(0);
			}, "update-relaunch");
			t.setDaemon(false);
			t.start();
			return true;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}

	public void addMessage(String arg0, String arg2, int arg3) {
		// DEV: single choke point for essentially all chat/message text - public chat sent and
		// received, private messages sent and received, NPC chat, game/system messages, etc.
		DevLog.log("CHAT", "type=" + arg3 + " from=\"" + arg0 + "\" msg=\"" + arg2 + "\"");
		if (arg3 == 0 && this.stickyChatInterfaceId != -1) {
			this.modalMessage = arg2;
			super.mouseClickButton = 0;
		}
		if (this.chatInterfaceId == -1) {
			this.redrawChatback = true;
		}
		// QoL (2026-09-21): a line wider than the chatbox continues on the next line instead of
		// running off the edge - whatever sent it, game message or chat. See wrapChat.
		java.util.List<String> lines = this.wrapChat(arg0, arg2, arg3);
		int indent = lines.size() > 1 ? this.chatPrefixWidth(arg0, arg2, arg3) : 0;
		for (int i = 0; i < lines.size(); i++) {
			this.pushMessage(arg0, lines.get(i), arg3, i > 0, arg3 == 0 || arg3 == 4 || arg3 == 5 || arg3 == 8 ? 0 : indent);
		}
		// Plugins see the message as it was sent, not the wrapped lines - a handler matching on
		// text should not have to care how wide the chatbox happens to be. Posted after the lines
		// are in, so a plugin that replies with a message of its own cannot interleave with this
		// one. PluginManager guards against the recursion that reply would otherwise cause.
		if (this.plugins != null) {
			this.plugins.onChatMessage(arg0, arg2, arg3);
		}
	}

	// Width the chatbox gives text: its clip stops at the scrollbar and every line starts at x=4. The
	// server wraps its own game messages at 456 (Player.wrappedMessageGame), narrower than this, so
	// nothing it wrapped is split again.
	private static final int CHAT_WIDTH = CHAT_W - 16 - 5;

	// What each message type draws before its text, in the chatbox draw loop below: "Name:" and a
	// crown for public chat, "From [crown]Name:" for private, "To Name:", or the name for trade/duel.
	private int chatPrefixWidth(String sender, String text, int type) {
		PixFont font = this.fontPlain12;
		String name = sender == null ? "" : sender;
		String lead = ChatIcons.leading(name);
		int crown = ChatIcons.iconCount(lead) * ChatIcons.ICON_WIDTH;
		name = name.substring(lead.length());
		switch (type) {
			case 0:
				// A game message has no prefix of its own; its icons are measured where they sit, by
				// wrapChat's ChatIcons.width, rather than reserved at the front of the first line.
				return 0;
			case 1:
			case 2:
				return crown + font.stringWidTag(name) + 8;
			case 3:
			case 7:
				return font.stringWidTag("From ") + crown + font.stringWidTag(name) + 8;
			case 6:
				return font.stringWidTag("To " + name) + 8;
			case 11:
				return font.stringWidTag("[" + this.nextMessageChannel + "] ") + crown + font.stringWidTag(name) + 8;
			case 4:
			case 8:
				return font.stringWidTag(name + " ");
			default:
				return 0;
		}
	}

	// Split on spaces to fit. The first line fits beside its prefix; the rest are indented to sit
	// under it (chat) or start at the margin (game, trade and "To" lines, whose prefix is the line).
	// A word too long for a line on its own is cut. In a game message a colour tag carries onto the next line.
	private java.util.List<String> wrapChat(String sender, String text, int type) {
		java.util.List<String> out = new java.util.ArrayList<>();
		PixFont font = this.fontPlain12;
		if (font == null || text == null) {
			out.add(text);
			return out;
		}
		int prefix = this.chatPrefixWidth(sender, text, type);
		// Only a game message is drawn reading its tags. Every other line - a player's chat above
		// all - is drawn with drawString, which prints "@red@" as five characters, so it is measured
		// the same way and no colour is carried onto its next line.
		boolean tags = type == 0;
		boolean indented = !(type == 0 || type == 4 || type == 5 || type == 8);
		int first = CHAT_WIDTH - prefix;
		int rest = indented ? first : CHAT_WIDTH;
		if (this.chatWidth(font, tags, text) <= first || first < 60) {
			out.add(text);
			return out;
		}
		String colour = "";
		boolean shadow = false;
		String remaining = text;
		int width = first;
		while (remaining.length() > 0) {
			if (this.chatWidth(font, tags, remaining) <= width) {
				out.add(remaining);
				break;
			}
			int cut = -1;
			for (int i = 1; i < remaining.length(); i++) {
				if (remaining.charAt(i) == ' ') {
					if (this.chatWidth(font, tags, remaining.substring(0, i)) > width) {
						break;
					}
					cut = i;
				}
			}
			if (cut <= 0) {
				cut = 1;
				while (cut < remaining.length() && this.chatWidth(font, tags, remaining.substring(0, cut + 1)) <= width) {
					cut++;
				}
			}
			String line = remaining.substring(0, cut);
			out.add(line);
			for (int i = 0; tags && i + 4 < line.length(); i++) {
				if (line.charAt(i) == '@' && line.charAt(i + 4) == '@') {
					String tag = line.substring(i + 1, i + 4);
					if (tag.equals("sh1") || tag.equals("sh0")) {
						shadow = tag.equals("sh1");
					} else if (!tag.startsWith("cr")) {
						colour = tag.equals("bla") ? "" : line.substring(i, i + 5);
					}
					i += 4;
				}
			}
			remaining = remaining.substring(cut).trim();
			if (remaining.length() > 0 && colour.length() > 0) {
				remaining = colour + remaining;
			}
			if (remaining.length() > 0 && shadow) {
				remaining = "@sh1@" + remaining;
			}
			width = rest;
		}
		return out;
	}

	// A chatbox line's width: read for tags and icons (a game message), or every character as it is
	// drawn (chat - see wrapChat).
	private int chatWidth(PixFont font, boolean tags, String text) {
		return tags ? ChatIcons.width(font, text) : font.stringWid(text);
	}

	private void pushMessage(String sender, String text, int type, boolean cont, int indent) {
		for (int var5 = 99; var5 > 0; var5--) {
			this.messageType[var5] = this.messageType[var5 - 1];
			this.messageSender[var5] = this.messageSender[var5 - 1];
			this.messageText[var5] = this.messageText[var5 - 1];
			this.messageCont[var5] = this.messageCont[var5 - 1];
			this.messageIndent[var5] = this.messageIndent[var5 - 1];
			this.messageChannel[var5] = this.messageChannel[var5 - 1];
		}
		this.messageType[0] = type;
		this.messageSender[0] = sender;
		this.messageText[0] = text;
		this.messageCont[0] = cont;
		this.messageIndent[0] = indent;
		this.messageChannel[0] = type == 11 ? this.nextMessageChannel : null;
	}

	// A clan line: "[channel] name: text". The channel rides beside the sender (messageChannel).
	private void addClanMessage(String channel, String sender, String text) {
		this.nextMessageChannel = channel;
		this.addMessage(sender, text, 11);
		this.nextMessageChannel = null;
	}

	// Whether a clan line from this sender shows, by the Clan button's mode.
	private boolean showClanLine(String sender) {
		return this.chatClanMode == 0 || this.chatClanMode == 1 && this.isFriend(sender);
	}

	@ObfuscatedName("client.a(ILjava/lang/String;)Z")
	public boolean isFriend(String arg1) {
		if (arg1 == null) {
			return false;
		}
		for (int var3 = 0; var3 < this.friendCount; var3++) {
			if (arg1.equalsIgnoreCase(this.friendName[var3])) {
				return true;
			}
		}
		return arg1.equalsIgnoreCase(localPlayer.name);
	}

	/**
	 * Whether this name is actually ON the friend list.
	 * <p>
	 * Deliberately NOT {@link #isFriend(String)}: that one answers "may this person's chat reach
	 * me", so it returns true for your own name as well, which would put "Remove friend" on your
	 * own messages. This one is only about list membership, which is what the chat right-click
	 * menu needs to decide between Add and Remove.
	 */
	public boolean isOnFriendList(String arg1) {
		if (arg1 == null) {
			return false;
		}
		for (int var3 = 0; var3 < this.friendCount; var3++) {
			if (arg1.equalsIgnoreCase(this.friendName[var3])) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Whether this name is on the ignore list. The ignore list only keeps base37 names, so the
	 * comparison goes through {@link JString#toBase37(String)} rather than string equality.
	 */
	public boolean isOnIgnoreList(String arg1) {
		if (arg1 == null) {
			return false;
		}
		long var3 = JString.toBase37(arg1);
		if (var3 == 0L) {
			return false;
		}
		for (int var5 = 0; var5 < this.ignoreCount; var5++) {
			if (this.ignoreName37[var5] == var3) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Adds the friend/ignore pair of options to the right-click menu for a name in the chatbox.
	 * <p>
	 * The menu used to offer "Add friend" and "Add ignore" unconditionally, so right-clicking
	 * someone already on your list offered to add them again - and clicking it only got you
	 * "&lt;name&gt; is already on your friend list". It now offers Remove for a name that is
	 * already listed, which is what the later clients do.
	 * <p>
	 * The action ids are the ones {@link #useMenuOption(int)} already handles: 762/574 add,
	 * 775/859 remove. {@code offset} is 0 for the chatbox menu and 2000 for the split-private-chat
	 * overlay, which useMenuOption subtracts back off.
	 */
	public void addSocialMenuOptions(String name, int offset) {
		if (this.isOnIgnoreList(name)) {
			this.menuOption[this.menuSize] = "Remove ignore @whi@" + name;
			this.menuAction[this.menuSize] = offset + 859;
		} else {
			this.menuOption[this.menuSize] = "Add ignore @whi@" + name;
			this.menuAction[this.menuSize] = offset + 574;
		}
		this.menuSize++;
		if (this.isOnFriendList(name)) {
			this.menuOption[this.menuSize] = "Remove friend @whi@" + name;
			this.menuAction[this.menuSize] = offset + 775;
		} else {
			this.menuOption[this.menuSize] = "Add friend @whi@" + name;
			this.menuAction[this.menuSize] = offset + 762;
		}
		this.menuSize++;
	}

	@ObfuscatedName("client.b(JI)V")
	public void addFriend(long arg0) {
		if (arg0 == 0L) {
			return;
		}
		if (this.friendCount >= 100 && this.membersAccount != 1) {
			this.addMessage("", "Your friendlist is full. Max of 100 for free users, and 200 for members", 0);
		} else if (this.friendCount >= 200) {
			this.addMessage("", "Your friendlist is full. Max of 100 for free users, and 200 for members", 0);
		} else {
			String var4 = JString.formatDisplayName(JString.fromBase37(arg0));
			for (int var5 = 0; var5 < this.friendCount; var5++) {
				if (this.friendName37[var5] == arg0) {
					this.addMessage("", var4 + " is already on your friend list", 0);
					return;
				}
			}
			for (int var6 = 0; var6 < this.ignoreCount; var6++) {
				if (this.ignoreName37[var6] == arg0) {
					this.addMessage("", "Please remove " + var4 + " from your ignore list first", 0);
					return;
				}
			}
			if (!var4.equals(localPlayer.name)) {
				this.friendName[this.friendCount] = var4;
				this.friendName37[this.friendCount] = arg0;
				this.friendWorld[this.friendCount] = 0;
				this.friendCount++;
				this.redrawSidebar = true;
				// FRIENDLIST_ADD
				this.out.p1isaac(120);
				this.out.p8(arg0);
			}
		}
	}

	@ObfuscatedName("client.a(JI)V")
	public void removeFriend(long arg0) {
		if (arg0 == 0L) {
			return;
		}
		for (int var4 = 0; var4 < this.friendCount; var4++) {
			if (this.friendName37[var4] == arg0) {
				this.friendCount--;
				this.redrawSidebar = true;
				for (int var5 = var4; var5 < this.friendCount; var5++) {
					this.friendName[var5] = this.friendName[var5 + 1];
					this.friendWorld[var5] = this.friendWorld[var5 + 1];
					this.friendName37[var5] = this.friendName37[var5 + 1];
				}
				// FRIENDLIST_DEL
				this.out.p1isaac(141);
				this.out.p8(arg0);
				break;
			}
		}
	}

	@ObfuscatedName("client.a(IJ)V")
	public void addIgnore(long arg1) {
		if (arg1 == 0L) {
			return;
		}
		if (this.ignoreCount >= 100) {
			this.addMessage("", "Your ignore list is full. Max of 100 hit", 0);
			return;
		}
		String var4 = JString.formatDisplayName(JString.fromBase37(arg1));
		for (int var5 = 0; var5 < this.ignoreCount; var5++) {
			if (this.ignoreName37[var5] == arg1) {
				this.addMessage("", var4 + " is already on your ignore list", 0);
				return;
			}
		}
		for (int var6 = 0; var6 < this.friendCount; var6++) {
			if (this.friendName37[var6] == arg1) {
				this.addMessage("", "Please remove " + var4 + " from your friend list first", 0);
				return;
			}
		}
		this.ignoreName37[this.ignoreCount++] = arg1;
		this.redrawSidebar = true;
		// IGNORELIST_ADD
		this.out.p1isaac(217);
		this.out.p8(arg1);
	}

	@ObfuscatedName("client.b(IJ)V")
	public void removeIgnore(long arg1) {
		if (arg1 == 0L) {
			return;
		}
		for (int var4 = 0; var4 < this.ignoreCount; var4++) {
			if (this.ignoreName37[var4] == arg1) {
				this.ignoreCount--;
				this.redrawSidebar = true;
				for (int var5 = var4; var5 < this.ignoreCount; var5++) {
					this.ignoreName37[var5] = this.ignoreName37[var5 + 1];
				}
				// IGNORELIST_DEL
				this.out.p1isaac(160);
				this.out.p8(arg1);
				break;
			}
		}
	}

	@ObfuscatedName("client.H(I)V")
	public void unloadTitle() {
		this.flameActive = false;
		while (this.flameThread) {
			this.flameActive = false;
			try {
				Thread.sleep(50L);
			} catch (Exception var2) {
			}
		}
		this.imageTitlebox = null;
		this.imageTitlebutton = null;
		this.imageRunes = null;
		this.flameGradient = null;
		this.flameGradient0 = null;
		this.flameGradient1 = null;
		this.flameGradient2 = null;
		this.flameBuffer0 = null;
		this.flameBuffer1 = null;
		this.flameBuffer2 = null;
		this.flameBuffer3 = null;
		this.imageFlamesLeft = null;
		this.imageFlamesRight = null;
		this.titleBlur = null;
	}

	@ObfuscatedName("client.c(B)V")
	public void runFlames() {
		this.flameThread = true;
		try {
			long var3 = System.currentTimeMillis();
			int var5 = 0;
			int var6 = 20;
			while (this.flameActive) {
				this.flameCycle++;
				this.updateFlames();
				this.updateFlames();
				this.drawFlames();
				var5++;
				if (var5 > 10) {
					long var7 = System.currentTimeMillis();
					int var9 = (int) (var7 - var3) / 10 - var6;
					var6 = 40 - var9;
					if (var6 < 5) {
						var6 = 5;
					}
					var5 = 0;
					var3 = var7;
				}
				try {
					Thread.sleep((long) var6);
				} catch (Exception var10) {
				}
			}
		} catch (Exception var11) {
		}
		this.flameThread = false;
	}

	@ObfuscatedName("client.i(B)V")
	public void updateFlames() {
		short var2 = 256;
		for (int var3 = 10; var3 < 117; var3++) {
			int var20 = (int) (Math.random() * 100.0D);
			if (var20 < 50) {
				this.flameBuffer2[(var2 - 2 << 7) + var3] = 255;
			}
		}
		for (int var4 = 0; var4 < 100; var4++) {
			int var17 = (int) (Math.random() * 124.0D) + 2;
			int var18 = (int) (Math.random() * 128.0D) + 128;
			int var19 = (var18 << 7) + var17;
			this.flameBuffer2[var19] = 192;
		}
		for (int var5 = 1; var5 < var2 - 1; var5++) {
			for (int var15 = 1; var15 < 127; var15++) {
				int var16 = (var5 << 7) + var15;
				this.flameBuffer3[var16] = (this.flameBuffer2[var16 - 1] + this.flameBuffer2[var16 + 1] + this.flameBuffer2[var16 - 128] + this.flameBuffer2[var16 + 128]) / 4;
			}
		}
		this.flameCycle0 += 128;
		if (this.flameCycle0 > this.flameBuffer0.length) {
			this.flameCycle0 -= this.flameBuffer0.length;
			int var6 = (int) (Math.random() * 12.0D);
			this.updateFlameBuffer(this.imageRunes[var6]);
		}
		for (int var7 = 1; var7 < var2 - 1; var7++) {
			for (int var12 = 1; var12 < 127; var12++) {
				int var13 = (var7 << 7) + var12;
				int var14 = this.flameBuffer3[var13 + 128] - this.flameBuffer0[this.flameCycle0 + var13 & this.flameBuffer0.length - 1] / 5;
				if (var14 < 0) {
					var14 = 0;
				}
				this.flameBuffer2[var13] = var14;
			}
		}
		for (int var10 = 0; var10 < var2 - 1; var10++) {
			this.flameLineOffset[var10] = this.flameLineOffset[var10 + 1];
		}
		this.flameLineOffset[var2 - 1] = (int) (Math.sin((double) loopCycle / 14.0D) * 16.0D + Math.sin((double) loopCycle / 15.0D) * 14.0D + Math.sin((double) loopCycle / 16.0D) * 12.0D);
		if (this.flameGradientCycle0 > 0) {
			this.flameGradientCycle0 -= 4;
		}
		if (this.flameGradientCycle1 > 0) {
			this.flameGradientCycle1 -= 4;
		}
		if (this.flameGradientCycle0 == 0 && this.flameGradientCycle1 == 0) {
			int var11 = (int) (Math.random() * 2000.0D);
			if (var11 == 0) {
				this.flameGradientCycle0 = 1024;
			}
			if (var11 == 1) {
				this.flameGradientCycle1 = 1024;
			}
		}
	}

	@ObfuscatedName("client.a(LWRRBQEHV;I)V")
	public void updateFlameBuffer(Pix8 arg0) {
		short var3 = 256;
		for (int var4 = 0; var4 < this.flameBuffer0.length; var4++) {
			this.flameBuffer0[var4] = 0;
		}
		for (int var5 = 0; var5 < 5000; var5++) {
			int var17 = (int) (Math.random() * 128.0D * (double) var3);
			this.flameBuffer0[var17] = (int) (Math.random() * 256.0D);
		}
		for (int var6 = 0; var6 < 20; var6++) {
			for (int var13 = 1; var13 < var3 - 1; var13++) {
				for (int var15 = 1; var15 < 127; var15++) {
					int var16 = (var13 << 7) + var15;
					this.flameBuffer1[var16] = (this.flameBuffer0[var16 - 1] + this.flameBuffer0[var16 + 1] + this.flameBuffer0[var16 - 128] + this.flameBuffer0[var16 + 128]) / 4;
				}
			}
			int[] var14 = this.flameBuffer0;
			this.flameBuffer0 = this.flameBuffer1;
			this.flameBuffer1 = var14;
		}
		if (arg0 != null) {
			int var7 = 0;
			for (int var8 = 0; var8 < arg0.hi; var8++) {
				for (int var9 = 0; var9 < arg0.wi; var9++) {
					if (arg0.pixels[var7++] != 0) {
						int var10 = var9 + 16 + arg0.xof;
						int var11 = var8 + 16 + arg0.yof;
						int var12 = (var11 << 7) + var10;
						this.flameBuffer0[var12] = 0;
					}
				}
			}
		}
	}

	@ObfuscatedName("client.y(I)V")
	public void drawFlames() {
		short var2 = 256;
		if (this.flameGradientCycle0 > 0) {
			for (int var3 = 0; var3 < 256; var3++) {
				if (this.flameGradientCycle0 > 768) {
					this.flameGradient[var3] = this.mix(this.flameGradient0[var3], this.flameGradient1[var3], 1024 - this.flameGradientCycle0);
				} else if (this.flameGradientCycle0 > 256) {
					this.flameGradient[var3] = this.flameGradient1[var3];
				} else {
					this.flameGradient[var3] = this.mix(this.flameGradient1[var3], this.flameGradient0[var3], 256 - this.flameGradientCycle0);
				}
			}
		} else if (this.flameGradientCycle1 > 0) {
			for (int var4 = 0; var4 < 256; var4++) {
				if (this.flameGradientCycle1 > 768) {
					this.flameGradient[var4] = this.mix(this.flameGradient0[var4], this.flameGradient2[var4], 1024 - this.flameGradientCycle1);
				} else if (this.flameGradientCycle1 > 256) {
					this.flameGradient[var4] = this.flameGradient2[var4];
				} else {
					this.flameGradient[var4] = this.mix(this.flameGradient2[var4], this.flameGradient0[var4], 256 - this.flameGradientCycle1);
				}
			}
		} else {
			for (int var5 = 0; var5 < 256; var5++) {
				this.flameGradient[var5] = this.flameGradient0[var5];
			}
		}
		for (int var6 = 0; var6 < 33920; var6++) {
			this.imageTitle0.data[var6] = this.imageFlamesLeft.pixels[var6];
		}
		int var7 = 0;
		int var8 = 1152;
		for (int var9 = 1; var9 < var2 - 1; var9++) {
			int var24 = (var2 - var9) * this.flameLineOffset[var9] / var2;
			int var25 = var24 + 22;
			if (var25 < 0) {
				var25 = 0;
			}
			var7 += var25;
			for (int var26 = var25; var26 < 128; var26++) {
				int var27 = this.flameBuffer2[var7++];
				if (var27 == 0) {
					var8++;
				} else {
					int var29 = 256 - var27;
					int var30 = this.flameGradient[var27];
					int var31 = this.imageTitle0.data[var8];
					this.imageTitle0.data[var8++] = ((var30 & 0xFF00FF) * var27 + (var31 & 0xFF00FF) * var29 & 0xFF00FF00) + ((var30 & 0xFF00) * var27 + (var31 & 0xFF00) * var29 & 0xFF0000) >> 8;
				}
			}
			var8 += var25;
		}
		this.imageTitle0.draw(0, 0, super.graphics);
		for (int var11 = 0; var11 < 33920; var11++) {
			this.imageTitle1.data[var11] = this.imageFlamesRight.pixels[var11];
		}
		int var12 = 0;
		int var13 = 1176;
		for (int var14 = 1; var14 < var2 - 1; var14++) {
			int var15 = (var2 - var14) * this.flameLineOffset[var14] / var2;
			int var16 = 103 - var15;
			int var17 = var13 + var15;
			for (int var18 = 0; var18 < var16; var18++) {
				int var19 = this.flameBuffer2[var12++];
				if (var19 == 0) {
					var17++;
				} else {
					int var21 = 256 - var19;
					int var22 = this.flameGradient[var19];
					int var23 = this.imageTitle1.data[var17];
					this.imageTitle1.data[var17++] = ((var22 & 0xFF00FF) * var19 + (var23 & 0xFF00FF) * var21 & 0xFF00FF00) + ((var22 & 0xFF00) * var19 + (var23 & 0xFF00) * var21 & 0xFF0000) >> 8;
				}
			}
			var12 += 128 - var16;
			var13 = 128 - var16 - var15 + var17;
		}
		this.imageTitle1.draw(0, 637, super.graphics);
	}

	@ObfuscatedName("client.b(IIII)I")
	public int mix(int arg0, int arg1, int arg2) {
		int var5 = 256 - arg2;
		return ((arg0 & 0xFF00FF) * var5 + (arg1 & 0xFF00FF) * arg2 & 0xFF00FF00) + ((arg0 & 0xFF00) * var5 + (arg1 & 0xFF00) * arg2 & 0xFF0000) >> 8;
	}

	@ObfuscatedName("client.a(Ljava/lang/String;I)V")
	public void searchObjNames(String arg0) {
		if (arg0 == null || arg0.length() == 0) {
			this.field158 = 0;
			return;
		}
		String var3 = arg0;
		String[] var4 = new String[100];
		int var5 = 0;
		while (true) {
			int var6 = var3.indexOf(" ");
			if (var6 == -1) {
				String var8 = var3.trim();
				if (var8.length() > 0) {
					var4[var5++] = var8.toLowerCase();
				}
				this.field158 = 0;
				label56: for (int var9 = 0; var9 < ObjType.field817; var9++) {
					ObjType var10 = ObjType.get(var9);
					if (var10.field825 == -1 && var10.field811 != null) {
						String var11 = var10.field811.toLowerCase();
						for (int var12 = 0; var12 < var5; var12++) {
							if (var11.indexOf(var4[var12]) == -1) {
								continue label56;
							}
						}
						this.field159[this.field158] = var11;
						this.field160[this.field158] = var9;
						this.field158++;
						if (this.field158 >= this.field159.length) {
							return;
						}
					}
				}
				return;
			}
			String var7 = var3.substring(0, var6).trim();
			if (var7.length() > 0) {
				var4[var5++] = var7.toLowerCase();
			}
			var3 = var3.substring(var6 + 1);
		}
	}

	@ObfuscatedName("client.a(Ljava/lang/String;)V")
	public void showError(String err) {
		System.out.println(err);

		try {
			this.getAppletContext().showDocument(new URL(this.getCodeBase(), "loaderror_" + err + ".html"));
		} catch (Exception ex) {
			ex.printStackTrace();
		}

		while (true) {
			try {
				Thread.sleep(1000L);
			} catch (Exception ignore) {
			}
		}
	}

	@ObfuscatedName("client.a(ZI)V")
	public void unloadCom(int arg1) {
		Component.unloadCom(arg1);
	}

	@ObfuscatedName("client.k(Z)V")
	public void getJagCrc() {
		int var2 = 5;
		this.jagChecksum[8] = 0;
		int var4 = 0;
		while (this.jagChecksum[8] == 0) {
			String var5 = "Unknown problem";
			this.drawProgress(20, "Connecting to web server");
			try {
				DataInputStream var6 = this.openUrl("crc" + (int) (Math.random() * 9.9999999E7D) + "-" + 377);
				Packet var7 = new Packet(new byte[40]);
				var6.readFully(var7.data, 0, 40);
				var6.close();
				for (int var8 = 0; var8 < 9; var8++) {
					this.jagChecksum[var8] = var7.g4();
				}
				int var9 = var7.g4();
				int var10 = 1234;
				for (int var11 = 0; var11 < 9; var11++) {
					var10 = (var10 << 1) + this.jagChecksum[var11];
				}
				if (var9 != var10) {
					var5 = "checksum problem";
					this.jagChecksum[8] = 0;
				}
			} catch (EOFException var14) {
				var5 = "EOF problem";
				this.jagChecksum[8] = 0;
			} catch (IOException var15) {
				var5 = "connection problem";
				this.jagChecksum[8] = 0;
			} catch (Exception var16) {
				var5 = "logic problem";
				this.jagChecksum[8] = 0;
				if (!signlink.reporterror) {
					return;
				}
			}
			if (this.jagChecksum[8] == 0) {
				var4++;
				for (int var12 = var2; var12 > 0; var12--) {
					if (var4 >= 10) {
						this.drawProgress(10, "Game updated - please reload page");
						var12 = 10;
					} else {
						this.drawProgress(10, var5 + " - Will retry in " + var12 + " secs.");
					}
					try {
						Thread.sleep(1000L);
					} catch (Exception var13) {
					}
				}
				var2 *= 2;
				if (var2 > 60) {
					var2 = 60;
				}
				this.field196 = !this.field196;
			}
		}
	}

	@ObfuscatedName("client.a(IB)Ljava/lang/String;")
	public String getDateString(int arg0) {
		if (arg0 > this.currentDay + 10) {
			return "Unknown";
		}
		long var3 = ((long) arg0 + 11745L) * 86400000L;
		Calendar var5 = Calendar.getInstance();
		var5.setTime(new Date(var3));
		int var6 = var5.get(Calendar.DATE);
		int var7 = var5.get(Calendar.MONTH);
		int var8 = var5.get(Calendar.YEAR);
		String[] var9 = new String[] { "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec" };
		return var6 + "-" + var9[var7] + "-" + var8;
	}

	public AppletContext getAppletContext() {
		return signlink.mainapp == null ? super.getAppletContext() : signlink.mainapp.getAppletContext();
	}

	@ObfuscatedName("client.a(ILjava/lang/String;Ljava/lang/String;)V")
	/**
	 * What to say under "Loading - please wait" when the scene has plainly stopped coming in, or null
	 * while it is still making normal progress.
	 *
	 * The four codes are checkScene's own, and they are worth telling apart because they fail in
	 * different places: -1 and -2 are a map or scenery FILE that has not arrived, so the download is
	 * the thing to look at; -3 is a loc whose models cannot be resolved, which never recovers on its
	 * own and means this client's cache does not match the world it is on; -4 is waiting on the
	 * server. The outstanding file count separates a download still running from one that has
	 * finished and left the scene short anyway.
	 */
	private String sceneStuckNote() {
		if (this.sceneState != 1 || this.sceneStuckReason == 0) {
			return null;
		}
		if (System.currentTimeMillis() - this.sceneLoadBegan < SCENE_STUCK_MS) {
			return null;
		}
		String why;
		if (this.sceneStuckReason == -1) {
			why = "still waiting for map data";
		} else if (this.sceneStuckReason == -2) {
			why = "still waiting for scenery data";
		} else if (this.sceneStuckReason == -3) {
			why = "scenery models will not load - this client's cache does not match this world";
		} else if (this.sceneStuckReason == -4) {
			why = "waiting for the server";
		} else {
			why = "not finishing";
		}
		int outstanding = this.onDemand == null ? -1 : this.onDemand.remaining();
		return why + " (code " + this.sceneStuckReason + ", " + outstanding + " files outstanding)";
	}

	public void showPopupMessage(String arg1, String arg2) {
		if (this.areaViewport != null) {
			this.areaViewport.bind();
			Pix3D.lineOffset = this.areaViewportOffset;
			int var4 = this.layout.mainY + 151;
			int cx = this.layout.mainX + 256;
			if (arg1 != null) {
				var4 -= 7;
			}
			this.fontPlain12.centreString(cx + 1, var4, 0, arg2);
			this.fontPlain12.centreString(cx, var4 - 1, 16777215, arg2);
			var4 += 15;
			if (arg1 != null) {
				this.fontPlain12.centreString(cx + 1, var4, 0, arg1);
				this.fontPlain12.centreString(cx, var4 - 1, 16777215, arg1);
			}
			this.areaViewport.draw(4, 4, super.graphics);
		} else if (super.drawArea != null) {
			super.drawArea.bind();
			Pix3D.lineOffset = this.areaFullscreenOffset;
			int var5 = 251;
			short var6 = 300;
			byte var7 = 50;
			Pix2D.fillRect(var7, var5 - 5 - var7 / 2, 0, var6, 383 - var6 / 2);
			Pix2D.drawRect(var5 - 5 - var7 / 2, var7, 16777215, 383 - var6 / 2, var6);
			if (arg1 != null) {
				var5 -= 7;
			}
			this.fontPlain12.centreString(383, var5, 0, arg2);
			this.fontPlain12.centreString(382, var5 - 1, 16777215, arg2);
			var5 += 15;
			if (arg1 != null) {
				this.fontPlain12.centreString(383, var5, 0, arg1);
				this.fontPlain12.centreString(382, var5 - 1, 16777215, arg1);
			}
			super.drawArea.draw(0, 0, super.graphics);
		}
	}

	@ObfuscatedName("client.J(I)V")
	public void prepareFullGame() {
		if (super.drawArea != null) {
			return;
		}
		this.unloadTitle();
		this.imageTitle2 = null;
		this.imageTitle3 = null;
		this.imageTitle4 = null;
		this.imageTitle0 = null;
		this.imageTitle1 = null;
		this.imageTitle5 = null;
		this.imageTitle6 = null;
		this.imageTitle7 = null;
		this.imageTitle8 = null;
		this.areaChatback = null;
		this.areaMapback = null;
		this.areaSidebar = null;
		this.areaViewport = null;
		this.areaBackbase1 = null;
		this.areaBackbase2 = null;
		this.areaBackmid1 = null;
		super.drawArea = new PixMap(503, this.getBaseComponent(), 765);
		this.redrawFrame = true;
	}

	// ---------------------------------------------------------------------------------------------
	// Resizable mode (F9, "Resizable window"). Layout says where everything goes and why; this is the
	// client's side of it.
	//
	// In fixed mode none of this runs: the layout is Layout.fixed(), PixMap.target is null, the mouse
	// is AWT's, and every area goes on the screen where 377 put it.
	//
	// In resizable mode the areas of the fixed frame (the stone, the sidebar, the chat, the tab rows,
	// the minimap) are still drawn exactly as they always were, but PixMap.target (this) catches them
	// and copies them into frameBuffer - the fixed frame, 765x503 - instead of the screen. The viewport
	// is the whole window. Each frame presentGame() copies the three panels out of frameBuffer onto
	// the viewport where the window has room for them, draws an open viewport menu over the lot, and
	// puts the viewport on the screen in one piece. The title screen and a fullscreen interface,
	// which are 765x503 by nature, are drawn in the middle of the window.

	private Layout layout = Layout.fixed();
	/** What the player asked for; the layout follows it at the next frame (syncLayout). */
	private int wantMode = Layout.FIXED;
	/** The fixed frame the panels are cut from, in resizable mode. */
	private int[] frameBuffer;
	/** The window's Graphics, and the same translated to where a 765x503 screen is centred. */
	private java.awt.Graphics screenGraphics;
	private java.awt.Graphics letterGraphics;
	/** The window has been filled around the centred 765x503 screen. */
	private boolean letterCleared;
	/** The title art shrunk, blurred and dimmed, for filling the window around the centred screen. */
	private PixMap titleBlur;
	/**
	 * How small the blurred copy is kept. 128x84 holds the 765x503 art's shape to within a pixel of
	 * its aspect, and shrinking that far is itself most of the blur: one pixel of it is the average
	 * of about 36 of the art's, so nothing sharp survives the shrink.
	 */
	private static final int BLUR_W = 128;
	private static final int BLUR_H = 84;
	/** Box passes over the small copy, to take out the blockiness that averaging leaves behind. */
	private static final int BLUR_PASSES = 3;
	/**
	 * How much of the art's brightness the surround keeps, out of 256. Dark enough that the crisp
	 * title screen in the middle is plainly the brighter thing, and the one to look at.
	 */
	private static final int BLUR_DIM = 108;
	/** How far the shadow around the centred screen reaches, and how dark it is where it meets it. */
	private static final int SHADOW = 26;
	private static final int SHADOW_ALPHA = 120;
	/** World3D.init's pitch distances, kept from load() to rebuild its visibility for a new viewport. */
	private int[] sceneDistances;
	/** The viewport World3D's visibility tables were last built for. */
	private int sceneVisW;
	private int sceneVisH;
	private int sceneVisZoom;
	private int sceneVisDistance;
	/** When the layout last changed: the visibility tables and the saved size wait for it to settle. */
	private long layoutChangedAt;
	private boolean layoutSettled = true;

	/**
	 * Move the window to one of the three display modes (Layout.FIXED/CLASSIC/MODERN) and remember
	 * it. Takes effect at the next frame. The window itself only has two states - the fixed 765x503
	 * or free to be dragged - so switching between the two resizable layouts leaves it alone and
	 * only the panels move.
	 */
	public void setDisplayMode(int mode) {
		this.wantMode = mode < Layout.FIXED || mode >= Layout.MODES ? Layout.FIXED : mode;
		DisplaySettings.setMode(this.wantMode);
		if (super.frame != null) {
			super.frame.setMode(this.wantMode != Layout.FIXED, DisplaySettings.width(), DisplaySettings.height(), DisplaySettings.maximized());
		}
	}

	/** Fixed or the classic layout; kept for what only wants to know whether the window is free. */
	public void setResizable(boolean on) {
		this.setDisplayMode(on ? Layout.CLASSIC : Layout.FIXED);
	}

	public boolean isResizable() {
		return this.layout.resizable;
	}

	public int displayMode() {
		return this.layout.mode;
	}

	/** Bring the layout into line with the request and the window's size. Game thread only. */
	public void syncLayout() {
		Layout next;
		if (this.wantMode == Layout.FIXED) {
			if (!this.layout.resizable) {
				return;
			}
			next = Layout.fixed();
		} else {
			java.awt.Component c = this.getBaseComponent();
			int w = c.getWidth();
			int h = c.getHeight();
			if (w <= 0 || h <= 0) {
				return;
			}
			next = Layout.resizable(this.wantMode, w, h);
		}
		if (next.sameAs(this.layout)) {
			if (!this.layoutSettled && this.sceneDistances != null && System.currentTimeMillis() - this.layoutChangedAt > 250L) {
				this.layoutSettled = true;
				this.updateSceneVisibility();
				if (this.layout.resizable && super.frame != null) {
					DisplaySettings.setWindow(this.layout.width, this.layout.height, super.frame.isMaximized());
				}
			}
			return;
		}
		this.applyLayout(next);
	}

	private void applyLayout(Layout next) {
		Layout was = this.layout;
		boolean first = was.resizable != next.resizable;
		this.layout = next;
		super.remapMouse = next.resizable;
		PixMap.target = next.resizable ? this : null;

		java.awt.Graphics g = this.acquireGraphics();
		if (g != null) {
			// (The old ones are left to the collector rather than disposed: the title screen's flame
			// thread may be half way through drawing with one.)
			this.screenGraphics = g;
			if (next.resizable) {
				this.letterGraphics = g.create();
				this.letterGraphics.translate(next.letterX, next.letterY);
			} else {
				this.letterGraphics = g;
			}
			super.graphics = next.resizable && !this.ingame ? this.letterGraphics : this.screenGraphics;
			if (was.resizable && !next.resizable) {
				// until the window has shrunk back to 765x503, nothing of the resizable screen is left
				// showing around the fixed one
				g.setColor(java.awt.Color.black);
				g.fillRect(0, 0, was.width, was.height);
			}
		}
		if (next.resizable) {
			if (this.frameBuffer == null) {
				this.frameBuffer = new int[Layout.FIXED_W * Layout.FIXED_H];
			}
		} else {
			this.frameBuffer = null;
		}
		if (this.areaViewport != null) {
			this.createViewportArea();
			this.areaViewport.bind();
			Pix3D.lineOffset = this.areaViewportOffset;
		}
		// Switching mode rebuilds the scene's visibility now; a window being dragged to a new size
		// waits until it stops (syncLayout), drawing with the last size's tables meanwhile.
		this.layoutChangedAt = System.currentTimeMillis();
		this.layoutSettled = false;
		if (first || this.sceneVisW == 0) {
			this.updateSceneVisibility();
		}
		// A menu laid out for the old viewport would be somewhere else now.
		if (this.menuVisible) {
			this.menuVisible = false;
			this.menuSwapMode = false;
		}
		if (!next.resizable) {
			super.mouseX = super.rawMouseX;
			super.mouseY = super.rawMouseY;
		}
		this.letterCleared = false;
		this.redrawFrame = true;
	}

	/** The viewport's buffer and scanline table, at the layout's size. */
	private void createViewportArea() {
		this.areaViewport = new PixMap(this.layout.vpH, this.getBaseComponent(), this.layout.vpW);
		Pix3D.init3D(this.layout.vpH, this.layout.vpW);
		this.areaViewportOffset = Pix3D.lineOffset;
	}

	/**
	 * World3D precomputes, for each camera angle, which tiles can land inside the viewport. That
	 * depends on the viewport's size and the projection, so a new layout needs new tables. On the
	 * fixed screen they are built once, in load(), exactly as 377 built them.
	 */
	private void updateSceneVisibility() {
		if (this.sceneDistances == null) {
			return;
		}
		int w = this.layout.vpW;
		int h = this.layout.vpH;
		int zoom = this.layout.zoom;
		int dist = DisplaySettings.drawDistance();
		if (w == this.sceneVisW && h == this.sceneVisH && zoom == this.sceneVisZoom && dist == this.sceneVisDistance) {
			return;
		}
		int was = Pix3D.zoom;
		Pix3D.zoom = zoom;
		World3D.drawDistance = dist;
		World3D.init(h, this.sceneDistances, 800, 500, w);
		Pix3D.zoom = was;
		this.sceneVisW = w;
		this.sceneVisH = h;
		this.sceneVisZoom = zoom;
		this.sceneVisDistance = dist;
	}

	/**
	 * How far the scene is drawn, in tiles (DisplaySettings.DRAW_DISTANCES). Rebuilding World3D's
	 * per-angle visibility tables takes a moment - a fraction of a second at the far end - so it is
	 * done here, once, when the row is clicked, and not per frame. Game thread only: the tables and
	 * the radius the tile loops index them with are two statics, and a frame half way through
	 * reading them while they change would read off the end of one.
	 */
	public void setDrawDistance(int tiles) {
		DisplaySettings.setDrawDistance(tiles);
		this.updateSceneVisibility();
		this.redrawFrame = true;
	}

	public int drawDistance() {
		return DisplaySettings.drawDistance();
	}

	/**
	 * Resizable: turn the raw canvas mouse into the client's coordinates (see Layout). An open
	 * menu, or an item being dragged, keeps the mouse in the area it belongs to, so its coordinates
	 * run on smoothly past that area's edge instead of jumping into another one.
	 */
	public void mapInput() {
		this.syncLayout();
		if (!this.layout.resizable) {
			return;
		}
		int rx = super.rawMouseX;
		int ry = super.rawMouseY;
		int cx = super.rawClickX;
		int cy = super.rawClickY;
		if (!this.ingame || this.fullscreenInterfaceId0 != -1) {
			int lx = this.layout.letterX;
			int ly = this.layout.letterY;
			super.mouseX = rx == -1 && ry == -1 ? -1 : rx - lx;
			super.mouseY = rx == -1 && ry == -1 ? -1 : ry - ly;
			super.mouseClickX = cx - lx;
			super.mouseClickY = cy - ly;
			return;
		}
		int area = this.mouseArea();
		super.mouseX = this.layout.mapX(rx, ry, area);
		super.mouseY = this.layout.mapY(rx, ry, area);
		super.mouseClickX = this.layout.mapX(cx, cy, area);
		super.mouseClickY = this.layout.mapY(cx, cy, area);
	}

	private int mouseArea() {
		if (this.menuVisible) {
			return this.menuArea == 1 ? Layout.SIDEBAR : this.menuArea == 2 ? Layout.CHAT : Layout.VIEWPORT;
		}
		if (this.objDragArea == 1 || this.bankTabDragFrom >= 1) {
			return Layout.VIEWPORT;
		}
		if (this.objDragArea == 2) {
			return Layout.SIDEBAR;
		}
		if (this.objDragArea == 3) {
			return Layout.CHAT;
		}
		return Layout.ANY;
	}

	/**
	 * Resizable, at the top of each frame: the title screen draws on the centred Graphics and the
	 * game on the window's, and the first centred frame fills the window around it.
	 */
	private void prepareResizableFrame() {
		if (this.screenGraphics == null) {
			return;
		}
		boolean letter = !this.ingame || super.drawArea != null;
		if (letter && !this.letterCleared) {
			this.fillTitleSurround(this.screenGraphics, this.layout.width, this.layout.height);
			this.letterCleared = true;
			this.redrawFrame = true;
		} else if (!letter) {
			this.letterCleared = false;
		}
		super.graphics = this.ingame ? this.screenGraphics : this.letterGraphics;
	}

	/**
	 * The window behind the centred 765x503 screen - the title screen and the loading bar. It used
	 * to be black, so the login screen sat in a letterbox on anything bigger than 765x503; the title
	 * art fills it instead, and the real title screen is drawn crisp over the middle of it as
	 * before, so the logo, the login box and its buttons are pixel for pixel what they always were.
	 *
	 * BLURRED AND DIMMED, not tiled and not drawn sharp. This was tiled at first - the art is a hall
	 * mirrored down its own middle, so a flipped copy joins it edge to edge with no seam - and the
	 * seam was never the problem. The problem is that the picture has no outside: its left and right
	 * 128 pixels are the brazier strips (imageTitle0 and imageTitle1), which end in a hard rectangle
	 * because at 765x503 that edge IS the edge of the screen and nobody ever sees it. Repeat the art
	 * and those rectangles land in the middle of the window, so the fire reads as two boxes pasted on
	 * the wall. Drawing it sharp at any size has the same trouble somewhere.
	 *
	 * So the surround is the art with everything that could show a join taken out of it: shrunk to
	 * BLUR_W x BLUR_H by averaging, box-blurred BLUR_PASSES times and dimmed to BLUR_DIM/256, then
	 * scaled back over the whole window. What is left is the shape and colour of the hall - dark
	 * stone, a warm glow at each side where the braziers were - with no edge anywhere in it, and the
	 * real title screen sits crisp in the middle of it, plainly the thing you are meant to look at.
	 * The blur is built once with the art, so the only per-frame cost is one scaled blit.
	 *
	 * In game (a fullscreen interface over the scene) there is no title art loaded and the surround
	 * stays black, as it was.
	 */
	private void fillTitleSurround(java.awt.Graphics g, int w, int h) {
		PixMap blur = this.titleBlur;
		if (blur == null || this.ingame) {
			g.setColor(java.awt.Color.black);
			g.fillRect(0, 0, w, h);
			return;
		}
		blur.setPixels();
		// Bilinear for this blit and this blit only. The hint lives on the Graphics, and the centred
		// title screen is drawn through the same one - it must stay nearest-neighbour or every pixel
		// of the login box goes soft.
		java.awt.Graphics2D g2 = g instanceof java.awt.Graphics2D ? (java.awt.Graphics2D) g : null;
		Object previous = null;
		if (g2 != null) {
			previous = g2.getRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION);
			g2.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		}
		g.drawImage(blur.image, 0, 0, w, h, blur);
		if (g2 != null) {
			g2.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
				previous == null ? java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR : previous);
			// A shadow hugging the centred screen, so its edge reads as the edge of something laid on
			// the window rather than as the art stopping. Rings of black a pixel thick, fading outwards,
			// drawn OUTSIDE the 765x503 - the title screen is blitted over that rectangle every frame
			// and would wipe anything drawn inside it.
			int lx = this.layout.letterX;
			int ly = this.layout.letterY;
			for (int i = 0; i < SHADOW; i++) {
				int alpha = SHADOW_ALPHA * (SHADOW - i) / SHADOW;
				g2.setColor(new java.awt.Color(0, 0, 0, alpha));
				g2.drawRect(lx - i - 1, ly - i - 1, Layout.FIXED_W + 2 * i + 1, Layout.FIXED_H + 2 * i + 1);
			}
		}
	}

	/**
	 * The art shrunk, blurred and dimmed, for fillTitleSurround. Shrinking by averaging whole blocks
	 * is the blur that does most of the work - 765x503 down to 128x84 throws away every edge in the
	 * picture at once - and the box passes after it take the blockiness out of what is left, so that
	 * scaling it back up bilinear has only smooth gradients to interpolate.
	 */
	private PixMap blurredBackdrop(PixMap src) {
		int[] small = new int[BLUR_W * BLUR_H];
		for (int y = 0; y < BLUR_H; y++) {
			int y0 = y * src.height / BLUR_H;
			int y1 = (y + 1) * src.height / BLUR_H;
			for (int x = 0; x < BLUR_W; x++) {
				int x0 = x * src.width / BLUR_W;
				int x1 = (x + 1) * src.width / BLUR_W;
				int r = 0;
				int gr = 0;
				int b = 0;
				int n = 0;
				for (int sy = y0; sy < y1; sy++) {
					int row = sy * src.width;
					for (int sx = x0; sx < x1; sx++) {
						int p = src.data[row + sx];
						r += p >> 16 & 0xFF;
						gr += p >> 8 & 0xFF;
						b += p & 0xFF;
						n++;
					}
				}
				small[y * BLUR_W + x] = n == 0 ? 0 : ((r / n) << 16) + ((gr / n) << 8) + b / n;
			}
		}
		for (int pass = 0; pass < BLUR_PASSES; pass++) {
			boxBlur(small, BLUR_W, BLUR_H);
		}
		PixMap out = new PixMap(BLUR_H, this.getBaseComponent(), BLUR_W);
		for (int i = 0; i < small.length; i++) {
			int p = small[i];
			int r = ((p >> 16 & 0xFF) * BLUR_DIM) >> 8;
			int gr = ((p >> 8 & 0xFF) * BLUR_DIM) >> 8;
			int b = ((p & 0xFF) * BLUR_DIM) >> 8;
			out.data[i] = (r << 16) + (gr << 8) + b;
		}
		out.setPixels();
		return out;
	}

	/** One 3x3 box pass over the small copy, in place. Edges average the neighbours they have. */
	private static void boxBlur(int[] px, int w, int h) {
		int[] tmp = new int[px.length];
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int r = 0;
				int g = 0;
				int b = 0;
				int n = 0;
				for (int dy = -1; dy <= 1; dy++) {
					int sy = y + dy;
					if (sy < 0 || sy >= h) {
						continue;
					}
					for (int dx = -1; dx <= 1; dx++) {
						int sx = x + dx;
						if (sx < 0 || sx >= w) {
							continue;
						}
						int p = px[sy * w + sx];
						r += p >> 16 & 0xFF;
						g += p >> 8 & 0xFF;
						b += p & 0xFF;
						n++;
					}
				}
				tmp[y * w + x] = ((r / n) << 16) + ((g / n) << 8) + b / n;
			}
		}
		System.arraycopy(tmp, 0, px, 0, px.length);
	}

	/** PixMap.target: see the top of this section. */
	public boolean draw(PixMap area, int x, int y, java.awt.Graphics g) {
		if (!this.layout.resizable || this.letterGraphics == null) {
			return false;
		}
		if (!this.ingame || area == super.drawArea) {
			area.drawDirect(y, x, this.letterGraphics);
			return true;
		}
		if (this.areaViewport == null) {
			return false;
		}
		if (area == this.areaViewport) {
			this.presentGame(false);
			return true;
		}
		copyRect(area.data, area.width, 0, 0, area.width, area.height, this.frameBuffer, Layout.FIXED_W, Layout.FIXED_H, x, y);
		return true;
	}

	/**
	 * The panels over the viewport, an open viewport menu over them, and the lot onto the screen.
	 *
	 * In the modern layout a panel is mixed with the scene behind it rather than laid on top of it
	 * (Layout.panelAlpha). Old School does that with panel art drawn to be see-through; the art in
	 * the 377 cache is the fixed screen's, which is solid, so what happens here is the whole panel
	 * faded - its text and its icons along with its background. That is why the chatbox and the
	 * inventory only give up a quarter of themselves and the tab rows almost nothing: any more and
	 * the things a player reads and aims at start to swim in the scene behind them. Faithful
	 * transparency needs Old School's own panel sprites in the cache, which is a content change, not
	 * a client one.
	 */
	private void presentGame(boolean menu) {
		if (this.areaViewport == null || this.frameBuffer == null) {
			return;
		}
		int[] dst = this.areaViewport.data;
		int dw = this.areaViewport.width;
		int dh = this.areaViewport.height;
		for (int p = 0; p < this.layout.panels; p++) {
			int alpha = this.layout.panelAlpha(p);
			int dx = this.layout.panelScreenX(p) - this.layout.vpScreenX;
			int dy = this.layout.panelScreenY(p) - this.layout.vpScreenY;
			if (alpha >= Layout.OPAQUE) {
				copyRect(this.frameBuffer, Layout.FIXED_W, this.layout.panelFixedX(p), this.layout.panelFixedY(p), this.layout.panelWidth(p), this.layout.panelHeight(p), dst, dw, dh, dx, dy);
			} else {
				blendRect(this.frameBuffer, Layout.FIXED_W, this.layout.panelFixedX(p), this.layout.panelFixedY(p), this.layout.panelWidth(p), this.layout.panelHeight(p), dst, dw, dh, dx, dy, alpha);
			}
		}
		if (menu && this.menuVisible && this.menuArea == 0) {
			this.areaViewport.bind();
			Pix3D.lineOffset = this.areaViewportOffset;
			this.drawMenu();
		}
		if (this.screenGraphics != null) {
			this.areaViewport.drawDirect(this.layout.vpScreenY, this.layout.vpScreenX, this.screenGraphics);
		}
	}

	/**
	 * The same block, mixed with what is already there: alpha parts of the source to 256 - alpha of
	 * the destination, per channel, the way Pix2D's own translucent fills do it. Used for the modern
	 * layout's panels, so the scene shows through them.
	 */
	private static void blendRect(int[] src, int sw, int sx, int sy, int w, int h, int[] dst, int dw, int dh, int dx, int dy, int alpha) {
		if (dx < 0) {
			sx -= dx;
			w += dx;
			dx = 0;
		}
		if (dy < 0) {
			sy -= dy;
			h += dy;
			dy = 0;
		}
		if (dx + w > dw) {
			w = dw - dx;
		}
		if (dy + h > dh) {
			h = dh - dy;
		}
		if (w <= 0 || h <= 0) {
			return;
		}
		int rest = 256 - alpha;
		for (int row = 0; row < h; row++) {
			int from = (sy + row) * sw + sx;
			int to = (dy + row) * dw + dx;
			for (int col = 0; col < w; col++) {
				int a = src[from + col];
				int b = dst[to + col];
				// red and blue in one multiply, green in another. The red-and-blue sum reaches
				// 0xFF00FF00, which is a negative int, so it is shifted back with >>> and not >>.
				int rb = ((a & 0xFF00FF) * alpha + (b & 0xFF00FF) * rest) >>> 8 & 0xFF00FF;
				int g = ((a & 0xFF00) * alpha + (b & 0xFF00) * rest) >>> 8 & 0xFF00;
				dst[to + col] = rb | g;
			}
		}
	}

	/** A w x h block of src (stride sw) at (sx, sy) to dst (dw x dh) at (dx, dy), clipped to dst. */
	private static void copyRect(int[] src, int sw, int sx, int sy, int w, int h, int[] dst, int dw, int dh, int dx, int dy) {
		if (dx < 0) {
			sx -= dx;
			w += dx;
			dx = 0;
		}
		if (dy < 0) {
			sy -= dy;
			h += dy;
			dy = 0;
		}
		if (dx + w > dw) {
			w = dw - dx;
		}
		if (dy + h > dh) {
			h = dh - dy;
		}
		if (w <= 0 || h <= 0) {
			return;
		}
		for (int row = 0; row < h; row++) {
			System.arraycopy(src, (sy + row) * sw + sx, dst, (dy + row) * dw + dx, w);
		}
	}

	/**
	 * A walkable overlay (the wilderness level, a minigame's score) is laid out on a 512x334 root
	 * against the fixed viewport's corners. Resizable keeps each piece at its own corner of the open
	 * area: a child whose middle is right of the root's middle moves right with the window's width,
	 * one below the middle moves down with its height. A rectangle that covers the whole root - the
	 * darkness of a cave without a light source - covers the whole window instead. Fixed mode draws
	 * the overlay as it always was.
	 */
	private void drawViewportOverlay(Component root) {
		if (!this.layout.resizable || root.children == null || root.childX == null || root.childY == null) {
			this.drawInterface(0, 0, root, 0);
			return;
		}
		int dx = this.layout.openW - Layout.VIEWPORT_W;
		int dy = this.layout.openH - Layout.VIEWPORT_H;
		int n = root.children.length;
		int[] ox = root.childX.clone();
		int[] oy = root.childY.clone();
		int ow = root.width;
		int oh = root.height;
		Component[] filled = new Component[n];
		int[] fw = new int[n];
		int[] fh = new int[n];
		try {
			for (int i = 0; i < n; i++) {
				Component child = Component.get(root.children[i]);
				if (child == null) {
					continue;
				}
				if (child.type == 3 && ox[i] <= 0 && oy[i] <= 0 && ox[i] + child.width >= ow && oy[i] + child.height >= oh) {
					filled[i] = child;
					fw[i] = child.width;
					fh[i] = child.height;
					root.childX[i] = 0;
					root.childY[i] = 0;
					child.width = this.layout.vpW;
					child.height = this.layout.vpH;
					continue;
				}
				if (ox[i] + child.width / 2 > ow / 2) {
					root.childX[i] = ox[i] + dx;
				}
				if (oy[i] + child.height / 2 > oh / 2) {
					root.childY[i] = oy[i] + dy;
				}
			}
			root.width = this.layout.vpW;
			root.height = this.layout.vpH;
			this.drawInterface(0, 0, root, 0);
		} finally {
			System.arraycopy(ox, 0, root.childX, 0, n);
			System.arraycopy(oy, 0, root.childY, 0, n);
			root.width = ow;
			root.height = oh;
			for (int i = 0; i < n; i++) {
				if (filled[i] != null) {
					filled[i].width = fw[i];
					filled[i].height = fh[i];
				}
			}
		}
	}
}
