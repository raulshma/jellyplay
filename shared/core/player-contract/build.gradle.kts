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

    // No wasmJs target: MediaEngine extends PlayerLifecycleCallbacks +
    // RemotePlayableEngine from :shared:core:data, which has no wasm build
    // (Room dependency). Phase W gives those supertypes a wasm-visible home
    // when HtmlVideoEngine lands (plan §Phase W / §2).
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        getByName("commonMain").dependencies {
            api(project(":shared:core:model"))
            // Super-interfaces of MediaEngine (PlayerLifecycleCallbacks,
            // RemotePlayableEngine) live in shared core:data's commonMain.
            api(project(":shared:core:data"))
            // Flow/StateFlow surface of the engine contract.
            implementation(libs.kotlinx.coroutines.core)
            // Annotation-only Compose usage (@Immutable/@Stable on the contract
            // data classes), same pattern as :shared:core:data and :model.
            implementation(project.dependencies.platform(libs.compose.bom))
            implementation(libs.compose.runtime)
        }
        getByName("commonTest").dependencies {
            implementation(kotlin("test"))
            implementation(libs.coroutines.test)
        }
    }
}
