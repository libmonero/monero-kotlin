# JNA finds the classes it marshals through reflection, which minification strips or renames:
# without these rules a minified app loads the library and marshals wrong
-keep class com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.** { public *; }
