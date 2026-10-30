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
		});
		held = map;

		MapWindow window = new MapWindow(map);
		window.setPlayer(3222, 3218, 0);
		window.pack();

		shot(window, map, out, "lumbridge", 3222, 3218, 0);
		shot(window, map, out, "varrock", 3213, 3428, 0);
		shot(window, map, out, "upstairs", 3222, 3218, 1);
		System.out.println("wrote 3 pictures to " + out);
		System.exit(0);
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
		img = paint(window);
		ImageIO.write(img, "png", new File(out, name + ".png"));
	}

	static BufferedImage paint(MapWindow window) {
		java.awt.Component view = window.getContentPane().getComponent(0);
		int wide = view.getWidth() > 0 ? view.getWidth() : 900;
		int high = view.getHeight() > 0 ? view.getHeight() : 700;
		view.setSize(wide, high);
		BufferedImage img = new BufferedImage(wide, high, BufferedImage.TYPE_INT_RGB);
		java.awt.Graphics2D g = img.createGraphics();
		view.paint(g);
		g.dispose();
		return img;
	}
}
