package com.selxo.rougo

import java.util.Locale

internal fun YoutubeStreamFormat.playbackUrl(): String? {
    val manifest = manifestUrl?.takeIf { it.isNotBlank() }
    val directUrl = url?.takeIf { it.isNotBlank() }
    val protocolValue = protocol?.lowercase(Locale.US).orEmpty()
    return when {
        protocolValue.contains("hls") || protocolValue.contains("m3u8") -> directUrl ?: manifest
        protocolValue.contains("dash") -> manifest ?: directUrl
        isVlcFriendlyVideoFormat() || isVlcFriendlyAudioFormat() -> directUrl ?: manifest
        else -> directUrl ?: manifest
    }
}

internal fun mergePipePipeStreamFormats(
    muxedVideoFormats: List<YoutubeStreamFormat>,
    videoOnlyFormats: List<YoutubeStreamFormat>,
    audioFormats: List<YoutubeStreamFormat>,
    manifestFormats: List<YoutubeStreamFormat>
): List<YoutubeStreamFormat> {
    val pairedVideoFormats = videoOnlyFormats.map { videoFormat ->
        videoFormat.withCompanionAudio(selectBestCompanionAudioFormat(audioFormats, videoFormat))
    }
    return (muxedVideoFormats + pairedVideoFormats + audioFormats + manifestFormats)
        .distinctBy { it.streamMappingKey() }
}

internal fun YoutubeStreamFormat.hasSeparateAudioStream(): Boolean =
    !audioUrl.isNullOrBlank()

internal fun YoutubeStreamFormat.withCompanionAudio(audioFormat: YoutubeStreamFormat?): YoutubeStreamFormat {
    if (vcodec == "none" || acodec != "none") return this
    val companionUrl = audioFormat?.playbackUrl()?.takeIf { it.isNotBlank() } ?: return this
    val companionCodec = audioFormat.acodec
        ?.takeIf { it.isNotBlank() && !it.equals("none", ignoreCase = true) }
        ?: "unknown"
    return copy(
        acodec = companionCodec,
        audioUrl = companionUrl,
        audioFormatId = audioFormat.formatId,
        audioExt = audioFormat.ext,
        audioCodec = companionCodec,
        formatNote = pairedFormatNote(formatNote, audioFormat.formatNote)
    )
}

internal fun selectBestCompanionAudioFormat(
    audioFormats: List<YoutubeStreamFormat>,
    videoFormat: YoutubeStreamFormat? = null
): YoutubeStreamFormat? {
    return audioFormats
        .filter { it.vcodec == "none" && it.acodec != "none" && it.playbackUrl() != null }
        .sortedWith(
            compareByDescending<YoutubeStreamFormat> { it.companionContainerScore(videoFormat) }
                .thenByDescending { if (it.isVlcFriendlyAudioFormat()) 1 else 0 }
                .thenByDescending { it.tbr }
                .thenByDescending { it.formatId.orEmpty() }
        )
        .firstOrNull()
}

private fun YoutubeStreamFormat.companionContainerScore(videoFormat: YoutubeStreamFormat?): Int {
    val videoExt = videoFormat?.ext?.lowercase(Locale.US).orEmpty()
    if (videoExt.isBlank()) return 1
    val audioExtValue = ext?.lowercase(Locale.US).orEmpty()
    val audioCodecValue = acodec?.lowercase(Locale.US).orEmpty()
    return when {
        videoExt in setOf("mp4", "m4v") &&
            (audioExtValue in setOf("m4a", "mp4", "aac") ||
                audioCodecValue.startsWith("mp4a") ||
                audioCodecValue.startsWith("aac")) -> 3
        videoExt == "webm" &&
            (audioExtValue == "webm" ||
                audioCodecValue.contains("opus") ||
                audioCodecValue.contains("vorbis")) -> 3
        videoExt in setOf("mp4", "m4v", "webm") -> 0
        else -> 1
    }
}

private fun pairedFormatNote(videoNote: String?, audioNote: String?): String? {
    val cleanVideo = cleanMetadataValue(videoNote)
    val cleanAudio = cleanMetadataValue(audioNote)
    return when {
        cleanVideo != null && cleanAudio != null -> "$cleanVideo + $cleanAudio"
        cleanVideo != null -> cleanVideo
        cleanAudio != null -> cleanAudio
        else -> null
    }
}

private fun YoutubeStreamFormat.streamMappingKey(): String {
    return listOf(
        formatId.orEmpty(),
        audioFormatId.orEmpty(),
        url.orEmpty(),
        audioUrl.orEmpty(),
        audioExt.orEmpty(),
        audioCodec.orEmpty(),
        manifestUrl.orEmpty(),
        height.toString(),
        vcodec.orEmpty().lowercase(Locale.US),
        acodec.orEmpty().lowercase(Locale.US)
    ).joinToString("\u001f")
}
