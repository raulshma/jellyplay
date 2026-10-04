package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.AlertTriangle
import com.composables.icons.tabler.outline.FileText
import com.composables.icons.tabler.outline.InfoCircle
import com.composables.icons.tabler.outline.Subtitles
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.AssOverrideMode
import com.raulshma.jellyplay.core.model.SubtitleBorderStyle
import com.raulshma.jellyplay.core.model.SubtitleColor
import com.raulshma.jellyplay.core.model.SubtitleEdgeType
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.core.ui.components.SubtitleFreeFormColorPickerDialog
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.components.DpadSlider
import com.raulshma.jellyplay.core.ui.tv.components.TvOrTouchSlider
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.feature.player.video.generated.resources.Res
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_ass_basic_notice
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_ass_embedded_notice
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_ass_force
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_ass_force_hint
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_ass_respect
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_ass_scale_hint
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_ass_styling
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_background_color
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_background_opacity
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_bold
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_border_background_box
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_border_opaque_box
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_border_outline_shadow
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_border_style
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_border_width
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_bundled_default
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_edge_color
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_edge_depressed
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_edge_none
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_edge_outline
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_edge_raised
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_edge_shadow
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_edge_type
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_font
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_font_color
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_font_size
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_italic
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_mpv_config_app_owned_keys
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_mpv_config_ass_override_owned
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_mpv_config_case_ass
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_mpv_config_case_srt
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_mpv_config_dropped_keys
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_mpv_config_notice_title
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_mpv_config_owned_keys_intro
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_override_subtitle_styles
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_override_subtitle_styles_hint
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_pick
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_reset
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_shadow_offset
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_subtitle_offset
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_vertical_position

import com.raulshma.jellyplay.feature.player.video.engine.EngineCapabilities
import com.raulshma.jellyplay.feature.player.video.engine.mpv.MpvSubtitleOwnership
import com.raulshma.jellyplay.feature.player.video.engine.mpv.MpvUserSubtitleKeys
import kotlin.math.roundToLong

