#!/usr/bin/env python3
"""Mutation test for tools/clienttests/run_titlescreentest.py.

Breaks the login screen's hit tests one way at a time and checks run_titlescreentest notices -
and notices by NAMING a check, not by crashing.

These are the mutations that matter because none of them change the picture. Move a hit test and
the screen renders identically; the button is still drawn in the right place, it just stops
answering, or answers somewhere it should not. The title music toggle shipped exactly like that
for nineteen pixels. A rendering test cannot see any of this, which is why these predicates have
a test of their own and why that test has this.

    python3 tools/clienttests/mutate_titlescreen.py [filter]
"""
import os
import subprocess
import sys

# THE WORKING TREE IS NEVER WRITTEN. Mutations go into a throwaway copy of the repository - see
# mutate_guard.workspace - and its check() is the standalone pass that every pattern below still
# matches its source exactly once.
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from mutate_guard import workspace  # noqa: E402  (after the sys.path line, necessarily)

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
SCREEN = os.path.join(ROOT, 'src/main/java/jagex2/client/TitleScreen.java')
RUNNER = os.path.join(HERE, 'run_titlescreentest.py')

MUTS = [
    # ---- THE FIELDS
    (SCREEN, 'the form width ignored, so a click anywhere on the row focuses a field',
     '\t\tif (x < FORM_X || x > FORM_X + FORM_W) {\n\t\t\treturn -1;\n\t\t}\n',
     ''),
    (SCREEN, 'the username field reaching down over the gap into the password one',
     '\t\tif (y >= USER_Y && y <= USER_Y + FIELD_H) {',
     '\t\tif (y >= USER_Y && y <= PASS_Y + FIELD_H) {'),
    (SCREEN, 'the password field starting where the username field does',
     '\t\tif (y >= PASS_Y && y <= PASS_Y + FIELD_H) {',
     '\t\tif (y >= USER_Y && y <= PASS_Y + FIELD_H) {'),

    # ---- THE BUTTON
    (SCREEN, 'ENTER reaching up into the password field, so typing there submits',
     '\t\treturn x >= FORM_X && x <= FORM_X + FORM_W && y >= BUTTON_Y && y <= BUTTON_Y + BUTTON_H;',
     '\t\treturn x >= FORM_X && x <= FORM_X + FORM_W && y >= PASS_Y && y <= BUTTON_Y + BUTTON_H;'),
    (SCREEN, 'ENTER ignoring x, so the whole width of the screen submits',
     '\t\treturn x >= FORM_X && x <= FORM_X + FORM_W && y >= BUTTON_Y && y <= BUTTON_Y + BUTTON_H;',
     '\t\treturn y >= BUTTON_Y && y <= BUTTON_Y + BUTTON_H;'),

    # ---- THE ROW. It is centred, so every one of these moves with the number of accounts.
    (SCREEN, 'the add button left out of the row width, so every card is drawn off its hit test',
     '\t\treturn CX - (count * PROFILE_W + count * PROFILE_GAP + ADD_W) / 2;',
     '\t\treturn CX - (count * PROFILE_W + count * PROFILE_GAP) / 2;'),
    (SCREEN, 'the gap left out of the step, so the cards and their tests drift apart',
     '\t\t\tat += PROFILE_W + PROFILE_GAP;\n\t\t}\n\t\treturn -1;\n\t}\n\n\t/**\n\t * The x on an account.',
     '\t\t\tat += PROFILE_W;\n\t\t}\n\t\treturn -1;\n\t}\n\n\t/**\n\t * The x on an account.'),
    (SCREEN, 'a card claiming the gap after it as well',
     '\t\t\tif (x >= at && x <= at + PROFILE_W) {\n\t\t\t\treturn i;',
     '\t\t\tif (x >= at && x <= at + PROFILE_W + PROFILE_GAP) {\n\t\t\t\treturn i;'),
    (SCREEN, 'the row answering above and below itself',
     '\t\tif (y < PROFILE_Y || y > PROFILE_Y + PROFILE_H) {\n\t\t\treturn -1;\n\t\t}\n',
     ''),
    (SCREEN, 'the add button placed before the cards instead of after them',
     '\t\tint at = rowStart(count) + count * (PROFILE_W + PROFILE_GAP);',
     '\t\tint at = rowStart(count);'),

    # ---- THE x. Inside the card, so these are the ones that would sign a player into the
    # account they were trying to remove.
    (SCREEN, 'the x spreading across the whole card, so nothing can be clicked but remove',
     '\t\t\tif (x >= at + PROFILE_W - FORGET_W - 4 && x <= at + PROFILE_W) {',
     '\t\t\tif (x >= at && x <= at + PROFILE_W) {'),
    (SCREEN, 'the x spreading down the whole card height',
     '\t\tif (y < PROFILE_Y || y > PROFILE_Y + 26) {',
     '\t\tif (y < PROFILE_Y || y > PROFILE_Y + PROFILE_H) {'),

    # ---- THE PLAYER COUNT. This one shipped: the live client's footer read "-1 PLAYERS ONLINE"
    # because the guard existed only in a commit message. It is a method rather than an `if` so
    # that it can be tested at all - with no font both branches of an `if` draw nothing.
    (SCREEN, 'the no-count guard dropped, so the footer prints whatever it was handed',
     '\t\treturn online < 0 ? null : online + " PLAYERS ONLINE";',
     '\t\treturn online + " PLAYERS ONLINE";'),
    (SCREEN, 'the guard off by one, so -1 prints and -2 does not',
     '\t\treturn online < 0 ? null : online + " PLAYERS ONLINE";',
     '\t\treturn online < -1 ? null : online + " PLAYERS ONLINE";'),
]


def main():
    only = sys.argv[1] if len(sys.argv) > 1 else None
    orig = {}
    for path in (SCREEN,):
        with open(path, encoding='utf-8', newline='') as f:
            orig[path] = f.read()
    _work, inside = workspace('titlescreen')
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
