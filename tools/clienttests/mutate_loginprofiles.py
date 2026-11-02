#!/usr/bin/env python3
"""Breaks the saved-login store one plausible way at a time and checks a NAMED check notices.

run_loginprofilestest.py passing says the suite ran. It does not say the suite would have noticed
if the store had been broken, and for this file that is the only question worth asking: what it
holds is the thing that signs in as a player. Each mutation below is a mistake somebody could
actually make - the shape check dropped, the old password field read as a token, the cap keeping
the wrong row - applied to a COPY of the repository, run, and put back.

A mutation that nobody catches is printed GREEN, which is the failure this script exists to find.

    python3 tools/clienttests/mutate_loginprofiles.py
    python3 tools/clienttests/mutate_loginprofiles.py "fixed IV"   # just the ones that match
"""
import os
import subprocess
import sys

# A copy of the repository is mutated, never the working tree: a kill mid-run cannot leave a
# broken file in a temp directory nobody builds from. mutate_guard.workspace has the reasoning,
# and its check() is the standalone pass that every pattern still matches its source exactly once.
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from mutate_guard import read_source, workspace  # noqa: E402  (after the sys.path line, necessarily)

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
STORE = os.path.join(ROOT, 'src/main/java/jagex2/client/LoginProfiles.java')
RUNNER = os.path.join(HERE, 'run_loginprofilestest.py')

MUTS = [
    # ---- WHAT IS STORED. None of these are visible from the login screen.
    (STORE, 'the token written to disk in readable text',
     '\t\t\treturn Base64.getEncoder().encodeToString(all);',
     '\t\t\treturn plain;'),
    (STORE, 'a fixed IV, so two cards written on one machine encrypt identically',
     '\t\t\tbyte[] iv = new byte[16];\n\t\t\tnew Random().nextBytes(iv);\n',
     '\t\t\tbyte[] iv = new byte[16];\n'),
    (STORE, 'the key no longer tied to the machine, so a copied file still decrypts',
     '\t\tmd.update(String.valueOf(System.getProperty("user.name", "")).getBytes("UTF-8"));\n',
     ''),
    (STORE, 'a failed decrypt handing back something that is not the token',
     '\t\t\treturn new String(c.doFinal(all, 16, all.length - 16), "UTF-8");\n\t\t} catch (Exception ignored) {\n\t\t\treturn "";',
     '\t\t\treturn new String(c.doFinal(all, 16, all.length - 16), "UTF-8");\n\t\t} catch (Exception ignored) {\n\t\t\treturn "junkjunkjunkjunkjunk";'),

    # ---- THE UPGRADE. Every player who had saved logins before this has a file with their
    # password in it, and these are the two ways to get that wrong.
    (STORE, "the old client's stored PASSWORD read as if it were a token",
     '\t\t\t\tString token = decrypt(p.getProperty("profile." + i + ".token", ""));',
     '\t\t\t\tString token = decrypt(p.getProperty("profile." + i + ".token",\n\t\t\t\t\tp.getProperty("profile." + i + ".secret", "")));'),
    (STORE, 'an old file read but never rewritten, so the password stays on the disk for ever',
     '\t\t\t\tif (p.getProperty("profile." + i + ".secret") != null) {\n\t\t\t\t\tlegacy = true;\n\t\t\t\t}\n',
     ''),

    # ---- THE SHAPE CHECK, which is what keeps a mangled file from putting a login on the wire
    # that the server can only refuse.
    (STORE, 'a stored value of any shape at all treated as a token',
     '\t\t\t\tif (!looksLikeToken(token)) {\n\t\t\t\t\ttoken = "";\n\t\t\t\t}',
     '\t\t\t\tif (token.length() > 4096) {\n\t\t\t\t\ttoken = "";\n\t\t\t\t}'),
    (STORE, 'a token of the wrong length accepted from the server',
     '\t\tif (username == null || !looksLikeToken(token)) {\n\t\t\treturn;\n\t\t}',
     '\t\tif (username == null || token == null) {\n\t\t\treturn;\n\t\t}'),
    (STORE, 'the alphabet not checked, so a token with impossible characters is kept',
     '\t\t\tif (TOKEN_CHARS.indexOf(value.charAt(i)) < 0) {\n\t\t\t\treturn false;\n\t\t\t}',
     '\t\t\tif (false) {\n\t\t\t\treturn false;\n\t\t\t}'),

    # ---- THE CARD'S OWN STATE.
    (STORE, 'a profile with no saved login claiming it can sign in',
     '\t\t\treturn this.token.length() > 0;',
     '\t\t\treturn true;'),
    (STORE, 'signing in clearing the saved login the account already had',
     '\t\tString token = tokenOf(name);',
     '\t\tString token = "";'),
    (STORE, 'a refused saved login taking the whole account with it',
     '\t\t\tprofiles.set(i, new Profile(p.username, "", p.lastUsed, p.look));',
     '\t\t\tprofiles.remove(i);'),
    (STORE, 'a refused saved login dropped in memory but left in the file',
     '\t\t\tprofiles.set(i, new Profile(p.username, "", p.lastUsed, p.look));\n\t\t\tsave();',
     '\t\t\tprofiles.set(i, new Profile(p.username, "", p.lastUsed, p.look));'),

    # ---- THE LIST RULES. These do show on screen, as a row that is wrong.
    (STORE, 'a name matched with case, so Corey and corey become two rows',
     '\t\t\tif (profiles.get(i).username.equalsIgnoreCase(name)) {\n\t\t\t\tprofiles.remove(i);',
     '\t\t\tif (profiles.get(i).username.equals(name)) {\n\t\t\t\tprofiles.remove(i);'),
    (STORE, 'the newest login appended rather than put first',
     '\t\tprofiles.add(0, new Profile(name, token, System.currentTimeMillis(), look));',
     '\t\tprofiles.add(new Profile(name, token, System.currentTimeMillis(), look));'),
    (STORE, 'the cap removed, so the row overflows the title area',
     '\t\twhile (profiles.size() > MAX) {\n\t\t\tprofiles.remove(profiles.size() - 1);\n\t\t}\n',
     ''),
    (STORE, 'the cap dropping the newest account instead of the longest unused',
     '\t\t\tprofiles.remove(profiles.size() - 1);',
     '\t\t\tprofiles.remove(0);'),
    (STORE, 'the login stamp never set, so the order cannot be trusted',
     'new Profile(name, token, System.currentTimeMillis(), look)',
     'new Profile(name, token, 0L, look)'),

    # ---- THE REST OF THE CONTRACT.
    (STORE, 'a name longer than the login field accepts saved anyway',
     '\t\tif (name.length() == 0 || name.length() > MAX_NAME) {\n\t\t\treturn;\n\t\t}\n',
     '\t\tif (name.length() == 0) {\n\t\t\treturn;\n\t\t}\n'),
    (STORE, 'forget leaving the account in the file it removed from the list',
     '\t\tif (username == null || !removeByName(username.trim())) {\n\t\t\treturn;\n\t\t}\n\t\tsave();',
     '\t\tif (username == null || !removeByName(username.trim())) {\n\t\t\treturn;\n\t\t}'),
]


def main():
    only = sys.argv[1] if len(sys.argv) > 1 else None
    orig = {}
    for path in (STORE,):
        orig[path] = read_source(path)
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
            print('  %-5s %-76s %s' % ('red', why, 'caught, but by a timeout - not a named check'))
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
