import com.android.build.api.dsl.ManagedVirtualDevice

plugins {
    alias(libs.plugins.android.test)
    // NOTE: no kotlin-compose plugin here — this module's macrobenchmark
    // sources use uiautomator only. The compose plugin made AGP register
    // generate<Variant>ComposePreviewRunfiles tasks, which crash under
    // androidx.baselineprofile 1.5.0 because it disables unit tests on the
    // benchmark variants via beforeVariants (Compose Preview needs the
    // unit-test variant to build the Layoutlib resource APK). See the
    // buildFeatures block below for the belt-and-braces part of the fix.
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
        // :app now carries a second dimension (distribution: store|fdroid);
        // resolve it to the Play/store line — the baseline-profile producer
        // targets the store release the same way it targets the phone flavor.
        missingDimensionStrategy("distribution", "store")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // This module has no Compose sources (macrobenchmark + uiautomator only),
    // so the Compose build feature stays off — belt-and-braces alongside
    // removing the kotlin-compose plugin above (AGP's generate<Variant>
    // ComposePreviewRunfiles registration conflicts with androidx
    // .baselineprofile 1.5.0, which disables unit tests on the benchmark
    // variants via beforeVariants).
    buildFeatures {
        compose = false
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
}
