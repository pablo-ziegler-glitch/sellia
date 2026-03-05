package com.floki.app.data.csv

import com.floki.app.data.local.entity.CustomerEntity

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

    /**
     * Exporta clientes a CSV.
     *
     * @param maskPii Si es `true`, los campos PII (phone, email, address) se enmascaran.
     *                Solo los roles con [Permission.EXPORT_PII_DATA] deben llamar con `maskPii = false`.
     */
    fun export(customers: List<CustomerEntity>, maskPii: Boolean = false): String {
        return buildString {
            appendLine(CsvExportUtils.line(header))
            customers.forEach { customer ->
                appendLine(
                    CsvExportUtils.line(
                        listOf(
                            customer.name,
                            if (maskPii) maskPhone(customer.phone) else customer.phone,
                            if (maskPii) maskEmail(customer.email) else customer.email,
                            if (maskPii) null else customer.address,
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

    private fun maskPhone(phone: String?): String? {
        if (phone.isNullOrBlank()) return phone
        return if (phone.length > 4) "*".repeat(phone.length - 4) + phone.takeLast(4)
        else "****"
    }

    private fun maskEmail(email: String?): String? {
        if (email.isNullOrBlank()) return email
        val atIndex = email.indexOf('@')
        if (atIndex <= 0) return "****"
        val local = email.substring(0, atIndex)
        val domain = email.substring(atIndex)
        val maskedLocal = if (local.length <= 2) "**" else local.first() + "*".repeat(local.length - 2) + local.last()
        return "$maskedLocal$domain"
    }

    fun exportFileName(timestamp: String): String = "clientes_$timestamp.csv"

    fun mimeType(): String = "text/csv"
}
