#!/usr/bin/env python3
"""Where a mutation is written, and the check that none was left behind.

MUTATIONS GO IN A COPY NOW. workspace() hands a runner a throwaway copy of the repository and a
way to translate a path into it, so nothing under the working tree is ever opened for writing.
The tree stays clean and committable for the whole two hours a full audit takes, and the copy is
a snapshot besides - an unrelated edit to the working tree mid-run cannot reach the run.

WHAT CAME BEFORE, because it is the reason the copy exists: every mutate_*.py rewrote real source
in place, one break at a time, and restored it in a `finally`. That covers a run that fails,
throws or times out. It does not cover the process being killed - a SIGTERM ends Python without
running `finally` at all - so there were atexit and signal handlers to restore the originals too.

That is not hypothetical. A kill landed mid-mutation and left a deliberately broken line sitting
in Client.java - a bare ";" where a log line's text used to be, the exact code the mutation was
testing. The file looked plausible and the build passed, because a mutation is by construction a
small, compiling change, and the next commit would have shipped it as a release to every player's
launcher. Nothing in the normal workflow would have caught it.

None of that closed the window. SIGKILL cannot be caught, and neither can the machine going away,
so restoring narrowed the gap without removing it - and for the two hours a full audit takes, the
tree was deliberately broken either way. A copy has no window to close and no tree to restore, so
the handlers went with the in-place writes rather than being kept as an uncalled safety net
nobody would remember to wire up.

check() below is the backstop either way - it reads every
mutate_*.py in this directory and asserts each pattern appears in its file exactly once, which
catches a leftover mutation AND a pattern gone stale against moved source. A stale pattern is
worth catching on its own: the runners report a non-matching pattern as SKIP and count it as a
survivor, so a renamed variable can quietly stop a mutation from testing anything at all.

    python3 tools/clienttests/mutate_guard.py          # check every mutate_*.py
    python3 tools/clienttests/mutate_guard.py jaggrab  # just the ones whose name matches

    DP_MUTATE_DIR=/somewhere python3 tools/clienttests/mutate_xptest.py   # put the copies there
"""
import atexit
import glob
import importlib.util
import os
import shutil
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(os.path.dirname(HERE))

# What a mutation run needs to compile and drive the client. Everything a run_*.py reads out of
# ROOT, and nothing else: the copy exists to be cheap enough that every suite can make one.
COPIED = ('src', 'launcher', 'tools', 'plugins', '.github')

# Read, never written, and large enough not to copy. No mutation targets anything under it, and
# it is gitignored, so a runner that writes a preview there writes it where it always did.
LINKED = ('build',)


def workspace(prefix):
    """A copy of the repository to mutate in, so the working tree is never written.

    WHY A COPY AT ALL. A runner used to rewrite real source in place and put it back in a
    `finally`. That works, and it is still what guard() below is for, but it means the working
    tree is deliberately broken for most of a run's two hours - so any "is the tree clean" check
    sees a mutation and reports it as uncommitted work, and committing during a run would ship a
    deliberately broken line as a release. The window was narrow and real: a kill once left a
    bare ";" in Client.java where a mutation had been.

    A copy closes it. Nothing under the working tree is opened for writing at any point, so the
    tree stays clean and committable throughout, and a kill at the worst moment leaves a broken
    file in a temp directory nobody builds from.

    IT IS ALSO A SNAPSHOT, which is a second thing worth having. Half an XP drops run was once
    lost to an unrelated half-finished edit in another file: the suite recompiles the whole
    client each pass, so the broken file turned every remaining mutation into a fake "caught by a
    crash". Against a copy taken at the start, an edit to the working tree mid-run cannot reach
    the run at all.

    Returns (root, inside) where inside(path) maps a working-tree path into the copy. The copy
    goes under DP_MUTATE_DIR when that is set, the system temp directory otherwise, and is
    deleted on exit.
    """
    # DP_MUTATE_DIR says where to put it. The default is the system temp directory, which is
    # where every run_*.py already builds, so nothing has to be configured for this to work; set
    # the variable when the copies should live somewhere specific - a scratch area outside the
    # checkout, or a faster disk than /tmp.
    where = os.environ.get('DP_MUTATE_DIR') or None
    if where:
        try:
            os.makedirs(where, exist_ok=True)
        except OSError:
            where = None        # unwritable: fall back rather than refuse to run the audit
    root = tempfile.mkdtemp(prefix='mutate-' + prefix + '-', dir=where)
    atexit.register(shutil.rmtree, root, True)
    for name in COPIED:
        source = os.path.join(REPO, name)
        if os.path.isdir(source):
            shutil.copytree(source, os.path.join(root, name))
    for name in LINKED:
        source = os.path.join(REPO, name)
        if os.path.exists(source):
            try:
                os.symlink(source, os.path.join(root, name))
            except OSError:
                pass            # a platform without symlinks loses only the preview runners

    # Said out loud, because a run that is mutating somewhere unexpected is otherwise
    # indistinguishable from one that is working - and "did it really use the copy" was the first
    # question asked of this mechanism.
    print('mutating a copy of the repository in %s' % root)

    def inside(path):
        """The copy's version of a working-tree path."""
        relative = os.path.relpath(os.path.abspath(path), REPO)
        if relative.startswith(os.pardir):
            raise ValueError('%s is outside the repository, so it has no copy' % path)
        return os.path.join(root, relative)

    return root, inside


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
