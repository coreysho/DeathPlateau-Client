package sign;

import javax.sound.midi.MidiSystem;
import javax.sound.sampled.*;
import java.applet.Applet;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URL;

public class signlink implements Runnable {

	public static int storeid = 32;

	public static RandomAccessFile cache_dat = null;

	public static RandomAccessFile[] cache_idx = new RandomAccessFile[5];

	public static Applet mainapp = null;

	public static Socket socket = null;

	public static int threadreqpri = 1;

	public static Runnable threadreq = null;

	public static String dnsreq = null;

	public static String dns = null;

	public static String urlreq = null;

	public static DataInputStream urlstream = null;

	public static String savereq = null;

	public static byte[] savebuf = null;

	public static String midi = "none";

	public static boolean reporterror = true;

	public static String errorname = "";

	public static final int clientversion = 377;

	public static int midifade;

	public static int midipos;

	public static int midivol = 96;

	public static int savelen;

	public static int socketreq;

	public static int threadliveid;

	public static int uid;

	public static int wavevol = 50;

	public static InetAddress socketip;

	public static boolean active;

	public static boolean midiplay;

	public static boolean sunjava;

	public static void startpriv(InetAddress arg0) {
		threadliveid = (int) (Math.random() * 9.9999999E7D);
		if (active) {
			try {
				Thread.sleep(500L);
			} catch (Exception var3) {
			}
			active = false;
		}
		socketreq = 0;
		threadreq = null;
		dnsreq = null;
		savereq = null;
		urlreq = null;
		socketip = arg0;
		Thread var1 = new Thread(new signlink());
		var1.setDaemon(true);
		var1.start();
		while (!active) {
			try {
				Thread.sleep(50L);
			} catch (Exception var2) {
			}
		}
	}

	public void run() {
		active = true;
		String var1 = findcachedir();
		uid = getuid(var1);
		try {
			midiPlayer = new MidiPlayer();
		} catch (Exception ex) {
		}
		try {
			// THE CACHE IS NOT ALLOWED TO BE BIGGER THAN THE GAME.
			//
			// This deleted the whole file store at startup once it passed FIFTY MEGABYTES, which is
			// the number the original client shipped with when the game it cached was smaller than
			// that. This build's data/pack is 86.9 MB. So every player whose cache had filled up was
			// having it wiped on EVERY LAUNCH, and then re-downloading the entire game as they walked
			// around in it: models arriving while you look at them, animation frames arriving while
			// they play.
			//
			// That is the Zulrah flicker. The boss brought 477 newly imported animation frames and
			// three new models with it, so a fight is where a re-streaming cache shows worst - the
			// npc is not drawn at all while a model is in flight (NpcType.method475 returns null) and
			// draws in its base pose while a frame is (Model.applyTransform gives up), and the owner
			// saw both as blinking. It is also why the Kalphite Queen looked fine: whatever had
			// already arrived this session stayed arrived.
			//
			// 512MB keeps the guard - a cache that runs away is still caught - while leaving room for
			// a build several times this one. The size is the only thing that changes; a cache under
			// the cap is kept exactly as before.
			File var2 = new File(var1 + "main_file_cache.dat");
			if (var2.exists() && var2.length() > 536870912L) {
				var2.delete();
			}
			cache_dat = new RandomAccessFile(var1 + "main_file_cache.dat", "rw");
			for (int var3 = 0; var3 < 5; var3++) {
				cache_idx[var3] = new RandomAccessFile(var1 + "main_file_cache.idx" + var3, "rw");
			}
		} catch (Exception var13) {
			var13.printStackTrace();
		}
		int var5 = threadliveid;
		while (threadliveid == var5) {
			if (socketreq != 0) {
				try {
					socket = new Socket(socketip, socketreq);
				} catch (Exception var8) {
					socket = null;
				}
				socketreq = 0;
			} else if (threadreq != null) {
				Thread var6 = new Thread(threadreq);
				var6.setDaemon(true);
				var6.start();
				var6.setPriority(threadreqpri);
				threadreq = null;
			} else if (dnsreq != null) {
				try {
					dns = InetAddress.getByName(dnsreq).getHostName();
				} catch (Exception var12) {
					dns = "unknown";
				}
				dnsreq = null;
			} else if (savereq != null) {
				if (savebuf != null) {
					try {
						FileOutputStream var7 = new FileOutputStream(var1 + savereq);
						var7.write(savebuf, 0, savelen);
						var7.close();
					} catch (Exception var11) {
					}
				}
				if (midiplay) {
					midi = var1 + savereq;
					midiplay = false;
				}
				savereq = null;
			} else if (urlreq != null) {
				try {
					urlstream = new DataInputStream((new URL(mainapp.getCodeBase(), urlreq)).openStream());
				} catch (Exception var10) {
					urlstream = null;
				}
				urlreq = null;
			}
			audioLoop();
			try {
				Thread.sleep(50L);
			} catch (Exception var9) {
			}
		}
	}

