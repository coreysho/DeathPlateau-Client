package jagex2.client;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.List;

import javax.imageio.ImageIO;

import jagex2.graphics.Pix2D;
import jagex2.graphics.Pix32;
import jagex2.graphics.PixFont;
import jagex2.jstring.JString;

/**
 * The login screen: the sigil, the wordmark, the two fields and the row of saved accounts.
 *
 * EVERYTHING HERE IS IN SCREEN COORDINATES, 0..765 by 0..503, and that is the whole point of the
 * class existing. The title screen is assembled from nine PixMap pieces that tile the frame, and
 * the pieces are addressed in a convention that reads backwards twice over: PixMap(h, component,
 * w) takes its HEIGHT first, and draw(y, x) takes its Y first. Read as (x, y) the nine pieces
 * overlap each other and run three hundred pixels off the bottom of a 503-tall screen. The music
 * toggle was nineteen pixels out for exactly this reason, and its comment in Client says the
 * lesson: have one coordinate space, convert once, at the edge.
 *
 * So this paints the entire frame into a single 765x503 sprite in plain screen coordinates, and
 * Client copies rectangles out of it into the nine pieces. Nothing in here knows the pieces
 * exist, and the hit tests below measure against the same constants the drawing uses, so a
 * button cannot be drawn in one place and answer clicks in another.
 *
 * THE EXPENSIVE PART IS BUILT ONCE. base() renders the background, the vignette and the sigil -
 * a third of a million pixels of blending - into an array that is then copied per frame. What
 * changes per frame is a text cursor; what changes per click is a tick or a highlight. Neither
 * is worth re-blending a sigil for.
 *
 * Nothing here may throw. Art that will not load is art that is skipped: a login screen missing
 * its logo is still a login screen, and a client that cannot reach its own login form is not.
 */
public final class TitleScreen {

	public static final int W = 765;
	public static final int H = 503;

	// ---- the palette, sampled from the sigil rather than matched by eye. The three stone tones
	// are the highlight, light and mid of 16851 of its own pixels; the darkest it also has sits
	// at 2.3:1 on this ground and is deliberately unused.
	private static final int GROUND = 0x040303;
	private static final int STONE = 0xE2DFE2;          // field text, the selected account
	private static final int STONE_DIM = 0xA19FA1;      // labels, the other accounts
	private static final int STONE_FAINT = 0x8D8B8D;    // timestamps, the player count
	private static final int CRIMSON = 0xB0131C;
	private static final int CRIMSON_EDGE = 0xC0161F;
	private static final int CRIMSON_TEXT = 0xF2726F;   // the link; #B0131C is 4:1 and fails small
	private static final int FIELD_RULE = 0x7A4A4B;

	// ---- layout. The form sits low enough that both fields fall inside the middle piece, which
	// is the only one redrawn per frame; the cursor blinks there and nowhere else.
	private static final int CX = W / 2;
	private static final int FORM_W = 296;
	private static final int FORM_X = CX - FORM_W / 2;

	private static final int SIGIL_SIZE = 468;
	private static final int WORDMARK_Y = 24;
	private static final int RULE_Y = 118;
	private static final int RULE_W = 112;

	private static final int FIELD_H = 42;
	private static final int USER_Y = 178;
	private static final int PASS_Y = 229;
	private static final int REMEMBER_Y = 288;
	private static final int REMEMBER_BOX = 16;
	private static final int BUTTON_Y = 325;
	private static final int BUTTON_H = 44;
	private static final int LINK_Y = 380;

	private static final int BAR_Y = BUTTON_Y;
	private static final int BAR_H = 22;

	private static final int PROFILE_Y = 423;
	private static final int PROFILE_H = 44;
	private static final int PROFILE_W = 128;
	private static final int PROFILE_GAP = 10;
	private static final int ADD_W = 42;
	private static final int FORGET_W = 16;
	private static final int FOOTER_Y = 479;

	private static final int SIGIL_ALPHA = 92;          // 36% of 256, the mockup's opacity

