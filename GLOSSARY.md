# JellyPlay glossary

Shared vocabulary for the client codebase: terms, idioms, and defect rules. Each entry carries the term, its essence, and its defect rules; mechanics live in `CONTEXT.md` and entries point there by section name. The companion plugin's vocabulary (contract, capability probe, namespaces, tombstones, dispatch, resilient fetch…) lives in `jellyfin-plugin-jellyplay/GLOSSARY.md` and is not duplicated here. A "defect" note marks a shape the codebase forbids — usually source-scan or test pinned.

## State sync (client side)

**Sync reservation** — the `jpsync.*` key space (mirrors, cursors, per-namespace toggles, device id). One module owns the grammar; adapters/engine acquire keys through it, never raw literals. It is the one classifier of key kinds: a derived per-device payload beside settings (e.g. a widget's CW buffer) is never a setting, and its exclusion belongs to the reservation's policy — **a store hand-rolling its own exclusion is a defect**.

**Sync mirror** — the last-synced snapshot a `ProfileSyncAdapter` diffs against to detect dirty values. Exactly one implementation; every adapter delegates — **an adapter hand-rolling the comparison is a defect**.

**Mirror cycle read** — cycle-start mirror state: each adapter's dirty + tombstone faces read ONCE into a `CycleNamespaceState` (two mirror reads per adapter per cycle); pending face is arithmetic over cycle inputs, never a re-diff. A dirty value equal to remote resolves synced there, never pending forever.

**Sync adapter base** — jvmShared base over the adapter↔mirror delegation seam (dirty/delete/markSynced, `deleteRemote` clear-tail, tombstone + key-agree rules): `MirrorProfileSyncAdapter`; an adapter implements namespace/snapshot/applyRemote + a deleteLocally hook. Tombstone-roaming namespaces override `deletedKeys`; delegating it for value-only namespaces would push resets as tombstones. Nine bindings multi-bind the interface; `ProfileSyncRepository` consumes them sorted by `REGISTRATION_ORDER` (Koin `getAll` on secondary binds is index-allocation order, not declaration — `DataKoinModulesTest` pins via `activeNamespaces`).

**Playlist row codec** — ONE home for smart/mood/moodpref row shape: `PlaylistRowCodec` owns entity↔payload↔domain (lenient JSON decode, wall-clock stamps) + criteria-blob schema; repositories' `toDomain`/`toEntity` and the backup slice mapper delegate. `{family}/{id}` key grammar one home beside `parseSlashKey`; JSON blobs stay opaque writer-produced strings.

## Plugin client

**Plugin role** — narrow consumer-facing interface over the one plugin client family (ADR-0010 §5): settings sync, device registry + push, analytics, transcodes, ratings, markers. One family impl satisfies all roles; consumers/fakes depend on the one role they use.

**Contract mismatch** — capabilities-probe refused-contract verdict: plugin answered but `contractVersion` outside `JellyPlayPluginStatusStore.KNOWN_CONTRACT_VERSIONS`. Status stays `UNAVAILABLE` (every gate fails closed like an absent plugin) while the `contractMismatch` side channel carries served version + supported range + plugin version (settings banner + admin hint consume it; `servedPluginVersion` feeds the settings summary). Range label is `supportedRangeLabel` on the mismatch type — **a second hand-fold of the range is a defect**.

**Library client families** — impl-side counterpart of consumer seams: small ctor-injected adapters over the one `JellyfinApiEngine` single; each family's wire bodies exist in exactly one place. → CONTEXT.md "Core data repositories".

## Data layer

**Library feed seam** — the former `MediaRepository` union is GONE: every consumer injects family seams (`LibraryFeed`, `DetailFeed`, `CollectionOperations`, `MusicCatalogue`, `UserDataChanges`, `UserDataWriteOperations`, `HomeFeed`, plus one-member `SeriesNotificationReads`/`PhotoFolderReads`), all aliased onto one `MediaRepositoryImpl` single. **No member carries a default argument** (family-seam rule, kept for mockk captures) — seam-typed callers pass defaults explicitly; `markPlayed` only on `UserDataWriteOperations` (drainer uses `recordDrainedPlays`). → CONTEXT.md "Core data repositories".

**Media freshness ledger** — the media repo's freshness/cache chassis as one ctor-injected collaborator (`MediaFreshnessLedger`); `MediaFreshnessLedgerTest` pins the ladder. → CONTEXT.md "Core data repositories".

## Home

**Home row module** — everything one `HomeSectionType` knows behind one registry interface; the CW rule (played-row exclusion, hidden ids, NextUp merge) belongs to the CW module alone. → CONTEXT.md "Home feature".

**Row transport registration** — the row module's network counterpart: one registration per type stating batch subcall + refresh arm once; a row without a refresh arm states absence on the registration, not an absent table entry. → CONTEXT.md "Home feature".

**Fetcher cache spec** — fetcher-local list every sub-call cache declares itself on; `invalidateCaches` iterates it — a forgotten clear serves stale badges for a TTL. → CONTEXT.md "Home feature".

**Plugin home content** — plugin rows + curated-hero family as one module over the `JellyPlayHomeSectionSources` port, exposing sub-call caches so the spec list stays total. → CONTEXT.md "Home feature".

**Items query executor** — /Items family transport half over `LibraryItemsQuerySpec`; a new dimension = one spec field + one executor line. → CONTEXT.md "Home feature".

**Home write generation** — repo-local monotonic counter bumped by the funnel AFTER a row-mutation op, paired with assembled-payload drop; **no token crosses the repo→port→fetcher seam**. → CONTEXT.md "Home feature" + "Core data repositories".

**Home content state core** — the eleven content-list fields of `HomeUiState` travel as one referentially-stable embedded value; deliberately NOT the wholesale `DetailContentState.core` shape. → CONTEXT.md "Home feature".

**Home feature gate** — opt-in decorations ride one reactive spine (combine gate flows → dedupe → collectLatest once per distinct emission). Folded verdicts: HardError keys on `isHardError`, header chip on refresher `fetchFailed` — **a raw `error != null` restatement at either site is a defect**. → CONTEXT.md "Home feature".

**Home user-data reactor** — user-data reaction chain as its own module; invariants: every change fans into exactly one silent refresh (never dropped), own-write echo must not re-trigger the forced path, drained-play completion echo bypasses throttle + demotion. `HomeRefresher` keeps WHEN, reads it through a five-member port. → CONTEXT.md "Home feature".

**HomeDiscovery slice rows** — the store's per-user read/restore rows derive from the spec tier; 42 of 47 derive, five hand-kept exclusions pinned by `HomeDiscoverySpecDerivationTest`. → CONTEXT.md "Home feature" (`HomeSectionPrefs`).

## Details

**Detail section module** — everything one detail section knows behind one registry interface (Home row module pattern); `DetailSectionRegistryTest` pins exactly one owner per kind (**a kind claimed by two modules renders twice**) and admission somewhere in the every-family span (**an over-tightened row renders nothing**). → CONTEXT.md "Details feature".

**Detail play sequencer** — play-click ordered effects decided once; process-wide seams arrive as one `MediaDetailPlaybackSeams` ctor bundle, not mid-body `koinInject` pulls. → CONTEXT.md "Details feature".

**Detail constructor arms** — the nullable "unwired" ctor arms as two bundles (`DetailPluginArms`, `DetailBookArms`); silent absence decided at one place per arm; tests construct bundles, not nine doubles. → CONTEXT.md "Details feature".

**Detail content state core** — VM's `DetailUiState` carried wholesale + genuinely screen-resolved extras; **no fold restates core fields**. → CONTEXT.md "Details feature".

**Model-typed enrichment roles** — enrichment seams interface-segregated per family; all roles model-only — wire **DTOs stop at the client boundary**. → CONTEXT.md "Details feature".

**Seerr detail view** — movie/tv union as one pure fold `SeerrDetailView.of(movie, tv)` computed ONCE at screen root — **a descendant re-deriving it is a defect**. → CONTEXT.md "Details feature".

**Detail load orchestrator** — the load-graph deep module (HomeRefresher pattern), sole writer of content state: VM-side UI writes cross a narrow `update` seam — patches, never a second writer. → CONTEXT.md "Details feature".

## Downloads

**Download action module** — admission (`DownloadActions.availableActions`) is consumed — **a row re-encoding it as a per-status `when` violates the table's contract**; row placement stays code keyed on the available-action set — **a placement flag table is the rejected shape**. → CONTEXT.md "Downloads & insights".

**Download action faces** — icon + label per action split by VOICE, never merged across locales (`bulk`/`bulkAll` — no DELETE/`rowCancel`/`groupPlay`); `Color.Unspecified` tint means NO colorFilter (intrinsic black), not inherit. → CONTEXT.md "Downloads & insights".

**Downloads resync actions** — resync/freshness family as one constructed module; ownership ceilings in `DownloadsViewModelOwnershipTest`. → CONTEXT.md "Downloads & insights".

**Downloads series-policy actions** — the policy store's two Downloads-screen consumers (gh-181 Ask-suggestion card + ADR-0015 armed list) as one constructed module (`DownloadsSeriesPolicyActions` from `SeriesDownloadPolicyStore` + `DownloadIntake` + `DownloadsStore`): suggestions observe/confirm/dismiss (confirm rides the plain intake — no policy write), the `armedSeries` flow (pure `armedSeriesRows` projection beside the VM — name fallback, `resolveAgainst` the LIVE global mode, name-then-id order), and `unArmSeries`, the disarm verb's only UI home. Mode labels reuse core:ui's `download_sheet_policy_*` — one home for the copy; one-shot feedback rides the holder's `postMessage`; screen-forward resolution stays VM-side.

**Queue placement verbs** — `moveToFront`/`moveToBack` on the `DownloadQueue` seam; `DownloadRepositoryImpl` computes placement over the DAO snapshot at call time (the authoritative queue state) — **a caller deriving max/min placement over its own uiState snapshot is the defect** (stale under concurrent list change). `DownloadRepositoryImplPlacementTest` (androidHostTest) pins placement incl. the concurrent-arrival case; the surface ratchet records the sanctioned 11→13 raise.

**Armed series** — the series download-policy state that keeps a series in the auto-download check's universe regardless of local files (gh-225/ADR-0015): an arm row (`series_download_policies`, mode `FOLLOW_GLOBAL` or an explicit override) survives watched-deletion hygiene, so a series with 0 downloaded episodes is still checked every pass. Arming is implicit-on-download (any series-shaped download arms) beside explicit sheet choices. Un-arm: explicit NEVER always; list-management deletion at zero rows; **watch-boundary deletions (auto-delete-after-watch, smart downloads, the #14 offer, the keep-days sweep) never un-arm**. Download candidates never include played or virtual episodes in any mode. A separate watchlist store, coverage-semantics widening, or synced arms are the rejected shapes; "watchlist" is not this repo's word.

## Playback

**Up Next module** — everything "what plays next" knows: queue-vs-sibling advance precedence (queue head over episode sibling), Up Next card projection, one settle-then-consume protocol for activating a queue row. Card and advance read the same verdict; SyncPlay gate the one deliberate split (display projections gate on uiState mirror, advance on manager flag). `SegmentCalculator` + autoplay gate consume the verdict.

**Manual advance verb** — every user-driven advance as one session verb `userAdvance(variant: UserAdvance)` (sealed `FreshOpen`/`Episode`/`Previous`/`Next`/`WatchedAndSkip`/`QueueItem`): engagement reset (`autoplayController.onUserInteraction`) ordered before the variant's verb; FreshOpen additionally resets the auto-advance streak and folds the full `initialize`; binge-streak resets for the other five stay inside UpNext (`playNextManually` owns #17). The six VM advance arms are one-line variant mappings — **an advance site pair-coding the engagement dance by hand is the defect**. `UserAdvanceTest` pins per-variant ordering.

**End-of-media decision** — end-of-media dispatch as one pure fold composing six tested verdict modules into ordered effects; #17 keep gate single-sited in `closeTail` across both close entries. → CONTEXT.md "Playback session".

**Transport routing module** — SyncPlay → cast → local seek/play precedence ladder + per-arm bypass rules behind one session-owned interface; collaborators call the router directly. → CONTEXT.md "Playback session".

**Engine commands funnel** — DIRECT decode-surface commands that never ride the transport ladder (playback-speed family + scrubbing bracket); "what's playing" reads through the `playbackFacts()` snapshot — no caller reaches the session manager directly. → CONTEXT.md "Playback session".

**Volume command chassis** — the four volume commands as one `VolumeCommandChassis` in player-contract beside `VolumeCommandTemplates`, composing a small `NativeVolumeSeam` (native residue) and implementing the full `NativeVolumeSurface` (unmute memory chassis-owned, `@Volatile`, audible-only). Containment is the `dispatch` ctor knob (Android engines' open hook vs the desktop alive-guard); snapshot source is `preMuteSnapshotLevel()` seam data (Android system-stream read vs desktop native re-read). All four engines compose it — **a host hand-copying template bodies or unmute glue is the defect**. `VolumeCommandChassisTest` drives the desktop arm end-to-end without libmpv.

**Player UI commands module** — preference-write choreography (guard → uiState mirror → persist launch → apply; sibling-snapshot-captured-before-write + engine-before-mirror rules) as one module, not per-setter copies; each setter's divergence stays code, not a flag table. Aspect-ratio belongs here despite persisting nothing. → CONTEXT.md "Playback session".

**Track selection hub** — `TrackSelectionHelper` consumes the pure `TrackSelectionPolicy` (pending→stored→scoring→type-specific→matcher→fallback ladder + side-loaded id grammar), never re-encodes. Downloaded-subtitle use is a hub verb: `useDownloadedSubtitle(rowKey)` returns a `UseDownloadedVerdict` (user message as data) and runs the same chain as a sheet pick — **a verdict hand-folded in the VM is the defect**. → CONTEXT.md "Playback session".

**Config delta** — one decision half for all four engines: `EngineConfigDelta.of(old, new)`; every adapter's `onConfigChanged` switches on flags, not per-field `old != new` ladders. Subtitle carve-out (style change excluding delay) computed inside `of()` from raw configs — never derived from other flags. → CONTEXT.md "Engine layer".

**Engine event verdict** — event→decision policy both video hosts ride (`EngineEventCoordinator` folds raw `EngineEventSource` into `EngineDecision`s; host divergence pinned as `Config` knobs); decision→execution split is the depth — the coordinator never writes uiState nor commands the engine (657-line suite, flow fixtures + injected clock, zero mocks). → CONTEXT.md "Engine layer".

**Session spine** — `PlayerSessionManager`: engine stack + load bookkeeping deep module. Per-item teardown hooks are session-private behind the `sessionHooks` delegate (recording-double seam `hooksOverride` unchanged) — **a hook body re-exported on the session root is the defect**; the VM's reachable `playbackSession.*` symbol set is exact-set pinned in `ControllerOwnershipTest`. SyncPlay group mode verbs are session arms over its bridge — the VM no longer touches the manager. → CONTEXT.md "Playback source resolution".

**Session position ledger** — position/report family as one module; pinned phase-verb orderings (stop-inputs snapshot BEFORE teardown; restore/allocate before persistence-window reset) private. → CONTEXT.md "Playback session".

**Playback reload coordinator** — five reload shapes as ONE verb `reload(variant)`; sealed variants carry deltas as data; ordering invariants private; a new reload feature adds a variant, not another choreography copy. → CONTEXT.md "Playback session".

**Cinema pre-roll sequencer** — cinema-mode chain as session-root state; **deliberately NOT a standalone module** — bodies need the full session graph, extraction fails ADR-0012's shallow-module test; re-proposing it re-litigates that ADR. → CONTEXT.md "Playback session".

**Player preference leaf table** — ONE preference→uiState-leaf mapping; three strategies read it (seed/projector/fanout); non-rows stay hand-written. → CONTEXT.md "State slices" (`PLAYER_PREF_LEAVES`).

**Player sheet module** — everything one player sheet knows (entry points, commands, slice region) registered in a sheet registry at Home-row-module grain; sheet tests derive from `PlayerSheetRegistry.all` — **a hand sheet list there is the defect shape**. → CONTEXT.md "Playback source resolution".

**Player parameter bundles** — overflow menu takes `capabilities: EngineCapabilities` + one `PlayerOverflowMenuActions` bundle instead of re-flattening capability projection into per-call-site booleans; `ticksToFraction` (state/) the one home for seek-bar ticks→fraction clamp policy.

**mpv load plan** — per-request mpv option ladder as one pure plan `MpvLoadOptionsPlan`: ordered (option, value) writes — TLS reset trio unconditional in BOTH spellings, normalized + blank-skipped language lists, User-Agent extraction, header persistence discipline. Each engine supplies only the transport seam; desktop shares the ladder (`MpvDesktopEngineAndroidParityTest` pins).

**mpv option vocabulary home** — option vocabulary (`MpvConfigMapping`, `MpvConfigApplier`, `MpvErrorTaxonomy`, `MpvAudioMappings`, `MpvLoadOptionsPlan`, `MpvProperties`) in core/player-contract; engine choreography (`MpvCore`, `MpvBinding`, host engines) in feature/player-video; files keep the `feature.player.video` package — deliberate split package to avoid import churn; **re-homing is a per-file rename, not an API break**. The desktop mpv host rides the same geography (player-video jvmMain `desktop.player` split package, parity suite beside it); the factory stays app-side. `MpvAudioMappings` helpers public (`internal` is module-scoped to player-video; `MpvCore` consumes across the edge). → CONTEXT.md preamble (package↔module anomaly).

**Stats publish guard** — engines' video-stats change-guard (last-value cache, scalar-change predicate, forced periodic full re-read, publish funnel) as one `StatsPublishGuard` in player-contract beside `MpvStatsProjection`, parameterized by read source; published-shape comparison (HDR fold computed before dedup guard) the pinned contract. Exo + mpv ride it.

**Audio shell seam** — app shell's read surface over the audio family: `ShellAudioSurface` (narrow interface) plus the face splits (`AudioRemoteControlSurface`, `AudioServiceSurface`, `AudioFocusCommands`, `NowPlayingSurface`) instead of concrete androidMain `AudioPlaybackManager` crossing into shell code; only Koin modules name the concrete class. Crossfade hand-off tail in `AudioCrossfadeHandoff` (crossfader decides, handoff owns stop-report→commit→engine swap→session rebuild).

**HDR mode verdict** — playback-mode derivation (`resolveHdrPlaybackMode`) + HDR family vocabulary (dovi/hdr10+/hlg) in `HdrOutputPolicy`; `hdrFamilyOf` the one parser feeding `hdrContentFromStreams`/sheet/metadata row; engines consume the same decision. `HdrPlaybackModeTest` pins the JVM lane.

**mpv core** — commonMain mpv engine choreography; platform code is an **mpv binding** satisfying the binding seam; two bindings make it real, a fake binding tests core without libmpv. "Where does mpv behavior live" has this term as its one-word answer. → CONTEXT.md "Engine layer".

**mpv recovery choreography** — stall-watchdog wiring (sticky-hwdec precedence ladder) + HDR probe machine owned by mpv core behind the binding seam; pure halves (`MpvStallWatchdog`, `HdrOutputPolicy`) stay put. Surface re-attach rides `MpvSurfaceReattachPolicy` (commonMain decision, no-reload arm by construction): the Android binding splits the initial-load epoch from mid-session (never reloads), and the frame-counter fold reads an unreadable counter as unknown, **never zero**. → CONTEXT.md "Engine layer" + "State machines" (MPV surface reattach row).

**Session slice** — one read seam between player UI and playback session: per-region projections (overlay, status, sheets, cast) whose interfaces screens program against — **a screen reading a controller directly, or a VM re-exposing one as pass-through, is a defect** (source-scan pinned). Collection follows slice: hot flows collected in the reading leaf, never screen root. → CONTEXT.md "Playback source resolution".

**Player chrome module** — everything controls chrome knows (visibility, auto-hide timer, summon policy, focus-grab arming, BACK hide-or-exit ladder, visibility→poll-cadence switch) behind one interface at the player-contract chrome seam. **Book reader deliberately OUTSIDE** — its chrome is gate-and-delay only (`AutoHideControlsEffect`, VM-owned visibility, sheet-stack suppression — no summon/BACK/focus/cadence to answer); a future adapter needs new controller vocabulary first, not forced fit (ADR-0012 bar). → CONTEXT.md "Playback session".

**Player screen wiring holders** — the root screen's three wiring clusters as remembered holders over pure policies in player-video `state/` (resume chip `ResumeChipPolicy`, BACK ladder + focus handoff `PlayerBackFocusLadder`, window/lifecycle pack `WindowLifecyclePolicy`) + thin `remember*` wrappers — **a wiring cluster re-accumulating in the root composable is the defect**; the root is composition + collector registration. Policies jvm-tested.

**Exo build plan** — `ExoBuildPlan.of(inputs)`: four ordered build verdicts as pure data — sink shape, ASS session verdict, extractor pairing (co-derived from the same input so they can't disagree), load-control numbers; `load()` folds the plan once. `ExoBuildPlanTest` pins the JVM lane.

**Audio session-effect trio** — engine-agnostic equalizer/dialogue-boost/night-mode attach/apply/release over audio session id (`AudioSessionEffectTrio`): the co-enabling call's ONE choreography home (rule itself folded in `EqualizerHelper`). `AudioEffectChain` composes it for Exo; mpv constructs against `assignAudioSessionId`. VLC stays out (constructor options, different mechanism).

**VLC slave registration holder** — `VlcSlaveRegistration`: generation-guarded external-subtitle registration machine over plain values (attempts budget, injectable `schedulePoll` seam); engine keeps wiring only. Android-hostTest lane.

**Seek-chip commit clock** — seek chip timing policy as pure fold `SeekChipCommitClock`: D-pad linger filter, keyboard-commit verdict with stale-timestamp stand-down, 500/800 ms constants with load-bearing ordering. Screen keeps only collector registration. → CONTEXT.md "Playback session".

**Trickplay feeds** — `rememberTrickplayFeeds` hoists the three collectors' gates + clear/grace effects; gate reads arrive as getter lambdas so `snapshotFlow` tracking is byte-identical — `snapshotFlow` observes ONLY snapshot state (a `StateFlow.value` read registers no dependency); gesture drag StateFlows ride `changePulses`. → CONTEXT.md "Player subtitles & trickplay".

**Playback request plan** — `PlaybackRequestPlan.of(inputs)`: 20-field `PlaybackRequest` build decisions pure over plain projections; `initializeEngine` consumes; `PlaybackRequestPlanTest` pins. → CONTEXT.md "Playback source resolution".

**Session ui mirrors** — `SessionUiMirrors`: the session's derived read/write seams over `PlayerStateHandles` as one holder, single construction site — data, not behavior seam (ADR-0012 bar). → CONTEXT.md "Playback session".

**Input dispatch module** — map resolution + discrete-arm executor behind one interface (`PlayerInputDispatcher` beside `PlayerInputPolicy`); each surface registers its arm sets; live TV D-pad rides the same persisted store (zap preserved as default binding). → CONTEXT.md "Playback session" (input map).

**Restore prompt applier** — `SessionRestorePromptApplier`: the cold-start restore prompt's shell half (evaluate-once Ask/AutoRestore/None ladder, three dialog arms); **the dismissal write rides a scope OUTSIDE the prompt-conditional composition — a scope inside dies with the dialog and the DataStore marker never lands**; desktop preconditions via suspend `gate()` parameter, never a flag table. → CONTEXT.md "Playback session".

**Player focus handoff** — "where focus lands when controls hide or overlay appears": overlay-priority ladder screen-owned (cinema intro > next-episode card > skip-segment prompt) + grab/retry/re-assert machine in `PlatformWindowSeam`'s `PlayerTvFocusGrabEffect`. → CONTEXT.md "Playback session".

## Admin (logs, plugins)

**Log line parse** — log screen severity-highlight decode as plain JVM types (`parseLogLineSpans`/`LogLineRole`/`LogLineSpan`): whole-word severity regex (alternation ordered so `WARN` yields to `WARNING`) + token→role fold beside a parse test; composable only maps role→tint.

**Plugin settle-then-refetch** — plugins' wait-for-server-effect idiom as one `settleThenRefetch(delayMs, refetch)`; delay literals per command (installs 1000 ms, others 500 ms) — no recorded rationale to unify.

**Plugin tab loader** — plugin-tab fetch dance as one `PluginTabLoader<Q, T>`: ONE gate spelling, stale-guard (only the currently-selected fetch may land on BOTH Result arms — **a superseded fetch's failure is as forbidden as its success**), quiet-degrade fold (fresh failure lands `quiet` value). Messages + Sync gate hand-written deliberately. `PluginTabLoaderTest` pins.

## Cross-feature

**Screen-shape declaration** — each settings screen's ordered group list declared once as `PreferenceScreenGroup(idSet, advancedOnly, emittedRows, content)` on `PreferenceScreenScaffold`; the shell derives scroll adjustment, whole-group advanced gating, hidden-row count from it. Row emission bodies hand-written inside each group's one composable slot — **a row-grain slot registry remains rejected**. The search→screen deep-link contract is family-wide: catalog↔declaration totality is pinned for EVERY sub-screen route (a searchable domain in neither the declared map nor the named exemption list is a red test). The contract test's exception arithmetic derives from named row sets beside the declarations — **hand-baked integer subtraction restating an exception set is the defect**; cross-pins pin the object, not a replica; `FusedRowsRatchetTest` pins row catalogs.

**Reset review cross-pin** — every spec-backed preference in a reset category carrying a search face has a `DiffField` row, so Factory-Reset/restore-wizard review totality isn't self-referential; sanctioned omissions ride a pinned-exception set in `PreferenceSpecDiffFieldRatchetTest`. Search-less reset-category rows stay outside the review surface.

**Restore wizard selection target** — `WizardSelectionTarget` names which wizard group a toggle addresses; `RestoreWizardViewModel.toggle` routes the tap; selection math stays in model folds.

**Request action table** — "which actions legal for this request status" as one `requestActionsFor(status, isAdmin)` beside the status-presentation table; list row + detail sheet render the one table. Admin TaskState family same single home (`TaskStatePresentation`), as do requests-side ArrDownloadStatus rows.

**Structural/live state split** — VM state protocol for screens with hot per-row values: structural rows ride the uiState bag, per-key moving values ride keyed side flows, leaves collect per key (downloads `progressById`, requests enrichment maps, home `presenceChips`/`headerShortcuts`). Term answers "which values ride outside the bag"; placement each screen's decision — **re-justifying the split in prose instead of citing the term is the defect shape**. → CONTEXT.md "Downloads & insights".

**Seerr media kind** — `SeerrMediaKind` + `SeerrRequestItem.kind` derived once in core:model beside `effectiveMediaStatus` (ignoreCase — a `"Movie"` payload would otherwise misroute); the parse sites read the model. → CONTEXT.md "Seerr request state".

**Scan grace window** — dashboard's optimistic scan grace-window machine + WS-freshness rule as small tested holders beside the `MediaCleanupScanStateHolder` precedent (`LibraryScanWindow`); dashboard VM delegates.

**Subtitle upload choreography** — editor's provider-subtitle save→encode→upload→reload→attribute sequencing in `EditorSubtitleStore.saveUploadAndAttribute` (one caller; persist/attribute halves there too); VM keeps only busy/error flags + degradation wording.

**User-data announcer** — synthetic-push seam for confirmed writes: `UserDataAnnouncer` (flow + record) a leaf single, so write paths upstream of the repo construction graph (`PlayedStateSyncImpl`, `PlaybackRepositoryImpl`) announce eagerly without `lazy { get<MediaRepository>() }` cycle-breaks. Outbox drainer names it directly for drain-tail fan-out: derived flips ride `recordDrainedPlays` (one batched call).

**Test fixtures home** — ADR-0006 rule 4: `:shared:core:test-fixtures` holds doubles of PUBLIC interfaces; construction graphs over INTERNAL ctors stay jvmTest-local. `MainDispatcherRule` (setMain/resetMain TestWatcher) is the jvmShared fixture for the Main-dispatcher boilerplate — **a jvmTest hand-inlining the setMain dance is the retired defect**. → CONTEXT.md "Core data repositories".

**Koin registration totality** — plain ViewModel registrations ride `viewModelOf(::X)`; hand-written blocks only where wiring genuinely non-obvious (nullable platform seams, function-typed ctor defaults); source-scan guard asserts exactly one registration per ViewModel class.

**Preference spec tier** — preference domains whose keys/reads/restore/reset lists/defaults/search faces derive from `<Domain>PreferenceSpecs` rows; invariant writes stay hand setters beside rows — documented `derived` opt-out per store. Migrating a domain requires a derivation test before reset rewire + plugin-catalog artifact regeneration; **every newly advertised bound needs a visible clamp in the owning store + a row in the generator test's audited-bounds set**. Each `<Domain>SpecDerivationTest` pins key identity.

**Receiver intake fold** — each entry receiver's decode (verb, extras, deep-link intent rebuilt by string) as a pure fold beside one goAsync scope-window idiom; receivers = fold + dispatch, decode JVM-testable without Robolectric. Widget/shortcut/PiP action strings stay pair-local — **a universal twenty-six-verb intent grammar is the rejected god-module shape**.

**Now-playing verdict** — ONE fold over the video-engine/audio-manager fork answering "is anything playing, anywhere"; `RemotePlaybackReporter`, `RemoteControlReceiver`, widget updater, tile read it. **Flag resolution: the `remoteSessionActive` cell lives in the verdict, NOT derived from playing flows** (cast/Play-To session can be active while local engine idles — deriving would gate the local reporter off during plain local playback); the reporter stays single writer via the verdict's internal mark. → CONTEXT.md "Audio playback, effects & cast cores".

**Refresh cycle module** — one module owning a poll loop's whole contract: start/stop, interval-or-backoff policy, visibility gate (a back-stacked screen cannot poll by construction). → CONTEXT.md "Concurrency".

**Plugin capability gate** — probe once while UNKNOWN, re-arm on identity reset, gate on store, behind one interface (`JellyPlayCapabilityGate` + `JellyPlayCapabilityState`); meta-keyed settings-sync family gates through `settingsSyncGateOpen()` (probe AVAILABLE + settings-sync meta key). → CONTEXT.md "JellyPlay companion server plugin".

**Download summary surface** — notification-group summary's in-flight count as one observed flow, single collector through the notification port; call sites read the flow instead of re-issuing COUNT queries. `DownloadSummarySurface` over DAO `getInFlightDownloadCountFlow`.

**TV focus cursor** — everything "where restored focus lands" knows: fallback leaf, one-shot initial grab, refresh-generation re-grab, modifier-order policy — one module TV containers parameterize by layout kind. `TvFocusCursor` in core:ui tv package; Robolectric contract suite pins the grab/re-grab ladder.

**Platform capability seams** — the two device facts (biometric prompt availability, dynamic color support) answered once by `PlatformCapabilities` in core/ui, beside the platform expect/actual family — **a module-local expect/actual twin re-deriving a device fact is the defect** (wizard and settings would disagree about one device). `PlatformCapabilitiesJvmTest` pins the desktop actuals.

**Onboarding wizard flow** — page truth in the VM (`currentPage` StateFlow, `navigateToPage` flush→clamp→set, `onBack`, one exit verb `exitWizard(markComplete)` signaling `WizardExit` on a buffered channel); skip and start-watching are both `exitWizard(true)` — **a second exit choreography or pager-owned page truth is the defect**. `OnboardingViewModelTest` pins the flow headlessly.

**Shortcut action surface** — ShortcutAction execution as one per-shell module beside `ShellHostHooks` (ShellSessionController pattern): `ShortcutActionSurface(focusHome, armSurprise, requestPlayOn, toggleOffline, onUnsupported)` with `supportedActions` deriving from armed arms so **a shell cannot claim support while no-op-ing**; `ShellHomeHooks` carries the surface as a required member (forgetting stops compiling). Desktop arms Surprise + offline toggle for real; Play On stays unsupported and LOUD (snackbar, not silence).

**Overflow shortcut decoration** — the overflow pill's decode + icon/label/badge/highlight/progress presentation as one pure fold `overflowShortcutDecoration(target, isOfflineActive, isGoingOnline, downloadCount)`; action×offline-state matrix pinned by app-lane tests. **A second hand-fold is a defect.**

**Nav primary-surface descriptor** — "may this destination be primary on this surface" on the `NavDestination` registry row: `primarySurfaces: Set<NavPrimarySurface>` (default derived from `topLevelGroups`); `tvPrimaryRoutes`, settings customization fork, desktop rail eligibility all derive; the desktop rail ORDER policy is registry data (`NavDestination.railOrder` → `DESKTOP_RAIL_ROUTES`), pinned both ways. → CONTEXT.md "Navigation destinations".

**Route totality guard** — `RouteSectionTotalityTest` (shell jvmTest) fails naming any `Route` leaf rendering `UnregisteredEntryContentKey`: adding a Route without a section is a red test, not a silent blank. Pinned exceptions: `LiveTvChannelPlayer`, `SubtitleTester` (Android-only, recorded reasons). → CONTEXT.md "Navigation destinations".

**Shell composition arms** — per-shell hook wiring (audio clicks, admin gate, `ShortcutActionSurface`, `shellHost`) as one construction `rememberShellComposition(navigator, arms)` over a `ShellCompositionArms` bundle; wiring shared, arms per-shell (ADR-0001) — adding a hook = one arms member + one wiring line + two arm values, not a factory-wide edit.

**Sync screen presentation folds** — sync screen's near-pure presentation as plain folds beside test (`JellyPlaySyncPresentation`): namespace face table, subtitle gate ORDER as data, `SyncMasterControls` (arms parameterized by copy keys — TV/non-TV subtitle keys deliberately separate).

**Playback speed ladder** — ONE home for playback-speed vocabulary (`PlaybackSpeedLadder` in core:model): range, derived step count (never a hand constant), full rung list, audio-settings subset, `1x` formatting; both sliders/pickers, CYCLE_SPEED cycle, settings rows read it — **a picker offering a rung the slider cannot reach is the defect class this shape makes unrepresentable**.

**I18n tooling home** — `:shared:core:i18n-tools` owns repo string-table tooling as one module: counted-key grammar (one home, two consumers), repo discovery walk, `StringsPropagator` (write side), parity ratchet (read side); `propagateStrings`/`checkStrings` run from root build. Propagated copies land `PENDING TRANSLATION`-marked so ratchet numbers only ever go down.

**Preference stores aggregate** — `UserPreferencesStore` + `PreferencesEditScope` take a grouped `PreferenceStores` value (member names mirror slice names one-to-one) instead of positional re-enumeration; adding a preference family touches three sites (`PreferenceStores` field, `PreferenceSliceSnapshot` row, Koin single) — **a missed aggregate update fails at compile time, not silent positional drift**. → CONTEXT.md "Preference stores".
