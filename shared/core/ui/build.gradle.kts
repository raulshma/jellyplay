@file:OptIn(ExperimentalWasmDsl::class)

import org.gradle.api.plugins.ExtensionAware
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "com.raulshma.jellyplay.shared.core.ui"
        compileSdk = 37
        minSdk = 28
        // Compose-resources packaging (device-pass finding): with the
        // AGP-9 KMP library plugin, android resources are OFF by default, so
        // copyAndroidMainComposeResourcesToAndroidAssets never runs and the
        // app APK ships this module's Res accessors with NO backing .cvr
        // assets — runtime MissingResourceException on the first string read.
        androidResources {
            enable = true
        }
        // cutover: the legacy :core:ui module's Robolectric suites
        // moved here — withHostTest creates the androidUnitTest variant bound
        // to the Kotlin test tree (AGP-9 KMP library plugin). The two flags
        // are the legacy module's testOptions carried over verbatim: resource
        // lookups (TranscodeReasonsTest) and unstubbed-Context tolerance.
        withHostTest {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    // Web UI target: paging 3.5.0 / lifecycle 2.11 / tabler /
    // coil 3.4.0 ship readable wasm klibs; nav3-ui and mikepenz need the
    // substitutions/scopes configured below. Shares commonMain with android+jvm.
    wasmJs {
        browser {
            testTask {
                // The karma/Chrome browser run stays opt-in/off: `gradlew
                // build`/`check` must not fail on Chrome-less machines (same
                // deliberate disable as :shared:core:network).
                enabled = false
            }
        }
        // Headless wasm test lane: wasmJsNodeTest compiles the full
        // main+test wasm graphs headlessly — no Karma, no Chrome — but CANNOT
        // EXECUTE this module's tests under plain Node: the Compose graph
        // links skiko.mjs and Node cannot fetch/prepare its wasm ("both async
        // and sync fetching of the wasm failed"). Execution is proven green
        // only for skiko-free modules (:shared:core:model). Kept as a compile
        // gate plus future hook; runs under FAIL_ON_PROJECT_REPOS — the
        // settings.gradle.kts node/yarn governance (no flips needed).
        nodejs()
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        // JVM-semantics code shared verbatim by android + desktop: the
        // SimpleDateFormat pipeline, the markdown renderer body, the LRU lock
        // actual, and the PlatformTime JVM actuals. Wasm gets pure-Kotlin
        // replacements in wasmJsMain for everything commonMain references.
        val jvmShared = create("jvmShared")
        jvmShared.dependsOn(getByName("commonMain"))
        getByName("androidMain") { dependsOn(jvmShared) }
        getByName("jvmMain") { dependsOn(jvmShared) }

        getByName("commonMain").dependencies {
            implementation(project(":shared:core:model"))
            implementation(project(":shared:core:designsystem"))
            // DateLabels.kt's public seam shapes take/return kotlinx-datetime
            // civil-date types — `api` so every feature façade consuming the
            // seam compiles against them without redeclaring the artifact.
            api(libs.kotlinx.datetime)
            // DeferredFetchCoordinator wraps fetch invocations in
            // runCatchingRethrowingCancellation — the repo's one
            // cancellation-safety seam (zero-dependency leaf, no cycle).
            implementation(project(":shared:core:concurrency"))
            // JetBrains CMP distribution (see catalog note): Android targets
            // redirect to the androidx artifacts.
            implementation(libs.jb.compose.runtime)
            implementation(libs.jb.compose.ui)
            implementation(libs.jb.compose.foundation)
            implementation(libs.jb.compose.animation)
            implementation(libs.jb.compose.material3)
            implementation(libs.jb.compose.saveable)
            // Compose-resources runtime (stringResource/StringResource API).
            implementation(compose.components.resources)
            implementation(libs.tabler.icons.outline)
            implementation(libs.tabler.icons.filled)
            implementation(libs.coil.compose)
            // MarkdownText's engine: the mikepenz 0.41.0 pin
            // publishes Kotlin-2.3-built wasm klibs, so the SAME GFM pipeline
            // renders on android + desktop + wasm (see the catalog note).
            implementation(libs.multiplatform.markdown.renderer)
            implementation(libs.multiplatform.markdown.renderer.m3)
            // Nav3 ships KMP variants from google maven directly (desktop/iOS/
            // js/wasm variants in the same androidx coordinates) — no mirror.
            implementation(libs.navigation3.runtime)
            implementation(libs.navigation3.ui)
            implementation(libs.lifecycle.viewmodel)
            // DeferredRefreshEffect (the screen side of the deferred
            // user-data refresh contract) is driven by LifecycleResumeEffect.
            implementation(libs.lifecycle.runtime.compose)
            implementation(libs.paging.compose)
            implementation(libs.kotlinx.serialization.json)
            // v0.10.6 merge: coreUiMessageModule owns the shared
            // UserMessageBus single (see di/CoreUiMessageModule.kt).
            implementation(libs.koin.core)
        }
        getByName("jvmShared").dependencies {
        }
        getByName("commonTest").dependencies {
            implementation(kotlin("test"))
        }
        // BlurHashCache byte-budget regression tests construct real ImageBitmaps;
        // the skiko JVM artifacts on main are code-only, natives (dll.sha256)
        // ride compose.desktop.currentOs. Host-OS only: jvmTest runs on it.
        getByName("jvmTest").dependencies {
            implementation(compose.desktop.currentOs)
            // runTest/UnconfinedTestDispatcher for the Channel/StateFlow pure-logic tests.
            implementation(libs.coroutines.test)
        }
        getByName("androidMain").dependencies {
            // Dominant-color extraction keeps the original Palette pipeline.
            implementation(libs.palette.ktx)
            // cutover moves from the legacy :core:ui module:
            // BiometricAuthHelper (BiometricPrompt + Keystore cipher pipeline).
            implementation(libs.biometric.ktx)
        }
    }
}

