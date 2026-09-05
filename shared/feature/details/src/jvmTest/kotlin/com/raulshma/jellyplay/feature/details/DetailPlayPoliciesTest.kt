package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.model.DetailOrigin
import com.raulshma.jellyplay.core.model.MediaType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the [DetailPlayPolicies] folds the media-detail callbacks adapter used
 * to open-code: local-origin subtitle-index selection (play + chapter share
 * it, extras never select) and the series mark-played confirmation gate
 * (item-level and season-level).
 */
class DetailPlayPoliciesTest {

    // ── resolvePlayStreamSelection ────────────────────────────────────────

    @Test
    fun localOrigin_usesLocalManifestSubtitleIndex() {
        assertEquals(
            3,
            DetailPlayPolicies.resolvePlayStreamSelection(
                origin = DetailOrigin.LOCAL_OFFLINE_MODE,
                localSubtitleIndex = 3,
                remoteSubtitleIndex = 7,
            ),
        )
        assertEquals(
            1,
            DetailPlayPolicies.resolvePlayStreamSelection(
                origin = DetailOrigin.LOCAL_REMOTE_FAILURE,
                localSubtitleIndex = 1,
                remoteSubtitleIndex = null,
            ),
        )
    }

    @Test
    fun remoteOrigin_usesServerSubtitleIndex() {
        assertEquals(
            7,
            DetailPlayPolicies.resolvePlayStreamSelection(
                origin = DetailOrigin.REMOTE,
                localSubtitleIndex = 3,
                remoteSubtitleIndex = 7,
            ),
        )
    }

    @Test
    fun nullOrigin_fallsBackToServerSubtitleIndex() {
        // Origin not resolved yet — the play dispatch must not silently pick
        // the local manifest.
        assertEquals(
            5,
            DetailPlayPolicies.resolvePlayStreamSelection(
                origin = null,
                localSubtitleIndex = 3,
                remoteSubtitleIndex = 5,
            ),
        )
        assertEquals(
            null,
            DetailPlayPolicies.resolvePlayStreamSelection(
                origin = null,
                localSubtitleIndex = 3,
                remoteSubtitleIndex = null,
            ),
        )
    }

    // ── requiresMarkPlayedConfirmation ────────────────────────────────────

    @Test
    fun seriesRequiresConfirmation() {
        assertTrue(DetailPlayPolicies.requiresMarkPlayedConfirmation(MediaType.SERIES))
    }

    @Test
    fun singleItemsFlipImmediately() {
        assertFalse(DetailPlayPolicies.requiresMarkPlayedConfirmation(MediaType.MOVIE))
        assertFalse(DetailPlayPolicies.requiresMarkPlayedConfirmation(MediaType.EPISODE))
        assertFalse(DetailPlayPolicies.requiresMarkPlayedConfirmation(MediaType.SEASON))
        assertFalse(DetailPlayPolicies.requiresMarkPlayedConfirmation(null))
    }

    @Test
    fun seasonActionsAlwaysConfirm() {
        // A season mark is series-scoped by construction — it confirms even
        // when the parent item's type has not resolved (null).
        assertTrue(DetailPlayPolicies.requiresMarkPlayedConfirmation(null, isSeasonAction = true))
        assertTrue(DetailPlayPolicies.requiresMarkPlayedConfirmation(MediaType.MOVIE, isSeasonAction = true))
        assertTrue(DetailPlayPolicies.requiresMarkPlayedConfirmation(MediaType.SERIES, isSeasonAction = true))
    }
}
