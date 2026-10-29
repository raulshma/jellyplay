package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.model.JellyPlayPluginFeatures
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.arr.ArrServiceSummary
import com.raulshma.jellyplay.core.model.seriesIdForDetail
import kotlinx.coroutines.delay

/**
 * The VM-state enrichment module: an enrichment arm family that feeds
 * [DetailUiState] fields but owns no section slot — the section registry's
 * sibling. Wave 3 of the detail modularization: the twelve declarations that
 * lived inline in DetailViewModel's `enrichments` initializer moved here
 * verbatim (guards and comments included), their reads swapped to the
 * [DetailSectionEnrichmentHost] guard/funnel plus the per-family fetch roles
 * ([DetailRatingsRoles], [DetailSimilarRoles], [DetailPluginRoles],
 * [DetailMediaRoles], [DetailAmbientRoles]) the module's `enrichments`
 * signature declares — only the roles its bodies read. A module is the unit a
 * new state-feeding enrichment joins — DetailViewModel stops being edited for
 * arm additions; [DetailEnrichmentRegistry] is the single wiring point that
 * hands the roles out.
 *
 * Two conventions carried over from the hand-written entries:
 *  - the gate lambda stays a pure snapshot read — a user preference rides the
 *    BODY via [DetailSectionEnrichmentHost.detailPreferences] (the
 *    externalRatings convention). The former themeMusic/themeVideo gate
 *    pref-reads moved to their bodies: same observable behavior (a gated-out
 *    body writes nothing either way), one more launched coroutine.
 *  - every body re-checks the staleness guard at its suspension-point writes
 *    ([DetailSectionEnrichmentHost.isCurrent]) — [DetailLoadGuard] stays the
 *    single admission seam.
 */

/** The stock similar row's limit (the plugin row mirrors it). */
internal const val SIMILAR_ITEMS_LIMIT = 12

/**
 * The state-arm registry: the ordered arm list spliced around the section
 * registry's arms in DetailViewModel's fan-out. The split is load-bearing
 * order, not taxonomy: the LEADING arms fire before the section arms (whose
 * pluginRatings probe externalRatings reads — the latch), the TRAILING arms
 * after. All arms are independent fire-and-forget guard-checked coroutines
 * (single-writer-per-field), so positions within a list preserve the
 * observable behavior the fan-out order contract documents.
 *
 * This object is the single wiring point for the state arms: each module's
 * `enrichments` declares only the roles it reads (the per-family role
 * interfaces beside [DetailSectionEnrichmentHost]) and is handed exactly
 * those here.
 */
internal object DetailEnrichmentRegistry {

    fun leadingArms(
        host: DetailSectionEnrichmentHost,
        ambient: DetailAmbientRoles,
        similar: DetailSimilarRoles,
        media: DetailMediaRoles,
        pluginGate: DetailPluginGateRole,
    ): List<DetailEnrichment> =
        ThemeMusicEnrichmentModule.enrichments(host, ambient) +
            ArrServerResolveEnrichmentModule.enrichments(host, ambient) +
            SimilarItemsEnrichmentModule.enrichments(host, similar) +
            MediaSegmentsEnrichmentModule.enrichments(host, media) +
            SeerrDiscoveryEnrichmentModule.enrichments(host, ambient) +
            LocalRelatedItemsEnrichmentModule.enrichments(host, similar)

    fun trailingArms(
        host: DetailSectionEnrichmentHost,
        pluginGate: DetailPluginGateRole,
        ratings: DetailRatingsRoles,
        plugin: DetailPluginRoles,
        media: DetailMediaRoles,
    ): List<DetailEnrichment> =
        ExternalRatingsEnrichmentModule.enrichments(host, pluginGate, ratings) +
            PluginSimilarItemsEnrichmentModule.enrichments(host, pluginGate, plugin) +
            PluginAnimeMarkersEnrichmentModule.enrichments(host, pluginGate, plugin) +
            PluginSeasonRatingsEnrichmentModule.enrichments(host, pluginGate, ratings) +
            TrailersEnrichmentModule.enrichments(host, media) +
            ThemeVideoEnrichmentModule.enrichments(host, media)
}

