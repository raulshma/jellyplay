package com.raulshma.jellyplay.core.ui.settingssearch

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import com.raulshma.jellyplay.core.ui.navigation.Route

data class SettingsSearchItem(
    val id: String,
    val titleRes: Int,
    val subtitleRes: Int,
    val categoryRes: Int,
    val keywords: List<String>,
    val route: Route,
    val icon: ImageVector,
    val isAdvanced: Boolean = false
)

/**
 * Plain-[String] projection of a [SettingsSearchItem] used for fuzzy matching and rendering.
 *
 * The catalog stores `@StringRes` ids so the UI resolves them lazily through the current locale;
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
 * Resolve a list of [SettingsSearchItem]s into [ResolvedSettingsItem]s using [resolve]
 * (typically `context::getString` or a Compose-backed resolver). Resolution is the single place
 * that turns resource ids into locale-aware text, so both search matching and rendering share one
 * localized snapshot.
 */
fun List<SettingsSearchItem>.resolve(@StringRes resolve: (Int) -> String): List<ResolvedSettingsItem> =
    map { item ->
        ResolvedSettingsItem(
            item = item,
            title = resolve(item.titleRes),
            subtitle = resolve(item.subtitleRes),
            category = resolve(item.categoryRes),
        )
    }
