@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
}

kotlin {
    android {
        namespace = "com.raulshma.jellyplay.shared.core.player.contract"
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

    // wasmJs target added Phase W.3: MediaEngine's supertypes
    // (PlayerLifecycleCallbacks, RemotePlayableEngine) previously lived in
    // :shared:core:data — which has no wasm build (Room) — and blocked this
    // module from shipping wasm. They now live here verbatim (SAME packages,
    // zero consumer import churn) so HtmlVideoEngine gets a wasm-visible
    // contract (plan §Phase W). Dependency edge flipped: core:data now depends
    // on this module instead of the reverse.
    wasmJs {
        browser {
            testTask {
                // commonTest suites run via jvmTest; the wasmJs browser test
                // run needs a local Chrome/Chromium (karma) and stays opt-in
                // until Phase W wires a headless wasm test lane — without this
                // guard, `gradlew build`/`check` would fail on Chrome-less
                // machines that previously ran no wasm tests at all. Same
                // pattern as :shared:core:network.
                enabled = false
            }
        }
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        getByName("commonMain").dependencies {
            api(project(":shared:core:model"))
            // Flow/StateFlow surface of the engine contract.
            implementation(libs.kotlinx.coroutines.core)
            // core:model's @Serializable enums surface their generated
            // serializer companions in this module's when-expressions
            // (PlayerType, DecoderMode, MediaSegmentType, …); compiling against
            // those klibs needs serialization-core on the classpath. Previously
            // leaked transitively through the (now-removed) core:data api edge;
            // json pulls core, same declaration core:model itself uses.
            implementation(libs.kotlinx.serialization.json)
            // Annotation-only Compose usage (@Immutable/@Stable on the contract
            // data classes), same pattern as :shared:core:data and :model.
            implementation(project.dependencies.platform(libs.compose.bom))
            implementation(libs.compose.runtime)
        }
        getByName("commonTest").dependencies {
            implementation(kotlin("test"))
            implementation(libs.coroutines.test)
        }
        // EngineCapabilityMatrixTest moved here from :feature:player:video's
        // unit-test source set with the subtitle-tester conveyor (feature
        // seventeen): the matrix itself moved to this module's commonMain
        // (same package) so shared feature modules can consume it; the test
        // follows its subject. kotlin.test replaces the org.junit imports.
        getByName("jvmTest").dependencies {
            implementation(kotlin("test"))
        }
    }
}
