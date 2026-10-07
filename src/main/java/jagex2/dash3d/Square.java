package jagex2.dash3d;

import deob.ObfuscatedName;
import jagex2.datastruct.Linkable;

public class Square extends Linkable {

	@ObfuscatedName("RIEEXHOP.e")
	public boolean field1379 = false;

	/**
	 * How many entities one tile of the scene can hold.
	 *
	 * It was FIVE, and World3D.method287 does not clip or queue when a tile is full - it returns
	 * false and the entity is not drawn AT ALL that frame. An entity claims every tile its footprint
	 * covers, so the bigger it is the more tiles it needs and the likelier one of them is already
	 * full. The biggest thing in the scene is therefore the first to vanish, which is exactly
	 * backwards.
	 *
	 * Zulrah is size 5 and, with the radius World3D.method285 adds, claims about a six-by-six block -
	 * thirty-odd tiles, any ONE of which being full drops the whole snake for a frame. In its own
	 * arena those tiles carry nine venom clouds a barrage, the orbs thrown at them, snakelings and
	 * the player.
	 *
	 * NOT YET PROVEN TO BE THE ZULRAH BUG. The mechanism is real and worth fixing on its own, but
	 * the owner reports the red and green forms blinking and the blue one NEVER doing it, and
	 * nothing here explains that split: counted off the rotation tables, blue barrages clouds on 5
	 * of its 26 runs and red on 2 of its 10, so the colour that never blinks clouds as often as one
	 * that does. Blue also spawns the most snakelings, and red - which blinks - throws no
	 * projectiles at all. The tilecrowd log in World3D.method287 is what settles it: it reports any
	 * tile under an entity holding five or more, which is what WOULD have dropped it before.
	 */
	public static final int CAPACITY = 16;

	@ObfuscatedName("RIEEXHOP.q")
	public Sprite[] field1391 = new Sprite[CAPACITY];

	@ObfuscatedName("RIEEXHOP.r")
	public int[] field1392 = new int[CAPACITY];

	@ObfuscatedName("RIEEXHOP.f")
	public int field1380;

	@ObfuscatedName("RIEEXHOP.i")
	public int field1383;

	@ObfuscatedName("RIEEXHOP.g")
	public int field1381;

	@ObfuscatedName("RIEEXHOP.h")
	public int field1382;

	@ObfuscatedName("RIEEXHOP.p")
	public int field1390;

	@ObfuscatedName("RIEEXHOP.s")
	public int field1393;

	@ObfuscatedName("RIEEXHOP.t")
	public int field1394;

	@ObfuscatedName("RIEEXHOP.x")
	public int field1398;

	@ObfuscatedName("RIEEXHOP.y")
	public int field1399;

	@ObfuscatedName("RIEEXHOP.z")
	public int field1400;

	@ObfuscatedName("RIEEXHOP.A")
	public int field1401;

	@ObfuscatedName("RIEEXHOP.j")
	public QuickGround field1384;

	@ObfuscatedName("RIEEXHOP.o")
	public GroundObject field1389;

	@ObfuscatedName("RIEEXHOP.k")
	public Ground field1385;

	@ObfuscatedName("RIEEXHOP.n")
	public GroundDecor field1388;

	@ObfuscatedName("RIEEXHOP.B")
	public Square field1402;

	@ObfuscatedName("RIEEXHOP.m")
	public Decor field1387;

	@ObfuscatedName("RIEEXHOP.l")
	public Wall field1386;

	@ObfuscatedName("RIEEXHOP.u")
	public boolean field1395;

	@ObfuscatedName("RIEEXHOP.v")
	public boolean field1396;

	@ObfuscatedName("RIEEXHOP.w")
	public boolean field1397;

	public Square(int arg0, int arg1, int arg2) {
		this.field1383 = this.field1380 = arg0;
		this.field1381 = arg1;
		this.field1382 = arg2;
	}
}