	public static String findcachedir() {
		// WHERE THE GAME KEEPS ITS CACHE. In a browser none of the paths below exist and the one place
		// that can be written is CheerpJ's /files, which it persists in the browser's own storage - so
		// the page names it (lostcity.cachedir) and the client writes its file store there. Without it
		// findcachedir returns null, nothing is ever cached, and every visit downloads every animation
		// and model again from the first byte.
		String given = System.getProperty("lostcity.cachedir");
		if (given != null) {
			try {
				File dir = new File(given);
				if (dir.exists() || dir.mkdirs()) {
					return given.endsWith("/") ? given : given + "/";
				}
			} catch (Exception ignore) {
			}
		}
		String[] var0 = new String[] { "c:/windows/", "c:/winnt/", "d:/windows/", "d:/winnt/", "e:/windows/", "e:/winnt/", "f:/windows/", "f:/winnt/", "c:/", "~/", "/tmp/", "", "c:/rscache", "/rscache" };
		if (storeid < 32 || storeid > 34) {
			storeid = 32;
		}
		String var1 = ".file_store_" + storeid;
		for (int var2 = 0; var2 < var0.length; var2++) {
			try {
				String var3 = var0[var2];
				if (var3.length() > 0) {
					File var4 = new File(var3);
					if (!var4.exists()) {
						continue;
					}
				}
				File var5 = new File(var3 + var1);
				if (var5.exists() || var5.mkdir()) {
					return var3 + var1 + "/";
				}
			} catch (Exception var6) {
			}
		}
		return null;
	}

	public static int getuid(String arg0) {
		try {
			File var1 = new File(arg0 + "uid.dat");
			if (!var1.exists() || var1.length() < 4L) {
				DataOutputStream var2 = new DataOutputStream(new FileOutputStream(arg0 + "uid.dat"));
				var2.writeInt((int) (Math.random() * 9.9999999E7D));
				var2.close();
			}
		} catch (Exception var6) {
		}
		try {
			DataInputStream var3 = new DataInputStream(new FileInputStream(arg0 + "uid.dat"));
			int var4 = var3.readInt();
			var3.close();
			return var4 + 1;
		} catch (Exception var5) {
			return 0;
		}
	}

	public static synchronized Socket opensocket(int arg0) throws IOException {
		socketreq = arg0;
		while (socketreq != 0) {
			try {
				Thread.sleep(50L);
			} catch (Exception var1) {
			}
		}
		if (socket == null) {
			throw new IOException("could not open socket");
		}
		return socket;
	}

	public static synchronized DataInputStream openurl(String arg0) throws IOException {
		urlreq = arg0;
		while (urlreq != null) {
			try {
				Thread.sleep(50L);
			} catch (Exception var1) {
			}
		}
		if (urlstream == null) {
			throw new IOException("could not open: " + arg0);
		}
		return urlstream;
	}

