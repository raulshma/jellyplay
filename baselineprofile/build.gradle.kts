import com.android.build.api.dsl.ManagedVirtualDevice

plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.androidx.baselineprofile.producer)
}

android {
    namespace = "com.raulshma.jellyplay.baselineprofile"
    compileSdk = 37

    defaultConfig {
        minSdk = 28
        targetSdk = 37

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        missingDimensionStrategy("platform", "phone")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.art-profile"] = true

    // Headless Gradle Managed Device so profile generation needs no plugged-in
    // hardware: CI (GitHub ubuntu runners expose /dev/kvm) and local runs boot
    // this emulator on demand. API 34+ so startup-profile rules are collected.
    // AGP 9 moved managedDevices under testOptions and replaced the old
    // `devices` container with allDevices.
    testOptions {
        managedDevices {
            allDevices.register<ManagedVirtualDevice>("ciPixel8") {
                device = "Pixel 8"
                apiLevel = 34
                systemImageSource = "aosp"
            }
        }
    }
}

baselineProfile {
    // Run generation on the managed device above instead of requiring a
    // device connected via adb.
    managedDevices += "ciPixel8"
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
