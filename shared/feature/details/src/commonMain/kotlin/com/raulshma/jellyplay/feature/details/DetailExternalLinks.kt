package com.raulshma.jellyplay.feature.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.designsystem.theme.BrandColors
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.DetailOrigin
import com.raulshma.jellyplay.core.model.ExternalUrl
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.ui.components.focusIndicator

/**
 * The shared external-link chip row, extracted verbatim from the Seerr
 * family's private `ExternalLinksRow` (SeerrDetailScreen) and generalized
 * from Seerr DTO ids to plain [ExternalUrl] links — the core-model
 * label + url pair the media detail already carries. Both families render
 * the same brand-tinted pill vocabulary; the brand tint is derived from the
 * link's host (TMDB / IMDb / TVDb) with a muted neutral fallback for the
 * server's other external urls, so one renderer covers both surfaces.
 *
 * The link list itself is assembled by [buildDetailExternalLinks] on the
 * media side and inline at the Seerr call site (which keeps its exact prior
 * id sources and url construction).
 */
@Composable
internal fun DetailExternalLinksRow(
    links: List<ExternalUrl>,
    modifier: Modifier = Modifier,
    // The Seerr family centers its three brand chips; the media-detail screen
    // overrides this to a start-aligned arrangement matching its chip rows.
    arrangement: Arrangement.Horizontal =
        Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
) {
    if (links.isEmpty()) return
    val uriHandler = LocalUriHandler.current

    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = arrangement,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        links.forEach { link ->
            val brandColor = externalLinkBrandColor(link.url)
            SuggestionChip(
                onClick = { uriHandler.openUri(link.url) },
                label = {
                    Text(
                        text = link.name.ifBlank { externalLinkHost(link.url) ?: link.url },
                        fontWeight = FontWeight.Bold,
                    )
                },
                modifier = Modifier.focusIndicator(ShapeCache.smoothPill),
                colors = SuggestionChipDefaults.suggestionChipColors(
                    containerColor = (brandColor ?: MaterialTheme.colorScheme.onSurfaceVariant)
                        .copy(alpha = 0.2f),
                    labelColor = brandColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                border = null
            )
        }
    }
}

/**
 * The brand accent for a link chip, read off the url host: the three hosts the
 * detail surfaces carry dedicated [BrandColors] entries; every other host (the
 * server's official-site / social links) renders the neutral fallback. Host
 * containment (not equality) matches how [resolveTmdbId] / [resolveImdbId]
 * recognize the same sites.
 */
private fun externalLinkBrandColor(url: String): Color? = when {
    url.contains("themoviedb.org") -> BrandColors.tmdb
    url.contains("imdb.com") -> BrandColors.imdb
    url.contains("thetvdb.com") -> BrandColors.tvdb
    else -> null
}

/**
 * Lowercased url host with the `www.` prefix stripped — the dedupe key of
 * [buildDetailExternalLinks]. Null when the url carries no `scheme://`
 * authority (callers then fall back to the whole url as the key, so distinct
 * malformed entries survive and identical ones collapse).
 */
internal fun externalLinkHost(url: String): String? {
    val rest = url.substringAfter("://", "")
    if (rest.isEmpty()) return null
    val host = rest.substringBefore('/').substringBefore('?').lowercase()
    return host.removePrefix("www.").ifEmpty { null }
}

/**
 * The media detail's merged external-link list, pure → directly
 * unit-testable (the [resolveTmdbId] pattern):
 *
 * 1. The server's [MediaDetail.externalUrls] first, in server order — they
 *    are already brand-named by the server.
 * 2. A TMDB link constructed from [resolveTmdbId] (which consults the
 *    provider ids first, then scans the external urls).
 * 3. An IMDb link constructed from [resolveImdbId], same resolution order.
 *
 * The merge dedupes by [externalLinkHost], first occurrence wins, so a
 * server-sourced TMDB/IMDb entry always outranks the constructed twin —
 * including the case where the constructed id was itself scanned off that
 * very server url ([resolveTmdbId]'s fallback) and would otherwise appear
 * twice.
 *
 * [mediaType] is the TMDB path segment ("movie" / "tv") for the constructed
 * TMDB url. [tmdbLabel] / [imdbLabel] arrive pre-resolved by the caller —
 * a pure function cannot read string resources (the `bookFormatLabel`
 * convention in the header renderer).
 *
 * The whole [detail] is passed (rather than bare `externalUrls +
 * providerIds`) so the two existing pure resolvers apply unchanged,
 * fallback branches included.
 */
internal fun buildDetailExternalLinks(
    detail: MediaDetail,
    mediaType: String,
    tmdbLabel: String,
    imdbLabel: String,
): List<ExternalUrl> {
    val merged = buildList {
        addAll(detail.externalUrls)
        val tmdbId = resolveTmdbId(detail)
        if (tmdbId != null && tmdbId > 0) {
            add(
                ExternalUrl(
                    name = tmdbLabel,
                    url = "https://www.themoviedb.org/$mediaType/$tmdbId",
                ),
            )
        }
        resolveImdbId(detail)?.let { imdbId ->
            add(
                ExternalUrl(
                    name = imdbLabel,
                    url = "https://www.imdb.com/title/$imdbId",
                ),
            )
        }
    }
    // Dedupe by host, first occurrence wins (server entry > constructed twin).
    val seenHosts = mutableSetOf<String>()
    return merged.filter { link -> seenHosts.add(externalLinkHost(link.url) ?: link.url) }
}

/**
 * The pure admission decision behind the detail screen's external-link row: the
 * preference is on, the origin is not LOCAL (an offline detail would offer
 * chips describing nothing reachable), and the merged list is non-empty.
 * Null origin (snapshot not yet landed — the section never renders then, but
 * stay defensive) counts as remote. Pure → directly unit-testable (the
 * [preferLogoTitleEnabled] pattern).
 */
internal fun detailExternalLinksVisible(
    showExternalLinks: Boolean,
    origin: DetailOrigin?,
    links: List<ExternalUrl>,
): Boolean = showExternalLinks && origin?.isLocal != true && links.isNotEmpty()
