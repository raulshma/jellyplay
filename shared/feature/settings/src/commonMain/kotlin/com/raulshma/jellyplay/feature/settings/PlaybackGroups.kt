package com.raulshma.jellyplay.feature.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.model.PlaybackPreferences
import com.raulshma.jellyplay.core.model.SyncPlayJoinBehavior
import com.raulshma.jellyplay.core.model.CastingStrategy
import com.raulshma.jellyplay.core.ui.components.SettingListItem
import com.raulshma.jellyplay.core.ui.components.SettingToggleItem
import com.raulshma.jellyplay.core.ui.components.SettingsItemList
import com.raulshma.jellyplay.core.ui.components.formatIntPattern
import com.raulshma.jellyplay.core.ui.model.localizedDescription
import com.raulshma.jellyplay.core.ui.model.localizedDisplayName
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import org.jetbrains.compose.resources.stringResource
import kotlinx.coroutines.CoroutineScope
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_media_segments
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_media_segments_summary
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_skip_segments_on_seek_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_skip_segments_on_seek_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_syncplay
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_syncplay_join_summary
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_join_always
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_join_ask
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_join_never
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_sync_tight
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_sync_balanced
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_sync_loose
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_sync_custom
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_casting_dlna
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_casting_strategy_summary
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_casting_prefer_cast
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_casting_prefer_dlna
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_casting_ask
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_none
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_living_room_tv
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_live_tv_dvr
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_dvr_padding_summary
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_x_minutes
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_dvr_start_on_time
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_dvr_start_early
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_dvr_stop_on_time
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_dvr_stop_late
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_dvr_quality_auto
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_dvr_quality_high
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_dvr_quality_medium
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_dvr_quality_low

/** The `playback.mediaSegments` group: the skip-on-seek toggle plus the per-type behavior rows. */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun PlaybackMediaSegmentsGroup(
    preferences: PlaybackPreferences,
    highlightSettingId: String?,
    viewModel: PlaybackSettingsViewModel,
    scope: CoroutineScope,
    activePicker: MutableState<PickerState<*>?>
) {
                SettingsGroup(
                    icon = Tabler.Outline.PlayerTrackNext,
                    title = stringResource(Res.string.settings_media_segments),
                    summary = {
                        val autoCount = preferences.segmentBehaviors.count { it.value == com.raulshma.jellyplay.core.model.SegmentBehavior.AUTO_SKIP }
                        val buttonCount = preferences.segmentBehaviors.count { it.value == com.raulshma.jellyplay.core.model.SegmentBehavior.SHOW_BUTTON }
                        stringResource(Res.string.settings_media_segments_summary, autoCount, buttonCount)
                    },
                    modifier = Modifier.padding(vertical = 8.dp),
                    initiallyExpanded = highlightSettingId?.startsWith("media_segment_") == true ||
                        highlightSettingId == PlaybackRows.SkipSegmentsOnSeek.id,
                ) {
                    val skipOnSeekOn = stringResource(Res.string.settings_skip_segments_on_seek_on)
                    val skipOnSeekOff = stringResource(Res.string.settings_skip_segments_on_seek_off)
                    SettingToggleItem(
                        icon = rowIcon(PlaybackRows.SkipSegmentsOnSeek),
                        title = rowTitle(PlaybackRows.SkipSegmentsOnSeek),
                        subtitle = if (preferences.skipSegmentsOnSeek) skipOnSeekOn else skipOnSeekOff,
                        checked = preferences.skipSegmentsOnSeek,
                        highlighted = highlightSettingId == PlaybackRows.SkipSegmentsOnSeek.id,
                        onCheckedChange = { viewModel.edit { scope -> scope.videoPlayer.setSkipSegmentsOnSeek(it) } },
                    )
                    val segmentTypes = com.raulshma.jellyplay.core.model.MediaSegmentType.entries
                    val totalTypes = segmentTypes.size
                    segmentTypes.forEachIndexed { index, type ->
                        val behavior = preferences.segmentBehaviors[type]
                            ?: com.raulshma.jellyplay.core.model.SegmentBehavior.IGNORE
                        // Resolve localized strings here in composable scope; the
                        // onClick lambda below is not composable so it must use
                        // these pre-resolved values.
                        val typeTitle = type.localizedDisplayName()
                        val allBehaviors = com.raulshma.jellyplay.core.model.SegmentBehavior.entries
                        val behaviorLabels = allBehaviors.associateWith { it.localizedDisplayName() }
                        val behaviorDescriptions = allBehaviors.associateWith { it.localizedDescription() }
                        SettingListItem(
                            icon = Tabler.Outline.PlayerTrackNext,
                            title = typeTitle,
                            subtitle = type.localizedDescription(),
                            trailingText = behavior.localizedDisplayName(),
                            index = index, count = totalTypes,
                            onClick = {
                                activePicker.value = PickerState.List(
                                    title = typeTitle,
                                    items = allBehaviors,
                                    label = { behaviorLabels.getValue(it) },
                                    subtitle = { behaviorDescriptions.getValue(it) },
                                    isSelected = { it == behavior },
                                    onSelect = { viewModel.edit { scope -> scope.videoPlayer.setSegmentBehavior(type, it) } },
                                )
                            },
                        )
                    }
                }
}