	public static synchronized void dnslookup(String arg0) {
		dns = arg0;
		dnsreq = arg0;
	}

	public static synchronized void startthread(Runnable arg0, int arg1) {
		threadreqpri = arg1;
		threadreq = arg0;
	}

	// SOUND EFFECTS PLAY AT ONCE, AND FROM MEMORY (2026-11-02).
	//
	// WHAT THEY USED TO DO. A sound went through the same savereq/savebuf machinery as a midi: the
	// signlink thread wrote it to one of five rotating files on disk, then played it back on that
	// SAME THREAD, synchronously, writing the whole wave into a SourceDataLine and draining it
	// before returning. So one sound at a time, and while it played the signlink thread could not
	// open a socket, resolve a name, start a thread or accept another sound.
	//
	// That is three separate ways to lose a sound, and they were all firing:
	//
	//   - wavesave refused outright while a save was pending, so anything asked for during a sound
	//     was dropped on the floor;
	//   - Client.updateAudio only played a sound if it would outlast the one already going, so a
	//     short sound landing during a long one was dropped as well;
	//   - and five rotating filenames mean the sixth sound overwrites the file the player thread
	//     may still be reading, which truncates it.
	//
	// Reported as "KBD sounds are getting cut short" and, on a two-tick blowpipe, hearing a
	// monster's defend sound twice with the first one cut off.
	//
	// Now a sound is a byte[] on a queue and there are WAVE_VOICES of them playing at once, each on
	// its own line, so they MIX the way they are supposed to rather than taking turns. Nothing
	// touches the disk and nothing blocks the signlink thread. Midi is untouched: it still goes
	// through savereq, because exactly one piece of music plays at a time and that is correct.
	private static final int WAVE_VOICES = 4;
	private static final java.util.concurrent.BlockingQueue<byte[]> wavequeue = new java.util.concurrent.ArrayBlockingQueue<byte[]>(24);
	private static byte[] lastwave = null;
	private static boolean wavevoicesstarted = false;

	public static synchronized boolean wavesave(byte[] arg0, int arg1) {
		if (arg0 == null || arg1 <= 0 || arg1 > 2000000) {
			return false;
		}
		// Copied because the caller reuses its buffer, and a voice may still be reading this one.
		byte[] wav = new byte[arg1];
		System.arraycopy(arg0, 0, wav, 0, arg1);
		lastwave = wav;
		return wavequeue.offer(wav) | startwavevoices();
	}

	public static synchronized boolean wavereplay() {
		if (lastwave == null) {
			return false;
		}
		return wavequeue.offer(lastwave) | startwavevoices();
	}

	/** Always true: a full queue means two dozen sounds are already waiting, which is a storm, and
	 *  dropping the newest is better than growing without limit. The client only needs to know the
	 *  sound was accepted, and the alternative it takes on false is to ask again next frame. */
	private static synchronized boolean startwavevoices() {
		if (wavevoicesstarted) {
			return true;
		}
		wavevoicesstarted = true;
		for (int i = 0; i < WAVE_VOICES; i++) {
			Thread voice = new Thread(new Runnable() {
				public void run() {
					while (true) {
						byte[] wav;
						try {
							wav = wavequeue.take();
						} catch (InterruptedException stop) {
							return;
						}
						playwave(wav);
					}
				}
			});
			voice.setDaemon(true);
			voice.setPriority(Thread.MIN_PRIORITY);
			voice.start();
		}
		return true;
	}

