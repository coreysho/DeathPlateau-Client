package jagex2.client;

import deob.ObfuscatedName;
import jagex2.graphics.Pix32;
import jagex2.graphics.PixMap;
import lostcity.Branding;

import java.applet.Applet;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;

public class GameShell extends Applet implements Runnable, MouseListener, MouseMotionListener, MouseWheelListener, KeyListener, FocusListener, WindowListener {

	@ObfuscatedName("JWWAIQPI.g")
	public int deltime = 20;

	@ObfuscatedName("JWWAIQPI.h")
	public int mindel = 1;

	@ObfuscatedName("JWWAIQPI.i")
	public long[] otim = new long[10];

	@ObfuscatedName("JWWAIQPI.k")
	public boolean debug = false;

	@ObfuscatedName("JWWAIQPI.p")
	public Pix32[] temp = new Pix32[6];

	@ObfuscatedName("JWWAIQPI.r")
	public boolean redrawScreen = true;

	@ObfuscatedName("JWWAIQPI.s")
	public boolean hasFocus = true;

	@ObfuscatedName("JWWAIQPI.F")
	public int[] actionKey = new int[128];

	@ObfuscatedName("JWWAIQPI.G")
	public int[] keyQueue = new int[128];

	@ObfuscatedName("JWWAIQPI.f")
	public int state;

	@ObfuscatedName("JWWAIQPI.j")
	public int fps;

	@ObfuscatedName("JWWAIQPI.l")
	public int canvasWidth;

	@ObfuscatedName("JWWAIQPI.m")
	public int canvasHeight;

	@ObfuscatedName("JWWAIQPI.t")
	public int idleCycles;

	@ObfuscatedName("JWWAIQPI.u")
	public int mouseButton;

	@ObfuscatedName("JWWAIQPI.v")
	public int mouseX;

	@ObfuscatedName("JWWAIQPI.w")
	public int mouseY;

	@ObfuscatedName("JWWAIQPI.x")
	public int nextMouseClickButton;

	@ObfuscatedName("JWWAIQPI.y")
	public int nextMouseClickX;

	@ObfuscatedName("JWWAIQPI.z")
	public int nextMouseClickY;

	@ObfuscatedName("JWWAIQPI.B")
	public int mouseClickButton;

	@ObfuscatedName("JWWAIQPI.C")
	public int mouseClickX;

	@ObfuscatedName("JWWAIQPI.D")
	public int mouseClickY;

	@ObfuscatedName("JWWAIQPI.H")
	public int keyQueueReadPos;

	@ObfuscatedName("JWWAIQPI.I")
	public int keyQueueWritePos;

	@ObfuscatedName("JWWAIQPI.A")
	public long nextMouseClickTime;

	@ObfuscatedName("JWWAIQPI.E")
	public long mouseClickTime;

	@ObfuscatedName("JWWAIQPI.q")
	public ViewBox frame;

	@ObfuscatedName("JWWAIQPI.o")
	public PixMap drawArea;

	@ObfuscatedName("JWWAIQPI.n")
	public Graphics graphics;

	// --- QoL additions (Corey, 2026-09-01): key/mouse input for client-side quality-of-life
	// features (middle-mouse camera drag, scroll-wheel zoom, shift-click drop, Tab/Space/Escape
	// hotkeys). See Client.java for where these get consumed.
	public static final int KEY_SHIFT = 6;
	public static final int KEY_ESCAPE = 7;
	// QoL: Alt is tracked as a held key for the ground item overlay. 11 because every lower value is
	// taken - 1-4 are the arrow keys, 5 is Ctrl (already sent to the server as the run flag), 6 and 7
	// are above, and 8/9/10 are backspace/tab/enter. Alt reports CHAR_UNDEFINED as its key char, so
	// like Shift and Escape it only gets a value at all because the code is mapped below.
	public static final int KEY_ALT = 11;
	public boolean middleMouseDown;
	private int middleMouseLastX;
	private int middleMouseLastY;
	public int cameraDragDeltaX;
	public int cameraDragDeltaY;
	// What this side has already taken off BrowserInput's running totals - see pollBrowserCameraDrag().
	private int browserCameraLastX;
	private int browserCameraLastY;
	public int mouseScrollDelta;

