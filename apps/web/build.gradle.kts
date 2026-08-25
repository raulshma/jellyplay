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
    // wasm network seam (W.1 chunks 1-3) is wired here; the Coil wasm image
    // engine (W.4) is blocked on the pinned coil version (see the W.4
    // dependency note in wasmJsMain below). Real screens are later slices.
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

                // Phase W.4 (Coil wasm image engine) BLOCKED at the pinned
                // coil 3.5.0 — NOT a resolution gap: coil-compose and
                // coil-network-ktor3 both ship wasmJs variants at 3.5.0 and
                // resolve cleanly onto wasmJsCompileClasspath. The blocker
                // is klib ABI: 3.5.0's wasmJs klibs are built with Kotlin
                // 2.4.0 ("KLIB loader: Incompatible ABI version 2.4.0"),
                // which this repo's Kotlin 2.3.21 compiler silently skips —
                // every coil3 reference stays unresolved. Desktop is
                // unaffected (JVM classpath metadata tolerates +1 minor).
                // NOT unblocked unilaterally (version pins are a maintainer
                // call); the two ways out: pin coil 3.4.0 (last
                // Kotlin-2.3-built line) or move the toolchain to 2.4.x.
                // Wiring recipe for when it lands: coil-compose +
                // coil-network-ktor3 (coordinate fixed in libs.versions.toml
                // — the old coil-network-ktor name was renamed upstream
                // before 3.0.0 stable) + ktor-client-js, then
                // setSingletonImageLoaderFactory in Main.kt with
                // KtorNetworkFetcherFactory(HttpClient(Js)) — see the
                // comment placeholder in Main.kt.

                implementation(libs.koin.core)
                implementation(libs.kotlinx.coroutines.core)
                // ComposeViewport(document.body) + localStorage-backed
                // storage interop.
                implementation(libs.kotlinx.browser)
            }
        }
    }
}
