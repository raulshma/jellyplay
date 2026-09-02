package com.raulshma.jellyplay.feature.player.video

import androidx.compose.runtime.saveable.Saver

sealed class PlayerSheet {
    abstract val key: String

    data object None : PlayerSheet() { override val key = "None" }
    data object Speed : PlayerSheet() { override val key = "Speed" }
    data object Audio : PlayerSheet() { override val key = "Audio" }
    data object Chapter : PlayerSheet() { override val key = "Chapter" }
    data object PlaybackInfo : PlayerSheet() { override val key = "PlaybackInfo" }
    data object AspectRatio : PlayerSheet() { override val key = "AspectRatio" }

    /**
     * The unified subtitle hub — consolidates the former `Subtitle` (track
     * picker), `SubtitleStyle`, and `SubtitleDownload` sheets, and pulls
     * subtitle delay out of [AVSync] (audio delay remains there). See
     * `SubtitleHubSheet`.
     */
    data object SubtitleHub : PlayerSheet() { override val key = "SubtitleHub" }

    data object AVSync : PlayerSheet() { override val key = "AVSync" }
    data object Decoder : PlayerSheet() { override val key = "Decoder" }
    data object Episodes : PlayerSheet() { override val key = "Episodes" }
    data object SyncPlay : PlayerSheet() { override val key = "SyncPlay" }
    data object Quality : PlayerSheet() { override val key = "Quality" }
    data object PlaybackMode : PlayerSheet() { override val key = "PlaybackMode" }
    data object SleepTimer : PlayerSheet() { override val key = "SleepTimer" }
    data object VideoFilter : PlayerSheet() { override val key = "VideoFilter" }
}

/**
 * Allows the currently-open sheet to survive configuration changes (locale
 * switch, rotation outside the player's locked orientation) via
 * `rememberSaveable(stateSaver = PlayerSheetSaver)`. Restores by matching
 * each sheet's [PlayerSheet.key]; unknown values fall back to `None`.
 */
val PlayerSheetSaver: Saver<PlayerSheet, String> = Saver(
    save = { it.key },
    restore = { name -> ALL_SHEETS.firstOrNull { it.key == name } ?: PlayerSheet.None },
)

// Explicit list avoids kotlin-reflect (sealedSubclasses), which is not on
// the runtime classpath and crashes PlayerSheetKt's <clinit> on launch.
private val ALL_SHEETS: List<PlayerSheet> = listOf(
    PlayerSheet.None,
    PlayerSheet.Speed,
    PlayerSheet.Audio,
    PlayerSheet.Chapter,
    PlayerSheet.PlaybackInfo,
    PlayerSheet.AspectRatio,
    PlayerSheet.SubtitleHub,
    PlayerSheet.AVSync,
    PlayerSheet.Decoder,
    PlayerSheet.Episodes,
    PlayerSheet.SyncPlay,
    PlayerSheet.Quality,
    PlayerSheet.PlaybackMode,
    PlayerSheet.SleepTimer,
    PlayerSheet.VideoFilter,
)
