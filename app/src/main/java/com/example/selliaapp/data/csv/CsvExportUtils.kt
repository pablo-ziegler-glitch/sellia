package com.example.selliaapp.data.csv

object CsvExportUtils {
    fun escape(value: String): String {
        val needsQuotes = value.contains(',') || value.contains('\n') || value.contains('\r') || value.contains('"')
        val escaped = value.replace("\"", "\"\"")
        return if (needsQuotes) "\"$escaped\"" else escaped
    }

    fun line(values: List<String?>): String {
        return values.joinToString(",") { value ->
            escape(value ?: "")
        }
    }
}
