package jagex2.client.plugin.event;

/**
 * A skill's level or experience changed, straight off the UPDATE_STAT packet.
 *
 * On login the server syncs every skill at once, and those arrive as stat changes like any other.
 * Those carry gained == 0 so that a plugin counting experience does not record a whole account's
 * worth of xp the moment someone logs in.
 */
public final class StatChanged {

	/** Skill index, as used by Client.skillLevel. See jagex2.client.Stats for the names. */
	public final int skill;

	/** The boosted/drained level the server just sent. */
	public final int level;

	/** Total experience in the skill after the change. */
	public final int experience;

	/** Experience gained by this change, or 0 for the initial sync and for level-only changes. */
	public final int gained;

	public StatChanged(int skill, int level, int experience, int gained) {
		this.skill = skill;
		this.level = level;
		this.experience = experience;
		this.gained = gained;
	}
}
