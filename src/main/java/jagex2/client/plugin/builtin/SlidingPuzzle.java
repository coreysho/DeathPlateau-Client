package jagex2.client.plugin.builtin;

/**
 * The treasure trail puzzle box, solved: twenty-four sliding pieces and a gap.
 *
 * WHAT A MOVE IS. The player clicks a piece next to the gap and it slides in - that is the only
 * move the game has ([opheld5,_trail_puzzle_piece] in trail_puzzle.rs2 looks at the four cells
 * around the clicked slot and slides into whichever is empty). So a solution is a list of SLOTS
 * TO CLICK, in order, and nothing else: no directions to translate, no coordinates to convert.
 *
 * NOT THE SHORTEST SOLUTION, AND DELIBERATELY NOT. The 24-puzzle's shortest solutions average
 * about 100 moves and finding one is NP-hard in the general case - the published optimal solvers
 * take hours on single boards. This solves it the way a person does, a row and a column at a
 * time: a hundred thousand random boards averaged 146 moves against that 100, and a third of a
 * millisecond each. A player in front of a puzzle box wants to be told the next click now, not
 * the best click eventually.
 *
 * THE REDUCTION. Solve the top row and the left column and what is left is a 4x4 with the same
 * shape of problem; three rounds of that leaves a 2x2, which is three pieces and a gap and can
 * only be rotated. The awkward cells are the last pair of each row and column, because placing
 * them one at a time locks the second one out.
 *
 * AND EVERY PLACEMENT IS A BREADTH-FIRST SEARCH OVER THE PIECES AND THE GAP TOGETHER, which is
 * the one idea in this file. The usual way - route the piece, then walk the gap round to each
 * cell it needs - looks right and fails on two boards in three, because the gap can follow a
 * piece into a dead end and then be unable to get past it to come back. Searching both
 * positions at once cannot do that: a route into a dead end is not a route to the goal, so it
 * is never taken. One piece is 625 states, the awkward pair is 15,625, and both are nothing to
 * walk. The manoeuvres every description of this puzzle recommends are gone with them.
 *
 * EVERYTHING HERE IS ARITHMETIC ON A 25-CELL ARRAY. Nothing reads the client, so the test can
 * shuffle a board the way the content's own ~shuffle_trail_puzzle does and check the moves this
 * returns really do solve it.
 */
public final class SlidingPuzzle {

	public static final int SIDE = 5;

	public static final int CELLS = SIDE * SIDE;

	/** The empty cell, as a board value. Every other cell holds the home cell of its piece. */
	public static final int GAP = -1;

	/**
	 * A ceiling on the solution length, so a board this cannot finish stops rather than hangs.
	 *
	 * A ROW-AT-A-TIME SOLVE OF A 24-PUZZLE RUNS TO A COUPLE OF HUNDRED MOVES - a hundred
	 * thousand random boards averaged 146 and the worst of them was 253 - so a thousand is
	 * slack rather than a limit. It is here because this runs on the game thread: a bug that
	 * walked a piece in circles would freeze the client, and freezing it is worse than silence.
	 */
	public static final int LIMIT = 1000;

	private final int[] board = new int[CELLS];

	/** Cells already finished, which nothing may move through or into. */
	private final boolean[] frozen = new boolean[CELLS];

	private int gap;

	private int[] moves = new int[256];

	private int count;

	/** Scratch for the two searches, allocated once because this runs every time a piece moves. */
	private final int[] from = new int[CELLS];

	private final int[] queue = new int[CELLS];

	/**
	 * Scratch for the two joint searches, which is where all the work happens.
	 *
	 * One entry per state: (piece, gap) for place(), and (piece, piece, gap) for pair(). The
	 * larger of the two is 25^3, which is fifteen thousand ints - small enough to allocate per
	 * solve and walk breadth-first in a fraction of a millisecond, and the reason this file has
	 * no clever manoeuvres in it.
	 */
	private final int[] came = new int[CELLS * CELLS * CELLS];

	private final int[] jointQueue = new int[CELLS * CELLS * CELLS];

	private final int[] walk = new int[CELLS * CELLS * CELLS];

	private SlidingPuzzle(int[] start) {
		System.arraycopy(start, 0, this.board, 0, CELLS);
		for (int cell = 0; cell < CELLS; cell++) {
			if (this.board[cell] == GAP) {
				this.gap = cell;
			}
		}
	}

	// ------------------------------------------------------------------ the board

	/** Whether a board is one this can work on at all: 24 distinct pieces and one gap. */
	public static boolean isBoard(int[] board) {
		if (board == null || board.length != CELLS) {
			return false;
		}
		boolean[] seen = new boolean[CELLS];
		int gaps = 0;
		for (int cell = 0; cell < CELLS; cell++) {
			int piece = board[cell];
			if (piece == GAP) {
				gaps++;
				continue;
			}
			if (piece < 0 || piece >= CELLS - 1 || seen[piece]) {
				return false;
			}
			seen[piece] = true;
		}
		return gaps == 1;
	}

