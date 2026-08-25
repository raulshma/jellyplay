package com.raulshma.jellyplay.feature.details


/**
 * Bundles the action helpers' Hilt factories into a single constructor
 * parameter for [DetailViewModel], so the helper-exclusive collaborators
 * (download intake/store/bitrate/repository, offline sync, the watch-later
 * runtime store, SyncPlay) reach the helpers without ever appearing in the
 * ViewModel's own constructor. Pure DI aggregation — no behaviour.
 */
internal class DetailActionFactories constructor(
    val downloads: DownloadLifecycleActions.Factory,
    val resync: ResyncActions.Factory,
    val playlists: PlaylistActions.Factory,
    val watchParty: WatchPartyActions.Factory,
)
