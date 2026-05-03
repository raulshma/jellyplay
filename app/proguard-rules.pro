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

-dontwarn kotlinx.serialization.**
-dontwarn org.jellyfin.sdk.**
-dontwarn org.slf4j.**
-dontwarn io.github.oshai.kotlinlogging.**
