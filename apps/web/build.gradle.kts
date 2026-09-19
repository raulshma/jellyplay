@file:OptIn(ExperimentalWasmDsl::class)

import java.util.zip.GZIPOutputStream

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    //  web shell (docs/kmp-migration-plan.md §): single
    // wasmJs/browser target proving the shared DI stacks on wasm. The Ktor
    // wasm network seam (W.1 chunks 1-3) is wired here and the Coil wasm
    // image engine (W.4) is wired since the repo-wide coil 3.4.0 pin
    // (libs.versions.toml version note): 3.5.0's wasmJs klibs are Kotlin-
    // 2.4-ABI and silently skipped by our 2.3.21 loader. This module adds the
    // real navigation shell (WebAppRoot over the JB fork's NavDisplay).
    // First FEATURE screen — `entry<Route.Requests>` renders the
    // shared RequestsScreen (15B's wasm target) with the requests DI slice.
    wasmJs {
        browser {
            commonWebpackConfig {
                // Pin the loader name so src/wasmJsMain/resources/index.html
                // references a deterministic file (compile task is unaffected).
                outputFileName = "webapp.js"
            }
        }
        // The main binary must be EXECUTABLE for webpack to produce
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
                // (WebSeerr probe re-home): the compose-resources RUNTIME is
                // needed directly — the pane resolves the settings module's
                // localized probe fallback texts at render time through the
                // public ConnectionProbe.FallbackText.resource() +
                // stringResource, and the runtime reaches this module's
                // compile classpath only as a transitive implementation dep
                // of the JB distribution otherwise (same leak shape as the
                // ktor-client-js edge below). The settings module's generated
                // Res object stays internal, so no generated-resource import
                // crosses the module boundary — only the runtime.
                implementation(libs.jb.compose.resources)

                //  stack: model (shared value types),
                // designsystem (JellyPlayTheme), ui (shared
                // composition locals + nav wiring; the wasm target is
                // machine-verified green since then), datastore
                // (datastoreCommonModule + webDatastoreModule DI), network
                // (networkWasmModule — .1 chunk 3: AtomicSessionState
                // + WasmClientIdentity + the three Ktor wasm clients +
                // AuthApiClient/LibraryApiClient/PlaybackApiClient bindings).
                // Deliberately absent: paging-compose (§1 proves
                // its 3.5.0 wasm klibs exist, but no consuming web module
                // needs LazyPagingItems yet). the database target joined: web gets
                // the real Room database (OPFS-backed via the vendored
                // webworker/ npm package riding this module's wasmJsMain) and
                // Main.kt registers webDatabaseModule alongside the rest of
                // the DI stack — nothing on web consumes the DAOs yet; the
                // edge exists so the wiring compiles and ships.
                implementation(project(":shared:core:model"))
                implementation(project(":shared:core:designsystem"))
                implementation(project(":shared:core:ui"))
                implementation(project(":shared:core:datastore"))
                implementation(project(":shared:core:network"))
                implementation(project(":shared:core:database"))
                // dataWasmModule (the requests repo slice —
                // SeerrRepository/ArrRepository over the wasm clients) is
                // imported into Main.kt's startKoin, hence the direct edge.
                implementation(project(":shared:core:data"))
                // The first shared feature screen renders here —
                // WebAppRoot's `entry<Route.Requests>` composes
                // RequestsScreen (VM ctor deps: SeerrRepository +
                // ArrRepository from dataWasmModule above, ExperimentalStore
                // from datastoreCommonModule). Later lanes add the second
                // and third: `entry<Route.UpcomingCalendar>` (calendarModule)
                // and `entry<Route.SeerrDetail>` (detailsModule). The
                // KoinModuleRegistrationGuardTest's web forward allowlist
                // pins exactly these three registrations.
                implementation(project(":shared:feature:requests"))
                implementation(project(":shared:feature:calendar"))
                // The third shared feature screen on web —
                // WebAppRoot's `entry<Route.SeerrDetail>` composes the shared
                // SeerrDetailScreen (details' wasmJs target; the MediaDetail
                // cluster stays jvmShared/off-web) and Main.kt registers
                // detailsModule + webDetailsPlatformModule (the narrow
                // MediaRepository for the SeerrDetail cross-link).
                implementation(project(":shared:feature:details"))
                // the third and fourth shared feature screens —
                // WebAppRoot's `entry<Route.ArrQueue>` (arrqueueModule; the
                // ArrRepository binding already lives in dataWasmModule) and
                // `entry<Route.Onboarding>` (onboardingModule; datastore
                // stores resolve from datastoreCommonModule/webDatastoreModule).
                // shortcuts + auth also gained wasmJs targets but stay
                // unrouted/unregistered on web for now (the auth feature's
                // screens duplicate what the landing pane covers; the
                // shortcuts grid's targets are mostly non-wasm; the web
                // landing drives sign-in through the shared AuthRepository
                // — dataWasmModule's WasmAuthRepository — not the feature).
                implementation(project(":shared:feature:arrqueue"))
                implementation(project(":shared:feature:onboarding"))
                // the settings feature's first web-routed slice —
                // WebAppRoot's entry<Route.ArrSettings> composes the shared
                // ArrSettingsScreen and Main.kt registers settingsModule (the
                // ArrSettingsViewModel closure — ArrRepository/ArrPreferences
                // Store/ArrSecureCredentialsStore — resolves from
                // dataWasmModule + datastoreCommonModule/webDatastoreModule).
                // The settings ROOT (Route.Settings) stays unrouted on web:
                // the wasm AuthRepository binding exists (dataWasmModule's
                // WasmAuthRepository — the session-seam port), but the
                // settings-root VM closure needs more than the repository
                // (SettingsBackupIo/AppMetaProvider/LogCollector have no
                // wasm actuals), so every settings-root/MediaRepository-backed
                // VM def settingsModule registers stays latent exactly like
                // detailsModule's MediaDetail cluster. player-audio also
                // gained a wasmJs target but gets NO edge here (nothing
                // on web consumes it): all four playback/cast ctor seams of
                // AudioPlayerViewModel (AudioQueueManager/AudioEffectsManager/
                // AudioPlayerEngine/AudioPlayerCast) lack wasm bindings — the
                // only impls are the Android media3 graph and the desktop
                // mpv app-layer manager — so a web route would need a real
                // wasm audio engine first (shortcuts/auth precedent:
                // target-only stays undepended-on).
                implementation(project(":shared:feature:settings"))
                // (HtmlVideoEngine): the wasm-visible MediaEngine
                // contract + EnginePositionTicker/WebPlaybackMappings the
                // web video engine implements. The engine class is landed and
                // browser-verified through the WebDiagnostics harness; the
                // shell's nav UI deliberately hosts no playback — in-shell
                // playback is a web-v1 scope cut, and this edge exists so the
                // engine compiles against the real contract.
                implementation(project(":shared:core:player-contract"))

                // DONE (was BLOCKED at coil 3.5.0 whose wasmJs
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
                // (WebConnectFlow): ktor-client-core is needed to
                // CLASSIFY transport failures typed there / in ktor-io
                // (HttpRequestTimeoutException, IOException) for the connect
                // form's error lines — the same taxonomy the wasm network
                // classifier uses. Direct edge for the same leak reason as
                // ktor-client-js above (implementation dep of core/network).
                implementation(libs.ktor.client.core)
                // (WebMediaRepositoryNarrow): PagingData appears in
                // MediaRepository's paged-member signatures — a direct edge
                // for the same leak reason as ktor-client-js above (paging-
                // common is an implementation dep of shared/core/data, so it
                // does not reach our compile classpath transitively). The
                // common-only artifact, no paging runtime enters the shell.
                implementation(libs.paging.common)

                implementation(libs.koin.core)
                // The koin-compose runtime behind the requests
                // entry's `koinViewModel()` (RequestsScreen's default
                // parameter, compiled inside the feature klib). Explicit
                // edges rather than relying on the feature module's
                // transitive `implementation` deps reaching the wasm link —
                // apps/desktop states the same policy (its line ~170: feature
                // screens bring their own koin-compose edges). Both
                // artifacts are ABI-safe on this repo's Kotlin 2.3.21 loader
                // (4.2.2 wasm klibs require only stdlib 2.3.20; verified in
                // the spike before the catalog entries landed).
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

        // Minimal wasmJs test source set: pure-logic unit tests — kotlin("test")
        // executed on the karma/webpack browser lane (wasmJsBrowserTest; there
        // is no wasmJsNodeTest here — only browser{} is configured on the
        // target — and ChromeHeadless runs it fine on the dev machines and CI).
        // See src/wasmJsTest/.../WebShellPureHelpersTest.kt for what is
        // asserted and WebConnectFailurePolicyTest for the connect-flow
        // failure taxonomy (extracted from WebConnectFlow.kt to be testable).
        val wasmJsTest by getting {
            dependencies {
                implementation(kotlin("test"))
                // runTest wrapper for the suspend controller/repository tests
                // — kotlin.test rejects `suspend @Test` on wasmJs outright.
                implementation(libs.coroutines.test)
            }
        }
    }
}

