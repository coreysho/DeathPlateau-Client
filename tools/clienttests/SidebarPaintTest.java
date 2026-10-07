// Does the window paint the sidebar when the window is told to paint itself?
//
// It did not. ViewBox overrides paint(Graphics) and hands it to the game shell, which is what 377
// wanted when the frame WAS the game - but that override replaces Container.paint, the thing that
// walks the child list. Add a lightweight Swing child and nothing ever paints it, so the sidebar
// came up as a black bar and only appeared when the mouse moved over it: a mouse-over repaints
// that component directly through its nearest heavyweight ancestor, which never goes near the
// frame's paint at all.
//
// So this paints the frame the way an expose does - a fresh window, an alt-tab, a window dragged
// off screen and back - and reads the pixels where the sidebar is. Driven by
// tools/clienttests/run_sidebarpreview.py.
package jagex2.client;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.image.BufferedImage;

import javax.swing.JPanel;

public class SidebarPaintTest {

	/** Stands in for the sidebar: a lightweight child that fills itself with one known colour. */
	private static final Color SIDEBAR_COLOUR = new Color(0x24, 0x9A, 0x24);

	public static void main(String[] args) throws Exception {
		GameShell shell = new GameShell();
		shell.setPreferredSize(new Dimension(Layout.FIXED_W, Layout.FIXED_H));

		ViewBox frame = new ViewBox(Layout.FIXED_H, shell, Layout.FIXED_W);

		JPanel sidebar = new JPanel();
		sidebar.setOpaque(true);
		sidebar.setBackground(SIDEBAR_COLOUR);
		sidebar.setPreferredSize(new Dimension(250, 0));
		frame.setSidebar(sidebar);
		frame.validate();

		// Paint the frame the way an expose event does, into an image instead of onto the screen.
		BufferedImage image = new BufferedImage(frame.getWidth(), frame.getHeight(), BufferedImage.TYPE_INT_RGB);
		Graphics g = image.getGraphics();
		g.setColor(Color.BLACK);
		g.fillRect(0, 0, image.getWidth(), image.getHeight());
		frame.paint(g);
		g.dispose();

		// A point well inside the sidebar: its own area, past the window border and the game.
		int x = frame.getWidth() - frame.getInsets().right - 125;
		int y = frame.getHeight() / 2;
		int painted = image.getRGB(x, y) & 0xFFFFFF;
		boolean ok = painted == (SIDEBAR_COLOUR.getRGB() & 0xFFFFFF);

		System.out.println((ok ? "  ok   " : "FAIL   ")
			+ "the window paints its sidebar (" + String.format("#%06X", painted)
			+ " at " + x + "," + y + ", wanted #" + String.format("%06X", SIDEBAR_COLOUR.getRGB() & 0xFFFFFF) + ")");

		frame.dispose();
		System.exit(ok ? 0 : 1);
	}
}