// The legacy :core:ui Robolectric suites moved here wholesale at the
// cutover (robolectric.properties pins sdk=35 — Robolectric 4.16 emulates at
// most 36 while the merged manifest targets 37). AGP 9.4's withHostTest names
// the lane's source set androidHostTest (src/androidHostTest/kotlin) and
// materializes it only in afterEvaluate, so the dependency wiring rides a
// configureEach — an eager lookup would run before the source set exists.
kotlin.sourceSets.configureEach {
    if (name == "androidHostTest") {
        dependencies {
            implementation(kotlin("test"))
            implementation(libs.junit)
            implementation(libs.robolectric)
            implementation(libs.androidx.test.core)
            // UserMessageBusTest pins the one-shot Channel semantics.
            implementation(libs.coroutines.test)
            // Compose UI tests under Robolectric (focus-behavior + sheet/scrim
            // regression suites, incl. the two former androidTest files which
            // were compile-gated only in the legacy module).
            implementation(project.dependencies.platform(libs.compose.bom))
            implementation(libs.compose.ui.test)
            implementation(libs.compose.ui.test.manifest)
        }
    }
}

// google's androidx.navigation3:navigation3-ui publishes no web artifacts at
// all (android AAR + jvm/linux stubs only), so every wasmJs configuration of
// this module fails dependency resolution unless it points at JetBrains'
// fork of the same release line — same package, ABI-stable surface; the
// fork's 1.1.1 already covers this repo on desktop (see apps/desktop).
// Scoped to wasmJs-named configurations so android/jvm graphs keep resolving
// google's published variants exactly as before.
configurations.configureEach {
    if (name.lowercase().contains("wasmjs")) {
        resolutionStrategy.dependencySubstitution {
            substitute(module("androidx.navigation3:navigation3-ui"))
                .using(module(libs.jb.navigation3.ui.get().toString()))
                .because("google navigation3-ui has no web artifacts; JB fork publishes the wasm klib")
        }
    }
}

// `compose.resources` is a nested extension with no generated Kotlin-DSL
// accessor; configure it explicitly. Same package as the legacy :core:ui so
// migrated files keep their `com.raulshma.jellyplay.core.ui` imports; generated
// accessors land in `...core.ui.generated.resources`.
val composeResources = (compose as ExtensionAware).extensions.getByName("resources") as org.jetbrains.compose.resources.ResourcesExtension
composeResources.packageOfResClass = "com.raulshma.jellyplay.core.ui.generated.resources"
// Cross-module string sharing: shared/feature modules resolve a handful of
// core strings (core_delete/core_cancel, ...) directly, which requires the
// generated Res object + accessors to be public (internal by default).
composeResources.publicResClass = true

