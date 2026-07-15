# Media Detail Screen — Performance & Resource Utilization Analysis

**Date:** 2026-07-15  
**Scope:** `feature/details/` module (media-detail screen tree) + shared dependencies (`core/data`, `core/model`, `core/ui`)  
**Branch:** `dev/v0.9.0`

---

## Executive Summary

The media detail screen is **architecturally mature** — it already employs `@Immutable` state/callback bundles for Compose skippability, single-atomic-reset state loading, parallel season fetching with generation-guarded Seerr staleness protection, memoized episode filtering, and right-sized Coil decode dimensions. The `DetailContentState`/`DetailContentCallbacks` decomposition (mirroring the home-screen pattern) is textbook Compose stability engineering.

That said, a deep review surfaces **21 actionable findings**. The highest-leverage issues cluster around three themes:

1. **Episode-map allocation amplification** — parallel season fetching multiplies map copies and smart-play re-computation.
2. **Entrance-animation coroutine sprawl** — ~33 independent `LaunchedEffect` + `animateFloatAsState` pairs fire on screen entry and never fully tear down.
3. **`uiState` combine-chain fan-out** — 9 upstream flows each create a full `DetailUiState.copy()`, triggering screen-wide recomposition for field-local changes.

**Estimated impact if all recommendations are applied:**
- ~40–60% reduction in allocation churn during series detail load (the worst-case path)
- Fewer recompositions per scroll frame on TV (unstable scroll-state holder)
- ~33 fewer coroutines + snapshot subscriptions during screen entry
- Elimination of redundant network calls (`resolveServers`, theme music) on unrelated state changes

---

## Existing Optimizations Already in Place (Acknowledged)

These demonstrate correct technique and should be preserved:

| # | Location | Technique |
|---|----------|-----------|
| O1 | `DetailContentState.kt` / `DetailContentCallbacks.kt` | `@Immutable` bundles reduce ~25 unstable params to 2 stable ones — composables are skippable |
| O2 | `DetailViewModel.kt:199–220` | Single atomic `_uiState.update` reset on `loadItem` — one recomposition instead of ~14 |
| O3 | `DetailViewModel.kt:286–296` | Parallel episode fetch collapses N serial round-trips into one concurrent batch |
| O4 | `DetailViewModel.kt:222–229, 786, 856–864` | Generation counter (`seerrDataGeneration`) + `currentItemId` guard reject stale Seerr/trailer loads from shared-VM navigations |
| O5 | `MediaDetailBody.kt:479–485` | `filteredEpisodes` memoized on `(episodes, skipSpecials)` — not recomputed on scroll-driven recompose |
| O6 | `MediaStreamPicker.kt:104–117` | Single-pass stream extraction (video/audio/subtitle) replaces 3 independent traversals |
| O7 | `DetailBackdrop.kt:118` / `DetailBody.kt:186, 258` / `MediaDetailSeasons.kt:375` | Coil decode sizes right-sized per use case (1920×1080 hero, 480×600 poster, 640×360 episode) |
| O8 | `DetailScrollState.kt:131–136` | Nav-bar color writes the settled target, not each interpolated frame |
| O9 | `MediaDetailBody.kt:88–89` | Render-thread blur on entrance dropped in favor of alpha-only fade |
| O10 | `MediaDetailBody.kt:601` / `MediaDetailSeasons.kt:319–335` | Card border / width computed once outside item lambdas, not per-item per-recompose |
| O11 | `DetailContent.kt:96–106` | Share-intent lambda memoized on `(itemId, context)` |
| O12 | `DetailViewModel.kt:136–137` | Stream-selection indices read synchronously at click-time from snapshot, not composition-captured |

---

## Critical Findings

### C1. `episodesMap.toMap()` full-copied on every season fetch

**File:** `DetailViewModel.kt:322, 331, 334, 731, 735`  
**Severity:** Critical (allocation amplification during series load)

```kotlin
private val episodesMap = java.util.Collections.synchronizedMap(mutableMapOf<String, List<MediaItem>>())

// Inside loadEpisodes(), called once per season in parallel:
episodesMap[seasonId] = episodeList
_uiState.update {
    it.copy(
        episodes = episodesMap.toMap(),          // ← full copy of entire map
        fetchedSeasonIds = it.fetchedSeasonIds + seasonId,
    )
}
```

**Problem:** For a series with N seasons, `loadSeasons` fans out N parallel `loadEpisodes` calls. Each completion does `episodesMap.toMap()`, creating a **full shallow copy** of the progressively-growing map. Season 1 landing copies a 1-entry map; season 10 copies a 10-entry map. Across N seasons, this is O(N²/2) map-entry copies. Each copy also feeds into `_uiState`, triggering a recomposition that re-runs the `filteredEpisodes` `remember` (see H4) and re-evaluates `maybeComputeSmartPlayTarget` (see C2).

The `.toMap()` is necessary because `MutableStateFlow.update` requires value equality for change detection — but the copy cost scales quadratically with season count.

**Fix:** Use a persistent/immutable map so updates share structure:

