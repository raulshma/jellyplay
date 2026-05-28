package com.raulshma.jellyplay.feature.player.video

sealed class PlayerSheet {
    data object None : PlayerSheet()
    data object Speed : PlayerSheet()
    data object Audio : PlayerSheet()
    data object Subtitle : PlayerSheet()
    data object Chapter : PlayerSheet()
    data object PlaybackInfo : PlayerSheet()
    data object AspectRatio : PlayerSheet()
    data object SubtitleStyle : PlayerSheet()
    
    data class TapToTranslate(val text: String) : PlayerSheet()
    data object OcrResult : PlayerSheet()
    data object AudioDelay : PlayerSheet()
    data object Decoder : PlayerSheet()
    data object SubtitleDownload : PlayerSheet()
    data object Episodes : PlayerSheet()
    data object SyncPlay : PlayerSheet()
    data object Quality : PlayerSheet()
    data object SleepTimer : PlayerSheet()
    data object VideoFilter : PlayerSheet()
}
