package com.raulshma.jellyplay.core.data.playback

/**
 * One resolved, playable audio track — the JVM twin of what the Android
 * audio path carries inside a media3 `MediaItem` (URI + metadata).
 *
 * [uri] is EITHER an absolute filesystem path ([isLocalFile] = true — see
 * the V2b mpv note in the desktop resolver: single-slash `file:/C:/…` URIs
 * are mis-parsed by mpv, so the raw path is handed over instead of the
 * `Uri.fromFile` string Android uses) or the Jellyfin stream URL
 * ([isLocalFile] = false).
 *
 * Relocated beside the queue chassis (from apps/desktop's
 * DesktopAudioSourceResolver.kt) when `DesktopAudioQueueManager` itself
 * moved into this module's jvmMain: the manager's resolution seam
 * ([AudioTrackResolver]) returns this shape, so the pair travels together.
 * The desktop RESOLVER impl stays app-side — it wires repositories and the
 * adaptive bitrate selector that only the desktop shell constructs.
 */
data class ResolvedAudioTrack(
    val itemId: String,
    val uri: String,
    val isLocalFile: Boolean,
    val title: String,
    val artist: String,
    val artistId: String?,
    val album: String?,
    val mediaSourceId: String?,
    val durationMs: Long,
    val normalizationGain: Float?,
    /** Server-reported resume position (10 kHz ticks), if any. */
    val resumePositionTicks: Long?,
)

/**
 * Per-item stream resolution seam for the desktop audio queue manager
 * (`DesktopAudioQueueManager`, core:data jvmMain). Kept a fun-interface so
 * the queue manager's unit tests can substitute local WAV fixtures without
 * standing up the repository cluster. The production impl is the app-side
 * `DesktopAudioSourceResolver` (apps/desktop) — the same
 * `PlaybackRepository.getStreamUrl` overload + adaptive bitrate tier the
 * Android audio browser builds its media3 `MediaItem`s from.
 */
fun interface AudioTrackResolver {
    suspend fun resolve(itemId: String, startPositionMs: Long): ResolvedAudioTrack?
}
