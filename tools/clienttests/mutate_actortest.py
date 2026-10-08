#!/usr/bin/env python3
"""Mutation test for tools/clienttests/run_actortest.py.

Breaks API level 4 one way at a time and checks run_actortest notices - and notices by NAMING a
check, not by crashing. getNpcs and getPlayers are the first API that tells a plugin about
somebody else, so the rules worth breaking are the ones that keep it a read: the derived tile, the
centre of a big npc, the ordering, the name matching, and the structural promise that an Actor
carries no handle on a live entity.

    python3 tools/clienttests/mutate_actortest.py [filter]
"""
import os
import subprocess
import sys

# THE SOURCE GOES BACK EVEN IF THIS PROCESS IS KILLED. The `finally` below covers a run that
# fails or times out; a SIGTERM skips it entirely, and a kill once left a mutation sitting in
# the tree where the next commit would have shipped it. mutate_guard also has the standalone
# check that every pattern still matches its source exactly once.
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from mutate_guard import guard  # noqa: E402  (after the sys.path line, necessarily)

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
ACTOR = os.path.join(ROOT, 'src/main/java/jagex2/client/plugin/Actor.java')
CONTEXT = os.path.join(ROOT, 'src/main/java/jagex2/client/plugin/PluginContext.java')
API = os.path.join(ROOT, 'src/main/java/jagex2/client/plugin/PluginApi.java')
PLUGIN = os.path.join(ROOT, 'src/main/java/jagex2/client/plugin/builtin/NpcIndicatorsPlugin.java')
TILES = os.path.join(ROOT, 'src/main/java/jagex2/client/plugin/builtin/TileIndicatorsPlugin.java')
RUNNER = os.path.join(HERE, 'run_actortest.py')

