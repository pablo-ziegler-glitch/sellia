package com.example.selliaapp.data.csv

import com.example.selliaapp.data.local.entity.CustomerEntity

object CustomerCsvExporter {
    private val header = listOf(
        "name",
        "phone",
        "email",
        "address",
        "nickname",
        "rubros",
        "payment_term",
        "payment_method",
        "created_at"
    )

    fun export(customers: List<CustomerEntity>): String {
        return buildString {
            appendLine(CsvExportUtils.line(header))
            customers.forEach { customer ->
                appendLine(
                    CsvExportUtils.line(
                        listOf(
                            customer.name,
                            customer.phone,
                            customer.email,
                            customer.address,
                            customer.nickname,
                            customer.rubrosCsv,
                            customer.paymentTerm,
                            customer.paymentMethod,
                            customer.createdAt.toString()
                        )
                    )
                )
            }
        }
    }

    fun exportFileName(timestamp: String): String = "clientes_$timestamp.csv"

    fun mimeType(): String = "text/csv"
}
