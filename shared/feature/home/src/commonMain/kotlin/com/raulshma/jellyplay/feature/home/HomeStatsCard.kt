package com.raulshma.jellyplay.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.ChartBar
import com.composables.icons.tabler.outline.Flame
import com.raulshma.jellyplay.core.data.repository.AdminStatisticsRepository
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.PlaybackReportingStatus
import com.raulshma.jellyplay.core.network.api.MediaInfoApiClient
import com.raulshma.jellyplay.core.ui.components.focusIndicator
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import com.raulshma.jellyplay.feature.home.generated.resources.Res
import com.raulshma.jellyplay.feature.home.generated.resources.home_stats_card_counts
import com.raulshma.jellyplay.feature.home.generated.resources.home_stats_card_streak
import com.raulshma.jellyplay.feature.home.generated.resources.home_stats_card_title
import com.raulshma.jellyplay.feature.home.generated.resources.home_stats_card_watch_approx
import com.raulshma.jellyplay.feature.home.generated.resources.home_stats_card_watch_exact
import org.jetbrains.compose.resources.stringResource

/**
 * "Your library week" — the personal stats card's whole render model.
 * The base shape is the non-admin-safe triplet (played / unplayed / approximate
 * watch minutes from the user-scoped item queries); the admin+plugin upgrade
 * fills [exactWatchHours] / [currentStreakDays] from the Playback Reporting
 * plugin's per-user aggregates when the user is an admin AND the plugin
 * reported AVAILABLE (see [HomeStatsCardLoader]).
 */
@Immutable
data class HomeStatsCardData(
    val playedCount: Int,
    val unplayedCount: Int,
    /**
     * Σ runtime of the user's played items over a CAPPED query — an
     * approximation, not the plugin's ledger (see [HomeStatsMath]).
     */
    val approximateWatchMinutes: Long,
    /** Plugin-reported total watch hours; null when the upgrade didn't apply. */
    val exactWatchHours: Double? = null,
    /** Plugin-reported current viewing streak (days); null without the upgrade. */
    val currentStreakDays: Int? = null,
) {
    /** True when the admin+plugin upgrade populated the real-hours fields. */
    val adminEnhanced: Boolean get() = exactWatchHours != null
}

/**
 * The pure aggregation math behind the stats card, split out so the unit
 * suite pins it without any client doubles.
 */
internal object HomeStatsMath {

    /** 10,000,000 ticks per second × 60 — the runtime-ticks → minutes divisor. */
    const val TICKS_PER_MINUTE: Long = 600_000_000L

    /**
     * Σ runtime over the fetched played items, floored to whole minutes. Null
     * runtimes (a fetch that didn't project Runtime) contribute zero — the
     * figure is documented as approximate, never a lie the card can't back.
     */
    fun approximateWatchMinutes(runtimesTicks: List<Long?>): Long =
        runtimesTicks.fold(0L) { acc, ticks -> acc + (ticks ?: 0L) } / TICKS_PER_MINUTE

    /** Plugin watch-time seconds → hours (the card renders whole hours). */
    fun watchHours(totalWatchTimeSec: Long): Double = totalWatchTimeSec / 3600.0

    /**
     * The admin+plugin upgrade gate: the richer stats exist only for admins
     * AND only when the Playback Reporting plugin answered AVAILABLE (the
     * [com.raulshma.jellyplay.core.data.session.PlaybackReportingStatusStore]
     * semantics — a failed probe is a definitive "no", UNKNOWN is "ask again
     * after a refresh").
     */
    fun statsUpgradeAllowed(isAdmin: Boolean, pluginStatus: PlaybackReportingStatus): Boolean =
        isAdmin && pluginStatus == PlaybackReportingStatus.AVAILABLE
}

/**
 * The stats-card loader — the one place the card's three primitives are
 * fetched and folded. Non-admin-safe by construction: the played/unplayed
 * counters and the capped played-items query are plain user-scoped reads;
 * the admin+plugin upgrade (real hours + streak from
 * [AdminStatisticsRepository]) runs only behind
 * [HomeStatsMath.statsUpgradeAllowed] and degrades silently to the base card
 * on any failure.
 *
 * Total (never throws): every network read is Result-based and folded with
 * getOrNull/getOrDefault, so the VM can treat a null return as "hide the
 * card" with no error surface (failure → hidden silently).
 */
