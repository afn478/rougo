package com.selxo.rougo

import android.content.Context
import android.net.Uri
import android.os.Environment
import java.io.File
import java.io.IOException
import java.net.URI
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.downloader.CancellableCall
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request as ExtractorRequest
import org.schabi.newpipe.extractor.downloader.Response as ExtractorResponse
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException
import org.schabi.newpipe.extractor.exceptions.NeedLoginException
import org.schabi.newpipe.extractor.exceptions.PaidContentException
import org.schabi.newpipe.extractor.exceptions.PrivateContentException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.SubtitlesStream
import org.schabi.newpipe.extractor.stream.VideoStream

internal enum class StreamProvider { YouTube, Bilibili, Niconico, Unknown }

internal fun detectStreamProvider(url: String): StreamProvider {
    val host = normalizedHost(url)
    return when {
        host == "youtu.be" || host.endsWith(".youtu.be") || host == "youtube.com" || host.endsWith(".youtube.com") -> StreamProvider.YouTube
        host == "b23.tv" || host.endsWith(".b23.tv") || host == "bilibili.com" || host.endsWith(".bilibili.com") -> StreamProvider.Bilibili
        host == "nico.ms" || host.endsWith(".nico.ms") || host == "nicovideo.jp" || host.endsWith(".nicovideo.jp") -> StreamProvider.Niconico
        else -> StreamProvider.Unknown
    }
}

internal fun isPipePipeSupportedUrl(url: String): Boolean =
    normalizePipePipeStreamUrl(url) != null

internal fun fetchPipePipeFastStream(
    context: Context,
    url: String,
    preferredResolution: String
): FastYoutubeStream? {
    val normalizedUrl = normalizePipePipeStreamUrl(url) ?: return null
    val setupData = fetchPipePipeSetupData(context, normalizedUrl)
    val selected = selectPreferredYoutubeFormat(setupData.formats, preferredResolution) ?: return null
    val streamUrl = selected.playbackUrl() ?: return null
    return FastYoutubeStream(
        title = setupData.title,
        streamUrl = streamUrl,
        formatId = selected.formatId,
        isVideo = selected.vcodec != "none",
        httpUserAgent = setupData.httpUserAgent,
        httpReferer = setupData.httpReferer
    )
}

internal fun fetchPipePipeSetupData(context: Context, url: String): YoutubeSetupData {
    ensurePipePipeReady(context)
    val normalizedUrl = normalizePipePipeStreamUrl(url)
        ?: throw IllegalArgumentException(context.getString(R.string.stream_error_unsupported_url))
    val service = pipePipeServiceForUrl(normalizedUrl)
    val extractor = service.getStreamExtractor(normalizedUrl)
    val info = StreamInfo.getInfo(extractor)
    val subtitles = runCatching { extractor.getSubtitles(MediaFormat.VTT) }
        .getOrElse { runCatching { extractor.getSubtitles(MediaFormat.SRT) }.getOrDefault(info.subtitles.orEmpty()) }
    return setupDataFromStreamInfo(context, normalizedUrl, info, subtitles)
}

internal fun resolvePipePipeStreamUrl(context: Context, url: String, formatId: String?): String? {
    val normalizedUrl = normalizePipePipeStreamUrl(url) ?: return null
    StreamUrlCache.get(context, normalizedUrl, formatId)?.let { return it }
    return runCatching {
        val setup = fetchPipePipeSetupData(context, normalizedUrl)
        val format = setup.formats.firstOrNull { it.formatId == formatId }
            ?: selectPreferredYoutubeFormat(setup.formats, DEFAULT_YOUTUBE_RESOLUTION)
            ?: setup.formats.firstOrNull()
        format?.playbackUrl() ?: setup.fallbackUrl
    }.onFailure {
        CrashReporter.recordHandled(context, "PipePipe stream URL", it)
    }.getOrNull()
        ?.also { resolvedUrl ->
            StreamUrlCache.put(context, normalizedUrl, formatId, resolvedUrl, detectStreamProvider(normalizedUrl))
        }
}

