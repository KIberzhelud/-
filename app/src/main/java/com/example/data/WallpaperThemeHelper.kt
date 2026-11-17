package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.example.model.WallpaperPaletteData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object WallpaperThemeHelper {

    private const val WALLPAPER_DIR = "wallpapers"
    private const val WALLPAPER_FILENAME = "vista_wallpaper_current.jpg"

    suspend fun importWallpaperFromUri(context: Context, sourceUri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(sourceUri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOpts)
            }
            val maxDim = maxOf(boundsOpts.outWidth, boundsOpts.outHeight).coerceAtLeast(1)
            val sampleSize = (maxDim / 1600).coerceAtLeast(1)

            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = context.contentResolver.openInputStream(sourceUri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            } ?: throw IllegalStateException("Не удалось прочитать изображение обоев")

            val scaled = scaleBitmapMax(decoded, 1600)
            if (scaled !== decoded) {
                decoded.recycle()
            }

            val dir = File(context.filesDir, WALLPAPER_DIR).apply { mkdirs() }
            val outFile = File(dir, WALLPAPER_FILENAME)
            FileOutputStream(outFile).use { fos ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 90, fos)
            }
            scaled.recycle()
            outFile.absolutePath
        }
    }

    suspend fun loadWallpaperAndPalette(path: String?): Pair<ImageBitmap?, WallpaperPaletteData?> =
        withContext(Dispatchers.IO) {
            if (path.isNullOrBlank()) return@withContext null to null
            val file = File(path)
            if (!file.exists() || !file.canRead()) return@withContext null to null

            val bmp = runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
                ?: return@withContext null to null
            val palette = extractPaletteFromBitmap(bmp)
            bmp.asImageBitmap() to palette
        }

    fun deleteWallpaperFile(path: String?) {
        if (!path.isNullOrBlank()) {
            runCatching { File(path).delete() }
        }
    }

    /**
     * Pure Kotlin adaptive Vista Aero color extractor:
     * downsamples bitmap to 32x32 and computes a rich dark control base + vivid Aero accent + crisp rim highlight.
     */
    fun extractPaletteFromBitmap(bitmap: Bitmap): WallpaperPaletteData {
        val sample = Bitmap.createScaledBitmap(bitmap, 32, 32, true)
        var sumR = 0.0
        var sumG = 0.0
        var sumB = 0.0
        var count = 0

        var bestVibrantScore = -1f
        var bestAccentColor = 0xFF4CA8FF.toInt()
        val hsv = FloatArray(3)

        for (y in 0 until sample.height) {
            for (x in 0 until sample.width) {
                val px = sample.getPixel(x, y)
                val r = (px shr 16) and 0xFF
                val g = (px shr 8) and 0xFF
                val b = px and 0xFF
                sumR += r
                sumG += g
                sumB += b
                count++

                android.graphics.Color.RGBToHSV(r, g, b, hsv)
                val sat = hsv[1]
                val valBr = hsv[2]
                // Prefer colorful, mid-to-bright pixels for accent highlight
                val vibrancy = sat * 2.2f + (if (valBr in 0.25f..0.92f) 0.7f else 0.15f)
                if (vibrancy > bestVibrantScore) {
                    bestVibrantScore = vibrancy
                    bestAccentColor = px
                }
            }
        }
        if (sample !== bitmap) {
            sample.recycle()
        }

        val avgR = (sumR / count.coerceAtLeast(1)).toInt().coerceIn(0, 255)
        val avgG = (sumG / count.coerceAtLeast(1)).toInt().coerceIn(0, 255)
        val avgB = (sumB / count.coerceAtLeast(1)).toInt().coerceIn(0, 255)

        val darkHsv = FloatArray(3)
        android.graphics.Color.RGBToHSV(avgR, avgG, avgB, darkHsv)
        darkHsv[1] = darkHsv[1].coerceIn(0.18f, 0.72f)
        darkHsv[2] = darkHsv[2].coerceIn(0.12f, 0.24f)
        val dominantDark = android.graphics.Color.HSVToColor(0xFF, darkHsv)

        val accentHsv = FloatArray(3)
        android.graphics.Color.colorToHSV(bestAccentColor, accentHsv)
        if (accentHsv[1] < 0.15f) {
            // Neutral monochrome wallpaper -> classy silver-cyan Aero tint
            accentHsv[1] = 0.12f
        } else {
            accentHsv[1] = accentHsv[1].coerceIn(0.38f, 0.88f)
        }
        accentHsv[2] = accentHsv[2].coerceIn(0.68f, 0.96f)
        val accent = android.graphics.Color.HSVToColor(0xFF, accentHsv)

        val highlightHsv = accentHsv.copyOf()
        highlightHsv[1] = (highlightHsv[1] * 0.55f).coerceIn(0.08f, 0.6f)
        highlightHsv[2] = 0.98f
        val highlight = android.graphics.Color.HSVToColor(0xFF, highlightHsv)

        return WallpaperPaletteData(
            dominantDarkArgb = dominantDark.toLong() and 0xFFFFFFFFL,
            accentArgb = accent.toLong() and 0xFFFFFFFFL,
            highlightArgb = highlight.toLong() and 0xFFFFFFFFL
        )
    }

    private fun scaleBitmapMax(bmp: Bitmap, maxDim: Int): Bitmap {
        if (bmp.width <= maxDim && bmp.height <= maxDim) return bmp
        val ratio = minOf(maxDim.toFloat() / bmp.width, maxDim.toFloat() / bmp.height)
        val w = (bmp.width * ratio).toInt().coerceAtLeast(1)
        val h = (bmp.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bmp, w, h, true)
    }
}
