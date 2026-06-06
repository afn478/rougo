package com.selxo.rougo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SubtitleCacheTest {
    @Test
    fun subtitleCacheKeyChangesWhenFileMetadataChanges() {
        val first = subtitleParseCacheKey("file:///subs/test.vtt", length = 100L, lastModified = 1_000L)
        val same = subtitleParseCacheKey("file:///subs/test.vtt", length = 100L, lastModified = 1_000L)
        val changedLength = subtitleParseCacheKey("file:///subs/test.vtt", length = 101L, lastModified = 1_000L)
        val changedModified = subtitleParseCacheKey("file:///subs/test.vtt", length = 100L, lastModified = 1_001L)

        assertEquals(first, same)
        assertNotEquals(first, changedLength)
        assertNotEquals(first, changedModified)
    }

    @Test
    fun subtitleTextDecodesCommonAndNumericHtmlEntities() {
        assertEquals(
            "Tom & Jerry 'said' \"hi\" <again>",
            cleanSubtitleText("Tom &amp; Jerry &#39;said&#39; &quot;hi&quot; &lt;again&gt;")
        )
    }

    @Test
    fun subtitleTextDecodesOnlyOneEntityLayer() {
        assertEquals(
            "Already &amp; encoded",
            cleanSubtitleText("Already &amp;amp; encoded")
        )
    }

    @Test
    fun subtitleTextRemovesSubtitleMarkupBeforeDecoding() {
        assertEquals(
            "Line one\nLine two & more",
            cleanSubtitleText("{\\an8}<i>Line one</i>\\NLine two &amp; more")
        )
    }
}
