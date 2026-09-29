package jagex2.config;

import deob.ObfuscatedName;
import jagex2.client.Client;
import jagex2.dash3d.AnimFrame;
import jagex2.dash3d.Model;
import jagex2.datastruct.LruCache;
import jagex2.graphics.Pix32;
import jagex2.graphics.PixFont;
import jagex2.io.Jagfile;
import jagex2.io.Packet;
import jagex2.jstring.JString;

public class Component {

	@ObfuscatedName("EWIXBTLV.K")
	public static int field728 = -1;

	@ObfuscatedName("EWIXBTLV.T")
	public static int field737 = -1;

	@ObfuscatedName("EWIXBTLV.cb")
	public static LruCache modelCache = new LruCache(30);

	@ObfuscatedName("EWIXBTLV.pb")
	public static int field759 = -1;

	@ObfuscatedName("EWIXBTLV.k")
	public byte trans;

	@ObfuscatedName("EWIXBTLV.f")
	public int id;

	@ObfuscatedName("EWIXBTLV.i")
	public int field700;

	@ObfuscatedName("EWIXBTLV.m")
	public int targetMask;

	@ObfuscatedName("EWIXBTLV.p")
	public int field707;

	@ObfuscatedName("EWIXBTLV.q")
	public int activeOverColour;

	@ObfuscatedName("EWIXBTLV.r")
	public int field709;

	@ObfuscatedName("EWIXBTLV.s")
	public int field710;

	@ObfuscatedName("EWIXBTLV.v")
	public int field713;

	@ObfuscatedName("EWIXBTLV.z")
	public int field717;

	@ObfuscatedName("EWIXBTLV.A")
	public int type;

	@ObfuscatedName("EWIXBTLV.C")
	public int height;

	@ObfuscatedName("EWIXBTLV.E")
	public int colour;

	@ObfuscatedName("EWIXBTLV.F")
	public int width;

	@ObfuscatedName("EWIXBTLV.G")
	public int clientCode;

	// Bank tabs: an inv component can be told to show only part of its inventory.
	// invWindowCount < 0 means "the whole thing", which is the state every component
	// starts in and every component except the bank grid stays in, so nothing else
	// changes behaviour. Set by the IF_SETINVWINDOW packet; see Client.java.
	public int invWindowFirst;
	public int invWindowCount = -1;

	// Slot numbers at which the grid should start a fresh row. Bank tabs use this so the
	// "all items" view breaks between tabs the way OSRS does instead of running them together.
	// Zeros mean no break, and a break at slot 0 is a no-op.
	public int[] invBreaks;

	// cell -> inventory slot, or -1 for a cell that draws nothing. Rebuilt whenever the window
	// or the breaks change, and read by BOTH the draw loop and the hit test, so the two can never
	// disagree about which item is where. null means the identity mapping, which is every
	// component except the bank grid.
	public int[] invCellSlot;

	// true for the first cell of each tab past the first, so the grid can rule a line above it.
	// Derived here rather than in the draw loop because the draw loop sees cells, not breaks.
	public boolean[] invCellBreak;

	// cell -> the slot a DROP on that cell should target, or -1. Differs from invCellSlot only on
	// the blank cells that pad a tab out to the end of its row: those draw nothing, but dropping
	// on them plainly means "put it in this tab", so they aim at that tab's last item. Without
	// this, a drop on the empty space after a tab hit no slot, the client sent no packet at all,
	// and the drag looked like it had simply been ignored.
	public int[] invCellDrop;

	// Bank search: when set (already lower-cased), the cell map shows only the slots whose obj name
	// contains it, in slot order, across every tab - the window and the breaks are ignored while a
	// search is active. Cells still map to REAL slots, so withdrawing from a search result works
	// through the same packets as everything else.
	public String invSearch;

