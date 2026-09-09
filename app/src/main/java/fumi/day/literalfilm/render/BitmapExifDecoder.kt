package fumi.day.literalfilm.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface

/**
 * Decodes a shared image URI into a Bitmap with EXIF rotation baked in.
 * Neither ImageDecoder nor BitmapFactory auto-rotates, so both paths apply
 * the same explicit matrix step after decoding.
 */
object BitmapExifDecoder {

    fun decode(context: Context, uri: Uri, maxDimension: Int?): Bitmap {
        val bitmap = decodeBitmap(context, uri, maxDimension)
        val matrix = readOrientationMatrix(context, uri)
        if (matrix.isIdentity) return bitmap

        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    private fun decodeBitmap(context: Context, uri: Uri, maxDimension: Int?): Bitmap =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                // GLUtils.texImage2D can't read pixels from a Config.HARDWARE bitmap
                // (ImageDecoder's default allocator), so force a software-backed decode.
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE)
                if (maxDimension != null) {
                    val scale = computeScale(info.size.width, info.size.height, maxDimension)
                    if (scale < 1.0) {
                        decoder.setTargetSize(
                            (info.size.width * scale).toInt().coerceAtLeast(1),
                            (info.size.height * scale).toInt().coerceAtLeast(1),
                        )
                    }
                }
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }

            val sampleSize = if (maxDimension != null) computeInSampleSize(bounds.outWidth, bounds.outHeight, maxDimension) else 1
            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decodeOptions) }
                ?: error("Unable to decode image: $uri")
        }

    private fun computeScale(width: Int, height: Int, maxDimension: Int): Double {
        val longest = maxOf(width, height)
        return if (longest <= maxDimension) 1.0 else maxDimension.toDouble() / longest
    }

    private fun computeInSampleSize(width: Int, height: Int, maxDimension: Int): Int {
        var sampleSize = 1
        val longest = maxOf(width, height)
        while (longest / (sampleSize * 2) >= maxDimension) sampleSize *= 2
        return sampleSize
    }

    private fun readOrientationMatrix(context: Context, uri: Uri): Matrix {
        val orientation =
            context.contentResolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
        }
        return matrix
    }
}
