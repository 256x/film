package fumi.day.literalfilm.data.export

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

private const val ALBUM_NAME = "LiteralFilm"

/**
 * Saves a processed Bitmap into the device's public gallery under Pictures/LiteralFilm.
 * API29+ uses scoped storage (RELATIVE_PATH + IS_PENDING), no permission needed.
 * API26-28 has no RELATIVE_PATH column and needs the legacy WRITE_EXTERNAL_STORAGE
 * runtime permission (declared with maxSdkVersion=28 in the manifest) — this branch
 * is unverified on real hardware since the dev device runs API 33.
 */
object GallerySaver {

    fun save(context: Context, bitmap: Bitmap): Uri {
        val resolver = context.contentResolver
        val filename = timestampedFilename("jpg")

        val values =
            ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/$ALBUM_NAME")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("MediaStore insert failed")

        writeBitmap(resolver, uri, bitmap)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val pendingDone = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
            resolver.update(uri, pendingDone, null, null)
        }

        return uri
    }

    private fun writeBitmap(resolver: ContentResolver, uri: Uri, bitmap: Bitmap) {
        val stream = resolver.openOutputStream(uri) ?: error("Unable to open output stream for $uri")
        stream.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
    }
}
