package com.raulshma.jellyplay.core.ui.model

import androidx.annotation.StringRes
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderKind
import com.raulshma.jellyplay.core.ui.R

/**
 * `@StringRes Int` half of the subtitle-provider label table. The `@Composable`
 * half lives in `shared/core/ui` over Compose Resources; this id-based variant
 * stays for legacy consumers and dies at cutover (plan §Phase X).
 */

/** Display-name resource for this subtitle provider (e.g. "Jellyfin", "Wyzie"). */
@StringRes
fun SubtitleProviderKind.displayNameRes(): Int = when (this) {
    SubtitleProviderKind.JELLYFIN -> R.string.core_subtitle_provider_jellyfin
    SubtitleProviderKind.WYZIE -> R.string.core_subtitle_provider_wyzie
    SubtitleProviderKind.OPENSUBTITLES -> R.string.core_subtitle_provider_opensubtitles
}
