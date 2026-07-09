package com.raulshma.jellyplay.feature.livetv.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.raulshma.jellyplay.core.ui.navigation.Navigator
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.feature.livetv.LiveTvScreen

/**
 * Registers the Live TV feature's navigation entries.
 *
 * Live TV is a single top-level destination ([Route.LiveTv]) rendered as a
 * 6-tab screen (Programs, Guide, Channels, Recordings, Schedule, Series) —
 * matching jellyfin-web's Live TV collection. The previous separate
 * [Route.LiveTvGuide] / [Route.Dvr] push destinations are subsumed by the
 * tabs and have been removed.
 */
fun EntryProviderScope<NavKey>.liveTvSection(navigator: Navigator) {
    entry<Route.LiveTv> {
        LiveTvScreen(
            onChannelClick = { channelId, channelName ->
                navigator.navigate(Route.LiveTvChannelPlayer(channelId, channelName))
            },
            onRecordingClick = { recordingId ->
                navigator.navigate(Route.VideoPlayer(itemId = recordingId))
            },
            onFolderClick = { folder ->
                navigator.navigate(
                    Route.LibraryBrowse(
                        folderId = folder.id,
                        folderName = folder.name,
                        collectionType = folder.collectionType,
                    )
                )
            },
        )
    }
}
