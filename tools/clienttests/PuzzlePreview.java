// Renders the puzzle box solver's overlay onto a puzzle window, into a PNG.
//
// WHAT IS REAL IN THIS PICTURE AND WHAT IS NOT. The board, the solve, the numbering, the
// geometry and the drawing are all the shipped code: a real PluginManager renders the real
// CluePlugin overlay through the real OverlayGraphics, and the cell boxes come from
// PluginContext.getInvCellBounds walking the real interface tree. What is built by hand is the
// WINDOW - the client has no cache here, so the trail_puzzle layer and its 5x5 grid are made
// from the numbers in the content's own trail_puzzle.if, read from the content repo when it is
// beside this one and transcribed below when it is not.
//
// So the picture answers the question a preview is for: with a shuffled box on screen, does the
// overlay mark the right cell, in the right place, legibly.
//
// Driven by tools/clienttests/run_puzzlepreview.py.
package jagex2.client;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;

import javax.imageio.ImageIO;

import jagex2.client.plugin.Overlay;
import jagex2.client.plugin.PluginManager;
import jagex2.client.plugin.builtin.PuzzlePieces;
import jagex2.client.plugin.builtin.SlidingPuzzle;
import jagex2.config.Component;
import jagex2.graphics.Pix2D;
import jagex2.graphics.PixFont;

public class PuzzlePreview {

	static final int W = 512;
	static final int H = 334;

	/** trail_puzzle.if's com_2, for a run with no content repo beside this one to read it from. */
	static int gridX = 171;
	static int gridY = 54;
	static int margin = 5;

	public static void main(String[] args) throws Exception {
		File out = new File(args[0]);
		int shuffle = args.length > 1 ? Integer.parseInt(args[1]) : 101;
		long seed = args.length > 2 ? Long.parseLong(args[2]) : 7;

		readGeometry(new File(args.length > 3 ? args[3] : "../DeathPlateau-Content"));

		Client client = new Client();
		set(client, "layout", Layout.fixed());
		PixFont small = new AwtFont(Font.PLAIN, 11);
		PixFont normal = new AwtFont(Font.PLAIN, 12);
		PixFont bold = new AwtFont(Font.BOLD, 12);

		window(shuffle, seed);
		client.viewportInterfaceId = PuzzlePieces.WINDOW;

		PluginManager manager = new PluginManager(client, small, normal, bold);
		manager.reload();
		boolean on = false;
		for (PluginManager.Entry entry : manager.getPlugins()) {
			if (entry.key.equals("clue-scroll")) {
				manager.setEnabled(entry, true);
				on = true;
			}
		}
		if (!on) {
			throw new IllegalStateException("the clue plugin is not loaded, so it drew nothing");
		}

		int[] pixels = new int[W * H];
		// The window's own panel colour behind it, so the overlay is judged against what it
		// will actually sit on rather than against black.
		java.util.Arrays.fill(pixels, 0x2E2820);
		Pix2D.bind(W, H, pixels);
		grid();
		manager.renderOverlays(W, H, Overlay.LAYER_SCREEN);

		BufferedImage image = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
		image.setRGB(0, 0, W, H, pixels, 0, W);
		ImageIO.write(image, "png", out);
		System.out.println("wrote " + out + " (grid at " + gridX + "," + gridY
			+ " margin " + margin + ", " + shuffle + " shuffle steps)");
		System.exit(0);
	}

	/** The real numbers out of trail_puzzle.if, when the content is there to read them from. */
	static void readGeometry(File content) {
		File file = new File(content,
			"scripts/minigames/game_trail/interfaces/trail_puzzle.if");
		if (!file.isFile()) {
			System.out.println("no content repo at " + content
				+ " - using the transcribed geometry, which may have drifted");
			return;
		}
		try {
			java.io.BufferedReader in = new java.io.BufferedReader(new java.io.FileReader(file));
			boolean here = false;
			String line;
			while ((line = in.readLine()) != null) {
				line = line.trim();
				if (line.startsWith("[")) {
					here = line.equals("[com_2]");
				} else if (here && line.startsWith("x=")) {
					gridX = Integer.parseInt(line.substring(2));
				} else if (here && line.startsWith("y=")) {
					gridY = Integer.parseInt(line.substring(2));
				} else if (here && line.startsWith("margin=")) {
					margin = Integer.parseInt(line.substring(7).split(",")[0]);
				}
			}
			in.close();
		} catch (Exception unreadable) {
			System.out.println("could not read " + file + " (" + unreadable + ")");
		}
	}

