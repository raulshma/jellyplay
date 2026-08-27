@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    // AGP 9 KMP library plugin: the Android target is configured inside the
    // kotlin block (the top-level android {} block of com.android.library
    // modules is not available here). Source sets follow the KMP layout:
    // src/androidMain, src/androidHostTest, src/androidDeviceTest.
    android {
        namespace = "com.raulshma.jellyplay.shared.core.model"
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

    wasmJs {
        browser()
        // Headless commonTest lane: wasmJsNodeTest runs under Kotlin's
        // downloaded Node.js distribution — no Karma, no Chrome (the browser
        // lane demanded Chrome, see plan history). The wave-12D note about a
        // PREFER_PROJECT flip being required is OBSOLETE: wave 13C's
        // settings.gradle.kts node/yarn governance owns the tool
        // repositories, so this lane runs under FAIL_ON_PROJECT_REPOS with
        // no flips. Suite verified green on Node that way.
        nodejs()
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        // JVM-semantics code shared verbatim by android + desktop: TtlCache
        // (synchronizedMap/LinkedHashMap access-order), BoundedCollections,
        // CacheIdentity (@JvmInline), and the java.util.Locale-driven
        // language-code tables. Wasm gets a pure-Kotlin replacement when it
        // first needs them (plan §Phase W).
        val jvmShared = create("jvmShared")
        jvmShared.dependsOn(getByName("commonMain"))
        getByName("androidMain") { dependsOn(jvmShared) }
        getByName("jvmMain") { dependsOn(jvmShared) }

        getByName("commonMain").dependencies {
            implementation(libs.kotlinx.serialization.json)
            // Annotation-only Compose usage (@Immutable/@Stable on models):
            // compose.runtime suffices, no compiler plugin needed — same pattern
            // the legacy :core:model module documented. androidx.compose.runtime
            // ships multiplatform variants resolved from the shared BOM.
            implementation(project.dependencies.platform(libs.compose.bom))
            implementation(libs.compose.runtime)
        }
        getByName("commonTest").dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.serialization.json)
        }
    }
}
