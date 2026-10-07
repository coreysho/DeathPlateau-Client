package jagex2.client.plugin.builtin;

import jagex2.client.plugin.Plugin;
import jagex2.client.plugin.PluginDescriptor;

/**
 * The Barrows tunnel door that opens for you is drawn green, so you can see which of the six it
 * is without trying them.
 *
 * NOT A DRAWN HIGHLIGHT. The green is the cache's own: the unlocked form of each per-player door
 * multiloc ships with a recolour and a little extra light (content: scripts/_unpack/377/all.loc),
 * and 377 threw both away. All this plugin does is stop throwing them away - which is why it is
 * a flag set once rather than an overlay, and why turning it off has to forget every loc type
 * already decoded with it on.
 */
@PluginDescriptor(
	name = "Barrows doors",
	description = "The tunnel door that opens for you keeps its green",
	key = "barrows-doors",
	enabledByDefault = true,
	legacySetting = "barrows_doors"
)
public final class BarrowsDoorsPlugin extends Plugin {

	protected void startUp() {
		this.ctx.setBarrowsDoorsHighlighted(true);
	}

	protected void shutDown() {
		this.ctx.setBarrowsDoorsHighlighted(false);
	}
}
