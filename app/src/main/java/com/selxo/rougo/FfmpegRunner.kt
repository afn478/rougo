package com.selxo.rougo

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipInputStream

private const val FFMPEG_PACKAGE_PREFS = "rougo_ffmpeg"
private const val PREF_FFMPEG_PACKAGE_VERSION = "ffmpeg_package_version"
private const val FFMPEG_PACKAGE_ROOT = "rougo-ffmpeg"

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

internal fun executeFfmpeg(context: Context, commands: Array<String>): Int {
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
        val outputDrain = Thread {
            try {
                process.inputStream.use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (input.read(buffer) >= 0) {
                        // Drain FFmpeg output so the process cannot block on a full pipe.
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        outputDrain.start()

        val exitCode = process.waitFor()
        outputDrain.join(1000)
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
