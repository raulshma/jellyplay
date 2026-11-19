# JellyPlay — agent pointers

This repo's steering docs are dense and deliberately opinionated; the pointers
below say when to reach for each. The docs are the source of truth — never
inline their content here.

- **Defect rules & vocabulary** → `GLOSSARY.md`. Read before adding a
  setting, a sync key, a cache, or a seam: most "obvious" refactors here are
  deliberately closed off by a pinned rule ("a second hand-fold is a
  defect").
- **Architecture orientation & module map** → `CONTEXT.md`. KMP tree layout,
  engine/session layering, and the player package↔module anomaly (grepping
  by package cannot tell the module — the edge is in player-contract's
  build.gradle.kts).
- **Recorded decisions** (what was tried and rejected, so it is not
  re-litigated) → `docs/adr/`. `0006` governs where a test goes
  (commonTest / jvmTest / androidHostTest / test-fixtures); check it before
  placing a new test.
- **Companion plugin vocabulary** → `jellyfin-plugin-jellyplay/GLOSSARY.md`
  (separate file, not duplicated into the client glossary).

## Verify locally before claiming done

CI runs on PRs and `main`/`kmp-alpha` pushes only — **pushes to `dev/*`
branches are unverified**, so run the touched modules' lanes yourself:

- Shared module logic: `./gradlew <module>:jvmTest` (commonTest included).
- Code under `androidMain`: `./gradlew <module>:testAndroidHostTest` and
  `<module>:compileAndroidMain` for the compile check.
- App unit suites: `:app:testPhoneStoreDebugUnitTest :app:testTvStoreDebugUnitTest`
  plus the `testAndroidHostTest` lanes CI names.
- Desktop: `./gradlew :apps:desktop:test`.

The full CI lane list is derived from `settings.gradle.kts` in
`.github/workflows/kmp-build.yml` ("Derive shared module lanes") and
ratcheted by `SharedModuleCiLaneGuardTest` — a new shared module joins the
lane by construction; do not hand-maintain task lists.
