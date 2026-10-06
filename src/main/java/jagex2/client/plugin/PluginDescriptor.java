package jagex2.client.plugin;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Identifies a {@link Plugin} to the loader and to the plugin panel.
 *
 * Deliberately shaped like RuneLite's annotation of the same name, so a plugin written against one
 * reads the same against the other. A plugin class without this annotation still loads - it falls
 * back to the simple class name - but it has no description and no stable key, so always add one.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface PluginDescriptor {

	/** Shown in the plugin panel. */
	String name();

	/** One line under the name in the panel. Keep it short - the panel is 480px wide. */
	String description() default "";

	/**
	 * The key this plugin's enabled state and config are saved under. NEVER change it after release
	 * - it silently resets everyone's settings for the plugin. Defaults to the fully qualified class
	 * name, which is stable as long as the class is not moved or renamed.
	 */
	String key() default "";

	/**
	 * Whether the plugin turns itself on the first time it is seen. Only ever honoured for plugins
	 * compiled into the client; a plugin loaded from a jar starts off whatever it asks for, because
	 * dropping a file in a folder should not be enough to make unreviewed code run on the next
	 * launch. The player ticks it on in the panel (F12), once, and that choice is remembered.
	 */
	boolean enabledByDefault() default false;
}
