plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.raulshma.jellyplay.core.datastore"
    compileSdk = 37

    defaultConfig {
        minSdk = 28
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        buildConfig = false
        resValues = false
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

// KMP cutover shim (docs/kmp-migration-plan.md §Phase C2): store classes live
// in :shared:core:datastore under the identical package; this module keeps
// only the Android DI wiring (Hilt modules + the Context-based user_prefs
// delegate) and re-exports the shared module so every consumer keeps
// compiling unchanged. DI wiring moves to Koin at §Phase C4/X.
dependencies {
    api(project(":shared:core:datastore"))

    // Context.preferencesDataStore delegate + PreferenceDataStoreFactory
    // (the Android artifact; the multiplatform core comes via the shared api).
    implementation(libs.datastore.preferences)
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
}