```kotlin
// Option A: kotlinx.collections.immutable
private var episodesSnapshot: PersistentMap<String, List<MediaItem>> = persistentMapOf()

// Inside loadEpisodes:
episodesSnapshot = episodesSnapshot.put(seasonId, episodeList)
_uiState.update {
    it.copy(episodes = episodesSnapshot, fetchedSeasonIds = it.fetchedSeasonIds + seasonId)
}
```

This makes each season landing O(log N) instead of O(N), and the snapshot is already immutable so no `.toMap()` copy is needed.

**Option B (no new dependency):** Fold all season results into a single emission using a counter:

```kotlin
// Track pending count; only emit when all parallel fetches complete
val pendingSeasons = java.util.concurrent.atomic.AtomicInteger(seasonList.size)
seasonList.forEach { season ->
    launch {
        mediaRepository.getEpisodes(seriesId, season.id).onSuccess { episodeList ->
            synchronized(episodesMap) { episodesMap[seasonId] = episodeList }
            if (pendingSeasons.decrementAndGet() == 0) {
                // Single emission with the complete map
                _uiState.update {
                    it.copy(episodes = episodesMap.toMap(), fetchedSeasonIds = episodesMap.keys.toSet())
                }
                maybeComputeSmartPlayTarget()
            }
        }
    }
}
```

This collapses N emissions (and N smart-play computations) into one, at the cost of later episode visibility (all-or-nothing per batch). A hybrid — emit the first season immediately for fast paint, then batch the rest — is the best trade-off.

---

### C2. `computeSeriesSmartPlayTarget` re-flattens ALL episodes on every season landing

**File:** `DetailViewModel.kt:326, 380–453`  
**Severity:** Critical (O(N × total_episodes) CPU on series load)

```kotlin
// Called after every loadEpisodes completion:
maybeComputeSmartPlayTarget()

private fun computeSeriesSmartPlayTarget() {
    launch(Dispatchers.Default) {
        val allEpisodes = state.episodes.values.flatten()   // ← O(total episodes)
        // ...
        if (allEpisodes.isEmpty()) {
            if (state.seasons.any { s -> !state.fetchedSeasonIds.contains(s.id) }) {
                return@launch  // more seasons pending — wait
            }
            // ...
        }
        val sorted = allEpisodes.sortedByPlaybackOrder()    // ← O(E log E) sort
```

**Problem:** `maybeComputeSmartPlayTarget()` is called after **every** `loadEpisodes` completion. With N parallel season fetches, smart-play is computed N times. Each computation:
1. Reads `_uiState.value` (cheap)
2. Flattens **all** episodes across **all** fetched seasons — O(total episodes so far)
3. Early-returns if seasons are still pending (but the flatten already happened)
4. If not pending, sorts the entire flattened list — O(E log E)

For a 10-season show with 200 episodes: 10 flatten passes (1+2+3+...+10 = 55 episode-list concatenations), 10 sorts of progressively larger lists. The early-return when seasons are pending saves the `_uiState.update` but not the flatten.

**Fix:** Guard the flatten behind the pending-seasons check:

```kotlin
private fun computeSeriesSmartPlayTarget() {
    launch(Dispatchers.Default) {
        val state = _uiState.value
        // Check pending FIRST — before flattening anything
        val pendingSeasons = state.seasons.any { s -> !state.fetchedSeasonIds.contains(s.id) }
        
        val allEpisodes = state.episodes.values.flatten()
        if (allEpisodes.isEmpty()) {
            if (pendingSeasons) return@launch
            _uiState.update { it.copy(smartPlayTarget = null) }
            return@launch
        }
        
        // If seasons are still arriving, only compute if we have a viable
        // resume/next target (don't wait for all seasons for that).
        // But avoid the full sort until all seasons are in:
        if (pendingSeasons) {
            // Quick scan for a resume episode without full sort
            val resumeEpisode = allEpisodes.firstOrNull { it.hasResumeProgress() }
            if (resumeEpisode != null) {
                // ... set target and return
            }
            return@launch  // defer full next-up computation until all seasons land
        }
        
        val sorted = allEpisodes.sortedByPlaybackOrder()
        // ... full computation
    }
}
```

Combined with C1's batch-emission fix, smart-play would compute exactly **once** per series load instead of N times.

---

### C3. `uiState` nested `combine()` chain causes full-screen recomposition on any Seerr flow tick

**File:** `DetailViewModel.kt:103–129`  
**Severity:** Critical (recomposition fan-out)

```kotlin
val uiState: StateFlow<DetailUiState> = combine(
    _uiState,
    seerrRequestState.requestResult,        // flow 1
    seerrRequestState.radarrServers,        // flow 2
    seerrRequestState.sonarrServers,        // flow 3
    seerrRequestState.isLoadingServices,    // flow 4
) { ... }.let { intermediate ->
    combine(
        intermediate,
        seerrRequestState.tvSeasons,                 // flow 5
        seerrRepository.isConnected(),               // flow 6
        seerrRepository.isRecommendationsEnabled(),  // flow 7
    ) { ... }
}.stateIn(scope, SharingStarted.WhileSubscribed(5_000), DetailUiState())
```