/**
 * Single source of truth for the subtitle style editing form. Shared by
 * [SubtitleStyleSheet] (player bottom sheet) and the standalone subtitle tester.
 *
 * Each caller wraps this in its own scroll container and controls presentation
 * concerns (sheet chrome, title, reset placement) via the parameters:
 * - [showOverrideToggle]: the player sheet shows the master switch; the tester
 *   forces `applyCustomStyle = true` upstream and hides it.
 * - [onReset]: when non-null a "Reset" chip is appended; pass null when the host
 *   owns reset (e.g. the tester's top-app-bar button).
 *
 * Local slider state is keyed on the matching [currentStyle] field so it
 * re-syncs when the host mutates the style externally (reset, undo, preset
 * load). Touch sliders debounce via `onValueChangeFinished`; D-pad sliders
 * (discrete steps) and chips emit immediately.
 *
 * @param currentStyle  Hoisted source of truth. Reads are controlled by this.
 * @param onStyleChange Emits the next style on every user action.
 * @param capabilities  Per-engine gates (hides unsupported controls).
 * @param subtitleOwnership Per-session snapshot of the user's custom mpv
 *  config (Advanced MPV Configuration / Android mpv.conf): when active, the
 *  engine skips the owned `sub-*` keys so the matching controls here are
 *  no-ops — the notice card spells out every interaction case (owned keys,
 *  the ASS-override requirement, the quoting trap) instead of letting edits
 *  silently do nothing. [MpvSubtitleOwnership.NONE] on engines without a
 *  user-config surface hides the card.
 * @param onPickFont    Invoked when the user taps the font row.
 * @param onReset       When non-null, renders a reset chip that calls it.
 * @param onSubtitleDelayChange When non-null, the subtitle-offset slider routes
 *  through it (per-media delay) instead of [onStyleChange]. The player hub uses
 *  this so delay edits persist per-item; the standalone tester leaves it null to
 *  keep the legacy style-bucket behaviour.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SubtitleStyleControls(
    currentStyle: SubtitleStyle,
    onStyleChange: (SubtitleStyle) -> Unit,
    modifier: Modifier = Modifier,
    capabilities: EngineCapabilities = EngineCapabilities(),
    subtitleOwnership: MpvSubtitleOwnership = MpvSubtitleOwnership.NONE,
    onPickFont: () -> Unit = {},
    showOverrideToggle: Boolean = true,
    onReset: (() -> Unit)? = null,
    onSubtitleDelayChange: ((Long) -> Unit)? = null,
) {
    val isTv = LocalTvMode.current

    // Master override toggle (player sheet only). Keyed so external mutations
    // (reset) re-sync the switch. When the toggle is hidden the tester forces
    // this on upstream, so we hard-gate every block to enabled.
    var overrideEnabled by remember(currentStyle.applyCustomStyle) {
        mutableStateOf(currentStyle.applyCustomStyle)
    }
    val applyCustomStyle = if (showOverrideToggle) overrideEnabled else true

    // --- Touch-slider local state (debounced via onValueChangeFinished) ---
    // Keyed on currentStyle so external resets re-sync the displayed value.
    var fontSize by remember(currentStyle.fontSize) { mutableIntStateOf(currentStyle.fontSize) }
    var backgroundOpacity by remember(currentStyle.backgroundOpacity) {
        mutableFloatStateOf(currentStyle.backgroundOpacity)
    }
    var borderWidth by remember(currentStyle.borderWidth) { mutableFloatStateOf(currentStyle.borderWidth) }
    var shadowOffset by remember(currentStyle.shadowOffset) { mutableFloatStateOf(currentStyle.shadowOffset) }
    var offsetMs by remember(currentStyle.offsetMs) { mutableLongStateOf(currentStyle.offsetMs) }
    var verticalPosition by remember(currentStyle.verticalPosition) {
        mutableFloatStateOf(currentStyle.verticalPosition)
    }

    // Free-form color picker dialog state (capability-gated entry below).
    var showFontColorPicker by remember { mutableStateOf(false) }
    var showBackgroundColorPicker by remember { mutableStateOf(false) }
    var showEdgeColorPicker by remember { mutableStateOf(false) }

    // Initial D-pad focus into the sheet's first focusable (toggle) on TV.
    val toggleFocusRequester = remember { FocusRequester() }
    LaunchedEffect(showOverrideToggle, isTv) {
        if (showOverrideToggle && isTv) toggleFocusRequester.tryRequestFocus("subtitle-style")
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Custom mpv config (Advanced MPV Configuration / Android's
        // <filesDir>/mpv/mpv.conf) owns one or more sub-* styling keys: the
        // engine skips exactly those keys (init AND runtime), so the config
        // value wins and the matching controls in this form are no-ops.
        // Spell out every interaction case instead of letting an edit
        // silently do nothing.
        if (subtitleOwnership.isActive) {
            Spacer(Modifier.height(8.dp))
            MpvConfigOwnershipNotice(
                ownership = subtitleOwnership,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Engine renders subtitles but not full ASS (libVLC). Inform the user.
        if (!capabilities.supportsAssOverride && capabilities.supportsSubtitleStyle) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().clip(ShapeCache.smooth8)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(12.dp),
            ) {
                Text(
                    stringResource(Res.string.player_video_ass_basic_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Style Override Toggle (player sheet only).
        if (showOverrideToggle) {
            val toggleFocusState = rememberTvFocusState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(ShapeCache.smooth8)
                    .then(
                        if (isTv) {
                            Modifier.focusRequester(toggleFocusRequester)
                                .then(toggleFocusState.focusModifier)
                                .tvFocusIndicator(toggleFocusState, ShapeCache.smooth8)
                                .clickable {
                                    overrideEnabled = !overrideEnabled
                                    onStyleChange(currentStyle.copy(applyCustomStyle = overrideEnabled))
                                }
                        } else {
                            Modifier
                        },
                    )
                    .padding(vertical = 8.dp, horizontal = if (isTv) 12.dp else 0.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        stringResource(Res.string.player_video_override_subtitle_styles),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    )
                    Text(
                        stringResource(Res.string.player_video_override_subtitle_styles_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = currentStyle.applyCustomStyle,
                    onCheckedChange = if (isTv) null else { checked ->
                        overrideEnabled = checked
                        onStyleChange(currentStyle.copy(applyCustomStyle = checked))
                    },
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        // --- Font Size ---
        Text(
            stringResource(Res.string.player_video_font_size, fontSize),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = if (applyCustomStyle) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TvOrTouchSlider(
            value = fontSize.toFloat(),
            onValueChange = { fontSize = it.toInt() },
            onValueChangeFinished = { onStyleChange(currentStyle.copy(fontSize = fontSize)) },
            valueRange = 16f..48f,
            modifier = Modifier.fillMaxWidth(),
            isTv = isTv,
            enabled = applyCustomStyle,
            steps = 32,
            dpadStep = 1f,
            colors = SliderDefaults.colors(
                thumbColor = if (applyCustomStyle) MaterialTheme.colorScheme.primary else Color.Gray,
                activeTrackColor = if (applyCustomStyle) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.5f),
            ),
        )

        // --- Typography: font family + bold/italic ---
        if (applyCustomStyle) {
            if (capabilities.supportsFontFamily) {
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(Res.string.player_video_font),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                )
                Spacer(Modifier.height(4.dp))
                val fontRowFocus = rememberTvFocusState(focusedScale = 1.03f)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(ShapeCache.smooth8)
                        .then(fontRowFocus.focusModifier)
                        .tvFocusIndicator(fontRowFocus, ShapeCache.smooth8)
                        .clickable { onPickFont() }
                        .padding(vertical = 10.dp, horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        currentStyle.fontFamilyName ?: stringResource(Res.string.player_video_bundled_default),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        stringResource(Res.string.player_video_pick),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextStyleToggle(label = stringResource(Res.string.player_video_bold), selected = currentStyle.bold) {
                    onStyleChange(currentStyle.copy(bold = it))
                }
                TextStyleToggle(label = stringResource(Res.string.player_video_italic), selected = currentStyle.italic) {
                    onStyleChange(currentStyle.copy(italic = it))
                }
            }
        }

        // --- ASS Override: SCALE keeps embedded styling, FORCE overrides it ---
        if (capabilities.supportsAssOverride && applyCustomStyle) {
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(Res.string.player_video_ass_styling),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssOverrideChip(
                    label = stringResource(Res.string.player_video_ass_respect),
                    isSelected = currentStyle.assOverride == AssOverrideMode.SCALE,
                ) {
                    onStyleChange(currentStyle.copy(assOverride = AssOverrideMode.SCALE))
                }
                // FORCE (full override of ASS colors/edges/font) is only honored by
                // engines that can apply user style overrides to ASS tracks (mpv via
                // libass --ass-override=force). ExoPlayer renders ASS as-authored, so
                // the chip would be a no-op there — hide it. Respect (SCALE) stays
                // because font scale is honored on both engines.
                if (capabilities.supportsAssStyleOverride) {
                    AssOverrideChip(
                        label = stringResource(Res.string.player_video_ass_force),
                        isSelected = currentStyle.assOverride == AssOverrideMode.FORCE,
                    ) {
                        onStyleChange(currentStyle.copy(assOverride = AssOverrideMode.FORCE))
                    }
                }
            }
            Text(
                if (currentStyle.assOverride == AssOverrideMode.SCALE)
                    stringResource(Res.string.player_video_ass_scale_hint)
                else
                    stringResource(Res.string.player_video_ass_force_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Honesty note for engines that render ASS but cannot override its
            // styling (ExoPlayer: ass-media 0.4.0 has no compile-time override API).
            if (!capabilities.supportsAssStyleOverride) {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(Res.string.player_video_ass_embedded_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // --- Font Color ---
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(Res.string.player_video_font_color),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = if (applyCustomStyle) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SubtitleColor.entries.forEach { color ->
                ColorChip(
                    color = Color(color.value),
                    // Free-form ARGB takes precedence when set.
                    isSelected = currentStyle.fontColorArgb == null && currentStyle.fontColor == color,
                    enabled = applyCustomStyle,
                    onClick = {
                        onStyleChange(currentStyle.copy(fontColor = color, fontColorArgb = null))
                    },
                )
            }
            // Free-form color picker swatch (capability-gated).
            if (capabilities.supportsCustomSubtitleColors && applyCustomStyle) {
                ColorChip(
                    color = if (currentStyle.fontColorArgb != null) Color(currentStyle.fontColorArgb!!) else Color.Transparent,
                    isSelected = currentStyle.fontColorArgb != null,
                    enabled = true,
                    onClick = { showFontColorPicker = true },
                )
            }
        }

        // --- Background Color ---
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(Res.string.player_video_background_color),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = if (applyCustomStyle) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SubtitleColor.entries.forEach { color ->
                ColorChip(
                    color = Color(color.value),
                    isSelected = currentStyle.backgroundColorArgb == null && currentStyle.backgroundColor == color,
                    enabled = applyCustomStyle,
                    onClick = {
                        onStyleChange(currentStyle.copy(backgroundColor = color, backgroundColorArgb = null))
                    },
                )
            }
            if (capabilities.supportsCustomSubtitleColors && applyCustomStyle) {
                ColorChip(
                    color = if (currentStyle.backgroundColorArgb != null) Color(currentStyle.backgroundColorArgb!!) else Color.Transparent,
                    isSelected = currentStyle.backgroundColorArgb != null,
                    enabled = true,
                    onClick = { showBackgroundColorPicker = true },
                )
            }
        }

        // --- Background Opacity ---
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(Res.string.player_video_background_opacity, (backgroundOpacity * 100).toInt()),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = if (applyCustomStyle) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TvOrTouchSlider(
            value = backgroundOpacity,
            onValueChange = { backgroundOpacity = it },
            onValueChangeFinished = { onStyleChange(currentStyle.copy(backgroundOpacity = backgroundOpacity)) },
            valueRange = 0f..1f,
            modifier = Modifier.fillMaxWidth(),
            isTv = isTv,
            enabled = applyCustomStyle,
            steps = 10,
            dpadStep = 0.1f,
            colors = SliderDefaults.colors(
                thumbColor = if (applyCustomStyle) MaterialTheme.colorScheme.primary else Color.Gray,
                activeTrackColor = if (applyCustomStyle) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.5f),
            ),
        )

        // --- Edge Type ---
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(Res.string.player_video_edge_type),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = if (applyCustomStyle) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SubtitleEdgeType.entries.forEach { type ->
                val isSelected = currentStyle.edgeType == type
                SubtitleStyleChip(
                    label = when (type) {
                        SubtitleEdgeType.NONE -> stringResource(Res.string.player_video_edge_none)
                        SubtitleEdgeType.OUTLINE -> stringResource(Res.string.player_video_edge_outline)
                        SubtitleEdgeType.DROP_SHADOW -> stringResource(Res.string.player_video_edge_shadow)
                        SubtitleEdgeType.RAISED -> stringResource(Res.string.player_video_edge_raised)
                        SubtitleEdgeType.DEPRESSED -> stringResource(Res.string.player_video_edge_depressed)
                    },
                    selected = isSelected,
                    enabled = applyCustomStyle,
                    onClick = { onStyleChange(currentStyle.copy(edgeType = type)) },
                )
            }
        }

        // --- Edge Color (only when an edge is active) ---
        if (applyCustomStyle && currentStyle.edgeType != SubtitleEdgeType.NONE) {
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(Res.string.player_video_edge_color),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            )
            Spacer(Modifier.height(4.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SubtitleColor.entries.forEach { color ->
                    ColorChip(
                        color = Color(color.value),
                        isSelected = currentStyle.edgeColorArgb == null && currentStyle.edgeColor == color,
                        enabled = applyCustomStyle,
                        onClick = {
                            onStyleChange(currentStyle.copy(edgeColor = color, edgeColorArgb = null))
                        },
                    )
                }
                if (capabilities.supportsCustomSubtitleColors && applyCustomStyle) {
                    ColorChip(
                        color = if (currentStyle.edgeColorArgb != null) Color(currentStyle.edgeColorArgb!!) else Color.Transparent,
                        isSelected = currentStyle.edgeColorArgb != null,
                        enabled = true,
                        onClick = { showEdgeColorPicker = true },
                    )
                }
            }
        }

        // --- Border Style: outline+shadow, opaque box, or background box ---
        // Gated on supportsAssStyleOverride: ExoPlayer renders ASS as-authored,
        // so the border-style control would be a no-op on ASS tracks there.
        if (capabilities.supportsBorderStyles && capabilities.supportsAssStyleOverride && applyCustomStyle) {
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(Res.string.player_video_border_style),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            )
            Spacer(Modifier.height(4.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SubtitleBorderStyle.entries.forEach { bs ->
                    val label = when (bs) {
                        SubtitleBorderStyle.OUTLINE_AND_SHADOW -> stringResource(Res.string.player_video_border_outline_shadow)
                        SubtitleBorderStyle.OPAQUE_BOX -> stringResource(Res.string.player_video_border_opaque_box)
                        SubtitleBorderStyle.BACKGROUND_BOX -> stringResource(Res.string.player_video_border_background_box)
                    }
                    val isSelected = currentStyle.borderStyle == bs
                    SubtitleStyleChip(
                        label = label,
                        selected = isSelected,
                        onClick = { onStyleChange(currentStyle.copy(borderStyle = bs)) },
                    )
                }
            }

            // Border width slider (0.0..6.0). Background box uses opacity, not width.
            if (currentStyle.borderStyle != SubtitleBorderStyle.BACKGROUND_BOX) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(Res.string.player_video_border_width, "%.1f".format(borderWidth)),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                )
                TvOrTouchSlider(
                    value = borderWidth,
                    onValueChange = { borderWidth = it },
                    onValueChangeFinished = { onStyleChange(currentStyle.copy(borderWidth = borderWidth)) },
                    valueRange = 0f..6f,
                    modifier = Modifier.fillMaxWidth(),
                    isTv = isTv,
                    steps = 59,
                    dpadStep = 0.2f,
                )

                // Shadow offset slider (only relevant for outline+shadow).
                if (currentStyle.borderStyle == SubtitleBorderStyle.OUTLINE_AND_SHADOW) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(Res.string.player_video_shadow_offset, "%.1f".format(shadowOffset)),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    )
                    TvOrTouchSlider(
                        value = shadowOffset,
                        onValueChange = { shadowOffset = it },
                        onValueChangeFinished = { onStyleChange(currentStyle.copy(shadowOffset = shadowOffset)) },
                        valueRange = 0f..4f,
                        modifier = Modifier.fillMaxWidth(),
                        isTv = isTv,
                        steps = 39,
                        dpadStep = 0.2f,
                    )
                }
            }
        }

        // --- Subtitle Offset ---
        if (capabilities.supportsSubtitleDelay) {
            Spacer(Modifier.height(12.dp))
            val offsetSec = offsetMs / 1000.0
            val offsetLabel = when {
                offsetMs == 0L -> "0.0s"
                offsetMs > 0 -> "+${"%.1f".format(offsetSec)}s"
                else -> "${"%.1f".format(offsetSec)}s"
            }
            Text(
                stringResource(Res.string.player_video_subtitle_offset, offsetLabel),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            )
            TvOrTouchSlider(
                value = offsetMs.toFloat(),
                onValueChange = { offsetMs = (it / 100f).roundToLong() * 100 },
                onValueChangeFinished = {
                    // Per-media delay is persisted per-item when a dedicated handler
                    // is supplied (player hub); otherwise the standalone tester falls
                    // back to the style bucket so its behaviour is unchanged.
                    val delayHandler = onSubtitleDelayChange
                    if (delayHandler != null) delayHandler(offsetMs)
                    else onStyleChange(currentStyle.copy(offsetMs = offsetMs))
                },
                valueRange = -30000f..30000f,
                modifier = Modifier.fillMaxWidth(),
                isTv = isTv,
                steps = 599,
                dpadStep = 500f,
            )
        }

        // --- Vertical Position ---
        if (capabilities.supportsSubtitleVerticalPosition) {
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(Res.string.player_video_vertical_position, (verticalPosition * 100).toInt()),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            )
            TvOrTouchSlider(
                value = verticalPosition,
                onValueChange = { verticalPosition = it },
                onValueChangeFinished = { onStyleChange(currentStyle.copy(verticalPosition = verticalPosition)) },
                valueRange = 0f..0.4f,
                modifier = Modifier.fillMaxWidth(),
                isTv = isTv,
                dpadStep = 0.02f,
            )
        }

        // --- Reset chip (host-opt-in) ---
        if (onReset != null) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                val resetFocusState = rememberTvFocusState(focusedScale = 1.05f)
                FilterChip(
                    selected = false,
                    enabled = applyCustomStyle,
                    onClick = { onReset() },
                    label = { Text(stringResource(Res.string.player_video_reset), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium) },
                    shape = ShapeCache.smoothPill,
                    modifier = Modifier
                        .then(resetFocusState.focusModifier)
                        .then(Modifier.tvFocusIndicator(resetFocusState, ShapeCache.smoothPill)),
                )
            }
        }
    }

    // --- Free-form color picker dialogs (capability-gated entry points above) ---
    SubtitleFreeFormColorPickerHost(
        show = showFontColorPicker,
        initialColor = currentStyle.fontColorArgb?.let(::Color) ?: Color(currentStyle.fontColor.value),
        onDismiss = { showFontColorPicker = false },
        onColorSelected = { picked -> onStyleChange(currentStyle.copy(fontColorArgb = picked)) },
    )
    SubtitleFreeFormColorPickerHost(
        show = showBackgroundColorPicker,
        initialColor = currentStyle.backgroundColorArgb?.let(::Color) ?: Color(currentStyle.backgroundColor.value),
        onDismiss = { showBackgroundColorPicker = false },
        onColorSelected = { picked -> onStyleChange(currentStyle.copy(backgroundColorArgb = picked)) },
    )
    SubtitleFreeFormColorPickerHost(
        show = showEdgeColorPicker,
        initialColor = currentStyle.edgeColorArgb?.let(::Color) ?: Color(currentStyle.edgeColor.value),
        onDismiss = { showEdgeColorPicker = false },
        onColorSelected = { picked -> onStyleChange(currentStyle.copy(edgeColorArgb = picked)) },
    )
}

/**
 * Renders [SubtitleFreeFormColorPickerDialog] while [show] is set — the shared
 * gate for the font/background/edge picker entry points, which differ only in
 * the style field they read and write.
 */
