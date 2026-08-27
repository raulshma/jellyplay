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
    // 2.4-ABI and silently skipped by our 2.3.21 loader. Wave 11B adds the
    // real navigation shell (WebAppRoot over the JB fork's NavDisplay).
    // Wave 15C: first FEATURE screen — `entry<Route.Requests>` renders the
    // shared RequestsScreen (15B's wasm target) with the requests DI slice.
    wasmJs {
        browser {
            commonWebpackConfig {
                // Pin the loader name so src/wasmJsMain/resources/index.html
                // references a deterministic file (compile task is unaffected).
                outputFileName = "webapp.js"
            }
        }
        // Wave 13C: the main binary must be EXECUTABLE for webpack to produce
        // the servable bundle (build/kotlin-webpack/wasmJs/
        // developmentExecutable) the CDP verification lane
        // (tools/e2e/web-verify.mjs) drives. Without this only the
        // test-compilation executables exist and no
        // wasmJsBrowserDevelopmentWebpack task is registered at all.
        binaries.executable()
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
                // designsystem (JellyPlayTheme), ui (wave 11B: shared
                // composition locals + nav wiring; the wasm target is
                // machine-verified green since wave 11A), datastore
                // (datastoreCommonModule + webDatastoreModule DI), network
                // (networkWasmModule — Phase W.1 chunk 3: AtomicSessionState
                // + WasmClientIdentity + the three Ktor wasm clients +
                // AuthApiClient/LibraryApiClient/PlaybackApiClient bindings).
                // Deliberately absent: paging-compose (spike w-10C §1 proves
                // its 3.5.0 wasm klibs exist, but no consuming web module
                // needs LazyPagingItems yet) and database (no Room on wasm
                // v1 — core:data's wasm slice is the Room-free repository
                // layer, wired below).
                implementation(project(":shared:core:model"))
                implementation(project(":shared:core:designsystem"))
                implementation(project(":shared:core:ui"))
                implementation(project(":shared:core:datastore"))
                implementation(project(":shared:core:network"))
                // Wave 15C: dataWasmModule (the requests repo slice —
                // SeerrRepository/ArrRepository over the wasm clients) is
                // imported into Main.kt's startKoin, hence the direct edge.
                implementation(project(":shared:core:data"))
                // Wave 15C: the FIRST shared feature screen renders here —
                // WebAppRoot's `entry<Route.Requests>` composes
                // RequestsScreen (VM ctor deps: SeerrRepository +
                // ArrRepository from dataWasmModule above, ExperimentalStore
                // from datastoreCommonModule). Deliberately the only feature
                // edge: every other shared/feature module still has no
                // wasmJs target, and the KoinModuleRegistrationGuardTest's
                // web forward allowlist pins exactly this one registration.
                implementation(project(":shared:feature:requests"))
                // Wave 16C: the SECOND shared feature screen on web —
                // WebAppRoot's `entry<Route.SeerrDetail>` composes the shared
                // SeerrDetailScreen (details' wasmJs target; the MediaDetail
                // cluster stays jvmShared/off-web) and Main.kt registers
                // detailsModule + webDetailsPlatformModule (the narrow
                // MediaRepository for the SeerrDetail cross-link).
                implementation(project(":shared:feature:details"))
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
                // Wave 12C (WebConnectFlow): ktor-client-core is needed to
                // CLASSIFY transport failures typed there / in ktor-io
                // (HttpRequestTimeoutException, IOException) for the connect
                // form's error lines — the same taxonomy the wasm network
                // classifier uses. Direct edge for the same leak reason as
                // ktor-client-js above (implementation dep of core/network).
                implementation(libs.ktor.client.core)
                // Wave 16C (WebMediaRepositoryNarrow): PagingData appears in
                // MediaRepository's paged-member signatures — a direct edge
                // for the same leak reason as ktor-client-js above (paging-
                // common is an implementation dep of shared/core/data, so it
                // does not reach our compile classpath transitively). The
                // common-only artifact, no paging runtime enters the shell.
                implementation(libs.paging.common)

                implementation(libs.koin.core)
                // Wave 15C: the koin-compose runtime behind the requests
                // entry's `koinViewModel()` (RequestsScreen's default
                // parameter, compiled inside the feature klib). Explicit
                // edges rather than relying on the feature module's
                // transitive `implementation` deps reaching the wasm link —
                // apps/desktop states the same policy (its line ~170: feature
                // screens bring their own koin-compose edges). Both
                // artifacts are ABI-safe on this repo's Kotlin 2.3.21 loader
                // (4.2.2 wasm klibs require only stdlib 2.3.20; verified in
                // the wave 15B spike before the catalog entries landed).
                implementation(libs.koin.compose)
                implementation(libs.koin.compose.viewmodel)
                implementation(libs.kotlinx.coroutines.core)
                // Web nav root (WebAppRoot, DesktopAppRoot's pattern): NavKey
                // is public API surface of shared/core/ui's navigation
                // helpers; NavDisplay + entryDecorator wiring need the -ui
                // artifact alongside. Both edges resolve against google's
                // coordinates — the substitution below swaps only -ui onto
                // the JetBrains fork.
                implementation(libs.navigation3.runtime)
                implementation(libs.navigation3.ui)
                // ComposeViewport(document.body) + localStorage-backed
                // storage interop.
                implementation(libs.kotlinx.browser)
            }
        }
    }
}

// google's androidx.navigation3:navigation3-ui ships NO web targets at all
// (android AAR + jvm/linux stubs only — spike w-10C §1), so every wasmJs
// configuration of this module — including ones that only pull
// :shared:core:ui and its transitive google -ui leaf — fails dependency
// resolution unless it points at JetBrains' fork of the same release line:
// same package, ABI-stable surface, real wasm klibs at the pinned 1.1.1.
// The fork's POM depends on google's runtime artifact, so only -ui is
// swapped. Graph-wide shape mirrored from apps/desktop/build.gradle.kts
// (its configurations.all block); :shared:core:ui keeps the same swap scoped
// to its own wasmJs-named configurations (spike w-10C S1/R2).
configurations.all {
    resolutionStrategy.dependencySubstitution {
        substitute(module("androidx.navigation3:navigation3-ui"))
            .using(module(libs.jb.navigation3.ui.get().toString()))
            .because("google navigation3-ui has no web artifacts; JB fork publishes the wasm klib")
    }
}
