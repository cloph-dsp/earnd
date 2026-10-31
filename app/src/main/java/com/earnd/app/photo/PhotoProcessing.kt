package com.earnd.app.photo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.File
import java.io.FileOutputStream

/** Downscales a raw capture so its long edge is at most [MAX_EDGE], applies EXIF rotation, writes JPEG. */
internal object PhotoProcessing {
    const val MAX_EDGE = 1080
    const val JPEG_QUALITY = 75

    fun downscale(src: File, dst: File, maxEdge: Int = MAX_EDGE, quality: Int = JPEG_QUALITY) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(src.path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxEdge) sample *= 2
        val decoded = BitmapFactory.decodeFile(src.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: error("Could not decode capture")
        val orientation = ExifInterface(src.path)
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        val rotation = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        val scale = minOf(1f, maxEdge.toFloat() / maxOf(decoded.width, decoded.height))
        val m = Matrix().apply { postScale(scale, scale); postRotate(rotation) }
        val out = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, m, true)
        FileOutputStream(dst).use { out.compress(Bitmap.CompressFormat.JPEG, quality, it) }
        if (out !== decoded) out.recycle()
        decoded.recycle()
    }
}
