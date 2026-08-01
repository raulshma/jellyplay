-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions
-keepattributes InnerClasses
-keepattributes EnclosingMethod

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class **$serializer {
    *** Companion;
    *** writeObject(...);
    *** readObject(...);
}
-keepclassmembers class ** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.raulshma.jellyplay.**$$serializer { *; }

-keep class org.jellyfin.sdk.model.api.** { *; }

-keep class com.raulshma.jellyplay.core.data.worker.DownloadWorker { *; }

-keep class com.raulshma.jellyplay.core.data.playback.JellyPlayPlaybackService { *; }
-keep class com.raulshma.jellyplay.screensaver.JellyPlayDreamService { *; }

-keepclassmembers class * {
    @dagger.hilt.android.lifecycle.HiltViewModel <init>(...);
}

-keep class com.raulshma.jellyplay.core.ui.navigation.Route** { *; }

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

# DataStore
-dontwarn androidx.datastore.**

# WorkManager
-keepclassmembers class * extends androidx.work.Worker {
    <init>(android.content.Context,androidx.work.WorkerParameters);
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
