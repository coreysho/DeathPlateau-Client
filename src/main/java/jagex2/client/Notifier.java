package jagex2.client;

import java.awt.AWTException;
import java.awt.Image;
import java.awt.SystemTray;
import java.awt.TrayIcon;

/**
 * Telling the player something when they are not looking at the game.
 *
 * A notification is three things at once, and which ones happen depends on where the player is:
 * a line in the chatbox always, a desktop notification only when the window is not focused, and
 * nothing at all if they have turned desktop notifications off. Notifying someone who is already
 * looking at the screen with a tray popup is how a feature becomes an annoyance.
 *
 * THE TRAY IS BEST-EFFORT. SystemTray is unsupported on some desktops, throws on others, and on
 * Linux depends on which panel is running. Every failure here is swallowed and the icon is not
 * tried again: a notification that cannot be delivered is not worth an exception in the game
 * loop, and the chat line went out regardless.
 *
 * NOT A PLUGIN API BY ITSELF. Plugins reach this through PluginContext.notify, which rate-limits
 * and which is the only caller that is not the client itself.
 */
public final class Notifier {

	/** The tray icon, made on first use. Null while unmade, and after a failure. */
	private static TrayIcon icon;

	/** Set once the tray has been tried, so a desktop that cannot do it is asked once. */
	private static boolean tried;

	private Notifier() {
	}

	/**
	 * Shows a desktop notification, if the player is not already looking at the game.
	 *
	 * Returns whether one was shown, which is only used for saying so in the log - the caller has
	 * already put the message in chat and does not change what it does based on this.
	 */
	public static boolean desktop(String title, String message, boolean focused) {
		if (message == null || message.length() == 0 || !wanted(focused)) {
			return false;
		}
		TrayIcon tray = icon();
		if (tray == null) {
			return false;
		}
		try {
			tray.displayMessage(title == null || title.length() == 0 ? "Death Plateau" : title,
				message, TrayIcon.MessageType.NONE);
			return true;
		} catch (Throwable ignored) {
			return false;
		}
	}

	/**
	 * Whether a desktop notification is wanted at all, which is the whole rule in one place.
	 *
	 * SEPARATE FROM DELIVERING ONE on purpose. Whether to show a popup is two booleans and is
	 * the part that can be wrong in a way players notice; showing it needs a system tray, which
	 * a test machine running a virtual display does not have. Kept inside desktop() the rule
	 * could only be checked by machines that could also pop a balloon, which is to say not by
	 * the test suite.
	 */
	static boolean wanted(boolean focused) {
		return !focused && QolSettings.on(QolSettings.DESKTOP_NOTIFY);
	}

	/**
	 * The tray icon, made once.
	 *
	 * Deliberately not removed on shutdown. The JVM drops it when the process goes, and a
	 * remove() racing a displayMessage() on a dying client is a crash on the way out for no gain.
	 */
	private static synchronized TrayIcon icon() {
		if (tried) {
			return icon;
		}
		tried = true;
		try {
			if (!SystemTray.isSupported()) {
				DevLog.log("NOTIFY", "no system tray on this desktop; chat only");
				return null;
			}
			SystemTray tray = SystemTray.getSystemTray();
			Image image = lostcity.Branding.iconAtLeast(tray.getTrayIconSize().width);
			if (image == null) {
				return null;
			}
			TrayIcon made = new TrayIcon(image, "Death Plateau");
			made.setImageAutoSize(true);
			tray.add(made);
			icon = made;
			DevLog.log("NOTIFY", "system tray ready");
		} catch (AWTException error) {
			DevLog.log("NOTIFY", "the system tray refused the icon: " + error);
		} catch (Throwable error) {
			DevLog.log("NOTIFY", "no system tray: " + error);
		}
		return icon;
	}
}
