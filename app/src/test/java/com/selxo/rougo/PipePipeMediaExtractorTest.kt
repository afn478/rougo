package com.selxo.rougo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun normalizesYoutubeWatchUrlsForSingleVideoExtraction() {
        assertEquals(
            "https://www.youtube.com/watch?v=BVdLpctzqzk",
            normalizePipePipeStreamUrl("https://www.youtube.com/watch?v=BVdLpctzqzk&list=RDBVdLpctzqzk&start_radio=1")
        )
        assertEquals(
            "https://www.youtube.com/watch?v=EIlIbE9HuB4",
            normalizePipePipeStreamUrl("https://www.youtube.com/watch?v=EIlIbE9HuB4")
        )
        assertEquals(
            "https://www.youtube.com/watch?v=EIlIbE9HuB4",
            normalizePipePipeStreamUrl("https://m.youtube.com/watch?v=EIlIbE9HuB4&feature=share&si=abc")
        )
        assertEquals(
            "https://www.youtube.com/watch?v=abc123XYZ_0",
            normalizePipePipeStreamUrl("https://youtu.be/abc123XYZ_0?si=abc")
        )
    }

    @Test
    fun normalizesBilibiliPageUrlsWithoutDroppingPageSelection() {
        assertEquals(
            "https://www.bilibili.com/video/BV1fqVh6sEaF?p=1",
            normalizePipePipeStreamUrl("https://www.bilibili.com/video/BV1fqVh6sEaF?p=1")
        )
        assertEquals(
            "https://www.bilibili.com/video/BV1fqVh6sEaF?p=3&t=42",
            normalizePipePipeStreamUrl("https://www.bilibili.com/video/BV1fqVh6sEaF?spm_id_from=x&p=3&t=42")
        )
    }

    @Test
    fun normalizesNiconicoWatchUrlsToCanonicalWatchUrl() {
        assertEquals(
            "https://www.nicovideo.jp/watch/sm46399371",
            normalizePipePipeStreamUrl("https://www.nicovideo.jp/watch/sm46399371?ref=search")
        )
        assertEquals(
            "https://www.nicovideo.jp/watch/sm46399371",
            normalizePipePipeStreamUrl("https://sp.nicovideo.jp/watch/sm46399371")
        )
        assertEquals(
            "https://www.nicovideo.jp/watch/sm46399371",
            normalizePipePipeStreamUrl("https://nico.ms/sm46399371")
        )
    }

    @Test
    fun youtubeWatchWithVideoIdIsNotTreatedAsPlaylistImport() {
        assertNotEquals(
            StreamProvider.Unknown,
            detectStreamProvider("https://www.youtube.com/watch?v=BVdLpctzqzk&list=RDBVdLpctzqzk&start_radio=1")
        )
        assertEquals(
            "https://www.youtube.com/watch?v=BVdLpctzqzk",
            normalizePipePipeStreamUrl("https://www.youtube.com/watch?v=BVdLpctzqzk&list=RDBVdLpctzqzk&start_radio=1")
        )
    }

    @Test
    fun youtubeWatchUrlsRouteToPipePipeExtractorNotInitialDataScraper() {
        val browserRoute = planPipePipeExtractionRoute(
            "https://www.youtube.com/watch?v=BVdLpctzqzk&list=RDBVdLpctzqzk&start_radio=1"
        )
        val sharedRoute = planPipePipeExtractionRoute("https://www.youtube.com/watch?v=EIlIbE9HuB4")

        assertEquals(StreamProvider.YouTube, browserRoute?.provider)
        assertEquals(PipePipeExtractionService.YouTube, browserRoute?.service)
        assertEquals("https://www.youtube.com/watch?v=BVdLpctzqzk", browserRoute?.normalizedUrl)
        assertTrue(browserRoute?.usesPipePipeExtractor == true)
        assertFalse(browserRoute?.usesYoutubeInitialDataScraper == true)

        assertEquals(StreamProvider.YouTube, sharedRoute?.provider)
        assertEquals(PipePipeExtractionService.YouTube, sharedRoute?.service)
        assertEquals("https://www.youtube.com/watch?v=EIlIbE9HuB4", sharedRoute?.normalizedUrl)
        assertTrue(sharedRoute?.usesPipePipeExtractor == true)
        assertFalse(sharedRoute?.usesYoutubeInitialDataScraper == true)
    }

    @Test
    fun niconicoWatchUrlRoutesToNiconicoPipePipeProvider() {
        val route = planPipePipeExtractionRoute("https://www.nicovideo.jp/watch/sm46399371")

        assertEquals(StreamProvider.Niconico, route?.provider)
        assertEquals(PipePipeExtractionService.Niconico, route?.service)
        assertEquals("https://www.nicovideo.jp/watch/sm46399371", route?.normalizedUrl)
        assertTrue(route?.usesPipePipeExtractor == true)
    }

    @Test
    fun niconicoDomandFragmentCookieIsMappedToRequestMetadata() {
        val request = streamRequestMetadataForUrl(
            "https://delivery.domand.nicovideo.jp/hlsbid/session/playlists/media/video-h264-720p.m3u8?ht2_nicovideo=123" +
                "#cookie=domand_bid%3D6b3112820bb69b5c31d9ae2daa624f9b024b1c7a1bab02ae9ef1f7cceae08bb0&length=130"
        )

        assertEquals(
            "https://delivery.domand.nicovideo.jp/hlsbid/session/playlists/media/video-h264-720p.m3u8?ht2_nicovideo=123",
            request.mediaUrl
        )
        assertEquals(
            "domand_bid=6b3112820bb69b5c31d9ae2daa624f9b024b1c7a1bab02ae9ef1f7cceae08bb0",
            request.cookieHeader
        )
        assertEquals(130L, request.lengthSeconds)
    }

    @Test
    fun streamRequestMetadataLeavesPlainUrlsAlone() {
        val request = streamRequestMetadataForUrl("https://video.example/stream.m3u8?token=abc")

        assertEquals("https://video.example/stream.m3u8?token=abc", request.mediaUrl)
        assertNull(request.cookieHeader)
        assertNull(request.lengthSeconds)
    }

    @Test
    fun videoOnlyFormatsArePairedWithBestAudioBeforeSelection() {
        val audioLow = YoutubeStreamFormat(
            formatId = "audio-low",
            formatNote = "64k",
            ext = "m4a",
            vcodec = "none",
            acodec = "mp4a",
            height = 0,
            tbr = 64,
            url = "https://stream.example/audio-low.m4a",
            manifestUrl = null,
            protocol = "http"
        )
        val audioHigh = audioLow.copy(
            formatId = "audio-high",
            formatNote = "128k",
            tbr = 128,
            url = "https://stream.example/audio-high.m4a"
        )
        val mapped = mergePipePipeStreamFormats(
            muxedVideoFormats = emptyList(),
            videoOnlyFormats = listOf(
                YoutubeStreamFormat(
                    formatId = "video-720",
                    formatNote = "720p",
                    ext = "mp4",
                    vcodec = "avc1",
                    acodec = "none",
                    height = 720,
                    tbr = 1200,
                    url = "https://stream.example/video.mp4",
                    manifestUrl = null,
                    protocol = "http"
                )
            ),
            audioFormats = listOf(audioLow, audioHigh),
            manifestFormats = emptyList()
        )
        val selected = selectPreferredYoutubeFormat(
            formats = mapped,
            preferredResolution = "720"
        )

        assertEquals("video-720", selected?.formatId)
        assertEquals("audio-high", selected?.audioFormatId)
        assertEquals("https://stream.example/audio-high.m4a", selected?.audioUrl)
        assertNotEquals("none", selected?.acodec)
    }
}
