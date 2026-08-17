import com.android.build.api.dsl.ManagedVirtualDevice

plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.androidx.baselineprofile.producer)
}

android {
    namespace = "com.raulshma.jellyplay.baselineprofile.tv"
    compileSdk = 37

    defaultConfig {
        minSdk = 28
        targetSdk = 37

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        missingDimensionStrategy("platform", "tv")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.art-profile"] = true

    // TV twin of :baselineprofile's ciPixel8: a headless Gradle Managed Device
    // booting an Android TV image, so the tv flavor's leanback UI installs and
    // launches without plugged-in hardware. "Television (1080p)" is the display
    // name of the SDK's tv_1080p device definition (AGP matches device profiles
    // by display name); the AOSP `android-tv` API 34 image is auto-downloaded
    // by AGP on demand. API 34+ so startup-profile rules are collected.
    // AGP 9 moved managedDevices under testOptions and replaced the old
    // `devices` container with allDevices.
    testOptions {
        managedDevices {
            allDevices.register<ManagedVirtualDevice>("ciTv1080p") {
                device = "Television (1080p)"
                apiLevel = 34
                systemImageSource = "android-tv"
            }
        }
    }
}

baselineProfile {
    // Run generation on the managed device above instead of requiring a
    // device connected via adb.
    managedDevices += "ciTv1080p"
    useConnectedDevices = false
}

dependencies {
    implementation(libs.junit)
    implementation(libs.androidx.junit)
    implementation(libs.androidx.espresso.core)
    implementation(libs.benchmark.macro.junit4)
    implementation(libs.androidx.uiautomator)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui.test)
    implementation(libs.compose.ui.test.manifest)
}