	private static boolean artTried;
	private static Pix32 sigil;
	private static Pix32 wordmark;
	private static int[] base;

	private TitleScreen() {
	}

	// ---------------------------------------------------------------- the art

	/**
	 * The sigil and the wordmark come out of the client jar, not the cache. Branding already
	 * reads PNGs this way for the window icon, so there is no second mechanism here - and it
	 * means the art cannot fall out of step with a cache the player downloaded separately.
	 */
	private static void loadArt() {
		if (artTried) {
			return;
		}
		artTried = true;
		sigil = read("/deathplateau/sigil.png");
		wordmark = read("/deathplateau/wordmark.png");
	}

	private static Pix32 read(String path) {
		InputStream in = null;
		try {
			in = TitleScreen.class.getResourceAsStream(path);
			if (in == null) {
				return null;
			}
			BufferedImage img = ImageIO.read(in);
			if (img == null) {
				return null;
			}
			int w = img.getWidth();
			int h = img.getHeight();
			Pix32 out = new Pix32(w, h);
			img.getRGB(0, 0, w, h, out.pixels, 0, w);
			// Pix32 has no alpha channel: it carries one transparent colour and that colour is 0.
			// The art is flattened onto black for exactly this reason, so dropping the alpha byte
			// leaves the background transparent and the glow fading into the page.
			for (int i = 0; i < out.pixels.length; i++) {
				out.pixels[i] &= 0xFFFFFF;
			}
			return out;
		} catch (Throwable error) {
			return null;
		} finally {
			try {
				if (in != null) {
					in.close();
				}
			} catch (Throwable ignored) {
			}
		}
	}

	/** The ground, the sigil and the ash - everything that never changes. Built once. */
	private static int[] base(Pix32 canvas) {
		if (base != null) {
			return base;
		}
		loadArt();
		canvas.bind();
		Pix2D.fillRect(H, 0, GROUND, W, 0);

		if (sigil != null) {
			sigil.transPlotSprite((W - SIGIL_SIZE) / 2, (H - SIGIL_SIZE) / 2, SIGIL_ALPHA);
		}

		// The vignette goes on AFTER the sigil so it takes the mark's edges down with the ground,
		// which is what stops it reading as a square picture laid on the screen.
		vignette(canvas.pixels);
		ash(canvas.pixels);

		base = new int[W * H];
		System.arraycopy(canvas.pixels, 0, base, 0, base.length);
		return base;
	}

	/** Darkens towards the corners, untouched over the middle 38%. */
	private static void vignette(int[] px) {
		int cx = W / 2;
		int cy = H / 2;
		// Radii as in the design: an ellipse 78% of the width and 68% of the height.
		double rx = W * 0.78 / 2.0;
		double ry = H * 0.68 / 2.0;
		for (int y = 0; y < H; y++) {
			for (int x = 0; x < W; x++) {
				double dx = (x - cx) / rx;
				double dy = (y - cy) / ry;
				double d = Math.sqrt(dx * dx + dy * dy);
				if (d <= 0.38) {
					continue;
				}
				double t = (d - 0.38) / 0.62;
				if (t > 1.0) {
					t = 1.0;
				}
				int keep = 256 - (int) (t * 0.78 * 256);
				int i = y * W + x;
				int c = px[i];
				px[i] = ((c >> 16 & 0xFF) * keep >> 8) << 16
					| ((c >> 8 & 0xFF) * keep >> 8) << 8
					| ((c & 0xFF) * keep >> 8);
			}
		}
	}

	/**
	 * Drifting ash. Scattered from a fixed seed rather than at random, so the screen looks the
	 * same every launch - a login screen that reshuffles itself each time reads as a glitch.
	 */
	private static void ash(int[] px) {
		int seed = 0x5EED1A;
		for (int i = 0; i < 46; i++) {
			seed = seed * 1103515245 + 12345;
			int x = (seed >>> 16) % W;
			seed = seed * 1103515245 + 12345;
			int y = (seed >>> 16) % H;
			seed = seed * 1103515245 + 12345;
			boolean ember = ((seed >>> 16) & 3) == 0;
			px[y * W + x] = ember ? 0xF0785F : 0xE2C6BE;
		}
	}

