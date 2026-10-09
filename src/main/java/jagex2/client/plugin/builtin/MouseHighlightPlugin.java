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
 * NOT DRAWN FOR "Walk here" by default. Everything that is not something is walk-here, so
 * showing it would mean a label following the cursor across every empty tile, which is noise with
 * a 100% duty cycle. It is a setting rather than a rule because a player learning the interface
 * may want it, but the default is off. The entry a left click performs is the LAST one in the
 * menu, not the first - the menu is built bottom-up.
 *
 * AND NOT WHILE A MENU IS OPEN, which is not a setting. The label says what a LEFT click would
 * do; with a right-click menu open in front of the player, that is a click they are not about to
 * make, and the label would be describing the wrong thing while sitting next to the list of right
 * ones.
 */
@PluginDescriptor(
	name = "Mouse highlight",
	description = "Shows what a left click would do, next to the cursor",
	key = "mouse-highlight",
	apiLevel = 7
)
public final class MouseHighlightPlugin extends Plugin {

	/** Offset from the cursor, so the text is beside the pointer rather than under it. */
	private static final int OFFSET_X = 14;
	private static final int OFFSET_Y = 18;

	/** What the box behind the text was before it was three settings. */
	static final String DEFAULT_BACKDROP = "000000";
	static final String DEFAULT_BORDER = "5A5A5A";
	static final int DEFAULT_BACKDROP_ALPHA = 150;

	static final int MIN_ALPHA = 0;
	static final int MAX_ALPHA = 255;

	@ConfigItem(keyName = "colour", name = "Text colour",
		description = "Click the swatch to pick one", colour = true)
	public String colour = "FFFF00";

	// Spelled out rather than choices = OverlayGraphics.FONT_CHOICES, which does not compile: an
	// annotation's array value must be an inline initialiser, not a reference to a constant
	// array. The individual constants are still the point - they are what keeps the drop-down's
	// entries and fontFor's branches from drifting apart - and FONT_CHOICES is what a test uses
	// to assert the two lists are the same length.
	@ConfigItem(keyName = "font", name = "Text size", choices = {
		OverlayGraphics.FONT_CHOICE_NORMAL,
		OverlayGraphics.FONT_CHOICE_BOLD,
		OverlayGraphics.FONT_CHOICE_SMALL
	})
	public String font = OverlayGraphics.FONT_CHOICE_NORMAL;

	@ConfigItem(keyName = "textOutline", name = "Outline the text",
		description = "Readable without a box behind it")
	public boolean textOutline = false;

	@ConfigItem(keyName = "boxed", name = "Draw a box behind it",
		description = "Easier to read over a bright scene")
	public boolean boxed = true;

	@ConfigItem(keyName = "backdropColour", name = "Box colour", colour = true)
	public String backdropColour = DEFAULT_BACKDROP;

	@ConfigItem(keyName = "backdropOpacity", name = "How solid the box is",
		description = "0 is invisible, 255 is opaque")
	public int backdropOpacity = DEFAULT_BACKDROP_ALPHA;

	@ConfigItem(keyName = "borderColour", name = "Box border colour", colour = true)
	public String borderColour = DEFAULT_BORDER;

	@ConfigItem(keyName = "showWalkHere", name = "Show \"Walk here\" too",
		description = "A label on every empty tile, which is most of them")
	public boolean showWalkHere = false;

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
		// A menu open in front of the player makes this label describe a click they are not
		// about to make. Not a setting: there is no reading of the screen where both belong.
		if (this.ctx.isMenuOpen()) {
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
		g.setFont(OverlayGraphics.fontFor(this.font));
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
			g.fillAlpha(left - 2, baseline - tall, wide + 4, tall + 3,
				PluginConfig.parseColour(this.backdropColour), alphaFor(this.backdropOpacity));
			g.box(left - 2, baseline - tall, wide + 4, tall + 3,
				PluginConfig.parseColour(this.borderColour));
		}
		int colour = PluginConfig.parseColour(this.colour);
		if (this.textOutline) {
			// textCentredOutlined is the only outlined draw the API has, so the left edge is
			// turned into a centre rather than a second entry point being added for it.
			g.textCentredOutlined(left + wide / 2, baseline, text, colour);
		} else {
			g.text(left, baseline, text, colour);
		}
	}

	/**
	 * The option a left click would perform, stripped of its colour tags, or "" for nothing
	 * worth saying.
	 */
	String label() {
		int index = this.ctx.getLeftClickIndex();
		if (index < 0 || (!this.showWalkHere && this.ctx.isWalkHere(index))) {
			return "";
		}
		return strip(this.ctx.getMenuOption(index));
	}

	/** How solid the box is. 0 is off, which is a legitimate way to lose it by hand. */
	static int alphaFor(int alpha) {
		if (alpha < MIN_ALPHA) {
			return MIN_ALPHA;
		}
		return alpha > MAX_ALPHA ? MAX_ALPHA : alpha;
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

}
