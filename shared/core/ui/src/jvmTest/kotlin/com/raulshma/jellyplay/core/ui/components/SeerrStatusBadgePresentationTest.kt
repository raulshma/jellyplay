package com.raulshma.jellyplay.core.ui.components

import com.raulshma.jellyplay.core.designsystem.theme.StatusColors
import com.raulshma.jellyplay.core.model.seerr.SeerrMediaStatus
import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the Seerr card's status-corner presentation table — the card-badge
 * counterpart of the requests feature's `requestStatusPresentation` and the
 * Seerr detail screen's action-button `when`. The drift this pins closed:
 * the card used to merge PROCESSING into "pending" and render an in-flight
 * download under the pending glyph/color; PROCESSING now renders as its own
 * state (info blue), consistent with both other status tables.
 */
class SeerrStatusBadgePresentationTest {

    @Test
    fun `available and partially available render the available check`() {
        for (status in listOf(SeerrMediaStatus.AVAILABLE, SeerrMediaStatus.PARTIALLY_AVAILABLE)) {
            assertEquals("✓" to StatusColors.available, seerrStatusBadgePresentation(status, hasRequest = false))
            assertEquals("✓" to StatusColors.available, seerrStatusBadgePresentation(status, hasRequest = true))
        }
    }

    @Test
    fun `processing renders its own in-flight state, not pending`() {
        assertEquals("⟳" to StatusColors.info, seerrStatusBadgePresentation(SeerrMediaStatus.PROCESSING, hasRequest = false))
        assertEquals("⟳" to StatusColors.info, seerrStatusBadgePresentation(SeerrMediaStatus.PROCESSING, hasRequest = true))
    }

    @Test
    fun `pending renders the pending hourglass`() {
        assertEquals("⏳" to StatusColors.pending, seerrStatusBadgePresentation(SeerrMediaStatus.PENDING, hasRequest = false))
    }

    @Test
    fun `a request entry without a recognizable status renders requested`() {
        assertEquals("→" to StatusColors.requested, seerrStatusBadgePresentation(SeerrMediaStatus.UNKNOWN, hasRequest = true))
        assertEquals("→" to StatusColors.requested, seerrStatusBadgePresentation(SeerrMediaStatus.DELETED, hasRequest = true))
    }

    @Test
    fun `nothing renders without an available or requested state`() {
        assertEquals("" to Color.Transparent, seerrStatusBadgePresentation(SeerrMediaStatus.UNKNOWN, hasRequest = false))
        assertEquals("" to Color.Transparent, seerrStatusBadgePresentation(SeerrMediaStatus.DELETED, hasRequest = false))
    }
}