// KGP's default binaryen line for this module is `-O3 --gufa` repeated three
// times. On this bundle that needs >10 GB of native RAM inside wasm-opt:
// measured in a container, the full line is OOM-killed at a 10 GB cap, and on
// the 16 GB CI runner it either wedged the box until the runner was evicted
// (runs 35424779995/35425885803/35427101486) or died with SIGSEGV under
// allocation pressure (runs 35427847303/35431664891, binaryen 125 and 128
// alike — so it is memory, not a specific binaryen bug; same crash shape as
// KT-80988). One -O3 round keeps the size win (40.6 MB -> 22.4 MB) with a
// measured 5 GB peak. binaryen stays pinned at 128 (KGP default is 125):
// same crash on both proves the version is not the variable.
tasks.withType<org.jetbrains.kotlin.gradle.targets.wasm.binaryen.BinaryenExec>().configureEach {
    binaryenArgs = mutableListOf(
        "--enable-nontrapping-float-to-int",
        "--enable-gc",
        "--enable-reference-types",
        "--enable-exception-handling",
        "--enable-bulk-memory",
        "--inline-functions-with-loops",
        "--traps-never-happen",
        "--fast-math",
        "--closed-world",
        "-O3",
    )
}

// Pin binaryen 128 explicitly (KGP 2.3.21 would download 125) so the optimize
// toolchain does not drift with the Kotlin pin.
plugins.withType<org.jetbrains.kotlin.gradle.targets.wasm.binaryen.BinaryenPlugin> {
    extensions.configure<org.jetbrains.kotlin.gradle.targets.wasm.binaryen.BinaryenEnvSpec> {
        version.set("128")
    }
}

