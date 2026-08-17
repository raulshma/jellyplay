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
            // android.util.Log / other android-stub calls return defaults
            // instead of throwing, so OkHttp's logging path stays unit-testable.
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:datastore"))

    implementation(libs.jellyfin.core)
    implementation(libs.hilt.android)
    implementation(libs.androidx.collection.ktx)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)

    ksp(libs.hilt.android.compiler)

    // SLF4J - required by Jellyfin SDK
    implementation(libs.slf4j.api)
    runtimeOnly(libs.slf4j.nop)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp)
    testImplementation(libs.okhttp.mockwebserver)
    // Real org.json for unit tests: the android.jar stub returns nulls, which
    // silently defeats the WebSocket payload parsing this module tests.
    testImplementation(libs.org.json)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
}
