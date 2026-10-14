package com.raulshma.jellyplay.feature.details

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Bell
import com.composables.icons.tabler.outline.BellOff
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.ui.components.shortMonthDayYear
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.feature.details.generated.resources.Res
import com.raulshma.jellyplay.feature.details.generated.resources.detail_airs_countdown_format
import com.raulshma.jellyplay.feature.details.generated.resources.detail_airs_countdown_today
import com.raulshma.jellyplay.feature.details.generated.resources.detail_airs_date_format
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.jetbrains.compose.resources.stringResource

/**
 * The D7 "Airing next" strip: one horizontal episode card per unaired
 * episode. The geometry mirrors the chapter-tile row (MediaDetailBodySections'
 * ChapterTile) — a 16:9 thumbnail over the text stack — with the virtual-
 * episode badge vocabulary ([MissingEpisodeBadge.Airs]) as the in-thumbnail
 * "Airs <date>" chip and a countdown subtitle under the title.
 *
 * The trailing slot is deliberate: each row carries a "Notify me" bell
 * (D12) composed to the card's right, so [UpcomingEpisodeCard] accepts an
 * optional [UpcomingEpisodeCard.trailingContent] and keeps its own width
 * fixed — the bell never squeezes the thumbnail.
 */
@Composable
internal fun UpcomingEpisodeCard(
    episode: MediaItem,
    imageUrl: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    val focusState = rememberTvFocusState(focusedScale = 1.03f)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .width(UPCOMING_CARD_WIDTH)
                .then(focusState.focusModifier)
                .then(Modifier.tvFocusIndicator(focusState, ShapeCache.smooth12))
                .clickable(onClick = onClick),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(UPCOMING_THUMBNAIL_ASPECT)
                    .clip(ShapeCache.smooth12)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                MediaImage(
                    url = imageUrl,
                    contentDescription = episode.name,
                    blurHash = episode.blurHashes.primary,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                upcomingAirBadge(episode.premiereDate)?.let { badge ->
                    val label = when (badge) {
                        is MissingEpisodeBadge.Airs ->
                            stringResource(Res.string.detail_airs_date_format, shortMonthDayYear(badge.date))
                        // An upcoming episode is never "missing" — the badge
                        // fold above returns null without a parseable date.
                        MissingEpisodeBadge.Missing -> null
                    }
                    if (label != null) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(6.dp),
                            color = Color.Black.copy(alpha = 0.55f),
                            contentColor = Color.White.copy(alpha = 0.9f),
                            shape = ShapeCache.smooth12,
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            val episodeContext = remember(episode.seasonNumber, episode.episodeNumber) {
                buildString {
                    episode.seasonNumber?.let { append("S$it") }
                    episode.episodeNumber?.let {
                        if (isNotEmpty()) append(" · ")
                        append("E$it")
                    }
                }
            }
            if (episodeContext.isNotEmpty()) {
                Text(
                    text = episodeContext,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = episode.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Countdown subtitle: "Today" / "in N d" — null (no line) without
            // a parseable future air date. `today` resolves once per card
            // composition; the strip's lifetime comfortably outlives any
            // midnight rollover concern the staleness would introduce.
            val today = remember(episode.premiereDate) {
                Clock.System.todayIn(TimeZone.currentSystemDefault())
            }
            airingCountdownDays(episode.premiereDate, today)?.let { days ->
                Text(
                    text = if (days == 0L) {
                        stringResource(Res.string.detail_airs_countdown_today)
                    } else {
                        stringResource(Res.string.detail_airs_countdown_format, days)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (trailingContent != null) {
            Spacer(Modifier.width(8.dp))
            trailingContent()
        }
    }
}

/** Fixed card width — the trailing (bell) slot composes beside it, never inside. */
private val UPCOMING_CARD_WIDTH = 240.dp

/** 16:9 episode thumbnail (the Up Next card's landscape shape). */
private const val UPCOMING_THUMBNAIL_ASPECT = 16f / 9f

/**
 * D12: the per-row bell's gate — the notification stack must exist (desktop:
 * none, the bell hides outright) and the series opt-in only makes sense on
 * SERIES detail (the strip's admission already guarantees it; the gate keeps
 * the invariant local to the bell). Pure — pinned by the gating-matrix test.
 */
internal fun showsSeriesNotifyBell(
    platformSupported: Boolean,
    mediaType: MediaType?,
): Boolean = platformSupported && mediaType == MediaType.SERIES

/**
 * D12: the per-row "Notify me" bell composed into [UpcomingEpisodeCard]'s
 * trailing slot. Toggles the same per-series config the ⋮ menu's entry
 * writes (every row of the strip belongs to the same series, so every bell
 * reflects one shared state). The D-pad-focusable trailing-button shape is
 * the NextUpExcludedScreen restore button's.
 */
@Composable
internal fun UpcomingEpisodeNotifyBell(
    enabled: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val focusState = rememberTvFocusState(focusedScale = 1.12f)
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(ShapeCache.smooth8)
            .then(focusState.focusModifier)
            .tvFocusIndicator(focusState, ShapeCache.smooth8)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (enabled) Tabler.Outline.Bell else Tabler.Outline.BellOff,
            contentDescription = contentDescription,
            tint = if (enabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(20.dp),
        )
    }
}
