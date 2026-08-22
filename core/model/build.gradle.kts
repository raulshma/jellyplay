plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.raulshma.jellyplay.core.model"
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

// KMP cutover shim (docs/kmp-migration-plan.md §Phase C1): every type this
// module used to declare now lives in :shared:core:model under the identical
// package, so an api() re-export keeps all consumers compiling unchanged.
// Deleted together with this module at Phase X cutover.
dependencies {
    api(project(":shared:core:model"))
}
