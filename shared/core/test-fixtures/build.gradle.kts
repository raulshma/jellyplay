plugins {
    id("jellyplay.kmp.library.base")
}

kotlin {
    android {
        namespace = "com.raulshma.jellyplay.shared.core.testfixtures"
    }

    sourceSets {
        // ── what this module is ──────────────────────────────────────────
        // Test doubles needed by MORE THAN ONE module's test lane — today
        // FakeTimeSource (livetv + core:data — the per-module copies were
        // single-homed here),
        // FakeUserDataMutator (details + home) and FakeMediaEngine (the one
        // MediaEngine double for the session + audio-queue jvmTest suites;
        // apps/desktop's app-side copy was adopted in the fake-twin merge —
        // its suites construct AUTO_PLAY instances of this class. The fake's
        // wall-clock pollUntil helper (and FakePositionStore, the session
        // recording double) have since moved back OUT — ADR-0006 rule 4:
        // each's only consumers were a single module's test lane, so each
        // lives in that module's test sources again (apps/desktop tests and
        // player-video jvmTest, respectively).
        // The desktop audio-queue fakes joined under ADR-0006 rule 4 proper:
        // the file existed VERBATIM in core:data jvmTest and apps/desktop's
        // test set (the app-side KDoc itself said "kept in sync by review"),
        // so both lanes now consume the single canonical copy here.
        // AGP 9 has no KMP testFixtures support, so this is a
        // plain library module declared inside consumers' test source-set
        // blocks ONLY.
        //
        // The dissolved :core:testing lesson: a central test module
        // accretes every helper anyone ever copied twice and becomes a
        // production-looking dependency nobody can audit. House rules:
        //  - per-touch adoption: a double moves here when a SECOND module
        //    needs it, never in bulk;
        //  - NEVER add this module to a main source set — the
        //    TestFixturesScopeGuardTest jvmTest below fails the build if a
        //    non-test-scoped reference creeps into any build script;
        //  - a double keeps the shape of the richest existing copy — no
        //    redesign at the move.
        //
        // The doubles live in jvmShared, not commonMain: FakeTimeSource
        // implements core:model's TimeSource (D3 moved the seam there from
        // core:data), which lives in THAT module's jvmShared
        // (java.time-flavored) and is therefore equally invisible to
        // commonMain here. One home for all doubles keeps the story simple;
        // a future common-safe double would add a commonMain file when a
        // commonTest consumer actually exists.

        getByName("jvmShared").dependencies {
            // TimeSource (core:model jvmShared, the D3 move) FakeTimeSource
            // implements. implementation, not api: every consumer already
            // carries its own core:model edge — this module must never
            // become a reason to widen one.
            implementation(project(":shared:core:model"))
            // The repository contract
            // types (UserDataMutator/MediaDetailProvider/AppliedMutation/
            // UserDataContainer, core:data commonMain) the doubles
            // implement. implementation, not api: every consumer already
            // carries its own core:data edge — this module must never
            // become a reason to widen one.
            implementation(project(":shared:core:data"))
            // The MediaEngine contract (+ PlayerLifecycleCallbacks/
            // RemotePlayableEngine supertypes, same packages) FakeMediaEngine
            // implements. implementation, not api: every consumer already
            // carries its own player-contract edge, same rule as core:data
            // above — this module must never become a reason to widen one.
            implementation(project(":shared:core:player-contract"))
        }
        getByName("jvmMain").dependencies {
            // The desktop audio-queue fakes (DesktopAudioQueueManagerFixtures)
            // live in jvmMain, not the usual jvmShared: they reference
            // core:data's JVM-only AudioEffectsSession (that module's
            // jvmMain), invisible to this module's ANDROID compilation — and
            // both consumer lanes (core:data jvmTest, apps/desktop test) are
            // JVM anyway. jvmMain inherits the jvmShared edges above for
            // everything else the fakes touch (core:data, player-contract).
            // The AudioQueueDao + AudioQueueEntity/AudioQueueStateEntity Room
            // edge InMemoryQueueDao extends. implementation, not api: every
            // consumer already carries its own core:database edge — this
            // module must never become a reason to widen one.
            implementation(project(":shared:core:database"))
        }
    }
}

// TestFixturesScopeGuardTest scans every build.gradle.kts in the repo, so its
// real inputs sit far outside the test classpath — wire them in explicitly or
// the tripwire goes stale (up-to-date skip) exactly when someone adds the bad
// reference it exists to catch. The scripts are enumerated per root instead of
// one fileTree over the repo root: a tree rooted at the project directory
// overlaps the desktop fetchBundledLibmpv outputs under tools/mpv, and Gradle
// 9's task-graph validation fails any single invocation that runs both (e.g.
// `gradlew jvmTest :apps:desktop:test`). Same file set, no location overlap —
// tools/ carries no build scripts, and a newly added module enters
// `subprojects` (recomputing this list) in the same configuration pass its
// settings include triggers.
tasks.named<Test>("jvmTest") {
    inputs.files(
        rootProject.files("build.gradle.kts"),
        rootProject.subprojects.map { it.files("build.gradle.kts") },
        gradle.includedBuilds.map { it.projectDir.resolve("build.gradle.kts") },
    ).withPropertyName("guardScannedBuildScripts")
        .withPathSensitivity(PathSensitivity.NONE)
}
