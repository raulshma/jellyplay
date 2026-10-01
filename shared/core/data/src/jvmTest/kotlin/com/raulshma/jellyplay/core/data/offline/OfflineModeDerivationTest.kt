package com.raulshma.jellyplay.core.data.offline

import com.raulshma.jellyplay.core.model.OfflineMode
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Truth-table pin for [OfflineModeDerivation] — the I2 fold of the platform
 * offline-mode ladders. Both platform managers are thin constructors over
 * [OfflineModeManagerBody] with these folds, so every row here holds on BOTH
 * platforms; the single policy delta is the `allowAuto` axis:
 *
 *  - `allowAuto = true` (Android): the full ladder — auto pref + lost
 *    network engages OFFLINE_AUTO, network restoration clears it;
 *  - `allowAuto = false` (desktop): the manual pref is the ONLY input — the
 *    network (collector) and the probe (re-derive) never engage or clear
 *    anything beyond the manual pref. The rows below pin that the dropped
 *    `autoOfflineEnabled` arm of the old desktop twins is this parameter,
 *    not silent drift.
 *
 * Stale-mode rows are pinned too: a current OFFLINE_MANUAL below a
 * manual-off snapshot clears to ONLINE before the network arms are read
 * (both ladders), and OFFLINE_AUTO is sticky while the network stays lost.
 */
class OfflineModeDerivationTest {

    // ── fromCollector: the store × network ladder ───────────────────────

    @Test
    fun `manual pref wins outright on both platforms`() {
        for (allowAuto in listOf(true, false)) {
            for (offline in listOf(true, false)) {
                for (auto in listOf(true, false)) {
                    assertEquals(
                        OfflineMode.OFFLINE_MANUAL,
                        OfflineModeDerivation.fromCollector(
                            manual = true, auto = auto, offline = offline,
                            current = OfflineMode.ONLINE, allowAuto = allowAuto,
                        ),
                        "allowAuto=$allowAuto offline=$offline auto=$auto",
                    )
                }
            }
        }
    }

    @Test
    fun `a stale OFFLINE_MANUAL clears before the network arms are read`() {
        // The pref flipped off between emissions: the mode must release the
        // manual override before the network decides — on both platforms,
        // offline or not, auto or not.
        for (allowAuto in listOf(true, false)) {
            for (offline in listOf(true, false)) {
                assertEquals(
                    OfflineMode.ONLINE,
                    OfflineModeDerivation.fromCollector(
                        manual = false, auto = false, offline = offline,
                        current = OfflineMode.OFFLINE_MANUAL, allowAuto = allowAuto,
                    ),
                    "allowAuto=$allowAuto offline=$offline",
                )
            }
        }
    }

    @Test
    fun `auto plus lost network engages OFFLINE_AUTO only when allowAuto`() {
        assertEquals(
            OfflineMode.OFFLINE_AUTO,
            OfflineModeDerivation.fromCollector(
                manual = false, auto = true, offline = true,
                current = OfflineMode.ONLINE, allowAuto = true,
            ),
        )
        // The desktop row: same inputs, mode held — never auto-engages.
        assertEquals(
            OfflineMode.ONLINE,
            OfflineModeDerivation.fromCollector(
                manual = false, auto = true, offline = true,
                current = OfflineMode.ONLINE, allowAuto = false,
            ),
        )
    }

    @Test
    fun `lost network without the auto pref converges to ONLINE on both platforms`() {
        for (allowAuto in listOf(true, false)) {
            assertEquals(
                OfflineMode.ONLINE,
                OfflineModeDerivation.fromCollector(
                    manual = false, auto = false, offline = true,
                    current = OfflineMode.ONLINE, allowAuto = allowAuto,
                ),
                "allowAuto=$allowAuto",
            )
        }
    }

    @Test
    fun `network restoration clears OFFLINE_AUTO when allowAuto`() {
        assertEquals(
            OfflineMode.ONLINE,
            OfflineModeDerivation.fromCollector(
                manual = false, auto = true, offline = false,
                current = OfflineMode.OFFLINE_AUTO, allowAuto = true,
            ),
        )
    }

    @Test
    fun `an online network leaves ONLINE untouched on both platforms`() {
        for (allowAuto in listOf(true, false)) {
            for (auto in listOf(true, false)) {
                assertEquals(
                    OfflineMode.ONLINE,
                    OfflineModeDerivation.fromCollector(
                        manual = false, auto = auto, offline = false,
                        current = OfflineMode.ONLINE, allowAuto = allowAuto,
                    ),
                    "allowAuto=$allowAuto auto=$auto",
                )
            }
        }
    }

    // ── fromProbe: the checkNetworkAndAutoDetect ladder ─────────────────

    @Test
    fun `probe manual pref wins outright on both platforms`() {
        for (allowAuto in listOf(true, false)) {
            for (reachable in listOf(true, false)) {
                assertEquals(
                    OfflineMode.OFFLINE_MANUAL,
                    OfflineModeDerivation.fromProbe(
                        manual = true, reachable = reachable, auto = false,
                        current = OfflineMode.ONLINE, allowAuto = allowAuto,
                    ),
                    "allowAuto=$allowAuto reachable=$reachable",
                )
            }
        }
    }

    @Test
    fun `probe unreachable engages OFFLINE_AUTO only from ONLINE when allowAuto`() {
        assertEquals(
            OfflineMode.OFFLINE_AUTO,
            OfflineModeDerivation.fromProbe(
                manual = false, reachable = false, auto = true,
                current = OfflineMode.ONLINE, allowAuto = true,
            ),
        )
        // Engaged AUTO is sticky while the network stays unreachable.
        assertEquals(
            OfflineMode.OFFLINE_AUTO,
            OfflineModeDerivation.fromProbe(
                manual = false, reachable = false, auto = true,
                current = OfflineMode.OFFLINE_AUTO, allowAuto = true,
            ),
        )
        // The desktop row: no auto path at all — the probe is not consulted.
        assertEquals(
            OfflineMode.ONLINE,
            OfflineModeDerivation.fromProbe(
                manual = false, reachable = false, auto = true,
                current = OfflineMode.ONLINE, allowAuto = false,
            ),
        )
    }

    @Test
    fun `probe reachable clears OFFLINE_AUTO when allowAuto and leaves ONLINE`() {
        assertEquals(
            OfflineMode.ONLINE,
            OfflineModeDerivation.fromProbe(
                manual = false, reachable = true, auto = true,
                current = OfflineMode.OFFLINE_AUTO, allowAuto = true,
            ),
        )
        assertEquals(
            OfflineMode.ONLINE,
            OfflineModeDerivation.fromProbe(
                manual = false, reachable = true, auto = true,
                current = OfflineMode.ONLINE, allowAuto = true,
            ),
        )
    }

    @Test
    fun `probe on desktop clears a stale OFFLINE_MANUAL and never engages AUTO`() {
        // The desktop re-derive's manual-only ladder, verbatim: stale MANUAL
        // clears (network status must not matter), and the auto pref alone
        // can never move ONLINE.
        assertEquals(
            OfflineMode.ONLINE,
            OfflineModeDerivation.fromProbe(
                manual = false, reachable = true, auto = true,
                current = OfflineMode.OFFLINE_MANUAL, allowAuto = false,
            ),
        )
        assertEquals(
            OfflineMode.ONLINE,
            OfflineModeDerivation.fromProbe(
                manual = false, reachable = false, auto = true,
                current = OfflineMode.ONLINE, allowAuto = false,
            ),
        )
    }
}
