package jagex2.client;

/**
 * WHAT THE BROWSER CANNOT HAND TO AWT (2026-10-07).
 *
 * CheerpJ never delivers a middle-button PRESS. Measured against the real runtime (4.3): a
 * pointerdown with button 0 arrives as AWT BUTTON1 and one with button 2 as BUTTON3, but button 1
 * - the wheel - is dropped on the way in. The release arrives (BUTTON2), and nothing else does: no
 * press, and no BUTTON2_DOWN_MASK on the moves in between. So GameShell.middleMouseDown can never
 * become true in a browser, no drag delta is ever accumulated, and middle-drag camera rotation -
 * which is how most people turn the camera - silently does nothing there.
 *
 * Neither side of that is ours to fix, so the page does the watching instead: web.ts listens for
 * the middle button itself and writes the drag here, and {@link GameShell#pollBrowserCameraDrag()}
 * folds it into the same deltas a real middle-drag produces. JavaScript can reach a static field
 * of a running CheerpJ program (through the library handle every native is passed) even while the
 * game loop is running, which a method call cannot be: a call is refused with "Java code still
 * running", a field write is just memory.
 *
 * RUNNING TOTALS, NOT DELTAS. The page cannot be stopped from writing between this side's read and
 * its clear, so anything cleared here could throw away a drag that arrived in that window - and a
 * dropped drag is a camera that sticks mid-turn. The page only ever adds to these; the game thread
 * keeps what it last saw and takes the difference. Nothing is lost and nothing is written twice.
 *
 * A desktop client never touches them: no page, no writes, both zero for ever, and the real
 * middle-button path in GameShell is untouched.
 */
public class BrowserInput {

	/** How far the middle button has been dragged, in pixels, since the page was loaded. */
	public static volatile int cameraDragX;

	public static volatile int cameraDragY;

	// ------------------------------------------------------------------ the phone's keyboard

	/**
	 * AND A PHONE HAS NO KEYS AT ALL (2026-11-16).
	 *
	 * The game is a canvas, and a phone raises its keyboard for a focused HTML input and for
	 * nothing else, so on a touch device there is no way to type a username - the one thing that
	 * has to happen before anything else can. The page therefore keeps a real {@code <input>} out
	 * of sight, lets the phone put its keyboard up against that, and sends what is typed here.
	 *
	 * IT CANNOT BE DONE WITH A SYNTHETIC EVENT. Measured against the live page, 2026-11-16: a
	 * {@code new KeyboardEvent('keydown', ...)} dispatched at the game canvas, at document, at
	 * window and at body, for three different characters, changed nothing at all on the login
	 * screen - while the very same characters typed as real key events landed in the username
	 * field. CheerpJ does not act on untrusted events, so every keystroke from the page has to
	 * cross as a field write, the same way the camera drag does.
	 *
	 * A HANDSHAKE, NOT A RUNNING TOTAL. The camera drag can be a total because a lost drag is only
	 * a slower turn; a lost keystroke is a letter missing from a password. So this is one key at a
	 * time, acknowledged: the page writes {@link #typedKey} and then raises {@link #typedSeq}, the
	 * game thread takes the key and raises {@link #typedAck}, and the page sends the next one only
	 * once the two agree. It keeps its own queue meanwhile, so a paste or an autofill is delivered
	 * in order rather than all but its last character being overwritten. At one key per frame that
	 * is fifty a second, which is faster than anyone types.
	 *
	 * A desktop client never touches these: no page, no writes, and the real AWT key path in
	 * GameShell is untouched.
	 */
	public static volatile int typedKey;

	/** Raised by the page once {@link #typedKey} holds a key the game has not been offered yet. */
	public static volatile int typedSeq;

	/** Raised by the game thread once it has taken that key. */
	public static volatile int typedAck;

	// ------------------------------------------------------------------ and no second button

	/**
	 * AND NO RIGHT BUTTON EITHER (2026-11-16).
	 *
	 * Half of this game is the right-click menu, and a finger has no second button. CheerpJ 4.3
	 * added long-press, but it delivers it as a mouse HOVER - which is what a Swing menu bar wants
	 * and not what this is: 377 opens its menu on a right PRESS, and no amount of hovering will
	 * produce one.
	 *
	 * So the page watches for the press itself - held, and not dragged - and sends where it
	 * happened. {@link GameShell#pollBrowserTap()} then latches it exactly as a real right press
	 * would, so the menu opens where the finger is and everything downstream is the ordinary path.
	 *
	 * The point is in the game canvas's own pixels, which is what AWT would have reported. When the
	 * frame is being drawn scaled (a phone), mapInput scales it back with every other mouse
	 * coordinate - there is nothing special for this to do about it.
	 *
	 * Acknowledged like the keyboard: a dropped menu would be a click that did nothing, which is
	 * worse than a slow one.
	 */
	public static volatile int tapX;

	public static volatile int tapY;

	/** Raised by the page once tapX/tapY hold a long press the game has not been offered yet. */
	public static volatile int tapSeq;

	/** Raised by the game thread once it has taken that press. */
	public static volatile int tapAck;

	private BrowserInput() {
	}
}
