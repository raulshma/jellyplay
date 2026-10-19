# Desktop release shrink rules. First enablement is SHRINK-ONLY
# (obfuscate = false in build.gradle.kts): R8's dead-code elimination does the
# installer/class-loading work; keeping names shields the reflective seams the
# Android keep-set exists for (JNA's name-mapped natives, kotlin-reflect,
# Koin's by-type lookups) without replicating that whole rule book. A later
# A/B can flip obfuscation on top — measurement-gated via
# tools/perf/desktop-baseline.sh either way.

-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# JNA + libmpv: the MpvC/RenderC interfaces map native symbols BY METHOD NAME
# — shrinking must keep them, and a future obfuscated build must not rename
# a single member.
-keep class com.raulshma.jellyplay.desktop.player.mpv.** { *; }
-keepclassmembers class com.raulshma.jellyplay.desktop.player.mpv.** { *; }
-dontwarn com.sun.jna.**

# kotlin-reflect (the Jellyfin SDK's model handling): keep the reflection
# machinery + the metadata it reads.
-keep class kotlin.reflect.** { *; }
-keep class kotlin.Metadata { *; }
-keepclassmembers class ** {
    *** Companion;
}
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Consumer rules ship for kotlinx-serialization and the Jellyfin SDK
# (serializer lookup + org.jellyfin.sdk.model.api.**) — same posture as the
# Android rules; nothing app-level needed for those two.
-dontwarn kotlinx.serialization.**
-dontwarn org.jellyfin.sdk.**
-dontwarn org.slf4j.**
-dontwarn io.github.oshai.kotlinlogging.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
