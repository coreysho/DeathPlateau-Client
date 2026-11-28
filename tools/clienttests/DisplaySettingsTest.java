import jagex2.client.DisplaySettings;

/**
 * One question per JVM, because DisplaySettings loads its file once into static fields and the
 * whole point of these cases is what it does on a FRESH start. The runner sets user.home to a
 * temp folder, puts a display.properties in it or does not, and reads the line back.
 *
 * argv[0] names what to do:
 *   read          - print the value smoothScale() settles on
 *   set-on-read   - turn it on, then print what a reload of the saved file gives
 */
public final class DisplaySettingsTest {
	public static void main(String[] args) {
		String what = args.length > 0 ? args[0] : "read";
		if (what.equals("set-on-read")) {
			// Goes through the setter, so it is written to disk the way the panel's switch writes it.
			DisplaySettings.setSmoothScale(true);
		}
		System.out.println("smoothscale=" + DisplaySettings.smoothScale());
		System.out.println("gamescale=" + DisplaySettings.gameScale());
	}
}
