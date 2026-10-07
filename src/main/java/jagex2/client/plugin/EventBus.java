package jagex2.client.plugin;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jagex2.client.DevLog;

/**
 * Delivers events to {@link Subscribe}-annotated methods.
 *
 * WHY REFLECTION. A plugin should be able to say "I care about game ticks" by writing one method,
 * without the client knowing anything about it at compile time - that is the whole point of a
 * plugin. The cost is a reflective call per handler per event, which at the rate these fire (a few
 * events a frame, a handful of handlers) does not register next to drawing the scene.
 *
 * NOTHING A PLUGIN DOES MAY KILL THE CLIENT. Every handler runs inside a catch of Throwable. A
 * plugin that throws is logged, and one that keeps throwing is handed back to the manager to be
 * shut down - an overlay that throws every frame would otherwise fill the log and drag the frame
 * rate down until the player gave up and killed the window.
 */
public final class EventBus {

	/** Failures in a row a single subscriber gets before the bus stops calling it. */
	private static final int MAX_ERRORS = 5;

	/** One registered handler method on one object. */
	private static final class Handler {

		final Object subscriber;
		final Method method;
		int errors;

		Handler(Object subscriber, Method method) {
			this.subscriber = subscriber;
			this.method = method;
		}
	}

	/** Event class -> handlers for it. Exact class match; events are final, so there is no hierarchy. */
	private final Map<Class<?>, List<Handler>> handlers = new HashMap<Class<?>, List<Handler>>();

	/** Told when a subscriber has thrown too often, so the plugin owning it can be turned off. */
	private final ErrorListener errorListener;

	public interface ErrorListener {

		void onSubscriberFailed(Object subscriber, Throwable error);
	}

	public EventBus(ErrorListener errorListener) {
		this.errorListener = errorListener;
	}

	/**
	 * Registers every @Subscribe method on the object. A method that is not shaped like a handler
	 * (wrong argument count, not public) is skipped with a log line: the plugin author gets told,
	 * and the player still gets a working client.
	 */
	public void register(Object subscriber) {
		if (subscriber == null) {
			return;
		}
		Method[] methods;
		try {
			methods = subscriber.getClass().getMethods();
		} catch (Throwable error) {
			DevLog.log("PLUGIN", "could not inspect " + subscriber.getClass().getName() + ": " + error);
			return;
		}
		for (int i = 0; i < methods.length; i++) {
			Method method = methods[i];
			if (!method.isAnnotationPresent(Subscribe.class)) {
				continue;
			}
			if (method.getParameterTypes().length != 1 || Modifier.isStatic(method.getModifiers())) {
				DevLog.log("PLUGIN", "ignoring @Subscribe " + method.getDeclaringClass().getSimpleName() + "."
					+ method.getName() + ": must be a non-static method taking exactly one event argument");
				continue;
			}
			Class<?> event = method.getParameterTypes()[0];
			List<Handler> list = this.handlers.get(event);
			if (list == null) {
				list = new ArrayList<Handler>();
				this.handlers.put(event, list);
			}
			list.add(new Handler(subscriber, method));
		}
	}

	/** Drops every handler belonging to the object. Safe to call for something never registered. */
	public void unregister(Object subscriber) {
		if (subscriber == null) {
			return;
		}
		for (List<Handler> list : this.handlers.values()) {
			for (int i = list.size() - 1; i >= 0; i--) {
				if (list.get(i).subscriber == subscriber) {
					list.remove(i);
				}
			}
		}
	}

	/**
	 * Delivers an event to everything listening for its exact class. Returns at once when nothing
	 * is - the common case, and the reason it is cheap to post an event from the game loop.
	 */
	public void post(Object event) {
		if (event == null) {
			return;
		}
		List<Handler> list = this.handlers.get(event.getClass());
		if (list == null || list.isEmpty()) {
			return;
		}
		// Iterated by index over a snapshot-free list, so a handler that unregisters itself mid-post
		// cannot throw ConcurrentModificationException. Removals only ever shrink the tail here
		// because a failing handler is removed after the loop, not during it.
		for (int i = 0; i < list.size(); i++) {
			Handler handler = list.get(i);
			try {
				handler.method.invoke(handler.subscriber, event);
				if (handler.errors != 0) {
					handler.errors = 0;  // a one-off throw is forgiven; it is a run of them that is fatal
				}
			} catch (Throwable error) {
				Throwable cause = error.getCause() == null ? error : error.getCause();
				handler.errors++;
				DevLog.log("PLUGIN", handler.subscriber.getClass().getSimpleName() + "."
					+ handler.method.getName() + " threw " + cause);
				if (handler.errors >= MAX_ERRORS) {
					// Dropped here rather than left to the listener to deal with: the bus must stop
					// calling a handler it has given up on whatever the listener does about it, or
					// a plugin the listener cannot find keeps throwing on every event forever.
					list.remove(i);
					if (this.errorListener != null) {
						this.errorListener.onSubscriberFailed(handler.subscriber, cause);
					}
					return;              // the listener shuts the plugin down, which edits this list
				}
			}
		}
	}
}
