/*
 * Headless test for the slayer page.
 *
 * WHAT THIS GUARDS. Two things, and both of them produce a page that looks fine while being
 * wrong.
 *
 * THE NAME TABLE, which is the one copy of content data this client carries. slayer_target comes
 * over as a number and the name that goes with it lives in the content, so SlayerTasks is
 * generated from it. A generated file falls behind the moment somebody adds a task, and the
 * symptom is a row reading "Task 81" - so when the content repo is beside this one the
 * generator is run again and its answer compared. Without the content it says SKIPPED, because
 * a check that cannot run must not read as one that passed.
 *
 * AND "AM I ON A TASK", which is subtler than it looks: finishing a task leaves slayer_target
 * where it was and takes slayer_count to zero, because that is how the game knows whose streak
 * to credit. A page reading the target alone shows a finished task for ever, and the player
 * walks to Vannaka to be told they already have one.
 */
package jagex2.client.plugin.builtin;

public class SlayerTest {

	static int fails;

	static void check(boolean ok, String what) {
		System.out.println((ok ? "  ok   " : "FAIL   ") + what);
		if (!ok) {
			fails++;
		}
	}

	static void skip(String what) {
		System.out.println("  SKIP  " + what);
	}

	public static void main(String[] args) {
		System.out.println("1. whether there is a task at all");
		taskTests();
		System.out.println();
		System.out.println("2. what it is called");
		nameTests();
		System.out.println();
		System.out.println("3. who set it");
		masterTests();
		System.out.println();
		System.out.println("4. the page");
		pageTests();
		System.out.println();
		System.out.println("5. the table against the content it came from");
		generatedTests();
		System.out.println();
		System.out.println("6. the varps, and the line the page is drawn on");
		sourceTests();

		System.out.println();
		if (fails == 0) {
			System.out.println("all checks passed");
		} else {
			System.out.println(fails + " CHECK(S) FAILED");
		}
		System.exit(fails == 0 ? 0 : 1);
	}

	// ------------------------------------------------------------------ 1. on task

	static void taskTests() {
		check(SlayerPlugin.onTask(2, 134), "a target and a count is a task");
		// THE ONE THAT MATTERS. complete_task leaves the target alone and zeroes the count.
		check(!SlayerPlugin.onTask(2, 0), "a finished task is NOT a task, though its target remains");
		check(!SlayerPlugin.onTask(0, 134), "nor is a count with nothing to kill");
		check(!SlayerPlugin.onTask(0, 0), "and a player who has never had one has none");
		check(!SlayerPlugin.onTask(-1, 5) && !SlayerPlugin.onTask(2, -1),
			"nor does a negative either side read as a task");
	}

	// ------------------------------------------------------------------ 2. the name

	/** Not a name any check expects, so a throw fails the check that caused it. */
	static final String THREW = "\u0000threw";

	/**
	 * SlayerTasks.name, with anything it throws turned into a failure that has a NAME.
	 *
	 * The answer for an id the table does not have is "". An exception is not "", and letting
	 * one out ends the run with no failure printed - which the mutation runner counts as a
	 * crash rather than a catch, because it does not say which promise broke.
	 */
	static String name(int target) {
		try {
			return SlayerTasks.name(target);
		} catch (RuntimeException threw) {
			return THREW;
		}
	}

	static void nameTests() {
		check(SlayerTasks.count() > 50,
			"the table has the content's tasks in it (" + SlayerTasks.count() + ")");
		check("Monkeys".equals(name(1)), "task 1 is Monkeys");
		check("Goblins".equals(name(2)), "task 2 is Goblins");
		check("Rats".equals(name(3)), "task 3 is Rats");
		check("".equals(name(0)), "0 is no task and has no name");
		check("".equals(name(9999)), "a number past the table has none either");
		check("".equals(name(-1)), "nor one below it");
		// Every name is a name: a blank in the middle is a row that says nothing.
		int blank = 0;
		for (int i = 1; i < 200; i++) {
			String name = name(i);
			if (name.length() > 0 && name.trim().length() == 0) {
				blank++;
			}
		}
		check(blank == 0, "and no task is named with whitespace");
	}

	// ------------------------------------------------------------------ 3. the master

