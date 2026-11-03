#!/usr/bin/env python3
"""Breaks the client's half of saved logins one plausible way at a time, and checks a NAMED check notices.

run_logintokentest.py passing says the five phases ran. It does not say they would have NOTICED,
and for this code that is the only question worth asking - because the feature has already failed
twice in one day without anything going red. Once on the live world, where the server ignored the
request and said nothing about it; once in the harness, which was talking to a stale engine and
blamed the client. Both times every check that existed was green.

So: each mutation below is a mistake somebody could actually make in `login()` or in the packet
handler - the remember byte never sent, the token sent outside the RSA envelope, the revision left
at 381, a dead token kept on the card - applied to a COPY of the repository, run, and put back.

A mutation nobody catches is printed GREEN, which is the whole reason this file exists.

ONE ENGINE FOR THE WHOLE RUN. The suite starts it and every mutation's phases are driven against
it with --port, because starting one per mutation is twenty-five seconds apiece for no gain: the
mutations are all in the client, and the engine never changes. Each run still gets its own scratch
home, which is what the phases actually depend on.

    python3 tools/clienttests/mutate_logintoken.py
    python3 tools/clienttests/mutate_logintoken.py "remember byte"   # just the ones that match
"""
import os
import subprocess
import sys

# A copy of the repository is mutated, never the working tree: a kill mid-run cannot leave a
# broken file in a temp directory nobody builds from. mutate_guard.workspace has the reasoning,
# and its check() is the standalone pass that every pattern still matches its source exactly once.
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from mutate_guard import read_source, workspace  # noqa: E402  (after the sys.path line, necessarily)
import run_logintokentest as harness  # noqa: E402  (same reason - and the real one, not the copy's)

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
CLIENT = os.path.join(ROOT, 'src/main/java/jagex2/client/Client.java')
RUNNER = os.path.join(HERE, 'run_logintokentest.py')

# Clear of the harness's own 43994-43996, so a mutation run and a plain run do not fight over a
# port - and three contiguous, for the reason written above PORT in the runner.
PORT = 44294

MUTS = [
    # ------------------------------------------------------------------ THE REQUEST
    # The byte that asks for a saved login. This mutation IS the live bug, moved to the client: a
    # login that never asks is accepted, played and logged like any other, and the only sign is a
    # card that says "name only" days later.
    (CLIENT, 'the remember byte never asking for a saved login',
     '\t\t\t\tthis.out.p1(wantsToken ? 1 : 0);',
     '\t\t\t\tthis.out.p1(0);'),
    (CLIENT, 'Remember me ignored, so every login asks for one',
     '\t\t\t\tboolean wantsToken = this.rememberLogin && this.loginToken.length() == 0;',
     '\t\t\t\tboolean wantsToken = this.loginToken.length() == 0;'),
    # NOT HERE, DELIBERATELY: dropping `&& this.loginToken.length() == 0`, so a token login asks
    # for a second token as well. It was written, run, and survived - and it survived because it
    # is not a defect. LoginToken is minted on the server only for `remember && !saved`, so a
    # token login that asks is simply not answered, and even the log line is unchanged (it tests
    # the token before the flag). The two guards are redundant on purpose and the server's is the
    # load-bearing one; if that `!saved` ever goes, every token login mints another token and
    # churns through the five an account may keep. A mutation with no observable consequence does
    # not belong in a suite whose whole output is "a named check noticed".
    (CLIENT, 'the saved login left out of the block it proves',
     '\t\t\t\tthis.out.pjstr(this.loginToken);',
     '\t\t\t\tthis.out.pjstr("");'),
    (CLIENT, 'the two fields written in the wrong order',
     '\t\t\t\tthis.out.p1(wantsToken ? 1 : 0);\n\t\t\t\tthis.out.pjstr(this.loginToken);',
     '\t\t\t\tthis.out.pjstr(this.loginToken);\n\t\t\t\tthis.out.p1(wantsToken ? 1 : 0);'),

    # THE ENVELOPE. A token is worth the account until it is cancelled, so it has to travel inside
    # the RSA block exactly as the password does. Written after rsaenc it goes out in the clear -
    # and the login still works, which is what makes this worth a mutation.
    (CLIENT, 'the saved login sent in the clear, outside the RSA envelope',
     '\t\t\t\tthis.out.p1(wantsToken ? 1 : 0);\n\t\t\t\tthis.out.pjstr(this.loginToken);\n\t\t\t\tthis.out.rsaenc(LOGIN_RSAN, LOGIN_RSAE);',
     '\t\t\t\tthis.out.rsaenc(LOGIN_RSAN, LOGIN_RSAE);\n\t\t\t\tthis.out.p1(wantsToken ? 1 : 0);\n\t\t\t\tthis.out.pjstr(this.loginToken);'),

    # THE GATE. The build number is what tells the server the two fields are there to read. A
    # client that sends them under an older number has them read as something else entirely.
    # THIS PATTERN TRACKS THE CLIENT'S CURRENT NUMBER and must be bumped with it: a stale literal
    # matches nothing, and mutate_guard counts a 0-match pattern as a survivor that tests nothing.
    # It went stale once already, at 382 -> 383 (PING/PONG), and moved again at 383 -> 384 (VARP_BATCH).
    (CLIENT, 'the revision left a build behind, with the two new fields still sent',
     '\t\t\t\tthis.login.p2(384);',
     '\t\t\t\tthis.login.p2(383);'),
    (CLIENT, 'the block length not counting the fields that were added to it',
     '\t\t\t\tthis.login.p1(this.out.pos + 36 + 1 + 1 + 2);',
     '\t\t\t\tthis.login.p1(this.out.pos + 36 + 1 + 1 + 1);'),

    # ------------------------------------------------------------------ THE CARD
    (CLIENT, "the card's saved login not picked up when it is clicked",
     '\t\t\t\t\tthis.loginToken = profile.token;',
     '\t\t\t\t\tthis.loginToken = "";'),

    # ------------------------------------------------------------------ WHAT COMES BACK
    (CLIENT, 'the token the server hands back dropped on the floor',
     '\t\t\t\tif (token.length() > 0) {\n\t\t\t\t\tthis.loginToken = token;\n\t\t\t\t\tLoginProfiles.rememberToken(this.username, token);\n\t\t\t\t}',
     '\t\t\t\tif (false) {\n\t\t\t\t\tthis.loginToken = token;\n\t\t\t\t\tLoginProfiles.rememberToken(this.username, token);\n\t\t\t\t}'),
    (CLIENT, 'the token kept for this session but never written to disk',
     '\t\t\t\t\tLoginProfiles.rememberToken(this.username, token);\n',
     ''),
    (CLIENT, 'the token written to disk but not kept for this session',
     '\t\t\t\t\tthis.loginToken = token;\n',
     ''),
    (CLIENT, 'the token saved against whatever name happens to be in the field',
     '\t\t\t\t\tLoginProfiles.rememberToken(this.username, token);',
     '\t\t\t\t\tLoginProfiles.rememberToken("somebody_else", token);'),

    # ------------------------------------------------------------------ A SAVED LOGIN REFUSED
    # Expired, cancelled, or for an account that no longer exists. The player never typed it, so
    # "invalid password" is not something they can act on - and a dead token left on the card
    # refuses the same login for ever.
    (CLIENT, 'a refused saved login left on the card to fail again',
     '\t\t\t\t\tLoginProfiles.forgetToken(arg0);\n',
     ''),
    (CLIENT, 'a refused saved login still held in the field it was sent from',
     '\t\t\t\t\tthis.loginToken = "";\n\t\t\t\t\tthis.password = "";\n\t\t\t\t\tthis.titleLoginField = 1;',
     '\t\t\t\t\tthis.password = "";\n\t\t\t\t\tthis.titleLoginField = 1;'),
    (CLIENT, 'the cursor left in the name field, not where the password has to go',
     '\t\t\t\t\tthis.titleLoginField = 1;\n\t\t\t\t\tthis.loginMessage0 = "Your saved login has expired.";',
     '\t\t\t\t\tthis.titleLoginField = 0;\n\t\t\t\t\tthis.loginMessage0 = "Your saved login has expired.";'),
    (CLIENT, 'a dead saved login reported as a wrong password',
     '\t\t\t\t\tthis.loginMessage0 = "Your saved login has expired.";\n\t\t\t\t\tthis.loginMessage1 = "Please enter your password.";',
     ''),
]


