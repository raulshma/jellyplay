package com.raulshma.jellyplay.core.ui.di

import com.raulshma.jellyplay.core.ui.message.UserMessageBus
import org.koin.dsl.koinApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * Pins the Koin wiring of the shared user-message stack: the module provides
 * [UserMessageBus] as a SINGLE — two resolution calls from the same Koin
 * container hand back the same instance (the root host collects this bus, and
 * migrated ViewModels receive the same one via `get()`), while the
 * no-argument constructor keeps the class constructible for tests and
 * previews (fresh buses start empty by contract of UserMessageBus itself).
 */
class CoreUiMessageModuleTest {

    @Test
    fun module_providesUserMessageBusAsSingleton() {
        val app = koinApplication { modules(coreUiMessageModule) }

        val first = app.koin.get<UserMessageBus>()
        val second = app.koin.get<UserMessageBus>()

        assertSame(first, second, "the bus must be container-scoped, not per-resolution")
        app.close()
    }

    @Test
    fun module_isStandalone_noOtherDependenciesRequired() {
        // The module must resolve without any other module on the classpath —
        // it keeps the star topology intact by depending on nothing.
        val app = koinApplication { modules(coreUiMessageModule) }

        assertEquals(app.koin.get<UserMessageBus>(), app.koin.get<UserMessageBus>())
        app.close()
    }

    @Test
    fun userMessageBus_isConstructibleWithoutKoin() {
        // Tests/previews construct the bus directly; emitting into a fresh
        // (unsubscribed) bus must not demand container context or suspend —
        // the buffered channel accepts the message (its delivery semantics
        // are pinned by UserMessageBusTest).
        val bus = UserMessageBus()

        bus.info("hello")
        bus.error("boom")
    }
}
