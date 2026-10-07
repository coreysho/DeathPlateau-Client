package jagex2.client.plugin.ui;

import java.awt.Color;
import java.awt.Font;

/**
 * Colours and fonts for the client's Swing chrome - the plugin sidebar and the window frame.
 *
 * One place for them because a window built out of a dozen small components goes wrong the moment
 * two of them disagree about what "the background" is. Swing's own look and feel is not used:
 * whatever the player's desktop theme is, this is bolted around a dark game client and has to
 * look like it belongs there.
 *
 * THE PALETTE IS THE LOGO'S. Near-black with a blood red, sampled from the badge on the window
 * icon rather than guessed at - the brightest reds in the artwork sit around #9C1818, which is
 * too dark to read as a highlight on #242424, so the accent is that hue lifted until it does.
 * It lives here and nowhere else, which is why changing it is one line.
 *
 * It does NOT reach the game's own 2D interface. The F9 panel, the loading bar and the chat are
 * drawn in the 2004 client's idiom, in its orange, and recolouring those is a different job from
 * theming the window around them.
 */
public final class Theme {

	public static final Color BACKGROUND = new Color(0x24, 0x24, 0x24);
	public static final Color DARKER = new Color(0x1E, 0x1E, 0x1E);
	public static final Color ROW = new Color(0x2B, 0x2B, 0x2B);
	public static final Color ROW_HOVER = new Color(0x36, 0x36, 0x36);
	public static final Color SEPARATOR = new Color(0x1A, 0x1A, 0x1A);

	public static final Color TEXT = new Color(0xD0, 0xD0, 0xD0);
	public static final Color TEXT_DIM = new Color(0x8A, 0x8A, 0x8A);

	/** The logo's red, lifted to read as a highlight against the greys above. */
	public static final Color ACCENT = new Color(0xB8, 0x32, 0x28);

	/** The same red with the lights on: hover, and the active tab's underline. */
	public static final Color ACCENT_BRIGHT = new Color(0xC0, 0x39, 0x2B);

	// ------------------------------------------------------------------ the window frame

	/** The title bar, darker than the sidebar so the window reads as having a top to it. */
	public static final Color TITLE_BAR = new Color(0x14, 0x14, 0x14);

	/** The line around the whole window, which is all an undecorated frame has for an edge. */
	public static final Color BORDER = new Color(0x3A, 0x3A, 0x3A);

	/** The same line while the window has focus, so a background window recedes. */
	public static final Color BORDER_FOCUSED = new Color(0x5A, 0x24, 0x20);

	/** A window button under the cursor. Close gets its own, because it is the dangerous one. */
	public static final Color BUTTON_HOVER = new Color(0x33, 0x33, 0x33);
	public static final Color CLOSE_HOVER = new Color(0xC0, 0x39, 0x2B);

	public static final Color SWITCH_OFF = new Color(0x55, 0x55, 0x55);
	public static final Color KNOB = new Color(0xDD, 0xDD, 0xDD);

	public static final Font FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
	public static final Font FONT_BOLD = new Font(Font.SANS_SERIF, Font.BOLD, 12);
	public static final Font FONT_SMALL = new Font(Font.SANS_SERIF, Font.PLAIN, 11);
	public static final Font FONT_TITLE = new Font(Font.SANS_SERIF, Font.BOLD, 12);

	/** Width of the sidebar. Wide enough for a plugin name and its two buttons, no wider. */
	public static final int WIDTH = 250;

	private Theme() {
	}
}
