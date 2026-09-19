package com.raulshma.jellyplay.feature.arrqueue

import com.raulshma.jellyplay.core.designsystem.theme.StatusColors
import com.raulshma.jellyplay.core.model.arr.ArrDownloadStatus
import com.raulshma.jellyplay.core.model.arr.ArrServiceKind
import com.raulshma.jellyplay.feature.arrqueue.generated.resources.Res
import com.raulshma.jellyplay.feature.arrqueue.generated.resources.arrqueue_brand_radarr
import com.raulshma.jellyplay.feature.arrqueue.generated.resources.arrqueue_brand_sonarr
import com.raulshma.jellyplay.feature.arrqueue.generated.resources.arrqueue_status_completed
import com.raulshma.jellyplay.feature.arrqueue.generated.resources.arrqueue_status_downloading
import com.raulshma.jellyplay.feature.arrqueue.generated.resources.arrqueue_status_failed
import com.raulshma.jellyplay.feature.arrqueue.generated.resources.arrqueue_status_imported
import com.raulshma.jellyplay.feature.arrqueue.generated.resources.arrqueue_status_paused
import com.raulshma.jellyplay.feature.arrqueue.generated.resources.arrqueue_status_queued
import com.raulshma.jellyplay.feature.arrqueue.generated.resources.arrqueue_status_unknown
import com.raulshma.jellyplay.feature.arrqueue.generated.resources.arrqueue_status_warning
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins every arm of [ArrQueuePresentation] exhaustively — the status→color
 * and kind→label/service tables the queue screen's composables render. The
 * resource-identity assertions pin WHICH string each arm resolves (labels
 * stay unresolved [org.jetbrains.compose.resources.StringResource]s, so the
 * assertion compares the resource object itself), and the color assertions
 * pin the tint per arm. A table edit must update this file in lockstep.
 */
class ArrQueuePresentationTest {

    @Test
    fun `service badge pins brand label and tint per kind`() {
        ArrServiceKind.entries.forEach { kind ->
            val badge = ArrQueuePresentation.serviceBadge(kind)
            when (kind) {
                ArrServiceKind.RADARR -> {
                    assertEquals(Res.string.arrqueue_brand_radarr, badge.label)
                    assertEquals(StatusColors.requested, badge.color)
                }
                ArrServiceKind.SONARR -> {
                    assertEquals(Res.string.arrqueue_brand_sonarr, badge.label)
                    assertEquals(StatusColors.pending, badge.color)
                }
            }
        }
    }

    @Test
    fun `status chip pins label and tint for every download status`() {
        val expected = mapOf(
            ArrDownloadStatus.DOWNLOADING to (Res.string.arrqueue_status_downloading to StatusColors.available),
            ArrDownloadStatus.QUEUED to (Res.string.arrqueue_status_queued to StatusColors.info),
            ArrDownloadStatus.PAUSED to (Res.string.arrqueue_status_paused to StatusColors.pending),
            ArrDownloadStatus.COMPLETED to (Res.string.arrqueue_status_completed to StatusColors.available),
            ArrDownloadStatus.IMPORTED to (Res.string.arrqueue_status_imported to StatusColors.success),
            ArrDownloadStatus.FAILED to (Res.string.arrqueue_status_failed to StatusColors.error),
            ArrDownloadStatus.WARNING to (Res.string.arrqueue_status_warning to StatusColors.warning),
            ArrDownloadStatus.UNKNOWN to (Res.string.arrqueue_status_unknown to StatusColors.debug),
        )
        ArrDownloadStatus.entries.forEach { status ->
            val (label, color) = ArrQueuePresentation.statusChip(status)
            assertEquals(expected.getValue(status).first, label, "chip label for $status")
            assertEquals(expected.getValue(status).second, color, "chip tint for $status")
        }
        // Exhaustive: the table's arms are exactly the enum.
        assertEquals(ArrDownloadStatus.entries.toSet(), expected.keys.toSet())
    }

    @Test
    fun `progress color pins every status arm including the else arm`() {
        val expected = mapOf(
            ArrDownloadStatus.COMPLETED to StatusColors.available,
            ArrDownloadStatus.IMPORTED to StatusColors.success,
            ArrDownloadStatus.PAUSED to StatusColors.pending,
            ArrDownloadStatus.DOWNLOADING to StatusColors.requested,
            ArrDownloadStatus.FAILED to StatusColors.error,
            ArrDownloadStatus.WARNING to StatusColors.warning,
            // The else arm: QUEUED and UNKNOWN ride the info tint (empty
            // track / unknown progress both read as neutral).
            ArrDownloadStatus.QUEUED to StatusColors.info,
            ArrDownloadStatus.UNKNOWN to StatusColors.info,
        )
        ArrDownloadStatus.entries.forEach { status ->
            assertEquals(expected.getValue(status), ArrQueuePresentation.progressColor(status), "progress tint for $status")
        }
    }

    @Test
    fun `service name pins the brand label per kind`() {
        assertEquals(Res.string.arrqueue_brand_radarr, ArrQueuePresentation.serviceName(ArrServiceKind.RADARR))
        assertEquals(Res.string.arrqueue_brand_sonarr, ArrQueuePresentation.serviceName(ArrServiceKind.SONARR))
    }
}
