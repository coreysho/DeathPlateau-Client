/*
 * Draws a saved profile's head the way the login screen does, so the numbers can be chosen by
 * looking instead of by reasoning.
 *
 * WHY THIS EXISTS. The portraits on the login screen did not appear, and three attempts to work
 * out why from the source were wrong in three different ways. A head needs models out of the
 * cache, run_titlepreview deliberately has none, and the only machine that could show the fault
 * was the one the game was being played on - so every fix was a guess pushed to a live client.
 * This renders the same model through the same call with a real cache, here, where it can be
 * looked at.
 *
 * It draws the head at a row of eye distances into one picture. The frame is the login screen's
 * own 48px box, so what is in the box here is what would be in the box there.
 *
 *     python3 tools/clienttests/run_facepreview.py [out-dir] [path/to/data/pack] [look]
 *
 * `look` is the eighteen numbers from a profile's look= line. Without one a face is built from
 * the first usable identikit of each body part, which is enough to aim the camera even though it
 * is nobody's character.
 */
package jagex2.client;

import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;

import jagex2.config.IdkType;
import jagex2.config.ObjType;
import jagex2.config.SeqType;
import jagex2.dash3d.ClientPlayer;
import jagex2.dash3d.Model;
import jagex2.io.Jagfile;
import jagex2.graphics.Pix2D;
import jagex2.graphics.Pix3D;

public class FacePreview {

	/** The login screen's own frame, so this is a like-for-like picture. */
	private static final int BOX = 48;
	private static final int PAD = 16;

	/** The eye distances to try. FACE_ZOOM shipped at 260 and showed nothing. */
	private static final int[] ZOOMS = { 700, 850, 1000, 1150, 1300 };

	/** And how far to lift the camera, since a head hangs above its own origin. */
	private static final int[] LIFTS = { -8, -4, 0, 4, 8 };

	public static void main(String[] args) throws Exception {
		File pack = new File(args[0]);
		File out = new File(args[1]);
		if (!out.isDirectory() && !out.mkdirs()) {
			throw new IllegalStateException("cannot make " + out);
		}

		Pix3D.unpackTextures(WorldMapRender.jagfile(pack, "textures"));
		Pix3D.initColourTable(0.8D);
		Pix3D.initPool(20);
		Jagfile config = WorldMapRender.jagfile(pack, "config");
		IdkType.unpack(config);
		ObjType.unpack(config);
		SeqType.unpack(config);
		WorldMapRender.loadModels(pack);

		int[] look = args.length > 2 && args[2].length() > 0 ? parse(args[2]) : invent();
		System.out.println("look: " + text(look));

		ClientPlayer who = ClientPlayer.fromFace(look);
		if (who == null) {
			throw new IllegalStateException("fromFace refused that look");
		}
		Model head = who.getHeadModel();
		System.out.println("getHeadModel: " + (head == null ? "NOTHING" : "built"));
		if (head != null) {
			// THE STEP getHeadModel DOES NOT DO. It merges the parts and recolours them and stops
			// there; the face arrays stay null until something lights the model, and a model with
			// no face arrays rasterises nothing at all. The same numbers the player's own body is
			// lit with in ClientPlayer.getBodyModel.
			head.calculateNormals(64, 850, -30, -50, -30, true);
			System.out.println("  lit");
			System.out.println("  vertices=" + head.field1195 + "  faces=" + (head.field1205 == null ? -1 : head.field1205.length));
		}
		if (head == null) {
			// Worth saying out loud rather than drawing eight empty boxes: with every model in
			// the cache already loaded, a null here is the appearance, not a missing download.
			System.out.println("nothing to draw - the parts in this look are not ones the cache has");
			return;
		}

		int width = PAD + ZOOMS.length * (BOX + PAD);
		int height = PAD + LIFTS.length * (BOX + PAD);
		int[] raster = new int[width * height];
		Pix2D.bind(width, height, raster);
		Pix2D.setClipping(0, 0, height, width);

		// The row table the login screen builds, for this raster.
		int[] offsets = new int[height];
		for (int y = 0; y < height; y++) {
			offsets[y] = width * y;
		}
		Pix3D.lineOffset = offsets;

		for (int cell = 0; cell < ZOOMS.length * LIFTS.length; cell++) {
			int i = cell % ZOOMS.length;
			int row = cell / ZOOMS.length;
			int x = PAD + i * (BOX + PAD);
			int y = PAD + row * (BOX + PAD);
			Pix2D.fillRect(BOX, y, 0x0E0909, BOX, x);
			Pix2D.drawRect(y, BOX, 0x2A1A1B, x, BOX);

			Pix3D.centerX = x + BOX / 2;
			Pix3D.centerY = y + BOX / 2 + LIFTS[row];
			int zoom = ZOOMS[i];
			int sin = Pix3D.sinTable[0] * zoom >> 16;
			int cos = Pix3D.cosTable[0] * zoom >> 16;
			int before = painted(raster);
			head.method380(0, 0, 0, 0, 0, sin, cos);
			System.out.println("  zoom " + zoom + " lift " + LIFTS[row] + "  pixels: " + (painted(raster) - before));
		}

		BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		img.setRGB(0, 0, width, height, raster, 0, width);
		File file = new File(out, "faces.png");
		ImageIO.write(img, "png", file);
		System.out.println("wrote " + file);
	}

	/** How many pixels are not the frame fill, which is how we tell a draw from a no-op. */
	private static int painted(int[] raster) {
		int n = 0;
		for (int i = 0; i < raster.length; i++) {
			if (raster[i] != 0 && raster[i] != 0x0E0909 && raster[i] != 0x2A1A1B) {
				n++;
			}
		}
		return n;
	}

	private static int[] parse(String text) {
		String[] parts = text.trim().split(",");
		if (parts.length != LoginProfiles.LOOK_LEN) {
			throw new IllegalArgumentException("a look is " + LoginProfiles.LOOK_LEN + " numbers, got " + parts.length);
		}
		int[] look = new int[LoginProfiles.LOOK_LEN];
		for (int i = 0; i < look.length; i++) {
			look[i] = Integer.parseInt(parts[i].trim());
		}
		return look;
	}

	/**
	 * A face from the first usable identikit of each body part.
	 *
	 * Nobody's character, and it does not need to be: the camera is being aimed at a head, and
	 * any head that the cache can build is a head of the right size in the right place.
	 */
	private static int[] invent() {
		int[] look = new int[LoginProfiles.LOOK_LEN];
		for (int i = 0; i < IdkType.field1699.length; i++) {
			IdkType idk = IdkType.field1699[i];
			if (idk == null || !idk.method579()) {
				continue;
			}
			int part = idk.field1700;
			if (part < 0 || part >= 12 || look[part] != 0) {
				continue;
			}
			look[part] = 256 + i;
		}
		return look;
	}

	private static String text(int[] look) {
		StringBuilder s = new StringBuilder();
		for (int i = 0; i < look.length; i++) {
			if (i > 0) {
				s.append(',');
			}
			s.append(look[i]);
		}
		return s.toString();
	}
}
