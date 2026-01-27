package com.example.selliaapp.data.csv

import com.example.selliaapp.data.dao.InvoiceWithItems
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

object SalesCsvExporter {
    private val header = listOf(
        "invoice_id",
        "invoice_number",
        "date",
        "customer_id",
        "customer_name",
        "subtotal",
        "taxes",
        "discount_percent",
        "discount_amount",
        "surcharge_percent",
        "surcharge_amount",
        "total",
        "payment_method",
        "payment_notes",
        "product_id",
        "product_name",
        "quantity",
        "unit_price",
        "line_total"
    )

    fun export(invoices: List<InvoiceWithItems>): String {
        return buildString {
            appendLine(CsvExportUtils.line(header))
            invoices.forEach { invoiceWithItems ->
                val invoice = invoiceWithItems.invoice
                val date = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(invoice.dateMillis),
                    ZoneId.systemDefault()
                )
                val items = invoiceWithItems.items.ifEmpty {
                    listOf(null)
                }
                items.forEach { item ->
                    appendLine(
                        CsvExportUtils.line(
                            listOf(
                                invoice.id.toString(),
                                invoice.id.toString(),
                                date.toString(),
                                invoice.customerId?.toString(),
                                invoice.customerName,
                                invoice.subtotal.toString(),
                                invoice.taxes.toString(),
                                invoice.discountPercent.toString(),
                                invoice.discountAmount.toString(),
                                invoice.surchargePercent.toString(),
                                invoice.surchargeAmount.toString(),
                                invoice.total.toString(),
                                invoice.paymentMethod,
                                invoice.paymentNotes,
                                item?.productId?.toString(),
                                item?.productName,
                                item?.quantity?.toString(),
                                item?.unitPrice?.toString(),
                                item?.lineTotal?.toString()
                            )
                        )
                    )
                }
            }
        }
    }

    fun exportFileName(timestamp: String): String = "ventas_$timestamp.csv"

    fun mimeType(): String = "text/csv"
}
