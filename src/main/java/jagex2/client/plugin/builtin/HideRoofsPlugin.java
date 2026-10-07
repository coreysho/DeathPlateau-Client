package jagex2.client.plugin.builtin;

import jagex2.client.plugin.Plugin;
import jagex2.client.plugin.PluginDescriptor;

/**
 * Hides the roofs over buildings, so you can see yourself inside one.
 *
 * OFF BY DEFAULT, and the one QoL feature that was: every other one adds a convenience and
 * leaves the world alone, and this changes what the world looks like. Old School ships its own
 * Roofs toggle off as well, so on is the surprising answer in both places. That reasoning was in
 * QolSettings and comes with the feature.
 *
 * A SETTING RATHER THAN AN OVERLAY. The decision this changes is made inside the renderer, every
 * frame, deciding which level of the scene to draw - there is no event to hang it off. So the
 * plugin sets a flag while it runs and puts it back when it stops, which is also what makes
 * turning the plugin off show the roofs again immediately.
 */
@PluginDescriptor(
	name = "Hide roofs",
	description = "Hides the roofs over buildings",
	key = "hide-roofs",
	enabledByDefault = false,
	legacySetting = "roofs_off"
)
public final class HideRoofsPlugin extends Plugin {

	protected void startUp() {
		this.ctx.setRoofsHidden(true);
	}

	protected void shutDown() {
		this.ctx.setRoofsHidden(false);
	}
}
