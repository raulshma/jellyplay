plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.raulshma.jellyplay.core.database"
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
}

// KMP cutover shim (docs/kmp-migration-plan.md §Phase C2): entities/DAOs/
// migrations now live in :shared:core:database (Room KMP, android+jvm) under
// the identical package; this module keeps only the Android Hilt wiring
// (Room.databaseBuilder with Context + Keystore-backed TokenCipher binding)
// and re-exports the shared module. Deleted at Phase X cutover.
dependencies {
    api(project(":shared:core:database"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
}
