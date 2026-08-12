plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.androidx.baselineprofile)
    alias(libs.plugins.aboutLibraries)
}

android {
    namespace = "com.raulshma.jellyplay"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.raulshma.jellyplay"
        minSdk = 28
        targetSdk = 37
        versionCode = (project.findProperty("versionCode") as? String)?.toInt() ?: 1
        versionName = project.findProperty("versionName") as? String ?: "0.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        resourceConfigurations += listOf("en", "de", "es", "fr", "it", "pt", "ja", "ko", "zh")

        manifestPlaceholders["versionName"] = versionName ?: "0.0.1"
    }

    flavorDimensions += "platform"
    productFlavors {
        create("phone") {
            dimension = "platform"
        }
        create("tv") {
            dimension = "platform"
            applicationIdSuffix = ".tv"
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a")
            if (gradle.startParameter.taskNames.any { it.contains("debug", ignoreCase = true) }) {
                include("x86_64")
            }
            isUniversalApk = true
        }
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file("jellyplay-release.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
            keyAlias = System.getenv("KEY_ALIAS") ?: ""
            keyPassword = System.getenv("KEY_PASSWORD") ?: ""
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".dev"
            resValue("string", "app_name", "JellyPlay Dev")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val hasSigning = !System.getenv("KEYSTORE_PASSWORD").isNullOrBlank()
            if (hasSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        resValues = true
    }

    // i18n: locales are translated in bulk but added incrementally per module.
    // Without this, release lint fails on the first untranslated key (abortOnError
    // defaults to true). All three reference apps under scratch/ do the same.
    lint {
        disable += "MissingTranslation"
    }

    packaging {
        jniLibs {
            pickFirsts += listOf(
                "lib/arm64-v8a/libc++_shared.so",
                "lib/armeabi-v7a/libc++_shared.so",
                "lib/x86/libc++_shared.so",
                "lib/x86_64/libc++_shared.so"
            )
        }
    }
}

// AboutLibraries — collect the full runtime dependency graph (this is the only
// module that aggregates every :core:* and :feature:* project) and export the
// SPDX license metadata into assets. Parsed at runtime by :feature:settings.
// phoneRelease is the canonical list; the TV flavor adds no new dependencies.
aboutLibraries {
    collect { includePlatform = false }
    export {
        outputFile = file("src/main/assets/aboutlibraries.json")
        excludeFields.addAll("funding", "developers")
        prettyPrint = false
    }
}

// Keep the generated asset in sync with the dependency graph on every build.
tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn("exportLibraryDefinitions")
}

dependencies {
    // Explicitly add libmpv first so pickFirsts grabs its newer libc++_shared.so
    implementation(libs.libmpv)
    
    implementation(project(":core:model"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:network"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(project(":core:data"))
    implementation(project(":core:ui"))
    implementation(project(":core:notification"))
    implementation(project(":feature:auth"))
    implementation(project(":feature:home"))
    implementation(project(":feature:library"))
    implementation(project(":feature:search"))
    implementation(project(":feature:details"))
    implementation(project(":feature:player:video"))
    implementation(project(":feature:player:core"))
    implementation(project(":feature:player:audio"))
    implementation(project(":feature:player:live"))
    implementation(project(":feature:downloads"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:music"))
    implementation(project(":feature:livetv"))
    implementation(project(":feature:syncplay"))
    implementation(project(":feature:subtitle-tester"))
    implementation(project(":feature:editor"))
    implementation(project(":feature:admin"))
    implementation(project(":feature:onboarding"))
    implementation(project(":feature:newsletter"))
    implementation(project(":feature:insights"))
    implementation(project(":feature:requests"))
    implementation(project(":feature:shortcuts"))
    implementation(project(":feature:arrqueue"))
    implementation(project(":feature:calendar"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.tabler.icons.outline)
    implementation(libs.tabler.icons.filled)
    implementation(libs.compose.material3.adaptive)
    implementation(libs.compose.material3.adaptive.layout)
    implementation(libs.compose.material3.adaptive.navigation)
    implementation(libs.compose.material3.adaptive.navigation.suite)
    implementation(libs.compose.animation)

    // Android TV - shared dependency so src/main can compile. R8 will strip it for phone release builds.
    implementation(libs.tv.material)

    implementation(libs.navigation3.runtime)
    implementation(libs.navigation3.ui)
    implementation(libs.lifecycle.viewmodel.navigation3)

    implementation(libs.work.runtime.ktx)
    implementation(libs.datastore.preferences)

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.hilt.work)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.play.services.cast.framework)

    // Required by the media3-ffmpeg-decoder native extension (pulled in via
    // :feature:player:video). Must also be enabled at the app dex entry point.
    coreLibraryDesugaring(libs.android.desugar.jdk)

    implementation(libs.media3.session)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test)
    debugImplementation(libs.compose.ui.test.manifest)

    // Generates and merges a Baseline Profile from the :baselineprofile
    // macrobenchmark module. AGP installs the resulting baseline-prof.txt /
    // startup-prof into release builds.
    // The androidx.baselineprofile 1.5.x consumer plugin (required for AGP 9)
    // exposes the producer via the `baselineProfile` configuration.
    "baselineProfile"(project(":baselineprofile"))
}
