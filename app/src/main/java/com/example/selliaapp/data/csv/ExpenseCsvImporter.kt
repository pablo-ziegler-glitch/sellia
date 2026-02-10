package com.example.selliaapp.data.csv

import com.example.selliaapp.data.model.ExpenseRecord
import com.example.selliaapp.data.model.ExpenseStatus

object ExpenseCsvImporter {
    data class Row(
        val id: Int,
        val templateId: Int,
        val name: String,
        val category: String,
        val amount: Double,
        val month: Int,
        val year: Int,
        val status: ExpenseStatus,
        val receiptUris: List<String>
    )

    fun parseTable(table: List<List<String>>): List<Row> {
        if (table.isEmpty()) return emptyList()
        val header = table.first()
        val idx = CsvUtils.HeaderIndex(header)
        return CsvUtils.dataRowsUntilFirstBlank(table)
            .mapNotNull { row ->
                val id = idx.get(row, "id")?.toIntOrNull() ?: 0
                val templateId = idx.get(row, "template_id", listOf("templateId"))?.toIntOrNull() ?: 0
                val name = idx.get(row, "name", listOf("nombre"))
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                val category = idx.get(row, "category", listOf("categoria", "categoría"))?.ifBlank { null } ?: "General"
                val amount = idx.get(row, "amount", listOf("monto", "importe"))
                    ?.replace(',', '.')
                    ?.toDoubleOrNull()
                    ?: return@mapNotNull null
                val month = idx.get(row, "month", listOf("mes"))?.toIntOrNull() ?: 1
                val year = idx.get(row, "year", listOf("anio", "año"))?.toIntOrNull() ?: 1970
                val statusText = idx.get(row, "status", listOf("estado"))?.ifBlank { null } ?: ExpenseStatus.IMPAGO.name
                val status = runCatching { ExpenseStatus.valueOf(statusText.uppercase()) }.getOrDefault(ExpenseStatus.IMPAGO)
                val receipts = idx.get(row, "receipt_uris", listOf("receipts", "comprobantes"))
                    ?.split("|")
                    ?.map { it.trim() }
                    ?.filter { it.isNotBlank() }
                    ?: emptyList()
                Row(
                    id = id,
                    templateId = templateId,
                    name = name,
                    category = category,
                    amount = amount,
                    month = month,
                    year = year,
                    status = status,
                    receiptUris = receipts
                )
            }
    }

    fun toRecords(rows: List<Row>): List<ExpenseRecord> {
        return rows.map { row ->
            ExpenseRecord(
                id = row.id,
                templateId = row.templateId,
                nameSnapshot = row.name,
                categorySnapshot = row.category,
                amount = row.amount,
                month = row.month,
                year = row.year,
                status = row.status,
                receiptUris = row.receiptUris
            )
        }
    }
}
