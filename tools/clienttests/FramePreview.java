// Paints the whole client window - the drawn title bar, the border, the sidebar - into a PNG, so
// the chrome can be looked at without a cache, a server or a screen. Driven by
// tools/clienttests/run_framepreview.py.
package jagex2.client;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;

import jagex2.client.plugin.PluginManager;
import jagex2.client.plugin.ui.Sidebar;

public class FramePreview {

	public static void main(String[] args) throws Exception {
		File out = new File(args[0]);
		boolean resizable = args.length > 1 && args[1].equals("resizable");

		GameShell shell = new GameShell();
		shell.setPreferredSize(new Dimension(Layout.FIXED_W, Layout.FIXED_H));
		ViewBox frame = new ViewBox(Layout.FIXED_H, shell, Layout.FIXED_W);
		if (resizable) {
			frame.setResizable(true);
		}

		// The real sidebar over the real built-in plugins, so the preview shows the accent as the
		// client will: the toggles, the headings and the tab underline all read Theme.ACCENT.
		Client client = new Client();
		PluginManager manager = new PluginManager(client, null, null, null);
		manager.reload();
		Sidebar sidebar = new Sidebar(manager);
		frame.setSidebar(sidebar);
		frame.validate();
		Thread.sleep(300);

		BufferedImage image = new BufferedImage(frame.getWidth(), frame.getHeight(),
			BufferedImage.TYPE_INT_RGB);
		Graphics g = image.getGraphics();
		// The game canvas paints nothing without a cache, so it is filled here - otherwise the
		// middle of the picture is whatever was in the buffer and the chrome is hard to judge.
		g.setColor(Color.BLACK);
		g.fillRect(0, 0, image.getWidth(), image.getHeight());
		frame.paint(g);
		g.dispose();
		ImageIO.write(image, "png", out);

		System.out.println("wrote " + out + " (" + frame.getWidth() + "x" + frame.getHeight()
			+ ", undecorated=" + frame.isUndecorated() + ")");
		frame.dispose();
		System.exit(0);
	}
}