	/**
	 * The trail_puzzle window: a layer with the 5x5 grid in it, shuffled.
	 *
	 * Built rather than decoded, because a headless run has no cache - but built to the shape
	 * Component.decode would have produced, so everything downstream of it is the real thing.
	 */
	static void window(int steps, long seed) {
		int size = Math.max(PuzzlePieces.WINDOW, PuzzlePieces.GRID) + 1;
		Component.types = new Component[size];
		Component.data = new byte[size][];

		Component grid = new Component();
		grid.id = PuzzlePieces.GRID;
		grid.type = 2;
		grid.width = SlidingPuzzle.SIDE;
		grid.height = SlidingPuzzle.SIDE;
		grid.marginX = margin;
		grid.marginY = margin;
		grid.invSlotObjId = new int[SlidingPuzzle.CELLS];
		grid.invSlotObjCount = new int[SlidingPuzzle.CELLS];
		grid.invSlotOffsetX = new int[20];
		grid.invSlotOffsetY = new int[20];
		Component.types[PuzzlePieces.GRID] = grid;

		Component window = new Component();
		window.id = PuzzlePieces.WINDOW;
		window.type = 0;
		window.width = W;
		window.height = H;
		window.children = new int[] { PuzzlePieces.GRID };
		window.childX = new int[] { gridX };
		window.childY = new int[] { gridY };
		Component.types[PuzzlePieces.WINDOW] = window;

		// Shuffled the way the content does it: walk the gap from the solved board.
		int[] board = new int[SlidingPuzzle.CELLS];
		for (int cell = 0; cell < SlidingPuzzle.CELLS - 1; cell++) {
			board[cell] = cell;
		}
		board[SlidingPuzzle.CELLS - 1] = SlidingPuzzle.GAP;
		java.util.Random rng = new java.util.Random(seed);
		int gap = SlidingPuzzle.CELLS - 1;
		int[] options = new int[4];
		for (int step = 0; step < steps; step++) {
			int n = 0;
			for (int side = 0; side < 4; side++) {
				int next = SlidingPuzzle.neighbour(gap, side);
				if (next >= 0) {
					options[n++] = next;
				}
			}
			int pick = options[rng.nextInt(n)];
			board[gap] = board[pick];
			board[pick] = SlidingPuzzle.GAP;
			gap = pick;
		}
		for (int cell = 0; cell < SlidingPuzzle.CELLS; cell++) {
			grid.invSlotObjId[cell] = board[cell] == SlidingPuzzle.GAP
				? 0 : PuzzlePieces.ID[board[cell]] + 1;
			grid.invSlotObjCount[cell] = board[cell] == SlidingPuzzle.GAP ? 0 : 1;
		}
	}

	/** The cells themselves, so the overlay has something recognisable to sit on. */
	static void grid() {
		Component com = Component.types[PuzzlePieces.GRID];
		for (int cell = 0; cell < SlidingPuzzle.CELLS; cell++) {
			int x = gridX + (margin + 32) * (cell % SlidingPuzzle.SIDE);
			int y = gridY + (margin + 32) * (cell / SlidingPuzzle.SIDE);
			boolean empty = com.invSlotObjId[cell] == 0;
			Pix2D.fillRect(32, y, empty ? 0x1A1711 : 0x6B5B3E, 32, x);
			Pix2D.drawRect(y, 32, 0x0E0C09, x, 32);
		}
	}

	static void set(Object target, String name, Object value) throws Exception {
		Field f = Client.class.getDeclaredField(name);
		f.setAccessible(true);
		f.set(target, value);
	}

	/** A PixFont whose glyphs are drawn by AWT rather than read from the cache. */
	static final class AwtFont extends PixFont {

		AwtFont(int style, int size) {
			Font font = new Font(Font.SANS_SERIF, style, size);
			BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
			FontMetrics metrics = probe.createGraphics().getFontMetrics(font);
			this.height = metrics.getHeight() - 2;

			for (int c = 0; c < 256; c++) {
				String ch = String.valueOf((char) c);
				int w = Math.max(1, metrics.charWidth((char) c));
				int h = Math.max(1, metrics.getAscent() + metrics.getDescent());
				BufferedImage glyph = new BufferedImage(w + 2, h + 2, BufferedImage.TYPE_INT_ARGB);
				Graphics2D g = glyph.createGraphics();
				g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
					RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
				g.setFont(font);
				g.setColor(Color.WHITE);
				g.drawString(ch, 1, metrics.getAscent());
				g.dispose();

				byte[] mask = new byte[(w + 2) * (h + 2)];
				for (int y = 0; y < h + 2; y++) {
					for (int x = 0; x < w + 2; x++) {
						mask[y * (w + 2) + x] =
							(byte) (((glyph.getRGB(x, y) >>> 24) > 128) ? 1 : 0);
					}
				}
				this.charMask[c] = mask;
				this.charMaskWidth[c] = w + 2;
				this.charMaskHeight[c] = h + 2;
				this.charOffsetX[c] = 0;
				this.charOffsetY[c] = 0;
				this.charAdvance[c] = w;
			}
		}
	}
}
