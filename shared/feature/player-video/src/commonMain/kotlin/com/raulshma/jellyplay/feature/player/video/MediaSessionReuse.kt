package com.raulshma.jellyplay.feature.player.video

/**
 * What the system media session was built FOR — the comparison base of the
 * session-reuse fast path ([canReuseMediaSession]) the androidMain media
 * session controller runs in [createForItem][MediaSessionController.createForItem].
 * Lives in commonMain (pure data, no Android types) so the predicate is
 * unit-pinnable in jvmTest; the androidMain controller is the only producer
 * and consumer at runtime.
 *
 * Every field is something the session carries verbatim — if it changes, the
 * session must be REBUILT, not kept, because a Media3 MediaSession fixes both
 * its player and its id at construction (there is no post-build swap for
 * either) and the per-item metadata is a closure baked into the session's
 * ForwardingPlayer override, not a settable property. Rebuild-for-any-change
 * is therefore also the SAFE default; the predicate exists so the session is
 * kept ONLY when a rebuild would be pure ceremony (same item, same labels,
 * same transport — the usual shape of a mini-player reclaim or an
 * ensureActiveForItem play-edge check).
 *
 * @property itemId the item the session fronts. Keys EVERYTHING derived at
 *   build time: the session id (`jellyplay_video_<itemId>`), the
 *   notification's content intent (the fullscreen reopen PendingIntent, built
 *   per item with an item-keyed request code) and the artwork URL. A
 *   different id means a different item — the notification must relabel and
 *   re-target, so this alone failing the comparison forces a rebuild.
 * @property title the pinned MediaMetadata title. The session pins it via a
 *   ForwardingPlayer `getMediaMetadata()` override because Jellyfin transcode
 *   manifests resolve empty metadata and would otherwise blank the
 *   now-playing notification — meaning the pinned value IS the displayed
 *   value, and a stale one is directly user-visible (lock-screen /
 *   Bluetooth / Wear OS text).
 * @property subtitle the pinned MediaMetadata subtitle (the episode /
 *   season line) — same staleness class as [title], same override path.
 * @property artworkUrl the resolved artwork URL string baked into the pinned
 *   metadata's artwork URI. Item-keyed but deliberately NOT folded into
 *   [itemId]: the URL is derived at build time from the detail snapshot held
 *   THEN (it carries the item's primary image tag), so a re-fetched detail —
 *   the mini-player reclaim re-fetches before rebinding — can change it
 *   without the id moving. Compared as the raw string (`null` = no artwork);
 *   both the held and the requested side are normalized through the same
 *   blank-filter by the builder, so the comparison never sees one side
 *   blank-parsed and the other null.
 * @property player the transport the session fronts: the engine's live media3
 *   player for ExoPlayer-family engines (Media3PlayerHost), the
 *   MediaEngineSessionPlayer adapter for every other engine (mpv / libVLC).
 *   Compared by IDENTITY — `===`, never structural equality, because the
 *   session's ForwardingPlayer closes over this exact instance and player
 *   types define no equals semantics. This field is what makes the fast path
 *   safe across the reload family: an engine swap ALWAYS replaces the
 *   transport (a URL swap never reuses the live engine — the replacement
 *   engine is created before the rebind), and a session kept against the
 *   released instance would drive notification play/pause commands into a
 *   dead player. Opaque `Any?` because the commonMain seam carries no
 *   platform player type (the controller narrows for real, the tests stub
 *   with bare objects — only reference identity is ever read).
 */
internal data class MediaSessionPin(
    val itemId: String,
    val title: String,
    val subtitle: String,
    val artworkUrl: String?,
    val player: Any?,
)

/**
 * Whether a session built for [held] can be kept (not rebuilt) for a request
 * described by [requested]. Pure fold over the two pins — data in, Boolean
 * out — so the reuse decision is pinned by MediaSessionReuseTest instead of
 * living as inline field comparisons in the controller.
 *
 * `held == null` means the controller holds no item session (first build, a
 * bare session from the reattach / background-cast paths, or a released
 * one): nothing to reuse, rebuild. The data-class equality trap is
 * deliberate avoidance: the [MediaSessionPin.player] field must compare by
 * reference, so this function — not generated `equals` — is the ONLY
 * sanctioned comparison of two pins.
 */
internal fun canReuseMediaSession(held: MediaSessionPin?, requested: MediaSessionPin): Boolean {
    if (held == null) return false
    return held.itemId == requested.itemId &&
        held.title == requested.title &&
        held.subtitle == requested.subtitle &&
        held.artworkUrl == requested.artworkUrl &&
        held.player === requested.player
}
