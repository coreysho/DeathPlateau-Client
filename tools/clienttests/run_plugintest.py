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

# Declares an API level no client will ever have. Must be refused by its declaration alone,
# before it is constructed - note the constructor, which must never run.
#
# IT WRITES A FILE, rather than setting a static the test could read. A static would be the
# obvious way and it does not work: the manager loads this class in a URLClassLoader of its own,
# so a static set there is not the static a test reading the class sees. The first version of
# this test did exactly that and could never have failed - the mutation that moves the level
# check to after newInstance() sailed through it. A file crosses classloaders.
FUTURE = '''package api;

import jagex2.client.plugin.Plugin;
import jagex2.client.plugin.PluginDescriptor;

@PluginDescriptor(name = "From the future", key = "api.future", apiLevel = 999)
public class FuturePlugin extends Plugin {

    public FuturePlugin() {
        try {
            new java.io.File(System.getProperty("api.future.witness")).createNewFile();
        } catch (Throwable ignored) {
        }
    }
}
'''

# Asks for exactly level 1, which this client has. Must load like any other plugin.
CURRENT = '''package api;

import jagex2.client.plugin.Plugin;
import jagex2.client.plugin.PluginDescriptor;

@PluginDescriptor(name = "Of this client", key = "api.current", apiLevel = 1)
public class CurrentPlugin extends Plugin {
}
'''

# The plugin every pre-levels jar is: it declares nothing, and calls something that is not there.
# Helper.v2() exists when this compiles and is gone from the jar that ships, so the call raises a
# real NoSuchMethodError from the JVM rather than one the test threw to make a point.
LINKAGE = '''package api;

import jagex2.client.plugin.Plugin;
import jagex2.client.plugin.PluginDescriptor;

@PluginDescriptor(name = "Built elsewhere", key = "api.linkage")
public class LinkagePlugin extends Plugin {

    protected void startUp() {
        Helper.v2();
    }
}
'''

HELPER_NEW = '''package api;

public class Helper {
    public static void v2() {
    }
}
'''

# Same class, one method short. Swapped into the jar after LinkagePlugin is compiled against the
# version above.
HELPER_OLD = '''package api;

public class Helper {
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


def api_jars(javac, work, classes, target):
    """Builds the three jars that test API levels, and returns the folder holding them.

    The interesting one is linkage.jar. Helper is compiled twice - once with v2() so
    LinkagePlugin compiles against it, once without - and the jar ships the second. The call in
    startUp therefore fails the way a real jar built against a newer client fails, with a
    NoSuchMethodError the JVM raises, not one the test wrote.
    """
    src = os.path.join(work, 'api-src', 'api')
    os.makedirs(src)
    write(os.path.join(src, 'FuturePlugin.java'), FUTURE)
    write(os.path.join(src, 'CurrentPlugin.java'), CURRENT)
    write(os.path.join(src, 'LinkagePlugin.java'), LINKAGE)
    write(os.path.join(src, 'Helper.java'), HELPER_NEW)

    out = os.path.join(work, 'api-classes')
    os.makedirs(out)
    r = run(javac, '-nowarn', '-cp', classes, '-d', out,
            *[os.path.join(src, f) for f in sorted(os.listdir(src))])
    if r.returncode != 0:
        print(r.stderr[-4000:])
        raise SystemExit('run_plugintest: the api sample plugins do not compile')

    # Helper again, a method short, into a folder of its own so it does not overwrite the one
    # LinkagePlugin was compiled against.
    old_src = os.path.join(work, 'api-old', 'api')
    os.makedirs(old_src)
    write(os.path.join(old_src, 'Helper.java'), HELPER_OLD)
    old_out = os.path.join(work, 'api-old-classes')
    os.makedirs(old_out)
    r = run(javac, '-nowarn', '-cp', classes, '-d', old_out, os.path.join(old_src, 'Helper.java'))
    if r.returncode != 0:
        print(r.stderr[-4000:])
        raise SystemExit('run_plugintest: the cut-down Helper does not compile')

    os.makedirs(target)
    pack(os.path.join(target, 'future.jar'), 'api.FuturePlugin',
         [(os.path.join(out, 'api/FuturePlugin.class'), 'api/FuturePlugin.class')])
    pack(os.path.join(target, 'current.jar'), 'api.CurrentPlugin',
         [(os.path.join(out, 'api/CurrentPlugin.class'), 'api/CurrentPlugin.class')])
    pack(os.path.join(target, 'linkage.jar'), 'api.LinkagePlugin',
         [(os.path.join(out, 'api/LinkagePlugin.class'), 'api/LinkagePlugin.class'),
          (os.path.join(old_out, 'api/Helper.class'), 'api/Helper.class')])
    return target


def pack(target, plugin_class, files):
    with zipfile.ZipFile(target, 'w') as z:
        z.writestr('META-INF/MANIFEST.MF',
                   'Manifest-Version: 1.0\nPlugin-Class: %s\n\n' % plugin_class)
        for full, entry in files:
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

        # 3b. the API-level jars, which the manager reads from ~/.deathplateau/plugins rather
        # than from a folder it is handed - so the run gets a home of its own and the test copies
        # them in itself. An empty home keeps every other section seeing no jars, as it does now.
        api = api_jars(javac, work, classes, os.path.join(work, 'api-jars'))
        home = os.path.join(work, 'home')
        os.makedirs(home)

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
        # The file FuturePlugin's constructor writes if it ever runs. Named on the command line
        # so the plugin, the manager and the test all agree on one path without sharing a class.
        witness = os.path.join(work, 'future-was-constructed')
        r = run(*(launcher + [java, '-Duser.home=' + home, '-Dapi.future.witness=' + witness,
                              '-cp', classes + os.pathsep + work,
                              'jagex2.client.plugin.PluginSystemTest', jars, settings, api,
                              witness]),
                cwd=work)
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
