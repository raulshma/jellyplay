package com.raulshma.jellyplay.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.WindowSizeClass
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.PlayerPlay
import com.composables.icons.tabler.outline.Users
import com.composables.icons.tabler.outline.X
import com.raulshma.jellyplay.core.data.repository.AdminRepository
import com.raulshma.jellyplay.core.concurrency.runCatchingRethrowingCancellation
import com.raulshma.jellyplay.core.data.syncplay.SyncPlayManager
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.SessionInfo
import com.raulshma.jellyplay.core.model.SyncPlayGroup
import com.raulshma.jellyplay.core.ui.components.focusIndicator
import com.raulshma.jellyplay.feature.home.generated.resources.Res
import com.raulshma.jellyplay.feature.home.generated.resources.home_presence_chip_label
import com.raulshma.jellyplay.feature.home.generated.resources.home_presence_dismiss_cd
import com.raulshma.jellyplay.feature.home.generated.resources.home_presence_join
import org.jetbrains.compose.resources.stringResource

/**
 * H19 — one watch-party/presence chip's render model: ANOTHER user's active
 * session that is currently playing something, with the SyncPlay group id
 * ([joinableGroupId]) when a visible group is playing the same item — the
 * chip then offers the "Join" primary action beside the default open-details
 * tap.
 */
@Immutable
data class HomePresenceChip(
    /** The server session id — also the per-chip dismiss memory's key. */
    val sessionId: String,
    val userId: String,
    val userName: String,
    /** The now-playing item's id — the chip tap's detail navigation target. */
    val itemId: String,
    /**
     * What the chip names as "watching": the episode's series name when the
     * session is inside a show (the show is what "Alex is watching"), the
     * item's own name otherwise.
     */
    val itemName: String,
    val mediaType: MediaType,
    /** The visible SyncPlay group playing [itemId], or null (no Join affordance). */
    val joinableGroupId: String? = null,
)

/**
 * The pure session-filter / projection math behind the presence chips, split
 * out so the unit suite pins it without any client doubles (the
 * [HomeStatsMath] precedent).
 */
internal object HomePresenceChipsMath {

    /** The plan's cap — at most 3 presence chips render on home. */
    const val MAX_CHIPS: Int = 3

    /**
     * The session filter: OTHER users' sessions (self-exclusion by user id)
     * that are actually playing something ([SessionInfo.nowPlayingItem]
     * non-null with a resolvable item id). Order is the server's — the cap
     * keeps the first [MAX_CHIPS].
     */
    fun otherUsersPlaying(sessions: List<SessionInfo>, currentUserId: String?): List<SessionInfo> =
        sessions.filter { session ->
            val playing = session.nowPlayingItem
            session.userId.isNotBlank() &&
                session.userId != currentUserId &&
                playing != null &&
                playing.id.isNotBlank()
        }.take(MAX_CHIPS)

    /**
     * "Alex is watching <the show>" for episodes — the series name reads
     * better than one episode title on a glanceable chip; every other item
     * kind is named by itself. Blank names fall through to the item name.
     */
    fun displayItemName(name: String, seriesName: String?): String =
        seriesName?.takeIf { it.isNotBlank() } ?: name

    /**
     * The now-playing item's wire type ("Movie", "Episode", …) mapped onto
     * [MediaType] for the chip tap's detail navigation (the same vocabulary
     * the network module's `parseItemKind` table uses; home keeps its own
     * copy because that mapper is module-internal). Unknown kinds degrade to
     * [MediaType.UNKNOWN] — the detail router's own pass-through.
     */
    fun mediaTypeFor(wireType: String?): MediaType = when (wireType) {
        "Movie" -> MediaType.MOVIE
        "Series" -> MediaType.SERIES
        "Season" -> MediaType.SEASON
        "Episode" -> MediaType.EPISODE
        "Audio" -> MediaType.AUDIO
        "MusicAlbum" -> MediaType.ALBUM
        "MusicArtist" -> MediaType.ARTIST
        "MusicVideo" -> MediaType.MUSIC_VIDEO
        "BoxSet" -> MediaType.COLLECTION
        "Book" -> MediaType.BOOK
        "Folder" -> MediaType.FOLDER
        else -> MediaType.UNKNOWN
    }

