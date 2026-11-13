#!/usr/bin/env python3
"""A private message draws the same rank icon as everything else, and grants nothing.

THE CONTRACT. The engine sends one byte with every private message: the rank icon, as the number
engine/entity/ChatCrown.ts picked. Two things in this client then read it, and they are not the
same kind of thing at all:

  - WHICH ICON TO DRAW. Everywhere else a name appears - over the head, on public chat, in the
    right-click menu - the icon comes from the appearance block through ChatIcons.forPlayer. The
    private message path has a ladder of its own, inline in Client's packet decode, and nothing
    compared the two. They disagreed: forPlayer tests 7 and 8 before its ">= 6", the private
    message ladder tested ">= 6" first, so a Veteran's message would have arrived wearing the
    second owner's blue and gold crown. The ONLY thing standing between an ornament and the
    highest crown in the game is the order of those branches.

  - WHAT THE SENDER MAY DO. The same byte gates the ignore list and picks the message type. Type 7
    is a staff line and is drawn whatever the recipient set their private chat to; type 3 respects
    it. Crowns 7 and 8 are the Veteran and Helper emblems and carry no rights whatever, so they
    must be type 3 and must stay ignorable. Get this wrong and handing somebody an ornament
    quietly lets them past a block and past a privacy setting - and taking the ornament away is
    the only way to stop them.

HOW IT ASKS. forPlayer is RUN, not read: it is compiled and asked what each crown draws. The
private message ladder cannot be run - it is twenty lines deep inside a packet decode - so it is
read out of Client.java and simulated in branch order, which is the thing that was wrong. A
reordering that puts ">= 6" back in front of 7 fails this.

    python3 tools/clienttests/run_pmcrowntest.py
"""
import os
import re
import shutil
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
SRC = os.path.join(ROOT, 'src/main/java')
# Both roots, as build.gradle declares: Theme and the rest of the launcher compile into the client.
LAUNCHER_SRC = os.path.join(ROOT, 'launcher/src')
CLIENT = os.path.join(SRC, 'jagex2/client/Client.java')

# Only the crowns chatCrown can actually return. 3 is folded into 2 there and never sent.
CROWNS = [0, 1, 2, 4, 5, 6, 7, 8]
# The ranks that are ornaments: no rights, and the recipient's settings still apply.
ORNAMENTS = {7, 8}

PROBE = '''import jagex2.client.ChatIcons;

public class CrownProbe {
    public static void main(String[] args) {
        StringBuilder out = new StringBuilder();
        for (String a : args) {
            int crown = Integer.parseInt(a);
            // The badge nibble is left at 0: this asks about the rank icon alone, which is the
            // half the private message byte carries.
            out.append(crown).append('=').append(ChatIcons.forPlayer(crown)).append('\\n');
        }
        System.out.print(out);
    }
}
'''


def ladder(text):
    """The private message icon ladder, as a list of (test, marker, msgtype) in branch order."""
    # The INCOMING MESSAGE_PRIVATE decode. There is a "// MESSAGE_PRIVATE" marker for the outgoing
    # send too, and a window from either one is large enough to reach the other's code, so the
    # anchor is the ring buffer write that only the incoming path does.
    anchor = text.find('this.messageIds[this.privateMessageCount]')
    if anchor == -1:
        return None, None
    # back far enough to take in the ignore-list gate, which sits above the anchor
    start = text.rfind('// MESSAGE_PRIVATE', 0, anchor)
    if start == -1:
        start = max(0, anchor - 2000)
    block = text[start:anchor + 6000]
    end = block.find('} catch (')
    if end != -1:
        block = block[:end]

    branches = []
    # if / else-if arms: one or more "varN == k" / "varN >= k" tests, then the addMessage call
    pattern = re.compile(
        r'(?:\}\s*else\s+)?if\s*\(([^)]*?)\)\s*\{\s*this\.addMessage\(\s*(?:"(@cr[0-9a]@)"\s*\+\s*)?'
        r'JString\.formatDisplayName[^;]*?,\s*\w+\s*,\s*(\d+)\s*\)\s*;',
        re.S)
    for m in pattern.finditer(block):
        branches.append((m.group(1).strip(), m.group(2) or '', int(m.group(3))))
    # the trailing bare else
    tail = re.search(r'\}\s*else\s*\{\s*this\.addMessage\(\s*JString\.formatDisplayName[^;]*?,\s*\w+\s*,\s*(\d+)\s*\)\s*;', block, re.S)
    if tail:
        branches.append(('else', '', int(tail.group(1))))

    gate = re.search(r'if\s*\(([^)]*?)\)\s*\{\s*for\s*\(int\s+\w+\s*=\s*0;[^;]*?ignoreCount', block, re.S)
    return branches, (gate.group(1).strip() if gate else None)


