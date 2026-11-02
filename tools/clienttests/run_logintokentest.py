#!/usr/bin/env python3
"""A saved login, end to end: the real client against a real engine, three times over.

WHY A WHOLE ENGINE FOR THIS. The client keeps a server-issued token where it used to keep the
player's password, and that is two new fields inside the RSA login block plus one new packet coming
back. LoginProfilesTest proves what the client writes to disk; the engine's own code proves what it
does with a token; neither can prove the two read the same envelope, and a disagreement there is
not a subtle bug - it is nobody being able to log in.

Three phases, each a fresh JVM sharing one scratch user.home, because "it still works after you
close the client" is the whole feature:

    password   type a password with Remember me ticked, and be handed a token
    token      click the saved card, type nothing, and get in on the token alone
    stale      hold a token the server never issued, and be refused in words a player can act on
    legacy     send the login block of a client from before saved logins, and still be let in

Starts an engine on its own ports unless --port names one already running, and tells the client the
engine's own login key, which is per-machine and never committed.

    python3 tools/clienttests/run_logintokentest.py
    python3 tools/clienttests/run_logintokentest.py --port 43694 --webport 8694   # one already up

Needs a display: the client opens its real window and is driven through its real input handling.
"""
import argparse
import os
import shutil
import subprocess
import sys
import tempfile
import time

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
SRC = os.path.join(ROOT, 'src/main/java')
LAUNCHER_SRC = os.path.join(ROOT, 'launcher/src')
TEST = os.path.join(HERE, 'LoginTokenTest.java')
ENGINE = os.path.join(os.path.dirname(ROOT), 'Engine-TS')

# Ports of its own, so a run cannot reach a world somebody is playing on and cannot collide with
# the engine a developer has up on the default 43594.
PORT = 43994
WEBPORT = 8994
MGMTPORT = 9094

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


def start_engine(engine, log, port, webport, mgmtport):
    """An engine on the test ports, left running until the phases are done."""
    env = dict(os.environ)
    env.update({'NODE_PORT': str(port), 'WEB_PORT': str(webport), 'WEB_MANAGEMENT_PORT': str(mgmtport)})
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
                return proc
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
    ap.add_argument('--node-port', type=int, default=PORT, help='move the started engine off a stuck port')
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
        # Kept a fixed distance apart, so moving one moves all three and they cannot land on each
        # other: the first attempt at this had the web port of one run equal to the management port
        # of the next, and the collision read as "the engine stopped while starting".
        webport = args.webport or (WEBPORT + (port - PORT))
        mgmtport = MGMTPORT + (port - PORT)
        if not args.port:
            log = os.path.join(work, 'engine.log')
            print('starting an engine on %d (log: %s)' % (port, log))
            engine = start_engine(args.engine, log, port, webport, mgmtport)
            print('engine ready')
        else:
            print('using the engine already on %d' % port)

        # ONE HOME ACROSS ALL THREE PHASES. That is what makes the second phase mean anything: the
        # token it signs in with is the one the first phase was given and wrote to disk.
        home = os.path.join(work, 'home')
        os.makedirs(home)

        failed = 0
        for i, phase in enumerate(('password', 'token', 'stale', 'legacy')):
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
                   '-Duser.home=' + home, '-cp', classes,
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
        print()
        if failed == 0:
            print('saved logins work end to end: all four phases passed')
        else:
            print('%d PHASE(S) FAILED' % failed)
        return 0 if failed == 0 else 1
    finally:
        if engine is not None:
            # THE WHOLE TREE. npx runs the engine as a grandchild through cmd.exe, and terminating
            # what Popen holds kills the wrapper and leaves node sitting on the port - so the next
            # run dies with EADDRINUSE and the orphan has to be hunted down by hand. taskkill /T
            # takes the children with it.
            if os.name == 'nt':
                subprocess.run(['taskkill', '/T', '/F', '/PID', str(engine.pid)],
                               capture_output=True, text=True)
            else:
                engine.terminate()
            try:
                engine.wait(timeout=30)
            except Exception:
                engine.kill()
        if args.keep:
            print('scratch home left at ' + work)
        else:
            shutil.rmtree(work, ignore_errors=True)


if __name__ == '__main__':
    sys.exit(main())
