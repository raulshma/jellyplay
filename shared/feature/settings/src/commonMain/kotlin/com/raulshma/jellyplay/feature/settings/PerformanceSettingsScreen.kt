package com.raulshma.jellyplay.feature.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Database
import com.composables.icons.tabler.outline.Gauge
import com.composables.icons.tabler.outline.InfoCircle
import com.raulshma.jellyplay.core.model.ImageDecodeCap
import com.raulshma.jellyplay.core.model.PerformanceLevel
import com.raulshma.jellyplay.core.model.PerformanceOverride
import com.raulshma.jellyplay.core.ui.components.SettingListItem
import com.raulshma.jellyplay.core.ui.components.SettingToggleItem
import com.raulshma.jellyplay.core.ui.components.SettingsItemList
import com.raulshma.jellyplay.core.ui.model.localizedDisplayName
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_perf_auto_effective
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_perf_buffer_auto
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_perf_crossref_back_buffer_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_perf_crossref_back_buffer_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_perf_crossref_profile_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_perf_crossref_profile_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_perf_decode_1080
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_perf_decode_256
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_perf_decode_512
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_perf_decode_720
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_perf_decode_auto
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_perf_decode_unlimited
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_perf_group_ambient
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_perf_group_buffers
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_perf_group_images
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_perf_group_motion
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_perf_image_cache_auto
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_perf_level_balanced_desc
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_perf_level_performance_desc
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_perf_level_standard_desc
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_perf_override_auto
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_perf_override_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_perf_override_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_performance

/**
 * The performance hub's ONE ordered group declaration — the derivation-only
 * shape (group ids/order, whole-group advanced flags) that BOTH the screen
 * and the catalog contract test's section-7 pins consume. The level group
 * composes always (its row declares [RowAdmission.Always]); the four expert
 * groups only compose behind the advanced toggle (their rows keep the spec
 * advanced base). Per-group row emission stays hand-written: the screen maps
 * each slot to its body inline in [performanceScreenGroups] (ADR-0009).
 */
internal fun PerformanceSettingsScreenSlots(): List<PreferenceScreenGroup> = listOf(
    SettingsScreenGroups.performanceLevel.asScreenGroup(),
    SettingsScreenGroups.performanceMotion.asScreenGroup(advancedOnly = true),
    SettingsScreenGroups.performanceImages.asScreenGroup(advancedOnly = true),
    SettingsScreenGroups.performanceAmbient.asScreenGroup(advancedOnly = true),
    SettingsScreenGroups.playbackBuffers.asScreenGroup(advancedOnly = true),
)

/**
 * The hub's group bodies, mapped onto the ONE ordered declaration
 * ([PerformanceSettingsScreenSlots], in LazyColumn order — the shell derives
 * the deep-link scroll resolution, the whole-group advanced gating and the
 * hidden-settings hint count from it) with each slot's hand-written body
 * attached inline — the bodies close over the hub state (view model, flags,
 * highlight id, nav actions) and the shell only reads the derived shape.
 */
