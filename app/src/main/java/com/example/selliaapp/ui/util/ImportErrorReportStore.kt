package com.example.selliaapp.ui.util

import android.content.Context
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object ImportErrorReportStore {

    enum class Scope(val key: String, val title: String) {
        CROSS("cross", "Catálogo CROSS"),
        PRODUCTS("products", "Productos"),
        CUSTOMERS("customers", "Clientes"),
        USERS("users", "Usuarios"),
        TOTAL("total", "Exportación total")
    }

    data class Report(
        val generatedAt: String,
        val fileName: String?,
        val errors: List<String>
    )

    private const val PREFS_NAME = "import_error_reports"
    private val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    fun save(context: Context, scope: Scope, fileName: String?, errors: List<String>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (errors.isEmpty()) {
            prefs.edit().remove(scope.key).apply()
            return
        }
        val generatedAt = LocalDateTime.now().format(formatter)
        val payload = buildString {
            append(generatedAt)
            append("\n")
            append(fileName ?: "")
            errors.forEach { append("\n").append(it) }
        }
        prefs.edit().putString(scope.key, payload).apply()
    }

    fun read(context: Context, scope: Scope): Report? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val payload = prefs.getString(scope.key, null) ?: return null
        val lines = payload.split("\n")
        if (lines.isEmpty()) return null
        val generatedAt = lines.firstOrNull().orEmpty()
        val fileName = lines.getOrNull(1)?.takeIf { it.isNotBlank() }
        val errors = if (lines.size > 2) lines.drop(2).filter { it.isNotBlank() } else emptyList()
        if (errors.isEmpty()) return null
        return Report(generatedAt = generatedAt, fileName = fileName, errors = errors)
    }

    fun buildCsv(report: Report): String = buildString {
        appendLine("archivo,fecha,error")
        report.errors.forEach { error ->
            append(csvCell(report.fileName ?: ""))
            append(',')
            append(csvCell(report.generatedAt))
            append(',')
            append(csvCell(error))
            appendLine()
        }
    }

    private fun csvCell(raw: String): String {
        val escaped = raw.replace("\"", "\"\"")
        return "\"$escaped\""
    }
}
