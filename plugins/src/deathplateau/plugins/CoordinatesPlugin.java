package deathplateau.plugins;

import jagex2.client.plugin.ConfigItem;
import jagex2.client.plugin.Overlay;
import jagex2.client.plugin.OverlayGraphics;
import jagex2.client.plugin.Plugin;
import jagex2.client.plugin.PluginDescriptor;

/**
 * Where you are, in the corner of the screen.
 *
 * The smallest useful plugin there is, and the one to copy when starting a new one: a descriptor,
 * two settings, and an overlay that reads the context and draws. No events, no state, nothing to
 * clean up.
 */
@PluginDescriptor(
	name = "Coordinates",
	description = "Shows your world position in the corner of the viewport",
	key = "coordinates"
)
public final class CoordinatesPlugin extends Plugin {

	@ConfigItem(keyName = "showPlane", name = "Show plane", description = "Include which floor you are on")
	public boolean showPlane = true;

	@ConfigItem(keyName = "showFps", name = "Show fps", description = "Include the frame rate")
	public boolean showFps = false;

	private final Overlay overlay = new Overlay() {

		public void render(OverlayGraphics g) {
			if (!ctx.isLoggedIn()) {
				return;                     // nothing to report on the login screen
			}
			String text = ctx.getWorldX() + ", " + ctx.getWorldZ();
			if (showPlane) {
				text = text + ", " + ctx.getPlane();
			}
			if (showFps) {
				text = text + "   @gre@" + ctx.getFps() + "fps";
			}
			g.setFont(OverlayGraphics.FONT_SMALL).panel(6, 6, null, new String[] { text }, 0xFFB000, 0xFFFFFF);
		}
	};

	protected void startUp() {
		this.addOverlay(this.overlay);
	}
}