MUTS = [
    # ---- THE TILE IS DERIVED. Passing it in, or deriving it wrongly, lets the tile and the fine
    # position disagree - and then an outline and a name tag describe different places.
    (ACTOR, 'the tile derived with the wrong shift, so it disagrees with the fine position',
     'this.sceneTileX = sceneX >> 7;',
     'this.sceneTileX = sceneX >> 6;'),
    (ACTOR, 'the tile taking z from x',
     'this.sceneTileZ = sceneZ >> 7;',
     'this.sceneTileZ = sceneX >> 7;'),

    # ---- THE CENTRE OF A BIG NPC. Anchored at its south-west tile, so a label drawn at sceneX
    # sits on that corner rather than over the thing.
    (ACTOR, 'the centre not offset at all, so a boss is labelled on its corner',
     'return this.sceneX + (this.size - 1) * 64;',
     'return this.sceneX;'),
    (ACTOR, 'the centre offset by a whole tile per size rather than half',
     'return this.sceneX + (this.size - 1) * 64;',
     'return this.sceneX + (this.size - 1) * 128;'),
    (ACTOR, 'the centre offset on x but not z, so the label drifts diagonally',
     'return this.sceneZ + (this.size - 1) * 64;',
     'return this.sceneZ;'),

    # ---- DEFENCES. All of this is handed straight into projection arithmetic.
    (ACTOR, 'a missing name left null, for a plugin matching on names to trip over',
     'this.name = name == null ? "?" : name;',
     'this.name = name;'),
    (ACTOR, 'a size of 0 believed, so centre arithmetic goes backwards',
     'this.size = size < 1 ? 1 : size;',
     'this.size = size;'),
    (ACTOR, 'a player counted as an npc',
     'return this.npcId >= 0;',
     'return true;'),

    # ---- THE ORDERING. Nearest first is what makes a cap drop the far ones rather than an
    # arbitrary subset.
    (ACTOR, 'the ordering reversed, so a cap keeps the farthest npcs',
     'return da < db ? -1 : da > db ? 1 : 0;',
     'return da < db ? 1 : da > db ? -1 : 0;'),
    (ACTOR, 'the ordering flattened, so nothing is sorted at all',
     'return da < db ? -1 : da > db ? 1 : 0;',
     'return 0;'),
    (ACTOR, 'distance measured on one axis, so a diagonal neighbour looks adjacent',
     'return dx * dx + dz * dz;',
     'return dx * dx;'),
    (ACTOR, 'distance measured from the anchor rather than the centre',
     '''		long dx = a.centreX() - fromX;
		long dz = a.centreZ() - fromZ;''',
     '''		long dx = a.sceneX - fromX;
		long dz = a.sceneZ - fromZ;'''),
    (ACTOR, 'the distance arithmetic narrowed to int, where a bigger scene would overflow',
     'long dx = a.centreX() - fromX;',
     'int dx = a.centreX() - fromX;'),

    # ---- WHICH NPCS. An empty list meaning "everything" would outline the whole scene the first
    # time anyone enabled the plugin.
    (PLUGIN, 'an empty name list matching everything',
     'if (want.length() > 0 && lower.indexOf(want.toLowerCase()) >= 0) {',
     'if (lower.indexOf(want.toLowerCase()) >= 0) {'),
    (PLUGIN, 'matching made case-sensitive, so "goblin" finds nothing',
     'String lower = name.toLowerCase();',
     'String lower = name;'),
    (PLUGIN, 'matching made exact, so part of a name no longer works',
     'if (want.length() > 0 && lower.indexOf(want.toLowerCase()) >= 0) {',
     'if (want.length() > 0 && lower.equals(want.toLowerCase())) {'),
    (PLUGIN, 'terms not trimmed, so a space after a comma breaks one',
     '''			String term = (comma < 0 ? terms.substring(from) : terms.substring(from, comma)).trim();
			String want = termName(term);''',
     '''			String term = comma < 0 ? terms.substring(from) : terms.substring(from, comma);
			String want = termName(term);'''),
    (PLUGIN, 'a null name thrown rather than declined, once per npc per frame',
     '''		if (name == null || terms == null || name.length() == 0) {
			return 0;
		}''',
     '\t\tif (false) {\n\t\t\treturn 0;\n\t\t}'),
    (PLUGIN, 'a combat level of 0 printed, so a banker reads "(level-0)"',
     'if (!withLevel || npc.combatLevel <= 0) {',
     'if (!withLevel) {'),
    (PLUGIN, 'the level shown even when the player turned it off',
     'if (!withLevel || npc.combatLevel <= 0) {',
     'if (npc.combatLevel <= 0) {'),

    # ---- THE CAP, which is what stops a one-letter name costing a frame.
    (PLUGIN, 'the per-frame cap removed',
     'for (int i = 0; i < npcs.size() && drawn < cap; i++) {',
     'for (int i = 0; i < npcs.size(); i++) {'),
    # A mutation that swaps the shared outlineTile call for a local copy is deliberately
    # NOT here: no single find/replace can both remove the call and supply the second copy,
    # so every version of it fails to compile and measures javac instead of the tests. The
    # promise is kept by ActorTest's source check that the shared call is still there.
    (PLUGIN, 'the declared API level lowered, so an older client would load it and throw',
     'apiLevel = 6',
     'apiLevel = 4'),

    # ---- THE READS. npcs[] is 16384 long and mostly stale; npcCount says how much of npcIds[]
    # is live this tick.
    (CONTEXT, 'the npc array walked directly, so dead entries are reported',
     'for (int i = 0; i < this.client.npcCount; i++) {',
     'for (int i = 0; i < this.client.npcs.length; i++) {'),
    (CONTEXT, 'an npc whose config has not arrived yet dereferenced anyway',
     'if (npc == null || !npc.method351() || npc.field1370 == null) {',
     'if (npc == null) {'),
    (CONTEXT, 'the local player left out of getPlayers',
     '''		if (inScene(me.field1157, me.field1158)) {
			actors.add(player(me, true));
		}''',
     ''),
    (CONTEXT, 'the local player listed as though it were somebody else',
     'actors.add(player(me, true));',
     'actors.add(player(me, false));'),
    (CONTEXT, 'the local player not skipped in the loop, so you can be listed twice',
     'if (other == null || other == me || !other.method351()) {',
     'if (other == null || !other.method351()) {'),
    (CONTEXT, 'actors outside the scene kept, so a label lands in the corner of the screen',
     '''		return tileX >= 0 && tileX < 104 && tileZ >= 0 && tileZ < 104;''',
     '''		return true;'''),
    (CONTEXT, 'a player given an npc id, so isNpc answers yes for a person',
     'return new Actor(p.name, p.field1675, -1, p.field1157, p.field1158, p.field1148, self);',
     'return new Actor(p.name, p.field1675, 0, p.field1157, p.field1158, p.field1148, self);'),
    (CONTEXT, 'the sort dropped, so nearest-first is not true and a cap keeps anything',
     '''		sortByDistanceFromPlayer(actors);
		return actors;
	}

	/**
	 * The players in the scene''',
     '''		return actors;
	}

	/**
	 * The players in the scene'''),

    # ---- THE LEVEL ITSELF.
    (API, 'the API level left behind, so a plugin built for this client is refused by it',
     'public static final int LEVEL = 6;',
     'public static final int LEVEL = 4;'),
    (API, 'the level check made exact, so every older plugin stops loading',
     'return apiLevel <= LEVEL;',
     'return apiLevel == LEVEL;'),
    # ---- A COLOUR PER TERM, AND THE EXACT TERMS TAGGING WORKS ON. Two kinds of matching live
    # side by side in this plugin: marking is by substring, tagging is by whole term. Mixing them
    # up gives a Tag row that offers to untag something it never added.
    (PLUGIN, '''a term's own colour ignored, so writing one does nothing''',
     '''				int own = termOwnColour(term);
				return own == 0 ? fallback : own;''',
     '''				return fallback;'''),
    (PLUGIN, '''the last matching term winning instead of the first, so list order reverses''',
     '''				int own = termOwnColour(term);
				return own == 0 ? fallback : own;
			}
			if (comma < 0) {
				return 0;
			}''',
     '''				int own = termOwnColour(term);
				if (comma < 0) {
					return own == 0 ? fallback : own;
				}
			}
			if (comma < 0) {
				return 0;
			}'''),
    (PLUGIN, '''a term coloured black taken at its word, so the npc silently stops being marked''',
     '''		for (int i = 0; i < 6; i++) {
			if (Character.digit(hex.charAt(i), 16) < 0) {
				return 0;
			}
		}
''',
     ''''''),
    (PLUGIN, '''a short colour parsed anyway, so =FFF is a number nobody asked for''',
     '''		if (hex.length() != 6) {
			return 0;
		}
''',
     ''''''),
    (PLUGIN, '''a term with no equals read as having a colour''',
     '''		if (equals < 0) {
			return 0;
		}
		String hex = term.substring(equals + 1).trim();''',
     '''		String hex = term.substring(equals + 1).trim();'''),
    (PLUGIN, '''a term's name taken whole, so Goblin=FF0000 never matches a Goblin''',
     '''		return (equals < 0 ? term : term.substring(0, equals)).trim();''',
     '''		return term.trim();'''),
    (PLUGIN, '''tagging matched by substring, so untagging Goblin takes Goblin Guard too''',
     '''			if (termName(term).equalsIgnoreCase(name.trim())) {''',
     '''			if (termName(term).toLowerCase().indexOf(name.trim().toLowerCase()) >= 0) {'''),
    (PLUGIN, '''tagging made case-sensitive, so a tag added from the menu is not found again''',
     '''			if (termName(term).equalsIgnoreCase(name.trim())) {''',
     '''			if (termName(term).equals(name.trim())) {'''),
    (PLUGIN, '''an empty name counting as a term, so every npc reads as tagged''',
     '''		if (terms == null || name == null || name.length() == 0) {
			return -1;
		}''',
     '''		if (terms == null || name == null) {
			return -1;
		}'''),
    (PLUGIN, '''the same name added twice, so the list grows every time it is tagged''',
     '''		if (hasTerm(terms, add)) {
			return terms;
		}
''',
     ''''''),
    (PLUGIN, '''a name with a comma stored anyway, so the list is read back as two terms''',
     '''if (add.length() == 0 || add.indexOf(',') >= 0 || add.indexOf('=') >= 0) {''',
     '''if (add.length() == 0) {'''),
    (PLUGIN, '''the first term written with a leading comma''',
     '''		return list.length() == 0 ? add : list + ", " + add;''',
     '''		return list + ", " + add;'''),
    (PLUGIN, '''terms joined without a separator, so two tags become one name''',
     '''		return list.length() == 0 ? add : list + ", " + add;''',
     '''		return list.length() == 0 ? add : list + add;'''),
    (PLUGIN, '''untagging removing nothing''',
     '''			if (term.length() > 0 && !termName(term).equalsIgnoreCase(want)) {''',
     '''			if (term.length() > 0) {'''),
    (PLUGIN, '''untagging removing everything''',
     '''			if (term.length() > 0 && !termName(term).equalsIgnoreCase(want)) {''',
     '''			if (false) {'''),
    (PLUGIN, '''a remaining term losing its colour, so untagging one rule recolours another''',
     '''				out.append(term);''',
     '''				out.append(termName(term));'''),
    (PLUGIN, '''the rebuilt list left with a leading separator''',
     '''				if (out.length() > 0) {
					out.append(", ");
				}
''',
     '''				out.append(", ");
'''),
    (PLUGIN, '''untagging by substring, so removing Goblin removes Goblin Guard as well''',
     '''			if (term.length() > 0 && !termName(term).equalsIgnoreCase(want)) {''',
     '''			if (term.length() > 0 && termName(term).toLowerCase().indexOf(want.toLowerCase()) < 0) {'''),
    (PLUGIN, '''the border floor removed, so a zero is no outline at all''',
     '''		if (width < MIN_BORDER) {
			return MIN_BORDER;
		}
''',
     ''''''),
    (PLUGIN, '''the border ceiling removed, so a thick one swallows a distant tile''',
     '''		return width > MAX_BORDER ? MAX_BORDER : width;''',
     '''		return width;'''),
    (PLUGIN, '''the border bounds moved, so five is no longer reachable''',
     '''	static final int MAX_BORDER = 5;''',
     '''	static final int MAX_BORDER = 2;'''),
    (PLUGIN, '''the cap floor removed, so a zero turns the feature off by typo''',
     '''		if (most < MIN_MAX_DRAWN) {
			return MIN_MAX_DRAWN;
		}
''',
     ''''''),
    (PLUGIN, '''the cap ceiling removed, so a crowd is unbounded''',
     '''		return most > MAX_MAX_DRAWN ? MAX_MAX_DRAWN : most;''',
     '''		return most;'''),
    (PLUGIN, '''the cap default moved, so thirty-two is not what it ships with''',
     '''	static final int DEFAULT_MAX_DRAWN = 32;''',
     '''	static final int DEFAULT_MAX_DRAWN = 16;'''),
    (PLUGIN, '''the tile style inverted, so the drop-down means the opposite''',
     '''		return !ANCHOR_TILE.equals(style);''',
     '''		return ANCHOR_TILE.equals(style);'''),
    (PLUGIN, '''an unknown tile style marking nothing rather than falling back''',
     '''		return !ANCHOR_TILE.equals(style);''',
     '''		return EVERY_TILE.equals(style);'''),
    (PLUGIN, '''the menu name taken with its tags on, so no term matches it''',
     '''		return MenuSwaps.parseTarget(option, at);''',
     '''		return option;'''),
    (TILES, '''the inset not clamped at the centre, so a distant tile grows a bigger ring''',
     '''		return (step > 0) == (moved > centre) ? centre : moved;''',
     '''		return moved;'''),
    (TILES, '''the inset going the wrong way, so a thick border leaves its tile''',
     '''		int step = from < centre ? by : -by;''',
     '''		int step = from < centre ? -by : by;'''),
    (TILES, '''the first ring inset too, so a one-pixel border is drawn shrunk''',
     '''		if (from == centre || by <= 0) {''',
     '''		if (from == centre) {'''),

]


