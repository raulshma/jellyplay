@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    // Phase W web shell (docs/kmp-migration-plan.md §Phase W): single
    // wasmJs/browser target — a boot-proof skeleton proving the shared
    // datastore DI stack on wasm. The Ktor wasm client (W.1) and Coil wasm
    // image engine (W.4) land in later slices.
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

                // Phase W skeleton stack: model (shared value types),
                // designsystem (JellyPlayTheme), datastore
                // (datastoreCommonModule + webDatastoreModule DI).
                // Deliberately absent: :shared:core:ui (nav3/paging wasm
                // unverified), network/database/data (no Room on wasm v1;
                // the Ktor seam lands with Phase W.1).
                implementation(project(":shared:core:model"))
                implementation(project(":shared:core:designsystem"))
                implementation(project(":shared:core:datastore"))

                implementation(libs.koin.core)
                implementation(libs.kotlinx.coroutines.core)
                // ComposeViewport(document.body) + localStorage-backed
                // storage interop.
                implementation(libs.kotlinx.browser)
            }
        }
    }
}
