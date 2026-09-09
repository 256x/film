package fumi.day.literalfilm.data.export

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val FILENAME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")

/** e.g. "lf_20260907195012.jpg" — shared by [GallerySaver] and [ExportFileManager]. */
internal fun timestampedFilename(extension: String): String = "lf_${LocalDateTime.now().format(FILENAME_FORMATTER)}.$extension"