@Composable
private fun SubtitleFreeFormColorPickerHost(
    show: Boolean,
    initialColor: Color,
    onDismiss: () -> Unit,
    onColorSelected: (Int) -> Unit,
) {
    if (!show) return
    SubtitleFreeFormColorPickerDialog(
        initialColor = initialColor,
        onDismiss = onDismiss,
        onColorSelected = onColorSelected,
    )
}

/**
 * The custom-mpv-config ownership notice: a warning-tinted card that spells
 * out, case by case, what the user's config wins versus what stays app-driven
 * (issue #165, UI half).
 *
 * Structure, top to bottom:
 *  - Title row (warning icon) — the card's reason to exist at a glance.
 *  - Owned keys as monospace pills — the exact `sub-*` keys the engine yields,
 *    so the no-op controls are identifiable without decoding a joined string.
 *  - Two scannable case rows: plain (SRT) subtitles always take the config's
 *    colors/borders/fonts; ASS/SSA subtitles keep embedded styling unless the
 *    ASS Styling choice below forces the app style. Shown only when style
 *    keys are actually owned — with a purely-broken config (dropped-keys only)
 *    nothing is owned and no control is a no-op.
 *  - Respect/Force dead-choice row when the config owns `sub-ass-override`.
 *  - App-owned reassurance row: on/off and delay always follow the app.
 *  - Error callout for owned keys whose mpv.conf value the parser destroys
 *    (unquoted `#`), with the quoting fix inline.
 *
 * Informational only — never focusable, so TV D-pad navigation skips past it.
 */