/**
 * The theme-music arm: plays the series' theme while its detail screen is up.
 * Gate matrix preserved from the hand-written entry: the global pref must be
 * ON and the series not per-series muted (the mute chip's memory) on top of
 * the shared remote gate — a muted series's enrichment doesn't start playback
 * at all.
 */
internal object ThemeMusicEnrichmentModule {
    fun enrichments(
        host: DetailSectionEnrichmentHost,
        ambient: DetailAmbientRoles,
    ) = listOf(
        DetailEnrichment(
            name = "themeMusic",
            gate = { it.remoteDiscoveryAllowed },
        ) { inputs ->
            val prefs = host.detailPreferences()
            val themeSourceId = inputs.detail.item.seriesId ?: inputs.itemId
            if (!prefs.playThemeMusic || prefs.isThemeMusicMutedFor(themeSourceId)) return@DetailEnrichment
            ambient.playThemeMusic(themeSourceId)
        },
    )
}

/** The Sonarr-resolution arm: latches the canManageSeries menu's server flag. */
internal object ArrServerResolveEnrichmentModule {
    fun enrichments(
        host: DetailSectionEnrichmentHost,
        ambient: DetailAmbientRoles,
    ) = listOf(
        DetailEnrichment(
            name = "arrServerResolve",
            // SERIES/EPISODE only (the canManageSeries menu lives on series
            // detail), and only when a Sonarr-resolvable tvdb id exists.
            gate = {
                it.remoteDiscoveryAllowed &&
                    (it.mediaType == MediaType.SERIES || it.mediaType == MediaType.EPISODE) &&
                    it.detail.providerIds["tvdb"]?.toIntOrNull() != null
            },
        ) { inputs ->
            val summary = ambient.resolveArrServers()
                .getOrDefault(ArrServiceSummary())
            // Guard: don't write sonarr resolution onto a different item's state.
            if (host.isCurrent(inputs.itemId)) {
                host.updateState { it.copy(sonarrServersResolved = summary.sonarrServers.isNotEmpty()) }
            }
        },
    )
}

/** The stock "More like this" arm: server-scored similar items, self-filtered. */
internal object SimilarItemsEnrichmentModule {
    fun enrichments(
        host: DetailSectionEnrichmentHost,
        similar: DetailSimilarRoles,
    ) = listOf(
        DetailEnrichment(
            name = "similarItems",
            gate = { it.remoteDiscoveryAllowed },
        ) { inputs ->
            // Fetch similar/related items concurrently and non-blocking so the core
            // detail renders immediately. The result lands in relatedItems.
            similar.similarItems(inputs.itemId, limit = SIMILAR_ITEMS_LIMIT)
                .onSuccess { items ->
                    if (!host.isCurrent(inputs.itemId)) return@onSuccess
                    host.updateState {
                        it.copy(relatedItems = items.filter { related -> related.id != inputs.itemId })
                    }
                }
        },
    )
}

/**
 * The media-segments arm: pre-warms the player's intro/credits TTL cache and
 * surfaces the two skip affordances as detail-side chips.
 */
internal object MediaSegmentsEnrichmentModule {
    fun enrichments(
        host: DetailSectionEnrichmentHost,
        media: DetailMediaRoles,
    ) = listOf(
        DetailEnrichment(
            name = "mediaSegments",
            gate = { it.remoteDiscoveryAllowed },
        ) { inputs ->
            // Pre-warm the player's media-segment TTL cache and surface the two
            // intro/credits skip affordances as a detail-side chip. The cache fill
            // is an implicit side effect of the call; only the booleans flow into
            // uiState so the chip can render before the player attaches.
            media.mediaSegments(inputs.itemId).onSuccess { segments ->
                if (!host.isCurrent(inputs.itemId)) return@onSuccess
                val availability = segments.toAvailability()
                host.updateState {
                    it.copy(
                        hasIntroSegment = availability.hasIntro,
                        hasCreditSegment = availability.hasCredits,
                    )
                }
            }
        },
    )
}

