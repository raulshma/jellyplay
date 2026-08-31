package com.raulshma.jellyplay.core.ui.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.raulshma.jellyplay.core.designsystem.theme.JellyPlayTheme
import com.raulshma.jellyplay.core.designsystem.theme.ThemeVariant
import com.raulshma.jellyplay.core.model.MainPreferences
import com.raulshma.jellyplay.core.model.ThemeMode
import kotlinx.coroutines.delay
import java.util.Calendar

/**
 * Derives the effective dark-theme flag from [preferences] honouring the user's
 * [ThemeMode] (DARK / LIGHT / SYSTEM / SCHEDULED) and the synthwave override.
 *
 * SCHEDULED re-evaluates on a 60s tick (the [LaunchedEffect] below) so a crossing
 * of the configured hour boundary flips the theme without a relaunch.
 *
 * Extracted from `MainActivity` so the dedicated `PlayerActivity` (fullscreen
 * video host) resolves dark mode identically. Read once and pass the result to
 * [JellyPlayPreferenceTheme] (and to any window-chrome call site that needs it,
 * e.g. `enableEdgeToEdge`), so the derivation runs a single time per recomposition.
 */
@Composable
fun rememberPreferenceDarkTheme(preferences: MainPreferences): Boolean {
    val isSystemDark = isSystemInDarkTheme()
    var themeClockTick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(preferences.themeMode) {
        if (preferences.themeMode == ThemeMode.SCHEDULED) {
            while (true) {
                delay(60_000L)
                themeClockTick = System.currentTimeMillis()
            }
        }
    }
    return remember(preferences, isSystemDark) {
        derivedStateOf {
            // Dark-locked variants (synthwave, aurora) only read against dark tones.
            // Parse through ThemeVariant.fromId so a non-canonical casing stored
            // by an old build still resolves the same way JellyPlayTheme does.
            ThemeVariant.fromId(preferences.themeVariant).isDarkLocked || when (preferences.themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> isSystemDark
                ThemeMode.SCHEDULED -> {
                    // Reading themeClockTick here makes derivedStateOf recompute
                    // on the 60s tick above, so an hour-boundary crossing flips
                    // the theme without a relaunch. Base the hour on the tick's
                    // captured time so the read is both tracked and used (and
                    // non-scheduled modes don't recompute every tick).
                    val hour = Calendar.getInstance()
                        .apply { if (themeClockTick > 0) timeInMillis = themeClockTick }
                        .get(Calendar.HOUR_OF_DAY)
                    val start = preferences.scheduledThemeStartHour
                    val end = preferences.scheduledThemeEndHour
                    if (start > end) {
                        hour >= start || hour < end
                    } else {
                        hour >= start && hour < end
                    }
                }
            }
        }.value
    }
}

/**
 * The full preference-driven UI theme stack used by the app's Activities:
 * [JellyPlayTheme] (Material colors/motion/typography from the user's appearance
 * prefs) → motion/performance [CompositionLocalProvider] → [HandModeProvider] →
 * [BlueLightFilterBox] → [colorBlindFilter] modifier → [content].
 *
 * Extracted from `MainActivity`'s inline stack so the separate `PlayerActivity`
 * (fullscreen video host) themes the player identically without duplicating the
 * ~40-line wrapper. [darkTheme] is computed by the caller via
 * [rememberPreferenceDarkTheme] so it can also feed window-chrome setup.
 */
@Composable
fun JellyPlayPreferenceTheme(
    preferences: MainPreferences,
    darkTheme: Boolean,
    isTv: Boolean = false,
    content: @Composable () -> Unit,
) {
    JellyPlayTheme(
        darkTheme = darkTheme,
        dynamicColor = preferences.theme.dynamicTheming,
        oledMode = preferences.theme.oledMode,
        contrastLevel = preferences.contrastLevel,
        isTv = isTv,
        performanceMode = preferences.performanceMode,
        reduceMotion = preferences.reduceMotionEnabled,
        accentColorSwatch = preferences.theme.accentColorSwatch,
        colorStyle = preferences.theme.colorStyle,
        themeVariant = preferences.themeVariant,
        synthwaveAccent = preferences.synthwaveAccent,
        soothingAccent = preferences.soothingAccent,
        vividAccent = preferences.vividAccent,
        auroraAccent = preferences.auroraAccent,
        sakuraAccent = preferences.sakuraAccent,
        vectorPopAccent = preferences.vectorPopAccent,
        appFontScale = preferences.appFontScale,
    ) {
        // Provide the motion/performance flags to the whole UI subtree in one place.
        // JellyPlayTheme already uses these to pick its MotionScheme; providing them as
        // CompositionLocals lets non-scheme animations (infinite loops, bespoke effects)
        // honor both "Performance Mode" and "Reduce Motion" via LocalReducedMotion.
        CompositionLocalProvider(
            LocalPerformanceMode provides preferences.performanceMode,
            LocalReduceMotionEnabled provides preferences.reduceMotionEnabled,
            LocalReducedMotion provides (preferences.performanceMode || preferences.reduceMotionEnabled),
        ) {
            HandModeProvider(mode = preferences.handMode) {
                BlueLightFilterBox(
                    enabled = preferences.blueLightFilterEnabled,
                    strength = preferences.blueLightFilterStrength,
                ) {
                    Box(modifier = Modifier.colorBlindFilter(preferences.colorBlindMode)) {
                        content()
                    }
                }
            }
        }
    }
}