def main():
    only = sys.argv[1] if len(sys.argv) > 1 else None
    why = harness.cannot_run(harness.ENGINE)
    if why:
        # 2, as the runner does: "could not run" is not a finding either way.
        print('mutate_logintoken: SKIP - %s.' % why)
        return 2

    orig = read_source(CLIENT)
    # Written into a copy, never into the working tree - see mutate_guard.workspace.
    work, inside = workspace('logintoken')
    muts = [m for m in MUTS if not only or only in m[1]]
    print('running %d of %d mutations' % (len(muts), len(MUTS)))

    port, webport, mgmtport = harness.ports(PORT)
    log = os.path.join(work, 'engine.log')
    print('starting one engine on %d for the whole run (log: %s)' % (port, log))
    engine = harness.start_engine(harness.ENGINE, log, port, webport, mgmtport)
    print('engine ready')

    bad = loose = 0
    try:
        for path, why, find, repl in muts:
            n = orig.count(find)
            if n != 1:
                print('  SKIP (pattern %s) %s'
                      % ('not found' if n == 0 else 'matches %d times' % n, why))
                bad += 1
                continue
            try:
                with open(inside(path), 'w', encoding='utf-8', newline='') as f:
                    f.write(orig.replace(find, repl))
                r = subprocess.run([sys.executable, inside(RUNNER), '--engine', harness.ENGINE,
                                    '--port', str(port), '--webport', str(webport)],
                                   capture_output=True, text=True, timeout=900)
            except subprocess.TimeoutExpired:
                print('  %-5s %-72s %s' % ('HUNG', why, 'the suite never finished - not a catch'))
                loose += 1
                continue
            finally:
                with open(inside(path), 'w', encoding='utf-8', newline='') as f:
                    f.write(orig)
            fired = [l.strip()[5:].strip() for l in r.stdout.split('\n') if l.strip().startswith('FAIL')]
            if r.returncode == 0:
                print('  %-5s %-72s %s' % ('GREEN', why, 'NOT CAUGHT'))
                bad += 1
            elif fired:
                print('  %-5s %-72s %s' % ('red', why, 'caught by: ' + fired[0][:44]))
            else:
                print('  %-5s %-72s %s' % ('red', why,
                      'caught, but by a non-zero exit with no check named - a crash is not a catch'))
                loose += 1
    finally:
        # The runner's own, which knows both platforms: taskkill /T on Windows, the process group
        # everywhere else. This used to call taskkill directly, which exists on one of the two -
        # on a Linux runner it would have thrown after every mutation had already been reported,
        # so the output would have read as a clean run under a red job.
        harness.stop_engine(engine, (port, webport, mgmtport))

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
