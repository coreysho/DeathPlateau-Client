/*
 * Where every map icon is: the banks, the altars, the furnaces, the fishing spots.
 *
 * NOT PART OF THE PICTURE, DELIBERATELY. A square is drawn once at four pixels a tile and then
 * scaled to whatever the window is showing; an icon baked into it would shrink with the map and
 * be four pixels across by the time a player can see a county at once, which is exactly when
 * they most want to find a bank. So icons are kept as positions and drawn over the top at the
 * same size whatever the zoom - which is also what makes them searchable and what lets the key
 * say how many of each there are.
 *
 * WHERE THEY COME FROM. A loc carries a mapfunction number; the client's own minimap collects
 * them the same way, by walking the scene and asking LocType. This reads the loc file itself
 * instead, because a map wants every icon in the world rather than the hundred tiles around the
 * player, and because a file is four kilobytes where a built scene is megabytes.
 *
 * THE CLIENT SCATTERS ITS MINIMAP ICONS and this does not. createMinimap walks each one up to
 * three tiles off its loc, re-rolled on every scene build, so that two icons on top of each
 * other come apart. On a minimap that is sensible; on a map you are reading coordinates off it
 * is not, so these sit where the content put them.
 */
package jagex2.client.worldmap;

import java.util.ArrayList;
import java.util.List;

import java.awt.image.BufferedImage;

import jagex2.config.LocType;
import jagex2.graphics.Pix32;
import jagex2.io.Packet;

public final class MapIcons {

	/**
	 * The pictures, handed in by whoever loaded the media.
	 *
	 * NOT THE CLIENT'S OWN COPY, for the same reason the map does not use its mapscenes: the
	 * client shifts all of its map art by a random shade on every login, and a map whose bank
	 * icon is a different yellow each time you open it is a map that looks broken.
	 */
	private static Pix32[] art = new Pix32[0];

	private static BufferedImage[] drawn = new BufferedImage[0];

	public static void useSprites(Pix32[] sprites) {
		art = sprites == null ? new Pix32[0] : sprites;
		drawn = new BufferedImage[art.length];
	}

	/**
	 * One icon's picture, converted once.
	 *
	 * A Pix32 has no alpha channel - it has one transparent colour, pure black - so the
	 * conversion is where that becomes a transparent pixel. Done once per icon rather than per
	 * repaint, because a window full of banks repaints twice a second.
	 */
	public static BufferedImage sprite(int function) {
		if (function < 0 || function >= art.length || art[function] == null) {
			return null;
		}
		if (drawn[function] != null) {
			return drawn[function];
		}
		Pix32 from = art[function];
		BufferedImage out = new BufferedImage(from.wi, from.hi, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < from.hi; y++) {
			for (int x = 0; x < from.wi; x++) {
				int rgb = from.pixels[y * from.wi + x];
				out.setRGB(x, y, rgb == 0 ? 0 : 0xFF000000 | rgb);
			}
		}
		drawn[function] = out;
		return out;
	}

	/** One icon, where the content put it. */
	public static final class Icon {

		public final int x;
		public final int z;
		public final int level;
		public final int function;

		Icon(int x, int z, int level, int function) {
			this.x = x;
			this.z = z;
			this.level = level;
			this.function = function;
		}

		public String name() {
			return MapIconNames.name(this.function);
		}
	}

	/**
	 * The mapfunction of every loc type, read once.
	 *
	 * LocType.method561 is a cache of twenty that the game is also using, and a world has a
	 * million and a half locs in it. Asking it once per loc would be a million and a half
	 * evictions of something the game wanted; asking it once per TYPE is twenty thousand.
	 *
	 * HANDED IN, OR READ FROM LocType IF NOT. The game has the config loaded and leaves this
	 * alone; anything driving the map without one - the checks, which are about where an icon
	 * lands rather than about what the cache says - supplies its own.
	 */
	private int[] functionOf;

	public void useFunctions(int[] functionOfLocType) {
		this.functionOf = functionOfLocType;
	}

	private final List icons = new ArrayList();

	/** Squares already read, so a loc file arriving twice does not double every icon on it. */
	private final List read = new ArrayList();

