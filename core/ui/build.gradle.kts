plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.raulshma.jellyplay.core.ui"
    compileSdk = 37

    defaultConfig {
        minSdk = 28
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlin {
        compilerOptions {
            freeCompilerArgs.add("-opt-in=androidx.compose.foundation.style.ExperimentalFoundationStyleApi")
        }
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:designsystem"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.tabler.icons.outline)
    implementation(libs.tabler.icons.filled)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.palette.ktx)
    implementation(libs.paging.compose)

    // Android TV
    implementation(libs.tv.material)

    implementation(libs.navigation3.runtime)
    implementation(libs.navigation3.ui)
    implementation(libs.lifecycle.viewmodel.navigation3)
    implementation(libs.kotlinx.serialization.json)
    implementation("androidx.compose.animation:animation-android")
    implementation(libs.media3.session)
    implementation(libs.media3.exoplayer)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.11.0")
    implementation(libs.smooth.corner.rect)
    implementation(libs.biometric.ktx)
}
