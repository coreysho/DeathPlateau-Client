package jagex2.client.plugin.builtin;

import jagex2.client.GameShell;
import jagex2.client.plugin.Plugin;
import jagex2.client.plugin.PluginDescriptor;
import jagex2.client.plugin.Subscribe;
import jagex2.client.plugin.event.KeyPressed;

/**
 * Escape closes whatever interface is open.
 *
 * The first of the QoL features to move out of Client.java. It was four lines there, switched on
 * by QolSettings.ESC_CLOSE; here it is a plugin, and Client.java has four fewer lines and one
 * less thing to know about.
 *
 * THE KEY IS NOT CONSUMED, deliberately. The old code closed interfaces and let the key carry on
 * through the rest of the key loop, so anything else that wanted Escape still got it. Consuming
 * it here would be a behaviour change dressed up as a port.
 */
@PluginDescriptor(
	name = "Escape closes interfaces",
	description = "Escape closes whatever interface is open",
	key = "escape-closes",
	enabledByDefault = true,
	legacySetting = "esc_close"
)
public final class EscapeClosesPlugin extends Plugin {

	@Subscribe
	public void onKeyPressed(KeyPressed event) {
		if (event.key == GameShell.KEY_ESCAPE) {
			this.ctx.closeInterfaces();
		}
	}
}