// google's androidx.navigation3:navigation3-ui ships NO web targets at all
// (android AAR + jvm/linux stubs only — §1), so every wasmJs
// configuration of this module — including ones that only pull
// :shared:core:ui and its transitive google -ui leaf — fails dependency
// resolution unless it points at JetBrains' fork of the same release line:
// same package, ABI-stable surface, real wasm klibs at the pinned 1.1.1.
// The fork's POM depends on google's runtime artifact, so only -ui is
// swapped. Graph-wide shape mirrored from apps/desktop/build.gradle.kts
// (its configurations.all block); :shared:core:ui keeps the same swap scoped
// to its own wasmJs-named configurations.
configurations.all {
    resolutionStrategy.dependencySubstitution {
        substitute(module("androidx.navigation3:navigation3-ui"))
            .using(module(libs.jb.navigation3.ui.get().toString()))
            .because("google navigation3-ui has no web artifacts; JB fork publishes the wasm klib")
    }
}

// (settings on web): aboutlibraries-core 15.0.4's wasm klib is a Kotlin
// 2.4.0 build (ABI 2.4.0) that this repo's 2.3.21 compiler cannot load, and
// it drags kotlin-stdlib-wasm-js:2.4.0 in whose rejection blanks the whole
// stdlib during the whole-program klib link ("Built-in class kotlin.Any is
// not found" — caught by wasmJsBrowserDistribution's productionExecutable
// link; the development compile lane only WARNS on the ABI mismatch, so CI's
// :apps:web:compileKotlinWasmJs stayed green through it). shared/feature/
// settings already forces 14.2.1 on its own wasmJs configurations (same
// rationale, verbatim), but a resolutionStrategy force does NOT travel
// through project-variant metadata — apps/web resolves the consumption graph
// itself, so the force is duplicated consumer-side (exactly the shape of the
// navigation3-ui substitution above, which core/ui also carries its own copy
// of). 14.2.1's wasm klib is a Kotlin 2.3.20 build (ABI 2.3.0, consumable);
// the Library/License entity API the Licenses path reads is unchanged
// between the two lines. Scoped to wasmJs-named configurations so any future
// non-wasm configuration keeps the repo-wide 15.0.4 pin.
configurations.configureEach {
    if (name.lowercase().contains("wasmjs")) {
        resolutionStrategy {
            force("com.mikepenz:aboutlibraries-core:14.2.1")
        }
    }
}