internal fun invalidateResolvedStreamUrl(context: Context, url: String, formatId: String?) {
    val normalizedUrl = normalizePipePipeStreamUrl(url) ?: url
    StreamUrlCache.invalidate(context, normalizedUrl, formatId)
}

internal fun streamImportErrorMessage(context: Context, throwable: Throwable): String {
    val chain = generateSequence(throwable) { it.cause }.toList()
    if (chain.any { it is GeographicRestrictionException }) {
        return context.getString(R.string.stream_error_geoblocked)
    }
    if (chain.any { it is NeedLoginException || it is PaidContentException || it is AgeRestrictedContentException || it is PrivateContentException }) {
        return context.getString(R.string.stream_error_restricted)
    }
    val message = chain.firstNotNullOfOrNull { it.localizedMessage?.takeIf(String::isNotBlank) }.orEmpty()
    val normalizedMessage = message.lowercase(Locale.US)
    if (
        throwable is IllegalArgumentException ||
        normalizedMessage.contains("not accepted") ||
        normalizedMessage.contains("not a bilibili video link") ||
        normalizedMessage.contains("unsupported url")
    ) {
        return context.getString(R.string.stream_error_unsupported_url)
    }
    return context.getString(
        R.string.stream_error_extraction_failed,
        message.ifBlank { throwable::class.java.simpleName }
    )
}

internal fun fetchPipePipePlaylistImportPlan(context: Context, url: String): PlaylistImportPlan? {
    ensurePipePipeReady(context)
    return runCatching {
        val playlistUrl = normalizePipePipePlaylistUrl(url) ?: return null
        val service = pipePipeServiceForUrl(playlistUrl)
        if (service.getLinkTypeByUrl(playlistUrl) != StreamingService.LinkType.PLAYLIST) return null
        val playlist = PlaylistInfo.getInfoWithFullItems(service, playlistUrl)
        val entries = playlist.relatedItems.orEmpty()
            .filter { !it.url.isNullOrBlank() }
            .map {
                val entryUrl = normalizePipePipeStreamUrl(it.url) ?: it.url
                PlaylistImportEntry(
                    title = cleanMetadataValue(it.name) ?: context.getString(R.string.media_source_default_title, streamSourceLabel(context, entryUrl)),
                    sourceUrl = entryUrl,
                    thumbnailUrl = it.thumbnailUrl,
                    isVideo = true
                )
            }
        if (entries.isEmpty()) return null
        buildPlaylistImportPlan(
            playlistTitle = cleanMetadataValue(playlist.name)
                ?: context.getString(R.string.media_source_playlist),
            playlistUrl = playlistUrl,
            entries = entries,
            nextId = { UUID.randomUUID().toString() }
        )
    }.onFailure {
        CrashReporter.recordHandled(context, "PipePipe playlist import", it)
    }.getOrNull()
}

internal fun isPipePipePlaylistUrl(context: Context, url: String): Boolean {
    ensurePipePipeReady(context)
    return runCatching {
        val playlistUrl = normalizePipePipePlaylistUrl(url) ?: return false
        pipePipeServiceForUrl(playlistUrl).getLinkTypeByUrl(playlistUrl) == StreamingService.LinkType.PLAYLIST
    }.getOrDefault(false)
}

