# Optimization and Shrinking
-optimizationpasses 5
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-dontpreverify
-verbose
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*

# Keep important Android classes
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.app.backup.BackupAgentHelper
-keep public class * extends android.preference.Preference

# Keep WebView and JavaScript interfaces
-keepattributes JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Material Design and Support libraries
-keep class com.google.android.material.** { *; }
-dontwarn com.google.android.material.**
-dontwarn androidx.**

# Keep Compose metadata
-keep class androidx.compose.runtime.** { *; }
-dontwarn androidx.compose.runtime.**

# Firebase/Google Play Services
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# Maintain stack traces for debugging crashes
-keepattributes SourceFile,LineNumberTable
