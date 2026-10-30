/*
 * A screen-layer overlay that says it is pinned, for the second case Alt-drag must refuse.
 *
 * The scene layer is refused because its coordinates come from a world projection. This is the
 * other kind: an overlay drawn ON something the client has put on screen - the numbers on the
 * cells of an open puzzle box - whose coordinates come from the interface it is marking. It is
 * on the screen layer, because it has to draw over the window rather than under it, and an
 * offset on it moves the numbers off the pieces they label.
 */
package jagex2.client.plugin;

@PluginDescriptor(name = "Pinned overlay", description = "Drawn on a window", key = "pinned-overlay",
	apiLevel = 1)
public final class PinnedOverlayPlugin extends Plugin {

	static final int X = 300;
	static final int Y = 60;
	static final int COLOUR = 0x33DD99;

	protected void startUp() {
		this.addOverlay(new Overlay() {

			public int layer() {
				return Overlay.LAYER_SCREEN;
			}

			public boolean movable() {
				return false;
			}

			public void render(OverlayGraphics g) {
				g.fill(X, Y, 30, 16, COLOUR);
			}
		});
	}
}
