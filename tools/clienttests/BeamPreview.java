// Renders one loot beam to a PNG so its look can be compared against Jagex's sprite directly.
//
// WHY THIS EXISTS. The beam shipped wrong twice, both times because it was judged from a
// description rather than from a picture: once from a measurement of the wrong thing, once from
// arithmetic that was right in isolation and produced a dotted hairline on screen. A render is
// the only thing that answers "does it look like the reference", and a render that costs one
// command is one that gets looked at.
//
// Driven by tools/clienttests/run_beampreview.py.
package jagex2.client.plugin.builtin;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import jagex2.client.Client;
import jagex2.client.GroundItemPrefs;
import jagex2.client.plugin.Overlay;
import jagex2.client.plugin.PluginConfig;
import jagex2.client.plugin.PluginManager;
import jagex2.dash3d.ClientPlayer;
import jagex2.graphics.Pix2D;
import jagex2.graphics.Pix3D;
import jagex2.graphics.PixFont;
import jagex2.datastruct.LinkList;
import jagex2.config.ObjType;
import jagex2.dash3d.ClientObj;

public final class BeamPreview {

	static final int W = 420;
	static final int H = 780;
	static final int MID_X = 64;
	static final int MID_Z = 64;
	static final int BASE_ID = 900;

	static Client client;
	static PluginManager manager;
	static int[] pixels;
	static PluginManager.Entry entry;

	public static void main(String[] args) throws Exception {
		String out = args.length > 0 ? args[0] : "build/preview/beam.png";
		setUp();

		pixels = new int[W * H];
		// A MID GREY, not black: the beam is translucent, and over an empty buffer every alpha
		// reads darker than it does over the game's floor. Judging the look against black is
		// how a beam that is too faint passes for one that is too dark.
		java.util.Arrays.fill(pixels, 0x6E7355);
		Pix2D.bind(W, H, pixels);
		manager.renderOverlays(W, H, Overlay.LAYER_SCENE);

		BufferedImage image = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < H; y++) {
			for (int x = 0; x < W; x++) {
				int p = pixels[y * W + x];
				image.setRGB(x, y, 0xFF000000 | p);
			}
		}
		File file = new File(out);
		if (file.getParentFile() != null) {
			file.getParentFile().mkdirs();
		}
		javax.imageio.ImageIO.write(image, "png", file);

		// The silhouette, as numbers, so a change can be read without opening the image.
		int top = -1;
		int bottom = -1;
		int widest = 0;
		int painted = 0;
		for (int y = 0; y < H; y++) {
			int n = 0;
			for (int x = 0; x < W; x++) {
				if (pixels[y * W + x] != 0x6E7355) {
					n++;
				}
			}
			if (n > 0) {
				if (top < 0) {
					top = y;
				}
				bottom = y;
				painted += n;
			}
			if (n > widest) {
				widest = n;
			}
		}
		System.out.println("beam top=" + top + " bottom=" + bottom + " height="
			+ (bottom - top + 1) + " widest=" + widest + " painted=" + painted);
		System.out.println("wrote " + file.getAbsolutePath());
		System.exit(0);
	}

	static void setUp() {
		client = new Client();
		client.levelHeightmap = new int[4][105][105];
		client.levelTileFlags = new byte[4][104][104];
		client.currentLevel = 0;
		client.ingame = true;

		// A GAME-LIKE CAMERA: above and behind, looking down, the way a player's is. The test
		// harness uses a level camera because it makes the projection arithmetic predictable;
		// that is exactly the view in which a beam looks fine and a ground pool is one pixel
		// tall, so it is the wrong camera to judge this from.
		// Placed ON THE VIEW BEARING rather than due south of the tile: with a yaw set and the
		// camera left where yaw 0 put it, it is simply pointing somewhere else and the frame
		// comes back empty. A yaw is wanted because at yaw 0 a tile projects to an
		// axis-aligned trapezoid, which makes the pool of light look rectangular when it is
		// not - the game's camera is almost never square on.
		int yaw = 300;
		int away = 760;
		double bearing = yaw * 2.0 * Math.PI / 2048.0;
		client.cameraPitch = 320;
		client.cameraYaw = yaw;
		client.cameraX = MID_X * 128 + 64 + (int) (away * Math.sin(bearing));
		client.cameraZ = MID_Z * 128 + 64 - (int) (away * Math.cos(bearing));
		client.cameraY = -560;

		Client.localPlayer = new ClientPlayer();
		Client.localPlayer.field1157 = MID_X * 128;
		Client.localPlayer.field1158 = MID_Z * 128;

		Pix3D.zoom = 420;
		Pix3D.centerX = W / 2;
		Pix3D.centerY = H * 3 / 4;

		ObjType.field818 = new ObjType[4];
		for (int i = 0; i < 4; i++) {
			ObjType type = new ObjType();
			type.field845 = BASE_ID + i;
			type.field811 = "Thing " + i;
			type.field827 = 1;
			type.field853 = true;
			ObjType.field818[i] = type;
		}

		PixFont font = new SilentFont();
		manager = new PluginManager(client, font, font, font);
		manager.reload();

		List<PluginManager.Entry> entries = manager.getPlugins();
		for (int i = 0; i < entries.size(); i++) {
			PluginManager.Entry e = entries.get(i);
			if ("ground-items".equals(e.key)) {
				entry = e;
				manager.setEnabled(e, true);
			} else if (e.isEnabled()) {
				manager.setEnabled(e, false);
			}
		}

		client.objStacks = new LinkList[4][104][104];
		LinkList stack = new LinkList();
		ClientObj obj = new ClientObj();
		obj.field873 = BASE_ID;
		obj.field875 = 1;
		stack.push(obj);
		client.objStacks[0][MID_X][MID_Z] = stack;

		GroundItemPrefs.clear();
		GroundItemPrefs.set("Thing 0", GroundItemPrefs.HIGHLIGHT);
		set("beamHighlighted", "1");
		set("highlightColour", "3EEE95");       // the wiki's green, to compare like with like
		set("beamPulse", "0");
		set("beamFade", "1");
		set("beamCore", "1");
		set("beamGlow", "1");
		set("beamSegments", "24");
		set("beamOpacity", "96");
		set("beamStyle", GroundItemsPlugin.BEAM_LOOT);
		set("showHidden", "0");
		set("distance", "0");                   // no labels in the way of the picture
	}

	static void set(String key, String value) {
		List<PluginConfig.Item> items = entry.getConfig().getItems();
		for (int i = 0; i < items.size(); i++) {
			if (items.get(i).key.equals(key)) {
				entry.getConfig().set(items.get(i), value);
				return;
			}
		}
	}

	/** Draws no glyphs: this picture is about the beam. */
	static final class SilentFont extends PixFont {
		SilentFont() {
			this.height = 12;
			java.util.Arrays.fill(this.charAdvance, 4);
		}

		public int stringWid(String text) {
			return text == null ? 0 : text.length() * 4;
		}

		public int stringWidTag(String text) {
			return this.stringWid(text);
		}

		public void drawStringTag(int colour, int x, int y, boolean shadow, String text) {
		}

		public void drawString(int x, int colour, int y, String text) {
		}
	}
}