@Composable
private fun performanceScreenGroups(
    state: PerformanceHubState,
    rowFlags: RowAdmissionFlags,
    highlightSettingId: String?,
    viewModel: PerformanceSettingsViewModel,
    navActions: SettingsNavActions,
): List<PreferenceScreenGroup> {
    return PerformanceSettingsScreenSlots().withContent(
        bodies = listOf(
            { activePicker ->
                PerformanceLevelGroupBody(
                    state = state,
                    highlightSettingId = highlightSettingId,
                    viewModel = viewModel,
                    navActions = navActions,
                    activePicker = activePicker,
                )
            },
            { activePicker ->
                PerformanceOverrideGroupBody(
                    group = SettingsScreenGroups.performanceMotion,
                    icon = PerformanceRows.PerfAnimations.icon,
                    triples = listOf(
                        PerformanceOverrideTriple(
                            row = PerformanceRows.PerfAnimations,
                            override = state.appearance.perfAnimations,
                            displayValue = !state.resolved.suppressAnimations,
                            onSelect = { viewModel.edit { scope -> scope.appearance.setPerfAnimations(it) } },
                        ),
                        PerformanceOverrideTriple(
                            row = PerformanceRows.PerfSharedElements,
                            override = state.appearance.perfSharedElements,
                            displayValue = state.resolved.sharedElements,
                            onSelect = { viewModel.edit { scope -> scope.appearance.setPerfSharedElements(it) } },
                        ),
                        PerformanceOverrideTriple(
                            row = PerformanceRows.PerfNavTransitions,
                            // The stored knob is the SUPPRESSION
                            // (navTransitionsInstant); the row's user-facing
                            // boolean is "animated transitions" — flip it.
                            override = state.appearance.perfNavTransitions,
                            displayValue = !state.resolved.navTransitionsInstant,
                            onSelect = { viewModel.edit { scope -> scope.appearance.setPerfNavTransitions(it) } },
                        ),
                    ),
                    state = state,
                    highlightSettingId = highlightSettingId,
                    activePicker = activePicker,
                )
            },
            { activePicker ->
                PerformanceImagesGroupBody(
                    state = state,
                    rowFlags = rowFlags,
                    highlightSettingId = highlightSettingId,
                    viewModel = viewModel,
                    activePicker = activePicker,
                )
            },
            { activePicker ->
                PerformanceOverrideGroupBody(
                    group = SettingsScreenGroups.performanceAmbient,
                    icon = PerformanceRows.PerfAmbientHeroVideo.icon,
                    triples = listOf(
                        PerformanceOverrideTriple(
                            row = PerformanceRows.PerfAmbientHeroVideo,
                            override = state.appearance.perfAmbientHeroVideo,
                            displayValue = state.resolved.ambientHeroVideo,
                            onSelect = { viewModel.edit { scope -> scope.appearance.setPerfAmbientHeroVideo(it) } },
                        ),
                        PerformanceOverrideTriple(
                            row = PerformanceRows.PerfBackdropBlur,
                            override = state.appearance.perfBackdropBlur,
                            displayValue = state.resolved.backdropBlur,
                            onSelect = { viewModel.edit { scope -> scope.appearance.setPerfBackdropBlur(it) } },
                        ),
                        PerformanceOverrideTriple(
                            row = PerformanceRows.PerfScreensaverMotion,
                            override = state.appearance.perfScreensaverMotion,
                            displayValue = state.resolved.screensaverMotion,
                            onSelect = { viewModel.edit { scope -> scope.appearance.setPerfScreensaverMotion(it) } },
                        ),
                    ),
                    state = state,
                    highlightSettingId = highlightSettingId,
                    activePicker = activePicker,
                )
            },
            { activePicker ->
                PlaybackBufferGroupBody(
                    state = state,
                    highlightSettingId = highlightSettingId,
                    viewModel = viewModel,
                    navActions = navActions,
                    activePicker = activePicker,
                )
            },
        ),
    )
}

/** One tri-state row's (row, override, folded display value, setter) bundle. */
private data class PerformanceOverrideTriple(
    val row: SettingsRow,
    val override: PerformanceOverride,
    /**
     * The row's user-facing boolean (the ON/OFF the title speaks) — callers
     * flip the resolved value where the stored knob is a suppression.
     */
    val displayValue: Boolean,
    val onSelect: (PerformanceOverride) -> Unit,
)

/** The level faces — the picker's short labels, one per [PerformanceLevel]. */
@Composable
internal fun performanceLevelLabel(level: PerformanceLevel): String = level.localizedDisplayName()

/** The level faces — the picker's per-level description lines. */
@Composable
private fun performanceLevelDescription(level: PerformanceLevel): String = when (level) {
    PerformanceLevel.STANDARD -> stringResource(Res.string.settings_perf_level_standard_desc)
    PerformanceLevel.BALANCED -> stringResource(Res.string.settings_perf_level_balanced_desc)
    PerformanceLevel.PERFORMANCE -> stringResource(Res.string.settings_perf_level_performance_desc)
}

