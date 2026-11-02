/*
 * Headless test for the login screen's hit tests.
 *
 * WHAT THIS GUARDS. Every control on this screen is drawn by one set of constants and clicked
 * through another function, and when those two disagree the screen still looks perfect - the
 * button is in the right place, it just does not answer. That is not hypothetical in this
 * client: the title music toggle shipped nineteen pixels out, drawn on the button and clickable
 * on bare panel below it, because the box is laid out in one coordinate space and the clicks
 * arrive in another.
 *
 * So these check the two against each other at the edges, where an off-by-one lives, and they
 * check the one ordering rule that is invisible from the drawing: the x that removes an account
 * sits INSIDE the card that signs into it, so it has to be tested first. Tested the other way
 * round, every attempt to forget an account logs into it instead - which is the worst possible
 * failure on this screen, because the player wanted that account gone.
 *
 * The row geometry is checked at several counts because it is centred: the cards move every time
 * an account is added or removed, so a hit test that happened to agree at three would not at one.
 */
package jagex2.client;

public class TitleScreenTest {

	static int fails;

	static void check(boolean ok, String what) {
		System.out.println((ok ? "  ok   " : "FAIL   ") + what);
		if (!ok) {
			fails++;
		}
	}

	public static void main(String[] args) {
		System.out.println("1. the two fields");
		fieldTests();
		System.out.println();
		System.out.println("2. the button and the tick box");
		buttonTests();
		System.out.println();
		System.out.println("3. the row of saved accounts");
		rowTests();
		System.out.println();
		System.out.println("4. the x is reachable, and is tested before the card it sits in");
		forgetTests();
		System.out.println();
		System.out.println("5. nothing claims a pixel that belongs to nothing");
		emptyTests();
		System.out.println();
		System.out.println("6. the player count, which the client does not always have");
		footerTests();
		System.out.println();
		System.out.println("7. the login server's reply, which is two lines and used to be one");
		messageTests();
		System.out.println();
		System.out.println("8. the two lines between the button and the saved accounts");
		bandTests();

		System.out.println();
		if (fails == 0) {
			System.out.println("all checks passed");
		} else {
			System.out.println(fails + " CHECK(S) FAILED");
		}
		System.exit(fails == 0 ? 0 : 1);
	}

	static final int CX = TitleScreen.W / 2;

	static void fieldTests() {
		check(TitleScreen.fieldAt(CX, 180) == 0, "the top field answers as the username");
		check(TitleScreen.fieldAt(CX, 240) == 1, "the one below it as the password");
		check(TitleScreen.fieldAt(CX, 120) == -1, "nothing above them");
		check(TitleScreen.fieldAt(CX, 310) == -1, "nothing below them");
		check(TitleScreen.fieldAt(CX, 224) == -1, "nor in the gap between them");

		// The form is 296 wide and centred; a click past its edge is not a click in the field.
		check(TitleScreen.fieldAt(CX - 200, 180) == -1, "nor well left of the form");
		check(TitleScreen.fieldAt(CX + 200, 180) == -1, "nor well right of it");
		check(TitleScreen.fieldAt(CX - 147, 180) == 0, "but the left edge of the field is in");
		check(TitleScreen.fieldAt(CX + 147, 180) == 0, "and so is the right edge");

		// The two must never both claim a pixel, or focus would depend on which was asked first.
		int both = 0;
		for (int y = 0; y < TitleScreen.H; y++) {
			if (TitleScreen.fieldAt(CX, y) == 0 && TitleScreen.fieldAt(CX, y) == 1) {
				both++;
			}
		}
		check(both == 0, "and no pixel belongs to both fields");
	}

