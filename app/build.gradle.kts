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

        manifestPlaceholders["versionName"] = versionName ?: "0.0.1"
    }

    // AGP 9 replacement for the legacy defaultConfig.resourceConfigurations.
    androidResources {
        localeFilters += listOf("en", "de", "es", "fr", "it", "pt", "ja", "ko", "zh")
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
                // x86_64 for modern emulators; x86 for the 32-bit-only Android TV
                // system images (API 30 and older).
                include("x86_64")
                include("x86")
            }
            // Universal (all 4 ABIs of the native player stacks) ships for
            // sideload only; local debug builds skip packaging it.
            isUniversalApk = !gradle.startParameter.taskNames.any { it.contains("debug", ignoreCase = true) }
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
        // Nothing in app/src references BuildConfig (version info flows through
        // manifestPlaceholders).
        buildConfig = false
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
// SPDX license metadata into assets. Parsed at runtime by :shared:feature:settings.
// phoneRelease is the canonical list; the TV flavor adds no new dependencies.
aboutLibraries {
    collect { includePlatform = false }
    export {
        outputFile = file("src/main/assets/aboutlibraries.json")
        excludeFields.addAll("funding", "developers")
        prettyPrint = false
    }
}

// The exported asset regenerates on demand (./gradlew :app:exportLibraryDefinitions)
// rather than on every preBuild — it only changes when the dependency graph does.

// Baseline Profile consumers. The androidx.baselineprofile 1.5.x plugin
// resolves producers per variant: dependencies are the merge of the `main`,
// flavor, build type and variant entries below. The global `baselineProfile`
// configuration cannot hold both producers — it extends into every variant
// and would run each generator against both flavors — so each platform
// flavor names its own producer module instead:
// - phone → :baselineprofile    (phone applicationId, ciPixel8 GMD)
// - tv    → :baselineprofile-tv (TV applicationId, ciTv1080p GMD)
baselineProfile {
    // Generated profiles are written into the flavor source sets
    // (src/<variant>Release/generated/baselineProfiles) so they can be
    // committed and regenerate on demand, like aboutlibraries.json. Builds
    // not preceded by a generate step pick up the committed files instead of
    // producing profile-less APKs.
    saveInSrc = true
    variants {
        create("phone") {
            from(project(":baselineprofile"))
        }
        create("tv") {
            from(project(":baselineprofile-tv"))
        }
    }
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
    implementation(project(":shared:feature:home"))
    implementation(project(":shared:feature:library"))
    implementation(project(":shared:feature:search"))
    implementation(project(":feature:details"))
    implementation(project(":feature:player:video"))
    implementation(project(":feature:player:core"))
    implementation(project(":feature:player:audio"))
    implementation(project(":feature:player:live"))
    implementation(project(":shared:feature:settings"))
    implementation(project(":shared:feature:music"))
    implementation(project(":shared:feature:livetv"))
    implementation(project(":shared:feature:downloads"))
    implementation(project(":shared:feature:syncplay"))
    implementation(project(":shared:feature:subtitle-tester"))
    implementation(project(":shared:feature:editor"))
    implementation(project(":shared:feature:admin"))
    implementation(project(":shared:feature:onboarding"))

    implementation(project(":shared:feature:newsletter"))
    implementation(project(":shared:feature:insights"))

    implementation(project(":shared:feature:requests"))
    implementation(project(":shared:feature:shortcuts"))
    implementation(project(":shared:feature:arrqueue"))
    implementation(project(":shared:feature:calendar"))

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

    // Koin composition root (Phase C4): startKoin in JellyPlayApplication
    // loads the shared-module definitions; Hilt shims bridge to them.
    implementation(libs.koin.core)
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
    // PlayerActivityArgs round-trip test exercises android.content.Intent
    // extras on the JVM (same setup as the nine modules already using it).
    testImplementation(libs.robolectric)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test)
    // NavigationRouteTest round-trips every Route through nav3's NavKeySerializer,
    // which encodes/decodes via kotlinx.serialization Json.
    androidTestImplementation(libs.kotlinx.serialization.json)
    debugImplementation(libs.compose.ui.test.manifest)

    // Baseline profile producers are wired per flavor in the `baselineProfile`
    // extension block above (:baselineprofile for phone,
    // :baselineprofile-tv for tv) instead of a global `baselineProfile`
    // configuration entry, which cannot disambiguate the two flavors.
}
