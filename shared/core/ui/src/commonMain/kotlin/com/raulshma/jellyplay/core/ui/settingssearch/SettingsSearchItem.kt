package com.raulshma.jellyplay.core.ui.settingssearch

import androidx.compose.ui.graphics.vector.ImageVector
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
    val isAdvanced: Boolean = false
)

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
