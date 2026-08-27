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
        namespace = "com.raulshma.jellyplay.shared.feature.calendar"
        compileSdk = 37
        minSdk = 28
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    // Wave 16A: the calendar slice follows requests (wave 15B) onto the web
    // target — the web shell renders UpcomingCalendarScreen behind the
    // Route.UpcomingCalendar entry. The old blocker (java.time in commonMain)
    // is gone: the grouping helpers, VM month windows, and screen date-picker
    // math all run kotlinx.datetime, and the two locale formatters moved
    // behind the CalendarDateLabels expect/actual seam (jvmShared = the
    // verbatim java.time bodies, wasmJsMain = fixed-English formatting with a
    // documented degrade). The karma/Chrome browser run stays off like
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
        // The java.time actuals for CalendarDateLabels.kt (JDK + the kotlinx
        // java-converters only — no module deps).
        val jvmShared = create("jvmShared")
        jvmShared.dependsOn(getByName("commonMain"))
        getByName("androidMain") { dependsOn(jvmShared) }
        getByName("jvmMain") { dependsOn(jvmShared) }

        getByName("commonMain").dependencies {
            implementation(project(":shared:core:model"))
            implementation(project(":shared:core:designsystem"))
            implementation(project(":shared:core:data"))
            // Wave 16A: the whole module runs kotlinx.datetime (wasmJs
            // purification) — grouping helpers, VM month windows, and the
            // date-picker epoch math included.
            implementation(libs.kotlinx.datetime)
            // ExperimentalStore (DIRECT_ARR_INTEGRATION flag slice).
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
            // Nav3 ships KMP variants from google maven directly — no mirror.
            // (The legacy build's lifecycle-viewmodel-navigation3 edge was
            // dropped: no calendar file imports it — navigation entries use
            // entry<Route> from the nav3 runtime/ui artifacts only, syncplay
            // precedent.)
            implementation(libs.navigation3.runtime)
            implementation(libs.navigation3.ui)
            implementation(libs.lifecycle.viewmodel)
            // collectAsStateWithLifecycle in the screen.
            implementation(libs.lifecycle.runtime.compose)
            // Coil itself is unnecessary here — the only image is rendered
            // through shared/core:ui's MediaImage, which brings its own coil
            // dependency (legacy build carried coil + coil-okhttp for the
            // module; the shared one rides MediaImage's).
            // Koin owns the calendar ViewModel (V3 feature conveyor: one
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
        }
        getByName("androidMain").dependencies {
            // The user-messenger actual bridges to the app-wide
            // LocalUserMessageBus, which still lives in the legacy Android-only
            // :core:ui shim (plan §V1a list) until its own conveyor move — same
            // transition-period relationship as the livetv conveyor's
            // AndroidLiveTvMessenger (4th documented shared→legacy :core:ui
            // androidMain edge after library/livetv/admin), dies at Phase X.
            implementation(project(":core:ui"))
        }
    }
}

// `compose.resources` is a nested extension with no generated Kotlin-DSL
// accessor; configure it explicitly. Same package as the legacy :feature:calendar
// so migrated files keep their `com.raulshma.jellyplay.feature.calendar` imports;
// generated accessors land in `...feature.calendar.generated.resources`.
val composeResources = (compose as ExtensionAware).extensions.getByName("resources") as org.jetbrains.compose.resources.ResourcesExtension
composeResources.packageOfResClass = "com.raulshma.jellyplay.feature.calendar.generated.resources"

// google's androidx.navigation3:navigation3-ui publishes no web artifacts at
// all (android AAR + jvm/linux stubs only), so every wasmJs configuration of
// this module fails dependency resolution unless it points at JetBrains'
// fork of the same release line — same package, ABI-stable surface. Scoped
// to wasmJs-named configurations so android/jvm graphs keep resolving
// google's published variants exactly as before (spike w-10C S1/R2; the
// identical block lives in shared/core/ui and shared/feature/requests).
configurations.configureEach {
    if (name.lowercase().contains("wasmjs")) {
        resolutionStrategy.dependencySubstitution {
            substitute(module("androidx.navigation3:navigation3-ui"))
                .using(module(libs.jb.navigation3.ui.get().toString()))
                .because("google navigation3-ui has no web artifacts; JB fork publishes the wasm klib")
        }
    }
}
