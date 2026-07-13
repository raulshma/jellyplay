plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.raulshma.jellyplay.feature.subtitle.tester"
    compileSdk = 37

    defaultConfig {
        minSdk = 28
    }

    androidResources {
        // Keep raw subtitle samples uncompressed. ExoPlayer's RawResourceDataSource
        // needs an AssetFileDescriptor, which Android can't hand back for a
        // compressed resource ("This file can not be opened as a file descriptor;
        // it is probably compressed"). The tester now materializes these to files
        // anyway (so mpv/libVLC get file:// paths), but leaving them uncompressed
        // keeps android.resource:// usable for any future in-process consumer.
        noCompress.add("srt")
        noCompress.add("ass")
        noCompress.add("ssa")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests {
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
    implementation(project(":feature:player:video"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.tabler.icons.outline)
    implementation(libs.tabler.icons.filled)

    implementation(libs.navigation3.runtime)
    implementation(libs.navigation3.ui)
    implementation(libs.lifecycle.viewmodel.navigation3)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.hilt.navigation.compose)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
}
