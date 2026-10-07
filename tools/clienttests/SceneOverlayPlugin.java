/*
 * A scene-layer overlay, for the one case Alt-drag must refuse.
 *
 * A scene overlay is drawn over a tile in the world - a label on a pile of loot, a highlight on a
 * door - and those are positioned from the projection of a world coordinate. An offset on one is
 * not a moved panel, it is a label pointing at the wrong thing.
 */
package jagex2.client.plugin;

@PluginDescriptor(name = "Scene overlay", description = "Drawn over a tile", key = "scene-overlay",
	apiLevel = 1)
public final class SceneOverlayPlugin extends Plugin {

	static final int X = 220;
	static final int Y = 140;
	static final int COLOUR = 0x2255EE;

	protected void startUp() {
		this.addOverlay(new Overlay() {

			public int layer() {
				return Overlay.LAYER_SCENE;
			}

			public void render(OverlayGraphics g) {
				g.fill(X, Y, 30, 16, COLOUR);
			}
		});
	}
}
