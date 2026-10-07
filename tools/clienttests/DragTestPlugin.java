/*
 * The plugin OverlayDragTest drags around.
 *
 * It draws at fixed coordinates and knows NOTHING about dragging - no offset, no position config,
 * no mention of Alt. That is the whole claim being tested: an overlay becomes movable without
 * being told, which is why the plugins already published are movable without being rebuilt, and
 * why a plugin cannot put itself somewhere a player did not.
 *
 * Three things, each there to catch a different mistake:
 *
 *   the box      a solid rectangle, so "it moved" is a pixel in a new place
 *   the text     drawn from its BASELINE, which is the one part of measuring an overlay's extent
 *                that is easy to get a line out
 *   the button   a clickable region, because a region left behind where the overlay used to be
 *                is a button the player presses by clicking empty space
 */
package jagex2.client.plugin;

@PluginDescriptor(name = "Drag test", description = "A box to drag", key = "drag-test", apiLevel = 1)
public final class DragTestPlugin extends Plugin {

	static final int X = 100;
	static final int Y = 80;
	static final int W = 60;
	static final int H = 24;
	static final int COLOUR = 0x33CC55;

	/** Where the text's baseline sits, well below the box, so the two extents differ. */
	static final int TEXT_BASELINE = Y + 36;

	/** The clickable part, a corner of the box. */
	static final int BUTTON_W = 12;
	static final int BUTTON_H = 10;

	/** Counted so the test can tell whether the region moved with the drawing. */
	static int pressed;

	protected void startUp() {
		this.addOverlay(new Overlay() {

			public void render(OverlayGraphics g) {
				g.fill(X, Y, W, H, COLOUR);
				g.text(X, TEXT_BASELINE, "drag me", 0xFFFFFF);
				g.clickable(X, Y, BUTTON_W, BUTTON_H, new Runnable() {

					public void run() {
						pressed++;
					}
				});
			}
		});
	}
}
