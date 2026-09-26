plugins {
    id("jellyplay.kmp.library.base")
}

kotlin {
    android {
        namespace = "com.raulshma.jellyplay.shared.core.player.contract"
    }

    sourceSets {
        // PlayerLifecycleCallbacks and RemotePlayableEngine previously lived in
        // shared:core:data; they now live here verbatim (SAME packages, zero
        // consumer import churn). Dependency edge flipped: core:data now depends
        // on this module instead of the reverse.

        getByName("commonMain").dependencies {
            api(project(":shared:core:model"))
            // Flow/StateFlow surface of the engine contract.
            implementation(libs.kotlinx.coroutines.core)
            // TaskBundle — the EngineEventCoordinator's per-engine policy slot
            // choreography (the same bundle the core:data session collaborators
            // use). core:concurrency is a dependency leaf, so no cycle.
            implementation(project(":shared:core:concurrency"))
            // core:model's @Serializable enums surface their generated
            // serializer companions in this module's when-expressions
            // (PlayerType, DecoderMode, MediaSegmentType, …); compiling against
            // those klibs needs serialization-core on the classpath. Previously
            // leaked transitively through the (now-removed) core:data api edge;
            // json pulls core, same declaration core:model itself uses.
            implementation(libs.kotlinx.serialization.json)
            // Annotation-only Compose usage (@Immutable/@Stable on the contract
            // data classes), same pattern as :shared:core:data and :model.
            implementation(project.dependencies.platform(libs.compose.bom))
            implementation(libs.compose.runtime)
        }
        getByName("commonTest").dependencies {
            implementation(libs.coroutines.test)
        }
        // EngineCapabilityMatrixTest moved here from :feature:player:video's
        // unit-test source set with the subtitle-tester conveyor (feature
        // seventeen): the matrix itself moved to this module's commonMain
        // (same package) so shared feature modules can consume it; the test
        // follows its subject. kotlin.test replaces the org.junit imports.
    }
}
