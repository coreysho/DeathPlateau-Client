#!/usr/bin/env python3
"""Mutation test for tools/clienttests/run_jaggrabtest.py.

Breaks the JAGGRAB endpoint rule one way at a time and checks run_jaggrabtest notices - and
notices by NAMING a check, not by crashing. The bug this guards against shipped and kept a
player off the server, so the guard is worth testing properly.

    python3 tools/clienttests/mutate_jaggrabtest.py [filter]
"""
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
CLIENT = os.path.join(ROOT, 'src/main/java/jagex2/client/Client.java')
RUNNER = os.path.join(HERE, 'run_jaggrabtest.py')

MUTS = [
    # THE BUG ITSELF, back the way it was.
    (CLIENT, 'JAGGRAB pointed at the game host on a hardcoded port, the way it shipped',
     'this.field520 = new Socket(InetAddress.getByName(WEB_HOST), JAGGRAB_PORT);',
     'this.field520 = this.openSocket(43595);'),
    (CLIENT, 'the attempt made even when there is nowhere to send it',
     'if (this.field196 && jaggrabUsable(JAGGRAB_PORT, WS_URL)) {',
     'if (this.field196) {'),
    (CLIENT, 'JAGGRAB left on through the tunnels, where no port forwards it',
     'return hostGiven ? 43595 : 0;',
     'return 43595;'),
    (CLIENT, 'JAGGRAB off even when pointed straight at the server',
     'return hostGiven ? 43595 : 0;',
     'return 0;'),
    (CLIENT, 'an explicit setting ignored, so nobody can tunnel it on purpose',
     '''		if (given != null) {
			try {
				int port = Integer.parseInt(given.trim());
				return port > 0 && port <= 65535 ? port : 0;
			} catch (RuntimeException notANumber) {
				return 0;
			}
		}
''', ''),
    (CLIENT, 'a port out of range believed',
     'return port > 0 && port <= 65535 ? port : 0;',
     'return port;'),
    (CLIENT, 'an unparseable setting thrown rather than turned off, at class load',
     '''			} catch (RuntimeException notANumber) {
				return 0;
			}''',
     '''			} catch (RuntimeException notANumber) {
				throw notANumber;
			}'''),
    (CLIENT, 'JAGGRAB allowed over the websocket, which is the game stream',
     'return port > 0 && wsUrl == null;',
     'return port > 0;'),
    (CLIENT, 'the port ignored when deciding whether it is usable',
     'return port > 0 && wsUrl == null;',
     'return wsUrl == null;'),
]


def main():
    only = sys.argv[1] if len(sys.argv) > 1 else None
    with open(CLIENT, encoding='utf-8', newline='') as f:
        orig = f.read()
    muts = [m for m in MUTS if not only or only in m[1]]
    print('running %d of %d mutations' % (len(muts), len(MUTS)))
    bad = loose = 0
    for path, why, find, repl in muts:
        n = orig.count(find)
        if n != 1:
            print('  SKIP (pattern %s) %s'
                  % ('not found' if n == 0 else 'matches %d times' % n, why))
            bad += 1
            continue
        try:
            with open(path, 'w', encoding='utf-8', newline='') as f:
                f.write(orig.replace(find, repl))
            r = subprocess.run([sys.executable, RUNNER], capture_output=True, text=True,
                               timeout=600)
        except subprocess.TimeoutExpired:
            print('  %-5s %-72s %s' % ('HUNG', why, 'the suite never finished - not a catch'))
            loose += 1
            continue
        finally:
            with open(path, 'w', encoding='utf-8', newline='') as f:
                f.write(orig)
        fired = [l.strip()[5:].strip() for l in r.stdout.split('\n') if l.startswith('FAIL')]
        if r.returncode == 0:
            print('  %-5s %-72s %s' % ('GREEN', why, 'NOT CAUGHT'))
            bad += 1
        elif fired:
            print('  %-5s %-72s %s' % ('red', why, 'caught by: ' + fired[0][:44]))
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
