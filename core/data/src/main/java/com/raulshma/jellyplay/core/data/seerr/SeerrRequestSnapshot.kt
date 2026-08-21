package com.raulshma.jellyplay.core.data.seerr

import com.raulshma.jellyplay.core.model.seerr.SeerrRadarrServiceDetail
import com.raulshma.jellyplay.core.model.seerr.SeerrRequestResult
import com.raulshma.jellyplay.core.model.seerr.SeerrSeason
import com.raulshma.jellyplay.core.model.seerr.SeerrSonarrServiceDetail

/**
 * One emission bundling every piece of [SeerrRequestStateHolder] state, so
 * consumers fold a single flow into their ui state instead of re-deriving a
 * nested combine over the individual flows (which each new holder flow used
 * to force every consumer to re-write).
 */
data class SeerrRequestSnapshot(
    val requestResult: SeerrRequestResult? = null,
    val radarrServers: List<SeerrRadarrServiceDetail> = emptyList(),
    val sonarrServers: List<SeerrSonarrServiceDetail> = emptyList(),
    val isLoadingServices: Boolean = false,
    val tvSeasons: List<SeerrSeason> = emptyList(),
    val tvIsAnime: Boolean = false,
)
