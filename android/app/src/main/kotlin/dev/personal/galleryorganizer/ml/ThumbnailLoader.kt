package dev.personal.galleryorganizer.ml

import android.content.ContentResolver
import android.content.ContentUris
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.provider.MediaStore
import android.util.Size

/**
 * Loads a downscaled, EXIF-oriented image via the system thumbnailer —
 * never decodes the full original. Always returns a software ARGB_8888
 * bitmap (pixel access + ML Kit need that).
 */
class ThumbnailLoader(private val resolver: ContentResolver) {
    fun load(assetId: Long, sizePx: Int): Bitmap {
        val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, assetId)
        val bmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            resolver.loadThumbnail(uri, Size(sizePx, sizePx), null)
        } else {
            decodeSampled(uri, sizePx)
        }
        return toSoftware(bmp)
    }

    private fun decodeSampled(uri: android.net.Uri, sizePx: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= sizePx && bounds.outHeight / (sample * 2) >= sizePx) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: error("decode failed")
    }

    private fun toSoftware(bmp: Bitmap): Bitmap {
        if (bmp.config == Bitmap.Config.ARGB_8888) return bmp
        val copy = bmp.copy(Bitmap.Config.ARGB_8888, false)
        bmp.recycle()
        return copy
    }

    companion object {
        /** Center square crop scaled to [size]×[size]. */
        fun centerSquare(src: Bitmap, size: Int): Bitmap {
            val (x, y, side) = Preprocess.centerSquare(src.width, src.height)
            val cropped = Bitmap.createBitmap(src, x, y, side, side)
            val scaled = Bitmap.createScaledBitmap(cropped, size, size, true)
            if (cropped !== src && cropped !== scaled) cropped.recycle()
            return scaled
        }
    }
}
