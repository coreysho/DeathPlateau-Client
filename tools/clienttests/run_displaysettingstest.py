#!/usr/bin/env python3
"""The display settings file, and in particular which way `smoothscale` falls when nobody has said.

What it proves: a player with no settings file gets smoothing OFF (tracker issue 24 - the game
going soft when the scale is turned up read as the client being broken); a player who has
`smoothscale=true` on disk KEEPS it, which is the whole reason there is no migration; `false` is
honoured; a junk value is not smoothing; and the setter round-trips through the file so the
panel's switch still sticks.

Each case is its own JVM with user.home pointed at a temp folder, because DisplaySettings reads
its file once into static fields and every case here is about a fresh start.

    python3 tools/clienttests/run_displaysettingstest.py
"""
import os
import shutil
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
SRC = os.path.join(ROOT, 'src/main/java/jagex2/client')
TEST = os.path.join(HERE, 'DisplaySettingsTest.java')

ok = 0
bad = 0


def check(what, got, want):
    global ok, bad
    if got == want:
        ok += 1
        print('  ok   %s: %r' % (what, got))
    else:
        bad += 1
        # FAIL AT COLUMN 0, ok INDENTED. mutate_guard reads a failure back with
        # `l.startswith('FAIL')`, and a mutation caught only by a non-zero exit with no check
        # named is counted as NOT caught - "a crash is not a catch". An indented FAIL would make
        # every mutation of this suite look like a crash.
        print('FAIL   %s: %r   (want %r)' % (what, got, want))


def run(work, home, mode='read'):
    """One JVM against `home`. Returns the printed keys as a dict."""
    env = dict(os.environ)
    r = subprocess.run(
        [shutil.which('java'), '-Duser.home=' + home, '-cp', work, 'DisplaySettingsTest', mode],
        capture_output=True, text=True, env=env)
    if r.returncode != 0:
        print(r.stdout[-2000:])
        print(r.stderr[-2000:])
        raise SystemExit('run_displaysettingstest: the client threw')
    out = {}
    for line in r.stdout.splitlines():
        if '=' in line:
            k, v = line.split('=', 1)
            out[k.strip()] = v.strip()
    return out


def home_with(root, name, body):
    """A fake home holding .deathplateau/display.properties, or holding nothing."""
    h = os.path.join(root, name)
    os.makedirs(os.path.join(h, '.deathplateau'), exist_ok=True)
    if body is not None:
        with open(os.path.join(h, '.deathplateau', 'display.properties'), 'w') as f:
            f.write(body)
    return h


def main():
    work = tempfile.mkdtemp(prefix='displaysettings')
    try:
        srcs = [os.path.join(SRC, 'DisplaySettings.java'), os.path.join(SRC, 'Layout.java'), TEST]
        r = subprocess.run([shutil.which('javac'), '-nowarn', '-encoding', 'UTF-8', '-d', work] + srcs,
                           capture_output=True, text=True)
        if r.returncode != 0:
            print(r.stderr[-4000:])
            raise SystemExit('run_displaysettingstest: does not compile')

        print('1. a fresh install, with nothing saved')
        # THE DECISION. Smoothing on was what produced the report: turn the game scale up and the
        # whole picture goes soft, text and icons with it.
        check('smoothing is off when no file exists', run(work, home_with(work, 'fresh', None))['smoothscale'], 'false')
        # The neighbouring default is read in the same breath, so a careless edit to the loader
        # that broke every key at once could not pass this file.
        check('...and the game scale is still 100', run(work, home_with(work, 'fresh2', None))['gamescale'], '100')

        print('\n2. a player who already has a value, which is most of them')
        # save() writes EVERY key whenever any one changes, so anybody who has ever resized the
        # window has smoothscale on disk. Flipping the default must not touch them.
        check('smoothscale=true on disk is kept',
              run(work, home_with(work, 'had_on', 'smoothscale=true\n'))['smoothscale'], 'true')
        check('smoothscale=false on disk is kept',
              run(work, home_with(work, 'had_off', 'smoothscale=false\n'))['smoothscale'], 'false')

        print('\n3. a file that does not say cleanly')
        # "Nothing here may throw: a missing or broken file is the fixed screen, as the client
        # always was." Junk is not smoothing, and it is not a crash either.
        check('a junk value is not smoothing',
              run(work, home_with(work, 'junk', 'smoothscale=yes please\n'))['smoothscale'], 'false')
        check('whitespace around true still reads as true',
              run(work, home_with(work, 'spaced', 'smoothscale=  true  \n'))['smoothscale'], 'true')
        check('an empty file is not smoothing',
              run(work, home_with(work, 'empty', ''))['smoothscale'], 'false')

        print('\n4. the panel\'s switch still sticks')
        # The default moved; the setting itself must still work, or the fix for the player who
        # WANTS smoothing would be gone.
        h = home_with(work, 'roundtrip', None)
        check('turning it on reads back as on', run(work, h, 'set-on-read')['smoothscale'], 'true')
        check('...and is still on in the next session', run(work, h)['smoothscale'], 'true')
        # READ DEFENSIVELY, and make the file's existence a check of its own. An earlier draft
        # opened it outright, and the mutation that put the default back to ON crashed this runner
        # with FileNotFoundError instead of failing: setSmoothScale only saves when the value
        # CHANGES, so with the default already on nothing was ever written. A test that throws
        # hides its own tally, which is worse than one that fails.
        saved_path = os.path.join(h, '.deathplateau', 'display.properties')
        check('...and a file was written at all', os.path.exists(saved_path), True)
        saved = ''
        if os.path.exists(saved_path):
            with open(saved_path) as f:
                saved = f.read()
        check('...because it was written to the file', 'smoothscale=true' in saved, True)

        print('\n%d CHECKS, %s' % (ok + bad, 'ALL PASS' if not bad else '%d FAILED' % bad))
        return 1 if bad else 0
    finally:
        shutil.rmtree(work, ignore_errors=True)


if __name__ == '__main__':
    sys.exit(main())
