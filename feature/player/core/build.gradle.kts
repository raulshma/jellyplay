plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.raulshma.jellyplay.feature.player.core"

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
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:data"))
    implementation(platform(libs.compose.bom))
    // MediaEngine.kt annotates its @Immutable / @Stable data classes with
    // androidx.compose.runtime markers (same pattern as :core:model). Only the
    // runtime artifact is needed — there are no @Composable functions in this
    // contract module, so the compose compiler plugin is not applied.
    implementation(libs.compose.runtime)
    implementation(libs.kotlinx.coroutines.android)
    // media3-common (androidx.media3.common.Player) arrives transitively via
    // media3-exoplayer; declared explicitly so the engine contract module has
    // a stable, declared surface for the Player type used in MediaEngine /
    // RemotePlayableEngine.
    implementation(libs.media3.exoplayer)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.robolectric)
}
