package com.raulshma.jellyplay.feature.details

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Bell
import com.composables.icons.tabler.outline.BellOff
import com.raulshma.jellyplay.core.designsystem.theme.LocalThemeVariant
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.detailCardBorder
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.ui.components.shortMonthDayYear
import com.raulshma.jellyplay.core.ui.feedback.rememberConfirmHaptic
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
 * On compact / phone screens ([isCompact] = true), the card expands to utilize
 * the full available width in a horizontal card layout mirroring [UpNextSection]
 * — 16:9 thumbnail on the left, metadata in the center, and the notify bell trailing.
 * On TV and wide screens ([isCompact] = false), it retains the fixed-width
 * horizontal rail geometry.
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
    isCompact: Boolean = false,
) {
    val focusState = rememberTvFocusState(focusedScale = if (isCompact) 1.02f else 1.03f)
    val confirmHaptic = rememberConfirmHaptic()

    val episodeContext = remember(episode.seasonNumber, episode.episodeNumber) {
        buildString {
            episode.seasonNumber?.let { append("S$it") }
            episode.episodeNumber?.let {
                if (isNotEmpty()) append(" · ")
                append("E$it")
            }
        }
    }

    val countdownDays = remember(episode.premiereDate) {
        airingCountdownDays(episode.premiereDate, Clock.System.todayIn(TimeZone.currentSystemDefault()))
    }

    if (isCompact) {
        val cardInteractionSource = remember { MutableInteractionSource() }
        val isCardPressed by cardInteractionSource.collectIsPressedAsState()
        val cardScale by animateFloatAsState(
            targetValue = if (isCardPressed) 0.98f else 1f,
            animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
            label = "upcomingCardScale",
        )

        val themeVariant = LocalThemeVariant.current
        val primaryColor = MaterialTheme.colorScheme.primary
        val secondaryColor = MaterialTheme.colorScheme.secondary
        val outlineColor = MaterialTheme.colorScheme.outline
        val borderModifier = remember(themeVariant, primaryColor, secondaryColor, outlineColor) {
            themeVariant.detailCardBorder(primaryColor, secondaryColor, outlineColor, includeAurora = false)
                ?.let { Modifier.border(it, ShapeCache.smooth16) }
                ?: Modifier
        }

        Row(
            modifier = modifier
                .fillMaxWidth()
                .then(borderModifier)
                .clip(ShapeCache.smooth16)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                .graphicsLayer { scaleX = cardScale; scaleY = cardScale }
                .then(focusState.focusModifier)
                .then(Modifier.tvFocusIndicator(focusState, ShapeCache.smooth16))
                .clickable(
                    interactionSource = cardInteractionSource,
                    indication = null,
                    onClick = {
                        confirmHaptic()
                        onClick()
                    },
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UpcomingThumbnail(
                episode = episode,
                imageUrl = imageUrl,
                shape = ShapeCache.smooth16,
                modifier = Modifier.size(width = 160.dp, height = 90.dp),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(12.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                if (episodeContext.isNotEmpty()) {
                    Text(
                        text = episodeContext,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                }
                Text(
                    text = episode.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                UpcomingCountdownText(
                    countdownDays = countdownDays,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            if (trailingContent != null) {
                Box(
                    modifier = Modifier.padding(end = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    trailingContent()
                }
            }
        }
    } else {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .width(UPCOMING_CARD_WIDTH)
                    .then(focusState.focusModifier)
                    .then(Modifier.tvFocusIndicator(focusState, ShapeCache.smooth12))
                    .clickable {
                        confirmHaptic()
                        onClick()
                    },
            ) {
                UpcomingThumbnail(
                    episode = episode,
                    imageUrl = imageUrl,
                    shape = ShapeCache.smooth12,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(UPCOMING_THUMBNAIL_ASPECT),
                )
                Spacer(Modifier.height(6.dp))
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
                UpcomingCountdownText(
                    countdownDays = countdownDays,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (trailingContent != null) {
                Spacer(Modifier.width(8.dp))
                trailingContent()
            }
        }
    }
}

/**
 * The card's 16:9 thumbnail — cropped episode still with the "Airs <date>"
 * badge pinned bottom-start. Both card layouts share it; only the size/shape
 * chrome differs per branch.
 */
@Composable
private fun UpcomingThumbnail(
    episode: MediaItem,
    imageUrl: String,
    shape: Shape,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(shape)
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
        UpcomingAirBadge(
            premiereDate = episode.premiereDate,
            modifier = Modifier.align(Alignment.BottomStart),
        )
    }
}

/** The "in N days / today" subtitle under the episode title; nothing when unparseable or past. */
@Composable
private fun UpcomingCountdownText(
    countdownDays: Long?,
    modifier: Modifier = Modifier,
) {
    countdownDays?.let { days ->
        Text(
            text = if (days == 0L) {
                stringResource(Res.string.detail_airs_countdown_today)
            } else {
                stringResource(Res.string.detail_airs_countdown_format, days)
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = modifier,
        )
    }
}

@Composable
private fun UpcomingAirBadge(
    premiereDate: String?,
    modifier: Modifier = Modifier,
) {
    upcomingAirBadge(premiereDate)?.let { badge ->
        val label = when (badge) {
            is MissingEpisodeBadge.Airs ->
                stringResource(Res.string.detail_airs_date_format, shortMonthDayYear(badge.date))
            // An upcoming episode is never "missing" — the badge
            // fold above returns null without a parseable date.
            MissingEpisodeBadge.Missing -> null
        }
        if (label != null) {
            Surface(
                modifier = modifier.padding(6.dp),
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
    val confirmHaptic = rememberConfirmHaptic()
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(ShapeCache.smooth8)
            .then(focusState.focusModifier)
            .tvFocusIndicator(focusState, ShapeCache.smooth8)
            .clickable {
                confirmHaptic()
                onClick()
            },
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
