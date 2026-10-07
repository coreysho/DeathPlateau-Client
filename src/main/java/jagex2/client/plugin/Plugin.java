package jagex2.client.plugin;

import java.util.ArrayList;
import java.util.List;

/**
 * A client feature that can be turned on and off without the client knowing what it does.
 *
 * Write one by extending this, tagging it with {@link PluginDescriptor}, and adding
 * {@link Subscribe} methods for the events you care about. Draw by adding an {@link Overlay} in
 * {@link #startUp()}. Everything the client will tell you is on {@link #ctx}.
 *
 * <pre>
 * {@literal @}PluginDescriptor(name = "Coordinates", description = "Your position, on screen")
 * public final class CoordinatesPlugin extends Plugin {
 *     {@literal @}Override
 *     protected void startUp() {
 *         this.addOverlay(new Overlay() {
 *             public void render(OverlayGraphics g) {
 *                 g.text(6, 20, ctx.getWorldX() + ", " + ctx.getWorldZ(), 0xFFFF00);
 *             }
 *         });
 *     }
 * }
 * </pre>
 *
 * LIFECYCLE. startUp() runs when the player turns the plugin on (and at launch for one that was
 * already on), shutDown() when they turn it off or the client closes. Between the two, the plugin
 * is subscribed to events and its overlays are drawn; outside them it is inert and must leave
 * nothing behind. Anything a plugin starts in startUp() - a timer, a cached list - it undoes in
 * shutDown(), because the player can toggle it repeatedly in one session.
 *
 * THREADING. Every method here is called on the client thread, in the game loop. There is no
 * locking to do, and no excuse for slow work: a plugin that takes 20ms costs a frame.
 */
public abstract class Plugin {

	/** The client, as much of it as plugins may see. Set before startUp() is ever called. */
	protected PluginContext ctx;

	/** This plugin's saved settings. Fields tagged @ConfigItem are read and written through it. */
	protected PluginConfig config;

	private final List<Overlay> overlays = new ArrayList<Overlay>();

	/** A list on this plugin's config page, under its heading. */
	static final class NamedList {

		final String title;
		final ConfigList list;

		NamedList(String title, ConfigList list) {
			this.title = title;
			this.list = list;
		}
	}

	/** Lists this plugin shows on its config page, in the order they were added. */
	private final List<NamedList> configLists = new ArrayList<NamedList>();

	/**
	 * A page of this plugin's own, reached from an icon on the sidebar's rail.
	 *
	 * DIFFERENT FROM A CONFIG LIST, which is a section of the plugin's settings page and is
	 * about what the player has told it. A panel is the plugin's own readout - what it has
	 * measured, what it is watching - and gets a place in the rail of its own, the way
	 * RuneLite's XP tracker does.
	 */
	static final class Panel {

		final String title;
		final String icon;
		final ConfigList list;

		Panel(String title, String icon, ConfigList list) {
			this.title = title;
			this.icon = icon;
			this.list = list;
		}
	}

	private final List<Panel> panels = new ArrayList<Panel>();

	/** Set by the manager so a plugin cannot lie about whether it is running. */
	private boolean running;

	/** Called by the manager, never by a plugin. */
	final void attach(PluginContext ctx, PluginConfig config) {
		this.ctx = ctx;
		this.config = config;
	}

	/** Turned on. Register overlays and set up state here. */
	protected void startUp() {
	}

	/** Turned off. Undo whatever startUp() did. */
	protected void shutDown() {
	}

	/**
	 * Adds an overlay for as long as the plugin is on. Call it from startUp(); overlays added
	 * there are removed automatically on shutdown, so there is nothing to clean up.
	 */
	protected final void addOverlay(Overlay overlay) {
		if (overlay != null && !this.overlays.contains(overlay)) {
			overlay.owner = this;
			this.overlays.add(overlay);
		}
	}

	/** Removes an overlay early. Rarely needed - shutting the plugin down removes them all. */
	protected final void removeOverlay(Overlay overlay) {
		if (overlay != null) {
			this.overlays.remove(overlay);
			overlay.owner = null;
		}
	}

	/**
	 * Adds a list to this plugin's config page - the swaps it has, the items it is hiding. Call
	 * it from startUp(); lists added there go when the plugin stops, like overlays.
	 */
	protected final void addConfigList(String title, ConfigList list) {
		if (title != null && list != null) {
			this.configLists.add(new NamedList(title, list));
		}
	}

	/**
	 * Gives this plugin a page on the sidebar's rail, with an icon to reach it by.
	 *
	 * The icon is NAMED rather than supplied: a plugin picks from the set the client draws (see
	 * jagex2.client.plugin.ui.Icons) instead of shipping an image. That keeps a jar from the hub
	 * out of the business of loading and scaling pictures into the client's UI, and keeps the
	 * rail looking like one set of icons rather than a row of everybody's artwork. An unknown
	 * name falls back to a plain one rather than to nothing.
	 *
	 * Call it from startUp(); panels go when the plugin stops, like overlays and config lists.
	 *
	 * <pre>
	 * this.addPanel("Session xp", "chart", new ConfigList() {
	 *     public int size()            { return skills.size(); }
	 *     public String label(int i)   { return name(i); }
	 *     public String value(int i)   { return gained(i) + " xp"; }
	 *     public int progress(int i)   { return percentToNextLevel(i); }
	 * });
	 * </pre>
	 */
	protected final void addPanel(String title, String icon, ConfigList list) {
		if (title != null && list != null) {
			this.panels.add(new Panel(title, icon == null ? "" : icon, list));
		}
	}

	final List<Overlay> getOverlays() {
		return this.overlays;
	}

	final List<Panel> getPanels() {
		return this.panels;
	}

	final List<NamedList> getConfigLists() {
		return this.configLists;
	}

	final void clearOverlays() {
		for (int i = 0; i < this.overlays.size(); i++) {
			this.overlays.get(i).owner = null;
		}
		this.overlays.clear();
		this.configLists.clear();
		this.panels.clear();
	}

	public final boolean isRunning() {
		return this.running;
	}

	final void setRunning(boolean running) {
		this.running = running;
	}
}
