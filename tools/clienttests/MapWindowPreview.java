/*
 * Paints the world map window to a png, so somebody can look at it.
 *
 * WHY THIS EXISTS. Everything else about the map is checked as arithmetic - which pixel a wall
 * lights up, which square goes where - and none of that says whether the window itself is any
 * good: whether the squares line up at the zoom they are scaled to, whether north is still up
 * once the view has done its own arithmetic on top of the renderer's, whether the you-are-here
 * marker lands on the player. Those are things you see or you do not.
 *
 * IT DRIVES THE REAL WINDOW over a packed cache instead of a server, through the Supply the map
 * takes for exactly that reason, and pumps the game thread's work by hand - tick() a few hundred
 * times, which is what a few hundred frames of a player staring at the map would do.
 *
 * Driven by tools/clienttests/run_mapwindowpreview.py, which needs a display.
 */
package jagex2.client;

import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;

import jagex2.client.worldmap.MapIcons;
import jagex2.client.worldmap.MapWindow;
import jagex2.client.worldmap.WorldMap;

public class MapWindowPreview {

	public static void main(String[] args) throws Exception {
		File pack = new File(args[0]);
		File out = new File(args[1]);
		if (!out.isDirectory() && !out.mkdirs()) {
			throw new IllegalStateException("cannot make " + out);
		}

		WorldMapRender.loadConfig(pack);
		MapIcons.useSprites(WorldMapRender.mapfunctions(pack));
		WorldMapRender.loadModels(pack);
		WorldMapRender.loadIndex(pack);
		WorldMapRender.maps = new jagex2.io.FileStream(5, 600000,
			new java.io.RandomAccessFile(new File(pack, "main_file_cache.dat"), "r"),
			new java.io.RandomAccessFile(new File(pack, "main_file_cache.idx4"), "r"));

		final WorldMap map = new WorldMap(new WorldMap.Supply() {

			public int fileFor(int x, int z, int type) {
				return WorldMapRender.fileFor(x, z, type);
			}

			public void request(int file) {
				// The packed cache answers at once, so a request and its answer are one step.
				try {
					map().arrived(file, WorldMapRender.fetch(file));
				} catch (Exception broken) {
					map().arrived(file, null);
				}
			}

			public void prefetchAll() {
			}

			public int[] squares() {
				int[] all = new int[WorldMapRender.mapIndex.length];
				System.arraycopy(WorldMapRender.mapIndex, 0, all, 0, all.length);
				return all;
			}
		});
		held = map;

		MapWindow window = new MapWindow(map);
		window.setPlayer(3222, 3218, 0);
		// SHOWN FOR REAL, under whatever display the runner found. A window that is never
		// shown never lays itself out properly and never runs its own repaint timer, so a
		// preview of one is a preview of something the player will not see.
		window.setVisible(true);

		// Every square's scenery, so the key and the search have the whole world in them - the
		// game gets this from the prefetch over a few minutes; here it is a file read.
		for (int i = 0; i < WorldMapRender.mapIndex.length; i++) {
			int sx = WorldMapRender.mapIndex[i] >> 8;
			int sz = WorldMapRender.mapIndex[i] & 0xFF;
			map.icons().add(sx, sz, WorldMapRender.fetch(WorldMapRender.fileFor(sx, sz, 1)));
		}
		System.out.println("icons: " + map.icons().all().size());

		shot(window, map, out, "lumbridge", 3222, 3218, 0);
		shot(window, map, out, "varrock", 3213, 3428, 0);
		shot(window, map, out, "upstairs", 3222, 3218, 1);
		searched(window, map, out, "furnace");
		orb(pack, out);
		System.out.println("wrote 5 pictures to " + out);
		System.exit(0);
	}

	/** A search typed in and entered, which is the only way to see the rings it draws. */
	static void searched(MapWindow window, WorldMap map, File out, String text) throws Exception {
		window.setPlayer(3222, 3218, 0);
		window.typeSearch(text);
		BufferedImage img = null;
		for (int pass = 0; pass < 4; pass++) {
			img = paint(window);
			for (int frame = 0; frame < 400; frame++) {
				map.tick();
			}
		}
		ImageIO.write(paint(window), "png", new File(out, "search.png"));
	}

