package com.raulshma.jellyplay.feature.settings

import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.AdjustmentsHorizontal
import com.composables.icons.tabler.outline.Apps
import com.composables.icons.tabler.outline.ArrowDown
import com.composables.icons.tabler.outline.ArrowUp
import com.composables.icons.tabler.outline.Book
import com.composables.icons.tabler.outline.Book2
import com.composables.icons.tabler.outline.Database
import com.composables.icons.tabler.outline.LayoutGrid
import com.composables.icons.tabler.outline.Movie
import com.composables.icons.tabler.outline.Palette
import com.composables.icons.tabler.outline.PlayerPlay
import com.composables.icons.tabler.outline.Playlist
import com.composables.icons.tabler.outline.Refresh
import com.composables.icons.tabler.outline.Search
import com.composables.icons.tabler.outline.Upload
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_ns_books_desc
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_ns_books_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_ns_cw_desc
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_ns_cw_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_ns_homelayout_desc
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_ns_homelayout_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_ns_integrations_desc
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_ns_integrations_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_ns_itemprefs_desc
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_ns_itemprefs_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_ns_playlists_desc
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_ns_playlists_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_ns_prefs_desc
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_ns_prefs_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_ns_reader_desc
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_ns_reader_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_ns_search_desc
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_ns_search_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_ns_ui_desc
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_ns_ui_title
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_op_pull
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_op_push
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_op_reset
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_op_unknown
import org.jetbrains.compose.resources.StringResource

/**
 * The sync screen's presentation DECISIONS as plain (non-composable) folds —
 * the log-line-parse precedent: the table lives beside a JVM test, and
 * [JellyPlaySyncScreen] only maps each verdict to its resolved string /
 * drawn icon. Folds return resource ids and raw strings, never resolved
 * strings.
 */

/**
 * One sync namespace's declared face: the title + description RESOURCES and
 * the row icon. [fallbackTitle] is non-empty only on the unknown-namespace
 * arm (the raw key, title-cased), where both resources are null.
 */
internal data class SyncNamespaceFace(
    val titleRes: StringResource?,
    val descriptionRes: StringResource?,
    val icon: ImageVector,
    val fallbackTitle: String = "",
)

/**
 * The 11-namespace presentation table (the selective-sync + usage rows):
 * each known `ns` maps to its title/description resources and icon;
 * case-insensitive (the server's payload is expected lowercase, a stray
 * uppercase must not lose the face). An unknown namespace folds to the
 * generic database icon with the raw key title-cased — a server reporting a
 * namespace this client doesn't know still renders honestly.
 *
 * Declared delta vs the former composable `when`: none — every arm is
 * byte-identical, only lifted out of composition.
 */
internal fun syncNamespaceFace(ns: String): SyncNamespaceFace = when (ns.lowercase()) {
    "prefs" -> SyncNamespaceFace(
        titleRes = Res.string.settings_jellyplay_sync_ns_prefs_title,
        descriptionRes = Res.string.settings_jellyplay_sync_ns_prefs_desc,
        icon = Tabler.Outline.AdjustmentsHorizontal,
    )
    "books" -> SyncNamespaceFace(
        titleRes = Res.string.settings_jellyplay_sync_ns_books_title,
        descriptionRes = Res.string.settings_jellyplay_sync_ns_books_desc,
        icon = Tabler.Outline.Book,
    )
    "search" -> SyncNamespaceFace(
        titleRes = Res.string.settings_jellyplay_sync_ns_search_title,
        descriptionRes = Res.string.settings_jellyplay_sync_ns_search_desc,
        icon = Tabler.Outline.Search,
    )
    "cw" -> SyncNamespaceFace(
        titleRes = Res.string.settings_jellyplay_sync_ns_cw_title,
        descriptionRes = Res.string.settings_jellyplay_sync_ns_cw_desc,
        icon = Tabler.Outline.PlayerPlay,
    )
    "reader" -> SyncNamespaceFace(
        titleRes = Res.string.settings_jellyplay_sync_ns_reader_title,
        descriptionRes = Res.string.settings_jellyplay_sync_ns_reader_desc,
        icon = Tabler.Outline.Book2,
    )
    "homelayout" -> SyncNamespaceFace(
        titleRes = Res.string.settings_jellyplay_sync_ns_homelayout_title,
        descriptionRes = Res.string.settings_jellyplay_sync_ns_homelayout_desc,
        icon = Tabler.Outline.LayoutGrid,
    )
    "integrations" -> SyncNamespaceFace(
        titleRes = Res.string.settings_jellyplay_sync_ns_integrations_title,
        descriptionRes = Res.string.settings_jellyplay_sync_ns_integrations_desc,
        icon = Tabler.Outline.Apps,
    )
    "itemprefs" -> SyncNamespaceFace(
        titleRes = Res.string.settings_jellyplay_sync_ns_itemprefs_title,
        descriptionRes = Res.string.settings_jellyplay_sync_ns_itemprefs_desc,
        icon = Tabler.Outline.Movie,
    )
    "playlists" -> SyncNamespaceFace(
        titleRes = Res.string.settings_jellyplay_sync_ns_playlists_title,
        descriptionRes = Res.string.settings_jellyplay_sync_ns_playlists_desc,
        icon = Tabler.Outline.Playlist,
    )
    "ui" -> SyncNamespaceFace(
        titleRes = Res.string.settings_jellyplay_sync_ns_ui_title,
        descriptionRes = Res.string.settings_jellyplay_sync_ns_ui_desc,
        icon = Tabler.Outline.Palette,
    )
    else -> SyncNamespaceFace(
        titleRes = null,
        descriptionRes = null,
        icon = Tabler.Outline.Database,
        fallbackTitle = ns.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
    )
}

