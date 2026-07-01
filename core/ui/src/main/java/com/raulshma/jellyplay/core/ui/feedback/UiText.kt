package com.raulshma.jellyplay.core.ui.feedback

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.res.stringResource

/**
 * Localizable, user-facing text.
 *
 * The app standardizes on this type so that every user-visible string can be
 * resolved from Android string resources — the prerequisite for real
 * localization. Two variants:
 *
 * - [UiText.Resource] references a `strings.xml` entry (with optional format
 *   args) and is resolved lazily by the UI layer, so it stays correct under
 *   locale changes.
 * - [UiText.Raw] wraps an already-resolved [String] for genuinely dynamic
 *   content that cannot be a resource (e.g. a server-supplied message or a
 *   thrown exception's `localizedMessage`).
 *
 * ViewModels and other non-Composable producers build [UiText] without an
 * `AndroidContext`; the resolution happens at the edges — either via
 * [asString] in a Composable or [resolve] where a [Context] is available
 * (e.g. a Toast host).
 */
@Stable
sealed interface UiText {

    /** Already-resolved, non-localizable text. */
    @Immutable
    data class Raw(val value: String) : UiText

    /**
     * A string resource with optional printf-style format args. Args may
     * themselves be [UiText] instances (resolved recursively) so composed
     * messages can mix localizable fragments; plain [String]s and other values
     * are formatted as-is.
     */
    @Immutable
    data class Resource(@param:StringRes val resId: Int, val args: List<Any> = emptyList()) : UiText
}

/** Build a [UiText.Resource] from a string resource id and optional format args. */
fun uiTextOf(@StringRes resId: Int, vararg args: Any): UiText =
    UiText.Resource(resId, args.toList())

/** Wrap a raw [String] as [UiText] for ergonomic call sites that mix both. */
fun String.asUiText(): UiText = UiText.Raw(this)

/**
 * Resolve this [UiText] to a concrete [String] using [context]. Used by
 * non-Composable hosts (e.g. the TV Toast renderer); Composables should
 * prefer [asString].
 */
fun UiText.resolve(context: Context): String = when (this) {
    is UiText.Raw -> value
    is UiText.Resource -> {
        val resolved = args.map { it.resolveArg(context) }
        if (resolved.isEmpty()) context.getString(resId)
        else context.getString(resId, *resolved.toTypedArray())
    }
}

/** Resolve this [UiText] inside a Composable via [stringResource]. */
@Composable
fun UiText.asString(): String = when (this) {
    is UiText.Raw -> value
    is UiText.Resource -> {
        val resolved = args.map { it.asArg() }
        if (resolved.isEmpty()) stringResource(resId)
        else stringResource(resId, *resolved.toTypedArray())
    }
}

private fun Any.resolveArg(context: Context): Any = when (this) {
    is UiText -> resolve(context)
    else -> this
}

@Composable
private fun Any.asArg(): Any = when (this) {
    is UiText -> asString()
    else -> this
}
