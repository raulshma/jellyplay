package com.raulshma.jellyplay.feature.details

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Building
import com.composables.icons.tabler.outline.CalendarEvent
import com.composables.icons.tabler.outline.Cash
import com.composables.icons.tabler.outline.InfoCircle
import com.composables.icons.tabler.outline.Language
import com.composables.icons.tabler.outline.PlayerPlay
import com.composables.icons.tabler.outline.Wallet
import com.composables.icons.tabler.outline.World
import com.raulshma.jellyplay.core.designsystem.theme.BrandColors
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.isLightColor
import com.raulshma.jellyplay.core.model.seerr.SeerrMovieDetails
import com.raulshma.jellyplay.core.model.seerr.SeerrRatings
import com.raulshma.jellyplay.core.model.seerr.SeerrReleaseDateRegion
import com.raulshma.jellyplay.core.model.seerr.SeerrReleaseDateType
import com.raulshma.jellyplay.core.model.seerr.SeerrRelatedVideo
import com.raulshma.jellyplay.core.model.seerr.SeerrTvDetails
import com.raulshma.jellyplay.core.model.seerr.SeerrWatchProvider
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.core.ui.tv.FocusRestoringItemRow
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.feature.details.generated.resources.Res
import com.raulshma.jellyplay.feature.details.generated.resources.detail_seerr_budget
import com.raulshma.jellyplay.feature.details.generated.resources.detail_seerr_country
import com.raulshma.jellyplay.feature.details.generated.resources.detail_seerr_currently_streaming_on
import com.raulshma.jellyplay.feature.details.generated.resources.detail_seerr_information
import com.raulshma.jellyplay.feature.details.generated.resources.detail_seerr_language
import com.raulshma.jellyplay.feature.details.generated.resources.detail_seerr_release_date
import com.raulshma.jellyplay.feature.details.generated.resources.detail_seerr_revenue
import com.raulshma.jellyplay.feature.details.generated.resources.detail_seerr_status
import com.raulshma.jellyplay.feature.details.generated.resources.detail_seerr_studios
import com.raulshma.jellyplay.feature.details.generated.resources.detail_seerr_unknown
import com.raulshma.jellyplay.feature.details.generated.resources.detail_seerr_video
import com.raulshma.jellyplay.feature.details.generated.resources.detail_section_videos
import org.jetbrains.compose.resources.stringResource

/**
 * The Seerr detail body's information sections: the related-videos row
 * ([VideosSection]), the ratings strip + condensed metadata line
 * ([RatingsRow]/[MediaInfoCondensed], hosted by the screen header), and the
 * media-information panel ([MediaInformationSection] with its label rows,
 * release-date markers and streaming-provider logos).
 *
 * Section-split sibling of `SeerrDetailScreen.kt` (the MediaDetailBody
 * precedent): bodies moved verbatim - [VideosSection], [MediaInfoCondensed]
 * and [MediaInformationSection] widened from `private` to `internal`
 * because their callers ([SeerrDetailBody] / [SeerrDetailContent]) stayed
 * in the screen file; the row-level helpers keep `private` (single in-file
 * consumers). No behaviour change. The video CARD body is the shared
 * [YouTubeVideoCard] (DetailSectionVocabulary) — collapsed with the media
 * family's identical copy; only the row scaffolding stays family-local.
 */
@Composable
internal fun VideosSection(
    videos: List<SeerrRelatedVideo>,
    onVideoClick: (SeerrRelatedVideo) -> Unit,
) {
    val uniqueVideos = remember(videos) {
        videos.distinctBy { it.key }.filter { !it.key.isNullOrBlank() }
    }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = stringResource(Res.string.detail_section_videos),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        FocusRestoringItemRow(
            items = uniqueVideos,
            key = { it.key!! },
            contentType = { "video" },
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 4.dp),
        ) { video ->
            YouTubeVideoCard(
                video = video,
                fallbackLabel = stringResource(Res.string.detail_seerr_video),
                onClick = { onVideoClick(video) },
            )
        }
    }
}

