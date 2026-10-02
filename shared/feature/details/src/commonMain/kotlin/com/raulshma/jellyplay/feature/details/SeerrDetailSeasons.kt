package com.raulshma.jellyplay.feature.details

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.composables.icons.tabler.outline.Calendar
import com.composables.icons.tabler.outline.ChevronDown
import com.composables.icons.tabler.outline.Clock
import com.composables.icons.tabler.outline.Movie
import com.composables.icons.tabler.outline.Pencil
import com.composables.icons.tabler.outline.Star
import com.composables.icons.tabler.outline.Users
import com.raulshma.jellyplay.core.designsystem.theme.RatingColors
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.seerr.SeerrEpisode
import com.raulshma.jellyplay.core.model.seerr.SeerrSeason
import com.raulshma.jellyplay.core.ui.components.JellyPlayLoadingIndicator
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.core.ui.tv.FocusRestoringItemRow
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.feature.details.generated.resources.Res
import com.raulshma.jellyplay.feature.details.generated.resources.detail_section_seasons
import com.raulshma.jellyplay.feature.details.generated.resources.detail_seerr_director_format
import com.raulshma.jellyplay.feature.details.generated.resources.detail_seerr_episodes_count
import com.raulshma.jellyplay.feature.details.generated.resources.detail_seerr_season_n
import com.raulshma.jellyplay.feature.details.generated.resources.detail_seerr_show_more
import org.jetbrains.compose.resources.stringResource

