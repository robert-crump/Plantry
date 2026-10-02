package com.example.plantry.data

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Turns a camera or gallery image into a JPEG of about [TARGET_BYTES]: scaled so its long edge is
 * at most [MAX_EDGE_PX] (the most Claude uses without downscaling itself), then compressed with
 * the highest quality that fits.
 */
class PhotoCompressor(private val contentResolver: ContentResolver) {

    /** Null when the image cannot be read. */
    suspend fun compress(uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
        val bitmap = try {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val longEdge = max(info.size.width, info.size.height)
                if (longEdge > MAX_EDGE_PX) {
                    val scale = MAX_EDGE_PX.toDouble() / longEdge
                    decoder.setTargetSize(
                        (info.size.width * scale).roundToInt(),
                        (info.size.height * scale).roundToInt(),
                    )
                }
            }
        } catch (_: Exception) {
            return@withContext null
        }
        try {
            encode(bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    private fun encode(bitmap: Bitmap): ByteArray {
        var current = bitmap
        while (true) {
            for (quality in QUALITIES) {
                val bytes = ByteArrayOutputStream().use { out ->
                    current.compress(Bitmap.CompressFormat.JPEG, quality, out)
                    out.toByteArray()
                }
                if (bytes.size <= TARGET_BYTES || (quality == QUALITIES.last() && current.width <= MIN_EDGE_PX)) {
                    if (current !== bitmap) current.recycle()
                    return bytes
                }
            }
            val scaled = Bitmap.createScaledBitmap(current, (current.width * 0.8).roundToInt(), (current.height * 0.8).roundToInt(), true)
            if (current !== bitmap) current.recycle()
            current = scaled
        }
    }

    companion object {
        const val MAX_EDGE_PX = 1568
        const val TARGET_BYTES = 200 * 1024
        private const val MIN_EDGE_PX = 600
        private val QUALITIES = listOf(85, 75, 65, 55)
    }
}
