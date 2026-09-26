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
