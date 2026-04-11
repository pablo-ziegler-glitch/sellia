package com.example.selliaapp.ui.util

import android.content.Context
import com.example.selliaapp.data.model.ImportResult
import com.example.selliaapp.data.model.ImportRowIssue
import org.json.JSONArray
import org.json.JSONObject
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
        val errors: List<String>,
        val rowIssues: List<ImportRowIssue> = emptyList()
    )

    private const val PREFS_NAME = "import_error_reports"
    private val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    fun save(context: Context, scope: Scope, fileName: String?, errors: List<String>) {
        save(context, scope, fileName, errors, emptyList())
    }

    fun save(context: Context, scope: Scope, fileName: String?, result: ImportResult) {
        save(context, scope, fileName, result.errors, result.rowIssues)
    }

    fun save(
        context: Context,
        scope: Scope,
        fileName: String?,
        errors: List<String>,
        rowIssues: List<ImportRowIssue>
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (errors.isEmpty() && rowIssues.isEmpty()) {
            prefs.edit().remove(scope.key).apply()
            return
        }
        val generatedAt = LocalDateTime.now().format(formatter)
        val payload = JSONObject().apply {
            put("generatedAt", generatedAt)
            put("fileName", fileName ?: "")
            put("errors", JSONArray().apply { errors.forEach { put(it) } })
            put(
                "rowIssues",
                JSONArray().apply {
                    rowIssues.forEach { issue ->
                        put(
                            JSONObject().apply {
                                put("line", issue.line)
                                put("productName", issue.productName ?: "")
                                put("skuOrBarcode", issue.skuOrBarcode ?: "")
                                put("attemptedAction", issue.attemptedAction)
                                put("technicalReason", issue.technicalReason)
                                put("userMessage", issue.userMessage)
                                put("suggestion", issue.suggestion)
                                put("status", issue.status)
                            }
                        )
                    }
                }
            )
        }.toString()
        prefs.edit().putString(scope.key, payload).apply()
    }

    fun read(context: Context, scope: Scope): Report? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val payload = prefs.getString(scope.key, null) ?: return null
        return runCatching {
            val json = JSONObject(payload)
            val generatedAt = json.optString("generatedAt")
            val fileName = json.optString("fileName").takeIf { it.isNotBlank() }
            val errors = mutableListOf<String>()
            val errorsJson = json.optJSONArray("errors") ?: JSONArray()
            for (i in 0 until errorsJson.length()) {
                val error = errorsJson.optString(i)
                if (error.isNotBlank()) errors += error
            }
            val rowIssues = mutableListOf<ImportRowIssue>()
            val rowIssuesJson = json.optJSONArray("rowIssues") ?: JSONArray()
            for (i in 0 until rowIssuesJson.length()) {
                val item = rowIssuesJson.optJSONObject(i) ?: continue
                rowIssues += ImportRowIssue(
                    line = item.optInt("line"),
                    productName = item.optString("productName").takeIf { it.isNotBlank() },
                    skuOrBarcode = item.optString("skuOrBarcode").takeIf { it.isNotBlank() },
                    attemptedAction = item.optString("attemptedAction"),
                    technicalReason = item.optString("technicalReason"),
                    userMessage = item.optString("userMessage"),
                    suggestion = item.optString("suggestion"),
                    status = item.optString("status").ifBlank { "rechazado" }
                )
            }
            if (errors.isEmpty() && rowIssues.isEmpty()) return null
            Report(
                generatedAt = generatedAt,
                fileName = fileName,
                errors = errors,
                rowIssues = rowIssues
            )
        }.getOrElse {
            // Compatibilidad con payload legacy (líneas)
            val lines = payload.split("\n")
            if (lines.isEmpty()) return null
            val generatedAt = lines.firstOrNull().orEmpty()
            val fileName = lines.getOrNull(1)?.takeIf { it.isNotBlank() }
            val errors = if (lines.size > 2) lines.drop(2).filter { it.isNotBlank() } else emptyList()
            if (errors.isEmpty()) return null
            Report(generatedAt = generatedAt, fileName = fileName, errors = errors)
        }
    }

    fun buildCsv(report: Report): String = buildString {
        if (report.rowIssues.isNotEmpty()) {
            appendLine("archivo,fecha,linea,producto,sku_barcode,accion,estado,motivo_tecnico,mensaje_amigable,sugerencia")
            report.rowIssues.forEach { issue ->
                append(csvCell(report.fileName ?: ""))
                append(',')
                append(csvCell(report.generatedAt))
                append(',')
                append(csvCell(issue.line.toString()))
                append(',')
                append(csvCell(issue.productName ?: ""))
                append(',')
                append(csvCell(issue.skuOrBarcode ?: ""))
                append(',')
                append(csvCell(issue.attemptedAction))
                append(',')
                append(csvCell(issue.status))
                append(',')
                append(csvCell(issue.technicalReason))
                append(',')
                append(csvCell(issue.userMessage))
                append(',')
                append(csvCell(issue.suggestion))
                appendLine()
            }
        } else {
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
    }

    private fun csvCell(raw: String): String {
        val escaped = raw.replace("\"", "\"\"")
        return "\"$escaped\""
    }
}