/** The tri-state override picker's faces. */
@Composable
private fun performanceOverrideLabel(override: PerformanceOverride): String = when (override) {
    PerformanceOverride.AUTO -> stringResource(Res.string.settings_perf_override_auto)
    PerformanceOverride.ON -> stringResource(Res.string.settings_perf_override_on)
    PerformanceOverride.OFF -> stringResource(Res.string.settings_perf_override_off)
}

@Composable
private fun performanceOnOffLabel(on: Boolean): String =
    if (on) stringResource(Res.string.settings_perf_override_on) else stringResource(Res.string.settings_perf_override_off)

@Composable
private fun performanceDecodeCapLabel(cap: ImageDecodeCap): String = when (cap) {
    ImageDecodeCap.AUTO -> stringResource(Res.string.settings_perf_decode_auto)
    ImageDecodeCap.UNLIMITED -> stringResource(Res.string.settings_perf_decode_unlimited)
    ImageDecodeCap.P1080 -> stringResource(Res.string.settings_perf_decode_1080)
    ImageDecodeCap.P720 -> stringResource(Res.string.settings_perf_decode_720)
    ImageDecodeCap.P512 -> stringResource(Res.string.settings_perf_decode_512)
    ImageDecodeCap.P256 -> stringResource(Res.string.settings_perf_decode_256)
}

/**
 * One tri-state override row's emission: a picker row whose subtitle folds
 * the AUTO state through the resolved posture ("Auto (Balanced → on)") and
 * whose picker lists Auto/On/Off with the same folded annotation on the Auto
 * face.
 */
@Composable
private fun PerformanceOverrideRow(
    triple: PerformanceOverrideTriple,
    level: PerformanceLevel,
    highlightSettingId: String?,
    activePicker: MutableState<PickerState<*>?>,
) {
    val autoAnnotation = stringResource(
        Res.string.settings_perf_auto_effective,
        performanceLevelLabel(level),
        performanceOnOffLabel(triple.displayValue),
    )
    val subtitle = when (triple.override) {
        PerformanceOverride.AUTO -> autoAnnotation
        PerformanceOverride.ON -> performanceOnOffLabel(true)
        PerformanceOverride.OFF -> performanceOnOffLabel(false)
    }
    // The picker's faces resolve outside its plain (non-composable) lambdas —
    // the precomputed label/annotation maps are the screens' picker grammar.
    val rowTitleText = rowTitle(triple.row)
    val overrideLabels = PerformanceOverride.entries.associateWith { performanceOverrideLabel(it) }
    val overrideAnnotations = PerformanceOverride.entries.associateWith {
        if (it == PerformanceOverride.AUTO) autoAnnotation
        else performanceOnOffLabel(it == PerformanceOverride.ON)
    }
    SettingListItem(
        icon = rowIcon(triple.row),
        title = rowTitle(triple.row),
        subtitle = subtitle,
        trailingText = performanceOverrideLabel(triple.override),
        highlighted = highlightSettingId == triple.row.id,
        onClick = {
            activePicker.value = PickerState.List(
                // Resolved in composition above — the onClick lambda is plain.
                title = rowTitleText,
                items = PerformanceOverride.entries,
                label = { overrideLabels.getValue(it) },
                // The Auto face carries the same folded annotation the row
                // subtitle shows; On/Off state themselves plainly.
                subtitle = { overrideAnnotations.getValue(it) },
                isSelected = { it == triple.override },
                onSelect = triple.onSelect,
            )
        },
    )
}

