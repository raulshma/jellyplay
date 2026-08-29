@file:OptIn(ExperimentalWasmDsl::class)

import org.gradle.api.plugins.ExtensionAware
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.compose)
    // compose-resources infrastructure (Res accessors + per-target resource
    // packaging) for the bundled brand fonts, which live in the jvmMain and
    // wasmJsMain composeResources dirs ONLY — deliberately not commonMain, so
    // the Android APK (whose actual resolves fonts via GMS) ships none of them.
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    android {
        namespace = "com.raulshma.jellyplay.shared.core.designsystem"
        compileSdk = 37
        minSdk = 28
        // Google Fonts certificates resource (font_certs.xml) → R class.
        androidResources {
            enable = true
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

    wasmJs {
        browser()
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        val jvmShared = create("jvmShared")
        jvmShared.dependsOn(getByName("commonMain"))
        getByName("androidMain") { dependsOn(jvmShared) }
        getByName("jvmMain") { dependsOn(jvmShared) }

        getByName("androidMain").dependencies {
            // Google Fonts provider + Palette swatch extraction (Android-only halves).
            implementation(project.dependencies.platform(libs.compose.bom))
            implementation(libs.compose.ui.google.fonts)
            implementation(libs.palette.ktx)
            implementation(libs.coil.compose)
        }
        getByName("commonMain").dependencies {
            implementation(project(":shared:core:model"))
            // JetBrains CMP distribution: only publisher of JVM/wasm compose
            // binaries (see catalog note). Android resolves androidx via the
            // JB→androidx redirection.
            api(libs.jb.compose.runtime)
            api(libs.jb.compose.ui)
            api(libs.jb.compose.foundation)
            api(libs.jb.compose.animation)
            api(libs.jb.compose.material3)
            // Compose-resources runtime (Font resource loading, jvm/wasm actuals).
            implementation(compose.components.resources)
        }
        getByName("commonTest").dependencies {
            implementation(kotlin("test"))
        }
    }
}

// `compose.resources` is a nested extension with no generated Kotlin-DSL
// accessor; configure it explicitly (same pattern as shared/core/ui and
// shared/feature/*). Legacy package + `.generated.resources`.
val composeResources = (compose as ExtensionAware).extensions.getByName("resources") as org.jetbrains.compose.resources.ResourcesExtension
composeResources.packageOfResClass = "com.raulshma.jellyplay.core.designsystem.generated.resources"
