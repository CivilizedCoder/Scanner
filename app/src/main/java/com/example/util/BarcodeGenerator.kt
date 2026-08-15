package com.example.util

import android.graphics.Bitmap
import android.graphics.Color
import android.util.LruCache
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object BarcodeGenerator {
    // Cache up to 100 generated barcode bitmaps to prevent repetitive ashmem / graphics allocations
    private val memoryCache = LruCache<String, Bitmap>(100)

    suspend fun generateBarcodeBitmap(
        content: String,
        format: BarcodeFormat = BarcodeFormat.CODE_128,
        width: Int = 500,
        height: Int = 160
    ): Bitmap? = withContext(Dispatchers.Default) {
        if (content.isBlank()) return@withContext null
        val cacheKey = "${content}_${format.name}_${width}x${height}"
        val cached = memoryCache.get(cacheKey)
        if (cached != null && !cached.isRecycled) {
            return@withContext cached
        }

        val bitmap = try {
            val bitMatrix: BitMatrix = MultiFormatWriter().encode(content, format, width, height)
            createBitmapFromMatrix(bitMatrix)
        } catch (_: Exception) {
            // Fallback for formats if Code 128 fails for arbitrary strings
            try {
                val bitMatrix: BitMatrix = MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, width, width)
                createBitmapFromMatrix(bitMatrix)
            } catch (_: Exception) {
                null
            }
        }

        if (bitmap != null) {
            memoryCache.put(cacheKey, bitmap)
        }
        bitmap
    }

    private fun createBitmapFromMatrix(bitMatrix: BitMatrix): Bitmap {
        val width = bitMatrix.width
        val height = bitMatrix.height
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val offset = y * width
            for (x in 0 until width) {
                pixels[offset + x] = if (bitMatrix[x, y]) Color.BLACK else Color.WHITE
            }
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }
}