	// Resizable mode. The canvas position of the mouse and of the last press, exactly as AWT gave
	// them. In fixed mode mouseX/mouseY and the click fields are these same numbers and nothing else
	// reads the raw ones. In resizable mode (remapMouse set) the events leave mouseX/mouseY alone
	// and the game thread fills them from these in mapInput(), because there a canvas point has to
	// be turned into the coordinates the rest of the client was written for - see Layout.
	public volatile boolean remapMouse;
	public volatile int rawMouseX = -1;
	public volatile int rawMouseY = -1;
	public int nextRawClickX;
	public int nextRawClickY;
	public int rawClickX;
	public int rawClickY;

	// A press is written by the AWT thread into the next* fields and copied out by the game thread,
	// which clears the button as it goes. Those are six separate writes and six separate reads: a
	// press that landed between the game thread reading nextMouseClickButton and clearing it had its
	// button wiped before any update() saw it, and the press it interrupted was left holding the new
	// press's coordinates. Both sides do it under this lock now, so a press is latched whole or not
	// at all. (It also gives the game thread a guaranteed-fresh read of fields the AWT thread wrote.)
	private final Object clickLock = new Object();

	// The press update() did not take, kept until draw() has had its look at it.
	//
	// The click latch above lives for exactly one update(), which is fine while there is one update
	// per frame: the game's own click handling all runs in update(), and the few readers that run in
	// draw() (Client's own F9 settings panel, the menu swapper, the ground item controls) see the
	// same press the update just saw. But when a frame takes longer than the 20ms tick the catch-up
	// loop in run() runs two or three updates per frame, and the press latched by the first of them
	// was cleared by the second - by the time draw() looked, the click was gone. At 1920x1080, where
	// a frame costs 35-60ms, that lost one click in two to two in three.
	//
	// So: whatever is still in the latch when update() returns is held here, and put back for the
	// one draw() at the end of the frame. Held, not re-latched - update() still sees each press
	// exactly once, so nothing can walk the player or use an item twice.
	private int heldClickButton;
	private int heldClickX;
	private int heldClickY;
	private long heldClickTime;
	private int heldRawClickX;
	private int heldRawClickY;

	@ObfuscatedName("JWWAIQPI.a(III)V")
	public void initApplication(int height, int width) {
		this.setPreferredSize(new Dimension(width, height));

		this.canvasWidth = width;
		this.canvasHeight = height;
		this.frame = new ViewBox(this.canvasHeight, this, this.canvasWidth);
		this.graphics = this.acquireGraphics();
		this.drawArea = new PixMap(this.canvasHeight, this.getBaseComponent(), this.canvasWidth);

		this.startThread(this, 1);
	}

	@ObfuscatedName("JWWAIQPI.b(III)V")
	public void initApplet(int width, int height) {
		this.setPreferredSize(new Dimension(width, height));

		this.canvasWidth = width;
		this.canvasHeight = height;
		this.graphics = this.acquireGraphics();
		this.drawArea = new PixMap(this.canvasHeight, this.getBaseComponent(), this.canvasWidth);

		this.startThread(this, 1);
	}

