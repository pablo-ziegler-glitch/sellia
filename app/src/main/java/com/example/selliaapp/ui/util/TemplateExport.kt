package com.example.selliaapp.ui.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore

fun exportContentToDownloads(
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
            true
        } ?: false
    }.getOrDefault(false)

    if (!success) {
        resolver.delete(uri, null, null)
        return null
    }

    return uri
}

fun exportTemplateToDownloads(
    context: Context,
    fileName: String,
    mimeType: String,
    content: String
): Uri? = exportContentToDownloads(context, fileName, mimeType, content)

fun shareExportedFile(
    context: Context,
    uri: Uri,
    mimeType: String,
    title: String
) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(shareIntent, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching {
        context.startActivity(chooser)
    }
}