	/**
	 * The orb on the minimap's own frame, cold and under the cursor.
	 *
	 * The one thing about the orb that only an eye can judge is whether it looks like a button
	 * and whether the eleven pixels on its face look like a map. Everything else about it -
	 * where it is, that it clears the round map, that its click is round and not square - is
	 * arithmetic, and is checked as arithmetic in MapWindowTest.
	 */
	static void orb(File pack, File out) throws Exception {
		jagex2.graphics.Pix8 back =
			new jagex2.graphics.Pix8(WorldMapRender.jagfile(pack, "media"), "mapback", 0);
		int wide = jagex2.client.worldmap.MapOrb.PANEL_WIDE;
		int high = jagex2.client.worldmap.MapOrb.PANEL_HIGH;
		BufferedImage sheet = new BufferedImage(wide * 6, high * 3, BufferedImage.TYPE_INT_RGB);
		java.awt.Graphics2D sg = sheet.createGraphics();
		for (int pass = 0; pass < 2; pass++) {
			int[] pixels = new int[wide * high];
			jagex2.graphics.Pix2D.bind(wide, high, pixels);
			jagex2.graphics.Pix2D.resetClipping();
			for (int y = 0; y < back.hi && y < high; y++) {
				for (int x = 0; x < back.wi && x < wide; x++) {
					int p = back.pixels[y * back.wi + x] & 0xFF;
					pixels[y * wide + x] = p == 0 ? 0x2E2A22 : back.bpal[p];
				}
			}
			jagex2.client.worldmap.MapOrb.draw(pass == 1);
			BufferedImage one = new BufferedImage(wide, high, BufferedImage.TYPE_INT_RGB);
			one.setRGB(0, 0, wide, high, pixels, 0, wide);
			sg.drawImage(one, pass * wide * 3, 0, wide * 3, high * 3, null);
		}
		sg.dispose();
		ImageIO.write(sheet, "png", new File(out, "orb.png"));
	}

	static WorldMap held;

	static WorldMap map() {
		return held;
	}

	/**
	 * One view, with the game thread's share of the work done first.
	 *
	 * The window only asks for what it is about to draw, so it has to be drawn once to find out
	 * what it wants, then given the frames to get it, then drawn again.
	 */
	static void shot(MapWindow window, WorldMap map, File out, String name,
			int atX, int atZ, int level) throws Exception {
		window.setPlayer(atX, atZ, level);
		window.centreOn(atX, atZ);
		BufferedImage img = null;
		for (int pass = 0; pass < 4; pass++) {
			img = paint(window);
			for (int frame = 0; frame < 400; frame++) {
				map.tick();
			}
		}
		ImageIO.write(paint(window), "png", new File(out, name + ".png"));
	}

	/**
	 * The whole window, not just the map: the key down the side and the bar along the bottom
	 * are the half of this that arithmetic cannot check.
	 */
	static BufferedImage paint(MapWindow window) {
		java.awt.Container pane = window.getContentPane();
		java.awt.Dimension want = pane.getSize();
		if (want.width <= 0) {
			want = pane.getPreferredSize();
			pane.setSize(want);
		}
		layOut(pane);
		BufferedImage img = new BufferedImage(want.width, want.height, BufferedImage.TYPE_INT_RGB);
		java.awt.Graphics2D g = img.createGraphics();
		pane.paint(g);
		g.dispose();
		return img;
	}

	/** Swing only lays a container out once it is on screen; off screen it has to be told. */
	static void layOut(java.awt.Container of) {
		of.doLayout();
		for (int i = 0; i < of.getComponentCount(); i++) {
			java.awt.Component child = of.getComponent(i);
			if (child instanceof java.awt.Container) {
				layOut((java.awt.Container) child);
			}
		}
	}
}
