package com.raulshma.jellyplay.core.ui.model

import androidx.compose.runtime.Composable
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderKind
import com.raulshma.jellyplay.core.ui.generated.resources.Res
import com.raulshma.jellyplay.core.ui.generated.resources.core_subtitle_provider_jellyfin
import com.raulshma.jellyplay.core.ui.generated.resources.core_subtitle_provider_opensubtitles
import com.raulshma.jellyplay.core.ui.generated.resources.core_subtitle_provider_wyzie
import org.jetbrains.compose.resources.stringResource

/**
 * Display labels for [SubtitleProviderKind].
 *
 * The model enum itself has no resource access, so the provider name is resolved
 * at the UI layer. This is the single source of truth shared by the player
 * (`SubtitleManagerSheet`) and the metadata editor (`SubtitlesTab`).
 *
 * The `@StringRes Int` half stays in the legacy `:core:ui` shim until every
 * consumer has migrated off resource ids (plan §Phase X).
 */

/** Localized display name for this subtitle provider. */
@Composable
fun SubtitleProviderKind.localizedDisplayName(): String = stringResource(
    when (this) {
        SubtitleProviderKind.JELLYFIN -> Res.string.core_subtitle_provider_jellyfin
        SubtitleProviderKind.WYZIE -> Res.string.core_subtitle_provider_wyzie
        SubtitleProviderKind.OPENSUBTITLES -> Res.string.core_subtitle_provider_opensubtitles
    },
)
