plugins {
    id("jellyplay.kmp.library.base")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "com.raulshma.jellyplay.shared.core.datastore"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":shared:core:model"))
            // Cancellation-safe suspend wrappers — the module's flagged
            // suspend-fun sites ride the same seam as the rest of the tree.
            implementation(project(":shared:core:concurrency"))
            // Multiplatform core: Preferences/Key/edit + PreferenceDataStoreFactory
            // (the Android-only Context delegate stays in the legacy shim's DI).
            api(libs.datastore.preferences.core)
            api(libs.okio)
            // Koin module/qualifier types are public commonMain API;
            // koin-core publishes android/jvm.
            api(libs.koin.core)
            implementation(libs.kotlinx.serialization.json)
            // Annotation-only Compose usage (@Immutable/@Stable on preference
            // models), same pattern as :shared:core:model.
            implementation(project.dependencies.platform(libs.compose.bom))
            implementation(libs.compose.runtime)
        }
        getByName("androidMain").dependencies {
            // SecureKeyValueStorage android actual (EncryptedSharedPreferences).
            implementation(libs.security.crypto)
        }
        getByName("jvmMain").dependencies {
            // SecureKeyValueStorage desktop actual (OS keyring via JNA).
            implementation(libs.java.keyring)
        }
        // kotlin("test") + coroutines-test on commonTest cover both test lanes
        // through the commonTest → jvmTest hierarchy edge.
        commonTest.dependencies {
            implementation(libs.coroutines.test)
        }
        getByName("jvmTest").dependencies {
            implementation(libs.koin.test)
        }
    }
}

// ── JellyPlay settings catalog artifact ──────────────────────────────────────
// The generator (jvmMain .../tools/SettingsCatalogGenerator.kt) walks this
// module's PreferenceSpec declarations and emits the catalog the Jellyfin
// plugin embeds + serves to its dashboard. Regenerate after any spec row
// change; `checkSettingsCatalog` (below, wired into `check`) fails when the
// committed artifact is stale, so client and plugin cannot silently drift.

val settingsCatalogJvmMain = kotlin.jvm().compilations.getByName("main")
val settingsCatalogArtifact = rootProject.layout.projectDirectory.file(
    "jellyfin-plugin-jellyplay/src/Jellyfin.Plugin.JellyPlay/Resources/jellyplay-settings-catalog.json",
)
// The resource tables the catalog's human labels/descriptions resolve from —
// the settings-search table plus the screen tables whose non-ss_* title keys
// a few folded rows reuse. KEEP IN SYNC with searchStrings() in
// SettingsCatalogGeneratorTest (same tables, relative to the repo root).
val settingsCatalogStrings = listOf(
    "shared/feature/settings/src/commonMain/composeResources/values/strings_search.xml",
    "shared/feature/settings/src/commonMain/composeResources/values/strings_appearance.xml",
    "shared/feature/settings/src/commonMain/composeResources/values/strings_playback.xml",
    "shared/feature/settings/src/commonMain/composeResources/values/strings.xml",
    "shared/core/ui/src/commonMain/composeResources/values/strings.xml",
).map { rootProject.layout.projectDirectory.file(it).asFile.absolutePath }
val settingsCatalogClasspath =
    settingsCatalogJvmMain.output.allOutputs + settingsCatalogJvmMain.compileDependencyFiles

tasks.register<JavaExec>("generateSettingsCatalog") {
    group = "jellyplay"
    description = "Regenerates the JellyPlay settings catalog artifact embedded by the Jellyfin plugin."
    mainClass.set("com.raulshma.jellyplay.core.datastore.tools.SettingsCatalogGeneratorKt")
    classpath = settingsCatalogClasspath
    args(settingsCatalogStrings.flatMap { listOf("--strings", it) } + settingsCatalogArtifact.asFile.absolutePath)
}

tasks.register<JavaExec>("checkSettingsCatalog") {
    group = "verification"
    description = "Fails when the committed settings catalog artifact no longer matches the PreferenceSpec declarations."
    mainClass.set("com.raulshma.jellyplay.core.datastore.tools.SettingsCatalogGeneratorKt")
    classpath = settingsCatalogClasspath
    args(listOf("--check") + settingsCatalogStrings.flatMap { listOf("--strings", it) } + settingsCatalogArtifact.asFile.absolutePath)
}

tasks.named("check") { dependsOn("checkSettingsCatalog") }