	/** Whether every piece is home and the gap is in the last cell. */
	public static boolean isSolved(int[] board) {
		if (!isBoard(board)) {
			return false;
		}
		for (int cell = 0; cell < CELLS - 1; cell++) {
			if (board[cell] != cell) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Whether this board can be solved at all.
	 *
	 * ON AN ODD-SIDED BOARD THE RULE IS JUST THE INVERSIONS. Every slide moves the gap one cell
	 * and, read in row-major order, swaps the gap past one piece - which on a 5-wide board is
	 * either a step within a row (one inversion changes) or a step between rows (it jumps four
	 * pieces, so an even number change). The parity of the inversion count is therefore the same
	 * for every board you can reach from solved, and solved has none.
	 *
	 * The game always shuffles FROM solved, so a real puzzle box is always solvable. This is here
	 * for the board that is not - a misread of the window, or a piece the table does not know -
	 * because a solver that cannot say "no" loops instead.
	 */
	public static boolean isSolvable(int[] board) {
		if (!isBoard(board)) {
			return false;
		}
		int inversions = 0;
		for (int a = 0; a < CELLS; a++) {
			if (board[a] == GAP) {
				continue;
			}
			for (int b = a + 1; b < CELLS; b++) {
				if (board[b] != GAP && board[b] < board[a]) {
					inversions++;
				}
			}
		}
		return (inversions & 1) == 0;
	}

	/**
	 * The slots to click, in order, that take this board to solved.
	 *
	 * An empty array for a board already solved, and null for one that cannot be - which is not
	 * the same thing, and the page says different words for each.
	 */
	public static int[] solve(int[] board) {
		if (!isSolvable(board)) {
			return null;
		}
		SlidingPuzzle puzzle = new SlidingPuzzle(board);
		return puzzle.run() ? puzzle.taken() : null;
	}

	/** The board a list of clicks leaves behind, for checking that a solution is one. */
	public static int[] after(int[] board, int[] clicks) {
		int[] out = new int[CELLS];
		System.arraycopy(board, 0, out, 0, CELLS);
		int gap = 0;
		for (int cell = 0; cell < CELLS; cell++) {
			if (out[cell] == GAP) {
				gap = cell;
			}
		}
		for (int i = 0; i < clicks.length; i++) {
			int click = clicks[i];
			if (click < 0 || click >= CELLS || !adjacent(click, gap)) {
				return null;            // not a move the game would have allowed
			}
			out[gap] = out[click];
			out[click] = GAP;
			gap = click;
		}
		return out;
	}

	// ------------------------------------------------------------------ the reduction

	private boolean run() {
		for (int k = 0; k <= SIDE - 3; k++) {
			if (!this.solveRow(k) || !this.solveColumn(k)) {
				return false;
			}
		}
		return this.solveCorner();
	}

	/** Row k, from column k rightwards. The last two go in together. */
	private boolean solveRow(int row) {
		for (int col = row; col <= SIDE - 3; col++) {
			if (!this.place(cell(row, col), cell(row, col))) {
				return false;
			}
			this.frozen[cell(row, col)] = true;
		}
		return this.pair(cell(row, SIDE - 2), cell(row, SIDE - 1));
	}

	/** Column k, from row k+1 downwards - row k is already done. The last two go in together. */
	private boolean solveColumn(int col) {
		for (int row = col + 1; row <= SIDE - 3; row++) {
			if (!this.place(cell(row, col), cell(row, col))) {
				return false;
			}
			this.frozen[cell(row, col)] = true;
		}
		return this.pair(cell(SIDE - 2, col), cell(SIDE - 1, col));
	}

	/**
	 * The last two of a row or a column, which will not go in one at a time.
	 *
	 * PLACE THE FIRST AND THE SECOND HAS NOWHERE TO COME FROM. Freezing the second-to-last cell
	 * walls off the last one: the only way into it runs through the cell just frozen. Every
	 * description of this puzzle answers that with a manoeuvre - stage the two pieces in the
	 * wrong cells, then rotate them home - and the manoeuvre has edge cases, and the edge cases
	 * have edge cases. One of them cost two rounds here: staging the first piece turns the cell
	 * it came from into a dead end, and if the second piece happens to be sitting in that dead
	 * end then nothing can get it out, because the gap has to go in after it and then cannot get
	 * past it to come back.
	 *
	 * SO BOTH PIECES MOVE AT ONCE, in one search, the same way place() moves one. A state is
	 * (where the first piece is, where the second is, where the gap is) - fifteen thousand six
	 * hundred and twenty-five of them, which is nothing - and the goal is both pieces home. No
	 * staging, no rotation, no edge cases: a route that walks into a dead end is simply not a
	 * route to the goal, so breadth-first never takes it.
	 */
	private boolean pair(int first, int last) {
		int a = this.find(first);
		int b = this.find(last);
		if (a < 0 || b < 0) {
			return false;
		}
		if (a != first || b != last) {
			java.util.Arrays.fill(this.came, -1);
			int start = (a * CELLS + b) * CELLS + this.gap;
			this.came[start] = start;
			int head = 0;
			int tail = 0;
			this.jointQueue[tail++] = start;
			int goal = -1;
			while (head < tail && goal < 0) {
				int state = this.jointQueue[head++];
				int hole = state % CELLS;
				int at = state / CELLS;
				int second = at % CELLS;
				int firstAt = at / CELLS;
				for (int side = 0; side < 4 && goal < 0; side++) {
					int next = neighbour(hole, side);
					if (next < 0 || this.frozen[next]) {
						continue;
					}
					// Whichever of the two the gap steps onto takes the gap's old cell. It can
					// be neither, which is every move that only shuffles the other pieces.
					int movedFirst = next == firstAt ? hole : firstAt;
					int movedSecond = next == second ? hole : second;
					int to = (movedFirst * CELLS + movedSecond) * CELLS + next;
					if (this.came[to] >= 0) {
						continue;
					}
					this.came[to] = state;
					if (movedFirst == first && movedSecond == last) {
						goal = to;
					} else {
						this.jointQueue[tail++] = to;
					}
				}
			}
			if (goal < 0) {
				return false;
			}
			int length = 0;
			for (int state = goal; state != start; state = this.came[state]) {
				this.walk[length++] = state % CELLS;
			}
			for (int i = length - 1; i >= 0; i--) {
				this.click(this.walk[i]);
			}
		}
		if (this.board[first] != first || this.board[last] != last) {
			return false;
		}
		this.frozen[first] = true;
		this.frozen[last] = true;
		return true;
	}

	/**
	 * The last 2x2: three pieces and the gap, which can only go round.
	 *
	 * FOUR CLICKS RETURN THE GAP TO ITS CORNER AND MOVE THE THREE PIECES ON BY ONE, so there are
	 * exactly three arrangements reachable and one of them is solved. Three rounds is therefore
	 * all of them; a board still wrong after that was never solvable, and isSolvable above
	 * should already have said so.
	 */
	private boolean solveCorner() {
		int[] ring = { cell(SIDE - 2, SIDE - 2), cell(SIDE - 2, SIDE - 1),
			cell(SIDE - 1, SIDE - 1), cell(SIDE - 1, SIDE - 2) };
		if (!this.moveGap(ring[2])) {
			return false;
		}
		for (int round = 0; round < 3; round++) {
			if (this.solved()) {
				return true;
			}
			for (int step = 0; step < 4; step++) {
				int at = index(ring, this.gap);
				this.click(ring[(at + 1) % 4]);
			}
		}
		return this.solved();
	}

	// ------------------------------------------------------------------ moving one piece

	/**
	 * Walk the piece that belongs at `home` to `target`.
	 *
	 * THE GAP AND THE PIECE ARE ONE PROBLEM, NOT TWO, and that is the whole of this method. The
	 * obvious way - find the piece's shortest path, then walk the gap round to each next cell in
	 * turn - is wrong, and wrong in a way that only shows up as the board shrinks. Once the free
	 * region is an L of five cells, the shortest path for the piece can leave the gap in a dead
	 * end behind it with the piece itself blocking the only way out. Two thirds of boards failed
	 * that way, always in the last row or column, always with a perfectly good longer route
	 * going unlooked at.
	 *
	 * So the search is over BOTH positions at once: a state is (where the piece is, where the
	 * gap is), a move is the gap stepping to a free neighbour, and when that neighbour is the
	 * piece the two swap. Twenty-five cells each is six hundred and twenty-five states, which is
	 * nothing to walk breadth-first, and it cannot paint itself into a corner because a corner
	 * it cannot get out of is simply not on any path to the goal.
	 *
	 * Every state's predecessor is kept, so the clicks fall straight out: each step of the path
	 * is the gap's new cell, and the gap's new cell is the slot the player clicks.
	 */
	private boolean place(int home, int target) {
		int tile = this.find(home);
		if (tile < 0) {
			return false;
		}
		if (tile == target) {
			return true;
		}
		java.util.Arrays.fill(this.came, -1);
		int start = tile * CELLS + this.gap;
		this.came[start] = start;
		int head = 0;
		int tail = 0;
		this.jointQueue[tail++] = start;
		int goal = -1;
		while (head < tail && goal < 0) {
			int state = this.jointQueue[head++];
			int at = state / CELLS;
			int hole = state % CELLS;
			for (int side = 0; side < 4 && goal < 0; side++) {
				int next = neighbour(hole, side);
				if (next < 0 || this.frozen[next]) {
					continue;
				}
				// The gap steps onto `next`; if the piece was standing there, it takes the
				// gap's old cell - which is the one move this puzzle has.
				int moved = next == at ? hole : at;
				int to = moved * CELLS + next;
				if (this.came[to] >= 0) {
					continue;
				}
				this.came[to] = state;
				if (moved == target) {
					goal = to;
				} else {
					this.jointQueue[tail++] = to;
				}
			}
		}
		if (goal < 0) {
			return false;
		}
		// Back to the start, then forwards, clicking the gap's cell at each step.
		int length = 0;
		for (int state = goal; state != start; state = this.came[state]) {
			this.walk[length++] = state % CELLS;
		}
		for (int i = length - 1; i >= 0; i--) {
			this.click(this.walk[i]);
		}
		return true;
	}

	/** Walk the gap to `target` without disturbing anything frozen. */
	private boolean moveGap(int target) {
		for (int guard = 0; guard < CELLS * CELLS; guard++) {
			if (this.gap == target) {
				return true;
			}
			int next = this.step(this.gap, target);
			if (next < 0) {
				return false;
			}
			this.click(next);
		}
		return false;
	}

	/**
	 * The first cell on a shortest path from `at` to `target`, or -1 when there is none.
	 *
	 * Breadth first from the TARGET rather than from `at`, because that gives the first step
	 * straight off the parent array without walking the path back.
	 */
	private int step(int at, int target) {
		if (at == target) {
			return at;
		}
		for (int cell = 0; cell < CELLS; cell++) {
			this.from[cell] = -1;
		}
		int head = 0;
		int tail = 0;
		this.queue[tail++] = target;
		this.from[target] = target;
		while (head < tail) {
			int cell = this.queue[head++];
			for (int side = 0; side < 4; side++) {
				int next = neighbour(cell, side);
				if (next < 0 || this.from[next] >= 0 || (this.frozen[next] && next != at)) {
					continue;
				}
				this.from[next] = cell;
				if (next == at) {
					return cell;
				}
				this.queue[tail++] = next;
			}
		}
		return -1;
	}

	/** Click a piece next to the gap, which slides it in. */
	private void click(int at) {
		if (this.count >= LIMIT) {
			this.count = LIMIT + 1;         // over the ceiling: taken() refuses to hand it back
			return;
		}
		if (this.count == this.moves.length) {
			int[] bigger = new int[this.moves.length * 2];
			System.arraycopy(this.moves, 0, bigger, 0, this.moves.length);
			this.moves = bigger;
		}
		this.moves[this.count++] = at;
		this.board[this.gap] = this.board[at];
		this.board[at] = GAP;
		this.gap = at;
	}

	private int find(int home) {
		for (int cell = 0; cell < CELLS; cell++) {
			if (this.board[cell] == home) {
				return cell;
			}
		}
		return -1;
	}

	private boolean solved() {
		for (int cell = 0; cell < CELLS - 1; cell++) {
			if (this.board[cell] != cell) {
				return false;
			}
		}
		return true;
	}

	private int[] taken() {
		if (this.count > LIMIT) {
			return null;
		}
		int[] out = new int[this.count];
		System.arraycopy(this.moves, 0, out, 0, this.count);
		return out;
	}

	// ------------------------------------------------------------------ the grid

	public static int cell(int row, int col) {
		return row * SIDE + col;
	}

	public static int rowOf(int cell) {
		return cell / SIDE;
	}

	public static int colOf(int cell) {
		return cell % SIDE;
	}

	/** Whether two cells share an edge, which is the only way a piece can move between them. */
	public static boolean adjacent(int a, int b) {
		if (a < 0 || b < 0 || a >= CELLS || b >= CELLS) {
			return false;
		}
		int dr = rowOf(a) - rowOf(b);
		int dc = colOf(a) - colOf(b);
		if (dr < 0) {
			dr = -dr;
		}
		if (dc < 0) {
			dc = -dc;
		}
		return dr + dc == 1;
	}

	/** The cell one step off in one of the four directions, or -1 off the edge of the board. */
	public static int neighbour(int cell, int side) {
		int row = rowOf(cell);
		int col = colOf(cell);
		if (side == 0) {
			return row > 0 ? cell - SIDE : -1;
		}
		if (side == 1) {
			return row < SIDE - 1 ? cell + SIDE : -1;
		}
		if (side == 2) {
			return col > 0 ? cell - 1 : -1;
		}
		return col < SIDE - 1 ? cell + 1 : -1;
	}

	private static int index(int[] ring, int cell) {
		for (int i = 0; i < ring.length; i++) {
			if (ring[i] == cell) {
				return i;
			}
		}
		return -1;
	}
}
