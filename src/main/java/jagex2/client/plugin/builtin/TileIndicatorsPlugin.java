package jagex2.client.plugin.builtin;

import jagex2.client.plugin.ConfigItem;
import jagex2.client.plugin.Overlay;
import jagex2.client.plugin.OverlayGraphics;
import jagex2.client.plugin.Plugin;
import jagex2.client.plugin.PluginContext;
import jagex2.client.plugin.PluginDescriptor;

/**
 * Outlines the tile under the cursor, and the one you are standing on.
 *
 * The hovered tile is the useful half: at a distance, with the camera low, "which square am I
 * about to click" is genuinely ambiguous, and a 2006 client gives you no feedback at all until
 * the click has already happened.
 *
 * A SCENE-LAYER OVERLAY, so it is drawn before the game's own interfaces and - unlike the screen
 * layer - cannot be dragged. There is nothing to drag: it is already attached to a tile, and an
 * offset would just point it at the wrong one.
 *
 * The outline is drawn from the four projected corners rather than as a rectangle. A tile seen in
 * perspective is not a rectangle, and the version of this that draws one looks fine under the
 * player and wrong everywhere else.
 */
@PluginDescriptor(
	name = "Tile indicators",
	description = "Outlines the tile under the cursor and the one you are on",
	key = "tile-indicators",
	apiLevel = 3
)
public final class TileIndicatorsPlugin extends Plugin {

	/** Tiles are 128 scene units across. */
	private static final int TILE = 128;

	/** The longest a line may be, in pixels. Comfortably past the diagonal of any viewport. */
	static final int MAX_STEPS = 4096;

	@ConfigItem(keyName = "hover", name = "Outline the tile under the cursor")
	public boolean hover = true;

	@ConfigItem(keyName = "current", name = "Outline the tile you are standing on")
	public boolean current = false;

	@ConfigItem(keyName = "hoverColour", name = "Cursor tile colour",
		description = "Click the swatch to pick one", colour = true)
	public String hoverColour = "FFFFFF";

	@ConfigItem(keyName = "currentColour", name = "Your tile colour", colour = true)
	public String currentColour = "00FFFF";

	protected void startUp() {
		this.addOverlay(new Overlay() {

			public int layer() {
				return Overlay.LAYER_SCENE;
			}

			public void render(OverlayGraphics g) {
				TileIndicatorsPlugin.this.draw(g);
			}
		});
	}

	private void draw(OverlayGraphics g) {
		if (!this.ctx.isLoggedIn()) {
			return;
		}
		if (this.current) {
			this.outline(g, this.ctx.worldToSceneX(this.ctx.getWorldX()),
				this.ctx.worldToSceneZ(this.ctx.getWorldZ()),
				MouseHighlightPlugin.parseColour(this.currentColour));
		}
		// Read every frame on purpose: reading is what keeps the client answering the question.
		// See PluginContext.getHoverTileX.
		int tileX = this.ctx.getHoverTileX();
		int tileZ = this.ctx.getHoverTileZ();
		if (this.hover && tileX >= 0 && tileZ >= 0) {
			this.outline(g, tileX, tileZ, MouseHighlightPlugin.parseColour(this.hoverColour));
		}
	}

	/**
	 * One tile, as a four-sided outline round its projected corners.
	 *
	 * Every corner has to project for anything to be drawn. A tile half behind the camera gives
	 * one or two corners that project to nonsense, and joining those up draws a line across the
	 * whole screen - which is what a partly-off-screen tile looks like if you skip this check.
	 */
	void outline(OverlayGraphics g, int sceneTileX, int sceneTileZ, int colour) {
		outlineTile(this.ctx, g, sceneTileX, sceneTileZ, colour);
	}

	/**
	 * The same outline, as a static anyone can call.
	 *
	 * Npc indicators wants a tile ring too, and a second copy of the corner walk below is a
	 * second place for the "joins up into a Z" bug to come back. Shared the way
	 * MouseHighlightPlugin.parseColour already is.
	 */
	static void outlineTile(PluginContext ctx, OverlayGraphics g, int sceneTileX, int sceneTileZ,
			int colour) {
		outlineTile(ctx, g, sceneTileX, sceneTileZ, colour, 1);
	}

