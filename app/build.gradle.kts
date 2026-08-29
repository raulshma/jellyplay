import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
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
    // defaults to true).
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

// ---------------------------------------------------------------------------
// Compose-resources APK guard (wave 22c, audit finding F1 — the wave-21 P0
// made automatic). Wave 21's launch-blocking crash: the AGP-9 KMP library
// plugin leaves android resources OFF by default, so a shared module can ship
// generated `Res` accessor classes in the APK while its backing `.cvr` string
// assets are silently missing (23 of 24 resource-carrying modules were in
// exactly that state until c6da8ff8a added
// `androidResources { enable = true }` across the tree). No compile gate can
// see it — the accessors compile fine, the MissingResourceException only
// fires on the first string read at runtime — so this task opens the BUILT
// phoneDebug APK as a zip instead and asserts, for every resource-carrying
// shared module, that its assets/composeResources/<packageOfResClass>/
// directory exists with at least one .cvr entry per locale:
//   * the module list is derived from the source tree at EXECUTION time
//     (every shared/*/* module with a commonMain/composeResources directory)
//     — deliberately not hardcoded, so a NEW resource-carrying module without
//     the enable flag fails here, and a module that stops carrying resources
//     drops out of scope by itself;
//   * the per-module asset namespace is the module's compose
//     `packageOfResClass` (parsed from its build.gradle.kts; fallback
//     `<android namespace>.generated.resources`), which is exactly the
//     directory name compose-resources creates under assets/composeResources/
//     in the APK (verified against the real phoneDebug artifact, wave 21:
//     e.g. shared/feature/home → com.raulshma.jellyplay.feature.home.
//     generated.resources/values-de/strings.commonMain.cvr — note the legacy
//     import paths, NOT the kotlin.android namespace);
//   * the locale set derives from the module's values/values-xx directory
//     names (today 9 per module: values, -de, -es, -fr, -it, -ja, -ko, -pt,
//     -zh) and the derivation reproduces the wave-21 measured baseline of
//     216 .cvr entries = 24 modules × 9 locales.
// Always re-runs (the zip scan is cheap): a cached "PASS" would defeat the
// point of a guard. TV flavor ships the same merged assets graph; the phone
// artifact is the guard's assertion surface.
// ---------------------------------------------------------------------------
abstract class VerifyPhoneDebugComposeResourcesTask : DefaultTask() {
    /** Directory :app:assemblePhoneDebug writes the per-ABI split APKs into. */
    @get:Internal
    abstract val apkDir: DirectoryProperty

    /** Repo root — the shared/ source tree is scanned at execution time. */
    @get:Internal
    abstract val repoRoot: DirectoryProperty

