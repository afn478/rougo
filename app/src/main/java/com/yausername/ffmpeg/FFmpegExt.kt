package com.yausername.ffmpeg

import com.yausername.youtubedl_android.YoutubeDL
import java.io.File

/**
 * Extension function to provide standalone FFmpeg execution support for youtubedl-android:ffmpeg 0.18.1.
 * This satisfies the signature requested by the user and implements it by running the unzipped binary.
 */
fun FFmpeg.execute(commands: Array<String>): Int {
    return try {
        val ytdl = YoutubeDL.getInstance()
        // Accessing private ffmpegPath via reflection as it's correctly set during YoutubeDL.init()
        val ffmpegPathField = ytdl.javaClass.getDeclaredField("ffmpegPath")
        ffmpegPathField.isAccessible = true
        val ffmpegPathFile = ffmpegPathField.get(ytdl) as? File ?: return -1
        val ffmpegPath = ffmpegPathFile.absolutePath
        
        val process = Runtime.getRuntime().exec(arrayOf(ffmpegPath) + commands)
        process.waitFor()
    } catch (e: Exception) {
        e.printStackTrace()
        -1
    }
}

/**
 * Extension property to provide the RETURN_CODE_SUCCESS constant.
 */
val FFmpeg.RETURN_CODE_SUCCESS: Int get() = 0
