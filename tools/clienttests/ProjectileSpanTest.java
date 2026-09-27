// Harness for tools/clienttests/run_projectilespantest.py - the REAL World3D, with stub models that
// only record the order they are drawn in.
//
// The scene is a straight run of seven wall segments with an open door leaf on the middle tile, a
// player standing on one of the four tiles beside the door tile, and a projectile at every 16 units
// within 96 of that player's centre - where a dart is for the first ticks of its flight. For each
// camera position the scene is drawn once without the projectile and once with it, and the order the
// walls and the door come out in has to be the same both times: a projectile is small, and it must
// not decide which of two walls is painted over the other. With 377's padding of 60 it did - a wall
// behind an open door came out after the door and painted over it, which is the door "vanishing"
// while a toxic blowpipe dart flies (reported with a video, 2026-09-27).
//
//     java ProjectileSpanTest <padding>   prints "frames=N orderChanged=M projMissing=K"
import jagex2.dash3d.ModelSource;
import jagex2.dash3d.World3D;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ProjectileSpanTest {

	static final List<String> order = new ArrayList<>();

	static final class Stub extends ModelSource {
		final String name;

		Stub(String name) {
			this.name = name;
		}

		@Override
		public void method381(int a0, int a1, int a2, int a3, int a4, int a5, int a6, int a7, int a8) {
			order.add(this.name);
		}
	}

	public static void main(String[] args) {
		int pad = Integer.parseInt(args[0]);
		// every tile visible: the test is about the painter's order, not the frustum
		for (boolean[][][] a : World3D.field1068) {
			for (boolean[][] b : a) {
				for (boolean[] c : b) {
					Arrays.fill(c, true);
				}
			}
		}
		int frames = 0;
		int changed = 0;
		int missing = 0;
		int[] types = { 1, 2, 4, 8 }; // the four straight-wall edges
		for (int wt : types) {
			for (int dt : types) {
				if (wt == dt) {
					continue;
				}
				World3D s = new World3D(new int[4][105][105], 104, 4, 104);
				s.method275(0);
				boolean alongZ = wt == 1 || wt == 4;
				for (int i = -3; i <= 3; i++) {
					int x = alongZ ? 50 : 50 + i;
					int z = alongZ ? 50 + i : 50;
					if (i == 0) {
						s.method282(0, 0, dt, null, x, 0, (byte) 0, z, new Stub("door"), 0);
					} else {
						s.method282(0, 0, wt, null, x, 0, (byte) 0, z, new Stub("wall" + i), 0);
					}
				}
				Stub player = new Stub("player");
				Stub proj = new Stub("proj");
				for (int cdx = -10; cdx <= 10; cdx += 5) {
					for (int cdz = -10; cdz <= 10; cdz += 5) {
						if (cdx == 0 && cdz == 0) {
							continue;
						}
						int camX = (50 + cdx) * 128 + 64;
						int camZ = (50 + cdz) * 128 + 64;
						for (int[] side : new int[][] { { -1, 0 }, { 1, 0 }, { 0, -1 }, { 0, 1 } }) {
							int px = (50 + side[0]) * 128 + 64;
							int pz = (50 + side[1]) * 128 + 64;
							s.clearLocChanges();
							order.clear();
							s.method285(-1, player, px, 0, false, 0, 0, 60, pz, 0);
							s.draw(camX, 3, -600, camZ, 0, 256);
							List<String> base = new ArrayList<>(order);
							base.remove("player");
							for (int ox = -96; ox <= 96; ox += 16) {
								for (int oz = -96; oz <= 96; oz += 16) {
									s.clearLocChanges();
									order.clear();
									s.method285(-1, player, px, 0, false, 0, 0, 60, pz, 0);
									s.method285(-1, proj, px + ox, -40, false, 0, 0, pad, pz + oz, 0);
									s.draw(camX, 3, -600, camZ, 0, 256);
									frames++;
									if (!order.contains("proj")) {
										missing++;
									}
									List<String> now = new ArrayList<>(order);
									now.remove("player");
									now.remove("proj");
									if (!now.equals(base)) {
										changed++;
									}
								}
							}
						}
					}
				}
			}
		}
		System.out.println("frames=" + frames + " orderChanged=" + changed + " projMissing=" + missing);
	}
}