internal fun downloadPipePipeSubtitle(
    context: Context,
    url: String,
    languageCode: String,
    isAutoGenerated: Boolean
): String? {
    ensurePipePipeReady(context)
    return runCatching {
        val normalizedUrl = normalizePipePipeStreamUrl(url) ?: return null
        val service = pipePipeServiceForUrl(normalizedUrl)
        val extractor = service.getStreamExtractor(normalizedUrl)
        extractor.fetchPage()
        val candidates = runCatching { extractor.getSubtitles(MediaFormat.VTT) }.getOrDefault(emptyList()) +
            runCatching { extractor.getSubtitles(MediaFormat.SRT) }.getOrDefault(emptyList()) +
            runCatching { extractor.getSubtitlesDefault() }.getOrDefault(emptyList())
        val selected = candidates.distinctBy { "${it.languageTag}:${it.isAutoGenerated}:${it.formatId}" }
            .firstOrNull {
                it.languageTag.equals(languageCode, ignoreCase = true) &&
                    it.isAutoGenerated == isAutoGenerated &&
                    (it.getFormat()?.let { format -> format == MediaFormat.VTT || format == MediaFormat.SRT } != false)
            }
            ?: candidates.firstOrNull {
                it.languageTag.equals(languageCode, ignoreCase = true) &&
                    (it.getFormat()?.let { format -> format == MediaFormat.VTT || format == MediaFormat.SRT } != false)
            }
            ?: return null
        saveSubtitleStream(context, selected)
    }.onFailure {
        CrashReporter.recordHandled(context, "PipePipe subtitle download", it)
    }.getOrNull()
}

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

private object PipePipeRuntime {
    private val downloader = PipePipeOkHttpDownloader()

    @Volatile
    var initialized = false

    fun init() {
        if (initialized) return
        synchronized(this) {
            if (!initialized) {
                NewPipe.init(downloader, Localization.DEFAULT)
                initialized = true
            }
        }
    }
}

private fun ensurePipePipeReady(context: Context) {
    try {
        PipePipeRuntime.init()
    } catch (t: Throwable) {
        CrashReporter.recordHandled(context, "PipePipe init", t)
        throw t
    }
}

private fun pipePipeServiceForUrl(url: String): StreamingService {
    return when (detectStreamProvider(url)) {
        StreamProvider.YouTube -> ServiceList.YouTube
        StreamProvider.Bilibili -> ServiceList.BiliBili
        StreamProvider.Niconico -> ServiceList.NicoNico
        StreamProvider.Unknown -> NewPipe.getServiceByUrl(url)
    }
}

private fun setupDataFromStreamInfo(
    context: Context,
    sourceUrl: String,
    info: StreamInfo,
    subtitles: List<SubtitlesStream>
): YoutubeSetupData {
    val formats = buildList {
        info.videoStreams.orEmpty().mapNotNullTo(this) { it.toYoutubeStreamFormat() }
        info.videoOnlyStreams.orEmpty().mapNotNullTo(this) { it.toYoutubeStreamFormat() }
        info.audioStreams.orEmpty().mapNotNullTo(this) { it.toYoutubeStreamFormat() }
        addManifestFormat("hls", info.hlsUrl, "m3u8", "hls")
        addManifestFormat("dash", info.dashMpdUrl, "mpd", "dash")
    }.distinctBy { "${it.formatId}:${it.url}:${it.manifestUrl}:${it.height}:${it.vcodec}:${it.acodec}" }
    val fallback = formats.firstNotNullOfOrNull { it.playbackUrl() } ?: info.hlsUrl ?: info.dashMpdUrl
    return YoutubeSetupData(
        title = cleanExtractorTitle(context, info.name, sourceUrl),
        fallbackUrl = fallback,
        formats = formats,
        subtitleChoices = subtitles.toYoutubeSubtitleChoices(),
        thumbnailUrl = cleanMetadataValue(info.thumbnailUrl),
        httpUserAgent = DEFAULT_PIPEPIPE_USER_AGENT,
        httpReferer = sourceUrl
    )
}

private fun MutableList<YoutubeStreamFormat>.addManifestFormat(
    id: String,
    url: String?,
    extension: String,
    protocol: String
) {
    val manifestUrl = url?.takeIf { it.isNotBlank() } ?: return
    add(
        YoutubeStreamFormat(
            formatId = id,
            formatNote = id.uppercase(Locale.US),
            ext = extension,
            vcodec = "unknown",
            acodec = "unknown",
            height = 0,
            tbr = 0,
            url = manifestUrl,
            manifestUrl = manifestUrl,
            protocol = protocol
        )
    )
}

