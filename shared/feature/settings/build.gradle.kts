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
        namespace = "com.raulshma.jellyplay.shared.feature.settings"
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

    // web breadth: the target compiles. The ViewModels still bind the
    // full core:data store/repository cluster (media/auth/seerr/arr/search-
    // history and more), which resolves only from the android+jvm DI graph —
    // the web stack registers no settings bindings, so web wiring stays with
    // the orchestrator's shared-wiring pass (shortcuts precedent). The
    // java.io seams went wasm-safe instead: SettingsBackupIo narrowed from
    // java.io streams to a text-level payload seam (the backup payload is one
    // JSON document), FileSize moved to jvmShared (java.io.File walk used
    // only by the android/desktop IO actuals), and the ConnectionProbe job
    // table got an expect/actual factory (ConcurrentHashMap is JVM-only).
    // core:data's jvmShared AdminRepository went behind the feature-local
    // ServerAdminActions seam (QuickDownloadActions template: jvmShared
    // adapter over the real single, honest no-op on web, gate folded into the
    // ViewModel). The karma/Chrome browser run stays off like
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
        // The JVM-side actuals shared by android + desktop (the FileSize walk
        // consumed by the storage/cache IO actuals, the ServerAdminActions
        // adapter over core:data's jvmShared AdminRepository single) — the
        // newsletter/requests jvmShared pattern.
        val jvmShared = create("jvmShared")
        jvmShared.dependsOn(getByName("commonMain"))
        getByName("androidMain") { dependsOn(jvmShared) }
        getByName("jvmMain") { dependsOn(jvmShared) }

        getByName("commonMain").dependencies {
            implementation(project(":shared:core:model"))
            implementation(project(":shared:core:designsystem"))
            implementation(project(":shared:core:data"))
            // Preference slice stores + PreferencesEditor + SettingsBackup.
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
            implementation(libs.navigation3.runtime)
            implementation(libs.navigation3.ui)
            implementation(libs.lifecycle.viewmodel)
            // collectAsStateWithLifecycle in the screens.
            implementation(libs.lifecycle.runtime.compose)
            implementation(libs.coil.compose)
            // Koin owns the settings ViewModels (V3 feature conveyor: one
            // framework per type — the Hilt annotations were stripped at the
            // move).
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            // LicensesScreen consumes aboutlibraries Library entities (KMP
            // artifact; JSON is loaded through the asset-reader seam).
            implementation(libs.aboutlibraries.core)
            // Backup JSON + home-layout preset share payloads.
            implementation(libs.kotlinx.serialization.json)
            // The scheduled-theme hour read (AppearanceSettingsScreen) runs
            // kotlinx.datetime — wasmJs has no java.util.Calendar.
            implementation(libs.kotlinx.datetime)
        }
        getByName("commonTest").dependencies {
            implementation(kotlin("test"))
        }
        getByName("jvmTest").dependencies {
            implementation(kotlin("test"))
            implementation(libs.coroutines.test)
            implementation(libs.mockk)
        }
        getByName("wasmJsMain").dependencies {
            // The PlatformIntents actual opens external links through
            // window.open (the only browser-backed seam; the rest degrade).
            implementation(libs.kotlinx.browser)
        }
        // Second documented shared→legacy edge (library/livetv precedent):
        // the Android messenger actual bridges legacy LocalUserMessageBus so
        // the Hilt-owned bus singleton stays the single instance.
        getByName("androidMain").dependencies {
        }
    }
}

// `compose.resources` is a nested extension with no generated Kotlin-DSL
// accessor; configure it explicitly. Same package as the legacy :feature:settings
// so migrated files keep their `com.raulshma.jellyplay.feature.settings` imports;
// generated accessors land in `...feature.settings.generated.resources`.
val composeResources = (compose as ExtensionAware).extensions.getByName("resources") as org.jetbrains.compose.resources.ResourcesExtension
composeResources.packageOfResClass = "com.raulshma.jellyplay.feature.settings.generated.resources"

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
        resolutionStrategy {
            dependencySubstitution {
                substitute(module("androidx.navigation3:navigation3-ui"))
                    .using(module(libs.jb.navigation3.ui.get().toString()))
                    .because("google navigation3-ui has no web artifacts; JB fork publishes the wasm klib")
            }
        // aboutlibraries-core 15.0.4's wasm klib was built by Kotlin 2.4.0
        // (ABI 2.4.0), which this repo's 2.3.21 compiler cannot LOAD — the
        // klib also drags kotlin-stdlib-wasm-js:2.4.0 in, whose rejection
        // blanks the whole stdlib (every compile error becomes "Cannot access
        // class kotlin.String"). 14.2.1's wasm klib is a Kotlin 2.3.20 build
        // (ABI 2.3.0, consumable — the same ABI rationale as
        // core:datastore's wasm datastore-preferences-core 1.3.0-alpha10
        // override). Target-scoped force: android/jvm keep the repo-wide
        // 15.0.4 pin; the entity API the Licenses screen reads
        // (Libs/Library/License fields) is unchanged between the two lines.
            force("com.mikepenz:aboutlibraries-core:14.2.1")
        }
    }
}
