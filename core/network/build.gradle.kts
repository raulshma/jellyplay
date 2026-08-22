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
    testOptions {
        unitTests {
            // Shim-resident code (realtime channels, shared NetworkLog.android
            // actual) calls android.util.Log; keep stub calls returning
            // defaults instead of throwing in local unit tests.
            isReturnDefaultValues = true
        }
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

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    // SLF4J - required by Jellyfin SDK
    implementation(libs.slf4j.api)
    runtimeOnly(libs.slf4j.nop)

    // Local-unit deps for the two realtime-channel tests that stayed with the
    // shim (their subjects depend on legacy @ApplicationScope / org.json WS
    // currency). Everything else moved to :shared:core:network jvmTest (C3).
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    // Real org.json so the channel's WS payload parsing runs outside android.jar
    // (the stub jar's org.json methods throw at test runtime).
    testImplementation(libs.org.json)
}
