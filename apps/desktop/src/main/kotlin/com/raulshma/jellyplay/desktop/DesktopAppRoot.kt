package com.raulshma.jellyplay.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.SavedStateConfiguration
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.navigation.rememberNavigationState
import kotlin.reflect.KClass
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

/**
 * Slice root: one nav stack, placeholder content until the V1c feature modules
 * (auth → home → details) register their NavDisplay entries here.
 */
@Composable
internal fun DesktopAppRoot(showAbout: Boolean, onDismissAbout: () -> Unit) {
    val navigation = rememberNavigationState(
        startRoute = Route.Home,
        topLevelRoutes = setOf(Route.Home),
        savedStateConfiguration = SavedStateConfiguration {
            // The sealed Route serializer handles every leaf; registering it as
            // the polymorphic default lets NavKey-scope lookups resolve any
            // Route subclass without enumerating ~100 leaves.
            serializersModule = SerializersModule {
                polymorphic(NavKey::class) {
                    // The sealed Route hierarchy enumerates its leaves via
                    // kotlin-reflect; new Route subclasses register themselves.
                    for (leaf in Route::class.sealedSubclasses) {
                        @Suppress("UNCHECKED_CAST")
                        val leafClass = leaf as KClass<NavKey>
                        @Suppress("UNCHECKED_CAST")
                        val leafSerializer =
                            serializer(leaf.java as Class<NavKey>) as KSerializer<NavKey>
                        subclass(leafClass, leafSerializer)
                    }
                }
            }
        },
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("JellyPlay desktop")
        Spacer(Modifier.height(8.dp))
        Text("Sign-in, home and details land with the V1c feature migration.")
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = onDismissAbout,
            confirmButton = { TextButton(onClick = onDismissAbout) { Text("Close") } },
            title = { Text("JellyPlay") },
            text = { Text("KMP desktop shell (Phase V1b). Android app unaffected.") },
        )
    }
}
