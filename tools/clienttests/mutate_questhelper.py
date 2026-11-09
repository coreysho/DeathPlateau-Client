#!/usr/bin/env python3
"""Mutation test for tools/clienttests/run_questhelpertest.py.

Breaks the quest helper one plausible way at a time and checks the suite notices - and notices by
NAMING a check, not by crashing.

THE ONE THESE EXIST FOR is the first mutation below: pointing at the first step of the leg rather
than at the step in front of you. That is not a crash and not a wrong number; it is a marker that
is always one step behind, which is how the plugin shipped and what a player reported. A suite
that could not tell those two apart would have passed on the broken version.

The rest are the ways a step table turns into a confident lie: a leg chosen off by one, a quest
picked for the player, a world tile treated as a scene tile, an item step offered to somebody who
has not got the item.

    python3 tools/clienttests/mutate_questhelper.py [filter]
"""
import os
import subprocess
import sys

# THE WORKING TREE IS NEVER WRITTEN. Mutations go into a throwaway copy of the repository.
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from mutate_guard import read_source, workspace  # noqa: E402  (after the sys.path line, necessarily)

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
PLUGIN = os.path.join(ROOT, 'src/main/java/jagex2/client/plugin/builtin/QuestHelperPlugin.java')
CONTEXT = os.path.join(ROOT, 'src/main/java/jagex2/client/plugin/PluginContext.java')
RUNNER = os.path.join(HERE, 'run_questhelpertest.py')

