package com.raulshma.jellyplay.web

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.raulshma.jellyplay.core.datastore.di.datastoreCommonModule
import com.raulshma.jellyplay.core.datastore.di.webDatastoreModule
import com.raulshma.jellyplay.core.designsystem.theme.JellyPlayTheme
import kotlinx.browser.document
import org.koin.core.context.startKoin

/**
 * Phase W web shell entry (docs/kmp-migration-plan.md §Phase W): boots the
 * shared datastore DI stack on wasm and renders a minimal placeholder —
 * this is a boot-proof skeleton, not a UI. API wiring (Ktor client, Coil
 * wasm engine, network/data modules) lands in the W.1–W.4 slices.
 *
 * `ComposeViewport` is the current CMP web entry (it replaced the deprecated
 * `CanvasBasedWindow`); it renders into the document body, taking the full
 * viewport.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // DI first: everything composable resolves lazily through Koin, so the
    // container must exist before the first composition. Same module shape
    // as the desktop shell's startKoin, minus the jvm-only stacks.
    startKoin {
        modules(
            datastoreCommonModule,
            webDatastoreModule(),
        )
    }

    ComposeViewport(document.body!!) {
        JellyPlayTheme(darkTheme = isSystemInDarkTheme(), dynamicColor = false) {
            WebShellScreen()
        }
    }
}
