package com.example.selliaapp.data.csv

object TotalCsvBundle {
    private const val sectionPrefix = "#SECTION:"
    private const val versionPrefix = "#VERSION:"

    const val CURRENT_VERSION = 2

    const val PRODUCTS = "${sectionPrefix}PRODUCTS"
    const val CUSTOMERS = "${sectionPrefix}CUSTOMERS"
    const val SALES = "${sectionPrefix}SALES"
    const val EXPENSES = "${sectionPrefix}EXPENSES"

    data class BundleParseResult(
        val version: Int,
        val sections: Map<String, String>
    )

    fun bundle(
        productsCsv: String,
        customersCsv: String,
        salesCsv: String,
        expensesCsv: String
    ): String {
        return buildString {
            appendLine("$versionPrefix$CURRENT_VERSION")
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

    fun splitSections(content: String): BundleParseResult {
        val lines = content.split("\n")
        val sections = linkedMapOf<String, StringBuilder>()
        var currentKey: String? = null
        var version = 1

        lines.forEach { rawLine ->
            val line = rawLine.trimEnd('\r')
            if (line.startsWith(versionPrefix)) {
                version = line.removePrefix(versionPrefix).trim().toIntOrNull() ?: 1
                return@forEach
            }
            if (line.startsWith(sectionPrefix)) {
                currentKey = line
                if (sections[line] == null) {
                    sections[line] = StringBuilder()
                }
                return@forEach
            }
            val key = currentKey
            if (key != null) {
                sections[key]?.appendLine(line)
            }
        }
        return BundleParseResult(
            version = version,
            sections = sections.mapValues { it.value.toString().trim() }
        )
    }
}
