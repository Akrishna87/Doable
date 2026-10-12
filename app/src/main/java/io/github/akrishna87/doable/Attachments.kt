package io.github.akrishna87.doable

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import io.github.akrishna87.doable.model.Attachment
import io.github.akrishna87.doable.model.newId
import java.io.File

/**
 * Files kept with comments. Each is copied into the app's own storage (files/attachments), so it
 * stays even if the original is moved or deleted, and is opened through a FileProvider.
 * Backups (JSON) don't include these files.
 */
object Attachments {
    private const val DIR = "attachments"

    private fun authority(context: Context) = context.packageName + ".files"

    fun file(context: Context, a: Attachment): File = File(context.filesDir, a.file)

    /** Copies what [uri] points to into the app; null if it can't be read. */
    fun copy(context: Context, uri: Uri): Attachment? = runCatching {
        val resolver = context.contentResolver
        var name = "file"
        var size = -1L
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getString(0)?.let { name = it }
                if (!c.isNull(1)) size = c.getLong(1)
            }
        }
        val safe = name.replace(Regex("[^\\p{L}\\p{N}._ -]"), "_").take(80).ifBlank { "file" }
        val rel = "$DIR/${newId().take(8)}-$safe"
        val out = File(context.filesDir, rel)
        out.parentFile?.mkdirs()
        resolver.openInputStream(uri)!!.use { input -> out.outputStream().use { input.copyTo(it) } }
        Attachment(rel, name, resolver.getType(uri) ?: guessMime(name), if (size >= 0) size else out.length())
    }.getOrNull()

    /** A new empty file for the camera to write a photo into, and the uri to give it. */
    fun newPhoto(context: Context): Pair<Attachment, Uri> {
        val name = "Photo ${java.time.LocalDateTime.now().toString().take(16).replace('T', ' ')}.jpg"
        val rel = "$DIR/${newId().take(8)}-photo.jpg"
        val f = File(context.filesDir, rel)
        f.parentFile?.mkdirs()
        f.createNewFile()
        return Attachment(rel, name, "image/jpeg", 0) to FileProvider.getUriForFile(context, authority(context), f)
    }

    /** The photo once the camera has written it, with its size; null if nothing was taken. */
    fun finished(context: Context, a: Attachment): Attachment? {
        val f = file(context, a)
        return if (f.exists() && f.length() > 0) a.copy(size = f.length()) else null.also { f.delete() }
    }

    fun open(context: Context, a: Attachment): Boolean {
        val f = file(context, a)
        if (!f.exists()) return false
        val uri = FileProvider.getUriForFile(context, authority(context), f)
        return try {
            context.startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_VIEW).setDataAndType(uri, a.mime.ifEmpty { "*/*" }).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                    a.name,
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    fun delete(context: Context, a: Attachment) {
        runCatching { file(context, a).delete() }
    }

    /** A small version of an image attachment for the comment list. */
    fun thumbnail(context: Context, a: Attachment, maxSide: Int = 480): Bitmap? = runCatching {
        val path = file(context, a).path
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxSide || bounds.outHeight / (sample * 2) >= maxSide) sample *= 2
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()

    fun sizeLabel(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> String.format(java.util.Locale.ROOT, "%.1f MB", bytes / 1024.0 / 1024.0)
    }

    private fun guessMime(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "pdf" -> "application/pdf"
        "txt" -> "text/plain"
        else -> "application/octet-stream"
    }
}
