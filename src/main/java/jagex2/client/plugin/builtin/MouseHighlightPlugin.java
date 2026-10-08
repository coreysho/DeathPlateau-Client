package jagex2.client.plugin.builtin;

import jagex2.client.plugin.ConfigItem;
import jagex2.client.plugin.Overlay;
import jagex2.client.plugin.OverlayGraphics;
import jagex2.client.plugin.Plugin;
import jagex2.client.plugin.PluginConfig;
import jagex2.client.plugin.PluginDescriptor;

/**
 * What a left click would do, written next to the cursor.
 *
 * The option is already on the screen - it is the text in the top-left - but reading it means
 * looking away from whatever you are about to click, which at a bank of twenty identical crates
 * is the whole problem. Putting it under the cursor is RuneLite's answer and it is a good one.
 *
 * NOT DRAWN FOR "Walk here". Everything that is not something is walk-here, so showing it would
 * mean a label following the cursor across every empty tile, which is noise with a 100% duty
 * cycle. The entry a left click performs is the LAST one in the menu, not the first - the menu
 * is built bottom-up.
 */
@PluginDescriptor(
	name = "Mouse highlight",
	description = "Shows what a left click would do, next to the cursor",
	key = "mouse-highlight",
	apiLevel = 3
)
public final class MouseHighlightPlugin extends Plugin {

	/** Offset from the cursor, so the text is beside the pointer rather than under it. */
	private static final int OFFSET_X = 14;
	private static final int OFFSET_Y = 18;

	private static final int BACKDROP = 0x000000;
	private static final int BACKDROP_ALPHA = 150;
	private static final int BORDER = 0x5A5A5A;

	@ConfigItem(keyName = "colour", name = "Text colour",
		description = "Click the swatch to pick one", colour = true)
	public String colour = "FFFF00";

	@ConfigItem(keyName = "boxed", name = "Draw a box behind it",
		description = "Easier to read over a bright scene")
	public boolean boxed = true;

	protected void startUp() {
		this.addOverlay(new Overlay() {

			public void render(OverlayGraphics g) {
				MouseHighlightPlugin.this.draw(g);
			}
		});
	}

	private void draw(OverlayGraphics g) {
		if (!this.ctx.isLoggedIn()) {
			return;
		}
		int x = this.ctx.getMouseX();
		int y = this.ctx.getMouseY();
		if (x < 0 || y < 0) {
			return;
		}
		String text = this.label();
		if (text.length() == 0) {
			return;
		}
		g.setFont(OverlayGraphics.FONT_NORMAL);
		int wide = g.textWidth(text);
		int tall = g.lineHeight();
		// Kept inside the viewport, or the label runs off the edge exactly when the cursor is
		// near something at the edge of the screen - which is most of the time in fixed mode.
		int left = x + OFFSET_X;
		if (left + wide + 4 > g.width()) {
			left = x - OFFSET_X - wide;
		}
		int baseline = y + OFFSET_Y;
		if (baseline + 2 > g.height()) {
			baseline = y - OFFSET_Y + tall;
		}
		if (this.boxed) {
			g.fillAlpha(left - 2, baseline - tall, wide + 4, tall + 3, BACKDROP, BACKDROP_ALPHA);
			g.box(left - 2, baseline - tall, wide + 4, tall + 3, BORDER);
		}
		g.text(left, baseline, text, parseColour(this.colour));
	}

	/**
	 * The option a left click would perform, stripped of its colour tags, or "" for nothing
	 * worth saying.
	 */
	String label() {
		int index = this.ctx.getLeftClickIndex();
		if (index < 0 || this.ctx.isWalkHere(index)) {
			return "";
		}
		return strip(this.ctx.getMenuOption(index));
	}

	/**
	 * Drops the client's colour tags from a menu option.
	 *
	 * The game writes targets as "Chop down <col=00ffff>Tree", and the font this draws with
	 * renders the tag rather than acting on it - so without this the label reads literally
	 * "Chop down <col=00ffff>Tree".
	 */
	static String strip(String option) {
		if (option == null || option.length() == 0) {
			return "";
		}
		int tag = option.indexOf('<');
		if (tag < 0) {
			return option;
		}
		StringBuilder out = new StringBuilder(option.length());
		int at = 0;
		while (at < option.length()) {
			int open = option.indexOf('<', at);
			if (open < 0) {
				out.append(option, at, option.length());
				break;
			}
			out.append(option, at, open);
			int close = option.indexOf('>', open);
			if (close < 0) {
				// An unclosed tag is not a tag. Keeping the rest is better than losing the name.
				out.append(option, open, option.length());
				break;
			}
			at = close + 1;
		}
		return out.toString();
	}

	/**
	 * A hex colour from the settings, or yellow for anything that is not one.
	 *
	 * This is a text box a player types into, so it will see "yellow", "#FFFF00" and "" as often
	 * as it sees six hex digits. A default beats refusing to draw.
	 */
	static int parseColour(String text) {
		// PluginConfig's, not a second copy: the panel's swatch has to agree with what gets drawn,
		// and two implementations of "is this six characters of hex" would eventually not.
		return PluginConfig.parseColour(text);
	}
}
