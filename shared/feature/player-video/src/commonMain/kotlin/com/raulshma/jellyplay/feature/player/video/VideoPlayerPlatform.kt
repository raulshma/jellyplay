package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.data.playback.AdaptiveBitrateManager
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.datastore.syncplaycast.SyncPlayCastStore
import com.raulshma.jellyplay.core.model.PlaybackMode
import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine
import com.raulshma.jellyplay.feature.player.video.trickplay.TrickplayController

/**
 * Aggregate platform seam for the video player session cluster (wave 8C):
 * every Android-only capability the commonMain
 * [VideoPlayerViewModel]/[PlayerSessionManager]/[SubtitleManager] cluster
 * needs behind one injectable object, so the ViewModel's constructor keeps a
 * single `platform` slot where it previously took `android.content.Context`.
 *
 * The androidMain actual ([AndroidVideoPlayerPlatform], module androidMain)
 * implements each member with the exact body the ViewModel/PlayerSessionManager
 * used to inline (ActivityManager low-RAM lookup, ContentResolver subtitle
 * IO, MediaMetadataRetriever probing, and construction of the androidMain
 * trickplay / cast-controller / audio-lifecycle collaborators). The jvmMain
 * actual is a no-op stub (desktop playback host is queued work).
 */
interface VideoPlayerPlatform : SubtitleContentGateway {

    /**
     * `true` on low-RAM devices (ActivityManager `isLowRamDevice` or
     * `memoryClass <= 256`). Gates trickplay tile-cache sizing; the desktop
     * actual returns `false`.
     */
    fun isLowRamDevice(): Boolean

    /** Offline-media probing seam (duration extraction + container→MIME). */
    val offlineMediaProbe: OfflineMediaProbe

    /**
     * Constructs the session's [TrickplayController]. The Android actual
     * returns the androidMain [TrickplayManager][com.raulshma.jellyplay.feature.player.video.trickplay.TrickplayManager]
     * (Bitmap tile cache); the desktop actual returns a no-op.
     */
    fun createTrickplayController(playbackRepository: PlaybackRepository): TrickplayController

    /**
     * Constructs the session's cast controller. [playbackRepository],
     * [adaptiveBitrateManager] and [syncPlayCastStore] are passed by the
     * caller (they are ViewModel constructor dependencies of shared types);
     * the Android actual additionally captures the legacy
     * [com.raulshma.jellyplay.core.data.cast.CastManager] singleton it
     * constructs the controller with.
     */
    fun createCastController(
        playbackRepository: PlaybackRepository,
        adaptiveBitrateManager: AdaptiveBitrateManager,
        syncPlayCastStore: SyncPlayCastStore,
        getEngine: () -> MediaEngine?,
        getCurrentPlaybackMode: () -> PlaybackMode,
        getSessionState: () -> PlayerSessionState,
    ): PlayerCastController

    /**
     * Constructs the audio-focus/becoming-noisy lifecycle owner. The getters
     * are re-read on every platform callback so engine swaps and mute changes
     * are observed live — the same contract the legacy inline adapter had.
     */
    fun createAudioLifecycle(
        getEngine: () -> MediaEngine?,
        isMuted: () -> Boolean,
        onRegain: (() -> Unit)?,
    ): VideoPlayerAudio
}

/**
 * Content-URI IO seam for side-loaded/uploaded subtitles (SAF/native picks).
 * The Android actual reads through `Context.contentResolver`
 * (OpenableColumns.SIZE query / openInputStream); the desktop actual (wave
 * 20C) resolves the AWT pickers' `file:` URIs to plain [java.io.File] length/
 * bytes and reports 0/empty for anything else. [uri] is the string form of
 * the picked URI — Uri is stringified at the API boundary because it only
 * flows to platform code.
 */
interface SubtitleContentGateway {

    /** Byte size of [uri] via OpenableColumns.SIZE, or 0 when unknown. */
    fun queryFileSizeBytes(uri: String): Long

    /** Reads [uri] fully. Throws on read failure. */
    fun readBytes(uri: String): ByteArray
}

/**
 * Offline-media probe seam for [PlayerSessionManager.loadOffline] (wave 8C):
 * duration extraction from a local media file and container→MIME mapping were
 * `android.media.MediaMetadataRetriever` + media3 `MimeTypes` constants.
 * The Android actual ([AndroidOfflineMediaProbe], module androidMain) runs
 * the retriever verbatim and delegates to the androidMain
 * ContainerMimeMapper; the desktop actual returns null for both (no desktop
 * offline-video host yet — null falls back to the persisted runTimeTicks and
 * extension-based extractor inference).
 */
interface OfflineMediaProbe {

    /**
     * Extracts the duration in ms from [path]'s metadata, or null when the
     * platform cannot read it (missing/unsupported file).
     */
    fun extractDurationMs(path: String): Long?

    /**
     * Maps a container format string (MediaSource-reported or sniffed) to the
     * platform player's MIME type, or null when unknown — the caller then
     * falls back to extension-based inference.
     */
    fun mapContainerToMime(container: String?): String?
}
