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

// NOT final: {@link #locCount()} and {@link #locFunction(int)} are the seam the checks override,
// because the real pair read the loc configs out of a cache a headless check has not got.
public class MapIcons {

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
	 * The mapfunction of every loc type, and how many types it was built for.
	 *
	 * LocType.method561 is a cache of twenty that the game is also using, and a world has a
	 * million and a half locs in it. Asking it once per loc would be a million and a half
	 * evictions of something the game wanted; asking it once per TYPE is twenty thousand.
	 */
	private int[] functionOf;

	private int builtFor = -1;

	/**
	 * How many loc types the cache knows, which is 0 until the loc configs have loaded.
	 *
	 * A SEAM, because the checks have no cache to ask and this is the number the whole of
	 * {@link #functions()} turns on. Overridden by a MapIcons the checks make for themselves.
	 */
	protected int locCount() {
		return LocType.field1662;
	}

	/** One loc type's mapfunction, or -1 for a loc that is not marked on the map. The same seam. */
	protected int locFunction(int id) {
		return LocType.method561(id).field1660;
	}

	/**
	 * Build the table if the cache's count of loc types has changed, and say whether it did.
	 *
	 * ASKED ONCE A FRAME by whoever holds the loc files, because a true means everything read
	 * before now was read against a table that could not name it and should go through again.
	 *
	 * IT ASKS RATHER THAN BEING TOLD, which is the whole point: the table is built when a
	 * square arrives, and a client whose squares ALL arrived before the loc configs did - which
	 * is every client with a file store, for the squares around wherever it logs in - would
	 * otherwise have nothing left to trigger it. The check is an int comparison.
	 */
	public boolean refresh() {
		int before = this.builtFor;
		this.functions();
		return this.builtFor != before;
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
		// THE TABLE FIRST, because building it empties `read` - a square parsed against the
		// table that was there before found whatever that one could name, which before the loc
		// configs load is nothing at all.
		int[] function = this.functions();
		Integer square = Integer.valueOf((sx << 8) + sz);
		if (this.read.contains(square)) {
			return;
		}
		this.read.add(square);
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

	/**
	 * The mapfunction of every loc type, rebuilt whenever the cache's count of them changes.
	 *
	 * IT USED TO BE BUILT ON FIRST USE AND KEPT WHATEVER THAT GAVE, and what that gave depended
	 * on a race nobody wins twice the same way. A loc file reaching the map is what triggers
	 * this, and with a file store on disk the map files are there at login - before the loc
	 * configs are. {@link #locCount()} is 0 until they load, so the table was built with no
	 * entries at all, memoised, and every id thereafter fell outside it: the map drew not one
	 * icon for the rest of the session.
	 *
	 * A PLAYER ALWAYS HAS A FILE STORE, so a player always lost that race - and the driver that
	 * checked this had a fresh home directory and no store, so it always won it and reported 55
	 * icons. Measured on a real client against a real server: 0 icons out of 1,404 map files
	 * before this, 641 after.
	 *
	 * ONE MECHANISM, NOT THREE. An earlier pass of this fix also refused to read a square while
	 * the table was empty and refused to memoise an empty table, and the mutation runner showed
	 * both for what they were - the count check below already covers them, and three guards
	 * over one invariant means two of them are never the one that fires.
	 */
	private int[] functions() {
		int count = this.locCount();
		if (this.functionOf == null || this.builtFor != count) {
			int[] all = new int[count];
			for (int id = 0; id < count; id++) {
				all[id] = this.locFunction(id);
			}
			this.functionOf = all;
			this.builtFor = count;
			this.read.clear();
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