// ---------------------------------------------------------------------------
// Production-bundle precompression + binaryen status.
//
// Deliberately NO binaryenArgs override: KGP 2.3.21 already seeds
// every BinaryenExec task with an aggressive default pipeline (verified by
// disassembling kotlin-gradle-plugin-2.3.21: BinaryenExec's constructor
// copies BinaryenConfig.binaryenArgs), namely
//   --enable-gc --enable-reference-types --enable-exception-handling
//   --enable-bulk-memory --enable-nontrapping-float-to-int --closed-world
//   --no-inline=... (x3) --inline-functions-with-loops --traps-never-happen
//   --fast-math --type-ssa -O3 --gufa -O3 --type-merging -O3 -Oz
// which already exceeds the suggested -O3/--shrink-level=2/
// --closed-world tuning. There is no ADD-only argument left worth risk.
//
// kmp-release.yml zips build/dist/wasmJs/productionExecutable
// verbatim, so self-hosters who unzip it lose every byte of wire
// compression unless their server compresses on the fly. This task writes
// sibling <name>.gz sidecars for every *.wasm / *.js / *.css in the
// distribution (originals untouched) so a static file server can serve
// them with zero per-request cost:
//   nginx:  gzip_static on;      caddy:  file_server precompressed
// (GH-Pages-style hosts without gzip_static simply ignore the extras.)
// Wired BOTH ways: a finalizer of wasmJsBrowserDistribution (local runs get
// the sidecars automatically) and a dependsOn from the task itself, so the
// standalone `./gradlew :apps:web:precompressWasmDist` invocation the
// release lane uses builds the distribution first when it is not there
// yet. Always re-runs (gzip of a rebuilt dist is cheap; a cached PASS
// would serve stale sidecars after an incremental rebuild) — same policy
// as :app:verifyPhoneDebugComposeResources.
// ---------------------------------------------------------------------------
abstract class PrecompressWasmDistTask : DefaultTask() {
    /** apps/web/build/dist/wasmJs/productionExecutable — what wasmJsBrowserDistribution emits. */
    @get:Internal
    abstract val distDir: DirectoryProperty

    @TaskAction
    fun compress() {
        val dir = distDir.get().asFile
        if (!dir.isDirectory) {
            throw GradleException(
                "precompressWasmDist: distribution directory $dir does not exist — " +
                    "run :apps:web:wasmJsBrowserDistribution first"
            )
        }
        // Orphaned sidecars from a previous run (source dropped or renamed by
        // an incremental rebuild) must not ship in the release zip.
        var removed = 0
        dir.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".gz") }
            .forEach { sidecar ->
                val source = sidecar.resolveSibling(sidecar.name.removeSuffix(".gz"))
                if (!source.isFile) {
                    sidecar.delete()
                    removed++
                }
            }
        var written = 0
        dir.walkTopDown()
            .filter { it.isFile && !it.name.endsWith(".gz") && it.extension in setOf("wasm", "js", "css") }
            .forEach { file ->
                val target = file.resolveSibling("${file.name}.gz")
                file.inputStream().use { input ->
                    GZIPOutputStream(target.outputStream().buffered()).use { output ->
                        input.copyTo(output)
                    }
                }
                written++
            }
        if (written == 0) {
            throw GradleException(
                "precompressWasmDist: no *.wasm/*.js/*.css found under $dir — " +
                    "distribution layout changed?"
            )
        }
        logger.lifecycle(
            "precompressWasmDist: wrote $written .gz sidecar(s) under $dir" +
                (if (removed > 0) ", removed $removed orphaned sidecar(s)" else "")
        )
    }
}

val precompressWasmDist = tasks.register<PrecompressWasmDistTask>("precompressWasmDist") {
    group = "build"
    description = "gzip sidecars (*.wasm/*.js/*.css -> <name>.gz) inside the production wasm " +
        "distribution — for self-hosters serving them via nginx gzip_static / Caddy precompressed."
    dependsOn("wasmJsBrowserDistribution")
    distDir.set(layout.buildDirectory.dir("dist/wasmJs/productionExecutable"))
    outputs.upToDateWhen { false }
}

tasks.named("wasmJsBrowserDistribution") {
    finalizedBy(precompressWasmDist)
}
