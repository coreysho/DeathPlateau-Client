#!/usr/bin/env python3
"""A saved login, end to end: the real client against a real engine, on both account authorities.

WHY A WHOLE ENGINE FOR THIS. The client keeps a server-issued token where it used to keep the
player's password, and that is two new fields inside the RSA login block plus one new packet coming
back. LoginProfilesTest proves what the client writes to disk; the engine's own code proves what it
does with a token; neither can prove the two read the same envelope, and a disagreement there is
not a subtle bug - it is nobody being able to log in.

Five phases, each a fresh JVM, the first four sharing one scratch user.home because "it still works
after you close the client" is the whole feature:

    password     type a password with Remember me ticked, and be handed a token
    token        click the saved card, type nothing, and get in on the token alone
    stale        hold a token the server never issued, and be refused in words a player can act on
    legacy       send the login block of a client from before saved logins, and still be let in
    noremember   log in with Remember me OFF, in a home of its own, and be given nothing to keep

AND ALL FIVE TWICE, against the two kinds of world there are. A world either checks passwords
itself or delegates to a login server (LOGIN_SERVER), and the default is false - so every run of
this suite before today tested the first kind, while the live world runs the second. The feature
did nothing there and said nothing about it, behind five green phases, for days. The client cannot
tell the two apart and should not have to, which is exactly why the suite has to.

Starts an engine on its own ports unless --port names one already running, and tells the client the
engine's own login key, which is per-machine and never committed. With --port there is one engine,
somebody else's, so only one pass runs and the heading does not guess which kind it is.

    python3 tools/clienttests/run_logintokentest.py
    python3 tools/clienttests/run_logintokentest.py --authority server   # the live shape only
    python3 tools/clienttests/run_logintokentest.py --port 43694 --webport 8694   # one already up

Needs a display: the client opens its real window and is driven through its real input handling.
"""
import argparse
import os
import shutil
import socket
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
SRC = os.path.join(ROOT, 'src/main/java')
LAUNCHER_SRC = os.path.join(ROOT, 'launcher/src')
TEST = os.path.join(HERE, 'LoginTokenTest.java')
ENGINE = os.path.join(os.path.dirname(ROOT), 'Engine-TS')

# Ports of its own, so a run cannot reach a world somebody is playing on and cannot collide with
# the engine a developer has up on the default 43594.
#
# CONTIGUOUS, AND THAT IS NOT COSMETIC. They used to be 43994 / 8994 / 9094 - the web and
# management ports a hundred apart - while --node-port moved in hundreds, so one run's web port was
# the previous run's management port. With a leftover engine still holding it, the new engine could
# not bind, died after saying it was ready, and the client fetched its cache from the old one:
# four runs reporting a client that never reached its title screen, for a reason that was never in
# the client. Six in a row can only collide with a run that moved by less than six.
PORT = 43994


def ports(node):
    """The three the engine needs: the game port, the web server, the management server.

    The second pass takes three more above them - node + 3, + 4, + 5 - for the login, friend and
    logger servers EASY_STARTUP brings up in the same process. It runs after the first engine has
    stopped, so it reuses these three.
    """
    return node, node + 1, node + 2


# THE LIVE WORLD'S SHAPE. LOGIN_SERVER means the world does not check passwords itself: it asks a
# login server, which owns the accounts. EASY_STARTUP starts that server (and the friend and logger
# servers) as workers in the same process, which is exactly how the deployed world runs.
#
# Their ports are moved off the 43500 / 45099 / 43501 defaults for the same reason the three above
# are: a developer's own engine, or one left over from another run, is already on them, and a
# worker that cannot bind takes the process down after it has said it was ready.
def server_env(node):
    return {
        'LOGIN_SERVER': 'true',
        'EASY_STARTUP': 'true',
        'LOGIN_PORT': str(node + 3),
        'FRIEND_PORT': str(node + 4),
        'LOGGER_PORT': str(node + 5)
    }

USER = 'tokentest'
PASSWORD = 'tok12345'

# Read out of the engine's own key file. The modulus is what the client encrypts the login block
# with, it is generated per machine by `npm run rsa`, and it is not in either repository.
RSAN_JS = (
    "const k=require('crypto').createPrivateKey(require('fs').readFileSync(process.argv[1]));"
    "console.log(BigInt('0x'+Buffer.from(k.export({format:'jwk'}).n,'base64url').toString('hex')).toString())"
)


