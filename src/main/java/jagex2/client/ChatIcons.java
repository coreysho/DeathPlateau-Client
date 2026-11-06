package jagex2.client;

import jagex2.graphics.Pix8;
import jagex2.graphics.PixFont;

/**
 * Icons and shadowed text inside a chatbox line: the rank crowns, and the XP-mode badges that the
 * rare-drop broadcasts and ::yell put beside a player's name.
 *
 * AN ICON IS A FIVE-CHARACTER MARKER in the text, "@cr1@" to "@cr9@", naming imageModIcons[0..8]:
 *   @cr1@ silver crown (moderator)    @cr3@ Realism badge (1x)
 *   @cr2@ gold crown (administrator)  @cr4@ 5x badge
 *   @cr6@ purple crown (developer)    @cr5@ 10x badge
 *   @cr7@ red crown (owner)
 *   @cr8@ blue and gold crown (a second owner)
 *   @cr9@ bronze emblem (veteran - tested before launch; the only one that is not a crown)
 * The first two were already the markers public and private chat put in front of a sender's name,
 * so the numbering carries on from them rather than starting a second scheme. Every marker is the
 * shape of a colour tag - "@" + three characters + "@" - which is what makes this degrade well: an
 * older client's drawStringTag and stringWidTag both skip a tag they do not recognise, so a line
 * with a badge in it prints without the badge instead of printing "@cr4@".
 *
 * SHADOW IS SWITCHED BY TAG, "@sh1@" on and "@sh0@" off. Only black, dark blue and dark red can be
 * read on the chatbox parchment - every bright colour is under 2.5:1 against it - so a bright label
 * gets a one-pixel black drop shadow, the way the game draws text over the 3D view. It is per
 * segment rather than per line so a gold "Rare drop!" can sit in front of an unshadowed dark name.
 *
 * COLOUR CARRIES ACROSS AN ICON. drawStringTag starts every call from the colour it is given, so a
 * line drawn in pieces would drop back to black after each icon. Each piece is scanned for the
 * colour it ends on, and the next piece starts from that.
 *
 * The server measures lines the same way (engine FontType.stringWidth counts a marker as ICON_WIDTH)
 * so what it wraps at 456 pixels is what this draws.
 */
public final class ChatIcons {

	/** A 13-pixel sprite and three pixels of space after it - one looked cramped beside the next icon or the name. */
	public static final int ICON_WIDTH = 16;

	private ChatIcons() {
	}

	/** The imageModIcons index a marker at text[i] names, or -1 if there is no icon marker there. */
	public static int iconAt(String text, int i) {
		if (i + 4 >= text.length() || text.charAt(i) != '@' || text.charAt(i + 4) != '@' || text.charAt(i + 1) != 'c' || text.charAt(i + 2) != 'r') {
			return -1;
		}
		char n = text.charAt(i + 3);
		return n >= '1' && n <= '9' ? n - '1' : -1;
	}

	private static int shadowAt(String text, int i) {
		if (i + 4 >= text.length() || text.charAt(i) != '@' || text.charAt(i + 4) != '@' || text.charAt(i + 1) != 's' || text.charAt(i + 2) != 'h') {
			return -1;
		}
		char n = text.charAt(i + 3);
		return n == '1' ? 1 : n == '0' ? 0 : -1;
	}

	/** The icon markers at the very front of a sender's name - a crown, a badge, both or neither. */
	public static String leading(String text) {
		if (text == null) {
			return "";
		}
		int i = 0;
		while (iconAt(text, i) != -1) {
			i += 5;
		}
		return text.substring(0, i);
	}

	/**
	 * The markers for the icons byte at the end of a player's appearance (engine Player.chatIcons):
	 * the crown in the low nibble - 1 silver, 2 gold, 4 developer, 5 owner, 6 second owner,
	 * 7 veteran - and the XP-mode badge in
	 * the high one - 1 Realism, 2 5x, 3 10x. Crown first, the order ~broadcast_name uses.
	 *
	 * 7 IS TESTED BEFORE THE ">= 6", and the order is the only thing stopping a veteran from
	 * wearing the second owner's crown. The low nibble is a set of icon ids rather than a ladder -
	 * the engine's chatCrown picks exactly one - so a new id can land anywhere in the range and the
	 * "at least" comparisons that read like a ranking have to be fenced off as they are added to.
	 */
	public static String forPlayer(int icons) {
		int crown = icons & 0xF;
		int badge = icons >> 4 & 0xF;
		String s = crown == 7 ? "@cr9@" : crown >= 6 ? "@cr8@" : crown == 5 ? "@cr7@" : crown == 4 ? "@cr6@" : crown >= 2 ? "@cr2@" : crown == 1 ? "@cr1@" : "";
		if (badge >= 1 && badge <= 3) {
			s += "@cr" + (badge + 2) + "@";
		}
		return s;
	}

	/** How many icon markers the text holds. */
	public static int iconCount(String text) {
		int n = 0;
		for (int i = 0; i < text.length(); i++) {
			if (iconAt(text, i) != -1) {
				n++;
				i += 4;
			}
		}
		return n;
	}

	/** The drawn width: the text's own width with its tags skipped, plus every icon. */
	public static int width(PixFont font, String text) {
		return font.stringWidTag(text) + iconCount(text) * ICON_WIDTH;
	}

	/**
	 * Draw a line with its icons, starting at x in the given colour. An icon whose sprite did not
	 * load (a cache older than this client) takes no space, so the line closes up around it rather
	 * than leaving a gap.
	 */
	public static void draw(PixFont font, Pix8[] icons, int x, int y, int colour, String text) {
		boolean shadow = false;
		int start = 0;
		for (int i = 0; i < text.length(); i++) {
			int icon = iconAt(text, i);
			int sh = icon == -1 ? shadowAt(text, i) : -1;
			if (icon == -1 && sh == -1) {
				continue;
			}
			if (i > start) {
				String piece = text.substring(start, i);
				font.drawStringTag(colour, x, y, shadow, piece);
				x += font.stringWidTag(piece);
				colour = endColour(font, piece, colour);
			}
			if (icon != -1) {
				if (icon < icons.length && icons[icon] != null) {
					icons[icon].plotSprite(y - 12, x);
					x += ICON_WIDTH;
				}
			} else {
				shadow = sh == 1;
			}
			i += 4;
			start = i + 1;
		}
		if (start < text.length()) {
			font.drawStringTag(colour, x, y, shadow, text.substring(start));
		}
	}

	/** The colour drawStringTag would be using when it reached the end of this text. */
	private static int endColour(PixFont font, String text, int colour) {
		for (int i = 0; i + 4 < text.length(); i++) {
			if (text.charAt(i) == '@' && text.charAt(i + 4) == '@') {
				int c = font.evaluateTag(text.substring(i + 1, i + 4));
				if (c != -1) {
					colour = c;
				}
				i += 4;
			}
		}
		return colour;
	}
}
