#!/usr/bin/env python3
"""Which chat commands a player can use, and which need staff.

WHY THIS EXISTS. A player was asked for a ping reading and answered "it isn't showing me
anything". ::fpson lived inside `if (this.staffmodlevel >= 2)`, so for everybody who was not staff
it did nothing AND said nothing - a :: line that matches no local branch is sent to the server,
which has no such command and ignores it without logging. Nothing anywhere reported a problem. The
only way left to get a number out of a player was to ssh in and sample the ping histogram while
they happened to be online.

The split that matters is not staff vs player, it is LOOKING vs CHANGING. A player's own frame rate
and round trip are facts about their own connection; ::noclip, ::clientdrop and the rest change the
world or the client's connection to it. So this checks the readout is reachable by anyone and that
the rest is still gated - the second half matters just as much, because "ungate ::fpson" is one
careless brace away from ungating ::noclip with it.

Needs nothing: no display, no network, no compile. The question is where a line sits in the source.

    python3 tools/clienttests/run_fpsgatetest.py
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
CLIENT = os.path.join(ROOT, 'src/main/java/jagex2/client/Client.java')

GATE = 'if (this.staffmodlevel >= 2) {'

# Reachable by anybody: these only read state back to the player who typed them.
OPEN = ['::fpson', '::fpsoff']
# Staff only: these change the world, or this client's connection to it.
GATED = ['::noclip', '::clientdrop', '::lag', '::prefetchmusic']

fails = 0


def check(ok, what):
    global fails
    print(('  ok   ' if ok else 'FAIL   ') + what)
    if not ok:
        fails += 1


def main():
    src = open(CLIENT, encoding='utf-8').read()

    at = src.find(GATE)
    check(at >= 0, 'the staff gate is where this test expects it')
    if at < 0:
        raise SystemExit('cannot locate %r - not checking anything' % GATE)

    # the gate's own block, by counting braces from its opening one
    depth = 0
    end = -1
    for i in range(at + len(GATE) - 1, len(src)):
        if src[i] == '{':
            depth += 1
        elif src[i] == '}':
            depth -= 1
            if depth == 0:
                end = i
                break
    check(end > at, 'and its block closes, so there is an inside and an outside')
    if end < 0:
        raise SystemExit('unbalanced braces after the gate - not checking anything')

    inside = src[at:end]
    outside = src[:at] + src[end:]

    print()
    print('a player can read their own numbers')
    for cmd in OPEN:
        handled = '"%s"' % cmd
        check(handled in outside, '%s is handled outside the staff gate' % cmd)
        check(handled not in inside, '...and not left inside it as well' % ())

    print()
    print('and everything that changes something still needs staff')
    for cmd in GATED:
        handled = '"%s"' % cmd
        check(handled in inside, '%s is inside the staff gate' % cmd)
        check(handled not in outside, '...and nowhere else' % ())

    print()
    print('%s' % ('ALL PASS' if fails == 0 else '%d FAILED' % fails))
    sys.exit(0 if fails == 0 else 1)


if __name__ == '__main__':
    main()
