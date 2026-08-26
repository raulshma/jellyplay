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
    // wasm network seam (W.1 chunks 1-3) is wired here and the Coil wasm
    // image engine (W.4) is wired since the repo-wide coil 3.4.0 pin
    // (libs.versions.toml version note): 3.5.0's wasmJs klibs are Kotlin-
    // 2.4-ABI and silently skipped by our 2.3.21 loader. Real screens are
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
                // Wave wC (HtmlVideoEngine): the wasm-visible MediaEngine
                // contract + EnginePositionTicker/WebPlaybackMappings the
                // web video engine implements. Not wired into the shell UI
                // yet — the engine class lands first.
                implementation(project(":shared:core:player-contract"))

                // Phase W.4 DONE (was BLOCKED at coil 3.5.0 whose wasmJs
                // klibs are Kotlin-2.4-ABI, unreadable by this repo's Kotlin
                // 2.3.21 klib loader). The pins moved to 3.4.0 — the last
                // Kotlin-2.3-built release line (Central's kotlin-tooling-
                // metadata.json reports buildPluginVersion 2.3.10 there,
                // 2.4.0 at 3.5.0) — so coil-compose resolves its real
                // coil-compose-wasm-js klib and the images pipeline compiles:
                //   https://repo1.maven.org/maven2/io/coil-kt/coil3/coil-compose-wasm-js/3.4.0/
                //   https://repo1.maven.org/maven2/io/coil-kt/coil3/coil-network-ktor3-wasm-js/3.4.0/
                // The ktor3 fetcher factory is registered EXPLICITLY inside
                // Main.kt's singleton loader (see the comment there for why
                // ServiceLoader does not cover us on wasmJs).
                implementation(libs.coil.compose)
                implementation(libs.coil.network.ktor3)
                // The Js engine for Main.kt's fetcher HttpClient(Js) — a
                // direct edge because ktor-client-js arrives here only as a
                // TRANSITIVE implementation dep of shared/core/network, which
                // does not leak onto our compile classpath.
                implementation(libs.ktor.client.js)

                implementation(libs.koin.core)
                implementation(libs.kotlinx.coroutines.core)
                // ComposeViewport(document.body) + localStorage-backed
                // storage interop.
                implementation(libs.kotlinx.browser)
            }
        }
    }
}
