package com.example.selliaapp.data.csv

import java.io.ByteArrayInputStream

object TotalCsvCompatibility {

    data class NormalizationResult(
        val content: String,
        val changed: Boolean,
        val notes: List<String>
    )

    fun normalizeForImport(content: String): NormalizationResult {
        val parsed = TotalCsvBundle.splitSections(content)
        val productsSection = parsed.sections[TotalCsvBundle.PRODUCTS]
            ?: return NormalizationResult(content = content, changed = false, notes = emptyList())
        if (productsSection.isBlank()) {
            return NormalizationResult(content = content, changed = false, notes = emptyList())
        }

        val normalizedProducts = normalizeProductsSection(productsSection)
        if (!normalizedProducts.changed) {
            return NormalizationResult(content = content, changed = false, notes = emptyList())
        }

        val rebuilt = TotalCsvBundle.bundle(
            productsCsv = normalizedProducts.csv,
            customersCsv = parsed.sections[TotalCsvBundle.CUSTOMERS].orEmpty(),
            salesCsv = parsed.sections[TotalCsvBundle.SALES].orEmpty(),
            expensesCsv = parsed.sections[TotalCsvBundle.EXPENSES].orEmpty()
        )
        return NormalizationResult(
            content = rebuilt,
            changed = true,
            notes = normalizedProducts.notes
        )
    }

    private data class NormalizedProducts(
        val csv: String,
        val changed: Boolean,
        val notes: List<String>
    )

    private fun normalizeProductsSection(productsCsv: String): NormalizedProducts {
        val table = CsvUtils.readAll(ByteArrayInputStream(productsCsv.toByteArray()))
        if (table.isEmpty()) {
            return NormalizedProducts(csv = productsCsv, changed = false, notes = emptyList())
        }
        val header = table.first()
        val expected = ProductCsvExporter.headerColumns()
        val existingByLower = header.map { it.trim().lowercase() }.toSet()
        val missing = expected.filter { it.lowercase() !in existingByLower }
        if (missing.isEmpty()) {
            return NormalizedProducts(csv = productsCsv, changed = false, notes = emptyList())
        }

        val normalizedHeader = header + missing
        val normalizedRows = table.drop(1).map { row ->
            val padded = row.toMutableList()
            while (padded.size < normalizedHeader.size) {
                padded.add("")
            }
            padded.take(normalizedHeader.size)
        }
        val normalizedCsv = buildString {
            appendLine(CsvExportUtils.line(normalizedHeader))
            normalizedRows.forEach { row ->
                appendLine(CsvExportUtils.line(row))
            }
        }.trimEnd()

        return NormalizedProducts(
            csv = normalizedCsv,
            changed = true,
            notes = listOf("Se agregaron columnas faltantes en PRODUCTS: ${missing.joinToString(", ")}")
        )
    }
}
