package com.raulshma.jellyplay.core.ui.message

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Localizable, user-facing text (v0.10.6 `core.ui.feedback.UiText` ported to
 * commonMain; lives in its own package so the legacy Android `:core:ui`
 * feedback classes and this shared set can coexist on androidMain classpaths).
 *
 * The app standardizes on this type so that every user-visible string can be
 * resolved from compose string resources — the prerequisite for real
 * localization. Two variants:
 *
 * - [UiText.Resource] references a string resource (with optional format
 *   args) and is resolved lazily by the UI layer, so it stays correct under
 *   locale changes.
 * - [UiText.Raw] wraps an already-resolved [String] for genuinely dynamic
 *   content that cannot be a resource (e.g. a server-supplied message).
 *
 * ViewModels and other non-Composable producers build [UiText] without a
 * platform context; resolution happens at the edges via [asString] in a
 * Composable (or `org.jetbrains.compose.resources.getString` where a
 * suspend context is available).
 */
@Stable
sealed interface UiText {

    /** Already-resolved, non-localizable text. */
    @Immutable
    data class Raw(val value: String) : UiText

    /**
     * A string resource with optional printf-style format args. Args must be
     * types compose-resources can format (String/Number); plain values are
     * formatted as-is.
     */
    @Immutable
    data class Resource(val res: StringResource, val args: List<Any> = emptyList()) : UiText
}

/** Build a [UiText.Resource] from a string resource and optional format args. */
fun uiTextOf(res: StringResource, vararg args: Any): UiText =
    UiText.Resource(res, args.toList())

/** Wrap a raw [String] as [UiText] for ergonomic call sites that mix both. */
fun String.asUiText(): UiText = UiText.Raw(this)

/** Resolve this [UiText] to a concrete [String] in composition. */
@Composable
fun UiText.asString(): String = when (this) {
    is UiText.Raw -> value
    is UiText.Resource -> stringResource(res, *args.toTypedArray())
}
