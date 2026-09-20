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

    // The ViewModels bind the full core:data store/repository cluster
    // (media/auth/seerr/arr/search-history and more), which resolves only
    // from the android+jvm DI graph. The java.io seams were reworked for
    // commonMain: SettingsBackupIo narrowed from java.io streams to a
    // text-level payload seam (the backup payload is one JSON document),
    // FileSize moved to jvmShared (java.io.File walk used only by the
    // android/desktop IO actuals), and the ConnectionProbe job table got
    // an expect/actual factory (ConcurrentHashMap is JVM-only).
    // core:data's jvmShared AdminRepository went behind the feature-local
    // ServerAdminActions seam (QuickDownloadActions template: jvmShared
    // adapter over the real single, gate folded into the ViewModel).
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
            // kotlinx.datetime — java.util.Calendar stays JVM-side.
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