/**
 * The Seerr detail body's TV seasons cluster: the season card row
 * ([SeasonsSection]) and the episode list it expands to ([EpisodeRow]).
 *
 * Section-split sibling of `SeerrDetailScreen.kt` (the MediaDetailBody
 * precedent): bodies moved verbatim - only [SeasonsSection] widened from
 * `private` to `internal` because its caller ([SeerrDetailBody]) stayed in
 * the screen file. [EpisodeRow] keeps `private`: its only caller is the
 * section above it in this file. No behaviour change.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SeasonsSection(
    seasons: List<SeerrSeason>,
    selectedSeasonNumber: Int? = null,
    episodesBySeason: Map<Int, List<SeerrEpisode>> = emptyMap(),
    isLoadingEpisodes: Boolean = false,
    onSeasonClick: (Int) -> Unit = {},
    showPosterUrl: String? = null,
) {
    val isTv = LocalTvMode.current
    val sortedSeasons = remember(seasons) {
        seasons.sortedByDescending { it.seasonNumber }.distinctBy { it.seasonNumber }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = stringResource(Res.string.detail_section_seasons),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        FocusRestoringItemRow(
            items = sortedSeasons,
            key = { it.seasonNumber },
            contentType = { "season" },
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 4.dp),
        ) { season ->
                val isSelected = selectedSeasonNumber == season.seasonNumber
                val borderModifier = if (isSelected) {
                    Modifier.border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = ShapeCache.smooth8
                    )
                } else Modifier

                // Fall back to the show's main poster when this season has no
                // dedicated artwork — Overseerr returns a null posterPath for
                // some seasons (notably specials/season 0, or seasons TMDB has
                // no poster for). Without the fallback the card showed the
                // generic placeholder even though the show itself has artwork.
                val seasonCardUrl = remember(season.posterUrl, showPosterUrl) {
                    season.posterUrl ?: showPosterUrl
                }
                Column(
                    modifier = Modifier
                        .width(120.dp)
                        .then(borderModifier)
                        .clip(ShapeCache.smooth8)
                        .clickable { onSeasonClick(season.seasonNumber) }
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(2f / 3f),
                        shape = ShapeCache.smooth8,
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Box {
                            MediaImage(
                                url = seasonCardUrl ?: "",
                                contentDescription = season.name,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                                        )
                                )
                                Icon(
                                    imageVector = Tabler.Outline.ChevronDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .size(32.dp)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = season.name,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(Res.string.detail_seerr_episodes_count, season.episodeCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        val selectedSeason = selectedSeasonNumber
        if (selectedSeason != null) {
            AnimatedVisibility(
                visible = true,
                enter = expandVertically(
                    animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                    initialHeight = { 0 }
                ) + fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
            ) {
                val episodes = episodesBySeason[selectedSeason]
                if (isLoadingEpisodes && episodes == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        JellyPlayLoadingIndicator()
                    }
                } else if (episodes != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(ShapeCache.smooth12)
                            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = sortedSeasons.find { it.seasonNumber == selectedSeason }?.name ?: stringResource(Res.string.detail_seerr_season_n, selectedSeason),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        // Prefer the selected season's own poster as the
                        // episode-still fallback, then the show poster.
                        val selectedSeasonPoster = sortedSeasons
                            .find { it.seasonNumber == selectedSeason }?.posterUrl
                            ?: showPosterUrl
                        episodes.forEach { episode ->
                            EpisodeRow(episode = episode, fallbackImageUrl = selectedSeasonPoster)
                        }
                    }
                }
        }
    }
}

@Composable
private fun EpisodeRow(
    episode: SeerrEpisode,
    fallbackImageUrl: String? = null,
) {
    // TMDB episode stills are frequently missing (unaired episodes, or episodes
    // TMDB has no still for). Fall back to the season/show poster so the row
    // isn't a bare text entry when stillPath is null.
    val stillUrl = episode.stillUrl?.takeIf { it.isNotBlank() } ?: fallbackImageUrl
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = ShapeCache.smooth8,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (stillUrl != null) {
                val surfaceContainerLow = MaterialTheme.colorScheme.surfaceContainerLow
                val scrimBrush = remember(surfaceContainerLow) {
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Transparent,
                            surfaceContainerLow.copy(alpha = 0.9f),
                            surfaceContainerLow,
                        ),
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                ) {
                    MediaImage(
                        url = stillUrl,
                        contentDescription = episode.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(scrimBrush)
                    )
                    Text(
                        text = "${episode.episodeNumber}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 12.dp, bottom = 4.dp)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "${episode.episodeNumber}. ${episode.name}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                val metaItems = mutableListOf<@Composable () -> Unit>()
                episode.airDate?.takeIf { it.isNotBlank() }?.let { date ->
                    metaItems.add {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Tabler.Outline.Calendar,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = formatDate(date),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                episode.runtime?.takeIf { it > 0 }?.let { mins ->
                    metaItems.add {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Tabler.Outline.Clock,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = formatRuntime(mins),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                episode.voteAverage?.takeIf { it > 0f }?.let { rating ->
                    metaItems.add {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Tabler.Outline.Star,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = RatingColors.star
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = formatRatingOneDecimal(rating),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            episode.voteCount.takeIf { it > 0 }?.let { count ->
                                Text(
                                    text = " ($count)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                if (metaItems.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        metaItems.forEach { it() }
                    }
                }

                val directors = remember(episode.crew) {
                    episode.crew.filter { it.job.equals("Director", ignoreCase = true) }
                }
                if (directors.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Tabler.Outline.Movie,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = stringResource(Res.string.detail_seerr_director_format, directors.joinToString(", ") { it.name }),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                val writers = remember(episode.crew) {
                    episode.crew.filter {
                        it.department.equals("Writing", ignoreCase = true)
                    }
                }
                if (writers.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Tabler.Outline.Pencil,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = writers.joinToString(", ") { it.name },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                if (episode.guestStars.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Tabler.Outline.Users,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = episode.guestStars.joinToString(", ") {
                                it.character?.takeIf { c -> c.isNotBlank() }
                                    ?.let { c -> "${it.name} ($c)" }
                                    ?: it.name
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                episode.overview?.takeIf { it.isNotBlank() }?.let { overview ->
                    Spacer(Modifier.height(2.dp))
                    var expanded by remember { mutableStateOf(false) }
                    Text(
                        text = overview,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        lineHeight = 20.sp,
                        maxLines = if (expanded) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { expanded = !expanded }
                    )
                    if (!expanded && overview.length > 200) {
                        Text(
                            text = stringResource(Res.string.detail_seerr_show_more),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { expanded = true }
                        )
                    }
                }
            }
        }
    }
}
