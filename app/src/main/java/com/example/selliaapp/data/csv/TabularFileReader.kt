package com.example.selliaapp.data.csv

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.annotation.VisibleForTesting
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.InputStream
import java.util.Locale

/**
 * Utilidad interna para leer archivos tabulares (CSV/Excel/Sheets) y convertirlos en una tabla
 * de strings. Normaliza filas eliminando espacios vacíos al inicio/fin para facilitar el parsing
 * por encabezados.
 */
object TabularFileReader {

    private val CSV_EXTENSIONS = setOf("csv", "tsv")
    private val SPREADSHEET_EXTENSIONS = setOf("xls", "xlsx", "xlsm", "gsheet")
    private val CSV_MIME_HINTS = listOf("text/csv", "text/comma-separated-values", "application/csv")
    private val SHEET_MIME_HINTS = listOf("spreadsheet", "excel")

    fun readAll(resolver: ContentResolver, uri: Uri): List<List<String>> {
        val mime = resolver.getType(uri)?.lowercase(Locale.ROOT).orEmpty()
        val displayName = queryDisplayName(resolver, uri)
        val extension = guessExtension(mime, displayName)

        val table = when {
            isCsv(extension, mime) -> resolver.openRequired(uri).use { CsvUtils.readAll(it) }
            isSpreadsheet(extension, mime) -> resolver.openRequired(uri).use { stream ->
                readSpreadsheet(stream)
            }
            else -> {
                val csvAttempt = runCatching {
                    resolver.openRequired(uri).use { CsvUtils.readAll(it) }
                }.getOrNull()
                if (!csvAttempt.isNullOrEmpty()) {
                    csvAttempt
                } else {
                    val sheetAttempt = runCatching {
                        resolver.openRequired(uri).use { stream -> readSpreadsheet(stream) }
                    }.getOrNull()
                    sheetAttempt ?: csvAttempt ?: emptyList()
                }
            }
        }

        if (table.isEmpty()) {
            throw IllegalArgumentException("Archivo vacío o sin datos")
        }

        return table.normalize()
    }

    private fun List<List<String>>.normalize(): List<List<String>> {
        if (isEmpty()) return this

        val trimmedStart = dropWhile { row -> row.isEmpty() || row.all { it.isBlank() } }
        val cleaned = trimmedStart.map { row ->
            val mutable = row.toMutableList()
            while (mutable.isNotEmpty() && mutable.last().isBlank()) {
                mutable.removeAt(mutable.lastIndex)
            }
            mutable.toList()
        }
        return cleaned.dropLastWhile { row -> row.isEmpty() || row.all { it.isBlank() } }
    }

    private fun guessExtension(mime: String, displayName: String?): String? {
        val fromMime = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
        if (!fromMime.isNullOrBlank()) return fromMime.lowercase(Locale.ROOT)
        val fromName = displayName
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.lowercase(Locale.ROOT)
        return fromName?.takeIf { it.isNotBlank() }
    }

    private fun queryDisplayName(resolver: ContentResolver, uri: Uri): String? {
        return resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor: Cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx != -1 && cursor.moveToFirst()) cursor.getString(idx) else null
            }
    }

    private fun isCsv(extension: String?, mime: String): Boolean {
        if (extension != null && extension in CSV_EXTENSIONS) return true
        return CSV_MIME_HINTS.any { hint -> hint in mime }
    }

    private fun isSpreadsheet(extension: String?, mime: String): Boolean {
        if (extension != null && extension in SPREADSHEET_EXTENSIONS) return true
        return SHEET_MIME_HINTS.any { hint -> hint in mime }
    }

    private fun ContentResolver.openRequired(uri: Uri): InputStream {
        return openInputStream(uri) ?: throw IllegalStateException("No se pudo abrir el archivo")
    }

    @VisibleForTesting
    internal fun readSpreadsheet(input: InputStream): List<List<String>> {
        val workbook = WorkbookFactory.create(input)
        workbook.use { wb ->
            if (wb.numberOfSheets <= 0) return emptyList()
            val sheet = wb.getSheetAt(0) ?: return emptyList()
            val formatter = DataFormatter()
            val evaluator = wb.creationHelper.createFormulaEvaluator()
            val rows = mutableListOf<List<String>>()
            val lastRow = sheet.lastRowNum
            for (rowIndex in 0..lastRow) {
                val row = sheet.getRow(rowIndex) ?: continue
                val lastCellIndex = row.lastCellNum.toInt().coerceAtLeast(0)
                if (lastCellIndex == 0) {
                    rows += emptyList<String>()
                    continue
                }
                val values = MutableList(lastCellIndex) { cellIdx ->
                    val cell = row.getCell(cellIdx)
                    if (cell != null) formatter.formatCellValue(cell, evaluator) else ""
                }
                while (values.isNotEmpty() && values.last().isBlank()) {
                    values.removeAt(values.lastIndex)
                }
                rows += values
            }
            return rows
        }
    }
}

