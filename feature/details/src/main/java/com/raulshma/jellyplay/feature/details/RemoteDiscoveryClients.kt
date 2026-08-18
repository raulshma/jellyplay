package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import com.raulshma.jellyplay.core.data.repository.ArrRepository
import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.data.seerr.SeerrRequestDelegate
import javax.inject.Inject

/**
 * Bundles the remote-discovery family — Seerr search/requests (incl. the
 * TMDB trailer fallback), Arr (Sonarr/Radarr) server resolution, and the
 * offline-mode gate
 * that fences them off on local connections — into a single constructor
 * parameter for [DetailViewModel], following the [DetailActionFactories]
 * aggregation pattern. Pure DI aggregation — no behaviour.
 */
internal class RemoteDiscoveryClients @Inject constructor(
    val seerrRepository: SeerrRepository,
    val seerrRequestDelegate: SeerrRequestDelegate,
    val arrRepository: ArrRepository,
    val offlineModeManager: OfflineModeManager,
)