	public void rebuildCellMap() {
		if (this.type != 2 && this.type != 7) {
			return;
		}
		int cells = this.width * this.height;
		if (this.invSearch != null && this.invSearch.length() > 0 && this.invSlotObjId != null) {
			int[] map = new int[cells];
			boolean[] rule = new boolean[cells];
			int[] drop = new int[cells];
			for (int i = 0; i < cells; i++) {
				map[i] = -1;
				drop[i] = -1;
			}
			int cell = 0;
			for (int slot = 0; slot < this.invSlotObjId.length && cell < cells; slot++) {
				int id = this.invSlotObjId[slot];
				if (id <= 0) {
					continue;
				}
				ObjType obj = ObjType.get(id - 1);
				if (obj == null || obj.field811 == null || obj.field811.toLowerCase().indexOf(this.invSearch) == -1) {
					continue;
				}
				map[cell] = slot;
				drop[cell] = slot;
				cell++;
			}
			this.invCellSlot = map;
			this.invCellBreak = rule;
			this.invCellDrop = drop;
			return;
		}
		boolean windowed = this.invWindowCount >= 0;
		boolean broken = false;
		if (this.invBreaks != null) {
			for (int i = 0; i < this.invBreaks.length; i++) {
				if (this.invBreaks[i] > 0) {
					broken = true;
				}
			}
		}
		if (!windowed && !broken) {
			this.invCellSlot = null;
			this.invCellBreak = null;
			this.invCellDrop = null;
			return;
		}
		int[] map = new int[cells];
		boolean[] rule = new boolean[cells];
		int[] drop = new int[cells];
		for (int i = 0; i < cells; i++) {
			drop[i] = -1;
		}
		for (int i = 0; i < cells; i++) {
			map[i] = -1;
		}
		int first = windowed ? this.invWindowFirst : 0;
		int count = windowed ? this.invWindowCount : cells;
		int cell = 0;
		for (int slot = first; slot < first + count && cell < cells; slot++) {
			if (broken) {
				for (int i = 0; i < this.invBreaks.length; i++) {
					// several empty tabs can share a boundary; the column test makes the extra
					// ones no-ops rather than inserting a blank row each
					if (this.invBreaks[i] == slot && slot > first) {
						if (cell % this.width != 0) {
							// the cells we are stepping over are this block's padding: aim them at its last item
							int pad = cell + this.width - cell % this.width;
							while (cell < pad && cell < cells) {
								drop[cell] = slot - 1;
								cell++;
							}
						}
						if (cell < cells) {
							rule[cell] = true;
						}
					}
				}
			}
			if (cell >= cells) {
				break;
			}
			drop[cell] = slot;
			map[cell++] = slot;
		}
		this.invCellSlot = map;
		this.invCellBreak = rule;
		this.invCellDrop = drop;
	}

	@ObfuscatedName("EWIXBTLV.H")
	public static int contrast;

	@ObfuscatedName("EWIXBTLV.I")
	public int marginY;

	@ObfuscatedName("EWIXBTLV.M")
	public int layer;

	@ObfuscatedName("EWIXBTLV.P")
	public int zoom;

	@ObfuscatedName("EWIXBTLV.Q")
	public int xan;

	@ObfuscatedName("EWIXBTLV.R")
	public int yan;

	@ObfuscatedName("EWIXBTLV.S")
	public int overlayer;

	@ObfuscatedName("EWIXBTLV.X")
	public int field741;

	@ObfuscatedName("EWIXBTLV.Y")
	public int activeColour;

	@ObfuscatedName("EWIXBTLV.Z")
	public int overColour;

	@ObfuscatedName("EWIXBTLV.bb")
	public int marginX;

	@ObfuscatedName("EWIXBTLV.eb")
	public int activeModelType;

	@ObfuscatedName("EWIXBTLV.fb")
	public int activeModel;

	@ObfuscatedName("EWIXBTLV.sb")
	public static int ambient;

	@ObfuscatedName("EWIXBTLV.vb")
	public int modelType;

	@ObfuscatedName("EWIXBTLV.wb")
	public int model;

	@ObfuscatedName("EWIXBTLV.xb")
	public int scroll;

	@ObfuscatedName("EWIXBTLV.yb")
	public int anim;

	@ObfuscatedName("EWIXBTLV.zb")
	public int activeAnim;

