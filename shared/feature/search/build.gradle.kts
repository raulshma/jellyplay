import org.gradle.api.plugins.ExtensionAware

plugins {
    id("jellyplay.kmp.library.compose")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "com.raulshma.jellyplay.shared.feature.search"
    }

    sourceSets {
        // The jvmShared slice (now empty of Kotlin — the QuickDownloadActions
        // wrapper/fragment actuals were deleted when core:data took over the
        // seam), kept so android + desktop share any future JVM-only Kotlin
        // like the newsletter/requests jvmShared source sets. (The jvmShared
        // middle source set comes from the convention plugin.)

        commonMain.dependencies {
            implementation(project(":shared:core:model"))
            implementation(project(":shared:core:designsystem"))
            implementation(project(":shared:core:data"))
            // SearchFiltersStore (persisted search filter blob).
            implementation(project(":shared:core:datastore"))
            // runCatchingRethrowingCancellation for the best-effort filter
            // persist/clear writes (bare runCatching would swallow the
            // caller's cancellation).
            implementation(project(":shared:core:concurrency"))
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
            // collectAsStateWithLifecycle in SearchScreen.
            implementation(libs.lifecycle.runtime.compose)
            implementation(libs.paging.compose)
            implementation(libs.kotlinx.serialization.json)
            // Koin owns SearchViewModel (V3 feature conveyor: one framework
            // per type — the Hilt annotations were stripped at the move).
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
        }
        // (kotlin("test") comes from the convention plugin.)
        getByName("jvmTest").dependencies {
            implementation(libs.coroutines.test)
            implementation(libs.mockk)
        }
        // androidMain needs no explicit deps: the voice-search actual's
        // androidx.activity.compose rides navigation3-ui's transitive edge,
        // same as shared/core/ui's BackHandler seam.
    }
}

// `compose.resources` is a nested extension with no generated Kotlin-DSL
// accessor; configure it explicitly. Same package as the legacy :feature:search
// so migrated files keep their `com.raulshma.jellyplay.feature.search` imports;
// generated accessors land in `...feature.search.generated.resources`.
val composeResources = (compose as ExtensionAware).extensions.getByName("resources") as org.jetbrains.compose.resources.ResourcesExtension
composeResources.packageOfResClass = "com.raulshma.jellyplay.feature.search.generated.resources"
