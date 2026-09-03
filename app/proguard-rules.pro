-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-repackageclasses ''

-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# kotlinx-serialization and the Jellyfin SDK ship consumer R8 rules covering
# serializer lookup and org.jellyfin.sdk.model.api.** — no app-level keeps needed.

# libmpv - JNI library, must keep all classes and methods
-keep class is.xyz.mpv.** { *; }
-keepclassmembers class is.xyz.mpv.** { *; }
-dontwarn is.xyz.mpv.**

# LibVLC - JNI library, must keep all classes and methods
-keep class org.videolan.** { *; }
-keepclassmembers class org.videolan.** { *; }
-dontwarn org.videolan.**

# Media3 / ExoPlayer - consumer rules provided by library
-dontwarn androidx.media3.**

# Coil - consumer rules provided by library
-dontwarn coil3.**

# Paging - consumer rules provided by library
-dontwarn androidx.paging.**

# Navigation 3
-dontwarn androidx.navigation3.**

# Navigation routes — the restore contract persists back-stack entries as
# `{type = <binary class name>, value = ...}` via nav3's reflective
# NavKeySerializer (Class.forName), and the nav-customization vocabulary
# historically derived keys from simple names (#152). Neither kotlinx-
# serialization (explicit allowobfuscation) nor navigation3 (empty consumer
# rules) keeps these names, so pin them here — without this, every
# name-derived comparison silently stops matching in minified release builds
# while debug builds keep working.
-keep class com.raulshma.jellyplay.core.ui.navigation.Route**

# DataStore
-dontwarn androidx.datastore.**

# WorkManager
# All workers are CoroutineWorkers (ListenableWorker subtree, not the Worker
# base class the old rule matched). Keep class NAMES so WorkSpec strings
# persisted by a previous app version still resolve after R8 renaming, and
# keep the (Context, WorkerParameters) ctors for the reflection fallback.
-keepnames class * extends androidx.work.ListenableWorker
-keepclassmembers class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context,androidx.work.WorkerParameters);
}

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

-dontwarn kotlinx.serialization.**
-dontwarn org.jellyfin.sdk.**
-dontwarn org.slf4j.**
-dontwarn io.github.oshai.kotlinlogging.**

-dontwarn com.google.android.gms.internal.**

-dontwarn okio.**
-dontwarn org.conscrypt.**

# Strip debug/verbose logs in release builds
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
}
