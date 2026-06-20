package com.raulshma.jellyplay.feature.player.video

import androidx.compose.runtime.saveable.Saver

sealed class PlayerSheet {
    data object None : PlayerSheet()
    data object Speed : PlayerSheet()
    data object Audio : PlayerSheet()
    data object Subtitle : PlayerSheet()
    data object Chapter : PlayerSheet()
    data object PlaybackInfo : PlayerSheet()
    data object AspectRatio : PlayerSheet()
    data object SubtitleStyle : PlayerSheet()

    data object AVSync : PlayerSheet()
    data object Decoder : PlayerSheet()
    data object SubtitleDownload : PlayerSheet()
    data object Episodes : PlayerSheet()
    data object SyncPlay : PlayerSheet()
    data object Quality : PlayerSheet()
    data object SleepTimer : PlayerSheet()
    data object VideoFilter : PlayerSheet()
}

/**
 * Allows the currently-open sheet to survive configuration changes (locale
 * switch, rotation outside the player's locked orientation) via
 * `rememberSaveable(stateSaver = PlayerSheetSaver)`. Restores by matching
 * the data-object's class simple name; unknown values fall back to `None`.
 */
val PlayerSheetSaver: Saver<PlayerSheet, String> = Saver(
    save = { it::class.simpleName ?: PlayerSheet.None::class.simpleName!! },
    restore = { name ->
        ALL_SHEETS.firstOrNull { it::class.simpleName == name } ?: PlayerSheet.None
    },
)

private val ALL_SHEETS: List<PlayerSheet> = listOf(
    PlayerSheet.None,
    PlayerSheet.Speed,
    PlayerSheet.Audio,
    PlayerSheet.Subtitle,
    PlayerSheet.Chapter,
    PlayerSheet.PlaybackInfo,
    PlayerSheet.AspectRatio,
    PlayerSheet.SubtitleStyle,
    PlayerSheet.AVSync,
    PlayerSheet.Decoder,
    PlayerSheet.SubtitleDownload,
    PlayerSheet.Episodes,
    PlayerSheet.SyncPlay,
    PlayerSheet.Quality,
    PlayerSheet.SleepTimer,
    PlayerSheet.VideoFilter,
)
