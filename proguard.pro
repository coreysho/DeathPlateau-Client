-target 1.8

-libraryjars <java.home>/lib/rt.jar
# AND jce.jar, which is NOT part of rt.jar on Java 8 - the build job pins java-version: 8, and
# the JCE classes (javax.crypto.Cipher and friends) ship in their own jar there. LoginProfiles is
# the first thing in this client to encrypt anything, and without this line proguard cannot
# resolve what it references and the Obfuscate step fails.
#
# It failed that way for seven commits before anyone noticed, because release.yml ships the PLAIN
# jar from `gradle build` and never runs proguard - so the shipped client was fine and only this
# workflow was red. Running the test suites does not touch proguard either. If you add a class
# here that reaches for a corner of the JDK nobody has used yet, this is the line that needs it.
-libraryjars <java.home>/lib/jce.jar

-repackageclasses ''
-applymapping 'proguard.map'
-printmapping 'build/libs/rs2client.map'

-keep,allowobfuscation public class * { public static void main(java.lang.String[]); }
-keepclassmembers public class * { public static void main(java.lang.String[]); }
# Text resources only. Unfiltered, this rewrites class names inside EVERY resource file - which
# on a PNG means stamping bytes into the middle of an image. The window icons are resources now,
# so the filter is what keeps them from being quietly corrupted the day someone ships a
# proguarded build (release.yml ships the plain jar today).
-adaptresourcefilecontents !**.png,**

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