private fun VideoStream.toYoutubeStreamFormat(): YoutubeStreamFormat? {
    val playbackUrl = contentUrl() ?: manifestUrl?.takeIf { it.isNotBlank() } ?: return null
    val heightValue = height.takeIf { it > 0 } ?: resolutionHeight(resolution)
    return YoutubeStreamFormat(
        formatId = streamFormatId(id, heightValue.takeIf { it > 0 }?.toString(), playbackUrl),
        formatNote = resolution.takeIf { it.isNotBlank() } ?: quality,
        ext = getFormat()?.suffix,
        vcodec = codec?.takeIf { it.isNotBlank() } ?: "unknown",
        acodec = if (isVideoOnly) "none" else "unknown",
        height = heightValue,
        tbr = bitrate.coerceAtLeast(0),
        url = playbackUrl,
        manifestUrl = manifestUrl,
        protocol = deliveryMethod.protocolName()
    )
}

private fun AudioStream.toYoutubeStreamFormat(): YoutubeStreamFormat? {
    val playbackUrl = contentUrl() ?: manifestUrl?.takeIf { it.isNotBlank() } ?: return null
    return YoutubeStreamFormat(
        formatId = streamFormatId(id, (averageBitrate.takeIf { it > 0 } ?: bitrate).takeIf { it > 0 }?.toString(), playbackUrl),
        formatNote = quality?.takeIf { it.isNotBlank() } ?: "Audio",
        ext = getFormat()?.suffix,
        vcodec = "none",
        acodec = codec?.takeIf { it.isNotBlank() } ?: "unknown",
        height = 0,
        tbr = averageBitrate.takeIf { it > 0 } ?: bitrate.coerceAtLeast(0),
        url = playbackUrl,
        manifestUrl = manifestUrl,
        protocol = deliveryMethod.protocolName()
    )
}

private fun streamFormatId(id: String?, qualifier: String?, playbackUrl: String): String? {
    val base = id?.takeIf { it.isNotBlank() } ?: return null
    val suffix = listOfNotNull(qualifier, playbackUrl.hashCode().toString()).joinToString("-")
    return if (suffix.isBlank()) base else "$base-$suffix"
}

private fun Stream.contentUrl(): String? =
    if (isUrl) content.takeIf { it.isNotBlank() } else null

private fun DeliveryMethod.protocolName(): String {
    return when (this) {
        DeliveryMethod.PROGRESSIVE_HTTP -> "http"
        DeliveryMethod.DASH -> "dash"
        DeliveryMethod.HLS -> "hls"
        DeliveryMethod.SS -> "ss"
        DeliveryMethod.TORRENT -> "torrent"
    }
}

private fun List<SubtitlesStream>.toYoutubeSubtitleChoices(): List<YoutubeSubtitleChoice> {
    return distinctBy { "${it.languageTag}:${it.isAutoGenerated}" }
        .map { subtitle ->
            val languageCode = subtitle.languageTag
            val displayName = subtitle.displayLanguageName.takeIf { it.isNotBlank() } ?: languageCode
            YoutubeSubtitleChoice(
                label = if (subtitle.isAutoGenerated) "$displayName ($languageCode, Auto)" else "$displayName ($languageCode)",
                languageCode = languageCode,
                isAutoGenerated = subtitle.isAutoGenerated
            )
        }
        .sortedWith(
            compareBy<YoutubeSubtitleChoice> { languagePriority(it.languageCode) }
                .thenBy { it.isAutoGenerated }
                .thenBy { it.label.lowercase(Locale.US) }
        )
}

private fun languagePriority(languageCode: String): Int {
    val code = languageCode.lowercase(Locale.US)
    return when {
        code == "ja" || code.startsWith("ja-") -> 0
        code == "en" || code.startsWith("en-") -> 1
        else -> 2
    }
}

private fun cleanExtractorTitle(context: Context, rawTitle: String?, sourceUrl: String): String {
    return cleanMetadataValue(rawTitle)
        ?.takeIf { !looksLikeGeneratedFileId(it) }
        ?: context.getString(R.string.media_source_default_title, streamSourceLabel(context, sourceUrl))
}

