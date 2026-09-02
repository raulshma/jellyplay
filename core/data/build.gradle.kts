plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.raulshma.jellyplay.core.data"
    compileSdk = 37

    defaultConfig {
        minSdk = 28
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        buildConfig = true
        resValues = false
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            // Pure-JVM tests in this module exercise code paths that touch
            // `android.os.SystemClock` / `android.util.Log` (TtlCache TTLs,
            // SleepTimerManager, download workers). Return default values for
            // unmocked Android APIs instead of throwing — matches the nine
            // other modules that already set this flag.
            isReturnDefaultValues = true
        }
    }
    // testFixtures hosts the shared `stubMediaSessionPlayer()` helper used by
    // PlaybackSessionManagerPriorityTest (here) and MediaSessionControllerTest
    // (in :feature:player:video) — both build a real MediaSession around a
    // mockk Player and need the identical getter pinning so MediaSession's
    // PlaybackStateCompat construction doesn't NPE on null boxed returns.
    testFixtures {
        enable = true
    }
}

dependencies {
    // KMP cutover shim (docs/kmp-migration-plan.md §Phase C4): portable data
    // classes migrate into :shared:core:data under the identical package;
    // this module keeps all of its code until each file moves and re-exports
    // the shared module so every consumer keeps compiling unchanged. DI
    // wiring moves to Koin at §Phase C4/X.
    api(project(":shared:core:data"))

    implementation(project(":shared:core:model"))
    implementation(project(":shared:core:network"))
    implementation(project(":shared:core:database"))
    implementation(project(":shared:core:datastore"))

    // Wave 8A/8B: Koin owns construction (androidCoreDataModule +
    // CoreDataWorkerFactory). No Hilt remains in this module.
    implementation(libs.koin.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.paging.runtime)
    implementation(libs.work.runtime.ktx)
    implementation(libs.room.ktx)
    implementation(libs.media3.session)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.cast)
    implementation(libs.media3.datasource)
    implementation(libs.media3.datasource.okhttp)
    implementation(libs.media3.database)
    // FFmpeg software audio decoder for codecs MediaCodec lacks on most
    // devices (DTS, MLP/TrueHD, EAC3, etc.). Auto-loaded by
    // DefaultRenderersFactory via reflection when EXTENSION_RENDERER_MODE is ON.
    implementation(libs.media3.ffmpeg.decoder)
    coreLibraryDesugaring(libs.android.desugar.jdk)
    implementation(libs.play.services.cast.framework)
    implementation(libs.okhttp)
    implementation(libs.palette.ktx)
    implementation(libs.androidx.core.ktx)
    implementation(libs.lifecycle.process)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.navigation3.runtime)
    // TV provider (Watch Next / preview channels) — R8 strips for phone release.
    implementation(libs.tvprovider)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.work.testing)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.okhttp.mockwebserver)
    // Shared coroutine test rule (MainDispatcherRule) for the JVM holder
    // suites — same dependency every feature module's tests already declare.
    testImplementation(project(":core:testing"))

    // testFixtures dependencies: the shared stubMediaSessionPlayer() helper
    // builds a mockk<Player> against media3-common. AGP's testFixtures source
    // set does not inherit the main `implementation` classpath, so media3 + the
    // stub's own test deps must be redeclared here. Available to every consumer
    // of this module's test fixtures (currently the two MediaSession test sites).
    testFixturesImplementation(libs.media3.session)
    testFixturesImplementation(libs.media3.exoplayer)
    testFixturesImplementation(libs.mockk)
}