/**
 * The Seerr companion arm: triggers the recommendations/videos fetch. The
 * 350ms delay is preserved for frame priority (don't contend with first-frame
 * GPU work); seerrDataLoaded keeps it idempotent across re-entries.
 */
internal object SeerrDiscoveryEnrichmentModule {
    fun enrichments(
        host: DetailSectionEnrichmentHost,
        ambient: DetailAmbientRoles,
    ) = listOf(
        DetailEnrichment(
            name = "seerrDiscovery",
            gate = { it.remoteDiscoveryAllowed },
        ) { inputs ->
            delay(350)
            if (!host.isCurrent(inputs.itemId)) return@DetailEnrichment
            ambient.triggerSeerrDiscovery(inputs.detail)
        },
    )
}

/**
 * The LOCAL-origin counterpart arm: remote discovery is server-only, so a
 * downloaded item would otherwise render as an island. Requires at least one
 * genre/studio to mine the on-device offline library for (the former
 * triggerLocalSideEffects early return).
 */
internal object LocalRelatedItemsEnrichmentModule {
    fun enrichments(
        host: DetailSectionEnrichmentHost,
        similar: DetailSimilarRoles,
    ) = listOf(
        DetailEnrichment(
            name = "localRelatedItems",
            gate = {
                !it.isRemote &&
                    (it.detail.item.genres.isNotEmpty() || it.detail.item.studios.isNotEmpty())
            },
        ) { inputs ->
            val related = similar.localRelatedItems(
                currentId = inputs.itemId,
                genres = inputs.detail.item.genres,
                studios = inputs.detail.item.studios,
                limit = SIMILAR_ITEMS_LIMIT,
            )
            if (!host.isCurrent(inputs.itemId)) return@DetailEnrichment
            host.updateState {
                it.copy(localRelatedItems = related.filter { r -> r.id != inputs.itemId })
            }
        },
    )
}

/**
 * The no-plugin ratings strip (RT/IMDb/TMDB chips when the plugin is absent).
 * Source order mirrors the spec: Seerr's detail-by-provider-id ratings first
 * (the loadSeerrData path — same repository, same tmdbId resolution), the
 * direct-TMDB vote aggregate as the fallback when Seerr is disconnected or
 * its ratings fetch failed/returned nothing. MOVIE/SERIES only: the
 * provider-id sources and the ratings payload exist for nothing else.
 */
