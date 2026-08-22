@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.compose)
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
        }
        getByName("commonTest").dependencies {
            implementation(kotlin("test"))
        }
    }
}
