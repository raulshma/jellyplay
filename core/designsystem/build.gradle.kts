plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.raulshma.jellyplay.core.designsystem"
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

// KMP cutover shim (docs/kmp-migration-plan.md §Phase C1): the theme now lives
// in :shared:core:designsystem under the identical package; this empty module
// re-exports it so every consumer keeps compiling unchanged. Deleted at Phase X.
dependencies {
    api(project(":shared:core:designsystem"))
}