	/** One sound on one line. Everything here is per-call, so two voices never share state. */
	private static void playwave(byte[] wav) {
		AudioInputStream in = null;
		SourceDataLine line = null;
		try {
			in = AudioSystem.getAudioInputStream(new ByteArrayInputStream(wav));
			AudioFormat format = in.getFormat();
			line = (SourceDataLine) AudioSystem.getLine(new DataLine.Info(SourceDataLine.class, format));
			line.open(format);
			// THE VOLUME SLIDER DID NOTHING. wavevol is 128/96/64/32 for the four levels the
			// Options tab offers and was written by setWaveVolume and read by absolutely nothing -
			// so sound effects played at full volume whatever it was set to. Decibels, because
			// MASTER_GAIN is in decibels and a linear fraction of it is not a linear loudness.
			if (line.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
				FloatControl gain = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
				int vol = wavevol < 0 ? 0 : (wavevol > 128 ? 128 : wavevol);
				float db = vol == 0 ? gain.getMinimum() : (float) (20.0 * Math.log10((double) vol / 128.0));
				if (db < gain.getMinimum()) {
					db = gain.getMinimum();
				}
				if (db > gain.getMaximum()) {
					db = gain.getMaximum();
				}
				gain.setValue(db);
			}
			line.start();
			byte[] buf = new byte[8192];
			int n;
			while ((n = in.read(buf, 0, buf.length)) >= 0) {
				line.write(buf, 0, n);
			}
			line.drain();
		} catch (Throwable ignore) {
			// A machine with no mixer, or one line too many: the game carries on without the sound.
		} finally {
			if (line != null) {
				try {
					line.close();
				} catch (Throwable ignore) {
				}
			}
			if (in != null) {
				try {
					in.close();
				} catch (Throwable ignore) {
				}
			}
		}
	}

	public static synchronized void midisave(byte[] arg0, int arg1) {
		if (arg1 > 2000000 || savereq != null) {
			return;
		}
		midipos = (midipos + 1) % 5;
		savelen = arg1;
		savebuf = arg0;
		midiplay = true;
		savereq = "jingle" + midipos + ".mid";
	}

	public static void reporterror(String arg0) {
		if (!reporterror || !active) {
			return;
		}
		System.out.println("Error: " + arg0);
		try {
			String var1 = arg0.replace(':', '_');
			String var2 = var1.replace('@', '_');
			String var3 = var2.replace('&', '_');
			String var4 = var3.replace('#', '_');
			DataInputStream var5 = openurl("reporterror" + 377 + ".cgi?error=" + errorname + " " + var4);
			var5.readLine();
			var5.close();
		} catch (IOException var6) {
		}
	}

	private MidiPlayer midiPlayer;
	public boolean midiFadingIn = false;
	public boolean midiFadingOut = false;
	public int midiFadeVol = 0;
	public void playMidi(String music) {
		if (midiFadingOut) {
			return;
		} else if (!midiFadingIn && midifade != 0 && midiPlayer.running()) {
			midiFadingOut = true;
			midiFadeVol = midivol;
			return;
		}

		try {
			if (midifade != 0 && midiFadingIn) {
				midiFadingOut = false;
				midiFadeVol = 0;
				midiPlayer.play(MidiSystem.getSequence(new File(music)), midifade, midiFadeVol);
			} else {
				midiPlayer.play(MidiSystem.getSequence(new File(music)), midifade, midivol);
			}
		} catch (Exception ignore) {
		}
	}

	// adapted from play_members.html's JS loop
	private void audioLoop() {
		if (midiFadingIn) {
			midiFadeVol += 8;
			if (midiFadeVol > midivol) {
				midiFadeVol = midivol;
			}
			midiPlayer.setVolume(0, midiFadeVol);
			if (midiFadeVol == midivol) {
				midiFadingIn = false;
			}
		} else if (midiFadingOut) {
			midiFadeVol -= 8;
			if (midiFadeVol < 0) {
				midiFadeVol = 0;
			}
			midiPlayer.setVolume(0, midiFadeVol);
			if (midiFadeVol == 0) {
				midiFadingOut = false;
				midiFadingIn = true;
			}
		}

		if (!midi.equals("none")) {
			if (midi.equals("stop")) {
				midiPlayer.stop();
			} else if (midi.equals("voladjust")) {
				midiPlayer.setVolume(0, midivol);
			} else {
				playMidi(midi);
			}

			if (!midiFadingOut) {
				midi = "none";
			}
		}

	}
}
