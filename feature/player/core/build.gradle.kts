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
    // testFixtures hosts the abstract [MediaEngineContractTest] base class so
    // :feature:player:video test sources can extend it for the NoOp + 3 adapter
    // specimens (NoOpEngine is `internal`, so it can only be reached from a
    // module that already sees the engine package). AGP 9 supports testFixtures
    // for library modules.
    testFixtures {
        enable = true
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

    // testFixtures dependencies: the abstract MediaEngineContractTest base
    // class references [MediaEngine] and its supertypes
    // ([RemotePlayableEngine], [PlayerLifecycleCallbacks]) plus the engine
    // data types (EngineError, TimedCue, SubtitleEvent, …). AGP's testFixtures
    // source set does not inherit the main `implementation` classpath, so every
    // module the base class touches must be redeclared here. Available to every
    // consumer of this module's test fixtures (currently :feature:player:video).
    testFixturesImplementation(project(":core:model"))
    testFixturesImplementation(project(":core:data"))
    testFixturesImplementation(platform(libs.compose.bom))
    testFixturesImplementation(libs.compose.runtime)
    testFixturesImplementation(libs.kotlinx.coroutines.android)
    testFixturesImplementation(libs.media3.exoplayer)
    testFixturesImplementation(libs.junit)
    testFixturesImplementation(libs.coroutines.test)
    testFixturesImplementation(libs.androidx.test.core)
}