/** The `playback.syncPlay` group. */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun PlaybackSyncPlayGroup(
    preferences: PlaybackPreferences,
    highlightSettingId: String?,
    viewModel: PlaybackSettingsViewModel,
    scope: CoroutineScope,
    activePicker: MutableState<PickerState<*>?>
) {
                SettingsGroup(
                    icon = Tabler.Outline.Users,
                    title = stringResource(Res.string.settings_syncplay),
                    summary = { stringResource(Res.string.settings_syncplay_join_summary, preferences.syncPlayJoinBehavior.displayName) },
                    modifier = Modifier.padding(vertical = 8.dp),
                    initiallyExpanded = highlightSettingId in SettingsScreenGroups.playbackSyncPlay.itemIdSet,
                ) {
                    SettingsItemList(total = SettingsScreenGroups.playbackSyncPlay.items.size) {
                    val joinBehaviorDescs = SyncPlayJoinBehavior.entries.associateWith {
                        when (it) {
                            SyncPlayJoinBehavior.ALWAYS_JOIN -> stringResource(Res.string.settings_join_always)
                            SyncPlayJoinBehavior.ASK -> stringResource(Res.string.settings_join_ask)
                            SyncPlayJoinBehavior.NEVER_JOIN -> stringResource(Res.string.settings_join_never)
                        }
                    }

                    val joinBehaviorTitle = rowTitle(PlaybackRows.SyncplayJoinBehavior)
                    SettingListItem(
                        icon = rowIcon(PlaybackRows.SyncplayJoinBehavior),
                        title = joinBehaviorTitle,
                        subtitle = rowSubtitle(PlaybackRows.SyncplayJoinBehavior),
                        trailingText = preferences.syncPlayJoinBehavior.displayName,
                        highlighted = highlightSettingId == PlaybackRows.SyncplayJoinBehavior.id,
                        onClick = {
                            activePicker.value = PickerState.List(
                                title = joinBehaviorTitle,
                                items = SyncPlayJoinBehavior.entries,
                                label = { it.displayName },
                                subtitle = { joinBehaviorDescs[it] ?: it.displayName },
                                isSelected = { it == preferences.syncPlayJoinBehavior },
                                onSelect = { viewModel.edit { scope -> scope.syncPlayCast.setSyncPlayJoinBehavior(it) } },
                            )
                        },
                    )

                    val syncToleranceTitle = rowTitle(PlaybackRows.SyncplayTolerance)
                    val toleranceDescs = mapOf(
                        50L to stringResource(Res.string.settings_sync_tight),
                        100L to stringResource(Res.string.settings_sync_balanced),
                        500L to stringResource(Res.string.settings_sync_loose),
                    )
                    val syncCustom = stringResource(Res.string.settings_sync_custom)
                    SettingListItem(
                        icon = rowIcon(PlaybackRows.SyncplayTolerance),
                        title = syncToleranceTitle,
                        subtitle = rowSubtitle(PlaybackRows.SyncplayTolerance),
                        trailingText = "${preferences.syncPlayToleranceMs}ms",
                        highlighted = highlightSettingId == PlaybackRows.SyncplayTolerance.id,
                        onClick = {
                            val options = listOf(50L, 100L, 200L, 300L, 500L, 1000L)
                            activePicker.value = PickerState.List(
                                title = syncToleranceTitle,
                                items = options,
                                label = { "${it}ms" },
                                subtitle = { toleranceDescs[it] ?: syncCustom },
                                isSelected = { it == preferences.syncPlayToleranceMs },
                                onSelect = { viewModel.edit { scope -> scope.syncPlayCast.setSyncPlayToleranceMs(it) } },
                            )
                        },
                    )

                    SettingToggleItem(
                        icon = rowIcon(PlaybackRows.SyncplayAutoAcceptInvites),
                        title = rowTitle(PlaybackRows.SyncplayAutoAcceptInvites),
                        subtitle = rowSubtitle(PlaybackRows.SyncplayAutoAcceptInvites),
                        checked = preferences.syncPlayAutoAcceptInvites,
                        highlighted = highlightSettingId == PlaybackRows.SyncplayAutoAcceptInvites.id,
                        onCheckedChange = { viewModel.edit { scope -> scope.syncPlayCast.setSyncPlayAutoAcceptInvites(it) } },
                    )
                    }
                }
}

