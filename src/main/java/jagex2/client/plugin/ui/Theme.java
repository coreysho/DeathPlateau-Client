package jagex2.client.plugin.ui;

import java.awt.Color;
import java.awt.Font;

/**
 * Colours and fonts for the plugin sidebar.
 *
 * One place for them because a sidebar built out of a dozen small components goes wrong the moment
 * two of them disagree about what "the background" is. Swing's own look and feel is not used:
 * whatever the player's desktop theme is, the panel is bolted to the side of a dark game client
 * and has to look like it belongs there.
 */
public final class Theme {

	public static final Color BACKGROUND = new Color(0x24, 0x24, 0x24);
	public static final Color DARKER = new Color(0x1E, 0x1E, 0x1E);
	public static final Color ROW = new Color(0x2B, 0x2B, 0x2B);
	public static final Color ROW_HOVER = new Color(0x36, 0x36, 0x36);
	public static final Color SEPARATOR = new Color(0x1A, 0x1A, 0x1A);

	public static final Color TEXT = new Color(0xD0, 0xD0, 0xD0);
	public static final Color TEXT_DIM = new Color(0x8A, 0x8A, 0x8A);

	/** The client's own orange, as the loading bar and the panel headers use it. */
	public static final Color ACCENT = new Color(0xFF, 0x98, 0x1F);

	public static final Color SWITCH_OFF = new Color(0x55, 0x55, 0x55);
	public static final Color KNOB = new Color(0xDD, 0xDD, 0xDD);

	public static final Font FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
	public static final Font FONT_BOLD = new Font(Font.SANS_SERIF, Font.BOLD, 12);
	public static final Font FONT_SMALL = new Font(Font.SANS_SERIF, Font.PLAIN, 11);

	/** Width of the sidebar. Wide enough for a plugin name and its two buttons, no wider. */
	public static final int WIDTH = 250;

	private Theme() {
	}
}
