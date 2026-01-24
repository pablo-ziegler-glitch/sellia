package com.example.selliaapp.ui.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore

fun exportTemplateToDownloads(
    context: Context,
    fileName: String,
    mimeType: String,
    content: String
): Uri? {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Downloads.DISPLAY_NAME, fileName)
        put(MediaStore.Downloads.MIME_TYPE, mimeType)
        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
    }

    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
    val success = runCatching {
        resolver.openOutputStream(uri)?.use { stream ->
            stream.write(content.toByteArray())
        } ?: false
    }.getOrDefault(false)

    if (!success) {
        resolver.delete(uri, null, null)
        return null
    }

    return uri
}