    /**
     * Projects a filtered session + its join-availability onto the render
     * model. Null only when the session lost its now-playing item between
     * the filter and the projection (a defensive re-check; the chip must
     * never render a tap target that isn't an item).
     */
    fun presenceChip(session: SessionInfo, joinableGroupId: String?): HomePresenceChip? {
        val nowPlaying = session.nowPlayingItem ?: return null
        if (nowPlaying.id.isBlank()) return null
        return HomePresenceChip(
            sessionId = session.id,
            userId = session.userId,
            userName = session.userName.ifBlank { session.deviceName },
            itemId = nowPlaying.id,
            itemName = displayItemName(nowPlaying.name, nowPlaying.seriesName),
            mediaType = mediaTypeFor(nowPlaying.type),
            joinableGroupId = joinableGroupId,
        )
    }
}

/**
 * The H19 presence-chips controller — the one place the chips' data policy
 * lives (the [HomeGenreRailCache] precedent: an internal, VM-scoped feature
 * class wired as a Koin factory; NOT a core/data controller — home keeps its
 * opt-in decoration surfaces feature-local). Owns:
 *
 *  - the PROBE-ONCE capability gate: `/Sessions` is an admin-surface endpoint
 *    in practice, so the FIRST [refresh] of a home session probes it — a
 *    failed probe (non-admin / offline server) flips the controller to
 *    unavailable and every later [refresh] short-circuits to empty WITHOUT
 *    touching the network again (hide-forever-per-session, the plan's
 *    degradation pattern). A user switch re-arms the probe: the identity
 *    swap underneath the admin client can change the answer, so the new
 *    user's session probes afresh (and the dismiss memory resets with it).
 *  - the session filter + cap + SyncPlay join availability
 *    ([HomePresenceChipsMath], one group-list read per refresh, only when a
 *    candidate chip exists).
 *  - the per-chip dismiss memory: an in-memory, session-scoped set —
 *    deliberately NOT persisted (the plan's inline-dismiss contract).
 *
 * Live updates: POLL on the home refresh cadence
 * (`HomeFreshness.REFRESH_INTERVAL_FOREGROUND_MS`, driven by the VM's
 * collector below this class's call site) rather than the websocket
 * `Sessions` push — the shared socket is connect-on-demand infrastructure
 * (SyncPlay joins, the admin dashboard, cast), NOT connected while home
 * shows, and home must not adopt socket-lifecycle side effects to power an
 * opt-in decoration. The poll rides the same `AdminRepository.getSessions`
 * read the probe used.
 *
 * Never throws: every network read is Result-folded, so a transient poll
 * failure just empties the chips for that emission (the row hides) without
 * flipping the capability verdict.
 */
internal class HomePresenceChipsController(
    private val adminRepository: AdminRepository,
    private val syncPlayManager: SyncPlayManager,
) {

    /** The user id the current probe verdict / dismiss memory belong to. */
    private var probedForUserId: String? = null

    /** True once the [currentUserId] session's first [refresh] probe answered. */
    private var probed = false

    /** The probe's verdict — false is hide-forever for this home session. */
    private var available = false

    /** The session-scoped dismiss memory (in-VM; never persisted). */
    private val dismissedSessionIds = mutableSetOf<String>()

    /** Test-visible probe verdict: did /Sessions answer for this home session. */
    fun isAvailableForSession(): Boolean = available

    /** Test-visible dismiss memory read (the pinned per-chip memory contract). */
    fun isDismissed(sessionId: String): Boolean = sessionId in dismissedSessionIds

    /** Per-chip dismiss — the session id leaves every later [refresh]. */
    fun dismiss(sessionId: String) {
        dismissedSessionIds.add(sessionId)
    }

    /**
     * The current chips for [currentUserId]: probe-once gated (see the class
     * KDoc), filtered, capped, joined with the visible SyncPlay group list.
     */
    suspend fun refresh(currentUserId: String?): List<HomePresenceChip> {
        // A user switch is a fresh home session for this controller's memory:
        // re-arm the probe (the admin capability can differ) and drop the
        // previous user's dismissals.
        if (probedForUserId != currentUserId) {
            probedForUserId = currentUserId
            probed = false
            dismissedSessionIds.clear()
        }
        // The hide-forever short-circuit — BEFORE any network read: a failed
        // probe means /Sessions is never asked again this home session.
        if (probed && !available) return emptyList()

        val sessionsResult = adminRepository.getSessions()
        if (!probed) {
            probed = true
            available = sessionsResult.isSuccess
            if (!available) return emptyList()
        }
        // A transient poll failure after a successful probe only hides the
        // chips for this emission — the verdict stays.
        val sessions = sessionsResult.getOrNull() ?: return emptyList()

        val candidates = HomePresenceChipsMath.otherUsersPlaying(sessions, currentUserId)
            .filter { it.id !in dismissedSessionIds }
        if (candidates.isEmpty()) return emptyList()

        // Join availability: ONE group-list read for the whole batch, only
        // when a candidate chip exists. A failed read (Cancellation excepted)
        // degrades to "no Join affordance" — it never throws, never hides
        // the chips.
        val groups = runCatchingRethrowingCancellation { syncPlayManager.visibleGroups() }
            .getOrDefault(emptyList())

        return candidates.mapNotNull { session ->
            val joinable = syncPlayManager.groupPlayingItem(groups, session.nowPlayingItem?.id.orEmpty())
            HomePresenceChipsMath.presenceChip(session, joinable?.groupId)
        }
    }

    /**
     * The Join primary action: the [SyncPlayManager.joinGroupPlayingItem]
     * seam (find the visible group playing the chip's item → join it). The
     * caller navigates to the player on success — the same
     * create-then-open-the-player shape the detail screen's watch party uses.
     */
    suspend fun join(itemId: String): Result<SyncPlayGroup> =
        syncPlayManager.joinGroupPlayingItem(itemId)
}

