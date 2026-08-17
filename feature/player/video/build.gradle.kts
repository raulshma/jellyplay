plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.raulshma.jellyplay.feature.player.video"
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
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            // Robolectric must serve src/main/assets (FontProviderTest copies the
            // bundled subfont.ttf asset into cacheDir) and src/main/res to tests.
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:data"))
    implementation(project(":core:datastore"))
    implementation(project(":core:ui"))
    implementation(project(":feature:player:core"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.tabler.icons.outline)
    implementation(libs.tabler.icons.filled)
    implementation(libs.compose.animation)

    implementation(libs.navigation3.runtime)
    implementation(libs.navigation3.ui)
    implementation(libs.lifecycle.viewmodel.navigation3)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.media3.session)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.media3.extractor)

    implementation(libs.media3.cast)
    implementation(libs.media3.datasource)
    implementation(libs.media3.datasource.okhttp)
    // FFmpeg software audio decoder for codecs the platform can't decode
    // (DTS, MLP/TrueHD, EAC3, etc.). DefaultRenderersFactory loads it via
    // reflection when EXTENSION_RENDERER_MODE is ON (the default HW_PREFERRED
    // decoder mode), so no engine wiring is needed.
    implementation(libs.media3.ffmpeg.decoder)
    coreLibraryDesugaring(libs.android.desugar.jdk)
    implementation(libs.play.services.cast.framework)
    implementation(libs.mediarouter)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.okhttp)

    implementation(libs.libmpv)
    // libass wired into Media3's renderer pipeline — full ASS/SSA rendering
    // (positioning, fonts, animations, karaoke, vector drawing) on the
    // ExoPlayer backend. Bundles its own libass native .so per ABI.
    implementation(libs.ass.media)
    // Direct compile dep on ass-kt so ExoPlayerEngine can reach AssRender.setFontScale
    // (was only a transitive runtime dep before; SCALE font-size override needs it).
    implementation(libs.ass.kt)
    // Parse font family names from .ttf/.otf headers for the subtitle font picker.
    implementation(libs.truetype.parser)
    implementation(libs.libvlc.all)
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.hilt.navigation.compose)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    // The abstract MediaEngineContractTest base lives in :feature:player:core's
    // test fixtures; the NoOp + 3-adapter specimens below extend it.
    testImplementation(testFixtures(project(":feature:player:core")))
    // Shared stubMediaSessionPlayer() helper for the MediaSession tests here.
    testImplementation(testFixtures(project(":core:data")))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test)
    debugImplementation(libs.compose.ui.test.manifest)
}