/** The `playback.casting` group. */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun PlaybackCastingGroup(
    preferences: PlaybackPreferences,
    highlightSettingId: String?,
    viewModel: PlaybackSettingsViewModel,
    scope: CoroutineScope,
    activePicker: MutableState<PickerState<*>?>
) {
                SettingsGroup(
                    icon = Tabler.Outline.DeviceTv,
                    title = stringResource(Res.string.settings_casting_dlna),
                    summary = { stringResource(Res.string.settings_casting_strategy_summary, preferences.defaultCastingStrategy.displayName) },
                    modifier = Modifier.padding(vertical = 8.dp),
                    initiallyExpanded = highlightSettingId in SettingsScreenGroups.playbackCasting.itemIdSet,
                ) {
                    SettingsItemList(total = SettingsScreenGroups.playbackCasting.items.size) {
                    val castingStrategyDescs = CastingStrategy.entries.associateWith {
                        when (it) {
                            CastingStrategy.PREFER_CAST -> stringResource(Res.string.settings_casting_prefer_cast)
                            CastingStrategy.PREFER_DLNA -> stringResource(Res.string.settings_casting_prefer_dlna)
                            CastingStrategy.ASK -> stringResource(Res.string.settings_casting_ask)
                        }
                    }

                    val castingStrategyTitle = rowTitle(PlaybackRows.CastingStrategy)
                    SettingListItem(
                        icon = rowIcon(PlaybackRows.CastingStrategy),
                        title = castingStrategyTitle,
                        subtitle = rowSubtitle(PlaybackRows.CastingStrategy),
                        trailingText = preferences.defaultCastingStrategy.displayName,
                        highlighted = highlightSettingId == PlaybackRows.CastingStrategy.id,
                        onClick = {
                            activePicker.value = PickerState.List(
                                title = castingStrategyTitle,
                                items = CastingStrategy.entries,
                                label = { it.displayName },
                                subtitle = { castingStrategyDescs[it] ?: it.displayName },
                                isSelected = { it == preferences.defaultCastingStrategy },
                                onSelect = { viewModel.edit { scope -> scope.syncPlayCast.setDefaultCastingStrategy(it) } },
                            )
                        },
                    )

                    SettingToggleItem(
                        icon = rowIcon(PlaybackRows.BackgroundCasting),
                        title = rowTitle(PlaybackRows.BackgroundCasting),
                        subtitle = rowSubtitle(PlaybackRows.BackgroundCasting),
                        checked = preferences.backgroundCastingEnabled,
                        highlighted = highlightSettingId == PlaybackRows.BackgroundCasting.id,
                        onCheckedChange = { viewModel.edit { scope -> scope.syncPlayCast.setBackgroundCastingEnabled(it) } },
                    )

                    val noneLabel = stringResource(Res.string.settings_none)
                    val rendererText = preferences.preferredRenderer ?: noneLabel
                    val livingRoomTvLabel = stringResource(Res.string.settings_living_room_tv)
                    SettingListItem(
                        icon = rowIcon(PlaybackRows.PreferredRenderer),
                        title = rowTitle(PlaybackRows.PreferredRenderer),
                        subtitle = rowSubtitle(PlaybackRows.PreferredRenderer),
                        trailingText = rendererText,
                        highlighted = highlightSettingId == PlaybackRows.PreferredRenderer.id,
                        onClick = {
                            if (preferences.preferredRenderer != null) {
                                viewModel.edit { it.syncPlayCast.setPreferredRenderer(null) }
                            } else {
                                viewModel.edit { it.syncPlayCast.setPreferredRenderer(livingRoomTvLabel) }
                            }
                        },
                    )
                    }
                }
}