	// ---------------------------------------------------------------- painting

	/**
	 * The whole frame, every time. The caller decides which pieces of it actually reach the
	 * screen; see Client.drawTitle.
	 *
	 * `message` is the login server's reply, or "" when there is nothing to say. `hover` is the
	 * account the pointer is over, or -1.
	 */
	public static void paint(Pix32 canvas, PixFont bold, PixFont plain, String username,
		String password, int field, boolean remember, String message,
		List<LoginProfiles.Profile> profiles, int hover, int online, int loopCycle) {

		int[] ground = base(canvas);
		canvas.bind();
		System.arraycopy(ground, 0, canvas.pixels, 0, ground.length);

		if (wordmark != null) {
			// plotSprite takes (Y, X) and transPlotSprite - on the SAME class - takes (X, Y).
			// Not a typo here: Pix32.plotSprite computes x from arg1 and y from arg0, while
			// transPlotSprite does the reverse. Passing this one (x, y) put the wordmark 150
			// pixels left and 150 down, over the sigil, while the sigil beside it was correct.
			wordmark.plotSprite(WORDMARK_Y, CX - wordmark.wi / 2);
		}
		rule();

		if (message != null && message.length() > 0) {
			centre(bold, message, CRIMSON_TEXT, 162);
		}

		field(plain, username, USER_Y, field == 0, false, loopCycle);
		field(plain, password, PASS_Y, field == 1, true, loopCycle);
		rememberRow(plain, remember);
		enterButton(bold);
		centre(plain, "Forgot your password?", CRIMSON_TEXT, LINK_Y + 13);

		profileRow(plain, profiles, hover);
		centre(plain, footerText(online), STONE_FAINT, FOOTER_Y + 10);
	}

	/**
	 * The loading screen: the same ground, sigil and wordmark, with a bar where the form goes.
	 *
	 * It is the FIRST thing a player sees, before the cache has arrived, so it cannot use any of
	 * it - the art comes out of the jar and the font may still be null, which is why this looked
	 * like the old stone hall long after the login screen behind it had changed. Drawn here
	 * rather than in Client so the two screens cannot drift apart: one ground, one mark, one set
	 * of colours.
	 */
	public static void paintProgress(Pix32 canvas, PixFont font, int percent, String message) {
		int[] ground = base(canvas);
		canvas.bind();
		System.arraycopy(ground, 0, canvas.pixels, 0, ground.length);

		if (wordmark != null) {
			wordmark.plotSprite(WORDMARK_Y, CX - wordmark.wi / 2);
		}
		rule();

		if (percent < 0) {
			percent = 0;
		}
		if (percent > 100) {
			percent = 100;
		}

		centre(font, message == null ? "" : message, STONE_DIM, BAR_Y - 12);
		Pix2D.fillRectTrans(CRIMSON, BAR_Y, FORM_W, BAR_H, 36, FORM_X);
		Pix2D.fillRect(BAR_H, BAR_Y, CRIMSON, FORM_W * percent / 100, FORM_X);
		Pix2D.drawRect(BAR_Y, BAR_H, CRIMSON_EDGE, FORM_X, FORM_W);
		centre(font, percent + "%", STONE, BAR_Y + BAR_H - 5);
	}

	/** The crimson hairline under the wordmark, fading out at both ends. */
	private static void rule() {
		int x0 = CX - RULE_W / 2;
		for (int i = 0; i < RULE_W; i++) {
			// Brightest in the middle, nothing at the ends.
			int d = Math.abs(i - RULE_W / 2);
			int alpha = 256 - d * 512 / RULE_W;
			if (alpha > 0) {
				Pix2D.fillRectTrans(CRIMSON, RULE_Y, 1, 1, alpha, x0 + i);
			}
		}
	}