    @TaskAction
    fun verify() {
        val root = repoRoot.get().asFile
        val sharedRoot = root.resolve("shared")
        if (!sharedRoot.isDirectory) {
            throw GradleException("verifyPhoneDebugComposeResources: shared/ tree not found under $root")
        }

        // 1. Expected modules + locale sets, derived NOW from the source tree.
        //    (module path, asset namespace, locale dir names)
        val expected = mutableListOf<Triple<String, String, List<String>>>()
        for (group in sharedRoot.listFiles { f -> f.isDirectory }.orEmpty()) {
            for (module in group.listFiles { f -> f.isDirectory }.orEmpty()) {
                val resRoot = module.resolve("src/commonMain/composeResources")
                if (!resRoot.isDirectory) continue
                val script = module.resolve("build.gradle.kts").readText()
                val namespace = Regex("""packageOfResClass\s*=\s*"([^"]+)"""").find(script)?.groupValues?.get(1)
                    ?: Regex("""namespace\s*=\s*"([^"]+)"""").find(script)?.groupValues?.get(1)
                        ?.plus(".generated.resources")
                    ?: throw GradleException(
                        "verifyPhoneDebugComposeResources: shared/${group.name}/${module.name} carries " +
                            "composeResources but its build.gradle.kts declares neither packageOfResClass " +
                            "nor namespace — cannot derive its assets/composeResources/ directory"
                    )
                val localeDirs = resRoot
                    .listFiles { f -> f.isDirectory && (f.name == "values" || f.name.startsWith("values-")) }
                    .orEmpty().map { it.name }.sorted()
                expected.add(Triple("shared/${group.name}/${module.name}", namespace, localeDirs))
            }
        }
        expected.sortBy { it.first }
        if (expected.isEmpty()) {
            throw GradleException(
                "verifyPhoneDebugComposeResources: no shared/*/* module carries " +
                    "commonMain/composeResources — guard input vanished?"
            )
        }

        // 2. The built APKs (per-ABI splits carry identical assets — check all).
        val apkDirFile = apkDir.get().asFile
        val apks = apkDirFile.listFiles { f -> f.isFile && f.name.endsWith(".apk") }.orEmpty()
        if (apks.isEmpty()) {
            throw GradleException(
                "verifyPhoneDebugComposeResources: no APK found in $apkDirFile — " +
                    "run :app:assemblePhoneDebug first"
            )
        }

        val localePairs = expected.sumOf { it.third.size }
        logger.lifecycle(
            "verifyPhoneDebugComposeResources: ${expected.size} resource-carrying shared modules, " +
                "$localePairs expected module/locale pairs (wave-21 baseline: 24 modules x 9 locales = 216)"
        )

        // 3. Assert every (module, locale) pair has a .cvr asset in every APK.
        val failures = mutableListOf<String>()
        for (apk in apks) {
            val entries = ZipFile(apk).use { zip ->
                zip.entries().toList().map { it.name }
            }
            for ((modulePath, namespace, localeDirs) in expected) {
                val prefix = "assets/composeResources/$namespace/"
                val moduleEntries = entries.filter { it.startsWith(prefix) }
                if (moduleEntries.isEmpty()) {
                    failures += "${apk.name}: $modulePath — $prefix missing entirely " +
                        "(androidResources { enable = true } absent or copyAndroidMainComposeResourcesToAndroidAssets never ran?)"
                    continue
                }
                for (localeDir in localeDirs) {
                    val hasCvr = moduleEntries.any {
                        it.startsWith("$prefix$localeDir/") && it.endsWith(".cvr")
                    }
                    if (!hasCvr) {
                        failures += "${apk.name}: $modulePath — no .cvr asset under $prefix$localeDir/ " +
                            "(locale '${localeDir.removePrefix("values-").ifEmpty { "base" }}' not packaged)"
                    }
                }
            }
        }

        // 4. Fail loudly, naming the first missing module/locale (then all of them).
        if (failures.isNotEmpty()) {
            throw GradleException(
                "verifyPhoneDebugComposeResources FAILED — first missing module/locale:\n  ${failures.first()}\n" +
                    "  all ${failures.size} gap(s):\n  " + failures.joinToString("\n  ")
            )
        }
        logger.lifecycle(
            "verifyPhoneDebugComposeResources: OK — every expected .cvr asset is packaged " +
                "in ${apks.size} phoneDebug APK(s)"
        )
    }
}

val verifyPhoneDebugComposeResources = tasks.register<VerifyPhoneDebugComposeResourcesTask>("verifyPhoneDebugComposeResources") {
    group = "verification"
    description = "Wave-21 P0 guard (audit F1): asserts the phoneDebug APK packages every " +
        "resource-carrying shared module's compose-resources .cvr assets for every locale."
    dependsOn("assemblePhoneDebug")
    apkDir.set(layout.buildDirectory.dir("outputs/apk/phone/debug"))
    repoRoot.set(rootProject.layout.projectDirectory)
    outputs.upToDateWhen { false }
}

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
    
    implementation(project(":shared:core:model"))
    implementation(project(":shared:core:designsystem"))
    implementation(project(":shared:core:network"))
    implementation(project(":shared:core:database"))
    implementation(project(":shared:core:datastore"))
    implementation(project(":core:data"))
    implementation(project(":core:ui"))
    implementation(project(":core:notification"))
    implementation(project(":shared:feature:auth"))
    implementation(project(":shared:feature:home"))
    implementation(project(":shared:feature:library"))
    implementation(project(":shared:feature:search"))
    implementation(project(":shared:feature:details"))
    implementation(project(":shared:feature:player-audio"))
    implementation(project(":shared:feature:player-live"))
    implementation(project(":shared:feature:player-video"))
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

    // Koin composition root (Phase C4 / wave 8B): startKoin in
    // JellyPlayApplication owns every definition — shared modules, the core
    // graphs, and the app-side androidAppModule set.
    implementation(libs.koin.core)
    // koinViewModel()/viewModel DSL for the app-shell ViewModels (same
    // artifact the shared feature modules use).
    implementation(libs.koin.compose.viewmodel)

    implementation(libs.play.services.cast.framework)

    // Required by the media3-ffmpeg-decoder native extension (pulled in via
    // :shared:feature:player-video). Must also be enabled at the app dex
    // entry point.
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
