package com.raulshma.jellyplay.core.data.worker

import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType

/**
 * The [MediaItem] season/episode builders the auto-download suites share —
 * `AutoDownloadCheckTest` and `DesktopAutoDownloadSchedulerTest` build their
 * catalogue snapshots from the same shapes.
 */
internal fun season(id: String) = MediaItem(id = id, name = "Season $id", mediaType = MediaType.SEASON)

internal fun episode(id: String, seasonId: String, seriesId: String = "s1") = MediaItem(
    id = id,
    name = "Episode $id",
    mediaType = MediaType.EPISODE,
    seriesId = seriesId,
    seasonId = seasonId,
)
