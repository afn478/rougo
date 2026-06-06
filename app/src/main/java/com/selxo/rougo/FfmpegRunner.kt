package com.selxo.rougo

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

private const val FFMPEG_PACKAGE_PREFS = "rougo_ffmpeg"
private const val PREF_FFMPEG_PACKAGE_VERSION = "ffmpeg_package_version"
private const val FFMPEG_PACKAGE_ROOT = "rougo-ffmpeg"
private const val FFMPEG_TAG = "FfmpegRunner"
private const val DEFAULT_FFMPEG_TIMEOUT_MS = 90_000L
private const val MAX_FFMPEG_LOG_CHARS = 16_384

private val ffmpegInitLock = Any()
@Volatile
private var ffmpegInitialized = false
@Volatile
private var ffmpegInitFailureLogged = false

internal fun ensureFfmpegReady(context: Context): Boolean {
    if (ffmpegInitialized) return true
    val appContext = context.applicationContext
    return synchronized(ffmpegInitLock) {
        if (ffmpegInitialized) {
            true
        } else {
            try {
                installFfmpegPackage(appContext)
                ffmpegInitialized = true
                true
            } catch (t: Throwable) {
                t.printStackTrace()
                if (!ffmpegInitFailureLogged) {
                    CrashReporter.recordHandled(appContext, "FFmpeg init", t)
                    ffmpegInitFailureLogged = true
                }
                false
            }
        }
    }
}

internal fun executeFfmpeg(
    context: Context,
    commands: Array<String>,
    timeoutMs: Long = DEFAULT_FFMPEG_TIMEOUT_MS
): Int {
    val appContext = context.applicationContext
    if (!ensureFfmpegReady(appContext)) return -1

    return try {
        val nativeDir = File(appContext.applicationInfo.nativeLibraryDir)
        val executable = File(nativeDir, "libffmpeg.so").takeIf { it.exists() && it.length() > 0L }
            ?: return -1
        val packageDir = ffmpegPackageDir(appContext)
        val libDir = File(packageDir, "usr/lib")

        val processBuilder = ProcessBuilder(mutableListOf(executable.absolutePath).apply { addAll(commands) })
            .redirectErrorStream(true)
        processBuilder.environment().apply {
            val existingLdPath = get("LD_LIBRARY_PATH").orEmpty()
            put("LD_LIBRARY_PATH", listOf(nativeDir.absolutePath, libDir.absolutePath, existingLdPath).filter { it.isNotBlank() }.joinToString(":"))
            put("TMPDIR", appContext.cacheDir.absolutePath)
            val existingPath = get("PATH").orEmpty()
            put("PATH", listOf(nativeDir.absolutePath, existingPath).filter { it.isNotBlank() }.joinToString(":"))
        }

        val process = processBuilder.start()
        val outputBuffer = StringBuilder()
        val outputDrain = Thread {
            try {
                process.inputStream.use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        // Drain FFmpeg output so the process cannot block on a full pipe.
                        if (outputBuffer.length < MAX_FFMPEG_LOG_CHARS) {
                            val remaining = MAX_FFMPEG_LOG_CHARS - outputBuffer.length
                            val text = String(buffer, 0, read, Charset.defaultCharset())
                            outputBuffer.append(text.take(remaining))
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        outputDrain.start()

        val boundedTimeoutMs = timeoutMs.coerceAtLeast(1_000L)
        val finished = process.waitFor(boundedTimeoutMs, TimeUnit.MILLISECONDS)
        if (!finished) {
            process.destroyForcibly()
            outputDrain.join(1000)
            val error = IllegalStateException(
                "FFmpeg timed out after ${boundedTimeoutMs}ms: ${redactFfmpegArgs(commands)}\n" +
                    redactFfmpegOutput(outputBuffer.toString())
            )
            Log.w(FFMPEG_TAG, error.message.orEmpty())
            CrashReporter.recordHandled(appContext, "FFmpeg timeout", error)
            return -1
        }

        val exitCode = process.exitValue()
        outputDrain.join(1000)
        if (exitCode != 0) {
            Log.w(
                FFMPEG_TAG,
                "FFmpeg failed rc=$exitCode command=${redactFfmpegArgs(commands)} " +
                    "output=${redactFfmpegOutput(outputBuffer.toString()).take(2000)}"
            )
        }
        exitCode
    } catch (t: Throwable) {
        t.printStackTrace()
        CrashReporter.recordHandled(appContext, "FFmpeg execute", t)
        -1
    }
}

private fun installFfmpegPackage(context: Context) {
    val nativeDir = File(context.applicationInfo.nativeLibraryDir)
    val packageZip = File(nativeDir, "libffmpeg.zip.so")
    val executable = File(nativeDir, "libffmpeg.so")
    if (!packageZip.exists() || packageZip.length() <= 0L || !executable.exists()) {
        throw IllegalStateException("FFmpeg native package is missing")
    }

    val packageDir = ffmpegPackageDir(context)
    val packageVersion = packageZip.length().toString()
    val prefs = context.getSharedPreferences(FFMPEG_PACKAGE_PREFS, Context.MODE_PRIVATE)
    if (packageDir.exists() && prefs.getString(PREF_FFMPEG_PACKAGE_VERSION, null) == packageVersion) return

    packageDir.deleteRecursively()
    packageDir.mkdirs()
    unzipSafely(packageZip, packageDir)
    prefs.edit().putString(PREF_FFMPEG_PACKAGE_VERSION, packageVersion).apply()
}

private fun unzipSafely(zipFile: File, destDir: File) {
    val canonicalDest = destDir.canonicalFile
    ZipInputStream(FileInputStream(zipFile)).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            val outputFile = File(destDir, entry.name).canonicalFile
            if (!outputFile.path.startsWith(canonicalDest.path + File.separator)) {
                throw IllegalStateException("Blocked unsafe FFmpeg package entry: ${entry.name}")
            }

            if (entry.isDirectory) {
                outputFile.mkdirs()
            } else {
                outputFile.parentFile?.mkdirs()
                outputFile.outputStream().use { output -> zip.copyTo(output) }
                outputFile.setExecutable(true, false)
            }
            zip.closeEntry()
        }
    }
}

private fun ffmpegPackageDir(context: Context): File =
    File(context.noBackupFilesDir, "$FFMPEG_PACKAGE_ROOT/packages/ffmpeg")

private fun redactFfmpegArgs(commands: Array<String>): String =
    commands.joinToString(" ") { arg ->
        when {
            arg.startsWith("http://", ignoreCase = true) || arg.startsWith("https://", ignoreCase = true) ->
                "<url:${runCatching { java.net.URI(arg).host }.getOrNull().orEmpty().ifBlank { "remote" }}>"
            arg.length > 180 -> arg.take(180) + "..."
            else -> arg
        }
    }

private fun redactFfmpegOutput(output: String): String =
    output.replace(Regex("https?://\\S+"), "<url>")
