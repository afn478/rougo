package com.selxo.rougo

import androidx.compose.material.icons.filled.*

internal fun extractFirstUrl(text: String): String? {
    return extractAllUrls(text)
        .map { normalizeVideoUrlCandidate(it) }
        .firstOrNull()
}
internal fun extractAllUrls(text: String): List<String> {
    val regex = Regex(
        "(?i)\\b((?:https?://|www\\d{0,3}[.]|[a-z0-9.\\-]+[.][a-z]{2,10}/)" +
            "(?:[^\\s()<>]+|\\((?:[^\\s()<>]+|\\([^\\s()<>]+\\))*\\))+" +
            "(?:\\((?:[^\\s()<>]+|\\([^\\s()<>]+\\))*\\)|[^\\s`!()\\[\\]{};:'\".,<>?«»“”‘’]))"
    )

    return regex.findAll(text)
        .map { it.value.trim() }
        .toList()
}
internal fun normalizeVideoUrlCandidate(raw: String): String {
    val trimmed = raw
        .trim()
        .trimEnd(
            '.', ',', ';', ':',
            '。', '、', '，', '；', '：',
            ')', ']', '}', '）', '】', '』', '」', '》',
            '"', '\''
        )

    return when {
        trimmed.startsWith("www.", ignoreCase = true) ->
            "https://$trimmed"

        !trimmed.startsWith("http://", ignoreCase = true) &&
            !trimmed.startsWith("https://", ignoreCase = true) &&
            trimmed.contains(".") ->
            "https://$trimmed"

        else -> trimmed
    }
}
internal fun isSupportedVideoLink(url: String): Boolean {
    return isYoutubeUrl(url) || isBilibiliUrl(url) || isNiconicoUrl(url)
}