def modulus(engine):
    key = os.path.join(engine, 'data/config/login-rsa.pem')
    r = subprocess.run(['node', '-e', RSAN_JS, key], capture_output=True, text=True)
    if r.returncode != 0 or not r.stdout.strip().isdigit():
        raise SystemExit('run_logintokentest: could not read the login key: ' + (r.stderr or r.stdout)[-500:])
    return r.stdout.strip()


def in_use(port):
    """Whether something already answers on this port.

    A connect, not a listener table: netstat and Get-NetTCPConnection both came back empty on this
    machine while two engines were plainly serving, and a harness that trusts them goes on to start
    an engine that dies on bind.
    """
    with socket.socket() as s:
        s.settimeout(1.5)
        return s.connect_ex(('127.0.0.1', port)) == 0


def serving(webport):
    """Whether the engine's web server answers for the cache the client is about to want."""
    try:
        with urllib.request.urlopen('http://127.0.0.1:%d/crc0' % webport, timeout=5) as r:
            return r.status == 200 and len(r.read()) > 8
    except (urllib.error.URLError, OSError):
        return False


def start_engine(engine, log, port, webport, mgmtport, extra_env=None):
    """An engine on the test ports, left running until the phases are done.

    extra_env is merged over the environment it starts with, and any _PORT in it is guarded below
    with the other three - a login server that cannot bind is as fatal as a world that cannot.
    """
    # BEFORE ANYTHING IS STARTED. An engine that cannot bind one of these dies AFTER printing
    # "World ready" - the game socket is the last thing it listens on - so starting it regardless
    # means handing the client a port that belongs to whatever got there first. That is not a
    # hypothetical: four runs in a row fetched their cache from a leftover engine whose pack files
    # had been replaced underneath it, and every one of them reported a client that never reached
    # its title screen.
    guarded = [('game', port), ('web', webport), ('management', mgmtport)]
    guarded += [(key[:-5].lower() + ' server', int(value))
                for key, value in sorted((extra_env or {}).items()) if key.endswith('_PORT')]
    for name, p in guarded:
        if in_use(p):
            raise SystemExit('run_logintokentest: something is already serving on the %s port %d. '
                             'An engine left over from an earlier run will do this - stop it, or '
                             'pass --node-port to move out of its way.' % (name, p))

    env = dict(os.environ)
    env.update({'NODE_PORT': str(port), 'WEB_PORT': str(webport), 'WEB_MANAGEMENT_PORT': str(mgmtport)})
    env.update(extra_env or {})
    out = open(log, 'w', encoding='utf-8')
    proc = subprocess.Popen(['npx', 'tsx', 'src/app.ts'], cwd=engine, env=env,
                            stdout=out, stderr=subprocess.STDOUT, shell=(os.name == 'nt'))

    # WAIT FOR "World ready", not for a fixed number of seconds. The world loads a map and twelve
    # thousand npcs first, and how long that takes is a property of the machine.
    giveUp = time.time() + 240
    while time.time() < giveUp:
        if proc.poll() is not None:
            print(open(log, encoding='utf-8').read()[-3000:])
            raise SystemExit('run_logintokentest: the engine stopped while starting')
        try:
            if 'World ready' in open(log, encoding='utf-8', errors='replace').read():
                # AND STILL ALIVE, AND ACTUALLY SERVING. "World ready" is printed before the game
                # socket is bound, so it is not the same thing as a working engine - and the cache
                # the client is about to ask for comes off the web server, not out of that line.
                for _ in range(30):
                    if proc.poll() is not None:
                        print(open(log, encoding='utf-8').read()[-2000:])
                        raise SystemExit('run_logintokentest: the engine stopped just after saying '
                                         'it was ready - usually a port it could not bind')
                    if serving(webport):
                        return proc
                    time.sleep(1)
                proc.terminate()
                raise SystemExit('run_logintokentest: the engine says it is ready but its web '
                                 'server is not answering on %d' % webport)
        except OSError:
            pass
        time.sleep(1)
    proc.terminate()
    raise SystemExit('run_logintokentest: the engine did not come up in 4 minutes')