	/** An underlined field, its text centred, with a block cursor when it has the focus. */
	private static void field(PixFont font, String text, int y, boolean focused, boolean hidden,
		int loopCycle) {
		Pix2D.fillRect(1, y + FIELD_H, FIELD_RULE, FORM_W, FORM_X);
		String shown = hidden ? JString.censor(text) : text;
		int wid = wid(font, shown);
		int x = CX - wid / 2;
		text(font, shown, STONE, x, y + 27);
		// 40 ticks on, 40 off, matching the rate the old login form blinked at.
		if (focused && loopCycle % 40 < 20) {
			Pix2D.fillRect(16, y + 12, STONE, 1, x + wid + 2);
		}
	}

	private static void rememberRow(PixFont font, boolean on) {
		String label = "Remember me";
		int wid = wid(font, label);
		int boxX = CX - (REMEMBER_BOX + 9 + wid) / 2;
		Pix2D.drawRect(REMEMBER_Y, REMEMBER_BOX, on ? CRIMSON_EDGE : FIELD_RULE, boxX, REMEMBER_BOX);
		if (on) {
			Pix2D.fillRect(REMEMBER_BOX - 6, REMEMBER_Y + 3, CRIMSON, REMEMBER_BOX - 6, boxX + 3);
		}
		text(font, label, STONE_DIM, boxX + REMEMBER_BOX + 9, REMEMBER_Y + REMEMBER_BOX - 3);
	}

	private static void enterButton(PixFont font) {
		Pix2D.fillRectTrans(CRIMSON, BUTTON_Y, FORM_W, BUTTON_H, 52, FORM_X);
		Pix2D.drawRect(BUTTON_Y, BUTTON_H, CRIMSON_EDGE, FORM_X, FORM_W);
		centre(font, "ENTER", STONE, BUTTON_Y + BUTTON_H / 2 + 5);
	}

	/**
	 * The saved accounts, centred as a row. An account that kept its password signs in on one
	 * click; one that did not fills the name in and leaves the cursor in the password field, so
	 * the two cases look the same and behave differently only where they must.
	 */
	private static void profileRow(PixFont font, List<LoginProfiles.Profile> profiles, int hover) {
		int count = profiles == null ? 0 : profiles.size();
		int total = count * PROFILE_W + count * PROFILE_GAP + ADD_W;
		int x = CX - total / 2;

		for (int i = 0; i < count; i++) {
			LoginProfiles.Profile p = profiles.get(i);
			boolean lit = i == hover;
			if (lit) {
				Pix2D.fillRectTrans(CRIMSON, PROFILE_Y, PROFILE_W, PROFILE_H, 41, x);
				Pix2D.drawRect(PROFILE_Y, PROFILE_H, CRIMSON, x, PROFILE_W);
			} else {
				Pix2D.fillRectTrans(0x0E0909, PROFILE_Y, PROFILE_W, PROFILE_H, 217, x);
				Pix2D.drawRect(PROFILE_Y, PROFILE_H, 0x382324, x, PROFILE_W);
			}
			text(font, p.username, lit ? STONE : STONE_DIM, x + 10, PROFILE_Y + 19);
			text(font, p.canSignIn() ? "saved" : "name only", STONE_FAINT, x + 10, PROFILE_Y + 34);
			// The x. Drawn dim because removing an account is not what the card is for.
			text(font, "x", STONE_FAINT, x + PROFILE_W - FORGET_W, PROFILE_Y + 19);
			x += PROFILE_W + PROFILE_GAP;
		}

		Pix2D.drawRect(PROFILE_Y, PROFILE_H, FIELD_RULE, x, ADD_W);
		text(font, "+", STONE_DIM, x + ADD_W / 2 - 3, PROFILE_Y + PROFILE_H / 2 + 5);
	}

	/**
	 * Every piece of text goes through these two, and both tolerate a null font, because the
	 * first thing a new player sees is this screen while the cache is still coming down - the
	 * fonts live in that cache. A screen with its art and no labels is recoverable; an exception
	 * out of the title paint is a client that cannot reach its own login form.
	 */
	private static void text(PixFont font, String s, int colour, int x, int y) {
		if (font != null && s != null) {
			font.drawStringTag(colour, x, y, false, s);
		}
	}

