package com.selxo.rougo

import java.net.URI
import java.net.URLDecoder
import java.util.Locale

internal fun normalizePipePipeStreamUrl(url: String): String? {
    val provider = detectStreamProvider(url)
    return if (provider == StreamProvider.YouTube) {
        normalizeYoutubeStreamUrl(url)
    } else if (provider == StreamProvider.Bilibili) {
        normalizeBilibiliStreamUrl(url)
    } else if (provider == StreamProvider.Niconico) {
        normalizeNiconicoStreamUrl(url)
    } else {
        null
    }
}

internal fun normalizePipePipePlaylistUrl(rawUrl: String): String? {
    val provider = detectStreamProvider(rawUrl)
    val parsed = parseLenientUri(rawUrl) ?: return null
    return if (provider == StreamProvider.YouTube) {
        val path = parsed.path.orEmpty().trim('/')
        val query = queryParameters(parsed.rawQuery)
        when {
            query["v"] != null -> null
            path == "playlist" && !query["list"].isNullOrBlank() ->
                "https://www.youtube.com/playlist?list=${query["list"]}"
            else -> null
        }
    } else if (provider == StreamProvider.Bilibili || provider == StreamProvider.Niconico) {
        parsed.toString()
    } else {
        null
    }
}

private fun normalizeYoutubeStreamUrl(rawUrl: String): String? {
    val parsed = parseLenientUri(rawUrl) ?: return null
    val host = parsed.host.orEmpty().lowercase(Locale.US).removePrefix("www.")
    val pathSegments = parsed.path.orEmpty().trim('/').split('/').filter { it.isNotBlank() }
    val query = queryParameters(parsed.rawQuery)
    val videoId = when {
        host == "youtu.be" || host.endsWith(".youtu.be") -> pathSegments.firstOrNull()
        host == "youtube.com" || host.endsWith(".youtube.com") -> query["v"]
            ?: if (pathSegments.size >= 2 && pathSegments[0] in setOf("shorts", "live", "embed")) pathSegments[1] else null
        else -> null
    }?.trim()
        ?.takeIf { Regex("[A-Za-z0-9_-]{6,}").matches(it) }
        ?: return null

    return "https://www.youtube.com/watch?v=$videoId"
}

private fun normalizeBilibiliStreamUrl(rawUrl: String): String? {
    val parsed = parseLenientUri(rawUrl) ?: return null
    val host = parsed.host.orEmpty().lowercase(Locale.US).removePrefix("www.")
    if (host == "b23.tv" || host.endsWith(".b23.tv")) return parsed.toString()

    val query = queryParameters(parsed.rawQuery)
    val path = parsed.path.orEmpty()
    val bvid = Regex("(?i)(?:^|/)video/(BV[0-9A-Za-z]+)").find(path)?.groupValues?.getOrNull(1)
        ?: query["bvid"]
        ?: return null
    val page = query["p"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
    val timestamp = query["t"]?.toLongOrNull()?.takeIf { it > 0L }
    return buildString {
        append("https://www.bilibili.com/video/")
        append(bvid)
        append("?p=")
        append(page)
        if (timestamp != null) {
            append("&t=")
            append(timestamp)
        }
    }
}

private fun normalizeNiconicoStreamUrl(rawUrl: String): String? {
    val parsed = parseLenientUri(rawUrl) ?: return null
    val host = parsed.host.orEmpty().lowercase(Locale.US).removePrefix("www.")
    val segments = parsed.path.orEmpty().trim('/').split('/').filter { it.isNotBlank() }
    val watchIndex = segments.indexOf("watch")
    val videoId = when {
        host == "nico.ms" || host.endsWith(".nico.ms") -> segments.firstOrNull()
        watchIndex >= 0 -> segments.getOrNull(watchIndex + 1)
        else -> null
    }?.trim()
        ?.takeIf { Regex("(?i)[a-z]{1,4}\\d+").matches(it) }
        ?: return null
    return "https://www.nicovideo.jp/watch/$videoId"
}

private fun parseLenientUri(rawUrl: String): URI? {
    val trimmed = rawUrl.trim()
    if (trimmed.isBlank()) return null
    return runCatching { URI(trimmed) }
        .mapCatching { parsed ->
            if (!parsed.host.isNullOrBlank()) parsed else URI("https://$trimmed")
        }
        .getOrElse {
            runCatching { URI("https://$trimmed") }.getOrNull()
        }
}

private fun queryParameters(rawQuery: String?): Map<String, String> {
    if (rawQuery.isNullOrBlank()) return emptyMap()
    return rawQuery.split('&')
        .mapNotNull { part ->
            val key = part.substringBefore('=', "").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val value = part.substringAfter('=', "")
            urlDecode(key) to urlDecode(value)
        }
        .toMap()
}

private fun urlDecode(value: String): String =
    runCatching { URLDecoder.decode(value, Charsets.UTF_8.name()) }.getOrDefault(value)
