#!/usr/bin/env python3
"""Mutation test for tools/clienttests/run_iconstest.py.

Breaks the rail's icons one way at a time and checks run_iconstest notices - and notices by
NAMING a check, not by crashing.

EVERY ONE OF THESE IS SILENT IN THE RUNNING CLIENT. Nothing throws, nothing logs, the sidebar
opens and works; two pages simply wear the same picture and the only way to tell them apart is
to click one. That is the state this shipped in, with Ground items and Skills both falling
through to the plain list icon, and no test could have caught it because none existed.

    python3 tools/clienttests/mutate_icons.py [filter]
"""
import os
import subprocess
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from mutate_guard import workspace  # noqa: E402  (after the sys.path line, necessarily)

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
ICONS = os.path.join(ROOT, 'src/main/java/jagex2/client/plugin/ui/Icons.java')
GROUND = os.path.join(ROOT, 'src/main/java/jagex2/client/plugin/builtin/GroundItemsPlugin.java')
IDLE = os.path.join(ROOT, 'src/main/java/jagex2/client/plugin/builtin/IdleNotifierPlugin.java')
RUNNER = os.path.join(HERE, 'run_iconstest.py')

MUTS = [
    # ---- THE BUG AS IT SHIPPED: two pages asking for one icon.
    (GROUND, 'Ground items asking for the icon Skills already has',
     'this.addPanel("Loot nearby", "coins", this.lootList());',
     'this.addPanel("Loot nearby", "chart", this.lootList());'),
    (GROUND, 'Ground items asking for a name nothing draws, so it falls through silently',
     'this.addPanel("Loot nearby", "coins", this.lootList());',
     'this.addPanel("Loot nearby", "loot", this.lootList());'),
    (IDLE, 'the notifier back on the wrench the plugin list already wears',
     'this.addPanel("Idle notifier", "bell", this.testList());',
     'this.addPanel("Idle notifier", "wrench", this.testList());'),

    # ---- THE LOOKUP. A case dropped here is a page quietly demoted to the fallback.
    (ICONS, 'the coins case dropped from the lookup',
     '\t\tif ("coins".equals(name)) {\n\t\t\treturn coins(size, colour);\n\t\t}\n',
     ''),
    (ICONS, 'the bell case dropped from the lookup',
     '\t\tif ("bell".equals(name)) {\n\t\t\treturn bell(size, colour);\n\t\t}\n',
     ''),
    (ICONS, 'the lookup falling back to something other than the documented list icon',
     '\t\treturn list(size, colour);\n\t}',
     '\t\treturn chart(size, colour);\n\t}'),

    # ---- THE SET. isKnown is what the plugin check leans on.
    # The set itself, which grows with every page that wants an icon of its own - mutate_guard
    # reports these two as stale the moment it does, which is how they get updated rather than
    # quietly matching nothing.
    (ICONS, 'coins missing from the known set, so nothing vouches for the name',
     '\tpublic static final String[] KNOWN = { "chart", "coins", "bag", "sprout", "skull", "bell", "wrench", "download", "move", "refresh", "list" };',
     '\tpublic static final String[] KNOWN = { "chart", "bag", "sprout", "skull", "bell", "wrench", "download", "move", "refresh", "list" };'),
    (ICONS, 'bag missing from the known set, so the loot page falls through to the plain list icon',
     '\t\tif ("bag".equals(name)) {\n\t\t\treturn bag(size, colour);\n\t\t}\n',
     ''),
    (ICONS, 'isKnown waving everything through',
     '\t\t\tif (KNOWN[i].equals(name)) {\n\t\t\t\treturn true;\n\t\t\t}',
     '\t\t\tif (KNOWN[i] != null) {\n\t\t\t\treturn true;\n\t\t\t}'),

    # ---- THE DRAWINGS. Two names can map to one picture without either name changing.
    (ICONS, 'coins drawn as the plain list icon, same name, same picture as another',
     '\t\t\t\tg.setStroke(new BasicStroke(Math.max(1.4f, s * 0.09f)));\n\t\t\t\tdouble d = s * 0.46;',
     '\t\t\t\tif (true) {\n\t\t\t\t\tint gap = s / 4;\n\t\t\t\t\tfor (int i = 0; i < 3; i++) {\n\t\t\t\t\t\tg.fillRect(3, gap * i + 4, s - 6, Math.max(1, s / 10));\n\t\t\t\t\t}\n\t\t\t\t\treturn;\n\t\t\t\t}\n\t\t\t\tdouble d = s * 0.46;'),
    (ICONS, 'the bell drawn as nothing at all, which no eye checks',
     '\t\t\t\tg.fill(body);',
     ''),
]


def main():
    only = sys.argv[1] if len(sys.argv) > 1 else None
    orig = {}
    for path in (ICONS, GROUND, IDLE):
        with open(path, encoding='utf-8', newline='') as f:
            orig[path] = f.read()
    _work, inside = workspace('icons')
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
                               timeout=600)
        except subprocess.TimeoutExpired:
            print('  %-5s %-72s %s' % ('HUNG', why, 'the suite never finished - not a catch'))
            loose += 1
            continue
        finally:
            with open(inside(path), 'w', encoding='utf-8', newline='') as f:
                f.write(orig[path])
        fired = [l.strip()[5:].strip() for l in r.stdout.split('\n') if l.startswith('FAIL')]
        if r.returncode == 0:
            print('  %-5s %-72s %s' % ('GREEN', why, 'NOT CAUGHT'))
            bad += 1
        elif fired:
            print('  %-5s %-72s %s' % ('red', why, 'caught by: ' + fired[0][:46]))
        else:
            print('  %-5s %-72s %s' % ('red', why,
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
