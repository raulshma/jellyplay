package com.raulshma.jellyplay.core.model

import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionCredentialsTest {

    @Test
    fun `deviceNameFor formats model and user name`() {
        val model = Build.MODEL.orEmpty().ifBlank { "Android" }
        assertEquals("JellyPlay on $model (Alice)", ConnectionCredentials.deviceNameFor("Alice"))
    }

    @Test
    fun `deviceNameFor omits parens for blank user name`() {
        val name = ConnectionCredentials.deviceNameFor("   ")
        assertFalse(name.contains("("))
        assertTrue(name.startsWith("JellyPlay on "))
    }

    @Test
    fun `deviceNameFor caps user name at 20 chars and total at 60`() {
        val name = ConnectionCredentials.deviceNameFor("a".repeat(100))
        assertTrue(name.length <= 60)
        // The parenthesised user portion carries at most 20 chars plus "()".
        val inParens = name.substringAfterLast("(", "").dropLast(1)
        assertTrue(inParens.length <= 20)
    }
}
