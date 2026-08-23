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
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    // No wasmJs target yet (same as shared/feature:search): the web shell lands
    // in plan §Phase W; android+jvm covers V3 consumers.
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        getByName("commonMain").dependencies {
            implementation(project(":shared:core:model"))
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
        // (coil-compose → coil-core-android; core-ktx via core:ui re-exports).
        getByName("androidMain").dependencies {
            // The user-messenger actual bridges to the app-wide
            // LocalUserMessageBus, which still lives in the legacy Android-only
            // :core:ui shim (plan §V1a list) until its own conveyor move — same
            // transition-period relationship as the shim's api() re-export of
            // the shared tree, dies at Phase X.
            implementation(project(":core:ui"))
        }
    }
}

// `compose.resources` is a nested extension with no generated Kotlin-DSL
// accessor; configure it explicitly. Same package as the legacy :feature:library
// so migrated files keep their `com.raulshma.jellyplay.feature.library` imports;
// generated accessors land in `...feature.library.generated.resources`.
val composeResources = (compose as ExtensionAware).extensions.getByName("resources") as org.jetbrains.compose.resources.ResourcesExtension
composeResources.packageOfResClass = "com.raulshma.jellyplay.feature.library.generated.resources"
