import java.util.Properties
import java.util.zip.ZipFile

import com.android.build.api.variant.HasUnitTestBuilder
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.provider.MapProperty

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.androidx.baselineprofile)
    alias(libs.plugins.aboutLibraries)
}

// ---------------------------------------------------------------------------
// Version resolution (F-Droid preparation). The canonical source is
// version.properties at the repo root:
//   jellyplay.versionName=0.11.9
//   jellyplay.versionCode=11090
// The versionCode scheme is major*100_000 + minor*1_000 + patch*10 —
// 0.11.9 → 11090 — leaving room for a per-ABI suffix: F-Droid expects one
// APK per ABI whose versionCode is the base plus a small abiIndex (map
// below), which is exactly what `-PabiFilter=<abi>` produces. CI's
// `-PversionCode=${{ github.run_number }}` (low hundreds) still wins over
// the file, so the CI code space stays unchanged; the file only backs local
// and F-Droid-style builds. The plain file read at configuration time is
// configuration-cache-safe: Gradle tracks files the build logic reads
// during configuration as build configuration inputs.
// ---------------------------------------------------------------------------
val versionProperties = Properties().apply {
    val f = rootProject.file("version.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

// Per-ABI versionCode suffix, added on top of the version.properties base
// when `-PabiFilter` narrows a build to one ABI. armeabi-v7a keeps 0
// (reserved — no lane builds it today), arm64-v8a is the default split (1),
// x86_64 the emulator/second device ABI (2).
val abiVersionIndexes = mapOf(
    "armeabi-v7a" to 0,
    "arm64-v8a" to 1,
    "x86_64" to 2,
)

// `-PabiFilter=<abi>`: F-Droid per-ABI build mode. Restricts the ABI split
// to exactly one ABI (splits block below — which also constrains what the
// packaged APK carries), disables the universal APK, and — when no explicit
// -PversionCode is also passed — bumps the versionCode by the abiIndex so
// each per-ABI APK carries a distinct, F-Droid-aligned code. Validated
// eagerly: an unknown ABI fails configuration with the supported list
// instead of dying mid-packaging.
val abiFilter = providers.gradleProperty("abiFilter").orNull?.trim().takeUnless { it.isNullOrEmpty() }
if (abiFilter != null && abiFilter !in abiVersionIndexes) {
    throw GradleException(
        "Unsupported -PabiFilter='$abiFilter'. Supported ABIs: ${abiVersionIndexes.keys.joinToString()}"
    )
}

// versionName: -PversionName → version.properties → "0.0.1".
val resolvedVersionName = (project.findProperty("versionName") as? String)?.takeIf { it.isNotBlank() }
    ?: versionProperties.getProperty("jellyplay.versionName")
    ?: "0.0.1"

// versionCode: -PversionCode → version.properties (+ abiIndex under
// -PabiFilter, only when no explicit code is passed) → 1.
val resolvedVersionCode = (project.findProperty("versionCode") as? String)?.toIntOrNull()
    ?: versionProperties.getProperty("jellyplay.versionCode")?.toIntOrNull()?.let { base ->
        base + (abiFilter?.let { abiVersionIndexes.getValue(it) } ?: 0)
    }
    ?: 1

android {
    namespace = "com.raulshma.jellyplay"
    compileSdk = 37

    // Reproducible-build parity pin. F-Droid's buildserver ships NO NDK —
    // AGP auto-installs whatever version the build requests into its
    // writable /opt/android-sdk/ndk (buildserver/provision-android-ndk
    // exists exactly to make that dir installable). Unpinned, that request
    // is AGP's compile-time default (28.2.13676358 for AGP 9.5), which
    // silently moves on every AGP upgrade — and with it every llvm-strip
    // pass over the bundled AAR .so files (release ndk.debugSymbolLevel
    // below makes the NDK required for release packaging). Strip output
    // bytes depend on the NDK version, so a moved default breaks the
    // Binaries byte-comparison against our CI-built reference APKs
    // (.github/workflows/fdroid-release.yml installs this same version).
    // Keep the pin and the workflow env var in lockstep.
    ndkVersion = "28.2.13676358"

    defaultConfig {
        applicationId = "com.raulshma.jellyplay"
        minSdk = 28
        targetSdk = 37
        versionCode = resolvedVersionCode
        versionName = resolvedVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        manifestPlaceholders["versionName"] = resolvedVersionName

        // NOTE: -PabiFilter intentionally does NOT mirror into ndk.abiFilters:
        // AGP rejects a build where an ndk abiFilter names an ABI that is
        // also in splits.abi.include ("Conflicting configuration"), and the
        // splits restriction in the abi block below already makes each
        // packaged APK carry exactly one ABI's native libs.
    }

    // AGP 9 replacement for the legacy defaultConfig.resourceConfigurations.
    androidResources {
        localeFilters += listOf("en", "de", "es", "fr", "it", "pt", "ja", "ko", "zh")
    }

    // Two orthogonal flavor dimensions:
    //   platform:     phone | tv       (device family)
    //   distribution: store | fdroid   (release channel)
    // Variant names concatenate in dimension order: phoneStoreRelease,
    // phoneFdroidDebug, tvStoreRelease, ... The fdroid channel targets the
    // phone app only (F-Droid's submission is the phone build), so the
    // tv × fdroid combination is disabled in androidComponents below.
    flavorDimensions += listOf("platform", "distribution")
    productFlavors {
        create("phone") {
            dimension = "platform"
        }
        create("tv") {
            dimension = "platform"
            applicationIdSuffix = ".tv"
        }
        create("store") {
            dimension = "distribution"
        }
        create("fdroid") {
            dimension = "distribution"
        }
    }

    // Disable tv × fdroid at the variant level: with this filter the tasks
    // (assembleTvFdroidRelease etc.) simply do not exist, which IS the guard
    // — requesting one fails with AGP's "task not found" instead of doubling
    // the CI build matrix for a combination nobody ships.
    androidComponents {
        beforeVariants { builder ->
            val flavors = builder.productFlavors
            if (("platform" to "tv") in flavors && ("distribution" to "fdroid") in flavors) {
                builder.enable = false
            }
            // androidx.baselineprofile 1.5.0 disables unit tests on release
            // variants AND on its own profiling-only extended build types
            // (nonMinifiedRelease/benchmark — a config-time optimization; no
            // lane runs those unit tests). AGP 9.5's
            // generate<Variant>ComposePreviewRunfiles task REQUIRES unit
            // tests and throws at task creation — which fails any
            // :app:tasks listing (the only lane that realizes those tasks).
            // Re-enable everywhere the plugin disabled them: the tasks merely
            // exist again, nothing runs them. Registered after the plugin's
            // own callback, so this wins. Drop when the plugin stops
            // disabling or AGP relaxes the preview requirement.
            (builder as? HasUnitTestBuilder)
                ?.takeIf { it.enableUnitTest == false }
                ?.let { it.enableUnitTest = true }
        }
    }

    // ABI splits are property-gated, not task-name-
    // sniffed: gradle.startParameter.taskNames is configuration-cache-hostile
    // and matches any invocation containing "debug" (testPhoneStoreDebugUnitTest
    // never packages an APK but would still pay the extra-ABI configuration).
    splits {
        abi {
            isEnable = true
            reset()
            if (abiFilter != null) {
                // -PabiFilter=<abi>: the F-Droid per-ABI lane builds exactly
                // one split APK — no universal, no other ABIs — with
                // versionCode = version.properties base + abiIndex, matching
                // F-Droid's VercodeOperation expectations.
                include(abiFilter)
            } else {
                include("arm64-v8a")
                // Builds default to arm64-only — the device fleet is
                // physical phones, and extra ABIs cost three dex+package passes.
                // The emulator ABIs (x86_64 for
                // modern images; x86 for the 32-bit-only Android TV system images,
                // API 30 and older) are opt-in via a comma-separated property.
                // Named extraAbis because splits.abi is variant-agnostic: it
                // widens every variant, debug and release alike (no CI lane
                // passes it — codeql's assembleDebug is compile-analysis only,
                // kmp-build's androidTest/verify lanes never install on an
                // emulator, and the device e2e pass installs the arm64 split):
                //   ./gradlew assemblePhoneStoreDebug -PextraAbis=x86_64,x86
                // Empty/unset/blank entries parse to "arm64 only".
                providers.gradleProperty("extraAbis")
                    .map { raw ->
                        raw.split(',').map(String::trim).filter { it.isNotBlank() }.distinct()
                    }
                    .orElse(emptyList())
                    .get()
                    .forEach { include(it) }
            }
            // The universal APK (all 4 ABIs of every native player
            // stack, ~3-4x the arm64 payload) is a sideload-only convenience
            // and does not build by default. The release lane opts back in
            // (-PbuildUniversal=true in release.yml on BOTH the baseline-
            // profile generate step — which assembles and installs release
            // APKs on the x86_64 ciPixel8 GMD — and the assemble step), so
            // release artifacts keep it; every other build skips it.
            // -PabiFilter always forces it off: F-Droid wants exactly one
            // APK per invocation.
            isUniversalApk = abiFilter == null &&
                providers.gradleProperty("buildUniversal")
                    .map(String::toBoolean)
                    .getOrElse(false)
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
            // StartupBenchmark measures the release build with
            // CompilationMode.None; on API 29+ macrobenchmark/systrace
            // visibility of a non-debuggable app requires profileability,
            // otherwise release timings silently degrade. Harmless on debug
            // builds (debuggable implies profileability). Set via the DSL,
            // NOT a manifest element: this AGP's aapt2 link rejects a
            // root-level <profileable> (verified with the bundled binary),
            // and inside <application> the element compiles but the runtime
            // package parser ignores it — neither works.
            isProfileable = true
            // Ship native symbol tables (function names + frame
            // addresses, no full DWARF) so libmpv/libvlc/libass/ffmpeg-decoder
            // crash stacks can be symbolicated. Config-only at this level —
            // extraction runs during release packaging only when an NDK is
            // present (CI runners ship the NDK sidecar with the SDK; local
            // builds without one log a warning and skip it, never fail).
            ndk {
                debugSymbolLevel = "SYMBOL_TABLE"
            }
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

    // Robolectric (unit) tests: expose the merged debug manifest + resources so
    // the Compose test rule can resolve the ui-test-manifest ComponentActivity
    // (OverflowMenuItemsAnimationRobolectricTest).
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
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
// phoneStoreRelease is the canonical list; the TV flavor adds no new dependencies.
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
// WIRING SCOPE — STORE MERGES ONLY (RB, ADR 0014): the export DOWNLOADS license
// texts it can't resolve from its bundled metadata (the GPL-3.0-only body is a
// gnu.org fetch), so a generated aboutlibraries.json is a network coin flip —
// the CI determinism watchdog caught two clean checkouts of one commit
// producing 162KB vs 127KB exports (the single differing APK entry). The
// F-Droid lane therefore ships a COMMITTED, CI-drift-checked asset instead:
// app/src/fdroid/assets/aboutlibraries.json (see the fdroid drift step in
// kmp-build.yml; regenerate+commit it whenever the dependency graph changes).
// Store variants keep live generation — they are not byte-compared anywhere.
// Debug builds keep the on-demand behavior.
tasks.matching { it.name.matches(Regex("merge.*Store.*ReleaseAssets")) }.configureEach {
    dependsOn("exportLibraryDefinitions" + name.removePrefix("merge").removeSuffix("Assets"))
}

// The export lands in src/main/assets, which every OTHER variant's merge —
// debug ones included, e.g. mergeTvStoreDebugAssets under
// :app:testTvStoreDebugUnitTest — also reads as a source dir. Co-schedule any
// merge with any export in one invocation and Gradle 9 fails the build as an
// implicit-dependency violation, so every merge declares ordering after every
// export (release merges additionally depend on their own, above).
tasks.matching { it.name.matches(Regex("merge.*Assets")) }.configureEach {
    mustRunAfter(tasks.matching { it.name.startsWith("exportLibraryDefinitions") })
}

// ---------------------------------------------------------------------------
// Compose-resources APK guard. The launch-blocking crash it prevents: the
// AGP-9 KMP library plugin leaves android resources OFF by default, so a shared module can ship
// generated `Res` accessor classes in the APK while its backing `.cvr` string
// assets are silently missing unless the module opts in via
// `androidResources { enable = true }`. No compile gate can
// see it — the accessors compile fine, the MissingResourceException only
// fires on the first string read at runtime — so this task opens the BUILT
// phoneStoreDebug APK as a zip instead and asserts, for every resource-carrying
// shared module, that its assets/composeResources/<packageOfResClass>/
// directory exists with at least one .cvr entry per locale:
//   * the module list is derived from the source tree at EXECUTION time
//     (every shared/*/* module with a commonMain/composeResources directory)
//     — deliberately not hardcoded, so a NEW resource-carrying module without
//     the enable flag fails here, and a module that stops carrying resources
//     drops out of scope by itself;
//   * the per-module asset namespace is the module's CONFIGURED compose
//     `packageOfResClass` — captured from the evaluated Gradle model (see the
//     capture block below the task), which is exactly the directory name
//     compose-resources creates under assets/composeResources/ in the APK
//     (verified against the real phoneStoreDebug artifact: e.g. shared/feature/home
//     → com.raulshma.jellyplay.feature.home.generated.resources/values-de/
//     strings.commonMain.cvr — note the legacy import paths, NOT the
//     kotlin.android namespace). The convention plugin derives
//     `packageOfResClass` from the module path, so the guard reads that one
//     source of truth instead of text-parsing build.gradle.kts literals that
//     can drift from it;
//   * the locale set derives from the module's values/values-xx directory
//     names (today 9 per module: values, -de, -es, -fr, -it, -ja, -ko, -pt,
//     -zh) and the derivation yields
//     216 .cvr entries = 24 modules × 9 locales.
// Always re-runs (the zip scan is cheap): a cached "PASS" would defeat the
// point of a guard. TV flavor ships the same merged assets graph; the phoneStore
// artifact is the guard's assertion surface.
// ---------------------------------------------------------------------------
abstract class VerifyPhoneStoreDebugComposeResourcesTask : DefaultTask() {
    /** Directory :app:assemblePhoneStoreDebug writes the per-ABI split APKs into. */
    @get:Internal
    abstract val apkDir: DirectoryProperty

    /** Repo root — the shared/ source tree is scanned at execution time. */
    @get:Internal
    abstract val repoRoot: DirectoryProperty

    /**
     * Module path ("shared/core/ui") → configured compose
     * `packageOfResClass` for that module, captured from the evaluated
     * Gradle model at configuration time (see the capture block below).
     */
    @get:Internal
    abstract val resPackages: MapProperty<String, String>

    @TaskAction
    fun verify() {
        val root = repoRoot.get().asFile
        val sharedRoot = root.resolve("shared")
        if (!sharedRoot.isDirectory) {
            throw GradleException("verifyPhoneStoreDebugComposeResources: shared/ tree not found under $root")
        }

        // 1. Expected modules + locale sets, derived NOW from the source tree.
        //    (module path, asset namespace, locale dir names)
        val expected = mutableListOf<Triple<String, String, List<String>>>()
        for (group in sharedRoot.listFiles { f -> f.isDirectory }.orEmpty()) {
            for (module in group.listFiles { f -> f.isDirectory }.orEmpty()) {
                val resRoot = module.resolve("src/commonMain/composeResources")
                if (!resRoot.isDirectory) continue
                val modulePath = "shared/${group.name}/${module.name}"
                val namespace = resPackages.get()[modulePath]
                    ?: throw GradleException(
                        "verifyPhoneStoreDebugComposeResources: $modulePath carries " +
                            "composeResources but no configured compose.resources.packageOfResClass " +
                            "was captured for it — the module must apply jellyplay.kmp.library.compose " +
                            "(or set packageOfResClass) so the guard can know its " +
                            "assets/composeResources/ directory"
                    )
                val localeDirs = resRoot
                    .listFiles { f -> f.isDirectory && (f.name == "values" || f.name.startsWith("values-")) }
                    .orEmpty().map { it.name }.sorted()
                expected.add(Triple(modulePath, namespace, localeDirs))
            }
        }
        expected.sortBy { it.first }
        if (expected.isEmpty()) {
            throw GradleException(
                "verifyPhoneStoreDebugComposeResources: no shared/*/* module carries " +
                    "commonMain/composeResources — guard input vanished?"
            )
        }

        // 2. The built APKs (per-ABI splits carry identical assets — check all).
        val apkDirFile = apkDir.get().asFile
        val apks = apkDirFile.listFiles { f -> f.isFile && f.name.endsWith(".apk") }.orEmpty()
        if (apks.isEmpty()) {
            throw GradleException(
                "verifyPhoneStoreDebugComposeResources: no APK found in $apkDirFile — " +
                    "run :app:assemblePhoneStoreDebug first"
            )
        }

        val localePairs = expected.sumOf { it.third.size }
        logger.lifecycle(
            "verifyPhoneStoreDebugComposeResources: ${expected.size} resource-carrying shared modules, " +
                "$localePairs expected module/locale pairs (baseline: 24 modules x 9 locales = 216)"
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
                "verifyPhoneStoreDebugComposeResources FAILED — first missing module/locale:\n  ${failures.first()}\n" +
                    "  all ${failures.size} gap(s):\n  " + failures.joinToString("\n  ")
            )
        }
        logger.lifecycle(
            "verifyPhoneStoreDebugComposeResources: OK — every expected .cvr asset is packaged " +
                "in ${apks.size} phoneStoreDebug APK(s)"
        )
    }
}

val verifyPhoneStoreDebugComposeResources = tasks.register<VerifyPhoneStoreDebugComposeResourcesTask>("verifyPhoneStoreDebugComposeResources") {
    group = "verification"
    description = "Asserts the phoneStoreDebug APK packages every " +
        "resource-carrying shared module's compose-resources .cvr assets for every locale."
    dependsOn("assemblePhoneStoreDebug")
    apkDir.set(layout.buildDirectory.dir("outputs/apk/phoneStore/debug"))
    repoRoot.set(rootProject.layout.projectDirectory)
    outputs.upToDateWhen { false }
}

// Capture each module's CONFIGURED compose.resources.packageOfResClass after
// every project — including the convention plugin's afterEvaluate
// path-derived default and any per-module `jellyplay { resPackage }`
// override — had its say (projectsEvaluated). The guard then asserts against
// what the build actually packages. Reflection keeps :app from needing the
// JetBrains Compose plugin types on its buildscript classpath (:app applies
// the androidx compose compiler plugin only; `compose` below is the JB
// plugin's extension and is absent here).
gradle.projectsEvaluated {
    val captured = mutableMapOf<String, String>()
    for (project in rootProject.subprojects) {
        val compose = project.extensions.findByName("compose") as? ExtensionAware ?: continue
        val resources = compose.extensions.findByName("resources") ?: continue
        val resPackage = runCatching {
            resources.javaClass.getMethod("getPackageOfResClass").invoke(resources) as? String
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: continue
        captured[project.path.removePrefix(":").replace(':', '/')] = resPackage
    }
    verifyPhoneStoreDebugComposeResources.configure { resPackages.putAll(captured) }
}

// Baseline Profile consumers. The androidx.baselineprofile 1.5.x plugin
// resolves producers per variant: dependencies are the merge of the `main`,
// flavor, build type and variant entries below. The global `baselineProfile`
// configuration cannot hold both producers — it extends into every variant
// and would run each generator against both flavors — so the phoneStore variant
// names its own producer module instead. TV has none: AGP cannot run TV
// images on Gradle Managed Devices, so the tv flavor ships without
// generated profiles.
// - phoneStore → :baselineprofile (phone applicationId, ciPixel8 GMD)
baselineProfile {
    // Generated profiles are written into the variant source sets
    // (src/<variant>Release/generated/baselineProfiles) so they can be
    // committed and regenerate on demand, like aboutlibraries.json. Builds
    // not preceded by a generate step pick up the committed files instead of
    // producing profile-less APKs.
    saveInSrc = true
    variants {
        create("phoneStore") {
            from(project(":baselineprofile"))
        }
    }
}

dependencies {
    // Explicitly add libmpv first so pickFirsts grabs its newer libc++_shared.so
    implementation(libs.libmpv)
    
    implementation(project(":shared:core:model"))
    implementation(project(":shared:core:concurrency"))
    implementation(project(":shared:core:designsystem"))
    implementation(project(":shared:core:network"))
    implementation(project(":shared:core:database"))
    implementation(project(":shared:core:datastore"))
    implementation(project(":shared:core:data"))
    // Shared core:ui (core.ui.message bus seam + Android platform
    // halves of shared UI) + compose-resources runtime for the shared
    // UserMessageBus collector (UiText resolution on Android).
    implementation(project(":shared:core:ui"))
    implementation(project(":shared:feature:auth"))
    implementation(project(":shared:feature:home"))
    implementation(project(":shared:feature:library"))
    implementation(project(":shared:feature:search"))
    implementation(project(":shared:feature:details"))
    implementation(project(":shared:feature:player-audio"))
    implementation(project(":shared:feature:player-book"))
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
    // Shared shell graph: appSections registers this module's own *Section
    // builders once for both shells (JellyPlayApp passes the Android
    // ShellHostHooks; the androidMain-only sections stay inline below it).
    implementation(project(":shared:feature:shell"))

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

    // Koin composition root: startKoin in
    // JellyPlayApplication owns every definition — shared modules, the core
    // graphs, and the app-side androidAppModule set.
    implementation(libs.koin.core)
    // koinViewModel()/viewModel DSL for the app-shell ViewModels (same
    // artifact the shared feature modules use).
    implementation(libs.koin.compose.viewmodel)

    // GMS cast is store-only: fdroid builds must not see play-services (or
    // the junrar UnRAR license) on their classpath, so these ride the store
    // FLAVOR configuration — the store source set binds GoogleCastStrategy /
    // GoogleCastIntegrationImpl (core:data's cast seam) and the junrar RAR
    // adapter, while fdroid binds the no-op fallbacks. The single-flavor
    // `store` configuration covers every store variant (phone AND tv — tv
    // has no fdroid variant at all, the tv × fdroid combination is disabled
    // in androidComponents). String-based add(): the Kotlin DSL generates no
    // accessor for flavor configurations under two flavor dimensions.
    // mediarouter is explicit because the store cast code
    // (DistributionPlatform's hidden route button, GoogleCastStrategy)
    // references androidx.mediarouter directly — it used to arrive only
    // transitively via cast-framework.
    add("storeImplementation", libs.play.services.cast.framework)
    // media3-cast rides the store configuration too: GoogleCastIntegrationImpl
    // constructs the media3 CastPlayer directly (core:data dropped the dep
    // with the cast seam — nothing in its sources references it anymore).
    add("storeImplementation", libs.media3.cast)
    add("storeImplementation", libs.mediarouter)
    add("storeImplementation", libs.junrar)

    // Required by the media3-ffmpeg-decoder native extension (pulled in via
    // shared:feature:player-video). Must also be enabled at the app dex
    // entry point.
    coreLibraryDesugaring(libs.android.desugar.jdk)

    implementation(libs.media3.session)

    // Android keeps the Coil 3.5.0 BOM so artwork→Palette
    // swatches stay byte-identical to the shipped build; the shared
    // modules' 3.4.0 pin does not apply to Android class files.
    implementation(platform(libs.coil.bom.android))
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
    // PlayerActivityArgs round-trip test exercises android.content.Intent
    // extras on the JVM (same setup as the nine modules already using it).
    testImplementation(libs.robolectric)
    // OverflowMenuItemsAnimationRobolectricTest drives the nav overflow menu's
    // dock transitions on the JVM (Robolectric + Compose UI test clock). The
    // fake ResolvedShortcut references org.jetbrains.compose.resources types.
    testImplementation(libs.compose.ui.test)
    testImplementation(libs.jb.compose.components.resources)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test)
    // NavigationRouteTest round-trips every Route through nav3's NavKeySerializer,
    // which encodes/decodes via kotlinx.serialization Json.
    androidTestImplementation(libs.kotlinx.serialization.json)
    debugImplementation(libs.compose.ui.test.manifest)

    // LeakCanary self-installs its watcher via a startup ContentProvider and
    // auto-reports retained Activities/Fragments/ViewModels/Services. Debug
    // builds only (phoneStoreDebug/phoneFdroidDebug + tvStoreDebug); runtime
    // tweaks live in
    // app/src/debug .../DebugLeakCanary.kt next to installDebugStrictMode().
    debugImplementation(libs.leakcanary.android)

    // Baseline profile producers are wired per variant in the `baselineProfile`
    // extension block above (:baselineprofile for phoneStore only — tv ships
    // without generated profiles) instead of a global `baselineProfile`
    // configuration entry, which cannot disambiguate the flavors.
}