	/**
	 * The same outline, drawn thicker.
	 *
	 * WIDTH IS CONCENTRIC RINGS, not a thick line routine. A tile seen in perspective is a
	 * quadrilateral whose edges run at four different angles, and "thicken that edge" means a
	 * different offset for each - which is a polygon-offset problem, not a drawing one. Rings
	 * pulled in toward the tile's own centre are the honest cheap version: at the sizes anyone
	 * would ask for, two or three pixels, it reads as a thicker border, and it cannot leave the
	 * tile the way an outward offset could.
	 */
	static void outlineTile(PluginContext ctx, OverlayGraphics g, int sceneTileX, int sceneTileZ,
			int colour, int width) {
		if (!ctx.isInScene(sceneTileX, sceneTileZ)) {
			return;
		}
		int[] xs = new int[4];
		int[] ys = new int[4];
		int corner = 0;
		for (int dz = 0; dz <= 1; dz++) {
			// The second pair is walked backwards, so the four points come out in a ring rather
			// than a Z and the lines joining them are the tile's edges.
			for (int step = 0; step <= 1; step++) {
				int dx = dz == 0 ? step : 1 - step;
				if (!ctx.project((sceneTileX + dx) * TILE, (sceneTileZ + dz) * TILE, 0)) {
					return;
				}
				xs[corner] = ctx.getProjectedX();
				ys[corner] = ctx.getProjectedY();
				corner++;
			}
		}
		// The tile's centre in screen pixels, which each ring is pulled toward.
		int midX = (xs[0] + xs[1] + xs[2] + xs[3]) / 4;
		int midY = (ys[0] + ys[1] + ys[2] + ys[3]) / 4;
		for (int ring = 0; ring < (width < 1 ? 1 : width); ring++) {
			for (int i = 0; i < 4; i++) {
				int next = (i + 1) % 4;
				line(g, toward(xs[i], midX, ring), toward(ys[i], midY, ring),
					toward(xs[next], midX, ring), toward(ys[next], midY, ring), colour);
			}
		}
	}

	/**
	 * A corner pulled `by` pixels toward the centre, stopping at it.
	 *
	 * Clamped, because a distant tile is a few pixels across and a three-pixel inset would
	 * otherwise send its corners out the far side - which draws a bigger ring than the one it was
	 * meant to sit inside.
	 */
	static int toward(int from, int centre, int by) {
		if (from == centre || by <= 0) {
			return from;
		}
		int step = from < centre ? by : -by;
		int moved = from + step;
		return (step > 0) == (moved > centre) ? centre : moved;
	}

	/**
	 * A line between two points, drawn as a run of horizontal or vertical spans.
	 *
	 * OverlayGraphics has hline and vline and nothing diagonal, which is the right place to stop
	 * - a general line routine is Pix2D's business, not a facade's. Bresenham over one-pixel
	 * spans is a dozen lines here and keeps the drawing API as small as it was.
	 *
	 * Returns how many pixels it drew, which is how the cap can be checked at all. Timing it
	 * cannot: pixels outside the buffer are clipped away so cheaply that ten million of them
	 * finish in under a tenth of a second, and the lengths that are slow enough to notice take
	 * minutes - too slow to put in a test, and far too slow to put in a mutation run.
	 */
	static int line(OverlayGraphics g, int x0, int y0, int x1, int y1, int colour) {
		int dx = Math.abs(x1 - x0);
		int dy = -Math.abs(y1 - y0);
		int stepX = x0 < x1 ? 1 : -1;
		int stepY = y0 < y1 ? 1 : -1;
		int error = dx + dy;
		// ONE BOUND, NOT TWO. The loop ends on its own when it reaches the far end, so a step
		// count computed alongside it was a second way of saying the same thing - and a wrong
		// one would be invisible, because the other would still stop it. What is left is a hard
		// cap for the case the natural end never arrives: a projection that came back absurd.
		// Nothing honest is longer than the diagonal of a viewport.
		for (int i = 0; i < MAX_STEPS; i++) {
			g.fill(x0, y0, 1, 1, colour);
			if (x0 == x1 && y0 == y1) {
				return i + 1;
			}
			int twice = error * 2;
			if (twice >= dy) {
				error += dy;
				x0 += stepX;
			}
			if (twice <= dx) {
				error += dx;
				y0 += stepY;
			}
		}
		return MAX_STEPS;
	}
}