internal object ExternalRatingsEnrichmentModule {
    fun enrichments(
        host: DetailSectionEnrichmentHost,
        pluginGate: DetailPluginGateRole,
        ratings: DetailRatingsRoles,
    ) = listOf(
        DetailEnrichment(
            name = "externalRatings",
            gate = { it.remoteDiscoveryAllowed },
        ) { inputs ->
            // The user-pref half of the gate rides the body: the gate lambda
            // stays a pure snapshot read (the DetailEnrichmentInputs
            // contract), so `showExternalRatings` is checked here instead —
            // a preference read, not snapshot state.
            if (!host.detailPreferences().showExternalRatings) return@DetailEnrichment
            // The plugin dedupe probe, the pluginSimilarSuppressesStock
            // mirror: one synchronous Ratings-feature read (the store refresh
            // is latched — the pluginRatings enrichment above probed this
            // item already), remembered on the state, and suppressing the external-ratings
            // chips at RENDER time only when the plugin row actually has
            // content. `!hasPluginRatings` cannot gate the START — the plugin
            // row's content lands asynchronously from the same fan-out.
            val pluginRatingsLive = pluginGate.pluginFeature(JellyPlayPluginFeatures.Ratings, inputs.itemId)
            val mediaType = inputs.mediaType
            if (mediaType != MediaType.MOVIE && mediaType != MediaType.SERIES) return@DetailEnrichment
            val tmdbId = resolveTmdbId(inputs.detail) ?: return@DetailEnrichment

            // Seerr ratings first — the same connection-flag read
            // loadSeerrData uses (the flags live only on the published
            // aggregator, never on _uiState). The fetch is cache-backed
            // (SeerrRepositoryImpl's detail cache), so a failed/empty Seerr
            // payload costs one call before the TMDB fallback.
            val seerrRatings = if (host.currentState().isSeerrConnected) {
                ratings.seerrRatings(tmdbId, if (mediaType == MediaType.MOVIE) "movie" else "tv")
                    .getOrNull()
                    ?.takeUnless { seerr ->
                        seerr.rt?.criticsScore == null && seerr.rt?.audienceScore == null &&
                            seerr.imdb?.criticsScore == null && seerr.imdb?.rating == null &&
                            seerr.tmdb?.rating == null
                    }
                    ?.let { DetailExternalRatings.fromSeerr(it) }
            } else {
                null
            }
            val resolved = seerrRatings
                // TMDB-only fallback: the public vote aggregate + the
                // constructed provider link. Null seam (unwired tests) or a
                // failed fetch leave the strip empty — silent absence.
                ?: ratings.tmdbDetails(tmdbId, mediaType)?.let { details ->
                    DetailExternalRatings.tmdbOnly(
                        voteAverage = details.voteAverage?.toFloat(),
                        tmdbPathSegment = if (mediaType == MediaType.MOVIE) "movie" else "tv",
                        tmdbId = tmdbId,
                    )
                }
            if (resolved == null || resolved.isEmpty) return@DetailEnrichment
            // The OMDb awards leg (the `omdb-awards` feature): an award badge
            // beside the chips when the plugin source answers AND the
            // appearance toggle allows badges. Every leg missing (unwired
            // seam, plugin unavailable, toggle off, no data for the id)
            // renders nothing — silent absence, the plugin contract. The
            // call is null-safe like the mdbList one; the toggle read is
            // a synchronous slice value (a preference read, not snapshot state).
            val awards: String? = if (
                ratings.awardsBadgesEnabled() &&
                pluginGate.pluginFeature(JellyPlayPluginFeatures.OmdbAwards, inputs.itemId)
            ) {
                resolveImdbId(inputs.detail)?.let { imdbId ->
                    ratings.omdbAwards(imdbId)?.awards
                }
            } else {
                null
            }
            if (!host.isCurrent(inputs.itemId)) return@DetailEnrichment
            host.updateState {
                it.copy(
                    externalRatings = resolved.copy(awards = awards),
                    externalRatingsSuppressedByPlugin = pluginRatingsLive,
                )
            }
        },
    )
}

/**
 * The server-scored "More like this" arm, rendered BESIDE the stock one. The
 * stock row stands down only when the capabilities handshake says the plugin
 * registered into the host's similar pipeline (Jellyfin 12+ — there the stock
 * endpoint returns the same scored list and both rows would duplicate); on
 * pre-12 hosts / older plugins the lists differ and both render. The plugin
 * returns scored item ids only, hydrated through ONE batched ids read (the
 * home plugin-row seam — the row renders poster cards, so list-grade fields
 * suffice) that restores the score order; ids the server no longer knows
 * drop and an empty hydration leaves the stock row in place.
 */
