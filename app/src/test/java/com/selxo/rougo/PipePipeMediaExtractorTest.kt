package com.selxo.rougo

import org.junit.Assert.assertEquals
import org.junit.Test

class PipePipeMediaExtractorTest {
    @Test
    fun detectsSupportedPipePipeProviders() {
        assertEquals(StreamProvider.YouTube, detectStreamProvider("https://www.youtube.com/watch?v=abc"))
        assertEquals(StreamProvider.YouTube, detectStreamProvider("https://youtu.be/abc"))
        assertEquals(StreamProvider.Bilibili, detectStreamProvider("https://www.bilibili.com/video/BV123"))
        assertEquals(StreamProvider.Bilibili, detectStreamProvider("https://b23.tv/abc"))
        assertEquals(StreamProvider.Niconico, detectStreamProvider("https://www.nicovideo.jp/watch/sm123"))
        assertEquals(StreamProvider.Niconico, detectStreamProvider("https://nico.ms/sm123"))
    }

    @Test
    fun unknownHostsAreNotSupported() {
        assertEquals(StreamProvider.Unknown, detectStreamProvider("https://example.com/watch/1"))
    }
}