def simulate(branches, crown):
    """Walk the ladder in order, the way the client does, and return (marker, msgtype)."""
    for test, marker, mtype in branches:
        if test == 'else':
            return marker, mtype
        hit = False
        for clause in test.split('||'):
            m = re.search(r'(==|>=|<=)\s*(\d+)', clause)
            if not m:
                continue
            op, n = m.group(1), int(m.group(2))
            if (op == '==' and crown == n) or (op == '>=' and crown >= n) or (op == '<=' and crown <= n):
                hit = True
                break
        if hit:
            return marker, mtype
    return None, None


def gate_applies(gate, crown):
    """Does the ignore list get checked for this crown, per the extracted condition?"""
    for clause in gate.split('||'):
        m = re.search(r'(==|>=|<=)\s*(\d+)', clause)
        if not m:
            continue
        op, n = m.group(1), int(m.group(2))
        if (op == '==' and crown == n) or (op == '>=' and crown >= n) or (op == '<=' and crown <= n):
            return True
    return False


def main():
    javac, java = shutil.which('javac'), shutil.which('java')
    if not javac or not java:
        print('run_pmcrowntest: SKIP - needs javac and java on PATH')
        return 2

    with open(CLIENT, encoding='utf-8') as f:
        text = f.read()
    branches, gate = ladder(text)
    if not branches:
        print('run_pmcrowntest: FAIL - could not find the private message icon ladder in Client.java')
        return 1
    if gate is None:
        print('run_pmcrowntest: FAIL - could not find the ignore-list gate in the private message decode')
        return 1

    work = tempfile.mkdtemp(prefix='pmcrowntest')
    try:
        probe = os.path.join(work, 'CrownProbe.java')
        with open(probe, 'w', encoding='utf-8') as f:
            f.write(PROBE)
        build = subprocess.run([javac, '-nowarn', '-d', work, '-sourcepath', SRC + os.pathsep + LAUNCHER_SRC, probe],
                               capture_output=True, text=True)
        if build.returncode != 0:
            print('run_pmcrowntest: FAIL - the probe did not compile')
            print((build.stderr or build.stdout).strip()[-1200:])
            return 1
        run = subprocess.run([java, '-cp', work, 'CrownProbe'] + [str(c) for c in CROWNS],
                             capture_output=True, text=True)
        if run.returncode != 0:
            print('run_pmcrowntest: FAIL - the probe did not run')
            print((run.stderr or run.stdout).strip()[-1200:])
            return 1
        drawn = {}
        for line in run.stdout.strip().splitlines():
            k, _, v = line.partition('=')
            drawn[int(k)] = v
    finally:
        shutil.rmtree(work, ignore_errors=True)

    bad = 0
    for crown in CROWNS:
        marker, mtype = simulate(branches, crown)
        want = drawn[crown]
        if marker != want:
            print('run_pmcrowntest: FAIL crown %d - a private message draws %r, everything else draws %r'
                  % (crown, marker or '(none)', want or '(none)'))
            bad += 1
        else:
            print('  ok  crown %d draws %-7s on a private message, as it does everywhere else' % (crown, marker or '(none)'))

        # An ornament grants nothing: ordinary message type, and still ignorable.
        if crown in ORNAMENTS:
            if mtype != 3:
                print('run_pmcrowntest: FAIL crown %d is message type %d - type 7 is a staff line and is '
                      'drawn whatever the recipient set their private chat to' % (crown, mtype))
                bad += 1
            else:
                print('  ok    and is an ordinary message type, so the recipient\'s private chat setting still applies')
            if not gate_applies(gate, crown):
                print('run_pmcrowntest: FAIL crown %d skips the ignore list (gate is "%s") - an ornament '
                      'must not let somebody past a block' % (crown, gate))
                bad += 1
            else:
                print('  ok    and is still ignorable')

    if bad:
        print('run_pmcrowntest: %d problem(s)' % bad)
        return 1
    print('run_pmcrowntest: ok - %d crowns, the private message ladder agrees with ChatIcons.forPlayer, '
          'and neither ornament grants anything' % len(CROWNS))
    return 0


if __name__ == '__main__':
    sys.exit(main())
