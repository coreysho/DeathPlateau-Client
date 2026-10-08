#!/usr/bin/env python3
"""Put the source back, whatever happens to a mutation runner - and check that it is back.

Every mutate_*.py script rewrites real source files in place, one break at a time, and restores
them in a `finally`. That covers a run that fails, throws or times out. It does not cover the
process being killed: a SIGTERM ends Python without running `finally` at all.

That is not hypothetical. A kill landed mid-mutation and left a deliberately broken line sitting
in Client.java - a bare ";" where a log line's text used to be, the exact code the mutation was
testing. The file looked plausible and the build passed, because a mutation is by construction a
small, compiling change, and the next commit would have shipped it as a release to every player's
launcher. Nothing in the normal workflow would have caught it.

So guard() registers atexit for a normal exit or an unhandled exception, and signal handlers for
the kill. Restoring twice is harmless, so every path just writes the originals back.

WHAT THIS STILL CANNOT DO: SIGKILL cannot be caught, and neither can the machine going away. The
guard narrows the window; it does not close it. check() below is the backstop - it reads every
mutate_*.py in this directory and asserts each pattern appears in its file exactly once, which
catches a leftover mutation AND a pattern gone stale against moved source. A stale pattern is
worth catching on its own: the runners report a non-matching pattern as SKIP and count it as a
survivor, so a renamed variable can quietly stop a mutation from testing anything at all.

    python3 tools/clienttests/mutate_guard.py          # check every mutate_*.py
    python3 tools/clienttests/mutate_guard.py jaggrab  # just the ones whose name matches
"""
import atexit
import glob
import importlib.util
import os
import signal
import sys

HERE = os.path.dirname(os.path.abspath(__file__))


def guard(originals):
    """Arrange for {path: contents} to be written back on exit, on an exception, or on a kill.

    Call it once, immediately after reading the originals and before the first mutation. Accepts
    the dict the multi-file runners keep; a single-file runner passes {THE_FILE: text}.
    """
    originals = dict(originals)

    def restore():
        for path, text in originals.items():
            try:
                with open(path, 'w', encoding='utf-8', newline='') as f:
                    f.write(text)
            except OSError as cannot:
                # There is nothing useful to do from inside a handler, but saying which file is
                # still broken is the difference between a known problem and a shipped one.
                print('  COULD NOT RESTORE %s (%s) - CHECK IT BEFORE COMMITTING' % (path, cannot))

    def restore_and_die(signum, _frame):
        restore()
        print('\n  interrupted by signal %d - %d file(s) restored' % (signum, len(originals)))
        sys.exit(1)

    atexit.register(restore)
    for name in ('SIGTERM', 'SIGINT', 'SIGHUP'):
        sig = getattr(signal, name, None)
        if sig is None:
            continue
        try:
            signal.signal(sig, restore_and_die)
        except (OSError, ValueError):
            pass  # not every signal can be caught on every platform, and that is not a failure
    return restore


def load(script):
    """A mutate_*.py as a module, without running its main()."""
    spec = importlib.util.spec_from_file_location(os.path.basename(script)[:-3], script)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def check(only=None):
    """Assert every mutation pattern is present exactly once. Returns the number of problems.

    The MUTS tuples come in three shapes, so they are read from the end, where all three agree
    on (..., why, find, repl):

        (why, find, repl)                     - one source file, named by the module's CLIENT
        (path, why, find, repl)
        (path, runner, why, find, repl)

    Only the path differs, so it comes from mut[0] when there is one and from the module's CLIENT
    when there is not. Assuming a single shape here is what made the first run of this check try
    to open a mutation's description as a filename.
    """
    scripts = sorted(glob.glob(os.path.join(HERE, 'mutate_*.py')))
    scripts = [s for s in scripts if os.path.basename(s) != 'mutate_guard.py']
    if only:
        scripts = [s for s in scripts if only in os.path.basename(s)]
    if not scripts:
        print('no mutate_*.py matched %r' % only)
        return 1

    source = {}
    problems = 0
    for script in scripts:
        name = os.path.basename(script)
        try:
            module = load(script)
            muts = module.MUTS
        except Exception as broken:
            print('%-28s COULD NOT LOAD: %s' % (name, broken))
            problems += 1
            continue
        only_file = getattr(module, 'CLIENT', None)
        bad = []
        for mut in muts:
            why, find = mut[-3], mut[-2]
            path = mut[0] if len(mut) >= 4 else only_file
            if path is None:
                print('%-28s cannot tell which file: %s' % (name, why))
                bad.append((0, why))
                continue
            if path not in source:
                try:
                    with open(path, encoding='utf-8', newline='') as f:
                        source[path] = f.read()
                except OSError as missing:
                    source[path] = None
                    print('%-28s cannot read %s (%s)' % (name, path, missing))
            text = source[path]
            if text is None:
                bad.append((0, why))
                continue
            seen = text.count(find)
            if seen != 1:
                bad.append((seen, why))
        print('%-28s %3d patterns, %d problems' % (name, len(muts), len(bad)))
        for seen, why in bad:
            print('      %s: %s' % ('0 matches (stale, or source mutated)' if seen == 0
                                    else '%d matches (ambiguous)' % seen, why))
        problems += len(bad)

    print()
    print('clean - every pattern present exactly once' if problems == 0
          else '%d PROBLEM(S): a 0-match pattern is reported as SKIP and counted a survivor,'
               ' so it tests nothing' % problems)
    return problems


if __name__ == '__main__':
    sys.exit(1 if check(sys.argv[1] if len(sys.argv) > 1 else None) else 0)
