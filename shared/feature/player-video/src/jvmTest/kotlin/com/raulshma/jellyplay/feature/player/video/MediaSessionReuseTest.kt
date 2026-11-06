package com.raulshma.jellyplay.feature.player.video

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The media-session reuse predicate's pure fold ([canReuseMediaSession] over
 * [MediaSessionPin] pins) — the field-by-field contract the androidMain
 * controller's fast path branches on, pinned here so a field dropped from the
 * comparison fails a test rather than silently staling the system
 * notification. The controller-side half (liveness: the held session must
 * still be the manager's active one) is behavioural and lives in the
 * androidHostTest MediaSessionReclaimTest, which builds real MediaLibrarySessions.
 *
 * The [MediaSessionPin.player] term is pinned for IDENTITY, not equality —
 * the session's ForwardingPlayer closes over the exact instance, so two
 * structurally-equal-but-distinct players must rebuild (a URL-swap reload
 * always replaces the transport; reusing the session against the released
 * instance would drive notification commands into a dead player).
 */
class MediaSessionReuseTest {

    // Bare objects as opaque player stand-ins: only reference identity is
    // ever read, mirroring how the controller stores the resolved transport.
    private val playerA = Any()

    /**
     * A stand-in whose instances are EQUAL while distinct — the exact shape
     * that catches an accidental structural (==) comparison of the player
     * field: two structurally-identical transports are still TWO transports,
     * and the session wraps a specific one.
     */
    private class PlayerLookalike(private val id: Int) {
        override fun equals(other: Any?): Boolean = other is PlayerLookalike && other.id == id
        override fun hashCode(): Int = id
    }

    private fun pin(
        itemId: String = "item1",
        title: String = "Movie",
        subtitle: String = "2024 · 1h52m",
        artworkUrl: String? = "https://server/Items/item1/Images/Primary",
        player: Any? = playerA,
    ): MediaSessionPin = MediaSessionPin(itemId, title, subtitle, artworkUrl, player)

    // ── the reuse case ──────────────────────────────────────────────────────

    @Test
    fun samePinOnEveryField_reuses() {
        assertTrue(
            canReuseMediaSession(pin(), pin()),
            "identical item + labels + artwork + SAME transport instance is the fast path's whole point",
        )
    }

    @Test
    fun samePinExceptPlayerInstance_mustRebuild() {
        // Two PlayerLookalikes with the same id compare EQUAL structurally —
        // the predicate must still reject them, because the session's
        // ForwardingPlayer closed over the FIRST instance and a replacement
        // engine's transport is a different object even when it looks
        // identical. This is the reload family's engine-swap shape.
        assertFalse(
            canReuseMediaSession(
                pin(player = PlayerLookalike(1)),
                pin(player = PlayerLookalike(1)),
            ),
            "player comparison must be identity (===), never structural equality",
        )
    }

    @Test
    fun nullPlayerVsNonNullPlayer_mustRebuild() {
        assertFalse(
            canReuseMediaSession(pin(), pin(player = null)),
            "identity comparison never conflates a transport with its absence",
        )
        assertFalse(
            canReuseMediaSession(pin(player = null), pin()),
        )
    }

    // ── each display field gates on its own ─────────────────────────────────

    @Test
    fun changedItemId_mustRebuild() {
        assertFalse(
            canReuseMediaSession(pin(itemId = "item1"), pin(itemId = "item2")),
            "the id keys the session id, artwork and the reopen PendingIntent — a different item must relabel",
        )
    }

    @Test
    fun changedTitle_mustRebuild() {
        assertFalse(
            canReuseMediaSession(pin(title = "Old"), pin(title = "New")),
            "the pinned metadata IS the displayed notification/lockscreen text",
        )
    }

    @Test
    fun changedSubtitle_mustRebuild() {
        assertFalse(
            canReuseMediaSession(pin(subtitle = "Season 1"), pin(subtitle = "Season 2")),
        )
    }

    @Test
    fun changedArtworkUrl_mustRebuild() {
        assertFalse(
            canReuseMediaSession(
                pin(artworkUrl = "https://server/Items/item1/Images/Primary?tag=old"),
                pin(artworkUrl = "https://server/Items/item1/Images/Primary?tag=new"),
            ),
            "the artwork URL folds in the primary image tag — a refreshed detail can change it without moving the id",
        )
    }

    // ── the null/blank pin edge cases ───────────────────────────────────────

    @Test
    fun nullHeldPin_neverReuses() {
        // No held item session: first build, a bare reattach/cast session, or
        // a released one — nothing to keep.
        assertFalse(
            canReuseMediaSession(null, pin()),
            "a missing pin means nothing held to reuse, whatever the request",
        )
    }

    @Test
    fun artworkAbsenceMatchesOnlyArtworkAbsence() {
        // Both sides are normalized through the same blank-filter by the
        // controller, so "no artwork" pins as null on BOTH sides; a
        // null-vs-URL difference is a real change (metadata gained/lost its
        // image) and must rebuild.
        assertTrue(
            canReuseMediaSession(pin(artworkUrl = null), pin(artworkUrl = null)),
            "two no-artwork sessions display identically — keeping is correct",
        )
        assertFalse(
            canReuseMediaSession(pin(artworkUrl = null), pin(artworkUrl = "https://server/img")),
        )
    }
}
