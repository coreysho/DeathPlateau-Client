/*
 * Headless test for the sound effect path.
 *
 * WHY THIS EXISTS. "KBD sounds are getting cut short", and on a two-tick blowpipe a monster's
 * defend sound heard twice with the first one cut off. One path, three ways to lose a sound:
 *
 *   - signlink wrote every wave to one of five rotating files and played it back ON THE SIGNLINK
 *     THREAD, synchronously, draining the line before returning. One sound at a time, and while
 *     it played that thread could not open a socket, resolve a name or accept another sound.
 *   - wavesave refused outright while a save was pending, so anything asked for during a sound
 *     was dropped.
 *   - Client.updateAudio played a new sound only if it would OUTLAST the one already going, so a
 *     short sound landing inside a long one was dropped too.
 *
 * And a fourth thing that was simply never wired: wavevol, the Options tab's sound slider, was
 * written by setWaveVolume and read by nothing at all.
 *
 * The first of those is the one that can be tested without ears, and it is tested for real rather
 * than read out of the source: two sounds are handed over back to back and both must be accepted.
 * The old code returned false for the second.
 */
package jagex2.client;

import sign.signlink;

public class SoundTest {

	static int fails;

	static void check(boolean ok, String what) {
		System.out.println((ok ? "  ok   " : "FAIL   ") + what);
		if (!ok) {
			fails++;
		}
	}

	public static void main(String[] args) {
		System.out.println("1. a second sound is accepted while the first is still going");
		// Not a real wave - nothing here reaches a mixer, and on a machine with no audio device
		// playwave swallows the failure by design. What is under test is the handover.
		byte[] first = new byte[2048];
		byte[] second = new byte[4096];
		boolean a = signlink.wavesave(first, first.length);
		boolean b = signlink.wavesave(second, second.length);
		check(a, "the first sound is taken");
		check(b, "and so is the second, immediately after");
		check(signlink.wavereplay(), "a replay of the last one is taken too");

		System.out.println();
		System.out.println("2. and nonsense is still refused");
		check(!signlink.wavesave(null, 10), "a null buffer");
		check(!signlink.wavesave(first, 0), "an empty sound");
		check(!signlink.wavesave(first, 2000001), "something absurdly long");

		System.out.println();
		System.out.println("3. the wave path does not go through the disk or the signlink thread");
		String sl = read("src/main/java/sign/signlink.java");
		// savereq is the midi machinery. Exactly one piece of music plays at a time and that is
		// correct; a sound effect sharing it is what serialised them.
		int save = sl.indexOf("public static synchronized boolean wavesave");
		String body = save < 0 ? "" : sl.substring(save, Math.min(sl.length(), save + 700));
		check(body.indexOf("savereq") < 0, "wavesave does not touch savereq");
		check(body.indexOf("System.arraycopy") >= 0, "it copies the buffer, which the caller reuses");
		check(sl.indexOf("audioLoop") >= 0 && !blockingWaveInAudioLoop(sl), "audioLoop no longer plays a wave on the signlink thread");
		check(sl.indexOf("WAVE_VOICES") >= 0, "there is a voice count");
		check(sl.indexOf("ArrayBlockingQueue") >= 0, "and a queue behind it");
		check(count(sl, "wavepos") == 0 && count(sl, "waveplay") == 0,
			"and the five rotating sound files are gone, so nothing can overwrite one mid-read");

		System.out.println();
		System.out.println("4. the volume slider reaches the line");
		// 128 / 96 / 64 / 32 are what Client.setWaveVolume passes for the four levels.
		check(count(sl, "wavevol") >= 2, "wavevol is read, not just written: " + count(sl, "wavevol") + " mentions");
		check(sl.indexOf("MASTER_GAIN") >= 0, "as a gain on the line");

		System.out.println();
		System.out.println("5. a short sound is no longer dropped for landing inside a long one");
		String cl = read("src/main/java/jagex2/client/Client.java");
		check(cl.indexOf("> (long) (this.lastWaveLength / 22) + this.lastWaveStartTime") < 0,
			"updateAudio has no outlast guard");
		check(cl.indexOf("this.saveWave(var4.pos, var4.data)") >= 0, "and still hands the wave over");

		System.out.println();
		System.out.println(fails == 0 ? "ALL PASS" : fails + " FAILED");
		System.exit(fails == 0 ? 0 : 1);
	}

	/** True if audioLoop still opens a line itself - the blocking playback that was the problem. */
	static boolean blockingWaveInAudioLoop(String src) {
		int at = src.indexOf("private void audioLoop()");
		if (at < 0) {
			return false;
		}
		int end = src.indexOf("\n\t}", at);
		String body = src.substring(at, end < 0 ? src.length() : end);
		return body.indexOf("SourceDataLine") >= 0;
	}

	static int count(String haystack, String needle) {
		int n = 0;
		for (int at = haystack.indexOf(needle); at >= 0; at = haystack.indexOf(needle, at + 1)) {
			n++;
		}
		return n;
	}

	static String read(String path) {
		try {
			return new String(java.nio.file.Files.readAllBytes(
				new java.io.File(System.getProperty("dp.root", "."), path).toPath()), "UTF-8");
		} catch (Throwable missing) {
			check(false, "cannot read " + path + " (" + missing + ")");
			return "";
		}
	}
}