def cannot_run(engine):
    """The reason this machine cannot run it, or None.

    Kept together and checked before anything is compiled, because every one of these is a
    property of the machine rather than of the client: a CI runner has no display, no engine
    beside it and no packed cache, and a suite that fails for those reasons is noise on every
    push. The repo's answer to that is exit 2, which the client job counts as skipped.
    """
    if not shutil.which('javac') or not shutil.which('java'):
        return 'no javac and java on PATH'
    # The client opens its real window and is driven through its real input handling. There is no
    # headless mode to fall back on - without a display GameShell has no Graphics to draw into.
    if os.name != 'nt' and not os.environ.get('DISPLAY') and not os.environ.get('WAYLAND_DISPLAY'):
        return 'no display (this drives the client\'s real window)'
    if not os.path.isdir(engine):
        return 'no engine checkout at %s' % engine
    if not os.path.exists(os.path.join(engine, 'data/config/login-rsa.pem')):
        return 'the engine has no login key (npm run rsa)'
    if not os.path.exists(os.path.join(engine, 'data/pack/main_file_cache.idx1')):
        return 'the engine has no packed cache (npm run build)'
    if not os.path.isdir(os.path.join(engine, 'node_modules')):
        return 'the engine has no node_modules (npm ci)'
    return None


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--engine', default=ENGINE)
    ap.add_argument('--port', type=int, help='an engine already running on this port')
    ap.add_argument('--webport', type=int)
    ap.add_argument('--keep', action='store_true', help='leave the scratch home behind to look at')
    ap.add_argument('--node-port', type=int, default=PORT,
                    help='the started engine takes this port and the five above it')
    ap.add_argument('--authority', choices=('local', 'server', 'both'), default='both',
                    help='which account authority to run against: the world itself, a login '
                         'server, or both (the default). Ignored with --port, where the engine '
                         'is already running and already configured.')
    args = ap.parse_args()

    why = cannot_run(args.engine)
    if why:
        print('run_logintokentest: SKIP - %s.' % why)
        return 2
    javac = shutil.which('javac')
    java = shutil.which('java')

    work = tempfile.mkdtemp(prefix='logintoken')
    engine = None
    try:
        sources = []
        for where in (SRC, LAUNCHER_SRC):
            for root, _dirs, files in os.walk(where):
                sources += [os.path.join(root, f) for f in files if f.endswith('.java')]
        classes = os.path.join(work, 'classes')
        os.makedirs(classes)
        r = subprocess.run([javac, '-nowarn', '-encoding', 'UTF-8', '-d', classes] + sources,
                           capture_output=True, text=True)
        if r.returncode != 0:
            print(r.stderr[-4000:])
            raise SystemExit('run_logintokentest: the client does not compile')
        print('client compiles: %d sources' % len(sources))

        # In jagex2.client, for TitleScreen's own layout constants and LoginProfiles.reload - a
        # test that recomputes a layout is a test that passes while the layout moves.
        r = subprocess.run([javac, '-nowarn', '-encoding', 'UTF-8', '-cp', classes, '-d', classes, TEST],
                           capture_output=True, text=True)
        if r.returncode != 0:
            print(r.stderr[-6000:])
            raise SystemExit('run_logintokentest: the test does not compile')

        rsan = modulus(args.engine)
        port = args.port or args.node_port
        _node, derived_web, mgmtport = ports(port)
        webport = args.webport or derived_web
        first = None if args.authority == 'server' else {}
        if not args.port:
            log = os.path.join(work, 'engine.log')
            if first is None:
                first = server_env(port)
                log = os.path.join(work, 'engine-loginserver.log')
            print('starting an engine on %d (log: %s)' % (port, log))
            engine = start_engine(args.engine, log, port, webport, mgmtport, first)
            print('engine ready')
        else:
            print('using the engine already on %d' % port)

        # The heading says what this pass is running against, and with --port it says only
        # that: the engine is somebody else's and its authority is not ours to claim.
        first_label = ('the engine already on %d' % port) if args.port else (
            'a login server' if args.authority == 'server' else 'local accounts')
        failed = run_phases(java, classes, rsan, work, port, webport, first_label)

        # AND AGAIN, AGAINST A LOGIN SERVER. Only when this run started the engine: with --port
        # there is one engine, somebody else's, and its authority is whatever they configured.
        if not args.port and args.authority == 'both':
            stop_engine(engine)
            engine = None
            log = os.path.join(work, 'engine-loginserver.log')
            print()
            print('starting a second engine, this one delegating to a login server (log: %s)' % log)
            engine = start_engine(args.engine, log, port, webport, mgmtport, server_env(port))
            print('engine ready')
            failed += run_phases(java, classes, rsan, work, port, webport, 'a login server')

        print()
        if failed == 0:
            print('saved logins work end to end: every phase passed, on both authorities'
                  if not args.port and args.authority == 'both' else
                  'saved logins work end to end: every phase passed')
        else:
            print('%d PHASE(S) FAILED' % failed)
        return 0 if failed == 0 else 1
    finally:
        stop_engine(engine)
        if args.keep:
            print('scratch home left at ' + work)
        else:
            shutil.rmtree(work, ignore_errors=True)


