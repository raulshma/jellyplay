package com.raulshma.jellyplay.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.work.Configuration
import androidx.work.WorkManager
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Pins the widget refresh scheduling contract against a REAL WorkManager
 * (Robolectric-initialized).
 *
 * CURRENTLY @Ignore'd — two independent environment blockers, NOT test bugs:
 *
 *  1. `WorkManager.initialize(...)` under Robolectric dies with
 *     Resources$NotFoundException on an app-package bool resource — the :app
 *     UNIT-test merged resource table is incomplete under AGP 9 + KMP
 *     compose-resources (the same packaging gap settings.gradle.kts documents
 *     for APK assets; FloatingPlayerService.onCreate's getString hit the same
 *     wall, worked around there by skipping onCreate). No work-runtime test
 *     artifact (work-testing) is available to bypass the resource read.
 *  2. Static-mocking WorkManager's Kotlin companion (`getInstance` moved off
 *     @JvmStatic in work 2.10) is not viable under mockk + Robolectric: the
 *     recording block's auto-hinter re-enters the original
 *     `WorkManagerImpl.getInstance` with a placeholder Context whose
 *     `getApplicationContext` is abstract → AbstractMethodError.
 *
 * Re-enable once the app unit-test resource table is fixed (or work-testing
 * lands on the test classpath). The suite keeps real-WorkManager assertions
 * (unique-work existence instead of mock call counts) so it is honest the
 * moment it runs:
 *
 *  - the periodic schedule is skipped entirely when NO widget of either kind
 *    is bound (no WorkManager writes on cold start of widget-less devices);
 *    with a bound widget only that kind's unique periodic work is enqueued,
 *    under KEEP so the 6 h cadence is never reset by re-enqueueing.
 *  - the manual one-shot refresh enqueues REPLACE one-time work per widget
 *    kind and is throttled by a 5 s in-process cooldown — a rejected request
 *    returns false without touching WorkManager; the two kinds' cooldowns
 *    are independent.
 */
@Ignore("Blocked by the app unit-test resource-table gap — see class KDoc")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class WidgetWorkSchedulerTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private lateinit var workManager: WorkManager

    @Before
    fun setUp() {
        WorkManager.initialize(context, Configuration.Builder().build())
        workManager = WorkManager.getInstance(context)
    }

    @After
    fun tearDown() {
        // Robolectric builds a fresh Application per test; no global reset needed.
    }

    private fun bindWidget(id: Int, provider: Class<*>) {
        Shadows.shadowOf(AppWidgetManager.getInstance(context))
            .bindAppWidgetId(id, ComponentName(context, provider))
    }

    private fun infosFor(uniqueName: String) =
        workManager.getWorkInfosForUniqueWork(uniqueName).get()

    // ── periodic schedule ──────────────────────────────────────────────────

    @Test
    fun `enqueuePeriodic with no bound widget touches no work`() {
        WidgetWorkSchedulerImpl(context).enqueuePeriodic()

        assertTrue(infosFor(LibraryRecommendationsWidgetWorker.UNIQUE_PERIODIC_NAME).isEmpty())
        assertTrue(infosFor(SeerrRecommendationsWidgetWorker.UNIQUE_PERIODIC_NAME).isEmpty())
    }

    @Test
    fun `a bound library widget schedules only the library periodic work`() {
        bindWidget(11, LibraryRecommendationsWidget::class.java)

        WidgetWorkSchedulerImpl(context).enqueuePeriodic()

        assertEquals(1, infosFor(LibraryRecommendationsWidgetWorker.UNIQUE_PERIODIC_NAME).size)
        assertTrue(infosFor(SeerrRecommendationsWidgetWorker.UNIQUE_PERIODIC_NAME).isEmpty())
    }

    @Test
    fun `a bound seerr widget schedules only the seerr periodic work`() {
        bindWidget(12, SeerrRecommendationsWidget::class.java)

        WidgetWorkSchedulerImpl(context).enqueuePeriodic()

        assertEquals(1, infosFor(SeerrRecommendationsWidgetWorker.UNIQUE_PERIODIC_NAME).size)
        assertTrue(infosFor(LibraryRecommendationsWidgetWorker.UNIQUE_PERIODIC_NAME).isEmpty())
    }

    // ── manual one-shot refresh + cooldown ─────────────────────────────────

    @Test
    fun `refreshLibraryNow enqueues a tagged one-shot and reports acceptance`() = runTest {
        val accepted = WidgetWorkSchedulerImpl(context).refreshLibraryNow()

        assertTrue(accepted)
        val infos = infosFor(LibraryRecommendationsWidgetWorker.UNIQUE_ONESHOT_NAME)
        assertEquals(1, infos.size)
        assertTrue(infos.single().tags.contains(LibraryRecommendationsWidgetWorker.WORK_TAG))
    }

    @Test
    fun `a second library refresh inside the cooldown is suppressed`() = runTest {
        val scheduler = WidgetWorkSchedulerImpl(context)
        assertTrue(scheduler.refreshLibraryNow())

        assertFalse(scheduler.refreshLibraryNow())

        assertEquals(1, infosFor(LibraryRecommendationsWidgetWorker.UNIQUE_ONESHOT_NAME).size)
    }

    @Test
    fun `the two widget kinds have independent cooldowns`() = runTest {
        val scheduler = WidgetWorkSchedulerImpl(context)
        assertTrue(scheduler.refreshLibraryNow())

        assertTrue(scheduler.refreshSeerrNow())

        assertEquals(1, infosFor(LibraryRecommendationsWidgetWorker.UNIQUE_ONESHOT_NAME).size)
        assertEquals(1, infosFor(SeerrRecommendationsWidgetWorker.UNIQUE_ONESHOT_NAME).size)
    }
}