	static void buttonTests() {
		check(TitleScreen.inEnter(CX, 340), "the middle of ENTER is ENTER");
		check(!TitleScreen.inEnter(CX, 300), "just above it is not");
		check(!TitleScreen.inEnter(CX, 400), "nor just below");
		check(!TitleScreen.inEnter(CX - 200, 340), "nor beside it");
		check(!TitleScreen.inEnter(CX, 240), "and it does not reach up into the password field");
		check(TitleScreen.fieldAt(CX, 340) == -1, "nor the field down into the button");

		// With no font the label has no width, so only the box answers - which is what the
		// preview shows and why this is asserted rather than assumed.
		check(TitleScreen.inRemember(CX, 296, null), "the tick box answers without a font");
		check(!TitleScreen.inRemember(CX, 200, null), "and not the field above it");
	}

	// WHERE TO PROBE, taken from the screen rather than written out a second time. These were
	// literals, and a card that grew a portrait moved every one of them: the suite then
	// failed saying the x was unreachable when the x was fine and these were stale.
	private static final int CARD_MID_Y = TitleScreen.PROFILE_Y + TitleScreen.PROFILE_H / 2;
	private static final int FORGET_Y = TitleScreen.PROFILE_Y + 10;
	private static final int CARD_BOTTOM_Y = TitleScreen.PROFILE_Y + TitleScreen.PROFILE_H - 6;

	static int rowStart(int count) {
		return CX - (count * TitleScreen.PROFILE_W + count * TitleScreen.PROFILE_GAP + TitleScreen.ADD_W) / 2;
	}

	static void rowTests() {
		for (int count = 0; count <= LoginProfiles.MAX; count++) {
			int at = rowStart(count);
			boolean allFound = true;
			for (int i = 0; i < count; i++) {
				int mid = at + 64;
				if (TitleScreen.profileAt(mid, CARD_MID_Y, count) != i) {
					allFound = false;
				}
				at += TitleScreen.PROFILE_W + TitleScreen.PROFILE_GAP;
			}
			check(allFound, "with " + count + " saved, every card answers as itself");
			check(TitleScreen.inAdd(at + 20, CARD_MID_Y, count), "and the add button sits after them");
			check(TitleScreen.profileAt(at + 20, CARD_MID_Y, count) == -1, "which is not a card");
		}

		check(TitleScreen.profileAt(CX, 300, 3) == -1, "no card above the row");
		check(TitleScreen.profileAt(CX, 490, 3) == -1, "none below it");
		check(TitleScreen.profileAt(rowStart(3) - 20, CARD_MID_Y, 3) == -1, "none left of the first");

		// The gap between cards belongs to neither, so a click there does nothing rather than
		// signing into whichever card is nearer.
		check(TitleScreen.profileAt(rowStart(3) + TitleScreen.PROFILE_W + 5, CARD_MID_Y, 3) == -1, "nor in the gap between two");
	}

	static void forgetTests() {
		int count = 3;
		int at = rowStart(count);
		for (int i = 0; i < count; i++) {
			int x = at + TitleScreen.PROFILE_W - 8;
			check(TitleScreen.forgetAt(x, FORGET_Y, count) == i, "the x on card " + i + " is reachable");
			// THE ORDERING RULE. The x is inside the card, so profileAt also claims this pixel -
			// the caller must ask forgetAt first, and this is the check that says so out loud.
			check(TitleScreen.profileAt(x, FORGET_Y, count) == i,
				"and the card claims that pixel too, so forgetAt must be asked first");
			at += TitleScreen.PROFILE_W + TitleScreen.PROFILE_GAP;
		}
		check(TitleScreen.forgetAt(rowStart(count) + 20, FORGET_Y, count) == -1,
			"the left of a card is not its x");
		check(TitleScreen.forgetAt(rowStart(count) + TitleScreen.PROFILE_W - 8, CARD_BOTTOM_Y, count) == -1,
			"nor the bottom of it, where the timestamp is");
	}

