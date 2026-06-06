package com.selxo.rougo

import java.net.URLDecoder

internal data class StreamRequestMetadata(
    val mediaUrl: String,
    val cookieHeader: String? = null,
    val lengthSeconds: Long? = null
)

internal fun streamRequestMetadataForUrl(rawUrl: String?): StreamRequestMetadata {
    val trimmed = rawUrl?.trim().orEmpty()
    if (trimmed.isBlank()) return StreamRequestMetadata(mediaUrl = "")

    val fragmentIndex = trimmed.indexOf('#')
    if (fragmentIndex < 0) return StreamRequestMetadata(mediaUrl = trimmed)

    val mediaUrl = trimmed.substring(0, fragmentIndex)
    val fragment = trimmed.substring(fragmentIndex + 1)
    val fragmentParams = fragmentQueryParameters(fragment)
    return StreamRequestMetadata(
        mediaUrl = mediaUrl,
        cookieHeader = fragmentParams["cookie"]?.takeIf { it.isNotBlank() },
        lengthSeconds = fragmentParams["length"]?.toLongOrNull()?.takeIf { it > 0L }
    )
}

private fun fragmentQueryParameters(fragment: String): Map<String, String> {
    if (fragment.isBlank()) return emptyMap()
    return fragment.split('&')
        .mapNotNull { part ->
            val key = part.substringBefore('=', "").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val value = part.substringAfter('=', "")
            urlDecodeFragmentComponent(key) to urlDecodeFragmentComponent(value)
        }
        .toMap()
}

private fun urlDecodeFragmentComponent(value: String): String =
    runCatching { URLDecoder.decode(value, Charsets.UTF_8.name()) }.getOrDefault(value)
