package com.raulshma.jellyplay.core.model.seerr

/**
 * One emission bundling every piece of the Seerr request holder's state
 * (core:data's `SeerrRequestStateHolder`), so consumers fold a single flow
 * into their ui state instead of re-deriving a nested combine over the
 * individual flows (which each new holder flow used to force every consumer
 * to re-write). It lives here, with the other Seerr models, so core/ui can
 * see it — `SeerrRequestDialog` folds it into dialog fields in one place.
 */
data class SeerrRequestSnapshot(
    val requestResult: SeerrRequestResult? = null,
    val radarrServers: List<SeerrRadarrServiceDetail> = emptyList(),
    val sonarrServers: List<SeerrSonarrServiceDetail> = emptyList(),
    val isLoadingServices: Boolean = false,
    val tvSeasons: List<SeerrSeason> = emptyList(),
    val tvIsAnime: Boolean = false,
)
