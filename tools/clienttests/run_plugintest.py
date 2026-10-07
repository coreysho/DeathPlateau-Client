#!/usr/bin/env python3
"""Headless test for the plugin system, driven against the real client source.

Simpler than the other client tests: the plugin system lives in its own package and needs nothing
from Client.java, so there is no source to slice - the test compiles against the real classes and
calls them. What it does build is the awkward half, jars: sample plugins are compiled and packed
here, with and without a Plugin-Class manifest, plus a deliberately corrupt one, so the loader is
tested against real files on a real disk rather than a mock of one.

    python3 tools/clienttests/run_plugintest.py
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
# The client's source set is BOTH directories - build.gradle says so, and the client jar
# compiles launcher/src in (Client.relaunchForUpdate runs the launcher, and lostcity.Branding
# is the window icon both of them use). Compiling src/main/java alone builds a subset of the
# real client and fails on anything that reaches across.
LAUNCHER_SRC = os.path.join(ROOT, 'launcher/src')
TEST = os.path.join(HERE, 'PluginSystemTest.java')

# A plugin with everything a real one has: a descriptor, a config item and an anonymous Overlay
# (whose SamplePlugin$1.class must not be mistaken for a plugin of its own).
SAMPLE = '''package sample;

import jagex2.client.plugin.ConfigItem;
import jagex2.client.plugin.Overlay;
import jagex2.client.plugin.OverlayGraphics;
import jagex2.client.plugin.Plugin;
import jagex2.client.plugin.PluginDescriptor;

@PluginDescriptor(name = "Sample", description = "A plugin in a jar", key = "sample.jar")
public class SamplePlugin extends Plugin {

    @ConfigItem(keyName = "on", name = "On")
    public boolean on = true;

    protected void startUp() {
        this.addOverlay(new Overlay() {
            public void render(OverlayGraphics g) {
                g.text(0, 0, "sample", 0xFFFFFF);
            }
        });
    }
}
'''

# No descriptor: the loader falls back to the class name, and must still load it.
OTHER = '''package sample;

import jagex2.client.plugin.Plugin;

public class OtherPlugin extends Plugin {
}
'''

# Not a plugin at all. Scanning a jar must walk past it.
NOT_A_PLUGIN = '''package sample;

public class NotAPlugin {
    public static final String HELLO = "not a plugin";
}
'''


def run(*cmd, **kwargs):
    return subprocess.run(list(cmd), capture_output=True, text=True, **kwargs)


def source_checks():
    """The seams a plugin reaches the client through, which only the source can show.

    The Java below proves the plugin writes PluginContext.setDragDelay and that the value lands
    on the client. What it cannot prove is that the drag handler READS it - a client still
    carrying the old `QolSettings.on(ANTI_DRAG) ? 10 : 5` would pass every one of those checks
    and ignore the plugin entirely.
    """
    with open(os.path.join(SRC, 'jagex2/client/Client.java'), encoding='utf-8') as f:
        client = f.read()
    out = []
    out.append(('the drag handler reads the field the plugin sets (%d sites)'
                % client.count('this.objDragCycles >= this.pluginDragCycles'),
                client.count('this.objDragCycles >= this.pluginDragCycles') == 1))
    out.append(('...and no copy of the old "on(ANTI_DRAG) ? 10 : 5" is left anywhere',
                'ANTI_DRAG' not in client))
    # Written in exactly two places: its declaration, and PluginContext. A third would be the
    # client arguing with the plugin about a value the plugin is supposed to own.
    with open(os.path.join(SRC, 'jagex2/client/plugin/PluginContext.java'), encoding='utf-8') as f:
        context = f.read()
    out.append(('...and only the plugin seam writes it: %d assignment in Client.java, %d in '
                'PluginContext' % (client.count('pluginDragCycles ='), context.count('pluginDragCycles =')),
                client.count('pluginDragCycles =') == 1 and context.count('pluginDragCycles =') == 1))
    return out


def write(path, text):
    with open(path, 'w', encoding='utf-8') as f:
        f.write(text)


def jar_from(classes_dir, names, target, manifest_attribute=None):
    """Packs the named .class files (plus their inner classes) into a jar."""
    with zipfile.ZipFile(target, 'w') as z:
        if manifest_attribute is not None:
            z.writestr('META-INF/MANIFEST.MF',
                       'Manifest-Version: 1.0\nPlugin-Class: %s\n\n' % manifest_attribute)
        for root, _, files in os.walk(classes_dir):
            for name in files:
                if not name.endswith('.class'):
                    continue
                full = os.path.join(root, name)
                entry = os.path.relpath(full, classes_dir).replace(os.sep, '/')
                if any(entry.startswith('sample/' + n) for n in names):
                    z.write(full, entry)


def main():
    javac = shutil.which('javac')
    java = shutil.which('java')
    if not javac or not java:
        raise SystemExit('run_plugintest: needs javac and java on PATH')

    work = tempfile.mkdtemp(prefix='plugintest')
    try:
        # 1. the client itself
        # Both source directories: see LAUNCHER_SRC above.
        sources = []
        for where in (SRC, LAUNCHER_SRC):
            for root, _dirs, files in os.walk(where):
                sources += [os.path.join(root, f) for f in files if f.endswith('.java')]
        classes = os.path.join(work, 'classes')
        os.makedirs(classes)
        r = run(javac, '-nowarn', '-d', classes, *sources)
        if r.returncode != 0:
            print(r.stderr[-4000:])
            raise SystemExit('run_plugintest: the client does not compile')
        print('client compiles: %d sources' % len(sources))

        # 2. sample plugins, compiled against it exactly as a third party would
        plugin_src = os.path.join(work, 'plugin-src', 'sample')
        os.makedirs(plugin_src)
        write(os.path.join(plugin_src, 'SamplePlugin.java'), SAMPLE)
        write(os.path.join(plugin_src, 'OtherPlugin.java'), OTHER)
        write(os.path.join(plugin_src, 'NotAPlugin.java'), NOT_A_PLUGIN)
        plugin_classes = os.path.join(work, 'plugin-classes')
        os.makedirs(plugin_classes)
        r = run(javac, '-nowarn', '-cp', classes, '-d', plugin_classes,
                *[os.path.join(plugin_src, f) for f in sorted(os.listdir(plugin_src))])
        if r.returncode != 0:
            print(r.stderr[-4000:])
            raise SystemExit('run_plugintest: the sample plugins do not compile against the client')
        print('sample plugins compile against the client')

        # 3. the folders the loader will be pointed at
        jars = os.path.join(work, 'jars')
        for name in ('declared', 'scanned', 'broken'):
            os.makedirs(os.path.join(jars, name))
        jar_from(plugin_classes, ['SamplePlugin', 'OtherPlugin', 'NotAPlugin'],
                 os.path.join(jars, 'declared', 'declared.jar'),
                 manifest_attribute='sample.SamplePlugin')
        jar_from(plugin_classes, ['SamplePlugin', 'OtherPlugin', 'NotAPlugin'],
                 os.path.join(jars, 'scanned', 'scanned.jar'))
        jar_from(plugin_classes, ['SamplePlugin'], os.path.join(jars, 'broken', 'good.jar'),
                 manifest_attribute='sample.SamplePlugin')
        with open(os.path.join(jars, 'broken', 'corrupt.jar'), 'wb') as f:
            f.write(b'this is not a zip file, let alone a jar')

        # 4. the test
        r = run(javac, '-nowarn', '-cp', classes, '-d', work, TEST)
        if r.returncode != 0:
            print(r.stderr[-6000:])
            raise SystemExit('run_plugintest: the test does not compile')

        print()
        print('0. the seams, read out of the source')
        bad = 0
        for what, ok in source_checks():
            print(('  ok   ' if ok else 'FAIL   ') + what)
            if not ok:
                bad += 1

        settings = os.path.join(work, 'settings')
        os.makedirs(settings)
        # signlink.findcachedir() walks a list of candidate paths and takes the first that
        # exists, and "~/" in that list is a LITERAL directory name, not the home folder. A
        # directory called ~ in the working directory therefore gives this run a cache folder of
        # its own, instead of the test writing into the real /tmp/.file_store_32.
        os.makedirs(os.path.join(work, '~'))
        # The built-in section constructs a Client, and Applet's constructor refuses to run
        # headless. A virtual display is enough; without one that section says it skipped.
        launcher = []
        if not os.environ.get('DISPLAY'):
            xvfb = shutil.which('xvfb-run')
            if xvfb:
                launcher = [xvfb, '-a']
        r = run(*(launcher + [java, '-cp', classes + os.pathsep + work,
                              'jagex2.client.plugin.PluginSystemTest', jars, settings]), cwd=work)
        lines = [l for l in r.stdout.split('\n') if l.strip()]
        for l in lines:
            print(l)
        if not lines:
            print(r.stderr[-4000:])
            return 1
        return 0 if r.returncode == 0 and not bad else 1
    finally:
        shutil.rmtree(work, ignore_errors=True)


if __name__ == '__main__':
    sys.exit(main())