private fun saveSubtitleStream(context: Context, stream: SubtitlesStream): String? {
    val extension = stream.getFormat()?.suffix?.takeIf { it.isNotBlank() } ?: "vtt"
    val destDir = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "RougoSubs").apply { mkdirs() }
    val file = File(destDir, "${UUID.randomUUID()}.$extension")
    val bytes = if (stream.isUrl) {
        pipePipeHttpClient.newCall(okhttp3.Request.Builder().url(stream.content).build()).execute().use { response ->
            if (!response.isSuccessful) return null
            response.body?.bytes() ?: return null
        }
    } else {
        stream.content.toByteArray(Charsets.UTF_8)
    }
    if (bytes.isEmpty()) return null
    file.writeBytes(bytes)
    return Uri.fromFile(file).toString()
}

private fun resolutionHeight(resolution: String?): Int {
    return resolution
        ?.let { Regex("(\\d{3,4})p").find(it)?.groupValues?.getOrNull(1)?.toIntOrNull() }
        ?: 0
}

private fun normalizedHost(url: String): String {
    return runCatching { URI(url).host.orEmpty() }
        .getOrElse { runCatching { URI("https://$url").host.orEmpty() }.getOrDefault("") }
        .lowercase(Locale.US)
        .removePrefix("www.")
}

private const val DEFAULT_PIPEPIPE_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0 Mobile Safari/537.36"

private val pipePipeHttpClient: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .followRedirects(true)
    .followSslRedirects(true)
    .build()

private class PipePipeOkHttpDownloader : Downloader() {
    @Throws(IOException::class, ReCaptchaException::class)
    override fun execute(request: ExtractorRequest): ExtractorResponse {
        pipePipeHttpClient.newCall(request.toOkHttpRequest()).execute().use { response ->
            return response.toExtractorResponse()
        }
    }

    @Throws(IOException::class, ReCaptchaException::class)
    override fun executeAsync(
        request: ExtractorRequest,
        callback: Downloader.AsyncCallback
    ): CancellableCall {
        val call = pipePipeHttpClient.newCall(request.toOkHttpRequest())
        val cancellable = CancellableCall(call)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                cancellable.setFinished()
                callback.onError(e)
            }

            override fun onResponse(call: Call, response: okhttp3.Response) {
                response.use {
                    try {
                        callback.onSuccess(it.toExtractorResponse())
                    } catch (e: Exception) {
                        callback.onError(e)
                    } finally {
                        cancellable.setFinished()
                    }
                }
            }
        })
        return cancellable
    }
}

private fun ExtractorRequest.toOkHttpRequest(): okhttp3.Request {
    val method = httpMethod().uppercase(Locale.US)
    val builder = okhttp3.Request.Builder().url(url())
    headers().forEach { (name, values) ->
        if (name.equals("Content-Length", ignoreCase = true)) return@forEach
        values.forEach { value -> builder.addHeader(name, value) }
    }
    if (headers().keys.none { it.equals("User-Agent", ignoreCase = true) }) {
        builder.header("User-Agent", DEFAULT_PIPEPIPE_USER_AGENT)
    }
    val body = when {
        dataToSend() != null -> dataToSend()!!.toRequestBody(contentTypeHeader()?.toMediaTypeOrNull())
        method == "POST" || method == "PUT" || method == "PATCH" -> ByteArray(0).toRequestBody(null)
        else -> null
    }
    return builder.method(method, body).build()
}

private fun ExtractorRequest.contentTypeHeader(): String? {
    return headers().entries.firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }
        ?.value
        ?.firstOrNull()
}

private fun okhttp3.Response.toExtractorResponse(): ExtractorResponse {
    val bodyBytes = body?.bytes() ?: ByteArray(0)
    val bodyText = bodyBytes.toString(Charsets.UTF_8)
    return ExtractorResponse(
        code,
        message,
        headers.toMultimap(),
        bodyText,
        bodyBytes,
        request.url.toString()
    )
}
