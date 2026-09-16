import org.gradle.api.plugins.ExtensionAware
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
        namespace = "com.raulshma.jellyplay.shared.feature.library"
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
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    // web breadth: the target compiles — the repository seams the
    // ViewModels bind (MediaRepository, UserDataMutator) are commonMain since
    //, and the one jvmShared type they consumed (MediaDownloadActions)
    // moved behind core:data's commonMain QuickDownloadActions seam
    // (declared, implemented AND bound by core:data on both platforms —
    // no-op actions on web via dataWasmModule; the feature needs no platform
    // fragment). The karma/Chrome browser run stays off like
    // core:ui/core:network/requests — jvmTest pins the semantics.
    wasmJs {
        browser {
            testTask {
                enabled = false
            }
        }
    }
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        // The jvmShared slice (now empty of Kotlin — the QuickDownloadActions
        // wrapper/fragment actuals were deleted when core:data took over the
        // seam), kept so android + desktop share any future JVM-only Kotlin
        // like the newsletter/requests jvmShared source sets.
        val jvmShared = create("jvmShared")
        jvmShared.dependsOn(getByName("commonMain"))
        getByName("androidMain") { dependsOn(jvmShared) }
        getByName("jvmMain") { dependsOn(jvmShared) }

        getByName("commonMain").dependencies {
            implementation(project(":shared:core:model"))
            implementation(project(":shared:core:concurrency"))
            implementation(project(":shared:core:designsystem"))
            implementation(project(":shared:core:data"))
            // LibraryStore (persisted library layout/filter slices).
            implementation(project(":shared:core:datastore"))
            implementation(project(":shared:core:ui"))
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
            // Nav3 ships KMP variants from google maven directly — no mirror.
            implementation(libs.navigation3.runtime)
            implementation(libs.navigation3.ui)
            implementation(libs.lifecycle.viewmodel)
            // StudioDetailViewModel ctor arg (nav-entry SavedStateHandle, KMP
            // since lifecycle 2.9 — synthesized from CreationExtras by Koin's
            // Android parameters holder, same extras the Hilt factory used).
            implementation(libs.lifecycle.viewmodel.savedstate)
            // collectAsStateWithLifecycle in the screens.
            implementation(libs.lifecycle.runtime.compose)
            implementation(libs.paging.compose)
            // coil3.size.Size + MediaImage sizing in PhotoViewerScreen.
            implementation(libs.coil.compose)
            implementation(libs.kotlinx.serialization.json)
            // Koin owns the library ViewModels (V3 feature conveyor: one
            // framework per type — the Hilt annotations were stripped at the
            // move).
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
        }
        getByName("commonTest").dependencies {
            implementation(kotlin("test"))
        }
        getByName("jvmTest").dependencies {
            implementation(kotlin("test"))
            implementation(libs.coroutines.test)
            implementation(libs.mockk)
        }
        // androidMain needs no explicit deps: the photo-export actual's
        // MediaStore/FileProvider/coil3-android bits ride transitive edges
        // (coil-compose → coil-core-android; core-ktx via :shared:core:ui).
        getByName("androidMain").dependencies {
            // The user-messenger actual bridges to the app-wide
            // LocalUserMessageBus (:shared:core:ui androidMain since the
            // cutover dissolved the legacy :core:ui shim).
        }
    }
}

// `compose.resources` is a nested extension with no generated Kotlin-DSL
// accessor; configure it explicitly. Same package as the legacy :feature:library
// so migrated files keep their `com.raulshma.jellyplay.feature.library` imports;
// generated accessors land in `...feature.library.generated.resources`.
val composeResources = (compose as ExtensionAware).extensions.getByName("resources") as org.jetbrains.compose.resources.ResourcesExtension
composeResources.packageOfResClass = "com.raulshma.jellyplay.feature.library.generated.resources"

// google's androidx.navigation3:navigation3-ui publishes no web artifacts at
// all (android AAR + jvm/linux stubs only), so every wasmJs configuration of
// this module fails dependency resolution unless it points at JetBrains'
// fork of the same release line — same package, ABI-stable surface. Scoped
// to wasmJs-named configurations so android/jvm graphs keep resolving
// google's published variants exactly as before (the
// identical block lives in shared/core/ui, shared/feature/requests and the
// other web modules).
configurations.configureEach {
    if (name.lowercase().contains("wasmjs")) {
        resolutionStrategy.dependencySubstitution {
            substitute(module("androidx.navigation3:navigation3-ui"))
                .using(module(libs.jb.navigation3.ui.get().toString()))
                .because("google navigation3-ui has no web artifacts; JB fork publishes the wasm klib")
        }
    }
}

