import org.gradle.api.plugins.ExtensionAware

plugins {
    id("jellyplay.kmp.library.compose")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "com.raulshma.jellyplay.shared.core.ui"
        // Compose-resources packaging: androidResources.enable comes from the
        // convention plugin (see its KDoc for the device-pass
        // MissingResourceException story).
        // cutover: the legacy :core:ui module's Robolectric suites
        // moved here — withHostTest creates the androidUnitTest variant bound
        // to the Kotlin test tree (AGP-9 KMP library plugin). The two flags
        // are the legacy module's testOptions carried over verbatim: resource
        // lookups (TranscodeReasonsTest) and unstubbed-Context tolerance.
        withHostTest {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    sourceSets {
        // The jvmShared middle source set comes from the convention plugin.

        commonMain.dependencies {
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
            // publishes Kotlin-2.3-built klibs, so the SAME GFM pipeline
            // renders on android + desktop (see the catalog note).
            implementation(libs.multiplatform.markdown.renderer)
            implementation(libs.multiplatform.markdown.renderer.m3)
            // Nav3 ships KMP variants from google maven directly (desktop/iOS/
            // js variants in the same androidx coordinates) — no mirror.
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
        // BlurHashCache byte-budget regression tests construct real ImageBitmaps;
        // the skiko JVM artifacts on main are code-only, natives (dll.sha256)
        // ride compose.desktop.currentOs. Host-OS only: jvmTest runs on it.
        // (kotlin("test") comes from the convention plugin.)
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
