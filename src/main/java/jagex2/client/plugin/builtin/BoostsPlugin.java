package jagex2.client.plugin.builtin;

import java.util.ArrayList;
import java.util.List;

import jagex2.client.plugin.ConfigItem;
import jagex2.client.plugin.Overlay;
import jagex2.client.plugin.OverlayGraphics;
import jagex2.client.plugin.Plugin;
import jagex2.client.plugin.PluginDescriptor;

/**
 * Which stats are boosted or drained, and by how much.
 *
 * The question this answers is the one you ask mid-fight: a potion is wearing off and you cannot
 * tell, because the stats tab shows the boosted number and nothing says what it started from.
 * The client knows both - it keeps the drainable level and the level the experience alone gives -
 * so the panel is just the difference between two numbers it already has.
 *
 * NOTHING IS SHOWN WHEN NOTHING IS BOOSTED, by default. An overlay that is always there stops
 * being read; one that appears exactly when a potion is active is information. {@link #always}
 * is there for anyone who would rather have it pinned.
 */
@PluginDescriptor(
	name = "Boosts",
	description = "Which stats are boosted or drained, and by how much",
	key = "boosts",
	apiLevel = 1
)
public final class BoostsPlugin extends Plugin {

	/** Draining red, boosting green, and the heading. Matched to the xp drops panel's palette. */
	private static final int DRAINED = 0xFF4444;
	private static final int BOOSTED = 0x44DD44;
	private static final int HEADING = 0xFFFFFF;

	/** A skill the cache has no real name for. See {@link #isRealSkill(int)}. */
	private static final char UNUSED_MARKER = '-';

	@ConfigItem(keyName = "always", name = "Show when nothing is boosted",
		description = "Keep the panel up with a - in it, instead of hiding it")
	public boolean always = false;

	@ConfigItem(keyName = "relative", name = "Show the difference, not the levels",
		description = "\"Attack +4\" instead of \"Attack 64/60\"")
	public boolean relative = false;

	/**
	 * Where the panel starts, before the player drags it.
	 *
	 * NOT A SETTING. It was one, for the few hours between this plugin shipping and overlays
	 * becoming draggable, and two ways to position the same thing is one too many: the final
	 * place would be this plus the drag offset, which is a sum nobody can reason about from
	 * either number alone. Hold Alt and move it.
	 */
	private static final int X = 6;
	private static final int Y = 6;

	/**
	 * One line of the panel: the text and the colour it is drawn in.
	 *
	 * Text and colour together, in one pass, because the alternative - build the strings, then
	 * work out the colours - means deciding twice which skills are boosted. Change one pass and
	 * not the other and the panel colours the wrong rows, which is a bug nobody reads as a bug.
	 */
	static final class Line {

		final String text;
		final int colour;

		Line(String text, int colour) {
			this.text = text;
			this.colour = colour;
		}
	}

	protected void startUp() {
		this.addOverlay(new Overlay() {

			public void render(OverlayGraphics g) {
				BoostsPlugin.this.draw(g);
			}
		});
	}

	private void draw(OverlayGraphics g) {
		if (!this.ctx.isLoggedIn()) {
			return;
		}
		List<Line> lines = this.lines();
		if (lines.isEmpty()) {
			return;
		}
		g.setFont(OverlayGraphics.FONT_SMALL);
		// Drawn a line at a time rather than through g.panel, because each line wants its own
		// colour - a drain and a boost in the same panel should not read alike.
		int padding = 4;
		int lineHeight = g.lineHeight() + 2;
		int width = g.textWidth(TITLE);
		for (int i = 0; i < lines.size(); i++) {
			int w = g.textWidth(lines.get(i).text);
			if (w > width) {
				width = w;
			}
		}
		width += padding * 2;
		int height = padding * 2 + lineHeight * (lines.size() + 1);

		g.fillAlpha(X, Y, width, height, 0x000000, 160);
		g.box(X, Y, width, height, 0x5A5A5A);

		int baseline = Y + padding + g.lineHeight();
		g.textFlat(X + padding, baseline, TITLE, HEADING);
		baseline += lineHeight;
		for (int i = 0; i < lines.size(); i++) {
			g.text(X + padding, baseline, lines.get(i).text, lines.get(i).colour);
			baseline += lineHeight;
		}
	}

	private static final String TITLE = "Boosts";

	/** One line per boosted or drained skill, in skill order, or empty for nothing to say. */
	List<Line> lines() {
		List<Line> lines = new ArrayList<Line>();
		for (int skill = 0; skill < this.ctx.getSkillCount(); skill++) {
			if (!this.isRealSkill(skill)) {
				continue;
			}
			int now = this.ctx.getSkillLevel(skill);
			int base = this.ctx.getBaseLevel(skill);
			if (now == base) {
				continue;
			}
			int delta = now - base;
			String name = name(this.ctx.getSkillName(skill));
			// "+4" for a boost, "-4" for a drain: the sign is already on a negative number, and
			// a boost without one reads as a level rather than as a difference.
			String text = this.relative
				? name + (delta > 0 ? " +" + delta : " " + delta)
				: name + " " + now + "/" + base;
			lines.add(new Line(text, delta > 0 ? BOOSTED : DRAINED));
		}
		if (lines.isEmpty() && this.always) {
			lines.add(new Line("-", HEADING));
		}
		return lines;
	}

	/**
	 * Whether a skill index is a skill rather than one of the cache's placeholder slots.
	 *
	 * getSkillCount() counts every slot the client has, and the last two are Jagex's unused
	 * placeholders - the client names them "-unused-". Asking the client which slots are real
	 * would be a better question for PluginContext to answer, but it is not one it answers yet,
	 * and the name it does give is enough to tell: a real skill is not called "-anything".
	 */
	private boolean isRealSkill(int skill) {
		String name = this.ctx.getSkillName(skill);
		return name.length() > 0 && name.charAt(0) != UNUSED_MARKER;
	}

	/** "attack" as "Attack". The client stores skill names lower case. */
	static String name(String skill) {
		if (skill == null || skill.length() == 0) {
			return "";
		}
		return Character.toUpperCase(skill.charAt(0)) + skill.substring(1);
	}
}
