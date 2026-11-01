// THE GAME LOOP SURVIVES A THROWN FRAME, AND STOPS WHEN THEY KEEP COMING.
//
// GameShell.run called update() and draw() bare. One exception out of either ended the loop
// thread, and because nothing above it caught anything the window simply stayed on its last
// painted frame - no message, no log the player could find, nothing to tell it apart from a scene
// that is loading slowly. This drives a real shell in a real window and holds the guard to both
// halves of its contract:
//
//   1. a single faulting frame is logged and stepped over, and the game carries on
//   2. frames that keep faulting stop the client, and the window stays up to say so
//
// TO SEE IT FAIL, take the try/catch off update() and draw() in GameShell.run: part 1 dies with
// the loop thread and the update count stops climbing. That is the whole bug, reproduced.
//
//   javac -cp build/classes/java/main -d <out> tools/clienttests/CrashGuardTest.java
//   java  -cp "build/classes/java/main;<out>" jagex2.client.CrashGuardTest
package jagex2.client;

public final class CrashGuardTest extends GameShell {

	/** Frames left to throw on. Set it to 1 for a blip, to something huge for a dead client. */
	private volatile int inject;

	private volatile int updates;
	private volatile int draws;

	private static int failures;

	public void load() {
		// nothing to load: this test is about the loop, not about the game in it
	}

	public void update() {
		this.updates++;
		this.maybeThrow();
	}

	public void draw() {
		this.draws++;
		this.maybeThrow();
	}

	private void maybeThrow() {
		if (this.inject > 0) {
			this.inject--;
			throw new IllegalStateException("injected fault");
		}
	}

	public static void main(String[] args) throws Exception {
		CrashGuardTest shell = new CrashGuardTest();
		shell.initApplication(503, 765);

		// WAIT FOR THE LOOP, do not assume it. run() calls drawProgress first, which blocks a
		// second at a time until AWT hands the component a Graphics - so the loop can be several
		// seconds behind initApplication returning, and a fixed sleep measures the wrong thing.
		int running = awaitUpdates(shell, 100, 20000L);
		check("the loop is running at all", running >= 100, "updates=" + running);

		// 1. ONE BAD FRAME
		shell.inject = 1;
		Thread.sleep(1000L);
		int afterBlip = shell.updates;
		check("a single faulting frame does not stop the loop", afterBlip > running + 10,
			"updates went " + running + " -> " + afterBlip);

		// 2. FRAMES THAT KEEP FAULTING
		shell.inject = Integer.MAX_VALUE;
		Thread.sleep(2500L);
		int atGiveUp = shell.updates;
		Thread.sleep(1500L);
		int afterGiveUp = shell.updates;
		check("a fault every frame stops the loop", afterGiveUp == atGiveUp,
			"updates went " + atGiveUp + " -> " + afterGiveUp + " after it should have stopped");

		// ...and the window is still there, which on its own proves nothing: the OLD behaviour
		// also left a window up and the updates stopped. The difference is whether anything is
		// written on it, so the pixels are what gets checked.
		check("the window is still up", shell.frame != null && shell.frame.isVisible(),
			"frame=" + shell.frame);

		java.awt.image.BufferedImage shot = new java.awt.Robot()
			.createScreenCapture(shell.getBaseComponent().getBounds(
				new java.awt.Rectangle(shell.getBaseComponent().getLocationOnScreen(), shell.getBaseComponent().getSize())));
		int lit = 0;
		for (int y = 0; y < shot.getHeight(); y += 2) {
			for (int x = 0; x < shot.getWidth(); x += 2) {
				if ((shot.getRGB(x, y) & 0xFFFFFF) > 0x202020) {
					lit++;
				}
			}
		}
		check("the stopped window has writing on it", lit > 200, lit + " lit pixels sampled");

		if (args.length > 0) {
			javax.imageio.ImageIO.write(shot, "png", new java.io.File(args[0]));
			System.out.println("  wrote " + args[0]);
		}

		System.out.println(failures == 0 ? "PASS" : failures + " FAILED");
		System.exit(failures == 0 ? 0 : 1);
	}

	/** Blocks until the loop has run at least this many updates, or the time is up. */
	private static int awaitUpdates(CrashGuardTest shell, int want, long timeoutMs) throws Exception {
		long until = System.currentTimeMillis() + timeoutMs;
		while (shell.updates < want && System.currentTimeMillis() < until) {
			Thread.sleep(100L);
		}
		return shell.updates;
	}

	private static void check(String what, boolean ok, String detail) {
		if (!ok) {
			failures++;
		}
		System.out.println((ok ? "  ok   " : "  FAIL ") + what + "  [" + detail + "]");
	}
}
