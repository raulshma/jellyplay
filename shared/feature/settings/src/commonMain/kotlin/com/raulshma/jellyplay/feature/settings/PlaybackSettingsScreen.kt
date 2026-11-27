package com.raulshma.jellyplay.feature.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulshma.jellyplay.core.model.PauseScreensaverMode
import com.raulshma.jellyplay.core.model.PlaybackPreferences
import com.raulshma.jellyplay.core.model.VideoPerformanceProfile
import com.raulshma.jellyplay.core.ui.components.SettingListItem
import com.raulshma.jellyplay.core.ui.components.SettingsItemList
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.PlayerPlay
import org.jetbrains.compose.resources.stringResource
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_playback_sections
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_playback_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_reset_playback_cd
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_reset_playback_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_reset_playback_message
import org.koin.compose.viewmodel.koinViewModel

/**
 * The playback domain's admission flags — the declared row admissions both
 * the SettingsItemList totals and the emission `if`s on every playback leaf
 * read. Pure (and internal) so the contract test can pin every WhenOn parent
 * the playback search groups declare against this wiring: the
 * AUDIO_PASSTHROUGH pair dropping out of it is exactly how the five
 * per-codec rows went permanently invisible.
 */
internal fun playbackRowAdmissionFlags(
    isTv: Boolean,
    showAdvanced: Boolean,
    preferences: PlaybackPreferences,
): RowAdmissionFlags = RowAdmissionFlags(
    isTv = isTv,
    showAdvanced = showAdvanced,
    parentsOn = rowParentsOn(
        PlaybackRows.DialogueBoost.id to preferences.dialogueBoostEnabled,
        PlaybackRows.VideoAutoplayNext.id to preferences.videoAutoplayNext,
        PlaybackRows.AudioPassthrough.id to preferences.audioPassthrough,
        PlaybackRows.SpatialAudio.id to preferences.spatialAudioEnabled,
        // The pause-screensaver delay rides the mode row: any non-OFF
        // screensaver counts as the parent being on.
        PlaybackRows.PauseScreensaver.id to (preferences.pauseScreensaverMode != PauseScreensaverMode.OFF),
        // The Re-detect action row rides the profile row's AUTO state: an
        // explicit LOW/MEDIUM/HIGH pins the tier, so re-detecting changes
        // nothing and the row hides (any non-AUTO counts as the parent off).
        PlaybackRows.VideoPerformanceProfile.id to (preferences.videoPerformanceProfile == VideoPerformanceProfile.AUTO),
    ),
)

/**
 * The playback hub's ONE ordered slot declaration (ADR-0009 amendment): the
 * single entrance group, no advanced gating, the default admission-derived
 * content count (the entrance rows declare Always — the hub's derived hint
 * is flat zero). The screen attaches its hand-written body via
 * [withContent]; the contract test pins the slot/group parity against this
 * same declaration.
 */
internal val PlaybackHubSlots: List<PreferenceScreenGroup> = listOf(
    SettingsScreenGroups.playbackHub.asScreenGroup(),
)

/**
 * The playback hub's ONE entrance group body (ADR-0017): the eight composite
 * entrance rows in [PlaybackRows]'s declared order, each deep-linking the
 * leaf screen it opens (the drill-in-row shape — the route lives on the row).
 * Each subtitle is the leaf's live current-value summary — the per-domain
 * `*SummaryParts` folds over [PlaybackPreferences] (ADR-0017 decision 3,
 * pinned by SettingsSummariesTest), resolved part-by-part at composition.
 * The group stays open — the entrances are the screen's whole body — and a
 * deep-linked entrance id highlights its row (the catalog routes entrance ids
 * to their leaf, so this only fires for a hand-built link).
 */
