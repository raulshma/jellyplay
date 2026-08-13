package com.raulshma.jellyplay.feature.details

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.model.DownloadQuality

/**
 * The user's pre-download choices, gathered into one type so the three values
 * that always travel together — sheet visibility, quality, and subtitle
 * selection — move as a single unit through [DownloadLifecycleState],
 * [DetailUiState], and [DetailContentState] instead of being copied
 * field-by-field into each holder (the former trio caused shotgun surgery: a
 * fourth picker field meant editing five places).
 *
 * Subtitle intent is modelled by [SubtitleSelection] (not a nullable `Set<Int>`)
 * so "all / subset / none" is explicit and the picker no longer leans on a
 * null-vs-empty sentinel with a materialize-then-collapse dance.
 */
@Immutable
data class DownloadPickerState(
    val visible: Boolean = false,
    val quality: DownloadQuality = DownloadQuality.ORIGINAL,
    val subtitleSelection: SubtitleSelection = SubtitleSelection.All,
)

/**
 * Which external subtitles to bundle for the next download. Replaces the former
 * `Set<Int>?` sentinel where `null` meant "all" and an empty set meant "none":
 * the three cases are now distinct values, and the picker toggles between them
 * directly.
 *
 * [toIndexSet] projects to the `Set<Int>?` contract the data layer
 * ([com.raulshma.jellyplay.core.data.download.DownloadIntake]) still expects —
 * `null` means "every deliverable subtitle" — at the single VM→intake boundary,
 * so the sealed type stays a UI-layer concern.
 */
@Immutable
sealed interface SubtitleSelection {
    /** Bundle every deliverable external subtitle (the default). */
    data object All : SubtitleSelection

    /** Bundle exactly [indices]; an empty set is a valid "no subtitles" choice. */
    data class Subset(val indices: Set<Int>) : SubtitleSelection

    fun toIndexSet(): Set<Int>? = when (this) {
        All -> null
        is Subset -> indices
    }
}