	@ObfuscatedName("EWIXBTLV.Bb")
	public int buttonType;

	@ObfuscatedName("EWIXBTLV.e")
	public static Jagfile media;

	@ObfuscatedName("EWIXBTLV.c")
	public Pix32 graphic;

	@ObfuscatedName("EWIXBTLV.J")
	public Pix32 activeGraphic;

	@ObfuscatedName("EWIXBTLV.B")
	public PixFont font;

	@ObfuscatedName("EWIXBTLV.O")
	public static LruCache imageCache;

	@ObfuscatedName("EWIXBTLV.b")
	public String targetText;

	@ObfuscatedName("EWIXBTLV.u")
	public String text;

	@ObfuscatedName("EWIXBTLV.N")
	public String activeText;

	@ObfuscatedName("EWIXBTLV.gb")
	public String option;

	@ObfuscatedName("EWIXBTLV.tb")
	public String targetVerb;

	@ObfuscatedName("EWIXBTLV.h")
	public boolean swappable;

	@ObfuscatedName("EWIXBTLV.j")
	public boolean hide;

	@ObfuscatedName("EWIXBTLV.t")
	public boolean interactable;

	@ObfuscatedName("EWIXBTLV.x")
	public boolean field715;

	@ObfuscatedName("EWIXBTLV.D")
	public boolean fill;

	@ObfuscatedName("EWIXBTLV.L")
	public boolean shadowed;

	@ObfuscatedName("EWIXBTLV.kb")
	public boolean center;

	@ObfuscatedName("EWIXBTLV.mb")
	public boolean draggable;

	@ObfuscatedName("EWIXBTLV.Ab")
	public boolean usable;

	@ObfuscatedName("EWIXBTLV.d")
	public int[] invSlotOffsetY;

	@ObfuscatedName("EWIXBTLV.l")
	public int[] invSlotOffsetX;

	@ObfuscatedName("EWIXBTLV.o")
	public int[] invSlotObjCount;

	@ObfuscatedName("EWIXBTLV.w")
	public int[] childX;

	@ObfuscatedName("EWIXBTLV.U")
	public int[] scriptOperand;

	@ObfuscatedName("EWIXBTLV.W")
	public int[] children;

	@ObfuscatedName("EWIXBTLV.hb")
	public int[] invSlotObjId;

	@ObfuscatedName("EWIXBTLV.lb")
	public int[] scriptComparator;

	@ObfuscatedName("EWIXBTLV.ob")
	public int[] childY;

	@ObfuscatedName("EWIXBTLV.db")
	public Pix32[] invSlotGraphic;

	@ObfuscatedName("EWIXBTLV.g")
	public static Component[] types;

	@ObfuscatedName("EWIXBTLV.n")
	public static PixFont[] fonts;

	@ObfuscatedName("EWIXBTLV.ab")
	public String[] iop;

	@ObfuscatedName("EWIXBTLV.ub")
	public static byte[][] data;

	@ObfuscatedName("EWIXBTLV.y")
	public int[][] scripts;

	@ObfuscatedName("EWIXBTLV.a(ILjava/lang/String;I)LEPQDEJTO;")
	public static Pix32 getImage(int index, String name) {
		long cacheKey = (JString.hashCode(name) << 8) + (long) index;
		Pix32 cachedImage = (Pix32) imageCache.get(cacheKey);
		if (cachedImage != null) {
			return cachedImage;
		}

		if (media == null) {
			return null;
		}

		try {
			Pix32 image = new Pix32(media, name, index);
			imageCache.put(image, cacheKey);
			return image;
		} catch (Exception var7) {
			return null;
		}
	}

	@ObfuscatedName("EWIXBTLV.a(I)LEWIXBTLV;")
	public static Component get(int id) {
		if (types[id] != null) {
			return types[id];
		}

		Packet buf = new Packet(data[id]);
		int layer = buf.g2();
		types[id] = decode(layer, buf, id);
		return types[id];
	}

