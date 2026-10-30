package com.raulshma.jellyplay.feature.player.video

/**
 * The player host's exit state, written by the app shell's activity so the
 * player VM's teardown can tell an in-app close from a HOST exit
 * ([VideoPlayerViewModel.isAppExiting] — the cold-start restore prompt's
 * handled-offer marker is persisted for the former and skipped for the
 * latter; an interrupted session must prompt on the next launch).
 *
 * Contract: the activity writes [isHostFinishing] in `onDestroy` — BEFORE
 * `super.onDestroy()`, whose ViewModelStore clear is what runs the player
 * VM's `onCleared` teardown — as
 * `isFinishing && !isChangingConfigurations` (a configuration rotation also
 * destroys the activity but is not an exit). It resets the flag in
 * `onCreate`, so a process that survives an exit cannot leak the stale TRUE
 * into the next session. `@Volatile`: written on the main thread's destroy
 * path, read on the teardown path.
 */
object PlayerHostExitState {
    @Volatile
    var isHostFinishing: Boolean = false
}
