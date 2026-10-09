package jagex2.client.plugin.builtin;

import jagex2.client.plugin.ConfigItem;
import jagex2.client.plugin.Overlay;
import jagex2.client.plugin.OverlayGraphics;
import jagex2.client.plugin.Plugin;
import jagex2.client.plugin.PluginContext;
import jagex2.client.plugin.PluginConfig;
import jagex2.client.plugin.PluginDescriptor;

/**
 * Outlines the tile under the cursor, the one you are standing on, and the one the server has
 * you on.
 *
 * THE LAST TWO ARE NOT THE SAME TILE WHILE YOU MOVE. "Your tile" is the rendered one, which the
 * client interpolates between tiles so it tracks your feet; the TRUE tile is what the server
 * sent, which during a step is already the tile you are walking onto. Standing still they are
 * the same square and the two outlines sit on top of each other. Tick-perfect movement is read
 * off the true one - it is where the server will act from - which is why RuneLite offers both
 * and why they want different colours.
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
	apiLevel = 8
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

	/** A border is at least a pixel, and five is already thicker than a distant tile. */
	static final int MIN_BORDER = 1;
	static final int MAX_BORDER = 5;

	/** Fully clear to fully solid, as a player thinks of it: 0 to 255. */
	static final int MIN_FILL = 0;
	static final int MAX_FILL = 255;
	static final int DEFAULT_FILL = 70;

	@ConfigItem(keyName = "borderWidth", name = "Outline thickness, in pixels")
	public int borderWidth = MIN_BORDER;

	@ConfigItem(keyName = "hoverFill", name = "Fill the cursor's tile",
		description = "As well as outlining it")
	public boolean hoverFill = false;

	@ConfigItem(keyName = "currentFill", name = "Fill your own tile")
	public boolean currentFill = false;

	@ConfigItem(keyName = "trueTile", name = "Outline the tile the server has you on",
		description = "Ahead of your own while you walk; the same square standing still")
	public boolean trueTile = false;

	@ConfigItem(keyName = "trueTileColour", name = "Server tile colour", colour = true)
	public String trueTileColour = "FFFF00";

	@ConfigItem(keyName = "trueTileFill", name = "Fill the server's tile")
	public boolean trueTileFill = false;

	@ConfigItem(keyName = "fillOpacity", name = "How solid a fill is",
		description = "0 is invisible, 255 is opaque")
	public int fillOpacity = DEFAULT_FILL;

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
		int width = borderFor(this.borderWidth);
		int alpha = fillFor(this.fillOpacity);
		// DRAWN BEFORE "your tile", so that where the two coincide - which is whenever you are
		// standing still - the one that tracks your feet is the one on top. The alternative
		// leaves a stationary player looking at the server's colour and wondering why their own
		// setting does nothing.
		if (this.trueTile) {
			int tx = this.ctx.worldToSceneX(this.ctx.getTrueTileX());
			int tz = this.ctx.worldToSceneZ(this.ctx.getTrueTileZ());
			int colour = PluginConfig.parseColour(this.trueTileColour);
			if (this.trueTileFill) {
				fillTile(this.ctx, g, tx, tz, colour, alpha);
			}
			outlineTile(this.ctx, g, tx, tz, colour, width);
		}
		if (this.current) {
			int tx = this.ctx.worldToSceneX(this.ctx.getWorldX());
			int tz = this.ctx.worldToSceneZ(this.ctx.getWorldZ());
			int colour = PluginConfig.parseColour(this.currentColour);
			// Filled first, so the outline sits on top of its own fill rather than under it.
			if (this.currentFill) {
				fillTile(this.ctx, g, tx, tz, colour, alpha);
			}
			outlineTile(this.ctx, g, tx, tz, colour, width);
		}
		// Read every frame on purpose: reading is what keeps the client answering the question.
		// See PluginContext.getHoverTileX.
		int tileX = this.ctx.getHoverTileX();
		int tileZ = this.ctx.getHoverTileZ();
		if (this.hover && tileX >= 0 && tileZ >= 0) {
			int colour = PluginConfig.parseColour(this.hoverColour);
			if (this.hoverFill) {
				fillTile(this.ctx, g, tileX, tileZ, colour, alpha);
			}
			outlineTile(this.ctx, g, tileX, tileZ, colour, width);
		}
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
		if (!corners(ctx, sceneTileX, sceneTileZ, xs, ys)) {
			return;
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
	 * One tile, filled, under its outline.
	 *
	 * SCANLINES, because a tile in perspective is not a rectangle. OverlayGraphics fills rects,
	 * so the honest version of a filled quadrilateral is one one-pixel-high rect per screen row
	 * between the quad's edges - which is what a polygon fill is, and is cheap at the size of a
	 * tile. Drawn through fillAlpha so a fill can be seen through, which is the only way a fill
	 * on the tile you are standing on does not hide you.
	 *
	 * Every corner has to project, for the same reason the outline needs it: a tile half behind
	 * the camera gives corners that project to nonsense, and a span between those is a bar across
	 * the screen.
	 */
	static void fillTile(PluginContext ctx, OverlayGraphics g, int sceneTileX, int sceneTileZ,
			int colour, int alpha) {
		if (alpha <= MIN_FILL || !ctx.isInScene(sceneTileX, sceneTileZ)) {
			return;
		}
		int[] xs = new int[4];
		int[] ys = new int[4];
		if (!corners(ctx, sceneTileX, sceneTileZ, xs, ys)) {
			return;
		}
		int rows = rowSpan(ys);
		if (rows == 0) {
			return;
		}
		int top = ys[0];
		for (int i = 1; i < 4; i++) {
			top = Math.min(top, ys[i]);
		}
		for (int y = top; y < top + rows; y++) {
			int left = spanLeft(xs, ys, y);
			int right = spanRight(xs, ys, y);
			if (left <= right) {
				g.fillAlpha(left, y, right - left + 1, 1, colour, alpha);
			}
		}
	}

	/**
	 * The four projected corners of a tile, in a ring. False if any of them cannot be placed.
	 *
	 * Shared by the outline and the fill so the two cannot disagree about where a tile is - they
	 * are drawn one on top of the other, and a half-pixel of disagreement shows.
	 */
	static boolean corners(PluginContext ctx, int sceneTileX, int sceneTileZ, int[] xs, int[] ys) {
		int corner = 0;
		for (int dz = 0; dz <= 1; dz++) {
			// The second pair is walked backwards, so the four points come out in a ring rather
			// than a Z and the lines joining them are the tile's edges.
			for (int step = 0; step <= 1; step++) {
				int dx = dz == 0 ? step : 1 - step;
				if (!ctx.project((sceneTileX + dx) * TILE, (sceneTileZ + dz) * TILE, 0)) {
					return false;
				}
				xs[corner] = ctx.getProjectedX();
				ys[corner] = ctx.getProjectedY();
				corner++;
			}
		}
		return true;
	}

	/**
	 * Where the quad's left edge is on this screen row, and where its right edge is.
	 *
	 * PURE, so the arithmetic that decides a fill's shape is testable without a scene - which
	 * matters more here than anywhere else in this plugin, because a wrong span is a bar of
	 * colour across the screen rather than a slightly wrong tile.
	 *
	 * Each of the four edges is checked for crossing the row, and the extremes of those crossings
	 * are the span. An edge that lies exactly along the row contributes both its ends, which is
	 * what fills the top and bottom rows of a tile seen side-on. A row the quad does not reach
	 * gives left > right, which the caller reads as "draw nothing" rather than as a span.
	 */
	static int spanLeft(int[] xs, int[] ys, int y) {
		return span(xs, ys, y, true);
	}

	static int spanRight(int[] xs, int[] ys, int y) {
		return span(xs, ys, y, false);
	}

	private static int span(int[] xs, int[] ys, int y, boolean wantLeft) {
		int best = wantLeft ? Integer.MAX_VALUE : Integer.MIN_VALUE;
		for (int i = 0; i < 4; i++) {
			int next = (i + 1) % 4;
			int y0 = ys[i];
			int y1 = ys[next];
			int x0 = xs[i];
			int x1 = xs[next];
			if (y0 == y1) {
				if (y0 != y) {
					continue;
				}
				// Horizontal edge on this row: both ends count, which is what gives the top and
				// bottom rows their width instead of a single pixel.
				best = wantLeft ? Math.min(best, Math.min(x0, x1)) : Math.max(best, Math.max(x0, x1));
				continue;
			}
			if (y < Math.min(y0, y1) || y > Math.max(y0, y1)) {
				continue;
			}
			// Linear along the edge. Integer, because the result is a pixel either way and a
			// float here would only move where the rounding happens.
			int at = x0 + (y - y0) * (x1 - x0) / (y1 - y0);
			best = wantLeft ? Math.min(best, at) : Math.max(best, at);
		}
		// Nothing crossed: left stays at MAX and right at MIN, so left > right and the caller
		// draws nothing. Returning them untouched says that without a second return value.
		return best;
	}

	/**
	 * How many screen rows a quad covers, or 0 for one that is absurd.
	 *
	 * PURE, AND SEPARATE, so the cap is testable at all. The bound used to be a line inside
	 * fillTile and the audit found it deletable with every check green: a projection that comes
	 * back absurd is the only thing it guards against, and no test can hand fillTile one without
	 * a fake scene. Pulled out, it takes four numbers and can be given the absurd case directly -
	 * which is exactly how line()'s own cap is checked a few lines below.
	 *
	 * A tile never covers more than a viewport, and MAX_STEPS is already past its diagonal.
	 */
	static int rowSpan(int[] ys) {
		int top = ys[0];
		int bottom = ys[0];
		for (int i = 1; i < 4; i++) {
			top = Math.min(top, ys[i]);
			bottom = Math.max(bottom, ys[i]);
		}
		int rows = bottom - top + 1;
		return rows > MAX_STEPS || rows < 1 ? 0 : rows;
	}

	/** The outline thickness a player asked for, within what a tile can carry. */
	static int borderFor(int width) {
		if (width < MIN_BORDER) {
			return MIN_BORDER;
		}
		return width > MAX_BORDER ? MAX_BORDER : width;
	}

	/** How solid a fill is. 0 is off, which is a legitimate way to turn one off by hand. */
	static int fillFor(int alpha) {
		if (alpha < MIN_FILL) {
			return MIN_FILL;
		}
		return alpha > MAX_FILL ? MAX_FILL : alpha;
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
