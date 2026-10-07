package jagex2.client.plugin;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a public field of a plugin as a player-facing setting.
 *
 * Supported types are boolean, int and String. Booleans get a row of their own under the plugin in
 * the panel and can be clicked; ints and Strings are shown read-only there and are edited in
 * plugin_config.dat (there is no text entry in the panel yet). Values are read from disk when the
 * plugin starts and written back whenever one is toggled, keyed by plugin key + field key, so a
 * field can be added or removed without disturbing the others.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ConfigItem {

	/** Saved name of this setting. NEVER rename one after release - it resets that setting. */
	String keyName();

	/** Shown in the panel. */
	String name();

	/** Shown under the name when the row is hovered. */
	String description() default "";
}
