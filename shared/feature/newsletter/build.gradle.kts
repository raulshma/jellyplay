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
            // (The legacy build's lifecycle-viewmodel-navigation3 edge was
            // dropped with the syncplay move: no newsletter file imports it —
            // the navigation entries use entry<Route> from the nav3 runtime/ui
            // artifacts only, syncplay precedent.)
            // Coil itself is unnecessary here — every image renders through
            // shared/core:ui's MediaImage, which brings its own coil
            // dependency (calendar precedent; the legacy build carried coil +
            // coil-okhttp for this module).
        }
        // (kotlin("test") comes from the convention plugin.)
        getByName("jvmTest").dependencies {
            implementation(libs.coroutines.test)
            implementation(libs.mockk)
        }
    }
}

// The compose-resources `packageOfResClass`
// (`...feature.newsletter.generated.resources`, same as the legacy value) is a
// path-derived default from the convention plugin now — see
// KmpLibraryComposePlugin.
