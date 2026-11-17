package jagex2.client;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Pattern;

// Logs every player interaction (menu actions/clicks, chat sent/received, login/logout, QoL
// hotkeys) to both the console (System.out) and a plain-text log file (dev-client.log, written
// next to wherever the jar is run from, one line appended per event, flushed immediately).
// Update (2026-09-02, Corey's call): this lives in the single shared javaclient source tree, not
// a separate standalone copy - there's only one Client.java. Which jar comes out of a build
// ('rs2client.jar' vs the console-logging 'rs2client-dev.jar') is controlled entirely by
// settings.gradle's rootProject.name; this class and its DevLog.log(...) call sites are compiled
// into both either way, so a "plain" build still carries the (unused-unless-you-look) logging
// code, just under whichever jar name settings.gradle currently produces.
public final class DevLog {
	private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss.SSS");
	private static final Pattern TAG_PATTERN = Pattern.compile("@\\w+@");
	private static PrintWriter fileOut;

	static {
		PrintWriter writer;
		try {
			writer = new PrintWriter(new FileWriter("dev-client.log", true), true);
			writer.println();
			writer.println("=== dev client session started " + new Date() + " ===");
		} catch (IOException e) {
			writer = null;
			System.out.println("[DevLog] could not open dev-client.log for writing: " + e.getMessage());
		}
		fileOut = writer;
	}

	private DevLog() {
	}

	// Strips RS2's inline colour/format tags (e.g. "@whi@", "@gr2@") out of menu option text so log
	// lines stay readable, e.g. "Attack @whi@Goblin" -> "Attack Goblin".
	public static String stripTags(String text) {
		if (text == null) {
			return "";
		}
		return TAG_PATTERN.matcher(text).replaceAll("");
	}

	public static void log(String category, String detail) {
		String line = "[" + TIME_FORMAT.format(new Date()) + "] [" + category + "] " + detail;
		System.out.println(line);
		if (fileOut != null) {
			fileOut.println(line);
		}
	}

	/** Distinct failures whose frames have already been written out once. */
	private static final java.util.Set<String> TRACED = new java.util.HashSet<String>();

	/** Frames written under a failure, which is plenty to cross the plugin boundary and stop. */
	private static final int FRAMES = 8;

	/** A ceiling on TRACED, so a failure whose message varies every time cannot grow it forever. */
	private static final int DISTINCT = 256;

	/**
	 * A caught-and-swallowed failure, recorded with WHERE it happened.
	 *
	 * WHY THE FRAMES AND NOT JUST THE EXCEPTION. Every catch in the plugin system logs
	 * {@code "... : " + error}, which is {@link Throwable#toString()}: the class, its message,
	 * and not one frame. Four reports of the Quest helper switching itself off (tracker 4, 10,
	 * 15 and 17) all came down to one line of a player's dev-client.log, and that line named a
	 * 1,864-line plugin with four overlays in it and nothing more. The sweep in
	 * QuestHelperTest draws all 845 legs without a throw, so the cause is in what the player had
	 * around them - and a player who sends the line back has still not told us where.
	 *
	 * THE TOP FRAME GOES ON THE SAME LINE as the exception, because the one thing a player is
	 * ever asked to do is search for a phrase and copy the line that matches it. A trace indented
	 * underneath is worth only the chance they copy that too; a file and a line number on the
	 * matching line itself is worth the whole report.
	 *
	 * AND THE REST OF THE TRACE ONCE. The frames below are written the FIRST time a given failure
	 * is seen and never again. An overlay throws at most five times before PluginManager switches
	 * it off, but a config list or a panel button throws on every frame it is drawn, and a full
	 * trace per frame would fill dev-client.log at sixty a second - the log a player is asked to
	 * search is no use if it is a hundred megabytes of the same eight lines.
	 *
	 * THE CAUSE, where there is one, because reflection wraps: an {@code @Subscribe} handler
	 * reaches the bus through Method.invoke and arrives as InvocationTargetException, whose own
	 * top frame is inside the JDK and says nothing about the plugin.
	 */
	public static void log(String category, String detail, Throwable error) {
		if (error == null) {
			log(category, detail);
			return;
		}
		StackTraceElement[] frames = error.getStackTrace();
		String where = frames != null && frames.length > 0 ? frames[0].toString() : "no stack trace";
		log(category, detail + ": " + error + "  at " + where);

		// WHAT COUNTS AS THE SAME FAILURE: the same thing failing the same way in the same place.
		// Not the message, which carries an index or a name that moves between frames and would
		// make every throw look new.
		String signature = category + '|' + detail + '|' + error.getClass().getName() + '|' + where;
		synchronized (TRACED) {
			if (TRACED.size() >= DISTINCT) {
				TRACED.clear();
			}
			if (!TRACED.add(signature)) {
				return;
			}
		}
		if (frames == null || frames.length == 0) {
			return;
		}
		int shown = frames.length < FRAMES ? frames.length : FRAMES;
		for (int i = 0; i < shown; i++) {
			log(category, "    at " + frames[i]);
		}
		if (frames.length > shown) {
			log(category, "    ... " + (frames.length - shown) + " more");
		}
		Throwable cause = error.getCause();
		if (cause != null && cause != error) {
			StackTraceElement[] under = cause.getStackTrace();
			log(category, "    caused by " + cause
				+ (under != null && under.length > 0 ? "  at " + under[0] : ""));
		}
	}
}
