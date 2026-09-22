# JNI looks up these bridge classes, their fields and their constructors by
# their JVM names — hoshidicts_jni.cpp constructs them directly.
-keep class de.manhhao.hoshi.** { *; }

# The pop-up's JavaScript talks to the host through these.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
