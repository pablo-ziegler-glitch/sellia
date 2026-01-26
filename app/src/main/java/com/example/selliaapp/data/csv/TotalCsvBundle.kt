package com.example.selliaapp.data.csv

object TotalCsvBundle {
    private const val prefix = "#SECTION:"
    const val PRODUCTS = "${prefix}PRODUCTS"
    const val CUSTOMERS = "${prefix}CUSTOMERS"
    const val SALES = "${prefix}SALES"
    const val EXPENSES = "${prefix}EXPENSES"

    fun bundle(
        productsCsv: String,
        customersCsv: String,
        salesCsv: String,
        expensesCsv: String
    ): String {
        return buildString {
            appendLine(PRODUCTS)
            appendLine(productsCsv.trimEnd())
            appendLine()
            appendLine(CUSTOMERS)
            appendLine(customersCsv.trimEnd())
            appendLine()
            appendLine(SALES)
            appendLine(salesCsv.trimEnd())
            appendLine()
            appendLine(EXPENSES)
            appendLine(expensesCsv.trimEnd())
        }
    }

    fun splitSections(content: String): Map<String, String> {
        val lines = content.split("\n")
        val sections = linkedMapOf<String, StringBuilder>()
        var currentKey: String? = null
        lines.forEach { rawLine ->
            val line = rawLine.trimEnd('\r')
            if (line.startsWith(prefix)) {
                currentKey = line
                if (sections[currentKey] == null) {
                    sections[currentKey!!] = StringBuilder()
                }
                return@forEach
            }
            if (currentKey != null) {
                sections[currentKey!!]?.appendLine(line)
            }
        }
        return sections.mapValues { it.value.toString().trim() }
    }
}