internal object PluginSimilarItemsEnrichmentModule {
    fun enrichments(
        host: DetailSectionEnrichmentHost,
        pluginGate: DetailPluginGateRole,
        plugin: DetailPluginRoles,
    ) = listOf(
        DetailEnrichment(
            name = "pluginSimilarItems",
            gate = { it.remoteDiscoveryAllowed },
        ) { inputs ->
            if (!pluginGate.pluginFeature(JellyPlayPluginFeatures.Recommendations, inputs.itemId)) return@DetailEnrichment
            val scored = plugin.pluginSimilarIds(inputs.itemId, limit = SIMILAR_ITEMS_LIMIT)
                .filter { it != inputs.itemId }
                .distinct()
            if (scored.isEmpty()) return@DetailEnrichment
            // ONE batched ids read (the home plugin-row resolution pattern)
            // instead of a per-id detail fan-out — twelve full-detail round
            // trips for a poster-card row were pure waste. Ids the server no
            // longer knows drop (found-only); a failed/empty read leaves the
            // stock row in place. Deliberately a server-side read (no offline
            // fallback — the old per-id detail path had one): offline, the
            // row degrades to the stock similar row.
            val hydrated = plugin.mediaDetailItems(scored)
            if (!host.isCurrent(inputs.itemId)) return@DetailEnrichment
            host.updateState {
                it.copy(
                    pluginSimilarItems = hydrated
                        .filter { item -> item.id != inputs.itemId }
                        .distinctBy { item -> item.id },
                    // The probe ran synchronously inside pluginFeature above, so
                    // the capabilities payload is fresh for this read.
                    pluginSimilarSuppressesStock = plugin.serverSimilarPipeline(),
                )
            }
        },
    )
}

/**
 * The series-scoped filler/recap badges arm for the seasons section's episode
 * rows. SERIES resolves to itself, EPISODE/SEASON to the parent series
 * (seriesIdForDetail) — the same series context the seasons tree renders.
 */
internal object PluginAnimeMarkersEnrichmentModule {
    fun enrichments(
        host: DetailSectionEnrichmentHost,
        pluginGate: DetailPluginGateRole,
        plugin: DetailPluginRoles,
    ) = listOf(
        DetailEnrichment(
            name = "pluginAnimeMarkers",
            gate = {
                it.remoteDiscoveryAllowed && it.detail.item.seriesIdForDetail != null
            },
        ) { inputs ->
            if (!pluginGate.pluginFeature(JellyPlayPluginFeatures.AnimeMarkers, inputs.itemId)) return@DetailEnrichment
            val seriesId = inputs.detail.item.seriesIdForDetail ?: return@DetailEnrichment
            val providerSeriesId = resolveProviderSeriesId(inputs.detail) ?: return@DetailEnrichment
            val markers = plugin.animeMarkers(seriesId, providerSeriesId) ?: return@DetailEnrichment
            if (!host.isCurrent(inputs.itemId)) return@DetailEnrichment
            host.updateState { it.copy(animeMarkers = animeBadges(markers)) }
        },
    )
}

/**
 * The TMDB per-episode scores arm for the seasons section's episode rows +
 * the season header's average (ADR 0010, the `ratings` feature family's
 * season leg). Same series context as the anime badges; one fetch per LOADED
 * season (a lazily-expanded season re-emits a new generation, so the
 * enrichment re-runs and picks it up — seasons already scored on a previous
 * generation merge in place of a re-fetch, so the re-run requests only the
 * new arrivals). The shared bounded-parallel per-season fetch (the
 * pluginSimilarItems idiom): per-season null/empty results and failures drop
 * out; a wholly empty fold leaves the field empty — silent absence, never an
 * error.
 */
