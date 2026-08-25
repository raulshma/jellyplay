@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    // Phase W web shell (docs/kmp-migration-plan.md §Phase W): single
    // wasmJs/browser target proving the shared DI stacks on wasm. The Ktor
    // wasm network seam (W.1 chunks 1-3) is wired here; real screens are
    // later slices.
    wasmJs {
        browser {
            commonWebpackConfig {
                // Pin the loader name so src/wasmJsMain/resources/index.html
                // references a deterministic file (compile task is unaffected).
                outputFileName = "webapp.js"
            }
        }
    }

    sourceSets {
        val wasmJsMain by getting {
            dependencies {
                // JB CMP distribution (catalog note): the only publisher of
                // real wasm compose binaries.
                implementation(libs.jb.compose.runtime)
                implementation(libs.jb.compose.ui)
                implementation(libs.jb.compose.foundation)
                implementation(libs.jb.compose.material3)

                // Phase W stack: model (shared value types),
                // designsystem (JellyPlayTheme), datastore
                // (datastoreCommonModule + webDatastoreModule DI), network
                // (networkWasmModule — Phase W.1 chunk 3: AtomicSessionState
                // + WasmClientIdentity + the three Ktor wasm clients +
                // AuthApiClient/LibraryApiClient/PlaybackApiClient bindings).
                // Deliberately absent: :shared:core:ui (nav3/paging wasm
                // unverified), database/data (no Room on wasm v1).
                implementation(project(":shared:core:model"))
                implementation(project(":shared:core:designsystem"))
                implementation(project(":shared:core:datastore"))
                implementation(project(":shared:core:network"))

                implementation(libs.koin.core)
                implementation(libs.kotlinx.coroutines.core)
                // ComposeViewport(document.body) + localStorage-backed
                // storage interop.
                implementation(libs.kotlinx.browser)
            }
        }
    }
}
