package com.example.selliaapp.data.csv

import com.example.selliaapp.data.model.Invoice
import com.example.selliaapp.data.model.InvoiceItem
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

object SalesCsvImporter {
    data class Row(
        val invoiceId: Long,
        val dateMillis: Long,
        val customerId: Int?,
        val customerName: String?,
        val subtotal: Double,
        val taxes: Double,
        val discountPercent: Int,
        val discountAmount: Double,
        val surchargePercent: Int,
        val surchargeAmount: Double,
        val total: Double,
        val paymentMethod: String,
        val paymentNotes: String?,
        val productId: Int?,
        val productName: String?,
        val quantity: Int?,
        val unitPrice: Double?,
        val lineTotal: Double?
    )

    data class ParsedSale(
        val invoice: Invoice,
        val items: List<InvoiceItem>
    )

    data class ImportResult(
        val inserted: Int,
        val errors: List<String>
    )

    fun parseTable(table: List<List<String>>): List<Row> {
        if (table.isEmpty()) return emptyList()
        val header = table.first()
        val idx = CsvUtils.HeaderIndex(header)
        return CsvUtils.dataRowsUntilFirstBlank(table)
            .mapNotNull { row ->
                val invoiceId = idx.get(row, "invoice_id", listOf("id"))?.toLongOrNull()
                    ?.takeIf { it > 0L }
                    ?: return@mapNotNull null
                val dateText = idx.get(row, "date", listOf("fecha"))?.ifBlank { null }
                val dateMillis = dateText?.let { parseDateMillis(it) } ?: System.currentTimeMillis()
                val customerId = idx.get(row, "customer_id", listOf("cliente_id"))?.toIntOrNull()
                val customerName = idx.get(row, "customer_name", listOf("cliente"))?.ifBlank { null }
                val subtotal = idx.get(row, "subtotal")?.replace(',', '.')?.toDoubleOrNull() ?: 0.0
                val taxes = idx.get(row, "taxes", listOf("impuestos"))?.replace(',', '.')?.toDoubleOrNull() ?: 0.0
                val discountPercent = idx.get(row, "discount_percent", listOf("descuento_pct"))?.toIntOrNull() ?: 0
                val discountAmount = idx.get(row, "discount_amount", listOf("descuento"))?.replace(',', '.')?.toDoubleOrNull() ?: 0.0
                val surchargePercent = idx.get(row, "surcharge_percent", listOf("recargo_pct"))?.toIntOrNull() ?: 0
                val surchargeAmount = idx.get(row, "surcharge_amount", listOf("recargo"))?.replace(',', '.')?.toDoubleOrNull() ?: 0.0
                val total = idx.get(row, "total")?.replace(',', '.')?.toDoubleOrNull() ?: 0.0
                val paymentMethod = idx.get(row, "payment_method", listOf("metodo_pago", "método_pago"))
                    ?.ifBlank { null } ?: "EFECTIVO"
                val paymentNotes = idx.get(row, "payment_notes", listOf("nota_pago"))?.ifBlank { null }
                val productId = idx.get(row, "product_id", listOf("producto_id"))?.toIntOrNull()
                    ?: return@mapNotNull null
                val productName = idx.get(row, "product_name", listOf("producto"))
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                val quantity = idx.get(row, "quantity", listOf("cantidad"))?.toIntOrNull()
                    ?: return@mapNotNull null
                val unitPrice = idx.get(row, "unit_price", listOf("precio_unitario"))
                    ?.replace(',', '.')
                    ?.toDoubleOrNull()
                    ?: return@mapNotNull null
                val lineTotal = idx.get(row, "line_total", listOf("total_linea"))?.replace(',', '.')?.toDoubleOrNull()
                Row(
                    invoiceId = invoiceId,
                    dateMillis = dateMillis,
                    customerId = customerId,
                    customerName = customerName,
                    subtotal = subtotal,
                    taxes = taxes,
                    discountPercent = discountPercent,
                    discountAmount = discountAmount,
                    surchargePercent = surchargePercent,
                    surchargeAmount = surchargeAmount,
                    total = total,
                    paymentMethod = paymentMethod,
                    paymentNotes = paymentNotes,
                    productId = productId,
                    productName = productName,
                    quantity = quantity,
                    unitPrice = unitPrice,
                    lineTotal = lineTotal
                )
            }
    }

    fun groupRows(rows: List<Row>): Pair<List<ParsedSale>, List<String>> {
        val errors = mutableListOf<String>()
        val grouped = rows.groupBy { it.invoiceId }
        val parsed = mutableListOf<ParsedSale>()
        grouped.forEach { (invoiceId, invoiceRows) ->
            if (invoiceId == 0L) {
                errors += "Venta sin invoice_id"
                return@forEach
            }
            val base = invoiceRows.first()
            val invoice = Invoice(
                id = invoiceId,
                dateMillis = base.dateMillis,
                customerId = base.customerId,
                customerName = base.customerName,
                subtotal = base.subtotal,
                taxes = base.taxes,
                discountPercent = base.discountPercent,
                discountAmount = base.discountAmount,
                surchargePercent = base.surchargePercent,
                surchargeAmount = base.surchargeAmount,
                total = base.total,
                paymentMethod = base.paymentMethod,
                paymentNotes = base.paymentNotes
            )
            val items = invoiceRows.mapNotNull { row ->
                val productId = row.productId
                val quantity = row.quantity
                val unitPrice = row.unitPrice
                val productName = row.productName
                if (productId == null || quantity == null || unitPrice == null || productName.isNullOrBlank()) {
                    return@mapNotNull null
                }
                InvoiceItem(
                    id = 0L,
                    invoiceId = invoiceId,
                    productId = productId,
                    productName = productName,
                    quantity = quantity,
                    unitPrice = unitPrice,
                    lineTotal = row.lineTotal ?: (quantity * unitPrice)
                )
            }
            parsed += ParsedSale(invoice = invoice, items = items)
        }
        return parsed to errors
    }

    private fun parseDateMillis(value: String): Long {
        return runCatching {
            val dateTime = LocalDateTime.parse(value)
            dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }.getOrElse {
            runCatching {
                Instant.parse(value).toEpochMilli()
            }.getOrDefault(System.currentTimeMillis())
        }
    }
}
