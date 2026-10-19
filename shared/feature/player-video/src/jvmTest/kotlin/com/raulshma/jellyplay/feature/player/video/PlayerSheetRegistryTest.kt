package com.raulshma.jellyplay.feature.player.video

import androidx.compose.runtime.saveable.SaverScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * Pins the player sheet registry (the sheet-module pilot wave): every
 * [PlayerSheet] member is registered exactly once, in declaration order, the
 * Saver's restore enumeration rides the registry, and the migrated sheets
 * (wave 1 pilots Hdr, Render; wave 2 SubtitleHub) route through their modules
 * while the rest stay hand-written.
 *
 * Totality technique: [PlayerSheet] is a sealed CLASS, so the home registry's
 * `HomeSectionType.entries` walk has no direct equivalent — and
 * `sealedSubclasses` needs kotlin-reflect, which is off the runtime classpath
 * (it crashed `PlayerSheetKt`'s `<clinit>` on launch). The stand-in is the
 * compile-time exhaustive `when`: [canonicalPosition] (here) and
 * `PlayerSheetRegistry.registrationOf` (production) each have one arm per
 * sealed member, so a new PlayerSheet subtype fails BOTH files' compilation
 * until its arms land — and the position pin below then fails until the
 * registration row lands with them.
 */
class PlayerSheetRegistryTest {

    /**
     * The canonical [PlayerSheet] declaration-order positions. Exhaustive
     * over the sealed class (the compile gate described in the class KDoc).
     */
    private fun canonicalPosition(sheet: PlayerSheet): Int = when (sheet) {
        PlayerSheet.None -> 0
        PlayerSheet.Speed -> 1
        PlayerSheet.Audio -> 2
        PlayerSheet.Chapter -> 3
        PlayerSheet.PlaybackInfo -> 4
        PlayerSheet.AspectRatio -> 5
        PlayerSheet.SubtitleHub -> 6
        PlayerSheet.AVSync -> 7
        PlayerSheet.Decoder -> 8
        PlayerSheet.Episodes -> 9
        PlayerSheet.Queue -> 10
        PlayerSheet.SyncPlay -> 11
        PlayerSheet.Quality -> 12
        PlayerSheet.PlaybackMode -> 13
        PlayerSheet.SleepTimer -> 14
        PlayerSheet.VideoFilter -> 15
        PlayerSheet.Hdr -> 16
        PlayerSheet.Render -> 17
        PlayerSheet.Version -> 18
    }

    // ── Totality ──────────────────────────────────────────────────────────

    @Test
    fun registry_coversEveryMemberExactlyOnce_inCanonicalOrder() {
        // Every sealed member registered, once, in declaration order: the
        // mapped canonical positions must be the full 0..last sequence (the
        // length derives from the registry itself — the exhaustive `when`s on
        // both sides are the compile gate that keeps a new subtype from
        // shipping unregistered). A duplicate registration repeats a position;
        // a missing one leaves a hole; a new subtype fails canonicalPosition's
        // compile gate first.
        assertEquals(PlayerSheetRegistry.all.indices.toList(), PlayerSheetRegistry.all.map { canonicalPosition(it) })
        // Exactly-once, stated directly too (the sequence pin implies it —
        // kept explicit so the intent survives pin updates).
        assertEquals(PlayerSheetRegistry.all.size, PlayerSheetRegistry.all.distinct().size)
    }

    // ── The Saver rides the registry (the former ALL_SHEETS consumer) ─────

    @Test
    fun saver_roundTripsEveryRegisteredSheet_throughItsKey() {
        // The always-yes SaverScope (every key is a plain string — savable).
        // Saver.save is a SaverScope-receiver extension member, hence the
        // with(scope)-shaped invocation.
        val scope = SaverScope { true }
        for (sheet in PlayerSheetRegistry.all) {
            val key = with(PlayerSheetSaver) { scope.save(sheet) }
            assertEquals(sheet, PlayerSheetSaver.restore(requireNotNull(key) { "unsaved: $sheet" }), "key=$key")
        }
    }

    @Test
    fun saver_restoreFallsBackToNone_onUnknownKey() {
        assertEquals(PlayerSheet.None, PlayerSheetSaver.restore("NotASheet"))
    }

    // ── The migrated sheets (the wave-1 pilots + wave 2) ──────────────────

    @Test
    fun registry_migratedSheets_routeThroughTheirModules() {
        assertSame(HdrSheetModule, PlayerSheetRegistry.moduleFor(PlayerSheet.Hdr))
        assertSame(RenderSheetModule, PlayerSheetRegistry.moduleFor(PlayerSheet.Render))
        assertSame(SubtitleHubSheetModule, PlayerSheetRegistry.moduleFor(PlayerSheet.SubtitleHub))
        // The migrated() factory derives the registration's key half from the
        // module — the two cannot drift.
        assertEquals(PlayerSheet.Hdr, HdrSheetModule.sheet)
        assertEquals(PlayerSheet.Render, RenderSheetModule.sheet)
        assertEquals(PlayerSheet.SubtitleHub, SubtitleHubSheetModule.sheet)
    }

    @Test
    fun registry_unmigratedSheets_haveNoModule() {
        assertNull(PlayerSheetRegistry.moduleFor(PlayerSheet.None))
        assertNull(PlayerSheetRegistry.moduleFor(PlayerSheet.Speed))
        assertNull(PlayerSheetRegistry.moduleFor(PlayerSheet.Version))
        // The rest of the registry is the pending carrier: every
        // un-migrated key resolves to null exactly like a raw lookup.
        for (sheet in PlayerSheetRegistry.all) {
            if (sheet != PlayerSheet.Hdr && sheet != PlayerSheet.Render && sheet != PlayerSheet.SubtitleHub) {
                assertNull(PlayerSheetRegistry.moduleFor(sheet), "sheet=$sheet")
            }
        }
    }
}