MUTS = [
    # ---- WHICH STEP. The bug this suite was written for.
    (PLUGIN, 'the leg\'s first step marked instead of the one in front of you, which is the bug',
     '\tprivate Spot look(Stage st) {\n\t\tif (st == null) {\n\t\t\treturn null;\n\t\t}',
     '\tprivate Spot look(Stage st) {\n\t\tif (st == null) {\n\t\t\treturn null;\n\t\t}\n'
     '\t\tif (true) {\n\t\t\tStep lead = leadStep(st);\n'
     '\t\t\treturn lead == null || lead.at == null ? null\n'
     '\t\t\t\t: at(lead, ctx.worldToSceneX(lead.at[0]), ctx.worldToSceneZ(lead.at[1]), lead.shown());\n\t\t}'),
    (PLUGIN, 'the npcs in the scene ignored, so only tiles are ever marked',
     '\t\tList<Actor> npcs = ctx.getNpcs();',
     '\t\tList<Actor> npcs = new ArrayList<>();'),
    (PLUGIN, 'the steps walked backwards, so the last thing in the leg leads',
     '\t\tfor (int i = start; i < st.steps.size(); i++) {\n\t\t\tStep s = st.steps.get(i);\n\t\t\tif (s.id < 0) {',
     '\t\tfor (int i = st.steps.size() - 1; i >= start; i--) {\n\t\t\tStep s = st.steps.get(i);\n\t\t\tif (s.id < 0) {'),
    (PLUGIN, 'an npc matched by nothing, so the first npc in the scene is marked whoever it is',
     '\t\t\t\t\tif (a != null && a.npcId == s.id) {',
     '\t\t\t\t\tif (a != null) {'),

    # ---- WHICH LEG. Stage granularity is all there is, so being off by one leg is the whole panel.
    (PLUGIN, 'the leg taken as the first one past the varp rather than the last one before it',
     '\t\t\tif (st.from <= value && (best == null || st.from >= best.from)) {',
     '\t\t\tif (st.from <= value && (best == null || st.from <= best.from)) {'),
    (PLUGIN, 'the varp read off by one, so every quest shows the leg after the one you are on',
     '\t\treturn q.stageIsVarbit ? ctx.getVarbit(q.stageVarId) : ctx.getVarp(q.stageVarId);',
     '\t\treturn (q.stageIsVarbit ? ctx.getVarbit(q.stageVarId) : ctx.getVarp(q.stageVarId)) + 1;'),
    (PLUGIN, 'a finished quest still counted as in progress',
     '\t\treturn q.done > 0 && this.questValue(q) >= q.done;',
     '\t\treturn false;'),

    # ---- WHOSE CHOICE. The plugin used to pick a quest for the player and got it wrong five
    # ---- times in six, which is worse than an empty page with a list on it.
    (PLUGIN, 'a quest picked for the player again, the first started one in table order',
     '\t\tif (this.pinned == null) {\n\t\t\treturn null;\n\t\t}',
     '\t\tif (this.pinned == null) {\n\t\t\tfor (Quest q : this.quests) {\n'
     '\t\t\t\tif (this.started(q) && !this.finished(q)) {\n\t\t\t\t\treturn q;\n\t\t\t\t}\n\t\t\t}\n'
     '\t\t\treturn null;\n\t\t}'),

    # ---- WHERE. A world tile out of the table is not a scene tile, and the difference is the
    # ---- width of whatever chunk of the map happens to be loaded.
    (CONTEXT, 'the scene base taken from the zone packet again, which is a different number',
     '\t\treturn sceneTileX + this.client.sceneBaseTileX;',
     '\t\treturn sceneTileX + this.client.baseX;'),
    (PLUGIN, 'a step tile used as a scene tile, with no conversion at all',
     '\t\t\tint x = ctx.worldToSceneX(s.at[0]);\n\t\t\tint z = ctx.worldToSceneZ(s.at[1]);',
     '\t\t\tint x = s.at[0];\n\t\t\tint z = s.at[1];'),
    (PLUGIN, 'a tile off the edge of the scene marked anyway, so the box points at a guess',
     '\t\t\tif (ctx.isInScene(x, z)) {',
     '\t\t\tif (true) {'),

    # ---- THE INVENTORY. "Wear the amulet" is a step only while the amulet is in the bag.
    (PLUGIN, 'an item step offered whether or not the item is carried, so the panel sticks on it',
     '\t\t\tif ("obj".equals(s.kind) && this.carrying(s)) {',
     '\t\t\tif ("obj".equals(s.kind)) {'),
    (PLUGIN, 'the item step dropped entirely, which is what skipped "wear it" before',
     '\t\t\tif ("obj".equals(s.kind) && this.carrying(s)) {',
     '\t\t\tif (false) {'),
    # NOT MUTATED: the -1 that means "an inventory step has nowhere to stand". Leaving the fields
    # at 0 is an EQUIVALENT mutant, deleted rather than chased - Client.projectFromGround refuses
    # anything under 128 scene units, so tile 0 on either axis cannot be drawn whatever asks for
    # it. The -1 is there to say so in the plugin rather than to rely on that guard.

    # ---- THE GUIDE'S WORDING. A leg can carry eleven headings and the panel shows one.
    (PLUGIN, 'the leg\'s first heading shown whatever step is being named',
     '\t\t\tif (head.at <= index) {\n\t\t\t\ttext = head.text;\n\t\t\t}',
     '\t\t\tif (text == null) {\n\t\t\t\ttext = head.text;\n\t\t\t}'),
    (PLUGIN, 'the leg\'s last heading shown instead of the one the step sits under',
     '\t\t\tif (head.at <= index) {\n\t\t\t\ttext = head.text;\n\t\t\t}',
     '\t\t\tif (true) {\n\t\t\t\ttext = head.text;\n\t\t\t}'),
    (PLUGIN, 'the heading keyed on the leg\'s first step rather than on the one being named',
     '\t\t\tString heading = headingFor(st, lead == null ? 0 : indexOf(st, lead));',
     '\t\t\tString heading = headingFor(st, 0);'),

    # ---- WHAT TO BRING, AND WHAT THE QUEST HANDS OVER. 819 of the 1371 item rows are the
    # ---- second kind, and listing those as missing told a player to fetch a key out of a book
    # ---- they have not found.
    (PLUGIN, 'every item asked for again, the quest\'s own included',
     '\t\t\tif (n.bring && n.id >= 0 && ctx.countObj(n.id) < n.n) {',
     '\t\t\tif (n.id >= 0 && ctx.countObj(n.id) < n.n) {'),
    (PLUGIN, 'nothing asked for at all, so a leg never names what to bring',
     '\t\t\tif (n.bring && n.id >= 0 && ctx.countObj(n.id) < n.n) {',
     '\t\t\tif (false) {'),
    (PLUGIN, 'the bring flag never read off the table, so everything reads as quest-given',
     '\t\t\t\tneed.bring = "true".equals(String.valueOf(m.get("bring")));',
     '\t\t\t\tneed.bring = false;'),

    # ---- HOW FAR INTO A LEG. The bag is the only record inside one.
    (PLUGIN, 'the leg always started from its first step, which is how it stuck on the bookcase',
     '\t\tint start = this.reached(this.tracked(), st);',
     '\t\tint start = 0;'),
    (PLUGIN, 'the LAST step that wants a held item taken rather than the first',
     '\t\t\t\tif (seen.contains(obj)) {\n\t\t\t\t\tcontinue;\n\t\t\t\t}\n\t\t\t\tseen.add(obj);',
     '\t\t\t\tif (false) {\n\t\t\t\t\tcontinue;\n\t\t\t\t}\n\t\t\t\tseen.add(obj);'),
    (PLUGIN, 'an item counted whether or not it is in the bag, so the leg jumps to its end',
     '\t\t\t\tif (ctx.countObj(obj.intValue()) > 0 && i > start) {',
     '\t\t\t\tif (i > start) {'),
    (PLUGIN, 'a brought item counted as progress, so a bucket skips half a leg',
     '\t\t\t\tif (q != null && q.brought.contains(obj)) {\n\t\t\t\t\tcontinue;\n\t\t\t\t}',
     '\t\t\t\tif (false) {\n\t\t\t\t\tcontinue;\n\t\t\t\t}'),
    (PLUGIN, 'the shopping list never collected, which is the same thing one layer down',
     '\t\t\t\tif (need.bring && need.id >= 0 && !q.brought.contains(Integer.valueOf(need.id))) {',
     '\t\t\t\tif (false) {'),
    (PLUGIN, 'a step\'s held item ignored, so "Use the key on the wall" is about no key',
     '\t\t\tint held = this.item;',
     '\t\t\tint held = -1;'),
    (PLUGIN, 'half an item step offered, so "use the knife on it" needs no knife',
     '\t\t\tif (ctx.countObj(objs[i]) <= 0) {\n\t\t\t\treturn false;\n\t\t\t}',
     '\t\t\tif (false) {\n\t\t\t\treturn false;\n\t\t\t}'),

    # ---- THE SLOT IN THE BAG.
    (PLUGIN, 'the repaint asked for an item the player has not got',
     '\t\tint[] objs = sp.step.objs();\n\t\tint held = 0;',
     '\t\tif (true) {\n\t\t\treturn sp.step.objs();\n\t\t}\n\t\tint[] objs = sp.step.objs();\n\t\tint held = 0;'),
    (PLUGIN, 'every slot holding the item boxed rather than the first',
     '\t\t\t\t\t// ONE SLOT PER ITEM.',
     '\t\t\t\t\tcontinue;\n\t\t\t\t\t// ONE SLOT PER ITEM.'),
    (PLUGIN, 'only the first of a step\'s two items marked',
     '\t\t\tfor (int i = 0; i < want.length; i++) {',
     '\t\t\tfor (int i = 0; i < want.length && i < 1; i++) {'),
    (PLUGIN, 'the sidebar never asked to repaint, so the mark comes and goes with the mouse',
     '\t\tif (want > 0 || this.marked > 0) {\n\t\t\tctx.requestSidebarRedraw();\n\t\t}',
     '\t\tif (false) {\n\t\t\tctx.requestSidebarRedraw();\n\t\t}'),
    (PLUGIN, 'the repaint asked for every tick, panel or no panel',
     '\t\tif (want > 0 || this.marked > 0) {',
     '\t\tif (true) {'),
    (CONTEXT, 'the slot found in the window\'s coordinates rather than the sidebar\'s',
     '\t\treturn open == null ? null : this.cellBounds(open, 0, 0, INVENTORY_COM, cell);',
     '\t\treturn open == null ? null : this.cellBounds(open, 8, 8, INVENTORY_COM, cell);'),

    # ---- THE TABLE AND THE PANEL.
    (PLUGIN, 'the quest table never read, so the page is empty and says nothing is loaded',
     '\t\t\t\t\tthis.quests.add(quest((Map<?, ?>) entry));',
     '\t\t\t\t\tif (this.quests.size() < 0) {\n\t\t\t\t\t\tthis.quests.add(quest((Map<?, ?>) entry));\n\t\t\t\t\t}'),
]


def main():
    only = sys.argv[1] if len(sys.argv) > 1 else None
    orig = {}
    for path in {m[0] for m in MUTS}:
        orig[path] = read_source(path)
    _work, inside = workspace('questhelper')
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
            with open(inside(path), 'w', encoding='utf-8', newline='') as f:
                f.write(orig[path].replace(find, repl))
            r = subprocess.run([sys.executable, inside(RUNNER)], capture_output=True, text=True,
                               timeout=900)
        except subprocess.TimeoutExpired:
            print('  %-5s %-76s %s' % ('HUNG', why, 'the suite never finished - not a catch'))
            loose += 1
            continue
        finally:
            with open(inside(path), 'w', encoding='utf-8', newline='') as f:
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
