package com.humayra.goddess.storage

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object FileImportUtils {
    fun getDisplayName(ctx: Context, uri: Uri): String {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) return c.getString(0) ?: "file"
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "imported_file"
    }

    suspend fun copyToInternal(ctx: Context, uri: Uri, subDir: String): File = withContext(Dispatchers.IO) {
        val dir = File(ctx.filesDir, subDir).apply { mkdirs() }
        val safeName = getDisplayName(ctx, uri).replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val dest = File(dir, safeName)
        ctx.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { out -> input.copyTo(out, 65536) }
        } ?: error("ফাইল পড়া যায়নি")
        dest
    }
}
