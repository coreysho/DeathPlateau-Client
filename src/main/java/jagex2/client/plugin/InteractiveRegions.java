package jagex2.client.plugin;

import java.util.ArrayList;
import java.util.List;

import jagex2.client.DevLog;

/**
 * The rectangles overlays claimed this frame, and what to run when one is clicked or scrolled.
 *
 * WHY A FRAME AT A TIME. An overlay already knows where it drew a thing; it says so while it
 * draws, and the list is thrown away before the next frame. Nothing has to be kept in sync,
 * nothing goes stale, and an overlay that stops drawing something stops it being clickable in
 * the same breath. The client's own ground item controls worked exactly this way before any of
 * this was a plugin.
 *
 * LAST DRAWN WINS where two overlap, which is what a player sees: the thing on top is the thing
 * they are pointing at. So the search runs backwards.
 *
 * A region whose action throws is dropped, not retried: whatever is wrong with it will be just
 * as wrong on the next click, and a handler that throws on every click of a button nobody can
 * stop pressing is worse than one that stops working.
 */
public final class InteractiveRegions {

	/** One rectangle. Either click or scroll is set, never both. */
	private static final class Region {

		int x;
		int y;
		int width;
		int height;
		Runnable click;
		OverlayGraphics.Scrolled scroll;
		Plugin owner;

		boolean contains(int px, int py) {
			return px >= this.x && px < this.x + this.width
				&& py >= this.y && py < this.y + this.height;
		}
	}

	private final List<Region> regions = new ArrayList<Region>();

	/** How many are in use; the list itself is kept and reused so a frame allocates nothing. */
	private int used;

	void clear() {
		this.used = 0;
	}

	void add(int x, int y, int width, int height, Runnable click, OverlayGraphics.Scrolled scroll,
		Plugin owner) {
		if (width <= 0 || height <= 0) {
			return;
		}
		Region region;
		if (this.used < this.regions.size()) {
			region = this.regions.get(this.used);
		} else {
			region = new Region();
			this.regions.add(region);
		}
		this.used++;
		region.x = x;
		region.y = y;
		region.width = width;
		region.height = height;
		region.click = click;
		region.scroll = scroll;
		region.owner = owner;
	}

	/** Runs the topmost click region under the point. Returns true when one was there. */
	boolean click(int x, int y) {
		for (int i = this.used - 1; i >= 0; i--) {
			Region region = this.regions.get(i);
			if (region.click == null || !region.contains(x, y)) {
				continue;
			}
			try {
				region.click.run();
			} catch (Throwable error) {
				DevLog.log("PLUGIN", "an overlay's click handler threw: " + error);
			}
			return true;
		}
		return false;
	}

	/** The same for the wheel. Returns true when a region took the turn. */
	boolean scroll(int x, int y, int delta) {
		for (int i = this.used - 1; i >= 0; i--) {
			Region region = this.regions.get(i);
			if (region.scroll == null || !region.contains(x, y)) {
				continue;
			}
			try {
				region.scroll.onScroll(delta);
			} catch (Throwable error) {
				DevLog.log("PLUGIN", "an overlay's scroll handler threw: " + error);
			}
			return true;
		}
		return false;
	}

	/** Drops everything a plugin claimed, for when it is turned off mid-frame. */
	void forget(Plugin owner) {
		for (int i = this.used - 1; i >= 0; i--) {
			if (this.regions.get(i).owner == owner) {
				this.regions.get(i).click = null;
				this.regions.get(i).scroll = null;
			}
		}
	}
}
