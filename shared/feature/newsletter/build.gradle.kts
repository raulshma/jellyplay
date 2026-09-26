import org.gradle.api.plugins.ExtensionAware

plugins {
    id("jellyplay.kmp.library.compose")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "com.raulshma.jellyplay.shared.feature.newsletter"
    }

    sourceSets {
        // The java.time actuals for NewsletterDateLabels.kt (JDK only — no
        // deps), shared verbatim by android + desktop like the requests
        // RequestTime.kt seam. (The jvmShared middle source set comes from
        // the convention plugin.)

        commonMain.dependencies {
            implementation(project(":shared:core:model"))
            implementation(project(":shared:core:designsystem"))
            implementation(project(":shared:core:data"))
            // NotificationStore — the newsletter last-viewed timestamp and
            // the section-order/enabled-sections prefs the ViewModel resolves
            // the section ordering from.
            implementation(project(":shared:core:datastore"))
            // The since-digest window ("today minus 7d at start of day") and
            // the header's weekend check run kotlinx.datetime.
            implementation(libs.kotlinx.datetime)
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
            // Nav3 ships KMP variants from google maven directly — no mirror.
            // (The legacy build's lifecycle-viewmodel-navigation3 edge was
            // dropped with the syncplay move: no newsletter file imports it —
            // the navigation entries use entry<Route> from the nav3 runtime/ui
            // artifacts only, syncplay precedent.)
            implementation(libs.navigation3.runtime)
            implementation(libs.navigation3.ui)
            implementation(libs.lifecycle.viewmodel)
            // collectAsStateWithLifecycle in the screens.
            implementation(libs.lifecycle.runtime.compose)
            // Coil itself is unnecessary here — every image renders through
            // shared/core:ui's MediaImage, which brings its own coil
            // dependency (calendar precedent; the legacy build carried coil +
            // coil-okhttp for this module).
            // Koin owns the newsletter ViewModel (V3 feature conveyor: one
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
        }
    }
}

// `compose.resources` is a nested extension with no generated Kotlin-DSL
// accessor; configure it explicitly. Same package as the legacy :feature:newsletter
// so migrated files keep their `com.raulshma.jellyplay.feature.newsletter` imports;
// generated accessors land in `...feature.newsletter.generated.resources`.
val composeResources = (compose as ExtensionAware).extensions.getByName("resources") as org.jetbrains.compose.resources.ResourcesExtension
composeResources.packageOfResClass = "com.raulshma.jellyplay.feature.newsletter.generated.resources"