	public void run() {
		this.getBaseComponent().addMouseListener(this);
		this.getBaseComponent().addMouseMotionListener(this);
		this.getBaseComponent().addMouseWheelListener(this);
		this.getBaseComponent().addKeyListener(this);
		this.getBaseComponent().addFocusListener(this);
		// QoL: Tab is AWT's default focus-traversal key by default, so without this, pressing Tab
		// just shifts component focus and the keystroke never reaches keyPressed() at all - which
		// is why the Tab-to-reply-PM hotkey silently did nothing.
		this.getBaseComponent().setFocusTraversalKeysEnabled(false);

		if (this.frame != null) {
			this.frame.addWindowListener(this);
		}

		this.drawProgress(0, "Loading...");
		this.load();

		int opos = 0;
		int ratio = 256;
		int delta = 1;
		int count = 0;
		int intex = 0;

		for (int i = 0; i < 10; i++) {
			this.otim[i] = System.currentTimeMillis();
		}

		long ntime = System.currentTimeMillis();
		while (this.state >= 0) {
			if (this.state > 0) {
				this.state--;

				if (this.state == 0) {
					this.shutdown();
					return;
				}
			}

			// Given up on: keep the window alive and keep saying so, rather than leaving the loop
			// and letting the last frame sit there looking like the game. Once a second, because
			// an exposed or resized window would otherwise lose the message.
			if (this.crashed != null) {
				this.drawCrash();

				try {
					Thread.sleep(1000L);
				} catch (InterruptedException ignore) {
				}

				continue;
			}

			// whether this frame got through update() and draw() without throwing
			boolean clean = true;

			int lastRatio = ratio;
			int lastDelta = delta;

			ratio = 300;
			delta = 1;

			ntime = System.currentTimeMillis();

			if (this.otim[opos] == 0L) {
				ratio = lastRatio;
				delta = lastDelta;
			} else if (ntime > this.otim[opos]) {
				ratio = (int) ((long) (this.deltime * 2560) / (ntime - this.otim[opos]));
			}

			if (ratio < 25) {
				ratio = 25;
			}

			if (ratio > 256) {
				ratio = 256;
				delta = (int) ((long) this.deltime - (ntime - this.otim[opos]) / 10L);
			}

			if (delta > this.deltime) {
				delta = this.deltime;
			}

			this.otim[opos] = ntime;
			opos = (opos + 1) % 10;

			if (delta > 1) {
				for (int i = 0; i < 10; i++) {
					if (this.otim[i] != 0L) {
						this.otim[i] += delta;
					}
				}
			}

			if (delta < this.mindel) {
				delta = this.mindel;
			}

			try {
				Thread.sleep((long) delta);
			} catch (InterruptedException ignore) {
				intex++;
			}

			while (count < 256) {
				synchronized (this.clickLock) {
					this.mouseClickButton = this.nextMouseClickButton;
					this.mouseClickX = this.nextMouseClickX;
					this.mouseClickY = this.nextMouseClickY;
					this.mouseClickTime = this.nextMouseClickTime;
					this.rawClickX = this.nextRawClickX;
					this.rawClickY = this.nextRawClickY;
					this.nextMouseClickButton = 0;
				}

				this.mapInput();

				try {
					this.update();
				} catch (Throwable error) {
					clean = false;
					this.fault("update", error);
				}

				// Anything update() left in the latch is still unclaimed - hold it for draw(). The
				// readers that consume a click in update() zero the button as they take it, so a
				// press this update acted on for good is not held. See heldClickButton.
				if (this.mouseClickButton != 0) {
					this.heldClickButton = this.mouseClickButton;
					this.heldClickX = this.mouseClickX;
					this.heldClickY = this.mouseClickY;
					this.heldClickTime = this.mouseClickTime;
					this.heldRawClickX = this.rawClickX;
					this.heldRawClickY = this.rawClickY;
				}

				this.keyQueueReadPos = this.keyQueueWritePos;
				count += ratio;
			}

			count &= 0xFF;

			if (this.deltime > 0) {
				this.fps = ratio * 1000 / (this.deltime * 256);
			}

			// Give the held press back before mapInput(), which is what turns a raw canvas point
			// into the coordinates the client reads (resizable mode). With one update per frame
			// these are the values the latch already holds and this changes nothing.
			if (this.heldClickButton != 0) {
				this.mouseClickButton = this.heldClickButton;
				this.mouseClickX = this.heldClickX;
				this.mouseClickY = this.heldClickY;
				this.mouseClickTime = this.heldClickTime;
				this.rawClickX = this.heldRawClickX;
				this.rawClickY = this.heldRawClickY;
				this.heldClickButton = 0;
			}

			this.mapInput();

			try {
				this.draw();
			} catch (Throwable error) {
				clean = false;
				this.fault("draw", error);
			}

			// A frame that got all the way through puts the count back: what is being counted is a
			// fault that keeps happening, not a fault that happened once.
			if (clean) {
				this.faults = 0;
			}

			if (this.debug) {
				System.out.println("ntime:" + ntime);
				for (int i = 0; i < 10; i++) {
					int o = (opos - i - 1 + 20) % 10;
					System.out.println("otim" + o + ":" + this.otim[o]);
				}
				System.out.println("fps:" + this.fps + " ratio:" + ratio + " count:" + count);
				System.out.println("del:" + delta + " deltime:" + this.deltime + " mindel:" + this.mindel);
				System.out.println("intex:" + intex + " opos:" + opos);
				this.debug = false;
				intex = 0;
			}
		}

		if (this.state == -1) {
			this.shutdown();
		}
	}

