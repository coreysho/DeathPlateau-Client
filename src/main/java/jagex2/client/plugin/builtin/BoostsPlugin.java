package jagex2.client.plugin.builtin;

import java.util.ArrayList;
import java.util.List;

import jagex2.client.plugin.ConfigItem;
import jagex2.client.plugin.Overlay;
import jagex2.client.plugin.OverlayGraphics;
import jagex2.client.plugin.Plugin;
import jagex2.client.plugin.PluginConfig;
import jagex2.client.plugin.Subscribe;
import jagex2.client.plugin.event.GameTick;
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
	apiLevel = 7
)
public final class BoostsPlugin extends Plugin {

	/** Draining red, boosting green, and the heading. Matched to the xp drops panel's palette. */
	static final String DEFAULT_DRAINED = "FF4444";
	static final String DEFAULT_BOOSTED = "44DD44";
	private static final int HEADING = 0xFFFFFF;

	/** A skill the cache has no real name for. See {@link #isRealSkill(int)}. */
	private static final char UNUSED_MARKER = '-';

	@ConfigItem(keyName = "always", name = "Show when nothing is boosted",
		description = "Keep the panel up with a - in it, instead of hiding it")
	public boolean always = false;

	@ConfigItem(keyName = "relative", name = "Show the difference, not the levels",
		description = "\"Attack +4\" instead of \"Attack 64/60\"")
	public boolean relative = false;

	@ConfigItem(keyName = "boostedColour", name = "Boosted colour", colour = true)
	public String boostedColour = DEFAULT_BOOSTED;

	@ConfigItem(keyName = "drainedColour", name = "Drained colour", colour = true)
	public String drainedColour = DEFAULT_DRAINED;

	// Inline, because an annotation's array value cannot be a reference to a constant array.
	@ConfigItem(keyName = "font", name = "Text size", choices = {
		OverlayGraphics.FONT_CHOICE_SMALL,
		OverlayGraphics.FONT_CHOICE_NORMAL,
		OverlayGraphics.FONT_CHOICE_BOLD
	})
	public String font = OverlayGraphics.FONT_CHOICE_SMALL;

	@ConfigItem(keyName = "showTitle", name = "Show the \"Boosts\" heading",
		description = "Off is a row shorter, which matters for a panel this small")
	public boolean showTitle = true;

	@ConfigItem(keyName = "skills", name = "Only these skills, comma separated",
		description = "Part of a name is enough. Blank is all of them")
	public String skills = "";

	/**
	 * Notified when a boost wears off.
	 *
	 * HITPOINTS AND PRAYER ARE NEVER NOTIFIED, whatever the filter says - see
	 * {@link #isVital(String)}. Everything else is a potion wearing off, which is about what you
	 * are doing; those two are how close to death you are, and this server does not put that in
	 * front of a player.
	 */
	@ConfigItem(keyName = "notifyExpired", name = "Notify when a boost wears off")
	public boolean notifyExpired = false;

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
		g.setFont(OverlayGraphics.fontFor(this.font));
		// Drawn a line at a time rather than through g.panel, because each line wants its own
		// colour - a drain and a boost in the same panel should not read alike.
		int padding = 4;
		int lineHeight = g.lineHeight() + 2;
		int width = this.showTitle ? g.textWidth(TITLE) : 0;
		for (int i = 0; i < lines.size(); i++) {
			int w = g.textWidth(lines.get(i).text);
			if (w > width) {
				width = w;
			}
		}
		width += padding * 2;
		int rows = lines.size() + (this.showTitle ? 1 : 0);
		int height = padding * 2 + lineHeight * rows;

		g.fillAlpha(X, Y, width, height, 0x000000, 160);
		g.box(X, Y, width, height, 0x5A5A5A);

		int baseline = Y + padding + g.lineHeight();
		if (this.showTitle) {
			g.textFlat(X + padding, baseline, TITLE, HEADING);
			baseline += lineHeight;
		}
		for (int i = 0; i < lines.size(); i++) {
			g.text(X + padding, baseline, lines.get(i).text, lines.get(i).colour);
			baseline += lineHeight;
		}
	}

	private static final String TITLE = "Boosts";

	/** One line per boosted or drained skill, in skill order, or empty for nothing to say. */
	List<Line> lines() {
		List<Line> lines = new ArrayList<Line>();
		int boosted = PluginConfig.parseColour(this.boostedColour);
		int drained = PluginConfig.parseColour(this.drainedColour);
		for (int skill = 0; skill < this.ctx.getSkillCount(); skill++) {
			if (!this.isRealSkill(skill)
					|| !SkillFilter.allows(this.ctx.getSkillName(skill), this.skills)) {
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
			lines.add(new Line(text, delta > 0 ? boosted : drained));
		}
		if (lines.isEmpty() && this.always) {
			lines.add(new Line("-", HEADING));
		}
		return lines;
	}

	/**
	 * Which skills were boosted at the last tick, so a boost wearing off can be noticed.
	 *
	 * BOOSTED, NOT DRAINED. A drain wearing off is a stat coming back, which nobody needs telling
	 * about; a boost wearing off is a potion to drink. The two are the same arithmetic with
	 * opposite signs and only one of them is news.
	 */
	private String wasBoosted = "";

	/** False until a tick has been seen, so turning this on does not report the current state. */
	private boolean scanned;

	/**
	 * Notices a boost that has gone, once per skill.
	 *
	 * The same shape as the Npc indicators appearance notice, and for the same reasons: a flag
	 * rather than an empty string for "there was no last tick", because "nothing was boosted a
	 * moment ago" and "this is the first look" are different answers and conflating them loses
	 * the first expiry after a quiet spell.
	 */
	@Subscribe
	public void onGameTick(GameTick event) {
		if (!this.notifyExpired || !this.ctx.isLoggedIn()) {
			this.wasBoosted = "";
			this.scanned = false;
			return;
		}
		StringBuilder now = new StringBuilder();
		for (int skill = 0; skill < this.ctx.getSkillCount(); skill++) {
			String name = this.ctx.getSkillName(skill);
			if (!this.isRealSkill(skill) || isVital(name)
					|| !SkillFilter.allows(name, this.skills)) {
				continue;
			}
			String key = "," + name.toLowerCase() + ",";
			if (this.ctx.getSkillLevel(skill) > this.ctx.getBaseLevel(skill)) {
				now.append(key);
			} else if (this.scanned && this.wasBoosted.indexOf(key) >= 0) {
				this.ctx.notify("Boosts", "Your " + name(name) + " boost has worn off.");
			}
		}
		this.wasBoosted = now.toString();
		this.scanned = true;
	}

	/**
	 * Whether a skill is one this server does not report on.
	 *
	 * Hitpoints and prayer, by name rather than by index: the indices are a cache detail and
	 * reading "3" in a condition tells nobody why. A boost to either wearing off is a vital
	 * going down, which is the thing the Status bars plugin was taken out for - and a
	 * notification is only a quieter way of saying it.
	 */
	static boolean isVital(String skillName) {
		if (skillName == null) {
			return false;
		}
		String lower = skillName.toLowerCase();
		return lower.equals("hitpoints") || lower.equals("prayer");
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