internal class HomeStatsCardLoader(
    private val mediaInfoApiClient: MediaInfoApiClient,
    private val adminStatisticsRepository: AdminStatisticsRepository,
) {

    /**
     * Loads the card for [userId]; null when a core count fails (the card
     * hides). [isAdmin] only widens the fetch — never the base triplet.
     *
     * The three base reads are independent — fan them out so the card
     * waits on the slowest leg, not their serial sum. Everything stays
     * Result-folded, so a failed leg does not abort its siblings mid-flight
     * either: the early return below cancels the remaining legs through the
     * [coroutineScope] unwind.
     */
    suspend fun load(userId: String, isAdmin: Boolean): HomeStatsCardData? = coroutineScope {
        val playedDeferred = async { mediaInfoApiClient.getUserPlayedItemCount(userId) }
        val unplayedDeferred = async { mediaInfoApiClient.getUserUnplayedItemCount(userId) }
        val runtimeDeferred = async {
            mediaInfoApiClient.getItemsWithUserData(
                userId = userId,
                includeItemTypes = WATCHED_RUNTIME_ITEM_TYPES,
                isPlayed = true,
                // The WatchHistoryRepository usage shape: newest-played first, so
                // the cap keeps the RECENT window when the library exceeds it.
                sortBy = "DatePlayed",
                sortOrder = "Descending",
                startIndex = 0,
                limit = RUNTIME_SAMPLE_CAP,
            ).map { (_, items) -> items.map { it.runTimeTicks } }.getOrDefault(emptyList())
        }

        val played = playedDeferred.await().getOrNull() ?: return@coroutineScope null
        val unplayed = unplayedDeferred.await().getOrNull() ?: return@coroutineScope null
        val runtimeTicks = runtimeDeferred.await()

        var exactWatchHours: Double? = null
        var currentStreakDays: Int? = null
        if (isAdmin) {
            val pluginStatus = adminStatisticsRepository.getPlaybackReportingStatus().first()
            val allowed = if (pluginStatus == PlaybackReportingStatus.UNKNOWN) {
                // Probe-once: UNKNOWN means nobody checked this session; the
                // refresh publishes the definitive answer (AVAILABLE or the
                // failed-probe UNAVAILABLE).
                adminStatisticsRepository.refreshPlaybackReportingStatus()
                HomeStatsMath.statsUpgradeAllowed(
                    isAdmin = true,
                    pluginStatus = adminStatisticsRepository.getPlaybackReportingStatus().first(),
                )
            } else {
                HomeStatsMath.statsUpgradeAllowed(isAdmin = true, pluginStatus = pluginStatus)
            }
            if (allowed) {
                adminStatisticsRepository.getUserDetailStatistics(userId).onSuccess { page ->
                    exactWatchHours = HomeStatsMath.watchHours(page.statistics.totalWatchTimeSec)
                    currentStreakDays = page.viewingStreak.currentStreak
                }
            }
        }

        return@coroutineScope HomeStatsCardData(
            playedCount = played,
            unplayedCount = unplayed,
            approximateWatchMinutes = HomeStatsMath.approximateWatchMinutes(runtimeTicks),
            exactWatchHours = exactWatchHours,
            currentStreakDays = currentStreakDays,
        )
    }

    companion object {
        /**
         * The played-items query cap (~200). Keeps the runtime
         * sample to one bounded page even for heavy libraries.
         */
        const val RUNTIME_SAMPLE_CAP: Int = 200

        /**
         * The item kinds whose runtime sums into "watch time". Video only —
         * books carry no meaningful runtime and per-song runtimes would
         * double-count music listening as watching.
         */
        val WATCHED_RUNTIME_ITEM_TYPES: List<String> = listOf("Movie", "Episode", "MusicVideo")
    }
}

/**
 * The compact stats card. [onClick] is null when the tap affordance is
 * not permitted (non-admin — the user-statistics detail is an admin area),
 * rendering the card as a plain non-clickable tile.
 */
@Composable
internal fun HomeStatsCard(
    data: HomeStatsCardData,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val watchedHours = remember(data.approximateWatchMinutes, data.exactWatchHours) {
        data.exactWatchHours?.let { HomeStatsMath.watchHoursLabel(it) }
            ?: (data.approximateWatchMinutes / 60).toString()
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(ShapeCache.smooth20)
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f))
            .then(
                if (onClick != null) {
                    Modifier
                        .focusIndicator()
                        .clickable(onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(ShapeCache.smooth12)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Tabler.Outline.ChartBar,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.home_stats_card_title),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(Res.string.home_stats_card_counts, data.playedCount, data.unplayedCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(
                        if (data.adminEnhanced) Res.string.home_stats_card_watch_exact
                        else Res.string.home_stats_card_watch_approx,
                        watchedHours,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (data.currentStreakDays != null && data.currentStreakDays > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Tabler.Outline.Flame,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(Res.string.home_stats_card_streak, data.currentStreakDays),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

/** Whole-hours label for the plugin-exact figure (one decimal when it matters). */
private fun HomeStatsMath.watchHoursLabel(hours: Double): String =
    if (hours >= 10 || hours % 1.0 == 0.0) {
        hours.toInt().toString()
    } else {
        // One decimal — String.format's locale handling is intentionally not
        // used: the label rides the localized "%1$s h watched" pattern, and a
        // plain dot keeps the number formatter out of shared code.
        val rounded = (hours * 10).toInt() / 10.0
        rounded.toString()
    }