@Composable
private fun MpvConfigOwnershipNotice(
    ownership: MpvSubtitleOwnership,
    modifier: Modifier = Modifier,
) {
    val warningColor = MaterialTheme.colorScheme.tertiary
    val errorColor = MaterialTheme.colorScheme.error
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val hasOwnedStyleKeys = ownership.ownedStyleKeys.isNotEmpty()

    Column(
        modifier = modifier
            .clip(ShapeCache.smooth8)
            .background(warningColor.copy(alpha = 0.10f))
            .border(1.dp, warningColor.copy(alpha = 0.28f), ShapeCache.smooth8)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Tabler.Outline.AlertTriangle,
                contentDescription = null,
                tint = warningColor,
                modifier = Modifier.size(18.dp),
            )
            Text(
                stringResource(Res.string.player_video_mpv_config_notice_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = onSurface,
            )
        }

        if (hasOwnedStyleKeys) {
            Text(
                stringResource(Res.string.player_video_mpv_config_owned_keys_intro),
                style = MaterialTheme.typography.bodySmall,
                color = onSurfaceVariant,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ownership.ownedStyleKeys.sorted().forEach { key ->
                    MpvConfigKeyPill(
                        key = key,
                        contentColor = onSurface,
                        borderColor = onSurfaceVariant.copy(alpha = 0.35f),
                    )
                }
            }
            MpvConfigCaseRow(
                icon = Tabler.Outline.FileText,
                text = stringResource(Res.string.player_video_mpv_config_case_srt),
                tint = onSurfaceVariant,
            )
            MpvConfigCaseRow(
                icon = Tabler.Outline.Subtitles,
                text = stringResource(Res.string.player_video_mpv_config_case_ass),
                tint = onSurfaceVariant,
            )
            if (MpvUserSubtitleKeys.ASS_OVERRIDE_KEY in ownership.ownedStyleKeys) {
                MpvConfigCaseRow(
                    icon = Tabler.Outline.AlertTriangle,
                    text = stringResource(Res.string.player_video_mpv_config_ass_override_owned),
                    tint = warningColor,
                )
            }
            MpvConfigCaseRow(
                icon = Tabler.Outline.InfoCircle,
                text = stringResource(Res.string.player_video_mpv_config_app_owned_keys),
                tint = onSurfaceVariant,
            )
        }

        if (ownership.confKeysDroppedByParser.isNotEmpty()) {
            MpvConfigCaseRow(
                icon = Tabler.Outline.AlertTriangle,
                text = stringResource(
                    Res.string.player_video_mpv_config_dropped_keys,
                    ownership.confKeysDroppedByParser.sorted().joinToString(),
                ),
                tint = errorColor,
            )
        }
    }
}

