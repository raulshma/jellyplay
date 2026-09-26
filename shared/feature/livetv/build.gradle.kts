import org.gradle.api.plugins.ExtensionAware

plugins {
    id("jellyplay.kmp.library.compose")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "com.raulshma.jellyplay.shared.feature.livetv"
    }

    sourceSets {
        // The ViewModels' clock dep narrowed from the jvmShared TimeSource to
        // its commonMain EpochMillisSource slice (the only read they ever
        // made), so no core:data type crosses the seam.
        //
        // jvmShared: the wall-clock/date-label formatters'
        // JVM actual resolves AM/PM and month/day names from the default
        // FORMAT locale via java.time. Desktop/android keep the localized
        // output the java.time formatters always had. (The jvmShared middle
        // source set comes from the convention plugin.)

        commonMain.dependencies {
            implementation(project(":shared:core:model"))
            implementation(project(":shared:core:designsystem"))
            implementation(project(":shared:core:data"))
            // AppRuntimeStateStore (favorite-channel persisted slice).
            implementation(project(":shared:core:datastore"))
            implementation(project(":shared:core:ui"))
            // JetBrains CMP distribution (see catalog note): Android targets
            // redirect to the androidx artifacts.
            implementation(libs.jb.compose.runtime)
            implementation(libs.jb.compose.ui)
            implementation(libs.jb.compose.foundation)
            implementation(libs.jb.compose.animation)
            implementation(libs.jb.compose.material3)
            // Compose-resources runtime (stringResource/StringResource API).
            implementation(compose.components.resources)
            implementation(libs.tabler.icons.outline)
            implementation(libs.tabler.icons.filled)
            // The Live-TV timestamp vocabulary (LiveTvTimeFormat.kt) and the
            // EPG window math run kotlinx.datetime — java.time stays JVM-side.
            implementation(libs.kotlinx.datetime)
            // Nav3 ships KMP variants from google maven directly — no mirror.
            // (The legacy build's lifecycle-viewmodel-navigation3 edge was
            // dropped: no livetv file imports it — navigation entries use
            // entry<Route> from the nav3 runtime/ui artifacts only.)
            implementation(libs.navigation3.runtime)
            implementation(libs.navigation3.ui)
            implementation(libs.lifecycle.viewmodel)
            // collectAsStateWithLifecycle in the screens.
            implementation(libs.lifecycle.runtime.compose)
            // coil3.size.Size in ChannelDetailBackdrop (library precedent).
            implementation(libs.coil.compose)
            // Koin owns the livetv ViewModels (V3 feature conveyor: one
            // framework per type — the Hilt annotations were stripped at the
            // move).
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
        }
        // (kotlin("test") comes from the convention plugin.)
        getByName("jvmTest").dependencies {
            implementation(libs.coroutines.test)
            implementation(libs.mockk)
            // The shared FakeTimeSource double (test-scoped only — see the
            // fixtures module's house rules).
            implementation(project(":shared:core:test-fixtures"))
        }
        getByName("androidMain").dependencies {
            // The user-messenger actual bridges to the app-wide
            // LocalUserMessageBus (:shared:core:ui androidMain since the
            // cutover dissolved the legacy :core:ui shim).
        }
    }
}

// `compose.resources` is a nested extension with no generated Kotlin-DSL
// accessor; configure it explicitly. Same package as the legacy :feature:livetv
// so migrated files keep their `com.raulshma.jellyplay.feature.livetv` imports;
// generated accessors land in `...feature.livetv.generated.resources`.
val composeResources = (compose as ExtensionAware).extensions.getByName("resources") as org.jetbrains.compose.resources.ResourcesExtension
composeResources.packageOfResClass = "com.raulshma.jellyplay.feature.livetv.generated.resources"
