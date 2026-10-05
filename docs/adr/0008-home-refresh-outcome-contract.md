# 0008 — Home refresh outcome stays `Result<HomeSection?>`; the executable spec is a real-stack fixture

- **Status:** accepted and implemented (2026-10-05;
  implementation in the same change set: `HomeSectionsFetcher`/
  `HomeSectionSources` now public, `HomeFeedRealStackContractTest` green)
- **Date:** 2026-10-05
- **Scope:** `shared/core/data` (`HomeFeed` seam), `shared/core/network`
  (`HomeSectionsFetcher`, `HomeSectionsCachePort`), `shared/feature/home`
  (`HomeRefresher`)

## Context

Two frictions around the
single-row refresh verb that landed with per-row edge-pull-to-refresh
(245c28963) and the write-generation token (d20e35189) were flagged on
2026-10-05:

1. The outcome contract — `success(section)` swap-in-place /
   `success(null)` drop-the-row / `failure` keep-the-stale-row — is
   restated in four KDoc homes (`MediaRepository.kt`,
   `HomeSectionsCachePort.kt`, `HomeSectionsFetcher.kt`,
   `HomeRefresher.kt`), each "deferring" to the fetcher while restating
   it. The first proposal was a sealed `RefreshOutcome` type at the seam.
2. No suite executes repo + fetcher together: `MediaRepositoryImpl`'s
   constructor is `internal` to core:data and `HomeSectionsFetcher` is
   `internal` to core:network, so no module's test lane sees both.
   `DiscoverRollContractTest` documents this "honest gap" and hand-builds
   a protocol-faithful double; every cross-layer invariant
   (bump-at-invalidate-AND-commit, stall-guard, generation mirror) is
   pinned piecewise across three suites (~3,650 lines).

## Decision

**Rejected: a sealed `RefreshOutcome` type.** All three suspend layers
already transport the contract as `Result<HomeSection?>` — the three
outcomes map exactly onto `Result`'s success-with-value / success-null /
failure vocabulary, and every caller already discriminates through that
interface. A parallel sealed type would be a second vocabulary for the
same interface at four call sites plus three test suites of churn; by the
deletion test it is a pass-through (delete it and `Result` carries the
identical meaning). Precedent in-repo is the opposite direction:
`InstantMixOutcome` exists because that seam has no such natural carrier.

**Accepted: one real-stack contract suite, unlocked by a visibility
widening.** `HomeSectionsFetcher` moves `internal` → `public`, and with
it `HomeSectionSources` (its constructor parameter type — Kotlin does
not allow a public class to expose an `internal` one) and the
`CacheIdentity` type if it is likewise exposed. Construction scope
stays documented as `LibraryApiClientImpl` plus the contract suite's
fakes. A new suite in core:data's `jvmTest` — the one module whose lane
can already see the repo's internal constructor —
builds `MediaRepositoryImpl` over the real `HomeSectionsFetcher` on a
fake `HomeSectionSources`, with a ten-line `HomeSectionsCachePort`
adapter delegating to the fetcher (exactly the four forwards
`LibraryApiClientImpl` performs internally). That suite becomes the one
executable spec for the token/mirror/roll invariants; the four KDoc
homes shrink to the fetcher's as canonical plus pointers. This uses the
ADR-0006 test-lane rules as written: file IO and JVM seams stay in
`jvmTest`, and no `:shared:core:test-fixtures` dependency is added
(the fixture is a test-local adapter, not a shared double).

## Consequences

- The outcome contract keeps one carrier (`Result<HomeSection?>`) and
  gains one executable pin instead of a new type.
- core:network's public interface widens by two types (fetcher +
  sources interface); their KDocs scope construction to
  `LibraryApiClientImpl` and the contract suite.
- `DiscoverRollContractTest`'s hand-built repo double remains for the
  feature-layer roll protocol (it doubles `HomeFeed`, not the fetcher)
  and cites the real-stack suite for the layers it faked.
- Future proposals: do not re-propose a sealed outcome type
  for this seam unless `Result<HomeSection?>` stops expressing a real
  outcome (e.g. a fourth outcome or per-outcome payload appears).
