package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * The shared/base remainder of the preference-model family. The per-domain
 * enums and aggregates live beside it in `PreferenceModels<Domain>.kt` /
 * `PreferenceGroups<Domain>.kt` (same package — the split is a pure
 * declaration move so consumer imports are unchanged); only the types with no
 * single domain stay here.
 */

/**
 * Marker for a preference enum (or other value) that carries a human-readable
 * [displayName]. Used by display helpers to render an enum's label without a
 * per-type `when` switch — anything implementing this is rendered via
 * [displayName], so newly added labeled enums are covered automatically.
 *
 * Note: `displayName` is an English stopgap, not an i18n mechanism. Enums
 * whose rows are user-facing localization surfaces resolve labels through the
 * `PreferenceEnumNames` seam in `core:ui` instead (and must not gain/keep a
 * `displayName` for them).
 */
interface HasDisplayName {
    val displayName: String
}

/**
 * Played-status filter for library browsing. Maps onto Jellyfin's
 * `ItemFilter` (`IS_PLAYED` / `IS_UNPLAYED`) — see `LibraryApiClientImpl`.
 * Lived in `core/model` so the data + UI layers share one definition.
 */
@Immutable
@Serializable
enum class PlayedStatus(val displayName: String) {
    ALL("All"),
    PLAYED("Played"),
    UNPLAYED("Unplayed"),
}
