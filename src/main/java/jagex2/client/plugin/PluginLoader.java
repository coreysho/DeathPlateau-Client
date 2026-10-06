package jagex2.client.plugin;

import java.io.File;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import jagex2.client.DevLog;

/**
 * Turns a folder of jars into plugin classes.
 *
 * Split out of {@link PluginManager} because this half needs nothing from the client - give it a
 * directory and it gives back classes - which means it can be driven headlessly by
 * tools/clienttests/run_plugintest.py. Loading code out of a jar is the part most worth having a
 * test for and the part hardest to check by playing the game.
 *
 * ONE CLASS LOADER PER JAR, parented to the client's own. A plugin therefore sees the client's
 * classes (and so its Plugin is the same class as the client's Plugin), while the client never
 * sees a plugin's. Two jars cannot collide with each other either, except where they both resolve
 * a name through the shared parent.
 *
 * NOTHING HERE THROWS. A jar that is corrupt, compiled against a different client, or not a plugin
 * jar at all is logged and skipped; the others still load.
 */
public final class PluginLoader {

	/** Manifest attribute naming a jar's plugin classes, comma separated. */
	public static final String MANIFEST_ATTRIBUTE = "Plugin-Class";

	/** A plugin class and the jar it came out of. */
	public static final class Found {

		public final Class<?> type;
		public final String source;

		Found(Class<?> type, String source) {
			this.type = type;
			this.source = source;
		}
	}

	private PluginLoader() {
	}

	/** Every plugin class in every jar in the folder. Empty when there is no folder. */
	public static List<Found> scan(File directory) {
		List<Found> found = new ArrayList<Found>();
		File[] files;
		try {
			if (directory == null || !directory.isDirectory()) {
				return found;                      // no folder, no plugins: not a problem
			}
			files = directory.listFiles();
		} catch (Throwable error) {
			DevLog.log("PLUGIN", "cannot read " + directory + ": " + error);
			return found;
		}
		if (files == null) {
			return found;
		}
		// Sorted so the list is the same on every machine and every launch - a folder's natural
		// order is whatever the filesystem feels like, and a plugin list that reshuffles itself
		// between launches is a bad list.
		Arrays.sort(files);
		for (int i = 0; i < files.length; i++) {
			File file = files[i];
			if (file.isFile() && file.getName().toLowerCase().endsWith(".jar")) {
				readJar(file, found);
			}
		}
		return found;
	}

	private static void readJar(File file, List<Found> found) {
		JarFile jar = null;
		try {
			jar = new JarFile(file);
			List<String> classNames = classNames(jar);
			if (classNames.isEmpty()) {
				DevLog.log("PLUGIN", file.getName() + " has no classes in it");
				return;
			}
			ClassLoader loader = new URLClassLoader(new URL[] { file.toURI().toURL() },
				PluginLoader.class.getClassLoader());
			int before = found.size();
			for (int i = 0; i < classNames.size(); i++) {
				// Loaded WITHOUT initialising: a class that turns out not to be a plugin must not
				// get to run its static initialiser on the way past.
				String className = classNames.get(i);
				try {
					Class<?> type = Class.forName(className, false, loader);
					if (isPlugin(type)) {
						found.add(new Found(type, file.getName()));
					}
				} catch (Throwable error) {
					DevLog.log("PLUGIN", "could not load " + className + " from " + file.getName() + ": " + error);
				}
			}
			if (found.size() == before) {
				DevLog.log("PLUGIN", file.getName() + " has no plugin classes in it");
			}
		} catch (Throwable error) {
			DevLog.log("PLUGIN", "could not read " + file.getName() + ": " + error);
		} finally {
			try {
				if (jar != null) {
					jar.close();
				}
			} catch (Exception ignored) {
			}
		}
	}

	private static boolean isPlugin(Class<?> type) {
		return Plugin.class.isAssignableFrom(type)
			&& type != Plugin.class
			&& !Modifier.isAbstract(type.getModifiers());
	}

	/**
	 * The classes to look at in a jar: whatever its manifest names, or failing that every class in
	 * it. The manifest route is there so a jar with a library inside it is not walked class by
	 * class, and so an author can say which classes are the plugins.
	 */
	private static List<String> classNames(JarFile jar) throws Exception {
		List<String> names = new ArrayList<String>();
		Manifest manifest = jar.getManifest();
		String declared = manifest == null ? null : manifest.getMainAttributes().getValue(MANIFEST_ATTRIBUTE);
		if (declared != null && declared.trim().length() > 0) {
			String[] split = declared.split(",");
			for (int i = 0; i < split.length; i++) {
				String name = split[i].trim();
				if (name.length() > 0) {
					names.add(name);
				}
			}
			return names;
		}
		Enumeration<JarEntry> entries = jar.entries();
		while (entries.hasMoreElements()) {
			String name = entries.nextElement().getName();
			// Inner and anonymous classes are reached through the class that owns them, and an
			// anonymous Overlay subclass would otherwise be picked up as a plugin of its own.
			if (name.endsWith(".class") && name.indexOf('$') < 0) {
				names.add(name.substring(0, name.length() - 6).replace('/', '.'));
			}
		}
		return names;
	}
}
