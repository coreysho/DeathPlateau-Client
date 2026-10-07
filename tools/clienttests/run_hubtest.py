#!/usr/bin/env python3
"""Headless test for the plugin hub, against a real HTTP server on localhost.

The hub downloads code and puts it where the client will run it, so the parts worth testing are
the refusals - an index entry whose id would write outside the plugins folder, a url that is not
http, a jar that does not match its checksum, a download that is not a jar. None of those are
exercised by clicking Install on something that works.

The jar it serves is the real example-plugins jar, built here, so "the installed file loads as a
plugin" means the real loader read the real thing.

    python3 tools/clienttests/run_hubtest.py
"""
import os
import shutil
import subprocess
import sys
import tempfile
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
SRC = os.path.join(ROOT, 'src/main/java')
LAUNCHER_SRC = os.path.join(ROOT, 'launcher/src')
PLUGIN_SRC = os.path.join(ROOT, 'plugins/src')
TEST = os.path.join(HERE, 'HubTest.java')


def run(*cmd, **kwargs):
    return subprocess.run(list(cmd), capture_output=True, text=True, **kwargs)


# The client's source set is BOTH directories - build.gradle says so, and the client jar
# compiles launcher/src in (Client.relaunchForUpdate runs the launcher, and lostcity.Branding
# is the window icon both of them use). Compiling src/main/java alone builds a subset of the
# real client and fails on anything that reaches across.
def java_sources(*roots):
    found = []
    for root in roots:
        for base, _, files in os.walk(root):
            found += [os.path.join(base, f) for f in files if f.endswith('.java')]
    return found


def main():
    javac = shutil.which('javac')
    java = shutil.which('java')
    if not javac or not java:
        raise SystemExit('run_hubtest: needs javac and java on PATH')

    work = tempfile.mkdtemp(prefix='hubtest')
    try:
        classes = os.path.join(work, 'classes')
        os.makedirs(classes)
        r = run(javac, '-nowarn', '-d', classes, *java_sources(SRC, LAUNCHER_SRC))
        if r.returncode != 0:
            print(r.stderr[-4000:])
            raise SystemExit('run_hubtest: the client does not compile')
        print('client compiles')

        # A real plugin jar to serve, so the install path ends in something loadable.
        plugin_classes = os.path.join(work, 'plugin-classes')
        os.makedirs(plugin_classes)
        r = run(javac, '-nowarn', '-cp', classes, '-d', plugin_classes, *java_sources(PLUGIN_SRC))
        if r.returncode != 0:
            print(r.stderr[-4000:])
            raise SystemExit('run_hubtest: the example plugins do not compile')
        jar = os.path.join(work, 'served.jar')
        with zipfile.ZipFile(jar, 'w') as z:
            z.writestr('META-INF/MANIFEST.MF',
                       'Manifest-Version: 1.0\n'
                       'Plugin-Class: deathplateau.plugins.CoordinatesPlugin\n\n')
            for base, _, files in os.walk(plugin_classes):
                for name in files:
                    full = os.path.join(base, name)
                    z.write(full, os.path.relpath(full, plugin_classes).replace(os.sep, '/'))
        print('a real plugin jar to serve')

        r = run(javac, '-nowarn', '-cp', classes, '-d', work, TEST)
        if r.returncode != 0:
            print(r.stderr[-6000:])
            raise SystemExit('run_hubtest: the test does not compile')

        plugins = os.path.join(work, 'plugins')
        os.makedirs(plugins)
        r = run(java, '-cp', classes + os.pathsep + work,
                'jagex2.client.plugin.hub.HubTest', plugins, jar, cwd=work)
        lines = [l for l in r.stdout.split('\n') if l.strip()]
        for l in lines:
            print(l)
        if not lines:
            print(r.stderr[-4000:])
            return 1
        return 0 if r.returncode == 0 else 1
    finally:
        shutil.rmtree(work, ignore_errors=True)


if __name__ == '__main__':
    sys.exit(main())
