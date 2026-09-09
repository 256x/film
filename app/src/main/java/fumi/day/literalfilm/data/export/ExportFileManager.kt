package fumi.day.literalfilm.data.export

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import java.io.File

private const val EXPORT_SUBDIR = "export"
private const val FILE_PROVIDER_AUTHORITY = "fumi.day.literalfilm.fileprovider"

/**
 * Writes a processed Bitmap to a private cache subfolder (never MediaStore) and hands it
 * out via FileProvider for the share-sheet "エクスポート" flow (e.g. KDE Connect to a PC).
 *
 * Android gives no callback for "the receiving app finished reading the file" after
 * startActivity(chooser) returns — that call returns as soon as the chooser launches, not
 * when the transfer completes. Deleting immediately would race a legitimate in-progress
 * read. [cleanupStaleExports] instead wipes the folder on the *next* app start, which is
 * the closest approximation Android's model allows to "doesn't stay on the phone" — not a
 * guarantee the file vanishes the moment the share completes.
 */
object ExportFileManager {

    fun exportDir(context: Context): File = File(context.cacheDir, EXPORT_SUBDIR).apply { mkdirs() }

    fun writeExportFile(context: Context, bitmap: Bitmap): File {
        val file = File(exportDir(context), timestampedFilename("jpg"))
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        return file
    }

    fun buildShareIntent(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(context, FILE_PROVIDER_AUTHORITY, file)
        val sendIntent =
            Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        return Intent.createChooser(sendIntent, null)
    }

    /** Call once on cold start, before any new export is written. */
    fun cleanupStaleExports(context: Context) {
        exportDir(context).listFiles()?.forEach { it.delete() }
    }
}
