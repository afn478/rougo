package com.selxo.rougo

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.util.Size
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun exportRecording(context: Context, file: File) {
    try {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/mp4"
            val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Segment"))
    } catch (e: Exception) {
        try {
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "RougoShare_${System.currentTimeMillis()}.m4a")
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "audio/mp4")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_MUSIC + "/Rougo")
                }
            }
            val uri = context.contentResolver.insert(android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    file.inputStream().use { input ->
                        input.copyTo(out)
                    }
                }
                Toast.makeText(context, "Exported to Music/Rougo!", Toast.LENGTH_SHORT).show()

                val viewIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "audio/mp4"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(viewIntent, "Share Segment"))
            }
        } catch (ex: Exception) {
            Toast.makeText(context, "Export failed: ${ex.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
@Composable
fun loadAlbumArt(
    context: Context,
    uriString: String,
    isVideo: Boolean,
    cachedCoverPath: String? = null,
    itemId: String? = null,
    sourceUrl: String? = null
): ImageBitmap? {
    val savedCoverPath = cachedCoverPath?.takeIf { File(it).exists() && File(it).length() > 0L }
    val remoteThumbnailUrl = if (isVideo) youtubeThumbnailUrl(sourceUrl) else null
    val cachedRemoteCoverPath = if (savedCoverPath == null) cachedCoverPathForItem(context, itemId) else null
    val cacheKey = savedCoverPath ?: cachedRemoteCoverPath ?: remoteThumbnailUrl ?: uriString
    var bitmap by remember(cacheKey) { mutableStateOf<ImageBitmap?>(ImageCache.cache[cacheKey]) }

    LaunchedEffect(uriString, cachedCoverPath, itemId, sourceUrl) {
        if (bitmap != null) return@LaunchedEffect

        val loadedImage = withContext(Dispatchers.IO) {
            savedCoverPath
                ?.let { decodeSampledBitmapFile(it)?.asImageBitmap() }
                ?: cachedRemoteCoverPath?.let { decodeSampledBitmapFile(it)?.asImageBitmap() }
                ?: remoteThumbnailUrl?.let { thumbnailUrl ->
                    val coverId = itemId?.takeIf { it.isNotBlank() } ?: "thumb_${thumbnailUrl.hashCode()}"
                    downloadRemoteCover(context, coverId, thumbnailUrl)
                        ?.let { decodeSampledBitmapFile(it)?.asImageBitmap() }
                }
                ?: if (sourceUrl != null && !isLocalMediaUriValue(uriString)) {
                    null
                } else {
                    loadAlbumArtFromMedia(context, uriString, isVideo)
                }
        }

        if (loadedImage != null) {
            ImageCache.cache[cacheKey] = loadedImage
            bitmap = loadedImage
        }
    }
    return bitmap
}
private fun loadAlbumArtFromMedia(context: Context, uriString: String, isVideo: Boolean): ImageBitmap? {
    val uri = Uri.parse(uriString)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        try {
            return context.contentResolver.loadThumbnail(uri, Size(800, 800), null).asImageBitmap()
        } catch (e: Throwable) { }
    }

    var retriever: MediaMetadataRetriever? = null
    var pfd: ParcelFileDescriptor? = null
    return try {
        retriever = MediaMetadataRetriever()
        if (uri.scheme == "file") {
            retriever.setDataSource(File(uri.path ?: "").absolutePath)
        } else {
            pfd = context.contentResolver.openFileDescriptor(uri, "r")
            if (pfd != null) retriever.setDataSource(pfd.fileDescriptor)
        }

        if (isVideo) {
            val duration = retriever.cleanMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val randomTimeUs = if (duration > 60000L) {
                (duration * 1000 * (0.1 + Math.random() * 0.8)).toLong()
            } else {
                10_000_000L
            }
            retriever.getFrameAtTime(randomTimeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?.asImageBitmap()
        } else {
            retriever.embeddedPicture
                ?.let { bytes ->
                    val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOptions)
                    var scale = 1
                    while (boundsOptions.outWidth / scale > 1024 || boundsOptions.outHeight / scale > 1024) scale *= 2
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = scale })
                        ?.asImageBitmap()
                }
        }
    } catch (e: Throwable) {
        e.printStackTrace()
        null
    } finally {
        try { retriever?.release() } catch (e: Throwable) {}
        try { pfd?.close() } catch (e: Throwable) {}
    }
}
