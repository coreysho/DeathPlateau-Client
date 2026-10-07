/*
 * An overlay that sits on top of DragTestPlugin's, for the "which one did I grab" case.
 *
 * Two overlays can overlap - a tracker over a status bar, two panels in the same corner - and the
 * one a player is pointing at is the one they can SEE, which is the one drawn last. Getting that
 * backwards means clicking the thing on top and picking up the thing underneath it.
 *
 * A higher priority than the default, so the manager sorts it after DragTestPlugin's and draws it
 * over the top.
 */
package jagex2.client.plugin;

@PluginDescriptor(name = "Overlap test", description = "Sits on top", key = "overlap-test",
	apiLevel = 1)
public final class OverlapTestPlugin extends Plugin {

	/** Overlapping the right-hand half of DragTestPlugin's box. */
	static final int X = DragTestPlugin.X + 30;
	static final int Y = DragTestPlugin.Y + 4;
	static final int W = 40;
	static final int H = 16;
	static final int COLOUR = 0xEE8822;

	protected void startUp() {
		this.addOverlay(new Overlay() {

			public int priority() {
				return 10;
			}

			public void render(OverlayGraphics g) {
				g.fill(X, Y, W, H, COLOUR);
			}
		});
	}
}