	@ObfuscatedName("JWWAIQPI.a(Z)V")
	public void shutdown() {
		this.state = -2;
		this.unload();

		if (this.frame == null) {
			return;
		}

		try {
			Thread.sleep(1000L);
		} catch (Exception ignore) {
		}

		try {
			System.exit(0);
		} catch (Throwable ignore) {
		}
	}

	@ObfuscatedName("JWWAIQPI.a(BI)V")
	public void setFramerate(int fps) {
		this.deltime = 1000 / fps;
	}

	public void start() {
		if (this.state >= 0) {
			this.state = 0;
		}
	}

	public void stop() {
		if (this.state >= 0) {
			this.state = 4000 / this.deltime;
		}
	}

	public void destroy() {
		this.state = -1;

		try {
			Thread.sleep(10000L);
		} catch (Exception ignore) {
		}

		if (this.state == -1) {
			this.shutdown();
		}
	}

	public void update(Graphics g) {
		if (this.graphics == null) {
			this.graphics = g;
		}

		this.redrawScreen = true;
		this.refresh();
	}

	public void paint(Graphics g) {
		if (this.graphics == null) {
			this.graphics = g;
		}

		this.redrawScreen = true;
		this.refresh();
	}

	/**
	 * The middle-button drag a browser cannot give AWT, taken off {@link BrowserInput} and folded
	 * into the same deltas a real one produces. Called once a tick by Client.updateOrbitCamera,
	 * beside the code that consumes them, so there is one place that turns a drag into rotation.
	 *
	 * The page only adds to those totals, so the difference since the last look is exactly what has
	 * been dragged since - nothing is lost if the page writes while this runs. In a desktop client
	 * both totals stay 0 and this does nothing at all.
	 */
	public void pollBrowserCameraDrag() {
		int x = BrowserInput.cameraDragX;
		int y = BrowserInput.cameraDragY;
		if (x != this.browserCameraLastX || y != this.browserCameraLastY) {
			this.cameraDragDeltaX += x - this.browserCameraLastX;
			this.cameraDragDeltaY += y - this.browserCameraLastY;
			this.browserCameraLastX = x;
			this.browserCameraLastY = y;
		}
	}

	public void mousePressed(MouseEvent e) {
		int x = e.getX();
		int y = e.getY();

		this.idleCycles = 0;

		// QoL: middle-mouse-button camera drag. Track it separately and don't let it fall through
		// to the normal left/right click handling below (middle click isn't a game click). Checked
		// before the latch below rather than after, so a middle press cannot move the coordinates of
		// a left or right press the game thread has not read yet - that used to make the pending
		// click act wherever the middle button happened to go down.
		if (e.getButton() == MouseEvent.BUTTON2) {
			this.middleMouseDown = true;
			this.middleMouseLastX = x;
			this.middleMouseLastY = y;
			return;
		}

		int button;
		try {
			// Java >8 no longer uses "isMetaDown" for right clicks
			button = e.getButton() == MouseEvent.BUTTON3 ? 2 : 1;
		} catch (NoSuchMethodError ex) {
			button = e.isMetaDown() ? 2 : 1;
		}
		this.mouseButton = button;

		// One press, one latch - see clickLock. The button goes in last, as it always did: it is
		// what tells the game thread there is a press to take.
		synchronized (this.clickLock) {
			this.nextRawClickX = x;
			this.nextRawClickY = y;
			this.nextMouseClickX = x;
			this.nextMouseClickY = y;
			this.nextMouseClickTime = System.currentTimeMillis();
			this.nextMouseClickButton = button;
		}
	}

	public void mouseReleased(MouseEvent e) {
		this.idleCycles = 0;
		this.mouseButton = 0;
		if (e.getButton() == MouseEvent.BUTTON2) {
			this.middleMouseDown = false;
		}
	}

	public void mouseClicked(MouseEvent e) {
	}

	public void mouseEntered(MouseEvent e) {
	}

	public void mouseExited(MouseEvent e) {
		this.idleCycles = 0;
		this.rawMouseX = -1;
		this.rawMouseY = -1;
		if (!this.remapMouse) {
			this.mouseX = -1;
			this.mouseY = -1;
		}
	}

