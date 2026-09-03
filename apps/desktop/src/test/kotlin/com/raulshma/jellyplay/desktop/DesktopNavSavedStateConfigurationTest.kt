package com.raulshma.jellyplay.desktop

import androidx.navigation3.runtime.NavKey
import com.raulshma.jellyplay.core.ui.navigation.Route
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule

/**
 * The desktop nav saved-state configuration ([desktopNavSavedStateConfiguration],
 * wave 11B): every desktop NavDisplay serializes its back stack through the
 * ONE serializersModule built here, which registers the ENTIRE sealed Route
 * hierarchy as NavKey-polymorphic serializers (kotlin-reflect enumeration, so
 * new Route leaves self-register).
 *
 * Invariants pinned:
 *  - A representative OBJECT leaf (Route.ServerList — the signed-out seed),
 *    another OBJECT leaf registered by a later conveyor wave (Route.Settings),
 *    and the ANDROID-only dead-end leaf (Route.SubtitleTester — pushed from
 *    settings, deliberately unregistered in the entryProvider) all resolve a
 *    NavKey polymorphic serializer: registration covers leaves regardless of
 *    whether a desktop entry exists.
 *  - A representative DATA-CLASS leaf (Route.VideoPlayer — the wave-9A
 *    playback route) resolves a serializer that actually ROUND-TRIPS an
 *    instance through JSON — presence alone is not enough, a broken
 *    registration crashes the first saved-state write/read in a real session.
 *  - An unregistered NavKey subclass resolves to null — the module must stay
 *    Route-scoped (a catch-all would silently serialize foreign keys).
 *
 * This is the machine-checked half of the KDoc promise "new Route subclasses
 * register themselves": if the reflective enumeration or the polymorphic
 * wiring breaks, saved state dies with SerializationException at runtime —
 * these tests die here first instead.
 */
class DesktopNavSavedStateConfigurationTest {

    /** The module under test, exactly as both desktop NavDisplays receive it. */
    private val module: SerializersModule = desktopNavSavedStateConfiguration().serializersModule

    @Suppress("UNCHECKED_CAST")
    private fun resolve(route: NavKey): KSerializer<NavKey>? =
        module.getPolymorphic(NavKey::class, route) as KSerializer<NavKey>?

    @Test
    fun `object route leaves resolve a polymorphic NavKey serializer`() {
        val leaves = listOf<NavKey>(
            Route.ServerList, // signed-out host seed
            Route.Home, // the start tab
            Route.Settings, // conveyor drill-ins push from here
            Route.SubtitleTester, // android-only leaf, dead-end-guarded on desktop
        )
        leaves.forEach { route ->
            assertNotNull(resolve(route), "${route::class.simpleName} must be registered as a NavKey polymorphic subclass")
        }
    }

    @Test
    fun `data class route leaf round-trips through its resolved serializer`() {
        val route: NavKey = Route.VideoPlayer(
            itemId = "item-1",
            startPositionTicks = 123_456_789L,
        )
        val serializer = assertNotNull(
            resolve(route),
            "Route.VideoPlayer must be registered (the session harness pushes it programmatically)",
        )
        val json = Json.encodeToString(serializer, route)
        val decoded = Json.decodeFromString(serializer, json)
        assertEquals(route, decoded, "saved-state round trip must preserve the route")
    }

    @Test
    fun `non-Route NavKey subclasses stay unregistered`() {
        class ForeignNavKey : NavKey
        assertNull(
            module.getPolymorphic(NavKey::class, ForeignNavKey() as NavKey),
            "the module must register the sealed Route hierarchy only — a foreign NavKey must not resolve",
        )
    }

    @Test
    fun `resolved serializers reject malformed payloads instead of inventing routes`() {
        val serializer = assertNotNull(resolve(Route.Home))
        assertFailsWith<SerializationException> {
            Json.decodeFromString(serializer, "not-a-route")
        }
    }
}
