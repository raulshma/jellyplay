package com.raulshma.jellyplay.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the invariants of [MediaItemStub.sortSizeBytes], the size-text parser used
 * to sort media-cleanup scan results by true byte size:
 *  - A size string is "[number][optional-whitespace][UNIT]" with units B/KB/MB/GB/TB
 *    (UPPERCASE ONLY — the regex is case-sensitive) and BINARY (1024-based) multipliers.
 *  - The number may be fractional ("1.5 GB") but must start with a digit.
 *  - The text may embed the size in longer prose; the FIRST match wins.
 *  - Anything unparseable — empty text, no unit suffix at all, lowercase units — is 0.
 */
class MediaItemStubSizeTest {

    private fun stub(sizeText: String) = MediaItemStub(sizeText = sizeText)

    @Test
    fun everyUnitSuffix_convertsThroughBinaryMultiples() {
        assertEquals(123L, stub("123 B").sortSizeBytes)
        assertEquals(1_536L, stub("1.5 KB").sortSizeBytes) // 1.5 * 1024
        assertEquals(524_288_000L, stub("500 MB").sortSizeBytes) // 500 * 1024^2
        assertEquals(1_610_612_736L, stub("1.5 GB").sortSizeBytes) // 1.5 * 1024^3
        assertEquals(2_199_023_255_552L, stub("2 TB").sortSizeBytes) // 2 * 1024^4
    }

    @Test
    fun plainNumberWithByteSuffix_isUsedVerbatim() {
        assertEquals(512L, stub("512 B").sortSizeBytes)
        assertEquals(0L, stub("0 B").sortSizeBytes)
    }

    @Test
    fun bareNumberWithoutUnitSuffix_doesNotMatch() {
        // The regex REQUIRES a unit group; a unitless number is not a size mention.
        assertEquals(0L, stub("1024").sortSizeBytes)
    }

    @Test
    fun noMatch_yieldsZero() {
        assertEquals(0L, stub("").sortSizeBytes)
        assertEquals(0L, stub("unknown").sortSizeBytes)
        assertEquals(0L, stub("GB").sortSizeBytes) // unit without a number
        assertEquals(0L, stub("1.5 gb").sortSizeBytes) // lowercase units are not matched
    }

    @Test
    fun sizeEmbeddedInProse_isFound() {
        assertEquals(5_046_586_572L, stub("Size: 4.7 GB").sortSizeBytes)
    }

    @Test
    fun whitespaceBetweenNumberAndUnit_isOptionalOrRepeated() {
        assertEquals(1_610_612_736L, stub("1.5GB").sortSizeBytes) // no space
        assertEquals(2_147_483_648L, stub("2  GB").sortSizeBytes) // multiple spaces
    }

    @Test
    fun firstMentionWins_whenTextContainsSeveralSizes() {
        assertEquals(1_610_612_736L, stub("1.5 GB (2 TB on disk)").sortSizeBytes)
    }

    @Test
    fun fractionalBytesTruncateTowardZero() {
        assertEquals(512L, stub("0.5 KB").sortSizeBytes)
        assertEquals(10_240L, stub("10 KB").sortSizeBytes)
    }

    @Test
    fun defaultStubWithoutSizeText_isZero() {
        assertEquals(0L, MediaItemStub().sortSizeBytes)
    }
}
