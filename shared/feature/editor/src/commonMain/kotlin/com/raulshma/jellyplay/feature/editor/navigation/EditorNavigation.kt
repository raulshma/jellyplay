package com.raulshma.jellyplay.feature.editor.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.raulshma.jellyplay.core.ui.navigation.Navigator
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.feature.editor.EditorScreen

fun EntryProviderScope<NavKey>.editorSection(
    navigator: Navigator,
) {
    entry<Route.MetadataEditor> { key ->
        EditorScreen(
            itemId = key.itemId,
            onBack = { navigator.goBack() },
        )
    }
}
