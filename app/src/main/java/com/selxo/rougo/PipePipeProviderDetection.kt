package com.selxo.rougo

import java.net.URI
import java.util.Locale

internal enum class StreamProvider { YouTube, Bilibili, Niconico, Unknown }

internal enum class PipePipeExtractionService { YouTube, Bilibili, Niconico }

internal data class PipePipeExtractionRoute(
    val normalizedUrl: String,
    val provider: StreamProvider,
    val service: PipePipeExtractionService,
    val usesPipePipeExtractor: Boolean = true,
    val usesYoutubeInitialDataScraper: Boolean = false
)

internal fun detectStreamProvider(url: String): StreamProvider {
    val host = normalizedPipePipeHost(url)
    return when {
        host == "youtu.be" || host.endsWith(".youtu.be") || host == "youtube.com" || host.endsWith(".youtube.com") -> StreamProvider.YouTube
        host == "b23.tv" || host.endsWith(".b23.tv") || host == "bilibili.com" || host.endsWith(".bilibili.com") -> StreamProvider.Bilibili
        host == "nico.ms" || host.endsWith(".nico.ms") || host == "nicovideo.jp" || host.endsWith(".nicovideo.jp") -> StreamProvider.Niconico
        else -> StreamProvider.Unknown
    }
}

internal fun planPipePipeExtractionRoute(url: String): PipePipeExtractionRoute? {
    val normalizedUrl = normalizePipePipeStreamUrl(url) ?: return null
    val provider = detectStreamProvider(normalizedUrl)
    val service = when (provider) {
        StreamProvider.YouTube -> PipePipeExtractionService.YouTube
        StreamProvider.Bilibili -> PipePipeExtractionService.Bilibili
        StreamProvider.Niconico -> PipePipeExtractionService.Niconico
        StreamProvider.Unknown -> return null
    }
    return PipePipeExtractionRoute(
        normalizedUrl = normalizedUrl,
        provider = provider,
        service = service
    )
}

private fun normalizedPipePipeHost(url: String): String {
    return (runCatching { URI(url).host?.takeIf { it.isNotBlank() } }.getOrNull()
        ?: runCatching { URI("https://$url").host.orEmpty() }.getOrDefault(""))
        .lowercase(Locale.US)
        .removePrefix("www.")
}
