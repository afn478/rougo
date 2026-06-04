package com.selxo.rougo

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.ui.unit.sp
import java.io.File
import java.util.Locale

fun parseSimpleSubtitles(context: Context, uri: Uri): List<SubtitleCue> {
    val cues = mutableListOf<SubtitleCue>()
    try {
        val inputStream = if (uri.scheme == "file") File(uri.path!!).inputStream() else context.contentResolver.openInputStream(uri)
        inputStream?.bufferedReader()?.use { reader ->
            val timeRegex = Regex("(\\d{2}:\\d{2}:\\d{2}[.,]\\d{3})\\s*-->\\s*(\\d{2}:\\d{2}:\\d{2}[.,]\\d{3})")
            val vttTimeRegex = Regex("(\\d{2}:\\d{2}[.,]\\d{3})\\s*-->\\s*(\\d{2}:\\d{2}[.,]\\d{3})")

            var currentStart = -1L
            var currentEnd = -1L
            val currentText = StringBuilder()

            var startIndex = 1
            var endIndex = 2
            var textIndex = 9

            reader.forEachLine { line ->
                val trimmed = line.trim()

                if (trimmed == "WEBVTT" || trimmed.startsWith("Language:") || trimmed.startsWith("Kind:") || trimmed.startsWith("Style:")) return@forEachLine

                if (trimmed.startsWith("Format:")) {
                    val formatParts = trimmed.substringAfter("Format:").split(",").map { it.trim() }
                    startIndex = formatParts.indexOf("Start").takeIf { it >= 0 } ?: startIndex
                    endIndex = formatParts.indexOf("End").takeIf { it >= 0 } ?: endIndex
                    textIndex = formatParts.indexOf("Text").takeIf { it >= 0 } ?: textIndex
                    return@forEachLine
                }

                if (trimmed.startsWith("Dialogue:")) {
                    val parts = trimmed.substringAfter("Dialogue:").split(",", limit = textIndex + 1)
                    if (parts.size > textIndex) {
                        val startMs = parseTimeMs(parts[startIndex].trim())
                        val endMs = parseTimeMs(parts[endIndex].trim())
                        val text = parts[textIndex].trim().replace(Regex("\\{.*?\\}"), "").replace("\\N", "\n")
                        if (text.isNotBlank()) cues.add(SubtitleCue(startMs, endMs, text))
                    }
                    return@forEachLine
                }

                val timeMatch = timeRegex.find(trimmed) ?: vttTimeRegex.find(trimmed)
                if (timeMatch != null) {
                    if (currentStart != -1L) {
                        cues.add(SubtitleCue(currentStart, currentEnd, currentText.toString().trim()))
                        currentText.clear()
                    }
                    var startStr = timeMatch.groupValues[1]
                    var endStr = timeMatch.groupValues[2]
                    if (startStr.length == 9) startStr = "00:$startStr"
                    if (endStr.length == 9) endStr = "00:$endStr"
                    currentStart = parseTimeMs(startStr)
                    currentEnd = parseTimeMs(endStr)
                } else if (trimmed.isNotBlank() && !trimmed.matches(Regex("^\\d+$"))) {
                    currentText.append(trimmed.replace(Regex("<[^>]*>"), "")).append("\n")
                }
            }
            if (currentStart != -1L && currentText.isNotBlank()) {
                cues.add(SubtitleCue(currentStart, currentEnd, currentText.toString().trim()))
            }
        }
    } catch (e: Exception) { e.printStackTrace() }
    return cues
}
fun findSubtitleCue(cues: List<SubtitleCue>, timeMs: Long): SubtitleCue? {
    var low = 0
    var high = cues.lastIndex
    while (low <= high) {
        val mid = (low + high) ushr 1
        val cue = cues[mid]
        when {
            timeMs < cue.startMs -> high = mid - 1
            timeMs > cue.endMs -> low = mid + 1
            else -> return cue
        }
    }
    return null
}
fun parseTimeMs(timeStr: String): Long {
    try {
        val parts = timeStr.replace(",", ".").split(":", ".")
        if (parts.size >= 3) {
            val h = parts[0].toLong() * 3600000
            val m = parts[1].toLong() * 60000
            val s = parts[2].toLong() * 1000
            var ms = if (parts.size >= 4) parts[3].toLong() else 0L
            if (parts.size >= 4 && parts[3].length == 2) ms *= 10
            else if (parts.size >= 4 && parts[3].length == 1) ms *= 100
            return h + m + s + ms
        }
    } catch (e: Exception) {}
    return 0L
}
fun formatTime(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSeconds = ms / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) String.format(Locale.US, "%02d:%02d:%02d", h, m, s) else String.format(Locale.US, "%02d:%02d", m, s)
}
fun getFileName(context: Context, uri: Uri): String {
    var name = ""
    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx != -1) name = cursor.getString(idx)
            }
        }
    } catch (e: Exception) {}
    return name.ifEmpty { "Unknown Media" }
}
