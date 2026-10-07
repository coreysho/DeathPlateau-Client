#!/usr/bin/env python3
"""Renders the plugin sidebar to png files, and checks it is not blank.

The sidebar is Swing, so none of it is reachable from the other client tests - and "it compiles"
says nothing about whether a label landed off the edge of the panel. This builds the real sidebar
over a real plugin jar, paints it into an image, and writes the three states worth looking at:
the list, a plugin's config page, and what a player with no plugins sees first.

It also fails if a page comes out blank or near enough, which is what a broken layout or an
unrealised frame actually looks like - that is the regression worth catching automatically; the
rest is for a human to look at.

    python3 tools/clienttests/run_sidebarpreview.py [output-dir]

Needs a display. Uses xvfb-run when there is no DISPLAY set, and skips (exit 0) when there is
neither, so it does not fail a headless machine that simply cannot run it.
"""
import os
import shutil
import struct
import subprocess
import sys
import tempfile
import zipfile
import zlib

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
SRC = os.path.join(ROOT, 'src/main/java')
LAUNCHER_SRC = os.path.join(ROOT, 'launcher/src')
PLUGIN_SRC = os.path.join(ROOT, 'plugins/src')
PREVIEW = os.path.join(HERE, 'SidebarPreview.java')
PAINT_TEST = os.path.join(HERE, 'SidebarPaintTest.java')

PAGES = [('list', []), ('config', ['config']), ('ground', ['ground']),
         ('antidrag', ['antidrag']), ('hub', ['hub']), ('empty', [])]


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


def unique_colours(png):
    """Reads a png back and counts distinct pixels, to tell a drawn panel from a blank one."""
    with open(png, 'rb') as f:
        data = f.read()
    raw = b''
    pos = 8
    width = height = 0
    while pos < len(data):
        length = struct.unpack('>I', data[pos:pos + 4])[0]
        kind = data[pos + 4:pos + 8]
        body = data[pos + 8:pos + 8 + length]
        if kind == b'IHDR':
            width, height = struct.unpack('>II', body[:8])
        elif kind == b'IDAT':
            raw += body
        pos += length + 12
    pixels = zlib.decompress(raw)
    stride = width * 3 + 1
    seen = set()
    for y in range(height):
        row = pixels[y * stride + 1:(y + 1) * stride]
        for x in range(0, len(row), 3):
            seen.add(row[x:x + 3])
    return len(seen)


def main():
    out_dir = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, 'build/preview')
    javac = shutil.which('javac')
    java = shutil.which('java')
    if not javac or not java:
        raise SystemExit('run_sidebarpreview: needs javac and java on PATH')

    launcher = []
    if not os.environ.get('DISPLAY'):
        xvfb = shutil.which('xvfb-run')
        if not xvfb:
            print('run_sidebarpreview: no DISPLAY and no xvfb-run, skipping')
            return 0
        launcher = [xvfb, '-a']

    work = tempfile.mkdtemp(prefix='sidebarpreview')
    try:
        classes = os.path.join(work, 'classes')
        os.makedirs(classes)
        r = run(javac, '-nowarn', '-d', classes, *java_sources(SRC, LAUNCHER_SRC))
        if r.returncode != 0:
            print(r.stderr[-4000:])
            raise SystemExit('run_sidebarpreview: the client does not compile')

        # The example plugins, packed as a jar, so the preview shows a real loaded plugin rather
        # than something stubbed in for the picture.
        plugin_classes = os.path.join(work, 'plugin-classes')
        os.makedirs(plugin_classes)
        r = run(javac, '-nowarn', '-cp', classes, '-d', plugin_classes, *java_sources(PLUGIN_SRC))
        if r.returncode != 0:
            print(r.stderr[-4000:])
            raise SystemExit('run_sidebarpreview: the example plugins do not compile')

        home = os.path.join(work, 'home')
        plugins = os.path.join(home, '.deathplateau', 'plugins')
        os.makedirs(plugins)
        empty_home = os.path.join(work, 'empty-home', '.deathplateau')
        os.makedirs(empty_home)
        jar = os.path.join(plugins, 'example-plugins.jar')
        with zipfile.ZipFile(jar, 'w') as z:
            z.writestr('META-INF/MANIFEST.MF',
                       'Manifest-Version: 1.0\n'
                       'Plugin-Class: deathplateau.plugins.CoordinatesPlugin,'
                       'deathplateau.plugins.XpTrackerPlugin\n\n')
            for base, _, files in os.walk(plugin_classes):
                for name in files:
                    full = os.path.join(base, name)
                    z.write(full, os.path.relpath(full, plugin_classes).replace(os.sep, '/'))

        r = run(javac, '-nowarn', '-cp', classes, '-d', work, PREVIEW, PAINT_TEST)
        if r.returncode != 0:
            print(r.stderr[-6000:])
            raise SystemExit('run_sidebarpreview: the preview does not compile')

        if not os.path.isdir(out_dir):
            os.makedirs(out_dir)

        fails = 0

        # Does the WINDOW paint the sidebar? Separate from the pages below, which paint the
        # sidebar directly and so would pass even while the window never painted it at all.
        r = run(*(launcher + [java, '-cp', classes + os.pathsep + work,
                              'jagex2.client.SidebarPaintTest']))
        for line in r.stdout.strip().split('\n'):
            if line.strip():
                print(line)
        if r.returncode != 0:
            fails += 1

        # A cache directory of this run's own. Without it the plugin store, and the ground item
        # rules the Ground items page sets up for its picture, are written to the real
        # signlink.findcachedir() - which on this machine is somebody's actual settings.
        cache = os.path.join(work, 'cache')
        os.makedirs(cache)

        for name, args in PAGES:
            png = os.path.join(out_dir, name + '.png')
            where = os.path.join(work, 'empty-home') if name == 'empty' else home
            r = run(*(launcher + [java, '-Duser.home=' + where, '-Dlostcity.cachedir=' + cache,
                                  '-cp', classes + os.pathsep + work,
                                  'jagex2.client.plugin.ui.SidebarPreview', png] + args))
            if r.returncode != 0 or not os.path.exists(png):
                print('FAIL   %s did not render' % name)
                print(r.stderr[-2000:])
                fails += 1
                continue
            colours = unique_colours(png)
            # A blank or single-block panel comes out at a handful of colours; a drawn one is in
            # the hundreds once there is antialiased text on it.
            ok = colours > 40
            print(('  ok   ' if ok else 'FAIL   ') + '%s rendered (%d colours) -> %s'
                  % (name, colours, png))
            if not ok:
                fails += 1

        print()
        print('ALL PASS' if not fails else '%d FAILED' % fails)
        return 1 if fails else 0
    finally:
        shutil.rmtree(work, ignore_errors=True)


if __name__ == '__main__':
    sys.exit(main())