	private static int wid(PixFont font, String s) {
		return font == null || s == null ? 0 : font.stringWidTag(s);
	}

	/**
	 * The player count line, or null for no line at all.
	 *
	 * BELOW ZERO MEANS NOBODY HAS TOLD US, and an invented number is worse than none: the client
	 * has no online count at the title screen - playerCount is the in-scene count during play -
	 * so Client passes -1 until a login server sends one.
	 *
	 * A method rather than an `if` around the draw, so the rule can be tested. As an `if` it
	 * could not be: every text call here tolerates a null font and no test in this repo has a
	 * font, so both branches draw nothing and a pixel cannot tell them apart. It shipped reading
	 * "-1 PLAYERS ONLINE" for exactly that reason.
	 */
	static String footerText(int online) {
		return online < 0 ? null : online + " PLAYERS ONLINE";
	}

	private static void centre(PixFont font, String text, int colour, int y) {
		text(font, text, colour, CX - wid(font, text) / 2, y);
	}

	// ---------------------------------------------------------------- hit tests
	//
	// Measured against the same constants the drawing uses. That is the entire defence against
	// the class of bug this screen replaced: a control drawn in one place and clicked in another.

	/** 0 for the username field, 1 for the password field, -1 for neither. */
	public static int fieldAt(int x, int y) {
		if (x < FORM_X || x > FORM_X + FORM_W) {
			return -1;
		}
		if (y >= USER_Y && y <= USER_Y + FIELD_H) {
			return 0;
		}
		if (y >= PASS_Y && y <= PASS_Y + FIELD_H) {
			return 1;
		}
		return -1;
	}

	public static boolean inEnter(int x, int y) {
		return x >= FORM_X && x <= FORM_X + FORM_W && y >= BUTTON_Y && y <= BUTTON_Y + BUTTON_H;
	}

	/**
	 * The tick box and its words both toggle it. A 16 pixel box is a small target, and the label
	 * beside it is the thing people actually aim at.
	 */
	public static boolean inRemember(int x, int y, PixFont font) {
		int wid = wid(font, "Remember me");
		int boxX = CX - (REMEMBER_BOX + 9 + wid) / 2;
		return x >= boxX && x <= boxX + REMEMBER_BOX + 9 + wid
			&& y >= REMEMBER_Y - 4 && y <= REMEMBER_Y + REMEMBER_BOX + 4;
	}

	private static int rowStart(int count) {
		return CX - (count * PROFILE_W + count * PROFILE_GAP + ADD_W) / 2;
	}

	/** The account under the pointer, or -1. Used for the highlight and for the click alike. */
	public static int profileAt(int x, int y, int count) {
		if (y < PROFILE_Y || y > PROFILE_Y + PROFILE_H) {
			return -1;
		}
		int at = rowStart(count);
		for (int i = 0; i < count; i++) {
			if (x >= at && x <= at + PROFILE_W) {
				return i;
			}
			at += PROFILE_W + PROFILE_GAP;
		}
		return -1;
	}

	/**
	 * The x on an account. Checked BEFORE profileAt by the caller, because it sits inside the
	 * card: tested the other way round, removing an account would sign into it instead.
	 */
	public static int forgetAt(int x, int y, int count) {
		if (y < PROFILE_Y || y > PROFILE_Y + 26) {
			return -1;
		}
		int at = rowStart(count);
		for (int i = 0; i < count; i++) {
			if (x >= at + PROFILE_W - FORGET_W - 4 && x <= at + PROFILE_W) {
				return i;
			}
			at += PROFILE_W + PROFILE_GAP;
		}
		return -1;
	}

	public static boolean inAdd(int x, int y, int count) {
		int at = rowStart(count) + count * (PROFILE_W + PROFILE_GAP);
		return x >= at && x <= at + ADD_W && y >= PROFILE_Y && y <= PROFILE_Y + PROFILE_H;
	}

	/** The tests rebuild the ground between cases; the client never needs it. */
	static void forget() {
		base = null;
		artTried = false;
		sigil = null;
		wordmark = null;
	}
}
