package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.model.MediaSource

/**
 * Builds the [TrackOption] rows for the player's Version sheet from the
 * session detail's media sources. Each row carries its media-source id in
 * [TrackOption.id] (the select handler routes it to
 * [PlayerSessionManager.switchMediaSource]); the positional [TrackOption.index]
 * only serves as the LazyColumn key. The label is the server version name —
 * suffixed with the derived quality badge (" · 4K HDR10") when one resolves,
 * and falling back to the quality/container/id ladder for unnamed sources
 * ([MediaSource.versionLabel]). Pure → directly unit-testable.
 */
internal fun buildVersionTrackOptions(
    mediaSources: List<MediaSource>,
    currentSourceId: String?,
): List<TrackOption> = mediaSources.mapIndexed { index, source ->
    val quality = source.qualityLabel()
    val label = when {
        source.name.isBlank() -> source.versionLabel()
        quality == null -> source.name
        else -> "${source.name} · $quality"
    }
    TrackOption(
        index = index,
        label = label,
        language = null,
        isSelected = source.id == currentSourceId,
        id = source.id,
    )
}
