plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.raulshma.jellyplay.core.designsystem"
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
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)
    implementation(libs.tabler.icons.outline)
    implementation(libs.tabler.icons.filled)
    implementation(libs.palette.ktx)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.androidx.core.ktx)
    api(libs.smooth.corner.rect)

    // Android TV
    implementation(libs.tv.material)
}
