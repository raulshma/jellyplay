package com.raulshma.jellyplay.core.data.playback

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.AudioDeviceInfoBuilder
import org.robolectric.util.ReflectionHelpers

/**
 * The [HeadsetAudioDeviceWatcher] chassis over Robolectric's shadow
 * AudioManager, plus its integration with the resume decision it feeds.
 *
 * The type-set classification is pinned JVM-side
 * (`HeadsetAudioDeviceWatcherTypeTest`); what needs a device host is the
 * dispatch path: a registered callback actually receives the shadow's
 * device-added events, the headphone-class filter (and only it) reaches the
 * consumer, release detaches, and the connect edge drives
 * [HeadsetResumePolicy] through the SAME one-shot decision body the wired
 * `ACTION_HEADSET_PLUG` broadcast uses — the first edge consumes the pause
 * marker, the second is a no-op.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HeadsetAudioDeviceWatcherTest {

    private lateinit var context: Context
    private lateinit var audioManager: AudioManager

    /** `AudioPort.ROLE_SINK` — a hidden constant on a non-SDK class, mirrored by value. */
    private val roleSink = 2

    @BeforeTest
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    private fun outputDevice(type: Int): AudioDeviceInfo =
        // The builder builds the device over a fresh AudioDevicePort whose
        // role stays ROLE_NONE (0) — framework `isSink()` reads
        // `mPort.mRole == ROLE_SINK`, so every built device would fail the
        // watcher's sink filter. Set the port's role to sink (the hidden
        // AudioPort.ROLE_SINK = 2, mirrored by value — the class is not in
        // the public SDK jar).
        AudioDeviceInfoBuilder.newBuilder().setType(type).build().also { info ->
            val port = ReflectionHelpers.getField<Any>(info, "mPort")
            ReflectionHelpers.setField(port, "mRole", roleSink)
        }

    private fun addOutput(type: Int) {
        shadowOf(audioManager).addOutputDevice(outputDevice(type), /* notifyAudioDeviceCallbacks = */ true)
    }

    @Test
    fun `a bluetooth reconnect fires the connect edge`() {
        var connected = 0
        val watcher = HeadsetAudioDeviceWatcher(context) { connected++ }
        watcher.register()

        addOutput(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)

        assertEquals(1, connected, "a BT A2DP device arriving must reach the consumer")
        watcher.release()
    }

    @Test
    fun `every headphone-class route fires and non-headphone routes do not`() {
        var connected = 0
        val watcher = HeadsetAudioDeviceWatcher(context) { connected++ }
        watcher.register()

        addOutput(AudioDeviceInfo.TYPE_USB_HEADSET)
        assertEquals(1, connected)
        addOutput(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)
        assertEquals(1, connected, "a speaker arriving must not read as headphones")
        addOutput(AudioDeviceInfo.TYPE_HDMI)
        assertEquals(1, connected, "HDMI is not a headphone route")
        watcher.release()
    }

    @Test
    fun `release detaches the watcher`() {
        var connected = 0
        val watcher = HeadsetAudioDeviceWatcher(context) { connected++ }
        watcher.register()
        watcher.release()

        addOutput(AudioDeviceInfo.TYPE_WIRED_HEADSET)

        assertEquals(0, connected, "a released watcher must not report connects")
    }

    @Test
    fun `register after release re-arms the watcher`() {
        var connected = 0
        val watcher = HeadsetAudioDeviceWatcher(context) { connected++ }
        watcher.register()
        watcher.release()
        watcher.register()

        addOutput(AudioDeviceInfo.TYPE_WIRED_HEADPHONES)

        assertEquals(1, connected, "a re-registered watcher must receive events again")
        watcher.release()
    }

    @Test
    fun `the connect edge feeds the resume policy through the one-shot decision`() {
        // The production body's shape (AndroidVideoPlayerBecomingNoisy's
        // shared onHeadsetConnected): both connect edges call the SAME
        // decision, and a fired resume consumes the pause marker.
        var noisyPauseAtMs: Long? = null
        var resumes = 0
        fun onConnectEdge(prefEnabled: Boolean) {
            val shouldResume = HeadsetResumePolicy.shouldResumeOnHeadsetPlug(
                nowMs = SystemClock.elapsedRealtime(),
                noisyPauseAtMs = noisyPauseAtMs,
                prefEnabled = prefEnabled,
            )
            if (shouldResume) {
                noisyPauseAtMs = null
                resumes++
            }
        }
        val watcher = HeadsetAudioDeviceWatcher(context) { onConnectEdge(prefEnabled = true) }
        watcher.register()
        noisyPauseAtMs = SystemClock.elapsedRealtime() // the becoming-noisy pause just marked

        addOutput(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
        assertEquals(1, resumes, "the BT reconnect must consume the marker and resume")
        addOutput(AudioDeviceInfo.TYPE_WIRED_HEADSET)
        assertEquals(1, resumes, "the second connect edge finds no marker — no double resume")
        watcher.release()
    }

    @Test
    fun `the connect edge respects the opt-in pref`() {
        var noisyPauseAtMs: Long? = SystemClock.elapsedRealtime()
        var resumes = 0
        val watcher = HeadsetAudioDeviceWatcher(context) {
            if (HeadsetResumePolicy.shouldResumeOnHeadsetPlug(
                    nowMs = SystemClock.elapsedRealtime(),
                    noisyPauseAtMs = noisyPauseAtMs,
                    prefEnabled = false, // the opt-in is off
                )
            ) {
                noisyPauseAtMs = null
                resumes++
            }
        }
        watcher.register()

        addOutput(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)

        assertEquals(0, resumes, "no resume without the opt-in pref")
        watcher.release()
    }

    @Test
    fun `a stale marker past the freshness window does not resume`() {
        var noisyPauseAtMs: Long? = SystemClock.elapsedRealtime() - HeadsetResumePolicy.RESUME_WINDOW_MS - 1L
        var resumes = 0
        val watcher = HeadsetAudioDeviceWatcher(context) {
            if (HeadsetResumePolicy.shouldResumeOnHeadsetPlug(
                    nowMs = SystemClock.elapsedRealtime(),
                    noisyPauseAtMs = noisyPauseAtMs,
                    prefEnabled = true,
                )
            ) {
                noisyPauseAtMs = null
                resumes++
            }
        }
        watcher.register()

        addOutput(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)

        assertEquals(0, resumes, "the reconnect is a new listening decision, not the interrupted one")
        watcher.release()
    }

    @Test
    fun `register consumes the sticky replay without firing the consumer`() {
        // The documented register-time dispatch of already-connected devices:
        // a fresh registration has no pause marker, so the replay must be
        // inert — pin it at the chassis level by asserting the consumer only
        // sees genuinely-arriving headphone devices (the initial device list
        // is the shadow's default output, not a connect edge).
        var connected = 0
        val watcher = HeadsetAudioDeviceWatcher(context) { connected++ }
        watcher.register()
        assertEquals(0, connected, "registration itself must not read as a headset arriving")
        assertFalse(HeadsetAudioDeviceWatcher.isHeadphoneDeviceType(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER))
        watcher.release()
    }
}
