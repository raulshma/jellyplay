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

# Coil's okhttp engine closes responses via
# coil3.network.okhttp.internal.UtilsKt.closeQuietly — compiled from the same
# `use`-finally idiom as kotlin.io.closeFinally. After R8 inlines it into
# CallsKt$await$2$2$onResponse$1 (receiver narrowed to okhttp3.Response), R8's
# close-support rewriting *sometimes* specializes the call to a
# closeFinally(Response) synthetic — the choice flips between build machines
# and even between runs on the same machine (two byte-stable dex attractors,
# ±44 bytes in one outline method). That broke F-Droid reproducible builds on
# v0.11.11/v0.11.12. Pinning the whole internal package stops the inlining, so
# the close call stays statically AutoCloseable and there is nothing to
# specialize — deterministic on every machine. Keep this even if Coil bumps.
-keep class coil3.network.okhttp.internal.** { *; }

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

# pdfbox-android (book reader TOC) references com.gemalto.jp2.JP2Decoder
# (JPEG-2000 decoding, JPXFilter) but does not pull the optional library in —
# JPEG-2000 images inside PDFs then render via the fallback path. Warn-only:
# R8 otherwise fails the release build with a missing-class error.
-dontwarn com.gemalto.jp2.**

-dontwarn okio.**
-dontwarn org.conscrypt.**

# Strip debug/verbose logs in release builds
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
}

# Strip debug/info logging calls (arguments included) from minified release
# builds. core:data's Log facade gates d/i at runtime (minimumLevel defaults
# to WARN), so this is the second belt: R8 removes the call and its argument
# construction entirely, leaving the runtime gate as defense-in-depth for the
# non-minified debuggable release builds. w/e survive both.
-assumenosideeffects class com.raulshma.jellyplay.core.data.log.Log {
    public int d(...);
    public int i(...);
}

# The sibling facades delegate to android.util.Log (whose d/v strip above only
# removes the inner delegation, not the caller-side message construction) —
# strip their debug calls too so argument construction dies with the call.
-assumenosideeffects class com.raulshma.jellyplay.core.network.NetworkLog {
    public void d(...);
}

-assumenosideeffects class com.raulshma.jellyplay.core.ui.components.UiLog_androidKt {
    public static void logUiDebug(...);
}

# Info-level from direct android.util.Log call sites: same treatment as the
# facades' i() above (their d/v are covered by the strip at the top).
-assumenosideeffects class android.util.Log {
    public static *** i(...);
}
