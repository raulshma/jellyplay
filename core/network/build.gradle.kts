plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.raulshma.jellyplay.core.network"
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

// KMP cutover shim (docs/kmp-migration-plan.md §Phase C3): client classes live
// in :shared:core:network under the identical package; this module keeps
// only the Android DI wiring (Hilt modules, the multicast-lock guard, and the
// realtime channels whose @ApplicationScope qualifier is provided by the
// legacy :core:datastore shim) and re-exports the shared module so every
// consumer keeps compiling unchanged. DI wiring moves to Koin at §Phase C4/X.
dependencies {
    api(project(":shared:core:network"))

    // ApplicationScope qualifier + CoroutineScope provider consumed by the
    // realtime channels that stayed in this shim.
    implementation(project(":core:datastore"))

    implementation(libs.jellyfin.core)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.kotlinx.serialization.json)

    // Koin runtime for the Hilt→Koin bridges in NetworkModule (C4).
    implementation(libs.koin.core)

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    // SLF4J - required by Jellyfin SDK
    implementation(libs.slf4j.api)
    runtimeOnly(libs.slf4j.nop)
}
