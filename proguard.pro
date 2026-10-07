-target 1.8

-libraryjars <java.home>/lib/rt.jar

-repackageclasses ''
-applymapping 'proguard.map'
-printmapping 'build/libs/rs2client.map'

-keep,allowobfuscation public class * { public static void main(java.lang.String[]); }
-keepclassmembers public class * { public static void main(java.lang.String[]); }
-adaptresourcefilecontents

# THE PLUGIN API IS A PUBLISHED CONTRACT. A plugin jar is compiled against these names and loaded
# by name at run time, so obfuscating them means no plugin ever loads again - and it would fail as
# "no plugin classes in it", with nothing to say why. Releases are built from the plain jar today
# (.github/workflows/release.yml says so), so this costs nothing now and is what stops a proguarded
# build from silently breaking every plugin the day someone switches to one.
-keep public class jagex2.client.plugin.** { public protected *; }

# The loader reads @PluginDescriptor, @Subscribe and @ConfigItem reflectively. Without the
# annotation attributes they are not there to read, and every plugin loads as an unnamed one with
# no settings and no event handlers.
-keepattributes *Annotation*, RuntimeVisibleAnnotations, AnnotationDefault, Signature
