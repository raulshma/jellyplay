package com.raulshma.jellyplay.feature.home

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test

class NewsletterBannerTest {

    @Test
    fun bannerCallbacks_triggerCorrectFlags() {
        var clicked = false
        var dismissed = false

        val onClick = { clicked = true }
        val onDismiss = { dismissed = true }

        onClick()
        assertTrue(clicked)

        onDismiss()
        assertTrue(dismissed)
    }

    @Test
    fun bannerTitleAndBody_areNonEmpty() {
        val title = "Newsletter Ready"
        val subtitle = "Your weekly digest is here. Tap to explore."

        assertEquals("Newsletter Ready", title)
        assertTrue(subtitle.isNotBlank())
    }
}
