package com.raulshma.jellyplay.feature.details

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests the detail title-block decision ([preferLogoTitleEnabled]) behind the
 * "prefer logos" display mode: the logo renders only for an opted-in user with
 * a server clear-logo AND a resolved URL — every miss falls back to the text
 * title exactly as before the feature. Pure decision — no Compose.
 */
class PreferLogoTitleFallbackTest {

    @Test
    fun `preferLogos with logo tag and url renders the logo`() {
        assertTrue(preferLogoTitleEnabled(preferLogos = true, logoTag = "logo-tag-1", logoUrl = "https://s/logo400"))
    }

    @Test
    fun `preferLogos off keeps the text title even with a logo`() {
        assertFalse(preferLogoTitleEnabled(preferLogos = false, logoTag = "logo-tag-1", logoUrl = "https://s/logo400"))
    }

    @Test
    fun `missing logo tag keeps the text path unaffected`() {
        assertFalse(preferLogoTitleEnabled(preferLogos = true, logoTag = null, logoUrl = ""))
        assertFalse(preferLogoTitleEnabled(preferLogos = true, logoTag = null, logoUrl = "https://s/logo400"))
    }

    @Test
    fun `unresolved or blank logo url keeps the text title`() {
        assertFalse(preferLogoTitleEnabled(preferLogos = true, logoTag = "logo-tag-1", logoUrl = ""))
        assertFalse(preferLogoTitleEnabled(preferLogos = true, logoTag = "logo-tag-1", logoUrl = " "))
    }
}
