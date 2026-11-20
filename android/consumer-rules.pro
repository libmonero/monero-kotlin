# JNA finds the classes it marshals through reflection, which minification strips or renames: without these rules a minified app loads the
# library and marshals wrong
-keep class com.sun.jna.** { *; }
# the interface of the library and the callbacks extend a type of JNA: minification strips the methods of an interface that it sees no
# implementation of, and rewrites the call sites of the app to throw, and the callback that the native library calls loses the method it
# calls by its name
-keep interface * extends com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.** { public *; }
# a structure that JNA maps reads its fields by their names, which minification renames
-keepclassmembers class * extends com.sun.jna.** { <fields>; }
# the models cross the native ABI as JSON through their fields, and a field that minification renames is a property the ABI cannot read:
# the names of the fields are the names of the JSON
-keepclassmembers class monero.** { <fields>; <init>(); }
# jackson calls the methods that carry its annotations, which minification removes with the methods
-keepclassmembers class monero.** {
    @com.fasterxml.jackson.annotation.JsonValue <methods>;
    @com.fasterxml.jackson.annotation.JsonCreator <methods>;
}
# the enumerations cross the abi as their index, which jackson reads through values()
-keepclassmembers enum monero.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
# the reified reads of the models carry their types in the signatures of their references
-keepattributes Signature,RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault
# jna references the classes of java.awt, which an android app does not carry
-dontwarn java.awt.**