	static void masterTests() {
		check("Turael".equals(SlayerPlugin.masterName(1)), "1 is Turael");
		check("Duradel".equals(SlayerPlugin.masterName(5)), "5 is Duradel");
		check("".equals(SlayerPlugin.masterName(0)),
			"0 is a task from before the master was recorded, and names nobody");
		check("".equals(SlayerPlugin.masterName(9)) && "".equals(SlayerPlugin.masterName(-1)),
			"and a number that is not a master names nobody either");
	}

	// ------------------------------------------------------------------ 4. the page

	static void pageTests() {
		check("134 left".equals(SlayerPlugin.taskDetail(134, 0)),
			"with no master recorded the row says only what is left");
		check("134 left, from Vannaka".equals(SlayerPlugin.taskDetail(134, 3)),
			"...and names them when it has one");
		check("1 left, from Turael".equals(SlayerPlugin.taskDetail(1, 1)),
			"one reads plainly - \"left\" has no plural, so there is no rule here to get wrong");
		check("0 left".equals(SlayerPlugin.taskDetail(0, 0)), "and zero is not");

		SlayerPlugin plugin = new SlayerPlugin();
		// No context, so no varps, so no task: the page shows its message rather than zeroes.
		check(plugin.rowCount() == 0, "with no task there are no rows at all");
		check("".equals(plugin.taskName()), "...and nothing to name");
	}

	// ------------------------------------------------------------------ 5. the generator

	static void generatedTests() {
		String root = System.getProperty("dp.root", ".");
		java.io.File content = new java.io.File(new java.io.File(root).getParentFile(),
			"DeathPlateau-Content");
		if (!content.isDirectory()) {
			skip("no content repo beside this one, so the table cannot be checked against it");
			return;
		}
		try {
			ProcessBuilder pb = new ProcessBuilder("python3", "tools/slayer/make_tasks.py",
				"--check", "--content", content.getAbsolutePath());
			pb.directory(new java.io.File(root));
			pb.redirectErrorStream(true);
			Process p = pb.start();
			byte[] out = readAll(p.getInputStream());
			int code = p.waitFor();
			String said = new String(out, "UTF-8").trim();
			check(code == 0, "SlayerTasks is up to date with the content (" + said + ")");
		} catch (Throwable error) {
			check(false, "the generator ran (" + error + ")");
		}
	}

	static byte[] readAll(java.io.InputStream in) throws java.io.IOException {
		java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
		byte[] buf = new byte[4096];
		int n;
		while ((n = in.read(buf)) > 0) {
			out.write(buf, 0, n);
		}
		return out.toByteArray();
	}

	// ------------------------------------------------------------------ 6. the varps

	/**
	 * The content's varp ids, written down here as well as in the plugin.
	 *
	 * A SECOND COPY ON PURPOSE, as with the farming ones: a test that reads the number it is
	 * checking cannot disagree with it. These are fixed assignments in the content's
	 * pack/varp.pack, so they move only when somebody edits that file.
	 */
	static void sourceTests() {
		check(SlayerPlugin.VARP_COUNT == 394, "slayer_count is varp 394");
		check(SlayerPlugin.VARP_TARGET == 395, "slayer_target is 395");
		check(SlayerPlugin.VARP_POINTS == 865, "slayer_points is 865");
		check(SlayerPlugin.VARP_STREAK == 866, "slayer_streak is 866");
		check(SlayerPlugin.VARP_MASTER == 867, "slayer_task_master is 867");
		boolean distinct = SlayerPlugin.VARP_COUNT != SlayerPlugin.VARP_TARGET
			&& SlayerPlugin.VARP_POINTS != SlayerPlugin.VARP_STREAK
			&& SlayerPlugin.VARP_STREAK != SlayerPlugin.VARP_MASTER;
		check(distinct, "and no two of them are the same varp");

		String src = read("src/main/java/jagex2/client/plugin/builtin/SlayerPlugin.java");
		check(src.length() > 0, "the plugin's source is where the test expects it");
		check(src.indexOf("setVarp") < 0 && src.indexOf("consume()") < 0,
			"the page changes nothing and consumes nothing");
		// A bar here would have to be drawn against a total the client is never sent.
		check(src.indexOf("int progress(int index) {\n\t\treturn -1;") >= 0,
			"and draws no bar, because what a task STARTED at is never sent");
	}

	static String read(String path) {
		try {
			byte[] bytes = java.nio.file.Files.readAllBytes(
				java.nio.file.Paths.get(System.getProperty("dp.root", "."), path));
			return new String(bytes, "UTF-8");
		} catch (Throwable missing) {
			return "";
		}
	}
}
