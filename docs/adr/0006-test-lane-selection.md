# 0006 — Test-lane selection: commonTest / jvmTest / androidHostTest / test-fixtures

- **Status:** accepted
- **Date:** 2026-10-02
- **Scope:** all `shared/` KMP modules (test source-set placement and shared
  test doubles)

## Context

The KMP tree has four test lanes and the choice between them was
prose-only folklore — each module's build comments carry fragments of the
rule, but no single document states it, so modules drift:

- `commonTest` — runs on every target via the common hierarchy edge.
- `jvmTest` — JVM-desktop lane (also inherits commonTest sources).
- `androidHostTest` — Robolectric host lane (`withHostTest` convention),
  exercising `androidMain` code on the JVM without a device.
- `:shared:core:test-fixtures` — plain library of shared test doubles,
  consumed test-scoped only (AGP 9 has no KMP testFixtures support).

Drift is visible in the tree: `core:model` carries pure-logic tests in
`jvmTest` with no JVM dependency at all (`TtlCacheTest` — its injectable
clock removed the `SystemClock` dependency its KDoc still apologizes for;
`SubtitleLanguageCodesTest` — pure string normalization), so they skip the
common lane they qualify for, while `feature/home` is the only feature that
discovered `commonTest` on its own. A new test lands in whatever lane the
neighboring files use, and the lanes quietly diverge.

## Decision

One rule per question "where does this test go":

1. **Pure common logic → `commonTest`.** If the test compiles against
   `commonMain` APIs only (models, reducers, pure functions, mappers), it
   lives in `commonTest` and runs on every target.
2. **JVM-only seams → `jvmTest`.** Tests that need threading
   (`Dispatchers`/runTest semantics beyond the common-set, real executors),
   `java.time`/`java.util` behavior, or file IO go to `jvmTest`.
3. **Exercising `androidMain` actuals → `androidHostTest`.** The Robolectric
   host lane exists so Android-side code (engines, stores, framework glue)
   is testable without a device; pure-derivation tests extracted FROM
   `androidMain` code run there too — the lane runs Robolectric, but a test
   that needs none of it is still fine and preferred over weakening the
   code under test to move lanes.
4. **Shared doubles → `:shared:core:test-fixtures`, test-scoped only.** A
   fake needed by more than one module's test lane belongs there — never a
   main source-set dependency (see its build.gradle.kts).

Two clarifications: (a) a test that COULD be common but genuinely exercises
a JVM-only seam through its subject stays in `jvmTest` — the subject's lane
wins over the test's own purity; (b) when a `jvmTest` file is actually pure
common logic, moving it to `commonTest` is the fix, not codifying the
outlier.

## Consequences

- New tests have a document to cite; lane choice stops being neighbor-copy.
- The two `core:model` outliers are annotated in place with their actual
  reason (historical drift, no JVM dependency — commonTest candidates) so
  the next reader does not take them as precedent; they can move lanes in a
  later cleanup without a behavior question.
- `feature/home`'s `commonTest` stops being an anomaly — it is simply the
  rule applied.
- No CI change: the lanes already exist and run; the ADR governs placement,
  not execution.