	@ObfuscatedName("EWIXBTLV.a(III)V")
	public void swapObj(int arg0, int arg2) {
		int var4 = this.invSlotObjId[arg2];
		this.invSlotObjId[arg2] = this.invSlotObjId[arg0];
		this.invSlotObjId[arg0] = var4;

		int var5 = this.invSlotObjCount[arg2];
		this.invSlotObjCount[arg2] = this.invSlotObjCount[arg0];
		this.invSlotObjCount[arg0] = var5;
	}

	@ObfuscatedName("EWIXBTLV.a(II)LLZYQDKJV;")
	public Model loadModel(int type, int id) {
		ObjType obj = null;
		if (type == 4) {
			obj = ObjType.get(id);
			ambient += obj.ambient;
			contrast += obj.contrast;
		}

		if (type == 6) {
			// The local player's whole body, dressed, for 474's Equipment Stats doll. Deliberately
			// NOT kept in modelCache: that cache is keyed on (type << 16) + id, and the id the doll
			// passes (Client.updateInterfaceContent, client code 328) is ClientPlayer.field1676
			// XOR-folded down to 15 bits. Every worn item sits in the top half of that hash and is
			// masked straight back out again, so a bare player, a player holding a staff and a
			// player holding a whip all come out as the same id - and the first body built in a
			// session was handed back for every change of clothes after it, weapon and all. The
			// body is cached in ClientPlayer instead, on the whole appearance.
			return Client.localPlayer.getBodyModel();
		}

		Model model = (Model) modelCache.get((long) ((type << 16) + id));
		if (model != null) {
			return model;
		}

		if (type == 1) {
			model = Model.tryGet(id);
		} else if (type == 2) {
			model = NpcType.get(id).getHeadModel();
		} else if (type == 3) {
			model = Client.localPlayer.getHeadModel();
		} else if (type == 4) {
			model = obj.getInvModel(50);
		} else if (type == 5) {
			model = null;
		}

		if (model != null) {
			modelCache.put(model, (long) ((type << 16) + id));
		}

		return model;
	}