	public void mouseDragged(MouseEvent e) {
		int x = e.getX();
		int y = e.getY();

		this.idleCycles = 0;
		this.rawMouseX = x;
		this.rawMouseY = y;
		if (!this.remapMouse) {
			this.mouseX = x;
			this.mouseY = y;
		}

		// QoL: accumulate the drag delta while the middle mouse button is held, for camera rotation.
		// Client.java's updateOrbitCamera() consumes and resets this every game tick.
		if (this.middleMouseDown) {
			this.cameraDragDeltaX += x - this.middleMouseLastX;
			this.cameraDragDeltaY += y - this.middleMouseLastY;
			this.middleMouseLastX = x;
			this.middleMouseLastY = y;
		}
	}

	public void mouseMoved(MouseEvent e) {
		int x = e.getX();
		int y = e.getY();

		this.idleCycles = 0;
		this.rawMouseX = x;
		this.rawMouseY = y;
		if (!this.remapMouse) {
			this.mouseX = x;
			this.mouseY = y;
		}
	}

	// QoL: scroll wheel camera zoom. Client.java's updateOrbitCamera() consumes and resets this.
	public void mouseWheelMoved(MouseWheelEvent e) {
		this.idleCycles = 0;
		this.mouseScrollDelta += e.getWheelRotation();
	}

	public void keyPressed(KeyEvent e) {
		this.idleCycles = 0;

		int code = e.getKeyCode();
		int ch = e.getKeyChar();

		if (ch < 30) {
			ch = 0;
		}

		if (code == 37) {
			ch = 1;
		} else if (code == 39) {
			ch = 2;
		} else if (code == 38) {
			ch = 3;
		} else if (code == 40) {
			ch = 4;
		} else if (code == 17) {
			ch = 5;
		} else if (code == 8) {
			ch = 8;
		} else if (code == 127) {
			ch = 8;
		} else if (code == 9) {
			ch = 9;
		} else if (code == 10) {
			ch = 10;
		} else if (code >= 112 && code <= 123) {
			ch = code + 1008 - 112;
		} else if (code == 36) {
			ch = 1000;
		} else if (code == 35) {
			ch = 1001;
		} else if (code == 33) {
			ch = 1002;
		} else if (code == 34) {
			ch = 1003;
		} else if (code == 16) {
			// QoL: track Shift as a held key (for shift-click-drop)
			ch = KEY_SHIFT;
		} else if (code == 27) {
			// QoL: Escape closes the current interface
			ch = KEY_ESCAPE;
		} else if (code == 18) {
			// QoL: Alt held shows the ground item controls; double-tapped it toggles reveal
			ch = KEY_ALT;
		}

		if (ch > 0 && ch < 128) {
			this.actionKey[ch] = 1;
		}

		if (ch > 4) {
			this.keyQueue[this.keyQueueWritePos] = ch;
			this.keyQueueWritePos = this.keyQueueWritePos + 1 & 0x7F;
		}
	}

	public void keyReleased(KeyEvent e) {
		this.idleCycles = 0;

		int code = e.getKeyCode();
		char ch = e.getKeyChar();

		if (ch < 30) {
			ch = 0;
		}

		if (code == 37) {
			ch = 1;
		} else if (code == 39) {
			ch = 2;
		} else if (code == 38) {
			ch = 3;
		} else if (code == 40) {
			ch = 4;
		} else if (code == 17) {
			ch = 5;
		} else if (code == 8) {
			ch = '\b';
		} else if (code == 127) {
			ch = '\b';
		} else if (code == 9) {
			ch = '\t';
		} else if (code == 10) {
			ch = '\n';
		} else if (code == 16) {
			ch = KEY_SHIFT;
		} else if (code == 27) {
			ch = KEY_ESCAPE;
		} else if (code == 18) {
			ch = KEY_ALT;
		}

		if (ch > 0 && ch < 128) {
			this.actionKey[ch] = 0;
		}
	}

	public void keyTyped(KeyEvent e) {
	}

	@ObfuscatedName("JWWAIQPI.a(I)I")
	public int pollKey() {
		int key = -1;
		if (this.keyQueueWritePos != this.keyQueueReadPos) {
			key = this.keyQueue[this.keyQueueReadPos];
			this.keyQueueReadPos = this.keyQueueReadPos + 1 & 0x7F;
		}
		return key;
	}