/**
 * One icon + text case row inside [MpvConfigOwnershipNotice]; [tint] drives
 * both so severity reads at a glance (variant = informational, tertiary =
 * config-conflict warning, error = broken config).
 */
@Composable
private fun MpvConfigCaseRow(
    icon: ImageVector,
    text: String,
    tint: Color,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = tint,
        )
    }
}

/** Monospace pill for one user-owned `sub-*` key — config-literal styling. */
@Composable
private fun MpvConfigKeyPill(
    key: String,
    contentColor: Color,
    borderColor: Color,
) {
    Text(
        key,
        style = MaterialTheme.typography.labelSmall.copy(
            fontFamily = FontFamily.Monospace,
        ),
        color = contentColor,
        modifier = Modifier
            .clip(ShapeCache.smoothPill)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
            .border(1.dp, borderColor, ShapeCache.smoothPill)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun ColorChip(
    color: Color,
    isSelected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val isTv = LocalTvMode.current
    val alpha = if (enabled) 1f else 0.4f
    val focusState = rememberTvFocusState(focusedScale = 1.15f)
    Box(
        modifier = Modifier
            .size(if (isTv) 40.dp else 34.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha * color.alpha))
            .then(
                if (isSelected && enabled) {
                    Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                } else {
                    Modifier.border(1.dp, Color.Gray.copy(alpha = 0.5f * alpha), CircleShape)
                }
            )
            .then(focusState.focusModifier)
            .tvFocusIndicator(focusState, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
    )
}

/**
 * Bold/Italic toggle for subtitle typography. Uses a FilterChip so D-pad focus
 * mirrors the existing Edge Type / Border Style chips.
 */
@Composable
private fun TextStyleToggle(
    label: String,
    selected: Boolean,
    onChange: (Boolean) -> Unit,
) = SubtitleStyleChip(label = label, selected = selected, onClick = { onChange(!selected) })

/**
 * ASS Override mode chip (Respect = SCALE, Force = FORCE). Mirrors [TextStyleToggle] focus handling.
 */
@Composable
private fun AssOverrideChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) = SubtitleStyleChip(label = label, selected = isSelected, onClick = onClick)

/**
 * Shared FilterChip styling for the subtitle-style picker chips (text-style
 * toggles, ASS-override mode, edge type, border style, presets row).
 * Collapses the byte-identical focusState + FilterChip config (pill shape,
 * primary-tinted selected container/label, transparent border with primary
 * selected border, TV focus indicator) that was previously copy-pasted across
 * four call sites. Internal so [SubtitleStylePresetsRow] reuses the idiom.
 */
@Composable
internal fun SubtitleStyleChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val focusState = rememberTvFocusState(focusedScale = 1.05f)
    FilterChip(
        selected = selected,
        enabled = enabled,
        onClick = onClick,
        label = {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            )
        },
        shape = ShapeCache.smoothPill,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
            selectedLabelColor = MaterialTheme.colorScheme.primary,
        ),
        border = FilterChipDefaults.filterChipBorder(
            borderColor = Color.Transparent,
            selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
            enabled = enabled,
            selected = selected,
        ),
        modifier = Modifier
            .then(focusState.focusModifier)
            .then(Modifier.tvFocusIndicator(focusState, ShapeCache.smoothPill)),
    )
}

