plugins {
    id("jellyplay.kmp.library.base")
}

kotlin {
    android {
        namespace = "com.raulshma.jellyplay.shared.core.i18ntools"
    }

    sourceSets {
        // ── what this module is ──────────────────────────────────────────
        // The one home for the repo's i18n string-table tooling: the
        // counted-key grammar (CountedKeyGrammar — the shared rules both
        // sides of the parity story read), the write-side StringsPropagator
        // and the I18nResourceDiscovery walk that the parity ratchet (this
        // module's jvmTest) and the root build's repo-wide
        // propagateStrings/checkStrings tasks consume. Nothing here ships in
        // the app; the module must stay a leaf — no dependencies at all,
        // kotlin-stdlib only.
        //
        // The sources live in jvmMain, NOT jvmShared: the tools walk the
        // repository filesystem (java.io.File, run from jvmTest suites and
        // Gradle JavaExec) and are meaningless on Android — the same shape
        // as test-fixtures' jvmMain-only desktop fakes. The Android target
        // exists only because every shared include must carry jvmJar +
        // compileAndroidMain + jvmTest for the CI lane derivation
        // (SharedModuleCiLaneGuardTest); its compilations are empty here.
    }
}
