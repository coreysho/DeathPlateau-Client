// Renders the plugin sidebar to a png without starting the game, so what it looks like can be
// checked in review and in CI rather than only by launching the client and squinting at it.
// Driven by tools/clienttests/run_sidebarpreview.py.
package jagex2.client.plugin.ui;

import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;
import javax.swing.JFrame;

import jagex2.client.Client;
import jagex2.client.plugin.PluginManager;

public class SidebarPreview {

	/** A sample index on localhost: one installable plugin, one served over plain http. */
	private static String startIndexServer() throws Exception {
		com.sun.net.httpserver.HttpServer server =
			com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
		final byte[] body = ("{\"plugins\":["
			+ "{\"id\":\"coordinates\",\"name\":\"Coordinates\",\"author\":\"Corey\","
			+ "\"version\":\"1.0\",\"description\":\"Shows your world position\","
			+ "\"url\":\"https://example.invalid/coordinates.jar\"},"
			+ "{\"id\":\"xptracker\",\"name\":\"XP tracker\",\"author\":\"Corey\","
			+ "\"version\":\"2.1\",\"description\":\"Session experience and xp per hour\","
			+ "\"url\":\"https://example.invalid/xptracker.jar\"},"
			+ "{\"id\":\"tilemarkers\",\"name\":\"Tile markers\",\"author\":\"Someone\","
			+ "\"version\":\"0.3\",\"description\":\"Mark tiles on the ground\","
			+ "\"url\":\"http://example.invalid/tiles.jar\"}"
			+ "]}").getBytes("UTF-8");
		server.createContext("/index.json", new com.sun.net.httpserver.HttpHandler() {

			public void handle(com.sun.net.httpserver.HttpExchange exchange) throws java.io.IOException {
				exchange.sendResponseHeaders(200, body.length);
				java.io.OutputStream out = exchange.getResponseBody();
				out.write(body);
				out.close();
			}
		});
		server.setExecutor(null);
		server.start();
		return "http://127.0.0.1:" + server.getAddress().getPort() + "/index.json";
	}

	public static void main(String[] args) throws Exception {
		File out = new File(args[0]);
		boolean config = args.length > 1 && args[1].equals("config");

		// A Client that is never started: the manager only reads fields off it, and nothing here
		// goes near the game loop, the cache or the network.
		Client client = new Client();
		PluginManager manager = new PluginManager(client, null, null, null);
		manager.reload();

		// The hub page is only worth a picture with plugins in it, so the preview serves a real
		// index over localhost and lets the hub fetch it for real - which also means this picture
		// is proof the fetch and the row layout work, not just that the panel draws.
		boolean hub = args.length > 1 && args[1].equals("hub");
		if (hub && System.getProperty("lostcity.pluginindex") == null) {
			// No index given, so serve a sample one locally. Passing -Dlostcity.pluginindex
			// points the preview at a real index instead, which is how the live one is checked.
			System.setProperty("lostcity.pluginindex", startIndexServer());
		}

		Sidebar sidebar = new Sidebar(manager);
		if (hub) {
			sidebar.openHub();
			Thread.sleep(1500);            // the fetch runs on the hub's own thread
			javax.swing.SwingUtilities.invokeAndWait(new Runnable() {

				public void run() {
					sidebar.openHub();     // redraw now the fetch has landed
				}
			});
		}
		if (config) {
			if (manager.getPlugins().isEmpty()) {
				throw new IllegalStateException("no plugins to show a config page for");
			}
			// The config page is only reachable for a plugin with settings, which is the point of
			// the preview: show the page a cog actually opens.
			for (PluginManager.Entry entry : manager.getPlugins()) {
				if (!entry.getConfig().getItems().isEmpty()) {
					sidebar.showConfig(entry);
					break;
				}
			}
		}

		// Laid out inside a real frame so the scroll panes and BoxLayouts size themselves exactly
		// as they will in the client, then painted into an image instead of onto the screen.
		//
		// The frame is sized ONCE and then validated, and nothing is resized afterwards: calling
		// setSize on the sidebar after validating leaves the nested scroll panes laid out for the
		// old size, which puts every label in the wrong place and makes the preview a picture of
		// a bug that is not in the client.
		JFrame frame = new JFrame("preview");
		frame.setUndecorated(true);
		frame.getContentPane().setLayout(new java.awt.BorderLayout());
		frame.getContentPane().add(sidebar, java.awt.BorderLayout.CENTER);
		frame.setSize(Theme.WIDTH, 503);
		// Shown on the (virtual) display rather than just validated: an undisplayed frame has no
		// peer, so its children never get laid out and printAll paints a blank rectangle.
		frame.setVisible(true);
		frame.validate();

		BufferedImage image = new BufferedImage(Theme.WIDTH, 503, BufferedImage.TYPE_INT_RGB);
		sidebar.printAll(image.getGraphics());
		ImageIO.write(image, "png", out);

		frame.dispose();
		System.out.println("wrote " + out + " (" + manager.getPlugins().size() + " plugins)");
		System.exit(0);
	}
}