/** The `performance.level` group: the master dial + the screen-local device-profile cross-reference. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PerformanceLevelGroupBody(
    state: PerformanceHubState,
    highlightSettingId: String?,
    viewModel: PerformanceSettingsViewModel,
    navActions: SettingsNavActions,
    activePicker: MutableState<PickerState<*>?>,
) {
    val level = state.appearance.performanceLevel
    SettingsGroup(
        icon = Tabler.Outline.Gauge,
        title = stringResource(Res.string.settings_performance),
        summary = { performanceLevelLabel(level) },
        modifier = Modifier.padding(vertical = 8.dp),
        initiallyExpanded = highlightSettingId in SettingsScreenGroups.performanceLevel.itemIdSet,
    ) {
        // The declared master row (Always) plus the screen-local device-profile
        // cross-reference row (+1, the explicit screen-local term — it owns
        // no knob of its own).
        SettingsItemList(total = rowTotalFor(SettingsScreenGroups.performanceLevel, RowAdmissionFlags(showAdvanced = true)) + 1) {
            val levelTitle = rowTitle(PerformanceRows.PerformanceLevel)
            // The picker's faces resolve outside its plain (non-composable)
            // lambdas — the precomputed label/description maps.
            val levelLabels = PerformanceLevel.entries.associateWith { performanceLevelLabel(it) }
            val levelDescriptions = PerformanceLevel.entries.associateWith { performanceLevelDescription(it) }
            SettingListItem(
                icon = rowIcon(PerformanceRows.PerformanceLevel),
                title = levelTitle,
                subtitle = performanceLevelDescription(level),
                trailingText = performanceLevelLabel(level),
                highlighted = highlightSettingId == PerformanceRows.PerformanceLevel.id,
                onClick = {
                    activePicker.value = PickerState.List(
                        title = levelTitle,
                        items = PerformanceLevel.entries,
                        label = { levelLabels.getValue(it) },
                        subtitle = { levelDescriptions.getValue(it) },
                        isSelected = { it == level },
                        onSelect = { viewModel.edit { scope -> scope.appearance.setPerformanceLevel(it) } },
                    )
                },
            )
            // The screen-local cross-reference: the device-profile Re-detect
            // lives in playback advanced (its AUTO cache + probe machinery
            // belong to the engine lane) — deep-link there instead of
            // duplicating the action (the hub's ONE cross-reference for the
            // device tier).
            SettingListItem(
                icon = Tabler.Outline.InfoCircle,
                title = stringResource(Res.string.settings_perf_crossref_profile_title),
                subtitle = stringResource(Res.string.settings_perf_crossref_profile_subtitle),
                trailingText = "",
                highlighted = false,
                onClick = { navActions.onNavigate(Route.PlaybackSettings(PlaybackRows.RedetectPerformance.id)) },
            )
        }
    }
}

/** A tri-state-only expert group (motion or ambient): one override row per bundle, in declaration order. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PerformanceOverrideGroupBody(
    group: SettingsSearchItemGroup,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    triples: List<PerformanceOverrideTriple>,
    state: PerformanceHubState,
    highlightSettingId: String?,
    activePicker: MutableState<PickerState<*>?>,
) {
    SettingsGroup(
        icon = icon,
        title = when (group.id) {
            "performance.motion" -> stringResource(Res.string.settings_perf_group_motion)
            else -> stringResource(Res.string.settings_perf_group_ambient)
        },
        summary = null,
        modifier = Modifier.padding(vertical = 8.dp),
        initiallyExpanded = highlightSettingId in group.itemIdSet,
    ) {
        SettingsItemList(total = rowTotalFor(group, RowAdmissionFlags(showAdvanced = true))) {
            triples.forEach { triple ->
                PerformanceOverrideRow(
                    triple = triple,
                    level = state.appearance.performanceLevel,
                    highlightSettingId = highlightSettingId,
                    activePicker = activePicker,
                )
            }
        }
    }
}

/** The `performance.images` group: decode cap, crossfade, BlurHash, badges, memory cache. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PerformanceImagesGroupBody(
    state: PerformanceHubState,
    rowFlags: RowAdmissionFlags,
    highlightSettingId: String?,
    viewModel: PerformanceSettingsViewModel,
    activePicker: MutableState<PickerState<*>?>,
) {
    val level = state.appearance.performanceLevel
    val memoryCacheAuto = stringResource(Res.string.settings_perf_image_cache_auto)
    SettingsGroup(
        icon = PerformanceRows.PerfImageDecodeCap.icon,
        title = stringResource(Res.string.settings_perf_group_images),
        summary = null,
        modifier = Modifier.padding(vertical = 8.dp),
        initiallyExpanded = highlightSettingId in SettingsScreenGroups.performanceImages.itemIdSet,
    ) {
        SettingsItemList(total = rowTotalFor(SettingsScreenGroups.performanceImages, rowFlags)) {
            // The decode-size cap: AUTO follows the level's policy (the
            // two-tier thumbnail rule vs the aggressive flat cap), explicit
            // caps replace it wholesale (the resolver owns the fold).
            val decodeCap = state.appearance.perfImageDecodeCap
            val decodeTitle = rowTitle(PerformanceRows.PerfImageDecodeCap)
            val decodeCapLabels = ImageDecodeCap.entries.associateWith { performanceDecodeCapLabel(it) }
            SettingListItem(
                icon = rowIcon(PerformanceRows.PerfImageDecodeCap),
                title = decodeTitle,
                subtitle = performanceDecodeCapLabel(decodeCap),
                trailingText = performanceDecodeCapLabel(decodeCap),
                highlighted = highlightSettingId == PerformanceRows.PerfImageDecodeCap.id,
                onClick = {
                    activePicker.value = PickerState.List(
                        title = decodeTitle,
                        items = ImageDecodeCap.entries,
                        label = { decodeCapLabels.getValue(it) },
                        isSelected = { it == decodeCap },
                        onSelect = { viewModel.edit { scope -> scope.appearance.setPerfImageDecodeCap(it) } },
                    )
                },
            )
            PerformanceOverrideRow(
                triple = PerformanceOverrideTriple(
                    row = PerformanceRows.PerfImageCrossfade,
                    override = state.appearance.perfImageCrossfade,
                    displayValue = state.resolved.imageCrossfade,
                    onSelect = { viewModel.edit { scope -> scope.appearance.setPerfImageCrossfade(it) } },
                ),
                level = level,
                highlightSettingId = highlightSettingId,
                activePicker = activePicker,
            )
            PerformanceOverrideRow(
                triple = PerformanceOverrideTriple(
                    row = PerformanceRows.PerfBlurhash,
                    override = state.appearance.perfBlurhash,
                    displayValue = state.resolved.blurHashPlaceholders,
                    onSelect = { viewModel.edit { scope -> scope.appearance.setPerfBlurhash(it) } },
                ),
                level = level,
                highlightSettingId = highlightSettingId,
                activePicker = activePicker,
            )
            PerformanceOverrideRow(
                triple = PerformanceOverrideTriple(
                    row = PerformanceRows.PerfQualityBadges,
                    override = state.appearance.perfQualityBadges,
                    displayValue = state.resolved.qualityBadges,
                    onSelect = { viewModel.edit { scope -> scope.appearance.setPerfQualityBadges(it) } },
                ),
                level = level,
                highlightSettingId = highlightSettingId,
                activePicker = activePicker,
            )
            // The image memory-cache size: 0 = the RAM-tiered AUTO (12%/20%),
            // else an explicit percent the setter clamps into 5..35.
            val cachePercent = state.appearance.imageMemoryCachePercent
            val cacheTitle = rowTitle(PerformanceRows.ImageMemoryCache)
            SettingListItem(
                icon = rowIcon(PerformanceRows.ImageMemoryCache),
                title = cacheTitle,
                subtitle = if (cachePercent == 0) memoryCacheAuto else "$cachePercent%",
                trailingText = if (cachePercent == 0) stringResource(Res.string.settings_perf_override_auto) else "$cachePercent%",
                highlighted = highlightSettingId == PerformanceRows.ImageMemoryCache.id,
                onClick = {
                    activePicker.value = PickerState.List(
                        title = cacheTitle,
                        items = listOf(0) + (5..35).toList(),
                        label = { if (it == 0) memoryCacheAuto else "$it%" },
                        isSelected = { it == cachePercent },
                        onSelect = { viewModel.edit { scope -> scope.appearance.setImageMemoryCachePercent(it) } },
                    )
                },
            )
        }
    }
}

/** The `performance.buffers` group: the ExoPlayer load-control quartet + the engine-lane cross-references. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaybackBufferGroupBody(
    state: PerformanceHubState,
    highlightSettingId: String?,
    viewModel: PerformanceSettingsViewModel,
    navActions: SettingsNavActions,
    activePicker: MutableState<PickerState<*>?>,
) {
    val bufferAuto = stringResource(Res.string.settings_perf_buffer_auto)
    SettingsGroup(
        icon = Tabler.Outline.Database,
        title = stringResource(Res.string.settings_perf_group_buffers),
        summary = null,
        modifier = Modifier.padding(vertical = 8.dp),
        initiallyExpanded = highlightSettingId in SettingsScreenGroups.playbackBuffers.itemIdSet,
    ) {
        // The declared quartet plus the TWO screen-local cross-reference rows
        // (+2, the explicit screen-local terms): the back-buffer DURATION row
        // is the engine domain's (Playback → Engine config → ExoPlayer), and
        // the video-performance ceiling lives in Playback → Advanced video.
        // The hub surfaces the retain POSTURE only.
        SettingsItemList(total = rowTotalFor(SettingsScreenGroups.playbackBuffers, RowAdmissionFlags(showAdvanced = true)) + 2) {
            val initialBuffer = state.playback.exoInitialBufferMs
            val initialTitle = rowTitle(PerformanceRows.ExoInitialBuffer)
            SettingListItem(
                icon = rowIcon(PerformanceRows.ExoInitialBuffer),
                title = initialTitle,
                subtitle = rowSubtitle(PerformanceRows.ExoInitialBuffer),
                trailingText = "$initialBuffer ms",
                highlighted = highlightSettingId == PerformanceRows.ExoInitialBuffer.id,
                onClick = {
                    activePicker.value = PickerState.List(
                        title = initialTitle,
                        // The spec's 250 ms floor (MIN_EXO_INITIAL_BUFFER_MS)
                        // leads the preset ladder; the store setter clamps.
                        items = listOf(250, 500, 1_000, 1_500, 2_000, 3_000, 5_000),
                        label = { "$it ms" },
                        isSelected = { it == initialBuffer },
                        onSelect = { viewModel.edit { scope -> scope.playback.setExoInitialBufferMs(it) } },
                    )
                },
            )
            val rebuffer = state.playback.exoRebufferMs
            val rebufferTitle = rowTitle(PerformanceRows.ExoRebuffer)
            SettingListItem(
                icon = rowIcon(PerformanceRows.ExoRebuffer),
                title = rebufferTitle,
                subtitle = rowSubtitle(PerformanceRows.ExoRebuffer),
                trailingText = "$rebuffer ms",
                highlighted = highlightSettingId == PerformanceRows.ExoRebuffer.id,
                onClick = {
                    activePicker.value = PickerState.List(
                        title = rebufferTitle,
                        // The spec's 500 ms floor (MIN_EXO_REBUFFER_MS) leads
                        // the preset ladder; the store setter clamps.
                        items = listOf(500, 1_000, 2_000, 3_000, 5_000, 10_000),
                        label = { "$it ms" },
                        isSelected = { it == rebuffer },
                        onSelect = { viewModel.edit { scope -> scope.playback.setExoRebufferMs(it) } },
                    )
                },
            )
            val targetMb = state.playback.exoTargetBufferMb
            val targetTitle = rowTitle(PerformanceRows.ExoTargetBuffer)
            SettingListItem(
                icon = rowIcon(PerformanceRows.ExoTargetBuffer),
                title = targetTitle,
                subtitle = rowSubtitle(PerformanceRows.ExoTargetBuffer),
                trailingText = if (targetMb == 0) bufferAuto else "$targetMb MB",
                highlighted = highlightSettingId == PerformanceRows.ExoTargetBuffer.id,
                onClick = {
                    activePicker.value = PickerState.List(
                        title = targetTitle,
                        // The 0 sentinel is the AUTO choice (the engine's
                        // derived target stands); explicit values are 5..512.
                        items = listOf(0, 5, 16, 32, 64, 128, 256, 512),
                        label = { if (it == 0) bufferAuto else "$it MB" },
                        isSelected = { it == targetMb },
                        onSelect = { viewModel.edit { scope -> scope.playback.setExoTargetBufferMb(it) } },
                    )
                },
            )
            SettingToggleItem(
                icon = rowIcon(PerformanceRows.ExoRetainBackBuffer),
                title = rowTitle(PerformanceRows.ExoRetainBackBuffer),
                subtitle = rowSubtitle(PerformanceRows.ExoRetainBackBuffer),
                checked = state.playback.exoRetainBackBuffer,
                highlighted = highlightSettingId == PerformanceRows.ExoRetainBackBuffer.id,
                onCheckedChange = { viewModel.edit { scope -> scope.playback.setExoRetainBackBuffer(it) } },
            )
            // The engine-lane cross-references (screen-local rows — no search
            // entries, the +2 above): never duplicate the engine-domain rows.
            SettingListItem(
                icon = Tabler.Outline.InfoCircle,
                title = stringResource(Res.string.settings_perf_crossref_back_buffer_title),
                subtitle = stringResource(Res.string.settings_perf_crossref_back_buffer_subtitle),
                trailingText = "",
                highlighted = false,
                onClick = { navActions.onNavigate(Route.PlaybackSettings(PlaybackRows.ExoBackBuffer.id)) },
            )
            SettingListItem(
                icon = Tabler.Outline.InfoCircle,
                title = stringResource(Res.string.settings_perf_crossref_profile_title),
                subtitle = stringResource(Res.string.settings_perf_crossref_profile_subtitle),
                trailingText = "",
                highlighted = false,
                onClick = { navActions.onNavigate(Route.PlaybackSettings(PlaybackRows.VideoPerformanceProfile.id)) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PerformanceSettingsScreen(
    onBack: () -> Unit,
    highlightSettingId: String? = null,
    /** Cross-reference drill-ins (the engine-lane rows) ride the shared nav facade. */
    navActions: SettingsNavActions,
    viewModel: PerformanceSettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val showAdvanced by viewModel.showAdvancedSettings.collectAsStateWithLifecycle()
    val isTv = LocalTvMode.current
    val rowFlags = RowAdmissionFlags(isTv = isTv, showAdvanced = showAdvanced)

    PreferenceScreenScaffold(
        title = stringResource(Res.string.settings_performance),
        onBack = onBack,
        focusTag = "performance_init",
        highlightSettingId = highlightSettingId,
        groups = performanceScreenGroups(
            state = state,
            rowFlags = rowFlags,
            highlightSettingId = highlightSettingId,
            viewModel = viewModel,
            navActions = navActions,
        ),
        advancedToggle = PreferenceAdvancedToggle(
            showAdvanced = showAdvanced,
            onToggle = { viewModel.setShowAdvancedSettings(!showAdvanced) },
        ),
        hiddenHint = PreferenceHiddenHint(
            // The four expert groups' rows (all advanced-tagged on their
            // specs); the level row declares Always, so it never counts.
            rowFlags = RowAdmissionFlags(isTv = isTv, showAdvanced = false),
            onShowAdvanced = { viewModel.setShowAdvancedSettings(true) },
        ),
        // No reset action: the hub's rows reset with their OWNING categories
        // on the owning screens (Appearance reset covers the appearance-side
        // rows, Playback reset the buffer quartet) — a hub-level reset that
        // spans two whole categories would clear unrelated prefs.
        pickerHost = true,
    )
}