/**
 * One namespace row's factual line, DECIDED (which parts appear, in which
 * order) without resolving a string: the sync-off hints (master gate beats
 * per-namespace toggle), the pending count, the description, and the usage.
 * The screen maps each part to its resource; [SyncNamespaceSubtitlePart.Usage]
 * rides the [keysCountText] plural fold.
 */
internal sealed interface SyncNamespaceSubtitlePart {
    /** The master sync toggle is off — the row's own state is moot. */
    data object MasterOffHint : SyncNamespaceSubtitlePart

    /** This namespace's selective-sync toggle is off. */
    data object NamespaceOff : SyncNamespaceSubtitlePart

    /** Keys waiting to push. */
    data class Pending(val count: Int) : SyncNamespaceSubtitlePart

    /** The server-recorded usage (rendered through the singular/plural fold). */
    data class Usage(val keys: Int, val bytes: Long) : SyncNamespaceSubtitlePart

    /** The namespace's description copy (already resolved by the screen). */
    data class Description(val text: String) : SyncNamespaceSubtitlePart
}

/**
 * The subtitle fold's decision half — the verbatim gate order of the former
 * composable's `buildList`:
 * master-off hint, else the per-namespace off note; then pending (master on
 * only); then the non-blank description; then the usage line when the
 * namespace carries any keys or bytes.
 */
internal fun syncNamespaceSubtitleParts(
    namespace: JellyPlaySyncNamespaceRow,
    description: String,
    masterSyncEnabled: Boolean,
): List<SyncNamespaceSubtitlePart> = buildList {
    if (!masterSyncEnabled) {
        add(SyncNamespaceSubtitlePart.MasterOffHint)
    } else if (namespace.toggleable && !namespace.enabled) {
        add(SyncNamespaceSubtitlePart.NamespaceOff)
    }
    if (namespace.pending > 0 && masterSyncEnabled) {
        add(SyncNamespaceSubtitlePart.Pending(namespace.pending))
    }
    if (description.isNotBlank()) {
        add(SyncNamespaceSubtitlePart.Description(description))
    }
    if (namespace.keys > 0 || namespace.bytes > 0) {
        add(SyncNamespaceSubtitlePart.Usage(namespace.keys, namespace.bytes))
    }
}

/**
 * The wire ops' declared faces: icon + label resource keyed by the op string
 * (rows own their faces, per wire op). Unknown ops get the generic arrow +
 * "unknown" label via [syncOpFace].
 */
internal val SyncOpFaces: Map<String, Pair<ImageVector, StringResource>> = mapOf(
    "push" to (Tabler.Outline.Upload to Res.string.settings_jellyplay_sync_op_push),
    "pull" to (Tabler.Outline.ArrowDown to Res.string.settings_jellyplay_sync_op_pull),
    "reset" to (Tabler.Outline.Refresh to Res.string.settings_jellyplay_sync_op_reset),
)

/** The op's face — case-insensitive; unknown ops get the generic arrow + "unknown" label. */
internal fun syncOpFace(op: String): Pair<ImageVector, StringResource> =
    SyncOpFaces[op.lowercase()] ?: (Tabler.Outline.ArrowUp to Res.string.settings_jellyplay_sync_op_unknown)