/**
 * Phone layout: not TV and a compact window — the H19 row's Column-vs-LazyRow
 * switch. Pure over its inputs so the layout test pins this predicate, not a
 * re-implementation of it.
 */
internal fun isPhoneLayout(isTv: Boolean, windowSizeClass: WindowSizeClass): Boolean =
    !isTv && windowSizeClass == WindowSizeClass.Compact

/**
 * The H19 presence-chip row — a LazyRow of [HomePresenceChipEntry]s (or a full-width
 * Column in phone layout), mounted under the top dock (the first HomeScreen-owned
 * slot of the content column, above the H17 stats card). Hidden by the caller whenever
 * the list is empty (prop off, offline, probe failure, no qualifying sessions).
 */
@Composable
internal fun HomePresenceChipRow(
    chips: List<HomePresenceChip>,
    onChipClick: (HomePresenceChip) -> Unit,
    onJoinClick: (HomePresenceChip) -> Unit,
    onDismiss: (HomePresenceChip) -> Unit,
    contentPad: Dp,
    modifier: Modifier = Modifier,
    isPhone: Boolean = isPhoneLayout(LocalTvMode.current, LocalAdaptiveInfo.current.windowSizeClass),
) {
    @Composable
    fun entry(chip: HomePresenceChip) {
        HomePresenceChipEntry(
            chip = chip,
            onChipClick = onChipClick,
            onJoinClick = onJoinClick,
            onDismiss = onDismiss,
            isPhone = isPhone,
        )
    }

    if (isPhone) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = contentPad),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            chips.forEach { chip -> entry(chip) }
        }
    } else {
        LazyRow(
            contentPadding = PaddingValues(horizontal = contentPad),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = modifier.fillMaxWidth(),
        ) {
            items(chips, key = { it.sessionId }) { chip -> entry(chip) }
        }
    }
}

/**
 * One "Alex is watching Dune" chip: the label (tap → item detail), the
 * Join primary action when a SyncPlay group covers the session, and the
 * per-chip dismiss. Visual language follows [HomeStatsCard] (the glass
 * surface tile) with the H18 rail's row shape.
 */
@Composable
private fun HomePresenceChipEntry(
    chip: HomePresenceChip,
    onChipClick: (HomePresenceChip) -> Unit,
    onJoinClick: (HomePresenceChip) -> Unit,
    onDismiss: (HomePresenceChip) -> Unit,
    isPhone: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = (if (isPhone) modifier.fillMaxWidth() else modifier.widthIn(max = 340.dp))
            .clip(ShapeCache.smooth20)
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f))
            .focusIndicator()
            .clickable(onClick = { onChipClick(chip) })
            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
    ) {
        Icon(
            imageVector = Tabler.Outline.Users,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = stringResource(Res.string.home_presence_chip_label, chip.userName, chip.itemName),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f, fill = isPhone)
                .padding(vertical = 4.dp),
        )
        if (chip.joinableGroupId != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .clip(ShapeCache.smooth12)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                    .focusIndicator()
                    .clickable(onClick = { onJoinClick(chip) })
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Icon(
                    imageVector = Tabler.Outline.PlayerPlay,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = stringResource(Res.string.home_presence_join),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        IconButton(
            onClick = { onDismiss(chip) },
            modifier = Modifier.size(28.dp),
        ) {
            Icon(
                imageVector = Tabler.Outline.X,
                contentDescription = stringResource(Res.string.home_presence_dismiss_cd),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}
