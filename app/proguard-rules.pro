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
-keep class org.jellyfin.sdk.model.** { *; }

-keep class com.raulshma.jellyplay.core.data.worker.DownloadWorker { *; }

-keep class com.raulshma.jellyplay.core.data.playback.JellyPlayPlaybackService { *; }
-keep class com.raulshma.jellyplay.screensaver.JellyPlayDreamService { *; }

-keep @androidx.room.Entity class * { *; }
-keep class * extends androidx.room.RoomDatabase { *; }

-keepclassmembers class * {
    @androidx.room.Query <methods>;
    @androidx.room.Insert <methods>;
    @androidx.room.Update <methods>;
    @androidx.room.Delete <methods>;
}

-keepnames class kotlinx.serialization.internal.EnumSerializer { *; }
-keepnames class kotlinx.serialization.internal.CollectionSerializer { *; }
-keepnames class kotlinx.serialization.internal.MapSerializer { *; }

-keepclassmembers class * {
    @dagger.hilt.android.lifecycle.HiltViewModel <init>(...);
}

-keep class com.raulshma.jellyplay.core.ui.navigation.Route** { *; }
-keepclassmembers class com.raulshma.jellyplay.core.ui.navigation.Route** {
    *;
}

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

# Paging
-keep class androidx.paging.**$* { *; }
-dontwarn androidx.paging.**

# Compose runtime
-keep class androidx.compose.runtime.**$Composable { *; }
-dontwarn androidx.compose.runtime.**

# Navigation 3
-keep class androidx.navigation3.**$Composable { *; }
-dontwarn androidx.navigation3.**

# DataStore
-dontwarn androidx.datastore.**

# WorkManager
-keepclassmembers class * extends androidx.work.Worker {
    <init>(android.content.Context,androidx.work.WorkerParameters);
}

# Hilt
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.internal.GeneratedComponent { *; }
-keepclassmembers @dagger.hilt.android.HiltAndroidApp class * { *; }

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

-dontwarn kotlinx.serialization.**
-dontwarn org.jellyfin.sdk.**
-dontwarn org.slf4j.**
-dontwarn io.github.oshai.kotlinlogging.**
