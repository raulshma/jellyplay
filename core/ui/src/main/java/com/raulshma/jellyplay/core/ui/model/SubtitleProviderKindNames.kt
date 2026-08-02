package com.raulshma.jellyplay.core.ui.model

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderKind
import com.raulshma.jellyplay.core.ui.R

/**
 * Display labels for [SubtitleProviderKind].
 *
 * The model enum itself has no resource access, so the provider name is resolved
 * at the UI layer. This is the single source of truth shared by the player
 * (`SubtitleManagerSheet`) and the metadata editor (`SubtitlesTab`) — previously
 * each duplicated a `when(kind)` cascade, with the editor hardcoding English
 * instead of using string resources.
 *
 * Brand names are not translated; prefer [subtitleProviderDisplayName] inside
 * `@Composable` scope, or [subtitleProviderDisplayNameRes] when a `@StringRes`
 * `Int` is required (e.g. for a [com.raulshma.jellyplay.core.ui.feedback.UiText]).
 */

/** Display-name resource for this subtitle provider (e.g. "Jellyfin", "Wyzie"). */
@StringRes
fun SubtitleProviderKind.displayNameRes(): Int = when (this) {
    SubtitleProviderKind.JELLYFIN -> R.string.core_subtitle_provider_jellyfin
    SubtitleProviderKind.WYZIE -> R.string.core_subtitle_provider_wyzie
    SubtitleProviderKind.OPENSUBTITLES -> R.string.core_subtitle_provider_opensubtitles
}

/** Localized display name for this subtitle provider. */
@Composable
fun SubtitleProviderKind.localizedDisplayName(): String = stringResource(displayNameRes())