**Problem:** `uiState` aggregates 8 upstream flows (1 `_uiState` + 7 Seerr-related). **Any** emission from **any** flow creates a new `DetailUiState` instance via `.copy()`. Since `DetailUiState` is a single flat data class, a change to `seerrRadarrServers` produces a new instance that is `!=` the previous one — even though `detail`, `seasons`, `episodes`, `smartPlayTarget`, and 20 other fields are identical.

At the UI level, `MediaDetailScreen.kt:189–223` keys the `DetailContentState` `remember` block on ~20 individual fields extracted from `uiState`. So a Seerr-only change **does** correctly skip the state rebuild (the extracted fields haven't changed). However, the `collectAsStateWithLifecycle` at line 73 still triggers a recomposition of `MediaDetailScreen` itself on every uiState emission, re-running the composable body (including the `remember` key comparison, the `ArtworkThemeWrapper` scope, and the `CompositionLocalProvider` lambda) before the `remember` short-circuits.

For flows like `seerrRepository.isConnected()` that may tick periodically (reconnection polling), this creates periodic no-op recompositions of the screen entry composable.

**Fix:** Split Seerr-ephemeral state into a separate `StateFlow` so Seerr flow ticks don't produce a new `DetailUiState`:

```kotlin
// Core state — only changes when detail/seasons/episodes/etc. change
val uiState: StateFlow<DetailUiState> = _uiState
    // ... fold in only the seerr fields that affect the main content tree
    .stateIn(scope, SharingStarted.WhileSubscribed(5_000), DetailUiState())

// Seerr-request-only state — consumed separately by the SeerrRequestDialog
val seerrRequestState: StateFlow<SeerrRequestSnapshot> = combine(
    seerrRequestStateHolder.requestResult,
    seerrRequestStateHolder.radarrServers,
    seerrRequestStateHolder.sonarrServers,
    seerrRequestStateHolder.isLoadingServices,
    seerrRequestStateHolder.tvSeasons,
) { ... }.stateIn(...)

// Connection flags — consumed only by the recommendations gating
val seerrFlags: StateFlow<SeerrConnectionFlags> = combine(
    seerrRepository.isConnected(),
    seerrRepository.isRecommendationsEnabled(),
) { ... }.stateIn(...)
```

The `MediaDetailScreen` would `collectAsStateWithLifecycle` each independently, and the `DetailContentState` `remember` naturally won't re-key when only Seerr-request fields change.

---

### C4. Unconditional parallel fetch of ALL seasons' episodes — no concurrency limit

**File:** `DetailViewModel.kt:286–296`  
**Severity:** Critical (network/memory resource burst)

```kotlin
if (seasonList.isNotEmpty()) {
    seasonList.forEach { season ->
        loadEpisodes(seriesId, season.id)  // each launches a coroutine + network request
    }
}
```

**Problem:** For a series with 30+ seasons (common for long-running shows or daily shows), this immediately fires 30+ concurrent `getEpisodes` network requests. This causes:
- **Network contention:** 30 simultaneous HTTP requests saturate the connection pool and compete for bandwidth, potentially slowing the **core** detail response (which is still in-flight or just landed).
- **Memory burst:** All episode lists are held in `episodesMap` simultaneously. A 30-season show with 20 episodes each = 600 `MediaItem` instances in memory at once, even though only one season is visible at a time.
- **Amplifies C1/C2:** Every season landing triggers `toMap()` + `flatten()` + smart-play recomputation.

**Fix:** Fetch the first season eagerly (for immediate smart-play + visible episodes), then lazy-load remaining seasons:

```kotlin
if (seasonList.isNotEmpty()) {
    // Fetch first season immediately for fast smart-play resolution
    loadEpisodes(seriesId, seasonList.first().id)
    
    // Remaining seasons: fetch on-demand when the user selects the tab
    // (loadEpisodesForSeason already exists and guards via fetchedSeasonIds).
    // OR: fetch the next 2-3 seasons in the background with a semaphore:
    val remaining = seasonList.drop(1)
    remaining.forEach { season ->
        loadEpisodes(seriesId, season.id)  // keep for smart-play, but see C1/C2 batch fix
    }
}
```

Combined with C1's batch emission + C2's deferred smart-play, this reduces the worst-case from 30 emissions + 30 smart-play computations to 1 batch emission + 1 computation.

Alternatively, if eager-all is desired for smart-play correctness, use a `Semaphore` to cap concurrency:

```kotlin
val semaphore = kotlinx.coroutines.sync.Semaphore(5) // max 5 concurrent
seasonList.forEach { season ->
    launch {
        semaphore.withPermit {
            // fetch episodes
        }
    }
}
```

---

## High Findings

### H1. `DetailScrollState` is an unstable type — backdrop/topbar/body never skip

**File:** `DetailScrollState.kt:50–60, 160–170`  
**Severity:** High (cascading recompositions on every scroll frame)

```kotlin
class DetailScrollState internal constructor(   // ← plain class, not @Immutable/data class
    val backdropHeight: Dp,
    val scrollOffset: Float,
    val scrollFraction: Float,
    val scrollCollapsed: Float,
    val contentAlpha: Float,
    val backgroundColor: Color,
    val animatedContainerColor: Color,
    val animatedTitleAlpha: Float,
)

// In rememberDetailScrollState — constructed fresh every recomposition:
return DetailScrollState(
    backdropHeight = backdropHeight,
    scrollOffset = scrollOffset,
    // ... 8 fields
)
```

**Problem:** `DetailScrollState` is a plain `class` — not a `data class`, not annotated `@Immutable`. The Compose compiler infers it as **unstable**, so any composable receiving it (`DetailBackdrop`, `DetailTopBar`, `DetailBodyPortrait`, `DetailBodyLandscape`) is **never skippable**. Even when all field values are identical to the previous recomposition, Compose re-invokes the composable because it can't prove stability.

Since `scrollOffset` and `scrollFraction` change on every scroll frame, `rememberDetailScrollState` returns a new `DetailScrollState` instance on every frame, and all children recompose regardless.

**Fix:** Annotate as `@Immutable` and convert to a `data class` so Compose can use structural equality for skipping:

```kotlin
@Immutable
data class DetailScrollState(   // ← data class + @Immutable
    val backdropHeight: Dp,
    val baseBackdropHeight: Dp,
    val scrollOffset: Float,
    val scrollFraction: Float,
    val scrollCollapsed: Float,
    val contentAlpha: Float,
    val backgroundColor: Color,
    val animatedContainerColor: Color,
    val animatedTitleAlpha: Float,
)
```

With `@Immutable data class`, when `DetailBackdrop` receives a `DetailScrollState` whose fields match the previous frame, Compose skips it. In practice, scroll-derived fields change every frame during scroll — but when the list is **idle** (the common case), the holder is identical and children skip entirely. This is especially impactful on TV where D-pad navigation triggers frequent but small scroll changes.

---

### H2. ~33 entrance-animation coroutines fire on screen entry and never fully release

**Files:** `MediaStreamPicker.kt:76–89` (`StaggeredDetailSection`/`rememberStaggerProgress`), `MediaDetailBody.kt:74–96` (`FadingItem`)  
**Severity:** High (coroutine/snapshot sprawl)

```kotlin
// StaggeredDetailSection — ~13 instances in DetailContentBody:
private fun rememberStaggerProgress(delayIndex: Int): Float {
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(delayIndex * 45L); revealed = true }
    return animateFloatAsState(...).value
}

// FadingItem — ~20 instances in DetailContentBody:
@Composable
internal fun FadingItem(...) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val alpha by animateFloatAsState(...)
}
```

**Problem:** On screen entry, `DetailContentBody` composes ~13 `StaggeredDetailSection`s + ~20 `FadingItem`s. Each creates:
- 1 `mutableStateOf` (snapshot state)
- 1 `LaunchedEffect` (coroutine)
- 1 `animateFloatAsState` (animation + snapshot state)

That's **~33 coroutines + ~66 snapshot state objects** for a one-time entrance animation. After the animations settle (~300ms), each `animateFloatAsState` still holds an active snapshot subscription and the `LaunchedEffect` coroutine remains alive (completed but not garbage-collected until the composable leaves composition).

During scroll, items entering/leaving the `LazyColumn`'s viewport compose new `FadingItem` instances (in the cast row, episode row, related-items row), each firing its own entrance animation. This creates **continuous animation churn during scroll** as items recycle.

**Fix:** Replace the per-item entrance pattern with a single shared entrance coordinator:

```kotlin
// Single source of truth for entrance progress, driven by one coroutine:
@Composable
fun rememberDetailEntranceProgress(): State<Float> {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, MaterialTheme.motionScheme.defaultEffectsSpec())
    }
    return progress.asState()
}

// Pass down via CompositionLocal or parameter; each FadingItem/StaggeredSection
// reads the shared progress and applies a delay-based offset locally:
@Composable
internal fun FadingItem(modifier: Modifier, delayIndex: Int = 0, content: ...) {
    val entrance = LocalDetailEntrance.current  // shared State<Float>
    val alpha = (entrance.value - delayIndex * 0.03f).coerceIn(0f, 1f)
    Box(modifier = modifier.graphicsLayer { this.alpha = alpha }) { content() }
}
```

This collapses ~33 coroutines + ~66 snapshot states into **1 coroutine + 1 snapshot state**. Per-item delay becomes a pure math offset, not a separate animation.

For scroll-recycled items in `LazyRow`s (cast, episodes, related), remove `FadingItem` wrapping entirely — the items should appear instantly as they scroll into view, not re-animate on every recycle.

---

### H3. `canManageSeries` combine triggers `arrRepository.resolveServers()` on unrelated state changes

**File:** `DetailViewModel.kt:89–99`  
**Severity:** High (redundant network/IO on favorite/play toggles)

```kotlin
val canManageSeries: StateFlow<Boolean> = combine(
    _uiState.map { it.detail?.item },           // ← re-emits on favorite toggle
    _uiState.map { it.detail?.providerIds },     // ← re-emits on favorite toggle
    preferencesStore.preferences.map { it.isExperimentalEnabled(...) },
) { item, providerIds, flagEnabled ->
    if (!flagEnabled || item == null) return@combine false
    if (item.mediaType != MediaType.SERIES) return@combine false
    if (providerIds?.get("tvdb")?.toIntOrNull() == null) return@combine false
    val summary = arrRepository.resolveServers().getOrDefault(...)  // ← network call!
    summary.sonarrServers.isNotEmpty()
}.stateIn(scope, SharingStarted.WhileSubscribed(5_000), false)
```

**Problem:** `_uiState.map { it.detail?.item }` emits a new `MediaItem` whenever the item changes — including when `toggleFavorite()`, `markPlayed()`, or `markUnplayed()` update the item via `detail.copy(item = item.copy(isFavorite = !currentIsFavorite))`. Each such toggle re-triggers the combine, which calls `arrRepository.resolveServers()` — potentially a network call to discover Sonarr/Radarr servers — even though server availability has nothing to do with favorite state.

**Fix:** Map only the identity-relevant fields so favorite/played toggles don't invalidate the flow:

```kotlin
val canManageSeries: StateFlow<Boolean> = combine(
    _uiState.map { it.detail?.item?.let { Triple(it.id, it.mediaType, Unit) } },
    _uiState.map { it.detail?.providerIds?.get("tvdb") },
    preferencesStore.preferences.map { it.isExperimentalEnabled(ExperimentalFeature.DIRECT_ARR_INTEGRATION) },
) { itemKey, tvdbId, flagEnabled ->
    if (!flagEnabled || itemKey == null) return@combine false
    if (itemKey.second != MediaType.SERIES) return@combine false
    if (tvdbId?.toIntOrNull() == null) return@combine false
    val summary = arrRepository.resolveServers().getOrDefault(...)
    summary.sonarrServers.isNotEmpty()
}.stateIn(...)
```

By mapping to `(id, mediaType)` instead of the full `MediaItem`, favorite/played toggles (which change `isFavorite`/`isPlayed` but not `id`/`mediaType`) produce structurally-equal emissions that `StateFlow` deduplicates — `resolveServers()` is called only when the item identity or tvdb id actually changes.

---

### H4. `filteredEpisodes` mapValues re-allocated N times during parallel season fetch

**File:** `MediaDetailBody.kt:479–485`  
**Severity:** High (allocation churn compounding with C1)

```kotlin
val filteredEpisodes = remember(episodes, preferences.skipSpecials) {
    if (preferences.skipSpecials) {
        episodes.mapValues { (_, eps) -> eps.filter { it.seasonNumber != 0 } }
    } else {
        episodes
    }
}
```

**Problem:** This is correctly memoized — but during parallel season loading (C1/C4), `episodes` changes N times (once per season landing). Each change re-runs `mapValues`, allocating a **new Map + new filtered List per season**. For a 10-season show with skipSpecials enabled, this is 10 full map rebuilds, each copying all accumulated seasons.

**Fix:** Combine with C1's batch-emission fix — if `episodes` only emits once (after all parallel fetches complete), `mapValues` runs exactly once. If incremental emission is desired, filter at the VM level so the UI receives already-filtered data:

```kotlin
// In DetailViewModel.loadEpisodes, before updating uiState:
val displayEpisodes = if (preferences.value.skipSpecials) {
    episodeList.filter { it.seasonNumber != 0 }
} else {
    episodeList
}
episodesMap[seasonId] = displayEpisodes
```

This moves the filter to the data layer (runs once per season, not per recomposition) and eliminates the `remember`-based filter entirely.

---

## Medium Findings

### M1. Episode sort inside `AnimatedContent` lambda not memoized

**File:** `MediaDetailSeasons.kt:213–215`  
**Severity:** Medium

```kotlin
AnimatedContent(
    targetState = selectedSeasonIndex to (seasonEpisodes?.size ?: 0),
    // ...
) { (seasonIdx, episodeCount) ->
    val currentEpisodes = seasons.getOrNull(seasonIdx)?.let { episodes[it.id] }
        ?.sortedBy { it.episodeNumber ?: it.indexNumber ?: Int.MAX_VALUE }
        ?.let { sorted -> if (episodesDescending) sorted.reversed() else sorted }
```

**Problem:** The sort + reverse runs on every recomposition of the `AnimatedContent` content lambda. While `AnimatedContent` caches its content during transitions, any recomposition triggered by a parent state change (e.g., scroll-driven `FadingItem` alpha updates in siblings) re-executes this sort.

**Fix:** Extract into a `remember`:

```kotlin
) { (seasonIdx, episodeCount) ->
    val currentEpisodes = remember(seasonIdx, episodes, episodesDescending) {
        seasons.getOrNull(seasonIdx)?.let { episodes[it.id] }
            ?.sortedBy { it.episodeNumber ?: it.indexNumber ?: Int.MAX_VALUE }
            ?.let { sorted -> if (episodesDescending) sorted.reversed() else sorted }
    }
```

---

### M2. Download sheet duplicates already-fetched episode data

**File:** `DetailViewModel.kt:140–141, 723–740`  
**Severity:** Medium (memory duplication)

```kotlin
private val episodesMap = ...syncedMap(...)
private val downloadSheetEpisodesMap = ...syncedMap(...)  // ← separate copy of same data

fun loadDownloadSheetEpisodes(seasonId: String) {
    // ... fetches episodes that are likely already in episodesMap
    mediaRepository.getEpisodes(seriesId, seasonId)
}
```

**Problem:** The series download sheet (`SeriesDownloadSheet`) fetches and stores episodes in `downloadSheetEpisodesMap`, separate from `episodesMap` which already holds the same data for the seasons section. For a series where all seasons were already fetched by the main display (C4's parallel fetch), this re-fetches and duplicates the episode lists.

**Fix:** Reuse `episodesMap` as a cache for the download sheet:

```kotlin
fun loadDownloadSheetEpisodes(seasonId: String) {
    // Reuse already-fetched data from the main seasons display
    episodesMap[seasonId]?.let { episodes ->
        _uiState.update { it.copy(downloadSheetEpisodes = it.downloadSheetEpisodes + (seasonId to episodes)) }
        downloadSheetFetchedSeasonIds = downloadSheetFetchedSeasonIds + seasonId
        return
    }
    // Only fetch if not already in the main cache
    // ...
}
```

---

### M3. Per-item `FadingItem` in scrollable rows creates animation churn during scroll

**File:** `MediaDetailBody.kt:529, 565, 609, 696` (cast, collection, related, Seerr rows)  
**Severity:** Medium

```kotlin
TvFocusableItemRow(items = detail.people, ...) { _, person, focusModifier ->
    val personClick = remember(person.id) { { onPersonClick(person.id) } }
    FadingItem {                              // ← per-item entrance animation
        PersonItem(...)
    }
}
```

**Problem:** `FadingItem` wraps every item in scrollable `LazyRow`s. As items scroll into/out of the viewport, each composing item fires a `LaunchedEffect(Unit)` + `animateFloatAsState` entrance. During fast scroll, this creates a stream of short-lived coroutines and animations that never visually complete before the item scrolls off-screen. The animation cost (coroutine launch, snapshot subscription, graphicsLayer re-execution) is paid but the visual effect is invisible.

**Fix:** Remove `FadingItem` from scrollable row items — entrance animation should apply only to **section headers** and **the first viewport** of content, not to items that recycle during scroll:

```kotlin
TvFocusableItemRow(items = detail.people, ...) { _, person, focusModifier ->
    val personClick = remember(person.id) { { onPersonClick(person.id) } }
    PersonItem(  // ← no FadingItem wrapper
        person = person,
        imageUrl = getImageUrl(person.id),
        onClick = personClick,
        modifier = focusModifier,
    )
}
```

---

### M4. Theme music player started without preference gate

**File:** `DetailViewModel.kt:255–256`  
**Severity:** Medium (unnecessary network/audio resource)

```kotlin
val themeSourceId = detail.item.seriesId ?: itemId
themeMusicPlayer.playThemeFor(themeSourceId)
```

**Problem:** `playThemeFor` is called unconditionally on every successful detail load. If the user has disabled theme music in preferences, the `ThemeMusicPlayer` may still initiate a network fetch of the theme audio before discovering the preference says "off" (depending on implementation). This wastes bandwidth and audio-decode resources.

**Fix:** Gate on the preference:

```kotlin
if (preferences.value.themeMusicEnabled) {
    themeMusicPlayer.playThemeFor(themeSourceId)
}
```

If `ThemeMusicPlayer.playThemeFor` already checks internally, this is a no-op — but the guard prevents the method call overhead and any internal fetch-from-network logic.

---

### M5. Autoplay trailer in backdrop consumes network/battery unconditionally

**File:** `DetailBackdrop.kt:124–142`  
**Severity:** Medium (resource utilization)

```kotlin
val playAutoplayTrailer = preferences.trailerAutoplay && trailerVideo != null && !autoplayEmbedFailed
if (playAutoplayTrailer && trailerKey != null) {
    InlineTrailerPlayer(
        videoKey = trailerKey,
        // ... YouTube embed autoplay
    )
}
```

**Problem:** When `trailerAutoplay` is enabled, the backdrop immediately starts a YouTube embed that streams video on screen open. This:
- Consumes significant bandwidth (video stream) on every detail navigation
- Keeps the YouTube iframe player alive in the composition tree even when scrolled off-screen (the backdrop's `graphicsLayer { alpha = ... }` hides it visually but the player keeps decoding)
- Drains battery on mobile, especially on cellular

**Fix:** Gate autoplay on network type and pause when scrolled:

```kotlin
val playAutoplayTrailer = preferences.trailerAutoplay &&
    trailerVideo != null &&
    !autoplayEmbedFailed &&
    adaptiveBitrateManager.isUnmeteredConnection() &&  // WiFi only
    scrollFraction < 0.3f  // stop when user scrolls past backdrop
```

Also consider pausing the trailer player (via a lifecycle-aware flag) when the backdrop alpha drops below a threshold, so it stops decoding when not visible.

---

## Low / Enhancement Findings

### L1. `Collections.synchronizedMap` adds unnecessary overhead for Main-thread-only access

**File:** `DetailViewModel.kt:140–141`  
**Severity:** Low

```kotlin
private val episodesMap = java.util.Collections.synchronizedMap(mutableMapOf())
private val downloadSheetEpisodesMap = java.util.Collections.synchronizedMap(mutableMapOf())
```

**Problem:** All access to these maps happens inside `launch { }` blocks on `viewModelScope`, which dispatches on `Dispatchers.Main`. The `synchronizedMap` wrapper adds a `synchronized` lock acquire/release on every read/write, but there's never contention from multiple threads. `computeSeriesSmartPlayTarget` switches to `Dispatchers.Default`, but it reads `_uiState.value.episodes` (the already-copied immutable snapshot), not `episodesMap` directly.

**Fix:** Use plain `mutableMapOf()` — the viewModelScope's Main dispatcher provides serialization:

```kotlin
private val episodesMap = mutableMapOf<String, List<MediaItem>>()
private val downloadSheetEpisodesMap = mutableMapOf<String, List<MediaItem>>()
```

---

### L2. `getImageUrl(episode.id)` called 2× per `EpisodeCard` recomposition without memoization

**File:** `MediaDetailSeasons.kt:302–307, 370`  
**Severity:** Low

```kotlin
val peek = rememberMediaPeek(
    item = episode,
    posterUrl = getImageUrl(episode.id),     // ← call 1
    backdropUrl = getImageUrl(episode.id),    // ← call 2
    blurHash = episode.blurHashes.primary,
)
// ...
MediaImage(
    url = getImageUrl(episode.id),            // ← call 3
    // ...
)
```

**Problem:** `getImageUrl(episode.id)` is called 3 times per `EpisodeCard` recomposition. While URL string construction is cheap, it's pure waste.

**Fix:**

```kotlin
val episodeImageUrl = remember(episode.id) { getImageUrl(episode.id) }
val peek = rememberMediaPeek(
    item = episode,
    posterUrl = episodeImageUrl,
    backdropUrl = episodeImageUrl,
    blurHash = episode.blurHashes.primary,
)
// ...
MediaImage(url = episodeImageUrl, ...)
```

---

### L3. `SeriesDownloadSheet` episode selection builds full map copy on each toggle

**File:** `DetailViewModel.kt:731, 735` (same pattern as C1, applied to download sheet)

Same `downloadSheetEpisodesMap.toMap()` full-copy pattern. Lower severity because the download sheet is user-initiated (not automatic like the main seasons fetch), but the same fix applies.

---

### L4. `detail?.item` read inside `DetailContentCallbacks.onSeasonSelected` captures stale detail

**File:** `MediaDetailScreen.kt:258–261`  
**Severity:** Low (correctness/perf interaction)

```kotlin
onSeasonSelected = { seasonId: String ->
    val seriesId = detail?.item?.seriesId ?: itemId
    viewModel.loadEpisodesForSeason(seriesId, seasonId)
},
```

**Problem:** This lambda is inside the `callbacks` `remember` block keyed on `detail` (among others). When `detail` changes (e.g., favorite toggle), the entire callbacks bundle is rebuilt — even though `onSeasonSelected` only needs `detail?.item?.seriesId`, which doesn't change on favorite toggle. This contributes to unnecessary callback reallocation.

**Fix:** Extract `seriesId` into a separate remembered value so the callback doesn't re-key on full `detail`:

```kotlin
val seriesIdForSeasons = remember(itemId) {
    detail?.item?.seriesId ?: itemId
}
// In callbacks:
onSeasonSelected = { seasonId: String ->
    viewModel.loadEpisodesForSeason(seriesIdForSeasons, seasonId)
},
```

Then `seriesIdForSeasons` can be added to the callbacks `remember` keys instead of the full `detail`.

---

### L5. `DetailContent` `scrollBehavior` created unconditionally even when top bar is transparent

**File:** `DetailContent.kt:79`  
**Severity:** Low (minor object allocation)

```kotlin
val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
```

**Problem:** `exitUntilCollapsedScrollBehavior` creates a `TopAppBarState` + nested scroll connection that participates in the scroll chain. The `MediumTopAppBar` uses it, but the detail screen's collapsing behavior is driven by the custom `DetailScrollState` (parallax, title alpha), not by the standard `scrollBehavior`. The two scroll systems coexist but may produce redundant scroll-offset tracking.

**Fix:** Verify whether `scrollBehavior` is actually needed — if `MediumTopAppBar` can function with a no-op `scrollBehavior`, removing it eliminates a nested-scroll interceptor from the `LazyColumn`'s scroll chain.

---

### L6. `sideEffect` for nav-bar color runs on every recomposition

**File:** `DetailScrollState.kt:131–136`  
**Severity:** Low

```kotlin
SideEffect {
    if (navBarColor.value != targetBackgroundColor) navBarColor.value = targetBackgroundColor
}
```

**Problem:** `SideEffect` runs after every successful recomposition of `rememberDetailScrollState`. The guard `if (navBarColor.value != targetBackgroundColor)` prevents redundant writes, but `targetBackgroundColor` is recomputed from `artworkColors` + theme flags on every recomposition. During scroll, this means the lerp + when-expression runs every frame even though the result hasn't changed (scroll doesn't change artwork colors).

**Fix:** Extract `targetBackgroundColor` into a `remember` keyed on its inputs:

```kotlin
val targetBackgroundColor = remember(baseOverlayColor, isSynthwave, isSoothing, isLightTheme) {
    when {
        isSynthwave -> ThemeVariantColors.SYNTHWAVE_DETAIL_BG
        isSoothing -> MaterialTheme.colorScheme.background
        isLightTheme -> MaterialTheme.colorScheme.background
        else -> lerp(baseOverlayColor, Color.Black, 0.65f)
    }
}
```

Note: `MaterialTheme.colorScheme.background` inside `remember` would need to be read outside — adjust accordingly. The key insight is that scroll-driven recompositions shouldn't recompute a color that only depends on artwork + theme.

---

## Summary Table

| ID | Severity | File | Issue | Est. Impact |
|----|----------|------|-------|-------------|
| C1 | Critical | `DetailViewModel.kt:322` | `episodesMap.toMap()` full-copied per season | O(N²) map copies on series load |
| C2 | Critical | `DetailViewModel.kt:380` | Smart-play flattens ALL episodes N times | O(N × E) CPU on series load |
| C3 | Critical | `DetailViewModel.kt:103` | `uiState` 8-flow combine → screen recompose on any Seerr tick | Periodic no-op recompositions |
| C4 | Critical | `DetailViewModel.kt:286` | All seasons fetched in parallel unconditionally | Network/memory burst on long series |
| H1 | High | `DetailScrollState.kt:50` | Unstable class → children never skip | Every-scroll-frame recomposition |
| H2 | High | `MediaStreamPicker.kt:76`, `MediaDetailBody.kt:74` | ~33 entrance-animation coroutines | Coroutine/snapshot sprawl |
| H3 | High | `DetailViewModel.kt:89` | `resolveServers()` on favorite toggle | Redundant network on state change |
| H4 | High | `MediaDetailBody.kt:479` | `filteredEpisodes` rebuilt N times | Allocation churn compounding C1 |
| M1 | Medium | `MediaDetailSeasons.kt:213` | Episode sort not memoized | Re-sort on unrelated recompose |
| M2 | Medium | `DetailViewModel.kt:723` | Download sheet duplicates episode data | Memory duplication |
| M3 | Medium | `MediaDetailBody.kt:529+` | `FadingItem` per scrollable item | Animation churn during scroll |
| M4 | Medium | `DetailViewModel.kt:255` | Theme music without pref check | Unnecessary network/audio |
| M5 | Medium | `DetailBackdrop.kt:124` | Trailer autoplay not network/scroll-gated | Bandwidth/battery on every open |
| L1 | Low | `DetailViewModel.kt:140` | `synchronizedMap` for Main-only access | Unnecessary lock overhead |
| L2 | Low | `MediaDetailSeasons.kt:302` | `getImageUrl()` called 3× per card recompose | Redundant string building |
| L3 | Low | `DetailViewModel.kt:731` | Download sheet `toMap()` copy | Same as C1, user-initiated |
| L4 | Low | `MediaDetailScreen.kt:258` | `onSeasonSelected` captures stale `detail` | Callback reallocation |
| L5 | Low | `DetailContent.kt:79` | Redundant `scrollBehavior` | Extra nested-scroll interceptor |
| L6 | Low | `DetailScrollState.kt:131` | Nav-bar color computed per-frame | Redundant color lerp during scroll |

---

## Recommended Fix Order

**Phase 1 — Series-load hot path (C1 + C2 + C4):** These three compound each other. Fixing them together (batch emission + deferred smart-play + concurrency limit) eliminates the worst-case O(N²) allocation and O(N × E) CPU spike. Estimated effort: 2–3 hours.

**Phase 2 — Recomposition reduction (H1 + C3):** Making `DetailScrollState` stable + splitting the `uiState` combine chain reduces baseline recomposition cost. Estimated effort: 1–2 hours.

**Phase 3 — Entrance animation consolidation (H2 + M3):** Replace per-item entrance pattern with shared coordinator + remove `FadingItem` from scrollable rows. Estimated effort: 2 hours.

**Phase 4 — Remaining:** H3, H4, M1–M5, L1–L6 are independent and can be addressed incrementally.