def main():
    only = sys.argv[1] if len(sys.argv) > 1 else None
    orig = {}
    for path in (ACTOR, CONTEXT, API, PLUGIN, TILES):
        with open(path, encoding='utf-8', newline='') as f:
            orig[path] = f.read()
    guard(orig)
    muts = [m for m in MUTS if not only or only in m[1]]
    print('running %d of %d mutations' % (len(muts), len(MUTS)))
    bad = loose = 0
    for path, why, find, repl in muts:
        n = orig[path].count(find)
        if n != 1:
            print('  SKIP (pattern %s) %s'
                  % ('not found' if n == 0 else 'matches %d times' % n, why))
            bad += 1
            continue
        try:
            with open(path, 'w', encoding='utf-8', newline='') as f:
                f.write(orig[path].replace(find, repl))
            r = subprocess.run([sys.executable, RUNNER], capture_output=True, text=True,
                               timeout=600)
        except subprocess.TimeoutExpired:
            print('  %-5s %-76s %s' % ('HUNG', why, 'the suite never finished - not a catch'))
            loose += 1
            continue
        finally:
            with open(path, 'w', encoding='utf-8', newline='') as f:
                f.write(orig[path])
        fired = [l.strip()[5:].strip() for l in r.stdout.split('\n') if l.startswith('FAIL')]
        if r.returncode == 0:
            print('  %-5s %-76s %s' % ('GREEN', why, 'NOT CAUGHT'))
            bad += 1
        elif fired:
            print('  %-5s %-76s %s' % ('red', why, 'caught by: ' + fired[0][:44]))
        else:
            print('  %-5s %-76s %s' % ('red', why,
                  'caught, but by a non-zero exit with no check named - a crash is not a catch'))
            loose += 1
    print()
    if bad:
        print('%d MUTATIONS SURVIVED OR SKIPPED' % bad)
    elif loose:
        print('every mutation was caught, but %d only by a crash' % loose)
    else:
        print('every mutation was caught, each by a named check')
    return 1 if bad or loose else 0


if __name__ == '__main__':
    sys.exit(main())
