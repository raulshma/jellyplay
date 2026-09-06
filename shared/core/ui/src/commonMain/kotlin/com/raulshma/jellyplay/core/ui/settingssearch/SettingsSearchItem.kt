package com.raulshma.jellyplay.core.ui.settingssearch

import androidx.compose.ui.graphics.vector.ImageVector
import com.raulshma.jellyplay.core.model.PlatformKind
import com.raulshma.jellyplay.core.ui.navigation.Route
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

data class SettingsSearchItem(
    val id: String,
    val titleRes: StringResource,
    val subtitleRes: StringResource,
    val categoryRes: StringResource,
    val keywords: List<String>,
    val route: Route,
    val icon: ImageVector,
    val isAdvanced: Boolean = false,
    /**
     * The platforms whose settings surface offers this item's row. Defaults
     * to all; narrow it when the row is platform-gated so the item never
     * surfaces as a stale search hit on a platform where the row cannot
     * exist. TV-only rows stay tagged for ANDROID — form factor is the
     * runtime `LocalTvMode` axis, not a platform.
     */
    val platforms: Set<PlatformKind> = PlatformKind.entries.toSet(),
)

/**
 * Drop items the given platform does not offer. Pure so both platforms of the
 * catalog are testable from a single JVM; the catalog applies it once at
 * aggregation with [com.raulshma.jellyplay.core.model.currentPlatform].
 */
fun List<SettingsSearchItem>.filterFor(platform: PlatformKind): List<SettingsSearchItem> =
    filter { platform in it.platforms }

/**
 * Plain-[String] projection of a [SettingsSearchItem] used for fuzzy matching and rendering.
 *
 * The catalog stores [org.jetbrains.compose.resources.StringResource]s so the UI resolves them
 * lazily through the current locale;
 * matching and display both operate on these already-resolved [title]/[subtitle]/[category]
 * strings. The original [item] is carried along so callers can navigate by `route`, read
 * `isAdvanced`, etc.
 */
data class ResolvedSettingsItem(
    val item: SettingsSearchItem,
    val title: String,
    val subtitle: String,
    val category: String,
) {
    val id: String get() = item.id
    val route: Route get() = item.route
    val icon: ImageVector get() = item.icon
    val isAdvanced: Boolean get() = item.isAdvanced
}

/**
 * Resolve a list of [SettingsSearchItem]s into [ResolvedSettingsItem]s via the suspend
 * [getString] (Compose Resources — works outside composition, e.g. in ViewModel flow
 * collectors). Resolution is the single place that turns resource references into
 * locale-aware text, so both search matching and rendering share one localized snapshot.
 */
suspend fun List<SettingsSearchItem>.resolve(): List<ResolvedSettingsItem> =
    map { item ->
        ResolvedSettingsItem(
            item = item,
            title = getString(item.titleRes),
            subtitle = getString(item.subtitleRes),
            category = getString(item.categoryRes),
        )
    }
