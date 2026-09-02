package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.model.MediaDetail
import io.mockk.coEvery

/**
 * Stubs cache-busted detail reads for [itemId]: the FIRST read returns
 * [first], every later read returns [rest]. The download flow's first read is
 * the pre-download snapshot seed; the rest are appear-polls. Shared by
 * [SubtitleManagerTest] (Jellyfin/offline path) and
 * [SubtitleManagerProviderDownloadTest] (external-provider path).
 */
internal fun MediaRepository.stubDetailReads(itemId: String, first: MediaDetail, rest: MediaDetail) {
    var reads = 0
    coEvery { getMediaDetail(itemId, any()) } coAnswers {
        reads++
        if (reads == 1) Result.success(first) else Result.success(rest)
    }
}
