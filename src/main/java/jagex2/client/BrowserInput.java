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

	private BrowserInput() {
	}
}
