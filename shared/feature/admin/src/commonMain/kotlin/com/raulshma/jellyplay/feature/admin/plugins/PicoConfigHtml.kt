package com.raulshma.jellyplay.feature.admin.plugins

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * Pure halves of the plugin-config WebView screen (the `PluginBridgeScript`
 * precedent, second edition): the Compose-color → Pico-CSS-variable injection
 * table and the HTML wrapper that loads the bridge script + Pico stylesheet
 * ahead of a plugin page's own scripts. Both are plain Color/String → String
 * builders with no Android API, so they live in commonMain where the jvmTest
 * lane can pin them; the androidMain `PluginConfigScreen.kt` keeps only the
 * WebView wiring and calls these.
 */

internal fun colorToHex(color: Color): String {
    val argb = color.toArgb()
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    return "#%02x%02x%02x".format(r, g, b)
}

internal fun buildPicoOverrides(isDark: Boolean, colors: ColorScheme): String {
    val bg = colorToHex(colors.background)
    val onBg = colorToHex(colors.onBackground)
    val surface = colorToHex(colors.surface)
    val surfaceVariant = colorToHex(colors.surfaceVariant)
    val onSurfaceVariant = colorToHex(colors.onSurfaceVariant)
    val outline = colorToHex(colors.outline)
    val outlineVariant = colorToHex(colors.outlineVariant)
    val primary = colorToHex(colors.primary)
    val primaryContainer = colorToHex(colors.primaryContainer)
    val onPrimary = colorToHex(colors.onPrimary)
    val onPrimaryContainer = colorToHex(colors.onPrimaryContainer)
    val secondary = colorToHex(colors.secondary)
    val secondaryContainer = colorToHex(colors.secondaryContainer)
    val error = colorToHex(colors.error)

    return """
        :root {
          --pico-background-color: $bg;
          --pico-color: $onBg;
          --pico-primary: $primary;
          --pico-primary-background: $primary;
          --pico-primary-border: $primary;
          --pico-primary-hover: $onPrimaryContainer;
          --pico-primary-hover-background: $primaryContainer;
          --pico-primary-hover-border: $primaryContainer;
          --pico-primary-inverse: $onPrimary;
          --pico-primary-underline: ${primary}80;
          --pico-primary-focus: ${primary}60;
          --pico-muted-color: $onSurfaceVariant;
          --pico-muted-border-color: $outlineVariant;
          --pico-secondary: $secondary;
          --pico-secondary-background: $secondary;
          --pico-secondary-hover: $secondaryContainer;
          --pico-secondary-inverse: $onPrimary;
          --pico-contrast: $onBg;
          --pico-contrast-background: $surfaceVariant;
          --pico-contrast-inverse: $onBg;
          --pico-h1-color: $onBg;
          --pico-h2-color: $onBg;
          --pico-h3-color: $onBg;
          --pico-h4-color: $onBg;
          --pico-h5-color: $onBg;
          --pico-h6-color: $onBg;
          --pico-table-border-color: $outlineVariant;
          --pico-table-row-stripped-background-color: ${surfaceVariant}18;
          --pico-card-background-color: $surface;
          --pico-card-border-color: $outlineVariant;
          --pico-card-sectioning-background-color: $surfaceVariant;
          --pico-form-element-background-color: $surface;
          --pico-form-element-border-color: $outline;
          --pico-form-element-color: $onBg;
          --pico-form-element-placeholder-color: $onSurfaceVariant;
          --pico-form-element-active-background-color: $surfaceVariant;
          --pico-form-element-active-border-color: $primary;
          --pico-form-element-focus-color: $primary;
          --pico-code-background-color: $surfaceVariant;
          --pico-code-color: $onSurfaceVariant;
          --pico-blockquote-border-color: $outlineVariant;
          --pico-blockquote-footer-color: $onSurfaceVariant;
          --pico-mark-background-color: $primaryContainer;
          --pico-mark-color: $onPrimaryContainer;
          --pico-ins-color: ${if (isDark) "#62af9a" else "#1c6954"};
          --pico-del-color: $error;
          --pico-progress-background-color: $surfaceVariant;
          --pico-progress-color: $primary;
          --pico-accordion-border-color: $outlineVariant;
          --pico-accordion-active-summary-color: $primary;
          --pico-switch-background-color: $outline;
          --pico-switch-checked-background-color: $primary;
          --pico-switch-color: $onPrimary;
          --pico-dropdown-background-color: $surface;
          --pico-dropdown-border-color: $outlineVariant;
          --pico-dropdown-color: $onBg;
          --pico-dropdown-hover-background-color: $surfaceVariant;
          --pico-modal-overlay-background-color: ${bg}BF;
          --pico-tooltip-background-color: $onBg;
          --pico-tooltip-color: $bg;
          --pico-border-radius: 0.75rem;
        }
    """.trimIndent()
}

/**
 * Wraps the plugin's HTML so that the bridge script and Pico CSS theme load
 * before the page's own scripts. The [bridgeScript] is injected at the very top
 * of `<head>` so `window.ApiClient`/`window.Dashboard` are defined before any
 * inline `<script>` runs (Pattern-A pages read these globals at load time).
 */
internal fun buildWrappedHtml(
    originalHtml: String,
    theme: String,
    overrides: String,
    bridgeScript: String,
): String {
    val picoStylesheet = """
        <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/@picocss/pico@2/css/pico.min.css">
        <style>$overrides</style>
    """.trimIndent()

    val hasHead = originalHtml.contains("<head", ignoreCase = true)
    val hasBody = originalHtml.contains("<body", ignoreCase = true)
    val hasHtml = originalHtml.contains("<html", ignoreCase = true)

    if (hasHtml) {
        return originalHtml
            .replace(
                Regex("(<html[^>]*?)>", RegexOption.IGNORE_CASE),
                "$1 data-theme=\"$theme\">",
            )
            .replace(
                Regex("(<head[^>]*?>)", RegexOption.IGNORE_CASE),
                "$1<script>$bridgeScript</script>",
            )
            .replace(
                Regex("(</head>)", RegexOption.IGNORE_CASE),
                "$picoStylesheet\n</head>",
            )
    }

    val picoHead = """
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <meta name="color-scheme" content="light dark">
        <script>$bridgeScript</script>
        $picoStylesheet
    """.trimIndent()

    if (hasHead && hasBody) {
        return originalHtml.replace(
            Regex("(<head[^>]*?>)", RegexOption.IGNORE_CASE),
            "$1$picoHead\n",
        )
    }

    if (hasBody) {
        return originalHtml.replace(
            Regex("(<body[^>]*>)", RegexOption.IGNORE_CASE),
            "$1\n$picoHead",
        )
    }

    return """<!DOCTYPE html>
        |<html lang="en" data-theme="$theme">
        |<head>$picoHead</head>
        |<body><main class="container">$originalHtml</main></body>
        |</html>""".trimMargin()
}
