package jagex2.client.plugin;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a public field of a plugin as a player-facing setting.
 *
 * Supported types are boolean, int and String. A boolean gets a switch, an int a numeric box, a
 * String a text box - and a String can ask for one of two richer editors instead: {@link
 * #colour()} for a swatch and a colour picker, {@link #choices()} for a drop-down. Values are read
 * from disk when the plugin starts and written back as they are edited, keyed by plugin key plus
 * field key, so a field can be added or removed without disturbing the others.
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

	/**
	 * Edit this String as a colour: a swatch that opens a picker, rather than six characters to
	 * type.
	 *
	 * The VALUE IS STILL HEX, "00FF00" and not a packed int, which is what makes this safe to turn
	 * on for a setting that already shipped: the file keeps its old contents, every
	 * PluginConfig.parseColour call keeps working, and a player who typed a colour by hand keeps
	 * what they typed. Only the editor changes.
	 *
	 * Ignored on a field that is not a String, because there is nothing sensible to show.
	 */
	boolean colour() default false;

	/**
	 * Edit this String as a drop-down of exactly these values.
	 *
	 * For a setting that is one of a few named modes - how a price is shown, which loot to list -
	 * where a text box invites a player to type something that is not one of them, and an int
	 * invites them to guess which number means what.
	 *
	 * A STORED VALUE THAT IS NOT IN THE LIST IS KEPT AND SHOWN. A choice removed in a later
	 * release must not silently rewrite a file the player did not ask to change; the panel offers
	 * the current value alongside the real ones so the player can see what they have and pick
	 * again. Ignored on a field that is not a String.
	 */
	String[] choices() default {};
}
