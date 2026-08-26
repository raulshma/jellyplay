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
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    // Web UI target (spike w-10C): paging 3.5.0 / lifecycle 2.11 / tabler /
    // coil 3.4.0 ship readable wasm klibs; nav3-ui and mikepenz need the
    // substitutions/scopes configured below. Shares commonMain with android+jvm.
    wasmJs {
        browser {
            testTask {
                // commonTest suites run via jvmTest; the wasmJs browser test
                // run needs a local Chrome/Chromium (karma) and stays opt-in
                // until Phase W wires a headless wasm test lane — without this
                // guard, `gradlew build`/`check` would fail on Chrome-less
                // machines that previously ran no wasm tests at all. (Same
                // deliberate disable as :shared:core:network.)
                enabled = false
            }
        }
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
            // mikepenz moved to jvmShared below: its 0.43.0 wasm klibs are
            // Kotlin-2.4-ABI (silently skipped by our 2.3.21 klib loader) and
            // its graph evicts kotlin-stdlib to 2.4 — spike w-10C class C.
            // Nav3 ships KMP variants from google maven directly (desktop/iOS/
            // js/wasm variants in the same androidx coordinates) — no mirror.
            implementation(libs.navigation3.runtime)
            implementation(libs.navigation3.ui)
            implementation(libs.lifecycle.viewmodel)
            implementation(libs.paging.compose)
            implementation(libs.kotlinx.serialization.json)
        }
        getByName("jvmShared").dependencies {
            // MarkdownText's engine (class C seam): android+desktop render real
            // GFM; the wasmJs actual is a styled plain Text fallback.
            implementation(libs.multiplatform.markdown.renderer)
            implementation(libs.multiplatform.markdown.renderer.m3)
        }
        getByName("commonTest").dependencies {
            implementation(kotlin("test"))
        }
        getByName("androidMain").dependencies {
            // Dominant-color extraction keeps the original Palette pipeline.
            implementation(libs.palette.ktx)
        }
    }
}

// google's androidx.navigation3:navigation3-ui publishes no web artifacts at
// all (android AAR + jvm/linux stubs only), so every wasmJs configuration of
// this module fails dependency resolution unless it points at JetBrains'
// fork of the same release line — same package, ABI-stable surface; the
// fork's 1.1.1 already covers this repo on desktop (see apps/desktop).
// Scoped to wasmJs-named configurations so android/jvm graphs keep resolving
// google's published variants exactly as before (spike w-10C S1/R2).
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
