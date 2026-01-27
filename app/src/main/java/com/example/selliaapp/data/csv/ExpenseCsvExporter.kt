package com.example.selliaapp.data.csv

import com.example.selliaapp.data.model.ExpenseRecord

object ExpenseCsvExporter {
    private val header = listOf(
        "id",
        "template_id",
        "name",
        "category",
        "amount",
        "month",
        "year",
        "status",
        "receipt_uris"
    )

    fun export(records: List<ExpenseRecord>): String {
        return buildString {
            appendLine(CsvExportUtils.line(header))
            records.forEach { record ->
                val receipts = record.receiptUris.joinToString("|").ifBlank { "" }
                appendLine(
                    CsvExportUtils.line(
                        listOf(
                            record.id.toString(),
                            record.templateId.toString(),
                            record.nameSnapshot,
                            record.categorySnapshot,
                            record.amount.toString(),
                            record.month.toString(),
                            record.year.toString(),
                            record.status.name,
                            receipts
                        )
                    )
                )
            }
        }
    }

    fun exportFileName(timestamp: String): String = "gastos_$timestamp.csv"

    fun mimeType(): String = "text/csv"
}
