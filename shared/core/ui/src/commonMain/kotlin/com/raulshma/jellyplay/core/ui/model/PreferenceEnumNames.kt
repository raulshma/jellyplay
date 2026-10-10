package com.raulshma.jellyplay.core.ui.model

import androidx.compose.runtime.Composable
import com.raulshma.jellyplay.core.model.CheckFrequency
import com.raulshma.jellyplay.core.model.GestureIndicatorSide
import com.raulshma.jellyplay.core.model.GestureMode
import com.raulshma.jellyplay.core.model.OrientationMode
import com.raulshma.jellyplay.core.ui.generated.resources.Res
import com.raulshma.jellyplay.core.ui.generated.resources.core_check_frequency_every_12_hours
import com.raulshma.jellyplay.core.ui.generated.resources.core_check_frequency_every_24_hours
import com.raulshma.jellyplay.core.ui.generated.resources.core_check_frequency_every_3_hours
import com.raulshma.jellyplay.core.ui.generated.resources.core_check_frequency_every_6_hours
import com.raulshma.jellyplay.core.ui.generated.resources.core_check_frequency_every_hour
import com.raulshma.jellyplay.core.ui.generated.resources.core_gesture_indicator_opposite
import com.raulshma.jellyplay.core.ui.generated.resources.core_gesture_indicator_same
import com.raulshma.jellyplay.core.ui.generated.resources.core_gesture_mode_all
import com.raulshma.jellyplay.core.ui.generated.resources.core_gesture_mode_none
import com.raulshma.jellyplay.core.ui.generated.resources.core_gesture_mode_tap_only
import com.raulshma.jellyplay.core.ui.generated.resources.core_orientation_locked_landscape
import com.raulshma.jellyplay.core.ui.generated.resources.core_orientation_locked_portrait
import com.raulshma.jellyplay.core.ui.generated.resources.core_orientation_sensor
import com.raulshma.jellyplay.core.ui.generated.resources.core_orientation_sensor_landscape
import com.raulshma.jellyplay.core.ui.generated.resources.core_orientation_sensor_portrait
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Localizable display labels for the enum-valued preference rows whose UI
 * used to render hardcoded English — or the raw persisted wire string
 * (`OrientationMode.constant`, e.g. "sensor_landscape") — next to an
 * already-localized subtitle on the very same row.
 *
 * The model enums carry no resource access (core:model cannot depend on
 * resources), so labels resolve here at the UI layer — the [SegmentNames]
 * pattern. This file (not feature:settings) is the ONE seam because the
 * rows span modules: the settings screens, the factory-reset/import-preview
 * diff (which formats values OUTSIDE composition, hence the `labelResource`
 * handles) and the onboarding orientation picker all render these enums, and
 * feature:onboarding has no dependency edge to feature:settings. A new enum
 * row joining the seam: add the `core_*` label resources (default locale),
 * a `labelResource()` + `localizedDisplayName()` pair here, an entry in
 * [preferenceEnumLabelResources], and route every renderer — picker labels,
 * trailing text, row summaries, diff values — through it.
 */

// region GestureMode

/** The localized label resource for this gesture mode (diff/picker label handle). */
fun GestureMode.labelResource(): StringResource = when (this) {
    GestureMode.ALL -> Res.string.core_gesture_mode_all
    GestureMode.TAP_ONLY -> Res.string.core_gesture_mode_tap_only
    GestureMode.NONE -> Res.string.core_gesture_mode_none
}

/** Localized display name for this gesture mode. */
@Composable
fun GestureMode.localizedDisplayName(): String = stringResource(labelResource())

// endregion

// region OrientationMode

/** The localized label resource for this orientation lock (diff/picker label handle). */
fun OrientationMode.labelResource(): StringResource = when (this) {
    OrientationMode.SENSOR_LANDSCAPE -> Res.string.core_orientation_sensor_landscape
    OrientationMode.SENSOR_PORTRAIT -> Res.string.core_orientation_sensor_portrait
    OrientationMode.SENSOR -> Res.string.core_orientation_sensor
    OrientationMode.LOCKED_LANDSCAPE -> Res.string.core_orientation_locked_landscape
    OrientationMode.LOCKED_PORTRAIT -> Res.string.core_orientation_locked_portrait
}

/** Localized display name for this orientation lock. */
@Composable
fun OrientationMode.localizedDisplayName(): String = stringResource(labelResource())

// endregion

// region GestureIndicatorSide

/** The localized label resource for this indicator side (diff/picker label handle). */
fun GestureIndicatorSide.labelResource(): StringResource = when (this) {
    GestureIndicatorSide.OPPOSITE -> Res.string.core_gesture_indicator_opposite
    GestureIndicatorSide.SAME -> Res.string.core_gesture_indicator_same
}

/** Localized display name for this indicator side. */
@Composable
fun GestureIndicatorSide.localizedDisplayName(): String = stringResource(labelResource())

// endregion

// region CheckFrequency

/** The localized label resource for this check frequency (diff/picker label handle). */
fun CheckFrequency.labelResource(): StringResource = when (this) {
    CheckFrequency.EVERY_HOUR -> Res.string.core_check_frequency_every_hour
    CheckFrequency.EVERY_3_HOURS -> Res.string.core_check_frequency_every_3_hours
    CheckFrequency.EVERY_6_HOURS -> Res.string.core_check_frequency_every_6_hours
    CheckFrequency.EVERY_12_HOURS -> Res.string.core_check_frequency_every_12_hours
    CheckFrequency.EVERY_24_HOURS -> Res.string.core_check_frequency_every_24_hours
}

/** Localized display name for this check frequency. */
@Composable
fun CheckFrequency.localizedDisplayName(): String = stringResource(labelResource())

// endregion

/**
 * Every enum-value label resource the seam can emit — the extra workload the
 * diff label table must pre-resolve beside its row labels (see
 * `PreferenceCategoryView.labelResources`), kept here so the seam file stays
 * the single enumeration of what it covers.
 */
val preferenceEnumLabelResources: List<StringResource> =
    GestureMode.entries.map(GestureMode::labelResource) +
        OrientationMode.entries.map(OrientationMode::labelResource) +
        GestureIndicatorSide.entries.map(GestureIndicatorSide::labelResource) +
        CheckFrequency.entries.map(CheckFrequency::labelResource)
