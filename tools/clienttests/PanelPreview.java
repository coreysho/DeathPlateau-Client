// Renders the client's own in-game panels - F9 "Client settings" and F8 "Plugins" - into a PNG,
// so their colours can be looked at without a cache, a server or a screen.
//
// THE FONT IS THE PROBLEM these panels have always posed to a test: they draw through PixFont,
// whose glyphs come out of the cache, and a headless run has none. So this builds a PixFont from
// an AWT font instead - rendering each character once and keeping its coverage as the glyph mask
// PixFont.plotLetter already knows how to blit. The panel code underneath is the real thing,
// drawing real text at real positions in real colours.
//
// Driven by tools/clienttests/run_panelpreview.py.
package jagex2.client;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import javax.imageio.ImageIO;

import jagex2.graphics.Pix2D;
import jagex2.graphics.PixFont;

public class PanelPreview {

	static final int W = 765;
	static final int H = 503;

	public static void main(String[] args) throws Exception {
		File out = new File(args[0]);
		String which = args.length > 1 ? args[1] : "qol";

		Client client = new Client();
		set(client, "fontPlain11", new AwtFont(Font.PLAIN, 11));
		set(client, "fontPlain12", new AwtFont(Font.PLAIN, 12));
		set(client, "fontBold12", new AwtFont(Font.BOLD, 12));
		set(client, "layout", Layout.fixed());

		int[] pixels = new int[W * H];
		// A flat mid-grey behind it, standing in for the game: these panels are translucent and
		// drawn over the world, and judging them against black would flatter them.
		java.util.Arrays.fill(pixels, 0x3A3A32);
		Pix2D.bind(W, H, pixels);

		if (which.equals("plugins")) {
			// The panel reads the manager's rows, so give it a real one over the built-ins.
			jagex2.client.plugin.PluginManager manager =
				new jagex2.client.plugin.PluginManager(client, null, null, null);
			manager.reload();
			set(client, "plugins", manager);
			set(client, "pluginPanelRows", manager.buildPanelRows());
			call(client, "drawPluginPanel");
		} else {
			call(client, "drawQolPanel");
		}

		BufferedImage image = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
		image.setRGB(0, 0, W, H, pixels, 0, W);
		ImageIO.write(image, "png", out);
		System.out.println("wrote " + out);
		System.exit(0);
	}

	static void set(Object target, String name, Object value) throws Exception {
		Field f = Client.class.getDeclaredField(name);
		f.setAccessible(true);
		f.set(target, value);
	}

	static void call(Object target, String name) throws Exception {
		Method m = Client.class.getDeclaredMethod(name);
		m.setAccessible(true);
		m.invoke(target);
	}

	/**
	 * A PixFont whose glyphs are drawn by AWT rather than read from the cache.
	 *
	 * A font here is six parallel arrays - a coverage mask per character, its size, its offset
	 * and its advance - and nothing about filling them in requires a jagfile. Each character is
	 * rendered once into a small image and its alpha becomes the mask, so the real drawString
	 * and the real plotLetter do the rest.
	 */
	static final class AwtFont extends PixFont {

		AwtFont(int style, int size) {
			Font font = new Font(Font.SANS_SERIF, style, size);
			BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
			FontMetrics metrics = probe.createGraphics().getFontMetrics(font);
			this.height = metrics.getHeight() - 2;

			for (int c = 0; c < 256; c++) {
				String ch = String.valueOf((char) c);
				int w = Math.max(1, metrics.charWidth((char) c));
				int h = Math.max(1, metrics.getAscent() + metrics.getDescent());
				BufferedImage glyph = new BufferedImage(w + 2, h + 2, BufferedImage.TYPE_INT_ARGB);
				Graphics2D g = glyph.createGraphics();
				g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
					RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
				g.setFont(font);
				g.setColor(Color.WHITE);
				g.drawString(ch, 1, metrics.getAscent());
				g.dispose();

				byte[] mask = new byte[(w + 2) * (h + 2)];
				for (int y = 0; y < h + 2; y++) {
					for (int x = 0; x < w + 2; x++) {
						// Anything more than half covered is on: PixFont's mask is one bit per
						// pixel, so the antialiasing has to be thresholded away somewhere.
						mask[y * (w + 2) + x] =
							(byte) (((glyph.getRGB(x, y) >>> 24) > 128) ? 1 : 0);
					}
				}
				this.charMask[c] = mask;
				this.charMaskWidth[c] = w + 2;
				this.charMaskHeight[c] = h + 2;
				this.charOffsetX[c] = 0;
				// plotLetter is given y - height as its top, and the glyph was drawn with its
				// baseline at ascent, so this lines the two up.
				this.charOffsetY[c] = this.height - metrics.getAscent() - 1;
				this.charAdvance[c] = w;
			}
			// A space has no ink; drawString skips it and only the advance matters.
			this.charAdvance[' '] = Math.max(2, metrics.charWidth(' '));
		}
	}
}
