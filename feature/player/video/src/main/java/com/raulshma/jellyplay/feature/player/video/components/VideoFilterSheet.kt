package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.feature.player.video.R
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.ui.components.PlayerModalBottomSheet
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.raulshma.jellyplay.core.ui.tv.components.TvOrTouchSlider
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.core.model.VideoEffectsConfig
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.ColorSwatch
import com.raulshma.jellyplay.core.ui.components.SheetHeader
import com.raulshma.jellyplay.core.ui.components.SheetSection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoFilterSheet(
    currentEffects: VideoEffectsConfig,
    onEffectsChange: (VideoEffectsConfig) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val isTv = LocalTvMode.current
    val focusRequester = remember { FocusRequester() }

    var brightness by remember { mutableFloatStateOf(currentEffects.brightness) }
    var contrast by remember { mutableFloatStateOf(currentEffects.contrast) }
    var saturation by remember { mutableFloatStateOf(currentEffects.saturation) }
    var sharpness by remember { mutableFloatStateOf(currentEffects.sharpness) }
    var hue by remember { mutableFloatStateOf(currentEffects.hue) }
    var rotation by remember { mutableFloatStateOf(currentEffects.rotationDegrees) }
    var redGain by remember { mutableFloatStateOf(currentEffects.redGain) }
    var greenGain by remember { mutableFloatStateOf(currentEffects.greenGain) }
    var blueGain by remember { mutableFloatStateOf(currentEffects.blueGain) }
    var blur by remember { mutableFloatStateOf(currentEffects.gaussianBlur) }

    fun emit() = VideoEffectsConfig(
        brightness = brightness,
        contrast = contrast,
        saturation = saturation,
        sharpness = sharpness,
        hue = hue,
        rotationDegrees = rotation,
        redGain = redGain,
        greenGain = greenGain,
        blueGain = blueGain,
        gaussianBlur = blur,
    )

    LaunchedEffect(isTv) {
        if (isTv) {
            focusRequester.tryRequestFocus("sheet")
        }
    }

    PlayerModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
        ) {
            val resetAllFocusState = rememberTvFocusState(focusedScale = 1.05f)
            SheetHeader(
                title = stringResource(R.string.player_video_video_filters),
                icon = Tabler.Outline.ColorSwatch,
                trailing = {
                    val pillShape = ShapeCache.smoothPill
                    Box(
                        modifier = Modifier
                            .clip(pillShape)
                            .then(resetAllFocusState.focusModifier)
                            .tvFocusIndicator(resetAllFocusState, pillShape)
                            .clickable {
                                brightness = 0f
                                contrast = 1f
                                saturation = 1f
                                sharpness = 0f
                                hue = 0f
                                rotation = 0f
                                redGain = 1f
                                greenGain = 1f
                                blueGain = 1f
                                blur = 0f
                                onEffectsChange(VideoEffectsConfig())
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.player_video_reset_all),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.tertiary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                },
            )
            Spacer(modifier = Modifier.height(8.dp))

            SheetSection(modifier = Modifier.padding(horizontal = 16.dp)) {
                FilterSlider(
                    label = stringResource(R.string.player_video_brightness),
                    value = brightness,
                    valueRange = -1f..1f,
                    valueLabel = String.format("%+.1f", brightness),
                    onValueChange = { brightness = it; onEffectsChange(emit()) },
                    onReset = { brightness = 0f; onEffectsChange(emit()) },
                    isTv = isTv,
                    focusRequester = focusRequester,
                )
                FilterSpacer()
                FilterSlider(
                    label = stringResource(R.string.player_video_contrast),
                    value = contrast,
                    valueRange = 0.5f..2f,
                    valueLabel = String.format("%.1f", contrast),
                    onValueChange = { contrast = it; onEffectsChange(emit()) },
                    onReset = { contrast = 1f; onEffectsChange(emit()) },
                    isTv = isTv,
                )
                FilterSpacer()
                FilterSlider(
                    label = stringResource(R.string.player_video_saturation),
                    value = saturation,
                    valueRange = 0f..3f,
                    valueLabel = String.format("%.1f", saturation),
                    onValueChange = { saturation = it; onEffectsChange(emit()) },
                    onReset = { saturation = 1f; onEffectsChange(emit()) },
                    isTv = isTv,
                )
                FilterSpacer()
                FilterSlider(
                    label = stringResource(R.string.player_video_sharpness),
                    value = sharpness,
                    valueRange = 0f..1f,
                    valueLabel = String.format("%.1f", sharpness),
                    onValueChange = { sharpness = it; onEffectsChange(emit()) },
                    onReset = { sharpness = 0f; onEffectsChange(emit()) },
                    isTv = isTv,
                )
                FilterSpacer()
                FilterSlider(
                    label = stringResource(R.string.player_video_hue),
                    value = hue,
                    valueRange = 0f..360f,
                    valueLabel = String.format("%.0f°", hue),
                    onValueChange = { hue = it; onEffectsChange(emit()) },
                    onReset = { hue = 0f; onEffectsChange(emit()) },
                    isTv = isTv,
                )
                FilterSpacer()
                FilterSlider(
                    label = stringResource(R.string.player_video_rotation),
                    value = rotation,
                    valueRange = -180f..180f,
                    valueLabel = String.format("%.0f°", rotation),
                    onValueChange = { rotation = it; onEffectsChange(emit()) },
                    onReset = { rotation = 0f; onEffectsChange(emit()) },
                    isTv = isTv,
                )
                FilterSpacer()
                FilterSlider(
                    label = stringResource(R.string.player_video_red_gain),
                    value = redGain,
                    valueRange = 0f..2f,
                    valueLabel = String.format("%.2f", redGain),
                    onValueChange = { redGain = it; onEffectsChange(emit()) },
                    onReset = { redGain = 1f; onEffectsChange(emit()) },
                    isTv = isTv,
                )
                FilterSpacer()
                FilterSlider(
                    label = stringResource(R.string.player_video_green_gain),
                    value = greenGain,
                    valueRange = 0f..2f,
                    valueLabel = String.format("%.2f", greenGain),
                    onValueChange = { greenGain = it; onEffectsChange(emit()) },
                    onReset = { greenGain = 1f; onEffectsChange(emit()) },
                    isTv = isTv,
                )
                FilterSpacer()
                FilterSlider(
                    label = stringResource(R.string.player_video_blue_gain),
                    value = blueGain,
                    valueRange = 0f..2f,
                    valueLabel = String.format("%.2f", blueGain),
                    onValueChange = { blueGain = it; onEffectsChange(emit()) },
                    onReset = { blueGain = 1f; onEffectsChange(emit()) },
                    isTv = isTv,
                )
                FilterSpacer()
                FilterSlider(
                    label = stringResource(R.string.player_video_gaussian_blur),
                    value = blur,
                    valueRange = 0f..10f,
                    valueLabel = String.format("%.1f", blur),
                    onValueChange = { blur = it; onEffectsChange(emit()) },
                    onReset = { blur = 0f; onEffectsChange(emit()) },
                    isTv = isTv,
                )
            }
        }
    }
}

@Composable
private fun FilterSpacer() {
    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
private fun FilterSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueLabel: String,
    onValueChange: (Float) -> Unit,
    onReset: () -> Unit,
    isTv: Boolean,
    focusRequester: FocusRequester? = null,
) {
    val resetFocusState = rememberTvFocusState(focusedScale = 1.05f)

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                ),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = valueLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )

                val shape = ShapeCache.smoothPill
                Box(
                    modifier = Modifier
                        .clip(shape)
                        .then(resetFocusState.focusModifier)
                        .tvFocusIndicator(resetFocusState, shape)
                        .clickable(onClick = onReset)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.player_video_reset),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
        TvOrTouchSlider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.fillMaxWidth(),
            isTv = isTv,
            colors = androidx.compose.material3.SliderDefaults.colors(
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
            ),
            focusRequester = focusRequester,
        )
    }
}