def run_phases(java, classes, rsan, work, port, webport, label):
    """The five phases against whatever engine is on these ports. Returns how many failed."""
    print()
    print('==== %s ====' % label)
    # Homes of this pass's own, so the second pass starts where the first did: with a machine that
    # has never saved a login. They are named for the pass because --keep is for looking at them.
    tag = label.replace(' ', '-')
    # ONE HOME ACROSS THE FIRST FOUR PHASES. That is what makes the second phase mean anything:
    # the token it signs in with is the one the first phase was given and wrote to disk.
    home = os.path.join(work, 'home-' + tag)
    os.makedirs(home)
    # AND ONE OF ITS OWN for the last. noremember asserts that nothing was saved, and the four
    # before it save on purpose - sharing a home would have it assert the opposite of what they
    # arranged.
    alone = os.path.join(work, 'home-' + tag + '-noremember')
    os.makedirs(alone)

    failed = 0
    phases = (('password', home), ('token', home), ('stale', home), ('legacy', home), ('noremember', alone))
    for i, (phase, where) in enumerate(phases):
            if i:
                # THE WORLD IS STILL LOGGING THE LAST PHASE OUT. Its socket went when that JVM
                # exited, and until the world has saved and removed the player the next login is
                # answered with "already logged in" - which is correct, and reads as a failure of
                # whatever the next phase was testing. The client retries for a minute as well;
                # this is just not spending that minute on something a pause settles.
                time.sleep(8)
            print()
            cmd = [java, '-Dshots.rsan=' + rsan, '-Dlostcity.host=127.0.0.1',
                   '-Dlostcity.port=%d' % port, '-Dlostcity.webport=%d' % webport,
                   # JAGGRAB off. The client assumes 43595 whenever it is talking to a host
                   # directly - the engine's own port at home, and nothing at all here - so every
                   # other retry goes to a closed port and makes the real failure harder to read.
                   # HTTP on the web port is what this harness wants.
                   '-Dlostcity.jaggrabport=0',
                   '-Duser.home=' + where, '-cp', classes,
                   'jagex2.client.LoginTokenTest', phase, USER, PASSWORD]
            r = subprocess.run(cmd, capture_output=True, text=True, cwd=work)
            printed = False
            for line in r.stdout.split('\n'):
                if line.strip():
                    print(line)
                    printed = True
            if r.returncode != 0:
                failed += 1
                if not printed or 'FAIL' not in r.stdout:
                    # NON-ZERO WITH NOTHING NAMED means the client died on the way in, and the
                    # reason is on stderr - usually the cache, the key or the port.
                    print(r.stderr[-3000:])
    return failed


def stop_engine(proc):
    """THE WHOLE TREE. npx runs the engine as a grandchild through cmd.exe, and terminating what
    Popen holds kills the wrapper and leaves node sitting on the port - so the next run dies with
    EADDRINUSE and the orphan has to be hunted down by hand. taskkill /T takes the children with
    it. Called between the two passes as well as at the end, because the second engine takes the
    first one's ports."""
    if proc is None:
        return
    if os.name == 'nt':
        subprocess.run(['taskkill', '/T', '/F', '/PID', str(proc.pid)], capture_output=True, text=True)
    else:
        proc.terminate()
    try:
        proc.wait(timeout=30)
    except Exception:
        proc.kill()


if __name__ == '__main__':
    sys.exit(main())