	static void messageTests() {
		// The screen was handed only the second of the login server's two lines, so six
		// messages reached players as the instruction with the reason missing - among them
		// "Please check your message-centre for details." with nothing to say the account had
		// been disabled. Both lines now, and these are the rules that decide where they go.
		int[] both = TitleScreen.messageRows(true, true);
		int[] one = TitleScreen.messageRows(false, true);
		int[] none = TitleScreen.messageRows(false, false);

		check(both.length == 2, "a reason and an instruction take two lines");
		check(one.length == 1, "one of them alone takes one");
		check(TitleScreen.messageRows(true, false).length == 1, "whichever one it is");
		check(none.length == 0, "and nothing is drawn for nothing");

		// A single line stays where a single line has always been. Sliding it up to make room
		// for a line that is not there would move every message that has only ever had one.
		check(one[0] == TitleScreen.MESSAGE_Y, "one line does not move from where it was");
		check(both[1] > both[0], "and the instruction goes below the reason");

		// THE PART A PICTURE CANNOT CHECK. Both lines live in the gap between the rule and
		// the username field, and the way to prove it is to ask the hit tests that ship with
		// the screen: a message sitting on the username field is a message over a field the
		// player is about to type in.
		for (int i = 0; i < both.length; i++) {
			check(both[i] > TitleScreen.RULE_Y, "line " + i + " is below the rule");
			check(TitleScreen.fieldAt(CX, both[i]) == -1, "line " + i + " is clear of both fields");
			check(!TitleScreen.inEnter(CX, both[i]), "line " + i + " is clear of the button");
		}
		check(TitleScreen.fieldAt(CX, one[0]) == -1, "and so is a single line");

		// The descenders of the lower line, not just its baseline: a 12-pixel font drops a
		// few pixels under it, and the field rule is immediately below.
		check(TitleScreen.fieldAt(CX, both[1] + 4) == -1, "with room under the lower line");
	}

	static void bandTests() {
		// The link and the empty-row hint were drawn at 393 and 394 - one pixel apart, both
		// sentences in the same place - and only ever for a player with no saved account.
		// A picture could not catch it (no font draws nothing) and neither could the preview,
		// which has three accounts in it and so never draws the hint at all.
		check(TitleScreen.HINT_Y - TitleScreen.LINK_Y >= TitleScreen.MESSAGE_LINE,
			"the link and the empty-row hint are a line apart");

		// And the band they share: under the button, above the cards.
		check(!TitleScreen.inEnter(CX, TitleScreen.LINK_Y - 9), "the link clears the ENTER button");
		check(TitleScreen.HINT_Y + 3 < TitleScreen.PROFILE_Y, "and the hint clears the cards below it");
		check(TitleScreen.profileAt(CX, TitleScreen.HINT_Y, 3) == -1, "which no card answers to");
	}

	static void footerTests() {
		// This shipped as "-1 PLAYERS ONLINE" on the live client. The guard was described in a
		// commit message and never written, and nothing could catch it: with no font both
		// branches draw nothing, so only the rule itself is testable.
		check(TitleScreen.footerText(-1) == null, "no line at all when nobody has told us");
		check(TitleScreen.footerText(-99) == null, "nor for any other negative");
		check("0 PLAYERS ONLINE".equals(TitleScreen.footerText(0)), "an empty world still says so");
		check("142 PLAYERS ONLINE".equals(TitleScreen.footerText(142)), "and a count reads plainly");
	}

	static void emptyTests() {
		check(TitleScreen.profileAt(10, 10, 0) == -1, "a corner belongs to no card");
		check(TitleScreen.forgetAt(10, 10, 0) == -1, "and to no x");
		check(!TitleScreen.inEnter(10, 10), "and to no button");
		check(TitleScreen.fieldAt(10, 10) == -1, "and to no field");
		check(TitleScreen.profileAt(CX, CARD_MID_Y, 0) == -1, "with nothing saved, the row holds no cards");
		check(TitleScreen.inAdd(rowStart(0) + 20, CARD_MID_Y, 0), "but the add button is still there");
	}
}