	@ObfuscatedName("EWIXBTLV.a(ILMFMVIYHT;II)LEWIXBTLV;")
	public static Component decode(int layer, Packet buf, int id) {
		Component com = new Component();
		com.id = id;
		com.layer = layer;

		com.type = buf.g1();
		com.buttonType = buf.g1();
		com.clientCode = buf.g2();
		com.width = buf.g2();
		com.height = buf.g2();
		com.trans = (byte) buf.g1();

		com.overlayer = buf.g1();
		if (com.overlayer == 0) {
			com.overlayer = -1;
		} else {
			com.overlayer = (com.overlayer - 1 << 8) + buf.g1();
		}

		if (com.clientCode == 600) {
			field728 = layer;
		} else if (com.clientCode == 650) {
			field737 = layer;
		} else if (com.clientCode == 655) {
			field759 = layer;
		}

		int comparatorCount = buf.g1();
		if (comparatorCount > 0) {
			com.scriptComparator = new int[comparatorCount];
			com.scriptOperand = new int[comparatorCount];

			for (int i = 0; i < comparatorCount; i++) {
				com.scriptComparator[i] = buf.g1();
				com.scriptOperand[i] = buf.g2();
			}
		}

		int scriptCount = buf.g1();
		if (scriptCount > 0) {
			com.scripts = new int[scriptCount][];

			for (int i = 0; i < scriptCount; i++) {
				int opcodeCount = buf.g2();

				com.scripts[i] = new int[opcodeCount];
				for (int j = 0; j < opcodeCount; j++) {
					com.scripts[i][j] = buf.g2();
				}
			}
		}

		if (com.type == 0) {
			com.scroll = buf.g2();
			com.hide = buf.g1() == 1;

			int childCount = buf.g2();
			com.children = new int[childCount];
			com.childX = new int[childCount];
			com.childY = new int[childCount];

			for (int i = 0; i < childCount; i++) {
				com.children[i] = buf.g2();
				com.childX[i] = buf.g2b();
				com.childY[i] = buf.g2b();
			}
		}

		if (com.type == 1) {
			com.field707 = buf.g2();
			com.field715 = buf.g1() == 1;
		}

		if (com.type == 2) {
			com.invSlotObjId = new int[com.width * com.height];
			com.invSlotObjCount = new int[com.width * com.height];

			com.draggable = buf.g1() == 1;
			com.interactable = buf.g1() == 1;
			com.usable = buf.g1() == 1;
			com.swappable = buf.g1() == 1;
			com.marginX = buf.g1();
			com.marginY = buf.g1();

			com.invSlotOffsetX = new int[20];
			com.invSlotOffsetY = new int[20];
			com.invSlotGraphic = new Pix32[20];

			for (int i = 0; i < 20; i++) {
				int hasGraphic = buf.g1();
				if (hasGraphic == 1) {
					com.invSlotOffsetX[i] = buf.g2b();
					com.invSlotOffsetY[i] = buf.g2b();

					String graphic = buf.gjstr();
					if (graphic.length() > 0) {
						int spriteIndex = graphic.lastIndexOf(",");
						com.invSlotGraphic[i] = getImage(Integer.parseInt(graphic.substring(spriteIndex + 1)), graphic.substring(0, spriteIndex));
					}
				}
			}

			com.iop = new String[5];
			for (int i = 0; i < 5; i++) {
				com.iop[i] = buf.gjstr();

				if (com.iop[i].length() == 0) {
					com.iop[i] = null;
				}
			}
		}

		if (com.type == 3) {
			com.fill = buf.g1() == 1;
		}

		if (com.type == 4 || com.type == 1) {
			com.center = buf.g1() == 1;
			int font = buf.g1();
			if (fonts != null) {
				com.font = fonts[font];
			}
			com.shadowed = buf.g1() == 1;
		}

		if (com.type == 4) {
			com.text = buf.gjstr();
			com.activeText = buf.gjstr();
		}

		if (com.type == 1 || com.type == 3 || com.type == 4) {
			com.colour = buf.g4();
		}

		if (com.type == 3 || com.type == 4) {
			com.activeColour = buf.g4();
			com.overColour = buf.g4();
			com.activeOverColour = buf.g4();
		}

		if (com.type == 5) {
			String graphic = buf.gjstr();
			if (graphic.length() > 0) {
				int spriteIndex = graphic.lastIndexOf(",");
				com.graphic = getImage(Integer.parseInt(graphic.substring(spriteIndex + 1)), graphic.substring(0, spriteIndex));
			}

			String activeGraphic = buf.gjstr();
			if (activeGraphic.length() > 0) {
				int spriteIndex = activeGraphic.lastIndexOf(",");
				com.activeGraphic = getImage(Integer.parseInt(activeGraphic.substring(spriteIndex + 1)), activeGraphic.substring(0, spriteIndex));
			}
		}

		if (com.type == 6) {
			int model = buf.g1();
			if (model != 0) {
				com.modelType = 1;
				com.model = (model - 1 << 8) + buf.g1();
			}

			int activeModel = buf.g1();
			if (activeModel != 0) {
				com.activeModelType = 1;
				com.activeModel = (activeModel - 1 << 8) + buf.g1();
			}

			int anim = buf.g1();
			if (anim == 0) {
				com.anim = -1;
			} else {
				com.anim = (anim - 1 << 8) + buf.g1();
			}

			int activeAnim = buf.g1();
			if (activeAnim == 0) {
				com.activeAnim = -1;
			} else {
				com.activeAnim = (activeAnim - 1 << 8) + buf.g1();
			}

			com.zoom = buf.g2();
			com.xan = buf.g2();
			com.yan = buf.g2();
		}

		if (com.type == 7) {
			com.invSlotObjId = new int[com.width * com.height];
			com.invSlotObjCount = new int[com.width * com.height];

			com.center = buf.g1() == 1;
			int font = buf.g1();
			if (fonts != null) {
				com.font = fonts[font];
			}
			com.shadowed = buf.g1() == 1;
			com.colour = buf.g4();
			com.marginX = buf.g2b();
			com.marginY = buf.g2b();
			com.interactable = buf.g1() == 1;

			com.iop = new String[5];
			for (int i = 0; i < 5; i++) {
				com.iop[i] = buf.gjstr();

				if (com.iop[i].length() == 0) {
					com.iop[i] = null;
				}
			}
		}

		if (com.type == 8) {
			com.text = buf.gjstr();
		}

		if (com.buttonType == 2 || com.type == 2) {
			com.targetVerb = buf.gjstr();
			com.targetText = buf.gjstr();
			com.targetMask = buf.g2();
		}

		if (com.buttonType == 1 || com.buttonType == 4 || com.buttonType == 5 || com.buttonType == 6) {
			com.option = buf.gjstr();

			if (com.option.length() == 0) {
				if (com.buttonType == 1) {
					com.option = "Ok";
				}
				if (com.buttonType == 4) {
					com.option = "Select";
				}
				if (com.buttonType == 5) {
					com.option = "Select";
				}
				if (com.buttonType == 6) {
					com.option = "Continue";
				}
			}
		}

		return com;
	}

