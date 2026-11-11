#!/usr/bin/env python3
"""The engine and this client agree on what a chat icon is.

THE CONTRACT. A chat icon is a five-character marker in the text - "@cr1@" through "@cra@" - and
two repositories have to read it the same way. This client DRAWS it (ChatIcons.iconAt, 16 pixels
wide). The engine MEASURES the line and decides where to wrap it (FontType.isIconTag). Neither can
see the other, and nothing compared them.

WHAT HAPPENED WHEN THEY DISAGREED. The tenth icon, "@cra@", was added here and the engine's rule
stopped at '9' - because 'a' > '9' as characters, which is true and easy to miss. Two separate
things break, and the second is the one the engine's own source already warns about:

  - stringWidth charges 0 pixels for a marker it does not know while this client draws 16, so the
    server wraps a line at a width that is not the width drawn, and the text runs past the edge.
  - split treats the unknown marker as a COLOUR and carries it onto every continuation line: a
    second icon at the front of every wrapped line, which is a bug the engine fixed once for icons
    1 to 9 and would have had back for the tenth.

Nothing could reach it - the engine's chatCrown tops out at 7 and the Helper rank is not built -
so it was a trap for whoever built that rank, and it would have read as a client bug and sent them
looking in this repository.

HOW IT ASKS. Both sides are RUN, not read. Reading two rules as text means writing a third rule
to compare them, which can be wrong on its own; and the interesting part is a character comparison
whose behaviour is the whole question. So this compiles ChatIcons and asks it, runs the engine's
tools/icontags.ts and asks that, and compares the two answers tag by tag.

    python3 tools/clienttests/run_chaticontest.py
"""
import json
import os
import shutil
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
SRC = os.path.join(ROOT, 'src/main/java')
# BOTH DIRECTORIES, as build.gradle declares and the other suites here note: Theme and the rest of
# the launcher are compiled into the client, so src/main/java alone is not the client and does not
# build on its own.
LAUNCHER_SRC = os.path.join(ROOT, 'launcher/src')

PROBE = '''import jagex2.client.ChatIcons;

public class IconProbe {
    public static void main(String[] args) {
        StringBuilder out = new StringBuilder();
        for (String tag : args) {
            // iconAt reads a marker out of a whole line, so the tag is wrapped in the @s it
            // would really arrive in - the shape is part of what is being compared.
            int at = ChatIcons.iconAt("@" + tag + "@", 0);
            out.append(tag).append('=').append(at).append('\\n');
        }
        System.out.print(out);
    }
}
'''


def engine():
    """The engine beside this checkout, under either name it goes by."""
    beside = os.path.dirname(ROOT)
    for name in ('Engine-TS', 'DeathPlateau-Engine'):
        if os.path.isfile(os.path.join(beside, name, 'tools/icontags.ts')):
            return os.path.join(beside, name)
    return None


def main():
    where = engine()
    if where is None:
        print('run_chaticontest: SKIP - no Engine-TS or DeathPlateau-Engine beside this checkout, '
              'so there is no other half of the contract to compare against')
        return 2
    javac, java = shutil.which('javac'), shutil.which('java')
    if not javac or not java or not shutil.which('npx'):
        print('run_chaticontest: SKIP - needs javac, java and npx on PATH')
        return 2

    # The engine's answer first: it also decides WHICH tags are worth asking about, so the two
    # sides cannot end up comparing different lists.
    run = subprocess.run(['npx', 'tsx', 'tools/icontags.ts'], cwd=where,
                         capture_output=True, text=True)
    if run.returncode != 0:
        print('run_chaticontest: SKIP - the engine\'s tools/icontags.ts did not run in %s' % where)
        print((run.stderr or run.stdout).strip()[-800:])
        return 2
    engine_says = json.loads(run.stdout.strip().splitlines()[-1])

    work = tempfile.mkdtemp(prefix='chaticontest')
    try:
        probe = os.path.join(work, 'IconProbe.java')
        with open(probe, 'w', encoding='utf-8') as f:
            f.write(PROBE)
        classes = os.path.join(work, 'classes')
        os.makedirs(classes)
        sources = []
        for where_src in (SRC, LAUNCHER_SRC):
            for base, _dirs, files in os.walk(where_src):
                sources += [os.path.join(base, f) for f in files if f.endswith('.java')]
        build = subprocess.run([javac, '-nowarn', '-encoding', 'UTF-8', '-d', classes]
                               + sources + [probe], capture_output=True, text=True)
        if build.returncode != 0:
            print('run_chaticontest: the client did not compile')
            print(build.stderr.strip()[-2000:])
            return 1
        tags = list(engine_says.keys())
        ask = subprocess.run([java, '-cp', classes, 'IconProbe'] + tags,
                             capture_output=True, text=True)
        if ask.returncode != 0:
            print('run_chaticontest: the probe did not run')
            print(ask.stderr.strip()[-2000:])
            return 1
        client_says = {}
        for line in ask.stdout.splitlines():
            tag, _, index = line.partition('=')
            client_says[tag] = int(index)
    finally:
        shutil.rmtree(work, ignore_errors=True)

    bad = 0
    icons = []
    for tag in tags:
        drawn = client_says.get(tag, -1) != -1
        measured = engine_says[tag]
        if drawn != measured:
            bad += 1
            print('  FAIL @%s@: the client %s it, the engine %s' % (
                tag,
                'draws' if drawn else 'does not draw',
                'measures it as an icon' if measured else 'does not'))
        elif drawn:
            icons.append('@%s@' % tag)

    print('  %s both sides agree on all %d markers (%d icons: %s)' % (
        'ok  ' if bad == 0 else 'FAIL', len(tags), len(icons), ' '.join(icons)))
    if bad:
        print('\nA marker one side draws and the other does not measure wraps a chat line at the '
              'wrong width, and gets carried onto continuation lines as if it were a colour. The '
              'two rules are ChatIcons.iconAt here and FontType.isIconTag in the engine.')
        return 1
    # The tenth is the one this exists for, so it is named rather than left inside a count.
    print('  ok   the tenth icon @cra@ is one of them, which is what this suite was written for')
    return 0


if __name__ == '__main__':
    sys.exit(main())
