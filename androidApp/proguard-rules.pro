# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
# kotlinx.serialization: protocol and prefs models are looked up through generated serializers.
-keepclassmembers @kotlinx.serialization.Serializable class com.saubh.deskbuddy.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.saubh.deskbuddy.**$$serializer { *; }
-keepclassmembers class com.saubh.deskbuddy.** {
    *** Companion;
}

# Ktor CIO references JVM-only classes that Android never loads.
-dontwarn java.lang.management.**
-dontwarn org.slf4j.impl.StaticLoggerBinder
