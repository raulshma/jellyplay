package com.raulshma.jellyplay.core.data.cast

/**
 * Canonical cast-strategy dispatch names — the single vocabulary behind
 * CastManager's routing (transport dispatch, state fan-out, seeding from the
 * `defaultCastingStrategy` preference). Values are stable wire identifiers
 * carried on [CastDevice.strategyName]; they must never be renamed.
 * CastManager re-exposes them as its public `STRATEGY_*` aliases.
 */
object CastStrategyNames {
    const val GOOGLE: String = "google"
    const val LIBVLC: String = "libvlc"
    const val DLNA: String = "dlna"
    const val JELLYFIN: String = "jellyfin"
}

/**
 * Playback payload read off the manager-owned CastPlayer (Google Cast and
 * every strategy that rides the local transport). Player property reads are
 * the effect; this value is what survives into the pure fan-out below, so it
 * carries no Player reference — only already-coerced plain values.
 */
internal data class CastPlayerSnapshot(
    val position: Long,
    val duration: Long,
    val buffered: Long,
    val isPlaying: Boolean,
    val volume: Float,
)

/**
 * Renderer playback payload read off [com.raulshma.jellyplay.core.data.cast.dlna.DlnaCastStrategy]'s
 * `renderer*` flows after `refreshPlaybackState()`. DLNA exposes no buffered
 * position and no now-playing metadata — those gaps are declared divergences
 * of [castStateFanout], not missing wiring here.
 */
internal data class DlnaRendererState(
    val positionMs: Long,
    val durationMs: Long,
    val isPlaying: Boolean,
    val volume: Float,
)

/**
 * Remote-session payload read off [com.raulshma.jellyplay.core.data.cast.remote.JellyfinRemotePlayCastStrategy]'s
 * playback and now-playing flows after `refreshPlaybackState()`. The only
 * strategy payload that carries title/subtitle.
 */
internal data class JellyfinNowPlayingState(
    val positionMs: Long,
    val durationMs: Long,
    val isPlaying: Boolean,
    val volume: Float,
    val title: String,
    val subtitle: String,
)

/**
 * The per-flow field assignments one state refresh contributes to
 * CastManager's now-playing flow family (`castPositionMs`, `castDurationMs`,
 * `castIsPlaying`, `castBufferedPositionMs`, `castVolume`, `castTitle`,
 * `castSubtitle`).
 *
 * Every field is nullable with one uniform meaning: `null` = the strategy
 * does not own this field, so the manager's flow keeps its previous value.
 * That null-ness IS the per-strategy contract — which fields each branch
 * contributes is decided solely by [castStateFanout], never by the caller.
 */
internal data class CastStateFanout(
    val positionMs: Long? = null,
    val durationMs: Long? = null,
    val bufferedPositionMs: Long? = null,
    val isPlaying: Boolean? = null,
    val volume: Float? = null,
    val title: String? = null,
    val subtitle: String? = null,
)

/**
 * Pure, total value-mapping from the active strategy name plus the state
 * payloads a refresh gathered to the manager-flow field assignments — the
 * extracted fan of CastManager's `updateCastState`. No Android imports, no
 * side effects: the manager owns the effects (refreshing the strategy,
 * reading its flows, snapshotting the CastPlayer, writing the `_cast*`
 * flows) and this function owns only the per-strategy field decision.
 *
 * Only the payload of the branch selected by [strategyName] is read; the
 * others are ignored (callers pass only the gathered payload anyway). A null
 * payload for the active branch yields an all-null fan-out — every flow left
 * untouched — which keeps the function total; in production the manager
 * always gathers the active branch's payload right before calling.
 *
 * Branch semantics (mirrors CastManager's transport dispatch, whose else-arm
 * also covers the libvlc fallback and ad-hoc registered strategies):
 *  - [CastStrategyNames.DLNA] contributes position, duration, isPlaying,
 *    volume;
 *  - [CastStrategyNames.JELLYFIN] contributes those four plus title/subtitle;
 *  - every other name rides the manager-owned CastPlayer and contributes
 *    position, duration, buffered position, isPlaying, volume.
 *
 * Declared divergences (intentional per-branch differences, not omissions):
 *  - title / subtitle: ONLY the Jellyfin branch contributes them. Google
 *    Cast titles ride the loaded MediaItem's metadata (rendered by the cast
 *    UI, not the manager's flows) and DLNA exposes no now-playing metadata,
 *    so `castTitle` / `castSubtitle` stay untouched outside Jellyfin.
 *  - bufferedPositionMs: ONLY the local CastPlayer branch contributes it —
 *    buffered position is a media3 Player concept neither renderer protocol
 *    reports, so `castBufferedPositionMs` stays untouched outside the local
 *    transport.
 */
internal fun castStateFanout(
    strategyName: String,
    dlna: DlnaRendererState? = null,
    jellyfin: JellyfinNowPlayingState? = null,
    player: CastPlayerSnapshot? = null,
): CastStateFanout = when (strategyName) {
    CastStrategyNames.DLNA -> CastStateFanout(
        positionMs = dlna?.positionMs,
        durationMs = dlna?.durationMs,
        isPlaying = dlna?.isPlaying,
        volume = dlna?.volume,
    )
    CastStrategyNames.JELLYFIN -> CastStateFanout(
        positionMs = jellyfin?.positionMs,
        durationMs = jellyfin?.durationMs,
        isPlaying = jellyfin?.isPlaying,
        volume = jellyfin?.volume,
        title = jellyfin?.title,
        subtitle = jellyfin?.subtitle,
    )
    else -> CastStateFanout(
        positionMs = player?.position,
        durationMs = player?.duration,
        bufferedPositionMs = player?.buffered,
        isPlaying = player?.isPlaying,
        volume = player?.volume,
    )
}