/** The `playback.dvr` group. */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun PlaybackDvrGroup(
    preferences: PlaybackPreferences,
    highlightSettingId: String?,
    viewModel: PlaybackSettingsViewModel,
    scope: CoroutineScope,
    activePicker: MutableState<PickerState<*>?>
) {
                SettingsGroup(
                    icon = Tabler.Outline.DeviceTvOld,
                    title = stringResource(Res.string.settings_live_tv_dvr),
                    summary = { stringResource(Res.string.settings_dvr_padding_summary, preferences.dvrPrePaddingMinutes, preferences.dvrPostPaddingMinutes) },
                    modifier = Modifier.padding(vertical = 8.dp),
                    initiallyExpanded = highlightSettingId in SettingsScreenGroups.playbackDvr.itemIdSet,
                ) {
                    SettingsItemList(total = SettingsScreenGroups.playbackDvr.items.size) {
                    val noneLabel = stringResource(Res.string.settings_none)

                    val dvrPrePaddingTitle = rowTitle(PlaybackRows.DvrPrePadding)
                    val xMinutesFormat = stringResource(Res.string.settings_x_minutes)
                    val dvrStartOnTime = stringResource(Res.string.settings_dvr_start_on_time)
                    val dvrStartEarlyFormat = stringResource(Res.string.settings_dvr_start_early)
                    SettingListItem(
                        icon = rowIcon(PlaybackRows.DvrPrePadding),
                        title = dvrPrePaddingTitle,
                        subtitle = rowSubtitle(PlaybackRows.DvrPrePadding),
                        trailingText = stringResource(Res.string.settings_x_minutes, preferences.dvrPrePaddingMinutes),
                        highlighted = highlightSettingId == PlaybackRows.DvrPrePadding.id,
                        onClick = {
                            val options = listOf(0, 1, 2, 5, 10, 15)
                            activePicker.value = PickerState.List(
                                title = dvrPrePaddingTitle,
                                items = options,
                                label = { if (it == 0) noneLabel else formatIntPattern(xMinutesFormat, it) },
                                subtitle = { if (it == 0) dvrStartOnTime else formatIntPattern(dvrStartEarlyFormat, it) },
                                isSelected = { it == preferences.dvrPrePaddingMinutes },
                                onSelect = { viewModel.edit { scope -> scope.syncPlayCast.setDvrPrePaddingMinutes(it) } },
                            )
                        },
                    )

                    val dvrPostPaddingTitle = rowTitle(PlaybackRows.DvrPostPadding)
                    val dvrStopOnTime = stringResource(Res.string.settings_dvr_stop_on_time)
                    val dvrStopLateFormat = stringResource(Res.string.settings_dvr_stop_late)
                    SettingListItem(
                        icon = rowIcon(PlaybackRows.DvrPostPadding),
                        title = dvrPostPaddingTitle,
                        subtitle = rowSubtitle(PlaybackRows.DvrPostPadding),
                        trailingText = stringResource(Res.string.settings_x_minutes, preferences.dvrPostPaddingMinutes),
                        highlighted = highlightSettingId == PlaybackRows.DvrPostPadding.id,
                        onClick = {
                            val options = listOf(0, 1, 2, 5, 10, 15, 30)
                            activePicker.value = PickerState.List(
                                title = dvrPostPaddingTitle,
                                items = options,
                                label = { if (it == 0) noneLabel else formatIntPattern(xMinutesFormat, it) },
                                subtitle = { if (it == 0) dvrStopOnTime else formatIntPattern(dvrStopLateFormat, it) },
                                isSelected = { it == preferences.dvrPostPaddingMinutes },
                                onSelect = { viewModel.edit { scope -> scope.syncPlayCast.setDvrPostPaddingMinutes(it) } },
                            )
                        },
                    )

                    val dvrRecordingQualityTitle = rowTitle(PlaybackRows.DvrRecordingQuality)
                    val dvrQualityDescs = mapOf(
                        "AUTO" to stringResource(Res.string.settings_dvr_quality_auto),
                        "HIGH" to stringResource(Res.string.settings_dvr_quality_high),
                        "MEDIUM" to stringResource(Res.string.settings_dvr_quality_medium),
                        "LOW" to stringResource(Res.string.settings_dvr_quality_low),
                    )
                    SettingListItem(
                        icon = rowIcon(PlaybackRows.DvrRecordingQuality),
                        title = dvrRecordingQualityTitle,
                        subtitle = rowSubtitle(PlaybackRows.DvrRecordingQuality),
                        trailingText = preferences.dvrRecordingQuality,
                        highlighted = highlightSettingId == PlaybackRows.DvrRecordingQuality.id,
                        onClick = {
                            val options = listOf("AUTO", "HIGH", "MEDIUM", "LOW")
                            activePicker.value = PickerState.List(
                                title = dvrRecordingQualityTitle,
                                items = options,
                                label = { it },
                                subtitle = { dvrQualityDescs[it] ?: "" },
                                isSelected = { it == preferences.dvrRecordingQuality },
                                onSelect = { viewModel.edit { scope -> scope.syncPlayCast.setDvrRecordingQuality(it) } },
                            )
                        },
                    )
                    }
                }
}
