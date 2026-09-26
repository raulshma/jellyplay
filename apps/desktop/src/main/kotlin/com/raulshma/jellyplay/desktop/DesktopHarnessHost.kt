package com.raulshma.jellyplay.desktop

import androidx.compose.runtime.Composable
import androidx.compose.ui.awt.ComposeWindow
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.desktop.harness.DesktopFlowHarness
import com.raulshma.jellyplay.desktop.harness.DesktopFlowHarnessHost
import com.raulshma.jellyplay.desktop.harness.DesktopNativeDialogHarness
import com.raulshma.jellyplay.desktop.harness.DesktopNativeDialogHarnessHost
import com.raulshma.jellyplay.desktop.harness.DesktopSessionHarness
import com.raulshma.jellyplay.desktop.harness.DesktopSessionHarnessHost
import java.util.concurrent.atomic.AtomicReference
import org.koin.compose.koinInject

/**
 * The E2E harness arming point DesktopAppRoot hosts ONCE per composition
 * (containment: the three `requested()` blocks the root used to inline, one
 * per lane). Each lane composes NOTHING unless its `jellyplay.*.enabled`
 * system property is `true` — on a normal boot every branch short-circuits
 * before its `koinInject` runs, so the harness surface costs three boolean
 * property reads.
 *
 * The host lives in DesktopAppRoot (not DesktopNavScaffold) because the
 * session/flows harnesses perform their own login and must keep running
 * across the sign-in → scaffold composition swap; see each lane's KDoc in
 * the `harness` subpackage for what an armed run proves.
 */
@Composable
internal fun DesktopHarnessHost(
    authRepository: AuthRepository,
    windowRef: AtomicReference<ComposeWindow?>?,
) {
    //  real-server E2E session harness (DesktopSessionHarness KDoc):
    // the host is a bare LaunchedEffect inside the lane's own host composable.
    if (DesktopSessionHarness.requested()) {
        val engineRecorder: com.raulshma.jellyplay.desktop.player.EngineActivityRecorder = koinInject()
        DesktopSessionHarnessHost(
            authRepository = authRepository,
            windowRef = windowRef,
            engineRecorder = engineRecorder,
        )
    }

    // Native-dialog harness (DesktopNativeDialogHarness KDoc): a no-op gate
    // for the AWT FileDialog flows. Server-free by design (the settings
    // backup round trip is local-prefs-only), so it needs no login and no
    // fixture.
    if (DesktopNativeDialogHarness.requested()) {
        DesktopNativeDialogHarnessHost()
    }

    // Flows harness (DesktopFlowHarness KDoc): the lane for the four
    // native-dialog flows (editor image/subtitle pickers, heatmap share,
    // player subtitle upload) — needs the real fixture + libmpv; the
    // click-reach bridge (HarnessClickBridge) is armed by Main.kt under the
    // same property, before any screen composes.
    if (DesktopFlowHarness.requested()) {
        val editorRepository: com.raulshma.jellyplay.core.data.repository.MetadataEditorRepository = koinInject()
        val engineRecorder: com.raulshma.jellyplay.desktop.player.EngineActivityRecorder = koinInject()
        DesktopFlowHarnessHost(
            authRepository = authRepository,
            editorRepository = editorRepository,
            engineRecorder = engineRecorder,
            windowRef = windowRef,
        )
    }
}