	@ObfuscatedName("EWIXBTLV.a(I[LJDPYRDAS;LATJMVOZR;LATJMVOZR;)V")
	public static void unpack(PixFont[] fonts, Jagfile interfaces, Jagfile media) {
		imageCache = new LruCache(50000);

		Component.media = media;
		Component.fonts = fonts;

		int layer = -1;
		Packet buf = new Packet(interfaces.read("data", null));
		int total = buf.g2();
		types = new Component[total];
		data = new byte[total][];

		while (buf.pos < buf.data.length) {
			int id = buf.g2();
			if (id == 65535) {
				layer = buf.g2();
				id = buf.g2();
			}

			int start = buf.pos;
			Component com = decode(layer, buf, id);
			byte[] copy = data[com.id] = new byte[buf.pos - start + 2];
			for (int i = start; i < buf.pos; i++) {
				copy[i - start + 2] = buf.data[i];
			}
			copy[0] = (byte) (layer >> 8);
			copy[1] = (byte) layer;
		}

		Component.media = null;
	}

	@ObfuscatedName("EWIXBTLV.a(ZI)V")
	public static void unloadCom(int arg1) {
		if (arg1 == -1) {
			return;
		}

		for (int var2 = 0; var2 < types.length; var2++) {
			if (types[var2] != null && types[var2].layer == arg1 && types[var2].type != 2) {
				types[var2] = null;
			}
		}
	}

	@ObfuscatedName("EWIXBTLV.a(ILLZYQDKJV;II)V")
	public static void cacheModel(int type, Model model, int id) {
		modelCache.clear();

		if (model != null && type != 4) {
			modelCache.put(model, (long) ((type << 16) + id));
		}
	}

	@ObfuscatedName("EWIXBTLV.a(Z)V")
	public static void unload() {
		types = null;
		media = null;
		imageCache = null;
		fonts = null;
		data = null;
	}

	@ObfuscatedName("EWIXBTLV.a(IIIZ)LLZYQDKJV;")
	public Model getModel(int primaryTransformId, int secondaryTransformId, boolean arg3) {
		ambient = 64;
		contrast = 768;

		Model model;
		if (arg3) {
			model = this.loadModel(this.activeModelType, this.activeModel);
		} else {
			model = this.loadModel(this.modelType, this.model);
		}

		if (model == null) {
			return null;
		} else if (primaryTransformId == -1 && secondaryTransformId == -1 && model.faceColour == null) {
			return model;
		}

		model = new Model(false, false, true, model, AnimFrame.isNull(primaryTransformId) & AnimFrame.isNull(secondaryTransformId));

		if (primaryTransformId != -1 || secondaryTransformId != -1) {
			model.createLabelReferences();
		}

		if (primaryTransformId != -1) {
			model.applyTransform(primaryTransformId);
		}

		if (secondaryTransformId != -1) {
			model.applyTransform(secondaryTransformId);
		}

		model.calculateNormals(ambient, contrast, -50, -10, -50, true);
		return model;
	}
}