	public void focusGained(FocusEvent e) {
		this.hasFocus = true;
		this.redrawScreen = true;
		this.refresh();
	}

	public void focusLost(FocusEvent e) {
		this.hasFocus = false;
		for (int i = 0; i < 128; i++) {
			this.actionKey[i] = 0;
		}
	}

	public void windowActivated(WindowEvent e) {
	}

	public void windowClosed(WindowEvent e) {
	}

	public void windowClosing(WindowEvent e) {
		this.destroy();
	}

	public void windowDeactivated(WindowEvent e) {
	}

	public void windowDeiconified(WindowEvent e) {
	}

	public void windowIconified(WindowEvent e) {
	}

	public void windowOpened(WindowEvent e) {
	}

	@ObfuscatedName("JWWAIQPI.a()V")
	public void load() {
	}

	/**
	 * Called on the game thread before every update() and draw(): where a subclass turns the raw
	 * canvas mouse (rawMouseX, rawClickX...) into mouseX/mouseY/mouseClickX/mouseClickY when it has
	 * set remapMouse. Does nothing otherwise, and nothing here by default.
	 */
	public void mapInput() {
	}

	/** The Graphics the game draws its frame on: the component's own, unless a subclass says otherwise. */
	public Graphics acquireGraphics() {
		return this.getBaseComponent().getGraphics();
	}

	@ObfuscatedName("JWWAIQPI.a(B)V")
	public void update() {
	}

	@ObfuscatedName("JWWAIQPI.b(I)V")
	public void unload() {
	}

	@ObfuscatedName("JWWAIQPI.c(I)V")
	public void draw() {
	}

	@ObfuscatedName("JWWAIQPI.b(B)V")
	public void refresh() {
	}

	@ObfuscatedName("JWWAIQPI.d(I)Ljava/awt/Component;")
	public Component getBaseComponent() {
		return this;
	}

	@ObfuscatedName("JWWAIQPI.a(Ljava/lang/Runnable;I)V")
	public void startThread(Runnable thread, int priority) {
		Thread t = new Thread(thread);
		t.start();
		t.setPriority(priority);
	}

	// ------------------------------------------------------------------ when a frame throws
	//
	// WHY THIS EXISTS (2026-11-01). update() and draw() were called bare. Either one throwing
	// ended the loop thread, and nothing above it caught anything - so the window stayed up
	// showing the last frame it painted, the game never moved again, and the client could not
	// say a word about it. It is indistinguishable from a scene loading slowly and never
	// finishing, which is exactly how it presented on 2026-09-29: a teleport to Zul-andra, a
	// stack trace going to a stderr nobody was reading, and a player left looking at a picture.
	//
	// A ONE-OFF IS SURVIVED, A REPEAT IS NOT. Most of what reaches here is one bad frame - a
	// packet that did not say what the handler expected, something half-built being drawn - and
	// the next frame is fine. Those are logged and skipped, which is strictly better than dying.
	// What must not happen is spinning for ever on a fault that comes back every frame, so after
	// MAX_FAULTS in a row with no clean frame between them the client stops and says so.

	/** Frames in a row in which update() or draw() threw. Any clean frame puts it back to zero. */
	private int faults;

	/** The fault it gave up on, or null while the game is running. */
	private Throwable crashed;

	/**
	 * How many frames in a row may throw before the client gives up.
	 *
	 * Ten rather than one because a single bad frame is common and recoverable, and ten rather
	 * than a hundred because at this frame rate a hundred is two seconds of a frozen picture
	 * before anybody is told anything.
	 */
	private static final int MAX_FAULTS = 10;

	private void fault(String where, Throwable error) {
		this.faults++;
		// stderr, because the launcher redirects it into ~/.deathplateau/client.log - which is
		// the whole reason there is anything to send afterwards.
		System.err.println("client: " + where + "() threw on frame " + this.faults + " in a row");
		error.printStackTrace();

		if (this.faults >= MAX_FAULTS && this.crashed == null) {
			this.crashed = error;
			System.err.println("client: giving up after " + MAX_FAULTS + " faulting frames in a row");
		}
	}

	/** One line naming the fault, for the player to quote. */
	private static String describe(Throwable error) {
		if (error == null) {
			return "unknown";
		}
		String text = error.getClass().getName();
		if (error.getMessage() != null) {
			text = text + ": " + error.getMessage();
		}
		StackTraceElement[] stack = error.getStackTrace();
		if (stack != null && stack.length > 0) {
			text = text + "  at " + stack[0];
		}
		return text;
	}

