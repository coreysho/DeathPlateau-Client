#!/usr/bin/env python3
"""Mutation test for tools/clienttests/run_loginprofilestest.py.

Breaks the saved-login store one way at a time and checks run_loginprofilestest notices - and
notices by NAMING a check, not by crashing.

This file holds people's passwords, so the mutations that matter most are the ones that would
never show on screen. A client that writes the password in readable text, or reuses one IV, or
hands back a wrongly decrypted secret, looks exactly like a client that does not: the login
screen is identical and every profile still works. Only a test that reads the bytes on disk can
tell the difference, which is why those checks exist and why these mutations exist to prove they
are load-bearing.

    python3 tools/clienttests/mutate_loginprofiles.py [filter]
"""
import os
import subprocess
import sys

# THE WORKING TREE IS NEVER WRITTEN. Mutations go into a throwaway copy of the repository, so
# the tree stays clean and committable for the whole run and a kill at the worst moment leaves a
# broken file in a temp directory nobody builds from. mutate_guard.workspace has the reasoning,
# and its check() is the standalone pass that every pattern still matches its source exactly once.
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from mutate_guard import workspace  # noqa: E402  (after the sys.path line, necessarily)

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
STORE = os.path.join(ROOT, 'src/main/java/jagex2/client/LoginProfiles.java')
RUNNER = os.path.join(HERE, 'run_loginprofilestest.py')

MUTS = [
    # ---- THE SECRECY OF THE FILE. None of these are visible from the login screen.
    (STORE, 'the password written to disk in readable text',
     '\t\t\treturn Base64.getEncoder().encodeToString(all);',
     '\t\t\treturn plain;'),
    (STORE, 'the keep-password choice ignored, so declining still stores it',
     '\t\tString secret = keepPassword && password != null && password.length() <= MAX_SECRET ? password : "";',
     '\t\tString secret = password != null && password.length() <= MAX_SECRET ? password : "";'),
    (STORE, 'a fixed IV, so two accounts sharing a password encrypt identically',
     '\t\t\tbyte[] iv = new byte[16];\n\t\t\tnew Random().nextBytes(iv);\n',
     '\t\t\tbyte[] iv = new byte[16];\n'),
    (STORE, 'the key no longer tied to the machine, so a copied file still decrypts',
     '\t\tmd.update(String.valueOf(System.getProperty("user.name", "")).getBytes("UTF-8"));\n',
     ''),
    (STORE, 'a failed decrypt handing back something that is not the password',
     '\t\t} catch (Exception ignored) {\n\t\t\treturn "";\n\t\t}\n\t}\n\n\t/**\n\t * Derived from this machine\'s account',
     '\t\t} catch (Exception ignored) {\n\t\t\treturn "junk";\n\t\t}\n\t}\n\n\t/**\n\t * Derived from this machine\'s account'),

    # ---- A BROKEN FILE MUST COST THE PASSWORD, NEVER THE ACCOUNT.
    (STORE, 'an undecryptable entry dropped entirely, so the account vanishes from the row',
     '\t\t\t\tif (secret.length() > MAX_SECRET) {\n\t\t\t\t\tsecret = "";\n\t\t\t\t}\n',
     '\t\t\t\tif (secret.length() == 0) {\n\t\t\t\t\tcontinue;\n\t\t\t\t}\n'),

    # ---- THE LIST RULES. These do show on screen, as a row that is wrong.
    (STORE, 'a name matched with case, so Corey and corey become two rows',
     '\t\t\tif (profiles.get(i).username.equalsIgnoreCase(name)) {\n\t\t\t\tprofiles.remove(i);',
     '\t\t\tif (profiles.get(i).username.equals(name)) {\n\t\t\t\tprofiles.remove(i);'),
    (STORE, 'the newest login appended rather than put first',
     '\t\tprofiles.add(0, new Profile(name, secret, System.currentTimeMillis()));',
     '\t\tprofiles.add(new Profile(name, secret, System.currentTimeMillis()));'),
    (STORE, 'the cap removed, so the row overflows the title area',
     '\t\twhile (profiles.size() > MAX) {\n\t\t\tprofiles.remove(profiles.size() - 1);\n\t\t}\n',
     ''),
    (STORE, 'the cap dropping the newest account instead of the longest unused',
     '\t\t\tprofiles.remove(profiles.size() - 1);',
     '\t\t\tprofiles.remove(0);'),
    (STORE, 'the login stamp never set, so the order cannot be trusted',
     'new Profile(name, secret, System.currentTimeMillis())',
     'new Profile(name, secret, 0L)'),

    # ---- THE REST OF THE CONTRACT.
    (STORE, 'a name longer than the login field accepts saved anyway',
     '\t\tif (name.length() == 0 || name.length() > MAX_NAME) {\n\t\t\treturn;\n\t\t}\n',
     '\t\tif (name.length() == 0) {\n\t\t\treturn;\n\t\t}\n'),
    (STORE, 'forget leaving the account in the file it removed from the list',
     '\t\tif (username == null || !removeByName(username.trim())) {\n\t\t\treturn;\n\t\t}\n\t\tsave();',
     '\t\tif (username == null || !removeByName(username.trim())) {\n\t\t\treturn;\n\t\t}'),
    (STORE, 'a profile with no password claiming it can sign in',
     '\t\t\treturn this.secret.length() > 0;',
     '\t\t\treturn true;'),
]


def main():
    only = sys.argv[1] if len(sys.argv) > 1 else None
    orig = {}
    for path in (STORE,):
        with open(path, encoding='utf-8', newline='') as f:
            orig[path] = f.read()
    # Written into a copy, never into the working tree - see mutate_guard.workspace.
    _work, inside = workspace('loginprofiles')
    muts = [m for m in MUTS if not only or only in m[1]]
    print('running %d of %d mutations' % (len(muts), len(MUTS)))
    bad = loose = 0
    for path, why, find, repl in muts:
        n = orig[path].count(find)
        if n != 1:
            print('  SKIP (pattern %s) %s'
                  % ('not found' if n == 0 else 'matches %d times' % n, why))
            bad += 1
            continue
        try:
            with open(inside(path), 'w', encoding='utf-8', newline='') as f:
                f.write(orig[path].replace(find, repl))
            r = subprocess.run([sys.executable, inside(RUNNER)], capture_output=True, text=True,
                               timeout=600)
        except subprocess.TimeoutExpired:
            print('  %-5s %-76s %s' % ('HUNG', why, 'the suite never finished - not a catch'))
            loose += 1
            continue
        finally:
            with open(inside(path), 'w', encoding='utf-8', newline='') as f:
                f.write(orig[path])
        fired = [l.strip()[5:].strip() for l in r.stdout.split('\n') if l.startswith('FAIL')]
        if r.returncode == 0:
            print('  %-5s %-76s %s' % ('GREEN', why, 'NOT CAUGHT'))
            bad += 1
        elif fired:
            print('  %-5s %-76s %s' % ('red', why, 'caught by: ' + fired[0][:44]))
        else:
            print('  %-5s %-76s %s' % ('red', why,
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
