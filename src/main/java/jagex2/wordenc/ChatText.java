package jagex2.wordenc;

import jagex2.io.Packet;

/**
 * Player chat on the wire: public chat, private messages and clan chat (custom, 2026-09-27).
 *
 * WHY NOT WordPack. The 377 packer squeezes a line into nibbles over a fixed table of 61 characters
 * and is full - a byte can name index 60 and no further - so it has no room for '_', '<' or '>', and
 * it has no capitals at all: it lower-cases the line and then capitalises each sentence, which is why
 * ":D" arrived as ":d". A line is now the characters themselves, one byte each, after the packet's
 * own length, so what a player typed is what everybody reads.
 *
 * THE CHARACTERS are the ones the chatbox lets you type: space to 'z' in ASCII - letters of both
 * cases, digits, and ! " # $ % & ' ( ) * + , - . / : ; < = > ? @ [ \ ] ^ _ `. Anything else is
 * dropped. The engine's ChatText (Engine-TS src/wordenc/ChatText.ts) holds the same rule - keep the
 * two the same, it is the protocol.
 *
 * CASE is kept as typed. The one change is the very first letter of a line, which is capitalised if
 * the line starts with one ("hello" reads "Hello", as it did in 2006); ":D", "PvP" and "xD mate"
 * after the first letter are left alone.
 *
 * "@" is safe to send: every chat line is drawn with PixFont.drawString, which draws a colour tag's
 * characters rather than obeying them. Where player text has to sit in a line that IS read for tags
 * (a ::yell is a game message), the server sends LITERAL_AT instead, which PixFont draws as '@' but
 * no tag reader treats as one.
 */
public final class ChatText {

	/** The most characters one line carries, as the chatbox input allows. */
	public static final int MAX_LENGTH = 80;

	/** Drawn as '@' by every font (see PixFont) but never read as the start or end of a tag. */
	public static final char LITERAL_AT = '\u007F';

	private ChatText() {
	}

	public static boolean allowed(char c) {
		return c >= ' ' && c <= 'z';
	}

	/**
	 * A typed line as it will be sent and shown: characters outside the set dropped, runs of spaces
	 * made one, trimmed, cut to MAX_LENGTH, and a leading lower-case letter capitalised.
	 */
	public static String format(String typed) {
		if (typed == null) {
			return "";
		}
		StringBuilder sb = new StringBuilder(typed.length());
		for (int i = 0; i < typed.length(); i++) {
			char c = typed.charAt(i);
			if (!allowed(c)) {
				continue;
			}
			if (c == ' ' && (sb.length() == 0 || sb.charAt(sb.length() - 1) == ' ')) {
				continue;
			}
			sb.append(c);
		}
		String s = sb.toString().trim();
		if (s.length() > MAX_LENGTH) {
			s = s.substring(0, MAX_LENGTH).trim();
		}
		if (s.length() > 0 && s.charAt(0) >= 'a' && s.charAt(0) <= 'z') {
			s = Character.toUpperCase(s.charAt(0)) + s.substring(1);
		}
		return s;
	}

	/** Write a line (already formatted) as one byte per character. */
	public static void pack(String text, Packet out) {
		for (int i = 0; i < text.length() && i < MAX_LENGTH; i++) {
			out.p1(text.charAt(i));
		}
	}

	/** Read length bytes of a line the server sent. Anything outside the set is dropped. */
	public static String unpack(Packet in, int length) {
		StringBuilder sb = new StringBuilder(length);
		for (int i = 0; i < length; i++) {
			char c = (char) in.g1();
			if (allowed(c)) {
				sb.append(c);
			}
		}
		return sb.toString();
	}
}
