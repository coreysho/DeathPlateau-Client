package jagex2.client.plugin;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as an event handler. The method must be public, return void and take exactly one
 * argument: the event type it wants. Anything else is ignored with a log line rather than an
 * exception, because a plugin that is subtly mis-written should not stop the client from starting.
 *
 * <pre>
 * {@literal @}Subscribe
 * public void onGameTick(GameTick tick) { ... }
 * </pre>
 *
 * Handlers run on the client thread, inside the game loop. Whatever one does, the frame waits for
 * it: keep the work small, and never block on IO or sleep.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Subscribe {
}