@Composable
private fun RatingsRow(ratings: SeerrRatings?) {
    if (ratings == null) return

    // Build a list of only valid (non-null) rating items
    val ratingItems = mutableListOf<@Composable () -> Unit>()

    // Rotten Tomatoes Critics
    ratings.rt?.criticsScore?.let { score ->
        ratingItems.add {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "🍅", modifier = Modifier.padding(end = 4.dp))
                Text(
                    text = "$score%",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }

    // Rotten Tomatoes Audience
    ratings.rt?.audienceScore?.let { score ->
        ratingItems.add {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "🍿", modifier = Modifier.padding(end = 4.dp))
                Text(
                    text = "$score%",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }

    // IMDb
    val imdbRating = ratings.imdb
    if (imdbRating != null) {
        val imdbScore = imdbRating.criticsScore ?: imdbRating.rating
        if (imdbScore != null) {
            ratingItems.add {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .background(BrandColors.imdb, ShapeCache.smooth4)
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "IMDb",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = formatRatingOneDecimal(imdbScore),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }

    // TMDb
    ratings.tmdb?.rating?.let { rating ->
        ratingItems.add {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The badge background is a fixed mint-green (#90CEA1), which is light in
                // every theme variant, so the onSurface token (light in dark theme) would be
                // low-contrast. Derive the text color from the badge's own luminance instead.
                val tmdbBadgeColor = BrandColors.tmdbBackground
                val tmdbBadgeText = remember(tmdbBadgeColor) {
                    if (isLightColor(tmdbBadgeColor)) Color.Black else Color.White
                }
                Box(
                    modifier = Modifier
                        .background(tmdbBadgeColor, ShapeCache.smooth4)
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "TMDB",
                        style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = tmdbBadgeText
                        )
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "${(rating * 10).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }

    if (ratingItems.isEmpty()) return

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
    ) {
        ratingItems.forEach { it() }
    }
}

@Composable
internal fun MediaInfoCondensed(
    movieDetail: SeerrMovieDetails?,
    tvDetail: SeerrTvDetails?,
    ratings: SeerrRatings?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        RatingsRow(ratings)

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            val releaseDate = movieDetail?.releaseDate ?: tvDetail?.firstAirDate
            releaseDate?.take(4)?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }

            val runtime = movieDetail?.runtime ?: tvDetail?.episodeRunTime?.firstOrNull()
            if (runtime != null && runtime > 0) {
                Surface(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                    shape = ShapeCache.smooth4
                ) {
                    Text(
                        text = "${runtime}m",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            val genres = movieDetail?.genres ?: tvDetail?.genres ?: emptyList()
            if (genres.isNotEmpty()) {
                Text(
                    text = genres.take(2).joinToString(", ") { it.name },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
internal fun MediaInformationSection(
    movie: SeerrMovieDetails?,
    tv: SeerrTvDetails?,
    streamingRegion: String = "US",
    discoverRegion: String = "US",
    seerrServerUrl: String = "",
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = stringResource(Res.string.detail_seerr_information),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Column(
            modifier = Modifier
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MediaInfoRow(stringResource(Res.string.detail_seerr_status), movie?.status ?: tv?.status ?: stringResource(Res.string.detail_seerr_unknown), Tabler.Outline.InfoCircle)

            val releaseDate = movie?.releaseDate ?: tv?.firstAirDate
            if (movie != null) {
                ReleaseDateRow(releaseDate, movie.releases, discoverRegion)
            } else {
                MediaInfoRow(stringResource(Res.string.detail_seerr_release_date), releaseDate ?: stringResource(Res.string.detail_seerr_unknown), Tabler.Outline.CalendarEvent)
            }

            if (movie != null) {
                movie.revenue?.takeIf { it > 0 }?.let {
                    MediaInfoRow(stringResource(Res.string.detail_seerr_revenue), formatUsCurrency(it), Tabler.Outline.Cash)
                }
                movie.budget?.takeIf { it > 0 }?.let {
                    MediaInfoRow(stringResource(Res.string.detail_seerr_budget), formatUsCurrency(it), Tabler.Outline.Wallet)
                }
            }

            val language = movie?.originalLanguage ?: tv?.originalLanguage
            if (language != null) {
                MediaInfoRow(stringResource(Res.string.detail_seerr_language), languageDisplayName(language) ?: language, Tabler.Outline.Language)
            }

            val productionCountries = movie?.productionCountries ?: emptyList()
            if (productionCountries.isNotEmpty()) {
                val countryText = productionCountries.joinToString(", ") { country ->
                    val flag = getFlagEmoji(country.iso31661)
                    if (flag != null) "$flag ${country.name}" else country.name
                }
                MediaInfoRow(stringResource(Res.string.detail_seerr_country), countryText, Tabler.Outline.World)
            }

            val studios = remember(movie, tv) {
                movie?.productionCompanies?.map { it.name } ?: tv?.networks?.map { it.name } ?: emptyList()
            }
            if (studios.isNotEmpty()) {
                MediaInfoRow(stringResource(Res.string.detail_seerr_studios), studios.joinToString(", "), Tabler.Outline.Building)
            }

            val watchProviders = movie?.watchProviders ?: tv?.watchProviders ?: emptyList()
            val regionProviders = watchProviders.find { it.iso31661 == streamingRegion }
            val streamingProviders = regionProviders?.streaming.orEmpty()
            if (streamingProviders.isNotEmpty()) {
                StreamingProvidersRow(streamingProviders, streamingRegion, seerrServerUrl)
            }
        }
    }
}

@Composable
private fun ReleaseDateRow(
    releaseDate: String?,
    releases: List<SeerrReleaseDateRegion>,
    discoverRegion: String,
) {
    val filteredReleases = remember(releases, discoverRegion) {
        releases
            .find { it.iso31661 == discoverRegion }
            ?.releaseDates
            .orEmpty()
            .filter { it.type in renderedReleaseTypes }
            .distinctBy { it.type }
            .sortedBy { it.type.value }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Tabler.Outline.CalendarEvent,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column {
            Text(
                text = stringResource(Res.string.detail_seerr_release_date),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = releaseDate ?: stringResource(Res.string.detail_seerr_unknown),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                    lineHeight = 20.sp
                )
                if (filteredReleases.isNotEmpty()) {
                    Spacer(Modifier.width(4.dp))
                    filteredReleases.forEach { release ->
                        ReleaseTypeIcon(release.type)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReleaseTypeIcon(type: SeerrReleaseDateType) {
    // Mapping table lives in SeerrDetailUtils (releaseTypePresentation);
    // only the Icon shell stays in composition.
    val presentation = releaseTypePresentation(type) ?: return
    Icon(
        imageVector = presentation.icon,
        contentDescription = stringResource(presentation.labelRes),
        modifier = Modifier.size(16.dp),
        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
    )
}

@Composable
private fun StreamingProvidersRow(
    providers: List<SeerrWatchProvider>,
    region: String,
    seerrServerUrl: String = "",
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Tabler.Outline.PlayerPlay,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column {
            Text(
                text = stringResource(Res.string.detail_seerr_currently_streaming_on),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(6.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                providers.forEach { provider ->
                    // TMDB-shape decoding lives in SeerrDetailUtils
                    // (seerrProviderLogoUrl); only the logo shell composes.
                    val logoUrl = seerrProviderLogoUrl(provider.logoPath, seerrServerUrl)
                    if (logoUrl != null) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(ShapeCache.smooth8)
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                        ) {
                            MediaImage(
                                url = logoUrl,
                                contentDescription = provider.name,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaInfoRow(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                lineHeight = 20.sp
            )
        }
    }
}
