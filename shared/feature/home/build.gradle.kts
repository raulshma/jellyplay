import org.gradle.api.plugins.ExtensionAware
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    android {
        namespace = "com.raulshma.jellyplay.shared.feature.home"
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

    // web breadth: the target compiles. The ViewModel/refresher's
    // jvmShared core:data handles went behind five feature-local seams
    // (AudioTrackDownloads/QuickDownloadActions templates, plus the livetv
    // clock-narrowing and locale-split precedents):
    //  - HomeClock over TimeSource (the java.time today(zone) surface stays
    //    JVM; wasm derives the same system-zone calendar day via kotlinx);
    //  - HomeDownloadActions over MediaDownloadActions + SeriesEpisodeDownloads
    //    over DownloadRepository (wasm: no download pipeline, honest empty);
    //  - HomeSyncStatus(+Factory) over SyncStatusStateHolder (wasm: the
    //    outbox is genuinely idle — no downloads exist to queue watch events);
    //  - HomeNewsletterGate over NewsletterTriggerManager (wasm: no
    //    notification pipeline, banner never shows).
    // HomeSession narrowed to its commonMain SessionIdentityProvider
    // interface (: bound on jvmShared AND wasm graphs). OfflineHomeSections'
    // java.util.PriorityQueue/java.time parse cluster ported to a bounded
    // keeper + kotlinx-datetime (deltas documented in place); wall-clock
    // reads ride core:model's wallNowMillis. Web routing/registration stays
    // with the orchestrator's integration pass. The karma/Chrome browser run
    // stays off like core:ui/core:network — jvmTest pins the semantics.
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
        // The JVM-side bindings (the web seam adapters over the core:data
        // jvmShared singles, plus the SyncStatusStateHolderFactory single
        // whose deps are jvmShared) — the player-audio jvmShared
        // platform-module pattern.
        val jvmShared = create("jvmShared")
        jvmShared.dependsOn(getByName("commonMain"))
        getByName("androidMain") { dependsOn(jvmShared) }
        getByName("jvmMain") { dependsOn(jvmShared) }

        getByName("commonMain").dependencies {
            implementation(project(":shared:core:model"))
            implementation(project(":shared:core:designsystem"))
            implementation(project(":shared:core:data"))
            //  ripple: ArrRepository's calendar window now takes
            // kotlinx.datetime.LocalDate — HomeRefresher converts java.time at the boundary.
            implementation(libs.kotlinx.datetime)
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
            // dropped with the syncplay move: navigation entries use
            // entry<Route> from the nav3 runtime/ui artifacts only.)
            implementation(libs.navigation3.runtime)
            implementation(libs.navigation3.ui)
            implementation(libs.lifecycle.viewmodel)
            // collectAsStateWithLifecycle + LocalLifecycleOwner (hero rotation
            // RESUMED gate) in the screens.
            implementation(libs.lifecycle.runtime.compose)
            // coil3.Size in HomeHero/HomeMediaRows image requests — coil3 is
            // not api-exported by shared/core:ui (insights precedent).
            implementation(libs.coil.compose)
            // Koin owns the home ViewModel (V3 feature conveyor: one
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
        getByName("androidMain").dependencies {
            // The process-lifecycle actual (refresher start/stop on app
            // foreground/background) registers against
            // ProcessLifecycleOwner — Android-only API, same androidMain dep
            // as shared/core:data's AndroidOfflineModeManager.
            implementation(libs.lifecycle.process)
            // The report-fully-drawn actual reads LocalActivity (TTFD metric).
            implementation(libs.androidx.activity.compose)
        }
    }
}

// `compose.resources` is a nested extension with no generated Kotlin-DSL
// accessor; configure it explicitly. Same package as the legacy :feature:home
// so migrated files keep their `com.raulshma.jellyplay.feature.home` imports;
// generated accessors land in `...feature.home.generated.resources`.
val composeResources = (compose as ExtensionAware).extensions.getByName("resources") as org.jetbrains.compose.resources.ResourcesExtension
composeResources.packageOfResClass = "com.raulshma.jellyplay.feature.home.generated.resources"

// google's androidx.navigation3:navigation3-ui publishes no web artifacts at
// all (android AAR + jvm/linux stubs only), so every wasmJs configuration of
// this module fails dependency resolution unless it points at JetBrains'
// fork of the same release line — same package, ABI-stable surface. Scoped
// to wasmJs-named configurations so android/jvm graphs keep resolving
// google's published variants exactly as before ( S1/R2; the
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