	/**
	 * Cuts a line down until it fits, measured rather than counted.
	 *
	 * A character count was wrong the first time it was looked at: a monospaced 12 is about seven
	 * pixels wide, so 110 characters is 770 and the line ran off the side of a 765-wide window.
	 * The window can also be wider than that, and then a fixed count throws away text that fitted.
	 */
	private String fit(String text, Font font, int width) {
		FontMetrics metrics = this.getBaseComponent().getFontMetrics(font);
		if (metrics.stringWidth(text) <= width) {
			return text;
		}
		String tail = "...";
		int room = width - metrics.stringWidth(tail);
		int end = text.length();
		while (end > 0 && metrics.stringWidth(text.substring(0, end)) > room) {
			end--;
		}
		return text.substring(0, end) + tail;
	}

	/**
	 * The stopped screen, drawn with plain AWT on purpose.
	 *
	 * Nothing here touches the game's own renderer, its fonts or its sprites. Whatever broke, it
	 * may well have been one of those, and a message that needs the broken thing to draw itself
	 * is no message at all.
	 */
	private void drawCrash() {
		try {
			Graphics g = this.acquireGraphics();
			if (g == null) {
				return;
			}
			int w = Math.max(765, this.canvasWidth);
			int h = Math.max(503, this.canvasHeight);
			g.setColor(Color.black);
			g.fillRect(0, 0, w, h);

			g.setFont(new Font("Helvetica", Font.BOLD, 16));
			g.setColor(Color.yellow);
			g.drawString(Branding.SERVER_NAME + " has stopped.", 30, 40);

			g.setFont(new Font("Helvetica", Font.PLAIN, 13));
			g.setColor(Color.white);
			g.drawString("It is not loading and it is not lagging - waiting will not help.", 30, 80);
			g.drawString("Close this window and start the launcher again; nothing has been lost.", 30, 105);
			g.drawString("What went wrong was written down in client.log, in your .deathplateau folder.", 30, 145);
			g.drawString("Please send that file with your report. It begins with the line below.", 30, 170);

			Font mono = new Font("Monospaced", Font.PLAIN, 12);
			g.setFont(mono);
			g.setColor(new Color(Branding.SLOGAN_COLOUR));
			g.drawString(this.fit(describe(this.crashed), mono, w - 60), 30, 210);
		} catch (Throwable ignored) {
			// The window has gone, or AWT will not give us a Graphics. There is nothing left to
			// try and nowhere left to say so.
		}
	}

	@ObfuscatedName("JWWAIQPI.a(IZLjava/lang/String;)V")
	public void drawProgress(int percent, String message) {
		while (this.graphics == null) {
			this.graphics = this.acquireGraphics();

			try {
				this.getBaseComponent().repaint();
			} catch (Exception ignore) {
			}

			try {
				Thread.sleep(1000L);
			} catch (Exception ignore) {
			}
		}

		Font bold = new Font("Helvetica", Font.BOLD, 13);
		FontMetrics boldMetrics = this.getBaseComponent().getFontMetrics(bold);

		Font plain = new Font("Helvetica", Font.PLAIN, 13);
		FontMetrics plainMetrics = this.getBaseComponent().getFontMetrics(plain);

		if (this.redrawScreen) {
			this.graphics.setColor(Color.black);
			this.graphics.fillRect(0, 0, this.canvasWidth, this.canvasHeight);
			this.redrawScreen = false;
		}

		Color background = new Color(140, 17, 17);

		int y = this.canvasHeight / 2 - 18;
		this.graphics.setColor(background);
		this.graphics.drawRect(this.canvasWidth / 2 - 152, y, 304, 34);
		this.graphics.fillRect(this.canvasWidth / 2 - 150, y + 2, percent * 3, 30);

		this.graphics.setColor(Color.black);
		this.graphics.fillRect(percent * 3 + (this.canvasWidth / 2 - 150), y + 2, 300 - percent * 3, 30);

		this.graphics.setFont(bold);
		this.graphics.setColor(Color.white);
		this.graphics.drawString(message, (this.canvasWidth - boldMetrics.stringWidth(message)) / 2, y + 22);
	}
}
