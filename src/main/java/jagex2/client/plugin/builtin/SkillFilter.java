package jagex2.client.plugin.builtin;

/**
 * "Only these skills", as a comma-separated list a player types.
 *
 * Two plugins want the same question - Boosts, to show a few rows rather than all of them, and
 * Idle notifier, to count experience in one skill rather than any - and one implementation is the
 * point: two walks of the same list would eventually disagree about whether "wood" matches
 * Woodcutting, and a filter that silently excludes what a player meant to include is read as the
 * plugin being broken.
 *
 * MATCHING IS BY PREFIX, case-insensitive, with each term trimmed. Prefix rather than substring,
 * because a substring rule makes "attack" match nothing a player would want from the word and a
 * one-letter term match half the list; and prefix rather than exact, because "wood" for
 * Woodcutting and "runecraft" for Runecrafting are what people actually type. Whole names work
 * too - a name is its own prefix.
 *
 * AN EMPTY LIST MEANS EVERY SKILL, which is the opposite of the rule in Npc indicators and is
 * right in both places. There, an empty list with no names is a plugin that has not been set up
 * yet, and "everything" would outline the whole scene. Here, the filter is a narrowing of
 * something already useful, so "no filter" has to mean "do not narrow it".
 */
final class SkillFilter {

	private SkillFilter() {
	}

	/**
	 * Whether this skill name passes the list.
	 *
	 * Never throws: both callers ask it from a render or a tick, per skill.
	 */
	static boolean allows(String skillName, String terms) {
		// THROUGH isFiltering, not a length check of its own. A list of nothing but commas is
		// not a filter, and the first version of this had the two disagreeing about that: a
		// trimmed length of 3 for ",,," meant allows() rejected every skill while isFiltering()
		// said there was no filter - an empty panel with nothing to explain it.
		if (!isFiltering(terms)) {
			return true;
		}
		if (skillName == null || skillName.length() == 0) {
			return false;
		}
		String trimmed = terms.trim();
		String lower = skillName.toLowerCase();
		int from = 0;
		while (from <= trimmed.length()) {
			int comma = trimmed.indexOf(',', from);
			String term = (comma < 0 ? trimmed.substring(from)
				: trimmed.substring(from, comma)).trim();
			if (term.length() > 0 && lower.startsWith(term.toLowerCase())) {
				return true;
			}
			if (comma < 0) {
				return false;
			}
			from = comma + 1;
		}
		return false;
	}

	/**
	 * Whether a list names anything at all, so a caller can tell "no filter" from "a filter that
	 * matches nothing".
	 *
	 * A list of nothing but commas is not a filter: honouring it literally would show an empty
	 * panel with no way to tell why.
	 */
	static boolean isFiltering(String terms) {
		if (terms == null) {
			return false;
		}
		String trimmed = terms.trim();
		for (int i = 0; i < trimmed.length(); i++) {
			if (trimmed.charAt(i) != ',' && trimmed.charAt(i) != ' ') {
				return true;
			}
		}
		return false;
	}
}