internal object PluginSeasonRatingsEnrichmentModule {
    fun enrichments(
        host: DetailSectionEnrichmentHost,
        pluginGate: DetailPluginGateRole,
        ratings: DetailRatingsRoles,
    ) = listOf(
        DetailEnrichment(
            name = "pluginSeasonRatings",
            gate = {
                it.remoteDiscoveryAllowed && it.detail.item.seriesIdForDetail != null
            },
        ) { inputs ->
            if (!pluginGate.pluginFeature(JellyPlayPluginFeatures.Ratings, inputs.itemId)) return@DetailEnrichment
            val tmdbId = resolveTmdbId(inputs.detail) ?: return@DetailEnrichment
            // Seasons still unscored on this field's last emission (the
            // merge source) — numberless seasons never fetch (the serial
            // loop's `?: continue`).
            val alreadyScored = host.currentState().seasonRatings.keys
            val seasonsToFetch = inputs.snapshot.seasons.mapNotNull { season ->
                val seasonNumber = season.indexNumber ?: return@mapNotNull null
                if (season.id in alreadyScored) return@mapNotNull null
                season to seasonNumber
            }
            if (seasonsToFetch.isEmpty()) return@DetailEnrichment
            val fetchedRatingsBySeasonId = host
                .mapConcurrent(seasonsToFetch) { (season, seasonNumber) ->
                    ratings.tmdbSeasonRatings(tmdbId.toString(), seasonNumber)
                        ?.takeIf { it.isNotEmpty() }
                        ?.let { ratings -> season.id to ratings }
                }
                .filterNotNull()
                .toMap()
            if (fetchedRatingsBySeasonId.isEmpty()) return@DetailEnrichment
            if (!host.isCurrent(inputs.itemId)) return@DetailEnrichment
            host.updateState { it.copy(seasonRatings = it.seasonRatings + fetchedRatingsBySeasonId) }
        },
    )
}

/**
 * The merged trailer list's local leg — the server-hosted trailers (the
 * parental-filtered getLocalTrailers wrapper) merged with the TMDB/Seerr
 * videos into `state.trailers`, ordered by the `preferLocalTrailers` pref.
 * The remote leg re-merges on its own landing (foldCompanionLanding's Videos
 * case — the seerrDiscovery enrichment fetches it independently and later),
 * so whichever source resolves first already leaves a correct list. A failed
 * local fetch degrades to the remote-only list (today's content);
 * remote-only is exactly the no-local-trailer shape.
 */
internal object TrailersEnrichmentModule {
    fun enrichments(
        host: DetailSectionEnrichmentHost,
        media: DetailMediaRoles,
    ) = listOf(
        DetailEnrichment(
            name = "trailers",
            gate = { it.remoteDiscoveryAllowed },
        ) { inputs ->
            val local = media.localTrailers(inputs.itemId).getOrNull().orEmpty()
            if (!host.isCurrent(inputs.itemId)) return@DetailEnrichment
            host.updateState {
                it.copy(
                    trailers = mergeTrailerEntries(
                        localTrailers = local,
                        remoteVideos = it.relatedVideos,
                        preferLocalTrailers = host.detailPreferences().preferLocalTrailers,
                    ),
                )
            }
        },
    )
}

/**
 * The server ThemeVideo clip arm behind the detail hero's ambient video
 * (preferred over the YouTube embed when it resolves). Same remote gate as
 * the trailers leg — the user-pref half (`trailerAutoplay`) rides the body:
 * without the autoplay pref no mount ever reads the URL, so the fetch is
 * skipped entirely. A failed/empty lookup degrades to the embed (today's
 * content); the URL is shaped fresh against the active session.
 */
internal object ThemeVideoEnrichmentModule {
    fun enrichments(
        host: DetailSectionEnrichmentHost,
        media: DetailMediaRoles,
    ) = listOf(
        DetailEnrichment(
            name = "themeVideo",
            gate = { it.remoteDiscoveryAllowed },
        ) { inputs ->
            if (!host.detailPreferences().trailerAutoplay) return@DetailEnrichment
            val clip = media.themeVideoClips(inputs.itemId).firstOrNull() ?: return@DetailEnrichment
            val streamUrl = media.streamUrl(itemId = clip.id, mediaSourceId = clip.id)
            if (streamUrl.isBlank()) return@DetailEnrichment
            if (!host.isCurrent(inputs.itemId)) return@DetailEnrichment
            host.updateState { it.copy(themeVideoStreamUrl = streamUrl) }
        },
    )
}