	/**
	 * Reads one square's loc file and keeps whatever icons are on it.
	 *
	 * The decode is the shape World.method27 uses - a run of ids, each with a run of positions -
	 * stopping at the position rather than going on to build anything, because an icon needs a
	 * tile and a number and nothing else.
	 */
	public void add(int sx, int sz, byte[] locData) {
		if (locData == null || locData.length == 0) {
			return;
		}
		Integer square = Integer.valueOf((sx << 8) + sz);
		if (this.read.contains(square)) {
			return;
		}
		this.read.add(square);
		int[] function = this.functions();
		Packet buf = new Packet(locData);
		int id = -1;
		while (true) {
			int step = buf.gsmarts();
			if (step == 0) {
				return;
			}
			id += step;
			int at = 0;
			while (true) {
				int moved = buf.gsmarts();
				if (moved == 0) {
					break;
				}
				at += moved - 1;
				buf.g1();
				if (id < 0 || id >= function.length || function[id] < 0) {
					continue;
				}
				this.icons.add(new Icon(sx * MapSquare.SQUARE + (at >> 6 & 0x3F),
					sz * MapSquare.SQUARE + (at & 0x3F), at >> 12, function[id]));
			}
		}
	}

	private int[] functions() {
		if (this.functionOf == null) {
			int[] all = new int[LocType.field1662];
			for (int id = 0; id < all.length; id++) {
				all[id] = LocType.method561(id).field1660;
			}
			this.functionOf = all;
		}
		return this.functionOf;
	}

	/** Every icon known, in the order they were read. */
	public List all() {
		return this.icons;
	}

	/** How many icons of one kind there are, which is what the key puts beside its rows. */
	public int count(int function) {
		int n = 0;
		for (int i = 0; i < this.icons.size(); i++) {
			if (((Icon) this.icons.get(i)).function == function) {
				n++;
			}
		}
		return n;
	}

	/**
	 * How many of each kind there are, all of them in one walk of the list.
	 *
	 * The key has sixty-four rows and refreshes four times a second while the map is open;
	 * counting each row on its own is sixty-four walks of every icon in the world for a
	 * number that changes only when a map file lands.
	 */
	public int[] counts(int kinds) {
		int[] found = new int[kinds];
		for (int i = 0; i < this.icons.size(); i++) {
			int function = ((Icon) this.icons.get(i)).function;
			if (function >= 0 && function < kinds) {
				found[function]++;
			}
		}
		return found;
	}

	/**
	 * The icons inside a rectangle of world tiles on one level.
	 *
	 * Walked rather than indexed: a world holds a few thousand of these, and a linear pass over
	 * a few thousand once a repaint is cheaper than keeping a grid in step with a list that
	 * grows every time a map file lands.
	 */
	public List within(int x0, int z0, int x1, int z1, int level) {
		List found = new ArrayList();
		for (int i = 0; i < this.icons.size(); i++) {
			Icon icon = (Icon) this.icons.get(i);
			if (icon.level == level && icon.x >= x0 && icon.x <= x1
					&& icon.z >= z0 && icon.z <= z1) {
				found.add(icon);
			}
		}
		return found;
	}

	/**
	 * Icons whose name contains the text, nearest to a tile first.
	 *
	 * NEAREST FIRST BECAUSE THERE ARE THIRTY-NINE BANKS. A search that answers "bank" with the
	 * first one in file order sends a player in Varrock to Falador; one that answers with the
	 * nearest is the one they meant. Distance is measured as a player walks it, the larger of
	 * the two axes, which is the same measure the clue page uses.
	 */
	public List search(String text, final int fromX, final int fromZ) {
		String want = text == null ? "" : text.trim().toLowerCase();
		List found = new ArrayList();
		if (want.length() == 0) {
			return found;
		}
		for (int i = 0; i < this.icons.size(); i++) {
			Icon icon = (Icon) this.icons.get(i);
			if (icon.name().toLowerCase().indexOf(want) >= 0) {
				found.add(icon);
			}
		}
		java.util.Collections.sort(found, new java.util.Comparator() {

			public int compare(Object left, Object right) {
				return paces((Icon) left, fromX, fromZ) - paces((Icon) right, fromX, fromZ);
			}
		});
		return found;
	}

	public static int paces(Icon icon, int fromX, int fromZ) {
		int dx = icon.x > fromX ? icon.x - fromX : fromX - icon.x;
		int dz = icon.z > fromZ ? icon.z - fromZ : fromZ - icon.z;
		return dx > dz ? dx : dz;
	}
}