@Composable
private fun PlaybackHubSection(
    preferences: PlaybackPreferences,
    highlightSettingId: String?,
    navActions: SettingsNavActions,
) {
    SettingsGroup(
        icon = Tabler.Outline.PlayerPlay,
        title = stringResource(Res.string.settings_playback_sections),
        modifier = Modifier.padding(vertical = 8.dp),
        initiallyExpanded = true,
    ) {
        SettingsItemList(total = SettingsScreenGroups.playbackHub.items.size) {
            // (row, summary parts) in the hub's declared order — the row's
            // declared route IS the destination (never reconstructed here, the
            // appearance/home hubs' shape), so hub links and search hits stay
            // on the same destination through one declaration. Each row's live
            // summary is selected by row id (the folds read the ONE
            // [PlaybackPreferences] slice all eight leaves render from), so a
            // row added to the declaration surfaces here (or the selection
            // fails loudly) instead of dropping off a hand-maintained parallel
            // list.
            val summaries = mapOf(
                PlaybackRows.BasicsEntrance.id to playbackBasicsSummaryParts(preferences),
                PlaybackRows.ControlsEntrance.id to playbackControlsSummaryParts(preferences),
                PlaybackRows.PlayerChromeEntrance.id to playbackPlayerChromeSummaryParts(preferences),
                PlaybackRows.VideoQualityEntrance.id to playbackVideoQualitySummaryParts(preferences),
                PlaybackRows.PlayerEnginesEntrance.id to playbackEnginesSummaryParts(preferences),
                PlaybackRows.SegmentsEntrance.id to playbackSegmentsSummaryParts(preferences),
                PlaybackRows.SyncPlayCastingEntrance.id to playbackSyncPlayCastingSummaryParts(preferences),
                PlaybackRows.LiveTvDvrEntrance.id to playbackLiveTvDvrSummaryParts(preferences),
            )
            PlaybackHubRows.forEach { row ->
                SettingListItem(
                    icon = rowIcon(row),
                    title = rowTitle(row),
                    subtitle = joinSummaryTokens(summaries.getValue(row.id).map { it.resolveAtComposition() }),
                    highlighted = highlightSettingId == row.id,
                    onClick = { navActions.onNavigate(requireNotNull(row.route) { "hub entrance ${row.id} declares no route" }) },
                )
            }
        }
    }
}

/**
 * The playback settings HUB (ADR-0017's domain hub split): one group of the
 * eight composite entrance rows ([PlaybackHubSection]), each navigating a
 * leaf screen. The domain's settings rows live on the leaves
 * ([Route.PlaybackBasics] et al. — their spec route kinds re-pointed, so
 * search/deep-link/highlight resolution lands on the owning leaf); this
 * screen keeps the domain root's faces: the settings root's Playback
 * entrance, the search pane's quick category, and the domain-wide reset
 * action ([PlaybackSettingsViewModel.resetPlaybackSettings] — the reset
 * spans both categories the domain owns, exactly as on the former combined
 * screen). The hub itself declares no advanced rows, so it carries no
 * advanced tier; a stale deep-link into a moved row id resolves to -1 here
 * and lands on the owning leaf through the catalog instead.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlaybackSettingsScreen(
    onBack: () -> Unit,
    highlightSettingId: String? = null,
    /** The entrances navigate through the shared nav facade. */
    navActions: SettingsNavActions,
    viewModel: PlaybackSettingsViewModel = koinViewModel(),
) {
    // The entrance summaries read the same slice the leaves render from.
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()

    val hubGroups = PlaybackHubSlots.withContent(
        bodies = listOf(
            { _ ->
                PlaybackHubSection(
                    preferences = preferences,
                    highlightSettingId = highlightSettingId,
                    navActions = navActions,
                )
            },
        ),
    )

    PreferenceScreenScaffold(
        title = stringResource(Res.string.settings_playback_title),
        onBack = onBack,
        focusTag = "playback_init",
        highlightSettingId = highlightSettingId,
        groups = hubGroups,
        reset = PreferenceResetAction(
            iconContentDescription = stringResource(Res.string.settings_reset_playback_cd),
            dialogTitle = stringResource(Res.string.settings_reset_playback_title),
            dialogMessage = stringResource(Res.string.settings_reset_playback_message),
            onReset = { viewModel.resetPlaybackSettings() },
        ),
    )
}
