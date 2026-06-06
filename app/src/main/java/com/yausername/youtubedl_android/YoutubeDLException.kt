package com.yausername.youtubedl_android

/**
 * Compatibility class required by the standalone FFmpeg artifact.
 *
 * Rougo no longer uses the YoutubeDL extractor/downloader, but the FFmpeg AAR
 * still references this exception type from its initialization error path.
 */
class YoutubeDLException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
